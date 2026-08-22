<!--
  用途：数迁整体看板页
  说明：展示项目数、组件数、活动资产数三项核心统计指标；调用 getDataMigrationDashboard('overall')
        拉取汇总数据，覆盖加载/空/失败/无权限状态，指标卡片使用语义主题变量，移动端单列排列。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import { apiErrorMessage } from '../../../../api/error'
import { getDataMigrationDashboard } from '../../../../api/data-migration'

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const metrics = ref<Record<string, unknown>>({})

function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}

async function load() {
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const data = (await getDataMigrationDashboard('overall')).data.data ?? {}
    metrics.value = Array.isArray(data) ? {} : data
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '整体看板数据加载失败')
  } finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <main class="dm-page-root">
    <UiPageHeader title="整体看板" description="按项目维度展示数据迁移资产核心统计指标。">
      <template #actions><el-button :disabled="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template>
    </UiPageHeader>

    <section v-if="forbidden" class="dm-state-panel"><el-result icon="warning" title="暂无整体看板查看权限" sub-title="请向数据迁移管理员申请 data-migration:dashboard 权限。" /></section>
    <section v-else-if="error" class="dm-state-panel"><el-result icon="error" title="整体看板加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <section v-else v-loading="loading" class="dm-dashboard-grid">
      <article class="dm-metric-card"><span>项目</span><strong>{{ metrics.projects ?? 0 }}</strong></article>
      <article class="dm-metric-card"><span>组件</span><strong>{{ metrics.components ?? 0 }}</strong></article>
      <article class="dm-metric-card"><span>活动资产</span><strong>{{ metrics.assets ?? 0 }}</strong></article>
    </section>
  </main>
</template>

<style scoped>
.dm-dashboard-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.dm-metric-card { min-height: 148px; padding: 20px; display: flex; flex-direction: column; gap: 10px; border: 1px solid var(--line); border-top: 3px solid var(--brand); border-radius: 6px; background: var(--panel-bg); box-shadow: var(--shadow); }
.dm-metric-card span { color: var(--muted); font-size: 13px; }
.dm-metric-card strong { color: var(--text); font-size: 32px; font-weight: 600; }
@media (max-width: 640px) { .dm-dashboard-grid { grid-template-columns: 1fr; } }
</style>
