<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  approveNetworkAccessApplication,
  cancelNetworkAccessApplication,
  createNetworkAccessExemptionRule,
  createExternalNetworkAddress,
  createNetworkAccessApplication,
  deactivateExternalNetworkAddress,
  disableNetworkAccessExemptionRule,
  enableNetworkAccessExemptionRule,
  evaluateNetworkAccessDecision,
  listEnvironments,
  listExternalNetworkAddresses,
  listNetworkAccessApplications,
  listNetworkAccessExemptionRules,
  listNetworkAccessRelations,
  listNetworkEndpointInstances,
  loadNetworkZoneOptions,
  loadPhysicalSubsystemOptions,
  loadResourceDeploymentUnitOptions,
  reactivateExternalNetworkAddress,
  rejectNetworkAccessApplication,
  submitNetworkAccessApplication,
  updateExternalNetworkAddress,
  updateNetworkAccessExemptionRule
} from './api'
import type {
  DeploymentUnitOption,
  Environment,
  EnvironmentRecordStatus,
  ExternalNetworkAddress,
  ManagedEndpointInstance,
  NetworkAccessActionType,
  NetworkAccessApplication,
  NetworkAccessApplicationStatus,
  NetworkAccessDecisionResult,
  NetworkAccessExemptionRule,
  NetworkAccessExemptionRuleStatus,
  NetworkAccessProtocol,
  NetworkAccessRelation,
  NetworkAccessRelationCloseType,
  NetworkAccessRelationStatus,
  NetworkAccessValidityType,
  NetworkAddressType,
  NetworkEndpointKind,
  NetworkEndpointPayload,
  NetworkZoneOption,
  PhysicalSubsystemOption
} from './types'
import { environmentStatusLabels, environmentStatusTone, formatDateTime, httpStatus } from './utils'
import './architecture.css'

type EndpointDraft = {
  kind: NetworkEndpointKind
  physicalSubsystemId: number | null
  environmentId: number | null
  deploymentUnitId: number | null
  externalAddressId: number | null
  instanceIds: number[]
  deploymentUnits: DeploymentUnitOption[]
  instances: ManagedEndpointInstance[]
  loading: boolean
}

type AddressForm = {
  addressType: NetworkAddressType
  addressValue: string
  displayName: string
  purpose: string | null
  remark: string | null
  rowVersion: number | null
}

type ExemptionRuleForm = {
  ruleCode: string
  ruleName: string
  sourceNetworkZoneId: number | null
  targetNetworkZoneId: number | null
  protocol: NetworkAccessProtocol
  ports: string
  validityType: NetworkAccessValidityType
  validFrom: string | null
  validUntil: string | null
  remark: string | null
  rowVersion: number | null
}

const auth = useAuthStore()
const activeTab = ref<'applications' | 'relations' | 'addresses' | 'rules'>('applications')
const applications = ref<NetworkAccessApplication[]>([])
const relations = ref<NetworkAccessRelation[]>([])
const addresses = ref<ExternalNetworkAddress[]>([])
const exemptionRules = ref<NetworkAccessExemptionRule[]>([])
const addressOptions = ref<ExternalNetworkAddress[]>([])
const networkZoneOptions = ref<NetworkZoneOption[]>([])
const physicalOptions = ref<PhysicalSubsystemOption[]>([])
const environments = ref<Environment[]>([])
const applicationLoading = ref(false)
const relationLoading = ref(false)
const addressLoading = ref(false)
const ruleLoading = ref(false)
const lookupLoading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const applicationPage = ref(1)
const relationPage = ref(1)
const pageSize = ref(20)
const applicationFilters = reactive({ status: '' as NetworkAccessApplicationStatus | '' })
const relationFilters = reactive({ status: '' as NetworkAccessRelationStatus | '' })
const ruleFilters = reactive({ status: '' as NetworkAccessExemptionRuleStatus | '' })
const addressFilters = reactive({
  keyword: '',
  status: '' as EnvironmentRecordStatus | ''
})

const applicationDialogOpen = ref(false)
const applicationSubmitting = ref(false)
const applicationFormError = ref('')
const applicationDecisionLoading = ref(false)
const applicationDecisionError = ref('')
const applicationDecisionResult = ref<NetworkAccessDecisionResult | null>(null)
const applicationForm = reactive({
  source: blankEndpoint(),
  target: blankEndpoint(),
  actionType: 'OPEN' as NetworkAccessActionType,
  targetRelationId: null as number | null,
  targetRelationNo: null as string | null,
  protocol: 'TCP' as NetworkAccessProtocol,
  ports: '',
  purpose: '',
  processDescription: null as string | null,
  validityType: 'LIMITED' as NetworkAccessValidityType,
  validFrom: null as string | null,
  validUntil: null as string | null
})

const addressDialogOpen = ref(false)
const addressMode = ref<'create' | 'edit'>('create')
const addressSubmitting = ref(false)
const addressFormError = ref('')
const editingAddressId = ref<number | null>(null)
const ruleDialogOpen = ref(false)
const ruleMode = ref<'create' | 'edit'>('create')
const ruleSubmitting = ref(false)
const ruleFormError = ref('')
const editingRuleId = ref<number | null>(null)
const applicationDetailOpen = ref(false)
const selectedApplication = ref<NetworkAccessApplication | null>(null)
const relationDetailOpen = ref(false)
const selectedRelation = ref<NetworkAccessRelation | null>(null)
const addressDetailOpen = ref(false)
const selectedAddress = ref<ExternalNetworkAddress | null>(null)
const addressForm = reactive<AddressForm>({
  addressType: 'IP',
  addressValue: '',
  displayName: '',
  purpose: null,
  remark: null,
  rowVersion: null
})
const ruleForm = reactive<ExemptionRuleForm>({
  ruleCode: '',
  ruleName: '',
  sourceNetworkZoneId: null,
  targetNetworkZoneId: null,
  protocol: 'TCP',
  ports: '',
  validityType: 'LONG_TERM',
  validFrom: currentDateTimeValue(),
  validUntil: null,
  remark: null,
  rowVersion: null
})

let applicationSequence = 0
let relationSequence = 0
let addressSequence = 0
let ruleSequence = 0
let applicationDecisionSequence = 0
let applicationDecisionTimer: ReturnType<typeof setTimeout> | null = null

