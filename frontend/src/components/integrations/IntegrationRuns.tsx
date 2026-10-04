import { Alert, Button, Chip, MenuItem, Pagination, Stack, TextField, Typography } from '@mui/material';
import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { connectionsService, runLabels, type IntegrationRun } from '../../services/connectionsService';
import { systemsService } from '../../services/systemsService';
import { getApiErrorMessage } from '../../services/api';
import type { MonitoredSystem } from '../../types/api';
import { Panel } from '../common/Panel';
import { ViewState } from '../common/ViewState';

export function IntegrationRuns({ systemId, revision = 0 }: { systemId?: string; revision?: number }) {
  const { user } = useAuth();
  const canManage = user?.role === 'ADMIN' || user?.role === 'DEVELOPER';
  const [runs, setRuns] = useState<IntegrationRun[]>([]);
  const [systems, setSystems] = useState<MonitoredSystem[]>([]);
  const [target, setTarget] = useState(systemId ?? '');
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { const result = await connectionsService.runs(systemId, page); setRuns(result.content); setPages(result.totalPages); }
    catch (failure) { setError(getApiErrorMessage(failure)); } finally { setLoading(false); }
  }, [systemId, page]);
  useEffect(() => { void load(); }, [load, revision]);
  useEffect(() => { if (!systemId) systemsService.list().then(setSystems).catch(failure => setError(getApiErrorMessage(failure))); }, [systemId]);
  const request = async (slug: string) => {
    // Explicit confirmation is necessary before asking a remote auditing service to inspect a URL.
    if (slug === 'ai-web-auditor' && !window.confirm('Confirmo que tenho autorização para auditar este sistema. Solicitar auditoria ao AI Web Auditor?')) return;
    setBusy(true); setError(null);
    try { await connectionsService.request(slug, target, slug === 'ai-web-auditor'); await load(); }
    catch (failure) { setError(getApiErrorMessage(failure)); } finally { setBusy(false); }
  };
  const refresh = async (id: string) => {
    setBusy(true); setError(null);
    try { await connectionsService.refresh(id); await load(); }
    catch (failure) { setError(getApiErrorMessage(failure)); } finally { setBusy(false); }
  };
  return <Panel sx={{ p: { xs: 2, md: 2.5 }, mt: 3 }}>
    <Typography variant="h2">Execuções das integrações</Typography>
    <Typography variant="body2" color="text.secondary" mt={1}>O Pulse Ops acompanha as respostas dos serviços. O AI Web Auditor analisa; o Nexus Flow executa os workflows.</Typography>
    {canManage && <Stack direction={{ xs: 'column', md: 'row' }} gap={1} my={2}>
      {!systemId && <TextField select size="small" label="Sistema alvo" value={target} onChange={event => setTarget(event.target.value)} sx={{ minWidth: 190 }}>{systems.map(system => <MenuItem key={system.id} value={system.id}>{system.name}</MenuItem>)}</TextField>}
      <Button variant="outlined" disabled={busy || !target} onClick={() => void request('ai-web-auditor')}>Solicitar auditoria</Button>
      <Button variant="outlined" disabled={busy || !target} onClick={() => void request('nexus-flow')}>Acionar Nexus Flow</Button>
    </Stack>}
    {error && <Alert severity="error" action={<Button onClick={() => void load()} color="inherit">Atualizar</Button>}>{error}</Alert>}
    {loading && <Typography role="status" my={2}>Carregando execuções…</Typography>}
    {!loading && !runs.length && !error && <ViewState kind="empty" title="Nenhuma execução registrada" description="Configure uma integração e solicite uma execução para acompanhar o resultado real." />}
    <Stack spacing={2} mt={2}>{runs.map(run => <Stack key={run.id} spacing={1} sx={{ borderBottom: '1px solid', borderColor: 'divider', pb: 2 }}>
      <Stack direction="row" flexWrap="wrap" gap={1} alignItems="center"><Typography fontWeight={650}>{run.systemName} · {run.slug === 'ai-web-auditor' ? 'AI Web Auditor' : 'Nexus Flow'}</Typography><Chip size="small" label={runLabels[run.state] ?? run.state} color={run.state === 'FAILED' ? 'error' : run.state === 'COMPLETED' ? 'success' : 'default'} /></Stack>
      <Typography variant="body2" color="text.secondary">{run.message}</Typography>
      <Typography variant="caption" color="text.secondary">Solicitada em {new Date(run.createdAt).toLocaleString('pt-BR')} · Última comunicação: {new Date(run.updatedAt).toLocaleString('pt-BR')}{run.httpStatus ? ` · HTTP ${run.httpStatus}` : ''}{run.overallScore != null ? ` · Nota da auditoria: ${run.overallScore}/100` : ''}</Typography>
      <Stack direction="row" flexWrap="wrap" gap={1}>{canManage && run.slug === 'ai-web-auditor' && run.externalId && <Button size="small" disabled={busy} onClick={() => void refresh(run.id)}>Consultar resultado</Button>}{run.reportUrl && <Button size="small" component="a" href={run.reportUrl} target="_blank" rel="noopener noreferrer">Abrir relatório</Button>}</Stack>
    </Stack>)}</Stack>
    {pages > 1 && <Pagination count={pages} page={page + 1} onChange={(_, value) => setPage(value - 1)} aria-label="Páginas de execuções" sx={{ mt: 2 }} />}
  </Panel>;
}
