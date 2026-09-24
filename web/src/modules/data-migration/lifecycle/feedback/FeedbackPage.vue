<!--
  用途：工序执行反馈（基线第 12 章，T6）
  说明：四段门禁（只读态→契约→锁态→身份）由服务端强制执行；本页仅按规格呈现保存/提审/问题/风险入口，
        管理员不可代填（后端校验），must_audit=false 工序无提审入口（A0 保存齐备即自动闭环）。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, Warning, CirclePlus, Tickets } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  addLifecycleWorkLog,
  getLifecycleFeedback,
  getLifecycleOrder,
  listLifecycleOrders,
  reportLifecycleIssue,
  reportLifecycleRisk,
  saveLifecycleFeedback,
  submitLifecycleFeedbackAudit
} from '../../../../api/data-migration-lifecycle'
import type { LifecycleFeedbackView, LifecycleOrderProcessView, LifecycleOrderView } from '../../../../types/data-migration-lifecycle'
import { LifecycleStatusText } from '../../../../types/data-migration-lifecycle'

function rowClassName({ row }: { row: LifecycleOrderView }) {
  return selectedOrder.value?.id === row.id ? 'dm-row-selected' : ''
}

function processRowClassName({ row }: { row: LifecycleOrderProcessView }) {
  return selectedSeq.value === row.processSeq ? 'dm-row-selected' : ''
}

const auth = useAuthStore()

const canRead = computed(() => auth.hasPermission('data-migration-lifecycle:feedback') || auth.hasPermission('data-migration:access') || auth.hasPermission('system:admin'))
const isAdmin = computed(() => auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')
const records = ref<LifecycleOrderView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive<Record<string, unknown>>({ orderCode: '', orderStatus: '' })

async function loadList() {
  if (!canRead.value) { forbidden.value = true; return }
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== '' && value !== undefined && value !== null) params[key] = value
    })
    const res = await listLifecycleOrders(params)
    records.value = res.data.data?.records ?? []
    total.value = res.data.data?.total ?? 0
  } catch (e) {
    error.value = apiErrorMessage(e, '工单列表加载失败')
  } finally {
    loading.value = false
  }
}

// ===== 工序反馈编辑 =====
const selectedOrder = ref<LifecycleOrderView | null>(null)
const processes = ref<LifecycleOrderProcessView[]>([])
const selectedSeq = ref<number | null>(null)
const feedbackLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const form = reactive<Record<string, unknown>>({
  progressDesc: '', exitContentFilled: '', deliverableIds: [] as number[], extraRemark: '', snapshotVersion: ''
})
const workLogs = ref<Array<{ id: number; content: string; operatorId: number; createdAt: string }>>([])
const issueCount = ref(0)
const riskCount = ref(0)

const PROCESS_STATUS_TAG: Record<string, string> = { LOCKED: 'info', EXECUTING: 'primary', REVIEWING: 'warning', REJECTED: 'danger', CLOSED: 'success' }

async function pickOrder(row: LifecycleOrderView) {
  selectedOrder.value = row
  selectedSeq.value = null
  processes.value = []
  try {
    const res = await getLifecycleOrder(row.id)
    processes.value = (res.data.data?.processes ?? []) as LifecycleOrderProcessView[]
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '工序加载失败'))
  }
}

async function pickProcess(seq: number) {
  if (!selectedOrder.value) return
  selectedSeq.value = seq
  feedbackLoading.value = true
  try {
    const res = await getLifecycleFeedback(selectedOrder.value.id, seq)
    const data = res.data.data as LifecycleFeedbackView
    Object.assign(form, {
      progressDesc: data.progressDesc ?? '',
      exitContentFilled: data.exitContentFilled ?? '',
      deliverableIds: data.deliverableIds ?? [],
      extraRemark: data.extraRemark ?? '',
      snapshotVersion: data.snapshotVersion
    })
    workLogs.value = data.workLog ?? []
    issueCount.value = (data.issues ?? []).length
    riskCount.value = (data.risks ?? []).length
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '反馈详情加载失败'))
  } finally {
    feedbackLoading.value = false
  }
}

const currentProcess = computed(() => processes.value.find(p => p.processSeq === selectedSeq.value) ?? null)
const writable = computed(() => {
  const p = currentProcess.value
  if (!p) return false
  if (isAdmin.value) return false
  return p.processStatus === 'EXECUTING'
})
const noAuditStage = computed(() => writable.value && currentProcess.value && !currentProcess.value.mustAudit)

