<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import { Collection } from '@element-plus/icons-vue'
import type { StageView } from './planApi'

type StageNodeData = { stage: StageView; selected?: boolean; containerHeight?: number }
const props = defineProps<NodeProps<StageNodeData>>()
const stage = computed(() => props.data.stage)
const containerHeight = computed(() => props.data.containerHeight || 92)
const timeText = computed(() => {
  if (stage.value.startOffsetDays == null && stage.value.durationDays == null) return '时间未配置'
  const start = stage.value.startOffsetDays == null ? '第 0 天' : `第 ${stage.value.startOffsetDays} 天起`
  const end = stage.value.durationDays == null ? '' : `，持续 ${stage.value.durationDays} 天`
  return `${start}${end}`
})
</script>

<template>
  <div class="workflow-node workflow-node--approval plan-template-flow-stage"
       :style="{ height: containerHeight + 'px' }"
       :class="{ 'is-selected': props.data.selected }">
    <Handle id="target-left" type="target" :position="Position.Left"
            class="workflow-node__handle workflow-node__handle--target workflow-node__handle--left" />
    <div class="workflow-node__topline">
      <span class="workflow-node__type">
        <span class="workflow-node__type-icon"><el-icon><Collection /></el-icon></span>环节
      </span>
      <span class="workflow-node__status">{{ stage.tasks.length }} 个任务模板</span>
    </div>
    <div class="workflow-node__content">
      <strong :title="stage.name">{{ stage.name }}</strong>
      <small :title="timeText">{{ timeText }}</small>
    </div>
    <div class="plan-template-flow-stage__slot" aria-hidden="true"></div>
    <Handle id="source-right" type="source" :position="Position.Right"
            class="workflow-node__handle workflow-node__handle--source workflow-node__handle--right" />
  </div>
</template>

<style scoped>
.plan-template-flow-stage {
  width: 460px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}
.plan-template-flow-stage__slot {
  flex: 1 1 auto;
  margin-top: 8px;
  border-radius: 6px;
  border: 1px dashed color-mix(in srgb, var(--line) 90%, transparent);
  background: color-mix(in srgb, var(--panel-bg) 55%, transparent);
}
</style>
