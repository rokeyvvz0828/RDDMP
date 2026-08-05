import http from './http'
import type { ApiResponse } from '../types/auth'
import type { OrgTreeNode, PermissionMenu, RoleOption, SystemPage, SystemResource, SystemRow, UserProfile } from '../types/system'

export function listSystem(resource: SystemResource, params: { page: number; size: number; keyword?: string; orgId?: number; categoryId?: number }) {
  return http.get<ApiResponse<SystemPage<SystemRow>>>(`/system/${resource}`, { params })
}

export function createSystem(resource: SystemResource, data: Record<string, unknown>) {
  return http.post<ApiResponse<SystemRow>>(`/system/${resource}`, data)
}

export function updateSystem(resource: SystemResource, id: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<SystemRow>>(`/system/${resource}/${id}`, data)
}

export function updateSystemStatus(resource: SystemResource, id: number, value: number) {
  return http.patch<ApiResponse<void>>(`/system/${resource}/${id}/status`, null, { params: { value } })
}

export function deleteSystem(resource: SystemResource, id: number) {
  return http.delete<ApiResponse<void>>('/system/' + resource + '/' + id)
}
export function getOrgTree() {
  return http.get<ApiResponse<OrgTreeNode[]>>('/system/orgs/tree')
}

export function uploadUserAvatar(id: number, file: File) {
  const data = new FormData()
  data.append('file', file)
  return http.post<ApiResponse<SystemRow>>(`/system/users/${id}/avatar`, data, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function deleteUserAvatar(id: number) {
  return http.delete<ApiResponse<SystemRow>>(`/system/users/${id}/avatar`)
}

export function toUserProfile(row: SystemRow): UserProfile {
  return {
    id: row.id,
    username: String(row.username || ''),
    displayName: String(row.display_name || ''),
    orgId: Number(row.org_id || 0),
    orgName: row.org_name ? String(row.org_name) : null,
    avatarUrl: row.avatar_url ? String(row.avatar_url) : null,
    status: Number(row.status ?? 1)
  }
}

export function getRoleOptions() {
  return http.get<ApiResponse<RoleOption[]>>('/system/roles/options')
}

export function getUserRoles(userId: number) {
  return http.get<ApiResponse<number[]>>('/system/users/' + userId + '/roles')
}

export function saveUserRoles(userId: number, roleIds: number[]) {
  return http.put<ApiResponse<void>>('/system/users/' + userId + '/roles', { roleIds })
}

export function getPermissionCatalog() {
  return http.get<ApiResponse<{ menus: PermissionMenu[] }>>('/system/roles/permission-catalog')
}

export function getRolePermissions(roleId: number) {
  return http.get<ApiResponse<{ permissionIds: number[] }>>('/system/roles/' + roleId + '/permissions')
}

export function saveRolePermissions(roleId: number, permissionIds: number[]) {
  return http.put<ApiResponse<void>>('/system/roles/' + roleId + '/permissions', { permissionIds })
}
