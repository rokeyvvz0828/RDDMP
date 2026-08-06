import http from './http'
import type { ApiResponse } from '../types/auth'

export interface AiProvider {
  id: number
  provider_code: string
  provider_name: string
  endpoint?: string
  status: number
  created_at?: string
}

export interface AiModel {
  id: number
  provider_id: number
  model_code: string
  model_name: string
  capabilities?: string
  status: number
  created_at?: string
}

export interface AiRoute {
  id: number
  capability: string
  model_id: number
  priority: number
  status: number
}

export function listAiProviders() {
  return http.get<ApiResponse<AiProvider[]>>('/ai/providers')
}

export function createAiProvider(data: { providerCode: string; providerName: string; endpoint?: string }) {
  return http.post<ApiResponse<AiProvider>>('/ai/providers', data)
}

export function listAiModels() {
  return http.get<ApiResponse<AiModel[]>>('/ai/models')
}

export function createAiModel(data: Record<string, unknown>) {
  return http.post<ApiResponse<AiModel>>('/ai/models', data)
}

export function listAiRoutes() {
  return http.get<ApiResponse<AiRoute[]>>('/ai/routes')
}

export function createAiRoute(data: { capability: string; modelId: number; priority: number }) {
  return http.post<ApiResponse<AiRoute>>('/ai/routes', data)
}
