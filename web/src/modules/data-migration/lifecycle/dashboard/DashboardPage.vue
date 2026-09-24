<!--
  用途：数据看板（基线第 16 章，T10）——六大维度统计、权限前置过滤、专题独立统计、时效分桶、下钻跳转。
  说明：本页为只读看板，无任何手动编辑/修改入口；统计数值 = 各业务台账在相同筛选条件下的条数；
        时效对账由后端闸门保证（不一致抛 DASHBOARD_DEADLINE_VIEW_MISMATCH）。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  getDashboardActivityStatus,
  getDashboardIssueStatus,
  getDashboardOrderStatus,
  getDashboardRiskStatus,
  getDashboardStageActivity,
  getDashboardTopicProgress
} from '../../../../api/data-migration-lifecycle'
import type {
  LifecycleActivityStatusView,
  LifecycleIssueStatusView,
  LifecycleOrderStatusView,
  LifecycleRiskStatusView,
  LifecycleStageActivityOrderView,
  LifecycleTopicProgressOverview
} from '../../../../types/data-migration-lifecycle'
import { LifecycleStatusText } from '../../../../types/data-migration-lifecycle'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')

const stageActivity = ref<LifecycleStageActivityOrderView[]>([])
const activityStatus = ref<LifecycleActivityStatusView | null>(null)
const topicProgress = ref<LifecycleTopicProgressOverview | null>(null)
const orderStatus = ref<LifecycleOrderStatusView | null>(null)
const issueStatus = ref<LifecycleIssueStatusView | null>(null)
const riskStatus = ref<LifecycleRiskStatusView | null>(null)

const canRead = computed(() => auth.hasPermission('data-migration-lifecycle:dashboard') || auth.hasPermission('data-migration:access') || auth.hasPermission('system:admin'))
const isAdmin = computed(() => auth.hasPermission('system:admin') || auth.hasPermission('data-migration:manage') || auth.hasPermission('data-migration-lifecycle:manage'))

const DEADLINE_TEXT: Record<string, string> = { NORMAL: '正常', NEAR_OVERDUE: '即将超时', OVERDUE: '已超时' }
const ORDER_STATUS_ORDER = ['WAIT_PRE', 'WAIT_ACCEPT', 'EXECUTING', 'SUSPENDED', 'REVIEWING', 'REVIEW_REJECTED', 'CLOSED', 'ARCHIVED', 'CANCELLED']
const ISSUE_TEXT: Record<string, string> = { WAIT_RECTIFY: '待整改', RECTIFYING: '整改中', CLOSED: '已闭环', CANCELLED: '已作废' }
const RISK_TEXT: Record<string, string> = { WAIT_PREVENT: '待防控', PREVENTING: '防控中', AVOIDED: '已规避', OCCURRED: '已发生', CLOSED: '已闭环', CANCELLED: '已作废' }
const ACT_STATUS_TEXT: Record<string, string> = { ACTIVE: '启用中', INACTIVE: '停用', OBSOLETE: '已作废' }

function orderStateCount(view: LifecycleOrderStatusView | null, key: string): number {
  if (!view) return 0
  const value = (view as unknown as Record<string, unknown>)[key]
  return typeof value === 'number' ? value : 0
}

function orderStateCountByCode(view: LifecycleOrderStatusView | null, code: string): number {
  const map: Record<string, string> = {
    WAIT_PRE: 'waitPreCnt', WAIT_ACCEPT: 'waitAcceptCnt', EXECUTING: 'executingCnt', SUSPENDED: 'suspendedCnt',
    REVIEWING: 'reviewingCnt', REVIEW_REJECTED: 'reviewRejectedCnt', CLOSED: 'closedCnt', ARCHIVED: 'archivedCnt',
    CANCELLED: 'cancelledCnt'
  }
  return orderStateCount(view, map[code] ?? '')
}

