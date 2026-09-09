<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import { apiErrorMessage } from '../../../api/error'
import { developmentApi } from '../api'
import { useDevelopmentPermission, useDraftGuard } from '../composables/useDevelopmentTasks'
import type { CreateTaskInput, DevelopmentTask, PendingRequirement, SystemRef, UpdateTaskInput, UserRef } from '../types'

const props = defineProps<{ modelValue: boolean; projectRef: string; task?: DevelopmentTask | null; pending?: PendingRequirement | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [task: DevelopmentTask] }>()

const formRef = ref<FormInstance>()
const saving = ref(false), baseline = ref(''), openedProjectRef = ref(''), requestId = ref(''), submitError = ref(''), isConflict = ref(false)
const pendingLoading = ref(false), pendingError = ref(''), pendingOptions = ref<PendingRequirement[]>([]), selectedPendingKey = ref('')
const systemsLoading = ref(false), systemsError = ref(''), systemOptions = ref<SystemRef[]>([])
const usersLoading = ref(false), usersError = ref(''), userOptions = ref<UserRef[]>([])
const editVersion = ref(0), currentActions = ref<string[]>([])
let pendingTicket = 0, systemsTicket = 0, usersTicket = 0
let editorEpoch = 0
let closeApproved = false

const form = reactive({
  sourceMode: 'LINKED' as 'LINKED' | 'STANDALONE',
  sourceRequirementId: null as string | null, sourceRevision: null as string | null, systemId: '',
  title: '', description: '', ownerId: null as string | null,
  developmentPlanStart: null as string | null, developmentPlanEnd: null as string | null,
  testPlanStart: null as string | null, testPlanEnd: null as string | null
})

const dirty = computed(() => props.modelValue && JSON.stringify(form) !== baseline.value)
const { confirmDiscard } = useDraftGuard(dirty, saving)
const hasPermission = useDevelopmentPermission()
const canSubmit = computed(() => props.task ? currentActions.value.includes('UPDATE') && hasPermission('development:task:update') : hasPermission('development:task:create'))
const drawerTitle = computed(() => (props.task ? '编辑开发任务' : '新建开发任务'))

function captureEditorSession() {
  const epoch = editorEpoch, targetId = props.task?.id || null, projectRef = openedProjectRef.value
  const currentProjectRef = props.projectRef
  return {
    targetId, projectRef,
    isCurrent: () => epoch === editorEpoch && props.modelValue && targetId === (props.task?.id || null)
      && projectRef === openedProjectRef.value && currentProjectRef === props.projectRef
  }
}

const selectedSystemName = computed(() => {
  if (props.task) return props.task.system?.name || ''
  const p = pendingOptions.value.find(item => item.system.id === form.systemId)
  if (p) return p.system.name
  const s = systemOptions.value.find(item => item.id === form.systemId)
  return s ? s.name : ''
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入任务标题', trigger: 'blur' },
    { min: 1, max: 200, message: '标题长度应在 1-200 个字符之间', trigger: 'blur' }
  ],
  description: [{ max: 8000, message: '内容不能超过 8000 个字符', trigger: 'blur' }],
  systemId: [{ required: true, message: '请选择所属系统', trigger: 'change' }],
  developmentPlanEnd: [{
    validator: (_r, val, cb) => {
      if (val && form.developmentPlanStart && val < form.developmentPlanStart) cb(new Error('开发计划结束日期不能早于开始日期'))
      else cb()
    },
    trigger: 'change'
  }],
  testPlanEnd: [{
    validator: (_r, val, cb) => {
      if (val && form.testPlanStart && val < form.testPlanStart) cb(new Error('测试计划结束日期不能早于开始日期'))
      else cb()
    },
    trigger: 'change'
  }]
}

