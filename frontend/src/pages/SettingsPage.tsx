import { zodResolver } from '@hookform/resolvers/zod';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DarkModeRoundedIcon from '@mui/icons-material/DarkModeRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import LightModeRoundedIcon from '@mui/icons-material/LightModeRounded';
import SecurityRoundedIcon from '@mui/icons-material/SecurityRounded';
import {
  Alert, Avatar, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, FormControl, FormHelperText, InputLabel, MenuItem, Select, Skeleton, Snackbar,
  Stack, Switch, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField,
  Typography, useMediaQuery, useTheme,
} from '@mui/material';
import { Controller, useForm } from 'react-hook-form';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { isDemoMode } from '../config/demo';
import { getApiErrorMessage } from '../services/api';
import { api } from '../services/api';
import { usersService } from '../services/usersService';
import { useColorMode } from '../theme/PulseOpsThemeProvider';
import type { User, UserInput, UserRole } from '../types/api';

const roleLabel: Record<UserRole, string> = {
  ADMIN: 'Administrador', DEVELOPER: 'Desenvolvedor', VIEWER: 'Visualizador',
};
const userSchema = z.object({
  name: z.string().trim().min(2, 'Informe ao menos 2 caracteres.').max(120, 'Use no máximo 120 caracteres.'),
  email: z.email('Informe um e-mail válido.'),
  password: z.string().min(8, 'Use ao menos 8 caracteres.').max(72),
  role: z.enum(['ADMIN', 'DEVELOPER', 'VIEWER']),
});
type UserForm = z.infer<typeof userSchema>;

