<!--
  用途：历史问题知识库（基线 13.2.5）
  说明：三元组（问题→解决方案→复用次数）；全员可检索查看；编辑/合并/下线仅管理员；
        已失效条目禁止删除仅标注 INVALID。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Refresh, Search, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import { invalidateLifecycleKnowledge, listLifecycleKnowledge, referLifecycleKnowledge } from '../../../../api/data-migration-lifecycle'
import type { LifecycleKnowledgeEntryView } from '../../../../types/data-migration-lifecycle'

const auth = useAuthStore()
const isAdmin = computed(() => auth.hasPermission('system:admin') || auth.hasPermission('data-migration:manage'))

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')
const records = ref<LifecycleKnowledgeEntryView[]>([])
const filters = reactive<Record<string, unknown>>({ keyword: '', status: 'ACTIVE' })

async function loadList() {
  if (!auth.hasPermission('data-migration-lifecycle:issue') && !auth.hasPermission('data-migration:access') && !auth.hasPermission('system:admin')) {
    forbidden.value = true
    return
  }
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, unknown> = {}
    Object.entries(filters).forEach(([k, v]) => { if (v !== '' && v !== undefined && v !== null) params[k] = v })
    const res = await listLifecycleKnowledge(params)
    records.value = res.data.data ?? []
  } catch (e) {
    error.value = apiErrorMessage(e, '知识库加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, { keyword: '', status: 'ACTIVE' })
  loadList()
}

const detailOpen = ref(false)
const current = ref<LifecycleKnowledgeEntryView | null>(null)
function openDetail(row: LifecycleKnowledgeEntryView) {
  current.value = row
  detailOpen.value = true
}

async function refer(row: LifecycleKnowledgeEntryView) {
  try {
    await referLifecycleKnowledge(row.id, {})
    ElMessage.success('已引用历史解决方案，复用次数 +1')
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '引用失败'))
  }
}

async function invalidateRow(row: LifecycleKnowledgeEntryView) {
  try {
    await invalidateLifecycleKnowledge(row.id)
    ElMessage.success('知识条目已标注失效（禁止物理删除）')
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '下线失败'))
  }
}

onMounted(loadList)
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra><span class="muted">三元组知识模型：问题 → 解决方案 → 复用次数；闭环问题自动沉淀；全员可检索</span></template>
      <template #actions>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </template>
    </UiToolbar>

    <el-card shadow="never" class="ui-surface-card">
      <el-form inline class="dm-filter-bar">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="问题描述/解决方案检索" style="width: 220px" @keyup.enter="loadList" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
            <el-option value="ACTIVE" label="有效" /><el-option value="INVALID" label="已失效" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">检索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="forbidden" class="dm-state-panel"><UiEmptyState title="无权限访问知识库" /></div>
      <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
      <template v-else>
        <div class="dm-desktop-table">
          <UiDataTable :loading="loading" :data="records" empty-text="暂无知识条目">
            <el-table-column prop="knowledgeCode" label="条目编码" width="150" show-overflow-tooltip />
            <el-table-column prop="problemDesc" label="问题描述" min-width="220" show-overflow-tooltip />
            <el-table-column prop="solution" label="解决方案" min-width="220" show-overflow-tooltip />
            <el-table-column label="复用次数" width="100">
              <template #default="{ row }"><el-tag size="small" type="warning">{{ row.reuseCount }}</el-tag></template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.knowledgeStatus === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.knowledgeStatus === 'ACTIVE' ? '有效' : '已失效' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
                <el-button v-if="row.knowledgeStatus === 'ACTIVE'" link type="warning" :icon="CopyDocument" @click="refer(row)">复用</el-button>
                <el-button v-if="isAdmin && row.knowledgeStatus === 'ACTIVE'" link type="danger" @click="invalidateRow(row)">失效</el-button>
              </template>
            </el-table-column>
          </UiDataTable>
        </div>
        <div v-if="records.length === 0 && !loading" class="dm-state-panel"><UiEmptyState title="暂无知识条目" description="问题核验闭环后将自动沉淀；也可由管理员手动补录" /></div>
      </template>
    </el-card>

    <el-dialog v-model="detailOpen" title="知识条目详情" width="640px" destroy-on-close>
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="条目编码">{{ current.knowledgeCode }}</el-descriptions-item>
        <el-descriptions-item label="问题描述">{{ current.problemDesc }}</el-descriptions-item>
        <el-descriptions-item label="解决方案">{{ current.solution }}</el-descriptions-item>
        <el-descriptions-item label="整改过程记录">{{ (current.rectifyRecords ?? []).join('；') || '—' }}</el-descriptions-item>
        <el-descriptions-item label="复用次数">{{ current.reuseCount }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ current.knowledgeStatus === 'ACTIVE' ? '有效' : '已失效' }}</el-descriptions-item>
        <el-descriptions-item label="来源问题">{{ current.sourceIssueId ?? '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </section>
</template>
