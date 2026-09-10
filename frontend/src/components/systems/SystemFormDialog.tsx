import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  FormControlLabel,
  TextField,
} from '@mui/material';
import { Controller, useForm } from 'react-hook-form';
import { useEffect } from 'react';
import { z } from 'zod';
import type { MonitoredSystemInput } from '../../types/api';

const schema = z.object({
  name: z.string().trim().min(2, 'Informe um nome com ao menos 2 caracteres.').max(120, 'Use no máximo 120 caracteres.'),
  description: z.string().trim().max(1000, 'Use no máximo 1.000 caracteres.').optional(),
  baseUrl: z.string().trim().url('Informe uma URL válida.').refine((value) => /^https?:\/\//i.test(value), 'Use HTTP ou HTTPS.'),
  healthEndpoint: z.string().trim().min(1, 'Informe o endpoint.').refine((value) => value.startsWith('/'), 'O endpoint deve começar com /.').refine((value) => !value.includes(' '), 'O endpoint não pode conter espaços.'),
  environment: z.enum(['PRODUCTION', 'STAGING', 'DEVELOPMENT']),
  active: z.boolean(),
  expectedStatusCode: z.coerce.number().int('Informe um status inteiro.').min(100, 'Use um status entre 100 e 599.').max(599, 'Use um status entre 100 e 599.'),
  timeoutMs: z.coerce.number().int().min(100, 'O mínimo é 100 ms.').max(60000, 'O máximo é 60.000 ms.'),
  latencyThresholdMs: z.coerce.number().int().min(1, 'O mínimo é 1 ms.').max(60000, 'O máximo é 60.000 ms.'),
  targetAvailability: z.coerce.number().min(0, 'A meta não pode ser negativa.').max(100, 'A meta não pode exceder 100%.'),
});

type FormInput = z.input<typeof schema>;
type FormData = z.output<typeof schema>;

interface Props {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: MonitoredSystemInput) => Promise<void>;
  initial?: MonitoredSystemInput;
  title?: string;
  submitLabel?: string;
}

const defaults: FormInput = {
  name: '',
  description: '',
  baseUrl: 'https://',
  healthEndpoint: '/actuator/health',
  environment: 'PRODUCTION',
  active: true,
  expectedStatusCode: 200,
  timeoutMs: 3000,
  latencyThresholdMs: 500,
  targetAvailability: 99.9,
};

export function SystemFormDialog({ open, onClose, onSubmit, initial, title = 'Cadastrar sistema', submitLabel = 'Cadastrar sistema' }: Props) {
  const { register, control, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<FormInput, unknown, FormData>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  });

  useEffect(() => {
    if (open) reset(initial ?? defaults);
  }, [initial, open, reset]);

  const close = () => { reset(initial ?? defaults); onClose(); };
  const submit = async (data: FormData) => { await onSubmit(data); reset(initial ?? defaults); };

  return (
    <Dialog open={open} onClose={isSubmitting ? undefined : close} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent dividers>
        <Stack component="form" id="system-form" onSubmit={handleSubmit(submit)} spacing={2.1} pt={0.5} noValidate>
          <TextField label="Nome do sistema" placeholder="Ex.: API de Pagamentos" {...register('name')} error={Boolean(errors.name)} helperText={errors.name?.message} autoFocus />
          <TextField label="Descrição" placeholder="Explique o papel deste serviço na plataforma" {...register('description')} multiline minRows={2} error={Boolean(errors.description)} helperText={errors.description?.message} />
          <TextField label="URL base" placeholder="https://api.exemplo.com:443" {...register('baseUrl')} error={Boolean(errors.baseUrl)} helperText={errors.baseUrl?.message ?? 'Inclua protocolo e, se necessário, uma porta válida.'} />
          <TextField label="Endpoint de saúde" placeholder="/actuator/health" {...register('healthEndpoint')} error={Boolean(errors.healthEndpoint)} helperText={errors.healthEndpoint?.message ?? 'Caminho relativo consultado pelo monitoramento.'} />
          <Controller name="environment" control={control} render={({ field }) => (
            <FormControl error={Boolean(errors.environment)}>
              <InputLabel>Ambiente</InputLabel>
              <Select {...field} label="Ambiente">
                <MenuItem value="PRODUCTION">Produção</MenuItem>
                <MenuItem value="STAGING">Staging</MenuItem>
                <MenuItem value="DEVELOPMENT">Desenvolvimento</MenuItem>
              </Select>
              {errors.environment && <FormHelperText>{errors.environment.message}</FormHelperText>}
            </FormControl>
          )} />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField type="number" label="Status HTTP esperado" {...register('expectedStatusCode')} error={Boolean(errors.expectedStatusCode)} helperText={errors.expectedStatusCode?.message} fullWidth />
            <TextField type="number" label="Timeout (ms)" {...register('timeoutMs')} error={Boolean(errors.timeoutMs)} helperText={errors.timeoutMs?.message} fullWidth />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField type="number" label="Limite de latência (ms)" {...register('latencyThresholdMs')} error={Boolean(errors.latencyThresholdMs)} helperText={errors.latencyThresholdMs?.message} fullWidth />
            <TextField type="number" inputProps={{ step: '0.01' }} label="Meta de SLA (%)" {...register('targetAvailability')} error={Boolean(errors.targetAvailability)} helperText={errors.targetAvailability?.message} fullWidth />
          </Stack>
          <Controller name="active" control={control} render={({ field }) => (
            <FormControlLabel control={<Switch checked={field.value} onChange={(_, checked) => field.onChange(checked)} />} label="Monitoramento ativo" />
          )} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={close} disabled={isSubmitting}>Cancelar</Button>
        <Button type="submit" form="system-form" variant="contained" disabled={isSubmitting} startIcon={isSubmitting ? <CircularProgress size={16} color="inherit" /> : undefined}>{isSubmitting ? 'Salvando…' : submitLabel}</Button>
      </DialogActions>
    </Dialog>
  );
}
