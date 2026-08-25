<!--
  用途：数迁资产内容 - 问题清单页
  说明：全流程记录与管理迁移过程各类缺陷及问题，集成多维度复杂条件检索、
        分页展示、详情页精细化编辑、单条新增、Excel批量导入、逻辑删除和回收站。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, FolderOpened, UploadFilled } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import { useAuthStore } from '../../../../stores/auth'
import {
  listIssues, createIssue, updateIssue, deleteIssues,
  listIssueRecycleBin, restoreIssues, purgeIssues, purgeAllIssues,
  getIssueProjectOptions, getIssueSystemOptions, getIssueSystemName,
  getIssueMeetingOptions, getIssueTargetTableOptions, getIssueTargetFieldOptions,
  type IssueRecord, type IssueFormData, type SelectOption
} from '../../../../api/data-migration'

const auth = useAuthStore()
const loading = ref(false), records = ref<IssueRecord[]>([]), total = ref(0), page = ref(1), size = ref(20), selectedIds = ref<number[]>([]), busy = ref(false)
const fProject = ref<number | null>(null), fGranularity = ref(''), fSystem = ref(''), fSource = ref(''), fDefect = ref(''), fFreq = ref(''), fKeyword = ref('')
const projectOpts = ref<SelectOption[]>([])
const cache = { sys: new Map<number, SelectOption[]>(), meet: new Map<number, SelectOption[]>(), tbl: new Map<number, SelectOption[]>(), fld: new Map<number, SelectOption[]>() }
const GRAV = [{ value: 'PROJECT', label: '项目级' }, { value: 'COMPONENT', label: '组件级' }, { value: 'TABLE', label: '表级' }, { value: 'FIELD', label: '字段级' }]
const SRC = [{ value: 'MIGRATION_CHECK', label: '数迁检核' }, { value: 'SIT_FEEDBACK', label: 'SIT测试反馈' }, { value: 'UAT_FEEDBACK', label: 'UAT测试反馈' }, { value: 'DATA_LINE_FEEDBACK', label: '数据线反馈' }, { value: 'EXPERT_FEEDBACK', label: '事业群专家反馈' }, { value: 'RISK_IDENTIFICATION', label: '风险识别' }, { value: 'MIGRATION_RELEASE', label: '数迁投产过程' }]
const DEF = [{ value: 'REQUIREMENT', label: '需求问题' }, { value: 'DESIGN', label: '设计问题' }, { value: 'CODING', label: '编码问题' }, { value: 'DATA_QUALITY', label: '数据质量问题' }, { value: 'CLEANUP', label: '清理补录问题' }, { value: 'BUSINESS', label: '业务问题' }, { value: 'UNDERSTANDING', label: '理解问题' }, { value: 'PERFORMANCE', label: '性能问题' }, { value: 'MASKING', label: '脱敏问题' }, { value: 'OTHER', label: '其他问题' }]
const FREQ = [{ value: 'CLASSIC', label: '经典问题' }, { value: 'HIGH_FREQ', label: '高频重复' }, { value: 'LOW_FREQ', label: '低频偶发' }, { value: 'SINGLE_CASE', label: '单次个案' }]
const drawer = ref(false), saving = ref(false), editing = ref(false), editId = ref<number | null>(null)
const fv = ref({ projectId: null as number | null, issueCode: '', issueName: '', granularity: '', systemCode: '', systemName: '', issueSource: '', defectType: '', issueDescription: '', solution: '', meetingConclusion: '', processingSteps: '', businessScenario: '', handler: '', responsibleParty: '', keywords: [] as string[], relatedMeetingMinutes: [] as number[], frequency: '', relatedTables: [] as number[], relatedFields: [] as number[] })
const fSystemOpts = ref<SelectOption[]>([]), fMeetingOpts = ref<SelectOption[]>([]), fTableOpts = ref<SelectOption[]>([]), fFieldOpts = ref<SelectOption[]>([]), kwInput = ref('')
const filterSysOpts = ref<SelectOption[]>([])
const tab = ref<'list' | 'recycle'>('list'), recLoading = ref(false), recRecords = ref<IssueRecord[]>([]), recTotal = ref(0), recPage = ref(1), recSize = ref(20), recSelected = ref<number[]>([]), recKeyword = ref('')
const importDlg = ref(false), importData = ref<IssueFormData[]>([]), importPreview = ref<any[]>([]), importErrors = ref<string[]>([]), importProject = ref<number | null>(null)
const canCreate = computed(() => auth.hasPermission('data-migration:content:issues:create') || auth.hasPermission('data-migration:write') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const msg = (e: unknown) => e instanceof Error ? e.message : '操作失败'
const cancelled = (e: unknown): boolean => { if (e === 'cancel' || e === 'close') return true; if (e instanceof Error && (e.message === 'cancel' || e.message === 'close')) return true; if (typeof e === 'object' && e !== null && ((e as any).action === 'cancel' || (e as any).action === 'close')) return true; return false }
const sd = (i: IssueRecord): Record<string, any> => i as Record<string, any>
const fmtDate = (v?: string | null) => { if (!v) return '—'; const d = new Date(v); return isNaN(d.getTime()) ? String(v) : `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}` }
const lbl = (o: SelectOption[], v?: string) => o.find(x => x.value === v)?.label ?? v ?? '—'
async function loadOpts() { try { projectOpts.value = (await getIssueProjectOptions()).data.data ?? [] } catch {} }
async function getCached(m: Map<number, SelectOption[]>, id: number, l: (id: number) => Promise<SelectOption[]>): Promise<SelectOption[]> { if (m.has(id)) return m.get(id)!; const r = await l(id); m.set(id, r); return r }
async function loadList() { loading.value = true; selectedIds.value = []; try { const p: any = { page: page.value, size: size.value }; if (fProject.value) p.projectId = fProject.value; if (fGranularity.value) p.granularity = fGranularity.value; if (fSystem.value) p.systemCode = fSystem.value; if (fSource.value) p.issueSource = fSource.value; if (fDefect.value) p.defectType = fDefect.value; if (fFreq.value) p.frequency = fFreq.value; if (fKeyword.value.trim()) p.keyword = fKeyword.value.trim(); const d = (await listIssues(p)).data.data; records.value = d?.records ?? []; total.value = d?.total ?? 0 } catch (e) { ElMessage.error(msg(e)); records.value = []; total.value = 0 } finally { loading.value = false } }
async function loadRecycle() { recLoading.value = true; recSelected.value = []; try { const p: any = { page: recPage.value, size: recSize.value }; if (fProject.value) p.projectId = fProject.value; if (recKeyword.value.trim()) p.keyword = recKeyword.value.trim(); const d = (await listIssueRecycleBin(p)).data.data; recRecords.value = d?.records ?? []; recTotal.value = d?.total ?? 0 } catch (e) { ElMessage.error(msg(e)); recRecords.value = []; recTotal.value = 0 } finally { recLoading.value = false } }
function doSearch() { page.value = 1; loadList() }
function doReset() { fProject.value = null; fGranularity.value = ''; fSystem.value = ''; fSource.value = ''; fDefect.value = ''; fFreq.value = ''; fKeyword.value = ''; page.value = 1; loadList() }
function switchTab(t: string) { t === 'recycle' ? loadRecycle() : loadList() }
function resetForm() { fv.value = { projectId: null, issueCode: '', issueName: '', granularity: '', systemCode: '', systemName: '', issueSource: '', defectType: '', issueDescription: '', solution: '', meetingConclusion: '', processingSteps: '', businessScenario: '', handler: '', responsibleParty: '', keywords: [], relatedMeetingMinutes: [], frequency: '', relatedTables: [], relatedFields: [] }; kwInput.value = ''; fSystemOpts.value = []; fMeetingOpts.value = []; fTableOpts.value = []; fFieldOpts.value = [] }
function openAdd() { editing.value = false; editId.value = null; resetForm(); drawer.value = true }
function openEdit(item: IssueRecord) { editing.value = true; editId.value = item.id; const d = sd(item); const kw = typeof d.keywords === 'string' ? d.keywords.split(',').map((v: string) => v.trim()).filter(Boolean) : (Array.isArray(d.keywords) ? [...d.keywords] : []); fv.value = { projectId: item.project_id, issueCode: item.asset_code, issueName: item.asset_name, granularity: d.granularity ?? '', systemCode: d.systemCode ?? '', systemName: d.systemName ?? '', issueSource: d.issueSource ?? '', defectType: d.defectType ?? '', issueDescription: d.issueDescription ?? '', solution: d.solution ?? '', meetingConclusion: d.meetingConclusion ?? '', processingSteps: d.processingSteps ?? '', businessScenario: d.businessScenario ?? '', handler: d.handler ?? '', responsibleParty: d.responsibleParty ?? '', keywords: kw, relatedMeetingMinutes: Array.isArray(d.relatedMeetingMinutes) ? [...d.relatedMeetingMinutes] : [], frequency: d.frequency ?? '', relatedTables: Array.isArray(d.relatedTables) ? [...d.relatedTables] : [], relatedFields: Array.isArray(d.relatedFields) ? [...d.relatedFields] : [] }; if (fv.value.projectId) loadFormOpts(fv.value.projectId); drawer.value = true }
async function loadFormOpts(pid: number) { const [s, m, t] = await Promise.all([getCached(cache.sys, pid, id => getIssueSystemOptions(id).then(r => r.data.data ?? [])), getCached(cache.meet, pid, id => getIssueMeetingOptions(id).then(r => r.data.data ?? [])), getCached(cache.tbl, pid, id => getIssueTargetTableOptions(id).then(r => r.data.data ?? []))]); fSystemOpts.value = s; fMeetingOpts.value = m; fTableOpts.value = t }
async function onProjectChange(pid: number | null) { fv.value.systemCode = ''; fv.value.systemName = ''; fv.value.relatedMeetingMinutes = []; fv.value.relatedTables = []; fv.value.relatedFields = []; fSystemOpts.value = []; fMeetingOpts.value = []; fTableOpts.value = []; fFieldOpts.value = []; if (pid) await loadFormOpts(pid) }
async function onSysChange(code: string) { fv.value.systemName = ''; if (code?.trim()) try { fv.value.systemName = (await getIssueSystemName(code.trim())).data.data ?? '' } catch {} }
async function onTblChange(ids: number[]) {
  const oldIds = fv.value.relatedTables
  const removedIds = oldIds.filter(id => !ids.includes(id))
  // 检查删除的表是否有关联字段
  if (removedIds.length > 0 && fv.value.relatedFields.length > 0) {
    try {
      await ElMessageBox.confirm(
        `删除表将同步删除所有关联字段（${fv.value.relatedFields.length} 个）。是否继续？`,
        '确认删除',
        { type: 'warning' }
      )
      // 同步删除所有关联字段
      fv.value.relatedFields = []
    } catch {
      // 用户取消，恢复表选择
      fv.value.relatedTables = [...oldIds]
      return
    }
  }
  fv.value.relatedTables = ids
  fv.value.relatedFields = []
  fFieldOpts.value = []
  for (const id of ids) { const r = await getCached(cache.fld, id, tid => getIssueTargetFieldOptions(tid).then(res => res.data.data ?? [])); fFieldOpts.value.push(...r) }
}

async function onFieldChange(fieldIds: number[]) {
  // 检查新添加的字段是否需要自动关联其所属表
  const newFieldIds = fieldIds.filter(id => !fv.value.relatedFields.includes(id))
  if (newFieldIds.length > 0 && fv.value.relatedTables.length === 0) {
    try {
      await ElMessageBox.confirm(
        `当前未关联任何表，是否先关联表再选择字段？`,
        '关联表',
        { type: 'info' }
      )
      // 用户确认，不修改字段选择
    } catch {
      // 用户取消，移除新添加的字段
      fv.value.relatedFields = fv.value.relatedFields.filter(id => !newFieldIds.includes(id))
      return
    }
  }
  fv.value.relatedFields = fieldIds
  // 检查删除字段后是否需要提示删除空表
  const removedFieldIds = fv.value.relatedFields.filter(id => !fieldIds.includes(id))
  if (removedFieldIds.length > 0 && fv.value.relatedFields.length === 0 && fv.value.relatedTables.length > 0) {
    try {
      await ElMessageBox.confirm(
        `所有字段已删除，是否同时删除关联的表？`,
        '删除表',
        { type: 'info' }
      )
      // 删除所有表
      fv.value.relatedTables = []
      fFieldOpts.value = []
    } catch {
      // 用户取消，不做操作
    }
  }
}
function addKw() { const v = kwInput.value.trim(); if (v && !fv.value.keywords.includes(v)) fv.value.keywords.push(v); kwInput.value = '' }
function rmKw(i: number) { fv.value.keywords.splice(i, 1) }
function validate(): string | null { if (!fv.value.projectId) return '请选择项目'; if (!fv.value.issueCode.trim()) return '请输入问题编号'; if (!fv.value.issueName.trim()) return '请输入问题名称'; if (!fv.value.issueDescription.trim()) return '请输入问题描述'; if (!fv.value.granularity) return '请选择颗粒度'; if (!fv.value.issueSource) return '请选择问题来源'; if (!fv.value.defectType) return '请选择缺陷类型'; if (!fv.value.frequency) return '请选择问题频率分类'; return null }
async function doSave() { const err = validate(); if (err) { ElMessage.warning(err); return }; saving.value = true; try { const body: IssueFormData = { projectId: fv.value.projectId!, issueCode: fv.value.issueCode.trim(), issueName: fv.value.issueName.trim(), granularity: fv.value.granularity || undefined, systemCode: fv.value.systemCode || undefined, systemName: fv.value.systemName || undefined, issueSource: fv.value.issueSource || undefined, defectType: fv.value.defectType || undefined, issueDescription: fv.value.issueDescription.trim() || undefined, solution: fv.value.solution.trim() || undefined, meetingConclusion: fv.value.meetingConclusion.trim() || undefined, processingSteps: fv.value.processingSteps.trim() || undefined, businessScenario: fv.value.businessScenario.trim() || undefined, handler: fv.value.handler.trim() || undefined, responsibleParty: fv.value.responsibleParty.trim() || undefined, keywords: fv.value.keywords.length ? fv.value.keywords : undefined, relatedMeetingMinutes: fv.value.relatedMeetingMinutes.length ? fv.value.relatedMeetingMinutes : undefined, relatedMeetingMinuteNames: fv.value.relatedMeetingMinutes.length ? fv.value.relatedMeetingMinutes.map(id => fMeetingOpts.value.find(o => o.value === id)?.label ?? String(id)).join(', ') : undefined, frequency: fv.value.frequency || undefined, relatedTables: fv.value.relatedTables.length ? fv.value.relatedTables : undefined, relatedTableNames: fv.value.relatedTables.length ? fv.value.relatedTables.map(id => fTableOpts.value.find(o => o.value === id)?.label ?? String(id)).join(', ') : undefined, relatedFields: fv.value.relatedFields.length ? fv.value.relatedFields : undefined, relatedFieldNames: fv.value.relatedFields.length ? fv.value.relatedFields.map(id => fFieldOpts.value.find(o => o.value === id)?.label ?? String(id)).join(', ') : undefined }; if (editing.value && editId.value) { await updateIssue(editId.value, body); ElMessage.success('更新成功') } else { await createIssue(body); ElMessage.success('新增成功') }; drawer.value = false; loadList() } catch (e) { ElMessage.error(msg(e)) } finally { saving.value = false } }
async function doDelete() { if (!selectedIds.value.length) return; try { await ElMessageBox.confirm(`确认将选中的 ${selectedIds.value.length} 条问题移入回收站？`, '移入回收站', { type: 'warning' }); busy.value = true; await deleteIssues(selectedIds.value); ElMessage.success('已移入回收站'); loadList() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doDeleteOne(item: IssueRecord) { try { await ElMessageBox.confirm(`确认将"${item.asset_name}"移入回收站？`, '移入回收站', { type: 'warning' }); busy.value = true; await deleteIssues([item.id]); ElMessage.success('已移入回收站'); loadList() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doRestore() { if (!recSelected.value.length) return; try { await ElMessageBox.confirm(`确认恢复选中的 ${recSelected.value.length} 条问题？`, '恢复问题'); busy.value = true; await restoreIssues(recSelected.value); ElMessage.success('恢复成功'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doRestoreOne(item: IssueRecord) { try { await ElMessageBox.confirm(`确认恢复"${item.asset_name}"？`, '恢复问题'); busy.value = true; await restoreIssues([item.id]); ElMessage.success('恢复成功'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doPurge() { if (!recSelected.value.length) return; try { await ElMessageBox.confirm(`确认彻底销毁选中的 ${recSelected.value.length} 条问题？此操作不可恢复。`, '彻底销毁', { type: 'error', confirmButtonText: '彻底销毁' }); busy.value = true; await purgeIssues(recSelected.value); ElMessage.success('清理完成'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doPurgeOne(item: IssueRecord) { try { await ElMessageBox.confirm(`确认彻底销毁"${item.asset_name}"？此操作不可恢复。`, '彻底销毁', { type: 'error', confirmButtonText: '彻底销毁' }); busy.value = true; await purgeIssues([item.id]); ElMessage.success('清理完成'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
async function doPurgeAll() { try { await ElMessageBox.confirm('确认清空回收站？此操作不可恢复。', '清空回收站', { type: 'error', confirmButtonText: '清空' }); busy.value = true; await purgeAllIssues(); ElMessage.success('回收站已清空'); loadRecycle() } catch (e) { if (!cancelled(e)) ElMessage.error(msg(e)) } finally { busy.value = false } }
function openImport() { importData.value = []; importPreview.value = []; importErrors.value = []; importProject.value = null; importDlg.value = true }
function handleImportFile(file: File) { importErrors.value = []; importData.value = []; importPreview.value = []; file.arrayBuffer().then(buf => { const wb = XLSX.read(buf, { type: 'array' }); const rows = XLSX.utils.sheet_to_json<Record<string, any>>(wb.Sheets[wb.SheetNames[0]]); if (!rows.length) { importErrors.value = ['Excel文件为空']; return } if (rows.length > 500) { importErrors.value = ['单次导入不超过500条']; return }; const mg: Record<string, string> = { '项目级': 'PROJECT', '组件级': 'COMPONENT', '表级': 'TABLE', '字段级': 'FIELD' }; const ms: Record<string, string> = { '数迁检核': 'MIGRATION_CHECK', 'SIT测试反馈': 'SIT_FEEDBACK', 'UAT测试反馈': 'UAT_FEEDBACK', '数据线反馈': 'DATA_LINE_FEEDBACK', '事业群专家反馈': 'EXPERT_FEEDBACK', '风险识别': 'RISK_IDENTIFICATION', '数迁投产过程': 'MIGRATION_RELEASE' }; const md: Record<string, string> = { '需求问题': 'REQUIREMENT', '设计问题': 'DESIGN', '编码问题': 'CODING', '数据质量问题': 'DATA_QUALITY', '清理补录问题': 'CLEANUP', '业务问题': 'BUSINESS', '理解问题': 'UNDERSTANDING', '性能问题': 'PERFORMANCE', '脱敏问题': 'MASKING', '其他问题': 'OTHER' }; const mf: Record<string, string> = { '经典问题': 'CLASSIC', '高频重复': 'HIGH_FREQ', '低频偶发': 'LOW_FREQ', '单次个案': 'SINGLE_CASE' }; const issues: IssueFormData[] = []; const preview: any[] = []; const errs: string[] = []; rows.forEach((row: Record<string, any>, i: number) => { const n = i + 2; const code = String(row['问题编号'] ?? '').trim(); const name = String(row['问题名称'] ?? '').trim(); const desc = String(row['问题描述'] ?? '').trim(); if (!code) { errs.push(`第${n}行: 问题编号为空`); return } if (!name) { errs.push(`第${n}行: 问题名称为空`); return } if (!desc) { errs.push(`第${n}行: 问题描述为空`); return }; const gv = mg[String(row['颗粒度'] ?? '').trim()] || ''; const sv = ms[String(row['问题来源'] ?? '').trim()] || ''; const dv = md[String(row['缺陷类型'] ?? '').trim()] || ''; const fq = mf[String(row['问题频率分类'] ?? '').trim()] || ''; const grav = String(row['颗粒度'] ?? '').trim(); const src = String(row['问题来源'] ?? '').trim(); const def = String(row['缺陷类型'] ?? '').trim(); const frq = String(row['问题频率分类'] ?? '').trim(); if (grav && !gv) { errs.push(`第${n}行: 颗粒度"${grav}"无效`); return } if (src && !sv) { errs.push(`第${n}行: 问题来源"${src}"无效`); return } if (def && !dv) { errs.push(`第${n}行: 缺陷类型"${def}"无效`); return } if (frq && !fq) { errs.push(`第${n}行: 问题频率分类"${frq}"无效`); return }; const sysCode = String(row['系统编号'] ?? '').trim() || undefined; const kws = String(row['问题关键字索引'] ?? '').trim() ? String(row['问题关键字索引']).split(/[,，]/).map((s: string) => s.trim()).filter(Boolean) : undefined; issues.push({ projectId: 0, issueCode: code, issueName: name, granularity: gv || undefined, systemCode: sysCode, issueSource: sv || undefined, defectType: dv || undefined, issueDescription: desc, solution: String(row['解决方案'] ?? '').trim() || undefined, meetingConclusion: String(row['会议结论'] ?? '').trim() || undefined, processingSteps: String(row['问题处理过程'] ?? '').trim() || undefined, businessScenario: String(row['所属业务场景'] ?? '').trim() || undefined, handler: String(row['问题处置方'] ?? '').trim() || undefined, responsibleParty: String(row['问题处置责任主体'] ?? '').trim() || undefined, keywords: kws, frequency: fq || undefined }); preview.push({ n, code, name, grav: grav || '—', src: src || '—', def: def || '—', frq: frq || '—', sys: sysCode || '—' }) }); importErrors.value = errs; importData.value = issues; importPreview.value = preview }).catch(e => { importErrors.value = ['Excel解析失败: ' + msg(e)] }); return false }
async function doImport() { if (!importProject.value) { ElMessage.warning('请选择项目'); return } if (!importData.value.length) { ElMessage.warning('无有效数据'); return }; busy.value = true; try { for (const item of importData.value) { item.projectId = importProject.value; await createIssue(item) }; ElMessage.success(`成功导入 ${importData.value.length} 条问题`); importDlg.value = false; loadList() } catch (e) { ElMessage.error(msg(e)) } finally { busy.value = false } }
onMounted(async () => { await loadOpts(); loadList(); getIssueSystemOptions().then(r => { filterSysOpts.value = r.data.data ?? [] }).catch(() => {}) })
</script>

<template>
  <section class="dm-page-root">
    <UiPageHeader title="问题清单">
      <template #actions>
        <el-button v-if="canCreate" type="primary" :disabled="loading || busy" @click="openAdd"><el-icon><Plus /></el-icon>新增问题</el-button>
        <el-button v-if="canCreate" type="primary" plain :disabled="loading || busy" @click="openImport"><el-icon><UploadFilled /></el-icon>Excel导入</el-button>
      </template>
    </UiPageHeader>
    <el-tabs v-model="tab" @tab-click="(t: any) => switchTab(t.paneName)">
      <el-tab-pane label="问题清单" name="list">
        <UiToolbar>
          <el-select v-model="fProject" placeholder="选择项目" clearable filterable style="width:180px" @change="doSearch">
            <el-option v-for="o in projectOpts" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
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
          <el-table-column type="selection" width="48" />
          <el-table-column label="问题编号" prop="asset_code" width="130" show-overflow-tooltip />
          <el-table-column label="问题名称" prop="asset_name" min-width="160" show-overflow-tooltip />
          <el-table-column label="项目" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ row.project_name ?? '—' }}</template></el-table-column>
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
              <el-button link type="primary" size="small" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-button link type="danger" size="small" @click="doDeleteOne(row)"><el-icon><Delete /></el-icon>删除</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiEmptyState v-if="!loading && records.length === 0" description="暂无问题数据" icon="search" />
        <UiPagination v-if="total > 0" :page="page" :page-size="size" :total="total" :page-sizes="[10,20,50,100]" @update:page-size="(v: number) => { size = v; page = 1; loadList() }" @update:page="(v: number) => { page = v; loadList() }" />
      </el-tab-pane>
      <el-tab-pane label="回收站" name="recycle">
        <UiToolbar>
          <el-input v-model="recKeyword" clearable placeholder="搜索问题编号/名称" style="width:240px" @keyup.enter="loadRecycle"><template #prefix><el-icon><Search /></el-icon></template></el-input>
          <template #actions>
            <el-button :disabled="recLoading" @click="loadRecycle"><el-icon><Refresh /></el-icon>刷新</el-button>
            <el-button v-if="recSelected.length" type="warning" plain :disabled="busy" @click="doRestore"><el-icon><FolderOpened /></el-icon>恢复({{ recSelected.length }})</el-button>
            <el-button v-if="recSelected.length" type="danger" plain :disabled="busy" @click="doPurge"><el-icon><Delete /></el-icon>彻底销毁({{ recSelected.length }})</el-button>
            <el-button v-if="recTotal > 0" type="danger" :disabled="busy" @click="doPurgeAll">清空回收站</el-button>
          </template>
        </UiToolbar>
        <UiDataTable :data="recRecords" :loading="recLoading" row-key="id" @selection-change="(r: IssueRecord[]) => recSelected = r.map(x => x.id)">
          <el-table-column type="selection" width="48" />
          <el-table-column label="问题编号" prop="asset_code" width="130" />
          <el-table-column label="问题名称" prop="asset_name" min-width="160" show-overflow-tooltip />
          <el-table-column label="项目" min-width="120"><template #default="{ row }">{{ row.project_name ?? '—' }}</template></el-table-column>
          <el-table-column label="删除时间" width="150"><template #default="{ row }">{{ fmtDate(row.deleted_at) }}</template></el-table-column>
          <el-table-column label="删除人" width="100"><template #default="{ row }">{{ row.deleted_by_name ?? '—' }}</template></el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="warning" size="small" :disabled="busy" @click="doRestoreOne(row)"><el-icon><FolderOpened /></el-icon>恢复</el-button>
              <el-button link type="danger" size="small" :disabled="busy" @click="doPurgeOne(row)"><el-icon><Delete /></el-icon>销毁</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiEmptyState v-if="!recLoading && recRecords.length === 0" description="回收站为空" icon="delete" />
        <UiPagination v-if="recTotal > 0" :page="recPage" :page-size="recSize" :total="recTotal" :page-sizes="[10,20,50,100]" @update:page-size="(v: number) => { recSize = v; recPage = 1; loadRecycle() }" @update:page="(v: number) => { recPage = v; loadRecycle() }" />
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑抽屉 -->
    <UiFormDrawer v-model="drawer" :title="editing ? '编辑问题' : '新增问题'" :loading="saving" width="min(900px, calc(100vw - 24px))" @submit="doSave">
      <el-form label-position="top">
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目" required>
              <el-select v-model="fv.projectId" placeholder="选择项目" filterable style="width:100%" @change="onProjectChange">
                <el-option v-for="o in projectOpts" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
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
      <el-form-item label="所属项目" required>
        <el-select v-model="importProject" placeholder="选择导入到的项目" filterable style="width:100%">
          <el-option v-for="o in projectOpts" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-upload :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="(f: any) => handleImportFile(f.raw)" :show-file-list="true" drag>
        <el-icon style="font-size:40px;color:#909399"><UploadFilled /></el-icon>
        <div style="margin-top:8px">将 Excel 文件拖到此处，或<em>点击上传</em></div>
        <template #tip><div style="color:#909399;font-size:12px">仅支持 .xlsx / .xls，单次不超过500条，文件仅在前端解析不上传服务器</div></template>
      </el-upload>
      <div v-if="importErrors.length" style="margin-top:12px">
        <el-alert type="error" :closable="false">
          <div v-for="e in importErrors" :key="e">{{ e }}</div>
        </el-alert>
      </div>
      <div v-if="importPreview.length" style="margin-top:12px">
        <div style="margin-bottom:8px;font-weight:500">预览（{{ importPreview.length }} 条）</div>
        <el-table :data="importPreview.slice(0, 10)" size="small" max-height="300" border>
          <el-table-column prop="n" label="行号" width="60" />
          <el-table-column prop="code" label="问题编号" width="120" />
          <el-table-column prop="name" label="问题名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="grav" label="颗粒度" width="80" />
          <el-table-column prop="src" label="问题来源" width="110" />
          <el-table-column prop="def" label="缺陷类型" width="110" />
          <el-table-column prop="frq" label="频率" width="90" />
        </el-table>
        <div v-if="importPreview.length > 10" style="color:#909399;font-size:12px;margin-top:4px">仅显示前10条...</div>
      </div>
      <template #footer>
        <el-button @click="importDlg = false">取消</el-button>
        <el-button type="primary" :disabled="busy || !importData.length || !importProject" @click="doImport">确认导入 ({{ importData.length }})</el-button>
      </template>
    </el-dialog>
  </section>
</template>
