<script setup lang="ts">
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useVueFlow, VueFlow, type Connection, type Edge, type Node, type NodeComponent, type NodeTypesObject, MarkerType, type EdgeMouseEvent, type NodeMouseEvent } from '@vue-flow/core'
import { Close, Delete, EditPen, FullScreen, Plus, Refresh, Share, Switch, TopRight } from '@element-plus/icons-vue'
import dagre from '@dagrejs/dagre'
import '@vue-flow/core/dist/style.css'
import WorkflowNode from './WorkflowNode.vue'
import type { WorkflowEdgeModel, WorkflowGraph, WorkflowNodeConfig, WorkflowNodeModel, WorkflowNodeType } from '../../api/workflow'

const props = defineProps<{ modelValue: WorkflowGraph; readonly?: boolean; nodeStatuses?: Record<string, string> }>()
const emit = defineEmits<{ 'update:modelValue': [value: WorkflowGraph]; select: [node: WorkflowNodeModel | null]; edgeSelect: [edge: WorkflowEdgeModel | null]; fullscreen: [value: boolean] }>()
type DesignerNodeData = { node: WorkflowNodeModel; selected?: boolean; readonly?: boolean; layoutDirection?: LayoutDirection; nodeStatus?: string }
type DesignerNode = Node<DesignerNodeData> & { data: DesignerNodeData }
type DesignerEdge = Edge<{ model: WorkflowEdgeModel }>
type LayoutDirection = 'LR' | 'TB'
const flowNodes = shallowRef<DesignerNode[]>([])
const flowEdges = shallowRef<DesignerEdge[]>([])
const selectedId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const layoutDirection = ref<LayoutDirection>('LR')
const nodeTypes: NodeTypesObject = markRaw({ workflow: WorkflowNode as unknown as NodeComponent })
const syncing = ref(false)
const canvas = ref<HTMLElement | null>(null)
const isFullscreen = ref(false)
const { screenToFlowCoordinate } = useVueFlow()
const contextMenu = reactive({ visible: false, kind: 'node' as 'node' | 'edge', id: '', left: 0, top: 0 })
const contextStyle = computed(() => ({ left: `${contextMenu.left}px`, top: `${contextMenu.top}px` }))

