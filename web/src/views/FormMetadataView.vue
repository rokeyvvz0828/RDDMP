<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Upload } from '@element-plus/icons-vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiFormDrawer from '../components/ui/UiFormDrawer.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { apiErrorMessage } from '../api/error'
import { createFormMetadataField, createFormMetadataScope, deleteFormMetadataField, getFormMetadataSchema, listFormMetadataModules, listFormMetadataScopes, publishFormMetadata, updateFormMetadataField, updateFormMetadataScope } from '../api/form-metadata'
import type { FormMetadataField, FormMetadataModule, FormMetadataOption, FormMetadataRule, FormMetadataSchema, FormMetadataScope } from '../types/form-metadata'

const scopes = ref<FormMetadataScope[]>([])
const modules = ref<FormMetadataModule[]>([])
const selectedScopeId = ref<number | null>(null)
const schema = ref<FormMetadataSchema | null>(null)
const keyword = ref('')
const loadingScopes = ref(false)
const loadingSchema = ref(false)
const saving = ref(false)
const pageError = ref('')
const scopeDrawer = ref(false)
const fieldDrawer = ref(false)
const editingScopeId = ref<number | null>(null)
const editingFieldId = ref<number | null>(null)

const scopeForm = ref({ scope_key: '', scope_name: '', module_key: '', entity_type: '', enabled: true })
const fieldForm = ref({
  field_key: '',
  label: '',
  field_kind: 'extension',
  field_role: 'normal',
  input_type: 'text',
  value_type: 'string',
  source_type: 'none',
  source_key: '',
  component_key: '',
  native_column: '',
  multiple: false,
  column_span: 12,
  visible: true,
  form_available: true,
  list_visible: false,
  filterable: false,
  sortable: false,
  dashboard_dimension: false,
  placeholder: '',
  help_text: '',
  sort_no: 10,
  enabled: true,
  rules: [] as FormMetadataRule[],
  options: [] as FormMetadataOption[]
})

const selectedScope = computed(() => scopes.value.find(item => item.id === selectedScopeId.value) || null)
const fields = computed(() => schema.value?.fields || [])
const moduleOptions = computed<FormMetadataModule[]>(() => {
  const options = new Map<string, FormMetadataModule>()
  modules.value.forEach(item => options.set(item.module_key, item))
  scopes.value.forEach(item => {
    if (!options.has(item.module_key)) options.set(item.module_key, { id: 0, module_key: item.module_key, module_name: item.module_key })
  })
  return [...options.values()].sort((left, right) => left.module_name.localeCompare(right.module_name))
})
const generatedEntityKey = computed(() => scopeForm.value.entity_type.trim().replace(/_/g, '-'))
const generatedScopeKey = computed(() => scopeForm.value.module_key.trim() && generatedEntityKey.value ? `${scopeForm.value.module_key.trim()}.${generatedEntityKey.value}` : '')
const generatedPermissionPrefix = computed(() => scopeForm.value.module_key.trim() && generatedEntityKey.value ? `${scopeForm.value.module_key.trim()}:${generatedEntityKey.value}` : '')

