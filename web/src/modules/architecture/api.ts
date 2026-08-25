import http from '../../api/http'
import type { ApiResponse } from '../../types/auth'
import type {
  ArchitectureResource,
  AttachmentItemView,
  ConclusionEffectiveStatus,
  ConclusionView,
  CreateSubsystemChangeApplicationPayload,
  DecisionActionItem,
  DecisionMaterial,
  DecisionMatterDetail,
  DecisionMatterStatus,
  DecisionMatterSummary,
  DecisionReview,
  DecisionUserReference,
  FirstHandlingOutcome,
  DeploymentUnit,
  DeploymentUnitImportBatch,
  DeploymentUnitImportBatchDetail,
  DeploymentUnitOption,
  DeploymentUnitPayload,
  DeploymentUnitVersion,
  Environment,
  EnvironmentDetail,
  EnvironmentPayload,
  EnvironmentRecordStatus,
  EnvironmentType,
  LogicalSubsystem,
  LogicalSubsystemOption,
  MaterialKind,
  OrganizationOption,
  PageResult,
  ParameterOption,
  PhysicalSubsystem,
  PublicationIntentView,
  ReviewMethod,
  ResourceRequestDetail,
  ResourceRequestPayload,
  ResourceRequestStatus,
  ResourceRequestSummary,
  StandardCategory,
  StandardDocumentDetail,
  StandardDocumentStatus,
  StandardDocumentSummary,
  StandardVersion,
  PhysicalSubsystemOption,
  SubsystemApplicationStatus,
  SubsystemChangeApplicationDetail,
  SubsystemChangeApplicationSummary,
  SubsystemSuggestion,
  SupersessionKind,
  UpdateSubsystemChangeApplicationPayload,
  UserOption
} from './types'

type QueryValue = string | number | boolean | null | undefined
type Query = Record<string, QueryValue>

function compact(query: Query) {
  return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== '' && value !== null && value !== undefined))
}

export async function listLogicalSubsystems(query: Query) {
  return (await http.get<ApiResponse<PageResult<LogicalSubsystem>>>('/architecture/logical-subsystems', { params: compact(query) })).data.data
}

export async function getLogicalSubsystem(id: number) {
  return (await http.get<ApiResponse<LogicalSubsystem>>(`/architecture/logical-subsystems/${id}`)).data.data
}

export async function listPhysicalSubsystems(query: Query) {
  return (await http.get<ApiResponse<PageResult<PhysicalSubsystem>>>('/architecture/physical-subsystems', { params: compact(query) })).data.data
}

export async function getPhysicalSubsystem(id: number) {
  return (await http.get<ApiResponse<PhysicalSubsystem>>(`/architecture/physical-subsystems/${id}`)).data.data
}

export async function listSubsystemChangeApplications(query: {
  status?: SubsystemApplicationStatus | ''
  limit?: number
  offset?: number
}) {
  return (await http.get<ApiResponse<SubsystemChangeApplicationSummary[]>>(
    '/architecture/subsystem-change-applications',
    { params: compact(query) }
  )).data.data
}

export async function getSubsystemChangeApplication(id: number) {
  return (await http.get<ApiResponse<SubsystemChangeApplicationDetail>>(
    `/architecture/subsystem-change-applications/${id}`
  )).data.data
}

export async function createSubsystemChangeApplication(payload: CreateSubsystemChangeApplicationPayload) {
  return (await http.post<ApiResponse<SubsystemChangeApplicationDetail>>(
    '/architecture/subsystem-change-applications',
    payload
  )).data.data
}

export async function updateSubsystemChangeApplication(id: number, payload: UpdateSubsystemChangeApplicationPayload) {
  return (await http.put<ApiResponse<SubsystemChangeApplicationDetail>>(
    `/architecture/subsystem-change-applications/${id}`,
    payload
  )).data.data
}

export async function submitSubsystemChangeApplication(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<SubsystemChangeApplicationDetail>>(
    `/architecture/subsystem-change-applications/${id}/submit`,
    { rowVersion }
  )).data.data
}

export async function cancelSubsystemChangeApplication(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<SubsystemChangeApplicationDetail>>(
    `/architecture/subsystem-change-applications/${id}/cancel`,
    { rowVersion }
  )).data.data
}

/** 当前实现只访问本地 no-op provider；返回候选值但不自动回写表单。 */
export async function requestSubsystemSuggestions(fieldValues: Record<string, string>) {
  return (await http.post<ApiResponse<SubsystemSuggestion[]>>(
    '/architecture/subsystem-change-applications/suggestions',
    { fieldValues }
  )).data.data
}

export async function loadOrganizationOptions(resource: ArchitectureResource, keyword = '', size = 50) {
  return (await http.get<ApiResponse<PageResult<OrganizationOption>>>(`/architecture/options/${resource}/organizations`, {
    params: compact({ page: 1, size, keyword })
  })).data.data.records
}