function cloneGraph(graph: WorkflowGraph): WorkflowGraph { return JSON.parse(JSON.stringify(graph)) as WorkflowGraph }
function edgeModel(edge: DesignerEdge): WorkflowEdgeModel { return (edge.data?.model || { id: edge.id, source: edge.source, target: edge.target, sourceHandle: edge.sourceHandle, targetHandle: edge.targetHandle }) as WorkflowEdgeModel }
function loadGraph(graph: WorkflowGraph) {
  syncing.value = true
  const cloned = cloneGraph(graph)
  flowNodes.value = cloned.nodes.map(node => ({ id: node.id, type: 'workflow', position: { ...node.position }, data: { node, selected: node.id === selectedId.value, readonly: Boolean(props.readonly), layoutDirection: layoutDirection.value, nodeStatus: props.nodeStatuses?.[node.id] } }))
  flowEdges.value = cloned.edges.map(edge => ({ id: edge.id, type: 'smoothstep', source: edge.source, target: edge.target, sourceHandle: edge.sourceHandle || (layoutDirection.value === 'TB' ? 'source-bottom' : 'source-right'), targetHandle: edge.targetHandle || (layoutDirection.value === 'TB' ? 'target-top' : 'target-left'), label: edge.label || undefined, markerEnd: MarkerType.ArrowClosed, animated: edge.source.includes('parallel') || edge.target.includes('parallel'), selectable: true, data: { model: { ...edge, sourceHandle: edge.sourceHandle || (layoutDirection.value === 'TB' ? 'source-bottom' : 'source-right'), targetHandle: edge.targetHandle || (layoutDirection.value === 'TB' ? 'target-top' : 'target-left') } } }))
  void nextTick(() => { syncing.value = false })
}
function normalizeEdge(edge: WorkflowEdgeModel): WorkflowEdgeModel {
  const source = flowNodes.value.find(node => node.id === edge.source)?.data.node
  const isConditional = source?.type === 'CONDITION'
  const configuredDefault = isConditional && source?.config?.defaultEdgeId === edge.id
  const isDefault = isConditional && (Boolean(edge.default) || configuredDefault)
  return { ...edge, condition: isConditional && !isDefault && edge.condition?.trim() ? edge.condition.trim() : null, default: isDefault }
}
function toGraph(): WorkflowGraph {
  return {
    schemaVersion: 2,
    variables: props.modelValue.variables || [],
    formBindings: props.modelValue.formBindings || [],
    nodes: flowNodes.value.map(flowNode => ({ ...flowNode.data.node, position: { x: flowNode.position.x, y: flowNode.position.y } })),
    edges: flowEdges.value.map(flowEdge => { const edge = normalizeEdge(edgeModel(flowEdge)); return { ...edge, id: flowEdge.id, source: flowEdge.source, target: flowEdge.target, label: flowEdge.label ? String(flowEdge.label) : null } })
  }
}
function syncToModel() { if (!syncing.value && !props.readonly) emit('update:modelValue', toGraph()) }
function selectNode(nodeId: string | null) {
  selectedId.value = nodeId
  selectedEdgeId.value = null
  flowNodes.value = flowNodes.value.map(flowNode => ({ ...flowNode, data: { ...flowNode.data, selected: flowNode.id === nodeId } }))
  const selected = flowNodes.value.find(flowNode => flowNode.id === nodeId)
  emit('select', selected ? { ...selected.data.node, position: { x: selected.position.x, y: selected.position.y } } : null)
  emit('edgeSelect', null)
  closeContextMenu()
}
function selectEdge(edgeId: string | null) {
  selectedId.value = null
  selectedEdgeId.value = edgeId
  flowNodes.value = flowNodes.value.map(flowNode => ({ ...flowNode, data: { ...flowNode.data, selected: false } }))
  const selected = flowEdges.value.find(edge => edge.id === edgeId)
  emit('select', null)
  emit('edgeSelect', selected ? edgeModel(selected) : null)
  closeContextMenu()
}
function onNodeClick(event: NodeMouseEvent) { selectNode(event.node.id) }
function onEdgeClick(event: EdgeMouseEvent) { selectEdge(event.edge.id) }
function onPaneClick() { selectNode(null) }
function onNodeDragStop(event: { node: Node }) { const node = flowNodes.value.find(item => item.id === event.node.id); if (node) { node.position = { ...event.node.position }; syncToModel() } }
function onConnect(connection: Connection) {
  if (props.readonly || !connection.source || !connection.target || connection.source === connection.target) return
  const source = flowNodes.value.find(node => node.id === connection.source)?.data.node
  const target = flowNodes.value.find(node => node.id === connection.target)?.data.node
  if (!source || !target || source.type === 'END' || target.type === 'START') return
  if (flowEdges.value.some(edge => edge.source === connection.source && edge.target === connection.target)) return
  const edge: WorkflowEdgeModel = { id: `edge-${Date.now()}`, source: connection.source, target: connection.target, sourceHandle: connection.sourceHandle || (layoutDirection.value === 'TB' ? 'source-bottom' : 'source-right'), targetHandle: connection.targetHandle || (layoutDirection.value === 'TB' ? 'target-top' : 'target-left'), label: null, condition: null, default: false }
  flowEdges.value = [...flowEdges.value, { id: edge.id, type: 'smoothstep', source: edge.source, target: edge.target, sourceHandle: edge.sourceHandle || (layoutDirection.value === 'TB' ? 'source-bottom' : 'source-right'), targetHandle: edge.targetHandle || (layoutDirection.value === 'TB' ? 'target-top' : 'target-left'), markerEnd: MarkerType.ArrowClosed, data: { model: edge } }]
  syncToModel()
}
function newId(type: string) { return `${type.toLowerCase()}-${Date.now()}-${Math.floor(Math.random() * 1000)}` }
function nodeSize(type: WorkflowNodeType) {
  if (type === 'START' || type === 'END') return { width: 112, height: 112 }
  if (type === 'CONDITION' || type === 'PARALLEL_SPLIT' || type === 'PARALLEL_JOIN') return { width: 136, height: 136 }
  return { width: 220, height: 104 }
}
function currentCanvasPosition(type: WorkflowNodeType): { x: number; y: number } {
  const rect = canvas.value?.getBoundingClientRect()
  const size = nodeSize(type)
  if (!rect || !rect.width || !rect.height) return { x: 180, y: 120 }
  const center = screenToFlowCoordinate({ x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 })
  return { x: center.x - size.width / 2, y: center.y - size.height / 2 }
}
function addNode(type: WorkflowNodeType, parentId?: string) {
  if (props.readonly) return
  const labels: Record<WorkflowNodeType, string> = { START: '发起', APPROVAL: '人工审批', CC: '抄送', CONDITION: '条件网关', PARALLEL_SPLIT: '并行分支', PARALLEL_JOIN: '并行汇聚', END: '结束' }
  const config: WorkflowNodeConfig = type === 'APPROVAL' ? { assigneeType: 'USER', assigneeIds: [], mode: 'ANY', emptyAssigneeAction: 'ERROR', actionPolicy: { allowedActions: ['APPROVE', 'REJECT', 'ADD_SIGN', 'CC'] } } : type === 'CC' ? { userIds: [] } : {}
  const id = newId(type)
  const parent = parentId ? flowNodes.value.find(node => node.id === parentId) : undefined
  const position = parent ? { x: parent.position.x + (layoutDirection.value === 'LR' ? 280 : 0), y: parent.position.y + (layoutDirection.value === 'TB' ? 170 : 0) } : currentCanvasPosition(type)
  const node: WorkflowNodeModel = { id, type, label: labels[type], position, config }
  flowNodes.value = [...flowNodes.value, { id, type: 'workflow', position: { ...node.position }, data: { node, selected: false, readonly: Boolean(props.readonly), layoutDirection: layoutDirection.value } }]
  if (parent && parent.data.node.type !== 'END') {
    const edge: WorkflowEdgeModel = { id: `edge-${Date.now()}`, source: parent.id, target: id, sourceHandle: layoutDirection.value === 'TB' ? 'source-bottom' : 'source-right', targetHandle: layoutDirection.value === 'TB' ? 'target-top' : 'target-left', label: null, condition: null, default: false }
    flowEdges.value = [...flowEdges.value, { id: edge.id, type: 'smoothstep', source: edge.source, target: edge.target, sourceHandle: edge.sourceHandle || (layoutDirection.value === 'TB' ? 'source-bottom' : 'source-right'), targetHandle: edge.targetHandle || (layoutDirection.value === 'TB' ? 'target-top' : 'target-left'), markerEnd: MarkerType.ArrowClosed, data: { model: edge } }]
  }
  selectNode(id)
  syncToModel()
}
function copyNode(nodeId: string) {
  if (props.readonly) return
  const source = flowNodes.value.find(node => node.id === nodeId)
  if (!source || ['START', 'END'].includes(source.data.node.type)) return
  const copied = { ...source.data.node, id: newId(source.data.node.type), label: `${source.data.node.label}（副本）`, position: { x: source.position.x + 70, y: source.position.y + 70 }, config: JSON.parse(JSON.stringify(source.data.node.config)) }
  flowNodes.value = [...flowNodes.value, { id: copied.id, type: 'workflow', position: { ...copied.position }, data: { node: copied, selected: false, readonly: false, layoutDirection: layoutDirection.value } }]
  selectNode(copied.id)
  syncToModel()
}
function deleteSelected() {
  if (props.readonly) return
  if (selectedEdgeId.value) {
    flowEdges.value = flowEdges.value.filter(edge => edge.id !== selectedEdgeId.value)
    selectEdge(null); syncToModel(); return
  }
  if (!selectedId.value) return
  const selected = flowNodes.value.find(node => node.id === selectedId.value)
  if (!selected || ['START', 'END'].includes(selected.data.node.type)) return
  flowNodes.value = flowNodes.value.filter(node => node.id !== selectedId.value)
  flowEdges.value = flowEdges.value.filter(edge => edge.source !== selectedId.value && edge.target !== selectedId.value)
  selectNode(null); syncToModel()
}
function layoutGraph() {
  const graph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}))
  graph.setGraph({ rankdir: layoutDirection.value, nodesep: 55, ranksep: 110, marginx: 70, marginy: 70 })
  flowNodes.value.forEach(node => graph.setNode(node.id, { width: 220, height: 104 }))
  flowEdges.value.forEach(edge => graph.setEdge(edge.source, edge.target))
  dagre.layout(graph)
  flowNodes.value = flowNodes.value.map(node => { const position = graph.node(node.id); return position ? { ...node, position: { x: position.x - 110, y: position.y - 52 } } : node })
  syncToModel()
}
function openContext(kind: 'node' | 'edge', id: string, event: MouseEvent) {
  if (props.readonly) return
  event.preventDefault()
  if (kind === 'node') selectNode(id); else selectEdge(id)
  const rect = canvas.value?.getBoundingClientRect()
  const left = rect ? event.clientX - rect.left : 20
  const top = rect ? event.clientY - rect.top : 20
  contextMenu.kind = kind; contextMenu.id = id; contextMenu.left = Math.max(8, Math.min(left, (rect?.width || 800) - 190)); contextMenu.top = Math.max(8, Math.min(top, (rect?.height || 560) - 170)); contextMenu.visible = true
}
function onNodeContextMenu(event: NodeMouseEvent) { openContext('node', event.node.id, event.event as MouseEvent) }
function onEdgeContextMenu(event: EdgeMouseEvent) { openContext('edge', event.edge.id, event.event as MouseEvent) }
function handleContextAction(action: 'edit' | 'copy' | 'delete' | 'add') {
  const id = contextMenu.id
  closeContextMenu()
  if (action === 'edit') { if (contextMenu.kind === 'node') selectNode(id); else selectEdge(id); return }
  if (action === 'copy') copyNode(id)
  if (action === 'delete') { if (contextMenu.kind === 'edge') { selectedEdgeId.value = id } else { selectedId.value = id; selectedEdgeId.value = null }; deleteSelected() }
  if (action === 'add') addNode('APPROVAL', id)
}
function closeContextMenu() { contextMenu.visible = false }
function toggleFullscreen() { isFullscreen.value = !isFullscreen.value; closeContextMenu(); emit('fullscreen', isFullscreen.value) }
function updateNodePresentation() {
  flowNodes.value = flowNodes.value.map(node => ({ ...node, data: { ...node.data, layoutDirection: layoutDirection.value } }))
}
function onKeydown(event: KeyboardEvent) {
  const target = event.target as HTMLElement | null
  const tagName = target?.tagName.toLowerCase()
  if (tagName === 'input' || tagName === 'textarea' || tagName === 'select' || target?.isContentEditable) return
  if (event.key === 'Escape' && isFullscreen.value) { event.preventDefault(); isFullscreen.value = false; emit('fullscreen', false); return }
  if (event.key === 'Delete' && !props.readonly && (selectedId.value || selectedEdgeId.value)) { event.preventDefault(); deleteSelected() }
}
function updateEdge(edge: WorkflowEdgeModel) {
  const normalized = normalizeEdge(edge)
  flowEdges.value = flowEdges.value.map(item => item.id === edge.id ? { ...item, label: normalized.label || undefined, data: { model: { ...normalized, sourceHandle: normalized.sourceHandle || (layoutDirection.value === 'TB' ? 'source-bottom' : 'source-right'), targetHandle: normalized.targetHandle || (layoutDirection.value === 'TB' ? 'target-top' : 'target-left') } } } : item)
  syncToModel()
}
watch(() => props.modelValue, value => loadGraph(value), { deep: true, immediate: true })
watch(() => props.readonly, value => { flowNodes.value = flowNodes.value.map(node => ({ ...node, data: { ...node.data, readonly: Boolean(value) } })) })
watch(() => props.nodeStatuses, value => { flowNodes.value = flowNodes.value.map(node => ({ ...node, data: { ...node.data, nodeStatus: value?.[node.id] } })) }, { deep: true })
watch(layoutDirection, updateNodePresentation)
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => { window.removeEventListener('keydown', onKeydown); if (isFullscreen.value) emit('fullscreen', false) })
defineExpose({ selectNode, layoutGraph })
</script>