const applicationStatusOptions: NetworkAccessApplicationStatus[] = ['DRAFT', 'RETURNED', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CANCELLED']
const relationStatusOptions: NetworkAccessRelationStatus[] = ['ACTIVE', 'CLOSED']
const ruleStatusOptions: NetworkAccessExemptionRuleStatus[] = ['ACTIVE', 'DISABLED']
const addressStatusOptions: EnvironmentRecordStatus[] = ['ACTIVE', 'INACTIVE']
const protocolOptions: NetworkAccessProtocol[] = ['TCP', 'UDP', 'HTTP', 'HTTPS', 'OTHER']
const actionTypeOptions: NetworkAccessActionType[] = ['OPEN', 'MODIFY', 'RENEW', 'CLOSE']
const validityTypeOptions: NetworkAccessValidityType[] = ['LIMITED', 'LONG_TERM']

const addressTypeLabels: Record<NetworkAddressType, string> = {
  IP: 'IP',
  CIDR: 'CIDR',
  DOMAIN: '域名'
}
const endpointKindLabels: Record<NetworkEndpointKind, string> = {
  MANAGED: '托管实例',
  EXTERNAL: '外部地址'
}
const applicationStatusLabels: Record<NetworkAccessApplicationStatus, string> = {
  DRAFT: '草稿',
  RETURNED: '已退回',
  IN_REVIEW: '审批中',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
  CANCELLED: '已取消'
}
const relationStatusLabels: Record<NetworkAccessRelationStatus, string> = {
  ACTIVE: '生效',
  CLOSED: '已关闭'
}
const actionTypeLabels: Record<NetworkAccessActionType, string> = {
  OPEN: '开通',
  MODIFY: '变更',
  RENEW: '续期',
  CLOSE: '关闭'
}
const validityTypeLabels: Record<NetworkAccessValidityType, string> = {
  LIMITED: '限期',
  LONG_TERM: '长期'
}
const decisionLabels = {
  NEEDS_APPLICATION: '需要申请',
  NOT_REQUIRED: '无需申请'
} as const
const decisionBasisLabels = {
  SUBNET_INTERNAL: '同子网内部访问',
  RELATION_COVERED: '已有关系覆盖',
  RULE_EXEMPT: '命中免申请规则',
  STRICT_REQUIRED: '未证明覆盖'
} as const
const ruleStatusLabels: Record<NetworkAccessExemptionRuleStatus, string> = {
  ACTIVE: '启用',
  DISABLED: '停用'
}
const closeTypeLabels = {
  SUPERSEDED: '被变更替代',
  CLOSED_BY_APPLICATION: '关闭申请',
  LEGACY_DIRECT: '历史直接关闭'
} as const

const canView = computed(() => ['architecture:network-access:view', 'architecture:network-access:apply', 'architecture:network-access:manage', 'architecture:view', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canApply = computed(() => ['architecture:network-access:apply', 'architecture:network-access:manage', 'architecture:apply', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canManage = computed(() => auth.hasPermission('architecture:network-access:manage') || auth.hasPermission('architecture:manage'))
const hasNextApplications = computed(() => applications.value.length === pageSize.value)
const hasNextRelations = computed(() => relations.value.length === pageSize.value)
const loading = computed(() => applicationLoading.value || relationLoading.value || addressLoading.value || ruleLoading.value || lookupLoading.value)
const applicationDialogTitle = computed(() => `${actionTypeLabels[applicationForm.actionType]}网络访问申请`)
const activeRelations = computed(() => relations.value.filter(row => row.status === 'ACTIVE'))
const applicationDecisionRelevant = computed(() => applicationForm.actionType === 'OPEN' || applicationForm.actionType === 'MODIFY')
const applicationDecisionReady = computed(() => applicationDecisionRelevant.value
  && endpointDraftReady(applicationForm.source)
  && endpointDraftReady(applicationForm.target)
  && !!applicationForm.ports.trim()
  && validityReady(applicationForm.validityType, applicationForm.validFrom, applicationForm.validUntil)
)
const applicationDecisionBlocksSubmit = computed(() => applicationDecisionRelevant.value && applicationDecisionResult.value?.decision === 'NOT_REQUIRED')

function blankEndpoint(): EndpointDraft {
  return {
    kind: 'MANAGED',
    physicalSubsystemId: null,
    environmentId: null,
    deploymentUnitId: null,
    externalAddressId: null,
    instanceIds: [],
    deploymentUnits: [],
    instances: [],
    loading: false
  }
}

function currentDateTimeValue() {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

function validateValidity(
  type: NetworkAccessValidityType,
  validFrom: string | null,
  validUntil: string | null,
  reportError: (message: string) => void
) {
  if (!validFrom) {
    reportError('请填写有效期开始时间')
    return false
  }
  if (type === 'LONG_TERM') return true
  if (!validUntil) {
    reportError('限期访问请填写有效期结束时间')
    return false
  }
  if (validUntil <= validFrom) {
    reportError('有效期结束时间必须晚于开始时间')
    return false
  }
  return true
}

function onValidityTypeChange(form: { validityType: NetworkAccessValidityType; validFrom: string | null; validUntil: string | null }) {
  if (!form.validFrom) form.validFrom = currentDateTimeValue()
  if (form.validityType === 'LONG_TERM') form.validUntil = null
}

function normalizeInstanceIds(ids: number[]) {
  const seen = new Set<number>()
  const result: number[] = []
  for (const id of ids) {
    if (!Number.isFinite(id) || id <= 0 || seen.has(id)) continue
    seen.add(id)
    result.push(id)
  }
  return result
}

function selectedManagedInstanceIds(endpoint: EndpointDraft) {
  return new Set(endpoint.kind === 'MANAGED' ? normalizeInstanceIds(endpoint.instanceIds) : [])
}

function isEndpointInstanceDisabled(instance: ManagedEndpointInstance, opposite: EndpointDraft) {
  return selectedManagedInstanceIds(opposite).has(instance.id)
}

function pruneEndpointInstanceSelection(endpoint: EndpointDraft, opposite: EndpointDraft, notify = false) {
  const oppositeIds = selectedManagedInstanceIds(opposite)
  const normalized = normalizeInstanceIds(endpoint.instanceIds)
  const next = normalized.filter(id => !oppositeIds.has(id))
  const changed = endpoint.instanceIds.length !== next.length
    || endpoint.instanceIds.some((id, index) => id !== next[index])
  if (changed) {
    endpoint.instanceIds = next
    if (notify) ElMessage.warning('来源实例和目标实例不能重复，已自动去重')
  }
  return changed
}

function changeEndpointInstances(endpoint: EndpointDraft, opposite: EndpointDraft) {
  pruneEndpointInstanceSelection(endpoint, opposite, true)
}

function validateDistinctEndpointPayloads(
  source: NetworkEndpointPayload,
  target: NetworkEndpointPayload,
  reportError: (message: string) => void
) {
  if (source.kind !== 'MANAGED' || target.kind !== 'MANAGED') return true
  const sourceIds = new Set(normalizeInstanceIds(source.instanceIds || []))
  const duplicate = normalizeInstanceIds(target.instanceIds || []).find(id => sourceIds.has(id))
  if (!duplicate) return true
  reportError('来源端点和目标端点不能选择同一环境部署实例')
  return false
}

async function loadLookups() {
  if (!canView.value) return
  lookupLoading.value = true
  try {
    const [physicalRows, environmentRows, activeAddresses, zoneRows] = await Promise.all([
      loadPhysicalSubsystemOptions('', 200),
      listEnvironments({ status: 'ACTIVE', limit: 200 }),
      listExternalNetworkAddresses({ status: 'ACTIVE' }),
      loadNetworkZoneOptions(true)
    ])
    physicalOptions.value = physicalRows
    environments.value = environmentRows
    addressOptions.value = activeAddresses
    networkZoneOptions.value = zoneRows
  } catch (error) {
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '网络访问选项加载失败')
  } finally {
    lookupLoading.value = false
  }
}

async function loadApplications() {
  if (!canView.value) return
  const request = ++applicationSequence
  applicationLoading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listNetworkAccessApplications({
      status: applicationFilters.status,
      limit: pageSize.value,
      offset: (applicationPage.value - 1) * pageSize.value
    })
    if (request === applicationSequence) applications.value = result
  } catch (error) {
    if (request !== applicationSequence) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '网络访问申请加载失败')
  } finally {
    if (request === applicationSequence) applicationLoading.value = false
  }
}

async function loadRelations() {
  if (!canView.value) return
  const request = ++relationSequence
  relationLoading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listNetworkAccessRelations({
      status: relationFilters.status,
      limit: pageSize.value,
      offset: (relationPage.value - 1) * pageSize.value
    })
    if (request === relationSequence) relations.value = result
  } catch (error) {
    if (request !== relationSequence) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '网络访问关系加载失败')
  } finally {
    if (request === relationSequence) relationLoading.value = false
  }
}

async function loadAddresses() {
  if (!canView.value) return
  const request = ++addressSequence
  addressLoading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listExternalNetworkAddresses({
      keyword: addressFilters.keyword,
      status: addressFilters.status
    })
    if (request === addressSequence) addresses.value = result
  } catch (error) {
    if (request !== addressSequence) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '外部网络地址加载失败')
  } finally {
    if (request === addressSequence) addressLoading.value = false
  }
}

async function loadExemptionRules() {
  if (!canView.value) return
  const request = ++ruleSequence
  ruleLoading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listNetworkAccessExemptionRules({ status: ruleFilters.status })
    if (request === ruleSequence) exemptionRules.value = result
  } catch (error) {
    if (request !== ruleSequence) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '免申请规则加载失败')
  } finally {
    if (request === ruleSequence) ruleLoading.value = false
  }
}

async function loadAll() {
  await Promise.all([loadLookups(), loadApplications(), loadRelations(), loadAddresses(), loadExemptionRules()])
}

async function refresh() {
  await loadAll()
  if (!loadError.value && !forbidden.value) ElMessage.success('网络访问数据已刷新')
}

function applicationSearch() {
  applicationPage.value = 1
  void loadApplications()
}

function relationSearch() {
  relationPage.value = 1
  void loadRelations()
}

function addressSearch() {
  void loadAddresses()
}

function ruleSearch() {
  void loadExemptionRules()
}

function resetApplications() {
  applicationFilters.status = ''
  applicationPage.value = 1
  void loadApplications()
}

function resetRelations() {
  relationFilters.status = ''
  relationPage.value = 1
  void loadRelations()
}

function resetAddresses() {
  addressFilters.keyword = ''
  addressFilters.status = ''
  void loadAddresses()
}

function resetRules() {
  ruleFilters.status = ''
  void loadExemptionRules()
}

function previousApplications() {
  if (applicationPage.value <= 1) return
  applicationPage.value -= 1
  void loadApplications()
}

function nextApplications() {
  if (!hasNextApplications.value) return
  applicationPage.value += 1
  void loadApplications()
}

function previousRelations() {
  if (relationPage.value <= 1) return
  relationPage.value -= 1
  void loadRelations()
}

function nextRelations() {
  if (!hasNextRelations.value) return
  relationPage.value += 1
  void loadRelations()
}

function applicationStatusTone(status: NetworkAccessApplicationStatus) {
  if (status === 'APPROVED') return 'success' as const
  if (status === 'REJECTED') return 'danger' as const
  if (status === 'IN_REVIEW' || status === 'RETURNED') return 'warning' as const
  return 'info' as const
}

function relationStatusTone(status: NetworkAccessRelationStatus) {
  return status === 'ACTIVE' ? 'success' as const : 'info' as const
}

function ruleStatusTone(status: NetworkAccessExemptionRuleStatus) {
  return status === 'ACTIVE' ? 'success' as const : 'info' as const
}

function decisionReasonText(result: NetworkAccessDecisionResult) {
  if (result.coveringRelationNos.length) return `已有访问关系完整覆盖：${result.coveringRelationNos.join('、')}`
  if (result.coveringRuleCodes.length) return `命中免申请规则完整覆盖：${result.coveringRuleCodes.join('、')}`
  if (result.basis === 'SUBNET_INTERNAL') return '同一启用子网内部访问'
  const labels: Record<string, string> = {
    NO_FULL_COVERAGE: '未证明已有关系或免申请规则覆盖本次访问',
    INVALID_OR_INCOMPLETE_INPUT: '输入无效或信息不完整',
    STRICT_REQUIRED_ON_EXCEPTION: '判定异常，按严格策略需要申请',
    EXISTING_RELATION_FULLY_COVERS: '已有访问关系完整覆盖',
    EXEMPTION_RULE_FULLY_COVERS: '命中免申请规则完整覆盖',
    SAME_SUBNET_INTERNAL: '同一启用子网内部访问'
  }
  const reasons = result.reasonCodes.map(code => labels[code] || code)
  return reasons.length ? reasons.join('；') : decisionBasisLabels[result.basis]
}

function decisionReasonCodeText(result: NetworkAccessDecisionResult) {
  return result.reasonCodes.length ? result.reasonCodes.join('、') : '无'
}

function decisionTitleText(result: NetworkAccessDecisionResult) {
  return decisionLabels[result.decision]
}

function decisionResultClass(result: NetworkAccessDecisionResult) {
  return result.decision === 'NOT_REQUIRED' ? 'is-not-required' : 'is-needs-application'
}

function addressTypeLabel(value: NetworkAddressType | string | null | undefined) {
  return value ? addressTypeLabels[value as NetworkAddressType] || value : '—'
}

function validityTypeLabel(value: NetworkAccessValidityType | string | null | undefined) {
  return value ? validityTypeLabels[value as NetworkAccessValidityType] || value : '—'
}

function actionTypeLabel(value: NetworkAccessActionType | string | null | undefined) {
  return value ? actionTypeLabels[value as NetworkAccessActionType] || value : '—'
}

function closeTypeText(value: NetworkAccessRelationCloseType | null | undefined) {
  return value ? closeTypeLabels[value] || value : '—'
}

function resetEndpoint(endpoint: EndpointDraft, kind: NetworkEndpointKind = endpoint.kind) {
  endpoint.kind = kind
  endpoint.physicalSubsystemId = null
  endpoint.environmentId = null
  endpoint.deploymentUnitId = null
  endpoint.externalAddressId = null
  endpoint.instanceIds = []
  endpoint.deploymentUnits = []
  endpoint.instances = []
  endpoint.loading = false
}

function changeEndpointKind(endpoint: EndpointDraft, value: NetworkEndpointKind | string | number | boolean) {
  resetEndpoint(endpoint, value as NetworkEndpointKind)
}

function endpointDraftReady(endpoint: EndpointDraft) {
  if (endpoint.loading) return false
  if (endpoint.kind === 'EXTERNAL') return !!endpoint.externalAddressId
  return !!endpoint.physicalSubsystemId
    && !!endpoint.environmentId
    && !!endpoint.deploymentUnitId
    && endpoint.instanceIds.length > 0
}

function validityReady(type: NetworkAccessValidityType, validFrom: string | null, validUntil: string | null) {
  let ready = true
  validateValidity(type, validFrom, validUntil, () => { ready = false })
  return ready
}

