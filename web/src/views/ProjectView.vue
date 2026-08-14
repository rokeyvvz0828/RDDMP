<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Briefcase, Calendar, Delete, Edit, Folder, Plus, User } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { apiErrorMessage } from '../api/error'
import { createProject, createProjectMember, createProjectPlan, createProjectPlanGroup, createProjectRole, deleteProject, deleteProjectMember, deleteProjectPlan, deleteProjectPlanGroup, deleteProjectRole, getProject, getProjectOptions, getProjectUserOptions, getProjectWorkbench, moveProjectPlanToGroup, updateProject, updateProjectMember, updateProjectPlan, updateProjectPlanGroup, updateProjectRole, updateProjectSettings } from '../api/project'
import type { Project, ProjectMember, ProjectOptions, ProjectPlan, ProjectRole, ProjectStatus, PlanStatus, ProjectUserOption, ProjectOrganizationOption, ProjectPlanGroupColorToken } from '../types/project'
import { formatDateOnly } from '../utils/date'
import { useAuthStore } from '../stores/auth'
import UiDataTable from '../components/ui/UiDataTable.vue'
import UiStatusTag from '../components/ui/UiStatusTag.vue'
import UiUserIdentity from '../components/ui/UiUserIdentity.vue'
import UiTreeSelect, { type UiTreeOption } from '../components/ui/UiTreeSelect.vue'
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
const projectTabs = ['overview', 'plans', 'members', 'roles', 'settings'] as const
type ProjectTab = typeof projectTabs[number]
const normalizeProjectTab = (value: unknown): ProjectTab => {
  const tab = Array.isArray(value) ? value[0] : value
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
const memberDialog = ref(false)
const memberEditingId = ref<number | null>(null)
const memberForm = reactive<{ user_id: number | null; role_ids: number[]; status: number }>({ user_id: null, role_ids: [], status: 1 })
const roleDialog = ref(false)
const roleEditingId = ref<number | null>(null)
const roleForm = reactive<Record<string, unknown>>({})
const planGroupDialog = ref(false)
const planGroupEditingId = ref<number | null>(null)
const planGroupForm = reactive<Record<string, unknown>>({ group_name: '', color_key: 'brand' as ProjectPlanGroupColorToken, description: '', sort_no: 0 })
const draggingPlanId = ref<number | null>(null)
const settingsForm = reactive({ plan_number_rule: '', child_plan_number_rule: '' })
const calendarDate = ref(new Date())
const userOptions = ref<ProjectUserOption[]>([])
const userOptionsLoaded = ref(false)
const userOptionsLoading = ref(false)
const projectOptions = ref<ProjectOptions>({ project_phases: [], plan_phases: [], organizations: [] })
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

const projectPermissions = computed(() => new Set(auth.user?.permissions || []))
const can = (permission: string) => projectPermissions.value.has(permission)
const canCreateProject = computed(() => can('project:project:list:create'))
const canUpdateProject = computed(() => can('project:project:list:update'))
const canDeleteProject = computed(() => can('project:project:list:delete'))
const canCreatePlan = computed(() => can('project:plan:list:create'))
const canUpdatePlan = computed(() => can('project:plan:list:update'))
const canDeletePlan = computed(() => can('project:plan:list:delete'))
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
type ProjectPlanRow = ProjectPlan & { children?: ProjectPlanRow[]; node_type?: 'group' | 'plan'; group_count?: number; group_color_key?: ProjectPlanGroupColorToken }
const groupRowId = (groupId: number | null) => groupId === null ? -1 : -1000000 - groupId
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
  const rows: ProjectPlanRow[] = []
  const sortedGroups = [...planGroups.value].sort((left, right) => Number(left.sort_no || 0) - Number(right.sort_no || 0) || left.id - right.id)
  sortedGroups.forEach(group => {
    const children = buildPlanBranch(grouped.get(group.id) || [])
    rows.push({ id: groupRowId(group.id), node_type: 'group', group_id: group.id, group_name: group.group_name, group_color_key: group.color_key || 'brand', group_count: children.length, plan_name: group.group_name, plan_code: '分组', parent_id: 0, progress: 0, status: 'NOT_STARTED', sort_no: group.sort_no, project_id: selectedProject.value?.id || 0, children } as ProjectPlanRow)
    grouped.delete(group.id)
  })
  const ungrouped = buildPlanBranch(grouped.get(null) || [])
  if (ungrouped.length || !planGroups.value.length && flatPlans.value.length) rows.push({ id: groupRowId(null), node_type: 'group', group_id: null, group_name: '未分组', group_color_key: 'brand', group_count: ungrouped.length, plan_name: '未分组', plan_code: '分组', parent_id: 0, progress: 0, status: 'NOT_STARTED', sort_no: 0, project_id: selectedProject.value?.id || 0, children: ungrouped } as ProjectPlanRow)
  return rows
})
const plans = flatPlans
const members = computed(() => selectedProject.value?.members || [])
const roles = computed(() => selectedProject.value?.roles || [])
const calendarPlans = computed(() => plans.value.filter(plan => plan.planned_start_date || plan.planned_end_date))
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
const statusChartRef = ref<HTMLElement | null>(null)
const progressChartRef = ref<HTMLElement | null>(null)
const scheduleChartRef = ref<HTMLElement | null>(null)
let statusChart: echarts.ECharts | null = null
let progressChart: echarts.ECharts | null = null
let scheduleChart: echarts.ECharts | null = null

function cssVar(name: string) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

function chartTextColor() { return cssVar('--text') || '#15232d' }
function chartMutedColor() { return cssVar('--muted') || '#637480' }
function chartLineColor() { return cssVar('--line') || '#d7e0e5' }
function chartPanelColor() { return cssVar('--panel-bg') || '#ffffff' }
function chartBrandColor() { return cssVar('--brand') || '#147d92' }
function chartBrandSoftColor() { const color = chartBrandColor(); const hex = color.replace('#', ''); if (hex.length !== 6) return color; return `rgba(${parseInt(hex.slice(0, 2), 16)}, ${parseInt(hex.slice(2, 4), 16)}, ${parseInt(hex.slice(4, 6), 16)}, .12)` }

