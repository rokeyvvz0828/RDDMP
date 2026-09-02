import http from './http'
import type { ApiResponse } from '../types/auth'

export type WorkflowNodeType = 'START' | 'APPROVAL' | 'CC' | 'CONDITION' | 'PARALLEL_SPLIT' | 'PARALLEL_JOIN' | 'END'
export type WorkflowAssigneeType = 'USER' | 'ROLE' | 'PROJECT_MEMBER' | 'PROJECT_ROLE' | 'TEMPLATE_PLACEHOLDER' | 'STARTER' | 'ORG_OWNER' | 'FORM_FIELD' | 'EXPRESSION'
export type WorkflowScopeType = 'TEMPLATE' | 'PROJECT'
export type WorkflowApprovalMode = 'ANY' | 'ALL' | 'PERCENT'

export interface WorkflowNodeConfig {
  assigneeType?: WorkflowAssigneeType
  assigneeIds?: number[]
  userIds?: number[]
  templatePlaceholder?: boolean
  mode?: WorkflowApprovalMode
  percentage?: number
  emptyAssigneeAction?: 'ERROR' | 'WAIT'
  fieldName?: string
  expression?: string
  multiInstance?: boolean
  collectionVariable?: string
  elementVariable?: string
  sequential?: boolean
  defaultEdgeId?: string
  actionPolicy?: { allowedActions: WorkflowTaskAction[] }
  signatureRequired?: boolean
  [key: string]: unknown
}

export interface WorkflowPosition { x: number; y: number }
export interface WorkflowNodeModel { id: string; type: WorkflowNodeType; label: string; position: WorkflowPosition; config: WorkflowNodeConfig }
export interface WorkflowEdgeModel { id: string; source: string; target: string; sourceHandle?: string | null; targetHandle?: string | null; label?: string | null; condition?: string | null; default?: boolean }
export interface WorkflowVariableModel { name: string; type: string; required?: boolean; defaultValue?: unknown; scope?: 'PROCESS' | 'TASK' }
export interface WorkflowFormBindingModel { nodeId: string; fieldName: string; variableName: string; required?: boolean }
export interface WorkflowGraph { schemaVersion: 2; nodes: WorkflowNodeModel[]; edges: WorkflowEdgeModel[]; variables: WorkflowVariableModel[]; formBindings: WorkflowFormBindingModel[] }
export interface WorkflowDefinition { id: number; code: string; name: string; scope_type: WorkflowScopeType; project_id?: number; status: string; current_version: number; model_schema_version?: number; requires_configuration?: boolean; created_at?: string }
export interface WorkflowPage<T> { records: T[]; total: number; page: number; size: number }
export interface WorkflowPageQuery { page: number; size: number; projectRef?: string; scopeType?: WorkflowScopeType }
export interface WorkflowMonitorQuery extends WorkflowPageQuery { businessKey?: string; definitionKeyword?: string; status?: string; starterKeyword?: string; createdFrom?: string; createdTo?: string }
export interface WorkflowDefinitionDetail extends WorkflowDefinition { version_no: number; definition_json: string | WorkflowGraph }
export interface WorkflowDefinitionVersion { version_no: number; status: string; model_schema_version?: number; created_at?: string; definition_json?: string | WorkflowGraph }
export interface WorkflowDefinitionEvent { id: number; event_type: string; version_no?: number; operator_id?: number; operator_name?: string; reason?: string; payload_json?: string; created_at?: string }
export interface WorkflowBusinessProjection { business_type?: string; business_title?: string; business_round?: number; project_ref?: string; project_name?: string; action_path?: string; starter_name?: string }
export interface WorkflowTask extends WorkflowBusinessProjection { id: number; instance_id: number; task_key: string; node_id?: string; node_name?: string; task_type: 'APPROVAL' | 'ADD_SIGN' | 'CC'; task_group_key?: string; status: string; assignee_name?: string; business_key: string; instance_status?: string; signature_required?: boolean; created_at?: string }
export interface WorkflowInstance { id: number; definition_id: number; definition_name?: string; version_no: number; business_key: string; project_ref?: string; project_name?: string; status: string; starter_id: number; starter_name?: string; current_node?: string; created_at?: string }
export interface WorkflowProjectOptions {
  project_id: number
  project_ref: string
  project_name: string
  members: { id: number; username: string; display_name: string }[]
  roles: { id: number; role_code: string; role_name: string }[]
}
export interface WorkflowNodeState { id: number; node_id?: string; task_key: string; node_name?: string; task_type: string; assignee_name?: string; status: string; comment?: string; created_at?: string; completed_at?: string }
export interface WorkflowSignatureItem { id: number; task_id: number; business_round: number; action_code: string; comment_text?: string; data_digest: string; signer_id: number; signer_username: string; signer_display_name: string; signed_at?: string }
export interface WorkflowMonitorDetail { instance: WorkflowInstance & { definition_code?: string }; definition_json: string | WorkflowGraph; node_states: WorkflowNodeState[]; timeline: WorkflowAuditEvent[]; signatures?: WorkflowSignatureItem[] }
export interface WorkflowAuditEvent { id: number; event_type: string; operator_id?: number; operator_name?: string; reason?: string; payload_json?: string; created_at?: string }
export interface WorkflowDoneItem extends WorkflowBusinessProjection { id: number; instance_id: number; task_id: number; action_code: WorkflowTaskAction; comment?: string; created_at?: string; node_id?: string; task_key?: string; node_name?: string; task_type?: string; business_key: string; instance_status: string; definition_name?: string }
export type WorkflowTaskAction = 'APPROVE' | 'REJECT' | 'RETURN' | 'ADD_SIGN' | 'CC' | 'TRANSFER' | 'DELEGATE'
export interface WorkflowTaskContext extends WorkflowBusinessProjection {
  task_id: number
  instance_id: number
  business_key: string
  action_path: string
  task_key: string
  node_id?: string
  node_name?: string
  task_type: 'APPROVAL' | 'ADD_SIGN' | 'CC'
  task_status: string
  instance_status: string
  allowed_actions: WorkflowTaskAction[]
  signature_required: boolean
  actionable: boolean
}

