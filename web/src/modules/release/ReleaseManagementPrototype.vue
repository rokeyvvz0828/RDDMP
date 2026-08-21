<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Calendar, DataAnalysis, Document, Monitor, Setting, Tickets } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AxiosError } from 'axios'
import { useRoute, useRouter } from 'vue-router'
import { apiErrorMessage } from '../../api/error'
import {
  cancelBlockedReleaseApplication,
  cancelReleaseApplication,
  changeReleaseWindowRegularEnabled,
  createReleaseApplication,
  createReleaseWindow,
  deleteReleaseApplicationAttachment,
  getCurrentProductionVersions,
  getReleaseApplication,
  getReleaseApplicationConflicts,
  listReleaseApplications,
  listReleaseWindows,
  previewReleaseApplicationConflicts,
  previewReleaseApplicationUpdateConflicts,
  resolveReleaseConflict,
  submitReleaseApplication,
  updateReleaseApplication,
  updateReleaseWindow,
  withdrawReleaseApplication,
  type ProductionEntryDto,
  type ReleaseApplicationDto,
  type ReleaseApplicationStatusCode,
  type ReleaseApplicationWrite,
  type ReleaseAttachmentInput,
  type ReleaseConflictReportDto,
  type ReleaseHistoricalApplicationDto,
  type ReleaseWindowDto,
  type ReleaseWindowUpdate,
  type ReleaseWindowWrite
} from '../../api/release'
import { useProjectContextStore } from '../../stores/project-context'
import { useAuthStore } from '../../stores/auth'
import ReleaseApplicationDrawer from './components/ReleaseApplicationDrawer.vue'
import ReleaseApplicationView from './components/ReleaseApplicationView.vue'
import ReleaseAnalyticsView from './components/ReleaseAnalyticsView.vue'
import ReleaseBaselineView from './components/ReleaseBaselineView.vue'
import ReleaseConflictDialog from './components/ReleaseConflictDialog.vue'
import ReleaseCurrentProductionView from './components/ReleaseCurrentProductionView.vue'
import ReleaseWindowView from './components/ReleaseWindowView.vue'
import ReleaseWorkflowBindingView from './components/ReleaseWorkflowBindingView.vue'
import type { ReleaseSearchOption, ReleaseViewKey } from './types'
import './release-prototype.css'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const projectStore = useProjectContextStore()
const allViews = [
  { key: 'windows' as const, label: '投产窗口', icon: Calendar, path: '/release/windows', permission: 'release:window:view' },
  { key: 'applications' as const, label: '版本申请', icon: Document, path: '/release/applications', permission: 'release:application:view' },
  { key: 'production-ledger' as const, label: '投产基线', icon: Tickets, path: '/release/production-baseline', permission: 'release:baseline:view' },
  { key: 'current-production' as const, label: '生产基线', icon: Monitor, path: '/release/production-versions', permission: 'release:production-version:view' },
  { key: 'analytics' as const, label: '统计分析', icon: DataAnalysis, path: '/release/analytics', permission: 'release:analytics:view' },
  { key: 'workflow-bindings' as const, label: '审批流程配置', icon: Setting, path: '/release/workflow-bindings', permission: 'release:workflow-config:view' }
]
const views = computed(() => allViews.filter(view => auth.hasPermission(view.permission)))
const activeView = computed<ReleaseViewKey>(() => allViews.find(view => view.path === route.path)?.key || views.value[0]?.key || 'windows')
const canViewWindows = computed(() => auth.hasPermission('release:window:view'))
const canCreateWindows = computed(() => auth.hasPermission('release:window:create'))
const canUpdateWindows = computed(() => auth.hasPermission('release:window:update'))
const canViewApplications = computed(() => auth.hasPermission('release:application:view'))
const canCreateApplications = computed(() => auth.hasPermission('release:application:create'))
const canUpdateApplications = computed(() => auth.hasPermission('release:application:update'))
const canSubmitApplications = computed(() => auth.hasPermission('release:application:submit'))
const canWithdrawApplications = computed(() => auth.hasPermission('release:application:withdraw'))
const canCancelApplications = computed(() => auth.hasPermission('release:application:cancel'))
const canUpdateBaseline = computed(() => auth.hasPermission('release:baseline:update'))
const canUpdateWorkflowBindings = computed(() => auth.hasPermission('release:workflow-config:update'))

