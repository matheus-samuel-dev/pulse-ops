import TravelExploreRoundedIcon from '@mui/icons-material/TravelExploreRounded';
import QueryStatsRoundedIcon from '@mui/icons-material/QueryStatsRounded';
import SportsTennisRoundedIcon from '@mui/icons-material/SportsTennisRounded';
import LocalShippingRoundedIcon from '@mui/icons-material/LocalShippingRounded';
import SupportAgentRoundedIcon from '@mui/icons-material/SupportAgentRounded';
import LocalHospitalRoundedIcon from '@mui/icons-material/LocalHospitalRounded';
import HubRoundedIcon from '@mui/icons-material/HubRounded';
import type { IntegrationStatus } from '../../types/integrations';

export const integrationLabels: Record<IntegrationStatus, string> = {
  ONLINE: 'Conectada', ATTENTION: 'Atenção', OFFLINE: 'Erro de conexão', UNKNOWN: 'Aguardando teste', NOT_CONFIGURED: 'Não configurada',
};
export const integrationColors = {
  ONLINE: 'success', ATTENTION: 'warning', OFFLINE: 'error', UNKNOWN: 'default', NOT_CONFIGURED: 'default',
} as const;

export function integrationIdentity(id: string) {
  switch (id) {
    case 'ai-web-auditor': return { Icon: TravelExploreRoundedIcon, color: 'primary' } as const;
    case 'arena-predict': return { Icon: QueryStatsRoundedIcon, color: 'secondary' } as const;
    case 'playspace': return { Icon: SportsTennisRoundedIcon, color: 'warning' } as const;
    case 'logitrack': return { Icon: LocalShippingRoundedIcon, color: 'info' } as const;
    case 'helpdesk': return { Icon: SupportAgentRoundedIcon, color: 'error' } as const;
    case 'hospital': return { Icon: LocalHospitalRoundedIcon, color: 'success' } as const;
    default: return { Icon: HubRoundedIcon, color: 'primary' } as const;
  }
}
