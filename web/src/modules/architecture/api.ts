import http from '../../api/http'
import { useProjectContextStore } from '../../stores/project-context'
import type { ApiResponse } from '../../types/auth'
import type { AxiosRequestConfig } from 'axios'
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
  DeliveryUnit,
  DeliveryUnitPayload,
  RelatedDeliveryUnit,
  DeploymentUnit,
  DeploymentUnitImportBatch,
  DeploymentUnitImportBatchDetail,
  DeploymentUnitOption,
  DeploymentUnitPayload,
  DeploymentUnitVersion,
  DisasterRecoveryPayload,
  Environment,
  EnvironmentDetail,
  EnvironmentInstance,
  EnvironmentPayload,
  EnvironmentRecordStatus,
  EnvironmentType,
  ExternalNetworkAddress,
  FulfillmentPayload,
  InstanceDisasterRecovery,
  InstanceStatus,
  MaterialKind,
  ManagedEndpointInstance,
  NetworkAccessApplication,
  NetworkAccessApplicationStatus,
  NetworkAccessDecisionPayload,
  NetworkAccessDecisionResult,
  NetworkAccessExemptionRule,
  NetworkAccessExemptionRulePayload,
  NetworkAccessExemptionRuleStatus,
  NetworkAccessPayload,
  NetworkAccessRelation,
  NetworkAccessRelationStatus,
  OfflineInstancePayload,
  OrganizationOption,
  PageResult,
  ParameterOption,
  PhysicalSubsystem,
  SubsystemParticipation,
  SubsystemParticipantCandidate,
  SubsystemParticipationPayload,
  ProvisionPreviewResult,
  PublicationIntentView,
  RelatedDeploymentUnit,
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
  NetworkZone,
  NetworkZoneOption,
  NetworkZoneSubnet,
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

function projectConfig(config: AxiosRequestConfig = {}): AxiosRequestConfig {
  const projectRef = useProjectContextStore().currentRef
  if (!projectRef) throw new Error('请先选择项目')
  return { ...config, params: { ...config.params, projectRef } }
}

const projectHttp = {
  get: <T>(url: string, config?: AxiosRequestConfig) => http.get<T>(url, projectConfig(config)),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => http.post<T>(url, data, projectConfig(config)),
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => http.put<T>(url, data, projectConfig(config)),
  delete: <T>(url: string, config?: AxiosRequestConfig) => http.delete<T>(url, projectConfig(config))
}

export async function listPhysicalSubsystems(query: Query) {
  return (await projectHttp.get<ApiResponse<PageResult<PhysicalSubsystem>>>('/architecture/physical-subsystems', { params: compact(query) })).data.data
}

export async function getPhysicalSubsystem(id: number) {
  return (await projectHttp.get<ApiResponse<PhysicalSubsystem>>(`/architecture/physical-subsystems/${id}`)).data.data
}

export async function listSubsystemChangeApplications(query: {
  status?: SubsystemApplicationStatus | ''
  limit?: number
  offset?: number
}) {
  return (await projectHttp.get<ApiResponse<SubsystemChangeApplicationSummary[]>>(
    '/architecture/subsystem-change-applications',
    { params: compact(query) }
  )).data.data
}

export async function getSubsystemChangeApplication(id: number) {
  return (await projectHttp.get<ApiResponse<SubsystemChangeApplicationDetail>>(
    `/architecture/subsystem-change-applications/${id}`
  )).data.data
}

export async function createSubsystemChangeApplication(payload: CreateSubsystemChangeApplicationPayload) {
  return (await projectHttp.post<ApiResponse<SubsystemChangeApplicationDetail>>(
    '/architecture/subsystem-change-applications',
    payload
  )).data.data
}

export async function updateSubsystemChangeApplication(id: number, payload: UpdateSubsystemChangeApplicationPayload) {
  return (await projectHttp.put<ApiResponse<SubsystemChangeApplicationDetail>>(
    `/architecture/subsystem-change-applications/${id}`,
    payload
  )).data.data
}

export async function submitSubsystemChangeApplication(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<SubsystemChangeApplicationDetail>>(
    `/architecture/subsystem-change-applications/${id}/submit`,
    { rowVersion }
  )).data.data
}

