<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Check, Clock, Close, Delete, Download, Edit, MoreFilled, Plus, Promotion, Refresh, RefreshRight, Tickets, UploadFilled } from '@element-plus/icons-vue'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiFormDrawer from '../components/ui/UiFormDrawer.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiToolbar from '../components/ui/UiToolbar.vue'
import UiPagination from '../components/ui/UiPagination.vue'
import UiEmptyState from '../components/ui/UiEmptyState.vue'
import { apiErrorMessage } from '../api/error'
import { listSystem } from '../api/system'
import { decideWorkflowTask, getWorkflowTaskContext, type WorkflowTaskAction, type WorkflowTaskContext } from '../api/workflow'
import {
  addProjectMember,
  confirmImport,
  createBaseline,
  createDifference,
  createLegacy,
  createProject,
  createSystem,
  deleteDifference,
  deleteLegacy,
  deleteProject,
  deleteSystem,
  differenceChanges,
  listDifferenceApprovalLogs,
  downloadTemplate,
  fetchRequirementEnums,
  legacyChanges,
  legacyStageLogs,
  listBaselineItems,
  listBaselines,
  listDifferences,
  listLegacy,
  listProjectMembers,
  listProjects,
  listReviewers,
  listSystems,
  previewImport,
  removeProjectMember,
  stageTransition,
  submitReview,
  cancelReview,
  updateDifference,
  updateLegacy,
  updateProject,
  updateSystem
} from '../api/requirements'
import type {
  BaselineItem,
  ChangeLogRow,
  ImportPreviewReport,
  LegacyRequirement,
  ProjectMember,
  RequirementApprovalLog,
  RequirementBaseline,
  RequirementDifference,
  RequirementEnums,
  RequirementProject,
  RequirementSystem,
  StageLogRow
} from '../types/requirements'
import type { RequirementReviewer } from '../api/requirements'

const route = useRoute()
const router = useRouter()
const section = computed(() => String(route.params.section || 'new-project'))
// 八大参数管理三级菜单：页面内容暂不实施，先展示占位
const PARAM_SECTION_TITLES: Record<string, string> = {
  'product-catalog': '产品目录',
  pricing: '定价管理',
  'finance-accounting': '财务会计',
  'org-staff': '机构员工',
  'staff-channel': '员工渠道',
  parameter: '参数管理',
  'auth-review': '授权复核',
  'voucher-receipt': '凭证回单'
}
const enums = ref<RequirementEnums | null>(null)
const options = computed(() => enums.value?.options || {})
const fieldLabels = computed(() => enums.value?.fieldLabels || {})

const changeTypeLabels: Record<string, string> = {
  CREATE: '新增', UPDATE: '修改', DELETE: '删除', SUBMIT_REVIEW: '提交评审',
  REVIEW_PASS: '评审通过', REVIEW_RETURN: '评审退回', BASELINE: '纳入基线',
  STAGE_TRANSITION: '阶段推进', IMPORT: '导入'
}

function label(field: string | null | undefined) {
  return field ? fieldLabels.value[field] || field : ''
}

async function loadEnums() {
  try {
    enums.value = (await fetchRequirementEnums()).data.data
    if (section.value === 'new-project') await loadSystems()
    if (section.value === 'legacy') await loadSystems()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '枚举加载失败'))
  }
}

watch(section, () => {
  if (section.value === 'new-project') loadProjects()
  if (section.value === 'legacy') {
    loadLegacy()
    loadSystems()
  }
  if (section.value === 'systems') loadSystems()
})

// ---------------- 工作流审批抽屉（从"我的代办"点击业务事项带 taskId 进入时） ----------------
type ApprovalAction = Extract<WorkflowTaskAction, 'APPROVE' | 'REJECT' | 'RETURN'>
const APPROVAL_ACTION_META: Record<ApprovalAction, { label: string; type: 'primary' | 'danger' | 'warning'; plain?: boolean }> = {
  APPROVE: { label: '同意', type: 'primary' },
  RETURN: { label: '回退', type: 'warning', plain: true },
  REJECT: { label: '不通过', type: 'danger', plain: true }
}
const approvalDialogVisible = ref(false)
const approvalLoading = ref(false)
const approvalTaskId = ref<number | null>(null)
const approvalContext = ref<WorkflowTaskContext | null>(null)
const approvalComment = ref('')
const approvalSubmitting = ref(false)
const approvalActions = computed<ApprovalAction[]>(() =>
  (approvalContext.value?.allowed_actions || []).filter((a): a is ApprovalAction =>
    ['APPROVE', 'RETURN', 'REJECT'].includes(a))
)

async function openApproval(taskId: number) {
  approvalTaskId.value = taskId
  approvalLoading.value = true
  approvalDialogVisible.value = true
  approvalComment.value = ''
  approvalContext.value = null
  try {
    const res = await getWorkflowTaskContext(taskId)
    approvalContext.value = res.data.data
  } catch (e) {
    ElMessage.error('审批任务上下文加载失败：' + apiErrorMessage(e, '加载失败，请稍后重试'))
    approvalDialogVisible.value = false
  } finally {
    approvalLoading.value = false
  }
}

async function submitApproval(action: ApprovalAction) {
  if (!approvalTaskId.value || approvalSubmitting.value) return
  approvalSubmitting.value = true
  try {
    await decideWorkflowTask(approvalTaskId.value, action, approvalComment.value.trim())
    ElMessage.success(action === 'APPROVE' ? '审批通过' : action === 'REJECT' ? '已驳回' : '已回退')
    approvalDialogVisible.value = false
    // 同步刷新两个列表（审批通过/驳回后对应记录的状态会更新）
    loadProjects()
    loadLegacy()
    // 清理路由 query 中的 taskId，避免回到页面时重复打开
    const { taskId, ...rest } = route.query
    void router.replace({ path: route.path, query: rest })
  } catch (e) {
    ElMessage.error('审批操作失败：' + apiErrorMessage(e, '操作失败，请稍后重试'))
  } finally {
    approvalSubmitting.value = false
  }
}

// 从我的代办中心跳转时带上 taskId / instanceId query → 自动打开审批抽屉
watch(() => route.query.taskId, (value) => {
  const id = typeof value === 'string' ? Number(value) : Number((value as unknown as string[] | undefined)?.[0])
  if (Number.isFinite(id) && id > 0) void openApproval(id)
}, { immediate: true })

onMounted(() => {
  void loadEnums()
  if (section.value === 'new-project') loadProjects()
  if (section.value === 'legacy') loadLegacy()
  if (section.value === 'systems') loadSystems()
})

// ---------------- 新建项目 ----------------
const projects = ref<RequirementProject[]>([])
const projectsLoading = ref(false)
const selectedProject = ref<RequirementProject | null>(null)
const selectedProjectId = ref<number | null>(null)
const projectFormVisible = ref(false)
const projectSaving = ref(false)
const projectForm = reactive<Record<string, unknown>>({ project_code: '', project_name: '', project_type: '0~1 新建', start_time: '', status: '进行中', description: '' })

async function loadProjects() {
  projectsLoading.value = true
  try {
    projects.value = (await listProjects()).data.data
    if (projects.value.length) {
      const keep = projects.value.find(project => project.id === selectedProjectId.value)
      selectedProjectId.value = keep ? keep.id : projects.value[0].id
      selectedProject.value = projects.value.find(project => project.id === selectedProjectId.value) || null
      await loadDifferences()
    } else {
      selectedProjectId.value = null
      selectedProject.value = null
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '项目加载失败'))
  } finally {
    projectsLoading.value = false
  }
}

function openProjectCreate() {
  Object.assign(projectForm, { project_code: '', project_name: '', project_type: '0~1 新建', start_time: '', status: '进行中', description: '' })
  projectFormVisible.value = true
}

function openProjectEdit(row: RequirementProject) {
  Object.assign(projectForm, row)
  projectFormVisible.value = true
}

async function saveProject() {
  projectSaving.value = true
  try {
    let createdId: number | null = null
    if (projectForm.id) {
      await updateProject(Number(projectForm.id), projectForm)
    } else {
      createdId = (await createProject(projectForm)).data.data.id
    }
    if (createdId) {
      selectedProjectId.value = createdId
    }
    projectFormVisible.value = false
    ElMessage.success('项目已保存')
    await loadProjects()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '项目保存失败'))
  } finally {
    projectSaving.value = false
  }
}

function onProjectChange(id: number) {
  selectedProject.value = projects.value.find(project => project.id === id) || null
  if (selectedProject.value) {
    differencePage.value = 1
    void loadDifferences()
  }
}

function projectCommand(command: string) {
  if (!selectedProject.value) return
  if (command === 'edit') openProjectEdit(selectedProject.value)
  if (command === 'delete') void removeProject(selectedProject.value)
}

async function removeProject(row: RequirementProject) {
  try {
    await ElMessageBox.confirm(`确认删除项目「${row.project_name}」？`, '删除确认', { type: 'warning' })
    await deleteProject(row.id)
    ElMessage.success('项目已删除')
    await loadProjects()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '项目删除失败'))
  }
}

// 项目成员
const memberDialogVisible = ref(false)
const members = ref<ProjectMember[]>([])
const memberUserId = ref<number | null>(null)
const userOptions = ref<Array<{ id: number; display_name?: string; username?: string }>>([])

async function openMembers(project: RequirementProject) {
  selectedProject.value = project
  memberUserId.value = null
  try {
    members.value = (await listProjectMembers(project.id)).data.data
    const page = (await listSystem('users', { page: 1, size: 100 })).data.data
    userOptions.value = (page as { records: Array<{ id: number; display_name?: string; username?: string }> }).records || []
    memberDialogVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '成员加载失败'))
  }
}

async function addMember() {
  if (!selectedProject.value || !memberUserId.value) return
  try {
    await addProjectMember(selectedProject.value.id, { userId: memberUserId.value })
    members.value = (await listProjectMembers(selectedProject.value.id)).data.data
    ElMessage.success('成员已添加')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '成员添加失败'))
  }
}

async function removeMember(row: ProjectMember) {
  try {
    await removeProjectMember(row.id)
    members.value = members.value.filter(item => item.id !== row.id)
    ElMessage.success('成员已移除')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '成员移除失败'))
  }
}

// 差异清单
const differences = ref<RequirementDifference[]>([])
const differencesTotal = ref(0)
const differencePage = ref(1)
const differenceSize = ref(20)
const differencesLoading = ref(false)
const diffFilters = reactive<{ reviewStatus: string; devStatus: string; testStatus: string; keyword: string }>({ reviewStatus: '', devStatus: '', testStatus: '', keyword: '' })

async function loadDifferences() {
  if (!selectedProject.value) return
  differencesLoading.value = true
  try {
    const page = (await listDifferences({
      projectId: selectedProject.value.id,
      ...diffFilters,
      page: differencePage.value,
      size: differenceSize.value
    })).data.data
    differences.value = page.records
    differencesTotal.value = page.total
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '差异清单加载失败'))
  } finally {
    differencesLoading.value = false
  }
}

const diffFormVisible = ref(false)
const diffSaving = ref(false)
const diffForm = reactive<Record<string, unknown>>({})

function openDiffCreate() {
  const blank: Record<string, unknown> = { name: '', business_conglomerate: '', business_section: '', business_group: '', requirement_no: '', category: '', system_id: undefined, jinke_practice: '', difference_type: '', monshang_practice: '', difference_desc: '', monshang_dept: '', monshang_analyst: '', jinke_analyst: '', adapt_mode: '', handle_status: '', coord_group: '', solution: '', is_special: '否', decision_level: '', decision_conclusion: '', monshang_confirm_dept: '', jinke_confirmer: '', dev_status: '未开始', test_status: '未开始' }
  Object.keys(diffForm).forEach(key => delete diffForm[key])
  Object.assign(diffForm, blank)
  diffFormVisible.value = true
}

function openDiffEdit(row: RequirementDifference) {
  Object.keys(diffForm).forEach(key => delete diffForm[key])
  Object.assign(diffForm, { id: row.id }, row)
  diffFormVisible.value = true
}

