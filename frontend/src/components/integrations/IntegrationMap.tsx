import HubRoundedIcon from '@mui/icons-material/HubRounded';
import { alpha, Box, ButtonBase, Typography, useTheme } from '@mui/material';
import { Panel } from '../common/Panel';
import type { Integration } from '../../types/integrations';
import { integrationColors, integrationLabels } from './integrationPresentation';
import { IntegrationIcon } from './IntegrationPrimitives';

const positions: Record<string, [number, number]> = {
  'arena-predict': [1, 1], 'ai-web-auditor': [2, 1], playspace: [3, 1],
  logitrack: [1, 3], hospital: [2, 3], helpdesk: [3, 3],
};

export function IntegrationMap({ integrations, onDetails }: { integrations: Integration[]; onDetails: (id: string) => void }) {
  const theme = useTheme();
  return <Panel component="section" aria-labelledby="integration-map-title" sx={{ p: 2, minWidth: 0 }}>
    <Typography id="integration-map-title" variant="h2">Mapa de integrações</Typography>
    <Typography variant="body2" color="text.secondary" mt={0.75}>Um ecossistema. Um centro de operação.</Typography>
    <Box sx={{ position: 'relative', display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gridTemplateRows: 'repeat(3, 112px)', mt: 1.5 }}>
      <Box component="svg" viewBox="0 0 300 336" preserveAspectRatio="none" aria-hidden="true" sx={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
        {integrations.map(integration => {
          const [column, row] = positions[integration.id] ?? [2, 1];
          const color = integrationColors[integration.status];
          return <line key={integration.id} x1="150" y1="168" x2={(column - 0.5) * 100} y2={(row - 0.5) * 112}
            stroke={color === 'default' ? theme.palette.divider : alpha(theme.palette[color].main, 0.65)}
            strokeWidth="1.5" strokeDasharray={integration.configured ? '4 5' : '2 6'} />;
        })}
      </Box>
      <Box sx={{ gridColumn: 2, gridRow: 2, zIndex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
        <Box sx={{ width: 64, height: 64, borderRadius: '50%', border: '2px solid', borderColor: 'primary.main', bgcolor: 'background.paper', display: 'grid', placeItems: 'center', boxShadow: `0 0 24px ${alpha(theme.palette.primary.main, 0.2)}` }}><HubRoundedIcon color="primary" fontSize="large" /></Box>
        <Typography fontSize="0.9rem" fontWeight={750} mt={0.75} sx={{ bgcolor: 'background.paper' }}>Pulse Ops</Typography>
        <Typography variant="caption" color="text.secondary">Hub operacional</Typography>
      </Box>
      {integrations.map(integration => {
        const [column, row] = positions[integration.id] ?? [2, 1];
        return <ButtonBase key={integration.id} onClick={() => onDetails(integration.id)} aria-label={`Detalhes de ${integration.name} no mapa`}
          sx={{ gridColumn: column, gridRow: row, minWidth: 0, zIndex: 1, borderRadius: 2, display: 'flex', flexDirection: 'column', px: 0.25, gap: 0.5, '&:hover': { bgcolor: 'action.hover' } }}>
          <IntegrationIcon id={integration.id} size={36} />
          <Typography variant="caption" fontWeight={650} textAlign="center" lineHeight={1.3} sx={{ bgcolor: 'background.paper' }}>{integration.name}</Typography>
          <Typography variant="caption" color="text.secondary" textAlign="center" lineHeight={1.3} sx={{ bgcolor: 'background.paper' }}>{!integration.configured ? 'Não configurado' : integrationLabels[integration.status]}</Typography>
        </ButtonBase>;
      })}
    </Box>
    <Typography variant="caption" color="text.secondary" display="block" borderTop="1px solid" borderColor="divider" pt={1.5}>Selecione um sistema para explorar a conexão.</Typography>
  </Panel>;
}
