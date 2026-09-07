<!--
  用途：数迁整体看板页
  说明：展示可访问项目数、当前项目组件数与活动资产数三项核心统计指标；调用 getDataMigrationDashboard('overall', projectId)
        拉取汇总数据，覆盖加载/空/失败/无权限状态，指标卡片使用语义主题变量，移动端单列排列。
        T32：看板按项目隔离，组件/活动资产均为当前项目内实时计数；项目不可用时不取数。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import { apiErrorMessage } from '../../../../api/error'
import { getDataMigrationDashboard } from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'

const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const metrics = ref<Record<string, unknown>>({})

function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}

async function load() {
  const projectId = scopeProjectId.value
  if (projectId == null) {
    metrics.value = {}
    return
  }
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const data = (await getDataMigrationDashboard('overall', projectId)).data.data ?? {}
    metrics.value = Array.isArray(data) ? {} : data
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '整体看板数据加载失败')
  } finally { loading.value = false }
}

onMounted(() => { void scope.ensureLoaded() })

// 全局项目变化：丢弃上一个项目的指标，按新项目重查。
watch(scopeProjectId, () => {
  metrics.value = {}
  error.value = ''
  forbidden.value = false
  void load()
}, { immediate: true })
</script>

<template>
  <main class="dm-page-root">
    <UiPageHeader title="整体看板" description="组件与活动资产按顶部项目切换器选中的当前项目统计；项目卡片展示当前账号可访问的项目数。">
      <template #actions><el-button :disabled="loading || scopeState !== 'ready'" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template>
    </UiPageHeader>

    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <section v-else-if="forbidden" class="dm-state-panel"><el-result icon="warning" title="暂无整体看板查看权限" sub-title="请向数据迁移管理员申请 data-migration:dashboard 权限。" /></section>
    <section v-else-if="error" class="dm-state-panel"><el-result icon="error" title="整体看板加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <section v-else v-loading="loading" class="dm-dashboard-grid">
      <article class="dm-metric-card"><span>可访问项目</span><strong>{{ metrics.projects ?? 0 }}</strong></article>
      <article class="dm-metric-card"><span>本项目组件</span><strong>{{ metrics.components ?? 0 }}</strong></article>
      <article class="dm-metric-card"><span>本项目活动资产</span><strong>{{ metrics.assets ?? 0 }}</strong></article>
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
