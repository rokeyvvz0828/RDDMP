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

/** 文件型内容类型 -> 资源路径段（REQ-20260831-050：一菜单一端点，替代旧 /assets/{type}）。 */
const FILE_RESOURCE_SEGMENTS: Record<string, string> = {
  PLAN: 'plans', MAPPING_DOC: 'mappings', DEPENDENCY: 'dependencies',
  SCRIPT: 'programs', TOPIC: 'topics', RELEASE_DRILL: 'release-drills',
}
/** 结构化内容类型 -> 新资源段；INTERMEDIATE_TABLE 属基础资料页，沿用 /structured/{type}。 */
const STRUCTURED_RESOURCE_SEGMENTS: Record<string, string> = {
  RULE: 'rules', PARAMETER: 'parameters',
}
function fileSegment(type: string): string {
  const segment = FILE_RESOURCE_SEGMENTS[type]
  if (!segment) throw new Error(`不支持的文件内容类型：${type}`)
  return segment
}
/** 结构化端点基路径：新资源段优先，未登记类型回退 /structured/{type}。 */
function structuredBase(type: string): string {
  const segment = STRUCTURED_RESOURCE_SEGMENTS[type]
  return segment ? `/data-migration/${segment}` : `/data-migration/structured/${encodeURIComponent(type)}`
}

export interface DataMigrationContentRecycleRow {
  id: number
  asset_type: string
  asset_code: string
  asset_name: string
  project_id: number
  component_id?: number
  owner_id: number
  checksum_md5?: string
  deleted_by?: number
  deleted_at?: string
  deleted_by_name?: string
}

export function listDataMigrationStructured(type: string, params?: Record<string, unknown>) {
  return http.get<ApiResponse<DataMigrationAsset[]>>(`${structuredBase(type)}`, { params })
}

export function updateDataMigrationStructured(type: string, id: number, body: Record<string, unknown>) {
  return http.put<ApiResponse<DataMigrationAsset>>(`${structuredBase(type)}/${id}`, body)
}

export function deleteDataMigrationStructured(type: string, ids: number[]) {
  return http.post<ApiResponse<null>>(`${structuredBase(type)}/delete`, ids)
}

export function listDataMigrationMenus() {
  return http.get<ApiResponse<DataMigrationMenu[]>>('/data-migration/menus')
}

export function listDataMigrationAssets(type: string, keyword?: string) {
  return http.get<ApiResponse<DataMigrationPage<DataMigrationAsset>>>(`/data-migration/${fileSegment(type)}`, { params: { keyword: keyword || undefined } })
}

export function listDataMigrationAssetsPage(type: string, params: { projectId?: number; componentId?: number; keyword?: string; page?: number; size?: number } = {}) {
  return http.get<ApiResponse<DataMigrationPage<DataMigrationAsset>>>(`/data-migration/${fileSegment(type)}`, {
    params: { ...params, keyword: params.keyword || undefined, page: params.page ?? 1, size: params.size ?? 20 },
  })
}

export function checkDataMigrationAssetMd5(md5: string) {
  return http.get<ApiResponse<{ available: boolean }>>('/data-migration/content/check-md5', { params: { md5 } })
}

/** 统一回收站列表：contentTypes 为内容类型数组，逗号拼接传参（Spring 侧按 List 解析）；
 * T26 新增分页与统一排序（后端以业务编号 asset_code 字典序升序，同编号时按 deleted_at DESC 回退）。 */
