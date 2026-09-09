<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Edit, Grid, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiPagination from '../../components/ui/UiPagination.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import { useProjectContextStore } from '../../stores/project-context'
import { apiErrorMessage } from '../../api/error'
import { developmentApi } from './api'
import { useDevelopmentTasks, useDevelopmentPermission, rememberDevelopmentScroll } from './composables/useDevelopmentTasks'
import { TASK_STATUS_LABELS, dateRange, statusTone, type DevelopmentTask, type PendingRequirement, type SystemRef, type UserRef } from './types'
import PendingRequirementsPanel from './components/PendingRequirementsPanel.vue'
import TaskFormDrawer from './components/TaskFormDrawer.vue'
import './development.css'

const project = useProjectContextStore(), route = useRoute(), router = useRouter(), can = useDevelopmentPermission()
const projectRef = computed(() => project.currentRef)
const { rows, total, loading, error, filters, reload, apply, setPage, setSize, scopeKey } = useDevelopmentTasks(projectRef)
const editing = ref(false), selectedTask = ref<DevelopmentTask | null>(null), selectedPending = ref<PendingRequirement | null>(null)
const systemOptions = ref<SystemRef[]>([]), userOptions = ref<UserRef[]>([]), optionError = ref('')
let optionTicket = 0
const systems = computed(() => [...new Map([...systemOptions.value, ...rows.value.map(row => row.system)].map(item => [item.id, item])).values()])
async function loadOptions() {
  const current = ++optionTicket, scope = projectRef.value
  systemOptions.value = []; userOptions.value = []; optionError.value = ''
  if (!scope) return
  try {
    const [systemPage, userPage] = await Promise.all([developmentApi.systems({ projectRef: scope, size: 100 }), developmentApi.users({ projectRef: scope, size: 100 })])
    if (current === optionTicket && scope === projectRef.value) { systemOptions.value = systemPage.records; userOptions.value = userPage.records }
  } catch (cause) { if (current === optionTicket) optionError.value = apiErrorMessage(cause, '筛选选项加载失败') }
}
function create(pending: PendingRequirement | null = null) { selectedTask.value = null; selectedPending.value = pending; editing.value = true }
function edit(task: DevelopmentTask) { selectedTask.value = task; selectedPending.value = null; editing.value = true }
function open(task: DevelopmentTask) {
  rememberDevelopmentScroll(scopeKey.value)
  void router.push({ path: `/development/tasks/${task.id}`, query: { returnTo: route.fullPath } })
}
async function saved(task: DevelopmentTask) { if (selectedTask.value) await reload(); else open(task) }
watch(projectRef, loadOptions, { immediate: true })
onMounted(() => { void project.initialize() })
</script>

