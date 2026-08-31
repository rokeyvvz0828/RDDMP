<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, MagicStick, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  createSubsystemChangeApplication,
  getLogicalSubsystem,
  getPhysicalSubsystem,
  getSubsystemChangeApplication,
  loadLogicalSubsystemOptions,
  loadOrganizationOptions,
  loadParameterOptions,
  loadUserOptions,
  requestSubsystemSuggestions,
  submitSubsystemChangeApplication,
  updateSubsystemChangeApplication
} from './api'
import SubsystemChangePhysicalCard from './components/SubsystemChangePhysicalCard.vue'
import type {
  LogicalDraftInput,
  LogicalSubsystemOption,
  OrganizationOption,
  ParameterOption,
  PhysicalDraftInput,
  SubsystemActionType,
  SubsystemChangeApplicationDetail,
  SubsystemSuggestion,
  SubsystemTargetKind,
  UserOption
} from './types'
import {
  actionTypeLabels,
  cancelled,
  formatLogicalNumber,
  httpStatus,
  normalizeText,
  targetKindLabels
} from './utils'
import './architecture.css'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const applicationId = ref<number | null>(positiveNumber(route.params.id))
const targetKind = ref<SubsystemTargetKind>(route.query.targetKind === 'PHYSICAL' ? 'PHYSICAL' : 'LOGICAL')
const actionType = ref<SubsystemActionType>(readAction(route.query.actionType))
const targetId = ref<number | null>(positiveNumber(route.query.targetId))
/** 物理工单发布记录的当前归属逻辑；REPLACE 需排除该选项并留空待选。 */
const sourceLogicalSubsystemId = ref<number | null>(null)
const reason = ref('')
const logicalDraft = ref<LogicalDraftInput>(emptyLogicalDraft())
const physicalDrafts = ref<PhysicalDraftInput[]>([])
const detail = ref<SubsystemChangeApplicationDetail | null>(null)
const organizations = ref<OrganizationOption[]>([])
const users = ref<UserOption[]>([])
const logicalSubsystems = ref<LogicalSubsystemOption[]>([])
const deploymentPlatforms = ref<ParameterOption[]>([])
const systemTypes = ref<ParameterOption[]>([])
const ownerships = ref<ParameterOption[]>([])
const runtimes = ref<ParameterOption[]>([])
const levels = ref<ParameterOption[]>([])
const frameworks = ref<ParameterOption[]>([])
const disasterRecoveryModes = ref<ParameterOption[]>([])
const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const submitting = ref(false)
const baseline = ref('')

const canApply = computed(() => auth.hasPermission('architecture:apply') || auth.hasPermission('architecture:manage'))
const isExisting = computed(() => Boolean(applicationId.value))
const isLogicalCreate = computed(() => targetKind.value === 'LOGICAL' && actionType.value === 'CREATE')
const title = computed(() => isExisting.value
  ? `编辑工单 #${applicationId.value}`
  : `${actionTypeLabels[actionType.value]}${targetKindLabels[targetKind.value]}申请`)
const numberLabel = computed(() => formatLogicalNumber(detail.value?.logicalDraft?.reservedNumberSequence))
const serialized = computed(() => JSON.stringify({
  targetKind: targetKind.value,
  actionType: actionType.value,
  targetId: targetId.value,
  reason: reason.value,
  logicalDraft: logicalDraft.value,
  physicalDrafts: physicalDrafts.value
}))
const dirty = computed(() => !loading.value && serialized.value !== baseline.value)
const ownsApplication = computed(() => !detail.value || detail.value.application.applicantId === auth.user?.id)

function positiveNumber(value: unknown) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function readAction(value: unknown): SubsystemActionType {
  const candidate = String(value || 'CREATE').toUpperCase()
  return ['CREATE', 'UPDATE', 'OFFLINE', 'REACTIVATE', 'VOID', 'REPLACE'].includes(candidate)
    ? candidate as SubsystemActionType
    : 'CREATE'
}

