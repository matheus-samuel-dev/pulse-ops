import { api } from './api';
import type { Incident, IncidentInput, IncidentSeverity, IncidentStatus } from '../types/api';

export interface IncidentFilters { systemId?: string; status?: IncidentStatus; severity?: IncidentSeverity }

export const incidentsService = {
  async list(filters: IncidentFilters = {}) { return (await api.get<Incident[]>('/incidents', { params: filters })).data; },
  async create(input: IncidentInput) { return (await api.post<Incident>('/incidents', input)).data; },
  async investigate(id: string) { return (await api.patch<Incident>(`/incidents/${id}/investigating`)).data; },
  async resolve(id: string) { return (await api.patch<Incident>(`/incidents/${id}/resolve`)).data; },
};
