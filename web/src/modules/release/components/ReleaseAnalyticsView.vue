<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { DataAnalysis, Grid, Link, Refresh, Tickets } from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import { apiErrorMessage } from '../../../api/error'
import { getReleaseAnalyticsDrilldown, getReleaseAnalyticsSummary, listReleaseWindows, type ReleaseAnalyticsSummaryDto, type ReleaseWindowDto } from '../../../api/release'
import { useProjectContextStore } from '../../../stores/project-context'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import ReleaseChart from './ReleaseChart.vue'

type DrillDimension = 'versionType' | 'status' | 'productionResult'
interface DrillSelection { dimension?: DrillDimension; value?: string; title: string }

const projectStore = useProjectContextStore()
const windows = ref<ReleaseWindowDto[]>([])
const windowId = ref<number>()
const summary = ref<ReleaseAnalyticsSummaryDto>({ windowCount: 0, applicationCount: 0, subsystemCount: 0, deliveryUnitCount: 0, fileMediaCount: 0, requirementCount: 0, versionTypes: {}, productionResults: {} })
const drill = ref<DrillSelection>({ title: '全部版本申请' })
const drillRows = ref<Record<string, unknown>[]>([])
const drillTotal = ref(0)
const drillPage = ref(1)
const drillPageSize = ref(10)
const loading = ref(false)
const drillLoading = ref(false)
const error = ref('')
const versionLabels: Record<string, string> = { REGULAR: '常规版本', URGENT: '紧急版本', EMERGENCY: '应急版本' }
const statusLabels: Record<string, string> = { DRAFT: '草稿', IN_REVIEW: '审批中', RETURNED: '已退回', WITHDRAWN: '已撤回', CANCELLED: '已取消', RELEASED: '制品准出' }
const resultLabels: Record<string, string> = { RELEASED: '制品准出', SUCCEEDED: '投产成功', FAILED: '投产失败', NOT_DEPLOYED: '未投产' }

