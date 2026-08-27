<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowLeft, Check, Document, Lock, Paperclip, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { apiErrorMessage } from '../../api/error'
import { getAttachmentPreview } from '../../api/attachments'
import {
  getReleaseApplication,
  getReleaseApplicationRelatedHistory,
  getReleaseApplicationRound,
  listReleaseApplicationAttachments,
  type ReleaseApplicationDto,
  type ReleaseApplicationStatusCode,
  type ReleaseAttachmentDto,
  type ReleaseRelatedHistoryDto,
  type ReleaseRoundDto
} from '../../api/release'
import { getWorkflowInstanceDetail, type WorkflowMonitorDetail } from '../../api/workflow'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import ReleaseApprovalPanel from './components/ReleaseApprovalPanel.vue'
import './release-prototype.css'

const route = useRoute()
const router = useRouter()
const applicationCode = computed(() => String(route.params.applicationCode || ''))
const taskId = computed(() => positiveInteger(route.query.taskId))
const application = ref<ReleaseApplicationDto | null>(null)
const round = ref<ReleaseRoundDto | null>(null)
const attachments = ref<ReleaseAttachmentDto[]>([])
const relatedHistory = ref<ReleaseRelatedHistoryDto[]>([])
const workflowDetail = ref<WorkflowMonitorDetail | null>(null)
const loading = ref(false)
const loadError = ref('')
const relatedHistoryLoading = ref(false)
const relatedHistoryError = ref('')
const workflowError = ref('')
let relatedHistoryRequestId = 0

const statusLabels: Record<ReleaseApplicationStatusCode, string> = {
  DRAFT: '草稿', IN_REVIEW: '审批中', RETURNED: '已退回', WITHDRAWN: '已撤回',
  CANCELLED: '已取消', RELEASED: '制品准出'
}
const versionLabels = { REGULAR: '常规版本', URGENT: '紧急版本', EMERGENCY: '应急版本' } as const
const artifactLabels = { IMAGE: '镜像', BINARY: '二进制' } as const
const nodeStatusLabels: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
  RETURNED: '已退回',
  CANCELLED: '已取消',
  SENT: '已抄送'
}
const currentNode = computed(() => {
  const pending = workflowDetail.value?.node_states.filter(item => item.status === 'PENDING').map(item => item.node_name || item.task_key)
  return pending?.filter(Boolean).join('、') || (application.value?.status === 'RELEASED' ? '制品准出' : '-')
})

function positiveInteger(value: unknown) {
  const text = Array.isArray(value) ? value[0] : value
  if (typeof text !== 'string' || !/^\d+$/.test(text)) return undefined
  const parsed = Number(text)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
}
function minute(value?: string) {
  if (!value) return '-'
  const source = value.trim().replace(' ', 'T')
  const instant = new Date(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(source) ? source : `${source}Z`)
  if (Number.isNaN(instant.getTime())) return value.replace('T', ' ').slice(0, 16)
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(instant)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find(item => item.type === type)?.value || ''
  return `${part('year')}-${part('month')}-${part('day')} ${part('hour')}:${part('minute')}`
}
function statusTone(value: ReleaseApplicationStatusCode) {
  return value === 'RELEASED' ? 'success' : value === 'IN_REVIEW' ? 'primary'
    : value === 'RETURNED' || value === 'WITHDRAWN' ? 'warning' : 'info'
}
function versionTone(value: ReleaseApplicationDto['versionType']) {
  return value === 'EMERGENCY' ? 'danger' : value === 'URGENT' ? 'warning' : 'info'
}
function nodeTone(value: string) {
  return value === 'COMPLETED' ? 'success' : value === 'PENDING' ? 'primary'
    : value === 'REJECTED' || value === 'RETURNED' ? 'warning' : 'info'
}
function nodeStatusLabel(value: string) { return nodeStatusLabels[value] || value }
function eventLabel(value: string) {
  const labels: Record<string, string> = {
    INSTANCE_STARTED: '流程已启动',
    TASK_APPROVE: '审批同意',
    TASK_APPROVED: '审批同意',
    TASK_REJECT: '审批不通过',
    TASK_REJECTED: '审批不通过',
    TASK_RETURN: '退回修改',
    TASK_RETURNED: '退回修改',
    TASK_ADD_SIGN: '加签',
    TASK_CC: '抄送',
    TASK_TRANSFER: '转办',
    TASK_DELEGATE: '委派',
    INSTANCE_COMPLETED: '流程已完成',
    INSTANCE_TERMINATED: '流程已终止',
    INSTANCE_DELETED: '流程已删除'
  }
  return labels[value] || value
}

