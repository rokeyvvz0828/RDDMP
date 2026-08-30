<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Delete, DocumentAdd, Loading, Plus } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import { deleteTemporaryAttachment, uploadAttachment } from '../../../api/attachments'
import { apiErrorMessage } from '../../../api/error'
import { listReleaseApplicationAttachments, type ProductionEntryDto, type ReleaseApplicationDto, type ReleaseApplicationWrite, type ReleaseAttachmentInput, type ReleaseWindowDto } from '../../../api/release'
import type { ProjectContextItem } from '../../../types/project-context'
import { useAuthStore } from '../../../stores/auth'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import { releaseSubsystemOptions } from '../release-master-data.mock'

interface DraftDelivery { deliveryUnitId: string; deliveryUnitCode: string; deliveryUnitName: string; artifactType: 'IMAGE' | 'BINARY'; artifactVersion: string }
interface DraftFileMedia { id: string; filePath: string }
interface DraftAttachment { id: number; name: string; category: 'TEST_REPORT' | 'SUPPORTING'; bound: boolean }
interface DraftState {
  emergency: boolean
  windowId?: number
  subsystemId: string
  subsystemCode: string
  subsystemName: string
  contentTypes: Array<'DELIVERY_UNIT' | 'FILE_MEDIA'>
  deliveries: DraftDelivery[]
  fileMedia: DraftFileMedia[]
  requirementCodes: string[]
  emergencyDescription: string
  urgentReason: string
  description: string
  attachments: DraftAttachment[]
}

const props = defineProps<{
  modelValue: boolean
  application?: ReleaseApplicationDto | null
  windows: ReleaseWindowDto[]
  project: ProjectContextItem | null
  currentProduction: ProductionEntryDto[]
  saving: boolean
  saveError?: string
  canSubmit: boolean
  canDeleteAttachment: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [payload: ReleaseApplicationWrite, mode: 'draft' | 'submit', attachments: ReleaseAttachmentInput[]]
  'delete-attachment': [application: ReleaseApplicationDto, attachmentId: number]
}>()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const uploadBusy = ref(false)
const selectedUnitCodes = ref<string[]>([])
const draft = reactive<DraftState>(emptyDraft())
const rules: FormRules = { subsystemCode: [{ required: true, message: '请选择物理子系统', trigger: 'change' }] }
const subsystem = computed(() => releaseSubsystemOptions.find(item => item.id === draft.subsystemId))
const selectedWindow = computed(() => props.windows.find(item => item.id === draft.windowId))
const windowOptions = computed(() => [...props.windows].sort((a, b) => a.productionStart.localeCompare(b.productionStart)))
const estimatedType = computed(() => draft.emergency ? 'EMERGENCY' : selectedWindow.value?.status === 'URGENT' ? 'URGENT' : 'REGULAR')
const requesterDisplay = computed(() => {
  const name = props.application?.requesterName || auth.user?.displayName || auth.user?.username
  const department = props.application?.requesterDepartment || (!props.application ? auth.user?.orgName : undefined)
  return [name, department].filter(Boolean).join(' / ') || '用户信息加载中'
})
const versionLabels = { REGULAR: '常规版本', URGENT: '紧急版本', EMERGENCY: '应急版本' } as const

function emptyDraft(): DraftState {
  return { emergency: false, windowId: undefined, subsystemId: '', subsystemCode: '', subsystemName: '', contentTypes: ['DELIVERY_UNIT'], deliveries: [], fileMedia: [], requirementCodes: [], emergencyDescription: '', urgentReason: '', description: '', attachments: [] }
}
function artifactLabel(value: 'IMAGE' | 'BINARY') { return value === 'IMAGE' ? '镜像' : '二进制' }
function versionTone() { return estimatedType.value === 'EMERGENCY' ? 'danger' : estimatedType.value === 'URGENT' ? 'warning' : 'info' }
function latestVersion(unitCode: string) { return props.currentProduction.find(item => item.subsystemCode === draft.subsystemCode && item.deliveryUnitCode === unitCode) }
function minute(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '' }

