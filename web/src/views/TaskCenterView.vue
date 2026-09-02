<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Check, Clock, Refresh, Search, View } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { apiErrorMessage } from '../api/error'
import { listWorkflowDone, listWorkflowInbox, listWorkflowSubmitted, type WorkflowDoneItem, type WorkflowPage, type WorkflowTask } from '../api/workflow'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import { useProjectContextStore } from '../stores/project-context'
import { formatDateOnly } from '../utils/date'
import { subscribeToPageActivation, subscribeToWorkflowTaskChanges } from '../utils/workflow-task-events'

type TaskTab = 'pending' | 'done' | 'submitted'
type WorkflowSubmittedItem = WorkflowTask & { current_assignees?: string; task_count?: number; instance_status?: string; definition_name?: string }
type TaskRow = WorkflowTask | WorkflowDoneItem | WorkflowSubmittedItem

const route = useRoute()
const router = useRouter()
const projectContext = useProjectContextStore()
const activeTab = ref<TaskTab>(route.query.tab === 'done' ? 'done' : route.query.tab === 'submitted' ? 'submitted' : 'pending')
const pending = ref<WorkflowTask[]>([])
const done = ref<WorkflowDoneItem[]>([])
const submitted = ref<WorkflowSubmittedItem[]>([])
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

const rawRows = computed<TaskRow[]>(() => {
  if (activeTab.value === 'pending') return pending.value
  if (activeTab.value === 'done') return done.value
  return submitted.value
})
// 项目关联过滤：需求模块(req_project)与系统模块(pm_project)是两套独立项目体系，project_ref 格式可能不一致。
// 兜底策略：currentRef 为空 OR task 无项目关联 OR project_ref 精确相等 OR project_name 匹配当前项目名，均保留显示。
function matchesProject(item: { project_ref?: string | null; project_name?: string | null }): boolean {
  if (!projectContext.currentRef) return true
  if (!item.project_ref) return true
  if (item.project_ref === projectContext.currentRef) return true
  const currentName = projectContext.current?.name?.trim()
  const itemName = item.project_name?.trim()
  return !!currentName && !!itemName && currentName === itemName
}
const projectRows = computed(() => rawRows.value.filter(item => matchesProject(item)))
const rows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return projectRows.value
  return projectRows.value.filter(item => `${item.business_title || ''}${item.business_key}${item.project_name || ''}${item.node_name || item.task_key || ''}${(item as WorkflowSubmittedItem).current_assignees || ''}${(item as WorkflowSubmittedItem).definition_name || ''}`.toLowerCase().includes(query))
})

function normalizePage<T>(value: WorkflowPage<T> | T[]): WorkflowPage<T> {
  if (Array.isArray(value)) return { records: value, total: value.length, page: page.value, size: pageSize.value }
  return value
}

