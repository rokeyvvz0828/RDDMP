<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Filter, MoreFilled, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  getPhysicalSubsystem,
  listPhysicalSubsystems,
  loadLogicalSubsystemOptions,
  loadOrganizationOptions,
  loadParameterOptions
} from './api'
import SubsystemDetailDrawer from './components/SubsystemDetailDrawer.vue'
import type {
  DetailItem,
  LogicalSubsystemOption,
  OrganizationOption,
  ParameterOption,
  PhysicalSubsystem,
  PublishedSubsystemStatus,
  SubsystemActionType
} from './types'
import {
  actionTypeLabels,
  allowedPublishedActions,
  formatDateTime,
  httpStatus,
  optionLabel,
  publishedStatusLabels,
  publishedStatusTone
} from './utils'
import './architecture.css'

const auth = useAuthStore()
const router = useRouter()
const rows = ref<PhysicalSubsystem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const advanced = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<PhysicalSubsystem | null>(null)
const organizations = ref<OrganizationOption[]>([])
const logicalSubsystems = ref<LogicalSubsystemOption[]>([])
const runtimes = ref<ParameterOption[]>([])
const levels = ref<ParameterOption[]>([])
const frameworks = ref<ParameterOption[]>([])
const deploymentPlatforms = ref<ParameterOption[]>([])
const disasterRecoveryModes = ref<ParameterOption[]>([])
const statusOptions: PublishedSubsystemStatus[] = ['ACTIVE', 'OFFLINE', 'VOIDED']
const filters = reactive({
  code: '',
  shortName: '',
  name: '',
  businessGroupName: '',
  responsibleTeamOrgId: null as number | null,
  logicalSubsystemId: null as number | null,
  status: '' as PublishedSubsystemStatus | ''
})
let listRequest = 0
let detailRequest = 0

