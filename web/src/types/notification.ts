export type NotificationLevel = 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR'

export interface SystemNotification {
  id: number
  title: string
  content: string
  level: NotificationLevel
  sourceName: string
  businessType: string
  businessKey: string
  actionPath: string | null
  read: boolean
  readAt: string | null
  createdAt: string
}

export interface NotificationPage {
  records: SystemNotification[]
  total: number
  page: number
  size: number
}
