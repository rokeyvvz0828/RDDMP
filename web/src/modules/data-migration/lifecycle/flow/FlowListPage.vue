<!--
  用途：工单流转台账与干预（基线第 11 章 + 18.2/18.3/18.4）
  说明：服务端状态机派生主状态；本页承载台账检索、工序流转面板、暂停/恢复/转交/重启/时效/归档干预
        与审核结果写入（T7 前由管理员/审核人消费）。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  adjustLifecycleOrderSla,
  applyLifecycleAuditResult,
  archiveLifecycleOrder,
  exemptLifecycleOrderSla,
  getLifecycleOrder,
  listLifecycleOrders,
  restartLifecycleOrder,
  resumeLifecycleOrder,
  suspendLifecycleOrder,
  transferLifecycleOrder
} from '../../../../api/data-migration-lifecycle'
import type { LifecycleOrderProcessView, LifecycleOrderView } from '../../../../types/data-migration-lifecycle'
import { LifecycleStatusText } from '../../../../types/data-migration-lifecycle'

const auth = useAuthStore()

const canRead = computed(() => auth.hasPermission('data-migration-lifecycle:order') || auth.hasPermission('data-migration:access') || auth.hasPermission('system:admin'))
const canIntervene = computed(() => auth.hasPermission('data-migration-lifecycle:order:intervene') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))
const canReview = computed(() => auth.hasPermission('data-migration-lifecycle:review') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')
const records = ref<LifecycleOrderView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive<Record<string, unknown>>({
  orderCode: '', orderStatus: '', granularity: '', activityType: '', deadlineStatus: '', createdFrom: undefined, createdTo: undefined
})

const GRAN_TEXT: Record<string, string> = { PROJECT: '项目级', COMPONENT: '组件级' }
const TYPE_TEXT: Record<string, string> = { NORMAL: '普通基础活动', TOPIC: '专题聚合活动' }
const DEADLINE_TEXT: Record<string, string> = { NORMAL: '正常', NEAR_OVERDUE: '即将超时', OVERDUE: '已超时' }

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

function resetFilters() {
  Object.assign(filters, { orderCode: '', orderStatus: '', granularity: '', activityType: '', deadlineStatus: '', createdFrom: undefined, createdTo: undefined })
  page.value = 1
  loadList()
}

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<Record<string, unknown>>({})
const processes = ref<LifecycleOrderProcessView[]>([])

function detailText(key: string) {
  return String(detail.value[key] ?? '').replace('T', ' ')
}

async function openDetail(row: LifecycleOrderView) {
  detailOpen.value = true
  detailLoading.value = true
  try {
    const res = await getLifecycleOrder(row.id)
    detail.value = res.data.data ?? {}
    processes.value = (res.data.data?.processes ?? []) as LifecycleOrderProcessView[]
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '工单详情加载失败'))
  } finally {
    detailLoading.value = false
  }
}

const orderId = () => Number(detail.value.id ?? 0)

async function doSuspend() {
  if (!canIntervene.value) { ElMessage.warning('无干预权限'); return }
  const { value } = await ElMessageBox.prompt('请输入暂停原因（留痕必填）', '暂停工单', { inputValidator: v => (v && v.trim().length > 0) || '暂停原因必填' })
  await runAction(() => suspendLifecycleOrder(orderId(), value.trim()), '工单已暂停，未闭环工序锁定')
}

async function doResume() {
  await runAction(() => resumeLifecycleOrder(orderId()), '工单已恢复，计划完成时间顺延暂停时长')
}

async function doTransfer() {
  if (!canIntervene.value) { ElMessage.warning('无干预权限'); return }
  const { value } = await ElMessageBox.prompt('请输入目标成员 ID', '转交工单', { inputValidator: v => (v && /^\d+$/.test(v)) || '请输入目标成员 ID' })
  const reasonText = await ElMessageBox.prompt('请输入转交原因（留痕必填）', '转交原因', { inputValidator: v => (v && v.trim().length > 0) || '转交原因必填' })
  await runAction(() => transferLifecycleOrder(orderId(), Number(value), reasonText.value.trim()), '工单已转交并留痕')
}

async function doRestart() {
  if (!canIntervene.value) { ElMessage.warning('无干预权限'); return }
  const seq = await ElMessageBox.prompt('请输入恢复点工序序号（该工序之后将被重置）', '异常重启', { inputValidator: v => (v && /^\d+$/.test(v)) || '恢复点序号必填' })
  const reasonText = await ElMessageBox.prompt('请输入重启原因（留痕必填）', '异常重启原因', { inputValidator: v => (v && v.trim().length > 0) || '原因必填' })
  await runAction(() => restartLifecycleOrder(orderId(), Number(seq.value), reasonText.value.trim()), '工单已重启并重置恢复点之后工序')
}

