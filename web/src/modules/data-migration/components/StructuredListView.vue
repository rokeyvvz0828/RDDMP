<!--
  用途：结构化资产列表视图（结构化数据通用列表组件）
  说明：按结构化类型展示字段型资产（如目标表结构、中间表结构等），支持关键词查询、
        批量删除（逻辑删除，进入回收站）、Excel 导入/导出、以及抽屉表单编辑字段内容；
        接收 structuredType（结构化类型）与 pageTitle（页面标题）两个 props，由各内容页复用。
        所属项目唯一取自全局项目上下文且不在页面展示：T32 起列表/导入/导出端点均强制携带 projectId，
        项目隔离由服务端 SQL 保证，前端不再做任何项目过滤；项目切换后重置查询状态重查。
        视觉与交互对齐 AssetListView：UiToolbar + UiDataTable + UiFormDrawer，
        覆盖加载/空/失败/无权限/提交中状态；基础资料子页面不展示标题横幅，定位依赖顶部 Tabs。
-->
<script setup lang="ts">
import '../data-migration.css'
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Edit, Search, UploadFilled } from '@element-plus/icons-vue'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../api/error'
import {
  deleteDataMigrationStructured,
  exportDataMigrationStructured,
  inspectDataMigrationStructuredImport,
  listDataMigrationStructured,
  updateDataMigrationStructured,
  type DataMigrationAsset
} from '../../../api/data-migration'
import { useProjectScope } from '../composables/useProjectScope'
import ProjectScopeState from './ProjectScopeState.vue'

const props = defineProps<{ structuredType: string; pageTitle: string }>()

const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const assets = ref<DataMigrationAsset[]>([])
const keyword = ref('')
const selectedIds = ref<number[]>([])
const actionBusy = ref(false)
const importResult = ref('')
const structuredInput = ref<HTMLInputElement | null>(null)

const drawerOpen = ref(false)
const saving = ref(false)
const editingAsset = ref<DataMigrationAsset | null>(null)
const editName = ref('')
const editJson = ref('')

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
  const projectId = scopeProjectId.value
  if (projectId == null) {
    assets.value = []
    selectedIds.value = []
    return
  }
  loading.value = true
  error.value = ''
  forbidden.value = false
  selectedIds.value = []
  try {
    const rows = (await listDataMigrationStructured(props.structuredType, { projectId, keyword: keyword.value || undefined })).data.data ?? []
    assets.value = rows
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '列表加载失败')
  } finally { loading.value = false }
}

function onSelectionChange(rows: DataMigrationAsset[]) {
  selectedIds.value = rows.map(row => row.id)
}

