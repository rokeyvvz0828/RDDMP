<!--
  用途：数迁基础资料 - 目标表结构 / 中间表结构（通用页，resolvedCategory.value 区分 TARGET/INTERMEDIATE）
  说明：上下结构维护表信息 + 字段明细；字段粒度分页列表；支持批量上传/单笔新增/查看/修改表信息/字段行编辑/删除/导出。
        系统编号联动物理子系统带出只读事业群/系统名称（不落库）；字段英文名/中文名/字典编号不允许空格；
        表英文名/中文名在 项目+系统编号 下唯一，字段英文名/中文名在表内唯一。覆盖加载/空/失败/无权限/提交中状态与移动端卡片化。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Edit, Plus, Refresh, Search, Upload, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  addTargetTableField,
  createTargetTable,
  deleteTargetTableField,
  deleteTargetTableFields,
  deleteTargetTables,
  downloadTargetTableTemplate,
  exportTargetTables,
  importTargetTables,
  listPhysicalSubsystemsByCode,
  listTargetTableFields,
  listTargetTables,
  updateTargetTable,
  updateTargetTableField,
  type TableCategory,
  type TargetTableField,
  type TargetTableRecord
} from '../../../../api/data-migration'
import { getProjectWorkbench } from '../../../../api/project'
import type { Project } from '../../../../types/project'

const route = useRoute()
const props = defineProps<{ category?: TableCategory }>()
const resolvedCategory = computed<TableCategory>(() => (props.category ?? (route.meta.category as TableCategory) ?? 'TARGET'))

const auth = useAuthStore()
const readCode = computed(() => resolvedCategory.value === 'TARGET' ? 'data-migration:base:table-fields-target' : 'data-migration:base:table-fields-intermediate')
const createCode = computed(() => resolvedCategory.value === 'TARGET' ? 'data-migration:base:table-fields-target:create' : 'data-migration:base:table-fields-intermediate:create')
const updateCode = computed(() => resolvedCategory.value === 'TARGET' ? 'data-migration:base:table-fields-target:update' : 'data-migration:base:table-fields-intermediate:update')
const deleteCode = computed(() => resolvedCategory.value === 'TARGET' ? 'data-migration:base:table-fields-target:delete' : 'data-migration:base:table-fields-intermediate:delete')
const canCreate = computed(() => auth.hasPermission(createCode.value))
const canUpdate = computed(() => auth.hasPermission(updateCode.value))
const canDelete = computed(() => auth.hasPermission(deleteCode.value))

const title = computed(() => (resolvedCategory.value === 'TARGET' ? '目标表结构' : '中间表结构'))

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const rows = ref<TargetTableRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const actionBusy = ref(false)
const selectedIds = ref<number[]>([])

// 记录每行被裁剪进 "..." 的操作（key: row.id），用于控制下拉菜单只显示被裁剪的操作
const clippedMap = reactive(new Map<number, Set<string>>())
function getClipped(row: TargetTableRecord) {
  return clippedMap.get(row.id) ?? new Set<string>()
}

const projects = ref<Project[]>([])
const filters = reactive({
  projectId: undefined as number | undefined,
  systemCode: '',
  isKeyField: undefined as number | undefined,
  dictCode: '',
  tableKeyword: '',
  fieldKeyword: ''
})

function httpStatus(e: unknown) {
  return (e as { response?: { status?: number } }).response?.status
}
function cancelled(e: unknown) {
  return (e as { action?: string }).action === 'cancel' || (e as { action?: string }).action === 'close'
}
function parseList(r: { data: { data: { records: TargetTableRecord[]; total: number } } }) {
  return { records: r.data.data.records ?? [], total: r.data.data.total ?? 0 }
}

async function load() {
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const r = await listTargetTables({
      category: resolvedCategory.value,
      projectId: filters.projectId,
      systemCode: filters.systemCode || undefined,
      isKeyField: filters.isKeyField,
      dictCode: filters.dictCode || undefined,
      tableKeyword: filters.tableKeyword || undefined,
      fieldKeyword: filters.fieldKeyword || undefined,
      page: page.value,
      size: pageSize.value
    })
    const { records, total: t } = parseList(r)
    rows.value = records
    total.value = t
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try { projects.value = (await getProjectWorkbench()).data.data ?? [] } catch { projects.value = [] }
}

function search() { page.value = 1; load() }
function resetFilters() {
  Object.assign(filters, { projectId: undefined, systemCode: '', isKeyField: undefined, dictCode: '', tableKeyword: '', fieldKeyword: '' })
  page.value = 1; load()
}
function onPageChange(p: number) { page.value = p; load() }
function onSizeChange(s: number) { pageSize.value = s; page.value = 1; load() }
function onSelectionChange(val: TargetTableRecord[]) { selectedIds.value = val.map(v => v.id) }

