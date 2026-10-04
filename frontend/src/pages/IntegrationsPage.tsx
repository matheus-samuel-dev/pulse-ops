import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import { Alert, Box, Button, Chip, Skeleton, Stack, Typography } from '@mui/material';
import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { ConnectionManager } from '../components/integrations/ConnectionManager';
import { IntegrationRuns } from '../components/integrations/IntegrationRuns';
import { IntegrationActivity } from '../components/integrations/IntegrationActivity';
import { useIntegrationResource } from '../components/integrations/useIntegrationResource';
import { integrationLabels, integrationColors } from '../components/integrations/integrationPresentation';
import { formatLatency, formatRelativeTime } from '../components/dashboard/dashboardFormatters';
import { integrationsService } from '../services/integrationsService';
import { getApiErrorMessage } from '../services/api';
import { isDemoMode } from '../config/demo';

export function IntegrationsPage() {
  const { user } = useAuth();
  const [configuring, setConfiguring] = useState<string | null>(null);
  const [checking, setChecking] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState<{ message: string; severity: 'success' | 'error' } | null>(null);
  const { data, loading, error, refresh } = useIntegrationResource(integrationsService.overview, revision);
  const canConfigure = Boolean(user && ['ADMIN', 'DEVELOPER'].includes(user.role) && !isDemoMode && !data?.readOnly);
  const check = async (id: string) => {
    if (checking || !canConfigure) return;
    setChecking(id); setNotice(null);
    try {
      const result = await integrationsService.check(id);
      setNotice({ message: `${result.message}${result.cached ? ' Resultado recente reutilizado; nenhuma nova requisição foi executada.' : ''}`, severity: result.status === 'ONLINE' ? 'success' : 'error' });
      setRevision(value => value + 1);
    } catch (failure) { setNotice({ message: getApiErrorMessage(failure), severity: 'error' }); }
    finally { setChecking(null); }
  };
  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1500} mx="auto">
    <PageHeader title="Integrações" description="Pulse Ops observa. AI Web Auditor analisa aplicações. Nexus Flow executa workflows." actions={<Button variant="outlined" startIcon={<RefreshRoundedIcon />} disabled={loading} onClick={refresh}>Atualizar</Button>} />
    {notice && <Alert severity={notice.severity} sx={{ mb: 2 }}>{notice.message}</Alert>}
    {!data && loading && <Skeleton variant="rounded" height={260} aria-label="Carregando integrações" />}
    {!data && !loading && error && <ViewState kind="error" title="Não foi possível carregar as integrações" description={error} actionLabel="Tentar novamente" onAction={refresh} />}
    {data && <Stack spacing={2}>
      {error && <Alert severity="warning">A atualização falhou. Os dados abaixo são da última consulta: {error}</Alert>}
      {data.summary.connected === 0 && <Alert severity="info">Nenhuma integração configurada. Configure somente os serviços que você utiliza.</Alert>}
      {data.readOnly && <Alert severity="info">Demonstração em modo somente leitura.</Alert>}
      <Box display="grid" gridTemplateColumns={{ xs: 'minmax(0,1fr)', md: 'repeat(2,minmax(0,1fr))' }} gap={2}>
        {data.integrations.map(integration => <Panel component="section" key={integration.id} sx={{ p: 2.5 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap"><Typography variant="h2">{integration.name}</Typography><Chip size="small" label={integrationLabels[integration.status]} color={integrationColors[integration.status]} variant="outlined" /></Stack>
          <Typography color="text.secondary" variant="body2" mt={1}>{integration.description}</Typography>
          <Typography variant="body2" mt={2}>{integration.statusReason}</Typography>
          {integration.configured && <Stack spacing={.5} mt={1.5}>
            <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>Destino: {integration.baseUrl}{integration.healthEndpoint}</Typography>
            <Typography variant="caption" color="text.secondary">Última verificação de conexão: {formatRelativeTime(integration.lastCheckedAt)} · {formatLatency(integration.responseTimeMs)}</Typography>
            <Typography variant="caption" color="text.secondary">Última comunicação bem-sucedida: {integration.lastSuccessfulSyncAt ? new Date(integration.lastSuccessfulSyncAt).toLocaleString('pt-BR') : 'Nenhuma registrada'}</Typography>
          </Stack>}
          <Stack direction="row" flexWrap="wrap" gap={1} mt={2}>
            {canConfigure && <Button variant="outlined" onClick={() => setConfiguring(integration.id)}>Configurar {integration.name}</Button>}
            {canConfigure && <Button variant="contained" disabled={!integration.configured || Boolean(checking)} onClick={() => void check(integration.id)} aria-label={`Testar conexão com ${integration.name}`}>{checking === integration.id ? 'Verificando…' : 'Testar conexão'}</Button>}
          </Stack>
        </Panel>)}
      </Box>
      <IntegrationRuns revision={revision} />
      <Panel sx={{ p: 2.5 }}><Typography variant="h2">Atividade das integrações</Typography><Typography variant="body2" color="text.secondary" mt={.5}>Últimos 12 eventos persistidos · até 30 dias</Typography><IntegrationActivity revision={revision} /></Panel>
    </Stack>}
    {configuring && <ConnectionManager slug={configuring} onClose={() => setConfiguring(null)} onSaved={() => setRevision(value => value + 1)} />}
  </Box>;
}
