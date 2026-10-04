import { Alert, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Stack, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { connectionsService, type ConnectionInput } from '../../services/connectionsService';
import { getApiErrorMessage } from '../../services/api';

export function ConnectionManager({ slug, onClose, onSaved }: { slug: string; onClose: () => void; onSaved: () => void }) {
  const [input, setInput] = useState<ConnectionInput>({ baseUrl: '', healthEndpoint: '/', timeoutMs: 10000, publicUrl: '', actionPath: slug === 'ai-web-auditor' ? '/api/audits' : '/webhook/pulseops', accessToken: '', clearToken: false, autoDispatch: false });
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [credential, setCredential] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [configured, setConfigured] = useState(false);
  useEffect(() => {
    let active = true;
    connectionsService.get(slug).then(config => {
      if (!active) return;
      if (config) { setInput({ ...config, publicUrl: config.publicUrl ?? '', accessToken: '', clearToken: false }); setCredential(config.credentialConfigured); setConfigured(true); }
    }).catch(failure => { if (active) setError(getApiErrorMessage(failure)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [slug]);
  const save = async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null);
    try { await connectionsService.save(slug, input); onSaved(); onClose(); }
    catch (failure) { setError(getApiErrorMessage(failure)); } finally { setBusy(false); }
  };
  const disconnect = async () => {
    setBusy(true); setError(null);
    try { await connectionsService.remove(slug); onSaved(); onClose(); }
    catch (failure) { setError(getApiErrorMessage(failure)); } finally { setBusy(false); }
  };
  return <Dialog open onClose={busy ? undefined : onClose} fullWidth maxWidth="sm" aria-labelledby="connection-title">
    <DialogTitle id="connection-title">Configurar {slug === 'ai-web-auditor' ? 'AI Web Auditor' : 'Nexus Flow'}</DialogTitle>
    <DialogContent dividers><Stack spacing={2} component="form" id="connection-form" onSubmit={event => void save(event)}>
      {error && <Alert severity="error">{error}</Alert>}
      {loading ? <Typography role="status">Carregando configuração…</Typography> : <>
<Typography variant="body2" color="text.secondary">O conector tem seu próprio destino e não cria um sistema monitorado.</Typography>
        <TextField label="URL base da integração" type="url" required value={input.baseUrl} onChange={event => setInput({ ...input, baseUrl: event.target.value })} />
        <TextField label="Endpoint para testar conexão" required value={input.healthEndpoint} onChange={event => setInput({ ...input, healthEndpoint: event.target.value })} helperText="Caminho de saúde do serviço remoto. O backend enviará a credencial configurada." />
        <TextField label="Tempo limite da integração (ms)" type="number" required value={input.timeoutMs} onChange={event => setInput({ ...input, timeoutMs: Number(event.target.value) })} slotProps={{ htmlInput: { min: 100, max: 60000 } }} />
        <TextField label="Caminho da API ou webhook" required value={input.actionPath} onChange={event => setInput({ ...input, actionPath: event.target.value })} helperText={slug === 'ai-web-auditor' ? 'Contrato do AI Web Auditor: POST /api/audits e GET /api/audits/{id}.' : 'Caminho do webhook publicado pelo seu workflow. Deve começar com /.'} />
        <TextField label="URL pública da interface" type="url" value={input.publicUrl} onChange={event => setInput({ ...input, publicUrl: event.target.value })} helperText="Opcional. No AI Web Auditor, permite abrir /audits/{id} após a conclusão." />
        <TextField label="Token de acesso" type="password" autoComplete="new-password" value={input.accessToken} onChange={event => setInput({ ...input, accessToken: event.target.value })} helperText={credential ? 'Credencial salva. Deixe vazio para manter; o valor nunca é devolvido pela API.' : 'Token Bearer do serviço remoto. Ele é armazenado cifrado no backend.'} />
        {credential && <FormControlLabel control={<Checkbox checked={input.clearToken} onChange={event => setInput({ ...input, clearToken: event.target.checked })} />} label="Remover a credencial salva" />}
        {slug === 'nexus-flow' && <FormControlLabel control={<Checkbox checked={input.autoDispatch} onChange={event => setInput({ ...input, autoDispatch: event.target.checked })} />} label="Enviar evento automaticamente quando um sistema ficar indisponível" />}
      </>}
    </Stack></DialogContent>
    <DialogActions sx={{ flexWrap: 'wrap' }}>{configured && <Button color="error" disabled={busy} onClick={() => void disconnect()}>Desconectar</Button>}<Button disabled={busy} onClick={onClose}>Cancelar</Button><Button variant="contained" form="connection-form" type="submit" disabled={busy || loading || !input.baseUrl}>{busy ? 'Salvando…' : 'Salvar configuração'}</Button></DialogActions>
  </Dialog>;
}
