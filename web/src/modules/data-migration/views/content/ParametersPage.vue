<!--
  用途：数迁资产内容 - 迁移参数页（REQ-20260906-067，对标迁移检核规则专属域）
  说明：统一管理「业务参数 / 技术参数」两类迁移配置参数，支持 参数类型 / 参数范围分类 / 关联系统 / 关键字
        多维筛选、分页展示、单条录入与编辑（可批量添加字段）、字段维护抽屉、Excel 十一列模板批量导入
        （参数 + 字段同文件）、条件导出、逻辑删除与统一回收站（REQ-20260910-069 增加字段子表）。
        所属项目唯一取自全局项目上下文：页内不展示项目字段；导出为准当前筛选条件；删除进入回收站；
        参数字段删除不留痕迹，参数删除时字段级联隐藏、恢复时一并恢复。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, genFileId, type UploadFile, type UploadInstance, type UploadProps, type UploadRawFile } from 'element-plus'
import { Delete, Document, Download, Edit, Plus, Refresh, Search, UploadFilled, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { useAuthStore } from '../../../../stores/auth'
import { apiErrorMessage } from '../../../../api/error'
import {
  batchCreateParameterFields,
  batchDeleteParameterFields,
  createParameter,
  deleteParameterField,
  deleteParameters,
  downloadParameterTemplate,
  exportParameters,
  getDataMigrationParamOptions,
  DM_CODE_CATEGORIES,
  getParameter,
  getSystemOptions,
  importParameters,
  listParameterFields,
  listParameters,
  updateParameter,
  updateParameterField,
  type ParameterFormData,
  type ParameterField,
  type ParameterFieldForm,
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
const fieldTypeOptions = ref<SelectOption[]>([])
const systemOptions = ref<SelectOption[]>([])
const optionError = ref('')
const englishNamePattern = /^[A-Za-z0-9_]+$/

const formDialogOpen = ref(false)
const dialogMode = ref<'create' | 'edit' | 'view'>('create')
const saving = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const submitError = ref('')
const form = ref<ParameterFormData>({
  projectId: 0,
  parameterType: '', parameterScope: '', systemCode: '',
  parameterName: '', parameterNameEn: '', parameterDescription: '',
  fields: [],
})

const fieldDrawerOpen = ref(false)
const fieldLoading = ref(false)
const fieldBusy = ref(false)
const fieldError = ref('')
const fieldRows = ref<ParameterField[]>([])
const selectedFieldIds = ref<number[]>([])
const activeParameter = ref<ParameterRecord | null>(null)
const batchFieldRows = ref<ParameterFieldForm[]>([])
const fieldEditorOpen = ref(false)
const fieldEditorSaving = ref(false)
const fieldEditorError = ref('')
const fieldEditor = ref<{ index: number; form: ParameterFieldForm }>({ index: -1, form: { fieldNameEn: '', fieldNameCn: '', fieldType: '', fieldLength: null, fieldDescription: '' } })

const detailLoading = ref(false)
const detail = ref<ParameterRecord | null>(null)
const detailError = ref('')

const importDialogOpen = ref(false)
const importUploadRef = ref<UploadInstance>()
const pendingImportFile = ref<File | null>(null)
const importResult = ref<ParameterImportResult | null>(null)
const importError = ref('')

const importReady = computed(() => Boolean(scopeProjectId.value))
const canImport = computed(() => hasCreate.value && importReady.value)
const canSubmitImport = computed(() => canImport.value && Boolean(pendingImportFile.value) && !actionBusy.value)
const hasFilters = computed(() => Boolean(parameterType.value || parameterScope.value || systemCode.value || keyword.value.trim()))

const messageOf = (cause: unknown, fallback = '操作失败，请稍后重试') => cause instanceof Error && cause.message ? cause.message : fallback
const httpStatus = (cause: unknown) => (cause as { response?: { status?: number } }).response?.status
const cancelled = (cause: unknown) => (cause as { action?: string }).action === 'cancel' || (cause as { action?: string }).action === 'close'

function resetPagination() { page.value = 1 }

async function loadOptions() {
  optionLoading.value = true
  optionError.value = ''
  try {
    const [typeRes, scopeRes, fieldTypeRes] = await Promise.all([
      getDataMigrationParamOptions(DM_CODE_CATEGORIES.parameterType),
      getDataMigrationParamOptions(DM_CODE_CATEGORIES.parameterScope),
      getDataMigrationParamOptions(DM_CODE_CATEGORIES.parameterFieldType),
    ])
    typeOptions.value = typeRes.data.data ?? []
    scopeOptions.value = scopeRes.data.data ?? []
    fieldTypeOptions.value = fieldTypeRes.data.data ?? []
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
  form.value = { projectId: 0, parameterType: '', parameterScope: '', systemCode: '', parameterName: '', parameterNameEn: '', parameterDescription: '', fields: [] }
  submitError.value = ''
}

function openCreate() {
  dialogMode.value = 'create'
  editing.value = false
  editId.value = null
  resetForm()
  form.value.projectId = scopeProjectId.value ?? 0
  formDialogOpen.value = true
}

async function openEdit(item: ParameterRecord) {
  dialogMode.value = 'edit'
  editing.value = true
  editId.value = item.id
  resetForm()
  submitError.value = ''
  detailLoading.value = true
  formDialogOpen.value = true
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
      parameterNameEn: data.parameter_name_en ?? '',
      parameterDescription: data.parameter_description ?? '',
    }
  } catch (cause) {
    submitError.value = messageOf(cause, '参数详情加载失败')
    formDialogOpen.value = false
  } finally { detailLoading.value = false }
}

async function handleSave() {
  const required = [
    ['参数类型', form.value.parameterType], ['参数范围分类', form.value.parameterScope],
    ['关联系统', form.value.systemCode], ['参数名称', form.value.parameterName],
    ['参数英文名', form.value.parameterNameEn],
  ] as const
  for (const [label, value] of required) {
    if (!value || String(value).trim() === '') return void ElMessage.warning(`请填写${label}`)
  }
  const parameterNameEn = form.value.parameterNameEn.trim()
  if (!englishNamePattern.test(parameterNameEn)) {
    return void ElMessage.warning('参数英文名只能包含字母、数字和下划线')
  }
  if (!editing.value) {
    const checked = checkCreateFields(form.value.fields ?? [])
    if (checked) return void ElMessage.warning(checked)
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
      parameterNameEn,
      parameterDescription: form.value.parameterDescription?.trim() || undefined,
    }
    if (editing.value && editId.value) {
      await updateParameter(editId.value, payload)
      ElMessage.success('迁移参数已更新')
    } else {
      if (!scopeProjectId.value) { ElMessage.warning('当前项目不可用，请重新选择项目'); return }
      await createParameter({ ...payload, projectId: scopeProjectId.value, fields: (form.value.fields ?? []).length ? form.value.fields : undefined })
      ElMessage.success('迁移参数已新增')
    }
    formDialogOpen.value = false
    resetForm()
    await load()
  } catch (cause) {
    submitError.value = messageOf(cause)
    ElMessage.error(submitError.value)
  } finally { saving.value = false }
}