async function save() {
  if (!selectedOrder.value || selectedSeq.value == null) { ElMessage.warning('请先选择工序'); return }
  if (!String(form.progressDesc ?? '').trim()) { ElMessage.warning('工序执行进度为必填项'); return }
  const p = currentProcess.value
  if (p && p.mustSubmitDeliverable && (!Array.isArray(form.deliverableIds) || form.deliverableIds.length === 0)) {
    ElMessage.warning('需提交交付件工序：请至少选取 1 个交付物（本期以文件 ID 占位）'); return
  }
  saving.value = true
  try {
    await saveLifecycleFeedback(selectedOrder.value.id, selectedSeq.value, { ...form })
    ElMessage.success('反馈已保存' + (noAuditStage.value ? '，准出齐备将自动闭环' : ''))
    pickOrder(selectedOrder.value)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function submitAudit() {
  if (!selectedOrder.value || selectedSeq.value == null) { ElMessage.warning('请先选择工序'); return }
  submitting.value = true
  try {
    await submitLifecycleFeedbackAudit(selectedOrder.value.id, selectedSeq.value)
    ElMessage.success('已提审，工序进入审核中')
    pickOrder(selectedOrder.value)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '提审失败'))
  } finally {
    submitting.value = false
  }
}

async function addLog() {
  if (!selectedOrder.value || selectedSeq.value == null || !currentProcess.value) return
  const { value } = await ElMessageBox.prompt('请输入作业日志内容（追加式留痕，不可清空）', '新增作业日志', { inputValidator: v => (v && v.trim().length > 0) || '内容必填' })
  try {
    await addLifecycleWorkLog(selectedOrder.value.id, selectedSeq.value, value.trim())
    ElMessage.success('作业日志已追加')
    pickProcess(selectedSeq.value)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '日志提交失败'))
  }
}

const issueOpen = ref(false)
const issueForm = reactive<Record<string, unknown>>({ title: '', desc: '', scene: '', impactScope: '' })
const issueSaving = ref(false)
async function submitIssue() {
  if (!selectedOrder.value || selectedSeq.value == null) { ElMessage.warning('请先选择工序'); return }
  if (!String(issueForm.title ?? '').trim() || !String(issueForm.desc ?? '').trim() || !String(issueForm.scene ?? '').trim()) {
    ElMessage.warning('问题上报必填三项：标题/描述/发生场景'); return
  }
  issueSaving.value = true
  try {
    await reportLifecycleIssue(selectedOrder.value.id, selectedSeq.value, { ...issueForm })
    ElMessage.success('问题已上报（独立台账，不可删除）')
    issueOpen.value = false
    Object.assign(issueForm, { title: '', desc: '', scene: '', impactScope: '' })
    pickProcess(selectedSeq.value)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '问题上报失败'))
  } finally {
    issueSaving.value = false
  }
}

const riskOpen = ref(false)
const riskForm = reactive<Record<string, unknown>>({ title: '', level: 'MEDIUM', desc: '', probability: 'MEDIUM', impactScope: '', preparedMeasure: '' })
const riskSaving = ref(false)
async function submitRisk() {
  if (!selectedOrder.value || selectedSeq.value == null) { ElMessage.warning('请先选择工序'); return }
  if (!String(riskForm.title ?? '').trim() || !String(riskForm.level ?? '').trim() || !String(riskForm.desc ?? '').trim()
    || !String(riskForm.probability ?? '').trim() || !String(riskForm.impactScope ?? '').trim()) {
    ElMessage.warning('风险上报必填五项：标题/等级/描述/概率/影响范围'); return
  }
  riskSaving.value = true
  try {
    await reportLifecycleRisk(selectedOrder.value.id, selectedSeq.value, { ...riskForm })
    ElMessage.success('风险已上报（独立台账，多条留痕）')
    riskOpen.value = false
    Object.assign(riskForm, { title: '', level: 'MEDIUM', desc: '', probability: 'MEDIUM', impactScope: '', preparedMeasure: '' })
    pickProcess(selectedSeq.value)
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '风险上报失败'))
  } finally {
    riskSaving.value = false
  }
}