async function initialize() {
  const source = props.application
  Object.assign(draft, source ? {
    emergency: source.emergency, windowId: source.windowId, subsystemId: source.subsystemId,
    subsystemCode: source.subsystemCode, subsystemName: source.subsystemName,
    contentTypes: [source.deliveries.length ? 'DELIVERY_UNIT' : null, source.fileMedia?.length ? 'FILE_MEDIA' : null]
      .filter((value): value is 'DELIVERY_UNIT' | 'FILE_MEDIA' => value !== null),
    deliveries: source.deliveries.map(item => ({ deliveryUnitId: item.deliveryUnitId, deliveryUnitCode: item.deliveryUnitCode,
      deliveryUnitName: item.deliveryUnitName, artifactType: item.artifactType, artifactVersion: item.artifactVersion })),
    fileMedia: (source.fileMedia || []).map(item => ({ id: String(item.id), filePath: item.filePath })),
    requirementCodes: [...source.requirementCodes], emergencyDescription: source.emergencyDescription || '',
    urgentReason: source.urgentReason || '', description: source.description || '', attachments: []
  } : emptyDraft())
  selectedUnitCodes.value = draft.deliveries.map(item => item.deliveryUnitCode)
  if (!source && !draft.windowId) draft.windowId = windowOptions.value.find(item => item.regularApplicationSelectable)?.id
  if (source) {
    try {
      const attachments = (await listReleaseApplicationAttachments(source.applicationCode)).data.data
      draft.attachments = attachments.map(item => ({ id: item.attachmentId, name: item.fileName, category: item.category, bound: true }))
    } catch (error) {
      ElMessage.error(apiErrorMessage(error, '申请附件加载失败'))
    }
  }
  await nextTick()
  formRef.value?.clearValidate()
}
watch(() => props.modelValue, open => { if (open) void initialize() }, { immediate: true })
watch(() => draft.emergency, emergency => {
  if (emergency) { draft.windowId = undefined; draft.requirementCodes = [] }
  else if (!draft.windowId) draft.windowId = windowOptions.value.find(item => item.regularApplicationSelectable)?.id
})

function changeSubsystem(id: string) {
  const option = releaseSubsystemOptions.find(item => item.id === id)
  draft.subsystemId = option?.id || ''
  draft.subsystemCode = option?.code || ''
  draft.subsystemName = option?.name || ''
  draft.deliveries = []
  draft.fileMedia = []
  selectedUnitCodes.value = []
}
function addFileMedia() { draft.fileMedia.push({ id: `${Date.now()}-${draft.fileMedia.length}`, filePath: '' }) }
function removeFileMedia(id: string) { draft.fileMedia = draft.fileMedia.filter(item => item.id !== id) }
function toggleContentTypes(values: Array<'DELIVERY_UNIT' | 'FILE_MEDIA'>) {
  if (!values.includes('DELIVERY_UNIT')) { draft.deliveries = []; selectedUnitCodes.value = [] }
  if (!values.includes('FILE_MEDIA')) draft.fileMedia = []
  if (values.includes('FILE_MEDIA') && !draft.fileMedia.length) addFileMedia()
}
function syncUnits(codes: string[]) {
  const previous = new Map(draft.deliveries.map(item => [item.deliveryUnitCode, item]))
  draft.deliveries = codes.map(code => {
    const option = subsystem.value?.units.find(item => item.code === code)!
    return previous.get(code) || { deliveryUnitId: option.id, deliveryUnitCode: option.code,
      deliveryUnitName: option.name, artifactType: option.artifactType as 'IMAGE' | 'BINARY', artifactVersion: '' }
  })
}
function removeUnit(code: string) {
  selectedUnitCodes.value = selectedUnitCodes.value.filter(item => item !== code)
  syncUnits(selectedUnitCodes.value)
}

