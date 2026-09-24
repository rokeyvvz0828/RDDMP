import http from './http'
import type { ApiResponse } from '../types/auth'
import type {
  LifecycleActivityView,
  LifecycleAuditView,
  LifecycleActivityStatusView,
  LifecycleComponentOption,
  LifecycleFeedbackView,
  LifecycleGranularityOrderStatus,
  LifecycleIssueStatusView,
  LifecycleOrderStatusView,
  LifecycleRiskStatusView,
  LifecycleStageActivityOrderView,
  LifecycleTopicProgressOverview,
  LifecycleIssueView,
  LifecycleKnowledgeEntryView,
  LifecycleMemberOption,
  LifecycleOrderProcessView,
  LifecycleOrderView,
  LifecyclePage,
  LifecycleProcessInput,
  LifecycleProcessView,
  LifecyclePublishStatus,
  LifecycleRiskStrategyLibView,
  LifecycleRiskView,
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

/** ===== 任务审核管理（基线第 15 章，T7） ===== */

export function listLifecycleAudit(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecyclePage<LifecycleAuditView>>>('/data-migration-lifecycle/audit', { params })
}

export function getLifecycleAuditRecords(orderId: number, processSeq: number) {
  return http.get<ApiResponse<LifecycleAuditView[]>>(`/data-migration-lifecycle/audit/${orderId}/records`, { params: { processSeq } })
}

export function getLifecycleAuditPackage(orderId: number) {
  return http.get<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/audit/${orderId}/package`)
}

export function passLifecycleAudit(body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/audit/pass', body)
}

export function rejectLifecycleAudit(body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/audit/reject', body)
}

export function batchPassLifecycleAudit(body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/audit/batch-pass', body)
}

export function batchRejectLifecycleAudit(body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/audit/batch-reject', body)
}

export function revokeLifecycleAuditBatch(body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/audit/batch-revoke', body)
}

/** ===== 问题管理 + 历史问题知识库（基线第 13 章，T8） ===== */

export function listLifecycleIssues(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecyclePage<LifecycleIssueView>>>('/data-migration-lifecycle/issues', { params })
}

export function createLifecycleIssue(body: Record<string, unknown>) {
  return http.post<ApiResponse<LifecycleIssueView>>('/data-migration-lifecycle/issues', body)
}

export function updateLifecycleIssue(issueId: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<LifecycleIssueView>>(`/data-migration-lifecycle/issues/${issueId}`, body)
}

export function importLifecycleIssues(body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/issue/import', body)
}

export function reconcileLifecycleIssues() {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/issue/reconcile')
}

export function rectifyLifecycleIssue(issueId: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<LifecycleIssueView>>(`/data-migration-lifecycle/issues/${issueId}/rectify`, body)
}

export function closeLifecycleIssue(issueId: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/issues/${issueId}/close`, body)
}

export function cancelLifecycleIssue(issueId: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<null>>(`/data-migration-lifecycle/issues/${issueId}/cancel`, body)
}

export function matchLifecycleIssueKnowledge(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecycleKnowledgeEntryView[]>>('/data-migration-lifecycle/issue/knowledge/match', { params })
}

export function listLifecycleKnowledge(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecycleKnowledgeEntryView[]>>('/data-migration-lifecycle/knowledge', { params })
}

export function referLifecycleKnowledge(knowledgeId: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<LifecycleKnowledgeEntryView>>(`/data-migration-lifecycle/knowledge/${knowledgeId}/refer`, body)
}

export function invalidateLifecycleKnowledge(knowledgeId: number) {
  return http.post<ApiResponse<LifecycleKnowledgeEntryView>>(`/data-migration-lifecycle/knowledge/${knowledgeId}/invalidate`)
}

/** ===== 风险管理 + 风险策略库（基线第 14 章，T9） ===== */

export function listLifecycleRisks(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecyclePage<LifecycleRiskView>>>('/data-migration-lifecycle/risks', { params })
}

export function createLifecycleRisk(body: Record<string, unknown>) {
  return http.post<ApiResponse<LifecycleRiskView>>('/data-migration-lifecycle/risks', body)
}

export function updateLifecycleRisk(riskId: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<LifecycleRiskView>>(`/data-migration-lifecycle/risks/${riskId}`, body)
}

export function reconcileLifecycleRisks() {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/risk/reconcile')
}

export function preventLifecycleRisk(riskId: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<LifecycleRiskView>>(`/data-migration-lifecycle/risks/${riskId}/prevent`, body)
}

export function closeLifecycleRisk(riskId: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/risks/${riskId}/close`, body)
}

export function cancelLifecycleRisk(riskId: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<null>>(`/data-migration-lifecycle/risks/${riskId}/cancel`, body)
}

export function matchLifecycleRiskStrategy(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecycleRiskStrategyLibView[]>>('/data-migration-lifecycle/risk/strategy/match', { params })
}

export function listLifecycleRiskStrategies(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecycleRiskStrategyLibView[]>>('/data-migration-lifecycle/risk/strategies', { params })
}

export function createLifecycleRiskStrategy(body: Record<string, unknown>) {
  return http.post<ApiResponse<LifecycleRiskStrategyLibView>>('/data-migration-lifecycle/risk/strategies', body)
}

export function reuseLifecycleRiskStrategy(strategyId: number, body: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/risk/strategies/${strategyId}/reuse`, body)
}

export function offlineLifecycleRiskStrategy(strategyId: number) {
  return http.post<ApiResponse<LifecycleRiskStrategyLibView>>(`/data-migration-lifecycle/risk/strategies/${strategyId}/offline`)
}

/** ===== 数据看板（基线第 16 章，T10）：六维度统计，全部只读 ===== */

export function getDashboardStageActivity() {
  return http.get<ApiResponse<LifecycleStageActivityOrderView[]>>('/data-migration-lifecycle/dashboard/stage-activity')
}

export function getDashboardActivityStatus() {
  return http.get<ApiResponse<LifecycleActivityStatusView>>('/data-migration-lifecycle/dashboard/activity-status')
}

export function getDashboardTopicProgress() {
  return http.get<ApiResponse<LifecycleTopicProgressOverview>>('/data-migration-lifecycle/dashboard/topic-progress')
}

export function getDashboardIssueStatus() {
  return http.get<ApiResponse<LifecycleIssueStatusView>>('/data-migration-lifecycle/dashboard/issue-status')
}

export function getDashboardRiskStatus() {
  return http.get<ApiResponse<LifecycleRiskStatusView>>('/data-migration-lifecycle/dashboard/risk-status')
}

export function getDashboardOrderStatus() {
  return http.get<ApiResponse<LifecycleOrderStatusView>>('/data-migration-lifecycle/dashboard/order-status')
}
