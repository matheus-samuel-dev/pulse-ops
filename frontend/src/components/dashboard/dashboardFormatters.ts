import type { Environment, SystemStatus } from '../../types/api';

export const percentFormatter = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

export const integerFormatter = new Intl.NumberFormat('pt-BR', {
  maximumFractionDigits: 0,
});

export function formatPercent(value: number): string {
  return `${percentFormatter.format(value)}%`;
}

export function formatLatency(value: number | null): string {
  return value == null ? '—' : `${integerFormatter.format(value)} ms`;
}

export function formatRelativeTime(value: string | null): string {
  if (!value) return 'Sem verificação';
  const date = new Date(value);
  const seconds = Math.round((date.getTime() - Date.now()) / 1_000);
  const formatter = new Intl.RelativeTimeFormat('pt-BR', { numeric: 'auto' });
  if (Math.abs(seconds) < 60) return formatter.format(seconds, 'second');
  const minutes = Math.round(seconds / 60);
  if (Math.abs(minutes) < 60) return formatter.format(minutes, 'minute');
  const hours = Math.round(minutes / 60);
  if (Math.abs(hours) < 24) return formatter.format(hours, 'hour');
  return formatter.format(Math.round(hours / 24), 'day');
}

export const statusLabels: Record<SystemStatus, string> = {
  OPERATIONAL: 'Operacional',
  DEGRADED: 'Atenção',
  DOWN: 'Indisponível',
  UNKNOWN: 'Sem dados',
};

export const environmentLabels: Record<Environment, string> = {
  PRODUCTION: 'Produção',
  STAGING: 'Staging',
  DEVELOPMENT: 'Desenvolvimento',
};