function clearApplicationDecisionTimer() {
  if (!applicationDecisionTimer) return
  clearTimeout(applicationDecisionTimer)
  applicationDecisionTimer = null
}

function resetApplicationDecisionState() {
  clearApplicationDecisionTimer()
  applicationDecisionSequence += 1
  applicationDecisionLoading.value = false
  applicationDecisionError.value = ''
  applicationDecisionResult.value = null
}

function scheduleApplicationDecision() {
  clearApplicationDecisionTimer()
  applicationDecisionError.value = ''
  applicationDecisionResult.value = null
  applicationDecisionSequence += 1
  applicationDecisionLoading.value = false
  if (!applicationDialogOpen.value || !applicationDecisionReady.value) return
  const request = applicationDecisionSequence
  applicationDecisionTimer = setTimeout(() => {
    if (request === applicationDecisionSequence) void evaluateApplicationFormDecision()
  }, 350)
}

async function evaluateApplicationFormDecision(showValidation = false): Promise<NetworkAccessDecisionResult | null> {
  clearApplicationDecisionTimer()
  const request = ++applicationDecisionSequence
  applicationDecisionLoading.value = false
  if (!applicationDecisionRelevant.value) {
    if (showValidation) applicationDecisionError.value = '当前申请动作无需访问判定'
    return null
  }
  applicationDecisionError.value = ''
  applicationDecisionResult.value = null
  const source = buildEndpointPayload(applicationForm.source, '来源端点', message => { applicationDecisionError.value = message })
  if (!source) return null
  const target = buildEndpointPayload(applicationForm.target, '目标端点', message => { applicationDecisionError.value = message })
  if (!target) return null
  if (!validateDistinctEndpointPayloads(source, target, message => { applicationDecisionError.value = message })) return null
  if (!applicationForm.ports.trim()) {
    applicationDecisionError.value = '请填写协议端口'
    return null
  }
  if (!validateValidity(applicationForm.validityType, applicationForm.validFrom, applicationForm.validUntil, message => { applicationDecisionError.value = message })) {
    return null
  }

  applicationDecisionLoading.value = true
  try {
    const result = await evaluateNetworkAccessDecision({
      source,
      target,
      protocol: applicationForm.protocol,
      ports: applicationForm.ports.trim(),
      validityType: applicationForm.validityType,
      validFrom: applicationForm.validFrom,
      validUntil: applicationForm.validityType === 'LONG_TERM' ? null : applicationForm.validUntil
    })
    if (request !== applicationDecisionSequence) return null
    applicationDecisionResult.value = result
    return result
  } catch (error) {
    if (request === applicationDecisionSequence) applicationDecisionError.value = apiErrorMessage(error, '访问判定失败')
    return null
  } finally {
    if (request === applicationDecisionSequence) applicationDecisionLoading.value = false
  }
}

function openApplicationCreate(actionType: NetworkAccessActionType = 'OPEN', relation?: NetworkAccessRelation) {
  resetApplicationDecisionState()
  resetEndpoint(applicationForm.source, 'MANAGED')
  resetEndpoint(applicationForm.target, 'MANAGED')
  applicationForm.actionType = actionType
  applicationForm.targetRelationId = relation?.id ?? null
  applicationForm.targetRelationNo = relation?.relationNo ?? null
  applicationForm.protocol = relation?.protocol ?? 'TCP'
  applicationForm.ports = relation?.ports ?? ''
  applicationForm.purpose = ''
  applicationForm.processDescription = null
  applicationForm.validityType = actionType === 'RENEW' ? 'LIMITED' : (relation?.validityType ?? 'LIMITED')
  applicationForm.validFrom = actionType === 'CLOSE' ? relation?.validFrom ?? currentDateTimeValue() : currentDateTimeValue()
  applicationForm.validUntil = applicationForm.validityType === 'LONG_TERM' ? null : relation?.validUntil ?? null
  applicationFormError.value = ''
  applicationDialogOpen.value = true
  if (!physicalOptions.value.length || !environments.value.length || !addressOptions.value.length) void loadLookups()
}

async function loadEndpointDeploymentUnits(endpoint: EndpointDraft) {
  endpoint.deploymentUnitId = null
  endpoint.instances = []
  endpoint.instanceIds = []
  endpoint.deploymentUnits = []
  if (!endpoint.physicalSubsystemId) return
  endpoint.loading = true
  try {
    endpoint.deploymentUnits = await loadResourceDeploymentUnitOptions(endpoint.physicalSubsystemId, 200)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '部署单元选项加载失败'))
  } finally {
    endpoint.loading = false
  }
}

async function loadEndpointInstances(endpoint: EndpointDraft, opposite?: EndpointDraft) {
  endpoint.instances = []
  endpoint.instanceIds = []
  if (!endpoint.physicalSubsystemId || !endpoint.environmentId || !endpoint.deploymentUnitId) return
  endpoint.loading = true
  try {
    endpoint.instances = await listNetworkEndpointInstances({
      physicalSubsystemId: endpoint.physicalSubsystemId,
      environmentId: endpoint.environmentId,
      deploymentUnitId: endpoint.deploymentUnitId
    })
    endpoint.instanceIds = endpoint.instances.map(instance => instance.id)
    if (opposite) pruneEndpointInstanceSelection(endpoint, opposite)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '环境部署实例加载失败'))
  } finally {
    endpoint.loading = false
  }
}

function endpointInstanceLabel(instance: ManagedEndpointInstance) {
  const zone = instance.networkZoneName ? ` · ${instance.networkZoneName}` : ''
  return `${instance.machineName} / ${instance.ipAddress}${zone}`
}

function buildEndpointPayload(
  endpoint: EndpointDraft,
  label: string,
  reportError: (message: string) => void = message => { applicationFormError.value = message }
): NetworkEndpointPayload | null {
  if (endpoint.kind === 'EXTERNAL') {
    if (!endpoint.externalAddressId) {
      reportError(`${label}请选择外部网络地址`)
      return null
    }
    return { kind: 'EXTERNAL', externalAddressId: endpoint.externalAddressId }
  }
  if (!endpoint.physicalSubsystemId || !endpoint.environmentId || !endpoint.deploymentUnitId) {
    reportError(`${label}请选择物理子系统、具体环境和部署单元`)
    return null
  }
  const instanceIds = normalizeInstanceIds(endpoint.instanceIds)
  if (!instanceIds.length) {
    reportError(`${label}至少选择一个在用环境部署实例`)
    return null
  }
  return {
    kind: 'MANAGED',
    physicalSubsystemId: endpoint.physicalSubsystemId,
    environmentId: endpoint.environmentId,
    deploymentUnitId: endpoint.deploymentUnitId,
    instanceIds
  }
}

async function submitApplicationForm() {
  if (applicationSubmitting.value) return
  applicationFormError.value = ''
  let source: NetworkEndpointPayload | null = null
  let target: NetworkEndpointPayload | null = null
  if (applicationForm.actionType === 'OPEN' || applicationForm.actionType === 'MODIFY') {
    source = buildEndpointPayload(applicationForm.source, '来源端点')
    if (!source) return
    target = buildEndpointPayload(applicationForm.target, '目标端点')
    if (!target) return
    if (!validateDistinctEndpointPayloads(source, target, message => { applicationFormError.value = message })) return
  }
  if ((applicationForm.actionType === 'MODIFY' || applicationForm.actionType === 'RENEW' || applicationForm.actionType === 'CLOSE') && !applicationForm.targetRelationId) {
    applicationFormError.value = '请选择目标访问关系'
    return
  }
  if (applicationForm.actionType !== 'CLOSE' && !applicationForm.ports.trim()) {
    applicationFormError.value = '请填写协议端口'
    return
  }
  if (applicationForm.actionType !== 'CLOSE'
      && !validateValidity(applicationForm.validityType, applicationForm.validFrom, applicationForm.validUntil, message => { applicationFormError.value = message })) {
    return
  }
  if (applicationDecisionRelevant.value) {
    const decision = await evaluateApplicationFormDecision(true)
    if (!decision) {
      applicationFormError.value = applicationDecisionError.value || '请先完成访问判定'
      return
    }
    if (!decision.needsApplication) {
      applicationFormError.value = '判定结果为无需申请，不能保存新的网络访问申请'
      return
    }
  }
  if (!applicationForm.purpose.trim()) {
    applicationFormError.value = '请填写访问用途'
    return
  }
  applicationSubmitting.value = true
  try {
    await createNetworkAccessApplication({
      source,
      target,
      actionType: applicationForm.actionType,
      targetRelationId: applicationForm.targetRelationId,
      protocol: applicationForm.protocol,
      ports: applicationForm.ports.trim(),
      purpose: applicationForm.purpose.trim(),
      processDescription: applicationForm.processDescription?.trim() || null,
      validityType: applicationForm.validityType,
      validFrom: applicationForm.validFrom,
      validUntil: applicationForm.validityType === 'LONG_TERM' ? null : applicationForm.validUntil
    })
    ElMessage.success('网络访问申请已创建')
    applicationDialogOpen.value = false
    applicationPage.value = 1
    void loadApplications()
  } catch (error) {
    applicationFormError.value = apiErrorMessage(error, '创建网络访问申请失败')
  } finally {
    applicationSubmitting.value = false
  }
}

async function submitApplication(row: NetworkAccessApplication) {
  try {
    await ElMessageBox.confirm(`提交「${row.applicationNo}」进入审批？`, '提交网络访问申请', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await submitNetworkAccessApplication(row.id, row.rowVersion)
    ElMessage.success('网络访问申请已提交')
    void loadApplications()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '提交网络访问申请失败'))
  }
}

async function approveApplication(row: NetworkAccessApplication) {
  try {
    await ElMessageBox.confirm(`批准「${row.applicationNo}」后会生成 RDDMP 内部网络访问关系，不代表真实网络设备已开通。`, '批准网络访问申请', {
      confirmButtonText: '批准',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await approveNetworkAccessApplication(row.id, row.rowVersion)
    ElMessage.success('网络访问关系已生成')
    void loadApplications()
    void loadRelations()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '批准网络访问申请失败'))
  }
}

async function rejectApplication(row: NetworkAccessApplication) {
  try {
    await ElMessageBox.confirm(`拒绝「${row.applicationNo}」？`, '拒绝网络访问申请', {
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await rejectNetworkAccessApplication(row.id, row.rowVersion)
    ElMessage.success('网络访问申请已拒绝')
    void loadApplications()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '拒绝网络访问申请失败'))
  }
}

