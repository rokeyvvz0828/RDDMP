<!--
  用途：数迁资产内容 - 问题清单页
  说明：全流程记录与管理迁移过程各类缺陷及问题，集成多维度复杂条件检索、
        分页展示、详情页精细化编辑、单条新增、Excel批量导入、逻辑删除和回收站。
        所属项目唯一取自全局项目上下文：页内不再有项目筛选、项目下拉与「所属项目」字段，列表/回收站/新增/编辑/导入
        均固定使用当前项目，项目切换后重置分页与其他筛选条件重查。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Edit, Plus, Refresh, Search, FolderOpened, UploadFilled } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import { useAuthStore } from '../../../../stores/auth'
import {
  listIssues, getIssue, createIssue, updateIssue, deleteIssues, importIssues, exportIssues,
  listIssueRecycleBin, restoreIssues, purgeIssues, purgeAllIssues,
  getIssueSystemOptions, getIssueSystemName,
  getIssueMeetingOptions, getIssueTargetTableOptions, getIssueTargetFieldOptions,
  type IssueRecord, type IssueQuery, type IssueFormData, type IssueUpdateData, type IssueImportResult, type SelectOption
} from '../../../../api/data-migration'
import ProjectScopeState from '../../components/ProjectScopeState.vue'
import { useProjectScope } from '../../composables/useProjectScope'

const auth = useAuthStore()
const scope = useProjectScope()
const scopeState = scope.state
const scopeProjectId = scope.projectId
const scopeProjectName = scope.projectName