function emptyLogicalDraft(): LogicalDraftInput {
  return {
    shortName: '',
    name: '',
    businessOrgId: null,
    deploymentPlatformCode: null,
    systemTypeCode: null,
    systemOwnershipCode: null,
    contactUserId: null,
    description: null,
    remark: null,
    sortNo: 0,
    sourceRowVersion: null
  }
}

function emptyPhysicalDraft(lineNo: number, logicalId: number | null = null): PhysicalDraftInput {
  return {
    lineNo,
    targetLogicalSubsystemId: logicalId,
    shortName: '',
    name: '',
    englishName: null,
    businessGroupName: null,
    businessContinuityLevel: null,
    collectedSystemLevel: null,
    deploymentPlatform: null,
    disasterRecoveryMode: null,
    responsibleTeamOrgId: null,
    responsibleTeamNameSnapshot: '',
    runtimeCode: null,
    systemLevelCode: null,
    developmentFrameworkCode: null,
    ownerUserId: null,
    description: null,
    remark: null,
    sourceRowVersion: null
  }
}

function logicalInput(source: NonNullable<SubsystemChangeApplicationDetail['logicalDraft']>): LogicalDraftInput {
  return {
    shortName: source.shortName,
    name: source.name,
    businessOrgId: source.businessOrgId,
    deploymentPlatformCode: source.deploymentPlatformCode,
    systemTypeCode: source.systemTypeCode,
    systemOwnershipCode: source.systemOwnershipCode,
    contactUserId: source.contactUserId,
    description: source.description,
    remark: source.remark,
    sortNo: source.sortNo,
    sourceRowVersion: source.sourceRowVersion
  }
}

function physicalInput(source: SubsystemChangeApplicationDetail['physicalDrafts'][number]): PhysicalDraftInput {
  return {
    lineNo: source.lineNo,
    targetLogicalSubsystemId: source.targetLogicalSubsystemId,
    shortName: source.shortName,
    name: source.name,
    englishName: source.englishName,
    businessGroupName: source.businessGroupName,
    businessContinuityLevel: source.businessContinuityLevel,
    collectedSystemLevel: source.collectedSystemLevel,
    deploymentPlatform: source.deploymentPlatform,
    disasterRecoveryMode: source.disasterRecoveryMode,
    responsibleTeamOrgId: source.responsibleTeamOrgId,
    responsibleTeamNameSnapshot: source.responsibleTeamNameSnapshot,
    runtimeCode: source.runtimeCode,
    systemLevelCode: source.systemLevelCode,
    developmentFrameworkCode: source.developmentFrameworkCode,
    ownerUserId: source.ownerUserId,
    description: source.description,
    remark: source.remark,
    sourceRowVersion: source.sourceRowVersion
  }
}

function hydrate(application: SubsystemChangeApplicationDetail) {
  detail.value = application
  applicationId.value = application.application.id
  targetKind.value = application.application.targetKind
  actionType.value = application.application.actionType
  targetId.value = application.application.targetId
  sourceLogicalSubsystemId.value = null
  reason.value = application.application.reason
  logicalDraft.value = application.logicalDraft ? logicalInput(application.logicalDraft) : emptyLogicalDraft()
  physicalDrafts.value = application.physicalDrafts.map(physicalInput)
  baseline.value = serialized.value
}

