import http from './http'
import type { ApiResponse } from '../types/auth'
import type { NotificationPage } from '../types/notification'

export function getNotifications(page = 1, size = 20, unreadOnly = false) {
  return http.get<ApiResponse<NotificationPage>>('/notifications', { params: { page, size, unreadOnly } })
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
