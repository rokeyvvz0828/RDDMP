<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import { listEnvironments, loadPhysicalSubsystemOptions, loadResourceDeploymentUnitOptions } from './api'
import type { DeploymentUnitOption, Environment, PhysicalSubsystemOption } from './types'
import { cancelPlan, createPlan, listPlanTemplates, listPlans, loadPlanUserOptions, restorePlan } from './planApi'
import type { PlanRowView, PlanStatus, PlanTemplateView } from './planApi'
import './architecture.css'

const router = useRouter()
const auth = useAuthStore()
const canView = computed(() => ['architecture:plan:view', 'architecture:plan:manage', 'architecture:view', 'architecture:manage'].some(permission => auth.hasPermission(permission)))
const canManage = computed(() => auth.hasPermission('architecture:plan:manage') || auth.hasPermission('architecture:manage'))

const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const rows = ref<PlanRowView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({
  environmentId: null as number | null,
  status: '' as PlanStatus | '',
  keyword: '',
  blocked: false,
  overdue: false,
  waived: false
})

async function load() {
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listPlans({
      environmentId: filters.environmentId,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
      blocked: filters.blocked || undefined,
      overdue: filters.overdue || undefined,
      waived: filters.waived || undefined,
      page: page.value,
      size: pageSize.value
    })
    rows.value = result.records
    total.value = result.total
  } catch (error) {
    const message = apiErrorMessage(error, '加载失败')
    if (/403|权限/.test(message)) {
      forbidden.value = true
    } else {
      loadError.value = message
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)

function search() {
  page.value = 1
  load()
}

function reset() {
  Object.assign(filters, { environmentId: null, status: '', keyword: '', blocked: false, overdue: false, waived: false })
  search()
}

function refresh() {
  load()
}

function previous() {
  if (page.value > 1) {
    page.value -= 1
    load()
  }
}

function next() {
  if (hasNext.value) {
    page.value += 1
    load()
  }
}

const hasNext = computed(() => page.value * pageSize.value < total.value)

const environments = ref<Environment[]>([])
onMounted(async () => {
  environments.value = await listEnvironments({}).catch(() => [])
})

const statusLabels: Record<PlanStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}
const statusTones: Record<PlanStatus, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  NOT_STARTED: 'info',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success',
  CANCELLED: 'danger'
}

// ---------- 新建计划向导 ----------
const createVisible = ref(false)
const createSaving = ref(false)
const wizard = ref(0)
const createForm = reactive({
  environmentId: null as number | null,
  templateId: null as number | null,
  name: '',
  planOwnerUserId: null as number | null,
  participantUserIds: [] as number[],
  physicalSubsystemIds: [] as number[],
  deploymentUnitIds: [] as number[],
  plannedRange: null as [string, string] | null
})

const templates = ref<PlanTemplateView[]>([])
const physicalOptions = ref<PhysicalSubsystemOption[]>([])
const deploymentUnitOptions = ref<DeploymentUnitOption[]>([])
const ownerOptions = ref<{ id: number; displayName: string }[]>([])

async function openCreate() {
  wizard.value = 0
  Object.assign(createForm, {
    environmentId: null, templateId: null, name: '', planOwnerUserId: null,
    participantUserIds: [], physicalSubsystemIds: [], deploymentUnitIds: [],
    plannedRange: null
  })
  createVisible.value = true
  try {
    templates.value = (await listPlanTemplates({ status: 'ACTIVE', size: 100 })).records
    ownerOptions.value = (await loadPlanUserOptions('', 50)).records
  } catch (error) {
    templates.value = []
    ownerOptions.value = []
  }
}

async function loadTargets() {
  physicalOptions.value = await loadPhysicalSubsystemOptions('', 100).catch(() => [])
  deploymentUnitOptions.value = []
}

function onPhysicalSelect(ids: number[]) {
  const physicalId = ids[0]
  if (physicalId) {
    loadResourceDeploymentUnitOptions(physicalId, 100).then(options => {
      deploymentUnitOptions.value = options
    }).catch(() => {
      deploymentUnitOptions.value = []
    })
  }
}

async function createPlanSubmit() {
  if (!createForm.environmentId || !createForm.templateId || !createForm.planOwnerUserId) {
    ElMessage.warning('请完成环境、模板与计划责任人选择')
    return
  }
  if (createForm.physicalSubsystemIds.length === 0 && createForm.deploymentUnitIds.length === 0) {
    ElMessage.warning('请至少选择一个目标（物理子系统或部署单元）')
    return
  }
  createSaving.value = true
  try {
    const plan = await createPlan({
      environmentId: createForm.environmentId,
      templateId: createForm.templateId,
      name: createForm.name.trim() || null,
      planOwnerUserId: createForm.planOwnerUserId,
      physicalSubsystemIds: createForm.physicalSubsystemIds,
      deploymentUnitIds: createForm.deploymentUnitIds,
      participantUserIds: createForm.participantUserIds,
      plannedStart: createForm.plannedRange?.[0] ?? null,
      plannedEnd: createForm.plannedRange?.[1] ?? null
    })
    ElMessage.success(`计划 ${plan.planNo} 已创建`)
    createVisible.value = false
    await load()
    router.push({ name: 'architecture-plan-detail', params: { id: plan.id } })
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '创建失败'))
  } finally {
    createSaving.value = false
  }
}

