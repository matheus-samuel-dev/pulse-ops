import { zodResolver } from '@hookform/resolvers/zod';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
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
  Drawer,
  FormControl,
  FormHelperText,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Skeleton,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Controller, useForm } from 'react-hook-form';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { z } from 'zod';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { isDemoMode } from '../config/demo';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { formatRelativeTime } from '../components/dashboard/dashboardFormatters';
import { getApiErrorMessage } from '../services/api';
import { incidentsService } from '../services/incidentsService';
import { systemsService } from '../services/systemsService';
import type { Incident, IncidentInput, IncidentSeverity, IncidentStatus, MonitoredSystem } from '../types/api';

const severityColor: Record<IncidentSeverity, 'default' | 'warning' | 'error'> = { LOW: 'default', MEDIUM: 'warning', HIGH: 'error', CRITICAL: 'error' };
const severityLabel: Record<IncidentSeverity, string> = { LOW: 'Baixa', MEDIUM: 'Média', HIGH: 'Alta', CRITICAL: 'Crítica' };
const statusLabel: Record<IncidentStatus, string> = { OPEN: 'Aberto', INVESTIGATING: 'Investigando', RESOLVED: 'Resolvido' };

const incidentSchema = z.object({
  systemId: z.string().uuid('Selecione um sistema.'),
  title: z.string().trim().min(4, 'Use ao menos 4 caracteres.').max(180),
  description: z.string().trim().max(4000).optional(),
  severity: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
});
type IncidentForm = z.infer<typeof incidentSchema>;