function submittedStatus(row: WorkflowSubmittedItem): string {
  if (row.instance_status && row.instance_status !== 'RUNNING') return row.instance_status
  return 'status' in row ? row.status || 'PENDING' : 'PENDING'
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
    } else if (activeTab.value === 'done') {
      const result = normalizePage((await listWorkflowDone({ page: page.value, size: pageSize.value })).data.data)
      if (requestId !== loadRequestId) return
      done.value = result.records
      total.value = result.total
    } else {
      const result = normalizePage((await listWorkflowSubmitted({ page: page.value, size: pageSize.value })).data.data)
      submitted.value = result.records as WorkflowSubmittedItem[]
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
  if (value === 'done') activeTab.value = 'done'
  else if (value === 'submitted') activeTab.value = 'submitted'
  else activeTab.value = 'pending'
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
  // submitted: 若后端回传的当前活跃审批task id存在，带上进入业务页（只读，显示审批信息）
  const sub = 'current_assignees' in row ? (row as WorkflowSubmittedItem) : null
  const carryTaskId = !!('id' in row && row.id) && (isPending || (sub != null && (sub.task_count ?? 0) > 0))
  const taskId: number | undefined = carryTaskId ? Number((row as { id: number }).id) : undefined
  const query: Record<string, string | number> = taskId ? { taskId } : { instanceId: row.instance_id }
  const location = safeRouteLocation(row.action_path, query)
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
  const next: TaskTab = value === 'done' ? 'done' : value === 'submitted' ? 'submitted' : 'pending'
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
    <el-tabs :model-value="activeTab" class="task-center-tabs" @tab-change="changeTab">
      <el-tab-pane name="pending"><template #label><span class="task-center-tab-label"><el-icon><Clock /></el-icon>我的待办</span></template></el-tab-pane>
      <el-tab-pane name="submitted"><template #label><span class="task-center-tab-label"><el-icon><View /></el-icon>我发起的</span></template></el-tab-pane>
      <el-tab-pane name="done"><template #label><span class="task-center-tab-label"><el-icon><Check /></el-icon>我的已办</span></template></el-tab-pane>
    </el-tabs>

    <UiToolbar>
      <el-input v-model="keyword" clearable :placeholder="activeTab === 'submitted' ? '业务标题、单号、项目、审批人或流程定义' : '业务标题、单号、项目或节点'" class="task-center-search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
      <span class="muted">{{ projectContext.current?.shortName || '当前项目' }}</span>
      <template #actions><el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template>
    </UiToolbar>

    <el-alert v-if="forbidden" type="warning" :closable="false" show-icon title="当前账号没有任务中心访问权限" />
    <el-alert v-else-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage"><el-button link type="primary" @click="load">重新加载</el-button></el-alert>
    <template v-else>
      <UiDataTable class="task-center-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="业务事项" min-width="240"><template #default="scope"><button type="button" class="task-center-business" @click="openBusiness(scope.row)"><strong>{{ scope.row.business_title || scope.row.business_key }}</strong><span>{{ scope.row.business_key }}</span></button></template></el-table-column>
        <el-table-column label="项目" min-width="180"><template #default="scope">{{ scope.row.project_name || '未关联项目' }}</template></el-table-column>
        <el-table-column label="当前节点" min-width="150"><template #default="scope">{{ scope.row.node_name || scope.row.task_key || (scope.row as WorkflowSubmittedItem).definition_name || '-' }}</template></el-table-column>
        <el-table-column v-if="activeTab === 'pending'" label="任务状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ PENDING: '待处理', SENT: '已抄送' }" tone="primary" /></template></el-table-column>
        <el-table-column v-else-if="activeTab === 'submitted'" label="当前审批人 / 状态" min-width="220"><template #default="scope">
          <template v-if="(scope.row as WorkflowSubmittedItem).current_assignees">
            <div class="task-center-assignees">{{ (scope.row as WorkflowSubmittedItem).current_assignees }}</div>
            <UiStatusTag
              :value="(scope.row as WorkflowSubmittedItem).task_count ? '审批中' : submittedStatus(scope.row as WorkflowSubmittedItem)"
              :labels="{ APPROVED: '已完成', COMPLETED: '已完成', DONE: '已完成', REJECTED: '被驳回', RETURNED: '被回退', TERMINATED: '已终止', RUNNING: '审批中', PENDING: '审批中', SENT: '已抄送' }"
              tone="primary" />
          </template>
          <template v-else>
            <UiStatusTag
              :value="submittedStatus(scope.row as WorkflowSubmittedItem)"
              :labels="{ APPROVED: '已完成', COMPLETED: '已完成', DONE: '已完成', REJECTED: '被驳回', RETURNED: '被回退', TERMINATED: '已终止', RUNNING: '审批中', PENDING: '审批中', SENT: '已抄送' }"
              :tone="['APPROVED','COMPLETED','DONE'].includes(submittedStatus(scope.row as WorkflowSubmittedItem)) ? 'success' : (submittedStatus(scope.row as WorkflowSubmittedItem) === 'TERMINATED' ? 'info' : (['REJECTED','RETURNED'].includes(submittedStatus(scope.row as WorkflowSubmittedItem)) ? 'danger' : 'warning'))" />
          </template>
        </template></el-table-column>
        <el-table-column v-else label="处理动作" width="110"><template #default="scope"><UiStatusTag :value="actionLabel(scope.row.action_code)" tone="success" /></template></el-table-column>
        <el-table-column :label="activeTab === 'done' ? '处理时间' : '发起时间'" width="150"><template #default="scope">{{ formatDateOnly(scope.row.created_at) }}</template></el-table-column>
        <el-table-column label="操作" width="112" fixed="right"><template #default="scope"><el-button link type="primary" @click="openBusiness(scope.row)"><el-icon><View /></el-icon>查看详情</el-button></template></el-table-column>
        <template #footer><div class="task-center-footer"><span>共 {{ total }} 条记录</span><UiPagination :page="page" :page-size="pageSize" :total="total" @update:page="changePage" @update:page-size="changePageSize" /></div></template>
      </UiDataTable>

      <div class="task-center-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id">
          <header>
            <div><strong>{{ row.business_title || row.business_key }}</strong><small>{{ row.business_key }}</small></div>
            <UiStatusTag v-if="activeTab === 'pending'" :value="pendingStatus(row)" :labels="{ PENDING: '待处理', SENT: '已抄送' }" tone="primary" />
            <UiStatusTag
              v-else-if="activeTab === 'submitted'"
              :value="['APPROVED','COMPLETED','DONE'].includes(submittedStatus(row as WorkflowSubmittedItem)) ? '已完成' : (submittedStatus(row as WorkflowSubmittedItem) === 'TERMINATED' ? '已终止' : (['REJECTED','RETURNED'].includes(submittedStatus(row as WorkflowSubmittedItem)) ? '已驳回' : '审批中'))"
              tone="primary" />
            <UiStatusTag v-else :value="completedAction(row)" tone="success" />
          </header>
          <dl>
            <div><dt>项目</dt><dd>{{ row.project_name || '未关联项目' }}</dd></div>
            <div><dt>节点</dt><dd>{{ row.node_name || row.task_key || (row as WorkflowSubmittedItem).definition_name || '-' }}</dd></div>
            <div v-if="activeTab === 'submitted' && (row as WorkflowSubmittedItem).current_assignees"><dt>当前审批人</dt><dd class="task-center-assignees">{{ (row as WorkflowSubmittedItem).current_assignees }}</dd></div>
            <div><dt>{{ activeTab === 'done' ? '处理时间' : '发起时间' }}</dt><dd>{{ formatDateOnly(row.created_at) }}</dd></div>
          </dl>
          <footer><el-button link type="primary" @click="openBusiness(row)"><el-icon><View /></el-icon>查看详情</el-button></footer>
        </article>
        <UiEmptyState v-if="!loading && !rows.length">
          <template #title>
            <template v-if="activeTab === 'pending'">暂无待办任务</template>
            <template v-else-if="activeTab === 'submitted'">暂无我发起的流程</template>
            <template v-else>暂无已办记录</template>
          </template>
          <template v-if="activeTab === 'pending'" #subtitle>
            若您刚刚提交了审批，请切换到「我发起的」Tab 查看流程当前所在的审批人。
          </template>
        </UiEmptyState>
        <div v-if="total > 0" class="task-center-mobile-footer"><UiPagination :page="page" :page-size="pageSize" :total="total" @update:page="changePage" @update:page-size="changePageSize" /></div>
      </div>
    </template>
  </main>
</template>

<style scoped>
.task-center-assignees {
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
  margin-bottom: 4px;
  word-break: break-all;
}
</style>
