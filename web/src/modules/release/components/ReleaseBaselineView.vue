<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Edit, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import { batchUpdateProductionResults, getProductionBaseline, listReleaseWindows, updateProductionResult, type MaintainedProductionResult, type ProductionEntryDto, type ReleaseWindowDto } from '../../../api/release'
import { useProjectContextStore } from '../../../stores/project-context'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import ReleaseSearchSelect from './ReleaseSearchSelect.vue'
import type { ReleaseSearchOption } from '../types'

type ProductionResult = ProductionEntryDto['productionResult']
const props = defineProps<{ canUpdate: boolean }>()
const projectStore = useProjectContextStore()
const windows = ref<ReleaseWindowDto[]>([])
const windowId = ref<number>()
const entries = ref<ProductionEntryDto[]>([])
const selectedSearchId = ref<string | number>()
const resultFilter = ref<ProductionResult | ''>('')
const loading = ref(false)
const error = ref('')
const resultDialogOpen = ref(false)
const editingEntries = ref<ProductionEntryDto[]>([])
const selectedEntries = ref<ProductionEntryDto[]>([])
const batchMode = ref(false)
const saving = ref(false)
const now = ref(Date.now())
let clockTimer: number | undefined
const resultForm = reactive<{ productionResult: MaintainedProductionResult; productionAt: string; resultReason: string; changeReason: string }>({ productionResult: 'SUCCEEDED', productionAt: '', resultReason: '', changeReason: '' })

const resultLabels: Record<ProductionResult, string> = { RELEASED: '制品准出', SUCCEEDED: '投产成功', FAILED: '投产失败', NOT_DEPLOYED: '未投产' }
const versionLabels = { REGULAR: '常规版本', URGENT: '紧急版本', EMERGENCY: '应急版本' } as const
const artifactLabels = { IMAGE: '镜像', BINARY: '二进制', FILE: '文件介质' } as const
const selectedWindow = computed(() => windows.value.find(item => item.id === windowId.value))
const selectedWindowMaintainable = computed(() => isWindowMaintainable(selectedWindow.value))
const selectedWindowUnavailableReason = computed(() => windowUnavailableReason(selectedWindow.value))
const resultDialogTitle = computed(() => batchMode.value ? `批量维护投产结果（${editingEntries.value.length} 条）` : '维护投产结果')
const searchOptions = computed<ReleaseSearchOption[]>(() => entries.value.map(item => ({
  value: item.id,
  label: item.itemType === 'FILE_MEDIA' ? item.filePath || '文件介质' : `${item.deliveryUnitCode} · ${item.deliveryUnitName}`,
  description: `${item.subsystemCode} · ${item.subsystemName} / ${item.artifactVersion || '无版本号'} / ${item.applicationCode}`,
  keywords: `${item.subsystemCode} ${item.subsystemName} ${item.deliveryUnitCode} ${item.deliveryUnitName} ${item.filePath || ''} ${item.artifactVersion || ''} ${item.applicationCode}`
})))
const rows = computed(() => entries.value.filter(item => {
  const matchesSelection = selectedSearchId.value === undefined || item.id === selectedSearchId.value
  return matchesSelection && (!resultFilter.value || item.productionResult === resultFilter.value)
}))
const summary = computed(() => ({
  systems: new Set(entries.value.map(item => item.subsystemCode)).size,
  units: entries.value.filter(item => item.itemType === 'DELIVERY_UNIT').length,
  files: entries.value.filter(item => item.itemType === 'FILE_MEDIA').length,
  success: entries.value.filter(item => item.productionResult === 'SUCCEEDED').length,
  released: entries.value.filter(item => item.productionResult === 'RELEASED').length
}))

