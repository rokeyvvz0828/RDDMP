<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { apiErrorMessage } from '../api/error'
import { getAuditCapabilities, getAuditProjects, getLoginAuditLogs, getOperationAuditLogs } from '../api/system'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import type { AuditProjectOption, LoginAuditRecord, OperationAuditRecord } from '../types/system'

type AuditTab = 'operations' | 'logins'
type ResultFilter = '' | boolean

const moduleOptions = [
  { value: 'system', label: '系统管理' },
  { value: 'project', label: '项目管理' },
  { value: 'workflow', label: '工作流' },
  { value: 'release', label: '配置管理' },
  { value: 'requirement', label: '需求管理' },
  { value: 'architecture', label: '架构管理' },
  { value: 'test-management', label: '测试管理' },
  { value: 'data-migration', label: '数据迁移' },
  { value: 'attachment', label: '附件管理' },
  { value: 'ai', label: '智能能力' },
  { value: 'security', label: '账号安全' }
]
const operationTypeLabels: Record<string, string> = {
  CREATE: '新增', UPDATE: '修改', DELETE: '删除', APPROVAL: '审批',
  IMPORT: '导入', EXPORT: '导出', SENSITIVE_QUERY: '敏感查询'
}

const activeTab = ref<AuditTab>('operations')
const loading = ref(false)
const operations = ref<OperationAuditRecord[]>([])
const logins = ref<LoginAuditRecord[]>([])
const projects = ref<AuditProjectOption[]>([])
const canViewLoginAudit = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const dateRange = ref<[string, string]>(defaultDateRange())
const moduleCode = ref('')
const operationType = ref('')
const success = ref<ResultFilter>('')
const projectId = ref<number | undefined>()
const detailOpen = ref(false)
const selectedOperation = ref<OperationAuditRecord | null>(null)
const selectedLogin = ref<LoginAuditRecord | null>(null)

const currentRows = computed(() => activeTab.value === 'operations' ? operations.value : logins.value)

