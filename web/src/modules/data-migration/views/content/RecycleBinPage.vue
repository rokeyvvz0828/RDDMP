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
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import {
  listDataMigrationRecycleBin,
  purgeDataMigrationAssets,
  restoreDataMigrationAssets,
  type DataMigrationContentRecycleRow,
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
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
  </main>
</template>
