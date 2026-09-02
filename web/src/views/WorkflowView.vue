<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Delete, EditPen, MoreFilled, Plus, Refresh, View } from '@element-plus/icons-vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import WorkflowDesigner from '../components/workflow/WorkflowDesigner.vue'
import WorkflowNodeInspector from '../components/workflow/WorkflowNodeInspector.vue'
import { archiveWorkflowDefinition, createWorkflowDefinition, defaultWorkflowGraph, deleteWorkflowDefinition, deleteWorkflowInstance, getWorkflowDefinition, getWorkflowDefinitionVersion, getWorkflowInstanceDetail, getWorkflowProjectOptions, listWorkflowDefinitionEvents, listWorkflowDefinitions, listWorkflowDefinitionVersions, listWorkflowInstances, publishWorkflowDefinition, restoreWorkflowDefinition, unpublishWorkflowDefinition, updateWorkflowDefinition, type WorkflowAssigneeType, type WorkflowDefinition, type WorkflowDefinitionEvent, type WorkflowDefinitionVersion, type WorkflowEdgeModel, type WorkflowGraph, type WorkflowInstance, type WorkflowNodeModel, type WorkflowNodeState, type WorkflowPage, type WorkflowScopeType } from '../api/workflow'
import { getRoleOptions, listSystem } from '../api/system'
import type { RoleOption, SystemRow } from '../types/system'
import { formatDateOnly } from '../utils/date'
import { apiErrorMessage } from '../api/error'
import { useProjectContextStore } from '../stores/project-context'

