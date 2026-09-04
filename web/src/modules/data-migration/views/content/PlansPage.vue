<!--
  用途：数迁资产内容 - 迁移方案页（REQ-20260820-031 增量，对标会议纪要/汇报材料）
  说明：统一管理「业务迁移方案 / 数据迁移方案」两类资料，支持资产颗粒度 / 方案类型 /
        关联系统 / 方案名称关键字的多维筛选、分页展示、单条录入与批量上传（多文件归入同一方案）、
        编辑（重传/追加源文件）、下载、在线预览、逻辑删除。
        所属项目唯一取自全局项目上下文：页内不再有项目筛选、项目下拉与「所属项目」字段，列表/新增/编辑均固定使用当前项目，
        项目切换后重置分页与其他筛选条件重查。
        唯一约束：同一「项目 + 资产颗粒度 + 迁移方案类型 + 关联系统」仅允许一条活动记录（服务端强制）。
        文档级回收站收敛到统一页（数迁内容 › 回收站，PLAN 作为内容类型，经 PlanRecycleBinSource 分发）。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Delete, Document, Download, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFilePreview from '../../../../components/ui/UiFilePreview.vue'
import { useAuthStore } from '../../../../stores/auth'
import {
  listPlans, getPlan, createPlan, updatePlan, deletePlans,
  getPlanSystemOptions, getPlanAttachments,
  type PlanRecord, type PlanQuery, type PlanFormData, type PlanAttachment, type SelectOption
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'
import { uploadAttachment, getAttachmentDownload } from '../../../../api/attachments'
import { getFilePreviewCapabilities, uploadFilePreview, type FilePreviewCapabilities, type FilePreviewResult } from '../../../../api/file-preview'

const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const loading = ref(false), records = ref<PlanRecord[]>([]), total = ref(0), page = ref(1), size = ref(20), selectedIds = ref<number[]>([]), busy = ref(false)
const fGranularity = ref(''), fPlanType = ref(''), fSystem = ref(''), fKeyword = ref('')
const filterSysOpts = ref<SelectOption[]>([])
const GRAV: SelectOption[] = [{ value: 'PROJECT', label: '项目级' }, { value: 'SYSTEM', label: '系统级' }]
const PLAN_TYPES: SelectOption[] = [{ value: 'BUSINESS', label: '业务迁移方案' }, { value: 'DATA', label: '数据迁移方案' }]

const drawer = ref(false), saving = ref(false), editing = ref(false), editId = ref<number | null>(null)
interface PlanFormState {
  projectId: number | null
  granularity: string
  planType: string
  systemCode: string
  planName: string
  summary: string
}
const fv = ref<PlanFormState>({ projectId: null, granularity: '', planType: '', systemCode: '', planName: '', summary: '' })
const formSysOpts = ref<SelectOption[]>([])
// 附件：编辑时保留的已绑定文件 + 待上传的新文件
const existingAttachments = ref<{ attachmentId: number; fileName: string }[]>([])
const pendingFiles = ref<File[]>([])

// 查看详情
const detailDialogOpen = ref(false), detailLoading = ref(false), detailData = ref<PlanRecord | null>(null)
// 附件下载/预览选择
const attSelectDialogOpen = ref(false), attSelectLoading = ref(false)
const attSelectList = ref<PlanAttachment[]>([]), attSelectMode = ref<'download' | 'preview'>('download')

// 文件预览
const previewCapabilities = ref<FilePreviewCapabilities | null>(null)
const previewDialogOpen = ref(false), previewResult = ref<FilePreviewResult | null>(null)
const previewSubmitting = ref(false), previewError = ref('')

const canCreate = computed(() => auth.hasPermission('data-migration:content:plans:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdatePermission = computed(() => auth.hasPermission('data-migration:content:plans:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDeletePermission = computed(() => auth.hasPermission('data-migration:content:plans:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEdit = (item: PlanRecord) => hasUpdatePermission.value && (canManage.value || item.created_by === auth.user?.id)
const canDelete = (item: PlanRecord) => hasDeletePermission.value && (canManage.value || item.created_by === auth.user?.id)

const msg = (e: unknown) => e instanceof Error ? e.message : '操作失败'
const cancelled = (e: unknown): boolean => {
  if (e === 'cancel' || e === 'close') return true
  if (e instanceof Error && (e.message === 'cancel' || e.message === 'close')) return true
  if (typeof e === 'object' && e !== null && ((e as any).action === 'cancel' || (e as any).action === 'close')) return true
  return false
}
const sd = (i: PlanRecord): Record<string, any> => i as Record<string, any>
const fmtDate = (v?: string | null) => { if (!v) return '—'; const d = new Date(v); return isNaN(d.getTime()) ? String(v) : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}` }
const lbl = (o: SelectOption[], v?: string) => o.find(x => x.value === v)?.label ?? v ?? '—'
const stripExt = (name: string) => { const dot = name.lastIndexOf('.'); return dot > 0 ? name.substring(0, dot) : name }

async function loadFilterSystems() {
  const pid = scopeProjectId.value
  filterSysOpts.value = pid ? ((await getPlanSystemOptions(pid).catch(() => null))?.data.data ?? []) : []
}
async function loadList() {
  if (scopeProjectId.value == null) { records.value = []; total.value = 0; selectedIds.value = []; return }
  loading.value = true; selectedIds.value = []
  try {
    const p: PlanQuery = { page: page.value, size: size.value, projectId: scopeProjectId.value }
    if (fGranularity.value) p.granularity = fGranularity.value
    if (fPlanType.value) p.planType = fPlanType.value
    if (fSystem.value) p.systemCode = fSystem.value
    if (fKeyword.value.trim()) p.keyword = fKeyword.value.trim()
    const d = (await listPlans(p)).data.data
    records.value = d?.records ?? []; total.value = d?.total ?? 0
  } catch (e) { ElMessage.error(msg(e)); records.value = []; total.value = 0 }
  finally { loading.value = false }
}
function doSearch() { page.value = 1; loadList() }
function doReset() { fGranularity.value = ''; fPlanType.value = ''; fSystem.value = ''; fKeyword.value = ''; page.value = 1; loadList() }
async function loadFormSysOpts(pid: number | null) {
  formSysOpts.value = pid ? ((await getPlanSystemOptions(pid).catch(() => null))?.data.data ?? []) : []
}
function resetForm() {
  fv.value = { projectId: scopeProjectId.value, granularity: '', planType: '', systemCode: '', planName: '', summary: '' }
  existingAttachments.value = []; pendingFiles.value = []; formSysOpts.value = []
}
function openAdd() { editing.value = false; editId.value = null; resetForm(); void loadFormSysOpts(scopeProjectId.value); drawer.value = true }
async function openEdit(item: PlanRecord) {
  if (busy.value) return
  busy.value = true; drawer.value = false
  try {
    const detail = (await getPlan(item.id)).data.data
    if (!detail) throw new Error('未获取到迁移方案详情')
    await loadFormSysOpts(detail.project_id)
    existingAttachments.value = (detail.attachments ?? []).map(a => ({ attachmentId: a.attachment_id, fileName: a.file_name }))
    fv.value = {
      projectId: detail.project_id,
      granularity: sd(detail).granularity ?? '',
      planType: sd(detail).plan_type ?? '',
      systemCode: sd(detail).system_code ?? '',
      planName: sd(detail).asset_name ?? '',
      summary: sd(detail).plan_summary ?? ''
    }
    pendingFiles.value = []
    editing.value = true; editId.value = detail.id; drawer.value = true
  } catch (e) { resetForm(); ElMessage.error(`迁移方案详情加载失败：${msg(e)}`) }
  finally { busy.value = false }
}
async function openDetail(item: PlanRecord) {
  detailLoading.value = true; detailData.value = null; detailDialogOpen.value = true
  try { detailData.value = (await getPlan(item.id)).data.data ?? item }
  catch { detailData.value = item }
  finally { detailLoading.value = false }
}

function handleFilePick(file: File) {
  if (!file.name) return false
  pendingFiles.value.push(file)
  // 方案名称留空时以首个文件名（去扩展名）自动填充，实现批量文件名回填
  if (!fv.value.planName.trim()) fv.value.planName = stripExt(file.name)
  ElMessage.success(`已选择文件：${file.name}`)
  return false
}
function removeExistingAttachment(index: number) { existingAttachments.value.splice(index, 1) }
function removePendingFile(index: number) { pendingFiles.value.splice(index, 1) }

function validate(): string | null {
  if (!fv.value.projectId) return '当前项目不可用，请在顶部项目切换器中重新选择项目'
  if (!fv.value.granularity) return '请选择资产颗粒度'
  if (!fv.value.planType) return '请选择迁移方案类型'
  if (fv.value.granularity === 'SYSTEM' && !fv.value.systemCode) return '系统级方案必须选择关联系统'
  if (!fv.value.planName.trim()) return '请输入方案名称'
  const totalFiles = existingAttachments.value.length + pendingFiles.value.length;
  if (!editing.value && totalFiles === 0) return '请至少上传一个源文件'
  return null
}
async function doSave() {
  const err = validate(); if (err) { ElMessage.warning(err); return }
  saving.value = true
  try {
    const files: PlanFormData['files'] = []
    // 已绑定文件原样保留
    for (const att of existingAttachments.value) files.push({ attachmentId: att.attachmentId, fileName: att.fileName })
    // 待上传文件直接上传
    for (const file of pendingFiles.value) {
      const res = await uploadAttachment(file)
      const attachmentId = res.data.data?.id
      if (!attachmentId) throw new Error(`文件 ${file.name} 上传失败`)
      files.push({ attachmentId, fileName: file.name })
    }
    // T32 决策 D1/D2：新增时归属取当前项目；维护不传 projectId，归属恒取库中记录。
    const meta = {
      granularity: fv.value.granularity,
      planType: fv.value.planType,
      systemCode: fv.value.granularity === 'SYSTEM' ? (fv.value.systemCode || undefined) : undefined,
      planName: fv.value.planName.trim(),
      summary: fv.value.summary?.trim() || undefined
    }
    if (editing.value && editId.value) {
      // 编辑：无文件变动时不发送 files，服务端维持原附件
      if (files.length) await updatePlan(editId.value, { ...meta, files }); else await updatePlan(editId.value, meta)
      ElMessage.success('更新成功')
    } else {
      await createPlan({ projectId: fv.value.projectId as number, ...meta, files }); ElMessage.success('新增成功')
    }
    drawer.value = false; loadList()
  } catch (e) { ElMessage.error(msg(e)) }
  finally { saving.value = false }
}
async function doDelete() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认将选中的 ${selectedIds.value.length} 条迁移方案移入回收站？`, '移入回收站', { type: 'warning' })
    busy.value = true; await deletePlans(selectedIds.value); ElMessage.success('已移入回收站'); loadList()
  } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false }
}
async function doDeleteOne(item: PlanRecord) {
  try {
    await ElMessageBox.confirm(`确认将"${sd(item).asset_name ?? '该方案'}"移入回收站？`, '移入回收站', { type: 'warning' })
    busy.value = true; await deletePlans([item.id]); ElMessage.success('已移入回收站'); loadList()
  } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false }
}

