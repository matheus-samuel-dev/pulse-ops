import { Box, Chip, MenuItem, Pagination, Stack, TextField, Typography } from '@mui/material';
import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { environmentLabels } from '../components/dashboard/dashboardFormatters';
import { eventsService, type RecordedEvent } from '../services/eventsService';
import { systemsService } from '../services/systemsService';
import { getApiErrorMessage } from '../services/api';
import type { MonitoredSystem } from '../types/api';
const severityLabels: Record<string, string> = { INFO: 'Informativo', SUCCESS: 'Sucesso', WARNING: 'Atenção', CRITICAL: 'Crítico', ERROR: 'Falha' };
export function AuditPage() {
  const [systems, setSystems] = useState<MonitoredSystem[]>([]);
  const [events, setEvents] = useState<RecordedEvent[]>([]);
  const [query, setQuery] = useState('');
  const [systemId, setSystemId] = useState('');
  const [severity, setSeverity] = useState('');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true); setError(null);
    try { const result = await eventsService.list({ page, query, systemId: systemId || undefined, severity: severity || undefined }, signal); if (!signal?.aborted) { setEvents(result.content); setPages(result.totalPages); } }
    catch (failure) { if (!signal?.aborted) setError(getApiErrorMessage(failure)); }
    finally { if (!signal?.aborted) setLoading(false); }
  }, [page, query, systemId, severity]);
  useEffect(() => { const controller = new AbortController(); const timer = window.setTimeout(() => void load(controller.signal), 250); return () => { clearTimeout(timer); controller.abort(); }; }, [load]);
  useEffect(() => { systemsService.list().then(setSystems).catch(failure => setError(getApiErrorMessage(failure))); }, []);
  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={3} maxWidth={1500} mx="auto">
    <PageHeader title="Trilha de auditoria" description="Histórico persistido das verificações, incidentes e ações realmente executadas" eyebrow="Operações" />
    <Panel sx={{ p: 2, mb: 2 }}><Stack direction={{ xs: 'column', md: 'row' }} gap={1.5}>
      <TextField size="small" label="Buscar eventos" value={query} onChange={event => { setQuery(event.target.value); setPage(0); }} sx={{ flex: 1 }} />
      <TextField select size="small" label="Sistema" value={systemId} onChange={event => { setSystemId(event.target.value); setPage(0); }} sx={{ minWidth: 180 }}><MenuItem value="">Todos</MenuItem>{systems.map(system => <MenuItem key={system.id} value={system.id}>{system.name}</MenuItem>)}</TextField>
      <TextField select size="small" label="Severidade" value={severity} onChange={event => { setSeverity(event.target.value); setPage(0); }} sx={{ minWidth: 160 }}><MenuItem value="">Todas</MenuItem>{Object.entries(severityLabels).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}</TextField>
    </Stack></Panel>
    {loading && <Typography role="status">Carregando eventos…</Typography>}
    {!loading && error && <ViewState kind="error" title="Não foi possível carregar os eventos" description={error} actionLabel="Tentar novamente" onAction={() => void load()} />}
    {!loading && !error && !events.length && <Panel><ViewState kind="empty" title="Nenhum evento registrado" description="Os eventos aparecerão após cadastrar sistemas, executar verificações ou realizar ações. Se houver filtros, tente ajustá-los." /></Panel>}
    {!loading && !error && <Stack spacing={1.5}>{events.map(event => <Panel key={event.id} sx={{ p: 2 }}>
      <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center"><Typography variant="h3">{event.title}</Typography><Chip size="small" variant="outlined" label={severityLabels[event.severity] ?? event.severity} color={event.severity === 'CRITICAL' || event.severity === 'ERROR' ? 'error' : event.severity === 'WARNING' ? 'warning' : 'default'} /></Stack>
      <Typography variant="body2" color="text.secondary" mt={1} sx={{ overflowWrap: 'anywhere' }}>{event.description}</Typography>
      <Typography variant="caption" color="text.secondary" display="block" mt={1}>{event.systemName} · {event.environment ? environmentLabels[event.environment] : 'Conta pessoal'} · {event.source} · {new Date(event.occurredAt).toLocaleString('pt-BR')}</Typography>
    </Panel>)}</Stack>}
    {pages > 1 && <Pagination count={pages} page={page + 1} onChange={(_, value) => setPage(value - 1)} aria-label="Páginas de eventos" sx={{ mt: 2 }} />}
  </Box>;
}
