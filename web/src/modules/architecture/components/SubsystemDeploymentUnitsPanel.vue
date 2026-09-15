<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, View } from '@element-plus/icons-vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import { apiErrorMessage } from '../../../api/error'
import { useAuthStore } from '../../../stores/auth'
import { useProjectContextStore } from '../../../stores/project-context'
import { getDeploymentUnit, getDeploymentUnitVersions, listDeploymentUnits, listEnvironmentInstances, listEnvironments } from '../api'
import DeploymentUnitDetailDrawer from './DeploymentUnitDetailDrawer.vue'
import type {
  DeploymentUnit,
  DeploymentUnitVersion,
  Environment,
  EnvironmentInstance,
  InstanceStatus,
  PhysicalSubsystem
} from '../types'
import {
  deploymentUnitKindLabels,
  deploymentUnitStatusLabels,
  deploymentUnitStatusTone,
  formatDateTime,
  httpStatus
} from '../utils'

const props = defineProps<{ subsystem: PhysicalSubsystem | null }>()
const auth = useAuthStore()
const project = useProjectContextStore()

const units = ref<DeploymentUnit[]>([])
const unitsLoading = ref(false)
const unitsError = ref('')
const unitsForbidden = ref(false)
const environments = ref<Environment[]>([])
const environmentsError = ref('')
const selectedEnvironmentId = ref(0) // 0 表示「全部环境」
const instances = ref<EnvironmentInstance[]>([])
const instancesLoading = ref(false)
const instancesError = ref('')
const instancesForbidden = ref(false)
let unitsRequest = 0
let instancesRequest = 0

const instanceStatusLabels: Record<InstanceStatus, string> = { ACTIVE: '在用', OFFLINE: '已下线' }
function instanceStatusTone(status: InstanceStatus) {
  return status === 'ACTIVE' ? 'success' as const : 'info' as const
}

const selectedEnvironmentLabel = computed(() => {
  if (!selectedEnvironmentId.value) return '全部环境'
  return environments.value.find(item => item.id === selectedEnvironmentId.value)?.name || '所选环境'
})
const instancesByUnit = computed(() => {
  const map = new Map<number, EnvironmentInstance[]>()
  instances.value.forEach(instance => {
    const list = map.get(instance.deploymentUnitId)
    if (list) list.push(instance)
    else map.set(instance.deploymentUnitId, [instance])
  })
  return map
})
const canViewInstances = computed(() => ['architecture:instance:view', 'architecture:instance:manage', 'architecture:view', 'architecture:manage']
  .some(permission => auth.hasPermission(permission)))

async function fetchAllUnits(subsystemId: number) {
  const size = 100
  const all: DeploymentUnit[] = []
  for (let page = 1; page <= 20; page++) {
    const result = await listDeploymentUnits({ page, size, physicalSubsystemId: subsystemId })
    all.push(...result.records)
    if (result.records.length < size || all.length >= result.total) break
  }
  return all
}

async function fetchAllInstances(subsystemId: number, environmentId: number) {
  const limit = 200
  const all: EnvironmentInstance[] = []
  for (let offset = 0; offset < 2000; offset += limit) {
    const result = await listEnvironmentInstances({
      physicalSubsystemId: subsystemId,
      environmentId: environmentId || undefined,
      limit,
      offset
    })
    all.push(...result)
    if (result.length < limit) break
  }
  return all
}

async function loadUnits() {
  if (!props.subsystem || !project.currentRef) return
  const subsystemId = props.subsystem.id
  const projectRef = project.currentRef
  const ticket = ++unitsRequest
  unitsLoading.value = true
  unitsError.value = ''
  unitsForbidden.value = false
  try {
    const result = await fetchAllUnits(subsystemId)
    if (ticket !== unitsRequest || project.currentRef !== projectRef) return
    units.value = result
  } catch (error) {
    if (ticket !== unitsRequest) return
    if (httpStatus(error) === 403) unitsForbidden.value = true
    else unitsError.value = apiErrorMessage(error, '部署单元加载失败')
  } finally { if (ticket === unitsRequest) unitsLoading.value = false }
}

async function loadEnvironments() {
  try {
    environments.value = await listEnvironments({ status: 'ACTIVE', limit: 100, offset: 0 })
    environmentsError.value = ''
  } catch (error) {
    if (httpStatus(error) !== 403) environmentsError.value = apiErrorMessage(error, '环境列表加载失败')
  }
}

async function loadInstances() {
  if (!props.subsystem || !project.currentRef || !canViewInstances.value) return
  const subsystemId = props.subsystem.id
  const projectRef = project.currentRef
  const environmentId = selectedEnvironmentId.value
  const ticket = ++instancesRequest
  instancesLoading.value = true
  instancesError.value = ''
  instancesForbidden.value = false
  try {
    const result = await fetchAllInstances(subsystemId, environmentId)
    if (ticket !== instancesRequest || project.currentRef !== projectRef) return
    instances.value = result
  } catch (error) {
    if (ticket !== instancesRequest) return
    if (httpStatus(error) === 403) instancesForbidden.value = true
    else instancesError.value = apiErrorMessage(error, '环境部署实例加载失败')
  } finally { if (ticket === instancesRequest) instancesLoading.value = false }
}

