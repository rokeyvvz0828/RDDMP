<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Download, Plus, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import { loadPhysicalSubsystemOptions, loadResourceDeploymentUnitOptions } from './api'
import * as XLSX from 'xlsx'
import { computed as vueComputed, markRaw, onBeforeUnmount, ref as vueRef, shallowRef } from 'vue'
import { MarkerType, Position, VueFlow, useVueFlow, type Edge, type Node, type NodeTypesObject } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import PlanStageNode from './PlanStageNode.vue'
import PlanTaskNode from './PlanTaskNode.vue'
import PlanGantt from './PlanGantt.vue'
import './architecture.css'
import {
  addCheckItem,
  addPlanStage,
  addPlanTargets,
  addPlanTask,
  addTaskBlock,
  attachTaskWorkOrders,
  cancelCheckItem,
  cancelPlan,
  cancelStage,
  cancelTask,
  completeCheckItem,
  correctEvent,
  deleteCheckItem,
  deleteTask,
  detachWorkOrder,
  getPlan,
  getPlanDashboard,
  getPlanReport,
  getPlanTimeline,
  listPlanSuggestions,
  rejectSuggestion,
  acceptSuggestion,
  removeDependency,
  removePlanTarget,
  reopenCheckItem,
  restoreCheckItem,
  restorePlan,
  restoreStage,
  restoreTask,
  resolveTaskBlock,
  setTaskDependencies,
  startTask,
  suggestCancelCheckItem,
  loadPlanUserOptions,
  updatePlanSchedule,
  updateTaskSchedule
} from './planApi' 
import type {
  BlockView,
  CheckItemView,
  DashboardView,
  EventView,
  PlanDetailView,
  PlanStatus,
  StageDetailView,
  SuggestionView,
  TargetView,
  TaskDetailView,
  TaskStatus,
  TimelineView,
  WorkOrderType
} from './planApi'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const planId = Number(route.params.id)
const canManage = computed(() => auth.hasPermission('architecture:plan:manage') || auth.hasPermission('architecture:manage'))

const loading = ref(false)
const loadError = ref('')
const detail = ref<PlanDetailView | null>(null)
const dashboard = ref<DashboardView | null>(null)
const timeline = ref<TimelineView | null>(null)
const suggestions = ref<SuggestionView[]>([])
const activeTab = ref('execution')
const openStages = ref<string[]>([])

const planStatusLabels: Record<PlanStatus, string> = {
  NOT_STARTED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消'
}
const planStatusTones: Record<PlanStatus, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  NOT_STARTED: 'info', IN_PROGRESS: 'primary', COMPLETED: 'success', CANCELLED: 'danger'
}
const taskStatusLabels: Record<TaskStatus, string> = {
  NOT_STARTED: '未开始', WAITING_PRECEDING: '等待前置', IN_PROGRESS: '进行中',
  BLOCKED: '阻塞', COMPLETED: '已完成', CANCELLED: '已取消'
}
const taskStatusTones: Record<TaskStatus, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  NOT_STARTED: 'info', WAITING_PRECEDING: 'warning', IN_PROGRESS: 'primary',
  BLOCKED: 'danger', COMPLETED: 'success', CANCELLED: 'info'
}
const workOrderTypeLabels: Record<WorkOrderType, string> = {
  RESOURCE_REQUEST: '资源申请', NETWORK_CLB: 'CLB 工单', NETWORK_DNS: 'DNS 工单',
  NETWORK_CERT: '证书工单', CRYPTO_POOL: '加密机入池（预留）'
}

async function loadAll() {
  loading.value = true
  loadError.value = ''
  try {
    detail.value = await getPlan(planId)
    if (openStages.value.length === 0 && detail.value.stages.length > 0) {
      openStages.value = [String(detail.value.stages[0].id)]
    }
    dashboard.value = await getPlanDashboard(planId)
    timeline.value = await getPlanTimeline(planId)
    suggestions.value = await listPlanSuggestions(planId).catch(() => [])
    buildFlowchart()
    void nextTick(() => {
      flowInitialized.value = true
      window.setTimeout(() => {
        const { fitView } = useVueFlow()
        void fitView({ padding: 0.15 })
      }, 150)
    })
  } catch (error) {
    loadError.value = apiErrorMessage(error, "操作失败")
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadUserMap()
  loadAll()
})

function isPlanOwner() {
  const plan = detail.value?.plan
  return !!plan && (auth.user?.id === plan.planOwnerUserId || auth.hasPermission('architecture:manage'))
}

function isTaskExecutor(task: TaskDetailView) {
  return auth.user?.id === task.ownerUserId || task.participantUserIds.includes(auth.user?.id ?? -1) || auth.hasPermission('architecture:manage')
}

