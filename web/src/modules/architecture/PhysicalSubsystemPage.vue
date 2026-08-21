<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Delete, Edit, Filter, MoreFilled, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  deletePhysicalSubsystem,
  getPhysicalSubsystem,
  listPhysicalSubsystems,
  loadLogicalSubsystemOptions,
  loadOrganizationOptions,
  loadParameterOptions
} from './api'
import PhysicalSubsystemFormDrawer from './components/PhysicalSubsystemFormDrawer.vue'
import SubsystemDetailDrawer from './components/SubsystemDetailDrawer.vue'
import { cancelled, formatDateTime, httpStatus, optionLabel } from './utils'
import type { DetailItem, LogicalSubsystemOption, OrganizationOption, ParameterOption, PhysicalSubsystem } from './types'
import './architecture.css'

const auth = useAuthStore()
const rows = ref<PhysicalSubsystem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const advanced = ref(false)
const formOpen = ref(false)
const editing = ref<PhysicalSubsystem | null>(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<PhysicalSubsystem | null>(null)
const organizations = ref<OrganizationOption[]>([])
const logicalSubsystems = ref<LogicalSubsystemOption[]>([])
const runtimes = ref<ParameterOption[]>([])
const levels = ref<ParameterOption[]>([])
const frameworks = ref<ParameterOption[]>([])
const filters = reactive({ code: '', shortName: '', name: '', businessGroupName: '', responsibleTeamOrgId: null as number | null, logicalSubsystemId: null as number | null })
let listRequest = 0
let detailRequest = 0

const permissionSet = computed(() => new Set(auth.user?.permissions || []))
const canList = computed(() => permissionSet.value.has('architecture:physical:list'))
const canCreate = computed(() => permissionSet.value.has('architecture:physical:create'))
const canUpdate = computed(() => permissionSet.value.has('architecture:physical:update'))
const canDelete = computed(() => permissionSet.value.has('architecture:physical:delete'))
const detailItems = computed<DetailItem[]>(() => detail.value ? [
  { label: 'ID', value: String(detail.value.id) },
  { label: '系统简称', value: detail.value.shortName },
  { label: '所属逻辑子系统', value: `${detail.value.logicalSubsystemName}（${detail.value.logicalSubsystemCode}）` },
  { label: '所属事业群', value: detail.value.businessGroupName || '—' },
  { label: '负责团队', value: detail.value.responsibleTeamDisplayName, tone: detail.value.responsibleTeamValid ? undefined : 'warning' },
  { label: '团队引用', value: detail.value.responsibleTeamValid ? '当前有效' : '已失效，编辑时必须重选', tone: detail.value.responsibleTeamValid ? undefined : 'warning' },
  { label: '系统运行时间', value: optionLabel(runtimes.value, detail.value.runtimeCode) },
  { label: '系统级别', value: optionLabel(levels.value, detail.value.systemLevelCode) },
  { label: '开发平台框架', value: optionLabel(frameworks.value, detail.value.developmentFrameworkCode) },
  { label: '负责人', value: detail.value.ownerDisplayName || '—' },
  { label: '创建人', value: detail.value.createdByDisplayName || `用户 #${detail.value.createdBy}` },
  { label: '创建时间', value: formatDateTime(detail.value.createdAt) },
  { label: '最后更新', value: formatDateTime(detail.value.updatedAt) },
  { label: '系统描述', value: detail.value.description || '—', wide: true },
  { label: '备注', value: detail.value.remark || '—', wide: true }
] : [])

async function loadReferences() {
  const results = await Promise.allSettled([
    loadOrganizationOptions('physical-subsystem', '', 100), loadLogicalSubsystemOptions('', 100),
    loadParameterOptions('physical-subsystem', 'ARCH_RUNTIME'),
    loadParameterOptions('physical-subsystem', 'ARCH_SYSTEM_LEVEL'),
    loadParameterOptions('physical-subsystem', 'ARCH_DEVELOPMENT_FRAMEWORK')
  ])
  if (results[0].status === 'fulfilled') organizations.value = results[0].value
  if (results[1].status === 'fulfilled') logicalSubsystems.value = results[1].value
  if (results[2].status === 'fulfilled') runtimes.value = results[2].value
  if (results[3].status === 'fulfilled') levels.value = results[3].value
  if (results[4].status === 'fulfilled') frameworks.value = results[4].value
}

async function load() {
  if (!canList.value) return
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
  } finally { if (request === listRequest) loading.value = false }
}

