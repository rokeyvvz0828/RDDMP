<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Edit, Grid, List, MoreFilled, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../components/ui/UiPagination.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import { apiErrorMessage } from '../../../api/error'
import { useAuthStore } from '../../../stores/auth'
import { developmentApi } from '../api'
import { useWorkItems } from '../composables/useWorkItems'
import { useDevelopmentPermission } from '../composables/useDevelopmentTasks'
import { ACTION_LABELS, WORK_ITEM_STATUS_LABELS, dateRange, statusTone, type DevelopmentTask, type WorkItem, type UserRef, type SystemRef, type WorkItemStatus } from '../types'
import WorkItemFormDrawer from './WorkItemFormDrawer.vue'
import WorkItemBoard from './WorkItemBoard.vue'

const props = defineProps<{ task?: DevelopmentTask; projectRef: string }>()
const emit = defineEmits<{ changed: [] }>()
const route = useRoute(), router = useRouter(), can = useDevelopmentPermission(), auth = useAuthStore()
const taskId = computed(() => props.task?.id), projectRef = computed(() => props.projectRef)
const { rows, total, page, size, mode, columns, filters, counts, loading, error, actionError, busy, reload, apply, setPage, setMode, changeColumnPage, retryColumn, rememberPosition, act } = useWorkItems(projectRef, taskId)
const editing = ref(false), selected = ref<WorkItem | null>(null), users = ref<UserRef[]>([]), systems = ref<SystemRef[]>([]), tasks = ref<DevelopmentTask[]>([])
const optionErrors = reactive({ users: '', systems: '', tasks: '', item: '' })
const optionError = computed(() => Object.values(optionErrors).filter(Boolean).join('；'))
let ticket = 0, systemTicket = 0, taskTicket = 0, itemTicket = 0, disposed = false
const canCreate = computed(() => props.task?.allowedActions.includes('CREATE_WORK_ITEM') && can('development:work-item:update'))
async function loadUsers(keyword = '') {
  if (disposed || !auth.user?.id || !props.projectRef) return
  const current = ++ticket
  optionErrors.users = ''
  try { const result = await developmentApi.users({ projectRef: props.projectRef, keyword, size: 100 }); if (current === ticket) users.value = result.records }
  catch (cause) { if (current === ticket) optionErrors.users = apiErrorMessage(cause, '人员选项加载失败') }
}
async function loadSystems(keyword = '') {
  if (disposed || !auth.user?.id || !props.projectRef) return
  const current = ++systemTicket; optionErrors.systems = ''
  try { const result = await developmentApi.systems({ projectRef: props.projectRef, keyword, size: 100 }); if (current === systemTicket) systems.value = result.records }
  catch (cause) { if (current === systemTicket) optionErrors.systems = apiErrorMessage(cause, '系统选项加载失败') }
}
async function loadTasks(keyword = '') {
  if (disposed || !auth.user?.id || !props.projectRef) return
  const current = ++taskTicket; optionErrors.tasks = ''
  try { const result = await developmentApi.tasks({ projectRef: props.projectRef, keyword, size: 100 }); if (current === taskTicket) tasks.value = result.records }
  catch (cause) { if (current === taskTicket) optionErrors.tasks = apiErrorMessage(cause, '任务选项加载失败') }
}
function reloadOptions() { void loadUsers(); if (!props.task) { void loadSystems(); void loadTasks() } }
function open(item: WorkItem | null) { selected.value = item; editing.value = true }
function openCard(item: WorkItem) {
  if (props.task) open(item)
  else { rememberPosition(); void router.push({ path: `/development/tasks/${item.taskId}`, query: { item: item.id, returnTo: route.fullPath } }) }
}
function selectStatus(status: WorkItemStatus) { filters.status = status; void apply() }
function primaryAction(item: WorkItem) { return ['START', 'SUBMIT', 'ACCEPT'].find(action => item.allowedActions.includes(action)) }
function moreActions(item: WorkItem) { return ['RETURN', 'REOPEN'].filter(action => item.allowedActions.includes(action)) }
async function perform(item: WorkItem, action: string) {
  if (disposed || !auth.user?.id || busy.has(item.id) || !item.allowedActions.includes(action)) return
  const scope = props.projectRef, fixedTask = props.task?.id, userId = auth.user.id
  let reason: string | undefined
  try {
    if (['RETURN', 'REOPEN'].includes(action)) reason = (await ElMessageBox.prompt(`请填写${ACTION_LABELS[action]}原因`, ACTION_LABELS[action], { inputType: 'textarea', inputValidator: value => Boolean(value?.trim()) || '原因不能为空', confirmButtonText: ACTION_LABELS[action], cancelButtonText: '返回' })).value
    else if (action === 'ACCEPT') await ElMessageBox.confirm(`确认「${item.title}」验收通过？`, '验收工作项', { confirmButtonText: '验收通过', cancelButtonText: '返回' })
  } catch { return }
  if (disposed || userId !== auth.user?.id || scope !== props.projectRef || fixedTask !== props.task?.id) return
  if (await act(item, action, reason)) emit('changed')
}
async function saved() { await reload(); emit('changed') }
async function locateItem() {
  if (disposed || !auth.user?.id || !props.projectRef) return
  const current = ++itemTicket
  const id = typeof route.query.item === 'string' ? route.query.item : ''
  if (!/^\d+$/.test(id)) return
  try { const item = await developmentApi.workItem(id); if (current === itemTicket && (!props.task || item.taskId === props.task.id)) open(item) }
  catch (cause) { if (current === itemTicket) optionErrors.item = apiErrorMessage(cause, '工作项不可访问') }
}
watch([projectRef, taskId, () => auth.user?.id], () => {
  ticket++; systemTicket++; taskTicket++; itemTicket++
  users.value = []; systems.value = []; tasks.value = []; selected.value = null; editing.value = false
  Object.assign(optionErrors, { users: '', systems: '', tasks: '', item: '' })
  if (projectRef.value && auth.user?.id) { reloadOptions(); void locateItem() }
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { disposed = true; ticket++; systemTicket++; taskTicket++; itemTicket++ })
</script>

<template>
  <section class="dev-work-items" :class="{ 'is-board': mode === 'board' }">
    <div class="dev-view-mode"><el-segmented :model-value="mode" :options="[{ label: '看板', value: 'board' }, { label: '列表', value: 'list' }]" aria-label="工作项呈现模式" @change="setMode"><template #default="{ item }"><el-icon><Grid v-if="item.value === 'board'" /><List v-else /></el-icon><span>{{ item.label }}</span></template></el-segmented></div>
    <UiToolbar>
      <el-input v-model="filters.keyword" placeholder="工作项名称或任务编号" clearable class="dev-search" aria-label="检索工作项" @keyup.enter="apply" @clear="apply"><template #prefix><el-icon><Search /></el-icon></template></el-input>
      <el-select v-model="filters.status" clearable placeholder="工作项状态" class="dev-filter dev-status-filter" aria-label="工作项状态筛选" @change="apply"><el-option label="全部状态" value="" /><el-option v-for="(label, status) in WORK_ITEM_STATUS_LABELS" :key="status" :label="`${label} (${counts[status]})`" :value="status" /></el-select>
      <el-select v-if="!task" v-model="filters.systemId" clearable filterable remote :remote-method="loadSystems" placeholder="所属系统" class="dev-filter" aria-label="看板系统筛选" @change="apply"><el-option v-for="system in systems" :key="system.id" :label="system.name" :value="system.id" /></el-select>
      <el-select v-if="!task" v-model="filters.taskId" clearable filterable remote popper-class="dev-select-popper" :remote-method="loadTasks" placeholder="开发任务" class="dev-filter" aria-label="看板任务筛选" @change="apply"><el-option v-for="entry in tasks" :key="entry.id" :label="`${entry.title} (${entry.number})`" :value="entry.id" /></el-select>
      <el-select v-model="filters.assigneeId" clearable filterable remote :remote-method="loadUsers" placeholder="指定人员" class="dev-filter" aria-label="指定人员筛选" @change="apply"><el-option v-for="user in users" :key="user.id" :label="user.name" :value="user.id" /></el-select>
      <el-date-picker v-model="filters.plannedFrom" type="date" value-format="YYYY-MM-DD" placeholder="计划开始自" class="dev-filter-date" aria-label="计划范围开始" @change="apply" />
      <el-date-picker v-model="filters.plannedTo" type="date" value-format="YYYY-MM-DD" placeholder="计划结束至" class="dev-filter-date" aria-label="计划范围结束" @change="apply" />
      <template #actions><el-tooltip content="刷新工作项"><el-button :icon="Refresh" circle :loading="loading" aria-label="刷新工作项" @click="reload" /></el-tooltip><el-button v-if="canCreate" type="primary" :icon="Plus" @click="open(null)">新增工作项</el-button></template>
    </UiToolbar>
    <el-alert v-if="optionError" :title="optionError" type="warning" :closable="false"><el-button link @click="reloadOptions">重试选项</el-button></el-alert>
    <el-alert v-if="error || actionError" :title="actionError || error" type="error" show-icon :closable="false"><el-button link @click="reload">重新加载</el-button></el-alert>
    <template v-if="mode === 'list'">
    <UiDataTable class="dev-desktop-table" :data="rows" :loading="loading" row-key="id">
      <el-table-column label="工作项" min-width="220"><template #default="{ row }"><button class="dev-record-link" type="button" @click="open(row)"><strong>{{ row.title }}</strong><small v-if="!task">{{ row.taskNumber }}</small></button><small v-if="row.blocked" class="dev-block dev-danger">阻塞：{{ row.blockReason }}</small><small v-for="warning in row.warnings" :key="warning" class="dev-block dev-warning">{{ warning }}</small></template></el-table-column>
      <el-table-column label="指定人员" width="110"><template #default="{ row }">{{ row.assignee.name }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><UiStatusTag :value="row.status" :labels="WORK_ITEM_STATUS_LABELS" :tone="statusTone(row.status)" /></template></el-table-column>
      <el-table-column label="计划 / 实际" min-width="210"><template #default="{ row }"><small class="dev-block">{{ dateRange(row.plannedStart, row.plannedEnd) }}</small><small class="dev-muted">{{ dateRange(row.actualStart, row.actualEnd) }}</small></template></el-table-column>
      <el-table-column label="耗时偏差" width="116"><template #default="{ row }"><span :class="{ 'dev-danger': row.duration.variancePercent > 0 }">{{ row.duration.status === 'available' ? `${row.duration.variancePercent}%` : '暂不可计算' }}</span></template></el-table-column>
      <el-table-column label="操作" width="190" fixed="right"><template #default="{ row }">
        <el-button v-if="primaryAction(row)" type="primary" link :loading="busy.has(row.id)" @click="perform(row, primaryAction(row)!)">{{ ACTION_LABELS[primaryAction(row)!] }}</el-button>
        <el-tooltip content="查看或编辑工作项"><el-button :icon="Edit" text circle aria-label="打开工作项" @click="open(row)" /></el-tooltip>
        <el-dropdown v-if="moreActions(row).length" @command="(action: string) => perform(row, action)"><el-button :icon="MoreFilled" text circle :disabled="busy.has(row.id)" aria-label="更多工作项操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="action in moreActions(row)" :key="action" :command="action">{{ ACTION_LABELS[action] }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      </template></el-table-column>
    </UiDataTable>
    <div v-loading="loading" class="dev-mobile-cards">
      <article v-for="item in rows" :key="item.id" class="dev-record-card">
        <header><button class="dev-record-link" type="button" @click="open(item)"><strong>{{ item.title }}</strong><small>{{ item.taskNumber }}</small></button><UiStatusTag :value="item.status" :labels="WORK_ITEM_STATUS_LABELS" :tone="statusTone(item.status)" /></header>
        <dl><div><dt>指定人员</dt><dd>{{ item.assignee.name }}</dd></div><div><dt>计划日期</dt><dd>{{ dateRange(item.plannedStart, item.plannedEnd) }}</dd></div><div><dt>实际日期</dt><dd>{{ dateRange(item.actualStart, item.actualEnd) }}</dd></div><div><dt>耗时偏差</dt><dd>{{ item.duration.status === 'available' ? `${item.duration.variancePercent}%` : '暂不可计算' }}</dd></div></dl>
        <p v-if="item.blocked" class="dev-danger">阻塞：{{ item.blockReason }}</p>
        <p v-for="warning in item.warnings" :key="warning" class="dev-warning">{{ warning }}</p>
        <footer><el-button link @click="open(item)">详情</el-button><el-button v-if="primaryAction(item)" type="primary" link :loading="busy.has(item.id)" @click="perform(item, primaryAction(item)!)">{{ ACTION_LABELS[primaryAction(item)!] }}</el-button><el-dropdown v-if="moreActions(item).length" @command="(action: string) => perform(item, action)"><el-button :icon="MoreFilled" text circle aria-label="更多工作项操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="action in moreActions(item)" :key="action" :command="action">{{ ACTION_LABELS[action] }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></footer>
      </article>
      <UiEmptyState v-if="!loading && !rows.length && !error" title="暂无工作项" description="当前范围内没有匹配记录" />
    </div>
    <div class="dev-pagination"><UiPagination :total="total" :page="page" :page-size="size" @update:page="setPage" @update:page-size="size = $event; apply()" /></div>
    </template>
    <WorkItemBoard v-else :columns="columns" :busy="busy" :status-filter="filters.status" :cross-task="!task" :page-size="size" @open="openCard" @edit="open" @action="perform" @column-page="changeColumnPage" @retry="retryColumn" @select-status="selectStatus" />
    <WorkItemFormDrawer v-model="editing" :project-ref="projectRef" :task-id="selected?.taskId || task?.id || ''" :item="selected" :default-owner="task?.owner" @saved="saved" />
  </section>
</template>
