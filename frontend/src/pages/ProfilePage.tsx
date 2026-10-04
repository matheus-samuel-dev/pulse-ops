import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Avatar, Box, Button, CircularProgress, Stack, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/common/PageHeader';
import { Panel } from '../components/common/Panel';
import { getApiErrorMessage } from '../services/api';
import { saveProfile } from '../services/authService';

const schema = z.object({ name: z.string().trim().min(2, 'Informe seu nome.').max(120), email: z.email('Informe um e-mail válido.').max(254), currentPassword: z.string().max(72).optional() });
type Form = z.infer<typeof schema>;
export function ProfilePage() {
  const { user, acceptSession } = useAuth();
  const [notice, setNotice] = useState<{ message: string; error: boolean } | null>(null);
  const { register, handleSubmit, reset, control, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { name: user?.name, email: user?.email, currentPassword: '' } });
  useEffect(() => { reset({ name: user?.name, email: user?.email, currentPassword: '' }); }, [user, reset]);
  const emailChanged = useWatch({ control, name: 'email' })?.trim().toLowerCase() !== user?.email;
  const submit = async (values: Form) => {
    setNotice(null);
    try { acceptSession(await saveProfile(values)); setNotice({ message: 'Perfil atualizado com sucesso.', error: false }); }
    catch (failure) { setNotice({ message: getApiErrorMessage(failure), error: true }); }
  };
  return <Box p={{ xs: 2, sm: 3.5 }} maxWidth={900} mx="auto">
    <PageHeader title="Meu perfil" description="Informações da sua conta" />
    <Panel sx={{ p: { xs: 2, sm: 3 } }}>
      <Stack direction="row" spacing={2} alignItems="center" mb={3}><Avatar sx={{ bgcolor: 'primary.dark', width: 48, height: 48 }}>{user?.name.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase()}</Avatar><Box minWidth={0}><Typography fontWeight={700}>{user?.name}</Typography><Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{user?.email}</Typography></Box></Stack>
      {notice && <Alert severity={notice.error ? 'error' : 'success'} sx={{ mb: 2 }}>{notice.message}</Alert>}
      <Stack component="form" spacing={2} onSubmit={handleSubmit(submit)} noValidate>
        <TextField label="Nome" autoComplete="name" {...register('name')} error={Boolean(errors.name)} helperText={errors.name?.message} />
        <TextField label="E-mail" type="email" autoComplete="email" {...register('email')} error={Boolean(errors.email)} helperText={errors.email?.message} />
        {emailChanged && <TextField label="Senha atual" type="password" autoComplete="current-password" {...register('currentPassword')} error={Boolean(errors.currentPassword)} helperText={errors.currentPassword?.message ?? 'Necessária para alterar o e-mail. As outras sessões serão encerradas.'} />}
        <Button type="submit" variant="contained" disabled={isSubmitting} sx={{ alignSelf: 'flex-start' }} startIcon={isSubmitting ? <CircularProgress color="inherit" size={18} /> : undefined}>{isSubmitting ? 'Salvando…' : 'Salvar alterações'}</Button>
      </Stack>
    </Panel>
  </Box>;
}