function renderCharts() {
  if (!statusChartRef.value || !progressChartRef.value || !scheduleChartRef.value) return
  statusChart ||= echarts.init(statusChartRef.value)
  progressChart ||= echarts.init(progressChartRef.value)
  scheduleChart ||= echarts.init(scheduleChartRef.value)
  const text = chartTextColor()
  const muted = chartMutedColor()
  const line = chartLineColor()
  const panel = chartPanelColor()
  const brand = chartBrandColor()
  const stats = planStats.value
  statusChart.setOption({
    animationDuration: 500,
    tooltip: { trigger: 'item', formatter: '{b}: {c} 项 ({d}%)' },
    legend: { bottom: 0, left: 'center', itemWidth: 9, itemHeight: 9, textStyle: { color: muted, fontSize: 11 } },
    series: [{ type: 'pie', radius: ['54%', '76%'], center: ['50%', '43%'], avoidLabelOverlap: true, itemStyle: { borderColor: panel, borderWidth: 3 }, label: { show: true, color: text, fontSize: 12, formatter: '{c}' }, data: stats.statusData.map(item => ({ value: item.value, name: item.name, itemStyle: { color: cssVar(item.color) } })) }]
  }, true)
  progressChart.setOption({
    animationDuration: 500,
    grid: { left: 92, right: 18, top: 12, bottom: 22 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (items: Array<{ name: string; value: number }>) => `${items[0]?.name || ''}<br/>进度：${items[0]?.value || 0}%` },
    xAxis: { type: 'value', max: 100, axisLabel: { color: muted, fontSize: 10, formatter: '{value}%' }, splitLine: { lineStyle: { color: line } } },
    yAxis: { type: 'category', inverse: true, axisLabel: { color: text, fontSize: 11, width: 78, overflow: 'truncate' }, axisLine: { show: false }, axisTick: { show: false }, data: stats.progressData.map(item => item.name) },
    series: [{ type: 'bar', barMaxWidth: 14, showBackground: true, backgroundStyle: { color: chartBrandSoftColor() }, itemStyle: { color: brand, borderRadius: [0, 4, 4, 0] }, label: { show: true, position: 'right', color: text, fontSize: 10, formatter: '{c}%' }, data: stats.progressData.map(item => item.value) }]
  }, true)
  const scheduleData = [...calendarPlans.value].sort((left, right) => String(left.planned_start_date || '').localeCompare(String(right.planned_start_date || ''))).slice(0, 8)
  const minDate = scheduleData.reduce((min, plan) => String(plan.planned_start_date || plan.planned_end_date || min), scheduleData[0]?.planned_start_date || new Date().toISOString().slice(0, 10))
  const maxDate = scheduleData.reduce((max, plan) => String(plan.planned_end_date || plan.planned_start_date || max), scheduleData[0]?.planned_end_date || minDate)
  const start = new Date(minDate).getTime()
  const end = Math.max(new Date(maxDate).getTime(), start + 86400000)
  const duration = Math.max(1, Math.ceil((end - start) / 86400000))
  // ECharts bars start at zero; use transparent offsets so the visible bar begins at its planned start.
  scheduleChart.setOption({
    animationDuration: 500,
    grid: { left: 92, right: 18, top: 12, bottom: 28 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (items: Array<{ dataIndex: number }>) => { const plan = scheduleData[items[0]?.dataIndex || 0]; return `${plan?.plan_name || ''}<br/>${formatDateOnly(plan?.planned_start_date) || '未设置'} 至 ${formatDateOnly(plan?.planned_end_date) || '未设置'}` } },
    xAxis: { type: 'value', min: 0, max: duration, axisLabel: { color: muted, fontSize: 10, formatter: (value: number) => `${Math.round(value)}天` }, splitLine: { lineStyle: { color: line } } },
    yAxis: { type: 'category', inverse: true, axisLabel: { color: text, fontSize: 11, width: 78, overflow: 'truncate' }, axisLine: { show: false }, axisTick: { show: false }, data: scheduleData.map(plan => plan.plan_name) },
    series: [
      { type: 'bar', stack: 'schedule', itemStyle: { color: 'transparent' }, emphasis: { itemStyle: { color: 'transparent' } }, data: scheduleData.map(plan => Math.max(0, Math.round((new Date(String(plan.planned_start_date || minDate)).getTime() - start) / 86400000))) },
      { name: '排期', type: 'bar', stack: 'schedule', barWidth: 13, itemStyle: { color: brand, borderRadius: 4 }, data: scheduleData.map(plan => { const planStart = new Date(String(plan.planned_start_date || minDate)).getTime(); const planEnd = new Date(String(plan.planned_end_date || plan.planned_start_date || minDate)).getTime(); return Math.max(1, Math.ceil((planEnd - planStart) / 86400000) + 1) }) }
    ]
  }, true)
}

function resizeCharts() { statusChart?.resize(); progressChart?.resize(); scheduleChart?.resize() }
function disposeCharts() { statusChart?.dispose(); progressChart?.dispose(); scheduleChart?.dispose(); statusChart = null; progressChart = null; scheduleChart = null }
async function refreshOverviewCharts() { await nextTick(); if (activeTab.value === 'overview') renderCharts() }

