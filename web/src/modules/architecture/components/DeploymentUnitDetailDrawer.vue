<script setup lang="ts">
import { computed } from 'vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import type { DeploymentUnit, DeploymentUnitVersion } from '../types'
import { deploymentUnitKindLabels, deploymentUnitStatusLabels, deploymentUnitStatusTone, formatDateTime } from '../utils'
import '../architecture.css'

const props = defineProps<{
  modelValue: boolean
  loading?: boolean
  title: string
  unit: DeploymentUnit | null
  versions: DeploymentUnitVersion[]
  versionsLoading?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'edit'): void
  (event: 'deactivate'): void
  (event: 'reactivate'): void
  (event: 'void'): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const kindLabel = computed(() => props.unit ? deploymentUnitKindLabels[props.unit.kind] : '—')

function item(label: string, value: string | null | undefined, wide = false, tone?: 'warning' | 'danger') {
  return { label, value: value || '—', wide, tone }
}

const items = computed(() => props.unit ? [
  item('部署单元编号', props.unit.code),
  item('发布状态', deploymentUnitStatusLabels[props.unit.status], false, props.unit.status === 'VOIDED' ? 'danger' : props.unit.status === 'INACTIVE' ? 'warning' : undefined),
  item('当前版本', props.unit.currentVersion ? `v${props.unit.currentVersion}` : '—'),
  item('部署单元类型', kindLabel.value),
  item('默认网络分区', props.unit.defaultNetworkZoneName),
  item('部署单元名称', props.unit.name),
  item('所属物理子系统', props.unit.physicalSubsystemCode ? `${props.unit.physicalSubsystemName}（${props.unit.physicalSubsystemCode}）` : '—'),
  item('物理子系统状态', props.unit.physicalSubsystemStatus ? deploymentUnitStatusLabels[props.unit.physicalSubsystemStatus as keyof typeof deploymentUnitStatusLabels] || props.unit.physicalSubsystemStatus : '—'),
  item('创建人', props.unit.createdByDisplayName || `用户 #${props.unit.createdBy}`),
  item('创建时间', formatDateTime(props.unit.createdAt)),
  item('最后更新', formatDateTime(props.unit.updatedAt)),
  item('数据版本', String(props.unit.rowVersion)),
  item('描述', props.unit.description, true),
  item('备注', props.unit.remark, true)
] : [])

function canDeactivate() {
  return props.unit?.status === 'ACTIVE'
}
function canReactivate() {
  return props.unit?.status === 'INACTIVE'
}
function canVoid() {
  return props.unit?.status === 'ACTIVE' || props.unit?.status === 'INACTIVE'
}
function canEdit() {
  return props.unit?.status === 'ACTIVE'
}
</script>

<template>
  <el-drawer v-model="open" :title="title" size="min(520px, 94vw)" destroy-on-close>
    <div v-loading="loading" class="architecture-drawer-body">
      <template v-if="unit">
        <dl class="architecture-detail-list">
          <div v-for="row in items" :key="row.label" :class="{ 'is-wide': row.wide }">
            <dt>{{ row.label }}</dt>
            <dd :class="row.tone ? `is-${row.tone}` : ''">{{ row.value }}</dd>
          </div>
        </dl>

        <section class="architecture-drawer-section">
          <header><strong>关联部署单元</strong><span class="architecture-muted">{{ unit.relatedDeploymentUnits.length }} 个</span></header>
          <el-empty v-if="!unit.relatedDeploymentUnits.length" description="暂无关联部署单元" :image-size="56" />
          <div v-else class="architecture-related-unit-list">
            <article v-for="related in unit.relatedDeploymentUnits" :key="related.id">
              <div><strong>{{ related.name }}</strong><small>{{ related.code }} · {{ related.physicalSubsystemName || '未知物理子系统' }}</small></div>
              <UiStatusTag :value="related.status" :labels="deploymentUnitStatusLabels" :tone="deploymentUnitStatusTone(related.status)" />
            </article>
          </div>
        </section>

        <section class="architecture-drawer-section">
          <header>
            <strong>发布版本历史</strong>
            <UiStatusTag v-if="unit" :value="unit.status" :labels="deploymentUnitStatusLabels" :tone="deploymentUnitStatusTone(unit.status)" />
          </header>
          <div v-loading="versionsLoading" class="architecture-version-timeline">
            <el-empty v-if="!versionsLoading && !versions.length" description="暂无版本记录" :image-size="64" />
            <el-timeline v-else>
              <el-timeline-item
                v-for="version in versions"
                :key="version.versionNo"
                :timestamp="formatDateTime(version.publishedAt)"
                placement="top"
                :type="version.versionNo === unit.currentVersion ? 'primary' : 'info'"
                :hollow="version.versionNo !== unit.currentVersion"
              >
                <div class="architecture-version-item">
                  <strong>v{{ version.versionNo }} · {{ version.name }}</strong>
                  <small>{{ deploymentUnitKindLabels[version.kind] }} · {{ version.defaultNetworkZoneName || '未设默认网络分区' }} · 发布人 {{ version.publishedByDisplayName }}</small>
                  <p v-if="version.description">{{ version.description }}</p>
                  <p v-if="version.remark" class="architecture-muted">备注：{{ version.remark }}</p>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </section>
      </template>
    </div>
    <template #footer>
      <div class="architecture-drawer-actions">
        <el-button v-if="canDeactivate()" type="warning" plain @click="emit('deactivate')">停用</el-button>
        <el-button v-if="canReactivate()" type="success" plain @click="emit('reactivate')">重新启用</el-button>
        <el-button v-if="canVoid()" type="danger" plain @click="emit('void')">作废</el-button>
        <el-button v-if="canEdit()" type="primary" @click="emit('edit')">修改并发布新版本</el-button>
      </div>
    </template>
  </el-drawer>
</template>