function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
function isoMinute(value: string) { return value ? value.replace(' ', 'T') + ':00' : undefined }
function artifactLabel(value: ProductionEntryDto['artifactType']) { return artifactLabels[value] }
function versionLabel(value: ProductionEntryDto['versionType']) { return versionLabels[value] }
function resultLabel(value: ProductionResult) { return resultLabels[value] }
function resultTone(value: ProductionResult) { return value === 'SUCCEEDED' ? 'success' : value === 'FAILED' ? 'danger' : value === 'NOT_DEPLOYED' ? 'warning' : 'primary' }
function versionTone(value: ProductionEntryDto['versionType']) { return value === 'EMERGENCY' ? 'danger' : value === 'URGENT' ? 'warning' : 'info' }
function windowEndTime(item?: ReleaseWindowDto) {
  if (!item?.productionEnd) return Number.NaN
  return new Date(item.productionEnd).getTime()
}
function isWindowMaintainable(item?: ReleaseWindowDto) {
  const endTime = windowEndTime(item)
  return Number.isFinite(endTime) && now.value >= endTime
}
function windowUnavailableReason(item?: ReleaseWindowDto) {
  if (!item) return ''
  const endTime = windowEndTime(item)
  if (!Number.isFinite(endTime)) return '投产窗口结束时间无效，请刷新后重试'
  return now.value < endTime ? `不可维护：投产窗口将于 ${minute(item.productionEnd)} 结束` : ''
}
function maintenanceState(item: ProductionEntryDto) {
  if (item.productionResult !== 'RELEASED') return '已维护'
  return Number.isFinite(windowEndTime(selectedWindow.value)) ? '窗口未结束' : '暂不可维护'
}

async function loadWindows() {
  if (!projectStore.current) return
  const response = await listReleaseWindows({ page: 1, size: 200, projectId: projectStore.current.ref })
  windows.value = response.data.data.records
  if (!windows.value.some(item => item.id === windowId.value)) {
    windowId.value = windows.value.find(item => item.status === 'IN_PRODUCTION')?.id || windows.value[0]?.id
  }
}
async function loadEntries() {
  selectedEntries.value = []
  loading.value = true
  error.value = ''
  try {
    if (!windowId.value) {
      entries.value = []
      return
    }
    entries.value = (await getProductionBaseline(windowId.value)).data.data
  } catch (requestError) {
    entries.value = []
    error.value = apiErrorMessage(requestError, '投产基线加载失败，请稍后重试')
  } finally { loading.value = false }
}
async function initialize() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.initialize()
    await loadWindows()
    await loadEntries()
  } catch (requestError) {
    error.value = apiErrorMessage(requestError, '投产基线加载失败，请稍后重试')
    loading.value = false
  }
}
function isMaintainable(item: ProductionEntryDto) { return props.canUpdate && selectedWindowMaintainable.value && item.productionResult === 'RELEASED' }
function onSelectionChange(items: ProductionEntryDto[]) { selectedEntries.value = items.filter(isMaintainable) }
function resetResultForm() {
  Object.assign(resultForm, { productionResult: 'SUCCEEDED', productionAt: '', resultReason: '', changeReason: '' })
}
function openResult(item: ProductionEntryDto) {
  if (!selectedWindowMaintainable.value) {
    ElMessage.warning(selectedWindowUnavailableReason.value || '当前投产窗口暂不可维护')
    return
  }
  if (!isMaintainable(item)) return
  batchMode.value = false
  editingEntries.value = [item]
  resetResultForm()
  resultDialogOpen.value = true
}
function openBatchResult() {
  if (!selectedWindowMaintainable.value) {
    ElMessage.warning(selectedWindowUnavailableReason.value || '当前投产窗口暂不可维护')
    return
  }
  if (!selectedEntries.value.length) return
  batchMode.value = true
  editingEntries.value = [...selectedEntries.value]
  resetResultForm()
  resultDialogOpen.value = true
}
async function saveResult() {
  const items = editingEntries.value
  if (!items.length) return
  if (!selectedWindowMaintainable.value) {
    ElMessage.warning(selectedWindowUnavailableReason.value || '当前投产窗口暂不可维护')
    return
  }
  if (!resultForm.changeReason.trim()) { ElMessage.warning('请填写变更原因'); return }
  if (resultForm.productionResult === 'SUCCEEDED' && !resultForm.productionAt) { ElMessage.warning('投产成功必须填写投产时间'); return }
  if (['FAILED', 'NOT_DEPLOYED'].includes(resultForm.productionResult) && !resultForm.resultReason.trim()) { ElMessage.warning('投产失败或未投产必须填写结果原因'); return }
  saving.value = true
  try {
    const payload = {
      productionResult: resultForm.productionResult,
      productionAt: resultForm.productionResult === 'SUCCEEDED' ? isoMinute(resultForm.productionAt) : undefined,
      resultReason: ['FAILED', 'NOT_DEPLOYED'].includes(resultForm.productionResult) ? resultForm.resultReason.trim() : undefined,
      changeReason: resultForm.changeReason.trim()
    }
    if (!batchMode.value) {
      await updateProductionResult(items[0].id, { ...payload, rowVersion: items[0].rowVersion })
    } else {
      await batchUpdateProductionResults({ ...payload, entries: items.map(item => ({ id: item.id, rowVersion: item.rowVersion })) })
    }
    resultDialogOpen.value = false
    ElMessage.success(batchMode.value ? `已批量维护 ${items.length} 条投产结果` : '投产结果已更新')
    await loadEntries()
  } catch (requestError) {
    ElMessage.error(apiErrorMessage(requestError, '投产结果更新失败'))
  } finally { saving.value = false }
}

