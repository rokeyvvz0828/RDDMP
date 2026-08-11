<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Plus, Refresh, Delete } from '@element-plus/icons-vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiFormDrawer from '../components/ui/UiFormDrawer.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import { createSystem, deleteSystem, listSystem, updateSystem, updateSystemStatus } from '../api/system'
import { apiErrorMessage } from '../api/error'
import type { SystemRow } from '../types/system'
import { formatDateOnly } from '../utils/date'

const categories = ref<SystemRow[]>([])
const params = ref<SystemRow[]>([])
const selectedCategoryId = ref<number | null>(null)
const categoryKeyword = ref('')
const paramKeyword = ref('')
const loadingCategories = ref(false)
const loadingParams = ref(false)
const categoryDrawer = ref(false)
const paramDrawer = ref(false)
const editingCategoryId = ref<number | null>(null)
const editingParamId = ref<number | null>(null)
const saving = ref(false)
const categoryForm = ref({ dict_code: '', dict_name: '' })
const paramForm = ref({ category_id: 0, config_key: '', config_value: '', config_type: 'string', remark: '' })

const selectedCategory = computed(() => categories.value.find(item => item.id === selectedCategoryId.value) || null)

async function loadCategories() {
  loadingCategories.value = true
  try {
    const response = await listSystem('param-categories', { page: 1, size: 1000, keyword: categoryKeyword.value || undefined })
    categories.value = response.data.data.records
    if (!selectedCategoryId.value || !categories.value.some(item => item.id === selectedCategoryId.value)) selectedCategoryId.value = categories.value[0]?.id || null
  } finally {
    loadingCategories.value = false
  }
}

async function loadParams() {
  loadingParams.value = true
  try {
    const response = await listSystem('params', { page: 1, size: 1000, keyword: paramKeyword.value || undefined, categoryId: selectedCategoryId.value || undefined })
    params.value = response.data.data.records
  } finally {
    loadingParams.value = false
  }
}

async function loadAll() {
  try {
    await loadCategories()
    await loadParams()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '参数数据加载失败，请检查权限和服务状态'))
  }
}

function openCategoryCreate() {
  editingCategoryId.value = null
  categoryForm.value = { dict_code: '', dict_name: '' }
  categoryDrawer.value = true
}

function openCategoryEdit(row: SystemRow) {
  editingCategoryId.value = row.id
  categoryForm.value = { dict_code: String(row.dict_code || ''), dict_name: String(row.dict_name || '') }
  categoryDrawer.value = true
}

async function saveCategory() {
  if (!categoryForm.value.dict_code.trim() || !categoryForm.value.dict_name.trim()) {
    ElMessage.warning('请填写参数类别编码和名称')
    return
  }
  saving.value = true
  try {
    if (editingCategoryId.value) await updateSystem('param-categories', editingCategoryId.value, { dict_name: categoryForm.value.dict_name })
    else await createSystem('param-categories', { ...categoryForm.value, status: 1 })
    categoryDrawer.value = false
    await loadCategories()
    ElMessage.success('参数类别已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '参数类别保存失败'))
  } finally {
    saving.value = false
  }
}

function openParamCreate() {
  if (!selectedCategoryId.value) {
    ElMessage.warning('请先选择参数类别')
    return
  }
  editingParamId.value = null
  paramForm.value = { category_id: selectedCategoryId.value, config_key: '', config_value: '', config_type: 'string', remark: '' }
  paramDrawer.value = true
}

function openParamEdit(row: SystemRow) {
  editingParamId.value = row.id
  paramForm.value = {
    category_id: Number(row.category_id || selectedCategoryId.value || 0),
    config_key: String(row.config_key || ''),
    config_value: String(row.config_value || ''),
    config_type: String(row.config_type || 'string'),
    remark: String(row.remark || '')
  }
  paramDrawer.value = true
}

async function saveParam() {
  const form = paramForm.value
  if (!form.category_id || !form.config_key.trim() || !form.config_value.trim()) {
    ElMessage.warning('请填写参数类别、参数键和值')
    return
  }
  if (['number', 'integer'].includes(form.config_type) && !/^[0-9]+$/.test(form.config_value.trim())) {
    ElMessage.warning('数字参数只能填写非负整数')
    return
  }
  saving.value = true
  try {
    if (editingParamId.value) await updateSystem('params', editingParamId.value, { category_id: form.category_id, config_value: form.config_value, config_type: form.config_type, remark: form.remark })
    else await createSystem('params', { ...form, status: 1 })
    paramDrawer.value = false
    await loadParams()
    ElMessage.success('参数已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '参数保存失败'))
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: SystemRow, resource: 'params' | 'param-categories') {
  try {
    await ElMessageBox.confirm('确认变更该参数状态吗？', '状态变更', { type: 'warning' })
    await updateSystemStatus(resource, row.id, row.status === 1 ? 0 : 1)
    await (resource === 'params' ? loadParams() : loadCategories())
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '状态更新失败'))
  }
}

async function removeRow(row: SystemRow, resource: 'params' | 'param-categories') {
  try {
    await ElMessageBox.confirm('删除后将不再显示该记录，确认继续吗？', '删除确认', { type: 'warning' })
    await deleteSystem(resource, row.id)
    await (resource === 'params' ? loadParams() : loadCategories())
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '删除失败'))
  }
}

