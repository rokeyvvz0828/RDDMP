<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Delete, Refresh, Switch } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import {
  addProjectMember,
  changeProjectMemberRole,
  listProjectMemberCandidates,
  listProjectMembers,
  removeProjectMember,
  transferProjectOwner
} from '../../../api/projects'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiUserIdentity from '../../../components/ui/UiUserIdentity.vue'
import type {
  ProjectMemberCandidate,
  ProjectMembership,
  ProjectRole,
  ProjectSummary
} from '../../../types/project-context'

const props = defineProps<{
  modelValue: boolean
  project: ProjectSummary | null
  canManage: boolean
  canTransfer: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  updated: [project: ProjectSummary]
}>()

const members = ref<ProjectMembership[]>([])
const candidates = ref<ProjectMemberCandidate[]>([])
const loading = ref(false)
const candidateLoading = ref(false)
const savingAction = ref('')
const error = ref('')
const addUserId = ref<number | null>(null)
const addRole = ref<Exclude<ProjectRole, 'OWNER'>>('MEMBER')
const version = ref(0)
const transferOpen = ref(false)
const nextOwnerUserId = ref<number | null>(null)
const assignableRoles: Array<{ value: Exclude<ProjectRole, 'OWNER'>; label: string }> = [
  { value: 'ADMIN', label: '项目管理员' },
  { value: 'MEMBER', label: '项目成员' },
  { value: 'VIEWER', label: '只读成员' }
]
const roleLabels: Record<ProjectRole, string> = {
  OWNER: '负责人',
  ADMIN: '项目管理员',
  MEMBER: '项目成员',
  VIEWER: '只读成员'
}
const transferCandidates = computed(() => members.value.filter(member => member.role !== 'OWNER'))

watch(() => props.modelValue, open => {
  if (!open || !props.project) return
  version.value = props.project.version
  addUserId.value = null
  addRole.value = 'MEMBER'
  error.value = ''
  void loadMembers()
  if (props.canManage) void searchCandidates('')
})

watch(() => props.project?.version, nextVersion => {
  if (typeof nextVersion === 'number') version.value = nextVersion
})

async function loadMembers() {
  if (!props.project || loading.value) return
  loading.value = true
  error.value = ''
  try {
    members.value = (await listProjectMembers(props.project.id)).data.data
  } catch (cause) {
    error.value = apiErrorMessage(cause, '项目成员加载失败，请重试')
  } finally {
    loading.value = false
  }
}

async function searchCandidates(keyword: string) {
  if (!props.canManage) return
  candidateLoading.value = true
  try {
    candidates.value = (await listProjectMemberCandidates({ page: 1, size: 50, keyword: keyword.trim() || undefined })).data.data.records
  } catch (cause) {
    error.value = apiErrorMessage(cause, '成员候选加载失败，请重试')
  } finally {
    candidateLoading.value = false
  }
}

function applyProject(nextProject: ProjectSummary) {
  version.value = nextProject.version
  emit('updated', nextProject)
}

async function addMember() {
  if (!props.project || !addUserId.value || savingAction.value) return
  savingAction.value = 'add'
  error.value = ''
  try {
    const nextProject = (await addProjectMember(props.project.id, {
      userId: addUserId.value,
      role: addRole.value,
      version: version.value
    })).data.data
    applyProject(nextProject)
    addUserId.value = null
    await loadMembers()
    ElMessage.success('项目成员已添加')
  } catch (cause) {
    error.value = apiErrorMessage(cause, '成员添加失败，请刷新后重试')
  } finally {
    savingAction.value = ''
  }
}

async function changeRole(member: ProjectMembership, role: Exclude<ProjectRole, 'OWNER'>) {
  if (!props.project || savingAction.value || member.role === role) return
  savingAction.value = `role-${member.userId}`
  error.value = ''
  try {
    const nextProject = (await changeProjectMemberRole(props.project.id, member.userId, { role, version: version.value })).data.data
    applyProject(nextProject)
    await loadMembers()
    ElMessage.success(`${member.displayName} 的项目角色已更新`)
  } catch (cause) {
    error.value = apiErrorMessage(cause, '角色更新失败，请刷新后重试')
  } finally {
    savingAction.value = ''
  }
}

