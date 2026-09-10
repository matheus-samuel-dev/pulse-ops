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
};

export function SystemStatusChip({ status, active = true }: { status: SystemStatus; active?: boolean }) {
  return (
    <Chip
      size="small"
      color={active ? statusColors[status] : 'default'}
      variant="outlined"
      icon={active ? <FiberManualRecordRoundedIcon /> : <PauseCircleOutlineRoundedIcon />}
      label={active ? statusLabels[status] : 'Manutenção'}
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
