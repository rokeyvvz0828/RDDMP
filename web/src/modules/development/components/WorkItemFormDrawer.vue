<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import { apiErrorMessage } from '../../../api/error'
import { developmentApi } from '../api'
import { useDevelopmentPermission, useDraftGuard } from '../composables/useDevelopmentTasks'
import type { UserRef, WorkItem, WorkItemInput } from '../types'

const props = defineProps<{ modelValue: boolean; projectRef: string; taskId: string; item?: WorkItem | null; defaultOwner?: UserRef | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [item: WorkItem] }>()

const formRef = ref<FormInstance>()
const saving = ref(false), baseline = ref(''), openedProjectRef = ref(''), submitError = ref(''), isConflict = ref(false)
const usersLoading = ref(false), usersError = ref(''), userOptions = ref<UserRef[]>([])
const editVersion = ref(0), currentActions = ref<string[]>([])
let usersTicket = 0
let editorEpoch = 0
let closeApproved = false

const form = reactive({
  title: '', description: '', assigneeId: null as string | null,
  plannedStart: null as string | null, plannedEnd: null as string | null,
  actualStart: null as string | null, actualEnd: null as string | null,
  blocked: false, blockReason: ''
})

const dirty = computed(() => props.modelValue && JSON.stringify(form) !== baseline.value)
const { confirmDiscard } = useDraftGuard(dirty, saving)
const hasPermission = useDevelopmentPermission()
const canSubmit = computed(() => hasPermission('development:work-item:update') && (!props.item || currentActions.value.includes('UPDATE')))
const canEditPlan = computed(() => canSubmit.value && (!props.item || currentActions.value.includes('EDIT_PLAN')))
const drawerTitle = computed(() => props.item ? (canSubmit.value ? '编辑工作项' : '工作项详情') : '新建工作项')

function captureEditorSession() {
  const epoch = editorEpoch, targetId = props.item?.id || null, taskId = props.taskId, projectRef = openedProjectRef.value
  const currentProjectRef = props.projectRef
  return {
    targetId, taskId, projectRef,
    isCurrent: () => epoch === editorEpoch && props.modelValue && targetId === (props.item?.id || null)
      && taskId === props.taskId && projectRef === openedProjectRef.value && currentProjectRef === props.projectRef
  }
}

const rules: FormRules = {
  title: [
    { required: true, message: '请输入工作项标题', trigger: 'blur' },
    { min: 1, max: 200, message: '标题长度应在 1-200 个字符之间', trigger: 'blur' }
  ],
  description: [{ max: 8000, message: '内容不能超过 8000 个字符', trigger: 'blur' }],
  blockReason: [{
    validator: (_r, val, cb) => {
      if (form.blocked && (!val || !val.trim())) cb(new Error('工作项阻塞时必须填写阻塞原因'))
      else if (val && val.length > 1000) cb(new Error('阻塞原因不能超过 1000 个字符'))
      else cb()
    },
    trigger: ['blur', 'change']
  }],
  plannedEnd: [{
    validator: (_r, val, cb) => {
      if (val && form.plannedStart && val < form.plannedStart) cb(new Error('计划完成日期不能早于开始日期'))
      else cb()
    },
    trigger: 'change'
  }],
  actualEnd: [{
    validator: (_r, val, cb) => {
      if (val && form.actualStart && val < form.actualStart) cb(new Error('实际完成日期不能早于开始日期'))
      else cb()
    },
    trigger: 'change'
  }]
}

async function loadUsers(keyword = '') {
  const ticket = ++usersTicket, scope = openedProjectRef.value
  usersLoading.value = true; usersError.value = ''
  try {
    const res = await developmentApi.users({ projectRef: scope, keyword, page: 1, size: 30 })
    if (ticket !== usersTicket || scope !== openedProjectRef.value) return
    userOptions.value = res.records
    const current = props.item?.assignee || props.defaultOwner
    if (current && !userOptions.value.some(u => u.id === current.id)) userOptions.value.unshift(current)
  } catch (cause) { if (ticket === usersTicket) usersError.value = apiErrorMessage(cause, '人员列表加载失败') }
  finally { if (ticket === usersTicket) usersLoading.value = false }
}

