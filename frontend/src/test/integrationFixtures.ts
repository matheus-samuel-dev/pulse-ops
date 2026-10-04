import type { Integration, IntegrationOverview } from '../types/integrations';

/** Test-only fixtures. No generated telemetry is imported by the application. */
export const auditorFixture: Integration = {
  id: 'ai-web-auditor', name: 'AI Web Auditor', description: 'Auditoria automatizada de sites e aplicações', type: 'HTTP · Relatórios de qualidade',
  primary: true, systemId: 'system-1', configured: true, enabled: true, status: 'ONLINE', statusReason: 'Serviço respondendo',
  publicUrl: 'https://auditor.example.com', baseUrl: 'Endereço protegido', healthEndpoint: 'Endpoint protegido',
  lastCheckedAt: '2026-09-10T14:00:00Z', lastSuccessfulSyncAt: '2026-09-10T13:00:00Z', lastReportAt: '2026-09-10T12:00:00Z',
  responseTimeMs: 120, healthPercent: 90, checksLast24h: 10, errorsLast24h: 1, lastFailureAt: '2026-09-10T11:00:00Z', nextCheckAt: null,
};
export const helpdeskFixture: Integration = { ...auditorFixture, id: 'helpdesk', name: 'HelpDesk', primary: false, status: 'OFFLINE', systemId: 'system-2', publicUrl: null };
export const hospitalFixture: Integration = { ...auditorFixture, id: 'hospital', name: 'Gestão Hospitalar', primary: false, status: 'UNKNOWN', configured: false,
  systemId: null, enabled: false, healthPercent: null, checksLast24h: 0, errorsLast24h: 0, lastSuccessfulSyncAt: null, lastCheckedAt: null,
  lastReportAt: null, responseTimeMs: null, publicUrl: null };
export const overviewFixture: IntegrationOverview = {
  integrations: [auditorFixture, helpdeskFixture, hospitalFixture],
  summary: { connected: 2, operational: 1, withErrors: 1, eventsToday: 7 },
  readOnly: false, generatedAt: '2026-09-10T14:00:00Z', reportingTimezone: 'America/Sao_Paulo',
};