function addCreateField() {
  form.value.fields = form.value.fields ?? []
  form.value.fields.push({ fieldNameEn: '', fieldNameCn: '', fieldType: '', fieldLength: null, fieldDescription: '' })
}

function removeCreateField(index: number) {
  form.value.fields?.splice(index, 1)
}

function checkCreateFields(fields: ParameterFieldForm[]): string {
  const enSeen = new Set<string>()
  const cnSeen = new Set<string>()
  for (let i = 0; i < fields.length; i++) {
    const field = fields[i]
    const en = String(field.fieldNameEn ?? '').trim()
    const cn = String(field.fieldNameCn ?? '').trim()
    const type = String(field.fieldType ?? '').trim()
    if (!en) return `第 ${i + 1} 行字段英文名为空`
    if (!englishNamePattern.test(en)) return `第 ${i + 1} 行字段英文名只能包含字母、数字和下划线`
    if (en.length > 128) return `第 ${i + 1} 行字段英文名不能超过 128 字符`
    if (!cn) return `第 ${i + 1} 行字段中文名为空`
    if (/\s/.test(cn)) return `第 ${i + 1} 行字段中文名不允许空格`
    if (cn.length > 128) return `第 ${i + 1} 行字段中文名不能超过 128 字符`
    if (!type) return `第 ${i + 1} 行字段类型不能为空`
    const lengthRaw = field.fieldLength
    if (lengthRaw != null && String(lengthRaw).trim() !== '') {
      const length = Number(lengthRaw)
      if (!Number.isInteger(length) || length <= 0 || length > 2147483647) return `第 ${i + 1} 行字段长度必须为正整数`
      field.fieldLength = length
    } else {
      field.fieldLength = null
    }
    const enKey = en.toLowerCase()
    const cnKey = cn.toLowerCase()
    if (enSeen.has(enKey)) return `第 ${i + 1} 行字段英文名在参数内重复`
    if (cnSeen.has(cnKey)) return `第 ${i + 1} 行字段中文名在参数内重复`
    enSeen.add(enKey)
    cnSeen.add(cnKey)
  }
  return ''
}

