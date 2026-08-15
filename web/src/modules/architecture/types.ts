export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
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
  contactUserId: number | null
  contactDisplayName: string | null
  contactPhone: string | null
  description: string | null
  remark: string | null
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface LogicalSubsystemCommand {
  code: string
  shortName: string
  name: string
  businessOrgId: number | null
  deploymentPlatformCode: string | null
  systemTypeCode: string | null
  systemOwnershipCode: string | null
  contactUserId: number | null
  description: string | null
  remark: string | null
}

export interface PhysicalSubsystemCommand {
  code: string
  shortName: string
  name: string
  logicalSubsystemId: number | null
  businessGroupName: string | null
  responsibleTeamOrgId: number | null
  runtimeCode: string | null
  systemLevelCode: string | null
  developmentFrameworkCode: string | null
  ownerUserId: number | null
  contactUserId: number | null
  description: string | null
  remark: string | null
}

export interface OrganizationOption { id: number; name: string; parentId: number | null; pathLabel: string }
export interface UserOption { id: number; displayName: string; username: string; phone: string | null }
export interface ParameterOption { code: string; label: string }
export interface LogicalSubsystemOption { id: number; code: string; name: string }

export type ArchitectureResource = 'logical-subsystem' | 'physical-subsystem'
export type DetailItem = { label: string; value: string; wide?: boolean; tone?: 'warning' | 'danger' }
