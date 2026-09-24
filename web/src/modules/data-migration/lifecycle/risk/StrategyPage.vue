<!--
  用途：风险策略库（基线 14.2.4）
  说明：策略沉淀与智能匹配；一键复用回填目标风险；普通用户只读，维护仅管理员；禁删仅可下线。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Plus, Refresh, Search } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import { createLifecycleRiskStrategy, listLifecycleRiskStrategies, offlineLifecycleRiskStrategy, reuseLifecycleRiskStrategy } from '../../../../api/data-migration-lifecycle'
import type { LifecycleRiskStrategyLibView } from '../../../../types/data-migration-lifecycle'

const auth = useAuthStore()
const isAdmin = computed(() => auth.hasPermission('system:admin') || auth.hasPermission('data-migration:manage'))

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')
const records = ref<LifecycleRiskStrategyLibView[]>([])
const filters = reactive<Record<string, unknown>>({ keyword: '', status: 'ACTIVE' })

async function loadList() {
  if (!auth.hasPermission('data-migration-lifecycle:risk') && !auth.hasPermission('data-migration:access') && !auth.hasPermission('system:admin')) {
    forbidden.value = true
    return
  }
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, unknown> = {}
    Object.entries(filters).forEach(([k, v]) => { if (v !== '' && v !== undefined && v !== null) params[k] = v })
    const res = await listLifecycleRiskStrategies(params)
    records.value = res.data.data ?? []
  } catch (e) {
    error.value = apiErrorMessage(e, '策略库加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, { keyword: '', status: 'ACTIVE' })
  loadList()
}

// ===== 新增 =====
const createOpen = ref(false)
const saving = ref(false)
const form = reactive<Record<string, unknown>>({ strategyTitle: '', riskTitlePattern: '', matchKeywords: '', riskLevel: undefined, probability: undefined, prePreventMeasure: '', responseStrategy: '', degradePlan: '', emergencyPlan: '' })
function openCreate() {
  Object.assign(form, { strategyTitle: '', riskTitlePattern: '', matchKeywords: '', riskLevel: undefined, probability: undefined, prePreventMeasure: '', responseStrategy: '', degradePlan: '', emergencyPlan: '' })
  createOpen.value = true
}
async function submitCreate() {
  if (!String(form.strategyTitle || '').trim()) { ElMessage.warning('策略标题必填'); return }
  if (!String(form.prePreventMeasure || '').trim()) { ElMessage.warning('前置防控措施必填'); return }
  saving.value = true
  try {
    const body: Record<string, unknown> = { ...form }
    body.matchKeywords = String(form.matchKeywords || '').split(/[,，]/).map(s => s.trim()).filter(Boolean)
    await createLifecycleRiskStrategy(body)
    ElMessage.success('策略已沉淀入库')
    createOpen.value = false
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '策略新增失败'))
  } finally { saving.value = false }
}

// ===== 一键复用 =====
const reuseOpen = ref(false)
const reuseForm = reactive<Record<string, unknown>>({ strategyId: undefined, riskId: undefined })
function openReuse(row: LifecycleRiskStrategyLibView) {
  reuseForm.strategyId = row.id
  reuseForm.riskId = undefined
  reuseOpen.value = true
}
async function submitReuse() {
  if (!reuseForm.riskId) { ElMessage.warning('请填写目标风险 ID'); return }
  saving.value = true
  try {
    await reuseLifecycleRiskStrategy(Number(reuseForm.strategyId), { riskId: Number(reuseForm.riskId) })
    ElMessage.success('策略已一键复用回填目标风险')
    reuseOpen.value = false
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '复用失败'))
  } finally { saving.value = false }
}

