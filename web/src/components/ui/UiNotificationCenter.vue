<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, CircleCheckFilled, CircleCloseFilled, InfoFilled, Refresh, Right, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getNotifications, getNotificationUnreadCount, markAllNotificationsRead, markNotificationRead } from '../../api/notifications'
import { apiErrorMessage } from '../../api/error'
import type { NotificationLevel, SystemNotification } from '../../types/notification'
import UiEmptyState from './UiEmptyState.vue'

const router = useRouter()
const open = ref(false)
const activeView = ref<'all' | 'unread'>('all')
const items = ref<SystemNotification[]>([])
const total = ref(0)
const page = ref(1)
const unreadCount = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const failed = ref(false)
const markingAll = ref(false)
let pollTimer: number | null = null

const hasMore = computed(() => items.value.length < total.value)
const badgeValue = computed(() => unreadCount.value > 99 ? '99+' : unreadCount.value)

const levelMeta: Record<NotificationLevel, { label: string; icon: typeof InfoFilled }> = {
  INFO: { label: '消息', icon: InfoFilled },
  SUCCESS: { label: '成功', icon: CircleCheckFilled },
  WARNING: { label: '提醒', icon: WarningFilled },
  ERROR: { label: '异常', icon: CircleCloseFilled }
}

async function refreshUnreadCount(silent = true) {
  try {
    unreadCount.value = (await getNotificationUnreadCount()).data.data.count
  } catch (error) {
    if (!silent) ElMessage.error(apiErrorMessage(error, '未读消息数量加载失败'))
  }
}

async function loadNotifications(reset = true) {
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
    const result = (await getNotifications(nextPage, 20, activeView.value === 'unread')).data.data
    items.value = reset ? result.records : [...items.value, ...result.records]
    total.value = result.total
    page.value = result.page
    failed.value = false
  } catch (error) {
    failed.value = true
    if (!reset) ElMessage.error(apiErrorMessage(error, '更多消息加载失败'))
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

async function showCenter() {
  open.value = true
  await Promise.all([loadNotifications(), refreshUnreadCount(false)])
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
    unreadCount.value = 0
    await loadNotifications()
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

watch(activeView, () => {
  if (open.value) void loadNotifications()
})

onMounted(() => {
  void refreshUnreadCount()
  pollTimer = window.setInterval(() => void refreshUnreadCount(), 60_000)
})

onBeforeUnmount(() => {
  if (pollTimer !== null) window.clearInterval(pollTimer)
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
        <el-tab-pane :label="`未读 ${unreadCount}`" name="unread" />
      </el-tabs>

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
            <span class="notification-item__meta"><b>{{ item.sourceName }}</b><time>{{ formatTime(item.createdAt) }}</time></span>
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