const route = useRoute()
const projectContext = useProjectContextStore()
const section = computed(() => String(route.params.section || 'definitions') === 'monitor' ? 'monitor' : 'definitions')
const isMonitor = computed(() => section.value === 'monitor')
const definitions = ref<WorkflowDefinition[]>([])
const instances = ref<WorkflowInstance[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const monitorFilters = reactive({ businessKey: '', definitionKeyword: '', status: '', starterKeyword: '', createdRange: [] as string[] })
const monitorOpen = ref(false)
const monitorLoading = ref(false)
const monitorInstance = ref<WorkflowInstance | null>(null)
const monitorGraph = ref<WorkflowGraph>(defaultWorkflowGraph())
const monitorNodeStates = ref<WorkflowNodeState[]>([])
const monitorNodeStatuses = ref<Record<string, string>>({})
const users = ref<{ id: number; username: string; display_name: string; org_name?: string }[]>([])
const roles = ref<RoleOption[]>([])
const definitionScopeFilter = ref<WorkflowScopeType>('PROJECT')
const scopeType = ref<WorkflowScopeType>('PROJECT')
const optionsScopeKey = ref('')
const loading = ref(false)
const optionsLoading = ref(false)
const designerOpen = ref(false)
const designerReadonly = ref(false)
const designerFullscreen = ref(false)
const editingDefinitionId = ref<number | null>(null)
const viewingDefinitionId = ref<number | null>(null)
const designerTab = ref<'current' | 'versions' | 'events'>('current')
const historyLoading = ref(false)
const definitionVersions = ref<WorkflowDefinitionVersion[]>([])
const definitionEvents = ref<WorkflowDefinitionEvent[]>([])
const selectedHistoryVersion = ref<number | null>(null)
const historyGraph = ref<WorkflowGraph>(defaultWorkflowGraph())
const saving = ref(false)
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const graph = ref<WorkflowGraph>(defaultWorkflowGraph())
const form = reactive({ code: '', name: '' })

function normalizeWorkflowPage<T>(value: unknown, fallbackPage: number, fallbackSize: number): WorkflowPage<T> {
  if (Array.isArray(value)) {
    return { records: value as T[], total: value.length, page: fallbackPage, size: fallbackSize }
  }
  if (value && typeof value === 'object') {
    const candidate = value as Partial<WorkflowPage<T>>
    if (Array.isArray(candidate.records)) {
      return {
        records: candidate.records,
        total: Number.isFinite(Number(candidate.total)) ? Number(candidate.total) : candidate.records.length,
        page: Number.isFinite(Number(candidate.page)) ? Number(candidate.page) : fallbackPage,
        size: Number.isFinite(Number(candidate.size)) ? Number(candidate.size) : fallbackSize
      }
    }
  }
  return { records: [], total: 0, page: fallbackPage, size: fallbackSize }
}

const selectedNode = computed(() => graph.value.nodes.find(node => node.id === selectedNodeId.value) || null)
const selectedEdge = computed(() => graph.value.edges.find(edge => edge.id === selectedEdgeId.value) || null)
const monitorCurrentNodes = computed(() => monitorGraph.value.nodes.filter(node => monitorNodeStatuses.value[node.id] === 'ACTIVE').map(node => node.label))
const approvalRecords = computed(() => [...monitorNodeStates.value].sort((left, right) => {
  const leftTime = Date.parse(String(left.completed_at || left.created_at || '')) || 0
  const rightTime = Date.parse(String(right.completed_at || right.created_at || '')) || 0
  return rightTime - leftTime || Number(right.id) - Number(left.id)
}))
const designerTitle = computed(() => editingDefinitionId.value ? '编辑流程图：' + form.name : viewingDefinitionId.value ? '查看流程：' + form.name : '新建流程定义')
const detailDialogTitle = computed(() => '流程监控：' + (monitorInstance.value?.business_key || ''))
type WorkflowStatusTone = 'primary' | 'success' | 'warning' | 'danger' | 'info'
function workflowStatusTone(status: string): WorkflowStatusTone {
  return ({ RUNNING: 'primary', PENDING: 'warning', APPROVED: 'success', SENT: 'info', REJECTED: 'danger', RETURNED: 'warning', TERMINATED: 'info', CANCELLED: 'info', DRAFT: 'warning', PUBLISHED: 'success', ARCHIVED: 'info' }[String(status).toUpperCase()] || 'info') as WorkflowStatusTone
}
function handleMobileDefinitionCommand(row: WorkflowDefinition, command: string) {
  if (command === 'CREATE_FROM_TEMPLATE') return createFromTemplate(row)
  if (command === 'PUBLISH') return publish(row)
  if (command === 'UNPUBLISH') return unpublish(row)
  if (command === 'ARCHIVE') return archiveDefinition(row)
  if (command === 'RESTORE') return restoreDefinition(row)
  if (command === 'DELETE') return removeDefinition(row)
}
function handleMobileInstanceCommand(row: WorkflowInstance, command: string) {
  if (command === 'DELETE') return removeInstance(row)
}

async function load() {
  loading.value = true
  try {
    const pageQuery = { page: page.value, size: pageSize.value, projectRef: projectContext.currentRef || undefined }
    if (isMonitor.value) {
      const response = await listWorkflowInstances({
        ...pageQuery,
        businessKey: monitorFilters.businessKey.trim() || undefined,
        definitionKeyword: monitorFilters.definitionKeyword.trim() || undefined,
        status: monitorFilters.status || undefined,
        starterKeyword: monitorFilters.starterKeyword.trim() || undefined,
        createdFrom: monitorFilters.createdRange[0] || undefined,
        createdTo: monitorFilters.createdRange[1] || undefined
      })
      const result = normalizeWorkflowPage<WorkflowInstance>(response.data.data, page.value, pageSize.value)
      instances.value = result.records
      total.value = result.total
    } else {
      const response = await listWorkflowDefinitions({ ...pageQuery, scopeType: definitionScopeFilter.value })
      const result = normalizeWorkflowPage<WorkflowDefinition>(response.data.data, page.value, pageSize.value)
      definitions.value = result.records
      total.value = result.total
    }
  } catch (error) { ElMessage.error(apiErrorMessage(error, '工作流数据加载失败')) } finally { loading.value = false }
}
function onPageChange(value: number) {
  page.value = value
  void load()
}
function onPageSizeChange(value: number) {
  pageSize.value = value
  page.value = 1
  void load()
}
function searchMonitor() {
  page.value = 1
  void load()
}
function resetMonitorFilters() {
  monitorFilters.businessKey = ''
  monitorFilters.definitionKeyword = ''
  monitorFilters.status = ''
  monitorFilters.starterKeyword = ''
  monitorFilters.createdRange = []
  page.value = 1
  void load()
}
async function loadOptions(force = false) {
  const scopeKey = `${scopeType.value}:${projectContext.currentRef}`
  if ((!force && optionsScopeKey.value === scopeKey) || optionsLoading.value) return
  optionsLoading.value = true
  try {
    if (scopeType.value === 'PROJECT') {
      if (!projectContext.currentRef) throw new Error('当前没有可用项目')
      const response = await getWorkflowProjectOptions(projectContext.currentRef)
      users.value = response.data.data.members
      roles.value = response.data.data.roles
    } else if (scopeType.value === 'PLATFORM') {
      const [userResponse, roleResponse] = await Promise.all([listSystem('users', { page: 1, size: 500 }), getRoleOptions()])
      users.value = userResponse.data.data.records.map((row: SystemRow) => ({ id: Number(row.id), username: String(row.username || ''), display_name: String(row.display_name || row.username || ''), org_name: row.org_name ? String(row.org_name) : undefined }))
      roles.value = roleResponse.data.data
    } else {
      users.value = []
      roles.value = []
    }
    optionsScopeKey.value = scopeKey
  } catch (error) { ElMessage.error(apiErrorMessage(error, '审批人选项加载失败')) } finally { optionsLoading.value = false }
}
function parseWorkflowGraph(value: string | WorkflowGraph): WorkflowGraph {
  const parsed = (typeof value === 'string' ? JSON.parse(value) : value) as WorkflowGraph
  if (!parsed || !Array.isArray(parsed.nodes) || !Array.isArray(parsed.edges)) throw new Error('流程图数据格式无效')
  return { schemaVersion: 2, nodes: parsed.nodes || [], edges: parsed.edges || [], variables: parsed.variables || [], formBindings: parsed.formBindings || [] }
}
function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 19)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
function definitionEventLabel(eventType: string) {
  return ({ DEFINITION_CREATED: '创建', DEFINITION_UPDATED: '修改', DEFINITION_PUBLISHED: '发布', DEFINITION_UNPUBLISHED: '取消发布', DEFINITION_ARCHIVED: '归档', DEFINITION_RESTORED: '恢复发布', DEFINITION_DELETED: '删除草稿' }[eventType] || eventType)
}
function openCreate() {
  editingDefinitionId.value = null
  viewingDefinitionId.value = null
  designerTab.value = 'current'
  definitionVersions.value = []
  definitionEvents.value = []
  selectedHistoryVersion.value = null
  form.code = ''
  form.name = ''
  scopeType.value = definitionScopeFilter.value === 'PROJECT' && !projectContext.currentRef ? 'TEMPLATE' : definitionScopeFilter.value
  graph.value = graphForScope(defaultWorkflowGraph(), scopeType.value)
  selectedNodeId.value = null
  selectedEdgeId.value = null
  designerReadonly.value = false
  designerFullscreen.value = false
  designerOpen.value = true
  void loadOptions()
}
function graphForScope(value: WorkflowGraph, scope: WorkflowScopeType): WorkflowGraph {
  return {
    ...value,
    nodes: value.nodes.map(node => {
      if (node.type === 'APPROVAL') {
        const assigneeType: WorkflowAssigneeType = node.config.assigneeType === 'STARTER'
          ? 'STARTER'
          : scope === 'PROJECT' ? 'PROJECT_MEMBER' : scope === 'TEMPLATE' ? 'TEMPLATE_PLACEHOLDER' : 'USER'
        return { ...node, config: { ...node.config, assigneeType, assigneeIds: [] } }
      }
      if (node.type === 'CC') {
        return { ...node, config: { ...node.config, userIds: [], templatePlaceholder: scope === 'TEMPLATE' } }
      }
      return node
    })
  }
}
function changeScope(value: string | number | boolean) {
  scopeType.value = String(value) as WorkflowScopeType
  graph.value = graphForScope(graph.value, scopeType.value)
  selectedNodeId.value = null
  selectedEdgeId.value = null
  optionsScopeKey.value = ''
  void loadOptions(true)
}
async function loadDefinitionHistory(definitionId: number) {
  historyLoading.value = true
  try {
    const [versionsResponse, eventsResponse] = await Promise.all([
      listWorkflowDefinitionVersions(definitionId),
      listWorkflowDefinitionEvents(definitionId)
    ])
    definitionVersions.value = versionsResponse.data.data || []
    definitionEvents.value = eventsResponse.data.data || []
  } catch (error) {
    definitionVersions.value = []
    definitionEvents.value = []
    ElMessage.error(apiErrorMessage(error, '流程历史加载失败'))
  } finally { historyLoading.value = false }
}
async function openHistoryVersion(versionNo: number) {
  if (!viewingDefinitionId.value) return
  historyLoading.value = true
  try {
    const detail = (await getWorkflowDefinitionVersion(viewingDefinitionId.value, versionNo)).data.data
    if (!detail.definition_json) throw new Error('历史流程图为空')
    historyGraph.value = parseWorkflowGraph(detail.definition_json)
    selectedHistoryVersion.value = versionNo
  } catch (error) { ElMessage.error(apiErrorMessage(error, '历史流程图加载失败')) } finally { historyLoading.value = false }
}
async function openView(row: { id: number; name: string; status: string }) {
  try {
    const detail = (await getWorkflowDefinition(row.id)).data.data
    graph.value = parseWorkflowGraph(detail.definition_json)
    form.code = detail.code
    form.name = detail.name
    scopeType.value = detail.scope_type || 'PLATFORM'
    selectedNodeId.value = null
    selectedEdgeId.value = null
    viewingDefinitionId.value = row.id
    editingDefinitionId.value = row.status === 'DRAFT' ? row.id : null
    designerReadonly.value = row.status !== 'DRAFT'
    designerFullscreen.value = false
    designerTab.value = 'current'
    selectedHistoryVersion.value = null
    designerOpen.value = true
    void loadOptions()
    void loadDefinitionHistory(row.id)
  } catch (error) { ElMessage.error(apiErrorMessage(error, '流程图加载失败')) }
}
async function createFromTemplate(row: WorkflowDefinition) {
  if (!projectContext.currentRef) {
    ElMessage.warning('请先选择项目')
    return
  }
  try {
    const detail = (await getWorkflowDefinition(row.id)).data.data
    editingDefinitionId.value = null
    viewingDefinitionId.value = null
    designerTab.value = 'current'
    definitionVersions.value = []
    definitionEvents.value = []
    selectedHistoryVersion.value = null
    form.code = detail.code
    form.name = detail.name
    scopeType.value = 'PROJECT'
    graph.value = graphForScope(parseWorkflowGraph(detail.definition_json), 'PROJECT')
    selectedNodeId.value = graph.value.nodes.find(node => node.type === 'APPROVAL' && node.config.assigneeType !== 'STARTER')?.id || null
    selectedEdgeId.value = null
    designerReadonly.value = false
    designerFullscreen.value = false
    designerOpen.value = true
    optionsScopeKey.value = ''
    await loadOptions(true)
  } catch (error) { ElMessage.error(apiErrorMessage(error, '模板加载失败')) }
}
function updateSelectedNode(node: WorkflowNodeModel) { graph.value = { ...graph.value, nodes: graph.value.nodes.map(item => item.id === node.id ? node : item) } }
function updateSelectedEdge(edge: WorkflowEdgeModel) {
  const source = graph.value.nodes.find(node => node.id === edge.source)
  const isConditional = source?.type === 'CONDITION'
  const next = { ...edge, condition: isConditional && !edge.default && edge.condition?.trim() ? edge.condition.trim() : null, default: isConditional && Boolean(edge.default) }
  const edges = graph.value.edges.map(item => {
    if (item.id === next.id) return next
    return next.default && item.source === next.source ? { ...item, default: false } : item
  })
  const nodes = graph.value.nodes.map(node => {
    if (node.id !== next.source || node.type !== 'CONDITION') return node
    const config = { ...node.config }
    if (next.default) config.defaultEdgeId = next.id
    else if (config.defaultEdgeId === next.id) delete config.defaultEdgeId
    return { ...node, config }
  })
  graph.value = { ...graph.value, nodes, edges }
}
function hasPositiveIds(value: unknown) { return Array.isArray(value) && value.length > 0 && value.every(item => Number(item) > 0) }
function graphForSave(): WorkflowGraph {
  const defaultEdgeBySource = new Map<string, string>()
  graph.value.nodes.filter(node => node.type === 'CONDITION').forEach(node => {
    const configured = node.config?.defaultEdgeId
    const edge = graph.value.edges.find(item => item.source === node.id && (Boolean(item.default) || item.id === configured))
    if (edge) defaultEdgeBySource.set(node.id, edge.id)
  })
  const edges = graph.value.edges.map(edge => {
    const source = graph.value.nodes.find(node => node.id === edge.source)
    const isConditional = source?.type === 'CONDITION'
    const isDefault = isConditional && defaultEdgeBySource.get(edge.source) === edge.id
    return { ...edge, condition: isConditional && !isDefault && edge.condition?.trim() ? edge.condition.trim() : null, default: isDefault }
  })
  const nodes = graph.value.nodes.map(node => {
    if (node.type !== 'CONDITION') return node
    const config = { ...node.config }
    const defaultEdgeId = defaultEdgeBySource.get(node.id)
    if (defaultEdgeId) config.defaultEdgeId = defaultEdgeId
    else delete config.defaultEdgeId
    return { ...node, config }
  })
  return { ...graph.value, nodes, edges }
}
function validateGraphBeforeSave() {
  const invalidNode = graph.value.nodes.find(node => {
    if (node.type === 'APPROVAL') {
      const config = node.config || {}
      const assigneeType = String(config.assigneeType || '').toUpperCase()
      if (['USER', 'ROLE', 'PROJECT_MEMBER', 'PROJECT_ROLE'].includes(assigneeType) && !hasPositiveIds(config.assigneeIds)) return true
      if (assigneeType === 'FORM_FIELD' && !String(config.fieldName || '').trim()) return true
      if (assigneeType === 'EXPRESSION' && !String(config.expression || '').trim()) return true
      if (!['USER', 'ROLE', 'PROJECT_MEMBER', 'PROJECT_ROLE', 'TEMPLATE_PLACEHOLDER', 'ORG_OWNER', 'STARTER', 'FORM_FIELD', 'EXPRESSION'].includes(assigneeType)) return true
      if (scopeType.value === 'TEMPLATE' && !['TEMPLATE_PLACEHOLDER', 'STARTER'].includes(assigneeType)) return true
      if (scopeType.value !== 'TEMPLATE' && assigneeType === 'TEMPLATE_PLACEHOLDER') return true
    }
    if (node.type === 'CC' && scopeType.value === 'TEMPLATE') return !node.config?.templatePlaceholder || Boolean(node.config?.userIds?.length)
    if (node.type === 'CC' && !hasPositiveIds(node.config?.userIds)) return true
    return false
  })
  if (invalidNode) {
    ElMessage.warning(invalidNode.type === 'CC' ? '节点“' + invalidNode.label + '”必须配置抄送人员' : '节点“' + invalidNode.label + '”必须配置审批人或角色')
    selectedNodeId.value = invalidNode.id
    selectedEdgeId.value = null
    return false
  }
  return true
}
async function save() {
  if (!form.code.trim() || !form.name.trim()) { ElMessage.warning('请填写流程编码和流程名称'); return }
  if (!validateGraphBeforeSave()) return
  saving.value = true
  try {
    const payload = { code: form.code.trim(), name: form.name.trim(), definitionJson: JSON.stringify(graphForSave()), scopeType: scopeType.value, projectRef: scopeType.value === 'PROJECT' ? projectContext.currentRef : undefined }
    if (editingDefinitionId.value) await updateWorkflowDefinition(editingDefinitionId.value, payload)
    else await createWorkflowDefinition(payload)
    designerOpen.value = false
    ElMessage.success(editingDefinitionId.value ? '流程草稿已更新' : '流程定义已保存，请在列表中发布')
    await load()
  }
  catch (error) { ElMessage.error(apiErrorMessage(error, '流程定义保存失败')) } finally { saving.value = false }
}
async function publish(row: { id: number; name: string }) {
  try { await ElMessageBox.confirm(`确认发布流程“${row.name}”吗？发布后运行实例使用固定版本。`, '发布流程', { type: 'warning' }); await publishWorkflowDefinition(row.id); ElMessage.success('流程已发布'); await load() }
  catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '流程发布失败')) }
}
async function unpublish(row: { id: number; name: string }) {
  try {
    await ElMessageBox.confirm(`确认取消发布流程“${row.name}”吗？取消发布后会保留历史版本，并生成可编辑草稿。`, '取消发布流程', { type: 'warning' })
    await unpublishWorkflowDefinition(row.id)
    ElMessage.success('流程已取消发布，可重新编辑')
    await load()
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '流程取消发布失败'))
  }
}
async function archiveDefinition(row: { id: number; name: string }) {
  try {
    const result = await ElMessageBox.prompt(`归档后流程“${row.name}”不能再发起新实例，运行中实例不受影响。`, '归档流程', { inputPlaceholder: '请输入归档原因', inputValidator: value => Boolean(value?.trim()) || '归档原因不能为空', type: 'warning' })
    await archiveWorkflowDefinition(row.id, result.value.trim())
    ElMessage.success('流程已归档')
    await load()
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '流程归档失败'))
  }
}
async function restoreDefinition(row: { id: number; name: string }) {
  try {
    const result = await ElMessageBox.prompt(`恢复后流程“${row.name}”将继续使用归档前的已发布版本。`, '恢复发布', { inputPlaceholder: '请输入恢复原因', inputValidator: value => Boolean(value?.trim()) || '恢复原因不能为空', type: 'warning' })
    await restoreWorkflowDefinition(row.id, result.value.trim())
    ElMessage.success('流程已恢复发布')
    await load()
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '流程恢复失败'))
  }
}
async function removeDefinition(row: { id: number; name: string; status: string }) {
  if (row.status !== 'DRAFT') { ElMessage.warning('只有草稿流程可以删除'); return }
  try { await ElMessageBox.confirm(`确认删除草稿流程“${row.name}”吗？删除后不可恢复。`, '删除流程', { type: 'warning' }); await deleteWorkflowDefinition(row.id); ElMessage.success('草稿流程已删除'); await load() }
  catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '流程删除失败')) }
}
function statusRank(status: string) { return ({ UNREACHED: 0, DONE: 1, CANCELLED: 2, REJECTED: 3, ACTIVE: 4 }[status] || 0) }
function buildMonitorStatuses(nodes: WorkflowNodeModel[], states: WorkflowNodeState[], instanceStatus: string) {
  const statuses: Record<string, string> = {}
  const nodeById = new Map(nodes.map(node => [node.id, node]))
  const gatewayTypes = new Set(['CONDITION', 'PARALLEL_SPLIT', 'PARALLEL_JOIN'])
  const visited = new Set<string>()
  nodes.forEach(node => { statuses[node.id] = 'UNREACHED' })
  states.forEach(state => {
    const nodeId = state.node_id || state.task_key
    if (!nodeId || !nodeById.has(nodeId)) return
    visited.add(nodeId)
    const status = state.status === 'PENDING' ? 'ACTIVE' : state.status === 'APPROVED' || state.status === 'SENT' ? 'DONE' : state.status === 'REJECTED' || state.status === 'RETURNED' ? 'REJECTED' : state.status === 'CANCELLED' ? 'CANCELLED' : 'UNREACHED'
    if (statusRank(status) >= statusRank(statuses[nodeId])) statuses[nodeId] = status
  })
  const start = nodes.find(node => node.type === 'START')
  if (start) {
    visited.add(start.id)
    statuses[start.id] = 'DONE'
  }
  if (instanceStatus === 'APPROVED') {
    const end = nodes.find(node => node.type === 'END')
    if (end) visited.add(end.id)
  }

  // Task rows identify the executed branch. Walk backwards through that branch so
  // gateways and the end event show as completed even though they have no task row.
  let changed = true
  while (changed) {
    changed = false
    monitorGraph.value.edges.forEach(edge => {
      const source = nodeById.get(edge.source)
      if (!source || !visited.has(edge.target)) return
      if (gatewayTypes.has(source.type) || source.type === 'START') {
        if (!visited.has(source.id)) {
          visited.add(source.id)
          changed = true
        }
      }
    })
  }
  nodes.forEach(node => {
    if (gatewayTypes.has(node.type) && visited.has(node.id)) statuses[node.id] = 'DONE'
    if (node.type === 'END' && visited.has(node.id)) statuses[node.id] = 'DONE'
  })
  return statuses
}
async function openInstanceDetail(instanceId: number) {
  monitorLoading.value = true
  try {
    const detail = (await getWorkflowInstanceDetail(instanceId)).data.data
    const rawDefinition = detail.definition_json
    const parsed = (typeof rawDefinition === 'string' ? JSON.parse(rawDefinition) : rawDefinition) as WorkflowGraph
    if (!parsed || !Array.isArray(parsed.nodes) || !Array.isArray(parsed.edges)) throw new Error('流程图数据格式无效')
    monitorInstance.value = detail.instance
    monitorGraph.value = { schemaVersion: 2, nodes: parsed.nodes || [], edges: parsed.edges || [], variables: parsed.variables || [], formBindings: parsed.formBindings || [] }
    monitorNodeStates.value = detail.node_states || []
    monitorNodeStatuses.value = buildMonitorStatuses(monitorGraph.value.nodes, monitorNodeStates.value, String(detail.instance.status || ''))
    monitorOpen.value = true
  } catch (error) { ElMessage.error(apiErrorMessage(error, '流程监控详情加载失败')) } finally { monitorLoading.value = false }
}
async function openMonitor(row: WorkflowInstance) { await openInstanceDetail(row.id) }
async function removeInstance(row: WorkflowInstance) {
  try {
    await ElMessageBox.confirm('确认删除流程实例“' + row.business_key + '”吗？删除后实例不再出现在监控列表中，审批历史会保留。', '删除流程实例', { type: 'warning' })
    await deleteWorkflowInstance(row.id)
    ElMessage.success('流程实例已删除')
    await load()
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '流程实例删除失败'))
  }
}
watch(section, () => { page.value = 1; void load() })
watch(() => projectContext.currentRef, () => {
  if (!projectContext.currentRef && definitionScopeFilter.value === 'PROJECT') definitionScopeFilter.value = 'TEMPLATE'
  page.value = 1
  optionsScopeKey.value = ''
  void load()
})
watch(definitionScopeFilter, () => { page.value = 1; void load() })
onMounted(async () => {
  await projectContext.initialize()
  if (!projectContext.currentRef) definitionScopeFilter.value = 'TEMPLATE'
  await load()
})
</script>