async function doDownloadById(attachmentId: number) {
  try {
    const r = await getAttachmentDownload(attachmentId)
    const url = r.data.data?.downloadUrl
    if (url) window.open(url, '_blank'); else ElMessage.error('获取下载链接失败')
  } catch (e) { ElMessage.error(msg(e)) }
}
async function doDownload(item: PlanRecord) {
  if (!item.attachment_id) { ElMessage.warning('暂无源文件'); return }
  await doDownloadById(item.attachment_id)
}
async function handleAttachmentCommand(command: string, row: PlanRecord) {
  if (command === 'download-all') {
    try {
      const list = (await getPlanAttachments(row.id)).data.data ?? []
      for (const att of list) { await doDownloadById(att.attachment_id); await new Promise(r => setTimeout(r, 300)) }
    } catch (e) { ElMessage.error(msg(e)) }
  } else if (command === 'preview') {
    await openAttachmentSelect(row.id, 'preview')
  }
}
async function openAttachmentSelect(planId: number, mode: 'download' | 'preview') {
  attSelectMode.value = mode; attSelectDialogOpen.value = true; attSelectLoading.value = true
  try { attSelectList.value = (await getPlanAttachments(planId)).data.data ?? [] }
  catch (e) { ElMessage.error(msg(e)) }
  finally { attSelectLoading.value = false }
}
async function doAttachmentSelectAction(att: PlanAttachment) {
  if (attSelectMode.value === 'download') await doDownloadById(att.attachment_id)
  else await doPreviewById(att.attachment_id, att.file_name)
  attSelectDialogOpen.value = false
}

