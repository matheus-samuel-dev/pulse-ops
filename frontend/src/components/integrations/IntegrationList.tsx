import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import { Box, Button, InputAdornment, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography, useMediaQuery, useTheme } from '@mui/material';
import { useMemo, useState } from 'react';
import { Panel } from '../common/Panel';
import { ViewState } from '../common/ViewState';
import { formatLatency } from '../dashboard/dashboardFormatters';
import type { Integration, IntegrationStatus } from '../../types/integrations';
import { integrationLabels } from './integrationPresentation';
import { IntegrationHealth, IntegrationIcon, IntegrationMetric, IntegrationStatusBadge, IntegrationTime } from './IntegrationPrimitives';
import { filterIntegrations, type IntegrationSort } from './filterIntegrations';

export function IntegrationList({ integrations, onDetails }: { integrations: Integration[]; onDetails: (id: string) => void }) {
  const theme = useTheme();
  const table = useMediaQuery(theme.breakpoints.up('md'));
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<IntegrationStatus | 'ALL'>('ALL');
  const [sort, setSort] = useState<IntegrationSort>('name');
  const filtered = useMemo(() => filterIntegrations(integrations, query, status, sort), [integrations, query, status, sort]);
  const identity = (item: Integration) => <Stack direction="row" spacing={1.25} alignItems="center"><IntegrationIcon id={item.id} /><Box minWidth={0}><Typography variant="body2" fontWeight={650}>{item.name}</Typography><Typography variant="caption" color="text.secondary" display="block" mt={0.3}>{item.description}</Typography></Box></Stack>;
  const details = (item: Integration) => <Button size="small" variant="outlined" onClick={() => onDetails(item.id)} aria-label={`Detalhes de ${item.name}`} sx={{ minHeight: 44 }}>Detalhes</Button>;
  return <Box component="section" aria-labelledby="integration-list-title" minWidth={0}>
    <Typography variant="h2" id="integration-list-title" mb={2}>Todos os sistemas integrados</Typography>
    <Stack direction={{ xs: 'column', sm: 'row' }} gap={1.25} mb={1.5}>
      <TextField label="Buscar integração" size="small" value={query} onChange={event => setQuery(event.target.value)} sx={{ flex: 1, minWidth: 0 }}
        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRoundedIcon fontSize="small" /></InputAdornment> } }} />
      <TextField select label="Status" size="small" value={status} onChange={event => setStatus(event.target.value as IntegrationStatus | 'ALL')} sx={{ minWidth: 145 }}>
        <MenuItem value="ALL">Todos</MenuItem>{Object.entries(integrationLabels).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
      </TextField>
      <TextField select label="Ordenar por" size="small" value={sort} onChange={event => setSort(event.target.value as IntegrationSort)} sx={{ minWidth: 140 }}>
        <MenuItem value="name">Nome</MenuItem><MenuItem value="sync">Última sincronização</MenuItem><MenuItem value="health">Saúde</MenuItem><MenuItem value="status">Status</MenuItem>
      </TextField>
    </Stack>
    <Typography variant="caption" color="text.secondary" display="block" mb={1.25} aria-live="polite">{filtered.length} de {integrations.length} sistemas · Saúde: sucesso dos checks em 24h</Typography>
    {filtered.length === 0 ? <Panel><ViewState kind="empty" title="Nenhuma integração encontrada" description="Ajuste a busca ou o filtro de status para encontrar um sistema." /></Panel> : table ?
      <Panel sx={{ overflow: 'hidden' }}><Table aria-label="Sistemas integrados" size="small" sx={{ tableLayout: 'fixed', '& td, & th': { borderColor: 'divider', px: 1.5, py: 1.75 }, '& th': { color: 'text.secondary', fontSize: '0.75rem' } }}>
        <TableHead><TableRow><TableCell sx={{ width: '28%' }}>Sistema</TableCell><TableCell sx={{ width: '19%' }}>Status</TableCell><TableCell sx={{ width: '16%' }}>Última sincronização</TableCell><TableCell sx={{ width: '11%' }}>Saúde</TableCell><TableCell sx={{ width: '12%' }}>Resposta</TableCell><TableCell sx={{ width: '14%' }}>Ações</TableCell></TableRow></TableHead>
        <TableBody>{filtered.map(item => <TableRow key={item.id} hover sx={{ bgcolor: item.primary ? 'action.selected' : undefined }}><TableCell>{identity(item)}</TableCell><TableCell><IntegrationStatusBadge integration={item} /></TableCell><TableCell><IntegrationTime value={item.lastSuccessfulSyncAt} /></TableCell><TableCell><IntegrationHealth integration={item} /></TableCell><TableCell>{formatLatency(item.responseTimeMs)}</TableCell><TableCell>{details(item)}</TableCell></TableRow>)}</TableBody>
      </Table></Panel> : <Stack spacing={1.5}>{filtered.map(item => <Panel key={item.id} sx={{ p: 2 }}>
        {identity(item)}<Box mt={1.5}><IntegrationStatusBadge integration={item} /></Box>
        <Box component="dl" display="grid" gridTemplateColumns="repeat(2,minmax(0,1fr))" gap={2} my={2}>
          <IntegrationMetric label="Última sincronização"><IntegrationTime value={item.lastSuccessfulSyncAt} /></IntegrationMetric><IntegrationMetric label="Saúde · 24h"><IntegrationHealth integration={item} /></IntegrationMetric><IntegrationMetric label="Tempo de resposta">{formatLatency(item.responseTimeMs)}</IntegrationMetric><IntegrationMetric label="Última verificação"><IntegrationTime value={item.lastCheckedAt} /></IntegrationMetric>
        </Box>{details(item)}
      </Panel>)}</Stack>}
  </Box>;
}
