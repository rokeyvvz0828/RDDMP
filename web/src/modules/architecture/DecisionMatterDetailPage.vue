<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Document, Refresh, UploadFilled, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { apiErrorMessage } from '../../api/error'
import { getAttachmentDownload, getAttachmentPreview, uploadAttachment } from '../../api/attachments'
import { getCurrentWorkflowTaskContext, decideWorkflowTask } from '../../api/workflow'
import type { WorkflowTaskAction, WorkflowTaskContext } from '../../api/workflow'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import {
  addDecisionMaterial,
  bindDecisionAttachment,
  completeDecisionActionItem,
  deleteDecisionAttachment,
  firstHandlingDecisionMatter,
  getDecisionConclusionChain,
  getDecisionMatter,
  listDecisionAttachments,
  listDecisionConclusions,
  listDecisionTypes,
  prepareDecisionPublication,
  recordDecisionReview,
  resubmitDecisionMatter,
  searchDecisionUsers,
  setDecisionType,
  startDecisionPublication,
  updateDecisionReview
} from './api'
import type {
  AttachmentItemView,
  ChainLink,
  ConclusionEffectiveStatus,
  ConclusionView,
  DecisionActionItem,
  DecisionMaterial,
  DecisionMatterDetail,
  DecisionMatterStatus,
  DecisionReview,
  DecisionUserReference,
  FirstHandlingOutcome,
  MaterialKind,
  ReviewMethod,
  StandardCategory,
  SupersessionKind
} from './types'
import { cancelled, formatDateTime, httpStatus } from './utils'
import './architecture.css'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const matter = ref<DecisionMatterDetail | null>(null)
const materials = ref<DecisionMaterial[]>([])
const reviews = ref<DecisionReview[]>([])
const actionItems = ref<Record<number, DecisionActionItem[]>>({})
interface ParticipantView { userId: number; displayName: string }
const participants = ref<Record<number, ParticipantView[]>>({})
const attachments = ref<AttachmentItemView[]>([])
const conclusions = ref<ConclusionView[]>([])
const chain = ref<ConclusionView | null>(null)
const types = ref<StandardCategory[]>([])
const workflowTask = ref<WorkflowTaskContext | null>(null)

const loading = ref(false)
const forbidden = ref(false)
const loadError = ref('')
const actionBusy = ref('')

const canManage = computed(() => auth.hasPermission('architecture:decision:manage'))
const canReview = computed(() => auth.hasPermission('architecture:decision:review') || canManage.value)
const canPropose = computed(() => ['architecture:decision:propose', 'architecture:decision:review', 'architecture:decision:manage'].some(p => auth.hasPermission(p)))

const statusLabels: Record<DecisionMatterStatus, string> = {
  SUBMITTED: '待首次处理',
  RETURNED_FOR_INFO: '要求补充',
  IN_REVIEW: '评审中',
  PUBLISHED: '已完成'
}
const outcomeLabels: Record<FirstHandlingOutcome, string> = {
  ACCEPTED: '确认受理',
  REQUESTED_INFO: '要求补充信息',
  REVIEW_MODE_SET: '确定评审方式'
}
const kindLabels: Record<MaterialKind, string> = {
  SOLUTION: '方案', IMPACT: '影响分析', DISPUTE: '争议点', OTHER: '其他'
}
const effectiveLabels: Record<ConclusionEffectiveStatus, string> = {
  EFFECTIVE: '有效', SUPERSEDED: '已被替代', PARTIALLY_SUPERSEDED: '已被部分修订'
}
const supersessionKindLabels: Record<SupersessionKind, string> = {
  SUPERSEDE: '替代', PARTIALLY_REVISE: '部分修订'
}

function isOwn(m: DecisionMatterDetail) {
  return m.proposerId === auth.user?.id
}

async function load() {
  if (!Number.isInteger(id) || id <= 0) {
    loadError.value = '事项编号无效'
    loading.value = false
    return
  }
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const [detail, typeOptions] = await Promise.all([
      getDecisionMatter(id),
      listDecisionTypes().catch(() => [] as StandardCategory[])
    ])
    matter.value = detail
    types.value = typeOptions
    await Promise.all([
      loadMaterials(detail),
      loadReviews(detail),
      loadAttachments(detail),
      loadConclusions(detail),
      loadWorkflow(detail)
    ])
  } catch (error) {
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '事项详情加载失败')
  } finally {
    loading.value = false
  }
}

