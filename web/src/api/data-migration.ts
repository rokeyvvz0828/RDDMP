import http from './http'
import type { ApiResponse } from '../types/auth'

export interface DataMigrationMenu { id: number; name: string; routePath: string }
export interface DataMigrationProject { id: number; project_code: string; project_name: string; status: string }
export interface DataMigrationAsset { id: number; project_id: number; component_id?: number; asset_type: string; asset_code: string; asset_name: string; file_size?: number; structured_data?: unknown; owner_id: number }
export interface DataMigrationComponent { id: number; project_id: number; component_code: string; component_name: string; description?: string; owner_id: number }

export function listDataMigrationProjects(params?: Record<string, unknown>) {
  return http.get<ApiResponse<DataMigrationProject[]>>('/data-migration/projects', { params })
}

export function listDataMigrationComponents(params?: Record<string, unknown>) {
  return http.get<ApiResponse<DataMigrationComponent[]>>('/data-migration/components', { params })
}

export function updateDataMigrationProject(id: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<DataMigrationProject>>(`/data-migration/projects/${id}`, body)
}

export function updateDataMigrationComponent(id: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<DataMigrationComponent>>(`/data-migration/components/${id}`, body)
}

export function listDataMigrationStructured(type: string, params?: Record<string, unknown>) {
  return http.get<ApiResponse<DataMigrationAsset[]>>(`/data-migration/structured/${encodeURIComponent(type)}`, { params })
}

export function updateDataMigrationStructured(type: string, id: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<DataMigrationAsset>>(`/data-migration/structured/${encodeURIComponent(type)}/${id}`, body)
}

export function deleteDataMigrationStructured(type: string, ids: number[]) {
  return http.post<ApiResponse<null>>(`/data-migration/structured/${encodeURIComponent(type)}/delete`, ids)
}

export function listDataMigrationMenus() {
  return http.get<ApiResponse<DataMigrationMenu[]>>('/data-migration/menus')
}

export function listDataMigrationAssets(params?: Record<string, unknown>) {
  return http.get<ApiResponse<DataMigrationAsset[]>>('/data-migration/assets', { params })
}

export function listDataMigrationRecycleBin(params?: Record<string, unknown>) {
  return http.get<ApiResponse<DataMigrationAsset[]>>('/data-migration/recycle-bin', { params })
}

export function uploadDataMigrationAsset(type: string, projectId: number, assetCode: string, file: File, componentId?: number) {
  const form = new FormData()
  form.append('projectId', String(projectId))
  form.append('assetCode', assetCode)
  if (componentId != null) form.append('componentId', String(componentId))
  form.append('file', file)
  return http.post<ApiResponse<DataMigrationAsset>>(`/data-migration/assets/${encodeURIComponent(type)}/upload`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function deleteDataMigrationAssets(ids: number[]) {
  return http.post<ApiResponse<null>>('/data-migration/assets/delete', ids)
}

export function restoreDataMigrationAssets(ids: number[]) {
  return http.post<ApiResponse<null>>('/data-migration/recycle-bin/restore', ids)
}

export function purgeDataMigrationAssets(ids: number[]) {
  return http.post<ApiResponse<null>>('/data-migration/recycle-bin/purge', ids)
}

export function downloadDataMigrationAsset(id: number) {
  return http.get<ApiResponse<string>>(`/data-migration/assets/${id}/download`)
}

export function exportDataMigrationStructured(type: string, params?: Record<string, unknown>) {
  return http.get(`/data-migration/structured/${encodeURIComponent(type)}/export`, { params, responseType: 'blob' })
}

export function inspectDataMigrationStructuredImport(type: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<ApiResponse<Record<string, unknown>>>(`/data-migration/structured/${encodeURIComponent(type)}/import`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function getDataMigrationDashboard(view: 'overall' | 'component' = 'overall') {
  return http.get<ApiResponse<Record<string, unknown> | Array<Record<string, unknown>>>>(`/data-migration/dashboard/${view}`)
}
