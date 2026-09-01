import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
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
  name: z.string().trim().min(2, 'Informe um nome.').max(120),
  description: z.string().trim().max(1000).optional(),
  baseUrl: z.string().trim().url('Informe uma URL válida.').refine((value) => /^https?:\/\//i.test(value), 'Use HTTP ou HTTPS.'),
  healthEndpoint: z.string().trim().min(1, 'Informe o endpoint.').refine((value) => value.startsWith('/'), 'Comece com /. '),
  environment: z.enum(['PRODUCTION', 'STAGING', 'DEVELOPMENT']),
  active: z.boolean(),
  expectedStatusCode: z.coerce.number().int().min(100).max(599),
  timeoutMs: z.coerce.number().int().min(100).max(60000),
  latencyThresholdMs: z.coerce.number().int().min(1).max(60000),
  targetAvailability: z.coerce.number().min(0).max(100),
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
          <TextField label="Nome" {...register('name')} error={Boolean(errors.name)} helperText={errors.name?.message} autoFocus />
          <TextField label="Descrição" {...register('description')} multiline minRows={2} error={Boolean(errors.description)} helperText={errors.description?.message} />
          <TextField label="URL base" {...register('baseUrl')} error={Boolean(errors.baseUrl)} helperText={errors.baseUrl?.message} />
          <TextField label="Endpoint de saúde" {...register('healthEndpoint')} error={Boolean(errors.healthEndpoint)} helperText={errors.healthEndpoint?.message} />
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
        <Button type="submit" form="system-form" variant="contained" disabled={isSubmitting}>{submitLabel}</Button>
      </DialogActions>
    </Dialog>
  );
}