async function loadMaterials(detail: DecisionMatterDetail) {
  try {
    const response = await fetch(`/api/architecture/decisions/${detail.id}/materials`, { headers: { Authorization: `Bearer ${auth.token || ''}` } })
    materials.value = response.ok ? await response.json().then(body => body.data || []) : []
  } catch {
    materials.value = []
  }
}

async function loadReviews(detail: DecisionMatterDetail) {
  try {
    const response = await fetch(`/api/architecture/decisions/${detail.id}/reviews`, { headers: { Authorization: `Bearer ${auth.token || ''}` } })
    const list: DecisionReview[] = response.ok ? await response.json().then(body => body.data || []) : []
    reviews.value = list
    for (const review of list) {
      const [participantResponse, actionResponse] = await Promise.all([
        fetch(`/api/architecture/decisions/${detail.id}/reviews/${review.id}/participants`, { headers: { Authorization: `Bearer ${auth.token || ''}` } }),
        fetch(`/api/architecture/decisions/${detail.id}/reviews/${review.id}/action-items`, { headers: { Authorization: `Bearer ${auth.token || ''}` } })
      ])
      participants.value[review.id] = participantResponse.ok ? await participantResponse.json().then(body => body.data || []) : []
      actionItems.value[review.id] = actionResponse.ok ? await actionResponse.json().then(body => body.data || []) : []
    }
  } catch {
    reviews.value = []
  }
}

async function loadAttachments(detail: DecisionMatterDetail) {
  attachments.value = await listDecisionAttachments(detail.id).catch(() => [])
}

async function loadConclusions(detail: DecisionMatterDetail) {
  try {
    const result = await listDecisionConclusions({ page: 1, size: 100 })
    conclusions.value = result.records
    const chainResult = await fetch(`/api/architecture/decisions/conclusions`, { headers: { Authorization: `Bearer ${auth.token || ''}` } })
    if (chainResult.ok) {
      const body = await chainResult.json()
      const own = (body.data?.records || []).find((item: ConclusionView) => item.matterId === detail.id)
      chain.value = own || null
    }
  } catch {
    conclusions.value = []
  }
}

async function loadWorkflow(detail: DecisionMatterDetail) {
  workflowTask.value = null
  if (!canManage.value || detail.status !== 'IN_REVIEW' || !detail.currentWorkflowInstanceId) return
  try {
    workflowTask.value = (await getCurrentWorkflowTaskContext(
      'architecture_decision_publish',
      String(detail.id)
    )).data.data
  } catch {
    workflowTask.value = null
  }
}

async function refresh() {
  await load()
}

// ---------- 首次处理 ----------
const firstHandlingOpen = ref(false)
const firstForm = reactive({
  outcome: '' as FirstHandlingOutcome | '',
  reviewMode: '' as ReviewMethod | '',
  comment: ''
})

function openFirstHandling() {
  firstForm.outcome = 'ACCEPTED'
  firstForm.reviewMode = 'ASYNC'
  firstForm.comment = ''
  firstHandlingOpen.value = true
}

async function submitFirstHandling() {
  const m = matter.value
  if (!m || !firstForm.outcome) { ElMessage.warning('请选择首次处理结果'); return }
  if (firstForm.outcome !== 'REQUESTED_INFO' && !firstForm.reviewMode) { ElMessage.warning('确认受理或确定评审方式时必须选择评审方式'); return }
  actionBusy.value = 'first-handling'
  try {
    matter.value = await firstHandlingDecisionMatter(m.id, {
      rowVersion: m.rowVersion,
      outcome: firstForm.outcome as FirstHandlingOutcome,
      reviewMode: (firstForm.reviewMode || null) as ReviewMethod | null,
      comment: firstForm.comment.trim() || null
    })
    firstHandlingOpen.value = false
    ElMessage.success('首次处理已记录')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '首次处理失败'))
  } finally {
    actionBusy.value = ''
  }
}

async function resubmit() {
  const m = matter.value
  if (!m) return
  try {
    await ElMessageBox.confirm('确认重新提交？受理时间与首次处理期限将重新计算。', '重新提交事项', { type: 'info' })
  } catch { return }
  actionBusy.value = 'resubmit'
  try {
    matter.value = await resubmitDecisionMatter(m.id, m.rowVersion)
    ElMessage.success('已重新提交')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '重新提交失败'))
  } finally {
    actionBusy.value = ''
  }
}

// ---------- 补充材料 ----------
const materialOpen = ref(false)
const materialForm = reactive({ kind: 'SOLUTION' as MaterialKind, content: '' })

