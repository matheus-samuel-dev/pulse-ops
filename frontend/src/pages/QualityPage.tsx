import ScienceRoundedIcon from '@mui/icons-material/ScienceRounded';
import VerifiedRoundedIcon from '@mui/icons-material/VerifiedRounded';
import {
  alpha,
  Box,
  Chip,
  MenuItem,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import { useCallback, useEffect, useState } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { formatPercent } from '../components/dashboard/dashboardFormatters';
import { getApiErrorMessage } from '../services/api';
import { qualityService } from '../services/qualityService';
import type { QualityClassification, QualityOverview, QualityReport } from '../types/api';

const classificationLabel: Record<QualityClassification, string> = { EXCELLENT: 'Excelente', GOOD: 'Boa', WARNING: 'Atenção', CRITICAL: 'Crítica', NO_DATA: 'Sem dados' };
const classificationColor: Record<QualityClassification, 'success' | 'info' | 'warning' | 'error' | 'default'> = { EXCELLENT: 'success', GOOD: 'info', WARNING: 'warning', CRITICAL: 'error', NO_DATA: 'default' };

export function QualityPage() {
  const theme = useTheme();
  const [period, setPeriod] = useState('30d');
  const [overview, setOverview] = useState<QualityOverview | null>(null);
  const [history, setHistory] = useState<QualityReport[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { const [overviewData, historyData] = await Promise.all([qualityService.overview(period), qualityService.history(period)]); setOverview(overviewData); setHistory(historyData); }
    catch (requestError) { setError(getApiErrorMessage(requestError)); } finally { setLoading(false); }
  }, [period]);
  useEffect(() => { void load(); }, [load]);

  const chartData = history.map((report) => ({
    ...report,
    label: new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' }).format(new Date(report.generatedAt)),
  }));

  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1580} mx="auto">
    <PageHeader title="Qualidade" description="Evidências de testes e índice técnico: 60% linhas + 40% ramificações" eyebrow="Qualidade de software"
      actions={<TextField select size="small" label="Histórico" value={period} onChange={(event) => setPeriod(event.target.value)} sx={{ minWidth: 170 }}><MenuItem value="24h">24 horas</MenuItem><MenuItem value="7d">7 dias</MenuItem><MenuItem value="30d">30 dias</MenuItem></TextField>} />
    {loading && <><Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2,1fr)', xl: 'repeat(5,1fr)' }} gap={2}>{[1,2,3,4,5].map((item) => <Skeleton key={item} variant="rounded" height={135}/>)}</Box><Skeleton variant="rounded" height={360} sx={{ mt: 2 }}/></>}
    {!loading && error && <ViewState kind="error" title="Falha ao carregar qualidade" description={error} actionLabel="Tentar novamente" onAction={() => void load()} />}
    {!loading && !error && overview?.systemsWithReports === 0 && <ViewState kind="empty" title="Nenhum relatório recebido" description="A cobertura de testes só aparece após um pipeline enviar um relatório real pela API de qualidade. Auditorias web são acompanhadas em Integrações." />}
    {!loading && !error && overview && overview.systemsWithReports > 0 && <Stack spacing={2}>
      <Panel sx={{ p: { xs: 2.5, md: 3 }, overflow: 'hidden', position: 'relative', borderColor: alpha(theme.palette.secondary.main,.3) }}>
        <Box position="absolute" width={280} height={280} borderRadius="50%" right={-100} top={-150} bgcolor={alpha(theme.palette.secondary.main,.07)} />
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" gap={3} position="relative"><Box><Stack direction="row" gap={1} alignItems="center"><ScienceRoundedIcon color="secondary"/><Typography variant="h2">Índice dos relatórios recebidos</Typography></Stack><Typography color="text.secondary" mt={.8} maxWidth={650}>Último relatório de cada sistema dentro da janela selecionada. Origem: importação pela API de qualidade. Este índice não representa auditoria web.</Typography><Stack direction="row" flexWrap="wrap" gap={.8} mt={2}></Stack></Box><Stack alignItems={{ xs: 'flex-start', md: 'flex-end' }}><Stack direction="row" gap={.7} alignItems="center"><VerifiedRoundedIcon color="secondary"/><Typography variant="body2" fontWeight={750}>{classificationLabel[overview.classification]}</Typography></Stack><Typography fontSize="3rem" fontWeight={780} letterSpacing="-.06em" mt={1}>{formatPercent(overview.averageCoverageScore)}</Typography><Typography variant="caption" color="text.secondary">índice de cobertura ponderado</Typography></Stack></Stack>
      </Panel>
      <Box display="grid" gridTemplateColumns={{ xs: '1fr', sm: 'repeat(2,1fr)', xl: 'repeat(5,1fr)' }} gap={2}>
        <QualityMetric label="Total de testes" value={overview.totalTests.toLocaleString('pt-BR')} color={theme.palette.primary.main}/><QualityMetric label="Aprovados" value={overview.passedTests.toLocaleString('pt-BR')} color={theme.palette.success.main}/><QualityMetric label="Falharam" value={overview.failedTests.toLocaleString('pt-BR')} color={theme.palette.error.main}/><QualityMetric label="Ignorados" value={overview.skippedTests.toLocaleString('pt-BR')} color={theme.palette.warning.main}/><QualityMetric label="Taxa de aprovação" value={formatPercent(overview.passRate)} color={theme.palette.secondary.main}/>
      </Box>
      <Box display="grid" gridTemplateColumns="minmax(0,1fr)" gap={2}>
        <Panel sx={{ p: 2.5 }}><Typography variant="h2">Histórico de cobertura</Typography><Typography variant="body2" color="text.secondary" mt={.45}>Linhas, ramificações e índice por relatório</Typography><Box height={310} mt={2}>{chartData.length ? <ResponsiveContainer width="100%" height="100%"><LineChart data={chartData}><CartesianGrid stroke={theme.palette.divider} vertical={false} strokeDasharray="4 5"/><XAxis dataKey="label" axisLine={false} tickLine={false} tick={{ fill: theme.palette.text.secondary, fontSize: 11 }}/><YAxis domain={[0,100]} axisLine={false} tickLine={false} tick={{ fill: theme.palette.text.secondary, fontSize: 11 }}/><Tooltip contentStyle={{ background: theme.palette.background.paper, border: `1px solid ${theme.palette.divider}`, borderRadius: 10 }}/><Line type="monotone" dataKey="lineCoverage" name="Linhas" stroke={theme.palette.primary.main} strokeWidth={2.2} dot={false}/><Line type="monotone" dataKey="branchCoverage" name="Ramificações" stroke={theme.palette.secondary.main} strokeWidth={2} dot={false}/><Line type="monotone" dataKey="coverageScore" name="Índice" stroke={theme.palette.success.main} strokeDasharray="5 4" dot={false}/></LineChart></ResponsiveContainer> : <ViewState kind="empty" title="Sem histórico no período" description="Novos relatórios aparecerão neste gráfico." />}</Box></Panel>
      </Box>
      <Panel sx={{ overflow: 'hidden' }}><Box p={2.5}><Typography variant="h2">Qualidade por sistema</Typography><Typography variant="body2" color="text.secondary" mt={.45}>{overview.systemsWithReports} de {overview.monitoredSystems} sistemas com relatório</Typography></Box><TableContainer><Table sx={{ minWidth: 760 }}><TableHead><TableRow><TableCell>Sistema</TableCell><TableCell>Classificação</TableCell><TableCell align="right">Testes</TableCell><TableCell align="right">Aprovação</TableCell><TableCell align="right">Linhas</TableCell><TableCell align="right">Ramificações</TableCell><TableCell align="right">Índice</TableCell></TableRow></TableHead><TableBody>{overview.systems.map((report) => <TableRow key={report.reportId} hover><TableCell><Typography variant="body2" fontWeight={650}>{report.systemName}</Typography><Typography variant="caption" color="text.secondary">{new Date(report.generatedAt).toLocaleString('pt-BR')} · {report.source === 'API_IMPORT' ? 'Importado via API' : report.source}</Typography></TableCell><TableCell><Chip size="small" label={classificationLabel[report.classification]} color={classificationColor[report.classification]} variant="outlined"/></TableCell><TableCell align="right">{report.totalTests}</TableCell><TableCell align="right">{formatPercent(report.passRate)}</TableCell><TableCell align="right">{formatPercent(report.lineCoverage)}</TableCell><TableCell align="right">{formatPercent(report.branchCoverage)}</TableCell><TableCell align="right"><b>{formatPercent(report.coverageScore)}</b></TableCell></TableRow>)}</TableBody></Table></TableContainer></Panel>
    </Stack>}
  </Box>;
}

function QualityMetric({ label, value, color }: { label: string; value: string; color: string }) {
  return <Panel sx={{ p: 2.2 }}><Box width={28} height={4} borderRadius={4} bgcolor={color}/><Typography variant="body2" color="text.secondary" mt={1.7}>{label}</Typography><Typography fontSize="1.8rem" fontWeight={750} mt={.7}>{value}</Typography></Panel>;
}
