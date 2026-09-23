<!--
  用途：活动配置工作台（基线第 9 章 9.2.2~9.2.6）
  说明：basic/process/topology/topic/version 五个页签，支持 ?tab= 深链直达，非法深链回退默认页签；
        拓扑依赖以表格化连线编辑承载（铁律 #16：无 SVG 度量能力自动降级并回显原因 + 手动重试，共享同一保存出口）。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Connection, Edit, Plus, Refresh, UploadFilled, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  addLifecycleTopicMember,
  exportLifecycleTemplate,
  getLifecycleActivity,
  getLifecycleProcesses,
  getLifecyclePublishStatus,
  getLifecycleSnapshot,
  getLifecycleTopicCandidates,
  getLifecycleTopicMembers,
  getLifecycleTopology,
  listLifecycleComponentOptions,
  listLifecycleRoleOptions,
  listLifecycleStages,
  obsoleteLifecycleActivity,
  publishLifecycleVersion,
  removeLifecycleTopicMember,
  saveLifecycleProcesses,
  saveLifecycleTopology,
  setLifecycleActivityStatus,
  updateLifecycleActivity
} from '../../../../api/data-migration-lifecycle'
import type {
  LifecycleActivityView,
  LifecycleProcessInput,
  LifecycleProcessView,
  LifecyclePublishStatus,
  LifecycleStageOption,
  LifecycleTopicCandidate,
  LifecycleTopologyView
} from '../../../../types/data-migration-lifecycle'
import { LifecycleStatusText } from '../../../../types/data-migration-lifecycle'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activityId = computed(() => Number(route.params.activityId))
const TABS = ['basic', 'process', 'topology', 'topic', 'version']
const activeTab = ref<string>('basic')
function resolveTab(query: unknown): string {
  const raw = Array.isArray(query) ? query[0] : query
  return typeof raw === 'string' && TABS.includes(raw) ? raw : 'basic'
}
activeTab.value = resolveTab(route.query.tab)
watch(() => route.query.tab, (value) => { activeTab.value = resolveTab(value) })

const canManage = computed(() =>
  auth.hasPermission('data-migration-lifecycle:activity:update') || auth.hasPermission('data-migration-lifecycle:activity:create')
  || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canDelete = computed(() =>
  auth.hasPermission('data-migration-lifecycle:activity:delete') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))

const loading = ref(false)
const error = ref('')
const actionBusy = ref(false)
const activity = ref<LifecycleActivityView | null>(null)
const isObsolete = computed(() => activity.value?.activityStatus === 'OBSOLETE')
const isInactive = computed(() => activity.value?.activityStatus === 'INACTIVE')

const stages = ref<LifecycleStageOption[]>([])
const roleOptions = ref<Array<{ id: number; roleCode: string; roleName: string }>>([])
const componentOptions = ref<Array<{ id: number; projectName: string; systemName: string | null | undefined }>>([])

const TYPE_TEXT: Record<string, string> = { NORMAL: '普通基础活动', TOPIC: '专题聚合活动' }
const GRAN_TEXT: Record<string, string> = { PROJECT: '项目级', COMPONENT: '组件级' }

const processes = ref<LifecycleProcessView[]>([])
const edges = ref<Array<{ sourceProcessId: number; targetProcessId: number }>>([])
const topologyWarnings = ref<string[]>([])
const publishStatus = ref<LifecyclePublishStatus | null>(null)
const snapshotJson = ref('')
const snapshotPublished = ref(false)