onMounted(() => {
  void initialize()
  clockTimer = window.setInterval(() => { now.value = Date.now() }, 60_000)
})
onBeforeUnmount(() => {
  if (clockTimer !== undefined) window.clearInterval(clockTimer)
})
watch(windowId, () => {
  selectedSearchId.value = undefined
  void loadEntries()
})
watch(entries, items => {
  if (selectedSearchId.value !== undefined && !items.some(item => item.id === selectedSearchId.value)) selectedSearchId.value = undefined
})
watch(() => projectStore.currentRef, () => {
  selectedSearchId.value = undefined
  void initialize()
})
watch(selectedWindowMaintainable, maintainable => {
  if (!maintainable) selectedEntries.value = []
})
</script>

<template>
  <div class="release-baseline-view">
    <UiToolbar>
      <el-select v-model="windowId" placeholder="选择投产窗口" popper-class="release-window-select-popper" style="width: 380px">
        <el-option v-for="item in windows" :key="item.id" :value="item.id" :label="`${item.windowCode} · ${item.windowName} · ${item.statusLabel}`">
          <div class="release-window-option release-baseline-window-option">
            <span>
              <strong>{{ item.windowCode }} · {{ item.windowName }}</strong>
              <small>投产结束 {{ minute(item.productionEnd) }}<template v-if="windowUnavailableReason(item)"> · {{ windowUnavailableReason(item) }}</template><template v-else> · 可维护投产结果</template></small>
            </span>
            <el-tag type="info" size="small">{{ item.statusLabel }}</el-tag>
          </div>
        </el-option>
      </el-select>
      <ReleaseSearchSelect v-model="selectedSearchId" :options="searchOptions" placeholder="搜索并选择基线明细" />
      <el-select v-model="resultFilter" clearable placeholder="投产结果" style="width: 138px"><el-option v-for="(label, value) in resultLabels" :key="value" :value="value" :label="label" /></el-select>
      <template #actions><span v-if="selectedEntries.length" class="release-result-count">已选 {{ selectedEntries.length }} 条</span><el-button v-if="canUpdate" type="primary" :icon="Edit" :disabled="!selectedWindowMaintainable || !selectedEntries.length" @click="openBatchResult">批量维护</el-button><el-button :icon="Refresh" circle aria-label="刷新投产基线" @click="initialize" /></template>
    </UiToolbar>
    <el-alert v-if="selectedWindowUnavailableReason" :title="selectedWindowUnavailableReason" type="warning" :closable="false" show-icon class="release-baseline-gate-alert" />
    <div v-if="selectedWindow" class="release-ledger-summary">
      <div class="release-ledger-summary__window">
        <div>
          <span>投产窗口</span>
          <strong>{{ selectedWindow.windowCode }}</strong>
          <small :title="selectedWindow.windowName">{{ selectedWindow.windowName }}</small>
        </div>
        <el-tag type="info" size="small">{{ selectedWindow.statusLabel }}</el-tag>
      </div>
      <div class="release-ledger-summary__metrics">
        <div><span>物理子系统</span><strong>{{ summary.systems }}</strong></div>
        <div><span>交付单元</span><strong>{{ summary.units }}</strong></div>
        <div><span>文件介质</span><strong>{{ summary.files }}</strong></div>
        <div><span>投产成功</span><strong>{{ summary.success }}</strong></div>
        <div><span>制品准出</span><strong>{{ summary.released }}</strong></div>
      </div>
    </div>
    <section v-if="error" class="release-state-panel"><el-result icon="error" title="投产基线加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="initialize">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiDataTable :data="rows" :loading="loading" row-key="id" border @selection-change="onSelectionChange">
        <el-table-column v-if="canUpdate" type="selection" width="46" :selectable="isMaintainable" />
        <el-table-column label="物理子系统" min-width="176"><template #default="scope"><div class="release-primary-cell is-static"><strong>{{ scope.row.subsystemName }}</strong><span>{{ scope.row.subsystemCode }}</span></div></template></el-table-column>
        <el-table-column label="交付内容" min-width="240"><template #default="scope"><div class="release-primary-cell is-static"><strong>{{ scope.row.itemType === 'FILE_MEDIA' ? '文件介质' : scope.row.deliveryUnitName }}</strong><span class="release-path-text">{{ scope.row.itemType === 'FILE_MEDIA' ? scope.row.filePath : `${scope.row.deliveryUnitCode} · ${artifactLabel(scope.row.artifactType)}` }}</span></div></template></el-table-column>
        <el-table-column label="最新准出版本" min-width="132"><template #default="scope"><code class="release-version-code">{{ scope.row.artifactVersion || '无版本号' }}</code></template></el-table-column>
        <el-table-column label="版本类型" width="106"><template #default="scope"><UiStatusTag :value="versionLabel(scope.row.versionType)" :tone="versionTone(scope.row.versionType)" /></template></el-table-column>
        <el-table-column prop="applicationCode" label="来源申请" min-width="150" />
        <el-table-column label="投产结果" width="116"><template #default="scope"><UiStatusTag :value="resultLabel(scope.row.productionResult)" :tone="resultTone(scope.row.productionResult)" /></template></el-table-column>
        <el-table-column label="投产信息" min-width="158"><template #default="scope"><div class="release-date-cell"><strong>{{ minute(scope.row.productionAt) }}</strong><span>{{ scope.row.resultReason || '无结果说明' }}</span></div></template></el-table-column>
        <el-table-column label="操作" width="112" fixed="right"><template #default="scope"><el-button v-if="isMaintainable(scope.row)" link type="primary" @click="openResult(scope.row)">维护结果</el-button><span v-else class="release-maintained-state">{{ canUpdate ? maintenanceState(scope.row) : '只读' }}</span></template></el-table-column>
      </UiDataTable>
      <UiEmptyState v-if="!rows.length && !loading" title="当前窗口没有匹配的准出制品" description="审批完成的交付单元和文件介质会自动进入此列表。" />
    </template>

    <el-dialog v-model="resultDialogOpen" :title="resultDialogTitle" width="min(620px, 94vw)" :close-on-click-modal="false">
      <template v-if="editingEntries.length"><div class="release-detail-heading"><div><span class="release-panel-kicker">{{ batchMode ? `${editingEntries.length} 条交付内容` : editingEntries[0].applicationCode }}</span><h3>{{ batchMode ? '统一维护投产结果' : editingEntries[0].itemType === 'FILE_MEDIA' ? '文件介质' : editingEntries[0].deliveryUnitName }}</h3><p class="release-path-text">{{ batchMode ? '本次填写的结果、时间及原因将应用到全部已选明细。' : editingEntries[0].filePath || editingEntries[0].artifactVersion || '无版本号' }}</p></div><UiStatusTag value="制品准出" tone="primary" /></div><el-form label-position="top"><el-form-item label="投产结果" required><el-radio-group v-model="resultForm.productionResult"><el-radio-button value="SUCCEEDED">投产成功</el-radio-button><el-radio-button value="FAILED">投产失败</el-radio-button><el-radio-button value="NOT_DEPLOYED">未投产</el-radio-button></el-radio-group></el-form-item><el-form-item v-if="resultForm.productionResult === 'SUCCEEDED'" label="投产时间" required><el-date-picker v-model="resultForm.productionAt" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm" /></el-form-item><el-form-item v-if="['FAILED', 'NOT_DEPLOYED'].includes(resultForm.productionResult)" label="结果原因" required><el-input v-model="resultForm.resultReason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item><el-form-item label="变更原因" required><el-input v-model="resultForm.changeReason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item></el-form></template>
      <template #footer><el-button :disabled="saving" @click="resultDialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveResult">{{ batchMode ? '确认批量维护' : '确认更新' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.release-baseline-gate-alert { margin-bottom: 12px; }
.release-baseline-window-option { align-items: flex-start; }
.release-baseline-window-option small { overflow: visible; line-height: 1.45; white-space: normal; }
.release-baseline-window-option .el-tag { flex: 0 0 auto; }
</style>