export async function cancelSubsystemChangeApplication(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<SubsystemChangeApplicationDetail>>(
    `/architecture/subsystem-change-applications/${id}/cancel`,
    { rowVersion }
  )).data.data
}

/** 当前实现只访问本地 no-op provider；返回候选值但不自动回写表单。 */
export async function requestSubsystemSuggestions(fieldValues: Record<string, string>) {
  return (await projectHttp.post<ApiResponse<SubsystemSuggestion[]>>(
    '/architecture/subsystem-change-applications/suggestions',
    { fieldValues }
  )).data.data
}

export async function loadOrganizationOptions(resource: ArchitectureResource, keyword = '', size = 50) {
  return (await projectHttp.get<ApiResponse<PageResult<OrganizationOption>>>(`/architecture/options/${resource}/organizations`, {
    params: compact({ page: 1, size, keyword })
  })).data.data.records
}

export async function loadUserOptions(resource: ArchitectureResource, keyword = '', size = 50) {
  return (await projectHttp.get<ApiResponse<PageResult<UserOption>>>(`/architecture/options/${resource}/users`, {
    params: compact({ page: 1, size, keyword })
  })).data.data.records
}

export async function loadParameterOptions(resource: ArchitectureResource, categoryCode: string) {
  return (await projectHttp.get<ApiResponse<ParameterOption[]>>(`/architecture/options/${resource}/parameters/${categoryCode}`)).data.data
}

export async function loadBusinessComponentOptions() {
  return (await projectHttp.get<ApiResponse<ParameterOption[]>>('/architecture/options/physical-subsystem/business-components')).data.data
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
  return (await projectHttp.get<ApiResponse<StandardCategory[]>>('/architecture/decisions/options/types')).data.data
}

export async function searchDecisionUsers(keyword = '') {
  return (await projectHttp.get<ApiResponse<DecisionUserReference[]>>('/architecture/decisions/options/users', {
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
  return (await projectHttp.get<ApiResponse<PageResult<DecisionMatterSummary>>>('/architecture/decisions', {
    params: compact(query)
  })).data.data
}

export async function getDecisionMatter(id: number) {
  return (await projectHttp.get<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}`)).data.data
}

export async function listDecisionMaterials(id: number) {
  return (await projectHttp.get<ApiResponse<DecisionMaterial[]>>(`/architecture/decisions/${id}/materials`)).data.data
}

export async function listDecisionReviews(id: number) {
  return (await projectHttp.get<ApiResponse<DecisionReview[]>>(`/architecture/decisions/${id}/reviews`)).data.data
}

export async function listDecisionReviewParticipants(id: number, reviewId: number) {
  return (await projectHttp.get<ApiResponse<{ userId: number; displayName: string }[]>>(
    `/architecture/decisions/${id}/reviews/${reviewId}/participants`
  )).data.data
}

export async function listDecisionReviewActionItems(id: number, reviewId: number) {
  return (await projectHttp.get<ApiResponse<DecisionActionItem[]>>(
    `/architecture/decisions/${id}/reviews/${reviewId}/action-items`
  )).data.data
}

export async function createDecisionMatter(payload: { title: string; problem: string }) {
  return (await projectHttp.post<ApiResponse<DecisionMatterDetail>>('/architecture/decisions', payload)).data.data
}

export async function updateDecisionMatter(id: number, payload: { rowVersion: number; title: string; problem: string }) {
  return (await projectHttp.put<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}`, payload)).data.data
}

export async function addDecisionMaterial(id: number, payload: { kind: MaterialKind; content: string }) {
  return (await projectHttp.post<ApiResponse<DecisionMaterial>>(`/architecture/decisions/${id}/materials`, payload)).data.data
}

export async function setDecisionType(id: number, payload: { rowVersion: number; typeCode: string }) {
  return (await projectHttp.post<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}/type`, payload)).data.data
}

export async function firstHandlingDecisionMatter(id: number, payload: {
  rowVersion: number
  outcome: FirstHandlingOutcome
  reviewMode?: ReviewMethod | null
  comment?: string | null
}) {
  return (await projectHttp.post<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}/first-handling`, payload)).data.data
}

