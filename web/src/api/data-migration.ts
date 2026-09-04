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

/** T32：数据迁移的所有清单/看板/回收站接口都必须携带 projectId，后端缺失时返回 400。 */
export type ProjectScopedParams = Record<string, unknown> & { projectId: number }

export function listDataMigrationComponents(params: ProjectScopedParams) {
  return http.get<ApiResponse<DataMigrationPage<DataMigrationComponent>>>('/data-migration/components', { params })
}

export function createDataMigrationComponent(body: Record<string, unknown>) {
  return http.post<ApiResponse<DataMigrationComponent>>('/data-migration/components', body)
}

export function exportDataMigrationComponents(params: ProjectScopedParams) {
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
/** 结构化内容类型 -> 新资源段；中间表统一使用目标表/字段接口。 */
const STRUCTURED_RESOURCE_SEGMENTS: Record<string, string> = {
  RULE: 'rules', PARAMETER: 'parameters',
}
function fileSegment(type: string): string {
  const segment = FILE_RESOURCE_SEGMENTS[type]
  if (!segment) throw new Error(`不支持的文件内容类型：${type}`)
  return segment
}
/** 结构化端点基路径：仅允许规则和参数，避免回退到旧中间表端点。 */
function structuredBase(type: string): string {
  const segment = STRUCTURED_RESOURCE_SEGMENTS[type]
  if (!segment) throw new Error(`不支持的结构化内容类型：${type}`)
  return `/data-migration/${segment}`
}

export interface DataMigrationContentRecycleRow {
  id: number
  asset_type: string
  asset_code: string
  asset_name: string
  project_id: number
  component_id?: number
  owner_id: number
  deleted_by?: number
  deleted_at?: string
  deleted_by_name?: string
}

export type DataMigrationContentRecycleDetail = DataMigrationContentRecycleRow & Record<string, unknown>

export function listDataMigrationStructured(type: string, params: ProjectScopedParams) {
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

export function listDataMigrationAssets(type: string, projectId: number, keyword?: string) {
  return http.get<ApiResponse<DataMigrationPage<DataMigrationAsset>>>(`/data-migration/${fileSegment(type)}`, { params: { projectId, keyword: keyword || undefined } })
}

export function listDataMigrationAssetsPage(type: string, params: { projectId: number; componentId?: number; keyword?: string; page?: number; size?: number }) {
  return http.get<ApiResponse<DataMigrationPage<DataMigrationAsset>>>(`/data-migration/${fileSegment(type)}`, {
    params: { ...params, keyword: params.keyword || undefined, page: params.page ?? 1, size: params.size ?? 20 },
  })
}

/** 统一回收站列表：contentTypes 为内容类型数组，逗号拼接传参（Spring 侧按 List 解析）；
 * T26 新增分页与统一排序（后端以业务编号 asset_code 字典序升序，同编号时按 deleted_at DESC 回退）；
 * T32 追加 projectId 必填，回收站不再跨项目聚合。 */
export function listDataMigrationRecycleBin(params: { projectId: number; contentTypes?: string[]; keyword?: string; page?: number; size?: number }) {
  return http.get<ApiResponse<DataMigrationPage<DataMigrationContentRecycleRow>>>('/data-migration/recycle-bin', {
    params: {
      projectId: params.projectId,
      contentTypes: params.contentTypes?.length ? params.contentTypes.join(',') : undefined,
      keyword: params.keyword || undefined,
      page: params.page ?? 1,
      size: params.size ?? 20,
    },
  })
}

/** 获取统一回收站单条软删除详情（只读，不触发恢复/清理/下载）。 */
export function getDataMigrationRecycleBinDetail(type: string, id: number) {
  return http.get<ApiResponse<DataMigrationContentRecycleDetail>>(`/data-migration/recycle-bin/${encodeURIComponent(type)}/${id}`)
}

async function buildDataMigrationAssetUpload(projectId: number, file: File, componentId?: number, includeProjectId = true) {
  const attachment = await uploadAttachment(file)
  const attachmentId = attachment.data.data?.id
  if (!attachmentId) throw new Error('公共附件上传失败')
  const form = new FormData()
  if (includeProjectId) form.append('projectId', String(projectId))
  if (componentId != null) form.append('componentId', String(componentId))
  form.append('attachmentId', String(attachmentId))
  return form
}

export async function uploadDataMigrationAsset(type: string, projectId: number, file: File, componentId?: number) {
  const form = await buildDataMigrationAssetUpload(projectId, file, componentId)
  return http.post<ApiResponse<DataMigrationAsset>>(`/data-migration/${fileSegment(type)}/upload`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export async function replaceDataMigrationAsset(type: string, id: number, projectId: number, file: File, componentId?: number) {
  const form = await buildDataMigrationAssetUpload(projectId, file, componentId, false)
  return http.put<ApiResponse<DataMigrationAsset>>(`/data-migration/${fileSegment(type)}/${id}/upload`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
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

export function exportDataMigrationStructured(type: string, params: ProjectScopedParams) {
  return http.get(`${structuredBase(type)}/export`, { params, responseType: 'blob' })
}

export function inspectDataMigrationStructuredImport(type: string, projectId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<ApiResponse<Record<string, unknown>>>(`${structuredBase(type)}/import`, form, { params: { projectId }, headers: { 'Content-Type': 'multipart/form-data' } })
}

export function getDataMigrationDashboard(view: 'overall' | 'component', projectId: number) {
  return http.get<ApiResponse<Record<string, unknown> | Array<Record<string, unknown>>>>(`/data-migration/dashboard/${view}`, { params: { projectId } })
}

/* ============ 目标表结构 / 中间表结构（基础资料管理） ============ */
export type TableCategory = 'TARGET' | 'INTERMEDIATE'

export interface TargetTableField {
  field_code: number
  table_code: number
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
  table_code: number
  field_code?: number
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
  /** T32：表结构查询/导出必须限定项目。 */
  projectId: number
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

export function getTargetTable(tableCode: number, category?: TableCategory) {
  return http.get<ApiResponse<TargetTableRecord>>(`/data-migration/target-tables/${tableCode}`, { params: { category } })
}

export function createTargetTable(category: TableCategory, body: Record<string, unknown>) {
  return http.post<ApiResponse<TargetTableRecord>>(`/data-migration/target-tables`, body, { params: { category } })
}

export function updateTargetTable(tableCode: number, category: TableCategory, body: Record<string, unknown>) {
  return http.put<ApiResponse<TargetTableRecord>>(`/data-migration/target-tables/${tableCode}`, body, { params: { category } })
}

export function deleteTargetTables(category: TableCategory, tableCodes: number[]) {
  return http.post<ApiResponse<null>>(`/data-migration/target-tables/batch-delete`, tableCodes, { params: { category } })
}

export function listTargetTableFields(tableCode: number, category?: TableCategory) {
  return http.get<ApiResponse<TargetTableField[]>>(`/data-migration/target-tables/${tableCode}/fields`, { params: { category } })
}

export function addTargetTableField(tableCode: number, category: TableCategory, body: Record<string, unknown>) {
  return http.post<ApiResponse<TargetTableField>>(`/data-migration/target-tables/${tableCode}/fields`, body, { params: { category } })
}

export function updateTargetTableField(fieldCode: number, category: TableCategory, body: Record<string, unknown>) {
  return http.put<ApiResponse<TargetTableField>>(`/data-migration/target-table-fields/${fieldCode}`, body, { params: { category } })
}

export function deleteTargetTableField(fieldCode: number, category: TableCategory) {
  return http.delete<ApiResponse<null>>(`/data-migration/target-table-fields/${fieldCode}`, { params: { category } })
}

export function deleteTargetTableFields(category: TableCategory, fieldCodes: number[]) {
  return http.post<ApiResponse<null>>(`/data-migration/target-table-fields/batch-delete`, fieldCodes, { params: { category } })
}

export function importTargetTables(category: TableCategory, projectId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<ApiResponse<{ accepted: number; failed: number; errors: string[] }>>(`/data-migration/target-tables/import`, form, { params: { category, projectId }, headers: { 'Content-Type': 'multipart/form-data' } })
}

export function exportTargetTables(params: { category?: TableCategory; fieldCodes?: number[] } & Omit<TargetTableQuery, 'category' | 'page' | 'size'>) {
  return http.get(`/data-migration/target-tables/export`, { params: { ...params }, responseType: 'blob' })
}

export function downloadTargetTableTemplate() {
  return http.get(`/data-migration/target-tables/template`, { responseType: 'blob' })
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
  /** T32：列表与回收站必须限定项目。 */
  projectId: number
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
}

export interface ReportBatchUploadParams {
  projectId: number
  reportPeriod: string
  attachmentIds: number[]
}

export interface ReportUpdateParams {
  /** T32 决策 D2：维护接口不再接受 projectId，归属恒取库中记录。 */
  reportPeriod?: string
  reportName?: string
  reportDate?: string
  keywords?: string
  attachmentId?: number
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
  return http.post<ApiResponse<ReportMaterial>>('/data-migration/reports/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 批量上传汇报材料 */
export function batchUploadReportMaterials(params: ReportBatchUploadParams) {
  const form = new FormData()
  form.append('projectId', String(params.projectId))
  form.append('reportPeriod', params.reportPeriod)
  params.attachmentIds.forEach(id => form.append('attachmentIds', String(id)))
  return http.post<ApiResponse<ReportMaterial[]>>('/data-migration/reports/batch', form, { headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 编辑汇报材料（不得传 projectId，归属由服务端取库中记录） */
export function updateReportMaterial(id: number, params: ReportUpdateParams) {
  const form = new FormData()
  if (params.reportPeriod) form.append('reportPeriod', params.reportPeriod)
  if (params.reportName) form.append('reportName', params.reportName)
  if (params.reportDate) form.append('reportDate', params.reportDate)
  if (params.keywords) form.append('keywords', params.keywords)
  if (params.attachmentId) form.append('attachmentId', String(params.attachmentId))
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
  /** T32：问题清单查询/导出必须限定项目。 */
  projectId: number
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

/** T32 决策 D2：维护接口不再接受 projectId，归属恒取库中记录且不可变更。 */
export type IssueUpdateData = Omit<IssueFormData, 'projectId'> & Required<Pick<IssueFormData,
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

/** 回收站列表（T32：按项目隔离） */
export function listIssueRecycleBin(params: { projectId: number; keyword?: string; page?: number; size?: number }) {
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

/** 清空回收站（T32：仅清空所选项目内的软删问题） */
export function purgeAllIssues(projectId: number) {
  return http.delete<ApiResponse<null>>('/data-migration/issues/purge-all', { params: { projectId } })
}

/** 获取系统选项（根据项目） */
export function getIssueSystemOptions(projectId: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/issues/options/systems', { params: { projectId } })
}

/** 获取系统名称（根据系统编号） */
export function getIssueSystemName(systemCode: string) {
  return http.get<ApiResponse<string>>('/data-migration/issues/options/system-name', { params: { systemCode } })
}

/** 获取会议纪要选项（根据项目） */
export function getIssueMeetingOptions(projectId: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/issues/options/meetings', { params: { projectId } })
}

/** 获取目标表选项（根据项目） */
export function getIssueTargetTableOptions(projectId: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/issues/options/target-tables', { params: { projectId } })
}

/** 获取目标表字段选项（根据表ID） */
export function getIssueTargetFieldOptions(tableCode: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/issues/options/target-fields', { params: { tableCode } })
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
  system_codes?: string
  related_issue_names?: string
  related_issue_ids?: string
}

export interface MeetingQuery {
  /** T32：会议纪要查询必须限定项目。 */
  projectId: number
  meetingSource?: string
  granularity?: string
  systemCode?: string
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
  systemCodes?: string[]
  issueIds?: number[]
  attachmentId?: number
  fileName?: string
  attachments?: { attachmentId: number; fileName: string }[]
}

/** T32 决策 D2：维护接口不再接受 projectId，归属恒取库中记录且不可变更。 */
export type MeetingUpdateData = Omit<MeetingFormData, 'projectId'>

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

/** 更新会议纪要（不得传 projectId，归属由服务端取库中记录） */
export function updateMeeting(meetingId: number, body: MeetingUpdateData) {
  return http.put<ApiResponse<MeetingRecord>>(`/data-migration/meetings/${meetingId}`, body)
}

/** 批量删除会议纪要（逻辑删除） */
export function deleteMeetings(meetingIds: number[]) {
  return http.delete<ApiResponse<null>>('/data-migration/meetings', { data: meetingIds })
}

/** 会议回收站相关旧前端入口已于 T26 下线（无 web 端调用方，旧后端 @RequestMapping 同步已删）；统一回收站入口使用 listDataMigrationRecycleBin/restoreDataMigrationAssets/purgeDataMigrationAssets 与 asset_type=MEETING 分发。附件级回收站 (listMeetingAttachmentsRecycleBin 等) 仍保留在会议页。 */

/** 获取系统选项（根据项目） */
export function getMeetingSystemOptions(projectId: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/meetings/options/systems', { params: { projectId } })
}

/** 获取问题选项（根据项目） */
export function getMeetingIssueOptions(projectId: number) {
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

/** 全局附件回收站列表（跨会议，T32：按项目隔离） */
export function listAttachmentRecycleBin(params: { projectId: number; keyword?: string; page?: number; size?: number }) {
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

/** 清空附件回收站（T32：仅清空所选项目下会议的关系行） */
export function purgeAllAttachments(projectId: number) {
  return http.delete<ApiResponse<null>>('/data-migration/meetings/attachments/purge-all', { params: { projectId } })
}

// ========== 迁移方案专属接口（REQ-20260820-031 增量，对标会议纪要/汇报材料） ==========

export interface PlanAttachment {
  id: number
  attachment_id: number
  file_name: string
  sort_order: number
  created_by: number
  created_at?: string
}

export interface PlanRecord {
  id: number
  project_id: number
  project_name?: string
  /** PROJECT=项目级 / SYSTEM=系统级 */
  granularity: string
  /** BUSINESS=业务迁移方案 / DATA=数据迁移方案 */
  plan_type: string
  system_code: string
  system_name?: string
  asset_type?: string
  asset_code?: string
  asset_name?: string
  plan_summary?: string
  content_type?: string
  file_size?: number
  attachment_id?: number
  attachment_count?: number
  owner_id: number
  created_by: number
  created_at?: string
  updated_by?: number
  updated_at?: string
  deleted_by?: number
  deleted_at?: string
  attachments?: PlanAttachment[]
}

export interface PlanQuery {
  /** T32：迁移方案查询必须限定项目。 */
  projectId: number
  granularity?: string
  planType?: string
  systemCode?: string
  keyword?: string
  page?: number
  size?: number
}

/** 一条方案挂多文件：files 至少一个；planName 留空则后端取首个文件名（去扩展名）。 */
export interface PlanFormData {
  projectId: number
  granularity: string
  planType: string
  systemCode?: string
  planName?: string
  summary?: string
  files: { attachmentId: number; fileName?: string }[]
}

/** T32 决策 D2：维护接口不再接受 projectId，归属恒取库中记录；无文件变动时可不发送 files。 */
export type PlanUpdateData = Omit<PlanFormData, 'projectId' | 'files'> & { files?: PlanFormData['files'] }

/** 分页查询迁移方案列表 */
export function listPlans(params: PlanQuery) {
  return http.get<ApiResponse<DataMigrationPage<PlanRecord>>>('/data-migration/plans', { params })
}

/** 获取单条迁移方案详情（含多附件） */
export function getPlan(id: number) {
  return http.get<ApiResponse<PlanRecord>>(`/data-migration/plans/${id}`)
}

/** 新增迁移方案（单条/批量多文件） */
export function createPlan(body: PlanFormData) {
  return http.post<ApiResponse<PlanRecord>>('/data-migration/plans', body)
}

/** 编辑迁移方案（元数据 + 全量重设附件集合；不得传 projectId） */
export function updatePlan(id: number, body: PlanUpdateData) {
  return http.put<ApiResponse<PlanRecord>>(`/data-migration/plans/${id}`, body)
}

/** 批量逻辑删除迁移方案 */
export function deletePlans(ids: number[]) {
  return http.delete<ApiResponse<null>>('/data-migration/plans', { data: ids })
}

/** 获取主源文件的平台下载路径（前端再经 getAttachmentDownload 取流） */
export function getPlanDownloadPath(id: number) {
  return http.get<ApiResponse<string>>(`/data-migration/plans/${id}/download`)
}

/** 获取迁移方案附件列表 */
export function getPlanAttachments(id: number) {
  return http.get<ApiResponse<PlanAttachment[]>>(`/data-migration/plans/${id}/attachments`)
}

/** 获取关联系统选项（根据项目） */
export function getPlanSystemOptions(projectId: number) {
  return http.get<ApiResponse<SelectOption[]>>('/data-migration/plans/options/systems', { params: { projectId } })
}
