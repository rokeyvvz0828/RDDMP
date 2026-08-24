import type {
  CertType,
  HandlingResultStatus,
  NetworkWorkOrderActionType,
  NetworkWorkOrderKind,
  NetworkWorkOrderStatus
} from './networkTypes'

export const networkKindLabels: Record<NetworkWorkOrderKind, string> = {
  CLB: 'CLB',
  DNS: 'DNS',
  CERT: '证书'
}

export const clbActionLabels: Partial<Record<NetworkWorkOrderActionType, string>> = {
  OPEN: '开通',
  ADJUST: '调整'
}

export const dnsActionLabels: Partial<Record<NetworkWorkOrderActionType, string>> = {
  ADD: '新增',
  CHANGE: '变更',
  REMOVE: '注销'
}

export const certActionLabels: Partial<Record<NetworkWorkOrderActionType, string>> = {
  APPLY: '申请',
  RENEW: '续期',
  REVOKE: '吊销'
}

export function networkActionLabel(kind: NetworkWorkOrderKind, action: NetworkWorkOrderActionType) {
  const labels = kind === 'CLB' ? clbActionLabels : kind === 'DNS' ? dnsActionLabels : certActionLabels
  return labels[action] || action
}

export function networkKindActionOptions(kind: NetworkWorkOrderKind): NetworkWorkOrderActionType[] {
  if (kind === 'CLB') return ['OPEN', 'ADJUST']
  if (kind === 'DNS') return ['ADD', 'CHANGE', 'REMOVE']
  return ['APPLY', 'RENEW', 'REVOKE']
}

export const networkStatusLabels: Record<NetworkWorkOrderStatus, string> = {
  DRAFT: '草稿',
  IN_REVIEW: '审批中',
  RETURNED: '已退回',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
  CANCELLED: '已取消'
}

export function networkStatusTone(status: NetworkWorkOrderStatus) {
  if (status === 'COMPLETED') return 'success' as const
  if (status === 'REJECTED' || status === 'CANCELLED') return 'danger' as const
  if (status === 'IN_REVIEW' || status === 'RETURNED') return 'warning' as const
  return 'info' as const
}

export const handlingResultLabels: Record<HandlingResultStatus, string> = {
  SUCCESS: '办理成功',
  FAILED: '办理失败'
}

export const certTypeLabels: Record<CertType, string> = {
  SSL: 'SSL 证书',
  EXTERNAL: '外联证书'
}

export function canEditNetworkWorkOrder(status: NetworkWorkOrderStatus) {
  return status === 'DRAFT' || status === 'RETURNED'
}

export function canCancelNetworkWorkOrder(status: NetworkWorkOrderStatus) {
  return status === 'DRAFT' || status === 'RETURNED' || status === 'IN_REVIEW'
}

export function canRegisterResult(status: NetworkWorkOrderStatus) {
  return status === 'IN_REVIEW' || status === 'COMPLETED'
}

export function certPayloadFromDetail(payload: Record<string, unknown>) {
  return {
    certType: (payload.certType || 'SSL') as CertType,
    subjectName: String(payload.subjectName || ''),
    purpose: String(payload.purpose || ''),
    description: payload.description ? String(payload.description) : null
  }
}

export function clbPayloadFromDetail(payload: Record<string, unknown>) {
  return {
    clbName: String(payload.clbName || ''),
    purpose: String(payload.purpose || ''),
    description: payload.description ? String(payload.description) : null
  }
}

export function dnsPayloadFromDetail(payload: Record<string, unknown>) {
  return {
    domainName: String(payload.domainName || ''),
    purpose: String(payload.purpose || ''),
    description: payload.description ? String(payload.description) : null
  }
}