function resetRelatedHistory() {
  relatedHistoryRequestId += 1
  relatedHistory.value = []
  relatedHistoryLoading.value = false
  relatedHistoryError.value = ''
}

async function fetchRelatedHistory() {
  if (!application.value || application.value.characteristic !== 'ADDITIONAL') {
    resetRelatedHistory()
    return
  }
  const code = application.value.applicationCode
  const requestId = ++relatedHistoryRequestId
  relatedHistory.value = []
  relatedHistoryError.value = ''
  relatedHistoryLoading.value = true
  try {
    const response = await getReleaseApplicationRelatedHistory(code)
    if (requestId !== relatedHistoryRequestId || applicationCode.value !== code) return
    relatedHistory.value = response.data.data
  } catch (error) {
    if (requestId !== relatedHistoryRequestId || applicationCode.value !== code) return
    relatedHistoryError.value = apiErrorMessage(error, '相关历史申请加载失败，当前申请和审批操作仍可使用。')
  } finally {
    if (requestId === relatedHistoryRequestId) relatedHistoryLoading.value = false
  }
}

async function fetchDetail(showLoading: boolean) {
  if (showLoading) {
    loading.value = true
    resetRelatedHistory()
  }
  loadError.value = ''
  workflowError.value = ''
  try {
    const [applicationResponse, roundResponse, attachmentResponse] = await Promise.all([
      getReleaseApplication(applicationCode.value),
      getReleaseApplicationRound(applicationCode.value),
      listReleaseApplicationAttachments(applicationCode.value)
    ])
    const nextApplication = applicationResponse.data.data
    const shouldLoadRelatedHistory = showLoading || application.value?.applicationCode !== nextApplication.applicationCode
    application.value = nextApplication
    round.value = roundResponse.data.data
    attachments.value = attachmentResponse.data.data
    if (shouldLoadRelatedHistory) void fetchRelatedHistory()
    workflowDetail.value = null
    if (round.value?.workflowInstanceId) {
      try {
        workflowDetail.value = (await getWorkflowInstanceDetail(round.value.workflowInstanceId)).data.data
      } catch (error) {
        workflowError.value = apiErrorMessage(error, '流程进展加载失败，申请业务信息仍可查看。')
      }
    }
  } catch (error) {
    application.value = null
    round.value = null
    attachments.value = []
    resetRelatedHistory()
    workflowDetail.value = null
    loadError.value = apiErrorMessage(error, '版本申请加载失败，请稍后重试。')
  } finally {
    if (showLoading) loading.value = false
  }
}

async function load() {
  await fetchDetail(true)
}

async function previewAttachment(item: ReleaseAttachmentDto) {
  try {
    const result = (await getAttachmentPreview(item.attachmentId)).data.data
    window.open(result.previewUrl, '_blank', 'noopener,noreferrer')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '附件预览失败'))
  }
}

async function handleDecided() {
  const decidedApplicationCode = applicationCode.value
  await load()
  for (let attempt = 0; attempt < 8 && application.value?.status === 'IN_REVIEW'; attempt += 1) {
    await new Promise(resolve => window.setTimeout(resolve, 1000))
    if (applicationCode.value !== decidedApplicationCode) return
    await fetchDetail(false)
  }
}

onMounted(load)
watch(applicationCode, load)
</script>

