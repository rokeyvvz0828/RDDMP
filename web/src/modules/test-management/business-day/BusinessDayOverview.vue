<!--
文件：web/src/modules/test-management/business-day/BusinessDayOverview.vue
说明：营业日管理的月历总览视图，按环境与月份聚合日历安排。
用途：展示固定 42 格月历、当日详情、月份切换、今天定位和视图链接复制。
作者：hengguan
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Share } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import type { Environment, Schedule, Theme } from '../api'
import { listEnvironmentOptions, listOverview } from '../api'

const today = localDate(new Date())
const month = ref(today.slice(0, 7))
const environments = ref<Environment[]>([])
const schedules = ref<Schedule[]>([])
const visibleEnvironments = ref<string[]>([])
const selectedDay = ref<CalendarDay | null>(null)
const loading = ref(false)
const error = ref('')
const weekdays = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日']
const shortWeekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

interface CalendarDay {
  key: string
  date: Date
  currentMonth: boolean
  records: Schedule[]
}

// 关键逻辑：以目标月份首日所在周为起点，始终生成 6 周共 42 个格子，避免跨月时布局跳动。
const calendarDays = computed(() => {
  const [year, value] = month.value.split('-').map(Number)
  const first = new Date(year, value - 1, 1)
  const mondayOffset = (first.getDay() + 6) % 7
  const start = new Date(year, value - 1, 1 - mondayOffset)
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start); date.setDate(start.getDate() + index)
    const key = localDate(date)
    const records = schedules.value.filter(item => normalizeDate(item.natural_date) === key && environmentVisible(item.env_code))
    return { key, date, currentMonth: date.getMonth() === value - 1, records }
  })
})
const enabledEnvironments = computed(() => environments.value)
const detailOpen = computed({
  get: () => selectedDay.value !== null,
  set: value => { if (!value) selectedDay.value = null }
})

// 关键逻辑：概览只请求当前租户可见的月范围数据；失败后保留筛选条件并提供显式重试。
async function load() {
  loading.value = true; error.value = ''
  try { schedules.value = (await listOverview({ month: month.value })).data.data }
  catch (reason) { error.value = apiErrorMessage(reason, '日历概览加载失败，请稍后重试') }
  finally { loading.value = false }
}

async function loadEnvironments() {
  try { environments.value = (await listEnvironmentOptions()).data.data }
  catch (reason) { error.value = apiErrorMessage(reason, '测试环境加载失败，请稍后重试') }
}