export async function loadUserOptions(resource: ArchitectureResource, keyword = '', size = 50) {
  return (await http.get<ApiResponse<PageResult<UserOption>>>(`/architecture/options/${resource}/users`, {
    params: compact({ page: 1, size, keyword })
  })).data.data.records
}

export async function loadParameterOptions(resource: ArchitectureResource, categoryCode: string) {
  return (await http.get<ApiResponse<ParameterOption[]>>(`/architecture/options/${resource}/parameters/${categoryCode}`)).data.data
}

export async function loadLogicalSubsystemOptions(keyword = '', size = 50) {
  const filter = keyword && /^[A-Za-z0-9_-]+$/.test(keyword) ? { code: keyword } : { name: keyword }
  return (await http.get<ApiResponse<PageResult<LogicalSubsystemOption>>>('/architecture/options/physical-subsystem/logical-subsystems', {
    params: compact({ page: 1, size, ...filter })
  })).data.data.records
}

// ---------- 架构规范 ----------

export async function listStandardCategories() {
  return (await http.get<ApiResponse<StandardCategory[]>>('/architecture/standards/categories')).data.data
}

export async function listStandardDocuments(query: {
  page?: number
  size?: number
  title?: string
  categoryCode?: string
  status?: StandardDocumentStatus | ''
}) {
  return (await http.get<ApiResponse<PageResult<StandardDocumentSummary>>>('/architecture/standards', {
    params: compact(query)
  })).data.data
}

export async function getStandardDocument(id: number) {
  return (await http.get<ApiResponse<StandardDocumentDetail>>(`/architecture/standards/${id}`)).data.data
}

export async function listStandardVersions(id: number) {
  return (await http.get<ApiResponse<StandardVersion[]>>(`/architecture/standards/${id}/versions`)).data.data
}

export async function createStandardDocument(payload: { title: string; categoryCode: string; summary?: string | null; content?: string | null }) {
  return (await http.post<ApiResponse<StandardDocumentDetail>>('/architecture/standards', payload)).data.data
}

export async function updateStandardDocument(id: number, payload: { rowVersion: number; title: string; categoryCode: string; summary?: string | null; content?: string | null }) {
  return (await http.put<ApiResponse<StandardDocumentDetail>>(`/architecture/standards/${id}`, payload)).data.data
}

export async function publishStandardDocument(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<StandardVersion>>(`/architecture/standards/${id}/publish`, { rowVersion })).data.data
}

export async function offlineStandardDocument(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<StandardDocumentDetail>>(`/architecture/standards/${id}/offline`, { rowVersion })).data.data
}

export async function deleteStandardDocument(id: number, rowVersion: number) {
  return (await http.delete<ApiResponse<void>>(`/architecture/standards/${id}`, { params: { rowVersion } })).data
}

export async function listStandardAttachments(id: number) {
  return (await http.get<ApiResponse<AttachmentItemView[]>>(`/architecture/standards/${id}/attachments`)).data.data
}

export async function bindStandardAttachment(id: number, attachmentId: number) {
  return (await http.post<ApiResponse<void>>(`/architecture/standards/${id}/attachments`, { attachmentId })).data
}

export async function deleteStandardAttachment(id: number, attachmentId: number) {
  return (await http.delete<ApiResponse<void>>(`/architecture/standards/${id}/attachments/${attachmentId}`)).data
}

// ---------- 架构决策 ----------

export async function listDecisionTypes() {
  return (await http.get<ApiResponse<StandardCategory[]>>('/architecture/decisions/options/types')).data.data
}

export async function searchDecisionUsers(keyword = '') {
  return (await http.get<ApiResponse<DecisionUserReference[]>>('/architecture/decisions/options/users', {
    params: compact({ keyword })
  })).data.data
}

export async function listDecisionMatters(query: {
  page?: number
  size?: number
  keyword?: string
  typeCode?: string
  status?: DecisionMatterStatus | ''
  firstHandlingOverdue?: boolean
}) {
  return (await http.get<ApiResponse<PageResult<DecisionMatterSummary>>>('/architecture/decisions', {
    params: compact(query)
  })).data.data
}

export async function getDecisionMatter(id: number) {
  return (await http.get<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}`)).data.data
}

export async function createDecisionMatter(payload: { title: string; problem: string }) {
  return (await http.post<ApiResponse<DecisionMatterDetail>>('/architecture/decisions', payload)).data.data
}

export async function updateDecisionMatter(id: number, payload: { rowVersion: number; title: string; problem: string }) {
  return (await http.put<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}`, payload)).data.data
}

export async function addDecisionMaterial(id: number, payload: { kind: MaterialKind; content: string }) {
  return (await http.post<ApiResponse<DecisionMaterial>>(`/architecture/decisions/${id}/materials`, payload)).data.data
}