async function removeMember(member: ProjectMembership) {
  if (!props.project || savingAction.value) return
  try {
    await ElMessageBox.confirm(
      `移除后，${member.displayName} 将无法继续访问该项目。`,
      '移除项目成员',
      { type: 'warning', confirmButtonText: '移除成员', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  savingAction.value = `remove-${member.userId}`
  error.value = ''
  try {
    const nextProject = (await removeProjectMember(props.project.id, member.userId, version.value)).data.data
    applyProject(nextProject)
    await loadMembers()
    ElMessage.success('项目成员已移除')
  } catch (cause) {
    error.value = apiErrorMessage(cause, '成员移除失败，请刷新后重试')
  } finally {
    savingAction.value = ''
  }
}

function openTransfer() {
  nextOwnerUserId.value = null
  transferOpen.value = true
}

async function transferOwner() {
  if (!props.project || !nextOwnerUserId.value || savingAction.value) return
  const nextOwner = members.value.find(member => member.userId === nextOwnerUserId.value)
  if (!nextOwner) return
  try {
    await ElMessageBox.confirm(
      `负责人将转移给 ${nextOwner.displayName}，您将变更为项目管理员。此操作会写入审计记录。`,
      '确认转移负责人',
      { type: 'warning', confirmButtonText: '确认转移', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  savingAction.value = 'transfer'
  error.value = ''
  try {
    const nextProject = (await transferProjectOwner(props.project.id, nextOwner.userId, version.value)).data.data
    applyProject(nextProject)
    transferOpen.value = false
    await loadMembers()
    ElMessage.success('项目负责人已转移')
  } catch (cause) {
    error.value = apiErrorMessage(cause, '负责人转移失败，请刷新后重试')
  } finally {
    savingAction.value = ''
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="project ? `${project.projectCode} · 项目成员` : '项目成员'"
    width="min(860px, calc(100vw - 24px))"
    :close-on-click-modal="false"
    class="project-member-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="project-member-dialog__alert" />
    <el-alert
      v-if="project?.status === 'ARCHIVED'"
      title="项目已归档，成员关系只读；恢复后原成员关系重新生效"
      type="warning"
      :closable="false"
      show-icon
      class="project-member-dialog__alert"
    />

    <section v-if="canManage" class="project-member-add">
      <el-select
        v-model="addUserId"
        filterable
        remote
        clearable
        :remote-method="searchCandidates"
        :loading="candidateLoading"
        placeholder="搜索启用用户"
        aria-label="待添加用户"
      >
        <el-option
          v-for="candidate in candidates"
          :key="candidate.id"
          :label="`${candidate.displayName} (${candidate.username})`"
          :value="candidate.id"
          :disabled="members.some(member => member.userId === candidate.id)"
        >
          <span>{{ candidate.displayName }}</span><small>{{ candidate.username }} · {{ candidate.orgName || '未分配组织' }}</small>
        </el-option>
      </el-select>
      <el-select v-model="addRole" aria-label="项目角色">
        <el-option v-for="role in assignableRoles" :key="role.value" :label="role.label" :value="role.value" />
      </el-select>
      <el-button type="primary" :loading="savingAction === 'add'" :disabled="!addUserId" @click="addMember">添加成员</el-button>
    </section>

    <div class="project-member-toolbar">
      <span>共 {{ members.length }} 名成员</span>
      <div>
        <el-button v-if="canTransfer" :icon="Switch" @click="openTransfer">转移负责人</el-button>
        <el-tooltip content="刷新成员"><el-button circle :icon="Refresh" :loading="loading" aria-label="刷新成员" @click="loadMembers" /></el-tooltip>
      </div>
    </div>

    <el-table v-loading="loading" :data="members" class="project-member-table" row-key="userId" border>
      <el-table-column label="成员" min-width="210">
        <template #default="scope">
          <UiUserIdentity :user="{ id: scope.row.userId, username: scope.row.username, displayName: scope.row.displayName }" :size="30" :show-profile="false" />
        </template>
      </el-table-column>
      <el-table-column prop="username" label="账号" min-width="140" />
      <el-table-column label="项目角色" min-width="170">
        <template #default="scope">
          <el-tag v-if="scope.row.role === 'OWNER'" type="success" effect="plain">负责人</el-tag>
          <el-select
            v-else-if="canManage"
            :model-value="scope.row.role"
            :loading="savingAction === `role-${scope.row.userId}`"
            :disabled="Boolean(savingAction)"
            aria-label="调整项目角色"
            @change="changeRole(scope.row, $event)"
          >
            <el-option v-for="role in assignableRoles" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
          <span v-else>{{ roleLabels[scope.row.role as ProjectRole] }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="92" align="center">
        <template #default="scope">
          <el-tooltip v-if="scope.row.role !== 'OWNER'" content="移除成员">
            <el-button
              circle
              plain
              type="danger"
              :icon="Delete"
              :loading="savingAction === `remove-${scope.row.userId}`"
              :disabled="Boolean(savingAction)"
              :aria-label="`移除${scope.row.displayName}`"
              @click="removeMember(scope.row)"
            />
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <div class="project-member-mobile-list">
      <article v-for="member in members" :key="member.userId">
        <header>
          <UiUserIdentity :user="{ id: member.userId, username: member.username, displayName: member.displayName }" :size="30" :show-profile="false" />
          <el-tag :type="member.role === 'OWNER' ? 'success' : 'info'" effect="plain">{{ roleLabels[member.role] }}</el-tag>
        </header>
        <small>{{ member.username }}</small>
        <footer v-if="canManage && member.role !== 'OWNER'">
          <el-select
            :model-value="member.role"
            :disabled="Boolean(savingAction)"
            aria-label="调整项目角色"
            @change="changeRole(member, $event)"
          >
            <el-option v-for="role in assignableRoles" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
          <el-button plain type="danger" :icon="Delete" :disabled="Boolean(savingAction)" @click="removeMember(member)">移除</el-button>
        </footer>
      </article>
    </div>

    <UiEmptyState v-if="!loading && !members.length" title="暂无项目成员" description="项目成员数据为空，请刷新后重试。" />
    <template #footer><el-button @click="emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>

  <el-dialog v-model="transferOpen" title="转移项目负责人" width="min(480px, calc(100vw - 24px))" append-to-body :close-on-click-modal="false">
    <el-alert title="新负责人必须是当前项目成员；转移后您将成为项目管理员。" type="warning" :closable="false" show-icon />
    <el-form label-position="top" class="project-owner-transfer-form">
      <el-form-item label="新负责人" required>
        <el-select v-model="nextOwnerUserId" filterable placeholder="选择现有项目成员">
          <el-option v-for="member in transferCandidates" :key="member.userId" :label="`${member.displayName} (${roleLabels[member.role]})`" :value="member.userId" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer><el-button @click="transferOpen = false">取消</el-button><el-button type="primary" :loading="savingAction === 'transfer'" :disabled="!nextOwnerUserId" @click="transferOwner">转移负责人</el-button></template>
  </el-dialog>
</template>
