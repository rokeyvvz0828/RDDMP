export type DateValue = string | null
export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type WorkItemStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE'
export interface Page<T> { records: T[]; total: number; page: number; size: number }
export interface UserRef { id: string; name: string }
export interface SystemRef { id: string; code: string; name: string; ownerId: string | null; status: string; rowVersion: number }
export interface SourceRef { id: string; number: string; revision: string; roles: string[]; systemCodes: string[] }
export interface DurationMetrics {
  status: 'available' | 'unavailable'; plannedDays: number | null; actualDays: number | null
  variancePercent: number | null; calendarVersion: string; calendarLabel: string
}
export interface TaskFields {
  title: string; description: string; ownerId: string | null
  developmentPlanStart: DateValue; developmentPlanEnd: DateValue; testPlanStart: DateValue; testPlanEnd: DateValue
}
export interface CreateTaskInput extends TaskFields {
  projectRef: string; sourceMode: 'LINKED' | 'STANDALONE'; sourceRequirementId: string | null
  sourceRevision: string | null; systemId: string; requestId: string
}
export interface UpdateTaskInput extends TaskFields { rowVersion: number }
export interface DevelopmentTask extends Omit<TaskFields, 'ownerId'> {
  id: string; number: string; projectRef: string; sourceMode: 'LINKED' | 'STANDALONE'; source: SourceRef | null
  system: SystemRef; owner: UserRef; status: TaskStatus; rowVersion: number; allowedActions: string[]
  workItemCount: number; completedWorkItemCount: number; createdAt: string; updatedAt: string
}
export interface PendingRequirement {
  sourceRequirementId: string; sourceNumber: string; sourceName: string; summary: string | null
  sourceRevision: string; system: SystemRef; roles: string[]
}
export interface WorkItemInput {
  taskId: string; title: string; description: string; assigneeId: string | null
  plannedStart: DateValue; plannedEnd: DateValue; actualStart: DateValue; actualEnd: DateValue
  blocked: boolean; blockReason: string; rowVersion?: number
}
export interface WorkItem extends Omit<WorkItemInput, 'assigneeId' | 'rowVersion'> {
  id: string; taskNumber: string; taskTitle: string; system: SystemRef; source: SourceRef | null
  assignee: UserRef; status: WorkItemStatus; allowedActions: string[]; rowVersion: number; warnings: string[]; duration: DurationMetrics
}
export interface WorkItemPage extends Page<WorkItem> { counts: Record<WorkItemStatus, number> }
export interface TaskFilters { keyword: string; status: string; systemId: string; ownerId: string; page: number; size: number }
export interface WorkItemFilters {
  keyword: string; status: string; taskId: string; systemId: string; assigneeId: string; plannedFrom: DateValue; plannedTo: DateValue
}
export interface WorkItemColumn { records: WorkItem[]; total: number | null; page: number; requestedPage: number; loading: boolean; error: string }
export interface AttachmentRef { id: string; fileName: string; fileSize: number; contentType: string | null }
export interface StageInput {
  designPlanStart: DateValue; designPlanEnd: DateValue; designDocumentPath: string; designAttachmentIds: string[]
  implementationActualStart: DateValue; implementationActualEnd: DateValue; codeWalkAttachmentIds: string[]
  testReportAttachmentIds: string[]; notApplicableDesign: boolean; notApplicableImplementation: boolean; rowVersion: number
}
export interface TaskStage extends Omit<StageInput, 'designAttachmentIds' | 'codeWalkAttachmentIds' | 'testReportAttachmentIds'> {
  taskId: string; taskRowVersion: number; pureTest: boolean; readOnly: boolean
  designAttachments: AttachmentRef[]; codeWalkAttachments: AttachmentRef[]; testReportAttachments: AttachmentRef[]
  designRegisteredAt: string | null; testRegisteredAt: string | null; duration: DurationMetrics
}
export interface TaskChange {
  id: string; objectType: string; objectId: string; action: string; before: Record<string, unknown> | null
  after: Record<string, unknown> | null; actor: UserRef; createdAt: string; traceId: string
}
export const TASK_STATUS_LABELS: Record<TaskStatus, string> = { NOT_STARTED: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' }
export const WORK_ITEM_STATUS_LABELS: Record<WorkItemStatus, string> = { TODO: '待办', IN_PROGRESS: '进行中', IN_REVIEW: '待验收', DONE: '已完成' }
export const WORK_ITEM_STATUSES: WorkItemStatus[] = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE']
export const ACTION_LABELS: Record<string, string> = { CREATE: '新建', UPDATE: '修改', START: '开始', SUBMIT: '提交验收', ACCEPT: '验收通过', RETURN: '退回', REOPEN: '重新打开', COMPLETE: '完成任务', CANCEL: '取消任务', RESTORE: '恢复任务', REFRESH: '更新来源' }
export const SOURCE_ROLE_LABELS: Record<string, string> = { LEAD: '主责', CHANGE: '改造', TEST: '测试' }
export function statusTone(status: string): 'primary' | 'success' | 'warning' | 'info' {
  return status === 'DONE' || status === 'COMPLETED' ? 'success' : status === 'IN_REVIEW' ? 'warning' : status === 'IN_PROGRESS' ? 'primary' : 'info'
}
export function dateRange(start: DateValue, end: DateValue) { return start || end ? `${start || '未定'} ~ ${end || '未定'}` : '未排期' }