async function loadReferences() {
  const results = await Promise.allSettled([
    loadOrganizationOptions('logical-subsystem', '', 100),
    loadUserOptions('logical-subsystem', '', 100),
    loadLogicalSubsystemOptions('', 100),
    loadParameterOptions('logical-subsystem', 'ARCH_DEPLOYMENT_PLATFORM'),
    loadParameterOptions('logical-subsystem', 'ARCH_SYSTEM_TYPE'),
    loadParameterOptions('logical-subsystem', 'ARCH_SYSTEM_OWNERSHIP'),
    loadParameterOptions('physical-subsystem', 'ARCH_RUNTIME'),
    loadParameterOptions('physical-subsystem', 'ARCH_SYSTEM_LEVEL'),
    loadParameterOptions('physical-subsystem', 'ARCH_DEVELOPMENT_FRAMEWORK'),
    loadParameterOptions('physical-subsystem', 'ARCH_DISASTER_RECOVERY_MODE')
  ])
  if (results[0].status === 'fulfilled') organizations.value = results[0].value
  if (results[1].status === 'fulfilled') users.value = results[1].value
  if (results[2].status === 'fulfilled') logicalSubsystems.value = results[2].value
  if (results[3].status === 'fulfilled') deploymentPlatforms.value = results[3].value
  if (results[4].status === 'fulfilled') systemTypes.value = results[4].value
  if (results[5].status === 'fulfilled') ownerships.value = results[5].value
  if (results[6].status === 'fulfilled') runtimes.value = results[6].value
  if (results[7].status === 'fulfilled') levels.value = results[7].value
  if (results[8].status === 'fulfilled') frameworks.value = results[8].value
  if (results[9].status === 'fulfilled') disasterRecoveryModes.value = results[9].value
  if (results.some(item => item.status === 'rejected')) ElMessage.warning('部分选项加载失败，仍可保留当前草稿并稍后重试')
}

async function initializeFromPublished() {
  if (!targetId.value || actionType.value === 'CREATE') {
    if (targetKind.value === 'PHYSICAL') physicalDrafts.value = [emptyPhysicalDraft(1)]
    return
  }
  if (targetKind.value === 'LOGICAL') {
    const source = await getLogicalSubsystem(targetId.value)
    logicalDraft.value = {
      shortName: source.shortName,
      name: source.name,
      businessOrgId: source.businessOrgId,
      deploymentPlatformCode: source.deploymentPlatformCode,
      systemTypeCode: source.systemTypeCode,
      systemOwnershipCode: source.systemOwnershipCode,
      contactUserId: source.contactUserId,
      description: source.description,
      remark: source.remark,
      sortNo: source.sortNo,
      sourceRowVersion: source.rowVersion
    }
    return
  }
  const source = await getPhysicalSubsystem(targetId.value)
  sourceLogicalSubsystemId.value = source.logicalSubsystemId
  physicalDrafts.value = [{
    lineNo: 1,
    targetLogicalSubsystemId: actionType.value === 'REPLACE' ? null : source.logicalSubsystemId,
    shortName: source.shortName,
    name: source.name,
    englishName: source.englishName,
    businessGroupName: source.businessGroupName,
    businessContinuityLevel: source.businessContinuityLevel,
    collectedSystemLevel: source.collectedSystemLevel,
    deploymentPlatform: source.deploymentPlatform,
    disasterRecoveryMode: source.disasterRecoveryMode,
    responsibleTeamOrgId: source.responsibleTeamValid ? source.responsibleTeamOrgId : null,
    responsibleTeamNameSnapshot: source.responsibleTeamValid ? source.responsibleTeamDisplayName : '',
    runtimeCode: source.runtimeCode,
    systemLevelCode: source.systemLevelCode,
    developmentFrameworkCode: source.developmentFrameworkCode,
    ownerUserId: source.ownerUserId,
    description: source.description,
    remark: source.remark,
    sourceRowVersion: source.rowVersion
  }]
}

async function initialize() {
  loading.value = true
  loadError.value = ''
  try {
    await loadReferences()
    if (applicationId.value) hydrate(await getSubsystemChangeApplication(applicationId.value))
    else {
      await initializeFromPublished()
      baseline.value = serialized.value
    }
  } catch (error) {
    loadError.value = apiErrorMessage(error, '工单表单加载失败')
  } finally {
    loading.value = false
  }
}

function changeTargetKind(value: SubsystemTargetKind) {
  if (isExisting.value || targetId.value) return
  targetKind.value = value
  sourceLogicalSubsystemId.value = null
  logicalDraft.value = emptyLogicalDraft()
  physicalDrafts.value = value === 'PHYSICAL' ? [emptyPhysicalDraft(1)] : []
}