async function loadPending(keyword = '') {
  if (!openedProjectRef.value) return
  const ticket = ++pendingTicket, scope = openedProjectRef.value
  pendingLoading.value = true; pendingError.value = ''
  try {
    const res = await developmentApi.pending({ projectRef: scope, keyword, page: 1, size: 30 })
    if (ticket !== pendingTicket || scope !== openedProjectRef.value) return
    pendingOptions.value = res.records
    if (props.pending && !pendingOptions.value.some(p => `${p.sourceRequirementId}:${p.system.id}` === `${props.pending!.sourceRequirementId}:${props.pending!.system.id}`)) {
      pendingOptions.value.unshift(props.pending)
    }
  } catch (cause) { if (ticket === pendingTicket) pendingError.value = apiErrorMessage(cause, '待承接需求加载失败') }
  finally { if (ticket === pendingTicket) pendingLoading.value = false }
}

async function loadSystems(keyword = '') {
  const ticket = ++systemsTicket, scope = openedProjectRef.value
  systemsLoading.value = true; systemsError.value = ''
  try {
    const res = await developmentApi.systems({ projectRef: scope, keyword, page: 1, size: 30 })
    if (ticket !== systemsTicket || scope !== openedProjectRef.value) return
    systemOptions.value = res.records
    if (props.task?.system && !systemOptions.value.some(s => s.id === props.task!.system.id)) systemOptions.value.unshift(props.task.system)
  } catch (cause) { if (ticket === systemsTicket) systemsError.value = apiErrorMessage(cause, '系统列表加载失败') }
  finally { if (ticket === systemsTicket) systemsLoading.value = false }
}

async function loadUsers(keyword = '') {
  const ticket = ++usersTicket, scope = openedProjectRef.value
  usersLoading.value = true; usersError.value = ''
  try {
    const res = await developmentApi.users({ projectRef: scope, keyword, page: 1, size: 30 })
    if (ticket !== usersTicket || scope !== openedProjectRef.value) return
    userOptions.value = res.records
    if (props.task?.owner && !userOptions.value.some(u => u.id === props.task!.owner.id)) userOptions.value.unshift(props.task.owner)
  } catch (cause) { if (ticket === usersTicket) usersError.value = apiErrorMessage(cause, '人员列表加载失败') }
  finally { if (ticket === usersTicket) usersLoading.value = false }
}

function onSelectPending(key: string) {
  const previous = pendingOptions.value.find(item => `${item.sourceRequirementId}:${item.system.id}` === selectedPendingKey.value)
  selectedPendingKey.value = key
  const found = pendingOptions.value.find(p => `${p.sourceRequirementId}:${p.system.id}` === key)
  if (found) {
    form.sourceRequirementId = found.sourceRequirementId; form.sourceRevision = found.sourceRevision
    form.systemId = found.system.id
    if (!form.title.trim() || form.title === previous?.sourceName) form.title = found.sourceName
    if (!form.description.trim() || form.description === previous?.summary) form.description = found.summary || ''
  }
}

function onSourceModeChange() {
  if (form.sourceMode === 'STANDALONE') {
    form.sourceRequirementId = null; form.sourceRevision = null; form.systemId = ''; selectedPendingKey.value = ''
    void loadSystems()
  } else {
    form.systemId = ''; void loadPending()
  }
}

watch(() => props.modelValue, open => {
  editorEpoch++
  closeApproved = false
  pendingTicket++; systemsTicket++; usersTicket++
  if (!open) return
  saving.value = false
  openedProjectRef.value = props.projectRef; submitError.value = ''; isConflict.value = false
  formRef.value?.clearValidate()
  if (props.task) {
    editVersion.value = props.task.rowVersion; currentActions.value = [...props.task.allowedActions]
    form.sourceMode = props.task.sourceMode; form.sourceRequirementId = props.task.source?.id || null
    form.sourceRevision = props.task.source?.revision || null; form.systemId = props.task.system?.id || ''
    form.title = props.task.title; form.description = props.task.description || ''; form.ownerId = props.task.owner?.id || null
    form.developmentPlanStart = props.task.developmentPlanStart || null; form.developmentPlanEnd = props.task.developmentPlanEnd || null
    form.testPlanStart = props.task.testPlanStart || null; form.testPlanEnd = props.task.testPlanEnd || null
    if (props.task.system) systemOptions.value = [props.task.system]
    if (props.task.owner) userOptions.value = [props.task.owner]
  } else {
    requestId.value = crypto.randomUUID()
    if (props.pending) {
      form.sourceMode = 'LINKED'; form.sourceRequirementId = props.pending.sourceRequirementId; form.sourceRevision = props.pending.sourceRevision
      form.systemId = props.pending.system.id; form.title = props.pending.sourceName; form.description = props.pending.summary || ''
      selectedPendingKey.value = `${props.pending.sourceRequirementId}:${props.pending.system.id}`
      pendingOptions.value = [props.pending]; systemOptions.value = [props.pending.system]
    } else {
      form.sourceMode = 'LINKED'; form.sourceRequirementId = null; form.sourceRevision = null; form.systemId = ''; form.title = ''; form.description = ''; selectedPendingKey.value = ''
    }
    form.ownerId = null; form.developmentPlanStart = null; form.developmentPlanEnd = null; form.testPlanStart = null; form.testPlanEnd = null
    if (form.sourceMode === 'LINKED') void loadPending()
    else void loadSystems()
  }
  baseline.value = JSON.stringify(form)
  void loadUsers()
}, { immediate: true })

