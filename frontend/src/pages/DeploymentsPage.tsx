import { zodResolver } from '@hookform/resolvers/zod';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import CommitRoundedIcon from '@mui/icons-material/CommitRounded';
import RocketLaunchRoundedIcon from '@mui/icons-material/RocketLaunchRounded';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Skeleton,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Controller, useForm } from 'react-hook-form';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { z } from 'zod';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { isDemoMode } from '../config/demo';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { environmentLabels, formatRelativeTime } from '../components/dashboard/dashboardFormatters';
import { getApiErrorMessage } from '../services/api';
import { deploymentsService } from '../services/deploymentsService';
import { systemsService } from '../services/systemsService';
import type { Deployment, DeploymentInput, DeploymentStatus, Environment, MonitoredSystem } from '../types/api';

const statusLabel: Record<DeploymentStatus, string> = { PENDING: 'Pendente', RUNNING: 'Em execução', SUCCESS: 'Sucesso', FAILED: 'Falhou', ROLLED_BACK: 'Revertida' };
const statusColor: Record<DeploymentStatus, 'default' | 'info' | 'success' | 'error' | 'warning'> = { PENDING: 'default', RUNNING: 'info', SUCCESS: 'success', FAILED: 'error', ROLLED_BACK: 'warning' };

const deploymentSchema = z.object({
  systemId: z.string().uuid('Selecione um sistema.'),
  version: z.string().trim().min(1, 'Informe a versão.').max(80).regex(
    /^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/,
    'Use Semantic Versioning, por exemplo 1.4.2 ou v2.0.0-rc.1.',
  ),
  environment: z.enum(['PRODUCTION', 'STAGING', 'DEVELOPMENT']),
  commitHash: z.string().trim().refine((value) => !value || /^[a-fA-F0-9]{7,64}$/.test(value), 'Use um hash hexadecimal de 7 a 64 caracteres.').optional(),
  description: z.string().trim().max(2000).optional(),
});
type DeploymentForm = z.infer<typeof deploymentSchema>;

