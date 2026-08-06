<script setup lang="ts">
import { computed } from 'vue'
import { Bell, Calendar, CircleCheck, Warning } from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import DeliveryChart from './DeliveryChart.vue'
import type { DeliveryProject } from '../types'

const props = defineProps<{ projects: DeliveryProject[] }>()
const emit = defineEmits<{ navigate: [view: string] }>()
const activeCount = computed(() => props.projects.filter(item => item.status === '进行中').length)
const riskCount = computed(() => props.projects.filter(item => item.status === '有风险').length)
const doneCount = computed(() => props.projects.filter(item => item.status === '已完成').length)
const averageProgress = computed(() => Math.round(props.projects.reduce((sum, item) => sum + item.progress, 0) / props.projects.length))
const trendOption: EChartsOption = {
  grid: { left: 42, right: 18, top: 26, bottom: 30 },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', boundaryGap: false, data: ['3月', '4月', '5月', '6月', '7月', '8月'] },
  yAxis: { type: 'value', min: 40, max: 100, axisLabel: { formatter: '{value}%' } },
  series: [{ name: '按期交付率', type: 'line', smooth: true, symbolSize: 8, areaStyle: { opacity: .12 }, data: [61, 68, 72, 70, 81, 86] }]
}
const recentProjects = computed(() => props.projects.slice(0, 4))
</script>

<template>
  <div class="delivery-workbench">
    <div class="delivery-metric-grid">
      <button class="delivery-metric" type="button" @click="emit('navigate', 'projects')"><span><Calendar /></span><div><small>在途项目</small><strong>{{ activeCount }}</strong><p>本周 2 个里程碑到期</p></div></button>
      <button class="delivery-metric is-risk" type="button" @click="emit('navigate', 'approval')"><span><Warning /></span><div><small>风险项目</small><strong>{{ riskCount }}</strong><p>1 项需要今日决策</p></div></button>
      <button class="delivery-metric is-success" type="button" @click="emit('navigate', 'analytics')"><span><CircleCheck /></span><div><small>已交付</small><strong>{{ doneCount }}</strong><p>本月按期率 86%</p></div></button>
      <button class="delivery-metric is-accent" type="button" @click="emit('navigate', 'schedule')"><span><Bell /></span><div><small>平均进度</small><strong>{{ averageProgress }}%</strong><p>较上周提升 7%</p></div></button>
    </div>

    <div class="delivery-overview-grid">
      <section class="delivery-panel delivery-panel--chart">
        <header class="delivery-panel__header"><div><span class="panel-kicker">趋势</span><h3>按期交付率</h3></div><el-button link type="primary" @click="emit('navigate', 'analytics')">查看分析</el-button></header>
        <DeliveryChart :option="trendOption" height="286px" aria-label="最近六个月按期交付率折线图" />
      </section>
      <section class="delivery-panel delivery-focus-list">
        <header class="delivery-panel__header"><div><span class="panel-kicker">关注事项</span><h3>今日交付动态</h3></div><el-badge :value="4" /></header>
        <button v-for="project in recentProjects" :key="project.id" type="button" @click="emit('navigate', 'projects')">
          <span class="delivery-focus-list__dot" :class="`is-${project.status}`" />
          <div><strong>{{ project.name }}</strong><small>{{ project.code }} · {{ project.stage }} · {{ project.owner }}</small></div>
          <b>{{ project.progress }}%</b>
        </button>
      </section>
    </div>

    <section class="delivery-panel delivery-stage-strip">
      <header class="delivery-panel__header"><div><span class="panel-kicker">阶段分布</span><h3>全生命周期交付看板</h3></div></header>
      <div class="delivery-stage-strip__items">
        <div v-for="(stage, index) in ['需求', '研发', '测试', '迁移', '投产']" :key="stage"><span>{{ index + 1 }}</span><strong>{{ stage }}</strong><small>{{ projects.filter(item => item.stage === stage).length }} 个项目</small></div>
      </div>
    </section>
  </div>
</template>
