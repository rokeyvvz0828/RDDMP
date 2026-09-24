<!--
  用途：问题台账（基线第 13 章）
  说明：双渠道归集（手动/批量导入/工单同步 reconcile）；整改闭环后自动沉淀知识条目；
        闭环/作废禁改；执行人仅见本人问题。
-->
<script setup lang="ts">
import '../../data-migration.css'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import UiDataTable from '../../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../../components/ui/UiFormDrawer.vue'
import UiToolbar from '../../../../components/ui/UiToolbar.vue'
import UiPagination from '../../../../components/ui/UiPagination.vue'
import { apiErrorMessage } from '../../../../api/error'
import { useAuthStore } from '../../../../stores/auth'
import {
  closeLifecycleIssue,
  createLifecycleIssue,
  importLifecycleIssues,
  listLifecycleIssues,
  reconcileLifecycleIssues,
  rectifyLifecycleIssue
} from '../../../../api/data-migration-lifecycle'
import type { LifecycleIssueView } from '../../../../types/data-migration-lifecycle'

const auth = useAuthStore()
const isAdmin = computed(() => auth.hasPermission('system:admin') || auth.hasPermission('data-migration:manage'))

const loading = ref(false)
const forbidden = ref(false)
const error = ref('')
const records = ref<LifecycleIssueView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive<Record<string, unknown>>({ issueCode: '', issueStatus: '', issueSource: '' })

const STATUS_TEXT: Record<string, string> = { WAIT_RECTIFY: '待整改', RECTIFYING: '整改中', CLOSED: '已闭环', CANCELLED: '已作废' }
const SOURCE_TEXT: Record<string, string> = { MANUAL: '手动新增', IMPORT: '批量导入', ORDER_SYNC: '工单同步' }

async function loadList() {
  if (!auth.hasPermission('data-migration-lifecycle:issue') && !auth.hasPermission('data-migration:access') && !auth.hasPermission('system:admin')) {
    forbidden.value = true
    return
  }
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    Object.entries(filters).forEach(([k, v]) => { if (v !== '' && v !== undefined && v !== null) params[k] = v })
    const res = await listLifecycleIssues(params)
    records.value = res.data.data?.records ?? []
    total.value = res.data.data?.total ?? 0
  } catch (e) {
    error.value = apiErrorMessage(e, '问题台账加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, { issueCode: '', issueStatus: '', issueSource: '' })
  page.value = 1
  loadList()
}

// ===== 新增 =====
const createOpen = ref(false)
const saving = ref(false)
const form = reactive<Record<string, unknown>>({ issueTitle: '', issueDesc: '', scene: '', impactScope: '', blockDesc: '', rectifierId: undefined })
function openCreate() {
  Object.assign(form, { issueTitle: '', issueDesc: '', scene: '', impactScope: '', blockDesc: '', rectifierId: undefined })
  createOpen.value = true
}
async function submitCreate() {
  if (!String(form.issueTitle || '').trim()) { ElMessage.warning('问题标题必填'); return }
  if (!String(form.issueDesc || '').trim()) { ElMessage.warning('问题描述必填'); return }
  saving.value = true
  try {
    await createLifecycleIssue({ ...form })
    ElMessage.success('问题新增成功')
    createOpen.value = false
    page.value = 1
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '新增失败'))
  } finally { saving.value = false }
}

// ===== 批量导入 =====
const importOpen = ref(false)
const importRows = ref('')
interface ImportResult { inserted: number; anomalies: Array<{ row: number; issueCode?: string; issueTitle?: string; error?: string }> }
const importResult = ref<ImportResult | null>(null)
function openImport() {
  importResult.value = null
  importRows.value = ''
  importOpen.value = true
}
function downloadTemplate() {
  const header = 'issueTitle,issueDesc,issueCode,issueStatus,scene,impactScope'.split(',')
  const blob = new Blob([header.join(',')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '问题导入模板.csv'
  a.click()
  URL.revokeObjectURL(url)
}
async function submitImport() {
  const rows = importRows.value.split('\n').filter(l => l.trim()).map(line => {
    const cols = line.split(',').map(c => c.trim())
    return { issueTitle: cols[0], issueDesc: cols[1], issueCode: cols[2], issueStatus: cols[3], scene: cols[4], impactScope: cols[5] }
  })
  if (rows.length === 0) { ElMessage.warning('请输入至少 1 行数据'); return }
  saving.value = true
  try {
    const res = await importLifecycleIssues({ rows })
    const data = res.data.data as unknown as ImportResult
    importResult.value = data
    ElMessage.success(`导入完成：${res.data.data?.inserted ?? 0} 条入库，${((res.data.data as unknown as ImportResult | null)?.anomalies ?? []).length} 条异常（已列出明细不入库）`)
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '导入失败'))
  } finally { saving.value = false }
}
function downloadAnomalies() {
  if (!importResult.value?.anomalies?.length) return
  const lines = ['row,issueCode,issueTitle,error', ...importResult.value.anomalies.map(a =>
    `${a.row},${a.issueCode ?? ''},${a.issueTitle ?? ''},${a.error ?? ''}`)]
  const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '问题导入异常明细.csv'
  a.click()
  URL.revokeObjectURL(url)
}

