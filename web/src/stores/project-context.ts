import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { listAvailableProjects } from '../api/projects'
import { useAuthStore } from './auth'
import type { ProjectSummary } from '../types/project-context'

export const useProjectContextStore = defineStore('project-context', () => {
  const auth = useAuthStore()
  const availableProjects = ref<ProjectSummary[]>([])
  const currentProjectId = ref<number | null>(null)
  const loading = ref(false)
  const refreshing = ref(false)
  const initialized = ref(false)
  const error = ref('')
  const selectionExpired = ref(false)
  const identityKey = ref('')
  let refreshPromise: Promise<void> | null = null
  let generation = 0

  const currentProject = computed(() => availableProjects.value.find(item => item.id === currentProjectId.value) || null)
  const storageKey = computed(() => auth.user ? `ccb.project-context.${auth.user.tenantId}.${auth.user.id}` : '')

  function persistedProjectId() {
    if (!storageKey.value) return null
    const value = Number(localStorage.getItem(storageKey.value))
    return Number.isSafeInteger(value) && value > 0 ? value : null
  }

  function persist(projectId: number | null) {
    if (!storageKey.value) return
    if (projectId === null) localStorage.removeItem(storageKey.value)
    else localStorage.setItem(storageKey.value, String(projectId))
  }

  function select(projectId: number) {
    if (!availableProjects.value.some(item => item.id === projectId)) {
      selectionExpired.value = true
      return
    }
    currentProjectId.value = projectId
    selectionExpired.value = false
    persist(projectId)
  }

  async function refresh() {
    if (refreshPromise) return refreshPromise
    const requestGeneration = generation
    const pending = (async () => {
      const firstLoad = !initialized.value
      loading.value = firstLoad
      refreshing.value = !firstLoad
      error.value = ''
      try {
        const response = await listAvailableProjects()
        if (requestGeneration !== generation) return
        const nextProjects = response.data.data
        const preferredId = currentProjectId.value ?? persistedProjectId()
        const preferredExists = preferredId !== null && nextProjects.some(item => item.id === preferredId)
        selectionExpired.value = preferredId !== null && !preferredExists
        availableProjects.value = nextProjects
        currentProjectId.value = preferredExists ? preferredId : nextProjects[0]?.id ?? null
        persist(currentProjectId.value)
        initialized.value = true
      } catch (cause) {
        if (requestGeneration !== generation) return
        error.value = cause instanceof Error ? cause.message : '项目上下文加载失败'
        throw cause
      } finally {
        if (requestGeneration === generation) {
          loading.value = false
          refreshing.value = false
        }
      }
    })()
    refreshPromise = pending
    void pending.finally(() => {
      if (refreshPromise === pending) refreshPromise = null
    }).catch(() => undefined)
    return pending
  }

  function clearState() {
    availableProjects.value = []
    currentProjectId.value = null
    loading.value = false
    refreshing.value = false
    initialized.value = false
    error.value = ''
    selectionExpired.value = false
    refreshPromise = null
  }

  function bindCurrentUser() {
    const nextIdentity = auth.user ? `${auth.user.tenantId}:${auth.user.id}` : ''
    if (identityKey.value === nextIdentity) return
    generation += 1
    clearState()
    identityKey.value = nextIdentity
    if (nextIdentity) void refresh().catch(() => undefined)
  }

  function reset() {
    persist(null)
    generation += 1
    clearState()
    identityKey.value = ''
  }

  return {
    availableProjects,
    currentProject,
    currentProjectId,
    loading,
    refreshing,
    initialized,
    error,
    selectionExpired,
    bindCurrentUser,
    refresh,
    select,
    reset
  }
})
