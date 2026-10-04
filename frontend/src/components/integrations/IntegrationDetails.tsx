import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import NetworkCheckRoundedIcon from '@mui/icons-material/NetworkCheckRounded';
import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded';
import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Skeleton, Stack, Typography, type AlertColor } from '@mui/material';
import { useCallback } from 'react';
import { ViewState } from '../common/ViewState';
import { formatLatency } from '../dashboard/dashboardFormatters';
import { integrationsService } from '../../services/integrationsService';
import { IntegrationActivity } from './IntegrationActivity';
import { IntegrationHealth, IntegrationIcon, IntegrationMetric, IntegrationStatusBadge, IntegrationTime } from './IntegrationPrimitives';
import { useIntegrationResource } from './useIntegrationResource';

interface Props { id: string; onClose: () => void; canCheck: boolean; checking: boolean; onCheck: (id: string) => void; revision: number; feedback?: { message: string; severity: AlertColor } | null }

export function IntegrationDetails({ id, onClose, canCheck, checking, onCheck, revision, feedback }: Props) {
  const request = useCallback((signal: AbortSignal) => integrationsService.detail(id, signal), [id]);
  const { data, loading, error, refresh } = useIntegrationResource(request, revision);
  return <Dialog open onClose={checking ? undefined : onClose} fullWidth maxWidth="md" aria-labelledby="integration-details-title">
    <DialogTitle id="integration-details-title" sx={{ pr: 7 }}>{data?.name ?? 'Detalhes da integração'}</DialogTitle>
    <IconButton aria-label="Fechar detalhes" onClick={onClose} disabled={checking} sx={{ position: 'absolute', top: 10, right: 12, width: 44, height: 44 }}><CloseRoundedIcon /></IconButton>
    <DialogContent dividers sx={{ px: { xs: 2, sm: 3 } }}>
      {feedback && <Alert severity={feedback.severity} sx={{ mb: 2 }}>{feedback.message}</Alert>}
      {!data && loading && <Stack aria-label="Carregando detalhes" spacing={2}><Skeleton height={60} /><Skeleton variant="rounded" height={240} /></Stack>}
      {error && <ViewState kind="error" title="Não foi possível carregar os detalhes" description={error} actionLabel="Tentar novamente" onAction={refresh} />}
      {data && <>
        <Stack direction="row" spacing={1.5} alignItems="center"><IntegrationIcon id={id} size={48} /><Box><Typography variant="body2" color="text.secondary" mb={1}>{data.description}</Typography><IntegrationStatusBadge integration={data} /></Box></Stack>
        <Alert severity={!data.configured || data.status === 'UNKNOWN' ? 'info' : data.status === 'ONLINE' ? 'success' : 'warning'} sx={{ mt: 2 }}>{data.statusReason}</Alert>
        <Box component="dl" display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2,minmax(0,1fr))' }} gap={2.5} my={3}>
          <IntegrationMetric label="Tipo de integração">{data.type}</IntegrationMetric>
          <IntegrationMetric label="URL pública">{data.publicUrl ?? 'Não configurada'}</IntegrationMetric>
          <IntegrationMetric label="URL base">{data.baseUrl ?? 'Não configurada'}</IntegrationMetric>
          <IntegrationMetric label="Endpoint de verificação">{data.healthEndpoint ?? 'Não configurado'}</IntegrationMetric>
          <IntegrationMetric label="Última verificação"><IntegrationTime value={data.lastCheckedAt} /></IntegrationMetric>
          <IntegrationMetric label="Última comunicação da execução"><IntegrationTime value={data.lastSuccessfulSyncAt} /></IntegrationMetric>
          <IntegrationMetric label="Tempo de resposta">{formatLatency(data.responseTimeMs)}</IntegrationMetric>
          <IntegrationMetric label="Saúde / taxa de sucesso · 24h"><IntegrationHealth integration={data} /></IntegrationMetric>
          <IntegrationMetric label="Verificações · 24h">{data.checksLast24h.toLocaleString('pt-BR')}</IntegrationMetric>
          <IntegrationMetric label="Erros · 24h">{data.errorsLast24h.toLocaleString('pt-BR')}</IntegrationMetric>
          <IntegrationMetric label="Última falha"><IntegrationTime value={data.lastFailureAt} /></IntegrationMetric>
          <IntegrationMetric label="Último resultado recebido"><IntegrationTime value={data.lastReportAt} /></IntegrationMetric>
        </Box>
        <Typography variant="body2" color="text.secondary" mb={2}>A saúde usa verificações reais nas últimas 24 horas, com pelo menos cinco amostras. Execuções e resultados são consultados no serviço externo; o teste HTTP não executa auditorias ou workflows.</Typography>
        {!canCheck && <Alert severity="info" sx={{ mb: 2 }}>Teste de conexão disponível para administradores e desenvolvedores em ambientes com permissão de escrita.</Alert>}
        <Typography variant="h2" mt={3}>Eventos recentes</Typography>
        <IntegrationActivity id={id} revision={revision} />
      </>}
    </DialogContent>
    <DialogActions sx={{ p: 2, flexWrap: 'wrap', gap: 1 }}>
      {data?.publicUrl && <Button component="a" href={data.publicUrl} target="_blank" rel="noopener noreferrer" endIcon={<OpenInNewRoundedIcon />} sx={{ minHeight: 44 }}>Acessar sistema</Button>}
      {canCheck && data?.configured && data.enabled && <Button variant="contained" disabled={checking} onClick={() => onCheck(id)}
        startIcon={checking ? <CircularProgress size={16} color="inherit" /> : <NetworkCheckRoundedIcon />} sx={{ minHeight: 44 }}>{checking ? 'Verificando…' : 'Testar conexão'}</Button>}
      <Button onClick={onClose} disabled={checking} sx={{ minHeight: 44 }}>Fechar</Button>
    </DialogActions>
  </Dialog>;
}