export function DeploymentsPage() {
  const { user } = useAuth();
  const [searchParams,setSearchParams] = useSearchParams();
  const focusedId=searchParams.get("implantacao");
  const canManage = !isDemoMode && (user?.role === 'ADMIN' || user?.role === 'DEVELOPER');
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [systems, setSystems] = useState<MonitoredSystem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [systemId, setSystemId] = useState('ALL');
  const [status, setStatus] = useState<DeploymentStatus | 'ALL'>('ALL');
  const [environment, setEnvironment] = useState<Environment | 'ALL'>('ALL');
  const [createOpen, setCreateOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { const [deploymentData, systemData] = await Promise.all([deploymentsService.list(), systemsService.list()]); setDeployments(deploymentData); setSystems(systemData); }
    catch (requestError) { setError(getApiErrorMessage(requestError)); } finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const filtered = useMemo(() => deployments.filter((deployment) =>
    (!focusedId || deployment.id === focusedId) && (systemId === 'ALL' || deployment.systemId === systemId)
    && (status === 'ALL' || deployment.status === status)
    && (environment === 'ALL' || deployment.environment === environment)), [deployments, systemId, status, environment, focusedId]);

  const create = async (input: DeploymentInput) => {
    const created = await deploymentsService.create(input); setDeployments((current) => [created, ...current]); setCreateOpen(false); setNotice('Deploy registrado.');
  };
  const transition = async (deployment: Deployment, action: 'start' | 'success' | 'failure' | 'rollback') => {
    try { const updated = await deploymentsService.transition(deployment.id, action, action === 'start' ? undefined : deployment.durationSeconds); setDeployments((current) => current.map((item) => item.id === updated.id ? updated : item)); setNotice('Estado da implantação atualizado.'); }
    catch (requestError) { setNotice(getApiErrorMessage(requestError)); }
  };

  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1500} mx="auto">
    <PageHeader title="Implantações" description="Mudanças de software correlacionadas à saúde operacional" eyebrow="Operações"
      actions={canManage && <Button variant="contained" startIcon={<AddRoundedIcon />} onClick={() => setCreateOpen(true)}>Registrar implantação</Button>} />
    {focusedId && <Button onClick={() => setSearchParams({})} sx={{ mb: 2 }}>Ver todas as implantações</Button>}
    <Panel sx={{ p: 2, mb: 2 }}><Stack direction={{ xs: 'column', md: 'row' }} gap={1.2}>
      <TextField select size="small" label="Sistema" value={systemId} onChange={(event) => setSystemId(event.target.value)} sx={{ minWidth: 190 }}><MenuItem value="ALL">Todos os sistemas</MenuItem>{systems.map((system) => <MenuItem key={system.id} value={system.id}>{system.name}</MenuItem>)}</TextField>
      <TextField select size="small" label="Ambiente" value={environment} onChange={(event) => setEnvironment(event.target.value as Environment | 'ALL')} sx={{ minWidth: 170 }}><MenuItem value="ALL">Todos</MenuItem><MenuItem value="PRODUCTION">Produção</MenuItem><MenuItem value="STAGING">Homologação</MenuItem><MenuItem value="DEVELOPMENT">Desenvolvimento</MenuItem></TextField>
      <TextField select size="small" label="Estado" value={status} onChange={(event) => setStatus(event.target.value as DeploymentStatus | 'ALL')} sx={{ minWidth: 170 }}><MenuItem value="ALL">Todos</MenuItem>{(Object.keys(statusLabel) as DeploymentStatus[]).map((value) => <MenuItem key={value} value={value}>{statusLabel[value]}</MenuItem>)}</TextField>
    </Stack></Panel>
    {loading && <Stack spacing={1.3}>{[1,2,3,4].map((item) => <Skeleton key={item} variant="rounded" height={128} />)}</Stack>}
    {!loading && error && <ViewState kind="error" title="Falha ao carregar implantações" description={error} actionLabel="Tentar novamente" onAction={() => void load()} />}
    {!loading && !error && filtered.length === 0 && <Panel><ViewState kind="empty" title="Nenhuma implantação encontrada" description="Nenhuma implantação recebida. Provedores automáticos de implantação ainda não estão configurados; você pode registrar uma implantação manual." /></Panel>}
    {!loading && !error && <Box position="relative" pl={{ xs: 0, md: 4 }} sx={{ '&:before': { content: '""', position: 'absolute', left: { xs: 14, md: 26 }, top: 18, bottom: 18, width: 1, bgcolor: 'divider' } }}><Stack spacing={1.5}>{filtered.map((deployment) => <Stack key={deployment.id} direction="row" spacing={2} alignItems="stretch" position="relative"><Box mt={2.4} zIndex={1} width={28} height={28} flex="0 0 28px" display="grid" sx={{ placeItems: 'center', borderRadius: '50%', bgcolor: deployment.status === 'SUCCESS' ? 'success.main' : deployment.status === 'FAILED' ? 'error.main' : 'background.paper', border: '2px solid', borderColor: deployment.status === 'RUNNING' ? 'info.main' : 'divider' }}><RocketLaunchRoundedIcon sx={{ fontSize: 14, color: deployment.status === 'SUCCESS' || deployment.status === 'FAILED' ? '#fff' : 'text.secondary' }} /></Box><Panel sx={{ p: 2.2, flex: 1 }}><Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1}><Box><Stack direction="row" gap={1} alignItems="center" flexWrap="wrap"><Typography variant="h3">{deployment.systemName} · v{deployment.version}</Typography><Chip size="small" label={statusLabel[deployment.status]} color={statusColor[deployment.status]} /></Stack><Typography variant="body2" color="text.secondary" mt={.65}>{environmentLabels[deployment.environment]} · {formatRelativeTime(deployment.deployedAt)}{deployment.durationSeconds != null ? ` · ${deployment.durationSeconds}s` : ''}</Typography></Box><Stack direction="row" gap={.7} flexWrap="wrap">{canManage && deployment.status === 'PENDING' && <Button size="small" variant="outlined" onClick={() => void transition(deployment, 'start')}>Iniciar</Button>}{canManage && deployment.status === 'RUNNING' && <><Button size="small" color="success" variant="outlined" onClick={() => void transition(deployment, 'success')}>Concluir</Button><Button size="small" color="error" variant="outlined" onClick={() => void transition(deployment, 'failure')}>Falhar</Button></>}{canManage && user?.role === 'ADMIN' && (deployment.status === 'SUCCESS' || deployment.status === 'FAILED') && <Button size="small" color="warning" variant="outlined" onClick={() => void transition(deployment, 'rollback')}>Reverter</Button>}</Stack></Stack>{deployment.description && <Typography variant="body2" mt={1.4}>{deployment.description}</Typography>}<Stack direction="row" gap={.6} alignItems="center" mt={1.3} flexWrap="wrap"><CommitRoundedIcon sx={{ fontSize: 15 }} color="disabled"/><Typography variant="caption" color="text.secondary" fontFamily="monospace">{deployment.commitHash ?? 'commit não informado'}</Typography><Typography variant="caption" color="text.secondary">· origem: {deployment.source === 'MANUAL' ? 'Registro manual' : deployment.source === 'LEGACY_API' ? 'Registro legado recebido via API' : deployment.source}</Typography></Stack></Panel></Stack>)}</Stack></Box>}
    <DeploymentCreateDialog open={createOpen} systems={systems} onClose={() => setCreateOpen(false)} onCreate={create} onError={setNotice} />
    <Snackbar open={Boolean(notice)} autoHideDuration={4200} onClose={() => setNotice(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}><Alert variant="filled" severity={notice?.includes('registrado') || notice?.includes('atualizado') ? 'success' : 'error'} onClose={() => setNotice(null)}>{notice}</Alert></Snackbar>
  </Box>;
}

