<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Download, Refresh, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadFiles } from 'element-plus'
import UiDataTable from '../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../components/ui/UiEmptyState.vue'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import UiStatusTag from '../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../components/ui/UiToolbar.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  confirmDeploymentUnitImport,
  downloadDeploymentUnitImportErrorReport,
  downloadDeploymentUnitImportTemplate,
  getDeploymentUnitImport,
  listDeploymentUnitImports,
  uploadDeploymentUnitImport
} from './api'
import type { DeploymentUnitImportBatch, DeploymentUnitImportBatchDetail, DeploymentUnitImportItem } from './types'
import {
  formatDateTime,
  httpStatus,
  importBatchStatusLabels,
  importBatchStatusTone,
  importItemStatusLabels,
  importItemStatusTone
} from './utils'
import './architecture.css'

const auth = useAuthStore()
const batches = ref<DeploymentUnitImportBatch[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const loadError = ref('')
const forbidden = ref(false)
const uploading = ref(false)
const confirming = ref(false)

const preview = ref<DeploymentUnitImportBatchDetail | null>(null)
const previewOpen = ref(false)
const previewLoading = ref(false)

const historyOpen = ref(false)
const historyLoading = ref(false)
const history = ref<DeploymentUnitImportBatchDetail | null>(null)

let listRequest = 0
let detailRequest = 0

const canView = computed(() => auth.hasPermission('architecture:deployment-unit:view')
  || auth.hasPermission('architecture:deployment-unit:manage')
  || ['architecture:view', 'architecture:apply', 'architecture:manage'].some(p => auth.hasPermission(p)))
const canManage = computed(() => auth.hasPermission('architecture:deployment-unit:manage'))

async function load() {
  if (!canView.value) return
  const request = ++listRequest
  loading.value = true
  loadError.value = ''
  forbidden.value = false
  try {
    const result = await listDeploymentUnitImports({ page: page.value, size: pageSize.value })
    if (request !== listRequest) return
    batches.value = result.records
    total.value = result.total
  } catch (error) {
    if (request !== listRequest) return
    if (httpStatus(error) === 403) forbidden.value = true
    else loadError.value = apiErrorMessage(error, '导入批次加载失败')
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function handleFile(file: UploadFile) {
  if (!canManage.value) return
  if (!file.raw) return
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('仅支持 .xlsx 格式的 Excel 文件')
    return false
  }
  uploading.value = true
  previewLoading.value = true
  preview.value = null
  previewOpen.value = true
  try {
    const result = await uploadDeploymentUnitImport(file.raw)
    preview.value = result
    if (result.batch.status === 'PREVIEW') {
      ElMessage.success(`文件已解析：共 ${result.batch.totalRows} 行，${result.batch.validRows} 行可写入，请核对后确认写入`)
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '导入文件解析失败'))
  } finally {
    uploading.value = false
    previewLoading.value = false
  }
  return false
}

async function confirmWrite() {
  if (!preview.value || confirming.value) return
  const batch = preview.value.batch
  if (batch.status !== 'PREVIEW') return
  try {
    await ElMessageBox.confirm(
      `确认写入第 ${batch.id} 批次的 ${batch.validRows} 行有效数据？写入后将分配永久编号，失败行记录明细不影响成功行。`,
      '确认写入部署单元',
      { confirmButtonText: '确认写入', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  confirming.value = true
  try {
    const result = await confirmDeploymentUnitImport(batch.id)
    preview.value = result
    ElMessage.success(result.batch.status === 'SUCCESS' ? '导入完成：全部行已写入' : `导入完成：成功 ${result.batch.successRows} 行，失败 ${result.batch.failedRows} 行`)
    void load()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '确认写入失败'))
  } finally {
    confirming.value = false
  }
}

async function showHistory(row: DeploymentUnitImportBatch) {
  const request = ++detailRequest
  history.value = null
  historyOpen.value = true
  historyLoading.value = true
  try {
    const result = await getDeploymentUnitImport(row.id)
    if (request === detailRequest) history.value = result
  } catch (error) {
    if (request === detailRequest) ElMessage.error(apiErrorMessage(error, '批次详情加载失败'))
  } finally {
    if (request === detailRequest) historyLoading.value = false
  }
}

async function downloadErrorReport(row: DeploymentUnitImportBatch) {
  try {
    await downloadDeploymentUnitImportErrorReport(row.id)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '错误报告下载失败'))
  }
}

async function downloadTemplate() {
  try {
    await downloadDeploymentUnitImportTemplate()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '模板下载失败'))
  }
}

function search() { page.value = 1; void load() }
function changePage(value: number) { page.value = value; void load() }
function changePageSize(value: number) { pageSize.value = value; page.value = 1; void load() }

