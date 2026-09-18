import http, { withProjectContext } from './http'
import type { ApiResponse } from '../types/auth'
import type { Project, ProjectMember, ProjectOptions, ProjectPlan, ProjectPlanGroup, ProjectPlanGroupPayload, ProjectRisk, ProjectRiskComment, ProjectRole, ProjectUserOption, ProjectOrganization, ProjectStage, ProjectReleaseCalendar, ProjectAnnouncement, ProjectReleaseCalendarToneKey } from '../types/project'

export function getProjectWorkbench() { return http.get<ApiResponse<Project[]>>('/project/workbench', withProjectContext(null)) }
export function getProject(id: number) { return http.get<ApiResponse<Project>>(`/project/${id}`, withProjectContext(id)) }
export function createProject(payload: Record<string, unknown>) { return http.post<ApiResponse<Project>>('/project', payload) }
export function updateProject(id: number, payload: Record<string, unknown>) { return http.put<ApiResponse<Project>>(`/project/${id}`, payload, withProjectContext(id)) }
export function updateProjectSettings(id: number, payload: Record<string, unknown>) { return http.put<ApiResponse<Project>>(`/project/${id}/settings`, payload, withProjectContext(id)) }
export function getProjectReleaseCalendar(id: number, month: string) { return http.get<ApiResponse<ProjectReleaseCalendar[]>>(`/project/${id}/release-calendar`, { ...withProjectContext(id), params: { month } }) }
export function createProjectReleaseCalendar(id: number, payload: { title: string; release_date: string; remark?: string; theme_key: ProjectReleaseCalendarToneKey }) { return http.post<ApiResponse<ProjectReleaseCalendar>>(`/project/${id}/release-calendar`, payload, withProjectContext(id)) }
export function updateProjectReleaseCalendar(id: number, calendarId: number, payload: { title: string; release_date: string; remark?: string; theme_key: ProjectReleaseCalendarToneKey; row_version: number }) { return http.put<ApiResponse<ProjectReleaseCalendar>>(`/project/${id}/release-calendar/${calendarId}`, payload, withProjectContext(id)) }
export function deleteProjectReleaseCalendar(id: number, calendarId: number, rowVersion: number) { return http.delete<ApiResponse<void>>(`/project/${id}/release-calendar/${calendarId}`, { ...withProjectContext(id), params: { rowVersion } }) }
export function getProjectAnnouncements(id: number, stageCode?: string) { return http.get<ApiResponse<ProjectAnnouncement[]>>(`/project/${id}/announcements`, { ...withProjectContext(id), params: stageCode ? { stageCode } : undefined }) }
export function getCurrentProjectAnnouncements(id: number) { return http.get<ApiResponse<ProjectAnnouncement[]>>(`/project/${id}/announcements/current`, withProjectContext(id)) }
export function createProjectAnnouncement(id: number, payload: { stage_code: string; title: string; content_html: string; pinned: boolean }) { return http.post<ApiResponse<ProjectAnnouncement>>(`/project/${id}/announcements`, payload, withProjectContext(id)) }
export function updateProjectAnnouncement(id: number, announcementId: number, payload: { stage_code: string; title: string; content_html: string; pinned: boolean; row_version: number }) { return http.put<ApiResponse<ProjectAnnouncement>>(`/project/${id}/announcements/${announcementId}`, payload, withProjectContext(id)) }
export function deleteProjectAnnouncement(id: number, announcementId: number, rowVersion: number) { return http.delete<ApiResponse<void>>(`/project/${id}/announcements/${announcementId}`, { ...withProjectContext(id), params: { rowVersion } }) }
export function getProjectStages(id: number) { return http.get<ApiResponse<ProjectStage[]>>(`/project/${id}/stages`, withProjectContext(id)) }
export function createProjectStage(id: number, payload: { stage_name: string; sort_no?: number }) { return http.post<ApiResponse<ProjectStage>>(`/project/${id}/stages`, payload, withProjectContext(id)) }
export function updateProjectStage(id: number, stageId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectStage>>(`/project/${id}/stages/${stageId}`, payload, withProjectContext(id)) }
export function deleteProjectStage(id: number, stageId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/stages/${stageId}`, withProjectContext(id)) }
export function deleteProject(id: number) { return http.delete<ApiResponse<void>>(`/project/${id}`, withProjectContext(id)) }
export function getProjectUserOptions(keyword?: string) { return http.get<ApiResponse<ProjectUserOption[]>>('/project/options/users', { params: { keyword } }) }
export function getProjectOptions() { return http.get<ApiResponse<ProjectOptions>>('/project/options') }
export function createProjectPlan(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectPlan>>(`/project/${id}/plans`, payload, withProjectContext(id)) }
export function updateProjectPlan(id: number, planId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectPlan>>(`/project/${id}/plans/${planId}`, payload, withProjectContext(id)) }
export function deleteProjectPlan(id: number, planId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/plans/${planId}`, withProjectContext(id)) }
export function getProjectPlanGroups(id: number) { return http.get<ApiResponse<ProjectPlanGroup[]>>(`/project/${id}/plan-groups`, withProjectContext(id)) }
export function createProjectPlanGroup(id: number, payload: ProjectPlanGroupPayload) { return http.post<ApiResponse<ProjectPlanGroup>>(`/project/${id}/plan-groups`, payload, withProjectContext(id)) }
export function updateProjectPlanGroup(id: number, groupId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectPlanGroup>>(`/project/${id}/plan-groups/${groupId}`, payload, withProjectContext(id)) }
export function deleteProjectPlanGroup(id: number, groupId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/plan-groups/${groupId}`, withProjectContext(id)) }
export function moveProjectPlanToGroup(id: number, planId: number, groupId: number | null) { return http.put<ApiResponse<void>>(`/project/${id}/plans/${planId}/group`, { group_id: groupId }, withProjectContext(id)) }
export function createProjectRisk(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectRisk>>(`/project/${id}/risks`, payload, withProjectContext(id)) }
export function updateProjectRisk(id: number, riskId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectRisk>>(`/project/${id}/risks/${riskId}`, payload, withProjectContext(id)) }
export function deleteProjectRisk(id: number, riskId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/risks/${riskId}`, withProjectContext(id)) }
export function getProjectRiskComments(id: number, riskId: number) { return http.get<ApiResponse<ProjectRiskComment[]>>(`/project/${id}/risks/${riskId}/comments`, withProjectContext(id)) }
export function createProjectRiskComment(id: number, riskId: number, payload: { comment_text: string }) { return http.post<ApiResponse<ProjectRiskComment>>(`/project/${id}/risks/${riskId}/comments`, payload, withProjectContext(id)) }
export function createProjectMember(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectMember>>(`/project/${id}/members`, payload, withProjectContext(id)) }
export function updateProjectMember(id: number, memberId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectMember>>(`/project/${id}/members/${memberId}`, payload, withProjectContext(id)) }
export function deleteProjectMember(id: number, memberId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/members/${memberId}`, withProjectContext(id)) }
export function getProjectOrganizations(id: number) { return http.get<ApiResponse<ProjectOrganization[]>>(`/project/${id}/organizations`, withProjectContext(id)) }
export function createProjectOrganization(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectOrganization>>(`/project/${id}/organizations`, payload, withProjectContext(id)) }
export function updateProjectOrganization(id: number, organizationId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectOrganization>>(`/project/${id}/organizations/${organizationId}`, payload, withProjectContext(id)) }
export function deleteProjectOrganization(id: number, organizationId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/organizations/${organizationId}`, withProjectContext(id)) }
export function createProjectRole(id: number, payload: Record<string, unknown>) { return http.post<ApiResponse<ProjectRole>>(`/project/${id}/roles`, payload, withProjectContext(id)) }
export function updateProjectRole(id: number, roleId: number, payload: Record<string, unknown>) { return http.put<ApiResponse<ProjectRole>>(`/project/${id}/roles/${roleId}`, payload, withProjectContext(id)) }
export function deleteProjectRole(id: number, roleId: number) { return http.delete<ApiResponse<void>>(`/project/${id}/roles/${roleId}`, withProjectContext(id)) }
export function getProjectRolePermissions(id: number, roleId: number) { return http.get<ApiResponse<{ menus: import('../types/system').PermissionMenu[]; permissionIds: number[] }>>(`/project/${id}/roles/${roleId}/permissions`, withProjectContext(id)) }
export function saveProjectRolePermissions(id: number, roleId: number, permissionIds: number[]) { return http.put<ApiResponse<void>>(`/project/${id}/roles/${roleId}/permissions`, { permissionIds }, withProjectContext(id)) }