function reload() {
  void loadUnits()
  void loadInstances()
}

// ---------- 只读部署单元详情 ----------
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<DeploymentUnit | null>(null)
const versions = ref<DeploymentUnitVersion[]>([])
const versionsLoading = ref(false)
let detailRequest = 0
let versionsRequest = 0

async function openUnit(unit: DeploymentUnit) {
  const ticket = ++detailRequest
  detail.value = unit
  detailOpen.value = true
  detailLoading.value = true
  versions.value = []
  try {
    const result = await getDeploymentUnit(unit.id)
    if (ticket === detailRequest) detail.value = result
  } catch (error) {
    if (ticket === detailRequest) ElMessage.error(apiErrorMessage(error, '部署单元详情加载失败'))
  } finally { if (ticket === detailRequest) detailLoading.value = false }
  void loadVersions(unit.id)
}

async function loadVersions(id: number) {
  const ticket = ++versionsRequest
  versionsLoading.value = true
  try {
    const result = await getDeploymentUnitVersions(id)
    if (ticket === versionsRequest) versions.value = result
  } catch (error) {
    if (ticket === versionsRequest) ElMessage.error(apiErrorMessage(error, '部署单元版本历史加载失败'))
  } finally { if (ticket === versionsRequest) versionsLoading.value = false }
}

watch(() => [props.subsystem?.id, project.currentRef] as const, () => {
  ++unitsRequest
  ++instancesRequest
  units.value = []
  instances.value = []
  environments.value = []
  selectedEnvironmentId.value = 0
  unitsError.value = ''
  unitsForbidden.value = false
  instancesError.value = ''
  instancesForbidden.value = false
  environmentsError.value = ''
  if (props.subsystem?.id && project.currentRef) {
    void loadUnits()
    void loadEnvironments()
    void loadInstances()
  }
}, { immediate: true })
</script>

