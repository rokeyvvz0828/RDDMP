<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, DocumentAdd, Edit, Loading, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import {
  getAttachment,
  getAttachmentDownload,
  getAttachmentPreview,
  uploadAttachment
} from '../../api/attachments'
import { apiErrorMessage } from '../../api/error'
import {
  decideWorkflowTask,
  getCurrentWorkflowTaskContext,
  type WorkflowTaskAction,
  type WorkflowTaskContext
} from '../../api/workflow'
import { useAuthStore } from '../../stores/auth'
import {
  cancelNetworkWorkOrder,
  getNetworkWorkOrder,
  registerNetworkWorkOrderHandlingResult
} from './network'
import type {
  CertPayload,
  ClbPayload,
  DnsPayload,
  HandlingResultStatus,
  NetworkWorkOrderDetail,
  NetworkWorkOrderPayload,
  NetworkWorkOrderStatus
} from './networkTypes'
import {
  canCancelNetworkWorkOrder,
  canEditNetworkWorkOrder,
  canRegisterResult,
  certTypeLabels,
  handlingResultLabels,
  networkActionLabel,
  networkKindLabels,
  networkStatusLabels,
  networkStatusTone
} from './networkUtils'
import { cancelled, formatDateTime, httpStatus } from './utils'
import './architecture.css'

interface AttachmentView { id: number; name: string }

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const id = Number(route.params.id)
const detail = ref<NetworkWorkOrderDetail | null>(null)
const workflowTask = ref<WorkflowTaskContext | null>(null)
const attachments = ref<AttachmentView[]>([])
const resultAttachments = ref<AttachmentView[]>([])
const loading = ref(true)
const loadError = ref('')
const forbidden = ref(false)
const deciding = ref<WorkflowTaskAction | ''>('')
const cancelling = ref(false)
const registering = ref(false)
const registerBusy = ref(false)

const workOrder = computed(() => detail.value?.workOrder || null)
const canApply = computed(() => auth.hasPermission('architecture:network-work-order:apply') || auth.hasPermission('architecture:network-work-order:manage'))
const canManage = computed(() => auth.hasPermission('architecture:network-work-order:manage'))
const owns = computed(() => workOrder.value?.applicantId === auth.user?.id)
const canEdit = computed(() => Boolean(workOrder.value && canApply.value && owns.value && canEditNetworkWorkOrder(workOrder.value.status)))
const canCancel = computed(() => Boolean(workOrder.value && canApply.value && owns.value && canCancelNetworkWorkOrder(workOrder.value.status)))
const canHandleResult = computed(() => Boolean(workOrder.value && canManage.value && canRegisterResult(workOrder.value.status)))
const allowedDecisions = computed(() => {
  if (!canManage.value || !workflowTask.value?.actionable) return [] as WorkflowTaskAction[]
  return workflowTask.value.allowed_actions.filter(action => ['APPROVE', 'RETURN', 'REJECT'].includes(action))
})
const title = computed(() => workOrder.value
  ? `工单 #${workOrder.value.id} · ${networkKindLabels[workOrder.value.kind]} ${networkActionLabel(workOrder.value.kind, workOrder.value.actionType)}`
  : '网络专项工单')

const resultStatus = ref<HandlingResultStatus>('SUCCESS')
const resultDescription = ref('')
const resultUploads = ref<AttachmentView[]>([])

function payloadKindFields() {
  const payload = detail.value?.payload as Record<string, unknown> | undefined
  if (!payload) return []
  if (workOrder.value?.kind === 'CLB') {
    const value = payload as unknown as ClbPayload
    return [
      { label: 'CLB 名称', value: value.clbName },
      { label: '用途', value: value.purpose },
      { label: '详细说明', value: value.description || '—', wide: true }
    ]
  }
  if (workOrder.value?.kind === 'DNS') {
    const value = payload as unknown as DnsPayload
    return [
      { label: '域名', value: value.domainName },
      { label: '用途', value: value.purpose },
      { label: '详细说明', value: value.description || '—', wide: true }
    ]
  }
  const value = payload as unknown as CertPayload
  return [
    { label: '证书类型', value: certTypeLabels[value.certType] || value.certType },
    { label: '证书主题/域名', value: value.subjectName },
    { label: '用途', value: value.purpose },
    { label: '详细说明', value: value.description || '—', wide: true }
  ]
}

function historyEventLabel(eventType: string) {
  const labels: Record<string, string> = {
    CREATED: '创建草稿',
    UPDATED: '更新草稿',
    SUBMITTED: '提交审批',
    RETURNED: '退回修改',
    REJECTED: '审批拒绝',
    COMPLETED: '办理完成',
    CANCELLED: '取消工单',
    CANCEL_REQUESTED: '请求终止审批',
    RESULT_REGISTERED: '登记办理结果',
    ATTACHMENT_REMOVED: '移除申请材料'
  }
  return labels[eventType] || eventType
}

