import http from './http'
import type { ApiResponse } from '../types/auth'
import type {
  AddProjectMemberCommand,
  ChangeProjectMemberRoleCommand,
  CreateProjectCommand,
  ProjectListQuery,
  ProjectMemberCandidate,
  ProjectMembership,
  ProjectPage,
  ProjectSummary,
  UpdateProjectCommand
} from '../types/project-context'

export function listProjects(params: ProjectListQuery) {
  return http.get<ApiResponse<ProjectPage<ProjectSummary>>>('/projects', { params })
}

export function listAvailableProjects() {
  return http.get<ApiResponse<ProjectSummary[]>>('/projects/available')
}

export function getProject(projectId: number) {
  return http.get<ApiResponse<ProjectSummary>>(`/projects/${projectId}`)
}

export function createProject(command: CreateProjectCommand) {
  return http.post<ApiResponse<ProjectSummary>>('/projects', command)
}

export function updateProject(projectId: number, command: UpdateProjectCommand) {
  return http.put<ApiResponse<ProjectSummary>>(`/projects/${projectId}`, command)
}

export function archiveProject(projectId: number, version: number) {
  return http.post<ApiResponse<ProjectSummary>>(`/projects/${projectId}/archive`, { version })
}

export function restoreProject(projectId: number, version: number) {
  return http.post<ApiResponse<ProjectSummary>>(`/projects/${projectId}/restore`, { version })
}

export function listProjectMembers(projectId: number) {
  return http.get<ApiResponse<ProjectMembership[]>>(`/projects/${projectId}/members`)
}

export function listProjectMemberCandidates(params: { page: number; size: number; keyword?: string }) {
  return http.get<ApiResponse<ProjectPage<ProjectMemberCandidate>>>('/projects/member-candidates', { params })
}

export function addProjectMember(projectId: number, command: AddProjectMemberCommand) {
  return http.post<ApiResponse<ProjectSummary>>(`/projects/${projectId}/members`, command)
}

export function changeProjectMemberRole(
  projectId: number,
  userId: number,
  command: ChangeProjectMemberRoleCommand
) {
  return http.patch<ApiResponse<ProjectSummary>>(`/projects/${projectId}/members/${userId}`, command)
}

export function removeProjectMember(projectId: number, userId: number, version: number) {
  return http.delete<ApiResponse<ProjectSummary>>(`/projects/${projectId}/members/${userId}`, { params: { version } })
}

export function transferProjectOwner(projectId: number, newOwnerUserId: number, version: number) {
  return http.post<ApiResponse<ProjectSummary>>(`/projects/${projectId}/owner-transfer`, { newOwnerUserId, version })
}