async function submitMaterial() {
  const m = matter.value
  if (!m) return
  if (!materialForm.content.trim()) { ElMessage.warning('请填写材料内容'); return }
  actionBusy.value = 'material'
  try {
    await addDecisionMaterial(m.id, { kind: materialForm.kind, content: materialForm.content.trim() })
    materialOpen.value = false
    materialForm.content = ''
    ElMessage.success('材料已补充')
    await loadMaterials(m)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '补充失败'))
  } finally {
    actionBusy.value = ''
  }
}

// ---------- 事项类型 ----------
const typeDialogOpen = ref(false)
const typeSelection = ref('')

function openTypeDialog() {
  const m = matter.value
  if (!m) return
  typeSelection.value = m.typeCode || ''
  typeDialogOpen.value = true
}

async function confirmType() {
  const m = matter.value
  if (!m) return
  if (!typeSelection.value) { ElMessage.warning('请选择事项类型'); return }
  actionBusy.value = 'type'
  try {
    matter.value = await setDecisionType(m.id, { rowVersion: m.rowVersion, typeCode: typeSelection.value })
    typeDialogOpen.value = false
    ElMessage.success('类型已确定')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '设置类型失败'))
  } finally {
    actionBusy.value = ''
  }
}

// ---------- 评审记录 ----------
const reviewOpen = ref(false)
const reviewSaving = ref(false)
const editingReview = ref<DecisionReview | null>(null)
const reviewForm = reactive({
  method: 'ASYNC' as ReviewMethod,
  reviewedAt: '',
  processMaterialSummary: '',
  keyOpinion: '',
  conclusionContent: '',
  conclusionRationale: '',
  participantIds: [] as number[],
  actionItems: [] as { id?: number | null; content: string; ownerUserId?: number | null; ownerName?: string | null }[]
})
const userOptions = ref<DecisionUserReference[]>([])

function openReview(review: DecisionReview | null) {
  const m = matter.value
  if (!m) return
  editingReview.value = review
  reviewForm.method = review?.method || 'ASYNC'
  reviewForm.reviewedAt = review ? review.reviewedAt.slice(0, 16) : ''
  reviewForm.processMaterialSummary = review?.processMaterialSummary || ''
  reviewForm.keyOpinion = review?.keyOpinion || ''
  reviewForm.conclusionContent = review?.conclusionContent || ''
  reviewForm.conclusionRationale = review?.conclusionRationale || ''
  reviewForm.participantIds = participants.value[review?.id || -1]?.map(p => p.userId) || []
  reviewForm.actionItems = actionItems.value[review?.id || -1]?.map(item => ({
    id: item.id, content: item.content, ownerUserId: item.ownerUserId, ownerName: item.ownerName
  })) || []
  searchDecisionUsers('').then(list => { userOptions.value = list }).catch(() => { userOptions.value = [] })
  reviewOpen.value = true
}

async function submitReview() {
  const m = matter.value
  if (!m) return
  if (!reviewForm.method) { ElMessage.warning('请选择评审方式'); return }
  reviewSaving.value = true
  try {
    const payload = {
      method: reviewForm.method,
      reviewedAt: reviewForm.reviewedAt || null,
      processMaterialSummary: reviewForm.processMaterialSummary.trim() || null,
      keyOpinion: reviewForm.keyOpinion.trim() || null,
      conclusionContent: reviewForm.conclusionContent.trim() || null,
      conclusionRationale: reviewForm.conclusionRationale.trim() || null,
      participantUserIds: reviewForm.participantIds,
      actionItems: reviewForm.actionItems.filter(item => item.content.trim())
    }
    if (editingReview.value) {
      await updateDecisionReview(m.id, editingReview.value.id, payload)
    } else {
      await recordDecisionReview(m.id, payload)
    }
    reviewOpen.value = false
    ElMessage.success('评审记录已保存')
    await loadReviews(m)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '保存评审失败'))
  } finally {
    reviewSaving.value = false
  }
}

async function completeActionItem(reviewId: number, item: DecisionActionItem) {
  const m = matter.value
  if (!m) return
  actionBusy.value = `action-${item.id}`
  try {
    await completeDecisionActionItem(m.id, reviewId, item.id)
    ElMessage.success('行动项已完成')
    await loadReviews(m)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '操作失败'))
  } finally {
    actionBusy.value = ''
  }
}