export function listDataMigrationRecycleBin(params: { contentTypes?: string[]; keyword?: string; page?: number; size?: number }) {
  return http.get<ApiResponse<DataMigrationPage<DataMigrationContentRecycleRow>>>('/data-migration/recycle-bin', {
    params: {
      contentTypes: params.contentTypes?.length ? params.contentTypes.join(',') : undefined,
      keyword: params.keyword || undefined,
      page: params.page ?? 1,
      size: params.size ?? 20,
    },
  })
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
  return http.post<ApiResponse<DataMigrationAsset>>(`/data-migration/${fileSegment(type)}/upload`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export function deleteDataMigrationAssets(type: string, ids: number[]) {
  return http.post<ApiResponse<null>>(`/data-migration/${fileSegment(type)}/delete`, ids)
}

/** 统一回收站恢复：请求体携带内容类型以分发到对应服务（管理员权限、审计由后端负责）。 */
export function restoreDataMigrationAssets(type: string, ids: number[]) {
  return http.post<ApiResponse<null>>('/data-migration/recycle-bin/restore', { type, ids })
}

/** 统一回收站彻底删除：请求体携带内容类型以分发到对应服务。 */
export function purgeDataMigrationAssets(type: string, ids: number[]) {
  return http.post<ApiResponse<null>>('/data-migration/recycle-bin/purge', { type, ids })
}

export function downloadDataMigrationAsset(type: string, id: number) {
  return http.get<Blob>(`/data-migration/${fileSegment(type)}/${id}/download`, { responseType: 'blob' })
}

export function exportDataMigrationStructured(type: string, params?: Record<string, unknown>) {
  return http.get(`${structuredBase(type)}/export`, { params, responseType: 'blob' })
}

export function inspectDataMigrationStructuredImport(type: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<ApiResponse<Record<string, unknown>>>(`${structuredBase(type)}/import`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
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

/** 下载汇报材料（自动解析附件真实下载地址） */
export function downloadReportMaterial(id: number) {
  return http.get<ApiResponse<string>>(`/data-migration/reports/${id}/download`).then(async (response) => {
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

/** 汇报材料回收站相关旧前端入口已于 T26 下线（无 web 端调用方，旧后端 @RequestMapping 同步已删）；统一回收站入口使用 listDataMigrationRecycleBin/restoreDataMigrationAssets/purgeDataMigrationAssets 与 asset_type=REPORT 分发。 */

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
  keywords?: string
  frequency?: string
  relatedMeetingMinutes?: number[]
  relatedMeetingMinuteNames?: string
  relatedTables?: number[]
  relatedTableNames?: string
  relatedFields?: number[]
  relatedFieldNames?: string
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

export type IssueUpdateData = IssueFormData & Required<Pick<IssueFormData,
  'relatedMeetingMinutes' | 'relatedTables' | 'relatedFields'>>

export interface SelectOption {
  value: number | string
  label: string
}

export interface IssueImportResult {
  successCount: number
  failureCount: number
  rowErrors: Array<{ row: number; message: string }>
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

/** 批量导入问题，服务端逐行校验并返回完整结果 */
export function importIssues(projectId: number, file: File) {
  const form = new FormData()
  form.append('projectId', String(projectId))
  form.append('file', file)
  return http.post<ApiResponse<IssueImportResult>>('/data-migration/issues/import', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 导出当前问题筛选结果 */
export function exportIssues(params: Omit<IssueQuery, 'page' | 'size'>) {
  return http.get('/data-migration/issues/export', { params, responseType: 'blob' })
}

/** 更新问题 */
export function updateIssue(id: number, body: IssueUpdateData) {
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

// ========== 会议纪要专属接口 ==========

export interface MeetingAttachment {
  id: number
  attachment_id: number
  file_name: string
  sort_order: number
  created_by: number
  created_at?: string
  deleted_by?: number
  deleted_at?: string
  deleted_by_name?: string
}

export interface MeetingRecord {
  meeting_id: number
  /** V103 后新增的业务编号，与统一回收站信封 asset_code 同构。 */
  meeting_code?: string
  asset_code?: string
  project_id: number
  project_name?: string
  granularity: string
  meeting_source: string
  meeting_title: string
  meeting_content?: string
  meeting_conclusion?: string
  business_scenario?: string
  keywords?: string
  attachment_id?: number
  file_name?: string
  attachments?: MeetingAttachment[]
  attachment_count?: number
  created_by: number
  created_at?: string
  updated_by?: number
  updated_at?: string
  deleted_by?: number
  deleted_at?: string
  created_by_name?: string
  updated_by_name?: string
  deleted_by_name?: string
  system_names?: string
  system_ids?: string
  related_issue_names?: string
  related_issue_ids?: string
}

export interface MeetingQuery {
  projectId?: number
  meetingSource?: string
  granularity?: string
  systemId?: number
  keyword?: string
  page?: number
  size?: number
}

export interface MeetingFormData {
  projectId: number
  granularity: string
  meetingSource: string
  meetingTitle: string
  /** 会议编号；新增时留空则后端自动生成 MEET-{id}，编辑时可调整。 */
  meetingCode?: string
  meetingContent?: string
  meetingConclusion?: string
  businessScenario?: string
  keywords?: string[]
  systemIds?: number[]
  issueIds?: number[]
  attachmentId?: number
  fileName?: string
  attachments?: { attachmentId: number; fileName: string }[]
}

/** 分页查询会议纪要列表 */
export function listMeetings(params: MeetingQuery) {
  return http.get<ApiResponse<DataMigrationPage<MeetingRecord>>>('/data-migration/meetings', { params })
}

/** 获取单条会议纪要详情 */
export function getMeeting(meetingId: number) {
  return http.get<ApiResponse<MeetingRecord>>(`/data-migration/meetings/${meetingId}`)
}

/** 创建会议纪要 */
export function createMeeting(body: MeetingFormData) {
  return http.post<ApiResponse<MeetingRecord>>('/data-migration/meetings', body)
}

/** 更新会议纪要 */
export function updateMeeting(meetingId: number, body: MeetingFormData) {
  return http.put<ApiResponse<MeetingRecord>>(`/data-migration/meetings/${meetingId}`, body)
}

/** 批量删除会议纪要（逻辑删除） */
export function deleteMeetings(meetingIds: number[]) {
  return http.delete<ApiResponse<null>>('/data-migration/meetings', { data: meetingIds })
}

/** 会议回收站相关旧前端入口已于 T26 下线（无 web 端调用方，旧后端 @RequestMapping 同步已删）；统一回收站入口使用 listDataMigrationRecycleBin/restoreDataMigrationAssets/purgeDataMigrationAssets 与 asset_type=MEETING 分发。附件级回收站 (listMeetingAttachmentsRecycleBin 等) 仍保留在会议页。 */

/** 获取系统选项（根据项目） */
export function getMeetingSystemOptions(projectId?: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/meetings/options/systems', { params: { projectId } })
}

/** 获取问题选项（根据项目） */
export function getMeetingIssueOptions(projectId?: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/meetings/options/issues', { params: { projectId } })
}

/** 获取会议纪要附件列表 */
export function getMeetingAttachments(meetingId: number) {
  return http.get<ApiResponse<MeetingAttachment[]>>(`/data-migration/meetings/${meetingId}/attachments`)
}

/** 删除会议纪要附件（移入回收站） */
export function deleteMeetingAttachment(meetingId: number, attachmentId: number) {
  return http.delete<ApiResponse<null>>(`/data-migration/meetings/${meetingId}/attachments/${attachmentId}`)
}

/** 获取附件回收站列表 */
export function getMeetingAttachmentRecycleBin(meetingId: number) {
  return http.get<ApiResponse<MeetingAttachment[]>>(`/data-migration/meetings/${meetingId}/attachments/recycle-bin`)
}

/** 恢复附件（单个，指定会议） */
export function restoreMeetingAttachment(meetingId: number, attachmentId: number) {
  return http.post<ApiResponse<null>>(`/data-migration/meetings/${meetingId}/attachments/${attachmentId}/restore`)
}

/** 全局附件回收站列表（跨会议） */
export function listAttachmentRecycleBin(params: { projectId?: number; keyword?: string; page?: number; size?: number }) {
  return http.get<ApiResponse<DataMigrationPage<MeetingAttachment & { meeting_id: number; meeting_title: string }>>>(
    '/data-migration/meetings/attachments/recycle-bin', { params }
  )
}

/** 批量恢复附件 */
export function restoreAttachments(ids: number[]) {
  return http.post<ApiResponse<null>>('/data-migration/meetings/attachments/restore', ids)
}

/** 彻底删除附件 */
export function purgeAttachments(ids: number[]) {
  return http.delete<ApiResponse<null>>('/data-migration/meetings/attachments/purge', { data: ids })
}

/** 清空附件回收站 */
export function purgeAllAttachments() {
  return http.delete<ApiResponse<null>>('/data-migration/meetings/attachments/purge-all')
}
