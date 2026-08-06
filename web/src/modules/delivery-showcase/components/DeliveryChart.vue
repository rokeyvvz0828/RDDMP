<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use, type EChartsType } from 'echarts/core'
import { BarChart, CustomChart, LineChart, PieChart } from 'echarts/charts'
import { DataZoomComponent, GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'

use([BarChart, CustomChart, LineChart, PieChart, DataZoomComponent, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = withDefaults(defineProps<{ option: EChartsOption; height?: string; ariaLabel?: string }>(), { height: '320px', ariaLabel: '数据图表' })
const chartEl = ref<HTMLDivElement | null>(null)
let chart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null
let themeObserver: MutationObserver | null = null

function render() {
  if (!chartEl.value) return
  if (!chart) chart = init(chartEl.value)
  const styles = getComputedStyle(document.documentElement)
  const text = styles.getPropertyValue('--text').trim()
  const muted = styles.getPropertyValue('--muted').trim()
  const line = styles.getPropertyValue('--line').trim()
  chart.setOption({
    color: ['#147d92', '#d86b42', '#2b9274', '#c47a2c', '#7657a6', '#596273'],
    textStyle: { color: text },
    legend: { textStyle: { color: text } },
    xAxis: { axisLabel: { color: muted }, axisLine: { lineStyle: { color: line } }, splitLine: { lineStyle: { color: line } } },
    yAxis: { axisLabel: { color: muted }, axisLine: { lineStyle: { color: line } }, splitLine: { lineStyle: { color: line } } },
    ...props.option
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
watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => { resizeObserver?.disconnect(); themeObserver?.disconnect(); chart?.dispose() })
</script>

<template><div ref="chartEl" class="delivery-chart" :style="{ height }" role="img" :aria-label="ariaLabel" /></template>
