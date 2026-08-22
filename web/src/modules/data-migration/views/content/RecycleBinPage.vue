<!--
  用途：数迁资产内容 - 回收站页
  说明：展示已移入回收站的文件资产，支持关键词查询、批量恢复、彻底清理（不可恢复）；
        使用 UiPageHeader + UiToolbar + UiDataTable，覆盖加载/空/失败/无权限/提交中状态，
        彻底清理为危险操作，需确认后执行。
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import { listDataMigrationRecycleBin, purgeDataMigrationAssets, restoreDataMigrationAssets, type DataMigrationAsset } from '../../../../api/data-migration'

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const assets = ref<DataMigrationAsset[]>([])
const keyword = ref('')
const selectedIds = ref<number[]>([])
const actionBusy = ref(false)

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}

function cancelled(error: unknown) {
  const action = (error as { action?: string }).action
  return action === 'cancel' || action === 'close'
}

async function load() {
  loading.value = true
  error.value = ''
  forbidden.value = false
  selectedIds.value = []
  try {
    assets.value = (await listDataMigrationRecycleBin({ keyword: keyword.value || undefined })).data.data ?? []
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '回收站列表加载失败')
  } finally { loading.value = false }
}

function onSelectionChange(rows: DataMigrationAsset[]) {
  selectedIds.value = rows.map(row => row.id)
}

async function restoreSelected() {
  if (!selectedIds.value.length) return
  actionBusy.value = true
  try {
    await restoreDataMigrationAssets(selectedIds.value)
    ElMessage.success('已恢复')
    await load()
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally { actionBusy.value = false }
}

async function purgeSelected() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认彻底清理选中的 ${selectedIds.value.length} 个资产吗？清理后将删除业务记录及文件且不可恢复，审计记录仍会保留。`, '彻底清理', { type: 'warning', confirmButtonText: '确认清理', cancelButtonText: '取消' })
    actionBusy.value = true
    await purgeDataMigrationAssets(selectedIds.value)
    ElMessage.success('已彻底清理')
    await load()
  } catch (e) {
    if (!cancelled(e)) ElMessage.error(messageOf(e))
  } finally { actionBusy.value = false }
}

onMounted(load)
</script>

<template>
  <main class="recycle-bin-page">
    <UiPageHeader title="回收站" description="管理已移入回收站的文件资产，可恢复或彻底清理。">
      <template #actions><el-button :disabled="loading || actionBusy" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template>
    </UiPageHeader>

    <section v-if="forbidden" class="dm-state-panel"><el-result icon="warning" title="暂无回收站查看权限" sub-title="请向数据迁移管理员申请回收站管理权限。" /></section>
    <section v-else-if="error" class="dm-state-panel"><el-result icon="error" title="回收站加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="keyword" clearable placeholder="搜索编号或名称" style="width: 240px" @keyup.enter="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="!selectedIds.length || actionBusy" @click="restoreSelected">恢复 ({{ selectedIds.length }})</el-button>
          <el-button type="danger" plain :disabled="!selectedIds.length || actionBusy" @click="purgeSelected"><el-icon><Delete /></el-icon>彻底清理 ({{ selectedIds.length }})</el-button>
        </template>
      </UiToolbar>

      <UiDataTable v-if="assets.length || loading" :data="assets" :loading="loading" row-key="id" border empty-text="回收站暂无资产" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="asset_code" label="资产编码" min-width="150" />
        <el-table-column prop="asset_name" label="名称" min-width="180" />
        <el-table-column prop="asset_type" label="类型" min-width="110" />
      </UiDataTable>
      <UiEmptyState v-if="!loading && !assets.length" title="回收站暂无资产" description="删除的文件资产会移入回收站，可在此恢复或彻底清理。" />
    </template>
  </main>
</template>

<style scoped>
.recycle-bin-page { min-width: 0; }
.dm-state-panel { padding: 24px; background: var(--panel-bg); border: 1px solid var(--line); border-radius: 6px; }
</style>