export async function setDecisionType(id: number, payload: { rowVersion: number; typeCode: string }) {
  return (await http.post<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}/type`, payload)).data.data
}

export async function firstHandlingDecisionMatter(id: number, payload: {
  rowVersion: number
  outcome: FirstHandlingOutcome
  reviewMode?: ReviewMethod | null
  comment?: string | null
}) {
  return (await http.post<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}/first-handling`, payload)).data.data
}

export async function resubmitDecisionMatter(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}/resubmit`, { rowVersion })).data.data
}

export interface ReviewPayload {
  method: ReviewMethod
  reviewedAt?: string | null
  processMaterialSummary?: string | null
  keyOpinion?: string | null
  conclusionContent?: string | null
  conclusionRationale?: string | null
  participantUserIds?: number[]
  actionItems?: { id?: number | null; content: string; ownerUserId?: number | null; ownerName?: string | null }[]
}

export async function recordDecisionReview(id: number, payload: ReviewPayload) {
  return (await http.post<ApiResponse<DecisionReview>>(`/architecture/decisions/${id}/reviews`, payload)).data.data
}

export async function updateDecisionReview(id: number, reviewId: number, payload: ReviewPayload) {
  return (await http.put<ApiResponse<DecisionReview>>(`/architecture/decisions/${id}/reviews/${reviewId}`, payload)).data.data
}

export async function completeDecisionActionItem(id: number, reviewId: number, actionItemId: number) {
  return (await http.post<ApiResponse<DecisionActionItem>>(
    `/architecture/decisions/${id}/reviews/${reviewId}/action-items/${actionItemId}/complete`
  )).data.data
}

export async function prepareDecisionPublication(id: number, payload: {
  rowVersion: number
  reviewId: number
  targets: { conclusionId: number; kind: SupersessionKind }[]
}) {
  return (await http.post<ApiResponse<PublicationIntentView>>(
    `/architecture/decisions/${id}/publication/prepare`, payload)).data.data
}

export async function startDecisionPublication(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<DecisionMatterDetail>>(
    `/architecture/decisions/${id}/publication/start`, { rowVersion })).data.data
}

export async function listDecisionConclusions(query: { page?: number; size?: number; effectiveStatus?: ConclusionEffectiveStatus | '' }) {
  return (await http.get<ApiResponse<PageResult<ConclusionView>>>('/architecture/decisions/conclusions', {
    params: compact(query)
  })).data.data
}

export async function getDecisionConclusionChain(conclusionId: number) {
  return (await http.get<ApiResponse<ConclusionView>>(`/architecture/decisions/conclusions/${conclusionId}/chain`)).data.data
}

export async function listDecisionAttachments(id: number) {
  return (await http.get<ApiResponse<AttachmentItemView[]>>(`/architecture/decisions/${id}/attachments`)).data.data
}

export async function bindDecisionAttachment(id: number, attachmentId: number) {
  return (await http.post<ApiResponse<void>>(`/architecture/decisions/${id}/attachments`, { attachmentId })).data
}

export async function deleteDecisionAttachment(id: number, attachmentId: number) {
  return (await http.delete<ApiResponse<void>>(`/architecture/decisions/${id}/attachments/${attachmentId}`)).data
}

// ---------- 部署单元 ----------

export async function listDeploymentUnits(query: Query) {
  return (await http.get<ApiResponse<PageResult<DeploymentUnit>>>('/architecture/deployment-units', { params: compact(query) })).data.data
}

export async function getDeploymentUnit(id: number) {
  return (await http.get<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}`)).data.data
}

export async function getDeploymentUnitVersions(id: number) {
  return (await http.get<ApiResponse<DeploymentUnitVersion[]>>(`/architecture/deployment-units/${id}/versions`)).data.data
}

export async function createDeploymentUnit(payload: DeploymentUnitPayload) {
  return (await http.post<ApiResponse<DeploymentUnit>>('/architecture/deployment-units', payload)).data.data
}

export async function updateDeploymentUnit(id: number, payload: DeploymentUnitPayload) {
  return (await http.put<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}`, payload)).data.data
}

export async function deactivateDeploymentUnit(id: number) {
  return (await http.post<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}/deactivate`)).data.data
}

export async function reactivateDeploymentUnit(id: number) {
  return (await http.post<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}/reactivate`)).data.data
}

export async function voidDeploymentUnit(id: number) {
  return (await http.post<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}/void`)).data.data
}

export async function loadPhysicalSubsystemOptions(keyword = '', size = 50) {
  const filter = keyword && /^[A-Za-z0-9_-]+$/.test(keyword) ? { code: keyword } : { name: keyword }
  return (await http.get<ApiResponse<PageResult<PhysicalSubsystemOption>>>('/architecture/options/deployment-unit/physical-subsystems', {
    params: compact({ page: 1, size, ...filter })
  })).data.data.records
}

