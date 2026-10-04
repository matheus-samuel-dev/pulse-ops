import { api } from './api';
import type {
  DashboardData,
  Environment,
} from '../types/api';

export type DashboardPeriod = '24h' | '7d' | '30d';
export type EnvironmentFilter = Environment | 'ALL';

export interface DashboardFilters {
  period: DashboardPeriod;
  environment: EnvironmentFilter;
}

export async function getDashboard(filters: DashboardFilters): Promise<DashboardData> {
  const params = {
    period: filters.period,
    ...(filters.environment !== 'ALL' && { environment: filters.environment }),
  };

  return (await api.get<DashboardData>('/dashboard', { params })).data;
}
