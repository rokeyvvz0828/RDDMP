<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, DocumentAdd, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import { deleteTemporaryAttachment, getAttachment, uploadAttachment } from '../../api/attachments'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  createNetworkWorkOrder,
  getNetworkWorkOrder,
  removeNetworkWorkOrderAttachment,
  submitNetworkWorkOrder,
  updateNetworkWorkOrder
} from './network'
import type {
  CertPayload,
  CertType,
  ClbPayload,
  DnsPayload,
  NetworkWorkOrderActionType,
  NetworkWorkOrderDetail,
  NetworkWorkOrderKind,
  NetworkWorkOrderPayload,
  NetworkWorkOrderStatus
} from './networkTypes'
import {
  canEditNetworkWorkOrder,
  certPayloadFromDetail,
  clbPayloadFromDetail,
  dnsPayloadFromDetail,
  networkKindActionOptions
} from './networkUtils'
import { cancelled, httpStatus } from './utils'
import './architecture.css'

interface DraftAttachment { id: number; name: string; bound: boolean }

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
const submitting = ref(false)
const uploadBusy = ref(false)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const statusOf = ref<NetworkWorkOrderStatus>('DRAFT')

const editId = computed(() => {
  const value = Number(route.params.id)
  return Number.isInteger(value) && value > 0 ? value : null
})
const kind = ref<NetworkWorkOrderKind>((route.query.kind as NetworkWorkOrderKind) || 'CERT')
const actionType = ref<NetworkWorkOrderActionType>('OPEN')
const certType = ref<CertType>('SSL')
const clbName = ref('')
const domainName = ref('')
const subjectName = ref('')
const purpose = ref('')
const description = ref('')
const reason = ref('')
const attachments = ref<DraftAttachment[]>([])
const rowVersion = ref(0)

const canSubmit = computed(() => auth.hasPermission('architecture:network-work-order:apply') || auth.hasPermission('architecture:network-work-order:manage'))

const rules: FormRules = {
  clbName: [{ validator: (_rule, value, callback) => {
    if (kind.value !== 'CLB' || (value || '').trim()) callback()
    else callback(new Error('CLB 名称不能为空'))
  }, trigger: 'blur' }],
  domainName: [{ validator: (_rule, value, callback) => {
    if (kind.value !== 'DNS' || (value || '').trim()) callback()
    else callback(new Error('域名不能为空'))
  }, trigger: 'blur' }],
  subjectName: [{ validator: (_rule, value, callback) => {
    if (kind.value !== 'CERT' || (value || '').trim()) callback()
    else callback(new Error('证书主题不能为空'))
  }, trigger: 'blur' }],
  purpose: [{ validator: (_rule, value, callback) => {
    if ((value || '').trim()) callback()
    else callback(new Error('用途不能为空'))
  }, trigger: 'blur' }]
}

function switchKind(next: NetworkWorkOrderKind) {
  if (next === kind.value || editId.value) return
  kind.value = next
  actionType.value = networkKindActionOptions(next)[0]
  certType.value = 'SSL'
}

function buildPayload(): NetworkWorkOrderPayload {
  if (kind.value === 'CLB') {
    const payload: ClbPayload = { clbName: clbName.value.trim(), purpose: purpose.value.trim() }
    if (description.value.trim()) payload.description = description.value.trim()
    return payload
  }
  if (kind.value === 'DNS') {
    const payload: DnsPayload = { domainName: domainName.value.trim(), purpose: purpose.value.trim() }
    if (description.value.trim()) payload.description = description.value.trim()
    return payload
  }
  const payload: CertPayload = { certType: certType.value, subjectName: subjectName.value.trim(), purpose: purpose.value.trim() }
  if (description.value.trim()) payload.description = description.value.trim()
  return payload
}

function fillPayload(payload: NetworkWorkOrderPayload) {
  if (kind.value === 'CLB') {
    const value = clbPayloadFromDetail(payload as unknown as Record<string, unknown>)
    clbName.value = value.clbName
    purpose.value = value.purpose
    description.value = value.description || ''
  } else if (kind.value === 'DNS') {
    const value = dnsPayloadFromDetail(payload as unknown as Record<string, unknown>)
    domainName.value = value.domainName
    purpose.value = value.purpose
    description.value = value.description || ''
  } else {
    const value = certPayloadFromDetail(payload as unknown as Record<string, unknown>)
    certType.value = value.certType
    subjectName.value = value.subjectName
    purpose.value = value.purpose
    description.value = value.description || ''
  }
}

