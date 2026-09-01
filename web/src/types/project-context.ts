import type { ProjectStatus } from './project'

export interface ProjectContextItem {
  ref: string
  name: string
  shortName: string
  status: ProjectStatus
}

export interface ProjectContextProvider {
  list(): Promise<ProjectContextItem[]>
  readSelection(): string | null
  saveSelection(projectRef: string): void
}
