import AssessmentRoundedIcon from '@mui/icons-material/AssessmentRounded';
import AutorenewRoundedIcon from '@mui/icons-material/AutorenewRounded';
import DnsRoundedIcon from '@mui/icons-material/DnsRounded';
import ReportProblemRoundedIcon from '@mui/icons-material/ReportProblemRounded';
import WarningAmberRoundedIcon from '@mui/icons-material/WarningAmberRounded';
import {
  Alert,
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Typography,
  useTheme,
} from '@mui/material';
import { useCallback, useEffect, useState } from 'react';
import { DashboardSkeleton } from '../components/dashboard/DashboardSkeleton';
import { ErrorDonut } from '../components/dashboard/ErrorDonut';
import { KpiCard } from '../components/dashboard/KpiCard';
import { LatencyChart } from '../components/dashboard/LatencyChart';
import { useNavigate } from 'react-router-dom';
import { SystemHealthTable } from '../components/dashboard/SystemHealthTable';
import { ViewState } from '../components/common/ViewState';
import { formatPercent } from '../components/dashboard/dashboardFormatters';
import { getApiErrorMessage } from '../services/api';
import {
  getDashboard,
  type DashboardFilters,
  type DashboardPeriod,
  type EnvironmentFilter,
} from '../services/dashboardService';
import type { DashboardData } from '../types/api';

const periodOptions: Array<{ value: DashboardPeriod; label: string }> = [
  { value: '24h', label: 'Últimas 24 horas' },
  { value: '7d', label: 'Últimos 7 dias' },
  { value: '30d', label: 'Últimos 30 dias' },
];

const environmentOptions: Array<{ value: EnvironmentFilter; label: string }> = [
  { value: 'ALL', label: 'Todos os ambientes' },
  { value: 'PRODUCTION', label: 'Produção' },
  { value: 'STAGING', label: 'Homologação' },
  { value: 'DEVELOPMENT', label: 'Desenvolvimento' },
];

export function DashboardPage() {
  const theme = useTheme();
  const navigate = useNavigate();
  const [filters, setFilters] = useState<DashboardFilters>({ period: '24h', environment: 'ALL' });
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async (background = false) => {
    if (background) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError(null);
    try {
      const response = await getDashboard(filters);
      setData(response);
      setLastUpdatedAt(new Date());
    } catch (requestError) {
      setError(getApiErrorMessage(requestError));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [filters]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1680} mx="auto">
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" gap={2.5} mb={3}>
        <Box>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="h1">Visão geral</Typography>
            {data && (
              <Box
                component="span"
                sx={{
                  px: 1,
                  py: 0.35,
                  borderRadius: 5,
                  bgcolor: data.summary.overallHealth === 'HEALTHY' ? 'rgba(57,201,149,.1)' : 'rgba(242,184,75,.1)',
                  color: data.summary.overallHealth === 'HEALTHY' ? 'success.main' : 'warning.main',
                  fontSize: '0.65rem',
                  fontWeight: 700,
                  letterSpacing: '.035em',
                }}
              >
                {data.summary.overallHealth === 'UNKNOWN' ? 'AGUARDANDO VERIFICAÇÕES' : data.summary.overallHealth === 'HEALTHY' ? 'SISTEMAS OPERACIONAIS' : 'REQUER ATENÇÃO'}
              </Box>
            )}
          </Stack>
          <Typography color="text.secondary" mt={0.75}>
            Visão geral da saúde da sua plataforma
          </Typography>
        </Box>
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={1.25} alignItems={{ sm: 'center' }}>
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="period-filter-label">Período</InputLabel>
            <Select
              labelId="period-filter-label"
              label="Período"
              value={filters.period}
              onChange={(event) => setFilters((current) => ({ ...current, period: event.target.value as DashboardPeriod }))}
            >
              {periodOptions.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 185 }}>
            <InputLabel id="environment-filter-label">Ambiente</InputLabel>
            <Select
              labelId="environment-filter-label"
              label="Ambiente"
              value={filters.environment}
              onChange={(event) => setFilters((current) => ({ ...current, environment: event.target.value as EnvironmentFilter }))}
            >
              {environmentOptions.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
            </Select>
          </FormControl>
          <Button
            variant="outlined"
            startIcon={<AutorenewRoundedIcon sx={{ animation: refreshing ? 'pulseops-spin .8s linear infinite' : 'none' }} />}
            onClick={() => void load(true)}
            disabled={refreshing}
            sx={{ whiteSpace: 'nowrap', '@keyframes pulseops-spin': { to: { transform: 'rotate(360deg)' } } }}
          >
            Atualizar
          </Button>
        </Stack>
      </Stack>

      {lastUpdatedAt && !loading && (
        <Typography variant="caption" color="text.secondary" display="block" mb={1.5} textAlign={{ sm: 'right' }}>
          Atualizado às {lastUpdatedAt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
        </Typography>
      )}

      {loading && <DashboardSkeleton />}
      {!loading && error && !data && (
        <ViewState
          kind="error"
          title="Não foi possível carregar o painel"
          description={error}
          actionLabel="Tentar novamente"
          onAction={() => void load()}
        />
      )}

      {!loading && data && (
        <Stack spacing={2}>
          {error && <Alert severity="warning">Dados anteriores preservados. {error}</Alert>}
          {data.summary.monitoredSystems === 0 && <ViewState kind="empty" title="Cadastre seu primeiro sistema" description="Nenhum sistema está sendo monitorado neste ambiente. Cadastre uma aplicação para executar a primeira verificação e gerar histórico real." actionLabel="Cadastrar sistema" onAction={() => navigate('/sistemas?novo=1')} />}
          <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2, 1fr)', xl: 'repeat(4, 1fr)' }} gap={2}>
            <KpiCard
              label="Sistemas cadastrados"
              value={data.summary.monitoredSystems.toLocaleString('pt-BR')}
              icon={DnsRoundedIcon}
              color={theme.palette.primary.main}
              note={`${data.summary.operationalSystems} operacionais`}
              help="Cadastros do escopo selecionado; sistemas pausados preservam seu histórico."
            />
            <KpiCard
              label="Disponibilidade média"
              value={formatPercent(data.summary.averageAvailability)}
              change={data.summary.availabilityChange}
              icon={AssessmentRoundedIcon}
              color={theme.palette.success.main}
              help="Média das disponibilidades por sistema, com no mínimo cinco verificações válidas na janela."
            />
            <KpiCard
              label="Sistemas com problemas"
              value={String(data.summary.problemSystems)}
              icon={WarningAmberRoundedIcon}
              color={theme.palette.warning.main}
              note={`${data.summary.degradedSystems} degradados · ${data.summary.downSystems} indisponíveis · ${data.summary.configurationRequiredSystems} com configuração inválida`}
              help="Sistemas cuja última condição registrada exige atenção."
            />
            <KpiCard
              label="Incidentes ativos na janela"
              value={data.summary.openIncidents.toLocaleString('pt-BR')}
              icon={ReportProblemRoundedIcon}
              color={theme.palette.error.main}
              help="Incidentes nos estados Aberto ou Investigando; queda é uma tendência favorável."
            />
          </Box>

          <Box display="grid" gridTemplateColumns={{ xs: '1fr', xl: 'minmax(0, 2fr) minmax(300px, .8fr)' }} gap={2}>
            <LatencyChart data={data.latency} />
            <ErrorDonut data={data.errors} />
          </Box>
          <SystemHealthTable systems={data.health} />

        </Stack>
      )}
    </Box>
  );
}
