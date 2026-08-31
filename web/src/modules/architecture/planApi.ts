import http from '../../api/http'
import type { ApiResponse } from '../../types/auth'

export type TemplateStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE'
export type PlanDimension = 'NONE' | 'PHYSICAL_SUBSYSTEM' | 'DEPLOYMENT_UNIT'
export type PlanStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type TaskStatus = 'NOT_STARTED' | 'WAITING_PRECEDING' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED'
export type CheckItemStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED'
export type TargetType = 'PHYSICAL_SUBSYSTEM' | 'DEPLOYMENT_UNIT'
export type WorkOrderType = 'RESOURCE_REQUEST' | 'NETWORK_CLB' | 'NETWORK_DNS' | 'NETWORK_CERT' | 'CRYPTO_POOL'

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PlanTemplateView {
  id: number
  name: string
  description: string | null
  status: TemplateStatus
  latestVersionNo: number
  rowVersion: number
}

export interface CheckItemDraftView {
  name: string
  sortNo: number
  guide?: string | null
}

export interface TaskTemplateView {
  id: number
  name: string
  dimension: PlanDimension
  checkItems: CheckItemDraftView[]
  status: TemplateStatus
  latestVersionNo: number
  rowVersion: number
}

export interface StageView {
  id: number
  name: string
  sortNo: number
  startOffsetDays: number | null
  durationDays: number | null
  tasks: TaskTemplateView[]
}

export interface TemplateVersionView {
  id: number
  versionNo: number
  contentJson: string
  note: string | null
  publishedBy: number
  publishedAt: string
}

export interface PlanTemplateDetail {
  template: PlanTemplateView
  stages: StageView[]
  stageDependencies: [number, number][]
  taskDependencies: [number, number][]
  versions: TemplateVersionView[]
}

export interface PlanView {
  id: number
  planNo: string
  name: string
  environmentId: number
  status: PlanStatus
  templateId: number
  templateVersionNo: number
  planOwnerUserId: number
  plannedStart: string | null
  plannedEnd: string | null
  actualStart: string | null
  actualEnd: string | null
  cancelled: boolean
  cancelReason: string | null
  rowVersion: number
}

export interface PlanRowView {
  id: number
  planNo: string
  name: string
  environmentCode: string
  environmentName: string
  status: PlanStatus
  progress: number | null
  taskCount: number
  hasBlocked: boolean
  hasOverdue: boolean
  hasWaived: boolean
  plannedEnd: string | null
  planOwnerUserId: number
}

export interface TargetView {
  id: number
  targetType: TargetType
  targetId: number
  targetNo: string | null
  targetName: string
  removed: boolean
  snapshotName: string | null
  currentName: string | null
  hasDiff: boolean
}

export interface CheckItemView {
  id: number
  name: string
  guide: string | null
  status: CheckItemStatus
  remark: string | null
  completedBy: number | null
  completedAt: string | null
  cancelled: boolean
  cancelReason: string | null
  cancelledBy: number | null
  cancelledAt: string | null
}

export interface DependencyView {
  id: number
  taskId: number
  predecessorId: number
  removed: boolean
}

export interface BlockView {
  id: number
  taskId: number
  description: string
  impact: string | null
  ownerUserId: number
  expectedResolveAt: string | null
  resolved: boolean
  resolvedNote: string | null
  resolvedBy: number | null
  resolvedAt: string | null
  createdBy: number
}

export interface WorkOrderLinkView {
  id: number
  taskId: number
  workOrderType: WorkOrderType
  workOrderId: number
  source: 'CREATED_FROM_TASK' | 'ATTACHED_LATER'
}

export interface EventView {
  id: number
  objectType: string
  objectId: number
  eventType: string
  occurredAt: string
  operatorUserId: number
  reason: string | null
  correctOfEventId: number | null
}

