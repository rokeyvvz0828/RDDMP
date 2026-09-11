<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import { apiErrorMessage } from '../../../api/error'
import { listDeploymentUnitDeliveryUnits, replaceDeploymentUnitDeliveryUnits, searchDeploymentUnitDeliveryUnitOptions } from '../api'
import type { DeploymentUnit, DeploymentUnitVersion, RelatedDeliveryUnit } from '../types'
import { deliveryUnitStatusLabels, deploymentUnitKindLabels, deploymentUnitStatusLabels, deploymentUnitStatusTone, formatDateTime } from '../utils'
import '../architecture.css'

const props = defineProps<{
  modelValue: boolean
  loading?: boolean
  title: string
  unit: DeploymentUnit | null
  versions: DeploymentUnitVersion[]
  versionsLoading?: boolean
  canManageRelations?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'edit'): void
  (event: 'deactivate'): void
  (event: 'reactivate'): void
  (event: 'void'): void
  (event: 'updated'): void
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

// ---------- 关联交付单元（只读反查） ----------

const relatedDeliveryUnits = ref<RelatedDeliveryUnit[]>([])
const relatedDeliveryUnitsLoading = ref(false)
const relatedDeliveryUnitsError = ref('')
let relatedDeliveryUnitsRequest = 0

async function loadRelatedDeliveryUnits() {
  const id = props.unit?.id
  if (!id) {
    relatedDeliveryUnits.value = []
    relatedDeliveryUnitsError.value = ''
    return
  }
  const request = ++relatedDeliveryUnitsRequest
  relatedDeliveryUnitsLoading.value = true
  relatedDeliveryUnitsError.value = ''
  try {
    const result = await listDeploymentUnitDeliveryUnits(id)
    if (request === relatedDeliveryUnitsRequest) relatedDeliveryUnits.value = result
  } catch (error) {
    if (request === relatedDeliveryUnitsRequest) {
      relatedDeliveryUnits.value = []
      relatedDeliveryUnitsError.value = apiErrorMessage(error, '关联交付单元加载失败')
    }
  } finally {
    if (request === relatedDeliveryUnitsRequest) relatedDeliveryUnitsLoading.value = false
  }
}

// ---------- 关联交付单元（两侧共享同一张关系表） ----------

const relationEditing = ref(false)
const relationSaving = ref(false)
const selectedDeliveryUnitIds = ref<number[]>([])
const deliveryUnitOptions = ref<RelatedDeliveryUnit[]>([])
const deliveryUnitOptionsLoading = ref(false)
const deliveryUnitOptionsError = ref('')
let deliveryUnitOptionsRequest = 0

function canEditRelations() {
  return Boolean(props.canManageRelations) && props.unit?.status === 'ACTIVE'
}

function mergeDeliveryUnitOptions(items: RelatedDeliveryUnit[]) {
  const selected = new Set(selectedDeliveryUnitIds.value)
  const retained = deliveryUnitOptions.value.filter(option => selected.has(option.id))
  const merged = new Map(retained.map(option => [option.id, option]))
  items.forEach(option => merged.set(option.id, option))
  deliveryUnitOptions.value = [...merged.values()]
}

async function searchDeliveryUnitOptions(keyword = '') {
  const id = props.unit?.id
  if (!id) return
  const request = ++deliveryUnitOptionsRequest
  deliveryUnitOptionsLoading.value = true
  deliveryUnitOptionsError.value = ''
  try {
    const result = await searchDeploymentUnitDeliveryUnitOptions({ deploymentUnitId: id, keyword, page: 1, size: 50 })
    if (request !== deliveryUnitOptionsRequest) return
    mergeDeliveryUnitOptions(result.records)
  } catch (error) {
    if (request !== deliveryUnitOptionsRequest) return
    deliveryUnitOptionsError.value = apiErrorMessage(error, '同物理子系统交付单元搜索失败')
  } finally {
    if (request === deliveryUnitOptionsRequest) deliveryUnitOptionsLoading.value = false
  }
}

