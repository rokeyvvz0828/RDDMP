import http from './http'
import type { ApiResponse } from '../types/auth'
import type {
  LifecycleActivityView,
  LifecycleComponentOption,
  LifecycleFeedbackView,
  LifecycleMemberOption,
  LifecycleOrderProcessView,
  LifecycleOrderView,
  LifecyclePage,
  LifecycleProcessInput,
  LifecycleProcessView,
  LifecyclePublishStatus,
  LifecycleRoleOption,
  LifecycleStageOption,
  LifecycleTaskView,
  LifecycleTopicCandidate,
  LifecycleTopologyInput,
  LifecycleTopologyView
} from '../types/data-migration-lifecycle'

/** 生命周期平台底座选项接口（R9：成员/组件/角色选择器复用既有数据源）。 */

export function listLifecycleMemberOptions(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecyclePage<LifecycleMemberOption>>>('/data-migration-lifecycle/options/members', { params })
}

export function listLifecycleComponentOptions(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecycleComponentOption[]>>('/data-migration-lifecycle/options/components', { params })
}

export function listLifecycleRoleOptions() {
  return http.get<ApiResponse<LifecycleRoleOption[]>>('/data-migration-lifecycle/options/roles')
}

export function listLifecycleStages() {
  return http.get<ApiResponse<LifecycleStageOption[]>>('/data-migration-lifecycle/options/stages')
}

/** ===== 活动管理（基线第 9 章，T3） ===== */

export function listLifecycleActivities(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecyclePage<LifecycleActivityView>>>('/data-migration-lifecycle/activities', { params })
}

export function getLifecycleActivity(id: number) {
  return http.get<ApiResponse<LifecycleActivityView>>(`/data-migration-lifecycle/activities/${id}`)
}

export function createLifecycleActivity(body: Record<string, unknown>) {
  return http.post<ApiResponse<LifecycleActivityView>>('/data-migration-lifecycle/activities', body)
}

export function updateLifecycleActivity(id: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<LifecycleActivityView>>(`/data-migration-lifecycle/activities/${id}`, body)
}

export function setLifecycleActivityStatus(id: number, status: string) {
  return http.put<ApiResponse<LifecycleActivityView>>(`/data-migration-lifecycle/activities/${id}/status`, { status })
}

export function obsoleteLifecycleActivity(id: number) {
  return http.post<ApiResponse<LifecycleActivityView>>(`/data-migration-lifecycle/activities/${id}/obsolete`)
}

export function getLifecycleTopicCandidates(id: number) {
  return http.get<ApiResponse<LifecycleTopicCandidate[]>>(`/data-migration-lifecycle/activities/${id}/topic-candidates`)
}

export function getLifecycleTopicMembers(id: number) {
  return http.get<ApiResponse<LifecycleTopicCandidate[]>>(`/data-migration-lifecycle/activities/${id}/topic-members`)
}

export function addLifecycleTopicMember(id: number, memberActivityId: number) {
  return http.post<ApiResponse<void>>(`/data-migration-lifecycle/activities/${id}/topic-members`, { memberActivityId })
}

export function removeLifecycleTopicMember(id: number, memberActivityId: number) {
  return http.delete<ApiResponse<void>>(`/data-migration-lifecycle/activities/${id}/topic-members/${memberActivityId}`)
}

export function getLifecycleProcesses(id: number) {
  return http.get<ApiResponse<LifecycleProcessView[]>>(`/data-migration-lifecycle/activities/${id}/processes`)
}

export function saveLifecycleProcesses(id: number, inputs: LifecycleProcessInput[]) {
  return http.put<ApiResponse<LifecycleProcessView[]>>(`/data-migration-lifecycle/activities/${id}/processes`, inputs)
}

export function getLifecycleTopology(id: number) {
  return http.get<ApiResponse<LifecycleTopologyView>>(`/data-migration-lifecycle/activities/${id}/topology`)
}

export function saveLifecycleTopology(id: number, input: LifecycleTopologyInput) {
  return http.put<ApiResponse<LifecycleTopologyView>>(`/data-migration-lifecycle/activities/${id}/topology`, input)
}

