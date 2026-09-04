export type NotificationLevel = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR'
export type NotificationView = 'ALL' | 'UNREAD' | 'ARCHIVED'

export interface SystemNotification {
  id: number
  title: string
  content: string
  level: NotificationLevel
  sourceName: string
  moduleCode: string
  moduleName: string
  businessType: string
  businessKey: string
  actionPath: string | null
  projectRef: string | null
  projectName: string | null
  read: boolean
  readAt: string | null
  archivedAt: string | null
  createdAt: string
}

export interface NotificationModuleSummary {
  moduleCode: string
  moduleName: string
  totalCount: number
  unreadCount: number
}

export interface NotificationPage {
  records: SystemNotification[]
  total: number
  page: number
  size: number
}
