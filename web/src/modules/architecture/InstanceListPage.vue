<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Connection, Delete, Plus, Refresh, Search, View, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  createInstanceDisasterRecovery,
  deleteInstanceDisasterRecovery,
  getEnvironmentInstance,
  listAvailableStandbyInstances,
  listEnvironments,
  listEnvironmentInstances,
  listInstanceDisasterRecoveries,
  loadPhysicalSubsystemOptions,
  loadResourceDeploymentUnitOptions,
  offlineEnvironmentInstance
} from './api'
import type {
  DeploymentUnitOption,
  DisasterRecoveryMode,
  Environment,
  EnvironmentInstance,
  InstanceDisasterRecovery,
  InstanceStatus,
  PhysicalSubsystemOption
} from './types'
import { formatDateTime, httpStatus } from './utils'
import './architecture.css'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const instances = ref<EnvironmentInstance[]>([])
const environments = ref<Environment[]>([])
const physicalOptions = ref<PhysicalSubsystemOption[]>([])
const deploymentUnitOptions = ref<DeploymentUnitOption[]>([])

const page = ref(1)
const pageSize = ref(20)

const filters = reactive({
  environmentId: null as number | null,
  physicalSubsystemId: null as number | null,
  deploymentUnitId: null as number | null,
  status: '' as InstanceStatus | '',
  keyword: ''
})

const statusLabels: Record<InstanceStatus, string> = {
  ACTIVE: '在用',
  OFFLINE: '已下线'
}

const statusTones: Record<InstanceStatus, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  ACTIVE: 'success',
  OFFLINE: 'info'
}

function instanceStatusTone(status: InstanceStatus) {
  return statusTones[status]
}

function networkZoneLabel(instance: EnvironmentInstance | null | undefined) {
  return instance?.networkZoneName || instance?.networkZone || '—'
}

const drModeLabels: Record<DisasterRecoveryMode, string> = {
  PRIMARY_STANDBY: '主备',
  ACTIVE_ACTIVE: '双活',
  COLD_STANDBY: '冷备'
}

const canView = computed(() =>
  ['architecture:instance:view', 'architecture:instance:manage', 'architecture:view', 'architecture:manage'].some(p => auth.hasPermission(p))
)
const canManage = computed(() =>
  auth.hasPermission('architecture:instance:manage') || auth.hasPermission('architecture:manage')
)

// Statistics
const totalCount = computed(() => instances.value.length)
const activeCount = computed(() => instances.value.filter(i => i.status === 'ACTIVE').length)
const offlineCount = computed(() => instances.value.filter(i => i.status === 'OFFLINE').length)
const versionDiffCount = computed(() => instances.value.filter(i => i.hasVersionDifference && i.status === 'ACTIVE').length)
const hasNext = computed(() => instances.value.length === pageSize.value)

// Detail Drawer
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<EnvironmentInstance | null>(null)
const instanceDrs = ref<InstanceDisasterRecovery[]>([])
const drLoading = ref(false)

// Offline Dialog
const offlineDialogVisible = ref(false)
const offlineSubmitting = ref(false)
const targetOfflineInstance = ref<EnvironmentInstance | null>(null)
const offlineReason = ref('')
const offlineRisks = ref<InstanceDisasterRecovery[]>([])
const offlineRiskLoading = ref(false)

// Disaster Recovery Create Dialog
const drDialogVisible = ref(false)
const drSubmitting = ref(false)
const availableStandbys = ref<EnvironmentInstance[]>([])
const standbysLoading = ref(false)
const drForm = reactive({
  primaryInstanceId: 0,
  standbyInstanceId: null as number | null,
  drMode: 'PRIMARY_STANDBY' as DisasterRecoveryMode,
  description: ''
})

