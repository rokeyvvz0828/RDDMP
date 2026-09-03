<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import {
  decideWorkflowTask,
  getCurrentWorkflowTaskContext,
  getWorkflowTaskContext,
  type WorkflowTaskAction,
  type WorkflowTaskContext
} from '../../api/workflow'
import { useAuthStore } from '../../stores/auth'
import {
  cancelResourceRequest,
  createResourceRequest,
  fulfillResourceRequest,
  getResourceRequest,
  listEnvironments,
  listResourceRequests,
  loadParameterOptions,
  loadPhysicalSubsystemOptions,
  loadNetworkZoneOptions,
  loadResourceDeploymentUnitOptions,
  loadUserOptions,
  previewAutomatedProvision,
  submitResourceRequest,
  updateResourceRequest
} from './api'
import type {
  DeploymentUnitKind,
  DeploymentUnitOption,
  Environment,
  FulfillInstanceItemPayload,
  FulfillmentMode,
  NetworkZoneOption,
  ParameterOption,
  PhysicalSubsystemOption,
  ResourceRequestDetail,
  ResourceRequestItem,
  ResourceRequestItemPayload,
  ResourceRequestPayload,
  ResourceRequestStatus,
  ResourceRequestSummary,
  ResourceRequestType,
  UserOption
} from './types'
import {
  canCancelResourceRequest,
  cancelled,
  canEditResourceRequest,
  canSubmitResourceRequest,
  deploymentUnitKindLabels,
  formatDateTime,
  httpStatus,
  optionLabel,
  resourceRequestStatusLabels,
  resourceRequestStatusTone,
  resourceRequestTypeLabels
} from './utils'
import './architecture.css'

type UnitShape = {
  deploymentUnitKind?: string | null
}

type ResourceFormItem = ResourceRequestItemPayload & UnitShape & {
  clientId: string
  deploymentUnitCode: string | null
  deploymentUnitName: string | null
  deploymentUnitDescription: string | null
  networkZoneName: string | null
}

type ResourceForm = Omit<ResourceRequestPayload, 'items'> & {
  items: ResourceFormItem[]
}

const DEFAULT_SERVER_TYPE_CODE = 'architecture.server-type.container'
const RESOURCE_REQUEST_BUSINESS_TYPE = 'architecture_resource_request'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const rows = ref<ResourceRequestSummary[]>([])
const environments = ref<Environment[]>([])
const users = ref<UserOption[]>([])
const physicalOptions = ref<PhysicalSubsystemOption[]>([])
const deploymentUnitOptions = ref<DeploymentUnitOption[]>([])
const networkZoneOptions = ref<NetworkZoneOption[]>([])
const serverTypes = ref<ParameterOption[]>([])
const deploymentPlatforms = ref<ParameterOption[]>([])
const disasterRecoveryModes = ref<ParameterOption[]>([])
const systemLevels = ref<ParameterOption[]>([])
const jdkVersions = ref<ParameterOption[]>([])
const middlewares = ref<ParameterOption[]>([])
const operatingSystems = ref<ParameterOption[]>([])
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const optionLoading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const filters = reactive({
  status: '' as ResourceRequestStatus | '',
  environmentId: null as number | null,
  physicalSubsystemId: null as number | null
})

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<ResourceRequestDetail | null>(null)
const workflowTask = ref<WorkflowTaskContext | null>(null)
const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSubmitting = ref(false)
const formError = ref('')
const editingId = ref<number | null>(null)
const selectedItemId = ref('')
const acceptedPhysicalSubsystemId = ref<number | null>(null)
const deploymentUnitLoading = ref(false)
const deploymentUnitLoadError = ref('')
const expandedItemExtras = ref<string[]>([])
const deciding = ref<WorkflowTaskAction | ''>('')
const form = reactive<ResourceForm>({
  physicalSubsystemId: null,
  environmentId: null,
  contactUserId: null,
  requestType: '',
  reason: null,
  items: [],
  rowVersion: null
})

