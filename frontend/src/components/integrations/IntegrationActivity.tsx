import CheckCircleOutlineRoundedIcon from '@mui/icons-material/CheckCircleOutlineRounded';
import ErrorOutlineRoundedIcon from '@mui/icons-material/ErrorOutlineRounded';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import { Alert, Box, Button, Skeleton, Stack, Typography } from '@mui/material';
import { useCallback } from 'react';
import { ViewState } from '../common/ViewState';
import { integrationsService } from '../../services/integrationsService';
import { IntegrationTime } from './IntegrationPrimitives';
import { useIntegrationResource } from './useIntegrationResource';

export function IntegrationActivity({ id = null, revision = 0 }: { id?: string | null; revision?: number }) {
  const request = useCallback((signal: AbortSignal) => integrationsService.events(id, signal), [id]);
  const { data, loading, error, refresh } = useIntegrationResource(request, revision);
  if (!data && loading) return <Stack aria-label="Carregando atividade" spacing={2} mt={2}>{[1, 2, 3].map(value => <Skeleton key={value} variant="rounded" height={54} />)}</Stack>;
  if (!data && error) return <ViewState kind="error" title="Atividade indisponível" description={error} actionLabel="Tentar novamente" onAction={refresh} />;
  return <>
    {error && <Alert severity="warning" action={<Button color="inherit" onClick={refresh}>Atualizar</Button>}>Não foi possível atualizar os eventos.</Alert>}
    {data?.length === 0 && <ViewState kind="empty" title="Nenhuma atividade recente" description="Checks e relatórios recebidos nos últimos 30 dias aparecerão aqui." />}
    <Box component="ol" sx={{ m: 0, p: 0, listStyle: 'none' }}>
      {data?.map(event => {
        const Icon = event.type === 'QUALITY' ? DescriptionOutlinedIcon : event.impact === 'SUCCESS' ? CheckCircleOutlineRoundedIcon : ErrorOutlineRoundedIcon;
        const severity = { INFO: 'Informação', SUCCESS: 'Sucesso', WARNING: 'Atenção', CRITICAL: 'Falha' }[event.impact];
        return <Box component="li" key={`${event.type}-${event.id}`} sx={{ display: 'flex', gap: 1.25, py: 1.75, borderBottom: '1px solid', borderColor: 'divider', '&:last-child': { borderBottom: 0 } }}>
          <Icon aria-hidden="true" sx={{ mt: 0.25, fontSize: 20, color: event.impact === 'SUCCESS' ? 'success.main' : event.impact === 'CRITICAL' ? 'error.main' : 'warning.main' }} />
          <Box minWidth={0} flex={1}><Typography variant="body2" fontWeight={600}>{event.title}</Typography><Typography variant="caption" color="text.secondary" display="block" mt={0.25}>{event.systemName} · {severity}</Typography><Typography variant="caption" color="text.secondary" display="block" mt={0.25}>{event.description}</Typography><Box color="text.secondary" mt={0.5}><IntegrationTime value={event.occurredAt} /></Box></Box>
        </Box>;
      })}
    </Box>
  </>;
}
