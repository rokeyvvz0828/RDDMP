import http from './http'
import type { ApiResponse } from '../types/auth'

export interface FilePreviewCapabilities {
  enabled: boolean
  maxFileSizeBytes: number
  allowedExtensions: string[]
}

export interface FilePreviewResult {
  previewId: string
  fileName: string
  contentType: string
  size: number
  previewUrl: string
}

export function getFilePreviewCapabilities() {
  return http.get<ApiResponse<FilePreviewCapabilities>>('/file-previews/capabilities')
}

export function uploadFilePreview(file: File) {
  const data = new FormData()
  data.append('file', file)
  return http.post<ApiResponse<FilePreviewResult>>('/file-previews', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60_000
  })
}

export function deleteFilePreview(previewId: string) {
  return http.delete<ApiResponse<void>>(`/file-previews/${encodeURIComponent(previewId)}`)
}
