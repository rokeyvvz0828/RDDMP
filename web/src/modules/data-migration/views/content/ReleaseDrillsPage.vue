<!--
  用途：数迁资产内容 - 投产及演练页（REQ-20260820-031 增量，对标迁移方案/专题材料）
  说明：统一管理投产方案、应急方案、投产总结等八类资料，支持颗粒度（项目级/组件级）/
        资料类型（参数管理）/资料名称关键字的多维筛选、分页展示、单条录入与单一源文件绑定、
        编辑（重传最新源文件）、下载、查看详情、逻辑删除。
        所属项目唯一取自全局项目上下文：页内不再有项目筛选、项目下拉与「所属项目」字段，
        项目切换后重置分页与其他筛选条件重查。组件级资料必选一个当前项目启用的物理子系统。
        文档级回收站收敛到统一页（数迁内容 › 回收站，RELEASE_DRILL 作为内容类型，经 ReleaseDrillRecycleBinSource 分发）。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref, computed, watch } from 'vue'
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
  listReleaseDrills, getReleaseDrill, createReleaseDrill, updateReleaseDrill, deleteReleaseDrills,
  getReleaseDrillTypeOptions, getSystemOptions, getReleaseDrillDownloadPath, getReleaseDrillAttachments,
  getDataMigrationParamOptions, DM_CODE_CATEGORIES,
  type ReleaseDrillRecord, type ReleaseDrillQuery, type ReleaseDrillFormData, type ReleaseDrillUpdateData, type SelectOption
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'
import { uploadAttachment } from '../../../../api/attachments'

const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId

const loading = ref(false), records = ref<ReleaseDrillRecord[]>([]), total = ref(0), page = ref(1), size = ref(20), selectedIds = ref<number[]>([]), busy = ref(false)
const fGranularity = ref(''), fMaterialType = ref(''), fSystem = ref(''), fKeyword = ref('')
const GRAV = ref<SelectOption[]>([])
const filterTypeOpts = ref<SelectOption[]>([])
const systemOpts = ref<SelectOption[]>([])
const systemOptsLoading = ref(false)

const drawer = ref(false), saving = ref(false), editing = ref(false), editId = ref<number | null>(null)
interface ReleaseDrillFormState {
  projectId: number | null
  granularity: string
  materialTypeCode: string
  materialName: string
  drillRound: string
  systemCode: string
}
const fv = ref<ReleaseDrillFormState>({ projectId: null, granularity: '', materialTypeCode: '', materialName: '', drillRound: '', systemCode: '' })
const formTypeOpts = ref<SelectOption[]>([])
const existingAttachments = ref<{ attachmentId: number; fileName: string }[]>([])
const pendingFiles = ref<File[]>([])

const detailDialogOpen = ref(false), detailLoading = ref(false), detailData = ref<ReleaseDrillRecord | null>(null)

