<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, CircleCheckFilled, CircleCloseFilled, InfoFilled, Refresh, Right, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getNotificationModules, getNotifications, getNotificationUnreadCount, markAllNotificationsRead, markNotificationRead } from '../../api/notifications'
import { apiErrorMessage } from '../../api/error'
import type { NotificationLevel, NotificationModuleSummary, SystemNotification } from '../../types/notification'
import UiEmptyState from './UiEmptyState.vue'

const router = useRouter()
const open = ref(false)
const activeView = ref<'all' | 'unread'>('all')
const items = ref<SystemNotification[]>([])
const total = ref(0)
const page = ref(1)
const unreadCount = ref(0)
const moduleOptions = ref<NotificationModuleSummary[]>([])
const selectedModuleCode = ref('')
const loading = ref(false)
const loadingMore = ref(false)
const failed = ref(false)
const modulesLoading = ref(false)
const modulesFailed = ref(false)
const markingAll = ref(false)
let pollTimer: number | null = null
let unreadCountRequest: Promise<UnreadRefreshResult> | null = null
let pollingCycle: Promise<void> | null = null
let notifyCountFailure = false
let pollFailureCount = 0
let suppressPollingDrawerRefresh = 0
let disposed = false
let listRequestVersion = 0
let moduleRequestVersion = 0
const POLL_INTERVAL_MS = 1_000
const POLL_FAILURE_INTERVALS_MS = [2_000, 5_000, 15_000]

interface UnreadRefreshResult {
  success: boolean
  changed: boolean
}

const hasMore = computed(() => items.value.length < total.value)
const badgeValue = computed(() => unreadCount.value > 99 ? '99+' : unreadCount.value)
const scopedUnreadCount = computed(() => {
  if (!selectedModuleCode.value) return unreadCount.value
  return moduleOptions.value.find(item => item.moduleCode === selectedModuleCode.value)?.unreadCount ?? 0
})

const levelMeta: Record<NotificationLevel, { label: string; icon: typeof InfoFilled }> = {
  INFO: { label: '消息', icon: InfoFilled },
  SUCCESS: { label: '成功', icon: CircleCheckFilled },
  WARNING: { label: '提醒', icon: WarningFilled },
  ERROR: { label: '异常', icon: CircleCloseFilled }
}

function refreshUnreadCount(silent = true) {
  if (!silent) notifyCountFailure = true
  if (unreadCountRequest) return unreadCountRequest
  unreadCountRequest = (async (): Promise<UnreadRefreshResult> => {
    try {
      const nextCount = (await getNotificationUnreadCount()).data.data.count
      const changed = nextCount !== unreadCount.value
      unreadCount.value = nextCount
      return { success: true, changed }
    } catch (error) {
      if (notifyCountFailure) ElMessage.error(apiErrorMessage(error, '未读消息数量加载失败'))
      return { success: false, changed: false }
    } finally {
      notifyCountFailure = false
      unreadCountRequest = null
    }
  })()
  return unreadCountRequest
}

function clearPollTimer() {
  if (pollTimer === null) return
  window.clearTimeout(pollTimer)
  pollTimer = null
}

function schedulePoll(delay: number) {
  clearPollTimer()
  if (disposed || document.visibilityState !== 'visible') return
  pollTimer = window.setTimeout(() => {
    pollTimer = null
    void runPollingCycle()
  }, delay)
}

function failurePollDelay() {
  return POLL_FAILURE_INTERVALS_MS[Math.min(Math.max(pollFailureCount - 1, 0), POLL_FAILURE_INTERVALS_MS.length - 1)]
}

function runPollingCycle() {
  if (disposed || document.visibilityState !== 'visible') return Promise.resolve()
  clearPollTimer()
  if (pollingCycle) return pollingCycle
  pollingCycle = (async () => {
    const result = await refreshUnreadCount()
    if (disposed || document.visibilityState !== 'visible') return
    if (result.success) {
      pollFailureCount = 0
      if (result.changed && open.value && suppressPollingDrawerRefresh === 0) {
        await Promise.all([loadNotifications(), loadModules()])
      }
      schedulePoll(POLL_INTERVAL_MS)
    } else {
      pollFailureCount++
      schedulePoll(failurePollDelay())
    }
  })().finally(() => {
    pollingCycle = null
  })
  return pollingCycle
}

function restartPolling() {
  pollFailureCount = 0
  clearPollTimer()
  return runPollingCycle()
}

async function loadModules(silent = true) {
  const requestVersion = ++moduleRequestVersion
  modulesLoading.value = true
  try {
    const result = (await getNotificationModules()).data.data
    if (requestVersion !== moduleRequestVersion) return
    moduleOptions.value = result
    modulesFailed.value = false
  } catch (error) {
    if (requestVersion !== moduleRequestVersion) return
    modulesFailed.value = true
    if (!silent) ElMessage.error(apiErrorMessage(error, '业务板块加载失败'))
  } finally {
    if (requestVersion === moduleRequestVersion) modulesLoading.value = false
  }
}

