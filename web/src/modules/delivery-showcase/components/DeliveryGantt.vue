<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EChartsOption, CustomSeriesRenderItemParams, CustomSeriesRenderItemAPI } from 'echarts'
import { ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import DeliveryChart from './DeliveryChart.vue'
import type { DeliveryProject } from '../types'

const props = defineProps<{ projects: DeliveryProject[] }>()
const zoom = ref(70)
const ganttRows = computed(() => props.projects.flatMap(project => project.milestones.slice(0, 2).map(item => ({ ...item, project: project.name }))).slice(0, 12))
function renderItem(params: CustomSeriesRenderItemParams, api: CustomSeriesRenderItemAPI) {
  const categoryIndex = api.value(0) as number
  const start = api.coord([api.value(1), categoryIndex])
  const end = api.coord([api.value(2), categoryIndex])
  const height = Math.min(18, (api.size?.([0, 1]) as number[])[1] * .5)
  const shape = { x: start[0], y: start[1] - height / 2, width: Math.max(2, end[0] - start[0]), height, r: 3 }
  return { type: 'rect' as const, shape: params.coordSys ? (echartsGraphicClip(shape, params.coordSys as unknown as { x: number; y: number; width: number; height: number }) || shape) : shape, style: { fill: api.visual('color') as string } }
}
function echartsGraphicClip(shape: { x: number; y: number; width: number; height: number; r: number }, area: { x: number; y: number; width: number; height: number }) {
  const x = Math.max(shape.x, area.x); const right = Math.min(shape.x + shape.width, area.x + area.width)
  if (right < x) return null
  return { ...shape, x, width: right - x }
}
const ganttOption = computed<EChartsOption>(() => ({
  tooltip: { formatter: (params: unknown) => { const item = (params as { data: { name: string; project: string; start: string; end: string; progress: number } }).data; return `${item.project}<br/>${item.name}<br/>${item.start} 至 ${item.end}<br/>进度 ${item.progress}%` } },
  grid: { left: 176, right: 30, top: 20, bottom: 62 },
  dataZoom: [{ type: 'slider', xAxisIndex: 0, bottom: 12, height: 20, start: 0, end: zoom.value }, { type: 'inside', xAxisIndex: 0, start: 0, end: zoom.value }],
  xAxis: { type: 'time', min: '2026-07-01', max: '2026-10-15', axisLabel: { formatter: '{MM}-{dd}' } },
  yAxis: { type: 'category', inverse: true, data: ganttRows.value.map(item => `${item.project.slice(0, 10)} / ${item.stage}`), axisLabel: { width: 154, overflow: 'truncate' } },
  series: [{ type: 'custom', renderItem, encode: { x: [1, 2], y: 0 }, data: ganttRows.value.map((item, index) => ({ name: item.name, project: item.project, start: item.start, end: item.end, progress: item.progress, value: [index, item.start, item.end], itemStyle: { color: item.status === '延期' ? '#b9503d' : item.status === '已完成' ? '#2b9274' : '#147d92' } })) }]
}))
</script>

<template>
  <section class="delivery-panel delivery-gantt">
    <header class="delivery-panel__header"><div><span class="panel-kicker">计划排期</span><h3>项目里程碑甘特图</h3></div><div class="delivery-icon-actions"><el-tooltip content="缩小时间范围"><el-button circle :aria-label="'缩小时间范围'" @click="zoom = Math.max(30, zoom - 10)"><el-icon><ZoomIn /></el-icon></el-button></el-tooltip><el-tooltip content="扩大时间范围"><el-button circle :aria-label="'扩大时间范围'" @click="zoom = Math.min(100, zoom + 10)"><el-icon><ZoomOut /></el-icon></el-button></el-tooltip></div></header>
    <DeliveryChart :option="ganttOption" height="520px" aria-label="交付项目里程碑甘特图" />
    <footer class="delivery-gantt__legend"><span><i class="is-active" />进行中</span><span><i class="is-done" />已完成</span><span><i class="is-delay" />延期</span><small>支持鼠标滚轮缩放和底部时间轴拖动</small></footer>
  </section>
</template>