async function loadAttachments() {
  attachments.value = []
  resultAttachments.value = []
  const order = detail.value?.workOrder
  if (!order) return
  for (const id of order ? detail.value?.attachmentIds || [] : []) {
    try {
      const item = (await getAttachment(id)).data.data
      attachments.value.push({ id, name: item.fileName })
    } catch {
      attachments.value.push({ id, name: `附件 #${id}` })
    }
  }
  for (const id of detail.value?.resultAttachmentIds || []) {
    try {
      const item = (await getAttachment(id)).data.data
      resultAttachments.value.push({ id, name: item.fileName })
    } catch {
      resultAttachments.value.push({ id, name: `凭证 #${id}` })
    }
  }
}

async function loadWorkflow(orderStatus: NetworkWorkOrderStatus) {
  workflowTask.value = null
  if (!canManage.value || orderStatus !== 'IN_REVIEW') return
  try {
    workflowTask.value = (await getCurrentWorkflowTaskContext(
      'architecture_network_work_order',
      String(id)
    )).data.data
  } catch (error) {
    ElMessage.warning(apiErrorMessage(error, '当前审批任务加载失败'))
  }
}

async function load() {
  if (!Number.isInteger(id) || id <= 0) {
    loadError.value = '工单编号无效'
    loading.value = false
    return
  }
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await getNetworkWorkOrder(id)
    detail.value = result
    resultStatus.value = result.workOrder.resultStatus || 'SUCCESS'
    resultDescription.value = ''
    resultUploads.value = []
    await Promise.all([loadAttachments(), loadWorkflow(result.workOrder.status)])
  } catch (error) {
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '工单详情加载失败')
  } finally {
    loading.value = false
  }
}

async function decide(action: WorkflowTaskAction) {
  const task = workflowTask.value
  if (!task || deciding.value) return
  const label = action === 'APPROVE' ? '批准' : action === 'RETURN' ? '退回' : '拒绝'
  try {
    const prompt = await ElMessageBox.prompt(
      action === 'APPROVE' ? '批准表示外部配置已办理并登记。可填写审批意见。' : '请填写处理原因。',
      `${label}工单`,
      {
        confirmButtonText: label,
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: value => action === 'APPROVE' || Boolean(value?.trim()) || '退回或拒绝必须填写原因'
      }
    )
    deciding.value = action
    await decideWorkflowTask(task.task_id, action, prompt.value.trim())
    ElMessage.success(`已${label}，业务状态将由工作流事件更新`)
    await load()
  } catch (error) {
    if (!cancelled(error)) ElMessage.error(apiErrorMessage(error, `${label}失败`))
  } finally {
    deciding.value = ''
  }
}

async function cancelOrder() {
  if (!workOrder.value || cancelling.value) return
  try {
    await ElMessageBox.confirm(
      workOrder.value.status === 'IN_REVIEW' ? '取消后将终止当前审批流程，确认继续？' : '确认取消当前工单？',
      '取消工单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '保留工单' }
    )
    cancelling.value = true
    const result = await cancelNetworkWorkOrder(workOrder.value.id, workOrder.value.rowVersion)
    detail.value = result
    ElMessage.success('工单已取消')
    await loadWorkflow(result.workOrder.status)
  } catch (error) {
    if (!cancelled(error)) ElMessage.error(apiErrorMessage(error, '取消工单失败'))
  } finally {
    cancelling.value = false
  }
}

async function selectResultAttachment(file: { raw?: File }) {
  if (!file.raw || registerBusy.value) return
  registerBusy.value = true
  try {
    const item = (await uploadAttachment(file.raw)).data.data
    resultUploads.value.push({ id: item.id, name: item.fileName })
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '凭证上传失败'))
  } finally {
    registerBusy.value = false
  }
}

function removeResultUpload(item: AttachmentView) {
  resultUploads.value = resultUploads.value.filter(entry => entry.id !== item.id)
}

async function registerResult() {
  const order = workOrder.value
  if (!order || registering.value) return
  if (!resultStatus.value) {
    ElMessage.warning('请选择办理结果状态')
    return
  }
  try {
    registering.value = true
    const result = await registerNetworkWorkOrderHandlingResult(order.id, {
      rowVersion: order.rowVersion,
      resultStatus: resultStatus.value,
      resultDescription: resultDescription.value.trim() || null,
      resultAttachmentIds: resultUploads.value.map(item => item.id)
    })
    detail.value = result
    ElMessage.success('办理结果已登记')
    await load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '办理结果登记失败'))
  } finally {
    registering.value = false
  }
}

function previewAttachment(item: AttachmentView) {
  void getAttachmentPreview(item.id).then(response => {
    window.open(response.data.data.previewUrl, '_blank', 'noopener')
  }).catch(error => ElMessage.error(apiErrorMessage(error, '预览失败')))
}

