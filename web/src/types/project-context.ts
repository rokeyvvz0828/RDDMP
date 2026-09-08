import type { ProjectStatus, ProjectCreationType } from './project'

export interface ProjectContextItem {
  id: number
  ref: string
  name: string
  shortName: string
  status: ProjectStatus
  creationType: ProjectCreationType
}

export interface ProjectContextProvider {
  list(): Promise<ProjectContextItem[]>
  readSelection(): string | null
  saveSelection(projectRef: string): void
}
