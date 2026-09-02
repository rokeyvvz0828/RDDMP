import http from './http'
import type { ApiResponse } from '../types/auth'

export interface PageResult<T> { records: T[]; total: number; page: number; size: number }

export type ReleaseWindowStatus = 'UPCOMING' | 'DECLARATION_OPEN' | 'URGENT' | 'IN_PRODUCTION' | 'CLOSED'
export interface ReleaseWindowDto {
  id: number
  windowCode: string
  windowName: string
  projectId: string
  projectCode: string
  projectName: string
  declarationStart: string
  declarationEnd: string
  productionStart: string
  productionEnd: string
  regularEnabled: boolean
  description?: string
  status: ReleaseWindowStatus
  statusLabel: string
  regularApplicationSelectable: boolean
  unavailableReason?: string
  rowVersion: number
  createdAt?: string
  updatedAt?: string
}

export interface ReleaseWindowWrite {
  windowName: string
  projectId: string
  projectCode: string
  projectName: string
  declarationStart: string
  declarationEnd: string
  productionStart: string
  productionEnd: string
  regularEnabled: boolean
  description?: string
}

export interface ReleaseWindowUpdate extends ReleaseWindowWrite {
  windowCode: string
  rowVersion: number
  changeReason: string
}

export type ArtifactTypeCode = 'IMAGE' | 'BINARY' | 'FILE'
export type DeliveryItemTypeCode = 'DELIVERY_UNIT' | 'FILE_MEDIA'
export type ReleaseVersionTypeCode = 'REGULAR' | 'URGENT' | 'EMERGENCY'
export type ReleaseCharacteristicCode = 'STANDARD' | 'ADDITIONAL'
export type ReleaseApplicationStatusCode = 'DRAFT' | 'IN_REVIEW' | 'RETURNED' | 'WITHDRAWN' | 'CANCELLED' | 'RELEASED'

export interface ReleaseDeliveryDto {
  id: number
  deliveryUnitId: string
  deliveryUnitCode: string
  deliveryUnitName: string
  artifactType: Exclude<ArtifactTypeCode, 'FILE'>
  artifactVersion: string
}

export interface ReleaseFileMediaDto { id: number; filePath: string }

export interface ReleaseApplicationDto {
  applicationCode: string
  projectId: string
  projectCode: string
  projectName: string
  emergency: boolean
  windowId?: number
  windowCode?: string
  windowName?: string
  subsystemId: string
  subsystemCode: string
  subsystemName: string
  versionType: ReleaseVersionTypeCode
  characteristic: ReleaseCharacteristicCode
  workflowCode?: string
  status: ReleaseApplicationStatusCode
  requesterId: number
  requesterName: string
  requesterDepartment?: string
  emergencyDescription?: string
  urgentReason?: string
  description?: string
  deliveries: ReleaseDeliveryDto[]
  fileMedia: ReleaseFileMediaDto[]
  requirementCodes: string[]
  windowAvailable: boolean
  windowUnavailableReason?: string
  rowVersion: number
  approvedAt?: string
  createdAt?: string
  updatedAt?: string
  conflicts: ReleaseConflictReportDto
}

export interface ReleaseVersionChangeDto {
  deliveryUnitCode: string
  deliveryUnitName: string
  previousVersion: string
  currentVersion: string
}

export interface ReleaseHistoricalApplicationDto {
  application: ReleaseApplicationDto
  versionChanges: ReleaseVersionChangeDto[]
  allowedActions: Array<'CANCEL_OLD' | 'EDIT_OLD' | 'CREATE_NEW'>
}

export interface ReleaseRelatedHistoryDto {
  applicationCode: string
  status: ReleaseApplicationStatusCode
  versionType: ReleaseVersionTypeCode
  characteristic: ReleaseCharacteristicCode
  requesterName: string
  requesterDepartment?: string
  createdAt?: string
  approvedAt?: string
  requirementCodes: string[]
  description?: string
  versionChanges: ReleaseVersionChangeDto[]
}

export interface ReleaseConflictReportDto {
  conflictToken?: string
  applications: ReleaseHistoricalApplicationDto[]
}