function search() { page.value = 1; void load() }
function reset() { Object.assign(filters, { code: '', shortName: '', name: '', businessGroupName: '', responsibleTeamOrgId: null, logicalSubsystemId: null }); page.value = 1; void load() }
async function refresh() { await Promise.all([load(), loadReferences()]); if (!loadError.value && !forbidden.value) ElMessage.success('列表已刷新') }
function createRecord() { editing.value = null; formOpen.value = true }

async function editRecord(row: PhysicalSubsystem) {
  try {
    editing.value = await getPhysicalSubsystem(row.id)
    formOpen.value = true
  } catch (error) { ElMessage.error(apiErrorMessage(error, '物理子系统详情加载失败')) }
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
  } finally { if (request === detailRequest) detailLoading.value = false }
}

async function remove(row: PhysicalSubsystem) {
  try {
    await ElMessageBox.confirm(`确认删除物理子系统“${row.name}”吗？删除后不可恢复，但不会删除所属逻辑子系统。`, '删除物理子系统', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await deletePhysicalSubsystem(row.id)
    ElMessage.success('物理子系统已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await load()
  } catch (error) {
    if (!cancelled(error)) ElMessage.error(apiErrorMessage(error, '物理子系统删除失败'))
  }
}

function command(action: string, row: PhysicalSubsystem) { if (action === 'delete') void remove(row) }
function saved() { formOpen.value = false; void Promise.all([load(), loadReferences()]) }
function changePage(value: number) { page.value = value; void load() }
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

watch(canList, allowed => {
  if (allowed) void Promise.all([load(), loadReferences()])
}, { immediate: true })
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader title="物理子系统" description="维护可部署的物理系统、所属逻辑系统、事业群、负责团队和负责人信息。">
      <template #actions><el-button v-if="canCreate" type="primary" @click="createRecord"><el-icon><Plus /></el-icon>新建物理子系统</el-button></template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canList || forbidden" class="architecture-state-panel"><el-result icon="warning" title="暂无物理子系统查看权限" sub-title="请向管理员申请 architecture:physical:list 权限。" /></section>
    <section v-else-if="loadError" class="architecture-state-panel"><el-result icon="error" title="物理子系统加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-input v-model="filters.code" clearable placeholder="系统编号" class="architecture-filter-input" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-input v-model="filters.shortName" clearable placeholder="系统简称" class="architecture-filter-input" @keyup.enter="search" />
        <el-input v-model="filters.name" clearable placeholder="系统名称" class="architecture-filter-input" @keyup.enter="search" />
        <el-button :type="advanced ? 'primary' : 'default'" plain @click="advanced = !advanced"><el-icon><Filter /></el-icon>更多筛选</el-button>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
        <template #actions><el-tooltip content="刷新列表"><el-button circle :loading="loading" aria-label="刷新物理子系统列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button></el-tooltip></template>
      </UiToolbar>
      <div v-if="advanced" class="architecture-advanced-filter"><el-form inline label-position="top"><el-form-item label="所属事业群"><el-input v-model="filters.businessGroupName" clearable placeholder="事业群名称" style="width:190px" /></el-form-item><el-form-item label="负责团队"><el-select v-model="filters.responsibleTeamOrgId" clearable filterable style="width:220px"><el-option v-for="item in organizations" :key="item.id" :label="item.pathLabel" :value="item.id" /></el-select></el-form-item><el-form-item label="所属逻辑子系统"><el-select v-model="filters.logicalSubsystemId" clearable filterable style="width:230px"><el-option v-for="item in logicalSubsystems" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="search">应用筛选</el-button></el-form-item></el-form></div>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="物理子系统" min-width="190"><template #default="scope"><button type="button" class="architecture-table-identity" @click="showDetail(scope.row)"><strong>{{ scope.row.name }}</strong><small>{{ scope.row.code }} · {{ scope.row.shortName }}</small></button></template></el-table-column>
        <el-table-column label="所属逻辑子系统" min-width="155"><template #default="scope">{{ scope.row.logicalSubsystemName }}<small class="architecture-inline-code">{{ scope.row.logicalSubsystemCode }}</small></template></el-table-column>
        <el-table-column prop="businessGroupName" label="所属事业群" min-width="105"><template #default="scope">{{ scope.row.businessGroupName || '—' }}</template></el-table-column>
        <el-table-column label="负责团队" min-width="130"><template #default="scope"><div class="architecture-team-cell"><span>{{ scope.row.responsibleTeamDisplayName }}</span><UiStatusTag v-if="!scope.row.responsibleTeamValid" :value="false" :labels="{ false: '已失效' }" tone="warning" /></div></template></el-table-column>
        <el-table-column label="负责人" width="90"><template #default="scope">{{ scope.row.ownerDisplayName || '—' }}</template></el-table-column>
        <el-table-column label="最后更新" width="125"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="214" fixed="right">
          <template #default="scope">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="showDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button v-if="canUpdate" link type="primary" @click="editRecord(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-dropdown v-if="canDelete" @command="command($event, scope.row)">
                <el-button link type="info"><el-icon><MoreFilled /></el-icon>更多</el-button>
                <template #dropdown><el-dropdown-menu><el-dropdown-item command="delete"><el-icon><Delete /></el-icon>删除</el-dropdown-item></el-dropdown-menu></template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
        <template #footer><div class="architecture-table-footer"><span>共 {{ total }} 条记录</span><el-pagination :current-page="page" :page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" /></div></template>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id"><header><div><strong>{{ row.name }}</strong><small>{{ row.code }} · {{ row.shortName }}</small></div><UiStatusTag v-if="!row.responsibleTeamValid" :value="false" :labels="{ false: '团队失效' }" tone="warning" /></header><dl><div><dt>所属逻辑子系统</dt><dd>{{ row.logicalSubsystemName }}</dd></div><div><dt>所属事业群</dt><dd>{{ row.businessGroupName || '—' }}</dd></div><div><dt>负责团队</dt><dd>{{ row.responsibleTeamDisplayName }}</dd></div><div><dt>负责人</dt><dd>{{ row.ownerDisplayName || '—' }}</dd></div></dl><footer><el-button link type="primary" @click="showDetail(row)"><el-icon><View /></el-icon>详情</el-button><el-button v-if="canUpdate" link type="primary" @click="editRecord(row)"><el-icon><Edit /></el-icon>编辑</el-button><el-dropdown v-if="canDelete" @command="command($event, row)"><el-button link type="info"><el-icon><MoreFilled /></el-icon>更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="delete"><el-icon><Delete /></el-icon>删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></footer></article>
        <div class="architecture-table-footer"><el-pagination :current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="changePage" /></div>
      </div>
      <UiEmptyState v-if="!loading && !rows.length" title="暂无物理子系统" description="调整筛选条件，或新建第一条物理子系统。"><template #action><el-button v-if="canCreate" type="primary" @click="createRecord">新建物理子系统</el-button><el-button v-else @click="reset">清空筛选</el-button></template></UiEmptyState>
    </template>

    <PhysicalSubsystemFormDrawer v-model="formOpen" :record="editing" @saved="saved" />
    <SubsystemDetailDrawer v-model="detailOpen" :loading="detailLoading" :title="detail?.name || '物理子系统详情'" :code="detail?.code" :items="detailItems" />
  </main>
</template>