export function SettingsPage() {
  const navigate = useNavigate();
  const theme = useTheme();
  const mobile = useMediaQuery(theme.breakpoints.down('sm'));
  const { user } = useAuth();
  const { mode, toggleColorMode } = useColorMode();
  const [monitoring, setMonitoring] = useState<{ enabled: boolean; intervalMs: number; failuresToOpen: number; successesToResolve: number } | null>(null);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(user?.role === 'ADMIN');
  const [error, setError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [removeUser, setRemoveUser] = useState<User | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [removing, setRemoving] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    api.get('/settings/monitoring', { signal: controller.signal })
      .then(response => setMonitoring(response.data))
      .catch(failure => { if (!controller.signal.aborted) setError(getApiErrorMessage(failure)); });
    return () => controller.abort();
  }, []);

  const loadUsers = useCallback(async () => {
    if (user?.role !== 'ADMIN') return;
    setLoading(true); setError(null);
    try { setUsers(await usersService.list()); }
    catch (requestError) { setError(getApiErrorMessage(requestError)); }
    finally { setLoading(false); }
  }, [user?.role]);
  useEffect(() => { void loadUsers(); }, [loadUsers]);

  const create = async (input: UserInput) => {
    const created = await usersService.create(input);
    setUsers((current) => [...current, created]);
    setDialogOpen(false);
    setNotice('Usuário criado com sucesso.');
  };
  const remove = async () => {
    if (!removeUser) return;
    setRemoving(true);
    try {
      await usersService.remove(removeUser.id);
      setUsers((current) => current.filter((item) => item.id !== removeUser.id));
      setRemoveUser(null);
      setNotice('Usuário removido.');
    } catch (requestError) { setNotice(getApiErrorMessage(requestError)); }
    finally { setRemoving(false); }
  };

  const memberCard = (member: User) => (
    <Box key={member.id} p={1.7} border="1px solid" borderColor="divider" borderRadius={2}>
      <Stack direction="row" justifyContent="space-between" gap={1}>
        <Box minWidth={0}><Typography variant="body2" fontWeight={700}>{member.name}</Typography><Typography variant="caption" color="text.secondary" noWrap display="block">{member.email}</Typography></Box>
        <Chip size="small" label={roleLabel[member.role]} variant="outlined" color={member.role === 'ADMIN' ? 'primary' : 'default'} />
      </Stack>
      <Stack direction="row" alignItems="center" justifyContent="space-between" mt={1.3}>
        <Typography variant="caption" color="text.secondary">{member.createdAt ? new Date(member.createdAt).toLocaleDateString('pt-BR') : 'Data indisponível'}</Typography>
        {!isDemoMode && <Button color="error" size="small" startIcon={<DeleteOutlineRoundedIcon />} disabled={member.id === user?.id} onClick={() => setRemoveUser(member)}>Remover</Button>}
      </Stack>
    </Box>
  );

  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1300} mx="auto">
    <PageHeader title="Configurações" description="Conta, aparência e parâmetros operacionais" eyebrow="Área de trabalho" />
    {isDemoMode && <Alert severity="info" variant="outlined" sx={{ mb: 2 }}>O ambiente público é demonstrativo e somente leitura. Preferências locais de aparência continuam disponíveis.</Alert>}
    <Box display="grid" gridTemplateColumns={{ xs: 'minmax(0,1fr)', lg: user?.role === 'ADMIN' ? 'minmax(0,.65fr) minmax(0,1.35fr)' : 'minmax(0,1fr)' }} gap={2}>
      <Stack spacing={user?.role === 'ADMIN' ? 2 : undefined} minWidth={0} sx={user?.role === 'ADMIN' ? {} : { display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'repeat(2,minmax(0,1fr))' }, gap: 2 }}>
        <Panel sx={{ p: 2.5 }}>
          <Stack direction="row" justifyContent="space-between"><Typography variant="h2">Seu perfil</Typography><Button onClick={() => navigate('/perfil')}>Editar perfil</Button></Stack>
          <Stack direction="row" alignItems="center" gap={1.5} mt={2.4}><Avatar sx={{ width: 50, height: 50, bgcolor: 'primary.dark' }}>{user?.name.split(' ').map((part) => part[0]).slice(0, 2).join('')}</Avatar><Box minWidth={0}><Typography fontWeight={700}>{user?.name}</Typography><Typography variant="body2" color="text.secondary" noWrap>{user?.email}</Typography><Chip size="small" label={user ? roleLabel[user.role] : ''} variant="outlined" sx={{ mt: .7 }} /></Box></Stack>
        </Panel>
        <Panel sx={{ p: 2.5 }}>
          <Typography variant="h2">Aparência</Typography><Typography variant="body2" color="text.secondary" mt={.55}>Tema aplicado em toda a interface e salvo neste dispositivo.</Typography>
          <Stack direction="row" justifyContent="space-between" alignItems="center" mt={2.2}><Stack direction="row" gap={1} alignItems="center">{mode === 'dark' ? <DarkModeRoundedIcon color="primary" /> : <LightModeRoundedIcon color="warning" />}<Typography variant="body2">Modo {mode === 'dark' ? 'escuro' : 'claro'}</Typography></Stack><Switch checked={mode === 'dark'} onChange={toggleColorMode} slotProps={{ input: { 'aria-label': 'Alternar tema claro e escuro' } }} /></Stack>
        </Panel>
        <Panel sx={{ p: 2.5 }}>
          <Stack direction="row" gap={1} alignItems="center"><SecurityRoundedIcon color="success" /><Typography variant="h2">Monitoramento</Typography></Stack>
          <Stack spacing={1.2} mt={2}>{monitoring ? <><SecurityLine label="Automático" value={monitoring.enabled ? "Ativo" : "Pausado"} /><SecurityLine label="Intervalo entre ciclos" value={`${monitoring.intervalMs / 1000} segundos`} /><SecurityLine label="Abrir incidente" value={`${monitoring.failuresToOpen} falhas consecutivas`} /><SecurityLine label="Resolver incidente" value={`${monitoring.successesToResolve} respostas saudáveis`} /></> : <Typography variant="body2">{error ?? "Carregando parâmetros…"}</Typography>}<Typography variant="body2" color="text.secondary">Tempo limite, endpoint e limite de latência são definidos por sistema. O intervalo automático é configurado na implantação.</Typography><Button onClick={() => navigate("/sistemas")}>Configurar sistemas</Button></Stack>
        </Panel>
      </Stack>
      {user?.role === 'ADMIN' && <Panel sx={{ overflow: 'hidden' }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1} p={2.5}><Box><Typography variant="h2">Equipe e permissões</Typography><Typography variant="body2" color="text.secondary" mt={.45}>{isDemoMode ? 'Acesso exibido em modo somente leitura' : user?.role === 'ADMIN' ? 'Gerencie o acesso ao ambiente de trabalho' : 'Disponível apenas para administradores'}</Typography></Box>{user?.role === 'ADMIN' && !isDemoMode && <Button startIcon={<AddRoundedIcon />} variant="contained" onClick={() => setDialogOpen(true)}>Adicionar pessoa</Button>}</Stack>
        {user?.role !== 'ADMIN' ? <Box px={2.5} pb={3}><Alert severity="info">Seu perfil não possui permissão para gerenciar usuários.</Alert></Box>
          : loading ? <Box p={2.5}><Skeleton height={300} /></Box>
          : error ? <Box p={2.5}><Alert severity="error">{error}</Alert></Box>
          : mobile ? <Stack spacing={1.1} px={2} pb={2}>{users.map(memberCard)}</Stack>
          : <TableContainer><Table><TableHead><TableRow><TableCell>Usuário</TableCell><TableCell>Permissão</TableCell><TableCell>Criado em</TableCell>{!isDemoMode && <TableCell align="right">Ações</TableCell>}</TableRow></TableHead><TableBody>{users.map((member) => <TableRow key={member.id} hover><TableCell><Typography variant="body2" fontWeight={650}>{member.name}</Typography><Typography variant="caption" color="text.secondary">{member.email}</Typography></TableCell><TableCell><Chip size="small" label={roleLabel[member.role]} variant="outlined" color={member.role === 'ADMIN' ? 'primary' : 'default'} /></TableCell><TableCell><Typography variant="body2" color="text.secondary">{member.createdAt ? new Date(member.createdAt).toLocaleDateString('pt-BR') : '—'}</Typography></TableCell>{!isDemoMode && <TableCell align="right"><Button color="error" size="small" startIcon={<DeleteOutlineRoundedIcon />} disabled={member.id === user.id} onClick={() => setRemoveUser(member)}>Remover</Button></TableCell>}</TableRow>)}</TableBody></Table></TableContainer>}
      </Panel>}
    </Box>
    <UserCreateDialog open={dialogOpen} onClose={() => setDialogOpen(false)} onCreate={create} onError={setNotice} />
    <Dialog open={Boolean(removeUser)} onClose={removing ? undefined : () => setRemoveUser(null)}><DialogTitle>Remover acesso?</DialogTitle><DialogContent><Typography color="text.secondary">{removeUser?.name} perderá o acesso ao ambiente de trabalho PulseOps.</Typography></DialogContent><DialogActions><Button onClick={() => setRemoveUser(null)} disabled={removing}>Cancelar</Button><Button color="error" variant="contained" disabled={removing} startIcon={removing ? <CircularProgress size={16} color="inherit" /> : undefined} onClick={() => void remove()}>{removing ? 'Removendo…' : 'Remover usuário'}</Button></DialogActions></Dialog>
    <Snackbar open={Boolean(notice)} autoHideDuration={4200} onClose={() => setNotice(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}><Alert variant="filled" severity={notice?.includes('sucesso') || notice?.includes('removido') ? 'success' : 'error'} onClose={() => setNotice(null)}>{notice}</Alert></Snackbar>
  </Box>;
}

