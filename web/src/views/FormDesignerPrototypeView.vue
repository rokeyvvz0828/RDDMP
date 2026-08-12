<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Component } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar, Check, CircleCheck, Clock, CopyDocument, Delete, Document, EditPen, Grid, InfoFilled, Paperclip, Plus, Rank, Refresh, Setting, User, View } from '@element-plus/icons-vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'

type ViewMode = 'design' | 'preview' | 'schema'
type FieldType = 'text' | 'textarea' | 'number' | 'select' | 'date' | 'datetime' | 'switch' | 'person' | 'attachment'

interface ComponentDefinition {
  type: FieldType
  label: string
  description: string
  icon: Component
}

interface FormField {
  id: string
  key: string
  type: FieldType
  label: string
  span: number
  required: boolean
  enabled: boolean
  placeholder: string
  options?: string[]
}

const modes: { key: ViewMode; label: string; icon: Component }[] = [
  { key: 'design', label: '设计', icon: EditPen },
  { key: 'preview', label: '预览', icon: View },
  { key: 'schema', label: 'Schema', icon: CopyDocument }
]

const componentDefinitions: ComponentDefinition[] = [
  { type: 'text', label: '单行文本', description: '编码、名称等短文本', icon: Document },
  { type: 'textarea', label: '多行文本', description: '描述、背景等长文本', icon: EditPen },
  { type: 'number', label: '数字', description: '金额、数量、排序值', icon: Grid },
  { type: 'select', label: '下拉选择', description: '固定选项或受控数据源', icon: Check },
  { type: 'date', label: '日期', description: '计划日期、截止日期', icon: Calendar },
  { type: 'datetime', label: '日期时间', description: '精确到时间的字段', icon: Clock },
  { type: 'switch', label: '开关', description: '是否启用等布尔值', icon: CircleCheck },
  { type: 'person', label: '人员', description: '负责人、审批人选择', icon: User },
  { type: 'attachment', label: '附件', description: '文件上传和查看', icon: Paperclip }
]

const initialFields: FormField[] = [
  { id: 'field_1', key: 'work_order_no', type: 'text', label: '工单编号', span: 12, required: true, enabled: true, placeholder: '系统自动生成' },
  { id: 'field_2', key: 'work_order_title', type: 'text', label: '工单标题', span: 12, required: true, enabled: true, placeholder: '请输入工单标题' },
  { id: 'field_3', key: 'request_type', type: 'select', label: '需求类型', span: 8, required: true, enabled: true, placeholder: '请选择需求类型', options: ['新功能', '问题修复', '数据变更'] },
  { id: 'field_4', key: 'priority', type: 'select', label: '优先级', span: 8, required: true, enabled: true, placeholder: '请选择优先级', options: ['高', '中', '低'] },
  { id: 'field_5', key: 'owner', type: 'person', label: '责任人', span: 8, required: true, enabled: true, placeholder: '请选择责任人', options: ['张伟', '李敏', '王强'] },
  { id: 'field_6', key: 'planned_date', type: 'date', label: '计划完成日期', span: 12, required: false, enabled: true, placeholder: '请选择日期' },
  { id: 'field_7', key: 'description', type: 'textarea', label: '需求说明', span: 12, required: false, enabled: true, placeholder: '描述需求背景、交付目标和验收标准' },
  { id: 'field_8', key: 'attachments', type: 'attachment', label: '相关附件', span: 24, required: false, enabled: true, placeholder: '上传需求文档或设计稿' }
]

const viewMode = ref<ViewMode>('design')
const fields = ref<FormField[]>(cloneFields())
const selectedFieldId = ref('field_2')
const draggingType = ref<FieldType | null>(null)
const draggingFieldId = ref<string | null>(null)
const dragOverIndex = ref<number | null>(null)
const previewForm = ref<Record<string, unknown>>({})

function cloneFields() { return initialFields.map(field => ({ ...field, options: field.options ? [...field.options] : undefined })) }
const selectedField = computed(() => fields.value.find(field => field.id === selectedFieldId.value) || null)
const selectedDefinition = computed(() => componentDefinitions.find(item => item.type === selectedField.value?.type))
const dsl = computed(() => ({
  schemaVersion: 1,
  scopeKey: 'delivery.work-order',
  mode: 'create',
  layout: { columns: 24, mobileColumns: 1, labelPosition: 'top' },
  fields: fields.value.map(field => ({
    id: field.id,
    key: field.key,
    type: field.type,
    label: field.label,
    span: field.span,
    required: field.required,
    enabled: field.enabled,
    placeholder: field.placeholder,
    ...(field.options?.length ? { options: field.options } : {})
  }))
}))
const schemaText = computed(() => JSON.stringify(dsl.value, null, 2))