async function offline(row: LifecycleRiskStrategyLibView) {
  try {
    await offlineLifecycleRiskStrategy(row.id)
    ElMessage.success('策略已下线（禁删仅可下线）')
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
      <template #extra><span class="muted">策略沉淀 → 智能匹配 → 一键复用；维护仅管理员；普通用户只读</span></template>
      <template #actions>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">新增策略</el-button>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </template>
    </UiToolbar>

    <el-card shadow="never" class="ui-surface-card">
      <el-form inline class="dm-filter-bar">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="策略标题/风险模式检索" style="width: 220px" @keyup.enter="loadList" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
            <el-option value="ACTIVE" label="启用" /><el-option value="INVALID" label="已下线" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">检索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="forbidden" class="dm-state-panel"><UiEmptyState title="无权限访问策略库" /></div>
      <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
      <template v-else>
        <div class="dm-desktop-table">
          <UiDataTable :loading="loading" :data="records" empty-text="暂无策略">
            <el-table-column prop="strategyCode" label="策略编码" width="150" show-overflow-tooltip />
            <el-table-column prop="strategyTitle" label="策略标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="riskTitlePattern" label="风险标题模式" min-width="150" show-overflow-tooltip />
            <el-table-column label="等级/概率" width="120">
              <template #default="{ row }">
                <span class="muted">{{ row.riskLevel ?? '—' }} / {{ row.probability ?? '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="prePreventMeasure" label="前置防控措施" min-width="200" show-overflow-tooltip />
            <el-table-column label="复用次数" width="90">
              <template #default="{ row }"><el-tag size="small" type="warning">{{ row.useCount }}</el-tag></template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '已下线' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'ACTIVE'" link type="warning" :icon="CopyDocument" @click="openReuse(row)">一键复用</el-button>
                <el-button v-if="isAdmin && row.status === 'ACTIVE'" link type="danger" @click="offline(row)">下线</el-button>
              </template>
            </el-table-column>
          </UiDataTable>
        </div>
        <div v-if="records.length === 0 && !loading" class="dm-state-panel"><UiEmptyState title="暂无策略" description="管理员沉淀同类风险应对策略后，新风险上报可智能匹配并一键复用" /></div>
      </template>
    </el-card>

    <UiFormDrawer v-model="createOpen" title="新增策略" :loading="saving" width="600px" @submit="submitCreate">
      <el-form label-width="130px">
        <el-form-item label="策略标题" required><el-input v-model="form.strategyTitle" maxlength="200" /></el-form-item>
        <el-form-item label="风险标题模式"><el-input v-model="form.riskTitlePattern" maxlength="255" placeholder="如：数据割接失败" /></el-form-item>
        <el-form-item label="匹配关键词"><el-input v-model="form.matchKeywords" placeholder="逗号分隔，如：割接,回退,双写" /></el-form-item>
        <el-form-item label="等级/概率">
          <el-select v-model="form.riskLevel" clearable placeholder="等级" style="width: 110px"><el-option value="HIGH" label="高" /><el-option value="MEDIUM" label="中" /><el-option value="LOW" label="低" /></el-select>
          <el-select v-model="form.probability" clearable placeholder="概率" style="width: 110px; margin-left: 8px"><el-option value="HIGH" label="高" /><el-option value="MEDIUM" label="中" /><el-option value="LOW" label="低" /></el-select>
        </el-form-item>
        <el-form-item label="前置防控措施" required><el-input v-model="form.prePreventMeasure" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="标准化应对策略"><el-input v-model="form.responseStrategy" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="降级方案"><el-input v-model="form.degradePlan" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="应急处置预案"><el-input v-model="form.emergencyPlan" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <el-dialog v-model="reuseOpen" title="策略一键复用" width="460px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="策略"><span class="muted">#{{ reuseForm.strategyId }}</span></el-form-item>
        <el-form-item label="目标风险 ID" required><el-input-number v-model="reuseForm.riskId" :min="1" style="width: 100%" /></el-form-item>
        <p class="muted">复用后将策略字段回填目标风险并留痕，策略库复用次数 +1。</p>
      </el-form>
      <template #footer>
        <el-button @click="reuseOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReuse">确认复用</el-button>
      </template>
    </el-dialog>
  </section>
</template>