// ===== 整改/闭环 =====
const operateOpen = ref(false)
const operateMode = ref<'RECTIFY' | 'CLOSE'>('RECTIFY')
const operateForm = reactive<Record<string, unknown>>({ issueId: undefined, rectifyProgress: '', record: '', solution: '' })
const currentRow = ref<LifecycleIssueView | null>(null)
function openOperate(row: LifecycleIssueView, mode: 'RECTIFY' | 'CLOSE') {
  currentRow.value = row
  operateMode.value = mode
  Object.assign(operateForm, { issueId: row.id, rectifyProgress: '', record: '', solution: '' })
  operateOpen.value = true
}
async function submitOperate() {
  if (operateMode.value === 'RECTIFY') {
    if (!String(operateForm.rectifyProgress || '').trim()) { ElMessage.warning('整改进度必填'); return }
    saving.value = true
    try {
      await rectifyLifecycleIssue(Number(operateForm.issueId), { rectifyProgress: operateForm.rectifyProgress, record: operateForm.record })
      ElMessage.success('整改进度已提交，问题进入整改中')
    } catch (e) {
      ElMessage.error(apiErrorMessage(e, '整改提交失败'))
    } finally { saving.value = false }
  } else {
    if (!String(operateForm.solution || '').trim()) { ElMessage.warning('闭环必须填写标准化解决方案'); return }
    saving.value = true
    try {
      const res = await closeLifecycleIssue(Number(operateForm.issueId), { solution: operateForm.solution })
      ElMessage.success(`已闭环并自动沉淀知识条目（KB-${res.data.data?.knowledgeId ?? ''}）`)
    } catch (e) {
      ElMessage.error(apiErrorMessage(e, '闭环失败'))
    } finally { saving.value = false }
  }
  if (!saving.value) {
    operateOpen.value = false
    loadList()
  }
}

async function reconcile() {
  loading.value = true
  try {
    const res = await reconcileLifecycleIssues()
    ElMessage.success(`工单问题归集完成，新增 ${res.data.data?.synced ?? 0} 条（来源=工单同步）`)
    loadList()
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '对账归集失败'))
  } finally { loading.value = false }
}

onMounted(loadList)
</script>