<template>
  <section class="subsystem-units" aria-label="物理子系统部署单元">
    <el-result v-if="unitsForbidden" icon="warning" title="暂无部署单元查看权限" sub-title="请申请 architecture:view 或部署单元相关权限。" />
    <template v-else>
      <div class="subsystem-units__toolbar">
        <div class="subsystem-units__environments">
          <span class="subsystem-units__label">环境</span>
          <el-radio-group v-model="selectedEnvironmentId" :disabled="instancesLoading || instancesForbidden || !canViewInstances" @change="loadInstances">
            <el-radio-button :value="0">全部</el-radio-button>
            <el-radio-button v-for="environment in environments" :key="environment.id" :value="environment.id">
              {{ environment.name }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <el-tooltip content="刷新部署单元与环境部署实例">
          <el-button circle :loading="unitsLoading || instancesLoading" aria-label="刷新部署单元与环境部署实例" @click="reload">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
      <p v-if="environmentsError" class="architecture-field-error">{{ environmentsError }}</p>
      <el-alert v-if="!canViewInstances" type="info" show-icon :closable="false" title="当前账号无环境部署实例查看权限，仅展示部署单元。" />
      <el-alert v-else-if="instancesForbidden" type="warning" show-icon :closable="false" :title="'暂无环境部署实例查看权限（' + selectedEnvironmentLabel + '）。'" />

      <el-alert v-if="unitsError" type="error" show-icon :closable="false" :title="unitsError" />

      <div v-loading="unitsLoading" class="subsystem-units__list">
        <UiEmptyState v-if="!unitsLoading && !units.length && !unitsError" title="暂无部署单元" description="该物理子系统当前没有部署单元记录。" />
        <article v-for="unit in units" :key="unit.id" class="subsystem-unit-card">
          <header>
            <div class="subsystem-unit-card__identity">
              <strong>{{ unit.name }}</strong>
              <small>{{ unit.code || '—' }} · {{ deploymentUnitKindLabels[unit.kind] }} · 更新 {{ formatDateTime(unit.updatedAt) }}</small>
            </div>
            <div class="subsystem-unit-card__meta">
              <UiStatusTag :value="unit.status" :labels="deploymentUnitStatusLabels" :tone="deploymentUnitStatusTone(unit.status)" />
              <el-button link type="primary" @click="openUnit(unit)"><el-icon><View /></el-icon>详情</el-button>
            </div>
          </header>
          <dl class="subsystem-unit-card__facts">
            <div><dt>当前版本</dt><dd>{{ unit.currentVersion ? `v${unit.currentVersion}` : '—' }}</dd></div>
            <div><dt>默认网络分区</dt><dd>{{ unit.defaultNetworkZoneName || '—' }}</dd></div>
            <div><dt>关联交付单元</dt><dd>{{ unit.relatedDeploymentUnits.length }} 个</dd></div>
          </dl>
          <section class="subsystem-unit-instances" :aria-label="`${unit.name} 环境部署实例`">
            <header>
              <strong>{{ selectedEnvironmentLabel }} · 环境部署实例</strong>
              <span>{{ (instancesByUnit.get(unit.id) || []).length }} 个</span>
            </header>
            <div v-if="instancesLoading" class="subsystem-unit-instances__loading" v-loading="true" />
            <el-empty
              v-else-if="!instancesError && !instancesForbidden && canViewInstances && !(instancesByUnit.get(unit.id) || []).length"
              :image-size="42"
              description="该环境下暂无部署实例"
            />
            <ul v-else-if="canViewInstances && !instancesForbidden && !instancesError" class="subsystem-instance-list">
              <li v-for="instance in instancesByUnit.get(unit.id) || []" :key="instance.id">
                <div class="subsystem-instance-list__identity">
                  <strong>{{ instance.machineName }}</strong>
                  <small>{{ instance.ipAddress }} · {{ instance.instanceNo }}</small>
                </div>
                <div class="subsystem-instance-list__meta">
                  <span class="subsystem-instance-list__env">{{ instance.environmentName }}</span>
                  <el-tag size="small" :type="instance.hasVersionDifference ? 'warning' : 'info'">
                    v{{ instance.deploymentUnitVersionNo }}<template v-if="instance.hasVersionDifference">（最新 v{{ instance.latestDeploymentUnitVersionNo }}）</template>
                  </el-tag>
                  <span class="subsystem-instance-list__zone">{{ instance.networkZoneName || instance.networkZone || '—' }}</span>
                  <UiStatusTag :value="instance.status" :labels="instanceStatusLabels" :tone="instanceStatusTone(instance.status)" />
                </div>
              </li>
            </ul>
          </section>
        </article>
      </div>
      <p v-if="instancesError" class="architecture-field-error">{{ instancesError }}，部署单元列表仍可用。</p>
    </template>

    <DeploymentUnitDetailDrawer
      v-model="detailOpen"
      readonly
      :loading="detailLoading"
      :title="detail?.name || '部署单元详情'"
      :unit="detail"
      :versions="versions"
      :versions-loading="versionsLoading"
    />
  </section>
</template>

<style scoped>
.subsystem-units { display: grid; min-width: 0; gap: 14px; }
.subsystem-units__toolbar { display: flex; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 12px; }
.subsystem-units__environments { display: flex; min-width: 0; align-items: center; flex-wrap: wrap; gap: 8px; }
.subsystem-units__label { flex-shrink: 0; color: var(--muted); font-size: 12px; }
.subsystem-units__environments .el-radio-group { flex-wrap: wrap; gap: 6px 0; }
.subsystem-units__list { display: grid; min-width: 0; gap: 12px; }
.subsystem-unit-card { display: grid; min-width: 0; gap: 12px; padding: 14px; background: var(--panel-bg); border: 1px solid var(--line); border-radius: 8px; }
.subsystem-unit-card > header { display: flex; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 10px; }
.subsystem-unit-card__identity { min-width: 0; }
.subsystem-unit-card__identity strong { display: block; overflow-wrap: anywhere; color: var(--text); font-size: 14px; }
.subsystem-unit-card__identity small { display: block; margin-top: 4px; overflow-wrap: anywhere; color: var(--muted); font-size: 11px; }
.subsystem-unit-card__meta { display: flex; flex-shrink: 0; align-items: center; gap: 8px; }
.subsystem-unit-card__facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin: 0; }
.subsystem-unit-card__facts div { min-width: 0; }
.subsystem-unit-card__facts dt { color: var(--muted); font-size: 11px; }
.subsystem-unit-card__facts dd { margin: 4px 0 0; overflow-wrap: anywhere; color: var(--text); font-size: 12px; }
.subsystem-unit-instances { display: grid; min-width: 0; gap: 8px; padding-top: 10px; border-top: 1px dashed var(--line); }
.subsystem-unit-instances > header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.subsystem-unit-instances > header strong { color: var(--text); font-size: 12px; }
.subsystem-unit-instances > header span { color: var(--muted); font-size: 11px; }
.subsystem-unit-instances__loading { min-height: 60px; }
.subsystem-instance-list { display: grid; gap: 6px; margin: 0; padding: 0; list-style: none; }
.subsystem-instance-list li { display: flex; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 10px; padding: 8px 10px; background: color-mix(in srgb, var(--brand) 5%, var(--panel-bg)); border: 1px solid var(--line); border-radius: 6px; }
.subsystem-instance-list__identity { min-width: 0; }
.subsystem-instance-list__identity strong { display: block; overflow-wrap: anywhere; color: var(--text); font-size: 12px; }
.subsystem-instance-list__identity small { display: block; margin-top: 3px; overflow-wrap: anywhere; color: var(--muted); font-size: 11px; }
.subsystem-instance-list__meta { display: flex; flex-shrink: 0; align-items: center; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.subsystem-instance-list__env,
.subsystem-instance-list__zone { color: var(--muted); font-size: 11px; }

@media (max-width: 640px) {
  .subsystem-units__toolbar { flex-direction: column; align-items: stretch; }
  .subsystem-unit-card__facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .subsystem-instance-list li { flex-direction: column; align-items: stretch; }
  .subsystem-instance-list__meta { justify-content: flex-start; }
}
</style>