export async function resubmitDecisionMatter(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<DecisionMatterDetail>>(`/architecture/decisions/${id}/resubmit`, { rowVersion })).data.data
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
  return (await projectHttp.post<ApiResponse<DecisionReview>>(`/architecture/decisions/${id}/reviews`, payload)).data.data
}

export async function updateDecisionReview(id: number, reviewId: number, payload: ReviewPayload) {
  return (await projectHttp.put<ApiResponse<DecisionReview>>(`/architecture/decisions/${id}/reviews/${reviewId}`, payload)).data.data
}

export async function completeDecisionActionItem(id: number, reviewId: number, actionItemId: number) {
  return (await projectHttp.post<ApiResponse<DecisionActionItem>>(
    `/architecture/decisions/${id}/reviews/${reviewId}/action-items/${actionItemId}/complete`
  )).data.data
}

export async function prepareDecisionPublication(id: number, payload: {
  rowVersion: number
  reviewId: number
  targets: { conclusionId: number; kind: SupersessionKind }[]
}) {
  return (await projectHttp.post<ApiResponse<PublicationIntentView>>(
    `/architecture/decisions/${id}/publication/prepare`, payload)).data.data
}

export async function startDecisionPublication(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<DecisionMatterDetail>>(
    `/architecture/decisions/${id}/publication/start`, { rowVersion })).data.data
}

export async function listDecisionConclusions(query: { page?: number; size?: number; effectiveStatus?: ConclusionEffectiveStatus | '' }) {
  return (await projectHttp.get<ApiResponse<PageResult<ConclusionView>>>('/architecture/decisions/conclusions', {
    params: compact(query)
  })).data.data
}

export async function getDecisionConclusionChain(conclusionId: number) {
  return (await projectHttp.get<ApiResponse<ConclusionView>>(`/architecture/decisions/conclusions/${conclusionId}/chain`)).data.data
}

export async function listDecisionAttachments(id: number) {
  return (await projectHttp.get<ApiResponse<AttachmentItemView[]>>(`/architecture/decisions/${id}/attachments`)).data.data
}

export async function bindDecisionAttachment(id: number, attachmentId: number) {
  return (await projectHttp.post<ApiResponse<void>>(`/architecture/decisions/${id}/attachments`, { attachmentId })).data
}

export async function deleteDecisionAttachment(id: number, attachmentId: number) {
  return (await projectHttp.delete<ApiResponse<void>>(`/architecture/decisions/${id}/attachments/${attachmentId}`)).data
}

// ---------- 部署单元 ----------

export async function listDeploymentUnits(query: Query) {
  return (await projectHttp.get<ApiResponse<PageResult<DeploymentUnit>>>('/architecture/deployment-units', { params: compact(query) })).data.data
}

export async function searchDeploymentUnitOptions(query: {
  keyword?: string
  page?: number
  size?: number
  excludeId?: number | null
}) {
  return (await projectHttp.get<ApiResponse<PageResult<RelatedDeploymentUnit>>>('/architecture/deployment-units/options', {
    params: compact(query)
  })).data.data
}

export async function getDeploymentUnit(id: number) {
  return (await projectHttp.get<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}`)).data.data
}

export async function getDeploymentUnitVersions(id: number) {
  return (await projectHttp.get<ApiResponse<DeploymentUnitVersion[]>>(`/architecture/deployment-units/${id}/versions`)).data.data
}

export async function createDeploymentUnit(payload: DeploymentUnitPayload) {
  return (await projectHttp.post<ApiResponse<DeploymentUnit>>('/architecture/deployment-units', payload)).data.data
}

export async function updateDeploymentUnit(id: number, payload: DeploymentUnitPayload) {
  return (await projectHttp.put<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}`, payload)).data.data
}

export async function deactivateDeploymentUnit(id: number) {
  return (await projectHttp.post<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}/deactivate`)).data.data
}

export async function reactivateDeploymentUnit(id: number) {
  return (await projectHttp.post<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}/reactivate`)).data.data
}

export async function voidDeploymentUnit(id: number) {
  return (await projectHttp.post<ApiResponse<DeploymentUnit>>(`/architecture/deployment-units/${id}/void`)).data.data
}