async function loadPreviewCapabilities() {
  previewError.value = ''
  try { previewCapabilities.value = (await getFilePreviewCapabilities()).data.data }
  catch { previewError.value = '文件预览配置加载失败' }
}
function canPreviewFile(fileName: string): boolean {
  if (!previewCapabilities.value?.enabled) return false
  const ext = fileName.includes('.') ? fileName.split('.').pop()?.toLowerCase() || '' : ''
  return previewCapabilities.value.allowedExtensions.includes(ext)
}
async function doPreview(item: PlanRecord) {
  if (!item.attachment_id) { ElMessage.warning('暂无源文件'); return }
  await doPreviewById(item.attachment_id, sd(item).asset_name ? `${sd(item).asset_name}` : '方案文件', item)
}
async function doPreviewById(attachmentId: number, fileName: string, fallback?: PlanRecord) {
  if (!previewCapabilities.value) await loadPreviewCapabilities()
  if (!previewCapabilities.value?.enabled) { ElMessage.warning('文件预览服务未启用'); return }
  if (!canPreviewFile(fileName)) { ElMessage.warning('该文件类型不支持在线预览'); return }
  previewSubmitting.value = true; previewError.value = ''
  try {
    const r = await getAttachmentDownload(attachmentId)
    const downloadUrl = r.data.data?.downloadUrl
    if (!downloadUrl) throw new Error('获取文件链接失败')
    void fallback
    const response = await fetch(downloadUrl)
    const blob = await response.blob()
    const file = new File([blob], fileName, { type: blob.type })
    previewResult.value = (await uploadFilePreview(file)).data.data
    previewDialogOpen.value = true
  } catch { previewError.value = '文件预览失败'; ElMessage.error('文件预览失败') }
  finally { previewSubmitting.value = false }
}

