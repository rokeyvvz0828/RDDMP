<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { apiErrorMessage } from '../../../api/error'
import { getCurrentProductionVersions, getProductionVersionHistoryByEntry, type ArtifactTypeCode, type ProductionEntryDto } from '../../../api/release'
import { useProjectContextStore } from '../../../stores/project-context'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import ReleaseSearchSelect from './ReleaseSearchSelect.vue'
import type { ReleaseSearchOption } from '../types'

const projectStore = useProjectContextStore()
const entries = ref<ProductionEntryDto[]>([])
const history = ref<ProductionEntryDto[]>([])
const selected = ref<ProductionEntryDto | null>(null)
const selectedSearchId = ref<string | number>()
const artifactType = ref<ArtifactTypeCode | ''>('')
const loading = ref(false)
const historyLoading = ref(false)
const error = ref('')
const historyOpen = ref(false)
const artifactLabels = { IMAGE: '镜像', BINARY: '二进制', FILE: '文件介质' } as const
const versionLabels = { REGULAR: '常规版本', URGENT: '紧急版本', EMERGENCY: '应急版本' } as const
const searchOptions = computed<ReleaseSearchOption[]>(() => entries.value.map(item => ({
  value: item.id,
  label: item.itemType === 'FILE_MEDIA' ? item.filePath || '文件介质' : `${item.deliveryUnitCode} · ${item.deliveryUnitName}`,
  description: `${item.subsystemCode} · ${item.subsystemName} / ${item.artifactVersion || '无版本号'}`,
  keywords: `${item.subsystemCode} ${item.subsystemName} ${item.deliveryUnitCode} ${item.deliveryUnitName} ${item.filePath || ''} ${item.artifactVersion || ''}`
})))
const rows = computed(() => entries.value.filter(item => {
  const matchesSelection = selectedSearchId.value === undefined || item.id === selectedSearchId.value
  return matchesSelection && (!artifactType.value || item.artifactType === artifactType.value)
}))
function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
function artifactLabel(value: ProductionEntryDto['artifactType']) { return artifactLabels[value] }
function versionLabel(value: ProductionEntryDto['versionType']) { return versionLabels[value] }
function versionTone(value: ProductionEntryDto['versionType']) { return value === 'EMERGENCY' ? 'danger' : value === 'URGENT' ? 'warning' : 'info' }
async function load() {
  loading.value = true
  error.value = ''
  try {
    await projectStore.initialize()
    entries.value = (await getCurrentProductionVersions(projectStore.current?.ref)).data.data
    if (selectedSearchId.value !== undefined && !entries.value.some(item => item.id === selectedSearchId.value)) selectedSearchId.value = undefined
  } catch (requestError) {
    entries.value = []
    error.value = apiErrorMessage(requestError, '生产版本加载失败，请稍后重试')
  } finally { loading.value = false }
}
async function openHistory(item: ProductionEntryDto) {
  selected.value = item
  history.value = []
  historyOpen.value = true
  historyLoading.value = true
  try { history.value = (await getProductionVersionHistoryByEntry(item.id)).data.data }
  catch (requestError) { error.value = apiErrorMessage(requestError, '版本历史加载失败') }
  finally { historyLoading.value = false }
}
onMounted(load)
watch(() => projectStore.currentRef, () => {
  selectedSearchId.value = undefined
  void load()
})
</script>

<template>
  <div class="release-current-production-view">
    <UiToolbar><ReleaseSearchSelect v-model="selectedSearchId" :options="searchOptions" placeholder="搜索并选择生产版本" /><el-select v-model="artifactType" clearable placeholder="制品类型" style="width: 136px"><el-option label="镜像" value="IMAGE" /><el-option label="二进制" value="BINARY" /><el-option label="文件介质" value="FILE" /></el-select><template #actions><span class="release-result-count">{{ rows.length }} 条生产交付内容</span><el-button :icon="Refresh" circle aria-label="刷新生产版本" @click="load" /></template></UiToolbar>
    <section v-if="error" class="release-state-panel"><el-result icon="error" title="生产版本加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else><UiDataTable :data="rows" :loading="loading" row-key="id" border><el-table-column label="物理子系统" min-width="190"><template #default="scope"><div class="release-primary-cell is-static"><strong>{{ scope.row.subsystemName }}</strong><span>{{ scope.row.subsystemCode }}</span></div></template></el-table-column><el-table-column label="交付内容" min-width="260"><template #default="scope"><button class="release-primary-cell" type="button" @click="openHistory(scope.row)"><strong>{{ scope.row.itemType === 'FILE_MEDIA' ? '文件介质' : scope.row.deliveryUnitName }}</strong><span class="release-path-text">{{ scope.row.filePath || scope.row.deliveryUnitCode }}</span></button></template></el-table-column><el-table-column label="生产版本" min-width="150"><template #default="scope"><code class="release-version-code">{{ scope.row.artifactVersion || '无版本号' }}</code></template></el-table-column><el-table-column label="制品类型" width="96"><template #default="scope"><el-tag effect="plain">{{ artifactLabel(scope.row.artifactType) }}</el-tag></template></el-table-column><el-table-column label="版本类型" width="108"><template #default="scope"><UiStatusTag :value="versionLabel(scope.row.versionType)" :tone="versionTone(scope.row.versionType)" /></template></el-table-column><el-table-column label="最近投产" min-width="168"><template #default="scope"><div class="release-date-cell"><strong>{{ minute(scope.row.productionAt) }}</strong><span>{{ scope.row.windowName || '窗口名称缺失' }}</span></div></template></el-table-column><el-table-column prop="applicationCode" label="来源申请" min-width="150" /><el-table-column label="操作" width="96" fixed="right"><template #default="scope"><el-button link type="primary" @click="openHistory(scope.row)">版本历史</el-button></template></el-table-column></UiDataTable><UiEmptyState v-if="!rows.length && !loading" title="暂无生产版本" description="仅展示最近一次投产成功的交付内容。" /></template>

    <el-dialog v-model="historyOpen" title="生产版本历史" width="min(760px, 94vw)"><div v-if="selected" class="release-lineage-dialog"><header><div><h3>{{ selected.itemType === 'FILE_MEDIA' ? '文件介质' : selected.deliveryUnitName }}</h3><small class="release-path-text">{{ selected.subsystemName }} · {{ selected.filePath || selected.deliveryUnitCode }}</small></div><code>{{ selected.artifactVersion || '无版本号' }}</code></header><el-skeleton v-if="historyLoading" :rows="5" animated /><ol v-else><li v-for="(item, index) in history" :key="item.id" :class="{ 'is-current': item.id === selected.id }"><span class="release-lineage-index">{{ index + 1 }}</span><div><strong class="release-path-text">{{ item.filePath || item.artifactVersion || '无版本号' }}</strong><small>{{ item.windowName || '窗口名称缺失' }}</small></div><span>{{ versionLabels[item.versionType] }}</span><span>{{ minute(item.productionAt) }}</span></li></ol><UiEmptyState v-if="!history.length && !historyLoading" title="暂无历史版本" /></div></el-dialog>
  </div>
</template>
