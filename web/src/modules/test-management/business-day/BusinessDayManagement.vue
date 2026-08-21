<!--
文件：web/src/modules/test-management/business-day/BusinessDayManagement.vue
说明：营业日管理单一 Sidebar 入口的页面装配组件。
用途：使用 URL 查询参数承载四个顶部视图，并按当前视图装配业务子页面。
作者：hengguan
-->
<script setup lang="ts">
import { computed } from 'vue'
import { Calendar, Calendar as CalendarIcon, Collection, Monitor } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import UiPageHeader from '../../../components/ui/UiPageHeader.vue'
import BusinessDayOverview from './BusinessDayOverview.vue'
import CalendarScheduleList from './CalendarScheduleList.vue'
import BatchRequirementList from './BatchRequirementList.vue'
import TestEnvironmentList from './TestEnvironmentList.vue'
import './business-day.css'

type ViewKey = 'overview' | 'schedule' | 'requirements' | 'environments'
const views: Array<{ key: ViewKey; label: string; description: string; icon: typeof Calendar }> = [
  { key: 'overview', label: '日历概览', description: '按月查看各测试环境的营业日与跑批分布', icon: CalendarIcon },
  { key: 'schedule', label: '日历安排', description: '维护自然日、营业日和跑批安排', icon: Collection },
  { key: 'requirements', label: '跑批需求', description: '登记并评审测试跑批需求', icon: Calendar },
  { key: 'environments', label: '测试环境管理', description: '维护营业日日历使用的测试环境', icon: Monitor }
]
const route = useRoute()
const router = useRouter()
const activeView = computed<ViewKey>(() => {
  const value = String(route.query.view || 'overview')
  return views.some(item => item.key === value) ? value as ViewKey : 'overview'
})
const current = computed(() => views.find(item => item.key === activeView.value) || views[0])

// 关键逻辑：顶部视图写入 URL，保证刷新、前进后退和复制链接后仍能还原当前位置。
function navigate(view: ViewKey) {
  if (view === activeView.value) return
  void router.replace({ path: '/test-management/business-day', query: view === 'overview' ? {} : { view } })
}
</script>

<template>
  <section class="business-day-page">
    <UiPageHeader eyebrow="测试管理" title="营业日管理" :description="current.description" />
    <nav class="business-day-nav" aria-label="营业日管理视图">
      <button v-for="view in views" :key="view.key" type="button" :class="{ active: activeView === view.key }" :aria-current="activeView === view.key ? 'page' : undefined" @click="navigate(view.key)">
        <el-icon><component :is="view.icon" /></el-icon><span>{{ view.label }}</span>
      </button>
    </nav>
    <BusinessDayOverview v-if="activeView === 'overview'" />
    <CalendarScheduleList v-else-if="activeView === 'schedule'" />
    <BatchRequirementList v-else-if="activeView === 'requirements'" />
    <TestEnvironmentList v-else />
  </section>
</template>
