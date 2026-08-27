<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Check, Clock, Refresh, Search, View } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { apiErrorMessage } from '../api/error'
import { listWorkflowDone, listWorkflowInbox, type WorkflowDoneItem, type WorkflowPage, type WorkflowTask } from '../api/workflow'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import { useProjectContextStore } from '../stores/project-context'
import { formatDateOnly } from '../utils/date'
import { subscribeToPageActivation, subscribeToWorkflowTaskChanges } from '../utils/workflow-task-events'

type TaskTab = 'pending' | 'done'
type TaskRow = WorkflowTask | WorkflowDoneItem

const route = useRoute()
const router = useRouter()
const projectContext = useProjectContextStore()
const activeTab = ref<TaskTab>(route.query.tab === 'done' ? 'done' : 'pending')
const pending = ref<WorkflowTask[]>([])
const done = ref<WorkflowDoneItem[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const keyword = ref('')
const loading = ref(false)
const forbidden = ref(false)
const errorMessage = ref('')
let unsubscribeTaskChanges: (() => void) | undefined
let unsubscribePageActivation: (() => void) | undefined
let loadRequestId = 0

const rawRows = computed<TaskRow[]>(() => activeTab.value === 'pending' ? pending.value : done.value)
const projectRows = computed(() => rawRows.value.filter(item => !projectContext.currentRef || !item.project_ref || item.project_ref === projectContext.currentRef))
const rows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return projectRows.value
  return projectRows.value.filter(item => `${item.business_title || ''}${item.business_key}${item.project_name || ''}${item.node_name || item.task_key || ''}`.toLowerCase().includes(query))
})

function normalizePage<T>(value: WorkflowPage<T> | T[]): WorkflowPage<T> {
  if (Array.isArray(value)) return { records: value, total: value.length, page: page.value, size: pageSize.value }
  return value
}

function safeRouteLocation(path: string | undefined, query: Record<string, string | number>) {
  if (!path || !path.startsWith('/') || path.startsWith('//') || /[\\\r\n]/.test(path)) return null
  try {
    const decoded = decodeURIComponent(path)
    if (!decoded.startsWith('/') || decoded.startsWith('//') || /[\\\r\n]/.test(decoded)) return null
    const resolved = router.resolve(path)
    return { path: resolved.path, query: { ...resolved.query, ...query }, hash: resolved.hash }
  } catch {
    return null
  }
}

async function load() {
  const requestId = ++loadRequestId
  loading.value = true
  forbidden.value = false
  errorMessage.value = ''
  try {
    if (activeTab.value === 'pending') {
      const result = normalizePage((await listWorkflowInbox({ page: page.value, size: pageSize.value })).data.data)
      if (requestId !== loadRequestId) return
      pending.value = result.records
      total.value = result.total
    } else {
      const result = normalizePage((await listWorkflowDone({ page: page.value, size: pageSize.value })).data.data)
      if (requestId !== loadRequestId) return
      done.value = result.records
      total.value = result.total
    }
  } catch (error) {
    if (requestId !== loadRequestId) return
    const status = (error as { response?: { status?: number } })?.response?.status
    forbidden.value = status === 403
    errorMessage.value = apiErrorMessage(error, '任务列表加载失败，请稍后重试。')
  } finally {
    if (requestId === loadRequestId) loading.value = false
  }
}

function changeTab(value: string | number) {
  activeTab.value = value === 'done' ? 'done' : 'pending'
  page.value = 1
  keyword.value = ''
  void router.replace({ query: { ...route.query, tab: activeTab.value } })
  void load()
}

function changePage(value: number) {
  page.value = value
  void load()
}

function changePageSize(value: number) {
  pageSize.value = value
  page.value = 1
  void load()
}

function openBusiness(row: TaskRow) {
  const isPending = activeTab.value === 'pending'
  const location = safeRouteLocation(row.action_path, isPending ? { taskId: (row as WorkflowTask).id } : { instanceId: row.instance_id })
  void router.push(location || { path: '/workbench/tasks', query: { tab: activeTab.value } })
}

function actionLabel(action: string) {
  return ({ APPROVE: '已同意', REJECT: '不通过', RETURN: '已退回', ADD_SIGN: '已加签', CC: '已抄送', TRANSFER: '已转交', DELEGATE: '已委托' }[action] || action)
}

function pendingStatus(row: TaskRow) {
  return 'status' in row ? row.status : ''
}

function completedAction(row: TaskRow) {
  return 'action_code' in row ? actionLabel(row.action_code) : '-'
}

