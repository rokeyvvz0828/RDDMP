<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, MagicStick } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import UiPageHeader from '../../components/ui/UiPageHeader.vue'
import { apiErrorMessage } from '../../api/error'
import { useAuthStore } from '../../stores/auth'
import {
  createSubsystemChangeApplication,
  getPhysicalSubsystem,
  getSubsystemChangeApplication,
  loadBusinessComponentOptions,
  loadOrganizationOptions,
  loadParameterOptions,
  loadUserOptions,
  requestSubsystemSuggestions,
  submitSubsystemChangeApplication,
  updateSubsystemChangeApplication
} from './api'
import SubsystemChangePhysicalCard from './components/SubsystemChangePhysicalCard.vue'
import type {
  OrganizationOption,
  ParameterOption,
  PhysicalDraftInput,
  SubsystemActionType,
  SubsystemChangeApplicationDetail,
  SubsystemSuggestion,
  UserOption
} from './types'
import {
  actionTypeLabels,
  cancelled,
  httpStatus,
  normalizeText
} from './utils'
import './architecture.css'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const applicationId = ref<number | null>(positiveNumber(route.params.id))
const actionType = ref<SubsystemActionType>(readAction(route.query.actionType))
const targetId = ref<number | null>(positiveNumber(route.query.targetId))
const reason = ref('')
const physicalDrafts = ref<PhysicalDraftInput[]>([])
const detail = ref<SubsystemChangeApplicationDetail | null>(null)
const organizations = ref<OrganizationOption[]>([])
const users = ref<UserOption[]>([])
const businessComponents = ref<ParameterOption[]>([])
const deploymentPlatforms = ref<ParameterOption[]>([])
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
const title = computed(() => isExisting.value
  ? `编辑工单 #${applicationId.value}`
  : `${actionTypeLabels[actionType.value]}物理子系统申请`)