/** 部署单元侧只读反查：某部署单元关联的交付单元。 */
export async function listDeploymentUnitDeliveryUnits(id: number) {
  return (await projectHttp.get<ApiResponse<RelatedDeliveryUnit[]>>(`/architecture/deployment-units/${id}/delivery-units`)).data.data
}

/** 部署单元侧覆盖式更新关联交付单元集合，与交付单元侧共享同一份关系数据。 */
export async function replaceDeploymentUnitDeliveryUnits(id: number, deliveryUnitIds: number[]) {
  return (await projectHttp.put<ApiResponse<RelatedDeliveryUnit[]>>(`/architecture/deployment-units/${id}/delivery-units`, { deploymentUnitIds: deliveryUnitIds })).data.data
}

/** 部署单元侧的交付单元候选，只返回同一物理子系统下的启用交付单元。 */
export async function searchDeploymentUnitDeliveryUnitOptions(query: {
  deploymentUnitId: number
  keyword?: string
  page?: number
  size?: number
}) {
  const { deploymentUnitId, ...rest } = query
  return (await projectHttp.get<ApiResponse<PageResult<RelatedDeliveryUnit>>>(`/architecture/deployment-units/${deploymentUnitId}/delivery-unit-options`, {
    params: compact(rest)
  })).data.data
}

// ---------- 交付单元 ----------

export async function listDeliveryUnits(query: Query) {
  return (await projectHttp.get<ApiResponse<PageResult<DeliveryUnit>>>('/architecture/delivery-units', { params: compact(query) })).data.data
}

export async function getDeliveryUnit(id: number) {
  return (await projectHttp.get<ApiResponse<DeliveryUnit>>(`/architecture/delivery-units/${id}`)).data.data
}

export async function createDeliveryUnit(payload: DeliveryUnitPayload) {
  return (await projectHttp.post<ApiResponse<DeliveryUnit>>('/architecture/delivery-units', payload)).data.data
}

export async function updateDeliveryUnit(id: number, payload: DeliveryUnitPayload) {
  return (await projectHttp.put<ApiResponse<DeliveryUnit>>(`/architecture/delivery-units/${id}`, payload)).data.data
}

export async function replaceDeliveryUnitDeploymentUnits(id: number, deploymentUnitIds: number[]) {
  return (await projectHttp.put<ApiResponse<DeliveryUnit>>(`/architecture/delivery-units/${id}/deployment-units`, { deploymentUnitIds })).data.data
}

export async function deactivateDeliveryUnit(id: number) {
  return (await projectHttp.post<ApiResponse<DeliveryUnit>>(`/architecture/delivery-units/${id}/deactivate`)).data.data
}

export async function reactivateDeliveryUnit(id: number) {
  return (await projectHttp.post<ApiResponse<DeliveryUnit>>(`/architecture/delivery-units/${id}/reactivate`)).data.data
}

export async function deleteDeliveryUnit(id: number) {
  return (await projectHttp.delete<ApiResponse<void>>(`/architecture/delivery-units/${id}`)).data
}

/** 交付单元关联选择用的部署单元候选，只返回同一物理子系统下的启用部署单元。 */
export async function searchDeliveryUnitDeploymentUnitOptions(query: {
  physicalSubsystemId: number
  keyword?: string
  page?: number
  size?: number
  excludeId?: number | null
}) {
  return (await projectHttp.get<ApiResponse<PageResult<RelatedDeploymentUnit>>>('/architecture/delivery-units/deployment-unit-options', {
    params: compact(query)
  })).data.data
}

/** 交付单元归属物理子系统候选，使用交付单元自身权限。 */
export async function loadDeliveryUnitPhysicalSubsystemOptions(keyword = '', size = 50) {
  const filter = keyword && /^[A-Za-z0-9_-]+$/.test(keyword) ? { code: keyword } : { name: keyword }
  return (await projectHttp.get<ApiResponse<PageResult<PhysicalSubsystemOption>>>('/architecture/options/delivery-unit/physical-subsystems', {
    params: compact({ page: 1, size, ...filter })
  })).data.data.records
}