onMounted(async () => {
  await scope.ensureLoaded()
  await loadFilterSystems()
})

// 全局项目变化：丢弃上一个项目的列表、筛选与表单状态，按新项目重新加载。
watch(scopeProjectId, () => {
  records.value = []
  total.value = 0
  selectedIds.value = []
  page.value = 1
  fGranularity.value = ''
  fPlanType.value = ''
  fSystem.value = ''
  fKeyword.value = ''
  filterSysOpts.value = []
  drawer.value = false
  detailDialogOpen.value = false
  attSelectDialogOpen.value = false
  resetForm()
  void loadList()
  void loadFilterSystems()
}, { immediate: true })
</script>

<template>
  <section class="dm-page-root">
    <UiPageHeader title="迁移方案" description="列表与录入均固定属于顶部项目切换器选择的当前项目。">
      <template #actions>
        <el-button v-if="canCreate && scopeState === 'ready'" type="primary" :disabled="loading || busy" @click="openAdd"><el-icon><Plus /></el-icon>新增迁移方案</el-button>
      </template>
    </UiPageHeader>

    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <template v-else>
    <UiToolbar>
      <el-select v-model="fGranularity" placeholder="资产颗粒度" clearable style="width:120px" @change="doSearch">
        <el-option v-for="o in GRAV" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select v-model="fPlanType" placeholder="迁移方案类型" clearable style="width:150px" @change="doSearch">
        <el-option v-for="o in PLAN_TYPES" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select v-model="fSystem" placeholder="关联系统" clearable filterable style="width:170px" @change="doSearch">
        <el-option v-for="o in filterSysOpts" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-input v-model="fKeyword" clearable placeholder="搜索方案名称" style="width:200px" @keyup.enter="doSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <template #actions>
        <el-button :disabled="loading" @click="loadList"><el-icon><Refresh /></el-icon>刷新</el-button>
        <el-button type="primary" :disabled="loading" @click="doSearch"><el-icon><Search /></el-icon>查询</el-button>
        <el-button :disabled="loading" @click="doReset">重置</el-button>
        <el-button v-if="selectedIds.length" type="danger" plain :disabled="busy" @click="doDelete"><el-icon><Delete /></el-icon>移入回收站({{ selectedIds.length }})</el-button>
      </template>
    </UiToolbar>

    <UiDataTable :data="records" :loading="loading" row-key="id" @selection-change="(r: PlanRecord[]) => selectedIds = r.map(x => x.id)">
      <el-table-column type="selection" width="48" />
      <el-table-column label="方案编号" width="180" show-overflow-tooltip><template #default="{ row }">{{ sd(row).asset_code ?? '—' }}</template></el-table-column>
      <el-table-column label="资产颗粒度" width="100"><template #default="{ row }">{{ lbl(GRAV, sd(row).granularity) }}</template></el-table-column>
      <el-table-column label="迁移方案类型" width="130"><template #default="{ row }">{{ lbl(PLAN_TYPES, sd(row).plan_type) }}</template></el-table-column>
      <el-table-column label="方案名称" min-width="180" show-overflow-tooltip><template #default="{ row }">
        <el-button link type="primary" @click="openDetail(row)" style="padding:0">{{ sd(row).asset_name ?? '—' }}</el-button>
      </template></el-table-column>
      <el-table-column label="关联系统" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ sd(row).system_name ?? '—' }}</template></el-table-column>
      <el-table-column label="方案简介" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ sd(row).plan_summary ?? '—' }}</template></el-table-column>
      <el-table-column label="源文件" width="160"><template #default="{ row }">
        <template v-if="row.attachment_count > 0">
          <el-dropdown v-if="row.attachment_count > 1" trigger="click" @command="(cmd: string) => handleAttachmentCommand(cmd, row)">
            <el-button link type="primary" size="small"><el-icon><Document /></el-icon>{{ row.attachment_count }}个文件<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="download-all"><el-icon><Download /></el-icon>全部下载</el-dropdown-item>
                <el-dropdown-item command="preview"><el-icon><View /></el-icon>选择预览</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <template v-else>
            <el-button link type="primary" size="small" @click="doDownload(row)"><el-icon><Download /></el-icon>下载</el-button>
            <el-button link type="primary" size="small" :loading="previewSubmitting" @click="doPreview(row)"><el-icon><View /></el-icon>预览</el-button>
          </template>
        </template>
        <span v-else>—</span>
      </template></el-table-column>
      <el-table-column label="上传人" width="100"><template #default="{ row }">{{ sd(row).created_by_name ?? '—' }}</template></el-table-column>
      <el-table-column label="上传时间" width="150"><template #default="{ row }">{{ fmtDate(row.created_at) }}</template></el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :disabled="busy || !canEdit(row)" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
          <el-button link type="danger" size="small" :disabled="!canDelete(row)" @click="doDeleteOne(row)"><el-icon><Delete /></el-icon>删除</el-button>
        </template>
      </el-table-column>
    </UiDataTable>
    <UiEmptyState v-if="!loading && records.length === 0" description="当前项目下暂无迁移方案数据" icon="search" />
    <div v-if="total > 0" class="dm-table-footer">
      <span>共 {{ total }} 条</span>
      <UiPagination :page="page" :page-size="size" :total="total" :page-sizes="[10,20,50,100]" @update:page-size="(v: number) => { size = v; page = 1; loadList() }" @update:page="(v: number) => { page = v; loadList() }" />
    </div>
    </template>

    <!-- 新增/编辑抽屉 -->
    <UiFormDrawer v-model="drawer" :title="editing ? '编辑迁移方案' : '新增迁移方案'" :loading="saving" width="min(820px, calc(100vw - 24px))" @submit="doSave">
      <el-form label-position="top">
        <el-divider content-position="left">归属维度</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="资产颗粒度" required>
              <el-select v-model="fv.granularity" placeholder="选择颗粒度" style="width:100%">
                <el-option v-for="o in GRAV" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="迁移方案类型" required>
              <el-select v-model="fv.planType" placeholder="选择方案类型" style="width:100%">
                <el-option v-for="o in PLAN_TYPES" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="fv.granularity === 'SYSTEM'" label="关联系统" required>
              <el-select v-model="fv.systemCode" placeholder="选择关联系统" filterable style="width:100%">
                <el-option v-for="o in formSysOpts" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">方案信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="方案名称" required><el-input v-model="fv.planName" placeholder="单条录入手动填写；选择文件后自动以文件名填充" maxlength="190" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="方案简介"><el-input v-model="fv.summary" type="textarea" :rows="3" placeholder="简要描述方案能力与用途" maxlength="1000" show-word-limit /></el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">源文件</el-divider>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="上传源文件（支持单文件/多文件，多文件归入同一方案）" :required="!editing">
              <div v-if="existingAttachments.length" class="attachment-list">
                <div v-for="(att, i) in existingAttachments" :key="att.attachmentId" class="attachment-item">
                  <div class="attachment-icon"><el-icon :size="20"><Document /></el-icon></div>
                  <div class="attachment-info">
                    <div class="attachment-name" :title="att.fileName">{{ att.fileName }}</div>
                    <div class="attachment-meta">已绑定 · 附件 #{{ att.attachmentId }}</div>
                  </div>
                  <div class="attachment-actions">
                    <el-button link type="primary" size="small" @click="doDownloadById(att.attachmentId)"><el-icon><Download /></el-icon></el-button>
                    <el-button link type="danger" size="small" @click="removeExistingAttachment(i)"><el-icon><Delete /></el-icon></el-button>
                  </div>
                </div>
              </div>
              <div v-if="pendingFiles.length" class="attachment-list" style="margin-top:8px">
                <div v-for="(file, i) in pendingFiles" :key="i" class="attachment-item" style="border-style:dashed">
                  <div class="attachment-icon" style="background:#fdf6ec;color:#e6a23c"><el-icon :size="20"><Document /></el-icon></div>
                  <div class="attachment-info">
                    <div class="attachment-name" :title="file.name">{{ file.name }}</div>
                    <div class="attachment-meta">待上传 · {{ (file.size / 1024).toFixed(1) }} KB</div>
                  </div>
                  <div class="attachment-actions">
                    <el-button link type="danger" size="small" @click="removePendingFile(i)"><el-icon><Delete /></el-icon></el-button>
                  </div>
                </div>
              </div>
              <div v-if="!existingAttachments.length && !pendingFiles.length" class="attachment-empty">
                <el-icon :size="24"><Document /></el-icon><span>暂无源文件</span>
              </div>
              <div style="display:flex;align-items:center;gap:12px;margin-top:12px">
                <el-upload :auto-upload="false" :multiple="true" :on-change="(f: any) => handleFilePick(f.raw)" :show-file-list="false">
                  <el-button type="primary" plain><el-icon><Plus /></el-icon>选择文件</el-button>
                </el-upload>
              </div>
              <div style="color:#909399;font-size:12px;margin-top:4px">选择文件后点击保存才会上传；单文件即单条上传，多文件即批量归入同一方案。同一「项目+颗粒度+方案类型+关联系统」仅允许一条。</div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </UiFormDrawer>

    <!-- 方案详情弹窗 -->
    <el-dialog v-model="detailDialogOpen" title="迁移方案详情" width="720px" top="6vh">
      <div v-loading="detailLoading" style="min-height:180px">
        <template v-if="detailData">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="方案编号" :span="2">{{ sd(detailData).asset_code ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="资产颗粒度">{{ lbl(GRAV, sd(detailData).granularity) }}</el-descriptions-item>
            <el-descriptions-item label="迁移方案类型">{{ lbl(PLAN_TYPES, sd(detailData).plan_type) }}</el-descriptions-item>
            <el-descriptions-item label="关联系统">{{ sd(detailData).system_name ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="方案名称" :span="2">{{ sd(detailData).asset_name ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="方案简介" :span="2">
              <div style="white-space:pre-wrap">{{ sd(detailData).plan_summary ?? '—' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="上传人">{{ sd(detailData).created_by_name ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ fmtDate(detailData.created_at) }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="detailData.attachments && detailData.attachments.length" style="margin-top:16px">
            <div style="font-weight:500;margin-bottom:8px">源文件列表</div>
            <div class="attachment-list">
              <div v-for="att in detailData.attachments" :key="att.attachment_id" class="attachment-item">
                <div class="attachment-icon"><el-icon :size="20"><Document /></el-icon></div>
                <div class="attachment-info"><div class="attachment-name" :title="att.file_name">{{ att.file_name }}</div></div>
                <div class="attachment-actions">
                  <el-button link type="primary" size="small" @click="doDownloadById(att.attachment_id)"><el-icon><Download /></el-icon>下载</el-button>
                  <el-button link type="primary" size="small" @click="doPreviewById(att.attachment_id, att.file_name)"><el-icon><View /></el-icon>预览</el-button>
                </div>
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

    <!-- 附件选择弹窗 -->
    <el-dialog v-model="attSelectDialogOpen" :title="attSelectMode === 'download' ? '选择要下载的源文件' : '选择要预览的源文件'" width="480px">
      <div v-loading="attSelectLoading" style="min-height:100px">
        <div v-if="attSelectList.length === 0 && !attSelectLoading" style="text-align:center;color:#909399;padding:20px">暂无源文件</div>
        <div v-else class="attachment-select-list">
          <div v-for="att in attSelectList" :key="att.attachment_id" class="attachment-select-item" @click="doAttachmentSelectAction(att)">
            <el-icon :size="18" style="color:#409eff;margin-right:8px"><Document /></el-icon>
            <span class="attachment-select-name" :title="att.file_name">{{ att.file_name }}</span>
            <el-icon :size="14" style="color:#c0c4cc;margin-left:auto"><Download v-if="attSelectMode === 'download'" /><View v-else /></el-icon>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 文件预览弹窗 -->
    <UiFilePreview v-model="previewDialogOpen" :url="previewResult?.previewUrl || null" :file-name="previewResult?.fileName || '文件预览'" />
  </section>
</template>

<style scoped>
.attachment-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 8px; }
.attachment-item { display: flex; align-items: center; gap: 12px; padding: 12px 16px; background: var(--panel-bg, #f5f7fa); border: 1px solid var(--line, #e4e7ed); border-radius: 8px; transition: all 0.2s ease; }
.attachment-item:hover { border-color: var(--brand, #409eff); box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.attachment-icon { display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; background: var(--brand-light, #ecf5ff); border-radius: 8px; color: var(--brand, #409eff); flex-shrink: 0; }
.attachment-info { flex: 1; min-width: 0; }
.attachment-name { font-size: 14px; font-weight: 500; color: var(--text, #303133); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.attachment-meta { font-size: 12px; color: var(--muted, #909399); margin-top: 2px; }
.attachment-actions { display: flex; gap: 4px; flex-shrink: 0; }
.attachment-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 24px; background: var(--panel-bg, #f5f7fa); border: 1px dashed var(--line, #e4e7ed); border-radius: 8px; color: var(--muted, #909399); font-size: 14px; }
.attachment-select-list { display: flex; flex-direction: column; gap: 4px; }
.attachment-select-item { display: flex; align-items: center; padding: 12px 16px; border-radius: 6px; cursor: pointer; transition: background-color 0.2s; }
.attachment-select-item:hover { background-color: var(--brand-light, #ecf5ff); }
.attachment-select-name { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: 14px; color: var(--text, #303133); }
</style>
