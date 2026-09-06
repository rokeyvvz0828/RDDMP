import http from '../../api/http'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '../../types/auth'
import { useProjectContextStore } from '../../stores/project-context'
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

function projectConfig(config: AxiosRequestConfig = {}): AxiosRequestConfig {
  const projectRef = useProjectContextStore().currentRef
  if (!projectRef) throw new Error('请先选择项目')
  return { ...config, params: { ...config.params, projectRef } }
}

const projectHttp = {
  get: <T>(url: string, config?: AxiosRequestConfig) => http.get<T>(url, projectConfig(config)),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => http.post<T>(url, data, projectConfig(config)),
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => http.put<T>(url, data, projectConfig(config))
}

export async function listNetworkWorkOrders(query: {
  kind?: NetworkWorkOrderKind | ''
  status?: NetworkWorkOrderStatus | ''
  limit?: number
  offset?: number
}) {
  return (await projectHttp.get<ApiResponse<NetworkWorkOrderSummary[]>>(BASE, { params: compact(query) })).data.data
}

export async function getNetworkWorkOrder(id: number) {
  return (await projectHttp.get<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}`)).data.data
}

export async function createNetworkWorkOrder(payload: CreateNetworkWorkOrderPayload) {
  return (await projectHttp.post<ApiResponse<NetworkWorkOrderDetail>>(BASE, payload)).data.data
}

export async function updateNetworkWorkOrder(id: number, payload: UpdateNetworkWorkOrderPayload) {
  return (await projectHttp.put<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}`, payload)).data.data
}

export async function submitNetworkWorkOrder(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}/submit`, { rowVersion })).data.data
}

export async function cancelNetworkWorkOrder(id: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}/cancel`, { rowVersion })).data.data
}

export async function registerNetworkWorkOrderHandlingResult(id: number, payload: RegisterHandlingResultPayload) {
  return (await projectHttp.post<ApiResponse<NetworkWorkOrderDetail>>(`${BASE}/${id}/handling-result`, payload)).data.data
}

export async function removeNetworkWorkOrderAttachment(id: number, attachmentId: number, rowVersion: number) {
  return (await projectHttp.post<ApiResponse<NetworkWorkOrderDetail>>(
    `${BASE}/${id}/attachments/${attachmentId}/remove`,
    { rowVersion }
  )).data.data
}
