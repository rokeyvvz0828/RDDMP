<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Filter, MoreFilled, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  createDeploymentUnit,
  deactivateDeploymentUnit,
  getDeploymentUnit,
  getDeploymentUnitVersions,
  listDeploymentUnits,
  loadNetworkZoneOptions,
  loadPhysicalSubsystemOptions,
  reactivateDeploymentUnit,
  searchDeploymentUnitOptions,
  updateDeploymentUnit,
  voidDeploymentUnit
} from './api'
import DeploymentUnitDetailDrawer from './components/DeploymentUnitDetailDrawer.vue'
import type { DeploymentUnit, DeploymentUnitKind, DeploymentUnitPayload, DeploymentUnitVersion, NetworkZoneOption, PhysicalSubsystemOption, RelatedDeploymentUnit } from './types'
import {
  deploymentUnitKindLabels,
  deploymentUnitStatusLabels,
  deploymentUnitStatusTone,
  formatDateTime,
  httpStatus
} from './utils'
import './architecture.css'

const auth = useAuthStore()
const rows = ref<DeploymentUnit[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const advanced = ref(false)

const filters = reactive({
  code: '',
  name: '',
  physicalSubsystemId: null as number | null,
  kind: '' as DeploymentUnitKind | '',
  status: '' as DeploymentUnit['status'] | ''
})
const kindOptions: DeploymentUnitKind[] = ['APPLICATION', 'DATABASE', 'WEB']
const statusOptions: DeploymentUnit['status'][] = ['ACTIVE', 'INACTIVE', 'VOIDED']
const physicalOptions = ref<PhysicalSubsystemOption[]>([])
const networkZoneOptions = ref<NetworkZoneOption[]>([])

const drawerOpen = ref(false)
const detail = ref<DeploymentUnit | null>(null)
const detailLoading = ref(false)
const versions = ref<DeploymentUnitVersion[]>([])
const versionsLoading = ref(false)

const formOpen = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSubmitting = ref(false)
const form = reactive<DeploymentUnitPayload>({
  physicalSubsystemId: null,
  name: '',
  kind: '',
  relatedDeploymentUnitIds: [],
  defaultNetworkZoneId: null,
  description: null,
  remark: null,
  rowVersion: null
})
const formError = ref('')
const formBaseline = ref('')
const relatedOptions = ref<RelatedDeploymentUnit[]>([])
const relatedLoading = ref(false)
const relatedError = ref('')

let listRequest = 0
let detailRequest = 0
let versionsRequest = 0
let relatedRequest = 0

const canView = computed(() => auth.hasPermission('architecture:deployment-unit:view')
  || auth.hasPermission('architecture:deployment-unit:manage')
  || ['architecture:view', 'architecture:apply', 'architecture:manage'].some(p => auth.hasPermission(p)))
const canManage = computed(() => auth.hasPermission('architecture:deployment-unit:manage'))

function text(value: string | null | undefined) {
  const normalized = value?.trim()
  return normalized || null
}

function networkZoneLabel(row: { defaultNetworkZoneName?: string | null }) {
  return row.defaultNetworkZoneName || '—'
}

const standardSuffixByKind: Record<DeploymentUnitKind, string> = {
  APPLICATION: 'AP',
  DATABASE: 'DB',
  WEB: 'WB'
}
const kindByStandardSuffix: Record<string, DeploymentUnitKind> = {
  AP: 'APPLICATION',
  DB: 'DATABASE',
  WB: 'WEB'
}

function splitDeploymentUnitName(value: string) {
  const normalized = value.trim().toUpperCase()
  const separator = normalized.lastIndexOf('_')
  if (separator <= 0) return { base: normalized, suffix: '' }
  return { base: normalized.slice(0, separator), suffix: normalized.slice(separator + 1) }
}

function normalizeNameInput(value: string) {
  form.name = value.toUpperCase().replace(/\s+/g, '')
  const { suffix } = splitDeploymentUnitName(form.name)
  const linkedKind = kindByStandardSuffix[suffix]
  if (linkedKind) form.kind = linkedKind
}

function applyKindToName(kind: DeploymentUnitKind) {
  const { base, suffix } = splitDeploymentUnitName(form.name)
  if (!base) return
  if (!suffix || kindByStandardSuffix[suffix]) {
    form.name = `${base}_${standardSuffixByKind[kind]}`
  }
}

function formSnapshot() {
  return JSON.stringify({ ...form, relatedDeploymentUnitIds: [...form.relatedDeploymentUnitIds].sort((a, b) => a - b) })
}

async function requestCloseForm(done?: () => void) {
  if (formSubmitting.value) return
  if (formSnapshot() !== formBaseline.value) {
    try {
      await ElMessageBox.confirm('当前部署单元信息尚未保存，关闭后修改将丢失。', '放弃未保存修改？', {
        confirmButtonText: '放弃修改',
        cancelButtonText: '继续编辑',
        type: 'warning'
      })
    } catch {
      return
    }
  }
  if (done) done()
  else formOpen.value = false
}

function mergeRelatedOptions(items: RelatedDeploymentUnit[]) {
  const selectedIds = new Set(form.relatedDeploymentUnitIds)
  const retained = relatedOptions.value.filter(item => selectedIds.has(item.id))
  const merged = new Map(retained.map(item => [item.id, item]))
  items.forEach(item => merged.set(item.id, item))
  relatedOptions.value = [...merged.values()]
}

async function searchRelatedOptions(keyword = '') {
  const request = ++relatedRequest
  relatedLoading.value = true
  relatedError.value = ''
  try {
    const result = await searchDeploymentUnitOptions({
      keyword,
      page: 1,
      size: 50,
      excludeId: formMode.value === 'edit' ? detail.value?.id ?? null : null
    })
    if (request !== relatedRequest) return
    mergeRelatedOptions(result.records)
  } catch (error) {
    if (request !== relatedRequest) return
    relatedError.value = apiErrorMessage(error, '关联部署单元搜索失败')
  } finally {
    if (request === relatedRequest) relatedLoading.value = false
  }
}

async function loadPhysicals() {
  try {
    physicalOptions.value = await loadPhysicalSubsystemOptions('', 100)
  } catch (error) {
    if (httpStatus(error) !== 403) ElMessage.warning(apiErrorMessage(error, '物理子系统选项加载失败'))
  }
}

async function loadNetworkZones() {
  try {
    networkZoneOptions.value = await loadNetworkZoneOptions(true)
  } catch (error) {
    if (httpStatus(error) !== 403) ElMessage.warning(apiErrorMessage(error, '网络分区选项加载失败'))
  }
}

async function load() {
  if (!canView.value) return
  const request = ++listRequest
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listDeploymentUnits({ page: page.value, size: pageSize.value, ...filters })
    if (request !== listRequest) return
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    if (request !== listRequest) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '部署单元列表加载失败')
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function showDetail(row: DeploymentUnit) {
  const request = ++detailRequest
  detail.value = row
  drawerOpen.value = true
  detailLoading.value = true
  versions.value = []
  try {
    const result = await getDeploymentUnit(row.id)
    if (request === detailRequest) detail.value = result
  } catch (error) {
    if (request === detailRequest) ElMessage.error(apiErrorMessage(error, '部署单元详情加载失败'))
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
  void loadVersions(row.id)
}

async function loadVersions(id: number) {
  const request = ++versionsRequest
  versionsLoading.value = true
  try {
    const result = await getDeploymentUnitVersions(id)
    if (request === versionsRequest) versions.value = result
  } catch (error) {
    if (request === versionsRequest) ElMessage.error(apiErrorMessage(error, '版本历史加载失败'))
  } finally {
    if (request === versionsRequest) versionsLoading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    physicalSubsystemId: null,
    name: '',
    kind: '',
    relatedDeploymentUnitIds: [],
    defaultNetworkZoneId: null,
    description: null,
    remark: null,
    rowVersion: null
  })
  formMode.value = 'create'
  formError.value = ''
  relatedOptions.value = []
  relatedError.value = ''
  formBaseline.value = formSnapshot()
  formOpen.value = true
  void searchRelatedOptions()
}

function openEdit(unit: DeploymentUnit) {
  Object.assign(form, {
    physicalSubsystemId: null,
    name: unit.name,
    kind: unit.kind,
    relatedDeploymentUnitIds: unit.relatedDeploymentUnits.map(item => item.id),
    defaultNetworkZoneId: unit.defaultNetworkZoneId,
    description: unit.description,
    remark: unit.remark,
    rowVersion: unit.rowVersion
  })
  formMode.value = 'edit'
  formError.value = ''
  relatedOptions.value = [...unit.relatedDeploymentUnits]
  relatedError.value = ''
  formBaseline.value = formSnapshot()
  formOpen.value = true
  void searchRelatedOptions()
}

async function submitForm() {
  if (formSubmitting.value) return
  normalizeNameInput(form.name)
  if (!form.name.trim()) {
    formError.value = '请填写部署单元名称'
    return
  }
  if (!form.kind) {
    formError.value = '请选择部署单元类型'
    return
  }
  if (!/^[A-Z0-9]+_[A-Z0-9]{1,8}$/.test(form.name)) {
    formError.value = '名称格式应为主名称_后缀；主名称和 1—8 位后缀只能包含大写字母或数字'
    return
  }
  const { suffix } = splitDeploymentUnitName(form.name)
  if (kindByStandardSuffix[suffix] && kindByStandardSuffix[suffix] !== form.kind) {
    formError.value = `标准后缀 _${suffix} 与部署单元类型不一致`
    return
  }
  if (formMode.value === 'create' && !form.physicalSubsystemId) {
    formError.value = '请选择所属物理子系统'
    return
  }
  formSubmitting.value = true
  formError.value = ''
  try {
    const payload: DeploymentUnitPayload = {
      physicalSubsystemId: form.physicalSubsystemId,
      name: form.name,
      kind: form.kind as DeploymentUnitKind,
      relatedDeploymentUnitIds: [...form.relatedDeploymentUnitIds],
      defaultNetworkZoneId: form.defaultNetworkZoneId,
      description: text(form.description),
      remark: text(form.remark),
      rowVersion: form.rowVersion
    }
    if (formMode.value === 'create') {
      await createDeploymentUnit(payload)
      ElMessage.success('部署单元已创建并发布版本 1')
    } else {
      const updated = await updateDeploymentUnit(detail.value!.id, { ...payload, physicalSubsystemId: null })
      ElMessage.success(`已发布新版本 v${updated.currentVersion}`)
      detail.value = updated
      void loadVersions(updated.id)
    }
    formOpen.value = false
    void load()
  } catch (error) {
    formError.value = apiErrorMessage(error, formMode.value === 'create' ? '创建失败' : '发布新版本失败')
  } finally {
    formSubmitting.value = false
  }
}

async function confirmLifecycle(action: 'deactivate' | 'reactivate' | 'void', unit: DeploymentUnit) {
  const messages = {
    deactivate: {
      title: '停用部署单元',
      text: `停用「${unit.name}」后将不能用于新的资源申请、搭建任务或部署，既有实例与历史保留。是否继续？`,
      confirm: '停用'
    },
    reactivate: {
      title: '重新启用部署单元',
      text: `重新启用「${unit.name}」后可以继续用于新的资源申请、搭建任务或部署。是否继续？`,
      confirm: '重新启用'
    },
    void: {
      title: '作废部署单元',
      text: `作废「${unit.name}」仅允许从未被引用的错误记录，作废后编号不可复用且不可恢复。是否继续？`,
      confirm: '作废'
    }
  }[action]
  try {
    await ElMessageBox.confirm(messages.text, messages.title, {
      confirmButtonText: messages.confirm,
      cancelButtonText: '取消',
      type: action === 'void' ? 'error' : 'warning'
    })
  } catch {
    return
  }
  try {
    const updated = action === 'deactivate' ? await deactivateDeploymentUnit(unit.id)
      : action === 'reactivate' ? await reactivateDeploymentUnit(unit.id)
        : await voidDeploymentUnit(unit.id)
    ElMessage.success(messages.confirm + '成功')
    detail.value = updated
    void loadVersions(updated.id)
    void load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, messages.confirm + '失败'))
  }
}