let listSequence = 0
let detailSequence = 0
let optionSequence = 0
let resourceItemSequence = 0
let initialFormSnapshot = ''
let allowFormClose = false
const statusOptions: ResourceRequestStatus[] = ['DRAFT', 'IN_REVIEW', 'RETURNED', 'APPROVED', 'FULFILLED', 'DIFF_FULFILLED', 'REJECTED', 'CANCELLED']
const typeOptions: ResourceRequestType[] = ['INITIAL', 'EXPANSION', 'SHRINK', 'ADJUSTMENT']
const canView = computed(() => ['architecture:resource-request:view', 'architecture:resource-request:apply', 'architecture:resource-request:manage', 'architecture:view', 'architecture:apply', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canApply = computed(() => ['architecture:resource-request:apply', 'architecture:resource-request:manage', 'architecture:apply', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canManage = computed(() => auth.hasPermission('architecture:resource-request:manage') || auth.hasPermission('architecture:manage'))
const hasNext = computed(() => rows.value.length === pageSize.value)
const scopeLabel = computed(() => canManage.value ? '当前租户全部资源申请' : '仅显示本人发起的资源申请')
const activeEnvironments = computed(() => environments.value.filter(item => item.status === 'ACTIVE'))
const selectedPhysical = computed(() => physicalOptions.value.find(item => item.id === form.physicalSubsystemId) ?? null)
const currentItem = computed(() => form.items.find(item => item.clientId === selectedItemId.value) ?? form.items[0] ?? null)
const allowedDecisions = computed(() => {
  if (!canManage.value || !workflowTask.value?.actionable) return [] as WorkflowTaskAction[]
  return workflowTask.value.allowed_actions.filter(action => ['APPROVE', 'RETURN', 'REJECT'].includes(action))
})

function selectedDeploymentUnit(item: ResourceFormItem) {
  return deploymentUnitOptions.value.find(unit => unit.id === item.deploymentUnitId) ?? null
}

function nextResourceItemId() {
  resourceItemSequence += 1
  return `resource-item-${resourceItemSequence}`
}

function routeRequestId() {
  const raw = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  if (typeof raw !== 'string' || !/^\d+$/.test(raw)) return null
  const id = Number(raw)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

function routeTaskId() {
  const raw = Array.isArray(route.query.taskId) ? route.query.taskId[0] : route.query.taskId
  if (typeof raw !== 'string' || !/^\d+$/.test(raw)) return null
  const id = Number(raw)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

function owns(row: ResourceRequestSummary) {
  return row.applicantId === auth.user?.id
}

function text(value: string | null | undefined) {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

function amount(value: number | null | undefined) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}

function integerAmount(value: number | null | undefined) {
  return Math.max(0, Math.trunc(amount(value)))
}

function integerValue(value: number | null | undefined) {
  return Math.trunc(amount(value))
}

function hasFraction(value: number | null | undefined) {
  const numberValue = amount(value)
  return !Number.isInteger(numberValue)
}

function displayText(value: string | number | null | undefined) {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

function networkZoneName(id: number | null | undefined) {
  if (!id) return null
  return networkZoneOptions.value.find(zone => zone.id === id)?.name ?? null
}

function displayNetworkZone(item: { networkZoneId?: number | null; networkZoneName?: string | null; networkZone?: string | null }) {
  return item.networkZoneName || networkZoneName(item.networkZoneId) || item.networkZone || '—'
}

function syncNetworkZoneText(item: { networkZoneId?: number | null; networkZoneName?: string | null; networkZone?: string | null }) {
  item.networkZoneName = networkZoneName(item.networkZoneId) ?? null
  item.networkZone = item.networkZoneName
}

function displayAmount(value: number | null | undefined, unit = '') {
  const formatted = integerAmount(value).toLocaleString('zh-CN')
  return unit ? `${formatted} ${unit}` : formatted
}

function boolLabel(value: boolean) {
  return value ? '是' : '否'
}

function userLabel(id: number | null | undefined) {
  if (!id) return '—'
  const user = users.value.find(item => item.id === id)
  if (user) return `${user.displayName}（${user.username}）`
  if (auth.user?.id === id) return auth.user.displayName || auth.user.username || `用户 #${id}`
  return `用户 #${id}`
}

function requestTypeLabel(value: ResourceRequestType | string | null | undefined) {
  if (!value) return '—'
  return resourceRequestTypeLabels[value as ResourceRequestType] ?? value
}

function isDatabaseRecord(item: UnitShape) {
  return item.deploymentUnitKind === 'DATABASE'
}

function serverTypeLabel(code: string | null | undefined) {
  return optionLabel(serverTypes.value, code)
}

function deploymentPlatformLabel(code: string | null | undefined) {
  return optionLabel(deploymentPlatforms.value, code)
}

function disasterRecoveryLabel(code: string | null | undefined) {
  return optionLabel(disasterRecoveryModes.value, code)
}

function systemLevelLabel(code: string | null | undefined) {
  return optionLabel(systemLevels.value, code)
}

function jdkVersionLabel(code: string | null | undefined) {
  return optionLabel(jdkVersions.value, code)
}

function middlewareLabel(code: string | null | undefined) {
  return optionLabel(middlewares.value, code)
}

function operatingSystemLabel(code: string | null | undefined) {
  return optionLabel(operatingSystems.value, code)
}

function defaultServerTypeCode() {
  return serverTypes.value.find(item => item.code === DEFAULT_SERVER_TYPE_CODE)?.code
    ?? serverTypes.value[0]?.code
    ?? DEFAULT_SERVER_TYPE_CODE
}

function syncDeploymentUnit(item: ResourceFormItem, resetDemand = true) {
  const unit = selectedDeploymentUnit(item)
  if (!unit) {
    item.deploymentUnitCode = null
    item.deploymentUnitName = null
    item.deploymentUnitKind = null
    item.deploymentUnitDescription = null
    item.networkZoneId = null
    item.networkZoneName = null
    return
  }
  item.deploymentUnitCode = unit.code
  item.deploymentUnitName = unit.name
  item.deploymentUnitKind = unit.kind
  item.deploymentUnitDescription = unit.description ?? null
  if (resetDemand) {
    item.networkZoneId = unit.defaultNetworkZoneId ?? null
    item.networkZoneName = unit.defaultNetworkZoneName ?? null
    item.networkZone = unit.defaultNetworkZoneName ?? null
  }
  if (resetDemand) normalizeDemandFields(item)
}

function normalizeDemandFields(item: ResourceFormItem) {
  if (isDatabaseRecord(item)) {
    item.fileStorageGb = 0
    item.networkZoneId = null
    item.networkZoneName = null
    item.networkZone = null
    item.serverType = null
    item.cpuCores = 0
    item.memoryGb = 0
    item.appWebGroupCount = 0
    item.plannedNodeCount = 0
    item.sidecarCpuCores = 0
    item.sidecarMemoryGb = 0
    item.hasSidecar = false
    item.jdkVersion = null
    item.middleware = null
    item.operatingSystem = null
    item.extraCbsGb = 0
    item.localDiskGb = 0
    item.needsNft = false
    item.needsFserver = false
    item.needsJobexecutor = false
    return
  }
  item.databaseStorageGb = 0
  item.databaseName = null
  item.databaseVersion = null
  if (!item.serverType) item.serverType = defaultServerTypeCode()
  syncSidecarFields(item)
}

function effectiveSidecarCpu(item: ResourceFormItem | ResourceRequestItem) {
  return item.hasSidecar ? amount(item.sidecarCpuCores) : 0
}

function effectiveSidecarMemory(item: ResourceFormItem | ResourceRequestItem) {
  return item.hasSidecar ? amount(item.sidecarMemoryGb) : 0
}

function itemBaseMemory(item: ResourceFormItem | ResourceRequestItem) {
  return amount(item.memoryGb) * integerAmount(item.plannedNodeCount)
}

function syncSidecarFields(item: ResourceFormItem) {
  if (!item.hasSidecar) {
    item.sidecarCpuCores = 0
    item.sidecarMemoryGb = 0
  }
}

function itemTotalCpu(item: ResourceFormItem | ResourceRequestItem) {
  return amount(item.cpuCores) * integerAmount(item.plannedNodeCount) + effectiveSidecarCpu(item)
}

function itemTotalMemory(item: ResourceFormItem | ResourceRequestItem) {
  return itemBaseMemory(item) + effectiveSidecarMemory(item)
}

function itemSidecarMemoryRatio(item: ResourceFormItem | ResourceRequestItem) {
  const baseMemory = itemBaseMemory(item)
  const sidecar = effectiveSidecarMemory(item)
  if (!item.hasSidecar || !baseMemory || !sidecar) return '—'
  const total = baseMemory + sidecar
  return `${((sidecar / total) * 100).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}%`
}

function itemHasDemand(item: ResourceFormItem) {
  if (isDatabaseRecord(item)) {
    return integerAmount(item.databaseStorageGb) > 0 || Boolean(text(item.databaseName)) || Boolean(text(item.databaseVersion))
  }
  return integerAmount(item.cpuCores) > 0
    || integerAmount(item.memoryGb) > 0
    || integerAmount(item.fileStorageGb) > 0
    || integerAmount(effectiveSidecarCpu(item)) > 0
    || integerAmount(effectiveSidecarMemory(item)) > 0
    || integerAmount(item.extraCbsGb) > 0
    || integerAmount(item.localDiskGb) > 0
    || integerAmount(item.appWebGroupCount) > 0
    || integerAmount(item.plannedNodeCount) > 0
    || item.needsNft
    || item.needsFserver
    || item.needsJobexecutor
}

async function loadOptions() {
  optionLoading.value = true
  try {
    const [
      environmentRows,
      physicalRows,
      userRows,
      serverTypeRows,
      platformRows,
      disasterRows,
      systemLevelRows,
      networkZoneRows,
      jdkRows,
      middlewareRows,
      operatingSystemRows
    ] = await Promise.all([
      listEnvironments({ limit: 200, offset: 0 }),
      loadPhysicalSubsystemOptions('', 100),
      loadUserOptions('physical-subsystem', '', 100),
      loadParameterOptions('physical-subsystem', 'ARCH_SERVER_TYPE'),
      loadParameterOptions('physical-subsystem', 'ARCH_DEPLOYMENT_PLATFORM'),
      loadParameterOptions('physical-subsystem', 'ARCH_DISASTER_RECOVERY_MODE'),
      loadParameterOptions('physical-subsystem', 'ARCH_SYSTEM_LEVEL'),
      loadNetworkZoneOptions(true),
      loadParameterOptions('physical-subsystem', 'ARCH_JDK_VERSION'),
      loadParameterOptions('physical-subsystem', 'ARCH_MIDDLEWARE'),
      loadParameterOptions('physical-subsystem', 'ARCH_OPERATING_SYSTEM')
    ])
    environments.value = environmentRows
    physicalOptions.value = physicalRows
    users.value = userRows
    serverTypes.value = serverTypeRows
    deploymentPlatforms.value = platformRows
    disasterRecoveryModes.value = disasterRows
    systemLevels.value = systemLevelRows
    networkZoneOptions.value = networkZoneRows
    jdkVersions.value = jdkRows
    middlewares.value = middlewareRows
    operatingSystems.value = operatingSystemRows
  } catch (error) {
    if (httpStatus(error) !== 403) ElMessage.warning(apiErrorMessage(error, '选项加载失败'))
  } finally {
    optionLoading.value = false
  }
}

async function load() {
  if (!canView.value) return
  const request = ++listSequence
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listResourceRequests({
      status: filters.status,
      environmentId: filters.environmentId,
      physicalSubsystemId: filters.physicalSubsystemId,
      limit: pageSize.value,
      offset: (page.value - 1) * pageSize.value
    })
    if (request === listSequence) rows.value = result
  } catch (error) {
    if (request !== listSequence) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '资源申请列表加载失败')
  } finally {
    if (request === listSequence) loading.value = false
  }
}

async function loadDeploymentUnits(physicalSubsystemId: number | null) {
  const request = ++optionSequence
  deploymentUnitOptions.value = []
  deploymentUnitLoadError.value = ''
  if (!physicalSubsystemId) {
    deploymentUnitLoading.value = false
    return
  }
  deploymentUnitLoading.value = true
  try {
    const result = await loadResourceDeploymentUnitOptions(physicalSubsystemId, 100)
    if (request === optionSequence) deploymentUnitOptions.value = result
  } catch (error) {
    if (request === optionSequence) {
      deploymentUnitLoadError.value = apiErrorMessage(error, '部署单元选项加载失败')
      ElMessage.warning(deploymentUnitLoadError.value)
    }
  } finally {
    if (request === optionSequence) deploymentUnitLoading.value = false
  }
}

function retryDeploymentUnits() {
  void loadDeploymentUnits(acceptedPhysicalSubsystemId.value)
}

async function refresh() {
  await Promise.all([load(), loadOptions()])
  if (!loadError.value && !forbidden.value) ElMessage.success('列表已刷新')
}

function search() {
  page.value = 1
  void load()
}

function reset() {
  Object.assign(filters, { status: '', environmentId: null, physicalSubsystemId: null })
  page.value = 1
  void load()
}

async function loadWorkflow(request: ResourceRequestSummary) {
  workflowTask.value = null
  if (!canManage.value || request.status !== 'IN_REVIEW') return
  try {
    const taskId = routeTaskId()
    const next = taskId
      ? (await getWorkflowTaskContext(taskId)).data.data
      : (await getCurrentWorkflowTaskContext(RESOURCE_REQUEST_BUSINESS_TYPE, String(request.id))).data.data
    if (!next) return
    if (next.business_type !== RESOURCE_REQUEST_BUSINESS_TYPE || next.business_key !== String(request.id)) return
    workflowTask.value = next
  } catch (error) {
    ElMessage.warning(apiErrorMessage(error, '当前审批任务加载失败'))
  }
}

async function loadDetail(requestId: number, fallback?: ResourceRequestSummary) {
  const request = ++detailSequence
  detailOpen.value = true
  detailLoading.value = true
  workflowTask.value = null
  detail.value = fallback ? { request: fallback, items: [], history: [] } : null
  try {
    const result = await getResourceRequest(requestId)
    if (request === detailSequence) {
      detail.value = result
      await loadWorkflow(result.request)
    }
  } catch (error) {
    if (request === detailSequence) ElMessage.error(apiErrorMessage(error, '资源申请详情加载失败'))
  } finally {
    if (request === detailSequence) detailLoading.value = false
  }
}

async function showDetail(row: ResourceRequestSummary) {
  await loadDetail(row.id, row)
}

function openRouteDetail() {
  const id = routeRequestId()
  if (!id || !canView.value) return
  void loadDetail(id)
}

function handleDetailClosed() {
  workflowTask.value = null
  if (routeRequestId()) void router.replace({ name: 'architecture-resource-requests' })
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    physicalSubsystemId: physicalOptions.value[0]?.id ?? null,
    environmentId: activeEnvironments.value[0]?.id ?? null,
    contactUserId: auth.user?.id ?? users.value[0]?.id ?? null,
    requestType: 'INITIAL',
    reason: null,
    rowVersion: null
  })
  form.items = [blankItem()]
  selectedItemId.value = form.items[0].clientId
  acceptedPhysicalSubsystemId.value = form.physicalSubsystemId
  expandedItemExtras.value = []
  formMode.value = 'create'
  formError.value = ''
  deploymentUnitLoadError.value = ''
  allowFormClose = false
  initialFormSnapshot = formSnapshot()
  formOpen.value = true
  void loadDeploymentUnits(form.physicalSubsystemId)
}

async function openEdit(row: ResourceRequestSummary) {
  try {
    const result = await getResourceRequest(row.id)
    detail.value = result
    editingId.value = row.id
    Object.assign(form, {
      physicalSubsystemId: result.request.physicalSubsystemId,
      environmentId: result.request.environmentId,
      contactUserId: result.request.contactUserId,
      requestType: result.request.requestType,
      reason: result.request.reason,
      rowVersion: result.request.rowVersion
    })
    form.items = result.items.map(item => ({
      clientId: nextResourceItemId(),
      deploymentUnitId: item.deploymentUnitId,
      deploymentUnitCode: item.deploymentUnitCode,
      deploymentUnitName: item.deploymentUnitName,
      deploymentUnitKind: item.deploymentUnitKind,
      deploymentUnitDescription: item.deploymentUnitDescription,
      databaseStorageGb: Number(item.databaseStorageGb),
      fileStorageGb: Number(item.fileStorageGb),
      networkZoneId: item.networkZoneId,
      networkZoneName: item.networkZoneName,
      networkZone: item.networkZone,
      serverType: item.serverType,
      cpuCores: Number(item.cpuCores),
      memoryGb: Number(item.memoryGb),
      appWebGroupCount: item.appWebGroupCount,
      plannedNodeCount: item.plannedNodeCount,
      sidecarCpuCores: Number(item.sidecarCpuCores),
      sidecarMemoryGb: Number(item.sidecarMemoryGb),
      hasSidecar: item.hasSidecar,
      databaseName: item.databaseName,
      databaseVersion: item.databaseVersion,
      jdkVersion: item.jdkVersion,
      middleware: item.middleware,
      operatingSystem: item.operatingSystem,
      extraCbsGb: Number(item.extraCbsGb),
      localDiskGb: Number(item.localDiskGb),
      needsNft: item.needsNft,
      needsFserver: item.needsFserver,
      needsJobexecutor: item.needsJobexecutor,
      remark: item.remark
    }))
    if (!form.items.length) form.items = [blankItem()]
    form.items.forEach(syncSidecarFields)
    selectedItemId.value = form.items[0].clientId
    acceptedPhysicalSubsystemId.value = form.physicalSubsystemId
    expandedItemExtras.value = []
    formMode.value = 'edit'
    formError.value = ''
    deploymentUnitLoadError.value = ''
    allowFormClose = false
    initialFormSnapshot = formSnapshot()
    formOpen.value = true
    void loadDeploymentUnits(form.physicalSubsystemId)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '资源申请草稿加载失败'))
  }
}

function blankItem(): ResourceFormItem {
  return {
    clientId: nextResourceItemId(),
    deploymentUnitId: null,
    deploymentUnitCode: null,
    deploymentUnitName: null,
    deploymentUnitKind: null,
    deploymentUnitDescription: null,
    databaseStorageGb: 0,
    fileStorageGb: 0,
    networkZoneId: null,
    networkZoneName: null,
    networkZone: null,
    serverType: defaultServerTypeCode(),
    cpuCores: 0,
    memoryGb: 0,
    appWebGroupCount: 0,
    plannedNodeCount: 0,
    sidecarCpuCores: 0,
    sidecarMemoryGb: 0,
    hasSidecar: false,
    databaseName: null,
    databaseVersion: null,
    jdkVersion: null,
    middleware: null,
    operatingSystem: null,
    extraCbsGb: 0,
    localDiskGb: 0,
    needsNft: false,
    needsFserver: false,
    needsJobexecutor: false,
    remark: null
  }
}

function addItem() {
  if (formSubmitting.value) return
  const item = blankItem()
  form.items.push(item)
  selectedItemId.value = item.clientId
  formError.value = ''
}

function selectItem(clientId: string) {
  if (formSubmitting.value || !form.items.some(item => item.clientId === clientId)) return
  selectedItemId.value = clientId
}

function removeItem(clientId: string) {
  if (formSubmitting.value || form.items.length <= 1) return
  const index = form.items.findIndex(item => item.clientId === clientId)
  if (index < 0) return
  const removingSelected = selectedItemId.value === clientId
  form.items.splice(index, 1)
  expandedItemExtras.value = expandedItemExtras.value.filter(name => name !== `extra-${clientId}`)
  if (removingSelected) selectedItemId.value = (form.items[index] ?? form.items[index - 1]).clientId
  formError.value = ''
}

function itemHasContent(item: ResourceFormItem) {
  return Boolean(item.deploymentUnitId)
    || itemHasDemand(item)
    || Boolean(item.networkZoneId)
    || Boolean(text(item.databaseName))
    || Boolean(text(item.databaseVersion))
    || Boolean(text(item.jdkVersion))
    || Boolean(text(item.middleware))
    || Boolean(text(item.operatingSystem))
    || Boolean(text(item.remark))
    || item.hasSidecar
}

function canResetPhysicalSubsystemDirectly() {
  return form.items.length === 1 && !itemHasContent(form.items[0])
}

function resetItemsForPhysicalSubsystem(physicalSubsystemId: number | null) {
  acceptedPhysicalSubsystemId.value = physicalSubsystemId
  const item = blankItem()
  form.items = [item]
  selectedItemId.value = item.clientId
  expandedItemExtras.value = []
  formError.value = ''
  void loadDeploymentUnits(physicalSubsystemId)
}

async function handlePhysicalSubsystemChange(physicalSubsystemId: number | null) {
  if (formSubmitting.value) {
    form.physicalSubsystemId = acceptedPhysicalSubsystemId.value
    return
  }
  if (physicalSubsystemId === acceptedPhysicalSubsystemId.value) return
  if (canResetPhysicalSubsystemDirectly()) {
    resetItemsForPhysicalSubsystem(physicalSubsystemId)
    return
  }
  try {
    await ElMessageBox.confirm(
      '切换物理子系统将清空当前全部申请项，并重新加载部署单元候选。是否继续？',
      '切换物理子系统',
      { confirmButtonText: '清空并切换', cancelButtonText: '保留当前内容', type: 'warning' }
    )
    resetItemsForPhysicalSubsystem(physicalSubsystemId)
  } catch {
    form.physicalSubsystemId = acceptedPhysicalSubsystemId.value
  }
}

function itemValidationError(item: ResourceFormItem) {
  if (!item.deploymentUnitId) return '请选择部署单元'
  if ([
    item.databaseStorageGb,
    item.fileStorageGb,
    item.cpuCores,
    item.memoryGb,
    item.sidecarCpuCores,
    item.sidecarMemoryGb,
    item.extraCbsGb,
    item.localDiskGb
  ].some(value => amount(value) < 0) || integerValue(item.appWebGroupCount) < 0 || integerValue(item.plannedNodeCount) < 0) {
    return '资源容量、组数和节点数不能为负数'
  }
  if ([
    item.databaseStorageGb,
    item.fileStorageGb,
    item.cpuCores,
    item.memoryGb,
    item.sidecarCpuCores,
    item.sidecarMemoryGb,
    item.extraCbsGb,
    item.localDiskGb
  ].some(hasFraction)) {
    return '资源容量、CPU、内存和存储需求必须为整数'
  }
  if (!itemHasDemand(item)) {
    return isDatabaseRecord(item) ? 'DB 明细至少填写数据库存储需求、数据库或数据库版本' : '非 DB 明细至少填写一项资源容量或附加需求'
  }
  if (!isDatabaseRecord(item) && !item.networkZoneId) return '非 DB 明细必须选择网络分区'
  return null
}

type ItemCompletionState = 'UNSELECTED' | 'INCOMPLETE' | 'COMPLETE'

function itemCompletionState(item: ResourceFormItem): ItemCompletionState {
  if (!item.deploymentUnitId) return 'UNSELECTED'
  return itemValidationError(item) ? 'INCOMPLETE' : 'COMPLETE'
}

function itemCompletionLabel(item: ResourceFormItem) {
  const state = itemCompletionState(item)
  if (state === 'COMPLETE') return '已填写'
  if (state === 'INCOMPLETE') return '待完善'
  return '未选择'
}

function validateForm() {
  if (!form.physicalSubsystemId || !form.environmentId || !form.contactUserId || !form.requestType) {
    return '请选择物理子系统、具体环境、资源申请联系人和申请类型'
  }
  if (!form.items.length) return '请至少添加一个申请项'
  for (const [index, item] of form.items.entries()) {
    const error = itemValidationError(item)
    if (!error) continue
    selectedItemId.value = item.clientId
    return `申请项 ${index + 1}：${error}`
  }
  return null
}

function requestItemPayload(item: ResourceFormItem): ResourceRequestItemPayload {
  const database = isDatabaseRecord(item)
  const hasSidecar = !database && Boolean(item.hasSidecar)
  return {
    deploymentUnitId: item.deploymentUnitId,
    databaseStorageGb: database ? integerAmount(item.databaseStorageGb) : 0,
    fileStorageGb: database ? 0 : integerAmount(item.fileStorageGb),
    networkZoneId: database ? null : item.networkZoneId,
    networkZone: database ? null : (networkZoneName(item.networkZoneId) || text(item.networkZone)),
    serverType: database ? null : (text(item.serverType) || defaultServerTypeCode()),
    cpuCores: database ? 0 : integerAmount(item.cpuCores),
    memoryGb: database ? 0 : integerAmount(item.memoryGb),
    appWebGroupCount: database ? 0 : integerAmount(item.appWebGroupCount),
    plannedNodeCount: database ? 0 : integerAmount(item.plannedNodeCount),
    sidecarCpuCores: hasSidecar ? integerAmount(item.sidecarCpuCores) : 0,
    sidecarMemoryGb: hasSidecar ? integerAmount(item.sidecarMemoryGb) : 0,
    hasSidecar,
    databaseName: database ? text(item.databaseName) : null,
    databaseVersion: database ? text(item.databaseVersion) : null,
    jdkVersion: database ? null : text(item.jdkVersion),
    middleware: database ? null : text(item.middleware),
    operatingSystem: database ? null : text(item.operatingSystem),
    extraCbsGb: database ? 0 : integerAmount(item.extraCbsGb),
    localDiskGb: database ? 0 : integerAmount(item.localDiskGb),
    needsNft: database ? false : Boolean(item.needsNft),
    needsFserver: database ? false : Boolean(item.needsFserver),
    needsJobexecutor: database ? false : Boolean(item.needsJobexecutor),
    remark: text(item.remark)
  }
}

function formSnapshot() {
  return JSON.stringify({
    physicalSubsystemId: form.physicalSubsystemId,
    environmentId: form.environmentId,
    contactUserId: form.contactUserId,
    requestType: form.requestType,
    reason: text(form.reason),
    rowVersion: form.rowVersion,
    items: form.items.map(requestItemPayload)
  })
}

function formIsDirty() {
  return Boolean(initialFormSnapshot) && formSnapshot() !== initialFormSnapshot
}

async function confirmDiscardForm() {
  try {
    await ElMessageBox.confirm(
      '当前资源申请有未保存修改，关闭后这些内容将丢失。',
      '放弃未保存修改？',
      { confirmButtonText: '放弃并关闭', cancelButtonText: '继续编辑', type: 'warning' }
    )
    return true
  } catch {
    return false
  }
}

async function handleFormBeforeClose(done: () => void) {
  if (formSubmitting.value) {
    ElMessage.warning('资源申请正在保存，请稍候')
    return
  }
  if (allowFormClose) {
    allowFormClose = false
    done()
    return
  }
  if (!formIsDirty() || await confirmDiscardForm()) done()
}

async function closeForm() {
  if (formSubmitting.value) return
  if (formIsDirty() && !await confirmDiscardForm()) return
  allowFormClose = true
  formOpen.value = false
}

async function submitForm() {
  if (formSubmitting.value) return
  const validation = validateForm()
  if (validation) {
    formError.value = validation
    return
  }
  formSubmitting.value = true
  formError.value = ''
  try {
    const payload: ResourceRequestPayload = {
      physicalSubsystemId: form.physicalSubsystemId,
      environmentId: form.environmentId,
      contactUserId: form.contactUserId,
      requestType: form.requestType,
      reason: text(form.reason),
      rowVersion: form.rowVersion,
      items: form.items.map(requestItemPayload)
    }
    const saved = formMode.value === 'create'
      ? await createResourceRequest(payload)
      : await updateResourceRequest(editingId.value!, payload)
    detail.value = saved
    ElMessage.success(formMode.value === 'create' ? '资源申请草稿已创建' : '资源申请草稿已更新')
    allowFormClose = true
    formOpen.value = false
    void load()
  } catch (error) {
    formError.value = apiErrorMessage(error, '保存资源申请失败')
  } finally {
    formSubmitting.value = false
  }
}

async function confirmSubmit(row: ResourceRequestSummary) {
  try {
    await ElMessageBox.confirm(`提交「${row.requestNo}」进入资源申请审批？`, '提交审批', { confirmButtonText: '提交', cancelButtonText: '取消', type: 'warning' })
    const result = await submitResourceRequest(row.id, row.rowVersion)
    detail.value = result
    ElMessage.success('资源申请已提交')
    void load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '提交资源申请失败'))
  }
}