export interface ReleaseDeliveryInput {
  deliveryUnitId: string
  deliveryUnitCode: string
  deliveryUnitName: string
  artifactType: Exclude<ArtifactTypeCode, 'FILE'>
  artifactVersion: string
}

export interface ReleaseFileMediaInput { filePath: string }

export interface ReleaseApplicationWrite {
  emergency: boolean
  windowId?: number
  projectId: string
  projectCode: string
  projectName: string
  subsystemId: string
  subsystemCode: string
  subsystemName: string
  deliveries: ReleaseDeliveryInput[]
  fileMedia: ReleaseFileMediaInput[]
  requirementCodes: string[]
  emergencyDescription?: string
  urgentReason?: string
  description?: string
}

export interface ReleaseApplicationUpdate extends ReleaseApplicationWrite { rowVersion: number }
export interface ReleaseAttachmentInput { attachmentId: number; category: 'TEST_REPORT' | 'SUPPORTING' }
export interface ReleaseSubmitRequest { rowVersion: number; conflictToken?: string; attachments: ReleaseAttachmentInput[] }
export interface ReleaseSubmitResult { applicationCode: string; status: 'IN_REVIEW'; rowVersion: number; roundNo: number; workflowInstanceId: number; workflowDefinitionId: number; workflowDefinitionVersion: number; dataDigest: string }
export interface ReleaseWorkflowActionResult { applicationCode: string; status: string; operationStatus: string; roundNo: number; workflowInstanceId: number; rowVersion: number }
export interface ReleaseRoundDto { roundNo: number; workflowCode: string; workflowDefinitionId?: number; workflowDefinitionVersion?: number; workflowInstanceId?: number; roundStatus: string; dataDigest: string; submittedAt?: string; completedAt?: string }
export interface ReleaseAttachmentDto { attachmentId: number; category: 'TEST_REPORT' | 'SUPPORTING'; fileName: string }

export interface ProductionEntryDto {
  id: number
  tenantId: number
  windowId: number
  windowName?: string
  applicationId: number
  applicationCode: string
  approvedAt: string
  subsystemId: string
  subsystemCode: string
  subsystemName: string
  deliveryUnitId: string
  deliveryUnitCode: string
  deliveryUnitName: string
  artifactType: ArtifactTypeCode
  artifactVersion?: string
  itemType: DeliveryItemTypeCode
  filePath?: string
  itemKey: string
  versionType: ReleaseVersionTypeCode
  characteristic: ReleaseCharacteristicCode
  productionResult: 'RELEASED' | 'SUCCEEDED' | 'FAILED' | 'NOT_DEPLOYED'
  productionAt?: string
  resultReason?: string
  activeCandidate: boolean
  rowVersion: number
  createdAt?: string
  updatedAt?: string
}

export type MaintainedProductionResult = Exclude<ProductionEntryDto['productionResult'], 'RELEASED'>
export interface ProductionResultWrite {
  productionResult: MaintainedProductionResult
  productionAt?: string
  resultReason?: string
  changeReason: string
}
export interface BatchProductionResultWrite extends ProductionResultWrite {
  entries: Array<{ id: number; rowVersion: number }>
}

export interface ReleaseAnalyticsSummaryDto {
  windowCount: number
  applicationCount: number
  subsystemCount: number
  deliveryUnitCount: number
  fileMediaCount: number
  requirementCount: number
  versionTypes: Record<string, number>
  productionResults: Record<string, number>
}

export type ReleaseWorkflowSceneCode = 'REGULAR' | 'REGULAR_ADDITIONAL' | 'URGENT' | 'URGENT_ADDITIONAL' | 'EMERGENCY'
export interface ReleaseWorkflowBindingDto {
  sceneCode: ReleaseWorkflowSceneCode
  sceneName: string
  projectRef: string
  projectName?: string
  workflowDefinitionId?: number
  workflowCode?: string
  workflowName?: string
  workflowVersion?: number
  configured: boolean
  valid: boolean
  invalidReason?: string
  rowVersion: number
  updatedAt?: string
}
export interface ReleasePublishedWorkflowDto {
  definitionId: number
  workflowCode: string
  workflowName: string
  workflowVersion: number
}
export interface ReleaseWorkflowBindingHistoryDto {
  id: number
  sceneCode: ReleaseWorkflowSceneCode
  beforeDefinitionId?: number
  beforeWorkflowCode?: string
  beforeWorkflowName?: string
  beforeWorkflowVersion?: number
  afterDefinitionId?: number
  afterWorkflowCode?: string
  afterWorkflowName?: string
  afterWorkflowVersion?: number
  reason: string
  operatorId: number
  operatorName: string
  occurredAt: string
}