// ============ 字段维护抽屉（REQ-20260910-069） ============
async function openFields(item: ParameterRecord) {
  activeParameter.value = item
  fieldDrawerOpen.value = true
  fieldError.value = ''
  fieldRows.value = []
  selectedFieldIds.value = []
  batchFieldRows.value = []
  fieldLoading.value = true
  try {
    const response = await listParameterFields(item.id)
    fieldRows.value = response.data.data ?? []
  } catch (cause) {
    fieldError.value = messageOf(cause, '字段加载失败')
  } finally { fieldLoading.value = false }
}

function addBatchFieldRow() {
  batchFieldRows.value.push({ fieldNameEn: '', fieldNameCn: '', fieldType: '', fieldLength: null, fieldDescription: '' })
}

function removeBatchFieldRow(index: number) {
  batchFieldRows.value.splice(index, 1)
}

async function saveBatchFields() {
  const pid = activeParameter.value?.id
  if (!pid) return
  const checked = checkCreateFields(batchFieldRows.value)
  if (checked) return void ElMessage.warning(checked)
  if (fieldBusy.value) return
  fieldBusy.value = true
  fieldError.value = ''
  try {
    const fields = batchFieldRows.value.map(field => ({
      ...field,
      fieldLength: field.fieldLength == null ? null : Number(field.fieldLength),
      fieldDescription: field.fieldDescription?.trim() || undefined,
    }))
    const response = await batchCreateParameterFields(pid, fields)
    fieldRows.value = response.data.data ?? []
    batchFieldRows.value = []
    ElMessage.success(`已批量新增 ${fields.length} 个字段`)
  } catch (cause) {
    fieldError.value = messageOf(cause, '批量新增字段失败')
    ElMessage.error(fieldError.value)
  } finally { fieldBusy.value = false }
}

function openFieldEdit(row: ParameterField, index: number) {
  fieldEditor.value = {
    index,
    form: {
      fieldNameEn: row.field_name_en,
      fieldNameCn: row.field_name_cn,
      fieldType: row.field_type,
      fieldLength: row.field_length ?? null,
      fieldDescription: row.field_description ?? '',
    },
  }
  fieldEditorOpen.value = true
  fieldEditorError.value = ''
}

function closeFieldEditor() {
  if (!fieldEditorSaving.value) fieldEditorOpen.value = false
}

