<script setup lang="ts">
import { computed, markRaw, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MarkerType, useVueFlow, VueFlow, type Connection, type Edge, type EdgeMouseEvent, type Node, type NodeMouseEvent, type NodeTypesObject } from '@vue-flow/core'
import dagre from '@dagrejs/dagre'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import PlanTemplateStageFlowNode from './PlanTemplateStageFlowNode.vue'
import PlanTemplateTaskFlowNode from './PlanTemplateTaskFlowNode.vue'
import { ArrowLeft, Delete, FullScreen, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  addPlanTemplateStage,
  addTaskTemplate,
  changePlanTemplateStatus,
  deletePlanTemplateStage,
  deleteTaskTemplate,
  getPlanTemplate,
  publishPlanTemplate,
  setPlanTemplateStageDependencies,
  setTaskTemplateDependencies as setTaskTemplateDependenciesApi,
  updatePlanTemplateStage,
  updateTaskTemplate
} from './planApi'
import type {
  CheckItemDraftView,
  PlanDimension,
  PlanTemplateDetail,
  StageView,
  TaskTemplateView
} from './planApi'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const templateId = Number(route.params.id)
const canManage = computed(() => auth.hasPermission('architecture:plan-template:manage') || auth.hasPermission('architecture:manage'))

// ---------- 数据加载 ----------
const structure = ref<PlanTemplateDetail | null>(null)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)

async function reloadStructure() {
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    structure.value = await getPlanTemplate(templateId)
    buildFlowGraph()
  } catch (error) {
    const message = apiErrorMessage(error, '加载失败')
    if (/403|权限/.test(message)) {
      forbidden.value = true
    } else {
      loadError.value = message
    }
  } finally {
    loading.value = false
  }
}

onMounted(reloadStructure)

const dimensionLabels: Record<PlanDimension, string> = { NONE: '不展开', PHYSICAL_SUBSYSTEM: '按物理子系统', DEPLOYMENT_UNIT: '按部署单元' }

// ---------- 流程图（左侧画布）----------
const STAGE_W = 460
const STAGE_HEADER_H = 92
const TASK_W = 150
const TASK_H = 68
const TASK_COLS = 2
const TASK_GAP_X = 50
const TASK_GAP_Y = 16
const TASK_ROW_GAP = 60

const flowNodes = ref<Node[]>([])
const flowEdges = ref<Edge[]>([])
const nodeTypes: NodeTypesObject = { planStage: markRaw(PlanTemplateStageFlowNode), planTask: markRaw(PlanTemplateTaskFlowNode) }
const flowReady = ref(false)
const activeStageId = ref<number | null>(null)
const activeTaskId = ref<number | null>(null)
const nodeBounds = new Map<string, { x: number; y: number; w: number; h: number }>()
const { setCenter, fitView } = useVueFlow()
const layoutDirection = ref<'TB' | 'LR'>('TB')
const editorFullscreen = ref(false)

function toggleFullscreen() {
  editorFullscreen.value = !editorFullscreen.value
  void nextTick(() => {
    window.setTimeout(() => void fitView({ padding: 0.15 }), 120)
  })
}

async function deleteSelectedNode() {
  if (activeTaskId.value != null) {
    const ref = taskRef(activeTaskId.value)
    if (ref) await removeTask(ref.task)
    return
  }
  if (activeStageId.value != null) {
    const stage = structure.value?.stages.find(item => item.id === activeStageId.value)
    if (stage) await removeStage(stage)
  }
}

function addTaskToActiveStage() {
  const stage = structure.value?.stages.find(item => item.id === activeStageId.value)
  if (!stage) {
    ElMessage.warning('请先在画布或右侧列表中选择一个环节')
    return
  }
  openTaskEdit(stage, null)
}

function stageBounds(stage: StageView) {
  const rows = stage.tasks.length === 0 ? 1 : Math.ceil(stage.tasks.length / TASK_COLS)
  const h = STAGE_HEADER_H + TASK_GAP_Y + rows * TASK_H + (rows - 1) * TASK_ROW_GAP + 14
  return { w: STAGE_W, h }
}

/** 用 dagre 对环节做自动布局，返回环节 id -> 坐标（左上角） */
function layoutStages(data: PlanTemplateDetail): Map<string, { x: number; y: number }> {
  const graph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}))
  const compact = document.documentElement.clientWidth <= 1080
  graph.setGraph({
    rankdir: layoutDirection.value,
    nodesep: compact ? 24 : 44,
    ranksep: layoutDirection.value === 'TB' ? (compact ? 46 : 96) : (compact ? 90 : 200),
    marginx: compact ? 24 : 48,
    marginy: compact ? 24 : 48
  })
  for (const stage of data.stages) {
    const size = stageBounds(stage)
    graph.setNode('s' + stage.id, { width: size.w, height: size.h })
  }
  for (const pair of data.stageDependencies) {
    graph.setEdge('s' + pair[1], 's' + pair[0])
  }
  dagre.layout(graph)
  const positions = new Map<string, { x: number; y: number }>()
  for (const stage of data.stages) {
    const id = 's' + stage.id
    const size = stageBounds(stage)
    const pos = graph.node(id)
    positions.set(id, { x: pos.x - size.w / 2, y: pos.y - size.h / 2 })
  }
  return positions
}

