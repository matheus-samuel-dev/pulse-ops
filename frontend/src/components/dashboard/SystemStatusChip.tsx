import FiberManualRecordRoundedIcon from '@mui/icons-material/FiberManualRecordRounded';
import PauseCircleOutlineRoundedIcon from '@mui/icons-material/PauseCircleOutlineRounded';
import { Chip } from '@mui/material';
import type { SystemStatus } from '../../types/api';
import { statusLabels } from './dashboardFormatters';

const statusColors: Record<SystemStatus, 'success' | 'warning' | 'error' | 'default'> = {
  OPERATIONAL: 'success',
  DEGRADED: 'warning',
  DOWN: 'error',
  UNKNOWN: 'default',
  MAINTENANCE: 'default',
  CONFIGURATION_REQUIRED: 'warning',
};

export function SystemStatusChip({ status, active = true, label }: { status: SystemStatus; active?: boolean; label?: string }) {
  return (
    <Chip
      size="small"
      color={active ? statusColors[status] : 'default'}
      variant="outlined"
      icon={active ? <FiberManualRecordRoundedIcon /> : <PauseCircleOutlineRoundedIcon />}
      label={label ?? (active ? statusLabels[status] : 'Pausado')}
      sx={{
        height: 25,
        borderRadius: 1.5,
        fontSize: '0.73rem',
        fontWeight: 650,
        bgcolor: 'transparent',
        '& .MuiChip-icon': { fontSize: 9, ml: 0.8 },
      }}
    />
  );
}
