import { api } from './api';
import type { Environment, PageResponse } from '../types/api';
export interface RecordedEvent { id: string; systemId?: string; systemName: string; environment: Environment | null; type: string; severity: string; title: string; description?: string; source: string; occurredAt: string }
export const eventsService = {
  async list(params: { page: number; query: string; severity?: string; systemId?: string; period?: string; environment?: Environment }, signal?: AbortSignal) {
    return (await api.get<PageResponse<RecordedEvent>>('/events', { params, signal })).data;
  },
};
