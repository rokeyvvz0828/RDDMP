<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Edit, Plus, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import ReleaseSearchSelect from './ReleaseSearchSelect.vue'
import type { ReleaseWindowDto, ReleaseWindowUpdate, ReleaseWindowWrite } from '../../../api/release'
import type { ProjectContextItem } from '../../../types/project-context'
import type { ReleaseSearchOption } from '../types'

const props = defineProps<{
  windows: ReleaseWindowDto[]
  project: ProjectContextItem | null
  loading: boolean
  error?: string
  canCreate: boolean
  canUpdate: boolean
}>()
const emit = defineEmits<{
  refresh: []
  create: [payload: ReleaseWindowWrite]
  update: [id: number, payload: ReleaseWindowUpdate]
  'toggle-regular': [window: ReleaseWindowDto, enabled: boolean, reason: string]
}>()

type WindowForm = ReleaseWindowWrite & { id?: number; windowCode?: string; rowVersion?: number; changeReason: string }
const selectedSearchId = ref<string | number>()
const status = ref('')
const dialogOpen = ref(false)
const detailOpen = ref(false)
const selected = ref<ReleaseWindowDto | null>(null)
const submitting = ref(false)
const form = reactive<WindowForm>(emptyForm())
const searchOptions = computed<ReleaseSearchOption[]>(() => props.windows.map(item => ({
  value: item.id,
  label: item.windowName,
  description: `${item.windowCode} · ${item.statusLabel}`,
  keywords: `${item.windowCode} ${item.windowName} ${item.statusLabel}`
})))
const rows = computed(() => props.windows.filter(item => {
  const matchesSelection = selectedSearchId.value === undefined || item.id === selectedSearchId.value
  return matchesSelection && (!status.value || item.status === status.value)
}))