function addPhysicalDraft() {
  physicalDrafts.value.push(emptyPhysicalDraft(physicalDrafts.value.length + 1))
}

function removePhysicalDraft(index: number) {
  physicalDrafts.value.splice(index, 1)
  physicalDrafts.value.forEach((item, position) => { item.lineNo = position + 1 })
}

function normalizeDrafts() {
  reason.value = reason.value.trim()
  logicalDraft.value = {
    ...logicalDraft.value,
    shortName: logicalDraft.value.shortName.trim(),
    name: logicalDraft.value.name.trim(),
    description: normalizeText(logicalDraft.value.description),
    remark: normalizeText(logicalDraft.value.remark)
  }
  physicalDrafts.value = physicalDrafts.value.map((item, index) => ({
    ...item,
    lineNo: index + 1,
    shortName: item.shortName.trim(),
    name: item.name.trim(),
    englishName: normalizeText(item.englishName),
    businessGroupName: normalizeText(item.businessGroupName),
    businessContinuityLevel: normalizeText(item.businessContinuityLevel),
    collectedSystemLevel: normalizeText(item.collectedSystemLevel),
    deploymentPlatform: normalizeText(item.deploymentPlatform),
    disasterRecoveryMode: normalizeText(item.disasterRecoveryMode),
    responsibleTeamNameSnapshot: item.responsibleTeamNameSnapshot.trim(),
    description: normalizeText(item.description),
    remark: normalizeText(item.remark)
  }))
}

function validate() {
  normalizeDrafts()
  if (!reason.value) return '请填写申请原因'
  if (targetKind.value === 'LOGICAL') {
    const draft = logicalDraft.value
    if (!draft.shortName || !draft.name) return '请完整填写逻辑子系统简称和名称'
    if (!draft.businessOrgId) return '请选择逻辑子系统所属事业群'
    if (!draft.contactUserId) return '请选择逻辑子系统联系人'
  }
  if (targetKind.value === 'PHYSICAL' && physicalDrafts.value.length !== 1) return '物理工单必须且只能包含一个物理子系统草稿'
  for (const [index, draft] of physicalDrafts.value.entries()) {
    if (targetKind.value === 'PHYSICAL' && !draft.targetLogicalSubsystemId) {
      return actionType.value === 'REPLACE' ? '请选择与当前归属不同的新逻辑子系统' : '请选择物理子系统所属逻辑子系统'
    }
    if (!draft.shortName || !draft.name) return `请完整填写第 ${index + 1} 个物理子系统的简称和名称`
    if (!draft.responsibleTeamOrgId || !draft.responsibleTeamNameSnapshot) return `请选择第 ${index + 1} 个物理子系统的负责团队`
  }
  return ''
}

async function saveDraft(showSuccess = true) {
  const validation = validate()
  if (validation) {
    ElMessage.warning(validation)
    return null
  }
  if (saving.value || submitting.value) return null
  saving.value = true
  try {
    const saved = applicationId.value && detail.value
      ? await updateSubsystemChangeApplication(applicationId.value, {
          rowVersion: detail.value.application.rowVersion,
          reason: reason.value,
          logicalDraft: targetKind.value === 'LOGICAL' ? logicalDraft.value : null,
          physicalDrafts: physicalDrafts.value
        })
      : await createSubsystemChangeApplication({
          targetKind: targetKind.value,
          actionType: actionType.value,
          targetId: targetId.value,
          reason: reason.value,
          logicalDraft: targetKind.value === 'LOGICAL' ? logicalDraft.value : null,
          physicalDrafts: targetKind.value === 'LOGICAL' ? physicalDrafts.value : [],
          physicalDraft: targetKind.value === 'PHYSICAL' ? physicalDrafts.value[0] : null
        })
    hydrate(saved)
    if (route.name !== 'architecture-subsystem-change-application-edit') {
      await router.replace({ name: 'architecture-subsystem-change-application-edit', params: { id: saved.application.id } })
    }
    if (showSuccess) ElMessage.success('草稿已保存')
    return saved
  } catch (error) {
    const message = apiErrorMessage(error, '草稿保存失败')
    ElMessage.error(httpStatus(error) === 409 ? `${message}；当前输入已保留，请刷新目标版本后重试` : message)
    return null
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (submitting.value) return
  const saved = await saveDraft(false)
  if (!saved) return
  submitting.value = true
  try {
    const submitted = await submitSubsystemChangeApplication(saved.application.id, saved.application.rowVersion)
    hydrate(submitted)
    ElMessage.success('工单已提交审批')
    await router.replace({ name: 'architecture-subsystem-change-application-detail', params: { id: submitted.application.id } })
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '提交审批失败；草稿和已保留编号保持不变'))
  } finally {
    submitting.value = false
  }
}