async function doSlaAdjust() {
  if (!canIntervene.value) { ElMessage.warning('无干预权限'); return }
  const { value } = await ElMessageBox.prompt('输入新的计划完成时间（YYYY-MM-DD HH:mm:ss）', '时效调整', { inputValidator: v => (v && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(v)) || '时间格式错误' })
  const reasonText = await ElMessageBox.prompt('请输入调整原因（留痕必填）', '时效调整原因', { inputValidator: v => (v && v.trim().length > 0) || '原因必填' })
  await runAction(() => adjustLifecycleOrderSla(orderId(), value.replace(' ', 'T'), reasonText.value.trim()), '时效已调整并重算分档')
}

async function doSlaExempt() {
  if (!canIntervene.value) { ElMessage.warning('无干预权限'); return }
  const { value } = await ElMessageBox.prompt('请输入豁免原因（留痕必填）', '时效豁免', { inputValidator: v => (v && v.trim().length > 0) || '原因必填' })
  await runAction(() => exemptLifecycleOrderSla(orderId(), true, value.trim()), '已豁免时效，恒为正常档不预警')
}

async function doArchive() {
  await runAction(() => archiveLifecycleOrder(orderId()), '工单已归档')
}

/** 审核结果写入（先落库后判定；T5 契约，审核页 T7 复用）。 */
async function audit(processSeq: number, result: 'PASSED' | 'REJECTED') {
  if (!canReview.value) { ElMessage.warning('无审核权限'); return }
  try {
    const r = await applyLifecycleAuditResult(orderId(), processSeq, result)
    ElMessage.success(result === 'PASSED' ? '审核通过，工序闭环并解锁后置' : '已打回，工序退回执行中并锁止后置')
    openDetail({ id: orderId() } as LifecycleOrderView)
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '审核结果写入失败'))
  }
}

async function runAction(action: () => Promise<unknown>, successText: string) {
  try {
    await action()
    ElMessage.success(successText)
    await openDetail({ id: orderId() } as LifecycleOrderView)
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '操作失败'))
  }
}

const PROCESS_STATUS_TAG: Record<string, string> = { LOCKED: 'info', EXECUTING: 'primary', REVIEWING: 'warning', REJECTED: 'danger', CLOSED: 'success' }

