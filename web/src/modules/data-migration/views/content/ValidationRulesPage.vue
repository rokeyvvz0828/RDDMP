<!--
  用途：数迁资产内容 - 迁移检核规则页（REQ-20260820-031 增量，对标迁移映射专属域）
  说明：专属域页面，复用 UiToolbar/UiDataTable/UiFormDrawer/UiEmptyState/UiPagination/ProjectScopeState；
        覆盖加载/空/失败/无权限/提交中/重复提交与部分导入失败状态。Excel 选择文件后不立即上传，
        需点“提交导入”才上传解析；导出为准当前筛选条件；删除为逻辑删除并进入统一回收站。
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

const importInput = ref<HTMLInputElement | null>(null)
const pendingImportFile = ref<File | null>(null)
const importResult = ref<RuleImportResult | null>(null)

const importReady = computed(() => Boolean(scopeProjectId.value))
const canImport = computed(() => hasCreate.value && importReady.value)
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

// ============ Excel 批量导入（选择文件不自动上传，点“提交导入”才上传解析） ============
function chooseImportFile() { importInput.value?.click() }

function onImportFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null
  pendingImportFile.value = file
  importResult.value = null
  if (file) ElMessage.info(`已选择 ${file.name}，点击“提交导入”按文件内容逐行导入`)
  if (importInput.value) importInput.value.value = ''
}

async function submitImport() {
  const projectId = scopeProjectId.value
  const file = pendingImportFile.value
  if (!projectId) return void ElMessage.warning('当前项目不可用，请重新选择项目')
  if (!file) return void ElMessage.warning('请先选择 Excel 文件')
  actionBusy.value = true
  importResult.value = null
  try {
    const response = await importMigrationCheckRules({ projectId }, file)
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
    const response = await downloadRuleTemplate()
    downloadBlob(response.data, '迁移检核规则模板.xlsx')
  } catch (cause) {
    ElMessage.error(messageOf(cause, '模板下载失败'))
  }
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
  pendingImportFile.value = null
  importResult.value = null
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
          <el-button :disabled="loading || actionBusy" @click="downloadTemplate"><el-icon><Document /></el-icon>模板下载</el-button>
          <input ref="importInput" class="dm-hidden-file" type="file" accept=".xlsx" @change="onImportFileChange">
          <el-button v-if="hasCreate" :disabled="actionBusy" @click="chooseImportFile"><el-icon><UploadFilled /></el-icon>{{ pendingImportFile ? '已选文件' : '选择 Excel' }}</el-button>
          <el-button v-if="hasCreate" type="primary" plain :disabled="!canImport || actionBusy" @click="submitImport"><el-icon><UploadFilled /></el-icon>提交导入</el-button>
          <el-button :disabled="actionBusy" @click="exportRules"><el-icon><Download /></el-icon>导出</el-button>
          <el-button v-if="hasCreate" @click="openCreate"><el-icon><Plus /></el-icon>新增</el-button>
          <el-button v-if="hasDelete" type="danger" plain :disabled="!selectedIds.length || actionBusy" @click="removeSelected"><el-icon><Delete /></el-icon>删除 ({{ selectedIds.length }})</el-button>
        </template>
      </UiToolbar>

      <el-alert v-if="optionError" class="dm-state-alert" type="error" :closable="false" show-icon :title="optionError" />
      <el-alert v-if="pendingImportFile && !canImport" class="dm-state-alert" type="warning" :closable="false" show-icon :title="`已选择 ${pendingImportFile.name}；请先选定所属项目后再提交`" />
      <el-alert v-if="importResult" class="dm-state-alert" type="success" :closable="true" show-icon
        :title="`导入完成：共 ${importResult.rows} 行，成功 ${importResult.accepted} 行，失败 ${importResult.failed} 行`">
        <template v-if="importResult.errors?.length" #default>
          <ul class="dm-import-errors">
            <li v-for="(item, index) in importResult.errors.slice(0, 20)" :key="index">{{ item }}</li>
            <li v-if="importResult.errors.length > 20">剩余 {{ importResult.errors.length - 20 }} 条错误未展示</li>
          </ul>
        </template>
      </el-alert>

      <UiDataTable v-if="records.length || loading" :data="records" :loading="loading" row-key="id" border empty-text="暂无迁移检核规则" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="project_name" label="项目名称" min-width="140" />
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

    <el-dialog v-model="detailOpen" title="迁移检核规则详情" width="min(680px, 94vw)">
      <div v-loading="detailLoading" class="dm-detail-box">
        <el-alert v-if="detailError" class="dm-state-alert" type="error" :closable="false" show-icon :title="detailError" />
        <el-descriptions v-if="detail" :column="1" border>
          <el-descriptions-item label="项目名称">{{ detail.project_name ?? '-' }}</el-descriptions-item>
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
.dm-hidden-file { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.dm-state-alert { margin: 0 0 12px; }
.dm-import-errors { margin: 8px 0 0; padding-left: 18px; max-height: 160px; overflow: auto; font-size: 12px; }
.dm-detail-box { min-height: 120px; }
</style>
