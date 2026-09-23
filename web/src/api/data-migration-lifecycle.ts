import http from './http'
import type { ApiResponse } from '../types/auth'
import type { LifecycleComponentOption, LifecycleMemberOption, LifecyclePage, LifecycleRoleOption } from '../types/data-migration-lifecycle'

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
