import AddRoundedIcon from '@mui/icons-material/AddRounded';
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import {
  Alert,
  alpha,
  Box,
  Button,
  Chip,
  InputAdornment,
  MenuItem,
  Skeleton,
  Snackbar,
  Stack,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { environmentLabels, formatLatency, formatPercent, formatRelativeTime } from '../components/dashboard/dashboardFormatters';
import { SystemStatusChip } from '../components/dashboard/SystemStatusChip';
import { SystemFormDialog } from '../components/systems/SystemFormDialog';
import { getApiErrorMessage } from '../services/api';
import { systemsService } from '../services/systemsService';
import { getDashboard } from '../services/dashboardService';
import { qualityService } from '../services/qualityService';
import { isDemoMode } from '../config/demo';
import type { Environment, MonitoredSystem, MonitoredSystemInput, QualityReport, SystemHealth, SystemStatus } from '../types/api';

export function SystemsPage() {
  const theme = useTheme();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [systems, setSystems] = useState<MonitoredSystem[]>([]);
  const [health, setHealth] = useState<Map<string, SystemHealth>>(new Map());
  const [quality, setQuality] = useState<Map<string, QualityReport>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [environment, setEnvironment] = useState<Environment | 'ALL'>('ALL');
  const [status, setStatus] = useState<SystemStatus | 'ALL'>('ALL');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const [systemData, dashboard, qualityData] = await Promise.all([
        systemsService.list(), getDashboard({ period: '24h', environment: 'ALL' }), qualityService.overview(),
      ]);
      setSystems(systemData);
      setHealth(new Map(dashboard.health.map((item) => [item.id, item])));
      setQuality(new Map(qualityData.systems.map((item) => [item.systemId, item])));
    } catch (requestError) { setError(getApiErrorMessage(requestError)); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const filtered = useMemo(() => systems.filter((system) =>
    (environment === 'ALL' || system.environment === environment)
    && (status === 'ALL' || system.status === status)
    && `${system.name} ${system.description ?? ''} ${system.baseUrl}`.toLowerCase().includes(query.toLowerCase())),
  [systems, query, environment, status]);

  const create = async (input: MonitoredSystemInput) => {
    try {
      const created = await systemsService.create(input);
      setSystems((current) => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      setDialogOpen(false); setNotice('Sistema cadastrado e pronto para monitoramento.');
    } catch (requestError) { setNotice(getApiErrorMessage(requestError)); throw requestError; }
  };

  return (
    <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1680} mx="auto">
      <PageHeader title="Sistemas" description="Aplicações, APIs e serviços acompanhados pelo PulseOps" eyebrow="Observabilidade"
        actions={user?.role === 'ADMIN' && !isDemoMode && <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setDialogOpen(true)}>Cadastrar sistema</Button>} />
      <Panel sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.25}>
          <TextField size="small" placeholder="Buscar por nome, descrição ou URL" value={query} onChange={(event) => setQuery(event.target.value)} sx={{ flex: 1 }}
            InputProps={{ startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> }} />
          <TextField select size="small" label="Ambiente" value={environment} onChange={(event) => setEnvironment(event.target.value as Environment | 'ALL')} sx={{ minWidth: 170 }}>
            <MenuItem value="ALL">Todos</MenuItem><MenuItem value="PRODUCTION">Produção</MenuItem><MenuItem value="STAGING">Staging</MenuItem><MenuItem value="DEVELOPMENT">Desenvolvimento</MenuItem>
          </TextField>
          <TextField select size="small" label="Status" value={status} onChange={(event) => setStatus(event.target.value as SystemStatus | 'ALL')} sx={{ minWidth: 160 }}>
            <MenuItem value="ALL">Todos</MenuItem><MenuItem value="OPERATIONAL">Operacional</MenuItem><MenuItem value="DEGRADED">Atenção</MenuItem><MenuItem value="DOWN">Indisponível</MenuItem><MenuItem value="UNKNOWN">Sem dados</MenuItem>
          </TextField>
        </Stack>
      </Panel>

      {loading && <Box display="grid" gridTemplateColumns={{ xs: '1fr', md: 'repeat(2,1fr)', xl: 'repeat(3,1fr)' }} gap={2}>{[1,2,3,4].map((item) => <Skeleton key={item} variant="rounded" height={225} />)}</Box>}
      {!loading && error && <ViewState kind="error" title="Falha ao carregar sistemas" description={error} actionLabel="Tentar novamente" onAction={() => void load()} />}
      {!loading && !error && filtered.length === 0 && <ViewState kind="empty" title="Nenhum sistema encontrado" description="Ajuste os filtros ou cadastre a primeira aplicação monitorada." />}
      {!loading && !error && filtered.length > 0 && (
        <Box display="grid" gridTemplateColumns={{ xs: '1fr', md: 'repeat(2,1fr)', xl: 'repeat(3,1fr)' }} gap={2}>
          {filtered.map((system) => (
            <Panel key={system.id} onClick={() => navigate(`/sistemas/${system.id}`)} tabIndex={0} role="link"
              aria-label={`Abrir detalhes do sistema ${system.name}`}
              onKeyDown={(event) => { if (event.key === 'Enter') navigate(`/sistemas/${system.id}`); }}
              sx={{ p: 2.4, cursor: 'pointer', '&:hover': { transform: 'translateY(-2px)', boxShadow: '0 16px 34px rgba(0,0,0,.16)' } }}>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                <Box width={42} height={42} display="grid" sx={{ placeItems: 'center', borderRadius: 2.5, bgcolor: alpha(theme.palette.primary.main,.1), color: 'primary.light', fontWeight: 800 }}>{system.name.slice(0,2).toUpperCase()}</Box>
                <SystemStatusChip status={system.status} active={system.active} />
              </Stack>
              <Typography variant="h2" mt={2}>{system.name}</Typography>
              <Typography color="text.secondary" variant="body2" mt={0.7} minHeight={42}>{system.description || 'Sem descrição cadastrada.'}</Typography>
              <Stack direction="row" gap={0.75} mt={2} flexWrap="wrap">
                <Chip size="small" variant="outlined" label={environmentLabels[system.environment]} />
                <Chip size="small" variant="outlined" label={`SLA ${system.targetAvailability}%`} />
                {!system.active && <Chip size="small" color="default" label="Pausado" />}
              </Stack>
              <Box display="grid" gridTemplateColumns="repeat(2,minmax(0,1fr))" gap={1.2} mt={2}>
                <SystemMetric label="Uptime · 24h" value={formatPercent(health.get(system.id)?.uptime ?? 0)} />
                <SystemMetric label="Latência" value={formatLatency(health.get(system.id)?.latencyMs ?? null)} />
                <SystemMetric label="Cobertura" value={quality.has(system.id) ? formatPercent(quality.get(system.id)!.coverageScore) : '—'} />
                <SystemMetric label="Último check" value={formatRelativeTime(health.get(system.id)?.lastCheckedAt ?? null)} />
              </Box>
              <Stack direction="row" alignItems="center" justifyContent="space-between" mt={1.8} pt={1.5} borderTop="1px solid" borderColor="divider">
                <Typography variant="caption" color="text.secondary" noWrap maxWidth="78%">{system.baseUrl}</Typography>
                <ArrowForwardRoundedIcon fontSize="small" color="primary" />
              </Stack>
            </Panel>
          ))}
        </Box>
      )}
      <SystemFormDialog open={dialogOpen} onClose={() => setDialogOpen(false)} onSubmit={create} />
      <Snackbar open={Boolean(notice)} autoHideDuration={4200} onClose={() => setNotice(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}>
        <Alert severity={notice?.startsWith('Sistema cadastrado') ? 'success' : 'error'} onClose={() => setNotice(null)} variant="filled">{notice}</Alert>
      </Snackbar>
    </Box>
  );
}

function SystemMetric({ label, value }: { label: string; value: string }) {
  return <Box minWidth={0}><Typography variant="caption" color="text.secondary" display="block">{label}</Typography><Typography variant="body2" fontWeight={680} noWrap mt={.2}>{value}</Typography></Box>;
}