const serialized = computed(() => JSON.stringify({
  actionType: actionType.value,
  targetId: targetId.value,
  reason: reason.value,
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

function emptyPhysicalDraft(lineNo: number): PhysicalDraftInput {
  return {
    lineNo,
    code: '',
    shortName: '',
    name: '',
    logicalSubsystemName: null,
    businessComponentCode: null,
    englishName: null,
    businessGroupName: null,
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

function physicalInput(source: SubsystemChangeApplicationDetail['physicalDrafts'][number]): PhysicalDraftInput {
  return {
    lineNo: source.lineNo,
    code: source.code,
    shortName: source.shortName,
    name: source.name,
    logicalSubsystemName: source.logicalSubsystemName,
    businessComponentCode: source.businessComponentCode,
    englishName: source.englishName,
    businessGroupName: source.businessGroupName,
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
  actionType.value = application.application.actionType
  targetId.value = application.application.targetId
  reason.value = application.application.reason
  physicalDrafts.value = application.physicalDrafts.length
    ? application.physicalDrafts.map(physicalInput)
    : [emptyPhysicalDraft(1)]
  baseline.value = serialized.value
}

async function loadReferences() {
  const results = await Promise.allSettled([
    loadOrganizationOptions('physical-subsystem', '', 100),
    loadUserOptions('physical-subsystem', '', 100),
    loadBusinessComponentOptions(),
    loadParameterOptions('physical-subsystem', 'ARCH_RUNTIME'),
    loadParameterOptions('physical-subsystem', 'ARCH_SYSTEM_LEVEL'),
    loadParameterOptions('physical-subsystem', 'ARCH_DEVELOPMENT_FRAMEWORK'),
    loadParameterOptions('physical-subsystem', 'ARCH_DEPLOYMENT_PLATFORM'),
    loadParameterOptions('physical-subsystem', 'ARCH_DISASTER_RECOVERY_MODE')
  ])
  if (results[0].status === 'fulfilled') organizations.value = results[0].value
  if (results[1].status === 'fulfilled') users.value = results[1].value
  if (results[2].status === 'fulfilled') businessComponents.value = results[2].value
  if (results[3].status === 'fulfilled') runtimes.value = results[3].value
  if (results[4].status === 'fulfilled') levels.value = results[4].value
  if (results[5].status === 'fulfilled') frameworks.value = results[5].value
  if (results[6].status === 'fulfilled') deploymentPlatforms.value = results[6].value
  if (results[7].status === 'fulfilled') disasterRecoveryModes.value = results[7].value
  if (results.some(item => item.status === 'rejected')) ElMessage.warning('部分选项加载失败，仍可保留当前草稿并稍后重试')
}

async function initializeFromPublished() {
  if (!targetId.value || actionType.value === 'CREATE') {
    physicalDrafts.value = [emptyPhysicalDraft(1)]
    return
  }
  const source = await getPhysicalSubsystem(targetId.value)
  physicalDrafts.value = [{
    lineNo: 1,
    code: source.code,
    shortName: source.shortName,
    name: source.name,
    logicalSubsystemName: source.logicalSubsystemName,
    businessComponentCode: source.businessComponentCode,
    englishName: source.englishName,
    businessGroupName: source.businessGroupName,
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

function normalizeDrafts() {
  reason.value = reason.value.trim()
  physicalDrafts.value = physicalDrafts.value.map((item, index) => ({
    ...item,
    lineNo: index + 1,
    code: item.code.trim().toUpperCase(),
    shortName: item.shortName.trim(),
    name: item.name.trim(),
    logicalSubsystemName: normalizeText(item.logicalSubsystemName),
    businessComponentCode: normalizeText(item.businessComponentCode),
    englishName: normalizeText(item.englishName),
    businessGroupName: normalizeText(item.businessGroupName),
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
  if (actionType.value !== 'CREATE' && !targetId.value) return '非新增工单必须指向已发布物理子系统'
  if (physicalDrafts.value.length !== 1) return '物理工单必须且只能包含一个物理子系统草稿'
  const draft = physicalDrafts.value[0]
  if (!/^[A-Z0-9_-]{2,32}$/.test(draft.code)) return '请填写 2—32 位物理子系统编号，仅支持字母、数字、连字符和下划线'
  if (!draft.shortName || !draft.name) return '请完整填写物理子系统简称和名称'
  if (!draft.responsibleTeamOrgId || !draft.responsibleTeamNameSnapshot) return '请选择物理子系统负责团队'
  if (actionType.value !== 'CREATE' && draft.sourceRowVersion === null) return '源物理子系统版本缺失，请返回列表重新发起'
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
          physicalDrafts: physicalDrafts.value
        })
      : await createSubsystemChangeApplication({
          targetKind: 'PHYSICAL',
          actionType: actionType.value,
          targetId: targetId.value,
          reason: reason.value,
          physicalDraft: physicalDrafts.value[0]
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
    ElMessage.error(apiErrorMessage(error, '提交审批失败；草稿保持不变'))
  } finally {
    submitting.value = false
  }
}

function currentSuggestionFields() {
  const draft = physicalDrafts.value[0]
  return {
    reason: reason.value,
    code: draft?.code || '',
    shortName: draft?.shortName || '',
    name: draft?.name || '',
    logicalSubsystemName: draft?.logicalSubsystemName || '',
    englishName: draft?.englishName || '',
    description: draft?.description || ''
  }
}

function applySuggestion(suggestion: SubsystemSuggestion) {
  if (suggestion.field === 'reason') {
    reason.value = suggestion.value
    return
  }
  const draft = physicalDrafts.value[0]
  if (!draft) return
  const key = suggestion.field as 'code' | 'shortName' | 'name' | 'logicalSubsystemName' | 'englishName' | 'description' | 'remark'
  if (['code', 'shortName', 'name', 'logicalSubsystemName', 'englishName', 'description', 'remark'].includes(key)) {
    draft[key] = suggestion.value
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
    <UiPageHeader :title="title" description="物理子系统编号由申请人填写；提交时校验编号、名称和英文名称在全生命周期内不重复。">
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
        title="工单已退回，可修改草稿后重新提交。"
      />

      <section class="architecture-change-section">
        <div class="architecture-change-section__heading">
          <div><span>APPLICATION</span><h2>申请信息</h2></div>
          <el-button :disabled="loading || saving || submitting" @click="requestSuggestions"><el-icon><MagicStick /></el-icon>获取本地建议</el-button>
        </div>
        <el-form label-position="top">
          <div class="architecture-form-grid">
            <el-form-item label="申请对象"><el-input model-value="物理子系统" disabled /></el-form-item>
            <el-form-item label="变更动作"><el-input :model-value="actionTypeLabels[actionType]" disabled /></el-form-item>
            <el-form-item label="申请原因" required class="is-wide">
              <el-input v-model="reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="说明业务目的、影响和期望结果" />
            </el-form-item>
          </div>
        </el-form>
        <p class="architecture-change-form__hint">建议接口当前只预留本地策略，不调用真实 AI；候选值必须由你显式采用。</p>
      </section>

      <section class="architecture-change-section">
        <div class="architecture-change-section__heading">
          <div><span>PHYSICAL SUBSYSTEM</span><h2>物理子系统草稿</h2><small>所属逻辑子系统为可选文本，业务组件编号来自系统字典。</small></div>
        </div>
        <SubsystemChangePhysicalCard
          v-if="physicalDrafts[0]"
          v-model="physicalDrafts[0]"
          :organizations="organizations"
          :users="users"
          :business-components="businessComponents"
          :runtimes="runtimes"
          :levels="levels"
          :frameworks="frameworks"
          :deployment-platforms="deploymentPlatforms"
          :disaster-recovery-modes="disasterRecoveryModes"
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
