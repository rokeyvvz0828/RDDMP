<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Edit, Check, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import { apiErrorMessage } from '../../../api/error'
import { replaceDeliveryUnitDeploymentUnits, searchDeliveryUnitDeploymentUnitOptions } from '../api'
import type { DeliveryUnit, ParameterOption, RelatedDeploymentUnit } from '../types'
import { deliveryUnitStatusLabels, deliveryUnitStatusTone, deploymentUnitKindLabels, deploymentUnitStatusLabels, formatDateTime, optionLabel } from '../utils'
import '../architecture.css'

const props = defineProps<{
  modelValue: boolean
  loading?: boolean
  title: string
  unit: DeliveryUnit | null
  canManage?: boolean
  artifactTypeOptions?: ParameterOption[]
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'edit'): void
  (event: 'deactivate'): void
  (event: 'reactivate'): void
  (event: 'delete'): void
  (event: 'updated', unit: DeliveryUnit): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const editing = ref(false)
const saving = ref(false)
const selectedIds = ref<number[]>([])
const relatedOptions = ref<RelatedDeploymentUnit[]>([])
const relatedLoading = ref(false)
const relatedError = ref('')
let relatedRequest = 0

watch(() => props.unit?.id, () => cancelEdit())

function item(label: string, value: string | null | undefined, wide = false, tone?: 'warning') {
  return { label, value: value || '—', wide, tone }
}

const items = computed(() => props.unit ? [
  item('交付单元编号', props.unit.code),
  item('制品类型', optionLabel(props.artifactTypeOptions || [], props.unit.artifactTypeCode)),
  item('状态', deliveryUnitStatusLabels[props.unit.status], false, props.unit.status === 'INACTIVE' ? 'warning' : undefined),
  item('归属物理子系统', props.unit.physicalSubsystemName
    ? `${props.unit.physicalSubsystemName}（${props.unit.physicalSubsystemCode}）` : '—'),
  item('物理子系统状态', props.unit.physicalSubsystemStatus),
  item('创建人', props.unit.createdByDisplayName || `用户 #${props.unit.createdBy}`),
  item('创建时间', formatDateTime(props.unit.createdAt)),
  item('最后更新', formatDateTime(props.unit.updatedAt)),
  item('数据版本', String(props.unit.rowVersion)),
  item('描述', props.unit.description, true),
  item('备注', props.unit.remark, true)
] : [])

function mergeRelatedOptions(items: RelatedDeploymentUnit[]) {
  const selected = new Set(selectedIds.value)
  const retained = relatedOptions.value.filter(option => selected.has(option.id))
  const merged = new Map(retained.map(option => [option.id, option]))
  items.forEach(option => merged.set(option.id, option))
  relatedOptions.value = [...merged.values()]
}

async function searchRelatedOptions(keyword = '') {
  if (!props.unit) return
  const request = ++relatedRequest
  relatedLoading.value = true
  relatedError.value = ''
  try {
    const result = await searchDeliveryUnitDeploymentUnitOptions({
      physicalSubsystemId: props.unit.physicalSubsystemId,
      keyword,
      page: 1,
      size: 50
    })
    if (request !== relatedRequest) return
    mergeRelatedOptions(result.records)
  } catch (error) {
    if (request !== relatedRequest) return
    relatedError.value = apiErrorMessage(error, '同物理子系统部署单元搜索失败')
  } finally {
    if (request === relatedRequest) relatedLoading.value = false
  }
}

function startEdit() {
  if (!props.unit) return
  selectedIds.value = props.unit.relatedDeploymentUnits.map(item => item.id)
  relatedOptions.value = [...props.unit.relatedDeploymentUnits]
  relatedError.value = ''
  editing.value = true
  void searchRelatedOptions()
}

function cancelEdit() {
  editing.value = false
  saving.value = false
  selectedIds.value = []
  relatedOptions.value = []
  relatedError.value = ''
}

