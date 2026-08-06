<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import type { WorkflowNodeModel } from '../../api/workflow'
type WorkflowNodeData = { node: WorkflowNodeModel; selected?: boolean }
const props = defineProps<NodeProps<WorkflowNodeData>>()
const node = computed(() => props.data.node)
const typeLabel = computed(() => ({ START: '开始', APPROVAL: '审批', CC: '抄送', CONDITION: '条件', PARALLEL_SPLIT: '并行分支', PARALLEL_JOIN: '并行汇聚', END: '结束' }[node.value.type]))
const typeClass = computed(() => `workflow-node--${node.value.type.toLowerCase()}`)
const assigneeText = computed(() => {
  if (node.value.type === 'START') return '流程入口'
  if (node.value.type === 'END') return '流程出口'
  if (node.value.type === 'CC') return '到达节点自动抄送'
  if (node.value.type === 'CONDITION') return '按流程变量选择分支'
  if (node.value.type === 'PARALLEL_SPLIT') return '同时进入多个审批分支'
  if (node.value.type === 'PARALLEL_JOIN') return '等待并行分支汇聚'
  const config = node.value.config
  const source = config.assigneeType === 'ORG_OWNER' ? '组织负责人' : config.assigneeType === 'STARTER' ? '发起人' : config.assigneeType === 'FORM_FIELD' ? `表单字段 ${config.fieldName || ''}` : config.assigneeType === 'EXPRESSION' ? '表达式' : `${config.assigneeIds?.length || 0} 人`
  const mode = config.mode === 'ALL' ? '全部同意' : config.mode === 'PERCENT' ? `${config.percentage || 0}%同意` : '任一同意'
  return `${source} · ${mode}`
})
</script>
<template>
  <div class="workflow-node" :class="[typeClass, { 'is-selected': selected || data.selected }]">
    <Handle v-if="node.type !== 'START'" type="target" :position="Position.Top" />
    <div class="workflow-node__topline"><span class="workflow-node__type">{{ typeLabel }}</span><span class="workflow-node__dot" /></div>
    <strong>{{ node.label }}</strong>
    <small>{{ assigneeText }}</small>
    <Handle v-if="node.type !== 'END'" type="source" :position="Position.Bottom" />
  </div>
</template>