async function saveFieldEdit() {
  const pid = activeParameter.value?.id
  const row = fieldRows.value[fieldEditor.value.index]
  if (!pid || !row) return
  const checked = checkCreateFields([fieldEditor.value.form])
  if (checked) return void ElMessage.warning(checked.replace(/^第 1 行/, ''))
  if (fieldEditorSaving.value) return
  fieldEditorSaving.value = true
  fieldEditorError.value = ''
  try {
    const formData = fieldEditor.value.form
    const updated = await updateParameterField(pid, row.id, {
      fieldNameEn: formData.fieldNameEn.trim(),
      fieldNameCn: formData.fieldNameCn.trim(),
      fieldType: formData.fieldType,
      fieldLength: formData.fieldLength == null ? null : Number(formData.fieldLength),
      fieldDescription: formData.fieldDescription?.trim() || undefined,
    })
    fieldRows.value[fieldEditor.value.index] = updated.data.data ?? row
    fieldEditorOpen.value = false
    ElMessage.success('字段已更新')
  } catch (cause) {
    fieldEditorError.value = messageOf(cause, '字段保存失败')
    ElMessage.error(fieldEditorError.value)
  } finally { fieldEditorSaving.value = false }
}

async function removeField(row: ParameterField) {
  const pid = activeParameter.value?.id
  if (!pid) return
  try {
    await ElMessageBox.confirm(`确认删除字段「${row.field_name_cn}（${row.field_name_en}）」吗？`, '删除字段', { type: 'warning' })
    fieldBusy.value = true
    await deleteParameterField(pid, row.id)
    fieldRows.value = fieldRows.value.filter(field => field.id !== row.id)
    ElMessage.success('字段已删除')
  } catch (cause) {
    if (!cancelled(cause)) ElMessage.error(messageOf(cause, '字段删除失败'))
  } finally { fieldBusy.value = false }
}

