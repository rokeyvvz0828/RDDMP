<!--
文件：web/src/modules/test-management/business-day/BatchRequirementList.vue
说明：营业日跑批需求的登记、查询和评审视图。
用途：完成跑批需求分页 CRUD、RDDMP 用户提出人选择、采纳评审和 XLSX 导出。
作者：hengguan
-->
<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Check, Delete, Download, Edit, Plus, Search, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import { useAuthStore } from '../../../stores/auth'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import UiPagination from '../../../components/ui/UiPagination.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import type { BatchRequirement, Environment, UserDirectoryItem } from '../api'
import { createRequirement, deleteRequirement, downloadBusinessDayFile, listBusinessDayUsers, listEnvironmentOptions, listRequirements, reviewRequirement, updateRequirement } from '../api'

const auth = useAuthStore()
const rows = ref<BatchRequirement[]>([])
const environments = ref<Environment[]>([])
const users = ref<UserDirectoryItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const envCode = ref('')
const naturalDate = ref('')
const adoption = ref('')
const loading = ref(false)
const usersLoading = ref(false)
const error = ref('')
const drawerOpen = ref(false)
const saving = ref(false)
const editing = ref<BatchRequirement | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ env_code: '', natural_date: '', business_date: '', has_batch: true, batch_type: '增量', batch_time: '22:00', systems: [] as string[], validation_content: '', proposer_id: null as number | null })
const batchTypes = ['全量', '增量', '初始化', '翻数']
const requiresBatchDetails = () => form.batch_type !== '翻数'
const rules: FormRules = {
  env_code: [{ required: true, message: '请选择测试环境', trigger: 'change' }],
  natural_date: [{ required: true, message: '请输入需求日期', trigger: 'blur' }, { pattern: /^\d{4}-\d{2}(-\d{2})?$/, message: '格式应为 YYYY-MM 或 YYYY-MM-DD', trigger: 'blur' }],
  business_date: [{ required: true, message: '请选择营业日', trigger: 'change' }],
  proposer_id: [{ required: true, message: '请选择提出人', trigger: 'change' }],
  batch_type: [{ required: true, message: '请选择跑批类型', trigger: 'change' }],
  batch_time: [{ validator: (_rule, value, callback) => requiresBatchDetails() && !value ? callback(new Error('请选择跑批时间')) : callback(), trigger: 'change' }],
  systems: [{ validator: (_rule, value, callback) => requiresBatchDetails() && (!Array.isArray(value) || !value.length) ? callback(new Error('请至少填写一个涉及系统')) : callback(), trigger: 'change' }],
  validation_content: [{ validator: (_rule, value, callback) => requiresBatchDetails() && !String(value || '').trim() ? callback(new Error('请填写验证内容')) : callback(), trigger: 'blur' }]
}
const adoptionLabels = { PENDING: '待定', ACCEPTED: '采纳', REJECTED: '不采纳' }

// 关键逻辑：需求列表由后端同时执行租户隔离并通过用户目录补充提出人展示信息。
async function load() {
  loading.value = true; error.value = ''
  try {
    const result = (await listRequirements({ page: page.value, size: pageSize.value, keyword: keyword.value || undefined, envCode: envCode.value || undefined, naturalDate: naturalDate.value || undefined, adoption: adoption.value || undefined })).data.data
    rows.value = result.records; total.value = result.total
  } catch (reason) { error.value = apiErrorMessage(reason, '跑批需求加载失败') }
  finally { loading.value = false }
}
async function loadOptions() {
  try { environments.value = (await listEnvironmentOptions()).data.data } catch (reason) { error.value = apiErrorMessage(reason, '测试环境加载失败') }
  await searchUsers('')
}

