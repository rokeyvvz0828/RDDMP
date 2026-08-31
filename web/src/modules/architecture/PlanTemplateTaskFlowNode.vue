<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import { Monitor } from '@element-plus/icons-vue'
import type { PlanDimension, TaskTemplateView } from './planApi'

type TaskNodeData = { task: TaskTemplateView; selected?: boolean }
const props = defineProps<NodeProps<TaskNodeData>>()
const task = computed(() => props.data.task)
const dimensionText = computed(() => ({ NONE: '', PHYSICAL_SUBSYSTEM: '物理子系统', DEPLOYMENT_UNIT: '部署单元' }[task.value.dimension as PlanDimension] || ''))
</script>

<template>
  <div class="workflow-node workflow-node--approval plan-template-flow-task"
       :class="{ 'is-selected': props.data.selected }">
    <Handle id="target-left" type="target" :position="Position.Left"
            class="workflow-node__handle workflow-node__handle--target workflow-node__handle--left" />
    <div class="workflow-node__topline">
      <span class="workflow-node__type">
        <span class="workflow-node__type-icon"><el-icon><Monitor /></el-icon></span>任务
      </span>
      <span class="workflow-node__status">{{ dimensionText || '计划级' }}</span>
    </div>
    <div class="workflow-node__content">
      <strong :title="task.name">{{ task.name }}</strong>
      <small>标准检查项 {{ task.checkItems.length }} 项</small>
    </div>
    <Handle id="source-right" type="source" :position="Position.Right"
            class="workflow-node__handle workflow-node__handle--source workflow-node__handle--right" />
  </div>
</template>

<style scoped>
.plan-template-flow-task {
  width: 150px;
  min-height: 68px;
}
.plan-template-flow-task :deep(.workflow-node__content strong) {
  font-size: 12px;
}
</style>