function downloadAttachment(item: AttachmentView) {
  void getAttachmentDownload(item.id).then(response => {
    window.open(response.data.data.downloadUrl, '_blank', 'noopener')
  }).catch(error => ElMessage.error(apiErrorMessage(error, '下载失败')))
}

function edit() {
  void router.push({ name: 'architecture-network-work-order-edit', params: { id } })
}

onMounted(() => { void load() })
</script>

<template>
  <main class="architecture-page architecture-change-page">
    <UiPageHeader :title="title" description="申请快照、办理结果与业务历史只读展示；审批人不能修改申请内容。">
      <template #actions>
        <div class="architecture-page__actions">
          <el-button @click="router.push({ name: 'architecture-network-work-orders' })"><el-icon><ArrowLeft /></el-icon>返回列表</el-button>
          <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
        </div>
      </template>
    </UiPageHeader>

    <section v-if="forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无工单查看权限" sub-title="需要 architecture:network-work-order:view、apply 或 manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="工单详情加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
    </section>
    <div v-else v-loading="loading" class="architecture-change-detail-shell">
      <section v-if="workOrder" class="architecture-change-hero">
        <div>
          <span>NETWORK WORK ORDER #{{ workOrder.id }}</span>
          <h2>{{ networkKindLabels[workOrder.kind] }} {{ networkActionLabel(workOrder.kind, workOrder.actionType) }}</h2>
          <p>{{ workOrder.reason || '未填写申请原因' }}</p>
        </div>
        <UiStatusTag :value="workOrder.status" :labels="networkStatusLabels" :tone="networkStatusTone(workOrder.status)" />
        <dl>
          <div><dt>主体</dt><dd>{{ workOrder.subject }}</dd></div>
          <div><dt>申请人</dt><dd>#{{ workOrder.applicantId }}</dd></div>
          <div><dt>业务轮次</dt><dd>第 {{ workOrder.currentBusinessRound }} 轮</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatDateTime(workOrder.createdAt) }}</dd></div>
          <div><dt>最后更新</dt><dd>{{ formatDateTime(workOrder.updatedAt) }}</dd></div>
        </dl>
      </section>

      <section v-if="detail" class="architecture-change-section">
        <div class="architecture-change-section__heading"><div><span>APPLICATION SNAPSHOT</span><h2>申请内容</h2></div></div>
        <dl class="architecture-detail-grid">
          <div v-for="field in payloadKindFields()" :key="field.label" :class="{ 'is-wide': field.wide }">
            <dt>{{ field.label }}</dt><dd>{{ field.value }}</dd>
          </div>
        </dl>
      </section>

      <section v-if="detail" class="architecture-change-section">
        <div class="architecture-change-section__heading"><div><span>MATERIALS</span><h2>申请材料</h2></div></div>
        <div v-if="attachments.length" class="architecture-network-attachment-list">
          <article v-for="item in attachments" :key="item.id">
            <span class="architecture-inline-code">{{ item.name }}</span>
            <div>
              <el-button link type="primary" @click="previewAttachment(item)">预览</el-button>
              <el-button link type="primary" @click="downloadAttachment(item)">下载</el-button>
            </div>
          </article>
        </div>
        <p v-else class="architecture-network-attachment-empty">无申请材料</p>
      </section>

      <section v-if="detail && (workOrder?.resultStatus || canHandleResult)" class="architecture-change-section">
        <div class="architecture-change-section__heading"><div><span>HANDLING RESULT</span><h2>办理结果</h2><small>完成仅表示外部实际配置已办理并登记，平台不执行任何外部动作。</small></div></div>
        <dl v-if="workOrder?.resultStatus" class="architecture-detail-grid">
          <div><dt>结果状态</dt><dd>{{ handlingResultLabels[workOrder.resultStatus] }}</dd></div>
          <div><dt>登记时间</dt><dd>{{ formatDateTime(workOrder.updatedAt) }}</dd></div>
          <div class="is-wide"><dt>结果说明</dt><dd>{{ workOrder.resultDescription || '—' }}</dd></div>
        </dl>
        <div v-if="resultAttachments.length" class="architecture-network-attachment-list">
          <article v-for="item in resultAttachments" :key="item.id">
            <span class="architecture-inline-code">{{ item.name }}</span>
            <div>
              <el-button link type="primary" @click="previewAttachment(item)">预览</el-button>
              <el-button link type="primary" @click="downloadAttachment(item)">下载</el-button>
            </div>
          </article>
        </div>

        <div v-if="canHandleResult" class="architecture-network-result-form">
          <el-alert type="info" :closable="false" show-icon title="登记办理结果与凭证；登记不改变工单状态。" />
          <div class="architecture-form-grid">
            <el-form-item label="结果状态" required>
              <el-select v-model="resultStatus">
                <el-option label="办理成功" value="SUCCESS" />
                <el-option label="办理失败" value="FAILED" />
              </el-select>
            </el-form-item>
            <el-form-item label="结果说明" class="is-wide">
              <el-input v-model="resultDescription" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="外部配置办理结果说明" />
            </el-form-item>
            <el-form-item label="凭证附件" class="is-wide">
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="selectResultAttachment" :disabled="registerBusy">
                <el-button :disabled="registerBusy" type="primary" plain><el-icon><Loading v-if="registerBusy" /><DocumentAdd v-else /></el-icon>{{ registerBusy ? '上传中' : '上传凭证' }}</el-button>
              </el-upload>
              <div v-if="resultUploads.length" class="architecture-network-attachment-chips">
                <el-tag v-for="item in resultUploads" :key="item.id" closable @close="removeResultUpload(item)">{{ item.name }}</el-tag>
              </div>
            </el-form-item>
          </div>
          <el-button type="primary" :loading="registering" @click="registerResult">登记办理结果</el-button>
        </div>
      </section>

      <section v-if="detail" class="architecture-change-section">
        <div class="architecture-change-section__heading"><div><span>BUSINESS HISTORY</span><h2>业务历史</h2></div></div>
        <div v-if="detail.history.length" class="architecture-change-timeline">
          <el-timeline>
            <el-timeline-item v-for="item in [...detail.history].sort((a, b) => new Date(a.occurredAt).getTime() - new Date(b.occurredAt).getTime())" :key="item.id" :timestamp="formatDateTime(item.occurredAt)" placement="top">
              <article>
                <header>
                  <strong>{{ historyEventLabel(item.eventType) }}</strong>
                  <span>第 {{ item.businessRound }} 轮 · 操作人 #{{ item.operatorId }}</span>
                </header>
                <div v-if="item.fromStatus || item.toStatus" class="architecture-change-timeline__status">
                  <UiStatusTag v-if="item.fromStatus" :value="item.fromStatus" :labels="networkStatusLabels" :tone="networkStatusTone(item.fromStatus)" />
                  <span v-if="item.fromStatus && item.toStatus">→</span>
                  <UiStatusTag v-if="item.toStatus" :value="item.toStatus" :labels="networkStatusLabels" :tone="networkStatusTone(item.toStatus)" />
                </div>
                <p>{{ item.summary || '未填写说明' }}</p>
              </article>
            </el-timeline-item>
          </el-timeline>
        </div>
        <el-empty v-else description="暂无业务历史" :image-size="64" />
      </section>

      <section v-if="canManage && workOrder?.status === 'IN_REVIEW'" class="architecture-change-section architecture-change-approval">
        <div class="architecture-change-section__heading">
          <div><span>WORKFLOW TASK</span><h2>审批处理</h2><small>审批区只处理工作流动作，不提供业务字段编辑。</small></div>
        </div>
        <el-alert v-if="!workflowTask" type="info" :closable="false" show-icon title="当前没有可由你处理的审批任务，请刷新或检查任务处理人。" />
        <div v-else class="architecture-change-approval__body">
          <dl>
            <div><dt>节点</dt><dd>{{ workflowTask.node_name || workflowTask.task_key }}</dd></div>
            <div><dt>任务状态</dt><dd>{{ workflowTask.task_status }}</dd></div>
            <div><dt>流程实例</dt><dd>#{{ workflowTask.instance_id }}</dd></div>
          </dl>
          <div class="architecture-change-approval__actions">
            <el-button v-if="allowedDecisions.includes('RETURN')" :loading="deciding === 'RETURN'" :disabled="Boolean(deciding)" @click="decide('RETURN')">退回修改</el-button>
            <el-button v-if="allowedDecisions.includes('REJECT')" type="danger" plain :loading="deciding === 'REJECT'" :disabled="Boolean(deciding)" @click="decide('REJECT')">拒绝</el-button>
            <el-button v-if="allowedDecisions.includes('APPROVE')" type="primary" :loading="deciding === 'APPROVE'" :disabled="Boolean(deciding)" @click="decide('APPROVE')">批准</el-button>
          </div>
        </div>
      </section>
    </div>

    <footer v-if="workOrder && (canEdit || canCancel)" class="architecture-change-sticky-actions">
      <div><strong>申请人操作</strong><span>管理权限不会扩大他人草稿的编辑、提交或取消范围。</span></div>
      <div>
        <el-button v-if="canCancel" type="danger" plain :loading="cancelling" @click="cancelOrder">取消工单</el-button>
        <el-button v-if="canEdit" type="primary" @click="edit"><el-icon><Edit /></el-icon>编辑并重新提交</el-button>
      </div>
    </footer>
  </main>
</template>
