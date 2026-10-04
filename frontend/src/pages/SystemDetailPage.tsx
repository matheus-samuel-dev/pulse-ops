import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import AutorenewRoundedIcon from '@mui/icons-material/AutorenewRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import SpeedRoundedIcon from '@mui/icons-material/SpeedRounded';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  LinearProgress,
  Pagination,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Skeleton,
  Snackbar,
  Stack,
  Tab,
  Tabs,
  Typography,
  useTheme,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useAuth } from '../auth/AuthContext';
import { isDemoMode } from '../config/demo';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { environmentLabels, formatLatency, formatPercent, formatRelativeTime } from '../components/dashboard/dashboardFormatters';
import { SystemStatusChip } from '../components/dashboard/SystemStatusChip';
import { SystemFormDialog } from '../components/systems/SystemFormDialog';
import { getApiErrorMessage } from '../services/api';
import { deploymentsService } from '../services/deploymentsService';
import { incidentsService } from '../services/incidentsService';
import { IntegrationRuns } from '../components/integrations/IntegrationRuns';
import { systemsService } from '../services/systemsService';
import type { Deployment, HealthCheck, Incident, MonitoredSystem, MonitoredSystemInput, SystemMetrics, LatencyPoint } from '../types/api';

interface DetailData {
  system: MonitoredSystem;
  metrics: SystemMetrics;
  checks: HealthCheck[];
  incidents: Incident[];
  deployments: Deployment[];
  checkPages: number;
  latency: LatencyPoint[];
}