export interface TaskDetailView {
  id: number
  stageId: number
  taskNo: number
  name: string
  targetName: string | null
  targetId: number | null
  targetType: string | null
  status: TaskStatus
  progress: number | null
  waivedAll: boolean
  overdue: boolean
  hasBlocked: boolean
  hasOpenWorkOrder: boolean
  ownerUserId: number
  plannedStart: string | null
  plannedEnd: string | null
  actualStart: string | null
  actualEnd: string | null
  cancelled: boolean
  cancelReason: string | null
  participantUserIds: number[]
  dependencies: DependencyView[]
  blocks: BlockView[]
  workOrders: WorkOrderLinkView[]
  checkItems: CheckItemView[]
  events: EventView[]
}

export interface StageDetailView {
  id: number
  stageNo: number
  name: string
  status: PlanStatus
  cancelled: boolean
  cancelReason: string | null
  ownerUserId: number
  plannedStart: string | null
  plannedEnd: string | null
  actualStart: string | null
  actualEnd: string | null
  progress: number | null
  hasWaived: boolean
  tasks: TaskDetailView[]
}

export interface PlanDetailView {
  plan: PlanView
  environmentCode: string
  environmentName: string
  targets: TargetView[]
  stages: StageDetailView[]
  progress: number | null
  hasBlocked: boolean
  hasOverdue: boolean
  hasWaived: boolean
  uncompletable: boolean
  pendingSuggestionCount: number
  stageDependencies: [number, number][]
  events: EventView[]
}

export interface DashboardView {
  planId: number
  planNo: string
  name: string
  environmentName: string
  status: PlanStatus
  progress: number | null
  hasBlocked: boolean
  hasOverdue: boolean
  hasWaived: boolean
  stages: Array<{
    id: number
    stageNo: number
    name: string
    status: PlanStatus
    progress: number | null
    hasWaived: boolean
    tasks: Array<{
      id: number
      name: string
      status: TaskStatus
      progress: number | null
      waivedAll: boolean
      overdue: boolean
      hasBlocked: boolean
      targetName: string | null
    }>
  }>
}

export interface TimelineView {
  planId: number
  planNo: string
  name: string
  rows: Array<{
    id: number
    name: string
    type: 'PLAN' | 'STAGE' | 'TASK'
    parentId: number
    status: string
    plannedStart: string | null
    plannedEnd: string | null
    actualStart: string | null
    actualEnd: string | null
    progress: number | null
    overdue: boolean
    targetName: string | null
  }>
}

export interface SuggestionView {
  id: number
  checkItemId: number
  reason: string
  submitterUserId: number
  status: string
  handledByUserId: number | null
  handledAt: string | null
  handlerNote: string | null
}

export interface ReportView {
  detail: PlanDetailView
  dashboard: DashboardView
  timeline: TimelineView
}

function compact(value: Record<string, unknown>) {
  const result: Record<string, unknown> = {}
  for (const [key, item] of Object.entries(value)) {
    if (item !== undefined && item !== null && item !== '') {
      result[key] = item
    }
  }
  return result
}

// ---------- 搭建计划模板 ----------

export async function listPlanTemplates(query: { keyword?: string; status?: TemplateStatus; page?: number; size?: number }) {
  return (await http.get<ApiResponse<PageResult<PlanTemplateView>>>('/architecture/plan-templates', { params: compact(query) })).data.data
}

export async function createPlanTemplate(payload: { name: string; description?: string | null }) {
  return (await http.post<ApiResponse<PlanTemplateView>>('/architecture/plan-templates', payload)).data.data
}

export async function updatePlanTemplate(id: number, payload: { name: string; description?: string | null; rowVersion: number }) {
  return (await http.put<ApiResponse<PlanTemplateView>>(`/architecture/plan-templates/${id}`, payload)).data.data
}

export async function getPlanTemplate(id: number) {
  return (await http.get<ApiResponse<PlanTemplateDetail>>(`/architecture/plan-templates/${id}`)).data.data
}