async function cancelApplication(row: NetworkAccessApplication) {
  try {
    await ElMessageBox.confirm(`取消「${row.applicationNo}」？`, '取消网络访问申请', {
      confirmButtonText: '取消申请',
      cancelButtonText: '返回',
      type: 'warning'
    })
    await cancelNetworkAccessApplication(row.id, row.rowVersion)
    ElMessage.success('网络访问申请已取消')
    void loadApplications()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, '取消网络访问申请失败'))
  }
}

function openRelationLifecycleApplication(row: NetworkAccessRelation, actionType: NetworkAccessActionType) {
  openApplicationCreate(actionType, row)
  if (actionType === 'CLOSE') {
    applicationForm.purpose = `关闭访问关系 ${row.relationNo}`
  } else if (actionType === 'RENEW') {
    applicationForm.purpose = `续期访问关系 ${row.relationNo}`
  } else if (actionType === 'MODIFY') {
    applicationForm.purpose = `变更访问关系 ${row.relationNo}`
  }
}

function changeApplicationTargetRelation(id: number | string | null | undefined) {
  const relation = activeRelations.value.find(row => row.id === Number(id))
  applicationForm.targetRelationNo = relation?.relationNo ?? null
  if (!relation) return
  applicationForm.protocol = relation.protocol
  applicationForm.ports = relation.ports
  applicationForm.validityType = relation.validityType
  applicationForm.validFrom = relation.validFrom || currentDateTimeValue()
  applicationForm.validUntil = relation.validityType === 'LONG_TERM' ? null : relation.validUntil
}

function parseSnapshot(snapshotJson: string | null) {
  if (!snapshotJson) return []
  try {
    const value = JSON.parse(snapshotJson) as unknown
    return Array.isArray(value) ? value as Record<string, unknown>[] : []
  } catch {
    return []
  }
}

function snapshotSummary(snapshotJson: string | null, kind: NetworkEndpointKind) {
  const items = parseSnapshot(snapshotJson)
  if (!items.length) return endpointKindLabels[kind]
  const first = items[0]
  if (kind === 'EXTERNAL') {
    const name = String(first.displayName || '外部地址')
    const value = String(first.addressValue || '')
    return value ? `${name} · ${value}` : name
  }
  const unit = String(first.deploymentUnitName || first.deploymentUnitCode || '部署单元')
  const zones = Array.from(new Set(items.map(item => item.networkZoneName).filter(Boolean).map(String)))
  return `${unit} · ${items.length} 个实例${zones.length ? ` · ${zones.slice(0, 2).join('、')}` : ''}`
}

function snapshotDetail(snapshotJson: string | null, kind: NetworkEndpointKind) {
  const items = parseSnapshot(snapshotJson)
  if (!items.length) return '—'
  if (kind === 'EXTERNAL') return snapshotSummary(snapshotJson, kind)
  return items.map(item => `${String(item.machineName || '实例')} / ${String(item.ipAddress || '—')}`).join('；')
}

function openApplicationDetail(row: NetworkAccessApplication) {
  selectedApplication.value = row
  applicationDetailOpen.value = true
}

function openRelationDetail(row: NetworkAccessRelation) {
  selectedRelation.value = row
  relationDetailOpen.value = true
}

function openAddressDetail(row: ExternalNetworkAddress) {
  selectedAddress.value = row
  addressDetailOpen.value = true
}

function snapshotItems(snapshotJson: string | null) {
  return parseSnapshot(snapshotJson)
}

function snapshotItemTitle(item: Record<string, unknown>, kind: NetworkEndpointKind) {
  if (kind === 'EXTERNAL') {
    const name = String(item.displayName || '外部地址')
    const value = String(item.addressValue || '')
    return value ? `${name} · ${value}` : name
  }
  return `${String(item.machineName || '实例')} / ${String(item.ipAddress || '—')}`
}

function snapshotItemMeta(item: Record<string, unknown>, kind: NetworkEndpointKind) {
  if (kind === 'EXTERNAL') {
    const type = String(item.addressType || '地址')
    const id = item.addressId ? `#${String(item.addressId)}` : ''
    return [type, id].filter(Boolean).join(' · ') || '—'
  }
  const physical = String(item.physicalSubsystemName || item.physicalSubsystemCode || '')
  const environment = String(item.environmentName || item.environmentCode || '')
  const unit = String(item.deploymentUnitName || item.deploymentUnitCode || '')
  const zone = String(item.networkZoneName || '')
  return [
    physical ? `物理子系统：${physical}` : '',
    environment ? `环境：${environment}` : '',
    unit ? `部署单元：${unit}` : '',
    zone ? `分区：${zone}` : ''
  ].filter(Boolean).join(' · ') || '—'
}

function validityText(row: { validFrom: string | null; validUntil: string | null; validityType?: NetworkAccessValidityType | null }) {
  if (row.validityType === 'LONG_TERM') return `${formatDateTime(row.validFrom) || '未设置'} 起长期有效`
  if (!row.validFrom && !row.validUntil) return '未设置'
  return `${validityTypeLabel(row.validityType || 'LIMITED')} · ${formatDateTime(row.validFrom) || '未设置'} 至 ${formatDateTime(row.validUntil) || '未设置'}`
}

function openAddressCreate() {
  editingAddressId.value = null
  addressMode.value = 'create'
  Object.assign(addressForm, {
    addressType: 'IP',
    addressValue: '',
    displayName: '',
    purpose: null,
    remark: null,
    rowVersion: null
  })
  addressFormError.value = ''
  addressDialogOpen.value = true
}

function openAddressEdit(row: ExternalNetworkAddress) {
  editingAddressId.value = row.id
  addressMode.value = 'edit'
  Object.assign(addressForm, {
    addressType: row.addressType,
    addressValue: row.addressValue,
    displayName: row.displayName,
    purpose: row.purpose,
    remark: row.remark,
    rowVersion: row.rowVersion
  })
  addressFormError.value = ''
  addressDialogOpen.value = true
}

async function submitAddressForm() {
  if (addressSubmitting.value) return
  if (!addressForm.addressValue.trim() || !addressForm.displayName.trim()) {
    addressFormError.value = '请填写地址值和显示名称'
    return
  }
  addressSubmitting.value = true
  addressFormError.value = ''
  try {
    const payload = {
      addressType: addressForm.addressType,
      addressValue: addressForm.addressValue.trim(),
      displayName: addressForm.displayName.trim(),
      purpose: addressForm.purpose?.trim() || null,
      remark: addressForm.remark?.trim() || null
    }
    if (addressMode.value === 'create') {
      await createExternalNetworkAddress(payload)
      ElMessage.success('外部网络地址已创建')
    } else if (editingAddressId.value && addressForm.rowVersion !== null) {
      await updateExternalNetworkAddress(editingAddressId.value, { ...payload, rowVersion: addressForm.rowVersion })
      ElMessage.success('外部网络地址已更新')
    }
    addressDialogOpen.value = false
    void loadAddresses()
    void loadLookups()
  } catch (error) {
    addressFormError.value = apiErrorMessage(error, '保存外部网络地址失败')
  } finally {
    addressSubmitting.value = false
  }
}

async function changeAddressStatus(row: ExternalNetworkAddress, next: EnvironmentRecordStatus) {
  const action = next === 'ACTIVE' ? '重新启用' : '停用'
  try {
    await ElMessageBox.confirm(`${action}「${row.displayName}」？停用后不能被新网络访问申请选择。`, `${action}外部网络地址`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: 'warning'
    })
    if (next === 'ACTIVE') await reactivateExternalNetworkAddress(row.id)
    else await deactivateExternalNetworkAddress(row.id)
    ElMessage.success(`${action}成功`)
    void loadAddresses()
    void loadLookups()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, `${action}外部网络地址失败`))
  }
}

function openRuleCreate() {
  editingRuleId.value = null
  ruleMode.value = 'create'
  Object.assign(ruleForm, {
    ruleCode: '',
    ruleName: '',
    sourceNetworkZoneId: null,
    targetNetworkZoneId: null,
    protocol: 'TCP',
    ports: '',
    validityType: 'LONG_TERM',
    validFrom: currentDateTimeValue(),
    validUntil: null,
    remark: null,
    rowVersion: null
  })
  ruleFormError.value = ''
  ruleDialogOpen.value = true
  if (!networkZoneOptions.value.length) void loadLookups()
}

function openRuleEdit(row: NetworkAccessExemptionRule) {
  editingRuleId.value = row.id
  ruleMode.value = 'edit'
  Object.assign(ruleForm, {
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    sourceNetworkZoneId: row.sourceNetworkZoneId,
    targetNetworkZoneId: row.targetNetworkZoneId,
    protocol: row.protocol,
    ports: row.ports,
    validityType: row.validityType,
    validFrom: row.validFrom,
    validUntil: row.validUntil,
    remark: row.remark,
    rowVersion: row.rowVersion
  })
  ruleFormError.value = ''
  ruleDialogOpen.value = true
}

async function submitRuleForm() {
  if (ruleSubmitting.value) return
  ruleFormError.value = ''
  if (!ruleForm.ruleCode.trim() || !ruleForm.ruleName.trim()) {
    ruleFormError.value = '请填写规则编码和规则名称'
    return
  }
  if (!ruleForm.sourceNetworkZoneId || !ruleForm.targetNetworkZoneId) {
    ruleFormError.value = '请选择来源和目标网络分区'
    return
  }
  if (!ruleForm.ports.trim()) {
    ruleFormError.value = '请填写协议端口'
    return
  }
  if (!validateValidity(ruleForm.validityType, ruleForm.validFrom, ruleForm.validUntil, message => { ruleFormError.value = message })) {
    return
  }
  ruleSubmitting.value = true
  try {
    const payload = {
      ruleCode: ruleForm.ruleCode.trim(),
      ruleName: ruleForm.ruleName.trim(),
      sourceNetworkZoneId: ruleForm.sourceNetworkZoneId,
      targetNetworkZoneId: ruleForm.targetNetworkZoneId,
      protocol: ruleForm.protocol,
      ports: ruleForm.ports.trim(),
      validityType: ruleForm.validityType,
      validFrom: ruleForm.validFrom,
      validUntil: ruleForm.validityType === 'LONG_TERM' ? null : ruleForm.validUntil,
      remark: ruleForm.remark?.trim() || null,
      rowVersion: ruleForm.rowVersion
    }
    if (ruleMode.value === 'create') {
      await createNetworkAccessExemptionRule(payload)
      ElMessage.success('免申请规则已创建')
    } else if (editingRuleId.value && ruleForm.rowVersion !== null) {
      await updateNetworkAccessExemptionRule(editingRuleId.value, payload)
      ElMessage.success('免申请规则已更新')
    }
    ruleDialogOpen.value = false
    void loadExemptionRules()
  } catch (error) {
    ruleFormError.value = apiErrorMessage(error, '保存免申请规则失败')
  } finally {
    ruleSubmitting.value = false
  }
}

