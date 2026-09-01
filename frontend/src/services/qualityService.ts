import { api } from './api';
import type { QualityOverview, QualityReport, QualitySummary } from '../types/api';

export const qualityService = {
  async overview() { return (await api.get<QualityOverview>('/quality/overview')).data; },
  async history(period = '30d', systemId?: string) {
    return (await api.get<QualityReport[]>('/quality/history', { params: { period, ...(systemId && { systemId }) } })).data;
  },
  async latest(systemId: string) { return (await api.get<QualitySummary>(`/quality/systems/${systemId}/latest`)).data; },
};
