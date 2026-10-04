import OpenInNewRoundedIcon from '@mui/icons-material/OpenInNewRounded';
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import { alpha, Box, Button, Chip, Stack, Typography } from '@mui/material';
import { Panel } from '../common/Panel';
import { formatLatency } from '../dashboard/dashboardFormatters';
import type { Integration } from '../../types/integrations';
import { IntegrationIcon, IntegrationMetric, IntegrationStatusBadge, IntegrationTime } from './IntegrationPrimitives';

export function IntegrationSpotlight({ integration, onDetails }: { integration: Integration; onDetails: (id: string) => void }) {
  return <Panel component="section" aria-labelledby="auditor-title" sx={theme => ({ p: { xs: 2, sm: 2.5 },
    borderColor: alpha(theme.palette.primary.main, 0.5), background: `linear-gradient(120deg, ${alpha(theme.palette.primary.main, 0.09)}, transparent 75%), ${theme.palette.background.paper}` })}>
    <Stack direction="row" spacing={1.5} alignItems="flex-start">
      <IntegrationIcon id={integration.id} size={52} />
      <Box flex={1} minWidth={0}><Typography variant="h2" id="auditor-title">{integration.name}</Typography>
        <Typography variant="body2" color="text.secondary" mt={0.6}>{integration.description}</Typography>
        <Chip size="small" variant="outlined" color="primary" label="Integração principal" sx={{ mt: 1.25 }} />
      </Box>
    </Stack>
    <Typography variant="body2" color="text.secondary" mt={2}>Monitore o serviço e acompanhe as auditorias solicitadas para os seus sistemas. A análise é executada pelo AI Web Auditor.</Typography>
    <Box component="dl" display="grid" gridTemplateColumns={{ xs: 'repeat(2, minmax(0, 1fr))', sm: 'repeat(4, minmax(0, 1fr))' }} gap={2} my={2.5}>
      <IntegrationMetric label="Último relatório"><IntegrationTime value={integration.lastReportAt} /></IntegrationMetric>
      <IntegrationMetric label="Última comunicação"><IntegrationTime value={integration.lastSuccessfulSyncAt ?? integration.lastCheckedAt} /></IntegrationMetric>
      <IntegrationMetric label="Tempo de resposta">{formatLatency(integration.responseTimeMs)}</IntegrationMetric>
      <IntegrationMetric label="Verificações · 24h">{integration.checksLast24h.toLocaleString('pt-BR')}</IntegrationMetric>
    </Box>
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} gap={1.5} pt={2} borderTop="1px solid" borderColor="divider">
      <IntegrationStatusBadge integration={integration} />
      <Stack direction="row" gap={1} flexWrap="wrap">
        <Button endIcon={<ArrowForwardRoundedIcon />} onClick={() => onDetails(integration.id)} sx={{ minHeight: 44 }}>Ver integração</Button>
        {integration.publicUrl && <Button component="a" href={integration.publicUrl} target="_blank" rel="noopener noreferrer" variant="outlined" endIcon={<OpenInNewRoundedIcon />} sx={{ minHeight: 44 }}>Acessar sistema</Button>}
      </Stack>
    </Stack>
  </Panel>;
}
