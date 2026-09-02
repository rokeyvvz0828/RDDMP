<!--
  用途：数迁资产内容 - 统一回收站页（REQ-20260831-050）
  说明：聚合 6 个文件型、3 个结构化内容菜单、汇报材料（REPORT）与会议纪要（MEETING，文档级）的软删记录，
        支持按内容类型筛选、关键词查询、批量/单条恢复与彻底清理（不可恢复）；
        恢复/彻底删除按类型分发到各自来源服务，保留其业务规则。
        会议附件级回收站仍在会议页内管理（信封不兼容、受 uk_dm_meeting_att_active 约束、需父会议未删前置校验）。
        使用 UiPageHeader + UiToolbar + UiDataTable，覆盖加载/空/失败/无权限/提交中状态，
        彻底清理为危险操作，需确认后执行。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, Search, View } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import {
  listDataMigrationRecycleBin,
  purgeDataMigrationAssets,
  restoreDataMigrationAssets,
  getDataMigrationRecycleBinDetail,
  type DataMigrationContentRecycleRow,
  type DataMigrationContentRecycleDetail,
} from '../../../../api/data-migration'

/** 统一回收站覆盖的内容类型（与后端 ContentRecycleBinService.supportedTypes() 对齐，由 RecycleBinSource 注册表自动探知）。 */
const CONTENT_TYPE_LABELS: Record<string, string> = {
  PLAN: '迁移方案',
  MAPPING_DOC: '迁移映射',
  DEPENDENCY: '迁移过程依赖文件',
  SCRIPT: '迁移程序',
  TOPIC: '专题材料',
  RELEASE_DRILL: '投产及演练',
  REPORT: '汇报材料',
  MEETING: '会议纪要',
  RULE: '迁移检核规则',
  PARAMETER: '迁移参数',
  INTERMEDIATE_TABLE: '中间表结构',
}
const typeOptions = Object.entries(CONTENT_TYPE_LABELS).map(([value, label]) => ({ value, label }))

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const rows = ref<DataMigrationContentRecycleRow[]>([])
const keyword = ref('')
const selectedType = ref('')
const selected = ref<DataMigrationContentRecycleRow[]>([])
const actionBusy = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailTarget = ref<DataMigrationContentRecycleRow | null>(null)
const detail = ref<DataMigrationContentRecycleDetail | null>(null)
/** T26 新增：后端统一回收站支持分页与按 asset_code 升序的全局排序；默认 20 行/页。 */
const page = ref(1)
const size = ref(20)
const total = ref(0)

const hasSelection = computed(() => selected.value.length > 0)

function typeLabel(type: string) {
  return CONTENT_TYPE_LABELS[type] ?? type
}

function messageOf(err: unknown) {
  return err instanceof Error ? err.message : '操作失败，请稍后重试'
}

function httpStatus(err: unknown) {
  return (err as { response?: { status?: number } }).response?.status
}

function cancelled(err: unknown) {
  const action = (err as { action?: string }).action
  return action === 'cancel' || action === 'close'
}

const DETAIL_LABELS: Record<string, string> = {
  project_id: '项目 ID', component_id: '组件 ID', owner_id: '属主 ID', checksum_md5: '文件 MD5',
  attachment_id: '附件 ID', file_name: '文件名', content_type: '文件类型', file_size: '文件大小',
  report_period: '汇报周期', report_date: '汇报日期', granularity: '颗粒度', meeting_source: '会议来源',
  meeting_content: '会议内容', meeting_conclusion: '会议结论', business_scenario: '业务场景', keywords: '关键字',
  structured_data: '结构化内容', attachments: '附件', system_names: '关联系统', related_issue_names: '关联问题',
}
const DETAIL_HIDDEN = new Set(['id', 'asset_type', 'asset_code', 'asset_name', 'project_id', 'component_id', 'owner_id', 'deleted_by', 'deleted_at', 'created_at', 'updated_at', 'deleted', 'tenant_id'])