export type ReleaseDrillStatus = 'PLANNED' | 'RUNNING' | 'COMPLETED'
export type ReleaseIssuePriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ReleaseIssueStatus = 'OPEN' | 'ANALYZING' | 'RESOLVED' | 'CLOSED'
export type ReleaseTimelineType = 'NORMAL' | 'ROLLBACK'

export interface ReleaseDrillRoundDto {
  id: number
  projectId: number
  roundNo: number
  roundName: string
  plannedAt?: string
  status: ReleaseDrillStatus
  resultContent?: string
  rowVersion: number
  updatedAt?: string
}
export interface ReleaseDrillPlanDto {
  id: number
  tenantId: number
  projectId: number
  scenarioContent?: string
  environmentContent?: string
  rowVersion: number
  updatedAt?: string
  rounds: ReleaseDrillRoundDto[]
}
export interface ReleaseDrillPlanWrite { scenarioContent?: string; environmentContent?: string; rowVersion: number }
export interface ReleaseDrillRoundWrite { roundName: string; plannedAt?: string; status: ReleaseDrillStatus; resultContent?: string; rowVersion: number }

export interface ReleaseTimelineItemDto {
  id: number
  projectId: number
  seqNo: number
  itemName: string
  plannedStart?: string
  plannedEnd?: string
  ownerId?: number
  ownerName?: string
  status: string
  description?: string
  rowVersion: number
  updatedAt?: string
}
export interface ReleaseTimelineDto {
  id: number
  projectId: number
  timelineType: ReleaseTimelineType
  timelineName: string
  description?: string
  rowVersion: number
  updatedAt?: string
  items: ReleaseTimelineItemDto[]
}
export interface ReleaseTimelineWrite { timelineName: string; description?: string; rowVersion: number }
export interface ReleaseTimelineItemWrite {
  seqNo?: number
  itemName: string
  plannedStart?: string
  plannedEnd?: string
  ownerId?: number
  status?: string
  description?: string
  rowVersion: number
}

export interface ReleaseIssueDto {
  id: number
  projectId: number
  issueNo: string
  issueTitle: string
  priority: ReleaseIssuePriority
  issueStatus: ReleaseIssueStatus
  discoveredAt?: string
  ownerId?: number
  ownerName?: string
  issueDescription?: string
  analysisContent?: string
  actionContent?: string
  followUpContent?: string
  closedAt?: string
  rowVersion: number
  updatedAt?: string
}
export interface ReleaseIssueWrite {
  issueNo: string
  issueTitle: string
  priority: ReleaseIssuePriority
  issueStatus: ReleaseIssueStatus
  discoveredAt?: string
  ownerId?: number
  issueDescription?: string
  analysisContent?: string
  actionContent?: string
  followUpContent?: string
  closedAt?: string
  rowVersion: number
}

export interface ReleaseGroupMemberDto {
  id: number
  groupId: number
  projectMemberId: number
  userId: number
  memberName: string
  createdAt?: string
}
export interface ReleaseGroupDto {
  id: number
  projectId: number
  groupName: string
  description?: string
  rowVersion: number
  updatedAt?: string
  members: ReleaseGroupMemberDto[]
}
export interface ReleaseGroupWrite { groupName: string; description?: string; rowVersion: number }
export interface ReleaseMemberOptionDto { id: number; userId: number; displayName: string; username: string }

