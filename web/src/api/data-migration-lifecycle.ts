import http from './http'
import type { ApiResponse } from '../types/auth'
import type {
  LifecycleActivityView,
  LifecycleComponentOption,
  LifecycleMemberOption,
  LifecyclePage,
  LifecycleProcessInput,
  LifecycleProcessView,
  LifecyclePublishStatus,
  LifecycleRoleOption,
  LifecycleStageOption,
  LifecycleTopicCandidate,
  LifecycleTopologyInput,
  LifecycleTopologyView
} from '../types/data-migration-lifecycle'

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

export function listLifecycleStages() {
  return http.get<ApiResponse<LifecycleStageOption[]>>('/data-migration-lifecycle/options/stages')
}

/** ===== 活动管理（基线第 9 章，T3） ===== */

export function listLifecycleActivities(params?: Record<string, unknown>) {
  return http.get<ApiResponse<LifecyclePage<LifecycleActivityView>>>('/data-migration-lifecycle/activities', { params })
}

export function getLifecycleActivity(id: number) {
  return http.get<ApiResponse<LifecycleActivityView>>(`/data-migration-lifecycle/activities/${id}`)
}

export function createLifecycleActivity(body: Record<string, unknown>) {
  return http.post<ApiResponse<LifecycleActivityView>>('/data-migration-lifecycle/activities', body)
}

export function updateLifecycleActivity(id: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<LifecycleActivityView>>(`/data-migration-lifecycle/activities/${id}`, body)
}

export function setLifecycleActivityStatus(id: number, status: string) {
  return http.put<ApiResponse<LifecycleActivityView>>(`/data-migration-lifecycle/activities/${id}/status`, { status })
}

export function obsoleteLifecycleActivity(id: number) {
  return http.post<ApiResponse<LifecycleActivityView>>(`/data-migration-lifecycle/activities/${id}/obsolete`)
}

export function getLifecycleTopicCandidates(id: number) {
  return http.get<ApiResponse<LifecycleTopicCandidate[]>>(`/data-migration-lifecycle/activities/${id}/topic-candidates`)
}

export function getLifecycleTopicMembers(id: number) {
  return http.get<ApiResponse<LifecycleTopicCandidate[]>>(`/data-migration-lifecycle/activities/${id}/topic-members`)
}

export function addLifecycleTopicMember(id: number, memberActivityId: number) {
  return http.post<ApiResponse<void>>(`/data-migration-lifecycle/activities/${id}/topic-members`, { memberActivityId })
}

export function removeLifecycleTopicMember(id: number, memberActivityId: number) {
  return http.delete<ApiResponse<void>>(`/data-migration-lifecycle/activities/${id}/topic-members/${memberActivityId}`)
}

export function getLifecycleProcesses(id: number) {
  return http.get<ApiResponse<LifecycleProcessView[]>>(`/data-migration-lifecycle/activities/${id}/processes`)
}

export function saveLifecycleProcesses(id: number, inputs: LifecycleProcessInput[]) {
  return http.put<ApiResponse<LifecycleProcessView[]>>(`/data-migration-lifecycle/activities/${id}/processes`, inputs)
}

export function getLifecycleTopology(id: number) {
  return http.get<ApiResponse<LifecycleTopologyView>>(`/data-migration-lifecycle/activities/${id}/topology`)
}

export function saveLifecycleTopology(id: number, input: LifecycleTopologyInput) {
  return http.put<ApiResponse<LifecycleTopologyView>>(`/data-migration-lifecycle/activities/${id}/topology`, input)
}

export function publishLifecycleVersion(id: number) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/activities/${id}/publish`)
}

export function getLifecyclePublishStatus(id: number) {
  return http.get<ApiResponse<LifecyclePublishStatus>>(`/data-migration-lifecycle/activities/${id}/publish-status`)
}

export function getLifecycleSnapshot(id: number) {
  return http.get<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/activities/${id}/snapshot`)
}

export function exportLifecycleTemplate(id: number) {
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration-lifecycle/activities/${id}/template/export`)
}

export function importLifecycleTemplate(packageJson: string) {
  return http.post<ApiResponse<Record<string, unknown>>>('/data-migration-lifecycle/activities/template/import', { packageJson })
}