function DeploymentCreateDialog({ open, systems, onClose, onCreate, onError }: { open: boolean; systems: MonitoredSystem[]; onClose: () => void; onCreate: (input: DeploymentInput) => Promise<void>; onError: (message: string) => void }) {
  const { register, control, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<DeploymentForm>({ resolver: zodResolver(deploymentSchema), defaultValues: { systemId: '', version: '', environment: 'PRODUCTION', commitHash: '', description: '' } });
  const submit = async (values: DeploymentForm) => { try { await onCreate(values); reset(); } catch (error) { onError(getApiErrorMessage(error)); } };
  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth><DialogTitle>Registrar implantação</DialogTitle><DialogContent dividers><Stack component="form" id="deployment-form" onSubmit={handleSubmit(submit)} spacing={2} pt={.5}><Controller name="systemId" control={control} render={({ field }) => <FormControl error={Boolean(errors.systemId)}><InputLabel>Sistema</InputLabel><Select {...field} label="Sistema">{systems.map((system) => <MenuItem key={system.id} value={system.id}>{system.name}</MenuItem>)}</Select>{errors.systemId && <FormHelperText>{errors.systemId.message}</FormHelperText>}</FormControl>} /><Stack direction={{ xs: 'column', sm: 'row' }} gap={2}><TextField label="Versão" placeholder="1.4.2" {...register('version')} error={Boolean(errors.version)} helperText={errors.version?.message} fullWidth/><TextField label="Commit" placeholder="a1b2c3d" {...register('commitHash')} error={Boolean(errors.commitHash)} helperText={errors.commitHash?.message} fullWidth/></Stack><Controller name="environment" control={control} render={({ field }) => <FormControl><InputLabel>Ambiente</InputLabel><Select {...field} label="Ambiente"><MenuItem value="PRODUCTION">Produção</MenuItem><MenuItem value="STAGING">Homologação</MenuItem><MenuItem value="DEVELOPMENT">Desenvolvimento</MenuItem></Select></FormControl>} /><TextField label="Descrição" {...register('description')} multiline minRows={3} error={Boolean(errors.description)} helperText={errors.description?.message}/></Stack></DialogContent><DialogActions><Button onClick={onClose}>Cancelar</Button><Button form="deployment-form" type="submit" variant="contained" disabled={isSubmitting}>Registrar</Button></DialogActions></Dialog>;
}
