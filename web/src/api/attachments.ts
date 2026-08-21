import http from './http'
import type { ApiResponse } from '../types/auth'
import type {
  AttachmentLink,
  ProjectAttachment,
  ProjectAttachmentPage
} from '../types/attachments'

export interface AttachmentItem {
  id: number
  fileName: string
  contentType?: string
  fileSize: number
  fileExtension?: string
  status: 'TEMP' | 'BOUND' | 'DELETED'
  businessType?: string
  businessKey?: string
  projectRef?: string
  uploaderId: number
  createdAt?: string
}

export function uploadAttachment(file: File) {
  const body = new FormData()
  body.append('file', file)
  return http.post<ApiResponse<AttachmentItem>>('/attachments', body, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getAttachment(id: number) {
  return http.get<ApiResponse<AttachmentItem>>(`/attachments/${id}`)
}

export function getAttachmentPreview(id: number) {
  return http.get<ApiResponse<{ previewUrl: string }>>(
    `/attachments/${id}/preview`
  )
}

export function getAttachmentDownload(id: number) {
  return http.get<ApiResponse<{ downloadUrl: string }>>(
    `/attachments/${id}/download`
  )
}

export function deleteTemporaryAttachment(id: number) {
  return http.delete<ApiResponse<void>>(`/attachments/${id}`)
}

export function getProjectAttachments(
  projectId: number,
  params: { page: number; size: number; keyword?: string }
) {
  return http.get<ApiResponse<ProjectAttachmentPage>>(
    `/project/${projectId}/attachments`,
    { params }
  )
}

export function uploadProjectAttachment(projectId: number, file: File) {
  const data = new FormData()
  data.append('file', file)
  return http.post<ApiResponse<ProjectAttachment>>(
    `/project/${projectId}/attachments`,
    data,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 60_000
    }
  )
}

export function getProjectAttachmentPreview(
  projectId: number,
  attachmentId: number
) {
  return http.get<ApiResponse<AttachmentLink>>(
    `/project/${projectId}/attachments/${attachmentId}/preview`
  )
}

export function getProjectAttachmentDownload(
  projectId: number,
  attachmentId: number
) {
  return http.get<ApiResponse<AttachmentLink>>(
    `/project/${projectId}/attachments/${attachmentId}/download`
  )
}

export function deleteProjectAttachment(
  projectId: number,
  attachmentId: number
) {
  return http.delete<ApiResponse<void>>(
    `/project/${projectId}/attachments/${attachmentId}`
  )
}