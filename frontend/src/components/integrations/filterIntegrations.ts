import type { Integration, IntegrationStatus } from '../../types/integrations';

export type IntegrationSort = 'name' | 'sync' | 'health' | 'status';
const statusOrder: Record<IntegrationStatus, number> = { OFFLINE: 0, ATTENTION: 1, ONLINE: 2, UNKNOWN: 3 };

export function filterIntegrations(integrations: Integration[], query: string, status: IntegrationStatus | 'ALL', sort: IntegrationSort) {
  const normalize = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  return integrations.filter(item => normalize(item.name).includes(normalize(query.trim())) && (status === 'ALL' || item.status === status))
    .sort((a, b) => {
      switch (sort) {
        case 'sync': return (b.lastSuccessfulSyncAt ? Date.parse(b.lastSuccessfulSyncAt) : 0) - (a.lastSuccessfulSyncAt ? Date.parse(a.lastSuccessfulSyncAt) : 0);
        case 'health': return (b.healthPercent ?? -1) - (a.healthPercent ?? -1);
        case 'status': return statusOrder[a.status] - statusOrder[b.status];
        default: return a.name.localeCompare(b.name, 'pt-BR');
      }
    });
}
