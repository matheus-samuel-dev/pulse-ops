import CloudDoneRoundedIcon from '@mui/icons-material/CloudDoneRounded';
import DownloadRoundedIcon from '@mui/icons-material/DownloadRounded';
import EventNoteRoundedIcon from '@mui/icons-material/EventNoteRounded';
import ReportProblemRoundedIcon from '@mui/icons-material/ReportProblemRounded';
import ScienceRoundedIcon from '@mui/icons-material/ScienceRounded';
import {
  alpha,
  Box,
  Button,
  Chip,
  MenuItem,
  Skeleton, Pagination,
  Stack,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import { useCallback, useEffect, useState, useRef, type ComponentType } from 'react';
import type { SvgIconProps } from '@mui/material';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { environmentLabels, formatPercent, formatRelativeTime } from '../components/dashboard/dashboardFormatters';
import { getApiErrorMessage } from '../services/api';
import { systemsService } from '../services/systemsService';
import { reportsService } from '../services/reportsService';
import type { Environment, OperationalEvent, OperationalReport, MonitoredSystem } from '../types/api';
import { buildOperationalCsv } from '../utils/operationalCsv';

const eventLabel = { HEALTH_CHECK: 'Verificação', INCIDENT: 'Incidente', DEPLOYMENT: 'Implantação', QUALITY: 'Qualidade', SYSTEM: 'Sistema', ACCOUNT: 'Conta', INTEGRATION: 'Integração' } as const;
const impactColor = { INFO: '#56b6f7', SUCCESS: '#39c995', WARNING: '#f2b84b', CRITICAL: '#ef6673' } as const;

export function ReportsPage() {
  const theme = useTheme();
  const [period, setPeriod] = useState('7d');
  const [environment, setEnvironment] = useState<Environment | 'ALL'>('ALL');
  const [report, setReport] = useState<OperationalReport | null>(null);
  const [systemId, setSystemId] = useState('ALL');
  const [systems, setSystems] = useState<MonitoredSystem[]>([]);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => { systemsService.list().then(setSystems).catch(failure => setError(getApiErrorMessage(failure))); }, []);
  const requestVersion=useRef(0);
  const load = useCallback(async () => {
    const version=++requestVersion.current; setLoading(true); setError(null);
    try { const data=await reportsService.operational(period, environment === 'ALL' ? undefined : environment, systemId === 'ALL' ? undefined : systemId, page); if(version===requestVersion.current) setReport(data); }
    catch (requestError) { if(version===requestVersion.current) setError(getApiErrorMessage(requestError)); } finally { if(version===requestVersion.current) setLoading(false); }
  }, [period, environment, systemId, page]);
  useEffect(() => { void load(); }, [load]);

  const filteredFeed = report?.feed ?? [];

  const exportCsv = () => {
    if (!report) return;
    const csv = buildOperationalCsv(filteredFeed);
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `pulseops-relatorio-${report.period}.csv`;
    anchor.hidden = true;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
  };

  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1500} mx="auto">
    <PageHeader title="Relatórios" description="Uma leitura consolidada da operação, implantações e relatórios de testes" eyebrow="Inteligência operacional" actions={<><TextField select size="small" label="Período" value={period} onChange={(event) => { setPage(0); setPeriod(event.target.value); }} sx={{ minWidth: 150 }}><MenuItem value="24h">24 horas</MenuItem><MenuItem value="7d">7 dias</MenuItem><MenuItem value="30d">30 dias</MenuItem></TextField><TextField select size="small" label="Ambiente" value={environment} onChange={(event) => { setPage(0); setEnvironment(event.target.value as Environment | 'ALL'); }} sx={{ minWidth: 170 }}><MenuItem value="ALL">Todos</MenuItem><MenuItem value="PRODUCTION">Produção</MenuItem><MenuItem value="STAGING">Homologação</MenuItem><MenuItem value="DEVELOPMENT">Desenvolvimento</MenuItem></TextField><Button variant="outlined" startIcon={<DownloadRoundedIcon/>} onClick={exportCsv} disabled={!filteredFeed.length}>Exportar esta página</Button></>} />
    {loading && <><Box display="grid" gridTemplateColumns={{ xs:'1fr',sm:'repeat(2,1fr)',xl:'repeat(4,1fr)' }} gap={2}>{[1,2,3,4].map((item)=><Skeleton key={item} variant="rounded" height={140}/>)}</Box><Skeleton variant="rounded" height={420} sx={{mt:2}}/></>}
    {!loading && error && <ViewState kind="error" title="Falha ao gerar relatório" description={error} actionLabel="Tentar novamente" onAction={() => void load()}/>} 
    {!loading && !error && report && <Stack spacing={2}>
      <Panel sx={{ p:2.5 }}><Stack direction={{xs:'column',sm:'row'}} justifyContent="space-between" gap={2}><Box><Typography variant="h2">Janela analisada</Typography><Typography color="text.secondary" variant="body2" mt={.6}>{new Date(report.windowStart).toLocaleString('pt-BR')} — {new Date(report.windowEnd).toLocaleString('pt-BR')}</Typography></Box><Stack direction="row" gap={.8}><Chip label={report.period} color="primary" variant="outlined"/><Chip label={report.environment ? environmentLabels[report.environment] : 'Todos os ambientes'} variant="outlined"/></Stack></Stack></Panel>
      <Panel sx={{ p:2 }}><Stack direction={{xs:'column',lg:'row'}} gap={1.2} alignItems={{lg:'center'}}><Box flex={1}><Typography variant="body2" fontWeight={680}>Escopo do relatório</Typography><Typography variant="caption" color="text.secondary">Indicadores e histórico usam o mesmo sistema, ambiente e janela no backend.</Typography></Box><TextField select size="small" label="Sistema" value={systemId} onChange={(event)=>{ setPage(0); setSystemId(event.target.value); }} sx={{minWidth:190}}><MenuItem value="ALL">Todos os sistemas</MenuItem>{systems.map(system=><MenuItem key={system.id} value={system.id}>{system.name}</MenuItem>)}</TextField></Stack></Panel>
      <Box display="grid" gridTemplateColumns={{ xs:'1fr',sm:'repeat(2,1fr)',xl:'repeat(4,1fr)' }} gap={2}>
        <ReportMetric icon={CloudDoneRoundedIcon} label="Disponibilidade média" value={formatPercent(report.kpis.availability)} note={`${report.kpis.totalHealthChecks} verificações · mínimo de 5 válidas por sistema`} color={theme.palette.success.main}/>
        <ReportMetric icon={ReportProblemRoundedIcon} label="Incidentes ativos na janela" value={String(report.kpis.activeIncidents)} note={`${report.kpis.incidentsOpened} abertos na janela`} color={theme.palette.error.main}/>
        <ReportMetric icon={EventNoteRoundedIcon} label="Sucesso das implantações" value={formatPercent(report.kpis.deploymentSuccessRate)} note={`${report.kpis.successfulDeployments}/${report.kpis.deployments} implantações`} color={theme.palette.primary.main}/>
        <ReportMetric icon={ScienceRoundedIcon} label="Aprovação de testes" value={formatPercent(report.kpis.testPassRate)} note={`${report.kpis.passedTests}/${report.kpis.totalTests} testes`} color={theme.palette.secondary.main}/>
      </Box>
      <Box display="grid" gridTemplateColumns={{xs:'1fr',lg:'minmax(0,1.4fr) minmax(300px,.6fr)'}} gap={2}>
        <Panel sx={{ p:2.5 }}><Typography variant="h2">Histórico operacional</Typography><Typography variant="body2" color="text.secondary" mt={.5}>Eventos persistidos, do mais recente ao mais antigo</Typography><Stack mt={2.5} spacing={0}>{filteredFeed.length ? filteredFeed.map((event,index)=><EventRow key={`${event.type}-${event.id}`} event={event} last={index===filteredFeed.length-1}/>) : <ViewState kind="empty" title="Sem eventos" description="Nenhum evento corresponde aos filtros selecionados."/>}</Stack>{report.eventPages > 1 && <Pagination count={report.eventPages} page={report.eventPage + 1} onChange={(_, value) => setPage(value - 1)} sx={{ mt: 2 }} />}</Panel>
        <Stack spacing={2}><Panel sx={{p:2.5}}><Typography variant="h2">Cobertura média</Typography><Typography fontSize="2.3rem" fontWeight={760} mt={2}>{formatPercent(report.kpis.averageLineCoverage)}</Typography><Typography variant="body2" color="text.secondary">linhas · {formatPercent(report.kpis.averageBranchCoverage)} ramificações</Typography></Panel></Stack>
      </Box>
    </Stack>}
  </Box>;
}