export async function loadParticipatingPhysicalOptions() {
  const result: PhysicalSubsystemOption[] = []
  for (let page = 1; ; page++) {
    const data = (await projectHttp.get<ApiResponse<PageResult<PhysicalSubsystemOption>>>('/architecture/options/resource-request/physical-subsystems', { params: { page, size: 100 } })).data.data
    result.push(...data.records)
    if (!data.records.length || result.length >= data.total) return result
  }
}

export async function loadPhysicalSubsystemOptions(keyword = '', size = 50) {
  const filter = keyword && /^[A-Za-z0-9_-]+$/.test(keyword) ? { code: keyword } : { name: keyword }
  return (await projectHttp.get<ApiResponse<PageResult<PhysicalSubsystemOption>>>('/architecture/options/deployment-unit/physical-subsystems', {
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
  return (await projectHttp.get<ApiResponse<Environment[]>>('/architecture/environments', {
    params: compact(query)
  })).data.data
}

export async function getEnvironment(id: number) {
  return (await projectHttp.get<ApiResponse<EnvironmentDetail>>(`/architecture/environments/${id}`)).data.data
}

export async function createEnvironment(payload: EnvironmentPayload) {
  return (await projectHttp.post<ApiResponse<Environment>>('/architecture/environments', payload)).data.data
}

export async function updateEnvironment(id: number, payload: EnvironmentPayload) {
  return (await projectHttp.put<ApiResponse<Environment>>(`/architecture/environments/${id}`, payload)).data.data
}

export async function deactivateEnvironment(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<Environment>>(`/architecture/environments/${id}/deactivate`, { rowVersion })).data.data
}

export async function reactivateEnvironment(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<Environment>>(`/architecture/environments/${id}/reactivate`, { rowVersion })).data.data
}

export async function deleteEnvironment(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<void>>(`/architecture/environments/${id}/delete`, { rowVersion })).data
}

export async function loadResourceDeploymentUnitOptions(physicalSubsystemId: number, limit = 100) {
  return (await projectHttp.get<ApiResponse<DeploymentUnitOption[]>>('/architecture/resource-requests/options/deployment-units', {
    params: compact({ physicalSubsystemId, limit })
  })).data.data
}

export async function listNetworkZones(query: {
  status?: EnvironmentRecordStatus | ''
  keyword?: string
} = {}) {
  return (await projectHttp.get<ApiResponse<NetworkZone[]>>('/architecture/network-zones', {
    params: compact(query)
  })).data.data
}

export async function loadNetworkZoneOptions(leafOnly = true) {
  return (await projectHttp.get<ApiResponse<NetworkZoneOption[]>>('/architecture/network-zones/options', {
    params: compact({ leafOnly })
  })).data.data
}

export async function createNetworkZone(payload: {
  parentId?: number | null
  code: string
  name: string
  restrictionLevel: number
  description?: string | null
  remark?: string | null
}) {
  return (await projectHttp.post<ApiResponse<NetworkZone>>('/architecture/network-zones', payload)).data.data
}

export async function updateNetworkZone(id: number, payload: {
  parentId?: number | null
  code: string
  name: string
  restrictionLevel: number
  description?: string | null
  remark?: string | null
  rowVersion: number
}) {
  return (await projectHttp.put<ApiResponse<NetworkZone>>(`/architecture/network-zones/${id}`, payload)).data.data
}

export async function deactivateNetworkZone(id: number) {
  return (await projectHttp.post<ApiResponse<NetworkZone>>(`/architecture/network-zones/${id}/deactivate`)).data.data
}

export async function reactivateNetworkZone(id: number) {
  return (await projectHttp.post<ApiResponse<NetworkZone>>(`/architecture/network-zones/${id}/reactivate`)).data.data
}

export async function listNetworkZoneSubnets(zoneId: number, query: {
  status?: EnvironmentRecordStatus | ''
} = {}) {
  return (await projectHttp.get<ApiResponse<NetworkZoneSubnet[]>>(`/architecture/network-zones/${zoneId}/subnets`, {
    params: compact(query)
  })).data.data
}

