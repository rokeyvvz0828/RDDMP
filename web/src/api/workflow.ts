import http from './http'
import type { ApiResponse } from '../types/auth'

export type WorkflowNodeType = 'START' | 'APPROVAL' | 'CC' | 'CONDITION' | 'PARALLEL_SPLIT' | 'PARALLEL_JOIN' | 'END'
export type WorkflowAssigneeType = 'USER' | 'ROLE' | 'STARTER' | 'ORG_OWNER' | 'FORM_FIELD' | 'EXPRESSION'
export type WorkflowApprovalMode = 'ANY' | 'ALL' | 'PERCENT'

export interface WorkflowNodeConfig {
  assigneeType?: WorkflowAssigneeType
  assigneeIds?: number[]
  userIds?: number[]
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
  [key: string]: unknown
}

export interface WorkflowPosition { x: number; y: number }

export interface WorkflowNodeModel {
  id: string
  type: WorkflowNodeType
  label: string
  position: WorkflowPosition
  config: WorkflowNodeConfig
}

export interface WorkflowEdgeModel {
  id: string
  source: string
  target: string
  label?: string | null
  condition?: string | null
  default?: boolean
}

export interface WorkflowVariableModel {
  name: string
  type: string
  required?: boolean
  defaultValue?: unknown
  scope?: 'PROCESS' | 'TASK'
}

export interface WorkflowFormBindingModel {
  nodeId: string
  fieldName: string
  variableName: string
  required?: boolean
}

export interface WorkflowGraph {
  schemaVersion: 2
  nodes: WorkflowNodeModel[]
  edges: WorkflowEdgeModel[]
  variables: WorkflowVariableModel[]
  formBindings: WorkflowFormBindingModel[]
}

export interface WorkflowDefinition {
  id: number
  code: string
  name: string
  status: string
  current_version: number
  model_schema_version?: number
  created_at?: string
}

export interface WorkflowTask {
  id: number
  instance_id: number
  task_key: string
  node_id?: string
  task_type: 'APPROVAL' | 'ADD_SIGN' | 'CC'
  task_group_key?: string
  status: string
  assignee_name?: string
  business_key: string
  instance_status?: string
  created_at?: string
}

export interface WorkflowInstance {
  id: number
  definition_id: number
  definition_name?: string
  version_no: number
  business_key: string
  status: string
  starter_id: number
  starter_name?: string
  created_at?: string
}

export interface WorkflowAuditEvent {
  id: number
  event_type: string
  operator_id?: number
  operator_name?: string
  reason?: string
  payload_json?: string
  created_at?: string
}

export type WorkflowTaskAction = 'APPROVE' | 'REJECT' | 'RETURN' | 'ADD_SIGN' | 'CC' | 'TRANSFER' | 'DELEGATE'

export function defaultWorkflowGraph(): WorkflowGraph {
  return {
    schemaVersion: 2,
    variables: [],
    formBindings: [],
    nodes: [
      { id: 'start', type: 'START', label: '发起', position: { x: 180, y: 60 }, config: {} },
      { id: 'approval-1', type: 'APPROVAL', label: '部门审批', position: { x: 180, y: 220 }, config: { assigneeType: 'USER', assigneeIds: [1], mode: 'ANY', emptyAssigneeAction: 'ERROR', actionPolicy: { allowedActions: ['APPROVE', 'REJECT', 'RETURN', 'ADD_SIGN', 'CC'] } } },
      { id: 'end', type: 'END', label: '结束', position: { x: 180, y: 380 }, config: {} }
    ],
    edges: [
      { id: 'edge-start-approval', source: 'start', target: 'approval-1' },
      { id: 'edge-approval-end', source: 'approval-1', target: 'end' }
    ]
  }
}

export function listWorkflowDefinitions() { return http.get<ApiResponse<WorkflowDefinition[]>>('/workflows/definitions') }
export function createWorkflowDefinition(data: { code: string; name: string; definitionJson: string }) { return http.post<ApiResponse<WorkflowDefinition>>('/workflows/definitions', data) }
export function publishWorkflowDefinition(id: number) { return http.post<ApiResponse<void>>(`/workflows/definitions/${id}/publish`) }
export function startWorkflow(definitionId: number, businessKey: string, variables: Record<string, unknown> = {}) { return http.post<ApiResponse<Record<string, unknown>>>('/workflows/instances', { definitionId, businessKey, ...variables }) }
export function listWorkflowInstances() { return http.get<ApiResponse<WorkflowInstance[]>>('/workflows/instances') }
export function getWorkflowTimeline(id: number) { return http.get<ApiResponse<WorkflowAuditEvent[]>>(`/workflows/instances/${id}/timeline`) }
export function listWorkflowInbox() { return http.get<ApiResponse<WorkflowTask[]>>('/workflows/inbox') }
export function decideWorkflowTask(id: number, action: WorkflowTaskAction, comment: string, options?: { targetUserId?: number; ccUserIds?: number[] }) {
  return http.post<ApiResponse<void>>(`/workflows/tasks/${id}/decision`, { action, comment, targetUserId: options?.targetUserId, ccUserIds: options?.ccUserIds })
}