<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import { Bell, CircleCheckFilled, Coordinate, Flag, Share, UserFilled } from '@element-plus/icons-vue'
import type { WorkflowNodeModel } from '../../api/workflow'

type WorkflowNodeData = { node: WorkflowNodeModel; selected?: boolean; readonly?: boolean; layoutDirection?: 'LR' | 'TB'; nodeStatus?: string }
const props = defineProps<NodeProps<WorkflowNodeData>>()
const node = computed(() => props.data.node)
const nodeStatusClass = computed(() => props.data.nodeStatus ? 'workflow-node--status-' + String(props.data.nodeStatus).toLowerCase() : '')
const nodeStatusLabel = computed(() => ({ DONE: '已完成', ACTIVE: '进行中', UNREACHED: '未到达', REJECTED: '已拒绝', CANCELLED: '已取消' }[String(props.data.nodeStatus || '')] || ''))
const handlePositions = [Position.Top, Position.Right, Position.Bottom, Position.Left] as const
function handleId(type: 'target' | 'source', position: Position) { return type + '-' + position }
const typeLabel = computed(() => ({ START: '开始事件', APPROVAL: '用户任务', CC: '抄送节点', CONDITION: '条件网关', PARALLEL_SPLIT: '并行分支', PARALLEL_JOIN: '并行汇聚', END: '结束事件' }[node.value.type]))
const typeClass = computed(() => `workflow-node--${node.value.type.toLowerCase()}`)
const isGateway = computed(() => ['CONDITION', 'PARALLEL_SPLIT', 'PARALLEL_JOIN'].includes(node.value.type))
const typeIcon = computed(() => ({ START: Flag, APPROVAL: UserFilled, CC: Bell, CONDITION: Coordinate, PARALLEL_SPLIT: Share, PARALLEL_JOIN: Share, END: CircleCheckFilled }[node.value.type]))
const assigneeText = computed(() => {
  if (node.value.type === 'START') return '流程入口'
  if (node.value.type === 'END') return '流程出口'
  if (node.value.type === 'CC') return node.value.config.templatePlaceholder ? '项目配置时指定' : '到达节点自动抄送'
  if (node.value.type === 'CONDITION') return '按连线条件选择分支'
  if (node.value.type === 'PARALLEL_SPLIT') return '同时进入多个审批分支'
  if (node.value.type === 'PARALLEL_JOIN') return '等待并行分支汇聚'
  const config = node.value.config
  const source = config.assigneeType === 'PROJECT_MEMBER' ? `项目成员 ${config.assigneeIds?.length || 0} 人` : config.assigneeType === 'PROJECT_ROLE' ? `项目角色 ${config.assigneeIds?.length || 0} 个` : config.assigneeType === 'TEMPLATE_PLACEHOLDER' ? '项目配置时指定' : config.assigneeType === 'ORG_OWNER' ? '组织负责人' : config.assigneeType === 'STARTER' ? '发起人' : config.assigneeType === 'FORM_FIELD' ? `表单字段 ${config.fieldName || ''}` : config.assigneeType === 'EXPRESSION' ? '表达式' : `${config.assigneeIds?.length || 0} 人`
  const mode = config.mode === 'ALL' ? '全部同意' : config.mode === 'PERCENT' ? `${config.percentage || 0}%同意` : '任一同意'
  return `${source} · ${mode}`
})
</script>
<template>
  <div class="workflow-node" :class="[typeClass, nodeStatusClass, { 'is-selected': selected || data.selected, 'is-readonly': data.readonly }]">
    <template v-if="node.type !== 'START'">
      <Handle v-for="position in handlePositions" :id="handleId('target', position)" :key="'target-' + position" type="target" :position="position" :connectable-start="false" :class="['workflow-node__handle', 'workflow-node__handle--target', 'workflow-node__handle--' + position]" />
    </template>
    <template v-if="isGateway">
      <div class="workflow-node__gateway-content">
        <span class="workflow-node__type-icon"><el-icon><component :is="typeIcon" /></el-icon></span>
        <strong :title="node.label">{{ node.label }}</strong>
      </div>
      <span v-if="nodeStatusLabel" class="workflow-node__status">{{ nodeStatusLabel }}</span>
    </template>
    <template v-else>
      <div class="workflow-node__topline">
        <span v-if="node.type === 'START' || node.type === 'END'" class="workflow-node__type-icon"><el-icon><component :is="typeIcon" /></el-icon></span>
        <span v-else class="workflow-node__type"><span class="workflow-node__type-icon"><el-icon><component :is="typeIcon" /></el-icon></span>{{ typeLabel }}</span>
        <span v-if="nodeStatusLabel" class="workflow-node__status">{{ nodeStatusLabel }}</span>
      </div>
      <div class="workflow-node__content">
        <strong :title="node.label">{{ node.label }}</strong>
        <small v-if="node.type === 'APPROVAL' || node.type === 'CC'" :title="assigneeText">{{ assigneeText }}</small>
      </div>
    </template>
    <template v-if="node.type !== 'END'">
      <Handle v-for="position in handlePositions" :id="handleId('source', position)" :key="'source-' + position" type="source" :position="position" :connectable-end="false" :class="['workflow-node__handle', 'workflow-node__handle--source', 'workflow-node__handle--' + position]" />
    </template>
  </div>
</template>
