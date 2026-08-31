<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type EChartsType } from 'echarts/core'
import { CustomChart } from 'echarts/charts'
import { DataZoomComponent, GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption, CustomSeriesRenderItemParams, CustomSeriesRenderItemAPI } from 'echarts'

use([CustomChart, DataZoomComponent, GridComponent, TooltipComponent, CanvasRenderer])

interface PlanGanttRow {
  id: number
  name: string
  type: 'PLAN' | 'STAGE' | 'TASK'
  status: string
  plannedStart: string | null
  plannedEnd: string | null
  actualStart: string | null
  actualEnd: string | null
  progress: number | null
  overdue: boolean
  targetName: string | null
}

const props = withDefaults(defineProps<{ rows: PlanGanttRow[]; height?: string; ariaLabel?: string }>(), {
  height: '560px',
  ariaLabel: '搭建计划甘特图'
})

const zoom = ref(100)
const chartEl = ref<HTMLDivElement | null>(null)
let chart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null
let themeObserver: MutationObserver | null = null

const ganttRows = computed(() => props.rows)

const typeLabels: Record<string, string> = { PLAN: '计划', STAGE: '环节', TASK: '任务' }
const statusLabels: Record<string, string> = {
  NOT_STARTED: '未开始', WAITING_PRECEDING: '等待前置', IN_PROGRESS: '进行中',
  BLOCKED: '阻塞', COMPLETED: '已完成', CANCELLED: '已取消'
}
const statusColors: Record<string, string> = {
  IN_PROGRESS: '#147d92', COMPLETED: '#2b9274', BLOCKED: '#b9503d',
  OVERDUE: '#d86b42', WAITING_PRECEDING: '#c47a2c', NOT_STARTED: '#147d92',
  CANCELLED: '#9aa2ad', PLAN_IN_PROGRESS: '#147d92'
}

function colorOf(row: PlanGanttRow) {
  if (row.status === 'COMPLETED') return '#2b9274'
  if (row.status === 'CANCELLED') return '#9aa2ad'
  if (row.overdue) return '#d86b42'
  if (row.status === 'BLOCKED') return '#b9503d'
  if (row.status === 'WAITING_PRECEDING') return '#c47a2c'
  return '#147d92'
}

function renderItem(params: CustomSeriesRenderItemParams, api: CustomSeriesRenderItemAPI) {
  const categoryIndex = api.value(0) as number
  const start = api.coord([api.value(1), categoryIndex])
  const end = api.coord([api.value(2), categoryIndex])
  const height = Math.min(16, (api.size?.([0, 1]) as number[])[1] * 0.5)
  const shape = { x: start[0], y: start[1] - height / 2, width: Math.max(2, end[0] - start[0]), height, r: 3 }
  return {
    type: 'rect' as const,
    shape: params.coordSys ? clipRect(shape, params.coordSys as unknown as { x: number; y: number; width: number; height: number }) || shape : shape,
    style: { fill: api.visual('color') as string }
  }
}

function clipRect(shape: { x: number; y: number; width: number; height: number; r: number }, area: { x: number; y: number; width: number; height: number }) {
  const x = Math.max(shape.x, area.x)
  const right = Math.min(shape.x + shape.width, area.x + area.width)
  if (right < x) return null
  return { ...shape, x, width: right - x }
}

const ganttOption = computed<EChartsOption>(() => ({
  tooltip: {
    formatter: (params: unknown) => {
      const item = (params as { data: PlanGanttRow }).data
      const name = item.targetName ? `${item.name}（${item.targetName}）` : item.name
      return `${typeLabels[item.type]}: ${name}<br/>计划：${item.plannedStart?.replace('T', ' ').slice(0, 16) || '—'} ~ ${item.plannedEnd?.replace('T', ' ').slice(0, 16) || '—'}<br/>实际：${item.actualStart?.replace('T', ' ').slice(0, 16) || '—'} ~ ${item.actualEnd?.replace('T', ' ').slice(0, 16) || '—'}<br/>状态：${statusLabels[item.status] || item.status}${item.progress != null ? ` · 进度 ${item.progress}%` : ''}${item.overdue ? ' · 已逾期' : ''}`
    }
  },
  grid: { left: 210, right: 36, top: 26, bottom: 62 },
  dataZoom: [
    { type: 'slider', xAxisIndex: 0, bottom: 12, height: 20, start: 0, end: zoom.value },
    { type: 'inside', xAxisIndex: 0, start: 0, end: zoom.value }
  ],
  xAxis: {
    type: 'time',
    axisLabel: { formatter: '{MM}-{dd}' }
  },
  yAxis: {
    type: 'category',
    inverse: true,
    data: ganttRows.value.map(row => {
      const prefix = row.type === 'PLAN' ? '◆ ' : row.type === 'STAGE' ? '■ ' : '  · '
      return prefix + (row.targetName ? `${row.name}（${row.targetName}）` : row.name)
    }),
    axisLabel: { width: 190, overflow: 'truncate' }
  },
  series: [{
    type: 'custom',
    renderItem,
    encode: { x: [1, 2], y: 0 },
    data: ganttRows.value.map((row, index) => ({
      ...row,
      value: [index, row.plannedStart ?? row.actualStart, row.plannedEnd ?? row.actualEnd],
      itemStyle: { color: colorOf(row), opacity: row.type === 'PLAN' ? 0.9 : row.type === 'STAGE' ? 0.85 : 0.75 }
    }))
  }]
}))

