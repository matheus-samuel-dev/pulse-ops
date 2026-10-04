import { api } from './api';
import type { Notification, NotificationPage } from '../types/api';

export const notificationsService = {
  async list(page = 0, size = 50) { return (await api.get<NotificationPage>('/notifications', { params: { page, size } })).data; },
  async markRead(id: string) { return (await api.patch<Notification>(`/notifications/${id}/read`)).data; },
  async markUnread(id: string) { return (await api.patch<Notification>(`/notifications/${id}/unread`)).data; },
  async markAllRead() { await api.patch('/notifications/read-all'); },
};
