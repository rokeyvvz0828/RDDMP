<!--
  用途：数迁资产内容 - 迁移过程依赖文件页（REQ-20260910-068，专属域化）
  说明：管理「迁移参数 + 使用方系统」的依赖关系，支持关键字 / 使用方系统筛选、分页展示、
        单条新增与编辑、Excel 两列模板批量导入、逻辑删除进入统一回收站。不再提供文件上传、替换或下载。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile, type UploadInstance } from 'element-plus'
import { Delete, Document, DocumentAdd, Edit, Refresh, Search, UploadFilled } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { useAuthStore } from '../../../../stores/auth'
import { apiErrorMessage } from '../../../../api/error'
import {
  batchCreateDependencies,
  createDependency,
  updateDependency,
  deleteDependencies,
  downloadDependencyTemplate,
  getSystemOptions,
  importDependencies,
  listDependencies,
  listDependencyParameterPage,
  listDependencyParameterOptions,
  type DependencyBatchCreateResult,
  type DependencyFormData,
  type DependencyImportResult,
  type DependencyParameterOption,
  type DependencyQuery,
  type DependencyRecord,
  type DependencyUpdateData,
  type SelectOption,
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'

const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const hasCreate = computed(() => auth.hasPermission('data-migration:content:dependencies:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdate = computed(() => auth.hasPermission('data-migration:content:dependencies:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDelete = computed(() => auth.hasPermission('data-migration:content:dependencies:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEditItem = (item: DependencyRecord) => hasUpdate.value && (canManage.value || item.created_by === auth.user?.id)
const canDeleteItem = (item: DependencyRecord) => hasDelete.value && (canManage.value || item.created_by === auth.user?.id)

const loading = ref(false)
const optionLoading = ref(false)
const actionBusy = ref(false)
const error = ref('')
const forbidden = ref(false)
const records = ref<DependencyRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selectedIds = ref<number[]>([])
const keyword = ref('')
const consumerSystemCode = ref('')
const systemOptions = ref<SelectOption[]>([])
const paramOptions = ref<DependencyParameterOption[]>([])
const paramSearchKeyword = ref('')
const paramLoading = ref(false)
const optionError = ref('')

const drawerOpen = ref(false)
const saving = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const submitError = ref('')
const form = ref<DependencyFormData>({
  projectId: 0,
  parameterId: 0,
  consumerSystemCode: '',
})

const importDialogOpen = ref(false)
const importUploadRef = ref<UploadInstance>()
const pendingImportFile = ref<File | null>(null)
const importResult = ref<DependencyImportResult | null>(null)
const importError = ref('')

const importReady = computed(() => Boolean(scopeProjectId.value))
const canImport = computed(() => hasCreate.value && importReady.value)
const canSubmitImport = computed(() => canImport.value && Boolean(pendingImportFile.value) && !actionBusy.value)
const hasFilters = computed(() => Boolean(consumerSystemCode.value || keyword.value.trim()))

const bulkDialogOpen = ref(false)
const bulkSystemCodes = ref<string[]>([])
const bulkParamPage = ref(1)
const bulkParamSize = ref(20)
const bulkParamTotal = ref(0)
const bulkParamRows = ref<DependencyParameterOption[]>([])
const bulkParamKeyword = ref('')
const bulkParamSystemCode = ref('')
const bulkParamLoading = ref(false)
const bulkSelectedParams = ref<DependencyParameterOption[]>([])
const bulkSubmitting = ref(false)
const bulkSubmitError = ref('')
const bulkResult = ref<DependencyBatchCreateResult | null>(null)
const bulkPairCount = computed(() => bulkSystemCodes.value.length * bulkSelectedParams.value.length)

const messageOf = (cause: unknown, fallback = '操作失败，请稍后重试') => cause instanceof Error && cause.message ? cause.message : fallback
const httpStatus = (cause: unknown) => (cause as { response?: { status?: number } }).response?.status
const cancelled = (cause: unknown) => (cause as { action?: string }).action === 'cancel' || (cause as { action?: string }).action === 'close'

function resetPagination() { page.value = 1 }

function search() {
  resetPagination()
  load()
}

function resetFilters() {
  keyword.value = ''
  consumerSystemCode.value = ''
  resetPagination()
  load()
}

async function loadSystemOptions() {
  if (!scopeProjectId.value) return
  optionLoading.value = true
  optionError.value = ''
  try {
    const res = await getSystemOptions(scopeProjectId.value)
    systemOptions.value = res.data.data ?? []
  } catch (cause) {
    optionError.value = messageOf(cause, '系统选项加载失败')
  } finally { optionLoading.value = false }
}

async function loadParamOptions(keywordVal = '') {
  if (!scopeProjectId.value) return
  paramLoading.value = true
  try {
    const res = await listDependencyParameterOptions({ projectId: scopeProjectId.value, keyword: keywordVal || undefined })
    paramOptions.value = res.data.data ?? []
  } catch (cause) {
    optionError.value = messageOf(cause, '参数选项加载失败')
  } finally { paramLoading.value = false }
}

async function loadBulkParams() {
  if (!scopeProjectId.value) return
  bulkParamLoading.value = true
  try {
    const res = await listDependencyParameterPage({
      projectId: scopeProjectId.value,
      systemCode: bulkParamSystemCode.value || undefined,
      keyword: bulkParamKeyword.value.trim() || undefined,
      page: bulkParamPage.value,
      size: bulkParamSize.value,
    })
    bulkParamRows.value = res.data.data.records ?? []
    bulkParamTotal.value = res.data.data.total ?? 0
  } catch (cause) {
    bulkSubmitError.value = messageOf(cause, '迁移参数加载失败')
  } finally { bulkParamLoading.value = false }
}

function openBulkCreate() {
  bulkDialogOpen.value = true
  bulkSystemCodes.value = []
  bulkSelectedParams.value = []
  bulkParamKeyword.value = ''
  bulkParamSystemCode.value = ''
  bulkParamPage.value = 1
  bulkResult.value = null
  bulkSubmitError.value = ''
  loadBulkParams()
}

function onBulkSelectionChange(rows: DependencyParameterOption[]) {
  bulkSelectedParams.value = rows
}

async function submitBulkCreate() {
  if (!scopeProjectId.value || bulkPairCount.value === 0) return
  bulkSubmitting.value = true
  bulkSubmitError.value = ''
  bulkResult.value = null
  try {
    const res = await batchCreateDependencies({
      projectId: scopeProjectId.value,
      consumerSystemCodes: bulkSystemCodes.value,
      parameterIds: bulkSelectedParams.value.map(item => item.id),
    })
    bulkResult.value = res.data.data
    ElMessage.success(`批量新增完成：新增 ${res.data.data.accepted} 条${res.data.data.skipped ? `，跳过已存在 ${res.data.data.skipped} 条` : ''}`)
    load()
  } catch (cause) {
    bulkSubmitError.value = messageOf(cause, '批量新增失败')
  } finally { bulkSubmitting.value = false }
}

function buildQuery(): DependencyQuery {
  return {
    projectId: scopeProjectId.value || 0,
    consumerSystemCode: consumerSystemCode.value || undefined,
    keyword: keyword.value.trim() || undefined,
    page: page.value,
    size: size.value,
  }
}

async function load() {
  if (!scopeProjectId.value) return
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const res = await listDependencies(buildQuery())
    records.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } catch (cause) {
    if (httpStatus(cause) === 403) {
      forbidden.value = true
      error.value = '没有查看迁移过程依赖文件的权限'
    } else {
      error.value = messageOf(cause, '加载失败，请稍后重试')
    }
  } finally { loading.value = false }
}

function onSelectionChange(val: DependencyRecord[]) {
  selectedIds.value = val.map(r => r.id)
}

function openEdit(row: DependencyRecord) {
  editing.value = true
  editId.value = row.id
  submitError.value = ''
  form.value = {
    projectId: row.project_id,
    parameterId: row.parameter_id,
    consumerSystemCode: row.consumer_system_code,
  }
  loadParamOptions('')
  drawerOpen.value = true
}

async function saveForm() {
  if (!form.value.parameterId) {
    submitError.value = '请选择迁移参数'
    return
  }
  if (!form.value.consumerSystemCode) {
    submitError.value = '请选择使用方系统'
    return
  }
  saving.value = true
  submitError.value = ''
  try {
    if (editing.value && editId.value != null) {
      const body: DependencyUpdateData = {
        parameterId: form.value.parameterId,
        consumerSystemCode: form.value.consumerSystemCode,
      }
      await updateDependency(editId.value, body)
      ElMessage.success('编辑成功')
    } else {
      await createDependency(form.value)
      ElMessage.success('新增成功')
    }
    drawerOpen.value = false
    load()
  } catch (cause) {
    submitError.value = messageOf(cause, '保存失败')
  } finally { saving.value = false }
}

async function removeItem(row: DependencyRecord) {
  try {
    await ElMessageBox.confirm(`确定删除依赖关系「${row.parameter_name_en} / ${row.consumer_system_code}」？删除后可在回收站恢复。`, '删除确认', { type: 'warning' })
  } catch (cause) { if (cancelled(cause)) return; throw cause }
  actionBusy.value = true
  try {
    await deleteDependencies([row.id])
    ElMessage.success('已删除')
    load()
  } catch (cause) {
    ElMessage.error(messageOf(cause, '删除失败'))
  } finally { actionBusy.value = false }
}

async function batchDelete() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条依赖关系？删除后可在回收站恢复。`, '批量删除确认', { type: 'warning' })
  } catch (cause) { if (cancelled(cause)) return; throw cause }
  actionBusy.value = true
  try {
    await deleteDependencies(selectedIds.value)
    ElMessage.success('已删除')
    selectedIds.value = []
    load()
  } catch (cause) {
    ElMessage.error(messageOf(cause, '删除失败'))
  } finally { actionBusy.value = false }
}

function onParamSearch(query: string) {
  loadParamOptions(query)
}

function formatParameterLabel(opt: DependencyParameterOption) {
  return `${opt.parameter_name_en} / ${opt.parameter_name} · ${opt.system_code}${opt.system_name ? ' - ' + opt.system_name : ''}`
}

// ============ 导入 ============

function openImport() {
  pendingImportFile.value = null
  importResult.value = null
  importError.value = ''
  importDialogOpen.value = true
}

function beforeImportUpload(file: UploadFile) {
  if (!(file.raw instanceof File)) return false
  const name = file.name || ''
  if (!name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('仅支持 .xlsx 格式')
    return false
  }
  if (file.size && file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件不能超过 50 MB')
    return false
  }
  pendingImportFile.value = file.raw
  importResult.value = null
  importError.value = ''
  return false // 阻止自动上传，手动提交
}

function clearImportFile() {
  pendingImportFile.value = null
  importResult.value = null
  importError.value = ''
  if (importUploadRef.value) {
    importUploadRef.value.clearFiles()
  }
}

async function submitImport() {
  if (!pendingImportFile.value || !scopeProjectId.value) return
  actionBusy.value = true
  importError.value = ''
  importResult.value = null
  try {
    const res = await importDependencies({ projectId: scopeProjectId.value }, pendingImportFile.value)
    importResult.value = res.data.data
    if (res.data.data.accepted > 0) {
      ElMessage.success(`导入完成：成功 ${res.data.data.accepted} 条，失败 ${res.data.data.failed} 条`)
    } else {
      ElMessage.warning(`导入完成：全部 ${res.data.data.failed} 条失败，请检查错误信息`)
    }
    load()
  } catch (cause) {
    importError.value = messageOf(cause, '导入失败')
  } finally { actionBusy.value = false }
}

async function downloadTemplate() {
  try {
    const res = await downloadDependencyTemplate()
    const blob = new Blob([res.data as unknown as BlobPart], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'data-migration-dependency-template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (cause) {
    ElMessage.error(messageOf(cause, '模板下载失败'))
  }
}

watch(scopeProjectId, () => {
  if (scopeProjectId.value) {
    loadSystemOptions()
    load()
  }
})

onMounted(() => {
  scope.ensureLoaded()
})
</script>

<template>
  <main class="dm-page-root">
    <UiPageHeader title="迁移过程依赖文件" description="维护迁移参数与使用方系统的依赖关系">
      <template #actions>
        <el-button v-if="hasCreate && scopeState === 'ready' && !forbidden && !error" :disabled="loading || actionBusy" @click="openImport">
          <el-icon><UploadFilled /></el-icon>批量导入
        </el-button>
        <el-button v-if="hasCreate && scopeState === 'ready' && !forbidden && !error" type="primary" plain :disabled="loading || actionBusy" @click="openBulkCreate">
          <el-icon><DocumentAdd /></el-icon>批量新增
        </el-button>
      </template>
    </UiPageHeader>

    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />

    <section v-else-if="forbidden" class="dm-state-panel">
      <el-result icon="warning" title="暂无权限" sub-title="您没有查看迁移过程依赖文件的权限。" />
    </section>

    <section v-else-if="error" class="dm-state-panel">
      <el-result icon="error" title="加载失败" :sub-title="error">
        <template #extra><el-button type="primary" @click="load()">重新加载</el-button></template>
      </el-result>
    </section>

    <template v-else>
      <UiToolbar>
        <el-select v-model="consumerSystemCode" clearable filterable :loading="optionLoading" placeholder="使用方系统" style="width: 220px" @change="search" @clear="search">
          <el-option v-for="opt in systemOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input v-model="keyword" clearable placeholder="参数名称 / 参数英文名 / 使用方系统" style="width: 280px" @keyup.enter="search" @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="!hasFilters" @click="resetFilters"><el-icon><Refresh /></el-icon>重置</el-button>
          <el-button v-if="hasDelete" type="danger" plain :disabled="!selectedIds.length || actionBusy" @click="batchDelete">
            <el-icon><Delete /></el-icon>删除 ({{ selectedIds.length }})
          </el-button>
        </template>
      </UiToolbar>

      <!-- 桌面表格 -->
      <div class="dm-desktop-only">
        <UiDataTable v-if="records.length || loading" :data="records" :loading="loading" row-key="id" border empty-text="暂无依赖关系" @selection-change="onSelectionChange">
          <el-table-column v-if="hasDelete" type="selection" width="46" />
          <el-table-column prop="parameter_name_en" label="参数英文" min-width="180" show-overflow-tooltip />
          <el-table-column prop="parameter_name" label="参数中文" min-width="180" show-overflow-tooltip />
          <el-table-column prop="consumer_system_code" label="使用方系统编号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="consumer_system_name" label="使用方系统名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="provider_system_code" label="提供方组件编号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="provider_system_name" label="提供方组件名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="updated_at" label="更新时间" min-width="170" />
          <el-table-column prop="updated_by_name" label="更新人" min-width="120" show-overflow-tooltip />
          <el-table-column v-if="hasUpdate || hasDelete" label="操作" width="160" fixed="right">
            <template #default="scopeRow">
              <el-button v-if="hasUpdate" link type="primary" :disabled="actionBusy" @click="openEdit(scopeRow.row)">
                <el-icon><Edit /></el-icon>编辑
              </el-button>
              <el-button v-if="hasDelete" link type="danger" :disabled="actionBusy" @click="removeItem(scopeRow.row)">
                <el-icon><Delete /></el-icon>删除
              </el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiEmptyState v-if="!loading && !records.length"
          :title="hasFilters ? '没有匹配的依赖关系' : '暂无依赖关系'"
          :description="hasCreate ? '调整筛选条件，或新增 / 批量导入依赖关系。' : '调整筛选条件后重试。'" />
        <div class="dm-page">
          <UiPagination :page="page" :page-size="size" :total="total" :page-sizes="[20, 50, 100]"
            @update:page-size="(v: number) => { size = v; page = 1; load() }" @update:page="(v: number) => { page = v; load() }" />
        </div>
      </div>

      <!-- 移动卡片列表 -->
      <div class="dm-mobile-only">
        <div v-if="loading" class="dm-loading-state">加载中…</div>
        <div v-else-if="!records.length" class="dm-empty-wrap">
          <UiEmptyState
            :title="hasFilters ? '没有匹配的依赖关系' : '暂无依赖关系'"
            :description="hasCreate ? '调整筛选条件，或新增 / 批量导入依赖关系。' : '调整筛选条件后重试。'" />
        </div>
        <div v-else class="dm-card-list">
          <div v-for="item in records" :key="item.id" class="dm-card-item">
            <div class="dm-card-title">{{ item.parameter_name_en }}</div>
            <div class="dm-card-subtitle">{{ item.parameter_name }}</div>
            <div class="dm-card-row">
              <span class="dm-card-label">使用方</span>
              <span class="dm-card-value">{{ item.consumer_system_code }} · {{ item.consumer_system_name || '-' }}</span>
            </div>
            <div class="dm-card-row">
              <span class="dm-card-label">提供方</span>
              <span class="dm-card-value">{{ item.provider_system_code }} · {{ item.provider_system_name || '-' }}</span>
            </div>
            <div class="dm-card-footer">
              <span class="dm-card-meta">{{ item.updated_at }} · {{ item.updated_by_name || '-' }}</span>
              <div class="dm-card-actions">
                <el-button v-if="hasUpdate && canEditItem(item)" size="small" link type="primary" :disabled="actionBusy" @click="openEdit(item)">编辑</el-button>
                <el-button v-if="hasDelete && canDeleteItem(item)" size="small" link type="danger" :disabled="actionBusy" @click="removeItem(item)">删除</el-button>
              </div>
            </div>
          </div>
        </div>
        <div v-if="records.length" class="dm-pagination-wrap">
          <UiPagination :page="page" :page-size="size" :total="total" :page-sizes="[20, 50, 100]" layout="prev, pager, next"
            @update:page-size="(v: number) => { size = v; page = 1; load() }" @update:page="(v: number) => { page = v; load() }" />
        </div>
      </div>

      <!-- 新增/编辑弹框 -->
      <el-dialog
        v-model="drawerOpen"
        :title="editing ? '编辑依赖关系' : '新增依赖关系'"
        width="min(760px, calc(100vw - 24px))"
        top="5vh"
        destroy-on-close
        :close-on-click-modal="!saving"
        :close-on-press-escape="!saving"
        :show-close="!saving"
      >
        <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon style="margin-bottom: 12px" />
        <el-form label-position="top">
          <el-form-item label="迁移参数" required>
            <el-select
              v-model="form.parameterId"
              filterable
              remote
              reserve-keyword
              placeholder="搜索参数英文名 / 中文名 / 系统"
              :loading="paramLoading"
              :remote-method="onParamSearch"
              @focus="loadParamOptions('')"
            >
              <el-option v-for="opt in paramOptions" :key="opt.id" :label="formatParameterLabel(opt)" :value="opt.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="使用方系统" required>
            <el-select
              v-model="form.consumerSystemCode"
              placeholder="选择使用方系统"
              filterable
              :loading="optionLoading"
            >
              <el-option v-for="opt in systemOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button :disabled="saving" @click="drawerOpen = false">关闭</el-button>
          <el-button type="primary" :loading="saving" @click="saveForm">保存</el-button>
        </template>
      </el-dialog>

      <!-- 导入对话框 -->
      <el-dialog v-model="importDialogOpen" title="批量导入依赖关系" width="520px" :close-on-click-modal="false">
        <div class="dm-import-content">
          <p class="dm-import-hint">
            请使用
            <el-button link type="primary" @click="downloadTemplate()">下载导入模板</el-button>
            ，按「参数英文名、使用方系统编号」两列逐行填写。支持 .xlsx，最大 50 MB、5000 行。
          </p>
          <el-upload
            ref="importUploadRef"
            :auto-upload="false"
            :show-file-list="false"
            :before-upload="beforeImportUpload"
            accept=".xlsx"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处，或 <em>点击选择</em>
            </div>
          </el-upload>
          <div v-if="pendingImportFile" class="dm-import-file">
            <el-icon><Document /></el-icon>
            <span class="dm-import-file-name">{{ pendingImportFile.name }}</span>
            <el-button size="small" link type="danger" @click="clearImportFile()">移除</el-button>
          </div>
          <div v-if="importError" class="dm-import-error">{{ importError }}</div>
          <div v-if="importResult" class="dm-import-result">
            <div class="dm-import-stats">
              <span>共 {{ importResult.rows }} 行</span>
              <span class="dm-import-success">成功 {{ importResult.accepted }}</span>
              <span class="dm-import-fail">失败 {{ importResult.failed }}</span>
            </div>
            <div v-if="importResult.errors && importResult.errors.length" class="dm-import-errors">
              <div class="dm-import-errors-title">失败详情：</div>
              <ul>
                <li v-for="(err, idx) in importResult.errors.slice(0, 20)" :key="idx">{{ err }}</li>
                <li v-if="importResult.errors.length > 20" class="dm-import-more">…还有 {{ importResult.errors.length - 20 }} 条错误</li>
              </ul>
            </div>
          </div>
        </div>
        <template #footer>
          <el-button @click="importDialogOpen = false">关闭</el-button>
          <el-button type="primary" :disabled="!canSubmitImport" :loading="actionBusy" @click="submitImport()">开始导入</el-button>
        </template>
      </el-dialog>

      <!-- 批量新增依赖关系对话框 -->
      <el-dialog v-model="bulkDialogOpen" title="按使用方系统批量新增迁移参数" width="min(760px, calc(100vw - 24px))" :close-on-click-modal="false" :close-on-press-escape="!bulkSubmitting" :show-close="!bulkSubmitting">
        <div class="dm-bulk-content">
          <el-form label-position="top">
            <el-form-item label="使用方系统（可多选）" required>
              <el-select v-model="bulkSystemCodes" multiple filterable placeholder="选择使用方系统" style="width: 100%">
                <el-option v-for="opt in systemOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="迁移参数（表格勾选，可多选）" required>
              <div class="dm-bulk-params">
                <div class="dm-bulk-filters">
                  <el-select v-model="bulkParamSystemCode" clearable filterable placeholder="按系统筛选" style="width: 200px" @change="bulkParamPage = 1; loadBulkParams()">
                    <el-option v-for="opt in systemOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                  <el-input v-model="bulkParamKeyword" clearable placeholder="参数名称 / 英文名" style="width: 240px" @keyup.enter="bulkParamPage = 1; loadBulkParams()" @clear="bulkParamPage = 1; loadBulkParams()">
                    <template #prefix><el-icon><Search /></el-icon></template>
                  </el-input>
                  <el-button :icon="Refresh" :loading="bulkParamLoading" @click="bulkParamPage = 1; loadBulkParams()">刷新</el-button>
                </div>
                <el-table :data="bulkParamRows" v-loading="bulkParamLoading" border row-key="id" height="320" @selection-change="onBulkSelectionChange">
                  <el-table-column type="selection" width="46" reserve-selection />
                  <el-table-column prop="parameter_name_en" label="参数英文名" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="parameter_name" label="参数中文名" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="system_code" label="所属系统编号" min-width="140" show-overflow-tooltip />
                  <el-table-column prop="system_name" label="所属系统名称" min-width="160" show-overflow-tooltip />
                </el-table>
                <div class="dm-bulk-pagination">
                  <UiPagination :page="bulkParamPage" :page-size="bulkParamSize" :total="bulkParamTotal" :page-sizes="[20, 50, 100]"
                    @update:page-size="(v: number) => { bulkParamSize = v; bulkParamPage = 1; loadBulkParams() }" @update:page="(v: number) => { bulkParamPage = v; loadBulkParams() }" />
                </div>
                <div v-if="bulkResult" class="dm-bulk-result">
                  批量新增完成：新增 {{ bulkResult.accepted }} 条，跳过已存在 {{ bulkResult.skipped }} 条。
                </div>
              </div>
            </el-form-item>
          </el-form>
          <el-alert v-if="bulkSubmitError" :title="bulkSubmitError" type="error" :closable="false" show-icon />
        </div>
        <template #footer>
          <el-button :disabled="bulkSubmitting" @click="bulkDialogOpen = false">关闭</el-button>
          <el-button type="primary" :disabled="bulkPairCount === 0" :loading="bulkSubmitting" @click="submitBulkCreate">
            批量新增 {{ bulkPairCount }} 条
          </el-button>
        </template>
      </el-dialog>
    </template>
  </main>
</template>

<style scoped>
.dm-desktop-only {
  display: block;
}
.dm-mobile-only {
  display: none;
}
.dm-loading-state {
  padding: 24px 0;
  text-align: center;
  color: var(--dm-text-tertiary, #909399);
  font-size: 13px;
}
.dm-state-panel {
  padding: 40px 20px;
}
.dm-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}
.dm-card-item {
  background: var(--dm-card-bg, #fff);
  border: 1px solid var(--dm-border-color, #ebeef5);
  border-radius: 8px;
  padding: 14px 16px;
}
.dm-card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--dm-text-primary, #303133);
  margin-bottom: 4px;
}
.dm-card-subtitle {
  font-size: 13px;
  color: var(--dm-text-secondary, #606266);
  margin-bottom: 10px;
}
.dm-card-row {
  display: flex;
  font-size: 13px;
  line-height: 1.8;
}
.dm-card-label {
  color: var(--dm-text-tertiary, #909399);
  width: 64px;
  flex-shrink: 0;
}
.dm-card-value {
  color: var(--dm-text-primary, #303133);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dm-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--dm-border-light, #f0f0f0);
}
.dm-card-meta {
  font-size: 12px;
  color: var(--dm-text-tertiary, #909399);
}
.dm-card-actions {
  display: flex;
  gap: 8px;
}
.dm-empty-wrap {
  padding: 40px 0;
}
.dm-pagination-wrap {
  display: flex;
  justify-content: center;
  padding: 16px 0;
}
.dm-bulk-content {
  min-height: 200px;
}
.dm-bulk-params {
  width: 100%;
  min-width: 0;
}
.dm-bulk-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}
.dm-bulk-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
}
.dm-bulk-result {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--dm-import-result-bg, #f5f7fa);
  border-radius: 4px;
  font-size: 13px;
}
.dm-import-content {
  min-height: 200px;
}
.dm-import-hint {
  margin-bottom: 16px;
  color: var(--dm-text-secondary, #606266);
  font-size: 13px;
  line-height: 1.6;
}
.dm-import-file {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--dm-import-file-bg, #f5f7fa);
  border-radius: 4px;
  margin-top: 12px;
}
.dm-import-file-name {
  flex: 1;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dm-import-error {
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--dm-error-bg, #fef0f0);
  color: var(--dm-error-color, #f56c6c);
  border-radius: 4px;
  font-size: 13px;
}
.dm-import-result {
  margin-top: 16px;
  padding: 12px;
  background: var(--dm-import-result-bg, #f5f7fa);
  border-radius: 4px;
}
.dm-import-stats {
  display: flex;
  gap: 16px;
  font-size: 14px;
  margin-bottom: 10px;
}
.dm-import-success {
  color: var(--dm-success-color, #67c23a);
}
.dm-import-fail {
  color: var(--dm-error-color, #f56c6c);
}
.dm-import-errors-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--dm-text-secondary, #606266);
}
.dm-import-errors ul {
  margin: 0;
  padding-left: 20px;
  font-size: 12px;
  color: var(--dm-text-secondary, #606266);
  max-height: 160px;
  overflow-y: auto;
}
.dm-import-errors li {
  line-height: 1.6;
}
.dm-import-more {
  color: var(--dm-text-tertiary, #909399);
}
@media (max-width: 768px) {
  .dm-desktop-only {
    display: none;
  }
  .dm-mobile-only {
    display: block;
  }
  .ui-toolbar :deep(.el-select),
  .ui-toolbar :deep(.el-input) { width: 100% !important; }
}
</style>
