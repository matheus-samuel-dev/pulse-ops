import { api } from './api';
import type { PageResponse } from '../types/api';

export interface ConnectionInput { baseUrl: string; healthEndpoint: string; timeoutMs: number; publicUrl?: string; actionPath: string; accessToken?: string; clearToken: boolean; autoDispatch: boolean }
export interface ConnectionView { baseUrl: string; healthEndpoint: string; timeoutMs: number; publicUrl?: string; actionPath: string; credentialConfigured: boolean; autoDispatch: boolean }
export interface IntegrationRun { id: string; systemName: string; systemId?: string; slug: string; state: string; message?: string; externalId?: string; httpStatus?: number; reportUrl?: string; overallScore?: number; createdAt: string; updatedAt: string }
export const runLabels: Record<string, string> = { PENDING: 'Aguardando', RUNNING: 'Em execução', COMPLETED: 'Concluída', FAILED: 'Falhou', CANCELLED: 'Cancelada', ACCEPTED: 'Recebida pelo Nexus Flow' };
export const connectionsService = {
  async get(slug: string) { return (await api.get<ConnectionView | null>(`/connections/${slug}`)).data || null; },
  async save(slug: string, input: ConnectionInput) { return (await api.put<ConnectionView>(`/connections/${slug}`, input)).data; },
  async remove(slug: string) { await api.delete(`/connections/${slug}`); },
  async runs(systemId?: string, page = 0) { return (await api.get<PageResponse<IntegrationRun>>('/connections/runs', { params: { systemId, page, size: 10 } })).data; },
  async request(slug: string, systemId: string, authorizationConfirmed: boolean) { return (await api.post<IntegrationRun>(`/connections/${slug}/actions`, { systemId, authorizationConfirmed }, { timeout: 15_000 })).data; },
  async refresh(id: string) { return (await api.post<IntegrationRun>(`/connections/runs/${id}/refresh`, null, { timeout: 15_000 })).data; },
};