export async function changePlanTemplateStatus(id: number, status: TemplateStatus) {
  return (await http.post<ApiResponse<PlanTemplateView>>(`/architecture/plan-templates/${id}/status`, { status })).data.data
}

export async function addPlanTemplateStage(templateId: number, payload: { name: string; sortNo?: number; startOffsetDays?: number | null; durationDays?: number | null }) {
  return (await http.post<ApiResponse<StageView>>(`/architecture/plan-templates/${templateId}/stages`, payload)).data.data
}

export async function updatePlanTemplateStage(stageId: number, payload: { name: string; sortNo?: number; startOffsetDays?: number | null; durationDays?: number | null }) {
  return (await http.put<ApiResponse<StageView>>(`/architecture/plan-templates/stages/${stageId}`, payload)).data.data
}

export async function deletePlanTemplateStage(stageId: number) {
  return (await http.delete<ApiResponse<void>>(`/architecture/plan-templates/stages/${stageId}`)).data
}

export async function addTaskTemplate(templateId: number, payload: { stageId: number; name: string; dimension: PlanDimension; checkItems: CheckItemDraftView[] }) {
  return (await http.post<ApiResponse<TaskTemplateView>>(`/architecture/plan-templates/${templateId}/task-templates`, payload)).data.data
}

export async function updateTaskTemplate(taskId: number, payload: { name: string; dimension: PlanDimension; checkItems: CheckItemDraftView[]; rowVersion?: number | null }) {
  return (await http.put<ApiResponse<TaskTemplateView>>(`/architecture/task-templates/${taskId}`, payload)).data.data
}

export async function deleteTaskTemplate(taskId: number) {
  return (await http.delete<ApiResponse<void>>(`/architecture/task-templates/${taskId}`)).data
}

export async function publishPlanTemplate(templateId: number, note?: string | null) {
  return (await http.post<ApiResponse<TemplateVersionView>>(`/architecture/plan-templates/${templateId}/publish`, { note })).data.data
}

export async function setPlanTemplateStageDependencies(stageId: number, predecessorStageIds: number[]) {
  return (await http.post<ApiResponse<[number, number][]>>(`/architecture/plan-templates/stages/${stageId}/dependencies`, { predecessorStageIds })).data.data
}

export async function setTaskTemplateDependencies(taskTemplateId: number, predecessorTaskTemplateIds: number[]) {
  return (await http.post<ApiResponse<[number, number][]>>(`/architecture/task-templates/${taskTemplateId}/dependencies`, { predecessorTaskTemplateIds })).data.data
}

export async function listPlanTemplateVersions(templateId: number) {
  return (await http.get<ApiResponse<TemplateVersionView[]>>(`/architecture/plan-templates/${templateId}/versions`)).data.data
}

// ---------- 搭建计划 ----------

export interface PlanUserOption {
  id: number
  displayName: string
  username: string
  phone: string | null
}

export async function loadPlanUserOptions(keyword = '', size = 50) {
  return (await http.get<ApiResponse<PageResult<PlanUserOption>>>('/architecture/plan-options/users', {
    params: compact({ keyword: keyword || undefined, page: 1, size })
  })).data.data
}

export async function listPlans(query: {
  environmentId?: number | null
  status?: PlanStatus | ''
  ownerUserId?: number | null
  blocked?: boolean
  overdue?: boolean
  waived?: boolean
  keyword?: string
  targetType?: TargetType | ''
  targetId?: number | null
  page?: number
  size?: number
}) {
  return (await http.get<ApiResponse<PageResult<PlanRowView>>>('/architecture/plans', { params: compact(query) })).data.data
}

export async function getPlan(id: number) {
  return (await http.get<ApiResponse<PlanDetailView>>(`/architecture/plans/${id}`)).data.data
}

export async function getPlanDashboard(id: number) {
  return (await http.get<ApiResponse<DashboardView>>(`/architecture/plans/${id}/dashboard`)).data.data
}

