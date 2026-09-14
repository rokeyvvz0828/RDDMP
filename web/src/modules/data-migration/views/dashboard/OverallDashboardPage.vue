<!--
  用途：数据迁移整体看板。
  说明：13 项指标按当前项目独立实时请求，最多 4 路并发；点击后才加载固定 20 条的滚动下钻。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ArrowRight, Close, Loading, Refresh, RefreshRight, Warning } from '@element-plus/icons-vue'
import { init, use, type EChartsType } from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { GraphicComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption } from 'echarts'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import { apiErrorMessage } from '../../../../api/error'
import {
  getDataMigrationDashboardDrilldown,
  getDataMigrationDashboardMetric,
  type DataMigrationDashboardDrilldownItem,
  type DataMigrationDashboardMetricCode,
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'

use([PieChart, GraphicComponent, TooltipComponent, CanvasRenderer])

type MetricStatus = 'idle' | 'loading' | 'success' | 'error'
type MetricTone = 'blue' | 'teal' | 'coral' | 'gold' | 'green' | 'violet'

interface MetricDefinition {
  code: DataMigrationDashboardMetricCode
  label: string
  scope: string
  tone: MetricTone
  featured: boolean
}

interface MetricState {
  status: MetricStatus
  count?: number
  calculatedAt?: string
  error: string
}

const metricDefinitions: readonly MetricDefinition[] = [
  { code: 'OVERALL_PLAN', label: '迁移整体方案', scope: '项目级', tone: 'blue', featured: true },
  { code: 'COMPONENT_PLAN', label: '组件级迁移方案', scope: '组件级', tone: 'teal', featured: true },
  { code: 'PROJECT_TOPIC', label: '项目级专题', scope: '项目级', tone: 'coral', featured: true },
  { code: 'COMPONENT_TOPIC', label: '组件级专题', scope: '组件级', tone: 'gold', featured: true },
  { code: 'REPORT', label: '汇报资料', scope: '资料', tone: 'coral', featured: false },
  { code: 'MEETING', label: '会议纪要', scope: '纪要', tone: 'gold', featured: false },
  { code: 'ISSUE', label: '问题清单', scope: '问题', tone: 'coral', featured: false },
  { code: 'RELEASE_DRILL', label: '投产及演练资料', scope: '执行资料', tone: 'green', featured: false },
  { code: 'MAPPING_DOC', label: '迁移映射资料', scope: '映射', tone: 'teal', featured: false },
  { code: 'RULE', label: '迁移检核规则', scope: '规则', tone: 'blue', featured: false },
  { code: 'PARAMETER', label: '迁移参数', scope: '参数', tone: 'violet', featured: false },
  { code: 'DEPENDENCY', label: '迁移过程依赖文件', scope: '依赖', tone: 'gold', featured: false },
  { code: 'SCRIPT', label: '迁移程序', scope: '程序', tone: 'green', featured: false },
]

const featuredMetrics = metricDefinitions.filter(item => item.featured)
const assetMetrics = metricDefinitions.filter(item => !item.featured)
const metricByCode = Object.fromEntries(metricDefinitions.map(item => [item.code, item])) as Record<DataMigrationDashboardMetricCode, MetricDefinition>

function emptyMetricState(): MetricState {
  return { status: 'idle', count: undefined, calculatedAt: undefined, error: '' }
}

const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId
const states = reactive(Object.fromEntries(metricDefinitions.map(item => [item.code, emptyMetricState()])) as Record<DataMigrationDashboardMetricCode, MetricState>)
const forbidden = ref(false)
const metricControllers = new Map<DataMigrationDashboardMetricCode, AbortController>()
let requestGeneration = 0

const successCount = computed(() => metricDefinitions.filter(item => states[item.code].status === 'success').length)
const errorCount = computed(() => metricDefinitions.filter(item => states[item.code].status === 'error').length)
const allSucceeded = computed(() => successCount.value === metricDefinitions.length)
const allLoading = computed(() => metricDefinitions.some(item => states[item.code].status === 'loading'))
const totalCount = computed(() => allSucceeded.value
  ? metricDefinitions.reduce((sum, item) => sum + (states[item.code].count ?? 0), 0)
  : undefined)
const latestCalculatedAt = computed(() => {
  const timestamps = metricDefinitions
    .map(item => states[item.code].calculatedAt)
    .filter((value): value is string => Boolean(value))
    .sort()
  return timestamps[timestamps.length - 1]
})

function isCanceled(error: unknown) {
  return (error as { code?: string; name?: string }).code === 'ERR_CANCELED'
    || (error as { name?: string }).name === 'CanceledError'
    || (error as { name?: string }).name === 'AbortError'
}

function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}

