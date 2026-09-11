import type { ApiResponse } from './auth'

export type SystemResource = 'users' | 'roles' | 'orgs' | 'menus' | 'params' | 'param-categories'

export interface SystemPage<T = Record<string, unknown>> {
  records: T[]
  total: number
  page: number
  size: number
}

export type SystemRow = Record<string, unknown> & { id: number; status?: number }
export type SystemPageResponse = ApiResponse<SystemPage<SystemRow>>

export interface UserProfile {
  id: number
  username: string
  displayName: string
  orgId?: number
  orgName?: string | null
  avatarUrl?: string | null
  roles?: string[]
  status?: number
}

export interface OrgUserSummary extends UserProfile {
  orgId: number
  status: number
}

export interface OrgTreeNode {
  id: number
  parentId: number
  orgCode: string
  orgName: string
  sortNo: number
  status: number
  children: OrgTreeNode[]
  users: OrgUserSummary[]
}

export interface RoleOption { id: number; role_code: string; role_name: string }

export interface PermissionAction { id: number; action_code: string; permission_code: string; permission_name: string }

export interface PermissionMenu { id: number; parent_id: number; menu_name: string; menu_type: string; route_path?: string; permission_code?: string; icon?: string; sort_no: number; actions: PermissionAction[]; children?: PermissionMenu[] }

export interface PermissionRecord {
  id: number
  menu_id: number
  menu_name: string
  action_code: string
  permission_code: string
  permission_name: string
  status: number
  created_at?: string
  updated_at?: string
}

export interface PermissionPayload {
  menu_id: number
  action_code: string
  permission_code: string
  permission_name: string
  status: number
}

export interface OperationAuditRecord {
  id: number
  operatorId: number
  operatorName?: string
  operationCode: string
  moduleCode?: string
  moduleName?: string
  operationType?: string
  targetType?: string
  targetId?: string
  projectId?: number
  projectName?: string
  requestMethod?: string
  requestPath?: string
  success: boolean
  httpStatus?: number
  durationMs: number
  errorMessage?: string
  clientIp?: string
  userAgent?: string
  changedFields: string[]
  traceId?: string
  createdAt: string
}

export interface LoginAuditRecord {
  id: number
  username: string
  success: boolean
  failureReason?: string
  clientIp?: string
  userAgent?: string
  createdAt: string
}

export interface AuditProjectOption {
  id: number
  projectCode: string
  projectName: string
}

export interface AuditCapabilities {
  loginAudit: boolean
}
