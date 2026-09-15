<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, View } from '@element-plus/icons-vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import { apiErrorMessage } from '../../../api/error'
import { useProjectContextStore } from '../../../stores/project-context'
import { getDeliveryUnit, listDeliveryUnits, loadParameterOptions } from '../api'
import DeliveryUnitDetailDrawer from './DeliveryUnitDetailDrawer.vue'
import type { DeliveryUnit, ParameterOption, PhysicalSubsystem } from '../types'
import { deliveryUnitStatusLabels, deliveryUnitStatusTone, formatDateTime, httpStatus, optionLabel } from '../utils'

const props = defineProps<{ subsystem: PhysicalSubsystem | null }>()
const project = useProjectContextStore()

const units = ref<DeliveryUnit[]>([])
const unitsLoading = ref(false)
const unitsError = ref('')
const unitsForbidden = ref(false)
const artifactTypeOptions = ref<ParameterOption[]>([])
let unitsRequest = 0

async function fetchAllUnits(subsystemId: number) {
  const size = 100
  const all: DeliveryUnit[] = []
  for (let page = 1; page <= 20; page++) {
    const result = await listDeliveryUnits({ page, size, physicalSubsystemId: subsystemId })
    all.push(...result.records)
    if (result.records.length < size || all.length >= result.total) break
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
    else unitsError.value = apiErrorMessage(error, '交付单元加载失败')
  } finally { if (ticket === unitsRequest) unitsLoading.value = false }
}

async function loadArtifactTypes() {
  try {
    artifactTypeOptions.value = await loadParameterOptions('delivery-unit', 'ARCH_ARTIFACT_TYPE')
  } catch {
    artifactTypeOptions.value = []
  }
}

// ---------- 只读交付单元详情（不传 canManage，隐藏维护动作） ----------
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<DeliveryUnit | null>(null)
let detailRequest = 0

async function openUnit(unit: DeliveryUnit) {
  const ticket = ++detailRequest
  detail.value = unit
  detailOpen.value = true
  detailLoading.value = true
  try {
    const result = await getDeliveryUnit(unit.id)
    if (ticket === detailRequest) detail.value = result
  } catch (error) {
    if (ticket === detailRequest) ElMessage.error(apiErrorMessage(error, '交付单元详情加载失败'))
  } finally { if (ticket === detailRequest) detailLoading.value = false }
}

watch(() => [props.subsystem?.id, project.currentRef] as const, () => {
  ++unitsRequest
  units.value = []
  unitsError.value = ''
  unitsForbidden.value = false
  if (props.subsystem?.id && project.currentRef) {
    void loadUnits()
    void loadArtifactTypes()
  }
}, { immediate: true })
</script>

<template>
  <section class="subsystem-deliveries" aria-label="物理子系统交付单元">
    <el-result v-if="unitsForbidden" icon="warning" title="暂无交付单元查看权限" sub-title="请申请 architecture:view 或交付单元相关权限。" />
    <template v-else>
      <div class="subsystem-deliveries__toolbar">
        <span class="subsystem-deliveries__count">共 {{ units.length }} 个交付单元</span>
        <el-tooltip content="刷新交付单元">
          <el-button circle :loading="unitsLoading" aria-label="刷新交付单元" @click="loadUnits">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
      <el-alert v-if="unitsError" type="error" show-icon :closable="false" :title="unitsError" />
      <div v-loading="unitsLoading" class="subsystem-deliveries__list">
        <UiEmptyState v-if="!unitsLoading && !units.length && !unitsError" title="暂无交付单元" description="该物理子系统当前没有交付单元记录。" />
        <article v-for="unit in units" :key="unit.id" class="subsystem-delivery-card">
          <header>
            <div class="subsystem-delivery-card__identity">
              <strong>{{ unit.name }}</strong>
              <small>{{ unit.code }} · {{ optionLabel(artifactTypeOptions, unit.artifactTypeCode) }}</small>
            </div>
            <div class="subsystem-delivery-card__meta">
              <UiStatusTag :value="unit.status" :labels="deliveryUnitStatusLabels" :tone="deliveryUnitStatusTone(unit.status)" />
              <el-button link type="primary" @click="openUnit(unit)"><el-icon><View /></el-icon>详情</el-button>
            </div>
          </header>
          <dl class="subsystem-delivery-card__facts">
            <div><dt>关联部署单元</dt><dd>{{ unit.relatedDeploymentUnits.length }} 个</dd></div>
            <div><dt>最后更新</dt><dd>{{ formatDateTime(unit.updatedAt) }}</dd></div>
          </dl>
        </article>
      </div>
    </template>

    <DeliveryUnitDetailDrawer
      v-model="detailOpen"
      :loading="detailLoading"
      :title="detail?.name || '交付单元详情'"
      :unit="detail"
      :artifact-type-options="artifactTypeOptions"
    />
  </section>
</template>

<style scoped>
.subsystem-deliveries { display: grid; min-width: 0; gap: 14px; }
.subsystem-deliveries__toolbar { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; }
.subsystem-deliveries__count { color: var(--muted); font-size: 12px; }
.subsystem-deliveries__list { display: grid; min-width: 0; gap: 10px; }
.subsystem-delivery-card { display: grid; min-width: 0; gap: 12px; padding: 14px; background: var(--panel-bg); border: 1px solid var(--line); border-radius: 8px; }
.subsystem-delivery-card > header { display: flex; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 10px; }
.subsystem-delivery-card__identity { min-width: 0; }
.subsystem-delivery-card__identity strong { display: block; overflow-wrap: anywhere; color: var(--text); font-size: 14px; }
.subsystem-delivery-card__identity small { display: block; margin-top: 4px; overflow-wrap: anywhere; color: var(--muted); font-size: 11px; }
.subsystem-delivery-card__meta { display: flex; flex-shrink: 0; align-items: center; gap: 8px; }
.subsystem-delivery-card__facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin: 0; }
.subsystem-delivery-card__facts dt { color: var(--muted); font-size: 11px; }
.subsystem-delivery-card__facts dd { margin: 4px 0 0; overflow-wrap: anywhere; color: var(--text); font-size: 12px; }
</style>