function SecurityLine({ label, value }: { label: string; value: string }) {
  return <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={0.5}><Typography variant="body2" color="text.secondary">{label}</Typography><Typography variant="body2" fontWeight={650}>{value}</Typography></Stack>;
}

function UserCreateDialog({ open, onClose, onCreate, onError }: { open: boolean; onClose: () => void; onCreate: (input: UserInput) => Promise<void>; onError: (message: string) => void }) {
  const { register, control, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<UserForm>({ resolver: zodResolver(userSchema), defaultValues: { name: '', email: '', password: '', role: 'VIEWER' } });
  const submit = async (values: UserForm) => { try { await onCreate(values); reset(); } catch (error) { onError(getApiErrorMessage(error)); } };
  return <Dialog open={open} onClose={isSubmitting ? undefined : onClose} fullWidth maxWidth="sm"><DialogTitle>Adicionar pessoa</DialogTitle><DialogContent dividers><Stack component="form" id="user-form" onSubmit={handleSubmit(submit)} spacing={2} pt={.5} noValidate><TextField label="Nome completo" placeholder="Ex.: Ana Souza" {...register('name')} error={Boolean(errors.name)} helperText={errors.name?.message} autoFocus /><TextField type="email" label="E-mail" placeholder="ana@empresa.com" {...register('email')} error={Boolean(errors.email)} helperText={errors.email?.message} /><TextField type="password" label="Senha temporária" {...register('password')} error={Boolean(errors.password)} helperText={errors.password?.message ?? 'Mínimo de 8 caracteres.'} /><Controller name="role" control={control} render={({ field }) => <FormControl error={Boolean(errors.role)}><InputLabel>Permissão</InputLabel><Select {...field} label="Permissão"><MenuItem value="ADMIN">Administrador</MenuItem><MenuItem value="DEVELOPER">Desenvolvedor</MenuItem><MenuItem value="VIEWER">Visualizador</MenuItem></Select>{errors.role && <FormHelperText>{errors.role.message}</FormHelperText>}</FormControl>} /></Stack></DialogContent><DialogActions><Button onClick={onClose} disabled={isSubmitting}>Cancelar</Button><Button form="user-form" type="submit" variant="contained" disabled={isSubmitting} startIcon={isSubmitting ? <CircularProgress size={16} color="inherit" /> : undefined}>{isSubmitting ? 'Criando…' : 'Criar usuário'}</Button></DialogActions></Dialog>;
}
