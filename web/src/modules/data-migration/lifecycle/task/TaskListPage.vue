<!--
  用途：任务下发台账（基线第 10 章）
  说明：任务为下发唯一入口产物；支持多维度检索、单发/批量下发（矩阵交集拆分）、
        工单级快照固化结果查看。角色/组件/成员复用平台既有能力。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  dispatchLifecycleTask,
  getLifecycleTask,
  listLifecycleActivities,
  listLifecycleComponentOptions,
  listLifecycleMemberOptions,
  listLifecycleTasks
} from '../../../../api/data-migration-lifecycle'
import { getReportProjectOptions } from '../../../../api/data-migration'
import type { LifecycleActivityView, LifecycleOrderView, LifecycleTaskView } from '../../../../types/data-migration-lifecycle'
import { LifecycleStatusText } from '../../../../types/data-migration-lifecycle'

const auth = useAuthStore()

const canRead = computed(() => auth.hasPermission('data-migration-lifecycle:task') || auth.hasPermission('data-migration:access') || auth.hasPermission('system:admin'))
const canDispatch = computed(() => auth.hasPermission('data-migration-lifecycle:task:create') || auth.hasPermission('data-migration:manage') || auth.hasPermission('system:admin'))

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')
const records = ref<LifecycleTaskView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive<Record<string, unknown>>({
  taskCode: '', taskName: '', granularity: '', activityType: '', taskStatus: '', createdFrom: undefined, createdTo: undefined
})

const GRAN_TEXT: Record<string, string> = { PROJECT: '项目级', COMPONENT: '组件级' }
const TYPE_TEXT: Record<string, string> = { NORMAL: '普通基础活动', TOPIC: '专题聚合活动' }

async function loadList() {
  if (!canRead.value) { forbidden.value = true; return }
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== '' && value !== undefined && value !== null) params[key] = value
    })
    const res = await listLifecycleTasks(params)
    records.value = res.data.data?.records ?? []
    total.value = res.data.data?.total ?? 0
  } catch (e) {
    error.value = apiErrorMessage(e, '任务列表加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, { taskCode: '', taskName: '', granularity: '', activityType: '', taskStatus: '', createdFrom: undefined, createdTo: undefined })
  page.value = 1
  loadList()
}

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<{ task?: Partial<LifecycleTaskView> | null; orders?: LifecycleOrderView[] }>({})

async function openDetail(row: LifecycleTaskView) {
  detailOpen.value = true
  detailLoading.value = true
  try {
    const res = await getLifecycleTask(row.id)
    detail.value = res.data.data ?? {}
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '任务详情加载失败'))
  } finally {
    detailLoading.value = false
  }
}

// ===== 任务下发 =====
const dispatchOpen = ref(false)
const saving = ref(false)
const activityOptions = ref<LifecycleActivityView[]>([])
const componentOptions = ref<Array<{ id: number; projectName: string; systemName: string | null | undefined }>>([])
const memberOptions = ref<Array<{ id: number; displayName: string; username: string }>>([])
const projectOptions = ref<Array<{ id: number; project_name: string }>>([])
const dispatch = reactive<Record<string, unknown>>({
  activityId: undefined, taskName: '', granularity: 'PROJECT', activityType: 'NORMAL',
  projectId: undefined, componentIds: [] as number[], participantIds: [] as number[],
  executorId: undefined, planFinishTime: undefined
})

async function openDispatch() {
  dispatchOpen.value = true
  Object.assign(dispatch, { activityId: undefined, taskName: '', granularity: 'PROJECT', activityType: 'NORMAL', componentIds: [], participantIds: [], executorId: undefined, planFinishTime: undefined })
  try {
    const [activityRes, componentRes, memberRes, projectRes] = await Promise.all([
      listLifecycleActivities({ page: 1, size: 500, activityStatus: 'ACTIVE' }),
      listLifecycleComponentOptions(),
      listLifecycleMemberOptions({ page: 1, size: 500 }),
      getReportProjectOptions()
    ])
    activityOptions.value = activityRes.data.data?.records ?? []
    componentOptions.value = (componentRes.data.data ?? []).map(c => ({ id: c.id, projectName: c.projectName, systemName: c.systemName }))
    memberOptions.value = (memberRes.data.data?.records ?? []).map(m => ({ id: m.id, displayName: m.displayName, username: m.username }))
    projectOptions.value = projectRes.data.data ?? []
  } catch (e) {
    ElMessage.warning('选项数据加载失败：' + apiErrorMessage(e, '选项加载失败'))
  }
}

