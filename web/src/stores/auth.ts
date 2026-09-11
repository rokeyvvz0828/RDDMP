import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import http, { PROJECT_CONTEXT_ID_STORAGE_KEY, withProjectContext } from '../api/http'
import type { ApiResponse, AuthMe, RouteNode, TokenPair } from '../types/auth'
import { useTabsStore } from './tabs'

function hideOfflineRoutes(nodes: RouteNode[]): RouteNode[] {
  return nodes
    .filter(node => node.routePath !== '/system/form-metadata')
    .map(node => ({ ...node, children: hideOfflineRoutes(node.children || []) }))
}

function storedProjectId() {
  const value = Number(localStorage.getItem(PROJECT_CONTEXT_ID_STORAGE_KEY) || 0)
  return Number.isSafeInteger(value) && value > 0 ? value : null
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('ccb.access_token') || '')
  const refreshToken = ref(localStorage.getItem('ccb.refresh_token') || '')
  const user = ref<AuthMe | null>(null)
  const routes = ref<RouteNode[]>([])
  const currentProjectId = ref<number | null>(null)
  const loading = ref(false)
  const isAuthenticated = computed(() => Boolean(token.value && user.value))

  function saveTokens(pair: TokenPair) {
    token.value = pair.accessToken
    refreshToken.value = pair.refreshToken
    localStorage.setItem('ccb.access_token', pair.accessToken)
    localStorage.setItem('ccb.refresh_token', pair.refreshToken)
  }

  function clear() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    routes.value = []
    currentProjectId.value = null
    useTabsStore().closeAll()
    localStorage.removeItem('ccb.access_token')
    localStorage.removeItem('ccb.refresh_token')
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const response = await http.post<ApiResponse<TokenPair>>('/auth/login', { username, password }, withProjectContext(null))
      useTabsStore().closeAll()
      saveTokens(response.data.data)
      await hydrate(null)
    } finally {
      loading.value = false
    }
  }

  async function fetchAuthorization(projectId: number | null = null) {
    const config = withProjectContext(projectId)
    const [me, menu] = await Promise.all([
      http.get<ApiResponse<AuthMe>>('/auth/me', config),
      http.get<ApiResponse<RouteNode[]>>('/auth/routes', config)
    ])
    return { user: me.data.data, routes: hideOfflineRoutes(menu.data.data), projectId }
  }

  function applyAuthorization(snapshot: { user: AuthMe; routes: RouteNode[]; projectId: number | null }) {
    user.value = snapshot.user
    routes.value = snapshot.routes
    currentProjectId.value = snapshot.projectId
  }

  async function hydrateProjectAuthorizationIfAvailable() {
    if (currentProjectId.value) return
    const { useProjectContextStore } = await import('./project-context')
    const projectContext = useProjectContextStore()
    await projectContext.initialize()
    if (!projectContext.currentId) return
    applyAuthorization(await fetchAuthorization(projectContext.currentId))
  }

  async function hydrate(projectId: number | null = storedProjectId()) {
    if (!token.value) return
    try {
      applyAuthorization(await fetchAuthorization(projectId))
      if (!projectId) {
        try {
          await hydrateProjectAuthorizationIfAvailable()
        } catch {
          // Keep global authorization when the optional project context cannot be restored.
        }
      }
    } catch {
      clear()
    }
  }

  async function logout() {
    try {
      if (token.value) await http.post('/auth/logout', { refreshToken: refreshToken.value })
    } finally {
      clear()
    }
  }

  async function changePassword(oldPassword: string, newPassword: string, confirmPassword: string) {
    await http.post('/auth/change-password', { oldPassword, newPassword, confirmPassword })
    clear()
  }

  function updateUser(nextUser: AuthMe) {
    user.value = nextUser
  }

  function hasPermission(permission: string) {
    return Boolean(user.value?.permissions.includes('system:admin') || user.value?.permissions.includes(permission))
  }

  function hasRoute(path: string) {
    const visit = (nodes: RouteNode[]): boolean => nodes.some(node => node.routePath === path || visit(node.children || []))
    return visit(routes.value)
  }

  function firstAccessibleReleasePath() {
    return [
      '/release/windows',
      '/release/applications',
      '/release/production-baseline',
      '/release/production-versions',
      '/release/analytics',
      '/release/workflow-bindings'
    ].find(hasRoute)
  }

  return { token, user, routes, currentProjectId, loading, isAuthenticated, login, hydrate, fetchAuthorization, applyAuthorization, logout, changePassword, updateUser, hasPermission, hasRoute, firstAccessibleReleasePath }
})