<template>
  <section class="workflow-page">
    <div v-if="isMonitor" class="workflow-monitor-filters">
      <el-input v-model="monitorFilters.businessKey" clearable placeholder="业务单号" @keyup.enter="searchMonitor" />
      <el-input v-model="monitorFilters.definitionKeyword" clearable placeholder="流程名称或编码" @keyup.enter="searchMonitor" />
      <el-select v-model="monitorFilters.status" clearable placeholder="流程状态" @change="searchMonitor">
        <el-option label="运行中" value="RUNNING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已拒绝" value="REJECTED" />
        <el-option label="已退回" value="RETURNED" />
        <el-option label="已终止" value="TERMINATED" />
      </el-select>
      <el-input v-model="monitorFilters.starterKeyword" clearable placeholder="发起人" @keyup.enter="searchMonitor" />
      <el-date-picker v-model="monitorFilters.createdRange" type="daterange" value-format="YYYY-MM-DD" format="YYYY-MM-DD" range-separator="至" start-placeholder="发起开始日期" end-placeholder="发起结束日期" />
      <el-button type="primary" @click="searchMonitor"><el-icon><View /></el-icon>查询</el-button>
      <el-button @click="resetMonitorFilters">重置</el-button>
    </div>
    <UiToolbar><div class="ui-toolbar__filters"><el-segmented v-if="!isMonitor" v-model="definitionScopeFilter" :options="[{ label: '本项目流程', value: 'PROJECT', disabled: !projectContext.currentRef }, { label: '全局模板', value: 'TEMPLATE' }, { label: '平台流程', value: 'PLATFORM' }]" /><el-tag v-if="projectContext.current" effect="plain">{{ projectContext.current.name }}</el-tag></div><template #actions><el-button @click="load"><el-icon><Refresh /></el-icon>刷新</el-button><el-button v-if="!isMonitor" type="primary" @click="openCreate"><el-icon><Plus /></el-icon>{{ definitionScopeFilter === 'TEMPLATE' ? '新建模板' : '新建流程' }}</el-button></template></UiToolbar>

    <UiDataTable v-if="!isMonitor" class="workflow-table workflow-table--desktop" :data="definitions" :loading="loading" row-key="id" border>
      <el-table-column prop="code" label="流程编码" min-width="160" class-name="workflow-primary-key" label-class-name="workflow-primary-key"><template #default="scope"><span class="workflow-primary-key__value">{{ scope.row.code }}</span></template></el-table-column><el-table-column prop="name" label="流程名称" min-width="180" /><el-table-column label="范围" width="120"><template #default="scope"><UiStatusTag :value="scope.row.scope_type" :labels="{ PROJECT: '当前项目', TEMPLATE: '全局模板', PLATFORM: '平台流程' }" :tone="scope.row.scope_type === 'PROJECT' ? 'primary' : 'info'" /></template></el-table-column><el-table-column prop="current_version" label="当前版本" width="110" /><el-table-column prop="model_schema_version" label="模型版本" width="110" /><el-table-column label="状态" width="120"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }" :tone="workflowStatusTone(scope.row.status)" /></template></el-table-column><el-table-column label="操作" width="460"><template #default="scope"><div class="workflow-table-actions workflow-table-actions--definitions"><el-button link type="primary" @click="openView(scope.row)"><el-icon><EditPen v-if="scope.row.status === 'DRAFT'" /><View v-else /></el-icon>{{ scope.row.status === 'DRAFT' ? '编辑流程图' : '查看详情' }}</el-button><el-button v-if="scope.row.scope_type === 'TEMPLATE'" link type="primary" @click="createFromTemplate(scope.row)"><el-icon><Plus /></el-icon>创建项目流程</el-button><el-button v-if="scope.row.scope_type !== 'TEMPLATE' && scope.row.status === 'DRAFT'" link type="primary" @click="publish(scope.row)"><el-icon><CircleCheck /></el-icon>发布</el-button><el-button v-if="scope.row.scope_type !== 'TEMPLATE' && scope.row.status === 'PUBLISHED'" link type="warning" @click="unpublish(scope.row)"><el-icon><Refresh /></el-icon>取消发布</el-button><el-button v-if="scope.row.scope_type !== 'TEMPLATE' && scope.row.status === 'PUBLISHED'" link type="danger" @click="archiveDefinition(scope.row)"><el-icon><Delete /></el-icon>归档</el-button><el-button v-if="scope.row.scope_type !== 'TEMPLATE' && scope.row.status === 'ARCHIVED'" link type="primary" @click="restoreDefinition(scope.row)"><el-icon><Refresh /></el-icon>恢复发布</el-button><el-button v-if="scope.row.status === 'DRAFT'" link type="danger" @click="removeDefinition(scope.row)"><el-icon><Delete /></el-icon>删除草稿</el-button></div></template></el-table-column>
      <template #footer><div class="workflow-table-footer"><span class="muted">共 {{ total }} 个流程定义</span><UiPagination :page="page" :page-size="pageSize" :total="total" @update:page="onPageChange" @update:page-size="onPageSizeChange" /></div></template>
    </UiDataTable>

    <section v-if="!isMonitor" class="workflow-mobile-list" v-loading="loading">
      <article v-for="row in definitions" :key="row.id" class="workflow-mobile-card">
        <header class="workflow-mobile-card__header"><div><span class="workflow-mobile-card__eyebrow">流程定义</span><strong>{{ row.name }}</strong></div><UiStatusTag :value="row.status" :labels="{ DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }" :tone="workflowStatusTone(row.status)" /></header>
        <div class="workflow-mobile-card__facts"><div><span>流程编码</span><strong class="workflow-primary-key__value">{{ row.code }}</strong></div><div><span>范围</span><strong>{{ row.scope_type === 'PROJECT' ? '当前项目' : row.scope_type === 'TEMPLATE' ? '全局模板' : '平台流程' }}</strong></div><div><span>版本</span><strong>V{{ row.current_version || 0 }} · 模型 {{ row.model_schema_version || '-' }}</strong></div></div>
        <div class="workflow-mobile-card__actions"><el-button class="workflow-mobile-card__primary-action" type="primary" plain @click="openView(row)"><el-icon><EditPen v-if="row.status === 'DRAFT'" /><View v-else /></el-icon>{{ row.status === 'DRAFT' ? '编辑流程图' : '查看详情' }}</el-button><el-dropdown trigger="click" @command="handleMobileDefinitionCommand(row, $event)"><el-button text type="primary">更多操作<el-icon><MoreFilled /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-if="row.scope_type === 'TEMPLATE'" command="CREATE_FROM_TEMPLATE"><el-icon><Plus /></el-icon>创建项目流程</el-dropdown-item><el-dropdown-item v-if="row.scope_type !== 'TEMPLATE' && row.status === 'DRAFT'" command="PUBLISH"><el-icon><CircleCheck /></el-icon>发布</el-dropdown-item><el-dropdown-item v-if="row.scope_type !== 'TEMPLATE' && row.status === 'PUBLISHED'" command="UNPUBLISH"><el-icon><Refresh /></el-icon>取消发布</el-dropdown-item><el-dropdown-item v-if="row.scope_type !== 'TEMPLATE' && row.status === 'PUBLISHED'" command="ARCHIVE" divided><el-icon><Delete /></el-icon>归档</el-dropdown-item><el-dropdown-item v-if="row.scope_type !== 'TEMPLATE' && row.status === 'ARCHIVED'" command="RESTORE"><el-icon><Refresh /></el-icon>恢复发布</el-dropdown-item><el-dropdown-item v-if="row.status === 'DRAFT'" command="DELETE" divided><el-icon><Delete /></el-icon>删除草稿</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
      </article>
      <UiEmptyState v-if="!loading && !definitions.length" title="暂无流程定义" />
      <div class="workflow-mobile-list__footer"><span class="muted">共 {{ total }} 个流程定义</span><UiPagination :page="page" :page-size="pageSize" :total="total" @update:page="onPageChange" @update:page-size="onPageSizeChange" /></div>
    </section>

    <UiDataTable v-if="isMonitor" class="workflow-table workflow-table--desktop workflow-monitor-table" :data="instances" :loading="loading" row-key="id" border>
      <el-table-column label="业务单号" min-width="180" class-name="workflow-business-key" label-class-name="workflow-business-key"><template #default="scope"><span class="workflow-business-key__value">{{ scope.row.business_key }}</span></template></el-table-column><el-table-column prop="definition_name" label="流程名称" min-width="170" /><el-table-column prop="current_node" label="当前节点" min-width="150"><template #default="scope">{{ scope.row.current_node || '流程已结束' }}</template></el-table-column><el-table-column prop="version_no" label="版本" width="80" /><el-table-column prop="starter_name" label="发起人" width="120" /><el-table-column label="状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ RUNNING: '运行中', APPROVED: '已通过', REJECTED: '已拒绝', RETURNED: '已退回', TERMINATED: '已终止' }" :tone="workflowStatusTone(scope.row.status)" /></template></el-table-column><el-table-column prop="created_at" label="发起日期" width="150" class-name="workflow-monitor-date" label-class-name="workflow-monitor-date"><template #default="scope">{{ formatDateOnly(scope.row.created_at) }}</template></el-table-column><el-table-column label="操作" width="190" class-name="workflow-monitor-actions" label-class-name="workflow-monitor-actions"><template #default="scope"><div class="workflow-table-actions workflow-table-actions--monitor"><el-button link type="primary" @click="openMonitor(scope.row)"><el-icon><View /></el-icon>流程图</el-button><el-button link type="danger" @click="removeInstance(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></div></template></el-table-column>
    </UiDataTable>

    <div v-if="isMonitor && total > 0" class="workflow-pagination workflow-pagination--monitor">
      <UiPagination :page="page" :page-size="pageSize" :total="total" @update:page="onPageChange" @update:page-size="onPageSizeChange" />
    </div>
    <section v-if="isMonitor" class="workflow-mobile-list" v-loading="loading">
      <article v-for="row in instances" :key="row.id" class="workflow-mobile-card">
        <header class="workflow-mobile-card__header"><div><span class="workflow-mobile-card__eyebrow">业务单号</span><strong class="workflow-business-key__value">{{ row.business_key }}</strong></div><UiStatusTag :value="row.status" :labels="{ RUNNING: '运行中', APPROVED: '已通过', REJECTED: '已拒绝', RETURNED: '已退回', TERMINATED: '已终止' }" :tone="workflowStatusTone(row.status)" /></header>
        <div class="workflow-mobile-card__facts"><div><span>流程名称</span><strong>{{ row.definition_name || '-' }}</strong></div><div><span>当前节点</span><strong>{{ row.current_node || '流程已结束' }}</strong></div><div><span>版本 / 发起人</span><strong>V{{ row.version_no }} · {{ row.starter_name || '-' }}</strong></div><div><span>发起日期</span><strong>{{ formatDateOnly(row.created_at) }}</strong></div></div>
        <div class="workflow-mobile-card__actions"><el-button class="workflow-mobile-card__primary-action" type="primary" plain @click="openMonitor(row)"><el-icon><View /></el-icon>查看流程图</el-button><el-dropdown trigger="click" @command="handleMobileInstanceCommand(row, $event)"><el-button text type="primary">更多操作<el-icon><MoreFilled /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="DELETE" divided><el-icon><Delete /></el-icon>删除实例</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
      </article>
      <UiEmptyState v-if="!loading && !instances.length" title="暂无流程实例" />
      <div class="workflow-mobile-list__footer muted">共 {{ total }} 个流程实例</div>
    </section>

    <el-dialog v-model="monitorOpen" :title="detailDialogTitle" width="min(1400px, 96vw)" top="3vh" destroy-on-close class="workflow-monitor-dialog">
      <div v-loading="monitorLoading">
        <div class="workflow-monitor-summary">
          <div><span>流程名称</span><strong>{{ monitorInstance?.definition_name || '-' }}</strong></div><div><span>当前节点</span><strong>{{ monitorCurrentNodes.length ? monitorCurrentNodes.join('、') : '流程已结束或尚未到达审批节点' }}</strong></div><div><span>发起人</span><strong>{{ monitorInstance?.starter_name || '-' }}</strong></div><div><span>实例状态</span><UiStatusTag :value="monitorInstance?.status || ''" :labels="{ RUNNING: '运行中', APPROVED: '已通过', REJECTED: '已拒绝', RETURNED: '已退回', TERMINATED: '已终止' }" /></div>
        </div>
        <div class="workflow-monitor-legend"><span class="workflow-monitor-legend__item is-done">已完成</span><span class="workflow-monitor-legend__item is-active">进行中</span><span class="workflow-monitor-legend__item is-unreached">未到达</span><span class="workflow-monitor-legend__item is-rejected">拒绝/取消</span></div>
        <div class="workflow-monitor-canvas"><WorkflowDesigner v-model="monitorGraph" readonly mobile-layout :node-statuses="monitorNodeStatuses" /></div>
        <section class="workflow-approval-records"><div class="workflow-detail-audit__heading">审批记录</div><el-timeline v-if="approvalRecords.length"><el-timeline-item v-for="state in approvalRecords" :key="state.id" :timestamp="formatDateOnly(state.completed_at || state.created_at)" placement="top"><div class="workflow-approval-record"><strong>{{ state.node_name || '未命名节点' }}</strong><UiStatusTag :value="state.task_type" :labels="{ APPROVAL: '审批', ADD_SIGN: '加签', CC: '抄送' }" /><UiStatusTag :value="state.status" :labels="{ PENDING: '进行中', APPROVED: '已同意', REJECTED: '已拒绝', RETURNED: '已退回', CANCELLED: '已取消', SENT: '已抄送' }" :tone="workflowStatusTone(state.status)" /><span class="muted">{{ state.assignee_name || '系统' }}</span></div><p v-if="state.comment">{{ state.comment }}</p></el-timeline-item></el-timeline><UiEmptyState v-else title="暂无审批记录" /></section>
      </div>
    </el-dialog>
    <el-dialog v-model="designerOpen" :title="designerTitle" width="min(1400px, 96vw)" top="12px" destroy-on-close class="workflow-designer-dialog">
      <el-tabs v-model="designerTab" class="workflow-definition-tabs" :class="{ 'is-create': !viewingDefinitionId }">
        <el-tab-pane label="当前设计" name="current">
          <div class="workflow-definition-meta"><el-form inline><el-form-item label="流程范围" required><el-segmented :model-value="scopeType" :options="[{ label: '当前项目', value: 'PROJECT', disabled: !projectContext.currentRef }, { label: '全局模板', value: 'TEMPLATE' }, { label: '平台流程', value: 'PLATFORM' }]" :disabled="designerReadonly || Boolean(viewingDefinitionId)" @change="changeScope" /></el-form-item><el-form-item v-if="scopeType === 'PROJECT'" label="所属项目"><el-tag effect="plain">{{ projectContext.current?.name || '-' }}</el-tag></el-form-item><el-form-item label="流程编码" required><el-input v-model="form.code" :disabled="designerReadonly" placeholder="例如 expense_approval" /></el-form-item><el-form-item label="流程名称" required><el-input v-model="form.name" :disabled="designerReadonly" placeholder="例如费用审批" /></el-form-item></el-form></div>
          <div v-loading="optionsLoading" class="workflow-builder" :class="{ 'is-fullscreen': designerFullscreen }"><WorkflowDesigner v-model="graph" :readonly="designerReadonly" @select="selectedNodeId = $event?.id || null; if ($event) selectedEdgeId = null" @edge-select="selectedEdgeId = $event?.id || null; if ($event) selectedNodeId = null" @fullscreen="designerFullscreen = $event" /><WorkflowNodeInspector :node="selectedNode" :edge="selectedEdge" :nodes="graph.nodes" :users="users" :roles="roles" :scope-type="scopeType" :readonly="designerReadonly" @update="updateSelectedNode" @update-edge="updateSelectedEdge" /></div>
        </el-tab-pane>
        <el-tab-pane v-if="viewingDefinitionId" label="历史版本" name="versions">
          <div v-loading="historyLoading" class="workflow-history-pane">
            <el-table :data="definitionVersions" border row-key="version_no">
              <el-table-column label="版本" width="100"><template #default="scope">V{{ scope.row.version_no }}</template></el-table-column>
              <el-table-column label="版本状态" width="130"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ DRAFT: '草稿', PUBLISHED: '已发布' }" :tone="workflowStatusTone(scope.row.status)" /></template></el-table-column>
              <el-table-column prop="model_schema_version" label="模型版本" width="120" />
              <el-table-column label="创建时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.created_at) }}</template></el-table-column>
              <el-table-column label="操作" width="130"><template #default="scope"><el-button link type="primary" @click="openHistoryVersion(scope.row.version_no)"><el-icon><View /></el-icon>查看流程图</el-button></template></el-table-column>
            </el-table>
            <UiEmptyState v-if="!historyLoading && !definitionVersions.length" title="暂无历史版本" />
            <section v-if="selectedHistoryVersion" class="workflow-history-graph">
              <header><strong>历史版本 V{{ selectedHistoryVersion }}</strong><span class="muted">只读查看</span></header>
              <WorkflowDesigner v-model="historyGraph" readonly />
            </section>
          </div>
        </el-tab-pane>
        <el-tab-pane v-if="viewingDefinitionId" label="更新记录" name="events">
          <div v-loading="historyLoading" class="workflow-history-pane">
            <el-table :data="definitionEvents" border row-key="id">
              <el-table-column label="操作" width="130"><template #default="scope">{{ definitionEventLabel(scope.row.event_type) }}</template></el-table-column>
              <el-table-column label="版本" width="90"><template #default="scope">{{ scope.row.version_no ? `V${scope.row.version_no}` : '-' }}</template></el-table-column>
              <el-table-column label="操作人" min-width="140"><template #default="scope">{{ scope.row.operator_name || '系统' }}</template></el-table-column>
              <el-table-column label="原因" min-width="220"><template #default="scope">{{ scope.row.reason || '-' }}</template></el-table-column>
              <el-table-column label="操作时间" min-width="180"><template #default="scope">{{ formatDateTime(scope.row.created_at) }}</template></el-table-column>
            </el-table>
            <UiEmptyState v-if="!historyLoading && !definitionEvents.length" title="暂无更新记录" />
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer><el-button @click="designerOpen = false">关闭</el-button><el-button v-if="designerTab === 'current' && !designerReadonly" type="primary" :loading="saving" @click="save">保存流程</el-button></template>
    </el-dialog>
  </section>
</template>