function handleMaintainCommand(command: string | number | object, unit: DeploymentUnit) {
  if (command === 'edit') {
    openEdit(unit)
    return
  }
  if (command === 'deactivate' || command === 'reactivate' || command === 'void') {
    void confirmLifecycle(command, unit)
  }
}

function search() { page.value = 1; void load() }
function reset() {
  Object.assign(filters, { code: '', name: '', physicalSubsystemId: null, kind: '', status: '' })
  page.value = 1
  void load()
}
async function refresh() {
  await Promise.all([load(), loadPhysicals(), loadNetworkZones()])
  if (!loadError.value && !forbidden.value) ElMessage.success('列表已刷新')
}
function changePage(value: number) { page.value = value; void load() }
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

watch(canView, allowed => {
  if (allowed) void Promise.all([load(), loadPhysicals(), loadNetworkZones()])
}, { immediate: true })

</script>

<template>
  <main class="architecture-page">
    <UiPageHeader title="部署单元" description="维护可独立部署、升级、启停和运行的架构定义；显示内容变更自动形成新版本，编号发布后永久唯一。">
      <template #actions><el-button v-if="canManage" type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建部署单元</el-button></template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel"><el-result icon="warning" title="暂无部署单元查看权限" sub-title="请申请 architecture:deployment-unit:view 权限。" /></section>
    <section v-else-if="loadError" class="architecture-state-panel"><el-result icon="error" title="部署单元加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="filters.code" clearable placeholder="部署单元编号" class="architecture-filter-input" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-input v-model="filters.name" clearable placeholder="部署单元名称，如 SMSLJ_AP" class="architecture-filter-input" @keyup.enter="search" />
        <el-select v-model="filters.status" clearable placeholder="发布状态" class="architecture-filter-select"><el-option v-for="status in statusOptions" :key="status" :label="deploymentUnitStatusLabels[status]" :value="status" /></el-select>
        <el-button :type="advanced ? 'primary' : 'default'" plain @click="advanced = !advanced"><el-icon><Filter /></el-icon>更多筛选</el-button>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
        <template #actions><el-tooltip content="刷新列表"><el-button circle :loading="loading" aria-label="刷新部署单元列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button></el-tooltip></template>
      </UiToolbar>
      <div v-if="advanced" class="architecture-advanced-filter"><el-form inline label-position="top"><el-form-item label="所属物理子系统"><el-select v-model="filters.physicalSubsystemId" clearable filterable style="width:240px"><el-option v-for="item in physicalOptions" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" /></el-select></el-form-item><el-form-item label="部署单元类型"><el-select v-model="filters.kind" clearable style="width:160px"><el-option v-for="kind in kindOptions" :key="kind" :label="deploymentUnitKindLabels[kind]" :value="kind" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="search">应用筛选</el-button></el-form-item></el-form></div>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="部署单元" min-width="220"><template #default="scope"><button type="button" class="architecture-table-identity" @click="showDetail(scope.row)"><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code || '—' }}</small></button></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="deploymentUnitStatusLabels" :tone="deploymentUnitStatusTone(scope.row.status)" /></template></el-table-column>
        <el-table-column label="类型" width="100"><template #default="scope">{{ deploymentUnitKindLabels[scope.row.kind as DeploymentUnitKind] }}</template></el-table-column>
        <el-table-column label="默认网络分区" min-width="140"><template #default="scope">{{ networkZoneLabel(scope.row) }}</template></el-table-column>
        <el-table-column label="所属物理子系统" min-width="180"><template #default="scope">{{ scope.row.physicalSubsystemName }}<small class="architecture-inline-code">{{ scope.row.physicalSubsystemCode }}</small></template></el-table-column>
        <el-table-column label="当前版本" width="100"><template #default="scope">v{{ scope.row.currentVersion }}</template></el-table-column>
        <el-table-column label="最后更新" width="145"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right"><template #default="scope"><div class="architecture-table-actions"><el-button link type="primary" @click="showDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button><el-dropdown v-if="canManage" @command="(command: string | number | object) => handleMaintainCommand(command, scope.row)"><el-button link type="primary"><el-icon><MoreFilled /></el-icon>维护</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-if="scope.row.status === 'ACTIVE'" command="edit">修改并发布新版本</el-dropdown-item><el-dropdown-item v-if="scope.row.status === 'ACTIVE'" command="deactivate" divided>停用</el-dropdown-item><el-dropdown-item v-if="scope.row.status === 'INACTIVE'" command="reactivate">重新启用</el-dropdown-item><el-dropdown-item v-if="scope.row.status === 'ACTIVE' || scope.row.status === 'INACTIVE'" command="void" divided>作废</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></template></el-table-column>
        <template #footer><div class="architecture-table-footer"><span>共 {{ total }} 条记录</span><el-pagination :current-page="page" :page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" /></div></template>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id"><header><div><strong>{{ row.name }}</strong><small>{{ row.code || '—' }}</small></div><UiStatusTag :value="row.status" :labels="deploymentUnitStatusLabels" :tone="deploymentUnitStatusTone(row.status)" /></header><dl><div><dt>类型</dt><dd>{{ deploymentUnitKindLabels[row.kind] }}</dd></div><div><dt>关联数量</dt><dd>{{ row.relatedDeploymentUnits.length }}</dd></div><div><dt>默认网络分区</dt><dd>{{ networkZoneLabel(row) }}</dd></div><div><dt>所属物理子系统</dt><dd>{{ row.physicalSubsystemName }}（{{ row.physicalSubsystemCode }}）</dd></div><div><dt>当前版本</dt><dd>v{{ row.currentVersion }}</dd></div><div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div></dl><footer><el-button link type="primary" @click="showDetail(row)"><el-icon><View /></el-icon>详情</el-button><el-dropdown v-if="canManage" @command="(command: string | number | object) => handleMaintainCommand(command, row)"><el-button link type="primary"><el-icon><MoreFilled /></el-icon>维护</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-if="row.status === 'ACTIVE'" command="edit">修改并发布新版本</el-dropdown-item><el-dropdown-item v-if="row.status === 'ACTIVE'" command="deactivate" divided>停用</el-dropdown-item><el-dropdown-item v-if="row.status === 'INACTIVE'" command="reactivate">重新启用</el-dropdown-item><el-dropdown-item v-if="row.status === 'ACTIVE' || row.status === 'INACTIVE'" command="void" divided>作废</el-dropdown-item></el-dropdown-menu></template></el-dropdown></footer></article>
        <div class="architecture-table-footer"><el-pagination :current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="changePage" /></div>
      </div>
      <UiEmptyState v-if="!loading && !rows.length" title="暂无部署单元" description="在已发布的物理子系统下创建，或通过初始化导入批量接入存量部署单元。"><template #action><el-button v-if="canManage" type="primary" @click="openCreate">新建部署单元</el-button><el-button v-else @click="reset">清空筛选</el-button></template></UiEmptyState>
    </template>

    <DeploymentUnitDetailDrawer
      v-model="drawerOpen"
      :loading="detailLoading"
      :versions-loading="versionsLoading"
      :title="detail?.name || '部署单元详情'"
      :unit="detail"
      :versions="versions"
      @edit="detail && openEdit(detail)"
      @deactivate="detail && confirmLifecycle('deactivate', detail)"
      @reactivate="detail && confirmLifecycle('reactivate', detail)"
      @void="detail && confirmLifecycle('void', detail)"
    />

    <el-dialog v-model="formOpen" :title="formMode === 'create' ? '新建部署单元' : '修改并发布新版本'" width="min(620px, 94vw)" destroy-on-close :before-close="requestCloseForm">
      <el-form label-position="top">
        <el-form-item v-if="formMode === 'create'" label="所属物理子系统">
          <el-select v-model="form.physicalSubsystemId" filterable placeholder="选择已发布的物理子系统" style="width:100%">
            <el-option v-for="item in physicalOptions" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部署单元名称" class="architecture-deployment-name-field">
          <el-input :model-value="form.name" maxlength="64" show-word-limit placeholder="如 SMSLJ_AP" @update:model-value="normalizeNameInput" />
          <p class="architecture-form-hint">使用一个完整名称，以最后一个“_”分隔主名称和后缀；输入 _AP、_DB、_WB 会自动选择类型。</p>
        </el-form-item>
        <el-form-item label="部署单元类型">
          <el-radio-group v-model="form.kind" @change="applyKindToName">
            <el-radio-button v-for="kind in kindOptions" :key="kind" :value="kind">{{ deploymentUnitKindLabels[kind] }}</el-radio-button>
          </el-radio-group>
          <p class="architecture-form-hint">应用、数据库、Web 分别对应 _AP、_DB、_WB；自定义后缀保持不变，由你手工选择类型。</p>
        </el-form-item>
        <el-form-item label="关联部署单元">
          <el-select
            v-model="form.relatedDeploymentUnitIds"
            multiple
            filterable
            remote
            reserve-keyword
            collapse-tags
            collapse-tags-tooltip
            :loading="relatedLoading"
            :remote-method="searchRelatedOptions"
            :no-data-text="relatedError || '没有匹配的启用部署单元'"
            placeholder="按名称、编号或物理子系统搜索，可多选"
            class="architecture-related-unit-select"
          >
            <el-option v-for="unit in relatedOptions" :key="unit.id" :label="`${unit.name}（${unit.code} · ${unit.physicalSubsystemName || '未知物理子系统'}）`" :value="unit.id" />
          </el-select>
          <p v-if="relatedError" class="architecture-field-error">{{ relatedError }}，已选项已保留，可重新输入关键字重试。</p>
        </el-form-item>
        <el-form-item label="默认网络分区">
          <el-select v-model="form.defaultNetworkZoneId" clearable filterable placeholder="选择启用叶子网络分区" style="width:100%">
            <el-option v-for="zone in networkZoneOptions" :key="zone.id" :label="`${zone.name}（${zone.code}）`" :value="zone.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item>
        <el-alert v-if="formMode === 'edit'" type="info" :closable="false" show-icon title="保存后立即发布新版本，历史版本保持不可改写。" />
        <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" class="architecture-form-error" />
      </el-form>
      <template #footer>
        <el-button @click="requestCloseForm()">取消</el-button>
        <el-button type="primary" :loading="formSubmitting" @click="submitForm">{{ formMode === 'create' ? '创建并发布版本 1' : '发布新版本' }}</el-button>
      </template>
    </el-dialog>
  </main>
</template>