// ---------- 计划级操作 ----------
async function doCancelPlan() {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消计划', {
      inputPlaceholder: '取消原因（必填）', inputValidator: v => (v && v.trim() ? true : '取消原因不能为空')
    })
    await cancelPlan(planId, value.trim())
    ElMessage.success('计划已取消')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function doRestorePlan() {
  try {
    const { value } = await ElMessageBox.prompt('请输入恢复原因', '恢复计划', {
      inputPlaceholder: '恢复原因（必填）', inputValidator: v => (v && v.trim() ? true : '恢复原因不能为空')
    })
    await restorePlan(planId, value.trim())
    ElMessage.success('计划已恢复')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

const scheduleVisible = ref(false)
const scheduleForm = reactive({ plannedRange: null as [string, string] | null, reason: '' })

function openSchedule() {
  const start = detail.value?.plan.plannedStart ?? null
  const end = detail.value?.plan.plannedEnd ?? null
  scheduleForm.plannedRange = start && end ? [start, end] : null
  scheduleForm.reason = ''
  scheduleVisible.value = true
}

async function saveSchedule() {
  try {
    await updatePlanSchedule(planId, {
      plannedStart: scheduleForm.plannedRange?.[0] ?? null,
      plannedEnd: scheduleForm.plannedRange?.[1] ?? null,
      reason: scheduleForm.reason.trim() || null
    })
    ElMessage.success('计划时间已更新')
    scheduleVisible.value = false
    await loadAll()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

// ---------- 目标 ----------
const targetVisible = ref(false)
const targetForm = reactive({ physicalSubsystemIds: [] as number[], deploymentUnitIds: [] as number[], reason: '' })
const physicalOptions = ref<{ id: number; name: string }[]>([])
const deploymentUnitOptions = ref<{ id: number; name: string }[]>([])

async function openTargetDialog() {
  targetForm.physicalSubsystemIds = []
  targetForm.deploymentUnitIds = []
  targetForm.reason = ''
  targetVisible.value = true
  physicalOptions.value = await loadPhysicalSubsystemOptions('', 100).catch(() => [])
}

async function onPhysicalChange(ids: number[]) {
  const physicalId = ids[0]
  deploymentUnitOptions.value = physicalId ? await loadResourceDeploymentUnitOptions(physicalId, 100).catch(() => []) : []
}

async function saveTargets() {
  if (targetForm.physicalSubsystemIds.length === 0 && targetForm.deploymentUnitIds.length === 0) {
    ElMessage.warning('请选择目标')
    return
  }
  if (!targetForm.reason.trim()) {
    ElMessage.warning('请填写增加目标原因')
    return
  }
  try {
    await addPlanTargets(planId, {
      physicalSubsystemIds: targetForm.physicalSubsystemIds,
      deploymentUnitIds: targetForm.deploymentUnitIds,
      reason: targetForm.reason.trim()
    })
    ElMessage.success('目标已增加，任务已按计划模板生成')
    targetVisible.value = false
    await loadAll()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function doRemoveTarget(target: TargetView) {
  try {
    const { value } = await ElMessageBox.prompt('移出目标将自动取消其未完成任务（已完成保留），请输入原因', '移出目标', {
      inputPlaceholder: '原因（必填）', inputValidator: v => (v && v.trim() ? true : '原因不能为空')
    })
    await removePlanTarget(planId, target.id, value.trim())
    ElMessage.success('目标已移出')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

// ---------- 环节/任务/检查项 ----------
const stageVisible = ref(false)
const stageForm = reactive({ name: '', reason: '' })

function openAddStage() {
  stageForm.name = ''
  stageForm.reason = ''
  stageVisible.value = true
}

async function saveStage() {
  if (!stageForm.name.trim()) {
    ElMessage.warning('请输入环节名称')
    return
  }
  try {
    await addPlanStage(planId, { name: stageForm.name.trim(), ownerUserId: detail.value?.plan.planOwnerUserId ?? 0 })
    ElMessage.success('环节已新增')
    stageVisible.value = false
    await loadAll()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function doCancelStage(stage: StageDetailView) {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', `取消环节「${stage.name}」`, {
      inputPlaceholder: '取消原因（必填）', inputValidator: v => (v && v.trim() ? true : '取消原因不能为空')
    })
    await cancelStage(stage.id, value.trim())
    ElMessage.success('环节已取消')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function doRestoreStage(stage: StageDetailView) {
  try {
    const { value } = await ElMessageBox.prompt('请输入恢复原因', `恢复环节「${stage.name}」`, {
      inputPlaceholder: '恢复原因（必填）', inputValidator: v => (v && v.trim() ? true : '恢复原因不能为空')
    })
    await restoreStage(stage.id, value.trim())
    ElMessage.success('环节已恢复')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

const taskVisible = ref(false)
const taskForm = reactive({
  stageId: 0, targetId: null as number | null, name: '', checkItemNames: '' as string
})

function openAddTask(stage: StageDetailView) {
  taskForm.stageId = stage.id
  taskForm.targetId = null
  taskForm.name = ''
  taskForm.checkItemNames = ''
  taskVisible.value = true
}

async function saveTask() {
  const checkItems = taskForm.checkItemNames.split('\n').map(item => item.trim()).filter(Boolean)
  if (!taskForm.name.trim() || checkItems.length === 0) {
    ElMessage.warning('请填写任务名称与至少一个检查项（每行一个）')
    return
  }
  try {
    await addPlanTask(planId, {
      stageId: taskForm.stageId, name: taskForm.name.trim(), targetId: taskForm.targetId,
      ownerUserId: detail.value?.plan.planOwnerUserId ?? 0, checkItemNames: checkItems
    })
    ElMessage.success('任务已新增')
    taskVisible.value = false
    await loadAll()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function doStartTask(task: TaskDetailView) {
  try {
    await startTask(task.id)
    ElMessage.success('任务已开始')
    await loadAll()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function doCancelTask(task: TaskDetailView) {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', `取消任务「${task.name}」`, {
      inputPlaceholder: '取消原因（必填）', inputValidator: v => (v && v.trim() ? true : '取消原因不能为空')
    })
    await cancelTask(task.id, value.trim())
    ElMessage.success('任务已取消')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function doRestoreTask(task: TaskDetailView) {
  try {
    const { value } = await ElMessageBox.prompt('请输入恢复原因', `恢复任务「${task.name}」`, {
      inputPlaceholder: '恢复原因（必填）', inputValidator: v => (v && v.trim() ? true : '恢复原因不能为空')
    })
    await restoreTask(task.id, value.trim())
    ElMessage.success('任务已恢复')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function doDeleteTask(task: TaskDetailView) {
  try {
    const { value } = await ElMessageBox.prompt('仅从未执行的错误任务可删除，请输入删除原因', '删除任务', {
      inputPlaceholder: '删除原因（必填）', inputValidator: v => (v && v.trim() ? true : '删除原因不能为空')
    })
    await deleteTask(task.id, value.trim())
    ElMessage.success('任务已删除')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function promptReason(title: string, label: string) {
  const { value } = await ElMessageBox.prompt(label, title, {
    inputPlaceholder: '原因（必填）', inputValidator: v => (v && v.trim() ? true : '原因不能为空')
  })
  return value.trim()
}

// ---------- 任务抽屉 ----------
const currentTask = ref<TaskDetailView | null>(null)
const taskDrawerVisible = ref(false)
const currentStage = ref<StageDetailView | null>(null)

function openTask(task: TaskDetailView, stage: StageDetailView) {
  currentTask.value = task
  currentStage.value = stage
  taskDrawerVisible.value = true
}

async function refreshCurrentTask() {
  const plan = await getPlan(planId)
  detail.value = plan
  if (currentTask.value) {
    for (const stage of plan.stages) {
      const found = stage.tasks.find(task => task.id === currentTask.value?.id)
      if (found) {
        currentTask.value = found
        currentStage.value = stage
        break
      }
    }
  }
}

async function onCompleteCheckItem(item: CheckItemView) {
  try {
    await completeCheckItem(item.id)
    ElMessage.success('检查项已完成')
    await refreshCurrentTask()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function onReopenCheckItem(item: CheckItemView) {
  try {
    const reason = await promptReason('重新打开检查项', '请输入重新打开原因')
    await reopenCheckItem(item.id, reason)
    ElMessage.success('检查项已重新打开')
    await refreshCurrentTask()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function onCancelCheckItem(item: CheckItemView) {
  try {
    const reason = await promptReason('取消检查项', '请输入取消原因（豁免）')
    await cancelCheckItem(item.id, reason)
    ElMessage.success('检查项已取消（豁免）')
    await refreshCurrentTask()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function onRestoreCheckItem(item: CheckItemView) {
  try {
    const reason = await promptReason('恢复检查项', '请输入恢复原因')
    await restoreCheckItem(item.id, reason)
    ElMessage.success('检查项已恢复')
    await refreshCurrentTask()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function onSuggestCancel(item: CheckItemView) {
  try {
    const reason = await promptReason('提交取消建议', '请输入建议取消原因')
    await suggestCancelCheckItem(item.id, reason)
    ElMessage.success('取消建议已提交，等待计划责任人处理')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function onDeleteCheckItem(item: CheckItemView) {
  try {
    const reason = await promptReason('删除检查项', '仅未执行的错误检查项可删除，请输入原因')
    await deleteCheckItem(item.id, reason)
    ElMessage.success('检查项已删除')
    await refreshCurrentTask()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

const addCheckItemVisible = ref(false)
const addCheckItemName = ref('')

async function saveCheckItem() {
  if (!currentTask.value || !addCheckItemName.value.trim()) return
  try {
    await addCheckItem(currentTask.value.id, { name: addCheckItemName.value.trim() })
    ElMessage.success('检查项已新增')
    addCheckItemVisible.value = false
    addCheckItemName.value = ''
    await refreshCurrentTask()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

// 依赖
const depVisible = ref(false)
const depSelected = ref<number[]>([])
const depReason = ref('')
const depOptions = computed(() => {
  if (!currentTask.value || !detail.value) return []
  const currentIds = currentTask.value.dependencies.filter(dep => !dep.removed).map(dep => dep.predecessorId)
  const options: { id: number; name: string; disabled: boolean }[] = []
  for (const stage of detail.value.stages) {
    for (const task of stage.tasks) {
      if (task.id === currentTask.value.id || task.cancelled) continue
      options.push({
        id: task.id,
        name: `[${stage.name}] ${task.name}${task.targetName ? `（${task.targetName}）` : ''}`,
        disabled: currentIds.includes(task.id)
      })
    }
  }
  return options
})

function openDependency() {
  depSelected.value = currentTask.value?.dependencies.filter(dep => !dep.removed).map(dep => dep.predecessorId) ?? []
  depReason.value = ''
  depVisible.value = true
}

async function saveDependency() {
  try {
    await setTaskDependencies(currentTask.value!.id, depSelected.value, depReason.value.trim() || null)
    ElMessage.success('前置依赖已更新')
    depVisible.value = false
    await refreshCurrentTask()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function onRemoveDependency(depId: number) {
  try {
    await removeDependency(depId, '移除前置依赖')
    ElMessage.success('依赖已移除')
    await refreshCurrentTask()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

// 阻塞
const blockVisible = ref(false)
const blockForm = reactive({ description: '', impact: '', expectedResolveAt: null as string | null })

function openBlock() {
  blockForm.description = ''
  blockForm.impact = ''
  blockForm.expectedResolveAt = null
  blockVisible.value = true
}

async function saveBlock() {
  if (!blockForm.description.trim()) {
    ElMessage.warning('请填写阻塞描述')
    return
  }
  try {
    await addTaskBlock(currentTask.value!.id, {
      description: blockForm.description.trim(),
      impact: blockForm.impact.trim() || null,
      ownerUserId: currentTask.value!.ownerUserId,
      expectedResolveAt: blockForm.expectedResolveAt
    })
    ElMessage.success('阻塞已登记')
    blockVisible.value = false
    await refreshCurrentTask()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function onResolveBlock(block: BlockView) {
  try {
    const { value } = await ElMessageBox.prompt('请输入解除说明（可选）', '解除阻塞', {
      inputPlaceholder: '说明（可选）', inputValidator: () => true
    })
    await resolveTaskBlock(block.id, value?.trim() || null)
    ElMessage.success('阻塞已解除')
    await refreshCurrentTask()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

// 工单关联
const workOrderVisible = ref(false)
const workOrderForm = reactive({ type: 'RESOURCE_REQUEST' as WorkOrderType, workOrderIds: '', reason: '' })

function openWorkOrder() {
  workOrderForm.type = 'RESOURCE_REQUEST'
  workOrderForm.workOrderIds = ''
  workOrderForm.reason = ''
  workOrderVisible.value = true
}

async function saveWorkOrder() {
  const ids = workOrderForm.workOrderIds.split(/[,，\s]+/).map(item => Number(item)).filter(Boolean)
  if (ids.length === 0) {
    ElMessage.warning('请填写工单 ID（多个用逗号分隔）')
    return
  }
  try {
    await attachTaskWorkOrders(currentTask.value!.id, { workOrderType: workOrderForm.type, workOrderIds: ids, reason: workOrderForm.reason.trim() || null })
    ElMessage.success('工单已关联')
    workOrderVisible.value = false
    await refreshCurrentTask()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function onDetachWorkOrder(relationId: number) {
  try {
    const reason = await promptReason('解除工单关联', '请输入解除原因')
    await detachWorkOrder(relationId, reason)
    ElMessage.success('工单关联已解除')
    await refreshCurrentTask()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

// 时间
const taskScheduleVisible = ref(false)
const taskScheduleForm = reactive({ plannedStart: null as string | null, plannedEnd: null as string | null, reason: '' })

function openTaskSchedule() {
  taskScheduleForm.plannedStart = currentTask.value?.plannedStart ?? null
  taskScheduleForm.plannedEnd = currentTask.value?.plannedEnd ?? null
  taskScheduleForm.reason = ''
  taskScheduleVisible.value = true
}

async function saveTaskSchedule() {
  try {
    await updateTaskSchedule(currentTask.value!.id, {
      plannedStart: taskScheduleForm.plannedStart,
      plannedEnd: taskScheduleForm.plannedEnd,
      reason: taskScheduleForm.reason.trim() || null
    })
    ElMessage.success('任务时间已更新')
    taskScheduleVisible.value = false
    await refreshCurrentTask()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

// 事件更正
async function onCorrectEvent(event: EventView) {
  try {
    const { value } = await ElMessageBox.prompt('请输入更正后的时间（yyyy-MM-ddTHH:mm:ss）与原因（分号分隔）', '更正事件', {
      inputPlaceholder: '新时间;更正原因',
      inputValidator: v => (v && v.includes(';') ? true : '格式：新时间;更正原因')
    })
    const [newTime, ...reasonParts] = value.split(';')
    await correctEvent(event.id, { newOccurredAt: newTime.trim(), reason: reasonParts.join(';').trim() })
    ElMessage.success('事件已更正')
    await refreshCurrentTask()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

// 建议处理
async function onAcceptSuggestion(suggestion: SuggestionView) {
  try {
    await acceptSuggestion(suggestion.id, '同意取消')
    ElMessage.success('建议已接受，检查项已取消')
    await loadAll()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

async function onRejectSuggestion(suggestion: SuggestionView) {
  try {
    await rejectSuggestion(suggestion.id, '不同意取消')
    ElMessage.success('建议已拒绝')
    await loadAll()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  }
}

// ---------- 看板/时间视图/报告 ----------
const users = ref<Record<number, string>>({})

async function loadUserMap() {
  try {
    let page = 1
    while (page <= 5) {
      const result = await loadPlanUserOptions('', 100)
      for (const user of result.records) {
        users.value[user.id] = user.displayName
      }
      if (page * 100 >= result.total || result.records.length === 0) break
      page += 1
    }
  } catch {
    // 映射加载失败不阻断页面，仅显示原始 id
  }
}

function userName(userId: number | null | undefined) {
  if (!userId) return '—'
  return users.value[userId] || String(userId)
}

const reportLoading = ref(false)
const reportData = ref<Awaited<ReturnType<typeof getPlanReport>> | null>(null)

async function exportReport() {
  reportLoading.value = true
  try {
    if (!reportData.value) {
      reportData.value = await getPlanReport(planId)
    }
    const d = reportData.value.detail
    const rows: Record<string, string | number | null>[] = []
    for (const stage of d.stages) {
      for (const task of stage.tasks) {
        for (const check of task.checkItems) {
          rows.push({
            计划: d.plan.name, 环节: stage.name, 任务: task.name, 目标: task.targetName ?? '计划级',
            检查项: check.name, 检查指标及内容: check.guide ?? '',
            状态: check.status === 'COMPLETED' ? '已完成' : check.status === 'CANCELLED' ? '已取消' : '待完成',
            检查人: check.completedBy ?? '', 备注: check.remark ?? '',
            任务状态: taskStatusLabels[task.status as TaskStatus] ?? task.status,
            任务进度: task.progress ?? 0, 豁免: task.waivedAll ? '全部豁免' : '',
            责任人: task.ownerUserId, 计划开始: task.plannedStart, 计划结束: task.plannedEnd,
            实际开始: task.actualStart, 实际结束: task.actualEnd
          })
        }
      }
    }
    const sheet = XLSX.utils.json_to_sheet(rows)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, sheet, '搭建进度报告')
    XLSX.writeFile(workbook, `${d.plan.planNo}-搭建进度报告.xlsx`)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, "操作失败"))
  } finally {
    reportLoading.value = false
  }
}

function statusOfStage(stage: StageDetailView) {
  return planStatusLabels[stage.status as PlanStatus] ?? stage.status
}

function taskRef(taskId: number) {
  for (const stage of detail.value?.stages ?? []) {
    const task = stage.tasks.find(item => item.id === taskId)
    if (task) {
      return { task, stageName: stage.name }
    }
  }
  return null
}

function dependencyTaskName(taskId: number) {
  return taskRef(taskId)?.task.name ?? ''
}

function dependencyTaskRef(taskId: number) {
  const ref = taskRef(taskId)
  return ref ? { stageName: ref.stageName, status: ref.task.status } : null
}

function jumpToTask(taskId: number) {
  const ref = taskRef(taskId)
  if (!ref) {
    ElMessage.warning('前置任务已删除或不在当前计划中')
    return
  }
  currentTask.value = ref.task
  currentStage.value = detail.value?.stages.find(stage => stage.tasks.some(task => task.id === taskId)) ?? null
}

// ---------- 流程图（只读，与模板结构编辑页同款样式）----------
const flowchartNodes = shallowRef<Node[]>([])
const flowchartEdges = shallowRef<Edge[]>([])
const nodeTypes: NodeTypesObject = { planStage: markRaw(PlanStageNode), planTask: markRaw(PlanTaskNode) }
const flowInitialized = vueRef(false)
const flowActiveStageId = vueRef<number | null>(null)
const flowActiveTaskId = vueRef<number | null>(null)

const FLOW_STAGE_W = 460
const FLOW_STAGE_HEADER_H = 92
const FLOW_TASK_W = 150
const FLOW_TASK_H = 68
const FLOW_TASK_COLS = 2
const FLOW_TASK_GAP_X = 50
const FLOW_TASK_GAP_Y = 16
const FLOW_TASK_ROW_GAP = 60
const FLOW_STAGE_GAP = 72

function flowStageBounds(stage: { tasks: unknown[] }) {
  const rows = stage.tasks.length === 0 ? 1 : Math.ceil(stage.tasks.length / FLOW_TASK_COLS)
  const h = FLOW_STAGE_HEADER_H + FLOW_TASK_GAP_Y + rows * FLOW_TASK_H + (rows - 1) * FLOW_TASK_ROW_GAP + 14
  return { w: FLOW_STAGE_W, h }
}

function flowFocusCanvasNode(stageId: number, taskId: number | null) {
  flowActiveStageId.value = stageId
  flowActiveTaskId.value = taskId
  // 重建节点数组以同步选中态（与模板编辑页一致）
  flowchartNodes.value = (flowchartNodes.value as Node[]).map(node => {
    const isStage = node.id === 'stage-' + stageId
    const isTask = taskId != null && node.id === 'task-' + taskId
    return { ...node, data: { ...(node.data || {}), selected: isStage || isTask } }
  })
  if (flowInitialized.value) {
    void nextTick(() => {
      window.setTimeout(() => {
        const { setCenter } = useVueFlow()
        const nodeId = taskId != null ? 'task-' + taskId : 'stage-' + stageId
        const target = flowchartNodes.value.find(node => node.id === nodeId)
        if (target) {
          const pos = target.position as { x: number; y: number }
          const parent = target.parentNode ? flowchartNodes.value.find(n => n.id === target.parentNode) : null
          const baseX = parent ? (parent.position as { x: number; y: number }).x : 0
          const baseY = parent ? (parent.position as { x: number; y: number }).y : 0
          const w = taskId != null ? FLOW_TASK_W : FLOW_STAGE_W
          const h = taskId != null ? FLOW_TASK_H : flowStageBounds(stageRef(stageId) ?? { tasks: [] }).h
          void setCenter(baseX + pos.x + w / 2, baseY + pos.y + h / 2, { zoom: 1.1, duration: 260 })
        }
      }, 160)
    })
  }
}

function stageRef(stageId: number) {
  return detail.value?.stages.find(stage => stage.id === stageId) ?? null
}

function scrollPanelToStageOrTask(stageId: number, taskId: number | null) {
  const panel = document.querySelector('.plan-exec-editor__panel')
  if (!panel) return
  const selector = taskId != null ? `[data-exec-task="${taskId}"]` : `[data-exec-stage="${stageId}"]`
  const target = panel.querySelector(selector)
  target?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
}

function onFlowNodeClick(event: { node: Node }) {
  const nodeId = event.node.id
  if (nodeId.startsWith('stage-')) {
    const stageId = Number(nodeId.slice(6))
    flowActiveStageId.value = stageId
    flowActiveTaskId.value = null
    focusExecList(stageId, null)
  } else if (nodeId.startsWith('task-')) {
    const taskId = Number(nodeId.slice(5))
    const ref = detail.value?.stages.find(stage => stage.tasks.some(task => task.id === taskId))
    if (ref) {
      flowActiveStageId.value = ref.id
      flowActiveTaskId.value = taskId
      focusExecList(ref.id, taskId)
    }
  }
}

function focusExecList(stageId: number, taskId: number | null) {
  flowActiveStageId.value = stageId
  flowActiveTaskId.value = taskId
  // 展开所属环节，便于定位
  if (!openStages.value.includes(String(stageId))) {
    openStages.value = [...openStages.value, String(stageId)]
  }
  flowFocusCanvasNode(stageId, taskId)
  void nextTick(() => scrollPanelToStageOrTask(stageId, taskId))
}

function buildFlowchart() {
  const planDetail = detail.value
  if (!planDetail) return
  const nodes: Node[] = []
  const edges: Edge[] = []
  let y = 24
  planDetail.stages.forEach(stage => {
    const stageNodeId = 'stage-' + stage.id
    const size = flowStageBounds(stage)
    nodes.push({
      id: stageNodeId,
      type: 'planStage',
      position: { x: 48, y },
      data: { stage, readonly: true, containerHeight: size.h },
      draggable: false,
      connectable: false
    })
    stage.tasks.forEach((task, taskIndex) => {
      const taskNodeId = 'task-' + task.id
      const col = taskIndex % FLOW_TASK_COLS
      const row = Math.floor(taskIndex / FLOW_TASK_COLS)
      const px = 48 + FLOW_TASK_GAP_X + col * (FLOW_TASK_W + FLOW_TASK_GAP_X - 4)
      const py = y + FLOW_STAGE_HEADER_H + FLOW_TASK_GAP_Y + row * (FLOW_TASK_H + FLOW_TASK_ROW_GAP)
      nodes.push({
        id: taskNodeId,
        type: 'planTask',
        parentNode: stageNodeId,
        position: { x: px - 48, y: py - y },
        data: { task, readonly: true },
        draggable: false,
        connectable: false
      })
      const dependencyEdges = task.dependencies.filter(dep => !dep.removed)
      for (const dep of dependencyEdges) {
        const fromId = 'task-' + dep.predecessorId
        if (nodes.some(node => node.id === fromId)) {
          edges.push({
            id: 'td-' + dep.id,
            type: 'smoothstep',
            source: fromId,
            target: taskNodeId,
            markerEnd: { type: MarkerType.ArrowClosed },
            style: { stroke: 'var(--muted)' }
          })
        }
      }
    })
    y += size.h + FLOW_STAGE_GAP
  })
  for (const pair of planDetail.stageDependencies) {
    const source = 'stage-' + pair[1]
    const target = 'stage-' + pair[0]
    // 后端 pair 为 [后续stageId, 前置stageId]
    const fromNode = nodes.find(node => node.id === source)
    const toNode = nodes.find(node => node.id === target)
    if (fromNode && toNode) {
      edges.push({
        id: 'sd-' + source + '-' + target,
        type: 'smoothstep',
        source,
        target,
        markerEnd: { type: MarkerType.ArrowClosed },
        style: { stroke: 'var(--brand)' }
      })
    }
  }
  flowchartNodes.value = nodes
  flowchartEdges.value = edges
  if (flowInitialized.value) {
    void nextTick(() => {
      window.setTimeout(() => {
        const { fitView } = useVueFlow()
        void fitView({ padding: 0.15 })
      }, 120)
    })
  }
}

function formatRange(start: string | null, end: string | null) {
  const fmt = (value: string | null) => (value ? value.replace('T', ' ').slice(0, 16) : '—')
  return `${fmt(start)} ~ ${fmt(end)}`
}
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader :title="detail ? `${detail.plan.name}（${detail.plan.planNo}）` : '计划详情'" description="环境搭建计划执行与跟踪">
      <template #actions>
        <el-button @click="router.push({ name: 'architecture-plans' })"><el-icon><ArrowLeft /></el-icon>返回列表</el-button>
        <el-button @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button v-if="detail && isPlanOwner()" @click="openSchedule">计划时间</el-button>
        <el-button v-if="detail && isPlanOwner() && detail.plan.status !== 'COMPLETED' && detail.plan.status !== 'CANCELLED'" type="danger" plain @click="doCancelPlan">取消计划</el-button>
        <el-button v-if="detail && isPlanOwner() && detail.plan.status === 'CANCELLED'" type="primary" @click="doRestorePlan">恢复计划</el-button>
      </template>
    </UiPageHeader>

    <div v-if="loadError" class="architecture-page__error">
      <el-result icon="error" :title="loadError">
        <template #extra><el-button type="primary" @click="loadAll">重试</el-button></template>
      </el-result>
    </div>

    <template v-else-if="detail">
      <section class="plan-summary-panel" v-loading="loading">
        <header class="plan-summary-panel__header">
          <div>
            <span class="panel-kicker">搭建摘要</span>
            <h3>环境搭建计划</h3>
          </div>
          <div class="plan-summary-panel__tags">
            <UiStatusTag :value="detail.plan.status" :labels="planStatusLabels" :tone="planStatusTones[detail.plan.status]" />
            <el-tag v-if="detail.hasBlocked" type="warning" size="small">存在阻塞</el-tag>
            <el-tag v-if="detail.hasOverdue" type="danger" size="small">存在逾期</el-tag>
            <el-tag v-if="detail.hasWaived" type="info" size="small">存在豁免</el-tag>
            <el-tag v-if="detail.uncompletable" type="danger" size="small">全部环节已取消，不能自动完成</el-tag>
          </div>
        </header>
        <div class="plan-summary-context">
          <div class="plan-summary-context__progress">
            <small>整体进度</small>
            <strong>{{ detail.progress ?? 0 }}%</strong>
            <el-progress :percentage="detail.progress ?? 0" :stroke-width="6" :show-text="false" />
          </div>
          <div>
            <small>环境</small>
            <strong>{{ detail.environmentName }}（{{ detail.environmentCode }}）</strong>
          </div>
          <div>
            <small>模板版本</small>
            <strong>v{{ detail.plan.templateVersionNo }}</strong>
          </div>
          <div>
            <small>计划时间</small>
            <strong>{{ formatRange(detail.plan.plannedStart, detail.plan.plannedEnd) }}</strong>
          </div>
          <div>
            <small>实际时间</small>
            <strong>{{ formatRange(detail.plan.actualStart, detail.plan.actualEnd) }}</strong>
          </div>
          <div v-if="detail.plan.cancelled && detail.plan.cancelReason" class="plan-summary-context__reason">
            <small>取消原因</small>
            <strong>{{ detail.plan.cancelReason }}</strong>
          </div>
        </div>
      </section>

      <div class="plan-detail-targets">
        <div class="plan-detail-section-title">
          <span>计划目标</span>
          <el-button v-if="isPlanOwner()" link type="primary" :icon="Plus" @click="openTargetDialog">增加目标</el-button>
        </div>
        <div class="plan-detail-targets__list">
          <el-tag v-for="target in detail.targets" :key="target.id" :type="target.removed ? 'info' : 'primary'" effect="plain"
                  :closable="isPlanOwner() && !target.removed" class="plan-detail-target" @close="doRemoveTarget(target)">
            {{ target.targetType === 'PHYSICAL_SUBSYSTEM' ? '物理子系统' : '部署单元' }}：{{ target.targetName }}
            <span v-if="target.hasDiff" class="plan-target-diff">（快照「{{ target.snapshotName }}」≠ 当前「{{ target.currentName }}」）</span>
          </el-tag>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="执行视图" name="execution">
          <div class="plan-exec-editor">
            <!-- 左：流程图画布（与模板编辑页同款样式，只读） -->
            <section class="plan-exec-editor__canvas" aria-label="计划流程图">
              <div class="workflow-designer__canvas plan-flow-canvas plan-exec-editor__flow">
                <VueFlow :nodes="flowchartNodes" :edges="flowchartEdges" :node-types="nodeTypes"
                         :nodes-draggable="false" :nodes-connectable="false" :elements-selectable="false"
                         :min-zoom="0.4" :max-zoom="1.5" fit-view-on-init @init="flowInitialized = true"
                         @node-click="onFlowNodeClick">
                  <template #node-planStage="nodeProps">
                    <PlanStageNode v-bind="nodeProps" />
                  </template>
                  <template #node-planTask="nodeProps">
                    <PlanTaskNode v-bind="nodeProps" />
                  </template>
                </VueFlow>
                <div class="plan-flow-canvas__legend">
                  <span>■ 环节依赖（品牌色箭头）</span>
                  <span>→ 任务依赖（灰色箭头）</span>
                  <span>点击节点定位到右侧执行列表</span>
                </div>
              </div>
            </section>

            <!-- 右：执行列表（环节/任务/检查项） -->
            <section class="plan-exec-editor__panel" aria-label="执行列表">
              <div v-if="suggestions.length > 0" class="plan-suggestion-bar">
                <span>待处理取消建议（{{ suggestions.length }}）：</span>
                <el-tag v-for="suggestion in suggestions" :key="suggestion.id" type="warning" style="margin-right: 8px">
                  #{{ suggestion.checkItemId }} {{ suggestion.reason }}
                </el-tag>
                <el-dropdown>
                  <el-button size="small">处理</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="suggestion in suggestions" :key="suggestion.id" @click="onAcceptSuggestion(suggestion)">
                        接受 #{{ suggestion.checkItemId }}（取消检查项）
                      </el-dropdown-item>
                      <el-dropdown-item v-for="suggestion in suggestions" :key="'r' + suggestion.id" @click="onRejectSuggestion(suggestion)">
                        拒绝 #{{ suggestion.checkItemId }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>

              <el-collapse v-if="detail.stages.length > 0" v-model="openStages">
                <el-collapse-item v-for="stage in detail.stages" :key="stage.id" :name="String(stage.id)">
                  <template #title>
                    <div class="plan-exec-stage-header" :data-exec-stage="stage.id"
                         :class="{ 'is-linked': stage.id === flowActiveStageId }"
                         @click.stop="focusExecList(stage.id, null)">
                      <span class="plan-exec-stage-header__name">{{ stage.stageNo }}. {{ stage.name }}</span>
                      <el-progress :percentage="stage.progress ?? 0" :stroke-width="8" :show-text="false" style="width: 120px" />
                      <span class="plan-exec-stage-header__percent">{{ stage.progress ?? 0 }}%</span>
                      <el-tag size="small" :type="stage.cancelled ? 'info' : 'primary'">{{ statusOfStage(stage) }}</el-tag>
                      <el-tag v-if="stage.hasWaived" size="small" type="info">存在豁免</el-tag>
                      <span class="plan-exec-stage-header__count">{{ stage.tasks.length }} 个任务</span>
                    </div>
                  </template>

                  <div class="plan-stage-actions">
                    <el-button v-if="canManage && !stage.cancelled" link type="primary" @click="openAddTask(stage)"><el-icon><Plus /></el-icon>新增任务</el-button>
                    <el-button v-if="isPlanOwner() && !stage.cancelled" link type="danger" @click="doCancelStage(stage)">取消环节</el-button>
                    <el-button v-if="isPlanOwner() && stage.cancelled" link type="primary" @click="doRestoreStage(stage)">恢复环节</el-button>
                  </div>
                  <UiEmptyState v-if="stage.tasks.length === 0" description="该环节暂无任务" />

                  <div class="plan-exec-task-list">
                    <article v-for="task in stage.tasks" :key="task.id" class="plan-exec-task"
                             :data-exec-task="task.id" :class="{ 'is-linked': task.id === flowActiveTaskId }"
                             @click="focusExecList(stage.id, task.id)">
                      <header class="plan-exec-task-header">
                        <span class="plan-exec-task-header__name">{{ task.name }}</span>
                        <span v-if="task.targetName" class="plan-exec-task-header__target">{{ task.targetName }}</span>
                        <UiStatusTag :value="task.status" :labels="taskStatusLabels" :tone="taskStatusTones[task.status as TaskStatus]" />
                        <el-tag v-if="task.waivedAll" size="small" type="info">全部豁免</el-tag>
                        <el-tag v-if="task.overdue" size="small" type="danger">逾期</el-tag>
                        <el-tag v-if="task.hasBlocked" size="small" type="warning">阻塞</el-tag>
                        <el-tag v-if="task.hasOpenWorkOrder" size="small">未结束工单</el-tag>
                        <span class="plan-exec-task-header__progress">{{ task.progress ?? 0 }}%</span>
                      </header>

                      <div class="plan-exec-task-body">
                        <div class="plan-exec-task-body__checks">
                          <article v-for="(item, index) in task.checkItems" :key="item.id"
                                   class="plan-exec-check" :class="{ 'is-completed': item.status === 'COMPLETED', 'is-cancelled': item.cancelled }">
                            <div class="plan-exec-check__main">
                              <span class="plan-exec-check__no">{{ index + 1 }}</span>
                              <span class="plan-exec-check__name">{{ item.name }}</span>
                              <el-tag size="small" :type="item.status === 'COMPLETED' ? 'success' : item.cancelled ? 'info' : 'warning'">
                                {{ item.status === 'COMPLETED' ? '已完成' : item.cancelled ? '已取消' : '待完成' }}
                              </el-tag>
                            </div>
                            <p v-if="item.guide" class="plan-exec-check__guide">{{ item.guide }}</p>
                          </article>
                        </div>
                        <div class="plan-exec-task-body__actions">
                          <el-button link type="primary" @click="openTask(task, stage)"><el-icon><View /></el-icon>任务详情</el-button>
                          <el-button v-if="isTaskExecutor(task) && task.status === 'NOT_STARTED'" link type="success" @click="doStartTask(task)">开始</el-button>
                          <el-dropdown v-if="isPlanOwner()">
                            <el-button link>更多</el-button>
                            <template #dropdown>
                              <el-dropdown-menu>
                                <el-dropdown-item v-if="!task.cancelled && task.status !== 'COMPLETED'" @click="doCancelTask(task)">取消任务</el-dropdown-item>
                                <el-dropdown-item v-if="task.cancelled" @click="doRestoreTask(task)">恢复任务</el-dropdown-item>
                                <el-dropdown-item v-if="task.status === 'NOT_STARTED' || task.status === 'WAITING_PRECEDING'" @click="doDeleteTask(task)">删除（未执行错误内容）</el-dropdown-item>
                              </el-dropdown-menu>
                            </template>
                          </el-dropdown>
                        </div>
                      </div>
                    </article>
                  </div>
                </el-collapse-item>
              </el-collapse>
              <el-button v-if="isPlanOwner()" style="margin-top: 12px" @click="openAddStage"><el-icon><Plus /></el-icon>新增环节</el-button>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="看板" name="dashboard">
          <div v-if="dashboard" class="plan-board">
            <div v-for="stage in dashboard.stages" :key="stage.id" class="plan-board__stage">
              <div class="plan-board__stage-header">
                <span>{{ stage.name }}</span>
                <span class="plan-board__stage-progress">{{ stage.progress ?? 0 }}%</span>
              </div>
              <div v-for="task in stage.tasks" :key="task.id" class="plan-board__task">
                <div class="plan-board__task-name">{{ task.name }}</div>
                <div class="plan-board__task-meta">
                  <el-tag size="small" :type="task.hasBlocked ? 'danger' : task.overdue ? 'danger' : 'info'">{{ taskStatusLabels[task.status as TaskStatus] }}</el-tag>
                  <span>{{ task.progress ?? 0 }}%</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="时间视图" name="timeline">
          <PlanGantt v-if="timeline" :rows="timeline.rows" height="clamp(420px, 58dvh, 680px)" aria-label="搭建计划排期甘特图" />
        </el-tab-pane>

        <el-tab-pane label="报告" name="report">
          <el-button type="primary" :loading="reportLoading" @click="exportReport"><el-icon><Download /></el-icon>导出搭建进度报告（Excel）</el-button>
          <p class="plan-report-tip">进度报告内容与页面数据一致：环节/任务/目标/状态/进度/时间按当前有效检查项计算。</p>
        </el-tab-pane>

        <el-tab-pane label="操作历史" name="history">
          <el-table :data="detail.events" size="small">
            <el-table-column prop="occurredAt" label="时间" width="170" />
            <el-table-column prop="objectType" label="对象类型" width="110" />
            <el-table-column prop="eventType" label="事件" width="110" />
            <el-table-column label="操作人" width="120">
              <template #default="{ row }">{{ userName(row.operatorUserId) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="原因/说明" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button v-if="isPlanOwner() && (row.eventType === 'START' || row.eventType === 'COMPLETE' || row.eventType === 'REOPEN') && !row.correctOfEventId"
                           link type="warning" @click="onCorrectEvent(row)">更正</el-button>
              </template>
            </el-table-column>
          </el-table>
          <UiEmptyState v-if="detail.events.length === 0" description="暂无执行事件" />
        </el-tab-pane>
      </el-tabs>
    </template>
    <UiEmptyState v-else-if="!loading" description="计划不存在或无权限" />

    <!-- 计划时间 -->
    <el-dialog v-model="scheduleVisible" title="计划时间" width="460px">
      <el-form label-width="100px">
        <el-form-item label="计划时间"><el-date-picker v-model="scheduleForm.plannedRange" type="datetimerange" range-separator="至" start-placeholder="计划开始时间" end-placeholder="计划结束时间" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="调整原因"><el-input v-model="scheduleForm.reason" placeholder="计划开始后调整必须填写原因" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSchedule">保存</el-button>
      </template>
    </el-dialog>

    <!-- 增加目标 -->
    <el-dialog v-model="targetVisible" title="增加计划目标" width="560px">
      <el-form label-width="100px">
        <el-form-item label="物理子系统">
          <el-select v-model="targetForm.physicalSubsystemIds" multiple filterable style="width: 100%" @change="onPhysicalChange">
            <el-option v-for="option in physicalOptions" :key="option.id" :label="option.name" :value="option.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部署单元">
          <el-select v-model="targetForm.deploymentUnitIds" multiple filterable style="width: 100%">
            <el-option v-for="option in deploymentUnitOptions" :key="option.id" :label="option.name" :value="option.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因" required><el-input v-model="targetForm.reason" placeholder="增加目标原因（必填）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="targetVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTargets">保存（按计划模板生成任务）</el-button>
      </template>
    </el-dialog>

    <!-- 新增环节 -->
    <el-dialog v-model="stageVisible" title="新增环节" width="440px">
      <el-form label-width="90px">
        <el-form-item label="环节名称" required><el-input v-model="stageForm.name" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stageVisible = false">取消</el-button>
        <el-button type="primary" @click="saveStage">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增任务 -->
    <el-dialog v-model="taskVisible" title="新增任务" width="520px">
      <el-form label-width="100px">
        <el-form-item label="任务名称" required><el-input v-model="taskForm.name" maxlength="300" /></el-form-item>
        <el-form-item label="目标">
          <el-select v-model="taskForm.targetId" clearable filterable placeholder="计划级任务可不选" style="width: 100%">
            <el-option v-for="target in detail?.targets.filter(item => !item.removed)" :key="target.id"
                       :label="`${target.targetType === 'PHYSICAL_SUBSYSTEM' ? '物理子系统' : '部署单元'}：${target.targetName}`" :value="target.targetId" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查项" required>
          <el-input v-model="taskForm.checkItemNames" type="textarea" :rows="4" placeholder="每行一个检查项名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <!-- 任务抽屉 -->
    <el-drawer v-model="taskDrawerVisible" size="min(760px, 96vw)" :title="currentTask ? `任务 · ${currentTask.name}` : ''" destroy-on-close>
      <template v-if="currentTask">
        <el-descriptions :column="2" border size="small" class="plan-task-desc">
          <el-descriptions-item label="状态">
            <UiStatusTag :value="currentTask.status" :labels="taskStatusLabels" :tone="taskStatusTones[currentTask.status]" />
            <el-tag v-if="currentTask.waivedAll" size="small" type="info">全部豁免</el-tag>
            <el-tag v-if="currentTask.overdue" size="small" type="danger">逾期</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="目标">{{ currentTask.targetName ?? '计划级' }}</el-descriptions-item>
          <el-descriptions-item label="进度">{{ currentTask.progress ?? 0 }}%</el-descriptions-item>
          <el-descriptions-item label="责任人">{{ currentTask.ownerUserId }}</el-descriptions-item>
          <el-descriptions-item label="计划时间">{{ currentTask.plannedStart ?? '—' }} ~ {{ currentTask.plannedEnd ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="实际时间">{{ currentTask.actualStart ?? '—' }} ~ {{ currentTask.actualEnd ?? '—' }}</el-descriptions-item>
        </el-descriptions>

        <div class="plan-detail-section-title">
          <span>检查项</span>
          <span>
            <el-button v-if="canManage && !currentTask.cancelled" link type="primary" @click="addCheckItemVisible = true; addCheckItemName = ''"><el-icon><Plus /></el-icon>新增检查项</el-button>
            <el-button v-if="isTaskExecutor(currentTask) && currentTask.status !== 'NOT_STARTED' && currentTask.status !== 'COMPLETED' && currentTask.status !== 'CANCELLED'" link type="primary" @click="openDependency">前置依赖</el-button>
            <el-button v-if="isTaskExecutor(currentTask) && currentTask.status !== 'COMPLETED' && currentTask.status !== 'CANCELLED'" link type="primary" @click="openBlock">登记阻塞</el-button>
            <el-button v-if="isTaskExecutor(currentTask)" link type="primary" @click="openWorkOrder">关联工单</el-button>
            <el-button v-if="isTaskExecutor(currentTask) || isPlanOwner()" link type="primary" @click="openTaskSchedule">任务时间</el-button>
          </span>
        </div>

        <div class="plan-detail-checks">
          <article v-for="(item, index) in currentTask.checkItems" :key="item.id"
                   class="plan-detail-check" :class="{ 'is-completed': item.status === 'COMPLETED', 'is-cancelled': item.cancelled }">
            <header class="plan-detail-check__header">
              <div class="plan-detail-check__title">
                <span class="plan-detail-check__no">检查项 {{ index + 1 }}</span>
                <span class="plan-detail-check__name">{{ item.name }}</span>
              </div>
              <el-tag size="small" :type="item.status === 'COMPLETED' ? 'success' : item.cancelled ? 'info' : 'warning'">
                {{ item.status === 'COMPLETED' ? '已完成' : item.cancelled ? '已取消' : '待完成' }}
              </el-tag>
            </header>
            <p v-if="item.guide" class="plan-detail-check__guide">{{ item.guide }}</p>
            <dl v-if="item.completedBy || item.completedAt || item.cancelReason" class="plan-detail-check__meta">
              <div v-if="item.completedBy"><dt>完成人</dt><dd>{{ userName(item.completedBy) }}</dd></div>
              <div v-if="item.completedAt"><dt>完成时间</dt><dd>{{ item.completedAt?.replace('T', ' ').slice(0, 19) }}</dd></div>
              <div v-if="item.cancelReason"><dt>取消原因</dt><dd>{{ item.cancelReason }}</dd></div>
            </dl>
            <footer class="plan-detail-check__actions">
              <el-button v-if="item.status === 'PENDING' && !item.cancelled && isTaskExecutor(currentTask) && currentTask.status !== 'WAITING_PRECEDING'" link type="success" @click="onCompleteCheckItem(item)">完成</el-button>
              <el-button v-if="item.status === 'PENDING' && !item.cancelled && isTaskExecutor(currentTask) && currentTask.status === 'WAITING_PRECEDING'" link disabled>前置完成后方可完成</el-button>
              <el-button v-if="item.status === 'COMPLETED' && isTaskExecutor(currentTask)" link type="warning" @click="onReopenCheckItem(item)">重新打开</el-button>
              <el-button v-if="item.status === 'PENDING' && !item.cancelled && isPlanOwner()" link type="danger" @click="onCancelCheckItem(item)">取消</el-button>
              <el-button v-if="item.status === 'PENDING' && !item.cancelled && isTaskExecutor(currentTask) && !isPlanOwner()" link type="warning" @click="onSuggestCancel(item)">建议取消</el-button>
              <el-button v-if="item.cancelled && isPlanOwner()" link type="primary" @click="onRestoreCheckItem(item)">恢复</el-button>
              <el-button v-if="item.status === 'PENDING' && !item.cancelled && isPlanOwner() && !currentTask.actualStart" link type="danger" @click="onDeleteCheckItem(item)">删除</el-button>
              <span v-if="!(['PENDING','COMPLETED'].includes(item.status) || item.cancelled)" class="plan-detail-check__none">—</span>
            </footer>
          </article>
        </div>

        <div v-if="currentTask.dependencies.length > 0" class="plan-task-block">
          <div class="plan-detail-section-title"><span>前置依赖</span></div>
          <div class="plan-dep-list">
            <div v-for="dep in currentTask.dependencies" :key="dep.id" class="plan-dep-row" :class="{ 'is-removed': dep.removed }">
              <div class="plan-dep-row__main" @click="dep.removed ? undefined : jumpToTask(dep.predecessorId)">
                <span class="plan-dep-row__name">#{{ dep.predecessorId }} {{ dependencyTaskName(dep.predecessorId) }}</span>
                <span v-if="dependencyTaskRef(dep.predecessorId)" class="plan-dep-row__stage">{{ dependencyTaskRef(dep.predecessorId)!.stageName }}</span>
                <span v-if="dependencyTaskRef(dep.predecessorId)" class="plan-dep-row__status">
                  <UiStatusTag :value="dependencyTaskRef(dep.predecessorId)!.status" :labels="taskStatusLabels" :tone="taskStatusTones[dependencyTaskRef(dep.predecessorId)!.status as TaskStatus]" />
                </span>
                <span v-else-if="dep.removed" class="plan-dep-row__muted">已移除</span>
              </div>
              <span class="plan-dep-row__actions">
                <el-button v-if="!dep.removed" link type="primary" @click="jumpToTask(dep.predecessorId)">查看</el-button>
                <el-button v-if="!dep.removed" link type="danger" @click="onRemoveDependency(dep.id)">移除</el-button>
              </span>
            </div>
          </div>
        </div>

        <div v-if="currentTask.blocks.length > 0" class="plan-task-block">
          <div class="plan-detail-section-title"><span>阻塞记录</span></div>
          <div v-for="block in currentTask.blocks" :key="block.id" class="plan-block-row">
            <span>{{ block.description }}<em v-if="block.impact">（影响：{{ block.impact }}）</em></span>
            <el-tag size="small" :type="block.resolved ? 'success' : 'danger'">{{ block.resolved ? '已解除' : '未解除' }}</el-tag>
            <el-button v-if="!block.resolved && isTaskExecutor(currentTask)" link type="primary" @click="onResolveBlock(block)">解除</el-button>
          </div>
        </div>

        <div v-if="currentTask.workOrders.length > 0" class="plan-task-block">
          <div class="plan-detail-section-title"><span>关联工单</span></div>
          <div v-for="link in currentTask.workOrders" :key="link.id" class="plan-work-order-row">
            <span>{{ workOrderTypeLabels[link.workOrderType] }} #{{ link.workOrderId }}（{{ link.source === 'CREATED_FROM_TASK' ? '任务发起' : '事后关联' }}）</span>
            <el-button v-if="isTaskExecutor(currentTask) || isPlanOwner()" link type="danger" @click="onDetachWorkOrder(link.id)">解除</el-button>
          </div>
        </div>

        <div class="plan-detail-section-title"><span>执行事件</span></div>
        <el-table :data="currentTask.events" size="small">
          <el-table-column prop="occurredAt" label="时间" width="170" />
          <el-table-column prop="eventType" label="事件" width="110" />
          <el-table-column prop="operatorUserId" label="操作人" width="90" />
          <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>

    <!-- 依赖 -->
    <el-dialog v-model="depVisible" title="前置依赖" width="520px">
      <p class="plan-reason-tip">前置任务未完成时，后续任务不能开始并显示「等待前置」。取消的前置任务不视为完成。</p>
      <el-select v-model="depSelected" multiple filterable style="width: 100%" placeholder="选择同计划内的前置任务">
        <el-option v-for="option in depOptions" :key="option.id" :label="option.name" :value="option.id" :disabled="option.disabled" />
      </el-select>
      <template #footer>
        <el-button @click="depVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDependency">保存</el-button>
      </template>
    </el-dialog>

    <!-- 阻塞 -->
    <el-dialog v-model="blockVisible" title="登记阻塞" width="520px">
      <el-form label-width="90px">
        <el-form-item label="描述" required><el-input v-model="blockForm.description" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
        <el-form-item label="影响范围"><el-input v-model="blockForm.impact" maxlength="2000" /></el-form-item>
        <el-form-item label="预计解决"><el-date-picker v-model="blockForm.expectedResolveAt" type="datetime" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="blockVisible = false">取消</el-button>
        <el-button type="primary" @click="saveBlock">保存</el-button>
      </template>
    </el-dialog>

    <!-- 工单关联 -->
    <el-dialog v-model="workOrderVisible" title="关联工单" width="500px">
      <el-form label-width="90px">
        <el-form-item label="工单类型">
          <el-select v-model="workOrderForm.type">
            <el-option v-for="(label, key) in workOrderTypeLabels" :key="key" :label="label" :value="key" :disabled="key === 'CRYPTO_POOL'" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单 ID" required><el-input v-model="workOrderForm.workOrderIds" placeholder="多个用逗号分隔" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="workOrderForm.reason" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="workOrderVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWorkOrder">关联</el-button>
      </template>
    </el-dialog>

    <!-- 任务时间 -->
    <el-dialog v-model="taskScheduleVisible" title="任务时间" width="480px">
      <el-form label-width="100px">
        <el-form-item label="计划开始"><el-date-picker v-model="taskScheduleForm.plannedStart" type="datetime" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="计划结束"><el-date-picker v-model="taskScheduleForm.plannedEnd" type="datetime" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="调整原因"><el-input v-model="taskScheduleForm.reason" placeholder="任务开始后调整必须填写原因" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskScheduleVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTaskSchedule">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增检查项 -->
    <el-dialog v-model="addCheckItemVisible" title="新增检查项" width="420px">
      <el-input v-model="addCheckItemName" placeholder="检查项名称" maxlength="500" />
      <template #footer>
        <el-button @click="addCheckItemVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCheckItem">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.plan-summary-panel {
  margin-bottom: 14px;
  overflow: hidden;
  background: var(--panel-bg);
  border: 1px solid var(--line);
  border-radius: 6px;
}
.plan-summary-panel__header {
  display: flex;
  min-height: 56px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--line);
}
.plan-summary-panel__header h3 {
  margin: 4px 0 0;
  font-size: 16px;
}
.plan-summary-panel__tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
}
.plan-summary-context {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
}
.plan-summary-context > div {
  display: grid;
  min-width: 0;
  gap: 5px;
  padding: 14px 18px;
  border-right: 1px solid var(--line);
}
.plan-summary-context > div:last-child {
  border-right: 0;
}
.plan-summary-context small {
  color: var(--muted);
  font-size: 11px;
}
.plan-summary-context strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}
.plan-summary-context__progress {
  grid-template-columns: 1fr auto;
  align-items: end;
  column-gap: 10px;
}
.plan-summary-context__progress small {
  grid-column: 1 / -1;
}
.plan-summary-context__progress el-progress {
  grid-column: 1 / -1;
}
.plan-summary-context__progress .el-progress {
  grid-column: 1 / -1;
}
.plan-summary-context__reason strong {
  white-space: normal;
}
.plan-detail-targets {
  margin-bottom: 14px;
}
.plan-detail-section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin: 10px 0 8px;
}
.plan-detail-targets__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.plan-exec-stage-header {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 92%;
  min-width: 0;
}
.plan-exec-stage-header__name {
  font-weight: 600;
  white-space: nowrap;
}
.plan-exec-stage-header__percent {
  color: var(--muted);
  font-size: 12px;
}
.plan-exec-stage-header__count {
  margin-left: auto;
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}
.plan-exec-task-collapse {
  margin-top: 6px;
}
.plan-exec-task-header {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}
.plan-exec-task-header__name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan-exec-task-header__target {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}
.plan-exec-task-header__progress {
  margin-left: auto;
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}
.plan-exec-task-body {
  padding: 4px 2px 6px;
}
.plan-exec-task-body__checks {
  display: grid;
  gap: 8px;
}
.plan-exec-check {
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 8px 10px;
  background: var(--page-bg);
}
.plan-exec-check.is-completed {
  background: var(--panel-bg);
}
.plan-exec-check.is-cancelled {
  opacity: 0.7;
}
.plan-exec-check__main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.plan-exec-check__no {
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
  flex: 0 0 auto;
}
.plan-exec-check__name {
  font-size: 13px;
  overflow-wrap: anywhere;
  flex: 1 1 auto;
  min-width: 0;
}
.plan-exec-check__guide {
  margin: 4px 0 0;
  padding-left: 20px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.plan-exec-task-body__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 10px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed var(--line);
}
.plan-exec-task-body__actions .el-button {
  margin-left: 0;
}
.plan-stage-actions {
  margin-bottom: 8px;
}





.plan-suggestion-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px;
  background: var(--panel-bg);
  border: 1px solid var(--line);
  border-radius: 6px;
  margin-bottom: 12px;
}
.plan-flow-canvas {
  height: 520px;
  min-height: 360px;
}
/* 执行视图 = 左画布 + 右列表（与模板编辑页同款双栏布局） */
.plan-exec-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 14px;
  align-items: start;
}
.plan-exec-editor__canvas {
  min-width: 0;
}
.plan-exec-editor__flow {
  height: clamp(560px, calc(100dvh - 300px), 860px);
  min-height: 480px;
}
.plan-exec-editor__panel {
  min-width: 0;
  max-height: clamp(560px, calc(100dvh - 300px), 860px);
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--page-bg);
  padding: 12px;
}
.plan-exec-task-list {
  display: grid;
  gap: 10px;
}
.plan-exec-task {
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--panel-bg);
  padding: 8px 10px 10px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.plan-exec-task.is-linked {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--brand) 18%, transparent);
}
.plan-exec-stage-header.is-linked .plan-exec-stage-header__name {
  color: var(--brand);
}
/* 任务节点嵌套在环节容器内，连线路径位于容器背景区域：将边层提升到节点之上 */
.plan-flow-canvas :deep(.vue-flow__edges) {
  z-index: 3;
}
.plan-flow-canvas :deep(.vue-flow__transformationpane) {
  z-index: 2;
}
.plan-flow-canvas__legend {
  position: absolute;
  left: 12px;
  bottom: 10px;
  z-index: 5;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 6px 10px;
  background: var(--panel-bg);
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--muted);
  font-size: 12px;
}
.plan-board {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.plan-board__stage {
  background: var(--panel-bg);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px;
}
.plan-board__stage-header {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 8px;
}
.plan-board__task {
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 8px;
  margin-bottom: 8px;
}
.plan-board__task-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
  font-size: 12px;
  color: var(--muted);
}
.plan-report-tip {
  color: var(--muted);
  font-size: 13px;
}
.plan-detail-checks {
  display: grid;
  gap: 10px;
}
.plan-detail-check {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px 8px;
  background: var(--page-bg);
}
.plan-detail-check.is-completed {
  background: var(--panel-bg);
}
.plan-detail-check.is-cancelled {
  opacity: 0.72;
}
.plan-detail-check__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.plan-detail-check__title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.plan-detail-check__no {
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.plan-detail-check__name {
  font-weight: 500;
  overflow-wrap: anywhere;
}
.plan-detail-check__guide {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.plan-detail-check__meta {
  display: grid;
  gap: 4px;
  margin: 6px 0 0;
}
.plan-detail-check__meta div {
  display: flex;
  gap: 10px;
  font-size: 12px;
}
.plan-detail-check__meta dt {
  color: var(--muted);
}
.plan-detail-check__meta dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.plan-detail-check__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 10px;
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--line);
}
.plan-detail-check__actions .el-button {
  margin-left: 0;
}
.plan-detail-check__none {
  color: var(--muted);
}
.plan-check-guide {
  color: var(--muted);
  font-size: 12px;
  white-space: pre-line;
}
.plan-reason-tip {
  color: var(--muted);
  font-size: 13px;
  margin: 0 0 10px;
}
.plan-task-block {
  margin-top: 8px;
}
.plan-dep-list {
  display: grid;
  gap: 6px;
}
.plan-dep-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--page-bg);
}
.plan-dep-row.is-removed {
  opacity: 0.6;
}
.plan-dep-row__main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  cursor: pointer;
}
.plan-dep-row__name {
  color: var(--brand);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan-dep-row__stage {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}
.plan-dep-row__muted {
  color: var(--muted);
  font-size: 12px;
}
.plan-dep-row__actions {
  display: inline-flex;
  gap: 4px;
  flex: 0 0 auto;
}
.plan-dep-row__actions .el-button {
  margin-left: 0;
}
.plan-block-row,
.plan-work-order-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 13px;
}
@media (max-width: 1080px) {
  .plan-exec-editor {
    grid-template-columns: 1fr;
  }
  .plan-exec-editor__panel {
    max-height: none;
    overflow: visible;
  }
  .plan-exec-editor__flow {
    height: 440px;
    min-height: 0;
  }
}
@media (max-width: 760px) {
  .plan-summary-context {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .plan-summary-context > div {
    padding: 12px 14px;
  }
  .plan-summary-context > div:nth-child(even) {
    border-right: 0;
  }
  .plan-summary-panel__header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .plan-board {
    grid-template-columns: 1fr;
  }
}
</style>
