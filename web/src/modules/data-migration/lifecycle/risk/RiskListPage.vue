<!--
  用途：风险台账（基线第 14 章）
  说明：双渠道归集（手动/工单同步 reconcile）；等级与概率单一值；6 态互斥（已规避/已发生/已闭环任一时刻仅唯一）；
        闭环固化归档；普通用户不可作废。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  cancelLifecycleRisk,
  closeLifecycleRisk,
  createLifecycleRisk,
  listLifecycleRisks,
  preventLifecycleRisk,
  reconcileLifecycleRisks
} from '../../../../api/data-migration-lifecycle'
import type { LifecycleRiskView } from '../../../../types/data-migration-lifecycle'

const auth = useAuthStore()
const isAdmin = computed(() => auth.hasPermission('system:admin') || auth.hasPermission('data-migration:manage'))

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')
const records = ref<LifecycleRiskView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive<Record<string, unknown>>({ riskCode: '', riskStatus: '', riskLevel: '' })

const STATUS_TEXT: Record<string, string> = { WAIT_PREVENT: '待防控', PREVENTING: '防控中', AVOIDED: '已规避', OCCURRED: '已发生', CLOSED: '已闭环', CANCELLED: '已作废' }
const STATUS_TYPE: Record<string, string> = { WAIT_PREVENT: 'primary', PREVENTING: 'warning', AVOIDED: 'success', OCCURRED: 'danger', CLOSED: 'success', CANCELLED: 'info' }
const LEVEL_TEXT: Record<string, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const LEVEL_TYPE: Record<string, string> = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }

async function loadList() {
  if (!auth.hasPermission('data-migration-lifecycle:risk') && !auth.hasPermission('data-migration:access') && !auth.hasPermission('system:admin')) {
    forbidden.value = true
    return
  }
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    Object.entries(filters).forEach(([k, v]) => { if (v !== '' && v !== undefined && v !== null) params[k] = v })
    const res = await listLifecycleRisks(params)
    records.value = res.data.data?.records ?? []
    total.value = res.data.data?.total ?? 0
  } catch (e) {
    error.value = apiErrorMessage(e, '风险台账加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, { riskCode: '', riskStatus: '', riskLevel: '' })
  page.value = 1
  loadList()
}

// ===== 新增 =====
const createOpen = ref(false)
const saving = ref(false)
const form = reactive<Record<string, unknown>>({ riskTitle: '', riskDesc: '', impactScope: '', riskLevel: 'MEDIUM', probability: 'MEDIUM', potentialHarm: '', predictedScene: '' })
function openCreate() {
  Object.assign(form, { riskTitle: '', riskDesc: '', impactScope: '', riskLevel: 'MEDIUM', probability: 'MEDIUM', potentialHarm: '', predictedScene: '' })
  createOpen.value = true
}
async function submitCreate() {
  if (!String(form.riskTitle || '').trim()) { ElMessage.warning('风险标题必填'); return }
  if (!String(form.riskDesc || '').trim()) { ElMessage.warning('风险描述必填'); return }
  if (!String(form.impactScope || '').trim()) { ElMessage.warning('影响范围必填'); return }
  saving.value = true
  try {
    await createLifecycleRisk({ ...form })
    ElMessage.success('风险新增成功（等级/概率单一值已固化）')
    createOpen.value = false
    page.value = 1
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '新增失败'))
  } finally { saving.value = false }
}

// ===== 防控/闭环/作废 =====
const operateOpen = ref(false)
const operateMode = ref<'PREVENT' | 'CLOSE' | 'CANCEL'>('PREVENT')
const operateForm = reactive<Record<string, unknown>>({ riskId: undefined, preventProgress: '', record: '', finalStatus: 'CLOSED', reason: '' })
const currentRow = ref<LifecycleRiskView | null>(null)
function openOperate(row: LifecycleRiskView, mode: 'PREVENT' | 'CLOSE' | 'CANCEL') {
  currentRow.value = row
  operateMode.value = mode
  Object.assign(operateForm, { riskId: row.id, preventProgress: '', record: '', finalStatus: 'CLOSED', reason: '' })
  operateOpen.value = true
}
async function submitOperate() {
  const riskId = Number(operateForm.riskId)
  saving.value = true
  try {
    if (operateMode.value === 'PREVENT') {
      if (!String(operateForm.preventProgress || '').trim()) { ElMessage.warning('防控进度必填'); return }
      await preventLifecycleRisk(riskId, { preventProgress: operateForm.preventProgress, record: operateForm.record })
      ElMessage.success('防控进度已提交，风险进入防控中')
    } else if (operateMode.value === 'CLOSE') {
      await closeLifecycleRisk(riskId, { finalStatus: operateForm.finalStatus })
      ElMessage.success('风险已闭环/规避/发生固化归档（策略快照留痕）')
    } else {
      await cancelLifecycleRisk(riskId, { reason: operateForm.reason })
      ElMessage.success('风险已作废')
    }
    operateOpen.value = false
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '操作失败'))
  } finally { saving.value = false }
}

async function reconcile() {
  loading.value = true
  try {
    const res = await reconcileLifecycleRisks()
    ElMessage.success(`工单风险归集完成，新增 ${res.data.data?.synced ?? 0} 条（来源=工单同步）`)
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '对账归集失败'))
  } finally { loading.value = false }
}

