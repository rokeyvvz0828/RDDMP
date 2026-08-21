<!--
文件：web/src/modules/test-management/business-day/TestEnvironmentList.vue
说明：营业日测试环境的查询与维护视图。
用途：完成测试环境分页、筛选、新增、编辑、启停和受引用保护删除交互。
作者：hengguan
-->
<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { apiErrorMessage } from '../../../api/error'
import UiDataTable from '../../../components/ui/UiDataTable.vue'
import UiEmptyState from '../../../components/ui/UiEmptyState.vue'
import UiFormDrawer from '../../../components/ui/UiFormDrawer.vue'
import UiPagination from '../../../components/ui/UiPagination.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import UiToolbar from '../../../components/ui/UiToolbar.vue'
import type { Environment, Theme } from '../api'
import { createEnvironment, deleteEnvironment, listEnvironments, updateEnvironment } from '../api'

const rows = ref<Environment[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const enabled = ref<boolean | undefined>()
const loading = ref(false)
const error = ref('')
const drawerOpen = ref(false)
const saving = ref(false)
const editing = ref<Environment | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ env_code: '', env_name: '', purpose: '', theme: 'brand' as Theme, sort_no: 0, enabled: true, remark: '' })
const rules: FormRules = {
  env_code: [{ required: true, message: '请输入环境编码', trigger: 'blur' }, { pattern: /^[A-Za-z0-9_-]{1,64}$/, message: '仅支持字母、数字、下划线和短横线', trigger: 'blur' }],
  env_name: [{ required: true, message: '请输入环境名称', trigger: 'blur' }]
}
const themeOptions: Array<{ value: Theme; label: string }> = [
  { value: 'brand', label: '品牌蓝' }, { value: 'success', label: '成功绿' }, { value: 'warning', label: '提醒橙' }, { value: 'danger', label: '风险红' }, { value: 'accent', label: '强调紫' }
]

// 关键逻辑：列表条件全部交给后端分页，避免本地筛选造成总数和引用计数失真。
async function load() {
  loading.value = true; error.value = ''
  try {
    const result = (await listEnvironments({ page: page.value, size: pageSize.value, keyword: keyword.value || undefined, enabled: enabled.value })).data.data
    rows.value = result.records; total.value = result.total
  } catch (reason) { error.value = apiErrorMessage(reason, '测试环境加载失败') }
  finally { loading.value = false }
}

function resetForm() {
  Object.assign(form, { env_code: '', env_name: '', purpose: '', theme: 'brand', sort_no: 0, enabled: true, remark: '' })
  formRef.value?.clearValidate()
}
function openCreate() { editing.value = null; resetForm(); drawerOpen.value = true }
function openEdit(row: Environment) {
  editing.value = row
  Object.assign(form, { env_code: row.env_code, env_name: row.env_name, purpose: row.purpose || '', theme: row.theme || 'brand', sort_no: row.sort_no, enabled: truthy(row.enabled), remark: row.remark || '' })
  drawerOpen.value = true
}

// 关键逻辑：环境编码修改由后端事务同步关联安排与需求，前端仅提交规范化编码并等待服务器确认。
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    const payload = { ...form, env_code: form.env_code.trim().toUpperCase(), env_name: form.env_name.trim() }
    if (editing.value) await updateEnvironment(editing.value.id, payload); else await createEnvironment(payload)
    ElMessage.success(editing.value ? '测试环境已更新' : '测试环境已创建')
    drawerOpen.value = false; await load()
  } catch (reason) { ElMessage.error(apiErrorMessage(reason, '测试环境保存失败')) }
  finally { saving.value = false }
}

async function remove(row: Environment) {
  const referenced = Number(row.schedule_count || 0) + Number(row.requirement_count || 0)
  const message = referenced ? `该环境当前有 ${row.schedule_count || 0} 条日历安排和 ${row.requirement_count || 0} 条跑批需求，服务器将拒绝删除。仍要检查吗？` : `确认删除测试环境“${row.env_name}（${row.env_code}）”？`
  const confirmed = await ElMessageBox.confirm(message, '删除测试环境', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }).then(() => true).catch(() => false)
  if (!confirmed) return
  try { await deleteEnvironment(row.id); ElMessage.success('测试环境已删除'); await load() }
  catch (reason) { ElMessage.error(apiErrorMessage(reason, '测试环境删除失败')) }
}
function search() { page.value = 1; void load() }
function resetFilters() { keyword.value = ''; enabled.value = undefined; page.value = 1; void load() }
function truthy(value: unknown) { return value === true || value === 1 || value === '1' }
watch(pageSize, () => { page.value = 1; void load() })
onMounted(load)
</script>