async function saveDifference() {
  if (!selectedProject.value) return
  diffSaving.value = true
  try {
    if (diffForm.id) {
      await updateDifference(Number(diffForm.id), diffForm)
    } else {
      await createDifference(selectedProject.value.id, diffForm)
    }
    diffFormVisible.value = false
    ElMessage.success('差异已保存')
    await loadDifferences()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '差异保存失败'))
  } finally {
    diffSaving.value = false
  }
}

async function removeDifference(row: RequirementDifference) {
  try {
    await ElMessageBox.confirm(`确认删除差异「${row.name}」？`, '删除确认', { type: 'warning' })
    await deleteDifference(row.id)
    ElMessage.success('差异已删除')
    await loadDifferences()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '差异删除失败'))
  }
}

async function submitDifferenceReview(row: RequirementDifference) {
  try {
    const reviewers = (await listReviewers()).data.data
    if (!reviewers || reviewers.length === 0) {
      ElMessage.warning('当前没有可选审批人，请先为相关用户配置 requirement:diff:review 权限或统筹角色')
      return
    }
    submitReviewTarget.value = row
    submitReviewApprovers.value = []
    submitReviewOptions.value = reviewers
    submitReviewDialogVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '加载审批人失败'))
  }
}

const submitReviewDialogVisible = ref(false)
const submitReviewSaving = ref(false)
const submitReviewTarget = ref<RequirementDifference | null>(null)
const submitReviewApprovers = ref<number[]>([])
const submitReviewOptions = ref<RequirementReviewer[]>([])

async function confirmSubmitReview() {
  if (!submitReviewTarget.value) return
  if (submitReviewApprovers.value.length === 0) {
    ElMessage.warning('请选择至少一位审批人')
    return
  }
  submitReviewSaving.value = true
  try {
    await submitReview(submitReviewTarget.value.id, submitReviewApprovers.value)
    submitReviewDialogVisible.value = false
    ElMessage.success('已提交评审，等待审批人处理')
    await loadDifferences()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '提交评审失败'))
  } finally {
    submitReviewSaving.value = false
  }
}

async function cancelDifferenceReview(row: RequirementDifference) {
  try {
    await ElMessageBox.confirm(`确定要撤销“${row.requirement_no || row.name || ''}”的当前评审流程吗？\n撤销后状态会回到“待评审”，修改完成后可重新提交。`,
      '撤销评审确认', { confirmButtonText: '确认撤销', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  try {
    await cancelReview(row.id)
    ElMessage.success('已撤销评审，可重新编辑后再提交')
    await loadDifferences()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '撤销评审失败'))
  }
}

// 基线
const baselineDialogVisible = ref(false)
const baselines = ref<RequirementBaseline[]>([])
const baselineItems = ref<BaselineItem[]>([])
const baselineDetailVisible = ref(false)

async function openBaselines() {
  if (!selectedProject.value) return
  try {
    baselines.value = (await listBaselines(selectedProject.value.id)).data.data
    baselineDialogVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '基线加载失败'))
  }
}

async function formBaseline() {
  if (!selectedProject.value) return
  try {
    await ElMessageBox.confirm(`确认对项目「${selectedProject.value.project_name}」形成基线？形成后差异整体锁定。`, '形成基线', { type: 'warning' })
    const result = (await createBaseline(selectedProject.value.id)).data.data
    ElMessage.success(`基线已形成：${result.baseline_no}`)
    await loadProjects()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '形成基线失败'))
  }
}

async function showBaselineItems(row: RequirementBaseline) {
  try {
    baselineItems.value = (await listBaselineItems(row.id)).data.data
    baselineDetailVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '基线明细加载失败'))
  }
}

// 修改记录
const changeLogDialogVisible = ref(false)
const changeLogs = ref<ChangeLogRow[]>([])
const changeLogTitle = ref('修改记录')

async function openChangeLogs(bizType: 'NEW_PROJECT_DIFF' | 'LEGACY_REQUIREMENT', bizId: number, title: string) {
  changeLogTitle.value = title
  try {
    changeLogs.value = bizType === 'NEW_PROJECT_DIFF'
      ? (await differenceChanges(bizId)).data.data
      : (await legacyChanges(bizId)).data.data
    changeLogDialogVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '修改记录加载失败'))
  }
}

// 审批记录（差异专属，独立于修改记录，直接读工作流 wf_task_action）
const approvalLogDialogVisible = ref(false)
const approvalLogs = ref<RequirementApprovalLog[]>([])
const approvalLogTitle = ref('审批记录')
const approvalLogsLoading = ref(false)

const approvalActionLabels: Record<string, string> = {
  APPROVE: '通过', REJECT: '驳回', ADD_SIGN: '加签', CC: '抄送'
}
const approvalTaskTypeLabels: Record<string, string> = {
  APPROVAL: '审批', CC: '抄送', ADD_SIGN: '加签'
}
const approvalTaskStatusLabels: Record<string, string> = {
  PENDING: '待处理', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已取消', SENT: '已送达'
}

function approvalActionTagType(action: string): 'success' | 'warning' | 'info' | 'primary' {
  if (action === 'APPROVE') return 'success'
  if (action === 'REJECT') return 'warning'
  return 'info'
}

async function openApprovalLogs(row: RequirementDifference) {
  approvalLogTitle.value = `审批记录：${row.requirement_no || row.name}`
  approvalLogDialogVisible.value = true
  approvalLogs.value = []
  approvalLogsLoading.value = true
  try {
    approvalLogs.value = (await listDifferenceApprovalLogs(row.id)).data.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '审批记录加载失败'))
  } finally {
    approvalLogsLoading.value = false
  }
}

// 导入
const importDialogVisible = ref(false)
const importReport = ref<ImportPreviewReport | null>(null)
const importFile = ref<File | null>(null)
const importing = ref(false)

function openImport() {
  importReport.value = null
  importFile.value = null
  importDialogVisible.value = true
}

async function onImportFile(file: File) {
  if (!selectedProject.value) return
  importing.value = true
  try {
    importReport.value = (await previewImport('DIFF', selectedProject.value.id, file)).data.data
    importFile.value = file
    ElMessage.success(`校验完成：成功 ${importReport.value.successRows} 行，错误 ${importReport.value.errorRows} 行`)
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '导入文件校验失败'))
  } finally {
    importing.value = false
  }
}

async function confirmImportRows() {
  if (!importReport.value) return
  importing.value = true
  try {
    const result = (await confirmImport({
      bizType: 'DIFF',
      projectId: selectedProject.value?.id,
      fileName: importFile.value?.name,
      rows: importReport.value.rows
    })).data.data
    ElMessage.success(`导入完成：${result.successRows} 行`)
    importDialogVisible.value = false
    await loadDifferences()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '确认导入失败'))
  } finally {
    importing.value = false
  }
}

// ---------------- 存量项目 ----------------
const legacyRows = ref<LegacyRequirement[]>([])
const legacyTotal = ref(0)
const legacyPage = ref(1)
const legacySize = ref(20)
const legacyLoading = ref(false)
const legacyFilters = reactive<{ businessGroup: string; stage: string; stageStatus: string; keyword: string }>({ businessGroup: '', stage: '', stageStatus: '', keyword: '' })
// 软需阶段主责/协同系统选择：数据源为需求管理系统清单（req_system，含事业群），先选事业群再选系统
const systemConglomerates = computed(() => [...new Set(systems.value.map(s => s.conglomerate).filter((v): v is string => !!v))].sort())
const systemsByConglomerate = (conglomerate: string) => systems.value.filter(s => s.conglomerate === conglomerate)
function systemValue(system: RequirementSystem) {
  return `${system.system_code}+${system.system_name}`
}
function splitEntries(text: unknown): string[] {
  if (text === null || text === undefined) return []
  return String(text).split(/[；;]/).map(s => s.trim()).filter(Boolean)
}
function systemCodeOf(value: string) {
  return value.split(/[+\s]/)[0]
}

async function loadLegacy() {
  legacyLoading.value = true
  try {
    const page = (await listLegacy({
      businessGroup: legacyFilters.businessGroup || undefined,
      stage: legacyFilters.stage || undefined,
      stageStatus: legacyFilters.stageStatus || undefined,
      keyword: legacyFilters.keyword || undefined,
      page: legacyPage.value,
      size: legacySize.value
    })).data.data
    legacyRows.value = page.records
    legacyTotal.value = page.total
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '存量需求加载失败'))
  } finally {
    legacyLoading.value = false
  }
}

const legacyFormVisible = ref(false)
const legacySaving = ref(false)
const legacyForm = reactive<Record<string, unknown>>({})
// 协同系统行（多个协同系统：一个用户需求可挂多个），保存时合并写入 coord_conglomerate/coord_system
const coordRows = ref<Array<{ conglomerate: string; system: string }>>([])

function openLegacyCreate() {
  Object.keys(legacyForm).forEach(key => delete legacyForm[key])
  Object.assign(legacyForm, {
    legacy_doc_name: '', requirement_no: '', requirement_name: '', content_summary: '',
    propose_dept: '', proposer: '', monshang_ba: '', monshang_architect: '',
    requirement_received_date: '', requirement_type: '', regulation_category: '',
    business_group: '', sub_group: '', jinke_contact: '', need_jinke_arch_decision: '否',
    jinke_architect: '', requirement_status: '需求分析', remark: ''
  })
  coordRows.value = []
  legacyFormVisible.value = true
}

function openLegacyEdit(row: LegacyRequirement) {
  Object.keys(legacyForm).forEach(key => delete legacyForm[key])
  Object.assign(legacyForm, { id: row.id }, row)
  // 从既有"；"分隔的协同字段还原为多行
  const conglomerates = splitEntries((row as any).coord_conglomerate)
  const systems = splitEntries((row as any).coord_system)
  coordRows.value = systems.map((system, index) => ({ conglomerate: conglomerates[index] || '', system }))
  legacyFormVisible.value = true
}

async function saveLegacy() {
  legacySaving.value = true
  try {
    // 保存前把协同系统多行合并写回表单字段（空行忽略）
    const filled = coordRows.value.filter(r => r.system && r.conglomerate)
    legacyForm.coord_conglomerate = filled.map(r => r.conglomerate).join('；')
    legacyForm.coord_system = filled.map(r => r.system).join('；')
    if (legacyForm.id) {
      await updateLegacy(Number(legacyForm.id), legacyForm)
    } else {
      await createLegacy(legacyForm)
    }
    legacyFormVisible.value = false
    ElMessage.success('存量需求已保存')
    await loadLegacy()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '存量需求保存失败'))
  } finally {
    legacySaving.value = false
  }
}

function addCoordRow() {
  coordRows.value.push({ conglomerate: '', system: '' })
}

function removeCoordRow(index: number) {
  coordRows.value.splice(index, 1)
}

// 主责事业群变化时清空已选主责系统，避免保留不属于新事业群的系统
function onOwnerConglomerateChange() {
  ;(legacyForm as any).owner_system = ''
}

async function removeLegacy(row: LegacyRequirement) {
  try {
    await ElMessageBox.confirm(`确认删除存量需求「${row.requirement_name}」？`, '删除确认', { type: 'warning' })
    await deleteLegacy(row.id)
    ElMessage.success('存量需求已删除')
    await loadLegacy()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '存量需求删除失败'))
  }
}

// 需求变更：独立"需求变更"按钮弹窗维护，不在各阶段字段中展示
const legacyChangeVisible = ref(false)
const legacyChangeSaving = ref(false)
const legacyChangeForm = reactive<Record<string, unknown>>({})

function openLegacyChange(row: LegacyRequirement | null) {
  const source = row || (legacyForm.id ? (legacyForm as unknown as LegacyRequirement) : null)
  if (!source || !source.id) return
  Object.keys(legacyChangeForm).forEach(key => delete legacyChangeForm[key])
  Object.assign(legacyChangeForm, {
    id: source.id,
    change_involved: (source as any).change_involved || '否',
    change_info: (source as any).change_info || '',
    change_review_conclusion: (source as any).change_review_conclusion || '',
    change_conclusion_status: (source as any).change_conclusion_status || '',
    change_remark: (source as any).change_remark || ''
  })
  legacyChangeVisible.value = true
}