onMounted(loadList)
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra><span class="muted">双渠道归集（手动/工单同步 reconcile）；等级/概率单一值；6 态互斥；闭环固化归档</span></template>
      <template #actions>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">新增风险</el-button>
        <el-button v-if="isAdmin" @click="reconcile">对账归集</el-button>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </template>
    </UiToolbar>

    <el-card shadow="never" class="ui-surface-card">
      <el-form inline class="dm-filter-bar">
        <el-form-item label="风险编号"><el-input v-model="filters.riskCode" clearable placeholder="RK-xxx" style="width: 150px" @keyup.enter="page = 1; loadList()" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.riskStatus" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="(label, code) in STATUS_TEXT" :key="code" :value="code" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="filters.riskLevel" clearable placeholder="全部" style="width: 110px">
            <el-option value="HIGH" label="高" /><el-option value="MEDIUM" label="中" /><el-option value="LOW" label="低" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page = 1; loadList()">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="forbidden" class="dm-state-panel"><UiEmptyState title="无权限查看风险台账" description="普通执行人仅可见本人上报/防控的风险" /></div>
      <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
      <template v-else>
        <div class="dm-desktop-table">
          <UiDataTable :loading="loading" :data="records" empty-text="暂无风险">
            <el-table-column prop="riskCode" label="风险编号" width="140" show-overflow-tooltip />
            <el-table-column prop="riskTitle" label="风险标题" min-width="180" show-overflow-tooltip />
            <el-table-column label="等级" width="80">
              <template #default="{ row }"><el-tag size="small" :type="LEVEL_TYPE[row.riskLevel] ?? 'primary'">{{ LEVEL_TEXT[row.riskLevel] ?? row.riskLevel }}</el-tag></template>
            </el-table-column>
            <el-table-column label="概率" width="80">
              <template #default="{ row }"><el-tag size="small">{{ LEVEL_TEXT[row.probability] ?? row.probability }}</el-tag></template>
            </el-table-column>
            <el-table-column label="来源" width="100">
              <template #default="{ row }"><el-tag size="small" :type="row.riskSource === 'ORDER_SYNC' ? 'warning' : 'info'">{{ row.riskSource === 'ORDER_SYNC' ? '工单同步' : '手动新增' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="STATUS_TYPE[row.riskStatus] ?? 'primary'" size="small">{{ STATUS_TEXT[row.riskStatus] ?? row.riskStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="上报时间" width="150">
              <template #default="{ row }">{{ row.reportedAt?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <template v-if="!(row.riskStatus === 'CLOSED' || row.riskStatus === 'CANCELLED')">
                  <el-button link type="warning" @click="openOperate(row, 'PREVENT')">防控</el-button>
                  <el-button v-if="isAdmin" link type="success" @click="openOperate(row, 'CLOSE')">闭环</el-button>
                  <el-button v-if="isAdmin" link type="danger" @click="openOperate(row, 'CANCEL')">作废</el-button>
                </template>
                <span v-else class="muted">已固化</span>
              </template>
            </el-table-column>
          </UiDataTable>
        </div>

        <div v-if="records.length === 0 && !loading" class="dm-state-panel"><UiEmptyState title="暂无风险" description="手动新增或触发工单对账归集" /></div>
        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <UiPagination v-model:page="page" v-model:page-size="size" :total="total" @update:page="loadList" @update:page-size="page = 1; loadList()" />
        </div>
      </template>
    </el-card>

    <UiFormDrawer v-model="createOpen" title="新增风险" :loading="saving" width="560px" @submit="submitCreate">
      <el-form label-width="100px">
        <el-form-item label="风险标题" required><el-input v-model="form.riskTitle" maxlength="200" /></el-form-item>
        <el-form-item label="风险描述" required><el-input v-model="form.riskDesc" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="影响范围" required><el-input v-model="form.impactScope" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="风险等级" required>
          <el-select v-model="form.riskLevel" style="width: 120px"><el-option value="HIGH" label="高" /><el-option value="MEDIUM" label="中" /><el-option value="LOW" label="低" /></el-select>
        </el-form-item>
        <el-form-item label="发生概率" required>
          <el-select v-model="form.probability" style="width: 120px"><el-option value="HIGH" label="高" /><el-option value="MEDIUM" label="中" /><el-option value="LOW" label="低" /></el-select>
        </el-form-item>
        <el-form-item label="潜在危害"><el-input v-model="form.potentialHarm" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="预判场景"><el-input v-model="form.predictedScene" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <el-dialog v-model="operateOpen" :title="operateMode === 'PREVENT' ? '防控推进' : operateMode === 'CLOSE' ? '闭环固化' : '作废'" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="风险"><span class="muted">{{ currentRow?.riskTitle }}</span></el-form-item>
        <template v-if="operateMode === 'PREVENT'">
          <el-form-item label="防控进度" required><el-input v-model="operateForm.preventProgress" type="textarea" :rows="3" /></el-form-item>
          <el-form-item label="执行记录"><el-input v-model="operateForm.record" type="textarea" :rows="2" placeholder="追加式留痕，不可清空" /></el-form-item>
        </template>
        <template v-else-if="operateMode === 'CLOSE'">
          <el-form-item label="闭环终点" required>
            <el-radio-group v-model="operateForm.finalStatus">
              <el-radio value="CLOSED">已闭环</el-radio>
              <el-radio value="AVOIDED">已规避</el-radio>
              <el-radio value="OCCURRED">已发生</el-radio>
            </el-radio-group>
          </el-form-item>
          <p class="muted">三态互斥：选定后风险归属唯一状态，不再参与其他统计；策略与记录固化归档。</p>
        </template>
        <template v-else>
          <el-form-item label="作废原因"><el-input v-model="operateForm.reason" type="textarea" :rows="2" /></el-form-item>
          <p class="muted">普通用户不可作废；作废为终态。</p>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="operateOpen = false">取消</el-button>
        <el-button :type="operateMode === 'PREVENT' ? 'warning' : operateMode === 'CLOSE' ? 'success' : 'danger'" :loading="saving" @click="submitOperate">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>
