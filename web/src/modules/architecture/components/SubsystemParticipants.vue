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
const owner = computed(() => data.value?.participants.find(person => person.userId === data.value?.ownerUserId))
const otherSelected = computed(() => selected.value.filter(id => id !== data.value?.ownerUserId))
let request = 0
let discardConfirmation: Promise<boolean> | null = null
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
  if (!discardConfirmation) {
    discardConfirmation = ElMessageBox.confirm('参与人员调整尚未保存，离开将丢弃本次修改。', '放弃修改？', {
      confirmButtonText: '放弃修改', cancelButtonText: '继续编辑', type: 'warning',
      closeOnClickModal: false, closeOnPressEscape: false
    }).then(() => true).catch(() => false).finally(() => { discardConfirmation = null })
  }
  return discardConfirmation
}
async function close(value: boolean) {
  const ticket = request
  if (!value && await discardAllowed() && ticket === request) emit('update:modelValue', false)
}
// 遮罩、右上角和 Esc 必须在内部关闭之前确认；确认后只更新受控值，
// 不调用内部 done，避免它再次触发 update:modelValue 并重复确认。
function beforeDrawerClose() {
  void close(false)
}
async function submit() {
  if (loading.value || saving.value) return
  if (!data.value?.canManage) { await close(false); return }
  if (!props.subsystem || !project.currentRef) return
  const id = props.subsystem.id
  const projectRef = project.currentRef
  const ticket = request
  saving.value = true
  error.value = ''
  try {
    const result = await replaceSubsystemParticipation(id, projectRef, {
      participantUserIds: [...selected.value], rowVersion: data.value.rowVersion, reason: reason.value.trim()
    })
    if (ticket !== request || project.currentRef !== projectRef) return
    data.value = result
    selected.value = [...result.explicitParticipantUserIds]
    reason.value = ''
    ElMessage.success('参与人员已保存')
    emit('update:modelValue', false)
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
    width="min(640px, calc(100vw - 24px))" :loading="saving" :before-close="beforeDrawerClose"
    :confirm-text="data?.canManage ? '保存参与人员' : '关闭'" @update:model-value="close" @submit="submit">
    <section v-loading="loading" class="subsystem-people" aria-label="系统参与人员">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-button v-if="error" :disabled="saving" @click="async () => { if (await discardAllowed()) await load() }">重新加载</el-button>
      <template v-if="data">
        <p class="subsystem-people__hint">维护该系统的参与资格；具体任务的负责人和参与人在搭建计划中调整。</p>
        <div class="subsystem-people__owner">
          <span class="subsystem-people__label">系统负责人</span>
          <div class="subsystem-people__identity">
            <el-avatar :size="36" aria-hidden="true">{{ owner?.displayName?.slice(0, 1) || '—' }}</el-avatar>
            <strong>{{ owner?.displayName || '未配置负责人' }}</strong>
            <el-tag v-if="data.ownerUserId" size="small" effect="plain">默认参与</el-tag>
          </div>
          <p class="subsystem-people__hint">负责人自动参与，不在下方重复添加；调整负责人请发起系统变更工单。</p>
        </div>
        <el-alert v-if="invalidIds.length" title="存在失效成员，请移交未完成责任后调整名单。" type="warning" show-icon :closable="false" />
        <el-form label-position="top" :disabled="saving">
          <template v-if="data.canManage">
            <el-form-item :label="`其他参与人员（${otherSelected.length}人）`">
              <el-select v-model="selected" class="subsystem-people__select" multiple filterable placeholder="搜索并选择项目成员" aria-label="其他参与人员">
                <el-option v-for="person in options" :key="person.userId" :value="person.userId"
                  :label="`${person.displayName}${invalidIds.includes(person.userId) ? '（已失效）' : ''}`"
                  :disabled="!candidates.some(candidate => candidate.userId === person.userId)" />
              </el-select>
              <p class="subsystem-people__hint">新增人员不会自动加入历史任务；退出前须先移交未完成责任。</p>
            </el-form-item>
            <el-form-item label="变更原因（选填）">
              <el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="可补充本次人员调整的说明" aria-label="变更原因（选填）" />
            </el-form-item>
          </template>
          <template v-else>
            <p class="subsystem-people__hint">当前为只读。系统负责人或具有维护权限的授权管理者可以调整名单。</p>
            <el-form-item :label="`其他参与人员（${otherSelected.length}人）`">
              <div v-if="otherSelected.length" class="subsystem-people__readonly">
                <el-tag v-for="person in options.filter(item => otherSelected.includes(item.userId))" :key="person.userId" effect="plain">
                  {{ person.displayName }}{{ invalidIds.includes(person.userId) ? '（已失效）' : '' }}
                </el-tag>
              </div>
              <UiEmptyState v-else title="暂无其他参与人员" description="系统负责人默认参与。" />
            </el-form-item>
          </template>
        </el-form>
      </template>
    </section>
  </UiFormDrawer>
</template>

<style scoped>
.subsystem-people { display: grid; min-width: 0; gap: 18px; }
.subsystem-people__hint { margin: 0; color: var(--muted); font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.subsystem-people__owner { display: grid; gap: 12px; padding: 16px; border: 1px solid var(--line); border-radius: 8px; background: var(--panel-bg); }
.subsystem-people__label { color: var(--muted); font-size: 12px; }
.subsystem-people__identity { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; min-width: 0; }
.subsystem-people__identity strong { color: var(--text); overflow-wrap: anywhere; }
.subsystem-people__identity .el-avatar { flex-shrink: 0; color: var(--brand); background: color-mix(in srgb, var(--brand) 12%, var(--panel-bg)); }
.subsystem-people__select { width: 100%; }
.subsystem-people__select :deep(.el-select__selection) { max-height: 200px; overflow-y: auto; }
.subsystem-people__select :deep(.el-tag) { max-width: 100%; height: auto; min-height: 24px; color: var(--text); background: color-mix(in srgb, var(--brand) 10%, var(--panel-bg)); border-color: var(--line); }
.subsystem-people__select :deep(.el-tag__content), .subsystem-people__select :deep(.el-select__tags-text) { white-space: normal; overflow-wrap: anywhere; }
.subsystem-people__select + .subsystem-people__hint { margin-top: 8px; }
.subsystem-people__readonly { display: flex; flex-wrap: wrap; gap: 8px; max-height: 200px; overflow-y: auto; }
.subsystem-people__readonly .el-tag { max-width: 100%; height: auto; white-space: normal; overflow-wrap: anywhere; }
</style>
