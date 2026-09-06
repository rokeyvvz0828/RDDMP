<!-- 数迁资产内容 - 迁移映射（REQ-20260820-031 增量，多文件） -->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheck, Delete, Document, Download, Edit, MoreFilled, Plus, Refresh, Search, WarningFilled
} from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { useAuthStore } from '../../../../stores/auth'
import {
  createMapping, deleteMappings, getMappingAttachments, getMappingDownloadAll, getMappingDownloadPath,
  getSystemOptions, getMappingTypeOptions, listMappings, updateMapping,
  type MappingFormData, type MappingQuery, type MappingRecord, type MappingUpdateData, type SelectOption
} from '../../../../api/data-migration'
import { getAttachmentDownload, uploadAttachment } from '../../../../api/attachments'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'

type UploadStatus = 'pending' | 'uploading' | 'uploaded' | 'error'
interface MappingFormState { projectId: number | null; mappingType: string; systemCode: string; fileName: string }
interface AttachmentEntry { attachmentId: number; fileName: string }
interface UploadEntry { key: string; file: File; status: UploadStatus; attachmentId?: number; error?: string }

const MAX_SIZE = 50 * 1024 * 1024
const MAX_FILE_NAME_LENGTH = 200
const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const loading = ref(false)
const records = ref<MappingRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selectedIds = ref<number[]>([])
const actionBusy = ref(false)
const listError = ref('')
const optionError = ref('')
const mappingTypeOptions = ref<SelectOption[]>([])
const systemOptions = ref<SelectOption[]>([])
const typeOptionsLoading = ref(false)
const systemOptionsLoading = ref(false)
const filterMappingType = ref('')
const filterSystem = ref('')
const filterKeyword = ref('')

const drawerOpen = ref(false)
const saving = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const form = ref<MappingFormState>({ projectId: null, mappingType: '', systemCode: '', fileName: '' })
const existingAttachments = ref<AttachmentEntry[]>([])
const pendingUploads = ref<UploadEntry[]>([])
const attachmentsLoading = ref(false)
const attachmentLoadError = ref('')
const submitError = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
let uploadSequence = 0

const downloadDialogOpen = ref(false)
const downloadTarget = ref<MappingRecord | null>(null)
const downloadAttachments = ref<AttachmentEntry[]>([])
const downloadLoading = ref(false)
const downloadError = ref('')
const downloadingAttachmentId = ref<number | null>(null)
const downloadingAll = ref(false)

