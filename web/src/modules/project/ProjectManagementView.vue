<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Edit, Lock, MoreFilled, Plus, Refresh, Search, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { isAxiosError } from 'axios'
import { apiErrorMessage } from '../../api/error'
import {
  archiveProject,
  createProject,
  listProjects,
  restoreProject,
  updateProject
} from '../../api/projects'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiPagination from '../../components/ui/UiPagination.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { useAuthStore } from '../../stores/auth'
import { useProjectContextStore } from '../../stores/project-context'
import type {
  CreateProjectCommand,
  ProjectAction,
  ProjectRole,
  ProjectStatus,
  ProjectSummary
} from '../../types/project-context'
import ProjectFormDrawer from './components/ProjectFormDrawer.vue'
import ProjectMemberDialog from './components/ProjectMemberDialog.vue'
import './project.css'

const auth = useAuthStore()
const projectContext = useProjectContextStore()
const records = ref<ProjectSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const status = ref<ProjectStatus | ''>('')
const loading = ref(false)
const forbidden = ref(false)
const listError = ref('')
const operationError = ref('')
const saving = ref(false)
const formOpen = ref(false)
const editingProject = ref<ProjectSummary | null>(null)
const memberDialogOpen = ref(false)
const selectedProject = ref<ProjectSummary | null>(null)
let requestSequence = 0

const permissions = computed(() => new Set(auth.user?.permissions || []))
const canCreate = computed(() => permissions.value.has('project:list:create'))
const statusLabels: Record<ProjectStatus, string> = { ACTIVE: '进行中', ARCHIVED: '已归档' }
const roleLabels: Record<ProjectRole, string> = {
  OWNER: '负责人',
  ADMIN: '项目管理员',
  MEMBER: '项目成员',
  VIEWER: '只读成员'
}

function hasPermission(permission: string) {
  return permissions.value.has(permission)
}

function hasAction(project: ProjectSummary, action: ProjectAction) {
  return project.allowedActions.includes(action)
}

function canEdit(project: ProjectSummary) {
  return project.status === 'ACTIVE' && hasPermission('project:list:update') && hasAction(project, 'MANAGE_PROJECT')
}

function canManageMembers(project: ProjectSummary | null) {
  return Boolean(project && project.status === 'ACTIVE' && hasPermission('project:list:member') && hasAction(project, 'MANAGE_MEMBERS'))
}

function canTransferOwner(project: ProjectSummary | null) {
  return Boolean(project && canManageMembers(project) && hasAction(project, 'MANAGE_PROJECT'))
}

function canChangeStatus(project: ProjectSummary) {
  return hasPermission('project:list:archive') && project.currentRole === 'OWNER'
}

