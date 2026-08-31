import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { listProjects } from '../api/requirements'
import type { ProjectContextItem, ProjectContextProvider } from '../types/project-context'
import type { RequirementProject } from '../types/requirements'

const STORAGE_KEY = 'ccb.current_project_ref'

// 兜底演示项目：需求接口不可用时仍保证顶部项目下拉框可用
const FALLBACK_PROJECTS: ProjectContextItem[] = [
  { ref: 'P-RDDMP-UPGRADE', name: '统一研发交付平台升级项目', shortName: '研发交付平台', status: 'ACTIVE' },
  { ref: 'P-CORE-MODERN', name: '核心业务系统现代化改造项目', shortName: '核心现代化', status: 'ACTIVE' },
  { ref: 'P-DATA-GOV', name: '企业数据治理能力建设项目', shortName: '数据治理', status: 'ACTIVE' }
]

function toContextItem(project: RequirementProject): ProjectContextItem {
  return {
    ref: String(project.project_code),
    name: project.project_name,
    shortName: project.project_name,
    status: project.status === 'ARCHIVED' ? 'ARCHIVED' : 'ACTIVE'
  }
}

// 以需求管理 req_project 为项目唯一事实源：顶部下拉框与需求差异数据共用同一项目体系
class RequirementProjectContextProvider implements ProjectContextProvider {
  async list(): Promise<ProjectContextItem[]> {
    try {
      const response = await listProjects()
      const rows = response.data?.data || []
      return rows.length ? rows.map(toContextItem) : FALLBACK_PROJECTS
    } catch {
      return FALLBACK_PROJECTS
    }
  }
  readSelection() { return localStorage.getItem(STORAGE_KEY) }
  saveSelection(projectRef: string) { localStorage.setItem(STORAGE_KEY, projectRef) }
}

const provider: ProjectContextProvider = new RequirementProjectContextProvider()

export const useProjectContextStore = defineStore('project-context', () => {
  const projects = ref<ProjectContextItem[]>([])
  const currentRef = ref('')
  const loading = ref(false)
  const current = computed(() => projects.value.find(item => item.ref === currentRef.value) || projects.value[0] || null)
  async function initialize() {
    if (projects.value.length || loading.value) return
    loading.value = true
    try {
      projects.value = (await provider.list()).filter(item => item.status === 'ACTIVE')
      const saved = provider.readSelection()
      currentRef.value = projects.value.some(item => item.ref === saved) ? String(saved) : projects.value[0]?.ref || ''
      if (currentRef.value) provider.saveSelection(currentRef.value)
    } finally { loading.value = false }
  }
  function select(projectRef: string) {
    if (!projects.value.some(item => item.ref === projectRef)) return
    currentRef.value = projectRef
    provider.saveSelection(projectRef)
  }
  return { projects, currentRef, current, loading, initialize, select }
})
