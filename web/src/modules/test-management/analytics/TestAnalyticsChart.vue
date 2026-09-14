<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { EChartsOption } from "echarts";
import { init, use, type EChartsType } from "echarts/core";
import { BarChart, HeatmapChart, LineChart, PieChart, RadarChart } from "echarts/charts";
import { GridComponent, LegendComponent, RadarComponent, TooltipComponent, VisualMapComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";

use([BarChart, HeatmapChart, LineChart, PieChart, RadarChart, GridComponent, LegendComponent, RadarComponent, TooltipComponent, VisualMapComponent, CanvasRenderer]);

const props = withDefaults(defineProps<{ option: EChartsOption; height?: string; ariaLabel?: string }>(), {
  height: "340px",
  ariaLabel: "统计图表",
});
const el = ref<HTMLDivElement>();
let chart: EChartsType | undefined;
let observer: ResizeObserver | undefined;

function render() {
  if (!el.value) return;
  chart ||= init(el.value);
  const styles = getComputedStyle(document.documentElement);
  chart.setOption({
    color: ["#147d92", "#d86b42", "#2b9274", "#c47a2c", "#7657a6", "#596273"],
    textStyle: { color: styles.getPropertyValue("--text").trim() },
    legend: { textStyle: { color: styles.getPropertyValue("--text").trim() } },
    ...props.option,
  }, { notMerge: true });
  chart.resize();
}
onMounted(() => {
  render();
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(el.value!);
});
watch(() => props.option, () => void nextTick(render), { deep: true });
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose(); });
</script>

<template>
  <div ref="el" class="analytics-chart" :style="{ height }" role="img" :aria-label="ariaLabel" />
</template>

<style scoped>
.analytics-chart { width: 100%; min-width: 0; }
</style>