function startRelationEdit() {
  if (!props.unit) return
  selectedDeliveryUnitIds.value = relatedDeliveryUnits.value.map(item => item.id)
  deliveryUnitOptions.value = [...relatedDeliveryUnits.value]
  deliveryUnitOptionsError.value = ''
  relationEditing.value = true
  void searchDeliveryUnitOptions()
}

function cancelRelationEdit() {
  relationEditing.value = false
  relationSaving.value = false
  selectedDeliveryUnitIds.value = []
  deliveryUnitOptions.value = []
  deliveryUnitOptionsError.value = ''
}

async function saveRelation() {
  const id = props.unit?.id
  if (!id || relationSaving.value) return
  relationSaving.value = true
  try {
    await replaceDeploymentUnitDeliveryUnits(id, selectedDeliveryUnitIds.value)
    ElMessage.success('关联交付单元已保存')
    cancelRelationEdit()
    await loadRelatedDeliveryUnits()
    emit('updated')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '保存关联失败'))
  } finally {
    relationSaving.value = false
  }
}

// 必须在所有被引用的状态声明之后注册；immediate 会在 setup 期立即执行。
watch(() => props.unit?.id, () => { void loadRelatedDeliveryUnits(); cancelRelationEdit() }, { immediate: true })
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
          <header><strong>关联交付单元</strong><span class="architecture-muted">{{ relatedDeliveryUnits.length }} 个 · 仅同一物理子系统</span></header>
          <template v-if="relationEditing">
            <el-select
              v-model="selectedDeliveryUnitIds"
              multiple
              filterable
              remote
              reserve-keyword
              collapse-tags
              collapse-tags-tooltip
              :loading="deliveryUnitOptionsLoading"
              :remote-method="searchDeliveryUnitOptions"
              placeholder="按名称或编号搜索，可多选"
              class="architecture-related-unit-select"
            >
              <el-option v-for="option in deliveryUnitOptions" :key="option.id" :label="`${option.name}（${option.code}）`" :value="option.id" />
              <!-- remote 选择器必须在空结果时提供 empty 插槽，否则下拉会自动收起 -->
              <template #empty><p class="el-select-dropdown__empty">{{ deliveryUnitOptionsError || '当前物理子系统下没有匹配的启用交付单元' }}</p></template>
            </el-select>
            <p v-if="deliveryUnitOptionsError" class="architecture-field-error">{{ deliveryUnitOptionsError }}，已选项已保留，可重新输入关键字重试。</p>
            <p class="architecture-form-hint">保存后以当前选择覆盖原有关联；与交付单元详情抽屉共享同一份关系数据，不会发布部署单元新版本。</p>
            <div class="architecture-drawer-actions architecture-relation-actions">
              <el-button :disabled="relationSaving" @click="cancelRelationEdit">取消</el-button>
              <el-button type="primary" :loading="relationSaving" @click="saveRelation">保存关联</el-button>
            </div>
          </template>
          <template v-else>
            <div v-loading="relatedDeliveryUnitsLoading" class="architecture-related-unit-list">
              <p v-if="relatedDeliveryUnitsError" class="architecture-field-error">{{ relatedDeliveryUnitsError }}</p>
              <el-empty v-else-if="!relatedDeliveryUnitsLoading && !relatedDeliveryUnits.length" description="暂无关联交付单元" :image-size="56" />
              <article v-for="delivery in relatedDeliveryUnits" :key="delivery.id">
                <div><strong>{{ delivery.name }}</strong><small>{{ delivery.code }}</small></div>
                <UiStatusTag :value="delivery.status" :labels="deliveryUnitStatusLabels" :tone="delivery.status === 'ACTIVE' ? 'success' : 'warning'" />
              </article>
            </div>
            <div v-if="canEditRelations()" class="architecture-drawer-actions architecture-relation-actions">
              <el-button plain @click="startRelationEdit">编辑关联</el-button>
            </div>
            <p v-else-if="canManageRelations && unit?.status !== 'ACTIVE'" class="architecture-form-hint">已停用或已作废部署单元不能调整关联，请先重新启用。</p>
          </template>
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