const windows = ref<ReleaseWindowDto[]>([])
const windowsLoading = ref(false)
const windowsError = ref('')
const windowViewRef = ref<InstanceType<typeof ReleaseWindowView>>()

const applications = ref<ReleaseApplicationDto[]>([])
const applicationTotal = ref(0)
const applicationPage = ref(1)
const applicationPageSize = ref(12)
const applicationKeyword = ref<string>()
const applicationStatus = ref<ReleaseApplicationStatusCode>()
const applicationsLoading = ref(false)
const applicationsError = ref('')
const applicationSearchOptions = ref<ReleaseSearchOption[]>([])
const applicationSearchLoading = ref(false)
const applicationSearchError = ref('')
let applicationSearchTimer: number | undefined
let applicationSearchGeneration = 0

const currentProduction = ref<ProductionEntryDto[]>([])
const applicationDrawerOpen = ref(false)
const editingApplication = ref<ReleaseApplicationDto | null>(null)
const savingApplication = ref(false)
const applicationSaveError = ref('')

const conflictOpen = ref(false)
const conflictReport = ref<ReleaseConflictReportDto>({ applications: [] })
const conflictResolving = ref(false)
const conflictRefreshError = ref('')
const cancellingConflictCode = ref<string>()
type BlockedApplicationOperation = {
  payload: ReleaseApplicationWrite
  mode: 'draft' | 'submit'
  attachments: ReleaseAttachmentInput[]
  applicationCode?: string
  rowVersion?: number
}
const blockedApplicationOperation = ref<BlockedApplicationOperation | null>(null)
let conflictPollTimer: number | undefined
const pendingSubmission = ref<{
  application: ReleaseApplicationDto
  attachments: ReleaseAttachmentInput[]
  confirmedConflictToken?: string
} | null>(null)
const applicationStatusLabels: Record<ReleaseApplicationStatusCode, string> = {
  DRAFT: '草稿', IN_REVIEW: '审批中', RETURNED: '已退回', WITHDRAWN: '已撤回',
  CANCELLED: '已取消', RELEASED: '制品准出'
}

function isStatus(error: unknown, status: number) {
  return (error as AxiosError).response?.status === status
}

function hasInReviewConflict(report: ReleaseConflictReportDto) {
  return report.applications.some(item => item.application.status === 'IN_REVIEW')
}

function stopConflictPolling() {
  if (conflictPollTimer) window.clearTimeout(conflictPollTimer)
  conflictPollTimer = undefined
}

function clearBlockedApplicationOperation() {
  stopConflictPolling()
  blockedApplicationOperation.value = null
  conflictRefreshError.value = ''
  cancellingConflictCode.value = undefined
}

async function loadWindows() {
  if (!projectStore.current || (!canViewWindows.value && !canViewApplications.value)) return
  windowsLoading.value = true
  windowsError.value = ''
  try {
    const response = await listReleaseWindows({ page: 1, size: 200, projectId: projectStore.current.ref })
    windows.value = response.data.data.records
  } catch (error) {
    windows.value = []
    windowsError.value = apiErrorMessage(error, '投产窗口加载失败，请稍后重试')
  } finally {
    windowsLoading.value = false
  }
}

async function loadApplications() {
  if (!projectStore.current || !canViewApplications.value) return
  applicationsLoading.value = true
  applicationsError.value = ''
  try {
    const response = await listReleaseApplications({
      page: applicationPage.value,
      size: applicationPageSize.value,
      projectId: projectStore.current.ref,
      keyword: applicationKeyword.value,
      status: applicationStatus.value
    })
    applications.value = response.data.data.records
    applicationTotal.value = response.data.data.total
  } catch (error) {
    applications.value = []
    applicationTotal.value = 0
    applicationsError.value = apiErrorMessage(error, '版本申请加载失败，请稍后重试')
  } finally {
    applicationsLoading.value = false
  }
}

function resetApplicationSearch() {
  window.clearTimeout(applicationSearchTimer)
  applicationSearchTimer = undefined
  applicationSearchGeneration += 1
  applicationSearchOptions.value = []
  applicationSearchLoading.value = false
  applicationSearchError.value = ''
}

