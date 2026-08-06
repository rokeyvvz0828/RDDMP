<script setup lang="ts">
import { computed } from 'vue'
import type { EChartsOption } from 'echarts'
import DeliveryChart from './DeliveryChart.vue'
import type { DeliveryProject } from '../types'

const props = defineProps<{ projects: DeliveryProject[] }>()
const stageNames = ['需求', '研发', '测试', '迁移', '投产']
const stageOption = computed<EChartsOption>(() => ({
  grid: { left: 54, right: 20, top: 30, bottom: 36 }, tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: stageNames }, yAxis: { type: 'value', minInterval: 1 },
  series: [{ name: '项目数量', type: 'bar', barMaxWidth: 34, itemStyle: { borderRadius: [3, 3, 0, 0] }, data: stageNames.map(stage => props.projects.filter(item => item.stage === stage).length) }]
}))
const statusOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 个（{d}%）' }, legend: { bottom: 0 },
  series: [{ name: '项目状态', type: 'pie', radius: ['48%', '70%'], center: ['50%', '44%'], label: { formatter: '{b}\n{c} 个' }, data: ['进行中', '待启动', '有风险', '已完成'].map(name => ({ name, value: props.projects.filter(item => item.status === name).length })) }]
}))
const qualityOption: EChartsOption = {
  grid: { left: 48, right: 24, top: 34, bottom: 36 }, tooltip: { trigger: 'axis' }, legend: { top: 0 },
  xAxis: { type: 'category', boundaryGap: false, data: ['第1周', '第2周', '第3周', '第4周', '第5周', '第6周'] },
  yAxis: { type: 'value' },
  series: [
    { name: '缺陷存量', type: 'line', smooth: true, data: [46, 52, 39, 31, 22, 16] },
    { name: '自动化覆盖率', type: 'line', smooth: true, data: [58, 61, 67, 72, 78, 83] }
  ]
}
</script>

<template>
  <div class="delivery-analytics-grid">
    <section class="delivery-panel"><header class="delivery-panel__header"><div><span class="panel-kicker">柱状图</span><h3>项目阶段分布</h3></div><el-tag effect="plain">实时汇总</el-tag></header><DeliveryChart :option="stageOption" aria-label="项目阶段分布柱状图" /></section>
    <section class="delivery-panel"><header class="delivery-panel__header"><div><span class="panel-kicker">饼状图</span><h3>交付状态构成</h3></div><el-tag effect="plain">{{ projects.length }} 个项目</el-tag></header><DeliveryChart :option="statusOption" aria-label="交付状态构成饼状图" /></section>
    <section class="delivery-panel delivery-analytics-grid__wide"><header class="delivery-panel__header"><div><span class="panel-kicker">折线图</span><h3>质量趋势</h3></div><el-date-picker type="monthrange" value-format="YYYY-MM" start-placeholder="开始月份" end-placeholder="结束月份" style="width:240px" /></header><DeliveryChart :option="qualityOption" height="340px" aria-label="缺陷存量与自动化覆盖率趋势折线图" /></section>
  </div>
</template>
