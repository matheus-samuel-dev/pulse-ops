import HubRoundedIcon from '@mui/icons-material/HubRounded';
import CheckCircleOutlineRoundedIcon from '@mui/icons-material/CheckCircleOutlineRounded';
import ReportProblemRoundedIcon from '@mui/icons-material/ReportProblemRounded';
import BoltRoundedIcon from '@mui/icons-material/BoltRounded';
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import { Alert, Box, Button, Skeleton, Stack, Typography, useTheme } from '@mui/material';
import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { KpiCard } from '../components/dashboard/KpiCard';
import { IntegrationSpotlight } from '../components/integrations/IntegrationSpotlight';
import { IntegrationList } from '../components/integrations/IntegrationList';
import { IntegrationMap } from '../components/integrations/IntegrationMap';
import { IntegrationActivity } from '../components/integrations/IntegrationActivity';
import { IntegrationDetails } from '../components/integrations/IntegrationDetails';
import { useIntegrationResource } from '../components/integrations/useIntegrationResource';
import { integrationsService } from '../services/integrationsService';
import { getApiErrorMessage } from '../services/api';
import { isDemoMode } from '../config/demo';

export function IntegrationsPage() {
  const theme = useTheme();
  const { user } = useAuth();
  const [selected, setSelected] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState<{ message: string; severity: 'success' | 'error' | 'warning' | 'info' } | null>(null);
  const { data, loading, error, refresh } = useIntegrationResource(integrationsService.overview, revision);
  const primary = data?.integrations.find(item => item.primary);
  const canCheck = Boolean(user && ['ADMIN', 'DEVELOPER'].includes(user.role) && data && !data.readOnly && !isDemoMode);
  const openDetails = (id: string) => { setNotice(null); setSelected(id); };

  const check = async (id: string) => {
    if (checking || !canCheck) return;
    setChecking(true);
    try {
      const result = await integrationsService.check(id);
      setNotice({ message: `${result.message}.${result.cached ? ' Resultado recente reutilizado para respeitar o intervalo entre verificações.' : ''}`,
        severity: result.status === 'ONLINE' ? 'success' : result.status === 'OFFLINE' ? 'error' : 'warning' });
      setRevision(value => value + 1);
    } catch (failure) { setNotice({ message: getApiErrorMessage(failure), severity: 'error' }); }
    finally { setChecking(false); }
  };

  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1800} mx="auto"
    sx={{ '@media (prefers-reduced-motion: reduce)': { '& *, & *::before, & *::after': { animation: 'none !important', transition: 'none !important' } } }}>
    <PageHeader title="Integrações" description="Conecte, monitore e gerencie sistemas integrados em um único hub operacional." eyebrow="Ecossistema Pulse Ops"
      actions={<Button variant="outlined" startIcon={<RefreshRoundedIcon />} onClick={refresh} disabled={loading} sx={{ minHeight: 44 }}>Atualizar</Button>} />
    {(isDemoMode || data?.readOnly) && <Alert severity="info" sx={{ mb: 2.5 }}>Ambiente demonstrativo · Os registros deste ambiente podem conter dados de demonstração. Testes de conexão estão desabilitados.</Alert>}
    {!data && loading && <Box role="status" aria-label="Carregando integrações"><Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2,1fr)', lg: 'repeat(4,1fr)' }} gap={2}>{[1, 2, 3, 4].map(value => <Skeleton key={value} variant="rounded" height={138} />)}</Box><Skeleton variant="rounded" height={275} sx={{ mt: 2.5 }} /><Skeleton variant="rounded" height={320} sx={{ mt: 2.5 }} /></Box>}
    {!data && !loading && error && <ViewState kind="error" title="Não foi possível carregar as integrações" description={error} actionLabel="Tentar novamente" onAction={refresh} />}
    {data && <>
      {error && <Alert severity="warning" sx={{ mb: 2 }} action={<Button color="inherit" onClick={refresh}>Tentar novamente</Button>}>A atualização falhou. Os dados abaixo são da última consulta.</Alert>}
      <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2,minmax(0,1fr))', lg: 'repeat(4,minmax(0,1fr))' }} gap={2} mb={2.5}>
        <KpiCard label="Sistemas conectados" value={String(data.summary.connected)} icon={HubRoundedIcon} color={theme.palette.primary.main} note={`${data.integrations.length} sistemas no ecossistema · vínculos cadastrados`} />
        <KpiCard label="Operacionais" value={String(data.summary.operational)} icon={CheckCircleOutlineRoundedIcon} color={theme.palette.success.main} note="Disponibilidade verificada recentemente" />
        <KpiCard label="Com incidentes" value={String(data.summary.withIncidents)} icon={ReportProblemRoundedIcon} color={theme.palette.warning.main} note="Integrações em atenção ou offline" />
        <KpiCard label="Eventos processados hoje" value={data.summary.eventsToday.toLocaleString('pt-BR')} icon={BoltRoundedIcon} color={theme.palette.secondary.main} note={`Checks e relatórios · ${data.reportingTimezone}`} />
      </Box>
      <Box display="grid" gap={2.5} alignItems="start" sx={{ gridTemplateColumns: 'minmax(0,1fr)', '@media (min-width:1440px)': { gridTemplateColumns: 'minmax(0,1fr) 320px' }, '@media (min-width:1680px)': { gridTemplateColumns: 'minmax(0,1fr) 380px' } }}>
        <Stack spacing={3} minWidth={0}>{primary && <IntegrationSpotlight integration={primary} onDetails={openDetails} />}<IntegrationList integrations={data.integrations} onDetails={openDetails} /></Stack>
        <Box display="grid" gap={2.5} alignItems="start" sx={{ gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'repeat(2,minmax(0,1fr))' }, '@media (min-width:1440px)': { gridTemplateColumns: 'minmax(0,1fr)' } }}>
          <IntegrationMap integrations={data.integrations} onDetails={openDetails} />
          <Panel component="section" aria-labelledby="integration-activity-title" sx={{ p: 2 }}><Typography id="integration-activity-title" variant="h2">Atividade recente</Typography><Typography variant="body2" color="text.secondary" mt={0.75}>Últimos 12 eventos · até 30 dias</Typography><Box role="region" aria-label="Histórico de atividade recente" tabIndex={0} sx={{ maxHeight: 540, overflowY: 'auto', mt: 1, pr: 0.5 }}><IntegrationActivity revision={revision} /></Box></Panel>
        </Box>
      </Box>
      <Typography variant="caption" color="text.secondary" display="block" mt={2.5}>Consulta atualizada às {new Date(data.generatedAt).toLocaleTimeString('pt-BR')}. A disponibilidade depende da última verificação; sistemas sem evidência recente permanecem desconhecidos.</Typography>
    </>}
    {selected && <IntegrationDetails key={selected} id={selected} onClose={() => setSelected(null)} canCheck={canCheck} checking={checking} onCheck={id => void check(id)} revision={revision} feedback={notice} />}
  </Box>;
}
