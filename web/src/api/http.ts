import axios, { type AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import router from '../router'
import type { ApiResponse, TokenPair } from '../types/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

let refreshPromise: Promise<string> | null = null

export const PROJECT_CONTEXT_ID_STORAGE_KEY = 'ccb.current_project_id'

export type ProjectContextRequestConfig = AxiosRequestConfig & { projectContext?: number | null }

export function withProjectContext(projectContext: number | null): ProjectContextRequestConfig {
  return { projectContext }
}

function applyToken(pair: TokenPair) {
  localStorage.setItem('ccb.access_token', pair.accessToken)
  localStorage.setItem('ccb.refresh_token', pair.refreshToken)
}

function expireSession() {
  localStorage.removeItem('ccb.access_token')
  localStorage.removeItem('ccb.refresh_token')
  const redirect = router.currentRoute.value.fullPath
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect } })
  }
}

function refreshAccessToken() {
  if (!refreshPromise) {
    const refreshToken = localStorage.getItem('ccb.refresh_token')
    if (!refreshToken) {
      expireSession()
      return Promise.reject(new Error('refresh token missing'))
    }
    refreshPromise = http.post<ApiResponse<TokenPair>>('/auth/refresh', { refreshToken }, withProjectContext(null))
      .then(response => {
        applyToken(response.data.data)
        return response.data.data.accessToken
      })
      .catch(error => {
        expireSession()
        throw error
      })
      .finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('ccb.access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  const request = config as InternalAxiosRequestConfig & { projectContext?: number | null }
  const storedProjectId = Number(localStorage.getItem(PROJECT_CONTEXT_ID_STORAGE_KEY) || 0)
  const projectId = request.projectContext === undefined ? storedProjectId : request.projectContext
  if (projectId && Number.isSafeInteger(projectId) && projectId > 0) config.headers.set('X-Project-Id', String(projectId))
  else config.headers.delete('X-Project-Id')
  return config
})

http.interceptors.response.use(
  response => response,
  async (error: AxiosError) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const url = String(config?.url || '')
    if (error.response?.status !== 401 || !config || url.includes('/auth/login') || url.includes('/auth/refresh') || config._retry) {
      return Promise.reject(error)
    }
    config._retry = true
    try {
      const accessToken = await refreshAccessToken()
      config.headers.Authorization = `Bearer ${accessToken}`
      return http(config)
    } catch {
      return Promise.reject(error)
    }
  }
)

export default http
