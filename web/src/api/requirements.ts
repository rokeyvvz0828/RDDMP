import http from './http'
import type { ApiResponse } from '../types/auth'
import type {
  ImportBatch,
  ImportPreviewReport,
  LegacyRequirement,
  PageEnvelope,
  ProjectMember,
  RequirementBaseline,
  RequirementDifference,
  RequirementEnums,
  RequirementProject,
  RequirementSystem,
  BaselineItem,
  ChangeLogRow,
  StageLogRow
} from '../types/requirements'

export function fetchRequirementEnums() {
  return http.get<ApiResponse<RequirementEnums>>('/requirements/enums')
}

export function listProjects(keyword?: string) {
  return http.get<ApiResponse<RequirementProject[]>>('/requirements/projects', { params: { keyword } })
}

export function getProject(id: number) {
  return http.get<ApiResponse<RequirementProject>>(`/requirements/projects/${id}`)
}

export function createProject(data: Record<string, unknown>) {
  return http.post<ApiResponse<RequirementProject>>('/requirements/projects', data)
}

export function updateProject(id: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<RequirementProject>>(`/requirements/projects/${id}`, data)
}

export function deleteProject(id: number) {
  return http.delete<ApiResponse<void>>(`/requirements/projects/${id}`)
}

export function listProjectMembers(projectId: number) {
  return http.get<ApiResponse<ProjectMember[]>>(`/requirements/projects/${projectId}/members`)
}

export function addProjectMember(projectId: number, data: { userId: number; memberRole?: string }) {
  return http.post<ApiResponse<ProjectMember>>(`/requirements/projects/${projectId}/members`, data)
}

export function removeProjectMember(memberId: number) {
  return http.delete<ApiResponse<void>>(`/requirements/project-members/${memberId}`)
}

export function listDifferences(params: {
  projectId: number
  reviewStatus?: string
  devStatus?: string
  testStatus?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PageEnvelope<RequirementDifference>>>('/requirements/differences', { params })
}

export function getDifference(id: number) {
  return http.get<ApiResponse<RequirementDifference>>(`/requirements/differences/${id}`)
}

export function createDifference(projectId: number, data: Record<string, unknown>) {
  return http.post<ApiResponse<RequirementDifference>>('/requirements/differences', data, { params: { projectId } })
}

export function updateDifference(id: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<RequirementDifference>>(`/requirements/differences/${id}`, data)
}

export function deleteDifference(id: number) {
  return http.delete<ApiResponse<void>>(`/requirements/differences/${id}`)
}

export function submitReview(id: number) {
  return http.post<ApiResponse<RequirementDifference>>(`/requirements/differences/${id}/submit-review`)
}

export function reviewResult(id: number, data: { decision: 'APPROVE' | 'RETURN'; comment?: string }) {
  return http.post<ApiResponse<RequirementDifference>>(`/requirements/differences/${id}/review-result`, data)
}

export function differenceChanges(id: number) {
  return http.get<ApiResponse<ChangeLogRow[]>>(`/requirements/differences/${id}/changes`)
}

export function listBaselines(projectId: number) {
  return http.get<ApiResponse<RequirementBaseline[]>>('/requirements/baselines', { params: { projectId } })
}

export function createBaseline(projectId: number, remark?: string) {
  return http.post<ApiResponse<{ id: number; baseline_no: string; difference_count: number }>>(`/requirements/projects/${projectId}/baseline`, { remark })
}

export function listBaselineItems(baselineId: number) {
  return http.get<ApiResponse<BaselineItem[]>>(`/requirements/baselines/${baselineId}/items`)
}

export function listImportBatches() {
  return http.get<ApiResponse<ImportBatch[]>>('/requirements/imports')
}

export function previewImport(bizType: string, projectId: number | null, file: File) {
  const data = new FormData()
  data.append('bizType', bizType)
  if (projectId) data.append('projectId', String(projectId))
  data.append('file', file)
  return http.post<ApiResponse<ImportPreviewReport>>('/requirements/imports/preview', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60_000
  })
}

export function confirmImport(body: {
  bizType: string
  projectId?: number | null
  fileName?: string
  rows: Array<Record<string, unknown>>
}) {
  return http.post<ApiResponse<{ batchId: number; totalRows: number; successRows: number }>>('/requirements/imports/confirm', body)
}

export async function downloadTemplate(bizType: string) {
  const response = await http.get<Blob>(`/requirements/imports/templates/${bizType}`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = bizType === 'DIFF' ? 'requirement-difference-template.xlsx' : 'legacy-requirement-template.xlsx'
  link.click()
  URL.revokeObjectURL(url)
}

export function listAttachments(bizType: string, bizId: number) {
  return http.get<ApiResponse<Array<Record<string, unknown>>>>('/requirements/attachments', { params: { bizType, bizId } })
}

export function createAttachment(bizType: string, bizId: number, data: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>('/requirements/attachments', data, { params: { bizType, bizId } })
}

export function deleteAttachment(id: number) {
  return http.delete<ApiResponse<void>>(`/requirements/attachments/${id}`)
}

export function listLegacy(params: {
  businessGroup?: string
  stage?: string
  stageStatus?: string
  keyword?: string
  page?: number
  size?: number
}) {
  return http.get<ApiResponse<PageEnvelope<LegacyRequirement>>>('/requirements/legacy', { params })
}

export function getLegacy(id: number) {
  return http.get<ApiResponse<LegacyRequirement>>(`/requirements/legacy/${id}`)
}

export function createLegacy(data: Record<string, unknown>) {
  return http.post<ApiResponse<LegacyRequirement>>('/requirements/legacy', data)
}

export function updateLegacy(id: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<LegacyRequirement>>(`/requirements/legacy/${id}`, data)
}

export function deleteLegacy(id: number) {
  return http.delete<ApiResponse<void>>(`/requirements/legacy/${id}`)
}

export function stageTransition(id: number, data: { stage: string; action: 'START' | 'COMPLETE' | 'BACK'; comment?: string }) {
  return http.post<ApiResponse<LegacyRequirement>>(`/requirements/legacy/${id}/stage`, data)
}

export function legacyStageLogs(id: number) {
  return http.get<ApiResponse<StageLogRow[]>>(`/requirements/legacy/${id}/stage-logs`)
}

export function legacyChanges(id: number) {
  return http.get<ApiResponse<ChangeLogRow[]>>(`/requirements/legacy/${id}/changes`)
}

export function listSystems() {
  return http.get<ApiResponse<RequirementSystem[]>>('/requirements/systems')
}

export function createSystem(data: Record<string, unknown>) {
  return http.post<ApiResponse<RequirementSystem>>('/requirements/systems', data)
}

export function updateSystem(id: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<RequirementSystem>>(`/requirements/systems/${id}`, data)
}

export function deleteSystem(id: number) {
  return http.delete<ApiResponse<void>>(`/requirements/systems/${id}`)
}
