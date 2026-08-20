import http from './http'
import type { ApiResponse } from '../types/auth'
import type { Project, ProjectMember, ProjectOptions, ProjectPlan, ProjectPlanGroup, ProjectPlanGroupPayload, ProjectRisk, ProjectRiskComment, ProjectRole, ProjectUserOption, ProjectOrganization } from '../types/project'

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
export function createProjectPlanGroup(id: number, payload: ProjectPlanGroupPayload) { return http.post<ApiResponse<ProjectPlanGroup>>(`/project/${id}/plan-groups`, payload) }
export function updateProjectPlanGroup(id: number, groupId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectPlanGroup>>(`/project/${id}/plan-groups/${groupId}`, payload) }
export function deleteProjectPlanGroup(id: number, groupId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/plan-groups/${groupId}`) }
export function moveProjectPlanToGroup(id: number, planId: number, groupId: number | null) { return http.put<ApiResponse<void>>(`/project/${id}/plans/${planId}/group`, { group_id: groupId }) }
export function createProjectRisk(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectRisk>>(`/project/${id}/risks`, payload) }
export function updateProjectRisk(id: number, riskId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectRisk>>(`/project/${id}/risks/${riskId}`, payload) }
export function deleteProjectRisk(id: number, riskId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/risks/${riskId}`) }
export function getProjectRiskComments(id: number, riskId: number) { return http.get<ApiResponse<ProjectRiskComment[]>>(`/project/${id}/risks/${riskId}/comments`) }
export function createProjectRiskComment(id: number, riskId: number, payload: { comment_text: string }) { return http.post<ApiResponse<ProjectRiskComment>>(`/project/${id}/risks/${riskId}/comments`, payload) }
export function createProjectMember(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectMember>>(`/project/${id}/members`, payload) }
export function updateProjectMember(id: number, memberId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectMember>>(`/project/${id}/members/${memberId}`, payload) }
export function deleteProjectMember(id: number, memberId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/members/${memberId}`) }
export function getProjectOrganizations(id: number) { return http.get<ApiResponse<ProjectOrganization[]>>(`/project/${id}/organizations`) }
export function createProjectOrganization(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectOrganization>>(`/project/${id}/organizations`, payload) }
export function updateProjectOrganization(id: number, organizationId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectOrganization>>(`/project/${id}/organizations/${organizationId}`, payload) }
export function deleteProjectOrganization(id: number, organizationId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/organizations/${organizationId}`) }
export function createProjectRole(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectRole>>(`/project/${id}/roles`, payload) }
export function updateProjectRole(id: number, roleId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectRole>>(`/project/${id}/roles/${roleId}`, payload) }
export function deleteProjectRole(id: number, roleId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/roles/${roleId}`) }