async function loadOptions() {
  try {
    const [envList, physList] = await Promise.all([
      listEnvironments({ limit: 100, offset: 0 }),
      loadPhysicalSubsystemOptions()
    ])
    environments.value = envList
    physicalOptions.value = physList
  } catch (err) {
    console.error('加载选项失败', err)
  }
}

async function onPhysicalChange(physId: number | null) {
  filters.deploymentUnitId = null
  if (!physId) {
    deploymentUnitOptions.value = []
    return
  }
  try {
    deploymentUnitOptions.value = await loadResourceDeploymentUnitOptions(physId)
  } catch (err) {
    console.error('加载部署单元失败', err)
  }
}

async function load() {
  if (!canView.value) return
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const query: any = {
      keyword: filters.keyword || undefined,
      environmentId: filters.environmentId || undefined,
      physicalSubsystemId: filters.physicalSubsystemId || undefined,
      deploymentUnitId: filters.deploymentUnitId || undefined,
      status: filters.status || undefined,
      limit: pageSize.value,
      offset: (page.value - 1) * pageSize.value
    }
    instances.value = await listEnvironmentInstances(query)
  } catch (error) {
    if (httpStatus(error) === 403) {
      forbidden.value = true
    } else {
      loadError.value = apiErrorMessage(error, '环境部署实例加载失败')
    }
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await load()
  if (!loadError.value && !forbidden.value) {
    ElMessage.success('实例列表已刷新')
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  filters.environmentId = null
  filters.physicalSubsystemId = null
  filters.deploymentUnitId = null
  filters.status = ''
  filters.keyword = ''
  deploymentUnitOptions.value = []
  page.value = 1
  load()
}

function previous() {
  if (page.value <= 1 || loading.value) return
  page.value--
  load()
}

function next() {
  if (!hasNext.value || loading.value) return
  page.value++
  load()
}

async function showDetail(inst: EnvironmentInstance) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  instanceDrs.value = []
  try {
    const [instDetail, drs] = await Promise.all([
      getEnvironmentInstance(inst.id),
      listInstanceDisasterRecoveries(inst.id)
    ])
    detail.value = instDetail
    instanceDrs.value = drs
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, '加载实例详情失败'))
  } finally {
    detailLoading.value = false
  }
}

async function refreshDrs() {
  if (!detail.value) return
  drLoading.value = true
  try {
    instanceDrs.value = await listInstanceDisasterRecoveries(detail.value.id)
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, '刷新灾备拓扑失败'))
  } finally {
    drLoading.value = false
  }
}

async function openOfflineDialog(inst: EnvironmentInstance) {
  targetOfflineInstance.value = inst
  offlineReason.value = ''
  offlineRisks.value = []
  offlineDialogVisible.value = true
  offlineRiskLoading.value = true
  const targetId = inst.id
  try {
    const risks = await listInstanceDisasterRecoveries(targetId)
    if (targetOfflineInstance.value?.id === targetId) {
      offlineRisks.value = risks
    }
  } catch (err) {
    if (targetOfflineInstance.value?.id === targetId) {
      ElMessage.warning(apiErrorMessage(err, '关联灾备关系加载失败，请在下线前人工确认拓扑影响'))
    }
  } finally {
    if (targetOfflineInstance.value?.id === targetId) {
      offlineRiskLoading.value = false
    }
  }
}

async function confirmOffline() {
  if (!targetOfflineInstance.value) return
  if (!offlineReason.value.trim()) {
    ElMessage.warning('请填写下线原因')
    return
  }
  offlineSubmitting.value = true
  try {
    const updated = await offlineEnvironmentInstance(targetOfflineInstance.value.id, {
      offlineReason: offlineReason.value.trim(),
      rowVersion: targetOfflineInstance.value.rowVersion
    })
    ElMessage.success(`实例 ${updated.machineName} (${updated.ipAddress}) 已成功下线`)
    offlineDialogVisible.value = false
    if (detail.value && detail.value.id === updated.id) {
      detail.value = updated
    }
    await load()
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, '实例下线操作失败'))
  } finally {
    offlineSubmitting.value = false
  }
}

