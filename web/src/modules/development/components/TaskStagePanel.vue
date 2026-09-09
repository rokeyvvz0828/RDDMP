<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Close, Download, Refresh, Upload, View } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import UiFilePreview from '../../../components/ui/UiFilePreview.vue'
import { uploadAttachment, getAttachmentDownload, getAttachmentPreview } from '../../../api/attachments'
import { getFilePreviewCapabilities } from '../../../api/file-preview'
import { apiErrorMessage } from '../../../api/error'
import { developmentApi } from '../api'
import { useDraftGuard } from '../composables/useDevelopmentTasks'
import type { AttachmentRef, DevelopmentTask, StageInput, TaskStage } from '../types'

const props = defineProps<{ task: DevelopmentTask }>()
const emit = defineEmits<{ dirty: [value: boolean]; saved: [stage: TaskStage] }>()
const formRef = ref<FormInstance>(), stage = ref<TaskStage | null>(null), loading = ref(false), saving = ref(false), uploading = ref(0), error = ref(''), fileError = ref('')
const previewEnabled = ref(false), preview = reactive({ open: false, url: null as string | null, fileName: '' })
const fileBusy = ref(false)
const baseline = ref('')
const form = reactive<StageInput>({ designPlanStart: null, designPlanEnd: null, designDocumentPath: '', designAttachmentIds: [], implementationActualStart: null, implementationActualEnd: null, codeWalkAttachmentIds: [], testReportAttachmentIds: [], notApplicableDesign: false, notApplicableImplementation: false, rowVersion: 0 })
const files = reactive<{ design: AttachmentRef[]; codeWalk: AttachmentRef[]; testReport: AttachmentRef[] }>({ design: [], codeWalk: [], testReport: [] })
const groups = [{ key: 'design', label: '设计附件' }, { key: 'codeWalk', label: '代码走查附件' }, { key: 'testReport', label: '测试报告' }] as const
type FileGroup = typeof groups[number]['key']
const dirty = computed(() => Boolean(baseline.value) && JSON.stringify(form) !== baseline.value)
const submitting = computed(() => saving.value || uploading.value > 0)
const readOnly = computed(() => Boolean(stage.value?.readOnly) || !props.task.allowedActions.includes('UPDATE'))
const guard = useDraftGuard(dirty, submitting)
let ticket = 0, disposed = false
const dateRule = (start: 'designPlanStart' | 'implementationActualStart', end: 'designPlanEnd' | 'implementationActualEnd') => ({ validator: (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => callback(form[start] && form[end] && form[end]! < form[start]! ? new Error('结束日期不能早于开始日期') : undefined), trigger: 'change' })
const rules: FormRules = { designPlanEnd: [dateRule('designPlanStart', 'designPlanEnd')], implementationActualEnd: [dateRule('implementationActualStart', 'implementationActualEnd')] }

function assign(value: TaskStage) {
  stage.value = value
  Object.assign(form, { designPlanStart: value.designPlanStart, designPlanEnd: value.designPlanEnd, designDocumentPath: value.designDocumentPath,
    designAttachmentIds: value.designAttachments.map(file => file.id), implementationActualStart: value.implementationActualStart,
    implementationActualEnd: value.implementationActualEnd, codeWalkAttachmentIds: value.codeWalkAttachments.map(file => file.id),
    testReportAttachmentIds: value.testReportAttachments.map(file => file.id), notApplicableDesign: value.notApplicableDesign,
    notApplicableImplementation: value.notApplicableImplementation, rowVersion: value.rowVersion })
  files.design = [...value.designAttachments]; files.codeWalk = [...value.codeWalkAttachments]; files.testReport = [...value.testReportAttachments]
  baseline.value = JSON.stringify(form)
}
async function load() {
  const current = ++ticket, id = props.task.id
  loading.value = true; error.value = ''
  try { const result = await developmentApi.stages(id); if (current === ticket && !disposed) assign(result) }
  catch (cause) { if (current === ticket) error.value = apiErrorMessage(cause, '阶段资料加载失败') }
  finally { if (current === ticket) loading.value = false }
}
async function reload() { if (await guard.confirmDiscard()) await load() }
function syncIds() { form.designAttachmentIds = files.design.map(file => file.id); form.codeWalkAttachmentIds = files.codeWalk.map(file => file.id); form.testReportAttachmentIds = files.testReport.map(file => file.id) }
async function upload(event: Event, group: FileGroup) {
  const input = event.target as HTMLInputElement, selected = Array.from(input.files || []), taskId = props.task.id
  input.value = ''
  if (readOnly.value || !selected.length) return
  if (files[group].length + selected.length > 50) { fileError.value = '每类资料最多 50 个附件'; return }
  fileError.value = ''; uploading.value++
  try {
    for (const file of selected) {
      const uploaded = (await uploadAttachment(file)).data.data
      if (disposed || taskId !== props.task.id) return
      files[group].push({ id: String(uploaded.id), fileName: uploaded.fileName, fileSize: uploaded.fileSize, contentType: uploaded.contentType || null })
      syncIds()
    }
  } catch (cause) { fileError.value = apiErrorMessage(cause, '附件上传失败，其他输入已保留') }
  finally { uploading.value-- }
}
function remove(group: FileGroup, id: string) { if (!readOnly.value && !submitting.value) { files[group] = files[group].filter(file => file.id !== id); syncIds() } }
async function openFile(file: AttachmentRef, kind: 'download' | 'preview') {
  if (fileBusy.value) return
  fileBusy.value = true
  fileError.value = ''
  try {
    const id = Number(file.id)
    if (!Number.isSafeInteger(id)) throw new Error('附件标识不受支持')
    if (kind === 'preview') {
      const result = (await getAttachmentPreview(id)).data.data
      preview.url = result.previewUrl; preview.fileName = file.fileName; preview.open = true
    } else {
      const result = (await getAttachmentDownload(id)).data.data
      const url = new URL(result.downloadUrl, window.location.origin)
      if (!['http:', 'https:'].includes(url.protocol)) throw new Error('下载地址无效')
      const response = await fetch(url.href, { credentials: 'omit' })
      if (!response.ok) throw new Error('文件内容不可访问')
      const objectUrl = URL.createObjectURL(await response.blob())
      const link = document.createElement('a')
      link.href = objectUrl; link.download = file.fileName
      document.body.append(link); link.click(); link.remove()
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 30_000)
    }
  } catch (cause) { fileError.value = apiErrorMessage(cause, kind === 'preview' ? '文件预览失败' : '文件下载失败') }
  finally { fileBusy.value = false }
}
async function save() {
  if (submitting.value || readOnly.value || !dirty.value) return
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true; error.value = ''
  try { const result = await developmentApi.saveStages(props.task.id, { ...form }); assign(result); emit('saved', result) }
  catch (cause) { error.value = apiErrorMessage(cause, '阶段资料保存失败，输入已保留') }
  finally { saving.value = false }
}
async function confirmLeave() { const result = await guard.confirmDiscard(); if (result) emit('dirty', false); return result }
defineExpose({ confirmLeave })
watch(dirty, value => emit('dirty', value))
watch(() => [props.task.id, props.task.status, props.task.rowVersion], () => { if (!dirty.value) void load() }, { immediate: true })
void getFilePreviewCapabilities().then(result => { previewEnabled.value = result.data.data.enabled }).catch(() => { previewEnabled.value = false })
onBeforeUnmount(() => { disposed = true; ticket++; emit('dirty', false) })
</script>