// 是否禁用变更详情字段：未选择 or 选择了"否"时都禁用
function isChangeDetailDisabled() {
  return legacyChangeForm.change_involved !== '是'
}

async function saveLegacyChange() {
  if (!legacyChangeForm.id) return
  legacyChangeSaving.value = true
  try {
    await updateLegacy(Number(legacyChangeForm.id), {
      change_involved: legacyChangeForm.change_involved,
      change_info: legacyChangeForm.change_info,
      change_review_conclusion: legacyChangeForm.change_review_conclusion,
      change_conclusion_status: legacyChangeForm.change_conclusion_status,
      change_remark: legacyChangeForm.change_remark
    })
    legacyChangeVisible.value = false
    ElMessage.success('需求变更信息已保存')
    await loadLegacy()
    // 若编辑抽屉正打开，同步最新变更字段，避免再次打开时展示旧值
    if (legacyForm.id === legacyChangeForm.id) {
      for (const field of CHANGE_FIELDS) (legacyForm as any)[field] = legacyChangeForm[field]
    }
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '需求变更保存失败'))
  } finally {
    legacyChangeSaving.value = false
  }
}

// 预览
const legacyPreviewVisible = ref(false)
const legacyPreviewRow = ref<LegacyRequirement | null>(null)
const legacyPreviewActiveTab = ref('')

// 预览：通用字段置顶 + 阶段字段按 tab 切换
const legacyPreviewCommonFields = computed(() => {
  const row = legacyPreviewRow.value
  if (!row) return []
  const fields: Array<{ key: string; label: string; value: string; span: number }> = []
  for (const f of COMMON_FIELDS) {
    const raw = (row as any)[f.key]
    if (raw === null || raw === undefined || raw === '') continue
    let value = String(raw)
    fields.push({ key: f.key, label: f.label, value, span: COMMON_FULL_WIDTH_KEYS.has(f.key) ? 2 : 1 })
  }
  return fields
})

const legacyPreviewStageGroups = computed(() => {
  const row = legacyPreviewRow.value
  if (!row) return []
  const groups: Array<{ title: string; fields: Array<{ key: string; label: string; value: string; span: number }> }> = []
  // 阶段状态行
  const stageStatusFields: Array<{ key: string; label: string; value: string; span: number }> = []
  for (const stage of LEGACY_STAGE_ORDER) {
    const statusKey = STAGE_FIELD_MAP[stage]
    const raw = (row as any)[statusKey]
    if (raw !== null && raw !== undefined && raw !== '') {
      stageStatusFields.push({ key: statusKey, label: `${stageLabel(stage)}状态`, value: String(raw), span: 1 })
    }
  }
  if (stageStatusFields.length > 0) groups.push({ title: '阶段状态', fields: stageStatusFields })
  // 各阶段字段
  for (const stage of LEGACY_STAGE_ORDER) {
    const stageFields = STAGE_FIELD_GROUPS[stage] || []
    const fields: Array<{ key: string; label: string; value: string; span: number }> = []
    for (const f of stageFields) {
      const raw = (row as any)[f.key]
      const value = raw === null || raw === undefined || String(raw).trim() === '' ? '-' : String(raw)
      fields.push({ key: f.key, label: f.label, value, span: isTextArea(f.key) || f.key === 'content_summary' || f.key === 'regulation_desc' ? 2 : 1 })
    }
    groups.push({ title: `${stageLabel(stage)}阶段`, fields })
  }
  // 需求变更信息独立展示（不属于任何阶段）
  const changeFields: Array<{ key: string; label: string; value: string; span: number }> = []
  for (const f of CHANGE_FIELDS) {
    const raw = (row as any)[f]
    if (raw === null || raw === undefined || raw === '') continue
    changeFields.push({ key: f, label: label(f), value: String(raw), span: f === 'change_info' || f === 'change_remark' ? 2 : 1 })
  }
  if (changeFields.length > 0) {
    groups.push({ title: '需求变更', fields: changeFields })
  }
  return groups
})

function openLegacyPreview(row: LegacyRequirement) {
  legacyPreviewRow.value = row
  legacyPreviewActiveTab.value = ''
  legacyPreviewVisible.value = true
  // 首个 tab
  nextTick(() => {
    if (legacyPreviewStageGroups.value.length > 0 && !legacyPreviewActiveTab.value) {
      legacyPreviewActiveTab.value = legacyPreviewStageGroups.value[0].title
    }
  })
}

// 主责/协同系统明细弹窗：点击列表系统单元格查看系统清单（req_system）信息
const legacySystemVisible = ref(false)
const legacySystemTarget = ref<LegacyRequirement | null>(null)
const legacySystemRows = computed<Array<{
  role: string; code: string; name: string; conglomerate: string; logical: string; component: string
  domain: string; productView: string; launchPoint: string; sourceType: string; raw: string
}>>(() => {
  const row = legacySystemTarget.value
  if (!row) return []
  const entries: Array<{
    role: string; code: string; name: string; conglomerate: string; logical: string; component: string
    domain: string; productView: string; launchPoint: string; sourceType: string; raw: string
  }> = []
  const push = (role: string, raw: unknown) => {
    for (const text of splitEntries(raw)) {
      if (!text) continue
      const code = systemCodeOf(text)
      const system = systems.value.find(s => s.system_code === code) || null
      entries.push({
        role, code,
        name: system?.system_name || text,
        conglomerate: system?.conglomerate || '',
        logical: system ? `${system.logical_subsystem_code || ''} ${system.logical_subsystem_name || ''}`.trim() : '',
        component: system ? `${system.business_component_code || ''} ${system.business_component_name || ''}`.trim() : '',
        domain: system?.business_domain || '',
        productView: system?.product_view || '',
        launchPoint: system?.launch_point || '',
        sourceType: system?.source_type || '',
        raw: text
      })
    }
  }
  push('主责系统', row.owner_system)
  push('协同系统', row.coord_system)
  return entries
})

function openLegacySystems(row: LegacyRequirement) {
  legacySystemTarget.value = row
  legacySystemVisible.value = true
}

// 差异预览（新建项目差异点）
const diffPreviewVisible = ref(false)
const diffPreviewRow = ref<RequirementDifference | null>(null)
const diffPreviewFields = computed(() => {
  const row = diffPreviewRow.value
  if (!row) return []
  const labels = fieldLabels.value
  const entries: Array<{ label: string; value: string }> = []
  for (const [key, raw] of Object.entries(row)) {
    if (['id', 'tenant_id', 'deleted', 'created_by', 'updated_by', 'created_at', 'updated_at', 'import_batch_id', 'baseline_id', 'workflow_instance_id', 'reviewed_by', 'reviewed_at'].includes(key)) continue
    if (raw === null || raw === undefined || raw === '') continue
    let value = String(raw)
    if (key === 'source') {
      value = value === 'IMPORT' ? '导入' : '在线填写'
    } else if (key === 'system_id') {
      const sys = systems.value.find(s => s.id === Number(raw))
      value = sys ? `${sys.system_code} ${sys.system_name}` : value
    } else if (key === 'is_special') {
      value = value === '是' || value === 'true' ? '是' : '否'
    }
    entries.push({ label: labels[key] || key, value })
  }
  return entries
})

function openDiffPreview(row: RequirementDifference) {
  diffPreviewRow.value = row
  diffPreviewVisible.value = true
}

const stageDialogVisible = ref(false)
const stageSaving = ref(false)
const stageTarget = ref<LegacyRequirement | null>(null)
const stageForm = reactive<{ stage: string; action: 'START' | 'COMPLETE' | 'BACK'; comment: string }>({ stage: 'PROPOSE', action: 'START', comment: '' })
const stageLogs = ref<StageLogRow[]>([])
const stageLogDialogVisible = ref(false)
const stageLogTarget = ref<LegacyRequirement | null>(null)

// 阶段中文 + 6 阶段顺序
const LEGACY_STAGE_ORDER = ['PROPOSE', 'DOCKING', 'WORKLOAD', 'PROJECT', 'SOFT', 'LAUNCH']
const stageStatusTagType = (s: string): 'info' | 'warning' | 'primary' | 'success' => {
  if (s === '未开始') return 'info'
  if (s === '进行中') return 'primary'
  if (s === '已完成') return 'success'
  return 'info'
}

// 阶段子状态列名映射（中文 → 后端字段名）
const STAGE_FIELD_MAP: Record<string, string> = {
  PROPOSE: 'propose_stage_status', DOCKING: 'docking_stage_status', WORKLOAD: 'workload_stage_status',
  PROJECT: 'project_stage_status', SOFT: 'soft_stage_status', LAUNCH: 'launch_stage_status'
}

// 阶段 → 字段分组（编辑时仅展示 current_stage + 通用段）
// 标签严格按 Excel 列头完整文字（含括号说明）
const STAGE_FIELD_GROUPS: Record<string, Array<{ key: string; label: string; placeholder?: string }>> = {
  PROPOSE: [
    { key: 'legacy_doc_name', label: '业需文档名称', placeholder: '【蒙商银行】业务需求说明书XXX项目-业务小组-YYYY-MM-DD' },
    { key: 'requirement_no', label: '需求编号（来自蒙商维普系统）', placeholder: 'JG-W0332C-240507-001' },
    { key: 'requirement_name', label: '需求名称', placeholder: 'ATM渠道跨行转账类交易上送完整对手方姓名' },
    { key: 'content_summary', label: '需求内容简述', placeholder: '简要描述需求内容' },
    { key: 'propose_dept', label: '需求提出部门', placeholder: '运营管理部' },
    { key: 'proposer', label: '需求提出人及电话', placeholder: '谢斌 13800000000' },
    { key: 'monshang_ba', label: '蒙商BA', placeholder: '' },
    { key: 'monshang_architect', label: '蒙商架构', placeholder: '' },
    { key: 'expected_launch_date', label: '业务期望上线时间', placeholder: '2024年6月底前' },
    { key: 'regulator', label: '外部监管单位（监管需求必填）', placeholder: '中国银联股份有限公司' },
    { key: 'regulation_doc_no', label: '监管文件名称+文号（监管需求必填）', placeholder: '《关于开展ATM渠道跨行转账类交易规范性改造的函》' },
    { key: 'regulation_desc', label: '监管文件内容描述（监管需求必填）', placeholder: '描述监管文件核心要求...' },
    { key: 'regulation_launch_date', label: '监管要求上线时间（监管需求必填）', placeholder: '2024年6月底前' }
  ],
  DOCKING: [
    { key: 'requirement_received_date', label: '业需入手日', placeholder: '5月22日' },
    { key: 'requirement_type', label: '需求类型（监管需求/业务需求/技术需求）' },
    { key: 'regulation_category', label: '监管分类（监管需求必填：国家级监管/地方级监管/处罚整改）' },
    { key: 'business_group', label: '业务组（原六小组）', placeholder: '渠道运营小组' },
    { key: 'sub_group', label: '分组', placeholder: '支付结算组' },
    { key: 'jinke_contact', label: '金科对接人及电话（业务统筹组）', placeholder: '朱琳 13800000000' },
    { key: 'need_jinke_arch_decision', label: '是否需要金科架构决策' },
    { key: 'jinke_architect', label: '金科架构人员', placeholder: '瞿真' },
    { key: 'ba_review_date', label: '业需评审完成日', placeholder: '6月11日' }
  ],
  WORKLOAD: [
    { key: 'workload_date', label: '工作量评估完成日', placeholder: '不涉及' }
  ],
  PROJECT: [
    { key: 'finance_project_date', label: '财务立项完成日（任务书）', placeholder: '不涉及' }
  ],
  SOFT: [
    { key: 'soft_doc_name', label: '软需文档名称', placeholder: '附件3：【蒙商银行】需求规格说明书XXX-V0.3(发布稿)' },
    { key: 'owner_conglomerate', label: '主责事业群（金科事业群/蒙商保留）', placeholder: '上海事业群' },
    { key: 'owner_system', label: '主责物理子系统编号+名称 示例：W05810+现金管理', placeholder: 'W0332C+银联CUPS业务子系统' },
    { key: 'owner_contact', label: '主责项目组联系人及电话', placeholder: '瞿真 13800000000' },
    { key: 'involve_cooperation', label: '是否涉及金科引入组件协同（是/否）' },
    { key: 'coord_conglomerate', label: '协同事业群 示例：1.XX事业群 2.XX事业群 3.保留项目组', placeholder: '成都事业群' },
    { key: 'coord_system', label: '协同系统名称 示例：1.W0101Z+对公资金证明 2.XX编号+XX系统名称', placeholder: 'WP106A+ATM自助渠道' },
    { key: 'soft_submit_date', label: '软需提交日', placeholder: '5月23日' },
    { key: 'soft_review_date', label: '软需评审完成日', placeholder: '6月11日' }
  ],
  LAUNCH: [
    { key: 'planned_launch_date', label: '计划上线时间', placeholder: '6月27日' },
    { key: 'actual_launch_date', label: '实际上线时间', placeholder: '6月27日' },
    { key: 'launch_mode', label: '上线形式（常规版本/紧急版本）' }
  ]
}