async function selectAttachment(file: UploadFile) {
  if (!file.raw) return
  if (draft.attachments.length >= 10) { ElMessage.warning('单张申请最多添加 10 个附件'); return }
  uploadBusy.value = true
  try {
    const item = (await uploadAttachment(file.raw)).data.data
    draft.attachments.push({ id: item.id, name: item.fileName,
      category: draft.emergency ? 'TEST_REPORT' : 'SUPPORTING', bound: false })
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '附件上传失败'))
  } finally { uploadBusy.value = false }
}
async function removeAttachment(item: DraftAttachment) {
  if (item.bound && props.application) {
    emit('delete-attachment', props.application, item.id)
    return
  }
  try { await deleteTemporaryAttachment(item.id); draft.attachments = draft.attachments.filter(file => file.id !== item.id) }
  catch (error) { ElMessage.error(apiErrorMessage(error, '附件删除失败')) }
}

function validateBusiness(mode: 'draft' | 'submit') {
  if (!draft.emergency && !selectedWindow.value?.regularApplicationSelectable) { ElMessage.warning(selectedWindow.value?.unavailableReason || '请选择可申报的投产窗口'); return false }
  if (!draft.contentTypes.length) { ElMessage.warning('至少选择一种交付内容'); return false }
  if (!draft.deliveries.length && !draft.fileMedia.length) { ElMessage.warning('至少添加一个交付单元或文件介质'); return false }
  if (draft.deliveries.some(item => !item.artifactVersion.trim() || /\s/.test(item.artifactVersion))) { ElMessage.warning('每个交付单元必须填写不含空格的制品版本'); return false }
  const paths = draft.fileMedia.map(item => item.filePath.trim())
  if (paths.some(path => !path)) { ElMessage.warning('每条文件介质都必须填写文件路径'); return false }
  if (draft.fileMedia.some(item => /[\u0000-\u001f\u007f]/.test(item.filePath))) { ElMessage.warning('文件路径不能包含控制字符'); return false }
  if (paths.some(path => path.length > 1024)) { ElMessage.warning('文件路径长度不能超过 1024 个字符'); return false }
  if (new Set(paths).size !== paths.length) { ElMessage.warning('同一申请中文件路径不能重复'); return false }
  if (!draft.emergency && !draft.requirementCodes.filter(Boolean).length) { ElMessage.warning('至少关联一个需求编号'); return false }
  if (draft.emergency && !draft.emergencyDescription.trim()) { ElMessage.warning('应急版本必须填写测试缺陷及应急情况说明'); return false }
  if (estimatedType.value === 'URGENT' && !draft.urgentReason.trim()) { ElMessage.warning('紧急版本必须填写紧急申请原因'); return false }
  const temporary = draft.attachments.filter(item => !item.bound)
  if (mode === 'draft' && temporary.length) { ElMessage.warning('附件在提交审批时绑定，请移除本次附件或直接提交审批'); return false }
  if (mode === 'submit' && draft.emergency && !draft.attachments.some(item => item.category === 'TEST_REPORT')) { ElMessage.warning('应急版本必须补充测试报告附件'); return false }
  return true
}
async function save(mode: 'draft' | 'submit') {
  if (!await formRef.value?.validate().catch(() => false) || !validateBusiness(mode)) return
  emit('save', {
    emergency: draft.emergency, windowId: draft.emergency ? undefined : draft.windowId,
    projectId: props.project?.ref || '', projectCode: props.project?.ref || '', projectName: props.project?.name || '',
    subsystemId: draft.subsystemId, subsystemCode: draft.subsystemCode, subsystemName: draft.subsystemName,
    deliveries: draft.deliveries.map(item => ({ ...item, artifactVersion: item.artifactVersion.trim() })),
    fileMedia: draft.fileMedia.map(item => ({ filePath: item.filePath.trim() })),
    requirementCodes: draft.emergency ? [] : draft.requirementCodes.map(item => item.trim()).filter(Boolean),
    emergencyDescription: draft.emergency ? draft.emergencyDescription.trim() : undefined,
    urgentReason: estimatedType.value === 'URGENT' ? draft.urgentReason.trim() : undefined,
    description: draft.description.trim() || undefined
  }, mode, draft.attachments.filter(item => !item.bound).map(item => ({ attachmentId: item.id, category: item.category })))
}
</script>