<template>
  <section class="dm-page-root">
    <UiToolbar>
      <template #extra><span class="muted">双渠道归集（手动/导入/工单同步 reconcile）；闭环自动沉淀知识条目；闭环/作废后禁改</span></template>
      <template #actions>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">新增问题</el-button>
        <el-button v-if="isAdmin" :icon="Upload" @click="openImport">批量导入</el-button>
        <el-button v-if="isAdmin" :icon="Download" @click="reconcile">对账归集</el-button>
        <el-button :icon="Refresh" @click="loadList">刷新</el-button>
      </template>
    </UiToolbar>

    <el-card shadow="never" class="ui-surface-card">
      <el-form inline class="dm-filter-bar">
        <el-form-item label="问题编号"><el-input v-model="filters.issueCode" clearable placeholder="IS-xxx" style="width: 150px" @keyup.enter="page = 1; loadList()" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.issueStatus" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="(label, code) in STATUS_TEXT" :key="code" :value="code" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="filters.issueSource" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="(label, code) in SOURCE_TEXT" :key="code" :value="code" :label="label" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="page = 1; loadList()">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <div v-if="forbidden" class="dm-state-panel"><UiEmptyState title="无权限查看问题台账" description="普通执行人仅可见本人上报/整改的问题" /></div>
      <div v-else-if="error" class="dm-state-panel"><UiEmptyState title="加载失败" :description="error" /></div>
      <template v-else>
        <div class="dm-desktop-table">
          <UiDataTable :loading="loading" :data="records" empty-text="暂无问题">
            <el-table-column prop="issueCode" label="问题编号" width="140" show-overflow-tooltip />
            <el-table-column prop="issueTitle" label="问题标题" min-width="180" show-overflow-tooltip />
            <el-table-column label="来源" width="100">
              <template #default="{ row }"><el-tag size="small" :type="row.issueSource === 'ORDER_SYNC' ? 'warning' : 'info'">{{ SOURCE_TEXT[row.issueSource] ?? row.issueSource }}</el-tag></template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.issueStatus === 'CLOSED' ? 'success' : row.issueStatus === 'CANCELLED' ? 'info' : row.issueStatus === 'RECTIFYING' ? 'warning' : 'primary'" size="small">
                  {{ STATUS_TEXT[row.issueStatus] ?? row.issueStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="首次上报" width="150">
              <template #default="{ row }">{{ row.firstReportedAt?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column label="整改闭环时间" width="150">
              <template #default="{ row }">{{ row.rectifiedAt?.replace('T', ' ') ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <template v-if="!(row.issueStatus === 'CLOSED' || row.issueStatus === 'CANCELLED')">
                  <el-button link type="warning" @click="openOperate(row, 'RECTIFY')">整改</el-button>
                  <el-button v-if="isAdmin" link type="success" @click="openOperate(row, 'CLOSE')">闭环</el-button>
                </template>
                <span v-else class="muted">已固化</span>
              </template>
            </el-table-column>
          </UiDataTable>
        </div>

        <div v-if="records.length === 0 && !loading" class="dm-state-panel"><UiEmptyState title="暂无问题" description="手动新增、批量导入或触发工单对账归集" /></div>
        <div class="dm-table-footer">
          <span>共 {{ total }} 条</span>
          <UiPagination v-model:page="page" v-model:page-size="size" :total="total" @update:page="loadList" @update:page-size="page = 1; loadList()" />
        </div>
      </template>
    </el-card>

    <UiFormDrawer v-model="createOpen" title="新增问题" :loading="saving" width="560px" @submit="submitCreate">
      <el-form label-width="100px">
        <el-form-item label="问题标题" required><el-input v-model="form.issueTitle" maxlength="200" /></el-form-item>
        <el-form-item label="问题描述" required><el-input v-model="form.issueDesc" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="发生场景"><el-input v-model="form.scene" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="影响范围"><el-input v-model="form.impactScope" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="原始卡点"><el-input v-model="form.blockDesc" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <el-dialog v-model="importOpen" title="问题批量导入" width="640px" destroy-on-close>
      <el-button link type="primary" :icon="Download" @click="downloadTemplate">下载导入模板（CSV）</el-button>
      <p class="muted">按模板列顺序填写：标题,描述,编码(可空),状态(可空),场景,影响范围。异常行会单独罗列，合法行一键入库。</p>
      <el-input v-model="importRows" type="textarea" :rows="8" placeholder="问题A,单据丢失,IS-20260924-999,,迁移高峰期,全量对账异常" />
      <div v-if="importResult" class="dm-import-result">
        <el-alert type="success" :closable="false" :title="`合法入库 ${importResult.inserted} 条`" />
        <el-alert v-if="(importResult.anomalies ?? []).length > 0" type="error" :closable="false"
                  :title="`异常 ${importResult.anomalies.length} 条（不入库）`" class="dm-import-anomaly" />
        <el-button v-if="(importResult.anomalies ?? []).length > 0" link type="danger" :icon="Download" @click="downloadAnomalies">下载异常明细</el-button>
      </div>
      <template #footer>
        <el-button @click="importOpen = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="submitImport">开始导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="operateOpen" :title="operateMode === 'RECTIFY' ? '整改推进' : '核验闭环'" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="问题"><span class="muted">{{ currentRow?.issueTitle }}</span></el-form-item>
        <template v-if="operateMode === 'RECTIFY'">
          <el-form-item label="整改进度" required><el-input v-model="operateForm.rectifyProgress" type="textarea" :rows="3" placeholder="整改执行进度" /></el-form-item>
          <el-form-item label="整改记录"><el-input v-model="operateForm.record" type="textarea" :rows="2" placeholder="追加式留痕，不可清空" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="解决方案" required><el-input v-model="operateForm.solution" type="textarea" :rows="3" placeholder="闭环后自动沉淀为知识条目三元组" /></el-form-item>
          <p class="muted">闭环后原问题禁止修改/删除，整改记录永久留存。</p>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="operateOpen = false">取消</el-button>
        <el-button :type="operateMode === 'RECTIFY' ? 'warning' : 'success'" :loading="saving" @click="submitOperate">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>