export async function getPlanTimeline(id: number) {
  return (await http.get<ApiResponse<TimelineView>>(`/architecture/plans/${id}/timeline`)).data.data
}

export async function getPlanReport(id: number) {
  return (await http.get<ApiResponse<ReportView>>(`/architecture/plans/${id}/report`)).data.data
}

export async function createPlan(payload: {
  environmentId: number
  templateId: number
  name?: string | null
  planOwnerUserId: number
  physicalSubsystemIds?: number[]
  deploymentUnitIds?: number[]
  participantUserIds?: number[]
  plannedStart?: string | null
  plannedEnd?: string | null
}) {
  return (await http.post<ApiResponse<PlanView>>('/architecture/plans', payload)).data.data
}

export async function cancelPlan(id: number, reason: string) {
  return (await http.post<ApiResponse<PlanView>>(`/architecture/plans/${id}/cancel`, { reason })).data.data
}

export async function restorePlan(id: number, reason: string) {
  return (await http.post<ApiResponse<PlanView>>(`/architecture/plans/${id}/restore`, { reason })).data.data
}

export async function addPlanTargets(id: number, payload: { physicalSubsystemIds?: number[]; deploymentUnitIds?: number[]; reason: string }) {
  return (await http.post<ApiResponse<PlanView>>(`/architecture/plans/${id}/targets`, payload)).data.data
}

export async function removePlanTarget(id: number, targetId: number, reason: string) {
  return (await http.post<ApiResponse<PlanView>>(`/architecture/plans/${id}/targets/${targetId}/remove`, { reason })).data.data
}

export async function addPlanStage(id: number, payload: { name: string; ownerUserId: number; plannedStart?: string | null; plannedEnd?: string | null }) {
  return (await http.post<ApiResponse<unknown>>(`/architecture/plans/${id}/stages`, payload)).data.data
}

export async function addPlanTask(id: number, payload: {
  stageId: number
  name: string
  targetId?: number | null
  ownerUserId: number
  participantUserIds?: number[]
  checkItemNames: string[]
  plannedStart?: string | null
  plannedEnd?: string | null
}) {
  return (await http.post<ApiResponse<unknown>>(`/architecture/plans/${id}/tasks`, payload)).data.data
}

export async function addCheckItem(taskId: number, payload: { name: string }) {
  return (await http.post<ApiResponse<CheckItemView>>(`/architecture/tasks/${taskId}/check-items`, payload)).data.data
}

export async function deleteTask(taskId: number, reason: string) {
  return (await http.delete<ApiResponse<void>>(`/architecture/tasks/${taskId}`, { data: { reason } })).data
}

export async function deleteCheckItem(checkItemId: number, reason: string) {
  return (await http.delete<ApiResponse<void>>(`/architecture/check-items/${checkItemId}`, { data: { reason } })).data
}

export async function startTask(taskId: number) {
  return (await http.post<ApiResponse<TaskDetailView>>(`/architecture/tasks/${taskId}/start`)).data.data
}

export async function completeCheckItem(checkItemId: number, remark?: string | null) {
  return (await http.post<ApiResponse<CheckItemView>>(`/architecture/check-items/${checkItemId}/complete`, { remark })).data.data
}

export async function reopenCheckItem(checkItemId: number, reason: string) {
  return (await http.post<ApiResponse<CheckItemView>>(`/architecture/check-items/${checkItemId}/reopen`, { reason })).data.data
}

export async function cancelCheckItem(checkItemId: number, reason: string) {
  return (await http.post<ApiResponse<CheckItemView>>(`/architecture/check-items/${checkItemId}/cancel`, { reason })).data.data
}

export async function restoreCheckItem(checkItemId: number, reason: string) {
  return (await http.post<ApiResponse<CheckItemView>>(`/architecture/check-items/${checkItemId}/restore`, { reason })).data.data
}

