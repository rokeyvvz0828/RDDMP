<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import { Collection } from '@element-plus/icons-vue'
import type { StageDetailView, PlanStatus } from './planApi'

type StageNodeData = {
  stage: StageDetailView
  selected?: boolean
  readonly?: boolean
  layoutDirection?: 'LR' | 'TB'
  containerHeight?: number
}
const props = defineProps<NodeProps<StageNodeData>>()
const stage = computed(() => props.data.stage)
const containerHeight = computed(() => props.data.containerHeight || 150)
const statusLabel = computed(() => ({ NOT_STARTED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' }[stage.value.status as PlanStatus] || stage.value.status))
const statusClass = computed(() => ({ NOT_STARTED: '', IN_PROGRESS: 'is-active', COMPLETED: 'is-done', CANCELLED: 'is-cancelled' }[stage.value.status as PlanStatus] || ''))
const handlePositions = [Position.Top, Position.Right, Position.Bottom, Position.Left] as const
function handleId(type: 'target' | 'source', position: Position) { return type + '-' + position }
</script>

<template>
  <div class="workflow-node workflow-node--approval plan-flow-stage"
       :style="{ height: containerHeight + 'px' }"
       :class="[statusClass, { 'is-selected': props.data.selected, 'is-readonly': props.data.readonly }]">
    <Handle v-for="position in handlePositions" :id="handleId('target', position)" :key="'target-' + position"
            type="target" :position="position" :connectable-start="false"
            :class="['workflow-node__handle', 'workflow-node__handle--target', 'workflow-node__handle--' + position]" />

    <div class="workflow-node__topline">
      <span class="workflow-node__type">
        <span class="workflow-node__type-icon"><el-icon><Collection /></el-icon></span>环节
      </span>
      <span class="workflow-node__status">{{ statusLabel }}</span>
    </div>
    <div class="workflow-node__content">
      <strong :title="stage.name">{{ stage.name }}</strong>
      <small :title="`${stage.plannedStart ?? '—'} ~ ${stage.plannedEnd ?? '—'}`">
        {{ stage.plannedStart?.replace('T', ' ').slice(0, 16) || '—' }} ~ {{ stage.plannedEnd?.replace('T', ' ').slice(0, 16) || '—' }}
      </small>
      <div class="plan-flow-stage__progress">
        <el-progress :percentage="stage.progress ?? 0" :stroke-width="6" :show-text="false" />
        <span>{{ stage.progress ?? 0 }}%</span>
      </div>
    </div>
    <div class="plan-flow-stage__slot" aria-hidden="true">
      <span class="plan-flow-stage__slot-tip">{{ stage.tasks.length }} 个任务</span>
    </div>

    <Handle v-for="position in handlePositions" :id="handleId('source', position)" :key="'source-' + position"
            type="source" :position="position" :connectable-end="false"
            :class="['workflow-node__handle', 'workflow-node__handle--source', 'workflow-node__handle--' + position]" />
  </div>
</template>

<style scoped>
.plan-flow-stage {
  width: 460px;
  min-height: 150px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}
.plan-flow-stage__progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.plan-flow-stage__progress span {
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}
.plan-flow-stage__slot {
  flex: 1 1 auto;
  margin-top: 8px;
  border-radius: 6px;
  border: 1px dashed color-mix(in srgb, var(--line) 90%, transparent);
  background: color-mix(in srgb, var(--panel-bg) 55%, transparent);
  display: grid;
  place-items: start center;
  padding-top: 6px;
}
.plan-flow-stage__slot-tip {
  color: var(--muted);
  font-size: 11px;
}
.plan-flow-stage.is-done::before {
  background: var(--success);
}
.plan-flow-stage.is-active::before {
  background: var(--brand);
}
.plan-flow-stage.is-cancelled::before {
  background: var(--muted);
}
</style>
