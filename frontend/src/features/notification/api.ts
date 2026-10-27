import { get, put } from '../../shared/api/client'
import type { AppNotification, Page } from '../../shared/api/types'

export const fetchNotifications = (cursor?: string) =>
  get<Page<AppNotification>>(`/api/notifications${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`)
export const fetchUnreadCount = () => get<{ unreadCount: number }>('/api/notifications/unread-count')
export const markRead = (id: number) => put<AppNotification>(`/api/notifications/${id}/read`)
