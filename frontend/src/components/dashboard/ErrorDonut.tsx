import { Box, Stack, Typography, useTheme } from '@mui/material';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { ErrorBreakdown } from '../../types/api';
import { Panel } from '../common/Panel';

export function ErrorDonut({ data }: { data: ErrorBreakdown }) {
  const theme = useTheme();
  const entries = [
    { name: '5xx', value: data.serverErrors, color: theme.palette.error.main },
    { name: '4xx', value: data.clientErrors, color: theme.palette.warning.main },
    { name: 'Timeout', value: data.timeouts, color: theme.palette.secondary.main },
    { name: 'Outros', value: data.others, color: theme.palette.info.main },
  ];
  const displayEntries = data.total ? entries : [{ name: 'Sem erros', value: 1, color: theme.palette.divider }];

  return (
    <Panel sx={{ p: { xs: 2, md: 2.5 }, minWidth: 0 }}>
      <Typography variant="h2">Erros por período</Typography>
      <Typography color="text.secondary" variant="body2" mt={0.45}>
        Distribuição das falhas observadas
      </Typography>
      <Box position="relative" height={180} mt={1}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={displayEntries}
              dataKey="value"
              nameKey="name"
              cx="50%"
              cy="50%"
              innerRadius={57}
              outerRadius={76}
              paddingAngle={data.total ? 3 : 0}
              stroke="none"
              animationDuration={650}
            >
              {displayEntries.map((entry) => <Cell key={entry.name} fill={entry.color} />)}
            </Pie>
            {data.total > 0 && <Tooltip
              contentStyle={{ background: theme.palette.background.paper, border: `1px solid ${theme.palette.divider}`, borderRadius: 10, boxShadow: '0 12px 30px rgba(0,0,0,.18)', fontSize: 12 }}
              formatter={(value, name) => [`${Number(value).toLocaleString('pt-BR')} evento(s)`, String(name)]}
            />}
          </PieChart>
        </ResponsiveContainer>
        <Stack
          alignItems="center"
          justifyContent="center"
          sx={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}
        >
          <Typography fontSize="1.55rem" fontWeight={720} lineHeight={1}>
            {data.total}
          </Typography>
          <Typography variant="caption" color="text.secondary" mt={0.5}>
            eventos
          </Typography>
        </Stack>
      </Box>
      <Box display="grid" gridTemplateColumns="repeat(2, minmax(0, 1fr))" gap={1.15}>
        {entries.map((entry) => (
          <Stack direction="row" alignItems="center" justifyContent="space-between" key={entry.name}>
            <Stack direction="row" spacing={0.75} alignItems="center">
              <Box width={7} height={7} borderRadius="50%" bgcolor={entry.color} />
              <Typography variant="caption" color="text.secondary">{entry.name}</Typography>
            </Stack>
            <Typography variant="caption" fontWeight={700}>{entry.value}</Typography>
          </Stack>
        ))}
      </Box>
    </Panel>
  );
}