onMounted(loadList)
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra>
        <span class="muted">工单主状态为服务端派生值；流转唯一驱动因子 = 执行反馈达标 + 审核结果</span>
      </template>
      <template #actions>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </template>
    </UiToolbar>

    <el-card shadow="never" class="ui-surface-card">
      <el-form inline class="dm-filter-bar">
        <el-form-item label="工单编号"><el-input v-model="filters.orderCode" clearable placeholder="WO-000001" style="width: 140px" @keyup.enter="page = 1; loadList()" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.orderStatus" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="code of ['WAIT_PRE', 'EXECUTING', 'SUSPENDED', 'REVIEWING', 'REVIEW_REJECTED', 'CLOSED', 'ARCHIVED', 'CANCELLED']" :key="code" :value="code" :label="LifecycleStatusText[code] ?? code" />
          </el-select>
        </el-form-item>
        <el-form-item label="时效">
          <el-select v-model="filters.deadlineStatus" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="(text, code) of DEADLINE_TEXT" :key="code" :value="code" :label="text" />
          </el-select>
        </el-form-item>
        <el-form-item label="颗粒度">
          <el-select v-model="filters.granularity" clearable placeholder="全部" style="width: 110px">
            <el-option value="PROJECT" label="项目级" /><el-option value="COMPONENT" label="组件级" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page = 1; loadList()">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="forbidden" class="dm-state-panel">
        <UiEmptyState title="无权限查看工单流转" description="请确认账号已分配工单流转菜单权限（data-migration-lifecycle:order）" />
      </div>
      <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
      <template v-else>
        <div class="dm-desktop-table">
          <UiDataTable :loading="loading" :data="records" empty-text="暂无工单">
            <el-table-column prop="orderCode" label="工单编号" width="130" show-overflow-tooltip />
            <el-table-column prop="activityName" label="活动" min-width="150" show-overflow-tooltip />
            <el-table-column label="颗粒度" width="90">
              <template #default="{ row }">{{ GRAN_TEXT[row.granularity] ?? row.granularity }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.orderStatus === 'CLOSED' ? 'success' : row.orderStatus === 'REVIEW_REJECTED' || row.orderStatus === 'CANCELLED' ? 'danger' : row.orderStatus === 'SUSPENDED' ? 'info' : row.orderStatus === 'REVIEWING' ? 'warning' : 'primary'" size="small">
                  {{ LifecycleStatusText[row.orderStatus] ?? row.orderStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时效" width="90">
              <template #default="{ row }">
                <el-tag :type="row.deadlineStatus === 'OVERDUE' ? 'danger' : row.deadlineStatus === 'NEAR_OVERDUE' ? 'warning' : 'info'" size="small">
                  {{ DEADLINE_TEXT[row.deadlineStatus] ?? row.deadlineStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="工序进度" width="100">
              <template #default="{ row }">{{ row.closedProcessCount }}/{{ row.totalProcessCount }}</template>
            </el-table-column>
            <el-table-column label="计划完成时间" width="150">
              <template #default="{ row }">{{ row.planFinishTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </UiDataTable>
        </div>

        <div v-if="records.length === 0 && !loading" class="dm-state-panel">
          <UiEmptyState title="暂无工单" description="任务下发后自动承接生成工单" />
        </div>

        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <UiPagination v-model:page="page" v-model:page-size="size" :total="total" @update:page="loadList" @update:page-size="page = 1; loadList()" />
        </div>
      </template>
    </el-card>

    <el-dialog v-model="detailOpen" title="工单详情 · 工序流转面板" width="860px">
      <div v-loading="detailLoading">
        <template v-if="detail.id">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="工单编号">{{ detail.orderCode }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ LifecycleStatusText[String(detail.orderStatus)] ?? detail.orderStatus }}</el-descriptions-item>
            <el-descriptions-item label="时效">{{ DEADLINE_TEXT[String(detail.deadlineStatus)] ?? detail.deadlineStatus }}</el-descriptions-item>
            <el-descriptions-item label="活动">{{ detail.activityName }}</el-descriptions-item>
            <el-descriptions-item label="组件">{{ detail.componentId ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="执行人">{{ detail.currentExecutorId }}</el-descriptions-item>
            <el-descriptions-item label="计划完成时间">{{ detailText('planFinishTime') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="闭环时间">{{ detailText('closedAt') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="阻塞原因">{{ detail.blockReason ?? 'NONE' }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="canIntervene" class="dm-detail-actions">
            <el-button v-if="String(detail.orderStatus) !== 'SUSPENDED' && !['CLOSED', 'ARCHIVED', 'CANCELLED'].includes(String(detail.orderStatus))" size="small" @click="doSuspend">暂停</el-button>
            <el-button v-if="String(detail.orderStatus) === 'SUSPENDED'" size="small" type="success" @click="doResume">恢复</el-button>
            <el-button v-if="!['CLOSED', 'ARCHIVED', 'CANCELLED', 'SUSPENDED'].includes(String(detail.orderStatus))" size="small" @click="doTransfer">转交</el-button>
            <el-button v-if="!['CLOSED', 'ARCHIVED', 'CANCELLED', 'SUSPENDED'].includes(String(detail.orderStatus))" size="small" @click="doRestart">异常重启</el-button>
            <el-button v-if="!['CLOSED', 'ARCHIVED', 'CANCELLED', 'SUSPENDED'].includes(String(detail.orderStatus))" size="small" @click="doSlaAdjust">时效调整</el-button>
            <el-button v-if="!['CLOSED', 'ARCHIVED', 'CANCELLED', 'SUSPENDED'].includes(String(detail.orderStatus))" size="small" @click="doSlaExempt">时效豁免</el-button>
            <el-button v-if="String(detail.orderStatus) === 'CLOSED'" size="small" type="warning" @click="doArchive">归档</el-button>
          </div>

          <h4 class="muted" style="margin: 14px 0 8px">工序实例（流转依据=工单快照）</h4>
          <el-table :data="processes" size="small" max-height="360">
            <el-table-column prop="processSeq" label="序号" width="60" />
            <el-table-column prop="processName" label="工序名称" min-width="140" show-overflow-tooltip />
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
            <el-table-column label="审核" width="90">
              <template #default="{ row }">{{ LifecycleStatusText[row.auditStatus] ?? row.auditStatus }}</template>
            </el-table-column>
            <el-table-column label="打回次数" width="80">
              <template #default="{ row }">{{ row.rejectCount }}</template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <template v-if="canReview && row.processStatus === 'REVIEWING'">
                  <el-button link type="success" @click="audit(row.processSeq, 'PASSED')">通过</el-button>
                  <el-button link type="danger" @click="audit(row.processSeq, 'REJECTED')">打回</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.dm-filter-bar { margin-bottom: 4px; }
.dm-detail-actions { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px; }
</style>