function onActivityChange(id: unknown) {
  const activity = activityOptions.value.find(a => a.id === Number(id))
  if (!activity) return
  dispatch.granularity = activity.granularity
  dispatch.activityType = activity.activityType
  dispatch.componentIds = []
}

async function submitDispatch() {
  if (!dispatch.activityId) { ElMessage.warning('请选择标准活动'); return }
  if (!dispatch.projectId) { ElMessage.warning('请选择所属项目'); return }
  if (!dispatch.planFinishTime) { ElMessage.warning('请填写计划完成时间'); return }
  if (dispatch.granularity === 'COMPONENT' && (!Array.isArray(dispatch.componentIds) || dispatch.componentIds.length === 0)) {
    ElMessage.warning('组件级任务必须勾选至少 1 个组件'); return
  }
  saving.value = true
  try {
    await dispatchLifecycleTask({ ...dispatch, planFinishTime: (dispatch.planFinishTime as string).replace(' ', 'T') })
    ElMessage.success('任务下发成功，工单已按矩阵口径拆分并固化快照')
    dispatchOpen.value = false
    page.value = 1
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '任务下发失败'))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra>
        <span class="muted">任务为下发唯一入口产物；工单=「普通活动 × 组件勾选」矩阵交集，快照下发即冻结</span>
      </template>
      <template #actions>
        <el-button v-if="canDispatch" type="primary" :icon="Plus" @click="openDispatch">任务下发</el-button>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </template>
    </UiToolbar>

    <el-card shadow="never" class="ui-surface-card">
      <el-form inline class="dm-filter-bar">
        <el-form-item label="任务编号"><el-input v-model="filters.taskCode" clearable placeholder="TK-20260924-001" style="width: 150px" @keyup.enter="page = 1; loadList()" /></el-form-item>
        <el-form-item label="任务名称"><el-input v-model="filters.taskName" clearable placeholder="按名称模糊检索" style="width: 150px" @keyup.enter="page = 1; loadList()" /></el-form-item>
        <el-form-item label="颗粒度">
          <el-select v-model="filters.granularity" clearable placeholder="全部" style="width: 110px">
            <el-option value="PROJECT" label="项目级" /><el-option value="COMPONENT" label="组件级" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.activityType" clearable placeholder="全部" style="width: 130px">
            <el-option value="NORMAL" label="普通基础活动" /><el-option value="TOPIC" label="专题聚合活动" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.taskStatus" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="code of ['WAIT_PRE', 'EXECUTING', 'REVIEWING', 'REVIEW_REJECTED', 'CLOSED']" :key="code" :value="code" :label="LifecycleStatusText[code] ?? code" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="filters.createdFrom" type="date" placeholder="开始" value-format="YYYY-MM-DD" style="width: 140px" />
          <span class="dm-range-sep">至</span>
          <el-date-picker v-model="filters.createdTo" type="date" placeholder="结束" value-format="YYYY-MM-DD" style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page = 1; loadList()">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="forbidden" class="dm-state-panel">
        <UiEmptyState title="无权限查看任务下发" description="请确认账号已分配任务下发菜单权限（data-migration-lifecycle:task）" />
      </div>
      <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
      <template v-else>
        <div class="dm-desktop-table">
          <UiDataTable :loading="loading" :data="records" empty-text="暂无任务">
            <el-table-column prop="taskCode" label="任务编号" width="150" show-overflow-tooltip />
            <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="类型" width="120">
              <template #default="{ row }"><el-tag :type="row.activityType === 'TOPIC' ? 'warning' : 'info'" size="small">{{ TYPE_TEXT[row.activityType] ?? row.activityType }}</el-tag></template>
            </el-table-column>
            <el-table-column label="颗粒度" width="90">
              <template #default="{ row }">{{ GRAN_TEXT[row.granularity] ?? row.granularity }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.taskStatus === 'CLOSED' ? 'success' : row.taskStatus === 'REVIEW_REJECTED' ? 'danger' : 'primary'" size="small">
                  {{ LifecycleStatusText[row.taskStatus] ?? row.taskStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="工单闭环" width="110">
              <template #default="{ row }">{{ row.orderClosed }}/{{ row.orderTotal }}</template>
            </el-table-column>
            <el-table-column label="进度" width="90">
              <template #default="{ row }">
                <el-progress :percentage="Number(row.closeProgress ?? 0)" :stroke-width="8" />
              </template>
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
          <UiEmptyState title="暂无任务" description="点击「任务下发」基于已启用标准活动生成任务与工单" />
        </div>

        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <UiPagination v-model:page="page" v-model:page-size="size" :total="total" @update:page="loadList" @update:page-size="page = 1; loadList()" />
        </div>
      </template>
    </el-card>

    <UiFormDrawer v-model="dispatchOpen" title="任务下发" :loading="saving" width="640px" @submit="submitDispatch">
      <el-form label-width="110px">
        <el-form-item label="标准活动" required>
          <el-select v-model="dispatch.activityId" filterable placeholder="仅启用活动可下发" style="width: 100%" @change="onActivityChange">
            <el-option v-for="a in activityOptions" :key="a.id" :value="a.id" :label="`${a.activityCode} ${a.activityName}（${TYPE_TEXT[a.activityType]} / ${GRAN_TEXT[a.granularity]}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input v-model="dispatch.taskName" maxlength="200" placeholder="留空默认使用活动名称生成" />
        </el-form-item>
        <el-form-item label="颗粒度">
          <span class="muted">{{ GRAN_TEXT[String(dispatch.granularity)] }}</span>
          <span v-if="dispatch.activityType === 'TOPIC'" class="muted tip-block">专题将按「普通活动 × 组件」交集矩阵拆分（D-22 快照取自被展开普通活动）</span>
        </el-form-item>
        <el-form-item label="所属项目" required>
          <el-select v-model="dispatch.projectId" filterable placeholder="选择项目（任务归属）" style="width: 100%">
            <el-option v-for="p in projectOptions" :key="p.id" :value="p.id" :label="p.project_name" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dispatch.granularity === 'COMPONENT'" label="组件勾选" required>
          <el-select v-model="dispatch.componentIds" multiple filterable placeholder="按活动关联范围勾选（交集拆分）" style="width: 100%">
            <el-option v-for="c in componentOptions" :key="c.id" :value="c.id" :label="`${c.projectName} / ${c.systemName ?? c.id}`" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="组件勾选">
          <span class="muted">项目级任务禁止携带组件</span>
        </el-form-item>
        <el-form-item label="默认执行人">
          <el-select v-model="dispatch.executorId" clearable filterable placeholder="组件级默认取组件负责人，可覆盖；项目级必填" style="width: 100%">
            <el-option v-for="m in memberOptions" :key="m.id" :value="m.id" :label="`${m.displayName}（${m.username}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="参与人">
          <el-select v-model="dispatch.participantIds" multiple filterable placeholder="可多选补充参与人" style="width: 100%">
            <el-option v-for="m in memberOptions" :key="m.id" :value="m.id" :label="`${m.displayName}（${m.username}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划完成时间" required>
          <el-date-picker v-model="dispatch.planFinishTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不得早于当前时间" style="width: 100%" />
        </el-form-item>
      </el-form>
    </UiFormDrawer>

    <el-dialog v-model="detailOpen" title="任务详情" width="720px">
      <div v-loading="detailLoading">
        <template v-if="detail.task">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="任务编号">{{ detail.task.taskCode }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ LifecycleStatusText[String(detail.task.taskStatus ?? '')] ?? detail.task.taskStatus }}</el-descriptions-item>
            <el-descriptions-item label="任务名称" :span="2">{{ detail.task.taskName }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ TYPE_TEXT[String(detail.task.activityType ?? '')] ?? detail.task.activityType }}</el-descriptions-item>
            <el-descriptions-item label="颗粒度">{{ GRAN_TEXT[String(detail.task.granularity ?? '')] ?? detail.task.granularity }}</el-descriptions-item>
            <el-descriptions-item label="快照版本">{{ detail.task.snapshotVersion }}</el-descriptions-item>
            <el-descriptions-item label="计划完成时间">{{ detail.task.planFinishTime?.replace('T', ' ') }}</el-descriptions-item>
          </el-descriptions>
          <h4 class="muted" style="margin: 14px 0 8px">下发生成工单（{{ Array.isArray(detail.orders) ? detail.orders.length : 0 }}）</h4>
          <el-table :data="detail.orders ?? []" size="small" max-height="320">
            <el-table-column prop="orderCode" label="工单编号" width="130" />
            <el-table-column prop="activityName" label="活动" min-width="140" show-overflow-tooltip />
            <el-table-column label="组件" width="90">
              <template #default="{ row }">{{ row.componentId ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ LifecycleStatusText[row.orderStatus] ?? row.orderStatus }}</template>
            </el-table-column>
            <el-table-column label="进度" width="80">
              <template #default="{ row }">{{ row.closedProcessCount }}/{{ row.totalProcessCount }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.dm-filter-bar { margin-bottom: 4px; }
.dm-range-sep { margin: 0 6px; color: var(--muted); }
.tip-block { display: block; margin-top: 6px; font-size: 12px; line-height: 1.6; }
</style>