export function publishLifecycleVersion(id: number) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/activities/${id}/publish`)
}

export function getLifecyclePublishStatus(id: number) {
  return http.get<ApiResponse<LifecyclePublishStatus>>(`/data-migration-lifecycle/activities/${id}/publish-status`)
}

export function getLifecycleSnapshot(id: number) {
  return http.get<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/activities/${id}/snapshot`)
}

export function exportLifecycleTemplate(id: number) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/activities/${id}/template/export`)
}

export function importLifecycleTemplate(packageJson: string) {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/activities/template/import', { packageJson })
}

/** ===== 任务发布（基线第 10 章，T4） ===== */

export function listLifecycleTasks(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecyclePage<LifecycleTaskView>>>('/data-migration-lifecycle/tasks', { params })
}

export function getLifecycleTask(id: number) {
  return http.get<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/tasks/${id}`)
}

export function dispatchLifecycleTask(body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/tasks/dispatch', body)
}

export function getTopicAggregateEdges(activityId: number) {
  return http.get<ApiResponse<Array<{ sourceActivityId: number; targetActivityId: number }>>>(`/data-migration-lifecycle/activities/${activityId}/aggregate-edges`)
}

export function saveTopicAggregateEdges(activityId: number, edges: Array<{ sourceActivityId: number; targetActivityId: number }>) {
  return http.put<ApiResponse<void>>(`/data-migration-lifecycle/activities/${activityId}/aggregate-edges`, { edges })
}

/** ===== 工单流转（基线第 11 章 + 18.x，T5） ===== */

export function listLifecycleOrders(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecyclePage<LifecycleOrderView>>>('/data-migration-lifecycle/orders', { params })
}

export function getLifecycleOrder(id: number) {
  return http.get<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/orders/${id}`)
}

export function getLifecycleOrderProcesses(id: number) {
  return http.get<ApiResponse<LifecycleOrderProcessView[]>>(`/data-migration-lifecycle/orders/${id}/processes`)
}

export function suspendLifecycleOrder(id: number, reason: string) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/orders/${id}/suspend`, { reason })
}

export function resumeLifecycleOrder(id: number) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/orders/${id}/resume`)
}

export function transferLifecycleOrder(id: number, nextExecutorId: number, reason: string) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/orders/${id}/transfer`, { nextExecutorId, reason })
}

export function restartLifecycleOrder(id: number, restartPointSeq: number, reason: string) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/orders/${id}/restart`, { restartPointSeq, reason })
}

export function adjustLifecycleOrderSla(id: number, planFinishTime: string | null, reason: string) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/orders/${id}/sla-adjust`, { planFinishTime, reason })
}

export function exemptLifecycleOrderSla(id: number, exempt: boolean, reason: string) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/orders/${id}/sla-exempt`, { exempt, reason })
}

export function archiveLifecycleOrder(id: number) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/orders/${id}/archive`)
}

export function applyLifecycleAuditResult(id: number, processSeq: number, auditResult: 'PASSED' | 'REJECTED') {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/orders/${id}/processes/${processSeq}/audit-result`, { auditResult })
}

/** ===== 执行反馈（基线第 12 章，T6） ===== */

export function getLifecycleFeedback(orderId: number, processSeq: number) {
  return http.get<ApiResponse<LifecycleFeedbackView>>(`/data-migration-lifecycle/feedback/orders/${orderId}/processes/${processSeq}`)
}

export function saveLifecycleFeedback(orderId: number, processSeq: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<LifecycleFeedbackView>>(`/data-migration-lifecycle/feedback/orders/${orderId}/processes/${processSeq}`, body)
}

export function submitLifecycleFeedbackAudit(orderId: number, processSeq: number) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/feedback/orders/${orderId}/processes/${processSeq}/submit-audit`)
}

export function reportLifecycleIssue(orderId: number, processSeq: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/feedback/orders/${orderId}/processes/${processSeq}/issues`, body)
}

export function reportLifecycleRisk(orderId: number, processSeq: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/feedback/orders/${orderId}/processes/${processSeq}/risks`, body)
}

export function addLifecycleWorkLog(orderId: number, processSeq: number, content: string) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/feedback/orders/${orderId}/processes/${processSeq}/work-log`, { content })
}
