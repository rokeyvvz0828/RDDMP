<!--
  用途：数迁资产列表视图（文件型资产通用列表组件）
  说明：按资产类型展示文件类资产（如汇报材料、会议纪要、迁移方案等），支持关键词查询、
        文件上传、按记录替换文件、下载、批量移入回收站；业务编号统一由服务端生成；
        所属项目唯一取自全局项目上下文（useProjectScope）且不在页面展示，页内不再提供项目筛选、
        项目 ID 输入与「所属项目」字段，
        项目切换后自动重置分页与筛选条件并按新项目重新查询；解析不到当前项目时展示闸门状态且不发起查询。
        视觉与交互对齐“用户管理”页：UiToolbar + UiDataTable + UiFormDrawer + Element Plus 反馈。
-->
<script setup lang="ts">
import '../data-migration.css'
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { Delete, Download, Plus, Refresh, Search, UploadFilled } from '@element-plus/icons-vue'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../components/ui/UiPagination.vue'
import { apiErrorMessage } from '../../../api/error'
import { deleteDataMigrationAssets, downloadDataMigrationAsset, listDataMigrationAssetsPage, replaceDataMigrationAsset, uploadDataMigrationAsset, type DataMigrationAsset } from '../../../api/data-migration'
import { useProjectScope } from '../composables/useProjectScope'
import ProjectScopeState from './ProjectScopeState.vue'

const props = defineProps<{ assetType: string; pageTitle: string }>()

const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const loading = ref(false)
const assets = ref<DataMigrationAsset[]>([])
const keyword = ref('')
const componentId = ref<number | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const selectedIds = ref<number[]>([])
const actionBusy = ref(false)

const drawerOpen = ref(false)
const saving = ref(false)
const uploadFile = ref<File | null>(null)
const replacingAsset = ref<DataMigrationAsset | null>(null)

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

function resetList() {
  assets.value = []
  total.value = 0
  selectedIds.value = []
}

async function load() {
  if (scopeProjectId.value == null) {
    resetList()
    return
  }
  loading.value = true
  selectedIds.value = []
  try {
    const result = (await listDataMigrationAssetsPage(props.assetType, {
      projectId: scopeProjectId.value,
      componentId: componentId.value ?? undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })).data.data
    assets.value = result?.records ?? []
    total.value = result?.total ?? 0
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally { loading.value = false }
}

function onSelectionChange(rows: DataMigrationAsset[]) {
  selectedIds.value = rows.map(row => row.id)
}

function openUpload() {
  replacingAsset.value = null
  uploadFile.value = null
  drawerOpen.value = true
}

function openReplace(asset: DataMigrationAsset) {
  replacingAsset.value = asset
  uploadFile.value = null
  drawerOpen.value = true
}

function onFileChange(file: UploadFile) {
  uploadFile.value = file.raw ?? null
}

async function saveUpload() {
  if (scopeProjectId.value == null) {
    ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目')
    return
  }
  if (!uploadFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  saving.value = true
  try {
    if (replacingAsset.value) {
      await replaceDataMigrationAsset(props.assetType, replacingAsset.value.id, scopeProjectId.value, uploadFile.value, replacingAsset.value.component_id)
      ElMessage.success('文件已替换')
    } else {
      await uploadDataMigrationAsset(props.assetType, scopeProjectId.value, uploadFile.value)
      ElMessage.success('上传成功')
    }
    drawerOpen.value = false
    await load()
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally { saving.value = false }
}

async function downloadAsset(row: DataMigrationAsset) {
  actionBusy.value = true
  try {
    const response = await downloadDataMigrationAsset(props.assetType, row.id)
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = row.asset_name || row.asset_code || 'asset'
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally { actionBusy.value = false }
}

async function moveToRecycleBin() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认将选中的 ${selectedIds.value.length} 个资产移入回收站吗？`, '移入回收站', { type: 'warning' })
    await deleteDataMigrationAssets(props.assetType, selectedIds.value)
    ElMessage.success('已移入回收站')
    await load()
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(messageOf(error))
  }
}

// 全局项目变化时清空上一个项目的列表、筛选与分页状态，再按新项目查询。
watch(scopeProjectId, () => {
  resetList()
  keyword.value = ''
  componentId.value = null
  page.value = 1
  drawerOpen.value = false
  void load()
}, { immediate: true })

onMounted(() => { void scope.ensureLoaded() })
</script>

<template>
  <section class="dm-page-root">
    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <template v-else>
    <UiToolbar>
      <el-input v-model="keyword" clearable placeholder="搜索编号或名称" style="width: 240px" @keyup.enter="load">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-input-number v-model="componentId" :min="1" :precision="0" :controls="false" clearable placeholder="组件 ID" style="width: 140px" />
      <template #actions>
        <el-button :disabled="loading || actionBusy" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" :disabled="loading || actionBusy" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button type="primary" plain :disabled="actionBusy" @click="openUpload"><el-icon><Plus /></el-icon>上传文件</el-button>
        <el-button type="danger" plain :disabled="!selectedIds.length || actionBusy" @click="moveToRecycleBin"><el-icon><Delete /></el-icon>移入回收站 ({{ selectedIds.length }})</el-button>
      </template>
    </UiToolbar>

    <UiDataTable :data="assets" :loading="loading" row-key="id" border empty-text="暂无资产" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="46" />
      <el-table-column prop="asset_code" label="资产编码" min-width="150" />
      <el-table-column prop="asset_name" label="名称" min-width="180" />
      <el-table-column prop="asset_type" label="类型" min-width="110" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="scope">
          <el-button link type="primary" :disabled="actionBusy" @click="downloadAsset(scope.row)"><el-icon><Download /></el-icon>下载</el-button>
          <el-button link type="primary" :disabled="actionBusy" @click="openReplace(scope.row)"><el-icon><UploadFilled /></el-icon>替换</el-button>
        </template>
      </el-table-column>
    </UiDataTable>
    <!-- 分页固定在模块约定的 .dm-table-footer 内：移动视口下隐藏总数/每页条数并允许换行，避免页面级横向溢出（design-h5.md §5.4） -->
    <div v-if="total > 0" class="dm-table-footer">
      <span>共 {{ total }} 条</span>
      <UiPagination :page="page" :page-size="size" :total="total" :page-sizes="[20, 50, 100]"
        @update:page-size="(value: number) => { size = value; page = 1; load() }"
        @update:page="(value: number) => { page = value; load() }" />
    </div>

    <UiFormDrawer v-model="drawerOpen" :title="replacingAsset ? `替换${pageTitle}文件` : `上传${pageTitle}`" :loading="saving" @submit="saveUpload">
      <el-form label-position="top">
        <el-form-item label="文件" required>
          <el-upload :auto-upload="false" :limit="1" drag style="width: 100%" @change="onFileChange">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
          </el-upload>
        </el-form-item>
      </el-form>
    </UiFormDrawer>
    </template>
  </section>
</template>