export function listReleaseWindows(params: { page?: number; size?: number; projectId?: string; keyword?: string }) {
  return http.get<ApiResponse<PageResult<ReleaseWindowDto>>>('/release/windows', { params })
}
export function getReleaseWindow(id: number) { return http.get<ApiResponse<ReleaseWindowDto>>(`/release/windows/${id}`) }
export function createReleaseWindow(data: ReleaseWindowWrite) { return http.post<ApiResponse<ReleaseWindowDto>>('/release/windows', data) }
export function updateReleaseWindow(id: number, data: ReleaseWindowUpdate) { return http.put<ApiResponse<ReleaseWindowDto>>(`/release/windows/${id}`, data) }
export function changeReleaseWindowRegularEnabled(id: number, regularEnabled: boolean, rowVersion: number, changeReason: string) {
  return http.put<ApiResponse<ReleaseWindowDto>>(`/release/windows/${id}/regular-enabled`, { regularEnabled, rowVersion, changeReason })
}

export function listReleaseApplications(params: { page?: number; size?: number; projectId?: string; windowId?: number; keyword?: string; status?: ReleaseApplicationStatusCode; mineOnly?: boolean }) {
  return http.get<ApiResponse<PageResult<ReleaseApplicationDto>>>('/release/applications', { params })
}
export function getReleaseApplication(code: string) { return http.get<ApiResponse<ReleaseApplicationDto>>(`/release/applications/${encodeURIComponent(code)}`) }
export function getReleaseApplicationRelatedHistory(code: string) { return http.get<ApiResponse<ReleaseRelatedHistoryDto[]>>(`/release/applications/${encodeURIComponent(code)}/related-history`) }
export function createReleaseApplication(data: ReleaseApplicationWrite) { return http.post<ApiResponse<ReleaseApplicationDto>>('/release/applications', data) }
export function updateReleaseApplication(code: string, data: ReleaseApplicationUpdate) { return http.put<ApiResponse<ReleaseApplicationDto>>(`/release/applications/${encodeURIComponent(code)}`, data) }
export function previewReleaseApplicationConflicts(data: ReleaseApplicationWrite) {
  return http.post<ApiResponse<ReleaseConflictReportDto>>('/release/applications/conflicts/preview', data)
}
export function previewReleaseApplicationUpdateConflicts(code: string, data: ReleaseApplicationUpdate) {
  return http.post<ApiResponse<ReleaseConflictReportDto>>(`/release/applications/${encodeURIComponent(code)}/conflicts/preview`, data)
}
export function getReleaseApplicationConflicts(code: string) { return http.get<ApiResponse<ReleaseConflictReportDto>>(`/release/applications/${encodeURIComponent(code)}/conflicts`) }
export function resolveReleaseConflict(code: string, data: { action: 'CANCEL_OLD' | 'EDIT_OLD' | 'CREATE_NEW'; targetApplicationCode: string; targetRowVersion?: number; conflictToken: string; reason?: string }) {
  return http.post<ApiResponse<{ action: string; navigateApplicationCode: string; conflicts: ReleaseConflictReportDto }>>(`/release/applications/${encodeURIComponent(code)}/conflicts`, data)
}
export function submitReleaseApplication(code: string, data: ReleaseSubmitRequest) { return http.post<ApiResponse<ReleaseSubmitResult>>(`/release/applications/${encodeURIComponent(code)}/submit`, data) }
export function withdrawReleaseApplication(code: string, rowVersion: number, reason: string) { return http.post<ApiResponse<ReleaseWorkflowActionResult>>(`/release/applications/${encodeURIComponent(code)}/withdraw`, { rowVersion, reason }) }
export function cancelBlockedReleaseApplication(code: string, rowVersion: number, reason: string) { return http.post<ApiResponse<ReleaseWorkflowActionResult>>(`/release/applications/${encodeURIComponent(code)}/conflict-cancel`, { rowVersion, reason }) }
export function cancelReleaseApplication(code: string, rowVersion: number, reason: string) { return http.post<ApiResponse<ReleaseApplicationDto>>(`/release/applications/${encodeURIComponent(code)}/cancel`, { rowVersion, reason }) }
export function getReleaseApplicationRound(code: string) { return http.get<ApiResponse<ReleaseRoundDto | null>>(`/release/applications/${encodeURIComponent(code)}/workflow`) }
export function listReleaseApplicationAttachments(code: string) { return http.get<ApiResponse<ReleaseAttachmentDto[]>>(`/release/applications/${encodeURIComponent(code)}/attachments`) }
export function deleteReleaseApplicationAttachment(code: string, attachmentId: number, rowVersion: number, reason: string) {
  return http.delete<ApiResponse<{ attachmentId: number; rowVersion: number }>>(`/release/applications/${encodeURIComponent(code)}/attachments/${attachmentId}`, { data: { rowVersion, reason } })
}

