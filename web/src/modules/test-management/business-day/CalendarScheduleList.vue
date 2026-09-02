<!--
文件：web/src/modules/test-management/business-day/CalendarScheduleList.vue
说明：营业日日历安排的查询、维护和批量文件视图。
用途：完成日历安排分页 CRUD、自然键覆盖、XLSX 模板下载、导入和导出。
作者：hengguan
-->
<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Delete, Download, Edit, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import TestManagementFormDialog from '../components/TestManagementFormDialog.vue'
import UiPagination from '../../../components/ui/UiPagination.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import type { Environment, Schedule } from '../api'
import { createSchedule, deleteSchedule, downloadBusinessDayFile, importSchedules, listEnvironmentOptions, listSchedules, updateSchedule } from '../api'

const rows = ref<Schedule[]>([])
const environments = ref<Environment[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const envCode = ref('')
const dateRange = ref<string[]>([])
const hasBatch = ref<boolean | undefined>()
const batchType = ref('')
const loading = ref(false)
const error = ref('')
const drawerOpen = ref(false)
const saving = ref(false)
const importing = ref(false)
const editing = ref<Schedule | null>(null)
const formRef = ref<FormInstance>()
const fileInput = ref<HTMLInputElement>()
const form = reactive({ env_code: '', natural_date: '', business_date: '', has_batch: true, batch_type: '增量', batch_time: '22:00', systems: [] as string[], validation_content: '', maintainer: '' })
const requiresBatchDetails = () => form.has_batch && form.batch_type !== '翻数'
const rules: FormRules = {
  env_code: [{ required: true, message: '请选择测试环境', trigger: 'change' }],
  natural_date: [{ required: true, message: '请选择自然日', trigger: 'change' }],
  business_date: [{ required: true, message: '请选择营业日', trigger: 'change' }],
  batch_type: [{ validator: (_rule, value, callback) => form.has_batch && !value ? callback(new Error('请选择跑批类型')) : callback(), trigger: 'change' }],
  batch_time: [{ validator: (_rule, value, callback) => requiresBatchDetails() && !value ? callback(new Error('请选择跑批时间')) : callback(), trigger: 'change' }],
  systems: [{ validator: (_rule, value, callback) => requiresBatchDetails() && (!Array.isArray(value) || !value.length) ? callback(new Error('请至少填写一个涉及系统')) : callback(), trigger: 'change' }],
  validation_content: [{ validator: (_rule, value, callback) => requiresBatchDetails() && !String(value || '').trim() ? callback(new Error('请填写验证内容')) : callback(), trigger: 'blur' }]
}
const batchTypes = ['全量', '增量', '初始化', '翻数']

// 关键逻辑：分页、日期范围与跑批条件由后端组合查询，前端不对已分页结果做二次筛选。
async function load() {
  loading.value = true; error.value = ''
  try {
    const result = (await listSchedules({ page: page.value, size: pageSize.value, keyword: keyword.value || undefined, envCode: envCode.value || undefined, dateFrom: dateRange.value?.[0], dateTo: dateRange.value?.[1], hasBatch: hasBatch.value, batchType: batchType.value || undefined })).data.data
    rows.value = result.records; total.value = result.total
  } catch (reason) { error.value = apiErrorMessage(reason, '日历安排加载失败') }
  finally { loading.value = false }
}
async function loadEnvironments() {
  try { environments.value = (await listEnvironmentOptions()).data.data }
  catch (reason) { error.value = apiErrorMessage(reason, '测试环境加载失败') }
}
function resetForm() { Object.assign(form, { env_code: environments.value[0]?.env_code || '', natural_date: '', business_date: '', has_batch: true, batch_type: '增量', batch_time: '22:00', systems: [], validation_content: '', maintainer: '' }); formRef.value?.clearValidate() }
function openCreate() { editing.value = null; resetForm(); drawerOpen.value = true }
function openEdit(row: Schedule) {
  editing.value = row
  Object.assign(form, { env_code: row.env_code, natural_date: normalizeDate(row.natural_date), business_date: row.business_date, has_batch: truthy(row.has_batch), batch_type: row.batch_type || '增量', batch_time: row.batch_time?.slice(0, 5) || '', systems: [...(row.systems || [])], validation_content: row.validation_content || '', maintainer: row.maintainer || '' })
  drawerOpen.value = true
}

// 关键逻辑：新增时允许后端按“环境 + 自然日”覆盖旧安排，覆盖结果必须明确告知用户。
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    // 关键逻辑：关闭跑批时主动清空全部从属字段，服务端仍会再次规范化以防直接 API 绕过。
    const payload = { ...form, batch_type: form.has_batch ? form.batch_type : undefined, batch_time: form.has_batch ? form.batch_time : undefined, systems: form.has_batch ? [...form.systems] : [], validation_content: form.has_batch ? form.validation_content : undefined }
    const result = editing.value ? (await updateSchedule(editing.value.id, payload)).data.data : (await createSchedule(payload)).data.data
    ElMessage.success(result.overwritten ? '同环境同自然日的原安排已覆盖' : editing.value ? '日历安排已更新' : '日历安排已创建')
    drawerOpen.value = false; await load()
  } catch (reason) { ElMessage.error(apiErrorMessage(reason, '日历安排保存失败')) }
  finally { saving.value = false }
}
async function remove(row: Schedule) {
  const confirmed = await ElMessageBox.confirm(`确认删除 ${normalizeDate(row.natural_date)} 的 ${row.env_name || row.env_code} 安排？`, '删除日历安排', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }).then(() => true).catch(() => false)
  if (!confirmed) return
  try { await deleteSchedule(row.id); ElMessage.success('日历安排已删除'); await load() }
  catch (reason) { ElMessage.error(apiErrorMessage(reason, '日历安排删除失败')) }
}