<template>
  <div class="workflow-designer" :class="{ 'is-fullscreen': isFullscreen }" @click="closeContextMenu">
    <div class="workflow-designer__toolbar">
      <div class="workflow-designer__tools">
        <template v-if="!readonly">
          <el-button size="small" @click="addNode('APPROVAL')"><el-icon><EditPen /></el-icon>人工审批</el-button>
          <el-button size="small" @click="addNode('CONDITION')"><el-icon><Switch /></el-icon>条件网关</el-button>
          <el-button size="small" @click="addNode('PARALLEL_SPLIT')"><el-icon><Share /></el-icon>并行分支</el-button>
          <el-button size="small" @click="addNode('PARALLEL_JOIN')"><el-icon><Share /></el-icon>并行汇聚</el-button>
          <el-button size="small" @click="addNode('CC')"><el-icon><Plus /></el-icon>抄送</el-button>
        </template>
        <span v-else class="workflow-designer__readonly"><el-icon><TopRight /></el-icon>已发布版本，只读查看</span>
      </div>
      <div class="workflow-designer__tools">
        <el-radio-group v-model="layoutDirection" size="small" :disabled="readonly" @change="layoutGraph"><el-radio-button value="LR">横向</el-radio-button><el-radio-button value="TB">纵向</el-radio-button></el-radio-group>
        <el-button size="small" text :disabled="readonly" @click="layoutGraph"><el-icon><Refresh /></el-icon>整理布局</el-button>
        <el-button size="small" text type="danger" :disabled="readonly || (!selectedId && !selectedEdgeId)" @click="deleteSelected"><el-icon><Delete /></el-icon>删除</el-button>
        <el-button size="small" text @click.stop="toggleFullscreen"><el-icon><Close v-if="isFullscreen" /><FullScreen v-else /></el-icon>{{ isFullscreen ? '退出全屏' : '全屏绘制' }}</el-button>
      </div>
    </div>
    <div ref="canvas" class="workflow-designer__canvas">
      <VueFlow :nodes="flowNodes" :edges="flowEdges" :node-types="nodeTypes" fit-view-on-init :min-zoom="0.35" :max-zoom="1.8" :nodes-draggable="!readonly" :nodes-connectable="!readonly" :elements-selectable="true" :default-edge-options="{ type: 'smoothstep', markerEnd: MarkerType.ArrowClosed }" @node-click="onNodeClick" @edge-click="onEdgeClick" @pane-click="onPaneClick" @node-drag-stop="onNodeDragStop" @connect="onConnect" @node-context-menu="onNodeContextMenu" @edge-context-menu="onEdgeContextMenu" />
      <div v-if="contextMenu.visible" class="workflow-context-menu" :style="contextStyle" @click.stop>
        <button type="button" @click="handleContextAction('edit')"><el-icon><EditPen /></el-icon>编辑配置</button>
        <button v-if="contextMenu.kind === 'node'" type="button" @click="handleContextAction('copy')"><el-icon><Plus /></el-icon>复制节点</button>
        <button v-if="contextMenu.kind === 'node'" type="button" @click="handleContextAction('add')"><el-icon><TopRight /></el-icon>从此处添加节点</button>
        <button type="button" class="is-danger" @click="handleContextAction('delete')"><el-icon><Delete /></el-icon>删除{{ contextMenu.kind === 'node' ? '节点' : '连线' }}</button>
      </div>
      <div class="workflow-designer__hint">拖拽节点调整位置，点击连线编辑条件；右键节点或连线打开操作菜单</div>
    </div>
  </div>
</template>