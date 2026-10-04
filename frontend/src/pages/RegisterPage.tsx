import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Box, Button, CircularProgress, Link, Stack, TextField, Typography } from '@mui/material';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link as RouterLink, Navigate, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../auth/AuthContext';
import { PulseOpsLogo } from '../components/common/PulseOpsLogo';
import { getApiErrorMessage } from '../services/api';

const schema = z.object({
  name: z.string().trim().min(2, 'Informe seu nome.').max(120, 'Use até 120 caracteres.'),
  email: z.email('Informe um e-mail válido.').max(254),
  password: z.string().min(8, 'Use pelo menos 8 caracteres.').max(72, 'Use até 72 caracteres.')
    .refine(value => new TextEncoder().encode(value).length <= 72, 'A senha deve ter até 72 bytes.'),
  confirmation: z.string(),
}).refine(value => value.password === value.confirmation, { path: ['confirmation'], message: 'As senhas devem ser iguais.' });
type Form = z.infer<typeof schema>;

export function RegisterPage() {
  const { signUp, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) });
  if (isAuthenticated) return <Navigate to="/" replace />;
  const submit = async (values: Form) => {
    setError(null);
    try { await signUp({ name: values.name, email: values.email, password: values.password }); navigate('/', { replace: true }); }
    catch (failure) { setError(getApiErrorMessage(failure)); }
  };
  return <Box minHeight="100vh" display="grid" p={{ xs: 2.5, sm: 5 }} sx={{ placeItems: 'center' }}>
    <Box width="100%" maxWidth={440}>
      <PulseOpsLogo sx={{ mb: 4 }} />
      <Typography variant="h1">Crie sua conta</Typography>
      <Typography color="text.secondary" mt={1} mb={3}>Cadastre suas aplicações e comece a gerar histórico real de monitoramento.</Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Stack component="form" spacing={2} onSubmit={handleSubmit(submit)} noValidate>
        <TextField label="Nome" autoComplete="name" autoFocus {...register('name')} error={Boolean(errors.name)} helperText={errors.name?.message} />
        <TextField label="E-mail" type="email" autoComplete="email" {...register('email')} error={Boolean(errors.email)} helperText={errors.email?.message} />
        <TextField label="Senha" type="password" autoComplete="new-password" {...register('password')} error={Boolean(errors.password)} helperText={errors.password?.message ?? 'Mínimo de 8 caracteres.'} />
        <TextField label="Confirmar senha" type="password" autoComplete="new-password" {...register('confirmation')} error={Boolean(errors.confirmation)} helperText={errors.confirmation?.message} />
        <Button type="submit" variant="contained" disabled={isSubmitting} size="large" startIcon={isSubmitting ? <CircularProgress color="inherit" size={18} /> : undefined}>{isSubmitting ? 'Criando conta…' : 'Criar conta'}</Button>
      </Stack>
      <Typography mt={3} textAlign="center" variant="body2">Já possui conta? <Link component={RouterLink} to="/login">Entrar</Link></Typography>
    </Box>
  </Box>;
}
