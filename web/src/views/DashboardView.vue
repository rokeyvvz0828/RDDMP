<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, Check, Clock, Connection, Lock, Refresh, UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useProjectContextStore } from '../stores/project-context'
import { listWorkflowDone, listWorkflowInbox, type WorkflowDoneItem, type WorkflowTask } from '../api/workflow'
import { apiErrorMessage } from '../api/error'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import { subscribeToPageActivation, subscribeToWorkflowTaskChanges } from '../utils/workflow-task-events'

const router = useRouter()
const auth = useAuthStore()
const projectContext = useProjectContextStore()
const inbox = ref<WorkflowTask[]>([])
const done = ref<WorkflowDoneItem[]>([])
const loading = ref(false)
const forbidden = ref(false)
const errorMessage = ref('')
let unsubscribeTaskChanges: (() => void) | undefined
let unsubscribePageActivation: (() => void) | undefined
let taskRequestId = 0
const currentHour = new Date().getHours()
const greeting = computed(() => currentHour < 12 ? '早上好' : currentHour < 18 ? '下午好' : '晚上好')
const projectInbox = computed(() => inbox.value.filter(item => !item.project_ref || item.project_ref === projectContext.currentRef))
const projectDone = computed(() => done.value.filter(item => !item.project_ref || item.project_ref === projectContext.currentRef))

async function loadTasks() {
  const requestId = ++taskRequestId
  loading.value = true
  forbidden.value = false
  errorMessage.value = ''
  try {
    const [inboxResponse, doneResponse] = await Promise.all([listWorkflowInbox({ page: 1, size: 5 }), listWorkflowDone({ page: 1, size: 5 })])
    if (requestId !== taskRequestId) return
    inbox.value = inboxResponse.data.data.records || []
    done.value = doneResponse.data.data.records || []
  } catch (error: unknown) {
    if (requestId !== taskRequestId) return
    const status = (error as { response?: { status?: number } })?.response?.status
    forbidden.value = status === 403
    errorMessage.value = apiErrorMessage(error, '工作流任务加载失败')
  } finally {
    if (requestId === taskRequestId) loading.value = false
  }
}

function openBusiness(item: WorkflowTask | WorkflowDoneItem) {
  const doneItem = 'action_code' in item
  const fallback = { path: '/workbench/tasks', query: { tab: doneItem ? 'done' : 'pending' } }
  const path = item.action_path
  if (!path || !path.startsWith('/') || path.startsWith('//') || /[\\\r\n]/.test(path)) {
    void router.push(fallback)
    return
  }
  try {
    const decoded = decodeURIComponent(path)
    if (!decoded.startsWith('/') || decoded.startsWith('//') || /[\\\r\n]/.test(decoded)) {
      void router.push(fallback)
      return
    }
    const resolved = router.resolve(path)
    void router.push({ path: resolved.path, query: { ...resolved.query, ...(doneItem ? { instanceId: item.instance_id } : { taskId: item.id }) }, hash: resolved.hash })
  } catch {
    void router.push(fallback)
  }
}

watch(() => projectContext.currentRef, () => { /* Project filtering is presentation-only. */ })
onMounted(async () => {
  unsubscribeTaskChanges = subscribeToWorkflowTaskChanges(() => { void loadTasks() })
  unsubscribePageActivation = subscribeToPageActivation(() => { void loadTasks() })
  await projectContext.initialize()
  await loadTasks()
})
onBeforeUnmount(() => {
  unsubscribeTaskChanges?.()
  unsubscribePageActivation?.()
})
</script>