export function SystemDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const theme = useTheme();
  const { user } = useAuth();
  const [checkPage, setCheckPage] = useState(0);
  const [period, setPeriod] = useState('24h');
  const [data, setData] = useState<DetailData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true); setError(null);
    try {
      const [system, metrics, checks, incidents, deployments, latency] = await Promise.all([
        systemsService.get(id), systemsService.metrics(id, period), systemsService.checks(id, checkPage, 20, period),
        incidentsService.list({ systemId: id }), deploymentsService.list(id), systemsService.latency(id, period),
      ]);

      setData({ system, metrics, checks: checks.content, incidents, deployments, checkPages: checks.totalPages, latency });
    } catch (requestError) { setError(getApiErrorMessage(requestError)); }
    finally { setLoading(false); }
  }, [id, period, checkPage]);

  useEffect(() => { void load(); }, [load]);

  const chartData = useMemo(() => (data?.latency ?? []).map((point) => ({
    timestamp: new Date(point.timestamp).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
    latency: point.averageMs,
  })), [data]);

  const runCheck = async () => {
    setChecking(true);
    try { const result = await systemsService.check(id); setNotice(result.success ? 'Verificação concluída com sucesso.' : 'Verificação registrou uma falha.'); await load(); }
    catch (requestError) { setNotice(getApiErrorMessage(requestError)); } finally { setChecking(false); }
  };

  const update = async (input: MonitoredSystemInput) => {
    try { await systemsService.update(id, input); setEditOpen(false); setNotice('Configuração atualizada.'); await load(); }
    catch (requestError) { setNotice(getApiErrorMessage(requestError)); throw requestError; }
  };

  const remove = async () => {
    try { await systemsService.remove(id); navigate('/sistemas', { replace: true }); }
    catch (requestError) { setDeleteOpen(false); setNotice(getApiErrorMessage(requestError)); }
  };

  if (loading) return <Box p={{ xs: 2, md: 4 }}><Skeleton width={260} height={44} /><Skeleton variant="rounded" height={170} sx={{ mt: 2 }} /><Skeleton variant="rounded" height={360} sx={{ mt: 2 }} /></Box>;
  if (error || !data) return <Box p={4}><ViewState kind="error" title="Sistema não encontrado" description={error ?? 'Não foi possível abrir o sistema.'} actionLabel="Voltar" onAction={() => navigate('/sistemas')} /></Box>;

  const { system, metrics } = data;
  const initial: MonitoredSystemInput = {
    name: system.name, description: system.description, baseUrl: system.baseUrl, healthEndpoint: system.healthEndpoint,
    environment: system.environment, active: system.active, expectedStatusCode: system.expectedStatusCode,
    timeoutMs: system.timeoutMs, latencyThresholdMs: system.latencyThresholdMs, targetAvailability: system.targetAvailability, monitoringIntervalSeconds: system.monitoringIntervalSeconds, maintenance: system.maintenance,
  };

  return (
    <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1680} mx="auto">
      <Button startIcon={<ArrowBackRoundedIcon />} onClick={() => navigate('/sistemas')} color="inherit" size="small">Sistemas</Button>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" gap={2} mt={2} mb={3}>
        <Box>
          <Stack direction="row" alignItems="center" gap={1.2} flexWrap="wrap"><Typography variant="h1">{system.name}</Typography><SystemStatusChip status={system.status} active={system.active} /></Stack>
          <Typography color="text.secondary" mt={0.8}>{system.description}</Typography>
          <Stack direction="row" gap={0.8} mt={1.4} flexWrap="wrap"><Chip size="small" label={environmentLabels[system.environment]} variant="outlined" /><Chip size="small" label={system.baseUrl} variant="outlined" /></Stack>
        </Box>
        <Stack direction="row" gap={1} alignItems="flex-start" flexWrap="wrap">
          {!isDemoMode && (user?.role === 'ADMIN' || user?.role === 'DEVELOPER') && <Button variant="contained" startIcon={<AutorenewRoundedIcon />} disabled={checking || !data.system.active || data.system.maintenance} onClick={() => void runCheck()}>Verificar agora</Button>}
          {!isDemoMode && (user?.role === 'ADMIN' || user?.role === 'DEVELOPER') && <Button variant="outlined" startIcon={<EditRoundedIcon />} onClick={() => setEditOpen(true)}>Editar</Button>}
          {!isDemoMode && (user?.role === 'ADMIN' || user?.role === 'DEVELOPER') && <Button color="error" variant="outlined" startIcon={<DeleteOutlineRoundedIcon />} onClick={() => setDeleteOpen(true)}>Excluir</Button>}
        </Stack>
      </Stack>

      <Alert severity={!system.active ? "info" : system.status === "DOWN" ? "error" : system.status === "DEGRADED" ? "warning" : "info"} sx={{ mb: 2 }}>{!system.active ? "Monitoramento pausado. Ative o sistema em Editar para executar verificações." : system.statusReason ? system.statusReason : !system.lastCheck ? "Aguardando a primeira verificação." : `Última verificação: ${new Date(system.lastCheck.checkedAt).toLocaleString("pt-BR")} · ${system.lastCheck.httpStatus != null ? `HTTP ${system.lastCheck.httpStatus}` : "Não foi possível alcançar o endpoint"}${system.lastCheck.success && system.status === "DEGRADED" ? " · Verifique o limite de latência e o histórico de falhas recentes." : ""}`}</Alert>
      <Typography variant="caption" color="text.secondary">Última verificação: {system.lastCheck ? new Date(system.lastCheck.checkedAt).toLocaleString("pt-BR") : "Nenhuma executada"} · Intervalo: {system.monitoringIntervalSeconds} segundos</Typography>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Tabs value={period} onChange={(_, value) => { setCheckPage(0); setPeriod(value); }} aria-label="Período das métricas"><Tab value="24h" label="24 horas" /><Tab value="7d" label="7 dias" /><Tab value="30d" label="30 dias" /></Tabs>
      </Stack>
      <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2,1fr)', xl: 'repeat(4,1fr)' }} gap={2}>
        <Metric label="Disponibilidade" value={formatPercent(metrics.availability.availabilityPercentage)} note={metrics.availability.eligibleChecks < 5 ? `${metrics.availability.eligibleChecks} de 5 verificações válidas necessárias` : `${metrics.availability.successfulChecks}/${metrics.availability.totalChecks} verificações`} color={theme.palette.success.main} />
        <Metric label="Meta de disponibilidade" value={formatPercent(metrics.sla.targetAvailability)} note={metrics.sla.targetMet ? 'Meta cumprida' : metrics.sla.status === 'NO_DATA' ? 'Sem dados' : 'Abaixo da meta'} color={metrics.sla.targetMet ? theme.palette.success.main : theme.palette.warning.main} />
        <Metric label="Latência média" value={formatLatency(metrics.latency.averageMs)} note={`p95 ${formatLatency(metrics.latency.p95Ms ?? null)}`} color={theme.palette.primary.main} />
        <Metric label="Falhas no período" value={String(metrics.availability.failedChecks)} note={`${metrics.availability.totalChecks} verificações realizadas`} color={theme.palette.error.main} />
      </Box>

      <Box display="grid" gridTemplateColumns={{ xs: '1fr', xl: 'minmax(0,2fr) minmax(320px,.8fr)' }} gap={2} mt={2}>
        <Panel sx={{ p: 2.5 }}>
          <Stack direction="row" justifyContent="space-between"><Box><Typography variant="h2">Histórico de latência</Typography><Typography variant="body2" color="text.secondary" mt={0.4}>Médias calculadas pelo backend na janela selecionada</Typography></Box><SpeedRoundedIcon color="primary" /></Stack>
          <Box height={300} mt={2}>{data.latency.some(point => point.samples > 0) ? <ResponsiveContainer width="100%" height="100%"><AreaChart data={chartData}><defs><linearGradient id="detailLatency" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={theme.palette.primary.main} stopOpacity={.35}/><stop offset="1" stopColor={theme.palette.primary.main} stopOpacity={0}/></linearGradient></defs><CartesianGrid stroke={theme.palette.divider} vertical={false} strokeDasharray="4 5"/><XAxis dataKey="timestamp" tick={{ fill: theme.palette.text.secondary, fontSize: 11 }} axisLine={false} tickLine={false}/><YAxis tick={{ fill: theme.palette.text.secondary, fontSize: 11 }} axisLine={false} tickLine={false}/><Tooltip/><Area type="monotone" dataKey="latency" stroke={theme.palette.primary.main} fill="url(#detailLatency)" strokeWidth={2}/></AreaChart></ResponsiveContainer> : <ViewState kind="empty" title="Nenhuma verificação executada" description="Execute a primeira verificação para começar o histórico." />}</Box>
        </Panel>
        <Panel sx={{ p: 2.5 }}>
          <Typography variant="h2">Meta de disponibilidade</Typography><Typography color="text.secondary" variant="body2" mt={0.4}>Cumprimento no período</Typography>
          <Typography fontSize="2.6rem" fontWeight={760} mt={3}>{formatPercent(metrics.sla.currentAvailability)}</Typography>
          {metrics.sla.currentAvailability != null && <LinearProgress variant="determinate" value={metrics.sla.currentAvailability == null ? 0 : Math.min(100, metrics.sla.currentAvailability)} sx={{ height: 8, borderRadius: 4, mt: 2 }} color={metrics.sla.targetMet ? 'success' : 'warning'} />}
          <Stack spacing={1.2} mt={3}><InfoRow label="Meta" value={formatPercent(metrics.sla.targetAvailability)} /><InfoRow label="Diferença" value={`${(metrics.sla.differencePercentagePoints ?? 0) > 0 ? '+' : ''}${formatPercent(metrics.sla.differencePercentagePoints)}`} /><InfoRow label="Amostras" value={String(metrics.sla.evaluatedChecks)} /><InfoRow label="Última falha" value={system.lastFailureAt ? new Date(system.lastFailureAt).toLocaleString("pt-BR") : "Nenhuma registrada"} /></Stack>
        </Panel>
      </Box>

      <Box display="grid" gridTemplateColumns={{ xs: '1fr', lg: 'repeat(2,1fr)' }} gap={2} mt={2}>
        <Panel sx={{ p: 2.5 }}><Typography variant="h2">Últimos incidentes</Typography><Divider sx={{ my: 2 }}/>{data.incidents.length ? data.incidents.slice(0,4).map((incident) => <Stack key={incident.id} direction="row" justifyContent="space-between" py={1}><Box><Typography variant="body2" fontWeight={650}>{incident.title}</Typography><Typography variant="caption" color="text.secondary">{formatRelativeTime(incident.startedAt)}</Typography></Box><Chip size="small" label={{ OPEN: "Aberto", INVESTIGATING: "Investigando", RESOLVED: "Resolvido" }[incident.status]} color={incident.status === 'RESOLVED' ? 'success' : 'warning'} variant="outlined" /></Stack>) : <Typography color="text.secondary">Nenhum incidente registrado.</Typography>}</Panel>
        <Panel sx={{ p: 2.5 }}><Typography variant="h2">Últimas implantações</Typography><Divider sx={{ my: 2 }}/>{data.deployments.length ? data.deployments.slice(0,4).map((deployment) => <Stack key={deployment.id} direction="row" justifyContent="space-between" py={1}><Box><Typography variant="body2" fontWeight={650}>v{deployment.version}</Typography><Typography variant="caption" color="text.secondary">{deployment.commitHash?.slice(0,7) ?? 'sem commit'} · {formatRelativeTime(deployment.deployedAt)}</Typography></Box><Chip size="small" label={{ PENDING: "Aguardando", RUNNING: "Em execução", SUCCESS: "Sucesso", FAILED: "Falhou", ROLLED_BACK: "Revertida" }[deployment.status]} color={deployment.status === 'SUCCESS' ? 'success' : deployment.status === 'FAILED' ? 'error' : 'default'} variant="outlined" /></Stack>) : <Typography color="text.secondary">Nenhuma implantação registrada.</Typography>}</Panel>
      </Box>

      <Panel sx={{ p: { xs: 2, md: 2.5 }, mt: 2 }}><Typography variant="h2">Histórico de verificações</Typography><Typography color="text.secondary" variant="body2" mt={1}>Resultados reais, ordenados pela data de execução. Disponibilidade é a proporção de verificações bem-sucedidas; não é uma medida contínua do tempo online.</Typography>
        {!data.checks.length ? <ViewState kind="empty" title="Nenhuma verificação executada" description="Verifique o sistema para começar a registrar resultados." /> : <TableContainer sx={{ mt: 2 }}><Table size="small" aria-label="Histórico de verificações"><TableHead><TableRow><TableCell>Data e hora</TableCell><TableCell>HTTP</TableCell><TableCell>Resposta</TableCell><TableCell>Resultado</TableCell></TableRow></TableHead><TableBody>{data.checks.map(check => <TableRow key={check.id}><TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(check.checkedAt).toLocaleString('pt-BR')}</TableCell><TableCell>{check.httpStatus ?? '—'}</TableCell><TableCell>{formatLatency(check.httpStatus == null ? null : check.responseTimeMs)}</TableCell><TableCell><Typography variant="body2">{check.success ? 'Disponível' : check.httpStatus != null ? 'Resposta diferente da esperada' : 'Não foi possível conectar'}</Typography>{check.errorMessage && <details><summary>Detalhes técnicos</summary><Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>{check.errorMessage}</Typography></details>}</TableCell></TableRow>)}</TableBody></Table></TableContainer>}
        {data.checkPages > 1 && <Pagination count={data.checkPages} page={checkPage + 1} onChange={(_, page) => setCheckPage(page - 1)} aria-label="Páginas de verificações" sx={{ mt: 2 }} />}
      </Panel>
      <IntegrationRuns systemId={id} />
      <SystemFormDialog open={editOpen} onClose={() => setEditOpen(false)} onSubmit={update} initial={initial} title="Editar sistema" submitLabel="Salvar alterações" />
      <Dialog open={deleteOpen} onClose={() => setDeleteOpen(false)}><DialogTitle>Excluir {system.name}?</DialogTitle><DialogContent><Typography color="text.secondary">Esta ação remove o sistema e seu histórico associado. Use somente quando o monitoramento não for mais necessário.</Typography></DialogContent><DialogActions><Button onClick={() => setDeleteOpen(false)}>Cancelar</Button><Button color="error" variant="contained" onClick={() => void remove()}>Excluir sistema</Button></DialogActions></Dialog>
      <Snackbar open={Boolean(notice)} autoHideDuration={4200} onClose={() => setNotice(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}><Alert onClose={() => setNotice(null)} severity={notice?.includes('sucesso') || notice?.includes('atualizada') ? 'success' : 'info'} variant="filled">{notice}</Alert></Snackbar>
    </Box>
  );
}

function Metric({ label, value, note, color }: { label: string; value: string; note: string; color: string }) {
  return <Panel sx={{ p: 2.3, borderTop: `2px solid ${color}` }}><Typography variant="body2" color="text.secondary">{label}</Typography><Typography fontSize="1.8rem" fontWeight={740} mt={1}>{value}</Typography><Typography variant="caption" color="text.secondary">{note}</Typography></Panel>;
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return <Stack direction="row" justifyContent="space-between"><Typography variant="body2" color="text.secondary">{label}</Typography><Typography variant="body2" fontWeight={700}>{value}</Typography></Stack>;
}

