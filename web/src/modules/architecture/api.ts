import http from '../../api/http'
import type { ApiResponse } from '../../types/auth'
import type {
  ArchitectureResource,
  CreateSubsystemChangeApplicationPayload,
  LogicalSubsystem,
  LogicalSubsystemOption,
  OrganizationOption,
  PageResult,
  ParameterOption,
  PhysicalSubsystem,
  SubsystemApplicationStatus,
  SubsystemChangeApplicationDetail,
  SubsystemChangeApplicationSummary,
  SubsystemSuggestion,
  UpdateSubsystemChangeApplicationPayload,
  UserOption
} from './types'

type QueryValue = string | number | null | undefined
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
