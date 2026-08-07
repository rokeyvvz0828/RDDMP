<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, CircleCheck, Delete, Document, EditPen, Plus, Promotion, Refresh, UserFilled, View } from '@element-plus/icons-vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import WorkflowDesigner from '../components/workflow/WorkflowDesigner.vue'
import WorkflowNodeInspector from '../components/workflow/WorkflowNodeInspector.vue'
import { createWorkflowDefinition, decideWorkflowTask, defaultWorkflowGraph, deleteWorkflowDefinition, deleteWorkflowInstance, getWorkflowDefinition, getWorkflowInstanceDetail, getWorkflowTimeline, listWorkflowDefinitions, listWorkflowDone, listWorkflowInbox, listWorkflowInstances, publishWorkflowDefinition, unpublishWorkflowDefinition, startWorkflow, updateWorkflowDefinition, type WorkflowAuditEvent, type WorkflowDoneItem, type WorkflowEdgeModel, type WorkflowGraph, type WorkflowInstance, type WorkflowNodeModel, type WorkflowNodeState, type WorkflowTask, type WorkflowTaskAction } from '../api/workflow'
import { getRoleOptions, listSystem } from '../api/system'
import type { RoleOption, SystemRow } from '../types/system'
import { formatDateOnly } from '../utils/date'
import { apiErrorMessage } from '../api/error'

const route = useRoute()
const section = computed(() => String(route.params.section || 'definitions'))
const isInbox = computed(() => section.value === 'inbox')
const isMonitor = computed(() => section.value === 'monitor')
const isDone = computed(() => section.value === 'done')
const definitions = ref<Awaited<ReturnType<typeof listWorkflowDefinitions>>['data']['data']>([])
const inbox = ref<WorkflowTask[]>([])
const instances = ref<WorkflowInstance[]>([])
const done = ref<WorkflowDoneItem[]>([])
const timeline = ref<WorkflowAuditEvent[]>([])
const monitorOpen = ref(false)
const monitorLoading = ref(false)
const monitorInstance = ref<WorkflowInstance | null>(null)
const monitorGraph = ref<WorkflowGraph>(defaultWorkflowGraph())
const monitorNodeStates = ref<WorkflowNodeState[]>([])
const monitorNodeStatuses = ref<Record<string, string>>({})
const timelineOpen = ref(false)
const users = ref<{ id: number; username: string; display_name: string; org_name?: string }[]>([])
const roles = ref<RoleOption[]>([])
const loading = ref(false)
const optionsLoading = ref(false)
const designerOpen = ref(false)
const designerReadonly = ref(false)
const designerFullscreen = ref(false)
const editingDefinitionId = ref<number | null>(null)
const saving = ref(false)
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const graph = ref<WorkflowGraph>(defaultWorkflowGraph())
const form = reactive({ code: '', name: '' })
const actionOpen = ref(false)
const actionSaving = ref(false)
const actionTask = ref<WorkflowTask | null>(null)
const actionType = ref<WorkflowTaskAction>('APPROVE')
const actionComment = ref('')
const targetUserId = ref<number | undefined>()
const ccUserIds = ref<number[]>([])
const selectedNode = computed(() => graph.value.nodes.find(node => node.id === selectedNodeId.value) || null)
const selectedEdge = computed(() => graph.value.edges.find(edge => edge.id === selectedEdgeId.value) || null)
const monitorCurrentNodes = computed(() => monitorGraph.value.nodes.filter(node => monitorNodeStatuses.value[node.id] === 'ACTIVE').map(node => node.label))
const pageTitle = computed(() => isInbox.value ? '审批待办' : isDone.value ? '流程已办' : isMonitor.value ? '流程监控' : '流程定义')
const pageDescription = computed(() => isInbox.value ? '处理当前账号待办审批和抄送记录。' : isDone.value ? '查看当前账号已经处理过的审批动作和意见。' : isMonitor.value ? '查看流程实例当前节点、流程图状态和完整审计时间线。' : '使用企业级流程图配置审批节点、条件分支、并行审批和抄送。')
const designerTitle = computed(() => editingDefinitionId.value ? '编辑流程图：' + form.name : designerReadonly.value ? '查看流程图：' + form.name : '新建流程定义')
const actionTitle = computed(() => ({ APPROVE: '同意审批', REJECT: '拒绝审批', RETURN: '退回流程', ADD_SIGN: '发起加签', CC: '发起抄送', TRANSFER: '转交任务', DELEGATE: '委托任务' }[actionType.value]))