function localMinute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '' }
function isoMinute(value: string) { return value ? value.replace(' ', 'T') + ':00' : '' }
function firstDayOfMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01 00:00`
}
function emptyForm(): WindowForm {
  return {
    windowName: '', projectId: props?.project?.ref || '', projectCode: props?.project?.ref || '',
    projectName: props?.project?.name || '', declarationStart: firstDayOfMonth(), declarationEnd: '',
    productionStart: '', productionEnd: '', regularEnabled: true, description: '', changeReason: ''
  }
}
function tone(statusCode: ReleaseWindowDto['status']) {
  return statusCode === 'CLOSED' ? 'info' : statusCode === 'DECLARATION_OPEN' ? 'primary'
    : statusCode === 'IN_PRODUCTION' ? 'danger' : statusCode === 'URGENT' ? 'warning' : 'info'
}
function isRegularEnabled(item: ReleaseWindowDto) {
  return item.regularEnabled && item.status !== 'CLOSED'
}
function canToggleRegular(item: ReleaseWindowDto) {
  return props.canUpdate && item.status !== 'CLOSED'
}
function openCreate() {
  Object.assign(form, emptyForm(), { projectId: props.project?.ref || '', projectCode: props.project?.ref || '', projectName: props.project?.name || '' })
  selected.value = null
  dialogOpen.value = true
}
function openEdit(item: ReleaseWindowDto) {
  selected.value = item
  Object.assign(form, {
    id: item.id, windowCode: item.windowCode, windowName: item.windowName,
    projectId: item.projectId, projectCode: item.projectCode, projectName: item.projectName,
    declarationStart: localMinute(item.declarationStart), declarationEnd: localMinute(item.declarationEnd),
    productionStart: localMinute(item.productionStart), productionEnd: localMinute(item.productionEnd),
    regularEnabled: item.regularEnabled, description: item.description || '', rowVersion: item.rowVersion,
    changeReason: ''
  })
  dialogOpen.value = true
}
function openDetail(item: ReleaseWindowDto) { selected.value = item; detailOpen.value = true }
function validTimes() {
  return form.declarationStart < form.declarationEnd && form.declarationEnd < form.productionStart
    && form.productionStart < form.productionEnd
}
async function save() {
  if (!form.windowName.trim()) { ElMessage.warning('请填写窗口名称'); return }
  if (!validTimes()) { ElMessage.warning('四个时间必须严格依次递增'); return }
  if (form.id && !form.changeReason.trim()) { ElMessage.warning('请填写修改原因'); return }
  submitting.value = true
  const payload: ReleaseWindowWrite = {
    windowName: form.windowName.trim(), projectId: form.projectId, projectCode: form.projectCode,
    projectName: form.projectName, declarationStart: isoMinute(form.declarationStart),
    declarationEnd: isoMinute(form.declarationEnd), productionStart: isoMinute(form.productionStart),
    productionEnd: isoMinute(form.productionEnd), regularEnabled: form.regularEnabled,
    description: form.description?.trim() || undefined
  }
  if (form.id) emit('update', form.id, { ...payload, windowCode: form.windowCode!, rowVersion: form.rowVersion!, changeReason: form.changeReason.trim() })
  else emit('create', payload)
}
function finishSave() { submitting.value = false; dialogOpen.value = false }
function failSave() { submitting.value = false }
defineExpose({ finishSave, failSave })

async function toggleRegular(item: ReleaseWindowDto) {
  try {
    const { value } = await ElMessageBox.prompt(`${item.regularEnabled ? '关闭' : '开启'} ${item.windowCode} 的常规版本申请。`, '填写变更原因', {
      inputType: 'textarea', inputValidator: value => Boolean(value?.trim()) || '请填写变更原因',
      confirmButtonText: '确认变更', cancelButtonText: '取消'
    })
    emit('toggle-regular', item, !item.regularEnabled, value.trim())
  } catch { /* cancelled */ }
}

watch(() => props.project?.ref, () => { if (!form.id && !dialogOpen.value) Object.assign(form, emptyForm()) })
watch(() => props.windows, items => {
  if (selectedSearchId.value !== undefined && !items.some(item => item.id === selectedSearchId.value)) selectedSearchId.value = undefined
})
</script>

<template>
  <div class="release-window-view">
    <UiToolbar>
      <ReleaseSearchSelect v-model="selectedSearchId" :options="searchOptions" placeholder="搜索并选择投产窗口" />
      <el-select v-model="status" clearable placeholder="窗口状态" style="width: 150px">
        <el-option label="未开始" value="UPCOMING" /><el-option label="申报中" value="DECLARATION_OPEN" />
        <el-option label="紧急申报期" value="URGENT" /><el-option label="投产中" value="IN_PRODUCTION" /><el-option label="已关闭" value="CLOSED" />
      </el-select>
      <template #actions><el-button :icon="Refresh" circle aria-label="刷新投产窗口" @click="emit('refresh')" /><el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreate">新增投产窗口</el-button></template>
    </UiToolbar>

    <section v-if="error" class="release-state-panel"><el-result icon="error" title="投产窗口加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="emit('refresh')">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiDataTable :data="rows" :loading="loading" row-key="id" border>
        <el-table-column label="投产窗口" min-width="230"><template #default="scope"><button type="button" class="release-primary-cell" @click="openDetail(scope.row)"><strong>{{ scope.row.windowName }}</strong><span>{{ scope.row.windowCode }}</span></button></template></el-table-column>
        <el-table-column label="申报周期" min-width="205"><template #default="scope"><div class="release-date-cell"><strong>{{ localMinute(scope.row.declarationStart) }}</strong><span>至 {{ localMinute(scope.row.declarationEnd) }}</span></div></template></el-table-column>
        <el-table-column label="计划投产" min-width="205"><template #default="scope"><div class="release-date-cell"><strong>{{ localMinute(scope.row.productionStart) }}</strong><span>至 {{ localMinute(scope.row.productionEnd) }}</span></div></template></el-table-column>
        <el-table-column label="常规申请" width="126"><template #default="scope"><div class="release-date-cell"><el-switch :model-value="isRegularEnabled(scope.row)" :disabled="!canToggleRegular(scope.row)" inline-prompt active-text="开" inactive-text="关" @click.prevent="canToggleRegular(scope.row) && toggleRegular(scope.row)" /><span v-if="scope.row.unavailableReason">{{ scope.row.unavailableReason }}</span><span v-else>允许提交</span></div></template></el-table-column>
        <el-table-column label="状态" width="112"><template #default="scope"><UiStatusTag :value="scope.row.statusLabel" :tone="tone(scope.row.status)" /></template></el-table-column>
        <el-table-column label="操作" width="140" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row)"><el-icon><View /></el-icon>详情</el-button><el-button v-if="canUpdate" link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button></template></el-table-column>
      </UiDataTable>
      <UiEmptyState v-if="!rows.length && !loading" title="暂无投产窗口" description="可新增当前项目的投产窗口。" />
    </template>

    <el-dialog v-model="detailOpen" title="投产窗口详情" width="min(760px, 92vw)">
      <template v-if="selected"><div class="release-detail-heading"><div><span class="release-panel-kicker">{{ selected.windowCode }}</span><h3>{{ selected.windowName }}</h3><p>{{ selected.description || '无窗口说明' }}</p></div><UiStatusTag :value="selected.statusLabel" :tone="tone(selected.status)" /></div><el-descriptions :column="2" border><el-descriptions-item label="所属项目">{{ selected.projectName }}</el-descriptions-item><el-descriptions-item label="常规申请">{{ isRegularEnabled(selected) ? '开启' : '关闭' }}</el-descriptions-item><el-descriptions-item label="申报开始">{{ localMinute(selected.declarationStart) }}</el-descriptions-item><el-descriptions-item label="申报截止">{{ localMinute(selected.declarationEnd) }}</el-descriptions-item><el-descriptions-item label="投产开始">{{ localMinute(selected.productionStart) }}</el-descriptions-item><el-descriptions-item label="投产结束">{{ localMinute(selected.productionEnd) }}</el-descriptions-item><el-descriptions-item label="可选状态" :span="2">{{ selected.regularApplicationSelectable ? '可用于版本申请' : selected.unavailableReason || '不可选择' }}</el-descriptions-item></el-descriptions></template>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button><el-button v-if="selected && canUpdate" type="primary" @click="detailOpen = false; openEdit(selected)">编辑</el-button></template>
    </el-dialog>

    <el-dialog v-model="dialogOpen" :title="form.id ? '编辑投产窗口' : '新增投产窗口'" width="min(720px, 94vw)" destroy-on-close>
      <el-form label-position="top" class="release-window-form"><div class="release-form-grid">
        <el-form-item v-if="form.id" label="窗口编码"><el-input v-model="form.windowCode" disabled /></el-form-item><el-form-item label="所属项目"><el-input v-model="form.projectName" disabled /></el-form-item>
        <el-form-item label="窗口名称" required :class="{ 'is-wide': !form.id }"><el-input v-model="form.windowName" maxlength="128" show-word-limit /></el-form-item>
        <el-form-item label="申报开始" required><el-date-picker v-model="form.declarationStart" value-format="YYYY-MM-DD HH:mm" format="YYYY-MM-DD HH:mm" type="datetime" /></el-form-item>
        <el-form-item label="申报截止" required><el-date-picker v-model="form.declarationEnd" value-format="YYYY-MM-DD HH:mm" format="YYYY-MM-DD HH:mm" type="datetime" /></el-form-item>
        <el-form-item label="计划投产开始" required><el-date-picker v-model="form.productionStart" value-format="YYYY-MM-DD HH:mm" format="YYYY-MM-DD HH:mm" type="datetime" /></el-form-item>
        <el-form-item label="计划投产结束" required><el-date-picker v-model="form.productionEnd" value-format="YYYY-MM-DD HH:mm" format="YYYY-MM-DD HH:mm" type="datetime" /></el-form-item>
        <el-form-item label="窗口说明" class="is-wide"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item v-if="form.id" label="修改原因" required class="is-wide"><el-input v-model="form.changeReason" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </div></el-form>
      <template #footer><el-button :disabled="submitting" @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="submitting" @click="save">保存窗口</el-button></template>
    </el-dialog>
  </div>
</template>
