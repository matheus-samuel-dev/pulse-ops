import { alpha, Box, LinearProgress, Typography, useTheme } from '@mui/material';
import { SystemStatusChip } from '../dashboard/SystemStatusChip';
import { formatPercent, formatRelativeTime } from '../dashboard/dashboardFormatters';
import type { Integration, IntegrationStatus } from '../../types/integrations';
import type { SystemStatus } from '../../types/api';
import { integrationIdentity, integrationLabels } from './integrationPresentation';

const systemStatuses: Record<IntegrationStatus, SystemStatus> = {
  ONLINE: 'OPERATIONAL', ATTENTION: 'DEGRADED', OFFLINE: 'DOWN', UNKNOWN: 'UNKNOWN',
};

export function IntegrationStatusBadge({ integration }: { integration: Integration }) {
  return <SystemStatusChip status={systemStatuses[integration.status]}
    label={!integration.configured ? 'Não configurado' : !integration.enabled ? 'Pausado' : integrationLabels[integration.status]} />;
}

export function IntegrationIcon({ id, size = 40 }: { id: string; size?: number }) {
  const theme = useTheme();
  const { Icon, color } = integrationIdentity(id);
  return <Box aria-hidden="true" sx={{ width: size, height: size, flexShrink: 0, display: 'grid', placeItems: 'center',
    borderRadius: 2.25, bgcolor: alpha(theme.palette[color].main, 0.12), color: `${color}.main`,
    border: '1px solid', borderColor: alpha(theme.palette[color].main, 0.18) }}><Icon fontSize={size > 44 ? 'large' : 'small'} /></Box>;
}

export function IntegrationTime({ value }: { value: string | null }) {
  return value ? <Typography component="time" dateTime={value} variant="body2"
    title={new Date(value).toLocaleString('pt-BR')}>{formatRelativeTime(value)}</Typography> : <Typography variant="body2" color="text.secondary">—</Typography>;
}

export function IntegrationHealth({ integration }: { integration: Integration }) {
  return <Box minWidth={70}>
    <Typography variant="body2" fontWeight={650}>{integration.healthPercent === null ? '—' : formatPercent(integration.healthPercent)}</Typography>
    {integration.healthPercent !== null && <LinearProgress variant="determinate" value={integration.healthPercent}
      aria-label={`Taxa de sucesso dos checks de ${integration.name} nas últimas 24 horas`}
      sx={{ height: 4, borderRadius: 2, mt: 0.6, maxWidth: 100 }} />}
  </Box>;
}

export function IntegrationMetric({ label, children }: { label: string; children: React.ReactNode }) {
  return <Box minWidth={0}><Typography variant="caption" component="dt" color="text.secondary" mb={0.5}>{label}</Typography>
    <Box component="dd" m={0} sx={{ overflowWrap: 'anywhere', fontSize: '0.85rem', fontWeight: 600 }}>{children}</Box></Box>;
}