<template>
  <main class="release-application-detail-page">
    <header class="release-review-header">
      <button type="button" aria-label="返回版本申请" @click="router.back()"><el-icon><ArrowLeft /></el-icon></button>
      <div><span>配置管理 / 版本申请</span><strong>{{ application?.applicationCode || applicationCode }}</strong></div>
      <div v-if="application" class="release-detail-statuses"><UiStatusTag :value="versionLabels[application.versionType]" :tone="versionTone(application.versionType)" /><UiStatusTag :value="statusLabels[application.status]" :tone="statusTone(application.status)" :class="{ 'release-detail-status--released': application.status === 'RELEASED' }" /></div>
    </header>

    <section v-if="loading" class="release-state-panel"><el-skeleton :rows="8" animated /></section>
    <el-result v-else-if="loadError" icon="error" title="版本申请加载失败" :sub-title="loadError"><template #extra><el-button type="primary" :icon="Refresh" @click="load">重新加载</el-button></template></el-result>
    <el-result v-else-if="!application" icon="warning" title="版本申请不存在"><template #extra><el-button type="primary" @click="router.push('/release')">返回配置管理</el-button></template></el-result>

    <section v-else class="release-review-layout">
      <div class="release-review-main">
        <section class="release-review-section">
          <header><span>申请信息</span><small>{{ minute(application.updatedAt) }} 更新</small></header>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="所属项目">{{ application.projectName }}</el-descriptions-item><el-descriptions-item label="申请人">{{ application.requesterName }} / {{ application.requesterDepartment || '-' }}</el-descriptions-item>
            <el-descriptions-item label="物理子系统">{{ application.subsystemName }}（{{ application.subsystemCode }}）</el-descriptions-item><el-descriptions-item label="投产窗口">{{ application.windowName || '制品准出后自动归入承接窗口' }}</el-descriptions-item>
            <el-descriptions-item label="版本类型"><UiStatusTag :value="versionLabels[application.versionType]" :tone="versionTone(application.versionType)" /></el-descriptions-item><el-descriptions-item label="申请特征">{{ application.characteristic === 'ADDITIONAL' ? '追加申请' : '普通申请' }}</el-descriptions-item>
            <el-descriptions-item label="需求编号" :span="2"><template v-if="application.requirementCodes.length"><el-tag v-for="item in application.requirementCodes" :key="item" class="release-inline-tag">{{ item }}</el-tag></template><span v-else>{{ application.emergency ? '应急版本不关联需求编号' : '暂无' }}</span></el-descriptions-item>
            <el-descriptions-item v-if="application.emergencyDescription" label="应急说明" :span="2">{{ application.emergencyDescription }}</el-descriptions-item><el-descriptions-item v-if="application.urgentReason" label="紧急原因" :span="2">{{ application.urgentReason }}</el-descriptions-item>
            <el-descriptions-item label="申请说明" :span="2">{{ application.description || '暂无' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section v-if="application.characteristic === 'ADDITIONAL'" class="release-review-section release-related-history">
          <header><span>相关历史申请</span><small>{{ relatedHistoryLoading ? '正在加载' : `${relatedHistory.length} 张直接相关申请` }}</small></header>
          <div v-if="relatedHistoryLoading" class="release-related-history__loading"><el-skeleton :rows="4" animated /></div>
          <div v-else-if="relatedHistoryError" class="release-related-history__state">
            <strong>相关历史申请加载失败</strong>
            <span>{{ relatedHistoryError }}</span>
            <el-button type="primary" plain :icon="Refresh" @click="fetchRelatedHistory">重新加载</el-button>
          </div>
          <div v-else-if="!relatedHistory.length" class="release-inline-empty">暂无直接相关的历史申请</div>
          <div v-else class="release-related-history__list">
            <article v-for="item in relatedHistory" :key="item.applicationCode">
              <header>
                <div>
                  <el-button link type="primary" @click="router.push(`/release/applications/${encodeURIComponent(item.applicationCode)}`)">{{ item.applicationCode }}</el-button>
                  <small>{{ item.requesterName }} / {{ item.requesterDepartment || '-' }}</small>
                </div>
                <div class="release-related-history__tags">
                  <UiStatusTag :value="versionLabels[item.versionType]" :tone="versionTone(item.versionType)" />
                  <UiStatusTag :value="item.characteristic === 'ADDITIONAL' ? '追加申请' : '普通申请'" :tone="item.characteristic === 'ADDITIONAL' ? 'warning' : 'info'" />
                  <UiStatusTag :value="statusLabels[item.status]" :tone="statusTone(item.status)" />
                </div>
              </header>
              <dl>
                <div><dt>申请时间</dt><dd>{{ minute(item.createdAt) }}</dd></div>
                <div><dt>准出时间</dt><dd>{{ minute(item.approvedAt) }}</dd></div>
                <div class="is-wide"><dt>需求编号</dt><dd><template v-if="item.requirementCodes.length"><el-tag v-for="code in item.requirementCodes" :key="code" size="small" effect="plain">{{ code }}</el-tag></template><span v-else>暂无</span></dd></div>
              </dl>
              <div class="release-related-history__description"><span>申请说明</span><p>{{ item.description || '暂无' }}</p></div>
              <div class="release-related-history__changes">
                <div v-for="(change, index) in item.versionChanges" :key="`${change.deliveryUnitCode}-${change.previousVersion}-${change.currentVersion}-${index}`">
                  <span><strong>{{ change.deliveryUnitName }}</strong><small>{{ change.deliveryUnitCode }}</small></span>
                  <p><code>{{ change.previousVersion || '-' }}</code><b>→</b><code>{{ change.currentVersion || '-' }}</code></p>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section class="release-review-section">
          <header><span>制品登记</span><small>{{ application.deliveries.length }} 个交付单元 · {{ application.fileMedia.length }} 个文件介质</small></header>
          <div class="release-review-units"><article v-for="unit in application.deliveries" :key="unit.deliveryUnitCode"><el-icon><Document /></el-icon><div><strong>{{ unit.deliveryUnitName }}</strong><small>{{ unit.deliveryUnitCode }} · {{ artifactLabels[unit.artifactType] }}</small></div><code>{{ unit.artifactVersion }}</code></article></div>
          <div v-if="application.fileMedia.length" class="release-review-files"><article v-for="file in application.fileMedia" :key="file.id"><el-icon><Document /></el-icon><div><strong>文件介质</strong><code>{{ file.filePath }}</code></div><span>无版本号</span></article></div>
        </section>

        <section class="release-review-section">
          <header><span>附件材料</span><el-icon><Paperclip /></el-icon></header>
          <div v-if="attachments.length" class="release-attachment-list"><article v-for="file in attachments" :key="file.attachmentId"><el-icon><Document /></el-icon><div><strong>{{ file.fileName }}</strong><small>{{ file.category === 'TEST_REPORT' ? '测试报告' : '说明材料' }}</small></div><el-button link type="primary" @click="previewAttachment(file)">预览</el-button></article></div><div v-else class="release-inline-empty">暂无附件</div>
        </section>

        <section class="release-review-section">
          <header><span>审批进展</span><UiStatusTag :value="currentNode" :tone="application.status === 'IN_REVIEW' ? 'primary' : 'info'" /></header>
          <div class="release-flow-summary"><div><span>申请状态</span><strong>{{ statusLabels[application.status] }}</strong></div><div><span>当前节点</span><strong>{{ currentNode }}</strong></div><div><span>审批轮次</span><strong>{{ round ? `第 ${round.roundNo} 轮` : '未提交' }}</strong></div></div>
          <el-alert v-if="workflowError" :title="workflowError" type="warning" :closable="false" show-icon class="release-workflow-runtime" />
          <div v-else-if="workflowDetail" class="release-approval-records">
            <article v-for="node in workflowDetail.node_states" :key="node.id"><span class="release-approval-records__icon" :class="{ done: node.status === 'COMPLETED' }"><el-icon><Check /></el-icon></span><div><header><strong>{{ node.node_name || node.task_key }}</strong><UiStatusTag :value="nodeStatusLabel(node.status)" :tone="nodeTone(node.status)" /></header><p>{{ node.comment || '暂无审批意见' }}</p><small>{{ node.assignee_name || '系统' }} · {{ minute(node.completed_at || node.created_at) }}</small><div v-if="workflowDetail.signatures?.some(signature => signature.task_id === node.id)" class="release-signature"><el-icon><Lock /></el-icon>已使用登录身份完成电子签名</div></div></article>
          </div>
          <div v-else class="release-inline-empty">{{ round ? '暂无可展示的流程节点' : '申请尚未提交审批' }}</div>
        </section>

        <section class="release-review-section">
          <header><span>流程日志</span><small>{{ workflowDetail?.timeline.length || 0 }} 条记录</small></header>
          <el-timeline v-if="workflowDetail?.timeline.length" class="release-audit-timeline"><el-timeline-item v-for="event in workflowDetail.timeline" :key="event.id" :timestamp="minute(event.created_at)" placement="top"><strong>{{ eventLabel(event.event_type) }}</strong><span>{{ event.operator_name || '系统' }}</span><p>{{ event.reason || '无补充说明' }}</p></el-timeline-item></el-timeline><div v-else class="release-inline-empty">暂无流程日志</div>
        </section>
      </div>

      <ReleaseApprovalPanel :application-code="application.applicationCode" :application-status="application.status" :task-id="taskId" @decided="handleDecided" />
    </section>
  </main>
</template>