async function loadAll() {
  if (!canRead.value) { forbidden.value = true; return }
  loading.value = true
  error.value = ''
  try {
    const [stageRes, actRes, topicRes, orderRes, issueRes, riskRes] = await Promise.all([
      getDashboardStageActivity(), getDashboardActivityStatus(), getDashboardTopicProgress(),
      getDashboardOrderStatus(), getDashboardIssueStatus(), getDashboardRiskStatus()
    ])
    stageActivity.value = stageRes.data.data ?? []
    activityStatus.value = actRes.data.data ?? null
    topicProgress.value = topicRes.data.data ?? null
    orderStatus.value = orderRes.data.data ?? null
    issueStatus.value = issueRes.data.data ?? null
    riskStatus.value = riskRes.data.data ?? null
  } catch (e) {
    error.value = apiErrorMessage(e, '看板加载失败')
    if (/时效|不一致|45801/.test(error.value)) {
      error.value = '看板时效对账失败：实时时效视图与工单冗余字段不一致，请先复核时效数据（DASHBOARD_DEADLINE_VIEW_MISMATCH）'
    }
  } finally {
    loading.value = false
  }
}

/** 下钻：跳转对应模块台账并自动带上筛选参数（大盘统计 - 明细溯源闭环）。 */
function drill(path: string, query: Record<string, string | number | undefined>) {
  const clean: Record<string, string | number> = {}
  Object.entries(query).forEach(([k, v]) => { if (v !== undefined && v !== '') clean[k] = v })
  router.push({ path, query: clean })
}

function drillOrderStatus(code: string) {
  drill('/data-migration/lifecycle/flow', { orderStatus: code })
}
function drillDeadline(code: string) {
  drill('/data-migration/lifecycle/flow', { deadlineStatus: code })
}
function drillIssueStatus(code: string) {
  drill('/data-migration/lifecycle/issue', { issueStatus: code })
}
function drillRiskStatus(code: string) {
  drill('/data-migration/lifecycle/risk', { riskStatus: code })
}

