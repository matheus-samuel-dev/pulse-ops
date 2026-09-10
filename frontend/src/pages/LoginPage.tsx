import { zodResolver } from '@hookform/resolvers/zod';
import AlternateEmailRoundedIcon from '@mui/icons-material/AlternateEmailRounded';
import CheckCircleRoundedIcon from '@mui/icons-material/CheckCircleRounded';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import VisibilityOffRoundedIcon from '@mui/icons-material/VisibilityOffRounded';
import VisibilityRoundedIcon from '@mui/icons-material/VisibilityRounded';
import {
  Alert,
  alpha,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  IconButton,
  InputAdornment,
  Link,
  Snackbar,
  Stack,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { useAuth } from '../auth/AuthContext';
import { PulseOpsLogo } from '../components/common/PulseOpsLogo';
import { getApiErrorMessage } from '../services/api';

const demoCredentials = { email: 'admin@pulseops.dev', password: 'PulseOps@2026' };

const schema = z.object({
  email: z.string().trim().min(1, 'Informe seu e-mail.').email('Informe um e-mail válido.'),
  password: z.string().min(1, 'Informe sua senha.'),
});

type LoginForm = z.infer<typeof schema>;

export function LoginPage() {
  const theme = useTheme();
  const { signIn, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [recoveryNoticeOpen, setRecoveryNoticeOpen] = useState(false);
  const {
    control,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } });

  if (isAuthenticated) return <Navigate to="/" replace />;

  const submit = async (values: LoginForm) => {
    setError(null);
    try {
      await signIn(values);
      const destination = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/';
      navigate(destination, { replace: true });
    } catch (requestError) {
      setError(getApiErrorMessage(requestError));
    }
  };

  const fillDemo = () => {
    setValue('email', demoCredentials.email, { shouldValidate: true });
    setValue('password', demoCredentials.password, { shouldValidate: true });
    setError(null);
  };

  return (
    <Box minHeight="100vh" display="grid" gridTemplateColumns={{ xs: '1fr', lg: 'minmax(430px, 0.9fr) minmax(520px, 1.1fr)' }}>
      <Box
        display={{ xs: 'none', lg: 'flex' }}
        flexDirection="column"
        position="relative"
        overflow="hidden"
        p={{ lg: 6, xl: 8 }}
        sx={{
          backgroundColor: theme.palette.mode === 'dark' ? '#091529' : '#e9f0ff',
          borderRight: '1px solid',
          borderColor: 'divider',
        }}
      >
        <PulseOpsLogo />
        <Box
          aria-hidden="true"
          sx={{
            position: 'absolute',
            inset: 0,
            opacity: theme.palette.mode === 'dark' ? 0.28 : 0.18,
            backgroundImage: `linear-gradient(${alpha(theme.palette.primary.main, 0.16)} 1px, transparent 1px), linear-gradient(90deg, ${alpha(theme.palette.primary.main, 0.16)} 1px, transparent 1px)`,
            backgroundSize: '48px 48px',
            maskImage: 'linear-gradient(to bottom right, black, transparent 72%)',
            pointerEvents: 'none',
          }}
        />
        <Box mt="auto" mb="auto" position="relative" maxWidth={560}>
          <Typography fontSize={{ lg: '3.4rem', xl: '4.2rem' }} lineHeight={1.04} fontWeight={760} letterSpacing="-0.065em">
            Monitore.<br />Entenda.<br /><Box component="span" color="primary.light">Resolva.</Box>
          </Typography>
          <Typography mt={3} maxWidth={470} color="text.secondary" fontSize="1rem" lineHeight={1.75}>
            Operações, confiabilidade e qualidade de software reunidas em uma visão clara — antes que ruídos se tornem incidentes.
          </Typography>
          <Stack spacing={1.4} mt={4}>
            {['Telemetria operacional em tempo real', 'SLA, incidentes e deploys correlacionados', 'Qualidade comprovada por testes automatizados'].map((item) => (
              <Stack key={item} direction="row" spacing={1.1} alignItems="center">
                <CheckCircleRoundedIcon sx={{ color: 'success.main', fontSize: 18 }} />
                <Typography variant="body2" color="text.secondary">{item}</Typography>
              </Stack>
            ))}
          </Stack>
        </Box>
        <Typography variant="caption" color="text.secondary" position="relative">
          PulseOps · Observability & Software Quality Platform
        </Typography>
      </Box>

      <Box display="grid" sx={{ placeItems: 'center' }} p={{ xs: 2.5, sm: 5, lg: 7 }}>
        <Box width="100%" maxWidth={420}>
          <PulseOpsLogo sx={{ display: { xs: 'flex', lg: 'none' }, mb: 5 }} />
          <Typography variant="h1">Bem-vindo de volta</Typography>
          <Typography color="text.secondary" mt={1} mb={4}>
            Entre para acompanhar a saúde da sua plataforma.
          </Typography>

          {error && <Alert severity="error" sx={{ mb: 2.5 }}>{error}</Alert>}

          <Box component="form" onSubmit={handleSubmit(submit)} noValidate>
            <Stack spacing={2.1}>
              <Controller
                name="email"
                control={control}
                render={({ field: { ref, ...field } }) => (
                  <TextField
                    {...field}
                    inputRef={ref}
                    label="E-mail"
                    placeholder="voce@empresa.com"
                    autoComplete="email"
                    autoFocus
                    error={Boolean(errors.email)}
                    helperText={errors.email?.message}
                    fullWidth
                    InputProps={{
                      startAdornment: <InputAdornment position="start"><AlternateEmailRoundedIcon fontSize="small" /></InputAdornment>,
                    }}
                  />
                )}
              />
              <Controller
                name="password"
                control={control}
                render={({ field: { ref, ...field } }) => (
                  <TextField
                    {...field}
                    inputRef={ref}
                    label="Senha"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    error={Boolean(errors.password)}
                    helperText={errors.password?.message}
                    fullWidth
                    InputProps={{
                      startAdornment: <InputAdornment position="start"><LockOutlinedIcon fontSize="small" /></InputAdornment>,
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton
                            onClick={() => setShowPassword((value) => !value)}
                            edge="end"
                            aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                            aria-pressed={showPassword}
                          >
                            {showPassword ? <VisibilityOffRoundedIcon fontSize="small" /> : <VisibilityRoundedIcon fontSize="small" />}
                          </IconButton>
                        </InputAdornment>
                      ),
                    }}
                  />
                )}
              />
              <Stack direction="row" alignItems="center" justifyContent="space-between">
                <FormControlLabel
                  control={<Checkbox size="small" defaultChecked />}
                  label={<Typography variant="body2" color="text.secondary">Manter conectado</Typography>}
                />
                <Link
                  component="button"
                  type="button"
                  variant="body2"
                  underline="hover"
                  onClick={() => setRecoveryNoticeOpen(true)}
                >
                  Esqueceu a senha?
                </Link>
              </Stack>
              <Button type="submit" variant="contained" size="large" disabled={isSubmitting} fullWidth sx={{ height: 47 }}>
                {isSubmitting ? <CircularProgress color="inherit" size={21} aria-label="Autenticando" /> : 'Entrar no PulseOps'}
              </Button>
            </Stack>
          </Box>

          <Box mt={3.2} p={2} border="1px solid" borderColor="divider" borderRadius={2.5} bgcolor={alpha(theme.palette.primary.main, 0.035)}>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={2}>
              <Box minWidth={0}>
                <Typography variant="caption" fontWeight={700} color="primary.light">ACESSO DEMONSTRATIVO</Typography>
                <Typography variant="body2" mt={0.8} fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace">{demoCredentials.email}</Typography>
                <Typography variant="body2" mt={0.25} fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace">{demoCredentials.password}</Typography>
              </Box>
              <Button size="small" variant="text" onClick={fillDemo}>Preencher</Button>
            </Stack>
          </Box>
          <Typography variant="caption" display="block" textAlign="center" color="text.secondary" mt={3}>
            Ambiente protegido por JWT · TLS preparado · RBAC ativo
          </Typography>
        </Box>
      </Box>
      <Snackbar
        open={recoveryNoticeOpen}
        autoHideDuration={5000}
        onClose={() => setRecoveryNoticeOpen(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="info" variant="filled" onClose={() => setRecoveryNoticeOpen(false)}>
          Recuperação de senha não está habilitada no ambiente demonstrativo.
        </Alert>
      </Snackbar>
    </Box>
  );
}
