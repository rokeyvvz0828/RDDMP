<!--
  用途：数迁资产内容 - 专题材料页（REQ-20260820-031 增量，对标迁移方案/会议纪要）
  说明：统一管理专题材料，支持颗粒度（项目级/系统级）/ 专题类型（参数管理）/
        关联系统（多选）/ 专题名称/简述关键字的多维筛选、分页展示、单条录入与多文件上传、
        编辑（重传/追加源文件）、下载、在线预览、逻辑删除。
        所属项目唯一取自全局项目上下文：页内不再有项目筛选、项目下拉与「所属项目」字段，
        列表/新增/编辑均固定使用当前项目，项目切换后重置分页与其他筛选条件重查。
        文档级回收站收敛到统一页（数迁内容 › 回收站，TOPIC 作为内容类型，经 TopicRecycleBinSource 分发）。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, Download, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFilePreview from '../../../../components/ui/UiFilePreview.vue'
import { useAuthStore } from '../../../../stores/auth'
import {
  listTopics, getTopic, createTopic, updateTopic, deleteTopics,
  getTopicTypeOptions, getSystemOptions, getTopicDownloadPath, getTopicAttachments,
  getDataMigrationParamOptions, DM_CODE_CATEGORIES,
  type TopicRecord, type TopicQuery, type TopicFormData, type TopicUpdateData, type SelectOption
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'
import { uploadAttachment, getAttachmentDownload } from '../../../../api/attachments'
import { getFilePreviewCapabilities, uploadFilePreview, type FilePreviewCapabilities, type FilePreviewResult } from '../../../../api/file-preview'

const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const loading = ref(false), records = ref<TopicRecord[]>([]), total = ref(0), page = ref(1), size = ref(20), selectedIds = ref<number[]>([]), busy = ref(false)
const fGranularity = ref(''), fTopicType = ref(''), fSystem = ref(''), fKeyword = ref('')
// 涉及系统：本项目数迁系统数量小（≤150），一次性全量加载，下拉内本地随输随筛（编号/名称均可、不区分大小写）
const systemOpts = ref<SelectOption[]>([])
const systemOptsLoading = ref(false)
const filterTypeOpts = ref<SelectOption[]>([])
const GRAV = ref<SelectOption[]>([])

const drawer = ref(false), saving = ref(false), editing = ref(false), editId = ref<number | null>(null)
interface TopicFormState {
  projectId: number | null
  granularity: string
  topicTypeCode: string
  topicName: string
  topicSummary: string
  systemCodes: string[]
}
const fv = ref<TopicFormState>({ projectId: null, granularity: '', topicTypeCode: '', topicName: '', topicSummary: '', systemCodes: [] })
const formTypeOpts = ref<SelectOption[]>([])
const existingAttachments = ref<{ attachmentId: number; fileName: string }[]>([])
const pendingFiles = ref<File[]>([])

// 查看详情
const detailDialogOpen = ref(false), detailLoading = ref(false), detailData = ref<TopicRecord | null>(null)

// 文件预览
const previewCapabilities = ref<FilePreviewCapabilities | null>(null)
const previewDialogOpen = ref(false), previewResult = ref<FilePreviewResult | null>(null)
const previewSubmitting = ref(false)

const canCreate = computed(() => auth.hasPermission('data-migration:content:topics:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdatePermission = computed(() => auth.hasPermission('data-migration:content:topics:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDeletePermission = computed(() => auth.hasPermission('data-migration:content:topics:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEdit = (item: TopicRecord) => hasUpdatePermission.value && (canManage.value || item.created_by === auth.user?.id)
const canDelete = (item: TopicRecord) => hasDeletePermission.value && (canManage.value || item.created_by === auth.user?.id)
const msg = (e: unknown) => e instanceof Error ? e.message : '操作失败'
const cancelled = (e: unknown): boolean => { if (e === 'cancel' || e === 'close') return true; if (e instanceof Error && (e.message === 'cancel' || e.message === 'close')) return true; if (typeof e === 'object' && e !== null && ((e as any).action === 'cancel' || (e as any).action === 'close')) return true; return false }
const sd = (i: TopicRecord): Record<string, any> => i as Record<string, any>
const fmtDate = (v?: string | null) => { if (!v) return '—'; const d = new Date(v); return isNaN(d.getTime()) ? String(v) : `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}` }

const fetchList = async () => {
  if (!scopeProjectId.value) { records.value = []; total.value = 0; return }
  loading.value = true
  try {
    const { data } = await listTopics({ projectId: scopeProjectId.value, granularity: fGranularity.value || undefined, topicTypeCode: fTopicType.value || undefined, systemCodes: fSystem.value || undefined, keyword: fKeyword.value || undefined, page: page.value, size: size.value })
    records.value = data.data?.records ?? []; total.value = data.data?.total ?? 0
  } catch (e) { ElMessage.error(msg(e)) } finally { loading.value = false }
}

// 涉及系统下拉：项目切换后一次性拉取本项目全部数迁系统，筛选栏与表单共用，前端本地过滤。
const loadSystemOptions = async () => {
  if (!scopeProjectId.value) { systemOpts.value = []; return }
  systemOptsLoading.value = true
  try {
    const { data } = await getSystemOptions(scopeProjectId.value)
    systemOpts.value = data.data ?? []
  } catch {
    systemOpts.value = []
  } finally { systemOptsLoading.value = false }
}

const fetchFilterTypeOpts = async () => {
  if (!scopeProjectId.value) { filterTypeOpts.value = []; return }
  try {
    if (fGranularity.value) {
      const { data } = await getTopicTypeOptions(fGranularity.value)
      filterTypeOpts.value = data.data ?? []
    } else {
      await fetchGravOpts()
      const codes = GRAV.value.map(o => String(o.value))
      const all = await Promise.all(codes.map(code => getTopicTypeOptions(code).then(r => r.data.data ?? []).catch(() => [])))
      filterTypeOpts.value = all.flat()
    }
  } catch { filterTypeOpts.value = [] }
}

const fetchFormTypeOpts = async () => {
  if (!fv.value.granularity) { formTypeOpts.value = []; return }
  try { const { data } = await getTopicTypeOptions(fv.value.granularity); formTypeOpts.value = data.data ?? [] } catch { formTypeOpts.value = [] }
}

const fetchGravOpts = async () => {
  try { const { data } = await getDataMigrationParamOptions(DM_CODE_CATEGORIES.topicGranularity); GRAV.value = data.data ?? [] } catch { GRAV.value = [] }
}

const resetFilters = () => {
  fGranularity.value = ''; fTopicType.value = ''; fSystem.value = ''; fKeyword.value = ''; page.value = 1
  fetchFilterTypeOpts()
}
const handleSearch = () => { page.value = 1; fetchList() }
const handleFilterGranularityChange = () => { fTopicType.value = ''; fetchFilterTypeOpts(); handleSearch() }
const handlePageChange = (p: number) => { page.value = p; fetchList() }
const handleSizeChange = (s: number) => { size.value = s; page.value = 1; fetchList() }
const handleSelectionChange = (ids: number[]) => { selectedIds.value = ids }

const openCreate = () => {
  editing.value = false; editId.value = null
  fv.value = { projectId: scopeProjectId.value, granularity: '', topicTypeCode: '', topicName: '', topicSummary: '', systemCodes: [] }
  existingAttachments.value = []; pendingFiles.value = []
  formTypeOpts.value = []
  drawer.value = true
}

const openEdit = async (row: TopicRecord) => {
  editing.value = true; editId.value = row.id
  busy.value = true
  try {
    const { data } = await getTopic(row.id)
    const d = data.data!
    fv.value = { projectId: scopeProjectId.value, granularity: d.granularity, topicTypeCode: d.topic_type_code, topicName: d.asset_name, topicSummary: d.topic_summary, systemCodes: d.system_codes ? d.system_codes.split(', ').filter(Boolean) : [] }
    await fetchFormTypeOpts()
    try { const { data: attData } = await getTopicAttachments(row.id); existingAttachments.value = attData.data ?? [] } catch { existingAttachments.value = [] }
    pendingFiles.value = []
    drawer.value = true
  } catch (e) { ElMessage.error(msg(e)) } finally { busy.value = false }
}

const removeExistingAtt = (idx: number) => { existingAttachments.value.splice(idx, 1) }
const removePendingFile = (idx: number) => { pendingFiles.value.splice(idx, 1) }
const onFilesSelected = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (input.files) { for (const f of Array.from(input.files)) pendingFiles.value.push(f); input.value = '' }
}

const handleSave = async () => {
  if (!fv.value.topicName?.trim()) { ElMessage.warning('请输入专题名称'); return }
  if (!fv.value.topicSummary?.trim()) { ElMessage.warning('请输入专题简述'); return }
  if (!fv.value.granularity) { ElMessage.warning('请选择专题颗粒度'); return }
  if (!fv.value.topicTypeCode) { ElMessage.warning('请选择专题类型'); return }
  if (fv.value.granularity === 'SYSTEM' && fv.value.systemCodes.length === 0) { ElMessage.warning('系统级专题必须选择至少一个涉及系统'); return }

  saving.value = true
  try {
    // 上传待上传文件
    const uploaded: { attachmentId: number; fileName: string }[] = []
    for (const f of pendingFiles.value) {
      const { data } = await uploadAttachment(f)
      uploaded.push({ attachmentId: data.data!.id, fileName: f.name })
    }
    const allFiles = [...existingAttachments.value, ...uploaded]
    if (allFiles.length === 0) { ElMessage.warning('至少上传一个源文件'); saving.value = false; return }

    const body: TopicFormData = {
      projectId: scopeProjectId.value!,
      granularity: fv.value.granularity,
      topicTypeCode: fv.value.topicTypeCode,
      topicName: fv.value.topicName.trim(),
      topicSummary: fv.value.topicSummary.trim(),
      files: allFiles
    }
    if (fv.value.granularity === 'SYSTEM') body.systemCodes = fv.value.systemCodes

    if (editing.value && editId.value) {
      await updateTopic(editId.value, body)
      ElMessage.success('专题已更新')
    } else {
      await createTopic(body)
      ElMessage.success('专题已创建')
    }
    drawer.value = false; fetchList()
  } catch (e) { ElMessage.error(msg(e)) } finally { saving.value = false }
}

const handleDelete = async (ids?: number[]) => {
  const targetIds = ids ?? selectedIds.value
  if (targetIds.length === 0) { ElMessage.warning('请先选择要删除的专题'); return }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${targetIds.length} 条专题吗？删除后可到回收站恢复。`, '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    busy.value = true
    await deleteTopics(targetIds)
    ElMessage.success('已移入回收站')
    if (selectedIds.value.length) selectedIds.value = []
    fetchList()
  } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false }
}

const doDownload = async (row: TopicRecord) => {
  try {
    const { data } = await getTopicDownloadPath(row.id)
    const downloadUrl = data.data!
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = row.asset_name || '专题文件'
    document.body.appendChild(link); link.click(); document.body.removeChild(link)
  } catch (e) { ElMessage.error(msg(e)) }
}

async function loadPreviewCapabilities() {
  try { previewCapabilities.value = (await getFilePreviewCapabilities()).data.data ?? null } catch { previewCapabilities.value = null }
}

function canPreviewFile(fileName: string): boolean {
  if (!previewCapabilities.value?.enabled) return false
  const ext = fileName.split('.').pop()?.toLowerCase() ?? ''
  return previewCapabilities.value.allowedExtensions.includes(ext)
}

const doPreviewById = async (attachmentId: number, fileName: string) => {
  if (!previewCapabilities.value) await loadPreviewCapabilities()
  if (!previewCapabilities.value?.enabled) { ElMessage.warning('文件预览服务未启用'); return }
  if (!canPreviewFile(fileName)) { ElMessage.warning('该文件类型不支持在线预览'); return }
  previewSubmitting.value = true
  try {
    const r = await getAttachmentDownload(attachmentId)
    const downloadUrl = r.data.data?.downloadUrl
    if (!downloadUrl) throw new Error('获取文件链接失败')
    const response = await window.fetch(downloadUrl)
    const blob = await response.blob()
    const file = new File([blob], fileName, { type: blob.type })
    previewResult.value = (await uploadFilePreview(file)).data.data ?? null
    previewDialogOpen.value = true
  } catch { ElMessage.error('文件预览失败') } finally { previewSubmitting.value = false }
}

const doDownloadById = async (attachmentId: number) => {
  try {
    const r = await getAttachmentDownload(attachmentId)
    const downloadUrl = r.data.data?.downloadUrl
    if (!downloadUrl) throw new Error('获取下载链接失败')
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = '专题文件'
    document.body.appendChild(link); link.click(); document.body.removeChild(link)
  } catch (e) { ElMessage.error(msg(e)) }
}

const openDetail = async (row: TopicRecord) => {
  detailDialogOpen.value = true; detailLoading.value = true
  try {
    const { data } = await getTopic(row.id)
    detailData.value = data.data!
    // 加载附件列表
    try { const { data: attData } = await getTopicAttachments(row.id); existingAttachments.value = attData.data ?? [] } catch { existingAttachments.value = [] }
  } catch (e) { ElMessage.error(msg(e)); detailDialogOpen.value = false } finally { detailLoading.value = false }
}

const granularityLabel = (v: string) => GRAV.value.find(g => String(g.value) === String(v))?.label ?? v

watch(() => fv.value.granularity, () => {
  fv.value.topicTypeCode = ''
  fv.value.systemCodes = []
  fetchFormTypeOpts()
})

watch([scopeProjectId], () => { page.value = 1; resetFilters(); fetchList(); void loadSystemOptions() })
onMounted(() => { void scope.ensureLoaded(); fetchList(); void loadSystemOptions(); fetchFilterTypeOpts(); void fetchGravOpts() })
</script>

<template>
  <section class="dm-page">
    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <template v-else>
      <UiPageHeader title="专题材料" description="管理项目级/系统级专题材料，支持多维筛选、多文件上传、在线预览。">
        <template #actions>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新增专题</el-button>
        </template>
      </UiPageHeader>

      <UiToolbar class="dm-topic-toolbar">
        <el-select v-model="fGranularity" placeholder="颗粒度" clearable size="default" style="width:120px" @change="handleFilterGranularityChange">
          <el-option v-for="g in GRAV" :key="g.value" :label="g.label" :value="g.value" />
        </el-select>
        <el-select v-model="fTopicType" placeholder="专题类型" clearable filterable size="default" style="width:170px" @change="handleSearch">
          <el-option v-for="t in filterTypeOpts" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="fSystem" placeholder="涉及系统(编号/名称)" clearable filterable :loading="systemOptsLoading" size="default" style="width:200px" @change="handleSearch">
          <el-option v-for="s in systemOpts" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-input v-model="fKeyword" placeholder="专题名称/简述" clearable size="default" style="width:200px" @keyup.enter="handleSearch" />
        <el-button :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters(); handleSearch()">重置</el-button>
        <el-button v-if="selectedIds.length && hasDeletePermission" type="danger" :icon="Delete" :loading="busy" @click="handleDelete()">批量删除 ({{ selectedIds.length }})</el-button>
      </UiToolbar>

      <div v-loading="loading" class="dm-content">
        <UiDataTable v-if="records.length" :data="records" :selected-ids="selectedIds" @selection-change="handleSelectionChange" row-key="id">
          <el-table-column type="selection" width="48" />
          <el-table-column prop="asset_code" label="专题编号" width="180" show-overflow-tooltip />
          <el-table-column label="专题名称" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" class="dm-topic-name" @click="openDetail(row)">{{ row.asset_name }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="granularity" label="颗粒度" width="100">
            <template #default="{ row }">{{ granularityLabel(row.granularity) }}</template>
          </el-table-column>
          <el-table-column prop="topic_type_code" label="专题类型" width="150" show-overflow-tooltip />
          <el-table-column prop="topic_summary" label="专题简述" min-width="220" show-overflow-tooltip />
          <el-table-column prop="system_names" label="涉及系统" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.system_names || '—' }}</template>
          </el-table-column>
          <el-table-column prop="attachment_count" label="附件数" width="80" align="center" />
          <el-table-column prop="created_at" label="创建时间" width="160">
            <template #default="{ row }">{{ fmtDate(row.created_at) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDetail(row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button link type="primary" size="small" @click="doDownload(row)"><el-icon><Download /></el-icon>下载</el-button>
              <el-button v-if="canEdit(row)" link type="primary" size="small" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-button v-if="canDelete(row)" link type="danger" size="small" @click="handleDelete([row.id])"><el-icon><Delete /></el-icon>删除</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiEmptyState v-else-if="!loading" description="暂无专题材料" />
        <UiPagination v-if="total > 0" :total="total" :page="page" :page-size="size" @update:page="handlePageChange" @update:page-size="handleSizeChange" />
      </div>

      <!-- 新增/编辑抽屉 -->
      <UiFormDrawer v-model="drawer" :title="editing ? '编辑专题' : '新增专题'" :saving="saving" @save="handleSave" @cancel="drawer = false" width="560px">
        <el-form label-position="top" :model="fv">
          <el-form-item label="专题颗粒度" required>
            <el-select v-model="fv.granularity" placeholder="请选择" style="width:100%">
              <el-option v-for="g in GRAV" :key="g.value" :label="g.label" :value="g.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="专题类型" required>
            <el-select v-model="fv.topicTypeCode" placeholder="请先选择颗粒度" style="width:100%" :disabled="!fv.granularity">
              <el-option v-for="t in formTypeOpts" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="fv.granularity === 'SYSTEM'" label="涉及系统" required>
            <el-select v-model="fv.systemCodes" placeholder="输入系统编号或名称搜索" multiple filterable :loading="systemOptsLoading" style="width:100%">
              <el-option v-for="s in systemOpts" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="专题名称" required>
            <el-input v-model="fv.topicName" placeholder="请输入专题名称" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="专题简述" required>
            <el-input v-model="fv.topicSummary" type="textarea" :rows="3" placeholder="请输入专题简述" maxlength="2000" show-word-limit />
          </el-form-item>
          <el-form-item label="源文件" required>
            <div class="dm-attachment-list">
              <div v-for="(att, idx) in existingAttachments" :key="'e-' + att.attachmentId" class="dm-attachment-item">
                <div class="dm-attachment-icon"><el-icon :size="18"><Document /></el-icon></div>
                <div class="dm-attachment-info"><div class="dm-attachment-name">{{ att.fileName }}</div></div>
                <el-button link type="danger" size="small" @click="removeExistingAtt(idx)">移除</el-button>
              </div>
              <div v-for="(f, idx) in pendingFiles" :key="'p-' + idx" class="dm-attachment-item is-pending">
                <div class="dm-attachment-icon is-pending"><el-icon :size="18"><Document /></el-icon></div>
                <div class="dm-attachment-info"><div class="dm-attachment-name">{{ f.name }}</div></div>
                <el-button link type="danger" size="small" @click="removePendingFile(idx)">移除</el-button>
              </div>
            </div>
            <el-button size="small" :icon="Plus" @click="($refs.fileInput as HTMLInputElement).click()">添加文件</el-button>
            <input ref="fileInput" type="file" multiple style="display:none" @change="onFilesSelected" />
          </el-form-item>
        </el-form>
      </UiFormDrawer>

      <!-- 详情弹窗 -->
      <el-dialog v-model="detailDialogOpen" title="专题详情" width="640px" :close-on-click-modal="false">
        <div v-loading="detailLoading" style="min-height:200px">
          <template v-if="detailData">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="专题编号">{{ detailData.asset_code }}</el-descriptions-item>
              <el-descriptions-item label="颗粒度">{{ granularityLabel(detailData.granularity) }}</el-descriptions-item>
              <el-descriptions-item label="专题类型">{{ detailData.topic_type_code }}</el-descriptions-item>
              <el-descriptions-item label="涉及系统">{{ detailData.system_names || '—' }}</el-descriptions-item>
              <el-descriptions-item label="专题名称" :span="2">{{ detailData.asset_name }}</el-descriptions-item>
              <el-descriptions-item label="专题简述" :span="2">
                <div style="white-space:pre-wrap">{{ detailData.topic_summary || '—' }}</div>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ fmtDate(detailData.created_at) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ fmtDate(detailData.updated_at) }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="existingAttachments.length" class="dm-attachment-section">
              <div class="dm-attachment-section-title">源文件列表</div>
              <div class="dm-attachment-list">
                <div v-for="att in existingAttachments" :key="att.attachmentId" class="dm-attachment-item">
                  <div class="dm-attachment-icon"><el-icon :size="20"><Document /></el-icon></div>
                  <div class="dm-attachment-info"><div class="dm-attachment-name">{{ att.fileName }}</div></div>
                  <div class="dm-attachment-actions">
                    <el-button link type="primary" size="small" @click="doDownloadById(att.attachmentId)"><el-icon><Download /></el-icon>下载</el-button>
                    <el-button link type="primary" size="small" @click="doPreviewById(att.attachmentId, att.fileName)"><el-icon><View /></el-icon>预览</el-button>
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

      <!-- 文件预览弹窗 -->
      <UiFilePreview v-model="previewDialogOpen" :url="previewResult?.previewUrl || null" :file-name="previewResult?.fileName || '文件预览'" />
    </template>
  </section>
</template>

<style scoped>
.dm-topic-name { padding: 0; font-weight: 500; }
@media (max-width: 760px) {
  .dm-topic-toolbar :deep(.ui-toolbar__filters), .dm-topic-toolbar :deep(.ui-toolbar__actions) { width: 100%; flex-wrap: wrap; }
  .dm-topic-toolbar :deep(.el-input), .dm-topic-toolbar :deep(.el-select) { width: 100% !important; }
  .dm-topic-toolbar :deep(.ui-toolbar__filters > .el-button), .dm-topic-toolbar :deep(.ui-toolbar__actions > .el-button) { flex: 1; }
}
</style>