function onBlockedChange() {
  if (!form.blocked) form.blockReason = ''
  formRef.value?.validateField('blockReason')
}

watch(() => props.modelValue, open => {
  editorEpoch++
  closeApproved = false
  usersTicket++
  if (!open) return
  saving.value = false
  openedProjectRef.value = props.projectRef; submitError.value = ''; isConflict.value = false
  formRef.value?.clearValidate()
  if (props.item) {
    editVersion.value = props.item.rowVersion; currentActions.value = [...props.item.allowedActions]
    form.title = props.item.title; form.description = props.item.description || ''; form.assigneeId = props.item.assignee?.id || null
    form.plannedStart = props.item.plannedStart || null; form.plannedEnd = props.item.plannedEnd || null
    form.actualStart = props.item.actualStart || null; form.actualEnd = props.item.actualEnd || null
    form.blocked = Boolean(props.item.blocked); form.blockReason = props.item.blockReason || ''
    if (props.item.assignee) userOptions.value = [props.item.assignee]
  } else {
    form.title = ''; form.description = ''; form.assigneeId = props.defaultOwner?.id || null
    form.plannedStart = null; form.plannedEnd = null; form.actualStart = null; form.actualEnd = null
    form.blocked = false; form.blockReason = ''
    if (props.defaultOwner) userOptions.value = [props.defaultOwner]
  }
  baseline.value = JSON.stringify(form)
  void loadUsers()
}, { immediate: true })

async function reloadLatestItem() {
  const session = captureEditorSession()
  if (!session.targetId || !session.isCurrent() || session.projectRef !== props.projectRef || saving.value) return
  if (!(await confirmDiscard()) || !session.isCurrent() || saving.value) return
  saving.value = true
  try {
    const latest = await developmentApi.workItem(session.targetId)
    if (!session.isCurrent()) return
    editVersion.value = latest.rowVersion; currentActions.value = [...latest.allowedActions]
    form.title = latest.title; form.description = latest.description || ''; form.assigneeId = latest.assignee?.id || null
    form.plannedStart = latest.plannedStart || null; form.plannedEnd = latest.plannedEnd || null
    form.actualStart = latest.actualStart || null; form.actualEnd = latest.actualEnd || null
    form.blocked = Boolean(latest.blocked); form.blockReason = latest.blockReason || ''
    if (latest.assignee) userOptions.value = [latest.assignee]
    baseline.value = JSON.stringify(form); isConflict.value = false; submitError.value = ''
  } catch (cause) { if (session.isCurrent()) submitError.value = apiErrorMessage(cause, '读取最新工作项失败') }
  finally { if (session.isCurrent()) saving.value = false }
}

async function handleDrawerClose(open: boolean) {
  if (!open) {
    const session = captureEditorSession()
    if (!session.isCurrent()) return
    if (closeApproved) { closeApproved = false; emit('update:modelValue', false) }
    else if (await confirmDiscard() && session.isCurrent() && !saving.value) emit('update:modelValue', false)
  }
  else emit('update:modelValue', true)
}

function beforeClose(done: () => void) {
  const session = captureEditorSession()
  if (!session.isCurrent()) return
  void confirmDiscard().then(allowed => { if (allowed && session.isCurrent() && !saving.value) { closeApproved = true; done() } })
}

async function submit() {
  if (!props.modelValue || saving.value || !canSubmit.value) return
  if (props.projectRef !== openedProjectRef.value) { submitError.value = '项目上下文已变更，已拒绝保存以防写入错误项目'; return }
  const session = captureEditorSession()
  saving.value = true; submitError.value = ''; isConflict.value = false
  try {
    if (!(await formRef.value?.validate().catch(() => false)) || !session.isCurrent() || !canSubmit.value) return
    const input: WorkItemInput = {
      taskId: session.taskId, title: form.title.trim(), description: form.description || '', assigneeId: form.assigneeId || null,
      plannedStart: form.plannedStart || null, plannedEnd: form.plannedEnd || null,
      actualStart: form.actualStart || null, actualEnd: form.actualEnd || null,
      blocked: form.blocked, blockReason: form.blocked ? (form.blockReason || '').trim() : '',
      ...(session.targetId ? { rowVersion: editVersion.value } : {})
    }
    const result = session.targetId ? await developmentApi.updateWorkItem(session.targetId, input) : await developmentApi.createWorkItem(input)
    if (!session.isCurrent()) return
    baseline.value = JSON.stringify(form); saving.value = false; emit('update:modelValue', false); emit('saved', result)
  } catch (err) {
    if (!session.isCurrent()) return
    isConflict.value = Boolean(session.targetId && (err as { response?: { status?: number } })?.response?.status === 409)
    submitError.value = apiErrorMessage(err, '保存失败，请检查后重试')
  } finally { if (session.isCurrent()) saving.value = false }
}
onBeforeUnmount(() => { editorEpoch++; usersTicket++ })
</script>