export function IncidentsPage() {
  const { user } = useAuth();
  const [searchParams] = useSearchParams();
  const canManage = !isDemoMode && (user?.role === 'ADMIN' || user?.role === 'DEVELOPER');
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [systems, setSystems] = useState<MonitoredSystem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<IncidentStatus | 'ALL'>('ALL');
  const [severity, setSeverity] = useState<IncidentSeverity | 'ALL'>('ALL');
  const [systemId, setSystemId] = useState('ALL');
  const [period,setPeriod] = useState("24h");
  const [date, setDate] = useState('');
  const [selected, setSelected] = useState<Incident | null>(null);
  useEffect(() => { const id=searchParams.get("incidente");if(id) setSelected(incidents.find(item => item.id===id) ?? null); }, [searchParams,incidents]);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 60_000); return () => window.clearInterval(timer); }, []);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { const [incidentData, systemData] = await Promise.all([incidentsService.list(period === "all" ? {} : { period }), systemsService.list()]); setIncidents(incidentData); setSystems(systemData); }
    catch (requestError) { setError(getApiErrorMessage(requestError)); } finally { setLoading(false); }
  }, [period]);
  useEffect(() => { void load(); }, [load]);

  const filtered = useMemo(() => incidents.filter((incident) =>
    (status === 'ALL' || incident.status === status)
    && (severity === 'ALL' || incident.severity === severity)
    && (systemId === 'ALL' || incident.systemId === systemId)
    && (!date || incident.startedAt.slice(0, 10) === date)
    && `${incident.title} ${incident.description ?? ''} ${incident.systemName}`.toLowerCase().includes(query.toLowerCase())),
  [incidents, status, severity, systemId, date, query]);

  const transition = async (incident: Incident, action: 'investigate' | 'resolve') => {
    setBusy(true);
    try {
      const updated = action === 'investigate' ? await incidentsService.investigate(incident.id) : await incidentsService.resolve(incident.id);
      setIncidents((current) => current.map((item) => item.id === updated.id ? updated : item)); setSelected(updated);
      setNotice(action === 'resolve' ? 'Incidente resolvido.' : 'Investigação iniciada.');
    } catch (requestError) { setNotice(getApiErrorMessage(requestError)); } finally { setBusy(false); }
  };

  const create = async (input: IncidentInput) => {
    const created = await incidentsService.create(input);
    setIncidents((current) => [created, ...current]); setCreateOpen(false); setNotice('Incidente registrado.');
  };

  return (
    <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1680} mx="auto">
      <PageHeader title="Incidentes" description="Investigue, acompanhe e resolva eventos que afetam a confiabilidade" eyebrow="Operações"
        actions={canManage && <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setCreateOpen(true)}>Novo incidente</Button>} />
      <Panel sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', lg: 'row' }} gap={1.2}>
          <TextField size="small" placeholder="Buscar incidentes" value={query} onChange={(event) => setQuery(event.target.value)} sx={{ flex: 1 }} InputProps={{ startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> }} />
          <TextField size="small" select label="Estado" value={status} onChange={(event) => setStatus(event.target.value as IncidentStatus | 'ALL')} sx={{ minWidth: 150 }}><MenuItem value="ALL">Todos</MenuItem><MenuItem value="OPEN">Aberto</MenuItem><MenuItem value="INVESTIGATING">Investigando</MenuItem><MenuItem value="RESOLVED">Resolvido</MenuItem></TextField>
          <TextField size="small" select label="Severidade" value={severity} onChange={(event) => setSeverity(event.target.value as IncidentSeverity | 'ALL')} sx={{ minWidth: 150 }}><MenuItem value="ALL">Todas</MenuItem>{(['LOW','MEDIUM','HIGH','CRITICAL'] as IncidentSeverity[]).map((value) => <MenuItem key={value} value={value}>{severityLabel[value]}</MenuItem>)}</TextField>
          <TextField size="small" select label="Sistema" value={systemId} onChange={(event) => setSystemId(event.target.value)} sx={{ minWidth: 170 }}><MenuItem value="ALL">Todos</MenuItem>{systems.map((system) => <MenuItem key={system.id} value={system.id}>{system.name}</MenuItem>)}</TextField>
          <TextField select size="small" label="Período" value={period} onChange={event => setPeriod(event.target.value)} sx={{ minWidth: 150 }}><MenuItem value="24h">24 horas</MenuItem><MenuItem value="7d">7 dias</MenuItem><MenuItem value="30d">30 dias</MenuItem><MenuItem value="all">Todo histórico</MenuItem></TextField><TextField size="small" type="date" label="Data" InputLabelProps={{ shrink: true }} value={date} onChange={(event) => setDate(event.target.value)} />
        </Stack>
      </Panel>
      {loading && <Stack spacing={1.3}>{[1,2,3].map((item) => <Skeleton key={item} variant="rounded" height={108} />)}</Stack>}
      {!loading && error && <ViewState kind="error" title="Falha ao carregar incidentes" description={error} actionLabel="Tentar novamente" onAction={() => void load()} />}
      {!loading && !error && filtered.length === 0 && <Panel><ViewState kind="empty" title="Nenhum incidente encontrado" description="Não há incidentes registrados para os filtros selecionados. A ausência de incidentes não substitui as verificações dos sistemas." /></Panel>}
      {!loading && !error && <Stack spacing={1.2}>{filtered.map((incident) => (
        <Panel
          key={incident.id}
          role="button"
          tabIndex={0}
          aria-label={`Abrir detalhes do incidente ${incident.title}`}
          onClick={() => setSelected(incident)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              setSelected(incident);
            }
          }}
          sx={{ p: 2.1, cursor: 'pointer', '&:hover': { transform: 'translateX(2px)' } }}
        >
          <Stack direction={{ xs: 'column', md: 'row' }} alignItems={{ md: 'center' }} gap={1.6}>
            <Box width={8} alignSelf={{ xs: 'stretch', md: 'stretch' }} minHeight={{ xs: 4, md: 54 }} borderRadius={5} bgcolor={incident.severity === 'CRITICAL' ? 'error.main' : incident.severity === 'HIGH' ? '#ef7b67' : incident.severity === 'MEDIUM' ? 'warning.main' : 'text.disabled'} />
            <Box flex={1}><Stack direction="row" gap={.8} alignItems="center" flexWrap="wrap"><Typography variant="h3">{incident.title}</Typography>{incident.automatic && <Chip size="small" label="Automático" variant="outlined" />}</Stack><Typography variant="body2" color="text.secondary" mt={.55}>{incident.systemName} · {formatRelativeTime(incident.startedAt)}</Typography></Box>
            <Stack direction="row" gap={.8}><Chip size="small" label={severityLabel[incident.severity]} color={severityColor[incident.severity]} variant="outlined" /><Chip size="small" label={statusLabel[incident.status]} color={incident.status === 'RESOLVED' ? 'success' : 'warning'} /></Stack>
          </Stack>
        </Panel>
      ))}</Stack>}

      <Drawer anchor="right" open={Boolean(selected)} onClose={() => setSelected(null)} PaperProps={{ sx: { width: { xs: '100%', sm: 480 }, p: 3 } }}>
        {selected && <><Stack direction="row" justifyContent="space-between" alignItems="flex-start"><Box><Typography variant="caption" color="error.main" fontWeight={750}>{severityLabel[selected.severity].toUpperCase()}</Typography><Typography variant="h2" mt={.5}>{selected.title}</Typography><Typography color="text.secondary" variant="body2" mt={.6}>{selected.systemName}</Typography><Typography color="text.secondary" variant="caption" display="block" mt={.5}>Origem: {selected.automatic ? 'Monitoramento Pulse Ops' : 'Equipe de Operações'}</Typography></Box><IconButton aria-label="Fechar detalhes" onClick={() => setSelected(null)}><CloseRoundedIcon /></IconButton></Stack><Divider sx={{ my: 3 }}/><Typography variant="body2" color="text.secondary" lineHeight={1.75}>{selected.description || 'Sem descrição adicional.'}</Typography><Typography variant="h3" mt={4} mb={2}>Linha do tempo</Typography><TimelinePoint title="Incidente detectado" time={selected.startedAt} active /><TimelinePoint title="Registro criado" time={selected.createdAt} />{selected.investigatingAt && <TimelinePoint title="Investigação iniciada" time={selected.investigatingAt} active />}{selected.resolvedAt && <TimelinePoint title="Operação normalizada" time={selected.resolvedAt} active />}
          <Typography variant="body2" mt={2}>Duração: {Math.max(0, Math.floor(((selected.resolvedAt ? new Date(selected.resolvedAt).getTime() : now) - new Date(selected.startedAt).getTime()) / 60000))} minutos{!selected.resolvedAt && " · em andamento"}</Typography>
          {canManage && <Button sx={{ mt: 2 }} onClick={() => { setEditing(true); setEditError(null); }}>Editar contexto</Button>}
          {canManage && selected.status !== 'RESOLVED' && <Stack direction="row" gap={1} mt={4}>{selected.status === 'OPEN' && <Button variant="outlined" disabled={busy} onClick={() => void transition(selected, 'investigate')}>Investigar</Button>}<Button variant="contained" color="success" disabled={busy} onClick={() => void transition(selected, 'resolve')}>Resolver incidente</Button></Stack>}</>}
      </Drawer>
      {selected && <Dialog open={editing} onClose={() => { if (!busy) { setEditing(false); setSelected(incidents.find(item => item.id === selected.id) ?? null); } }} fullWidth maxWidth="sm"><DialogTitle>Editar incidente</DialogTitle><DialogContent dividers>{editError && <Alert severity="error">{editError}</Alert>}<Stack component="form" id="edit-incident" spacing={2} pt={1} onSubmit={async event => { event.preventDefault(); setBusy(true); setEditError(null); try { const updated = await incidentsService.update(selected.id, { title: selected.title, description: selected.description, severity: selected.severity }); setIncidents(current => current.map(item => item.id === updated.id ? updated : item)); setSelected(updated); setEditing(false); } catch (failure) { setEditError(getApiErrorMessage(failure)); } finally { setBusy(false); } }}><TextField label="Título" required inputProps={{ minLength: 4, maxLength: 180 }} value={selected.title} onChange={event => setSelected({ ...selected, title: event.target.value })} /><TextField label="Descrição" multiline minRows={3} inputProps={{ maxLength: 4000 }} value={selected.description ?? ""} onChange={event => setSelected({ ...selected, description: event.target.value })} /><TextField select label="Severidade" value={selected.severity} onChange={event => setSelected({ ...selected, severity: event.target.value as IncidentSeverity })}>{Object.entries(severityLabel).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}</TextField></Stack></DialogContent><DialogActions><Button disabled={busy} onClick={() => { setEditing(false); setSelected(incidents.find(item => item.id === selected.id) ?? null); }}>Cancelar</Button><Button type="submit" form="edit-incident" variant="contained" disabled={busy}>Salvar contexto</Button></DialogActions></Dialog>}
      <IncidentCreateDialog open={createOpen} systems={systems} onClose={() => setCreateOpen(false)} onCreate={create} onError={(message) => setNotice(message)} />
      <Snackbar open={Boolean(notice)} autoHideDuration={4200} onClose={() => setNotice(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}><Alert variant="filled" severity={notice?.includes('registrado') || notice?.includes('resolvido') || notice?.includes('iniciada') ? 'success' : 'error'} onClose={() => setNotice(null)}>{notice}</Alert></Snackbar>
    </Box>
  );
}