function resetProjectForm() { Object.keys(projectForm).forEach(key => delete projectForm[key]); Object.assign(projectForm, { project_code: '', project_name: '', description: '', status: 'PLANNING', phase: projectOptions.value.project_phases[0]?.value || '', owner_id: auth.user?.id || null, planned_start_date: '', planned_end_date: '', actual_end_date: '' }) }
function resetPlanForm() { Object.keys(planForm).forEach(key => delete planForm[key]); Object.assign(planForm, { parent_id: 0, plan_name: '', description: '', owner_id: null, phase: projectOptions.value.plan_phases[0]?.value || '', lead_org_id: null, cooperating_org_ids: [], planned_start_date: '', planned_end_date: '', progress: 0, status: 'NOT_STARTED', sort_no: 0 }) }
function resetRoleForm() { Object.keys(roleForm).forEach(key => delete roleForm[key]); Object.assign(roleForm, { role_code: '', role_name: '', description: '' }) }
function resetPlanGroupForm() { Object.keys(planGroupForm).forEach(key => delete planGroupForm[key]); Object.assign(planGroupForm, { group_name: '', color_key: 'brand', description: '', sort_no: 0 }) }
async function loadOptions() { try { projectOptions.value = (await getProjectOptions()).data.data } catch (error) { ElMessage.error(apiErrorMessage(error, '项目参数加载失败')) } }
async function loadWorkbench() { loading.value = true; try { projects.value = (await getProjectWorkbench()).data.data } catch (error) { ElMessage.error(apiErrorMessage(error, '项目加载失败')) } finally { workbenchLoaded.value = true; loading.value = false } }
function addUserOption(id: number | null | undefined, displayName?: string | null, username = '') { if (!id) return; const existing = userOptions.value.find(item => item.id === id); if (existing) { if (displayName) existing.display_name = displayName; if (username) existing.username = username; return }; userOptions.value.push({ id, username, display_name: displayName || `用户 ${id}` }) }
function userOptionLabel(item: ProjectUserOption) { return item.username ? `${item.display_name}（${item.username}）` : item.display_name }
function ensureProjectUserOptions(project: Project) { addUserOption(project.owner_id, project.owner_name); project.plans?.forEach(plan => addUserOption(plan.owner_id, plan.owner_name)); project.members?.forEach(member => addUserOption(member.user_id, member.display_name)) }
async function refreshProject(projectId: number) { detailLoading.value = true; try { selectedProject.value = (await getProject(projectId)).data.data; ensureProjectUserOptions(selectedProject.value); resetSettingsForm() } catch (error) { selectedProject.value = null; ElMessage.error(apiErrorMessage(error, '项目详情加载失败')); await router.replace({ name: 'projects', query: {} }) } finally { detailLoading.value = false } }
async function syncProjectFromRoute() { const rawProjectId = route.params.projectId; if (!rawProjectId) { selectedProject.value = null; detailLoading.value = false; tabContentLoading.value = false; return }; const projectId = Number(rawProjectId); if (!Number.isSafeInteger(projectId) || projectId <= 0) { await router.replace({ name: 'projects', query: {} }); return }; activeTab.value = normalizeProjectTab(route.query.tab); if (selectedProject.value?.id !== projectId || detailLoading.value) await refreshProject(projectId) }
function clearTabLoadingTimer() { if (tabLoadingTimer !== null) { window.clearTimeout(tabLoadingTimer); tabLoadingTimer = null } }
async function setProjectTab(tab: string | number) { const normalizedTab = normalizeProjectTab(String(tab)); if (normalizeProjectTab(route.query.tab) === normalizedTab) return; clearTabLoadingTimer(); tabContentLoading.value = true; if (route.name === 'project-detail' && route.params.projectId != null) await router.replace({ query: { ...route.query, tab: normalizedTab } }); await nextTick(); await new Promise<void>(resolve => { tabLoadingTimer = window.setTimeout(resolve, 220) }); tabLoadingTimer = null; tabContentLoading.value = false }
async function openProject(project: Project) { selectedProject.value = project; activeTab.value = 'overview'; detailLoading.value = true; await router.push({ name: 'project-detail', params: { projectId: String(project.id) }, query: { tab: 'overview' } }) }
async function refreshSelectedProject() { if (selectedProject.value) await refreshProject(selectedProject.value.id) }
function resetSettingsForm() { settingsForm.plan_number_rule = selectedProject.value?.plan_number_rule || '{PROJECT_CODE}-P{SEQ:3}'; settingsForm.child_plan_number_rule = selectedProject.value?.child_plan_number_rule || '{PARENT_CODE}-S{SEQ:3}' }
function openSettings() { resetSettingsForm() }
async function saveSettings() { if (!selectedProject.value || !settingsForm.plan_number_rule.trim() || !settingsForm.child_plan_number_rule.trim()) { ElMessage.warning('请输入主计划和子计划编号规则'); return }; saving.value = true; try { selectedProject.value = (await updateProjectSettings(selectedProject.value.id, settingsForm)).data.data; await loadWorkbench(); ElMessage.success('项目设置已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目设置保存失败')) } finally { saving.value = false } }
function plansForDay(day: string) { return calendarPlans.value.filter(plan => (!plan.planned_start_date || day >= plan.planned_start_date.slice(0, 10)) && (!plan.planned_end_date || day <= plan.planned_end_date.slice(0, 10))) }
function openCalendarPlan() { void setProjectTab('plans') }
function openCreateProject() { projectEditingId.value = null; resetProjectForm(); projectDialog.value = true }
async function openEditProject() { if (!selectedProject.value) return; await loadUsers(); addUserOption(selectedProject.value.owner_id, selectedProject.value.owner_name); projectEditingId.value = selectedProject.value.id; Object.assign(projectForm, { project_code: selectedProject.value.project_code, project_name: selectedProject.value.project_name, description: selectedProject.value.description || '', status: selectedProject.value.status, phase: selectedProject.value.phase || '', owner_id: selectedProject.value.owner_id, planned_start_date: selectedProject.value.planned_start_date || '', planned_end_date: selectedProject.value.planned_end_date || '', actual_end_date: selectedProject.value.actual_end_date || '' }); projectDialog.value = true }
function validDateRange(start: unknown, end: unknown) { return !start || !end || String(end) >= String(start) }
async function saveProject() { if (!String(projectForm.project_code || '').trim() || !String(projectForm.project_name || '').trim()) { ElMessage.warning('请填写项目编号和项目名称'); return }; if (!validDateRange(projectForm.planned_start_date, projectForm.planned_end_date) || !validDateRange(projectForm.planned_start_date, projectForm.actual_end_date)) { ElMessage.warning('项目结束日期必须大于等于开始日期'); return }; saving.value = true; try { const response = projectEditingId.value ? await updateProject(projectEditingId.value, projectForm) : await createProject(projectForm); selectedProject.value = response.data.data; ensureProjectUserOptions(selectedProject.value); resetSettingsForm(); projectDialog.value = false; await loadWorkbench(); await router.push({ name: 'project-detail', params: { projectId: String(selectedProject.value.id) } }); ElMessage.success(projectEditingId.value ? '项目已更新' : '项目已创建') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目保存失败')) } finally { saving.value = false } }
async function removeProject() { if (!selectedProject.value) return; try { await ElMessageBox.confirm('删除项目后，项目计划、成员和角色也将不再显示，确认继续吗？', '删除确认', { type: 'warning' }); await deleteProject(selectedProject.value.id); selectedProject.value = null; await router.push({ name: 'projects' }); await loadWorkbench(); ElMessage.success('项目已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目删除失败')) } }
function openCreatePlan() { planEditingId.value = null; planParentName.value = ''; resetPlanForm(); planDialog.value = true }
function openCreateChildPlan(row: ProjectPlan) { planEditingId.value = null; planParentName.value = row.plan_name; resetPlanForm(); planForm.parent_id = row.id; planDialog.value = true }
async function openEditPlan(row: ProjectPlan) { await loadUsers(); addUserOption(row.owner_id, row.owner_name); planEditingId.value = row.id; planParentName.value = row.parent_id ? (plans.value.find(plan => plan.id === row.parent_id)?.plan_name || '') : ''; Object.assign(planForm, { parent_id: row.parent_id, plan_name: row.plan_name, description: row.description || '', owner_id: row.owner_id || null, phase: row.phase || '', lead_org_id: row.lead_org_id || null, cooperating_org_ids: row.cooperating_org_ids || [], planned_start_date: row.planned_start_date || '', planned_end_date: row.planned_end_date || '', progress: row.progress, status: row.status, sort_no: row.sort_no }); planDialog.value = true }
async function savePlan() { if (!selectedProject.value || !String(planForm.plan_name || '').trim()) { ElMessage.warning('请填写计划名称'); return }; if (!validDateRange(planForm.planned_start_date, planForm.planned_end_date)) { ElMessage.warning('计划结束日期必须大于等于开始日期'); return }; saving.value = true; try { if (planEditingId.value) await updateProjectPlan(selectedProject.value.id, planEditingId.value, planForm); else await createProjectPlan(selectedProject.value.id, planForm); planDialog.value = false; await refreshSelectedProject(); ElMessage.success('计划已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '计划保存失败')) } finally { saving.value = false } }
function openCreatePlanGroup() { planGroupEditingId.value = null; resetPlanGroupForm(); planGroupDialog.value = true }
function openEditPlanGroup(row: ProjectPlanRow) { if (!row.group_id) return; planGroupEditingId.value = Number(row.group_id); const group = planGroups.value.find(item => item.id === Number(row.group_id)); Object.assign(planGroupForm, { group_name: row.group_name || '', color_key: group?.color_key || 'brand', description: group?.description || '', sort_no: row.sort_no || 0 }); planGroupDialog.value = true }
async function savePlanGroup() {
  if (!selectedProject.value || !String(planGroupForm.group_name || '').trim()) { ElMessage.warning('请输入分组名称'); return }
  const projectId = selectedProject.value.id
  const colorKey = String(planGroupForm.color_key || 'brand') as ProjectPlanGroupColorToken
  const payload = { group_name: String(planGroupForm.group_name).trim(), color_key: colorKey, description: String(planGroupForm.description || ''), sort_no: Number(planGroupForm.sort_no || 0) }
  saving.value = true
  try {
    const response = planGroupEditingId.value ? await updateProjectPlanGroup(projectId, planGroupEditingId.value, payload) : await createProjectPlanGroup(projectId, payload)
    if (response.data.code !== 0) throw new Error(response.data.message || '计划分组保存失败')
    const savedGroup = response.data.data
    let listRefreshFailed = false
    if (savedGroup) {
      const groups = [...(selectedProject.value?.plan_groups || [])]
      const savedId = Number(savedGroup.id || planGroupEditingId.value || 0)
      const fallbackGroup = { id: savedId, project_id: projectId, ...payload }
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
    if (listRefreshFailed) ElMessage.warning('计划分组已保存，但列表刷新失败')
    else ElMessage.success('计划分组已保存')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error, '计划分组保存失败'))
  } finally { saving.value = false }
}
async function removePlanGroup(row: ProjectPlanRow) { if (!selectedProject.value || !row.group_id) return; try { await ElMessageBox.confirm('删除分组后，其中的计划会移入未分组，不会删除计划，确认继续吗？', '删除分组', { type: 'warning' }); await deleteProjectPlanGroup(selectedProject.value.id, Number(row.group_id)); await refreshSelectedProject(); ElMessage.success('计划分组已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '计划分组删除失败')) } }
function startPlanDrag(row: ProjectPlanRow, event: DragEvent) { if (row.node_type !== 'plan' || row.parent_id) { event.preventDefault(); return }; draggingPlanId.value = row.id; event.dataTransfer?.setData('text/plain', String(row.id)); if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move' }
function endPlanDrag() { draggingPlanId.value = null }
async function dropPlanOnGroup(row: ProjectPlanRow, event: DragEvent) { event.preventDefault(); event.stopPropagation(); if (!selectedProject.value || row.node_type !== 'group' || draggingPlanId.value == null) return; const plan = plans.value.find(item => item.id === draggingPlanId.value); if (!plan || plan.parent_id) return; const groupId = row.group_id == null ? null : Number(row.group_id); if (plan.group_id === groupId) { endPlanDrag(); return }; saving.value = true; try { await moveProjectPlanToGroup(selectedProject.value.id, plan.id, groupId); await refreshSelectedProject(); ElMessage.success('主计划及其子计划已完成分组') } catch (error) { ElMessage.error(apiErrorMessage(error, '计划分组移动失败')) } finally { saving.value = false; endPlanDrag() } }
function planRowClassName({ row }: { row: ProjectPlanRow }) {
  const palette = planPaletteKey(row)
  if (row.node_type === 'group') return `project-plan-row--group project-plan-row--group-${row.group_id == null ? 'ungrouped' : row.group_id} project-plan-palette--${palette}`
  return `project-plan-row--${row.parent_id ? 'child' : 'main'} project-plan-row--plan-${row.id} project-plan-palette--${palette}`
}
function planPaletteKey(row: ProjectPlanRow): ProjectPlanGroupColorToken {
  if (row.node_type === 'group') return row.group_color_key || 'brand'
  return planGroups.value.find(group => Number(group.id) === Number(row.group_id))?.color_key || 'brand'
}
function findPlanGroupRow(element: HTMLElement) {
  const className = [...element.classList].find(value => value.startsWith('project-plan-row--group-'))
  if (!className) return null
  const groupKey = className.replace('project-plan-row--group-', '')
  return planTree.value.find(row => row.node_type === 'group' && (groupKey === 'ungrouped' ? row.group_id == null : Number(row.group_id) === Number(groupKey))) || null
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
    if (isMainPlan && canUpdatePlan.value && isOwner.value && mainPlanClass) {
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
async function removePlan(row: ProjectPlan) { if (!selectedProject.value) return; try { const message = row.parent_id ? '确认删除该计划吗？' : '删除主计划后，其下全部子计划也会被删除，是否继续？'; await ElMessageBox.confirm(message, '删除确认', { type: 'warning' }); await deleteProjectPlan(selectedProject.value.id, row.id); await refreshSelectedProject(); ElMessage.success('计划已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '计划删除失败')) } }
async function loadUsers() { if (userOptionsLoaded.value) return; userOptionsLoading.value = true; try { const options = (await getProjectUserOptions()).data.data; options.forEach(item => addUserOption(item.id, item.display_name, item.username)); userOptionsLoaded.value = true } catch (error) { ElMessage.error(apiErrorMessage(error, '用户选项加载失败')) } finally { userOptionsLoading.value = false } }
function onUserOptionsVisible(visible: boolean) { if (visible) void loadUsers() }
async function openCreateMember() { memberEditingId.value = null; memberForm.user_id = null; memberForm.role_ids = []; memberForm.status = 1; await loadUsers(); memberDialog.value = true }
async function openEditMember(row: ProjectMember) { await loadUsers(); addUserOption(row.user_id, row.display_name); memberEditingId.value = row.id; memberForm.user_id = row.user_id; memberForm.role_ids = row.roles.map(role => role.id); memberForm.status = row.status; memberDialog.value = true }
async function saveMember() { if (!selectedProject.value || (!memberEditingId.value && !memberForm.user_id)) { ElMessage.warning('请选择成员'); return }; saving.value = true; try { const payload = { user_id: memberForm.user_id, role_ids: memberForm.role_ids, status: memberForm.status }; if (memberEditingId.value) await updateProjectMember(selectedProject.value.id, memberEditingId.value, payload); else await createProjectMember(selectedProject.value.id, payload); memberDialog.value = false; await refreshSelectedProject(); ElMessage.success('成员已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '成员保存失败')) } finally { saving.value = false } }
async function removeMember(row: ProjectMember) { if (!selectedProject.value) return; try { await ElMessageBox.confirm('确认移除该项目成员吗？', '移除确认', { type: 'warning' }); await deleteProjectMember(selectedProject.value.id, row.id); await refreshSelectedProject(); ElMessage.success('成员已移除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '成员移除失败')) } }
function openCreateRole() { roleEditingId.value = null; resetRoleForm(); roleDialog.value = true }
function openEditRole(row: ProjectRole) { roleEditingId.value = row.id; Object.assign(roleForm, { role_code: row.role_code, role_name: row.role_name, description: row.description || '' }); roleDialog.value = true }
async function saveRole() { if (!selectedProject.value || !String(roleForm.role_code || '').trim() || !String(roleForm.role_name || '').trim()) { ElMessage.warning('请填写角色编码和角色名称'); return }; saving.value = true; try { if (roleEditingId.value) await updateProjectRole(selectedProject.value.id, roleEditingId.value, roleForm); else await createProjectRole(selectedProject.value.id, roleForm); roleDialog.value = false; await refreshSelectedProject(); ElMessage.success('项目角色已保存') } catch (error) { ElMessage.error(apiErrorMessage(error, '项目角色保存失败')) } finally { saving.value = false } }
async function removeRole(row: ProjectRole) { if (!selectedProject.value) return; try { await ElMessageBox.confirm('确认删除该项目角色吗？', '删除确认', { type: 'warning' }); await deleteProjectRole(selectedProject.value.id, row.id); await refreshSelectedProject(); ElMessage.success('项目角色已删除') } catch (error) { const action = (error as { action?: string }).action; if (action !== 'cancel' && action !== 'close') ElMessage.error(apiErrorMessage(error, '项目角色删除失败')) } }
function projectStatusTone(status: string): 'primary' | 'success' | 'warning' | 'danger' { return status === 'COMPLETED' ? 'success' : status === 'SUSPENDED' ? 'danger' : status === 'RUNNING' ? 'primary' : 'warning' }
function planStatusTone(status: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' { return status === 'COMPLETED' ? 'success' : status === 'BLOCKED' ? 'danger' : status === 'IN_PROGRESS' ? 'primary' : 'info' }
watch([selectedProject, activeTab, plans], () => { void refreshOverviewCharts(); void nextTick(applyPlanRowDragability) }, { deep: true })
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
onMounted(async () => { await loadOptions(); if (route.params.projectId) await syncProjectFromRoute(); else await loadWorkbench(); window.addEventListener('resize', resizeCharts) })
onBeforeUnmount(() => { clearTabLoadingTimer(); window.removeEventListener('resize', resizeCharts); disposeCharts() })
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
      <header class="project-page__heading"><div><span class="panel-kicker">项目工作台 / PROJECT SPACE</span><h1>项目管理</h1><p>从项目卡片进入计划、成员和项目角色。</p></div><el-button v-if="canCreateProject" type="primary" @click="openCreateProject"><el-icon><Plus /></el-icon>新建项目</el-button></header>
      <div class="project-card-grid">
        <el-card v-for="project in projects" :key="project.id" shadow="never" class="project-card" @click="openProject(project)">
          <div class="project-card__top"><span class="project-card__icon"><el-icon><Briefcase /></el-icon></span><UiStatusTag :value="project.status" :labels="projectStatusLabels" :tone="projectStatusTone(project.status)" /></div>
          <div class="project-card__identity"><strong>{{ project.project_name }}</strong><span>{{ project.project_code }}<i v-if="project.phase_name"> · {{ project.phase_name }}</i></span></div>
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
            <div class="project-overview__identity"><span class="project-overview__icon"><el-icon><Briefcase /></el-icon></span><div><span class="panel-kicker">{{ selectedProject.project_code }}</span><h2>{{ selectedProject.project_name }}</h2><p>{{ selectedProject.phase_name || '未设置项目阶段' }} · {{ projectStatusLabels[selectedProject.status] }}</p></div></div>
            <div class="project-overview__hero-progress"><span>整体计划进度</span><strong>{{ Math.round(Number(selectedProject.plan_progress || 0)) }}%</strong><el-progress :percentage="Math.round(Number(selectedProject.plan_progress || 0))" :show-text="false" :stroke-width="8" /></div>
            <div class="project-overview__facts"><div><span>项目负责人</span><strong>{{ selectedProject.owner_name || '-' }}</strong></div><div><span>计划周期</span><strong>{{ formatDateOnly(selectedProject.planned_start_date) || '-' }} 至 {{ formatDateOnly(selectedProject.planned_end_date) || '-' }}</strong></div><div><span>项目成员</span><strong>{{ selectedProject.member_count }} 人</strong></div><div><span>项目计划</span><strong>{{ selectedProject.plan_count }} 项</strong></div></div>
          </section>
          <section class="project-overview__kpis"><div class="project-overview__kpi"><span>计划总数</span><strong>{{ planStats.total }}</strong><small>当前项目全部计划</small></div><div class="project-overview__kpi is-success"><span>已完成</span><strong>{{ planStats.completed }}</strong><small>完成率 {{ planStats.total ? Math.round(planStats.completed / planStats.total * 100) : 0 }}%</small></div><div class="project-overview__kpi is-brand"><span>进行中</span><strong>{{ planStats.inProgress }}</strong><small>正在执行的计划</small></div><div class="project-overview__kpi is-muted"><span>未开始</span><strong>{{ planStats.notStarted }}</strong><small>等待启动的计划</small></div><div class="project-overview__kpi is-danger"><span>已阻塞</span><strong>{{ planStats.blocked }}</strong><small>需要关注的计划</small></div><div class="project-overview__kpi is-accent"><span>平均进度</span><strong>{{ planStats.averageProgress }}%</strong><small>按计划平均计算</small></div></section>
          <section class="project-overview__charts"><el-card shadow="never" class="project-overview__chart"><div class="project-section-heading"><div><span class="panel-kicker">计划状态</span><h3>状态分布</h3></div></div><div v-if="planStats.total" ref="statusChartRef" class="project-chart project-chart--donut" /><el-empty v-else description="暂无计划数据" :image-size="54" /></el-card><el-card shadow="never" class="project-overview__chart"><div class="project-section-heading"><div><span class="panel-kicker">执行进度</span><h3>计划进度排行</h3></div></div><div v-if="planStats.progressData.length" ref="progressChartRef" class="project-chart" /><el-empty v-else description="暂无计划数据" :image-size="54" /></el-card><el-card shadow="never" class="project-overview__chart project-overview__chart--wide"><div class="project-section-heading"><div><span class="panel-kicker">计划排期</span><h3>计划时间分布</h3></div><span class="muted">最多展示 8 项</span></div><div v-if="calendarPlans.length" ref="scheduleChartRef" class="project-chart" /><el-empty v-else description="暂无排期数据" :image-size="54" /></el-card></section>
          <section class="project-overview__bottom"><el-card shadow="never" class="project-overview__description"><div class="project-section-heading"><div><span class="panel-kicker">项目信息</span><h3>基本信息</h3></div><el-button link type="primary" @click="openEditProject">编辑项目</el-button></div><div class="project-overview__info-grid"><div><span>项目负责人</span><strong>{{ selectedProject.owner_name || '-' }}</strong></div><div><span>项目阶段</span><strong>{{ selectedProject.phase_name || '未设置' }}</strong></div><div><span>项目周期</span><strong>{{ formatDateOnly(selectedProject.planned_start_date) || '-' }} 至 {{ formatDateOnly(selectedProject.planned_end_date) || '-' }}</strong></div><div><span>项目成员</span><strong>{{ selectedProject.member_count }} 人</strong></div><div><span>项目计划</span><strong>{{ selectedProject.plan_count }} 项</strong></div><div><span>当前状态</span><strong>{{ projectStatusLabels[selectedProject.status] }}</strong></div></div><div class="project-overview__description-copy"><span class="project-overview__description-label">项目说明</span><p>{{ selectedProject.description || '暂无项目描述，点击编辑项目补充说明。' }}</p></div></el-card><el-card shadow="never" class="project-overview__calendar"><div class="project-section-heading"><div><span class="panel-kicker">项目排期</span><h3>项目日历</h3></div><span class="muted">按计划起止日期展示</span></div><el-calendar v-model="calendarDate"><template #date-cell="{ data }"><div class="project-calendar__cell"><span class="project-calendar__date">{{ data.day.slice(-2) }}</span><button v-for="plan in plansForDay(data.day).slice(0, 3)" :key="plan.id" type="button" class="project-calendar__plan" @click.stop="openCalendarPlan"><strong>{{ plan.plan_code || plan.plan_name }}</strong><small>{{ Math.round(Number(plan.progress || 0)) }}% · {{ planStatusLabels[plan.status] }}</small></button><small v-if="plansForDay(data.day).length > 3" class="project-calendar__more">还有 {{ plansForDay(data.day).length - 3 }} 项</small></div></template></el-calendar></el-card></section>
        </div></el-tab-pane>
      <el-tab-pane name="plans" label="项目计划"><div class="project-tab-panel"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT PLAN</span><h2>项目计划</h2><p class="project-plan-group-hint"><el-icon><Folder /></el-icon>可以长按主计划并拖动到目标分组</p></div><div class="project-plan-toolbar"><el-button v-if="canCreatePlan && isOwner" @click="openCreatePlanGroup"><el-icon><Folder /></el-icon>新建分组</el-button><el-button v-if="canCreatePlan && isOwner" type="primary" @click="openCreatePlan"><el-icon><Plus /></el-icon>新增主计划</el-button></div></div><UiDataTable class="project-plans-table" :data="planTree" row-key="id" :row-class-name="planRowClassName" :tree-props="{ children: 'children' }" default-expand-all border empty-text="暂无项目计划"><el-table-column prop="plan_code" label="计划编号" width="230"><template #default="scope"><span v-if="scope.row.node_type === 'group'" class="project-plan-group-label"><el-icon><Folder /></el-icon>{{ scope.row.group_name }}</span><span v-else>{{ scope.row.plan_code || '未生成' }}</span></template></el-table-column><el-table-column label="计划名称" min-width="220"><template #default="scope"><span v-if="scope.row.node_type === 'group'" class="project-plan-group-summary"><small>{{ scope.row.group_count || 0 }} 项计划，拖动主计划到此行即可归组</small></span><span v-else>{{ scope.row.plan_name }}</span></template></el-table-column><el-table-column prop="phase_name" label="计划阶段" width="140"><template #default="scope">{{ scope.row.node_type === 'group' ? '-' : scope.row.phase_name || '-' }}</template></el-table-column><el-table-column prop="owner_name" label="负责人" width="140"><template #default="scope">{{ scope.row.node_type === 'group' ? '-' : scope.row.owner_name || '-' }}</template></el-table-column><el-table-column label="牵头方" min-width="150"><template #default="scope">{{ scope.row.node_type === 'group' ? '-' : scope.row.lead_org_name || '-' }}</template></el-table-column><el-table-column label="配合方" min-width="180"><template #default="scope">{{ scope.row.node_type === 'group' ? '-' : scope.row.cooperating_org_names?.join('、') || '-' }}</template></el-table-column><el-table-column label="时间范围" min-width="190"><template #default="scope">{{ scope.row.node_type === 'group' ? '-' : `${formatDateOnly(scope.row.planned_start_date) || '-'} 至 ${formatDateOnly(scope.row.planned_end_date) || '-'}` }}</template></el-table-column><el-table-column label="进度" width="170"><template #default="scope"><el-progress v-if="scope.row.node_type !== 'group'" :percentage="Number(scope.row.progress || 0)" :stroke-width="7" /><span v-else class="muted">-</span></template></el-table-column><el-table-column label="状态" width="110"><template #default="scope"><UiStatusTag v-if="scope.row.node_type !== 'group'" :value="scope.row.status" :labels="planStatusLabels" :tone="planStatusTone(scope.row.status)" /><span v-else class="muted">-</span></template></el-table-column><el-table-column v-if="canCreatePlan && isOwner || canUpdatePlan && isOwner || canDeletePlan && isOwner" label="操作" width="260" fixed="right"><template #default="scope"><template v-if="scope.row.node_type === 'group'"><el-button v-if="scope.row.group_id && canUpdatePlan && isOwner" link type="primary" @click="openEditPlanGroup(scope.row)"><el-icon><Edit /></el-icon>编辑分组</el-button><el-button v-if="scope.row.group_id && canDeletePlan && isOwner" link type="danger" @click="removePlanGroup(scope.row)"><el-icon><Delete /></el-icon>删除分组</el-button></template><template v-else><el-button v-if="canCreatePlan && isOwner" link type="success" @click="openCreateChildPlan(scope.row)"><el-icon><Plus /></el-icon>新增子计划</el-button><el-button v-if="canUpdatePlan && isOwner" link type="primary" @click="openEditPlan(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button v-if="canDeletePlan && isOwner" link type="danger" @click="removePlan(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></template></template></el-table-column></UiDataTable></div></el-tab-pane>
      <el-tab-pane name="members" label="项目成员"><div class="project-tab-panel"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT MEMBERS</span><h2>项目成员</h2></div><el-button v-if="canCreateMember && isOwner" type="primary" @click="openCreateMember"><el-icon><Plus /></el-icon>添加成员</el-button></div><UiDataTable :data="members" row-key="id" border empty-text="暂无项目成员"><el-table-column label="成员" min-width="220"><template #default="scope"><UiUserIdentity :user="{ id: scope.row.user_id, username: scope.row.username, displayName: scope.row.display_name, avatarUrl: scope.row.avatar_url }" :show-profile="false" /></template></el-table-column><el-table-column label="项目角色" min-width="220"><template #default="scope"><el-tag v-for="role in scope.row.roles" :key="role.id" size="small" effect="plain" class="project-role-tag">{{ role.role_name }}</el-tag><span v-if="!scope.row.roles.length" class="muted">未分配角色</span></template></el-table-column><el-table-column label="状态" width="100"><template #default="scope"><UiStatusTag :value="scope.row.status" :labels="{ '0': '停用', '1': '有效' }" /></template></el-table-column><el-table-column v-if="canUpdateMember && isOwner || canDeleteMember && isOwner" label="操作" width="150" fixed="right"><template #default="scope"><el-button v-if="canUpdateMember && isOwner" link type="primary" @click="openEditMember(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button v-if="canDeleteMember && isOwner" link type="danger" @click="removeMember(scope.row)"><el-icon><Delete /></el-icon>移除</el-button></template></el-table-column></UiDataTable></div></el-tab-pane>
      <el-tab-pane name="roles" label="项目角色"><div class="project-tab-panel"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT ROLES</span><h2>项目角色</h2></div><el-button v-if="canCreateRole && isOwner" type="primary" @click="openCreateRole"><el-icon><Plus /></el-icon>新增角色</el-button></div><UiDataTable :data="roles" row-key="id" border empty-text="暂无项目角色"><el-table-column prop="role_name" label="角色名称" min-width="180" /><el-table-column prop="role_code" label="角色编码" min-width="180" /><el-table-column prop="description" label="角色说明" min-width="240" show-overflow-tooltip /><el-table-column prop="member_count" label="成员数" width="100" /><el-table-column v-if="canUpdateRole && isOwner || canDeleteRole && isOwner" label="操作" width="150" fixed="right"><template #default="scope"><el-button v-if="canUpdateRole && isOwner" link type="primary" @click="openEditRole(scope.row)"><el-icon><Edit /></el-icon>编辑</el-button><el-button v-if="canDeleteRole && isOwner" link type="danger" @click="removeRole(scope.row)"><el-icon><Delete /></el-icon>删除</el-button></template></el-table-column></UiDataTable></div></el-tab-pane>
        <el-tab-pane name="settings" label="项目设置"><div class="project-tab-panel project-settings"><div class="project-section-heading"><div><span class="panel-kicker">PROJECT SETTINGS</span><h2>项目设置</h2></div></div><el-form label-position="top" class="project-settings__form"><el-form-item label="主计划编号规则"><el-input v-model="settingsForm.plan_number_rule" maxlength="128" show-word-limit /></el-form-item><p class="project-settings__hint">主计划支持 {PROJECT_CODE}、{SEQ}、{SEQ:3}、{YYYY}、{MM}、{DD}。例如：{PROJECT_CODE}-P{SEQ:3}，项目编号为 RDC 时会生成 RDC-P001。</p><el-form-item label="子计划编号规则"><el-input v-model="settingsForm.child_plan_number_rule" maxlength="128" show-word-limit /></el-form-item><p class="project-settings__hint">子计划支持 {PARENT_CODE}、{SEQ}、{SEQ:3}、{YYYY}、{MM}、{DD}。例如：{PARENT_CODE}-S{SEQ:3}，主计划 RDC-P001 下会生成 RDC-P001-S001。</p><el-button v-if="canUpdateProject && isOwner" type="primary" :loading="saving" @click="saveSettings">保存设置</el-button></el-form></div></el-tab-pane>
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

    <el-dialog v-model="projectDialog" :title="projectEditingId ? '编辑项目' : '新建项目'" width="600px" destroy-on-close><el-form label-position="top"><el-row :gutter="16"><el-col :span="12"><el-form-item label="项目编号" required><el-input v-model="projectForm.project_code" /></el-form-item></el-col><el-col :span="12"><el-form-item label="项目名称" required><el-input v-model="projectForm.project_name" /></el-form-item></el-col></el-row><el-form-item label="项目描述"><el-input v-model="projectForm.description" type="textarea" :rows="3" /></el-form-item><el-row :gutter="16"><el-col :span="8"><el-form-item label="项目状态"><el-select v-model="projectForm.status" style="width:100%"><el-option v-for="(label, value) in projectStatusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-col><el-col :span="8"><el-form-item label="项目阶段"><el-select v-model="projectForm.phase" clearable style="width:100%"><el-option v-for="item in projectOptions.project_phases" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="8"><el-form-item label="负责人"><el-select v-model="projectForm.owner_id" filterable :loading="userOptionsLoading" style="width:100%" @visible-change="onUserOptionsVisible"><el-option v-for="item in userOptions" :key="item.id" :label="userOptionLabel(item)" :value="item.id" /></el-select></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="8"><el-form-item label="计划开始"><el-date-picker v-model="projectForm.planned_start_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="计划结束"><el-date-picker v-model="projectForm.planned_end_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="8"><el-form-item label="实际结束"><el-date-picker v-model="projectForm.actual_end_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="projectDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveProject">保存</el-button></template></el-dialog>
    <el-dialog v-model="planDialog" :title="planEditingId ? '编辑项目计划' : (planParentName ? '新增子计划' : '新增主计划')" width="600px" destroy-on-close><el-form label-position="top"><el-form-item label="计划名称" required><el-input v-model="planForm.plan_name" /></el-form-item><el-form-item label="计划层级"><el-input :model-value="planParentName ? `子计划（${planParentName}）` : '主计划'" disabled /></el-form-item><el-form-item label="计划描述"><el-input v-model="planForm.description" type="textarea" :rows="2" /></el-form-item><el-row :gutter="16"><el-col :span="12"><el-form-item label="负责人"><el-select v-model="planForm.owner_id" clearable filterable :loading="userOptionsLoading" style="width:100%" @visible-change="onUserOptionsVisible"><el-option v-for="item in userOptions" :key="item.id" :label="userOptionLabel(item)" :value="item.id" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="计划阶段"><el-select v-model="planForm.phase" clearable style="width:100%"><el-option v-for="item in projectOptions.plan_phases" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col></el-row><el-row :gutter="16"><el-col :span="12"><el-form-item label="牵头方"><UiTreeSelect :model-value="planForm.lead_org_id === null ? null : Number(planForm.lead_org_id || 0)" :options="organizationTreeOptions" placeholder="请选择牵头组织" @update:model-value="planForm.lead_org_id = $event" /></el-form-item></el-col><el-col :span="12"><el-form-item label="状态"><el-select v-model="planForm.status" style="width:100%"><el-option v-for="(label, value) in planStatusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item></el-col></el-row><el-form-item label="配合方"><el-tree-select v-model="planForm.cooperating_org_ids" :data="organizationTreeOptions" multiple show-checkbox check-strictly clearable filterable node-key="value" placeholder="请选择配合组织" style="width:100%" /></el-form-item><el-row :gutter="16"><el-col :span="12"><el-form-item label="计划开始"><el-date-picker v-model="planForm.planned_start_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item label="计划结束"><el-date-picker v-model="planForm.planned_end_date" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col></el-row><el-form-item label="完成进度"><el-slider v-model="planForm.progress" :max="100" /></el-form-item></el-form><template #footer><el-button @click="planDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePlan">保存</el-button></template></el-dialog>
    <el-dialog v-model="planGroupDialog" :title="planGroupEditingId ? '编辑计划分组' : '新建计划分组'" width="520px" destroy-on-close><el-form label-position="top"><el-form-item label="分组名称" required><el-input v-model="planGroupForm.group_name" maxlength="128" show-word-limit /></el-form-item><el-form-item label="分组颜色"><div class="project-plan-palette-picker"><button v-for="palette in planGroupColorOptions" :key="palette.key" type="button" class="project-plan-palette-option" :class="{ 'is-selected': planGroupForm.color_key === palette.key }" :style="{ '--palette-color': `var(${palette.colorVar})`, '--palette-accent': `var(${palette.accentVar})` }" :aria-label="palette.key" @click="planGroupForm.color_key = palette.key"><span class="project-plan-palette-option__swatch" /></button></div></el-form-item><el-form-item label="分组说明"><el-input v-model="planGroupForm.description" type="textarea" :rows="3" maxlength="500" /></el-form-item><el-form-item label="排序号"><el-input-number v-model="planGroupForm.sort_no" :min="0" :max="9999" controls-position="right" /></el-form-item></el-form><template #footer><el-button @click="planGroupDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePlanGroup">保存</el-button></template></el-dialog>
    <el-dialog v-model="memberDialog" :title="memberEditingId ? '编辑项目成员' : '添加项目成员'" width="520px" destroy-on-close><el-form label-position="top"><el-form-item label="成员" required><el-select v-model="memberForm.user_id" filterable :disabled="Boolean(memberEditingId)" :loading="userOptionsLoading" style="width:100%"><el-option v-for="item in userOptions" :key="item.id" :label="userOptionLabel(item)" :value="item.id" /></el-select></el-form-item><el-form-item label="项目角色"><el-select v-model="memberForm.role_ids" multiple collapse-tags filterable style="width:100%"><el-option v-for="role in roles" :key="role.id" :label="role.role_name" :value="role.id" /></el-select></el-form-item><el-form-item label="成员状态"><el-switch v-model="memberForm.status" :active-value="1" :inactive-value="0" active-text="有效" inactive-text="停用" /></el-form-item></el-form><template #footer><el-button @click="memberDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveMember">保存</el-button></template></el-dialog>
    <el-dialog v-model="roleDialog" :title="roleEditingId ? '编辑项目角色' : '新增项目角色'" width="520px" destroy-on-close><el-form label-position="top"><el-form-item label="角色编码" required><el-input v-model="roleForm.role_code" /></el-form-item><el-form-item label="角色名称" required><el-input v-model="roleForm.role_name" /></el-form-item><el-form-item label="角色说明"><el-input v-model="roleForm.description" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="roleDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRole">保存</el-button></template></el-dialog>
  </section>
</template>
