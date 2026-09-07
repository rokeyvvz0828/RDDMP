<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import { apiErrorMessage } from '../../../api/error'
import { useProjectContextStore } from '../../../stores/project-context'
import { getSubsystemParticipation, getSubsystemParticipantCandidates, replaceSubsystemParticipation } from '../api'
import type { PhysicalSubsystem, SubsystemParticipation, SubsystemParticipantCandidate } from '../types'

const props = defineProps<{ modelValue: boolean; subsystem: PhysicalSubsystem | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()
const project = useProjectContextStore()
const data = ref<SubsystemParticipation | null>(null)
const candidates = ref<SubsystemParticipantCandidate[]>([])
const selected = ref<number[]>([])
const reason = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
let request = 0
const dirty = computed(() => !!data.value && (reason.value !== ''
  || JSON.stringify([...selected.value].sort()) !== JSON.stringify([...data.value.explicitParticipantUserIds].sort())))
const options = computed(() => {
  const items = new Map((data.value?.participants || []).map(person => [person.userId, person]))
  candidates.value.forEach(person => items.set(person.userId, person))
  return [...items.values()].filter(person => person.userId !== data.value?.ownerUserId)
})
const invalidIds = computed(() => data.value?.explicitParticipantUserIds.filter(id => !data.value?.effectiveParticipantUserIds.includes(id)) || [])

async function load() {
  if (!props.subsystem || !project.currentRef) return
  const id = props.subsystem.id
  const projectRef = project.currentRef
  const ticket = ++request
  loading.value = true
  error.value = ''
  data.value = null
  try {
    const result = await getSubsystemParticipation(id, projectRef)
    const people = result.canManage ? await getSubsystemParticipantCandidates(id, projectRef) : []
    if (ticket !== request || project.currentRef !== projectRef) return
    data.value = result
    candidates.value = people
    selected.value = [...result.explicitParticipantUserIds]
    reason.value = ''
  } catch (cause) {
    if (ticket === request) error.value = apiErrorMessage(cause, '参与人员加载失败，请重试或联系项目管理者确认权限。')
  } finally { if (ticket === request) loading.value = false }
}
async function discardAllowed() {
  if (saving.value) return false
  if (!dirty.value) return true
  return ElMessageBox.confirm('参与人员调整尚未保存，离开将丢弃本次修改。', '放弃修改？', {
    confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning'
  }).then(() => true).catch(() => false)
}
async function close(value: boolean) {
  if (!value && await discardAllowed()) emit('update:modelValue', false)
}
async function submit() {
  if (loading.value || saving.value) return
  if (!data.value?.canManage) { await close(false); return }
  if (!props.subsystem || !project.currentRef) return
  if (!reason.value.trim()) { error.value = '请填写变更原因。'; return }
  const id = props.subsystem.id
  const projectRef = project.currentRef
  const ticket = request
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await replaceSubsystemParticipation(id, projectRef, {
      participantUserIds: [...selected.value], rowVersion: data.value.rowVersion, reason: reason.value.trim()
    })
    if (ticket !== request || project.currentRef !== projectRef) return
    data.value = result
    selected.value = [...result.explicitParticipantUserIds]
    reason.value = ''
    success.value = '参与人员已保存。新增人员不会自动加入历史任务；退出人员须先完成责任移交。'
    emit('saved')
  } catch (cause) {
    if (ticket === request) error.value = apiErrorMessage(cause, '保存失败，已保留修改。版本冲突请重新加载；存在未完成责任请先移交。')
  } finally { saving.value = false }
}
watch(() => [props.modelValue, props.subsystem?.id, project.currentRef] as const, () => {
  ++request
  data.value = null
  candidates.value = []
  selected.value = []
  reason.value = ''
  error.value = ''
  success.value = ''
  if (props.modelValue) void load()
}, { immediate: true })
watch(() => project.currentRef, () => {
  if (props.modelValue) {
    emit('update:modelValue', false)
    ElMessage.info('项目已切换，请在当前项目重新选择系统。')
  }
})
onBeforeRouteLeave(() => props.modelValue ? discardAllowed() : true)
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = '' }
}
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { ++request; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <UiFormDrawer :model-value="modelValue" :title="`${subsystem?.name || '系统'} · 参与人员`"
    width="min(640px, calc(100vw - 24px))" :loading="saving"
    :confirm-text="data?.canManage ? '保存参与人员' : '关闭'" @update:model-value="close" @submit="submit">
    <section v-loading="loading" aria-label="系统参与人员">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-button v-if="error" :disabled="saving" @click="async () => { if (await discardAllowed()) await load() }">重新加载</el-button>
      <el-alert v-if="success" :title="success" type="success" show-icon :closable="false" />
      <template v-if="data">
        <el-alert title="系统负责人默认参与，不可从成员名单移除；负责人调整仍需通过系统变更工单。" type="info" :closable="false" />
        <el-alert v-if="invalidIds.length" title="存在失效成员，已停止提供参与资格。请移交未完成责任后调整名单。" type="warning" :closable="false" />
        <el-form label-position="top" :disabled="saving">
          <el-form-item label="系统负责人"><span>{{ data.participants.find(item => item.userId === data?.ownerUserId)?.displayName || '未配置负责人' }}</span></el-form-item>
          <template v-if="data.canManage">
            <el-form-item label="其他参与人员">
              <el-select v-model="selected" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择有效项目成员" style="width: 100%">
                <el-option v-for="person in options" :key="person.userId" :value="person.userId"
                  :label="`${person.displayName}${invalidIds.includes(person.userId) ? '（已失效）' : ''}`"
                  :disabled="!candidates.some(candidate => candidate.userId === person.userId)" />
              </el-select>
            </el-form-item>
            <el-form-item label="变更原因" required><el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明新增或退出原因；退出前需先移交未完成责任" /></el-form-item>
          </template>
          <template v-else>
            <el-alert title="只读：维护人员需系统负责人或授权管理者身份，并具有维护权限。" type="info" :closable="false" />
            <el-form-item label="其他参与人员"><span v-for="person in options" :key="person.userId">{{ person.displayName }}{{ invalidIds.includes(person.userId) ? '（已失效）' : '' }}；</span></el-form-item>
            <UiEmptyState v-if="!options.length" title="暂无其他参与人员" description="系统负责人默认参与。" />
          </template>
        </el-form>
      </template>
    </section>
  </UiFormDrawer>
</template>
