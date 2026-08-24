import type {
  DeploymentUnitImportBatchStatus,
  DeploymentUnitImportItemStatus,
  DeploymentUnitKind,
  DeploymentUnitStatus,
  ParameterOption,
  PublishedSubsystemStatus,
  SubsystemActionType,
  SubsystemApplicationStatus,
  SubsystemTargetKind
} from './types'

export const applicationStatusLabels: Record<SubsystemApplicationStatus, string> = {
  DRAFT: '草稿',
  IN_REVIEW: '审批中',
  RETURNED: '已退回',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
  CANCELLED: '已取消'
}

export const actionTypeLabels: Record<SubsystemActionType, string> = {
  CREATE: '新增',
  UPDATE: '更新',
  OFFLINE: '下线',
  REACTIVATE: '重新启用',
  VOID: '作废',
  REPLACE: '更换归属'
}

export const targetKindLabels: Record<SubsystemTargetKind, string> = {
  LOGICAL: '逻辑子系统',
  PHYSICAL: '物理子系统'
}

export const publishedStatusLabels: Record<PublishedSubsystemStatus, string> = {
  ACTIVE: '启用',
  OFFLINE: '已下线',
  VOIDED: '已作废'
}

export function applicationStatusTone(status: SubsystemApplicationStatus) {
  if (status === 'APPROVED') return 'success' as const
  if (status === 'REJECTED') return 'danger' as const
  if (status === 'IN_REVIEW' || status === 'RETURNED') return 'warning' as const
  return 'info' as const
}

export function publishedStatusTone(status: PublishedSubsystemStatus) {
  if (status === 'ACTIVE') return 'success' as const
  if (status === 'VOIDED') return 'danger' as const
  return 'info' as const
}

export function canEditApplication(status: SubsystemApplicationStatus) {
  return status === 'DRAFT' || status === 'RETURNED'
}

export function canSubmitApplication(status: SubsystemApplicationStatus) {
  return status === 'DRAFT' || status === 'RETURNED'
}

export function canCancelApplication(status: SubsystemApplicationStatus) {
  return status === 'DRAFT' || status === 'RETURNED' || status === 'IN_REVIEW'
}

export function allowedPublishedActions(kind: SubsystemTargetKind, status: PublishedSubsystemStatus): SubsystemActionType[] {
  if (status === 'VOIDED') return []
  if (status === 'OFFLINE') return ['REACTIVATE', 'VOID']
  return kind === 'PHYSICAL' ? ['UPDATE', 'OFFLINE', 'VOID', 'REPLACE'] : ['UPDATE', 'OFFLINE', 'VOID']
}

export function formatLogicalNumber(sequence?: number | null) {
  return sequence && sequence > 0 ? `A${String(sequence).padStart(4, '0')}` : '待生成'
}

export function formatPhysicalNumber(logicalSequence?: number | null, slot?: string | null) {
  return logicalSequence && slot ? `W${String(logicalSequence).padStart(4, '0')}${slot}` : '待生成'
}

export function formatDateTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(date).replace(/\//g, '-')
}

export function optionLabel(options: ParameterOption[], code?: string | null) {
  if (!code) return '—'
  return findOption(options, code)?.label || code
}

export function canonicalOptionCode(options: ParameterOption[], code?: string | null) {
  if (!code) return null
  return findOption(options, code)?.code || code
}

function findOption(options: ParameterOption[], code: string) {
  const normalized = code.trim().toLowerCase()
  return options.find(item => item.code === code)
    || options.find(item => item.code.trim().toLowerCase() === normalized)
}

export function normalizeText(value?: string | null) {
  const normalized = value?.trim()
  return normalized || null
}

export function cancelled(error: unknown) {
  if (error === 'cancel' || error === 'close') return true
  const action = (error as { action?: string }).action
  return action === 'cancel' || action === 'close'
}

export function httpStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status
}

export function httpErrorCode(error: unknown) {
  return (error as { response?: { data?: { code?: string } } }).response?.data?.code
}

// ---------- 部署单元 ----------

export const deploymentUnitKindLabels: Record<DeploymentUnitKind, string> = {
  APPLICATION: '应用',
  DATABASE: '数据库',
  MQ: '消息队列'
}

export const deploymentUnitStatusLabels: Record<DeploymentUnitStatus, string> = {
  ACTIVE: '启用',
  INACTIVE: '已停用',
  VOIDED: '已作废'
}

export const importBatchStatusLabels: Record<DeploymentUnitImportBatchStatus, string> = {
  PREVIEW: '待确认',
  SUCCESS: '全部成功',
  PARTIAL: '部分失败',
  FAILED: '已回滚'
}

export const importItemStatusLabels: Record<DeploymentUnitImportItemStatus, string> = {
  VALID: '可写入',
  INVALID: '校验失败',
  SUCCESS: '已写入',
  FAILED: '写入失败',
  SKIPPED: '已跳过'
}

export function deploymentUnitStatusTone(status: DeploymentUnitStatus) {
  if (status === 'ACTIVE') return 'success' as const
  if (status === 'INACTIVE') return 'warning' as const
  return 'danger' as const
}

export function importBatchStatusTone(status: DeploymentUnitImportBatchStatus) {
  if (status === 'SUCCESS') return 'success' as const
  if (status === 'PARTIAL' || status === 'PREVIEW') return 'warning' as const
  return 'danger' as const
}

export function importItemStatusTone(status: DeploymentUnitImportItemStatus) {
  if (status === 'SUCCESS' || status === 'SKIPPED') return 'success' as const
  if (status === 'VALID') return 'info' as const
  return 'danger' as const
}