const loading = ref(false), records = ref<IssueRecord[]>([]), total = ref(0), page = ref(1), size = ref(20), selectedIds = ref<number[]>([]), busy = ref(false)
const fGranularity = ref(''), fSystem = ref(''), fSource = ref(''), fDefect = ref(''), fFreq = ref(''), fKeyword = ref('')
const cache = { sys: new Map<number, SelectOption[]>(), meet: new Map<number, SelectOption[]>(), tbl: new Map<number, SelectOption[]>(), fld: new Map<number, SelectOption[]>() }
const GRAV = [{ value: 'PROJECT', label: '项目级' }, { value: 'COMPONENT', label: '组件级' }, { value: 'TABLE', label: '表级' }, { value: 'FIELD', label: '字段级' }]
const SRC = [{ value: 'MIGRATION_CHECK', label: '数迁检核' }, { value: 'SIT_FEEDBACK', label: 'SIT测试反馈' }, { value: 'UAT_FEEDBACK', label: 'UAT测试反馈' }, { value: 'DATA_LINE_FEEDBACK', label: '数据线反馈' }, { value: 'EXPERT_FEEDBACK', label: '事业群专家反馈' }, { value: 'RISK_IDENTIFICATION', label: '风险识别' }, { value: 'MIGRATION_RELEASE', label: '数迁投产过程' }]
const DEF = [{ value: 'REQUIREMENT', label: '需求问题' }, { value: 'DESIGN', label: '设计问题' }, { value: 'CODING', label: '编码问题' }, { value: 'DATA_QUALITY', label: '数据质量问题' }, { value: 'CLEANUP', label: '清理补录问题' }, { value: 'BUSINESS', label: '业务问题' }, { value: 'UNDERSTANDING', label: '理解问题' }, { value: 'PERFORMANCE', label: '性能问题' }, { value: 'MASKING', label: '脱敏问题' }, { value: 'OTHER', label: '其他问题' }]
const FREQ = [{ value: 'CLASSIC', label: '经典问题' }, { value: 'HIGH_FREQ', label: '高频重复' }, { value: 'LOW_FREQ', label: '低频偶发' }, { value: 'SINGLE_CASE', label: '单次个案' }]
const drawer = ref(false), saving = ref(false), editing = ref(false), editId = ref<number | null>(null)
const fv = ref({ projectId: null as number | null, issueCode: '', issueName: '', granularity: '', systemCode: '', systemName: '', issueSource: '', defectType: '', issueDescription: '', solution: '', meetingConclusion: '', processingSteps: '', businessScenario: '', handler: '', responsibleParty: '', keywords: [] as string[], relatedMeetingMinutes: [] as number[], frequency: '', relatedTables: [] as number[], relatedFields: [] as number[] })
const fSystemOpts = ref<SelectOption[]>([]), fMeetingOpts = ref<SelectOption[]>([]), fTableOpts = ref<SelectOption[]>([]), fFieldOpts = ref<SelectOption[]>([]), kwInput = ref('')
const fieldTableMap = new Map<number, number>()
const previousTableIds = ref<number[]>([])
const filterSysOpts = ref<SelectOption[]>([])
const tab = ref<'list' | 'recycle'>('list'), recLoading = ref(false), recRecords = ref<IssueRecord[]>([]), recTotal = ref(0), recPage = ref(1), recSize = ref(20), recSelected = ref<number[]>([]), recKeyword = ref('')
const importDlg = ref(false), importFile = ref<File | null>(null), importResult = ref<IssueImportResult | null>(null)
const canCreate = computed(() => auth.hasPermission('data-migration:content:issues:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasUpdatePermission = computed(() => auth.hasPermission('data-migration:content:issues:update') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const hasDeletePermission = computed(() => auth.hasPermission('data-migration:content:issues:delete') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canManage = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canEdit = (item: IssueRecord) => hasUpdatePermission.value && (canManage.value || item.owner_id === auth.user?.id)
const canDelete = (item: IssueRecord) => hasDeletePermission.value && (canManage.value || item.owner_id === auth.user?.id)
const msg = (e: unknown) => e instanceof Error ? e.message : '操作失败'
const cancelled = (e: unknown): boolean => { if (e === 'cancel' || e === 'close') return true; if (e instanceof Error && (e.message === 'cancel' || e.message === 'close')) return true; if (typeof e === 'object' && e !== null && ((e as any).action === 'cancel' || (e as any).action === 'close')) return true; return false }
const sd = (i: IssueRecord): Record<string, any> => i as Record<string, any>
const fmtDate = (v?: string | null) => { if (!v) return '—'; const d = new Date(v); return isNaN(d.getTime()) ? String(v) : `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}` }
const lbl = (o: SelectOption[], v?: string) => o.find(x => x.value === v)?.label ?? v ?? '—'
async function loadFilterSystems() { const pid = scopeProjectId.value; filterSysOpts.value = pid ? ((await getIssueSystemOptions(pid).catch(() => null))?.data.data ?? []) : [] }
async function getCached(m: Map<number, SelectOption[]>, id: number, l: (id: number) => Promise<SelectOption[]>): Promise<SelectOption[]> { if (m.has(id)) return m.get(id)!; const r = await l(id); m.set(id, r); return r }
async function loadList() { if (scopeProjectId.value == null) { records.value = []; total.value = 0; selectedIds.value = []; return }; loading.value = true; selectedIds.value = []; try { const p: IssueQuery = { page: page.value, size: size.value, projectId: scopeProjectId.value }; if (fGranularity.value) p.granularity = fGranularity.value; if (fSystem.value) p.systemCode = fSystem.value; if (fSource.value) p.issueSource = fSource.value; if (fDefect.value) p.defectType = fDefect.value; if (fFreq.value) p.frequency = fFreq.value; if (fKeyword.value.trim()) p.keyword = fKeyword.value.trim(); const d = (await listIssues(p)).data.data; records.value = d?.records ?? []; total.value = d?.total ?? 0 } catch (e) { ElMessage.error(msg(e)); records.value = []; total.value = 0 } finally { loading.value = false } }
async function loadRecycle() { if (scopeProjectId.value == null) { recRecords.value = []; recTotal.value = 0; recSelected.value = []; return }; recLoading.value = true; recSelected.value = []; try { const p: { projectId: number; keyword?: string; page?: number; size?: number } = { page: recPage.value, size: recSize.value, projectId: scopeProjectId.value }; if (recKeyword.value.trim()) p.keyword = recKeyword.value.trim(); const d = (await listIssueRecycleBin(p)).data.data; recRecords.value = d?.records ?? []; recTotal.value = d?.total ?? 0 } catch (e) { ElMessage.error(msg(e)); recRecords.value = []; recTotal.value = 0 } finally { recLoading.value = false } }
function doSearch() { page.value = 1; loadList() }
function doReset() { fGranularity.value = ''; fSystem.value = ''; fSource.value = ''; fDefect.value = ''; fFreq.value = ''; fKeyword.value = ''; page.value = 1; loadList() }
function switchTab(t: string) { t === 'recycle' ? loadRecycle() : loadList() }
function resetForm() { fv.value = { projectId: scopeProjectId.value, issueCode: '', issueName: '', granularity: '', systemCode: '', systemName: '', issueSource: '', defectType: '', issueDescription: '', solution: '', meetingConclusion: '', processingSteps: '', businessScenario: '', handler: '', responsibleParty: '', keywords: [], relatedMeetingMinutes: [], frequency: '', relatedTables: [], relatedFields: [] }; previousTableIds.value = []; kwInput.value = ''; fSystemOpts.value = []; fMeetingOpts.value = []; fTableOpts.value = []; fFieldOpts.value = [] }
function openAdd() { editing.value = false; editId.value = null; resetForm(); if (scopeProjectId.value) void loadFormOpts(scopeProjectId.value); drawer.value = true }
async function loadFormOpts(pid: number) { const [s, m, t] = await Promise.all([getCached(cache.sys, pid, id => getIssueSystemOptions(id).then(r => r.data.data ?? [])), getCached(cache.meet, pid, id => getIssueMeetingOptions(id).then(r => r.data.data ?? [])), getCached(cache.tbl, pid, id => getIssueTargetTableOptions(id).then(r => r.data.data ?? []))]); fSystemOpts.value = s; fMeetingOpts.value = m; fTableOpts.value = t }
async function loadFieldOpts(tableCodes: number[]) { fFieldOpts.value = []; fieldTableMap.clear(); for (const tableCode of tableCodes) { const r = await getCached(cache.fld, tableCode, tid => getIssueTargetFieldOptions(tid).then(res => res.data.data ?? [])); r.forEach(o => fieldTableMap.set(Number(o.value), tableCode)); fFieldOpts.value.push(...r) } }
async function openEdit(item: IssueRecord) {
  if (!canEdit(item)) return
  if (busy.value) return
  busy.value = true
  drawer.value = false
  editing.value = false
  editId.value = null
  resetForm()
  try {
    const detail = (await getIssue(item.id)).data.data
    if (!detail) throw new Error('未获取到问题详情')
    const d = sd(detail)
    const relatedMeetingMinutes = Array.isArray(d.relatedMeetingMinutes) ? [...d.relatedMeetingMinutes] : []
    const relatedTables = Array.isArray(d.relatedTables) ? [...d.relatedTables] : []
    const relatedFields = Array.isArray(d.relatedFields) ? [...d.relatedFields] : []
    const kw = typeof d.keywords === 'string' ? d.keywords.split(',').map((v: string) => v.trim()).filter(Boolean) : (Array.isArray(d.keywords) ? [...d.keywords] : [])
    await loadFormOpts(detail.project_id)
    await loadFieldOpts(relatedTables)
    fv.value = { projectId: scopeProjectId.value, issueCode: detail.asset_code, issueName: detail.asset_name, granularity: d.granularity ?? '', systemCode: d.systemCode ?? '', systemName: d.systemName ?? '', issueSource: d.issueSource ?? '', defectType: d.defectType ?? '', issueDescription: d.issueDescription ?? '', solution: d.solution ?? '', meetingConclusion: d.meetingConclusion ?? '', processingSteps: d.processingSteps ?? '', businessScenario: d.businessScenario ?? '', handler: d.handler ?? '', responsibleParty: d.responsibleParty ?? '', keywords: kw, relatedMeetingMinutes, frequency: d.frequency ?? '', relatedTables, relatedFields }
    previousTableIds.value = [...relatedTables]
    editing.value = true
    editId.value = detail.id
    drawer.value = true
  } catch (e) {
    resetForm()
    ElMessage.error(`问题详情加载失败：${msg(e)}`)
  } finally {
    busy.value = false
  }
}
async function onSysChange(code: string) { fv.value.systemName = ''; if (code?.trim()) try { fv.value.systemName = (await getIssueSystemName(code.trim())).data.data ?? '' } catch {} }
async function onTblChange(ids: number[]) {
  const oldIds = [...previousTableIds.value]
  const removedIds = oldIds.filter(id => !ids.includes(id))
  const removedFieldIds = fv.value.relatedFields.filter(id => removedIds.includes(fieldTableMap.get(id) ?? -1))
  if (removedFieldIds.length > 0) {
    try {
      await ElMessageBox.confirm(
        `移除目标表将同步移除其关联字段（${removedFieldIds.length} 个）。是否继续？`,
        '确认删除',
        { type: 'warning' }
      )
    } catch {
      fv.value.relatedTables = oldIds
      return
    }
  }
  fv.value.relatedTables = [...ids]
  previousTableIds.value = [...ids]
  fv.value.relatedFields = fv.value.relatedFields.filter(id => !removedFieldIds.includes(id))
  await loadFieldOpts(ids)
}

async function onFieldChange(fieldIds: number[]) {
  fv.value.relatedFields = [...fieldIds]
}
function addKw() { const v = kwInput.value.trim(); if (v && !fv.value.keywords.includes(v)) fv.value.keywords.push(v); kwInput.value = '' }
function rmKw(i: number) { fv.value.keywords.splice(i, 1) }
function validate(): string | null { if (!fv.value.projectId) return '当前项目不可用，请在顶部项目切换器中重新选择项目'; if (!fv.value.issueCode.trim()) return '请输入问题编号'; if (!fv.value.issueName.trim()) return '请输入问题名称'; if (!fv.value.issueDescription.trim()) return '请输入问题描述'; if (!fv.value.granularity) return '请选择颗粒度'; if (!fv.value.issueSource) return '请选择问题来源'; if (!fv.value.defectType) return '请选择缺陷类型'; if (!fv.value.frequency) return '请选择问题频率分类'; return null }
async function doSave() { const err = validate(); if (err) { ElMessage.warning(err); return }; saving.value = true; try { const body: IssueUpdateData = { issueCode: fv.value.issueCode.trim(), issueName: fv.value.issueName.trim(), granularity: fv.value.granularity || undefined, systemCode: fv.value.systemCode || undefined, systemName: fv.value.systemName || undefined, issueSource: fv.value.issueSource || undefined, defectType: fv.value.defectType || undefined, issueDescription: fv.value.issueDescription.trim() || undefined, solution: fv.value.solution.trim() || undefined, meetingConclusion: fv.value.meetingConclusion.trim() || undefined, processingSteps: fv.value.processingSteps.trim() || undefined, businessScenario: fv.value.businessScenario.trim() || undefined, handler: fv.value.handler.trim() || undefined, responsibleParty: fv.value.responsibleParty.trim() || undefined, keywords: fv.value.keywords.length ? fv.value.keywords : undefined, relatedMeetingMinutes: [...fv.value.relatedMeetingMinutes], relatedMeetingMinuteNames: fv.value.relatedMeetingMinutes.length ? fv.value.relatedMeetingMinutes.map(id => fMeetingOpts.value.find(o => o.value === id)?.label ?? String(id)).join(', ') : undefined, frequency: fv.value.frequency || undefined, relatedTables: [...fv.value.relatedTables], relatedTableNames: fv.value.relatedTables.length ? fv.value.relatedTables.map(id => fTableOpts.value.find(o => o.value === id)?.label ?? String(id)).join(', ') : undefined, relatedFields: [...fv.value.relatedFields], relatedFieldNames: fv.value.relatedFields.length ? fv.value.relatedFields.map(id => fFieldOpts.value.find(o => o.value === id)?.label ?? String(id)).join(', ') : undefined }; if (editing.value && editId.value) { await updateIssue(editId.value, body); ElMessage.success('更新成功') } else { await createIssue({ ...body, projectId: fv.value.projectId! }); ElMessage.success('新增成功') }; drawer.value = false; loadList() } catch (e) { ElMessage.error(msg(e)) } finally { saving.value = false } }
async function doDelete() { if (!selectedIds.value.length) return; try { await ElMessageBox.confirm(`确认将选中的 ${selectedIds.value.length} 条问题移入回收站？`, '移入回收站', { type: 'warning' }); busy.value = true; await deleteIssues(selectedIds.value); ElMessage.success('已移入回收站'); loadList() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doDeleteOne(item: IssueRecord) { if (!canDelete(item)) return; try { await ElMessageBox.confirm(`确认将"${item.asset_name}"移入回收站？`, '移入回收站', { type: 'warning' }); busy.value = true; await deleteIssues([item.id]); ElMessage.success('已移入回收站'); loadList() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doRestore() { if (!recSelected.value.length) return; try { await ElMessageBox.confirm(`确认恢复选中的 ${recSelected.value.length} 条问题？`, '恢复问题'); busy.value = true; await restoreIssues(recSelected.value); ElMessage.success('恢复成功'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doRestoreOne(item: IssueRecord) { try { await ElMessageBox.confirm(`确认恢复"${item.asset_name}"？`, '恢复问题'); busy.value = true; await restoreIssues([item.id]); ElMessage.success('恢复成功'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doPurge() { if (!recSelected.value.length) return; try { await ElMessageBox.confirm(`确认彻底销毁选中的 ${recSelected.value.length} 条问题？此操作不可恢复。`, '彻底销毁', { type: 'error', confirmButtonText: '彻底销毁' }); busy.value = true; await purgeIssues(recSelected.value); ElMessage.success('清理完成'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doPurgeOne(item: IssueRecord) { try { await ElMessageBox.confirm(`确认彻底销毁"${item.asset_name}"？此操作不可恢复。`, '彻底销毁', { type: 'error', confirmButtonText: '彻底销毁' }); busy.value = true; await purgeIssues([item.id]); ElMessage.success('清理完成'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doPurgeAll() { const pid = scopeProjectId.value; if (!pid) { ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目'); return }; try { await ElMessageBox.confirm(`确认彻底销毁当前项目（${scopeProjectName.value || pid}）回收站内的全部问题？其他项目不受影响，此操作不可恢复。`, '清空当前项目回收站', { type: 'error', confirmButtonText: '清空' }); busy.value = true; await purgeAllIssues(pid); ElMessage.success('当前项目回收站已清空'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
function openImport() { importFile.value = null; importResult.value = null; importDlg.value = true }
function handleImportFile(file: File) { importFile.value = file; importResult.value = null; return false }
function handleImportRemove() { importFile.value = null; importResult.value = null }
async function doImport() { const pid = scopeProjectId.value; if (!pid) { ElMessage.warning('当前项目不可用，请在顶部项目切换器中重新选择项目'); return } if (!importFile.value) { ElMessage.warning('请选择 Excel 文件'); return }; busy.value = true; try { importResult.value = (await importIssues(pid, importFile.value)).data.data; ElMessage.success(`导入完成：成功 ${importResult.value?.successCount ?? 0} 条，失败 ${importResult.value?.failureCount ?? 0} 条`); loadList() } catch (e) { ElMessage.error(msg(e)) } finally { busy.value = false } }
onMounted(async () => { await scope.ensureLoaded(); await loadFilterSystems() })

// 全局项目变化：丢弃上一个项目的列表、回收站、筛选与表单状态，按新项目重新加载。
watch(scopeProjectId, () => {
  records.value = []
  total.value = 0
  selectedIds.value = []
  page.value = 1
  recRecords.value = []
  recTotal.value = 0
  recSelected.value = []
  recPage.value = 1
  recKeyword.value = ''
  fGranularity.value = ''
  fSystem.value = ''
  fSource.value = ''
  fDefect.value = ''
  fFreq.value = ''
  fKeyword.value = ''
  filterSysOpts.value = []
  drawer.value = false
  importDlg.value = false
  editing.value = false
  editId.value = null
  resetForm()
  void loadList()
  void loadFilterSystems()
  if (tab.value === 'recycle') void loadRecycle()
}, { immediate: true })
</script>

<template>
  <section class="dm-page-root">
    <UiPageHeader title="问题清单" description="列表、新增与导入均固定属于顶部项目切换器选择的当前项目。">
      <template #actions>
        <el-button v-if="canCreate && scopeState === 'ready'" type="primary" :disabled="loading || busy" @click="openAdd"><el-icon><Plus /></el-icon>新增问题</el-button>
        <el-button v-if="canCreate && scopeState === 'ready'" type="primary" plain :disabled="loading || busy" @click="openImport"><el-icon><UploadFilled /></el-icon>Excel导入</el-button>
      </template>
    </UiPageHeader>
    <ProjectScopeState v-if="scopeState !== 'ready'" :state="scopeState" @retry="scope.retry()" />
    <template v-else>
    <el-tabs v-model="tab" @tab-click="(t: any) => switchTab(t.paneName)">
      <el-tab-pane label="问题清单" name="list">
        <UiToolbar>
          <el-select v-model="fGranularity" placeholder="颗粒度" clearable style="width:110px" @change="doSearch">
            <el-option v-for="o in GRAV" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="fSystem" placeholder="系统编号" clearable filterable style="width:140px" @change="doSearch">
            <el-option v-for="o in filterSysOpts" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="fSource" placeholder="问题来源" clearable style="width:140px" @change="doSearch">
            <el-option v-for="o in SRC" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="fDefect" placeholder="缺陷类型" clearable style="width:130px" @change="doSearch">
            <el-option v-for="o in DEF" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select v-model="fFreq" placeholder="问题频率" clearable style="width:120px" @change="doSearch">
            <el-option v-for="o in FREQ" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input v-model="fKeyword" clearable placeholder="全局模糊搜索" style="width:200px" @keyup.enter="doSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <template #actions>
            <el-button :disabled="loading" @click="loadList"><el-icon><Refresh /></el-icon>刷新</el-button>
            <el-button type="primary" :disabled="loading" @click="doSearch"><el-icon><Search /></el-icon>查询</el-button>
            <el-button :disabled="loading" @click="doReset">重置</el-button>
            <el-button v-if="selectedIds.length" type="danger" plain :disabled="busy" @click="doDelete"><el-icon><Delete /></el-icon>移入回收站({{ selectedIds.length }})</el-button>
          </template>
        </UiToolbar>
        <UiDataTable :data="records" :loading="loading" row-key="id" @selection-change="(r: IssueRecord[]) => selectedIds = r.map(x => x.id)">
          <el-table-column type="selection" width="48" :selectable="canDelete" />
          <el-table-column label="问题编号" prop="asset_code" width="130" show-overflow-tooltip />
          <el-table-column label="问题名称" prop="asset_name" min-width="160" show-overflow-tooltip />
          <el-table-column label="颗粒度" width="90"><template #default="{ row }">{{ lbl(GRAV, sd(row).granularity) }}</template></el-table-column>
          <el-table-column label="系统编号" width="110" show-overflow-tooltip><template #default="{ row }">{{ sd(row).systemCode ?? '—' }}</template></el-table-column>
          <el-table-column label="系统名称" width="120" show-overflow-tooltip><template #default="{ row }">{{ sd(row).systemName ?? '—' }}</template></el-table-column>
          <el-table-column label="问题来源" width="120"><template #default="{ row }">{{ lbl(SRC, sd(row).issueSource) }}</template></el-table-column>
          <el-table-column label="缺陷类型" width="110"><template #default="{ row }">{{ lbl(DEF, sd(row).defectType) }}</template></el-table-column>
          <el-table-column label="问题频率" width="100"><template #default="{ row }">{{ lbl(FREQ, sd(row).frequency) }}</template></el-table-column>
          <el-table-column label="问题描述" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ sd(row).issueDescription ?? '—' }}</template></el-table-column>
          <el-table-column label="解决方案" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ sd(row).solution ?? '—' }}</template></el-table-column>
          <el-table-column label="会议结论" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ sd(row).meetingConclusion ?? '—' }}</template></el-table-column>
          <el-table-column label="关联纪要" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.relatedMeetingMinutes?.join(', ') || '—' }}</template></el-table-column>
          <el-table-column label="问题相关表" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.relatedTables?.join(', ') || '—' }}</template></el-table-column>
          <el-table-column label="问题相关字段" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.relatedFields?.join(', ') || '—' }}</template></el-table-column>
          <el-table-column label="更新时间" width="150"><template #default="{ row }">{{ fmtDate(row.updated_at) }}</template></el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" :disabled="busy || !canEdit(row)" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-button link type="danger" size="small" :disabled="busy || !canDelete(row)" @click="doDeleteOne(row)"><el-icon><Delete /></el-icon>删除</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiEmptyState v-if="!loading && records.length === 0" description="当前项目下暂无问题数据" icon="search" />
        <UiPagination v-if="total > 0" :page="page" :page-size="size" :total="total" :page-sizes="[10,20,50,100]" @update:page-size="(v: number) => { size = v; page = 1; loadList() }" @update:page="(v: number) => { page = v; loadList() }" />
      </el-tab-pane>
      <el-tab-pane v-if="canManage" label="回收站" name="recycle">
        <UiToolbar>
          <el-input v-model="recKeyword" clearable placeholder="搜索问题编号/名称" style="width:240px" @keyup.enter="loadRecycle"><template #prefix><el-icon><Search /></el-icon></template></el-input>
          <template #actions>
            <el-button :disabled="recLoading" @click="loadRecycle"><el-icon><Refresh /></el-icon>刷新</el-button>
            <el-button v-if="recSelected.length" type="warning" plain :disabled="busy" @click="doRestore"><el-icon><FolderOpened /></el-icon>恢复({{ recSelected.length }})</el-button>
            <el-button v-if="recSelected.length" type="danger" plain :disabled="busy" @click="doPurge"><el-icon><Delete /></el-icon>彻底销毁({{ recSelected.length }})</el-button>
            <el-button v-if="recTotal > 0" type="danger" :disabled="busy" @click="doPurgeAll">清空本项目回收站</el-button>
          </template>
        </UiToolbar>
        <UiDataTable :data="recRecords" :loading="recLoading" row-key="id" @selection-change="(r: IssueRecord[]) => recSelected = r.map(x => x.id)">
          <el-table-column type="selection" width="48" />
          <el-table-column label="问题编号" prop="asset_code" width="130" />
          <el-table-column label="问题名称" prop="asset_name" min-width="160" show-overflow-tooltip />
          <el-table-column label="删除时间" width="150"><template #default="{ row }">{{ fmtDate(row.deleted_at) }}</template></el-table-column>
          <el-table-column label="删除人" width="100"><template #default="{ row }">{{ row.deleted_by_name ?? '—' }}</template></el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="warning" size="small" :disabled="busy" @click="doRestoreOne(row)"><el-icon><FolderOpened /></el-icon>恢复</el-button>
              <el-button link type="danger" size="small" :disabled="busy" @click="doPurgeOne(row)"><el-icon><Delete /></el-icon>销毁</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiEmptyState v-if="!recLoading && recRecords.length === 0" description="当前项目回收站为空" icon="delete" />
        <UiPagination v-if="recTotal > 0" :page="recPage" :page-size="recSize" :total="recTotal" :page-sizes="[10,20,50,100]" @update:page-size="(v: number) => { recSize = v; recPage = 1; loadRecycle() }" @update:page="(v: number) => { recPage = v; loadRecycle() }" />
      </el-tab-pane>
    </el-tabs>
    </template>

    <!-- 新增/编辑抽屉 -->
    <UiFormDrawer v-model="drawer" :title="editing ? '编辑问题' : '新增问题'" :loading="saving" width="min(900px, calc(100vw - 24px))" @submit="doSave">
      <el-form label-position="top">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="问题编号" required><el-input v-model="fv.issueCode" placeholder="同一项目下不允许重复" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="问题名称" required><el-input v-model="fv.issueName" /></el-form-item>
          </el-col>
        </el-row>

        <!-- 分类信息 -->
        <el-divider content-position="left">分类信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="颗粒度" required>
              <el-select v-model="fv.granularity" placeholder="选择颗粒度" style="width:100%">
                <el-option v-for="o in GRAV" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="系统编号">
              <el-select v-model="fv.systemCode" placeholder="选择系统" clearable filterable style="width:100%" @change="onSysChange">
                <el-option v-for="o in fSystemOpts" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="系统名称"><el-input v-model="fv.systemName" disabled /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="问题来源" required>
              <el-select v-model="fv.issueSource" placeholder="选择问题来源" style="width:100%">
                <el-option v-for="o in SRC" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="缺陷类型" required>
              <el-select v-model="fv.defectType" placeholder="选择缺陷类型" style="width:100%">
                <el-option v-for="o in DEF" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="问题频率分类" required>
              <el-select v-model="fv.frequency" placeholder="选择频率分类" style="width:100%">
                <el-option v-for="o in FREQ" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 详细描述 -->
        <el-divider content-position="left">详细描述</el-divider>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="问题描述" required><el-input v-model="fv.issueDescription" type="textarea" :rows="3" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="解决方案"><el-input v-model="fv.solution" type="textarea" :rows="3" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="会议结论"><el-input v-model="fv.meetingConclusion" type="textarea" :rows="3" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="问题处理过程"><el-input v-model="fv.processingSteps" type="textarea" :rows="2" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="所属业务场景"><el-input v-model="fv.businessScenario" /></el-form-item>
          </el-col>
        </el-row>

        <!-- 处置信息 -->
        <el-divider content-position="left">处置信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="问题处置方"><el-input v-model="fv.handler" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="问题处置责任主体"><el-input v-model="fv.responsibleParty" /></el-form-item>
          </el-col>
        </el-row>

        <!-- 关联信息 -->
        <el-divider content-position="left">关联信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="问题关键字索引">
              <div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px">
                <el-tag v-for="(kw, i) in fv.keywords" :key="i" closable @close="rmKw(i)">{{ kw }}</el-tag>
              </div>
              <el-input v-model="kwInput" placeholder="输入关键字后回车添加" @keydown="(e: KeyboardEvent) => { if (e.key === 'Enter') { e.preventDefault(); addKw() } }">
                <template #append><el-button @click="addKw">添加</el-button></template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="关联纪要">
              <el-select v-model="fv.relatedMeetingMinutes" placeholder="选择会议纪要" multiple filterable style="width:100%">
                <el-option v-for="o in fMeetingOpts" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="问题相关表">
              <el-select v-model="fv.relatedTables" placeholder="选择目标表" multiple filterable style="width:100%" @change="onTblChange">
                <el-option v-for="o in fTableOpts" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="问题相关字段">
              <el-select v-model="fv.relatedFields" placeholder="选择字段" multiple filterable style="width:100%" @change="onFieldChange">
                <el-option v-for="o in fFieldOpts" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </UiFormDrawer>

    <!-- Excel导入对话框 -->
    <el-dialog v-model="importDlg" title="Excel批量导入问题" width="800px" :close-on-click-modal="false">
      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        <template #title>
          <div>Excel模板列名：问题编号、问题名称、颗粒度、系统编号、问题来源、缺陷类型、问题频率分类、问题描述、解决方案、会议结论、问题处理过程、所属业务场景、问题处置方、问题处置责任主体、问题关键字索引</div>
          <div>必填列：问题编号、问题名称、问题描述</div>
        </template>
      </el-alert>
      <el-upload :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="(f: any) => handleImportFile(f.raw)" :on-remove="handleImportRemove" :show-file-list="true" drag>
        <el-icon style="font-size:40px;color:#909399"><UploadFilled /></el-icon>
        <div style="margin-top:8px">将 Excel 文件拖到此处，或<em>点击上传</em></div>
        <template #tip><div style="color:#909399;font-size:12px">仅支持 .xlsx / .xls，文件不超过 50 MB，单次不超过 5000 行</div></template>
      </el-upload>
      <div v-if="importResult" style="margin-top:12px">
        <el-alert :type="importResult.failureCount > 0 ? 'warning' : 'success'" :closable="false">
          <template #title>成功 {{ importResult.successCount }} 条，失败 {{ importResult.failureCount }} 条</template>
          <div v-for="error in importResult.rowErrors" :key="`${error.row}-${error.message}`">第 {{ error.row }} 行：{{ error.message }}</div>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="importDlg = false">取消</el-button>
        <el-button type="primary" :disabled="busy || !importFile || scopeProjectId == null" @click="doImport">确认导入</el-button>
      </template>
    </el-dialog>
  </section>
</template>