async function loadProjects() {
  const requestId = ++requestSequence
  loading.value = true
  listError.value = ''
  forbidden.value = false
  try {
    const response = await listProjects({
      page: page.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined
    })
    if (requestId !== requestSequence) return
    records.value = response.data.data.records
    total.value = response.data.data.total
  } catch (cause) {
    if (requestId !== requestSequence) return
    forbidden.value = isAxiosError(cause) && cause.response?.status === 403
    listError.value = forbidden.value ? '' : apiErrorMessage(cause, '项目列表加载失败，请重试')
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

function applyFilters() {
  page.value = 1
  void loadProjects()
}

function resetFilters() {
  keyword.value = ''
  status.value = ''
  page.value = 1
  void loadProjects()
}

function changePage(nextPage: number) {
  page.value = nextPage
  void loadProjects()
}

function changePageSize(nextSize: number) {
  pageSize.value = nextSize
  page.value = 1
  void loadProjects()
}

function openCreate() {
  editingProject.value = null
  operationError.value = ''
  formOpen.value = true
}

function openEdit(project: ProjectSummary) {
  editingProject.value = project
  operationError.value = ''
  formOpen.value = true
}

function openMembers(project: ProjectSummary) {
  selectedProject.value = project
  memberDialogOpen.value = true
}

function replaceProject(project: ProjectSummary) {
  const index = records.value.findIndex(item => item.id === project.id)
  if (index >= 0) records.value[index] = project
  if (selectedProject.value?.id === project.id) selectedProject.value = project
}

async function refreshContext() {
  await projectContext.refresh().catch(() => undefined)
}

async function submitProject(command: CreateProjectCommand) {
  if (saving.value) return
  saving.value = true
  operationError.value = ''
  try {
    if (editingProject.value) {
      const next = (await updateProject(editingProject.value.id, {
        projectName: command.projectName,
        version: editingProject.value.version
      })).data.data
      replaceProject(next)
      ElMessage.success('项目资料已保存')
    } else {
      await createProject(command)
      page.value = 1
      ElMessage.success('项目已创建')
    }
    formOpen.value = false
    await Promise.all([loadProjects(), refreshContext()])
  } catch (cause) {
    operationError.value = apiErrorMessage(cause, '项目保存失败，请检查输入后重试')
  } finally {
    saving.value = false
  }
}

async function changeStatus(project: ProjectSummary) {
  const restoring = project.status === 'ARCHIVED'
  try {
    await ElMessageBox.confirm(
      restoring
        ? `恢复后，${project.projectName} 将按原成员关系重新开放写操作。`
        : `归档后，${project.projectName} 及其消费模块数据将进入只读状态。`,
      restoring ? '恢复项目' : '归档项目',
      {
        type: 'warning',
        confirmButtonText: restoring ? '恢复项目' : '归档项目',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  operationError.value = ''
  try {
    const response = restoring
      ? await restoreProject(project.id, project.version)
      : await archiveProject(project.id, project.version)
    replaceProject(response.data.data)
    await Promise.all([loadProjects(), refreshContext()])
    ElMessage.success(restoring ? '项目已恢复' : '项目已归档')
  } catch (cause) {
    operationError.value = apiErrorMessage(cause, `${restoring ? '恢复' : '归档'}失败，请刷新后重试`)
    await loadProjects()
  }
}

function handleMore(command: string, project: ProjectSummary) {
  if (command === 'status') void changeStatus(project)
}

function handleProjectUpdated(project: ProjectSummary) {
  replaceProject(project)
  void refreshContext()
}

onMounted(() => { void loadProjects() })
</script>

<template>
  <section class="project-management-page">
    <UiPageHeader title="项目管理" description="维护正式项目主数据、成员范围与归档状态。">
      <template #actions>
        <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新建项目</el-button>
      </template>
    </UiPageHeader>

    <UiToolbar>
      <el-input v-model="keyword" clearable placeholder="项目编号或名称" class="project-search-input" @keyup.enter="applyFilters">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="status" clearable placeholder="项目状态" class="project-status-filter" @change="applyFilters">
        <el-option label="进行中" value="ACTIVE" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <el-button @click="resetFilters">重置</el-button>
      <template #actions>
        <el-tooltip content="刷新项目列表"><el-button circle :icon="Refresh" :loading="loading" aria-label="刷新项目列表" @click="loadProjects" /></el-tooltip>
      </template>
    </UiToolbar>

    <el-alert v-if="operationError" :title="operationError" type="error" :closable="true" show-icon class="project-page-alert" @close="operationError = ''" />
    <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon class="project-page-alert">
      <template #default><el-button link type="primary" @click="loadProjects">重新加载</el-button></template>
    </el-alert>

    <section v-if="forbidden" class="project-state-panel">
      <el-result icon="warning" title="暂无项目查看权限" sub-title="需要 project:list 平台权限后才能进入项目管理。" />
    </section>

    <template v-else>
      <UiDataTable v-if="loading || records.length" :data="records" :loading="loading" row-key="id" border class="project-table">
        <el-table-column label="项目" min-width="250">
          <template #default="scope"><div class="project-name-cell"><strong>{{ scope.row.projectName }}</strong><span>{{ scope.row.projectCode }}</span></div></template>
        </el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="statusLabels" /></template></el-table-column>
        <el-table-column label="负责人" min-width="130" prop="ownerDisplayName" />
        <el-table-column label="我的角色" min-width="126"><template #default="scope">{{ roleLabels[scope.row.currentRole as ProjectRole] }}</template></el-table-column>
        <el-table-column label="版本" width="84"><template #default="scope">v{{ scope.row.version }}</template></el-table-column>
        <el-table-column label="操作" width="238" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :icon="User" @click="openMembers(scope.row)">成员</el-button>
            <el-button v-if="canEdit(scope.row)" link type="primary" :icon="Edit" @click="openEdit(scope.row)">编辑</el-button>
            <el-dropdown v-if="canChangeStatus(scope.row)" @command="(command: string) => handleMore(command, scope.row)">
              <el-button link :type="scope.row.status === 'ARCHIVED' ? 'success' : 'warning'"><el-icon><MoreFilled /></el-icon>{{ scope.row.status === 'ARCHIVED' ? '恢复' : '归档' }}</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="status" :icon="Lock">{{ scope.row.status === 'ARCHIVED' ? '恢复项目' : '归档项目' }}</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #footer>
          <div class="project-table-footer"><span>服务端共 {{ total }} 个项目</span><UiPagination :total="total" :page="page" :page-size="pageSize" @update:page="changePage" @update:page-size="changePageSize" /></div>
        </template>
      </UiDataTable>

      <div v-if="loading && !records.length" class="project-mobile-loading" role="status" aria-label="项目列表加载中">
        <article v-for="index in 3" :key="index"><el-skeleton :rows="3" animated /></article>
      </div>

      <div v-if="records.length" class="project-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="project in records" :key="project.id">
          <header><div><strong>{{ project.projectName }}</strong><small>{{ project.projectCode }}</small></div><UiStatusTag :value="project.status" :labels="statusLabels" /></header>
          <dl><div><dt>负责人</dt><dd>{{ project.ownerDisplayName }}</dd></div><div><dt>我的角色</dt><dd>{{ roleLabels[project.currentRole] }}</dd></div><div><dt>版本</dt><dd>v{{ project.version }}</dd></div><div><dt>可用动作</dt><dd>{{ project.allowedActions.length }}</dd></div></dl>
          <footer>
            <el-button type="primary" plain :icon="User" @click="openMembers(project)">成员</el-button>
            <el-button v-if="canEdit(project)" :icon="Edit" @click="openEdit(project)">编辑</el-button>
            <el-dropdown v-if="canChangeStatus(project)" @command="(command: string) => handleMore(command, project)">
              <el-button :type="project.status === 'ARCHIVED' ? 'success' : 'warning'" plain><el-icon><MoreFilled /></el-icon>{{ project.status === 'ARCHIVED' ? '恢复' : '更多' }}</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="status" :icon="Lock">{{ project.status === 'ARCHIVED' ? '恢复项目' : '归档项目' }}</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </footer>
        </article>
      </div>

      <UiEmptyState
        v-if="!loading && !records.length && !listError"
        :title="keyword || status ? '没有匹配的项目' : '暂无可访问项目'"
        :description="keyword || status ? '调整筛选条件后重新查询。' : '创建项目后，负责人和项目成员可在这里维护项目范围。'"
      >
        <template #action><el-button v-if="keyword || status" @click="resetFilters">清空筛选</el-button><el-button v-else-if="canCreate" type="primary" @click="openCreate">新建项目</el-button></template>
      </UiEmptyState>
    </template>

    <ProjectFormDrawer v-model="formOpen" :project="editingProject" :saving="saving" :error="operationError" @submit="submitProject" />
    <ProjectMemberDialog
      v-model="memberDialogOpen"
      :project="selectedProject"
      :can-manage="canManageMembers(selectedProject)"
      :can-transfer="canTransferOwner(selectedProject)"
      @updated="handleProjectUpdated"
    />
  </section>
</template>
