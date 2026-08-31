<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowUp, Briefcase, Calendar, ChatDotRound, Delete, Document, Download, Edit, Folder, Lock, Plus, Search, Upload, User, View } from '@element-plus/icons-vue'
import type { UploadFile } from 'element-plus'
import { apiErrorMessage } from '../api/error'
import { createProjectAttachmentCategory, deleteProjectAttachment, getProjectAttachmentCategories, getProjectAttachmentDownload, getProjectAttachmentPreview, getProjectAttachments, uploadProjectAttachment } from '../api/attachments'
import { createProject, createProjectMember, createProjectOrganization, createProjectPlan, createProjectPlanGroup, createProjectRisk, createProjectRiskComment, createProjectRole, createProjectStage, deleteProject, deleteProjectMember, deleteProjectOrganization, deleteProjectPlan, deleteProjectPlanGroup, deleteProjectRisk, deleteProjectRole, deleteProjectStage, getProject, getProjectOptions, getProjectRiskComments, getProjectUserOptions, getProjectWorkbench, moveProjectPlanToGroup, updateProject, updateProjectMember, updateProjectOrganization, updateProjectPlan, updateProjectRisk, updateProjectRole, updateProjectSettings, updateProjectStage } from '../api/project'
import type { Project, ProjectMember, ProjectOptions, ProjectOrganization, ProjectPlan, ProjectRisk, ProjectRiskComment, ProjectRole, ProjectStage, ProjectStatus, PlanStatus, ProjectUserOption, ProjectOrganizationOption, ProjectPlanGroupColorToken } from '../types/project'
import type { AttachmentCategory, ProjectAttachment } from '../types/attachments'
import { formatDateOnly } from '../utils/date'
import { useAuthStore } from '../stores/auth'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiUserIdentity from '../components/ui/UiUserIdentity.vue'
import UiTreeSelect, { type UiTreeOption } from '../components/ui/UiTreeSelect.vue'
import UiFilePreview from '../components/ui/UiFilePreview.vue'
import UiPagination from '../components/ui/UiPagination.vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const projects = ref<Project[]>([])
const selectedProject = ref<Project | null>(null)
const isProjectDetailRoute = computed(() => Boolean(route.params.projectId))
const projectTabs = ['overview', 'plans', 'risk', 'organization', 'settings', 'attachments'] as const
type ProjectTab = typeof projectTabs[number]
const normalizeProjectTab = (value: unknown): ProjectTab => {
  const tab = Array.isArray(value) ? value[0] : value
  if (tab === 'members' || tab === 'roles') return 'organization'
  return projectTabs.includes(tab as ProjectTab) ? tab as ProjectTab : 'overview'
}
const activeTab = ref<ProjectTab>(normalizeProjectTab(route.query.tab))
const loading = ref(false)
const workbenchLoaded = ref(false)
const detailLoading = ref(Boolean(route.params.projectId))
const tabContentLoading = ref(false)
let tabLoadingTimer: number | null = null
const saving = ref(false)
const projectDialog = ref(false)
const projectEditingId = ref<number | null>(null)
const projectForm = reactive<Record<string, unknown>>({})
const projectDateRange = ref<string[]>([])
const planDialog = ref(false)
const planEditingId = ref<number | null>(null)
const planParentName = ref('')
const planForm = reactive<Record<string, unknown>>({})
const planDateRange = ref<string[]>([])
const planChildrenDialog = ref(false)
const selectedPlanForChildrenId = ref<number | null>(null)
const memberDialog = ref(false)
const memberEditingId = ref<number | null>(null)
const memberForm = reactive<{ user_id: number | null; org_id: number | null; role_ids: number[]; status: number }>({ user_id: null, org_id: null, role_ids: [], status: 1 })
const roleDialog = ref(false)
const roleEditingId = ref<number | null>(null)
const roleForm = reactive<Record<string, unknown>>({})
const projectOrganizationDialog = ref(false)
const projectOrganizationEditingId = ref<number | null>(null)
const projectOrganizationForm = reactive<Record<string, unknown>>({})
const selectedProjectOrganizationId = ref<number | null>(null)
const draggingPlanId = ref<number | null>(null)
const newlyCreatedStageRowIds = ref<Set<number>>(new Set())
const riskDialog = ref(false)
const riskEditingId = ref<number | null>(null)
const riskForm = reactive<Record<string, unknown>>({})
type RiskMode = 'report' | 'progress' | 'tracking'
const riskMode = ref<RiskMode>('report')
const riskEntryMode = ref<RiskMode>('report')
const riskComments = ref<ProjectRiskComment[]>([])
const riskCommentsLoading = ref(false)
const riskCommentSubmitting = ref(false)
const riskCommentText = ref('')
const attachments = ref<ProjectAttachment[]>([])
const attachmentsLoading = ref(false)
const attachmentKeyword = ref('')
const attachmentCategoryFilterId = ref<number | null>(null)
const attachmentPage = ref(1)
const attachmentPageSize = ref(10)
const attachmentTotal = ref(0)
const attachmentUploading = ref(false)
const attachmentDeletingId = ref<number | null>(null)
const attachmentCategories = ref<AttachmentCategory[]>([])
const attachmentCategoriesLoading = ref(false)
const attachmentCategoriesError = ref('')
const activeAttachmentCategoryName = computed(() => {
  if (attachmentCategoryFilterId.value === null) return '全部附件'
  if (attachmentCategoryFilterId.value === 0) return '未分类'
  return attachmentCategories.value.find(category => category.id === attachmentCategoryFilterId.value)?.name || '分类附件'
})
const attachmentCategoryDialog = ref(false)
const attachmentCategoryName = ref('')
const attachmentCategorySaving = ref(false)
const attachmentPreviewVisible = ref(false)
const attachmentPreviewUrl = ref<string | null>(null)
const attachmentPreviewName = ref('文件预览')
let attachmentRequestSequence = 0
const settingsForm = reactive({ plan_number_rule: '', child_plan_number_rule: '', risk_number_rule: '' })
const stageDialog = ref(false)
const stageEditingId = ref<number | null>(null)
const stageForm = reactive({ stage_name: '', sort_no: 0 })
const userOptions = ref<ProjectUserOption[]>([])
const userOptionsLoaded = ref(false)
const userOptionsLoading = ref(false)
const projectOptions = ref<ProjectOptions>({ project_phases: [], plan_phases: [], risk_urgencies: [], risk_report_levels: [], risk_statuses: [], risk_attention_levels: [], risk_escalation_levels: [], risk_problem_levels: [], organizations: [] })
const organizationTreeOptions = computed<UiTreeOption[]>(() => {
  const nodes = new Map<number, UiTreeOption>()
  projectOptions.value.organizations.forEach((organization: ProjectOrganizationOption) => {
    nodes.set(organization.id, { value: organization.id, label: organization.org_name, children: [] })
  })
  const roots: UiTreeOption[] = []
  projectOptions.value.organizations.forEach((organization: ProjectOrganizationOption) => {
    const node = nodes.get(organization.id)
    if (!node) return
    const parent = nodes.get(Number(organization.parent_id || 0))
    if (parent) parent.children?.push(node)
    else roots.push(node)
  })
  return roots
})
const projectOrganizations = computed<ProjectOrganization[]>(() => selectedProject.value?.project_organizations || [])
function buildProjectOrganizationTreeOptions(values: ProjectOrganization[], excludedIds = new Set<number>()) {
  const nodes = new Map<number, UiTreeOption>()
  values.filter(organization => !excludedIds.has(organization.id)).forEach(organization => nodes.set(organization.id, { value: organization.id, label: `${organization.org_name}（${organization.org_code}）`, children: [] }))
  const roots: UiTreeOption[] = []
  values.filter(organization => !excludedIds.has(organization.id)).forEach(organization => {
    const node = nodes.get(organization.id)
    if (!node) return
    const parent = nodes.get(Number(organization.parent_id || 0))
    if (parent) parent.children?.push(node)
    else roots.push(node)
  })
  return roots
}
const projectOrganizationTreeOptions = computed<UiTreeOption[]>(() => buildProjectOrganizationTreeOptions(projectOrganizations.value))
const projectOrganizationParentOptions = computed<UiTreeOption[]>(() => {
  const excludedIds = new Set<number>()
  const markExcluded = (id: number) => {
    if (excludedIds.has(id)) return
    excludedIds.add(id)
    projectOrganizations.value.filter(organization => Number(organization.parent_id || 0) === id).forEach(child => markExcluded(child.id))
  }
  if (projectOrganizationEditingId.value !== null) markExcluded(projectOrganizationEditingId.value)
  return buildProjectOrganizationTreeOptions(projectOrganizations.value, excludedIds)
})
const filteredMembers = computed(() => selectedProjectOrganizationId.value === null ? members.value : members.value.filter(member => Number(member.org_id || 0) === selectedProjectOrganizationId.value))
function selectProjectOrganization(node: UiTreeOption) { selectedProjectOrganizationId.value = Number(node.value) }
function projectOrganizationById(id: unknown) { return projectOrganizations.value.find(item => item.id === Number(id)) }