onMounted(loadList)
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra>
        <span class="muted">管理员不可代填；只读态/未解锁/已闭环拒绝写操作（服务端四段门禁）</span>
      </template>
      <template #actions>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </template>
    </UiToolbar>

    <el-card shadow="never" class="ui-surface-card">
      <el-form inline class="dm-filter-bar">
        <el-form-item label="工单编号"><el-input v-model="filters.orderCode" clearable placeholder="WO-000001" style="width: 150px" @keyup.enter="page = 1; loadList()" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.orderStatus" clearable placeholder="全部" style="width: 130px">
            <el-option v-for="code of ['EXECUTING', 'REVIEWING', 'REVIEW_REJECTED', 'CLOSED']" :key="code" :value="code" :label="LifecycleStatusText[code] ?? code" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page = 1; loadList()">查询</el-button>
          <el-button @click="page = 1; loadList()">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="forbidden" class="dm-state-panel">
        <UiEmptyState title="无权限查看执行反馈" description="请确认账号已分配执行反馈菜单权限（data-migration-lifecycle:feedback）" />
      </div>
      <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
      <template v-else>
        <div class="dm-desktop-table">
          <UiDataTable :loading="loading" :data="records" empty-text="暂无工单" :row-class-name="rowClassName" @row-click="pickOrder">
            <el-table-column prop="orderCode" label="工单编号" width="130" />
            <el-table-column prop="activityName" label="活动" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.orderStatus === 'CLOSED' ? 'success' : row.orderStatus === 'REVIEW_REJECTED' ? 'danger' : row.orderStatus === 'REVIEWING' ? 'warning' : 'primary'">
                  {{ LifecycleStatusText[row.orderStatus] ?? row.orderStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="工序进度" width="100">
              <template #default="{ row }">{{ row.closedProcessCount }}/{{ row.totalProcessCount }}</template>
            </el-table-column>
            <el-table-column label="计划完成时间" width="150">
              <template #default="{ row }">{{ row.planFinishTime?.replace('T', ' ') }}</template>
            </el-table-column>
          </UiDataTable>
        </div>

        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <UiPagination v-model:page="page" v-model:page-size="size" :total="total" @update:page="loadList" @update:page-size="page = 1; loadList()" />
        </div>
      </template>
    </el-card>

    <el-card v-if="selectedOrder" shadow="never" class="ui-surface-card" style="margin-top: 12px">
      <template #header>
        <div class="dm-card-header">
          <span>{{ selectedOrder.orderCode }} · {{ selectedOrder.activityName }}</span>
          <el-tag size="small" :type="selectedOrder.orderStatus === 'CLOSED' ? 'success' : 'primary'">{{ LifecycleStatusText[selectedOrder.orderStatus] }}</el-tag>
        </div>
      </template>

      <el-table :data="processes" size="small" highlight-current-row :row-class-name="processRowClassName" @row-click="(row: LifecycleOrderProcessView) => pickProcess(row.processSeq)">
        <el-table-column prop="processSeq" label="序号" width="60" />
        <el-table-column prop="processName" label="工序名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="PROCESS_STATUS_TAG[row.processStatus] ?? 'info'" size="small">{{ LifecycleStatusText[row.processStatus] ?? row.processStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="准出" width="70">
          <template #default="{ row }">{{ row.exitFilled ? '已填' : '未填' }}</template>
        </el-table-column>
        <el-table-column label="交付物" width="80">
          <template #default="{ row }">{{ row.deliverableSubmitted ? '已提交' : (row.mustSubmitDeliverable ? '缺失' : '免交') }}</template>
        </el-table-column>
        <el-table-column label="需审核" width="80">
          <template #default="{ row }">{{ row.mustAudit ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="打回次数" width="80">
          <template #default="{ row }">{{ row.rejectCount }}</template>
        </el-table-column>
      </el-table>

      <div v-if="selectedSeq != null" v-loading="feedbackLoading" class="dm-feedback-panel">
        <template v-if="currentProcess">
          <div class="dm-feedback-warning" v-if="!writable">
            <span v-if="isAdmin">管理员不可代填执行反馈（作业权责主体只能是执行人/参与人）</span>
            <span v-else-if="currentProcess.processStatus === 'LOCKED'">工序未解锁，禁止填写反馈</span>
            <span v-else-if="currentProcess.processStatus === 'REVIEWING'">工序审核中，内容锁定</span>
            <span v-else-if="currentProcess.processStatus === 'CLOSED'">工序已闭环，执行内容锁定</span>
            <span v-else>只读查看</span>
          </div>

          <el-form label-width="140px">
            <el-form-item label="工序执行进度" required>
              <el-input v-model="form.progressDesc" type="textarea" :rows="3" maxlength="2000" show-word-limit :disabled="!writable" placeholder="作业完成情况/实施进展描述（必填）" />
            </el-form-item>
            <el-form-item label="准出标准内容" required>
              <el-input v-model="form.exitContentFilled" type="textarea" :rows="3" maxlength="2000" show-word-limit :disabled="!writable" placeholder="准出结果/执行结论/合规判定/落地说明（必填）" />
            </el-form-item>
            <el-form-item :label="currentProcess.mustSubmitDeliverable ? '交付物 ID（必选）' : '交付物 ID（选填）'">
              <el-select v-model="form.deliverableIds" multiple filterable allow-create :disabled="!writable" placeholder="本期以文件 ID 占位，批次4接入真实附件流" style="width: 100%" />
            </el-form-item>
            <el-form-item label="补充备注">
              <el-input v-model="form.extraRemark" type="textarea" :rows="2" :disabled="!writable" />
            </el-form-item>
          </el-form>

          <div class="dm-feedback-actions">
            <el-button v-if="writable" type="primary" :loading="saving" @click="save">保存反馈</el-button>
            <el-button v-if="writable && currentProcess.mustAudit" type="warning" :loading="submitting" @click="submitAudit">提审（进入审核）</el-button>
            <el-button v-if="writable" :icon="Tickets" @click="addLog">新增作业日志</el-button>
            <el-button v-if="writable" :icon="Warning" @click="issueOpen = true">上报问题</el-button>
            <el-button v-if="writable" :icon="CirclePlus" @click="riskOpen = true">上报风险</el-button>
          </div>

          <el-descriptions v-if="workLogs.length" :column="1" border size="small" style="margin-top: 12px">
            <el-descriptions-item v-for="log in workLogs" :key="log.id" :label="`日志 #${log.id}`">
              <span class="muted">{{ log.content }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <p v-if="issueCount || riskCount" class="muted" style="margin-top: 8px">独立台账：问题 {{ issueCount }} 条 / 风险 {{ riskCount }} 条（批次4出入台账页）</p>
        </template>
      </div>
    </el-card>

    <el-dialog v-model="issueOpen" title="问题上报（三项必填）" width="560px">
      <el-form label-width="110px">
        <el-form-item label="标题" required><el-input v-model="issueForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="描述" required><el-input v-model="issueForm.desc" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="发生场景" required><el-input v-model="issueForm.scene" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="影响范围"><el-input v-model="issueForm.impactScope" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueOpen = false">取消</el-button>
        <el-button type="primary" :loading="issueSaving" @click="submitIssue">提交上报</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="riskOpen" title="风险上报（五项必填）" width="560px">
      <el-form label-width="110px">
        <el-form-item label="标题" required><el-input v-model="riskForm.title" maxlength="200" /></el-form-item>
        <el-form-item label="等级" required>
          <el-select v-model="riskForm.level" style="width: 100%">
            <el-option value="HIGH" label="高" /><el-option value="MEDIUM" label="中" /><el-option value="LOW" label="低" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" required><el-input v-model="riskForm.desc" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="发生概率" required>
          <el-select v-model="riskForm.probability" style="width: 100%">
            <el-option value="HIGH" label="高" /><el-option value="MEDIUM" label="中" /><el-option value="LOW" label="低" />
          </el-select>
        </el-form-item>
        <el-form-item label="影响范围" required><el-input v-model="riskForm.impactScope" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="预备措施"><el-input v-model="riskForm.preparedMeasure" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="riskOpen = false">取消</el-button>
        <el-button type="primary" :loading="riskSaving" @click="submitRisk">提交上报</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.dm-filter-bar { margin-bottom: 4px; }
.dm-card-header { display: flex; align-items: center; justify-content: space-between; }
.dm-feedback-panel { margin-top: 14px; }
.dm-feedback-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
.dm-feedback-warning { background: var(--app-warning-bg, #fdf6ec); border: 1px solid var(--app-warning-border, #faecd8); border-radius: 6px; padding: 8px 12px; margin-bottom: 12px; font-size: 13px; }
:deep(.dm-row-selected) { --el-table-tr-bg-color: var(--el-color-primary-light-9) !important; }
</style>