function render() {
  if (!chartEl.value) return
  if (!chart) chart = init(chartEl.value)
  const styles = getComputedStyle(document.documentElement)
  const text = styles.getPropertyValue('--text').trim()
  const muted = styles.getPropertyValue('--muted').trim()
  const line = styles.getPropertyValue('--line').trim()
  chart.setOption({
    textStyle: { color: text },
    xAxis: { axisLabel: { color: muted }, axisLine: { lineStyle: { color: line } }, splitLine: { lineStyle: { color: line } } },
    yAxis: { axisLabel: { color: muted }, axisLine: { lineStyle: { color: line } }, splitLine: { lineStyle: { color: line } } },
    ...ganttOption.value
  }, { notMerge: true })
  chart.resize()
}

function recreate() {
  chart?.dispose()
  chart = null
  void nextTick(render)
}

onMounted(() => {
  render()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartEl.value!)
  themeObserver = new MutationObserver(recreate)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'data-palette'] })
})
watch(() => props.rows, render, { deep: true })
watch(zoom, render)
onBeforeUnmount(() => { resizeObserver?.disconnect(); themeObserver?.disconnect(); chart?.dispose() })

function zoomIn() { zoom.value = Math.max(30, zoom.value - 10) }
function zoomOut() { zoom.value = Math.min(100, zoom.value + 10) }
defineExpose({ zoomIn, zoomOut })
</script>

<template>
  <section class="plan-gantt">
    <header class="plan-gantt__toolbar">
      <span class="plan-gantt__title">计划排期</span>
      <span class="plan-gantt__legend">
        <span><i class="is-active" />进行中</span>
        <span><i class="is-done" />已完成</span>
        <span><i class="is-delay" />逾期/阻塞</span>
        <span><i class="is-wait" />等待前置</span>
        <span><i class="is-cancel" />已取消</span>
      </span>
      <span class="plan-gantt__spacer" />
      <el-tooltip content="缩小时间范围"><el-button circle aria-label="缩小时间范围" @click="zoomIn"><el-icon><ZoomIn /></el-icon></el-button></el-tooltip>
      <el-tooltip content="扩大时间范围"><el-button circle aria-label="扩大时间范围" @click="zoomOut"><el-icon><ZoomOut /></el-icon></el-button></el-tooltip>
    </header>
    <div ref="chartEl" class="plan-gantt__chart" :style="{ height }" role="img" :aria-label="ariaLabel" />
    <footer class="plan-gantt__hint">支持鼠标滚轮缩放和底部时间轴拖动</footer>
  </section>
</template>

<style scoped>
.plan-gantt {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel-bg);
  overflow: hidden;
}
.plan-gantt__toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 14px;
  min-height: 48px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--line);
}
.plan-gantt__title {
  font-weight: 700;
  font-size: 13px;
}
.plan-gantt__legend {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--muted);
  font-size: 11px;
}
.plan-gantt__legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.plan-gantt__legend i {
  width: 18px;
  height: 7px;
  border-radius: 2px;
  background: #147d92;
}
.plan-gantt__legend i.is-done { background: #2b9274; }
.plan-gantt__legend i.is-delay { background: #d86b42; }
.plan-gantt__legend i.is-wait { background: #c47a2c; }
.plan-gantt__legend i.is-cancel { background: #9aa2ad; }
.plan-gantt__spacer {
  flex: 1 1 auto;
}
.plan-gantt__chart {
  min-height: 320px;
}
.plan-gantt__hint {
  padding: 8px 14px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 11px;
}
@media (max-width: 760px) {
  .plan-gantt__chart {
    min-width: 680px;
  }
  .plan-gantt {
    overflow-x: auto;
  }
}
</style>
