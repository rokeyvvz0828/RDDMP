<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import {
  addReleaseOperationGroupMember,
  createReleaseOperationGroup,
  deleteReleaseOperationGroup,
  deleteReleaseOperationGroupMember,
  listReleaseOperationGroups,
  listReleaseOperationMemberOptions,
  updateReleaseOperationGroup,
  type ReleaseGroupDto,
  type ReleaseGroupWrite,
  type ReleaseMemberOptionDto
} from '../../../api/release'
import { useAuthStore } from '../../../stores/auth'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'

const props = defineProps<{ projectId: number }>()
const auth = useAuthStore()
const groups = ref<ReleaseGroupDto[]>([])
const members = ref<ReleaseMemberOptionDto[]>([])
const selectedGroupId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const memberSaving = ref(false)
const error = ref('')
const forbidden = ref(false)
const dialogOpen = ref(false)
const editingGroup = ref<ReleaseGroupDto | null>(null)
const memberId = ref<number>()
const groupForm = reactive<ReleaseGroupWrite>({ groupName: '', description: '', rowVersion: 0 })
const canManage = computed(() => auth.hasPermission('release-operations:organization:manage'))
const selectedGroup = computed(() => groups.value.find(group => group.id === selectedGroupId.value) || groups.value[0] || null)
const availableMembers = computed(() => members.value.filter(member => !selectedGroup.value?.members.some(item => item.projectMemberId === member.id)))

function projectId() { return props.projectId }
function isForbidden(cause: unknown) { return (cause as { response?: { status?: number } }).response?.status === 403 }

async function load() {
  if (!projectId()) return
  loading.value = true
  error.value = ''
  forbidden.value = false
  try {
    const response = await Promise.all([listReleaseOperationGroups(projectId()), listReleaseOperationMemberOptions(projectId())])
    groups.value = response[0].data.data
    members.value = response[1].data.data
    if (!selectedGroupId.value || !groups.value.some(group => group.id === selectedGroupId.value)) selectedGroupId.value = groups.value[0]?.id
  } catch (cause) {
    groups.value = []
    members.value = []
    selectedGroupId.value = undefined
    error.value = apiErrorMessage(cause, '投产组织加载失败，请稍后重试')
    forbidden.value = isForbidden(cause)
  } finally { loading.value = false }
}

function openGroup(group?: ReleaseGroupDto) {
  editingGroup.value = group || null
  Object.assign(groupForm, { groupName: group?.groupName || '', description: group?.description || '', rowVersion: group?.rowVersion || 0 })
  dialogOpen.value = true
}

async function saveGroup() {
  if (!groupForm.groupName.trim() || saving.value) return
  saving.value = true
  try {
    const response = editingGroup.value
      ? await updateReleaseOperationGroup(projectId(), editingGroup.value.id, { ...groupForm })
      : await createReleaseOperationGroup(projectId(), { ...groupForm })
    const next = groups.value.filter(group => group.id !== response.data.data.id)
    next.push(response.data.data)
    groups.value = next.sort((a, b) => a.groupName.localeCompare(b.groupName, 'zh-CN'))
    selectedGroupId.value = response.data.data.id
    dialogOpen.value = false
    ElMessage.success(editingGroup.value ? '投产组已更新' : '投产组已创建')
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '投产组保存失败，请重试'))
  } finally { saving.value = false }
}