// 关键逻辑：导入只接受单个 XLSX，后端继续执行 5MB、2000 行、字段和环境有效性校验，并在一个事务内写入。
async function selectImport(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = ''
  if (!file) return
  importing.value = true
  try { const result = (await importSchedules(file)).data.data; ElMessage.success(`导入 ${result.total} 条：新增 ${result.created} 条，覆盖 ${result.overwritten} 条`); await load() }
  catch (reason) { ElMessage.error(apiErrorMessage(reason, '日历安排导入失败')) }
  finally { importing.value = false }
}
async function download(path: string, fallback: string) {
  try { await downloadBusinessDayFile(path, { envCode: envCode.value || undefined, dateFrom: dateRange.value?.[0], dateTo: dateRange.value?.[1] }, fallback) }
  catch (reason) { ElMessage.error(apiErrorMessage(reason, '文件下载失败')) }
}
function search() { page.value = 1; void load() }
function resetFilters() { keyword.value = ''; envCode.value = ''; dateRange.value = []; hasBatch.value = undefined; batchType.value = ''; page.value = 1; void load() }
function normalizeDate(value: unknown) { return String(value || '').slice(0, 10) }
function truthy(value: unknown) { return value === true || value === 1 || value === '1' }
watch(pageSize, () => { page.value = 1; void load() })
// 关键逻辑：类型切换只清除旧校验提示，不清空用户已填内容；翻数改回其他类型后会按新矩阵重新校验。
watch([() => form.has_batch, () => form.batch_type], () => formRef.value?.clearValidate(['batch_time', 'systems', 'validation_content']))
onMounted(async () => { await loadEnvironments(); await load() })
</script>