function searchApplicationOptions(query: string) {
  window.clearTimeout(applicationSearchTimer)
  const requestGeneration = ++applicationSearchGeneration
  applicationSearchLoading.value = true
  applicationSearchError.value = ''
  applicationSearchTimer = window.setTimeout(async () => {
    const projectRef = projectStore.current?.ref
    if (!projectRef) {
      if (requestGeneration === applicationSearchGeneration) {
        applicationSearchOptions.value = []
        applicationSearchLoading.value = false
      }
      return
    }
    try {
      const response = await listReleaseApplications({
        page: 1,
        size: 20,
        projectId: projectRef,
        keyword: query || undefined
      })
      if (requestGeneration !== applicationSearchGeneration) return
      applicationSearchOptions.value = response.data.data.records.slice(0, 20).map(application => ({
        value: application.applicationCode,
        label: application.applicationCode,
        description: `${application.subsystemCode} · ${application.subsystemName} · ${applicationStatusLabels[application.status]}`,
        keywords: `${application.applicationCode} ${application.subsystemCode} ${application.subsystemName}`
      }))
    } catch (error) {
      if (requestGeneration !== applicationSearchGeneration) return
      applicationSearchOptions.value = []
      applicationSearchError.value = apiErrorMessage(error, '候选申请加载失败，请重新输入后重试')
    } finally {
      if (requestGeneration === applicationSearchGeneration) applicationSearchLoading.value = false
    }
  }, 300)
}

async function loadCurrentProduction() {
  if (!projectStore.current || !canViewApplications.value) return
  try {
    currentProduction.value = (await getCurrentProductionVersions(projectStore.current.ref)).data.data
  } catch {
    currentProduction.value = []
  }
}

async function refreshPrimaryData() {
  const requests: Promise<void>[] = []
  if (canViewWindows.value) requests.push(loadWindows())
  if (canViewApplications.value) requests.push(loadApplications())
  await Promise.all(requests)
}

async function createWindow(payload: ReleaseWindowWrite) {
  try {
    await createReleaseWindow(payload)
    windowViewRef.value?.finishSave()
    ElMessage.success('投产窗口已创建')
    await loadWindows()
  } catch (error) {
    windowViewRef.value?.failSave()
    ElMessage.error(apiErrorMessage(error, '投产窗口创建失败'))
  }
}

async function updateWindow(id: number, payload: ReleaseWindowUpdate) {
  try {
    await updateReleaseWindow(id, payload)
    windowViewRef.value?.finishSave()
    ElMessage.success('投产窗口已更新')
    await loadWindows()
  } catch (error) {
    windowViewRef.value?.failSave()
    ElMessage.error(apiErrorMessage(error, '投产窗口更新失败'))
  }
}

async function toggleRegular(window: ReleaseWindowDto, enabled: boolean, reason: string) {
  try {
    await changeReleaseWindowRegularEnabled(window.id, enabled, window.rowVersion, reason)
    ElMessage.success(enabled ? '已允许常规版本申请' : '已关闭常规版本申请')
    await loadWindows()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '常规申请开关更新失败'))
  }
}

function queryApplications(query: { page: number; size: number; keyword?: string; status?: ReleaseApplicationStatusCode }) {
  applicationPage.value = query.page
  applicationPageSize.value = query.size
  applicationKeyword.value = query.keyword
  applicationStatus.value = query.status
  void loadApplications()
}

async function openCreateApplication() {
  if (!canCreateApplications.value) return
  applicationSaveError.value = ''
  conflictOpen.value = false
  clearBlockedApplicationOperation()
  pendingSubmission.value = null
  editingApplication.value = null
  await Promise.all([loadWindows(), loadCurrentProduction()])
  applicationDrawerOpen.value = true
}

async function openEditApplication(application: ReleaseApplicationDto) {
  if (!canUpdateApplications.value) return
  applicationSaveError.value = ''
  conflictOpen.value = false
  clearBlockedApplicationOperation()
  pendingSubmission.value = null
  try {
    await Promise.all([loadWindows(), loadCurrentProduction()])
    editingApplication.value = (await getReleaseApplication(application.applicationCode)).data.data
    applicationDrawerOpen.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '版本申请详情加载失败'))
  }
}

function openApplicationDetail(application: ReleaseApplicationDto) {
  void router.push(`/release/applications/${encodeURIComponent(application.applicationCode)}`)
}