// 通用字段：需求状态 + 备注（在每条数据的最后两列/表单底部展示，不属于任何阶段）
const COMMON_FIELDS: Array<{ key: string; label: string; placeholder?: string }> = [
  { key: 'requirement_status', label: '需求状态' },
  { key: 'remark', label: '备注', placeholder: '【0611】713前投产，纳入建设合同' }
]

// 通用字段是否长文本（占2列）
const COMMON_FULL_WIDTH_KEYS = new Set(['remark'])

// 需求变更字段（独立"需求变更"按钮弹窗维护，不在阶段字段中展示）
const CHANGE_FIELDS = ['change_involved', 'change_info', 'change_review_conclusion', 'change_conclusion_status', 'change_remark']

// 编辑时选中查看/维护的阶段（默认等于 current_stage；点击历史阶段节点时可临时切换只读浏览）
const legacyViewStage = ref<string>('')
// 打开表单时重置为当前阶段；当 current_stage 变化时保持同步
watch(() => (legacyForm as any).current_stage as string | undefined, (v) => {
  legacyViewStage.value = v || 'PROPOSE'
}, { immediate: true })
function switchViewStage(stage: string) {
  legacyViewStage.value = stage
}
// 是否在"当前阶段"（非当前阶段只读，防止误编辑到非受控范围字段）
function isViewStageActive() {
  const cur = (legacyForm as any).current_stage as string | undefined || 'PROPOSE'
  return legacyViewStage.value === cur
}
// 编辑时按选中的 legacyViewStage 展示字段（当前阶段可编辑，其它阶段只读）
const currentStageFields = computed(() => {
  return STAGE_FIELD_GROUPS[legacyViewStage.value || 'PROPOSE'] || []
})

// 字段类型判断（用于动态渲染表单控件）
const DATE_FIELDS = new Set([
  'expected_launch_date', 'regulation_launch_date', 'requirement_received_date',
  'ba_review_date', 'workload_date', 'finance_project_date', 'soft_submit_date',
  'soft_review_date', 'planned_launch_date', 'actual_launch_date'
])
const TEXTAREA_FIELDS = new Set(['content_summary', 'regulation_desc'])
// 软需阶段系统确认字段：主责/协同系统由专用联动控件维护，不在通用循环里渲染
const SOFT_SYSTEM_FIELDS = new Set(['owner_conglomerate', 'owner_system', 'coord_conglomerate', 'coord_system'])
const ENUM_FIELD_MAP: Record<string, keyof typeof options.value> = {
  requirement_type: 'requirementTypes',
  regulation_category: 'regulationCategories',
  need_jinke_arch_decision: 'yesNo',
  involve_cooperation: 'yesNo',
  launch_mode: 'launchModes',
  change_involved: 'yesNo',
  change_review_conclusion: 'changeReviewConclusions',
  change_conclusion_status: 'changeConclusionStatuses'
}

function isTextField(key: string) { return !DATE_FIELDS.has(key) && !ENUM_FIELD_MAP[key] && !SOFT_SYSTEM_FIELDS.has(key) }
function isDateField(key: string) { return DATE_FIELDS.has(key) }
function isEnumField(key: string) { return !!ENUM_FIELD_MAP[key] }
function isTextArea(key: string) { return TEXTAREA_FIELDS.has(key) }
function getOptionsForField(key: string) {
  const optKey = ENUM_FIELD_MAP[key]
  return optKey ? (options.value[optKey] as string[] || []) : []
}

// 核心标识字段：保存与阶段推进强校验；阶段业务字段不强卡流转
const CORE_REQUIRED_FIELDS = new Set(['requirement_no', 'requirement_name', 'business_group'])

function isStageRequired(key: string) {
  return CORE_REQUIRED_FIELDS.has(key)
}
function isRequiredField(key: string) {
  return CORE_REQUIRED_FIELDS.has(key)
}

// 当前选中阶段的子状态（未开始/进行中/已完成）
const currentStageSubStatus = computed<string>(() => {
  const row = stageTarget.value
  if (!row) return '未开始'
  const field = STAGE_FIELD_MAP[stageForm.stage]
  return (row[field] as string) || '未开始'
})

// 动作智能过滤：按当前阶段子状态决定可选项
const availableActions = computed<Array<{ value: 'START' | 'COMPLETE' | 'BACK'; label: string; disabled?: boolean; reason?: string }>>(() => {
  const s = currentStageSubStatus.value
  if (s === '未开始') {
    return [
      { value: 'START', label: '启动（未开始→进行中）' },
      { value: 'COMPLETE', label: '完成（进行中→已完成）', disabled: true, reason: '未开始不可完成' },
      { value: 'BACK', label: '回退（进行中→未开始）', disabled: true, reason: '未开始无需回退' }
    ]
  }
  if (s === '进行中') {
    return [
      { value: 'START', label: '启动（未开始→进行中）', disabled: true, reason: '已启动' },
      { value: 'COMPLETE', label: '完成（进行中→已完成）' },
      { value: 'BACK', label: '回退（进行中→未开始）' }
    ]
  }
  if (s === '已完成') {
    return [
      { value: 'START', label: '启动（未开始→进行中）', disabled: true, reason: '阶段已完成' },
      { value: 'COMPLETE', label: '完成（进行中→已完成）', disabled: true, reason: '阶段已完成' },
      { value: 'BACK', label: '回退（进行中→未开始）', disabled: true, reason: '已完成不可回退' }
    ]
  }
  return []
})

// 二维联动状态映射（与后端 RequirementEnums.LEGACY_STAGE_ACTION_TO_REQ_STATUS 对齐）
const STAGE_ACTION_TO_REQ_STATUS: Record<string, string> = {
  'PROPOSE:START': '需求分析', 'PROPOSE:COMPLETE': '业需修订',
  'DOCKING:START': '业需修订', 'DOCKING:COMPLETE': '业需评审通过',
  'WORKLOAD:START': '业需评审通过', 'WORKLOAD:COMPLETE': '业需评审通过',
  'PROJECT:START': '立项中', 'PROJECT:COMPLETE': '软需编制',
  'SOFT:START': '软需编制', 'SOFT:COMPLETE': '软需评审通过',
  'LAUNCH:START': '软需评审通过', 'LAUNCH:COMPLETE': '已投产',
  'DOCKING:BACK': '需求分析', 'WORKLOAD:BACK': '业需修订',
  'PROJECT:BACK': '业需评审通过', 'SOFT:BACK': '立项中', 'LAUNCH:BACK': '软需编制'
}

// 联动预览：选定 stage + action 后预测 requirement_status 变化
const linkedRequirementStatus = computed<string | null>(() => {
  const row = stageTarget.value
  if (!row) return null
  const key = `${stageForm.stage}:${stageForm.action}`
  return STAGE_ACTION_TO_REQ_STATUS[key] || null
})

async function openStage(row: LegacyRequirement) {
  stageTarget.value = row
  stageForm.stage = row.current_stage
  stageForm.action = 'START'
  stageForm.comment = ''
  stageDialogVisible.value = true
  // 按子状态自动选择首个可用动作
  const first = availableActions.value.find(a => !a.disabled)
  if (first) stageForm.action = first.value
}

async function saveStage() {
  if (!stageTarget.value) return
  stageSaving.value = true
  try {
    const result = (await stageTransition(stageTarget.value.id, stageForm)).data.data as unknown
    const reminder = result as { confirmed: boolean; missingFields: string[] } | null
    if (reminder && reminder.confirmed === false && Array.isArray(reminder.missingFields) && reminder.missingFields.length > 0) {
      // 阶段业务字段缺失：弹窗提醒，由用户确认是否继续推进
      try {
        await ElMessageBox.confirm(
          `以下阶段字段尚未填写：\n${reminder.missingFields.map(f => `· ${f}`).join('\n')}\n\n仍要继续推进吗？`,
          '阶段字段未填写完整',
          { confirmButtonText: '继续推进', cancelButtonText: '取消', type: 'warning' }
        )
      } catch {
        return
      }
      await stageTransition(stageTarget.value.id, { ...stageForm, ignoreMissingStageFields: true })
    }
    stageDialogVisible.value = false
    ElMessage.success('阶段已推进，状态已更新并记录阶段日志')
    await loadLegacy()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '阶段推进失败'))
  } finally {
    stageSaving.value = false
  }
}

async function showStageLogs(row: LegacyRequirement) {
  stageLogTarget.value = row
  try {
    stageLogs.value = (await legacyStageLogs(row.id)).data.data
    stageLogDialogVisible.value = true
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '阶段记录加载失败'))
  }
}

// ---------------- 系统清单 ----------------
const systems = ref<RequirementSystem[]>([])
const systemsLoading = ref(false)
const systemFormVisible = ref(false)
const systemSaving = ref(false)
const systemForm = reactive<Record<string, unknown>>({})

async function loadSystems() {
  systemsLoading.value = true
  try {
    systems.value = (await listSystems()).data.data
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '系统清单加载失败'))
  } finally {
    systemsLoading.value = false
  }
}

function openSystemCreate() {
  Object.keys(systemForm).forEach(key => delete systemForm[key])
  Object.assign(systemForm, { system_code: '', system_name: '', english_name: '', conglomerate: '', status: '启用', logical_subsystem_code: '', logical_subsystem_name: '', business_component_code: '', business_component_name: '', business_domain: '', product_view: '', launch_point: '', category: '', introduction: '', disaster_level: '', source_type: '' })
  systemFormVisible.value = true
}

function openSystemEdit(row: RequirementSystem) {
  Object.keys(systemForm).forEach(key => delete systemForm[key])
  Object.assign(systemForm, { id: row.id }, row)
  systemFormVisible.value = true
}

async function saveSystem() {
  systemSaving.value = true
  try {
    if (systemForm.id) {
      await updateSystem(Number(systemForm.id), systemForm)
    } else {
      await createSystem(systemForm)
    }
    systemFormVisible.value = false
    ElMessage.success('系统已保存')
    await loadSystems()
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '系统保存失败'))
  } finally {
    systemSaving.value = false
  }
}

async function removeSystem(row: RequirementSystem) {
  try {
    await ElMessageBox.confirm(`确认删除系统「${row.system_name}」？`, '删除确认', { type: 'warning' })
    await deleteSystem(row.id)
    ElMessage.success('系统已删除')
    await loadSystems()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(apiErrorMessage(error, '系统删除失败'))
  }
}

function stageLabel(stage: string) {
  const map = options.value.legacyStageLabelMap as unknown as Record<string, string> | undefined
  return map?.[stage] || stage
}

function canEditDiff(row: RequirementDifference) {
  return row.review_status === '待评审' || row.review_status === '已退回'
}
</script>