function dateText(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function defaultDateRange(): [string, string] {
  const end = new Date()
  const start = new Date(end)
  start.setDate(start.getDate() - 30)
  return [dateText(start), dateText(end)]
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const source = value.trim().replace(' ', 'T')
  const date = new Date(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(source) ? source : `${source}Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false, timeZone: 'Asia/Shanghai'
  }).format(date)
}

function operationLabel(value?: string) {
  return value ? operationTypeLabels[value] || value : '-'
}

function targetLabel(row: OperationAuditRecord) {
  if (!row.targetType && !row.targetId) return '-'
  return [row.targetType, row.targetId].filter(Boolean).join(' / ')
}

function resultParams() {
  return success.value === '' ? {} : { success: success.value }
}

async function load() {
  loading.value = true
  try {
    const dates = dateRange.value || defaultDateRange()
    if (activeTab.value === 'operations') {
      const response = await getOperationAuditLogs({
        page: page.value,
        size: size.value,
        startDate: dates[0],
        endDate: dates[1],
        moduleCode: moduleCode.value || undefined,
        operationType: operationType.value || undefined,
        projectId: projectId.value,
        keyword: keyword.value.trim() || undefined,
        ...resultParams()
      })
      operations.value = response.data.data.records
      total.value = response.data.data.total
    } else {
      const response = await getLoginAuditLogs({
        page: page.value,
        size: size.value,
        startDate: dates[0],
        endDate: dates[1],
        keyword: keyword.value.trim() || undefined,
        ...resultParams()
      })
      logins.value = response.data.data.records
      total.value = response.data.data.total
    }
  } catch (error) {
    operations.value = []
    logins.value = []
    total.value = 0
    ElMessage.error(apiErrorMessage(error, '审计日志加载失败'))
  } finally {
    loading.value = false
  }
}

async function initialize() {
  loading.value = true
  try {
    const [capabilities, projectOptions] = await Promise.all([getAuditCapabilities(), getAuditProjects()])
    canViewLoginAudit.value = capabilities.data.data.loginAudit
    projects.value = projectOptions.data.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '审计权限信息加载失败'))
  } finally {
    loading.value = false
  }
  await load()
}

function query() {
  page.value = 1
  load()
}

function reset() {
  dateRange.value = defaultDateRange()
  keyword.value = ''
  moduleCode.value = ''
  operationType.value = ''
  success.value = ''
  projectId.value = undefined
  query()
}

function openOperation(row: OperationAuditRecord) {
  selectedOperation.value = row
  selectedLogin.value = null
  detailOpen.value = true
}

function openLogin(row: LoginAuditRecord) {
  selectedLogin.value = row
  selectedOperation.value = null
  detailOpen.value = true
}

function onPageChange(value: number) {
  page.value = value
  load()
}

function onSizeChange(value: number) {
  size.value = value
  page.value = 1
  load()
}

watch(activeTab, () => {
  page.value = 1
  total.value = 0
  load()
})

onMounted(initialize)
</script>

<template>
  <section class="audit-page">
    <UiPageHeader eyebrow="系统管理" title="审计日志" />

    <el-tabs v-model="activeTab" class="audit-tabs">
      <el-tab-pane label="操作日志" name="operations" />
      <el-tab-pane v-if="canViewLoginAudit" label="登录日志" name="logins" />
    </el-tabs>

    <UiToolbar class="audit-toolbar">
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" format="YYYY-MM-DD"
        range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" :clearable="false" />
      <el-select v-if="activeTab === 'operations'" v-model="projectId" clearable filterable placeholder="所属项目" class="audit-filter-select">
        <el-option v-for="project in projects" :key="project.id" :value="project.id" :label="`${project.projectName} (${project.projectCode})`" />
      </el-select>
      <el-select v-if="activeTab === 'operations'" v-model="moduleCode" clearable placeholder="操作模块" class="audit-filter-select">
        <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-if="activeTab === 'operations'" v-model="operationType" clearable placeholder="操作类型" class="audit-filter-select">
        <el-option v-for="(label, value) in operationTypeLabels" :key="value" :label="label" :value="value" />
      </el-select>
      <el-select v-model="success" placeholder="执行结果" class="audit-result-select">
        <el-option label="全部结果" value="" />
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-input v-model="keyword" clearable :placeholder="activeTab === 'operations' ? '用户、路径或对象' : '账号或 IP'"
        class="audit-keyword" @keyup.enter="query"><template #prefix><el-icon><Search /></el-icon></template></el-input>
      <template #actions>
        <el-button @click="reset"><el-icon><Refresh /></el-icon>重置</el-button>
        <el-button type="primary" @click="query"><el-icon><Search /></el-icon>查询</el-button>
      </template>
    </UiToolbar>

    <UiDataTable v-if="activeTab === 'operations'" class="audit-table audit-table--desktop" :data="operations" :loading="loading" row-key="id" border empty-text="暂无操作日志">
      <el-table-column label="时间" width="166"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作人" min-width="120"><template #default="{ row }">{{ row.operatorName || `用户 #${row.operatorId}` }}</template></el-table-column>
      <el-table-column label="模块" min-width="100"><template #default="{ row }">{{ row.moduleName || row.moduleCode || '-' }}</template></el-table-column>
      <el-table-column label="类型" width="100"><template #default="{ row }">{{ operationLabel(row.operationType) }}</template></el-table-column>
      <el-table-column label="所属项目" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.projectName || '平台级' }}</template></el-table-column>
      <el-table-column label="操作对象" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ targetLabel(row) }}</template></el-table-column>
      <el-table-column label="结果" width="90"><template #default="{ row }"><UiStatusTag :value="row.success" :labels="{ true: '成功', false: '失败' }" :tone="row.success ? 'success' : 'danger'" /></template></el-table-column>
      <el-table-column label="耗时" width="86"><template #default="{ row }">{{ row.durationMs }} ms</template></el-table-column>
      <el-table-column label="详情" width="76" fixed="right"><template #default="{ row }"><el-tooltip content="查看详情"><el-button link type="primary" :icon="View" aria-label="查看审计详情" @click="openOperation(row)" /></el-tooltip></template></el-table-column>
      <template #footer><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, sizes, prev, pager, next" @current-change="onPageChange" @size-change="onSizeChange" /></template>
    </UiDataTable>

    <UiDataTable v-else class="audit-table audit-table--desktop" :data="logins" :loading="loading" row-key="id" border empty-text="暂无登录日志">
      <el-table-column label="时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
      <el-table-column prop="username" label="登录账号" min-width="180" />
      <el-table-column label="结果" width="100"><template #default="{ row }"><UiStatusTag :value="row.success" :labels="{ true: '成功', false: '失败' }" :tone="row.success ? 'success' : 'danger'" /></template></el-table-column>
      <el-table-column prop="clientIp" label="来源 IP" min-width="160" />
      <el-table-column label="失败原因" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.failureReason || '-' }}</template></el-table-column>
      <el-table-column label="详情" width="76" fixed="right"><template #default="{ row }"><el-tooltip content="查看详情"><el-button link type="primary" :icon="View" aria-label="查看登录详情" @click="openLogin(row)" /></el-tooltip></template></el-table-column>
      <template #footer><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, sizes, prev, pager, next" @current-change="onPageChange" @size-change="onSizeChange" /></template>
    </UiDataTable>

    <div v-loading="loading" class="audit-mobile-list">
      <article v-for="row in currentRows" :key="row.id" class="audit-mobile-card">
        <template v-if="activeTab === 'operations'">
          <header><div><span>{{ operationLabel((row as OperationAuditRecord).operationType) }}</span><strong>{{ (row as OperationAuditRecord).moduleName || (row as OperationAuditRecord).moduleCode || '平台能力' }}</strong></div><UiStatusTag :value="(row as OperationAuditRecord).success" :labels="{ true: '成功', false: '失败' }" :tone="(row as OperationAuditRecord).success ? 'success' : 'danger'" /></header>
          <dl><div><dt>操作人</dt><dd>{{ (row as OperationAuditRecord).operatorName || `用户 #${(row as OperationAuditRecord).operatorId}` }}</dd></div><div><dt>所属项目</dt><dd>{{ (row as OperationAuditRecord).projectName || '平台级' }}</dd></div><div><dt>操作对象</dt><dd>{{ targetLabel(row as OperationAuditRecord) }}</dd></div><div><dt>耗时</dt><dd>{{ (row as OperationAuditRecord).durationMs }} ms</dd></div></dl>
          <footer><time>{{ formatDateTime((row as OperationAuditRecord).createdAt) }}</time><el-button link type="primary" @click="openOperation(row as OperationAuditRecord)"><el-icon><View /></el-icon>详情</el-button></footer>
        </template>
        <template v-else>
          <header><div><span>登录账号</span><strong>{{ (row as LoginAuditRecord).username }}</strong></div><UiStatusTag :value="(row as LoginAuditRecord).success" :labels="{ true: '成功', false: '失败' }" :tone="(row as LoginAuditRecord).success ? 'success' : 'danger'" /></header>
          <dl><div><dt>来源 IP</dt><dd>{{ (row as LoginAuditRecord).clientIp || '-' }}</dd></div><div><dt>失败原因</dt><dd>{{ (row as LoginAuditRecord).failureReason || '-' }}</dd></div></dl>
          <footer><time>{{ formatDateTime((row as LoginAuditRecord).createdAt) }}</time><el-button link type="primary" @click="openLogin(row as LoginAuditRecord)"><el-icon><View /></el-icon>详情</el-button></footer>
        </template>
      </article>
      <el-empty v-if="!loading && !currentRows.length" description="暂无审计日志" />
      <div v-if="total" class="audit-mobile-pagination"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="onPageChange" /></div>
    </div>

    <el-drawer v-model="detailOpen" title="审计详情" class="audit-detail-drawer" size="min(560px, 92vw)" destroy-on-close>
      <el-descriptions v-if="selectedOperation" :column="1" border>
        <el-descriptions-item label="操作时间">{{ formatDateTime(selectedOperation.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ selectedOperation.operatorName || `用户 #${selectedOperation.operatorId}` }}</el-descriptions-item>
        <el-descriptions-item label="操作编码">{{ selectedOperation.operationCode }}</el-descriptions-item>
        <el-descriptions-item label="模块 / 类型">{{ selectedOperation.moduleName || selectedOperation.moduleCode || '-' }} / {{ operationLabel(selectedOperation.operationType) }}</el-descriptions-item>
        <el-descriptions-item label="所属项目">{{ selectedOperation.projectName || '平台级' }}</el-descriptions-item>
        <el-descriptions-item label="操作对象">{{ targetLabel(selectedOperation) }}</el-descriptions-item>
        <el-descriptions-item label="请求">{{ selectedOperation.requestMethod || '-' }} {{ selectedOperation.requestPath || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果 / 状态码">{{ selectedOperation.success ? '成功' : '失败' }} / {{ selectedOperation.httpStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ selectedOperation.durationMs }} ms</el-descriptions-item>
        <el-descriptions-item label="修改字段">{{ selectedOperation.changedFields?.length ? selectedOperation.changedFields.join('、') : '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源 IP">{{ selectedOperation.clientIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Trace ID">{{ selectedOperation.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户端">{{ selectedOperation.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="!selectedOperation.success" label="失败标记">{{ selectedOperation.errorMessage || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-descriptions v-else-if="selectedLogin" :column="1" border>
        <el-descriptions-item label="登录时间">{{ formatDateTime(selectedLogin.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="登录账号">{{ selectedLogin.username }}</el-descriptions-item>
        <el-descriptions-item label="执行结果">{{ selectedLogin.success ? '成功' : '失败' }}</el-descriptions-item>
        <el-descriptions-item label="失败原因">{{ selectedLogin.failureReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源 IP">{{ selectedLogin.clientIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户端">{{ selectedLogin.userAgent || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<style scoped>
.audit-page { min-width: 0; max-width: 1480px; margin: 0 auto; }
.audit-tabs { margin-bottom: 12px; }
.audit-toolbar { align-items: flex-start; }
.audit-toolbar :deep(.ui-toolbar__filters) { flex-wrap: wrap; }
.audit-filter-select { width: 150px; }
.audit-result-select { width: 120px; }
.audit-keyword { width: 210px; }
.audit-mobile-list { display: none; min-height: 120px; }
.audit-detail-drawer :deep(.el-descriptions__label) { width: 120px; }
.audit-detail-drawer :deep(.el-descriptions__content) { overflow-wrap: anywhere; }

@media (max-width: 760px) {
  .audit-toolbar :deep(.ui-toolbar__filters), .audit-toolbar :deep(.ui-toolbar__actions) { width: 100%; }
  .audit-toolbar :deep(.el-date-editor), .audit-filter-select, .audit-result-select, .audit-keyword { width: 100% !important; }
  .audit-toolbar :deep(.ui-toolbar__actions) { justify-content: flex-end; }
  .audit-table--desktop { display: none; }
  .audit-mobile-list { display: grid; grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .audit-mobile-card { min-width: 0; padding: 13px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel-bg); }
  .audit-mobile-card header, .audit-mobile-card footer { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 10px; }
  .audit-mobile-card header > div { display: grid; min-width: 0; gap: 3px; }
  .audit-mobile-card header span { color: var(--muted); font-size: 11px; }
  .audit-mobile-card header strong { overflow-wrap: anywhere; font-size: 14px; }
  .audit-mobile-card dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 12px; margin: 12px 0; padding: 11px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
  .audit-mobile-card dl > div { display: grid; min-width: 0; gap: 3px; }
  .audit-mobile-card dt { color: var(--muted); font-size: 11px; }
  .audit-mobile-card dd { min-width: 0; margin: 0; overflow-wrap: anywhere; color: var(--text); font-size: 12px; }
  .audit-mobile-card footer time { color: var(--muted); font-size: 11px; }
  .audit-mobile-pagination { min-width: 0; overflow-x: auto; padding: 8px 0; }
  .audit-detail-drawer :deep(.el-drawer__body) { padding: 12px; }
  .audit-detail-drawer :deep(.el-descriptions__label) { width: 92px; }
}

@media (max-width: 390px) {
  .audit-mobile-card dl { grid-template-columns: 1fr; }
}
</style>
