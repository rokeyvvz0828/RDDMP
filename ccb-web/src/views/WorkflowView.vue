<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, CircleCheck, Document, Plus, Promotion, Refresh, UserFilled } from '@element-plus/icons-vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import WorkflowDesigner from '../components/workflow/WorkflowDesigner.vue'
import WorkflowNodeInspector from '../components/workflow/WorkflowNodeInspector.vue'
import { createWorkflowDefinition, decideWorkflowTask, defaultWorkflowGraph, getWorkflowTimeline, listWorkflowDefinitions, listWorkflowInbox, listWorkflowInstances, publishWorkflowDefinition, startWorkflow, type WorkflowAuditEvent, type WorkflowGraph, type WorkflowInstance, type WorkflowNodeModel, type WorkflowTask, type WorkflowTaskAction } from '../api/workflow'
import { getRoleOptions, listSystem } from '../api/system'
import type { RoleOption, SystemRow } from '../types/system'
import { formatDateOnly } from '../utils/date'
import { apiErrorMessage } from '../api/error'

const route = useRoute()
const section = computed(() => String(route.params.section || 'definitions'))
const isInbox = computed(() => section.value === 'inbox')
const isMonitor = computed(() => section.value === 'monitor')
const definitions = ref<Awaited<ReturnType<typeof listWorkflowDefinitions>>['data']['data']>([])
const inbox = ref<WorkflowTask[]>([])
const instances = ref<WorkflowInstance[]>([])
const timeline = ref<WorkflowAuditEvent[]>([])
const timelineOpen = ref(false)
const users = ref<{ id: number; username: string; display_name: string; org_name?: string }[]>([])
const roles = ref<RoleOption[]>([])
const loading = ref(false)
const optionsLoading = ref(false)
const designerOpen = ref(false)
const saving = ref(false)
const selectedNodeId = ref<string | null>(null)
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
const pageTitle = computed(() => isInbox.value ? '审批待办' : isMonitor.value ? '流程监控' : '流程定义')
const pageDescription = computed(() => isInbox.value ? '处理当前账号待办审批和抄送记录。' : isMonitor.value ? '查看流程实例状态、版本和完整审计时间线。' : '使用企业级流程图配置审批节点、条件分支、并行审批和抄送。')
const actionTitle = computed(() => ({ APPROVE: '同意审批', REJECT: '拒绝审批', RETURN: '退回流程', ADD_SIGN: '发起加签', CC: '发起抄送', TRANSFER: '转交任务', DELEGATE: '委托任务' }[actionType.value]))