export function getProductionBaseline(windowId: number) { return http.get<ApiResponse<ProductionEntryDto[]>>('/release/production-baselines', { params: { windowId } }) }
export function updateProductionResult(entryId: number, data: ProductionResultWrite & { rowVersion: number }) { return http.put<ApiResponse<ProductionEntryDto>>(`/release/production-baselines/entries/${entryId}/result`, data) }
export function batchUpdateProductionResults(data: BatchProductionResultWrite) { return http.put<ApiResponse<ProductionEntryDto[]>>('/release/production-baselines/results/batch', data) }
export function getCurrentProductionVersions(projectId?: string) { return http.get<ApiResponse<ProductionEntryDto[]>>('/release/production-versions', { params: { projectId } }) }
export function getProductionVersionHistory(subsystemCode: string, deliveryUnitCode: string) { return http.get<ApiResponse<ProductionEntryDto[]>>(`/release/production-versions/${encodeURIComponent(subsystemCode)}/${encodeURIComponent(deliveryUnitCode)}/history`) }
export function getProductionVersionHistoryByEntry(entryId: number) { return http.get<ApiResponse<ProductionEntryDto[]>>(`/release/production-versions/entries/${entryId}/history`) }
export function getReleaseAnalyticsSummary(projectId?: string, windowId?: number) { return http.get<ApiResponse<ReleaseAnalyticsSummaryDto>>('/release/analytics/summary', { params: { projectId, windowId } }) }
export function getReleaseAnalyticsDrilldown(params: { page?: number; size?: number; projectId?: string; windowId?: number; dimension?: string; value?: string }) { return http.get<ApiResponse<PageResult<Record<string, unknown>>>>('/release/analytics/drilldown', { params }) }
export function listReleaseWorkflowBindings(projectRef: string) {
  return http.get<ApiResponse<ReleaseWorkflowBindingDto[]>>('/release/workflow-bindings', { params: { projectRef } })
}
export function listPublishedReleaseWorkflows() {
  return http.get<ApiResponse<ReleasePublishedWorkflowDto[]>>('/release/workflow-bindings/published-definitions')
}
export function updateReleaseWorkflowBinding(sceneCode: ReleaseWorkflowSceneCode, data: {
  projectRef: string; projectName: string; workflowDefinitionId?: number; rowVersion: number; reason: string
}) {
  return http.put<ApiResponse<ReleaseWorkflowBindingDto>>(`/release/workflow-bindings/${sceneCode}`, data)
}
export function listReleaseWorkflowBindingHistory(sceneCode: ReleaseWorkflowSceneCode, projectRef: string) {
  return http.get<ApiResponse<ReleaseWorkflowBindingHistoryDto[]>>(`/release/workflow-bindings/${sceneCode}/history`, { params: { projectRef } })
}

export function getReleaseDrillPlan(projectId: number) { return http.get<ApiResponse<ReleaseDrillPlanDto | null>>('/release/operations/drill-plan', { params: { projectId } }) }
export function saveReleaseDrillPlan(projectId: number, data: ReleaseDrillPlanWrite) { return http.put<ApiResponse<ReleaseDrillPlanDto>>('/release/operations/drill-plan', data, { params: { projectId } }) }
export function createReleaseDrillRound(projectId: number, data: ReleaseDrillRoundWrite) { return http.post<ApiResponse<ReleaseDrillRoundDto>>('/release/operations/drill-plan/rounds', data, { params: { projectId } }) }
export function updateReleaseDrillRound(projectId: number, id: number, data: ReleaseDrillRoundWrite) { return http.put<ApiResponse<ReleaseDrillRoundDto>>(`/release/operations/drill-plan/rounds/${id}`, data, { params: { projectId } }) }
export function deleteReleaseDrillRound(projectId: number, id: number, rowVersion: number) { return http.delete<ApiResponse<void>>(`/release/operations/drill-plan/rounds/${id}`, { params: { projectId, rowVersion } }) }