function buildFlowGraph() {
  const data = structure.value
  if (!data) return
  const nodes: Node[] = []
  const edges: Edge[] = []
  nodeBounds.clear()
  const stagePositions = layoutStages(data)
  for (const stage of data.stages) {
    const stageId = 's' + stage.id
    const size = stageBounds(stage)
    const origin = stagePositions.get(stageId) ?? { x: 48, y: 24 }
    nodes.push({
      id: stageId,
      type: 'planStage',
      position: { x: origin.x, y: origin.y },
      data: { stage, selected: stage.id === activeStageId.value, containerHeight: size.h }
    })
    nodeBounds.set(stageId, { x: origin.x, y: origin.y, w: size.w, h: size.h })
    stage.tasks.forEach((task, index) => {
      const taskId = 't' + task.id
      const col = index % TASK_COLS
      const row = Math.floor(index / TASK_COLS)
      const px = origin.x + TASK_GAP_X + col * (TASK_W + TASK_GAP_X - 4)
      const py = origin.y + STAGE_HEADER_H + TASK_GAP_Y + row * (TASK_H + TASK_ROW_GAP)
      nodes.push({
        id: taskId,
        type: 'planTask',
        parentNode: stageId,
        position: { x: px - origin.x, y: py - origin.y },
        data: { task, selected: task.id === activeTaskId.value }
      })
      nodeBounds.set(taskId, { x: px, y: py, w: TASK_W, h: TASK_H })
    })
  }
  for (const pair of data.stageDependencies) {
    const source = 's' + pair[1]
    const target = 's' + pair[0]
    edges.push({
      id: 'sd-' + pair[1] + '-' + pair[0],
      type: 'smoothstep',
      source,
      target,
      markerEnd: { type: MarkerType.ArrowClosed },
      style: { stroke: 'var(--brand)' }
    })
  }
  for (const pair of data.taskDependencies) {
    const source = 't' + pair[1]
    const target = 't' + pair[0]
    edges.push({
      id: 'td-' + pair[1] + '-' + pair[0],
      type: 'smoothstep',
      source,
      target,
      markerEnd: { type: MarkerType.ArrowClosed },
      style: { stroke: 'var(--muted)' }
    })
  }
  flowNodes.value = nodes
  flowEdges.value = edges
  if (flowReady.value) {
    void nextTick(() => {
      window.setTimeout(() => void fitView({ padding: 0.15 }), 120)
    })
  }
}

function rearrangeLayout() {
  buildFlowGraph()
}

function markSelection() {
  const next: Node[] = []
  for (const node of flowNodes.value as Node[]) {
    const isStage = node.id.startsWith('s')
    const id = Number(node.id.slice(1))
    const selected = isStage ? id === activeStageId.value : id === activeTaskId.value
    next.push({ ...node, data: { ...node.data, selected } })
  }
  flowNodes.value = next
}

function selectNode(nodeId: string | null) {
  activeTaskId.value = null
  activeStageId.value = null
  if (nodeId) {
    if (nodeId.startsWith('s')) activeStageId.value = Number(nodeId.slice(1))
    else activeTaskId.value = Number(nodeId.slice(1))
  }
  markSelection()
  scrollPanelToSelection()
}