async function openCreateDrDialog(inst: EnvironmentInstance) {
  drForm.primaryInstanceId = inst.id
  drForm.standbyInstanceId = null
  drForm.drMode = 'PRIMARY_STANDBY'
  drForm.description = ''
  drDialogVisible.value = true
  standbysLoading.value = true
  try {
    availableStandbys.value = await listAvailableStandbyInstances(inst.deploymentUnitId, inst.id)
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, '获取可选备用实例失败'))
  } finally {
    standbysLoading.value = false
  }
}

async function submitCreateDr() {
  if (!drForm.standbyInstanceId) {
    ElMessage.warning('请选择备实例')
    return
  }
  drSubmitting.value = true
  try {
    await createInstanceDisasterRecovery({
      deploymentUnitId: detail.value?.deploymentUnitId,
      primaryInstanceId: drForm.primaryInstanceId,
      standbyInstanceId: drForm.standbyInstanceId,
      drMode: drForm.drMode,
      description: drForm.description.trim() || undefined
    })
    ElMessage.success('灾备拓扑关系建立成功')
    drDialogVisible.value = false
    await refreshDrs()
  } catch (err) {
    ElMessage.error(apiErrorMessage(err, '建立灾备关系失败'))
  } finally {
    drSubmitting.value = false
  }
}

async function handleRemoveDr(dr: InstanceDisasterRecovery) {
  try {
    await ElMessageBox.confirm(
      `确定解除实例 ${dr.primaryMachineName} 与 ${dr.standbyMachineName} 之间的 ${drModeLabels[dr.drMode]} 灾备关系吗？`,
      '解除灾备确认',
      { type: 'warning', confirmButtonText: '确定解除', cancelButtonText: '取消' }
    )
    await deleteInstanceDisasterRecovery(dr.id)
    ElMessage.success('灾备关系已成功解除')
    await refreshDrs()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(apiErrorMessage(err, '解除灾备关系失败'))
    }
  }
}

function navigateToRequest(requestId: number) {
  router.push(`/architecture/resource-requests/${requestId}`)
}