function buildApplicationOperation(payload: ReleaseApplicationWrite, mode: 'draft' | 'submit', attachments: ReleaseAttachmentInput[]): BlockedApplicationOperation {
  return {
    payload: {
      ...payload,
      deliveries: payload.deliveries.map(item => ({ ...item })),
      fileMedia: payload.fileMedia.map(item => ({ ...item })),
      requirementCodes: [...payload.requirementCodes]
    },
    mode,
    attachments: attachments.map(item => ({ ...item })),
    applicationCode: editingApplication.value?.applicationCode,
    rowVersion: editingApplication.value?.rowVersion
  }
}

async function previewApplicationOperation(operation: BlockedApplicationOperation) {
  if (operation.applicationCode) {
    if (operation.rowVersion == null) throw new Error('编辑申请缺少 rowVersion')
    return (await previewReleaseApplicationUpdateConflicts(operation.applicationCode, {
      ...operation.payload,
      rowVersion: operation.rowVersion
    })).data.data
  }
  return (await previewReleaseApplicationConflicts(operation.payload)).data.data
}

function scheduleConflictPolling() {
  stopConflictPolling()
  if (!conflictOpen.value || !blockedApplicationOperation.value) return
  conflictPollTimer = window.setTimeout(() => void refreshBlockedApplicationConflicts(), 2000)
}

async function refreshBlockedApplicationConflicts() {
  const operation = blockedApplicationOperation.value
  if (!operation || !conflictOpen.value) return
  stopConflictPolling()
  try {
    const report = await previewApplicationOperation(operation)
    if (blockedApplicationOperation.value !== operation || !conflictOpen.value) return
    conflictReport.value = report
    conflictRefreshError.value = ''
    if (cancellingConflictCode.value && !report.applications.some(item =>
      item.application.applicationCode === cancellingConflictCode.value
      && item.application.status === 'IN_REVIEW')) {
      cancellingConflictCode.value = undefined
    }
    if (hasInReviewConflict(report)) scheduleConflictPolling()
  } catch (error) {
    if (blockedApplicationOperation.value !== operation || !conflictOpen.value) return
    conflictRefreshError.value = apiErrorMessage(error, '审批状态刷新失败，请稍后重试')
    scheduleConflictPolling()
  }
}

function openBlockedApplicationOperation(operation: BlockedApplicationOperation, report: ReleaseConflictReportDto) {
  pendingSubmission.value = null
  blockedApplicationOperation.value = operation
  conflictReport.value = report
  conflictRefreshError.value = ''
  cancellingConflictCode.value = undefined
  conflictOpen.value = true
  scheduleConflictPolling()
}

async function persistApplicationOperation(operation: BlockedApplicationOperation, confirmedByPreflight = false) {
  const response = operation.applicationCode
    ? await updateReleaseApplication(operation.applicationCode, { ...operation.payload, rowVersion: operation.rowVersion! })
    : await createReleaseApplication(operation.payload)
  const saved = response.data.data
  editingApplication.value = saved
  await loadApplications()
  if (operation.mode === 'draft') {
    applicationDrawerOpen.value = false
    ElMessage.success('版本申请草稿已保存')
    return
  }
  const previousConfirmedToken = pendingSubmission.value?.application.applicationCode === saved.applicationCode
    ? pendingSubmission.value.confirmedConflictToken
    : undefined
  const confirmedConflictToken = confirmedByPreflight
    ? saved.conflicts.conflictToken
    : saved.conflicts.conflictToken === previousConfirmedToken ? previousConfirmedToken : undefined
  pendingSubmission.value = { application: saved, attachments: operation.attachments, confirmedConflictToken }
  if (saved.conflicts.applications.length) {
    if (confirmedConflictToken) {
      await submitSavedApplication(saved, operation.attachments, confirmedConflictToken)
      return
    }
    conflictReport.value = saved.conflicts
    conflictOpen.value = true
    return
  }
  await submitSavedApplication(saved, operation.attachments)
}