const versionData = computed(() => Object.entries(summary.value.versionTypes).map(([name, value]) => ({ name: versionLabels[name] || name, code: name, value })))
const resultData = computed(() => Object.entries(summary.value.productionResults).map(([name, value]) => ({ name: resultLabels[name] || name, code: name, value })))
const versionOption = computed<EChartsOption>(() => ({ grid: { left: 44, right: 20, top: 28, bottom: 50 }, tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: versionData.value.map(item => item.name) }, yAxis: { type: 'value', minInterval: 1 }, series: [{ name: '申请单', type: 'bar', barMaxWidth: 38, data: versionData.value.map(item => item.value) }] }))
const resultOption = computed<EChartsOption>(() => ({ tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 个（{d}%）' }, legend: { bottom: 0 }, series: [{ name: '投产结果', type: 'pie', radius: ['44%', '68%'], center: ['50%', '43%'], label: { formatter: '{b}\n{c}' }, data: resultData.value }] }))

function text(row: Record<string, unknown>, key: string) { return row[key] == null ? '-' : String(row[key]) }
function minute(value: unknown) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-' }
function versionTone(value: string) { return value === 'EMERGENCY' ? 'danger' : value === 'URGENT' ? 'warning' : 'info' }
function statusTone(value: string) { return value === 'RELEASED' ? 'success' : value === 'IN_REVIEW' ? 'primary' : value === 'RETURNED' || value === 'WITHDRAWN' ? 'warning' : 'info' }

async function loadWindows() {
  if (!projectStore.current) return
  windows.value = (await listReleaseWindows({ page: 1, size: 200, projectId: projectStore.current.ref })).data.data.records
}
async function loadSummary() {
  if (!projectStore.current) return
  summary.value = (await getReleaseAnalyticsSummary(projectStore.current.ref, windowId.value)).data.data
}
async function loadDrilldown() {
  if (!projectStore.current) return
  drillLoading.value = true
  try {
    const response = await getReleaseAnalyticsDrilldown({
      page: drillPage.value, size: drillPageSize.value, projectId: projectStore.current.ref,
      windowId: windowId.value, dimension: drill.value.dimension, value: drill.value.value
    })
    drillRows.value = response.data.data.records
    drillTotal.value = response.data.data.total
  } finally { drillLoading.value = false }
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.initialize()
    await Promise.all([loadWindows(), loadSummary()])
    drillPage.value = 1
    await loadDrilldown()
  } catch (requestError) {
    error.value = apiErrorMessage(requestError, '统计分析加载失败，请稍后重试')
  } finally { loading.value = false }
}
function selectDrill(dimension: DrillDimension | undefined, value: string | undefined, title: string) {
  drill.value = { dimension, value, title }
  drillPage.value = 1
  void loadDrilldown().catch(requestError => { error.value = apiErrorMessage(requestError, '统计下钻加载失败') })
}
function selectVersion(payload: { name: string }) {
  const item = versionData.value.find(value => value.name === payload.name)
  if (item) selectDrill('versionType', item.code, `${item.name}申请`)
}
function selectResult(payload: { name: string }) {
  const item = resultData.value.find(value => value.name === payload.name)
  if (item) selectDrill('productionResult', item.code, `${item.name}明细`)
}
function changePage(page: number, size = drillPageSize.value) {
  drillPage.value = page
  drillPageSize.value = size
  void loadDrilldown()
}

onMounted(load)
watch(windowId, () => { drill.value = { title: windowId.value ? '当前窗口全部申请' : '全部版本申请' }; void load() })
watch(() => projectStore.currentRef, () => { windowId.value = undefined; void load() })
</script>

<template>
  <div class="release-analytics-view">
    <UiToolbar><el-select v-model="windowId" clearable placeholder="全部投产窗口" style="width: 300px"><el-option v-for="item in windows" :key="item.id" :value="item.id" :label="`${item.windowCode} · ${item.windowName}`" /></el-select><template #actions><el-button :icon="Refresh" circle aria-label="刷新统计分析" @click="load" /></template></UiToolbar>
    <section v-if="error" class="release-state-panel"><el-result icon="error" title="统计数据加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <div v-else v-loading="loading" class="release-analytics-content">
      <div class="release-metric-grid release-metric-grid--five"><button type="button" @click="selectDrill(undefined, undefined, '全部版本申请')"><span><Tickets /></span><div><small>投产窗口</small><strong>{{ summary.windowCount }}</strong><p>{{ summary.applicationCount }} 张申请</p></div></button><button type="button" @click="selectDrill(undefined, undefined, '物理子系统明细')"><span><Grid /></span><div><small>物理子系统</small><strong>{{ summary.subsystemCount }}</strong><p>{{ summary.deliveryUnitCount }} 个交付单元 · {{ summary.fileMediaCount }} 个文件</p></div></button><button type="button" @click="selectDrill(undefined, undefined, '需求关联明细')"><span><Link /></span><div><small>关联需求</small><strong>{{ summary.requirementCount }}</strong><p>按需求编号去重</p></div></button><button type="button" @click="selectDrill('status', 'RELEASED', '制品准出申请')"><span><DataAnalysis /></span><div><small>版本申请</small><strong>{{ summary.applicationCount }}</strong><p>查看全部状态</p></div></button><button type="button" @click="selectDrill('productionResult', 'SUCCEEDED', '投产成功明细')"><span><DataAnalysis /></span><div><small>投产成功</small><strong>{{ summary.productionResults.SUCCEEDED || 0 }}</strong><p>交付内容结果</p></div></button></div>
      <UiEmptyState v-if="!summary.applicationCount && !loading" title="当前范围暂无统计数据" description="请切换投产窗口或先创建版本申请。" />
      <div v-else class="release-chart-grid"><section class="release-chart-panel"><header><div><span class="release-panel-kicker">版本构成</span><h3>版本类型分布</h3></div><small>点击柱形下钻</small></header><ReleaseChart :option="versionOption" @chart-click="selectVersion" /></section><section class="release-chart-panel"><header><div><span class="release-panel-kicker">投产结果</span><h3>交付内容投产状态</h3></div><small>点击图形下钻</small></header><ReleaseChart :option="resultOption" @chart-click="selectResult" /></section></div>
      <section class="release-drilldown-panel"><header><div><span class="release-panel-kicker">数据下钻</span><h3>{{ drill.title }}</h3></div><span>{{ drillTotal }} 张申请</span></header><UiDataTable :data="drillRows" :loading="drillLoading" row-key="application_code" border><el-table-column label="版本申请" min-width="180"><template #default="scope"><div class="release-primary-cell is-static"><strong>{{ text(scope.row, 'application_code') }}</strong><span>{{ text(scope.row, 'project_name') }}</span></div></template></el-table-column><el-table-column label="物理子系统" min-width="180"><template #default="scope"><div class="release-primary-cell is-static"><strong>{{ text(scope.row, 'subsystem_name') }}</strong><span>{{ text(scope.row, 'subsystem_code') }}</span></div></template></el-table-column><el-table-column label="版本类型" width="112"><template #default="scope"><UiStatusTag :value="versionLabels[text(scope.row, 'version_type')] || text(scope.row, 'version_type')" :tone="versionTone(text(scope.row, 'version_type'))" /></template></el-table-column><el-table-column label="申请状态" width="110"><template #default="scope"><UiStatusTag :value="statusLabels[text(scope.row, 'application_status')] || text(scope.row, 'application_status')" :tone="statusTone(text(scope.row, 'application_status'))" /></template></el-table-column><el-table-column label="投产结果" width="110"><template #default="scope">{{ resultLabels[text(scope.row, 'production_result')] || text(scope.row, 'production_result') }}</template></el-table-column><el-table-column label="准出时间" min-width="148"><template #default="scope">{{ minute(scope.row.approved_at) }}</template></el-table-column><template #footer><div class="release-table-footer"><span>共 {{ drillTotal }} 张申请</span><el-pagination :current-page="drillPage" :page-size="drillPageSize" :total="drillTotal" layout="prev, pager, next" @update:current-page="changePage" /></div></template></UiDataTable></section>
    </div>
  </div>
</template>
