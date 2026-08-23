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

export interface PhysicalSubsystemSummary {
  id: number
  code: string
  shortName: string
  name: string
  numberSlot: string | null
  englishName: string | null
  status: PublishedSubsystemStatus
  rowVersion: number
}

export interface LogicalSubsystem {
  id: number
  code: string
  shortName: string
  name: string
  businessOrgId: number
  deploymentPlatformCode: string | null
  systemTypeCode: string | null
  systemOwnershipCode: string | null
  contactUserId: number
  description: string | null
  remark: string | null
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
  numberSequence: number | null
  status: PublishedSubsystemStatus
  sortNo: number
  rowVersion: number
  physicalSubsystems: PhysicalSubsystemSummary[]
}

export interface PhysicalSubsystem {
  id: number
  code: string
  shortName: string
  name: string
  logicalSubsystemId: number
  logicalSubsystemCode: string
  logicalSubsystemName: string
  businessGroupName: string | null
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
  numberSlot: string | null
  englishName: string | null
  status: PublishedSubsystemStatus
  rowVersion: number
  logicalSubsystemNumberSequence: number | null
  logicalSubsystemStatus: PublishedSubsystemStatus | null
}

export interface LogicalDraftInput {
  shortName: string
  name: string
  businessOrgId: number | null
  deploymentPlatformCode: string | null
  systemTypeCode: string | null
  systemOwnershipCode: string | null
  contactUserId: number | null
  description: string | null
  remark: string | null
  sortNo: number
  sourceRowVersion: number | null
}

export interface PhysicalDraftInput {
  lineNo: number
  targetLogicalSubsystemId: number | null
  shortName: string
  name: string
  englishName: string | null
  businessGroupName: string | null
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

export interface LogicalDraft extends Omit<LogicalDraftInput, 'businessOrgId' | 'contactUserId'> {
  sourceLogicalSubsystemId: number | null
  businessOrgId: number
  contactUserId: number
  reservedNumberSequence: number | null
  draftRevision: number
  submittedSnapshotJson: string | null
  createdAt: string
  updatedAt: string
}

export interface PhysicalDraft extends Omit<PhysicalDraftInput, 'responsibleTeamOrgId'> {
  sourcePhysicalSubsystemId: number | null
  responsibleTeamOrgId: number
  reservedNumberSlot: string | null
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
  logicalDraft: LogicalDraft | null
  physicalDrafts: PhysicalDraft[]
  history: SubsystemChangeHistory[]
}

export interface CreateSubsystemChangeApplicationPayload {
  targetKind: SubsystemTargetKind
  actionType: SubsystemActionType
  targetId: number | null
  reason: string
  logicalDraft?: LogicalDraftInput | null
  physicalDrafts?: PhysicalDraftInput[]
  physicalDraft?: PhysicalDraftInput | null
}

export interface UpdateSubsystemChangeApplicationPayload {
  rowVersion: number
  reason: string
  logicalDraft: LogicalDraftInput | null
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
export interface LogicalSubsystemOption { id: number; code: string; name: string }

export type ArchitectureResource = 'logical-subsystem' | 'physical-subsystem'
export type DetailItem = { label: string; value: string; wide?: boolean; tone?: 'warning' | 'danger' }