async function confirmCancel(row: ResourceRequestSummary) {
  try {
    await ElMessageBox.confirm(`取消「${row.requestNo}」？`, '取消资源申请', { confirmButtonText: '取消申请', cancelButtonText: '返回', type: 'warning' })
    const result = await cancelResourceRequest(row.id, row.rowVersion)
    detail.value = result
    ElMessage.success('资源申请已取消')
    void load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '取消资源申请失败'))
  }
}

async function decide(action: WorkflowTaskAction) {
  const task = workflowTask.value
  const current = detail.value?.request
  if (!task || !current || deciding.value) return
  const label = action === 'APPROVE' ? '批准' : action === 'RETURN' ? '退回' : '拒绝'
  try {
    const prompt = await ElMessageBox.prompt(
      action === 'APPROVE' ? '可填写审批意见；批准后资源申请进入已批准，等待后续实际下发登记。' : '请填写处理原因。',
      `${label}资源申请`,
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
    await loadDetail(current.id, current)
    void load()
  } catch (error) {
    if (!cancelled(error)) ElMessage.error(apiErrorMessage(error, `${label}失败`))
  } finally {
    deciding.value = ''
  }
}

// ---------- 资源办理下发 (REQ-20260825-053) ----------

const fulfillOpen = ref(false)
const fulfillLoading = ref(false)
const fulfillSubmitting = ref(false)
const fulfillMode = ref<FulfillmentMode>('AUTOMATED')
const mockExecutionLog = ref('')
const fulfillmentInstances = ref<FulfillInstanceItemPayload[]>([])
const differenceReason = ref('')
const targetFulfillRequest = ref<ResourceRequestSummary | null>(null)
const targetFulfillItems = ref<ResourceRequestItem[]>([])

const requestedNodes = computed(() => targetFulfillItems.value.reduce((acc, it) => acc + (it.plannedNodeCount || 0), 0))
const requestedCpu = computed(() => targetFulfillItems.value.reduce((acc, it) => acc + (it.cpuCores * (it.plannedNodeCount || 0) + (it.hasSidecar ? it.sidecarCpuCores : 0)), 0))
const requestedMem = computed(() => targetFulfillItems.value.reduce((acc, it) => acc + (it.memoryGb * (it.plannedNodeCount || 0) + (it.hasSidecar ? it.sidecarMemoryGb : 0)), 0))
const requestedStorage = computed(() => targetFulfillItems.value.reduce((acc, it) => acc + (it.databaseStorageGb + it.fileStorageGb + it.extraCbsGb + it.localDiskGb), 0))

const actualNodes = computed(() => fulfillmentInstances.value.length)
const actualCpu = computed(() => fulfillmentInstances.value.reduce((acc, it) => acc + (Number(it.cpuCores) || 0), 0))
const actualMem = computed(() => fulfillmentInstances.value.reduce((acc, it) => acc + (Number(it.memoryGb) || 0), 0))
const actualStorage = computed(() => fulfillmentInstances.value.reduce((acc, it) => acc + (Number(it.databaseStorageGb || 0) + Number(it.fileStorageGb || 0) + Number(it.extraCbsGb || 0) + Number(it.localDiskGb || 0)), 0))

const hasResourceDiff = computed(() => {
  return requestedNodes.value !== actualNodes.value
    || requestedCpu.value !== actualCpu.value
    || requestedMem.value !== actualMem.value
    || requestedStorage.value !== actualStorage.value
})

async function openFulfillDialog(req: ResourceRequestSummary, items?: ResourceRequestItem[]) {
  targetFulfillRequest.value = req
  differenceReason.value = ''
  mockExecutionLog.value = ''
  fulfillmentInstances.value = []
  fulfillMode.value = 'AUTOMATED'
  fulfillOpen.value = true

  if (items && items.length) {
    targetFulfillItems.value = items
  } else {
    try {
      const full = await getResourceRequest(req.id)
      targetFulfillItems.value = full.items
    } catch (err) {
      console.error(err)
    }
  }

  await loadAutomatedPreview()
}

async function loadAutomatedPreview() {
  if (!targetFulfillRequest.value) return
  fulfillLoading.value = true
  try {
    const preview = await previewAutomatedProvision(targetFulfillRequest.value.id)
    mockExecutionLog.value = preview.instances.map(i => i.mockExecutionLog).filter(Boolean).join('\n') || preview.message
    fulfillmentInstances.value = preview.instances.map(inst => ({
      sourceItemId: inst.sourceItemId,
      deploymentUnitId: inst.deploymentUnitId,
      machineName: inst.machineName,
      ipAddress: inst.ipAddress,
      serverType: inst.serverType || DEFAULT_SERVER_TYPE_CODE,
      deploymentPlatform: inst.deploymentPlatform || targetFulfillRequest.value?.physicalSubsystemDeploymentPlatform,
      networkZoneId: inst.networkZoneId ?? null,
      networkZoneName: inst.networkZoneName ?? null,
      networkZone: inst.networkZone,
      cpuCores: inst.cpuCores,
      memoryGb: inst.memoryGb,
      databaseStorageGb: inst.databaseStorageGb,
      fileStorageGb: inst.fileStorageGb,
      extraCbsGb: inst.extraCbsGb,
      localDiskGb: inst.localDiskGb,
      databaseName: inst.databaseName,
      databaseVersion: inst.databaseVersion,
      jdkVersion: inst.jdkVersion,
      middleware: inst.middleware,
      operatingSystem: inst.operatingSystem,
      needsNft: inst.needsNft,
      needsFserver: inst.needsFserver,
      needsJobexecutor: inst.needsJobexecutor,
      fulfillmentMode: 'AUTOMATED',
      remark: inst.remark
    }))
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, '自动部署预览失败，已切换为手动模式'))
    fulfillMode.value = 'MANUAL'
  } finally {
    fulfillLoading.value = false
  }
}