// ---------- 结论发布 ----------
const publicationOpen = ref(false)
const publicationSaving = ref(false)
const publicationForm = reactive({ reviewId: null as number | null, targets: [] as { conclusionId: number; kind: SupersessionKind }[] })

function reviewWithConclusion() {
  return reviews.value.filter(review => review.conclusionContent && review.conclusionContent.trim())
}

function openPublication() {
  const m = matter.value
  if (!m) return
  const withConclusion = reviewWithConclusion()
  publicationForm.reviewId = withConclusion.length ? withConclusion[withConclusion.length - 1].id : null
  publicationForm.targets = []
  publicationOpen.value = true
}

async function submitPublication() {
  const m = matter.value
  if (!m || !publicationForm.reviewId) { ElMessage.warning('请选择含正式结论的评审记录'); return }
  publicationSaving.value = true
  try {
    await prepareDecisionPublication(m.id, {
      rowVersion: m.rowVersion,
      reviewId: publicationForm.reviewId,
      targets: publicationForm.targets
    })
    publicationOpen.value = false
    ElMessage.success('发布准备完成，请启动发布审批')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '发布准备失败'))
  } finally {
    publicationSaving.value = false
  }
}

async function startPublication() {
  const m = matter.value
  if (!m) return
  try {
    await ElMessageBox.confirm('确认启动结论发布审批？只有审批通过后结论才会正式发布。', '启动发布审批', { type: 'info' })
  } catch { return }
  actionBusy.value = 'start-publication'
  try {
    matter.value = await startDecisionPublication(m.id, m.rowVersion)
    ElMessage.success('发布审批已启动')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '启动失败'))
  } finally {
    actionBusy.value = ''
  }
}

async function decide(action: WorkflowTaskAction) {
  const task = workflowTask.value
  if (!task || actionBusy.value) return
  const label = action === 'APPROVE' ? '通过' : action === 'RETURN' ? '退回' : '拒绝'
  try {
    const prompt = await ElMessageBox.prompt(
      action === 'APPROVE' ? '通过后结论将正式发布，可填写审批意见。' : '请填写处理原因。',
      `${label}结论发布`,
      {
        confirmButtonText: label,
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: value => action === 'APPROVE' || Boolean(value?.trim()) || '退回或拒绝必须填写原因'
      }
    )
    actionBusy.value = `decide-${action}`
    await decideWorkflowTask(task.task_id, action, prompt.value.trim())
    ElMessage.success(`已${label}，业务状态将由工作流事件更新`)
    await load()
  } catch (error) {
    if (!cancelled(error)) ElMessage.error(apiErrorMessage(error, `${label}失败`))
  } finally {
    actionBusy.value = ''
  }
}

// ---------- 附件 ----------
const uploadRef = ref<UploadInstance>()
const uploading = ref(false)

async function onAttachmentUploaded(file: UploadFile) {
  const raw = file.raw
  const m = matter.value
  if (!raw || !m) return
  uploading.value = true
  try {
    const item = (await uploadAttachment(raw)).data.data
    await bindDecisionAttachment(m.id, item.id)
    ElMessage.success('附件已上传')
    await loadAttachments(m)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '上传失败'))
  } finally {
    uploading.value = false
    if (uploadRef.value) uploadRef.value.clearFiles()
  }
}

async function removeAttachment(item: AttachmentItemView) {
  const m = matter.value
  if (!m) return
  try {
    await ElMessageBox.confirm(`确认移除附件《${item.fileName}》？`, '移除附件', { type: 'warning' })
  } catch { return }
  try {
    await deleteDecisionAttachment(m.id, item.id)
    ElMessage.success('已移除')
    await loadAttachments(m)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '移除失败'))
  }
}

function previewAttachmentItem(item: AttachmentItemView) {
  getAttachmentPreview(item.id).then(result => window.open(result.data.data.previewUrl, '_blank', 'noopener'))
    .catch(error => ElMessage.error(apiErrorMessage(error, '预览失败')))
}

function downloadAttachmentItem(item: AttachmentItemView) {
  getAttachmentDownload(item.id).then(result => window.open(result.data.data.downloadUrl, '_blank', 'noopener'))
    .catch(error => ElMessage.error(apiErrorMessage(error, '下载失败')))
}

// ---------- 替代链 ----------
async function openChain(conclusion: ConclusionView) {
  try {
    const detail = await getDecisionConclusionChain(conclusion.conclusionId)
    chain.value = detail
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '加载替代链失败'))
  }
}

