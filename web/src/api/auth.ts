import http from './http'
import type { ApiResponse, AuthMe } from '../types/auth'

export function uploadOwnAvatar(file: File) {
  const data = new FormData()
  data.append('file', file)
  return http.post<ApiResponse<AuthMe>>('/auth/me/avatar', data, { headers: { 'Content-Type': 'multipart/form-data' } })
}