function handleFulfillModeChange(mode: FulfillmentMode) {
  fulfillMode.value = mode
  if (mode === 'AUTOMATED') {
    loadAutomatedPreview()
  } else {
    if (!fulfillmentInstances.value.length && targetFulfillItems.value.length) {
      for (const item of targetFulfillItems.value) {
        const count = Math.max(1, item.plannedNodeCount || 1)
        for (let i = 0; i < count; i++) {
          fulfillmentInstances.value.push({
            sourceItemId: item.id,
            deploymentUnitId: item.deploymentUnitId,
            machineName: '',
            ipAddress: '',
            serverType: item.serverType || DEFAULT_SERVER_TYPE_CODE,
            deploymentPlatform: targetFulfillRequest.value?.physicalSubsystemDeploymentPlatform,
            networkZoneId: item.networkZoneId,
            networkZoneName: item.networkZoneName,
            networkZone: item.networkZoneName || item.networkZone,
            cpuCores: item.cpuCores,
            memoryGb: item.memoryGb,
            databaseStorageGb: item.databaseStorageGb,
            fileStorageGb: item.fileStorageGb,
            extraCbsGb: item.extraCbsGb,
            localDiskGb: item.localDiskGb,
            databaseName: item.databaseName,
            databaseVersion: item.databaseVersion,
            jdkVersion: item.jdkVersion,
            middleware: item.middleware,
            operatingSystem: item.operatingSystem,
            needsNft: item.needsNft,
            needsFserver: item.needsFserver,
            needsJobexecutor: item.needsJobexecutor,
            fulfillmentMode: 'MANUAL',
            remark: item.remark
          })
        }
      }
    }
  }
}