async function reloadLatestTask() {
  const session = captureEditorSession()
  if (!session.targetId || !session.isCurrent() || session.projectRef !== props.projectRef || saving.value) return
  if (!(await confirmDiscard()) || !session.isCurrent() || saving.value) return
  saving.value = true
  try {
    const latest = await developmentApi.task(session.targetId)
    if (!session.isCurrent()) return
    editVersion.value = latest.rowVersion; currentActions.value = [...latest.allowedActions]
    form.title = latest.title; form.description = latest.description || ''; form.ownerId = latest.owner?.id || null
    form.developmentPlanStart = latest.developmentPlanStart || null; form.developmentPlanEnd = latest.developmentPlanEnd || null
    form.testPlanStart = latest.testPlanStart || null; form.testPlanEnd = latest.testPlanEnd || null
    if (latest.owner) userOptions.value = [latest.owner]
    baseline.value = JSON.stringify(form); isConflict.value = false; submitError.value = ''
  } catch (cause) { if (session.isCurrent()) submitError.value = apiErrorMessage(cause, '读取最新任务失败') }
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
    let result: DevelopmentTask
    if (session.targetId) {
      const input: UpdateTaskInput = {
        title: form.title.trim(), description: form.description || '', ownerId: form.ownerId || null,
        developmentPlanStart: form.developmentPlanStart || null, developmentPlanEnd: form.developmentPlanEnd || null,
        testPlanStart: form.testPlanStart || null, testPlanEnd: form.testPlanEnd || null, rowVersion: editVersion.value
      }
      result = await developmentApi.updateTask(session.targetId, input)
    } else {
      const input: CreateTaskInput = {
        projectRef: session.projectRef, sourceMode: form.sourceMode,
        sourceRequirementId: form.sourceMode === 'LINKED' ? form.sourceRequirementId : null,
        sourceRevision: form.sourceMode === 'LINKED' ? form.sourceRevision : null,
        systemId: form.systemId, title: form.title.trim(), description: form.description || '', ownerId: form.ownerId || null,
        developmentPlanStart: form.developmentPlanStart || null, developmentPlanEnd: form.developmentPlanEnd || null,
        testPlanStart: form.testPlanStart || null, testPlanEnd: form.testPlanEnd || null, requestId: requestId.value
      }
      result = await developmentApi.createTask(input)
    }
    if (!session.isCurrent()) return
    baseline.value = JSON.stringify(form); saving.value = false; emit('update:modelValue', false); emit('saved', result)
  } catch (err) {
    if (!session.isCurrent()) return
    isConflict.value = Boolean(session.targetId && (err as { response?: { status?: number } })?.response?.status === 409)
    submitError.value = apiErrorMessage(err, '保存失败，请检查后重试')
  } finally { if (session.isCurrent()) saving.value = false }
}
onBeforeUnmount(() => { editorEpoch++; pendingTicket++; systemsTicket++; usersTicket++ })
</script>