function formatTime(value?: string | null, includeDate = false) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', includeDate
    ? { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }
    : { hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}

function resetMetricStates() {
  metricDefinitions.forEach(item => Object.assign(states[item.code], emptyMetricState()))
}

function abortMetricRequests() {
  metricControllers.forEach(controller => controller.abort())
  metricControllers.clear()
}

async function loadMetric(code: DataMigrationDashboardMetricCode, projectId: number, generation: number) {
  metricControllers.get(code)?.abort()
  const controller = new AbortController()
  metricControllers.set(code, controller)
  const state = states[code]
  state.status = 'loading'
  state.error = ''
  try {
    const response = await getDataMigrationDashboardMetric(code, projectId, controller.signal)
    if (generation !== requestGeneration || controller.signal.aborted || scopeProjectId.value !== projectId) return
    const metric = response.data.data
    if (!metric) throw new Error('接口未返回指标数据')
    state.count = Number(metric.count) || 0
    state.calculatedAt = metric.calculatedAt
    state.status = 'success'
  } catch (error) {
    if (generation !== requestGeneration || controller.signal.aborted || isCanceled(error)) return
    if (httpStatus(error) === 403) {
      forbidden.value = true
      requestGeneration += 1
      abortMetricRequests()
      closeDrilldown()
      return
    }
    state.status = 'error'
    state.error = apiErrorMessage(error, '指标加载失败')
  } finally {
    if (metricControllers.get(code) === controller) metricControllers.delete(code)
  }
}

function refreshAll() {
  const projectId = scopeProjectId.value
  if (projectId == null || scopeState.value !== 'ready') return
  requestGeneration += 1
  const generation = requestGeneration
  forbidden.value = false
  abortMetricRequests()
  let cursor = 0
  const worker = async () => {
    while (cursor < metricDefinitions.length && generation === requestGeneration) {
      const definition = metricDefinitions[cursor++]
      await loadMetric(definition.code, projectId, generation)
    }
  }
  void Promise.all(Array.from({ length: 4 }, worker))
}

function retryMetric(code: DataMigrationDashboardMetricCode) {
  const projectId = scopeProjectId.value
  if (projectId == null || scopeState.value !== 'ready') return
  void loadMetric(code, projectId, requestGeneration)
}

const compositionGroups = computed(() => {
  const groups: Array<{ name: string; codes: DataMigrationDashboardMetricCode[]; color: string }> = [
    { name: '方案专题', codes: ['OVERALL_PLAN', 'COMPONENT_PLAN', 'PROJECT_TOPIC', 'COMPONENT_TOPIC'], color: '#2878b5' },
    { name: '汇报纪要', codes: ['REPORT', 'MEETING'], color: '#16867c' },
    { name: '问题清单', codes: ['ISSUE'], color: '#c65f48' },
    { name: '执行资料', codes: ['RELEASE_DRILL', 'MAPPING_DOC'], color: '#a77a22' },
    { name: '规则参数', codes: ['RULE', 'PARAMETER'], color: '#3b865f' },
    { name: '依赖程序', codes: ['DEPENDENCY', 'SCRIPT'], color: '#755a9f' },
  ]
  return groups.map(group => ({
    ...group,
    value: group.codes.reduce((sum, code) => sum + (states[code].count ?? 0), 0),
  }))
})

const rankedMetrics = computed(() => metricDefinitions
  .filter(item => states[item.code].count != null)
  .map(item => ({ ...item, value: states[item.code].count ?? 0 }))
  .sort((a, b) => b.value - a.value)
  .slice(0, 3))
const rankMaximum = computed(() => Math.max(1, ...rankedMetrics.value.map(item => item.value)))
const chartIncomplete = computed(() => successCount.value > 0 && !allSucceeded.value)