function currentSuggestionFields() {
  const fields: Record<string, string> = { reason: reason.value }
  if (targetKind.value === 'LOGICAL') {
    fields.shortName = logicalDraft.value.shortName
    fields.name = logicalDraft.value.name
    fields.description = logicalDraft.value.description || ''
  } else {
    fields.shortName = physicalDrafts.value[0]?.shortName || ''
    fields.name = physicalDrafts.value[0]?.name || ''
    fields.englishName = physicalDrafts.value[0]?.englishName || ''
  }
  return fields
}

function applySuggestion(suggestion: SubsystemSuggestion) {
  if (suggestion.field === 'reason') reason.value = suggestion.value
  else if (targetKind.value === 'LOGICAL' && suggestion.field in logicalDraft.value) {
    const key = suggestion.field as 'shortName' | 'name' | 'description' | 'remark'
    if (['shortName', 'name', 'description', 'remark'].includes(key)) logicalDraft.value[key] = suggestion.value
  } else if (targetKind.value === 'PHYSICAL' && physicalDrafts.value[0]) {
    const key = suggestion.field as 'shortName' | 'name' | 'englishName' | 'description' | 'remark'
    if (['shortName', 'name', 'englishName', 'description', 'remark'].includes(key)) physicalDrafts.value[0][key] = suggestion.value
  }
}

async function requestSuggestions() {
  try {
    const suggestions = await requestSubsystemSuggestions(currentSuggestionFields())
    if (!suggestions.length) {
      ElMessage.info('当前建议策略未返回候选值；系统未调用真实 AI')
      return
    }
    for (const suggestion of suggestions) {
      try {
        await ElMessageBox.confirm(
          `${suggestion.explanation}\n\n候选值：${suggestion.value}`,
          `采用 ${suggestion.field} 建议？`,
          { confirmButtonText: '采用此建议', cancelButtonText: '跳过', type: 'info' }
        )
        applySuggestion(suggestion)
      } catch (error) {
        if (!cancelled(error)) throw error
      }
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '建议加载失败'))
  }
}

function physicalNumberLabel(draft: PhysicalDraftInput) {
  const slot = detail.value?.physicalDrafts.find(item => item.lineNo === draft.lineNo)?.reservedNumberSlot
  if (!slot) return '待生成'
  const logicalSequence = detail.value?.logicalDraft?.reservedNumberSequence
  if (logicalSequence) return `W${String(logicalSequence).padStart(4, '0')}${slot}`
  const logicalCode = logicalSubsystems.value.find(item => item.id === draft.targetLogicalSubsystemId)?.code
  return logicalCode?.startsWith('A') ? `W${logicalCode.slice(1)}${slot}` : `已保留槽位 ${slot}`
}

async function back() {
  await router.push({ name: 'architecture-subsystem-change-applications' })
}

