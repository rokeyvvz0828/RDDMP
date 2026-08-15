<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Delete, MoreFilled, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  deleteLogicalSubsystem,
  getLogicalSubsystem,
  listLogicalSubsystems,
  loadOrganizationOptions,
  loadParameterOptions,
  loadUserOptions
} from './api'
import LogicalSubsystemFormDrawer from './components/LogicalSubsystemFormDrawer.vue'
import SubsystemDetailDrawer from './components/SubsystemDetailDrawer.vue'
import { cancelled, formatDateTime, httpStatus, optionLabel } from './utils'
import type { DetailItem, LogicalSubsystem, OrganizationOption, ParameterOption, UserOption } from './types'
import './architecture.css'

const auth = useAuthStore()
const rows = ref<LogicalSubsystem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const formOpen = ref(false)
const editing = ref<LogicalSubsystem | null>(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<LogicalSubsystem | null>(null)
const organizations = ref<OrganizationOption[]>([])
const users = ref<UserOption[]>([])
const deploymentPlatforms = ref<ParameterOption[]>([])
const systemTypes = ref<ParameterOption[]>([])
const ownerships = ref<ParameterOption[]>([])
const filters = reactive({ code: '', shortName: '', name: '', businessOrgId: null as number | null })
let listRequest = 0
let detailRequest = 0

const permissionSet = computed(() => new Set(auth.user?.permissions || []))
const canList = computed(() => permissionSet.value.has('architecture:logical:list'))
const canCreate = computed(() => permissionSet.value.has('architecture:logical:create'))
const canUpdate = computed(() => permissionSet.value.has('architecture:logical:update'))
const canDelete = computed(() => permissionSet.value.has('architecture:logical:delete'))
const orgLabels = computed(() => new Map(organizations.value.map(item => [item.id, item.pathLabel])))
const userLabels = computed(() => new Map(users.value.map(item => [item.id, item.displayName])))
const detailItems = computed<DetailItem[]>(() => detail.value ? [
  { label: 'ID', value: String(detail.value.id) },
  { label: '系统简称', value: detail.value.shortName },
  { label: '所属事业群', value: orgLabel(detail.value.businessOrgId) },
  { label: '联系人', value: userLabel(detail.value.contactUserId) },
  { label: '部署平台', value: optionLabel(deploymentPlatforms.value, detail.value.deploymentPlatformCode) },
  { label: '系统类型', value: optionLabel(systemTypes.value, detail.value.systemTypeCode) },
  { label: '系统归属', value: optionLabel(ownerships.value, detail.value.systemOwnershipCode) },
  { label: '创建人 ID', value: String(detail.value.createdBy) },
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
  if (!canList.value) return
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

function search() { page.value = 1; void load() }
function reset() { Object.assign(filters, { code: '', shortName: '', name: '', businessOrgId: null }); page.value = 1; void load() }
async function refresh() { await Promise.all([load(), loadReferences()]); if (!loadError.value && !forbidden.value) ElMessage.success('列表已刷新') }
function createRecord() { editing.value = null; formOpen.value = true }

async function editRecord(row: LogicalSubsystem) {
  try {
    editing.value = await getLogicalSubsystem(row.id)
    formOpen.value = true
  } catch (error) { ElMessage.error(apiErrorMessage(error, '逻辑子系统详情加载失败')) }
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
  } finally { if (request === detailRequest) detailLoading.value = false }
}

async function remove(row: LogicalSubsystem) {
  try {
    await ElMessageBox.confirm(`确认删除逻辑子系统“${row.name}”吗？删除后不可恢复；若仍有物理子系统引用，服务端将拒绝删除。`, '删除逻辑子系统', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await deleteLogicalSubsystem(row.id)
    ElMessage.success('逻辑子系统已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch (error) {
    if (!cancelled(error)) ElMessage.error(apiErrorMessage(error, '逻辑子系统删除失败'))
  }
}

function command(action: string, row: LogicalSubsystem) { if (action === 'delete') void remove(row) }
function saved() { formOpen.value = false; void Promise.all([load(), loadReferences()]) }
function changePage(value: number) { page.value = value; void load() }
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

watch(canList, allowed => {
  if (!allowed) return
  void Promise.all([load(), loadReferences()])
}, { immediate: true })
onMounted(() => { if (canList.value && !rows.value.length && !loading.value) void load() })
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader title="逻辑子系统" description="维护业务视角的逻辑系统边界，以及事业群、联系人和受控分类信息。">
      <template #actions><el-button v-if="canCreate" type="primary" @click="createRecord"><el-icon><Plus /></el-icon>新建逻辑子系统</el-button></template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canList || forbidden" class="architecture-state-panel"><el-result icon="warning" title="暂无逻辑子系统查看权限" sub-title="请向管理员申请 architecture:logical:list 权限。" /></section>
    <section v-else-if="loadError" class="architecture-state-panel"><el-result icon="error" title="逻辑子系统加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="filters.code" clearable placeholder="系统编号" class="architecture-filter-input" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-input v-model="filters.shortName" clearable placeholder="系统简称" class="architecture-filter-input" @keyup.enter="search" />
        <el-input v-model="filters.name" clearable placeholder="系统名称" class="architecture-filter-input" @keyup.enter="search" />
        <el-select v-model="filters.businessOrgId" clearable filterable placeholder="所属事业群" class="architecture-filter-select"><el-option v-for="item in organizations" :key="item.id" :label="item.pathLabel" :value="item.id" /></el-select>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
        <template #actions><el-tooltip content="刷新列表"><el-button circle :loading="loading" aria-label="刷新逻辑子系统列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button></el-tooltip></template>
      </UiToolbar>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="逻辑子系统" min-width="190"><template #default="scope"><button type="button" class="architecture-table-identity" @click="showDetail(scope.row)"><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }} · {{ scope.row.shortName }}</small></button></template></el-table-column>
        <el-table-column label="所属事业群" min-width="150"><template #default="scope">{{ orgLabel(scope.row.businessOrgId) }}</template></el-table-column>
        <el-table-column label="部署平台" min-width="125"><template #default="scope">{{ optionLabel(deploymentPlatforms, scope.row.deploymentPlatformCode) }}</template></el-table-column>
        <el-table-column label="联系人" width="100"><template #default="scope">{{ userLabel(scope.row.contactUserId) }}</template></el-table-column>
        <el-table-column label="最后更新" width="125"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="144" fixed="right"><template #default="scope"><el-button link type="primary" @click="showDetail(scope.row)">详情</el-button><el-button v-if="canUpdate" link type="primary" @click="editRecord(scope.row)">编辑</el-button><el-dropdown v-if="canDelete" @command="command($event, scope.row)"><el-button link type="info">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="delete"><el-icon><Delete /></el-icon>删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
        <template #footer><div class="architecture-table-footer"><span>共 {{ total }} 条记录</span><el-pagination :current-page="page" :page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" /></div></template>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id"><header><div><strong>{{ row.name }}</strong><small>{{ row.code }} · {{ row.shortName }}</small></div></header><dl><div><dt>所属事业群</dt><dd>{{ orgLabel(row.businessOrgId) }}</dd></div><div><dt>部署平台</dt><dd>{{ optionLabel(deploymentPlatforms, row.deploymentPlatformCode) }}</dd></div><div><dt>联系人</dt><dd>{{ userLabel(row.contactUserId) }}</dd></div><div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div></dl><footer><el-button link type="primary" @click="showDetail(row)">查看详情</el-button><el-button v-if="canUpdate" link type="primary" @click="editRecord(row)">编辑</el-button><el-dropdown v-if="canDelete" @command="command($event, row)"><el-button link type="info">更多操作<el-icon><MoreFilled /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="delete">删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></footer></article>
        <div class="architecture-table-footer"><el-pagination :current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="changePage" /></div>
      </div>
      <UiEmptyState v-if="!loading && !rows.length" title="暂无逻辑子系统" description="调整筛选条件，或新建第一条逻辑子系统。"><template #action><el-button v-if="canCreate" type="primary" @click="createRecord">新建逻辑子系统</el-button><el-button v-else @click="reset">清空筛选</el-button></template></UiEmptyState>
    </template>

    <LogicalSubsystemFormDrawer v-model="formOpen" :record="editing" @saved="saved" />
    <SubsystemDetailDrawer v-model="detailOpen" :loading="detailLoading" :title="detail?.name || '逻辑子系统详情'" :code="detail?.code" :items="detailItems" />
  </main>
</template>