const chartElement = ref<HTMLDivElement | null>(null)
let chart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null
let themeObserver: MutationObserver | null = null

function renderChart() {
  if (!chartElement.value) return
  if (!chart) chart = init(chartElement.value)
  const styles = getComputedStyle(document.documentElement)
  const text = styles.getPropertyValue('--text').trim() || '#1c2632'
  const muted = styles.getPropertyValue('--muted').trim() || '#687483'
  const data = compositionGroups.value.filter(item => item.value > 0)
  const option: EChartsOption = {
    color: compositionGroups.value.map(item => item.color),
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 条（{d}%）' },
    series: [{
      name: '资产构成', type: 'pie', radius: ['56%', '76%'], center: ['50%', '46%'], avoidLabelOverlap: true,
      label: { show: false },
      emphasis: { label: { show: true, color: text, fontSize: 12, formatter: '{b}\n{c} 条' } },
      data,
    }],
    graphic: data.length ? [{
      type: 'text', left: 'center', top: '39%', silent: true,
      style: { text: String(data.reduce((sum, item) => sum + item.value, 0)), fill: text, fontSize: 23, fontWeight: 600 },
    }, {
      type: 'text', left: 'center', top: '54%', silent: true,
      style: { text: allSucceeded.value ? '全部资产' : '已加载', fill: muted, fontSize: 11 },
    }] : [],
  }
  chart.setOption(option, { notMerge: true })
  chart.resize()
}

function recreateChart() {
  chart?.dispose()
  chart = null
  void nextTick(renderChart)
}

watch(compositionGroups, () => renderChart(), { deep: true })

const drawerOpen = ref(false)
const activeMetricCode = ref<DataMigrationDashboardMetricCode | null>(null)
const drilldownRecords = ref<DataMigrationDashboardDrilldownItem[]>([])
const drilldownTotal = ref(0)
const drilldownPage = ref(0)
const drilldownLoading = ref(false)
const drilldownLoadingMore = ref(false)
const drilldownInitialError = ref('')
const drilldownPageError = ref('')
let drilldownController: AbortController | null = null
let drilldownGeneration = 0

const activeMetric = computed(() => activeMetricCode.value ? metricByCode[activeMetricCode.value] : null)
const drilldownComplete = computed(() => drilldownRecords.value.length >= drilldownTotal.value && drilldownPage.value > 0)

function closeDrilldown() {
  drilldownGeneration += 1
  drilldownController?.abort()
  drilldownController = null
  drilldownLoading.value = false
  drilldownLoadingMore.value = false
  drawerOpen.value = false
}

function openDrilldown(code: DataMigrationDashboardMetricCode) {
  if (states[code].status !== 'success') return
  closeDrilldown()
  activeMetricCode.value = code
  drilldownRecords.value = []
  drilldownTotal.value = 0
  drilldownPage.value = 0
  drilldownInitialError.value = ''
  drilldownPageError.value = ''
  drawerOpen.value = true
  void loadDrilldownPage(1)
}

async function loadDrilldownPage(page: number) {
  const code = activeMetricCode.value
  const projectId = scopeProjectId.value
  if (!code || projectId == null || drilldownLoading.value || drilldownLoadingMore.value) return
  const generation = drilldownGeneration
  const controller = new AbortController()
  drilldownController = controller
  if (page === 1) {
    drilldownLoading.value = true
    drilldownInitialError.value = ''
  } else {
    drilldownLoadingMore.value = true
    drilldownPageError.value = ''
  }
  try {
    const response = await getDataMigrationDashboardDrilldown(code, projectId, page, controller.signal)
    if (generation !== drilldownGeneration || controller.signal.aborted || code !== activeMetricCode.value || projectId !== scopeProjectId.value) return
    const result = response.data.data
    if (!result) throw new Error('接口未返回下钻数据')
    drilldownRecords.value = page === 1 ? result.records : [...drilldownRecords.value, ...result.records]
    drilldownTotal.value = Number(result.total) || 0
    drilldownPage.value = page
  } catch (error) {
    if (generation !== drilldownGeneration || controller.signal.aborted || isCanceled(error)) return
    const message = apiErrorMessage(error, '下钻数据加载失败')
    if (page === 1) drilldownInitialError.value = message
    else drilldownPageError.value = message
  } finally {
    if (generation === drilldownGeneration) {
      drilldownLoading.value = false
      drilldownLoadingMore.value = false
      if (drilldownController === controller) drilldownController = null
    }
  }
}