export async function suggestCancelCheckItem(checkItemId: number, reason: string) {
  return (await http.post<ApiResponse<SuggestionView>>(`/architecture/check-items/${checkItemId}/suggest-cancel`, { reason })).data.data
}

export async function acceptSuggestion(suggestionId: number, note?: string | null) {
  return (await http.post<ApiResponse<CheckItemView>>(`/architecture/suggestions/${suggestionId}/accept`, { reason: note })).data.data
}

export async function rejectSuggestion(suggestionId: number, note?: string | null) {
  return (await http.post<ApiResponse<SuggestionView>>(`/architecture/suggestions/${suggestionId}/reject`, { reason: note })).data.data
}

export async function listPlanSuggestions(id: number) {
  return (await http.get<ApiResponse<SuggestionView[]>>(`/architecture/plans/${id}/suggestions`)).data.data
}

export async function cancelTask(taskId: number, reason: string) {
  return (await http.post<ApiResponse<TaskDetailView>>(`/architecture/tasks/${taskId}/cancel`, { reason })).data.data
}

export async function restoreTask(taskId: number, reason: string) {
  return (await http.post<ApiResponse<TaskDetailView>>(`/architecture/tasks/${taskId}/restore`, { reason })).data.data
}

export async function cancelStage(stageId: number, reason: string) {
  return (await http.post<ApiResponse<unknown>>(`/architecture/stages/${stageId}/cancel`, { reason })).data.data
}

export async function restoreStage(stageId: number, reason: string) {
  return (await http.post<ApiResponse<unknown>>(`/architecture/stages/${stageId}/restore`, { reason })).data.data
}

export async function setTaskDependencies(taskId: number, predecessorTaskIds: number[], reason?: string | null) {
  return (await http.post<ApiResponse<DependencyView[]>>(`/architecture/tasks/${taskId}/dependencies`, { predecessorTaskIds, reason })).data.data
}

export async function removeDependency(dependencyId: number, reason: string) {
  return (await http.delete<ApiResponse<void>>(`/architecture/dependencies/${dependencyId}`, { data: { reason } })).data
}

export async function addTaskBlock(taskId: number, payload: { description: string; impact?: string | null; ownerUserId: number; expectedResolveAt?: string | null }) {
  return (await http.post<ApiResponse<BlockView>>(`/architecture/tasks/${taskId}/blocks`, payload)).data.data
}

export async function resolveTaskBlock(blockId: number, note?: string | null) {
  return (await http.post<ApiResponse<BlockView>>(`/architecture/blocks/${blockId}/resolve`, { reason: note })).data.data
}

export async function updatePlanSchedule(id: number, payload: { plannedStart?: string | null; plannedEnd?: string | null; reason?: string | null }) {
  return (await http.put<ApiResponse<PlanView>>(`/architecture/plans/${id}/schedule`, payload)).data.data
}

export async function updateTaskSchedule(taskId: number, payload: { plannedStart?: string | null; plannedEnd?: string | null; reason?: string | null }) {
  return (await http.put<ApiResponse<TaskDetailView>>(`/architecture/tasks/${taskId}/schedule`, payload)).data.data
}

export async function correctEvent(eventId: number, payload: { newOccurredAt: string; reason: string }) {
  return (await http.post<ApiResponse<void>>(`/architecture/events/${eventId}/correct`, payload)).data
}

export async function listPlanEvents(id: number) {
  return (await http.get<ApiResponse<EventView[]>>(`/architecture/plans/${id}/events`)).data.data
}

export async function attachTaskWorkOrders(taskId: number, payload: { workOrderType: WorkOrderType; workOrderIds: number[]; reason?: string | null }) {
  return (await http.post<ApiResponse<WorkOrderLinkView[]>>(`/architecture/tasks/${taskId}/work-orders`, payload)).data.data
}

export async function detachWorkOrder(workOrderRelationId: number, reason: string) {
  return (await http.delete<ApiResponse<void>>(`/architecture/work-orders/${workOrderRelationId}`, { data: { reason } })).data
}