watch(selectedCategoryId, () => { void loadParams() })
onMounted(loadAll)
</script>

<template>
  <section class="parameter-page">
    <UiToolbar>
      <span class="muted">按参数类别维护系统、流程和 AI 运行参数</span>
      <template #actions><el-button type="primary" @click="openParamCreate"><el-icon><Plus /></el-icon>新增参数</el-button><el-button plain @click="openCategoryCreate"><el-icon><Plus /></el-icon>新增类别</el-button></template>
    </UiToolbar>
    <div class="parameter-layout">
      <el-card class="parameter-categories" shadow="never">
        <template #header><div class="parameter-panel-heading"><strong>参数类别</strong><el-button text circle title="刷新类别" @click="loadCategories"><el-icon><Refresh /></el-icon></el-button></div></template>
        <el-input v-model="categoryKeyword" clearable placeholder="搜索类别" @keyup.enter="loadCategories" />
        <div v-loading="loadingCategories" class="parameter-category-list">
          <button v-for="category in categories" :key="category.id" class="parameter-category" :class="{ active: selectedCategoryId === category.id }" type="button" @click="selectedCategoryId = category.id">
            <span><strong>{{ category.dict_name }}</strong><small>{{ category.dict_code }}</small></span>
            <el-dropdown trigger="click" @command="(command: string) => command === 'edit' ? openCategoryEdit(category) : command === 'status' ? toggleStatus(category, 'param-categories') : removeRow(category, 'param-categories')">
              <el-button text circle title="类别操作"><el-icon><Edit /></el-icon></el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="edit">编辑</el-dropdown-item><el-dropdown-item command="status">{{ category.status === 1 ? '停用' : '启用' }}</el-dropdown-item><el-dropdown-item command="delete">删除</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </button>
          <el-empty v-if="!categories.length" description="暂无参数类别" />
        </div>
      </el-card>
      <div class="parameter-detail">
        <div class="parameter-detail-heading"><div><span class="panel-kicker">参数明细</span><h3>{{ selectedCategory?.dict_name || '请选择参数类别' }}</h3></div><el-input v-model="paramKeyword" clearable placeholder="搜索参数键" style="width:240px" @keyup.enter="loadParams" /></div>
        <UiDataTable :data="params" :loading="loadingParams" row-key="id" border empty-text="当前类别暂无参数">
          <el-table-column prop="config_key" label="参数键" min-width="260" />
          <el-table-column prop="config_value" label="参数值" min-width="180" show-overflow-tooltip />
          <el-table-column prop="config_type" label="类型" width="110" />
          <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="90"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '0': '停用', '1': '启用' }" /></template></el-table-column>
          <el-table-column label="更新时间" width="120"><template #default="scope">{{ formatDateOnly(scope.row.updated_at) }}</template></el-table-column>
          <el-table-column label="操作" width="190" fixed="right"><template #default="scope"><el-button link type="primary" @click="openParamEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="danger" @click="removeRow(scope.row, 'params')"><el-icon><Delete /></el-icon>删除</el-button></template></el-table-column>
        </UiDataTable>
      </div>
    </div>
    <UiFormDrawer v-model="categoryDrawer" title="参数类别" :loading="saving" @submit="saveCategory">
      <el-form label-position="top"><el-form-item label="类别编码" required><el-input v-model="categoryForm.dict_code" :disabled="Boolean(editingCategoryId)" /></el-form-item><el-form-item label="类别名称" required><el-input v-model="categoryForm.dict_name" /></el-form-item></el-form>
    </UiFormDrawer>
    <UiFormDrawer v-model="paramDrawer" :title="editingParamId ? '编辑参数' : '新增参数'" :loading="saving" @submit="saveParam">
      <el-form label-position="top"><el-form-item label="参数类别" required><el-select v-model="paramForm.category_id" style="width:100%"><el-option v-for="category in categories" :key="category.id" :label="category.dict_name" :value="category.id" /></el-select></el-form-item><el-form-item label="参数键" required><el-input v-model="paramForm.config_key" :disabled="Boolean(editingParamId)" /></el-form-item><el-form-item label="参数值" required><el-input v-model="paramForm.config_value" /></el-form-item><el-form-item label="参数类型" required><el-select v-model="paramForm.config_type" style="width:100%"><el-option label="字符串" value="string" /><el-option label="数字" value="number" /><el-option label="整数" value="integer" /><el-option label="布尔值" value="boolean" /></el-select></el-form-item><el-form-item label="备注"><el-input v-model="paramForm.remark" type="textarea" :rows="3" /></el-form-item></el-form>
    </UiFormDrawer>
  </section>
</template>