async function changeRuleStatus(row: NetworkAccessExemptionRule, next: NetworkAccessExemptionRuleStatus) {
  const action = next === 'ACTIVE' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`${action}免申请规则「${row.ruleName}」？`, `${action}免申请规则`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: 'warning'
    })
    if (next === 'ACTIVE') await enableNetworkAccessExemptionRule(row.id, row.rowVersion)
    else await disableNetworkAccessExemptionRule(row.id, row.rowVersion)
    ElMessage.success(`${action}成功`)
    void loadExemptionRules()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(apiErrorMessage(error, `${action}免申请规则失败`))
  }
}

watch(canView, allowed => {
  if (allowed) void loadAll()
}, { immediate: true })

watch(() => ({
  open: applicationDialogOpen.value,
  actionType: applicationForm.actionType,
  sourceKind: applicationForm.source.kind,
  sourcePhysicalSubsystemId: applicationForm.source.physicalSubsystemId,
  sourceEnvironmentId: applicationForm.source.environmentId,
  sourceDeploymentUnitId: applicationForm.source.deploymentUnitId,
  sourceExternalAddressId: applicationForm.source.externalAddressId,
  sourceInstanceIds: [...applicationForm.source.instanceIds].sort((a, b) => a - b).join(','),
  sourceLoading: applicationForm.source.loading,
  targetKind: applicationForm.target.kind,
  targetPhysicalSubsystemId: applicationForm.target.physicalSubsystemId,
  targetEnvironmentId: applicationForm.target.environmentId,
  targetDeploymentUnitId: applicationForm.target.deploymentUnitId,
  targetExternalAddressId: applicationForm.target.externalAddressId,
  targetInstanceIds: [...applicationForm.target.instanceIds].sort((a, b) => a - b).join(','),
  targetLoading: applicationForm.target.loading,
  protocol: applicationForm.protocol,
  ports: applicationForm.ports,
  validityType: applicationForm.validityType,
  validFrom: applicationForm.validFrom,
  validUntil: applicationForm.validUntil
}), () => {
  scheduleApplicationDecision()
})
</script>

