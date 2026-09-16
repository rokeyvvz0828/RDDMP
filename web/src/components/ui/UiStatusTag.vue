<script setup lang="ts">
import { computed } from 'vue'
type StatusTone = 'primary' | 'success' | 'warning' | 'danger' | 'info'
const props = withDefaults(defineProps<{
  value: string | number | boolean
  labels?: Record<string, string>
  tone?: StatusTone
  /** 在标签前显示同色状态指示灯，默认关闭以保持既有页面外观。 */
  indicator?: boolean
}>(), { labels: undefined, tone: undefined, indicator: false })
const label = computed(() => props.labels?.[String(props.value)] || String(props.value))
const type = computed(() => {
  if (props.tone) return props.tone
  const value = String(props.value).toLowerCase()
  if (['1', 'true', 'enabled', 'active', 'online', 'success'].includes(value)) return 'success'
  if (['0', 'false', 'disabled', 'inactive', 'offline'].includes(value)) return 'info'
  if (['error', 'rejected', 'failed'].includes(value)) return 'danger'
  return 'warning'
})
</script>
<template>
  <el-tag :type="type" effect="plain" size="small">
    <span v-if="indicator" class="ui-status-tag__indicator" aria-hidden="true" />
    <span>{{ label }}</span>
  </el-tag>
</template>
<style scoped>
.ui-status-tag__indicator {
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 5px;
  vertical-align: middle;
  background: currentColor;
  border-radius: 50%;
  box-shadow: 0 0 0 3px color-mix(in srgb, currentColor 18%, transparent);
}
</style>
