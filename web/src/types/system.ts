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

export interface PermissionMenu { id: number; parent_id: number; menu_name: string; menu_type: string; route_path?: string; permission_code?: string; icon?: string; module_key?: string; sort_no: number; actions: PermissionAction[]; children?: PermissionMenu[] }
