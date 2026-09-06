<!--
  用途：数迁资产内容 - 迁移程序（REQ-20260820-031 增量，方案B多文件）
  说明：统一管理迁出/迁入程序包，程序类型经系统管理/参数管理维护，系统编号单选自
        当前项目 dm_component 启用清单（前端本地随输随筛，编号/名称均可、不区分大小写）。
        支持程序类型/系统编号/程序包名称组合筛选、分页、多文件上传、编辑、单文件与
        打包下载、详情和逻辑删除；统一回收站继续以 SCRIPT 类型分发。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, Download, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import { useAuthStore } from '../../../../stores/auth'
import {
  listPrograms, getProgram, createProgram, updateProgram, deletePrograms,
  getProgramTypeOptions, getSystemOptions, getProgramDownloadPath, getProgramDownloadAll,
  getProgramAttachments, DM_CODE_CATEGORIES,
  type ProgramRecord, type ProgramQuery, type ProgramFormData, type ProgramUpdateData, type SelectOption
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'
import { uploadAttachment } from '../../../../api/attachments'

const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const loading = ref(false), records = ref<ProgramRecord[]>([]), total = ref(0), page = ref(1), size = ref(20), selectedIds = ref<number[]>([]), busy = ref(false)
const fProgramType = ref(''), fSystemCode = ref(''), fKeyword = ref('')
const filterTypeOpts = ref<SelectOption[]>([])
const systemOpts = ref<SelectOption[]>([])
const systemOptsLoading = ref(false)

const drawer = ref(false), saving = ref(false), editing = ref(false), editId = ref<number | null>(null)
interface ProgramFormState {
  projectId: number | null
  programType: string
  systemCode: string
  programName: string
  programDescription: string
}
const fv = ref<ProgramFormState>({ projectId: null, programType: '', systemCode: '', programName: '', programDescription: '' })
const formTypeOpts = ref<SelectOption[]>([])
interface AttachmentEntry { attachmentId: number; fileName: string }
const existingAttachments = ref<AttachmentEntry[]>([])
const pendingFiles = ref<File[]>([])

const detailDialogOpen = ref(false), detailLoading = ref(false), detailData = ref<ProgramRecord | null>(null)

const canCreate = computed(() => auth.hasPermission('data-migration:content:programs:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdatePermission = computed(() => auth.hasPermission('data-migration:content:programs:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDeletePermission = computed(() => auth.hasPermission('data-migration:content:programs:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEdit = (item: ProgramRecord) => hasUpdatePermission.value && (canManage.value || item.created_by === auth.user?.id)
const canDelete = (item: ProgramRecord) => hasDeletePermission.value && (canManage.value || item.created_by === auth.user?.id)
const msg = (e: unknown) => e instanceof Error ? e.message : '操作失败'
const cancelled = (e: unknown): boolean => e === 'cancel' || e === 'close' || (e instanceof Error && (e.message === 'cancel' || e.message === 'close'))
const sd = (i: ProgramRecord): Record<string, any> => i as Record<string, any>
const fmtDate = (v?: string | null) => { if (!v) return '—'; const d = new Date(v); if (Number.isNaN(d.getTime())) return '—'; return d.toLocaleString() }
const MAX_SIZE = 50 * 1024 * 1024

const fetchList = async () => {
  if (!scopeProjectId.value) return
  loading.value = true
  try {
    const params: ProgramQuery = { projectId: scopeProjectId.value, page: page.value, size: size.value }
    if (fProgramType.value) params.programType = fProgramType.value
    if (fSystemCode.value) params.systemCode = fSystemCode.value
    if (fKeyword.value.trim()) params.keyword = fKeyword.value.trim()
    const { data } = await listPrograms(params)
    records.value = data.data?.records ?? []
    total.value = data.data?.total ?? 0
  } catch (e) { ElMessage.error(msg(e)) } finally { loading.value = false }
}

const loadSystemOptions = async () => {
  if (!scopeProjectId.value) { systemOpts.value = []; return }
  systemOptsLoading.value = true
  try {
    const { data } = await getSystemOptions(scopeProjectId.value)
    systemOpts.value = data.data ?? []
  } catch { systemOpts.value = [] } finally { systemOptsLoading.value = false }
}

const fetchFilterTypeOpts = async () => {
  try { const { data } = await getProgramTypeOptions(); filterTypeOpts.value = data.data ?? [] } catch { filterTypeOpts.value = [] }
}

const fetchFormTypeOpts = async () => {
  try { const { data } = await getProgramTypeOptions(); formTypeOpts.value = data.data ?? [] } catch { formTypeOpts.value = [] }
}

const resetFilters = () => { fProgramType.value = ''; fSystemCode.value = ''; fKeyword.value = ''; page.value = 1 }
const handleSearch = () => { page.value = 1; fetchList() }
const handlePageChange = (p: number) => { page.value = p; fetchList() }
const handleSizeChange = (s: number) => { size.value = s; page.value = 1; fetchList() }

const openCreate = () => {
  editing.value = false; editId.value = null
  fv.value = { projectId: scopeProjectId.value, programType: '', systemCode: '', programName: '', programDescription: '' }
  existingAttachments.value = []; pendingFiles.value = []
  fetchFormTypeOpts()
  drawer.value = true
}

const openEdit = async (row: ProgramRecord) => {
  editing.value = true; editId.value = row.id
  busy.value = true
  try {
    const { data } = await getProgram(row.id)
    const d = data.data!
    fv.value = { projectId: scopeProjectId.value, programType: d.program_type, systemCode: d.system_code, programName: d.asset_name, programDescription: d.program_description ?? '' }
    await fetchFormTypeOpts()
    try { const { data: attData } = await getProgramAttachments(row.id); existingAttachments.value = (attData.data ?? []).map(a => ({ attachmentId: a.attachment_id, fileName: a.file_name })) } catch { existingAttachments.value = [] }
    pendingFiles.value = []
    drawer.value = true
  } catch (e) { ElMessage.error(msg(e)) } finally { busy.value = false }
}

const removeExistingAtt = (idx: number) => { existingAttachments.value.splice(idx, 1) }
const removePendingFile = (idx: number) => { pendingFiles.value.splice(idx, 1) }
const onFilesSelected = (e: Event) => {
  const input = e.target as HTMLInputElement
  const inputs = Array.from(input.files ?? [])
  const accepted: File[] = []
  for (const file of inputs) {
    if (file.size > MAX_SIZE) { ElMessage.warning(`文件 ${file.name} 超过 50MB，已跳过`); continue }
    accepted.push(file)
  }
  pendingFiles.value.push(...accepted)
  input.value = ''
}

const handleSave = async () => {
  if (!fv.value.programType) { ElMessage.warning('请选择程序类型'); return }
  if (!fv.value.systemCode) { ElMessage.warning('请选择系统编号'); return }
  if (!fv.value.programName?.trim()) { ElMessage.warning('请输入程序包名称'); return }

  const finalExisting = editing.value ? existingAttachments.value.map(a => ({ attachmentId: a.attachmentId, fileName: a.fileName })) : []
  if (finalExisting.length === 0 && pendingFiles.value.length === 0) { ElMessage.warning(editing.value ? '请至少保留一个源文件' : '请选择要绑定的源文件'); return }

  saving.value = true
  try {
    const uploaded: { attachmentId: number; fileName: string }[] = []
    for (const file of pendingFiles.value) {
      if (file.size > MAX_SIZE) { ElMessage.warning(`文件 ${file.name} 超过 50MB，已跳过`); continue }
      const { data } = await uploadAttachment(file)
      uploaded.push({ attachmentId: data.data!.id, fileName: file.name })
    }
    const body: ProgramFormData = {
      projectId: scopeProjectId.value!,
      programType: fv.value.programType,
      systemCode: fv.value.systemCode,
      programName: fv.value.programName.trim(),
      programDescription: fv.value.programDescription.trim() || undefined,
      files: [...finalExisting, ...uploaded]
    }

    if (editing.value && editId.value) {
      const patch: ProgramUpdateData = { programType: body.programType, systemCode: body.systemCode, programName: body.programName, programDescription: body.programDescription, files: body.files }
      if (!pendingFiles.value.length && !body.files.length) delete patch.files
      await updateProgram(editId.value, patch)
      ElMessage.success('迁移程序已更新')
    } else {
      await createProgram(body)
      ElMessage.success('迁移程序已创建')
    }
    drawer.value = false; fetchList()
  } catch (e) { ElMessage.error(msg(e)) } finally { saving.value = false }
}

const handleDelete = async (ids?: number[]) => {
  const targetIds = ids ?? selectedIds.value
  if (targetIds.length === 0) { ElMessage.warning('请先选择要删除的程序包'); return }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${targetIds.length} 个程序包吗？删除后可到回收站恢复。`, '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    busy.value = true
    await deletePrograms(targetIds)
    ElMessage.success('已移入回收站')
    if (selectedIds.value.length) selectedIds.value = []
    fetchList()
  } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false }
}

const downloadSingle = async (row: ProgramRecord, attachmentId?: number) => {
  try {
    const { data } = await getProgramDownloadPath(row.id, attachmentId)
    const downloadUrl = data.data!
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = row.asset_name || '迁移程序'
    document.body.appendChild(link); link.click(); document.body.removeChild(link)
  } catch (e) { ElMessage.error(msg(e)) }
}

const downloadAll = async (row: ProgramRecord) => {
  try {
    const response = await getProgramDownloadAll(row.id)
    const blob = response.data as Blob
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${row.asset_name || '迁移程序'}.zip`
    document.body.appendChild(link); link.click(); document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (e) { ElMessage.error(msg(e)) }
}

const openDetail = async (row: ProgramRecord) => {
  detailDialogOpen.value = true; detailLoading.value = true
  try {
    const { data } = await getProgram(row.id)
    const source = data.data!
    const rawAttachments = (source as unknown as Record<string, unknown>).attachments as Array<Record<string, unknown>> | undefined
    const attachments = (rawAttachments ?? []).map(a => ({
      attachmentId: Number(a.attachment_id ?? a.attachmentId),
      fileName: String(a.file_name ?? a.fileName ?? '')
    }))
    detailData.value = { ...source, attachments }
  } catch (e) { ElMessage.error(msg(e)); detailDialogOpen.value = false } finally { detailLoading.value = false }
}

const typeLabel = (v: string) => filterTypeOpts.value.find(o => String(o.value) === String(v))?.label ?? v
const systemLabel = (row: ProgramRecord) => {
  const label = systemOpts.value.find(o => String(o.value) === String(row.system_code))?.label
  return (typeof label === 'string' && label) || (row.system_name ? `${row.system_code} - ${row.system_name}` : (row.system_code || '—'))
}

watch(scopeProjectId, () => { page.value = 1; resetFilters(); fetchList(); void loadSystemOptions(); fetchFilterTypeOpts() })
onMounted(() => { void scope.ensureLoaded(); fetchList(); void loadSystemOptions(); fetchFilterTypeOpts() })
</script>

<template>
  <section class="dm-page">
    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <template v-else>
      <UiPageHeader title="迁移程序" description="统一管理迁出/迁入程序包，支持程序类型、系统编号检索，多文件上传、下载与回收站管理。">
        <template #actions>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新增程序包</el-button>
        </template>
      </UiPageHeader>

      <UiToolbar class="dm-topic-toolbar">
        <el-select v-model="fProgramType" placeholder="程序类型" clearable size="default" style="width:140px" @change="handleSearch">
          <el-option v-for="t in filterTypeOpts" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="fSystemCode" placeholder="输入系统编号或名称搜索" clearable filterable size="default" style="width:220px" @change="handleSearch" :loading="systemOptsLoading">
          <el-option v-for="s in systemOpts" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-input v-model="fKeyword" clearable placeholder="搜索程序包名称" size="default" style="width:220px" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button size="default" :icon="Refresh" :disabled="loading" @click="fetchList">刷新</el-button>
          <el-button size="default" type="primary" :icon="Search" :disabled="loading" @click="handleSearch">查询</el-button>
          <el-button size="default" @click="resetFilters">重置</el-button>
          <el-button v-if="selectedIds.length" size="default" type="danger" plain :disabled="busy" @click="handleDelete()"><el-icon><Delete /></el-icon>移入回收站({{ selectedIds.length }})</el-button>
        </template>
      </UiToolbar>

      <UiDataTable :data="records" :loading="loading" row-key="id" @selection-change="(r: ProgramRecord[]) => selectedIds = r.map(x => x.id)">
        <el-table-column type="selection" width="48" />
        <el-table-column label="程序包编号" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ sd(row).asset_code ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="程序包名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)" style="padding:0">{{ row.asset_name ?? '—' }}</el-button></template>
        </el-table-column>
        <el-table-column label="程序类型" width="130"><template #default="{ row }">{{ typeLabel(row.program_type) }}</template></el-table-column>
        <el-table-column label="系统编号" width="200" show-overflow-tooltip><template #default="{ row }">{{ systemLabel(row) }}</template></el-table-column>
        <el-table-column label="程序包说明" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.program_description || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="downloadAll(row)"><el-icon><Download /></el-icon>打包下载</el-button>
            <el-button v-if="canEdit(row)" link type="primary" size="small" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
            <el-button v-if="canDelete(row)" link type="danger" size="small" @click="handleDelete([row.id])"><el-icon><Delete /></el-icon>删除</el-button>
          </template>
        </el-table-column>
      </UiDataTable>

      <div v-if="!loading && records.length === 0" style="margin:24px 0">
        <UiEmptyState text="暂无迁出/迁入程序包，请新增或调整筛选条件。" />
      </div>

      <div v-if="total > 0" class="program-pagination">
        <UiPagination :page="page" :page-size="size" :total="total" :page-sizes="[10,20,50,100]" @update:page-size="handleSizeChange" @update:page="handlePageChange" />
      </div>

      <UiFormDrawer v-model="drawer" :title="editing ? '编辑迁移程序' : '新增迁移程序'" :loading="saving" @submit="handleSave">
        <el-form label-position="top">
          <el-form-item label="程序类型" required>
            <el-select v-model="fv.programType" placeholder="请选择程序类型" style="width:100%">
              <el-option v-for="t in formTypeOpts" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="系统编号" required>
            <el-select v-model="fv.systemCode" placeholder="输入系统编号或名称搜索" filterable :loading="systemOptsLoading" style="width:100%">
              <el-option v-for="s in systemOpts" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="程序包名称" required>
            <el-input v-model="fv.programName" placeholder="请输入程序包名称" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="程序包说明">
            <el-input v-model="fv.programDescription" type="textarea" :rows="3" placeholder="程序功能、执行逻辑、适用场景说明" maxlength="2000" show-word-limit />
          </el-form-item>
          <el-form-item label="源文件" :required="!editing">
            <div class="attachment-list">
              <div v-for="(att, idx) in existingAttachments" :key="`e-${att.attachmentId}`" class="attachment-item">
                <div class="attachment-icon"><el-icon :size="18"><Document /></el-icon></div>
                <div class="attachment-info"><div class="attachment-name">{{ att.fileName }}</div></div>
                <el-button link type="danger" size="small" @click="removeExistingAtt(idx)">移除</el-button>
              </div>
              <div v-for="(f, idx) in pendingFiles" :key="`p-${idx}`" class="attachment-item">
                <div class="attachment-icon"><el-icon :size="18"><Document /></el-icon></div>
                <div class="attachment-info"><div class="attachment-name">{{ f.name }}</div></div>
                <el-button link type="danger" size="small" @click="removePendingFile(idx)">移除</el-button>
              </div>
            </div>
            <el-button size="small" :icon="Plus" @click="($refs.fileInput as HTMLInputElement).click()">选择多个文件</el-button>
            <input ref="fileInput" type="file" multiple style="display:none" @change="onFilesSelected" />
          </el-form-item>
        </el-form>
      </UiFormDrawer>

      <el-dialog v-model="detailDialogOpen" title="迁移程序详情" width="640px" :close-on-click-modal="false">
        <div v-loading="detailLoading" style="min-height:160px">
          <template v-if="detailData">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="程序包编号">{{ detailData.asset_code }}</el-descriptions-item>
              <el-descriptions-item label="程序类型">{{ typeLabel(detailData.program_type) }}</el-descriptions-item>
              <el-descriptions-item label="系统编号" :span="2">{{ systemLabel(detailData) }}</el-descriptions-item>
              <el-descriptions-item label="程序包名称" :span="2">{{ detailData.asset_name }}</el-descriptions-item>
              <el-descriptions-item label="程序包说明" :span="2">{{ detailData.program_description || '—' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ fmtDate(detailData.created_at) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ fmtDate(detailData.updated_at) }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="detailData.attachments?.length" style="margin-top:16px">
              <div style="font-weight:500;margin-bottom:8px">源文件</div>
              <div class="attachment-list">
                <div v-for="att in detailData.attachments" :key="att.attachmentId" class="attachment-item">
                  <div class="attachment-icon"><el-icon :size="20"><Document /></el-icon></div>
                  <div class="attachment-info"><div class="attachment-name">{{ att.fileName }}</div></div>
                  <el-button link type="primary" size="small" @click="downloadSingle(detailData!, att.attachmentId)"><el-icon><Download /></el-icon>下载</el-button>
                </div>
              </div>
            </div>
          </template>
        </div>
        <template #footer>
          <el-button @click="detailDialogOpen = false">关闭</el-button>
          <el-button v-if="detailData && canEdit(detailData)" type="primary" @click="detailDialogOpen = false; openEdit(detailData)">编辑</el-button>
        </template>
      </el-dialog>
    </template>
  </section>
</template>

<style scoped>
.attachment-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 8px; }
.attachment-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: var(--panel-bg, #f5f7fa); border: 1px solid var(--line, #e4e7ed); border-radius: 8px; }
.attachment-icon { display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; background: var(--brand-light, #ecf5ff); border-radius: 8px; color: var(--brand, #409eff); flex-shrink: 0; }
.attachment-info { flex: 1; min-width: 0; }
.attachment-name { font-size: 14px; font-weight: 500; color: var(--text, #303133); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
@media (max-width: 760px) {
  .dm-topic-toolbar :deep(.ui-toolbar__filters), .dm-topic-toolbar :deep(.ui-toolbar__actions) { width: 100%; flex-wrap: wrap; }
  .dm-topic-toolbar :deep(.el-input), .dm-topic-toolbar :deep(.el-select) { width: 100% !important; }
  .dm-topic-toolbar :deep(.ui-toolbar__filters > .el-button), .dm-topic-toolbar :deep(.ui-toolbar__actions > .el-button) { flex: 1; }
  .dm-page :deep(.ui-data-table) { max-width: 100%; overflow-x: auto; }
  .dm-page :deep(.el-card__body) { overflow-x: auto; }
  .dm-page :deep(.el-table) { width: 100% !important; }
  .program-pagination { max-width: 100%; overflow-x: auto; overflow-y: hidden; -webkit-overflow-scrolling: touch; padding-bottom: 2px; }
  .program-pagination :deep(.el-pagination) { flex-wrap: nowrap; }
}
</style>
