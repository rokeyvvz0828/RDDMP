<script setup lang="ts">
import { ref, watch } from 'vue'
import { Edit, Link, MoreFilled, Plus, Refresh, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import ReleaseSearchSelect from './ReleaseSearchSelect.vue'
import type { ReleaseApplicationDto, ReleaseApplicationStatusCode, ReleaseWindowDto } from '../../../api/release'
import type { ReleaseSearchOption } from '../types'

const props = defineProps<{
  applications: ReleaseApplicationDto[]
  total: number
  page: number
  pageSize: number
  windowId?: number
  windows: ReleaseWindowDto[]
  windowsLoading: boolean
  loading: boolean
  error?: string
  searchOptions: ReleaseSearchOption[]
  searchLoading: boolean
  searchError?: string
  canCreate: boolean
  canUpdate: boolean
  canSubmit: boolean
  canWithdraw: boolean
  canCancel: boolean
}>()
const emit = defineEmits<{
  query: [query: { page: number; size: number; windowId?: number; keyword?: string; status?: ReleaseApplicationStatusCode }]
  refresh: []
  create: []
  edit: [application: ReleaseApplicationDto]
  detail: [application: ReleaseApplicationDto]
  'copy-approval-link': [application: ReleaseApplicationDto]
  action: [application: ReleaseApplicationDto, action: 'withdraw' | 'cancel' | 'resubmit']
  'search-options': [query: string, windowId?: number]
}>()

const selectedApplicationCode = ref<string | number>()
const selectedWindowId = ref<number | ''>(props.windowId ?? '')
const status = ref<ReleaseApplicationStatusCode | ''>('')
function query(page = 1, size = props.pageSize) {
  emit('query', {
    page,
    size,
    windowId: selectedWindowId.value || undefined,
    keyword: typeof selectedApplicationCode.value === 'string' ? selectedApplicationCode.value : undefined,
    status: status.value || undefined
  })
}
watch([selectedApplicationCode, selectedWindowId, status], () => query(1))

let defaultWindowInitialized = false
watch(() => props.windows, items => {
  if (defaultWindowInitialized || !items.length) return
  defaultWindowInitialized = true
  if (selectedWindowId.value !== '') return
  selectedWindowId.value = items.reduce((latest, item) => {
    if (item.productionStart !== latest.productionStart) {
      return item.productionStart > latest.productionStart ? item : latest
    }
    return item.id > latest.id ? item : latest
  }).id
}, { immediate: true })

const statusLabels: Record<ReleaseApplicationStatusCode, string> = {
  DRAFT: '草稿', IN_REVIEW: '审批中', RETURNED: '已退回', WITHDRAWN: '已撤回',
  CANCELLED: '已取消', RELEASED: '制品准出'
}
const versionLabels = { REGULAR: '常规版本', URGENT: '紧急版本', EMERGENCY: '应急版本' } as const
function statusTone(value: ReleaseApplicationStatusCode) { return value === 'RELEASED' ? 'success' : value === 'IN_REVIEW' ? 'primary' : value === 'RETURNED' || value === 'WITHDRAWN' ? 'warning' : 'info' }
function versionTone(value: ReleaseApplicationDto['versionType']) { return value === 'EMERGENCY' ? 'danger' : value === 'URGENT' ? 'warning' : 'info' }
function statusLabel(value: ReleaseApplicationStatusCode) { return statusLabels[value] }
function versionLabel(value: ReleaseApplicationDto['versionType']) { return versionLabels[value] }
function canEdit(item: ReleaseApplicationDto) { return ['DRAFT', 'RETURNED', 'WITHDRAWN'].includes(item.status) }
function hasMoreActions(item: ReleaseApplicationDto) {
  return canEdit(item) && (props.canUpdate || props.canCancel || (item.status !== 'DRAFT' && props.canSubmit))
}
function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '-' }
</script>