onMounted(async () => {
  if (route.query.environmentId) {
    filters.environmentId = Number(route.query.environmentId)
  }
  if (route.query.physicalSubsystemId) {
    filters.physicalSubsystemId = Number(route.query.physicalSubsystemId)
    await onPhysicalChange(filters.physicalSubsystemId)
  }
  if (route.query.deploymentUnitId) {
    filters.deploymentUnitId = Number(route.query.deploymentUnitId)
  }
  await Promise.all([loadOptions(), load()])
})
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader
      title="环境部署实例"
      description="管理物理/虚拟主机、容器部署实例与资源下发事实，支持同部署单元跨/同环境灾备拓扑及下线生命周期审计。"
    />

    <el-alert v-if="forbidden" type="warning" :closable="false" show-icon title="暂无环境部署实例查看权限。" />
    <el-alert v-else-if="loadError" type="error" :closable="false" show-icon :title="loadError" />

    <template v-else>
      <!-- Summary Metrics Bar -->
      <section class="architecture-stats-grid" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 16px;">
        <div style="background: var(--el-bg-color-overlay, #fff); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px 18px;">
          <div style="font-size: 13px; color: var(--el-text-color-secondary);">全部实例总数</div>
          <div style="font-size: 24px; font-weight: 600; margin-top: 4px;">{{ totalCount }}</div>
        </div>
        <div style="background: var(--el-bg-color-overlay, #fff); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px 18px;">
          <div style="font-size: 13px; color: var(--el-color-success);">在用实例</div>
          <div style="font-size: 24px; font-weight: 600; margin-top: 4px; color: var(--el-color-success);">{{ activeCount }}</div>
        </div>
        <div style="background: var(--el-bg-color-overlay, #fff); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px 18px;">
          <div style="font-size: 13px; color: var(--el-text-color-placeholder);">已下线实例</div>
          <div style="font-size: 24px; font-weight: 600; margin-top: 4px; color: var(--el-text-color-secondary);">{{ offlineCount }}</div>
        </div>
        <div style="background: var(--el-bg-color-overlay, #fff); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px 18px;">
          <div style="font-size: 13px; color: var(--el-color-warning);">存在设计版本差异</div>
          <div style="font-size: 24px; font-weight: 600; margin-top: 4px; color: var(--el-color-warning);">{{ versionDiffCount }}</div>
        </div>
      </section>

      <!-- Filter Toolbar -->
      <UiToolbar>
        <el-select v-model="filters.environmentId" placeholder="所属具体环境" clearable filterable style="width: 180px;" @change="search">
          <el-option v-for="env in environments" :key="env.id" :label="`${env.name} (${env.code})`" :value="env.id" />
        </el-select>
        <el-select v-model="filters.physicalSubsystemId" placeholder="所属物理子系统" clearable filterable style="width: 190px;" @change="onPhysicalChange(filters.physicalSubsystemId); search()">
          <el-option v-for="p in physicalOptions" :key="p.id" :label="`${p.name} (${p.shortName || p.code})`" :value="p.id" />
        </el-select>
        <el-select v-model="filters.deploymentUnitId" placeholder="所属部署单元" clearable filterable style="width: 190px;" :disabled="!filters.physicalSubsystemId" @change="search">
          <el-option v-for="u in deploymentUnitOptions" :key="u.id" :label="`${u.name} (${u.code})`" :value="u.id" />
        </el-select>
        <el-select v-model="filters.status" placeholder="实例状态" clearable style="width: 130px;" @change="search">
          <el-option label="在用 (ACTIVE)" value="ACTIVE" />
          <el-option label="已下线 (OFFLINE)" value="OFFLINE" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="搜索机器名 / IP / 实例号" clearable style="width: 210px;" @keyup.enter="search" />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <template #actions>
          <el-tooltip content="刷新列表">
            <el-button circle :loading="loading" aria-label="刷新实例列表" @click="refresh">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
        </template>
      </UiToolbar>

      <!-- Desktop Table -->
      <UiDataTable v-if="instances.length || loading" class="architecture-desktop-table" :data="instances" :loading="loading" row-key="id" border>
        <el-table-column label="实例 / 机器信息" min-width="210">
          <template #default="{ row }">
            <button type="button" class="architecture-table-identity" @click="showDetail(row)">
              <strong>{{ row.machineName }}</strong>
              <small><code>{{ row.ipAddress }}</code> · {{ row.instanceNo }}</small>
            </button>
          </template>
        </el-table-column>

        <el-table-column label="所属环境 / 子系统" min-width="200">
          <template #default="{ row }">
            <div><strong>{{ row.environmentName }}</strong></div>
            <small class="architecture-muted">{{ row.physicalSubsystemName }}</small>
          </template>
        </el-table-column>

        <el-table-column label="所属部署单元 / 版本" min-width="220">
          <template #default="{ row }">
            <div><strong>{{ row.deploymentUnitName }}</strong></div>
            <div style="display: flex; align-items: center; gap: 6px; margin-top: 2px;">
              <el-tag size="small" :type="row.hasVersionDifference ? 'warning' : 'info'">
                v{{ row.deploymentUnitVersionNo }}
                <template v-if="row.hasVersionDifference"> (最新 v{{ row.latestDeploymentUnitVersionNo }})</template>
              </el-tag>
              <el-tooltip v-if="row.hasVersionDifference" content="该实例采用的部署单元版本与最新设计版本存在差异，请评估是否需发起变更工单。">
                <el-icon color="#e6a23c"><Warning /></el-icon>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="实际资源" width="160">
          <template #default="{ row }">
            <div style="font-size: 13px;">
              <span><strong>{{ row.cpuCores }}</strong> 核</span> /
              <span><strong>{{ row.memoryGb }}</strong> G</span>
            </div>
            <small class="architecture-muted">
              存储: {{ Number(row.databaseStorageGb || 0) + Number(row.fileStorageGb || 0) + Number(row.extraCbsGb || 0) + Number(row.localDiskGb || 0) }} G
            </small>
          </template>
        </el-table-column>

        <el-table-column label="网络 / 平台" width="140">
          <template #default="{ row }">
            <div>{{ networkZoneLabel(row) }}</div>
            <small class="architecture-muted">{{ row.deploymentPlatform || '—' }}</small>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <UiStatusTag :value="row.status" :labels="statusLabels" :tone="instanceStatusTone(row.status)" />
          </template>
        </el-table-column>

        <el-table-column label="办理方式" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.fulfillmentMode === 'AUTOMATED' ? 'success' : 'info'">
              {{ row.fulfillmentMode === 'AUTOMATED' ? '自动部署' : '手动录入' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="来源工单" width="140">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="navigateToRequest(row.sourceRequestId)">
              {{ row.sourceRequestNo }}
            </el-button>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="architecture-table-actions">
              <el-button link type="primary" @click="showDetail(row)"><el-icon><View /></el-icon>详情</el-button>
              <el-button link type="primary" @click="showDetail(row)"><el-icon><Connection /></el-icon>灾备</el-button>
              <el-button
                v-if="canManage && row.status === 'ACTIVE'"
                link
                type="danger"
                @click="openOfflineDialog(row)"
              >
                下线
              </el-button>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>

      <!-- Mobile List -->
      <div v-if="instances.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in instances" :key="row.id">
          <header>
            <div>
              <strong>{{ row.machineName }} ({{ row.ipAddress }})</strong>
              <small>{{ row.environmentName }} · {{ row.deploymentUnitName }}</small>
            </div>
            <UiStatusTag :value="row.status" :labels="statusLabels" :tone="instanceStatusTone(row.status)" />
          </header>
          <dl>
            <div><dt>实际规格</dt><dd>{{ row.cpuCores }}核 / {{ row.memoryGb }}GB</dd></div>
            <div><dt>采用版本</dt><dd>v{{ row.deploymentUnitVersionNo }} <span v-if="row.hasVersionDifference" style="color: #e6a23c;">(最新v{{ row.latestDeploymentUnitVersionNo }})</span></dd></div>
            <div><dt>网络分区</dt><dd>{{ networkZoneLabel(row) }}</dd></div>
            <div><dt>来源工单</dt><dd>{{ row.sourceRequestNo }}</dd></div>
          </dl>
          <footer>
            <el-button link type="primary" @click="showDetail(row)"><el-icon><View /></el-icon>详情与灾备</el-button>
            <el-button v-if="canManage && row.status === 'ACTIVE'" link type="danger" @click="openOfflineDialog(row)">下线</el-button>
          </footer>
        </article>
      </div>

      <UiEmptyState v-if="!loading && !instances.length" title="暂无环境部署实例" description="当前筛选条件下未查询到实例记录。可在资源申请审批通过后办理下发生成实例。">
        <template #action>
          <el-button @click="reset">清空筛选</el-button>
        </template>
      </UiEmptyState>

      <nav v-if="instances.length || page > 1" class="architecture-change-pagination" aria-label="环境部署实例分页">
        <span>第 {{ page }} 页</span>
        <div>
          <el-button :disabled="page <= 1 || loading" @click="previous">上一页</el-button>
          <el-button :disabled="!hasNext || loading" @click="next">下一页</el-button>
        </div>
      </nav>
    </template>

    <!-- Instance Detail Drawer -->
    <el-drawer v-model="detailOpen" size="min(760px, 96vw)" :title="detail?.instanceNo || '环境部署实例详情'">
      <div v-loading="detailLoading" class="architecture-drawer-body">
        <template v-if="detail">
          <div class="architecture-detail-heading">
            <strong>{{ detail.machineName }}</strong>
            <span><code>{{ detail.ipAddress }}</code> · {{ detail.environmentName }} ({{ detail.environmentCode }})</span>
          </div>

          <dl class="architecture-detail-list">
            <div><dt>实例编号</dt><dd>{{ detail.instanceNo }}</dd></div>
            <div><dt>状态</dt><dd><UiStatusTag :value="detail.status" :labels="statusLabels" :tone="instanceStatusTone(detail.status)" /></dd></div>
            <div><dt>所属环境</dt><dd>{{ detail.environmentName }}（{{ detail.environmentTypeName || detail.environmentCode }}）</dd></div>
            <div><dt>物理子系统</dt><dd>{{ detail.physicalSubsystemName }}（{{ detail.physicalSubsystemCode }}）</dd></div>
            <div><dt>所属部署单元</dt><dd>{{ detail.deploymentUnitName }}（{{ detail.deploymentUnitCode }} · {{ detail.deploymentUnitKind }}）</dd></div>
            <div><dt>采用 DU 版本</dt><dd>v{{ detail.deploymentUnitVersionNo }}</dd></div>
            <div><dt>最新设计 DU 版本</dt><dd>v{{ detail.latestDeploymentUnitVersionNo }}</dd></div>
            <div><dt>版本差异状态</dt><dd><el-tag :type="detail.hasVersionDifference ? 'warning' : 'success'">{{ detail.hasVersionDifference ? '存在设计版本差异' : '与最新设计一致' }}</el-tag></dd></div>
          </dl>

          <el-alert
            v-if="detail.hasVersionDifference && detail.status === 'ACTIVE'"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 16px;"
            title="版本差异提示"
            :description="`采用版本为 v${detail.deploymentUnitVersionNo}，部署单元已发布更新设计版本 v${detail.latestDeploymentUnitVersionNo}。如需升级实例规格或技术栈，请发起架构变更与资源调整。`"
          />

          <section class="architecture-drawer-section">
            <header><strong>实际分配资源明细</strong></header>
            <div class="architecture-resource-summary-grid">
              <article><span>CPU 核心数</span><strong>{{ detail.cpuCores }} 核</strong></article>
              <article><span>内存容量</span><strong>{{ detail.memoryGb }} GB</strong></article>
              <article><span>数据库存储</span><strong>{{ detail.databaseStorageGb }} GB</strong></article>
              <article><span>文件存储</span><strong>{{ detail.fileStorageGb }} GB</strong></article>
              <article><span>CBS 附加盘</span><strong>{{ detail.extraCbsGb }} GB</strong></article>
              <article><span>本地盘</span><strong>{{ detail.localDiskGb }} GB</strong></article>
            </div>
          </section>

          <section class="architecture-drawer-section">
            <header><strong>运行环境与技术栈</strong></header>
            <dl class="architecture-detail-list">
              <div><dt>服务器类型</dt><dd>{{ detail.serverType || '—' }}</dd></div>
              <div><dt>部署平台</dt><dd>{{ detail.deploymentPlatform || '—' }}</dd></div>
              <div><dt>网络分区</dt><dd>{{ networkZoneLabel(detail) }}</dd></div>
              <div><dt>JDK 版本</dt><dd>{{ detail.jdkVersion || '—' }}</dd></div>
              <div><dt>中间件</dt><dd>{{ detail.middleware || '—' }}</dd></div>
              <div><dt>操作系统</dt><dd>{{ detail.operatingSystem || '—' }}</dd></div>
              <div><dt>数据库</dt><dd>{{ detail.databaseName ? `${detail.databaseName} ${detail.databaseVersion || ''}` : '—' }}</dd></div>
              <div><dt>组件支持</dt><dd>{{ [detail.needsNft && 'NFT', detail.needsFserver && 'FServer', detail.needsJobexecutor && 'JobExecutor'].filter(Boolean).join('、') || '无附加组件' }}</dd></div>
            </dl>
          </section>

          <section class="architecture-drawer-section">
            <header><strong>来源工单与下发事实</strong></header>
            <dl class="architecture-detail-list">
              <div><dt>来源工单</dt><dd><el-button link type="primary" @click="navigateToRequest(detail.sourceRequestId)">{{ detail.sourceRequestNo }}</el-button></dd></div>
              <div><dt>办理方式</dt><dd><el-tag size="small" :type="detail.fulfillmentMode === 'AUTOMATED' ? 'success' : 'info'">{{ detail.fulfillmentMode === 'AUTOMATED' ? '自动部署' : '手动逐台录入' }}</el-tag></dd></div>
              <div v-if="detail.differenceReason" class="is-wide"><dt>差异说明原因</dt><dd style="color: #e6a23c; font-weight: 500;">{{ detail.differenceReason }}</dd></div>
              <div class="is-wide"><dt>备注</dt><dd>{{ detail.remark || '—' }}</dd></div>
            </dl>
          </section>

          <!-- Offline Audit section -->
          <section v-if="detail.status === 'OFFLINE'" class="architecture-drawer-section" style="background: var(--el-fill-color-light); border-radius: 8px; padding: 14px;">
            <header><strong style="color: var(--el-text-color-secondary);">下线审计事实</strong></header>
            <dl class="architecture-detail-list" style="margin-top: 8px;">
              <div><dt>下线时间</dt><dd>{{ formatDateTime(detail.offlinedAt) }}</dd></div>
              <div><dt>下线操作人 ID</dt><dd>{{ detail.offlinedBy }}</dd></div>
              <div class="is-wide"><dt>下线原因</dt><dd style="font-weight: 500;">{{ detail.offlineReason }}</dd></div>
            </dl>
          </section>

          <!-- Disaster Recovery Section -->
          <section class="architecture-drawer-section">
            <header style="display: flex; justify-content: space-between; align-items: center;">
              <div>
                <strong>灾备拓扑关系</strong>
                <span class="architecture-muted" style="margin-left: 8px;">同一部署单元 ({{ detail.deploymentUnitName }}) 内的主备/双活</span>
              </div>
              <el-button v-if="canManage && detail.status === 'ACTIVE'" type="primary" size="small" :icon="Plus" @click="openCreateDrDialog(detail)">
                建立灾备关系
              </el-button>
            </header>

            <div v-loading="drLoading" style="margin-top: 12px;">
              <UiEmptyState v-if="!instanceDrs.length" title="暂无关联灾备实例" description="当前实例尚未与其他同部署单元实例建立主备/双活拓扑关系。" />
              <div v-else style="display: flex; flex-direction: column; gap: 12px;">
                <div
                  v-for="dr in instanceDrs"
                  :key="dr.id"
                  style="border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 12px 16px; display: flex; justify-content: space-between; align-items: center;"
                >
                  <div>
                    <div style="display: flex; align-items: center; gap: 8px;">
                      <el-tag size="small" type="success">{{ drModeLabels[dr.drMode] || dr.drMode }}</el-tag>
                      <strong>主: {{ dr.primaryMachineName }} ({{ dr.primaryIpAddress }})</strong>
                      <span>⇄</span>
                      <strong>备: {{ dr.standbyMachineName }} ({{ dr.standbyIpAddress }})</strong>
                    </div>
                    <div style="font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px;">
                      环境: {{ dr.primaryEnvironmentName }} → {{ dr.standbyEnvironmentName }} | 说明: {{ dr.description || '—' }}
                    </div>
                  </div>
                  <el-button v-if="canManage" link type="danger" size="small" @click="handleRemoveDr(dr)">解除</el-button>
                </div>
              </div>
            </div>
          </section>

          <!-- Drawer Actions -->
          <div v-if="canManage && detail.status === 'ACTIVE'" class="architecture-drawer-actions architecture-drawer-section">
            <el-button type="danger" plain @click="openOfflineDialog(detail)">实例下线</el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- Offline Modal -->
    <el-dialog v-model="offlineDialogVisible" title="环境部署实例下线" width="min(520px, 94vw)" destroy-on-close>
      <div v-if="targetOfflineInstance">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 16px;"
          :title="offlineRisks.length ? `下线风险警示：存在 ${offlineRisks.length} 项灾备关系` : '下线风险警示'"
          description="实例下线后将释放该机器的资源分配指标，该机器名及IP在具体环境中将被标记为历史下线事实。若存在关联灾备关系，请确认是否同步调整灾备拓扑。"
        />
        <div v-if="offlineRiskLoading || offlineRisks.length" v-loading="offlineRiskLoading" class="architecture-offline-risk-list">
          <strong>关联灾备关系 {{ offlineRisks.length }} 项</strong>
          <p v-for="risk in offlineRisks" :key="risk.id">
            {{ drModeLabels[risk.drMode] || risk.drMode }}：{{ risk.primaryMachineName }} ({{ risk.primaryIpAddress }}) → {{ risk.standbyMachineName }} ({{ risk.standbyIpAddress }})
          </p>
        </div>
        <el-form label-position="top">
          <el-form-item label="待下线实例">
            <el-input :model-value="`${targetOfflineInstance.machineName} (${targetOfflineInstance.ipAddress}) - ${targetOfflineInstance.environmentName}`" disabled />
          </el-form-item>
          <el-form-item label="下线原因 (必填)" required>
            <el-input v-model="offlineReason" type="textarea" :rows="3" placeholder="请输入实例下线的原因与背景（如：业务缩容、机器置换、退役等）" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="offlineDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="offlineSubmitting" @click="confirmOffline">确认下线</el-button>
      </template>
    </el-dialog>

    <!-- Create Disaster Recovery Dialog -->
    <el-dialog v-model="drDialogVisible" title="建立同部署单元灾备关系" width="min(560px, 94vw)" destroy-on-close>
      <el-form v-loading="standbysLoading" label-position="top">
        <el-form-item label="主实例">
          <el-input :model-value="`${detail?.machineName} (${detail?.ipAddress}) - ${detail?.environmentName}`" disabled />
        </el-form-item>
        <el-form-item label="备用实例" required>
          <el-select v-model="drForm.standbyInstanceId" placeholder="选择同部署单元的在用备用实例" filterable style="width: 100%;">
            <el-option
              v-for="s in availableStandbys"
              :key="s.id"
              :label="`${s.machineName} (${s.ipAddress}) - ${s.environmentName} [${s.environmentCode}]`"
              :value="s.id"
            />
          </el-select>
          <small v-if="!availableStandbys.length && !standbysLoading" style="color: var(--el-color-warning); margin-top: 4px; display: block;">
            未找到同部署单元的其他在用实例。需先在同环境或灾备环境中下发同部署单元的实例。
          </small>
        </el-form-item>
        <el-form-item label="灾备模式" required>
          <el-radio-group v-model="drForm.drMode">
            <el-radio label="PRIMARY_STANDBY">主备 (PRIMARY_STANDBY)</el-radio>
            <el-radio label="ACTIVE_ACTIVE">双活 (ACTIVE_ACTIVE)</el-radio>
            <el-radio label="COLD_STANDBY">冷备 (COLD_STANDBY)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="灾备关系说明">
          <el-input v-model="drForm.description" type="textarea" :rows="2" placeholder="如：同城双活节点对、主备机同步等" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="drSubmitting" :disabled="!availableStandbys.length" @click="submitCreateDr">建立灾备</el-button>
      </template>
    </el-dialog>
  </main>
</template>
