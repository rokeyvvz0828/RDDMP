export type ProjectStatus = 'ACTIVE' | 'ARCHIVED'
export type ProjectRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER'
export type ProjectAction = 'VIEW' | 'WRITE' | 'MANAGE_MEMBERS' | 'MANAGE_PROJECT'

export interface ProjectSummary {
  id: number
  projectCode: string
  projectName: string
  status: ProjectStatus
  ownerUserId: number
  ownerDisplayName: string
  currentRole: ProjectRole
  allowedActions: ProjectAction[]
  version: number
}

export interface ProjectMembership {
  projectId: number
  userId: number
  username: string
  displayName: string
  role: ProjectRole
  allowedActions: ProjectAction[]
}

export interface ProjectMemberCandidate {
  id: number
  username: string
  displayName: string
  orgId: number
  orgName: string | null
}

export interface ProjectPage<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface ProjectListQuery {
  page: number
  size: number
  keyword?: string
  status?: ProjectStatus
}

export interface CreateProjectCommand {
  projectCode: string
  projectName: string
}

export interface UpdateProjectCommand {
  projectName: string
  version: number
}

export interface AddProjectMemberCommand {
  userId: number
  role: Exclude<ProjectRole, 'OWNER'>
  version: number
}

export interface ChangeProjectMemberRoleCommand {
  role: Exclude<ProjectRole, 'OWNER'>
  version: number
}

export interface ProjectContextProvider {
  list(): Promise<ProjectSummary[]>
}

export interface ProjectContextStoreContract {
  availableProjects: ProjectSummary[]
  currentProject: ProjectSummary | null
  refresh(): Promise<void>
  select(projectId: number): void
  reset(): void
}
