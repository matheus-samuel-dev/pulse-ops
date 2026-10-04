import MoreHorizRoundedIcon from '@mui/icons-material/MoreHorizRounded';
import { alpha, Box, IconButton, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tooltip, Typography, useMediaQuery, useTheme } from '@mui/material';
import { Line, LineChart, ResponsiveContainer, YAxis } from 'recharts';
import { useNavigate } from 'react-router-dom';
import type { SystemHealth, SystemStatus } from '../../types/api';
import { Panel } from '../common/Panel';
import { environmentLabels, formatLatency, formatPercent, formatRelativeTime } from './dashboardFormatters';
import { SystemStatusChip } from './SystemStatusChip';

const statusColor: Record<SystemStatus, string> = {
  OPERATIONAL: '#39c995',
  DEGRADED: '#f2b84b',
  DOWN: '#ef6673',
  UNKNOWN: '#8fa0b9',
  MAINTENANCE: '#8c98ab',
  CONFIGURATION_REQUIRED: '#ef6673',
};

export function SystemHealthTable({ systems }: { systems: SystemHealth[] }) {
  const theme = useTheme();
  const tableVisible = useMediaQuery(theme.breakpoints.up('md'));
  const navigate = useNavigate();

  return (
    <Panel sx={{ overflow: 'hidden' }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        alignItems={{ xs: 'flex-start', sm: 'center' }}
        justifyContent="space-between"
        gap={1}
        px={{ xs: 2, md: 2.5 }}
        py={2.25}
        borderBottom="1px solid"
        borderColor="divider"
      >
        <Box>
          <Typography variant="h2">Saúde dos sistemas</Typography>
          <Typography color="text.secondary" variant="body2" mt={0.45}>
            Condição operacional e desempenho mais recente
          </Typography>
        </Box>
        <Typography variant="caption" color="text.secondary">
          {systems.length} {systems.length === 1 ? 'sistema monitorado' : 'sistemas monitorados'}
        </Typography>
      </Stack>
      <Stack spacing={1.1} p={2} sx={{ display: { xs: 'flex', md: 'none' } }}>
        {systems.map((system) => (
          <Box key={system.id} component="button" type="button" onClick={() => navigate(`/sistemas/${system.id}`)} sx={{ appearance: 'none', color: 'text.primary', textAlign: 'left', font: 'inherit', width: '100%', p: 1.6, borderRadius: 2, border: '1px solid', borderColor: 'divider', bgcolor: 'transparent', cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}>
            <Stack direction="row" justifyContent="space-between" gap={1} alignItems="flex-start"><Box><Typography variant="body2" fontWeight={700}>{system.name}</Typography><Typography variant="caption" color="text.secondary">{environmentLabels[system.environment]}</Typography></Box><SystemStatusChip status={system.status} active={system.active} /></Stack>
            <Typography variant="caption" display="block" mt={1} sx={{ overflowWrap: "anywhere" }}>{system.statusReason}</Typography>
            <Box display="grid" gridTemplateColumns="repeat(2,minmax(0,1fr))" gap={1.2} mt={1.5}><MobileMetric label="Disponibilidade" value={formatPercent(system.uptime)} /><MobileMetric label="Latência" value={formatLatency(system.latencyMs)} /><MobileMetric label="Última verificação" value={formatRelativeTime(system.lastCheckedAt)} /><MobileMetric label="Respostas HTTP" value={`${system.sparkline.length} recentes`} /></Box>
          </Box>
        ))}
      </Stack>
      {tableVisible && <TableContainer>
        <Table sx={{ minWidth: 760 }} aria-label="Saúde dos sistemas monitorados">
          <TableHead>
            <TableRow>
              {['Sistema', 'Estado', 'Disponibilidade', 'Última verificação', 'Latência', 'Tendência', ''].map((label) => (
                <TableCell
                  key={label || 'actions'}
                  align={['Disponibilidade', 'Latência', 'Tendência'].includes(label) ? 'right' : 'left'}
                  sx={{
                    color: 'text.secondary',
                    fontSize: '0.67rem',
                    fontWeight: 650,
                    letterSpacing: '0.045em',
                    textTransform: 'uppercase',
                    borderColor: 'divider',
                    py: 1.2,
                  }}
                >
                  {label}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {systems.map((system) => {
              const chartData = system.sparkline.map((value, index) => ({ index, value }));
              const color = statusColor[system.status];
              return (
                <TableRow
                  key={system.id}
                  hover
                  sx={{
                    '&:last-child td': { borderBottom: 0 },
                    '&:hover': { bgcolor: alpha(theme.palette.primary.main, 0.025) },
                  }}
                >
                  <TableCell sx={{ borderColor: 'divider', py: 1.45 }}>
                    <Stack direction="row" spacing={1.2} alignItems="center">
                      <Box
                        sx={{
                          width: 31,
                          height: 31,
                          borderRadius: 2,
                          display: 'grid',
                          placeItems: 'center',
                          bgcolor: alpha(theme.palette.primary.main, 0.09),
                          color: 'primary.main',
                          fontSize: '0.72rem',
                          fontWeight: 750,
                        }}
                      >
                        {system.name.slice(0, 2).toUpperCase()}
                      </Box>
                      <Box>
                        <Typography variant="body2" fontWeight={650}>{system.name}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {environmentLabels[system.environment]}
                        </Typography>
                      </Box>
                    </Stack>
                  </TableCell>
                  <TableCell sx={{ borderColor: 'divider' }}><SystemStatusChip status={system.status} active={system.active} /><Typography variant="caption" display="block" sx={{ maxWidth: 230 }}>{system.statusReason}</Typography>{system.statusChangedAt && system.status !== "OPERATIONAL" && <Typography variant="caption" color="text.secondary">Desde {new Date(system.statusChangedAt).toLocaleString("pt-BR")}</Typography>}</TableCell>
                  <TableCell align="right" sx={{ borderColor: 'divider', fontWeight: 650 }}>
                    {formatPercent(system.uptime)}
                  </TableCell>
                  <TableCell sx={{ borderColor: 'divider', color: 'text.secondary', fontSize: '0.78rem' }}>
                    {formatRelativeTime(system.lastCheckedAt)}
                  </TableCell>
                  <TableCell align="right" sx={{ borderColor: 'divider', fontWeight: 650 }}>
                    {formatLatency(system.latencyMs)}
                  </TableCell>
                  <TableCell align="right" sx={{ borderColor: 'divider' }}>
                    <Box width={88} height={30} ml="auto" aria-label={`Tendência de latência de ${system.name}`}>
                      {chartData.length ? (
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={chartData}>
                            <YAxis domain={['dataMin - 10', 'dataMax + 10']} hide />
                            <Line dataKey="value" type="monotone" stroke={color} strokeWidth={1.8} dot={false} isAnimationActive={false} />
                          </LineChart>
                        </ResponsiveContainer>
                      ) : <Typography color="text.secondary">—</Typography>}
                    </Box>
                  </TableCell>
                  <TableCell align="right" sx={{ borderColor: 'divider', width: 48 }}>
                    <Tooltip title="Ver detalhes">
                      <IconButton
                        size="small"
                        aria-label={`Ver detalhes de ${system.name}`}
                        onClick={() => navigate(`/sistemas/${system.id}`)}
                      >
                        <MoreHorizRoundedIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>}
    </Panel>
  );
}

function MobileMetric({ label, value }: { label: string; value: string }) {
  return <Box minWidth={0}><Typography variant="caption" color="text.secondary" display="block">{label}</Typography><Typography variant="body2" fontWeight={650} noWrap>{value}</Typography></Box>;
}