export function defaultWorkflowGraph(): WorkflowGraph {
  return {
    schemaVersion: 2,
    variables: [],
    formBindings: [],
    nodes: [
      { id: 'start', type: 'START', label: '发起', position: { x: 80, y: 160 }, config: {} },
      { id: 'approval-1', type: 'APPROVAL', label: '部门审批', position: { x: 360, y: 160 }, config: { assigneeType: 'USER', assigneeIds: [], mode: 'ANY', emptyAssigneeAction: 'ERROR', actionPolicy: { allowedActions: ['APPROVE', 'REJECT', 'ADD_SIGN', 'CC'] } } },
      { id: 'end', type: 'END', label: '结束', position: { x: 680, y: 160 }, config: {} }
    ],
    edges: [
      { id: 'edge-start-approval', source: 'start', target: 'approval-1', label: null, condition: null, default: false },
      { id: 'edge-approval-end', source: 'approval-1', target: 'end', label: null, condition: null, default: false }
    ]
  }
}

export function listWorkflowDefinitions(params: WorkflowPageQuery) { return http.get<ApiResponse<WorkflowPage<WorkflowDefinition>>>('/workflows/definitions', { params }) }
export function getWorkflowDefinition(id: number) { return http.get<ApiResponse<WorkflowDefinitionDetail>>(`/workflows/definitions/${id}`) }
export function createWorkflowDefinition(data: { code: string; name: string; definitionJson: string; scopeType: WorkflowScopeType; projectRef?: string }) { return http.post<ApiResponse<WorkflowDefinition>>('/workflows/definitions', data) }
export function updateWorkflowDefinition(id: number, data: { code: string; name: string; definitionJson: string }) { return http.put<ApiResponse<void>>('/workflows/definitions/' + id, data) }
export function deleteWorkflowDefinition(id: number) { return http.delete<ApiResponse<void>>(`/workflows/definitions/${id}`) }
export function archiveWorkflowDefinition(id: number, reason: string) { return http.post<ApiResponse<void>>(`/workflows/definitions/${id}/archive`, { reason }) }
export function restoreWorkflowDefinition(id: number, reason: string) { return http.post<ApiResponse<void>>(`/workflows/definitions/${id}/restore`, { reason }) }
export function listWorkflowDefinitionVersions(id: number) { return http.get<ApiResponse<WorkflowDefinitionVersion[]>>(`/workflows/definitions/${id}/versions`) }
export function getWorkflowDefinitionVersion(id: number, versionNo: number) { return http.get<ApiResponse<WorkflowDefinitionVersion>>(`/workflows/definitions/${id}/versions/${versionNo}`) }
export function listWorkflowDefinitionEvents(id: number) { return http.get<ApiResponse<WorkflowDefinitionEvent[]>>(`/workflows/definitions/${id}/events`) }
export function publishWorkflowDefinition(id: number) { return http.post<ApiResponse<void>>(`/workflows/definitions/${id}/publish`) }
export function unpublishWorkflowDefinition(id: number) { return http.post<ApiResponse<void>>(`/workflows/definitions/${id}/unpublish`) }
export function startWorkflow(definitionId: number, businessKey: string, variables: Record<string, unknown> = {}) { return http.post<ApiResponse<Record<string, unknown>>>('/workflows/instances', { definitionId, businessKey, ...variables }) }
export function listWorkflowInstances(params: WorkflowMonitorQuery) { return http.get<ApiResponse<WorkflowPage<WorkflowInstance>>>('/workflows/instances', { params }) }
export function getWorkflowProjectOptions(projectRef: string) { return http.get<ApiResponse<WorkflowProjectOptions>>('/workflows/project-options', { params: { projectRef } }) }
export function getWorkflowInstanceDetail(id: number) { return http.get<ApiResponse<WorkflowMonitorDetail>>('/workflows/instances/' + id + '/detail') }
export function deleteWorkflowInstance(id: number) { return http.delete<ApiResponse<void>>('/workflows/instances/' + id) }
export function listWorkflowDone(params: WorkflowPageQuery) { return http.get<ApiResponse<WorkflowPage<WorkflowDoneItem>>>('/workflows/done', { params }) }
export function getWorkflowTimeline(id: number) { return http.get<ApiResponse<WorkflowAuditEvent[]>>(`/workflows/instances/${id}/timeline`) }
export function listWorkflowInbox(params: WorkflowPageQuery) { return http.get<ApiResponse<WorkflowPage<WorkflowTask>>>('/workflows/inbox', { params }) }
export function listWorkflowSubmitted(params: WorkflowPageQuery) { return http.get<ApiResponse<WorkflowPage<WorkflowTask & { current_assignees?: string; task_count?: number; instance_status?: string; definition_name?: string }>>>('/workflows/submitted', { params }) }
export function getWorkflowTaskContext(id: number) { return http.get<ApiResponse<WorkflowTaskContext>>(`/workflows/tasks/${id}/context`) }
export function getCurrentWorkflowTaskContext(businessType: string, businessKey: string) { return http.get<ApiResponse<WorkflowTaskContext | null>>('/workflows/tasks/current-context', { params: { businessType, businessKey } }) }
export function decideWorkflowTask(id: number, action: WorkflowTaskAction, comment: string, options?: { targetUserId?: number; ccUserIds?: number[]; signatureConfirmed?: boolean }) {
  return http.post<ApiResponse<void>>(`/workflows/tasks/${id}/decision`, {
    action,
    comment,
    targetUserId: options?.targetUserId,
    ccUserIds: options?.ccUserIds,
    signatureConfirmed: options?.signatureConfirmed
  }, { timeout: 30_000 })
}
