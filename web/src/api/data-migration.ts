import http from './http'
import type { ApiResponse } from '../types/auth'
import { getAttachmentDownload, uploadAttachment } from './attachments'

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

export function checkDataMigrationAssetMd5(md5: string) {
  return http.get<ApiResponse<{ available: boolean }>>('/data-migration/assets/check-md5', { params: { md5 } })
}

export function listDataMigrationRecycleBin(params?: Record<string, unknown>) {
  return http.get<ApiResponse<DataMigrationAsset[]>>('/data-migration/recycle-bin', { params })
}

export async function uploadDataMigrationAsset(type: string, projectId: number, assetCode: string, file: File, componentId?: number) {
  const md5 = await computeFileMd5(file)
  const availability = await checkDataMigrationAssetMd5(md5)
  if (!availability.data.data?.available) throw new Error('文件 MD5 已存在，不允许重复提交')
  const attachment = await uploadAttachment(file)
  const attachmentId = attachment.data.data?.id
  if (!attachmentId) throw new Error('公共附件上传失败')
  const form = new FormData()
  form.append('projectId', String(projectId))
  form.append('assetCode', assetCode)
  if (componentId != null) form.append('componentId', String(componentId))
  form.append('attachmentId', String(attachmentId))
  form.append('md5', md5)
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
  return http.get<ApiResponse<string>>(`/data-migration/assets/${id}/download`).then(async (response) => {
    const attachmentPath = response.data.data
    const match = typeof attachmentPath === 'string' && attachmentPath.match(/^\/api\/attachments\/(\d+)\/download$/)
    if (!match) return response
    const attachment = await getAttachmentDownload(Number(match[1]))
    return {
      ...response,
      data: { ...response.data, data: attachment.data.data?.downloadUrl ?? '' }
    }
  })
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

export function deleteTargetTableFields(category: TableCategory, ids: number[]) {
  return http.post<ApiResponse<null>>(`/data-migration/target-table-fields/batch-delete`, ids, { params: { category } })
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

/** 前端计算文件 MD5（32 位小写 hex）。Web Crypto 不提供 MD5，因此使用 RFC 1321 的本地实现。 */
export async function computeFileMd5(file: File): Promise<string> {
  return md5(new Uint8Array(await file.arrayBuffer()))
}

function md5(input: Uint8Array): string {
  const bitLength = input.length * 8
  const paddedLength = (((input.length + 8) >>> 6) + 1) * 64
  const data = new Uint8Array(paddedLength)
  data.set(input)
  data[input.length] = 0x80
  const view = new DataView(data.buffer)
  view.setUint32(paddedLength - 8, bitLength >>> 0, true)
  view.setUint32(paddedLength - 4, Math.floor(bitLength / 0x100000000), true)

  let a0 = 0x67452301
  let b0 = 0xefcdab89
  let c0 = 0x98badcfe
  let d0 = 0x10325476
  const shifts = [7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21]
  const constants = Array.from({ length: 64 }, (_, index) => Math.floor(Math.abs(Math.sin(index + 1)) * 0x100000000) >>> 0)

  for (let offset = 0; offset < data.length; offset += 64) {
    const words = new Uint32Array(16)
    for (let index = 0; index < 16; index += 1) words[index] = view.getUint32(offset + index * 4, true)
    let a = a0
    let b = b0
    let c = c0
    let d = d0
    for (let index = 0; index < 64; index += 1) {
      let f: number
      let wordIndex: number
      if (index < 16) {
        f = (b & c) | (~b & d)
        wordIndex = index
      } else if (index < 32) {
        f = (d & b) | (~d & c)
        wordIndex = (5 * index + 1) % 16
      } else if (index < 48) {
        f = b ^ c ^ d
        wordIndex = (3 * index + 5) % 16
      } else {
        f = c ^ (b | ~d)
        wordIndex = (7 * index) % 16
      }
      const shift = shifts[(index < 16 ? 0 : index < 32 ? 4 : index < 48 ? 8 : 12) + (index % 4)]
      const next = d
      d = c
      c = b
      const sum = (a + f + constants[index] + words[wordIndex]) >>> 0
      b = (b + ((sum << shift) | (sum >>> (32 - shift)))) >>> 0
      a = next
    }
    a0 = (a0 + a) >>> 0
    b0 = (b0 + b) >>> 0
    c0 = (c0 + c) >>> 0
    d0 = (d0 + d) >>> 0
  }

  return [a0, b0, c0, d0]
    .map((word) => Array.from({ length: 4 }, (_, index) => ((word >>> (index * 8)) & 0xff).toString(16).padStart(2, '0')).join(''))
    .join('')
}

// ========== 汇报材料专属接口 ==========

export interface ReportMaterial {
  id: number
  project_id: number
  project_name?: string
  asset_type: string
  asset_code: string
  asset_name: string
  content_type?: string
  file_size?: number
  attachment_id?: number
  checksum_md5?: string
  report_period: string
  report_date?: string
  keywords?: string
  owner_id: number
  created_at?: string
  updated_at?: string
  created_by?: number
  updated_by?: number
  deleted_by?: number
  deleted_at?: string
}

export interface ReportPageQuery {
  projectId?: number
  reportPeriod?: string
  keyword?: string
  page?: number
  size?: number
  deleteDateStart?: string
  deleteDateEnd?: string
}

export interface ReportUploadParams {
  projectId: number
  reportPeriod: string
  reportName: string
  reportDate?: string
  keywords: string
  attachmentId: number
  checksumMd5?: string
}

export interface ReportBatchUploadParams {
  projectId: number
  reportPeriod: string
  attachmentIds: number[]
  checksumMd5s: string[]
}

export interface ReportUpdateParams {
  projectId?: number
  reportPeriod?: string
  reportName?: string
  reportDate?: string
  keywords?: string
  attachmentId?: number
  checksumMd5?: string
}

/** 分页查询汇报材料列表 */
export function listReportMaterials(params: ReportPageQuery) {
  return http.get<ApiResponse<DataMigrationPage<ReportMaterial>>>('/data-migration/reports', { params })
}

/** 单条上传汇报材料 */
export function uploadReportMaterial(params: ReportUploadParams) {
  const form = new FormData()
  form.append('projectId', String(params.projectId))
  form.append('reportPeriod', params.reportPeriod)
  form.append('reportName', params.reportName)
  if (params.reportDate) form.append('reportDate', params.reportDate)
  form.append('keywords', params.keywords)
  form.append('attachmentId', String(params.attachmentId))
  if (params.checksumMd5) form.append('checksumMd5', params.checksumMd5)
  return http.post<ApiResponse<ReportMaterial>>('/data-migration/reports/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 批量上传汇报材料 */
export function batchUploadReportMaterials(params: ReportBatchUploadParams) {
  const form = new FormData()
  form.append('projectId', String(params.projectId))
  form.append('reportPeriod', params.reportPeriod)
  params.attachmentIds.forEach(id => form.append('attachmentIds', String(id)))
  params.checksumMd5s.forEach(md5 => form.append('checksumMd5s', md5))
  return http.post<ApiResponse<ReportMaterial[]>>('/data-migration/reports/batch', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 编辑汇报材料 */
export function updateReportMaterial(id: number, params: ReportUpdateParams) {
  const form = new FormData()
  if (params.projectId) form.append('projectId', String(params.projectId))
  if (params.reportPeriod) form.append('reportPeriod', params.reportPeriod)
  if (params.reportName) form.append('reportName', params.reportName)
  if (params.reportDate) form.append('reportDate', params.reportDate)
  if (params.keywords) form.append('keywords', params.keywords)
  if (params.attachmentId) form.append('attachmentId', String(params.attachmentId))
  if (params.checksumMd5) form.append('checksumMd5', params.checksumMd5)
  return http.put<ApiResponse<ReportMaterial>>(`/data-migration/reports/${id}`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 逻辑删除汇报材料 */
export function deleteReportMaterials(ids: number[]) {
  return http.delete<ApiResponse<null>>('/data-migration/reports', { data: ids })
}

/** 下载汇报材料 */
export function downloadReportMaterial(id: number) {
  return http.get<ApiResponse<string>>(`/data-migration/reports/${id}/download`)
}

/** 回收站列表 */
export function listReportRecycleBin(params: ReportPageQuery) {
  return http.get<ApiResponse<DataMigrationPage<ReportMaterial>>>('/data-migration/reports/recycle-bin', { params })
}

/** 恢复汇报材料 */
export function restoreReportMaterials(ids: number[]) {
  return http.post<ApiResponse<null>>('/data-migration/reports/recycle-bin/restore', ids)
}

/** 确认清理汇报材料 */
export function purgeReportMaterials(ids: number[]) {
  return http.post<ApiResponse<null>>('/data-migration/reports/recycle-bin/purge', ids)
}

/** 检查MD5是否可用 */
export function checkReportMd5(md5: string) {
  return http.get<ApiResponse<{ available: boolean }>>('/data-migration/reports/check-md5', { params: { md5 } })
}

/** 获取项目选项列表 */
export function getReportProjectOptions() {
  return http.get<ApiResponse<Array<{ id: number; project_name: string }>>>('/data-migration/reports/project-options')
}

// ========== 问题清单专属接口 ==========

export interface IssueRecord {
  id: number
  project_id: number
  project_name?: string
  asset_code: string
  asset_name: string
  structured_data?: string
  owner_id: number
  created_at?: string
  updated_at?: string
  created_by?: number
  updated_by?: number
  created_by_name?: string
  updated_by_name?: string
  deleted_by?: number
  deleted_at?: string
  deleted_by_name?: string
}

export interface IssueQuery {
  projectId?: number
  granularity?: string
  systemCode?: string
  issueSource?: string
  defectType?: string
  frequency?: string
  keyword?: string
  page?: number
  size?: number
}

export interface IssueFormData {
  projectId: number
  issueCode: string
  issueName: string
  granularity?: string
  systemCode?: string
  systemName?: string
  issueSource?: string
  defectType?: string
  issueDescription?: string
  solution?: string
  meetingConclusion?: string
  processingSteps?: string
  businessScenario?: string
  handler?: string
  responsibleParty?: string
  keywords?: string[]
  relatedMeetingMinutes?: number[]
  relatedMeetingMinuteNames?: string
  frequency?: string
  relatedTables?: number[]
  relatedTableNames?: string
  relatedFields?: number[]
  relatedFieldNames?: string
}

export interface SelectOption {
  value: number | string
  label: string
}

/** 分页查询问题清单列表 */
export function listIssues(params: IssueQuery) {
  return http.get<ApiResponse<DataMigrationPage<IssueRecord>>>('/data-migration/issues', { params })
}

/** 获取单条问题详情 */
export function getIssue(id: number) {
  return http.get<ApiResponse<IssueRecord>>(`/data-migration/issues/${id}`)
}

/** 新增问题 */
export function createIssue(body: IssueFormData) {
  return http.post<ApiResponse<IssueRecord>>('/data-migration/issues', body)
}

/** 更新问题 */
export function updateIssue(id: number, body: Partial<IssueFormData>) {
  return http.put<ApiResponse<IssueRecord>>(`/data-migration/issues/${id}`, body)
}

/** 批量删除问题 */
export function deleteIssues(ids: number[]) {
  return http.delete<ApiResponse<null>>('/data-migration/issues', { data: ids })
}

/** 回收站列表 */
export function listIssueRecycleBin(params: { projectId?: number; keyword?: string; page?: number; size?: number }) {
  return http.get<ApiResponse<DataMigrationPage<IssueRecord>>>('/data-migration/issues/recycle-bin', { params })
}

/** 恢复问题 */
export function restoreIssues(ids: number[]) {
  return http.post<ApiResponse<null>>('/data-migration/issues/restore', ids)
}

/** 彻底删除问题 */
export function purgeIssues(ids: number[]) {
  return http.delete<ApiResponse<null>>('/data-migration/issues/purge', { data: ids })
}

/** 清空回收站 */
export function purgeAllIssues() {
  return http.delete<ApiResponse<null>>('/data-migration/issues/purge-all')
}

/** 获取项目选项 */
export function getIssueProjectOptions() {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/issues/options/projects')
}

/** 获取系统选项（根据项目） */
export function getIssueSystemOptions(projectId?: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/issues/options/systems', { params: { projectId } })
}

/** 获取系统名称（根据系统编号） */
export function getIssueSystemName(systemCode: string) {
  return http.get<ApiResponse<string>>('/data-migration/issues/options/system-name', { params: { systemCode } })
}

/** 获取会议纪要选项（根据项目） */
export function getIssueMeetingOptions(projectId?: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/issues/options/meetings', { params: { projectId } })
}

/** 获取目标表选项（根据项目） */
export function getIssueTargetTableOptions(projectId?: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/issues/options/target-tables', { params: { projectId } })
}

/** 获取目标表字段选项（根据表ID） */
export function getIssueTargetFieldOptions(tableId: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/issues/options/target-fields', { params: { tableId } })
}