<template>
  <section v-loading="loading" class="dev-stage-panel">
    <div class="dev-section-heading"><h3>阶段信息</h3><div class="dev-actions"><span v-if="dirty" class="dev-warning">有未保存修改</span><el-tooltip content="重新载入阶段资料"><el-button :icon="Refresh" circle :disabled="submitting" aria-label="重新载入阶段资料" @click="reload" /></el-tooltip><el-button v-if="!readOnly" type="primary" :loading="saving" :disabled="uploading > 0 || !dirty" @click="save">保存阶段资料</el-button></div></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"><el-button link :disabled="submitting" @click="reload">读取最新资料</el-button></el-alert>
    <el-alert v-if="fileError" :title="fileError" type="error" show-icon :closable="false" />
    <el-form v-if="stage" ref="formRef" :model="form" :rules="rules" label-position="top" class="dev-form">
      <section class="dev-form-section"><h4>需求承接</h4><dl class="dev-facts"><div><dt>来源</dt><dd>{{ task.source?.number || '自主创建' }}</dd></div><div><dt>登记时间</dt><dd>{{ task.createdAt.replace('T', ' ').slice(0, 19) }}</dd></div><div><dt>系统</dt><dd>{{ task.system.name }}</dd></div><div><dt>负责人</dt><dd>{{ task.owner.name }}</dd></div></dl></section>
      <section class="dev-form-section"><div class="dev-section-heading"><h4>开发设计</h4><el-checkbox v-if="stage.pureTest" v-model="form.notApplicableDesign" :disabled="readOnly">不适用</el-checkbox></div>
        <div v-if="!form.notApplicableDesign" class="dev-form-grid"><el-form-item label="计划开始" prop="designPlanStart"><el-date-picker v-model="form.designPlanStart" type="date" value-format="YYYY-MM-DD" :disabled="readOnly" /></el-form-item><el-form-item label="计划结束" prop="designPlanEnd"><el-date-picker v-model="form.designPlanEnd" type="date" value-format="YYYY-MM-DD" :disabled="readOnly" /></el-form-item><el-form-item label="设计文档路径" class="dev-wide"><el-input v-model="form.designDocumentPath" maxlength="2000" :disabled="readOnly" /></el-form-item></div>
        <small v-if="stage.designRegisteredAt" class="dev-muted">资料登记：{{ stage.designRegisteredAt.replace('T', ' ').slice(0, 19) }}</small>
        <div v-if="!form.notApplicableDesign" class="dev-file-group">
          <div class="dev-section-heading"><strong>设计附件</strong><label v-if="!readOnly" class="dev-upload-trigger" :class="{ 'is-disabled': submitting }"><el-icon><Upload /></el-icon>上传附件<input type="file" multiple aria-label="上传设计附件" :disabled="submitting" @change="upload($event, 'design')" /></label></div>
          <div v-for="file in files.design" :key="file.id" class="dev-attachment"><span>{{ file.fileName }}</span><small class="dev-muted">{{ Math.max(1, Math.ceil(file.fileSize / 1024)) }} KB</small><div class="dev-actions"><el-tooltip content="下载附件"><el-button :icon="Download" text circle :aria-label="`下载${file.fileName}`" @click="openFile(file, 'download')" /></el-tooltip><el-tooltip :content="previewEnabled ? '预览附件' : '预览服务不可用'"><el-button :icon="View" text circle :disabled="!previewEnabled" :aria-label="`预览${file.fileName}`" @click="openFile(file, 'preview')" /></el-tooltip><el-tooltip v-if="!readOnly" content="移除当前资料引用"><el-button :icon="Close" text circle :disabled="submitting" :aria-label="`移除${file.fileName}`" @click="remove('design', file.id)" /></el-tooltip></div></div>
          <p v-if="!files.design.length" class="dev-muted">暂无资料</p>
        </div>
      </section>
      <section class="dev-form-section"><div class="dev-section-heading"><h4>开发实施</h4><el-checkbox v-if="stage.pureTest" v-model="form.notApplicableImplementation" :disabled="readOnly">不适用</el-checkbox></div>
        <div v-if="!form.notApplicableImplementation" class="dev-form-grid"><el-form-item label="实际开始" prop="implementationActualStart"><el-date-picker v-model="form.implementationActualStart" type="date" value-format="YYYY-MM-DD" :disabled="readOnly" /></el-form-item><el-form-item label="实际结束" prop="implementationActualEnd"><el-date-picker v-model="form.implementationActualEnd" type="date" value-format="YYYY-MM-DD" :disabled="readOnly" /></el-form-item></div>
        <dl class="dev-duration"><div><dt>计划工作日</dt><dd>{{ stage.duration.plannedDays ?? '-' }}</dd></div><div><dt>实际工作日</dt><dd>{{ stage.duration.actualDays ?? '-' }}</dd></div><div><dt>耗时偏差</dt><dd :class="{ 'dev-danger': (stage.duration.variancePercent || 0) > 0 }">{{ stage.duration.status === 'available' ? `${stage.duration.variancePercent}%` : '暂不可计算' }}</dd></div></dl>
        <small class="dev-muted">{{ stage.duration.calendarLabel }} · {{ stage.duration.calendarVersion.slice(0, 12) }}</small>
      </section>
      <section class="dev-form-section"><h4>单元测试与资料</h4>
        <div class="dev-form-grid"><el-form-item label="测试计划开始"><el-input :model-value="task.testPlanStart || '未排期'" disabled /></el-form-item><el-form-item label="测试计划结束"><el-input :model-value="task.testPlanEnd || '未排期'" disabled /></el-form-item></div>
        <div v-for="group in groups.filter(group => group.key !== 'design')" :key="group.key" class="dev-file-group">
          <div class="dev-section-heading"><strong>{{ group.label }}<span v-if="group.key === 'testReport'" class="dev-muted">（可选）</span></strong><label v-if="!readOnly" class="dev-upload-trigger" :class="{ 'is-disabled': submitting }"><el-icon><Upload /></el-icon>上传附件<input type="file" multiple :aria-label="`上传${group.label}`" :disabled="submitting" @change="upload($event, group.key)" /></label></div>
          <div v-for="file in files[group.key]" :key="file.id" class="dev-attachment"><span>{{ file.fileName }}</span><small class="dev-muted">{{ Math.max(1, Math.ceil(file.fileSize / 1024)) }} KB</small><div class="dev-actions"><el-tooltip content="下载附件"><el-button :icon="Download" text circle :aria-label="`下载${file.fileName}`" @click="openFile(file, 'download')" /></el-tooltip><el-tooltip :content="previewEnabled ? '预览附件' : '预览服务不可用'"><el-button :icon="View" text circle :disabled="!previewEnabled" :aria-label="`预览${file.fileName}`" @click="openFile(file, 'preview')" /></el-tooltip><el-tooltip v-if="!readOnly" content="移除当前资料引用"><el-button :icon="Close" text circle :disabled="submitting" :aria-label="`移除${file.fileName}`" @click="remove(group.key, file.id)" /></el-tooltip></div></div>
          <p v-if="!files[group.key].length" class="dev-muted">暂无资料</p>
        </div>
        <small v-if="stage.testRegisteredAt" class="dev-muted">资料登记：{{ stage.testRegisteredAt.replace('T', ' ').slice(0, 19) }}</small>
      </section>
    </el-form>
    <div v-if="stage && !readOnly" class="dev-stage-footer"><span v-if="dirty" class="dev-warning">有未保存修改</span><el-button type="primary" :loading="saving" :disabled="uploading > 0 || !dirty" @click="save">保存</el-button></div>
    <UiFilePreview v-model="preview.open" :url="preview.url" :file-name="preview.fileName" />
  </section>
</template>