onMounted(loadAll)
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra>
        <span class="muted">只读数据看板 · 无编辑入口；统计数值 = 对应台账在相同筛选条件下的条数{{ isAdmin ? '' : '（当前按您的权责范围过滤）' }}</span>
      </template>
      <template #actions>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </template>
    </UiToolbar>

    <div v-if="forbidden" class="dm-state-panel">
      <UiEmptyState title="无权限查看数据看板" description="请确认账号已分配数据看板权限（data-migration-lifecycle:dashboard）" />
    </div>
    <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
    <div v-else v-loading="loading" class="dm-dashboard">

      <!-- 维度 6：工单进度状态 + 时效分桶 -->
      <el-card shadow="never" class="ui-surface-card dm-dash-card">
        <template #header><div class="dm-dash-card__head"><strong>工单进度状态</strong><el-tag effect="plain" size="small">9 态分项之和 = 总数</el-tag></div></template>
        <div class="dm-dash-grid">
          <button v-for="code in ORDER_STATUS_ORDER" :key="code" class="dm-dash-cell" type="button" @click="drillOrderStatus(code)">
            <span class="dm-dash-cell__label">{{ LifecycleStatusText[code] ?? code }}</span>
            <span class="dm-dash-cell__value">{{ orderStateCountByCode(orderStatus, code) }}</span>
          </button>
          <button v-if="orderStatus" class="dm-dash-cell dm-dash-cell--total" type="button" @click="drill('/data-migration/lifecycle/flow', {})">
            <span class="dm-dash-cell__label">总工单</span><span class="dm-dash-cell__value">{{ orderStatus.orderTotal }}</span>
          </button>
        </div>
        <div class="dm-dash-sub">
          <span>闭环率 {{ orderStatus?.closeRate ?? 0 }}%</span>
          <span>流转正常率 {{ orderStatus?.normalRate ?? 0 }}%</span>
          <span>打回率 {{ orderStatus?.rejectRate ?? 0 }}%</span>
          <span>归档率 {{ orderStatus?.archiveRate ?? 0 }}%</span>
        </div>
        <div class="dm-dash-sub dm-dash-sub--deadline">
          <span class="muted">时效分桶（仅「在办工单」参与，叠加维度）</span>
          <button class="dm-chip" type="button" @click="drillDeadline('NORMAL')">正常 {{ orderStatus?.deadlineNormalCnt ?? 0 }}</button>
          <button class="dm-chip dm-chip--warn" type="button" @click="drillDeadline('NEAR_OVERDUE')">即将超时 {{ orderStatus?.deadlineNearCnt ?? 0 }}</button>
          <button class="dm-chip dm-chip--danger" type="button" @click="drillDeadline('OVERDUE')">已超时 {{ orderStatus?.deadlineOverdueCnt ?? 0 }}</button>
        </div>
        <div v-if="orderStatus?.byGranularity?.length" class="dm-dash-sub">
          <span v-for="g in orderStatus.byGranularity" :key="g.granularity" class="muted">
            {{ g.granularity === 'PROJECT' ? '项目级' : '组件级' }}：{{ g.orderTotal }}（超时 {{ g.deadlineOverdueCnt }}）
          </span>
        </div>
      </el-card>

      <!-- 维度 1：全生命周期阶段-活动-工单层级统计（仅普通活动） -->
      <el-card shadow="never" class="ui-surface-card dm-dash-card">
        <template #header><div class="dm-dash-card__head"><strong>全生命周期阶段 · 活动 · 工单</strong><el-tag effect="plain" size="small">仅普通活动；专题独立统计</el-tag></div></template>
        <el-empty v-if="!stageActivity.length" description="暂无归属阶段的普通活动" :image-size="60" />
        <el-collapse v-else>
          <el-collapse-item v-for="stage in stageActivity" :key="stage.stageId">
            <template #title>
              <div class="dm-dash-stage">
                <strong>{{ stage.stageName }}</strong>
                <span class="muted">{{ stage.stageCode }}</span>
                <span>工单 {{ stage.orderTotal }} · 闭环率 {{ stage.closeRate }}%</span>
              </div>
            </template>
            <el-table :data="stage.activities" size="small">
              <el-table-column prop="activityName" label="活动" min-width="140" show-overflow-tooltip />
              <el-table-column label="颗粒度" width="80">
                <template #default="{ row }">{{ row.granularity === 'PROJECT' ? '项目级' : '组件级' }}</template>
              </el-table-column>
              <el-table-column label="待前置" width="70"><template #default="{ row }">{{ row.waitPreCnt }}</template></el-table-column>
              <el-table-column label="待接收" width="70"><template #default="{ row }">{{ row.waitAcceptCnt }}</template></el-table-column>
              <el-table-column label="执行中" width="70"><template #default="{ row }">{{ row.executingCnt }}</template></el-table-column>
              <el-table-column label="已暂停" width="70"><template #default="{ row }">{{ row.suspendedCnt }}</template></el-table-column>
              <el-table-column label="审核中" width="70"><template #default="{ row }">{{ row.reviewingCnt }}</template></el-table-column>
              <el-table-column label="审核打回" width="80"><template #default="{ row }">{{ row.reviewRejectedCnt }}</template></el-table-column>
              <el-table-column label="已闭环" width="70"><template #default="{ row }">{{ row.closedCnt }}</template></el-table-column>
              <el-table-column label="已归档" width="70"><template #default="{ row }">{{ row.archivedCnt }}</template></el-table-column>
              <el-table-column label="已作废" width="70"><template #default="{ row }">{{ row.cancelledCnt }}</template></el-table-column>
              <el-table-column label="闭环率" width="80"><template #default="{ row }">{{ row.closeRate }}%</template></el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <!-- 维度 3：专题进度 -->
      <el-card shadow="never" class="ui-surface-card dm-dash-card">
        <template #header><div class="dm-dash-card__head"><strong>专题聚合活动进度</strong><el-tag effect="plain" size="small">独立于生命周期阶段</el-tag></div></template>
        <div class="dm-dash-sub">
          <span>专题 {{ topicProgress?.topicTotal ?? 0 }}</span>
          <span v-for="(text, code) of ACT_STATUS_TEXT" :key="code">{{ text }} {{ topicProgress?.[{ ACTIVE: 'activeCnt', INACTIVE: 'inactiveCnt', OBSOLETE: 'obsoleteCnt' }[code] as keyof LifecycleTopicProgressOverview] ?? 0 }}</span>
          <span>已派工 {{ topicProgress?.taskedCnt ?? 0 }}</span>
          <span>未派工 {{ topicProgress?.untaskedCnt ?? 0 }}</span>
        </div>
        <el-table v-if="topicProgress?.topics?.length" :data="topicProgress.topics" size="small">
          <el-table-column prop="topicName" label="专题活动" min-width="150" show-overflow-tooltip />
          <el-table-column label="运行状态" width="90">
            <template #default="{ row }">{{ ACT_STATUS_TEXT[row.topicStatus] ?? row.topicStatus }}</template>
          </el-table-column>
          <el-table-column label="总工单" width="80"><template #default="{ row }">{{ row.orderTotal }}</template></el-table-column>
          <el-table-column label="执行中" width="70"><template #default="{ row }">{{ row.executingCnt }}</template></el-table-column>
          <el-table-column label="已闭环" width="70"><template #default="{ row }">{{ row.closedCnt }}</template></el-table-column>
          <el-table-column label="已归档" width="70"><template #default="{ row }">{{ row.archivedCnt }}</template></el-table-column>
          <el-table-column label="已作废" width="70"><template #default="{ row }">{{ row.cancelledCnt }}</template></el-table-column>
          <el-table-column label="闭环率" width="80"><template #default="{ row }">{{ row.closeRate }}%</template></el-table-column>
          <el-table-column label="进行率" width="80"><template #default="{ row }">{{ row.doingRate }}%</template></el-table-column>
          <el-table-column label="问题率" width="80"><template #default="{ row }">{{ row.issueRate }}%</template></el-table-column>
        </el-table>
        <el-empty v-else description="暂无专题聚合活动" :image-size="60" />
      </el-card>

      <!-- 维度 2：活动进展状态 -->
      <el-card shadow="never" class="ui-surface-card dm-dash-card">
        <template #header><div class="dm-dash-card__head"><strong>活动进展状态</strong><el-tag effect="plain" size="small">类型标签区分普通/专题</el-tag></div></template>
        <div class="dm-dash-grid dm-dash-grid--4">
          <div class="dm-dash-cell"><span class="dm-dash-cell__label">活动总数</span><span class="dm-dash-cell__value">{{ activityStatus?.total ?? 0 }}</span></div>
          <div class="dm-dash-cell"><span class="dm-dash-cell__label">启用中</span><span class="dm-dash-cell__value">{{ activityStatus?.activeCnt ?? 0 }}</span></div>
          <div class="dm-dash-cell"><span class="dm-dash-cell__label">停用</span><span class="dm-dash-cell__value">{{ activityStatus?.inactiveCnt ?? 0 }}</span></div>
          <div class="dm-dash-cell"><span class="dm-dash-cell__label">已作废</span><span class="dm-dash-cell__value">{{ activityStatus?.obsoleteCnt ?? 0 }}</span></div>
        </div>
        <div class="dm-dash-sub">
          <span>普通活动 {{ activityStatus?.byType?.NORMAL ?? 0 }} · 专题 {{ activityStatus?.byType?.TOPIC ?? 0 }}</span>
          <span>项目级 {{ activityStatus?.byGranularity?.PROJECT ?? 0 }} · 组件级 {{ activityStatus?.byGranularity?.COMPONENT ?? 0 }}</span>
          <span>被专题引用 {{ activityStatus?.linkedTopicCnt ?? 0 }} · 未被引用 {{ activityStatus?.unlinkedTopicCnt ?? 0 }}</span>
        </div>
      </el-card>

      <el-row :gutter="12">
        <!-- 维度 4：问题状态分项 -->
        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="ui-surface-card dm-dash-card">
            <template #header><div class="dm-dash-card__head"><strong>问题状态分项</strong><el-tag effect="plain" size="small">{{ issueStatus?.total ?? 0 }} 条</el-tag></div></template>
            <div class="dm-dash-grid dm-dash-grid--4">
              <button v-for="(text, code) in ISSUE_TEXT" :key="code" class="dm-dash-cell" type="button" @click="drillIssueStatus(code)">
                <span class="dm-dash-cell__label">{{ text }}</span>
                <span class="dm-dash-cell__value">{{ issueStatus?.[{ WAIT_RECTIFY: 'waitRectifyCnt', RECTIFYING: 'rectifyingCnt', CLOSED: 'closedCnt', CANCELLED: 'cancelledCnt' }[code] as keyof LifecycleIssueStatusView] ?? 0 }}</span>
              </button>
            </div>
          </el-card>
        </el-col>
        <!-- 维度 5：风险状态分项 -->
        <el-col :xs="24" :md="12">
          <el-card shadow="never" class="ui-surface-card dm-dash-card">
            <template #header><div class="dm-dash-card__head"><strong>风险状态分项</strong><el-tag effect="plain" size="small">{{ riskStatus?.total ?? 0 }} 条</el-tag></div></template>
            <div class="dm-dash-grid dm-dash-grid--3">
              <button v-for="(text, code) in RISK_TEXT" :key="code" class="dm-dash-cell" type="button" @click="drillRiskStatus(code)">
                <span class="dm-dash-cell__label">{{ text }}</span>
                <span class="dm-dash-cell__value">{{ riskStatus?.[{ WAIT_PREVENT: 'waitPreventCnt', PREVENTING: 'preventingCnt', AVOIDED: 'avoidedCnt', OCCURRED: 'occurredCnt', CLOSED: 'closedCnt', CANCELLED: 'cancelledCnt' }[code] as keyof LifecycleRiskStatusView] ?? 0 }}</span>
              </button>
            </div>
            <div class="dm-dash-sub"><span>高 {{ riskStatus?.levelHighCnt ?? 0 }} · 中 {{ riskStatus?.levelMidCnt ?? 0 }} · 低 {{ riskStatus?.levelLowCnt ?? 0 }}</span></div>
          </el-card>
        </el-col>
      </el-row>

    </div>
  </section>