<template>
  <section class="requirements-page">
    <el-tabs :model-value="section" class="requirements-tabs" @tab-change="(name: string) => router.replace('/requirements/' + name)">
      <el-tab-pane label="新建项目" name="new-project" />
      <el-tab-pane label="存量项目" name="legacy" />
      <el-tab-pane label="系统清单" name="systems" />
    </el-tabs>

    <!-- 新建项目 -->
    <div v-if="section === 'new-project'" class="req-section">
      <UiToolbar>
        <el-select v-model="selectedProjectId" filterable placeholder="选择项目" class="req-project-select" @change="onProjectChange">
          <el-option v-for="project in projects" :key="project.id" :label="`${project.project_code} ${project.project_name}`" :value="project.id" />
        </el-select>
        <span v-if="selectedProject" class="muted">已评审 {{ selectedProject.reviewed_count || 0 }} / {{ selectedProject.difference_count || 0 }}</span>
        <template #actions>
          <el-button @click="loadProjects"><el-icon><Refresh /></el-icon>刷新</el-button>
          <el-button type="primary" @click="openProjectCreate"><el-icon><Plus /></el-icon>新建项目</el-button>
          <el-dropdown v-if="selectedProject" trigger="click" @command="projectCommand">
            <el-button>项目操作<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑项目</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除项目</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="selectedProject" @click="openMembers(selectedProject)">成员</el-button>
          <el-button v-if="selectedProject" @click="openBaselines()">基线</el-button>
          <el-button v-if="selectedProject" type="success" @click="formBaseline">形成基线</el-button>
        </template>
      </UiToolbar>

      <template v-if="selectedProject">
        <UiToolbar>
          <span class="muted">差异清单（{{ differencesTotal }} 条）</span>
          <template #actions>
            <el-select v-model="diffFilters.reviewStatus" placeholder="差异状态" clearable style="width: 130px" @change="loadDifferences">
              <el-option v-for="item in options.reviewStatuses || []" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="diffFilters.devStatus" placeholder="开发状态" clearable style="width: 130px" @change="loadDifferences">
              <el-option v-for="item in options.devStatuses || []" :key="item" :label="item" :value="item" />
            </el-select>
            <el-input v-model="diffFilters.keyword" placeholder="名称/需求编号" clearable style="width: 180px" @keyup.enter="loadDifferences" @clear="loadDifferences" />
            <el-button @click="loadDifferences"><el-icon><Refresh /></el-icon>查询</el-button>
            <el-button @click="downloadTemplate('DIFF')"><el-icon><Download /></el-icon>模板下载</el-button>
            <el-button @click="openImport"><el-icon><UploadFilled /></el-icon>导入</el-button>
            <el-button type="primary" @click="openDiffCreate"><el-icon><Plus /></el-icon>新增差异</el-button>
            <el-button type="success" @click="formBaseline">形成基线</el-button>
          </template>
        </UiToolbar>
        <UiDataTable :data="differences" :loading="differencesLoading" row-key="id" border>
          <el-table-column prop="seq_no" label="序号" width="60" />
          <el-table-column label="需求编号" min-width="130">
            <template #default="scope">
              <el-button link type="primary" class="req-link-cell" @click="openDiffPreview(scope.row)">{{ scope.row.requirement_no || '-' }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column prop="difference_type" label="差异类型" min-width="150" show-overflow-tooltip />
          <el-table-column prop="business_group" label="业务组" width="100" />
          <el-table-column label="差异状态" width="110"><template #default="scope"><UiStatusTag :value="scope.row.review_status" /></template></el-table-column>
          <el-table-column label="开发状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.dev_status" /></template></el-table-column>
          <el-table-column label="测试状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.test_status" /></template></el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="scope">
              <div class="req-table-actions">
                <el-button link type="primary" @click="openChangeLogs('NEW_PROJECT_DIFF', scope.row.id, `修改记录：${scope.row.name}`)"><el-icon><Clock /></el-icon>修改记录</el-button>
                <el-button v-if="scope.row.workflow_instance_id" link type="primary" @click="openApprovalLogs(scope.row)"><el-icon><Tickets /></el-icon>审批记录</el-button>
                <el-button v-if="scope.row.review_status === '待评审' || scope.row.review_status === '已退回'" link type="warning" @click="submitDifferenceReview(scope.row)"><el-icon><Promotion /></el-icon>提交评审</el-button>
                <el-button v-if="scope.row.review_status === '评审中' || scope.row.review_status === '已退回'" link type="info" @click="cancelDifferenceReview(scope.row)" title="撤销评审流程，回到待评审后可重新编辑提交"><el-icon><RefreshRight /></el-icon>撤销评审</el-button>
                <el-dropdown v-if="canEditDiff(scope.row)" @command="(command: string) => command === 'edit' ? openDiffEdit(scope.row) : removeDifference(scope.row)">
                  <el-button link type="info"><el-icon><MoreFilled /></el-icon>更多</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit"><el-icon><Edit /></el-icon>编辑</el-dropdown-item>
                      <el-dropdown-item command="delete" divided><el-icon><Delete /></el-icon>删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </UiDataTable>
        <UiPagination v-model:page="differencePage" v-model:page-size="differenceSize" :total="differencesTotal" @update:page="loadDifferences" @update:page-size="loadDifferences" />
      </template>
      <UiEmptyState v-else title="暂无项目" description="先创建一个新建项目，再维护需求差异清单。" />
    </div>

    <!-- 存量项目 -->
    <div v-if="section === 'legacy'" class="req-section">
      <UiToolbar>
        <el-input v-model="legacyFilters.keyword" placeholder="需求名称/编号" clearable style="width: 200px" @keyup.enter="loadLegacy" @clear="loadLegacy" />
        <el-input v-model="legacyFilters.businessGroup" placeholder="业务组" clearable style="width: 150px" @keyup.enter="loadLegacy" @clear="loadLegacy" />
        <el-select v-model="legacyFilters.stage" placeholder="当前阶段" clearable style="width: 150px" @change="loadLegacy"><el-option v-for="stage in options.legacyStages || []" :key="stage" :label="stageLabel(stage)" :value="stage" /></el-select>
        <template #actions>
          <el-button @click="loadLegacy"><el-icon><Refresh /></el-icon>查询</el-button>
          <el-button @click="downloadTemplate('LEGACY')"><el-icon><Download /></el-icon>模板下载</el-button>
          <el-button type="primary" @click="openLegacyCreate"><el-icon><Plus /></el-icon>新增存量需求</el-button>
        </template>
      </UiToolbar>
      <UiDataTable :data="legacyRows" :loading="legacyLoading" row-key="id" border>
        <el-table-column label="需求编号" min-width="180">
          <template #default="scope">
            <el-button link type="primary" class="req-link-cell" @click="openLegacyPreview(scope.row)">{{ scope.row.requirement_no || '-' }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="requirement_name" label="需求名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="business_group" label="业务组" width="110" />
        <el-table-column label="当前阶段" width="120"><template #default="scope"><UiStatusTag :value="stageLabel(scope.row.current_stage)" /></template></el-table-column>
        <el-table-column label="主责系统" min-width="170" show-overflow-tooltip>
          <template #default="scope">
            <el-button v-if="scope.row.owner_system" link type="primary" @click="openLegacySystems(scope.row)">{{ scope.row.owner_system }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="协同系统" min-width="170" show-overflow-tooltip>
          <template #default="scope">
            <el-button v-if="scope.row.coord_system" link type="primary" @click="openLegacySystems(scope.row)">{{ scope.row.coord_system }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- 需求状态及备注：每条数据的最后两列 -->
        <el-table-column label="需求状态" width="120"><template #default="scope"><span>{{ scope.row.requirement_status || '-' }}</span></template></el-table-column>
        <el-table-column label="备注" min-width="180" show-overflow-tooltip><template #default="scope"><span>{{ scope.row.remark || '-' }}</span></template></el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="scope">
            <div class="req-table-actions">
              <el-button link type="primary" @click="openStage(scope.row)"><el-icon><Promotion /></el-icon>阶段推进</el-button>
              <el-button link type="warning" @click="openLegacyChange(scope.row)"><el-icon><RefreshRight /></el-icon>需求变更</el-button>
              <el-button link type="primary" @click="openChangeLogs('LEGACY_REQUIREMENT', scope.row.id, `修改记录：${scope.row.requirement_name}`)"><el-icon><Clock /></el-icon>修改记录</el-button>
              <el-dropdown @command="(command: string) => command === 'stage-logs' ? showStageLogs(scope.row) : command === 'edit' ? openLegacyEdit(scope.row) : removeLegacy(scope.row)">
                <el-button link type="info"><el-icon><MoreFilled /></el-icon>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="stage-logs"><el-icon><Tickets /></el-icon>阶段记录</el-dropdown-item>
                    <el-dropdown-item command="edit"><el-icon><Edit /></el-icon>编辑</el-dropdown-item>
                    <el-dropdown-item command="delete" divided><el-icon><Delete /></el-icon>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>
      <UiPagination v-model:page="legacyPage" v-model:page-size="legacySize" :total="legacyTotal" @update:page="loadLegacy" @update:page-size="loadLegacy" />

      <!-- 需求预览 -->
      <el-dialog v-model="legacyPreviewVisible" :title="`需求预览：${legacyPreviewRow?.requirement_no || ''}`" width="min(900px, calc(100vw - 24px))" top="6vh">
        <div v-if="legacyPreviewRow">
          <div class="req-preview-header">
            <span class="req-preview-title">{{ legacyPreviewRow.requirement_name }}</span>
            <el-tag size="small">{{ stageLabel(legacyPreviewRow.current_stage) }}</el-tag>
            <el-tag v-if="legacyPreviewRow.requirement_status" size="small" type="info">{{ legacyPreviewRow.requirement_status }}</el-tag>
            <el-tag size="small" :type="legacyPreviewRow.source === 'IMPORT' ? 'warning' : 'success'">{{ legacyPreviewRow.source === 'IMPORT' ? '导入' : '在线填写' }}</el-tag>
          </div>
          <!-- 需求状态及备注（始终可见） -->
          <div v-if="legacyPreviewCommonFields.length" class="req-preview-common">
            <div class="req-section-title">需求状态及备注</div>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item v-for="f in legacyPreviewCommonFields" :key="f.key" :label="f.label" :span="f.span">
                <span class="req-preview-value">{{ f.value }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <!-- 阶段字段（tab 切换） -->
          <el-tabs v-if="legacyPreviewStageGroups.length" v-model="legacyPreviewActiveTab" type="border-card">
            <el-tab-pane v-for="g in legacyPreviewStageGroups" :key="g.title" :label="g.title" :name="g.title">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item v-for="f in g.fields" :key="f.key" :label="f.label" :span="f.span">
                  <span class="req-preview-value">{{ f.value }}</span>
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </div>
        <template #footer>
          <el-button @click="legacyPreviewVisible = false">关闭</el-button>
          <el-button type="primary" @click="legacyPreviewVisible = false; legacyPreviewRow && openLegacyEdit(legacyPreviewRow)">编辑</el-button>
        </template>
      </el-dialog>

      <!-- 主责/协同系统明细 -->
      <el-dialog v-model="legacySystemVisible" :title="`系统明细：${legacySystemTarget?.requirement_name || ''}`" width="min(860px, calc(100vw - 24px))">
        <el-table v-if="legacySystemRows.length" :data="legacySystemRows" border size="small">
          <el-table-column prop="role" label="角色" width="90" />
          <el-table-column label="系统编号" width="110"><template #default="scope">{{ scope.row.code || '-' }}</template></el-table-column>
          <el-table-column label="系统名称" min-width="150" show-overflow-tooltip><template #default="scope">{{ scope.row.name || '-' }}</template></el-table-column>
          <el-table-column label="事业群" width="105"><template #default="scope">{{ scope.row.conglomerate || '-' }}</template></el-table-column>
          <el-table-column label="所属逻辑子系统" min-width="140" show-overflow-tooltip><template #default="scope">{{ scope.row.logical || '-' }}</template></el-table-column>
          <el-table-column label="归属业务组件" min-width="140" show-overflow-tooltip><template #default="scope">{{ scope.row.component || '-' }}</template></el-table-column>
          <el-table-column label="业务领域" min-width="100" show-overflow-tooltip><template #default="scope">{{ scope.row.domain || '-' }}</template></el-table-column>
          <el-table-column label="产品视图" min-width="100" show-overflow-tooltip><template #default="scope">{{ scope.row.productView || '-' }}</template></el-table-column>
          <el-table-column label="投产点" width="85"><template #default="scope">{{ scope.row.launchPoint || '-' }}</template></el-table-column>
          <el-table-column label="引入/保留" width="90"><template #default="scope">{{ scope.row.sourceType || '-' }}</template></el-table-column>
        </el-table>
        <el-empty v-else description="尚未选择主责/协同系统" :image-size="80" />
        <template #footer>
          <el-button @click="legacySystemVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </div>

    <!-- 八大参数管理三级菜单：页面建设中占位 -->
    <div v-if="PARAM_SECTION_TITLES[String(section)]" class="req-section">
      <UiEmptyState :title="`${PARAM_SECTION_TITLES[String(section)]}`" description="页面建设中，敬请期待。" />
    </div>

    <!-- 系统清单 -->
    <div v-if="section === 'systems'" class="req-section">
      <UiToolbar>
        <span class="muted">系统清单主数据（新建项目与存量项目共用）</span>
        <template #actions>
          <el-button @click="loadSystems"><el-icon><Refresh /></el-icon>刷新</el-button>
          <el-button type="primary" @click="openSystemCreate"><el-icon><Plus /></el-icon>新增系统</el-button>
        </template>
      </UiToolbar>
      <UiDataTable :data="systems" :loading="systemsLoading" row-key="id" border>
        <el-table-column prop="system_code" label="系统编号" min-width="120" />
        <el-table-column prop="system_name" label="系统名称" min-width="200" />
        <el-table-column prop="english_name" label="英文简称" min-width="120" />
        <el-table-column prop="conglomerate" label="事业群" min-width="130" />
        <el-table-column prop="business_domain" label="业务领域" min-width="120" />
        <el-table-column prop="source_type" label="引入/保留" width="110" />
        <el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" /></template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="scope">
            <div class="req-table-actions">
              <el-button link type="primary" @click="openSystemEdit(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button>
              <el-dropdown @command="() => removeSystem(scope.row)">
                <el-button link type="info"><el-icon><MoreFilled /></el-icon>更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="delete"><el-icon><Delete /></el-icon>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </UiDataTable>
    </div>

    <!-- 项目表单 -->
    <UiFormDrawer v-model="projectFormVisible" :title="projectForm.id ? '编辑项目' : '新建项目'" :loading="projectSaving" width="min(560px, calc(100vw - 24px))" @submit="saveProject">
      <el-form label-position="top">
        <el-form-item label="项目编码" required><el-input v-model="projectForm.project_code" :disabled="Boolean(projectForm.id)" /></el-form-item>
        <el-form-item label="项目名称" required><el-input v-model="projectForm.project_name" /></el-form-item>
        <el-form-item label="项目类型"><el-select v-model="projectForm.project_type" style="width: 100%"><el-option v-for="item in options.projectTypes || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="启动时间"><el-date-picker v-model="projectForm.start_time" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="projectForm.status" style="width: 100%"><el-option v-for="item in options.projectStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="projectForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <!-- 成员管理 -->
    <el-dialog v-model="memberDialogVisible" title="项目成员" width="min(560px, calc(100vw - 24px))">
      <div class="req-member-row"><el-select v-model="memberUserId" placeholder="选择成员用户" filterable style="flex: 1"><el-option v-for="user in userOptions" :key="user.id" :label="`${user.display_name || user.username}（${user.username}）`" :value="user.id" /></el-select><el-button type="primary" @click="addMember">添加</el-button></div>
      <el-table :data="members" border size="small">
        <el-table-column prop="display_name" label="姓名" min-width="120" />
        <el-table-column prop="username" label="账号" min-width="140" />
        <el-table-column prop="member_role" label="角色" width="90" />
        <el-table-column label="操作" width="90"><template #default="scope"><el-button link type="danger" @click="removeMember(scope.row)">移除</el-button></template></el-table-column>
      </el-table>
    </el-dialog>

    <!-- 差异表单 -->
    <UiFormDrawer v-model="diffFormVisible" :title="diffForm.id ? '编辑差异' : '新增差异'" :loading="diffSaving" width="min(760px, calc(100vw - 24px))" @submit="saveDifference">
      <el-form label-position="top" class="req-form-grid">
        <el-form-item label="序号"><el-input v-model.number="diffForm.seq_no" type="number" /></el-form-item>
        <el-form-item label="事业群"><el-input v-model="diffForm.business_conglomerate" /></el-form-item>
        <el-form-item label="业务板块"><el-input v-model="diffForm.business_section" /></el-form-item>
        <el-form-item label="业务组" required><el-input v-model="diffForm.business_group" /></el-form-item>
        <el-form-item label="需求编号"><el-input v-model="diffForm.requirement_no" placeholder="组件物理子系统编号+三位序号，如 W01812-001" /></el-form-item>
        <el-form-item label="分类"><el-select v-model="diffForm.category" clearable style="width: 100%"><el-option v-for="item in options.categories || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="名称" required class="req-span-2"><el-input v-model="diffForm.name" /></el-form-item>
        <el-form-item label="涉及系统"><el-select v-model="diffForm.system_id" clearable filterable style="width: 100%"><el-option v-for="system in systems" :key="system.id" :label="`${system.system_code} ${system.system_name}`" :value="system.id" /></el-select></el-form-item>
        <el-form-item label="差异类型"><el-select v-model="diffForm.difference_type" clearable style="width: 100%"><el-option v-for="item in options.differenceTypes || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="适配方式"><el-select v-model="diffForm.adapt_mode" clearable style="width: 100%"><el-option v-for="item in options.adaptModes || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="处理状态"><el-select v-model="diffForm.handle_status" clearable style="width: 100%"><el-option v-for="item in options.handleStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="是否专题"><el-select v-model="diffForm.is_special" clearable style="width: 100%"><el-option v-for="item in options.yesNo || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="上升决策层级"><el-select v-model="diffForm.decision_level" clearable style="width: 100%"><el-option v-for="item in options.decisionLevels || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="开发状态"><el-select v-model="diffForm.dev_status" style="width: 100%"><el-option v-for="item in options.devStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="测试状态"><el-select v-model="diffForm.test_status" style="width: 100%"><el-option v-for="item in options.testStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="金科做法" class="req-span-2"><el-input v-model="diffForm.jinke_practice" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="蒙商作法" class="req-span-2"><el-input v-model="diffForm.monshang_practice" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="差异描述" class="req-span-2"><el-input v-model="diffForm.difference_desc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="解决方案" class="req-span-2"><el-input v-model="diffForm.solution" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="蒙商分析部门"><el-input v-model="diffForm.monshang_dept" /></el-form-item>
        <el-form-item label="蒙商分析人"><el-input v-model="diffForm.monshang_analyst" /></el-form-item>
        <el-form-item label="金科分析人"><el-input v-model="diffForm.jinke_analyst" /></el-form-item>
        <el-form-item label="协同组"><el-input v-model="diffForm.coord_group" /></el-form-item>
        <el-form-item label="决策结论" class="req-span-2"><el-input v-model="diffForm.decision_conclusion" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="蒙商确认部门"><el-input v-model="diffForm.monshang_confirm_dept" /></el-form-item>
        <el-form-item label="金科确认人"><el-input v-model="diffForm.jinke_confirmer" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <!-- 提交评审：选择审批人 -->
    <el-dialog v-model="submitReviewDialogVisible" title="提交评审" width="min(480px, calc(100vw - 24px))">
      <el-form label-position="top">
        <el-form-item v-if="submitReviewTarget" label="差异点">
          <span>{{ submitReviewTarget.name }}（{{ submitReviewTarget.requirement_no || '-' }}）</span>
        </el-form-item>
        <el-form-item label="审批人" required>
          <el-select v-model="submitReviewApprovers" multiple filterable placeholder="选择一位或多位审批人" style="width: 100%">
            <el-option v-for="item in submitReviewOptions" :key="item.id" :label="`${item.display_name}（${item.username}）`" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="submitReviewDialogVisible = false">取消</el-button><el-button type="primary" :loading="submitReviewSaving" @click="confirmSubmitReview">提交</el-button></template>
    </el-dialog>

    <!-- 基线列表 -->
    <el-dialog v-model="baselineDialogVisible" title="基线版本" width="min(720px, calc(100vw - 24px))">
      <el-table :data="baselines" border size="small">
        <el-table-column prop="baseline_no" label="基线版本号" min-width="200" />
        <el-table-column prop="baseline_name" label="基线名称" min-width="200" />
        <el-table-column prop="difference_count" label="差异数" width="90" />
        <el-table-column prop="created_at" label="形成时间" width="170" />
        <el-table-column label="操作" width="100"><template #default="scope"><el-button link type="primary" @click="showBaselineItems(scope.row)">明细</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!baselines.length" description="暂无基线" />
    </el-dialog>

    <el-dialog v-model="baselineDetailVisible" title="基线明细（快照）" width="min(720px, calc(100vw - 24px))">
      <el-table :data="baselineItems" border size="small">
        <el-table-column prop="difference_id" label="差异 ID" width="110" />
        <el-table-column label="快照" min-width="320"><template #default="scope"><code class="req-snapshot">{{ scope.row.snapshot_json }}</code></template></el-table-column>
        <el-table-column prop="created_at" label="纳入时间" width="170" />
      </el-table>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog v-model="importDialogVisible" title="Excel 导入（模板下载 → 校验 → 确认导入）" width="min(680px, calc(100vw - 24px))">
      <div class="req-import-box">
        <el-upload drag :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" :on-change="(file: any) => onImportFile(file.raw)">
          <el-icon :size="30"><UploadFilled /></el-icon>
          <div>拖拽或点击选择差异清单 Excel 文件</div>
          <template #tip><div class="el-upload__tip">仅支持 xlsx/xls，按标准模板逐行校验</div></template>
        </el-upload>
      </div>
      <div v-if="importReport" class="req-import-report">
        <p>总行数 {{ importReport.totalRows }}，成功 {{ importReport.successRows }}，错误 {{ importReport.errorRows }}</p>
        <el-table v-if="importReport.errors.length" :data="importReport.errors" border size="small">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column label="错误信息"><template #default="scope"><span v-for="message in scope.row.messages" :key="message" class="req-error-line">{{ message }}</span></template></el-table-column>
        </el-table>
        <el-alert v-else type="success" :closable="false" title="校验全部通过，可确认导入" />
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importReport || importReport.errorRows > 0" @click="confirmImportRows">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- 存量需求表单 -->
    <UiFormDrawer v-model="legacyFormVisible" :title="legacyForm.id ? '编辑存量需求' : '新增存量需求'" :loading="legacySaving" width="min(820px, calc(100vw - 24px))" @submit="saveLegacy">
      <!-- 阶段时序条（可点击切换查看各阶段；非当前阶段为只读） -->
      <div v-if="legacyForm.current_stage" class="req-stage-overview">
        <div class="req-stage-track">
          <div v-for="s in LEGACY_STAGE_ORDER" :key="s"
               class="req-stage-node"
               :class="{
                 'req-stage-active': s === legacyForm.current_stage,
                 'req-stage-viewing': s === legacyViewStage
               }"
               @click="switchViewStage(s)"
               role="button"
               :title="isViewStageActive() && s === legacyForm.current_stage ? `当前维护阶段：${stageLabel(s)}（可编辑）` : `点击查看${stageLabel(s)}阶段字段（只读）`">
            <span class="req-stage-name">{{ stageLabel(s) }}</span>
            <el-tag size="small" :type="stageStatusTagType(((legacyForm as any)[STAGE_FIELD_MAP[s]] as string) || '未开始')">{{ ((legacyForm as any)[STAGE_FIELD_MAP[s]] as string) || '未开始' }}</el-tag>
          </div>
        </div>
        <div class="req-stage-summary">
          当前维护阶段：<strong>{{ stageLabel(String((legacyForm as any).current_stage || 'PROPOSE')) }}</strong>
          <span class="req-stage-view-tag" v-if="!isViewStageActive()">&nbsp;· 正在查看 <u>{{ stageLabel(legacyViewStage) }}</u> 阶段历史字段（只读）· </span>
          <el-button v-if="!isViewStageActive()" link size="small" @click="switchViewStage(String((legacyForm as any).current_stage || 'PROPOSE'))">回到当前阶段</el-button>
        </div>
      </div>

      <!-- 查看阶段字段 + 通用信息（非 current_stage 时禁用） -->
      <el-form label-position="top" class="req-form-grid">
        <template v-if="currentStageFields.length">
          <div class="req-section-title d-flex align-items-center gap-8">
            <span>{{ stageLabel(legacyViewStage || 'PROPOSE') }}阶段字段</span>
            <el-tag v-if="!isViewStageActive()" size="small" type="info" effect="plain">历史阶段 · 只读</el-tag>
            <el-tag v-else size="small" type="success" effect="plain">当前维护</el-tag>
          </div>
          <template v-for="f in currentStageFields" :key="f.key">
            <template v-if="!SOFT_SYSTEM_FIELDS.has(f.key)">
              <el-form-item :label="f.label" :required="isStageRequired(f.key) && isViewStageActive()">
                <el-input v-if="isTextField(f.key)" v-model="(legacyForm as any)[f.key]" :type="isTextArea(f.key) ? 'textarea' : 'text'" :rows="isTextArea(f.key) ? 2 : undefined" :placeholder="f.placeholder" :disabled="!isViewStageActive()" />
                <el-date-picker v-else-if="isDateField(f.key)" v-model="(legacyForm as any)[f.key]" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled="!isViewStageActive()" />
                <el-select v-else-if="isEnumField(f.key)" v-model="(legacyForm as any)[f.key]" :clearable="!isRequiredField(f.key) || !isViewStageActive()" style="width: 100%" :disabled="!isViewStageActive()">
                  <el-option v-for="item in getOptionsForField(f.key)" :key="item" :label="item" :value="item" />
                </el-select>
                <el-input v-else v-model="(legacyForm as any)[f.key]" :placeholder="f.placeholder" :disabled="!isViewStageActive()" />
              </el-form-item>
            </template>
          </template>
        </template>

        <!-- 软需阶段系统确认：先选事业群，再选系统（数据源为需求管理系统清单）；协同系统可多个 -->
        <div v-if="legacyViewStage === 'SOFT'" class="req-span-2">
          <div class="req-section-title">软需系统确认</div>
          <div class="req-system-row">
            <div class="req-system-field">
              <el-form-item label="主责事业群" :required="isViewStageActive()">
                <el-select v-model="(legacyForm as any).owner_conglomerate" filterable clearable style="width: 100%" :disabled="!isViewStageActive()" placeholder="先选择事业群" @change="onOwnerConglomerateChange">
                  <el-option v-for="g in systemConglomerates" :key="g" :label="g" :value="g" />
                </el-select>
              </el-form-item>
            </div>
            <div class="req-system-field">
              <el-form-item label="主责系统" :required="isViewStageActive()">
                <el-select v-model="(legacyForm as any).owner_system" filterable clearable style="width: 100%" :disabled="!isViewStageActive() || !(legacyForm as any).owner_conglomerate" placeholder="再选择系统">
                  <el-option v-for="s in systemsByConglomerate(String((legacyForm as any).owner_conglomerate || ''))" :key="s.system_code" :label="`${s.system_code} ${s.system_name}`" :value="systemValue(s)" />
                </el-select>
              </el-form-item>
            </div>
          </div>
          <div class="req-section-title">协同系统（可多个）</div>
          <div v-for="(row, index) in coordRows" :key="index" class="req-system-row">
            <div class="req-system-field">
              <el-form-item :label="`协同事业群 ${index + 1}`">
                <el-select v-model="row.conglomerate" filterable clearable style="width: 100%" :disabled="!isViewStageActive()" placeholder="先选择事业群">
                  <el-option v-for="g in systemConglomerates" :key="g" :label="g" :value="g" />
                </el-select>
              </el-form-item>
            </div>
            <div class="req-system-field">
              <el-form-item :label="`协同系统 ${index + 1}`">
                <el-select v-model="row.system" filterable clearable style="width: 100%" :disabled="!isViewStageActive() || !row.conglomerate" placeholder="再选择系统">
                  <el-option v-for="s in systemsByConglomerate(row.conglomerate)" :key="s.system_code" :label="`${s.system_code} ${s.system_name}`" :value="systemValue(s)" />
                </el-select>
              </el-form-item>
            </div>
            <el-button v-if="isViewStageActive()" link type="danger" @click="removeCoordRow(index)">移除</el-button>
          </div>
          <el-button v-if="isViewStageActive()" plain type="primary" size="small" @click="addCoordRow"><el-icon><Plus /></el-icon>添加协同系统</el-button>
          <div v-if="coordRows.length === 0 && !isViewStageActive()" class="req-form-hint">未配置协同系统</div>
        </div>

        <!-- 需求状态及备注（不属于任何阶段，在每条数据最后两列/表单底部固定展示） -->
        <div class="req-section-title d-flex align-items-center gap-8">
          <span>需求状态及备注</span>
          <el-button v-if="legacyForm.id" link type="warning" size="small" @click="openLegacyChange(null)"><el-icon><RefreshRight /></el-icon>需求变更</el-button>
        </div>
        <el-form-item label="需求状态">
          <el-input :model-value="(legacyForm as any).requirement_status || '需求分析'" disabled placeholder="由阶段推进自动维护" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="(legacyForm as any).remark" type="textarea" :rows="2" placeholder="【0611】713前投产，纳入建设合同" />
        </el-form-item>
      </el-form>
    </UiFormDrawer>

    <!-- 需求变更（独立按钮弹窗，不属于任何阶段） -->
    <el-dialog v-model="legacyChangeVisible" title="需求变更" width="min(680px, calc(100vw - 24px))">
      <el-form label-position="top" class="req-form-grid">
        <el-form-item label="是否涉及需求变更(是/否)">
          <el-select v-model="legacyChangeForm.change_involved" style="width: 100%">
            <el-option v-for="item in options.yesNo || []" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求变更信息（需求变更：至少包括1.谁发起变更;2.变更内容3.发起变更阶段）">
          <el-input v-model="legacyChangeForm.change_info" type="textarea" :rows="3" placeholder="谁发起变更/变更内容/发起变更阶段" :disabled="isChangeDetailDisabled()" />
        </el-form-item>
        <el-form-item label="变更评审结论（评审通过/评审不通过）">
          <el-select v-model="legacyChangeForm.change_review_conclusion" clearable :disabled="isChangeDetailDisabled()" style="width: 100%">
            <el-option v-for="item in options.changeReviewConclusions || []" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更结论及状态（审核通过/评估工作量/蒙商立项完成）">
          <el-select v-model="legacyChangeForm.change_conclusion_status" clearable :disabled="isChangeDetailDisabled()" style="width: 100%">
            <el-option v-for="item in options.changeConclusionStatuses || []" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求变更备注">
          <el-input v-model="legacyChangeForm.change_remark" type="textarea" :rows="2" :disabled="isChangeDetailDisabled()" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="legacyChangeVisible = false">取消</el-button>
        <el-button type="primary" :loading="legacyChangeSaving" @click="saveLegacyChange">保存</el-button>
      </template>
    </el-dialog>

    <!-- 阶段推进 -->
    <el-dialog v-model="stageDialogVisible" title="阶段推进" width="min(620px, calc(100vw - 24px))">
      <div v-if="stageTarget" class="req-stage-overview">
        <div class="req-stage-track">
          <div v-for="s in LEGACY_STAGE_ORDER" :key="s" class="req-stage-node" :class="{ 'req-stage-active': s === stageTarget.current_stage, 'req-stage-selected': s === stageForm.stage }">
            <span class="req-stage-name">{{ stageLabel(s) }}</span>
            <el-tag size="small" :type="stageStatusTagType((stageTarget[STAGE_FIELD_MAP[s]] as string) || '未开始')">{{ (stageTarget[STAGE_FIELD_MAP[s]] as string) || '未开始' }}</el-tag>
          </div>
        </div>
        <div class="req-stage-summary">
          当前阶段：<strong>{{ stageLabel(stageTarget.current_stage) }}</strong> · 需求状态：<el-tag size="small" type="info">{{ stageTarget.requirement_status || '需求分析' }}</el-tag>
        </div>
      </div>
      <el-form label-position="top" class="req-stage-form">
        <el-form-item label="阶段" required>
          <el-select v-model="stageForm.stage" style="width: 100%">
            <el-option v-for="stage in options.legacyStages || []" :key="stage" :label="stageLabel(stage)" :value="stage" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作" required>
          <el-radio-group v-model="stageForm.action">
            <el-radio v-for="a in availableActions" :key="a.value" :value="a.value" :disabled="a.disabled">{{ a.label }}<span v-if="a.disabled" class="req-action-reason">（{{ a.reason }}）</span></el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="联动预览">
          <div class="req-linkage-preview">
            需求状态：<span class="req-old-value">{{ stageTarget?.requirement_status || '需求分析' }}</span>
            → <span class="req-new-value">{{ linkedRequirementStatus || '不变' }}</span>
            <span v-if="!linkedRequirementStatus" class="req-form-hint">（当前动作不触发联动）</span>
          </div>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="stageForm.comment" type="textarea" :rows="2" placeholder="可选：记录本次推进说明（写入阶段日志）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stageDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="stageSaving" @click="saveStage">确认推进</el-button>
      </template>
    </el-dialog>

    <!-- 阶段记录 -->
    <el-dialog v-model="stageLogDialogVisible" title="阶段流转记录" width="min(860px, calc(100vw - 24px))">
      <div v-if="stageLogTarget" class="req-stage-overview">
        <div class="req-stage-track">
          <div v-for="s in LEGACY_STAGE_ORDER" :key="s" class="req-stage-node" :class="{ 'req-stage-active': s === stageLogTarget.current_stage }">
            <span class="req-stage-name">{{ stageLabel(s) }}</span>
            <el-tag size="small" :type="stageStatusTagType((stageLogTarget[STAGE_FIELD_MAP[s]] as string) || '未开始')">{{ (stageLogTarget[STAGE_FIELD_MAP[s]] as string) || '未开始' }}</el-tag>
          </div>
        </div>
        <div class="req-stage-summary">
          当前阶段：<strong>{{ stageLabel(stageLogTarget.current_stage) }}</strong> · 需求状态：<el-tag size="small" type="info">{{ stageLogTarget.requirement_status || '需求分析' }}</el-tag>
        </div>
      </div>
      <el-table :data="stageLogs" border size="small">
        <el-table-column label="阶段" min-width="160"><template #default="scope">{{ stageLabel(scope.row.to_stage) }}</template></el-table-column>
        <el-table-column prop="from_status" label="原状态" width="90" />
        <el-table-column prop="to_status" label="新状态" width="90" />
        <el-table-column prop="operator_name" label="推进人" width="120" />
        <el-table-column prop="comment" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="created_at" label="时间" width="160" />
      </el-table>
    </el-dialog>

    <!-- 修改记录 -->
    <el-dialog v-model="changeLogDialogVisible" :title="changeLogTitle" width="min(860px, calc(100vw - 24px))">
      <el-table :data="changeLogs" border size="small">
        <el-table-column prop="created_at" label="时间" width="170" />
        <el-table-column prop="operator_name" label="操作人" width="120" />
        <el-table-column label="操作类型" width="110"><template #default="scope">{{ changeTypeLabels[scope.row.change_type] || scope.row.change_type }}</template></el-table-column>
        <el-table-column label="字段" width="140"><template #default="scope">{{ label(scope.row.field_name) }}</template></el-table-column>
        <el-table-column label="旧值" min-width="150"><template #default="scope"><span class="req-old-value">{{ scope.row.old_value || '-' }}</span></template></el-table-column>
        <el-table-column label="新值" min-width="150"><template #default="scope"><span class="req-new-value">{{ scope.row.new_value || '-' }}</span></template></el-table-column>
      </el-table>
    </el-dialog>

    <!-- 审批记录 -->
    <el-dialog v-model="approvalLogDialogVisible" :title="approvalLogTitle" width="min(900px, calc(100vw - 24px))">
      <el-empty v-if="!approvalLogsLoading && approvalLogs.length === 0" description="暂无审批记录（差异未走审批流或工作流未产生动作）" />
      <el-table v-else v-loading="approvalLogsLoading" :data="approvalLogs" border size="small">
        <el-table-column prop="created_at" label="审批时间" width="170" />
        <el-table-column label="审批人" width="140"><template #default="scope">{{ scope.row.operator_name || scope.row.operator_id || '-' }}</template></el-table-column>
        <el-table-column label="审批结果" width="100"><template #default="scope"><el-tag size="small" :type="approvalActionTagType(scope.row.action_code)">{{ approvalActionLabels[scope.row.action_code] || scope.row.action_code }}</el-tag></template></el-table-column>
        <el-table-column label="任务类型" width="90"><template #default="scope">{{ approvalTaskTypeLabels[scope.row.task_type] || scope.row.task_type || '-' }}</template></el-table-column>
        <el-table-column label="审批对象" width="140"><template #default="scope">{{ scope.row.assignee_name || '-' }}</template></el-table-column>
        <el-table-column label="任务状态" width="100"><template #default="scope">{{ approvalTaskStatusLabels[scope.row.task_status] || scope.row.task_status || '-' }}</template></el-table-column>
        <el-table-column label="目标用户" width="120"><template #default="scope">{{ scope.row.target_user_name || (scope.row.action_code === 'ADD_SIGN' ? '-' : '—') }}</template></el-table-column>
        <el-table-column label="审批意见" min-width="200"><template #default="scope"><span>{{ scope.row.comment || '-' }}</span></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="approvalLogDialogVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 差异预览 -->
    <el-dialog v-model="diffPreviewVisible" :title="`差异预览：${diffPreviewRow?.requirement_no || ''}`" width="min(820px, calc(100vw - 24px))" top="6vh">
      <div v-if="diffPreviewRow">
        <div class="req-preview-header">
          <span class="req-preview-title">{{ diffPreviewRow.name }}</span>
          <el-tag v-if="diffPreviewRow.review_status" size="small" :type="diffPreviewRow.review_status === '已评审' ? 'success' : (diffPreviewRow.review_status === '已退回' ? 'warning' : 'info')">{{ diffPreviewRow.review_status }}</el-tag>
          <el-tag size="small" :type="diffPreviewRow.source === 'IMPORT' ? 'warning' : 'success'">{{ diffPreviewRow.source === 'IMPORT' ? '导入' : '在线填写' }}</el-tag>
        </div>
        <el-descriptions :column="2" border size="small" class="req-preview-desc">
          <el-descriptions-item v-for="item in diffPreviewFields" :key="item.label" :label="item.label" :span="['solution', 'difference_desc', 'monshang_practice', 'jinke_practice', 'decision_conclusion', 'review_comment'].includes(item.label) ? 2 : 1">
            <span class="req-preview-value">{{ item.value }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="diffPreviewVisible = false">关闭</el-button>
        <el-button v-if="canEditDiff(diffPreviewRow as RequirementDifference)" type="primary" @click="diffPreviewVisible = false; diffPreviewRow && openDiffEdit(diffPreviewRow)">编辑</el-button>
      </template>
    </el-dialog>

    <!-- 系统表单 -->
    <UiFormDrawer v-model="systemFormVisible" :title="systemForm.id ? '编辑系统' : '新增系统'" :loading="systemSaving" width="min(640px, calc(100vw - 24px))" @submit="saveSystem">
      <el-form label-position="top" class="req-form-grid">
        <el-form-item label="系统编号" required><el-input v-model="systemForm.system_code" :disabled="Boolean(systemForm.id)" /></el-form-item>
        <el-form-item label="系统名称" required><el-input v-model="systemForm.system_name" /></el-form-item>
        <el-form-item label="英文简称"><el-input v-model="systemForm.english_name" /></el-form-item>
        <el-form-item label="事业群"><el-input v-model="systemForm.conglomerate" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="systemForm.status" style="width: 100%"><el-option v-for="item in options.systemStatuses || []" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="引入/保留"><el-input v-model="systemForm.source_type" /></el-form-item>
        <el-form-item label="所属逻辑子系统编号"><el-input v-model="systemForm.logical_subsystem_code" /></el-form-item>
        <el-form-item label="所属逻辑子系统名称"><el-input v-model="systemForm.logical_subsystem_name" /></el-form-item>
        <el-form-item label="归属业务组件编号"><el-input v-model="systemForm.business_component_code" /></el-form-item>
        <el-form-item label="归属业务组件名称"><el-input v-model="systemForm.business_component_name" /></el-form-item>
        <el-form-item label="业务领域"><el-input v-model="systemForm.business_domain" /></el-form-item>
        <el-form-item label="产品视图"><el-input v-model="systemForm.product_view" /></el-form-item>
        <el-form-item label="投产点"><el-input v-model="systemForm.launch_point" /></el-form-item>
        <el-form-item label="类别"><el-input v-model="systemForm.category" /></el-form-item>
        <el-form-item label="灾备等级"><el-input v-model="systemForm.disaster_level" /></el-form-item>
        <el-form-item label="系统介绍" class="req-span-2"><el-input v-model="systemForm.introduction" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </UiFormDrawer>

    <!-- 工作流任务审批抽屉（从我的代办点击业务事项进入） -->
    <el-dialog v-model="approvalDialogVisible" :title="approvalContext ? '审批 - ' + (approvalContext.business_title || ('任务 #' + approvalContext.task_id)) : '审批任务'" width="min(640px, calc(100vw - 24px))" @close="approvalSubmitting = false">
      <el-skeleton v-if="approvalLoading" :rows="6" animated />
      <div v-else-if="approvalContext" class="req-approval-context">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="业务类型">{{ approvalContext.business_type || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务标题">{{ approvalContext.business_title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务单号">{{ approvalContext.business_key || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所属项目">{{ approvalContext.project_name || '未关联项目' }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ approvalContext.node_name || approvalContext.task_key || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发起">{{ approvalContext.starter_name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实例状态"><UiStatusTag :value="approvalContext.instance_status" tone="primary" /></el-descriptions-item>
          <el-descriptions-item label="允许操作">
            <el-tag v-for="a in approvalContext.allowed_actions" :key="a" size="small" class="req-approval-action-tag" :type="(APPROVAL_ACTION_META[a as ApprovalAction]?.type) || 'info'">
              {{ APPROVAL_ACTION_META[a as ApprovalAction]?.label || a }}
            </el-tag>
            <span v-if="approvalContext.allowed_actions.length === 0">-</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top" class="req-approval-form">
          <el-form-item label="审批意见"><el-input v-model="approvalComment" type="textarea" :rows="3" placeholder="可选：补充审批意见（建议驳回/回退时填写原因）" /></el-form-item>
        </el-form>
      </div>
      <template v-else><el-empty description="任务上下文为空" /></template>
      <template #footer>
        <el-button @click="approvalDialogVisible = false" :disabled="approvalSubmitting">取消</el-button>
        <el-button v-for="a in approvalActions" :key="a" :class="`is-approval-${a.toLowerCase()}`" :type="APPROVAL_ACTION_META[a].type" :plain="APPROVAL_ACTION_META[a].plain" :loading="approvalSubmitting" :disabled="approvalLoading || !approvalContext?.actionable" @click="submitApproval(a)">
          <el-icon v-if="a === 'APPROVE'"><Check /></el-icon><el-icon v-else-if="a === 'REJECT'"><Close /></el-icon>
          {{ APPROVAL_ACTION_META[a].label }}
        </el-button>
        <span v-if="approvalContext && !approvalContext.actionable" class="req-form-hint">当前任务对您不可操作</span>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.requirements-page {
  min-width: 0;
  overflow-x: hidden;
}
.requirements-tabs {
  margin-bottom: 12px;
}
.req-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.req-system-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}
.req-system-field {
  flex: 1;
  min-width: 0;
}
.req-span-2 {
  grid-column: span 2;
}
.req-member-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.req-import-box {
  margin-bottom: 12px;
}
.req-import-report p {
  margin: 8px 0;
}
.req-error-line {
  display: block;
  color: var(--el-color-danger);
  font-size: 12px;
}
.req-snapshot {
  word-break: break-all;
  white-space: pre-wrap;
  font-size: 12px;
}
.req-old-value {
  color: var(--el-color-danger);
}
.req-new-value {
  color: var(--el-color-success);
}
.req-link-cell {
  padding: 0;
  font-weight: 500;
}
.req-table-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.req-table-actions .el-button {
  margin-left: 0;
}
.req-table-actions .el-dropdown {
  display: inline-flex;
  align-items: center;
}
.req-preview-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px 14px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}
.req-preview-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-right: 4px;
  word-break: break-all;
}
.req-preview-desc {
  margin-bottom: 8px;
}
.req-preview-value {
  word-break: break-all;
  white-space: pre-wrap;
}
.muted {
  color: var(--el-text-color-secondary);
}
/* 固定操作列不透明：避免横向滚动时左侧内容从固定列背后透出 */
.requirements-page :deep(.el-table__fixed-right),
.requirements-page :deep(.el-table__fixed),
.requirements-page :deep(.el-table__fixed-right td.el-table__cell),
.requirements-page :deep(.el-table__fixed td.el-table__cell) {
  background: var(--panel-bg);
}
.requirements-page :deep(.el-table__fixed-right th.el-table__cell),
.requirements-page :deep(.el-table__fixed th.el-table__cell) {
  background: var(--panel-muted);
}
.requirements-page :deep(.el-table__fixed-right::before),
.requirements-page :deep(.el-table__fixed::before) {
  background: var(--line);
}
@media (max-width: 760px) {
  .req-form-grid {
    grid-template-columns: 1fr;
  }
  .req-span-2 {
    grid-column: span 1;
  }
  .ui-toolbar {
    flex-wrap: wrap;
  }
}

/* 阶段推进 / 阶段记录 顶部时序条 */
.req-stage-overview {
  margin-bottom: 16px;
  padding: 12px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 6px;
}
.req-stage-track {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.req-stage-node {
  flex: 1 1 14%;
  min-width: 110px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 8px;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color, #e4e7ed);
  border-radius: 4px;
  align-items: flex-start;
}
.req-stage-node.req-stage-active {
  border-color: var(--el-color-primary, #409eff);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.18);
}
.req-stage-node.req-stage-viewing {
  background: var(--el-color-primary-light-9, #ecf5ff);
  border-color: var(--el-color-primary-light-5, #a0cfff);
}
.req-stage-node[role="button"] {
  cursor: pointer;
  transition: border-color .2s, background-color .2s, box-shadow .2s;
}
.req-stage-node[role="button"]:hover {
  border-color: var(--el-color-primary-light-5, #a0cfff);
}
.req-stage-node.req-stage-selected {
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.req-stage-name {
  font-weight: 500;
  font-size: 13px;
}
.req-stage-summary {
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
}
.req-stage-view-tag {
  color: var(--el-color-primary, #409eff);
  font-size: 12px;
}
.req-stage-inst {
  color: var(--el-text-color-secondary, #909399);
}
.req-stage-form .req-form-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  margin-top: 4px;
}
.req-action-reason {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  margin-left: 4px;
}
.req-linkage-preview {
  font-size: 13px;
  line-height: 24px;
}
.req-linkage-preview .req-old-value {
  color: var(--el-text-color-secondary, #909399);
  text-decoration: line-through;
  margin: 0 4px;
}
.req-linkage-preview .req-new-value {
  color: var(--el-color-success, #67c23a);
  font-weight: 500;
  margin: 0 4px;
}
.req-section-title {
  grid-column: span 2;
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary, #303133);
  margin: 12px 0 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}
.req-preview-common {
  margin-bottom: 12px;
  padding: 8px;
  background: var(--el-fill-color-lighter, #fafafa);
  border-radius: 4px;
}
</style>