async function batchDeleteFields() {
  const pid = activeParameter.value?.id
  if (!pid || !selectedFieldIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认批量删除选中的 ${selectedFieldIds.value.length} 个字段吗？`, '批量删除字段', { type: 'warning' })
    fieldBusy.value = true
    await batchDeleteParameterFields(pid, selectedFieldIds.value)
    fieldRows.value = fieldRows.value.filter(field => !selectedFieldIds.value.includes(field.id))
    selectedFieldIds.value = []
    ElMessage.success('已批量删除字段')
  } catch (cause) {
    if (!cancelled(cause)) ElMessage.error(messageOf(cause, '批量删除字段失败'))
  } finally { fieldBusy.value = false }
}

function fieldTypeLabel(value?: string) {
  return fieldTypeOptions.value.find(option => option.value === value)?.label ?? value ?? ''
}

// ============ 详情 ============
async function openDetail(item: ParameterRecord) {
  dialogMode.value = 'view'
  formDialogOpen.value = true
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

// ============ Excel 批量导入（当前页对话框） ============
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
  const pid = scopeProjectId.value
  const file = pendingImportFile.value
  if (!pid) return void ElMessage.warning('当前项目不可用，请重新选择项目')
  if (!file) return void ElMessage.warning('请先选择 Excel 文件')
  if (actionBusy.value) return
  actionBusy.value = true
  importResult.value = null
  importError.value = ''
  try {
    const response = await importParameters({ projectId: pid }, file)
    const data = response.data.data
    importResult.value = data ?? { rows: 0, accepted: 0, failed: 0, errors: [] }
    const text = `导入完成：共 ${data?.rows ?? 0} 行，成功 ${data?.accepted ?? 0} 行，失败 ${data?.failed ?? 0} 行`
    if (data?.errors?.length) ElMessage.warning(text)
    else ElMessage.success(text)
    await load()
  } catch (cause) {
    importError.value = messageOf(cause, '导入失败')
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
  importDialogOpen.value = false
  resetImportDialog()
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
    <UiPageHeader title="迁移参数" description="列表、新增与导入均固定属于顶部项目切换器选择的当前项目。">
      <template #actions>
        <el-button v-if="hasCreate && scopeState === 'ready' && !forbidden && !error" :disabled="loading || actionBusy" @click="openImportDialog">
          <el-icon><UploadFilled /></el-icon>批量导入
        </el-button>
        <el-button v-if="hasCreate && scopeState === 'ready' && !forbidden && !error" type="primary" :disabled="loading || actionBusy" @click="openCreate">
          <el-icon><Plus /></el-icon>新增迁移参数
        </el-button>
      </template>
    </UiPageHeader>

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
        <el-input v-model="keyword" clearable placeholder="参数名称 / 参数英文名 / 参数说明" style="width: 280px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="!hasFilters" @click="resetFilters"><el-icon><Refresh /></el-icon>重置</el-button>
          <el-button :disabled="actionBusy" @click="exportData"><el-icon><Download /></el-icon>导出</el-button>
          <el-button v-if="hasDelete" type="danger" plain :disabled="!selectedIds.length || actionBusy" @click="removeSelected"><el-icon><Delete /></el-icon>删除 ({{ selectedIds.length }})</el-button>
        </template>
      </UiToolbar>

      <UiDataTable v-if="records.length || loading" :data="records" :loading="loading" row-key="id" border empty-text="暂无迁移参数" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column label="参数类型" min-width="110">
          <template #default="scopeRow">{{ typeLabel(scopeRow.row.parameter_type_name || scopeRow.row.parameter_type) }}</template>
        </el-table-column>
        <el-table-column label="参数范围分类" min-width="120">
          <template #default="scopeRow">{{ scopeLabel(scopeRow.row.parameter_scope_name || scopeRow.row.parameter_scope) }}</template>
        </el-table-column>
        <el-table-column prop="system_code" label="系统编号" min-width="140" />
        <el-table-column prop="system_name" label="系统名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="parameter_name" label="参数名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="parameter_name_en" label="参数英文名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="parameter_description" label="参数说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="字段数" width="90" align="center">
          <template #default="scopeRow">
            <el-button link type="primary" :disabled="actionBusy" @click="openFields(scopeRow.row)">{{ scopeRow.row.field_count ?? 0 }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="scopeRow">
            <el-button link type="primary" :disabled="actionBusy" @click="openDetail(scopeRow.row)">
              <el-icon><View /></el-icon>查看
            </el-button>
            <el-button v-if="hasUpdate" link type="primary" :disabled="actionBusy" @click="openFields(scopeRow.row)">
              <el-icon><Document /></el-icon>字段
            </el-button>
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
      <div class="dm-page">
        <UiPagination :page="page" :page-size="size" :total="total" :page-sizes="[20, 50, 100]"
          @update:page-size="(v: number) => { size = v; page = 1; load() }" @update:page="(v: number) => { page = v; load() }" />
      </div>
    </template>

    <el-dialog
      v-model="formDialogOpen"
      class="dm-form-dialog"
      :title="dialogMode === 'view' ? '迁移参数详情' : editing ? '编辑迁移参数' : '新增迁移参数'"
      width="min(1120px, calc(100vw - 24px))"
      destroy-on-close
      :close-on-click-modal="!saving"
      :close-on-press-escape="!saving"
      :show-close="!saving"
    >
      <div v-if="dialogMode === 'view'" v-loading="detailLoading" class="dm-form-dialog-body dm-detail-dialog-body">
        <el-result v-if="detailError" icon="error" :title="detailError" />
        <template v-else-if="detail">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="参数类型">{{ typeLabel(detail.parameter_type_name || detail.parameter_type) }}</el-descriptions-item>
            <el-descriptions-item label="参数范围分类">{{ scopeLabel(detail.parameter_scope_name || detail.parameter_scope) }}</el-descriptions-item>
            <el-descriptions-item label="系统编号">{{ detail.system_code ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="系统名称">{{ detail.system_name ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="参数名称">{{ detail.parameter_name ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="参数英文名">{{ detail.parameter_name_en ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="参数说明"><div style="white-space: pre-wrap">{{ detail.parameter_description ?? '—' }}</div></el-descriptions-item>
            <el-descriptions-item label="上传人">{{ detail.created_by_name ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ detail.created_at ?? '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">字段信息（{{ detail.fields?.length ?? 0 }}）</el-divider>
          <el-table v-if="detail.fields?.length" :data="detail.fields" border size="small" empty-text="暂无字段">
            <el-table-column prop="field_name_en" label="字段英文名" min-width="130" show-overflow-tooltip />
            <el-table-column prop="field_name_cn" label="字段中文名" min-width="130" show-overflow-tooltip />
            <el-table-column label="字段类型" min-width="100"><template #default="scopeRow">{{ fieldTypeLabel(scopeRow.row.field_type) }}</template></el-table-column>
            <el-table-column prop="field_length" label="长度" width="70" align="center"><template #default="scopeRow">{{ scopeRow.row.field_length ?? '—' }}</template></el-table-column>
            <el-table-column prop="field_description" label="字段说明" min-width="150" show-overflow-tooltip><template #default="scopeRow">{{ scopeRow.row.field_description ?? '—' }}</template></el-table-column>
          </el-table>
          <el-empty v-else description="暂无字段" :image-size="60" />
        </template>
        <el-empty v-else-if="!detailLoading" description="暂无详情" />
      </div>
      <div v-else class="dm-form-dialog-body">
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
          <el-form-item label="参数英文名" required>
            <el-input v-model="form.parameterNameEn" maxlength="255" placeholder="仅字母、数字和下划线，同项目同系统下不区分大小写唯一" />
          </el-form-item>
          <el-form-item label="参数说明">
            <el-input v-model="form.parameterDescription" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="描述参数用途、取值规则、适用场景（可选）" />
          </el-form-item>
          <template v-if="!editing">
            <el-divider content-position="left">字段信息（可选，保存后可在「字段」中继续维护）</el-divider>
            <div class="dm-form-fields">
              <div class="dm-form-fields-head">
                <span class="dm-form-fields-tip">同一参数下字段英文名、字段中文名均不允许重复</span>
                <el-button link type="primary" :disabled="saving" @click="addCreateField"><el-icon><Plus /></el-icon>添加字段</el-button>
              </div>
              <div v-if="form.fields?.length" class="dm-form-fields-table">
                <el-table :data="form.fields" border size="small" empty-text="暂无字段">
                  <el-table-column type="index" label="序号" width="56" align="center" />
                  <el-table-column label="字段英文名" min-width="150">
                    <template #default="scopeRow">
                      <el-input v-model="scopeRow.row.fieldNameEn" maxlength="128" placeholder="仅字母、数字和下划线" />
                    </template>
                  </el-table-column>
                  <el-table-column label="字段中文名" min-width="150">
                    <template #default="scopeRow">
                      <el-input v-model="scopeRow.row.fieldNameCn" maxlength="128" placeholder="不允许空格" />
                    </template>
                  </el-table-column>
                  <el-table-column label="字段类型" min-width="150">
                    <template #default="scopeRow">
                      <el-select v-model="scopeRow.row.fieldType" :loading="optionLoading" placeholder="请选择（系统管理/参数管理维护）" style="width: 100%">
                        <el-option v-for="opt in fieldTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="字段长度" width="110">
                    <template #default="scopeRow">
                      <el-input v-model="scopeRow.row.fieldLength" type="number" min="1" placeholder="可选，正整数" />
                    </template>
                  </el-table-column>
                  <el-table-column label="字段说明" min-width="170">
                    <template #default="scopeRow">
                      <el-input v-model="scopeRow.row.fieldDescription" maxlength="500" placeholder="可选" />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="94" align="center">
                    <template #default="scopeRow">
                      <el-button link type="danger" :disabled="saving" @click="removeCreateField(scopeRow.$index)"><el-icon><Delete /></el-icon>移除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <el-empty v-else description="点击「添加字段」批量录入字段信息" :image-size="60" />
            </div>
          </template>
        </el-form>
      </div>
      <template #footer>
        <el-button :disabled="saving || detailLoading" @click="formDialogOpen = false">{{ dialogMode === 'view' ? '关闭' : '取消' }}</el-button>
        <el-button v-if="dialogMode === 'view' && detail && canEditItem(detail)" type="primary" @click="openEdit(detail)">编辑</el-button>
        <el-button v-else-if="dialogMode !== 'view'" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importDialogOpen"
      class="dm-import-dialog"
      title="批量导入迁移参数"
      width="min(720px, calc(100vw - 24px))"
      destroy-on-close
      :close-on-click-modal="!actionBusy"
      :close-on-press-escape="!actionBusy"
      :show-close="!actionBusy"
      :before-close="beforeImportDialogClose"
      @closed="resetImportDialog"
    >
      <div class="dm-import-dialog-body">
        <el-alert title="请使用迁移参数模板填写数据（参数列 + 字段列：每行一个字段，同一参数连续多行，字段列留空即只导入参数），选择 Excel 文件后确认导入。" type="info" :closable="false" show-icon />

        <div class="dm-import-template-row">
          <span>模板包含参数类型、参数范围分类、关联系统、参数名称、参数英文名、参数说明及字段英文名、字段中文名、字段类型、字段长度、字段说明。</span>
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

    <el-dialog
      v-model="fieldDrawerOpen"
      class="dm-field-drawer"
      :title="`字段维护${activeParameter ? ' — ' + activeParameter.parameter_name : ''}`"
      width="min(960px, calc(100vw - 24px))"
      top="5vh"
      destroy-on-close
      :close-on-click-modal="!fieldBusy"
      :close-on-press-escape="!fieldBusy"
      :show-close="!fieldBusy"
    >
      <div v-loading="fieldLoading" class="dm-field-drawer-body">
        <el-result v-if="fieldError" icon="error" :title="'字段加载失败'" :sub-title="fieldError" />
        <template v-else>
          <section class="dm-field-section">
            <div class="dm-field-section-head">
              <span class="dm-field-section-title">批量新增字段</span>
              <el-button link type="primary" :disabled="fieldBusy" @click="addBatchFieldRow"><el-icon><Plus /></el-icon>添加行</el-button>
            </div>
            <div v-for="(field, index) in batchFieldRows" :key="index" class="dm-field-batch-row">
              <el-input v-model="field.fieldNameEn" maxlength="128" placeholder="字段英文名" />
              <el-input v-model="field.fieldNameCn" maxlength="128" placeholder="字段中文名" />
              <el-select v-model="field.fieldType" :loading="optionLoading" placeholder="字段类型" style="width: 130px">
                <el-option v-for="opt in fieldTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-input v-model="field.fieldLength" type="number" min="1" placeholder="长度" style="width: 100px" />
              <el-input v-model="field.fieldDescription" maxlength="500" placeholder="字段说明" />
              <el-button circle plain type="danger" :disabled="fieldBusy" :aria-label="`移除第 ${index + 1} 行`" @click="removeBatchFieldRow(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button v-if="batchFieldRows.length" type="primary" plain :loading="fieldBusy" :disabled="!batchFieldRows.length" @click="saveBatchFields">
              保存新增字段
            </el-button>
          </section>

          <section class="dm-field-section">
            <div class="dm-field-section-head">
              <span class="dm-field-section-title">字段清单（{{ fieldRows.length }}）</span>
              <el-button v-if="hasDelete && selectedFieldIds.length" type="danger" link :disabled="fieldBusy" @click="batchDeleteFields"><el-icon><Delete /></el-icon>批量删除（{{ selectedFieldIds.length }}）</el-button>
            </div>
            <el-table :data="fieldRows" border size="small" row-key="id" empty-text="暂无字段" @selection-change="(rows: ParameterField[]) => selectedFieldIds = rows.map(row => row.id)">
              <el-table-column type="selection" width="42" />
              <el-table-column prop="field_name_en" label="字段英文名" min-width="140" show-overflow-tooltip />
              <el-table-column prop="field_name_cn" label="字段中文名" min-width="140" show-overflow-tooltip />
              <el-table-column label="字段类型" min-width="110">
                <template #default="scopeRow">{{ fieldTypeLabel(scopeRow.row.field_type) }}</template>
              </el-table-column>
              <el-table-column prop="field_length" label="字段长度" width="90" align="center">
                <template #default="scopeRow">{{ scopeRow.row.field_length ?? '—' }}</template>
              </el-table-column>
              <el-table-column prop="field_description" label="字段说明" min-width="160" show-overflow-tooltip>
                <template #default="scopeRow">{{ scopeRow.row.field_description ?? '—' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="scopeRow">
                  <el-button v-if="hasUpdate" link type="primary" :disabled="fieldBusy" @click="openFieldEdit(scopeRow.row, scopeRow.$index)">
                    <el-icon><Edit /></el-icon>编辑
                  </el-button>
                  <el-button v-if="hasDelete" link type="danger" :disabled="fieldBusy" @click="removeField(scopeRow.row)">
                    <el-icon><Delete /></el-icon>删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </section>
        </template>
      </div>
      <template #footer>
        <el-button :disabled="fieldBusy" @click="fieldDrawerOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fieldEditorOpen" title="编辑字段" width="min(520px, calc(100vw - 24px))" :close-on-click-modal="!fieldEditorSaving" :close-on-press-escape="!fieldEditorSaving" :show-close="!fieldEditorSaving" @closed="fieldEditorError = ''">
      <el-alert v-if="fieldEditorError" :title="fieldEditorError" type="error" :closable="false" show-icon style="margin-bottom: 12px" />
      <el-form label-position="top">
        <el-form-item label="字段英文名" required>
          <el-input v-model="fieldEditor.form.fieldNameEn" maxlength="128" placeholder="仅字母、数字和下划线，参数内唯一" />
        </el-form-item>
        <el-form-item label="字段中文名" required>
          <el-input v-model="fieldEditor.form.fieldNameCn" maxlength="128" placeholder="不允许空格，参数内唯一" />
        </el-form-item>
        <el-form-item label="字段类型" required>
          <el-select v-model="fieldEditor.form.fieldType" :loading="optionLoading" placeholder="请选择" style="width: 100%">
            <el-option v-for="opt in fieldTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段长度">
          <el-input v-model="fieldEditor.form.fieldLength" type="number" min="1" placeholder="可选，正整数" />
        </el-form-item>
        <el-form-item label="字段说明">
          <el-input v-model="fieldEditor.form.fieldDescription" maxlength="500" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="fieldEditorSaving" @click="closeFieldEditor">取消</el-button>
        <el-button type="primary" :loading="fieldEditorSaving" @click="saveFieldEdit">保存</el-button>
      </template>
    </el-dialog>

  </main>
</template>

<style scoped>
.dm-import-dialog-body { min-width: 0; max-height: min(60vh, 520px); padding-right: 2px; overflow-x: hidden; overflow-y: auto; }
.dm-import-template-row { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; margin: 14px 0; color: var(--muted); font-size: 13px; }
.dm-import-template-row span { min-width: 0; overflow-wrap: anywhere; }
.dm-import-result { margin: 14px 0 0; }
.dm-import-message { word-break: break-word; }
.dm-import-errors { margin: 6px 0 0; padding-left: 18px; }
.dm-import-errors li { margin: 2px 0; word-break: break-word; }
:global(.el-dialog.dm-form-dialog) { --el-dialog-margin-top: 24px; }
.dm-form-dialog-body { min-width: 0; max-height: min(calc(100dvh - 210px), 760px); padding-right: 2px; overflow-x: hidden; overflow-y: auto; }
.dm-form-fields { min-width: 0; }
.dm-form-fields-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.dm-form-fields-tip { min-width: 0; color: var(--muted); font-size: 12px; overflow-wrap: anywhere; }
.dm-form-fields-table { min-width: 0; overflow-x: auto; }
.dm-form-fields-table .el-table { min-width: 720px; }
.dm-form-fields-table .el-select { width: 100%; }
.dm-field-drawer-body { min-width: 0; max-height: calc(100vh - 180px); overflow-x: hidden; overflow-y: auto; }
.dm-field-section { margin-bottom: 18px; min-width: 0; }
.dm-field-section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.dm-field-section-title { color: var(--muted); font-size: 13px; font-weight: 600; }
.dm-field-batch-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; min-width: 0; flex-wrap: wrap; }
.dm-field-batch-row .el-input,
.dm-field-batch-row .el-select { max-width: 100%; }
.dm-field-drawer .el-drawer__body { min-width: 0; overflow: hidden; }
@media (max-width: 640px) {
  .dm-field-batch-row { flex-direction: column; align-items: stretch; }
  .dm-field-batch-row .el-select,
  .dm-field-batch-row .el-input { width: 100% !important; }
}
</style>