function scrollPanelToSelection() {
  void nextTick(() => {
    const panel = document.querySelector('.plan-template-editor__panel')
    if (!panel) return
    const selector = activeTaskId.value != null ? `[data-task-id="${activeTaskId.value}"]` : activeStageId.value != null ? `[data-stage-id="${activeStageId.value}"]` : null
    if (!selector) return
    const target = panel.querySelector(selector)
    target?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
}

function onNodeClick(event: NodeMouseEvent) {
  selectNode(event.node.id)
}

function focusNode(nodeId: string) {
  const bounds = nodeBounds.get(nodeId)
  if (!bounds) return
  void setCenter(bounds.x + bounds.w / 2, bounds.y + bounds.h / 2, { zoom: 1.15, duration: 260 })
}

function focusStage(stageId: number) {
  selectNode('s' + stageId)
  focusNode('s' + stageId)
}

function focusTask(stageId: number, taskId: number) {
  selectNode('t' + taskId)
  void nextTick(() => focusNode('t' + taskId))
}

// ---------- 连线创建/删除 ----------
async function onFlowConnect(connection: Connection) {
  if (!structure.value || !connection.source || !connection.target || connection.source === connection.target) return
  const isStageEdge = connection.source.startsWith('s') && connection.target.startsWith('s')
  const isTaskEdge = connection.source.startsWith('t') && connection.target.startsWith('t')
  if (!isStageEdge && !isTaskEdge) {
    ElMessage.warning('仅支持环节连接环节、任务连接任务')
    return
  }
  if (isTaskEdge) {
    const sourceNode = (flowNodes.value as Node[]).find(node => node.id === connection.source)
    const targetNode = (flowNodes.value as Node[]).find(node => node.id === connection.target)
    if (!sourceNode || !targetNode || sourceNode.parentNode !== targetNode.parentNode) {
      ElMessage.warning('任务模板依赖必须位于同一环节内')
      return
    }
  }
  const sourceId = Number(connection.source.slice(1))
  const targetId = Number(connection.target.slice(1))
  try {
    if (isStageEdge) {
      const current = structure.value.stageDependencies.filter(pair => pair[0] === targetId).map(pair => pair[1])
      if (current.includes(sourceId)) {
        ElMessage.warning('该依赖已存在')
        return
      }
      await setPlanTemplateStageDependencies(targetId, [...current, sourceId])
      ElMessage.success('环节依赖已建立')
    } else {
      const current = structure.value.taskDependencies.filter(pair => pair[0] === targetId).map(pair => pair[1])
      if (current.includes(sourceId)) {
        ElMessage.warning('该依赖已存在')
        return
      }
      await setTaskTemplateDependenciesApi(targetId, [...current, sourceId])
      ElMessage.success('任务模板依赖已建立')
    }
    await reloadStructure()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '操作失败'))
  }
}

async function removeFlowDependency(event: EdgeMouseEvent) {
  if (!structure.value || !canManage.value) return
  const edge = event.edge
  const isStageEdge = edge.id.startsWith('sd-')
  const targetId = Number(String(edge.target).slice(1))
  const sourceId = Number(String(edge.source).slice(1))
  const kind = isStageEdge ? '环节依赖' : '任务模板依赖'
  try {
    await ElMessageBox.confirm(`确认删除该${kind}？删除后生成计划时将不再按此约束排序。`, '删除依赖', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, '操作失败'))
    return
  }
  try {
    if (isStageEdge) {
      const current = structure.value.stageDependencies.filter(pair => pair[0] === targetId).map(pair => pair[1])
      await setPlanTemplateStageDependencies(targetId, current.filter(id => id !== sourceId))
    } else {
      const current = structure.value.taskDependencies.filter(pair => pair[0] === targetId).map(pair => pair[1])
      await setTaskTemplateDependenciesApi(targetId, current.filter(id => id !== sourceId))
    }
    ElMessage.success(kind + '已移除')
    await reloadStructure()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '操作失败'))
  }
}

// ---------- 右侧列表：环节/任务操作 ----------
const stageEdit = reactive<{ visible: boolean; id: number; name: string; startOffsetDays: number | null; durationDays: number | null; predecessorIds: number[] }>({
  visible: false, id: 0, name: '', startOffsetDays: null, durationDays: null, predecessorIds: []
})

function openStageEdit(stage: StageView | null) {
  stageEdit.id = stage?.id ?? 0
  stageEdit.name = stage?.name ?? ''
  stageEdit.startOffsetDays = stage?.startOffsetDays ?? null
  stageEdit.durationDays = stage?.durationDays ?? null
  stageEdit.predecessorIds = stage ? (stagePredecessors.value.get(stage.id) ?? []) : []
  stageEdit.visible = true
}

async function saveStage() {
  if (!stageEdit.name.trim()) {
    ElMessage.warning('请输入环节名称')
    return
  }
  try {
    let savedStageId = stageEdit.id
    if (stageEdit.id === 0) {
      const created = await addPlanTemplateStage(templateId, { name: stageEdit.name.trim(), startOffsetDays: stageEdit.startOffsetDays, durationDays: stageEdit.durationDays })
      savedStageId = created.id
    } else {
      const current = structure.value?.stages.find(item => item.id === stageEdit.id)
      await updatePlanTemplateStage(stageEdit.id, {
        name: stageEdit.name.trim(),
        sortNo: current?.sortNo,
        startOffsetDays: stageEdit.startOffsetDays,
        durationDays: stageEdit.durationDays
      })
    }
    if (savedStageId > 0) {
      await setPlanTemplateStageDependencies(savedStageId, stageEdit.predecessorIds)
    }
    stageEdit.visible = false
    ElMessage.success(stageEdit.id === 0 ? '环节已添加' : '环节配置已保存')
    await reloadStructure()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '操作失败'))
  }
}

