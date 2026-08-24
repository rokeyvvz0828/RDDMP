<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import { listNetworkWorkOrders } from './network'
import type { NetworkWorkOrderKind, NetworkWorkOrderStatus, NetworkWorkOrderSummary } from './networkTypes'
import {
  handlingResultLabels,
  networkActionLabel,
  networkKindLabels,
  networkStatusLabels,
  networkStatusTone,
  canEditNetworkWorkOrder
} from './networkUtils'
import { formatDateTime, httpStatus } from './utils'
import './architecture.css'

const auth = useAuthStore()
const router = useRouter()
const rows = ref<NetworkWorkOrderSummary[]>([])
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const activeKind = ref<NetworkWorkOrderKind | ''>('')
const filters = reactive({ status: '' as NetworkWorkOrderStatus | '' })
let sequence = 0

const canView = computed(() => ['architecture:network-work-order:view', 'architecture:network-work-order:apply', 'architecture:network-work-order:manage'].some(permission => auth.hasPermission(permission)))
const canApply = computed(() => auth.hasPermission('architecture:network-work-order:apply') || auth.hasPermission('architecture:network-work-order:manage'))
const canManage = computed(() => auth.hasPermission('architecture:network-work-order:manage'))
const hasNext = computed(() => rows.value.length === pageSize.value)
const scopeLabel = computed(() => canManage.value ? '当前租户全部工单' : '仅显示本人发起的工单')
const statusOptions: NetworkWorkOrderStatus[] = ['DRAFT', 'IN_REVIEW', 'RETURNED', 'COMPLETED', 'REJECTED', 'CANCELLED']
const kindTabs: { value: NetworkWorkOrderKind | ''; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'CLB', label: 'CLB' },
  { value: 'DNS', label: 'DNS' },
  { value: 'CERT', label: '证书' }
]

function owns(row: NetworkWorkOrderSummary) {
  return row.applicantId === auth.user?.id
}

function kindLabel(kind: NetworkWorkOrderKind) {
  return networkKindLabels[kind]
}

function actionLabel(kind: NetworkWorkOrderKind, action: NetworkWorkOrderSummary['actionType']) {
  return networkActionLabel(kind, action)
}

function resultLabel(status: NetworkWorkOrderSummary['resultStatus']) {
  return status ? handlingResultLabels[status] : '—'
}

async function load() {
  if (!canView.value) return
  const request = ++sequence
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listNetworkWorkOrders({
      kind: activeKind.value,
      status: filters.status,
      limit: pageSize.value,
      offset: (page.value - 1) * pageSize.value
    })
    if (request === sequence) rows.value = result
  } catch (error) {
    if (request !== sequence) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '工单列表加载失败')
  } finally {
    if (request === sequence) loading.value = false
  }
}

function search() {
  page.value = 1
  void load()
}

function reset() {
  filters.status = ''
  page.value = 1
  void load()
}

function switchKind(kind: NetworkWorkOrderKind | '') {
  activeKind.value = kind
  page.value = 1
  void load()
}

async function refresh() {
  await load()
  if (!loadError.value && !forbidden.value) ElMessage.success('工单列表已刷新')
}

function create(kind: NetworkWorkOrderKind) {
  void router.push({ name: 'architecture-network-work-order-new', query: { kind } })
}

function detail(row: NetworkWorkOrderSummary) {
  void router.push({ name: 'architecture-network-work-order-detail', params: { id: row.id } })
}

function edit(row: NetworkWorkOrderSummary) {
  void router.push({ name: 'architecture-network-work-order-edit', params: { id: row.id } })
}

function previous() {
  if (page.value <= 1) return
  page.value -= 1
  void load()
}

function next() {
  if (!hasNext.value) return
  page.value += 1
  void load()
}

watch(canView, allowed => {
  if (allowed) void load()
}, { immediate: true })
</script>

