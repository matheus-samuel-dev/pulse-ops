import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import {
  alpha,
  Box,
  Chip,
  InputAdornment,
  MenuItem,
  Skeleton,
  Stack,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { environmentLabels } from '../components/dashboard/dashboardFormatters';
import { getApiErrorMessage } from '../services/api';
import { reportsService } from '../services/reportsService';
import type { OperationalEvent, OperationalReport } from '../types/api';

const typeLabels = { HEALTH_CHECK: 'Health check', INCIDENT: 'Incidente', DEPLOYMENT: 'Deploy', QUALITY: 'Qualidade' } as const;
const impactLabels = { INFO: 'Informativo', SUCCESS: 'Sucesso', WARNING: 'Atenção', CRITICAL: 'Crítico' } as const;

export function AuditPage() {
  const theme = useTheme();
  const [report, setReport] = useState<OperationalReport | null>(null);
  const [query, setQuery] = useState('');
  const [systemId, setSystemId] = useState('ALL');
  const [impact, setImpact] = useState<OperationalEvent['impact'] | 'ALL'>('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { setReport(await reportsService.operational('30d')); }
    catch (requestError) { setError(getApiErrorMessage(requestError)); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const systems = useMemo(() => {
    const unique = new Map(report?.feed.map((event) => [event.systemId, event.systemName]) ?? []);
    return [...unique.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  }, [report]);
  const events = useMemo(() => report?.feed.filter((event) =>
    (systemId === 'ALL' || event.systemId === systemId)
    && (impact === 'ALL' || event.impact === impact)
    && `${event.title} ${event.description ?? ''} ${event.systemName} ${event.source}`.toLowerCase().includes(query.toLowerCase())) ?? [],
  [report, systemId, impact, query]);

  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1500} mx="auto">
    <PageHeader title="Auditoria" description="Trilha correlacionada e não sensível das operações do workspace" eyebrow="Governança" />
    <Panel sx={{ p: 2, mb: 2 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} gap={1.2}>
        <TextField size="small" placeholder="Buscar por ação, sistema ou origem" value={query} onChange={(event) => setQuery(event.target.value)} sx={{ flex: 1 }} InputProps={{ startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> }} />
        <TextField select size="small" label="Sistema" value={systemId} onChange={(event) => setSystemId(event.target.value)} sx={{ minWidth: 190 }}><MenuItem value="ALL">Todos os sistemas</MenuItem>{systems.map(([id, name]) => <MenuItem key={id} value={id}>{name}</MenuItem>)}</TextField>
        <TextField select size="small" label="Resultado" value={impact} onChange={(event) => setImpact(event.target.value as OperationalEvent['impact'] | 'ALL')} sx={{ minWidth: 160 }}><MenuItem value="ALL">Todos</MenuItem>{Object.entries(impactLabels).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}</TextField>
      </Stack>
    </Panel>
    {loading && <Stack spacing={1.2}>{[1,2,3,4,5].map((item) => <Skeleton key={item} variant="rounded" height={105} />)}</Stack>}
    {!loading && error && <ViewState kind="error" title="Falha ao carregar a auditoria" description={error} actionLabel="Tentar novamente" onAction={() => void load()} />}
    {!loading && !error && events.length === 0 && <Panel><ViewState kind="empty" title="Nenhum evento encontrado" description="Ajuste os filtros para consultar outra parte da trilha operacional." /></Panel>}
    {!loading && !error && <Stack spacing={1.1}>{events.map((event) => {
      const color = event.impact === 'SUCCESS' ? theme.palette.success.main : event.impact === 'CRITICAL' ? theme.palette.error.main : event.impact === 'WARNING' ? theme.palette.warning.main : theme.palette.info.main;
      return <Panel key={`${event.type}-${event.id}`} sx={{ p: { xs: 2, md: 2.2 } }}>
        <Stack direction={{ xs: 'column', md: 'row' }} gap={1.5} alignItems={{ md: 'center' }}>
          <Box width={38} height={38} flex="0 0 38px" display="grid" sx={{ placeItems: 'center', borderRadius: 2, color, bgcolor: alpha(color, .1) }}><ShieldOutlinedIcon fontSize="small" /></Box>
          <Box flex={1} minWidth={0}><Stack direction="row" gap={.7} alignItems="center" flexWrap="wrap"><Typography variant="h3">{event.title}</Typography><Chip size="small" label={event.status} variant="outlined" sx={{ color, borderColor: alpha(color, .35) }} /></Stack><Typography variant="body2" color="text.secondary" mt={.45}>{event.description || 'Evento operacional registrado sem detalhes adicionais.'}</Typography></Box>
          <Box minWidth={{ md: 245 }}><Typography variant="body2" fontWeight={650}>{event.systemName} · {typeLabels[event.type]}</Typography><Typography variant="caption" color="text.secondary" display="block">{environmentLabels[event.environment]} · {event.source}</Typography><Typography variant="caption" color="text.secondary" display="block">{new Date(event.occurredAt).toLocaleString('pt-BR')}</Typography></Box>
        </Stack>
      </Panel>;
    })}</Stack>}
  </Box>;
}