watch(() => route.query.tab, value => {
  const next = value === 'done' ? 'done' : 'pending'
  if (next !== activeTab.value) {
    activeTab.value = next
    page.value = 1
    void load()
  }
})
watch(() => projectContext.currentRef, () => { page.value = 1; void load() })
onMounted(async () => {
  unsubscribeTaskChanges = subscribeToWorkflowTaskChanges(() => { void load() })
  unsubscribePageActivation = subscribeToPageActivation(() => { void load() })
  await projectContext.initialize()
  await load()
})
onBeforeUnmount(() => {
  unsubscribeTaskChanges?.()
  unsubscribePageActivation?.()
})
</script>

<template>
  <main class="task-center-page">
    <UiPageHeader eyebrow="工作台" title="任务中心" :description="projectContext.current?.name || '当前项目'" />
    <el-tabs :model-value="activeTab" class="task-center-tabs" @tab-change="changeTab"><el-tab-pane name="pending"><template #label><span class="task-center-tab-label"><el-icon><Clock /></el-icon>我的待办</span></template></el-tab-pane><el-tab-pane name="done"><template #label><span class="task-center-tab-label"><el-icon><Check /></el-icon>我的已办</span></template></el-tab-pane></el-tabs>

    <UiToolbar>
      <el-input v-model="keyword" clearable placeholder="业务标题、单号、项目或节点" class="task-center-search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
      <span class="muted">{{ projectContext.current?.shortName || '当前项目' }}</span>
      <template #actions><el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template>
    </UiToolbar>

    <el-alert v-if="forbidden" type="warning" :closable="false" show-icon title="当前账号没有任务中心访问权限" />
    <el-alert v-else-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage"><el-button link type="primary" @click="load">重新加载</el-button></el-alert>
    <template v-else>
      <UiDataTable class="task-center-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="业务事项" min-width="240"><template #default="scope"><button type="button" class="task-center-business" @click="openBusiness(scope.row)"><strong>{{ scope.row.business_title || scope.row.business_key }}</strong><span>{{ scope.row.business_key }}</span></button></template></el-table-column>
        <el-table-column label="项目" min-width="180"><template #default="scope">{{ scope.row.project_name || '未关联项目' }}</template></el-table-column>
        <el-table-column label="当前节点" min-width="150"><template #default="scope">{{ scope.row.node_name || scope.row.task_key || '-' }}</template></el-table-column>
        <el-table-column v-if="activeTab === 'pending'" label="任务状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ PENDING: '待处理', SENT: '已抄送' }" tone="primary" /></template></el-table-column>
        <el-table-column v-else label="处理动作" width="110"><template #default="scope"><UiStatusTag :value="actionLabel(scope.row.action_code)" tone="success" /></template></el-table-column>
        <el-table-column :label="activeTab === 'pending' ? '进入时间' : '处理时间'" width="150"><template #default="scope">{{ formatDateOnly(scope.row.created_at) }}</template></el-table-column>
        <el-table-column label="操作" width="112" fixed="right"><template #default="scope"><el-button link type="primary" @click="openBusiness(scope.row)"><el-icon><View /></el-icon>查看详情</el-button></template></el-table-column>
        <template #footer><div class="task-center-footer"><span>共 {{ total }} 条记录</span><UiPagination :page="page" :page-size="pageSize" :total="total" @update:page="changePage" @update:page-size="changePageSize" /></div></template>
      </UiDataTable>

      <div class="task-center-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id">
          <header><div><strong>{{ row.business_title || row.business_key }}</strong><small>{{ row.business_key }}</small></div><UiStatusTag v-if="activeTab === 'pending'" :value="pendingStatus(row)" :labels="{ PENDING: '待处理', SENT: '已抄送' }" tone="primary" /><UiStatusTag v-else :value="completedAction(row)" tone="success" /></header>
          <dl><div><dt>项目</dt><dd>{{ row.project_name || '未关联项目' }}</dd></div><div><dt>节点</dt><dd>{{ row.node_name || row.task_key || '-' }}</dd></div><div><dt>{{ activeTab === 'pending' ? '进入时间' : '处理时间' }}</dt><dd>{{ formatDateOnly(row.created_at) }}</dd></div></dl>
          <footer><el-button link type="primary" @click="openBusiness(row)"><el-icon><View /></el-icon>查看详情</el-button></footer>
        </article>
        <UiEmptyState v-if="!loading && !rows.length" :title="activeTab === 'pending' ? '暂无待办任务' : '暂无已办记录'" />
        <div v-if="total > 0" class="task-center-mobile-footer"><UiPagination :page="page" :page-size="pageSize" :total="total" @update:page="changePage" @update:page-size="changePageSize" /></div>
      </div>
    </template>
  </main>
</template>