async function loadNotifications(reset = true) {
  const requestVersion = ++listRequestVersion
  const moduleCode = selectedModuleCode.value
  const unreadOnly = activeView.value === 'unread'
  if (reset) {
    loading.value = true
    page.value = 1
    items.value = []
    total.value = 0
    failed.value = false
  } else {
    loadingMore.value = true
  }
  try {
    const nextPage = reset ? 1 : page.value + 1
    const result = (await getNotifications(nextPage, 20, unreadOnly, moduleCode)).data.data
    if (requestVersion !== listRequestVersion) return
    items.value = reset ? result.records : [...items.value, ...result.records]
    total.value = result.total
    page.value = result.page
    failed.value = false
  } catch (error) {
    if (requestVersion !== listRequestVersion) return
    failed.value = true
    if (!reset) ElMessage.error(apiErrorMessage(error, '更多消息加载失败'))
  } finally {
    if (requestVersion === listRequestVersion) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

async function showCenter() {
  open.value = true
  pollFailureCount = 0
  clearPollTimer()
  suppressPollingDrawerRefresh++
  const countRequest = refreshUnreadCount(false)
  try {
    await Promise.all([loadNotifications(), countRequest, loadModules(false)])
  } finally {
    suppressPollingDrawerRefresh--
  }
  const countResult = await countRequest
  pollFailureCount = countResult.success ? 0 : 1
  schedulePoll(countResult.success ? POLL_INTERVAL_MS : failurePollDelay())
}

async function readNotification(item: SystemNotification) {
  if (!item.read) {
    try {
      await markNotificationRead(item.id)
      item.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
      if (activeView.value === 'unread') {
        items.value = items.value.filter(current => current.id !== item.id)
        total.value = Math.max(0, total.value - 1)
      }
      await Promise.all([refreshUnreadCount(), loadModules()])
    } catch (error) {
      ElMessage.error(apiErrorMessage(error, '消息状态更新失败'))
      return
    }
  }
  if (item.actionPath) {
    open.value = false
    try {
      await router.push(item.actionPath)
    } catch {
      ElMessage.error('消息关联页面暂不可达')
    }
  }
}

async function readAll() {
  if (!unreadCount.value) return
  markingAll.value = true
  try {
    await markAllNotificationsRead()
    await Promise.all([refreshUnreadCount(false), loadModules(false), loadNotifications()])
    ElMessage.success('全部消息已标记为已读')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '全部已读操作失败'))
  } finally {
    markingAll.value = false
  }
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}

watch([activeView, selectedModuleCode], () => {
  if (open.value) void loadNotifications()
})

function refreshOnFocus() {
  void restartPolling()
}

function refreshOnVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    clearPollTimer()
    return
  }
  void restartPolling()
}

onMounted(() => {
  disposed = false
  void restartPolling()
  window.addEventListener('focus', refreshOnFocus)
  document.addEventListener('visibilitychange', refreshOnVisibilityChange)
})

onBeforeUnmount(() => {
  disposed = true
  clearPollTimer()
  window.removeEventListener('focus', refreshOnFocus)
  document.removeEventListener('visibilitychange', refreshOnVisibilityChange)
})
</script>

<template>
  <div class="notification-center">
    <el-tooltip content="消息通知" placement="bottom">
      <el-badge :value="badgeValue" :hidden="unreadCount === 0" :max="99" class="notification-trigger__badge">
        <el-button class="notification-trigger" text circle aria-label="打开消息通知" title="消息通知" @click="showCenter">
          <el-icon :size="18"><Bell /></el-icon>
        </el-button>
      </el-badge>
    </el-tooltip>

    <el-drawer v-model="open" size="min(420px, 94vw)" class="notification-center-drawer" :show-close="true">
      <template #header>
        <div class="notification-drawer__header">
          <div><strong>消息通知</strong><span>{{ unreadCount }} 条未读</span></div>
          <el-button text :loading="markingAll" :disabled="unreadCount === 0" @click="readAll">全部已读</el-button>
        </div>
      </template>

      <el-tabs v-model="activeView" class="notification-tabs">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane :label="`未读 ${scopedUnreadCount}`" name="unread" />
      </el-tabs>

      <div class="notification-filter">
        <el-select
          v-model="selectedModuleCode"
          class="notification-filter__select"
          filterable
          clearable
          :loading="modulesLoading"
          placeholder="全部业务板块"
          aria-label="按业务板块筛选通知"
        >
          <el-option label="全部业务板块" value="" />
          <el-option
            v-for="module in moduleOptions"
            :key="module.moduleCode"
            :label="`${module.moduleName}（${module.totalCount}）`"
            :value="module.moduleCode"
          />
        </el-select>
        <span v-if="modulesFailed" class="notification-filter__error">
          业务板块加载失败
          <el-button link type="primary" :icon="Refresh" @click="loadModules(false)">重试</el-button>
        </span>
      </div>

      <div v-loading="loading" class="notification-list" aria-live="polite">
        <div v-if="failed && !items.length" class="notification-failure">
          <UiEmptyState title="消息加载失败" description="请检查网络或服务状态后重试。">
            <template #action><el-button :icon="Refresh" @click="loadNotifications()">重新加载</el-button></template>
          </UiEmptyState>
        </div>
        <UiEmptyState v-else-if="!loading && !items.length" :title="activeView === 'unread' ? '没有未读消息' : '暂无消息'" :description="activeView === 'unread' ? '当前消息都已处理。' : '业务通知将在这里集中展示。'" />
        <button v-for="item in items" v-else :key="item.id" type="button" class="notification-item" :class="[`is-${item.level.toLowerCase()}`, { 'is-unread': !item.read }]" @click="readNotification(item)">
          <span class="notification-item__icon"><el-icon :size="18"><component :is="levelMeta[item.level].icon" /></el-icon></span>
          <span class="notification-item__body">
            <span class="notification-item__meta"><b>{{ item.moduleName }} · {{ item.sourceName }}</b><time>{{ formatTime(item.createdAt) }}</time></span>
            <strong>{{ item.title }}</strong>
            <span class="notification-item__content">{{ item.content }}</span>
            <small>{{ levelMeta[item.level].label }} · {{ item.businessKey }}</small>
          </span>
          <span v-if="item.actionPath" class="notification-item__action"><el-icon><Right /></el-icon></span>
        </button>
        <div v-if="hasMore" class="notification-list__more">
          <el-button text :loading="loadingMore" @click="loadNotifications(false)">加载更多</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>