// mappingAdminRoles removed: use permission-based checks instead
const canCreate = computed(() => auth.hasPermission('data-migration:content:mappings:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdatePermission = computed(() => auth.hasPermission('data-migration:content:mappings:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDeletePermission = computed(() => auth.hasPermission('data-migration:content:mappings:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEdit = (item: MappingRecord) => hasUpdatePermission.value && (canManage.value || item.created_by === auth.user?.id)
const canDelete = (item: MappingRecord) => hasDeletePermission.value && (canManage.value || item.created_by === auth.user?.id)
const hasFilters = computed(() => Boolean(filterMappingType.value || filterSystem.value || filterKeyword.value.trim()))
const hasUploadingFiles = computed(() => pendingUploads.value.some(item => item.status === 'uploading'))
const hasFailedFiles = computed(() => pendingUploads.value.some(item => item.status === 'error'))
const formBusy = computed(() => saving.value || attachmentsLoading.value || hasUploadingFiles.value)

const messageOf = (cause: unknown, fallback = '操作失败') => cause instanceof Error && cause.message ? cause.message : fallback
const cancelled = (cause: unknown) => cause === 'cancel' || cause === 'close' || (cause instanceof Error && ['cancel', 'close'].includes(cause.message))
const raw = (item: MappingRecord): Record<string, unknown> => item as unknown as Record<string, unknown>
const typeLabel = (value: string) => mappingTypeOptions.value.find(option => String(option.value) === String(value))?.label ?? value
const systemLabel = (item: MappingRecord) => item.system_short_name || item.system_name
  ? `${item.system_code} - ${item.system_short_name || item.system_name}` : item.system_code || '—'
const fileSizeLabel = (bytes: number) => bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.ceil(bytes / 1024))} KB`
const uploadStatusLabel = (status: UploadStatus) => ({ pending: '待上传', uploading: '上传中', uploaded: '已上传', error: '上传失败' })[status]
const uploadStatusType = (status: UploadStatus) => status === 'uploaded' ? 'success' : status === 'error' ? 'danger' : status === 'uploading' ? 'warning' : 'info'

async function fetchList() {
  const requestedProjectId = scopeProjectId.value
  if (!requestedProjectId) { records.value = []; total.value = 0; return }
  loading.value = true
  listError.value = ''
  try {
    const params: MappingQuery = { projectId: requestedProjectId, page: page.value, size: size.value }
    if (filterMappingType.value) params.mappingType = filterMappingType.value
    if (filterSystem.value) params.systemCode = filterSystem.value
    if (filterKeyword.value.trim()) params.keyword = filterKeyword.value.trim()
    const response = await listMappings(params)
    if (scopeProjectId.value !== requestedProjectId) return
    records.value = response.data.data?.records ?? []
    total.value = response.data.data?.total ?? 0
  } catch (cause) {
    if (scopeProjectId.value === requestedProjectId) listError.value = messageOf(cause, '迁移映射加载失败')
  } finally {
    if (scopeProjectId.value === requestedProjectId) loading.value = false
  }
}

async function loadTypeOptions() {
  typeOptionsLoading.value = true
  try { mappingTypeOptions.value = (await getMappingTypeOptions()).data.data ?? [] }
  catch (cause) { mappingTypeOptions.value = []; optionError.value = messageOf(cause, '映射类型加载失败') }
  finally { typeOptionsLoading.value = false }
}

async function loadSystemOptions() {
  const requestedProjectId = scopeProjectId.value
  if (!requestedProjectId) return
  systemOptionsLoading.value = true
  try {
    const response = await getSystemOptions(requestedProjectId)
    if (scopeProjectId.value !== requestedProjectId) return
    systemOptions.value = response.data.data ?? []
  } catch (cause) {
    if (scopeProjectId.value === requestedProjectId) {
      systemOptions.value = []
      optionError.value = messageOf(cause, '系统选项加载失败')
    }
  } finally {
    if (scopeProjectId.value === requestedProjectId) systemOptionsLoading.value = false
  }
}

async function loadPage() {
  optionError.value = ''
  await Promise.allSettled([fetchList(), loadTypeOptions(), loadSystemOptions()])
}

function handleSearch() { page.value = 1; void fetchList() }
function resetFilters() {
  filterMappingType.value = ''; filterSystem.value = ''; filterKeyword.value = ''; page.value = 1; void fetchList()
}

function resetEditor() {
  editing.value = false
  editId.value = null
  form.value = { projectId: scopeProjectId.value, mappingType: '', systemCode: '', fileName: '' }
  existingAttachments.value = []
  pendingUploads.value = []
  attachmentsLoading.value = false
  attachmentLoadError.value = ''
  submitError.value = ''
}

function openCreate() {
  resetEditor()
  drawerOpen.value = true
  if (!mappingTypeOptions.value.length) void loadTypeOptions()
  if (!systemOptions.value.length) void loadSystemOptions()
}

async function loadEditorAttachments(mappingId: number) {
  const requestedProjectId = scopeProjectId.value
  attachmentsLoading.value = true
  attachmentLoadError.value = ''
  try {
    const response = await getMappingAttachments(mappingId)
    if (scopeProjectId.value !== requestedProjectId || editId.value !== mappingId) return
    existingAttachments.value = (response.data.data ?? []).map(item => ({ attachmentId: item.attachment_id, fileName: item.file_name }))
  } catch (cause) {
    if (scopeProjectId.value === requestedProjectId && editId.value === mappingId) attachmentLoadError.value = messageOf(cause, '附件列表加载失败')
  } finally {
    if (scopeProjectId.value === requestedProjectId && editId.value === mappingId) attachmentsLoading.value = false
  }
}

function openEdit(item: MappingRecord) {
  resetEditor()
  editing.value = true
  editId.value = item.id
  form.value = { projectId: scopeProjectId.value, mappingType: item.mapping_type, systemCode: item.system_code, fileName: item.asset_name }
  drawerOpen.value = true
  void loadEditorAttachments(item.id)
}

function chooseFiles() { if (!formBusy.value) fileInput.value?.click() }
function onFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement
  for (const file of Array.from(input.files ?? [])) {
    if (file.size <= 0) { ElMessage.warning(`文件 ${file.name} 为空，未加入上传列表`); continue }
    if (file.size > MAX_SIZE) { ElMessage.warning(`文件 ${file.name} 超过 50MB，未加入上传列表`); continue }
    const duplicated = pendingUploads.value.some(item => item.file.name === file.name && item.file.size === file.size && item.file.lastModified === file.lastModified)
    if (duplicated) { ElMessage.warning(`文件 ${file.name} 已在上传列表中`); continue }
    pendingUploads.value.push({ key: `${Date.now()}-${uploadSequence++}`, file, status: 'pending' })
  }
  input.value = ''
}

function removeExistingAttachment(index: number) { if (!formBusy.value) existingAttachments.value.splice(index, 1) }
function removePendingUpload(index: number) { if (!formBusy.value) pendingUploads.value.splice(index, 1) }

async function uploadOne(entry: UploadEntry) {
  entry.status = 'uploading'
  entry.error = ''
  try {
    const attachment = (await uploadAttachment(entry.file)).data.data
    if (!attachment?.id) throw new Error('上传成功但未返回附件编号')
    entry.attachmentId = attachment.id
    entry.status = 'uploaded'
  } catch (cause) {
    entry.attachmentId = undefined
    entry.status = 'error'
    entry.error = messageOf(cause, '文件上传失败')
  }
}

async function retryUpload(entry: UploadEntry) {
  if (formBusy.value || entry.status !== 'error') return
  await uploadOne(entry)
}

async function handleSave() {
  const projectId = scopeProjectId.value
  if (!projectId) return
  if (!form.value.mappingType) return void ElMessage.warning('请选择映射类型')
  if (!form.value.systemCode) return void ElMessage.warning('请选择系统编号')
  const fileName = form.value.fileName.trim()
  if (!fileName) return void ElMessage.warning('请输入文件名称')
  if (Array.from(fileName).length > MAX_FILE_NAME_LENGTH) return void ElMessage.warning('文件名称不能超过 200 个字符')
  if (attachmentsLoading.value) return void ElMessage.warning('附件列表仍在加载')
  if (attachmentLoadError.value) return void ElMessage.warning('请先重新加载附件列表')
  if (existingAttachments.value.length + pendingUploads.value.length === 0) {
    return void ElMessage.warning(editing.value ? '请至少保留一个源文件' : '请选择要绑定的源文件')
  }
  if (hasFailedFiles.value) return void ElMessage.warning('请先重试或移除上传失败的文件')

  saving.value = true
  submitError.value = ''
  try {
    await Promise.all(pendingUploads.value.filter(item => item.status === 'pending').map(uploadOne))
    if (hasFailedFiles.value) { ElMessage.warning('部分文件上传失败，已保留成功项，请重试失败文件'); return }
    if (scopeProjectId.value !== projectId) {
      ElMessage.warning('当前项目已切换，本次记录未提交')
      return
    }
    const files: AttachmentEntry[] = [
      ...existingAttachments.value,
      ...pendingUploads.value
        .filter((item): item is UploadEntry & { attachmentId: number } => item.status === 'uploaded' && typeof item.attachmentId === 'number')
        .map(item => ({ attachmentId: item.attachmentId, fileName: item.file.name }))
    ]
    if (!files.length) return void ElMessage.warning('请至少保留一个源文件')
    const payload: MappingFormData = { projectId, mappingType: form.value.mappingType, systemCode: form.value.systemCode, fileName, files }
    if (editing.value && editId.value) {
      const update: MappingUpdateData = { mappingType: payload.mappingType, systemCode: payload.systemCode, fileName: payload.fileName, files: payload.files }
      await updateMapping(editId.value, update)
      ElMessage.success('迁移映射已更新')
    } else {
      await createMapping(payload)
      ElMessage.success('迁移映射已创建')
    }
    drawerOpen.value = false
    resetEditor()
    await fetchList()
  } catch (cause) {
    submitError.value = messageOf(cause)
    ElMessage.error(submitError.value)
  } finally { saving.value = false }
}

async function loadDownloadAttachments() {
  const target = downloadTarget.value
  if (!target) return
  downloadLoading.value = true
  downloadError.value = ''
  try {
    const response = await getMappingAttachments(target.id)
    if (downloadTarget.value?.id !== target.id) return
    downloadAttachments.value = (response.data.data ?? []).map(item => ({ attachmentId: item.attachment_id, fileName: item.file_name }))
    if (!downloadAttachments.value.length) downloadError.value = '当前迁移映射没有可下载的附件'
  } catch (cause) {
    if (downloadTarget.value?.id === target.id) downloadError.value = messageOf(cause, '附件列表加载失败')
  } finally {
    if (downloadTarget.value?.id === target.id) downloadLoading.value = false
  }
}

function openDownloads(item: MappingRecord) {
  downloadTarget.value = item
  downloadAttachments.value = []
  downloadError.value = ''
  downloadDialogOpen.value = true
  void loadDownloadAttachments()
}

function triggerDownload(url: string, fileName: string) {
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
}

async function downloadOne(attachment: AttachmentEntry) {
  const target = downloadTarget.value
  if (!target) return
  downloadingAttachmentId.value = attachment.attachmentId
  downloadError.value = ''
  try {
    await getMappingDownloadPath(target.id, attachment.attachmentId)
    const url = (await getAttachmentDownload(attachment.attachmentId)).data.data?.downloadUrl
    if (!url) throw new Error('附件下载地址不可用')
    triggerDownload(url, attachment.fileName)
  } catch (cause) { downloadError.value = messageOf(cause, '附件下载失败') }
  finally { downloadingAttachmentId.value = null }
}

async function downloadAll(item = downloadTarget.value) {
  if (!item) return
  downloadingAll.value = true
  try {
    const response = await getMappingDownloadAll(item.id)
    const url = URL.createObjectURL(response.data as Blob)
    triggerDownload(url, `${item.asset_name || '迁移映射'}.zip`)
    URL.revokeObjectURL(url)
  } catch (cause) {
    const error = messageOf(cause, '打包下载失败')
    if (downloadDialogOpen.value) downloadError.value = error
    else ElMessage.error(error)
  } finally { downloadingAll.value = false }
}

async function handleDelete(ids?: number[]) {
  const targetIds = ids ?? selectedIds.value
  if (!targetIds.length) return void ElMessage.warning('请先选择要删除的迁移映射')
  try {
    await ElMessageBox.confirm(`确定将选中的 ${targetIds.length} 条迁移映射移入回收站吗？`, '移入回收站', {
      type: 'warning', confirmButtonText: '移入回收站', cancelButtonText: '取消'
    })
    actionBusy.value = true
    await deleteMappings(targetIds)
    selectedIds.value = []
    ElMessage.success('已移入回收站')
    await fetchList()
  } catch (cause) { if (!cancelled(cause)) ElMessage.error(messageOf(cause)) }
  finally { actionBusy.value = false }
}

function handleRowCommand(command: string, item: MappingRecord) {
  if (command === 'edit') openEdit(item)
  if (command === 'delete') void handleDelete([item.id])
}
function handleSelectionChange(items: MappingRecord[]) { selectedIds.value = items.map(item => item.id) }

function resetForProject() {
  drawerOpen.value = false
  downloadDialogOpen.value = false
  downloadTarget.value = null
  records.value = []
  total.value = 0
  selectedIds.value = []
  page.value = 1
  filterMappingType.value = ''
  filterSystem.value = ''
  filterKeyword.value = ''
  mappingTypeOptions.value = []
  systemOptions.value = []
  listError.value = ''
  optionError.value = ''
  resetEditor()
}

watch(scopeProjectId, projectId => { resetForProject(); if (projectId) void loadPage() })
onMounted(() => { void scope.ensureLoaded() })
</script>

<template>
  <section class="dm-page mapping-page">
    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <template v-else>
      <UiPageHeader title="迁移映射">
        <template #actions><el-button v-if="canCreate" type="primary" :icon="Plus" :disabled="loading || actionBusy" @click="openCreate">新增</el-button></template>
      </UiPageHeader>

      <UiToolbar class="mapping-toolbar">
        <el-select v-model="filterMappingType" placeholder="映射类型" clearable filterable :loading="typeOptionsLoading" @change="handleSearch">
          <el-option v-for="option in mappingTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="filterSystem" placeholder="系统编号或名称" clearable filterable :loading="systemOptionsLoading" @change="handleSearch">
          <el-option v-for="option in systemOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-input v-model="filterKeyword" clearable placeholder="文件名称" @keyup.enter="handleSearch"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <template #actions>
          <el-button :icon="Refresh" :disabled="loading" @click="fetchList">刷新</el-button>
          <el-button type="primary" :icon="Search" :disabled="loading" @click="handleSearch">查询</el-button>
          <el-button :disabled="loading" @click="resetFilters">重置</el-button>
          <el-button v-if="selectedIds.length" type="danger" plain :icon="Delete" :disabled="actionBusy" @click="handleDelete()">移入回收站（{{ selectedIds.length }}）</el-button>
        </template>
      </UiToolbar>

      <el-alert v-if="optionError" class="mapping-state-alert" type="warning" :closable="false" show-icon :title="optionError"><el-button link type="primary" @click="loadPage">重新加载</el-button></el-alert>
      <el-alert v-if="listError" class="mapping-state-alert" type="error" :closable="false" show-icon :title="listError"><el-button link type="primary" @click="fetchList">重试</el-button></el-alert>

      <div class="dm-desktop-table">
        <UiDataTable :data="records" :loading="loading" row-key="id" @selection-change="handleSelectionChange">
          <el-table-column v-if="hasDeletePermission" type="selection" width="48" :selectable="canDelete" />
          <el-table-column label="系统" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ systemLabel(row) }}</template></el-table-column>
          <el-table-column label="映射类型" width="130"><template #default="{ row }">{{ typeLabel(row.mapping_type) }}</template></el-table-column>
          <el-table-column label="文件编号" width="180" show-overflow-tooltip><template #default="{ row }">{{ raw(row).asset_code ?? '—' }}</template></el-table-column>
          <el-table-column label="文件名称" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.asset_name || '—' }}</template></el-table-column>
          <el-table-column label="操作" width="176" fixed="right" align="center"><template #default="{ row }"><div class="mapping-row-actions">
            <el-button link type="primary" :icon="Download" :disabled="actionBusy" @click="openDownloads(row)">下载</el-button>
            <el-dropdown v-if="canEdit(row) || canDelete(row)" trigger="click" @command="(command: string) => handleRowCommand(command, row)">
              <el-button link :icon="MoreFilled" :disabled="actionBusy">更多</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item v-if="canEdit(row)" command="edit" :icon="Edit">编辑</el-dropdown-item><el-dropdown-item v-if="canDelete(row)" command="delete" :icon="Delete" divided>移入回收站</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </div></template></el-table-column>
        </UiDataTable>
      </div>

      <div v-if="records.length || loading" class="dm-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="item in records" :key="item.id">
          <header><div><strong>{{ item.asset_name || '—' }}</strong><small>{{ item.asset_code || '—' }}</small></div><el-tag size="small" type="info" effect="plain">{{ typeLabel(item.mapping_type) }}</el-tag></header>
          <dl>
            <div><dt>系统</dt><dd>{{ systemLabel(item) }}</dd></div><div><dt>映射类型</dt><dd>{{ typeLabel(item.mapping_type) }}</dd></div>
            <div><dt>文件编号</dt><dd>{{ item.asset_code || '—' }}</dd></div><div><dt>文件名称</dt><dd>{{ item.asset_name || '—' }}</dd></div>
          </dl>
          <footer>
            <el-button link type="primary" :icon="Download" :disabled="actionBusy" @click="openDownloads(item)">下载</el-button>
            <el-dropdown v-if="canEdit(item) || canDelete(item)" trigger="click" @command="(command: string) => handleRowCommand(command, item)">
              <el-button link :icon="MoreFilled" :disabled="actionBusy">更多</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item v-if="canEdit(item)" command="edit" :icon="Edit">编辑</el-dropdown-item><el-dropdown-item v-if="canDelete(item)" command="delete" :icon="Delete" divided>移入回收站</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </footer>
        </article>
      </div>

      <UiEmptyState v-if="!loading && !listError && records.length === 0" :title="hasFilters ? '没有匹配的迁移映射' : '暂无迁移映射'" :description="hasFilters ? '请调整筛选条件后重试。' : '当前项目下还没有迁移映射记录。'">
        <template v-if="hasFilters" #action><el-button @click="resetFilters">清空筛选</el-button></template>
      </UiEmptyState>

      <div v-if="total > 0" class="dm-table-footer mapping-pagination"><span>共 {{ total }} 条</span><UiPagination :page="page" :page-size="size" :total="total" :page-sizes="[20, 50, 100]" @update:page-size="(value: number) => { size = value; page = 1; fetchList() }" @update:page="(value: number) => { page = value; fetchList() }" /></div>

      <UiFormDrawer v-model="drawerOpen" :title="editing ? '编辑迁移映射' : '新增迁移映射'" width="min(520px, 100vw)" :loading="formBusy" @submit="handleSave">
        <el-alert v-if="attachmentLoadError" class="mapping-state-alert" type="error" :closable="false" show-icon :title="attachmentLoadError"><el-button v-if="editing && editId" link type="primary" @click="loadEditorAttachments(editId)">重新加载附件</el-button></el-alert>
        <el-alert v-if="submitError" class="mapping-state-alert" type="error" :closable="false" show-icon :title="submitError" />
        <el-form label-position="top" :disabled="saving">
          <el-form-item label="映射类型" required><el-select v-model="form.mappingType" placeholder="请选择映射类型" filterable :loading="typeOptionsLoading"><el-option v-for="option in mappingTypeOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
          <el-form-item label="系统编号" required><el-select v-model="form.systemCode" placeholder="输入系统编号或名称" filterable :loading="systemOptionsLoading"><el-option v-for="option in systemOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
          <el-form-item label="文件名称" required><el-input v-model="form.fileName" placeholder="请输入文件名称" maxlength="200" show-word-limit /></el-form-item>
          <el-form-item label="源文件" required>
            <div v-loading="attachmentsLoading" class="mapping-attachment-list">
              <div v-for="(attachment, index) in existingAttachments" :key="`existing-${attachment.attachmentId}`" class="mapping-attachment-item">
                <el-icon class="mapping-attachment-icon" :size="18"><Document /></el-icon><div class="mapping-attachment-info"><strong>{{ attachment.fileName }}</strong><span>已绑定</span></div><el-button link type="danger" :disabled="formBusy" @click="removeExistingAttachment(index)">移除</el-button>
              </div>
              <div v-for="(entry, index) in pendingUploads" :key="entry.key" class="mapping-attachment-item">
                <el-icon class="mapping-attachment-icon" :class="`is-${entry.status}`" :size="18"><CircleCheck v-if="entry.status === 'uploaded'" /><WarningFilled v-else-if="entry.status === 'error'" /><Document v-else /></el-icon>
                <div class="mapping-attachment-info"><strong>{{ entry.file.name }}</strong><span>{{ fileSizeLabel(entry.file.size) }}</span><small v-if="entry.error">{{ entry.error }}</small></div>
                <el-tag size="small" effect="plain" :type="uploadStatusType(entry.status)">{{ uploadStatusLabel(entry.status) }}</el-tag>
                <el-button v-if="entry.status === 'error'" link type="primary" :icon="Refresh" :disabled="formBusy" @click="retryUpload(entry)">重试</el-button><el-button link type="danger" :disabled="formBusy" @click="removePendingUpload(index)">移除</el-button>
              </div>
            </div>
            <el-button :icon="Plus" :disabled="formBusy" @click="chooseFiles">添加文件</el-button><input ref="fileInput" type="file" multiple hidden @change="onFilesSelected" />
          </el-form-item>
        </el-form>
      </UiFormDrawer>

      <el-dialog v-model="downloadDialogOpen" class="mapping-download-dialog" title="下载附件" width="min(560px, calc(100vw - 24px))" destroy-on-close>
        <div v-loading="downloadLoading" class="mapping-download-list">
          <el-alert v-if="downloadError" type="error" :closable="false" show-icon :title="downloadError"><el-button v-if="!downloadAttachments.length" link type="primary" @click="loadDownloadAttachments">重试</el-button></el-alert>
          <div v-for="attachment in downloadAttachments" :key="attachment.attachmentId" class="mapping-download-item"><el-icon :size="18"><Document /></el-icon><span>{{ attachment.fileName }}</span><el-button link type="primary" :icon="Download" :loading="downloadingAttachmentId === attachment.attachmentId" :disabled="downloadingAll || downloadingAttachmentId !== null" @click="downloadOne(attachment)">下载</el-button></div>
        </div>
        <template #footer><el-button @click="downloadDialogOpen = false">关闭</el-button><el-button type="primary" :icon="Download" :loading="downloadingAll" :disabled="downloadLoading || !downloadAttachments.length || downloadingAttachmentId !== null" @click="downloadAll()">全部下载</el-button></template>
      </el-dialog>
    </template>
  </section>
</template>

<style scoped>
.mapping-toolbar :deep(.ui-toolbar__filters > .el-select), .mapping-toolbar :deep(.ui-toolbar__filters > .el-input) { width: 210px; }
.mapping-state-alert { margin-bottom: 12px; }
.mapping-state-alert :deep(.el-alert__content) { min-width: 0; }
.mapping-row-actions { display: flex; align-items: center; justify-content: center; gap: 12px; }
.mapping-row-actions :deep(.el-button) { margin-left: 0; }
.mapping-pagination { margin-top: 16px; }
.mapping-attachment-list, .mapping-download-list { display: grid; width: 100%; min-height: 32px; gap: 8px; margin-bottom: 10px; }
.mapping-attachment-item, .mapping-download-item { display: flex; min-width: 0; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel-bg); }
.mapping-attachment-icon { flex: 0 0 auto; color: var(--brand); }
.mapping-attachment-icon.is-uploaded { color: var(--el-color-success); }
.mapping-attachment-icon.is-error { color: var(--el-color-danger); }
.mapping-attachment-info { display: grid; min-width: 0; flex: 1; gap: 2px; }
.mapping-attachment-info strong, .mapping-download-item span { min-width: 0; overflow-wrap: anywhere; color: var(--text); font-size: 13px; font-weight: 500; }
.mapping-attachment-info span { color: var(--muted); font-size: 11px; }
.mapping-attachment-info small { overflow-wrap: anywhere; color: var(--el-color-danger); font-size: 11px; }
.mapping-download-item span { flex: 1; }
@media (max-width: 760px) {
  .mapping-toolbar :deep(.ui-toolbar__filters), .mapping-toolbar :deep(.ui-toolbar__actions) { width: 100%; flex-wrap: wrap; }
  .mapping-toolbar :deep(.ui-toolbar__filters > .el-select), .mapping-toolbar :deep(.ui-toolbar__filters > .el-input) { width: 100%; }
  .mapping-toolbar :deep(.ui-toolbar__actions > .el-button) { flex: 1 1 120px; margin-left: 0; }
  .mapping-attachment-item { align-items: flex-start; flex-wrap: wrap; }
  .mapping-attachment-info { flex-basis: calc(100% - 30px); }
  :deep(.mapping-download-dialog) { width: calc(100vw - 24px) !important; margin: 12px auto; }
}
</style>
