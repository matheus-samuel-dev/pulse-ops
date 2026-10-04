import ArrowDownwardRoundedIcon from '@mui/icons-material/ArrowDownwardRounded';
import ArrowUpwardRoundedIcon from '@mui/icons-material/ArrowUpwardRounded';
import RemoveRoundedIcon from '@mui/icons-material/RemoveRounded';
import { alpha, Box, Stack, Tooltip, Typography, useTheme, type SvgIconProps } from '@mui/material';
import type { ComponentType } from 'react';
import { Panel } from '../common/Panel';

interface KpiCardProps {
  label: string;
  value: string;
  change?: number | null;
  icon: ComponentType<SvgIconProps>;
  color: string;
  inverseChange?: boolean;
  note?: string;
  help?: string;
}

export function KpiCard({ label, value, change, icon: Icon, color, inverseChange, note, help }: KpiCardProps) {
  const theme = useTheme();
  const hasChange = typeof change === 'number';
  const positive = (change ?? 0) > 0;
  const neutral = !change;
  const favorable = neutral || (inverseChange ? !positive : positive);
  const TrendIcon = neutral ? RemoveRoundedIcon : positive ? ArrowUpwardRoundedIcon : ArrowDownwardRoundedIcon;

  return (
    <Panel sx={{ p: { xs: 2, lg: 2.25 }, minHeight: 138, position: 'relative', overflow: 'hidden' }}>
      <Box
        aria-hidden="true"
        sx={{
          position: 'absolute',
          width: 100,
          height: 100,
          right: -35,
          top: -35,
          borderRadius: '50%',
          backgroundColor: alpha(color, theme.palette.mode === 'dark' ? 0.08 : 0.06),
        }}
      />
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between">
        <Box>
          <Tooltip title={help ?? ''} disableHoverListener={!help}>
            <Typography color="text.secondary" fontSize="0.78rem" fontWeight={550} sx={{ cursor: help ? 'help' : 'default', textDecoration: help ? 'underline dotted' : 'none', textUnderlineOffset: 3 }}>
              {label}
            </Typography>
          </Tooltip>
          <Typography mt={1.15} fontSize={{ xs: '1.65rem', lg: '1.85rem' }} fontWeight={720} letterSpacing="-0.045em">
            {value}
          </Typography>
        </Box>
        <Box
          sx={{
            width: 36,
            height: 36,
            display: 'grid',
            placeItems: 'center',
            borderRadius: 2.25,
            color,
            bgcolor: alpha(color, 0.11),
          }}
        >
          <Icon sx={{ fontSize: 19 }} />
        </Box>
      </Stack>
      <Stack direction="row" spacing={0.65} alignItems="center" mt={1.1}>
        {hasChange && (
          <Stack
            direction="row"
            alignItems="center"
            sx={{ color: favorable ? 'success.main' : 'error.main' }}
          >
            <TrendIcon sx={{ fontSize: 14 }} />
            <Typography fontSize="0.7rem" fontWeight={650}>
              {Math.abs(change ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%
            </Typography>
          </Stack>
        )}
        <Typography variant="caption" color="text.secondary">
          {note ?? (hasChange ? 'vs. período anterior' : 'no período selecionado')}
        </Typography>
      </Stack>
    </Panel>
  );
}