const canCreate = computed(() => auth.hasPermission('data-migration:content:release-drills:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdatePermission = computed(() => auth.hasPermission('data-migration:content:release-drills:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDeletePermission = computed(() => auth.hasPermission('data-migration:content:release-drills:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEdit = (item: ReleaseDrillRecord) => hasUpdatePermission.value && (canManage.value || item.created_by === auth.user?.id)
const canDelete = (item: ReleaseDrillRecord) => hasDeletePermission.value && (canManage.value || item.created_by === auth.user?.id)
const msg = (e: unknown) => e instanceof Error ? e.message : '操作失败'
const cancelled = (e: unknown): boolean => e === 'cancel' || e === 'close' || (e instanceof Error && (e.message === 'cancel' || e.message === 'close'))
const sd = (i: ReleaseDrillRecord): Record<string, any> => i as Record<string, any>
const fmtDate = (v?: string | null) => { if (!v) return '—'; const d = new Date(v); return isNaN(d.getTime()) ? String(v) : `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}` }

const fetchList = async () => {
  if (!scopeProjectId.value) { records.value = []; total.value = 0; return }
  loading.value = true
  try {
    const params: ReleaseDrillQuery = { projectId: scopeProjectId.value, page: page.value, size: size.value }
    if (fGranularity.value) params.granularity = fGranularity.value
    if (fMaterialType.value) params.materialTypeCode = fMaterialType.value
    if (fSystem.value) params.systemCode = fSystem.value
    if (fKeyword.value.trim()) params.keyword = fKeyword.value.trim()
    const { data } = await listReleaseDrills(params)
    records.value = data.data?.records ?? []; total.value = data.data?.total ?? 0
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
  try { const { data } = await getReleaseDrillTypeOptions(); filterTypeOpts.value = data.data ?? [] } catch { filterTypeOpts.value = [] }
}

const fetchFormTypeOpts = async () => {
  try { const { data } = await getReleaseDrillTypeOptions(); formTypeOpts.value = data.data ?? [] } catch { formTypeOpts.value = [] }
}

const fetchGravOpts = async () => {
  try { const { data } = await getDataMigrationParamOptions(DM_CODE_CATEGORIES.releaseDrillGranularity); GRAV.value = data.data ?? [] } catch { GRAV.value = [] }
}

const resetFilters = () => { fGranularity.value = ''; fMaterialType.value = ''; fSystem.value = ''; fKeyword.value = ''; page.value = 1 }
const handleSearch = () => { page.value = 1; fetchList() }
const handlePageChange = (p: number) => { page.value = p; fetchList() }
const handleSizeChange = (s: number) => { size.value = s; page.value = 1; fetchList() }

const openCreate = () => {
  editing.value = false; editId.value = null
  fv.value = { projectId: scopeProjectId.value, granularity: '', materialTypeCode: '', materialName: '', drillRound: '', systemCode: '' }
  existingAttachments.value = []; pendingFiles.value = []
  fetchFormTypeOpts()
  drawer.value = true
}

const openEdit = async (row: ReleaseDrillRecord) => {
  editing.value = true; editId.value = row.id
  busy.value = true
  try {
    const { data } = await getReleaseDrill(row.id)
    const d = data.data!
    fv.value = { projectId: scopeProjectId.value, granularity: d.granularity, materialTypeCode: d.material_type_code, materialName: d.asset_name, drillRound: d.drill_round ?? '', systemCode: d.system_code || '' }
    await fetchFormTypeOpts()
    try { const { data: attData } = await getReleaseDrillAttachments(row.id); existingAttachments.value = attData.data ?? [] } catch { existingAttachments.value = [] }
    pendingFiles.value = []
    drawer.value = true
  } catch (e) { ElMessage.error(msg(e)) } finally { busy.value = false }
}

const removeExistingAtt = () => { existingAttachments.value = [] }
const removePendingFile = (idx: number) => { pendingFiles.value.splice(idx, 1) }
const onFilesSelected = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (input.files?.length) { pendingFiles.value = [input.files[0]]; input.value = '' }
}

const handleSave = async () => {
  if (!fv.value.materialName?.trim()) { ElMessage.warning('请输入资料名称'); return }
  if (!fv.value.granularity) { ElMessage.warning('请选择颗粒度'); return }
  if (!fv.value.materialTypeCode) { ElMessage.warning('请选择资料类型'); return }
  if (fv.value.granularity === 'COMPONENT' && !fv.value.systemCode) { ElMessage.warning('组件级资料必须选择涉及物理子系统'); return }
  if (!editing.value && pendingFiles.value.length === 0) { ElMessage.warning('请选择要绑定的源文件'); return }

  saving.value = true
  try {
    const uploaded: { attachmentId: number; fileName: string }[] = []
    if (pendingFiles.value.length) {
      const { data } = await uploadAttachment(pendingFiles.value[0])
      uploaded.push({ attachmentId: data.data!.id, fileName: pendingFiles.value[0].name })
    }
    const body: ReleaseDrillFormData = {
      projectId: scopeProjectId.value!,
      granularity: fv.value.granularity,
      materialTypeCode: fv.value.materialTypeCode,
      materialName: fv.value.materialName.trim(),
      drillRound: fv.value.drillRound.trim() || undefined,
      files: uploaded
    }
    if (fv.value.granularity === 'COMPONENT') body.systemCode = fv.value.systemCode

    if (editing.value && editId.value) {
      const patch: ReleaseDrillUpdateData = { granularity: body.granularity, materialTypeCode: body.materialTypeCode, materialName: body.materialName, drillRound: body.drillRound, systemCode: body.systemCode }
      if (uploaded.length) patch.files = uploaded
      await updateReleaseDrill(editId.value, patch)
      ElMessage.success('投产及演练资料已更新')
    } else {
      await createReleaseDrill(body)
      ElMessage.success('投产及演练资料已创建')
    }
    drawer.value = false; fetchList()
  } catch (e) { ElMessage.error(msg(e)) } finally { saving.value = false }
}

const handleDelete = async (ids?: number[]) => {
  const targetIds = ids ?? selectedIds.value
  if (targetIds.length === 0) { ElMessage.warning('请先选择要删除的资料'); return }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${targetIds.length} 条资料吗？删除后可到回收站恢复。`, '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    busy.value = true
    await deleteReleaseDrills(targetIds)
    ElMessage.success('已移入回收站')
    if (selectedIds.value.length) selectedIds.value = []
    fetchList()
  } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false }
}

const doDownload = async (row: ReleaseDrillRecord) => {
  try {
    const { data } = await getReleaseDrillDownloadPath(row.id)
    const downloadUrl = data.data!
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = row.asset_name || '投产及演练资料'
    document.body.appendChild(link); link.click(); document.body.removeChild(link)
  } catch (e) { ElMessage.error(msg(e)) }
}

const openDetail = async (row: ReleaseDrillRecord) => {
  detailDialogOpen.value = true; detailLoading.value = true
  try {
    const { data } = await getReleaseDrill(row.id)
    detailData.value = data.data!
    try { const { data: attData } = await getReleaseDrillAttachments(row.id); existingAttachments.value = attData.data ?? [] } catch { existingAttachments.value = [] }
  } catch (e) { ElMessage.error(msg(e)); detailDialogOpen.value = false } finally { detailLoading.value = false }
}

const granularityLabel = (v: string) => GRAV.value.find(g => String(g.value) === String(v))?.label ?? v
const typeLabel = (v: string) => filterTypeOpts.value.find(o => String(o.value) === String(v))?.label ?? v

watch(() => fv.value.granularity, () => { fv.value.materialTypeCode = ''; fv.value.systemCode = '' })

watch(scopeProjectId, () => { page.value = 1; resetFilters(); fetchList(); void loadSystemOptions(); fetchFilterTypeOpts() })
onMounted(() => { void scope.ensureLoaded(); fetchList(); void loadSystemOptions(); fetchFilterTypeOpts(); void fetchGravOpts() })
</script>

<template>
  <section class="dm-page">
    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <template v-else>
      <UiPageHeader title="投产及演练" description="归档投产方案、应急方案、投产总结等资料，支持按颗粒度/资料类型检索、上传、下载与回收站管理。">
        <template #actions>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新增资料</el-button>
        </template>
      </UiPageHeader>

      <UiToolbar class="dm-topic-toolbar">
        <el-select v-model="fGranularity" placeholder="颗粒度" clearable size="default" style="width:120px" @change="handleSearch">
          <el-option v-for="g in GRAV" :key="g.value" :label="g.label" :value="g.value" />
        </el-select>
        <el-select v-model="fMaterialType" placeholder="资料类型" clearable filterable size="default" style="width:160px" @change="handleSearch">
          <el-option v-for="t in filterTypeOpts" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="fSystem" placeholder="输入系统编号或名称搜索" clearable filterable size="default" style="width:220px" :loading="systemOptsLoading" @change="handleSearch">
          <el-option v-for="s in systemOpts" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-input v-model="fKeyword" clearable placeholder="搜索资料名称" size="default" style="width:220px" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button size="default" :icon="Refresh" :disabled="loading" @click="fetchList">刷新</el-button>
          <el-button size="default" type="primary" :icon="Search" :disabled="loading" @click="handleSearch">查询</el-button>
          <el-button size="default" @click="resetFilters">重置</el-button>
          <el-button v-if="selectedIds.length" size="default" type="danger" plain :disabled="busy" @click="handleDelete()"><el-icon><Delete /></el-icon>移入回收站({{ selectedIds.length }})</el-button>
        </template>
      </UiToolbar>

      <UiDataTable :data="records" :loading="loading" row-key="id" @selection-change="(r: ReleaseDrillRecord[]) => selectedIds = r.map(x => x.id)">
        <el-table-column type="selection" width="48" />
        <el-table-column label="资料编号" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ sd(row).asset_code ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="资料名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)" style="padding:0">{{ row.asset_name ?? '—' }}</el-button></template>
        </el-table-column>
        <el-table-column label="资料类型" width="130"><template #default="{ row }">{{ typeLabel(row.material_type_code) }}</template></el-table-column>
        <el-table-column label="颗粒度" width="100"><template #default="{ row }">{{ granularityLabel(row.granularity) }}</template></el-table-column>
        <el-table-column label="所属轮次" width="140" show-overflow-tooltip><template #default="{ row }">{{ row.drill_round || '—' }}</template></el-table-column>
        <el-table-column label="涉及物理子系统" width="180" show-overflow-tooltip><template #default="{ row }">{{ row.system_name ? `${row.system_code} - ${row.system_name}` : (row.system_code || '—') }}</template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="doDownload(row)"><el-icon><Download /></el-icon>下载</el-button>
            <el-button v-if="canEdit(row)" link type="primary" size="small" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
            <el-button v-if="canDelete(row)" link type="danger" size="small" @click="handleDelete([row.id])"><el-icon><Delete /></el-icon>删除</el-button>
          </template>
        </el-table-column>
      </UiDataTable>

      <div v-if="!loading && records.length === 0" style="margin:24px 0">
        <UiEmptyState text="暂无投产及演练资料，请新增或调整筛选条件。" />
      </div>

      <UiPagination v-if="total > 0" :page="page" :page-size="size" :total="total" :page-sizes="[10,20,50,100]" @update:page-size="(v: number) => { size = v; page = 1; fetchList() }" @update:page="(v: number) => { page = v; fetchList() }" />

      <UiFormDrawer v-model="drawer" :title="editing ? '编辑投产及演练资料' : '新增投产及演练资料'" :confirm-loading="saving" @confirm="handleSave">
        <el-form label-position="top">
          <el-form-item label="颗粒度" required>
            <el-select v-model="fv.granularity" placeholder="请选择" style="width:100%">
              <el-option v-for="g in GRAV" :key="g.value" :label="g.label" :value="g.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="资料类型" required>
            <el-select v-model="fv.materialTypeCode" placeholder="请选择资料类型" style="width:100%">
              <el-option v-for="t in formTypeOpts" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属轮次">
            <el-input v-model="fv.drillRound" placeholder="手动输入投产/演练轮次" maxlength="100" />
          </el-form-item>
          <el-form-item label="资料名称" required>
            <el-input v-model="fv.materialName" placeholder="请输入资料名称" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item v-if="fv.granularity === 'COMPONENT'" label="涉及物理子系统" required>
            <el-select v-model="fv.systemCode" placeholder="输入系统编号或名称搜索" filterable :loading="systemOptsLoading" style="width:100%">
              <el-option v-for="s in systemOpts" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="源文件" :required="!editing">
            <div class="attachment-list">
              <div v-for="att in existingAttachments" :key="att.attachmentId" class="attachment-item">
                <div class="attachment-icon"><el-icon :size="18"><Document /></el-icon></div>
                <div class="attachment-info"><div class="attachment-name">{{ att.fileName }}</div></div>
                <el-button link type="danger" size="small" @click="removeExistingAtt">移除</el-button>
              </div>
              <div v-for="(f, idx) in pendingFiles" :key="idx" class="attachment-item">
                <div class="attachment-icon"><el-icon :size="18"><Document /></el-icon></div>
                <div class="attachment-info"><div class="attachment-name">{{ f.name }}</div></div>
                <el-button link type="danger" size="small" @click="removePendingFile(idx)">移除</el-button>
              </div>
            </div>
            <el-button size="small" :icon="Plus" @click="($refs.fileInput as HTMLInputElement).click()">{{ existingAttachments.length || pendingFiles.length ? '替换文件' : '选择文件' }}</el-button>
            <input ref="fileInput" type="file" style="display:none" @change="onFilesSelected" />
          </el-form-item>
        </el-form>
      </UiFormDrawer>

      <el-dialog v-model="detailDialogOpen" title="投产及演练资料详情" width="640px" :close-on-click-modal="false">
        <div v-loading="detailLoading" style="min-height:160px">
          <template v-if="detailData">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="资料编号">{{ detailData.asset_code }}</el-descriptions-item>
              <el-descriptions-item label="颗粒度">{{ granularityLabel(detailData.granularity) }}</el-descriptions-item>
              <el-descriptions-item label="资料类型">{{ typeLabel(detailData.material_type_code) }}</el-descriptions-item>
              <el-descriptions-item label="所属轮次">{{ detailData.drill_round || '—' }}</el-descriptions-item>
              <el-descriptions-item label="涉及物理子系统" :span="2">{{ detailData.system_name ? `${detailData.system_code} - ${detailData.system_name}` : (detailData.system_code || '—') }}</el-descriptions-item>
              <el-descriptions-item label="资料名称" :span="2">{{ detailData.asset_name }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ fmtDate(detailData.created_at) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ fmtDate(detailData.updated_at) }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="existingAttachments.length" style="margin-top:16px">
              <div style="font-weight:500;margin-bottom:8px">源文件</div>
              <div class="attachment-list">
                <div v-for="att in existingAttachments" :key="att.attachmentId" class="attachment-item">
                  <div class="attachment-icon"><el-icon :size="20"><Document /></el-icon></div>
                  <div class="attachment-info"><div class="attachment-name">{{ att.fileName }}</div></div>
                  <el-button link type="primary" size="small" @click="doDownload(detailData)"><el-icon><Download /></el-icon>下载</el-button>
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
}
</style>