function loadMore() {
  if (!drilldownComplete.value && !drilldownPageError.value) void loadDrilldownPage(drilldownPage.value + 1)
}

function onDrilldownScroll(event: Event) {
  const element = event.currentTarget as HTMLElement
  if (element.scrollHeight - element.scrollTop - element.clientHeight <= 120) loadMore()
}

watch(scopeProjectId, projectId => {
  requestGeneration += 1
  abortMetricRequests()
  closeDrilldown()
  resetMetricStates()
  forbidden.value = false
  if (projectId != null && scopeState.value === 'ready') refreshAll()
}, { immediate: true })

watch(scopeState, state => {
  if (state === 'ready' && scopeProjectId.value != null && successCount.value === 0 && !allLoading.value) refreshAll()
})

onMounted(() => {
  void scope.ensureLoaded()
  void nextTick(() => {
    renderChart()
    if (chartElement.value) {
      resizeObserver = new ResizeObserver(() => chart?.resize())
      resizeObserver.observe(chartElement.value)
    }
    themeObserver = new MutationObserver(recreateChart)
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'data-palette'] })
  })
})

onBeforeUnmount(() => {
  requestGeneration += 1
  abortMetricRequests()
  closeDrilldown()
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <main class="dm-page-root migration-dashboard">
    <UiPageHeader title="迁移资产总览" description="方案、专题与执行资料的实时覆盖情况">
      <template #actions>
        <div class="dashboard-actions">
          <span v-if="latestCalculatedAt" class="dashboard-updated">更新于 {{ formatTime(latestCalculatedAt) }}</span>
          <el-tooltip content="刷新全部指标" placement="bottom">
            <el-button :disabled="allLoading || scopeState !== 'ready'" aria-label="刷新全部指标" @click="refreshAll">
              <el-icon :class="{ 'is-loading': allLoading }"><Refresh /></el-icon>
              <span class="dashboard-action-label">刷新数据</span>
            </el-button>
          </el-tooltip>
        </div>
      </template>
    </UiPageHeader>

    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <section v-else-if="forbidden" class="dm-state-panel">
      <el-result icon="warning" title="暂无整体看板查看权限" sub-title="请联系管理员开通数据迁移看板权限。" />
    </section>

    <template v-else>
      <section class="dashboard-lead" aria-label="核心迁移资产指标">
        <article class="dashboard-total">
          <span>迁移资产总量</span>
          <strong>{{ totalCount ?? '—' }}</strong>
          <small v-if="allSucceeded">13 类有效资产</small>
          <small v-else-if="errorCount">已完成 {{ successCount }}/13 · {{ errorCount }} 项异常</small>
          <small v-else>正在加载 {{ successCount }}/13</small>
        </article>

        <div class="dashboard-featured">
          <button
            v-for="metric in featuredMetrics"
            :key="metric.code"
            class="dashboard-metric"
            :class="[`tone-${metric.tone}`, `is-${states[metric.code].status}`]"
            type="button"
            :aria-label="`${metric.label}，${states[metric.code].status === 'success' ? `${states[metric.code].count} 条` : '尚未加载完成'}`"
            @click="openDrilldown(metric.code)"
          >
            <span class="dashboard-metric-name">{{ metric.label }}</span>
            <span v-if="states[metric.code].status === 'loading' && states[metric.code].count == null" class="dashboard-metric-loading"><el-icon class="is-loading"><Loading /></el-icon></span>
            <b v-else-if="states[metric.code].status === 'error'" class="dashboard-metric-error"><el-icon><Warning /></el-icon>加载失败</b>
            <b v-else class="dashboard-metric-value">{{ states[metric.code].count ?? '—' }}</b>
            <span class="dashboard-metric-meta">
              <em>{{ metric.scope }}</em>
              <el-tooltip v-if="states[metric.code].status === 'error'" :content="states[metric.code].error" placement="top">
                <span class="dashboard-retry" role="button" tabindex="0" aria-label="重试此指标" @click.stop="retryMetric(metric.code)" @keydown.enter.stop="retryMetric(metric.code)"><el-icon><RefreshRight /></el-icon></span>
              </el-tooltip>
              <el-icon v-else-if="states[metric.code].status === 'success'"><ArrowRight /></el-icon>
              <el-icon v-else-if="states[metric.code].status === 'loading'" class="is-loading"><Loading /></el-icon>
            </span>
          </button>
        </div>
      </section>

      <div class="dashboard-layout">
        <section class="dashboard-section">
          <header class="dashboard-section-head"><h2>内容与执行资产</h2><span>9 类资料</span></header>
          <div class="dashboard-asset-grid">
            <button
              v-for="metric in assetMetrics"
              :key="metric.code"
              class="dashboard-asset"
              :class="[`tone-${metric.tone}`, `is-${states[metric.code].status}`]"
              type="button"
              :aria-label="`${metric.label}，${states[metric.code].status === 'success' ? `${states[metric.code].count} 条` : '尚未加载完成'}`"
              @click="openDrilldown(metric.code)"
            >
              <i class="dashboard-asset-mark" />
              <span>{{ metric.label }}</span>
              <el-icon v-if="states[metric.code].status === 'loading' && states[metric.code].count == null" class="is-loading dashboard-asset-status"><Loading /></el-icon>
              <span v-else-if="states[metric.code].status === 'error'" class="dashboard-asset-status is-error" role="button" tabindex="0" aria-label="重试此指标" @click.stop="retryMetric(metric.code)" @keydown.enter.stop="retryMetric(metric.code)"><el-icon><RefreshRight /></el-icon></span>
              <b v-else>{{ states[metric.code].count ?? '—' }}</b>
            </button>
          </div>
        </section>

        <aside class="dashboard-visual">
          <header class="dashboard-visual-head">
            <div><span>实时分布</span><h2>资产构成</h2></div>
            <el-tag v-if="chartIncomplete" type="warning" effect="plain" size="small">数据未完整</el-tag>
            <el-tag v-else-if="allSucceeded" type="success" effect="plain" size="small">13 项已加载</el-tag>
          </header>
          <div class="dashboard-chart-wrap">
            <div ref="chartElement" class="dashboard-chart" role="img" aria-label="迁移资产分类构成环形图" />
            <div v-if="successCount === 0" class="dashboard-chart-empty">
              <el-icon :class="{ 'is-loading': allLoading }"><Loading v-if="allLoading" /><Warning v-else /></el-icon>
              <span>{{ allLoading ? '构成数据加载中' : '暂无可用构成数据' }}</span>
            </div>
          </div>
          <div class="dashboard-legend">
            <div v-for="group in compositionGroups" :key="group.name">
              <i :style="{ backgroundColor: group.color }" /><span>{{ group.name }}</span><b>{{ group.value }}</b>
            </div>
          </div>
          <div class="dashboard-ranked">
            <div v-for="metric in rankedMetrics" :key="metric.code" class="dashboard-rank">
              <span :title="metric.label">{{ metric.label }}</span>
              <div class="dashboard-rank-track"><i :class="`tone-${metric.tone}`" :style="{ width: `${metric.value / rankMaximum * 100}%` }" /></div>
              <b>{{ metric.value }}</b>
            </div>
          </div>
        </aside>
      </div>
    </template>

    <el-drawer v-model="drawerOpen" class="dashboard-drawer" :size="'min(480px, 96vw)'" :show-close="false" @closed="closeDrilldown">
      <template #header>
        <div class="dashboard-drawer-header">
          <div><h2>{{ activeMetric?.label }}</h2><span>{{ drilldownTotal }} 条实时数据</span></div>
          <el-tooltip content="关闭明细" placement="bottom">
            <el-button circle aria-label="关闭明细" @click="closeDrilldown"><el-icon><Close /></el-icon></el-button>
          </el-tooltip>
        </div>
      </template>

      <div class="dashboard-drawer-scroll" @scroll.passive="onDrilldownScroll">
        <div class="dashboard-drawer-context"><span>当前项目</span><b>{{ activeMetric?.scope }}</b></div>
        <div v-if="drilldownLoading" class="dashboard-drawer-state"><el-icon class="is-loading"><Loading /></el-icon><span>明细加载中</span></div>
        <el-result v-else-if="drilldownInitialError" icon="error" title="明细加载失败" :sub-title="drilldownInitialError">
          <template #extra><el-button type="primary" @click="loadDrilldownPage(1)">重新加载</el-button></template>
        </el-result>
        <el-empty v-else-if="drilldownPage > 0 && drilldownTotal === 0" description="暂无明细数据" />
        <template v-else>
          <div class="dashboard-drilldown-list">
            <article v-for="item in drilldownRecords" :key="item.id" class="dashboard-drilldown-item">
              <div class="dashboard-drilldown-main"><strong>{{ item.name || item.code || `记录 ${item.id}` }}</strong><span>{{ item.code || '未设置编号' }}</span></div>
              <dl>
                <div v-if="item.systemCode"><dt>关联系统</dt><dd>{{ item.systemCode }}</dd></div>
                <div><dt>层级</dt><dd>{{ item.granularity === 'PROJECT' ? '项目级' : item.granularity === 'SYSTEM' ? '组件级' : activeMetric?.scope }}</dd></div>
                <div><dt>更新时间</dt><dd>{{ formatTime(item.updatedAt, true) }}</dd></div>
              </dl>
            </article>
          </div>
          <div v-if="drilldownLoadingMore" class="dashboard-load-more"><el-icon class="is-loading"><Loading /></el-icon><span>正在加载更多</span></div>
          <div v-else-if="drilldownPageError" class="dashboard-load-more is-error"><span>{{ drilldownPageError }}</span><el-button text type="primary" @click="loadDrilldownPage(drilldownPage + 1)">继续加载</el-button></div>
          <div v-else-if="drilldownComplete" class="dashboard-load-more"><span>已加载全部 {{ drilldownTotal }} 条</span></div>
        </template>
      </div>
    </el-drawer>
  </main>
</template>

<style scoped>
.migration-dashboard { --dashboard-blue: #2878b5; --dashboard-teal: #16867c; --dashboard-coral: #c65f48; --dashboard-gold: #a77a22; --dashboard-green: #3b865f; --dashboard-violet: #755a9f; }
.dashboard-actions { display: flex; align-items: center; gap: 12px; }
.dashboard-updated { color: var(--muted); font-size: 12px; white-space: nowrap; }
.dashboard-lead { display: grid; grid-template-columns: minmax(208px, .72fr) minmax(0, 2.28fr); gap: 10px; margin-bottom: 18px; }
.dashboard-total { display: flex; min-height: 126px; flex-direction: column; justify-content: center; padding: 18px; border-radius: 6px; background: var(--dashboard-blue); color: #fff; }
.dashboard-total span { opacity: .86; font-size: 13px; }
.dashboard-total strong { margin: 6px 0 8px; font-size: 38px; line-height: 1; font-weight: 600; }
.dashboard-total small { min-height: 18px; opacity: .84; font-size: 12px; }
.dashboard-featured { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.dashboard-metric { --metric-accent: var(--dashboard-blue); position: relative; display: flex; min-width: 0; min-height: 126px; flex-direction: column; padding: 16px; overflow: hidden; border: 1px solid var(--line); border-radius: 6px; background: var(--panel-bg); color: var(--text); text-align: left; cursor: pointer; transition: border-color .16s ease, box-shadow .16s ease, transform .16s ease; }
.dashboard-metric::before { position: absolute; top: 0; right: 16px; left: 16px; height: 3px; background: var(--metric-accent); content: ''; }
.dashboard-metric:hover, .dashboard-metric:focus-visible { border-color: var(--metric-accent); box-shadow: 0 6px 18px color-mix(in srgb, var(--text) 9%, transparent); transform: translateY(-1px); outline: none; }
.dashboard-metric.is-loading { cursor: progress; }
.dashboard-metric.is-error { border-style: dashed; }
.dashboard-metric-name { min-height: 36px; color: var(--muted); font-size: 13px; line-height: 18px; overflow-wrap: anywhere; }
.dashboard-metric-value { margin: 6px 0 auto; font-size: 29px; line-height: 34px; font-weight: 600; }
.dashboard-metric-loading, .dashboard-metric-error { display: flex; min-height: 40px; align-items: center; gap: 5px; margin: 4px 0 auto; color: var(--muted); font-size: 13px; font-weight: 500; }
.dashboard-metric-error { color: var(--danger, #c45656); }
.dashboard-metric-meta { display: flex; min-height: 18px; align-items: center; justify-content: space-between; gap: 8px; color: var(--muted); font-size: 12px; }
.dashboard-metric-meta em { color: var(--metric-accent); font-style: normal; }
.dashboard-retry { display: inline-flex; width: 26px; height: 26px; align-items: center; justify-content: center; border-radius: 4px; color: var(--danger, #c45656); }
.dashboard-retry:hover, .dashboard-retry:focus-visible { background: color-mix(in srgb, var(--danger, #c45656) 12%, transparent); outline: 1px solid var(--danger, #c45656); }
.tone-blue { --metric-accent: var(--dashboard-blue); }
.tone-teal { --metric-accent: var(--dashboard-teal); }
.tone-coral { --metric-accent: var(--dashboard-coral); }
.tone-gold { --metric-accent: var(--dashboard-gold); }
.tone-green { --metric-accent: var(--dashboard-green); }
.tone-violet { --metric-accent: var(--dashboard-violet); }
.dashboard-layout { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(300px, .75fr); align-items: start; gap: 16px; }
.dashboard-section, .dashboard-visual { min-width: 0; }
.dashboard-section-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin: 4px 0 10px; }
.dashboard-section-head h2, .dashboard-visual-head h2 { margin: 0; color: var(--text); font-size: 17px; font-weight: 600; }
.dashboard-section-head span { color: var(--muted); font-size: 12px; }
.dashboard-asset-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.dashboard-asset { display: grid; grid-template-columns: 5px minmax(0, 1fr) auto; min-width: 0; min-height: 66px; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 5px; background: var(--panel-bg); color: var(--text); text-align: left; cursor: pointer; transition: border-color .16s ease, background-color .16s ease; }
.dashboard-asset:hover, .dashboard-asset:focus-visible { border-color: var(--metric-accent); outline: none; }
.dashboard-asset.is-error { border-style: dashed; }
.dashboard-asset-mark { width: 5px; height: 30px; border-radius: 2px; background: var(--metric-accent); }
.dashboard-asset > span:not(.dashboard-asset-status) { min-width: 0; color: var(--muted); font-size: 13px; line-height: 18px; overflow-wrap: anywhere; }
.dashboard-asset b { font-size: 20px; font-weight: 600; }
.dashboard-asset-status { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border-radius: 4px; color: var(--muted); }
.dashboard-asset-status.is-error { color: var(--danger, #c45656); }
.dashboard-asset-status.is-error:hover, .dashboard-asset-status.is-error:focus-visible { background: color-mix(in srgb, var(--danger, #c45656) 12%, transparent); outline: 1px solid var(--danger, #c45656); }
.dashboard-visual { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel-bg); }
.dashboard-visual-head { display: flex; min-height: 42px; align-items: flex-start; justify-content: space-between; gap: 8px; }
.dashboard-visual-head span { color: var(--muted); font-size: 11px; }
.dashboard-chart-wrap { position: relative; min-height: 168px; }
.dashboard-chart { width: 100%; height: 168px; }
.dashboard-chart-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--muted); font-size: 12px; }
.dashboard-legend { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px 12px; }
.dashboard-legend div { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; align-items: center; gap: 7px; min-width: 0; }
.dashboard-legend i { width: 8px; height: 8px; border-radius: 2px; }
.dashboard-legend span { overflow: hidden; color: var(--muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-legend b { font-size: 12px; font-weight: 600; }
.dashboard-ranked { display: grid; gap: 8px; min-height: 74px; padding-top: 12px; border-top: 1px solid var(--line); }
.dashboard-rank { display: grid; grid-template-columns: minmax(76px, 112px) minmax(0, 1fr) 30px; align-items: center; gap: 8px; }
.dashboard-rank > span { overflow: hidden; color: var(--muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-rank-track { height: 7px; overflow: hidden; background: var(--panel-muted); }
.dashboard-rank-track i { display: block; height: 100%; background: var(--metric-accent); }
.dashboard-rank b { font-size: 12px; font-weight: 600; text-align: right; }
:deep(.dashboard-drawer .el-drawer__header) { flex: 0 0 auto; margin: 0; padding: 18px 18px 14px; border-bottom: 1px solid var(--line); }
:deep(.dashboard-drawer .el-drawer__body) { min-height: 0; padding: 0; overflow: hidden; }
.dashboard-drawer-header { display: flex; width: 100%; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 12px; }
.dashboard-drawer-header > div { min-width: 0; }
.dashboard-drawer-header h2 { margin: 0 0 4px; color: var(--text); font-size: 18px; font-weight: 600; overflow-wrap: anywhere; }
.dashboard-drawer-header span { color: var(--muted); font-size: 12px; }
.dashboard-drawer-scroll { height: 100%; padding: 14px 18px 24px; overflow-y: auto; overscroll-behavior: contain; }
.dashboard-drawer-context { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 12px; background: var(--panel-muted); color: var(--muted); font-size: 12px; }
.dashboard-drawer-context b { color: var(--text); font-weight: 500; }
.dashboard-drawer-state { display: flex; min-height: 260px; align-items: center; justify-content: center; gap: 8px; color: var(--muted); }
.dashboard-drilldown-list { display: grid; }
.dashboard-drilldown-item { display: grid; gap: 10px; padding: 14px 2px; border-bottom: 1px solid var(--line); }
.dashboard-drilldown-main { display: flex; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 12px; }
.dashboard-drilldown-main strong { min-width: 0; color: var(--text); font-size: 14px; font-weight: 600; line-height: 20px; overflow-wrap: anywhere; }
.dashboard-drilldown-main span { flex: 0 0 auto; max-width: 42%; overflow: hidden; color: var(--muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.dashboard-drilldown-item dl { display: flex; flex-wrap: wrap; gap: 8px 18px; margin: 0; }
.dashboard-drilldown-item dl > div { min-width: 0; }
.dashboard-drilldown-item dt { color: var(--muted); font-size: 10px; }
.dashboard-drilldown-item dd { margin: 3px 0 0; color: var(--text); font-size: 12px; overflow-wrap: anywhere; }
.dashboard-load-more { display: flex; min-height: 52px; align-items: center; justify-content: center; gap: 8px; color: var(--muted); font-size: 12px; text-align: center; }
.dashboard-load-more.is-error { flex-wrap: wrap; color: var(--danger, #c45656); }
@media (max-width: 980px) {
  .dashboard-lead { grid-template-columns: minmax(176px, .55fr) minmax(0, 1.45fr); }
  .dashboard-featured { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dashboard-total { min-height: 0; }
  .dashboard-layout { grid-template-columns: 1fr; }
  .dashboard-asset-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .dashboard-visual { grid-template-columns: minmax(260px, .8fr) minmax(0, 1.2fr); align-items: center; }
  .dashboard-visual-head { grid-column: 1 / -1; }
  .dashboard-ranked { align-self: stretch; }
}
@media (max-width: 680px) {
  .dashboard-lead { grid-template-columns: 1fr; }
  .dashboard-total { min-height: 104px; }
  .dashboard-asset-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dashboard-visual { grid-template-columns: 1fr; }
  .dashboard-visual-head { grid-column: auto; }
}
@media (max-width: 520px) {
  .dashboard-actions { gap: 8px; }
  .dashboard-action-label { display: none; }
  .dashboard-lead { margin-bottom: 16px; }
  .dashboard-total { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-content: center; column-gap: 12px; padding: 14px; }
  .dashboard-total span, .dashboard-total small { grid-column: 1; }
  .dashboard-total strong { grid-column: 2; grid-row: 1 / span 2; align-self: center; margin: 0; font-size: 34px; }
  .dashboard-featured, .dashboard-asset-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dashboard-metric { min-height: 112px; padding: 12px; }
  .dashboard-metric::before { right: 12px; left: 12px; }
  .dashboard-metric-name { min-height: 38px; font-size: 12px; }
  .dashboard-metric-value { font-size: 26px; }
  .dashboard-asset { grid-template-columns: 4px minmax(0, 1fr); min-height: 76px; align-content: center; gap: 5px 9px; padding: 10px; }
  .dashboard-asset-mark { grid-row: 1 / span 2; width: 4px; height: 38px; }
  .dashboard-asset b, .dashboard-asset-status { grid-column: 2; justify-self: start; }
}
</style>
