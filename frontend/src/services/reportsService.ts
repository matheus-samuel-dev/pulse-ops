import { api } from './api';
import type { Environment, OperationalReport } from '../types/api';

export const reportsService = {
  async operational(period: string, environment?: Environment) {
    return (await api.get<OperationalReport>('/reports/operational', {
      params: { period, ...(environment && { environment }) },
    })).data;
  },
};
