export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export type PublishedSubsystemStatus = 'ACTIVE' | 'OFFLINE' | 'VOIDED'
export type SubsystemTargetKind = 'LOGICAL' | 'PHYSICAL'
export type SubsystemActionType = 'CREATE' | 'UPDATE' | 'OFFLINE' | 'REACTIVATE' | 'VOID' | 'REPLACE'
export type SubsystemApplicationStatus = 'DRAFT' | 'IN_REVIEW' | 'RETURNED' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface PhysicalSubsystem {
  id: number
  code: string
  shortName: string
  name: string
  logicalSubsystemName: string | null
  businessComponentCode: string | null
  businessGroupName: string | null
  deploymentPlatform: string | null
  disasterRecoveryMode: string | null
  responsibleTeamOrgId: number
  responsibleTeamDisplayName: string
  responsibleTeamValid: boolean
  runtimeCode: string | null
  systemLevelCode: string | null
  developmentFrameworkCode: string | null
  ownerUserId: number | null
  ownerDisplayName: string | null
  description: string | null
  remark: string | null
  createdBy: number
  createdByDisplayName: string | null
  updatedBy: number
  createdAt: string
  updatedAt: string
  englishName: string | null
  status: PublishedSubsystemStatus
  rowVersion: number
}

export interface PhysicalDraftInput {
  lineNo: number
  code: string
  shortName: string
  name: string
  logicalSubsystemName: string | null
  businessComponentCode: string | null
  englishName: string | null
  businessGroupName: string | null
  deploymentPlatform: string | null
  disasterRecoveryMode: string | null
  responsibleTeamOrgId: number | null
  responsibleTeamNameSnapshot: string
  runtimeCode: string | null
  systemLevelCode: string | null
  developmentFrameworkCode: string | null
  ownerUserId: number | null
  description: string | null
  remark: string | null
  sourceRowVersion: number | null
}

export interface PhysicalDraft extends Omit<PhysicalDraftInput, 'responsibleTeamOrgId'> {
  sourcePhysicalSubsystemId: number | null
  responsibleTeamOrgId: number
  draftRevision: number
  submittedSnapshotJson: string | null
  createdAt: string
  updatedAt: string
}