async function removeStage(stage: StageView) {
  try {
    await ElMessageBox.confirm(`删除环节「${stage.name}」将同时删除其任务模板（已发布版本在计划中不受影响），确认删除？`, '删除环节', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, '操作失败'))
    return
  }
  try {
    await deletePlanTemplateStage(stage.id)
    ElMessage.success('环节已删除')
    if (activeStageId.value === stage.id) selectNode(null)
    await reloadStructure()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '操作失败'))
  }
}

const taskEdit = reactive<{ visible: boolean; id: number; stageId: number; name: string; dimension: PlanDimension; checkItems: CheckItemDraftView[]; rowVersion: number | null; predecessorIds: number[] }>({
  visible: false, id: 0, stageId: 0, name: '', dimension: 'NONE', checkItems: [], rowVersion: null, predecessorIds: []
})

function openTaskEdit(stage: StageView, task: TaskTemplateView | null) {
  taskEdit.id = task?.id ?? 0
  taskEdit.stageId = stage.id
  taskEdit.name = task?.name ?? ''
  taskEdit.dimension = task?.dimension ?? 'NONE'
  taskEdit.checkItems = task ? task.checkItems.map(item => ({ ...item })) : [{ name: '', sortNo: 1, guide: null }]
  taskEdit.rowVersion = task?.rowVersion ?? 0
  taskEdit.predecessorIds = task ? (taskPredecessors.value.get(task.id) ?? []) : []
  taskEdit.visible = true
}

function addCheckItemRow() {
  taskEdit.checkItems.push({ name: '', sortNo: taskEdit.checkItems.length + 1, guide: null })
}

function removeCheckItemRow(index: number) {
  taskEdit.checkItems.splice(index, 1)
}

async function saveTask() {
  const checkItems = taskEdit.checkItems.filter(item => item.name.trim()).map((item, index) => ({ name: item.name.trim(), sortNo: index + 1, guide: item.guide?.trim() || null }))
  if (!taskEdit.name.trim() || checkItems.length === 0) {
    ElMessage.warning('请填写任务模板名称与至少一个标准检查项')
    return
  }
  try {
    let savedTaskId = taskEdit.id
    if (taskEdit.id === 0) {
      const created = await addTaskTemplate(templateId, { stageId: taskEdit.stageId, name: taskEdit.name.trim(), dimension: taskEdit.dimension, checkItems })
      savedTaskId = created.id
    } else {
      await updateTaskTemplate(taskEdit.id, { name: taskEdit.name.trim(), dimension: taskEdit.dimension, checkItems, rowVersion: taskEdit.rowVersion })
    }
    if (savedTaskId > 0) {
      await setTaskTemplateDependenciesApi(savedTaskId, taskEdit.predecessorIds)
    }
    taskEdit.visible = false
    ElMessage.success('任务模板已保存')
    await reloadStructure()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '操作失败'))
  }
}

async function removeTask(task: TaskTemplateView) {
  try {
    await ElMessageBox.confirm(`删除任务模板「${task.name}」？已发布计划不受影响。`, '删除任务模板', { type: 'warning' })
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(apiErrorMessage(error, '操作失败'))
    return
  }
  try {
    await deleteTaskTemplate(task.id)
    ElMessage.success('任务模板已删除')
    if (activeTaskId.value === task.id) selectNode(null)
    await reloadStructure()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '操作失败'))
  }
}

// ---------- 派生展示 ----------
const stagePredecessors = computed(() => {
  const map = new Map<number, number[]>()
  if (structure.value) {
    for (const pair of structure.value.stageDependencies) {
      const list = map.get(pair[0]) ?? []
      list.push(pair[1])
      map.set(pair[0], list)
    }
  }
  return map
})

const taskPredecessors = computed(() => {
  const map = new Map<number, number[]>()
  if (structure.value) {
    for (const pair of structure.value.taskDependencies) {
      const list = map.get(pair[0]) ?? []
      list.push(pair[1])
      map.set(pair[0], list)
    }
  }
  return map
})

function stageName(stageId: number) {
  return structure.value?.stages.find(stage => stage.id === stageId)?.name ?? `环节${stageId}`
}

function taskRef(taskId: number) {
  for (const stage of structure.value?.stages ?? []) {
    const task = stage.tasks.find(item => item.id === taskId)
    if (task) return { stage, task }
  }
  return null
}

function taskLabel(taskId: number) {
  return taskRef(taskId)?.task.name ?? `任务${taskId}`
}

/** 环节配置弹窗候选：同模板其他环节 */
const stagePredecessorCandidates = computed(() => (structure.value?.stages ?? []).filter(stage => stage.id !== stageEdit.id))

