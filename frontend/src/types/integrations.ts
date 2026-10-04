export type IntegrationStatus = 'ONLINE' | 'ATTENTION' | 'OFFLINE' | 'UNKNOWN' | 'NOT_CONFIGURED';

export interface Integration {
  id: string;
  name: string;
  description: string;
  type: string;
  primary: boolean;
  systemId: string | null;
  configured: boolean;
  enabled: boolean;
  status: IntegrationStatus;
  statusReason: string;
  publicUrl: string | null;
  baseUrl: string | null;
  healthEndpoint: string | null;
  lastCheckedAt: string | null;
  lastSuccessfulSyncAt: string | null;
  lastReportAt: string | null;
  responseTimeMs: number | null;
  healthPercent: number | null;
  checksLast24h: number;
  errorsLast24h: number;
  lastFailureAt: string | null;
  nextCheckAt: string | null;
}

export interface IntegrationOverview {
  integrations: Integration[];
  summary: { connected: number; operational: number; withErrors: number; eventsToday: number };
  readOnly: boolean;
  generatedAt: string;
  reportingTimezone: string;
}

export interface IntegrationCheckResult {
  integrationId: string;
  status: IntegrationStatus;
  responseTimeMs: number | null;
  checkedAt: string;
  message: string;
  cached: boolean;
  nextCheckAt: string;
}