async function load() {
  loading.value = true
  try {
    if (isInbox.value) inbox.value = (await listWorkflowInbox()).data.data
    else if (isDone.value) done.value = (await listWorkflowDone()).data.data
    else if (isMonitor.value) instances.value = (await listWorkflowInstances()).data.data
    else definitions.value = (await listWorkflowDefinitions()).data.data
  } catch (error) { ElMessage.error(apiErrorMessage(error, '工作流数据加载失败')) } finally { loading.value = false }
}
async function loadOptions() {
  if (users.value.length || optionsLoading.value) return
  optionsLoading.value = true
  try {
    const [userResponse, roleResponse] = await Promise.all([listSystem('users', { page: 1, size: 500 }), getRoleOptions()])
    users.value = userResponse.data.data.records.map((row: SystemRow) => ({ id: Number(row.id), username: String(row.username || ''), display_name: String(row.display_name || row.username || ''), org_name: row.org_name ? String(row.org_name) : undefined }))
    roles.value = roleResponse.data.data
  } catch (error) { ElMessage.error(apiErrorMessage(error, '审批人选项加载失败')) } finally { optionsLoading.value = false }
}
function openCreate() { editingDefinitionId.value = null; form.code = ''; form.name = ''; graph.value = defaultWorkflowGraph(); selectedNodeId.value = null; selectedEdgeId.value = null; designerReadonly.value = false; designerFullscreen.value = false; designerOpen.value = true; void loadOptions() }
async function openView(row: { id: number; name: string; status: string }) {
  try {
    const detail = (await getWorkflowDefinition(row.id)).data.data
    const rawDefinition = detail.definition_json
    const parsed = (typeof rawDefinition === 'string' ? JSON.parse(rawDefinition) : rawDefinition) as WorkflowGraph
    if (!parsed || !Array.isArray(parsed.nodes) || !Array.isArray(parsed.edges)) throw new Error('流程图数据格式无效')
    graph.value = { schemaVersion: 2, nodes: parsed.nodes || [], edges: parsed.edges || [], variables: parsed.variables || [], formBindings: parsed.formBindings || [] }
    form.code = detail.code
    form.name = detail.name
    selectedNodeId.value = null
    selectedEdgeId.value = null
    editingDefinitionId.value = row.status === 'DRAFT' ? row.id : null
    designerReadonly.value = row.status !== 'DRAFT'
    designerFullscreen.value = false
    designerOpen.value = true
    if (!designerReadonly.value) void loadOptions()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '流程图加载失败')) }
}
function updateSelectedNode(node: WorkflowNodeModel) { graph.value = { ...graph.value, nodes: graph.value.nodes.map(item => item.id === node.id ? node : item) } }
function updateSelectedEdge(edge: WorkflowEdgeModel) {
  const source = graph.value.nodes.find(node => node.id === edge.source)
  const isConditional = source?.type === 'CONDITION'
  const next = { ...edge, condition: isConditional && !edge.default && edge.condition?.trim() ? edge.condition.trim() : null, default: isConditional && Boolean(edge.default) }
  const edges = graph.value.edges.map(item => {
    if (item.id === next.id) return next
    return next.default && item.source === next.source ? { ...item, default: false, condition: null } : item
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
      if (['USER', 'ROLE'].includes(assigneeType) && !hasPositiveIds(config.assigneeIds)) return true
      if (assigneeType === 'FORM_FIELD' && !String(config.fieldName || '').trim()) return true
      if (assigneeType === 'EXPRESSION' && !String(config.expression || '').trim()) return true
      if (!['USER', 'ROLE', 'ORG_OWNER', 'STARTER', 'FORM_FIELD', 'EXPRESSION'].includes(assigneeType)) return true
    }
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
    const payload = { code: form.code.trim(), name: form.name.trim(), definitionJson: JSON.stringify(graphForSave()) }
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
async function removeDefinition(row: { id: number; name: string; status: string }) {
  const isPublished = row.status === 'PUBLISHED'
  try { await ElMessageBox.confirm(isPublished ? `确认删除已发布流程“${row.name}”吗？删除后将停止新发起，历史审批实例仍会保留。` : `确认删除草稿流程“${row.name}”吗？删除后不可恢复。`, '删除流程', { type: 'warning' }); await deleteWorkflowDefinition(row.id); ElMessage.success(isPublished ? '已发布流程已删除' : '草稿流程已删除'); await load() }
  catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '流程删除失败')) }
}
async function start(row: { id: number }) {
  try { const prompt = await ElMessageBox.prompt('请输入业务单号', '发起审批', { inputPlaceholder: '例如 EXP-2026-0001' }); if (!prompt.value.trim()) return; await startWorkflow(row.id, prompt.value.trim()); ElMessage.success('审批实例已发起') }
  catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '审批发起失败')) }
}
async function openTimeline(row: WorkflowInstance) { try { timeline.value = (await getWorkflowTimeline(row.id)).data.data; timelineOpen.value = true } catch (error) { ElMessage.error(apiErrorMessage(error, '流程时间线加载失败')) } }
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
async function openMonitor(row: WorkflowInstance) {
  monitorLoading.value = true
  try {
    const detail = (await getWorkflowInstanceDetail(row.id)).data.data
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
function doneActionLabel(action: string) { return ({ APPROVE: '同意', REJECT: '拒绝', RETURN: '退回', ADD_SIGN: '加签', CC: '抄送', TRANSFER: '转交', DELEGATE: '委托' }[action] || action) }
function openAction(row: WorkflowTask, action: WorkflowTaskAction) { actionTask.value = row; actionType.value = action; actionComment.value = ''; targetUserId.value = undefined; ccUserIds.value = []; actionOpen.value = true; void loadOptions() }
async function submitAction() {
  if (!actionTask.value) return
  if (['ADD_SIGN', 'TRANSFER', 'DELEGATE'].includes(actionType.value) && !targetUserId.value) { ElMessage.warning('请选择目标用户'); return }
  if (actionType.value === 'CC' && !ccUserIds.value.length) { ElMessage.warning('请选择抄送人员'); return }
  actionSaving.value = true
  try { await decideWorkflowTask(actionTask.value.id, actionType.value, actionComment.value.trim(), { targetUserId: targetUserId.value, ccUserIds: ccUserIds.value }); actionOpen.value = false; ElMessage.success(`${actionTitle.value}已提交`); await load() }
  catch (error) { ElMessage.error(apiErrorMessage(error, `${actionTitle.value}失败`)) } finally { actionSaving.value = false }
}
watch(section, load)
onMounted(load)
</script>

<template>
  <section class="workflow-page">
    <UiPageHeader eyebrow="工作流" :title="pageTitle" :description="pageDescription"><template #actions><el-button v-if="!isInbox && !isMonitor && !isDone" type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建流程</el-button></template></UiPageHeader>
    <UiToolbar><div class="ui-toolbar__filters"><span class="muted">{{ isMonitor ? '实例状态和审计记录按租户隔离。' : isInbox ? '审批动作会记录操作人、意见和时间。' : isDone ? '显示当前账号已处理过的审批动作。' : '发布前校验流程拓扑、审批人和分支配置。' }}</span></div><template #actions><el-button @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template></UiToolbar>

    <UiDataTable v-if="!isInbox && !isMonitor && !isDone" class="workflow-table" :data="definitions" :loading="loading" row-key="id" border>
      <el-table-column prop="code" label="流程编码" min-width="160" class-name="workflow-primary-key" label-class-name="workflow-primary-key"><template #default="scope"><span class="workflow-primary-key__value">{{ scope.row.code }}</span></template></el-table-column><el-table-column prop="name" label="流程名称" min-width="180" /><el-table-column prop="current_version" label="当前版本" width="110" /><el-table-column prop="model_schema_version" label="模型版本" width="110" /><el-table-column label="状态" width="120"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ DRAFT: '草稿', PUBLISHED: '已发布' }" /></template></el-table-column><el-table-column label="操作" width="470"><template #default="scope"><div class="workflow-table-actions workflow-table-actions--definitions"><el-button link type="primary" @click="openView(scope.row)"><el-icon><EditPen v-if="scope.row.status === 'DRAFT'" /><View v-else /></el-icon>{{ scope.row.status === 'DRAFT' ? '编辑流程图' : '查看流程图' }}</el-button><el-button link type="primary" :disabled="scope.row.status === 'PUBLISHED'" @click="publish(scope.row)"><el-icon><CircleCheck /></el-icon>发布</el-button><el-button link type="warning" :disabled="scope.row.status !== 'PUBLISHED'" @click="unpublish(scope.row)"><el-icon><Refresh /></el-icon>取消发布</el-button><el-button link type="success" :disabled="scope.row.status !== 'PUBLISHED'" @click="start(scope.row)"><el-icon><Promotion /></el-icon>发起审批</el-button><el-button link type="danger" @click="removeDefinition(scope.row)"><el-icon><Delete /></el-icon>删除流程</el-button></div></template></el-table-column>
      <template #footer><span class="muted">共 {{ definitions.length }} 个流程定义</span></template>
    </UiDataTable>

    <UiDataTable v-else-if="isInbox" class="workflow-table" :data="inbox" :loading="loading" row-key="id" border>
      <el-table-column prop="business_key" label="业务单号" min-width="180" class-name="workflow-business-key" label-class-name="workflow-business-key"><template #default="scope"><span class="workflow-business-key__value">{{ scope.row.business_key }}</span></template></el-table-column><el-table-column prop="task_type" label="任务类型" width="110"><template #default="scope"><UiStatusTag :value="scope.row.task_type" :labels="{ APPROVAL: '审批', ADD_SIGN: '加签', CC: '抄送' }" /></template></el-table-column><el-table-column label="节点" min-width="160"><template #default="scope">{{ scope.row.node_name || '未命名节点' }}</template></el-table-column><el-table-column prop="assignee_name" label="处理人" width="130" /><el-table-column prop="created_at" label="进入时间" min-width="150" class-name="workflow-date" label-class-name="workflow-date"><template #default="scope">{{ formatDateOnly(scope.row.created_at) }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ PENDING: '待处理', SENT: '已抄送', APPROVED: '已同意', REJECTED: '已拒绝', CANCELLED: '已取消' }" /></template></el-table-column><el-table-column label="操作" width="300"><template #default="scope"><div class="workflow-table-actions workflow-table-actions--inbox"><template v-if="scope.row.task_type !== 'CC'"><el-button link type="success" @click="openAction(scope.row, 'APPROVE')"><el-icon><Check /></el-icon>同意</el-button><el-button link type="danger" @click="openAction(scope.row, 'REJECT')">拒绝</el-button><el-button link type="warning" @click="openAction(scope.row, 'RETURN')">退回</el-button><el-button link type="warning" @click="openAction(scope.row, 'ADD_SIGN')"><el-icon><UserFilled /></el-icon>加签</el-button><el-button link type="primary" @click="openAction(scope.row, 'CC')">抄送</el-button></template><span v-else class="muted">抄送记录</span></div></template></el-table-column>
      <template #footer><span class="muted">共 {{ inbox.length }} 条待办或抄送记录</span></template>
    </UiDataTable>

    <UiDataTable v-else-if="isDone" class="workflow-table" :data="done" :loading="loading" row-key="id" border>
      <el-table-column prop="business_key" label="业务单号" min-width="180" class-name="workflow-business-key" label-class-name="workflow-business-key"><template #default="scope"><span class="workflow-business-key__value">{{ scope.row.business_key }}</span></template></el-table-column><el-table-column prop="definition_name" label="流程名称" min-width="170" /><el-table-column label="审批节点" min-width="150"><template #default="scope">{{ scope.row.node_name || '未命名节点' }}</template></el-table-column><el-table-column prop="action_code" label="处理动作" width="100"><template #default="scope">{{ doneActionLabel(scope.row.action_code) }}</template></el-table-column><el-table-column prop="comment" label="审批意见" min-width="220" show-overflow-tooltip /><el-table-column prop="instance_status" label="实例状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.instance_status" :labels="{ RUNNING: '进行中', APPROVED: '已通过', REJECTED: '已拒绝', RETURNED: '已退回', TERMINATED: '已终止' }" /></template></el-table-column><el-table-column prop="created_at" label="处理时间" width="150"><template #default="scope">{{ formatDateOnly(scope.row.created_at) }}</template></el-table-column>
      <template #footer><span class="muted">共 {{ done.length }} 条已办记录</span></template>
    </UiDataTable>

    <UiDataTable v-else-if="isMonitor" class="workflow-table workflow-monitor-table" :data="instances" :loading="loading" row-key="id" border>
      <el-table-column label="业务单号" min-width="180" class-name="workflow-business-key" label-class-name="workflow-business-key"><template #default="scope"><span class="workflow-business-key__value">{{ scope.row.business_key }}</span></template></el-table-column><el-table-column prop="definition_name" label="流程名称" min-width="170" /><el-table-column prop="current_node" label="当前节点" min-width="150"><template #default="scope">{{ scope.row.current_node || '流程已结束' }}</template></el-table-column><el-table-column prop="version_no" label="版本" width="80" /><el-table-column prop="starter_name" label="发起人" width="120" /><el-table-column label="状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ RUNNING: '运行中', APPROVED: '已通过', REJECTED: '已拒绝', RETURNED: '已退回', TERMINATED: '已终止' }" /></template></el-table-column><el-table-column prop="created_at" label="发起日期" width="150" class-name="workflow-monitor-date" label-class-name="workflow-monitor-date"><template #default="scope">{{ formatDateOnly(scope.row.created_at) }}</template></el-table-column><el-table-column label="操作" width="260" class-name="workflow-monitor-actions" label-class-name="workflow-monitor-actions"><template #default="scope"><div class="workflow-table-actions workflow-table-actions--monitor"><el-button link type="primary" @click="openMonitor(scope.row)"><el-icon><View /></el-icon>流程图</el-button><el-button link type="primary" @click="openTimeline(scope.row)"><el-icon><Document /></el-icon>时间线</el-button><el-button link type="danger" @click="removeInstance(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></div></template></el-table-column>
    </UiDataTable>

    <el-dialog v-model="monitorOpen" :title="'流程监控：' + (monitorInstance?.business_key || '')" width="min(1400px, 96vw)" top="3vh" destroy-on-close class="workflow-monitor-dialog">
      <div v-loading="monitorLoading">
        <div class="workflow-monitor-summary">
          <div><span>流程名称</span><strong>{{ monitorInstance?.definition_name || '-' }}</strong></div><div><span>当前节点</span><strong>{{ monitorCurrentNodes.length ? monitorCurrentNodes.join('、') : '流程已结束或尚未到达审批节点' }}</strong></div><div><span>发起人</span><strong>{{ monitorInstance?.starter_name || '-' }}</strong></div><div><span>实例状态</span><UiStatusTag :value="monitorInstance?.status || ''" :labels="{ RUNNING: '运行中', APPROVED: '已通过', REJECTED: '已拒绝', RETURNED: '已退回', TERMINATED: '已终止' }" /></div>
        </div>
        <div class="workflow-monitor-legend"><span class="workflow-monitor-legend__item is-done">已完成</span><span class="workflow-monitor-legend__item is-active">进行中</span><span class="workflow-monitor-legend__item is-unreached">未到达</span><span class="workflow-monitor-legend__item is-rejected">拒绝/取消</span></div>
        <div class="workflow-monitor-canvas"><WorkflowDesigner v-model="monitorGraph" readonly :node-statuses="monitorNodeStatuses" /></div>
        <el-table :data="monitorNodeStates" size="small" border max-height="250"><el-table-column label="节点" min-width="160"><template #default="scope">{{ scope.row.node_name || '未命名节点' }}</template></el-table-column><el-table-column prop="assignee_name" label="审批人" width="130" /><el-table-column prop="status" label="节点状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ PENDING: '进行中', APPROVED: '已完成', REJECTED: '已拒绝', RETURNED: '已退回', CANCELLED: '已取消', SENT: '已抄送' }" /></template></el-table-column><el-table-column prop="comment" label="审批意见" min-width="180" show-overflow-tooltip /><el-table-column prop="created_at" label="进入时间" width="145"><template #default="scope">{{ formatDateOnly(scope.row.created_at) }}</template></el-table-column><el-table-column prop="completed_at" label="完成时间" width="145"><template #default="scope">{{ formatDateOnly(scope.row.completed_at) }}</template></el-table-column></el-table>
      </div>
    </el-dialog>
    <el-dialog v-model="designerOpen" :title="designerTitle" width="min(1400px, 96vw)" top="3vh" destroy-on-close class="workflow-designer-dialog"><div class="workflow-definition-meta"><el-form inline><el-form-item label="流程编码" required><el-input v-model="form.code" :disabled="designerReadonly" placeholder="例如 expense_approval" /></el-form-item><el-form-item label="流程名称" required><el-input v-model="form.name" :disabled="designerReadonly" placeholder="例如费用审批" /></el-form-item></el-form></div><div v-loading="optionsLoading" class="workflow-builder" :class="{ 'is-fullscreen': designerFullscreen }"><WorkflowDesigner v-model="graph" :readonly="designerReadonly" @select="selectedNodeId = $event?.id || null; if ($event) selectedEdgeId = null" @edge-select="selectedEdgeId = $event?.id || null; if ($event) selectedNodeId = null" @fullscreen="designerFullscreen = $event" /><WorkflowNodeInspector :node="selectedNode" :edge="selectedEdge" :nodes="graph.nodes" :users="users" :roles="roles" :readonly="designerReadonly" @update="updateSelectedNode" @update-edge="updateSelectedEdge" /></div><template #footer><el-button @click="designerOpen = false">关闭</el-button><el-button v-if="!designerReadonly" type="primary" :loading="saving" @click="save">保存流程</el-button></template></el-dialog>
    <el-dialog v-model="actionOpen" :title="actionTitle" width="520px" destroy-on-close><div v-if="actionTask" class="workflow-action-context"><span>业务单号</span><strong>{{ actionTask.business_key }}</strong><span>当前节点</span><strong>{{ actionTask.node_name || '未命名节点' }}</strong></div><el-form label-position="top"><el-form-item v-if="['ADD_SIGN', 'TRANSFER', 'DELEGATE'].includes(actionType)" label="目标用户" required><el-select v-model="targetUserId" filterable placeholder="请选择目标用户"><el-option v-for="user in users" :key="user.id" :label="`${user.display_name}（${user.username}）`" :value="user.id" /></el-select></el-form-item><el-form-item v-if="actionType === 'CC'" label="抄送人员" required><el-select v-model="ccUserIds" multiple filterable collapse-tags placeholder="请选择抄送人员"><el-option v-for="user in users" :key="user.id" :label="`${user.display_name}（${user.username}）`" :value="user.id" /></el-select></el-form-item><el-form-item label="审批意见"><el-input v-model="actionComment" type="textarea" :rows="4" placeholder="请输入审批意见（可选）" /></el-form-item></el-form><template #footer><el-button @click="actionOpen = false">取消</el-button><el-button type="primary" :loading="actionSaving" @click="submitAction">提交{{ actionTitle }}</el-button></template></el-dialog>
    <el-dialog v-model="timelineOpen" title="流程审计时间线" width="760px"><el-timeline><el-timeline-item v-for="event in timeline" :key="event.id" :timestamp="formatDateOnly(event.created_at)" placement="top"><strong>{{ event.event_type }}</strong><span class="muted"> · {{ event.operator_name || '系统' }}</span><p v-if="event.reason">{{ event.reason }}</p><pre v-if="event.payload_json">{{ event.payload_json }}</pre></el-timeline-item></el-timeline><UiEmptyState v-if="!timeline.length" title="暂无审计记录" /></el-dialog>
  </section>
</template>