async function loadDetail() {
  if (!editId.value) return
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const detail: NetworkWorkOrderDetail = await getNetworkWorkOrder(editId.value)
    kind.value = detail.workOrder.kind
    actionType.value = detail.workOrder.actionType
    reason.value = detail.workOrder.reason || ''
    rowVersion.value = detail.workOrder.rowVersion
    statusOf.value = detail.workOrder.status
    fillPayload(detail.payload)
    attachments.value = []
    for (const id of detail.attachmentIds) {
      try {
        const item = (await getAttachment(id)).data.data
        attachments.value.push({ id, name: item.fileName, bound: true })
      } catch {
        attachments.value.push({ id, name: `附件 #${id}`, bound: true })
      }
    }
  } catch (error) {
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '工单加载失败')
  } finally {
    loading.value = false
  }
}

async function selectAttachment(file: UploadFile) {
  if (!file.raw || uploadBusy.value) return
  uploadBusy.value = true
  try {
    const item = (await uploadAttachment(file.raw)).data.data
    attachments.value.push({ id: item.id, name: item.fileName, bound: false })
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '附件上传失败'))
  } finally {
    uploadBusy.value = false
  }
}

async function removeAttachment(item: DraftAttachment) {
  try {
    if (item.bound && editId.value) {
      const detail = await removeNetworkWorkOrderAttachment(editId.value, item.id, rowVersion.value)
      rowVersion.value = detail.workOrder.rowVersion
      statusOf.value = detail.workOrder.status
      attachments.value = attachments.value.filter(entry => entry.id !== item.id)
      ElMessage.success('附件已移除')
    } else {
      await deleteTemporaryAttachment(item.id)
      attachments.value = attachments.value.filter(entry => entry.id !== item.id)
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '附件移除失败'))
  }
}

function validate(): boolean {
  if (!kind.value || !actionType.value) {
    ElMessage.warning('请选择工单类型与动作')
    return false
  }
  const payload = buildPayload()
  if (kind.value === 'CLB' && !(payload as ClbPayload).clbName?.trim()) {
    ElMessage.warning('CLB 名称不能为空')
    return false
  }
  if (kind.value === 'DNS' && !(payload as DnsPayload).domainName?.trim()) {
    ElMessage.warning('域名不能为空')
    return false
  }
  if (kind.value === 'CERT' && !(payload as CertPayload).subjectName?.trim()) {
    ElMessage.warning('证书主题不能为空')
    return false
  }
  if (!payload.purpose?.trim()) {
    ElMessage.warning('用途不能为空')
    return false
  }
  return true
}

async function save(submit: boolean) {
  if (!validate() || saving.value || submitting.value) return
  const payload = buildPayload()
  const attachmentIds = attachments.value.map(item => item.id)
  try {
    if (submit) submitting.value = true
    else saving.value = true
    let detail: NetworkWorkOrderDetail
    if (editId.value) {
      detail = await updateNetworkWorkOrder(editId.value, { rowVersion: rowVersion.value, reason: reason.value.trim() || null, payload, attachmentIds })
    } else {
      detail = await createNetworkWorkOrder({ kind: kind.value, actionType: actionType.value, reason: reason.value.trim() || null, payload, attachmentIds })
    }
    if (submit) {
      const submitted = await submitNetworkWorkOrder(detail.workOrder.id, detail.workOrder.rowVersion)
      ElMessage.success(`工单 #${submitted.workOrder.id} 已提交审批`)
      void router.push({ name: 'architecture-network-work-order-detail', params: { id: submitted.workOrder.id } })
    } else {
      ElMessage.success('草稿已保存')
      void router.push({ name: 'architecture-network-work-order-detail', params: { id: detail.workOrder.id } })
    }
  } catch (error) {
    if (!cancelled(error)) ElMessage.error(apiErrorMessage(error, submit ? '提交失败' : '保存失败'))
  } finally {
    saving.value = false
    submitting.value = false
  }
}

onMounted(() => { void loadDetail() })
</script>

