<script setup lang="ts">
import { markRaw, nextTick, ref, shallowRef, watch } from 'vue'
import { VueFlow, type Connection, type Edge, type Node, type NodeComponent, type NodeTypesObject } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import { Delete, Plus, Refresh, Share, Switch } from '@element-plus/icons-vue'
import WorkflowNode from './WorkflowNode.vue'
import type { WorkflowGraph, WorkflowNodeConfig, WorkflowNodeModel, WorkflowNodeType } from '../../api/workflow'

const props = defineProps<{ modelValue: WorkflowGraph }>()
const emit = defineEmits<{ 'update:modelValue': [value: WorkflowGraph]; select: [node: WorkflowNodeModel | null] }>()
type DesignerNodeData = { node: WorkflowNodeModel; selected?: boolean }
type DesignerNode = Node<DesignerNodeData> & { data: DesignerNodeData }
const flowNodes = shallowRef<DesignerNode[]>([])
const flowEdges = shallowRef<Edge[]>([])
const selectedId = ref<string | null>(null)
const nodeTypes: NodeTypesObject = markRaw({ workflow: WorkflowNode as unknown as NodeComponent })
const syncing = ref(false)

function cloneGraph(graph: WorkflowGraph): WorkflowGraph { return JSON.parse(JSON.stringify(graph)) as WorkflowGraph }
function loadGraph(graph: WorkflowGraph) {
  syncing.value = true
  const cloned = cloneGraph(graph)
  flowNodes.value = cloned.nodes.map(node => ({ id: node.id, type: 'workflow', position: { ...node.position }, data: { node, selected: node.id === selectedId.value } }))
  flowEdges.value = cloned.edges.map(edge => ({ id: edge.id, source: edge.source, target: edge.target, label: edge.label || undefined, animated: edge.source.includes('parallel') || edge.target.includes('parallel') }))
  void nextTick(() => { syncing.value = false })
}
function toGraph(): WorkflowGraph {
  return {
    schemaVersion: 2,
    variables: props.modelValue.variables || [],
    formBindings: props.modelValue.formBindings || [],
    nodes: flowNodes.value.map(flowNode => ({ ...(flowNode.data.node), position: { x: flowNode.position.x, y: flowNode.position.y } })),
    edges: flowEdges.value.map(edge => ({ id: edge.id, source: edge.source, target: edge.target, label: edge.label as string | undefined }))
  }
}
function syncToModel() { if (!syncing.value) emit('update:modelValue', toGraph()) }
function selectNode(nodeId: string | null) {
  selectedId.value = nodeId
  flowNodes.value.forEach(flowNode => { flowNode.data.selected = flowNode.id === nodeId })
  const selected = flowNodes.value.find(flowNode => flowNode.id === nodeId)
  emit('select', selected ? { ...selected.data.node, position: { x: selected.position.x, y: selected.position.y } } : null)
}
function onNodeClick(event: { node: Node }) { selectNode(event.node.id) }
function onPaneClick() { selectNode(null) }
function onNodeDragStop(event: { node: Node }) { const node = flowNodes.value.find(item => item.id === event.node.id); if (node) node.position = { ...event.node.position }; syncToModel() }
function onConnect(connection: Connection) {
  if (!connection.source || !connection.target || connection.source === connection.target) return
  const source = flowNodes.value.find(node => node.id === connection.source)?.data.node
  const target = flowNodes.value.find(node => node.id === connection.target)?.data.node
  if (!source || !target || source.type === 'END' || target.type === 'START') return
  if (flowEdges.value.some(edge => edge.source === connection.source && edge.target === connection.target)) return
  flowEdges.value.push({ id: `edge-${Date.now()}`, source: connection.source, target: connection.target, animated: source.type.includes('PARALLEL') || target.type.includes('PARALLEL') })
  syncToModel()
}
function addNode(type: WorkflowNodeType) {
  const id = `${type.toLowerCase()}-${Date.now()}`
  const labels: Record<WorkflowNodeType, string> = { START: '发起', APPROVAL: '审批节点', CC: '抄送节点', CONDITION: '条件网关', PARALLEL_SPLIT: '并行分支', PARALLEL_JOIN: '并行汇聚', END: '结束' }
  const config: WorkflowNodeConfig = type === 'APPROVAL' ? { assigneeType: 'USER', assigneeIds: [], mode: 'ANY', emptyAssigneeAction: 'ERROR', actionPolicy: { allowedActions: ['APPROVE', 'REJECT', 'RETURN', 'ADD_SIGN', 'CC'] } } : type === 'CC' ? { userIds: [] } : {}
  const node: WorkflowNodeModel = { id, type, label: labels[type], position: { x: 180, y: 120 + flowNodes.value.length * 90 }, config }
  flowNodes.value.push({ id, type: 'workflow', position: { ...node.position }, data: { node, selected: false } })
  selectNode(id); syncToModel()
}
function deleteSelected() {
  if (!selectedId.value) return
  const selected = flowNodes.value.find(node => node.id === selectedId.value)
  if (!selected || ['START', 'END'].includes(selected.data.node.type)) return
  flowNodes.value = flowNodes.value.filter(node => node.id !== selectedId.value)
  flowEdges.value = flowEdges.value.filter(edge => edge.source !== selectedId.value && edge.target !== selectedId.value)
  selectNode(null); syncToModel()
}
function resetLayout() { flowNodes.value.forEach((node, index) => { node.position = { x: 180, y: 60 + index * 150 } }); syncToModel() }
watch(() => props.modelValue, value => loadGraph(value), { deep: true, immediate: true })
defineExpose({ selectNode })
</script>

<template>
  <div class="workflow-designer">
    <div class="workflow-designer__toolbar">
      <div class="workflow-designer__tools">
        <el-button size="small" @click="addNode('APPROVAL')"><el-icon><Plus /></el-icon>审批节点</el-button>
        <el-button size="small" @click="addNode('CONDITION')"><el-icon><Switch /></el-icon>条件网关</el-button>
        <el-button size="small" @click="addNode('PARALLEL_SPLIT')"><el-icon><Share /></el-icon>并行分支</el-button>
        <el-button size="small" @click="addNode('PARALLEL_JOIN')"><el-icon><Share /></el-icon>并行汇聚</el-button>
        <el-button size="small" @click="addNode('CC')"><el-icon><Plus /></el-icon>抄送节点</el-button>
      </div>
      <div class="workflow-designer__tools">
        <el-button size="small" text @click="resetLayout"><el-icon><Refresh /></el-icon>整理布局</el-button>
        <el-button size="small" text type="danger" :disabled="!selectedId" @click="deleteSelected"><el-icon><Delete /></el-icon>删除节点</el-button>
      </div>
    </div>
    <div class="workflow-designer__canvas">
      <VueFlow :nodes="flowNodes" :edges="flowEdges" :node-types="nodeTypes" fit-view-on-init :min-zoom="0.45" :max-zoom="1.5" :nodes-draggable="true" :nodes-connectable="true" :elements-selectable="true" @node-click="onNodeClick" @pane-click="onPaneClick" @node-drag-stop="onNodeDragStop" @connect="onConnect" />
      <div class="workflow-designer__hint">拖拽节点调整位置，连接节点形成审批流程</div>
    </div>
  </div>
</template>