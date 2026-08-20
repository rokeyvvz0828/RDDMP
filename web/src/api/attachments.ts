import http from './http'
import type { ApiResponse } from '../types/auth'
import type { AttachmentLink, ProjectAttachment, ProjectAttachmentPage } from '../types/attachments'

export function getProjectAttachments(projectId: number, params: { page: number; size: number; keyword?: string }) {
  return http.get<ApiResponse<ProjectAttachmentPage>>(`/project/${projectId}/attachments`, { params })
}

export function uploadProjectAttachment(projectId: number, file: File) {
  const data = new FormData()
  data.append('file', file)
  return http.post<ApiResponse<ProjectAttachment>>(`/project/${projectId}/attachments`, data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60_000
  })
}

export function getProjectAttachmentPreview(projectId: number, attachmentId: number) {
  return http.get<ApiResponse<AttachmentLink>>(`/project/${projectId}/attachments/${attachmentId}/preview`)
}

export function getProjectAttachmentDownload(projectId: number, attachmentId: number) {
  return http.get<ApiResponse<AttachmentLink>>(`/project/${projectId}/attachments/${attachmentId}/download`)
}

export function deleteProjectAttachment(projectId: number, attachmentId: number) {
  return http.delete<ApiResponse<void>>(`/project/${projectId}/attachments/${attachmentId}`)
}