async function submitSavedApplication(application: ReleaseApplicationDto, attachments: ReleaseAttachmentInput[], conflictToken?: string) {
  const confirmedConflictToken = conflictToken ?? (
    pendingSubmission.value?.application.applicationCode === application.applicationCode
      ? pendingSubmission.value.confirmedConflictToken
      : undefined
  )
  try {
    await submitReleaseApplication(application.applicationCode, { rowVersion: application.rowVersion, conflictToken: confirmedConflictToken, attachments })
    conflictOpen.value = false
    pendingSubmission.value = null
    applicationDrawerOpen.value = false
    ElMessage.success('版本申请已提交审批')
    await loadApplications()
  } catch (error) {
    if (isStatus(error, 409)) {
      try {
        const report = (await getReleaseApplicationConflicts(application.applicationCode)).data.data
        const conflictChanged = !confirmedConflictToken || report.conflictToken !== confirmedConflictToken
        if (report.applications.length && conflictChanged) {
          conflictReport.value = report
          pendingSubmission.value = {
            application: (await getReleaseApplication(application.applicationCode)).data.data,
            attachments
          }
          conflictOpen.value = true
          applicationSaveError.value = '历史申请信息发生变化，请重新确认冲突处理方式。'
          return
        }
      } catch { /* use original error below */ }
    }
    applicationSaveError.value = apiErrorMessage(error, '版本申请提交失败，请稍后重试')
  }
}

async function saveApplication(payload: ReleaseApplicationWrite, mode: 'draft' | 'submit', attachments: ReleaseAttachmentInput[]) {
  const operation = buildApplicationOperation(payload, mode, attachments)
  savingApplication.value = true
  applicationSaveError.value = ''
  try {
    const report = await previewApplicationOperation(operation)
    if (hasInReviewConflict(report)) {
      openBlockedApplicationOperation(operation, report)
      return
    }
    await persistApplicationOperation(operation)
  } catch (error) {
    if (isStatus(error, 409)) {
      const report = await previewApplicationOperation(operation).catch(() => null)
      if (report && hasInReviewConflict(report)) {
        openBlockedApplicationOperation(operation, report)
        return
      }
    }
    applicationSaveError.value = apiErrorMessage(error, '版本申请保存失败，请检查填写内容后重试')
  } finally {
    savingApplication.value = false
  }
}

async function continueBlockedApplication() {
  const operation = blockedApplicationOperation.value
  if (!operation || conflictRefreshError.value || hasInReviewConflict(conflictReport.value)) return
  conflictResolving.value = true
  savingApplication.value = true
  applicationSaveError.value = ''
  try {
    const report = await previewApplicationOperation(operation)
    conflictReport.value = report
    if (hasInReviewConflict(report)) {
      scheduleConflictPolling()
      return
    }
    stopConflictPolling()
    blockedApplicationOperation.value = null
    cancellingConflictCode.value = undefined
    conflictOpen.value = false
    await persistApplicationOperation(operation, true)
  } catch (error) {
    if (isStatus(error, 409)) {
      const report = await previewApplicationOperation(operation).catch(() => null)
      if (report && hasInReviewConflict(report)) {
        openBlockedApplicationOperation(operation, report)
        return
      }
    }
    applicationSaveError.value = apiErrorMessage(error, '继续申请失败，请检查冲突状态后重试')
  } finally {
    savingApplication.value = false
    conflictResolving.value = false
  }
}

