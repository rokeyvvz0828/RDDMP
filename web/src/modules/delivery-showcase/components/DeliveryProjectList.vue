<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Delete, Download, Edit, Filter, MoreFilled, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import type { DeliveryProject, DemoAsyncState } from '../types'
import { owners } from '../mock'

const props = defineProps<{ projects: DeliveryProject[] }>()
const emit = defineEmits<{ detail: [project: DeliveryProject]; stepDetail: [project: DeliveryProject]; edit: [project: DeliveryProject]; risk: [project: DeliveryProject]; remove: [ids: number[]] }>()
const keyword = ref('')
const status = ref('')
const owner = ref('')
const type = ref('')
const dateRange = ref<[string, string] | null>(null)
const advanced = ref(false)
const page = ref(1)
const pageSize = ref(6)
const selected = ref<DeliveryProject[]>([])
const demoState = ref<DemoAsyncState>('normal')
const refreshing = ref(false)

const filtered = computed(() => props.projects.filter(project => {
  const textMatched = !keyword.value || `${project.code}${project.name}${project.owner}${project.department}`.toLowerCase().includes(keyword.value.toLowerCase())
  const rangeMatched = !dateRange.value || (project.startDate <= dateRange.value[1] && project.endDate >= dateRange.value[0])
  return textMatched && (!status.value || project.status === status.value) && (!owner.value || project.owner === owner.value) && (!type.value || project.type === type.value) && rangeMatched
}))
const rows = computed(() => demoState.value === 'empty' ? [] : filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
watch([keyword, status, owner, type, dateRange, pageSize], () => { page.value = 1 })
watch(demoState, value => { if (value === 'loading') window.setTimeout(() => { if (demoState.value === 'loading') demoState.value = 'normal' }, 1200) })
function statusType(value: DeliveryProject['status']) { return value === '已完成' ? 'success' : value === '有风险' ? 'danger' : value === '进行中' ? 'primary' : 'info' }
function reset() { keyword.value = ''; status.value = ''; owner.value = ''; type.value = ''; dateRange.value = null; page.value = 1 }
async function refresh() { refreshing.value = true; await new Promise(resolve => window.setTimeout(resolve, 500)); refreshing.value = false; ElMessage.success('列表已刷新') }
async function batchRemove() {
  await ElMessageBox.confirm(`将删除已选择的 ${selected.value.length} 个示范项目，此操作只影响当前页面会话。`, '批量删除', { type: 'warning', confirmButtonText: '删除项目', cancelButtonText: '取消' })
  emit('remove', selected.value.map(item => item.id)); selected.value = []
}
function exportRows() { ElMessage.success(`已生成 ${filtered.value.length} 条示范导出任务`) }
</script>

<template>
  <div class="delivery-project-list">
    <UiToolbar>
      <el-input v-model="keyword" clearable placeholder="项目编号、名称、负责人" class="delivery-search-input" @keyup.enter="page = 1"><template #prefix><el-icon><Search /></el-icon></template></el-input>
      <el-select v-model="status" clearable placeholder="交付状态" style="width:138px"><el-option v-for="item in ['进行中', '待启动', '有风险', '已完成']" :key="item" :label="item" :value="item" /></el-select>
      <el-select v-model="owner" clearable filterable placeholder="负责人" style="width:128px"><el-option v-for="item in owners" :key="item" :label="item" :value="item" /></el-select>
      <el-button :type="advanced ? 'primary' : 'default'" plain @click="advanced = !advanced"><el-icon><Filter /></el-icon>更多筛选</el-button>
      <template #actions><el-select v-model="demoState" aria-label="页面状态示例" style="width:132px"><el-option label="正常状态" value="normal" /><el-option label="加载状态" value="loading" /><el-option label="空数据" value="empty" /><el-option label="加载失败" value="error" /><el-option label="无权限" value="forbidden" /></el-select><el-tooltip content="刷新列表"><el-button circle :loading="refreshing" aria-label="刷新列表" @click="refresh"><el-icon><Refresh /></el-icon></el-button></el-tooltip><el-button @click="exportRows"><el-icon><Download /></el-icon>导出</el-button></template>
    </UiToolbar>

    <div v-if="advanced" class="delivery-advanced-filter"><el-form inline label-position="top"><el-form-item label="项目类型"><el-select v-model="type" clearable placeholder="全部类型" style="width:180px"><el-option v-for="item in ['产品迭代', '系统建设', '数据迁移', '基础设施']" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="计划周期"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item><el-form-item class="delivery-filter-actions"><el-button @click="reset">重置条件</el-button><el-button type="primary" @click="page = 1">应用筛选</el-button></el-form-item></el-form></div>

    <div v-if="selected.length" class="delivery-batch-bar"><strong>已选择 {{ selected.length }} 项</strong><span>批量操作仅针对当前明确选择的记录</span><div><el-button size="small" @click="exportRows"><el-icon><Download /></el-icon>导出所选</el-button><el-button size="small" type="danger" plain @click="batchRemove"><el-icon><Delete /></el-icon>删除</el-button></div></div>

    <section v-if="demoState === 'error'" class="delivery-state-panel"><el-result icon="error" title="项目列表加载失败" sub-title="示范状态：网络请求失败后保留筛选条件，用户可以重试。"><template #extra><el-button type="primary" @click="demoState = 'normal'">重新加载</el-button></template></el-result></section>
    <section v-else-if="demoState === 'forbidden'" class="delivery-state-panel"><el-result icon="warning" title="暂无项目查看权限" sub-title="请向交付管理员申请 delivery:project:view 权限。"><template #extra><el-button @click="demoState = 'normal'">返回正常示例</el-button></template></el-result></section>
    <template v-else>
      <UiDataTable class="delivery-project-table" :data="rows" :loading="demoState === 'loading'" row-key="id" border @selection-change="selected = $event">
        <el-table-column type="selection" width="46" /><el-table-column label="项目" min-width="248" sortable sort-by="name"><template #default="scope"><button class="delivery-project-cell" type="button" @click="emit('detail', scope.row)"><strong>{{ scope.row.name }}</strong><span>{{ scope.row.code }} · {{ scope.row.type }}</span></button></template></el-table-column>
        <el-table-column label="状态" width="102"><template #default="scope"><el-tag :type="statusType(scope.row.status)" effect="plain" size="small">{{ scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="stage" label="当前阶段" width="92" /><el-table-column prop="owner" label="负责人" width="88" />
        <el-table-column label="计划进度" width="160" sortable sort-by="progress"><template #default="scope"><div class="delivery-progress-cell"><el-progress :percentage="scope.row.progress" :stroke-width="7" /><small>{{ scope.row.endDate }}</small></div></template></el-table-column>
        <el-table-column label="风险" width="86"><template #default="scope"><el-tag v-if="scope.row.risks.length" type="danger" effect="light">{{ scope.row.risks.length }} 项</el-tag><span v-else class="delivery-muted">无</span></template></el-table-column>
        <el-table-column label="操作" width="214" fixed="right"><template #default="scope"><el-button link type="primary" @click="emit('detail', scope.row)"><el-icon><View /></el-icon>详情</el-button><el-button link type="primary" @click="emit('edit', scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-dropdown @command="(command: string) => command === 'steps' ? emit('stepDetail', scope.row) : emit('risk', scope.row)"><el-button link type="info"><el-icon><MoreFilled /></el-icon>更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="steps">分步详情</el-dropdown-item><el-dropdown-item command="risk">登记风险</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
        <template #footer><div class="delivery-table-footer"><span>共 {{ filtered.length }} 条，已展示 {{ rows.length }} 条</span><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="filtered.length" :page-sizes="[6, 10, 20]" layout="total, sizes, prev, pager, next" /></div></template>
      </UiDataTable>

      <div class="delivery-mobile-list" :class="{ 'is-loading': demoState === 'loading' }"><article v-for="project in rows" :key="project.id"><header><div><strong>{{ project.name }}</strong><small>{{ project.code }}</small></div><el-tag :type="statusType(project.status)" effect="plain" size="small">{{ project.status }}</el-tag></header><dl><div><dt>阶段</dt><dd>{{ project.stage }}</dd></div><div><dt>负责人</dt><dd>{{ project.owner }}</dd></div><div><dt>计划完成</dt><dd>{{ project.endDate }}</dd></div></dl><el-progress :percentage="project.progress" :stroke-width="7" /><footer><el-button link type="primary" @click="emit('detail', project)">查看详情</el-button><el-button link type="primary" @click="emit('edit', project)">编辑</el-button><el-button v-if="project.risks.length" link type="danger" @click="emit('risk', project)">风险 {{ project.risks.length }}</el-button></footer></article></div>
      <UiEmptyState v-if="!rows.length && demoState !== 'loading'" :title="filtered.length ? '当前页暂无数据' : '没有匹配的交付项目'" description="调整筛选条件或新建一个交付项目。"><template #action><el-button @click="reset">清空筛选</el-button></template></UiEmptyState>
    </template>
  </div>
</template>