export async function createNetworkZoneSubnet(zoneId: number, payload: {
  cidrBlock: string
  gatewayIp?: string | null
  purpose?: string | null
  remark?: string | null
}) {
  return (await projectHttp.post<ApiResponse<NetworkZoneSubnet>>(`/architecture/network-zones/${zoneId}/subnets`, payload)).data.data
}

export async function updateNetworkZoneSubnet(zoneId: number, subnetId: number, payload: {
  cidrBlock: string
  gatewayIp?: string | null
  purpose?: string | null
  remark?: string | null
  rowVersion: number
}) {
  return (await projectHttp.put<ApiResponse<NetworkZoneSubnet>>(`/architecture/network-zones/${zoneId}/subnets/${subnetId}`, payload)).data.data
}

export async function deactivateNetworkZoneSubnet(zoneId: number, subnetId: number) {
  return (await projectHttp.post<ApiResponse<NetworkZoneSubnet>>(`/architecture/network-zones/${zoneId}/subnets/${subnetId}/deactivate`)).data.data
}

export async function reactivateNetworkZoneSubnet(zoneId: number, subnetId: number) {
  return (await projectHttp.post<ApiResponse<NetworkZoneSubnet>>(`/architecture/network-zones/${zoneId}/subnets/${subnetId}/reactivate`)).data.data
}

export async function listExternalNetworkAddresses(query: {
  status?: EnvironmentRecordStatus | ''
  keyword?: string
} = {}) {
  return (await projectHttp.get<ApiResponse<ExternalNetworkAddress[]>>('/architecture/external-network-addresses', {
    params: compact(query)
  })).data.data
}

export async function createExternalNetworkAddress(payload: {
  addressType: 'IP' | 'CIDR' | 'DOMAIN'
  addressValue: string
  displayName: string
  purpose?: string | null
  remark?: string | null
}) {
  return (await projectHttp.post<ApiResponse<ExternalNetworkAddress>>('/architecture/external-network-addresses', payload)).data.data
}

export async function updateExternalNetworkAddress(id: number, payload: {
  addressType: 'IP' | 'CIDR' | 'DOMAIN'
  addressValue: string
  displayName: string
  purpose?: string | null
  remark?: string | null
  rowVersion: number
}) {
  return (await projectHttp.put<ApiResponse<ExternalNetworkAddress>>(`/architecture/external-network-addresses/${id}`, payload)).data.data
}

export async function deactivateExternalNetworkAddress(id: number) {
  return (await projectHttp.post<ApiResponse<ExternalNetworkAddress>>(`/architecture/external-network-addresses/${id}/deactivate`)).data.data
}

export async function reactivateExternalNetworkAddress(id: number) {
  return (await projectHttp.post<ApiResponse<ExternalNetworkAddress>>(`/architecture/external-network-addresses/${id}/reactivate`)).data.data
}

export async function listNetworkEndpointInstances(query: {
  physicalSubsystemId?: number | null
  environmentId?: number | null
  deploymentUnitId?: number | null
}) {
  return (await projectHttp.get<ApiResponse<ManagedEndpointInstance[]>>('/architecture/network-access/options/instances', {
    params: compact(query)
  })).data.data
}

export async function listNetworkAccessApplications(query: {
  status?: NetworkAccessApplicationStatus | ''
  limit?: number
  offset?: number
} = {}) {
  return (await projectHttp.get<ApiResponse<NetworkAccessApplication[]>>('/architecture/network-access-applications', {
    params: compact(query)
  })).data.data
}

export async function createNetworkAccessApplication(payload: NetworkAccessPayload) {
  return (await projectHttp.post<ApiResponse<NetworkAccessApplication>>('/architecture/network-access-applications', payload)).data.data
}

export async function evaluateNetworkAccessDecision(payload: NetworkAccessDecisionPayload) {
  return (await projectHttp.post<ApiResponse<NetworkAccessDecisionResult>>('/architecture/network-access/decision', payload)).data.data
}

export async function listNetworkAccessExemptionRules(query: {
  status?: NetworkAccessExemptionRuleStatus | ''
} = {}) {
  return (await projectHttp.get<ApiResponse<NetworkAccessExemptionRule[]>>('/architecture/network-access-exemption-rules', {
    params: compact(query)
  })).data.data
}

