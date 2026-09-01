<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { apiErrorMessage } from '../../api/error'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { listDecisionMatters, listDecisionTypes } from './api'
import type { DecisionMatterStatus, DecisionMatterSummary, StandardCategory } from './types'
import { formatDateTime, httpStatus } from './utils'
import './architecture.css'

const auth = useAuthStore()
const router = useRouter()
const rows = ref<DecisionMatterSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const types = ref<StandardCategory[]>([])
const filters = reactive({ keyword: '', typeCode: '', status: '' as DecisionMatterStatus | '', overdue: false })

const canView = computed(() => ['architecture:decision:view', 'architecture:decision:propose', 'architecture:decision:review', 'architecture:decision:manage'].some(p => auth.hasPermission(p)))
const canPropose = computed(() => ['architecture:decision:propose', 'architecture:decision:review', 'architecture:decision:manage'].some(p => auth.hasPermission(p)))

const statusLabels: Record<DecisionMatterStatus, string> = {
  SUBMITTED: '待首次处理',
  RETURNED_FOR_INFO: '要求补充',
  IN_REVIEW: '评审中',
  PUBLISHED: '已完成'
}

function statusTone(status: DecisionMatterStatus) {
  if (status === 'PUBLISHED') return 'success' as const
  if (status === 'RETURNED_FOR_INFO') return 'danger' as const
  if (status === 'IN_REVIEW') return 'warning' as const
  return 'info' as const
}

function typeLabel(code: string | null) {
  return code ? types.value.find(item => item.code === code)?.label || code : '待分类'
}

async function load() {
  if (!canView.value) return
  loading.value = true
  loadError.value = ''
  try {
    const result = await listDecisionMatters({
      page: page.value, size: pageSize.value,
      keyword: filters.keyword || undefined,
      typeCode: filters.typeCode || undefined,
      status: filters.status || undefined,
      firstHandlingOverdue: filters.overdue || undefined
    })
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    if (httpStatus(error) === 403) { forbidden.value = true } else { loadError.value = apiErrorMessage(error, '加载失败') }
  } finally {
    loading.value = false
  }
}

async function loadTypes() {
  try {
    types.value = await listDecisionTypes()
  } catch { /* 类型加载失败不阻断列表 */ }
}

function resetFilters() {
  filters.keyword = ''
  filters.typeCode = ''
  filters.status = ''
  filters.overdue = false
  page.value = 1
  load()
}

watch([page, pageSize], load)
onMounted(async () => {
  await Promise.all([loadTypes(), load()])
})
</script>

<template>
  <div class="architecture-page">
    <UiPageHeader eyebrow="ARCHITECTURE" title="架构决策" description="围绕一事一议的架构决策事项，从提交、首次处理与评审推进到正式结论发布与替代链追溯。">
      <template #actions>
        <el-button v-if="canPropose" type="primary" :icon="Plus" @click="router.push('/architecture/decisions/new')">提交事项</el-button>
      </template>
    </UiPageHeader>

    <UiToolbar>
      <template #filters>
        <el-input v-model="filters.keyword" placeholder="搜索编号或标题" clearable style="width:220px" @keyup.enter="page = 1; load()" />
        <el-select v-model="filters.typeCode" placeholder="事项类型" clearable style="width:160px">
          <el-option v-for="type in types" :key="type.code" :label="type.label" :value="type.code" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width:150px">
          <el-option label="待首次处理" value="SUBMITTED" />
          <el-option label="要求补充" value="RETURNED_FOR_INFO" />
          <el-option label="评审中" value="IN_REVIEW" />
          <el-option label="已完成" value="PUBLISHED" />
        </el-select>
        <el-checkbox v-model="filters.overdue">仅看首次处理逾期</el-checkbox>
        <el-button :icon="Search" @click="page = 1; load()">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </template>
    </UiToolbar>

    <UiEmptyState v-if="forbidden" title="无访问权限" description="当前账号缺少架构决策查看权限，请联系管理员授权。">
      <template #action><el-button @click="router.push('/dashboard')">返回首页</el-button></template>
    </UiEmptyState>

    <UiEmptyState v-else-if="loadError" title="加载失败" :description="loadError">
      <template #action><el-button :icon="Refresh" @click="load">重试</el-button></template>
    </UiEmptyState>

    <UiDataTable v-else-if="canView" :data="rows" :loading="loading" :empty-text="'暂无架构决策事项'">
      <el-table-column label="事项编号" width="150">
        <template #default="{ row }"><a class="standard-title-link" @click="router.push(`/architecture/decisions/${row.id}`)">{{ row.matterNo }}</a></template>
      </el-table-column>
      <el-table-column label="标题" min-width="220">
        <template #default="{ row }">{{ row.title }}</template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <UiStatusTag v-if="row.typeCode" :value="row.typeCode" :labels="Object.fromEntries(types.map(t => [t.code, t.label]))" tone="info" />
          <span v-else class="standard-muted">待分类</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }"><UiStatusTag :value="row.status" :labels="statusLabels" :tone="statusTone(row.status)" /></template>
      </el-table-column>
      <el-table-column label="首次处理期限" width="120">
        <template #default="{ row }">
          <span :class="{ 'standard-overdue': row.firstHandlingOverdue }">{{ row.firstHandlingDeadline }}</span>
          <UiStatusTag v-if="row.firstHandlingOverdue" value="已逾期" tone="danger" />
        </template>
      </el-table-column>
      <el-table-column label="评审方式" width="100">
        <template #default="{ row }">{{ row.reviewMode === 'ASYNC' ? '异步' : row.reviewMode === 'MEETING' ? '会议' : '—' }}</template>
      </el-table-column>
      <el-table-column label="提出人" width="120">
        <template #default="{ row }">{{ row.proposerName }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="router.push(`/architecture/decisions/${row.id}`)">详情</el-button>
        </template>
      </el-table-column>
      <template #footer>
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                       layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" />
      </template>
    </UiDataTable>
  </div>
</template>
