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
import { listSubsystemChangeApplications } from './api'
import type { SubsystemActionType, SubsystemApplicationStatus, SubsystemChangeApplicationSummary, SubsystemTargetKind } from './types'
import {
  actionTypeLabels,
  applicationStatusLabels,
  applicationStatusTone,
  canEditApplication,
  formatDateTime,
  httpStatus,
  targetKindLabels
} from './utils'
import './architecture.css'

const auth = useAuthStore()
const router = useRouter()
const rows = ref<SubsystemChangeApplicationSummary[]>([])
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const filters = reactive({ status: '' as SubsystemApplicationStatus | '' })
let sequence = 0

const canView = computed(() => ['architecture:view', 'architecture:apply', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canApply = computed(() => auth.hasPermission('architecture:apply') || auth.hasPermission('architecture:manage'))
const canManage = computed(() => auth.hasPermission('architecture:manage'))
const hasNext = computed(() => rows.value.length === pageSize.value)
const scopeLabel = computed(() => canManage.value ? '当前租户全部申请' : '仅显示本人申请')
const statusOptions: SubsystemApplicationStatus[] = ['DRAFT', 'IN_REVIEW', 'RETURNED', 'APPROVED', 'REJECTED', 'CANCELLED']

function owns(row: SubsystemChangeApplicationSummary) {
  return row.applicantId === auth.user?.id
}

function targetKindLabel(kind: SubsystemTargetKind) {
  return targetKindLabels[kind]
}

function actionTypeLabel(action: SubsystemActionType) {
  return actionTypeLabels[action]
}

async function load() {
  if (!canView.value) return
  const request = ++sequence
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listSubsystemChangeApplications({
      status: filters.status,
      limit: pageSize.value,
      offset: (page.value - 1) * pageSize.value
    })
    if (request === sequence) rows.value = result
  } catch (error) {
    if (request !== sequence) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '变更工单加载失败')
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

async function refresh() {
  await load()
  if (!loadError.value && !forbidden.value) ElMessage.success('工单列表已刷新')
}

function create(kind: SubsystemTargetKind) {
  void router.push({ name: 'architecture-subsystem-change-application-new', query: { targetKind: kind } })
}

function detail(row: SubsystemChangeApplicationSummary) {
  void router.push({ name: 'architecture-subsystem-change-application-detail', params: { id: row.id } })
}

function edit(row: SubsystemChangeApplicationSummary) {
  void router.push({ name: 'architecture-subsystem-change-application-edit', params: { id: row.id } })
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
    <UiPageHeader title="架构子系统变更工单" description="所有新增、变更、下线、重新启用、作废和归属替换都在审批流程中完成。">
      <template #actions>
        <div v-if="canApply" class="architecture-page__actions">
          <el-button @click="create('PHYSICAL')"><el-icon><Plus /></el-icon>申请物理子系统</el-button>
          <el-button type="primary" @click="create('LOGICAL')"><el-icon><Plus /></el-icon>申请逻辑子系统</el-button>
        </div>
      </template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无工单查看权限" sub-title="请申请 architecture:view、architecture:apply 或 architecture:manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="工单列表加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
      </el-result>
    </section>
    <template v-else>
      <div class="architecture-change-scope-note">
        <span>{{ scopeLabel }}</span>
        <small v-if="canManage">管理权限包含申请与审批，但草稿编辑、提交和取消仍仅限申请人本人。</small>
      </div>
      <UiToolbar>
        <el-select v-model="filters.status" clearable placeholder="工单状态" class="architecture-filter-select" @change="search">
          <el-option v-for="status in statusOptions" :key="status" :label="applicationStatusLabels[status]" :value="status" />
        </el-select>
        <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
        <el-button @click="reset">重置</el-button>
        <template #actions>
          <el-tooltip content="刷新列表">
            <el-button circle :loading="loading" aria-label="刷新变更工单列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button>
          </el-tooltip>
        </template>
      </UiToolbar>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="工单" min-width="170">
          <template #default="scope">
            <button type="button" class="architecture-table-identity" @click="detail(scope.row)">
              <strong>#{{ scope.row.id }} · {{ targetKindLabel(scope.row.targetKind) }}</strong>
              <small>{{ actionTypeLabel(scope.row.actionType) }} · 第 {{ scope.row.currentBusinessRound }} 轮</small>
            </button>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <UiStatusTag :value="scope.row.status" :labels="applicationStatusLabels" :tone="applicationStatusTone(scope.row.status)" />
          </template>
        </el-table-column>
        <el-table-column label="申请人" width="100"><template #default="scope">#{{ scope.row.applicantId }}</template></el-table-column>
        <el-table-column label="最后更新" width="150"><template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="detail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button v-if="canApply && owns(scope.row) && canEditApplication(scope.row.status)" link type="primary" @click="edit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id">
          <header>
            <div><strong>#{{ row.id }} · {{ targetKindLabel(row.targetKind) }}</strong><small>{{ actionTypeLabel(row.actionType) }} · 第 {{ row.currentBusinessRound }} 轮</small></div>
            <UiStatusTag :value="row.status" :labels="applicationStatusLabels" :tone="applicationStatusTone(row.status)" />
          </header>
          <p class="architecture-mobile-card__reason">{{ row.reason }}</p>
          <dl>
            <div><dt>申请人</dt><dd>#{{ row.applicantId }}</dd></div>
            <div><dt>最后更新</dt><dd>{{ formatDateTime(row.updatedAt) }}</dd></div>
          </dl>
          <footer>
            <el-button link type="primary" @click="detail(row)"><el-icon><View /></el-icon>详情</el-button>
            <el-button v-if="canApply && owns(row) && canEditApplication(row.status)" link type="primary" @click="edit(row)"><el-icon><Edit /></el-icon>编辑</el-button>
          </footer>
        </article>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" title="暂无变更工单" description="当前筛选下没有记录，可发起逻辑或物理子系统申请。">
        <template #action><el-button v-if="canApply" type="primary" @click="create('LOGICAL')">发起申请</el-button><el-button v-else @click="reset">清空筛选</el-button></template>
      </UiEmptyState>

      <nav v-if="rows.length || page > 1" class="architecture-change-pagination" aria-label="工单分页">
        <span>第 {{ page }} 页</span>
        <div><el-button :disabled="page <= 1 || loading" @click="previous">上一页</el-button><el-button :disabled="!hasNext || loading" @click="next">下一页</el-button></div>
      </nav>
    </template>
  </main>
</template>