const standardDetail = computed(() => {
  const item = detail.value
  if (!item) return []
  return [
    ['编号', item.asset_code], ['名称', item.asset_name], ['项目 ID', item.project_id], ['组件 ID', item.component_id],
    ['属主 ID', item.owner_id], ['删除人', item.deleted_by_name ?? item.deleted_by], ['删除时间', item.deleted_at],
    ['创建时间', item.created_at], ['更新时间', item.updated_at],
  ].filter(([, value]) => value !== undefined && value !== null && value !== '')
})
const extraDetail = computed(() => {
  const item = detail.value
  if (!item) return []
  return Object.entries(item)
    .filter(([key, value]) => !DETAIL_HIDDEN.has(key) && value !== undefined && value !== null && value !== '')
    .map(([key, value]) => ({ label: DETAIL_LABELS[key] ?? key, value: formatDetailValue(value) }))
})

function formatDetailValue(value: unknown) {
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  try { return JSON.stringify(value, null, 2) }
  catch { return String(value) }
}

async function openDetail(row: DataMigrationContentRecycleRow) {
  detailTarget.value = row
  detail.value = null
  detailError.value = ''
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await getDataMigrationRecycleBinDetail(row.asset_type, row.id)
    detail.value = response.data.data ?? null
    if (!detail.value) detailError.value = '详情为空，请稍后重试'
  } catch (e) {
    detailError.value = apiErrorMessage(e, '详情加载失败')
  } finally { detailLoading.value = false }
}

function retryDetail() {
  if (detailTarget.value) return openDetail(detailTarget.value)
}