<template>
  <main class="architecture-page architecture-network-access-page">
    <UiPageHeader title="网络访问关系" description="提交来源和目标端点的访问申请，批准后形成 RDDMP 内部关系快照；真实网络策略仍由外部平台或线下流程执行。">
      <template #actions>
        <div class="architecture-page__actions">
          <el-button v-if="canManage" @click="openAddressCreate"><el-icon><Plus /></el-icon>外部地址</el-button>
          <el-button v-if="canApply" type="primary" @click="openApplicationCreate()"><el-icon><Plus /></el-icon>访问申请</el-button>
        </div>
      </template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无网络访问查看权限" sub-title="请申请 architecture:network-access:view、apply 或 manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="网络访问数据加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="loadAll">重新加载</el-button></template>
      </el-result>
    </section>
    <template v-else>
      <el-tabs v-model="activeTab" class="architecture-network-tabs">
        <el-tab-pane label="访问申请" name="applications" />
        <el-tab-pane label="访问关系" name="relations" />
        <el-tab-pane label="外部地址" name="addresses" />
        <el-tab-pane label="免申请规则" name="rules" />
      </el-tabs>

      <template v-if="activeTab === 'applications'">
        <UiToolbar>
          <el-select v-model="applicationFilters.status" clearable placeholder="申请状态" class="architecture-filter-select" @change="applicationSearch">
            <el-option v-for="status in applicationStatusOptions" :key="status" :label="applicationStatusLabels[status]" :value="status" />
          </el-select>
          <el-button type="primary" @click="applicationSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetApplications">重置</el-button>
          <template #actions>
            <el-tooltip content="刷新网络访问数据">
              <el-button circle :loading="loading" aria-label="刷新网络访问数据" @click="refresh"><el-icon><Refresh /></el-icon></el-button>
            </el-tooltip>
          </template>
        </UiToolbar>

        <UiDataTable v-if="applications.length || applicationLoading" class="architecture-desktop-table" :data="applications" :loading="applicationLoading" row-key="id" border>
          <el-table-column label="申请" min-width="160">
            <template #default="scope">
              <button type="button" class="architecture-table-identity" @click="openApplicationDetail(scope.row)">
                <strong>{{ scope.row.applicationNo }}</strong>
                <small>{{ actionTypeLabel(scope.row.actionType) }} · 申请人 #{{ scope.row.applicantId }}</small>
              </button>
            </template>
          </el-table-column>
          <el-table-column label="动作" width="90"><template #default="scope">{{ actionTypeLabel(scope.row.actionType) }}</template></el-table-column>
          <el-table-column label="来源" min-width="220"><template #default="scope">{{ snapshotSummary(scope.row.sourceSnapshotJson, scope.row.sourceKind) }}</template></el-table-column>
          <el-table-column label="目标" min-width="220"><template #default="scope">{{ snapshotSummary(scope.row.targetSnapshotJson, scope.row.targetKind) }}</template></el-table-column>
          <el-table-column label="协议端口" width="130"><template #default="scope">{{ scope.row.protocol }} / {{ scope.row.ports }}</template></el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="applicationStatusLabels" :tone="applicationStatusTone(scope.row.status)" /></template></el-table-column>
          <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="scope">
              <div class="architecture-table-actions">
                <el-button link type="primary" @click="openApplicationDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
                <el-button v-if="canApply && (scope.row.status === 'DRAFT' || scope.row.status === 'RETURNED')" link type="primary" @click="submitApplication(scope.row)">提交</el-button>
                <el-button v-if="canManage && scope.row.status === 'IN_REVIEW'" link type="primary" @click="approveApplication(scope.row)">批准</el-button>
                <el-button v-if="canManage && scope.row.status === 'IN_REVIEW'" link type="danger" @click="rejectApplication(scope.row)">拒绝</el-button>
                <el-button v-if="canApply && (scope.row.status === 'DRAFT' || scope.row.status === 'RETURNED' || scope.row.status === 'IN_REVIEW')" link type="warning" @click="cancelApplication(scope.row)">取消</el-button>
              </div>
            </template>
          </el-table-column>
        </UiDataTable>

        <div v-if="applications.length || applicationLoading" v-loading="applicationLoading" class="architecture-mobile-list" :class="{ 'is-loading': applicationLoading }">
          <article v-for="row in applications" :key="row.id">
            <header>
              <button type="button" class="architecture-table-identity" @click="openApplicationDetail(row)">
                <strong>{{ row.applicationNo }}</strong>
                <small>{{ actionTypeLabels[row.actionType] }} · {{ row.protocol }} / {{ row.ports }}</small>
              </button>
              <UiStatusTag :value="row.status" :labels="applicationStatusLabels" :tone="applicationStatusTone(row.status)" />
            </header>
            <dl>
              <div class="is-wide"><dt>来源</dt><dd>{{ snapshotSummary(row.sourceSnapshotJson, row.sourceKind) }}</dd></div>
              <div class="is-wide"><dt>目标</dt><dd>{{ snapshotSummary(row.targetSnapshotJson, row.targetKind) }}</dd></div>
              <div><dt>动作</dt><dd>{{ actionTypeLabels[row.actionType] }}</dd></div>
              <div class="is-wide"><dt>用途</dt><dd>{{ row.purpose }}</dd></div>
              <div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div>
            </dl>
            <footer>
              <el-button link type="primary" @click="openApplicationDetail(row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button v-if="canApply && (row.status === 'DRAFT' || row.status === 'RETURNED')" link type="primary" @click="submitApplication(row)">提交</el-button>
              <el-button v-if="canManage && row.status === 'IN_REVIEW'" link type="primary" @click="approveApplication(row)">批准</el-button>
              <el-button v-if="canManage && row.status === 'IN_REVIEW'" link type="danger" @click="rejectApplication(row)">拒绝</el-button>
              <el-button v-if="canApply && (row.status === 'DRAFT' || row.status === 'RETURNED' || row.status === 'IN_REVIEW')" link type="warning" @click="cancelApplication(row)">取消</el-button>
            </footer>
          </article>
        </div>

        <UiEmptyState v-if="!applicationLoading && !applications.length" title="暂无网络访问申请" description="当前筛选下没有申请记录。">
          <template #action>
            <el-button v-if="canApply" type="primary" @click="openApplicationCreate()">新建访问申请</el-button>
            <el-button v-else @click="resetApplications">清空筛选</el-button>
          </template>
        </UiEmptyState>

        <nav v-if="applications.length || applicationPage > 1" class="architecture-change-pagination" aria-label="网络访问申请分页">
          <span>第 {{ applicationPage }} 页</span>
          <div><el-button :disabled="applicationPage <= 1 || applicationLoading" @click="previousApplications">上一页</el-button><el-button :disabled="!hasNextApplications || applicationLoading" @click="nextApplications">下一页</el-button></div>
        </nav>
      </template>

      <template v-else-if="activeTab === 'relations'">
        <UiToolbar>
          <el-select v-model="relationFilters.status" clearable placeholder="关系状态" class="architecture-filter-select" @change="relationSearch">
            <el-option v-for="status in relationStatusOptions" :key="status" :label="relationStatusLabels[status]" :value="status" />
          </el-select>
          <el-button type="primary" @click="relationSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetRelations">重置</el-button>
          <template #actions>
            <el-tooltip content="刷新网络访问数据">
              <el-button circle :loading="loading" aria-label="刷新网络访问数据" @click="refresh"><el-icon><Refresh /></el-icon></el-button>
            </el-tooltip>
          </template>
        </UiToolbar>

        <UiDataTable v-if="relations.length || relationLoading" class="architecture-desktop-table" :data="relations" :loading="relationLoading" row-key="id" border>
          <el-table-column label="关系" min-width="160">
            <template #default="scope">
              <button type="button" class="architecture-table-identity" @click="openRelationDetail(scope.row)">
                <strong>{{ scope.row.relationNo }}</strong>
                <small>来自申请 #{{ scope.row.applicationId }}</small>
              </button>
            </template>
          </el-table-column>
          <el-table-column label="来源快照" min-width="230" show-overflow-tooltip><template #default="scope">{{ snapshotDetail(scope.row.sourceSnapshotJson, scope.row.sourceKind) }}</template></el-table-column>
          <el-table-column label="目标快照" min-width="230" show-overflow-tooltip><template #default="scope">{{ snapshotDetail(scope.row.targetSnapshotJson, scope.row.targetKind) }}</template></el-table-column>
          <el-table-column label="协议端口" width="130"><template #default="scope">{{ scope.row.protocol }} / {{ scope.row.ports }}</template></el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="130">
            <template #default="scope">
              <div class="architecture-table-actions">
                <UiStatusTag :value="scope.row.status" :labels="relationStatusLabels" :tone="relationStatusTone(scope.row.status)" />
                <el-tag v-if="scope.row.hasOfflineEndpointRisk" type="warning" size="small">下线端点</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="scope">
              <div class="architecture-table-actions">
                <el-button link type="primary" @click="openRelationDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
                <el-button v-if="canApply && scope.row.status === 'ACTIVE'" link type="primary" @click="openRelationLifecycleApplication(scope.row, 'MODIFY')">变更</el-button>
                <el-button v-if="canApply && scope.row.status === 'ACTIVE' && scope.row.validityType === 'LIMITED'" link type="primary" @click="openRelationLifecycleApplication(scope.row, 'RENEW')">续期</el-button>
                <el-button v-if="canApply && scope.row.status === 'ACTIVE'" link type="warning" @click="openRelationLifecycleApplication(scope.row, 'CLOSE')">关闭</el-button>
              </div>
            </template>
          </el-table-column>
        </UiDataTable>

        <div v-if="relations.length || relationLoading" v-loading="relationLoading" class="architecture-mobile-list" :class="{ 'is-loading': relationLoading }">
          <article v-for="row in relations" :key="row.id">
            <header>
              <button type="button" class="architecture-table-identity" @click="openRelationDetail(row)">
                <strong>{{ row.relationNo }}</strong>
                <small>{{ row.protocol }} / {{ row.ports }}</small>
              </button>
              <div class="architecture-table-actions">
                <UiStatusTag :value="row.status" :labels="relationStatusLabels" :tone="relationStatusTone(row.status)" />
                <el-tag v-if="row.hasOfflineEndpointRisk" type="warning" size="small">下线端点</el-tag>
              </div>
            </header>
            <dl>
              <div class="is-wide"><dt>来源快照</dt><dd>{{ snapshotDetail(row.sourceSnapshotJson, row.sourceKind) }}</dd></div>
              <div class="is-wide"><dt>目标快照</dt><dd>{{ snapshotDetail(row.targetSnapshotJson, row.targetKind) }}</dd></div>
              <div><dt>有效期</dt><dd>{{ validityText(row) }}</dd></div>
              <div class="is-wide"><dt>用途</dt><dd>{{ row.purpose }}</dd></div>
              <div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div>
            </dl>
            <footer>
              <el-button link type="primary" @click="openRelationDetail(row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button v-if="canApply && row.status === 'ACTIVE'" link type="primary" @click="openRelationLifecycleApplication(row, 'MODIFY')">变更</el-button>
              <el-button v-if="canApply && row.status === 'ACTIVE' && row.validityType === 'LIMITED'" link type="primary" @click="openRelationLifecycleApplication(row, 'RENEW')">续期</el-button>
              <el-button v-if="canApply && row.status === 'ACTIVE'" link type="warning" @click="openRelationLifecycleApplication(row, 'CLOSE')">关闭</el-button>
            </footer>
          </article>
        </div>

        <UiEmptyState v-if="!relationLoading && !relations.length" title="暂无网络访问关系" description="批准网络访问申请后会形成关系快照。" />
        <nav v-if="relations.length || relationPage > 1" class="architecture-change-pagination" aria-label="网络访问关系分页">
          <span>第 {{ relationPage }} 页</span>
          <div><el-button :disabled="relationPage <= 1 || relationLoading" @click="previousRelations">上一页</el-button><el-button :disabled="!hasNextRelations || relationLoading" @click="nextRelations">下一页</el-button></div>
        </nav>
      </template>

      <template v-else-if="activeTab === 'addresses'">
        <UiToolbar>
          <el-input v-model="addressFilters.keyword" clearable placeholder="地址或名称" class="architecture-filter-input" @keyup.enter="addressSearch"><template #prefix><el-icon><Search /></el-icon></template></el-input>
          <el-select v-model="addressFilters.status" clearable placeholder="地址状态" class="architecture-filter-select" @change="addressSearch">
            <el-option v-for="status in addressStatusOptions" :key="status" :label="environmentStatusLabels[status]" :value="status" />
          </el-select>
          <el-button type="primary" @click="addressSearch">查询</el-button>
          <el-button @click="resetAddresses">重置</el-button>
          <template #actions>
            <el-tooltip content="刷新网络访问数据">
              <el-button circle :loading="loading" aria-label="刷新网络访问数据" @click="refresh"><el-icon><Refresh /></el-icon></el-button>
            </el-tooltip>
          </template>
        </UiToolbar>

        <UiDataTable v-if="addresses.length || addressLoading" class="architecture-desktop-table" :data="addresses" :loading="addressLoading" row-key="id" border>
          <el-table-column label="外部地址" min-width="220">
            <template #default="scope">
              <button type="button" class="architecture-table-identity" @click="openAddressDetail(scope.row)">
                <strong>{{ scope.row.displayName }}</strong>
                <small>{{ addressTypeLabel(scope.row.addressType) }} · {{ scope.row.addressValue }}</small>
              </button>
            </template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="environmentStatusLabels" :tone="environmentStatusTone(scope.row.status)" /></template></el-table-column>
          <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="scope">
              <div class="architecture-table-actions">
                <el-button link type="primary" @click="openAddressDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
                <el-button v-if="canManage" link type="primary" @click="openAddressEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
                <el-button v-if="canManage && scope.row.status === 'ACTIVE'" link type="warning" @click="changeAddressStatus(scope.row, 'INACTIVE')">停用</el-button>
                <el-button v-else-if="canManage" link type="primary" @click="changeAddressStatus(scope.row, 'ACTIVE')">启用</el-button>
              </div>
            </template>
          </el-table-column>
        </UiDataTable>

        <div v-if="addresses.length || addressLoading" v-loading="addressLoading" class="architecture-mobile-list" :class="{ 'is-loading': addressLoading }">
          <article v-for="row in addresses" :key="row.id">
            <header>
              <button type="button" class="architecture-table-identity" @click="openAddressDetail(row)">
                <strong>{{ row.displayName }}</strong>
                <small>{{ addressTypeLabel(row.addressType) }} · {{ row.addressValue }}</small>
              </button>
              <UiStatusTag :value="row.status" :labels="environmentStatusLabels" :tone="environmentStatusTone(row.status)" />
            </header>
            <dl>
              <div class="is-wide"><dt>用途</dt><dd>{{ row.purpose || '—' }}</dd></div>
              <div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div>
            </dl>
            <footer>
              <el-button link type="primary" @click="openAddressDetail(row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button v-if="canManage" link type="primary" @click="openAddressEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-button v-if="canManage && row.status === 'ACTIVE'" link type="warning" @click="changeAddressStatus(row, 'INACTIVE')">停用</el-button>
              <el-button v-else-if="canManage" link type="primary" @click="changeAddressStatus(row, 'ACTIVE')">启用</el-button>
            </footer>
          </article>
        </div>

        <UiEmptyState v-if="!addressLoading && !addresses.length" title="暂无外部网络地址" description="外部地址用于网络访问申请的来源或目标端点。">
          <template #action>
            <el-button v-if="canManage" type="primary" @click="openAddressCreate">新建外部地址</el-button>
            <el-button v-else @click="resetAddresses">清空筛选</el-button>
          </template>
        </UiEmptyState>
      </template>

      <template v-else>
        <UiToolbar>
          <el-select v-model="ruleFilters.status" clearable placeholder="规则状态" class="architecture-filter-select" @change="ruleSearch">
            <el-option v-for="status in ruleStatusOptions" :key="status" :label="ruleStatusLabels[status]" :value="status" />
          </el-select>
          <el-button type="primary" @click="ruleSearch"><el-icon><Search /></el-icon>查询</el-button>
          <el-button @click="resetRules">重置</el-button>
          <template #actions>
            <el-button v-if="canManage" type="primary" @click="openRuleCreate"><el-icon><Plus /></el-icon>新建规则</el-button>
            <el-tooltip content="刷新网络访问数据">
              <el-button circle :loading="loading" aria-label="刷新网络访问数据" @click="refresh"><el-icon><Refresh /></el-icon></el-button>
            </el-tooltip>
          </template>
        </UiToolbar>

        <UiDataTable v-if="exemptionRules.length || ruleLoading" class="architecture-desktop-table" :data="exemptionRules" :loading="ruleLoading" row-key="id" border>
          <el-table-column label="规则" min-width="220">
            <template #default="scope">
              <button type="button" class="architecture-table-identity" @click="openRuleEdit(scope.row)">
                <strong>{{ scope.row.ruleName }}</strong>
                <small>{{ scope.row.ruleCode }}</small>
              </button>
            </template>
          </el-table-column>
          <el-table-column label="来源分区" min-width="160"><template #default="scope">{{ scope.row.sourceNetworkZoneName }}</template></el-table-column>
          <el-table-column label="目标分区" min-width="160"><template #default="scope">{{ scope.row.targetNetworkZoneName }}</template></el-table-column>
          <el-table-column label="协议端口" width="140"><template #default="scope">{{ scope.row.protocol }} / {{ scope.row.ports }}</template></el-table-column>
          <el-table-column label="有效期" min-width="180"><template #default="scope">{{ validityText(scope.row) }}</template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="ruleStatusLabels" :tone="ruleStatusTone(scope.row.status)" /></template></el-table-column>
          <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <div class="architecture-table-actions">
                <el-button v-if="canManage" link type="primary" @click="openRuleEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
                <el-button v-if="canManage && scope.row.status === 'ACTIVE'" link type="warning" @click="changeRuleStatus(scope.row, 'DISABLED')">停用</el-button>
                <el-button v-else-if="canManage" link type="primary" @click="changeRuleStatus(scope.row, 'ACTIVE')">启用</el-button>
              </div>
            </template>
          </el-table-column>
        </UiDataTable>

        <div v-if="exemptionRules.length || ruleLoading" v-loading="ruleLoading" class="architecture-mobile-list" :class="{ 'is-loading': ruleLoading }">
          <article v-for="row in exemptionRules" :key="row.id">
            <header>
              <button type="button" class="architecture-table-identity" @click="openRuleEdit(row)">
                <strong>{{ row.ruleName }}</strong>
                <small>{{ row.ruleCode }}</small>
              </button>
              <UiStatusTag :value="row.status" :labels="ruleStatusLabels" :tone="ruleStatusTone(row.status)" />
            </header>
            <dl>
              <div><dt>来源分区</dt><dd>{{ row.sourceNetworkZoneName }}</dd></div>
              <div><dt>目标分区</dt><dd>{{ row.targetNetworkZoneName }}</dd></div>
              <div><dt>协议端口</dt><dd>{{ row.protocol }} / {{ row.ports }}</dd></div>
              <div class="is-wide"><dt>有效期</dt><dd>{{ validityText(row) }}</dd></div>
            </dl>
            <footer v-if="canManage">
              <el-button link type="primary" @click="openRuleEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-button v-if="row.status === 'ACTIVE'" link type="warning" @click="changeRuleStatus(row, 'DISABLED')">停用</el-button>
              <el-button v-else link type="primary" @click="changeRuleStatus(row, 'ACTIVE')">启用</el-button>
            </footer>
          </article>
        </div>

        <UiEmptyState v-if="!ruleLoading && !exemptionRules.length" title="暂无免申请规则" description="免申请规则只用于明确边界内的无需申请判定。">
          <template #action>
            <el-button v-if="canManage" type="primary" @click="openRuleCreate">新建免申请规则</el-button>
            <el-button v-else @click="resetRules">清空筛选</el-button>
          </template>
        </UiEmptyState>
      </template>
    </template>

    <el-drawer v-model="applicationDetailOpen" title="网络访问申请详情" size="min(720px, 92vw)" destroy-on-close>
      <section v-if="selectedApplication" class="architecture-drawer-body">
        <header class="architecture-detail-heading">
          <strong>{{ selectedApplication.applicationNo }}</strong>
          <span>申请人 #{{ selectedApplication.applicantId }} · {{ applicationStatusLabels[selectedApplication.status] }}</span>
        </header>

        <dl class="architecture-detail-list">
          <div>
            <dt>协议端口</dt>
            <dd>{{ selectedApplication.protocol }} / {{ selectedApplication.ports }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd><UiStatusTag :value="selectedApplication.status" :labels="applicationStatusLabels" :tone="applicationStatusTone(selectedApplication.status)" /></dd>
          </div>
          <div>
            <dt>申请动作</dt>
            <dd>{{ actionTypeLabels[selectedApplication.actionType] }}</dd>
          </div>
          <div>
            <dt>目标关系</dt>
            <dd>{{ selectedApplication.targetRelationId ? `#${selectedApplication.targetRelationId}` : '—' }}</dd>
          </div>
          <div class="is-wide">
            <dt>用途</dt>
            <dd>{{ selectedApplication.purpose }}</dd>
          </div>
          <div class="is-wide">
            <dt>有效期</dt>
            <dd>{{ validityText(selectedApplication) }}</dd>
          </div>
          <div class="is-wide">
            <dt>办理说明</dt>
            <dd>{{ selectedApplication.processDescription || '—' }}</dd>
          </div>
          <div>
            <dt>业务轮次</dt>
            <dd>第 {{ selectedApplication.currentBusinessRound }} 轮</dd>
          </div>
          <div>
            <dt>流程实例</dt>
            <dd>{{ selectedApplication.currentWorkflowInstanceId ? `#${selectedApplication.currentWorkflowInstanceId}` : '—' }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatDateTime(selectedApplication.createdAt) }}</dd>
          </div>
          <div>
            <dt>最后更新</dt>
            <dd>{{ formatDateTime(selectedApplication.updatedAt) }}</dd>
          </div>
        </dl>

        <section class="architecture-drawer-section">
          <header><strong>来源端点</strong><span class="architecture-muted">{{ endpointKindLabels[selectedApplication.sourceKind] }}</span></header>
          <ul class="architecture-network-snapshot-list">
            <li v-for="(item, index) in snapshotItems(selectedApplication.sourceSnapshotJson)" :key="`source-${index}`">
              <strong>{{ snapshotItemTitle(item, selectedApplication.sourceKind) }}</strong>
              <span>{{ snapshotItemMeta(item, selectedApplication.sourceKind) }}</span>
            </li>
          </ul>
          <p v-if="!snapshotItems(selectedApplication.sourceSnapshotJson).length" class="architecture-muted">暂无来源快照</p>
        </section>

        <section class="architecture-drawer-section">
          <header><strong>目标端点</strong><span class="architecture-muted">{{ endpointKindLabels[selectedApplication.targetKind] }}</span></header>
          <ul class="architecture-network-snapshot-list">
            <li v-for="(item, index) in snapshotItems(selectedApplication.targetSnapshotJson)" :key="`target-${index}`">
              <strong>{{ snapshotItemTitle(item, selectedApplication.targetKind) }}</strong>
              <span>{{ snapshotItemMeta(item, selectedApplication.targetKind) }}</span>
            </li>
          </ul>
          <p v-if="!snapshotItems(selectedApplication.targetSnapshotJson).length" class="architecture-muted">暂无目标快照</p>
        </section>
      </section>
    </el-drawer>

    <el-drawer v-model="relationDetailOpen" title="网络访问关系详情" size="min(720px, 92vw)" destroy-on-close>
      <section v-if="selectedRelation" class="architecture-drawer-body">
        <header class="architecture-detail-heading">
          <strong>{{ selectedRelation.relationNo }}</strong>
          <span>来自申请 #{{ selectedRelation.applicationId }} · {{ relationStatusLabels[selectedRelation.status] }}</span>
        </header>

        <dl class="architecture-detail-list">
          <div>
            <dt>协议端口</dt>
            <dd>{{ selectedRelation.protocol }} / {{ selectedRelation.ports }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd><UiStatusTag :value="selectedRelation.status" :labels="relationStatusLabels" :tone="relationStatusTone(selectedRelation.status)" /></dd>
          </div>
          <div>
            <dt>替代来源</dt>
            <dd>{{ selectedRelation.replacesRelationId ? `#${selectedRelation.replacesRelationId}` : '—' }}</dd>
          </div>
          <div>
            <dt>被替代为</dt>
            <dd>{{ selectedRelation.replacedByRelationId ? `#${selectedRelation.replacedByRelationId}` : '—' }}</dd>
          </div>
          <div class="is-wide">
            <dt>用途</dt>
            <dd>{{ selectedRelation.purpose }}</dd>
          </div>
          <div class="is-wide">
            <dt>有效期</dt>
            <dd>{{ validityText(selectedRelation) }}</dd>
          </div>
          <div class="is-wide">
            <dt>办理说明</dt>
            <dd>{{ selectedRelation.processDescription || '—' }}</dd>
          </div>
          <div class="is-wide">
            <dt>关闭原因</dt>
            <dd>{{ selectedRelation.closeReason || '—' }}</dd>
          </div>
          <div>
            <dt>关闭方式</dt>
            <dd>{{ closeTypeText(selectedRelation.closeType) }}</dd>
          </div>
          <div>
            <dt>关闭申请</dt>
            <dd>{{ selectedRelation.closedApplicationId ? `#${selectedRelation.closedApplicationId}` : '—' }}</dd>
          </div>
          <div class="is-wide">
            <dt>下线端点风险</dt>
            <dd>{{ selectedRelation.hasOfflineEndpointRisk ? selectedRelation.offlineEndpointSummaries.join('；') : '无' }}</dd>
          </div>
          <div>
            <dt>关闭时间</dt>
            <dd>{{ formatDateTime(selectedRelation.closedAt) }}</dd>
          </div>
          <div>
            <dt>最后更新</dt>
            <dd>{{ formatDateTime(selectedRelation.updatedAt) }}</dd>
          </div>
        </dl>

        <section class="architecture-drawer-section">
          <header><strong>来源快照</strong><span class="architecture-muted">{{ endpointKindLabels[selectedRelation.sourceKind] }}</span></header>
          <ul class="architecture-network-snapshot-list">
            <li v-for="(item, index) in snapshotItems(selectedRelation.sourceSnapshotJson)" :key="`relation-source-${index}`">
              <strong>{{ snapshotItemTitle(item, selectedRelation.sourceKind) }}</strong>
              <span>{{ snapshotItemMeta(item, selectedRelation.sourceKind) }}</span>
            </li>
          </ul>
          <p v-if="!snapshotItems(selectedRelation.sourceSnapshotJson).length" class="architecture-muted">暂无来源快照</p>
        </section>

        <section class="architecture-drawer-section">
          <header><strong>目标快照</strong><span class="architecture-muted">{{ endpointKindLabels[selectedRelation.targetKind] }}</span></header>
          <ul class="architecture-network-snapshot-list">
            <li v-for="(item, index) in snapshotItems(selectedRelation.targetSnapshotJson)" :key="`relation-target-${index}`">
              <strong>{{ snapshotItemTitle(item, selectedRelation.targetKind) }}</strong>
              <span>{{ snapshotItemMeta(item, selectedRelation.targetKind) }}</span>
            </li>
          </ul>
          <p v-if="!snapshotItems(selectedRelation.targetSnapshotJson).length" class="architecture-muted">暂无目标快照</p>
        </section>
      </section>
    </el-drawer>

    <el-drawer v-model="addressDetailOpen" title="外部网络地址详情" size="min(560px, 92vw)" destroy-on-close>
      <section v-if="selectedAddress" class="architecture-drawer-body">
        <header class="architecture-detail-heading">
          <strong>{{ selectedAddress.displayName }}</strong>
          <span>{{ addressTypeLabel(selectedAddress.addressType) }} · {{ selectedAddress.addressValue }}</span>
        </header>

        <dl class="architecture-detail-list">
          <div>
            <dt>地址类型</dt>
            <dd>{{ addressTypeLabel(selectedAddress.addressType) }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd><UiStatusTag :value="selectedAddress.status" :labels="environmentStatusLabels" :tone="environmentStatusTone(selectedAddress.status)" /></dd>
          </div>
          <div class="is-wide">
            <dt>地址值</dt>
            <dd>{{ selectedAddress.addressValue }}</dd>
          </div>
          <div class="is-wide">
            <dt>用途</dt>
            <dd>{{ selectedAddress.purpose || '—' }}</dd>
          </div>
          <div class="is-wide">
            <dt>备注</dt>
            <dd>{{ selectedAddress.remark || '—' }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatDateTime(selectedAddress.createdAt) }}</dd>
          </div>
          <div>
            <dt>最后更新</dt>
            <dd>{{ formatDateTime(selectedAddress.updatedAt) }}</dd>
          </div>
        </dl>
      </section>
    </el-drawer>

    <el-dialog v-model="applicationDialogOpen" :title="applicationDialogTitle" width="min(1080px, 96vw)" destroy-on-close>
      <el-form label-position="top" class="architecture-network-access-form">
        <section class="architecture-form-section">
          <h3>申请动作</h3>
          <div class="architecture-form-grid">
            <el-form-item label="动作类型">
              <el-radio-group v-model="applicationForm.actionType">
                <el-radio-button v-for="type in actionTypeOptions" :key="type" :label="type">{{ actionTypeLabels[type] }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="applicationForm.actionType !== 'OPEN'" label="目标访问关系">
              <el-select v-model="applicationForm.targetRelationId" filterable placeholder="请选择" @change="changeApplicationTargetRelation">
                <el-option v-for="item in activeRelations" :key="item.id" :label="`${item.relationNo} · ${item.protocol}/${item.ports}`" :value="item.id" />
              </el-select>
            </el-form-item>
          </div>
        </section>

        <div v-if="applicationForm.actionType === 'OPEN' || applicationForm.actionType === 'MODIFY'" class="architecture-network-endpoint-grid">
          <section class="architecture-network-endpoint-card">
            <header><strong>来源端点</strong></header>
            <el-form-item label="端点类型">
              <el-radio-group v-model="applicationForm.source.kind" @change="changeEndpointKind(applicationForm.source, $event)">
                <el-radio-button label="MANAGED">托管实例</el-radio-button>
                <el-radio-button label="EXTERNAL">外部地址</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <template v-if="applicationForm.source.kind === 'MANAGED'">
              <el-form-item label="物理子系统">
                <el-select v-model="applicationForm.source.physicalSubsystemId" filterable placeholder="请选择" @change="loadEndpointDeploymentUnits(applicationForm.source)">
                  <el-option v-for="item in physicalOptions" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="具体环境">
                <el-select v-model="applicationForm.source.environmentId" filterable placeholder="请选择" @change="loadEndpointInstances(applicationForm.source, applicationForm.target)">
                  <el-option v-for="item in environments" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="部署单元">
                <el-select v-model="applicationForm.source.deploymentUnitId" filterable placeholder="请选择" :loading="applicationForm.source.loading" @change="loadEndpointInstances(applicationForm.source, applicationForm.target)">
                  <el-option v-for="item in applicationForm.source.deploymentUnits" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="实例集合">
                <el-select v-model="applicationForm.source.instanceIds" multiple collapse-tags collapse-tags-tooltip filterable placeholder="默认选择当前在用实例" :loading="applicationForm.source.loading" @change="changeEndpointInstances(applicationForm.source, applicationForm.target)">
                  <el-option v-for="item in applicationForm.source.instances" :key="item.id" :label="endpointInstanceLabel(item)" :value="item.id" :disabled="isEndpointInstanceDisabled(item, applicationForm.target)" />
                </el-select>
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="外部网络地址">
                <el-select v-model="applicationForm.source.externalAddressId" filterable placeholder="请选择">
                  <el-option v-for="item in addressOptions" :key="item.id" :label="`${item.displayName} · ${item.addressValue}`" :value="item.id" />
                </el-select>
              </el-form-item>
            </template>
          </section>

          <section class="architecture-network-endpoint-card">
            <header><strong>目标端点</strong></header>
            <el-form-item label="端点类型">
              <el-radio-group v-model="applicationForm.target.kind" @change="changeEndpointKind(applicationForm.target, $event)">
                <el-radio-button label="MANAGED">托管实例</el-radio-button>
                <el-radio-button label="EXTERNAL">外部地址</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <template v-if="applicationForm.target.kind === 'MANAGED'">
              <el-form-item label="物理子系统">
                <el-select v-model="applicationForm.target.physicalSubsystemId" filterable placeholder="请选择" @change="loadEndpointDeploymentUnits(applicationForm.target)">
                  <el-option v-for="item in physicalOptions" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="具体环境">
                <el-select v-model="applicationForm.target.environmentId" filterable placeholder="请选择" @change="loadEndpointInstances(applicationForm.target, applicationForm.source)">
                  <el-option v-for="item in environments" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="部署单元">
                <el-select v-model="applicationForm.target.deploymentUnitId" filterable placeholder="请选择" :loading="applicationForm.target.loading" @change="loadEndpointInstances(applicationForm.target, applicationForm.source)">
                  <el-option v-for="item in applicationForm.target.deploymentUnits" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="实例集合">
                <el-select v-model="applicationForm.target.instanceIds" multiple collapse-tags collapse-tags-tooltip filterable placeholder="默认选择当前在用实例" :loading="applicationForm.target.loading" @change="changeEndpointInstances(applicationForm.target, applicationForm.source)">
                  <el-option v-for="item in applicationForm.target.instances" :key="item.id" :label="endpointInstanceLabel(item)" :value="item.id" :disabled="isEndpointInstanceDisabled(item, applicationForm.source)" />
                </el-select>
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="外部网络地址">
                <el-select v-model="applicationForm.target.externalAddressId" filterable placeholder="请选择">
                  <el-option v-for="item in addressOptions" :key="item.id" :label="`${item.displayName} · ${item.addressValue}`" :value="item.id" />
                </el-select>
              </el-form-item>
            </template>
          </section>
        </div>

        <section class="architecture-form-section">
          <h3>访问内容</h3>
          <div class="architecture-form-grid">
            <el-form-item v-if="applicationForm.actionType !== 'CLOSE'" label="协议">
              <el-select v-model="applicationForm.protocol">
                <el-option v-for="protocol in protocolOptions" :key="protocol" :label="protocol" :value="protocol" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="applicationForm.actionType !== 'CLOSE'" label="端口"><el-input v-model="applicationForm.ports" maxlength="120" placeholder="如 443 或 8080,8443" /></el-form-item>
            <el-form-item class="is-wide" label="访问用途"><el-input v-model="applicationForm.purpose" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
            <el-form-item v-if="applicationForm.actionType !== 'CLOSE'" label="有效期类型">
              <el-radio-group v-model="applicationForm.validityType" @change="onValidityTypeChange(applicationForm)">
                <el-radio-button v-for="type in validityTypeOptions" :key="type" :label="type">{{ validityTypeLabels[type] }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="applicationForm.actionType !== 'CLOSE'" label="有效期开始"><el-date-picker v-model="applicationForm.validFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="请选择" style="width: 100%;" /></el-form-item>
            <el-form-item v-if="applicationForm.actionType !== 'CLOSE' && applicationForm.validityType === 'LIMITED'" label="有效期结束"><el-date-picker v-model="applicationForm.validUntil" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="请选择" style="width: 100%;" /></el-form-item>
            <el-form-item class="is-wide" label="办理说明"><el-input v-model="applicationForm.processDescription" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
          </div>
        </section>
        <section v-if="applicationDecisionRelevant" class="architecture-network-inline-decision">
          <header>
            <strong>访问判定</strong>
            <el-button size="small" :loading="applicationDecisionLoading" :disabled="!applicationDecisionReady" @click="evaluateApplicationFormDecision(true)">重新判定</el-button>
          </header>
          <section
            v-if="applicationDecisionResult"
            class="architecture-network-decision-result"
            :class="decisionResultClass(applicationDecisionResult)"
          >
            <header>
              <strong>{{ decisionTitleText(applicationDecisionResult) }}</strong>
            </header>
            <p class="architecture-network-decision-result__reason">{{ decisionReasonText(applicationDecisionResult) }}</p>
            <p class="architecture-network-decision-result__code">原因代码：{{ decisionReasonCodeText(applicationDecisionResult) }}</p>
          </section>
          <el-alert
            v-else-if="applicationDecisionError"
            type="error"
            :closable="false"
            show-icon
            :title="applicationDecisionError"
          />
          <p v-else-if="applicationDecisionLoading" class="architecture-form-hint">判定中</p>
          <p v-else class="architecture-form-hint">{{ applicationDecisionReady ? '等待判定结果' : '补齐来源、目标、协议端口和有效期后判定' }}</p>
        </section>
        <el-alert v-if="applicationFormError" class="architecture-form-error" type="error" :closable="false" show-icon :title="applicationFormError" />
      </el-form>
      <template #footer>
        <el-button @click="applicationDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="applicationSubmitting" :disabled="applicationDecisionLoading || applicationDecisionBlocksSubmit" @click="submitApplicationForm">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="addressDialogOpen" :title="addressMode === 'create' ? '新建外部网络地址' : '编辑外部网络地址'" width="min(560px, 94vw)" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="地址类型">
          <el-select v-model="addressForm.addressType" style="width: 100%;">
            <el-option v-for="(label, value) in addressTypeLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址值"><el-input v-model="addressForm.addressValue" maxlength="255" placeholder="IP、CIDR 或域名" /></el-form-item>
        <el-form-item label="显示名称"><el-input v-model="addressForm.displayName" maxlength="160" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="addressForm.purpose" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="addressForm.remark" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
        <el-alert v-if="addressFormError" class="architecture-form-error" type="error" :closable="false" show-icon :title="addressFormError" />
      </el-form>
      <template #footer>
        <el-button @click="addressDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="addressSubmitting" @click="submitAddressForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ruleDialogOpen" :title="ruleMode === 'create' ? '新建免申请规则' : '编辑免申请规则'" width="min(760px, 94vw)" destroy-on-close>
      <el-form label-position="top" class="architecture-network-access-form">
        <div class="architecture-form-grid">
          <el-form-item label="规则编码"><el-input v-model="ruleForm.ruleCode" maxlength="96" /></el-form-item>
          <el-form-item label="规则名称"><el-input v-model="ruleForm.ruleName" maxlength="160" /></el-form-item>
          <el-form-item label="来源网络分区">
            <el-select v-model="ruleForm.sourceNetworkZoneId" filterable placeholder="请选择">
              <el-option v-for="item in networkZoneOptions" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标网络分区">
            <el-select v-model="ruleForm.targetNetworkZoneId" filterable placeholder="请选择">
              <el-option v-for="item in networkZoneOptions" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="协议">
            <el-select v-model="ruleForm.protocol">
              <el-option v-for="protocol in protocolOptions" :key="protocol" :label="protocol" :value="protocol" />
            </el-select>
          </el-form-item>
          <el-form-item label="端口"><el-input v-model="ruleForm.ports" maxlength="120" placeholder="如 443 或 8080,8443-8445" /></el-form-item>
          <el-form-item label="有效期类型">
            <el-radio-group v-model="ruleForm.validityType" @change="onValidityTypeChange(ruleForm)">
              <el-radio-button v-for="type in validityTypeOptions" :key="type" :label="type">{{ validityTypeLabels[type] }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="有效期开始"><el-date-picker v-model="ruleForm.validFrom" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="请选择" style="width: 100%;" /></el-form-item>
          <el-form-item v-if="ruleForm.validityType === 'LIMITED'" label="有效期结束"><el-date-picker v-model="ruleForm.validUntil" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="请选择" style="width: 100%;" /></el-form-item>
          <el-form-item class="is-wide" label="备注"><el-input v-model="ruleForm.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
        </div>
        <el-alert v-if="ruleFormError" class="architecture-form-error" type="error" :closable="false" show-icon :title="ruleFormError" />
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="ruleSubmitting" @click="submitRuleForm">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>
