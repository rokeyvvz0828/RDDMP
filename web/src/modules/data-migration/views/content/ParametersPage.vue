<!--
  用途：数迁资产内容 - 迁移参数页（REQ-20260906-067，对标迁移检核规则专属域）
  说明：统一管理「业务参数 / 技术参数」两类迁移配置参数，支持 参数类型 / 参数范围分类 / 关联系统 / 关键字
        多维筛选、分页展示、单条录入与编辑、Excel 五列模板逐行批量导入、条件导出、逻辑删除与统一回收站。
        所属项目唯一取自全局项目上下文：页内不展示项目字段；导出为准当前筛选条件；删除进入回收站。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, Download, Edit, Plus, Refresh, Search, UploadFilled, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { useAuthStore } from '../../../../stores/auth'
import { apiErrorMessage } from '../../../../api/error'
import {
  createParameter,
  deleteParameters,
  downloadParameterTemplate,
  exportParameters,
  getDataMigrationParamOptions,
  DM_CODE_CATEGORIES,
  getParameter,
  getSystemOptions,
  importParameters,
  listParameters,
  updateParameter,
  type ParameterFormData,
  type ParameterImportResult,
  type ParameterQuery,
  type ParameterRecord,
  type ParameterUpdateData,
  type SelectOption,
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'

const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const hasCreate = computed(() => auth.hasPermission('data-migration:content:parameters:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdate = computed(() => auth.hasPermission('data-migration:content:parameters:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDelete = computed(() => auth.hasPermission('data-migration:content:parameters:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEditItem = (item: ParameterRecord) => hasUpdate.value && (canManage.value || item.created_by === auth.user?.id)
const canDeleteItem = (item: ParameterRecord) => hasDelete.value && (canManage.value || item.created_by === auth.user?.id)

const loading = ref(false)
const optionLoading = ref(false)
const actionBusy = ref(false)
const error = ref('')
const forbidden = ref(false)
const records = ref<ParameterRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selectedIds = ref<number[]>([])
const keyword = ref('')
const parameterType = ref('')
const parameterScope = ref('')
const systemCode = ref('')
const typeOptions = ref<SelectOption[]>([])
const scopeOptions = ref<SelectOption[]>([])
const systemOptions = ref<SelectOption[]>([])
const optionError = ref('')

const drawerOpen = ref(false)
const saving = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const submitError = ref('')
const form = ref<ParameterFormData>({
  projectId: 0,
  parameterType: '', parameterScope: '', systemCode: '',
  parameterName: '', parameterDescription: '',
})

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<ParameterRecord | null>(null)
const detailError = ref('')

const importInput = ref<HTMLInputElement | null>(null)
const pendingImportFile = ref<File | null>(null)
const importResult = ref<ParameterImportResult | null>(null)

const canImport = computed(() => hasCreate.value)
const hasFilters = computed(() => Boolean(parameterType.value || parameterScope.value || systemCode.value || keyword.value.trim()))

const messageOf = (cause: unknown, fallback = '操作失败，请稍后重试') => cause instanceof Error && cause.message ? cause.message : fallback
const httpStatus = (cause: unknown) => (cause as { response?: { status?: number } }).response?.status
const cancelled = (cause: unknown) => (cause as { action?: string }).action === 'cancel' || (cause as { action?: string }).action === 'close'

function resetPagination() { page.value = 1 }

async function loadOptions() {
  optionLoading.value = true
  optionError.value = ''
  try {
    const [typeRes, scopeRes] = await Promise.all([
      getDataMigrationParamOptions(DM_CODE_CATEGORIES.parameterType),
      getDataMigrationParamOptions(DM_CODE_CATEGORIES.parameterScope),
    ])
    typeOptions.value = typeRes.data.data ?? []
    scopeOptions.value = scopeRes.data.data ?? []
  } catch (cause) {
    optionError.value = messageOf(cause, '选项加载失败')
  } finally { optionLoading.value = false }
}

async function loadSystems() {
  const pid = scopeProjectId.value
  if (pid == null) { systemOptions.value = []; return }
  try {
    const response = await getSystemOptions(pid)
    systemOptions.value = response.data.data ?? []
  } catch (cause) {
    optionError.value = messageOf(cause, '关联系统选项加载失败')
  }
}

async function load() {
  const pid = scopeProjectId.value
  if (pid == null) { records.value = []; total.value = 0; selectedIds.value = []; return }
  loading.value = true
  error.value = ''
  forbidden.value = false
  selectedIds.value = []
  try {
    const response = await listParameters({
      projectId: pid,
      parameterType: parameterType.value || undefined,
      parameterScope: parameterScope.value || undefined,
      systemCode: systemCode.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value,
    })
    records.value = response.data.data?.records ?? []
    total.value = response.data.data?.total ?? 0
  } catch (cause) {
    if (httpStatus(cause) === 403) forbidden.value = true
    else error.value = apiErrorMessage(cause, '迁移参数加载失败')
  } finally { loading.value = false }
}

function search() { resetPagination(); void load() }
function resetFilters() { parameterType.value = ''; parameterScope.value = ''; systemCode.value = ''; keyword.value = ''; search() }
function onSelectionChange(rows: ParameterRecord[]) { selectedIds.value = rows.map(row => row.id) }

// ============ 新增 / 编辑 ============
function resetForm() {
  form.value = { projectId: 0, parameterType: '', parameterScope: '', systemCode: '', parameterName: '', parameterDescription: '' }
  submitError.value = ''
}

function openCreate() {
  editing.value = false
  editId.value = null
  resetForm()
  form.value.projectId = scopeProjectId.value ?? 0
  drawerOpen.value = true
}

async function openEdit(item: ParameterRecord) {
  editing.value = true
  editId.value = item.id
  resetForm()
  submitError.value = ''
  detailLoading.value = true
  drawerOpen.value = true
  try {
    const response = await getParameter(item.id)
    const data = response.data.data
    if (!data) throw new Error('参数详情为空')
    form.value = {
      projectId: data.project_id,
      parameterType: data.parameter_type,
      parameterScope: data.parameter_scope,
      systemCode: data.system_code,
      parameterName: data.parameter_name ?? '',
      parameterDescription: data.parameter_description ?? '',
    }
  } catch (cause) {
    submitError.value = messageOf(cause, '参数详情加载失败')
    drawerOpen.value = false
  } finally { detailLoading.value = false }
}

async function handleSave() {
  const required = [
    ['参数类型', form.value.parameterType], ['参数范围分类', form.value.parameterScope],
    ['关联系统', form.value.systemCode], ['参数名称', form.value.parameterName],
  ] as const
  for (const [label, value] of required) {
    if (!value || String(value).trim() === '') return void ElMessage.warning(`请填写${label}`)
  }
  if (saving.value || detailLoading.value) return
  saving.value = true
  submitError.value = ''
  try {
    const payload: ParameterUpdateData = {
      parameterType: form.value.parameterType,
      parameterScope: form.value.parameterScope,
      systemCode: form.value.systemCode,
      parameterName: form.value.parameterName.trim(),
      parameterDescription: form.value.parameterDescription?.trim() || undefined,
    }
    if (editing.value && editId.value) {
      await updateParameter(editId.value, payload)
      ElMessage.success('迁移参数已更新')
    } else {
      if (!scopeProjectId.value) { ElMessage.warning('当前项目不可用，请重新选择项目'); return }
      await createParameter({ ...payload, projectId: scopeProjectId.value })
      ElMessage.success('迁移参数已新增')
    }
    drawerOpen.value = false
    resetForm()
    await load()
  } catch (cause) {
    submitError.value = messageOf(cause)
    ElMessage.error(submitError.value)
  } finally { saving.value = false }
}

// ============ 详情 ============
async function openDetail(item: ParameterRecord) {
  detailOpen.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    const response = await getParameter(item.id)
    detail.value = response.data.data ?? null
  } catch (cause) {
    detailError.value = messageOf(cause, '详情加载失败')
  } finally { detailLoading.value = false }
}

// ============ Excel 批量导入 ============
function chooseImportFile() { importInput.value?.click() }

function onImportFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  pendingImportFile.value = file
  importResult.value = null
  if (file) ElMessage.info(`已选择 ${file.name}，请点击“提交导入”`)
  if (importInput.value) importInput.value.value = ''
}

async function submitImport() {
  const pid = scopeProjectId.value
  const file = pendingImportFile.value
  if (!pid) return void ElMessage.warning('当前项目不可用，请重新选择项目')
  if (!file) return void ElMessage.warning('请先选择 Excel 文件')
  actionBusy.value = true
  importResult.value = null
  try {
    const response = await importParameters({ projectId: pid }, file)
    const data = response.data.data
    importResult.value = data ?? { rows: 0, accepted: 0, failed: 0, errors: [] }
    const text = `导入完成：共 ${data?.rows ?? 0} 行，成功 ${data?.accepted ?? 0} 行，失败 ${data?.failed ?? 0} 行`
    if (data?.errors?.length) ElMessage.warning(text)
    else ElMessage.success(text)
    pendingImportFile.value = null
    await load()
  } catch (cause) {
    ElMessage.error(messageOf(cause, '导入失败'))
  } finally { actionBusy.value = false }
}

// ============ 模板 / 条件下载 ============
function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

async function downloadTemplate() {
  try {
    const response = await downloadParameterTemplate()
    downloadBlob(response.data, '迁移参数导入模板.xlsx')
  } catch (cause) {
    ElMessage.error(messageOf(cause, '模板下载失败'))
  }
}

async function exportData() {
  const pid = scopeProjectId.value
  if (!pid) return void ElMessage.warning('当前项目不可用，请重新选择项目')
  actionBusy.value = true
  try {
    const response = await exportParameters({
      projectId: pid,
      parameterType: parameterType.value || undefined,
      parameterScope: parameterScope.value || undefined,
      systemCode: systemCode.value || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    downloadBlob(response.data, '迁移参数.xlsx')
  } catch (cause) {
    ElMessage.error(messageOf(cause, '导出失败'))
  } finally { actionBusy.value = false }
}

// ============ 删除 ============
async function deleteIds(ids: number[], tip: string) {
  try {
    await ElMessageBox.confirm(tip, '删除', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    actionBusy.value = true
    await deleteParameters(ids)
    ElMessage.success('已删除')
    await load()
  } catch (cause) {
    if (!cancelled(cause)) ElMessage.error(messageOf(cause))
  } finally { actionBusy.value = false }
}

async function removeSelected() {
  if (!selectedIds.value.length) return
  await deleteIds(selectedIds.value, `确认将选中的 ${selectedIds.value.length} 条迁移参数删除吗？删除后进入回收站。`)
}

async function removeItem(item: ParameterRecord) {
  await deleteIds([item.id], `确认删除参数名称「${item.parameter_name}」吗？删除后进入回收站。`)
}

onMounted(() => {
  void scope.ensureLoaded()
  void loadOptions()
})

watch(scopeProjectId, () => {
  records.value = []
  selectedIds.value = []
  page.value = 1
  parameterType.value = ''
  parameterScope.value = ''
  systemCode.value = ''
  keyword.value = ''
  pendingImportFile.value = null
  importResult.value = null
  error.value = ''
  forbidden.value = false
  void loadSystems()
  void load()
}, { immediate: true })

function typeLabel(value?: string) {
  return typeOptions.value.find(option => option.value === value)?.label ?? value ?? ''
}
function scopeLabel(value?: string) {
  return scopeOptions.value.find(option => option.value === value)?.label ?? value ?? ''
}
function systemLabel(value?: string) {
  const option = systemOptions.value.find(item => item.value === value)
  return option ? String(option.label) : (value ?? '')
}
</script>

<template>
  <main class="dm-page-root">
    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <section v-else-if="forbidden" class="dm-state-panel">
      <el-result icon="warning" :title="'暂无迁移参数查看权限'" sub-title="请向数据迁移管理员申请 data-migration:content:parameters 权限。" />
    </section>
    <section v-else-if="error" class="dm-state-panel">
      <el-result icon="error" :title="'迁移参数加载失败'" :sub-title="error">
        <template #extra><el-button type="primary" @click="search">重新加载</el-button></template>
      </el-result>
    </section>
    <template v-else>
      <UiToolbar>
        <el-select v-model="parameterType" clearable placeholder="参数类型" style="width: 180px" @change="search">
          <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="parameterScope" clearable placeholder="参数范围分类" style="width: 180px" @change="search">
          <el-option v-for="opt in scopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="systemCode" clearable filterable placeholder="关联组件" style="width: 220px" @change="search">
          <el-option v-for="opt in systemOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input v-model="keyword" clearable placeholder="参数名称 / 参数说明" style="width: 220px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="!hasFilters" @click="resetFilters"><el-icon><Refresh /></el-icon>重置</el-button>
          <el-button v-if="hasCreate" :disabled="actionBusy" type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增参数</el-button>
          <el-button :disabled="actionBusy" @click="downloadTemplate"><el-icon><Document /></el-icon>下载模板</el-button>
          <input ref="importInput" class="dm-hidden" type="file" accept=".xlsx" @change="onImportFileChange">
          <el-button v-if="hasCreate" :disabled="actionBusy" @click="chooseImportFile"><el-icon><UploadFilled /></el-icon>批量导入</el-button>
          <el-button v-if="pendingImportFile" :disabled="actionBusy" type="primary" plain @click="submitImport">提交导入</el-button>
          <el-button :disabled="actionBusy" @click="exportData"><el-icon><Download /></el-icon>导出</el-button>
          <el-button v-if="hasDelete" type="danger" plain :disabled="!selectedIds.length || actionBusy" @click="removeSelected"><el-icon><Delete /></el-icon>删除 ({{ selectedIds.length }})</el-button>
        </template>
      </UiToolbar>

      <p v-if="importResult" class="dm-import-result" role="status">
        导入完成：共 {{ importResult.rows }} 行，成功 {{ importResult.accepted }} 行，失败 {{ importResult.failed }} 行
        <span v-if="importResult.errors?.length" class="dm-import-detail">（首个失败：{{ importResult.errors[0] }}）</span>
      </p>

      <UiDataTable v-if="records.length || loading" :data="records" :loading="loading" row-key="id" border empty-text="暂无迁移参数" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="project_name" label="项目名称" min-width="150" />
        <el-table-column label="参数类型" min-width="110">
          <template #default="scopeRow">{{ typeLabel(scopeRow.row.parameter_type_name || scopeRow.row.parameter_type) }}</template>
        </el-table-column>
        <el-table-column label="参数范围分类" min-width="120">
          <template #default="scopeRow">{{ scopeLabel(scopeRow.row.parameter_scope_name || scopeRow.row.parameter_scope) }}</template>
        </el-table-column>
        <el-table-column prop="system_code" label="系统编号" min-width="140" />
        <el-table-column prop="system_name" label="系统名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="parameter_name" label="参数名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="parameter_description" label="参数说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scopeRow">
            <el-button v-if="canEditItem(scopeRow.row)" link type="primary" :disabled="actionBusy" @click="openEdit(scopeRow.row)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button v-if="canDeleteItem(scopeRow.row)" link type="danger" :disabled="actionBusy" @click="removeItem(scopeRow.row)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
        </el-table-column>
      </UiDataTable>
      <UiEmptyState v-if="!loading && !records.length" title="暂无迁移参数" description="调整筛选条件，或新增 / 批量导入迁移参数。" />
      <UiPagination :page="page" :page-size="size" :total="total" :page-sizes="[20, 50, 100]"
        @update:page-size="(v: number) => { size = v; page = 1; load() }" @update:page="(v: number) => { page = v; load() }" />
    </template>

    <UiFormDrawer v-model="drawerOpen" :title="editing ? '编辑迁移参数' : '新增迁移参数'" :loading="saving" @submit="handleSave">
      <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" show-icon style="margin-bottom: 12px" />
      <el-form label-position="top">
        <el-form-item label="参数类型" required>
          <el-select v-model="form.parameterType" :loading="optionLoading" placeholder="请选择（系统管理/参数管理维护）" style="width: 100%">
            <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数范围分类" required>
          <el-select v-model="form.parameterScope" :loading="optionLoading" placeholder="请选择（系统管理/参数管理维护）" style="width: 100%">
            <el-option v-for="opt in scopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联系统" required>
          <el-select v-model="form.systemCode" filterable placeholder="请选择当前项目下系统" style="width: 100%">
            <el-option v-for="opt in systemOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数名称" required>
          <el-input v-model="form.parameterName" maxlength="255" placeholder="必填，同项目同系统下唯一" />
        </el-form-item>
        <el-form-item label="参数说明">
          <el-input v-model="form.parameterDescription" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="描述参数用途、取值规则、适用场景（可选）" />
        </el-form-item>
      </el-form>
    </UiFormDrawer>

    <el-dialog v-model="detailOpen" title="迁移参数详情" width="520px">
      <div v-loading="detailLoading" style="min-height: 60px">
        <el-result v-if="detailError" icon="error" :title="detailError" />
        <el-descriptions v-else-if="detail" :column="1" border>
          <el-descriptions-item label="所属项目">{{ detail.project_name ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="参数类型">{{ typeLabel(detail.parameter_type_name || detail.parameter_type) }}</el-descriptions-item>
          <el-descriptions-item label="参数范围分类">{{ scopeLabel(detail.parameter_scope_name || detail.parameter_scope) }}</el-descriptions-item>
          <el-descriptions-item label="系统编号">{{ detail.system_code ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="系统名称">{{ detail.system_name ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="参数名称">{{ detail.parameter_name ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="参数说明">
            <div style="white-space: pre-wrap">{{ detail.parameter_description ?? '—' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="上传人">{{ detail.created_by_name ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="上传时间">{{ detail.created_at ?? '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else-if="!detailLoading" description="暂无详情" />
      </div>
      <template #footer>
        <el-button @click="detailOpen = false">关闭</el-button>
        <el-button v-if="detail && canEditItem(detail)" type="primary" @click="detailOpen = false; openEdit(detail)">编辑</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.dm-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.dm-import-result { margin: 0 0 12px; color: var(--success); font-size: 13px; }
.dm-import-detail { color: var(--warning); }
</style>
