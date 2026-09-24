<!--
  用途：任务审核台账（基线第 15 章）
  说明：仅审核角色可审（管理员只读不得代审）；单条/批量通过、批量打回三约束（统一原因/二次确认/≤50）、
        10 分钟撤销窗口还原工序为待审；审核记录固化可溯源。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Refresh, RefreshLeft, Search, View, Warning } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  batchPassLifecycleAudit,
  batchRejectLifecycleAudit,
  getLifecycleAuditRecords,
  listLifecycleAudit,
  passLifecycleAudit,
  rejectLifecycleAudit,
  revokeLifecycleAuditBatch
} from '../../../../api/data-migration-lifecycle'
import type { LifecycleAuditView } from '../../../../types/data-migration-lifecycle'
import { LifecycleStatusText } from '../../../../types/data-migration-lifecycle'

const auth = useAuthStore()
const isAdmin = computed(() => auth.hasPermission('system:admin') || auth.hasPermission('data-migration:manage'))
const canAudit = computed(() => auth.hasPermission('data-migration-lifecycle:audit:pass') && !isAdmin.value)
const canRevoke = computed(() => canAudit.value && auth.hasPermission('data-migration-lifecycle:audit:revoke'))

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')
const records = ref<LifecycleAuditView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const selection = ref<LifecycleAuditView[]>([])

const filters = reactive<Record<string, unknown>>({ orderCode: '', auditStatus: '', auditResult: '' })

const AUDIT_STATUS_TEXT: Record<string, string> = { WAIT_REVIEW: '待审核', PASSED: '已通过', REJECTED: '已打回', RECHECK: '整改复审' }
const PROCESS_STATUS_TEXT: Record<string, string> = { LOCKED: '锁定', EXECUTING: '执行中', REVIEWING: '审核中', REJECTED: '打回整改', CLOSED: '已闭环' }

async function loadList() {
  if (!auth.hasPermission('data-migration-lifecycle:audit') && !auth.hasPermission('data-migration:access') && !auth.hasPermission('system:admin')) {
    forbidden.value = true
    return
  }
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    Object.entries(filters).forEach(([k, v]) => { if (v !== '' && v !== undefined && v !== null) params[k] = v })
    const res = await listLifecycleAudit(params)
    records.value = res.data.data?.records ?? []
    total.value = res.data.data?.total ?? 0
  } catch (e) {
    error.value = apiErrorMessage(e, '审核台账加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, { orderCode: '', auditStatus: '', auditResult: '' })
  page.value = 1
  loadList()
}

const detailOpen = ref(false)
const detailLoading = ref(false)
const auditRecords = ref<LifecycleAuditView[]>([])
async function openDetail(row: LifecycleAuditView) {
  detailOpen.value = true
  detailLoading.value = true
  try {
    const res = await getLifecycleAuditRecords(row.orderId, row.processSeq || 0)
    auditRecords.value = res.data.data ?? []
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '审核记录加载失败'))
  } finally {
    detailLoading.value = false
  }
}

// ===== 单条审核 =====
const auditOpen = ref(false)
const saving = ref(false)
const auditForm = reactive<Record<string, unknown>>({ orderId: undefined, processSeq: undefined, auditOpinion: '', rectifyRequirement: '', specialRemark: '' })
const currentRow = ref<LifecycleAuditView | null>(null)
function openAudit(row: LifecycleAuditView, result: 'PASSED' | 'REJECTED') {
  currentRow.value = row
  Object.assign(auditForm, { orderId: row.orderId, processSeq: row.processSeq, auditOpinion: '', rectifyRequirement: '', specialRemark: '' })
  auditForm.auditResult = result
  auditOpen.value = true
}
async function submitAudit() {
  if (!String(auditForm.auditOpinion || '').trim()) { ElMessage.warning('审核意见必填'); return }
  if (auditForm.auditResult === 'REJECTED' && !String(auditForm.rectifyRequirement || '').trim()) { ElMessage.warning('打回整改要求必填'); return }
  saving.value = true
  try {
    if (auditForm.auditResult === 'PASSED') {
      await passLifecycleAudit({ ...auditForm })
    } else {
      await rejectLifecycleAudit({ ...auditForm })
    }
    ElMessage.success(auditForm.auditResult === 'PASSED' ? '审核通过，工序闭环并解锁后置' : '已打回，工序退回执行中')
    auditOpen.value = false
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '审核提交失败'))
  } finally {
    saving.value = false
  }
}