<template>
  <section class="business-day-view">
    <UiToolbar>
      <div class="business-day-filters">
        <el-input v-model="keyword" clearable placeholder="搜索环境编码、名称或用途" @keyup.enter="search"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-select v-model="enabled" clearable placeholder="全部状态"><el-option label="启用" :value="true" /><el-option label="停用" :value="false" /></el-select>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="resetFilters">重置</el-button>
      </div>
      <template #actions><el-tooltip content="刷新"><el-button circle :loading="loading" aria-label="刷新测试环境" @click="load"><el-icon><Refresh /></el-icon></el-button></el-tooltip><el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增环境</el-button></template>
    </UiToolbar>

    <UiEmptyState v-if="error" title="测试环境加载失败" :description="error"><template #action><el-button type="primary" @click="load">重新加载</el-button></template></UiEmptyState>
    <template v-else>
      <UiDataTable class="business-day-table" :data="rows" row-key="id" border table-layout="auto" v-loading="loading">
        <el-table-column label="测试环境"><template #default="scope"><strong>{{ scope.row.env_name }}</strong></template></el-table-column>
        <el-table-column prop="purpose" label="用途"><template #default="scope">{{ scope.row.purpose || '—' }}</template></el-table-column>
        <el-table-column label="状态"><template #default="scope"><UiStatusTag :value="truthy(scope.row.enabled) ? 'ENABLED' : 'DISABLED'" :labels="{ ENABLED: '启用', DISABLED: '停用' }" :tone="truthy(scope.row.enabled) ? 'success' : 'info'" /></template></el-table-column>
        <el-table-column prop="sort_no" label="排序" />
        <el-table-column label="引用"><template #default="scope"><span class="muted">日历 {{ scope.row.schedule_count || 0 }} · 需求 {{ scope.row.requirement_count || 0 }}</span></template></el-table-column>
        <el-table-column prop="updated_at" label="更新时间" />
        <el-table-column label="操作"><template #default="scope"><div class="business-day-actions"><el-button link type="primary" @click="openEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="danger" @click="remove(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></div></template></el-table-column>
      </UiDataTable>
      <div class="business-day-mobile-list" :class="{ 'is-loading': loading }">
        <article v-for="row in rows" :key="row.id"><header><div class="business-day-primary-cell"><i :class="`theme-${row.theme}`" /><div><strong>{{ row.env_name }}</strong><small>{{ row.env_code }}</small></div></div><UiStatusTag :value="truthy(row.enabled) ? 'ENABLED' : 'DISABLED'" :labels="{ ENABLED: '启用', DISABLED: '停用' }" :tone="truthy(row.enabled) ? 'success' : 'info'" /></header><p>{{ row.purpose || '暂无用途说明' }}</p><dl><div><dt>排序</dt><dd>{{ row.sort_no }}</dd></div><div><dt>日历引用</dt><dd>{{ row.schedule_count || 0 }}</dd></div><div><dt>需求引用</dt><dd>{{ row.requirement_count || 0 }}</dd></div></dl><footer><el-button link type="primary" @click="openEdit(row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="danger" @click="remove(row)"><el-icon><Delete /></el-icon>删除</el-button></footer></article>
      </div>
      <UiEmptyState v-if="!loading && !rows.length" title="暂无测试环境" description="先创建一个启用的测试环境，再维护日历安排和跑批需求。"><template #action><el-button type="primary" @click="openCreate">新增环境</el-button></template></UiEmptyState>
      <UiPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </template>

    <UiFormDrawer v-model="drawerOpen" :title="editing ? '编辑测试环境' : '新增测试环境'" width="min(560px, 94vw)" :loading="saving" @submit="save">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="business-day-form-grid"><el-form-item label="环境编码" prop="env_code"><el-input v-model="form.env_code" maxlength="64" placeholder="如 SIT1" /></el-form-item><el-form-item label="环境名称" prop="env_name"><el-input v-model="form.env_name" maxlength="128" /></el-form-item></div>
        <el-form-item label="环境用途"><el-input v-model="form.purpose" maxlength="255" show-word-limit /></el-form-item>
        <div class="business-day-form-grid"><el-form-item label="主题标识"><el-select v-model="form.theme"><el-option v-for="item in themeOptions" :key="item.value" :label="item.label" :value="item.value"><span class="business-day-theme-option"><i :class="`theme-${item.value}`" />{{ item.label }}</span></el-option></el-select></el-form-item><el-form-item label="排序号"><el-input-number v-model="form.sort_no" :min="0" :max="9999" /></el-form-item></div>
        <el-form-item label="状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
    </UiFormDrawer>
  </section>
</template>