<template>
  <div class="release-application-view">
    <UiToolbar>
      <el-select v-model="selectedWindowId" clearable filterable placeholder="投产窗口" :loading="windowsLoading" style="width: 250px">
        <el-option v-for="item in windows" :key="item.id" :value="item.id" :label="`${item.windowCode} · ${item.windowName}`" />
      </el-select>
      <ReleaseSearchSelect
        v-model="selectedApplicationCode"
        :options="searchOptions"
        :loading="searchLoading"
        :error="searchError"
        placeholder="搜索并选择版本申请"
        remote
        @search="emit('search-options', $event, selectedWindowId || undefined)"
      />
      <el-select v-model="status" clearable placeholder="申请状态" style="width: 138px"><el-option v-for="(label, code) in statusLabels" :key="code" :value="code" :label="label" /></el-select>
      <template #actions><el-button :icon="Refresh" circle aria-label="刷新版本申请" @click="emit('refresh')" /><el-button v-if="canCreate" type="primary" @click="emit('create')"><el-icon><Plus /></el-icon>新建版本申请</el-button></template>
    </UiToolbar>
    <section v-if="error" class="release-state-panel"><el-result icon="error" title="版本申请加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="emit('refresh')">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiDataTable :data="applications" :loading="loading" row-key="applicationCode" border>
        <el-table-column label="版本申请" min-width="222"><template #default="scope"><button class="release-primary-cell" type="button" @click="emit('detail', scope.row)"><strong>{{ scope.row.applicationCode }}</strong><span>{{ scope.row.windowName || '应急版本，准出后归入窗口' }}</span></button></template></el-table-column>
        <el-table-column label="物理子系统" min-width="210"><template #default="scope"><div class="release-primary-cell is-static"><strong>{{ scope.row.subsystemName }}</strong><span>{{ scope.row.subsystemCode }} · {{ scope.row.deliveries.length }} 个交付单元 · {{ scope.row.fileMedia.length }} 个文件</span></div></template></el-table-column>
        <el-table-column label="版本类型" width="112"><template #default="scope"><UiStatusTag :value="versionLabel(scope.row.versionType)" :tone="versionTone(scope.row.versionType)" /></template></el-table-column>
        <el-table-column label="申请特征" width="100"><template #default="scope"><UiStatusTag :value="scope.row.characteristic === 'ADDITIONAL' ? '追加申请' : '普通申请'" :tone="scope.row.characteristic === 'ADDITIONAL' ? 'warning' : 'info'" /></template></el-table-column>
        <el-table-column label="状态" width="106"><template #default="scope"><UiStatusTag :value="statusLabel(scope.row.status)" :tone="statusTone(scope.row.status)" /></template></el-table-column>
        <el-table-column prop="requesterName" label="申请人" width="102" />
        <el-table-column label="更新时间" width="150"><template #default="scope">{{ minute(scope.row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="224" fixed="right"><template #default="scope"><div class="release-table-actions"><el-button link type="primary" @click="emit('detail', scope.row)"><el-icon><View /></el-icon>详情</el-button><el-button v-if="scope.row.status === 'IN_REVIEW'" link type="primary" aria-label="复制审批链接" title="复制审批链接" @click="emit('copy-approval-link', scope.row)"><el-icon><Link /></el-icon>复制链接</el-button><el-button v-if="scope.row.status === 'IN_REVIEW' && canWithdraw" link type="warning" @click="emit('action', scope.row, 'withdraw')">撤回</el-button><el-dropdown v-else-if="hasMoreActions(scope.row)" @command="(command: 'edit' | 'cancel' | 'resubmit') => command === 'edit' ? emit('edit', scope.row) : emit('action', scope.row, command)"><el-button link type="info"><el-icon><MoreFilled /></el-icon>更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-if="canUpdate" command="edit"><el-icon><Edit /></el-icon>编辑申请</el-dropdown-item><el-dropdown-item v-if="scope.row.status !== 'DRAFT' && canSubmit" command="resubmit">重新提交</el-dropdown-item><el-dropdown-item v-if="canCancel" command="cancel" divided>取消申请</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></template></el-table-column>
        <template #footer><div class="release-table-footer"><span>共 {{ total }} 张申请</span><el-pagination :current-page="page" :page-size="pageSize" :page-sizes="[8, 12, 20]" :total="total" layout="total, sizes, prev, pager, next" @update:current-page="query($event)" @update:page-size="query(1, $event)" /></div></template>
      </UiDataTable>
      <UiEmptyState v-if="!applications.length && !loading" title="暂无版本申请" description="可创建当前项目的版本申请。" />
    </template>
  </div>
</template>
