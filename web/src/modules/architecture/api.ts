import http from '../../api/http'
import type { ApiResponse } from '../../types/auth'
import type {
  ArchitectureResource,
  LogicalSubsystem,
  LogicalSubsystemCommand,
  LogicalSubsystemOption,
  OrganizationOption,
  PageResult,
  ParameterOption,
  PhysicalSubsystem,
  PhysicalSubsystemCommand,
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

export async function createLogicalSubsystem(command: LogicalSubsystemCommand) {
  return (await http.post<ApiResponse<LogicalSubsystem>>('/architecture/logical-subsystems', command)).data.data
}

export async function updateLogicalSubsystem(id: number, command: LogicalSubsystemCommand) {
  return (await http.put<ApiResponse<LogicalSubsystem>>(`/architecture/logical-subsystems/${id}`, command)).data.data
}

export async function deleteLogicalSubsystem(id: number) {
  await http.delete(`/architecture/logical-subsystems/${id}`)
}

export async function listPhysicalSubsystems(query: Query) {
  return (await http.get<ApiResponse<PageResult<PhysicalSubsystem>>>('/architecture/physical-subsystems', { params: compact(query) })).data.data
}

export async function getPhysicalSubsystem(id: number) {
  return (await http.get<ApiResponse<PhysicalSubsystem>>(`/architecture/physical-subsystems/${id}`)).data.data
}

export async function createPhysicalSubsystem(command: PhysicalSubsystemCommand) {
  return (await http.post<ApiResponse<PhysicalSubsystem>>('/architecture/physical-subsystems', command)).data.data
}

export async function updatePhysicalSubsystem(id: number, command: PhysicalSubsystemCommand) {
  return (await http.put<ApiResponse<PhysicalSubsystem>>(`/architecture/physical-subsystems/${id}`, command)).data.data
}

export async function deletePhysicalSubsystem(id: number) {
  await http.delete(`/architecture/physical-subsystems/${id}`)
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