function addManualInstanceRow() {
  const firstUnit = targetFulfillItems.value[0]
  fulfillmentInstances.value.push({
    sourceItemId: firstUnit?.id || null,
    deploymentUnitId: firstUnit?.deploymentUnitId || 0,
    machineName: '',
    ipAddress: '',
    serverType: firstUnit?.serverType || DEFAULT_SERVER_TYPE_CODE,
    deploymentPlatform: targetFulfillRequest.value?.physicalSubsystemDeploymentPlatform,
    networkZoneId: firstUnit?.networkZoneId || null,
    networkZoneName: firstUnit?.networkZoneName || null,
    networkZone: firstUnit?.networkZoneName || firstUnit?.networkZone || null,
    cpuCores: firstUnit?.cpuCores || 2,
    memoryGb: firstUnit?.memoryGb || 4,
    databaseStorageGb: firstUnit?.databaseStorageGb || 0,
    fileStorageGb: firstUnit?.fileStorageGb || 0,
    extraCbsGb: firstUnit?.extraCbsGb || 0,
    localDiskGb: firstUnit?.localDiskGb || 0,
    databaseName: firstUnit?.databaseName || null,
    databaseVersion: firstUnit?.databaseVersion || null,
    jdkVersion: firstUnit?.jdkVersion || null,
    middleware: firstUnit?.middleware || null,
    operatingSystem: firstUnit?.operatingSystem || null,
    needsNft: firstUnit?.needsNft || false,
    needsFserver: firstUnit?.needsFserver || false,
    needsJobexecutor: firstUnit?.needsJobexecutor || false,
    fulfillmentMode: 'MANUAL',
    remark: null
  })
}

function removeManualInstanceRow(index: number) {
  fulfillmentInstances.value.splice(index, 1)
}

async function submitFulfill() {
  if (!targetFulfillRequest.value) return
  if (!fulfillmentInstances.value.length) {
    ElMessage.warning('请至少填报一台下发机器实例')
    return
  }
  for (let i = 0; i < fulfillmentInstances.value.length; i++) {
    const inst = fulfillmentInstances.value[i]
    if (!inst.deploymentUnitId) {
      ElMessage.warning(`第 ${i + 1} 台实例必须选择所属部署单元`)
      return
    }
    if (!inst.machineName?.trim()) {
      ElMessage.warning(`第 ${i + 1} 台实例的主机名/机器名不能为空`)
      return
    }
    if (!inst.ipAddress?.trim()) {
      ElMessage.warning(`第 ${i + 1} 台实例的 IP 地址不能为空`)
      return
    }
    if (!inst.networkZoneId) {
      ElMessage.warning(`第 ${i + 1} 台实例必须选择网络分区`)
      return
    }
  }
  if (hasResourceDiff.value && !differenceReason.value.trim()) {
    ElMessage.warning('实际下发资源与工单申请值存在差异，必须填写差异原因')
    return
  }

  fulfillSubmitting.value = true
  try {
    const res = await fulfillResourceRequest(targetFulfillRequest.value.id, {
      fulfillmentMode: fulfillMode.value,
      differenceReason: differenceReason.value.trim() || undefined,
      instances: fulfillmentInstances.value.map(i => ({
        ...i,
        machineName: i.machineName.trim(),
        ipAddress: i.ipAddress.trim(),
        networkZone: networkZoneName(i.networkZoneId) || i.networkZone || null,
        fulfillmentMode: fulfillMode.value
      })),
      rowVersion: targetFulfillRequest.value.rowVersion
    })
    ElMessage.success(`资源办理成功，已生成 ${res.length} 台环境部署实例！`)
    fulfillOpen.value = false
    void load()
    if (detail.value && detail.value.request.id === targetFulfillRequest.value.id) {
      await showDetail(detail.value.request)
    }
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, '资源下发办理失败'))
  } finally {
    fulfillSubmitting.value = false
  }
}

function previous() {
  if (page.value <= 1) return
  page.value -= 1
  void load()
}

function next() {
  if (!hasNext.value) return
  page.value += 1
  void load()
}

watch(canView, allowed => {
  if (allowed) void Promise.all([load(), loadOptions()])
}, { immediate: true })

watch(() => [route.params.id, route.query.taskId, canView.value], () => { openRouteDetail() }, { immediate: true })

watch(deploymentUnitOptions, options => {
  if (!formOpen.value || !options.length) return
  form.items.forEach(item => {
    if (item.deploymentUnitId && options.some(option => option.id === item.deploymentUnitId)) syncDeploymentUnit(item, false)
  })
})
</script>