async function load() {
  loading.value = true
  error.value = ''
  forbidden.value = false
  selected.value = []
  try {
    const response = await listDataMigrationRecycleBin({
      contentTypes: selectedType.value ? [selectedType.value] : undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    const payload = response.data.data
    rows.value = payload?.records ?? []
    total.value = payload?.total ?? 0
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '回收站列表加载失败')
  } finally { loading.value = false }
}

function reloadKeepingSelection() {
  page.value = 1
  return load()
}

function onPageChange(next: number) {
  page.value = next
  return load()
}

function onSizeChange(next: number) {
  size.value = next
  page.value = 1
  return load()
}

function onSelectionChange(next: DataMigrationContentRecycleRow[]) {
  selected.value = next
}

/** 按内容类型分组，逐类调用后端分发接口（恢复/彻底删除均携带类型）。 */
function groupByType(items: DataMigrationContentRecycleRow[]) {
  const groups = new Map<string, number[]>()
  for (const item of items) {
    const ids = groups.get(item.asset_type) ?? []
    ids.push(item.id)
    groups.set(item.asset_type, ids)
  }
  return groups
}

async function runBatch(items: DataMigrationContentRecycleRow[], mode: 'restore' | 'purge') {
  if (!items.length) return
  const groups = groupByType(items)
  try {
    for (const [type, ids] of groups) {
      if (mode === 'restore') await restoreDataMigrationAssets(type, ids)
      else await purgeDataMigrationAssets(type, ids)
    }
    ElMessage.success(mode === 'restore' ? '已恢复' : '已彻底清理')
    await load()
  } catch (e) {
    ElMessage.error(messageOf(e))
  }
}

async function restoreSelected() {
  if (!hasSelection.value) return
  actionBusy.value = true
  try { await runBatch(selected.value, 'restore') } finally { actionBusy.value = false }
}

async function purgeSelected() {
  if (!hasSelection.value) return
  try {
    await ElMessageBox.confirm(`确认彻底清理选中的 ${selected.value.length} 条记录吗？清理后将删除业务记录及文件且不可恢复，审计记录仍会保留。`, '彻底清理', { type: 'warning', confirmButtonText: '确认清理', cancelButtonText: '取消' })
  } catch (e) {
    if (cancelled(e)) return
    ElMessage.error(messageOf(e))
    return
  }
  actionBusy.value = true
  try { await runBatch(selected.value, 'purge') } finally { actionBusy.value = false }
}

async function restoreOne(row: DataMigrationContentRecycleRow) {
  actionBusy.value = true
  try { await runBatch([row], 'restore') } finally { actionBusy.value = false }
}

async function purgeOne(row: DataMigrationContentRecycleRow) {
  try {
    await ElMessageBox.confirm(`确认彻底清理「${row.asset_name}」吗？清理后不可恢复。`, '彻底清理', { type: 'warning', confirmButtonText: '确认清理', cancelButtonText: '取消' })
  } catch (e) {
    if (cancelled(e)) return
    ElMessage.error(messageOf(e))
    return
  }
  actionBusy.value = true
  try { await runBatch([row], 'purge') } finally { actionBusy.value = false }
}

onMounted(load)
</script>

<template>
  <main class="dm-page-root">
    <UiPageHeader title="回收站" description="管理各内容菜单（含汇报材料与会议纪要文档级）移入回收站的记录，可按内容类型筛选、恢复或彻底清理；会议附件级回收站仍在会议页内管理。">
      <template #actions><el-button :disabled="loading || actionBusy" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template>
    </UiPageHeader>

    <section v-if="forbidden" class="dm-state-panel"><el-result icon="warning" title="暂无回收站查看权限" sub-title="请向数据迁移管理员申请回收站管理权限。" /></section>
    <section v-else-if="error" class="dm-state-panel"><el-result icon="error" title="回收站加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-select v-model="selectedType" placeholder="全部内容类型" clearable style="width: 200px" @change="reloadKeepingSelection">
          <el-option v-for="opt in typeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input v-model="keyword" clearable placeholder="搜索编号或名称" style="width: 220px" @keyup.enter="reloadKeepingSelection">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="reloadKeepingSelection"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="!hasSelection || actionBusy" @click="restoreSelected">恢复 ({{ selected.length }})</el-button>
          <el-button type="danger" plain :disabled="!hasSelection || actionBusy" @click="purgeSelected"><el-icon><Delete /></el-icon>彻底清理 ({{ selected.length }})</el-button>
        </template>
      </UiToolbar>

      <UiDataTable v-if="rows.length || loading" :data="rows" :loading="loading" row-key="id" border empty-text="回收站暂无记录" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column label="内容类型" width="150">
          <template #default="{ row }">{{ typeLabel(row.asset_type) }}</template>
        </el-table-column>
        <el-table-column prop="asset_code" label="编号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="asset_name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="删除人" width="120">
          <template #default="{ row }">{{ row.deleted_by_name || '—' }}</template>
        </el-table-column>
        <el-table-column prop="deleted_at" label="删除时间" width="180">
          <template #default="{ row }">{{ row.deleted_at || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="actionBusy" @click="openDetail(row)"><el-icon><View /></el-icon>查看</el-button>
            <el-button link type="primary" :disabled="actionBusy" @click="restoreOne(row)">恢复</el-button>
            <el-button link type="danger" :disabled="actionBusy" @click="purgeOne(row)">彻底清理</el-button>
          </template>
        </el-table-column>
      </UiDataTable>
      <div v-if="total > 0" class="dm-table-footer">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :disabled="loading || actionBusy"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
      <UiEmptyState v-if="!loading && !rows.length" title="回收站暂无记录" description="各内容菜单删除的记录会移入回收站，可在此恢复或彻底清理。" />
    </template>

    <el-drawer v-model="detailVisible" :title="detailTarget ? `查看${typeLabel(detailTarget.asset_type)}明细` : '查看明细'" size="min(760px, calc(100vw - 24px))" append-to-body>
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <el-result v-else-if="detailError" icon="error" title="详情加载失败" :sub-title="detailError">
        <template #extra><el-button type="primary" @click="retryDetail">重新加载</el-button></template>
      </el-result>
      <template v-else-if="detail">
        <el-divider content-position="left">基本信息与审计</el-divider>
        <el-descriptions :column="1" border class="dm-detail-descriptions">
          <el-descriptions-item v-for="([label, value], index) in standardDetail" :key="`${label}-${index}`" :label="label">{{ formatDetailValue(value) }}</el-descriptions-item>
        </el-descriptions>
        <template v-if="extraDetail.length">
          <el-divider content-position="left">类型专属信息</el-divider>
          <el-descriptions :column="1" border class="dm-detail-descriptions">
            <el-descriptions-item v-for="item in extraDetail" :key="item.label" :label="item.label">
              <pre v-if="item.value.startsWith('{') || item.value.startsWith('[')" class="dm-detail-json">{{ item.value }}</pre>
              <span v-else>{{ item.value }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </template>
    </el-drawer>
  </main>
</template>

<style scoped>
.dm-detail-descriptions { margin-bottom: 16px; }
.dm-detail-json { margin: 0; max-width: 100%; white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; line-height: 1.5; }
@media (max-width: 600px) {
  :deep(.el-drawer__body) { padding: 16px; overflow-y: auto; }
}
</style>
