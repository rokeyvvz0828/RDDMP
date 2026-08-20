<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Briefcase, Calendar, ChatDotRound, Delete, Document, Download, Edit, Folder, Plus, Search, Upload, User, View } from '@element-plus/icons-vue'
import type { UploadFile } from 'element-plus'
import * as echarts from 'echarts'
import type { CustomSeriesRenderItemAPI, CustomSeriesRenderItemParams } from 'echarts'
import { apiErrorMessage } from '../api/error'
import { deleteProjectAttachment, getProjectAttachmentDownload, getProjectAttachmentPreview, getProjectAttachments, uploadProjectAttachment } from '../api/attachments'
import { createProject, createProjectMember, createProjectOrganization, createProjectPlan, createProjectPlanGroup, createProjectRisk, createProjectRiskComment, createProjectRole, deleteProject, deleteProjectMember, deleteProjectOrganization, deleteProjectPlan, deleteProjectPlanGroup, deleteProjectRisk, deleteProjectRole, getProject, getProjectOptions, getProjectRiskComments, getProjectUserOptions, getProjectWorkbench, moveProjectPlanToGroup, updateProject, updateProjectMember, updateProjectOrganization, updateProjectPlan, updateProjectRisk, updateProjectRole, updateProjectSettings } from '../api/project'
import type { Project, ProjectMember, ProjectOptions, ProjectOrganization, ProjectPlan, ProjectRisk, ProjectRiskComment, ProjectRole, ProjectStatus, PlanStatus, ProjectUserOption, ProjectOrganizationOption, ProjectPlanGroupColorToken } from '../types/project'
import type { ProjectAttachment } from '../types/attachments'
import { formatDateOnly } from '../utils/date'
import { useAuthStore } from '../stores/auth'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiUserIdentity from '../components/ui/UiUserIdentity.vue'
import UiTreeSelect, { type UiTreeOption } from '../components/ui/UiTreeSelect.vue'
import UiFilePreview from '../components/ui/UiFilePreview.vue'
import UiPagination from '../components/ui/UiPagination.vue'
const planGroupColorOptions: Array<{ key: ProjectPlanGroupColorToken; colorVar: string; accentVar: string }> = [
  { key: 'brand', colorVar: '--brand', accentVar: '--brand-strong' },
  { key: 'accent', colorVar: '--accent', accentVar: '--brand-strong' },
  { key: 'success', colorVar: '--success', accentVar: '--brand-strong' },
  { key: 'warning', colorVar: '--warning', accentVar: '--brand-strong' },
  { key: 'danger', colorVar: '--danger', accentVar: '--brand-strong' },
  { key: 'muted', colorVar: '--muted', accentVar: '--line' }
]

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
const planDialog = ref(false)
const planEditingId = ref<number | null>(null)
const planParentName = ref('')
const planForm = reactive<Record<string, unknown>>({})
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
const planGroupDialog = ref(false)
const planGroupForm = reactive<Record<string, unknown>>({ phase: '', color_key: 'brand' as ProjectPlanGroupColorToken, description: '', sort_no: 0 })
const draggingPlanId = ref<number | null>(null)
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
const attachmentPage = ref(1)
const attachmentPageSize = ref(10)
const attachmentTotal = ref(0)
const attachmentUploading = ref(false)
const attachmentDeletingId = ref<number | null>(null)
const attachmentPreviewVisible = ref(false)
const attachmentPreviewUrl = ref<string | null>(null)
const attachmentPreviewName = ref('文件预览')
let attachmentRequestSequence = 0
const settingsForm = reactive({ plan_number_rule: '', child_plan_number_rule: '', risk_number_rule: '' })
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
  projectOptions.value.plan_phases.forEach((stage, index) => stageMap.set(stage.value, { key: stage.value, name: stage.label, index }))
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
      stageChildren.push({ id: groupRowId(group.id, stage.index), node_type: 'group', stage_key: stage.key, stage_name: stage.name, stage_index: stage.index, group_id: group.id, group_name: stagePlanCode, group_color_key: group.color_key || 'brand', group_count: children.length, plan_name: stagePlanCode, plan_code: '阶段计划', parent_id: 0, progress: 0, status: 'NOT_STARTED', sort_no: group.sort_no, project_id: selectedProject.value?.id || 0, children } as ProjectPlanRow)
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
const planStats = computed(() => {
  const values = plans.value
  const count = (status: PlanStatus) => values.filter(plan => plan.status === status).length
  const progressTotal = values.reduce((total, plan) => total + Math.max(0, Math.min(100, Number(plan.progress || 0))), 0)
  return {
    total: values.length,
    completed: count('COMPLETED'),
    inProgress: count('IN_PROGRESS'),
    notStarted: count('NOT_STARTED'),
    blocked: count('BLOCKED'),
    averageProgress: values.length ? Math.round(progressTotal / values.length) : 0,
    statusData: [
      { value: count('COMPLETED'), name: '已完成', color: '--success' },
      { value: count('IN_PROGRESS'), name: '进行中', color: '--brand' },
      { value: count('NOT_STARTED'), name: '未开始', color: '--muted' },
      { value: count('BLOCKED'), name: '已阻塞', color: '--danger' }
    ],
    progressData: [...values].sort((left, right) => Number(right.progress || 0) - Number(left.progress || 0)).slice(0, 8).map(plan => ({ name: plan.plan_name, value: Math.round(Number(plan.progress || 0)) }))
  }
})
const mainGanttRows = computed<GanttRow[]>(() => {
  const rows: GanttRow[] = []
  plans.value.filter(plan => !Number(plan.parent_id || 0)).forEach(plan => {
    const range = planRange(plan)
    if (range) rows.push({ plan, kind: 'master', label: plan.plan_name, range })
  })
  return rows
})
type PlanTimelineGroup = { key: string; id: number | null; name: string; colorKey: ProjectPlanGroupColorToken; masters: ProjectPlan[] }
type PlanTimelineStage = { key: string; name: string; index: number; masters: ProjectPlan[]; groups: PlanTimelineGroup[] }
const planTimelineStages = computed<PlanTimelineStage[]>(() => {
  const stageMap = new Map<string, { key: string; name: string; index: number }>()
  projectOptions.value.plan_phases.forEach((stage, index) => stageMap.set(stage.value, { key: stage.value, name: stage.label, index }))
  const masterPlans = plans.value.filter(plan => !Number(plan.parent_id || 0))
  const unknownStages = [...new Set([...masterPlans.map(plan => plan.phase), ...planGroups.value.map(group => group.phase)].filter((phase): phase is string => Boolean(phase)))].filter(phase => !stageMap.has(phase))
  unknownStages.forEach((phase, index) => stageMap.set(phase, { key: phase, name: phase, index: stageMap.size + index }))
  if (masterPlans.some(plan => !plan.phase)) stageMap.set('__UNASSIGNED__', { key: '__UNASSIGNED__', name: '未设置阶段', index: stageMap.size })
  return [...stageMap.values()].sort((left, right) => left.index - right.index).map(stage => ({
    ...stage,
    masters: masterPlans.filter(plan => (plan.phase || '__UNASSIGNED__') === stage.key),
    groups: timelineGroupsForStage(stage.key, masterPlans)
  }))
})
function sortTimelinePlans(items: ProjectPlan[]) {
  return [...items].sort((left, right) => {
    const leftRange = planRange(left)?.start ?? Number.MAX_SAFE_INTEGER
    const rightRange = planRange(right)?.start ?? Number.MAX_SAFE_INTEGER
    const leftEnd = planRange(left)?.end ?? Number.MAX_SAFE_INTEGER
    const rightEnd = planRange(right)?.end ?? Number.MAX_SAFE_INTEGER
    return leftRange - rightRange || leftEnd - rightEnd || Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id
  })
}
function timelineGroupsForStage(stageKey: string, masterPlans: ProjectPlan[]): PlanTimelineGroup[] {
  const stagePlans = masterPlans.filter(plan => (plan.phase || '__UNASSIGNED__') === stageKey)
  const configuredGroups = planGroups.value.filter(group => (group.phase || '__UNASSIGNED__') === stageKey).sort((left, right) => Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id)
  const groups: PlanTimelineGroup[] = configuredGroups.map(group => ({
    key: `group-${group.id}`,
    id: group.id,
    name: group.stage_plan_code || group.group_name,
    colorKey: group.color_key || 'brand',
    masters: sortTimelinePlans(stagePlans.filter(plan => Number(plan.group_id) === Number(group.id)))
  }))
  const ungrouped = sortTimelinePlans(stagePlans.filter(plan => !plan.group_id || !configuredGroups.some(group => Number(group.id) === Number(plan.group_id))))
  if (ungrouped.length || !groups.length) groups.push({ key: `ungrouped-${stageKey}`, id: null, name: '未分组', colorKey: 'muted', masters: ungrouped })
  return groups
}
const planTimelineAxis = computed(() => {
  const axis = ganttAxisRange(mainGanttRows.value, 'month')
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
function timelineLayoutStyle() {
  return { '--project-plan-timeline-track-min-width': `${Math.max(760, planTimelineMonths.value.length * 112)}px` }
}
const selectedPlanForChildren = computed(() => plans.value.find(plan => plan.id === selectedPlanForChildrenId.value) || null)
const selectedPlanChildren = computed(() => {
  const parentId = selectedPlanForChildren.value?.id
  if (!parentId) return []
  return plans.value.filter(plan => Number(plan.parent_id || 0) === parentId).sort((left, right) => Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id)
})
const selectedMasterPlanId = ref<number | null>(null)
const selectedMasterPlan = computed(() => mainGanttRows.value.find(row => row.plan.id === selectedMasterPlanId.value)?.plan || null)
const childGanttRows = computed<GanttRow[]>(() => {
  const rows: GanttRow[] = []
  const mainPlan = selectedMasterPlan.value
  if (!mainPlan) return rows
  const children = plans.value
    .filter(plan => Number(plan.parent_id || 0) === mainPlan.id && planRange(plan))
    .sort((left, right) => Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id)
  const mainRange = planRange(mainPlan) || mergePlanRanges(children)
  if (!children.length || !mainRange) return rows
  rows.push({ plan: mainPlan, kind: 'master', label: `主计划 · ${mainPlan.plan_name}`, range: mainRange })
  children.forEach(plan => {
    const range = planRange(plan)
    if (range) rows.push({ plan, kind: 'child', label: `　子计划 · ${plan.plan_name}`, range })
  })
  return rows
})
const statusChartRef = ref<HTMLElement | null>(null)
const progressChartRef = ref<HTMLElement | null>(null)
const mainGanttChartRef = ref<HTMLElement | null>(null)
const childGanttChartRef = ref<HTMLElement | null>(null)
let statusChart: echarts.ECharts | null = null
let progressChart: echarts.ECharts | null = null
let mainGanttChart: echarts.ECharts | null = null
let childGanttChart: echarts.ECharts | null = null
let chartThemeObserver: MutationObserver | null = null

function cssVar(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function chartTextColor() { return cssVar('--text') || '#15232d' }
function chartMutedColor() { return cssVar('--muted') || '#637480' }
function chartLineColor() { return cssVar('--line') || '#d7e0e5' }
function chartPanelColor() { return cssVar('--panel-bg') || '#ffffff' }
function chartBrandColor() { return cssVar('--brand') || '#147d92' }
function chartBrandSoftColor() { const color = chartBrandColor(); const hex = color.replace('#', ''); if (hex.length !== 6) return color; return `rgba(${parseInt(hex.slice(0, 2), 16)}, ${parseInt(hex.slice(2, 4), 16)}, ${parseInt(hex.slice(4, 6), 16)}, .12)` }

type GanttUnit = 'month' | 'week'
type GanttRange = { start: number; end: number }
type GanttRow = { plan: ProjectPlan; kind: 'master' | 'child'; label: string; range: GanttRange }
type GanttData = { planName: string; startLabel: string; endLabel: string; progress: number; kind: GanttRow['kind']; value: number[] }

const dayMillis = 86400000

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

function mergePlanRanges(values: ProjectPlan[]) {
  const ranges = values.map(planRange).filter((range): range is GanttRange => Boolean(range))
  if (!ranges.length) return null
  return { start: Math.min(...ranges.map(range => range.start)), end: Math.max(...ranges.map(range => range.end)) }
}

function startOfMonth(timestamp: number) {
  const date = new Date(timestamp)
  return new Date(date.getFullYear(), date.getMonth(), 1).getTime()
}

function startOfWeek(timestamp: number) {
  const date = new Date(timestamp)
  const mondayOffset = (date.getDay() + 6) % 7
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - mondayOffset).getTime()
}

function ganttAxisRange(rows: GanttRow[], unit: GanttUnit) {
  if (!rows.length) return null
  const range = { start: Math.min(...rows.map(row => row.range.start)), end: Math.max(...rows.map(row => row.range.end)) }
  const min = unit === 'month' ? startOfMonth(range.start) : startOfWeek(range.start)
  const endAnchor = unit === 'month' ? startOfMonth(Math.max(range.end - dayMillis, range.start)) : startOfWeek(Math.max(range.end - dayMillis, range.start))
  const max = unit === 'month' ? new Date(new Date(endAnchor).getFullYear(), new Date(endAnchor).getMonth() + 1, 1).getTime() : endAnchor + 7 * dayMillis
  const count = unit === 'month'
    ? (new Date(max).getFullYear() - new Date(min).getFullYear()) * 12 + new Date(max).getMonth() - new Date(min).getMonth()
    : Math.max(1, Math.ceil((max - min) / (7 * dayMillis)))
  return { min, max, splitNumber: Math.max(1, Math.min(12, count)) }
}

function monthAxisLabel(value: number) {
  const date = new Date(value)
  return `${date.getFullYear()}年${date.getMonth() + 1}月`
}

function weekAxisLabel(value: number) {
  const date = new Date(value)
  const yearStart = startOfWeek(new Date(date.getFullYear(), 0, 1).getTime())
  const currentWeek = Math.floor((startOfWeek(value) - yearStart) / (7 * dayMillis)) + 1
  return `第${currentWeek}周`
}

function planStatusColor(status: PlanStatus) {
  const colors: Record<PlanStatus, string> = { COMPLETED: '--success', IN_PROGRESS: '--brand', NOT_STARTED: '--muted', BLOCKED: '--danger' }
  return cssVar(colors[status]) || chartBrandColor()
}

function clipGanttRect(shape: { x: number; y: number; width: number; height: number }, area: { x: number; y: number; width: number; height: number }) {
  const x = Math.max(shape.x, area.x)
  const right = Math.min(shape.x + shape.width, area.x + area.width)
  if (right <= x) return null
  return { ...shape, x, width: right - x }
}

function renderGanttItem(params: CustomSeriesRenderItemParams, api: CustomSeriesRenderItemAPI) {
  if (!params.coordSys) return null
  const categoryIndex = Number(api.value(0))
  const start = api.coord([api.value(1), categoryIndex])
  const end = api.coord([api.value(2), categoryIndex])
  const rowHeight = Number((api.size?.([0, 1]) as number[] | undefined)?.[1] || 32)
  const height = Math.min(20, Math.max(12, rowHeight * .46))
  const shape = { x: Math.min(start[0], end[0]), y: start[1] - height / 2, width: Math.max(2, Math.abs(end[0] - start[0])), height }
  const coordSys = params.coordSys as unknown as { x: number; y: number; width: number; height: number }
  const clipped = clipGanttRect(shape, coordSys)
  if (!clipped) return null
  const color = api.visual('color') as string
  const progress = Math.max(0, Math.min(100, Number(api.value(3) || 0)))
  const isMaster = Number(api.value(4)) === 1
  const isSelected = Number(api.value(5)) === 1
  const cursor = isMaster ? 'pointer' : 'default'
  const border = isSelected ? { stroke: chartTextColor(), lineWidth: 2 } : { stroke: 'transparent', lineWidth: 0 }
  const children: Array<Record<string, unknown>> = [{ type: 'rect', shape: { ...clipped, r: 5 }, style: { fill: color, opacity: isMaster ? .24 : .16, cursor, ...border } }]
  if (progress > 0) {
    const progressShape = clipGanttRect({ ...clipped, width: clipped.width * progress / 100 }, coordSys)
    if (progressShape) children.push({ type: 'rect', shape: { ...progressShape, r: 5 }, style: { fill: color, opacity: isMaster ? .92 : .84, cursor, ...border } })
  }
  return { type: 'group' as const, children }
}

function ganttTooltip(params: unknown) {
  const item = (Array.isArray(params) ? params[0] : params) as { data?: GanttData } | undefined
  const data = item?.data
  if (!data) return ''
  const level = data.kind === 'master' ? '主计划' : '子计划'
  return `${level}：${data.planName}<br/>${data.startLabel} 至 ${data.endLabel}<br/>进度：${data.progress}%`
}

function createGanttOption(rows: GanttRow[], unit: GanttUnit, axis: { min: number; max: number; splitNumber: number }) {
  const text = chartTextColor()
  const muted = chartMutedColor()
  const line = chartLineColor()
  return {
    animationDuration: 360,
    grid: { left: 148, right: 20, top: 18, bottom: 34 },
    tooltip: { trigger: 'item', formatter: ganttTooltip },
    xAxis: {
      type: 'time', min: axis.min, max: axis.max, splitNumber: axis.splitNumber,
      minInterval: unit === 'week' ? 7 * dayMillis : undefined,
      axisLabel: { color: muted, fontSize: 10, margin: 12, hideOverlap: true, formatter: unit === 'month' ? monthAxisLabel : weekAxisLabel },
      axisLine: { lineStyle: { color: line } },
      splitLine: { show: true, lineStyle: { color: line, type: 'dashed' } }
    },
    yAxis: {
      type: 'category', inverse: true, data: rows.map(row => row.label),
      axisLabel: { color: text, fontSize: 11, width: 132, overflow: 'truncate' },
      axisLine: { show: false }, axisTick: { show: false }, splitLine: { show: true, lineStyle: { color: line } }
    },
    series: [{
      type: 'custom', renderItem: renderGanttItem, encode: { x: [1, 2], y: 0 },
      data: rows.map((row, index) => ({
        name: row.plan.plan_name,
        planName: row.plan.plan_name,
        startLabel: formatDateOnly(row.plan.planned_start_date) || formatDateOnly(new Date(row.range.start).toISOString()),
        endLabel: formatDateOnly(row.plan.planned_end_date) || formatDateOnly(new Date(row.range.end - dayMillis).toISOString()),
        progress: Math.round(Number(row.plan.progress || 0)),
        kind: row.kind,
        value: [index, row.range.start, row.range.end, Math.max(0, Math.min(100, Number(row.plan.progress || 0))), row.kind === 'master' ? 1 : 0, row.kind === 'master' && row.plan.id === selectedMasterPlanId.value ? 1 : 0],
        itemStyle: { color: planStatusColor(row.plan.status) }
      }))
    }]
  }
}

function handleMasterGanttClick(params: unknown) {
  const dataIndex = Number((params as { dataIndex?: unknown } | undefined)?.dataIndex)
  const row = Number.isInteger(dataIndex) ? mainGanttRows.value[dataIndex] : null
  if (row) selectedMasterPlanId.value = row.plan.id
}

function ganttCanvasWidth(rows: GanttRow[], unit: GanttUnit) {
  const axis = ganttAxisRange(rows, unit)
  const units = axis?.splitNumber || 1
  return `${Math.max(760, units * (unit === 'month' ? 128 : 72) + 170)}px`
}

function ganttCanvasHeight(rows: GanttRow[]) {
  return `${Math.max(190, rows.length * 42 + 64)}px`
}

function renderCharts() {
  const text = chartTextColor()
  const muted = chartMutedColor()
  const line = chartLineColor()
  const panel = chartPanelColor()
  const brand = chartBrandColor()
  const stats = planStats.value
  if (statusChartRef.value) {
    statusChart ||= echarts.init(statusChartRef.value)
    statusChart.setOption({
    animationDuration: 500,
    tooltip: { trigger: 'item', formatter: '{b}: {c} 项 ({d}%)' },
    legend: { bottom: 0, left: 'center', itemWidth: 9, itemHeight: 9, textStyle: { color: muted, fontSize: 11 } },
    series: [{ type: 'pie', radius: ['54%', '76%'], center: ['50%', '43%'], avoidLabelOverlap: true, itemStyle: { borderColor: panel, borderWidth: 3 }, label: { show: true, color: text, fontSize: 12, formatter: '{c}' }, data: stats.statusData.map(item => ({ value: item.value, name: item.name, itemStyle: { color: cssVar(item.color) } })) }]
    }, true)
  }
  if (progressChartRef.value) {
    progressChart ||= echarts.init(progressChartRef.value)
    progressChart.setOption({
    animationDuration: 500,
    grid: { left: 92, right: 18, top: 12, bottom: 22 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (items: Array<{ name: string; value: number }>) => `${items[0]?.name || ''}<br/>进度：${items[0]?.value || 0}%` },
    xAxis: { type: 'value', max: 100, axisLabel: { color: muted, fontSize: 10, formatter: '{value}%' }, splitLine: { lineStyle: { color: line } } },
    yAxis: { type: 'category', inverse: true, axisLabel: { color: text, fontSize: 11, width: 78, overflow: 'truncate' }, axisLine: { show: false }, axisTick: { show: false }, data: stats.progressData.map(item => item.name) },
    series: [{ type: 'bar', barMaxWidth: 14, showBackground: true, backgroundStyle: { color: chartBrandSoftColor() }, itemStyle: { color: brand, borderRadius: [0, 4, 4, 0] }, label: { show: true, position: 'right', color: text, fontSize: 10, formatter: '{c}%' }, data: stats.progressData.map(item => item.value) }]
    }, true)
  }
  const mainRows = mainGanttRows.value
  const mainAxis = ganttAxisRange(mainRows, 'month')
  if (mainGanttChartRef.value && mainAxis) {
    mainGanttChart ||= echarts.init(mainGanttChartRef.value)
    mainGanttChart.setOption(createGanttOption(mainRows, 'month', mainAxis), true)
    mainGanttChart.off('click')
    mainGanttChart.on('click', handleMasterGanttClick)
  } else if (mainGanttChart) {
    mainGanttChart.dispose()
    mainGanttChart = null
  }
  const childRows = childGanttRows.value
  const childAxis = ganttAxisRange(childRows, 'week')
  if (childGanttChartRef.value && childAxis) {
    childGanttChart ||= echarts.init(childGanttChartRef.value)
    childGanttChart.setOption(createGanttOption(childRows, 'week', childAxis), true)
  } else if (childGanttChart) {
    childGanttChart.dispose()
    childGanttChart = null
  }
}

function resizeCharts() { statusChart?.resize(); progressChart?.resize(); mainGanttChart?.resize(); childGanttChart?.resize() }
function disposeCharts() { statusChart?.dispose(); progressChart?.dispose(); mainGanttChart?.dispose(); childGanttChart?.dispose(); statusChart = null; progressChart = null; mainGanttChart = null; childGanttChart = null }
async function refreshOverviewCharts() { await nextTick(); if (activeTab.value === 'overview') renderCharts() }

function resetProjectForm() { Object.keys(projectForm).forEach(key => delete projectForm[key]); Object.assign(projectForm, { project_code: '', project_name: '', description: '', status: 'PLANNING', owner_id: auth.user?.id || null, planned_start_date: '', planned_end_date: '', actual_end_date: '' }) }
function resetPlanForm() { Object.keys(planForm).forEach(key => delete planForm[key]); Object.assign(planForm, { parent_id: 0, group_id: null, phase: '', plan_name: '', description: '', owner_id: null, lead_org_id: null, cooperating_org_ids: [], planned_start_date: '', planned_end_date: '', progress: 0, status: 'NOT_STARTED', sort_no: 0 }) }
function resetRoleForm() { Object.keys(roleForm).forEach(key => delete roleForm[key]); Object.assign(roleForm, { role_code: '', role_name: '', description: '', member_ids: [] }) }
function resetProjectOrganizationForm(parentId = 0) { Object.keys(projectOrganizationForm).forEach(key => delete projectOrganizationForm[key]); Object.assign(projectOrganizationForm, { parent_id: parentId, org_code: '', org_name: '', sort_no: 0, status: 1 }) }
function resetPlanGroupForm(phase = '') { Object.keys(planGroupForm).forEach(key => delete planGroupForm[key]); Object.assign(planGroupForm, { phase: phase || projectOptions.value.plan_phases[0]?.value || '', color_key: 'brand', description: '', sort_no: 0 }) }
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
async function refreshProject(projectId: number) { detailLoading.value = true; try { selectedProject.value = (await getProject(projectId)).data.data; ensureProjectUserOptions(selectedProject.value); resetSettingsForm(); if (activeTab.value === 'attachments') { resetAttachmentQuery(); await loadProjectAttachments() } } catch (error) { selectedProject.value = null; ElMessage.error(apiErrorMessage(error, '项目详情加载失败')); await router.replace({ name: 'projects', query: {} }) } finally { detailLoading.value = false } }
function resetAttachmentQuery() { attachmentKeyword.value = ''; attachmentPage.value = 1; attachmentPageSize.value = 10; attachmentTotal.value = 0; attachments.value = [] }
async function loadProjectAttachments() { if (!selectedProject.value) return; const requestSequence = ++attachmentRequestSequence; attachmentsLoading.value = true; try { const page = (await getProjectAttachments(selectedProject.value.id, { page: attachmentPage.value, size: attachmentPageSize.value, keyword: attachmentKeyword.value.trim() || undefined })).data.data; if (requestSequence !== attachmentRequestSequence) return; attachments.value = page.records; attachmentTotal.value = page.total } catch (error) { if (requestSequence !== attachmentRequestSequence) return; attachments.value = []; attachmentTotal.value = 0; ElMessage.error(apiErrorMessage(error, '项目附件加载失败')) } finally { if (requestSequence === attachmentRequestSequence) attachmentsLoading.value = false } }
function searchAttachments() { attachmentPage.value = 1; void loadProjectAttachments() }
function resetAttachmentSearch() { attachmentKeyword.value = ''; attachmentPage.value = 1; void loadProjectAttachments() }
function changeAttachmentPage(page: number) { attachmentPage.value = page; void loadProjectAttachments() }
function changeAttachmentPageSize(size: number) { attachmentPageSize.value = size; attachmentPage.value = 1; void loadProjectAttachments() }
function formatAttachmentSize(size: number) { if (size < 1024) return `${size} B`; if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`; if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`; return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB` }
async function handleAttachmentChange(file: UploadFile) { if (file.raw) await uploadAttachment(file.raw) }
async function uploadAttachment(file: File) { if (!selectedProject.value) return; attachmentUploading.value = true; try { await uploadProjectAttachment(selectedProject.value.id, file); attachmentPage.value = 1; await loadProjectAttachments(); ElMessage.success('附件上传成功') } catch (error) { ElMessage.error(apiErrorMessage(error, '附件上传失败')) } finally { attachmentUploading.value = false } }
async function previewAttachment(row: ProjectAttachment) { if (!selectedProject.value) return; try { const link = (await getProjectAttachmentPreview(selectedProject.value.id, row.id)).data.data; attachmentPreviewName.value = link.fileName || row.fileName; attachmentPreviewUrl.value = link.url; attachmentPreviewVisible.value = true } catch (error) { ElMessage.error(apiErrorMessage(error, '附件预览地址获取失败')) } }
async function downloadAttachment(row: ProjectAttachment) { if (!selectedProject.value) return; try { const link = (await getProjectAttachmentDownload(selectedProject.value.id, row.id)).data.data; const anchor = document.createElement('a'); anchor.href = link.url; anchor.download = link.fileName || row.fileName; anchor.rel = 'noopener'; anchor.target = '_blank'; anchor.click() } catch (error) { ElMessage.error(apiErrorMessage(error, '附件下载地址获取失败')) } }
async function removeAttachment(row: ProjectAttachment) { if (!selectedProject.value) return; try { await ElMessageBox.confirm(`确认删除附件“${row.fileName}”吗？删除后不可恢复。`, '删除附件', { type: 'warning' }); attachmentDeletingId.value = row.id; await deleteProjectAttachment(selectedProject.value.id, row.id); await loadProjectAttachments(); if (!attachments.value.length && attachmentPage.value > 1) { attachmentPage.value -= 1; await loadProjectAttachments() } ElMessage.success('附件已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '附件删除失败')) } finally { attachmentDeletingId.value = null } }
async function syncProjectFromRoute() { const rawProjectId = route.params.projectId; if (!rawProjectId) { selectedProject.value = null; detailLoading.value = false; tabContentLoading.value = false; return }; const projectId = Number(rawProjectId); if (!Number.isSafeInteger(projectId) || projectId <= 0) { await router.replace({ name: 'projects', query: {} }); return }; activeTab.value = normalizeProjectTab(route.query.tab); if (selectedProject.value?.id !== projectId || detailLoading.value) await refreshProject(projectId) }
function clearTabLoadingTimer() { if (tabLoadingTimer !== null) { window.clearTimeout(tabLoadingTimer); tabLoadingTimer = null } }
async function setProjectTab(tab: string | number) { const normalizedTab = normalizeProjectTab(String(tab)); if (normalizeProjectTab(route.query.tab) === normalizedTab) return; clearTabLoadingTimer(); tabContentLoading.value = true; if (route.name === 'project-detail' && route.params.projectId != null) await router.replace({ query: { ...route.query, tab: normalizedTab } }); if (normalizedTab === 'attachments') await loadProjectAttachments(); await nextTick(); await new Promise<void>(resolve => { tabLoadingTimer = window.setTimeout(resolve, 220) }); tabLoadingTimer = null; tabContentLoading.value = false }
async function openProject(project: Project) { selectedProject.value = project; activeTab.value = 'overview'; detailLoading.value = true; await router.push({ name: 'project-detail', params: { projectId: String(project.id) }, query: { tab: 'overview' } }) }
async function refreshSelectedProject() { if (selectedProject.value) await refreshProject(selectedProject.value.id) }
function resetSettingsForm() { settingsForm.plan_number_rule = selectedProject.value?.plan_number_rule || '{PROJECT_CODE}-P{SEQ:3}'; settingsForm.child_plan_number_rule = selectedProject.value?.child_plan_number_rule || '{PARENT_CODE}-S{SEQ:3}'; settingsForm.risk_number_rule = selectedProject.value?.risk_number_rule || '{PROJECT_CODE}-R{SEQ:3}' }
function openSettings() { resetSettingsForm() }
async function saveSettings() { if (!selectedProject.value || !settingsForm.plan_number_rule.trim() || !settingsForm.child_plan_number_rule.trim() || !settingsForm.risk_number_rule.trim()) { ElMessage.warning('请输入主计划、子计划和风险编号规则'); return }; saving.value = true; try { selectedProject.value = (await updateProjectSettings(selectedProject.value.id, settingsForm)).data.data; await loadWorkbench(); ElMessage.success('项目设置已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目设置保存失败')) } finally { saving.value = false } }
function openCreateProject() { projectEditingId.value = null; resetProjectForm(); projectDialog.value = true }
async function openEditProject() { if (!selectedProject.value) return; await loadUsers(); addUserOption(selectedProject.value.owner_id, selectedProject.value.owner_name); projectEditingId.value = selectedProject.value.id; Object.assign(projectForm, { project_code: selectedProject.value.project_code, project_name: selectedProject.value.project_name, description: selectedProject.value.description || '', status: selectedProject.value.status, owner_id: selectedProject.value.owner_id, planned_start_date: selectedProject.value.planned_start_date || '', planned_end_date: selectedProject.value.planned_end_date || '', actual_end_date: selectedProject.value.actual_end_date || '' }); projectDialog.value = true }
function validDateRange(start: unknown, end: unknown) { return !start || !end || String(end) >= String(start) }
async function saveProject() { if (!String(projectForm.project_code || '').trim() || !String(projectForm.project_name || '').trim()) { ElMessage.warning('请填写项目编号和项目名称'); return }; if (!validDateRange(projectForm.planned_start_date, projectForm.planned_end_date) || !validDateRange(projectForm.planned_start_date, projectForm.actual_end_date)) { ElMessage.warning('项目结束日期必须大于等于开始日期'); return }; saving.value = true; try { const response = projectEditingId.value ? await updateProject(projectEditingId.value, projectForm) : await createProject(projectForm); selectedProject.value = response.data.data; ensureProjectUserOptions(selectedProject.value); resetSettingsForm(); projectDialog.value = false; await loadWorkbench(); await router.push({ name: 'project-detail', params: { projectId: String(selectedProject.value.id) } }); ElMessage.success(projectEditingId.value ? '项目已更新' : '项目已创建') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目保存失败')) } finally { saving.value = false } }
async function removeProject() { if (!selectedProject.value) return; try { await ElMessageBox.confirm('删除项目后，项目计划、成员和角色也将不再显示，确认继续吗？', '删除确认', { type: 'warning' }); await deleteProject(selectedProject.value.id); selectedProject.value = null; await router.push({ name: 'projects' }); await loadWorkbench(); ElMessage.success('项目已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目删除失败')) } }
function openCreatePlanForGroup(group: PlanTimelineGroup, phase: PlanTimelineStage) { planEditingId.value = null; planParentName.value = ''; resetPlanForm(); planForm.phase = phase.key === '__UNASSIGNED__' ? '' : phase.key; planForm.group_id = group.id; planDialog.value = true }
function openCreateChildPlan(row: ProjectPlan) { planChildrenDialog.value = false; planEditingId.value = null; planParentName.value = row.plan_name; resetPlanForm(); planForm.parent_id = row.id; planForm.group_id = row.group_id || null; planForm.phase = row.phase || ''; planDialog.value = true }
async function openEditPlan(row: ProjectPlan) { planChildrenDialog.value = false; await loadUsers(); addUserOption(row.owner_id, row.owner_name); planEditingId.value = row.id; planParentName.value = row.parent_id ? (plans.value.find(plan => plan.id === row.parent_id)?.plan_name || '') : ''; Object.assign(planForm, { parent_id: row.parent_id, group_id: row.group_id || null, phase: row.phase || '', plan_name: row.plan_name, description: row.description || '', owner_id: row.owner_id || null, lead_org_id: row.lead_org_id || null, cooperating_org_ids: row.cooperating_org_ids || [], planned_start_date: row.planned_start_date || '', planned_end_date: row.planned_end_date || '', progress: row.progress, status: row.status, sort_no: row.sort_no }); planDialog.value = true }
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
function timelineGroupHeight(group: PlanTimelineGroup) { return `${Math.max(58, group.masters.some(plan => !planRange(plan)) ? 72 : 58)}px` }
function timelineMastersWithDate(group: PlanTimelineGroup) { return sortTimelinePlans(group.masters.filter(plan => planRange(plan))) }
function timelineMastersWithoutDate(group: PlanTimelineGroup) { return group.masters.filter(plan => !planRange(plan)) }
function timelineGridStyle() { return { gridTemplateColumns: `repeat(${Math.max(1, planTimelineMonths.value.length)}, minmax(112px, 1fr))` } }
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
async function savePlan() { if (!selectedProject.value || !String(planForm.plan_name || '').trim()) { ElMessage.warning('请填写计划名称'); return }; const isMainPlan = !Number(planForm.parent_id || 0); if (isMainPlan && (!String(planForm.planned_start_date || '').trim() || !String(planForm.planned_end_date || '').trim())) { ElMessage.warning('主计划必须填写开始和结束日期'); return }; if (!validDateRange(planForm.planned_start_date, planForm.planned_end_date)) { ElMessage.warning('计划结束日期必须大于等于开始日期'); return }; const sequenceMessage = mainPlanSequenceMessage(); if (sequenceMessage) { ElMessage.warning(sequenceMessage); return }; saving.value = true; try { if (planEditingId.value) await updateProjectPlan(selectedProject.value.id, planEditingId.value, planForm); else await createProjectPlan(selectedProject.value.id, planForm); planDialog.value = false; await refreshSelectedProject(); ElMessage.success('计划已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '计划保存失败')) } finally { saving.value = false } }
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
async function removeRisk(row: ProjectRisk) { if (!selectedProject.value) return; try { await ElMessageBox.confirm(`确认删除风险 ${row.risk_code} 吗？删除后不可恢复。`, '删除确认', { type: 'warning' }); await deleteProjectRisk(selectedProject.value.id, row.id); await refreshSelectedProject(); ElMessage.success('项目风险已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目风险删除失败')) } }
function openCreatePlanGroup(phase = '') { resetPlanGroupForm(phase); planGroupDialog.value = true }
async function savePlanGroup() {
  if (!selectedProject.value) return
  const projectId = selectedProject.value.id
  const colorKey = String(planGroupForm.color_key || 'brand') as ProjectPlanGroupColorToken
  const phase = String(planGroupForm.phase || '').trim()
  if (!phase) { ElMessage.warning('请选择计划阶段'); return }
  const payload = { phase, color_key: colorKey, description: String(planGroupForm.description || ''), sort_no: Number(planGroupForm.sort_no || 0) }
  saving.value = true
  try {
    const response = await createProjectPlanGroup(projectId, payload)
    if (response.data.code !== 0) throw new Error(response.data.message || '阶段计划保存失败')
    const savedGroup = response.data.data
    let listRefreshFailed = false
    if (savedGroup) {
      const groups = [...(selectedProject.value?.plan_groups || [])]
      const savedId = Number(savedGroup.id || 0)
      const fallbackGroup = { id: savedId, project_id: projectId, ...payload, group_name: String(savedGroup.stage_plan_code || savedGroup.group_name || '') }
      const index = groups.findIndex(group => Number(group.id) === savedId)
      if (index >= 0) groups[index] = { ...groups[index], ...fallbackGroup, ...savedGroup }
      else groups.push({ ...fallbackGroup, ...savedGroup })
      selectedProject.value = { ...selectedProject.value!, plan_groups: groups }
    } else {
      try {
        const refreshedProject = (await getProject(projectId)).data.data
        selectedProject.value = refreshedProject
        ensureProjectUserOptions(refreshedProject)
        resetSettingsForm()
      } catch {
        listRefreshFailed = true
      }
    }
    planGroupDialog.value = false
    if (listRefreshFailed) ElMessage.warning('阶段计划已保存，但列表刷新失败')
    else ElMessage.success('阶段计划已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '阶段计划保存失败'))
  } finally { saving.value = false }
}
async function removePlanGroup(group: PlanTimelineGroup) {
  if (!selectedProject.value || group.id == null || saving.value) return
  try {
    await ElMessageBox.confirm(`确认删除阶段计划“${group.name}”吗？删除后其下主计划和子计划将转为未分组。`, '删除确认', { type: 'warning' })
    saving.value = true
    await deleteProjectPlanGroup(selectedProject.value.id, group.id)
    await refreshSelectedProject()
    ElMessage.success('阶段计划已删除')
  } catch (error) {
    const action = (error as { action?: string }).action
    if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '阶段计划删除失败'))
  } finally { saving.value = false }
}
function startPlanDrag(row: ProjectPlanRow, event: DragEvent) { if (row.node_type !== 'plan' || row.parent_id) { event.preventDefault(); return }; draggingPlanId.value = row.id; event.dataTransfer?.setData('text/plain', String(row.id)); if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move' }
function endPlanDrag() { draggingPlanId.value = null }
async function dropPlanOnGroup(row: ProjectPlanRow, event: DragEvent) { event.preventDefault(); event.stopPropagation(); if (!selectedProject.value || row.node_type !== 'group' || draggingPlanId.value == null) return; const plan = plans.value.find(item => item.id === draggingPlanId.value); if (!plan || plan.parent_id) return; const groupId = row.group_id == null ? null : Number(row.group_id); if (plan.group_id === groupId) { endPlanDrag(); return }; saving.value = true; try { await moveProjectPlanToGroup(selectedProject.value.id, plan.id, groupId); await refreshSelectedProject(); ElMessage.success('主计划及其子计划已归入阶段计划') } catch (error) { ElMessage.error(apiErrorMessage(error, '阶段计划移动失败')) } finally { saving.value = false; endPlanDrag() } }
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
async function removePlan(row: ProjectPlan) { if (!selectedProject.value) return; try { const message = row.parent_id ? '确认删除该计划吗？' : '删除主计划后，其下全部子计划也会被删除，是否继续？'; await ElMessageBox.confirm(message, '删除确认', { type: 'warning' }); await deleteProjectPlan(selectedProject.value.id, row.id); if (selectedPlanForChildrenId.value === row.id) closePlanChildren(); await refreshSelectedProject(); ElMessage.success('计划已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '计划删除失败')) } }
async function loadUsers() { if (userOptionsLoaded.value) return; userOptionsLoading.value = true; try { const options = (await getProjectUserOptions()).data.data; options.forEach(item => addUserOption(item.id, item.display_name, item.username)); userOptionsLoaded.value = true } catch (error) { ElMessage.error(apiErrorMessage(error, '用户选项加载失败')) } finally { userOptionsLoading.value = false } }
function onUserOptionsVisible(visible: boolean) { if (visible) void loadUsers() }
async function openCreateMember() { memberEditingId.value = null; memberForm.user_id = null; memberForm.org_id = selectedProjectOrganizationId.value; memberForm.role_ids = []; memberForm.status = 1; await loadUsers(); memberDialog.value = true }
async function openEditMember(row: ProjectMember) { await loadUsers(); addUserOption(row.user_id, row.display_name); memberEditingId.value = row.id; memberForm.user_id = row.user_id; memberForm.org_id = row.org_id ? Number(row.org_id) : null; memberForm.role_ids = row.roles.map(role => role.id); memberForm.status = row.status; memberDialog.value = true }
async function saveMember() { if (!selectedProject.value || (!memberEditingId.value && !memberForm.user_id)) { ElMessage.warning('请选择成员'); return }; saving.value = true; try { const payload = { user_id: memberForm.user_id, org_id: memberForm.org_id, role_ids: memberForm.role_ids, status: memberForm.status }; if (memberEditingId.value) await updateProjectMember(selectedProject.value.id, memberEditingId.value, payload); else await createProjectMember(selectedProject.value.id, payload); memberDialog.value = false; await refreshSelectedProject(); ElMessage.success('成员已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '成员保存失败')) } finally { saving.value = false } }
async function removeMember(row: ProjectMember) { if (!selectedProject.value) return; try { await ElMessageBox.confirm('确认移除该项目成员吗？', '移除确认', { type: 'warning' }); await deleteProjectMember(selectedProject.value.id, row.id); await refreshSelectedProject(); ElMessage.success('成员已移除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '成员移除失败')) } }
function openCreateRole() { roleEditingId.value = null; resetRoleForm(); roleDialog.value = true }
function openEditRole(row: ProjectRole) { roleEditingId.value = row.id; Object.assign(roleForm, { role_code: row.role_code, role_name: row.role_name, description: row.description || '', member_ids: (row.members || []).map(member => member.id) }); roleDialog.value = true }
async function saveRole() { if (!selectedProject.value || !String(roleForm.role_code || '').trim() || !String(roleForm.role_name || '').trim()) { ElMessage.warning('请填写角色编码和角色名称'); return }; saving.value = true; try { if (roleEditingId.value) await updateProjectRole(selectedProject.value.id, roleEditingId.value, roleForm); else await createProjectRole(selectedProject.value.id, roleForm); roleDialog.value = false; await refreshSelectedProject(); ElMessage.success('项目角色已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目角色保存失败')) } finally { saving.value = false } }
async function removeRole(row: ProjectRole) { if (!selectedProject.value) return; try { await ElMessageBox.confirm('确认删除该项目角色吗？', '删除确认', { type: 'warning' }); await deleteProjectRole(selectedProject.value.id, row.id); await refreshSelectedProject(); ElMessage.success('项目角色已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目角色删除失败')) } }
function openCreateProjectOrganization(parentId = 0) { projectOrganizationEditingId.value = null; resetProjectOrganizationForm(parentId); projectOrganizationDialog.value = true }
function openEditProjectOrganization(row: ProjectOrganization) { projectOrganizationEditingId.value = row.id; Object.assign(projectOrganizationForm, { parent_id: row.parent_id, org_code: row.org_code, org_name: row.org_name, sort_no: row.sort_no, status: row.status }); projectOrganizationDialog.value = true }
async function saveProjectOrganization() { if (!selectedProject.value || !String(projectOrganizationForm.org_code || '').trim() || !String(projectOrganizationForm.org_name || '').trim()) { ElMessage.warning('请填写项目组织编码和名称'); return }; saving.value = true; try { if (projectOrganizationEditingId.value) await updateProjectOrganization(selectedProject.value.id, projectOrganizationEditingId.value, projectOrganizationForm); else await createProjectOrganization(selectedProject.value.id, projectOrganizationForm); projectOrganizationDialog.value = false; await refreshSelectedProject(); ElMessage.success('项目组织已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目组织保存失败')) } finally { saving.value = false } }
async function removeProjectOrganization(row: ProjectOrganization) { if (!selectedProject.value) return; try { await ElMessageBox.confirm(`确认删除项目组织“${row.org_name}”吗？`, '删除确认', { type: 'warning' }); await deleteProjectOrganization(selectedProject.value.id, row.id); if (selectedProjectOrganizationId.value === row.id) selectedProjectOrganizationId.value = null; await refreshSelectedProject(); ElMessage.success('项目组织已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目组织删除失败')) } }
function projectStatusTone(status: string): 'primary' | 'success' | 'warning' | 'danger' { return status === 'COMPLETED' ? 'success' : status === 'SUSPENDED' ? 'danger' : status === 'RUNNING' ? 'primary' : 'warning' }
function planStatusTone(status: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' { return status === 'COMPLETED' ? 'success' : status === 'BLOCKED' ? 'danger' : status === 'IN_PROGRESS' ? 'primary' : 'info' }
function riskStatusTone(value?: string | null, label?: string | null): 'primary' | 'success' | 'warning' | 'danger' | 'info' { const text = `${value || ''}${label || ''}`; return text.includes('关闭') || text.includes('解决') || value === 'CLOSED' || value === 'RESOLVED' ? 'success' : text.includes('升级') || text.includes('高') || value === 'OPEN' ? 'warning' : text.includes('处理中') || value === 'PROCESSING' ? 'primary' : 'info' }
watch([selectedProject, activeTab, plans, selectedMasterPlanId], () => { void refreshOverviewCharts(); void nextTick(applyPlanRowDragability) }, { deep: true })
watch([selectedProject, plans], () => {
  if (selectedMasterPlanId.value !== null && !mainGanttRows.value.some(row => row.plan.id === selectedMasterPlanId.value)) selectedMasterPlanId.value = null
}, { deep: true })
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
onMounted(async () => { await loadOptions(); if (route.params.projectId) await syncProjectFromRoute(); else await loadWorkbench(); window.addEventListener('resize', resizeCharts); chartThemeObserver = new MutationObserver(() => { void refreshOverviewCharts() }); chartThemeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'data-palette'] }) })
onBeforeUnmount(() => { clearTabLoadingTimer(); window.removeEventListener('resize', resizeCharts); chartThemeObserver?.disconnect(); disposeCharts() })
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
          <section class="project-overview__schedule"><el-card shadow="never" class="project-overview__chart project-overview__gantt-card"><div class="project-section-heading"><div><span class="panel-kicker">主计划排期</span><h3>主计划甘特图</h3></div><span class="muted">{{ selectedMasterPlan ? `已选：${selectedMasterPlan.plan_name}` : '点击计划条查看子计划' }} · 月份</span></div><div v-if="mainGanttRows.length" class="project-gantt-scroll"><div ref="mainGanttChartRef" class="project-gantt-canvas project-gantt-canvas--selectable" :style="{ width: ganttCanvasWidth(mainGanttRows, 'month'), height: ganttCanvasHeight(mainGanttRows) }" role="img" aria-label="主计划月维度甘特图，点击计划条查看子计划" /></div><div v-else class="project-gantt-empty"><el-empty description="暂无有效日期的主计划" :image-size="48" /></div><div class="project-gantt-legend"><span><i class="is-completed" />已完成</span><span><i class="is-progress" />进行中</span><span><i class="is-pending" />未开始</span><span><i class="is-blocked" />已阻塞</span></div></el-card><el-card shadow="never" class="project-overview__chart project-overview__gantt-card project-overview__child-gantt-card"><template v-if="selectedMasterPlan"><div class="project-section-heading"><div><span class="panel-kicker">子计划排期</span><h3>{{ selectedMasterPlan.plan_name }} · 子计划甘特图</h3></div><span class="muted">横轴：周</span></div><div v-if="childGanttRows.length" class="project-gantt-scroll"><div ref="childGanttChartRef" class="project-gantt-canvas" :style="{ width: ganttCanvasWidth(childGanttRows, 'week'), height: ganttCanvasHeight(childGanttRows) }" role="img" aria-label="当前主计划子计划周维度甘特图" /></div><div v-else class="project-gantt-empty"><el-empty description="该主计划暂无有效日期的子计划" :image-size="48" /></div><div v-if="childGanttRows.length" class="project-gantt-legend"><span><i class="is-completed" />已完成</span><span><i class="is-progress" />进行中</span><span><i class="is-pending" />未开始</span><span><i class="is-blocked" />已阻塞</span></div></template><div v-else class="project-gantt-selection-empty"><span class="project-gantt-selection-empty__icon">＋</span><strong>请选择主计划</strong><p>点击上方主计划甘特图中的计划条，查看对应的子计划排期。</p></div></el-card></section>
          <section class="project-overview__kpis"><div class="project-overview__kpi"><span>计划总数</span><strong>{{ planStats.total }}</strong><small>当前项目全部计划</small></div><div class="project-overview__kpi is-success"><span>已完成</span><strong>{{ planStats.completed }}</strong><small>完成率 {{ planStats.total ? Math.round(planStats.completed / planStats.total * 100) : 0 }}%</small></div><div class="project-overview__kpi is-brand"><span>进行中</span><strong>{{ planStats.inProgress }}</strong><small>正在执行的计划</small></div><div class="project-overview__kpi is-muted"><span>未开始</span><strong>{{ planStats.notStarted }}</strong><small>等待启动的计划</small></div><div class="project-overview__kpi is-danger"><span>已阻塞</span><strong>{{ planStats.blocked }}</strong><small>需要关注的计划</small></div><div class="project-overview__kpi is-accent"><span>平均进度</span><strong>{{ planStats.averageProgress }}%</strong><small>按计划平均计算</small></div></section>
          <section class="project-overview__charts"><el-card shadow="never" class="project-overview__chart"><div class="project-section-heading"><div><span class="panel-kicker">计划状态</span><h3>状态分布</h3></div></div><div v-if="planStats.total" ref="statusChartRef" class="project-chart project-chart--donut" /><el-empty v-else description="暂无计划数据" :image-size="54" /></el-card><el-card shadow="never" class="project-overview__chart"><div class="project-section-heading"><div><span class="panel-kicker">执行进度</span><h3>计划进度排行</h3></div></div><div v-if="planStats.progressData.length" ref="progressChartRef" class="project-chart" /><el-empty v-else description="暂无计划数据" :image-size="54" /></el-card></section>
        </div></el-tab-pane>
      <el-tab-pane name="plans" label="项目计划">
        <div class="project-tab-panel project-plan-timeline-panel">
          <div class="project-section-heading">
            <div><span class="panel-kicker">PROJECT PLAN</span><h2>项目计划</h2><p class="project-plan-group-hint"><el-icon><Calendar /></el-icon>阶段为纵轴，月份为横轴，点击主计划查看子计划</p></div>
          </div>
          <div v-if="planTimelineStages.length" class="project-plan-timeline" :style="timelineLayoutStyle()">
            <div class="project-plan-timeline__header">
              <div class="project-plan-timeline__axis-label">项目阶段</div>
              <div class="project-plan-timeline__group-axis-label">阶段计划</div>
              <div class="project-plan-timeline__months" :style="timelineGridStyle()"><span v-for="month in planTimelineMonths" :key="month.timestamp">{{ month.label }}</span></div>
            </div>
            <div class="project-plan-timeline__body">
              <section v-for="stage in planTimelineStages" :key="stage.key" class="project-plan-timeline__stage-row" :style="{ gridTemplateRows: `repeat(${stage.groups.length}, minmax(58px, auto))` }">
                <div class="project-plan-timeline__stage" :style="{ gridRow: `1 / span ${stage.groups.length}` }">
                  <div><el-icon><Calendar /></el-icon><strong>{{ stage.name }}</strong></div>
                  <small>{{ stage.masters.length }} 个主计划</small>
                  <el-button v-if="canCreatePlan" class="project-plan-timeline__stage-action" text type="primary" @click.stop="openCreatePlanGroup(stage.key)"><el-icon><Plus /></el-icon>新增阶段计划</el-button>
                </div>
                <template v-for="group in stage.groups" :key="group.key">
                  <div class="project-plan-timeline__group" :class="`project-plan-palette--${group.colorKey}`">
                    <div class="project-plan-timeline__group-title"><el-icon><Folder /></el-icon><strong>阶段计划 {{ group.name }}</strong><el-tooltip v-if="canDeletePlan && group.id != null" content="删除阶段计划"><el-button class="project-plan-timeline__group-delete" text type="danger" aria-label="删除阶段计划" :disabled="saving" @click.stop="removePlanGroup(group)"><el-icon><Delete /></el-icon></el-button></el-tooltip></div><small>{{ group.masters.length }} 个主计划</small>
                    <el-button v-if="canCreatePlan" class="project-plan-timeline__group-action" text type="primary" @click.stop="openCreatePlanForGroup(group, stage)"><el-icon><Plus /></el-icon>新增主计划</el-button>
                  </div>
                  <div class="project-plan-timeline__track" :style="{ minHeight: timelineGroupHeight(group) }">
                    <div class="project-plan-timeline__grid" :style="timelineGridStyle()" aria-hidden="true"><i v-for="month in planTimelineMonths" :key="month.timestamp" /></div>
                    <button v-for="master in timelineMastersWithDate(group)" :key="master.id" type="button" :class="['project-plan-timeline__bar', `project-plan-palette--${group.colorKey}`, `project-plan-status--${String(master.status || 'NOT_STARTED').toLowerCase()}`]" :style="timelineBarStyle(master)" :title="master.plan_name" @click="openPlanChildren(master)">
                      <span class="project-plan-timeline__bar-progress" :style="{ width: `${Math.max(0, Math.min(100, Number(master.progress || 0)))}%` }" />
                      <span class="project-plan-timeline__bar-content"><strong>{{ master.plan_name }}</strong><small>{{ Math.round(Number(master.progress || 0)) }}%</small></span>
                    </button>
                    <button v-for="master in timelineMastersWithoutDate(group)" :key="`undated-${master.id}`" type="button" class="project-plan-timeline__undated" @click="openPlanChildren(master)">未排期：{{ master.plan_name }}</button>
                    <span v-if="!group.masters.length" class="project-plan-timeline__empty">暂无主计划</span>
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
        <el-tab-pane name="settings" label="项目设置"><div class="project-tab-panel project-settings"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT SETTINGS</span><h2>项目设置</h2></div></div><el-form label-position="top" class="project-settings__form"><el-form-item label="主计划编号规则"><el-input v-model="settingsForm.plan_number_rule" maxlength="128" show-word-limit /></el-form-item><p class="project-settings__hint">主计划支持 {PROJECT_CODE}、{SEQ}、{SEQ:3}、{YYYY}、{MM}、{DD}。例如：{PROJECT_CODE}-P{SEQ:3}，项目编号为 RDC 时会生成 RDC-P001。</p><el-form-item label="子计划编号规则"><el-input v-model="settingsForm.child_plan_number_rule" maxlength="128" show-word-limit /></el-form-item><p class="project-settings__hint">子计划支持 {PARENT_CODE}、{SEQ}、{SEQ:3}、{YYYY}、{MM}、{DD}。例如：{PARENT_CODE}-S{SEQ:3}，主计划 RDC-P001 下会生成 RDC-P001-S001。</p><el-form-item label="项目风险编号规则"><el-input v-model="settingsForm.risk_number_rule" maxlength="128" show-word-limit /></el-form-item><p class="project-settings__hint">风险支持 {PROJECT_CODE}、{SEQ}、{SEQ:3}、{YYYY}、{MM}、{DD}。例如：{PROJECT_CODE}-R{SEQ:3}，项目编号为 RDC 时会生成 RDC-R001。</p><el-button v-if="canUpdateProject && isOwner" type="primary" :loading="saving" @click="saveSettings">保存设置</el-button></el-form></div></el-tab-pane>
        <el-tab-pane name="attachments" label="项目附件">
          <div class="project-tab-panel project-attachments-panel">
            <div class="project-section-heading">
              <div><span class="panel-kicker">PROJECT ATTACHMENTS</span><h2>项目附件</h2><p class="project-attachments__hint">项目成员可查看、预览和下载；具备项目更新权限的成员可上传或删除。</p></div>
              <el-upload v-if="canUpdateProject" :auto-upload="false" :show-file-list="false" :disabled="attachmentUploading" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.png,.jpg,.jpeg,.gif,.zip,.rar" :on-change="handleAttachmentChange"><el-button type="primary" :loading="attachmentUploading"><el-icon><Upload /></el-icon>上传附件</el-button></el-upload>
            </div>
            <div class="project-attachments__toolbar">
              <el-input v-model="attachmentKeyword" class="project-attachments__search" clearable placeholder="按文件名检索" @keyup.enter="searchAttachments">
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-button type="primary" @click="searchAttachments">查询</el-button>
              <el-button @click="resetAttachmentSearch">重置</el-button>
            </div>
            <div v-loading="attachmentsLoading" class="project-attachments__content">
              <el-empty v-if="!attachmentsLoading && !attachments.length" description="暂无项目附件" :image-size="72" />
              <div v-else class="project-attachment-list">
                <article v-for="attachment in attachments" :key="attachment.id" class="project-attachment-item">
                  <div class="project-attachment-item__icon"><el-icon><Document /></el-icon></div>
                  <div class="project-attachment-item__main"><strong :title="attachment.fileName">{{ attachment.fileName }}</strong><span>{{ formatAttachmentSize(attachment.size) }} · {{ attachment.uploaderName || '未知用户' }} · {{ formatDateOnly(attachment.createdAt) || '-' }}</span></div>
                  <div class="project-attachment-item__actions"><el-button link type="primary" @click="previewAttachment(attachment)"><el-icon><View /></el-icon>预览</el-button><el-button link type="primary" @click="downloadAttachment(attachment)"><el-icon><Download /></el-icon>下载</el-button><el-button v-if="canUpdateProject" link type="danger" :loading="attachmentDeletingId === attachment.id" @click="removeAttachment(attachment)"><el-icon><Delete /></el-icon>删除</el-button></div>
                </article>
              </div>
            </div>
            <div v-if="!attachmentsLoading && attachmentTotal > 0" class="project-attachments__footer">
              <span class="muted">共 {{ attachmentTotal }} 个附件，按上传时间倒序</span>
              <UiPagination :page="attachmentPage" :page-size="attachmentPageSize" :total="attachmentTotal" :page-sizes="[10, 20, 50]" @update:page="changeAttachmentPage" @update:page-size="changeAttachmentPageSize" />
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

    <el-dialog v-model="projectDialog" :title="projectEditingId ? '编辑项目' : '新建项目'" width="600px" destroy-on-close><el-form label-position="top"><el-row :gutter="16"><el-col :span="12"><el-form-item label="项目编号" required><el-input v-model="projectForm.project_code" /></el-form-item></el-col><el-col :span="12"><el-form-item label="项目名称" required><el-input v-model="projectForm.project_name" /></el-form-item></el-col></el-row><el-form-item label="项目描述"><el-input v-model="projectForm.description" type="textarea" :rows="3" /></el-form-item><el-row :gutter="16"><el-col :span="12"><el-form-item label="项目状态"><el-select v-model="projectForm.status" style="width:100%"><el-option v-for="(label, value) in projectStatusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="负责人"><el-select v-model="projectForm.owner_id" filterable :loading="userOptionsLoading" style="width:100%" @visible-change="onUserOptionsVisible"><el-option v-for="item in userOptions" :key="item.id" :label="userOptionLabel(item)" :value="item.id" /></el-select></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="8"><el-form-item label="计划开始"><el-date-picker v-model="projectForm.planned_start_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="计划结束"><el-date-picker v-model="projectForm.planned_end_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="实际结束"><el-date-picker v-model="projectForm.actual_end_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="projectDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveProject">保存</el-button></template></el-dialog>
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
    <el-dialog v-model="planDialog" :title="planEditingId ? '编辑项目计划' : (planParentName ? '新增子计划' : '新增主计划')" width="600px" destroy-on-close><el-form label-position="top"><el-form-item label="计划名称" required><el-input v-model="planForm.plan_name" /></el-form-item><el-form-item label="计划层级"><el-input :model-value="planParentName ? `子计划（${planParentName}）` : '主计划'" disabled /></el-form-item><el-form-item label="计划描述"><el-input v-model="planForm.description" type="textarea" :rows="2" /></el-form-item><el-row :gutter="16"><el-col :span="12"><el-form-item label="负责人"><el-select v-model="planForm.owner_id" clearable filterable :loading="userOptionsLoading" style="width:100%" @visible-change="onUserOptionsVisible"><el-option v-for="item in userOptions" :key="item.id" :label="userOptionLabel(item)" :value="item.id" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="状态"><el-select v-model="planForm.status" style="width:100%"><el-option v-for="(label, value) in planStatusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-col></el-row><el-form-item label="牵头方"><UiTreeSelect :model-value="planForm.lead_org_id === null ? null : Number(planForm.lead_org_id || 0)" :options="organizationTreeOptions" placeholder="请选择牵头组织" @update:model-value="planForm.lead_org_id = $event" /></el-form-item><el-form-item label="配合方"><el-tree-select v-model="planForm.cooperating_org_ids" :data="organizationTreeOptions" multiple show-checkbox check-strictly clearable filterable node-key="value" placeholder="请选择配合组织" style="width:100%" /></el-form-item><el-row :gutter="16"><el-col :span="12"><el-form-item label="计划开始" :required="!Number(planForm.parent_id || 0)"><el-date-picker v-model="planForm.planned_start_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item label="计划结束" :required="!Number(planForm.parent_id || 0)"><el-date-picker v-model="planForm.planned_end_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row><el-form-item label="完成进度"><el-slider v-model="planForm.progress" :max="100" /></el-form-item></el-form><template #footer><el-button @click="planDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePlan">保存</el-button></template></el-dialog>
<el-dialog v-model="planGroupDialog" title="新增阶段计划" width="520px" destroy-on-close><el-form label-position="top"><el-form-item label="所属阶段" required><el-select v-model="planGroupForm.phase" clearable style="width:100%" placeholder="请选择计划阶段"><el-option v-for="item in projectOptions.plan_phases" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="阶段计划编号"><el-input model-value="保存后按阶段自动生成，例如 1-1" disabled /></el-form-item><el-form-item label="阶段计划配色"><div class="project-plan-palette-picker"><button v-for="palette in planGroupColorOptions" :key="palette.key" type="button" class="project-plan-palette-option project-plan-palette--brand" :class="{ 'is-selected': planGroupForm.color_key === palette.key }" :style="{ '--palette-color': 'var(' + palette.colorVar + ')', '--palette-accent': 'var(' + palette.accentVar + ')' }" :aria-label="palette.key" @click="planGroupForm.color_key = palette.key"><span class="project-plan-palette-option__swatch" /></button></div></el-form-item><el-form-item label="阶段计划说明"><el-input v-model="planGroupForm.description" type="textarea" :rows="3" maxlength="500" /></el-form-item><el-form-item label="排序号"><el-input-number v-model="planGroupForm.sort_no" :min="0" :max="9999" controls-position="right" /></el-form-item></el-form><template #footer><el-button @click="planGroupDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePlanGroup">保存</el-button></template></el-dialog>
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
    <UiFilePreview v-model="attachmentPreviewVisible" :url="attachmentPreviewUrl" :file-name="attachmentPreviewName" />
  </section>
</template>