function startComponentDrag(event: DragEvent, type: FieldType) {
  draggingType.value = type
  event.dataTransfer?.setData('application/x-form-component', type)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}
function startFieldDrag(event: DragEvent, fieldId: string) {
  draggingFieldId.value = fieldId
  event.dataTransfer?.setData('application/x-form-field', fieldId)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
function clearDragState() {
  draggingType.value = null
  draggingFieldId.value = null
  dragOverIndex.value = null
}
function fieldFromType(type: FieldType): FormField {
  const definition = componentDefinitions.find(item => item.type === type)
  const count = fields.value.filter(field => field.type === type).length + 1
  const key = `${type}_${count}`
  return {
    id: `field_${Date.now()}_${Math.random().toString(16).slice(2)}`,
    key,
    type,
    label: definition?.label || '新字段',
    span: type === 'textarea' || type === 'attachment' ? 24 : 12,
    required: false,
    enabled: true,
    placeholder: `请输入${definition?.label || '内容'}`,
    options: ['选项一', '选项二']
  }
}
function addField(type: FieldType, index = fields.value.length) {
  const field = fieldFromType(type)
  fields.value.splice(index, 0, field)
  selectedFieldId.value = field.id
  viewMode.value = 'design'
  ElMessage.success(`已添加${field.label}`)
}
function handleCanvasDrop(event: DragEvent, index = fields.value.length) {
  const type = (event.dataTransfer?.getData('application/x-form-component') || draggingType.value) as FieldType | ''
  const fieldId = event.dataTransfer?.getData('application/x-form-field') || draggingFieldId.value
  if (type && componentDefinitions.some(item => item.type === type)) {
    addField(type, index)
  } else if (fieldId) {
    const fromIndex = fields.value.findIndex(field => field.id === fieldId)
    if (fromIndex >= 0 && fromIndex !== index && fromIndex !== index - 1) {
      const [field] = fields.value.splice(fromIndex, 1)
      const targetIndex = fromIndex < index ? index - 1 : index
      fields.value.splice(targetIndex, 0, field)
    }
  }
  clearDragState()
}
function selectField(field: FormField) { selectedFieldId.value = field.id; viewMode.value = 'design' }
function deleteField(field: FormField) {
  const index = fields.value.findIndex(item => item.id === field.id)
  fields.value = fields.value.filter(item => item.id !== field.id)
  selectedFieldId.value = fields.value[Math.max(0, index - 1)]?.id || fields.value[0]?.id || ''
  ElMessage.success(`${field.label}已移除`)
}
function updateSelected<K extends keyof FormField>(key: K, value: FormField[K]) {
  if (!selectedField.value) return
  selectedField.value[key] = value
}
function updatePreview(key: string, value: unknown) { previewForm.value[key] = value }
function previewNumber(key: string) {
  const value = previewForm.value[key]
  return typeof value === 'number' ? value : undefined
}
function resetPrototype() {
  fields.value = cloneFields()
  selectedFieldId.value = 'field_2'
  previewForm.value = {}
  viewMode.value = 'design'
  ElMessage.success('已恢复示例表单')
}
function typeLabel(type: FieldType) { return componentDefinitions.find(item => item.type === type)?.label || type }
</script>

<template>
  <section class="form-designer-page">
    <UiPageHeader eyebrow="平台表单能力原型" title="表单设计器" description="平台 DSL、拖拽设计器和自有渲染器的交互原型。当前仅保存在浏览器内存。">
      <template #actions>
        <el-button title="恢复示例字段" @click="resetPrototype"><el-icon><Refresh /></el-icon>重置</el-button>
        <el-button type="primary" title="保存当前原型" @click="ElMessage.info('原型暂不持久化，后续接入发布快照')"><el-icon><Check /></el-icon>保存原型</el-button>
      </template>
    </UiPageHeader>

    <section class="designer-toolbar surface-card">
      <div class="designer-toolbar__context"><span class="designer-toolbar__mark"><el-icon><Setting /></el-icon></span><div><strong>交付工单 · 新建表单</strong><small>delivery.work-order / draft</small></div></div>
      <div class="designer-mode-switch" role="tablist" aria-label="表单设计视图">
        <button v-for="mode in modes" :key="mode.key" type="button" :class="{ 'is-active': viewMode === mode.key }" role="tab" :aria-selected="viewMode === mode.key" @click="viewMode = mode.key"><el-icon><component :is="mode.icon" /></el-icon>{{ mode.label }}</button>
      </div>
      <div class="designer-toolbar__status"><span class="designer-dot" />草稿 · {{ fields.length }} 个字段</div>
    </section>

    <section v-if="viewMode === 'design'" class="designer-workspace">
      <aside class="designer-panel component-library surface-card">
        <div class="designer-panel__header"><div><span class="panel-kicker">COMPONENTS</span><h2>组件目录</h2></div><span class="designer-count">{{ componentDefinitions.length }}</span></div>
        <p class="designer-panel__hint">拖动组件到画布中，开始构建字段。</p>
        <div class="component-list">
          <button v-for="item in componentDefinitions" :key="item.type" type="button" class="component-item" draggable="true" @dragstart="startComponentDrag($event, item.type)" @dragend="clearDragState" @click="addField(item.type)">
            <span class="component-item__icon"><el-icon><component :is="item.icon" /></el-icon></span><span class="component-item__copy"><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span><el-icon class="component-item__drag"><Rank /></el-icon>
          </button>
        </div>
        <div class="component-library__note"><el-icon><InfoFilled /></el-icon><span>后续接入 form-create 时，由适配层将组件配置转换为平台 DSL。</span></div>
      </aside>

      <main class="designer-canvas-panel surface-card">
        <div class="designer-panel__header"><div><span class="panel-kicker">CANVAS</span><h2>表单画布</h2></div><span class="designer-canvas-panel__schema">24 栅格 · 移动端 1 列</span></div>
        <div class="designer-canvas" @dragover.prevent="dragOverIndex = fields.length" @drop.prevent="handleCanvasDrop($event)">
          <div class="canvas-heading"><div><span class="canvas-heading__eyebrow">CREATE FORM</span><h3>交付工单</h3><p>请填写工单信息，提交后进入研发交付流程。</p></div><el-tag type="warning" effect="plain">草稿</el-tag></div>
          <div v-if="!fields.length" class="designer-empty"><el-icon :size="32"><Plus /></el-icon><strong>拖入组件开始设计</strong><span>从左侧选择组件，字段会出现在这里</span></div>
          <div v-else class="canvas-field-grid">
            <div v-for="(field, index) in fields" :key="field.id" class="canvas-field-wrap" :style="{ gridColumn: `span ${field.span}` }" @dragover.prevent="dragOverIndex = index" @drop.prevent.stop="handleCanvasDrop($event, index)">
              <div v-if="dragOverIndex === index" class="field-drop-indicator" />
              <article class="canvas-field" :class="{ 'is-selected': selectedFieldId === field.id, 'is-disabled': !field.enabled }" draggable="true" @dragstart.stop="startFieldDrag($event, field.id)" @dragend="clearDragState" @click="selectField(field)">
                <div class="canvas-field__label"><span>{{ field.label }}</span><b v-if="field.required">必填</b><small>{{ typeLabel(field.type) }}</small></div>
                <div class="canvas-field__control">
                  <el-input v-if="field.type === 'text'" :placeholder="field.placeholder" disabled />
                  <el-input v-else-if="field.type === 'textarea'" type="textarea" :rows="2" :placeholder="field.placeholder" disabled />
                  <el-input-number v-else-if="field.type === 'number'" :placeholder="field.placeholder" controls-position="right" disabled />
                  <el-select v-else-if="['select', 'person'].includes(field.type)" :placeholder="field.placeholder" disabled><el-option v-for="option in field.options" :key="option" :label="option" :value="option" /></el-select>
                  <el-date-picker v-else-if="field.type === 'date'" type="date" :placeholder="field.placeholder" disabled />
                  <el-date-picker v-else-if="field.type === 'datetime'" type="datetime" :placeholder="field.placeholder" disabled />
                  <el-switch v-else-if="field.type === 'switch'" disabled />
                  <el-button v-else type="primary" plain disabled><el-icon><Paperclip /></el-icon>{{ field.placeholder || '上传附件' }}</el-button>
                </div>
                <div v-if="selectedFieldId === field.id" class="canvas-field__actions"><span>拖动排序</span><button type="button" title="删除字段" @click.stop="deleteField(field)"><el-icon><Delete /></el-icon></button></div>
              </article>
            </div>
          </div>
          <div class="canvas-drop-tail" :class="{ 'is-active': dragOverIndex === fields.length }"><el-icon><Plus /></el-icon>拖到这里添加字段</div>
        </div>
      </main>

      <aside class="designer-panel inspector-panel surface-card">
        <div class="designer-panel__header"><div><span class="panel-kicker">INSPECTOR</span><h2>属性配置</h2></div><el-icon class="inspector-setting"><Setting /></el-icon></div>
        <template v-if="selectedField">
          <div class="inspector-selected"><span class="component-item__icon"><el-icon><component :is="selectedDefinition?.icon || Document" /></el-icon></span><div><strong>{{ selectedField.label }}</strong><small>{{ typeLabel(selectedField.type) }} · {{ selectedField.id }}</small></div></div>
          <el-form label-position="top" class="inspector-form">
            <el-form-item label="字段编码"><el-input :model-value="selectedField.key" @update:model-value="updateSelected('key', $event)" /></el-form-item>
            <el-form-item label="字段名称"><el-input :model-value="selectedField.label" @update:model-value="updateSelected('label', $event)" /></el-form-item>
            <el-form-item label="控件类型"><el-select :model-value="selectedField.type" @update:model-value="updateSelected('type', $event)"><el-option v-for="item in componentDefinitions" :key="item.type" :label="item.label" :value="item.type" /></el-select></el-form-item>
            <div class="inspector-two-col"><el-form-item label="栅格宽度"><el-select :model-value="selectedField.span" @update:model-value="updateSelected('span', Number($event))"><el-option v-for="span in [6, 8, 12, 16, 24]" :key="span" :label="`${span} / 24`" :value="span" /></el-select></el-form-item><el-form-item label="字段状态"><el-switch :model-value="selectedField.enabled" active-text="启用" @update:model-value="updateSelected('enabled', $event)" /></el-form-item></div>
            <el-form-item label="提示语"><el-input :model-value="selectedField.placeholder" @update:model-value="updateSelected('placeholder', $event)" /></el-form-item>
            <el-form-item v-if="['select', 'person'].includes(selectedField.type)" label="选项"><el-input :model-value="selectedField.options?.join('\n')" type="textarea" :rows="3" placeholder="每行一个选项" @update:model-value="updateSelected('options', String($event).split('\n').filter(Boolean))" /></el-form-item>
            <div class="inspector-switches"><el-checkbox :model-value="selectedField.required" @update:model-value="updateSelected('required', Boolean($event))">必填字段</el-checkbox><el-checkbox :model-value="selectedField.enabled" @update:model-value="updateSelected('enabled', Boolean($event))">允许使用</el-checkbox></div>
          </el-form>
        </template>
        <div v-else class="inspector-empty"><el-icon :size="28"><Setting /></el-icon><strong>选择一个字段</strong><span>字段属性将在这里显示</span></div>
      </aside>
    </section>

    <section v-else-if="viewMode === 'preview'" class="preview-workspace surface-card">
      <div class="preview-heading"><div><span class="panel-kicker">PREVIEW</span><h2>交付工单 · 新建</h2><p>这是平台自有渲染器的预览效果，控件状态和 DSL 字段保持同步。</p></div><el-tag type="success" effect="plain">可交互预览</el-tag></div>
      <el-form :model="previewForm" label-position="top" class="preview-form"><div class="preview-field-grid"><el-form-item v-for="field in fields.filter(item => item.enabled)" :key="field.id" :label="field.label" :required="field.required" :style="{ gridColumn: `span ${field.span}` }">
        <el-input v-if="field.type === 'text' || field.type === 'textarea'" :model-value="String(previewForm[field.key] || '')" :type="field.type === 'textarea' ? 'textarea' : 'text'" :rows="field.type === 'textarea' ? 4 : undefined" :placeholder="field.placeholder" @update:model-value="updatePreview(field.key, $event)" />
        <el-input-number v-else-if="field.type === 'number'" :model-value="previewNumber(field.key)" :placeholder="field.placeholder" controls-position="right" @update:model-value="updatePreview(field.key, $event)" />
        <el-select v-else-if="['select', 'person'].includes(field.type)" :model-value="previewForm[field.key]" :placeholder="field.placeholder" @update:model-value="updatePreview(field.key, $event)"><el-option v-for="option in field.options" :key="option" :label="option" :value="option" /></el-select>
        <el-date-picker v-else-if="field.type === 'date'" :model-value="previewForm[field.key]" type="date" value-format="YYYY-MM-DD" :placeholder="field.placeholder" @update:model-value="updatePreview(field.key, $event)" />
        <el-date-picker v-else-if="field.type === 'datetime'" :model-value="previewForm[field.key]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" :placeholder="field.placeholder" @update:model-value="updatePreview(field.key, $event)" />
        <el-switch v-else-if="field.type === 'switch'" :model-value="Boolean(previewForm[field.key])" @update:model-value="updatePreview(field.key, $event)" />
        <el-upload v-else drag action="#" :auto-upload="false" :show-file-list="true"><el-icon class="el-icon--upload"><Paperclip /></el-icon><div class="el-upload__text">{{ field.placeholder || '拖入文件或点击上传' }}</div></el-upload>
      </el-form-item></div></el-form>
      <div class="preview-footer"><span><el-icon><Setting /></el-icon>当前为本地预览，不会提交数据</span><div><el-button @click="previewForm = {}">清空</el-button><el-button type="primary" @click="ElMessage.success('预览校验通过，正式版本将进入提交流程')"><el-icon><Check /></el-icon>校验表单</el-button></div></div>
    </section>

    <section v-else class="schema-workspace surface-card">
      <div class="schema-heading"><div><span class="panel-kicker">PLATFORM DSL</span><h2>Schema</h2><p>平台长期保存的契约由自有 DSL 定义，第三方设计器只负责产生编辑结果。</p></div><el-tag type="info" effect="plain">schemaVersion 1</el-tag></div>
      <div class="schema-meta"><div><span>业务范围</span><strong>delivery.work-order</strong></div><div><span>表单模式</span><strong>create</strong></div><div><span>字段数量</span><strong>{{ fields.length }}</strong></div><div><span>布局</span><strong>24 / mobile 1</strong></div></div>
      <pre class="schema-code"><code>{{ schemaText }}</code></pre>
    </section>
  </section>
</template>

<style scoped>
.form-designer-page { min-width: 0; }
.designer-toolbar, .designer-workspace, .preview-workspace, .schema-workspace { min-width: 0; }
.surface-card { border: 1px solid var(--line); background: var(--panel-bg); box-shadow: var(--shadow); border-radius: 6px; }
.designer-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 12px 16px; margin-bottom: 16px; }
.designer-toolbar__context, .designer-toolbar__status, .designer-mode-switch, .designer-toolbar__context > div, .preview-footer, .schema-meta { display: flex; align-items: center; }
.designer-toolbar__context { gap: 10px; min-width: 0; }
.designer-toolbar__context > div { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; min-width: 0; }
.designer-toolbar__context strong { font-size: 13px; }
.designer-toolbar__context small, .designer-toolbar__status { color: var(--muted); font-size: 11px; }
.designer-toolbar__mark { display: grid; place-items: center; width: 34px; height: 34px; color: var(--brand); background: color-mix(in srgb, var(--brand) 11%, var(--panel-bg)); }
.designer-mode-switch { gap: 3px; padding: 3px; background: var(--panel-muted); border-radius: 5px; }
.designer-mode-switch button { display: inline-flex; align-items: center; gap: 6px; height: 30px; padding: 0 13px; border: 0; border-radius: 4px; background: transparent; color: var(--muted); cursor: pointer; font-size: 12px; }
.designer-mode-switch button.is-active { color: var(--brand); background: var(--panel-bg); box-shadow: 0 1px 4px rgba(25, 44, 61, .1); font-weight: 650; }
.designer-toolbar__status { gap: 6px; white-space: nowrap; }
.designer-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--warning); }
.designer-workspace { display: grid; grid-template-columns: 222px minmax(420px, 1fr) 270px; gap: 14px; align-items: start; }
.designer-panel, .designer-canvas-panel { min-height: 650px; }
.designer-panel { padding: 17px 14px; }
.designer-panel__header, .canvas-heading, .preview-heading, .schema-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.designer-panel__header h2, .preview-heading h2, .schema-heading h2 { margin: 7px 0 0; font-size: 17px; }
.designer-count { display: grid; place-items: center; min-width: 24px; height: 24px; color: var(--brand); background: color-mix(in srgb, var(--brand) 10%, var(--panel-bg)); border-radius: 50%; font-size: 11px; font-weight: 700; }
.designer-panel__hint, .preview-heading p, .schema-heading p { margin: 10px 0 16px; color: var(--muted); font-size: 11px; line-height: 1.6; }
.component-list { display: grid; gap: 7px; }
.component-item { display: flex; align-items: center; gap: 9px; min-width: 0; padding: 9px 8px; border: 1px solid var(--line); border-radius: 5px; color: var(--text); background: var(--panel-bg); text-align: left; cursor: grab; }
.component-item:hover { border-color: var(--brand); background: color-mix(in srgb, var(--brand) 5%, var(--panel-bg)); }
.component-item:active { cursor: grabbing; }
.component-item__icon { flex: 0 0 auto; display: grid; place-items: center; width: 28px; height: 28px; color: var(--brand); background: color-mix(in srgb, var(--brand) 10%, var(--panel-bg)); border-radius: 4px; }
.component-item__copy { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1; }
.component-item__copy strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.component-item__copy small { overflow: hidden; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.component-item__drag { color: var(--muted); }
.component-library__note { display: flex; gap: 7px; margin-top: 18px; padding: 10px; color: var(--muted); background: var(--panel-muted); font-size: 10px; line-height: 1.55; }
.component-library__note .el-icon { flex: 0 0 auto; color: var(--brand); margin-top: 1px; }
.designer-canvas-panel { overflow: hidden; }
.designer-canvas-panel > .designer-panel__header { padding: 17px 18px 13px; border-bottom: 1px solid var(--line); }
.designer-canvas-panel__schema { color: var(--muted); font-size: 10px; }
.designer-canvas { min-height: 592px; padding: 26px 24px 18px; background: color-mix(in srgb, var(--panel-muted) 42%, var(--panel-bg)); }
.canvas-heading { padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.canvas-heading__eyebrow { color: var(--brand); font-size: 10px; font-weight: 750; letter-spacing: 1.5px; }
.canvas-heading h3 { margin: 7px 0 4px; font-size: 20px; }
.canvas-heading p { margin: 0; color: var(--muted); font-size: 11px; }
.canvas-field-grid, .preview-field-grid { display: grid; grid-template-columns: repeat(24, minmax(0, 1fr)); gap: 0 14px; padding-top: 17px; }
.canvas-field-wrap { position: relative; min-width: 0; padding-bottom: 14px; }
.canvas-field { position: relative; min-width: 0; padding: 11px 12px 12px; border: 1px solid transparent; background: var(--panel-bg); cursor: grab; }
.canvas-field:hover, .canvas-field.is-selected { border-color: color-mix(in srgb, var(--brand) 60%, var(--line)); box-shadow: 0 3px 12px rgba(25, 44, 61, .06); }
.canvas-field.is-selected { outline: 2px solid color-mix(in srgb, var(--brand) 20%, transparent); }
.canvas-field.is-disabled { opacity: .55; }
.canvas-field__label { display: flex; align-items: center; gap: 5px; min-width: 0; margin-bottom: 7px; }
.canvas-field__label span { overflow: hidden; font-size: 12px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.canvas-field__label b { color: var(--danger); font-size: 9px; font-weight: 500; }
.canvas-field__label small { margin-left: auto; color: var(--muted); font-size: 9px; white-space: nowrap; }
.canvas-field__control { min-width: 0; pointer-events: none; }
.canvas-field__control .el-select, .canvas-field__control .el-date-editor, .canvas-field__control .el-input-number { width: 100%; }
.canvas-field__control > .el-button { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.canvas-field__actions { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; color: var(--muted); font-size: 9px; }
.canvas-field__actions button { display: grid; place-items: center; width: 22px; height: 22px; padding: 0; border: 0; color: var(--danger); background: transparent; cursor: pointer; }
.field-drop-indicator { position: absolute; z-index: 1; top: -4px; right: 0; left: 0; height: 3px; background: var(--brand); }
.canvas-drop-tail { display: flex; justify-content: center; align-items: center; gap: 6px; min-height: 34px; margin-top: 2px; border: 1px dashed var(--line); color: var(--muted); font-size: 10px; }
.canvas-drop-tail.is-active { border-color: var(--brand); color: var(--brand); background: color-mix(in srgb, var(--brand) 6%, var(--panel-bg)); }
.designer-empty, .inspector-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 280px; gap: 9px; color: var(--muted); text-align: center; }
.designer-empty .el-icon, .inspector-empty .el-icon { color: var(--brand); }
.designer-empty strong, .inspector-empty strong { color: var(--text); font-size: 13px; }
.designer-empty span, .inspector-empty span { font-size: 11px; }
.inspector-setting { color: var(--muted); margin-top: 3px; }
.inspector-selected { display: flex; gap: 9px; align-items: center; margin: 18px 0; padding: 10px; background: var(--panel-muted); }
.inspector-selected > div { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.inspector-selected strong { overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.inspector-selected small { color: var(--muted); font-size: 10px; }
.inspector-form :deep(.el-form-item) { margin-bottom: 13px; }
.inspector-form :deep(.el-form-item__label) { padding-bottom: 5px; color: var(--muted); font-size: 11px; line-height: 1.2; }
.inspector-two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }
.inspector-two-col .el-switch { margin-top: 7px; }
.inspector-switches { display: flex; flex-direction: column; gap: 8px; padding-top: 3px; }
.inspector-switches :deep(.el-checkbox__label) { color: var(--text); font-size: 11px; }
.preview-workspace, .schema-workspace { padding: 24px; }
.preview-heading, .schema-heading { padding-bottom: 18px; border-bottom: 1px solid var(--line); }
.preview-form { padding-top: 21px; }
.preview-field-grid :deep(.el-form-item) { min-width: 0; padding: 0 4px; }
.preview-field-grid :deep(.el-form-item__label) { color: var(--text); font-size: 12px; font-weight: 650; }
.preview-field-grid :deep(.el-select), .preview-field-grid :deep(.el-date-editor), .preview-field-grid :deep(.el-input-number) { width: 100%; }
.preview-footer { justify-content: space-between; gap: 14px; padding-top: 18px; border-top: 1px solid var(--line); color: var(--muted); font-size: 11px; }
.preview-footer > span { display: flex; align-items: center; gap: 6px; min-width: 0; }
.schema-meta { flex-wrap: wrap; gap: 0; margin: 20px 0; border: 1px solid var(--line); }
.schema-meta div { display: flex; flex-direction: column; gap: 5px; min-width: 150px; padding: 12px 16px; border-right: 1px solid var(--line); }
.schema-meta div:last-child { border-right: 0; }
.schema-meta span { color: var(--muted); font-size: 10px; }
.schema-meta strong { font-size: 12px; }
.schema-code { overflow: auto; min-height: 500px; margin: 0; padding: 20px; background: #17202a; color: #d7e7ef; font: 12px/1.8 "SFMono-Regular", Consolas, monospace; }

@media (max-width: 1200px) {
  .designer-workspace { grid-template-columns: 196px minmax(360px, 1fr) 250px; gap: 10px; }
  .designer-canvas { padding-right: 15px; padding-left: 15px; }
}
@media (max-width: 760px) {
  .designer-toolbar { align-items: stretch; flex-direction: column; gap: 10px; }
  .designer-toolbar__status { align-self: flex-start; }
  .designer-mode-switch { width: 100%; }
  .designer-mode-switch button { flex: 1; justify-content: center; }
  .designer-workspace { grid-template-columns: 1fr; }
  .designer-panel, .designer-canvas-panel { min-height: 0; }
  .component-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .component-library__note { margin-top: 14px; }
  .designer-canvas { min-height: 0; padding: 18px 12px 14px; }
  .canvas-field-grid, .preview-field-grid { grid-template-columns: 1fr; gap: 0; }
  .canvas-field-wrap, .preview-field-grid :deep(.el-form-item) { grid-column: span 1 !important; }
  .canvas-heading h3 { font-size: 18px; }
  .inspector-panel { order: 3; }
  .preview-workspace, .schema-workspace { padding: 16px 12px; }
  .preview-heading, .schema-heading { flex-direction: column; }
  .preview-footer { align-items: stretch; flex-direction: column; }
  .preview-footer > div { display: flex; justify-content: flex-end; }
  .schema-meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .schema-meta div { min-width: 0; border-right: 1px solid var(--line); border-bottom: 1px solid var(--line); }
  .schema-meta div:nth-child(2n) { border-right: 0; }
  .schema-meta div:nth-last-child(-n+2) { border-bottom: 0; }
}
</style>
