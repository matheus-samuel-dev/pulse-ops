import { api } from './api';
import type { HealthCheck, MonitoredSystem, MonitoredSystemInput, PageResponse, SystemMetrics, LatencyPoint } from '../types/api';

export const systemsService = {
  async list() { return (await api.get<MonitoredSystem[]>('/systems')).data; },
  async get(id: string) { return (await api.get<MonitoredSystem>(`/systems/${id}`)).data; },
  async create(input: MonitoredSystemInput) { return (await api.post<MonitoredSystem>('/systems', input)).data; },
  async update(id: string, input: MonitoredSystemInput) { return (await api.put<MonitoredSystem>(`/systems/${id}`, input)).data; },
  async remove(id: string) { await api.delete(`/systems/${id}`); },
  async check(id: string) { return (await api.post<HealthCheck>(`/systems/${id}/checks`, undefined, { timeout: 70_000 })).data; },
  async latency(id: string, period: string) { return (await api.get<LatencyPoint[]>(`/systems/${id}/latency`, { params: { period } })).data; },
  async metrics(id: string, period: string) {
    return (await api.get<SystemMetrics>(`/systems/${id}/metrics`, { params: { period } })).data;
  },
  async checks(id: string, page = 0, size = 20, period = '24h') {
    return (await api.get<PageResponse<HealthCheck>>(`/systems/${id}/checks`, { params: { page, size, period } })).data;
  },
};
