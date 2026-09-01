import { api } from './api';
import type { Deployment, DeploymentInput } from '../types/api';

export const deploymentsService = {
  async list(systemId?: string) { return (await api.get<Deployment[]>('/deployments', { params: systemId ? { systemId } : {} })).data; },
  async create(input: DeploymentInput) { return (await api.post<Deployment>('/deployments', input)).data; },
  async transition(id: string, action: 'start' | 'success' | 'failure' | 'rollback', durationSeconds = 0) {
    return (await api.patch<Deployment>(`/deployments/${id}/${action}`, { durationSeconds })).data;
  },
};
