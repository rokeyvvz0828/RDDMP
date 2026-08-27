<!--
  用途：数迁资产内容 - 回收站页
  说明：展示已移入回收站的文件资产，支持关键词查询、批量恢复、彻底清理（不可恢复）；
        使用 UiPageHeader + UiToolbar + UiDataTable，覆盖加载/空/失败/无权限/提交中状态，
        彻底清理为危险操作，需确认后执行。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Refresh, Search } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../../../components/ui/UiPageHeader.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../../../api/error'
import {
  listReportRecycleBin,
  purgeReportMaterials,
  restoreReportMaterials,
  getReportProjectOptions,
  type ReportMaterial
} from '../../../../api/data-migration'

const periodOptions = [
  { label: '日报', value: 'daily' },
  { label: '周报', value: 'weekly' },
  { label: '月报', value: 'monthly' },
  { label: '季报', value: 'quarterly' },
  { label: '年报', value: 'yearly' },
]

const loading = ref(false)
const error = ref('')
const forbidden = ref(false)
const assets = ref<ReportMaterial[]>([])
const keyword = ref('')
const selectedProject = ref<number | undefined>(undefined)
const selectedPeriod = ref('')
const deleteDateRange = ref<[string, string] | null>(null)
const selectedIds = ref<number[]>([])
const actionBusy = ref(false)

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试'
}

function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}

function cancelled(error: unknown) {
  const action = (error as { action?: string }).action
  return action === 'cancel' || action === 'close'
}

async function load() {
  loading.value = true
  error.value = ''
  forbidden.value = false
  selectedIds.value = []
  try {
    const response = await listReportRecycleBin({
      keyword: keyword.value || undefined,
      projectId: selectedProject.value || undefined,
      reportPeriod: selectedPeriod.value || undefined,
      deleteDateStart: deleteDateRange.value?.[0] || undefined,
      deleteDateEnd: deleteDateRange.value?.[1] || undefined
    })
    assets.value = response.data.data?.records ?? []
  } catch (e) {
    if (httpStatus(e) === 403) forbidden.value = true
    else error.value = apiErrorMessage(e, '回收站列表加载失败')
  } finally { loading.value = false }
}

const projectOptions = ref<{ label: string; value: number }[]>([])

async function loadProjectOptions() {
  try {
    const response = await getReportProjectOptions()
    const data = response.data.data ?? []
    projectOptions.value = data.map(item => ({
      label: item.project_name,
      value: item.id
    }))
  } catch {
    // 静默失败，使用空列表
  }
}

function onSelectionChange(rows: ReportMaterial[]) {
  selectedIds.value = rows.map(row => row.id)
}

async function restoreSelected() {
  if (!selectedIds.value.length) return
  actionBusy.value = true
  try {
    await restoreReportMaterials(selectedIds.value)
    ElMessage.success('已恢复')
    await load()
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally { actionBusy.value = false }
}

async function purgeSelected() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认彻底清理选中的 ${selectedIds.value.length} 个材料吗？清理后将删除业务记录及文件且不可恢复，审计记录仍会保留。`, '彻底清理', { type: 'warning', confirmButtonText: '确认清理', cancelButtonText: '取消' })
    actionBusy.value = true
    await purgeReportMaterials(selectedIds.value)
    ElMessage.success('已彻底清理')
    await load()
  } catch (e) {
    if (!cancelled(e)) ElMessage.error(messageOf(e))
  } finally { actionBusy.value = false }
}

onMounted(() => {
  load()
  loadProjectOptions()
})
</script>

<template>
  <main class="dm-page-root">
    <UiPageHeader title="回收站" description="管理已移入回收站的汇报材料，可恢复或彻底清理。">
      <template #actions><el-button :disabled="loading || actionBusy" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button></template>
    </UiPageHeader>

    <section v-if="forbidden" class="dm-state-panel"><el-result icon="warning" title="暂无回收站查看权限" sub-title="请向数据迁移管理员申请回收站管理权限。" /></section>
    <section v-else-if="error" class="dm-state-panel"><el-result icon="error" title="回收站加载失败" :sub-title="error"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <UiToolbar>
        <el-select v-model="selectedProject" placeholder="所属项目" clearable style="width: 200px" @change="load">
          <el-option v-for="p in projectOptions" :key="p.value" :label="p.label" :value="p.value" />
        </el-select>
        <el-select v-model="selectedPeriod" placeholder="汇报周期" clearable style="width: 180px" @change="load">
          <el-option v-for="p in periodOptions" :key="p.value" :label="p.label" :value="p.value" />
        </el-select>
        <el-input v-model="keyword" clearable placeholder="搜索资料名称或关键字" style="width: 200px" @keyup.enter="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-date-picker
          v-model="deleteDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="删除开始日期"
          end-placeholder="删除结束日期"
          value-format="YYYY-MM-DD"
          style="width: 300px"
          @change="load"
        />
        <template #actions>
          <el-button :disabled="loading || actionBusy" @click="load"><el-icon><Search /></el-icon>查询</el-button>
          <el-button :disabled="!selectedIds.length || actionBusy" @click="restoreSelected">恢复 ({{ selectedIds.length }})</el-button>
          <el-button type="danger" plain :disabled="!selectedIds.length || actionBusy" @click="purgeSelected"><el-icon><Delete /></el-icon>彻底清理 ({{ selectedIds.length }})</el-button>
        </template>
      </UiToolbar>

      <UiDataTable v-if="assets.length || loading" :data="assets" :loading="loading" row-key="id" border empty-text="回收站暂无材料" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="projectName" label="所属项目" min-width="150" />
        <el-table-column prop="reportPeriod" label="汇报周期" width="120" />
        <el-table-column prop="reportName" label="资料名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="reportDate" label="汇报日期" width="120">
          <template #default="{ row }">{{ row.reportDate || '—' }}</template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键字索引" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.keywords || '—' }}</template>
        </el-table-column>
        <el-table-column label="文件" min-width="200">
          <template #default="{ row }">
            <span v-if="row.originalName">{{ row.originalName }}{{ row.extension }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="deleteTime" label="删除时间" width="180" />
      </UiDataTable>
      <UiEmptyState v-if="!loading && !assets.length" title="回收站暂无材料" description="删除的汇报材料会移入回收站，可在此恢复或彻底清理。" />
    </template>
  </main>
</template>
