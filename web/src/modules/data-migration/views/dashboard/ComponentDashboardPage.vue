<!--
  用途：数迁组件看板页
  说明：按组件维度展示资产数量统计（系统编号/系统名称/资产数量，组件身份即物理子系统）；
        调用 getDataMigrationDashboard('component') 拉取数据，使用 UiPageHeader + UiDataTable，
        覆盖加载/空/失败/无权限状态。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import { apiErrorMessage } from '../../../../api/error'
import { getDataMigrationDashboard } from '../../../../api/data-migration'

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const rows = ref<Array<Record<string, unknown>>>([])

function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}

async function load() {
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const data = (await getDataMigrationDashboard('component')).data.data ?? []
    rows.value = Array.isArray(data) ? data : []
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '组件看板数据加载失败')
  } finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <main class="dm-page-root">
    <UiPageHeader title="组件看板" description="按组件维度展示数据迁移资产数量统计。">
      <template #actions><el-button :disabled="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template>
    </UiPageHeader>

    <section v-if="forbidden" class="dm-state-panel"><el-result icon="warning" title="暂无组件看板查看权限" sub-title="请向数据迁移管理员申请 data-migration:dashboard 权限。" /></section>
    <section v-else-if="error" class="dm-state-panel"><el-result icon="error" title="组件看板加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiDataTable v-if="rows.length || loading" :data="rows" :loading="loading" border empty-text="暂无组件数据">
        <el-table-column label="系统编号" min-width="150"><template #default="scope">{{ scope.row.system_code }}</template></el-table-column>
        <el-table-column label="系统名称" min-width="180"><template #default="scope">{{ scope.row.system_name }}</template></el-table-column>
        <el-table-column label="资产数量" min-width="110"><template #default="scope">{{ scope.row.asset_count }}</template></el-table-column>
      </UiDataTable>
      <UiEmptyState v-if="!loading && !rows.length" title="暂无组件数据" description="按组件维度暂无可展示的资产统计。" />
    </template>
  </main>
</template>
