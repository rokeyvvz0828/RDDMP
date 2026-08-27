<!--
  用途：数迁基础资料 - 系统/组件清单页
  说明：维护各项目中涉及数据迁移的系统与组件。支持按项目/事业群/系统编号/负责团队/简称名称/总分核对/关键字筛选，
        12 列分页列表、筛选后 Excel 导出；新增时通过系统编号联动物理子系统带出只读元数据（不落库），
        修改仅允许变更"是否涉及总分核对"；覆盖加载/空/失败/无权限/提交中状态与移动端卡片化。
        基础资料子页面不展示标题横幅，定位依赖顶部 Tabs（见 T5-r8）。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  createDataMigrationComponent,
  deleteDataMigrationComponent,
  exportDataMigrationComponents,
  listDataMigrationComponents,
  listPhysicalSubsystemsByCode,
  updateDataMigrationComponent,
  type DataMigrationComponent
} from '../../../../api/data-migration'
import { getProjectWorkbench } from '../../../../api/project'
import type { Project } from '../../../../types/project'

const auth = useAuthStore()
const canManage = computed(() => auth.hasPermission('data-migration:manage'))

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const rows = ref<DataMigrationComponent[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const actionBusy = ref(false)

const projects = ref<Project[]>([])
const filters = reactive<Record<string, unknown>>({
  projectId: undefined,
  businessGroupName: '',
  systemCode: '',
  responsibleTeam: '',
  systemKeyword: '',
  totalCheck: undefined,
  keyword: ''
})
const advanced = ref(false)

function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}

function cancelled(error: unknown) {
  const action = (error as { action?: string }).action
  return action === 'cancel' || action === 'close'
}

function parseList(response: { data: { data: { records: DataMigrationComponent[]; total: number } } }) {
  return { records: response.data.data.records ?? [], total: response.data.data.total ?? 0 }
}

async function load() {
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const response = await listDataMigrationComponents({
      ...filters,
      page: page.value,
      size: pageSize.value
    })
    const { records, total: totalCount } = parseList(response)
    rows.value = records
    total.value = totalCount
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '组件列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  try {
    projects.value = (await getProjectWorkbench()).data.data ?? []
  } catch {
    projects.value = []
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  Object.assign(filters, {
    projectId: undefined,
    businessGroupName: '',
    systemCode: '',
    responsibleTeam: '',
    systemKeyword: '',
    totalCheck: undefined,
    keyword: ''
  })
  page.value = 1
  load()
}

function onPageChange(nextPage: number) {
  page.value = nextPage
  load()
}

function onSizeChange(nextSize: number) {
  pageSize.value = nextSize
  page.value = 1
  load()
}