function locateToday() { month.value = today.slice(0, 7) }
async function copyLink() {
  await navigator.clipboard.writeText(window.location.href)
  ElMessage.success('日历概览地址已复制')
}
// 关键逻辑：空筛选表示全部环境；首次点击按 RADAR 语义隐藏该环境，后续按钮逐项切换显示状态。
function toggleEnvironment(code: string) {
  if (!visibleEnvironments.value.length) {
    visibleEnvironments.value = enabledEnvironments.value.filter(item => item.env_code !== code).map(item => item.env_code)
  } else if (visibleEnvironments.value.includes(code)) {
    visibleEnvironments.value = visibleEnvironments.value.filter(item => item !== code)
  } else {
    visibleEnvironments.value = [...visibleEnvironments.value, code]
  }
  selectedDay.value = null
}
function environmentVisible(code: string) { return !visibleEnvironments.value.length || visibleEnvironments.value.includes(code) }
function environmentName(item: Schedule) { return item.env_name || environments.value.find(env => env.env_code === item.env_code)?.env_name || item.env_code }
function themeColor(theme?: Theme) {
  return ({ brand: 'var(--brand)', success: 'var(--success)', warning: 'var(--warning)', danger: 'var(--danger)', accent: 'var(--accent)' } as Record<string, string>)[theme || 'brand'] || 'var(--brand)'
}
function environmentStyle(item: Schedule | Environment) { return { '--business-day-environment-color': themeColor(item.theme) } }
function monthDay(value: Date) { return `${value.getMonth() + 1}-${value.getDate()}` }
function naturalWeekday(value: Date) { return shortWeekdays[value.getDay()] }
function businessWeekday(value: unknown) {
  const match = /^(\d{4})(\d{2})(\d{2})$/.exec(String(value || ''))
  return match ? shortWeekdays[new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])).getDay()] : '—'
}
function localDate(value: Date) {
  const year = value.getFullYear(); const month = String(value.getMonth() + 1).padStart(2, '0'); const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
function normalizeDate(value: unknown) { return String(value || '').slice(0, 10) }
function truthy(value: unknown) { return value === true || value === 1 || value === '1' || value === 'true' }

watch(month, () => { selectedDay.value = null; void load() })
onMounted(async () => { await loadEnvironments(); await load() })
</script>

<template>
  <div class="business-day-overview">
    <section class="business-day-calendar-panel" v-loading="loading">
      <header class="business-day-calendar-toolbar">
        <div class="business-day-calendar-period">
          <el-date-picker v-model="month" type="month" value-format="YYYY-MM" format="YYYY年MM月" :clearable="false" aria-label="选择日历月份" />
          <el-button @click="locateToday">今天</el-button>
        </div>
        <div class="business-day-calendar-actions">
          <div class="business-day-calendar-environments" aria-label="环境筛选">
            <el-button v-for="item in enabledEnvironments" :key="item.id" size="small" :class="{ 'is-active': environmentVisible(item.env_code) }" :style="environmentVisible(item.env_code) ? environmentStyle(item) : undefined" :aria-pressed="environmentVisible(item.env_code)" @click="toggleEnvironment(item.env_code)">{{ item.env_name }}</el-button>
          </div>
          <el-button @click="copyLink"><el-icon><Share /></el-icon>分享</el-button>
        </div>
      </header>

      <UiEmptyState v-if="error" title="日历加载失败" :description="error"><template #action><el-button type="primary" @click="load">重新加载</el-button></template></UiEmptyState>
      <UiEmptyState v-else-if="!loading && !schedules.length" title="本月尚未导入日历安排" description="请前往日历安排下载模板并导入。" />
      <div v-else class="business-day-calendar-frame">
        <div class="business-day-calendar">
          <div class="business-day-weekdays"><span v-for="day in weekdays" :key="day">{{ day }}</span></div>
          <div class="business-day-calendar-grid">
            <article v-for="day in calendarDays" :key="day.key" class="business-day-calendar-cell" :class="{ 'is-other-month': !day.currentMonth }">
              <header><strong>{{ monthDay(day.date) }}</strong><span>{{ naturalWeekday(day.date) }}</span></header>
              <div class="business-day-calendar-events">
                <button v-for="item in day.records" :key="item.id" type="button" :class="{ 'has-batch': truthy(item.has_batch) }" :style="environmentStyle(item)" :aria-label="`${environmentName(item)} ${item.business_date}，查看当日日历安排`" @click="selectedDay = day">
                  <span class="business-day-calendar-event-line"><b>{{ environmentName(item) }}</b><em>{{ item.business_date }}</em><small>{{ businessWeekday(item.business_date) }}</small></span>
                  <span v-if="truthy(item.has_batch)" class="business-day-calendar-batch-line"><el-tag size="small">{{ item.batch_type || '跑批' }}</el-tag><span v-if="item.validation_content">{{ item.validation_content }}</span></span>
                </button>
              </div>
            </article>
          </div>
        </div>
      </div>
    </section>

    <el-dialog v-model="detailOpen" title="当日日历安排" width="min(820px, calc(100vw - 24px))" top="5vh" class="business-day-calendar-detail" destroy-on-close>
      <section class="business-day-calendar-detail-section">
        <h3>自然日信息</h3>
        <div class="business-day-calendar-day-title"><span>自然日期</span><strong>{{ selectedDay?.key }}</strong><el-tag>{{ selectedDay ? naturalWeekday(selectedDay.date) : '—' }}</el-tag></div>
      </section>
      <section class="business-day-calendar-detail-section">
        <h3>营业日及批处理信息</h3>
        <div class="business-day-calendar-detail-list">
          <article v-for="item in selectedDay?.records || []" :key="item.id" :style="environmentStyle(item)">
            <header><span><i aria-hidden="true" /><strong>{{ environmentName(item) }}</strong></span><strong>{{ item.business_date }}</strong><small>{{ businessWeekday(item.business_date) }}</small></header>
            <div v-if="truthy(item.has_batch)" class="business-day-calendar-detail-batch">
              <div><el-tag size="small">{{ item.batch_type || '跑批' }}</el-tag><span v-if="item.validation_content">{{ item.validation_content }}</span></div>
              <p v-if="item.batch_time || item.systems?.length"><span>{{ item.batch_time?.slice(0, 5) || '—' }}</span><small v-if="item.systems?.length">{{ item.systems.join('、') }}</small></p>
            </div>
          </article>
        </div>
      </section>
      <template #footer><el-button type="primary" @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