// 关键逻辑：提出人远程检索只读取 RDDMP 当前租户有效用户，不接受任意文本或失效用户编号。
async function searchUsers(query: string) {
  usersLoading.value = true
  try { users.value = (await listBusinessDayUsers(query || undefined)).data.data }
  catch (reason) { ElMessage.error(apiErrorMessage(reason, '用户目录加载失败')) }
  finally { usersLoading.value = false }
}
function resetForm() {
  Object.assign(form, { env_code: environments.value[0]?.env_code || '', natural_date: '', business_date: '', has_batch: true, batch_type: '增量', batch_time: '22:00', systems: [], validation_content: '', proposer_id: auth.user?.id || null })
  formRef.value?.clearValidate()
}
function openCreate() { editing.value = null; resetForm(); drawerOpen.value = true }
function openEdit(row: BatchRequirement) {
  editing.value = row
  if (!users.value.some(item => item.id === row.proposer_id)) void searchUsers(row.proposer_name || '')
  Object.assign(form, { env_code: row.env_code, natural_date: row.natural_date, business_date: row.business_date, has_batch: true, batch_type: row.batch_type || '增量', batch_time: row.batch_time?.slice(0, 5) || '', systems: [...(row.systems || [])], validation_content: row.validation_content || '', proposer_id: row.proposer_id })
  drawerOpen.value = true
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    // 关键逻辑：跑批需求固定提交 has_batch=true，不能通过前端载荷绕过后端跑批字段矩阵。
    const payload = { ...form, has_batch: true, proposer_id: form.proposer_id || undefined }
    if (editing.value) await updateRequirement(editing.value.id, payload); else await createRequirement(payload)
    ElMessage.success(editing.value ? '跑批需求已更新' : '跑批需求已登记')
    drawerOpen.value = false; await load()
  } catch (reason) { ElMessage.error(apiErrorMessage(reason, '跑批需求保存失败')) }
  finally { saving.value = false }
}

// 关键逻辑：采纳/不采纳只记录评审结论，不自动生成或修改日历安排。
async function review(row: BatchRequirement, value: 'ACCEPTED' | 'REJECTED') {
  const label = value === 'ACCEPTED' ? '采纳' : '不采纳'
  const result = await ElMessageBox.prompt(`确认将该跑批需求标记为“${label}”？评审不会自动同步日历安排。`, `评审：${label}`, { inputPlaceholder: '评审意见（选填）', inputType: 'textarea', confirmButtonText: label, cancelButtonText: '取消', type: value === 'ACCEPTED' ? 'success' : 'warning' }).then(value => value).catch(() => null)
  if (!result) return
  try { await reviewRequirement(row.id, value, result.value || ''); ElMessage.success(`需求已${label}`); await load() }
  catch (reason) { ElMessage.error(apiErrorMessage(reason, '跑批需求评审失败')) }
}
async function remove(row: BatchRequirement) {
  const confirmed = await ElMessageBox.confirm(`确认删除 ${row.natural_date} 的 ${row.env_name || row.env_code} 跑批需求？`, '删除跑批需求', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }).then(() => true).catch(() => false)
  if (!confirmed) return
  try { await deleteRequirement(row.id); ElMessage.success('跑批需求已删除'); await load() }
  catch (reason) { ElMessage.error(apiErrorMessage(reason, '跑批需求删除失败')) }
}
async function exportRows() {
  try { await downloadBusinessDayFile('/requirements/export', { envCode: envCode.value || undefined, naturalDate: naturalDate.value || undefined, adoption: adoption.value || undefined }, '营业日跑批需求.xlsx') }
  catch (reason) { ElMessage.error(apiErrorMessage(reason, '跑批需求导出失败')) }
}
function search() { page.value = 1; void load() }
function resetFilters() { keyword.value = ''; envCode.value = ''; naturalDate.value = ''; adoption.value = ''; page.value = 1; void load() }
function truthy(value: unknown) { return value === true || value === 1 || value === '1' }
function adoptionTone(value: string): 'success' | 'danger' | 'warning' { return value === 'ACCEPTED' ? 'success' : value === 'REJECTED' ? 'danger' : 'warning' }
watch(pageSize, () => { page.value = 1; void load() })
// 关键逻辑：翻数只放宽三个从属字段的必填状态，类型切换不丢弃用户已经填写的内容。
watch(() => form.batch_type, () => formRef.value?.clearValidate(['batch_time', 'systems', 'validation_content']))
onMounted(async () => { await loadOptions(); await load() })
</script>