export async function createNetworkAccessExemptionRule(payload: NetworkAccessExemptionRulePayload) {
  return (await projectHttp.post<ApiResponse<NetworkAccessExemptionRule>>('/architecture/network-access-exemption-rules', payload)).data.data
}

export async function updateNetworkAccessExemptionRule(id: number, payload: NetworkAccessExemptionRulePayload) {
  return (await projectHttp.put<ApiResponse<NetworkAccessExemptionRule>>(`/architecture/network-access-exemption-rules/${id}`, payload)).data.data
}

export async function enableNetworkAccessExemptionRule(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<NetworkAccessExemptionRule>>(
    `/architecture/network-access-exemption-rules/${id}/enable`,
    { rowVersion }
  )).data.data
}

export async function disableNetworkAccessExemptionRule(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<NetworkAccessExemptionRule>>(
    `/architecture/network-access-exemption-rules/${id}/disable`,
    { rowVersion }
  )).data.data
}

export async function submitNetworkAccessApplication(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<NetworkAccessApplication>>(
    `/architecture/network-access-applications/${id}/submit`,
    { rowVersion }
  )).data.data
}

export async function approveNetworkAccessApplication(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<NetworkAccessApplication>>(
    `/architecture/network-access-applications/${id}/approve`,
    { rowVersion }
  )).data.data
}

export async function rejectNetworkAccessApplication(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<NetworkAccessApplication>>(
    `/architecture/network-access-applications/${id}/reject`,
    { rowVersion }
  )).data.data
}

export async function cancelNetworkAccessApplication(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<NetworkAccessApplication>>(
    `/architecture/network-access-applications/${id}/cancel`,
    { rowVersion }
  )).data.data
}

export async function listNetworkAccessRelations(query: {
  status?: NetworkAccessRelationStatus | ''
  limit?: number
  offset?: number
} = {}) {
  return (await projectHttp.get<ApiResponse<NetworkAccessRelation[]>>('/architecture/network-access-relations', {
    params: compact(query)
  })).data.data
}

export async function closeNetworkAccessRelation(id: number, payload: { closeReason: string; rowVersion: number }) {
  return (await projectHttp.post<ApiResponse<NetworkAccessRelation>>(`/architecture/network-access-relations/${id}/close`, payload)).data.data
}

export async function listResourceRequests(query: {
  status?: ResourceRequestStatus | ''
  environmentId?: number | null
  physicalSubsystemId?: number | null
  limit?: number
  offset?: number
}) {
  return (await projectHttp.get<ApiResponse<ResourceRequestSummary[]>>('/architecture/resource-requests', {
    params: compact(query)
  })).data.data
}

export async function getResourceRequest(id: number) {
  return (await projectHttp.get<ApiResponse<ResourceRequestDetail>>(`/architecture/resource-requests/${id}`)).data.data
}

export async function createResourceRequest(payload: ResourceRequestPayload) {
  return (await projectHttp.post<ApiResponse<ResourceRequestDetail>>('/architecture/resource-requests', payload)).data.data
}

export async function updateResourceRequest(id: number, payload: ResourceRequestPayload) {
  return (await projectHttp.put<ApiResponse<ResourceRequestDetail>>(`/architecture/resource-requests/${id}`, payload)).data.data
}

export async function submitResourceRequest(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<ResourceRequestDetail>>(`/architecture/resource-requests/${id}/submit`, { rowVersion })).data.data
}

export async function cancelResourceRequest(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<ResourceRequestDetail>>(`/architecture/resource-requests/${id}/cancel`, { rowVersion })).data.data
}

// ---------- 部署单元初始化导入 ----------

export async function uploadDeploymentUnitImport(file: File) {
  const data = new FormData()
  data.append('file', file)
  return (await projectHttp.post<ApiResponse<DeploymentUnitImportBatchDetail>>('/architecture/deployment-unit-imports', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })).data.data
}

export async function listDeploymentUnitImports(query: { page?: number; size?: number }) {
  return (await projectHttp.get<ApiResponse<PageResult<DeploymentUnitImportBatch>>>('/architecture/deployment-unit-imports', { params: compact(query) })).data.data
}