function ReportMetric({ icon:Icon,label,value,note,color }:{icon:ComponentType<SvgIconProps>;label:string;value:string;note:string;color:string}) { return <Panel sx={{p:2.3}}><Stack direction="row" justifyContent="space-between"><Box><Typography variant="body2" color="text.secondary">{label}</Typography><Typography fontSize="1.8rem" fontWeight={750} mt={1}>{value}</Typography><Typography variant="caption" color="text.secondary">{note}</Typography></Box><Box width={36} height={36} display="grid" sx={{placeItems:'center',borderRadius:2,bgcolor:alpha(color,.11),color}}><Icon fontSize="small"/></Box></Stack></Panel>; }
function EventRow({event,last}:{event:OperationalEvent;last:boolean}) { const color=impactColor[event.impact]; return <Stack direction="row" spacing={1.5}><Box display="flex" flexDirection="column" alignItems="center"><Box width={10} height={10} borderRadius="50%" bgcolor={color} boxShadow={`0 0 0 5px ${color}18`}/>{!last&&<Box width="1px" flex={1} minHeight={58} bgcolor="divider" mt={.8}/>}</Box><Box pb={last?0:2.2} flex={1}><Stack direction="row" justifyContent="space-between" gap={1}><Box><Typography variant="body2" fontWeight={680}>{event.title}</Typography><Typography variant="caption" color="text.secondary">{event.systemName} · {eventLabel[event.type]} · {event.source}</Typography></Box><Typography variant="caption" color="text.secondary" whiteSpace="nowrap">{formatRelativeTime(event.occurredAt)}</Typography></Stack>{event.description&&<Typography variant="body2" color="text.secondary" mt={.65}>{event.description}</Typography>}<Chip size="small" label={event.status === "RECORDED" ? "Registrado" : event.status === "SUCCESS" ? "Sucesso" : event.status === "FAILED" ? "Falhou" : event.status === "OPEN" ? "Aberto" : event.status === "INVESTIGATING" ? "Investigando" : event.status === "RESOLVED" ? "Resolvido" : event.status === "RUNNING" ? "Em execução" : event.status === "PENDING" ? "Aguardando" : event.status === "EXCELLENT" ? "Excelente" : event.status === "GOOD" ? "Boa" : event.status === "WARNING" ? "Atenção" : event.status === "CRITICAL" ? "Crítico" : event.status} variant="outlined" sx={{mt:.8,color,borderColor:alpha(color,.35)}}/></Box></Stack>; }
