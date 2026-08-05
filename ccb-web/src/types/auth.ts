export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId: string
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  accessExpiresInSeconds: number
  refreshExpiresInSeconds: number
  tokenType: string
}

export interface AuthMe {
  id: number
  tenantId: number
  username: string
  displayName: string
  orgId: number
  orgName?: string | null
  avatarUrl?: string | null
  roles: string[]
  permissions: string[]
}

export interface RouteNode {
  id: number
  parentId: number
  menuType: string
  menuName: string
  routeName: string
  routePath: string
  componentPath: string
  permissionCode: string
  icon: string | null
  sortNo: number
  children: RouteNode[]
}