function TimelinePoint({ title, time, active }: { title: string; time: string; active?: boolean }) {
  return <Stack direction="row" spacing={1.4} pb={2.5}><Box display="flex" flexDirection="column" alignItems="center"><Box width={10} height={10} borderRadius="50%" bgcolor={active ? 'primary.main' : 'text.disabled'} boxShadow={active ? '0 0 0 5px rgba(91,140,255,.12)' : 'none'} /><Box flex={1} width="1px" bgcolor="divider" mt={.8} /></Box><Box><Typography variant="body2" fontWeight={650}>{title}</Typography><Typography variant="caption" color="text.secondary">{new Date(time).toLocaleString('pt-BR')}</Typography></Box></Stack>;
}

function IncidentCreateDialog({ open, systems, onClose, onCreate, onError }: { open: boolean; systems: MonitoredSystem[]; onClose: () => void; onCreate: (input: IncidentInput) => Promise<void>; onError: (message: string) => void }) {
  const { register, control, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<IncidentForm>({ resolver: zodResolver(incidentSchema), defaultValues: { systemId: '', title: '', description: '', severity: 'MEDIUM' } });
  const submit = async (values: IncidentForm) => { try { await onCreate(values); reset(); } catch (error) { onError(getApiErrorMessage(error)); } };
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>Novo incidente</DialogTitle><DialogContent dividers><Stack component="form" id="incident-form" onSubmit={handleSubmit(submit)} spacing={2} pt={.5}><Controller name="systemId" control={control} render={({ field }) => <FormControl error={Boolean(errors.systemId)}><InputLabel>Sistema</InputLabel><Select {...field} label="Sistema">{systems.map((system) => <MenuItem key={system.id} value={system.id}>{system.name}</MenuItem>)}</Select>{errors.systemId && <FormHelperText>{errors.systemId.message}</FormHelperText>}</FormControl>} /><TextField label="Título" {...register('title')} error={Boolean(errors.title)} helperText={errors.title?.message} /><TextField label="Descrição" {...register('description')} multiline minRows={3} error={Boolean(errors.description)} helperText={errors.description?.message} /><Controller name="severity" control={control} render={({ field }) => <FormControl><InputLabel>Severidade</InputLabel><Select {...field} label="Severidade">{(['LOW','MEDIUM','HIGH','CRITICAL'] as IncidentSeverity[]).map((value) => <MenuItem key={value} value={value}>{severityLabel[value]}</MenuItem>)}</Select></FormControl>} /></Stack></DialogContent><DialogActions><Button onClick={onClose}>Cancelar</Button><Button type="submit" form="incident-form" variant="contained" disabled={isSubmitting}>Registrar</Button></DialogActions></Dialog>;
}