<template>
  <section class="business-day-view">
    <UiToolbar>
      <div class="business-day-filters business-day-filters--wide">
        <el-input v-model="keyword" clearable placeholder="搜索营业日、验证内容或维护人" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-select v-model="envCode" clearable placeholder="全部环境"><el-option v-for="item in environments" :key="item.id" :label="item.env_name" :value="item.env_code" /></el-select>
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
        <el-select v-model="hasBatch" clearable placeholder="跑批状态"><el-option label="有跑批" :value="true" /><el-option label="无跑批" :value="false" /></el-select>
        <el-select v-model="batchType" clearable placeholder="跑批类型"><el-option v-for="item in batchTypes" :key="item" :label="item" :value="item" /></el-select>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="resetFilters">重置</el-button>
      </div>
      <template #actions>
        <input ref="fileInput" class="business-day-file-input" type="file" accept=".xlsx" @change="selectImport" />
        <el-dropdown trigger="click"><el-button><el-icon><Download /></el-icon>文件<el-icon class="el-icon--right"><Refresh /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="download('/schedules/template', '营业日日历导入模板.xlsx')">下载导入模板</el-dropdown-item><el-dropdown-item @click="download('/schedules/export', '营业日日历安排.xlsx')">导出当前条件</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        <el-button :loading="importing" @click="fileInput?.click()"><el-icon><Upload /></el-icon>导入 XLSX</el-button>
        <el-button type="primary" :disabled="!environments.length" @click="openCreate"><el-icon><Plus /></el-icon>新增安排</el-button>
      </template>
    </UiToolbar>
    <el-alert v-if="!environments.length && !error" title="请先在“测试环境管理”创建并启用测试环境。" type="warning" :closable="false" show-icon />
    <UiEmptyState v-if="error" title="日历安排加载失败" :description="error"><template #action><el-button type="primary" @click="load">重新加载</el-button></template></UiEmptyState>
    <template v-else>
      <UiDataTable class="business-day-table" :data="rows" row-key="id" border table-layout="auto" v-loading="loading">
        <el-table-column label="测试环境"><template #default="scope"><strong>{{ scope.row.env_name || scope.row.env_code }}</strong></template></el-table-column>
        <el-table-column label="自然日期"><template #default="scope">{{ normalizeDate(scope.row.natural_date) }}</template></el-table-column>
        <el-table-column prop="business_date" label="营业日期" />
        <el-table-column label="跑批"><template #default="scope"><UiStatusTag :value="truthy(scope.row.has_batch) ? 'BATCH' : 'NO_BATCH'" :labels="{ BATCH: scope.row.batch_type || '跑批', NO_BATCH: '无跑批' }" :tone="truthy(scope.row.has_batch) ? 'warning' : 'info'" /><span v-if="truthy(scope.row.has_batch)" class="business-day-inline-meta">{{ scope.row.batch_time?.slice(0,5) }}</span></template></el-table-column>
        <el-table-column label="涉及系统"><template #default="scope"><div class="business-day-tags"><el-tag v-for="item in scope.row.systems" :key="item" size="small" effect="plain">{{ item }}</el-tag><span v-if="!scope.row.systems?.length">—</span></div></template></el-table-column>
        <el-table-column prop="validation_content" label="验证内容"><template #default="scope">{{ scope.row.validation_content || '—' }}</template></el-table-column>
        <el-table-column prop="maintainer" label="维护人" />
        <el-table-column label="操作"><template #default="scope"><div class="business-day-actions"><el-button link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="danger" @click="remove(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></div></template></el-table-column>
      </UiDataTable>
      <div class="business-day-mobile-list" :class="{ 'is-loading': loading }"><article v-for="row in rows" :key="row.id"><header><div class="business-day-primary-cell"><i :class="`theme-${row.theme || 'brand'}`" /><div><strong>{{ row.env_name || row.env_code }}</strong><small>{{ normalizeDate(row.natural_date) }} · 营业日 {{ row.business_date }}</small></div></div><UiStatusTag :value="truthy(row.has_batch) ? 'BATCH' : 'NO_BATCH'" :labels="{ BATCH: row.batch_type || '跑批', NO_BATCH: '无跑批' }" :tone="truthy(row.has_batch) ? 'warning' : 'info'" /></header><div class="business-day-tags"><el-tag v-for="item in row.systems" :key="item" size="small" effect="plain">{{ item }}</el-tag></div><p>{{ row.validation_content || '暂无验证内容' }}</p><dl><div><dt>跑批时间</dt><dd>{{ row.batch_time?.slice(0,5) || '—' }}</dd></div><div><dt>维护人</dt><dd>{{ row.maintainer || '—' }}</dd></div></dl><footer><el-button link type="primary" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="danger" @click="remove(row)"><el-icon><Delete /></el-icon>删除</el-button></footer></article></div>
      <UiEmptyState v-if="!loading && !rows.length" title="暂无日历安排" description="新增一条安排，或下载模板后批量导入。"><template #action><el-button type="primary" :disabled="!environments.length" @click="openCreate">新增安排</el-button></template></UiEmptyState>
      <UiPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </template>

    <TestManagementFormDialog v-model="drawerOpen" :title="editing ? '编辑日历安排' : '新增日历安排'" width="min(680px, 96vw)" :loading="saving" @submit="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="business-day-form-grid"><el-form-item label="测试环境" prop="env_code"><el-select v-model="form.env_code" filterable><el-option v-for="item in environments" :key="item.id" :label="`${item.env_name}（${item.env_code}）`" :value="item.env_code" /></el-select></el-form-item><el-form-item label="自然日" prop="natural_date"><el-date-picker v-model="form.natural_date" type="date" value-format="YYYY-MM-DD" /></el-form-item></div>
        <div class="business-day-form-grid"><el-form-item label="营业日" prop="business_date"><el-date-picker v-model="form.business_date" type="date" value-format="YYYYMMDD" /></el-form-item><el-form-item label="是否跑批"><el-switch v-model="form.has_batch" active-text="是" inactive-text="否" /></el-form-item></div>
        <div v-if="form.has_batch" class="business-day-form-grid"><el-form-item label="跑批类型" prop="batch_type" required><el-select v-model="form.batch_type"><el-option v-for="item in batchTypes" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="跑批时间" prop="batch_time" :required="requiresBatchDetails()"><el-time-picker v-model="form.batch_time" value-format="HH:mm" format="HH:mm" /></el-form-item></div>
        <el-form-item v-if="form.has_batch" label="涉及系统（手工输入）" prop="systems" :required="requiresBatchDetails()"><el-select v-model="form.systems" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="输入系统名称后按回车，可添加多个" /></el-form-item>
        <el-form-item v-if="form.has_batch" label="验证内容" prop="validation_content" :required="requiresBatchDetails()"><el-input v-model="form.validation_content" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="维护人"><el-input v-model="form.maintainer" maxlength="128" placeholder="留空时使用当前登录人" /></el-form-item>
      </el-form>
    </TestManagementFormDialog>
  </section>
</template>