const inputTypes = [
  { value: 'text', label: '单行文本' }, { value: 'textarea', label: '多行文本' }, { value: 'number', label: '数字' },
  { value: 'date', label: '日期' }, { value: 'datetime', label: '日期时间' }, { value: 'select', label: '下拉选择' },
  { value: 'radio', label: '单选' }, { value: 'checkbox', label: '多选' }, { value: 'boolean', label: '开关' },
  { value: 'person', label: '人员' }, { value: 'organization', label: '组织' }, { value: 'attachment', label: '附件' },
  { value: 'rich_text', label: '富文本' }, { value: 'json', label: '结构化 JSON' }
]
const valueTypes = [
  { value: 'string', label: '字符串' }, { value: 'text', label: '长文本' }, { value: 'code', label: '编码' },
  { value: 'integer', label: '整数' }, { value: 'decimal', label: '小数' }, { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' }, { value: 'boolean', label: '布尔值' }, { value: 'reference', label: '引用' }, { value: 'json', label: 'JSON' }
]
const sourceTypes = [
  { value: 'none', label: '无选项来源' }, { value: 'static', label: '静态选项' }, { value: 'dict', label: '系统字典' },
  { value: 'user', label: '平台用户' }, { value: 'organization', label: '组织机构' }, { value: 'role', label: '角色' },
  { value: 'attachment', label: '附件资源' }, { value: 'api', label: '受控 API' }
]

function flag(value: number | boolean | undefined) { return value === true || value === 1 }
function inputLabel(value: string) { return inputTypes.find(item => item.value === value)?.label || value }
function sourceLabel(value: string) { return sourceTypes.find(item => item.value === value)?.label || value }

async function loadScopes() {
  loadingScopes.value = true
  pageError.value = ''
  try {
    const response = await listFormMetadataScopes(keyword.value)
    scopes.value = response.data.data
    if (!selectedScopeId.value || !scopes.value.some(item => item.id === selectedScopeId.value)) selectedScopeId.value = scopes.value[0]?.id || null
  } catch (error) {
    pageError.value = apiErrorMessage(error, '业务对象加载失败，请检查权限和服务状态')
  } finally { loadingScopes.value = false }
}

async function loadModules() {
  try { modules.value = (await listFormMetadataModules()).data.data }
  catch { modules.value = [] }
}

async function loadSchema() {
  if (!selectedScopeId.value) { schema.value = null; return }
  loadingSchema.value = true
  pageError.value = ''
  try { schema.value = (await getFormMetadataSchema(selectedScopeId.value)).data.data }
  catch (error) { pageError.value = apiErrorMessage(error, '输入项配置加载失败') }
  finally { loadingSchema.value = false }
}

function openScopeCreate() {
  editingScopeId.value = null
  scopeForm.value = { scope_key: '', scope_name: '', module_key: moduleOptions.value[0]?.module_key || '', entity_type: '', enabled: true }
  scopeDrawer.value = true
}
function openScopeEdit(scope: FormMetadataScope) {
  editingScopeId.value = scope.id
  scopeForm.value = { scope_key: scope.scope_key, scope_name: scope.scope_name, module_key: scope.module_key, entity_type: scope.entity_type, enabled: flag(scope.enabled) }
  scopeDrawer.value = true
}
async function saveScope() {
  const form = scopeForm.value
  if (!form.scope_name.trim() || !form.module_key.trim() || !form.entity_type.trim() || !generatedScopeKey.value) {
    ElMessage.warning('请填写完整的业务对象信息')
    return
  }
  saving.value = true
  try {
    const payload = { scope_key: editingScopeId.value ? form.scope_key : generatedScopeKey.value, scope_name: form.scope_name, module_key: form.module_key, entity_type: form.entity_type, permission_prefix: generatedPermissionPrefix.value, enabled: form.enabled ? 1 : 0 }
    if (editingScopeId.value) await updateFormMetadataScope(editingScopeId.value, payload)
    else {
      const created = await createFormMetadataScope(payload)
      selectedScopeId.value = created.data.data.id
    }
    scopeDrawer.value = false
    await loadScopes()
    await loadSchema()
    ElMessage.success('业务对象已保存')
  } catch (error) { ElMessage.error(apiErrorMessage(error, '业务对象保存失败')) }
  finally { saving.value = false }
}

function emptyField(): typeof fieldForm.value {
  return { field_key: '', label: '', field_kind: 'extension', field_role: 'normal', input_type: 'text', value_type: 'string', source_type: 'none', source_key: '', component_key: '', native_column: '', multiple: false, column_span: 12, visible: true, form_available: true, list_visible: false, filterable: false, sortable: false, dashboard_dimension: false, placeholder: '', help_text: '', sort_no: (fields.value.length + 1) * 10, enabled: true, rules: [], options: [] }
}
function openFieldCreate() { editingFieldId.value = null; fieldForm.value = emptyField(); fieldDrawer.value = true }
function openFieldEdit(field: FormMetadataField) {
  editingFieldId.value = field.id
  fieldForm.value = { field_key: field.field_key, label: field.label, field_kind: field.field_kind, field_role: field.field_role || 'normal', input_type: field.input_type, value_type: field.value_type, source_type: field.source_type, source_key: field.source_key || '', component_key: field.component_key || '', native_column: field.native_column || '', multiple: flag(field.multiple), column_span: field.column_span, visible: flag(field.visible), form_available: flag(field.form_available), list_visible: flag(field.list_visible), filterable: flag(field.filterable), sortable: flag(field.sortable), dashboard_dimension: flag(field.dashboard_dimension), placeholder: field.placeholder || '', help_text: field.help_text || '', sort_no: field.sort_no, enabled: flag(field.enabled), rules: field.rules.map(item => ({ ...item, required: flag(item.required), editable: flag(item.editable), visible: flag(item.visible) })), options: field.options.map(item => ({ ...item })) }
  fieldDrawer.value = true
}
function addRule() { fieldForm.value.rules.push({ action_code: 'submit', condition_type: 'status', condition_key: '', required: true, editable: true, visible: true }) }
function addOption() { fieldForm.value.options.push({ option_value: '', option_label: '', option_group: '', sort_no: fieldForm.value.options.length * 10 }) }
async function saveField() {
  const form = fieldForm.value
  if (!selectedScopeId.value || !form.field_key.trim() || !form.label.trim()) { ElMessage.warning('请填写字段编码和显示名称'); return }
  if (form.source_type === 'static' && form.options.some(item => !item.option_value.trim() || !item.option_label.trim())) { ElMessage.warning('静态选项的值和名称不能为空'); return }
  if (form.source_type !== 'static') form.options = []
  saving.value = true
  try {
    const payload = { ...form, multiple: form.multiple ? 1 : 0, visible: form.visible ? 1 : 0, form_available: form.form_available ? 1 : 0, list_visible: form.list_visible ? 1 : 0, filterable: form.filterable ? 1 : 0, sortable: form.sortable ? 1 : 0, dashboard_dimension: form.dashboard_dimension ? 1 : 0, enabled: form.enabled ? 1 : 0, rules: form.rules.map(item => ({ ...item, required: flag(item.required) ? 1 : 0, editable: flag(item.editable) ? 1 : 0, visible: flag(item.visible) ? 1 : 0 })), options: form.options }
    if (editingFieldId.value) await updateFormMetadataField(selectedScopeId.value, editingFieldId.value, payload)
    else await createFormMetadataField(selectedScopeId.value, payload)
    fieldDrawer.value = false
    await loadSchema()
    ElMessage.success('输入项已保存')
  } catch (error) { ElMessage.error(apiErrorMessage(error, '输入项保存失败')) }
  finally { saving.value = false }
}
async function removeField(field: FormMetadataField) {
  if (!selectedScopeId.value) return
  try {
    await ElMessageBox.confirm(`确认删除输入项“${field.label}”吗？历史字段值会保留。`, '删除输入项', { type: 'warning' })
    await deleteFormMetadataField(selectedScopeId.value, field.id)
    await loadSchema()
  } catch (error) { if ((error as { action?: string }).action !== 'cancel') ElMessage.error(apiErrorMessage(error, '输入项删除失败')) }
}
async function publish() {
  if (!selectedScopeId.value) return
  try {
    await ElMessageBox.confirm('发布后将成为该业务对象的当前配置版本，确认发布吗？', '发布配置', { type: 'warning', confirmButtonText: '发布' })
    const result = await publishFormMetadata(selectedScopeId.value, '系统设置手工发布')
    await loadScopes(); await loadSchema()
    ElMessage.success(`配置已发布，版本 ${result.data.data.revisionNo}`)
  } catch (error) { if ((error as { action?: string }).action !== 'cancel') ElMessage.error(apiErrorMessage(error, '配置发布失败')) }
}

watch(selectedScopeId, () => { void loadSchema() })
onMounted(() => { void Promise.all([loadScopes(), loadModules()]) })
</script>

<template>
  <section class="form-metadata-page">
    <UiPageHeader eyebrow="系统管理" title="输入项配置" description="维护业务对象的字段主数据、校验规则、选项来源和列表能力。分区与布局请在表单视图设计器中编排。">
      <template #actions><el-button type="primary" @click="openFieldCreate" :disabled="!selectedScopeId"><el-icon><Plus /></el-icon>新增字段</el-button><el-button type="success" plain @click="publish" :disabled="!selectedScopeId || loadingSchema"><el-icon><Upload /></el-icon>发布配置</el-button></template>
    </UiPageHeader>

    <el-alert v-if="pageError" class="metadata-alert" type="error" :title="pageError" show-icon :closable="false" />
    <div class="form-metadata-layout">
      <el-card class="metadata-scope-panel" shadow="never">
        <template #header><div class="metadata-panel-heading"><strong>业务对象</strong><span class="metadata-panel-actions"><el-button plain size="small" @click="openScopeCreate"><el-icon><Plus /></el-icon>新增业务对象</el-button><el-button text circle title="刷新业务对象" @click="loadScopes"><el-icon><Refresh /></el-icon></el-button></span></div></template>
        <el-input v-model="keyword" clearable placeholder="搜索业务对象" @keyup.enter="loadScopes" />
        <div v-loading="loadingScopes" class="metadata-scope-list">
          <button v-for="scope in scopes" :key="scope.id" type="button" class="metadata-scope-item" :class="{ active: selectedScopeId === scope.id }" @click="selectedScopeId = scope.id">
            <span><strong>{{ scope.scope_name }}</strong><small>{{ scope.scope_key }}</small><small>{{ scope.field_count || 0 }} 个字段</small></span><el-button text circle title="编辑业务对象" @click.stop="openScopeEdit(scope)"><el-icon><Edit /></el-icon></el-button>
          </button>
          <el-empty v-if="!loadingScopes && !scopes.length" description="暂无业务对象，请先新增" />
        </div>
      </el-card>

      <main class="metadata-detail" v-loading="loadingSchema">
        <template v-if="schema && selectedScope">
          <div class="metadata-detail-header"><div><span class="panel-kicker">当前业务对象</span><h3>{{ selectedScope.scope_name }}</h3><p>{{ selectedScope.scope_key }} · {{ selectedScope.entity_type }} · 权限前缀 {{ selectedScope.permission_prefix }}</p></div><el-tag :type="selectedScope.published_revision_id ? 'success' : 'info'" effect="plain">{{ selectedScope.published_revision_id ? '已发布' : '未发布' }}</el-tag></div>
          <section class="metadata-block"><header class="metadata-block__header"><div><span class="panel-kicker">字段主列表</span><h3>字段定义</h3><p class="metadata-block__hint">字段编码、控件和通用属性在这里统一维护；字段进入表单、列表或分步视图后的分区与布局由设计器管理。</p></div><el-button text type="primary" @click="openFieldCreate"><el-icon><Plus /></el-icon>新增字段</el-button></header><UiDataTable :data="fields" :loading="loadingSchema" row-key="id" border empty-text="暂无字段定义"><el-table-column prop="label" label="显示名称" min-width="170" fixed="left"><template #default="scope"><div class="field-name"><strong>{{ scope.row.label }}</strong><small>{{ scope.row.field_key }}</small></div></template></el-table-column><el-table-column label="控件" width="120"><template #default="scope">{{ inputLabel(scope.row.input_type) }}</template></el-table-column><el-table-column label="字段角色" width="110"><template #default="scope"><el-tag v-if="scope.row.field_role === 'status'" type="warning" size="small">业务状态</el-tag><span v-else>普通字段</span></template></el-table-column><el-table-column label="来源" min-width="120"><template #default="scope">{{ sourceLabel(scope.row.source_type) }}</template></el-table-column><el-table-column label="页面能力" min-width="170"><template #default="scope"><el-tag v-if="flag(scope.row.visible)" size="small" effect="plain">表单</el-tag><el-tag v-if="flag(scope.row.list_visible)" size="small" effect="plain">列表</el-tag><el-tag v-if="flag(scope.row.filterable)" size="small" effect="plain">筛选</el-tag></template></el-table-column><el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="flag(scope.row.enabled) ? 'success' : 'info'" size="small">{{ flag(scope.row.enabled) ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="150" fixed="right"><template #default="scope"><el-button link type="primary" @click="openFieldEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="danger" @click="removeField(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></template></el-table-column></UiDataTable></section>
          <section class="metadata-block metadata-revision-block"><header class="metadata-block__header"><div><span class="panel-kicker">发布记录</span><h3>配置版本</h3></div></header><el-table :data="schema.revisions" size="small" border><el-table-column prop="revision_no" label="版本" width="90" /><el-table-column prop="revision_status" label="状态" width="110" /><el-table-column prop="change_summary" label="变更说明" min-width="220" show-overflow-tooltip /><el-table-column prop="created_at" label="创建时间" min-width="170" /></el-table></section>
        </template>
        <el-empty v-else-if="!loadingSchema && !pageError" description="请选择或新增业务对象" />
      </main>
    </div>

    <UiFormDrawer v-model="scopeDrawer" :title="editingScopeId ? '编辑业务对象' : '新增业务对象'" :loading="saving" width="560px" @submit="saveScope"><el-form label-position="top"><el-form-item label="对象编码"><el-input :model-value="editingScopeId ? scopeForm.scope_key : generatedScopeKey" disabled /></el-form-item><el-form-item label="对象名称" required><el-input v-model="scopeForm.scope_name" /></el-form-item><el-form-item label="业务模块" required><el-select v-model="scopeForm.module_key" filterable :disabled="Boolean(editingScopeId)" placeholder="选择一级菜单业务模块"><el-option v-for="module in moduleOptions" :key="module.module_key" :label="`${module.module_name}（${module.module_key}）`" :value="module.module_key" /></el-select></el-form-item><el-form-item label="实体编码" required><el-input v-model="scopeForm.entity_type" :disabled="Boolean(editingScopeId)" placeholder="例如 work_order" /></el-form-item><el-form-item label="权限前缀（自动生成）"><el-input :model-value="generatedPermissionPrefix" disabled /></el-form-item><el-form-item label="启用"><el-switch v-model="scopeForm.enabled" /></el-form-item></el-form></UiFormDrawer>
    <UiFormDrawer v-model="fieldDrawer" :title="editingFieldId ? '编辑字段' : '新增字段'" :loading="saving" width="700px" @submit="saveField"><el-form label-position="top"><div class="metadata-form-grid"><el-form-item label="字段编码" required><el-input v-model="fieldForm.field_key" :disabled="Boolean(editingFieldId)" /></el-form-item><el-form-item label="显示名称" required><el-input v-model="fieldForm.label" /></el-form-item><el-form-item label="字段归类"><el-select v-model="fieldForm.field_kind"><el-option label="扩展字段" value="extension" /><el-option label="内置字段元数据" value="builtin" /></el-select></el-form-item><el-form-item label="字段角色"><el-select v-model="fieldForm.field_role"><el-option label="普通字段" value="normal" /><el-option label="业务状态" value="status" /></el-select></el-form-item><el-form-item label="输入控件"><el-select v-model="fieldForm.input_type" filterable><el-option v-for="item in inputTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="存储类型"><el-select v-model="fieldForm.value_type"><el-option v-for="item in valueTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="选项来源"><el-select v-model="fieldForm.source_type"><el-option v-for="item in sourceTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="来源编码"><el-input v-model="fieldForm.source_key" :disabled="fieldForm.source_type === 'none' || fieldForm.source_type === 'static'" placeholder="字典编码或受控 API 编码" /></el-form-item><el-form-item label="桌面栅格宽度"><el-input-number v-model="fieldForm.column_span" :min="1" :max="24" /></el-form-item><el-form-item label="排序"><el-input-number v-model="fieldForm.sort_no" :min="0" :max="9999" /></el-form-item></div><el-form-item label="提示语"><el-input v-model="fieldForm.placeholder" /></el-form-item><el-form-item label="字段说明"><el-input v-model="fieldForm.help_text" type="textarea" :rows="2" /></el-form-item><div class="metadata-switch-row"><el-checkbox v-model="fieldForm.multiple">允许多值</el-checkbox><el-checkbox v-model="fieldForm.visible">表单展示</el-checkbox><el-checkbox v-model="fieldForm.list_visible">列表展示</el-checkbox><el-checkbox v-model="fieldForm.filterable">允许筛选</el-checkbox><el-checkbox v-model="fieldForm.sortable">允许排序</el-checkbox><el-checkbox v-model="fieldForm.dashboard_dimension">统计维度</el-checkbox><el-checkbox v-model="fieldForm.enabled">启用</el-checkbox></div><div v-if="fieldForm.source_type === 'static'" class="metadata-form-subsection"><header><strong>静态选项</strong><el-button text type="primary" @click="addOption"><el-icon><Plus /></el-icon>新增选项</el-button></header><div v-for="(option, index) in fieldForm.options" :key="index" class="metadata-inline-row"><el-input v-model="option.option_value" placeholder="选项值" /><el-input v-model="option.option_label" placeholder="显示名称" /><el-input-number v-model="option.sort_no" :min="0" controls-position="right" /><el-button text type="danger" title="删除选项" @click="fieldForm.options.splice(index, 1)"><el-icon><Delete /></el-icon></el-button></div><el-empty v-if="!fieldForm.options.length" description="暂无静态选项" /></div><div class="metadata-form-subsection"><header><strong>条件规则</strong><el-button text type="primary" @click="addRule"><el-icon><Plus /></el-icon>新增规则</el-button></header><div v-for="(rule, index) in fieldForm.rules" :key="index" class="metadata-rule-row"><el-select v-model="rule.action_code" placeholder="动作"><el-option label="新建" value="create" /><el-option label="编辑" value="edit" /><el-option label="提交" value="submit" /><el-option label="审批" value="approve" /><el-option label="查询" value="view" /></el-select><el-input v-model="rule.condition_key" placeholder="状态/角色编码" /><el-checkbox v-model="rule.required">必填</el-checkbox><el-checkbox v-model="rule.editable">可编辑</el-checkbox><el-button text type="danger" title="删除规则" @click="fieldForm.rules.splice(index, 1)"><el-icon><Delete /></el-icon></el-button></div><el-empty v-if="!fieldForm.rules.length" description="暂无条件规则，字段按默认属性展示" /></div></el-form></UiFormDrawer>
  </section>
</template>

<style scoped>
.form-metadata-page { min-width: 0; }
.metadata-alert { margin-bottom: 16px; }
.form-metadata-layout { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 16px; min-width: 0; }
.metadata-scope-panel { min-width: 0; }
.metadata-panel-heading, .metadata-block__header, .metadata-detail-header, .metadata-scope-item, .metadata-form-subsection > header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.metadata-panel-actions { display: inline-flex; align-items: center; gap: 4px; }
.metadata-scope-list { margin-top: 12px; display: grid; gap: 6px; }
.metadata-scope-item { width: 100%; border: 1px solid transparent; background: transparent; border-radius: 6px; padding: 10px; text-align: left; color: inherit; cursor: pointer; }
.metadata-scope-item:hover, .metadata-scope-item.active { border-color: var(--brand); background: var(--surface-muted); }
.metadata-scope-item > span { min-width: 0; display: grid; gap: 3px; }
.metadata-scope-item small, .metadata-detail-header p, .field-name small { color: var(--text-secondary); font-size: 12px; }
.metadata-scope-item small, .field-name small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.metadata-detail { min-width: 0; display: grid; gap: 16px; }
.metadata-detail-header { padding: 4px 2px 0; }
.metadata-detail-header h3, .metadata-block__header h3 { margin: 4px 0 0; font-size: 18px; }
.metadata-detail-header p { margin: 6px 0 0; }
.metadata-block { min-width: 0; padding: 18px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.metadata-block__header { margin-bottom: 14px; }
.metadata-block__hint { max-width: 720px; margin: 5px 0 0; color: var(--text-secondary); font-size: 12px; line-height: 1.5; }
.field-name { display: grid; gap: 3px; }
.metadata-revision-block { padding-bottom: 12px; }
.metadata-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.metadata-form-grid .el-form-item { min-width: 0; }
.metadata-switch-row { display: flex; flex-wrap: wrap; gap: 12px 18px; padding: 4px 0 12px; border-bottom: 1px solid var(--line); }
.metadata-form-subsection { padding-top: 16px; }
.metadata-form-subsection > header { margin-bottom: 10px; }
.metadata-inline-row, .metadata-rule-row { display: grid; align-items: center; gap: 8px; margin-bottom: 8px; }
.metadata-inline-row { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 100px 34px; }
.metadata-rule-row { grid-template-columns: 100px minmax(0, 1fr) auto auto 34px; }
@media (max-width: 760px) {
  .form-metadata-layout { grid-template-columns: 1fr; }
  .metadata-scope-panel { order: 0; }
  .metadata-detail { order: 1; }
  .metadata-form-grid { grid-template-columns: 1fr; }
  .metadata-detail-header, .metadata-block__header { align-items: flex-start; }
  .metadata-detail-header { flex-wrap: wrap; }
  .metadata-inline-row, .metadata-rule-row { grid-template-columns: 1fr 1fr; }
  .metadata-inline-row .el-input-number, .metadata-inline-row .el-button, .metadata-rule-row .el-button { grid-column: span 1; }
}
</style>