async function exportExcel() {
  actionBusy.value = true
  try {
    const response = await exportDataMigrationComponents(filters)
    const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `系统组件清单_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(anchor)
    anchor.click()
    document.body.removeChild(anchor)
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '导出失败'))
  } finally {
    actionBusy.value = false
  }
}

/* ---------- 新增抽屉 ---------- */
interface SubsystemCandidate { code: string; shortName: string; name: string; businessGroupName?: string; description?: string; responsibleTeamDisplayName?: string }

const createOpen = ref(false)
const createSaving = ref(false)
const subsystemSearching = ref(false)
const subsystemForbidden = ref(false)
const subsystemCandidates = ref<SubsystemCandidate[]>([])
const createForm = reactive<{
  projectId?: number
  physicalSubsystemCode: string
  totalCheck: number
}>({ projectId: undefined, physicalSubsystemCode: '', totalCheck: 0 })

const selectedSubsystem = computed(() => subsystemCandidates.value.find(c => c.code === createForm.physicalSubsystemCode))

async function searchSubsystem() {
  if (!createForm.physicalSubsystemCode.trim()) return
  subsystemSearching.value = true
  subsystemForbidden.value = false
  try {
    const result = await listPhysicalSubsystemsByCode(createForm.physicalSubsystemCode.trim())
    subsystemCandidates.value = (result.data.data.records ?? []).map(r => ({
      code: r.code,
      shortName: r.shortName,
      name: r.name,
      businessGroupName: r.businessGroupName ?? undefined,
      description: r.description ?? undefined,
      responsibleTeamDisplayName: r.responsibleTeamDisplayName
    }))
    if (!subsystemCandidates.value.length) ElMessage.warning('未找到匹配的物理子系统')
  } catch (e) {
    if (httpStatus(e) === 403) {
      subsystemForbidden.value = true
      ElMessage.warning('缺少物理子系统查询权限，无法联动带出系统信息')
    } else {
      ElMessage.error(apiErrorMessage(e, '系统编号查询失败'))
    }
  } finally {
    subsystemSearching.value = false
  }
}

function openCreate() {
  Object.assign(createForm, { projectId: undefined, physicalSubsystemCode: '', totalCheck: 0 })
  subsystemCandidates.value = []
  subsystemForbidden.value = false
  createOpen.value = true
}

async function submitCreate() {
  if (!createForm.projectId) return ElMessage.warning('请选择所属项目')
  if (!createForm.physicalSubsystemCode.trim()) return ElMessage.warning('请输入系统编号')
  createSaving.value = true
  try {
    await createDataMigrationComponent({
      projectId: createForm.projectId,
      physicalSubsystemCode: createForm.physicalSubsystemCode.trim(),
      totalCheck: createForm.totalCheck
    })
    ElMessage.success('新增成功')
    createOpen.value = false
    await load()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '新增失败'))
  } finally {
    createSaving.value = false
  }
}

/* ---------- 编辑抽屉（仅总分核对） ---------- */
const editOpen = ref(false)
const editSaving = ref(false)
const editing = ref<DataMigrationComponent | null>(null)
const editTotalCheck = ref(0)

function openEdit(row: DataMigrationComponent) {
  editing.value = row
  editTotalCheck.value = row.total_check
  editOpen.value = true
}

async function submitEdit() {
  if (!editing.value) return
  editSaving.value = true
  try {
    await updateDataMigrationComponent(editing.value.id, { totalCheck: editTotalCheck.value })
    ElMessage.success('修改成功')
    editOpen.value = false
    await load()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '修改失败'))
  } finally {
    editSaving.value = false
  }
}

async function removeComponent(row: DataMigrationComponent) {
  try {
    await ElMessageBox.confirm(`确认删除组件「${row.system_name || row.physical_subsystem_code}」（系统编号 ${row.physical_subsystem_code}）吗？删除后可通过回收站恢复。`, '删除组件', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    actionBusy.value = true
    await deleteDataMigrationComponent(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (!cancelled(e)) ElMessage.error(apiErrorMessage(e, '删除失败'))
  } finally {
    actionBusy.value = false
  }
}

/* ---------- 查看详情 ---------- */
const viewOpen = ref(false)
const viewing = ref<DataMigrationComponent | null>(null)

function openView(row: DataMigrationComponent) {
  viewing.value = row
  viewOpen.value = true
}

onMounted(() => {
  loadProjects()
  load()
})
</script>

<template>
  <main class="dm-page-root components-page">
    <section v-if="forbidden" class="dm-state-panel"><el-result icon="warning" title="暂无组件清单查看权限" sub-title="请向数据迁移管理员申请组件清单管理权限。" /></section>
    <section v-else-if="error" class="dm-state-panel"><el-result icon="error" title="组件清单加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-select v-model="filters.projectId" clearable filterable placeholder="所属项目" class="components-filter-select" style="width: 190px">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.project_name}（${p.project_code}）`" :value="p.id" />
        </el-select>
        <el-input v-model="filters.systemCode" clearable placeholder="系统编号" style="width: 160px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input v-model="filters.systemKeyword" clearable placeholder="系统简称/名称" style="width: 170px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.totalCheck" clearable placeholder="总分核对" style="width: 120px">
          <el-option label="是" :value="1" />
          <el-option label="否" :value="0" />
        </el-select>
        <el-button v-if="!advanced" link type="primary" @click="advanced = true">更多筛选</el-button>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
          <el-button :disabled="loading || actionBusy" @click="search"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="loading" @click="resetFilters">重置</el-button>
          <el-button :disabled="loading || actionBusy" @click="exportExcel"><el-icon><Download /></el-icon>导出 Excel</el-button>
          <el-button v-if="canManage" type="primary" :disabled="loading || actionBusy" @click="openCreate"><el-icon><Plus /></el-icon>新增组件</el-button>
        </template>
      </UiToolbar>

      <section v-if="advanced" class="components-advanced-filter">
        <el-form label-width="84px">
          <el-form-item label="事业群"><el-input v-model="filters.businessGroupName" clearable placeholder="事业群模糊匹配" @keyup.enter="search" /></el-form-item>
          <el-form-item label="负责团队"><el-input v-model="filters.responsibleTeam" clearable placeholder="负责团队模糊匹配" @keyup.enter="search" /></el-form-item>
          <el-form-item label="关键字"><el-input v-model="filters.keyword" clearable placeholder="系统编号/名称/简称模糊" @keyup.enter="search" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="advanced = false">收起</el-button>
          </el-form-item>
        </el-form>
      </section>

      <div v-if="rows.length || loading" class="components-desktop-table">
        <UiDataTable :data="rows" :loading="loading" row-key="id" border empty-text="暂无组件数据">
          <el-table-column prop="project_name" label="所属项目" min-width="150" show-overflow-tooltip />
          <el-table-column prop="business_group_name" label="所属事业群" min-width="120" show-overflow-tooltip />
          <el-table-column prop="physical_subsystem_code" label="系统编号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="system_short_name" label="系统简称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="system_name" label="系统名称" min-width="170" show-overflow-tooltip />
          <el-table-column prop="system_description" label="系统描述" min-width="180" show-overflow-tooltip />
          <el-table-column prop="responsible_team_name" label="负责团队" min-width="130" show-overflow-tooltip />
          <el-table-column label="总分核对" width="100" align="center">
            <template #default="{ row }"><el-tag :type="row.total_check === 1 ? 'success' : 'info'" effect="plain" size="small">{{ row.total_check === 1 ? '是' : '否' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="created_at" label="创建时间" min-width="160" show-overflow-tooltip />
          <el-table-column prop="created_by_name" label="创建人" min-width="100" show-overflow-tooltip />
          <el-table-column prop="updated_at" label="更新时间" min-width="160" show-overflow-tooltip />
          <el-table-column prop="updated_by_name" label="更新人" min-width="100" show-overflow-tooltip />
          <el-table-column label="操作" width="200" fixed="right" align="center">
            <template #default="{ row }">
              <div class="dm-table-actions">
                <el-button link type="primary" :disabled="actionBusy" @click="openView(row)"><el-icon><View /></el-icon>查看</el-button>
                <el-button v-if="canManage" link type="primary" :disabled="actionBusy" @click="openEdit(row)"><el-icon><Edit /></el-icon>修改</el-button>
                <el-button v-if="canManage" link type="danger" :disabled="actionBusy" @click="removeComponent(row)"><el-icon><Delete /></el-icon>删除</el-button>
              </div>
            </template>
          </el-table-column>
          <template #footer>
            <div class="dm-table-footer">
              <span>共 {{ total }} 条</span>
              <el-pagination background layout="total, sizes, prev, pager, next" :total="total" :current-page="page" :page-size="pageSize" :page-sizes="[20, 50, 100]" @current-change="onPageChange" @size-change="onSizeChange" />
            </div>
          </template>
        </UiDataTable>
      </div>

      <div v-if="rows.length || loading" class="dm-mobile-list">
        <article v-for="row in rows" :key="row.id">
          <header>
            <div>
              <strong>{{ row.project_name }}</strong>
              <small>{{ row.physical_subsystem_code }} · {{ row.system_name }}</small>
            </div>
            <el-tag :type="row.total_check === 1 ? 'success' : 'info'" effect="plain" size="small">总分核对：{{ row.total_check === 1 ? '是' : '否' }}</el-tag>
          </header>
          <dl>
            <div><dt>系统编号</dt><dd>{{ row.physical_subsystem_code }}</dd></div>
            <div><dt>事业群</dt><dd>{{ row.business_group_name }}</dd></div>
            <div><dt>系统简称</dt><dd>{{ row.system_short_name }}</dd></div>
            <div><dt>负责团队</dt><dd>{{ row.responsible_team_name }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ row.created_at }}</dd></div>
            <div><dt>创建人</dt><dd>{{ row.created_by_name }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ row.updated_at }}</dd></div>
            <div><dt>更新人</dt><dd>{{ row.updated_by_name }}</dd></div>
          </dl>
          <footer>
            <el-button link type="primary" :disabled="actionBusy" @click="openView(row)"><el-icon><View /></el-icon>查看</el-button>
            <el-button v-if="canManage" link type="primary" :disabled="actionBusy" @click="openEdit(row)"><el-icon><Edit /></el-icon>修改</el-button>
            <el-button v-if="canManage" link type="danger" :disabled="actionBusy" @click="removeComponent(row)"><el-icon><Delete /></el-icon>删除</el-button>
          </footer>
        </article>
        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <el-pagination background layout="prev, pager, next" :total="total" :current-page="page" :page-size="pageSize" @current-change="onPageChange" />
        </div>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" title="暂无组件数据" description="当前筛选条件下没有组件记录，可通过「新增组件」录入，或调整筛选条件。" />
    </template>

    <UiFormDrawer v-model="createOpen" title="新增组件" width="560px" :loading="createSaving" confirm-text="保存" @submit="submitCreate">
      <el-form label-width="96px" label-position="left">
        <el-form-item label="所属项目" required>
          <el-select v-model="createForm.projectId" filterable placeholder="请选择所属项目" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.project_name}（${p.project_code}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="系统编号" required>
          <div class="components-subsystem-search">
            <el-input v-model="createForm.physicalSubsystemCode" placeholder="输入物理子系统编号" @keyup.enter="searchSubsystem" />
            <el-button type="primary" :loading="subsystemSearching" @click="searchSubsystem">查询</el-button>
          </div>
        </el-form-item>
        <template v-if="selectedSubsystem">
          <el-form-item label="所属事业群"><el-input :model-value="selectedSubsystem.businessGroupName ?? '-'" disabled /></el-form-item>
          <el-form-item label="系统简称"><el-input :model-value="selectedSubsystem.shortName" disabled /></el-form-item>
          <el-form-item label="系统名称"><el-input :model-value="selectedSubsystem.name" disabled /></el-form-item>
          <el-form-item label="系统描述"><el-input :model-value="selectedSubsystem.description ?? '-'" disabled /></el-form-item>
          <el-form-item label="负责团队"><el-input :model-value="selectedSubsystem.responsibleTeamDisplayName" disabled /></el-form-item>
        </template>
        <el-alert v-else-if="subsystemForbidden" type="warning" :closable="false" show-icon title="缺少物理子系统查询权限，无法联动带出系统信息，请先在权限管理中授权架构模块查询权限。" class="components-subsystem-alert" />
        <el-alert v-else type="info" :closable="false" show-icon title="输入系统编号后点击「查询」，系统信息将自动带出（仅展示、不保存）。" class="components-subsystem-alert" />
        <el-form-item label="总分核对" required>
          <el-radio-group v-model="createForm.totalCheck">
            <el-radio :value="0">否</el-radio>
            <el-radio :value="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </UiFormDrawer>

    <UiFormDrawer v-model="editOpen" title="修改组件" width="560px" :loading="editSaving" confirm-text="保存" @submit="submitEdit">
      <el-form label-width="96px" label-position="left">
        <el-form-item label="所属项目"><el-input :model-value="editing?.project_name" disabled /></el-form-item>
        <el-form-item label="系统编号"><el-input :model-value="editing?.physical_subsystem_code" disabled /></el-form-item>
        <el-form-item label="所属事业群"><el-input :model-value="editing?.business_group_name || '-'" disabled /></el-form-item>
        <el-form-item label="系统简称"><el-input :model-value="editing?.system_short_name" disabled /></el-form-item>
        <el-form-item label="系统名称"><el-input :model-value="editing?.system_name" disabled /></el-form-item>
        <el-form-item label="系统描述"><el-input :model-value="editing?.system_description || '-'" type="textarea" :rows="2" disabled /></el-form-item>
        <el-form-item label="负责团队"><el-input :model-value="editing?.responsible_team_name || '-'" disabled /></el-form-item>
        <el-form-item label="总分核对" required>
          <el-radio-group v-model="editTotalCheck">
            <el-radio :value="0">否</el-radio>
            <el-radio :value="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon title="组件其他信息由系统编号联动物理子系统维护，仅允许修改「是否涉及总分核对」。" class="components-subsystem-alert" />
      </el-form>
    </UiFormDrawer>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="viewOpen" title="组件详情" width="560px" :close-on-click-modal="true" align-center destroy-on-close>
      <el-form label-width="96px" label-position="left">
        <el-form-item label="所属项目"><el-input :model-value="viewing?.project_name" disabled /></el-form-item>
        <el-form-item label="系统编号"><el-input :model-value="viewing?.physical_subsystem_code" disabled /></el-form-item>
        <el-form-item label="所属事业群"><el-input :model-value="viewing?.business_group_name || '-'" disabled /></el-form-item>
        <el-form-item label="系统简称"><el-input :model-value="viewing?.system_short_name" disabled /></el-form-item>
        <el-form-item label="系统名称"><el-input :model-value="viewing?.system_name" disabled /></el-form-item>
        <el-form-item label="系统描述"><el-input :model-value="viewing?.system_description || '-'" type="textarea" :rows="2" disabled /></el-form-item>
        <el-form-item label="负责团队"><el-input :model-value="viewing?.responsible_team_name || '-'" disabled /></el-form-item>
        <el-form-item label="总分核对">
          <el-tag :type="viewing?.total_check === 1 ? 'success' : 'info'" effect="plain" size="small">{{ viewing?.total_check === 1 ? '是' : '否' }}</el-tag>
        </el-form-item>
        <el-form-item label="创建时间"><el-input :model-value="viewing?.created_at" disabled /></el-form-item>
        <el-form-item label="创建人"><el-input :model-value="viewing?.created_by_name" disabled /></el-form-item>
        <el-form-item label="更新时间"><el-input :model-value="viewing?.updated_at" disabled /></el-form-item>
        <el-form-item label="更新人"><el-input :model-value="viewing?.updated_by_name" disabled /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="viewOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.components-page .ui-toolbar { align-items: flex-start; }
.components-page .ui-toolbar__filters, .components-page .ui-toolbar__actions { flex-wrap: wrap; }
.components-page .dm-table-actions { flex-wrap: nowrap; }
.components-page .dm-state-panel { padding: 0; }
.components-advanced-filter { margin: -6px 0 16px; padding: 14px 16px 0; background: var(--panel-bg); border: 1px solid var(--line); border-radius: 6px; }
.components-advanced-filter .el-form { display: flex; flex-wrap: wrap; gap: 0 14px; }
.components-subsystem-search { display: flex; width: 100%; gap: 8px; }
.components-subsystem-alert { width: 100%; margin-bottom: 16px; }

@media (max-width: 760px) {
  .components-page .ui-toolbar__filters, .components-page .ui-toolbar__actions { width: 100%; }
  .components-filter-select, .components-page .ui-toolbar .el-input, .components-page .ui-toolbar .el-select { width: 100% !important; }
  .components-advanced-filter .el-form-item,
  .components-advanced-filter .el-input,
  .components-advanced-filter .el-select { width: 100% !important; }
  .components-page .ui-toolbar__filters > .el-button, .components-page .ui-toolbar__actions > .el-button { flex: 1; }
  .components-desktop-table { display: none; }
}
</style>
