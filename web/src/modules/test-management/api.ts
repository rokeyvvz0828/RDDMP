/*
文件：web/src/modules/test-management/api.ts
说明：测试管理营业日功能的前端类型与 HTTP 请求契约。
用途：集中封装环境、日历、跑批需求、用户目录和 XLSX 下载接口。
作者：hengguan
*/
import http from '../../api/http'
import type { ApiResponse } from '../../types/auth'

export interface BusinessDayPage<T> { records: T[]; total: number; page: number; size: number }
export interface Environment {
  id: number; env_code: string; env_name: string; purpose?: string; theme: Theme; sort_no: number
  enabled: number | boolean; remark?: string; schedule_count?: number; requirement_count?: number; updated_at?: string
}
export type Theme = 'brand' | 'success' | 'warning' | 'danger' | 'accent'
export interface Schedule {
  id: number; env_code: string; env_name?: string; theme?: Theme; natural_date: string; business_date: string
  has_batch: number | boolean; batch_type?: string; batch_time?: string; systems: string[]
  validation_content?: string; maintainer?: string; created_at?: string; updated_at?: string; overwritten?: boolean
}
export interface BatchRequirement extends Schedule {
  proposer_id: number; proposer_name?: string; proposer_username?: string; proposer_org_name?: string
  proposer_mobile_phone?: string; reviewer_id?: number; reviewer_name?: string; adoption: 'PENDING' | 'ACCEPTED' | 'REJECTED'
  review_comment?: string; reviewed_at?: string
}
export interface UserDirectoryItem {
  id: number; username: string; displayName: string; orgId: number; orgName?: string; mobilePhone?: string
}
export interface PageParams { page: number; size: number; keyword?: string }
export interface ScheduleFilters extends PageParams { envCode?: string; dateFrom?: string; dateTo?: string; hasBatch?: boolean; batchType?: string }
export interface RequirementFilters extends PageParams { envCode?: string; naturalDate?: string; adoption?: string }

const base = '/test-management/business-days'

export const listEnvironments = (params: PageParams & { enabled?: boolean }) => http.get<ApiResponse<BusinessDayPage<Environment>>>(`${base}/environments`, { params })
export const listEnvironmentOptions = () => http.get<ApiResponse<Environment[]>>(`${base}/environment-options`)
export const createEnvironment = (data: Partial<Environment>) => http.post<ApiResponse<Environment>>(`${base}/environments`, data)
export const updateEnvironment = (id: number, data: Partial<Environment>) => http.put<ApiResponse<Environment>>(`${base}/environments/${id}`, data)
export const deleteEnvironment = (id: number) => http.delete<ApiResponse<void>>(`${base}/environments/${id}`)

export const listSchedules = (params: ScheduleFilters) => http.get<ApiResponse<BusinessDayPage<Schedule>>>(`${base}/schedules`, { params })
export const listOverview = (params: { month: string; envCode?: string }) => http.get<ApiResponse<Schedule[]>>(`${base}/overview`, { params })
export const createSchedule = (data: Partial<Schedule>) => http.post<ApiResponse<Schedule>>(`${base}/schedules`, data)
export const updateSchedule = (id: number, data: Partial<Schedule>) => http.put<ApiResponse<Schedule>>(`${base}/schedules/${id}`, data)
export const deleteSchedule = (id: number) => http.delete<ApiResponse<void>>(`${base}/schedules/${id}`)
export const importSchedules = (file: File) => {
  const data = new FormData(); data.append('file', file)
  return http.post<ApiResponse<{ total: number; created: number; overwritten: number }>>(`${base}/schedules/import`, data, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export const listRequirements = (params: RequirementFilters) => http.get<ApiResponse<BusinessDayPage<BatchRequirement>>>(`${base}/requirements`, { params })
export const createRequirement = (data: Partial<BatchRequirement>) => http.post<ApiResponse<BatchRequirement>>(`${base}/requirements`, data)
export const updateRequirement = (id: number, data: Partial<BatchRequirement>) => http.put<ApiResponse<BatchRequirement>>(`${base}/requirements/${id}`, data)
export const reviewRequirement = (id: number, adoption: 'ACCEPTED' | 'REJECTED', comment: string) => http.patch<ApiResponse<BatchRequirement>>(`${base}/requirements/${id}/adoption`, { adoption, comment })
export const deleteRequirement = (id: number) => http.delete<ApiResponse<void>>(`${base}/requirements/${id}`)
export const listBusinessDayUsers = (keyword?: string) => http.get<ApiResponse<UserDirectoryItem[]>>(`${base}/users`, { params: { keyword } })

// 关键逻辑：文件响应使用临时对象 URL 触发下载，完成后立即释放，避免浏览器内存长期占用。
export async function downloadBusinessDayFile(path: string, params: Record<string, unknown>, fallbackName: string) {
  const response = await http.get<Blob>(`${base}${path}`, { params, responseType: 'blob' })
  const disposition = String(response.headers['content-disposition'] || '')
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const filename = encoded ? decodeURIComponent(encoded) : fallbackName
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a'); link.href = url; link.download = filename; link.click()
  URL.revokeObjectURL(url)
}
