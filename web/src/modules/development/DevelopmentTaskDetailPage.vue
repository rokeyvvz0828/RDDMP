<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { isNavigationFailure, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Edit, MoreFilled, Refresh, Select, VideoPlay } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import { useProjectContextStore } from '../../stores/project-context'
import { apiErrorMessage } from '../../api/error'
import { developmentApi } from './api'
import { safeDevelopmentReturn, useDevelopmentPermission } from './composables/useDevelopmentTasks'
import { ACTION_LABELS, TASK_STATUS_LABELS, SOURCE_ROLE_LABELS, statusTone, dateRange, type DevelopmentTask } from './types'
import TaskFormDrawer from './components/TaskFormDrawer.vue'
import WorkItemsView from './components/WorkItemsView.vue'
import TaskStagePanel from './components/TaskStagePanel.vue'
import TaskChangeLog from './components/TaskChangeLog.vue'
import './development.css'

const route = useRoute(), router = useRouter(), project = useProjectContextStore(), can = useDevelopmentPermission()
const task = ref<DevelopmentTask | null>(null), loading = ref(false), error = ref(''), actionError = ref(''), busy = ref(false), editing = ref(false), stageDirty = ref(false)
const tab = ref(['work', 'stages', 'changes'].includes(String(route.query.tab)) ? String(route.query.tab) : 'work')
const stagePanel = ref<{ confirmLeave: () => Promise<boolean> }>()
const expanded = ref(false)
let ticket = 0
const backPath = computed(() => safeDevelopmentReturn(route.query.returnTo))
const backLabel = computed(() => backPath.value.startsWith('/development/work-items/board') ? '返回工作项看板' : '返回任务列表')
const moreActions = computed(() => task.value?.allowedActions.filter(action => ['CANCEL', 'REOPEN', 'RESTORE'].includes(action)) || [])
const scopeMatches = computed(() => !task.value || !project.currentRef || project.currentRef === task.value.projectRef)
async function load() {
  const current = ++ticket, id = String(route.params.taskId || '')
  if (!/^\d+$/.test(id)) { error.value = '任务标识无效'; return }
  loading.value = true; error.value = ''
  try {
    const result = await developmentApi.task(id)
    await project.initialize()
    if (current !== ticket) return
    task.value = result
    project.select(result.projectRef)
  } catch (cause) { if (current === ticket) { task.value = null; error.value = apiErrorMessage(cause, '任务详情加载失败') } }
  finally { if (current === ticket) loading.value = false }
}
async function perform(action: string) {
  const current = task.value
  if (!current || busy.value || stageDirty.value || !current.allowedActions.includes(action)) return
  let reason: string | undefined
  try {
    if (['CANCEL', 'REOPEN', 'RESTORE'].includes(action)) {
      reason = (await ElMessageBox.prompt(`请填写${ACTION_LABELS[action]}的原因`, ACTION_LABELS[action], { inputValidator: value => Boolean(value?.trim()) || '原因不能为空', inputType: 'textarea', confirmButtonText: ACTION_LABELS[action], cancelButtonText: '返回' })).value
    } else if (action === 'COMPLETE') {
      await ElMessageBox.confirm(`确认完成「${current.title}」？`, '完成任务', { type: 'warning', confirmButtonText: '确认完成', cancelButtonText: '返回' })
    }
  } catch { return }
  busy.value = true; actionError.value = ''
  try { task.value = await developmentApi.taskAction(current.id, action, current.rowVersion, reason) }
  catch (cause) { actionError.value = apiErrorMessage(cause, '任务操作失败，请重试'); await load() }
  finally { busy.value = false }
}
async function beforeTabLeave() { return !stageDirty.value || await stagePanel.value?.confirmLeave() !== false }
watch(() => route.params.taskId, () => { task.value = null; void load() }, { immediate: true })
watch(() => project.currentRef, async (next, previous) => {
  if (!previous || next === previous || task.value?.projectRef === next) return
  ticket++
  const result = await router.push('/development/tasks')
  if (isNavigationFailure(result)) project.select(previous)
}, { flush: 'sync' })
onBeforeUnmount(() => { ticket++ })
</script>