onBeforeRouteLeave(async () => {
  if (!dirty.value || saving.value || submitting.value) return true
  try {
    await ElMessageBox.confirm('当前工单有未保存修改，离开后将丢失。', '离开表单', {
      type: 'warning',
      confirmButtonText: '放弃并离开',
      cancelButtonText: '继续编辑'
    })
    return true
  } catch {
    return false
  }
})

onMounted(() => { void initialize() })
</script>

<template>
  <main class="architecture-page architecture-change-page">
    <UiPageHeader :title="title" description="编号在首次提交时由系统确定性分配；保存草稿不会占用正式编号。">
      <template #actions>
        <el-button @click="back"><el-icon><ArrowLeft /></el-icon>返回工单列表</el-button>
      </template>
    </UiPageHeader>

    <section v-if="!canApply" class="architecture-state-panel">
      <el-result icon="warning" title="暂无申请权限" sub-title="需要 architecture:apply 或 architecture:manage 权限。" />
    </section>
    <section v-else-if="loadError" class="architecture-state-panel">
      <el-result icon="error" title="工单表单加载失败" :sub-title="loadError">
        <template #extra><el-button type="primary" @click="initialize">重新加载</el-button></template>
      </el-result>
    </section>
    <section v-else-if="!ownsApplication" class="architecture-state-panel">
      <el-result icon="warning" title="只能编辑本人申请" sub-title="管理权限可查看和审批全部申请，但不能修改他人草稿。" />
    </section>
    <div v-else v-loading="loading" class="architecture-change-form-shell">
      <el-alert
        v-if="detail?.application.status === 'RETURNED'"
        type="warning"
        :closable="false"
        show-icon
        title="工单已退回，可修改草稿后重新提交；原保留编号保持不变。"
      />

      <section class="architecture-change-section">
        <div class="architecture-change-section__heading">
          <div><span>APPLICATION</span><h2>申请信息</h2></div>
          <el-button :disabled="loading || saving || submitting" @click="requestSuggestions"><el-icon><MagicStick /></el-icon>获取本地建议</el-button>
        </div>
        <el-form label-position="top">
          <div class="architecture-form-grid">
            <el-form-item label="申请对象">
              <el-segmented
                :model-value="targetKind"
                :disabled="isExisting || Boolean(targetId)"
                :options="[{ label: '逻辑子系统', value: 'LOGICAL' }, { label: '物理子系统', value: 'PHYSICAL' }]"
                @change="changeTargetKind($event as SubsystemTargetKind)"
              />
            </el-form-item>
            <el-form-item label="变更动作"><el-input :model-value="actionTypeLabels[actionType]" disabled /></el-form-item>
            <el-form-item label="申请原因" required class="is-wide">
              <el-input v-model="reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="说明业务目的、影响和期望结果" />
            </el-form-item>
          </div>
        </el-form>
        <p class="architecture-change-form__hint">建议接口当前只预留本地策略，不调用真实 AI；候选值必须由你显式采用。</p>
      </section>

      <section v-if="targetKind === 'LOGICAL'" class="architecture-change-section">
        <div class="architecture-change-section__heading">
          <div><span>LOGICAL SUBSYSTEM</span><h2>逻辑子系统草稿</h2><small>系统编号：{{ numberLabel }}</small></div>
        </div>
        <el-form label-position="top">
          <div class="architecture-form-grid">
            <el-form-item label="系统简称" required><el-input v-model="logicalDraft.shortName" maxlength="100" /></el-form-item>
            <el-form-item label="系统名称" required><el-input v-model="logicalDraft.name" maxlength="200" /></el-form-item>
            <el-form-item label="所属事业群" required>
              <el-select v-model="logicalDraft.businessOrgId" filterable placeholder="选择事业群">
                <el-option v-for="item in organizations" :key="item.id" :label="item.pathLabel" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="联系人" required>
              <el-select v-model="logicalDraft.contactUserId" filterable placeholder="选择联系人">
                <el-option v-for="item in users" :key="item.id" :label="`${item.displayName}（${item.username}）`" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="部署平台"><el-select v-model="logicalDraft.deploymentPlatformCode" clearable><el-option v-for="item in deploymentPlatforms" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item>
            <el-form-item label="系统类型"><el-select v-model="logicalDraft.systemTypeCode" clearable><el-option v-for="item in systemTypes" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item>
            <el-form-item label="系统归属"><el-select v-model="logicalDraft.systemOwnershipCode" clearable><el-option v-for="item in ownerships" :key="item.code" :label="item.label" :value="item.code" /></el-select></el-form-item>
            <el-form-item label="排序号"><el-input-number v-model="logicalDraft.sortNo" :min="0" :max="999999" /></el-form-item>
            <el-form-item label="系统描述" class="is-wide"><el-input v-model="logicalDraft.description" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
            <el-form-item label="备注" class="is-wide"><el-input v-model="logicalDraft.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item>
          </div>
        </el-form>
      </section>

      <section v-if="isLogicalCreate" class="architecture-change-section">
        <div class="architecture-change-section__heading">
          <div><span>CASCADE CREATE · OPTIONAL</span><h2>级联物理子系统</h2><small>可不添加，也可随本工单一次创建多条；批准时原子发布。</small></div>
          <el-button type="primary" plain @click="addPhysicalDraft"><el-icon><Plus /></el-icon>添加物理子系统</el-button>
        </div>
        <el-empty v-if="!physicalDrafts.length" description="本次不级联创建物理子系统" :image-size="64">
          <el-button type="primary" plain @click="addPhysicalDraft">添加第一条</el-button>
        </el-empty>
        <div v-else class="architecture-change-card-list">
          <SubsystemChangePhysicalCard
            v-for="(draft, index) in physicalDrafts"
            :key="draft.lineNo"
            v-model="physicalDrafts[index]"
            :organizations="organizations"
            :users="users"
            :logical-subsystems="logicalSubsystems"
            :runtimes="runtimes"
            :levels="levels"
            :frameworks="frameworks"
            :deployment-platforms="deploymentPlatforms"
            :disaster-recovery-modes="disasterRecoveryModes"
            :title="`物理子系统 ${index + 1}`"
            :number-label="physicalNumberLabel(draft)"
            removable
            @remove="removePhysicalDraft(index)"
          />
        </div>
      </section>

      <section v-if="targetKind === 'PHYSICAL'" class="architecture-change-section">
        <div class="architecture-change-section__heading">
          <div><span>PHYSICAL SUBSYSTEM</span><h2>物理子系统草稿</h2><small v-if="actionType === 'REPLACE'">请选择与原归属不同的新逻辑子系统。</small></div>
        </div>
        <SubsystemChangePhysicalCard
          v-if="physicalDrafts[0]"
          v-model="physicalDrafts[0]"
          :organizations="organizations"
          :users="users"
          :logical-subsystems="logicalSubsystems"
          :runtimes="runtimes"
          :levels="levels"
          :frameworks="frameworks"
          :deployment-platforms="deploymentPlatforms"
          :disaster-recovery-modes="disasterRecoveryModes"
          :number-label="physicalNumberLabel(physicalDrafts[0])"
          show-logical-target
          :logical-target-locked="actionType !== 'CREATE' && actionType !== 'REPLACE'"
          :logical-target-exclusions="actionType === 'REPLACE' && sourceLogicalSubsystemId ? [sourceLogicalSubsystemId] : []"
        />
      </section>
    </div>

    <footer v-if="canApply && !loadError && ownsApplication" class="architecture-change-sticky-actions">
      <div><strong>{{ dirty ? '有未保存修改' : '草稿已同步' }}</strong><span>提交后字段锁定，由架构管理员审批。</span></div>
      <div>
        <el-button :disabled="loading || submitting" :loading="saving" @click="saveDraft()">保存草稿</el-button>
        <el-button type="primary" :disabled="loading || saving" :loading="submitting" @click="submit">保存并提交</el-button>
      </div>
    </footer>
  </main>
</template>
