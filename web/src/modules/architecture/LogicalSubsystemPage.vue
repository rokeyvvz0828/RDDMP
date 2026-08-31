<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { MoreFilled, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
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
  getLogicalSubsystem,
  listLogicalSubsystems,
  loadOrganizationOptions,
  loadParameterOptions,
  loadUserOptions
} from './api'
import SubsystemDetailDrawer from './components/SubsystemDetailDrawer.vue'
import type {
  DetailItem,
  LogicalSubsystem,
  OrganizationOption,
  ParameterOption,
  PublishedSubsystemStatus,
  SubsystemActionType,
  UserOption
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
const rows = ref<LogicalSubsystem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<LogicalSubsystem | null>(null)
const organizations = ref<OrganizationOption[]>([])
const users = ref<UserOption[]>([])
const deploymentPlatforms = ref<ParameterOption[]>([])
const systemTypes = ref<ParameterOption[]>([])
const ownerships = ref<ParameterOption[]>([])
const filters = reactive({ code: '', shortName: '', name: '', businessOrgId: null as number | null, status: '' as PublishedSubsystemStatus | '' })
let listRequest = 0
let detailRequest = 0

const canView = computed(() => auth.hasPermission('architecture:logical:list')
  || ['architecture:view', 'architecture:apply', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canApply = computed(() => auth.hasPermission('architecture:apply') || auth.hasPermission('architecture:manage'))
const statusOptions: PublishedSubsystemStatus[] = ['ACTIVE', 'OFFLINE', 'VOIDED']
const orgLabels = computed(() => new Map(organizations.value.map(item => [item.id, item.pathLabel])))
const userLabels = computed(() => new Map(users.value.map(item => [item.id, item.displayName])))
const detailItems = computed<DetailItem[]>(() => detail.value ? [
  { label: '系统编号', value: detail.value.code },
  { label: '系统状态', value: publishedStatusLabels[detail.value.status], tone: detail.value.status === 'VOIDED' ? 'danger' : detail.value.status === 'OFFLINE' ? 'warning' : undefined },
  { label: '系统简称', value: detail.value.shortName },
  { label: '所属事业群', value: orgLabel(detail.value.businessOrgId) },
  { label: '联系人', value: userLabel(detail.value.contactUserId) },
  { label: '部署平台', value: optionLabel(deploymentPlatforms.value, detail.value.deploymentPlatformCode) },
  { label: '系统类型', value: optionLabel(systemTypes.value, detail.value.systemTypeCode) },
  { label: '系统归属', value: optionLabel(ownerships.value, detail.value.systemOwnershipCode) },
  { label: '排序号', value: String(detail.value.sortNo) },
  { label: '数据版本', value: String(detail.value.rowVersion) },
  { label: '创建时间', value: formatDateTime(detail.value.createdAt) },
  { label: '最后更新', value: formatDateTime(detail.value.updatedAt) },
  { label: '系统描述', value: detail.value.description || '—', wide: true },
  { label: '备注', value: detail.value.remark || '—', wide: true }
] : [])

function orgLabel(id: number) { return orgLabels.value.get(id) || `组织 #${id}` }
function userLabel(id: number) { return userLabels.value.get(id) || `用户 #${id}` }

async function loadReferences() {
  const results = await Promise.allSettled([
    loadOrganizationOptions('logical-subsystem', '', 100),
    loadUserOptions('logical-subsystem', '', 100),
    loadParameterOptions('logical-subsystem', 'ARCH_DEPLOYMENT_PLATFORM'),
    loadParameterOptions('logical-subsystem', 'ARCH_SYSTEM_TYPE'),
    loadParameterOptions('logical-subsystem', 'ARCH_SYSTEM_OWNERSHIP')
  ])
  if (results[0].status === 'fulfilled') organizations.value = results[0].value
  if (results[1].status === 'fulfilled') users.value = results[1].value
  if (results[2].status === 'fulfilled') deploymentPlatforms.value = results[2].value
  if (results[3].status === 'fulfilled') systemTypes.value = results[3].value
  if (results[4].status === 'fulfilled') ownerships.value = results[4].value
}

async function load() {
  if (!canView.value) return
  const request = ++listRequest
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listLogicalSubsystems({ page: page.value, size: pageSize.value, ...filters })
    if (request !== listRequest) return
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    if (request !== listRequest) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '逻辑子系统列表加载失败')
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function showDetail(row: LogicalSubsystem) {
  const request = ++detailRequest
  detail.value = row
  detailOpen.value = true
  detailLoading.value = true
  try {
    const result = await getLogicalSubsystem(row.id)
    if (request === detailRequest) detail.value = result
  } catch (error) {
    if (request === detailRequest) ElMessage.error(apiErrorMessage(error, '逻辑子系统详情加载失败'))
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}

function createApplication() {
  void router.push({ name: 'architecture-subsystem-change-application-new', query: { targetKind: 'LOGICAL' } })
}

function beginChange(row: LogicalSubsystem, command: string | number | object) {
  const actionType = String(command) as SubsystemActionType
  void router.push({
    name: 'architecture-subsystem-change-application-new',
    query: { targetKind: 'LOGICAL', actionType, targetId: row.id }
  })
}

function search() { page.value = 1; void load() }
function reset() { Object.assign(filters, { code: '', shortName: '', name: '', businessOrgId: null, status: '' }); page.value = 1; void load() }
async function refresh() { await Promise.all([load(), loadReferences()]); if (!loadError.value && !forbidden.value) ElMessage.success('列表已刷新') }
function changePage(value: number) { page.value = value; void load() }
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

watch(canView, allowed => {
  if (allowed) void Promise.all([load(), loadReferences()])
}, { immediate: true })
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader title="逻辑子系统" description="查看已发布的逻辑子系统事实；所有新增和生命周期变更均通过审批工单完成。">
      <template #actions><el-button v-if="canApply" type="primary" @click="createApplication"><el-icon><Plus /></el-icon>申请逻辑子系统</el-button></template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel"><el-result icon="warning" title="暂无逻辑子系统查看权限" sub-title="请申请 architecture:view 权限。" /></section>
    <section v-else-if="loadError" class="architecture-state-panel"><el-result icon="error" title="逻辑子系统加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="filters.code" clearable placeholder="系统编号" class="architecture-filter-input" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-input v-model="filters.shortName" clearable placeholder="系统简称" class="architecture-filter-input" @keyup.enter="search" />
        <el-input v-model="filters.name" clearable placeholder="系统名称" class="architecture-filter-input" @keyup.enter="search" />
        <el-select v-model="filters.businessOrgId" clearable filterable placeholder="所属事业群" class="architecture-filter-select"><el-option v-for="item in organizations" :key="item.id" :label="item.pathLabel" :value="item.id" /></el-select>
        <el-select v-model="filters.status" clearable placeholder="发布状态" class="architecture-filter-select"><el-option v-for="status in statusOptions" :key="status" :label="publishedStatusLabels[status]" :value="status" /></el-select>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
        <template #actions><el-tooltip content="刷新列表"><el-button circle :loading="loading" aria-label="刷新逻辑子系统列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button></el-tooltip></template>
      </UiToolbar>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="逻辑子系统" min-width="210"><template #default="scope"><button type="button" class="architecture-table-identity" @click="showDetail(scope.row)"><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }} · {{ scope.row.shortName }}</small></button></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="publishedStatusLabels" :tone="publishedStatusTone(scope.row.status)" /></template></el-table-column>
        <el-table-column label="所属事业群" min-width="150"><template #default="scope">{{ orgLabel(scope.row.businessOrgId) }}</template></el-table-column>
        <el-table-column label="部署平台" min-width="125"><template #default="scope">{{ optionLabel(deploymentPlatforms, scope.row.deploymentPlatformCode) }}</template></el-table-column>
        <el-table-column label="物理子系统" width="100"><template #default="scope">{{ scope.row.physicalSubsystems?.length || 0 }} 个</template></el-table-column>
        <el-table-column label="最后更新" width="145"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="scope">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="showDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
              <el-dropdown v-if="canApply && allowedPublishedActions('LOGICAL', scope.row.status).length" @command="beginChange(scope.row, $event)">
                <el-button link type="primary"><el-icon><MoreFilled /></el-icon>发起变更</el-button>
                <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="action in allowedPublishedActions('LOGICAL', scope.row.status)" :key="action" :command="action">{{ actionTypeLabels[action] }}</el-dropdown-item></el-dropdown-menu></template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #footer><div class="architecture-table-footer"><span>共 {{ total }} 条记录</span><el-pagination :current-page="page" :page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" /></div></template>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id">
          <header><div><strong>{{ row.name }}</strong><small>{{ row.code }} · {{ row.shortName }}</small></div><UiStatusTag :value="row.status" :labels="publishedStatusLabels" :tone="publishedStatusTone(row.status)" /></header>
          <dl><div><dt>所属事业群</dt><dd>{{ orgLabel(row.businessOrgId) }}</dd></div><div><dt>物理子系统</dt><dd>{{ row.physicalSubsystems?.length || 0 }} 个</dd></div><div><dt>联系人</dt><dd>{{ userLabel(row.contactUserId) }}</dd></div><div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div></dl>
          <footer><el-button link type="primary" @click="showDetail(row)"><el-icon><View /></el-icon>详情</el-button><el-dropdown v-if="canApply && allowedPublishedActions('LOGICAL', row.status).length" @command="beginChange(row, $event)"><el-button link type="primary"><el-icon><MoreFilled /></el-icon>发起变更</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="action in allowedPublishedActions('LOGICAL', row.status)" :key="action" :command="action">{{ actionTypeLabels[action] }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></footer>
        </article>
        <div class="architecture-table-footer"><el-pagination :current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="changePage" /></div>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" title="暂无逻辑子系统" description="调整筛选条件，或发起第一张逻辑子系统申请。"><template #action><el-button v-if="canApply" type="primary" @click="createApplication">发起申请</el-button><el-button v-else @click="reset">清空筛选</el-button></template></UiEmptyState>
    </template>

    <SubsystemDetailDrawer
      v-model="detailOpen"
      :loading="detailLoading"
      :title="detail?.name || '逻辑子系统详情'"
      :code="detail?.code"
      :items="detailItems"
      :physical-subsystems="detail?.physicalSubsystems || []"
    />
  </main>
</template>
