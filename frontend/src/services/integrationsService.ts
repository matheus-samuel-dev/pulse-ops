import { api } from './api';
import type { Integration, IntegrationCheckResult, IntegrationOverview } from '../types/integrations';
import type { OperationalEvent } from '../types/api';

export const integrationsService = {
  async overview(signal?: AbortSignal) {
    return (await api.get<IntegrationOverview>('/integrations', { signal })).data;
  },
  async detail(id: string, signal?: AbortSignal) {
    return (await api.get<Integration>(`/integrations/${encodeURIComponent(id)}`, { signal })).data;
  },
  async events(id: string | null, signal?: AbortSignal) {
    const path = id ? `/${encodeURIComponent(id)}/events` : '/events';
    return (await api.get<OperationalEvent[]>(`/integrations${path}`, { signal })).data;
  },
  async check(id: string) {
    // Existing system configuration permits up to 60 seconds for an outbound check.
    return (await api.post<IntegrationCheckResult>(`/integrations/${encodeURIComponent(id)}/health-check`, undefined, { timeout: 75_000 })).data;
  },
};