<template>
  <div class="dashboard-page">
    <section class="page-intro">
      <div><span class="panel-kicker">工作台 / 01</span><h1>{{ greeting }}，{{ auth.user?.displayName || '管理员' }}</h1><p>{{ projectContext.current?.name || '当前项目' }}</p></div>
      <div class="status-pill"><span class="status-dot"></span>系统运行正常</div>
    </section>

    <section class="metric-grid">
      <el-card shadow="never" class="metric-card accent-blue"><div class="metric-top"><span>我的待办</span><el-icon><Clock /></el-icon></div><strong>{{ projectInbox.length }}</strong><small>当前项目工作流任务</small></el-card>
      <el-card shadow="never" class="metric-card accent-green"><div class="metric-top"><span>最近已办</span><el-icon><Check /></el-icon></div><strong>{{ projectDone.length }}</strong><small>最近处理记录</small></el-card>
      <el-card shadow="never" class="metric-card accent-orange"><div class="metric-top"><span>角色权限</span><el-icon><Lock /></el-icon></div><strong>{{ auth.user?.permissions?.length || 0 }}</strong><small>后端授权生效</small></el-card>
      <el-card shadow="never" class="metric-card accent-purple"><div class="metric-top"><span>当前账号</span><el-icon><UserFilled /></el-icon></div><strong>{{ auth.user?.displayName || '管理员' }}</strong><small>{{ auth.user?.username }}</small></el-card>
    </section>

    <el-alert v-if="forbidden" type="warning" :closable="false" show-icon title="当前账号没有工作流任务访问权限" />
    <el-alert v-else-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage"><el-button link type="primary" @click="loadTasks"><el-icon><Refresh /></el-icon>重试</el-button></el-alert>

    <section v-else v-loading="loading" class="dashboard-task-grid">
      <div class="dashboard-task-panel">
        <header><div><span class="panel-kicker">MY TASKS</span><h3>我的待办</h3></div><router-link :to="{ path: '/workbench/tasks', query: { tab: 'pending' } }">查看全部</router-link></header>
        <div v-if="projectInbox.length" class="dashboard-task-list">
          <button v-for="item in projectInbox" :key="item.id" type="button" @click="openBusiness(item)">
            <span class="dashboard-task-icon"><Clock /></span><span><strong>{{ item.business_title || item.business_key }}</strong><small>{{ item.project_name || '未关联项目' }} · {{ item.node_name || item.task_key }}</small></span><UiStatusTag :value="item.status" :labels="{ PENDING: '待处理', SENT: '已抄送' }" />
          </button>
        </div>
        <UiEmptyState v-else title="暂无待办任务" />
      </div>
      <div class="dashboard-task-panel">
        <header><div><span class="panel-kicker">RECENT DONE</span><h3>最近已办</h3></div><router-link :to="{ path: '/workbench/tasks', query: { tab: 'done' } }">查看全部</router-link></header>
        <div v-if="projectDone.length" class="dashboard-task-list">
          <button v-for="item in projectDone" :key="item.id" type="button" @click="openBusiness(item)">
            <span class="dashboard-task-icon is-done"><Check /></span><span><strong>{{ item.business_title || item.business_key }}</strong><small>{{ item.project_name || '未关联项目' }} · {{ item.node_name || item.definition_name || '-' }}</small></span><UiStatusTag :value="item.action_code" :labels="{ APPROVE: '已同意', REJECT: '已拒绝', RETURN: '已退回' }" />
          </button>
        </div>
        <UiEmptyState v-else title="暂无已办记录" />
      </div>
    </section>

    <section class="dashboard-grid">
      <el-card shadow="never" class="surface-card activity-card"><template #header><div class="card-heading"><div><span class="panel-kicker">系统状态</span><h3>平台状态</h3></div><span class="muted">实时</span></div></template><div class="signal-row"><span class="signal-icon blue"><Connection /></span><div><strong>认证服务</strong><p>当前登录会话有效</p></div><span class="signal-ok">在线</span></div><div class="signal-row"><span class="signal-icon green"><Calendar /></span><div><strong>项目上下文</strong><p>{{ projectContext.current?.shortName || '加载中' }}</p></div><span class="signal-ok">已接入</span></div></el-card>
    </section>
  </div>
</template>