async function chooseConflict(action: 'CANCEL_OLD' | 'EDIT_OLD' | 'CREATE_NEW', conflict?: ReleaseHistoricalApplicationDto) {
  if (action === 'CREATE_NEW' && blockedApplicationOperation.value) {
    await continueBlockedApplication()
    return
  }
  const pending = pendingSubmission.value
  const token = conflictReport.value.conflictToken
  if (!pending || !token) return
  if (action === 'CREATE_NEW') {
    pending.confirmedConflictToken = token
    conflictOpen.value = false
    conflictResolving.value = true
    savingApplication.value = true
    try {
      await submitSavedApplication(pending.application, pending.attachments, token)
    } finally {
      savingApplication.value = false
      conflictResolving.value = false
    }
    return
  }
  if (!conflict) return
  let reason: string | undefined
  if (action === 'CANCEL_OLD') {
    try {
      const response = await ElMessageBox.prompt(`取消 ${conflict.application.applicationCode} 后继续处理本次申请。`, '填写取消原因', {
        inputType: 'textarea', inputValidator: value => Boolean(value?.trim()) || '请填写取消原因',
        confirmButtonText: '取消旧申请', cancelButtonText: '返回'
      })
      reason = response.value.trim()
    } catch { return }
  }
  conflictResolving.value = true
  try {
    const response = await resolveReleaseConflict(pending.application.applicationCode, {
      action,
      targetApplicationCode: conflict.application.applicationCode,
      targetRowVersion: conflict.application.rowVersion,
      conflictToken: token,
      reason
    })
    const result = response.data.data
    if (action === 'EDIT_OLD') {
      conflictOpen.value = false
      pendingSubmission.value = null
      const target = (await getReleaseApplication(result.navigateApplicationCode)).data.data
      editingApplication.value = target
      applicationSaveError.value = `本次新申请已保存为草稿，当前正在修改历史申请 ${target.applicationCode}。`
      applicationDrawerOpen.value = true
      return
    }
    if (action === 'CANCEL_OLD' && result.conflicts.applications.length) {
      conflictReport.value = result.conflicts
      pending.application = (await getReleaseApplication(pending.application.applicationCode)).data.data
      return
    }
    await submitSavedApplication(pending.application, pending.attachments, result.conflicts.conflictToken || token)
  } catch (error) {
    applicationSaveError.value = apiErrorMessage(error, '重复申请处理失败，请刷新后重试')
    if (isStatus(error, 409)) {
      const report = await getReleaseApplicationConflicts(pending.application.applicationCode).catch(() => null)
      if (report) conflictReport.value = report.data.data
    }
  } finally {
    conflictResolving.value = false
  }
}

async function cancelBlockedConflict(conflict: ReleaseHistoricalApplicationDto) {
  const application = conflict.application
  if (!blockedApplicationOperation.value || application.status !== 'IN_REVIEW'
      || application.requesterId !== auth.user?.id || !canWithdrawApplications.value) return
  let reason: string
  try {
    const response = await ElMessageBox.prompt(
      `取消 ${application.applicationCode} 后，该申请将变为“已取消”且不能再编辑。审批流程终止完成后才能继续当前申请。`,
      '填写取消原因',
      {
        inputType: 'textarea', inputValidator: value => Boolean(value?.trim()) || '请填写取消原因',
        confirmButtonText: '确认取消', cancelButtonText: '返回'
      }
    )
    reason = response.value.trim()
  } catch {
    return
  }
  cancellingConflictCode.value = application.applicationCode
  try {
    await cancelBlockedReleaseApplication(application.applicationCode, application.rowVersion, reason)
    ElMessage.success('取消请求已提交，正在等待审批流程终止')
    await refreshBlockedApplicationConflicts()
  } catch (error) {
    cancellingConflictCode.value = undefined
    ElMessage.error(apiErrorMessage(error, '取消申请失败，请稍后重试'))
  }
}

function handleConflictOpenChange(value: boolean) {
  conflictOpen.value = value
  if (value) return
  clearBlockedApplicationOperation()
  pendingSubmission.value = null
  conflictReport.value = { applications: [] }
}

async function handleApplicationAction(application: ReleaseApplicationDto, action: 'withdraw' | 'cancel' | 'resubmit') {
  try {
    if (action === 'withdraw') {
      const response = await ElMessageBox.prompt(`撤回 ${application.applicationCode} 后，流程终止完成时申请单可重新编辑。`, '填写撤回原因', {
        inputType: 'textarea', inputValidator: value => Boolean(value?.trim()) || '请填写撤回原因',
        confirmButtonText: '确认撤回', cancelButtonText: '取消'
      })
      await withdrawReleaseApplication(application.applicationCode, application.rowVersion, response.value.trim())
      ElMessage.success('撤回请求已提交，正在等待流程终止结果')
    } else if (action === 'cancel') {
      const response = await ElMessageBox.prompt(`取消后 ${application.applicationCode} 将不能再次提交。`, '填写取消原因', {
        inputType: 'textarea', inputValidator: value => Boolean(value?.trim()) || '请填写取消原因',
        confirmButtonText: '确认取消', cancelButtonText: '返回'
      })
      await cancelReleaseApplication(application.applicationCode, application.rowVersion, response.value.trim())
      ElMessage.success('版本申请已取消')
    } else {
      pendingSubmission.value = { application, attachments: [] }
      if (application.conflicts.applications.length) {
        conflictReport.value = application.conflicts
        conflictOpen.value = true
      } else {
        await submitSavedApplication(application, [])
      }
    }
    await loadApplications()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '申请状态操作失败'))
  }
}