<template>
  <div class="development-page">
    <UiPageHeader title="开发任务"><template #actions><el-button :icon="Grid" :disabled="!projectRef" @click="router.push('/development/work-items/board')">工作项看板</el-button><el-button v-if="can('development:task:create')" type="primary" :icon="Plus" :disabled="!projectRef" @click="create()">新建任务</el-button></template></UiPageHeader>
    <UiEmptyState v-if="!projectRef" title="请选择项目" :description="project.error || '暂无可访问项目'" />
    <template v-else>
      <PendingRequirementsPanel :project-ref="projectRef" :system-id="filters.systemId" :keyword="filters.keyword" :can-claim="can('development:task:create')" @claim="create" />
      <UiToolbar>
        <el-input v-model="filters.keyword" class="dev-search" placeholder="任务编号或名称" clearable aria-label="检索任务" @keyup.enter="apply" @clear="apply"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-select v-model="filters.status" clearable placeholder="任务状态" class="dev-filter" aria-label="任务状态筛选" @change="apply"><el-option v-for="(label, value) in TASK_STATUS_LABELS" :key="value" :label="label" :value="value" /></el-select>
        <el-select v-model="filters.systemId" clearable filterable placeholder="所属系统" class="dev-filter" aria-label="所属系统筛选" @change="apply"><el-option v-for="system in systems" :key="system.id" :label="system.name" :value="system.id" /></el-select>
        <el-select v-model="filters.ownerId" clearable filterable placeholder="负责人" class="dev-filter" aria-label="负责人筛选" @change="apply"><el-option v-for="user in userOptions" :key="user.id" :label="user.name" :value="user.id" /></el-select>
        <template #actions><el-tooltip content="查询任务"><el-button :icon="Search" circle aria-label="查询任务" @click="apply" /></el-tooltip><el-tooltip content="刷新任务"><el-button :icon="Refresh" circle :loading="loading" aria-label="刷新任务" @click="reload" /></el-tooltip></template>
      </UiToolbar>
      <el-alert v-if="optionError" :title="optionError" type="warning" :closable="false"><el-button link @click="loadOptions">重试选项</el-button></el-alert>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button link @click="reload">重试</el-button></el-alert>
      <UiDataTable class="dev-desktop-table" :data="rows" :loading="loading" row-key="id">
        <el-table-column label="任务" min-width="200"><template #default="{ row }"><button type="button" class="dev-record-link" @click="open(row)"><strong>{{ row.title }}</strong><small>{{ row.number }}</small></button></template></el-table-column>
        <el-table-column label="系统 / 来源" min-width="145"><template #default="{ row }"><span>{{ row.system.name }}</span><small class="dev-block dev-muted">{{ row.source?.number || '自主创建' }}</small></template></el-table-column>
        <el-table-column label="负责人" min-width="110"><template #default="{ row }">{{ row.owner.name }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><UiStatusTag :value="row.status" :labels="TASK_STATUS_LABELS" :tone="statusTone(row.status)" /></template></el-table-column>
        <el-table-column label="开发 / 测试排期" min-width="195"><template #default="{ row }"><small class="dev-block">{{ dateRange(row.developmentPlanStart, row.developmentPlanEnd) }}</small><small class="dev-muted">{{ dateRange(row.testPlanStart, row.testPlanEnd) }}</small></template></el-table-column>
        <el-table-column label="工作项" width="75"><template #default="{ row }">{{ row.completedWorkItemCount }} / {{ row.workItemCount }}</template></el-table-column>
        <el-table-column label="操作" width="112" fixed="right"><template #default="{ row }"><el-tooltip content="查看详情"><el-button :icon="View" text circle aria-label="查看任务详情" @click="open(row)" /></el-tooltip><el-tooltip v-if="row.allowedActions.includes('UPDATE') && can('development:task:update')" content="编辑任务"><el-button :icon="Edit" text circle aria-label="编辑任务" @click="edit(row)" /></el-tooltip></template></el-table-column>
      </UiDataTable>
      <div v-loading="loading" class="dev-mobile-cards">
        <article v-for="task in rows" :key="task.id" class="dev-record-card">
          <header><button class="dev-record-link" type="button" @click="open(task)"><strong>{{ task.title }}</strong><small>{{ task.number }}</small></button><UiStatusTag :value="task.status" :labels="TASK_STATUS_LABELS" :tone="statusTone(task.status)" /></header>
          <dl><div><dt>系统</dt><dd>{{ task.system.name }}</dd></div><div><dt>负责人</dt><dd>{{ task.owner.name }}</dd></div><div><dt>开发排期</dt><dd>{{ dateRange(task.developmentPlanStart, task.developmentPlanEnd) }}</dd></div><div><dt>工作项</dt><dd>{{ task.completedWorkItemCount }} / {{ task.workItemCount }}</dd></div></dl>
          <footer><el-button type="primary" link :icon="View" @click="open(task)">查看详情</el-button><el-button v-if="task.allowedActions.includes('UPDATE') && can('development:task:update')" link :icon="Edit" @click="edit(task)">编辑</el-button></footer>
        </article>
        <UiEmptyState v-if="!loading && !error && !rows.length" title="暂无开发任务" description="当前范围内没有匹配记录" />
      </div>
      <div class="dev-pagination"><UiPagination :total="total" :page="filters.page" :page-size="filters.size" @update:page="setPage" @update:page-size="setSize" /></div>
    </template>
    <TaskFormDrawer v-model="editing" :project-ref="projectRef" :task="selectedTask" :pending="selectedPending" @saved="saved" />
  </div>
</template>