<template>
  <UiFormDrawer :model-value="modelValue" :title="drawerTitle" :class="{ 'dev-editor-readonly': !canSubmit }" width="min(680px, 94vw)" :loading="saving" :before-close="beforeClose" @update:model-value="handleDrawerClose" @submit="submit">
    <el-alert v-if="submitError" :title="submitError" :type="isConflict ? 'warning' : 'error'" show-icon :closable="false" class="dev-wide">
      <template v-if="isConflict" #default>
        <el-button type="warning" link :icon="Refresh" :disabled="saving" @click="reloadLatestTask">读取最新内容</el-button>
      </template>
    </el-alert>
    <el-form ref="formRef" :model="form" :rules="rules" :disabled="!canSubmit || saving" label-position="top" class="dev-form">
      <div class="dev-form-section">
        <div class="dev-form-grid">
          <el-form-item v-if="!task" label="来源模式" class="dev-wide">
            <el-segmented v-model="form.sourceMode" :options="[{ label: '关联需求', value: 'LINKED' }, { label: '自主创建', value: 'STANDALONE' }]" @change="onSourceModeChange" />
          </el-form-item>
          <el-form-item v-if="!task && form.sourceMode === 'LINKED'" label="待承接需求" class="dev-wide" required>
            <el-select :model-value="selectedPendingKey" filterable remote popper-class="dev-select-popper" :remote-method="loadPending" :loading="pendingLoading" placeholder="搜索待承接需求" @change="onSelectPending">
              <el-option v-for="item in pendingOptions" :key="`${item.sourceRequirementId}:${item.system.id}`" :label="`${item.sourceName} · ${item.system.name} (${item.sourceNumber})`" :value="`${item.sourceRequirementId}:${item.system.id}`" />
            </el-select>
            <div v-if="pendingError" class="dev-inline-error">{{ pendingError }} <el-button link type="primary" :icon="Refresh" @click="() => loadPending()">重试</el-button></div>
          </el-form-item>
          <el-form-item label="所属系统" prop="systemId">
            <el-input v-if="task || form.sourceMode === 'LINKED'" :model-value="selectedSystemName || '根据需求自动匹配'" disabled placeholder="根据需求自动匹配" />
            <template v-else>
              <el-select v-model="form.systemId" filterable remote :remote-method="loadSystems" :loading="systemsLoading" placeholder="搜索系统">
                <el-option v-for="item in systemOptions" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
              </el-select>
              <div v-if="systemsError" class="dev-inline-error">{{ systemsError }} <el-button link type="primary" :icon="Refresh" @click="() => loadSystems()">重试</el-button></div>
            </template>
          </el-form-item>
          <el-form-item label="负责人">
            <el-select v-model="form.ownerId" clearable filterable remote :remote-method="loadUsers" :loading="usersLoading" placeholder="留空则默认系统负责人">
              <el-option v-for="item in userOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <div v-if="usersError" class="dev-inline-error">{{ usersError }} <el-button link type="primary" :icon="Refresh" @click="() => loadUsers()">重试</el-button></div>
          </el-form-item>
          <el-form-item label="任务标题" prop="title" class="dev-wide">
            <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="请输入任务标题" />
          </el-form-item>
          <el-form-item label="任务描述" prop="description" class="dev-wide">
            <el-input v-model="form.description" type="textarea" :rows="4" maxlength="8000" show-word-limit placeholder="请输入详细描述" />
          </el-form-item>
        </div>
      </div>
      <div class="dev-form-section">
        <div class="dev-form-grid">
          <el-form-item label="开发计划开始">
            <el-date-picker v-model="form.developmentPlanStart" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" @change="() => formRef?.validateField('developmentPlanEnd')" />
          </el-form-item>
          <el-form-item label="开发计划完成" prop="developmentPlanEnd">
            <el-date-picker v-model="form.developmentPlanEnd" type="date" value-format="YYYY-MM-DD" placeholder="完成日期" />
          </el-form-item>
          <el-form-item label="测试计划开始">
            <el-date-picker v-model="form.testPlanStart" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" @change="() => formRef?.validateField('testPlanEnd')" />
          </el-form-item>
          <el-form-item label="测试计划完成" prop="testPlanEnd">
            <el-date-picker v-model="form.testPlanEnd" type="date" value-format="YYYY-MM-DD" placeholder="完成日期" />
          </el-form-item>
        </div>
      </div>
    </el-form>
  </UiFormDrawer>
</template>
