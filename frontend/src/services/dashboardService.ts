import { api } from './api';
import type {
  DashboardData,
  DashboardSummary,
  Environment,
  ErrorBreakdown,
  LatencyPoint,
  SystemHealth,
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

  const [summary, latency, errors, health] = await Promise.all([
    api.get<DashboardSummary>('/dashboard/summary', { params }),
    api.get<LatencyPoint[]>('/dashboard/latency', { params }),
    api.get<ErrorBreakdown>('/dashboard/errors', { params }),
    api.get<SystemHealth[]>('/dashboard/health', { params }),
  ]);

  return {
    summary: summary.data,
    latency: latency.data,
    errors: errors.data,
    health: health.data,
  };
}