async function saveRelations() {
  if (!props.unit || saving.value) return
  saving.value = true
  try {
    const updated = await replaceDeliveryUnitDeploymentUnits(props.unit.id, selectedIds.value)
    ElMessage.success('关联部署单元已保存')
    emit('updated', updated)
    cancelEdit()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '保存关联失败'))
  } finally {
    saving.value = false
  }
}

function canEditRelations() {
  return Boolean(props.canManage) && props.unit?.status === 'ACTIVE'
}
</script>

<template>
  <el-drawer v-model="open" :title="title" size="min(560px, 94vw)" destroy-on-close>
    <div v-loading="loading" class="architecture-drawer-body">
      <template v-if="unit">
        <dl class="architecture-detail-list">
          <div v-for="row in items" :key="row.label" :class="{ 'is-wide': row.wide }">
            <dt>{{ row.label }}</dt>
            <dd :class="row.tone ? `is-${row.tone}` : ''">{{ row.value }}</dd>
          </div>
        </dl>

        <section class="architecture-drawer-section">
          <header>
            <strong>关联部署单元</strong>
            <span class="architecture-muted">
              {{ unit.relatedDeploymentUnits.length }} 个 · 仅同一物理子系统
            </span>
          </header>

          <template v-if="editing">
            <el-select
              v-model="selectedIds"
              multiple
              filterable
              remote
              reserve-keyword
              collapse-tags
              collapse-tags-tooltip
              :loading="relatedLoading"
              :remote-method="searchRelatedOptions"
              placeholder="按名称或编号搜索，可多选"
              class="architecture-related-unit-select"
            >
              <el-option v-for="option in relatedOptions" :key="option.id" :label="`${option.name}（${option.code}）`" :value="option.id" />
              <!-- remote 选择器必须在空结果时提供 empty 插槽，否则下拉会自动收起 -->
              <template #empty><p class="el-select-dropdown__empty">{{ relatedError || '当前物理子系统下没有匹配的启用部署单元' }}</p></template>
            </el-select>
            <p v-if="relatedError" class="architecture-field-error">{{ relatedError }}，已选项已保留，可重新输入关键字重试。</p>
            <p class="architecture-form-hint">保存后以当前选择覆盖原有关联；被移除的部署单元不会受影响。</p>
            <div class="architecture-drawer-actions architecture-relation-actions">
              <el-button :disabled="saving" @click="cancelEdit"><el-icon><Close /></el-icon>取消</el-button>
              <el-button type="primary" :loading="saving" @click="saveRelations"><el-icon><Check /></el-icon>保存关联</el-button>
            </div>
          </template>
          <template v-else>
            <el-empty v-if="!unit.relatedDeploymentUnits.length" description="暂无关联部署单元" :image-size="56" />
            <div v-else class="architecture-related-unit-list">
              <article v-for="related in unit.relatedDeploymentUnits" :key="related.id">
                <div><strong>{{ related.name }}</strong><small>{{ related.code }} · {{ deploymentUnitKindLabels[related.kind] }}</small></div>
                <UiStatusTag :value="related.status" :labels="deploymentUnitStatusLabels" :tone="related.status === 'ACTIVE' ? 'success' : 'warning'" />
              </article>
            </div>
            <div v-if="canEditRelations()" class="architecture-drawer-actions architecture-relation-actions">
              <el-button plain @click="startEdit"><el-icon><Edit /></el-icon>编辑关联</el-button>
            </div>
            <p v-else-if="canManage && unit.status === 'INACTIVE'" class="architecture-form-hint">已停用交付单元不能调整关联，请先重新启用。</p>
          </template>
        </section>
      </template>
    </div>
    <template #footer>
      <div class="architecture-drawer-actions">
        <el-button v-if="canManage && unit?.status === 'ACTIVE'" type="warning" plain @click="emit('deactivate')">停用</el-button>
        <el-button v-if="canManage && unit?.status === 'INACTIVE'" type="success" plain @click="emit('reactivate')">重新启用</el-button>
        <el-button v-if="canManage" type="danger" plain @click="emit('delete')">删除</el-button>
        <el-button v-if="canManage" type="primary" @click="emit('edit')">修改</el-button>
      </div>
    </template>
  </el-drawer>
</template>
