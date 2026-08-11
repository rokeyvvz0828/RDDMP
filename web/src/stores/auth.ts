import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import http from '../api/http'
import type { ApiResponse, AuthMe, RouteNode, TokenPair } from '../types/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('ccb.access_token') || '')
  const refreshToken = ref(localStorage.getItem('ccb.refresh_token') || '')
  const user = ref<AuthMe | null>(null)
  const routes = ref<RouteNode[]>([])
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
    localStorage.removeItem('ccb.access_token')
    localStorage.removeItem('ccb.refresh_token')
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const response = await http.post<ApiResponse<TokenPair>>('/auth/login', { username, password })
      saveTokens(response.data.data)
      await hydrate()
    } finally {
      loading.value = false
    }
  }

  async function hydrate() {
    if (!token.value) return
    try {
      const [me, menu] = await Promise.all([
        http.get<ApiResponse<AuthMe>>('/auth/me'),
        http.get<ApiResponse<RouteNode[]>>('/auth/routes')
      ])
      user.value = me.data.data
      routes.value = menu.data.data
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

  return { token, user, routes, loading, isAuthenticated, login, hydrate, logout, changePassword }
})
