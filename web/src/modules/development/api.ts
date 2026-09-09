import http from '../../api/http'
import type { ApiResponse } from '../../types/auth'
import type { CreateTaskInput, UpdateTaskInput, DevelopmentTask, Page, PendingRequirement, SystemRef, UserRef, WorkItemInput, WorkItem, WorkItemPage, TaskStage, StageInput, TaskChange } from './types'

type Query = Record<string, string | number | null | undefined>
export const developmentApi = {
  tasks: (params: Query) => http.get<ApiResponse<Page<DevelopmentTask>>>('/development/tasks', { params }).then(r => r.data.data),
  task: (id: string) => http.get<ApiResponse<DevelopmentTask>>(`/development/tasks/${encodeURIComponent(id)}`).then(r => r.data.data),
  createTask: (input: CreateTaskInput) => http.post<ApiResponse<DevelopmentTask>>('/development/tasks', input).then(r => r.data.data),
  updateTask: (id: string, input: UpdateTaskInput) => http.put<ApiResponse<DevelopmentTask>>(`/development/tasks/${encodeURIComponent(id)}`, input).then(r => r.data.data),
  pending: (params: Query) => http.get<ApiResponse<Page<PendingRequirement>>>('/development/pending-requirements', { params }).then(r => r.data.data),
  systems: (params: Query) => http.get<ApiResponse<Page<SystemRef>>>('/development/options/systems', { params }).then(r => r.data.data),
  users: (params: Query) => http.get<ApiResponse<Page<UserRef>>>('/development/options/users', { params }).then(r => r.data.data),
  workItems: (params: Query) => http.get<ApiResponse<WorkItemPage>>('/development/work-items', { params }).then(r => r.data.data),
  workItem: (id: string) => http.get<ApiResponse<WorkItem>>(`/development/work-items/${encodeURIComponent(id)}`).then(r => r.data.data),
  createWorkItem: (input: WorkItemInput) => http.post<ApiResponse<WorkItem>>('/development/work-items', input).then(r => r.data.data),
  updateWorkItem: (id: string, input: WorkItemInput) => http.put<ApiResponse<WorkItem>>(`/development/work-items/${encodeURIComponent(id)}`, input).then(r => r.data.data),
  workItemAction: (id: string, action: string, rowVersion: number, reason?: string) => http.post<ApiResponse<WorkItem>>(`/development/work-items/${encodeURIComponent(id)}/actions`, { action, rowVersion, reason }).then(r => r.data.data),
  taskAction: (id: string, action: string, rowVersion: number, reason?: string) => http.post<ApiResponse<DevelopmentTask>>(`/development/tasks/${encodeURIComponent(id)}/actions`, { action, rowVersion, reason }).then(r => r.data.data),
  stages: (id: string) => http.get<ApiResponse<TaskStage>>(`/development/tasks/${encodeURIComponent(id)}/stages`).then(r => r.data.data),
  saveStages: (id: string, input: StageInput) => http.put<ApiResponse<TaskStage>>(`/development/tasks/${encodeURIComponent(id)}/stages`, input).then(r => r.data.data),
  changes: (id: string, page = 1) => http.get<ApiResponse<Page<TaskChange>>>(`/development/tasks/${encodeURIComponent(id)}/changes`, { params: { page, size: 20 } }).then(r => r.data.data)
}
