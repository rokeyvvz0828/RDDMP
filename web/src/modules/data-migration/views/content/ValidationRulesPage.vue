<!--
  用途：数迁资产内容 - 迁移检核规则页（REQ-20260820-031 增量，对标迁移映射专属域）
  说明：专属域页面，复用 UiToolbar/UiDataTable/UiFormDrawer/UiEmptyState/UiPagination/ProjectScopeState；
        覆盖加载/空/失败/无权限/提交中/重复提交与部分导入失败状态。Excel 选择文件后不立即上传，
        需在批量导入对话框中确认才上传解析；导出为准当前筛选条件；删除为逻辑删除并进入统一回收站。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, genFileId, type UploadFile, type UploadInstance, type UploadProps, type UploadRawFile } from 'element-plus'
import { Delete, Document, Download, Edit, Plus, Refresh, Search, UploadFilled, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { useAuthStore } from '../../../../stores/auth'
import { apiErrorMessage } from '../../../../api/error'
import {
  DM_CODE_CATEGORIES,
  createMigrationCheckRule,
  deleteMigrationCheckRules,
  downloadRuleTemplate,
  exportMigrationCheckRules,
  getDataMigrationParamOptions,
  getMigrationCheckRule,
  getSystemOptions,
  importMigrationCheckRules,
  listMigrationCheckRules,
  updateMigrationCheckRule,
  type MigrationCheckRule,
  type RuleFormData,
  type RuleImportResult,
  type RuleUpdateData,
  type SelectOption,
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'

const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const hasCreate = computed(() => auth.hasPermission('data-migration:content:validation-rules:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdate = computed(() => auth.hasPermission('data-migration:content:validation-rules:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDelete = computed(() => auth.hasPermission('data-migration:content:validation-rules:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEditItem = (item: MigrationCheckRule) => hasUpdate.value && (canManage.value || item.created_by === auth.user?.id)
const canDeleteItem = (item: MigrationCheckRule) => hasDelete.value && (canManage.value || item.created_by === auth.user?.id)

const loading = ref(false)
const optionLoading = ref(false)
const actionBusy = ref(false)
const error = ref('')
const forbidden = ref(false)
const records = ref<MigrationCheckRule[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selectedIds = ref<number[]>([])
const keyword = ref('')
const ruleKeyword = ref('')
const checkTargetType = ref('')
const ruleCategory = ref('')
const systemCode = ref('')
const targetTypeOptions = ref<SelectOption[]>([])
const categoryOptions = ref<SelectOption[]>([])
const systemOptions = ref<SelectOption[]>([])
const optionError = ref('')

const drawerOpen = ref(false)
const saving = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const submitError = ref('')
const form = ref<RuleFormData>({
  projectId: 0, checkTargetType: '', ruleCategory: '', systemCode: '',
  ruleCode: '', ruleCodeDesc: '', ruleDescription: '',
  tableNameEn: '', tableNameCn: '', fieldNameEn: '', fieldNameCn: '',
})

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<MigrationCheckRule | null>(null)
const detailError = ref('')

const importDialogOpen = ref(false)
const importUploadRef = ref<UploadInstance>()
const pendingImportFile = ref<File | null>(null)
const importResult = ref<RuleImportResult | null>(null)
const importError = ref('')

const importReady = computed(() => Boolean(scopeProjectId.value))
const canImport = computed(() => hasCreate.value && importReady.value)
const canSubmitImport = computed(() => canImport.value && Boolean(pendingImportFile.value) && !actionBusy.value)
const hasFilters = computed(() => Boolean(checkTargetType.value || ruleCategory.value || systemCode.value || ruleKeyword.value.trim() || keyword.value.trim()))

const messageOf = (cause: unknown, fallback = '操作失败，请稍后重试') => cause instanceof Error && cause.message ? cause.message : fallback
const httpStatus = (cause: unknown) => (cause as { response?: { status?: number } }).response?.status
const cancelled = (cause: unknown) => (cause as { action?: string }).action === 'cancel' || (cause as { action?: string }).action === 'close'

function resetPagination() { page.value = 1 }

async function loadOptions() {
  optionLoading.value = true
  optionError.value = ''
  try {
    const [target, category] = await Promise.all([
      getDataMigrationParamOptions(DM_CODE_CATEGORIES.ruleTargetType),
      getDataMigrationParamOptions(DM_CODE_CATEGORIES.ruleCategory),
    ])
    targetTypeOptions.value = target.data.data ?? []
    categoryOptions.value = category.data.data ?? []
  } catch (cause) {
    optionError.value = messageOf(cause, '选项加载失败')
  } finally { optionLoading.value = false }
}

async function loadSystems() {
  const projectId = scopeProjectId.value
  if (projectId == null) { systemOptions.value = []; return }
  try {
    const response = await getSystemOptions(projectId)
    systemOptions.value = response.data.data ?? []
  } catch (cause) {
    optionError.value = messageOf(cause, '关联系统选项加载失败')
  }
}

async function load() {
  const projectId = scopeProjectId.value
  if (projectId == null) { records.value = []; total.value = 0; selectedIds.value = []; return }
  loading.value = true
  error.value = ''
  forbidden.value = false
  selectedIds.value = []
  try {
    const response = await listMigrationCheckRules({
      projectId,
      checkTargetType: checkTargetType.value || undefined,
      ruleCategory: ruleCategory.value || undefined,
      systemCode: systemCode.value || undefined,
      ruleKeyword: ruleKeyword.value.trim() || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value,
    })
    records.value = response.data.data?.records ?? []
    total.value = response.data.data?.total ?? 0
  } catch (cause) {
    if (httpStatus(cause) === 403) forbidden.value = true
    else error.value = apiErrorMessage(cause, '迁移检核规则加载失败')
  } finally { loading.value = false }
}

function search() { resetPagination(); void load() }
function resetFilters() { checkTargetType.value = ''; ruleCategory.value = ''; systemCode.value = ''; ruleKeyword.value = ''; keyword.value = ''; search() }

function onSelectionChange(rows: MigrationCheckRule[]) { selectedIds.value = rows.map(row => row.id) }

// ============ 新增 / 编辑 ============
function resetForm() {
  form.value = { projectId: 0, checkTargetType: '', ruleCategory: '', systemCode: '', ruleCode: '', ruleCodeDesc: '', ruleDescription: '', tableNameEn: '', tableNameCn: '', fieldNameEn: '', fieldNameCn: '' }
  submitError.value = ''
}

function openCreate() {
  editing.value = false
  editId.value = null
  resetForm()
  form.value.projectId = scopeProjectId.value ?? 0
  drawerOpen.value = true
}

async function openEdit(item: MigrationCheckRule) {
  editing.value = true
  editId.value = item.id
  resetForm()
  submitError.value = ''
  detailLoading.value = true
  drawerOpen.value = true
  try {
    const response = await getMigrationCheckRule(item.id)
    const data = response.data.data
    if (!data) throw new Error('规则详情为空')
    form.value = {
      projectId: data.project_id,
      checkTargetType: data.check_target_type,
      ruleCategory: data.rule_category,
      systemCode: data.system_code,
      ruleCode: data.rule_code ?? '',
      ruleCodeDesc: data.rule_code_desc ?? '',
      ruleDescription: data.rule_description ?? '',
      tableNameEn: data.table_name_en ?? '',
      tableNameCn: data.table_name_cn ?? '',
      fieldNameEn: data.field_name_en ?? '',
      fieldNameCn: data.field_name_cn ?? '',
    }
  } catch (cause) {
    submitError.value = messageOf(cause, '规则详情加载失败')
    drawerOpen.value = false
  } finally { detailLoading.value = false }
}

async function handleSave() {
  const required = [
    ['检核目标类型', form.value.checkTargetType], ['检核规则大类', form.value.ruleCategory],
    ['关联系统', form.value.systemCode], ['规则编码', form.value.ruleCode],
  ] as const
  for (const [label, value] of required) {
    if (!value || String(value).trim() === '') return void ElMessage.warning(`请填写${label}`)
  }
  if (saving.value || detailLoading.value) return
  saving.value = true
  submitError.value = ''
  try {
    const payload: RuleUpdateData = {
      checkTargetType: form.value.checkTargetType,
      ruleCategory: form.value.ruleCategory,
      systemCode: form.value.systemCode,
      ruleCode: form.value.ruleCode.trim(),
      ruleCodeDesc: form.value.ruleCodeDesc?.trim() || undefined,
      ruleDescription: form.value.ruleDescription?.trim() || undefined,
      tableNameEn: form.value.tableNameEn?.trim() || undefined,
      tableNameCn: form.value.tableNameCn?.trim() || undefined,
      fieldNameEn: form.value.fieldNameEn?.trim() || undefined,
      fieldNameCn: form.value.fieldNameCn?.trim() || undefined,
    }
    if (editing.value && editId.value) {
      await updateMigrationCheckRule(editId.value, payload)
      ElMessage.success('迁移检核规则已更新')
    } else {
      if (!scopeProjectId.value) { ElMessage.warning('当前项目不可用，请重新选择项目'); return }
      await createMigrationCheckRule({ ...payload, projectId: scopeProjectId.value })
      ElMessage.success('迁移检核规则已新增')
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
async function openDetail(item: MigrationCheckRule) {
  detailOpen.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    const response = await getMigrationCheckRule(item.id)
    detail.value = response.data.data ?? null
  } catch (cause) {
    detailError.value = messageOf(cause, '详情加载失败')
  } finally { detailLoading.value = false }
}

// ============ Excel 批量导入（选择文件不自动上传，确认后才上传解析） ============
function resetImportDialog() {
  pendingImportFile.value = null
  importResult.value = null
  importError.value = ''
  importUploadRef.value?.clearFiles()
}

function openImportDialog() {
  resetImportDialog()
  importDialogOpen.value = true
}

function onImportFileChange(file: UploadFile) {
  pendingImportFile.value = file.raw ?? null
  importResult.value = null
  importError.value = ''
}

const onImportFileExceed: UploadProps['onExceed'] = (files) => {
  importUploadRef.value?.clearFiles()
  const file = files[0] as UploadRawFile
  file.uid = genFileId()
  importUploadRef.value?.handleStart(file)
}

function onImportFileRemove() {
  pendingImportFile.value = null
  importResult.value = null
  importError.value = ''
}

function clearImportFile() {
  importUploadRef.value?.clearFiles()
  onImportFileRemove()
}

function beforeImportDialogClose(done: () => void) {
  if (!actionBusy.value) done()
}

function closeImportDialog() {
  if (!actionBusy.value) importDialogOpen.value = false
}

async function submitImport() {
  const projectId = scopeProjectId.value
  const file = pendingImportFile.value
  if (!projectId) return void ElMessage.warning('当前项目不可用，请重新选择项目')
  if (!file) return void ElMessage.warning('请先选择 Excel 文件')
  if (actionBusy.value) return
  actionBusy.value = true
  importResult.value = null
  importError.value = ''
  try {
    const response = await importMigrationCheckRules({ projectId }, file)
    const data = response.data.data
    importResult.value = data ?? { rows: 0, accepted: 0, failed: 0, errors: [] }
    const text = `导入完成：共 ${data?.rows ?? 0} 行，成功 ${data?.accepted ?? 0} 行，失败 ${data?.failed ?? 0} 行`
    if (data?.errors?.length) ElMessage.warning(text)
    else ElMessage.success(text)
    await load()
  } catch (cause) {
    importError.value = apiErrorMessage(cause, '导入失败')
    ElMessage.error(importError.value)
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
  if (actionBusy.value) return
  actionBusy.value = true
  try {
    const response = await downloadRuleTemplate()
    downloadBlob(response.data, '迁移检核规则模板.xlsx')
  } catch (cause) {
    ElMessage.error(messageOf(cause, '模板下载失败'))
  } finally { actionBusy.value = false }
}

async function exportRules() {
  const projectId = scopeProjectId.value
  if (!projectId) return void ElMessage.warning('当前项目不可用，请重新选择项目')
  actionBusy.value = true
  try {
    const response = await exportMigrationCheckRules({
      projectId,
      checkTargetType: checkTargetType.value || undefined,
      ruleCategory: ruleCategory.value || undefined,
      systemCode: systemCode.value || undefined,
      ruleKeyword: ruleKeyword.value.trim() || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    downloadBlob(response.data, '迁移检核规则.xlsx')
  } catch (cause) {
    ElMessage.error(messageOf(cause, '导出失败'))
  } finally { actionBusy.value = false }
}

// ============ 删除 ============
async function deleteIds(ids: number[], tip: string) {
  try {
    await ElMessageBox.confirm(tip, '删除', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    actionBusy.value = true
    await deleteMigrationCheckRules(ids)
    ElMessage.success('已删除')
    await load()
  } catch (cause) {
    if (!cancelled(cause)) ElMessage.error(messageOf(cause))
  } finally { actionBusy.value = false }
}

async function removeSelected() {
  if (!selectedIds.value.length) return
  await deleteIds(selectedIds.value, `确认将选中的 ${selectedIds.value.length} 条迁移检核规则删除吗？删除后进入回收站。`)
}

async function removeItem(item: MigrationCheckRule) {
  await deleteIds([item.id], `确认删除规则编码「${item.rule_code}」吗？删除后进入回收站。`)
}

function targetLabel(value?: string) {
  return targetTypeOptions.value.find(option => option.value === value)?.label ?? value ?? ''
}
function categoryLabel(value?: string) {
  return categoryOptions.value.find(option => option.value === value)?.label ?? value ?? ''
}

onMounted(() => {
  void scope.ensureLoaded()
  void loadOptions()
})

watch(scopeProjectId, () => {
  records.value = []
  selectedIds.value = []
  page.value = 1
  checkTargetType.value = ''
  ruleCategory.value = ''
  systemCode.value = ''
  keyword.value = ''
  ruleKeyword.value = ''
  importDialogOpen.value = false
  resetImportDialog()
  error.value = ''
  forbidden.value = false
  void loadSystems()
  void load()
}, { immediate: true })

function systemLabel(value?: string) {
  const option = systemOptions.value.find(item => item.value === value)
  return option ? String(option.label) : (value ?? '')
}
</script>

<template>
  <main class="dm-page-root">
    <UiPageHeader title="迁移检核规则" description="列表、新增与导入均固定属于顶部项目切换器选择的当前项目。">
      <template #actions>
        <el-button v-if="hasCreate && scopeState === 'ready' && !forbidden && !error" :disabled="loading || actionBusy" @click="openImportDialog">
          <el-icon><UploadFilled /></el-icon>批量导入
        </el-button>
        <el-button v-if="hasCreate && scopeState === 'ready' && !forbidden && !error" type="primary" :disabled="loading || actionBusy" @click="openCreate">
          <el-icon><Plus /></el-icon>新增迁移检核规则
        </el-button>
      </template>
    </UiPageHeader>

    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <section v-else-if="forbidden" class="dm-state-panel">
      <el-result icon="warning" :title="'暂无迁移检核规则查看权限'" sub-title="请向数据迁移管理员申请 data-migration:content:validation-rules 权限。" />
    </section>
    <section v-else-if="error" class="dm-state-panel">
      <el-result icon="error" :title="'迁移检核规则加载失败'" :sub-title="error">
        <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
      </el-result>
    </section>
    <template v-else>
      <UiToolbar>
        <el-select v-model="checkTargetType" :loading="optionLoading" clearable filterable placeholder="检核目标类型" style="width: 180px" @change="search">
          <el-option v-for="option in targetTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="ruleCategory" :loading="optionLoading" clearable filterable placeholder="检核规则大类" style="width: 160px" @change="search">
          <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="systemCode" clearable filterable placeholder="关联系统(可输入过滤)" style="width: 200px" @change="search">
          <el-option v-for="option in systemOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-input v-model="ruleKeyword" clearable placeholder="规则编码/说明关键字" style="width: 200px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input v-model="keyword" clearable placeholder="表/字段名称关键字" style="width: 190px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="optionLoading || loading" @click="resetFilters"><el-icon><Refresh /></el-icon>重置</el-button>
          <el-button :disabled="actionBusy" @click="exportRules"><el-icon><Download /></el-icon>导出</el-button>
          <el-button v-if="hasDelete" type="danger" plain :disabled="!selectedIds.length || actionBusy" @click="removeSelected"><el-icon><Delete /></el-icon>删除 ({{ selectedIds.length }})</el-button>
        </template>
      </UiToolbar>

      <el-alert v-if="optionError" class="dm-state-alert" type="error" :closable="false" show-icon :title="optionError" />

      <UiDataTable v-if="records.length || loading" :data="records" :loading="loading" row-key="id" border empty-text="暂无迁移检核规则" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column label="检核目标类型" min-width="130">
          <template #default="scope"><span>{{ targetLabel(scope.row.check_target_type) }}</span></template>
        </el-table-column>
        <el-table-column label="检核规则大类" min-width="120">
          <template #default="scope"><span>{{ categoryLabel(scope.row.rule_category) }}</span></template>
        </el-table-column>
        <el-table-column prop="system_code" label="系统编号" min-width="120" />
        <el-table-column prop="system_name" label="系统名称" min-width="140" />
        <el-table-column prop="table_name_en" label="表英文名" min-width="140" />
        <el-table-column prop="table_name_cn" label="表中文名" min-width="140" />
        <el-table-column prop="field_name_en" label="字段英文名称" min-width="150" />
        <el-table-column prop="field_name_cn" label="字段中文名称" min-width="150" />
        <el-table-column label="规则编码" min-width="160">
          <template #default="scope"><el-button link type="primary" @click="openDetail(scope.row)">{{ scope.row.rule_code }}</el-button></template>
        </el-table-column>
        <el-table-column label="规则编码说明" min-width="200">
          <template #default="scope"><el-button link type="primary" @click="openDetail(scope.row)">{{ scope.row.rule_code_desc ?? '-' }}</el-button></template>
        </el-table-column>
        <el-table-column prop="rule_description" label="检核规则说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="scope">
            <el-button v-if="canEditItem(scope.row)" link type="primary" :disabled="actionBusy" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
            <el-button v-if="canDeleteItem(scope.row)" link type="danger" :disabled="actionBusy" @click="removeItem(scope.row)"><el-icon><Delete /></el-icon>删除</el-button>
            <el-button link type="primary" @click="openDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
          </template>
        </el-table-column>
      </UiDataTable>
      <UiEmptyState v-if="!loading && !records.length" title="暂无迁移检核规则" description="调整筛选条件，或通过 Excel 批量导入。" />

      <div v-if="total > 0" class="dm-table-footer">
        <span>共 {{ total }} 条</span>
        <UiPagination :page="page" :page-size="size" :total="total" :page-sizes="[20, 50, 100]"
          @update:page-size="(value: number) => { size = value; page = 1; load() }"
          @update:page="(value: number) => { page = value; load() }" />
      </div>
    </template>

    <UiFormDrawer v-model="drawerOpen" :title="editing ? '编辑迁移检核规则' : '新增迁移检核规则'" :loading="saving || detailLoading" @submit="handleSave">
      <el-alert v-if="submitError" class="dm-state-alert" type="error" :closable="false" show-icon :title="submitError" />
      <el-form label-position="top" :disabled="saving || detailLoading">
        <el-form-item label="检核目标类型" required>
          <el-select v-model="form.checkTargetType" :loading="optionLoading" placeholder="请选择检核目标类型" style="width:100%">
            <el-option v-for="option in targetTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="检核规则大类" required>
          <el-select v-model="form.ruleCategory" :loading="optionLoading" placeholder="请选择检核规则大类" style="width:100%">
            <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联系统" required>
          <el-select v-model="form.systemCode" filterable placeholder="请选择关联系统" style="width:100%">
            <el-option v-for="option in systemOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则编码" required>
          <el-input v-model="form.ruleCode" placeholder="全局唯一规则编码" maxlength="96" />
        </el-form-item>
        <el-form-item label="规则编码说明">
          <el-input v-model="form.ruleCodeDesc" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="检核规则说明">
          <el-input v-model="form.ruleDescription" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="表英文名">
          <el-input v-model="form.tableNameEn" maxlength="255" />
        </el-form-item>
        <el-form-item label="表中文名">
          <el-input v-model="form.tableNameCn" maxlength="255" />
        </el-form-item>
        <el-form-item label="字段英文名称">
          <el-input v-model="form.fieldNameEn" maxlength="255" />
        </el-form-item>
        <el-form-item label="字段中文名称">
          <el-input v-model="form.fieldNameCn" maxlength="255" />
        </el-form-item>
      </el-form>
    </UiFormDrawer>

    <el-dialog
      v-model="importDialogOpen"
      class="dm-import-dialog"
      title="批量导入迁移检核规则"
      width="min(720px, calc(100vw - 24px))"
      destroy-on-close
      :close-on-click-modal="!actionBusy"
      :close-on-press-escape="!actionBusy"
      :show-close="!actionBusy"
      :before-close="beforeImportDialogClose"
      @closed="resetImportDialog"
    >
      <div class="dm-import-dialog-body">
        <el-alert title="请使用迁移检核规则模板填写数据，选择 Excel 文件后确认导入。" type="info" :closable="false" show-icon />

        <div class="dm-import-template-row">
          <span>模板包含检核目标类型、规则大类、关联系统及规则字段。</span>
          <el-button :disabled="actionBusy" @click="downloadTemplate"><el-icon><Document /></el-icon>下载模板</el-button>
        </div>

        <el-upload
          ref="importUploadRef"
          class="dm-upload-dropzone"
          drag
          :auto-upload="false"
          :limit="1"
          accept=".xlsx,.xls"
          :show-file-list="false"
          :disabled="actionBusy"
          :on-change="onImportFileChange"
          :on-exceed="onImportFileExceed"
          :on-remove="onImportFileRemove"
        >
          <el-icon class="dm-upload-icon"><UploadFilled /></el-icon>
          <div class="el-upload__text">将 Excel 文件拖到此处，或 <em>点击选择</em></div>
          <template #tip><div class="dm-upload-hint">支持 .xlsx、.xls，单次选择一个文件</div></template>
        </el-upload>

        <div v-if="pendingImportFile" class="dm-attachment-section">
          <div class="dm-attachment-section-title">已选择文件</div>
          <div class="dm-attachment-list">
            <div class="dm-attachment-item is-pending">
              <span class="dm-attachment-icon is-pending"><el-icon><Document /></el-icon></span>
              <div class="dm-attachment-info">
                <div class="dm-attachment-name" :title="pendingImportFile.name">{{ pendingImportFile.name }}</div>
                <div class="dm-attachment-meta">Excel 文件 · 待导入</div>
              </div>
              <div class="dm-attachment-actions">
                <el-button circle plain type="danger" :disabled="actionBusy" title="移除文件" aria-label="移除文件" @click="clearImportFile">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <el-alert v-if="importError" class="dm-import-result" type="error" :closable="false" show-icon title="导入请求失败">
          <template #default><div class="dm-import-message">{{ importError }}</div></template>
        </el-alert>
        <el-alert
          v-if="importResult"
          class="dm-import-result"
          :type="importResult.failed > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
          :title="`导入完成：共 ${importResult.rows} 行，成功 ${importResult.accepted} 行，失败 ${importResult.failed} 行`"
        >
          <template v-if="importResult.errors?.length" #default>
            <ul class="dm-import-errors">
              <li v-for="(item, index) in importResult.errors.slice(0, 20)" :key="index">{{ item }}</li>
              <li v-if="importResult.errors.length > 20">剩余 {{ importResult.errors.length - 20 }} 条错误未展示</li>
            </ul>
          </template>
        </el-alert>
      </div>

      <template #footer>
        <el-button :disabled="actionBusy" @click="closeImportDialog">取消</el-button>
        <el-button type="primary" :loading="actionBusy" :disabled="!canSubmitImport" @click="submitImport">确认导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" title="迁移检核规则详情" width="min(680px, 94vw)">
      <div v-loading="detailLoading" class="dm-detail-box">
        <el-alert v-if="detailError" class="dm-state-alert" type="error" :closable="false" show-icon :title="detailError" />
        <el-descriptions v-if="detail" :column="1" border>
          <el-descriptions-item label="检核目标类型">{{ targetLabel(detail.check_target_type) }}</el-descriptions-item>
          <el-descriptions-item label="检核规则大类">{{ categoryLabel(detail.rule_category) }}</el-descriptions-item>
          <el-descriptions-item label="系统编号">{{ detail.system_code ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="系统名称">{{ detail.system_name ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="表英文名">{{ detail.table_name_en ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="表中文名">{{ detail.table_name_cn ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="字段英文名称">{{ detail.field_name_en ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="字段中文名称">{{ detail.field_name_cn ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="规则编码">{{ detail.rule_code }}</el-descriptions-item>
          <el-descriptions-item label="规则编码说明">{{ detail.rule_code_desc ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="检核规则说明">{{ detail.rule_description ?? '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.dm-state-alert { margin: 0 0 12px; }
.dm-import-dialog-body { min-width: 0; max-height: min(60vh, 520px); padding-right: 2px; overflow-x: hidden; overflow-y: auto; }
.dm-import-template-row { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; margin: 14px 0; color: var(--muted); font-size: 13px; }
.dm-import-template-row span { min-width: 0; overflow-wrap: anywhere; }
.dm-import-template-row .el-button { flex: 0 0 auto; }
.dm-import-result { margin-top: 14px; }
.dm-import-message { overflow-wrap: anywhere; }
.dm-import-errors { max-height: 180px; margin: 8px 0 0; padding-left: 18px; overflow: auto; overflow-wrap: anywhere; font-size: 12px; }
.dm-detail-box { min-height: 120px; }

@media (max-width: 760px) {
  .dm-import-template-row { align-items: stretch; flex-direction: column; }
  .dm-import-template-row .el-button { align-self: flex-start; }
}
</style>