export interface SubsystemChangeApplicationSummary {
  id: number
  targetKind: SubsystemTargetKind
  actionType: SubsystemActionType
  targetId: number | null
  applicantId: number
  reason: string
  status: SubsystemApplicationStatus
  currentBusinessRound: number
  currentWorkflowDefinitionId: number | null
  currentWorkflowVersionId: number | null
  currentWorkflowInstanceId: number | null
  currentPayloadDigest: string | null
  cancellationRequested: boolean
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface SubsystemChangeHistory {
  id: number
  eventType: string
  fromStatus: SubsystemApplicationStatus | null
  toStatus: SubsystemApplicationStatus | null
  businessRound: number
  summary: string
  snapshotJson: string | null
  diffJson: string | null
  operatorId: number
  occurredAt: string
}

export interface SubsystemChangeApplicationDetail {
  application: SubsystemChangeApplicationSummary
  physicalDrafts: PhysicalDraft[]
  history: SubsystemChangeHistory[]
}

export interface CreateSubsystemChangeApplicationPayload {
  targetKind: 'PHYSICAL'
  actionType: SubsystemActionType
  targetId: number | null
  reason: string
  physicalDraft: PhysicalDraftInput
}

export interface UpdateSubsystemChangeApplicationPayload {
  rowVersion: number
  reason: string
  physicalDrafts: PhysicalDraftInput[]
}

export interface SubsystemSuggestion {
  field: string
  value: string
  source: string
  explanation: string
}

export interface OrganizationOption { id: number; name: string; parentId: number | null; pathLabel: string }
export interface UserOption { id: number; displayName: string; username: string; phone: string | null }
export interface ParameterOption { code: string; label: string }

export type ArchitectureResource = 'physical-subsystem'
export type DetailItem = { label: string; value: string; wide?: boolean; tone?: 'warning' | 'danger' }

// ---------- 架构规范 ----------
export type StandardDocumentStatus = 'DRAFT' | 'PUBLISHED' | 'OFFLINE'

export interface StandardDocumentSummary {
  id: number
  title: string
  categoryCode: string
  status: StandardDocumentStatus
  currentVersion: number
  publishedAt: string | null
  publishedByName: string | null
  updatedAt: string
}

export interface StandardDocumentDetail {
  id: number
  title: string
  categoryCode: string
  summary: string | null
  content: string | null
  status: StandardDocumentStatus
  currentVersion: number
  publishedAt: string | null
  publishedBy: number | null
  publishedByName: string | null
  rowVersion: number
  createdByName: string | null
  createdAt: string
  updatedAt: string
}

export interface StandardVersion {
  id: number
  documentId: number
  versionNo: number
  title: string
  categoryCode: string
  summary: string | null
  content: string | null
  publishedAt: string
  publishedBy: number
  publishedByName: string | null
}

export interface StandardCategory { code: string; label: string }

export interface AttachmentItemView {
  id: number
  fileName: string
  contentType: string | null
  size: number
  uploaderName: string | null
  createdAt: string
}

// ---------- 架构决策 ----------
export type DecisionMatterStatus = 'SUBMITTED' | 'RETURNED_FOR_INFO' | 'IN_REVIEW' | 'PUBLISHED'
export type FirstHandlingOutcome = 'ACCEPTED' | 'REQUESTED_INFO' | 'REVIEW_MODE_SET'
export type ReviewMethod = 'ASYNC' | 'MEETING'
export type MaterialKind = 'SOLUTION' | 'IMPACT' | 'DISPUTE' | 'OTHER'
export type SupersessionKind = 'SUPERSEDE' | 'PARTIALLY_REVISE'
export type ConclusionEffectiveStatus = 'EFFECTIVE' | 'SUPERSEDED' | 'PARTIALLY_SUPERSEDED'

export interface DecisionMatterSummary {
  id: number
  matterNo: string
  title: string
  typeCode: string | null
  status: DecisionMatterStatus
  receivedAt: string
  firstHandlingDeadline: string
  firstHandlingOverdue: boolean
  firstHandlingOutcome: FirstHandlingOutcome | null
  reviewMode: ReviewMethod | null
  proposerName: string
  updatedAt: string
}

export interface DecisionMatterDetail {
  id: number
  matterNo: string
  title: string
  problem: string
  typeCode: string | null
  status: DecisionMatterStatus
  receivedAt: string
  firstHandlingDeadline: string
  firstHandlingOverdue: boolean
  firstHandlingOutcome: FirstHandlingOutcome | null
  firstHandlingComment: string | null
  firstHandledAt: string | null
  firstHandlerId: number | null
  firstHandlerName: string | null
  reviewMode: ReviewMethod | null
  proposerId: number
  proposerName: string
  submitterId: number
  submitterName: string
  publicationPreparedAt: string | null
  publicationPreparedBy: number | null
  currentBusinessRound: number
  currentWorkflowInstanceId: number | null
  rowVersion: number
  createdByName: string | null
  createdAt: string
  updatedAt: string
}

export interface DecisionMaterial {
  id: number
  matterId: number
  kind: MaterialKind
  content: string
  createdByName: string | null
  createdAt: string
}

export interface DecisionReview {
  id: number
  matterId: number
  reviewNo: number
  method: ReviewMethod
  reviewedAt: string
  processMaterialSummary: string | null
  keyOpinion: string | null
  conclusionContent: string | null
  conclusionRationale: string | null
  createdByName: string | null
  createdAt: string
  updatedAt: string
}

export interface DecisionActionItem {
  id: number
  reviewId: number
  content: string
  ownerUserId: number | null
  ownerName: string | null
  status: 'OPEN' | 'DONE'
  createdAt: string
}

export interface ChainLink {
  id: number
  kind: SupersessionKind
  conclusionId: number
  conclusionMatterNo: string | null
  supersededConclusionId: number
  supersededMatterNo: string | null
  createdAt: string
}

export interface ConclusionView {
  conclusionId: number
  matterId: number
  matterNo: string | null
  matterTitle: string | null
  typeCode: string | null
  content: string
  rationale: string | null
  publishedAt: string
  publishedBy: number
  publishedByName: string | null
  effectiveStatus: ConclusionEffectiveStatus
  supersedes: ChainLink[]
  supersededBy: ChainLink[]
}

export interface PublicationIntentView {
  matterId: number
  reviewId: number
  targets: { conclusionId: number; kind: SupersessionKind }[]
  payloadDigest: string
  preparedByName: string | null
  preparedAt: string
}

export interface DecisionUserReference { id: number; displayName: string; username: string }
// ---------- 部署单元 ----------

export type DeploymentUnitKind = 'APPLICATION' | 'DATABASE' | 'MQ'
export type DeploymentUnitStatus = 'ACTIVE' | 'INACTIVE' | 'VOIDED'
export type DeploymentUnitImportBatchStatus = 'PREVIEW' | 'SUCCESS' | 'PARTIAL' | 'FAILED'
export type DeploymentUnitImportItemStatus = 'VALID' | 'INVALID' | 'SUCCESS' | 'FAILED' | 'SKIPPED'

export interface DeploymentUnit {
  id: number
  code: string | null
  physicalSubsystemId: number
  physicalSubsystemCode: string | null
  physicalSubsystemName: string | null
  physicalSubsystemStatus: string | null
  shortName: string
  name: string
  relatedDeploymentUnitName: string | null
  deploymentUnitType: string
  kind: DeploymentUnitKind
  status: DeploymentUnitStatus
  defaultNetworkZoneId: number | null
  defaultNetworkZoneName: string | null
  currentVersion: number
  description: string | null
  remark: string | null
  createdBy: number
  createdByDisplayName: string | null
  updatedBy: number
  updatedByDisplayName: string | null
  createdAt: string
  updatedAt: string
  rowVersion: number
}

export interface DeploymentUnitVersion {
  versionNo: number
  shortName: string
  name: string
  relatedDeploymentUnitName: string | null
  deploymentUnitType: string
  kind: DeploymentUnitKind
  defaultNetworkZoneId: number | null
  defaultNetworkZoneName: string | null
  description: string | null
  remark: string | null
  publishedBy: number
  publishedByDisplayName: string
  publishedAt: string
}

export interface DeploymentUnitPayload {
  physicalSubsystemId: number | null
  shortName: string
  name: string
  relatedDeploymentUnitName: string | null
  deploymentUnitType: string | null
  kind: DeploymentUnitKind | ''
  defaultNetworkZoneId: number | null
  description: string | null
  remark: string | null
  rowVersion?: number | null
}

export interface DeploymentUnitImportBatch {
  id: number
  fileName: string
  fileSize: number
  totalRows: number
  validRows: number
  successRows: number
  failedRows: number
  skippedRows: number
  status: DeploymentUnitImportBatchStatus
  errorMessage: string | null
  createdBy: number
  createdByDisplayName: string
  createdAt: string
  completedAt: string | null
}

export interface DeploymentUnitImportRow {
  physicalCode: string | null
  shortName: string | null
  name: string | null
  kindLabel: string | null
  description: string | null
  remark: string | null
}

export interface DeploymentUnitImportItem {
  itemId: number
  lineNo: number
  row: DeploymentUnitImportRow
  rowStatus: DeploymentUnitImportItemStatus
  errorMessage: string | null
  note: string | null
  unitId: number | null
}

export interface DeploymentUnitImportBatchDetail {
  batch: DeploymentUnitImportBatch
  items: DeploymentUnitImportItem[]
}

export interface PhysicalSubsystemOption {
  id: number
  code: string
  shortName: string | null
  name: string
  businessGroupName: string | null
  deploymentPlatform: string | null
  disasterRecoveryMode: string | null
  systemLevelCode: string | null
  status: string
}

// ---------- 具体环境与资源申请 ----------

export type EnvironmentRecordStatus = 'ACTIVE' | 'INACTIVE'
export type ResourceRequestType = 'INITIAL' | 'EXPANSION' | 'SHRINK' | 'ADJUSTMENT'
export type ResourceRequestStatus = 'DRAFT' | 'IN_REVIEW' | 'RETURNED' | 'APPROVED' | 'FULFILLED' | 'DIFF_FULFILLED' | 'REJECTED' | 'CANCELLED'
export type NetworkAddressType = 'IP' | 'CIDR' | 'DOMAIN'
export type NetworkEndpointKind = 'MANAGED' | 'EXTERNAL'
export type NetworkAccessProtocol = 'TCP' | 'UDP' | 'HTTP' | 'HTTPS' | 'OTHER'
export type NetworkAccessValidityType = 'LIMITED' | 'LONG_TERM'
export type NetworkAccessActionType = 'OPEN' | 'MODIFY' | 'RENEW' | 'CLOSE'
export type NetworkAccessApplicationStatus = 'DRAFT' | 'RETURNED' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type NetworkAccessRelationStatus = 'ACTIVE' | 'CLOSED'
export type NetworkAccessDecision = 'NEEDS_APPLICATION' | 'NOT_REQUIRED'
export type NetworkAccessDecisionBasis = 'SUBNET_INTERNAL' | 'RELATION_COVERED' | 'RULE_EXEMPT' | 'STRICT_REQUIRED'
export type NetworkAccessExemptionRuleStatus = 'ACTIVE' | 'DISABLED'
export type NetworkAccessRelationCloseType = 'SUPERSEDED' | 'CLOSED_BY_APPLICATION' | 'LEGACY_DIRECT'

export interface NetworkZone {
  id: number
  tenantId: number
  parentId: number | null
  parentName: string | null
  code: string
  name: string
  restrictionLevel: number
  status: EnvironmentRecordStatus
  description: string | null
  remark: string | null
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface NetworkZoneOption {
  id: number
  code: string
  name: string
  parentId: number | null
  parentName: string | null
  restrictionLevel: number
  leaf: boolean
}

export interface NetworkZoneSubnet {
  id: number
  tenantId: number
  networkZoneId: number
  networkZoneCode: string
  networkZoneName: string
  cidrBlock: string
  gatewayIp: string | null
  purpose: string | null
  status: EnvironmentRecordStatus
  remark: string | null
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface ExternalNetworkAddress {
  id: number
  tenantId: number
  addressType: NetworkAddressType
  addressValue: string
  displayName: string
  purpose: string | null
  status: EnvironmentRecordStatus
  remark: string | null
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface ManagedEndpointInstance {
  id: number
  instanceNo: string
  physicalSubsystemId: number
  physicalSubsystemCode: string
  physicalSubsystemName: string
  environmentId: number
  environmentCode: string
  environmentName: string
  deploymentUnitId: number
  deploymentUnitCode: string
  deploymentUnitName: string
  machineName: string
  ipAddress: string
  networkZoneId: number | null
  networkZoneName: string | null
}

export interface NetworkEndpointPayload {
  kind: NetworkEndpointKind
  physicalSubsystemId?: number | null
  environmentId?: number | null
  deploymentUnitId?: number | null
  externalAddressId?: number | null
  instanceIds?: number[]
}

export interface NetworkAccessPayload {
  source?: NetworkEndpointPayload | null
  target?: NetworkEndpointPayload | null
  protocol: NetworkAccessProtocol
  ports: string
  purpose: string
  processDescription?: string | null
  validFrom?: string | null
  validUntil?: string | null
  validityType?: NetworkAccessValidityType | null
  actionType?: NetworkAccessActionType | null
  targetRelationId?: number | null
}

export interface NetworkAccessDecisionPayload {
  source: NetworkEndpointPayload
  target: NetworkEndpointPayload
  protocol: NetworkAccessProtocol
  ports: string
  validFrom: string | null
  validUntil: string | null
  validityType: NetworkAccessValidityType
}

export interface NetworkAccessDecisionResult {
  decision: NetworkAccessDecision
  needsApplication: boolean
  basis: NetworkAccessDecisionBasis
  reasonCodes: string[]
  coveringRelationNos: string[]
  coveringRuleCodes: string[]
}

export interface NetworkAccessApplication {
  id: number
  tenantId: number
  applicationNo: string
  applicantId: number
  actionType: NetworkAccessActionType
  targetRelationId: number | null
  sourceKind: NetworkEndpointKind
  sourcePhysicalSubsystemId: number | null
  sourceEnvironmentId: number | null
  sourceDeploymentUnitId: number | null
  sourceExternalAddressId: number | null
  sourceSnapshotJson: string | null
  targetKind: NetworkEndpointKind
  targetPhysicalSubsystemId: number | null
  targetEnvironmentId: number | null
  targetDeploymentUnitId: number | null
  targetExternalAddressId: number | null
  targetSnapshotJson: string | null
  protocol: NetworkAccessProtocol
  ports: string
  purpose: string
  processDescription: string | null
  validFrom: string | null
  validUntil: string | null
  validityType: NetworkAccessValidityType
  status: NetworkAccessApplicationStatus
  currentBusinessRound: number
  currentWorkflowDefinitionId: number | null
  currentWorkflowVersionId: number | null
  currentWorkflowInstanceId: number | null
  currentPayloadDigest: string | null
  cancellationRequested: boolean
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface NetworkAccessRelation {
  id: number
  tenantId: number
  relationNo: string
  applicationId: number
  replacesRelationId: number | null
  replacedByRelationId: number | null
  closedApplicationId: number | null
  sourceKind: NetworkEndpointKind
  sourceSnapshotJson: string | null
  targetKind: NetworkEndpointKind
  targetSnapshotJson: string | null
  protocol: NetworkAccessProtocol
  ports: string
  purpose: string
  processDescription: string | null
  validFrom: string | null
  validUntil: string | null
  validityType: NetworkAccessValidityType
  status: NetworkAccessRelationStatus
  closeReason: string | null
  closeType: NetworkAccessRelationCloseType | null
  closedBy: number | null
  closedAt: string | null
  hasOfflineEndpointRisk: boolean
  offlineEndpointCount: number
  offlineEndpointSummaries: string[]
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface NetworkAccessExemptionRule {
  id: number
  tenantId: number
  ruleCode: string
  ruleName: string
  sourceNetworkZoneId: number
  sourceNetworkZoneName: string
  targetNetworkZoneId: number
  targetNetworkZoneName: string
  protocol: NetworkAccessProtocol
  ports: string
  validFrom: string | null
  validUntil: string | null
  validityType: NetworkAccessValidityType
  status: NetworkAccessExemptionRuleStatus
  remark: string | null
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface NetworkAccessExemptionRulePayload {
  ruleCode: string
  ruleName: string
  sourceNetworkZoneId: number | null
  targetNetworkZoneId: number | null
  protocol: NetworkAccessProtocol
  ports: string
  validFrom: string | null
  validUntil: string | null
  validityType: NetworkAccessValidityType
  remark?: string | null
  rowVersion?: number | null
}

export interface EnvironmentType {
  code: string
  name: string
}

export interface Environment {
  id: number
  code: string
  name: string
  typeCode: string
  typeName: string
  status: EnvironmentRecordStatus
  description: string | null
  remark: string | null
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface EnvironmentResourceSummary {
  environmentId: number
  requestCount: number
  approvedRequestCount: number
  pendingRequestCount: number
  requestedCpuCores: number
  requestedMemoryGb: number
  requestedStorageGb: number
  requestedNodeCount: number
  actualCpuCores: number
  actualMemoryGb: number
  actualStorageGb: number
  actualNodeCount: number
}

export interface EnvironmentDetail {
  environment: Environment
  resourceSummary: EnvironmentResourceSummary
}

export interface EnvironmentPayload {
  code: string
  name: string
  typeCode: string | null
  description: string | null
  remark: string | null
  rowVersion?: number | null
}

export interface ResourceRequestItemPayload {
  deploymentUnitId: number | null
  databaseStorageGb: number
  fileStorageGb: number
  networkZoneId: number | null
  networkZone: string | null
  serverType: string | null
  cpuCores: number
  memoryGb: number
  appWebGroupCount: number
  plannedNodeCount: number
  sidecarCpuCores: number
  sidecarMemoryGb: number
  hasSidecar: boolean
  databaseName: string | null
  databaseVersion: string | null
  jdkVersion: string | null
  middleware: string | null
  operatingSystem: string | null
  extraCbsGb: number
  localDiskGb: number
  needsNft: boolean
  needsFserver: boolean
  needsJobexecutor: boolean
  remark: string | null
}

export interface ResourceRequestPayload {
  physicalSubsystemId: number | null
  environmentId: number | null
  contactUserId: number | null
  requestType: ResourceRequestType | ''
  reason: string | null
  items: ResourceRequestItemPayload[]
  rowVersion?: number | null
}

export interface DeploymentUnitOption {
  id: number
  code: string
  name: string
  kind: DeploymentUnitKind
  physicalSubsystemId: number
  relatedDeploymentUnitName: string | null
  deploymentUnitType: string | null
  description: string | null
  defaultNetworkZoneId: number | null
  defaultNetworkZoneName: string | null
}

export interface ResourceRequestSummary {
  id: number
  requestNo: string
  physicalSubsystemId: number
  physicalSubsystemCode: string
  physicalSubsystemShortName: string | null
  physicalSubsystemName: string
  physicalSubsystemBusinessGroupName: string | null
  physicalSubsystemSystemLevelCode: string | null
  physicalSubsystemDeploymentPlatform: string | null
  physicalSubsystemDisasterRecoveryMode: string | null
  environmentId: number
  environmentCode: string
  environmentName: string
  environmentTypeName: string
  applicantId: number
  contactUserId: number
  requestType: ResourceRequestType
  reason: string | null
  status: ResourceRequestStatus
  currentBusinessRound: number
  cancellationRequested: boolean
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface ResourceRequestItem {
  id: number
  itemSeq: number
  deploymentUnitId: number
  deploymentUnitCode: string
  deploymentUnitName: string
  deploymentUnitKind: DeploymentUnitKind
  relatedDeploymentUnitName: string | null
  deploymentUnitDescription: string | null
  deploymentUnitType: string | null
  databaseStorageGb: number
  fileStorageGb: number
  networkZoneId: number | null
  networkZoneName: string | null
  networkZone: string | null
  serverType: string | null
  cpuCores: number
  memoryGb: number
  appWebGroupCount: number
  plannedNodeCount: number
  totalCpuCores: number
  totalMemoryGb: number
  sidecarCpuCores: number
  sidecarMemoryGb: number
  sidecarMemoryRatio: string | null
  hasSidecar: boolean
  databaseName: string | null
  databaseVersion: string | null
  jdkVersion: string | null
  middleware: string | null
  operatingSystem: string | null
  extraCbsGb: number
  localDiskGb: number
  needsNft: boolean
  needsFserver: boolean
  needsJobexecutor: boolean
  remark: string | null
}

export interface ResourceRequestHistory {
  id: number
  eventType: string
  fromStatus: ResourceRequestStatus | null
  toStatus: ResourceRequestStatus | null
  businessRound: number
  summary: string
  snapshotJson: string | null
  diffJson: string | null
  operatorId: number
  occurredAt: string
}

export interface ResourceRequestDetail {
  request: ResourceRequestSummary
  items: ResourceRequestItem[]
  history: ResourceRequestHistory[]
}

export type InstanceStatus = 'ACTIVE' | 'OFFLINE'
export type FulfillmentMode = 'MANUAL' | 'AUTOMATED'
export type DisasterRecoveryMode = 'PRIMARY_STANDBY' | 'ACTIVE_ACTIVE' | 'COLD_STANDBY'

export interface EnvironmentInstance {
  id: number
  instanceNo: string
  environmentId: number
  environmentCode: string
  environmentName: string
  environmentTypeName?: string
  deploymentUnitId: number
  deploymentUnitCode: string
  deploymentUnitName: string
  deploymentUnitKind: string
  deploymentUnitVersionId?: number | null
  deploymentUnitVersionNo: number
  latestDeploymentUnitVersionNo: number
  hasVersionDifference: boolean
  physicalSubsystemId: number
  physicalSubsystemCode: string
  physicalSubsystemName: string
  sourceRequestId: number
  sourceRequestNo: string
  sourceItemId?: number | null
  machineName: string
  ipAddress: string
  serverType?: string
  deploymentPlatform?: string
  networkZoneId?: number | null
  networkZoneName?: string | null
  networkZone?: string
  status: InstanceStatus
  cpuCores: number
  memoryGb: number
  databaseStorageGb: number
  fileStorageGb: number
  extraCbsGb: number
  localDiskGb: number
  databaseName?: string
  databaseVersion?: string
  jdkVersion?: string
  middleware?: string
  operatingSystem?: string
  needsNft: boolean
  needsFserver: boolean
  needsJobexecutor: boolean
  fulfillmentMode: FulfillmentMode
  differenceReason?: string
  remark?: string
  offlinedAt?: string
  offlinedBy?: number
  offlineReason?: string
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface InstanceDisasterRecovery {
  id: number
  deploymentUnitId: number
  deploymentUnitCode: string
  deploymentUnitName: string
  primaryInstanceId: number
  primaryMachineName: string
  primaryIpAddress: string
  primaryEnvironmentCode: string
  primaryEnvironmentName: string
  standbyInstanceId: number
  standbyMachineName: string
  standbyIpAddress: string
  standbyEnvironmentCode: string
  standbyEnvironmentName: string
  drMode: DisasterRecoveryMode
  description?: string
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface ProvisionedInstance {
  sourceItemId: number
  itemSeq: number
  deploymentUnitId: number
  deploymentUnitCode: string
  deploymentUnitName: string
  machineName: string
  ipAddress: string
  serverType?: string
  deploymentPlatform?: string
  networkZoneId?: number | null
  networkZoneName?: string | null
  networkZone?: string
  cpuCores: number
  memoryGb: number
  databaseStorageGb: number
  fileStorageGb: number
  extraCbsGb: number
  localDiskGb: number
  databaseName?: string
  databaseVersion?: string
  jdkVersion?: string
  middleware?: string
  operatingSystem?: string
  needsNft: boolean
  needsFserver: boolean
  needsJobexecutor: boolean
  remark?: string
  mockExecutionLog?: string
}

export interface ProvisionPreviewResult {
  success: boolean
  executionId: string
  message: string
  instances: ProvisionedInstance[]
}

export interface FulfillInstanceItemPayload {
  sourceItemId?: number | null
  deploymentUnitId: number
  machineName: string
  ipAddress: string
  serverType?: string | null
  deploymentPlatform?: string | null
  networkZoneId?: number | null
  networkZoneName?: string | null
  networkZone?: string | null
  cpuCores: number
  memoryGb: number
  databaseStorageGb: number
  fileStorageGb: number
  extraCbsGb: number
  localDiskGb: number
  databaseName?: string | null
  databaseVersion?: string | null
  jdkVersion?: string | null
  middleware?: string | null
  operatingSystem?: string | null
  needsNft?: boolean
  needsFserver?: boolean
  needsJobexecutor?: boolean
  fulfillmentMode?: FulfillmentMode
  remark?: string | null
}

export interface FulfillmentPayload {
  fulfillmentMode: FulfillmentMode
  differenceReason?: string
  instances: FulfillInstanceItemPayload[]
  rowVersion?: number
}

export interface OfflineInstancePayload {
  offlineReason: string
  rowVersion?: number
}

export interface DisasterRecoveryPayload {
  deploymentUnitId?: number
  primaryInstanceId: number
  standbyInstanceId: number
  drMode: DisasterRecoveryMode
  description?: string
}