function typeLabel(code: string | null) {
  return code ? types.value.find(t => t.code === code)?.label || code : '待分类'
}

onMounted(load)
</script>

<template>
  <div class="architecture-page">
    <el-button :icon="ArrowLeft" link @click="router.push('/architecture/decisions')">返回事项列表</el-button>
    <UiPageHeader eyebrow="ARCHITECTURE DECISION" :title="matter ? `${matter.matterNo} · ${matter.title}` : '架构决策事项详情'" />

    <UiEmptyState v-if="forbidden" title="无访问权限" description="当前账号缺少架构决策查看权限，请联系管理员授权。" />
    <UiEmptyState v-else-if="loadError" title="加载失败" :description="loadError">
      <template #action><el-button :icon="Refresh" @click="refresh">重试</el-button></template>
    </UiEmptyState>

    <template v-else-if="matter">
      <el-card shadow="never" class="ui-surface-card" v-loading="loading">
        <template #header>
          <div class="decision-card-header">
            <span>事项信息</span>
            <el-button :icon="Refresh" circle size="small" @click="refresh" />
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态"><UiStatusTag :value="matter.status" :labels="statusLabels" /></el-descriptions-item>
          <el-descriptions-item label="事项类型">
            <div class="decision-type-field">
              <span v-if="!matter.typeCode" class="standard-muted">待分类</span>
              <UiStatusTag v-else :value="matter.typeCode" :labels="Object.fromEntries(types.map(t => [t.code, t.label]))" tone="info" />
              <el-button v-if="canReview && matter.status !== 'PUBLISHED'" link type="primary" @click="openTypeDialog">确定类型</el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="提出人">{{ matter.proposerName }}</el-descriptions-item>
          <el-descriptions-item label="受理时间">{{ formatDateTime(matter.receivedAt) }}</el-descriptions-item>
          <el-descriptions-item label="首次处理期限">
            <span :class="{ 'standard-overdue': matter.firstHandlingOverdue }">{{ matter.firstHandlingDeadline }}</span>
            <UiStatusTag v-if="matter.firstHandlingOverdue" value="已逾期" tone="danger" style="margin-left:6px" />
          </el-descriptions-item>
          <el-descriptions-item label="首次处理">
            {{ matter.firstHandlingOutcome ? outcomeLabels[matter.firstHandlingOutcome] : '待处理' }}
            <span v-if="matter.firstHandlerName" class="standard-muted">（{{ matter.firstHandlerName }} · {{ formatDateTime(matter.firstHandledAt) }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="评审方式">{{ matter.reviewMode === 'ASYNC' ? '异步' : matter.reviewMode === 'MEETING' ? '会议' : '—' }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatDateTime(matter.publicationPreparedAt) || '—' }}</el-descriptions-item>
          <el-descriptions-item label="问题或困难描述" :span="2"><pre class="standard-detail-text standard-detail-pre">{{ matter.problem }}</pre></el-descriptions-item>
        </el-descriptions>

        <div class="decision-actions">
          <template v-if="matter.status === 'SUBMITTED' && canReview">
            <el-button type="primary" :loading="actionBusy === 'first-handling'" @click="openFirstHandling">首次处理</el-button>
          </template>
          <template v-if="matter.status === 'RETURNED_FOR_INFO' && (canManage || isOwn(matter))">
            <el-button type="primary" :loading="actionBusy === 'resubmit'" @click="resubmit">补充后重新提交</el-button>
          </template>
          <template v-if="matter.status === 'IN_REVIEW' && canReview">
            <el-button type="primary" @click="openReview(null)">记录评审</el-button>
          </template>
          <template v-if="matter.status === 'IN_REVIEW' && canManage">
            <el-button type="success" @click="openPublication">结论发布准备</el-button>
            <el-button v-if="matter.publicationPreparedAt && !matter.currentWorkflowInstanceId" type="warning"
                       :loading="actionBusy === 'start-publication'" @click="startPublication">启动发布审批</el-button>
          </template>
          <template v-if="matter.status !== 'PUBLISHED' && (canReview || (canPropose && isOwn(matter)))">
            <el-button @click="materialOpen = true">补充材料</el-button>
          </template>
        </div>

        <!-- 工作流任务 -->
        <el-alert v-if="matter.status === 'IN_REVIEW' && matter.currentWorkflowInstanceId && !workflowTask"
                  type="info" :closable="false" show-icon title="发布审批进行中，当前账号无可处理任务（流程实例 #' + matter.currentWorkflowInstanceId + '）。" />
        <div v-else-if="matter.status === 'IN_REVIEW' && matter.currentWorkflowInstanceId && workflowTask"
             class="decision-workflow">
          <div class="decision-workflow__head">
            <div>
              <span class="decision-workflow__eyebrow">WORKFLOW TASK</span>
              <h3>发布审批</h3>
              <small>{{ workflowTask.business_title || '架构决策结论发布' }}</small>
            </div>
            <UiStatusTag value="待办" :labels="{ 待办: '待办' }" tone="warning" />
          </div>
          <div class="decision-workflow__body">
            <dl>
              <div><dt>节点</dt><dd>{{ workflowTask.node_name || workflowTask.task_key }}</dd></div>
              <div><dt>任务状态</dt><dd>{{ workflowTask.task_status }}</dd></div>
              <div><dt>流程实例</dt><dd>#{{ workflowTask.instance_id }}</dd></div>
            </dl>
          </div>
          <div class="decision-workflow__actions">
            <el-button v-if="workflowTask.allowed_actions?.includes('APPROVE')" type="success"
                       :loading="actionBusy === 'decide-APPROVE'" :disabled="Boolean(actionBusy)" @click="decide('APPROVE')">通过并发布</el-button>
            <el-button v-if="workflowTask.allowed_actions?.includes('RETURN')" type="warning"
                       :loading="actionBusy === 'decide-RETURN'" :disabled="Boolean(actionBusy)" @click="decide('RETURN')">退回</el-button>
            <el-button v-if="workflowTask.allowed_actions?.includes('REJECT')" type="danger"
                       :loading="actionBusy === 'decide-REJECT'" :disabled="Boolean(actionBusy)" @click="decide('REJECT')">拒绝</el-button>
          </div>
        </div>

        <el-divider content-position="left">协作补齐材料</el-divider>
        <el-timeline v-if="materials.length">
          <el-timeline-item v-for="item in materials" :key="item.id" :timestamp="`${formatDateTime(item.createdAt)} · ${item.createdByName || '—'}`" placement="top">
            <el-tag size="small" style="margin-right:8px">{{ kindLabels[item.kind] }}</el-tag>
            <pre class="standard-detail-text standard-detail-pre">{{ item.content }}</pre>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="尚未补充材料（方案、影响分析、争议点可后续补齐）" :image-size="60" />

        <el-divider content-position="left">评审记录</el-divider>
        <el-empty v-if="reviews.length === 0" description="尚未记录评审" :image-size="60" />
        <el-card v-for="review in reviews" :key="review.id" shadow="never" class="decision-review-card">
          <div class="decision-card-header">
            <span>评审 #{{ review.reviewNo }} · {{ review.method === 'ASYNC' ? '异步' : '会议' }} · {{ formatDateTime(review.reviewedAt) }}</span>
            <div>
              <el-button v-if="canReview && matter.status !== 'PUBLISHED'" link type="primary" @click="openReview(review)">编辑</el-button>
            </div>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="过程材料">{{ review.processMaterialSummary || '—' }}</el-descriptions-item>
            <el-descriptions-item label="关键意见">{{ review.keyOpinion || '—' }}</el-descriptions-item>
            <el-descriptions-item label="参与人" :span="2">
              <el-tag v-for="participant in participants[review.id] || []" :key="participant.userId" size="small" style="margin-right:6px">{{ participant.displayName }}</el-tag>
              <span v-if="!(participants[review.id] || []).length">—</span>
            </el-descriptions-item>
            <el-descriptions-item label="正式结论">{{ review.conclusionContent || '—' }}</el-descriptions-item>
            <el-descriptions-item label="理由">{{ review.conclusionRationale || '—' }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="(actionItems[review.id] || []).length" class="decision-action-items">
            <div v-for="item in actionItems[review.id]" :key="item.id" class="decision-action-item">
              <span>{{ item.content }}</span>
              <el-tag size="small" :type="item.status === 'DONE' ? 'success' : 'warning'" style="margin:0 8px">
                {{ item.status === 'DONE' ? '已完成' : '待跟踪' }}
              </el-tag>
              <span v-if="item.ownerName" class="standard-muted">{{ item.ownerName }}</span>
              <el-button v-if="canReview && item.status === 'OPEN'" link type="primary" size="small"
                         :loading="actionBusy === `action-${item.id}`" @click="completeActionItem(review.id, item)">完成</el-button>
            </div>
          </div>
        </el-card>

        <el-divider content-position="left">已发布结论与替代链</el-divider>
        <el-empty v-if="!chain" description="本事项尚未发布正式结论" :image-size="60" />
        <template v-else>
          <el-alert :type="chain.effectiveStatus === 'EFFECTIVE' ? 'success' : chain.effectiveStatus === 'SUPERSEDED' ? 'danger' : 'warning'"
                    :closable="false" show-icon :title="`结论状态：${effectiveLabels[chain.effectiveStatus]}`" />
          <el-descriptions :column="1" border style="margin-top:12px">
            <el-descriptions-item label="正式结论">{{ chain.content }}</el-descriptions-item>
            <el-descriptions-item label="理由">{{ chain.rationale || '—' }}</el-descriptions-item>
            <el-descriptions-item label="发布信息">{{ formatDateTime(chain.publishedAt) }} · {{ chain.publishedByName || '—' }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="chain.supersedes.length" style="margin-top:10px">
            <strong>本结论替代/部分修订：</strong>
            <el-tag v-for="link in chain.supersedes" :key="link.id" size="small" style="margin:4px">
              {{ supersessionKindLabels[link.kind] }} {{ link.supersededMatterNo || `结论 #${link.supersededConclusionId}` }}
            </el-tag>
          </div>
          <div v-if="chain.supersededBy.length" style="margin-top:10px">
            <strong>本结论被后续决策：</strong>
            <el-tag v-for="link in chain.supersededBy" :key="link.id" size="small" type="warning" style="margin:4px">
              {{ supersessionKindLabels[link.kind] }} {{ link.conclusionMatterNo || `结论 #${link.conclusionId}` }}
            </el-tag>
          </div>
        </template>

        <el-divider content-position="left">附件</el-divider>
        <div class="standard-attachments">
          <div v-for="item in attachments" :key="item.id" class="standard-attachment-item">
            <el-icon><Document /></el-icon>
            <span class="standard-attachment-name" @click="previewAttachmentItem(item)">{{ item.fileName }}</span>
            <el-button link type="primary" @click="downloadAttachmentItem(item)">下载</el-button>
            <el-button v-if="matter.status !== 'PUBLISHED'" link type="danger" @click="removeAttachment(item)">移除</el-button>
          </div>
          <el-empty v-if="attachments.length === 0" description="暂无附件" :image-size="60" />
          <el-upload v-if="matter.status !== 'PUBLISHED' && canPropose" ref="uploadRef" :auto-upload="false" :show-file-list="false" :on-change="onAttachmentUploaded">
            <el-button :icon="UploadFilled" :loading="uploading">上传过程材料</el-button>
          </el-upload>
        </div>
      </el-card>
    </template>

    <!-- 确定类型弹窗 -->
    <el-dialog v-model="typeDialogOpen" title="确定事项类型" width="min(480px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="事项类型" required>
          <el-select v-model="typeSelection" placeholder="请选择事项类型" style="width:100%">
            <el-option v-for="type in types" :key="type.code" :label="type.label" :value="type.code" />
          </el-select>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon title="类型来自平台参数 ARCH_MATTER_TYPE；正式决策发布前必须确定。" />
      </el-form>
      <template #footer>
        <el-button @click="typeDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="actionBusy === 'type'" @click="confirmType">确定</el-button>
      </template>
    </el-dialog>

    <!-- 首次处理弹窗 -->
    <el-dialog v-model="firstHandlingOpen" title="首次处理" width="min(560px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="处理结果" required>
          <el-radio-group v-model="firstForm.outcome">
            <el-radio value="ACCEPTED">确认受理</el-radio>
            <el-radio value="REQUESTED_INFO">要求补充信息</el-radio>
            <el-radio value="REVIEW_MODE_SET">确定评审方式</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="firstForm.outcome !== 'REQUESTED_INFO'" label="评审方式" required>
          <el-radio-group v-model="firstForm.reviewMode">
            <el-radio value="ASYNC">异步评审</el-radio>
            <el-radio value="MEETING">会议评审</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="办理意见">
          <el-input v-model="firstForm.comment" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="firstHandlingOpen = false">取消</el-button>
        <el-button type="primary" :loading="actionBusy === 'first-handling'" @click="submitFirstHandling">提交首次处理</el-button>
      </template>
    </el-dialog>

    <!-- 补充材料弹窗 -->
    <el-dialog v-model="materialOpen" title="补充材料" width="min(560px, 92vw)">
      <el-form label-position="top">
        <el-form-item label="材料类别" required>
          <el-select v-model="materialForm.kind" style="width:100%">
            <el-option v-for="(label, key) in kindLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="materialForm.content" type="textarea" :rows="6" maxlength="20000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="materialOpen = false">取消</el-button>
        <el-button type="primary" :loading="actionBusy === 'material'" @click="submitMaterial">补充</el-button>
      </template>
    </el-dialog>

    <!-- 评审弹窗 -->
    <el-dialog v-model="reviewOpen" :title="editingReview ? `编辑评审 #${editingReview.reviewNo}` : '记录评审'" width="min(720px, 94vw)" :close-on-click-modal="false">
      <el-form label-position="top">
        <div class="decision-form-grid">
          <el-form-item label="评审方式" required>
            <el-radio-group v-model="reviewForm.method">
              <el-radio value="ASYNC">异步</el-radio>
              <el-radio value="MEETING">会议</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="评审时间">
            <el-date-picker v-model="reviewForm.reviewedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
          </el-form-item>
        </div>
        <el-form-item label="参与人">
          <el-select v-model="reviewForm.participantIds" multiple filterable placeholder="搜索并选择参与人" style="width:100%">
            <el-option v-for="user in userOptions" :key="user.id" :label="`${user.displayName}（${user.username}）`" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="过程材料摘要">
          <el-input v-model="reviewForm.processMaterialSummary" type="textarea" :rows="2" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="关键意见">
          <el-input v-model="reviewForm.keyOpinion" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="正式结论（发布前必填）">
          <el-input v-model="reviewForm.conclusionContent" type="textarea" :rows="4" placeholder="记录正式结论内容" />
        </el-form-item>
        <el-form-item label="理由（发布前必填）">
          <el-input v-model="reviewForm.conclusionRationale" type="textarea" :rows="3" placeholder="记录结论理由" />
        </el-form-item>
        <el-form-item label="行动项">
          <div v-for="(item, index) in reviewForm.actionItems" :key="index" class="decision-action-item">
            <el-input v-model="item.content" placeholder="行动项内容" style="flex:1" />
            <el-input v-model="item.ownerName" placeholder="责任人" style="width:140px" />
            <el-button link type="danger" @click="reviewForm.actionItems.splice(index, 1)">移除</el-button>
          </div>
          <el-button link type="primary" @click="reviewForm.actionItems.push({ content: '', ownerUserId: null, ownerName: '' })">+ 添加行动项</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewOpen = false">取消</el-button>
        <el-button type="primary" :loading="reviewSaving" @click="submitReview">保存评审</el-button>
      </template>
    </el-dialog>

    <!-- 发布准备弹窗 -->
    <el-dialog v-model="publicationOpen" title="结论发布准备" width="min(720px, 94vw)" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" show-icon title="发布准备只登记意图；正式结论将在发布审批通过后一次性写入并不可修改。" style="margin-bottom:12px" />
      <el-form label-position="top">
        <el-form-item label="结论来源评审" required>
          <el-select v-model="publicationForm.reviewId" style="width:100%">
            <el-option v-for="review in reviewWithConclusion()" :key="review.id"
                       :label="`评审 #${review.reviewNo}（${formatDateTime(review.reviewedAt)}）`" :value="review.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="替代/部分修订的既有结论">
          <div v-for="(target, index) in publicationForm.targets" :key="index" class="decision-action-item">
            <el-select v-model="target.conclusionId" style="flex:1" placeholder="选择已发布结论">
              <el-option v-for="conclusion in conclusions.filter(c => c.matterId !== matter?.id)" :key="conclusion.conclusionId"
                         :label="`${conclusion.matterNo || '结论'} #${conclusion.conclusionId}（${conclusion.matterTitle || ''}）`"
                         :value="conclusion.conclusionId" />
            </el-select>
            <el-select v-model="target.kind" style="width:150px">
              <el-option label="替代" value="SUPERSEDE" />
              <el-option label="部分修订" value="PARTIALLY_REVISE" />
            </el-select>
            <el-button link type="danger" @click="publicationForm.targets.splice(index, 1)">移除</el-button>
          </div>
          <el-button link type="primary" @click="publicationForm.targets.push({ conclusionId: null as unknown as number, kind: 'SUPERSEDE' })">+ 添加替代目标</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publicationOpen = false">取消</el-button>
        <el-button type="primary" :loading="publicationSaving" @click="submitPublication">保存发布准备</el-button>
      </template>
    </el-dialog>
  </div>
</template>