export async function getDeploymentUnitImport(id: number) {
  return (await projectHttp.get<ApiResponse<DeploymentUnitImportBatchDetail>>(`/architecture/deployment-unit-imports/${id}`)).data.data
}

export async function confirmDeploymentUnitImport(id: number) {
  return (await projectHttp.post<ApiResponse<DeploymentUnitImportBatchDetail>>(`/architecture/deployment-unit-imports/${id}/confirm`)).data.data
}

export async function downloadDeploymentUnitImportErrorReport(id: number) {
  const response = await projectHttp.get<Blob>(`/architecture/deployment-unit-imports/${id}/error-report`, { responseType: 'blob' })
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

// ---------- 环境部署实例与资源下发 ----------

export async function previewAutomatedProvision(requestId: number) {
  return (await projectHttp.get<ApiResponse<ProvisionPreviewResult>>(`/architecture/resource-requests/${requestId}/preview-automated-provision`)).data.data
}

export async function fulfillResourceRequest(requestId: number, payload: FulfillmentPayload) {
  return (await projectHttp.post<ApiResponse<EnvironmentInstance[]>>(`/architecture/resource-requests/${requestId}/fulfill`, payload)).data.data
}

export async function listEnvironmentInstances(query: {
  environmentId?: number
  physicalSubsystemId?: number
  deploymentUnitId?: number
  status?: InstanceStatus
  keyword?: string
  limit?: number
  offset?: number
}) {
  return (await projectHttp.get<ApiResponse<EnvironmentInstance[]>>('/architecture/instances', { params: compact(query) })).data.data
}

export async function getEnvironmentInstance(id: number) {
  return (await projectHttp.get<ApiResponse<EnvironmentInstance>>(`/architecture/instances/${id}`)).data.data
}

export async function offlineEnvironmentInstance(id: number, payload: OfflineInstancePayload) {
  return (await projectHttp.post<ApiResponse<EnvironmentInstance>>(`/architecture/instances/${id}/offline`, payload)).data.data
}

export async function listInstanceDisasterRecoveries(instanceId: number) {
  return (await projectHttp.get<ApiResponse<InstanceDisasterRecovery[]>>(`/architecture/instances/${instanceId}/disaster-recoveries`)).data.data
}

export async function listAllDisasterRecoveries(query?: { deploymentUnitId?: number; instanceId?: number }) {
  return (await projectHttp.get<ApiResponse<InstanceDisasterRecovery[]>>('/architecture/instance-disaster-recoveries', { params: compact(query || {}) })).data.data
}

export async function createInstanceDisasterRecovery(payload: DisasterRecoveryPayload) {
  return (await projectHttp.post<ApiResponse<InstanceDisasterRecovery>>('/architecture/instance-disaster-recoveries', payload)).data.data
}

export async function deleteInstanceDisasterRecovery(id: number) {
  return (await projectHttp.delete<ApiResponse<void>>(`/architecture/instance-disaster-recoveries/${id}`)).data
}

export async function listAvailableStandbyInstances(deploymentUnitId: number, excludeInstanceId?: number) {
  return (await projectHttp.get<ApiResponse<EnvironmentInstance[]>>('/architecture/instances/options/available-standbys', {
    params: compact({ deploymentUnitId, excludeInstanceId })
  })).data.data
}

export async function getSubsystemParticipation(id: number, projectRef: string) {
  return (await http.get<ApiResponse<SubsystemParticipation>>(`/architecture/physical-subsystems/${id}/participants`, { params: { projectRef } })).data.data
}
export async function getSubsystemParticipantCandidates(id: number, projectRef: string) {
  return (await http.get<ApiResponse<SubsystemParticipantCandidate[]>>(`/architecture/physical-subsystems/${id}/participants/candidates`, { params: { projectRef } })).data.data
}
export async function replaceSubsystemParticipation(id: number, projectRef: string, payload: SubsystemParticipationPayload) {
  return (await http.put<ApiResponse<SubsystemParticipation>>(`/architecture/physical-subsystems/${id}/participants`, payload, { params: { projectRef } })).data.data
}