async function removeGroup(group: ReleaseGroupDto) {
  try { await ElMessageBox.confirm(`将删除“${group.groupName}”及其组内成员关系，删除后不可恢复。`, '删除投产组', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  try { await deleteReleaseOperationGroup(projectId(), group.id, group.rowVersion); ElMessage.success('投产组已删除'); await load() }
  catch (cause) { ElMessage.error(apiErrorMessage(cause, '投产组删除失败，请刷新后重试')) }
}

async function addMember() {
  if (!selectedGroup.value || memberId.value == null || memberSaving.value) return
  memberSaving.value = true
  try {
    const response = await addReleaseOperationGroupMember(projectId(), selectedGroup.value.id, memberId.value)
    const next = groups.value.map(group => group.id === selectedGroup.value?.id ? { ...group, members: [...group.members, response.data.data] } : group)
    groups.value = next
    memberId.value = undefined
    ElMessage.success('成员已加入投产组')
  } catch (cause) { ElMessage.error(apiErrorMessage(cause, '成员加入失败，请重试'))
  } finally { memberSaving.value = false }
}

async function removeMember(member: ReleaseGroupDto['members'][number]) {
  if (!selectedGroup.value) return
  try { await ElMessageBox.confirm(`将移除成员“${member.memberName}”，是否继续？`, '移除投产组成员', { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' }) } catch { return }
  try { await deleteReleaseOperationGroupMember(projectId(), selectedGroup.value.id, member.projectMemberId); groups.value = groups.value.map(group => group.id === selectedGroup.value?.id ? { ...group, members: group.members.filter(item => item.projectMemberId !== member.projectMemberId) } : group); ElMessage.success('成员已移出投产组') }
  catch (cause) { ElMessage.error(apiErrorMessage(cause, '成员移除失败，请重试')) }
}

onMounted(load)
</script>

<template>
  <div class="release-operations-layout">
    <div v-if="forbidden" class="release-operations-state"><el-result icon="warning" title="无权查看投产组织" sub-title="请联系项目管理员申请查看权限。" /></div>
    <div v-else-if="error" class="release-operations-state release-operations-state--error"><el-result icon="error" title="投产组织加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></div>
    <section v-else v-loading="loading" class="release-operations-panel">
      <header class="release-operations-panel__header"><div><span class="release-operations-kicker">RELEASE TEAM</span><h2>投产组织</h2><p>维护无层级投产组，并从当前项目有效成员中组成执行团队。</p></div><el-button v-if="canManage" type="primary" :icon="Plus" @click="openGroup()">新增投产组</el-button></header>
      <div class="release-operations-panel__body">
        <UiEmptyState v-if="!groups.length && !loading" title="暂无投产组" description="创建投产组后，可从当前项目成员中添加执行人员。"><template #action><el-button v-if="canManage" type="primary" @click="openGroup()">新增投产组</el-button></template></UiEmptyState>
        <div v-else class="release-operations-group-layout">
          <aside><div class="release-operations-group-list"><button v-for="group in groups" :key="group.id" type="button" :class="{ 'is-active': group.id === selectedGroup?.id }" @click="selectedGroupId = group.id"><strong>{{ group.groupName }}</strong><small>{{ group.members.length }} 名成员</small></button></div></aside>
          <section class="release-operations-panel"><header class="release-operations-panel__header"><div><span class="release-operations-kicker">SELECTED GROUP</span><h3>{{ selectedGroup?.groupName }}</h3><p>{{ selectedGroup?.description || '暂无组说明' }}</p></div><div v-if="canManage"><el-button link :icon="Edit" aria-label="编辑投产组" @click="openGroup(selectedGroup || undefined)" /><el-button link type="danger" :icon="Delete" aria-label="删除投产组" @click="selectedGroup && removeGroup(selectedGroup)" /></div></header><div class="release-operations-panel__body"><div v-if="canManage" style="display:flex;gap:8px;align-items:flex-end;flex-wrap:wrap"><el-form label-position="top"><el-form-item label="加入当前项目成员"><el-select v-model="memberId" clearable placeholder="选择项目成员" style="width:280px"><el-option v-for="member in availableMembers" :key="member.id" :value="member.id" :label="`${member.displayName}（${member.username}）`" /></el-select></el-form-item></el-form><el-button type="primary" :icon="Plus" :loading="memberSaving" :disabled="memberId == null" @click="addMember">加入投产组</el-button></div><UiEmptyState v-if="!selectedGroup?.members.length" title="暂无组内成员" description="选择当前项目成员后加入投产组。" /><div v-else class="release-operations-member-list"><article v-for="member in selectedGroup.members" :key="member.id" class="release-operations-member"><div class="release-operations-member__identity"><strong>{{ member.memberName }}</strong><small>项目成员 · 用户 ID {{ member.userId }}</small></div><el-button v-if="canManage" link type="danger" :icon="Delete" aria-label="移除投产组成员" @click="removeMember(member)" /></article></div></div></section>
        </div>
      </div>
    </section>
  </div>

  <el-dialog v-model="dialogOpen" :title="editingGroup ? '编辑投产组' : '新增投产组'" width="560px" destroy-on-close>
    <el-form label-position="top" class="release-operations-dialog-form"><el-form-item label="投产组名称" required><el-input v-model="groupForm.groupName" maxlength="128" /></el-form-item><el-form-item label="组说明"><el-input v-model="groupForm.description" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item></el-form>
    <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!groupForm.groupName.trim()" @click="saveGroup">保存</el-button></template>
  </el-dialog>
</template>
