export interface ProjectContextItem {
  ref: string
  name: string
  shortName: string
  status: 'ACTIVE' | 'ARCHIVED'
}

export interface ProjectContextProvider {
  list(): Promise<ProjectContextItem[]>
  readSelection(): string | null
  saveSelection(projectRef: string): void
}
