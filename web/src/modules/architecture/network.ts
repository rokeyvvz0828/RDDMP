import http from '../../api/http'
import type { ApiResponse } from '../../types/auth'
import type {
  CreateNetworkWorkOrderPayload,
  NetworkWorkOrderDetail,
  NetworkWorkOrderKind,
  NetworkWorkOrderStatus,
  NetworkWorkOrderSummary,
  RegisterHandlingResultPayload,
  UpdateNetworkWorkOrderPayload
} from './networkTypes'

type QueryValue = string | number | null | undefined
type Query = Record<string, QueryValue>

function compact(query: Query) {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== '' && value !== null && value !== undefined)
  )
}

const BASE = '/architecture/network-work-orders'

export async function listNetworkWorkOrders(query: {
  kind?: NetworkWorkOrderKind | ''
  status?: NetworkWorkOrderStatus | ''
  limit?: number
  offset?: number
}) {
  return (await http.get<ApiResponse<NetworkWorkOrderSummary[]>>(BASE, { params: compact(query) })).data.data
}

export async function getNetworkWorkOrder(id: number) {
  return (await http.get<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}`)).data.data
}

export async function createNetworkWorkOrder(payload: CreateNetworkWorkOrderPayload) {
  return (await http.post<ApiResponse<NetworkWorkOrderDetail>>(BASE, payload)).data.data
}

export async function updateNetworkWorkOrder(id: number, payload: UpdateNetworkWorkOrderPayload) {
  return (await http.put<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}`, payload)).data.data
}

export async function submitNetworkWorkOrder(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}/submit`, { rowVersion })).data.data
}

export async function cancelNetworkWorkOrder(id: number, rowVersion: number) {
  return (await http.post<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}/cancel`, { rowVersion })).data.data
}

export async function registerNetworkWorkOrderHandlingResult(id: number, payload: RegisterHandlingResultPayload) {
  return (await http.post<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}/handling-result`, payload)).data.data
}

export async function removeNetworkWorkOrderAttachment(id: number, attachmentId: number, rowVersion: number) {
  return (await http.post<ApiResponse<NetworkWorkOrderDetail>>(
    `${BASE}/${id}/attachments/${attachmentId}/remove`,
    { rowVersion }
  )).data.data
}