// ---------- 具体环境与资源申请 ----------

export async function listEnvironmentTypes(query: { status?: EnvironmentRecordStatus | '' } = {}) {
  return (await http.get<ApiResponse<EnvironmentType[]>>('/architecture/environment-types', {
    params: compact(query)
  })).data.data
}

export async function listEnvironments(query: {
  typeCode?: string | null
  status?: EnvironmentRecordStatus | ''
  keyword?: string
  limit?: number
  offset?: number
}) {
  return (await http.get<ApiResponse<Environment[]>>('/architecture/environments', {
    params: compact(query)
  })).data.data
}

export async function getEnvironment(id: number) {
  return (await http.get<ApiResponse<EnvironmentDetail>>(`/architecture/environments/${id}`)).data.data
}

export async function createEnvironment(payload: EnvironmentPayload) {
  return (await http.post<ApiResponse<Environment>>('/architecture/environments', payload)).data.data
}

export async function updateEnvironment(id: number, payload: EnvironmentPayload) {
  return (await http.put<ApiResponse<Environment>>(`/architecture/environments/${id}`, payload)).data.data
}

export async function deactivateEnvironment(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<Environment>>(`/architecture/environments/${id}/deactivate`, { rowVersion })).data.data
}

export async function reactivateEnvironment(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<Environment>>(`/architecture/environments/${id}/reactivate`, { rowVersion })).data.data
}

export async function deleteEnvironment(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<void>>(`/architecture/environments/${id}/delete`, { rowVersion })).data
}

export async function loadResourceDeploymentUnitOptions(physicalSubsystemId: number, limit = 100) {
  return (await http.get<ApiResponse<DeploymentUnitOption[]>>('/architecture/resource-requests/options/deployment-units', {
    params: compact({ physicalSubsystemId, limit })
  })).data.data
}

export async function listResourceRequests(query: {
  status?: ResourceRequestStatus | ''
  environmentId?: number | null
  physicalSubsystemId?: number | null
  limit?: number
  offset?: number
}) {
  return (await http.get<ApiResponse<ResourceRequestSummary[]>>('/architecture/resource-requests', {
    params: compact(query)
  })).data.data
}

export async function getResourceRequest(id: number) {
  return (await http.get<ApiResponse<ResourceRequestDetail>>(`/architecture/resource-requests/${id}`)).data.data
}

export async function createResourceRequest(payload: ResourceRequestPayload) {
  return (await http.post<ApiResponse<ResourceRequestDetail>>('/architecture/resource-requests', payload)).data.data
}

export async function updateResourceRequest(id: number, payload: ResourceRequestPayload) {
  return (await http.put<ApiResponse<ResourceRequestDetail>>(`/architecture/resource-requests/${id}`, payload)).data.data
}

export async function submitResourceRequest(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<ResourceRequestDetail>>(`/architecture/resource-requests/${id}/submit`, { rowVersion })).data.data
}

export async function cancelResourceRequest(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<ResourceRequestDetail>>(`/architecture/resource-requests/${id}/cancel`, { rowVersion })).data.data
}

// ---------- 部署单元初始化导入 ----------

export async function uploadDeploymentUnitImport(file: File) {
  const data = new FormData()
  data.append('file', file)
  return (await http.post<ApiResponse<DeploymentUnitImportBatchDetail>>('/architecture/deployment-unit-imports', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })).data.data
}

export async function listDeploymentUnitImports(query: { page?: number; size?: number }) {
  return (await http.get<ApiResponse<PageResult<DeploymentUnitImportBatch>>>('/architecture/deployment-unit-imports', { params: compact(query) })).data.data
}

export async function getDeploymentUnitImport(id: number) {
  return (await http.get<ApiResponse<DeploymentUnitImportBatchDetail>>(`/architecture/deployment-unit-imports/${id}`)).data.data
}

export async function confirmDeploymentUnitImport(id: number) {
  return (await http.post<ApiResponse<DeploymentUnitImportBatchDetail>>(`/architecture/deployment-unit-imports/${id}/confirm`)).data.data
}

export async function downloadDeploymentUnitImportErrorReport(id: number) {
  const response = await http.get<Blob>(`/architecture/deployment-unit-imports/${id}/error-report`, { responseType: 'blob' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(response.data)
  link.download = `deployment-unit-import-${id}-errors.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

export async function downloadDeploymentUnitImportTemplate() {
  const response = await http.get<Blob>('/architecture/deployment-unit-imports/template', { responseType: 'blob' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(response.data)
  link.download = 'deployment-unit-import-template.xlsx'
  link.click()
  URL.revokeObjectURL(link.href)
}