<template>
  <div v-loading="loading" class="development-page dev-detail">
    <div class="dev-back-row"><el-button :icon="ArrowLeft" text @click="router.push(backPath)">{{ backLabel }}</el-button><el-tooltip content="刷新详情"><el-button :icon="Refresh" circle :disabled="stageDirty || busy" :loading="loading" aria-label="刷新任务详情" @click="load" /></el-tooltip></div>
    <el-result v-if="error" icon="warning" :title="error"><template #extra><el-button @click="load">重试</el-button></template></el-result>
    <div v-else-if="task" v-show="scopeMatches" class="dev-detail-content">
      <UiPageHeader :title="task.title"><template #actions>
        <el-button v-if="task.allowedActions.includes('UPDATE') && can('development:task:update')" :icon="Edit" :disabled="busy || stageDirty" @click="editing = true">编辑</el-button>
        <el-button v-if="task.allowedActions.includes('START')" :icon="VideoPlay" :disabled="stageDirty" :loading="busy" @click="perform('START')">开始任务</el-button>
        <el-button v-if="task.allowedActions.includes('COMPLETE')" type="primary" :icon="Select" :disabled="stageDirty" :loading="busy" @click="perform('COMPLETE')">完成任务</el-button>
        <el-dropdown v-if="moreActions.length" @command="perform"><el-button :icon="MoreFilled" :disabled="busy || stageDirty" aria-label="更多任务操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="action in moreActions" :key="action" :command="action">{{ ACTION_LABELS[action] }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      </template></UiPageHeader>
      <div class="dev-identity"><code>{{ task.number }}</code><UiStatusTag :value="task.status" :labels="TASK_STATUS_LABELS" :tone="statusTone(task.status)" /><span v-for="role in task.source?.roles || []" :key="role" class="dev-muted">{{ SOURCE_ROLE_LABELS[role] || role }}</span></div>
      <dl class="dev-facts"><div><dt>所属系统</dt><dd>{{ task.system.name }}</dd></div><div><dt>任务负责人</dt><dd>{{ task.owner.name }}</dd></div><div><dt>来源需求</dt><dd>{{ task.source?.number || '自主创建' }}</dd></div><div><dt>已完成工作项</dt><dd>{{ task.completedWorkItemCount }} / {{ task.workItemCount }}</dd></div><div><dt>开发排期</dt><dd>{{ dateRange(task.developmentPlanStart, task.developmentPlanEnd) }}</dd></div><div><dt>测试排期</dt><dd>{{ dateRange(task.testPlanStart, task.testPlanEnd) }}</dd></div></dl>
      <div v-if="task.description" class="dev-description"><p>{{ expanded ? task.description : task.description.slice(0, 240) }}</p><el-button v-if="task.description.length > 240" link @click="expanded = !expanded">{{ expanded ? '收起' : '展开' }}</el-button></div>
      <el-alert v-if="actionError" :title="actionError" type="error" show-icon :closable="false" />
      <el-tabs v-model="tab" :before-leave="beforeTabLeave" class="dev-detail-tabs">
        <el-tab-pane label="工作项" name="work"><WorkItemsView v-if="tab === 'work'" :task="task" :project-ref="task.projectRef" @changed="load" /></el-tab-pane>
        <el-tab-pane label="阶段信息" name="stages"><TaskStagePanel v-if="tab === 'stages'" ref="stagePanel" :task="task" @dirty="stageDirty = $event" @saved="load" /></el-tab-pane>
        <el-tab-pane label="修改记录" name="changes"><TaskChangeLog v-if="tab === 'changes'" :task-id="task.id" /></el-tab-pane>
      </el-tabs>
      <TaskFormDrawer v-model="editing" :project-ref="task.projectRef" :task="task" @saved="task = $event" />
    </div>
  </div>
</template>