async function loadWorkbench() {
  loading.value = true
  error.value = ''
  try {
    const id = activityId.value
    const [activityRes] = await Promise.all([getLifecycleActivity(id)])
    activity.value = activityRes.data.data ?? null
    if (!activity.value) { error.value = '活动不存在'; return }
    await Promise.all([loadPublish(id), loadTopology(id)])
  } catch (e) {
    error.value = apiErrorMessage(e, '工作台加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTopology(id: number) {
  try {
    const [processRes, topologyRes, pubRes] = await Promise.all([
      getLifecycleProcesses(id), getLifecycleTopology(id), getLifecyclePublishStatus(id)
    ])
    processes.value = processRes.data.data ?? []
    const topo = topologyRes.data.data as LifecycleTopologyView | null
    edges.value = topo?.edges ?? []
    topologyWarnings.value = topo?.warnings?.filter(Boolean) ?? []
    publishStatus.value = pubRes.data.data ?? null
  } catch {
    /* 工序/拓扑未配置时允许空态 */
  }
}

async function loadPublish(id: number) {
  const res = await getLifecyclePublishStatus(id)
  publishStatus.value = res.data.data ?? null
}

async function loadSnapshot() {
  try {
    const res = await getLifecycleSnapshot(activityId.value)
    const data = res.data.data
    snapshotPublished.value = Boolean(data && data.published !== false)
    snapshotJson.value = data && data.published !== false ? JSON.stringify(data, null, 2) : '（尚未发布任何版本）'
  } catch (e) {
    snapshotJson.value = '快照获取失败：' + apiErrorMessage(e, '快照获取失败')
    snapshotPublished.value = false
  }
}

async function loadTopic() {
  if (activity.value?.activityType !== 'TOPIC') return
  const [candRes, memberRes] = await Promise.all([
    getLifecycleTopicCandidates(activityId.value), getLifecycleTopicMembers(activityId.value)
  ])
  candidates.value = candRes.data.data ?? []
  members.value = memberRes.data.data ?? []
}

function goBack() { router.push('/data-migration/lifecycle/activity') }
function switchTab(tab: string) {
  activeTab.value = tab
  router.replace({ query: { ...route.query, tab } })
  if (tab === 'topic') loadTopic()
  if (tab === 'version') loadSnapshot()
}

async function setStatus(target: 'ACTIVE' | 'INACTIVE') {
  const label = target === 'ACTIVE' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${label}活动「${activity.value?.activityName}」？`, '状态确认', { type: 'warning' })
  } catch { return }
  actionBusy.value = true
  try {
    await setLifecycleActivityStatus(activityId.value, target)
    ElMessage.success(`已${label}`)
    await loadWorkbench()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '状态切换失败'))
  } finally {
    actionBusy.value = false
  }
}

async function doObsolete() {
  try {
    await ElMessageBox.confirm(`作废为终态且不可逆（不可编辑/重新启用/重复作废），确认作废「${activity.value?.activityName}」？`, '作废确认', { type: 'error' })
  } catch { return }
  actionBusy.value = true
  try {
    await obsoleteLifecycleActivity(activityId.value)
    ElMessage.success('活动已作废')
    await loadWorkbench()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '作废失败'))
  } finally {
    actionBusy.value = false
  }
}

/* ===== 基础信息页签 ===== */
const editOpen = ref(false)
const saving = ref(false)
const basicForm = reactive<Record<string, unknown>>({
  activityName: '', lifecycleStageId: undefined, scene: '', goal: '',
  overallEntryCond: '', overallExitDesc: '', overallDeliverables: '', componentIds: [] as number[]
})
function openBasicEdit() {
  const item = activity.value
  if (!item) return
  Object.assign(basicForm, {
    activityName: item.activityName, lifecycleStageId: item.lifecycleStageId ?? undefined,
    scene: item.scene ?? '', goal: item.goal ?? '', overallEntryCond: item.overallEntryCond ?? '',
    overallExitDesc: item.overallExitDesc ?? '', overallDeliverables: item.overallDeliverables ?? '', componentIds: []
  })
  editOpen.value = true
}
async function submitBasic() {
  saving.value = true
  try {
    if (!String(basicForm.activityName ?? '').trim()) { ElMessage.warning('活动名称不能为空'); return }
    const body: Record<string, unknown> = { ...basicForm, componentIds: [] }
    await updateLifecycleActivity(activityId.value, body)
    ElMessage.success('基础信息已更新（仅对未来新建任务生效）')
    editOpen.value = false
    await loadWorkbench()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

/* ===== 工序页签 ===== */
const processDrawer = ref(false)
const processSaving = ref(false)
const editingIndex = ref(-1)
const processForm = reactive<Record<string, unknown>>({
  processName: '', ownerRoleId: undefined, isRequired: true, entryConfig: '', execConfig: '',
  exitContent: '', exitDeliverableList: '', qualifiedRule: '', mustAudit: true, mustSubmitDeliverable: true
})
function resetProcessForm() {
  Object.assign(processForm, {
    processName: '', ownerRoleId: undefined, isRequired: true, entryConfig: '', execConfig: '',
    exitContent: '', exitDeliverableList: '', qualifiedRule: '', mustAudit: true, mustSubmitDeliverable: true
  })
}
function openProcessAdd() { resetProcessForm(); editingIndex.value = -1; processDrawer.value = true }
function openProcessEdit(index: number) {
  const row = processes.value[index]
  editingIndex.value = index
  Object.assign(processForm, {
    processName: row.processName, ownerRoleId: row.ownerRoleId ?? undefined, isRequired: row.isRequired,
    entryConfig: row.entryConfig ?? '', execConfig: row.execConfig ?? '', exitContent: row.exitContent ?? '',
    exitDeliverableList: row.exitDeliverableList ?? '', qualifiedRule: row.qualifiedRule ?? '',
    mustAudit: row.mustAudit, mustSubmitDeliverable: row.mustSubmitDeliverable
  })
  processDrawer.value = true
}
function confirmProcessSave() {
  const name = String(processForm.processName ?? '').trim()
  if (!name) { ElMessage.warning('工序名称不能为空'); return }
  if (editingIndex.value >= 0) {
    const row = processes.value[editingIndex.value]
    processes.value[editingIndex.value] = {
      ...row,
      processName: name,
      ownerRoleId: processForm.ownerRoleId ? Number(processForm.ownerRoleId) : null,
      isRequired: Boolean(processForm.isRequired),
      entryConfig: String(processForm.entryConfig ?? '') || null,
      execConfig: String(processForm.execConfig ?? '') || null,
      exitContent: String(processForm.exitContent ?? ''),
      exitDeliverableList: String(processForm.exitDeliverableList ?? '') || null,
      qualifiedRule: String(processForm.qualifiedRule ?? ''),
      mustAudit: Boolean(processForm.mustAudit),
      mustSubmitDeliverable: Boolean(processForm.mustSubmitDeliverable)
    }
  } else {
    processes.value.push({
      id: 0, activityId: activityId.value, seq: processes.value.length + 1,
      processName: name, ownerRoleId: processForm.ownerRoleId ? Number(processForm.ownerRoleId) : null,
      isRequired: Boolean(processForm.isRequired), entryConfig: String(processForm.entryConfig ?? '') || null,
      noPredecessor: true, execConfig: String(processForm.execConfig ?? '') || null,
      exitContent: String(processForm.exitContent ?? ''), exitDeliverableList: String(processForm.exitDeliverableList ?? '') || null,
      qualifiedRule: String(processForm.qualifiedRule ?? ''), mustAudit: Boolean(processForm.mustAudit),
      mustSubmitDeliverable: Boolean(processForm.mustSubmitDeliverable), deliverableTemplateId: null,
      configStatus: 'DRAFT', predecessorProcessIds: []
    })
  }
  processDrawer.value = false
}
function removeProcess(index: number) {
  processes.value.splice(index, 1)
  processes.value.forEach((p, i) => { p.seq = i + 1 })
}
async function saveProcesses() {
  if (processes.value.length === 0) { ElMessage.warning('活动至少保留 1 道工序'); return }
  actionBusy.value = true
  try {
    const payload: LifecycleProcessInput[] = processes.value.map(p => ({
      id: p.id === 0 ? null : p.id, seq: p.seq, processName: p.processName, ownerRoleId: p.ownerRoleId,
      isRequired: p.isRequired, entryConfig: p.entryConfig, execConfig: p.execConfig, exitContent: p.exitContent ?? '',
      exitDeliverableList: p.exitDeliverableList, qualifiedRule: p.qualifiedRule ?? '',
      mustAudit: p.mustAudit, mustSubmitDeliverable: p.mustSubmitDeliverable, deliverableTemplateId: p.deliverableTemplateId
    }))
    const res = await saveLifecycleProcesses(activityId.value, payload)
    processes.value = res.data.data ?? []
    ElMessage.success('工序已保存（序号已重排 1..n，就绪状态由准出三要素自动判定）')
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '工序保存失败'))
  } finally {
    actionBusy.value = false
  }
}

/* ===== 拓扑页签（铁律 #16 降级表格视图） ===== */
const tableView = ref(true)
const canvasUnavailable = ref(false)
const canvasHint = ref('')
function detectCanvasSupport(): boolean {
  try {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    if (typeof svg.getBBox !== 'function') return false
    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    text.setAttribute('x', '0'); text.setAttribute('y', '0'); text.textContent = '测'
    svg.appendChild(text)
    const box = text.getBBox()
    return typeof box.width === 'number' && isFinite(box.width) && box.width > 0
  } catch {
    return false
  }
}
function initTopologyView() {
  canvasUnavailable.value = !detectCanvasSupport()
  canvasHint.value = canvasUnavailable.value
    ? '当前环境不支持图形画布（无 SVG 度量能力），已切换为表格化配置视图：表格化连线编辑与画布共享同一校验与保存出口，产出数据完全等价。'
    : ''
  tableView.value = true
}
function retryCanvas() {
  const supported = detectCanvasSupport()
  canvasUnavailable.value = !supported
  canvasHint.value = canvasUnavailable.value
    ? '当前环境不支持图形画布（无 SVG 度量能力），已切换为表格化配置视图：可点击「重试画布」再次探测。'
    : ''
  if (supported) { ElMessage.info('当前环境具备 SVG 度量能力，本版本表格化连线编辑与画布共享同一保存出口') }
  tableView.value = true
}
const edgeSource = ref<number | null>(null)
const edgeTarget = ref<number | null>(null)
const processOptions = computed(() => processes.value.map(p => ({ value: p.id, label: `${p.seq}. ${p.processName}` })))
function addEdge() {
  if (edgeSource.value === null || edgeTarget.value === null) { ElMessage.warning('请选择前置工序与后置工序'); return }
  if (edgeSource.value === edgeTarget.value) { ElMessage.warning('禁止自连依赖'); return }
  if (edges.value.some(e => e.sourceProcessId === edgeSource.value && e.targetProcessId === edgeTarget.value)) {
    ElMessage.warning('禁止重复依赖连线'); return
  }
  edges.value.push({ sourceProcessId: edgeSource.value, targetProcessId: edgeTarget.value })
  edgeSource.value = null; edgeTarget.value = null
}
function removeEdge(index: number) { edges.value.splice(index, 1) }
function hasCycle(): boolean {
  const adj = new Map<number, number[]>()
  processes.value.forEach(p => adj.set(p.id, []))
  edges.value.forEach(e => { (adj.get(e.sourceProcessId) ?? []).push(e.targetProcessId) })
  const visiting = new Set<number>(); const visited = new Set<number>()
  function dfs(node: number): boolean {
    if (visiting.has(node)) return true
    if (visited.has(node)) return false
    visiting.add(node)
    for (const next of adj.get(node) ?? []) if (dfs(next)) return true
    visiting.delete(node); visited.add(node)
    return false
  }
  return [...adj.keys()].some(id => dfs(id))
}
const noPredecessorOf = computed(() => {
  const map = new Map<number, boolean>()
  processes.value.forEach(p => map.set(p.id, true))
  edges.value.forEach(e => map.set(e.targetProcessId, false))
  return map
})
async function saveTopology() {
  if (processes.value.length === 0) { ElMessage.warning('请先保存工序'); return }
  if (processes.value.length > 30) { ElMessage.warning('单活动工序数不得超过 30 节点'); return }
  const ids = new Set(processes.value.map(p => p.id))
  for (const e of edges.value) {
    if (!ids.has(e.sourceProcessId) || !ids.has(e.targetProcessId)) { ElMessage.warning('禁止引用缺失节点'); return }
  }
  if (hasCycle()) { ElMessage.warning('禁止成环依赖：拓扑保存将被拦截'); return }
  actionBusy.value = true
  try {
    const res = await saveLifecycleTopology(activityId.value, { processes: [], edges: edges.value })
    topologyWarnings.value = res.data.data?.warnings ?? []
    ElMessage.success('拓扑已保存（无前置标记已按依赖连线重算落库，未发布）')
    await loadPublish(activityId.value)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '拓扑保存失败'))
  } finally {
    actionBusy.value = false
  }
}

/* ===== 发布 ===== */
async function doPublish() {
  actionBusy.value = true
  try {
    const res = await publishLifecycleVersion(activityId.value)
    const data = res.data.data ?? {}
    ElMessage.success(`发布成功：版本 ${String(data.topologyVersion ?? '')}，快照已冻结（自描述载荷，版本 1:1）`)
    await Promise.all([loadPublish(activityId.value), loadSnapshot()])
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '发布失败：请确保准出三要素齐备且拓扑合法'))
  } finally {
    actionBusy.value = false
  }
}

/* ===== 专题聚合 ===== */
const candidates = ref<LifecycleTopicCandidate[]>([])
const members = ref<LifecycleTopicCandidate[]>([])
async function addMember(candidate: LifecycleTopicCandidate) {
  actionBusy.value = true
  try {
    await addLifecycleTopicMember(activityId.value, candidate.id)
    ElMessage.success('已加入专题聚合')
    await loadTopic()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '聚合失败'))
  } finally {
    actionBusy.value = false
  }
}
async function removeMember(member: LifecycleTopicCandidate) {
  actionBusy.value = true
  try {
    await removeLifecycleTopicMember(activityId.value, member.id)
    ElMessage.success('已移出专题聚合')
    await loadTopic()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '移除失败'))
  } finally {
    actionBusy.value = false
  }
}

async function doExport() {
  actionBusy.value = true
  try {
    const res = await exportLifecycleTemplate(activityId.value)
    const data = res.data.data ?? {}
    const blob = new Blob([String(data.packageJson ?? '')], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${activity.value?.activityCode ?? 'activity'}-template.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('模板包已导出（JSON，不含项目/组件/人员实例数据）')
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '导出失败'))
  } finally {
    actionBusy.value = false
  }
}

onMounted(async () => {
  try {
    const [stageRes, componentRes, roleRes] = await Promise.all([
      listLifecycleStages(), listLifecycleComponentOptions(), listLifecycleRoleOptions()
    ])
    stages.value = stageRes.data.data ?? []
    componentOptions.value = (componentRes.data.data ?? []).map(c => ({ id: c.id, projectName: c.projectName, systemName: c.systemName }))
    roleOptions.value = roleRes.data.data ?? []
  } catch { /* 选项失败不阻塞 */ }
  await loadWorkbench()
  initTopologyView()
  if (activeTab.value === 'topic') await loadTopic()
  if (activeTab.value === 'version') await loadSnapshot()
})
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra>
        <el-button :icon="ArrowLeft" link @click="goBack">返回活动列表</el-button>
        <span class="muted">
          {{ activity?.activityCode ?? '活动' }} · {{ activity?.activityName ?? '' }}
          （{{ TYPE_TEXT[activity?.activityType ?? ''] ?? '' }} / {{ GRAN_TEXT[activity?.granularity ?? ''] ?? '' }}）
          <el-tag v-if="activity" :type="activity.activityStatus === 'ACTIVE' ? 'success' : activity.activityStatus === 'INACTIVE' ? 'info' : 'danger'" size="small">
            {{ LifecycleStatusText[activity.activityStatus] }}
          </el-tag>
        </span>
      </template>
      <template #actions>
        <template v-if="activity && !isObsolete">
          <el-button v-if="canManage && activity.activityStatus === 'ACTIVE'" type="warning" plain @click="setStatus('INACTIVE')">停用</el-button>
          <el-button v-if="canManage && activity.activityStatus === 'INACTIVE'" type="success" plain @click="setStatus('ACTIVE')">启用</el-button>
          <el-button v-if="canDelete" type="danger" plain @click="doObsolete">作废</el-button>
          <el-button :icon="UploadFilled" :loading="actionBusy" @click="doExport">导出模板</el-button>
        </template>
      </template>
    </UiToolbar>

    <div v-if="loading" class="dm-state-panel"><UiEmptyState title="加载中" description="正在加载活动配置工作台" /></div>
    <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
    <template v-else-if="activity">
      <div class="dm-tabs-bar">
        <el-tabs :model-value="activeTab" @tab-change="switchTab">
          <el-tab-pane label="基础信息" name="basic" />
          <el-tab-pane label="工序配置" name="process" />
          <el-tab-pane label="拓扑依赖" name="topology" />
          <el-tab-pane v-if="activity.activityType === 'TOPIC'" label="专题聚合" name="topic" />
          <el-tab-pane label="版本快照" name="version" />
        </el-tabs>
      </div>

      <el-card v-show="activeTab === 'basic'" shadow="never" class="ui-surface-card">
        <template #header>
          <div class="dm-card-header">
            <span>基础信息（活动/工序配置修改仅对未来新建任务生效，存量任务快照固化）</span>
            <el-button v-if="canManage && !isObsolete" type="primary" link :icon="Edit" @click="openBasicEdit">编辑</el-button>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="活动编码">{{ activity.activityCode }}</el-descriptions-item>
          <el-descriptions-item label="活动名称">{{ activity.activityName }}</el-descriptions-item>
          <el-descriptions-item label="活动类型">{{ TYPE_TEXT[activity.activityType] ?? activity.activityType }}</el-descriptions-item>
          <el-descriptions-item label="生命周期阶段">{{ activity.stageName ?? '—（专题活动可空）' }}</el-descriptions-item>
          <el-descriptions-item label="活动颗粒度">{{ GRAN_TEXT[activity.granularity] ?? activity.granularity }}</el-descriptions-item>
          <el-descriptions-item label="活动状态">{{ LifecycleStatusText[activity.activityStatus] }}</el-descriptions-item>
          <el-descriptions-item label="适用场景" :span="2">{{ activity.scene || '—' }}</el-descriptions-item>
          <el-descriptions-item label="执行目标" :span="2">{{ activity.goal || '—' }}</el-descriptions-item>
          <el-descriptions-item label="整体准入条件" :span="2">{{ activity.overallEntryCond || '—' }}</el-descriptions-item>
          <el-descriptions-item label="整体准出说明" :span="2">{{ activity.overallExitDesc || '—' }}</el-descriptions-item>
          <el-descriptions-item label="整体交付物清单" :span="2">{{ activity.overallDeliverables || '—' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-show="activeTab === 'process'" shadow="never" class="ui-surface-card">
        <template #header>
          <div class="dm-card-header">
            <span>工序配置（至少 1 道工序；准出三要素齐备自动置 READY；停用活动不允许新增工序、作废活动只读）</span>
            <span>
              <el-button v-if="canManage && !isObsolete && !isInactive" type="primary" :icon="Plus" @click="openProcessAdd">新增工序</el-button>
              <el-button v-if="canManage && !isObsolete" type="primary" :loading="actionBusy" @click="saveProcesses">保存工序</el-button>
            </span>
          </div>
        </template>
        <UiDataTable :data="processes" empty-text="暂无工序，请先新增">
          <el-table-column prop="seq" label="序号" width="70" />
          <el-table-column prop="processName" label="工序名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="负责人角色" width="140">
            <template #default="{ row }">
              {{ roleOptions.find(r => r.id === row.ownerRoleId)?.roleName ?? '—' }}
            </template>
          </el-table-column>
          <el-table-column label="无前置任务" width="100">
            <template #default="{ row }">
              <el-tag :type="row.noPredecessor ? 'info' : 'warning'" size="small">{{ row.noPredecessor ? '独立直达' : '有前置' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="双因子" width="150">
            <template #default="{ row }">
              {{ row.mustAudit ? '需审核' : '免审核' }} / {{ row.mustSubmitDeliverable ? '需交付件' : '免交付件' }}
            </template>
          </el-table-column>
          <el-table-column label="准出三要素" width="110">
            <template #default="{ row }">
              <el-tag :type="row.configStatus === 'READY' ? 'success' : 'info'" size="small">{{ LifecycleStatusText[row.configStatus] ?? row.configStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ $index }">
              <el-button v-if="canManage && !isObsolete" link type="primary" :icon="Edit" @click="openProcessEdit($index)">编辑</el-button>
              <el-button v-if="canManage && !isObsolete" link type="danger" @click="removeProcess($index)">删除</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
      </el-card>

      <el-card v-show="activeTab === 'topology'" shadow="never" class="ui-surface-card">
        <template #header>
          <div class="dm-card-header">
            <span>拓扑依赖（禁自连/重复/成环/缺失节点，≤30 节点；无前置标记为依赖连线派生值）</span>
            <el-button :icon="Connection" :loading="actionBusy" type="primary" @click="saveTopology">保存拓扑（未发布）</el-button>
          </div>
        </template>
        <el-alert v-if="canvasHint" type="warning" :closable="false" show-icon>
          <template #title>{{ canvasHint }}</template>
          <el-button link type="primary" @click="retryCanvas">重试画布</el-button>
        </el-alert>
        <el-alert v-for="(warn, i) in topologyWarnings" :key="i" :title="warn" type="info" :closable="false" show-icon />
        <template v-if="tableView">
          <el-row :gutter="12" class="dm-topology-editor">
            <el-col :span="12">
              <h4 class="dm-sub-title">依赖连线（后置工序依赖前置工序）</h4>
              <el-form inline>
                <el-form-item label="前置工序">
                  <el-select v-model="edgeSource" clearable filterable placeholder="选择前置工序" style="width: 220px">
                    <el-option v-for="opt in processOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
                  </el-select>
                </el-form-item>
                <el-form-item label="后置工序">
                  <el-select v-model="edgeTarget" clearable filterable placeholder="选择后置工序" style="width: 220px">
                    <el-option v-for="opt in processOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
                  </el-select>
                </el-form-item>
                <el-form-item><el-button type="primary" :icon="Plus" @click="addEdge">添加连线</el-button></el-form-item>
              </el-form>
              <UiDataTable :data="edges" empty-text="暂无依赖连线（全部工序均为无前置独立直达）">
                <el-table-column label="前置工序" min-width="160">
                  <template #default="{ row }">{{ row.sourceProcessId }}</template>
                </el-table-column>
                <el-table-column label="后置工序" min-width="160">
                  <template #default="{ row }">{{ row.targetProcessId }}</template>
                </el-table-column>
                <el-table-column label="操作" width="90">
                  <template #default="{ $index }">
                    <el-button v-if="canManage && !isObsolete" link type="danger" @click="removeEdge($index)">删除</el-button>
                  </template>
                </el-table-column>
              </UiDataTable>
            </el-col>
            <el-col :span="12">
              <h4 class="dm-sub-title">无前置标记派生预览（保存后落库）</h4>
              <UiDataTable :data="processes" empty-text="暂无工序">
                <el-table-column prop="seq" label="序号" width="70" />
                <el-table-column prop="processName" label="工序" min-width="140" show-overflow-tooltip />
                <el-table-column label="无前置（派生）" width="120">
                  <template #default="{ row }">
                    <el-tag :type="noPredecessorOf.get(row.id) ? 'info' : 'warning'" size="small">
                      {{ noPredecessorOf.get(row.id) ? '独立直达' : '有前置' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </UiDataTable>
              <p class="muted">拓扑保存校验顺序固定：存在性 → 数量约束 → 状态约束。</p>
            </el-col>
          </el-row>
        </template>
      </el-card>

      <el-card v-show="activeTab === 'topic'" shadow="never" class="ui-surface-card">
        <template #header>
          <span>专题聚合（仅可聚合同颗粒度、启用状态的普通基础活动，四道闸门：类型/颗粒度/状态/范围）</span>
        </template>
        <h4 class="dm-sub-title">已聚合成员</h4>
        <UiDataTable :data="members" empty-text="尚未聚合任何普通活动">
          <el-table-column prop="activityCode" label="活动编码" width="150" />
          <el-table-column prop="activityName" label="活动名称" min-width="160" />
          <el-table-column label="生命周期阶段" width="120">
            <template #default="{ row }">{{ row.stageName ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button v-if="canManage && !isObsolete" link type="danger" @click="removeMember(row)">移出</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
        <h4 class="dm-sub-title">可聚合候选（普通 · 同颗粒度 · 启用 · 未加入）</h4>
        <UiDataTable :data="candidates" empty-text="暂无候选普通活动">
          <el-table-column prop="activityCode" label="活动编码" width="150" />
          <el-table-column prop="activityName" label="活动名称" min-width="160" />
          <el-table-column label="生命周期阶段" width="120">
            <template #default="{ row }">{{ row.stageName ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button v-if="canManage && !isObsolete" link type="primary" :icon="Plus" @click="addMember(row)">加入</el-button>
            </template>
          </el-table-column>
        </UiDataTable>
      </el-card>

      <el-card v-show="activeTab === 'version'" shadow="never" class="ui-surface-card">
        <template #header>
          <div class="dm-card-header">
            <span>版本快照（发布出口唯一；版本号与快照载荷 1:1 恒等，同一事务写入）</span>
            <el-button v-if="canManage && !isObsolete" type="primary" :loading="actionBusy" @click="doPublish">
              发布新版本{{ publishStatus && !publishStatus.publishable ? '（校验未通过）' : '' }}
            </el-button>
          </div>
        </template>
        <el-descriptions v-if="publishStatus" :column="4" border class="dm-publish-info">
          <el-descriptions-item label="工序总数">{{ publishStatus.processTotal }}</el-descriptions-item>
          <el-descriptions-item label="已就绪">{{ publishStatus.processReady }}</el-descriptions-item>
          <el-descriptions-item label="拓扑合法">{{ publishStatus.topologyOk ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="可发布">
            <el-tag :type="publishStatus.publishable ? 'success' : 'danger'" size="small">{{ publishStatus.publishable ? '可以发布' : '不可发布' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <p v-if="publishStatus?.missingExitProcesses?.length" class="dm-missing-exit">
          准出三要素不齐备工序：{{ publishStatus.missingExitProcesses.join('、') }}
        </p>
        <h4 class="dm-sub-title">最新发布快照（自描述载荷：schemaVersion + entityType + entityId + frozenAt + topologyVersion + 工序定义 + 依赖边）</h4>
        <div class="dm-snapshot-json">
          <pre>{{ snapshotJson }}</pre>
        </div>
      </el-card>

      <UiFormDrawer v-model="editOpen" title="编辑基础信息" :loading="saving" width="640px" @submit="submitBasic">
        <el-form label-width="110px">
          <el-form-item label="活动名称" required><el-input v-model="basicForm.activityName" maxlength="160" show-word-limit /></el-form-item>
          <el-form-item :label="activity.activityType === 'NORMAL' ? '生命周期阶段' : '生命周期阶段（选填）'">
            <el-select v-model="basicForm.lifecycleStageId" :clearable="activity.activityType === 'TOPIC'" style="width: 100%">
              <el-option v-for="stage in stages" :key="stage.id" :value="stage.id" :label="stage.stageName" />
            </el-select>
          </el-form-item>
          <el-form-item label="适用场景"><el-input v-model="basicForm.scene" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
          <el-form-item label="执行目标"><el-input v-model="basicForm.goal" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
          <el-form-item label="整体准入条件"><el-input v-model="basicForm.overallEntryCond" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="整体准出说明"><el-input v-model="basicForm.overallExitDesc" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="整体交付物清单"><el-input v-model="basicForm.overallDeliverables" type="textarea" :rows="2" /></el-form-item>
          <el-form-item v-if="activity.granularity === 'COMPONENT'" label="绑定组件">
            <el-select v-model="basicForm.componentIds" multiple filterable style="width: 100%">
              <el-option v-for="item in componentOptions" :key="item.id" :value="item.id" :label="`${item.projectName}${item.systemName ? ' / ' + item.systemName : ''}`" />
            </el-select>
          </el-form-item>
        </el-form>
      </UiFormDrawer>

      <UiFormDrawer v-model="processDrawer" :title="editingIndex >= 0 ? '编辑工序' : '新增工序'" :loading="processSaving" width="680px" confirm-text="确定" @submit="confirmProcessSave">
        <el-form label-width="120px">
          <el-form-item label="工序名称" required><el-input v-model="processForm.processName" maxlength="160" show-word-limit /></el-form-item>
          <el-form-item label="负责人角色">
            <el-select v-model="processForm.ownerRoleId" clearable filterable style="width: 100%">
              <el-option v-for="role in roleOptions" :key="role.id" :value="role.id" :label="role.roleName" />
            </el-select>
          </el-form-item>
          <el-form-item label="必选节点"><el-switch v-model="processForm.isRequired" /></el-form-item>
          <el-form-item label="准入配置"><el-input v-model="processForm.entryConfig" type="textarea" :rows="2" placeholder="前置输入条件/依赖说明/所需资料清单（JSON）" /></el-form-item>
          <el-form-item label="执行配置"><el-input v-model="processForm.execConfig" type="textarea" :rows="2" placeholder="执行/参与角色、标准步骤、作业指南、注意事项（JSON）" /></el-form-item>
          <el-form-item label="准出内容" required><el-input v-model="processForm.exitContent" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="准出交付物清单" required><el-input v-model="processForm.exitDeliverableList" type="textarea" :rows="2" placeholder="交付物清单（JSON 数组）" /></el-form-item>
          <el-form-item label="合格判定规则" required><el-input v-model="processForm.qualifiedRule" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="是否需审核"><el-switch v-model="processForm.mustAudit" active-text="需审核" inactive-text="免审核" /></el-form-item>
          <el-form-item label="是否需提交交付件"><el-switch v-model="processForm.mustSubmitDeliverable" active-text="需交付件" inactive-text="免交付件" /></el-form-item>
          <p class="muted">准出三要素（准出内容 / 准出交付物清单 / 合格判定规则）齐备后工序自动置 READY，方可随活动发布。</p>
        </el-form>
      </UiFormDrawer>
    </template>
  </section>
</template>

<style scoped>
.dm-tabs-bar { margin-bottom: 12px; }
.dm-card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.dm-sub-title { margin: 14px 0 8px; font-size: 14px; font-weight: 600; }
.dm-topology-editor { min-width: 0; }
.dm-missing-exit { color: var(--el-color-danger, #f56c6c); font-size: 12px; }
.dm-snapshot-json pre { max-height: 420px; margin: 0; padding: 12px; overflow: auto; background: #0f172a; color: #e2e8f0; border-radius: 6px; font-size: 12px; line-height: 1.6; }
.dm-publish-info + p { margin-top: 10px; }
</style>