async function load() {
  loading.value = true
  try {
    if (isInbox.value) inbox.value = (await listWorkflowInbox()).data.data
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
function openCreate() { form.code = ''; form.name = ''; graph.value = defaultWorkflowGraph(); selectedNodeId.value = null; designerOpen.value = true; void loadOptions() }
function updateSelectedNode(node: WorkflowNodeModel) { graph.value = { ...graph.value, nodes: graph.value.nodes.map(item => item.id === node.id ? node : item) } }
async function save() {
  if (!form.code.trim() || !form.name.trim()) { ElMessage.warning('请填写流程编码和流程名称'); return }
  saving.value = true
  try { await createWorkflowDefinition({ code: form.code.trim(), name: form.name.trim(), definitionJson: JSON.stringify(graph.value) }); designerOpen.value = false; ElMessage.success('流程定义已保存，请在列表中发布'); await load() }
  catch (error) { ElMessage.error(apiErrorMessage(error, '流程定义保存失败')) } finally { saving.value = false }
}
async function publish(row: { id: number; name: string }) {
  try { await ElMessageBox.confirm(`确认发布流程“${row.name}”吗？发布后运行实例使用固定版本。`, '发布流程', { type: 'warning' }); await publishWorkflowDefinition(row.id); ElMessage.success('流程已发布'); await load() }
  catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '流程发布失败')) }
}
async function start(row: { id: number }) {
  try { const prompt = await ElMessageBox.prompt('请输入业务单号', '发起审批', { inputPlaceholder: '例如 EXP-2026-0001' }); if (!prompt.value.trim()) return; await startWorkflow(row.id, prompt.value.trim()); ElMessage.success('审批实例已发起') }
  catch { /* 用户取消 */ }
}
async function openTimeline(row: WorkflowInstance) { try { timeline.value = (await getWorkflowTimeline(row.id)).data.data; timelineOpen.value = true } catch (error) { ElMessage.error(apiErrorMessage(error, '流程时间线加载失败')) } }
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
    <UiPageHeader eyebrow="工作流" :title="pageTitle" :description="pageDescription"><template #actions><el-button v-if="!isInbox && !isMonitor" type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建流程</el-button></template></UiPageHeader>
    <UiToolbar><div class="ui-toolbar__filters"><span class="muted">{{ isMonitor ? '实例状态和审计记录按租户隔离。' : isInbox ? '审批动作会记录操作人、意见和时间。' : '发布前校验流程拓扑、审批人和分支配置。' }}</span></div><template #actions><el-button @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template></UiToolbar>

    <UiDataTable v-if="!isInbox && !isMonitor" :data="definitions" :loading="loading" row-key="id" border>
      <el-table-column prop="code" label="流程编码" min-width="160" /><el-table-column prop="name" label="流程名称" min-width="180" /><el-table-column prop="current_version" label="当前版本" width="110" /><el-table-column prop="model_schema_version" label="模型版本" width="110" /><el-table-column label="状态" width="120"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ DRAFT: '草稿', PUBLISHED: '已发布' }" /></template></el-table-column><el-table-column label="操作" width="290" fixed="right"><template #default="scope"><el-button link type="primary" :disabled="scope.row.status === 'PUBLISHED'" @click="publish(scope.row)"><el-icon><CircleCheck /></el-icon>发布</el-button><el-button link type="success" :disabled="scope.row.status !== 'PUBLISHED'" @click="start(scope.row)"><el-icon><Promotion /></el-icon>发起审批</el-button></template></el-table-column>
      <template #footer><span class="muted">共 {{ definitions.length }} 个流程定义</span></template>
    </UiDataTable>

    <UiDataTable v-else-if="isInbox" :data="inbox" :loading="loading" row-key="id" border>
      <el-table-column prop="business_key" label="业务单号" min-width="180" /><el-table-column prop="task_type" label="任务类型" width="110"><template #default="scope"><UiStatusTag :value="scope.row.task_type" :labels="{ APPROVAL: '审批', ADD_SIGN: '加签', CC: '抄送' }" /></template></el-table-column><el-table-column prop="task_key" label="节点" min-width="160" /><el-table-column prop="assignee_name" label="处理人" width="130" /><el-table-column prop="created_at" label="进入时间" min-width="150"><template #default="scope">{{ formatDateOnly(scope.row.created_at) }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ PENDING: '待处理', SENT: '已抄送', APPROVED: '已同意', REJECTED: '已拒绝', CANCELLED: '已取消' }" /></template></el-table-column><el-table-column label="操作" width="420" fixed="right"><template #default="scope"><template v-if="scope.row.task_type !== 'CC'"><el-button link type="success" @click="openAction(scope.row, 'APPROVE')"><el-icon><Check /></el-icon>同意</el-button><el-button link type="danger" @click="openAction(scope.row, 'REJECT')">拒绝</el-button><el-button link type="warning" @click="openAction(scope.row, 'RETURN')">退回</el-button><el-button link type="warning" @click="openAction(scope.row, 'ADD_SIGN')"><el-icon><UserFilled /></el-icon>加签</el-button><el-button link type="primary" @click="openAction(scope.row, 'CC')">抄送</el-button><el-dropdown @command="(command: WorkflowTaskAction) => openAction(scope.row, command)"><el-button link type="info">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="TRANSFER">转交</el-dropdown-item><el-dropdown-item command="DELEGATE">委托</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template><span v-else class="muted">抄送记录</span></template></el-table-column>
      <template #footer><span class="muted">共 {{ inbox.length }} 条待办或抄送记录</span></template>
    </UiDataTable>

    <UiDataTable v-else :data="instances" :loading="loading" row-key="id" border>
      <el-table-column prop="business_key" label="业务单号" min-width="180" /><el-table-column prop="definition_name" label="流程名称" min-width="170" /><el-table-column prop="version_no" label="版本" width="80" /><el-table-column prop="starter_name" label="发起人" width="120" /><el-table-column label="状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ RUNNING: '运行中', APPROVED: '已通过', REJECTED: '已拒绝', RETURNED: '已退回' }" /></template></el-table-column><el-table-column prop="created_at" label="发起日期" width="120"><template #default="scope">{{ formatDateOnly(scope.row.created_at) }}</template></el-table-column><el-table-column label="操作" width="120" fixed="right"><template #default="scope"><el-button link type="primary" @click="openTimeline(scope.row)"><el-icon><Document /></el-icon>查看时间线</el-button></template></el-table-column>
    </UiDataTable>

    <el-dialog v-model="designerOpen" title="新建流程定义" width="min(1400px, 96vw)" top="3vh" destroy-on-close class="workflow-designer-dialog"><div class="workflow-definition-meta"><el-form inline><el-form-item label="流程编码" required><el-input v-model="form.code" placeholder="例如 expense_approval" /></el-form-item><el-form-item label="流程名称" required><el-input v-model="form.name" placeholder="例如费用审批" /></el-form-item></el-form></div><div v-loading="optionsLoading" class="workflow-builder"><WorkflowDesigner v-model="graph" @select="selectedNodeId = $event?.id || null" /><WorkflowNodeInspector :node="selectedNode" :users="users" :roles="roles" @update="updateSelectedNode" /></div><template #footer><el-button @click="designerOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存流程</el-button></template></el-dialog>
    <el-dialog v-model="actionOpen" :title="actionTitle" width="520px" destroy-on-close><div v-if="actionTask" class="workflow-action-context"><span>业务单号</span><strong>{{ actionTask.business_key }}</strong><span>当前节点</span><strong>{{ actionTask.task_key }}</strong></div><el-form label-position="top"><el-form-item v-if="['ADD_SIGN', 'TRANSFER', 'DELEGATE'].includes(actionType)" label="目标用户" required><el-select v-model="targetUserId" filterable placeholder="请选择目标用户"><el-option v-for="user in users" :key="user.id" :label="`${user.display_name}（${user.username}）`" :value="user.id" /></el-select></el-form-item><el-form-item v-if="actionType === 'CC'" label="抄送人员" required><el-select v-model="ccUserIds" multiple filterable collapse-tags placeholder="请选择抄送人员"><el-option v-for="user in users" :key="user.id" :label="`${user.display_name}（${user.username}）`" :value="user.id" /></el-select></el-form-item><el-form-item label="审批意见"><el-input v-model="actionComment" type="textarea" :rows="4" placeholder="请输入审批意见（可选）" /></el-form-item></el-form><template #footer><el-button @click="actionOpen = false">取消</el-button><el-button type="primary" :loading="actionSaving" @click="submitAction">提交{{ actionTitle }}</el-button></template></el-dialog>
    <el-dialog v-model="timelineOpen" title="流程审计时间线" width="760px"><el-timeline><el-timeline-item v-for="event in timeline" :key="event.id" :timestamp="formatDateOnly(event.created_at)" placement="top"><strong>{{ event.event_type }}</strong><span class="muted"> · {{ event.operator_name || '系统' }}</span><p v-if="event.reason">{{ event.reason }}</p><pre v-if="event.payload_json">{{ event.payload_json }}</pre></el-timeline-item></el-timeline><UiEmptyState v-if="!timeline.length" title="暂无审计记录" /></el-dialog>
  </section>
</template>