import http from './http'
import type { ApiResponse } from '../types/auth'

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
  return http.post<ApiResponse<AttachmentItem>>('/attachments', body, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function getAttachment(id: number) { return http.get<ApiResponse<AttachmentItem>>(`/attachments/${id}`) }
export function getAttachmentPreview(id: number) { return http.get<ApiResponse<{ previewUrl: string }>>(`/attachments/${id}/preview`) }
export function getAttachmentDownload(id: number) { return http.get<ApiResponse<{ downloadUrl: string }>>(`/attachments/${id}/download`) }
export function deleteTemporaryAttachment(id: number) { return http.delete<ApiResponse<void>>(`/attachments/${id}`) }