// ---------- 取消/恢复 ----------
async function doCancel(row: PlanRowView) {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', `取消计划「${row.name}」`, {
      inputPlaceholder: '取消原因（必填）', confirmButtonText: '确认取消', type: 'warning',
      inputValidator: value => (value && value.trim() ? true : '取消原因不能为空')
    })
    await cancelPlan(row.id, value.trim())
    ElMessage.success('计划已取消')
    await load()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(apiErrorMessage(error, '操作失败'))
    }
  }
}

async function doRestore(row: PlanRowView) {
  try {
    const { value } = await ElMessageBox.prompt('请输入恢复原因', `恢复计划「${row.name}」`, {
      inputPlaceholder: '恢复原因（必填）', confirmButtonText: '确认恢复',
      inputValidator: value => (value && value.trim() ? true : '恢复原因不能为空')
    })
    await restorePlan(row.id, value.trim())
    ElMessage.success('计划已恢复')
    await load()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(apiErrorMessage(error, '操作失败'))
    }
  }
}

function openDetail(row: PlanRowView) {
  router.push({ name: 'architecture-plan-detail', params: { id: row.id } })
}

function formatDateTime(value: string | null | undefined) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader title="环境搭建计划" description="从已发布模板创建搭建计划，按目标生成任务并跟踪整体进度">
      <template #actions>
        <el-button v-if="canManage" type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新建计划
        </el-button>
      </template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel">
      <el-result icon="warning" title="暂无环境搭建计划查看权限" sub-title="请申请 architecture:plan:view 或 manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="搭建计划加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="load">重新加载</el-button></template>
      </el-result>
    </section>

    <template v-else>
      <UiToolbar>
        <el-select v-model="filters.environmentId" clearable filterable placeholder="具体环境" class="architecture-filter-select">
          <el-option v-for="env in environments" :key="env.id" :label="`${env.name}（${env.code}）`" :value="env.id" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="状态" class="architecture-filter-select">
          <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
        </el-select>
        <el-input v-model="filters.keyword" clearable placeholder="计划名称/编号" style="width: 200px" @keyup.enter="search" />
        <el-checkbox v-model="filters.blocked" label="存在阻塞" />
        <el-checkbox v-model="filters.overdue" label="存在逾期" />
        <el-checkbox v-model="filters.waived" label="存在豁免" />
        <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
        <el-button @click="reset">重置</el-button>
        <template #actions>
          <el-tooltip content="刷新列表">
            <el-button circle :loading="loading" aria-label="刷新搭建计划列表" @click="refresh">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
        </template>
      </UiToolbar>

      <UiDataTable v-if="rows.length || loading" class="architecture-desktop-table" :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="计划" min-width="200">
          <template #default="scope">
            <button type="button" class="architecture-table-identity" @click="openDetail(scope.row)">
              <strong>{{ scope.row.name }}</strong>
              <small>{{ scope.row.planNo }} · {{ scope.row.environmentName }}</small>
            </button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="scope">
            <UiStatusTag :value="scope.row.status" :labels="statusLabels" :tone="statusTones[scope.row.status as PlanStatus]" />
          </template>
        </el-table-column>
        <el-table-column label="进度" width="150">
          <template #default="scope">
            <el-progress :percentage="scope.row.progress ?? 0" :stroke-width="10" :show-text="false" />
            <span class="plan-progress-text">{{ scope.row.progress ?? 0 }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="标识" min-width="150">
          <template #default="scope">
            <el-tag v-if="scope.row.hasBlocked" type="warning" size="small">阻塞</el-tag>
            <el-tag v-if="scope.row.hasOverdue" type="danger" size="small">逾期</el-tag>
            <el-tag v-if="scope.row.hasWaived" type="info" size="small">豁免</el-tag>
            <span v-if="!scope.row.hasBlocked && !scope.row.hasOverdue && !scope.row.hasWaived" class="architecture-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="任务数" width="90" align="center">
          <template #default="scope">{{ scope.row.taskCount }}</template>
        </el-table-column>
        <el-table-column label="计划结束时间" width="170">
          <template #default="scope">{{ formatDateTime(scope.row.plannedEnd) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="openDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button v-if="canManage && scope.row.status === 'CANCELLED'" link type="primary" @click="doRestore(scope.row)">恢复</el-button>
              <el-button v-if="canManage && scope.row.status !== 'COMPLETED' && scope.row.status !== 'CANCELLED'" link type="danger" @click="doCancel(scope.row)">取消</el-button>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>

      <div v-if="rows.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id">
          <header>
            <div><strong>{{ row.name }}</strong><small>{{ row.planNo }} · {{ row.environmentName }}</small></div>
            <UiStatusTag :value="row.status" :labels="statusLabels" :tone="statusTones[row.status as PlanStatus]" />
          </header>
          <dl>
            <div><dt>进度</dt><dd>{{ row.progress ?? 0 }}%（{{ row.taskCount }} 个任务）</dd></div>
            <div><dt>计划结束</dt><dd>{{ formatDateTime(row.plannedEnd) }}</dd></div>
            <div><dt>标识</dt><dd>{{ [row.hasBlocked ? '阻塞' : '', row.hasOverdue ? '逾期' : '', row.hasWaived ? '豁免' : ''].filter(Boolean).join('、') || '—' }}</dd></div>
          </dl>
          <footer>
            <el-button link type="primary" @click="openDetail(row)"><el-icon><View /></el-icon>详情</el-button>
            <el-button v-if="canManage && row.status === 'CANCELLED'" link type="primary" @click="doRestore(row)">恢复</el-button>
            <el-button v-if="canManage && row.status !== 'COMPLETED' && row.status !== 'CANCELLED'" link type="danger" @click="doCancel(row)">取消</el-button>
          </footer>
        </article>
      </div>

      <UiEmptyState v-if="!loading && !rows.length" title="暂无搭建计划" description="当前筛选下没有搭建计划记录。">
        <template #action>
          <el-button v-if="canManage" type="primary" @click="openCreate">新建计划</el-button>
          <el-button v-else @click="reset">清空筛选</el-button>
        </template>
      </UiEmptyState>

      <nav v-if="rows.length || page > 1" class="architecture-change-pagination" aria-label="搭建计划分页">
        <span>第 {{ page }} 页</span>
        <div>
          <el-button :disabled="page <= 1 || loading" @click="previous">上一页</el-button>
          <el-button :disabled="!hasNext || loading" @click="next">下一页</el-button>
        </div>
      </nav>
    </template>

    <!-- 新建计划向导 -->
    <el-dialog v-model="createVisible" title="新建搭建计划" width="min(720px, 96vw)" destroy-on-close>
      <el-steps :active="wizard" finish-status="success" align-center style="margin-bottom: 20px">
        <el-step title="环境与模板" />
        <el-step title="选择目标" />
        <el-step title="责任人与时间" />
      </el-steps>

      <el-form v-if="wizard === 0" label-width="110px">
        <el-form-item label="具体环境" required>
          <el-select v-model="createForm.environmentId" placeholder="选择具体环境" filterable style="width: 100%">
            <el-option v-for="env in environments" :key="env.id" :label="`${env.name}（${env.code}）`" :value="env.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划模板" required>
          <el-select v-model="createForm.templateId" placeholder="选择已启用模板" style="width: 100%">
            <el-option v-for="template in templates" :key="template.id" :label="`${template.name}（v${template.latestVersionNo}）`" :value="template.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划名称">
          <el-input v-model="createForm.name" placeholder="不填则自动命名" maxlength="300" />
        </el-form-item>
      </el-form>

      <el-form v-if="wizard === 1" label-width="110px">
        <el-form-item label="物理子系统" required>
          <el-select v-model="createForm.physicalSubsystemIds" multiple placeholder="选择物理子系统" filterable style="width: 100%" @change="onPhysicalSelect">
            <el-option v-for="option in physicalOptions" :key="option.id" :label="option.name" :value="option.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部署单元">
          <el-select v-model="createForm.deploymentUnitIds" multiple placeholder="选择部署单元（按部署单元维度生成）" filterable style="width: 100%">
            <el-option v-for="option in deploymentUnitOptions" :key="option.id" :label="option.name" :value="option.id" />
          </el-select>
        </el-form-item>
        <p class="plan-wizard-tip">提示：任务模板按「物理子系统」维度时对每个选中的物理子系统生成任务；按「部署单元」维度时对每个选中的部署单元生成任务。</p>
      </el-form>

      <el-form v-if="wizard === 2" label-width="110px">
        <el-form-item label="计划责任人" required>
          <el-select v-model="createForm.planOwnerUserId" filterable placeholder="选择计划责任人" style="width: 100%">
            <el-option v-for="user in ownerOptions" :key="user.id" :label="user.displayName" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务参与人">
          <el-select v-model="createForm.participantUserIds" multiple filterable placeholder="选择参与人（默认加入所有任务）" style="width: 100%">
            <el-option v-for="user in ownerOptions" :key="user.id" :label="user.displayName" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划时间">
          <el-date-picker v-model="createForm.plannedRange" type="datetimerange" range-separator="至"
                          start-placeholder="计划开始时间" end-placeholder="计划结束时间"
                          style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button v-if="wizard > 0" @click="wizard--">上一步</el-button>
        <el-button v-if="wizard < 2" type="primary" @click="wizard === 0 ? (loadTargets(), wizard++) : wizard++">下一步</el-button>
        <el-button v-else type="primary" :loading="createSaving" @click="createPlanSubmit">创建计划</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.plan-progress-text {
  margin-left: 8px;
  font-size: 12px;
  color: var(--muted);
}
.plan-wizard-tip {
  color: var(--muted);
  font-size: 12px;
  margin: 0 0 0 110px;
}
</style>
