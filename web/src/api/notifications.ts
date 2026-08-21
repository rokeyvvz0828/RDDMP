import http from './http'
import type { ApiResponse } from '../types/auth'
import type { NotificationModuleSummary, NotificationPage, NotificationView } from '../types/notification'

export function getNotifications(page = 1, size = 20, view: NotificationView = 'ALL', moduleCode?: string) {
  return http.get<ApiResponse<NotificationPage>>('/notifications', {
    params: { page, size, view, moduleCode: moduleCode || undefined }
  })
}

export function getNotificationModules(view: NotificationView = 'ALL') {
  return http.get<ApiResponse<NotificationModuleSummary[]>>('/notifications/modules', { params: { view } })
}

export function getNotificationUnreadCount() {
  return http.get<ApiResponse<{ count: number }>>('/notifications/unread-count')
}

export function markNotificationRead(notificationId: number) {
  return http.patch<ApiResponse<void>>(`/notifications/${notificationId}/read`)
}

export function markAllNotificationsRead() {
  return http.patch<ApiResponse<{ changed: number }>>('/notifications/read-all')
}

export function archiveNotification(notificationId: number) {
  return http.patch<ApiResponse<void>>(`/notifications/${notificationId}/archive`)
}

export function restoreNotification(notificationId: number) {
  return http.patch<ApiResponse<void>>(`/notifications/${notificationId}/restore`)
}

export function archiveReadNotifications() {
  return http.patch<ApiResponse<{ changed: number }>>('/notifications/archive-read')
}
