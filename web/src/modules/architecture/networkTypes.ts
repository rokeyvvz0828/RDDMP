/**
 * CLB/DNS/证书网络专项工单类型（REQ-20260823-051）。
 * 后端契约见 docs/integration/architecture-module-contract.md 网络专项工单章节。
 */
export type NetworkWorkOrderKind = 'CLB' | 'DNS' | 'CERT'
export type NetworkWorkOrderActionType =
  | 'OPEN' | 'ADJUST'          // CLB
  | 'ADD' | 'CHANGE' | 'REMOVE' // DNS
  | 'APPLY' | 'RENEW' | 'REVOKE' // CERT
export type NetworkWorkOrderStatus = 'DRAFT' | 'IN_REVIEW' | 'RETURNED' | 'COMPLETED' | 'REJECTED' | 'CANCELLED'
export type HandlingResultStatus = 'SUCCESS' | 'FAILED'
export type CertType = 'SSL' | 'EXTERNAL'

export interface ClbPayload {
  clbName: string
  purpose: string
  description?: string | null
}

export interface DnsPayload {
  domainName: string
  purpose: string
  description?: string | null
}

export interface CertPayload {
  certType: CertType
  subjectName: string
  purpose: string
  description?: string | null
}

export type NetworkWorkOrderPayload = ClbPayload | DnsPayload | CertPayload

export interface NetworkWorkOrderSummary {
  id: number
  kind: NetworkWorkOrderKind
  actionType: NetworkWorkOrderActionType
  subject: string
  applicantId: number
  reason: string | null
  status: NetworkWorkOrderStatus
  resultStatus: HandlingResultStatus | null
  resultDescription: string | null
  currentBusinessRound: number
  cancellationRequested: boolean
  rowVersion: number
  createdBy: number
  updatedBy: number
  createdAt: string
  updatedAt: string
}

export interface NetworkWorkOrderHistory {
  id: number
  eventType: string
  fromStatus: NetworkWorkOrderStatus | null
  toStatus: NetworkWorkOrderStatus | null
  businessRound: number
  summary: string | null
  snapshotJson: string | null
  diffJson: string | null
  operatorId: number
  occurredAt: string
}

export interface NetworkWorkOrderDetail {
  workOrder: NetworkWorkOrderSummary
  payload: NetworkWorkOrderPayload
  attachmentIds: number[]
  resultAttachmentIds: number[]
  history: NetworkWorkOrderHistory[]
}

export interface CreateNetworkWorkOrderPayload {
  kind: NetworkWorkOrderKind
  actionType: NetworkWorkOrderActionType
  reason?: string | null
  payload: NetworkWorkOrderPayload
  attachmentIds?: number[]
}

export interface UpdateNetworkWorkOrderPayload {
  rowVersion: number
  reason?: string | null
  payload: NetworkWorkOrderPayload
  attachmentIds?: number[]
}

export interface RegisterHandlingResultPayload {
  rowVersion: number
  resultStatus: HandlingResultStatus
  resultDescription?: string | null
  resultAttachmentIds?: number[]
}