function itemMessage(item: DeploymentUnitImportItem) {
  if (item.rowStatus === 'VALID') return item.note || '可写入'
  return item.errorMessage || item.note || '—'
}

watch(canView, allowed => { if (allowed) void load() }, { immediate: true })
</script>

<template>
  <main class="architecture-page">
    <UiPageHeader title="部署单元初始化导入" description="上传 Excel 模板文件，先预览与关系校验，确认后写入；每个批次保留来源、结果明细与错误报告。">
      <template #actions>
        <el-button v-if="canManage" @click="downloadTemplate"><el-icon><Download /></el-icon>下载模板</el-button>
      </template>
    </UiPageHeader>

    <section v-if="auth.token && !auth.user" v-loading="true" class="architecture-state-panel" aria-label="正在确认访问权限" />
    <section v-else-if="!canView || forbidden" class="architecture-state-panel"><el-result icon="warning" title="暂无导入批次查看权限" sub-title="请申请 architecture:deployment-unit:view 权限。" /></section>
    <section v-else-if="loadError" class="architecture-state-panel"><el-result icon="error" title="导入批次加载失败" :sub-title="loadError"><template #extra><el-button type="primary" @click="load">重新加载</el-button></template></el-result></section>
    <template v-else>
      <section v-if="canManage" class="architecture-import-dropzone">
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept=".xlsx"
          :before-upload="() => false"
          :on-change="handleFile"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将 .xlsx 文件拖到此处，或<em>点击选择文件</em></div>
          <template #tip>
            <div class="el-upload__tip">模板列：物理子系统编号、部署单元简称、部署单元名称、部署单元类型（应用/数据库/消息队列）、描述、备注；最多 5000 行、10MB。</div>
          </template>
        </el-upload>
      </section>

      <UiToolbar>
        <span class="architecture-toolbar-title">导入批次台账</span>
        <el-button type="primary" @click="search">刷新</el-button>
        <template #actions><el-tooltip content="刷新批次"><el-button circle :loading="loading" aria-label="刷新导入批次" @click="load"><el-icon><Refresh /></el-icon></el-button></el-tooltip></template>
      </UiToolbar>

      <UiDataTable v-if="batches.length || loading" class="architecture-desktop-table" :data="batches" :loading="loading" row-key="id" border>
        <el-table-column label="批次" min-width="200"><template #default="scope"><button type="button" class="architecture-table-identity" @click="showHistory(scope.row)"><strong>批次 #{{ scope.row.id }}</strong><small>{{ scope.row.fileName }}</small></button></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="importBatchStatusLabels" :tone="importBatchStatusTone(scope.row.status)" /></template></el-table-column>
        <el-table-column label="总行数" width="80" prop="totalRows" />
        <el-table-column label="可写入" width="80" prop="validRows" />
        <el-table-column label="成功" width="80" prop="successRows" />
        <el-table-column label="失败" width="80" prop="failedRows" />
        <el-table-column label="操作人" min-width="110"><template #default="scope">{{ scope.row.createdByDisplayName || `用户 #${scope.row.createdBy}` }}</template></el-table-column>
        <el-table-column label="创建时间" width="145"><template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="130" fixed="right"><template #default="scope"><div class="architecture-table-actions"><el-button link type="primary" @click="showHistory(scope.row)">明细</el-button><el-button v-if="scope.row.failedRows > 0" link type="danger" @click="downloadErrorReport(scope.row)">错误报告</el-button></div></template></el-table-column>
        <template #footer><div class="architecture-table-footer"><span>共 {{ total }} 条记录</span><el-pagination :current-page="page" :page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changePageSize" /></div></template>
      </UiDataTable>

      <div v-if="batches.length || loading" v-loading="loading" class="architecture-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in batches" :key="row.id"><header><div><strong>批次 #{{ row.id }}</strong><small>{{ row.fileName }}</small></div><UiStatusTag :value="row.status" :labels="importBatchStatusLabels" :tone="importBatchStatusTone(row.status)" /></header><dl><div><dt>行数</dt><dd>共 {{ row.totalRows }} · 可写 {{ row.validRows }} · 成功 {{ row.successRows }} · 失败 {{ row.failedRows }}</dd></div><div><dt>操作人</dt><dd>{{ row.createdByDisplayName || `用户 #${row.createdBy}` }}</dd></div><div><dt>创建时间</dt><dd>{{ formatDateTime(row.createdAt) }}</dd></div></dl><footer><el-button link type="primary" @click="showHistory(row)">明细</el-button><el-button v-if="row.failedRows > 0" link type="danger" @click="downloadErrorReport(row)">错误报告</el-button></footer></article>
        <div class="architecture-table-footer"><el-pagination :current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="changePage" /></div>
      </div>
      <UiEmptyState v-if="!loading && !batches.length" title="暂无导入批次" description="上传 Excel 文件开始第一次部署单元初始化导入。"><template #action><el-button v-if="canManage" type="primary" @click="downloadTemplate">下载模板</el-button></template></UiEmptyState>
    </template>

    <!-- 预览与确认写入 -->
    <el-drawer v-model="previewOpen" title="导入预览" size="min(760px, 96vw)" destroy-on-close>
      <div v-loading="previewLoading" class="architecture-drawer-body">
        <template v-if="preview">
          <div class="architecture-import-summary">
            <span><strong>{{ preview.batch.totalRows }}</strong> 总行数</span>
            <span><strong>{{ preview.batch.validRows }}</strong> 可写入</span>
            <span><strong>{{ preview.batch.successRows }}</strong> 已成功</span>
            <span><strong>{{ preview.batch.failedRows }}</strong> 失败</span>
            <UiStatusTag :value="preview.batch.status" :labels="importBatchStatusLabels" :tone="importBatchStatusTone(preview.batch.status)" />
          </div>
          <div v-if="preview.batch.errorMessage" class="architecture-import-error">{{ preview.batch.errorMessage }}</div>
          <el-table :data="preview.items" size="small" border max-height="52vh">
            <el-table-column label="行号" prop="lineNo" width="64" />
            <el-table-column label="物理子系统" min-width="110"><template #default="scope">{{ scope.row.row.physicalCode || '—' }}</template></el-table-column>
            <el-table-column label="简称" min-width="100"><template #default="scope">{{ scope.row.row.shortName || '—' }}</template></el-table-column>
            <el-table-column label="名称" min-width="150"><template #default="scope">{{ scope.row.row.name || '—' }}</template></el-table-column>
            <el-table-column label="类型" width="90"><template #default="scope">{{ scope.row.row.kindLabel || '—' }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="scope"><UiStatusTag :value="scope.row.rowStatus" :labels="importItemStatusLabels" :tone="importItemStatusTone(scope.row.rowStatus)" /></template></el-table-column>
            <el-table-column label="说明" min-width="180"><template #default="scope">{{ itemMessage(scope.row) }}</template></el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <div class="architecture-drawer-actions">
          <el-button v-if="preview?.batch.status === 'PREVIEW'" type="primary" :loading="confirming" @click="confirmWrite">确认写入</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 批次历史明细 -->
    <el-drawer v-model="historyOpen" title="批次明细" size="min(760px, 96vw)" destroy-on-close>
      <div v-loading="historyLoading" class="architecture-drawer-body">
        <template v-if="history">
          <div class="architecture-import-summary">
            <span><strong>{{ history.batch.totalRows }}</strong> 总行数</span>
            <span><strong>{{ history.batch.validRows }}</strong> 可写入</span>
            <span><strong>{{ history.batch.successRows }}</strong> 已成功</span>
            <span><strong>{{ history.batch.failedRows }}</strong> 失败</span>
            <UiStatusTag :value="history.batch.status" :labels="importBatchStatusLabels" :tone="importBatchStatusTone(history.batch.status)" />
          </div>
          <p class="architecture-muted">来源文件：{{ history.batch.fileName }} · 操作人 {{ history.batch.createdByDisplayName }} · {{ formatDateTime(history.batch.createdAt) }}</p>
          <el-table :data="history.items" size="small" border max-height="52vh">
            <el-table-column label="行号" prop="lineNo" width="64" />
            <el-table-column label="物理子系统" min-width="110"><template #default="scope">{{ scope.row.row.physicalCode || '—' }}</template></el-table-column>
            <el-table-column label="名称" min-width="160"><template #default="scope">{{ scope.row.row.name || '—' }}</template></el-table-column>
            <el-table-column label="类型" width="90"><template #default="scope">{{ scope.row.row.kindLabel || '—' }}</template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="scope"><UiStatusTag :value="scope.row.rowStatus" :labels="importItemStatusLabels" :tone="importItemStatusTone(scope.row.rowStatus)" /></template></el-table-column>
            <el-table-column label="说明" min-width="200"><template #default="scope">{{ itemMessage(scope.row) }}</template></el-table-column>
          </el-table>
          <div v-if="history.batch.failedRows > 0" class="architecture-import-error-actions">
            <el-button type="danger" plain @click="downloadDeploymentUnitImportErrorReport(history!.batch.id)"><el-icon><Download /></el-icon>导出错误报告</el-button>
          </div>
        </template>
      </div>
    </el-drawer>
  </main>
</template>
