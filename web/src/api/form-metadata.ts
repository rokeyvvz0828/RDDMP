import http from './http'
import type { ApiResponse } from '../types/auth'
import type { FormMetadataField, FormMetadataModule, FormMetadataSchema, FormMetadataScope, FormMetadataSection, FormMetadataView, FormMetadataViewSchema } from '../types/form-metadata'

const base = '/system/form-metadata'

export function listFormMetadataModules() {
  return http.get<ApiResponse<FormMetadataModule[]>>(`${base}/modules`)
}

export function listFormMetadataScopes(keyword?: string) {
  return http.get<ApiResponse<FormMetadataScope[]>>(`${base}/scopes`, { params: { keyword: keyword || undefined } })
}

export function getFormMetadataSchema(scopeId: number) {
  return http.get<ApiResponse<FormMetadataSchema>>(`${base}/scopes/${scopeId}`)
}

export function createFormMetadataScope(data: Record<string, unknown>) {
  return http.post<ApiResponse<FormMetadataScope>>(`${base}/scopes`, data)
}

export function updateFormMetadataScope(scopeId: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<FormMetadataScope>>(`${base}/scopes/${scopeId}`, data)
}

export function createFormMetadataSection(scopeId: number, data: Record<string, unknown>) {
  return http.post<ApiResponse<FormMetadataSection>>(`${base}/scopes/${scopeId}/sections`, data)
}

export function updateFormMetadataSection(scopeId: number, sectionId: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<FormMetadataSection>>(`${base}/scopes/${scopeId}/sections/${sectionId}`, data)
}

export function deleteFormMetadataSection(scopeId: number, sectionId: number) {
  return http.delete<ApiResponse<void>>(`${base}/scopes/${scopeId}/sections/${sectionId}`)
}

export function createFormMetadataField(scopeId: number, data: Record<string, unknown>) {
  return http.post<ApiResponse<FormMetadataField>>(`${base}/scopes/${scopeId}/fields`, data)
}

export function updateFormMetadataField(scopeId: number, fieldId: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<FormMetadataField>>(`${base}/scopes/${scopeId}/fields/${fieldId}`, data)
}

export function deleteFormMetadataField(scopeId: number, fieldId: number) {
  return http.delete<ApiResponse<void>>(`${base}/scopes/${scopeId}/fields/${fieldId}`)
}

export function publishFormMetadata(scopeId: number, changeSummary: string) {
  return http.post<ApiResponse<{ revisionId: number; revisionNo: number }>>(`${base}/scopes/${scopeId}/publish`, { change_summary: changeSummary })
}

export function listFormMetadataViews(scopeId: number) {
  return http.get<ApiResponse<FormMetadataView[]>>(`${base}/scopes/${scopeId}/views`)
}

export function getFormMetadataView(scopeId: number, viewId: number) {
  return http.get<ApiResponse<FormMetadataViewSchema>>(`${base}/scopes/${scopeId}/views/${viewId}`)
}

export function createFormMetadataView(scopeId: number, data: Record<string, unknown>) {
  return http.post<ApiResponse<FormMetadataView>>(`${base}/scopes/${scopeId}/views`, data)
}

export function updateFormMetadataView(scopeId: number, viewId: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<FormMetadataView>>(`${base}/scopes/${scopeId}/views/${viewId}`, data)
}

export function createFormMetadataViewField(scopeId: number, viewId: number, data: Record<string, unknown>) {
  return http.post<ApiResponse<Record<string, unknown>>>(`${base}/scopes/${scopeId}/views/${viewId}/fields`, data)
}

export function updateFormMetadataViewField(scopeId: number, viewId: number, viewFieldId: number, data: Record<string, unknown>) {
  return http.put<ApiResponse<Record<string, unknown>>>(`${base}/scopes/${scopeId}/views/${viewId}/fields/${viewFieldId}`, data)
}

export function deleteFormMetadataViewField(scopeId: number, viewId: number, viewFieldId: number) {
  return http.delete<ApiResponse<void>>(`${base}/scopes/${scopeId}/views/${viewId}/fields/${viewFieldId}`)
}

export function publishFormMetadataView(scopeId: number, viewId: number, changeSummary: string) {
  return http.post<ApiResponse<{ revisionId: number; revisionNo: number }>>(`${base}/scopes/${scopeId}/views/${viewId}/publish`, { change_summary: changeSummary })
}