</template>

<style scoped>
.dm-dashboard { display: grid; gap: 12px; width: 100%; min-width: 0; }
.dm-dash-card__head { display: flex; align-items: center; gap: 8px; min-width: 0; }
.dm-dash-card__head strong { overflow-wrap: anywhere; }
.dm-dash-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); gap: 8px; }
.dm-dash-grid--3 { grid-template-columns: repeat(auto-fill, minmax(112px, 1fr)); }
.dm-dash-grid--4 { grid-template-columns: repeat(auto-fill, minmax(108px, 1fr)); }
.dm-dash-cell { display: grid; gap: 2px; min-width: 0; padding: 10px 8px; text-align: left; background: var(--panel-bg, #fff); border: 1px solid var(--line, #e4e7ed); border-radius: 6px; cursor: pointer; transition: border-color .15s; font: inherit; }
.dm-dash-cell:hover { border-color: var(--el-color-primary, #409eff); }
.dm-dash-cell--total { background: var(--el-color-primary-light-9, #ecf5ff); }
.dm-dash-cell__label { color: var(--muted, #909399); font-size: 12px; overflow-wrap: anywhere; }
.dm-dash-cell__value { font-size: 20px; font-weight: 600; line-height: 1.1; }
.dm-dash-sub { display: flex; flex-wrap: wrap; gap: 4px 14px; padding: 10px 2px 0; color: var(--el-text-color-regular, #606266); font-size: 12px; }
.dm-dash-sub--deadline { align-items: center; }
.dm-chip { padding: 4px 10px; border: 1px solid var(--line, #e4e7ed); border-radius: 12px; background: var(--panel-bg, #fff); color: var(--el-text-color-regular, #606266); cursor: pointer; font-size: 12px; }
.dm-chip--warn { color: var(--el-color-warning, #e6a23c); border-color: var(--el-color-warning-light-5, #f3d19e); }
.dm-chip--danger { color: var(--el-color-danger, #f56c6c); border-color: var(--el-color-danger-light-5, #fab6b6); }
.dm-dash-stage { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 10px; min-width: 0; }
</style>