const canView = computed(() => auth.hasPermission('architecture:physical:list')
  || ['architecture:view', 'architecture:apply', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canApply = computed(() => auth.hasPermission('architecture:apply') || auth.hasPermission('architecture:manage'))
function publishedStatusLabel(status: PublishedSubsystemStatus | null) {
  return status ? publishedStatusLabels[status] : '—'
}
const detailItems = computed<DetailItem[]>(() => detail.value ? [
  { label: '系统编号', value: detail.value.code },
  { label: '编号槽位', value: detail.value.numberSlot || '—' },
  { label: '发布状态', value: publishedStatusLabels[detail.value.status], tone: detail.value.status === 'VOIDED' ? 'danger' : detail.value.status === 'OFFLINE' ? 'warning' : undefined },
  { label: '系统简称', value: detail.value.shortName },
  { label: '英文名称', value: detail.value.englishName || '—' },
  { label: '所属逻辑子系统', value: `${detail.value.logicalSubsystemName}（${detail.value.logicalSubsystemCode}）` },
  { label: '逻辑子系统状态', value: detail.value.logicalSubsystemStatus ? publishedStatusLabels[detail.value.logicalSubsystemStatus] : '—' },
  { label: '所属事业群', value: detail.value.businessGroupName || '—' },
  { label: '农信业务连续性等级', value: detail.value.businessContinuityLevel || '—' },
  { label: '项目组收集系统等级', value: detail.value.collectedSystemLevel || '—' },
  { label: '部署平台', value: optionLabel(deploymentPlatforms.value, detail.value.deploymentPlatform) },
  { label: '灾备模式', value: optionLabel(disasterRecoveryModes.value, detail.value.disasterRecoveryMode) },
  { label: '负责团队', value: detail.value.responsibleTeamDisplayName, tone: detail.value.responsibleTeamValid ? undefined : 'warning' },
  { label: '团队引用', value: detail.value.responsibleTeamValid ? '当前有效' : '已失效，变更时必须重选', tone: detail.value.responsibleTeamValid ? undefined : 'warning' },
  { label: '系统运行时间', value: optionLabel(runtimes.value, detail.value.runtimeCode) },
  { label: '系统级别', value: optionLabel(levels.value, detail.value.systemLevelCode) },
  { label: '开发平台框架', value: optionLabel(frameworks.value, detail.value.developmentFrameworkCode) },
  { label: '负责人', value: detail.value.ownerDisplayName || '—' },
  { label: '创建人', value: detail.value.createdByDisplayName || `用户 #${detail.value.createdBy}` },
  { label: '数据版本', value: String(detail.value.rowVersion) },
  { label: '创建时间', value: formatDateTime(detail.value.createdAt) },
  { label: '最后更新', value: formatDateTime(detail.value.updatedAt) },
  { label: '系统描述', value: detail.value.description || '—', wide: true },
  { label: '备注', value: detail.value.remark || '—', wide: true }
] : [])

async function loadReferences() {
  const results = await Promise.allSettled([
    loadOrganizationOptions('physical-subsystem', '', 100),
    loadLogicalSubsystemOptions('', 100),
    loadParameterOptions('physical-subsystem', 'ARCH_RUNTIME'),
    loadParameterOptions('physical-subsystem', 'ARCH_SYSTEM_LEVEL'),
    loadParameterOptions('physical-subsystem', 'ARCH_DEVELOPMENT_FRAMEWORK'),
    loadParameterOptions('physical-subsystem', 'ARCH_DEPLOYMENT_PLATFORM'),
    loadParameterOptions('physical-subsystem', 'ARCH_DISASTER_RECOVERY_MODE')
  ])
  if (results[0].status === 'fulfilled') organizations.value = results[0].value
  if (results[1].status === 'fulfilled') logicalSubsystems.value = results[1].value
  if (results[2].status === 'fulfilled') runtimes.value = results[2].value
  if (results[3].status === 'fulfilled') levels.value = results[3].value
  if (results[4].status === 'fulfilled') frameworks.value = results[4].value
  if (results[5].status === 'fulfilled') deploymentPlatforms.value = results[5].value
  if (results[6].status === 'fulfilled') disasterRecoveryModes.value = results[6].value
}

async function load() {
  if (!canView.value) return
  const request = ++listRequest
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listPhysicalSubsystems({ page: page.value, size: pageSize.value, ...filters })
    if (request !== listRequest) return
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    if (request !== listRequest) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '物理子系统列表加载失败')
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function showDetail(row: PhysicalSubsystem) {
  const request = ++detailRequest
  detail.value = row
  detailOpen.value = true
  detailLoading.value = true
  try {
    const result = await getPhysicalSubsystem(row.id)
    if (request === detailRequest) detail.value = result
  } catch (error) {
    if (request === detailRequest) ElMessage.error(apiErrorMessage(error, '物理子系统详情加载失败'))
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}

function createApplication() {
  void router.push({ name: 'architecture-subsystem-change-application-new', query: { targetKind: 'PHYSICAL' } })
}

function beginChange(row: PhysicalSubsystem, command: string | number | object) {
  const actionType = String(command) as SubsystemActionType
  void router.push({
    name: 'architecture-subsystem-change-application-new',
    query: { targetKind: 'PHYSICAL', actionType, targetId: row.id }
  })
}

function search() { page.value = 1; void load() }
function reset() {
  Object.assign(filters, {
    code: '', shortName: '', name: '', businessGroupName: '', responsibleTeamOrgId: null,
    logicalSubsystemId: null, status: ''
  })
  page.value = 1
  void load()
}
async function refresh() {
  await Promise.all([load(), loadReferences()])
  if (!loadError.value && !forbidden.value) ElMessage.success('列表已刷新')
}
function changePage(value: number) { page.value = value; void load() }
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

watch(canView, allowed => {
  if (allowed) void Promise.all([load(), loadReferences()])
}, { immediate: true })
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader title="物理子系统" description="查看已发布的物理子系统事实；新增、变更、替换和生命周期操作均通过审批工单完成。">
      <template #actions><el-button v-if="canApply" type="primary" @click="createApplication"><el-icon><Plus /></el-icon>申请物理子系统</el-button></template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel"><el-result icon="warning" title="暂无物理子系统查看权限" sub-title="请申请 architecture:view 权限。" /></section>
    <section v-else-if="loadError" class="architecture-state-panel"><el-result icon="error" title="物理子系统加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="filters.code" clearable placeholder="系统编号" class="architecture-filter-input" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-input v-model="filters.shortName" clearable placeholder="系统简称" class="architecture-filter-input" @keyup.enter="search" />
        <el-input v-model="filters.name" clearable placeholder="系统名称" class="architecture-filter-input" @keyup.enter="search" />
        <el-select v-model="filters.status" clearable placeholder="发布状态" class="architecture-filter-select"><el-option v-for="status in statusOptions" :key="status" :label="publishedStatusLabels[status]" :value="status" /></el-select>
        <el-button :type="advanced ? 'primary' : 'default'" plain @click="advanced = !advanced"><el-icon><Filter /></el-icon>更多筛选</el-button>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
        <template #actions><el-tooltip content="刷新列表"><el-button circle :loading="loading" aria-label="刷新物理子系统列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button></el-tooltip></template>
      </UiToolbar>
      <div v-if="advanced" class="architecture-advanced-filter"><el-form inline label-position="top"><el-form-item label="所属事业群"><el-input v-model="filters.businessGroupName" clearable placeholder="事业群名称" style="width:190px" /></el-form-item><el-form-item label="负责团队"><el-select v-model="filters.responsibleTeamOrgId" clearable filterable style="width:220px"><el-option v-for="item in organizations" :key="item.id" :label="item.pathLabel" :value="item.id" /></el-select></el-form-item><el-form-item label="所属逻辑子系统"><el-select v-model="filters.logicalSubsystemId" clearable filterable style="width:230px"><el-option v-for="item in logicalSubsystems" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="search">应用筛选</el-button></el-form-item></el-form></div>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="物理子系统" min-width="210"><template #default="scope"><button type="button" class="architecture-table-identity" @click="showDetail(scope.row)"><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }} · {{ scope.row.shortName }}</small></button></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="publishedStatusLabels" :tone="publishedStatusTone(scope.row.status)" /></template></el-table-column>
        <el-table-column label="所属逻辑子系统" min-width="170"><template #default="scope">{{ scope.row.logicalSubsystemName }}<small class="architecture-inline-code">{{ scope.row.logicalSubsystemCode }} · {{ publishedStatusLabel(scope.row.logicalSubsystemStatus) }}</small></template></el-table-column>
        <el-table-column prop="businessGroupName" label="所属事业群" min-width="115"><template #default="scope">{{ scope.row.businessGroupName || '—' }}</template></el-table-column>
        <el-table-column label="负责团队" min-width="140"><template #default="scope"><div class="architecture-team-cell"><span>{{ scope.row.responsibleTeamDisplayName }}</span><UiStatusTag v-if="!scope.row.responsibleTeamValid" :value="false" :labels="{ false: '已失效' }" tone="warning" /></div></template></el-table-column>
        <el-table-column label="负责人" width="100"><template #default="scope">{{ scope.row.ownerDisplayName || '—' }}</template></el-table-column>
        <el-table-column label="最后更新" width="145"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right"><template #default="scope"><div class="architecture-table-actions"><el-button link type="primary" @click="showDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button><el-dropdown v-if="canApply && allowedPublishedActions('PHYSICAL', scope.row.status).length" @command="beginChange(scope.row, $event)"><el-button link type="primary"><el-icon><MoreFilled /></el-icon>发起变更</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="action in allowedPublishedActions('PHYSICAL', scope.row.status)" :key="action" :command="action">{{ actionTypeLabels[action] }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></template></el-table-column>
        <template #footer><div class="architecture-table-footer"><span>共 {{ total }} 条记录</span><el-pagination :current-page="page" :page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" /></div></template>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id"><header><div><strong>{{ row.name }}</strong><small>{{ row.code }} · {{ row.shortName }}</small></div><UiStatusTag :value="row.status" :labels="publishedStatusLabels" :tone="publishedStatusTone(row.status)" /></header><dl><div><dt>所属逻辑子系统</dt><dd>{{ row.logicalSubsystemName }}（{{ row.logicalSubsystemCode }}）</dd></div><div><dt>所属事业群</dt><dd>{{ row.businessGroupName || '—' }}</dd></div><div><dt>负责团队</dt><dd>{{ row.responsibleTeamDisplayName }}<span v-if="!row.responsibleTeamValid" class="architecture-warning-text">（已失效）</span></dd></div><div><dt>负责人</dt><dd>{{ row.ownerDisplayName || '—' }}</dd></div></dl><footer><el-button link type="primary" @click="showDetail(row)"><el-icon><View /></el-icon>详情</el-button><el-dropdown v-if="canApply && allowedPublishedActions('PHYSICAL', row.status).length" @command="beginChange(row, $event)"><el-button link type="primary"><el-icon><MoreFilled /></el-icon>发起变更</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="action in allowedPublishedActions('PHYSICAL', row.status)" :key="action" :command="action">{{ actionTypeLabels[action] }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></footer></article>
        <div class="architecture-table-footer"><el-pagination :current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="changePage" /></div>
      </div>
      <UiEmptyState v-if="!loading && !rows.length" title="暂无物理子系统" description="调整筛选条件，或发起第一张物理子系统申请。"><template #action><el-button v-if="canApply" type="primary" @click="createApplication">发起申请</el-button><el-button v-else @click="reset">清空筛选</el-button></template></UiEmptyState>
    </template>

    <SubsystemDetailDrawer v-model="detailOpen" :loading="detailLoading" :title="detail?.name || '物理子系统详情'" :code="detail?.code" :items="detailItems" />
  </main>
</template>