const projectPermissions = computed(() => new Set(auth.user?.permissions || []))
const can = (permission: string) => projectPermissions.value.has(permission)
const canCreateProject = computed(() => can('project:project:list:create'))
const canUpdateProject = computed(() => can('project:project:list:update'))
const canDeleteProject = computed(() => can('project:project:list:delete'))
const canCreatePlan = computed(() => can('project:plan:list:create'))
const canUpdatePlan = computed(() => can('project:plan:list:update'))
const canDeletePlan = computed(() => can('project:plan:list:delete'))
const canCreateRisk = computed(() => can('project:risk:list:create'))
const canUpdateRisk = computed(() => can('project:risk:list:update'))
const canDeleteRisk = computed(() => can('project:risk:list:delete'))
const canCreateMember = computed(() => can('project:member:list:create'))
const canUpdateMember = computed(() => can('project:member:list:update'))
const canDeleteMember = computed(() => can('project:member:list:delete'))
const canCreateRole = computed(() => can('project:role:list:create'))
const canUpdateRole = computed(() => can('project:role:list:update'))
const canDeleteRole = computed(() => can('project:role:list:delete'))
const projectStatusLabels: Record<ProjectStatus, string> = { PLANNING: '计划中', RUNNING: '进行中', COMPLETED: '已完成', SUSPENDED: '已暂停' }
const planStatusLabels: Record<PlanStatus, string> = { NOT_STARTED: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', BLOCKED: '已阻塞' }
const isOwner = computed(() => Boolean(selectedProject.value && (selectedProject.value.owner_id === auth.user?.id || auth.user?.roles.includes('SUPER_ADMIN'))))
const flatPlans = computed(() => selectedProject.value?.plans || [])
const planGroups = computed(() => selectedProject.value?.plan_groups || [])
const projectStages = computed<ProjectStage[]>(() => selectedProject.value?.plan_stages || [])
const stageOptionsForProject = computed(() => projectStages.value.length ? projectStages.value.map(stage => ({ value: stage.stage_code, label: stage.stage_name })) : projectOptions.value.plan_phases)
const canManageStages = computed(() => canUpdateProject.value && isOwner.value)
type ProjectPlanRow = ProjectPlan & { children?: ProjectPlanRow[]; node_type?: 'stage' | 'group' | 'plan'; stage_key?: string; stage_name?: string; stage_index?: number; group_count?: number; group_color_key?: ProjectPlanGroupColorToken }
const stageRowId = (stageIndex: number) => -2000000000 - stageIndex
const groupRowId = (groupId: number | null, stageIndex: number) => groupId === null ? -1500000000 - stageIndex : -1000000 - groupId
function buildPlanBranch(values: ProjectPlan[]): ProjectPlanRow[] {
  const nodes = new Map<number, ProjectPlanRow>()
  values.forEach(plan => nodes.set(plan.id, { ...plan, node_type: 'plan', children: [] }))
  const roots: ProjectPlanRow[] = []
  nodes.forEach(node => {
    const parent = nodes.get(Number(node.parent_id || 0))
    if (parent && parent.id !== node.id) parent.children?.push(node)
    else roots.push(node)
  })
  const sortTree = (items: ProjectPlanRow[]) => {
    items.sort((left, right) => Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id)
    items.forEach(item => {
      if (item.children?.length) sortTree(item.children)
      else delete item.children
    })
  }
  sortTree(roots)
  return roots
}
const planTree = computed<ProjectPlanRow[]>(() => {
  const grouped = new Map<number | null, ProjectPlan[]>()
  flatPlans.value.forEach(plan => { const key = plan.group_id == null ? null : Number(plan.group_id); grouped.set(key, [...(grouped.get(key) || []), plan]) })
  const stageMap = new Map<string, { key: string; name: string; index: number }>()
  stageOptionsForProject.value.forEach((stage, index) => stageMap.set(stage.value, { key: stage.value, name: stage.label, index }))
  const knownStageKeys = [...new Set([...flatPlans.value.map(plan => plan.phase), ...planGroups.value.map(group => group.phase)].filter((phase): phase is string => Boolean(phase)))]
  const unknownStages = knownStageKeys.filter(phase => !stageMap.has(phase))
  unknownStages.forEach((phase, index) => stageMap.set(phase, { key: phase, name: phase, index: stageMap.size + index }))
  const rows: ProjectPlanRow[] = []
  ;[...stageMap.values()].sort((left, right) => left.index - right.index).forEach(stage => {
    const stageChildren: ProjectPlanRow[] = []
    const stageGroups = planGroups.value.filter(group => group.phase === stage.key).sort((left, right) => Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id)
    stageGroups.forEach(group => {
      const children = buildPlanBranch(grouped.get(group.id) || [])
      const stagePlanCode = group.stage_plan_code || group.group_name
      stageChildren.push({ id: groupRowId(group.id, stage.index), node_type: 'group', stage_key: stage.key, stage_name: stage.name, stage_index: stage.index, group_id: group.id, group_name: stagePlanCode, group_color_key: group.color_key || 'brand', group_count: children.length, plan_name: stagePlanCode, plan_code: '阶段', parent_id: 0, progress: 0, status: 'NOT_STARTED', sort_no: group.sort_no, project_id: selectedProject.value?.id || 0, children } as ProjectPlanRow)
      grouped.delete(group.id)
    })
    const ungrouped = buildPlanBranch((grouped.get(null) || []).filter(plan => plan.phase === stage.key))
    if (ungrouped.length) stageChildren.push({ id: groupRowId(null, stage.index), node_type: 'group', stage_key: stage.key, stage_name: stage.name, stage_index: stage.index, group_id: null, group_name: '未分组', group_color_key: 'brand', group_count: ungrouped.length, plan_name: '未分组', plan_code: '分组', parent_id: 0, progress: 0, status: 'NOT_STARTED', sort_no: 0, project_id: selectedProject.value?.id || 0, children: ungrouped } as ProjectPlanRow)
    rows.push({ id: stageRowId(stage.index), node_type: 'stage', stage_key: stage.key, stage_name: stage.name, stage_index: stage.index, group_id: null, group_name: stage.name, plan_name: stage.name, plan_code: '阶段', parent_id: 0, progress: 0, status: 'NOT_STARTED', sort_no: stage.index, project_id: selectedProject.value?.id || 0, group_count: stageChildren.reduce((total, group) => total + Number(group.group_count || 0), 0), children: stageChildren } as ProjectPlanRow)
  })
  return rows
})
const plans = flatPlans
const risks = computed(() => selectedProject.value?.risks || [])
const members = computed(() => selectedProject.value?.members || [])
const roles = computed(() => selectedProject.value?.roles || [])
const mainPlanTimelineRanges = computed<GanttRange[]>(() => {
  return plans.value
    .filter(plan => !Number(plan.parent_id || 0))
    .map(planRange)
    .filter((range): range is GanttRange => Boolean(range))
})
type PlanTimelineRow = { key: string; id: number | null; colorKey: ProjectPlanGroupColorToken; masters: ProjectPlan[] }
type PlanTimelineStage = { key: string; name: string; index: number; masters: ProjectPlan[]; rows: PlanTimelineRow[] }
const planTimelineStages = computed<PlanTimelineStage[]>(() => {
  const stageMap = new Map<string, { key: string; name: string; index: number }>()
  stageOptionsForProject.value.forEach((stage, index) => stageMap.set(stage.value, { key: stage.value, name: stage.label, index }))
  const masterPlans = plans.value.filter(plan => !Number(plan.parent_id || 0))
  const unknownStages = [...new Set([...masterPlans.map(plan => plan.phase), ...planGroups.value.map(group => group.phase)].filter((phase): phase is string => Boolean(phase)))].filter(phase => !stageMap.has(phase))
  unknownStages.forEach((phase, index) => stageMap.set(phase, { key: phase, name: phase, index: stageMap.size + index }))
  if (masterPlans.some(plan => !plan.phase)) stageMap.set('__UNASSIGNED__', { key: '__UNASSIGNED__', name: '未设置阶段', index: stageMap.size })
  return [...stageMap.values()].sort((left, right) => left.index - right.index).map(stage => ({
    ...stage,
    masters: masterPlans.filter(plan => (plan.phase || '__UNASSIGNED__') === stage.key),
    rows: timelineRowsForStage(stage.key, masterPlans)
  }))
})
const overviewPlanTimelineStages = computed<PlanTimelineStage[]>(() => planTimelineStages.value.map(stage => ({ ...stage, masters: sortTimelinePlans(stage.masters) })))
const hasOverviewPlanTimeline = computed(() => overviewPlanTimelineStages.value.some(stage => stage.masters.length > 0))
type OverviewTimelineRow = { plan: ProjectPlan; lane: number }
function overviewTimelineRows(stage: PlanTimelineStage): OverviewTimelineRow[] {
  const laneEnds: number[] = []
  return sortTimelinePlans(stage.masters.filter(plan => planRange(plan))).map(plan => {
    const range = planRange(plan)!
    let lane = laneEnds.findIndex(end => end <= range.start)
    if (lane < 0) { lane = laneEnds.length; laneEnds.push(range.end) } else laneEnds[lane] = range.end
    return { plan, lane }
  })
}
function overviewTimelineStageHeight(stage: PlanTimelineStage) {
  const rows = overviewTimelineRows(stage)
  const laneCount = rows.length ? Math.max(...rows.map(row => row.lane)) + 1 : 1
  return `${Math.max(58, laneCount * 34 + (stage.masters.some(plan => !planRange(plan)) ? 48 : 24))}px`
}
function overviewTimelineBarStyle(plan: ProjectPlan, lane: number) {
  return { ...timelineBarStyle(plan), top: `${12 + lane * 34}px` }
}
function overviewTimelineUndated(stage: PlanTimelineStage) { return stage.masters.filter(plan => !planRange(plan)) }
function sortTimelinePlans(items: ProjectPlan[]) {
  return [...items].sort((left, right) => {
    const leftRange = planRange(left)?.start ?? Number.MAX_SAFE_INTEGER
    const rightRange = planRange(right)?.start ?? Number.MAX_SAFE_INTEGER
    const leftEnd = planRange(left)?.end ?? Number.MAX_SAFE_INTEGER
    const rightEnd = planRange(right)?.end ?? Number.MAX_SAFE_INTEGER
    return leftRange - rightRange || leftEnd - rightEnd || Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id
  })
}
function timelineRowsForStage(stageKey: string, masterPlans: ProjectPlan[]): PlanTimelineRow[] {
  const stagePlans = masterPlans.filter(plan => (plan.phase || '__UNASSIGNED__') === stageKey)
  const configuredGroups = planGroups.value.filter(group => (group.phase || '__UNASSIGNED__') === stageKey).sort((left, right) => Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id)
  const rows: PlanTimelineRow[] = configuredGroups.map(group => ({
    key: `group-${group.id}`,
    id: group.id,
    colorKey: group.color_key || 'brand',
    masters: sortTimelinePlans(stagePlans.filter(plan => Number(plan.group_id) === Number(group.id)))
  }))
  const ungrouped = sortTimelinePlans(stagePlans.filter(plan => !plan.group_id || !configuredGroups.some(group => Number(group.id) === Number(plan.group_id))))
  if (ungrouped.length || !rows.length) rows.push({ key: `ungrouped-${stageKey}`, id: null, colorKey: 'muted', masters: ungrouped })
  return rows
}
const dayMillis = 86400000
const todayTimelineDate = computed(() => startOfDay(Date.now()))
const planTimelineAxis = computed(() => {
  const axis = ganttAxisRange([...mainPlanTimelineRanges.value, { start: todayTimelineDate.value, end: todayTimelineDate.value + dayMillis }])
  if (axis) return axis
  const projectStart = planDateValue(selectedProject.value?.planned_start_date)
  const projectEnd = planDateValue(selectedProject.value?.planned_end_date)
  const fallbackStart = startOfMonth(projectStart || projectEnd || Date.now())
  const fallbackEnd = new Date(new Date(fallbackStart).getFullYear(), new Date(fallbackStart).getMonth() + 1, 1).getTime()
  return { min: fallbackStart, max: fallbackEnd, splitNumber: 1 }
})
const planTimelineMonths = computed(() => {
  const axis = planTimelineAxis.value
  if (!axis) return []
  const months: Array<{ timestamp: number; label: string }> = []
  for (let timestamp = axis.min; timestamp < axis.max; timestamp = new Date(new Date(timestamp).getFullYear(), new Date(timestamp).getMonth() + 1, 1).getTime()) {
    months.push({ timestamp, label: monthAxisLabel(timestamp) })
  }
  return months
})
const todayTimelinePosition = computed(() => {
  const axis = planTimelineAxis.value
  if (!axis || todayTimelineDate.value < axis.min || todayTimelineDate.value >= axis.max) return null
  return `${Math.max(0, Math.min(100, (todayTimelineDate.value - axis.min) / (axis.max - axis.min) * 100))}%`
})
function timelineLayoutStyle() {
  return { '--project-plan-timeline-track-min-width': `${Math.max(760, planTimelineMonths.value.length * 112)}px` }
}
const selectedPlanForChildren = computed(() => plans.value.find(plan => plan.id === selectedPlanForChildrenId.value) || null)
const selectedPlanChildren = computed(() => {
  const parentId = selectedPlanForChildren.value?.id
  if (!parentId) return []
  return plans.value.filter(plan => Number(plan.parent_id || 0) === parentId).sort((left, right) => Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id)
})
type GanttRange = { start: number; end: number }

function planDateValue(value: string | null | undefined) {
  const raw = String(value || '').slice(0, 10)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(raw)) return null
  const timestamp = new Date(`${raw}T00:00:00`).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

function planRange(plan: ProjectPlan): GanttRange | null {
  const start = planDateValue(plan.planned_start_date)
  const end = planDateValue(plan.planned_end_date) ?? start
  if (start === null && end === null) return null
  const first = Math.min(start ?? end!, end ?? start!)
  const last = Math.max(start ?? end!, end ?? start!) + dayMillis
  return { start: first, end: Math.max(last, first + dayMillis) }
}

function startOfMonth(timestamp: number) {
  const date = new Date(timestamp)
  return new Date(date.getFullYear(), date.getMonth(), 1).getTime()
}

function startOfDay(timestamp: number) {
  const date = new Date(timestamp)
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
}

function ganttAxisRange(ranges: GanttRange[]) {
  if (!ranges.length) return null
  const range = { start: Math.min(...ranges.map(item => item.start)), end: Math.max(...ranges.map(item => item.end)) }
  const min = startOfMonth(range.start)
  const endAnchor = startOfMonth(Math.max(range.end - dayMillis, range.start))
  const max = new Date(new Date(endAnchor).getFullYear(), new Date(endAnchor).getMonth() + 1, 1).getTime()
  const count = (new Date(max).getFullYear() - new Date(min).getFullYear()) * 12 + new Date(max).getMonth() - new Date(min).getMonth()
  return { min, max, splitNumber: Math.max(1, Math.min(12, count)) }
}

function monthAxisLabel(value: number) {
  const date = new Date(value)
  return `${date.getFullYear()}年${date.getMonth() + 1}月`
}

function resetProjectForm() { Object.keys(projectForm).forEach(key => delete projectForm[key]); Object.assign(projectForm, { project_code: '', project_name: '', description: '', status: 'PLANNING', owner_id: auth.user?.id || null, planned_start_date: '', planned_end_date: '', actual_end_date: '' }); projectDateRange.value = [] }
function resetPlanForm() { Object.keys(planForm).forEach(key => delete planForm[key]); Object.assign(planForm, { parent_id: 0, group_id: null, phase: '', plan_name: '', description: '', owner_id: null, lead_org_id: null, cooperating_org_ids: [], planned_start_date: '', planned_end_date: '', progress: 0, status: 'NOT_STARTED', sort_no: 0 }); planDateRange.value = [] }
function resetRoleForm() { Object.keys(roleForm).forEach(key => delete roleForm[key]); Object.assign(roleForm, { role_code: '', role_name: '', description: '', member_ids: [] }) }
function resetProjectOrganizationForm(parentId = 0) { Object.keys(projectOrganizationForm).forEach(key => delete projectOrganizationForm[key]); Object.assign(projectOrganizationForm, { parent_id: parentId, org_code: '', org_name: '', sort_no: 0, status: 1 }) }
function resetRiskForm() {
  Object.keys(riskForm).forEach(key => delete riskForm[key])
  Object.assign(riskForm, {
    occurred_date: '', project_phase: projectOptions.value.project_phases[0]?.value || '',
    urgency: projectOptions.value.risk_urgencies[0]?.value || '', report_level: projectOptions.value.risk_report_levels[0]?.value || '', current_status: projectOptions.value.risk_statuses[0]?.value || 'OPEN',
    proposer_org_id: null, proposer_subsystem: '', proposer_contact_name: '', proposer_contact_phone: '', involved_org_id: null, involved_subsystem: '', problem_description: '', expected_resolution_date: '', suggested_solution: '', current_handler_name: '', current_handler_phone: '',
    progress_description: '', attention_level: projectOptions.value.risk_attention_levels[0]?.value || '', problem_nature: '', problem_domain: '', pmo_contact: '', escalation_level: projectOptions.value.risk_escalation_levels[0]?.value || '', current_problem_level: projectOptions.value.risk_problem_levels[0]?.value || '', planned_resolution_date: '', actual_resolution_date: '', resolution_solution: ''
  })
}
async function loadOptions() { try { projectOptions.value = (await getProjectOptions()).data.data } catch (error) { ElMessage.error(apiErrorMessage(error, '项目参数加载失败')) } }
async function loadWorkbench() { loading.value = true; try { projects.value = (await getProjectWorkbench()).data.data } catch (error) { ElMessage.error(apiErrorMessage(error, '项目加载失败')) } finally { workbenchLoaded.value = true; loading.value = false } }
function addUserOption(id: number | null | undefined, displayName?: string | null, username = '') { if (!id) return; const existing = userOptions.value.find(item => item.id === id); if (existing) { if (displayName) existing.display_name = displayName; if (username) existing.username = username; return }; userOptions.value.push({ id, username, display_name: displayName || `用户 ${id}` }) }
function userOptionLabel(item: ProjectUserOption) { return item.username ? `${item.display_name}（${item.username}）` : item.display_name }
function ensureProjectUserOptions(project: Project) { addUserOption(project.owner_id, project.owner_name); project.plans?.forEach(plan => addUserOption(plan.owner_id, plan.owner_name)); project.members?.forEach(member => addUserOption(member.user_id, member.display_name)) }
async function refreshProject(projectId: number) { detailLoading.value = true; try { if (selectedProject.value?.id !== projectId) newlyCreatedStageRowIds.value = new Set(); selectedProject.value = (await getProject(projectId)).data.data; ensureProjectUserOptions(selectedProject.value); resetSettingsForm(); if (activeTab.value === 'attachments') { resetAttachmentQuery(); await Promise.all([loadProjectAttachments(), loadProjectAttachmentCategories()]) } } catch (error) { newlyCreatedStageRowIds.value = new Set(); selectedProject.value = null; ElMessage.error(apiErrorMessage(error, '项目详情加载失败')); await router.replace({ name: 'projects', query: {} }) } finally { detailLoading.value = false } }
function resetAttachmentQuery() { attachmentKeyword.value = ''; attachmentCategoryFilterId.value = null; attachmentPage.value = 1; attachmentPageSize.value = 10; attachmentTotal.value = 0; attachments.value = []; attachmentCategories.value = []; attachmentCategoriesError.value = '' }
async function loadProjectAttachments() { if (!selectedProject.value) return; const requestSequence = ++attachmentRequestSequence; attachmentsLoading.value = true; try { const page = (await getProjectAttachments(selectedProject.value.id, { page: attachmentPage.value, size: attachmentPageSize.value, keyword: attachmentKeyword.value.trim() || undefined, categoryId: attachmentCategoryFilterId.value ?? undefined })).data.data; if (requestSequence !== attachmentRequestSequence) return; attachments.value = page.records; attachmentTotal.value = page.total } catch (error) { if (requestSequence !== attachmentRequestSequence) return; attachments.value = []; attachmentTotal.value = 0; ElMessage.error(apiErrorMessage(error, '项目附件加载失败')) } finally { if (requestSequence === attachmentRequestSequence) attachmentsLoading.value = false } }
async function loadProjectAttachmentCategories() { if (!selectedProject.value) return; attachmentCategoriesLoading.value = true; attachmentCategoriesError.value = ''; try { attachmentCategories.value = (await getProjectAttachmentCategories(selectedProject.value.id)).data.data } catch (error) { attachmentCategories.value = []; attachmentCategoriesError.value = apiErrorMessage(error, '附件分类加载失败'); ElMessage.error(attachmentCategoriesError.value) } finally { attachmentCategoriesLoading.value = false } }
function searchAttachments() { attachmentPage.value = 1; void loadProjectAttachments() }
function selectAttachmentCategory(categoryId: number | null) { attachmentCategoryFilterId.value = categoryId; attachmentPage.value = 1; void loadProjectAttachments() }
function resetAttachmentSearch() { attachmentKeyword.value = ''; attachmentCategoryFilterId.value = null; attachmentPage.value = 1; void loadProjectAttachments() }
function changeAttachmentPage(page: number) { attachmentPage.value = page; void loadProjectAttachments() }
function changeAttachmentPageSize(size: number) { attachmentPageSize.value = size; attachmentPage.value = 1; void loadProjectAttachments() }
function formatAttachmentSize(size: number) { if (size < 1024) return `${size} B`; if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`; if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`; return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB` }
function openCreateAttachmentCategory() { attachmentCategoryName.value = ''; attachmentCategoryDialog.value = true }
async function saveAttachmentCategory() { if (!selectedProject.value) return; const name = attachmentCategoryName.value.trim(); if (!name) { ElMessage.warning('请输入分类名称'); return } attachmentCategorySaving.value = true; try { const category = (await createProjectAttachmentCategory(selectedProject.value.id, name)).data.data; attachmentCategories.value = [...attachmentCategories.value, category].sort((left, right) => left.sortNo - right.sortNo || left.id - right.id); attachmentCategoryDialog.value = false; ElMessage.success('附件分类已创建') } catch (error) { ElMessage.error(apiErrorMessage(error, '附件分类创建失败')) } finally { attachmentCategorySaving.value = false } }
async function handleAttachmentChange(file: UploadFile) { if (file.raw) await uploadAttachment(file.raw) }
async function uploadAttachment(file: File) { if (!selectedProject.value) return; attachmentUploading.value = true; try { await uploadProjectAttachment(selectedProject.value.id, file); attachmentPage.value = 1; await loadProjectAttachments(); ElMessage.success('附件上传成功') } catch (error) { ElMessage.error(apiErrorMessage(error, '附件上传失败')) } finally { attachmentUploading.value = false } }
async function previewAttachment(row: ProjectAttachment) { if (!selectedProject.value) return; try { const link = (await getProjectAttachmentPreview(selectedProject.value.id, row.id)).data.data; attachmentPreviewName.value = link.fileName || row.fileName; attachmentPreviewUrl.value = link.url; attachmentPreviewVisible.value = true } catch (error) { ElMessage.error(apiErrorMessage(error, '附件预览地址获取失败')) } }
async function downloadAttachment(row: ProjectAttachment) { if (!selectedProject.value) return; try { const link = (await getProjectAttachmentDownload(selectedProject.value.id, row.id)).data.data; const anchor = document.createElement('a'); anchor.href = link.url; anchor.download = link.fileName || row.fileName; anchor.rel = 'noopener'; anchor.target = '_blank'; anchor.click() } catch (error) { ElMessage.error(apiErrorMessage(error, '附件下载地址获取失败')) } }
function messageBoxAction(error: unknown) { return typeof error === 'string' ? error : (error as { action?: string } | null)?.action }
async function removeAttachment(row: ProjectAttachment) { if (!selectedProject.value) return; try { await ElMessageBox.confirm(`确认删除附件“${row.fileName}”吗？删除后不可恢复。`, '删除附件', { type: 'warning' }); attachmentDeletingId.value = row.id; await deleteProjectAttachment(selectedProject.value.id, row.id); await loadProjectAttachments(); if (!attachments.value.length && attachmentPage.value > 1) { attachmentPage.value -= 1; await loadProjectAttachments() } ElMessage.success('附件已删除') } catch (error) { const action = messageBoxAction(error); if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '附件删除失败')) } finally { attachmentDeletingId.value = null } }
async function syncProjectFromRoute() { const rawProjectId = route.params.projectId; if (!rawProjectId) { selectedProject.value = null; detailLoading.value = false; tabContentLoading.value = false; return }; const projectId = Number(rawProjectId); if (!Number.isSafeInteger(projectId) || projectId <= 0) { await router.replace({ name: 'projects', query: {} }); return }; activeTab.value = normalizeProjectTab(route.query.tab); if (selectedProject.value?.id !== projectId || detailLoading.value) await refreshProject(projectId) }
function clearTabLoadingTimer() { if (tabLoadingTimer !== null) { window.clearTimeout(tabLoadingTimer); tabLoadingTimer = null } }
async function setProjectTab(tab: string | number) { const normalizedTab = normalizeProjectTab(String(tab)); if (normalizeProjectTab(route.query.tab) === normalizedTab) return; clearTabLoadingTimer(); tabContentLoading.value = true; if (route.name === 'project-detail' && route.params.projectId != null) await router.replace({ query: { ...route.query, tab: normalizedTab } }); if (normalizedTab === 'attachments') await Promise.all([loadProjectAttachments(), loadProjectAttachmentCategories()]); await nextTick(); await new Promise<void>(resolve => { tabLoadingTimer = window.setTimeout(resolve, 220) }); tabLoadingTimer = null; tabContentLoading.value = false }
async function openProject(project: Project) { if (selectedProject.value?.id !== project.id) newlyCreatedStageRowIds.value = new Set(); selectedProject.value = project; activeTab.value = 'overview'; detailLoading.value = true; await router.push({ name: 'project-detail', params: { projectId: String(project.id) }, query: { tab: 'overview' } }) }
async function refreshSelectedProject() { if (selectedProject.value) await refreshProject(selectedProject.value.id) }
function resetSettingsForm() { settingsForm.plan_number_rule = selectedProject.value?.plan_number_rule || '{PROJECT_CODE}-P{SEQ:3}'; settingsForm.child_plan_number_rule = selectedProject.value?.child_plan_number_rule || '{PARENT_CODE}-S{SEQ:3}'; settingsForm.risk_number_rule = selectedProject.value?.risk_number_rule || '{PROJECT_CODE}-R{SEQ:3}' }
function openSettings() { resetSettingsForm() }
async function saveSettings() { if (!selectedProject.value || !settingsForm.plan_number_rule.trim() || !settingsForm.child_plan_number_rule.trim() || !settingsForm.risk_number_rule.trim()) { ElMessage.warning('请输入主计划、子计划和风险编号规则'); return }; saving.value = true; try { selectedProject.value = (await updateProjectSettings(selectedProject.value.id, settingsForm)).data.data; await loadWorkbench(); ElMessage.success('项目设置已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目设置保存失败')) } finally { saving.value = false } }
function stageIsLocked(stage: ProjectStage) { return Boolean(stage.locked) || Number(stage.has_master_plans || 0) > 0 }
function openCreateStage() { stageEditingId.value = null; stageForm.stage_name = ''; stageForm.sort_no = projectStages.value.length; stageDialog.value = true }
function openEditStage(stage: ProjectStage) { if (stageIsLocked(stage)) return; stageEditingId.value = stage.id; stageForm.stage_name = stage.stage_name; stageForm.sort_no = stage.sort_no; stageDialog.value = true }
async function saveStage() {
  if (!selectedProject.value || !stageForm.stage_name.trim()) { ElMessage.warning('请输入阶段名称'); return }
  saving.value = true
  try {
    if (stageEditingId.value) await updateProjectStage(selectedProject.value.id, stageEditingId.value, { stage_name: stageForm.stage_name.trim(), sort_no: stageForm.sort_no })
    else await createProjectStage(selectedProject.value.id, { stage_name: stageForm.stage_name.trim(), sort_no: stageForm.sort_no })
    stageDialog.value = false
    await refreshSelectedProject()
    ElMessage.success(stageEditingId.value ? '项目阶段已保存' : '项目阶段已新增')
  } catch (error) { ElMessage.error(apiErrorMessage(error, '项目阶段保存失败')) } finally { saving.value = false }
}
async function reorderStage(stage: ProjectStage, offset: -1 | 1) {
  if (!selectedProject.value || stageIsLocked(stage) || saving.value) return
  const index = projectStages.value.findIndex(item => item.id === stage.id)
  const target = projectStages.value[index + offset]
  if (!target || stageIsLocked(target)) return
  saving.value = true
  try {
    await updateProjectStage(selectedProject.value.id, stage.id, { sort_no: target.sort_no })
    await updateProjectStage(selectedProject.value.id, target.id, { sort_no: stage.sort_no })
    await refreshSelectedProject()
  } catch (error) { ElMessage.error(apiErrorMessage(error, '项目阶段排序失败')) } finally { saving.value = false }
}
async function removeStage(stage: ProjectStage) {
  if (!selectedProject.value || stageIsLocked(stage) || saving.value) return
  try {
    await ElMessageBox.confirm(`确认删除阶段“${stage.stage_name}”吗？删除后该阶段将不再用于新计划。`, '删除项目阶段', { type: 'warning' })
    saving.value = true
    await deleteProjectStage(selectedProject.value.id, stage.id)
    await refreshSelectedProject()
    ElMessage.success('项目阶段已删除')
  } catch (error) { const action = messageBoxAction(error); if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目阶段删除失败')) } finally { saving.value = false }
}
function openCreateProject() { projectEditingId.value = null; resetProjectForm(); projectDialog.value = true }
async function openEditProject() { if (!selectedProject.value) return; await loadUsers(); addUserOption(selectedProject.value.owner_id, selectedProject.value.owner_name); projectEditingId.value = selectedProject.value.id; Object.assign(projectForm, { project_code: selectedProject.value.project_code, project_name: selectedProject.value.project_name, description: selectedProject.value.description || '', status: selectedProject.value.status, owner_id: selectedProject.value.owner_id, planned_start_date: selectedProject.value.planned_start_date || '', planned_end_date: selectedProject.value.planned_end_date || '', actual_end_date: selectedProject.value.actual_end_date || '' }); projectDateRange.value = selectedProject.value.planned_start_date && selectedProject.value.planned_end_date ? [selectedProject.value.planned_start_date, selectedProject.value.planned_end_date] : []; projectDialog.value = true }
function validDateRange(start: unknown, end: unknown) { return !start || !end || String(end) >= String(start) }
function onProjectDateRangeChange(value: unknown) {
  const values = Array.isArray(value) ? value.map(item => String(item || '')) : []
  projectForm.planned_start_date = values.length === 2 ? values[0] : ''
  projectForm.planned_end_date = values.length === 2 ? values[1] : ''
}
async function saveProject() { if (!String(projectForm.project_code || '').trim() || !String(projectForm.project_name || '').trim()) { ElMessage.warning('请填写项目编号和项目名称'); return }; if (!validDateRange(projectForm.planned_start_date, projectForm.planned_end_date) || !validDateRange(projectForm.planned_start_date, projectForm.actual_end_date)) { ElMessage.warning('项目结束日期必须大于等于开始日期'); return }; saving.value = true; try { const response = projectEditingId.value ? await updateProject(projectEditingId.value, projectForm) : await createProject(projectForm); selectedProject.value = response.data.data; ensureProjectUserOptions(selectedProject.value); resetSettingsForm(); projectDialog.value = false; await loadWorkbench(); await router.push({ name: 'project-detail', params: { projectId: String(selectedProject.value.id) } }); ElMessage.success(projectEditingId.value ? '项目已更新' : '项目已创建') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目保存失败')) } finally { saving.value = false } }
async function removeProject() { if (!selectedProject.value) return; try { await ElMessageBox.confirm('删除项目后，项目计划、成员和角色也将不再显示，确认继续吗？', '删除确认', { type: 'warning' }); await deleteProject(selectedProject.value.id); selectedProject.value = null; await router.push({ name: 'projects' }); await loadWorkbench(); ElMessage.success('项目已删除') } catch (error) { const action = messageBoxAction(error); if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目删除失败')) } }
function openCreatePlanForStageRow(row: PlanTimelineRow, phase: PlanTimelineStage) { planEditingId.value = null; planParentName.value = ''; resetPlanForm(); planForm.phase = phase.key === '__UNASSIGNED__' ? '' : phase.key; planForm.group_id = row.id; planDialog.value = true }
function openCreateChildPlan(row: ProjectPlan) { planChildrenDialog.value = false; planEditingId.value = null; planParentName.value = row.plan_name; resetPlanForm(); planForm.parent_id = row.id; planForm.group_id = row.group_id || null; planForm.phase = row.phase || ''; planDialog.value = true }
async function openEditPlan(row: ProjectPlan) { planChildrenDialog.value = false; await loadUsers(); addUserOption(row.owner_id, row.owner_name); planEditingId.value = row.id; planParentName.value = row.parent_id ? (plans.value.find(plan => plan.id === row.parent_id)?.plan_name || '') : ''; Object.assign(planForm, { parent_id: row.parent_id, group_id: row.group_id || null, phase: row.phase || '', plan_name: row.plan_name, description: row.description || '', owner_id: row.owner_id || null, lead_org_id: row.lead_org_id || null, cooperating_org_ids: row.cooperating_org_ids || [], planned_start_date: row.planned_start_date || '', planned_end_date: row.planned_end_date || '', progress: row.progress, status: row.status, sort_no: row.sort_no }); planDateRange.value = row.planned_start_date && row.planned_end_date ? [row.planned_start_date, row.planned_end_date] : []; planDialog.value = true }
function openPlanChildren(row: ProjectPlan) { selectedPlanForChildrenId.value = row.id; planChildrenDialog.value = true }
function closePlanChildren() { planChildrenDialog.value = false; selectedPlanForChildrenId.value = null }
function timelineBarStyle(plan: ProjectPlan) {
  const axis = planTimelineAxis.value
  const range = planRange(plan)
  if (!axis || !range) return { top: '12px' }
  const total = axis.max - axis.min
  const start = Math.max(axis.min, range.start)
  const end = Math.min(axis.max, range.end)
  const left = Math.max(0, Math.min(100, (start - axis.min) / total * 100))
  const width = Math.max(.8, Math.min(100 - left, (end - start) / total * 100))
  return { left: `${left}%`, width: `${width}%`, top: '12px' }
}
function timelineRowHeight(row: PlanTimelineRow) { return `${Math.max(58, row.masters.some(plan => !planRange(plan)) ? 72 : 58)}px` }
function timelineMastersWithDate(row: PlanTimelineRow) { return sortTimelinePlans(row.masters.filter(plan => planRange(plan))) }
function timelineMastersWithoutDate(row: PlanTimelineRow) { return row.masters.filter(plan => !planRange(plan)) }
function timelineGridStyle() { return { gridTemplateColumns: `repeat(${Math.max(1, planTimelineMonths.value.length)}, minmax(112px, 1fr))` } }
function onPlanDateRangeChange(value: unknown) {
  const values = Array.isArray(value) ? value.map(item => String(item || '')) : []
  planForm.planned_start_date = values.length === 2 ? values[0] : ''
  planForm.planned_end_date = values.length === 2 ? values[1] : ''
}
function mainPlanSequenceMessage() {
  const groupId = Number(planForm.group_id || 0)
  if (!groupId || Number(planForm.parent_id || 0)) return ''
  const editingId = Number(planEditingId.value || 0)
  const existing = plans.value.filter(plan => !Number(plan.parent_id || 0) && Number(plan.group_id || 0) === groupId && plan.id !== editingId)
  const current = editingId ? plans.value.find(plan => plan.id === editingId) : null
  const sequence = existing.map(plan => ({ id: plan.id, created_at: plan.created_at, plan_name: plan.plan_name, planned_start_date: plan.planned_start_date, planned_end_date: plan.planned_end_date }))
  sequence.push({ id: editingId || Number.MAX_SAFE_INTEGER, created_at: current?.created_at, plan_name: String(planForm.plan_name || ''), planned_start_date: String(planForm.planned_start_date || '') || null, planned_end_date: String(planForm.planned_end_date || '') || null })
  sequence.sort((left, right) => {
    const leftCreated = Date.parse(String(left.created_at || ''))
    const rightCreated = Date.parse(String(right.created_at || ''))
    return (Number.isFinite(leftCreated) ? leftCreated : left.id) - (Number.isFinite(rightCreated) ? rightCreated : right.id) || left.id - right.id
  })
  for (let index = 1; index < sequence.length; index++) {
    const previous = sequence[index - 1]
    const currentPlan = sequence[index]
    if (!previous.planned_end_date || !currentPlan.planned_start_date || String(currentPlan.planned_start_date) <= String(previous.planned_end_date)) return '同组内后建主计划开始日期必须大于前一个主计划结束日期'
  }
  return ''
}
async function savePlan() { if (!selectedProject.value || !String(planForm.plan_name || '').trim()) { ElMessage.warning('请填写计划名称'); return }; const isMainPlan = !Number(planForm.parent_id || 0); if (isMainPlan && (!String(planForm.planned_start_date || '').trim() || !String(planForm.planned_end_date || '').trim())) { ElMessage.warning('主计划必须填写时间范围'); return }; if (!validDateRange(planForm.planned_start_date, planForm.planned_end_date)) { ElMessage.warning('计划结束日期必须大于等于开始日期'); return }; const sequenceMessage = mainPlanSequenceMessage(); if (sequenceMessage) { ElMessage.warning(sequenceMessage); return }; saving.value = true; try { if (planEditingId.value) await updateProjectPlan(selectedProject.value.id, planEditingId.value, planForm); else await createProjectPlan(selectedProject.value.id, planForm); planDialog.value = false; await refreshSelectedProject(); ElMessage.success('计划已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '计划保存失败')) } finally { saving.value = false } }
function openCreateRisk() { riskEditingId.value = null; resetRiskForm(); riskEntryMode.value = 'report'; riskMode.value = 'report'; riskComments.value = []; riskCommentText.value = ''; riskDialog.value = true }
function riskDateInputValue(value: unknown) { const formatted = formatDateOnly(value); return formatted === '-' ? '' : formatted }
function fillRiskForm(row: ProjectRisk) { riskEditingId.value = row.id; Object.assign(riskForm, { ...row, occurred_date: riskDateInputValue(row.occurred_date), expected_resolution_date: riskDateInputValue(row.expected_resolution_date), planned_resolution_date: riskDateInputValue(row.planned_resolution_date), actual_resolution_date: riskDateInputValue(row.actual_resolution_date) }) }
function openEditRisk(row: ProjectRisk) { fillRiskForm(row); riskEntryMode.value = 'report'; riskMode.value = 'report'; riskDialog.value = true }
async function openRiskProgress(row: ProjectRisk) { fillRiskForm(row); riskEntryMode.value = 'progress'; riskMode.value = 'progress'; riskCommentText.value = ''; riskDialog.value = true; await loadRiskComments() }
function openRiskTracking(row: ProjectRisk) { fillRiskForm(row); riskEntryMode.value = 'tracking'; riskMode.value = 'tracking'; riskDialog.value = true }
async function switchRiskMode(mode: RiskMode) {
  if (riskEntryMode.value !== 'tracking' || !riskEditingId.value || !canUpdateRisk.value || !isOwner.value || !['progress', 'tracking'].includes(mode)) return
  riskMode.value = mode
  if (mode === 'progress') {
    riskCommentText.value = ''
    await loadRiskComments()
  }
}
async function loadRiskComments() { if (!selectedProject.value || !riskEditingId.value) return; riskCommentsLoading.value = true; try { riskComments.value = (await getProjectRiskComments(selectedProject.value.id, riskEditingId.value)).data.data } catch (error) { riskComments.value = []; ElMessage.error(apiErrorMessage(error, '进展评论加载失败')) } finally { riskCommentsLoading.value = false } }
function riskPayload(keys: string[]) { return Object.fromEntries(keys.filter(key => Object.prototype.hasOwnProperty.call(riskForm, key)).map(key => [key, riskForm[key]])) }
async function saveRisk() {
  if (!selectedProject.value) return
  if (riskMode.value === 'progress') return
  if (riskMode.value === 'report' && !String(riskForm.current_status || '').trim()) { ElMessage.warning('请选择当前状态'); return }
  const reportFields = ['occurred_date', 'project_phase', 'urgency', 'report_level', 'current_status', 'proposer_org_id', 'proposer_subsystem', 'proposer_contact_name', 'proposer_contact_phone', 'involved_org_id', 'involved_subsystem', 'problem_description', 'expected_resolution_date', 'suggested_solution', 'current_handler_name', 'current_handler_phone']
  const trackingFields = ['attention_level', 'problem_nature', 'problem_domain', 'pmo_contact', 'escalation_level', 'current_problem_level', 'planned_resolution_date', 'actual_resolution_date', 'resolution_solution']
  const payload = riskPayload(riskMode.value === 'tracking' ? trackingFields : reportFields)
  saving.value = true
  try { if (!riskEditingId.value) await createProjectRisk(selectedProject.value.id, payload); else await updateProjectRisk(selectedProject.value.id, riskEditingId.value, payload); riskDialog.value = false; await refreshSelectedProject(); ElMessage.success(riskMode.value === 'tracking' ? '问题跟踪已保存' : '项目风险已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, riskMode.value === 'tracking' ? '问题跟踪保存失败' : '项目风险保存失败')) } finally { saving.value = false }
}
async function submitRiskComment() { if (!selectedProject.value || !riskEditingId.value) return; const comment = riskCommentText.value.trim(); if (!comment) { ElMessage.warning('请输入评论内容'); return }; riskCommentSubmitting.value = true; try { await createProjectRiskComment(selectedProject.value.id, riskEditingId.value, { comment_text: comment }); riskCommentText.value = ''; await loadRiskComments(); ElMessage.success('评论已发表') } catch (error) { ElMessage.error(apiErrorMessage(error, '评论发表失败')) } finally { riskCommentSubmitting.value = false } }
function commentUser(comment: ProjectRiskComment) { return { id: comment.user_id, username: comment.username || '', displayName: comment.display_name || comment.username || '未命名用户', orgName: comment.org_name || null, avatarUrl: comment.avatar_url || null } }
async function removeRisk(row: ProjectRisk) { if (!selectedProject.value) return; try { await ElMessageBox.confirm(`确认删除风险 ${row.risk_code} 吗？删除后不可恢复。`, '删除确认', { type: 'warning' }); await deleteProjectRisk(selectedProject.value.id, row.id); await refreshSelectedProject(); ElMessage.success('项目风险已删除') } catch (error) { const action = messageBoxAction(error); if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目风险删除失败')) } }
async function createStageRow(phase = '') {
  if (!selectedProject.value || !phase || phase === '__UNASSIGNED__' || saving.value) return
  const nextSortNo = planGroups.value.filter(group => group.phase === phase).reduce((max, group) => Math.max(max, Number(group.sort_no || 0)), -1) + 1
  saving.value = true
  try {
    const response = await createProjectPlanGroup(selectedProject.value.id, { phase, color_key: 'brand', sort_no: nextSortNo })
    newlyCreatedStageRowIds.value = new Set([...newlyCreatedStageRowIds.value, response.data.data.id])
    await refreshSelectedProject()
    ElMessage.success('阶段已新增')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '阶段新增失败'))
  } finally { saving.value = false }
}
async function removeStageRow(row: PlanTimelineRow) {
  if (!selectedProject.value || row.id == null || saving.value) return
  try {
    await ElMessageBox.confirm('确认删除该阶段行吗？删除后其中主计划和子计划将转为该阶段的未分配计划。', '删除确认', { type: 'warning' })
    saving.value = true
    await deleteProjectPlanGroup(selectedProject.value.id, row.id)
    if (newlyCreatedStageRowIds.value.has(row.id)) {
      const nextIds = new Set(newlyCreatedStageRowIds.value)
      nextIds.delete(row.id)
      newlyCreatedStageRowIds.value = nextIds
    }
    await refreshSelectedProject()
    ElMessage.success('阶段行已删除')
  } catch (error) {
    const action = messageBoxAction(error)
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '阶段行删除失败'))
  } finally { saving.value = false }
}
function startPlanDrag(row: ProjectPlanRow, event: DragEvent) { if (row.node_type !== 'plan' || row.parent_id) { event.preventDefault(); return }; draggingPlanId.value = row.id; event.dataTransfer?.setData('text/plain', String(row.id)); if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move' }
function endPlanDrag() { draggingPlanId.value = null }
async function dropPlanOnGroup(row: ProjectPlanRow, event: DragEvent) { event.preventDefault(); event.stopPropagation(); if (!selectedProject.value || row.node_type !== 'group' || draggingPlanId.value == null) return; const plan = plans.value.find(item => item.id === draggingPlanId.value); if (!plan || plan.parent_id) return; const groupId = row.group_id == null ? null : Number(row.group_id); if (plan.group_id === groupId) { endPlanDrag(); return }; saving.value = true; try { await moveProjectPlanToGroup(selectedProject.value.id, plan.id, groupId); await refreshSelectedProject(); ElMessage.success('主计划及其子计划已归入该阶段') } catch (error) { ElMessage.error(apiErrorMessage(error, '阶段归属调整失败')) } finally { saving.value = false; endPlanDrag() } }
function planRowClassName({ row }: { row: ProjectPlanRow }) {
  const palette = planPaletteKey(row)
  if (row.node_type === 'stage') return `project-plan-row--stage project-plan-stage-${row.stage_index || 0}`
  if (row.node_type === 'group') return `project-plan-row--group project-plan-row--group-${row.group_id == null ? `ungrouped-${row.stage_index || 0}` : row.group_id} project-plan-palette--${palette}`
  return `project-plan-row--${row.parent_id ? 'child' : 'main'} project-plan-row--plan-${row.id} project-plan-palette--${palette}`
}
function planPaletteKey(row: ProjectPlanRow): ProjectPlanGroupColorToken {
  if (row.node_type !== 'plan') return row.group_color_key || 'brand'
  return planGroups.value.find(group => Number(group.id) === Number(row.group_id))?.color_key || 'brand'
}
function findPlanGroupRow(element: HTMLElement) {
  const className = [...element.classList].find(value => value.startsWith('project-plan-row--group-'))
  if (!className) return null
  const groupKey = className.replace('project-plan-row--group-', '')
  return planTree.value.flatMap(stage => stage.children || []).find(row => row.node_type === 'group' && (groupKey.startsWith('ungrouped-') ? row.group_id == null && `ungrouped-${row.stage_index || 0}` === groupKey : Number(row.group_id) === Number(groupKey))) || null
}
function applyPlanRowDragability() {
  const tableBody = document.querySelector('.project-plans-table .el-table__body-wrapper tbody')
  if (!tableBody) return
  tableBody.querySelectorAll('tr').forEach(rowElement => {
    const row = rowElement as HTMLElement
    row.removeAttribute('draggable')
    row.removeEventListener('dragstart', handlePlanRowDragStart)
    row.removeEventListener('dragend', endPlanDrag)
    row.removeEventListener('dragover', handlePlanGroupDragOver)
    row.removeEventListener('drop', handlePlanGroupDrop)
    row.classList.remove('is-dragging', 'is-drag-over')
    const mainPlanClass = [...row.classList].find(value => value.startsWith('project-plan-row--plan-'))
    const isMainPlan = row.classList.contains('project-plan-row--main')
    const groupRow = findPlanGroupRow(row)
    if (isMainPlan && canUpdatePlan.value && mainPlanClass) {
      row.draggable = true
      row.title = '长按主计划行并拖动到目标分组'
      row.addEventListener('dragstart', handlePlanRowDragStart)
      row.addEventListener('dragend', endPlanDrag)
    } else if (groupRow) {
      row.addEventListener('dragover', handlePlanGroupDragOver)
      row.addEventListener('drop', handlePlanGroupDrop)
    }
  })
}
function handlePlanRowDragStart(event: DragEvent) {
  const row = event.currentTarget as HTMLElement
  const planClass = [...row.classList].find(value => value.startsWith('project-plan-row--plan-'))
  const planId = planClass ? Number(planClass.replace('project-plan-row--plan-', '')) : NaN
  if (!Number.isFinite(planId)) { event.preventDefault(); return }
  draggingPlanId.value = planId
  event.dataTransfer?.setData('text/plain', String(planId))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
  row.classList.add('is-dragging')
}
function handlePlanGroupDragOver(event: DragEvent) {
  event.preventDefault()
  const row = event.currentTarget as HTMLElement
  row.classList.add('is-drag-over')
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
}
function handlePlanGroupDrop(event: DragEvent) {
  const row = event.currentTarget as HTMLElement
  row.classList.remove('is-drag-over')
  const groupRow = findPlanGroupRow(row)
  if (groupRow) void dropPlanOnGroup(groupRow, event)
}
async function removePlan(row: ProjectPlan) { if (!selectedProject.value) return; try { const message = row.parent_id ? '确认删除该计划吗？' : '删除主计划后，其下全部子计划也会被删除，是否继续？'; await ElMessageBox.confirm(message, '删除确认', { type: 'warning' }); await deleteProjectPlan(selectedProject.value.id, row.id); if (selectedPlanForChildrenId.value === row.id) closePlanChildren(); await refreshSelectedProject(); ElMessage.success('计划已删除') } catch (error) { const action = messageBoxAction(error); if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '计划删除失败')) } }
async function loadUsers() { if (userOptionsLoaded.value) return; userOptionsLoading.value = true; try { const options = (await getProjectUserOptions()).data.data; options.forEach(item => addUserOption(item.id, item.display_name, item.username)); userOptionsLoaded.value = true } catch (error) { ElMessage.error(apiErrorMessage(error, '用户选项加载失败')) } finally { userOptionsLoading.value = false } }
function onUserOptionsVisible(visible: boolean) { if (visible) void loadUsers() }
async function openCreateMember() { memberEditingId.value = null; memberForm.user_id = null; memberForm.org_id = selectedProjectOrganizationId.value; memberForm.role_ids = []; memberForm.status = 1; await loadUsers(); memberDialog.value = true }
async function openEditMember(row: ProjectMember) { await loadUsers(); addUserOption(row.user_id, row.display_name); memberEditingId.value = row.id; memberForm.user_id = row.user_id; memberForm.org_id = row.org_id ? Number(row.org_id) : null; memberForm.role_ids = row.roles.map(role => role.id); memberForm.status = row.status; memberDialog.value = true }
async function saveMember() { if (!selectedProject.value || (!memberEditingId.value && !memberForm.user_id)) { ElMessage.warning('请选择成员'); return }; saving.value = true; try { const payload = { user_id: memberForm.user_id, org_id: memberForm.org_id, role_ids: memberForm.role_ids, status: memberForm.status }; if (memberEditingId.value) await updateProjectMember(selectedProject.value.id, memberEditingId.value, payload); else await createProjectMember(selectedProject.value.id, payload); memberDialog.value = false; await refreshSelectedProject(); ElMessage.success('成员已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '成员保存失败')) } finally { saving.value = false } }
async function removeMember(row: ProjectMember) { if (!selectedProject.value) return; try { await ElMessageBox.confirm('确认移除该项目成员吗？', '移除确认', { type: 'warning' }); await deleteProjectMember(selectedProject.value.id, row.id); await refreshSelectedProject(); ElMessage.success('成员已移除') } catch (error) { const action = messageBoxAction(error); if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '成员移除失败')) } }
function openCreateRole() { roleEditingId.value = null; resetRoleForm(); roleDialog.value = true }
function openEditRole(row: ProjectRole) { roleEditingId.value = row.id; Object.assign(roleForm, { role_code: row.role_code, role_name: row.role_name, description: row.description || '', member_ids: (row.members || []).map(member => member.id) }); roleDialog.value = true }
async function saveRole() { if (!selectedProject.value || !String(roleForm.role_code || '').trim() || !String(roleForm.role_name || '').trim()) { ElMessage.warning('请填写角色编码和角色名称'); return }; saving.value = true; try { if (roleEditingId.value) await updateProjectRole(selectedProject.value.id, roleEditingId.value, roleForm); else await createProjectRole(selectedProject.value.id, roleForm); roleDialog.value = false; await refreshSelectedProject(); ElMessage.success('项目角色已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目角色保存失败')) } finally { saving.value = false } }
async function removeRole(row: ProjectRole) { if (!selectedProject.value) return; try { await ElMessageBox.confirm('确认删除该项目角色吗？', '删除确认', { type: 'warning' }); await deleteProjectRole(selectedProject.value.id, row.id); await refreshSelectedProject(); ElMessage.success('项目角色已删除') } catch (error) { const action = messageBoxAction(error); if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目角色删除失败')) } }
function openCreateProjectOrganization(parentId = 0) { projectOrganizationEditingId.value = null; resetProjectOrganizationForm(parentId); projectOrganizationDialog.value = true }
function openEditProjectOrganization(row: ProjectOrganization) { projectOrganizationEditingId.value = row.id; Object.assign(projectOrganizationForm, { parent_id: row.parent_id, org_code: row.org_code, org_name: row.org_name, sort_no: row.sort_no, status: row.status }); projectOrganizationDialog.value = true }
async function saveProjectOrganization() { if (!selectedProject.value || !String(projectOrganizationForm.org_code || '').trim() || !String(projectOrganizationForm.org_name || '').trim()) { ElMessage.warning('请填写项目组织编码和名称'); return }; saving.value = true; try { if (projectOrganizationEditingId.value) await updateProjectOrganization(selectedProject.value.id, projectOrganizationEditingId.value, projectOrganizationForm); else await createProjectOrganization(selectedProject.value.id, projectOrganizationForm); projectOrganizationDialog.value = false; await refreshSelectedProject(); ElMessage.success('项目组织已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目组织保存失败')) } finally { saving.value = false } }
async function removeProjectOrganization(row: ProjectOrganization) { if (!selectedProject.value) return; try { await ElMessageBox.confirm(`确认删除项目组织“${row.org_name}”吗？`, '删除确认', { type: 'warning' }); await deleteProjectOrganization(selectedProject.value.id, row.id); if (selectedProjectOrganizationId.value === row.id) selectedProjectOrganizationId.value = null; await refreshSelectedProject(); ElMessage.success('项目组织已删除') } catch (error) { const action = messageBoxAction(error); if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目组织删除失败')) } }
function projectStatusTone(status: string): 'primary' | 'success' | 'warning' | 'danger' { return status === 'COMPLETED' ? 'success' : status === 'SUSPENDED' ? 'danger' : status === 'RUNNING' ? 'primary' : 'warning' }
function planStatusTone(status: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' { return status === 'COMPLETED' ? 'success' : status === 'BLOCKED' ? 'danger' : status === 'IN_PROGRESS' ? 'primary' : 'info' }
function riskStatusTone(value?: string | null, label?: string | null): 'primary' | 'success' | 'warning' | 'danger' | 'info' { const text = `${value || ''}${label || ''}`; return text.includes('关闭') || text.includes('解决') || value === 'CLOSED' || value === 'RESOLVED' ? 'success' : text.includes('升级') || text.includes('高') || value === 'OPEN' ? 'warning' : text.includes('处理中') || value === 'PROCESSING' ? 'primary' : 'info' }
watch([selectedProject, activeTab, plans], () => { void nextTick(applyPlanRowDragability) }, { deep: true })
watch(planTree, () => { void nextTick(applyPlanRowDragability) }, { deep: true })
watch(() => route.params.projectId, () => {
  if (route.params.projectId) void syncProjectFromRoute()
  else { selectedProject.value = null; detailLoading.value = false; tabContentLoading.value = false; activeTab.value = 'overview'; void loadWorkbench() }
})
watch(() => route.query.tab, (tab) => {
  if (route.params.projectId) {
    const nextTab = normalizeProjectTab(tab)
    if (nextTab !== activeTab.value) activeTab.value = nextTab
  }
})
onMounted(async () => { await loadOptions(); if (route.params.projectId) await syncProjectFromRoute(); else await loadWorkbench() })
onBeforeUnmount(() => { clearTabLoadingTimer() })
</script>

<template>
  <section class="project-page">
    <div v-if="loading && !isProjectDetailRoute" class="project-loading-overlay" role="status" aria-live="polite">
      <div class="project-loading-panel">
        <span class="project-loading-spinner" aria-hidden="true" />
        <span>{{ detailLoading ? '正在加载项目详情' : '正在加载项目' }}</span>
      </div>
    </div>
    <template v-if="!selectedProject && !isProjectDetailRoute">
      <header class="project-page__heading"><div><span class="panel-kicker">项目工作台 / PROJECT SPACE</span><h1>项目管理</h1><p>从项目卡片进入计划、组织架构和项目风险。</p></div><el-button v-if="canCreateProject" type="primary" @click="openCreateProject"><el-icon><Plus /></el-icon>新建项目</el-button></header>
      <div class="project-card-grid">
        <el-card v-for="project in projects" :key="project.id" shadow="never" class="project-card" @click="openProject(project)">
          <div class="project-card__top"><span class="project-card__icon"><el-icon><Briefcase /></el-icon></span><UiStatusTag :value="project.status" :labels="projectStatusLabels" :tone="projectStatusTone(project.status)" /></div>
            <div class="project-card__identity"><strong>{{ project.project_name }}</strong><span>{{ project.project_code }}</span></div>
          <p>{{ project.description || '暂无项目描述' }}</p>
          <div class="project-card__progress"><div><span>计划实际进度</span><b>{{ Math.round(Number(project.plan_progress || 0)) }}%</b></div><el-progress :percentage="Math.round(Number(project.plan_progress || 0))" :show-text="false" :stroke-width="6" /></div>
          <div class="project-card__facts"><span><el-icon><User /></el-icon>{{ project.owner_name || '未设置负责人' }}</span><span><el-icon><Calendar /></el-icon>{{ formatDateOnly(project.planned_end_date) || '未设置日期' }}</span></div>
          <div class="project-card__footer"><span>{{ project.member_count }} 位成员</span><span>{{ project.plan_count }} 项计划</span><el-icon><ArrowLeft /></el-icon></div>
        </el-card>
        <el-empty v-if="workbenchLoaded && !loading && !projects.length" description="暂无可见项目" />
      </div>
    </template>

    <template v-else-if="selectedProject">
      <header class="project-detail__heading"><div class="project-detail__title"><el-button text circle title="返回项目工作台" @click="router.push({ name: 'projects' })"><el-icon><ArrowLeft /></el-icon></el-button><div><span class="panel-kicker">{{ selectedProject.project_code }}</span><h1>{{ selectedProject.project_name }}</h1></div><UiStatusTag :value="selectedProject.status" :labels="projectStatusLabels" :tone="projectStatusTone(selectedProject.status)" /></div><div class="project-detail__actions"><el-button v-if="canUpdateProject && isOwner" @click="openEditProject"><el-icon><Edit /></el-icon>编辑项目</el-button><el-button v-if="canDeleteProject && isOwner" type="danger" plain @click="removeProject"><el-icon><Delete /></el-icon>删除项目</el-button></div></header>
      <div class="project-tabs-shell">
      <el-tabs v-model="activeTab" class="project-tabs" @tab-change="setProjectTab">
        <el-tab-pane name="overview" label="项目概览"><div class="project-overview">
          <section class="project-overview__hero">
            <div class="project-overview__identity"><span class="project-overview__icon"><el-icon><Briefcase /></el-icon></span><div><span class="panel-kicker">{{ selectedProject.project_code }}</span><h2>{{ selectedProject.project_name }}</h2><p>{{ projectStatusLabels[selectedProject.status] }}</p></div></div>
            <div class="project-overview__hero-progress"><span>整体计划进度</span><strong>{{ Math.round(Number(selectedProject.plan_progress || 0)) }}%</strong><el-progress :percentage="Math.round(Number(selectedProject.plan_progress || 0))" :show-text="false" :stroke-width="8" /></div>
            <div class="project-overview__facts"><div><span>项目负责人</span><strong>{{ selectedProject.owner_name || '-' }}</strong></div><div><span>计划周期</span><strong>{{ formatDateOnly(selectedProject.planned_start_date) || '-' }} 至 {{ formatDateOnly(selectedProject.planned_end_date) || '-' }}</strong></div><div><span>项目成员</span><strong>{{ selectedProject.member_count }} 人</strong></div><div><span>项目计划</span><strong>{{ selectedProject.plan_count }} 项</strong></div><div><span>当前状态</span><strong>{{ projectStatusLabels[selectedProject.status] }}</strong></div></div>
            <div class="project-overview__description-copy"><span class="project-overview__description-label">项目说明</span><p>{{ selectedProject.description || '暂无项目描述，点击编辑项目补充说明。' }}</p></div>
          </section>
          <section class="project-overview__schedule project-overview__schedule--readonly"><el-card shadow="never" class="project-overview__chart project-overview__timeline-card"><div class="project-section-heading"><div><span class="panel-kicker">项目计划</span><h3>主计划排期</h3></div><span class="muted">只读 · 月份</span></div><div v-if="hasOverviewPlanTimeline" class="project-plan-timeline project-plan-timeline--overview" :style="timelineLayoutStyle()"><div class="project-plan-timeline__header"><div class="project-plan-timeline__axis-label">项目阶段</div><div class="project-plan-timeline__months" :style="timelineGridStyle()"><span v-for="month in planTimelineMonths" :key="month.timestamp">{{ month.label }}</span><span v-if="todayTimelinePosition" class="project-plan-timeline__today-marker" :style="{ left: todayTimelinePosition }" aria-label="今天"><strong>今天</strong></span></div></div><div class="project-plan-timeline__body"><section v-for="stage in overviewPlanTimelineStages" :key="stage.key" class="project-plan-timeline__stage-row project-plan-timeline__stage-row--overview" :style="{ minHeight: overviewTimelineStageHeight(stage) }"><div class="project-plan-timeline__stage"><div><el-icon><Calendar /></el-icon><strong>{{ stage.name }}</strong></div><small>{{ stage.masters.length }} 个主计划</small></div><div class="project-plan-timeline__track" :style="{ minHeight: overviewTimelineStageHeight(stage) }"><div class="project-plan-timeline__grid" :style="timelineGridStyle()" aria-hidden="true"><i v-for="month in planTimelineMonths" :key="month.timestamp" /></div><span v-if="todayTimelinePosition" class="project-plan-timeline__today-line" :style="{ left: todayTimelinePosition }" aria-hidden="true" /><div v-for="row in overviewTimelineRows(stage)" :key="row.plan.id" :class="['project-plan-timeline__bar', 'project-plan-timeline__bar--readonly', `project-plan-palette--${row.plan.group_id ? (planGroups.find(group => group.id === row.plan.group_id)?.color_key || 'brand') : 'muted'}`, `project-plan-status--${String(row.plan.status || 'NOT_STARTED').toLowerCase()}`]" :style="overviewTimelineBarStyle(row.plan, row.lane)" :title="row.plan.plan_name"><span class="project-plan-timeline__bar-progress" :style="{ width: `${Math.max(0, Math.min(100, Number(row.plan.progress || 0)))}%` }" /><span class="project-plan-timeline__bar-content"><strong>{{ row.plan.plan_name }}</strong><small>{{ Math.round(Number(row.plan.progress || 0)) }}%</small></span></div><span v-for="plan in overviewTimelineUndated(stage)" :key="`undated-${plan.id}`" class="project-plan-timeline__undated">未排期：{{ plan.plan_name }}</span><span v-if="!stage.masters.length" class="project-plan-timeline__empty">暂无主计划</span></div></section></div></div><div v-else class="project-gantt-empty"><el-empty description="暂无项目计划" :image-size="48" /></div><div class="project-gantt-legend"><span><i class="is-completed" />已完成</span><span><i class="is-progress" />进行中</span><span><i class="is-pending" />未开始</span><span><i class="is-blocked" />已阻塞</span></div></el-card></section>
        </div></el-tab-pane>
      <el-tab-pane name="plans" label="项目计划">
        <div class="project-tab-panel project-plan-timeline-panel">
          <div class="project-section-heading">
            <div><span class="panel-kicker">PROJECT PLAN</span><h2>项目计划</h2><p class="project-plan-group-hint"><el-icon><Calendar /></el-icon>阶段为纵轴，月份为横轴，点击主计划查看子计划</p></div>
          </div>
          <div v-if="planTimelineStages.length" class="project-plan-timeline" :style="timelineLayoutStyle()">
            <div class="project-plan-timeline__header">
              <div class="project-plan-timeline__axis-label">项目阶段</div>
              <div class="project-plan-timeline__stage-row-axis-label">阶段操作</div>
              <div class="project-plan-timeline__months" :style="timelineGridStyle()"><span v-for="month in planTimelineMonths" :key="month.timestamp">{{ month.label }}</span><span v-if="todayTimelinePosition" class="project-plan-timeline__today-marker" :style="{ left: todayTimelinePosition }" aria-label="今天"><strong>今天</strong></span></div>
            </div>
            <div class="project-plan-timeline__body">
              <section v-for="stage in planTimelineStages" :key="stage.key" class="project-plan-timeline__stage-row" :style="{ gridTemplateRows: `repeat(${stage.rows.length}, minmax(58px, auto))` }">
                <div class="project-plan-timeline__stage" :style="{ gridRow: `1 / span ${stage.rows.length}` }">
                  <div><el-icon><Calendar /></el-icon><strong>{{ stage.name }}</strong></div>
                  <small>{{ stage.masters.length }} 个主计划</small>
                  <el-button v-if="canCreatePlan && stage.key !== '__UNASSIGNED__'" class="project-plan-timeline__stage-action" text type="primary" :loading="saving" @click.stop="createStageRow(stage.key)"><el-icon><Plus /></el-icon>新增阶段</el-button>
                </div>
                <template v-for="row in stage.rows" :key="row.key">
                  <div class="project-plan-timeline__stage-row-actions" :class="`project-plan-palette--${row.colorKey}`">
                    <el-button v-if="canCreatePlan && row.id != null && newlyCreatedStageRowIds.has(row.id)" class="project-plan-timeline__row-action" text type="primary" @click.stop="openCreatePlanForStageRow(row, stage)"><el-icon><Plus /></el-icon>新增主计划</el-button>
                    <el-tooltip v-if="canDeletePlan && row.id != null" content="删除该行"><el-button class="project-plan-timeline__row-delete" text type="danger" aria-label="删除该行" :disabled="saving" @click.stop="removeStageRow(row)"><el-icon><Delete /></el-icon></el-button></el-tooltip>
                  </div>
                  <div class="project-plan-timeline__track" :style="{ minHeight: timelineRowHeight(row) }">
                    <div class="project-plan-timeline__grid" :style="timelineGridStyle()" aria-hidden="true"><i v-for="month in planTimelineMonths" :key="month.timestamp" /></div><span v-if="todayTimelinePosition" class="project-plan-timeline__today-line" :style="{ left: todayTimelinePosition }" aria-hidden="true" />
                    <button v-for="master in timelineMastersWithDate(row)" :key="master.id" type="button" :class="['project-plan-timeline__bar', `project-plan-palette--${row.colorKey}`, `project-plan-status--${String(master.status || 'NOT_STARTED').toLowerCase()}`]" :style="timelineBarStyle(master)" :title="master.plan_name" @click="openPlanChildren(master)">
                      <span class="project-plan-timeline__bar-progress" :style="{ width: `${Math.max(0, Math.min(100, Number(master.progress || 0)))}%` }" />
                      <span class="project-plan-timeline__bar-content"><strong>{{ master.plan_name }}</strong><small>{{ Math.round(Number(master.progress || 0)) }}%</small></span>
                    </button>
                    <button v-for="master in timelineMastersWithoutDate(row)" :key="`undated-${master.id}`" type="button" class="project-plan-timeline__undated" @click="openPlanChildren(master)">未排期：{{ master.plan_name }}</button>
                    <span v-if="!row.masters.length" class="project-plan-timeline__empty">暂无主计划</span>
                  </div>
                </template>
              </section>
            </div>
          </div>
          <el-empty v-else description="暂无项目阶段" :image-size="64" />
          <div class="project-plan-timeline__legend"><span><i class="is-completed" />已完成</span><span><i class="is-progress" />进行中</span><span><i class="is-pending" />未开始</span><span><i class="is-blocked" />已阻塞</span><span class="project-plan-timeline__legend-note">主计划可点击查看子计划</span></div>
        </div>
      </el-tab-pane>
      <el-tab-pane name="risk" label="项目风险"><div class="project-tab-panel project-risk-panel"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT RISK</span><h2>项目风险</h2><p class="project-risk__hint">记录项目问题、进展和升级解决情况，风险编号由项目设置规则自动生成。</p></div><el-button v-if="canCreateRisk && isOwner" type="primary" @click="openCreateRisk"><el-icon><Plus /></el-icon>新增风险</el-button></div><UiDataTable class="project-risks-table" :data="risks" row-key="id" border empty-text="暂无项目风险"><el-table-column prop="risk_code" label="编号" width="190" fixed="left" /><el-table-column prop="occurred_date" label="发生时间" width="120"><template #default="scope">{{ formatDateOnly(scope.row.occurred_date) || '-' }}</template></el-table-column><el-table-column label="项目阶段" min-width="130"><template #default="scope">{{ scope.row.project_phase_name || scope.row.project_phase || '-' }}</template></el-table-column><el-table-column label="紧急程度" width="110"><template #default="scope">{{ scope.row.urgency_name || scope.row.urgency || '-' }}</template></el-table-column><el-table-column label="当前状态" width="120"><template #default="scope"><UiStatusTag :value="scope.row.current_status" :labels="{ [scope.row.current_status]: scope.row.current_status_name || scope.row.current_status || '-' }" :tone="riskStatusTone(scope.row.current_status, scope.row.current_status_name)" /></template></el-table-column><el-table-column label="提出组织/组" min-width="160"><template #default="scope">{{ scope.row.proposer_org_name || '-' }}</template></el-table-column><el-table-column label="问题描述" min-width="260" show-overflow-tooltip><template #default="scope">{{ scope.row.problem_description || '-' }}</template></el-table-column><el-table-column label="关注等级" width="120"><template #default="scope">{{ scope.row.attention_level_name || scope.row.attention_level || '-' }}</template></el-table-column><el-table-column label="当前处理人" min-width="160"><template #default="scope">{{ scope.row.current_handler_name ? `${scope.row.current_handler_name}${scope.row.current_handler_phone ? ` / ${scope.row.current_handler_phone}` : ''}` : '-' }}</template></el-table-column><el-table-column label="操作" width="290" fixed="right"><template #default="scope"><el-button v-if="canUpdateRisk && isOwner" link type="primary" @click="openEditRisk(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button link type="success" @click="openRiskProgress(scope.row)"><el-icon><ChatDotRound /></el-icon>进度描述</el-button><el-button v-if="canUpdateRisk && isOwner" link type="warning" @click="openRiskTracking(scope.row)"><el-icon><Edit /></el-icon>问题跟踪</el-button><el-button v-if="canDeleteRisk && isOwner" link type="danger" @click="removeRisk(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></template></el-table-column></UiDataTable></div></el-tab-pane>
       <el-tab-pane name="organization" label="项目组织架构"><div class="project-tab-panel project-organization-panel"><div class="project-organization-layout"><aside class="project-organization-tree-panel"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT STRUCTURE</span><h2>项目组织树</h2><p class="project-organization__hint">项目独立维护，不读取系统组织架构。</p></div><el-button v-if="canCreateMember && isOwner" type="primary" circle title="新增根组织" @click="openCreateProjectOrganization()"><el-icon><Plus /></el-icon></el-button></div><button type="button" class="project-organization-tree__all" :class="{ 'is-active': selectedProjectOrganizationId === null }" @click="selectedProjectOrganizationId = null"><el-icon><Folder /></el-icon><span>全部机构</span><strong>{{ members.length }}</strong></button><el-tree v-if="projectOrganizationTreeOptions.length" class="project-organization-tree" :data="projectOrganizationTreeOptions" node-key="value" default-expand-all highlight-current @node-click="selectProjectOrganization"><template #default="{ data }"><div class="project-organization-tree__node"><span class="project-organization-tree__node-label" :title="data.label">{{ data.label }}</span><span v-if="isOwner && (canCreateMember || canUpdateMember || canDeleteMember)" class="project-organization-tree__node-actions"><el-button v-if="canCreateMember" text circle title="新增下级组织" @click.stop="openCreateProjectOrganization(Number(data.value))"><el-icon><Plus /></el-icon></el-button><el-button v-if="canUpdateMember" text circle title="编辑组织" @click.stop="projectOrganizationById(data.value) && openEditProjectOrganization(projectOrganizationById(data.value)!)"><el-icon><Edit /></el-icon></el-button><el-button v-if="canDeleteMember" text circle title="删除组织" @click.stop="projectOrganizationById(data.value) && removeProjectOrganization(projectOrganizationById(data.value)!)"><el-icon><Delete /></el-icon></el-button></span></div></template></el-tree><el-empty v-else description="暂无项目组织，请先新增根组织" :image-size="56" /></aside><div class="project-organization-maintenance"><section class="project-organization-section"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT MEMBERS</span><h2>项目成员</h2><p class="project-organization__hint">将成员挂接到项目机构，并维护成员承担的项目角色。</p></div><el-button v-if="canCreateMember && isOwner" type="primary" @click="openCreateMember"><el-icon><Plus /></el-icon>添加成员</el-button></div><UiDataTable :data="filteredMembers" row-key="id" border empty-text="暂无项目成员"><el-table-column label="成员" min-width="220"><template #default="scope"><UiUserIdentity :user="{ id: scope.row.user_id, username: scope.row.username, displayName: scope.row.display_name, avatarUrl: scope.row.avatar_url }" :show-profile="false" /></template></el-table-column><el-table-column prop="org_name" label="所属机构" min-width="180"><template #default="scope">{{ scope.row.org_name || '未分配机构' }}</template></el-table-column><el-table-column label="项目角色" min-width="220"><template #default="scope"><el-tag v-for="role in scope.row.roles" :key="role.id" size="small" effect="plain" class="project-role-tag">{{ role.role_name }}</el-tag><span v-if="!scope.row.roles.length" class="muted">未分配角色</span></template></el-table-column><el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '0': '停用', '1': '有效' }" /></template></el-table-column><el-table-column v-if="canUpdateMember && isOwner || canDeleteMember && isOwner" label="操作" width="150" fixed="right"><template #default="scope"><el-button v-if="canUpdateMember && isOwner" link type="primary" @click="openEditMember(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button v-if="canDeleteMember && isOwner" link type="danger" @click="removeMember(scope.row)"><el-icon><Delete /></el-icon>移除</el-button></template></el-table-column></UiDataTable></section><section class="project-organization-section"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT ROLES</span><h2>项目角色</h2><p class="project-organization__hint">维护角色定义，并直接关联项目成员。</p></div><el-button v-if="canCreateRole && isOwner" type="primary" @click="openCreateRole"><el-icon><Plus /></el-icon>新增角色</el-button></div><UiDataTable :data="roles" row-key="id" border empty-text="暂无项目角色"><el-table-column prop="role_name" label="角色名称" min-width="180" /><el-table-column prop="role_code" label="角色编码" min-width="180" /><el-table-column label="关联人员" min-width="260"><template #default="scope"><el-tag v-for="member in (scope.row.members || [])" :key="member.id" size="small" effect="plain" class="project-role-tag">{{ member.display_name }}</el-tag><span v-if="!scope.row.members?.length" class="muted">未关联人员</span></template></el-table-column><el-table-column prop="description" label="角色说明" min-width="220" show-overflow-tooltip /><el-table-column v-if="canUpdateRole && isOwner || canDeleteRole && isOwner" label="操作" width="150" fixed="right"><template #default="scope"><el-button v-if="canUpdateRole && isOwner" link type="primary" @click="openEditRole(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button v-if="canDeleteRole && isOwner" link type="danger" @click="removeRole(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></template></el-table-column></UiDataTable></section></div></div></div></el-tab-pane>
        <el-tab-pane name="settings" label="项目设置"><div class="project-tab-panel project-settings"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT SETTINGS</span><h2>项目设置</h2></div></div><div class="project-settings__layout"><el-form label-position="top" class="project-settings__form"><el-form-item label="主计划编号规则"><el-input v-model="settingsForm.plan_number_rule" maxlength="128" show-word-limit /></el-form-item><p class="project-settings__hint">主计划支持 {PROJECT_CODE}、{SEQ}、{SEQ:3}、{YYYY}、{MM}、{DD}。例如：{PROJECT_CODE}-P{SEQ:3}，项目编号为 RDC 时会生成 RDC-P001。</p><el-form-item label="子计划编号规则"><el-input v-model="settingsForm.child_plan_number_rule" maxlength="128" show-word-limit /></el-form-item><p class="project-settings__hint">子计划支持 {PARENT_CODE}、{SEQ}、{SEQ:3}、{YYYY}、{MM}、{DD}。例如：{PARENT_CODE}-S{SEQ:3}，主计划 RDC-P001 下会生成 RDC-P001-S001。</p><el-form-item label="项目风险编号规则"><el-input v-model="settingsForm.risk_number_rule" maxlength="128" show-word-limit /></el-form-item><p class="project-settings__hint">风险支持 {PROJECT_CODE}、{SEQ}、{SEQ:3}、{YYYY}、{MM}、{DD}。例如：{PROJECT_CODE}-R{SEQ:3}，项目编号为 RDC 时会生成 RDC-R001。</p><el-button v-if="canUpdateProject && isOwner" type="primary" :loading="saving" @click="saveSettings">保存设置</el-button></el-form><section class="project-settings__stages"><div class="project-settings__stages-heading"><div><h3>项目计划阶段</h3><p>阶段名称和顺序仅对当前项目生效；已有主计划的阶段将锁定配置。</p></div><el-button v-if="canManageStages" type="primary" plain @click="openCreateStage"><el-icon><Plus /></el-icon>新增阶段</el-button></div><div v-if="!projectStages.length" class="project-settings__stages-empty"><el-empty description="暂无项目阶段，请新增阶段" :image-size="48" /></div><div v-else class="project-settings__stage-list"><div v-for="(stage, index) in projectStages" :key="stage.id" class="project-settings__stage-row" :class="{ 'is-locked': stageIsLocked(stage) }"><div class="project-settings__stage-order">{{ index + 1 }}</div><div class="project-settings__stage-main"><strong>{{ stage.stage_name }}</strong><span>{{ stageIsLocked(stage) ? (stage.locked_reason || '已有主计划，无法修改或删除') : '暂无主计划，可配置' }}</span></div><div class="project-settings__stage-actions"><el-tooltip v-if="stageIsLocked(stage)" :content="stage.locked_reason || '该阶段已有主计划，不能编辑或删除'" placement="top"><el-icon class="project-settings__stage-lock"><Lock /></el-icon></el-tooltip><template v-else-if="canManageStages"><el-button text circle :disabled="index === 0 || saving" title="上移阶段" @click="reorderStage(stage, -1)"><el-icon><ArrowUp /></el-icon></el-button><el-button text circle :disabled="index === projectStages.length - 1 || saving" title="下移阶段" @click="reorderStage(stage, 1)"><el-icon><ArrowDown /></el-icon></el-button><el-button text circle title="编辑阶段" @click="openEditStage(stage)"><el-icon><Edit /></el-icon></el-button><el-button text circle type="danger" title="删除阶段" @click="removeStage(stage)"><el-icon><Delete /></el-icon></el-button></template></div></div></div></section></div></div></el-tab-pane>
        <el-tab-pane name="attachments" label="项目附件">
          <div class="project-tab-panel project-attachments-panel">
            <div class="project-section-heading">
              <div><span class="panel-kicker">PROJECT ATTACHMENTS</span><h2>项目附件</h2><p class="project-attachments__hint">项目成员可查看、预览和下载；具备项目更新权限的成员可维护分类、上传或删除。</p></div>
              <div v-if="canUpdateProject" class="project-attachments__actions">
                <el-button plain @click="openCreateAttachmentCategory"><el-icon><Plus /></el-icon>新建分类</el-button>
              </div>
            </div>
            <div class="project-attachments__workspace">
              <aside class="project-attachments__sidebar" aria-label="附件分类导航">
                <div class="project-attachments__sidebar-heading"><div><el-icon><Folder /></el-icon><div><strong>附件分类</strong><span>按分类浏览</span></div></div><b>{{ attachmentCategories.length + 2 }}</b></div>
                <div v-if="attachmentCategoriesLoading" class="project-attachments__sidebar-loading"><el-skeleton :rows="4" animated /></div>
                <nav v-else class="project-attachments__category-nav">
                  <button type="button" class="project-attachments__category-nav-item" :class="{ 'is-active': attachmentCategoryFilterId === null }" :aria-current="attachmentCategoryFilterId === null ? 'page' : undefined" @click="selectAttachmentCategory(null)"><el-icon><Document /></el-icon><span>全部附件</span></button>
                  <button type="button" class="project-attachments__category-nav-item" :class="{ 'is-active': attachmentCategoryFilterId === 0 }" :aria-current="attachmentCategoryFilterId === 0 ? 'page' : undefined" @click="selectAttachmentCategory(0)"><el-icon><Folder /></el-icon><span>未分类</span></button>
                  <button v-for="category in attachmentCategories" :key="category.id" type="button" class="project-attachments__category-nav-item" :class="{ 'is-active': attachmentCategoryFilterId === category.id }" :aria-current="attachmentCategoryFilterId === category.id ? 'page' : undefined" @click="selectAttachmentCategory(category.id)"><el-icon><Folder /></el-icon><span :title="category.name">{{ category.name }}</span></button>
                </nav>
                <div class="project-attachments__sidebar-footer"><span>当前分类</span><strong>{{ activeAttachmentCategoryName }}</strong></div>
              </aside>
              <section class="project-attachments__browser" aria-label="附件列表">
                <div class="project-attachments__browser-heading"><div><span class="panel-kicker">ATTACHMENT LIBRARY</span><h3>附件列表</h3><p>正在查看 <strong>{{ activeAttachmentCategoryName }}</strong> · 共 {{ attachmentTotal }} 个附件</p></div><el-tag effect="plain" type="info">按上传时间倒序</el-tag></div>
            <div class="project-attachments__toolbar">
              <div class="project-attachments__filter-group">
                <el-select v-model="attachmentCategoryFilterId" class="project-attachments__category-select" placeholder="全部分类" clearable @change="searchAttachments">
                  <el-option label="全部分类" :value="null" />
                  <el-option label="未分类" :value="0" />
                  <el-option v-for="category in attachmentCategories" :key="category.id" :label="category.name" :value="category.id" />
                </el-select>
                <el-input v-model="attachmentKeyword" class="project-attachments__search" clearable placeholder="搜索文件名" @keyup.enter="searchAttachments">
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
                <el-button type="primary" @click="searchAttachments">查询</el-button>
                <el-button @click="resetAttachmentSearch">重置</el-button>
              </div>
              <el-upload v-if="canUpdateProject" :auto-upload="false" :show-file-list="false" :disabled="attachmentUploading || attachmentCategoriesLoading" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.png,.jpg,.jpeg,.gif,.zip,.rar" :on-change="handleAttachmentChange"><el-button type="primary" :loading="attachmentUploading"><el-icon><Upload /></el-icon>上传附件</el-button></el-upload>
              <span v-else class="muted">附件分类可由项目更新权限成员维护</span>
              <span v-if="attachmentCategoriesError" class="project-attachments__category-error">{{ attachmentCategoriesError }} <el-button link type="primary" @click="loadProjectAttachmentCategories">重试</el-button></span>
            </div>
            <div v-loading="attachmentsLoading" class="project-attachments__content">
              <el-empty v-if="!attachmentsLoading && !attachments.length" description="暂无项目附件" :image-size="72" />
              <div v-else class="project-attachment-list">
                <div class="project-attachment-list__header" aria-hidden="true"><span>文件信息</span><span>所属分类</span><span>操作</span></div>
                <article v-for="attachment in attachments" :key="attachment.id" class="project-attachment-item">
                  <div class="project-attachment-item__leading"><div class="project-attachment-item__icon"><el-icon><Document /></el-icon></div><div class="project-attachment-item__main"><strong :title="attachment.fileName">{{ attachment.fileName }}</strong><span>{{ formatAttachmentSize(attachment.size) }} · {{ attachment.uploaderName || '未知用户' }} · {{ formatDateOnly(attachment.createdAt) || '-' }}</span></div></div>
                  <el-tag size="small" effect="plain" class="project-attachment-item__category"><el-icon><Folder /></el-icon>{{ attachment.categoryName || '未分类' }}</el-tag>
                  <div class="project-attachment-item__actions">
                    <el-tooltip content="在线预览" placement="top"><el-button circle plain type="primary" aria-label="在线预览附件" @click="previewAttachment(attachment)"><el-icon><View /></el-icon></el-button></el-tooltip>
                    <el-tooltip content="下载附件" placement="top"><el-button circle plain type="primary" aria-label="下载附件" @click="downloadAttachment(attachment)"><el-icon><Download /></el-icon></el-button></el-tooltip>
                    <el-tooltip v-if="canUpdateProject" content="删除附件" placement="top"><el-button circle plain type="danger" :loading="attachmentDeletingId === attachment.id" aria-label="删除附件" @click="removeAttachment(attachment)"><el-icon><Delete /></el-icon></el-button></el-tooltip>
                  </div>
                </article>
              </div>
            </div>
            <div v-if="!attachmentsLoading && attachmentTotal > 0" class="project-attachments__footer">
              <span class="muted">共 {{ attachmentTotal }} 个附件，按上传时间倒序</span>
              <UiPagination :page="attachmentPage" :page-size="attachmentPageSize" :total="attachmentTotal" :page-sizes="[10, 20, 50]" @update:page="changeAttachmentPage" @update:page-size="changeAttachmentPageSize" />
            </div>
              </section>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
      <div v-if="detailLoading || tabContentLoading" class="project-loading-overlay project-loading-overlay--tab-content" role="status" aria-live="polite">
        <div class="project-loading-panel">
          <span class="project-loading-spinner" aria-hidden="true" />
          <span>正在加载项目详情</span>
        </div>
      </div>
      </div>
    </template>

    <div v-else-if="isProjectDetailRoute" class="project-tabs-shell project-detail-loading-shell">
      <div class="project-loading-overlay" role="status" aria-live="polite">
        <div class="project-loading-panel">
          <span class="project-loading-spinner" aria-hidden="true" />
          <span>正在加载项目详情</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="stageDialog" :title="stageEditingId ? '编辑项目阶段' : '新增项目阶段'" width="440px" destroy-on-close><el-form label-position="top"><el-form-item label="阶段名称" required><el-input v-model="stageForm.stage_name" maxlength="128" show-word-limit /></el-form-item><el-form-item label="排序号"><el-input-number v-model="stageForm.sort_no" :min="0" :max="9999" controls-position="right" /></el-form-item></el-form><template #footer><el-button @click="stageDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveStage">保存</el-button></template></el-dialog>
    <el-dialog v-model="projectDialog" :title="projectEditingId ? '编辑项目' : '新建项目'" width="600px" destroy-on-close><el-form label-position="top"><el-row :gutter="16"><el-col :span="12"><el-form-item label="项目编号" required><el-input v-model="projectForm.project_code" /></el-form-item></el-col><el-col :span="12"><el-form-item label="项目名称" required><el-input v-model="projectForm.project_name" /></el-form-item></el-col></el-row><el-form-item label="项目描述"><el-input v-model="projectForm.description" type="textarea" :rows="3" /></el-form-item><el-row :gutter="16"><el-col :span="12"><el-form-item label="项目状态"><el-select v-model="projectForm.status" style="width:100%"><el-option v-for="(label, value) in projectStatusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="负责人"><el-select v-model="projectForm.owner_id" filterable :loading="userOptionsLoading" style="width:100%" @visible-change="onUserOptionsVisible"><el-option v-for="item in userOptions" :key="item.id" :label="userOptionLabel(item)" :value="item.id" /></el-select></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="16"><el-form-item label="计划时间范围"><el-date-picker v-model="projectDateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" clearable unlink-panels style="width:100%" @change="onProjectDateRangeChange" /></el-form-item></el-col><el-col :span="8"><el-form-item label="实际结束"><el-date-picker v-model="projectForm.actual_end_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="projectDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveProject">保存</el-button></template></el-dialog>
    <el-dialog v-model="planChildrenDialog" :title="selectedPlanForChildren ? selectedPlanForChildren.plan_name + ' · 子计划' : '子计划'" width="860px" destroy-on-close class="project-plan-children-dialog">
      <template v-if="selectedPlanForChildren">
        <div class="project-plan-children-dialog__summary">
          <div><span class="panel-kicker">主计划</span><strong>{{ selectedPlanForChildren.plan_name }}</strong><small>{{ selectedPlanForChildren.plan_code || '未生成编号' }} · {{ selectedPlanForChildren.phase_name || selectedPlanForChildren.phase || '未设置阶段' }}</small></div>
          <div class="project-plan-children-dialog__summary-facts"><span>计划周期<strong>{{ formatDateOnly(selectedPlanForChildren.planned_start_date) || '-' }} 至 {{ formatDateOnly(selectedPlanForChildren.planned_end_date) || '-' }}</strong></span><span>完成进度<strong>{{ Math.round(Number(selectedPlanForChildren.progress || 0)) }}%</strong></span><UiStatusTag :value="selectedPlanForChildren.status" :labels="planStatusLabels" :tone="planStatusTone(selectedPlanForChildren.status)" /></div>
        </div>
        <div class="project-section-heading project-plan-children-dialog__heading"><div><span class="panel-kicker">SUB PLANS</span><h3>子计划列表</h3><p class="muted">子计划继承主计划的项目阶段，按计划顺序展示。</p></div><el-button v-if="canCreatePlan" type="primary" @click="openCreateChildPlan(selectedPlanForChildren)"><el-icon><Plus /></el-icon>新增子计划</el-button></div>
        <UiDataTable :data="selectedPlanChildren" row-key="id" border empty-text="暂无子计划" class="project-plan-children-table">
          <el-table-column prop="plan_code" label="计划编号" min-width="190"><template #default="scope">{{ scope.row.plan_code || '未生成' }}</template></el-table-column>
          <el-table-column prop="plan_name" label="子计划名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="owner_name" label="负责人" width="130"><template #default="scope">{{ scope.row.owner_name || '-' }}</template></el-table-column>
          <el-table-column label="时间范围" min-width="190"><template #default="scope">{{ formatDateOnly(scope.row.planned_start_date) || '-' }} 至 {{ formatDateOnly(scope.row.planned_end_date) || '-' }}</template></el-table-column>
          <el-table-column label="进度" width="150"><template #default="scope"><el-progress :percentage="Number(scope.row.progress || 0)" :stroke-width="7" /></template></el-table-column>
          <el-table-column label="状态" width="105"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="planStatusLabels" :tone="planStatusTone(scope.row.status)" /></template></el-table-column>
          <el-table-column v-if="canUpdatePlan || canDeletePlan" label="操作" width="145" fixed="right"><template #default="scope"><el-button v-if="canUpdatePlan" link type="primary" @click="openEditPlan(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button v-if="canDeletePlan" link type="danger" @click="removePlan(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></template></el-table-column>
        </UiDataTable>
      </template>
      <el-empty v-else description="暂无主计划信息" :image-size="64" />
      <template #footer><el-button @click="closePlanChildren">关闭</el-button><el-button v-if="selectedPlanForChildren && canUpdatePlan" type="primary" @click="openEditPlan(selectedPlanForChildren)"><el-icon><Edit /></el-icon>编辑主计划</el-button><el-button v-if="selectedPlanForChildren && canDeletePlan" type="danger" plain @click="removePlan(selectedPlanForChildren)"><el-icon><Delete /></el-icon>删除主计划</el-button></template>
    </el-dialog>
    <el-dialog v-model="planDialog" :title="planEditingId ? '编辑项目计划' : (planParentName ? '新增子计划' : '新增主计划')" width="600px" destroy-on-close><el-form label-position="top"><el-form-item label="计划名称" required><el-input v-model="planForm.plan_name" /></el-form-item><el-form-item label="计划层级"><el-input :model-value="planParentName ? `子计划（${planParentName}）` : '主计划'" disabled /></el-form-item><el-form-item label="计划描述"><el-input v-model="planForm.description" type="textarea" :rows="2" /></el-form-item><el-row :gutter="16"><el-col :span="12"><el-form-item label="负责人"><el-select v-model="planForm.owner_id" clearable filterable :loading="userOptionsLoading" style="width:100%" @visible-change="onUserOptionsVisible"><el-option v-for="item in userOptions" :key="item.id" :label="userOptionLabel(item)" :value="item.id" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="状态"><el-select v-model="planForm.status" style="width:100%"><el-option v-for="(label, value) in planStatusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-col></el-row><el-form-item label="牵头方"><UiTreeSelect :model-value="planForm.lead_org_id === null ? null : Number(planForm.lead_org_id || 0)" :options="organizationTreeOptions" placeholder="请选择牵头组织" @update:model-value="planForm.lead_org_id = $event" /></el-form-item><el-form-item label="配合方"><el-tree-select v-model="planForm.cooperating_org_ids" :data="organizationTreeOptions" multiple show-checkbox check-strictly clearable filterable node-key="value" placeholder="请选择配合组织" style="width:100%" /></el-form-item><el-form-item label="计划时间范围" :required="!Number(planForm.parent_id || 0)"><el-date-picker v-model="planDateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" clearable unlink-panels style="width:100%" @change="onPlanDateRangeChange" /></el-form-item><el-form-item label="完成进度"><el-slider v-model="planForm.progress" :max="100" /></el-form-item></el-form><template #footer><el-button @click="planDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePlan">保存</el-button></template></el-dialog>
    <el-dialog v-model="memberDialog" :title="memberEditingId ? '编辑项目成员' : '添加项目成员'" width="520px" destroy-on-close><el-form label-position="top"><el-form-item label="成员" required><el-select v-model="memberForm.user_id" filterable :disabled="Boolean(memberEditingId)" :loading="userOptionsLoading" style="width:100%"><el-option v-for="item in userOptions" :key="item.id" :label="userOptionLabel(item)" :value="item.id" /></el-select></el-form-item><el-form-item label="所属项目机构"><UiTreeSelect :model-value="memberForm.org_id" :options="projectOrganizationTreeOptions" placeholder="请选择项目机构" @update:model-value="memberForm.org_id = $event" /></el-form-item><el-form-item label="项目角色"><el-select v-model="memberForm.role_ids" multiple collapse-tags filterable style="width:100%"><el-option v-for="role in roles" :key="role.id" :label="role.role_name" :value="role.id" /></el-select></el-form-item><el-form-item label="成员状态"><el-switch v-model="memberForm.status" :active-value="1" :inactive-value="0" active-text="有效" inactive-text="停用" /></el-form-item></el-form><template #footer><el-button @click="memberDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveMember">保存</el-button></template></el-dialog>
    <el-dialog v-model="roleDialog" :title="roleEditingId ? '编辑项目角色' : '新增项目角色'" width="560px" destroy-on-close><el-form label-position="top"><el-form-item label="角色编码" required><el-input v-model="roleForm.role_code" /></el-form-item><el-form-item label="角色名称" required><el-input v-model="roleForm.role_name" /></el-form-item><el-form-item label="关联人员"><el-select v-model="roleForm.member_ids" multiple collapse-tags filterable clearable style="width:100%"><el-option v-for="member in members" :key="member.id" :label="`${member.display_name}${member.org_name ? `（${member.org_name}）` : ''}`" :value="member.id" /></el-select></el-form-item><el-form-item label="角色说明"><el-input v-model="roleForm.description" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="roleDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRole">保存</el-button></template></el-dialog>
    <el-dialog v-model="projectOrganizationDialog" :title="projectOrganizationEditingId ? '编辑项目组织' : '新增项目组织'" width="520px" destroy-on-close><el-form label-position="top"><el-form-item label="上级项目组织"><UiTreeSelect :model-value="projectOrganizationForm.parent_id ? Number(projectOrganizationForm.parent_id) : null" :options="projectOrganizationParentOptions" placeholder="请选择上级项目组织" @update:model-value="projectOrganizationForm.parent_id = $event || 0" /></el-form-item><el-form-item label="组织编码" required><el-input v-model="projectOrganizationForm.org_code" maxlength="64" /></el-form-item><el-form-item label="组织名称" required><el-input v-model="projectOrganizationForm.org_name" maxlength="128" /></el-form-item><el-form-item label="排序号"><el-input-number v-model="projectOrganizationForm.sort_no" :min="0" :max="9999" controls-position="right" /></el-form-item><el-form-item label="状态"><el-switch v-model="projectOrganizationForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item></el-form><template #footer><el-button @click="projectOrganizationDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveProjectOrganization">保存</el-button></template></el-dialog>
    <el-dialog v-model="riskDialog" :title="riskEditingId ? '编辑项目风险' : '新增项目风险'" width="920px" top="5vh" destroy-on-close class="project-risk-dialog"><el-form label-position="top" class="project-risk-form"><section class="project-risk-form-section"><div class="project-risk-form-section__heading"><span>一、问题上报</span><small>记录问题来源、影响范围和初始处理信息</small></div><el-row :gutter="16"><el-col :span="6"><el-form-item label="编号"><el-input :model-value="riskEditingId ? String(riskForm.risk_code || '') : '保存后自动生成'" disabled /></el-form-item></el-col><el-col :span="6"><el-form-item label="发生时间"><el-date-picker v-model="riskForm.occurred_date" type="date" value-format="YYYY-MM-DD" placeholder="请选择日期" style="width:100%" /></el-form-item></el-col><el-col :span="6"><el-form-item label="项目阶段"><el-select v-model="riskForm.project_phase" clearable style="width:100%"><el-option v-for="item in projectOptions.project_phases" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="紧急程度"><el-select v-model="riskForm.urgency" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_urgencies" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="6"><el-form-item label="上报问题级别"><el-select v-model="riskForm.report_level" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_report_levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="当前状态" required><el-select v-model="riskForm.current_status" style="width:100%"><el-option v-for="item in projectOptions.risk_statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="提出组织/组"><UiTreeSelect :model-value="riskForm.proposer_org_id ? Number(riskForm.proposer_org_id) : null" :options="organizationTreeOptions" placeholder="请选择提出组织" clearable @update:model-value="riskForm.proposer_org_id = $event" /></el-form-item></el-col><el-col :span="6"><el-form-item label="提出物理子系统"><el-input v-model="riskForm.proposer_subsystem" maxlength="128" /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="12"><el-form-item label="联系人"><el-input v-model="riskForm.proposer_contact_name" maxlength="128" /></el-form-item></el-col><el-col :span="12"><el-form-item label="联系方式"><el-input v-model="riskForm.proposer_contact_phone" maxlength="64" /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="6"><el-form-item label="涉及组织/组"><UiTreeSelect :model-value="riskForm.involved_org_id ? Number(riskForm.involved_org_id) : null" :options="organizationTreeOptions" placeholder="请选择涉及组织" clearable @update:model-value="riskForm.involved_org_id = $event" /></el-form-item></el-col><el-col :span="6"><el-form-item label="涉及物理子系统"><el-input v-model="riskForm.involved_subsystem" maxlength="128" /></el-form-item></el-col><el-col :span="12"><el-form-item label="问题描述"><el-input v-model="riskForm.problem_description" type="textarea" :rows="2" maxlength="2000" show-word-limit /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="6"><el-form-item label="期望解决时间"><el-date-picker v-model="riskForm.expected_resolution_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="9"><el-form-item label="建议解决方案"><el-input v-model="riskForm.suggested_solution" type="textarea" :rows="2" /></el-form-item></el-col><el-col :span="5"><el-form-item label="当前处理人"><el-input v-model="riskForm.current_handler_name" maxlength="128" /></el-form-item></el-col><el-col :span="4"><el-form-item label="联系方式"><el-input v-model="riskForm.current_handler_phone" maxlength="64" /></el-form-item></el-col></el-row></section><section class="project-risk-form-section"><div class="project-risk-form-section__heading"><span>二、进展描述</span><small>持续更新风险处理过程和关键进展</small></div><el-form-item label="进展描述"><el-input v-model="riskForm.progress_description" type="textarea" :rows="5" maxlength="4000" show-word-limit /></el-form-item></section><section class="project-risk-form-section"><div class="project-risk-form-section__heading"><span>三、升级与解决</span><small>记录风险关注、升级和最终解决结果</small></div><el-row :gutter="16"><el-col :span="6"><el-form-item label="关注等级"><el-select v-model="riskForm.attention_level" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_attention_levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="问题性质"><el-input v-model="riskForm.problem_nature" maxlength="128" /></el-form-item></el-col><el-col :span="6"><el-form-item label="问题领域"><el-input v-model="riskForm.problem_domain" maxlength="128" /></el-form-item></el-col><el-col :span="6"><el-form-item label="PMO联系人"><el-input v-model="riskForm.pmo_contact" maxlength="256" /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="6"><el-form-item label="是否升级"><el-select v-model="riskForm.escalation_level" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_escalation_levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="当前问题级别"><el-select v-model="riskForm.current_problem_level" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_problem_levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="计划解决时间"><el-date-picker v-model="riskForm.planned_resolution_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="6"><el-form-item label="实际解决时间"><el-date-picker v-model="riskForm.actual_resolution_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row><el-form-item label="问题解决方案"><el-input v-model="riskForm.resolution_solution" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item></section></el-form><template #footer><el-button @click="riskDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRisk">保存风险</el-button></template></el-dialog>
    <el-drawer v-model="riskDialog" direction="rtl" size="min(820px, 100vw)" destroy-on-close class="project-risk-drawer">
      <template #header>
        <div class="project-risk-drawer__header-copy">
          <span class="project-risk-drawer__eyebrow">项目风险</span>
          <div class="project-risk-drawer__title-row">
            <h2>{{ riskMode === 'progress' ? '风险进度描述' : riskMode === 'tracking' ? '问题跟踪' : (riskEditingId ? '编辑项目风险' : '新增项目风险') }}</h2>
            <UiStatusTag v-if="riskEditingId && riskForm.current_status" :value="String(riskForm.current_status)" :labels="{ [String(riskForm.current_status)]: String(riskForm.current_status_name || riskForm.current_status) }" :tone="riskStatusTone(String(riskForm.current_status), String(riskForm.current_status_name || ''))" />
          </div>
          <p>{{ riskEditingId ? (String(riskForm.problem_description || '').trim() || '暂无问题描述') : '填写问题上报信息，保存后将自动生成风险编号' }}</p>
        </div>
      </template>
      <nav v-if="riskEntryMode === 'tracking'" class="project-risk-drawer__mode" role="tablist" aria-label="风险维护模式">
        <button type="button" role="tab" :aria-selected="riskMode === 'progress'" :class="{ 'is-active': riskMode === 'progress' }" @click="switchRiskMode('progress')"><el-icon><ChatDotRound /></el-icon><span>进度描述</span><small>评论与进展</small></button>
        <button type="button" role="tab" :aria-selected="riskMode === 'tracking'" :class="{ 'is-active': riskMode === 'tracking' }" @click="switchRiskMode('tracking')"><el-icon><Folder /></el-icon><span>问题跟踪</span><small>升级与解决</small></button>
      </nav>

      <el-form v-if="riskMode === 'report'" label-position="top" class="project-risk-form project-risk-drawer__body">
        <section class="project-risk-form-section"><div class="project-risk-form-section__heading"><span>一、问题上报</span><small>记录问题来源、影响范围和初始处理信息</small></div><el-row :gutter="16"><el-col :span="6"><el-form-item label="编号"><el-input :model-value="riskEditingId ? String(riskForm.risk_code || '') : '保存后自动生成'" disabled /></el-form-item></el-col><el-col :span="6"><el-form-item label="发生时间"><el-date-picker v-model="riskForm.occurred_date" type="date" value-format="YYYY-MM-DD" placeholder="请选择日期" style="width:100%" /></el-form-item></el-col><el-col :span="6"><el-form-item label="项目阶段"><el-select v-model="riskForm.project_phase" clearable style="width:100%"><el-option v-for="item in projectOptions.project_phases" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="紧急程度"><el-select v-model="riskForm.urgency" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_urgencies" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="6"><el-form-item label="上报问题级别"><el-select v-model="riskForm.report_level" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_report_levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="当前状态" required><el-select v-model="riskForm.current_status" style="width:100%"><el-option v-for="item in projectOptions.risk_statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="提出组织/组"><UiTreeSelect :model-value="riskForm.proposer_org_id ? Number(riskForm.proposer_org_id) : null" :options="organizationTreeOptions" placeholder="请选择提出组织" clearable @update:model-value="riskForm.proposer_org_id = $event" /></el-form-item></el-col><el-col :span="6"><el-form-item label="提出物理子系统"><el-input v-model="riskForm.proposer_subsystem" maxlength="128" /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="12"><el-form-item label="联系人"><el-input v-model="riskForm.proposer_contact_name" maxlength="128" /></el-form-item></el-col><el-col :span="12"><el-form-item label="联系方式"><el-input v-model="riskForm.proposer_contact_phone" maxlength="64" /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="6"><el-form-item label="涉及组织/组"><UiTreeSelect :model-value="riskForm.involved_org_id ? Number(riskForm.involved_org_id) : null" :options="organizationTreeOptions" placeholder="请选择涉及组织" clearable @update:model-value="riskForm.involved_org_id = $event" /></el-form-item></el-col><el-col :span="6"><el-form-item label="涉及物理子系统"><el-input v-model="riskForm.involved_subsystem" maxlength="128" /></el-form-item></el-col><el-col :span="12"><el-form-item label="问题描述"><el-input v-model="riskForm.problem_description" type="textarea" :rows="2" maxlength="2000" show-word-limit /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="6"><el-form-item label="期望解决时间"><el-date-picker v-model="riskForm.expected_resolution_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="9"><el-form-item label="建议解决方案"><el-input v-model="riskForm.suggested_solution" type="textarea" :rows="2" /></el-form-item></el-col><el-col :span="5"><el-form-item label="当前处理人"><el-input v-model="riskForm.current_handler_name" maxlength="128" /></el-form-item></el-col><el-col :span="4"><el-form-item label="联系方式"><el-input v-model="riskForm.current_handler_phone" maxlength="64" /></el-form-item></el-col></el-row></section>
      </el-form>

      <div v-else-if="riskMode === 'progress'" class="project-risk-drawer__body project-risk-progress">
        <section class="project-risk-form-section project-risk-readonly"><div class="project-risk-form-section__heading"><span>一、问题上报</span><small>进度描述模式下只读</small></div><el-descriptions :column="2" border size="small"><el-descriptions-item label="编号">{{ riskForm.risk_code || '保存后自动生成' }}</el-descriptions-item><el-descriptions-item label="发生时间">{{ formatDateOnly(riskForm.occurred_date) || '-' }}</el-descriptions-item><el-descriptions-item label="项目阶段">{{ riskForm.project_phase_name || riskForm.project_phase || '-' }}</el-descriptions-item><el-descriptions-item label="紧急程度">{{ riskForm.urgency_name || riskForm.urgency || '-' }}</el-descriptions-item><el-descriptions-item label="上报问题级别">{{ riskForm.report_level_name || riskForm.report_level || '-' }}</el-descriptions-item><el-descriptions-item label="当前状态">{{ riskForm.current_status_name || riskForm.current_status || '-' }}</el-descriptions-item><el-descriptions-item label="提出组织/组">{{ riskForm.proposer_org_name || '-' }}</el-descriptions-item><el-descriptions-item label="提出物理子系统">{{ riskForm.proposer_subsystem || '-' }}</el-descriptions-item><el-descriptions-item label="联系人">{{ riskForm.proposer_contact_name || '-' }}{{ riskForm.proposer_contact_phone ? ` / ${riskForm.proposer_contact_phone}` : '' }}</el-descriptions-item><el-descriptions-item label="涉及组织/组">{{ riskForm.involved_org_name || '-' }}</el-descriptions-item><el-descriptions-item label="涉及物理子系统">{{ riskForm.involved_subsystem || '-' }}</el-descriptions-item><el-descriptions-item label="期望解决时间">{{ formatDateOnly(riskForm.expected_resolution_date) || '-' }}</el-descriptions-item><el-descriptions-item label="问题描述" :span="2">{{ riskForm.problem_description || '-' }}</el-descriptions-item><el-descriptions-item label="建议解决方案" :span="2">{{ riskForm.suggested_solution || '-' }}</el-descriptions-item><el-descriptions-item label="当前处理人" :span="2">{{ riskForm.current_handler_name || '-' }}{{ riskForm.current_handler_phone ? ` / ${riskForm.current_handler_phone}` : '' }}</el-descriptions-item></el-descriptions></section>
        <section class="project-risk-form-section"><div class="project-risk-form-section__heading"><span>二、进展描述</span><small>所有项目成员均可发表</small></div><el-form class="project-risk-comment-form" @submit.prevent="submitRiskComment"><el-form-item label="发表评论"><el-input v-model="riskCommentText" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="请输入本次进展、处理结果或风险变化" /></el-form-item><el-button type="primary" :loading="riskCommentSubmitting" @click="submitRiskComment">发表评论</el-button></el-form><div v-if="riskCommentsLoading" class="project-risk-comments__loading"><el-skeleton :rows="4" animated /></div><el-empty v-else-if="!riskComments.length" description="暂无进展评论" :image-size="64" /><div v-else class="project-risk-comments"><article v-for="comment in riskComments" :key="comment.id" class="project-risk-comment"><div class="project-risk-comment__content"><div class="project-risk-comment__meta"><UiUserIdentity :user="commentUser(comment)" :size="34" /><time>{{ String(comment.created_at || '').replace('T', ' ').slice(0, 19) || '-' }}</time></div><p>{{ comment.comment_text }}</p></div></article></div></section>
      </div>

      <el-form v-else label-position="top" class="project-risk-form project-risk-drawer__body"><section class="project-risk-form-section"><div class="project-risk-form-section__heading"><span>三、升级与解决</span><small>记录风险关注、升级和最终解决结果</small></div><el-row :gutter="16"><el-col :span="6"><el-form-item label="关注等级"><el-select v-model="riskForm.attention_level" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_attention_levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="问题性质"><el-input v-model="riskForm.problem_nature" maxlength="128" /></el-form-item></el-col><el-col :span="6"><el-form-item label="问题领域"><el-input v-model="riskForm.problem_domain" maxlength="128" /></el-form-item></el-col><el-col :span="6"><el-form-item label="PMO联系人"><el-input v-model="riskForm.pmo_contact" maxlength="256" /></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="6"><el-form-item label="是否升级"><el-select v-model="riskForm.escalation_level" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_escalation_levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="当前问题级别"><el-select v-model="riskForm.current_problem_level" clearable style="width:100%"><el-option v-for="item in projectOptions.risk_problem_levels" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="6"><el-form-item label="计划解决时间"><el-date-picker v-model="riskForm.planned_resolution_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="6"><el-form-item label="实际解决时间"><el-date-picker v-model="riskForm.actual_resolution_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row><el-form-item label="问题解决方案"><el-input v-model="riskForm.resolution_solution" type="textarea" :rows="5" maxlength="4000" show-word-limit /></el-form-item></section></el-form>
      <template #footer><el-button @click="riskDialog = false">{{ riskMode === 'progress' ? '关闭' : '取消' }}</el-button><el-button v-if="riskMode !== 'progress'" type="primary" :loading="saving" @click="saveRisk">{{ riskMode === 'tracking' ? '保存跟踪' : '保存风险' }}</el-button></template>
    </el-drawer>
    <el-dialog v-model="attachmentCategoryDialog" title="新建附件分类" width="420px" destroy-on-close>
      <el-form label-position="top" @submit.prevent="saveAttachmentCategory">
        <el-form-item label="分类名称" required>
          <el-input v-model="attachmentCategoryName" maxlength="128" show-word-limit placeholder="例如：需求文档、会议纪要" @keyup.enter="saveAttachmentCategory" />
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="attachmentCategoryDialog = false">取消</el-button><el-button type="primary" :loading="attachmentCategorySaving" @click="saveAttachmentCategory">保存分类</el-button></template>
    </el-dialog>
    <UiFilePreview v-model="attachmentPreviewVisible" :url="attachmentPreviewUrl" :file-name="attachmentPreviewName" />
  </section>
</template>
