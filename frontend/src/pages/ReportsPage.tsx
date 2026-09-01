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
  Skeleton,
  Stack,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import { useCallback, useEffect, useState, type ComponentType } from 'react';
import type { SvgIconProps } from '@mui/material';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { environmentLabels, formatPercent, formatRelativeTime } from '../components/dashboard/dashboardFormatters';
import { getApiErrorMessage } from '../services/api';
import { reportsService } from '../services/reportsService';
import type { Environment, OperationalEvent, OperationalReport } from '../types/api';

const eventLabel = { HEALTH_CHECK: 'Health check', INCIDENT: 'Incidente', DEPLOYMENT: 'Deploy', QUALITY: 'Qualidade' } as const;
const impactColor = { INFO: '#56b6f7', SUCCESS: '#39c995', WARNING: '#f2b84b', CRITICAL: '#ef6673' } as const;

export function ReportsPage() {
  const theme = useTheme();
  const [period, setPeriod] = useState('7d');
  const [environment, setEnvironment] = useState<Environment | 'ALL'>('ALL');
  const [report, setReport] = useState<OperationalReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { setReport(await reportsService.operational(period, environment === 'ALL' ? undefined : environment)); }
    catch (requestError) { setError(getApiErrorMessage(requestError)); } finally { setLoading(false); }
  }, [period, environment]);
  useEffect(() => { void load(); }, [load]);

  const exportCsv = () => {
    if (!report) return;
    const rows = [['data','tipo','sistema','ambiente','titulo','status'], ...report.feed.map((event) => [event.occurredAt,event.type,event.systemName,event.environment,event.title,event.status])];
    const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"','""')}"`).join(',')).join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = `pulseops-relatorio-${report.period}.csv`; anchor.click(); URL.revokeObjectURL(url);
  };

  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1500} mx="auto">
    <PageHeader title="Relatórios" description="Uma leitura consolidada da operação, delivery e qualidade" eyebrow="Inteligência operacional" actions={<><TextField select size="small" label="Período" value={period} onChange={(event) => setPeriod(event.target.value)} sx={{ minWidth: 150 }}><MenuItem value="24h">24 horas</MenuItem><MenuItem value="7d">7 dias</MenuItem><MenuItem value="30d">30 dias</MenuItem></TextField><TextField select size="small" label="Ambiente" value={environment} onChange={(event) => setEnvironment(event.target.value as Environment | 'ALL')} sx={{ minWidth: 170 }}><MenuItem value="ALL">Todos</MenuItem><MenuItem value="PRODUCTION">Produção</MenuItem><MenuItem value="STAGING">Staging</MenuItem><MenuItem value="DEVELOPMENT">Desenvolvimento</MenuItem></TextField><Button variant="outlined" startIcon={<DownloadRoundedIcon/>} onClick={exportCsv} disabled={!report}>Exportar CSV</Button></>} />
    {loading && <><Box display="grid" gridTemplateColumns={{ xs:'1fr',sm:'repeat(2,1fr)',xl:'repeat(4,1fr)' }} gap={2}>{[1,2,3,4].map((item)=><Skeleton key={item} variant="rounded" height={140}/>)}</Box><Skeleton variant="rounded" height={420} sx={{mt:2}}/></>}
    {!loading && error && <ViewState kind="error" title="Falha ao gerar relatório" description={error} actionLabel="Tentar novamente" onAction={() => void load()}/>} 
    {!loading && report && <Stack spacing={2}>
      <Panel sx={{ p:2.5 }}><Stack direction={{xs:'column',sm:'row'}} justifyContent="space-between" gap={2}><Box><Typography variant="h2">Janela analisada</Typography><Typography color="text.secondary" variant="body2" mt={.6}>{new Date(report.windowStart).toLocaleString('pt-BR')} — {new Date(report.windowEnd).toLocaleString('pt-BR')}</Typography></Box><Stack direction="row" gap={.8}><Chip label={report.period} color="primary" variant="outlined"/><Chip label={report.environment ? environmentLabels[report.environment] : 'Todos os ambientes'} variant="outlined"/></Stack></Stack></Panel>
      <Box display="grid" gridTemplateColumns={{ xs:'1fr',sm:'repeat(2,1fr)',xl:'repeat(4,1fr)' }} gap={2}>
        <ReportMetric icon={CloudDoneRoundedIcon} label="Disponibilidade" value={formatPercent(report.kpis.availability)} note={`${report.kpis.successfulHealthChecks}/${report.kpis.totalHealthChecks} checks`} color={theme.palette.success.main}/>
        <ReportMetric icon={ReportProblemRoundedIcon} label="Incidentes ativos" value={String(report.kpis.activeIncidents)} note={`${report.kpis.incidentsOpened} abertos na janela`} color={theme.palette.error.main}/>
        <ReportMetric icon={EventNoteRoundedIcon} label="Sucesso de deploy" value={formatPercent(report.kpis.deploymentSuccessRate)} note={`${report.kpis.successfulDeployments}/${report.kpis.deployments} deploys`} color={theme.palette.primary.main}/>
        <ReportMetric icon={ScienceRoundedIcon} label="Aprovação de testes" value={formatPercent(report.kpis.testPassRate)} note={`${report.kpis.passedTests}/${report.kpis.totalTests} testes`} color={theme.palette.secondary.main}/>
      </Box>
      <Box display="grid" gridTemplateColumns={{xs:'1fr',lg:'minmax(0,1.4fr) minmax(300px,.6fr)'}} gap={2}>
        <Panel sx={{ p:2.5 }}><Typography variant="h2">Histórico operacional</Typography><Typography variant="body2" color="text.secondary" mt={.5}>Eventos correlacionados, do mais recente ao mais antigo</Typography><Stack mt={2.5} spacing={0}>{report.feed.length ? report.feed.map((event,index)=><EventRow key={`${event.type}-${event.id}`} event={event} last={index===report.feed.length-1}/>) : <ViewState kind="empty" title="Sem eventos" description="Nenhum evento foi registrado nesta janela."/>}</Stack></Panel>
        <Stack spacing={2}><Panel sx={{p:2.5}}><Typography variant="h2">Saúde da frota</Typography><Stack mt={2.2} spacing={1.4}><Bar label="Operacionais" value={report.kpis.operationalSystems} total={report.kpis.monitoredSystems} color={theme.palette.success.main}/><Bar label="Degradados" value={report.kpis.degradedSystems} total={report.kpis.monitoredSystems} color={theme.palette.warning.main}/><Bar label="Indisponíveis" value={report.kpis.downSystems} total={report.kpis.monitoredSystems} color={theme.palette.error.main}/><Bar label="Sem dados" value={report.kpis.unknownSystems} total={report.kpis.monitoredSystems} color={theme.palette.text.disabled}/></Stack></Panel><Panel sx={{p:2.5}}><Typography variant="h2">Cobertura média</Typography><Typography fontSize="2.3rem" fontWeight={760} mt={2}>{formatPercent(report.kpis.averageLineCoverage)}</Typography><Typography variant="body2" color="text.secondary">linhas · {formatPercent(report.kpis.averageBranchCoverage)} branches</Typography></Panel></Stack>
      </Box>
    </Stack>}
  </Box>;
}

function ReportMetric({ icon:Icon,label,value,note,color }:{icon:ComponentType<SvgIconProps>;label:string;value:string;note:string;color:string}) { return <Panel sx={{p:2.3}}><Stack direction="row" justifyContent="space-between"><Box><Typography variant="body2" color="text.secondary">{label}</Typography><Typography fontSize="1.8rem" fontWeight={750} mt={1}>{value}</Typography><Typography variant="caption" color="text.secondary">{note}</Typography></Box><Box width={36} height={36} display="grid" sx={{placeItems:'center',borderRadius:2,bgcolor:alpha(color,.11),color}}><Icon fontSize="small"/></Box></Stack></Panel>; }
function EventRow({event,last}:{event:OperationalEvent;last:boolean}) { const color=impactColor[event.impact]; return <Stack direction="row" spacing={1.5}><Box display="flex" flexDirection="column" alignItems="center"><Box width={10} height={10} borderRadius="50%" bgcolor={color} boxShadow={`0 0 0 5px ${color}18`}/>{!last&&<Box width="1px" flex={1} minHeight={58} bgcolor="divider" mt={.8}/>}</Box><Box pb={last?0:2.2} flex={1}><Stack direction="row" justifyContent="space-between" gap={1}><Box><Typography variant="body2" fontWeight={680}>{event.title}</Typography><Typography variant="caption" color="text.secondary">{event.systemName} · {eventLabel[event.type]}</Typography></Box><Typography variant="caption" color="text.secondary" whiteSpace="nowrap">{formatRelativeTime(event.occurredAt)}</Typography></Stack>{event.description&&<Typography variant="body2" color="text.secondary" mt={.65}>{event.description}</Typography>}<Chip size="small" label={event.status} variant="outlined" sx={{mt:.8,color,borderColor:alpha(color,.35)}}/></Box></Stack>; }
function Bar({label,value,total,color}:{label:string;value:number;total:number;color:string}) { const percentage=total?value/total*100:0; return <Box><Stack direction="row" justifyContent="space-between"><Typography variant="body2" color="text.secondary">{label}</Typography><Typography variant="body2" fontWeight={700}>{value}</Typography></Stack><Box height={6} bgcolor="action.hover" borderRadius={4} mt={.7}><Box height="100%" width={`${percentage}%`} bgcolor={color} borderRadius={4}/></Box></Box>; }