/** 任务编辑弹窗候选：同一环节内其他任务 */
const taskPredecessorCandidates = computed(() => {
  const stage = (structure.value?.stages ?? []).find(item => item.id === taskEdit.stageId)
  return (stage?.tasks ?? []).filter(task => task.id !== taskEdit.id)
})

// ---------- 发布 / 状态 ----------
const publishVisible = ref(false)
const publishSaving = ref(false)
const publishNote = ref('')
const statusSaving = ref(false)

function openPublish() {
  publishNote.value = ''
  publishVisible.value = true
}

async function doPublish() {
  publishSaving.value = true
  try {
    const version = await publishPlanTemplate(templateId, publishNote.value.trim() || null)
    ElMessage.success(`已发布版本 v${version.versionNo}`)
    publishVisible.value = false
    await reloadStructure()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '操作失败'))
  } finally {
    publishSaving.value = false
  }
}

async function toggleStatus() {
  const current = structure.value?.template
  if (!current) return
  statusSaving.value = true
  try {
    await changePlanTemplateStatus(templateId, current.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')
    ElMessage.success(current.status === 'ACTIVE' ? '模板已停用' : '模板已启用')
    await reloadStructure()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '操作失败'))
  } finally {
    statusSaving.value = false
  }
}
</script>

<template>
  <main class="architecture-page plan-editor-page">
    <UiPageHeader :title="structure ? `${structure.template.name} · 结构编辑` : '搭建计划模板编辑'" description="左侧画布配置结构图，右侧列表维护环节与任务模板；两侧实时联动">
      <template #actions>
        <el-button @click="router.push({ name: 'architecture-plan-templates' })"><el-icon><ArrowLeft /></el-icon>返回模板列表</el-button>
        <el-button :loading="loading" @click="reloadStructure"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-tooltip :content="structure?.template.status === 'DRAFT' ? '草稿模板未启用，可修改任何结构' : '已启用/停用模板可继续修改草稿结构，发布后生成新快照'">
          <span>
            <el-button v-if="canManage" :disabled="!structure || structure.template.status === 'DRAFT'" @click="toggleStatus" :loading="statusSaving">
              {{ structure?.template.status === 'ACTIVE' ? '停用模板' : '启用模板' }}
            </el-button>
          </span>
        </el-tooltip>
        <el-button v-if="canManage" type="primary" :disabled="!structure" @click="openPublish">发布新版本</el-button>
      </template>
    </UiPageHeader>

    <section v-if="loading && !structure" v-loading="true" class="architecture-state-panel" aria-label="正在加载模板结构" />
    <section v-else-if="forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无模板查看权限" sub-title="请申请 architecture:plan-template:view 或 manage 权限后访问。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="模板加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="reloadStructure">重新加载</el-button></template>
      </el-result>
    </section>

    <template v-else-if="structure">
      <div class="plan-template-editor plan-editor-grid" :class="{ 'is-fullscreen': editorFullscreen }" v-loading="loading">
        <!-- 左：流程图画布 -->
        <section class="plan-template-editor__canvas" aria-label="模板结构流程图">
          <div class="workflow-designer__toolbar plan-template-editor__toolbar">
            <div class="workflow-designer__tools">
              <el-button v-if="canManage" size="small" @click="openStageEdit(null)"><el-icon><Plus /></el-icon>添加环节</el-button>
              <el-button v-if="canManage" size="small" :disabled="activeStageId == null" @click="addTaskToActiveStage"><el-icon><Plus /></el-icon>任务模板（当前环节）</el-button>
            </div>
            <div class="workflow-designer__tools">
              <el-radio-group v-model="layoutDirection" size="small" @change="rearrangeLayout">
                <el-radio-button value="TB">纵向</el-radio-button>
                <el-radio-button value="LR">横向</el-radio-button>
              </el-radio-group>
              <el-button size="small" text @click="rearrangeLayout"><el-icon><Refresh /></el-icon>整理布局</el-button>
              <el-button v-if="canManage" size="small" text type="danger" :disabled="activeStageId == null && activeTaskId == null" @click="deleteSelectedNode"><el-icon><Delete /></el-icon>删除选中</el-button>
              <el-button size="small" text @click="toggleFullscreen"><el-icon><FullScreen /></el-icon>全屏绘制</el-button>
            </div>
          </div>
          <div class="workflow-designer__canvas plan-template-editor__flow">
            <VueFlow :nodes="flowNodes" :edges="flowEdges" :node-types="nodeTypes"
                     :nodes-draggable="false" :nodes-connectable="canManage" :edges-updatable="false" :elements-selectable="canManage"
                     :min-zoom="0.35" :max-zoom="1.6" fit-view-on-init @init="flowReady = true"
                     @connect="onFlowConnect" @node-click="onNodeClick" @edge-click="removeFlowDependency" />
            <div class="plan-template-editor__legend">
              <span>■ 环节依赖（品牌色）/ 任务依赖（灰色）</span>
              <span v-if="canManage">拖拽右侧圆点到目标左侧圆点建立「前置 → 后续」；点击连线删除；点击节点定位到右侧列表</span>
              <span v-else>只读模式：连线与结构仅可查看</span>
            </div>
          </div>
        </section>

        <!-- 右：结构列表 -->
        <section class="plan-template-editor__panel" aria-label="模板结构列表">
          <el-empty v-if="structure.stages.length === 0" description="暂无环节，请先添加环节">
            <el-button v-if="canManage" type="primary" @click="openStageEdit(null)">添加环节</el-button>
          </el-empty>
          <div v-else class="plan-template-editor__list">
            <article v-for="(stage, stageIndex) in structure.stages" :key="stage.id"
                     class="plan-editor-stage" :class="{ 'is-active': stage.id === activeStageId }"
                     :data-stage-id="stage.id" @click="focusStage(stage.id)">
              <header class="plan-editor-stage__header">
                <div class="plan-editor-stage__title">
                  <span class="plan-editor-stage__no">{{ stageIndex + 1 }}</span>
                  <strong>{{ stage.name }}</strong>
                </div>
                <div class="plan-editor-stage__tools">
                  <el-button v-if="canManage" link type="primary" @click.stop="openStageEdit(stage)">配置</el-button>
                  <el-button v-if="canManage" link type="danger" @click.stop="removeStage(stage)">删除</el-button>
                </div>
              </header>
              <div class="plan-editor-stage__meta">
                <span>时间：{{
                  stage.startOffsetDays == null && stage.durationDays == null ? '未配置'
                    : `第 ${stage.startOffsetDays ?? 0} 天起 · 持续 ${stage.durationDays ?? '—'} 天`
                }}</span>
                <span v-if="stagePredecessors.has(stage.id)" class="plan-editor-stage__deps">
                  前置：
                  <el-tag v-for="pid in stagePredecessors.get(stage.id) || []" :key="pid" size="small" effect="plain"
                          @click.stop="focusStage(pid)">{{ stageName(pid) }}</el-tag>
                </span>
              </div>
              <ul class="plan-editor-stage__tasks">
                <li v-for="task in stage.tasks" :key="task.id"
                    class="plan-editor-task" :class="{ 'is-active': task.id === activeTaskId }"
                    :data-task-id="task.id" @click.stop="focusTask(stage.id, task.id)">
                  <div class="plan-editor-task__main">
                    <strong>{{ task.name }}</strong>
                    <small>{{ dimensionLabels[task.dimension] }} · {{ task.checkItems.length }} 个检查项</small>
                    <span v-if="taskPredecessors.has(task.id)" class="plan-editor-task__deps">
                      前置：
                      <el-tag v-for="pid in taskPredecessors.get(task.id) || []" :key="pid" size="small" effect="plain"
                              @click.stop="(() => { const ref = taskRef(pid); if (ref) focusTask(ref.stage.id, pid) })()">
                        {{ taskLabel(pid) }}
                      </el-tag>
                    </span>
                  </div>
                  <div class="plan-editor-task__tools">
                    <el-button v-if="canManage" link type="primary" @click.stop="openTaskEdit(stage, task)">编辑</el-button>
                    <el-button v-if="canManage" link type="danger" @click.stop="removeTask(task)">删除</el-button>
                  </div>
                </li>
                <li v-if="stage.tasks.length === 0" class="plan-editor-task plan-editor-task--empty">
                  暂无任务模板{{ canManage ? '，点击下方按钮添加' : '' }}
                </li>
              </ul>
              <el-button v-if="canManage" class="plan-editor-stage__add" plain size="small" @click.stop="openTaskEdit(stage, null)">
                <el-icon><Plus /></el-icon>添加任务模板
              </el-button>
            </article>
            <el-button v-if="canManage" class="plan-template-editor__add-stage" @click="openStageEdit(null)"><el-icon><Plus /></el-icon>添加环节</el-button>
            <p class="plan-template-editor__hint">提示：画布上拖拽建立依赖；任务模板依赖仅限同一环节内；点击连线可删除。</p>
          </div>
        </section>
      </div>

      <!-- 环节配置 -->
      <el-dialog v-model="stageEdit.visible" :title="stageEdit.id === 0 ? '添加环节' : '配置环节'" width="min(520px, 96vw)">
        <el-form label-width="90px">
          <el-form-item label="环节名称" required>
            <el-input v-model="stageEdit.name" placeholder="如：资源下发" maxlength="200" />
          </el-form-item>
          <el-form-item label="开始偏移">
            <el-input-number v-model="stageEdit.startOffsetDays" :min="0" :max="3650" controls-position="right"
                             placeholder="计划开始后天数" style="width: 180px" />
            <span class="plan-editor-field-tip">计划开始后第 N 天（留空=第 0 天）</span>
          </el-form-item>
          <el-form-item label="持续天数">
            <el-input-number v-model="stageEdit.durationDays" :min="1" :max="3650" controls-position="right"
                             placeholder="默认 1 天" style="width: 180px" />
            <span class="plan-editor-field-tip">留空=1 天</span>
          </el-form-item>
          <el-form-item label="前置环节">
            <el-select v-model="stageEdit.predecessorIds" multiple clearable filterable
                       placeholder="选择本环节开始前需完成的环节（可多选）" style="width: 100%">
              <el-option v-for="candidate in stagePredecessorCandidates" :key="candidate.id"
                         :label="candidate.name" :value="candidate.id" />
            </el-select>
            <span class="plan-editor-field-tip">未选择时无前置约束；也可在画布上拖拽连线配置</span>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="stageEdit.visible = false">取消</el-button>
          <el-button type="primary" @click="saveStage">保存</el-button>
        </template>
      </el-dialog>

      <!-- 任务模板编辑 -->
      <el-dialog v-model="taskEdit.visible" :title="taskEdit.id === 0 ? '添加任务模板' : '编辑任务模板'" class="plan-template-dialog">
        <el-form label-width="110px" class="plan-template-dialog__form">
          <el-form-item label="任务名称" required>
            <el-input v-model="taskEdit.name" maxlength="200" placeholder="如：系统部署" />
          </el-form-item>
          <el-form-item label="生成维度" required>
            <el-select v-model="taskEdit.dimension">
              <el-option label="不展开" value="NONE" />
              <el-option label="按物理子系统" value="PHYSICAL_SUBSYSTEM" />
              <el-option label="按部署单元" value="DEPLOYMENT_UNIT" />
            </el-select>
          </el-form-item>
          <el-form-item label="前置任务模板">
            <el-select v-model="taskEdit.predecessorIds" multiple clearable filterable
                       placeholder="选择本任务开始前需完成的任务（仅同一环节，可多选）" style="width: 100%">
              <el-option v-for="candidate in taskPredecessorCandidates" :key="candidate.id"
                         :label="`${candidate.name}${candidate.id === taskEdit.id ? '（当前任务）' : ''}`" :value="candidate.id" />
            </el-select>
            <span class="plan-editor-field-tip">仅同一环节内任务可相互依赖；也可在画布上拖拽连线配置</span>
          </el-form-item>
          <el-form-item label="标准检查项" required class="plan-template-dialog__checks">
            <div class="plan-check-cards">
              <div v-for="(item, index) in taskEdit.checkItems" :key="index" class="plan-check-card">
                <header class="plan-check-card__header">
                  <span class="plan-check-card__no">检查项 {{ index + 1 }}</span>
                  <el-button link type="danger" :disabled="taskEdit.checkItems.length <= 1" aria-label="删除检查项" @click="removeCheckItemRow(index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </header>
                <label class="plan-check-card__field">
                  <span class="plan-check-card__label">检查项名称</span>
                  <el-input v-model="item.name" placeholder="检查项名称" maxlength="500" class="plan-check-card__name" />
                </label>
                <label class="plan-check-card__field">
                  <span class="plan-check-card__label">检查指标及内容</span>
                  <el-input v-model="item.guide" placeholder="检查指标及内容（可选，生成计划时复制），如：检查部署日志是否报错。注：附截图" maxlength="2000"
                            type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" class="plan-check-card__guide" />
                </label>
              </div>
              <el-button link type="primary" class="plan-check-cards__add" @click="addCheckItemRow">
                <el-icon><Plus /></el-icon>添加检查项
              </el-button>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="taskEdit.visible = false">取消</el-button>
          <el-button type="primary" @click="saveTask">保存</el-button>
        </template>
      </el-dialog>

      <!-- 发布 -->
      <el-dialog v-model="publishVisible" title="发布新版本" width="min(460px, 96vw)">
        <p class="plan-template-publish-tip">发布后生成不可变版本快照，后续模板调整不会影响已创建的计划。</p>
        <el-input v-model="publishNote" placeholder="发布说明（可选）" maxlength="1000" />
        <template #footer>
          <el-button @click="publishVisible = false">取消</el-button>
          <el-button type="primary" :loading="publishSaving" @click="doPublish">确认发布</el-button>
        </template>
      </el-dialog>
    </template>
  </main>
