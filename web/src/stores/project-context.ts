import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getProjectWorkbench } from '../api/project'
import { apiErrorMessage } from '../api/error'
import type { ProjectContextItem, ProjectContextProvider } from '../types/project-context'
import type { Project } from '../types/project'

const STORAGE_KEY = 'ccb.current_project_ref'

function projectContextItem(project: Project): ProjectContextItem {
  return {
    ref: project.project_code,
    name: project.project_name,
    shortName: project.project_code,
    status: project.status,
    creationType: project.creation_type ?? 'NEW'
  }
}

class ProjectApiContextProvider implements ProjectContextProvider {
  async list(): Promise<ProjectContextItem[]> {
    const response = await getProjectWorkbench()
    const projects = Array.isArray(response.data.data) ? response.data.data : []
    return projects.map(projectContextItem)
  }
  readSelection() { return localStorage.getItem(STORAGE_KEY) }
  saveSelection(projectRef: string) { localStorage.setItem(STORAGE_KEY, projectRef) }
}

const provider: ProjectContextProvider = new ProjectApiContextProvider()

export const useProjectContextStore = defineStore('project-context', () => {
  const projects = ref<ProjectContextItem[]>([])
  const currentRef = ref('')
  const loading = ref(false)
  const error = ref('')
  const savedDuringLoad = new Map<string, ProjectContextItem>()
  const current = computed(() => projects.value.find(item => item.ref === currentRef.value) || projects.value[0] || null)
  async function initialize(force = false) {
    if (loading.value || (!force && projects.value.length)) return
    loading.value = true
    error.value = ''
    try {
      const loaded = await provider.list()
      // A list started before a save must not replace the confirmed metadata.
      const merged = new Map(loaded.map(item => [item.ref, item]))
      savedDuringLoad.forEach((item, key) => merged.set(key, item))
      projects.value = [...merged.values()]
      const saved = provider.readSelection()
      currentRef.value = projects.value.some(item => item.ref === saved) ? String(saved) : projects.value[0]?.ref || ''
      if (currentRef.value) provider.saveSelection(currentRef.value)
    } catch (cause: unknown) {
      error.value = apiErrorMessage(cause, '项目列表加载失败，请稍后重试')
    } finally {
      savedDuringLoad.clear()
      loading.value = false
    }
  }
  function retry() {
    return initialize(true)
  }
  function select(projectRef: string) {
    if (!projects.value.some(item => item.ref === projectRef)) return false
    currentRef.value = projectRef
    provider.saveSelection(projectRef)
    return true
  }
  function canAccess(projectRef: string) {
    return projects.value.some(item => item.ref === projectRef)
  }
  function syncProject(project: Project) {
    const item = projectContextItem(project)
    const index = projects.value.findIndex(value => value.ref === item.ref)
    if (index < 0) projects.value.push(item)
    else projects.value[index] = item
    if (loading.value) savedDuringLoad.set(item.ref, item)
  }
  return { projects, currentRef, current, loading, error, initialize, retry, select, canAccess, syncProject }
})