async function deleteAttachment(application: ReleaseApplicationDto, attachmentId: number) {
  try {
    const response = await ElMessageBox.prompt('删除后无法在本申请中继续使用该附件。', '填写删除原因', {
      inputType: 'textarea', inputValidator: value => Boolean(value?.trim()) || '请填写删除原因',
      confirmButtonText: '删除附件', cancelButtonText: '取消'
    })
    await deleteReleaseApplicationAttachment(application.applicationCode, attachmentId, application.rowVersion, response.value.trim())
    editingApplication.value = (await getReleaseApplication(application.applicationCode)).data.data
    ElMessage.success('附件已删除')
  } catch (error) {
    if (!String(error).includes('cancel')) ElMessage.error(apiErrorMessage(error, '附件删除失败'))
  }
}

onMounted(async () => {
  await projectStore.initialize()
  await refreshPrimaryData()
})
watch(() => projectStore.currentRef, async (next, previous) => {
  if (!next || next === previous) return
  resetApplicationSearch()
  applicationPage.value = 1
  applicationDrawerOpen.value = false
  conflictOpen.value = false
  clearBlockedApplicationOperation()
  pendingSubmission.value = null
  await refreshPrimaryData()
})
watch(activeView, view => {
  if (view !== 'applications') resetApplicationSearch()
})
onBeforeUnmount(() => {
  resetApplicationSearch()
  stopConflictPolling()
})
</script>

<template>
  <section class="release-prototype-page">
    <nav class="release-module-nav" aria-label="配置管理视图">
      <button v-for="view in views" :key="view.key" type="button" :class="{ active: activeView === view.key }" @click="router.push(view.path)"><el-icon><component :is="view.icon" /></el-icon><span>{{ view.label }}</span></button>
    </nav>

    <ReleaseWindowView v-if="activeView === 'windows'" ref="windowViewRef" :windows="windows" :project="projectStore.current" :loading="windowsLoading" :error="windowsError" :can-create="canCreateWindows" :can-update="canUpdateWindows" @refresh="loadWindows" @create="createWindow" @update="updateWindow" @toggle-regular="toggleRegular" />
    <ReleaseApplicationView v-else-if="activeView === 'applications'" :key="projectStore.currentRef" :applications="applications" :total="applicationTotal" :page="applicationPage" :page-size="applicationPageSize" :loading="applicationsLoading" :error="applicationsError" :search-options="applicationSearchOptions" :search-loading="applicationSearchLoading" :search-error="applicationSearchError" :can-create="canCreateApplications" :can-update="canUpdateApplications" :can-submit="canSubmitApplications" :can-withdraw="canWithdrawApplications" :can-cancel="canCancelApplications" @search-options="searchApplicationOptions" @query="queryApplications" @refresh="loadApplications" @create="openCreateApplication" @edit="openEditApplication" @detail="openApplicationDetail" @action="handleApplicationAction" />
    <ReleaseBaselineView v-else-if="activeView === 'production-ledger'" :can-update="canUpdateBaseline" />
    <ReleaseCurrentProductionView v-else-if="activeView === 'current-production'" />
    <ReleaseAnalyticsView v-else-if="activeView === 'analytics'" />
    <ReleaseWorkflowBindingView v-else-if="projectStore.current" :project="projectStore.current" :can-update="canUpdateWorkflowBindings" />

    <ReleaseApplicationDrawer v-model="applicationDrawerOpen" :application="editingApplication" :windows="windows" :project="projectStore.current" :current-production="currentProduction" :saving="savingApplication" :save-error="applicationSaveError" :can-submit="canSubmitApplications" :can-delete-attachment="canUpdateApplications" @save="saveApplication" @delete-attachment="deleteAttachment" />
    <ReleaseConflictDialog
      :model-value="conflictOpen"
      :conflicts="conflictReport.applications"
      :resolving="conflictResolving"
      :current-user-id="auth.user?.id"
      :can-cancel-review="canWithdrawApplications"
      :cancelling-code="cancellingConflictCode"
      :preflight="Boolean(blockedApplicationOperation)"
      :refresh-error="conflictRefreshError"
      @update:model-value="handleConflictOpenChange"
      @choose="chooseConflict"
      @cancel="cancelBlockedConflict"
    />
  </section>
</template>