</template>

<style scoped>
/* 页面级：单屏高度内对齐，页面不滚动（顶部导航 68 + 路由页签 52 + app-main 内边距 52） */
.plan-editor-page {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - 172px);
  min-height: 380px;
}
.plan-editor-page :deep(.ui-page-header) {
  flex: 0 0 auto;
}
.plan-template-editor {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 14px;
  align-items: stretch;
}
.plan-template-editor.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  padding: 16px;
  background: var(--page-bg);
}
.plan-template-editor__canvas {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.plan-template-editor__toolbar {
  flex: 0 0 auto;
}
.plan-template-editor__flow {
  flex: 1 1 auto;
  height: auto;
  min-height: 0;
}
/* 任务节点嵌套在环节容器内，连线路径位于容器背景区域：
   将边层提升到节点之上，避免容器背景遮挡任务依赖连线 */
.plan-template-editor__flow :deep(.vue-flow__edges) {
  z-index: 3;
}
.plan-template-editor__flow :deep(.vue-flow__transformationpane) {
  z-index: 2;
}
.plan-template-editor__legend {
  position: absolute;
  left: 12px;
  bottom: 10px;
  z-index: 5;
  max-width: calc(100% - 24px);
  padding: 6px 10px;
  background: var(--panel-bg);
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--muted);
  font-size: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}