<template>
  <UiFormDrawer :model-value="modelValue" :title="drawerTitle" :class="{ 'dev-editor-readonly': !canSubmit }" width="min(680px, 94vw)" :loading="saving" :before-close="beforeClose" @update:model-value="handleDrawerClose" @submit="submit">
    <el-alert v-if="submitError" :title="submitError" :type="isConflict ? 'warning' : 'error'" show-icon :closable="false" class="dev-wide">
      <template v-if="isConflict" #default>
        <el-button type="warning" link :icon="Refresh" :disabled="saving" @click="reloadLatestItem">读取最新内容</el-button>
      </template>
    </el-alert>
    <el-form ref="formRef" :model="form" :rules="rules" :disabled="!canSubmit || saving" label-position="top" class="dev-form">
      <div class="dev-form-section">
        <div class="dev-form-grid">
          <el-form-item label="工作项标题" prop="title" class="dev-wide">
            <el-input v-model="form.title" :disabled="!canEditPlan" maxlength="200" show-word-limit placeholder="请输入工作项标题" />
          </el-form-item>
          <el-form-item label="指派人员">
            <el-select v-model="form.assigneeId" clearable filterable remote :remote-method="loadUsers" :loading="usersLoading" :disabled="!canEditPlan" placeholder="选择指派人员">
              <el-option v-for="item in userOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <div v-if="usersError" class="dev-inline-error">{{ usersError }} <el-button link type="primary" :icon="Refresh" @click="() => loadUsers()">重试</el-button></div>
          </el-form-item>
          <el-form-item label="工作项描述" prop="description" class="dev-wide">
            <el-input v-model="form.description" :disabled="!canEditPlan" type="textarea" :rows="4" maxlength="8000" show-word-limit placeholder="请输入工作项描述" />
          </el-form-item>
        </div>
      </div>
      <div class="dev-form-section">
        <div class="dev-form-grid">
          <el-form-item label="计划开始">
            <el-date-picker v-model="form.plannedStart" :disabled="!canEditPlan" type="date" value-format="YYYY-MM-DD" placeholder="计划开始日期" @change="() => formRef?.validateField('plannedEnd')" />
          </el-form-item>
          <el-form-item label="计划完成" prop="plannedEnd">
            <el-date-picker v-model="form.plannedEnd" :disabled="!canEditPlan" type="date" value-format="YYYY-MM-DD" placeholder="计划完成日期" />
          </el-form-item>
          <el-form-item label="实际开始">
            <el-date-picker v-model="form.actualStart" type="date" value-format="YYYY-MM-DD" placeholder="实际开始日期" @change="() => formRef?.validateField('actualEnd')" />
          </el-form-item>
          <el-form-item label="实际完成" prop="actualEnd">
            <el-date-picker v-model="form.actualEnd" type="date" value-format="YYYY-MM-DD" placeholder="实际完成日期" />
          </el-form-item>
          <el-form-item label="是否阻塞" class="dev-wide">
            <el-switch v-model="form.blocked" active-text="阻塞中" inactive-text="正常" @change="onBlockedChange" />
          </el-form-item>
          <el-form-item v-if="form.blocked" label="阻塞原因" prop="blockReason" class="dev-wide">
            <el-input v-model="form.blockReason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="请详细说明阻塞原因" />
          </el-form-item>
        </div>
      </div>
    </el-form>
  </UiFormDrawer>
</template>
