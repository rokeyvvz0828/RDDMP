<!--
  用途：数迁资产列表视图（文件型资产通用列表组件）
  说明：按资产类型展示文件类资产（如汇报材料、会议纪要、迁移方案等），支持关键词查询、
        文件上传（抽屉表单填写项目 ID、文件编号并选择文件）、下载、批量移入回收站；
        视觉与交互对齐“用户管理”页：UiToolbar + UiDataTable + UiFormDrawer + Element Plus 反馈。
-->
<script setup lang="ts">
import '../data-migration.css'
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { Delete, Download, Plus, Refresh, Search, UploadFilled } from '@element-plus/icons-vue'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../components/ui/UiPagination.vue'
import { apiErrorMessage } from '../../../api/error'
import { deleteDataMigrationAssets, downloadDataMigrationAsset, listDataMigrationAssetsPage, uploadDataMigrationAsset, type DataMigrationAsset } from '../../../api/data-migration'

const props = defineProps<{ assetType: string; pageTitle: string }>()

const loading = ref(false)
const assets = ref<DataMigrationAsset[]>([])
const keyword = ref('')
const projectId = ref<number | null>(null)
const componentId = ref<number | null>(null)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const selectedIds = ref<number[]>([])
const actionBusy = ref(false)

const drawerOpen = ref(false)
const saving = ref(false)
const uploadProjectId = ref<number | null>(null)
const uploadAssetCode = ref('')
const uploadFile = ref<File | null>(null)

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

async function load() {
  loading.value = true
  selectedIds.value = []
  try {
    const result = (await listDataMigrationAssetsPage(props.assetType, {
      projectId: projectId.value ?? undefined,
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
  uploadProjectId.value = null
  uploadAssetCode.value = ''
  uploadFile.value = null
  drawerOpen.value = true
}

function onFileChange(file: UploadFile) {
  uploadFile.value = file.raw ?? null
}

async function saveUpload() {
  const projectId = uploadProjectId.value
  if (projectId == null || !Number.isInteger(projectId) || projectId <= 0) {
    ElMessage.warning('请填写有效的项目 ID')
    return
  }
  if (!uploadAssetCode.value.trim()) {
    ElMessage.warning('请填写文件编号')
    return
  }
  if (!uploadFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  saving.value = true
  try {
    await uploadDataMigrationAsset(props.assetType, projectId, uploadAssetCode.value.trim(), uploadFile.value)
    ElMessage.success('上传成功')
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

onMounted(load)
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <el-input v-model="keyword" clearable placeholder="搜索编号或名称" style="width: 240px" @keyup.enter="load">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-input-number v-model="projectId" :min="1" :precision="0" :controls="false" clearable placeholder="项目 ID" style="width: 140px" />
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
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="scope">
          <el-button link type="primary" :disabled="actionBusy" @click="downloadAsset(scope.row)"><el-icon><Download /></el-icon>下载</el-button>
        </template>
      </el-table-column>
    </UiDataTable>
    <UiPagination v-if="total > 0" :page="page" :page-size="size" :total="total" :page-sizes="[20, 50, 100]"
      @update:page-size="(value: number) => { size = value; page = 1; load() }"
      @update:page="(value: number) => { page = value; load() }" />

    <UiFormDrawer v-model="drawerOpen" :title="`上传${pageTitle}`" :loading="saving" @submit="saveUpload">
      <el-form label-position="top">
        <el-form-item label="项目 ID" required>
          <el-input-number v-model="uploadProjectId" :min="1" :precision="0" :controls="false" style="width: 100%" placeholder="必填" />
        </el-form-item>
        <el-form-item label="文件编号" required>
          <el-input v-model="uploadAssetCode" placeholder="必填" />
        </el-form-item>
        <el-form-item label="文件" required>
          <el-upload :auto-upload="false" :limit="1" drag style="width: 100%" @change="onFileChange">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
          </el-upload>
        </el-form-item>
      </el-form>
    </UiFormDrawer>
  </section>
</template>