<template>
  <main class="architecture-page architecture-change-page">
    <UiPageHeader :title="editId ? `编辑网络专项工单 #${editId}` : '新建网络专项工单'" description="申请字段按工单类型独立校验；平台只登记申请与办理结果，不执行任何外部动作。">
      <template #actions>
        <div class="architecture-page__actions">
          <el-button @click="router.push({ name: 'architecture-network-work-orders' })"><el-icon><ArrowLeft /></el-icon>返回列表</el-button>
        </div>
      </template>
    </UiPageHeader>

    <section v-if="forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无工单查看权限" sub-title="需要 architecture:network-work-order:view、apply 或 manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="工单加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="loadDetail">重新加载</el-button></template>
      </el-result>
    </section>
    <el-form v-else ref="formRef" :model="{ clbName, domainName, subjectName, purpose }" :rules="rules" label-position="top" class="architecture-change-form-shell" v-loading="loading">
      <section class="architecture-change-form">
        <div class="architecture-change-section__heading"><div><span>WORK ORDER</span><h2>工单信息</h2></div></div>
        <div class="architecture-form-grid">
          <el-form-item label="工单类型" required>
            <el-select v-model="kind" :disabled="Boolean(editId)" @change="switchKind">
              <el-option label="CLB（开通/调整）" value="CLB" />
              <el-option label="DNS（新增/变更/注销）" value="DNS" />
              <el-option label="证书（申请/续期/吊销）" value="CERT" />
            </el-select>
          </el-form-item>
          <el-form-item label="工单动作" required>
            <el-select v-model="actionType">
              <el-option v-for="action in networkKindActionOptions(kind)" :key="action" :label="action" :value="action" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="kind === 'CLB'" label="CLB 名称" required prop="clbName" class="is-wide">
            <el-input v-model="clbName" maxlength="200" placeholder="例如：渠道接入CLB" />
          </el-form-item>
          <el-form-item v-else-if="kind === 'DNS'" label="域名" required prop="domainName" class="is-wide">
            <el-input v-model="domainName" maxlength="255" placeholder="例如：demo.example.test" />
          </el-form-item>
          <template v-else>
            <el-form-item label="证书类型" required>
              <el-select v-model="certType">
                <el-option label="SSL 证书" value="SSL" />
                <el-option label="外联证书" value="EXTERNAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="证书主题/域名" required prop="subjectName" class="is-wide">
              <el-input v-model="subjectName" maxlength="255" placeholder="例如：demo.example.test" />
            </el-form-item>
          </template>
          <el-form-item label="用途" required prop="purpose" class="is-wide">
            <el-input v-model="purpose" maxlength="1000" placeholder="申请用途说明" />
          </el-form-item>
          <el-form-item label="详细说明" class="is-wide">
            <el-input v-model="description" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="可选，补充申请或变更内容" />
          </el-form-item>
          <el-form-item label="申请原因/变更说明" class="is-wide">
            <el-input v-model="reason" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="可选" />
          </el-form-item>
        </div>
      </section>

      <section class="architecture-change-form">
        <div class="architecture-change-section__heading">
          <div><span>MATERIALS</span><h2>申请材料</h2><small v-if="kind === 'CERT'">仅允许申请材料或公开证书，禁止上传私钥类文件（key/pem/pfx/p12/jks）。</small></div>
        </div>
        <div class="architecture-network-attachment-editor">
          <el-upload :auto-upload="false" :show-file-list="false" :on-change="selectAttachment" :disabled="uploadBusy">
            <el-button :disabled="uploadBusy" type="primary" plain>
              <el-icon><Loading v-if="uploadBusy" /><DocumentAdd v-else /></el-icon>{{ uploadBusy ? '上传中' : '上传附件' }}
            </el-button>
          </el-upload>
          <div v-if="attachments.length" class="architecture-network-attachment-chips">
            <el-tag v-for="item in attachments" :key="item.id" closable @close="removeAttachment(item)">
              {{ item.name }}{{ item.bound ? ' · 已绑定' : '' }}
            </el-tag>
          </div>
          <p v-else class="architecture-network-attachment-empty">暂无申请材料附件</p>
        </div>
      </section>

      <footer class="architecture-change-sticky-actions">
        <div><strong>草稿与提交</strong><span>提交后进入网络办理人员审批，草稿仅本人可编辑。</span></div>
        <div>
          <el-button :loading="saving" :disabled="submitting" @click="save(false)">保存草稿</el-button>
          <el-button v-if="canSubmit" type="primary" :loading="submitting" :disabled="saving" @click="save(true)">保存并提交</el-button>
        </div>
      </footer>
    </el-form>
  </main>
</template>