async function removeSelected() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认将选中的 ${selectedIds.value.length} 个${props.pageTitle}删除吗？删除后进入回收站。`, '删除', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    actionBusy.value = true
    await deleteDataMigrationStructured(props.structuredType, selectedIds.value)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (!cancelled(e)) ElMessage.error(messageOf(e))
  } finally { actionBusy.value = false }
}

function chooseStructuredImport() { structuredInput.value?.click() }

async function exportStructured() {
  if (scopeProjectId.value == null) {
    ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目')
    return
  }
  actionBusy.value = true
  try {
    const response = await exportDataMigrationStructured(props.structuredType, { projectId: scopeProjectId.value, keyword: keyword.value || undefined })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `data-migration-${props.structuredType}.xlsx`
    anchor.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally { actionBusy.value = false }
}

function openEdit(asset: DataMigrationAsset) {
  editingAsset.value = asset
  editName.value = asset.asset_name
  editJson.value = typeof asset.structured_data === 'string' ? asset.structured_data : JSON.stringify(asset.structured_data ?? {}, null, 2)
  drawerOpen.value = true
}

async function saveEdit() {
  const asset = editingAsset.value
  if (!asset) return
  if (!editName.value.trim()) {
    ElMessage.warning('请填写资产名称')
    return
  }
  let structuredData: unknown
  try { structuredData = JSON.parse(editJson.value) } catch { ElMessage.warning('字段 JSON 格式无效'); return }
  saving.value = true
  try {
    await updateDataMigrationStructured(props.structuredType, asset.id, { componentId: asset.component_id, assetName: editName.value.trim(), structuredData })
    ElMessage.success('已保存')
    drawerOpen.value = false
    await load()
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally { saving.value = false }
}

async function inspectStructuredImport(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const projectId = scopeProjectId.value
  if (projectId == null) {
    ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目')
    if (structuredInput.value) structuredInput.value.value = ''
    return
  }
  actionBusy.value = true
  importResult.value = ''
  try {
    const response = await inspectDataMigrationStructuredImport(props.structuredType, projectId, file)
    const data = response.data.data ?? {}
    importResult.value = `导入完成：成功 ${String(data.accepted ?? 0)} 行，失败 ${String(data.failed ?? 0)} 行`
    ElMessage.success(importResult.value)
    await load()
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally {
    actionBusy.value = false
    if (structuredInput.value) structuredInput.value.value = ''
  }
}

onMounted(() => { void scope.ensureLoaded() })

// 全局项目变化时丢弃上一个项目的列表与筛选条件，按新项目重新查询。
watch(scopeProjectId, () => {
  assets.value = []
  selectedIds.value = []
  keyword.value = ''
  importResult.value = ''
  error.value = ''
  forbidden.value = false
  drawerOpen.value = false
  void load()
}, { immediate: true })
</script>

<template>
  <main class="dm-page-root">
    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <section v-else-if="forbidden" class="dm-state-panel"><el-result icon="warning" :title="`暂无${pageTitle}查看权限`" sub-title="请向数据迁移管理员申请 data-migration:access 权限。" /></section>
    <section v-else-if="error" class="dm-state-panel"><el-result icon="error" :title="`${pageTitle}加载失败`" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="keyword" clearable placeholder="搜索编号或名称" style="width: 240px" @keyup.enter="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <input ref="structuredInput" class="dm-hidden" type="file" accept=".xlsx" @change="inspectStructuredImport">
          <el-button :disabled="actionBusy" @click="chooseStructuredImport"><el-icon><UploadFilled /></el-icon>导入 Excel</el-button>
          <el-button :disabled="actionBusy" @click="exportStructured"><el-icon><Download /></el-icon>导出 Excel</el-button>
          <el-button type="danger" plain :disabled="!selectedIds.length || actionBusy" @click="removeSelected"><el-icon><Delete /></el-icon>删除 ({{ selectedIds.length }})</el-button>
        </template>
      </UiToolbar>

      <p v-if="importResult" class="dm-import-result" role="status">{{ importResult }}</p>

      <UiDataTable v-if="assets.length || loading" :data="assets" :loading="loading" row-key="id" border empty-text="暂无资产" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="asset_code" label="资产编码" min-width="150" />
        <el-table-column prop="asset_name" label="名称" min-width="180" />
        <el-table-column prop="asset_type" label="类型" min-width="110" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :disabled="actionBusy" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑字段</el-button>
          </template>
        </el-table-column>
      </UiDataTable>
      <UiEmptyState v-if="!loading && !assets.length" title="暂无资产" description="调整筛选条件，或通过 Excel 导入资产。" />
    </template>

    <UiFormDrawer v-model="drawerOpen" :title="`编辑${pageTitle}`" :loading="saving" @submit="saveEdit">
      <el-form label-position="top">
        <el-form-item label="资产名称" required>
          <el-input v-model="editName" placeholder="必填" />
        </el-form-item>
        <el-form-item label="字段 JSON" required>
          <el-input v-model="editJson" type="textarea" :rows="12" placeholder="{}" />
        </el-form-item>
      </el-form>
    </UiFormDrawer>
  </main>
</template>

<style scoped>
.dm-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.dm-import-result { margin: 0 0 12px; color: var(--success); font-size: 13px; }
</style>
