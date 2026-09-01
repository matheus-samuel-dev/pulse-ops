import FiberManualRecordRoundedIcon from '@mui/icons-material/FiberManualRecordRounded';
import { Chip } from '@mui/material';
import type { SystemStatus } from '../../types/api';
import { statusLabels } from './dashboardFormatters';

const statusColors: Record<SystemStatus, 'success' | 'warning' | 'error' | 'default'> = {
  OPERATIONAL: 'success',
  DEGRADED: 'warning',
  DOWN: 'error',
  UNKNOWN: 'default',
};

export function SystemStatusChip({ status }: { status: SystemStatus }) {
  return (
    <Chip
      size="small"
      color={statusColors[status]}
      variant="outlined"
      icon={<FiberManualRecordRoundedIcon />}
      label={statusLabels[status]}
      sx={{
        height: 25,
        borderRadius: 1.5,
        fontSize: '0.68rem',
        fontWeight: 650,
        bgcolor: 'transparent',
        '& .MuiChip-icon': { fontSize: 9, ml: 0.8 },
      }}
    />
  );
}
