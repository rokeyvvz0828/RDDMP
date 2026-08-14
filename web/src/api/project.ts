import http from './http'
import type { ApiResponse } from '../types/auth'
import type { Project, ProjectMember, ProjectOptions, ProjectPlan, ProjectPlanGroup, ProjectRole, ProjectUserOption } from '../types/project'

export function getProjectWorkbench() { return http.get<ApiResponse<Project[]>>('/project/workbench') }
export function getProject(id: number) { return http.get<ApiResponse<Project>>(`/project/${id}`) }
export function createProject(payload: Record<string, unknown>) { return http.post<ApiResponse<Project>>('/project', payload) }
export function updateProject(id: number, payload: Record<string, unknown>) { return http.put<ApiResponse<Project>>(`/project/${id}`, payload) }
export function updateProjectSettings(id: number, payload: Record<string, unknown>) { return http.put<ApiResponse<Project>>(`/project/${id}/settings`, payload) }
export function deleteProject(id: number) { return http.delete<ApiResponse<void>>(`/project/${id}`) }
export function getProjectUserOptions(keyword?: string) { return http.get<ApiResponse<ProjectUserOption[]>>('/project/options/users', { params: { keyword } }) }
export function getProjectOptions() { return http.get<ApiResponse<ProjectOptions>>('/project/options') }
export function createProjectPlan(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectPlan>>(`/project/${id}/plans`, payload) }
export function updateProjectPlan(id: number, planId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectPlan>>(`/project/${id}/plans/${planId}`, payload) }
export function deleteProjectPlan(id: number, planId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/plans/${planId}`) }
export function getProjectPlanGroups(id: number) { return http.get<ApiResponse<ProjectPlanGroup[]>>(`/project/${id}/plan-groups`) }
export function createProjectPlanGroup(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectPlanGroup>>(`/project/${id}/plan-groups`, payload) }
export function updateProjectPlanGroup(id: number, groupId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectPlanGroup>>(`/project/${id}/plan-groups/${groupId}`, payload) }
export function deleteProjectPlanGroup(id: number, groupId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/plan-groups/${groupId}`) }
export function moveProjectPlanToGroup(id: number, planId: number, groupId: number | null) { return http.put<ApiResponse<void>>(`/project/${id}/plans/${planId}/group`, { group_id: groupId }) }
export function createProjectMember(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectMember>>(`/project/${id}/members`, payload) }
export function updateProjectMember(id: number, memberId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectMember>>(`/project/${id}/members/${memberId}`, payload) }
export function deleteProjectMember(id: number, memberId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/members/${memberId}`) }
export function createProjectRole(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectRole>>(`/project/${id}/roles`, payload) }
export function updateProjectRole(id: number, roleId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectRole>>(`/project/${id}/roles/${roleId}`, payload) }
export function deleteProjectRole(id: number, roleId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/roles/${roleId}`) }
