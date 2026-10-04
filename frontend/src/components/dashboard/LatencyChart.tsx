import { Box, Stack, Typography, useTheme } from '@mui/material';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { LatencyPoint } from '../../types/api';
import { ViewState } from '../common/ViewState';
import { Panel } from '../common/Panel';

export function LatencyChart({ data }: { data: LatencyPoint[] }) {
  const theme = useTheme();
  const chartData = data.map((point) => ({
    ...point,
    label: new Intl.DateTimeFormat('pt-BR', {
      hour: '2-digit',
      minute: '2-digit',
      ...(data.length > 24 && { day: '2-digit', month: 'short' }),
    }).format(new Date(point.timestamp)),
  }));

  return (
    <Panel sx={{ p: { xs: 2, md: 2.5 }, minWidth: 0 }}>
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between" mb={2.5}>
        <Box>
          <Typography variant="h2">Latência das APIs</Typography>
          <Typography color="text.secondary" variant="body2" mt={0.45}>
            Tempo médio de resposta e percentil 95
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5} mt={0.25}>
          <ChartLegend color={theme.palette.primary.main} label="Média" />
          <ChartLegend color={theme.palette.secondary.main} label="p95" />
        </Stack>
      </Stack>
      <Box height={278} aria-label="Gráfico de latência das APIs">
        {!data.some(point => point.samples > 0) ? <ViewState kind="empty" title="Ainda não há amostras de latência" description="Execute uma verificação para medir o tempo real de resposta." /> : <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={chartData} margin={{ top: 8, right: 4, left: 8, bottom: 0 }}>
            <defs>
              <linearGradient id="averageLatency" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={theme.palette.primary.main} stopOpacity={0.34} />
                <stop offset="100%" stopColor={theme.palette.primary.main} stopOpacity={0.015} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke={theme.palette.divider} strokeDasharray="4 5" vertical={false} />
            <XAxis
              dataKey="label"
              axisLine={false}
              tickLine={false}
              minTickGap={30}
              tick={{ fill: theme.palette.text.secondary, fontSize: 11 }}
              dy={9}
            />
            <YAxis
              width={58}
              axisLine={false}
              tickLine={false}
              tick={{ fill: theme.palette.text.secondary, fontSize: 11 }}
              tickFormatter={(value) => `${value} ms`}
            />
            <Tooltip
              cursor={{ stroke: theme.palette.divider }}
              contentStyle={{
                background: theme.palette.background.paper,
                border: `1px solid ${theme.palette.divider}`,
                borderRadius: 10,
                boxShadow: '0 12px 30px rgba(0,0,0,.18)',
                fontSize: 12,
              }}
              formatter={(value, name) => [`${Math.round(Number(value ?? 0))} ms`, name === 'p95Ms' ? 'p95' : 'Média']}
            />
            <Area
              type="monotone"
              dataKey="averageMs"
              stroke={theme.palette.primary.main}
              strokeWidth={2.2}
              fill="url(#averageLatency)"
              animationDuration={650}
            />
            <Area
              type="monotone"
              dataKey="p95Ms"
              stroke={theme.palette.secondary.main}
              strokeWidth={1.5}
              strokeDasharray="5 4"
              fill="transparent"
              animationDuration={800}
            />
          </AreaChart>
        </ResponsiveContainer>}
      </Box>
    </Panel>
  );
}

function ChartLegend({ color, label }: { color: string; label: string }) {
  return (
    <Stack direction="row" alignItems="center" spacing={0.65}>
      <Box width={7} height={7} borderRadius="50%" bgcolor={color} />
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
    </Stack>
  );
}
