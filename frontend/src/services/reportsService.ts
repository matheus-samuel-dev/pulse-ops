import { api } from './api';
import type { Environment, OperationalReport } from '../types/api';

export const reportsService = {
  async operational(period: string, environment?: Environment, systemId?: string, page = 0) {
    return (await api.get<OperationalReport>('/reports/operational', {
      params: { period, ...(environment && { environment }), ...(systemId && { systemId }), page },
    })).data;
  },
};