// ===== 批量 =====
const batchOpen = ref(false)
const batchMode = ref<'PASS' | 'REJECT'>('PASS')
const batchForm = reactive<Record<string, unknown>>({ auditOpinion: '', unifiedReason: '', rectifyRequirement: '', confirmed: false })
function openBatch(mode: 'PASS' | 'REJECT') {
  if (selection.value.length === 0) { ElMessage.warning('请先勾选待审工单'); return }
  if (mode === 'REJECT' && selection.value.length > 50) { ElMessage.warning('单批上限 50 条，超出需分批'); return }
  batchMode.value = mode
  Object.assign(batchForm, { auditOpinion: '', unifiedReason: '', rectifyRequirement: '', confirmed: false })
  batchOpen.value = true
}
async function submitBatch() {
  if (batchMode.value === 'PASS') {
    if (!String(batchForm.auditOpinion || '').trim()) { ElMessage.warning('批量审核意见必填'); return }
    saving.value = true
    try {
      const ids = selection.value.map(r => r.orderId)
      await batchPassLifecycleAudit({ orderIds: ids, auditOpinion: batchForm.auditOpinion, specialRemark: batchForm.specialRemark })
      ElMessage.success(`批量通过成功（${ids.length} 条）`)
      batchOpen.value = false
      selection.value = []
      loadList()
    } catch (e) {
      ElMessage.error(apiErrorMessage(e, '批量通过失败'))
    } finally { saving.value = false }
    return
  }
  if (!String(batchForm.unifiedReason || '').trim()) { ElMessage.warning('统一打回原因必填'); return }
  if (!String(batchForm.rectifyRequirement || '').trim()) { ElMessage.warning('整改要求必填'); return }
  if (!batchForm.confirmed) {
    ElMessage.warning('请二次确认本次批量打回的工单数量与清单一致')
    return
  }
  saving.value = true
  try {
    const ids = selection.value.map(r => r.orderId)
    const res = await batchRejectLifecycleAudit({ orderIds: ids, unifiedReason: batchForm.unifiedReason, rectifyRequirement: batchForm.rectifyRequirement, confirmed: true })
    ElMessage.success(`批量打回已提交（${res.data.data?.rejected ?? ids.length} 条），10 分钟内可整批撤销`)
    batchOpen.value = false
    selection.value = []
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '批量打回失败'))
  } finally { saving.value = false }
}

// ===== 撤销 =====
const revoking = ref(false)
const batchInput = ref('')
async function revokeBatch() {
  if (!batchInput.value) { ElMessage.warning('请输入批次号'); return }
  revoking.value = true
  try {
    const res = await revokeLifecycleAuditBatch({ batchId: Number(batchInput.value) })
    ElMessage.success(res.data.data?.message ?? '撤销完成')
    batchInput.value = ''
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '撤销失败'))
  } finally { revoking.value = false }
}

function handleSelectionChange(rows: LifecycleAuditView[]) {
  selection.value = rows
}

