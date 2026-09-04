<!--
  用途：数迁资产内容 - 会议纪要页
  说明：全流程记录与管理迁移过程各类会议纪要和问题提取纪要，集成多维度复杂条件检索、
        分页展示、详情页精细化编辑、单条新增与逻辑删除。
        会议纪要文档级回收站已收敛到统一页（数迁内容 › 回收站，MEETING 作为内容类型）。
        本页保留“附件回收站”：附件行受 uk_dm_meeting_att_active 与父会议未删前置校验约束，不兼容统一信封，因此不并入。
        所属项目唯一取自全局项目上下文：页内不再有项目筛选、项目下拉与「所属项目」字段，列表/附件回收站/新增/编辑
        均固定使用当前项目（编辑不传 projectId，归属由服务端取库中记录），项目切换后重置分页与其他筛选条件重查。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Delete, Document, Download, Edit, Plus, Refresh, Search, FolderOpened, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFilePreview from '../../../../components/ui/UiFilePreview.vue'
import { useAuthStore } from '../../../../stores/auth'
import {
  listMeetings, getMeeting, createMeeting, updateMeeting, deleteMeetings,
  getMeetingSystemOptions, getMeetingIssueOptions,
  getMeetingAttachments, deleteMeetingAttachment, getMeetingAttachmentRecycleBin, restoreMeetingAttachment,
  listAttachmentRecycleBin, restoreAttachments, purgeAttachments, purgeAllAttachments,
  type MeetingRecord, type MeetingQuery, type MeetingFormData, type MeetingUpdateData, type MeetingAttachment, type SelectOption
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'
import { uploadAttachment, getAttachmentDownload } from '../../../../api/attachments'
import { getFilePreviewCapabilities, uploadFilePreview, type FilePreviewCapabilities, type FilePreviewResult } from '../../../../api/file-preview'

const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId
const scopeProjectName = scope.projectName

const loading = ref(false), records = ref<MeetingRecord[]>([]), total = ref(0), page = ref(1), size = ref(20), selectedIds = ref<number[]>([]), busy = ref(false)
const fSource = ref(''), fGranularity = ref(''), fSystem = ref(''), fKeyword = ref('')
const filterSysOpts = ref<SelectOption[]>([])
const GRAV = [{ value: 'PROJECT', label: '项目级' }, { value: 'COMPONENT', label: '组件级' }, { value: 'TABLE', label: '表级' }, { value: 'FIELD', label: '字段级' }]
const SRC = [{ value: 'MEETING_MINUTES', label: '会议纪要' }, { value: 'ISSUE_EXTRACT', label: '问题提取' }]
const drawer = ref(false), saving = ref(false), editing = ref(false), editId = ref<number | null>(null)
const fv = ref<MeetingFormData & { keywords: string[]; systemCodes: string[]; issueIds: number[]; attachments: { attachmentId: number; fileName: string }[] }>({
  projectId: 0,
  granularity: '',
  meetingSource: '',
  meetingCode: '',
  meetingTitle: '',
  meetingContent: '',
  meetingConclusion: '',
  businessScenario: '',
  keywords: [],
  systemCodes: [],
  issueIds: [],
  attachmentId: undefined,
  fileName: '',
  attachments: []
})
const fSystemOpts = ref<SelectOption[]>([]), fIssueOpts = ref<SelectOption[]>([]), kwInput = ref('')
const tab = ref<'list' | 'recycle'>('list')
const attRecLoading = ref(false), attRecRecords = ref<(MeetingAttachment & { meeting_id: number; meeting_title: string })[]>([]), attRecTotal = ref(0), attRecPage = ref(1), attRecSize = ref(20), attRecSelected = ref<number[]>([]), attRecKeyword = ref('')
// 查看详情相关
const detailDialogOpen = ref(false)
const detailLoading = ref(false)
const detailData = ref<MeetingRecord | null>(null)

const canCreate = computed(() => auth.hasPermission('data-migration:content:meetings:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdatePermission = computed(() => auth.hasPermission('data-migration:content:meetings:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDeletePermission = computed(() => auth.hasPermission('data-migration:content:meetings:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEdit = (item: MeetingRecord) => hasUpdatePermission.value && (canManage.value || item.created_by === auth.user?.id)
const canDelete = (item: MeetingRecord) => hasDeletePermission.value && (canManage.value || item.created_by === auth.user?.id)
const msg = (e: unknown) => e instanceof Error ? e.message : '操作失败'
const cancelled = (e: unknown): boolean => { if (e === 'cancel' || e === 'close') return true; if (e instanceof Error && (e.message === 'cancel' || e.message === 'close')) return true; if (typeof e === 'object' && e !== null && ((e as any).action === 'cancel' || (e as any).action === 'close')) return true; return false }
const sd = (i: MeetingRecord): Record<string, any> => i as Record<string, any>
const fmtDate = (v?: string | null) => { if (!v) return '—'; const d = new Date(v); return isNaN(d.getTime()) ? String(v) : `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}` }
const lbl = (o: SelectOption[], v?: string) => o.find(x => x.value === v)?.label ?? v ?? '—'

async function loadFilterSystems() {
  const pid = scopeProjectId.value
  if (!pid) { filterSysOpts.value = []; return }
  filterSysOpts.value = ((await getMeetingSystemOptions(pid).catch(() => null))?.data.data ?? [])
}
async function loadList() {
  if (scopeProjectId.value == null) { records.value = []; total.value = 0; selectedIds.value = []; return }
  loading.value = true; selectedIds.value = []
  try {
    const p: MeetingQuery = { page: page.value, size: size.value, projectId: scopeProjectId.value }
    if (fSource.value) p.meetingSource = fSource.value
    if (fGranularity.value) p.granularity = fGranularity.value
    if (fSystem.value) p.systemCode = fSystem.value
    if (fKeyword.value.trim()) p.keyword = fKeyword.value.trim()
    const d = (await listMeetings(p)).data.data
    records.value = d?.records ?? []; total.value = d?.total ?? 0
  } catch (e) { ElMessage.error(msg(e)); records.value = []; total.value = 0 }
  finally { loading.value = false }
}
function doSearch() { page.value = 1; loadList() }
function doReset() { fSource.value = ''; fGranularity.value = ''; fSystem.value = ''; fKeyword.value = ''; page.value = 1; loadList() }
function switchTab(t: string) {
  // 会议纪要文档级回收站已收敛到统一页；此处 recycle tab 仅管理附件级回收站。
  if (t === 'recycle') loadAttRecycle()
  else loadList()
}
function resetForm() {
  fv.value = { projectId: scopeProjectId.value ?? 0, granularity: '', meetingSource: '', meetingCode: '', meetingTitle: '', meetingContent: '', meetingConclusion: '', businessScenario: '', keywords: [], systemCodes: [], issueIds: [], attachmentId: undefined, fileName: '', attachments: [] }
  kwInput.value = ''; fSystemOpts.value = []; fIssueOpts.value = []; editingAttachments.value = []; pendingFiles.value = []
}
function openAdd() { editing.value = false; editId.value = null; resetForm(); if (scopeProjectId.value) void loadFormOpts(scopeProjectId.value); drawer.value = true }
async function loadFormOpts(pid: number) {
  const [sysRes, issRes] = await Promise.all([getMeetingSystemOptions(pid), getMeetingIssueOptions(pid)])
  fSystemOpts.value = sysRes.data.data ?? []
  fIssueOpts.value = issRes.data.data ?? []
}
async function openEdit(item: MeetingRecord) {
  if (busy.value) return
  busy.value = true; drawer.value = false; editing.value = false; editId.value = null; resetForm()
  try {
    const detail = (await getMeeting(item.meeting_id)).data.data
    if (!detail) throw new Error('未获取到会议纪要详情')
    const d = sd(detail)
    const systemCodes = d.system_codes ? String(d.system_codes).split(',').map((v: string) => v.trim()).filter(Boolean) : []
    const issueIds = d.related_issue_ids ? String(d.related_issue_ids).split(',').map((v: string) => Number(v.trim())).filter(Boolean) : []
    // 解析 keywords：支持 JSON 数组格式 '["kw1","kw2"]' 和旧的逗号分隔格式
    let kw: string[] = []
    if (typeof d.keywords === 'string' && d.keywords.trim()) {
      try {
        const parsed = JSON.parse(d.keywords)
        kw = Array.isArray(parsed) ? parsed.map((v: any) => String(v).trim()).filter(Boolean) : []
      } catch {
        // 兼容旧的逗号分隔格式
        kw = d.keywords.split(',').map((v: string) => v.trim()).filter(Boolean)
      }
    } else if (Array.isArray(d.keywords)) {
      kw = [...d.keywords]
    }
    await loadFormOpts(detail.project_id)
    // 加载附件列表
    const attachments = (detail.attachments ?? []).map((a: any) => ({ attachmentId: a.attachment_id, fileName: a.file_name }))
    editingAttachments.value = [...attachments]
    fv.value = {
      projectId: detail.project_id,
      granularity: d.granularity ?? '',
      meetingSource: d.meeting_source ?? '',
      meetingCode: d.meeting_code ?? '',
      meetingTitle: d.meeting_title ?? '',
      meetingContent: d.meeting_content ?? '',
      meetingConclusion: d.meeting_conclusion ?? '',
      businessScenario: d.business_scenario ?? '',
      keywords: kw,
      systemCodes,
      issueIds,
      attachmentId: d.attachment_id ?? undefined,
      fileName: d.file_name ?? '',
      attachments: [...attachments]
    }
    editing.value = true; editId.value = detail.meeting_id; drawer.value = true
  } catch (e) { resetForm(); ElMessage.error(`会议纪要详情加载失败：${msg(e)}`) }
  finally { busy.value = false }
}
async function openDetail(item: MeetingRecord) {
  detailLoading.value = true; detailData.value = null; detailDialogOpen.value = true
  try {
    detailData.value = (await getMeeting(item.meeting_id)).data.data ?? item
  } catch {
    detailData.value = item
  } finally {
    detailLoading.value = false
  }
}
function addKw() { const v = kwInput.value.trim(); if (v && !fv.value.keywords.includes(v)) fv.value.keywords.push(v); kwInput.value = '' }
function rmKw(i: number) { fv.value.keywords.splice(i, 1) }
function validate(): string | null {
  if (!fv.value.projectId) return '当前项目不可用，请在顶部项目切换器中重新选择项目'
  if (!fv.value.granularity) return '请选择颗粒度'
  if (!fv.value.meetingSource) return '请选择会议纪要来源'
  if (!fv.value.meetingTitle.trim()) return '请输入会议主题'
  if (!fv.value.businessScenario?.trim()) return '请输入所属业务场景'
  return null
}
async function doSave() {
  const err = validate(); if (err) { ElMessage.warning(err); return }
  saving.value = true
  try {
    // 先上传暂存的文件
    const allAttachments = [...fv.value.attachments]
    for (const file of pendingFiles.value) {
      const res = await uploadAttachment(file)
      const attachmentId = res.data.data?.id
      if (!attachmentId) throw new Error(`文件 ${file.name} 上传失败`)
      allAttachments.push({ attachmentId, fileName: file.name })
    }
    // T32 决策 D1/D2：新增时归属取当前项目；维护不传 projectId，归属恒取库中记录。
    const body: MeetingUpdateData = {
      granularity: fv.value.granularity,
      meetingSource: fv.value.meetingSource,
      meetingCode: fv.value.meetingCode?.trim() || undefined,
      meetingTitle: fv.value.meetingTitle.trim(),
      meetingContent: fv.value.meetingContent?.trim() || undefined,
      meetingConclusion: fv.value.meetingConclusion?.trim() || undefined,
      businessScenario: fv.value.businessScenario?.trim() || undefined,
      keywords: fv.value.keywords.length ? fv.value.keywords : undefined,
      systemCodes: fv.value.systemCodes.length ? [...fv.value.systemCodes] : undefined,
      issueIds: [...fv.value.issueIds],
      attachmentId: allAttachments.length ? allAttachments[0].attachmentId : undefined,
      fileName: allAttachments.length ? allAttachments[0].fileName : undefined,
      attachments: allAttachments.length ? allAttachments : undefined
    }
    if (editing.value && editId.value) {
      await updateMeeting(editId.value, body); ElMessage.success('更新成功')
    } else {
      await createMeeting({ ...body, projectId: fv.value.projectId }); ElMessage.success('新增成功')
    }
    drawer.value = false; loadList()
  } catch (e) { ElMessage.error(msg(e)) }
  finally { saving.value = false }
}
async function doDelete() { if (!selectedIds.value.length) return; try { await ElMessageBox.confirm(`确认将选中的 ${selectedIds.value.length} 条会议纪要移入回收站？`, '移入回收站', { type: 'warning' }); busy.value = true; await deleteMeetings(selectedIds.value); ElMessage.success('已移入回收站'); loadList() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doDeleteOne(item: MeetingRecord) { try { await ElMessageBox.confirm(`确认将"${item.meeting_title}"移入回收站？`, '移入回收站', { type: 'warning' }); busy.value = true; await deleteMeetings([item.meeting_id]); ElMessage.success('已移入回收站'); loadList() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
// 会议纪要文档级回收站的恢复/彻底销毁/清空已收敛到统一页（MEETING 类型），本页不再提供局部入口。

// 附件回收站
async function loadAttRecycle() {
  if (scopeProjectId.value == null) { attRecRecords.value = []; attRecTotal.value = 0; attRecSelected.value = []; return }
  attRecLoading.value = true; attRecSelected.value = []
  try {
    const p: { projectId: number; keyword?: string; page?: number; size?: number } = { page: attRecPage.value, size: attRecSize.value, projectId: scopeProjectId.value }
    if (attRecKeyword.value.trim()) p.keyword = attRecKeyword.value.trim()
    const d = (await listAttachmentRecycleBin(p)).data.data
    attRecRecords.value = d?.records ?? []; attRecTotal.value = d?.total ?? 0
  } catch (e) { ElMessage.error(msg(e)); attRecRecords.value = []; attRecTotal.value = 0 }
  finally { attRecLoading.value = false }
}
async function doRestoreAttBatch() { if (!attRecSelected.value.length) return; try { await ElMessageBox.confirm(`确认恢复选中的 ${attRecSelected.value.length} 个附件？`, '恢复附件'); busy.value = true; await restoreAttachments(attRecSelected.value); ElMessage.success('恢复成功'); loadAttRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doRestoreAttOne(item: MeetingAttachment & { meeting_id: number; meeting_title: string }) { try { await ElMessageBox.confirm(`确认恢复"${item.file_name}"？`, '恢复附件'); busy.value = true; await restoreAttachments([item.id]); ElMessage.success('恢复成功'); loadAttRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doPurgeAttBatch() { if (!attRecSelected.value.length) return; try { await ElMessageBox.confirm(`确认彻底销毁选中的 ${attRecSelected.value.length} 个附件？此操作不可恢复。`, '彻底销毁', { type: 'error', confirmButtonText: '彻底销毁' }); busy.value = true; await purgeAttachments(attRecSelected.value); ElMessage.success('清理完成'); loadAttRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doPurgeAttOne(item: MeetingAttachment & { meeting_id: number; meeting_title: string }) { try { await ElMessageBox.confirm(`确认彻底销毁"${item.file_name}"？此操作不可恢复。`, '彻底销毁', { type: 'error', confirmButtonText: '彻底销毁' }); busy.value = true; await purgeAttachments([item.id]); ElMessage.success('清理完成'); loadAttRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doPurgeAllAtt() { const pid = scopeProjectId.value; if (!pid) { ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目'); return }; try { await ElMessageBox.confirm(`确认彻底销毁当前项目（${scopeProjectName.value || pid}）附件回收站内的全部附件？其他项目不受影响，此操作不可恢复。`, '清空当前项目附件回收站', { type: 'error', confirmButtonText: '清空' }); busy.value = true; await purgeAllAttachments(pid); ElMessage.success('当前项目附件回收站已清空'); loadAttRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }

// 文件上传相关：暂存待上传文件，保存时才真正上传
const uploadFile = ref<File | null>(null)
const editingAttachments = ref<{ attachmentId: number; fileName: string }[]>([])
const pendingFiles = ref<File[]>([])

function handleFileUpload(file: File) {
  pendingFiles.value.push(file)
  ElMessage.success(`已选择文件：${file.name}`)
  return false
}

function removeExistingAttachment(index: number) {
  fv.value.attachments.splice(index, 1)
}

function removePendingFile(index: number) {
  pendingFiles.value.splice(index, 1)
}


async function doDownload(item: MeetingRecord) {
  if (!item.attachment_id) { ElMessage.warning('暂无附件'); return }
  try {
    const r = await getAttachmentDownload(item.attachment_id)
    const url = r.data.data?.downloadUrl
    if (url) window.open(url, '_blank')
    else ElMessage.error('获取下载链接失败')
  } catch (e) { ElMessage.error(msg(e)) }
}

async function doDownloadById(attachmentId: number) {
  try {
    const r = await getAttachmentDownload(attachmentId)
    const url = r.data.data?.downloadUrl
    if (url) window.open(url, '_blank')
    else ElMessage.error('获取下载链接失败')
  } catch (e) { ElMessage.error(msg(e)) }
}

// 多附件选择相关
const attSelectDialogOpen = ref(false)
const attSelectLoading = ref(false)
const attSelectList = ref<MeetingAttachment[]>([])
const attSelectMode = ref<'download' | 'preview'>('download')
const attSelectMeetingId = ref<number | null>(null)

async function handleAttachmentCommand(command: string, row: MeetingRecord) {
  if (command === 'download-all') {
    await downloadAllAttachments(row.meeting_id)
  } else if (command === 'preview') {
    await openAttachmentSelect(row.meeting_id, 'preview')
  }
}

async function downloadAllAttachments(meetingId: number) {
  try {
    const r = await getMeetingAttachments(meetingId)
    const attachments = r.data.data ?? []
    for (const att of attachments) {
      await doDownloadById(att.attachment_id)
      // 小延迟避免浏览器阻止多个下载
      await new Promise(resolve => setTimeout(resolve, 300))
    }
  } catch (e) { ElMessage.error(msg(e)) }
}

async function openAttachmentSelect(meetingId: number, mode: 'download' | 'preview') {
  attSelectMeetingId.value = meetingId
  attSelectMode.value = mode
  attSelectDialogOpen.value = true
  attSelectLoading.value = true
  try {
    const r = await getMeetingAttachments(meetingId)
    attSelectList.value = r.data.data ?? []
  } catch (e) { ElMessage.error(msg(e)) }
  finally { attSelectLoading.value = false }
}

async function doAttachmentSelectAction(attachment: MeetingAttachment) {
  if (attSelectMode.value === 'download') {
    await doDownloadById(attachment.attachment_id)
  } else {
    await doPreviewById(attachment.attachment_id, attachment.file_name)
  }
  attSelectDialogOpen.value = false
}

// 文件预览相关
const previewCapabilities = ref<FilePreviewCapabilities | null>(null)
const previewCapabilitiesLoading = ref(false)
const previewDialogOpen = ref(false)
const previewResult = ref<FilePreviewResult | null>(null)
const previewSubmitting = ref(false)
const previewError = ref('')

async function loadPreviewCapabilities() {
  previewCapabilitiesLoading.value = true
  previewError.value = ''
  try {
    previewCapabilities.value = (await getFilePreviewCapabilities()).data.data
  } catch (error) {
    previewError.value = '文件预览配置加载失败'
  } finally {
    previewCapabilitiesLoading.value = false
  }
}

function canPreviewFile(fileName: string): boolean {
  if (!previewCapabilities.value?.enabled) return false
  const extension = fileName.includes('.') ? fileName.split('.').pop()?.toLowerCase() || '' : ''
  return previewCapabilities.value.allowedExtensions.includes(extension)
}

async function doPreview(item: MeetingRecord) {
  if (!item.attachment_id) { ElMessage.warning('暂无附件'); return }

  // 先加载预览能力配置
  if (!previewCapabilities.value) {
    await loadPreviewCapabilities()
  }

  if (!previewCapabilities.value?.enabled) {
    ElMessage.warning('文件预览服务未启用')
    return
  }

  // 检查文件类型是否支持预览
  const fileName = item.file_name || ''
  if (!canPreviewFile(fileName)) {
    ElMessage.warning('该文件类型不支持在线预览')
    return
  }

  await doPreviewById(item.attachment_id, fileName)
}

async function doPreviewById(attachmentId: number, fileName: string) {
  // 先加载预览能力配置
  if (!previewCapabilities.value) {
    await loadPreviewCapabilities()
  }

  if (!previewCapabilities.value?.enabled) {
    ElMessage.warning('文件预览服务未启用')
    return
  }

  // 检查文件类型是否支持预览
  if (!canPreviewFile(fileName)) {
    ElMessage.warning('该文件类型不支持在线预览')
    return
  }

  previewSubmitting.value = true
  previewError.value = ''
  try {
    // 获取下载链接并上传到预览服务
    const r = await getAttachmentDownload(attachmentId)
    const downloadUrl = r.data.data?.downloadUrl
    if (!downloadUrl) throw new Error('获取文件链接失败')

    // 通过下载链接获取文件
    const response = await fetch(downloadUrl)
    const blob = await response.blob()
    const file = new File([blob], fileName, { type: blob.type })

    // 上传到预览服务
    previewResult.value = (await uploadFilePreview(file)).data.data
    previewDialogOpen.value = true
  } catch (error) {
    previewError.value = '文件预览失败'
    ElMessage.error('文件预览失败')
  } finally {
    previewSubmitting.value = false
  }
}

watch(scopeProjectId, () => {
  // 项目切换：清空上一项目的列表、附件回收站、筛选条件与弹层，避免残留
  records.value = []; total.value = 0; selectedIds.value = []
  attRecRecords.value = []; attRecTotal.value = 0; attRecSelected.value = []
  fSource.value = ''; fGranularity.value = ''; fSystem.value = ''; fKeyword.value = ''; attRecKeyword.value = ''
  page.value = 1; attRecPage.value = 1
  detailDialogOpen.value = false; detailData.value = null
  drawer.value = false; editing.value = false; editId.value = null; resetForm()
  void loadFilterSystems()
  switchTab(tab.value)
}, { immediate: true })

onMounted(() => { void scope.ensureLoaded() })
</script>

<template>
  <section class="dm-page-root">
    <UiPageHeader title="会议纪要" description="列表、附件回收站与录入均固定属于顶部项目切换器选择的当前项目。">
      <template #actions>
        <el-button v-if="canCreate && scopeState === 'ready'" type="primary" :disabled="loading || busy" @click="openAdd"><el-icon><Plus /></el-icon>新增会议纪要</el-button>
      </template>
    </UiPageHeader>
    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <template v-else>
    <el-tabs v-model="tab" @tab-click="(t: any) => switchTab(t.paneName)">
      <el-tab-pane label="会议纪要" name="list">
        <UiToolbar>
          <el-select v-model="fSource" placeholder="会议纪要来源" clearable style="width:140px" @change="doSearch">
            <el-option v-for="o in SRC" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="fGranularity" placeholder="颗粒度" clearable style="width:110px" @change="doSearch">
            <el-option v-for="o in GRAV" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="fSystem" placeholder="关联系统" clearable filterable style="width:160px" @change="doSearch">
            <el-option v-for="o in filterSysOpts" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input v-model="fKeyword" clearable placeholder="搜索主题/系统/关键字" style="width:200px" @keyup.enter="doSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <template #actions>
            <el-button :disabled="loading" @click="loadList"><el-icon><Refresh /></el-icon>刷新</el-button>
            <el-button type="primary" :disabled="loading" @click="doSearch"><el-icon><Search /></el-icon>查询</el-button>
            <el-button :disabled="loading" @click="doReset">重置</el-button>
            <el-button v-if="selectedIds.length" type="danger" plain :disabled="busy" @click="doDelete"><el-icon><Delete /></el-icon>移入回收站({{ selectedIds.length }})</el-button>
          </template>
        </UiToolbar>
        <UiDataTable :data="records" :loading="loading" row-key="meeting_id" @selection-change="(r: MeetingRecord[]) => selectedIds = r.map(x => x.meeting_id)">
          <el-table-column type="selection" width="48" />
          <el-table-column label="编号" width="150" show-overflow-tooltip><template #default="{ row }">{{ sd(row).meeting_code ?? '—' }}</template></el-table-column>
          <el-table-column label="颗粒度" width="90"><template #default="{ row }">{{ lbl(GRAV, sd(row).granularity) }}</template></el-table-column>
          <el-table-column label="会议纪要来源" width="120"><template #default="{ row }">{{ lbl(SRC, sd(row).meeting_source) }}</template></el-table-column>
          <el-table-column label="会议主题" min-width="180" show-overflow-tooltip><template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)" style="padding:0">{{ sd(row).meeting_title ?? '—' }}</el-button>
          </template></el-table-column>
          <el-table-column label="会议内容" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ sd(row).meeting_content ?? '—' }}</template></el-table-column>
          <el-table-column label="会议结论" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ sd(row).meeting_conclusion ?? '—' }}</template></el-table-column>
          <el-table-column label="所属业务场景" width="130" show-overflow-tooltip><template #default="{ row }">{{ sd(row).business_scenario ?? '—' }}</template></el-table-column>
          <el-table-column label="关联系统" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ sd(row).system_names ?? '—' }}</template></el-table-column>
          <el-table-column label="关键字" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ sd(row).keywords ?? '—' }}</template></el-table-column>
          <el-table-column label="关联问题" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ sd(row).related_issue_names ?? '—' }}</template></el-table-column>
          <el-table-column label="附件" width="160"><template #default="{ row }">
            <template v-if="row.attachment_count > 0">
              <el-dropdown v-if="row.attachment_count > 1" trigger="click" @command="(cmd: string) => handleAttachmentCommand(cmd, row)">
                <el-button link type="primary" size="small"><el-icon><Document /></el-icon>{{ row.attachment_count }}个附件<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
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
          <el-table-column label="上传人" width="100"><template #default="{ row }">{{ row.created_by_name ?? '—' }}</template></el-table-column>
          <el-table-column label="上传时间" width="150"><template #default="{ row }">{{ fmtDate(row.created_at) }}</template></el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" :disabled="busy" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-button link type="danger" size="small" :disabled="!canDelete(row)" @click="doDeleteOne(row)"><el-icon><Delete /></el-icon>删除</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiEmptyState v-if="!loading && records.length === 0" description="当前项目下暂无会议纪要数据" icon="search" />
        <UiPagination v-if="total > 0" :page="page" :page-size="size" :total="total" :page-sizes="[10,20,50,100]" @update:page-size="(v: number) => { size = v; page = 1; loadList() }" @update:page="(v: number) => { page = v; loadList() }" />
      </el-tab-pane>
      <!-- 附件级回收站：受 uk_dm_meeting_att_active 与父会议未删前置校验约束，不并入统一回收站。 -->
      <el-tab-pane label="附件回收站" name="recycle">
        <UiToolbar>
          <el-input v-model="attRecKeyword" clearable placeholder="搜索文件名或会议主题" style="width:240px" @keyup.enter="loadAttRecycle"><template #prefix><el-icon><Search /></el-icon></template></el-input>
          <template #actions>
            <el-button :disabled="attRecLoading" @click="loadAttRecycle"><el-icon><Refresh /></el-icon>刷新</el-button>
            <el-button v-if="attRecSelected.length" type="warning" plain :disabled="busy" @click="doRestoreAttBatch"><el-icon><FolderOpened /></el-icon>恢复({{ attRecSelected.length }})</el-button>
            <el-button v-if="attRecSelected.length" type="danger" plain :disabled="busy" @click="doPurgeAttBatch"><el-icon><Delete /></el-icon>彻底销毁({{ attRecSelected.length }})</el-button>
            <el-button v-if="attRecTotal > 0" type="danger" :disabled="busy" @click="doPurgeAllAtt">清空本项目附件回收站</el-button>
          </template>
        </UiToolbar>
        <UiDataTable :data="attRecRecords" :loading="attRecLoading" row-key="id" @selection-change="(r: any[]) => attRecSelected = r.map(x => x.id)">
          <el-table-column type="selection" width="48" />
          <el-table-column label="文件名" prop="file_name" min-width="180" show-overflow-tooltip />
          <el-table-column label="所属会议" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ row.meeting_title ?? '—' }}</template></el-table-column>
          <el-table-column label="删除时间" width="150"><template #default="{ row }">{{ fmtDate(row.deleted_at) }}</template></el-table-column>
          <el-table-column label="删除人" width="100"><template #default="{ row }">{{ row.deleted_by_name ?? '—' }}</template></el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="warning" size="small" :disabled="busy" @click="doRestoreAttOne(row)"><el-icon><FolderOpened /></el-icon>恢复</el-button>
              <el-button link type="danger" size="small" :disabled="busy" @click="doPurgeAttOne(row)"><el-icon><Delete /></el-icon>销毁</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiEmptyState v-if="!attRecLoading && attRecRecords.length === 0" description="当前项目附件回收站为空" icon="delete" />
        <UiPagination v-if="attRecTotal > 0" :page="attRecPage" :page-size="attRecSize" :total="attRecTotal" :page-sizes="[10,20,50,100]" @update:page-size="(v: number) => { attRecSize = v; attRecPage = 1; loadAttRecycle() }" @update:page="(v: number) => { attRecPage = v; loadAttRecycle() }" />
      </el-tab-pane>
    </el-tabs>
    </template>

    <!-- 新增/编辑抽屉 -->
    <UiFormDrawer v-model="drawer" :title="editing ? '编辑会议纪要' : '新增会议纪要'" :loading="saving" width="min(900px, calc(100vw - 24px))" @submit="doSave">
      <el-form label-position="top">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="颗粒度" required>
              <el-select v-model="fv.granularity" placeholder="选择颗粒度" style="width:100%">
                <el-option v-for="o in GRAV" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="会议纪要来源" required>
              <el-select v-model="fv.meetingSource" placeholder="选择来源" style="width:100%">
                <el-option v-for="o in SRC" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属业务场景" required><el-input v-model="fv.businessScenario" placeholder="请输入业务场景" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="editing ? '会议编号' : '会议编号（可选）'">
              <el-input v-model="fv.meetingCode" :placeholder="editing ? '可调整当前编号' : '留空自动生成 MEET-{id}'" maxlength="96" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="会议主题" required><el-input v-model="fv.meetingTitle" placeholder="请输入会议主题" /></el-form-item>
          </el-col>
        </el-row>

        <!-- 详细内容 -->
        <el-divider content-position="left">详细内容</el-divider>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="会议内容"><el-input v-model="fv.meetingContent" type="textarea" :rows="4" placeholder="请输入会议核心内容" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="会议结论"><el-input v-model="fv.meetingConclusion" type="textarea" :rows="3" placeholder="请输入会议总结结论" /></el-form-item>
          </el-col>
        </el-row>

        <!-- 关联信息 -->
        <el-divider content-position="left">关联信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="关联系统">
              <el-select v-model="fv.systemCodes" placeholder="选择关联系统" multiple filterable style="width:100%">
                <el-option v-for="o in fSystemOpts" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关联问题">
              <el-select v-model="fv.issueIds" placeholder="选择关联问题" multiple filterable style="width:100%">
                <el-option v-for="o in fIssueOpts" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="关键字索引">
              <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px">
                <el-tag v-for="(kw, i) in fv.keywords" :key="i" closable @close="rmKw(i)">{{ kw }}</el-tag>
              </div>
              <el-input v-model="kwInput" placeholder="输入关键字后回车添加" @keydown="(e: KeyboardEvent) => { if (e.key === 'Enter') { e.preventDefault(); addKw() } }">
                <template #append><el-button @click="addKw">添加</el-button></template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 附件上传 -->
        <el-divider content-position="left">附件</el-divider>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="附件文件">
              <!-- 已有附件列表 -->
              <div v-if="fv.attachments.length" class="attachment-list">
                <div
                  v-for="(att, i) in fv.attachments"
                  :key="att.attachmentId"
                  class="attachment-item"
                >
                  <div class="attachment-icon">
                    <el-icon :size="20"><Document /></el-icon>
                  </div>
                  <div class="attachment-info">
                    <div class="attachment-name" :title="att.fileName">{{ att.fileName }}</div>
                    <div class="attachment-meta">附件 #{{ att.attachmentId }}</div>
                  </div>
                  <div class="attachment-actions">
                    <el-button
                      link
                      type="primary"
                      size="small"
                      @click="doDownloadById(att.attachmentId)"
                    >
                      <el-icon><Download /></el-icon>
                    </el-button>
                    <el-button
                      link
                      type="danger"
                      size="small"
                      @click="removeExistingAttachment(i)"
                    >
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>
              <!-- 待上传文件列表 -->
              <div v-if="pendingFiles.length" class="attachment-list" style="margin-top:8px">
                <div
                  v-for="(file, i) in pendingFiles"
                  :key="i"
                  class="attachment-item"
                  style="border-style:dashed"
                >
                  <div class="attachment-icon" style="background:#fdf6ec;color:#e6a23c">
                    <el-icon :size="20"><Document /></el-icon>
                  </div>
                  <div class="attachment-info">
                    <div class="attachment-name" :title="file.name">{{ file.name }}</div>
                    <div class="attachment-meta">待上传 · {{ (file.size / 1024).toFixed(1) }} KB</div>
                  </div>
                  <div class="attachment-actions">
                    <el-button
                      link
                      type="danger"
                      size="small"
                      @click="removePendingFile(i)"
                    >
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>
              <div v-if="!fv.attachments.length && !pendingFiles.length" class="attachment-empty">
                <el-icon :size="24"><FolderOpened /></el-icon>
                <span>暂无附件</span>
              </div>
              <div style="display:flex;align-items:center;gap:12px;margin-top:12px">
                <el-upload :auto-upload="false" :on-change="(f: any) => handleFileUpload(f.raw)" :show-file-list="false">
                  <el-button type="primary" plain><el-icon><Plus /></el-icon>选择文件</el-button>
                </el-upload>
              </div>
              <div style="color:#909399;font-size:12px;margin-top:4px">选择文件后点击保存才会上传，取消不会产生冗余文件</div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </UiFormDrawer>

    <!-- 文件预览弹窗 -->
    <UiFilePreview v-model="previewDialogOpen" :url="previewResult?.previewUrl || null" :file-name="previewResult?.fileName || '文件预览'" />

    <!-- 附件选择弹窗 -->
    <el-dialog v-model="attSelectDialogOpen" :title="attSelectMode === 'download' ? '选择要下载的附件' : '选择要预览的附件'" width="480px">
      <div v-loading="attSelectLoading" style="min-height:100px">
        <div v-if="attSelectList.length === 0 && !attSelectLoading" style="text-align:center;color:#909399;padding:20px">暂无附件</div>
        <div v-else class="attachment-select-list">
          <div
            v-for="att in attSelectList"
            :key="att.attachment_id"
            class="attachment-select-item"
            @click="doAttachmentSelectAction(att)"
          >
            <el-icon :size="18" style="color:#409eff;margin-right:8px"><Document /></el-icon>
            <span class="attachment-select-name" :title="att.file_name">{{ att.file_name }}</span>
            <el-icon :size="14" style="color:#c0c4cc;margin-left:auto"><Download v-if="attSelectMode === 'download'" /><View v-else /></el-icon>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 会议详情弹窗 -->
    <el-dialog v-model="detailDialogOpen" title="会议纪要详情" width="720px" top="6vh">
      <div v-loading="detailLoading" style="min-height:200px">
        <template v-if="detailData">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="会议编号" :span="2">{{ sd(detailData).meeting_code ?? detailData.meeting_code ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="颗粒度">{{ lbl(GRAV, sd(detailData).granularity) }}</el-descriptions-item>
            <el-descriptions-item label="会议纪要来源">{{ lbl(SRC, sd(detailData).meeting_source) }}</el-descriptions-item>
            <el-descriptions-item label="会议主题" :span="2">{{ sd(detailData).meeting_title ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="会议内容" :span="2">
              <div style="white-space:pre-wrap;max-height:200px;overflow-y:auto">{{ sd(detailData).meeting_content ?? '—' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="会议结论" :span="2">
              <div style="white-space:pre-wrap;max-height:150px;overflow-y:auto">{{ sd(detailData).meeting_conclusion ?? '—' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="所属业务场景" :span="2">{{ sd(detailData).business_scenario ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="关联系统">{{ sd(detailData).system_names ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="关联问题">{{ sd(detailData).related_issue_names ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="关键字" :span="2">{{ sd(detailData).keywords ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="上传人">{{ detailData.created_by_name ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ fmtDate(detailData.created_at) }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="detailData.attachments && detailData.attachments.length > 0" style="margin-top:16px">
            <div style="font-weight:500;margin-bottom:8px">附件列表</div>
            <div class="attachment-list">
              <div v-for="att in detailData.attachments" :key="att.attachment_id" class="attachment-item">
                <div class="attachment-icon"><el-icon :size="20"><Document /></el-icon></div>
                <div class="attachment-info">
                  <div class="attachment-name" :title="att.file_name">{{ att.file_name }}</div>
                </div>
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
  </section>
</template>

<style scoped>
.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--panel-bg, #f5f7fa);
  border: 1px solid var(--line, #e4e7ed);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.attachment-item:hover {
  border-color: var(--brand, #409eff);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.attachment-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  background: var(--brand-light, #ecf5ff);
  border-radius: 8px;
  color: var(--brand, #409eff);
  flex-shrink: 0;
}

.attachment-info {
  flex: 1;
  min-width: 0;
}

.attachment-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text, #303133);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attachment-meta {
  font-size: 12px;
  color: var(--muted, #909399);
  margin-top: 2px;
}

.attachment-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.attachment-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  background: var(--panel-bg, #f5f7fa);
  border: 1px dashed var(--line, #e4e7ed);
  border-radius: 8px;
  color: var(--muted, #909399);
  font-size: 14px;
}

.attachment-select-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.attachment-select-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.attachment-select-item:hover {
  background-color: var(--brand-light, #ecf5ff);
}

.attachment-select-name {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  color: var(--text, #303133);
}
</style>