<template>
  <el-drawer :model-value="modelValue" :title="application ? '编辑版本申请' : '新建版本申请'" size="min(860px, 96vw)" class="release-application-drawer" destroy-on-close @update:model-value="emit('update:modelValue', $event)">
    <div class="release-application-drawer__body"><el-form ref="formRef" :model="draft" :rules="rules" label-position="top" class="release-application-form">
      <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" show-icon />
      <section class="release-form-section"><header><span>01</span><div><strong>申请基础</strong></div></header>
        <el-form-item label="是否应急版本"><el-radio-group v-model="draft.emergency"><el-radio-button :value="false">否</el-radio-button><el-radio-button :value="true">是</el-radio-button></el-radio-group></el-form-item>
        <div class="release-form-grid"><el-form-item label="所属项目"><el-input :model-value="project?.name" disabled /></el-form-item><el-form-item label="申请人"><el-input :model-value="requesterDisplay" disabled /></el-form-item>
          <el-form-item v-if="!draft.emergency" label="投产窗口" required><el-select v-model="draft.windowId" placeholder="选择投产窗口" popper-class="release-window-select-popper"><el-option v-for="item in windowOptions" :key="item.id" :value="item.id" :label="`${item.windowCode} · ${item.windowName}`" :disabled="!item.regularApplicationSelectable"><div class="release-window-option"><span><strong>{{ item.windowCode }} · {{ item.windowName }}</strong><small>{{ minute(item.declarationStart) }} 至 {{ minute(item.declarationEnd) }} / {{ item.statusLabel }}</small></span><el-tag :type="item.regularApplicationSelectable ? 'success' : 'info'" size="small">{{ item.regularApplicationSelectable ? '可选择' : item.unavailableReason }}</el-tag></div></el-option></el-select></el-form-item>
          <el-form-item v-if="!draft.emergency" label="版本类型"><div class="release-scenario-preview"><UiStatusTag :value="versionLabels[estimatedType]" :tone="versionTone()" /></div></el-form-item>
        </div>
      </section>

      <section class="release-form-section"><header><span>02</span><div><strong>制品登记</strong></div></header><div class="release-form-grid"><el-form-item label="物理子系统" prop="subsystemCode" class="is-wide"><el-select :model-value="draft.subsystemId" filterable placeholder="选择物理子系统" @update:model-value="changeSubsystem"><el-option v-for="item in releaseSubsystemOptions" :key="item.id" :value="item.id" :label="`${item.code} · ${item.name}`" /></el-select></el-form-item><el-form-item label="交付内容" class="is-wide"><el-checkbox-group v-model="draft.contentTypes" @change="toggleContentTypes"><el-checkbox value="DELIVERY_UNIT">交付单元</el-checkbox><el-checkbox value="FILE_MEDIA">文件介质</el-checkbox></el-checkbox-group></el-form-item><el-form-item v-if="draft.contentTypes.includes('DELIVERY_UNIT')" label="交付单元" class="is-wide"><el-select v-model="selectedUnitCodes" multiple collapse-tags collapse-tags-tooltip :disabled="!subsystem" placeholder="选择交付单元" @change="syncUnits"><el-option v-for="item in subsystem?.units || []" :key="item.code" :value="item.code" :label="`${item.code} · ${item.name}`" /></el-select></el-form-item></div>
        <div v-if="draft.deliveries.length" class="release-unit-editor"><article v-for="unit in draft.deliveries" :key="unit.deliveryUnitCode"><div><strong>{{ unit.deliveryUnitName }}</strong><small>{{ unit.deliveryUnitCode }} · {{ artifactLabel(unit.artifactType) }}</small></div><div class="release-current-version"><span>生产版本</span><strong>{{ latestVersion(unit.deliveryUnitCode)?.artifactVersion || '暂无' }}</strong><small>{{ minute(latestVersion(unit.deliveryUnitCode)?.productionAt) || '尚无成功投产记录' }}</small></div><el-input v-model="unit.artifactVersion" placeholder="本次制品版本，不允许空格" /><el-tooltip content="移除交付单元"><el-button circle plain type="danger" aria-label="移除交付单元" @click="removeUnit(unit.deliveryUnitCode)"><el-icon><Delete /></el-icon></el-button></el-tooltip></article></div>
        <div v-if="draft.contentTypes.includes('FILE_MEDIA')" class="release-file-media-editor">
          <div class="release-file-media-editor__head">
            <strong>文件介质</strong>
            <el-button class="release-file-media-editor__add" type="primary" link size="small" @click="addFileMedia"><el-icon><Plus /></el-icon>添加路径</el-button>
          </div>
          <div class="release-file-media-editor__list">
            <article v-for="(file, index) in draft.fileMedia" :key="file.id">
              <span class="release-file-media-editor__index">{{ index + 1 }}</span>
              <el-input class="release-file-media-editor__path" v-model="file.filePath" maxlength="1024" placeholder="填写文件路径" />
              <el-tooltip content="移除文件路径"><el-button class="release-file-media-editor__remove" circle plain type="danger" aria-label="移除文件路径" @click="removeFileMedia(file.id)"><el-icon><Delete /></el-icon></el-button></el-tooltip>
            </article>
          </div>
        </div>
      </section>

      <section class="release-form-section"><header><span>03</span><div><strong>关联与材料</strong></div></header><div class="release-form-grid"><el-form-item v-if="!draft.emergency" label="需求编号" required class="is-wide"><el-select v-model="draft.requirementCodes" multiple filterable allow-create default-first-option placeholder="输入需求编号后回车" /></el-form-item><el-form-item v-if="draft.emergency" label="测试缺陷及应急情况说明" required class="is-wide"><el-input v-model="draft.emergencyDescription" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item><el-form-item v-if="estimatedType === 'URGENT'" label="紧急申请原因" required class="is-wide"><el-input v-model="draft.urgentReason" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item><el-form-item label="申请说明" class="is-wide"><el-input v-model="draft.description" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item></div>
        <div class="release-attachment-editor"><div><strong>{{ draft.emergency ? '测试报告' : '说明附件' }}</strong><small>{{ draft.emergency ? '提交审批时必填' : '选填' }}</small></div><el-upload :auto-upload="false" :show-file-list="false" :on-change="selectAttachment" :disabled="uploadBusy"><el-button :disabled="uploadBusy"><el-icon><Loading v-if="uploadBusy" /><DocumentAdd v-else /></el-icon>{{ uploadBusy ? '上传中' : '上传附件' }}</el-button></el-upload></div><div v-if="draft.attachments.length" class="release-attachment-chips"><el-tag v-for="item in draft.attachments" :key="item.id" :closable="!item.bound || canDeleteAttachment" @close="removeAttachment(item)">{{ item.name }}{{ item.bound ? ' · 已绑定' : '' }}</el-tag></div>
      </section>
    </el-form></div>
    <template #footer><div class="release-drawer-actions"><el-button :disabled="saving" @click="emit('update:modelValue', false)">取消</el-button><el-button :loading="saving" @click="save('draft')">保存草稿</el-button><el-button v-if="canSubmit" type="primary" :loading="saving" @click="save('submit')">提交审批</el-button></div></template>
  </el-drawer>
</template>
