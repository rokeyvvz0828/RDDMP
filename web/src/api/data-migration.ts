import http from './http'
import type { ApiResponse } from '../types/auth'

export interface DataMigrationMenu { id: number; name: string; routePath: string }
export interface DataMigrationAsset { id: number; project_id: number; component_id?: number; asset_type: string; asset_code: string; asset_name: string; file_size?: number; structured_data?: unknown; owner_id: number }
export interface DataMigrationComponent {
  id: number
  project_id: number
  project_code: string
  project_name: string
  physical_subsystem_code?: string
  business_group_name?: string
  system_short_name?: string
  system_name?: string
  system_description?: string
  responsible_team_name?: string
  total_check: number
  created_at?: string
  created_by_name?: string
  updated_at?: string
  updated_by_name?: string
}
export interface DataMigrationPage<T> { records: T[]; total: number; page: number; size: number }

/** 架构管理/物理子系统联动：仅读取系统编号匹配的物理子系统（跨模块只读 REST 契约，供系统/组件清单带出系统信息）。 */
export interface PhysicalSubsystemLite {
  code: string
  shortName: string
  name: string
  businessGroupName?: string | null
  description?: string | null
  responsibleTeamDisplayName: string
}

export function listPhysicalSubsystemsByCode(code: string) {
  return http.get<ApiResponse<DataMigrationPage<PhysicalSubsystemLite>>>('/architecture/physical-subsystems', { params: { code, page: 1, size: 20 } })
}

export function listDataMigrationComponents(params?: Record<string, unknown>) {
  return http.get<ApiResponse<DataMigrationPage<DataMigrationComponent>>>('/data-migration/components', { params })
}

export function createDataMigrationComponent(body: Record<string, unknown>) {
  return http.post<ApiResponse<DataMigrationComponent>>('/data-migration/components', body)
}

export function exportDataMigrationComponents(params?: Record<string, unknown>) {
  return http.get('/data-migration/components/export', { params, responseType: 'blob' })
}

export function updateDataMigrationComponent(id: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<DataMigrationComponent>>(`/data-migration/components/${id}`, body)
}

export function deleteDataMigrationComponent(id: number) {
  return http.delete<ApiResponse<null>>(`/data-migration/components/${id}`)
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

/* ============ 目标表结构 / 中间表结构（基础资料管理） ============ */
export type TableCategory = 'TARGET' | 'INTERMEDIATE'

export interface TargetTableField {
  id: number
  field_code: string
  table_id: number
  field_name_en: string
  field_name_cn: string
  field_meaning?: string | null
  code_description?: string | null
  is_key_field: number
  oracle_type?: string | null
  mysql_type?: string | null
  is_nullable: number
  is_primary_key: number
  dict_code?: string | null
}

export interface TargetTableRecord {
  id: number
  field_id?: number
  field_code?: string
  table_code: string
  project_id: number
  system_code: string
  table_name_en: string
  table_name_cn: string
  table_meaning?: string | null
  table_category: TableCategory
  project_name?: string
  business_group?: string
  system_name?: string
  owner_name?: string
  created_at?: string
  updated_at?: string
  field_name_en?: string
  field_name_cn?: string
  field_meaning?: string | null
  code_description?: string | null
  is_key_field?: number
  oracle_type?: string | null
  mysql_type?: string | null
  is_nullable?: number
  is_primary_key?: number
  dict_code?: string | null
  fields?: TargetTableField[]
}

export interface TargetTableQuery {
  category?: TableCategory
  projectId?: number
  systemCode?: string
  isKeyField?: number
  dictCode?: string
  tableKeyword?: string
  fieldKeyword?: string
  page?: number
  size?: number
}

export function listTargetTables(params: TargetTableQuery) {
  return http.get<ApiResponse<DataMigrationPage<TargetTableRecord>>>(`/data-migration/target-tables`, { params })
}

export function getTargetTable(id: number, category?: TableCategory) {
  return http.get<ApiResponse<TargetTableRecord>>(`/data-migration/target-tables/${id}`, { params: { category } })
}

export function createTargetTable(category: TableCategory, body: Record<string, unknown>) {
  return http.post<ApiResponse<TargetTableRecord>>(`/data-migration/target-tables`, body, { params: { category } })
}

export function updateTargetTable(id: number, category: TableCategory, body: Record<string, unknown>) {
  return http.put<ApiResponse<TargetTableRecord>>(`/data-migration/target-tables/${id}`, body, { params: { category } })
}

export function deleteTargetTables(category: TableCategory, ids: number[]) {
  return http.post<ApiResponse<null>>(`/data-migration/target-tables/batch-delete`, ids, { params: { category } })
}

export function listTargetTableFields(id: number, category?: TableCategory) {
  return http.get<ApiResponse<TargetTableField[]>>(`/data-migration/target-tables/${id}/fields`, { params: { category } })
}

export function addTargetTableField(id: number, category: TableCategory, body: Record<string, unknown>) {
  return http.post<ApiResponse<TargetTableField>>(`/data-migration/target-tables/${id}/fields`, body, { params: { category } })
}

export function updateTargetTableField(fieldId: number, category: TableCategory, body: Record<string, unknown>) {
  return http.put<ApiResponse<TargetTableField>>(`/data-migration/target-table-fields/${fieldId}`, body, { params: { category } })
}

export function deleteTargetTableField(fieldId: number, category: TableCategory) {
  return http.delete<ApiResponse<null>>(`/data-migration/target-table-fields/${fieldId}`, { params: { category } })
}

export function importTargetTables(category: TableCategory, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<ApiResponse<{ accepted: number; failed: number; errors: string[] }>>(`/data-migration/target-tables/import`, form, { params: { category }, headers: { 'Content-Type': 'multipart/form-data' } })
}

export function exportTargetTables(params: { category?: TableCategory; ids?: number[] } & Omit<TargetTableQuery, 'category' | 'page' | 'size'>) {
  return http.get(`/data-migration/target-tables/export`, { params: { ...params }, responseType: 'blob' })
}

export function downloadTargetTableTemplate() {
  return http.get(`/data-migration/target-tables/template`, { responseType: 'blob' })
}