<template>
  <section class="business-day-view">
    <UiToolbar>
      <div class="business-day-filters business-day-filters--wide">
        <el-input v-model="keyword" clearable placeholder="搜索营业日或验证内容" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-select v-model="envCode" clearable placeholder="全部环境"><el-option v-for="item in environments" :key="item.id" :label="item.env_name" :value="item.env_code" /></el-select>
        <el-input v-model="naturalDate" clearable placeholder="需求月份/日期" />
        <el-select v-model="adoption" clearable placeholder="全部评审状态"><el-option v-for="(label, value) in adoptionLabels" :key="value" :label="label" :value="value" /></el-select>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="resetFilters">重置</el-button>
      </div>
      <template #actions><el-button @click="exportRows"><el-icon><Download /></el-icon>导出</el-button><el-button type="primary" :disabled="!environments.length" @click="openCreate"><el-icon><Plus /></el-icon>登记需求</el-button></template>
    </UiToolbar>
    <el-alert v-if="!environments.length && !error" title="请先在“测试环境管理”创建并启用测试环境。" type="warning" :closable="false" show-icon />
    <UiEmptyState v-if="error" title="跑批需求加载失败" :description="error"><template #action><el-button type="primary" @click="load">重新加载</el-button></template></UiEmptyState>
    <template v-else>
      <UiDataTable class="business-day-table" :data="rows" row-key="id" border table-layout="auto" v-loading="loading">
        <el-table-column label="测试环境"><template #default="scope"><strong>{{ scope.row.env_name || scope.row.env_code }}</strong></template></el-table-column>
        <el-table-column prop="natural_date" label="自然日期" />
        <el-table-column prop="business_date" label="营业日期" />
        <el-table-column label="跑批"><template #default="scope"><UiStatusTag :value="truthy(scope.row.has_batch) ? 'BATCH' : 'NO_BATCH'" :labels="{ BATCH: scope.row.batch_type || '跑批', NO_BATCH: '无跑批' }" :tone="truthy(scope.row.has_batch) ? 'warning' : 'info'" /><span v-if="truthy(scope.row.has_batch)" class="business-day-inline-meta">{{ scope.row.batch_time?.slice(0,5) }}</span></template></el-table-column>
        <el-table-column label="提出人"><template #default="scope"><div class="business-day-user"><strong>{{ scope.row.proposer_name || `用户#${scope.row.proposer_id}` }}</strong><small>{{ scope.row.proposer_org_name || '未分配组织' }}<template v-if="scope.row.proposer_mobile_phone"> · {{ scope.row.proposer_mobile_phone }}</template></small></div></template></el-table-column>
        <el-table-column label="涉及系统"><template #default="scope"><div class="business-day-tags"><el-tag v-for="item in scope.row.systems" :key="item" size="small" effect="plain">{{ item }}</el-tag><span v-if="!scope.row.systems?.length">—</span></div></template></el-table-column>
        <el-table-column label="评审"><template #default="scope"><UiStatusTag :value="scope.row.adoption" :labels="adoptionLabels" :tone="adoptionTone(scope.row.adoption)" /><small v-if="scope.row.reviewer_name" class="business-day-block-meta">{{ scope.row.reviewer_name }}</small></template></el-table-column>
        <el-table-column label="操作"><template #default="scope"><div class="business-day-actions"><el-button link type="success" @click="review(scope.row, 'ACCEPTED')"><el-icon><Check /></el-icon>采纳</el-button><el-button link type="warning" @click="review(scope.row, 'REJECTED')"><el-icon><Close /></el-icon>不采纳</el-button><el-button link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="danger" @click="remove(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></div></template></el-table-column>
      </UiDataTable>
      <div class="business-day-mobile-list" :class="{ 'is-loading': loading }"><article v-for="row in rows" :key="row.id"><header><div class="business-day-primary-cell"><i :class="`theme-${row.theme || 'brand'}`" /><div><strong>{{ row.env_name || row.env_code }}</strong><small>{{ row.natural_date }} · 营业日 {{ row.business_date }}</small></div></div><UiStatusTag :value="row.adoption" :labels="adoptionLabels" :tone="adoptionTone(row.adoption)" /></header><p>{{ row.validation_content || '暂无验证内容' }}</p><dl><div><dt>提出人</dt><dd>{{ row.proposer_name || `用户#${row.proposer_id}` }}</dd></div><div><dt>跑批类型</dt><dd>{{ truthy(row.has_batch) ? row.batch_type : '无跑批' }}</dd></div><div><dt>跑批时间</dt><dd>{{ row.batch_time?.slice(0,5) || '—' }}</dd></div></dl><div class="business-day-tags"><el-tag v-for="item in row.systems" :key="item" size="small" effect="plain">{{ item }}</el-tag></div><footer><el-button link type="success" @click="review(row, 'ACCEPTED')">采纳</el-button><el-button link type="warning" @click="review(row, 'REJECTED')">不采纳</el-button><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></footer></article></div>
      <UiEmptyState v-if="!loading && !rows.length" title="暂无跑批需求" description="登记需求后可由有评审权限的人员采纳或不采纳。"><template #action><el-button type="primary" :disabled="!environments.length" @click="openCreate">登记需求</el-button></template></UiEmptyState>
      <UiPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </template>

    <UiFormDrawer v-model="drawerOpen" :title="editing ? '编辑跑批需求' : '登记跑批需求'" width="min(680px, 96vw)" :loading="saving" @submit="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="business-day-form-grid"><el-form-item label="测试环境" prop="env_code"><el-select v-model="form.env_code" filterable><el-option v-for="item in environments" :key="item.id" :label="`${item.env_name}（${item.env_code}）`" :value="item.env_code" /></el-select></el-form-item><el-form-item label="提出人" prop="proposer_id"><el-select v-model="form.proposer_id" filterable remote :remote-method="searchUsers" :loading="usersLoading" placeholder="搜索姓名、账号或手机号"><el-option v-for="item in users" :key="item.id" :label="`${item.displayName}（${item.username}）`" :value="item.id"><div class="business-day-user-option"><strong>{{ item.displayName }}</strong><span>{{ item.orgName || '未分配组织' }} · {{ item.mobilePhone || item.username }}</span></div></el-option></el-select></el-form-item></div>
        <div class="business-day-form-grid"><el-form-item label="需求月份/日期" prop="natural_date"><el-input v-model="form.natural_date" placeholder="YYYY-MM 或 YYYY-MM-DD" /></el-form-item><el-form-item label="营业日" prop="business_date"><el-date-picker v-model="form.business_date" type="date" value-format="YYYYMMDD" /></el-form-item></div>
        <div class="business-day-form-grid"><el-form-item label="跑批类型" prop="batch_type"><el-select v-model="form.batch_type"><el-option v-for="item in batchTypes" :key="item" :label="item" :value="item" /></el-select></el-form-item><el-form-item label="跑批时间" prop="batch_time" :required="requiresBatchDetails()"><el-time-picker v-model="form.batch_time" value-format="HH:mm" format="HH:mm" /></el-form-item></div>
        <el-form-item label="涉及系统（手工输入）" prop="systems" :required="requiresBatchDetails()"><el-select v-model="form.systems" multiple filterable allow-create default-first-option :reserve-keyword="false" placeholder="输入系统名称后按回车，可添加多个" /></el-form-item>
        <el-form-item label="验证内容" prop="validation_content" :required="requiresBatchDetails()"><el-input v-model="form.validation_content" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
    </UiFormDrawer>
  </section>
</template>