export function getReleaseTimeline(projectId: number, type: ReleaseTimelineType) { return http.get<ApiResponse<ReleaseTimelineDto | null>>(`/release/operations/timelines/${type}`, { params: { projectId } }) }
export function saveReleaseTimeline(projectId: number, type: ReleaseTimelineType, data: ReleaseTimelineWrite) { return http.put<ApiResponse<ReleaseTimelineDto>>(`/release/operations/timelines/${type}`, data, { params: { projectId } }) }
export function createReleaseTimelineItem(projectId: number, type: ReleaseTimelineType, data: ReleaseTimelineItemWrite) { return http.post<ApiResponse<ReleaseTimelineItemDto>>(`/release/operations/timelines/${type}/items`, data, { params: { projectId } }) }
export function updateReleaseTimelineItem(projectId: number, type: ReleaseTimelineType, id: number, data: ReleaseTimelineItemWrite) { return http.put<ApiResponse<ReleaseTimelineItemDto>>(`/release/operations/timelines/${type}/items/${id}`, data, { params: { projectId } }) }
export function deleteReleaseTimelineItem(projectId: number, type: ReleaseTimelineType, id: number, rowVersion: number) { return http.delete<ApiResponse<void>>(`/release/operations/timelines/${type}/items/${id}`, { params: { projectId, rowVersion } }) }

export function listReleaseOperationsIssues(params: { projectId: number; keyword?: string; priority?: ReleaseIssuePriority; status?: ReleaseIssueStatus; page?: number; size?: number }) { return http.get<ApiResponse<PageResult<ReleaseIssueDto>>>('/release/operations/issues', { params }) }
export function createReleaseOperationsIssue(projectId: number, data: ReleaseIssueWrite) { return http.post<ApiResponse<ReleaseIssueDto>>('/release/operations/issues', data, { params: { projectId } }) }
export function updateReleaseOperationsIssue(projectId: number, id: number, data: ReleaseIssueWrite) { return http.put<ApiResponse<ReleaseIssueDto>>(`/release/operations/issues/${id}`, data, { params: { projectId } }) }
export function deleteReleaseOperationsIssue(projectId: number, id: number, rowVersion: number) { return http.delete<ApiResponse<void>>(`/release/operations/issues/${id}`, { params: { projectId, rowVersion } }) }

export function listReleaseOperationGroups(projectId: number) { return http.get<ApiResponse<ReleaseGroupDto[]>>('/release/operations/groups', { params: { projectId } }) }
export function createReleaseOperationGroup(projectId: number, data: ReleaseGroupWrite) { return http.post<ApiResponse<ReleaseGroupDto>>('/release/operations/groups', data, { params: { projectId } }) }
export function updateReleaseOperationGroup(projectId: number, id: number, data: ReleaseGroupWrite) { return http.put<ApiResponse<ReleaseGroupDto>>(`/release/operations/groups/${id}`, data, { params: { projectId } }) }
export function deleteReleaseOperationGroup(projectId: number, id: number, rowVersion: number) { return http.delete<ApiResponse<void>>(`/release/operations/groups/${id}`, { params: { projectId, rowVersion } }) }
export function listReleaseOperationGroupMembers(projectId: number, groupId: number) { return http.get<ApiResponse<ReleaseGroupMemberDto[]>>(`/release/operations/groups/${groupId}/members`, { params: { projectId } }) }
export function addReleaseOperationGroupMember(projectId: number, groupId: number, projectMemberId: number) { return http.post<ApiResponse<ReleaseGroupMemberDto>>(`/release/operations/groups/${groupId}/members`, undefined, { params: { projectId, projectMemberId } }) }
export function deleteReleaseOperationGroupMember(projectId: number, groupId: number, projectMemberId: number) { return http.delete<ApiResponse<void>>(`/release/operations/groups/${groupId}/members/${projectMemberId}`, { params: { projectId } }) }
export function listReleaseOperationMemberOptions(projectId: number) { return http.get<ApiResponse<ReleaseMemberOptionDto[]>>('/release/operations/members', { params: { projectId } }) }