<template>
  <main class="architecture-page architecture-change-page">
    <UiPageHeader title="网络专项工单" description="CLB 开通/调整、DNS 新增/变更/注销、证书申请/续期/吊销的申请与办理结果登记，实际动作在线下或外部系统完成。">
      <template #actions>
        <div v-if="canApply" class="architecture-page__actions">
          <el-button @click="create('CLB')"><el-icon><Plus /></el-icon>申请 CLB</el-button>
          <el-button @click="create('DNS')"><el-icon><Plus /></el-icon>申请 DNS</el-button>
          <el-button type="primary" @click="create('CERT')"><el-icon><Plus /></el-icon>申请证书</el-button>
        </div>
      </template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无工单查看权限" sub-title="请申请 architecture:network-work-order:view、apply 或 manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="工单列表加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
      </el-result>
    </section>
    <template v-else>
      <div class="architecture-change-scope-note">
        <span>{{ scopeLabel }}</span>
        <small v-if="canManage">管理权限包含办理与审批，但草稿编辑、提交和取消仍仅限申请人本人。</small>
      </div>

      <el-tabs :model-value="activeKind" class="architecture-network-tabs" @tab-change="switchKind">
        <el-tab-pane v-for="tab in kindTabs" :key="tab.value" :label="tab.label" :name="tab.value" />
      </el-tabs>

      <UiToolbar>
        <el-select v-model="filters.status" clearable placeholder="工单状态" class="architecture-filter-select" @change="search">
          <el-option v-for="status in statusOptions" :key="status" :label="networkStatusLabels[status]" :value="status" />
        </el-select>
        <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
        <el-button @click="reset">重置</el-button>
        <template #actions>
          <el-tooltip content="刷新列表">
            <el-button circle :loading="loading" aria-label="刷新网络专项工单列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button>
          </el-tooltip>
        </template>
      </UiToolbar>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="工单" min-width="190">
          <template #default="scope">
            <button type="button" class="architecture-table-identity" @click="detail(scope.row)">
              <strong>#{{ scope.row.id }} · {{ kindLabel(scope.row.kind) }} {{ actionLabel(scope.row.kind, scope.row.actionType) }}</strong>
              <small>{{ scope.row.subject }}</small>
            </button>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <UiStatusTag :value="scope.row.status" :labels="networkStatusLabels" :tone="networkStatusTone(scope.row.status)" />
          </template>
        </el-table-column>
        <el-table-column label="办理结果" width="110">
          <template #default="scope">
            <span :class="{ 'architecture-inline-code': scope.row.resultStatus, 'architecture-muted': !scope.row.resultStatus }">{{ resultLabel(scope.row.resultStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="申请人" width="100"><template #default="scope">#{{ scope.row.applicantId }}</template></el-table-column>
        <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="detail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button v-if="canApply && owns(scope.row) && canEditNetworkWorkOrder(scope.row.status)" link type="primary" @click="edit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id">
          <header>
            <div>
              <strong>#{{ row.id }} · {{ kindLabel(row.kind) }} {{ actionLabel(row.kind, row.actionType) }}</strong>
              <small>{{ row.subject }}</small>
            </div>
            <UiStatusTag :value="row.status" :labels="networkStatusLabels" :tone="networkStatusTone(row.status)" />
          </header>
          <p class="architecture-mobile-card__reason">{{ row.reason || '未填写申请原因' }}</p>
          <dl>
            <div><dt>办理结果</dt><dd>{{ resultLabel(row.resultStatus) }}</dd></div>
            <div><dt>申请人</dt><dd>#{{ row.applicantId }}</dd></div>
            <div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div>
          </dl>
          <footer>
            <el-button link type="primary" @click="detail(row)"><el-icon><View /></el-icon>详情</el-button>
            <el-button v-if="canApply && owns(row) && canEditNetworkWorkOrder(row.status)" link type="primary" @click="edit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
          </footer>
        </article>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" title="暂无网络专项工单" description="当前筛选下没有记录，可发起 CLB、DNS 或证书申请。">
        <template #action>
          <el-button v-if="canApply" type="primary" @click="create('CERT')">发起申请</el-button>
          <el-button v-else @click="reset">清空筛选</el-button>
        </template>
      </UiEmptyState>

      <nav v-if="rows.length || page > 1" class="architecture-change-pagination" aria-label="工单分页">
        <span>第 {{ page }} 页</span>
        <div>
          <el-button :disabled="page <= 1 || loading" @click="previous">上一页</el-button>
          <el-button :disabled="!hasNext || loading" @click="next">下一页</el-button>
        </div>
      </nav>
    </template>
  </main>
</template>