onMounted(loadList)
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra><span class="muted">审核结果驱动工序闭环与后置解锁；管理员只读不代审；批量打回 10 分钟内可整批撤销</span></template>
      <template #actions>
        <el-button v-if="canAudit" type="primary" :icon="Check" @click="openBatch('PASS')">批量通过</el-button>
        <el-button v-if="canAudit" type="danger" :icon="Warning" @click="openBatch('REJECT')">批量打回</el-button>
        <el-input v-if="canRevoke" v-model="batchInput" placeholder="批次号撤销" style="width: 150px" />
        <el-button v-if="canRevoke" :icon="RefreshLeft" :loading="revoking" @click="revokeBatch">撤销</el-button>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </template>
    </UiToolbar>

    <el-card shadow="never" class="ui-surface-card">
      <el-form inline class="dm-filter-bar">
        <el-form-item label="工单编号"><el-input v-model="filters.orderCode" clearable placeholder="WO-xxx" style="width: 150px" @keyup.enter="page = 1; loadList()" /></el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="filters.auditStatus" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="(label, code) in AUDIT_STATUS_TEXT" :key="code" :value="code" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核结果">
          <el-select v-model="filters.auditResult" clearable placeholder="全部" style="width: 120px">
            <el-option value="PASSED" label="审核通过" /><el-option value="REJECTED" label="审核打回" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page = 1; loadList()">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="forbidden" class="dm-state-panel"><UiEmptyState title="无权限查看审核台账" description="请确认账号已分配审核菜单权限" /></div>
      <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
      <template v-else>
        <div class="dm-desktop-table">
          <UiDataTable :loading="loading" :data="records" empty-text="暂无待审工单" row-key="id" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="44" :disabled="!canAudit" />
            <el-table-column prop="orderCode" label="工单编号" width="140" show-overflow-tooltip />
            <el-table-column prop="activityName" label="活动名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="颗粒度" width="90">
              <template #default="{ row }">{{ row.granularity === 'COMPONENT' ? '组件级' : '项目级' }}</template>
            </el-table-column>
            <el-table-column prop="processName" label="当前工序" min-width="130" show-overflow-tooltip />
            <el-table-column label="工序状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.processStatus === 'REVIEWING' ? 'warning' : row.processStatus === 'REJECTED' ? 'danger' : 'info'" size="small">
                  {{ PROCESS_STATUS_TEXT[row.processStatus] ?? row.processStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审核状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.auditStatus === 'PASSED' ? 'success' : row.auditStatus === 'REJECTED' ? 'danger' : 'primary'" size="small">
                  {{ AUDIT_STATUS_TEXT[row.auditStatus ?? 'WAIT_REVIEW'] ?? row.auditStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="复审次数" width="90">
              <template #default="{ row }">{{ row.auditRound ?? row.rejectCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="openDetail(row)">记录</el-button>
                <template v-if="canAudit && row.processStatus !== 'CLOSED'">
                  <el-button link type="success" :icon="Check" @click="openAudit(row, 'PASSED')">通过</el-button>
                  <el-button link type="danger" :icon="Warning" @click="openAudit(row, 'REJECTED')">打回</el-button>
                </template>
              </template>
            </el-table-column>
          </UiDataTable>
        </div>

        <div v-if="records.length === 0 && !loading" class="dm-state-panel"><UiEmptyState title="暂无待审/打回工单" description="执行人提审后工单将进入本台账" /></div>
        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <UiPagination v-model:page="page" v-model:page-size="size" :total="total" @update:page="loadList" @update:page-size="page = 1; loadList()" />
        </div>
      </template>
    </el-card>

    <!-- 单条审核 -->
    <el-dialog v-model="auditOpen" :title="auditForm.auditResult === 'PASSED' ? '审核通过' : '审核打回'" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="工单/工序"><span class="muted">{{ currentRow?.orderCode }} / {{ currentRow?.processName }}</span></el-form-item>
        <el-form-item label="审核意见" required>
          <el-input v-model="auditForm.auditOpinion" type="textarea" :rows="3" maxlength="500" placeholder="通过=合规验收结论；打回=问题定位与不合规点" />
        </el-form-item>
        <el-form-item v-if="auditForm.auditResult === 'REJECTED'" label="整改要求" required>
          <el-input v-model="auditForm.rectifyRequirement" type="textarea" :rows="3" maxlength="500" placeholder="整改内容/整改标准/补充资料要求/复审条件" />
        </el-form-item>
        <el-form-item label="特殊备注"><el-input v-model="auditForm.specialRemark" maxlength="300" placeholder="选填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditOpen = false">取消</el-button>
        <el-button :type="auditForm.auditResult === 'PASSED' ? 'success' : 'danger'" :loading="saving" @click="submitAudit">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 批量操作 -->
    <el-dialog v-model="batchOpen" :title="batchMode === 'PASS' ? '批量审核通过' : '批量打回（二次确认）'" width="560px" destroy-on-close>
      <el-alert v-if="batchMode === 'REJECT'" type="warning" :closable="false" show-icon
                :title="`本次将打回 ${selection.length} 条工单（统一原因 + 整改要求，10 分钟内可整批撤销）`" class="dm-batch-alert" />
      <el-form label-width="110px">
        <template v-if="batchMode === 'PASS'">
          <el-form-item label="统一审核意见" required><el-input v-model="batchForm.auditOpinion" type="textarea" :rows="3" placeholder="批量通过的合规验收结论" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="统一打回原因" required><el-input v-model="batchForm.unifiedReason" type="textarea" :rows="3" placeholder="按工单逐条留存" /></el-form-item>
          <el-form-item label="整改要求" required><el-input v-model="batchForm.rectifyRequirement" type="textarea" :rows="3" placeholder="整改内容/标准/复审条件" /></el-form-item>
          <el-form-item label="二次确认">
            <el-checkbox v-model="batchForm.confirmed">已确认本次打回工单数量与清单与实际一致（{{ selection.length }} 条）</el-checkbox>
          </el-form-item>
        </template>
      </el-form>
      <el-scrollbar max-height="220px" class="dm-batch-list">
        <div v-for="row in selection" :key="row.orderId" class="dm-batch-row">{{ row.orderCode }} · {{ row.activityName }} · {{ row.processName }}</div>
      </el-scrollbar>
      <template #footer>
        <el-button @click="batchOpen = false">取消</el-button>
        <el-button :type="batchMode === 'PASS' ? 'success' : 'danger'" :loading="saving" @click="submitBatch">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 审核记录溯源 -->
    <el-dialog v-model="detailOpen" title="审核记录（固化溯源）" width="720px" destroy-on-close>
      <div v-if="detailLoading" class="dm-state-panel"><UiEmptyState title="加载中…" /></div>
      <div v-else-if="auditRecords.length === 0" class="dm-state-panel"><UiEmptyState title="暂无审核记录" description="打回/通过后在此固化留痕" /></div>
      <el-timeline v-else>
        <el-timeline-item v-for="rec in auditRecords" :key="rec.id" :timestamp="rec.auditedAt?.replace('T', ' ') ?? ''"
                          :type="rec.auditResult === 'PASSED' ? 'success' : rec.revokedAt ? 'info' : 'danger'">
          <div class="dm-audit-record">
            <span class="dm-audit-tag">第 {{ rec.auditRound }} 轮</span>
            <el-tag size="small" :type="rec.auditResult === 'PASSED' ? 'success' : 'danger'">{{ rec.auditResult === 'PASSED' ? '审核通过' : '审核打回' }}</el-tag>
            <el-tag v-if="rec.revokedAt" size="small" type="info">已于 {{ String(rec.revokedAt).replace('T', ' ') }} 撤销</el-tag>
            <p class="muted">{{ rec.auditOpinion }}</p>
            <p v-if="rec.rectifyRequirement" class="muted">整改要求：{{ rec.rectifyRequirement }}</p>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </section>
</template>
