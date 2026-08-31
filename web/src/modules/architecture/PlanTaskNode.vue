<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import { Monitor } from '@element-plus/icons-vue'
import type { TaskDetailView, TaskStatus } from './planApi'

type TaskNodeData = { task: TaskDetailView; selected?: boolean; readonly?: boolean }
const props = defineProps<NodeProps<TaskNodeData>>()
const task = computed(() => props.data.task)
const statusLabel = computed(() => ({ NOT_STARTED: '未开始', WAITING_PRECEDING: '等待前置', IN_PROGRESS: '进行中', BLOCKED: '阻塞', COMPLETED: '已完成', CANCELLED: '已取消' }[task.value.status as TaskStatus] || task.value.status))
const statusClass = computed(() => ({ NOT_STARTED: '', WAITING_PRECEDING: 'is-waiting', IN_PROGRESS: 'is-active', BLOCKED: 'is-blocked', COMPLETED: 'is-done', CANCELLED: 'is-cancelled' }[task.value.status as TaskStatus] || ''))
</script>

<template>
  <div class="workflow-node workflow-node--approval plan-flow-task"
       :class="[statusClass, { 'is-selected': props.data.selected, 'is-readonly': props.data.readonly }]">
    <template v-if="!props.data.readonly">
      <Handle id="target-left" type="target" :position="Position.Left"
              class="workflow-node__handle workflow-node__handle--target workflow-node__handle--left" />
    </template>
    <div class="workflow-node__topline">
      <span class="workflow-node__type">
        <span class="workflow-node__type-icon"><el-icon><Monitor /></el-icon></span>任务
      </span>
      <span class="workflow-node__status">{{ statusLabel }}</span>
    </div>
    <div class="workflow-node__content">
      <strong :title="task.name">{{ task.name }}</strong>
      <small :title="task.targetName || '计划级'">{{ task.targetName || '计划级' }} · {{ task.progress ?? 0 }}%</small>
    </div>
    <template v-if="!props.data.readonly">
      <Handle id="source-right" type="source" :position="Position.Right"
              class="workflow-node__handle workflow-node__handle--source workflow-node__handle--right" />
    </template>
  </div>
</template>

<style scoped>
.plan-flow-task {
  width: 150px;
  min-height: 68px;
}
.plan-flow-task :deep(.workflow-node__content strong) {
  font-size: 12px;
}
.plan-flow-task.is-done::before {
  background: var(--success);
}
.plan-flow-task.is-active::before {
  background: var(--brand);
}
.plan-flow-task.is-waiting::before,
.plan-flow-task.is-blocked::before {
  background: var(--warning);
}
.plan-flow-task.is-cancelled::before {
  background: var(--muted);
}
</style>