.plan-template-editor__panel {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--page-bg);
  padding: 12px;
}
.plan-template-editor__list {
  display: grid;
  gap: 12px;
}
.plan-editor-stage {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel-bg);
  padding: 10px 12px 12px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.plan-editor-stage.is-active {
  border-color: var(--brand);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--brand) 18%, transparent);
}
.plan-editor-stage__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.plan-editor-stage__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.plan-editor-stage__title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plan-editor-stage__no {
  flex: 0 0 auto;
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  color: var(--brand);
  background: color-mix(in srgb, var(--brand) 12%, transparent);
  border-radius: 5px;
  font-size: 12px;
  font-weight: 600;
}
.plan-editor-stage__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  color: var(--muted);
  font-size: 12px;
  margin: 6px 0 8px;
}
.plan-editor-stage__deps {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.plan-editor-stage__tasks {
  display: grid;
  gap: 6px;
  list-style: none;
  margin: 0;
  padding: 0;
}
.plan-editor-task {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 8px 10px;
  background: var(--page-bg);
  cursor: pointer;
}
.plan-editor-task.is-active {
  border-color: var(--brand);
  background: color-mix(in srgb, var(--brand) 7%, var(--page-bg));
}
.plan-editor-task--empty {
  color: var(--muted);
  font-size: 12px;
  cursor: default;
}
.plan-editor-task__main {
  min-width: 0;
  display: grid;
  gap: 3px;
}
.plan-editor-task__main strong {
  font-size: 13px;
}
.plan-editor-task__main small {
  color: var(--muted);
  font-size: 12px;
}
.plan-editor-task__deps {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  color: var(--muted);
  font-size: 12px;
}
.plan-editor-task__tools {
  flex: 0 0 auto;
  display: flex;
  gap: 0;
}
.plan-editor-stage__add {
  margin-top: 8px;
}
.plan-template-editor__add-stage {
  width: 100%;
}
.plan-template-editor__hint {
  color: var(--muted);
  font-size: 12px;
  margin: 0;
}
.plan-editor-field-tip {
  margin-left: 8px;
  color: var(--muted);
  font-size: 12px;
}
.plan-template-dialog {
  --el-dialog-width: auto;
  min-width: min(720px, calc(100vw - 24px));
  max-width: calc(100vw - 24px);
}
.plan-template-dialog__form {
  min-width: 0;
}
.plan-check-cards {
  width: 100%;
  min-width: 0;
  display: grid;
  gap: 10px;
}
.plan-check-card {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px 12px;
  background: var(--page-bg);
  display: grid;
  gap: 8px;
}
.plan-check-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.plan-check-card__field {
  display: grid;
  gap: 4px;
  min-width: 0;
}
.plan-check-card__label {
  color: var(--muted);
  font-size: 12px;
}
.plan-check-card__no {
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
}
.plan-check-cards__add {
  justify-self: start;
}
.plan-template-publish-tip {
  color: var(--muted);
  margin: 0 0 12px;
  font-size: 13px;
}
@media (max-width: 1080px) {
  .plan-editor-page {
    height: auto;
  }
  .plan-template-editor {
    grid-template-columns: 1fr;
  }
  .plan-template-editor__panel {
    overflow: visible;
  }
  .plan-template-editor__flow {
    flex: none;
    height: 440px;
  }
}
@media (max-width: 760px) {
  .plan-template-dialog {
    min-width: calc(100vw - 24px);
  }
  .plan-template-dialog__form :deep(.el-form-item__label) {
    display: block;
    float: none;
    width: auto;
    text-align: left;
    margin-bottom: 4px;
  }
  .plan-template-dialog__form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }
  .plan-editor-field-tip {
    display: block;
    margin: 4px 0 0;
  }
}
</style>