<template>
  <main class="architecture-page architecture-change-page architecture-resource-page">
    <UiPageHeader title="资源申请" description="按物理子系统和具体环境提交部署单元资源需求，审批通过后进入申请态。">
      <template #actions><el-button v-if="canApply" type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建资源申请</el-button></template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无资源申请查看权限" sub-title="请申请 architecture:resource-request:view、apply 或 manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="资源申请加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result>
    </section>
    <template v-else>
      <UiToolbar>
        <el-select v-model="filters.status" clearable placeholder="申请状态" class="architecture-filter-select"><el-option v-for="status in statusOptions" :key="status" :label="resourceRequestStatusLabels[status]" :value="status" /></el-select>
        <el-select v-model="filters.environmentId" clearable filterable placeholder="具体环境" class="architecture-filter-select"><el-option v-for="item in environments" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" /></el-select>
        <el-select v-model="filters.physicalSubsystemId" clearable filterable placeholder="物理子系统" class="architecture-filter-select"><el-option v-for="item in physicalOptions" :key="item.id" :label="`${item.name}（${item.shortName || item.code}）`" :value="item.id" /></el-select>
        <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button><el-button @click="reset">重置</el-button>
        <template #actions><span class="architecture-muted">{{ scopeLabel }}</span><el-tooltip content="刷新列表"><el-button circle :loading="loading" aria-label="刷新资源申请列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button></el-tooltip></template>
      </UiToolbar>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="申请单" min-width="190">
          <template #default="scope"><button type="button" class="architecture-table-identity" @click="showDetail(scope.row)"><strong>{{ scope.row.requestNo }}</strong><small>{{ requestTypeLabel(scope.row.requestType) }} · {{ scope.row.environmentName }}</small></button></template>
        </el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="resourceRequestStatusLabels" :tone="resourceRequestStatusTone(scope.row.status)" /></template></el-table-column>
        <el-table-column label="物理子系统" min-width="180"><template #default="scope">{{ scope.row.physicalSubsystemName }}<small class="architecture-inline-code">{{ scope.row.physicalSubsystemShortName || scope.row.physicalSubsystemCode }}</small></template></el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="210" show-overflow-tooltip />
        <el-table-column label="申请人" width="120"><template #default="scope">{{ userLabel(scope.row.applicantId) }}</template></el-table-column>
        <el-table-column label="联系人" width="120"><template #default="scope">{{ userLabel(scope.row.contactUserId) }}</template></el-table-column>
        <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="scope">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="showDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button v-if="canManage && scope.row.status === 'APPROVED'" link type="success" @click="openFulfillDialog(scope.row)">办理下发</el-button>
              <el-button v-if="canApply && owns(scope.row) && canEditResourceRequest(scope.row.status)" link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-button v-if="canApply && owns(scope.row) && canSubmitResourceRequest(scope.row.status)" link type="success" @click="confirmSubmit(scope.row)">提交</el-button>
              <el-button v-if="canApply && owns(scope.row) && canCancelResourceRequest(scope.row.status)" link type="warning" @click="confirmCancel(scope.row)">取消</el-button>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id">
          <header><div><strong>{{ row.requestNo }}</strong><small>{{ requestTypeLabel(row.requestType) }} · {{ row.environmentName }}</small></div><UiStatusTag :value="row.status" :labels="resourceRequestStatusLabels" :tone="resourceRequestStatusTone(row.status)" /></header>
          <p class="architecture-mobile-card__reason">{{ row.reason || '未填写申请原因' }}</p>
          <dl><div><dt>物理子系统</dt><dd>{{ row.physicalSubsystemName }}（{{ row.physicalSubsystemShortName || row.physicalSubsystemCode }}）</dd></div><div><dt>联系人</dt><dd>{{ userLabel(row.contactUserId) }}</dd></div><div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div></dl>
          <footer>
            <el-button link type="primary" @click="showDetail(row)"><el-icon><View /></el-icon>详情</el-button>
            <el-button v-if="canManage && row.status === 'APPROVED'" link type="success" @click="openFulfillDialog(row)">办理下发</el-button>
            <el-button v-if="canApply && owns(row) && canEditResourceRequest(row.status)" link type="primary" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
            <el-button v-if="canApply && owns(row) && canSubmitResourceRequest(row.status)" link type="success" @click="confirmSubmit(row)">提交</el-button>
            <el-button v-if="canApply && owns(row) && canCancelResourceRequest(row.status)" link type="warning" @click="confirmCancel(row)">取消</el-button>
          </footer>
        </article>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" title="暂无资源申请" description="当前筛选下没有资源申请记录。"><template #action><el-button v-if="canApply" type="primary" @click="openCreate">新建资源申请</el-button><el-button v-else @click="reset">清空筛选</el-button></template></UiEmptyState>

      <nav v-if="rows.length || page > 1" class="architecture-change-pagination" aria-label="资源申请分页">
        <span>第 {{ page }} 页</span>
        <div><el-button :disabled="page <= 1 || loading" @click="previous">上一页</el-button><el-button :disabled="!hasNext || loading" @click="next">下一页</el-button></div>
      </nav>
    </template>

    <el-drawer v-model="detailOpen" size="min(760px, 96vw)" :title="detail?.request.requestNo || '资源申请详情'" @closed="handleDetailClosed">
      <div v-loading="detailLoading" class="architecture-drawer-body">
        <template v-if="detail">
          <div class="architecture-detail-heading"><strong>{{ detail.request.requestNo }}</strong><span>{{ requestTypeLabel(detail.request.requestType) }} · {{ detail.request.environmentName }}</span></div>
          <dl class="architecture-detail-list">
            <div><dt>状态</dt><dd><UiStatusTag :value="detail.request.status" :labels="resourceRequestStatusLabels" :tone="resourceRequestStatusTone(detail.request.status)" /></dd></div>
            <div><dt>申请人</dt><dd>{{ userLabel(detail.request.applicantId) }}</dd></div>
            <div><dt>资源申请联系人</dt><dd>{{ userLabel(detail.request.contactUserId) }}</dd></div>
            <div><dt>具体环境</dt><dd>{{ detail.request.environmentName }}（{{ detail.request.environmentCode }}）</dd></div>
            <div><dt>物理子系统</dt><dd>{{ detail.request.physicalSubsystemName }}（{{ detail.request.physicalSubsystemShortName || detail.request.physicalSubsystemCode }}）</dd></div>
            <div><dt>所属事业群</dt><dd>{{ displayText(detail.request.physicalSubsystemBusinessGroupName) }}</dd></div>
            <div><dt>系统等级</dt><dd>{{ systemLevelLabel(detail.request.physicalSubsystemSystemLevelCode) }}</dd></div>
            <div><dt>部署平台</dt><dd>{{ deploymentPlatformLabel(detail.request.physicalSubsystemDeploymentPlatform) }}</dd></div>
            <div><dt>灾备模式</dt><dd>{{ disasterRecoveryLabel(detail.request.physicalSubsystemDisasterRecoveryMode) }}</dd></div>
            <div class="is-wide"><dt>申请原因</dt><dd>{{ detail.request.reason || '—' }}</dd></div>
          </dl>
          <section class="architecture-drawer-section">
            <header><strong>资源登记明细</strong><span class="architecture-muted">{{ detail.items.length }} 条</span></header>
            <div class="architecture-resource-item-list">
              <article v-for="item in detail.items" :key="item.id">
                <header>
                  <strong>{{ item.deploymentUnitName }}（{{ item.deploymentUnitCode }}）</strong>
                  <span>{{ deploymentUnitKindLabels[item.deploymentUnitKind] }}</span>
                </header>
                <dl>
                  <div class="is-wide"><dt>部署单元简述</dt><dd>{{ displayText(item.deploymentUnitDescription) }}</dd></div>
                </dl>
                <dl v-if="isDatabaseRecord(item)">
                  <div><dt>数据库存储需求</dt><dd>{{ displayAmount(item.databaseStorageGb, 'G') }}</dd></div>
                  <div><dt>数据库</dt><dd>{{ displayText(item.databaseName) }}</dd></div>
                  <div><dt>数据库版本</dt><dd>{{ displayText(item.databaseVersion) }}</dd></div>
                </dl>
                <template v-else>
                  <dl>
                    <div><dt>服务器类型</dt><dd>{{ serverTypeLabel(item.serverType) }}</dd></div>
                    <div><dt>文件存储需求</dt><dd>{{ displayAmount(item.fileStorageGb, 'G') }}</dd></div>
                    <div><dt>网络分区</dt><dd>{{ displayNetworkZone(item) }}</dd></div>
                    <div><dt>CPU</dt><dd>{{ displayAmount(item.cpuCores) }}</dd></div>
                    <div><dt>内存</dt><dd>{{ displayAmount(item.memoryGb, 'G') }}</dd></div>
                    <div><dt>AP、WEB组数</dt><dd>{{ item.appWebGroupCount }}</dd></div>
                    <div><dt>生产环境节点数</dt><dd>{{ item.plannedNodeCount }}</dd></div>
                    <div><dt>总CPU</dt><dd>{{ displayAmount(itemTotalCpu(item)) }}</dd></div>
                    <div><dt>总内存</dt><dd>{{ displayAmount(itemTotalMemory(item), 'G') }}</dd></div>
                    <div><dt>总边车CPU</dt><dd>{{ displayAmount(effectiveSidecarCpu(item)) }}</dd></div>
                    <div><dt>总边车内存</dt><dd>{{ displayAmount(effectiveSidecarMemory(item), 'G') }}</dd></div>
                    <div><dt>边车内存占比</dt><dd>{{ itemSidecarMemoryRatio(item) }}</dd></div>
                    <div><dt>有边车</dt><dd>{{ boolLabel(item.hasSidecar) }}</dd></div>
                  </dl>
                  <dl>
                    <div><dt>JDK</dt><dd>{{ jdkVersionLabel(item.jdkVersion) }}</dd></div>
                    <div><dt>中间件</dt><dd>{{ middlewareLabel(item.middleware) }}</dd></div>
                    <div><dt>产品化操作系统</dt><dd>{{ operatingSystemLabel(item.operatingSystem) }}</dd></div>
                    <div><dt>额外的CBS容量C</dt><dd>{{ displayAmount(item.extraCbsGb, 'G') }}</dd></div>
                    <div><dt>本地盘需求</dt><dd>{{ displayAmount(item.localDiskGb, 'G') }}</dd></div>
                    <div><dt>是否需要NFT</dt><dd>{{ boolLabel(item.needsNft) }}</dd></div>
                    <div><dt>是否需要FSever</dt><dd>{{ boolLabel(item.needsFserver) }}</dd></div>
                    <div><dt>是否需要jobexecutor</dt><dd>{{ boolLabel(item.needsJobexecutor) }}</dd></div>
                  </dl>
                </template>
                <p v-if="item.remark">{{ item.remark }}</p>
              </article>
            </div>
          </section>
          <section class="architecture-drawer-section">
            <header><strong>流转历史</strong></header>
            <div class="architecture-change-timeline">
              <article v-for="event in detail.history" :key="event.id">
                <header><strong>{{ event.summary }}</strong><span>{{ formatDateTime(event.occurredAt) }}</span></header>
                <div class="architecture-change-timeline__status"><span>{{ event.fromStatus ? resourceRequestStatusLabels[event.fromStatus] : '起始' }}</span><span>→</span><span>{{ event.toStatus ? resourceRequestStatusLabels[event.toStatus] : '—' }}</span></div>
              </article>
            </div>
          </section>
          <section v-if="canManage && detail.request.status === 'IN_REVIEW'" class="architecture-drawer-section architecture-change-approval">
            <header><strong>审批处理</strong><span class="architecture-muted">只处理工作流动作，不编辑申请内容</span></header>
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
          <section v-if="canManage && detail.request.status === 'APPROVED'" class="architecture-drawer-section architecture-change-approval">
            <header><strong>资源下发办理</strong><span class="architecture-muted">将工单审批资源规格落地为具体环境部署实例</span></header>
            <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 12px;">
              <div>
                <p style="margin: 0 0 4px; font-weight: 500;">工单审批已通过，可执行自动部署或手动逐台登记下发。</p>
                <span class="architecture-muted" style="font-size: 12px;">自动部署将模拟带出主机名、IP及资源指标。</span>
              </div>
              <el-button type="primary" @click="openFulfillDialog(detail.request, detail.items)">办理下发</el-button>
            </div>
          </section>
          <div v-if="canApply && owns(detail.request)" class="architecture-drawer-actions architecture-drawer-section">
            <el-button v-if="canEditResourceRequest(detail.request.status)" @click="openEdit(detail.request)"><el-icon><Edit /></el-icon>编辑</el-button>
            <el-button v-if="canSubmitResourceRequest(detail.request.status)" type="primary" @click="confirmSubmit(detail.request)">提交</el-button>
            <el-button v-if="canCancelResourceRequest(detail.request.status)" @click="confirmCancel(detail.request)">取消</el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-dialog
      v-model="formOpen"
      :title="formMode === 'create' ? '新建资源申请' : '编辑资源申请'"
      width="min(1480px, calc(100vw - 32px))"
      top="3vh"
      class="architecture-resource-request-form-dialog"
      :before-close="handleFormBeforeClose"
      destroy-on-close
    >
      <el-form v-loading="optionLoading" :disabled="formSubmitting" label-position="top" class="architecture-resource-request-form">
        <el-alert v-if="formError" class="architecture-resource-request-form__error" type="error" :closable="false" show-icon :title="formError" />
        <div class="architecture-resource-request-layout">
          <section class="architecture-resource-request-basics" aria-labelledby="resource-request-basics-title">
            <header class="architecture-resource-request-section-heading">
              <div>
                <strong id="resource-request-basics-title">基础信息</strong>
                <span>确定申请范围与联系人</span>
              </div>
            </header>
            <div class="architecture-resource-request-basic-fields">
              <el-form-item label="物理子系统" required>
                <el-select v-model="form.physicalSubsystemId" filterable @change="handlePhysicalSubsystemChange">
                  <el-option v-for="item in physicalOptions" :key="item.id" :label="`${item.name}（${item.shortName || item.code}）`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="资源申请联系人" required>
                <el-select v-model="form.contactUserId" filterable>
                  <el-option v-for="item in users" :key="item.id" :label="`${item.displayName}（${item.username}）`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="具体环境" required>
                <el-select v-model="form.environmentId" filterable>
                  <el-option v-for="item in activeEnvironments" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="申请类型" required>
                <el-select v-model="form.requestType">
                  <el-option v-for="item in typeOptions" :key="item" :label="resourceRequestTypeLabels[item]" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item label="申请原因">
                <el-input v-model="form.reason" type="textarea" :rows="2" maxlength="1000" show-word-limit />
              </el-form-item>
            </div>

            <div class="architecture-resource-request-summary-heading">物理子系统摘要</div>
            <dl v-if="selectedPhysical" class="architecture-resource-request-summary">
              <div><dt>编号</dt><dd>{{ selectedPhysical.code }}</dd></div>
              <div><dt>简称</dt><dd>{{ displayText(selectedPhysical.shortName) }}</dd></div>
              <div class="is-wide"><dt>名称</dt><dd>{{ selectedPhysical.name }}</dd></div>
              <div><dt>系统等级</dt><dd>{{ systemLevelLabel(selectedPhysical.systemLevelCode) }}</dd></div>
              <div><dt>所属事业群</dt><dd>{{ displayText(selectedPhysical.businessGroupName) }}</dd></div>
              <div><dt>部署平台</dt><dd>{{ deploymentPlatformLabel(selectedPhysical.deploymentPlatform) }}</dd></div>
              <div><dt>灾备模式</dt><dd>{{ disasterRecoveryLabel(selectedPhysical.disasterRecoveryMode) }}</dd></div>
            </dl>
          </section>

          <section class="architecture-resource-request-registration" aria-labelledby="resource-request-registration-title">
            <header class="architecture-resource-request-section-heading">
              <div>
                <strong id="resource-request-registration-title">部署单元登记表</strong>
                <span>共 {{ form.items.length }} 个申请项，允许同一部署单元登记多套规格</span>
              </div>
              <el-button type="primary" plain :disabled="formSubmitting" @click="addItem"><el-icon><Plus /></el-icon>添加申请项</el-button>
            </header>

            <div v-if="deploymentUnitLoadError" class="architecture-resource-request-option-error" role="alert">
              <span>{{ deploymentUnitLoadError }}</span>
              <el-button link type="primary" :loading="deploymentUnitLoading" @click="retryDeploymentUnits">重新加载</el-button>
            </div>

            <div class="architecture-resource-request-master-detail">
              <aside class="architecture-resource-request-item-panel" aria-label="申请项列表">
                <div class="architecture-resource-request-item-list" role="list">
                  <div
                    v-for="(item, index) in form.items"
                    :key="item.clientId"
                    class="architecture-resource-request-item-row"
                    :class="{ 'is-active': selectedItemId === item.clientId }"
                    role="listitem"
                  >
                    <button
                      type="button"
                      class="architecture-resource-request-item-select"
                      :aria-current="selectedItemId === item.clientId ? 'true' : undefined"
                      :disabled="formSubmitting"
                      @click="selectItem(item.clientId)"
                    >
                      <span class="architecture-resource-request-item-title">
                        <strong>{{ item.deploymentUnitName || '未选择部署单元' }}</strong>
                        <small :class="`is-${itemCompletionState(item).toLowerCase()}`">{{ itemCompletionLabel(item) }}</small>
                      </span>
                      <span class="architecture-resource-request-item-meta">
                        {{ item.deploymentUnitCode || '尚未关联' }}
                        <template v-if="item.deploymentUnitKind"> · {{ deploymentUnitKindLabels[item.deploymentUnitKind as DeploymentUnitKind] }}</template>
                      </span>
                    </button>
                    <el-tooltip :content="form.items.length <= 1 ? '至少保留一个申请项' : `删除申请项 ${index + 1}`">
                      <el-button
                        class="architecture-resource-request-item-delete"
                        :disabled="formSubmitting || form.items.length <= 1"
                        circle
                        :aria-label="`删除申请项 ${index + 1}`"
                        @click="removeItem(item.clientId)"
                      ><el-icon><Delete /></el-icon></el-button>
                    </el-tooltip>
                  </div>
                </div>
              </aside>

              <article v-if="currentItem" class="architecture-resource-request-editor">
                <header class="architecture-resource-request-editor__header">
                  <div>
                    <strong>{{ currentItem.deploymentUnitName || '未选择部署单元' }}</strong>
                    <span>{{ currentItem.deploymentUnitCode || '先选择部署单元，再填写资源规格' }}</span>
                  </div>
                  <span class="architecture-resource-request-editor__state" :class="`is-${itemCompletionState(currentItem).toLowerCase()}`">
                    {{ itemCompletionLabel(currentItem) }}
                  </span>
                </header>

                <div class="architecture-registration-subtitle">部署单元</div>
                <div class="architecture-registration-grid">
                  <el-form-item label="部署单元名称" required>
                    <el-select v-model="currentItem.deploymentUnitId" :loading="deploymentUnitLoading" filterable placeholder="选择部署单元" @change="syncDeploymentUnit(currentItem)">
                      <el-option v-for="unit in deploymentUnitOptions" :key="unit.id" :label="`${unit.name}（${unit.code}）`" :value="unit.id" />
                    </el-select>
                  </el-form-item>
                  <div><dt>部署单元类型</dt><dd>{{ currentItem.deploymentUnitKind ? deploymentUnitKindLabels[currentItem.deploymentUnitKind as DeploymentUnitKind] : '—' }}</dd></div>
                  <div class="is-wide"><dt>部署单元简述</dt><dd>{{ displayText(currentItem.deploymentUnitDescription) }}</dd></div>
                </div>

                <template v-if="isDatabaseRecord(currentItem)">
                  <div class="architecture-registration-subtitle">数据库资源</div>
                  <div class="architecture-registration-grid architecture-registration-grid--numbers">
                    <el-form-item label="数据库存储需求（G）"><el-input-number v-model="currentItem.databaseStorageGb" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                    <el-form-item label="数据库"><el-input v-model="currentItem.databaseName" maxlength="100" /></el-form-item>
                    <el-form-item label="数据库版本"><el-input v-model="currentItem.databaseVersion" maxlength="100" /></el-form-item>
                    <el-form-item class="is-wide" label="备注"><el-input v-model="currentItem.remark" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
                  </div>
                </template>

                <template v-else>
                  <div class="architecture-registration-subtitle">容量与部署</div>
                  <div class="architecture-registration-grid architecture-resource-request-capacity-fields">
                    <el-form-item class="architecture-resource-request-capacity-field--select" label="服务器类型">
                      <el-select v-model="currentItem.serverType">
                        <el-option v-for="serverType in serverTypes" :key="serverType.code" :label="serverType.label" :value="serverType.code" />
                      </el-select>
                    </el-form-item>
                    <el-form-item class="architecture-resource-request-capacity-field--number" label="文件存储需求（G）"><el-input-number v-model="currentItem.fileStorageGb" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                    <el-form-item class="architecture-resource-request-capacity-field--select" label="网络分区" required>
                      <el-select v-model="currentItem.networkZoneId" filterable placeholder="选择启用叶子网络分区" @change="syncNetworkZoneText(currentItem)">
                        <el-option v-for="zone in networkZoneOptions" :key="zone.id" :label="`${zone.name}（${zone.code}）`" :value="zone.id" />
                      </el-select>
                    </el-form-item>
                    <el-form-item class="architecture-resource-request-capacity-field--number" label="CPU"><el-input-number v-model="currentItem.cpuCores" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                    <el-form-item class="architecture-resource-request-capacity-field--number" label="内存"><el-input-number v-model="currentItem.memoryGb" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                    <el-form-item class="architecture-resource-request-capacity-field--number" label="AP、WEB组数"><el-input-number v-model="currentItem.appWebGroupCount" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                    <el-form-item class="architecture-resource-request-capacity-field--number" label="生产环境节点数"><el-input-number v-model="currentItem.plannedNodeCount" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                    <el-form-item class="architecture-resource-request-capacity-field--number" label="总边车CPU"><el-input-number v-model="currentItem.sidecarCpuCores" :disabled="!currentItem.hasSidecar" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                    <el-form-item class="architecture-resource-request-capacity-field--number" label="总边车内存"><el-input-number v-model="currentItem.sidecarMemoryGb" :disabled="!currentItem.hasSidecar" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                    <el-form-item class="architecture-resource-request-capacity-field--boolean" label="有边车？"><el-switch v-model="currentItem.hasSidecar" active-text="是" inactive-text="否" @change="syncSidecarFields(currentItem)" /></el-form-item>
                    <div class="architecture-registration-computed">
                      <span>总CPU {{ displayAmount(itemTotalCpu(currentItem)) }}</span>
                      <span>总内存 {{ displayAmount(itemTotalMemory(currentItem), 'G') }}</span>
                      <span>边车内存占比 {{ itemSidecarMemoryRatio(currentItem) }}</span>
                    </div>
                  </div>

                  <div class="architecture-registration-subtitle">技术栈</div>
                  <div class="architecture-registration-grid">
                    <el-form-item label="JDK">
                      <el-select v-model="currentItem.jdkVersion" clearable filterable>
                        <el-option v-for="option in jdkVersions" :key="option.code" :label="option.label" :value="option.code" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="中间件">
                      <el-select v-model="currentItem.middleware" clearable filterable>
                        <el-option v-for="option in middlewares" :key="option.code" :label="option.label" :value="option.code" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="产品化操作系统">
                      <el-select v-model="currentItem.operatingSystem" clearable filterable>
                        <el-option v-for="option in operatingSystems" :key="option.code" :label="option.label" :value="option.code" />
                      </el-select>
                    </el-form-item>
                  </div>

                  <el-collapse v-model="expandedItemExtras" class="architecture-registration-collapse">
                    <el-collapse-item :name="`extra-${currentItem.clientId}`" title="附加需求">
                      <div class="architecture-registration-grid">
                        <el-form-item label="额外的CBS容量C"><el-input-number v-model="currentItem.extraCbsGb" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                        <el-form-item label="本地盘需求（G）"><el-input-number v-model="currentItem.localDiskGb" :min="0" :precision="0" :step="1" controls-position="right" /></el-form-item>
                        <el-form-item label="是否需要NFT"><el-switch v-model="currentItem.needsNft" active-text="是" inactive-text="否" /></el-form-item>
                        <el-form-item label="是否需要FSever"><el-switch v-model="currentItem.needsFserver" active-text="是" inactive-text="否" /></el-form-item>
                        <el-form-item label="是否需要jobexecutor"><el-switch v-model="currentItem.needsJobexecutor" active-text="是" inactive-text="否" /></el-form-item>
                        <el-form-item class="is-wide" label="备注"><el-input v-model="currentItem.remark" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </template>
              </article>
            </div>
          </section>
        </div>
      </el-form>
      <template #footer><el-button :disabled="formSubmitting" @click="closeForm">取消</el-button><el-button type="primary" :loading="formSubmitting" @click="submitForm">保存草稿</el-button></template>
    </el-dialog>

    <!-- Fulfillment Dialog (REQ-20260825-053) -->
    <el-dialog
      v-model="fulfillOpen"
      :title="`资源申请下发办理 — ${targetFulfillRequest?.requestNo || ''}`"
      width="min(1280px, 96vw)"
      destroy-on-close
    >
      <div v-loading="fulfillLoading" class="architecture-drawer-body">
        <div style="display: flex; justify-content: space-between; align-items: center; background: var(--el-fill-color-light); border-radius: 8px; padding: 14px 18px; margin-bottom: 16px;">
          <div>
            <strong style="font-size: 15px;">下发办理方式</strong>
            <p style="margin: 4px 0 0; font-size: 13px; color: var(--el-text-color-secondary);">
              {{ fulfillMode === 'AUTOMATED' ? '自动部署模式：根据工单规格自动分配主机名与子网 IP（当前为对接平台 Mock 模式）' : '手动录入模式：管理员逐台手工登记下发主机名与 IP' }}
            </p>
          </div>
          <el-radio-group v-model="fulfillMode" @change="handleFulfillModeChange">
            <el-radio-button label="AUTOMATED">自动部署带出 (Mock)</el-radio-button>
            <el-radio-button label="MANUAL">手动逐台登记</el-radio-button>
          </el-radio-group>
        </div>

        <el-alert
          v-if="fulfillMode === 'AUTOMATED' && mockExecutionLog"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 16px;"
          title="自动部署调度就绪"
          :description="`已通过自动部署服务生成 ${fulfillmentInstances.length} 台实例初始规划配置。`"
        />

        <section class="architecture-drawer-section" style="border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px; margin-bottom: 16px;">
          <header style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
            <strong>资源规格对比</strong>
            <el-tag :type="hasResourceDiff ? 'warning' : 'success'">
              {{ hasResourceDiff ? '下发规格与申请存在差异' : '下发规格与申请一致' }}
            </el-tag>
          </header>
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
            <div style="background: var(--el-fill-color-blank); border-radius: 6px; padding: 10px 14px; border: 1px dashed var(--el-border-color);">
              <div style="font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px;">工单申请规格</div>
              <div style="font-size: 13px; font-weight: 500;">
                {{ requestedNodes }} 节点 / {{ requestedCpu }} 核 CPU / {{ requestedMem }} GB 内存 / {{ requestedStorage }} GB 存储
              </div>
            </div>
            <div style="background: var(--el-fill-color-blank); border-radius: 6px; padding: 10px 14px; border: 1px dashed var(--el-border-color);" :style="{ borderColor: hasResourceDiff ? 'var(--el-color-warning)' : undefined }">
              <div style="font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px;">实际下发规格</div>
              <div style="font-size: 13px; font-weight: 500;" :style="{ color: hasResourceDiff ? 'var(--el-color-warning)' : undefined }">
                {{ actualNodes }} 节点 / {{ actualCpu }} 核 CPU / {{ actualMem }} GB 内存 / {{ actualStorage }} GB 存储
              </div>
            </div>
          </div>
          <div v-if="hasResourceDiff" style="margin-top: 12px;">
            <el-form-item label="差异原因说明 (必填，因实际下发资源与工单申请值存在差异)" required>
              <el-input
                v-model="differenceReason"
                type="textarea"
                :rows="2"
                placeholder="请详细说明实际下发资源规格与工单申请规格产生差异的原因（如：机型配额限制、规格升档等）"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </div>
        </section>

        <section class="architecture-drawer-section">
          <header style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
            <strong>待落成环境部署实例明细 ({{ fulfillmentInstances.length }} 台)</strong>
            <el-button v-if="fulfillMode === 'MANUAL'" size="small" type="primary" :icon="Plus" @click="addManualInstanceRow">
              新增实例行
            </el-button>
          </header>

          <el-table :data="fulfillmentInstances" border style="width: 100%;">
            <el-table-column label="#" type="index" width="50" align="center" />
            <el-table-column label="所属部署单元" min-width="160">
              <template #default="{ row }">
                <el-select v-model="row.deploymentUnitId" size="small" style="width: 100%;">
                  <el-option
                    v-for="item in targetFulfillItems"
                    :key="item.deploymentUnitId"
                    :label="`${item.deploymentUnitName} (${item.deploymentUnitCode})`"
                    :value="item.deploymentUnitId"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="机器标识 / 主机名" min-width="170">
              <template #default="{ row }">
                <el-input v-model="row.machineName" size="small" placeholder="如 vm-dev-01" />
              </template>
            </el-table-column>
            <el-table-column label="IP 地址" min-width="140">
              <template #default="{ row }">
                <el-input v-model="row.ipAddress" size="small" placeholder="如 10.10.1.10" />
              </template>
            </el-table-column>
            <el-table-column label="CPU(核)" width="95">
              <template #default="{ row }">
                <el-input-number v-model="row.cpuCores" size="small" :min="0" :precision="0" style="width: 100%;" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column label="内存(G)" width="95">
              <template #default="{ row }">
                <el-input-number v-model="row.memoryGb" size="small" :min="0" :precision="0" style="width: 100%;" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column label="DB存储(G)" width="95">
              <template #default="{ row }">
                <el-input-number v-model="row.databaseStorageGb" size="small" :min="0" :precision="0" style="width: 100%;" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column label="文件存储(G)" width="95">
              <template #default="{ row }">
                <el-input-number v-model="row.fileStorageGb" size="small" :min="0" :precision="0" style="width: 100%;" controls-position="right" />
              </template>
            </el-table-column>
            <el-table-column label="网络分区" min-width="170">
              <template #default="{ row }">
                <el-select v-model="row.networkZoneId" size="small" filterable placeholder="网络分区" style="width:100%" @change="syncNetworkZoneText(row)">
                  <el-option v-for="zone in networkZoneOptions" :key="zone.id" :label="`${zone.name}（${zone.code}）`" :value="zone.id" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column v-if="fulfillMode === 'MANUAL'" label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" :disabled="fulfillmentInstances.length <= 1" @click="removeManualInstanceRow($index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
      <template #footer>
        <el-button @click="fulfillOpen = false">取消</el-button>
        <el-button type="primary" :loading="fulfillSubmitting" @click="submitFulfill">
          确认下发并生成环境部署实例
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>
