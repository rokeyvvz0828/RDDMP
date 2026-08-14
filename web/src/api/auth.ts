import http from './http'
import type { ApiResponse, AuthMe } from '../types/auth'

export function uploadOwnAvatar(file: File) {
  const data = new FormData()
  data.append('file', file)
  // Let the browser add the multipart boundary; overriding this header can make some clients reject the payload.
  return http.post<ApiResponse<AuthMe>>('/auth/me/avatar', data)
}