/* ---------- 导出 ---------- */
async function exportExcel(ids?: number[]) {
  actionBusy.value = true
  try {
    const r = await exportTargetTables({
      category: resolvedCategory.value,
      ids,
      projectId: filters.projectId,
      systemCode: filters.systemCode || undefined,
      isKeyField: filters.isKeyField,
      dictCode: filters.dictCode || undefined,
      tableKeyword: filters.tableKeyword || undefined,
      fieldKeyword: filters.fieldKeyword || undefined
    })
    const blob = new Blob([r.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${title.value}_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(a); a.click(); document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) { ElMessage.error(apiErrorMessage(e, '导出失败')) } finally { actionBusy.value = false }
}

async function downloadTemplate() {
  try {
    const r = await downloadTargetTableTemplate()
    const blob = new Blob([r.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = '目标表结构模板.xlsx'
    document.body.appendChild(a); a.click(); document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) { ElMessage.error(apiErrorMessage(e, '模板下载失败')) }
}

/* ---------- 导入 ---------- */
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importLoading = ref(false)
const importResult = ref<{ accepted: number; failed: number; errors: string[] } | null>(null)
function openImport() { importVisible.value = true; importFile.value = null; importResult.value = null }
async function submitImport() {
  if (!importFile.value) return ElMessage.warning('请选择 Excel 文件')
  importLoading.value = true
  try {
    const r = await importTargetTables(resolvedCategory.value, importFile.value)
    importResult.value = r.data.data
    ElMessage.success(`导入完成：成功 ${r.data.data.accepted} 条，失败 ${r.data.data.failed} 条`)
    importVisible.value = false
    await load()
  } catch (e) { ElMessage.error(apiErrorMessage(e, '导入失败')) } finally { importLoading.value = false }
}

/* ---------- 查看 ---------- */
const viewOpen = ref(false)
const viewing = ref<TargetTableRecord | null>(null)
const viewFields = ref<TargetTableField[]>([])
async function openView(row: TargetTableRecord) {
  viewing.value = row
  viewFields.value = []
  viewOpen.value = true
  try {
    const r = await listTargetTableFields(row.id, resolvedCategory.value)
    viewFields.value = r.data.data ?? []
  } catch (e) { ElMessage.error(apiErrorMessage(e, '字段加载失败')) }
}

/* ---------- 修改表信息 ---------- */
const editOpen = ref(false)
const editSaving = ref(false)
const editing = ref<TargetTableRecord | null>(null)
const editForm = reactive({ table_name_en: '', table_name_cn: '', table_meaning: '' })
function openEdit(row: TargetTableRecord) {
  editing.value = row
  editForm.table_name_en = row.table_name_en
  editForm.table_name_cn = row.table_name_cn
  editForm.table_meaning = row.table_meaning ?? ''
  editOpen.value = true
}
async function submitEdit() {
  if (!editing.value) return
  if (!editForm.table_name_en.trim() || /\s/.test(editForm.table_name_en)) return ElMessage.warning('表英文名不允许空格')
  if (!editForm.table_name_cn.trim() || /\s/.test(editForm.table_name_cn)) return ElMessage.warning('表中文名不允许空格')
  editSaving.value = true
  try {
    await updateTargetTable(editing.value.id, resolvedCategory.value, { tableNameEn: editForm.table_name_en.trim(), tableNameCn: editForm.table_name_cn.trim(), tableMeaning: editForm.table_meaning.trim() })
    ElMessage.success('修改成功')
    editOpen.value = false
    await load()
  } catch (e) { ElMessage.error(apiErrorMessage(e, '修改失败')) } finally { editSaving.value = false }
}

/* ---------- 新增表 + 字段 ---------- */
const createOpen = ref(false)
const createSaving = ref(false)
const subsystemSearching = ref(false)
const subsystemForbidden = ref(false)
const subsystemCandidates = ref<{ code: string; name: string; businessGroupName?: string | null }[]>([])
const createForm = reactive<{
  projectId?: number
  systemCode: string
  table_name_en: string
  table_name_cn: string
  table_meaning: string
  fields: Record<string, unknown>[]
}>({ projectId: undefined, systemCode: '', table_name_en: '', table_name_cn: '', table_meaning: '', fields: [] })
const selectedSubsystem = computed(() => subsystemCandidates.value.find(c => c.code === createForm.systemCode))

async function searchSubsystem() {
  if (!createForm.systemCode.trim()) return
  subsystemSearching.value = true
  subsystemForbidden.value = false
  try {
    const r = await listPhysicalSubsystemsByCode(createForm.systemCode.trim())
    subsystemCandidates.value = (r.data.data.records ?? []).map(s => ({ code: s.code, name: s.name, businessGroupName: s.businessGroupName ?? undefined }))
    if (!subsystemCandidates.value.length) ElMessage.warning('未找到匹配的物理子系统')
  } catch (e) {
    if (httpStatus(e) === 403) { subsystemForbidden.value = true; ElMessage.warning('缺少物理子系统查询权限，无法联动带出系统信息') }
    else ElMessage.error(apiErrorMessage(e, '系统编号查询失败'))
  } finally { subsystemSearching.value = false }
}
function openCreate() {
  Object.assign(createForm, { projectId: undefined, systemCode: '', table_name_en: '', table_name_cn: '', table_meaning: '', fields: [] })
  subsystemCandidates.value = []
  subsystemForbidden.value = false
  createOpen.value = true
}
function addCreateField() { createForm.fields.push({ fieldNameEn: '', fieldNameCn: '', fieldMeaning: '', codeDescription: '', isKeyField: 0, oracleType: '', mysqlType: '', isNullable: 1, isPrimaryKey: 0, dictCode: '' }) }
function removeCreateField(idx: number) { createForm.fields.splice(idx, 1) }
async function submitCreate() {
  if (!createForm.projectId) return ElMessage.warning('请选择所属项目')
  if (!createForm.systemCode.trim()) return ElMessage.warning('请输入系统编号')
  if (!createForm.table_name_en.trim() || /\s/.test(createForm.table_name_en)) return ElMessage.warning('表英文名不允许空格')
  if (!createForm.table_name_cn.trim() || /\s/.test(createForm.table_name_cn)) return ElMessage.warning('表中文名不允许空格')
  for (const f of createForm.fields) {
    if (!f.fieldNameEn || /\s/.test(String(f.fieldNameEn))) return ElMessage.warning('字段英文名不允许空格')
    if (!f.fieldNameCn || /\s/.test(String(f.fieldNameCn))) return ElMessage.warning('字段中文名不允许空格')
    if (f.dictCode && /\s/.test(String(f.dictCode))) return ElMessage.warning('数据字典编号不允许空格')
  }
  createSaving.value = true
  try {
    await createTargetTable(resolvedCategory.value, {
      projectId: createForm.projectId,
      systemCode: createForm.systemCode.trim(),
      tableNameEn: createForm.table_name_en.trim(),
      tableNameCn: createForm.table_name_cn.trim(),
      tableMeaning: createForm.table_meaning.trim(),
      fields: createForm.fields
    })
    ElMessage.success('新增成功')
    createOpen.value = false
    await load()
  } catch (e) { ElMessage.error(apiErrorMessage(e, '新增失败')) } finally { createSaving.value = false }
}

/* ---------- 字段操作（抽屉行编辑/新增/删除） ---------- */
const fieldOpen = ref(false)
const fieldTable = ref<TargetTableRecord | null>(null)
const fieldRows = ref<TargetTableField[]>([])
const fieldBusy = ref(false)
const editingField = ref<TargetTableField | null>(null)
const fieldForm = reactive<Record<string, unknown>>({})
const fieldSaving = ref(false)
const selectedFieldIds = ref<number[]>([])

async function openFields(row: TargetTableRecord) {
  fieldTable.value = row
  fieldRows.value = []
  fieldOpen.value = true
  try {
    const r = await listTargetTableFields(row.id, resolvedCategory.value)
    fieldRows.value = r.data.data ?? []
  } catch (e) { ElMessage.error(apiErrorMessage(e, '字段加载失败')) }
}
async function addField() {
  if (!fieldTable.value) return
  fieldBusy.value = true
  try {
    const r = await addTargetTableField(fieldTable.value.id, resolvedCategory.value, newFieldTemplate())
    fieldRows.value.push(r.data.data)
    ElMessage.success('新增字段成功')
  } catch (e) { ElMessage.error(apiErrorMessage(e, '新增字段失败')) } finally { fieldBusy.value = false }
}
function newFieldTemplate() {
  return { fieldNameEn: '', fieldNameCn: '', fieldMeaning: '', codeDescription: '', isKeyField: 0, oracleType: '', mysqlType: '', isNullable: 1, isPrimaryKey: 0, dictCode: '' }
}
function openEditField(f: TargetTableField) {
  editingField.value = f
  Object.assign(fieldForm, { fieldNameEn: f.field_name_en, fieldNameCn: f.field_name_cn, fieldMeaning: f.field_meaning ?? '', codeDescription: f.code_description ?? '', isKeyField: f.is_key_field, oracleType: f.oracle_type ?? '', mysqlType: f.mysql_type ?? '', isNullable: f.is_nullable, isPrimaryKey: f.is_primary_key, dictCode: f.dict_code ?? '' })
}
function cancelEditField() { editingField.value = null }
async function saveEditField(f: TargetTableField) {
  if (!fieldForm.fieldNameEn || /\s/.test(String(fieldForm.fieldNameEn))) return ElMessage.warning('字段英文名不允许空格')
  if (!fieldForm.fieldNameCn || /\s/.test(String(fieldForm.fieldNameCn))) return ElMessage.warning('字段中文名不允许空格')
  if (fieldForm.dictCode && /\s/.test(String(fieldForm.dictCode))) return ElMessage.warning('数据字典编号不允许空格')
  fieldSaving.value = true
  try {
    const r = await updateTargetTableField(f.id, resolvedCategory.value, { ...fieldForm })
    Object.assign(f, r.data.data)
    editingField.value = null
    ElMessage.success('字段保存成功')
  } catch (e) { ElMessage.error(apiErrorMessage(e, '字段保存失败')) } finally { fieldSaving.value = false }
}
async function removeField(f: TargetTableField) {
  try {
    await ElMessageBox.confirm(`确认删除字段「${f.field_name_cn}（${f.field_name_en}）」吗？`, '删除字段', { type: 'warning' })
    fieldBusy.value = true
    await deleteTargetTableField(f.id, resolvedCategory.value)
    fieldRows.value = fieldRows.value.filter(x => x.id !== f.id)
    selectedFieldIds.value = selectedFieldIds.value.filter(x => x !== f.id)
    ElMessage.success('已删除')
    // 若该表字段已全部删除，后端会同步删除表：关闭抽屉并刷新列表
    if (fieldRows.value.length === 0) {
      fieldOpen.value = false
      await load()
    }
  } catch (e) { if (!cancelled(e)) ElMessage.error(apiErrorMessage(e, '删除失败')) } finally { fieldBusy.value = false }
}
async function batchDeleteFields() {
  if (!selectedFieldIds.value.length) return ElMessage.warning('请先勾选要删除的字段')
  try {
    await ElMessageBox.confirm(`确认批量删除 ${selectedFieldIds.value.length} 个字段吗？若某表字段被全部删除将同步删除该表`, '批量删除字段', { type: 'warning' })
    fieldBusy.value = true
    await deleteTargetTableFields(resolvedCategory.value, selectedFieldIds.value)
    fieldRows.value = fieldRows.value.filter(x => !selectedFieldIds.value.includes(x.id))
    selectedFieldIds.value = []
    ElMessage.success('已批量删除')
    if (fieldRows.value.length === 0) {
      fieldOpen.value = false
      await load()
    }
  } catch (e) { if (!cancelled(e)) ElMessage.error(apiErrorMessage(e, '批量删除失败')) } finally { fieldBusy.value = false }
}

/* ---------- 删除表 ---------- */
async function removeRow(row: TargetTableRecord) {
  try {
    await ElMessageBox.confirm(`确认删除表「${row.table_name_cn}（${row.table_name_en}）」及其全部字段吗？`, '删除表结构', { type: 'warning' })
    actionBusy.value = true
    await deleteTargetTables(resolvedCategory.value, [row.id])
    ElMessage.success('已删除')
    await load()
  } catch (e) { if (!cancelled(e)) ElMessage.error(apiErrorMessage(e, '删除失败')) } finally { actionBusy.value = false }
}
async function batchDelete() {
  if (!selectedIds.value.length) return ElMessage.warning('请先勾选要删除的表')
  try {
    await ElMessageBox.confirm(`确认批量删除 ${selectedIds.value.length} 张表及其字段吗？`, '批量删除', { type: 'warning' })
    actionBusy.value = true
    await deleteTargetTables(resolvedCategory.value, selectedIds.value)
    ElMessage.success('已批量删除')
    selectedIds.value = []
    await load()
  } catch (e) { if (!cancelled(e)) ElMessage.error(apiErrorMessage(e, '批量删除失败')) } finally { actionBusy.value = false }
}

/* ---------- 溢出检测指令 ---------- */
const vOverflow = {
  mounted(el: HTMLElement) {
    checkOverflow(el)
    const observer = new ResizeObserver(() => checkOverflow(el))
    observer.observe(el)
    ;(el as any).__overflowObserver = observer
  },
  updated(el: HTMLElement) {
    checkOverflow(el)
  },
  unmounted(el: HTMLElement) {
    ;(el as any).__overflowObserver?.disconnect()
  },
}
const _overflowLock = new WeakSet<HTMLElement>()
function checkOverflow(el: HTMLElement) {
  // 防止重入：如果正在处理同一元素，跳过
  if (_overflowLock.has(el)) return
  _overflowLock.add(el)
  
  const buttons = Array.from(el.querySelectorAll('.el-button[data-action]')) as HTMLElement[]
  const trigger = el.querySelector('.dm-overflow-trigger') as HTMLElement | null
  const rowId = Number(el.getAttribute('data-row-id') || 0)
  
  if (!trigger) {
    el.classList.remove('is-overflow')
    _overflowLock.delete(el)
    return
  }
  
  // 先清除所有 is-clipped 类，让按钮恢复自然宽度
  buttons.forEach((btn) => btn.classList.remove('is-clipped'))
  
  // 使用 requestAnimationFrame 确保浏览器完成布局后再测量
  requestAnimationFrame(() => {
    const containerWidth = el.getBoundingClientRect().width
    const gap = 8 // 与 CSS gap: 8px 一致
    
    // 临时显示触发器以测量其宽度
    const origDisplay = trigger.style.display
    trigger.style.display = 'flex'
    const triggerWidth = trigger.getBoundingClientRect().width
    trigger.style.display = origDisplay
    
    const availableWidth = containerWidth - triggerWidth
    
    // 用累积宽度判断哪些按钮溢出（此时按钮均处于自然状态）
    let cumWidth = 0
    const clippedActions = new Set<string>()
    buttons.forEach((btn, i) => {
      const btnWidth = btn.getBoundingClientRect().width
      if (i > 0) cumWidth += gap
      cumWidth += btnWidth
      const isHidden = cumWidth > availableWidth
      btn.classList.toggle('is-clipped', isHidden)
      if (isHidden) {
        clippedActions.add(btn.getAttribute('data-action') || '')
      }
    })
    
    // 记录到响应式状态，驱动下拉菜单只展示被裁剪的操作
    if (rowId) {
      clippedMap.set(rowId, clippedActions)
    }
    
    const isOverflow = clippedActions.size > 0
    el.classList.toggle('is-overflow', isOverflow)
    _overflowLock.delete(el)
  })
}

onMounted(() => { loadProjects(); load() })
</script>

<template>
  <main class="dm-page-root tt-page">
    <section v-if="forbidden" class="dm-state-panel">
      <el-result icon="warning" :title="`暂无${title}查看权限`" sub-title="请向数据迁移管理员申请相应权限。" />
    </section>
    <section v-else-if="error" class="dm-state-panel">
      <el-result icon="error" :title="`${title}加载失败`" :sub-title="error">
        <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
      </el-result>
    </section>
    <template v-else>
      <UiToolbar>
        <el-select v-model="filters.projectId" clearable filterable placeholder="所属项目" style="width: 190px">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.project_name}（${p.project_code}）`" :value="p.id" />
        </el-select>
        <el-input v-model="filters.systemCode" clearable placeholder="系统编号" style="width: 150px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.isKeyField" clearable placeholder="关键栏位" style="width: 120px">
          <el-option label="是" :value="1" /><el-option label="否" :value="0" />
        </el-select>
        <el-input v-model="filters.dictCode" clearable placeholder="数据字典编号（模糊）" style="width: 180px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input v-model="filters.tableKeyword" clearable placeholder="表英文/中文名称" style="width: 170px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input v-model="filters.fieldKeyword" clearable placeholder="字段英文/中文名称" style="width: 170px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
          <el-button :disabled="loading || actionBusy" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="loading" @click="resetFilters">重置</el-button>
          <el-button :disabled="loading || actionBusy" @click="exportExcel()"><el-icon><Download /></el-icon>导出</el-button>
          <el-button v-if="canCreate" :disabled="loading || actionBusy" @click="downloadTemplate"><el-icon><Download /></el-icon>模板</el-button>
          <el-button v-if="canCreate" :disabled="loading || actionBusy" @click="openImport"><el-icon><Upload /></el-icon>批量上传</el-button>
          <el-button v-if="canCreate" type="primary" :disabled="loading || actionBusy" @click="openCreate"><el-icon><Plus /></el-icon>新增</el-button>
        </template>
      </UiToolbar>

      <div v-if="rows.length || loading" class="tt-desktop">
        <UiDataTable :data="rows" :loading="loading" row-key="id" border empty-text="暂无数据" @selection-change="onSelectionChange">
          <el-table-column type="selection" width="46" />
          <el-table-column prop="project_name" label="所属项目" min-width="150" show-overflow-tooltip />
          <el-table-column prop="business_group" label="事业群" min-width="110" show-overflow-tooltip />
          <el-table-column prop="system_code" label="系统编号" min-width="130" show-overflow-tooltip />
          <el-table-column prop="system_name" label="系统名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="table_name_en" label="表英文名" min-width="150" show-overflow-tooltip />
          <el-table-column prop="table_name_cn" label="表中文名" min-width="150" show-overflow-tooltip />
          <el-table-column prop="table_meaning" label="表含义" min-width="150" show-overflow-tooltip />
          <el-table-column prop="field_name_en" label="字段英文名" min-width="150" show-overflow-tooltip />
          <el-table-column prop="field_name_cn" label="字段中文名" min-width="150" show-overflow-tooltip />
          <el-table-column prop="field_meaning" label="字段含义" min-width="140" show-overflow-tooltip />
          <el-table-column prop="code_description" label="码值说明" min-width="120" show-overflow-tooltip />
          <el-table-column label="关键栏位" width="90" align="center">
            <template #default="{ row }"><el-tag :type="row.is_key_field === 1 ? 'danger' : 'info'" effect="plain" size="small">{{ row.is_key_field === 1 ? '是' : '否' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="oracle_type" label="ORACLE类型" min-width="120" show-overflow-tooltip />
          <el-table-column prop="mysql_type" label="mysql类型" min-width="120" show-overflow-tooltip />
          <el-table-column label="可空" width="70" align="center">
            <template #default="{ row }"><el-tag :type="row.is_nullable === 1 ? 'info' : 'warning'" effect="plain" size="small">{{ row.is_nullable === 1 ? '是' : '否' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="主键" width="70" align="center">
            <template #default="{ row }"><el-tag :type="row.is_primary_key === 1 ? 'success' : 'info'" effect="plain" size="small">{{ row.is_primary_key === 1 ? '是' : '否' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="dict_code" label="数据字典编号" min-width="130" show-overflow-tooltip />
          <el-table-column label="操作" width="210" fixed="right" align="center">
            <template #default="{ row }">
              <div v-overflow class="dm-table-actions" :data-row-id="row.id">
                <el-button data-action="view" link type="primary" :disabled="actionBusy" @click="openView(row)"><el-icon><View /></el-icon>查看</el-button>
                <el-button v-if="canUpdate" data-action="edit" link type="primary" :disabled="actionBusy" @click="openEdit(row)"><el-icon><Edit /></el-icon>修改</el-button>
                <el-button v-if="canUpdate" data-action="fields" link type="primary" :disabled="actionBusy" @click="openFields(row)">字段</el-button>
                <el-button v-if="canDelete" data-action="delete" link type="danger" :disabled="actionBusy" @click="removeRow(row)"><el-icon><Delete /></el-icon>删除</el-button>
                <el-dropdown class="dm-overflow-trigger" trigger="click">
                  <span class="el-dropdown-link">...</span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="canUpdate && getClipped(row).has('edit')" data-action="edit" @click="openEdit(row)"><el-icon><Edit /></el-icon>修改</el-dropdown-item>
                      <el-dropdown-item v-if="canUpdate && getClipped(row).has('fields')" data-action="fields" @click="openFields(row)">字段</el-dropdown-item>
                      <el-dropdown-item v-if="canDelete && getClipped(row).has('delete')" data-action="delete" divided @click="removeRow(row)"><el-icon><Delete /></el-icon>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
          <template #footer>
            <div class="dm-table-footer">
              <span>共 {{ total }} 条</span>
              <el-button v-if="canDelete && selectedIds.length" type="danger" link :disabled="actionBusy" @click="batchDelete">批量删除（{{ selectedIds.length }}）</el-button>
              <el-pagination background layout="total, sizes, prev, pager, next" :total="total" :current-page="page" :page-size="pageSize" :page-sizes="[20, 50, 100]" @current-change="onPageChange" @size-change="onSizeChange" />
            </div>
          </template>
        </UiDataTable>
      </div>

      <div v-if="rows.length || loading" class="dm-mobile-list">
        <article v-for="row in rows" :key="row.id">
          <header>
            <div><strong>{{ row.table_name_cn }}</strong><small>{{ row.table_name_en }} · {{ row.system_code }}</small></div>
            <el-tag :type="row.is_key_field === 1 ? 'danger' : 'info'" effect="plain" size="small">关键：{{ row.is_key_field === 1 ? '是' : '否' }}</el-tag>
          </header>
          <dl>
            <div><dt>所属项目</dt><dd>{{ row.project_name }}</dd></div>
            <div><dt>事业群</dt><dd>{{ row.business_group }}</dd></div>
            <div><dt>系统名称</dt><dd>{{ row.system_name }}</dd></div>
            <div><dt>表含义</dt><dd>{{ row.table_meaning }}</dd></div>
            <div><dt>字段</dt><dd>{{ row.field_name_cn }}（{{ row.field_name_en }}）</dd></div>
            <div><dt>字典编号</dt><dd>{{ row.dict_code }}</dd></div>
          </dl>
          <footer>
            <el-button link type="primary" :disabled="actionBusy" @click="openView(row)"><el-icon><View /></el-icon>查看</el-button>
            <el-button v-if="canUpdate" link type="primary" :disabled="actionBusy" @click="openEdit(row)"><el-icon><Edit /></el-icon>修改</el-button>
            <el-button v-if="canUpdate" link type="primary" :disabled="actionBusy" @click="openFields(row)">字段</el-button>
            <el-button v-if="canDelete" link type="danger" :disabled="actionBusy" @click="removeRow(row)"><el-icon><Delete /></el-icon>删除</el-button>
          </footer>
        </article>
        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <el-pagination background layout="prev, pager, next" :total="total" :current-page="page" :page-size="pageSize" @current-change="onPageChange" />
        </div>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" :title="`暂无${title}数据`" :description="`当前筛选条件下没有记录，可通过「新增」或「批量上传」录入。`" />
    </template>

    <!-- 查看 -->
    <el-dialog v-model="viewOpen" :title="`${title}详情`" width="720px" align-center destroy-on-close>
      <template v-if="viewing">
        <h4 class="tt-section-title">表信息</h4>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="表编号">{{ viewing.table_code }}</el-descriptions-item>
          <el-descriptions-item label="所属项目">{{ viewing.project_name }}</el-descriptions-item>
          <el-descriptions-item label="所属事业群">{{ viewing.business_group }}</el-descriptions-item>
          <el-descriptions-item label="系统编号">{{ viewing.system_code }}</el-descriptions-item>
          <el-descriptions-item label="系统名称">{{ viewing.system_name }}</el-descriptions-item>
          <el-descriptions-item label="表英文名">{{ viewing.table_name_en }}</el-descriptions-item>
          <el-descriptions-item label="表中文名">{{ viewing.table_name_cn }}</el-descriptions-item>
          <el-descriptions-item label="表含义">{{ viewing.table_meaning }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="tt-section-title">字段信息</h4>
        <el-table :data="viewFields" border size="small" max-height="320">
          <el-table-column prop="field_name_en" label="字段英文名" min-width="120" />
          <el-table-column prop="field_name_cn" label="字段中文名" min-width="120" />
          <el-table-column prop="field_meaning" label="字段含义" min-width="120" />
          <el-table-column prop="code_description" label="码值说明" min-width="100" />
          <el-table-column label="关键栏位" width="80" align="center"><template #default="{ row }">{{ row.is_key_field === 1 ? '是' : '否' }}</template></el-table-column>
          <el-table-column prop="oracle_type" label="ORACLE类型" min-width="100" />
          <el-table-column prop="mysql_type" label="mysql类型" min-width="100" />
          <el-table-column label="可空" width="60" align="center"><template #default="{ row }">{{ row.is_nullable === 1 ? '是' : '否' }}</template></el-table-column>
          <el-table-column label="主键" width="60" align="center"><template #default="{ row }">{{ row.is_primary_key === 1 ? '是' : '否' }}</template></el-table-column>
          <el-table-column prop="dict_code" label="字典编号" min-width="100" />
        </el-table>
      </template>
      <template #footer><el-button @click="viewOpen = false">关闭</el-button></template>
    </el-dialog>

    <!-- 修改表信息 -->
    <UiFormDrawer v-model="editOpen" title="修改表信息" width="560px" :loading="editSaving" confirm-text="保存" @submit="submitEdit">
      <el-form label-width="96px" label-position="left">
        <el-form-item label="表编号"><el-input :model-value="editing?.table_code" disabled /></el-form-item>
        <el-form-item label="所属项目"><el-input :model-value="editing?.project_name" disabled /></el-form-item>
        <el-form-item label="系统编号"><el-input :model-value="editing?.system_code" disabled /></el-form-item>
        <el-form-item label="表英文名" required><el-input v-model="editForm.table_name_en" placeholder="不允许空格" /></el-form-item>
        <el-form-item label="表中文名" required><el-input v-model="editForm.table_name_cn" placeholder="不允许空格" /></el-form-item>
        <el-form-item label="表含义"><el-input v-model="editForm.table_meaning" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <!-- 新增表 + 字段 -->
    <UiFormDrawer v-model="createOpen" :title="`新增${title}`" width="720px" :loading="createSaving" confirm-text="保存" @submit="submitCreate">
      <el-form label-width="110px" label-position="left">
        <el-form-item label="所属项目" required>
          <el-select v-model="createForm.projectId" filterable placeholder="请选择所属项目" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.project_name}（${p.project_code}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统编号" required>
          <div class="tt-subsystem-search">
            <el-input v-model="createForm.systemCode" placeholder="输入物理子系统编号" @keyup.enter="searchSubsystem" />
            <el-button type="primary" :loading="subsystemSearching" @click="searchSubsystem">查询</el-button>
          </div>
        </el-form-item>
        <template v-if="selectedSubsystem">
          <el-form-item label="所属事业群"><el-input :model-value="selectedSubsystem.businessGroupName ?? '-'" disabled /></el-form-item>
          <el-form-item label="系统名称"><el-input :model-value="selectedSubsystem.name" disabled /></el-form-item>
        </template>
        <el-alert v-else-if="subsystemForbidden" type="warning" :closable="false" show-icon title="缺少物理子系统查询权限，无法联动带出系统信息，请先授权架构模块查询权限。" class="tt-subsystem-alert" />
        <el-alert v-else type="info" :closable="false" show-icon title="输入系统编号后点击「查询」，系统信息将自动带出（仅展示、不保存）。" class="tt-subsystem-alert" />
        <el-form-item label="表英文名" required><el-input v-model="createForm.table_name_en" placeholder="不允许空格" /></el-form-item>
        <el-form-item label="表中文名" required><el-input v-model="createForm.table_name_cn" placeholder="不允许空格" /></el-form-item>
        <el-form-item label="表含义"><el-input v-model="createForm.table_meaning" type="textarea" :rows="2" /></el-form-item>

        <h4 class="tt-section-title">字段信息（可空，保存后可继续在「字段」中维护）</h4>
        <div v-for="(f, idx) in createForm.fields" :key="idx" class="tt-create-field">
          <el-divider content-position="left">字段 {{ idx + 1 }}</el-divider>
          <el-form-item label="字段英文名" required><el-input v-model="f.fieldNameEn" placeholder="不允许空格" /></el-form-item>
          <el-form-item label="字段中文名" required><el-input v-model="f.fieldNameCn" placeholder="不允许空格" /></el-form-item>
          <el-form-item label="字段含义"><el-input v-model="f.fieldMeaning" /></el-form-item>
          <el-form-item label="码值说明"><el-input v-model="f.codeDescription" /></el-form-item>
          <el-form-item label="ORACLE类型"><el-input v-model="f.oracleType" /></el-form-item>
          <el-form-item label="mysql类型"><el-input v-model="f.mysqlType" /></el-form-item>
          <el-form-item label="数据字典编号"><el-input v-model="f.dictCode" placeholder="不允许空格" /></el-form-item>
          <el-form-item label="关键/可空/主键">
            <el-checkbox v-model="f.isKeyField" :true-value="1" :false-value="0">关键栏位</el-checkbox>
            <el-checkbox v-model="f.isNullable" :true-value="1" :false-value="0">可空</el-checkbox>
            <el-checkbox v-model="f.isPrimaryKey" :true-value="1" :false-value="0">主键</el-checkbox>
          </el-form-item>
          <el-button link type="danger" @click="removeCreateField(idx)"><el-icon><Delete /></el-icon>移除该字段</el-button>
        </div>
        <el-button link type="primary" @click="addCreateField"><el-icon><Plus /></el-icon>添加字段</el-button>
      </el-form>
    </UiFormDrawer>

    <!-- 字段操作抽屉 -->
    <UiFormDrawer v-model="fieldOpen" :title="`${title}字段维护 - ${fieldTable?.table_name_cn}`" width="860px">
      <template v-if="fieldTable">
        <el-alert type="info" :closable="false" show-icon :title="`表编号 ${fieldTable.table_code} · 系统编号 ${fieldTable.system_code} · 字段级操作，按行编辑提交`" class="tt-subsystem-alert" />
        <div class="tt-field-toolbar">
          <el-button v-if="canUpdate" type="primary" :loading="fieldBusy" @click="addField"><el-icon><Plus /></el-icon>新增字段</el-button>
          <el-button v-if="canDelete" type="danger" :loading="fieldBusy" :disabled="!selectedFieldIds.length" @click="batchDeleteFields"><el-icon><Delete /></el-icon>批量删除字段（{{ selectedFieldIds.length }}）</el-button>
        </div>
        <el-table :data="fieldRows" border size="small" row-key="id" @selection-change="(rows: TargetTableField[]) => selectedFieldIds = rows.map(r => r.id)">
          <el-table-column type="selection" width="46" />
          <el-table-column type="expand">
            <template #default="{ row }">
              <el-form label-width="110px" label-position="left" class="tt-field-edit">
                <el-form-item label="字段英文名"><el-input v-model="row.field_name_en" :disabled="editingField?.id !== row.id" /></el-form-item>
                <el-form-item label="字段中文名"><el-input v-model="row.field_name_cn" :disabled="editingField?.id !== row.id" /></el-form-item>
                <el-form-item label="字段含义"><el-input v-model="row.field_meaning" :disabled="editingField?.id !== row.id" /></el-form-item>
                <el-form-item label="码值说明"><el-input v-model="row.code_description" :disabled="editingField?.id !== row.id" /></el-form-item>
                <el-form-item label="ORACLE类型"><el-input v-model="row.oracle_type" :disabled="editingField?.id !== row.id" /></el-form-item>
                <el-form-item label="mysql类型"><el-input v-model="row.mysql_type" :disabled="editingField?.id !== row.id" /></el-form-item>
                <el-form-item label="数据字典编号"><el-input v-model="row.dict_code" :disabled="editingField?.id !== row.id" /></el-form-item>
                <el-form-item label="关键/可空/主键" v-if="editingField?.id === row.id">
                  <el-checkbox v-model="fieldForm.isKeyField" :true-value="1" :false-value="0">关键栏位</el-checkbox>
                  <el-checkbox v-model="fieldForm.isNullable" :true-value="1" :false-value="0">可空</el-checkbox>
                  <el-checkbox v-model="fieldForm.isPrimaryKey" :true-value="1" :false-value="0">主键</el-checkbox>
                </el-form-item>
                <el-form-item v-if="editingField?.id === row.id">
                  <el-button type="primary" :loading="fieldSaving" @click="saveEditField(row)">保存</el-button>
                  <el-button @click="cancelEditField">取消</el-button>
                </el-form-item>
              </el-form>
            </template>
          </el-table-column>
          <el-table-column prop="field_code" label="字段编号" min-width="110" />
          <el-table-column prop="field_name_en" label="字段英文名" min-width="120" />
          <el-table-column prop="field_name_cn" label="字段中文名" min-width="120" />
          <el-table-column label="关键" width="60" align="center"><template #default="{ row }">{{ row.is_key_field === 1 ? '是' : '否' }}</template></el-table-column>
          <el-table-column label="主键" width="60" align="center"><template #default="{ row }">{{ row.is_primary_key === 1 ? '是' : '否' }}</template></el-table-column>
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <el-button v-if="canUpdate" link type="primary" :disabled="fieldBusy" @click="editingField?.id === row.id ? cancelEditField() : openEditField(row)">{{ editingField?.id === row.id ? '取消' : '编辑' }}</el-button>
              <el-button v-if="canDelete" link type="danger" :disabled="fieldBusy" @click="removeField(row)"><el-icon><Delete /></el-icon>删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </UiFormDrawer>

    <!-- 导入 -->
    <el-dialog v-model="importVisible" title="批量上传表结构" width="520px" align-center>
      <el-upload drag :auto-upload="false" :limit="1" :on-change="(f: any) => importFile = f.raw" accept=".xlsx,.xls">
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div>将 Excel 拖到此处，或点击选择（模板列：所属项目编码/系统编号/表英文名称/表中文名称/表含义/字段…）</div>
      </el-upload>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.tt-page .ui-toolbar { align-items: flex-start; }
.tt-page .ui-toolbar__filters, .tt-page .ui-toolbar__actions { flex-wrap: wrap; }
.tt-page .dm-table-actions { gap: 6px; }
.tt-page .dm-state-panel { padding: 0; }
.tt-section-title { margin: 14px 0 8px; font-size: 14px; font-weight: 600; color: var(--text); }
.tt-subsystem-search { display: flex; width: 100%; gap: 8px; }
.tt-subsystem-alert { width: 100%; margin-bottom: 12px; }
.tt-create-field { padding: 8px 0; }
.tt-field-toolbar { margin: 8px 0; }
.tt-field-edit { padding: 8px 16px; }

@media (max-width: 760px) {
  .tt-page .ui-toolbar__filters, .tt-page .ui-toolbar__actions { width: 100%; }
  .tt-page .ui-toolbar .el-input, .tt-page .ui-toolbar .el-select { width: 100% !important; }
  .tt-page .ui-toolbar__filters > .el-button, .tt-page .ui-toolbar__actions > .el-button { flex: 1; }
  .tt-desktop { display: none; }
}
</style>
