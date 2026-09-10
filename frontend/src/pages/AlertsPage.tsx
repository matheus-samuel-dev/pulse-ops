import CheckRoundedIcon from '@mui/icons-material/CheckRounded';
import DoneAllRoundedIcon from '@mui/icons-material/DoneAllRounded';
import NotificationsActiveRoundedIcon from '@mui/icons-material/NotificationsActiveRounded';
import {
  Alert,
  alpha,
  Box,
  Button,
  Chip,
  MenuItem,
  Skeleton,
  Snackbar,
  Stack,
  TextField,
  Typography,
  useTheme,
} from '@mui/material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { PageHeader } from '../components/common/PageHeader';
import { isDemoMode } from '../config/demo';
import { Panel } from '../components/common/Panel';
import { ViewState } from '../components/common/ViewState';
import { formatRelativeTime } from '../components/dashboard/dashboardFormatters';
import { getApiErrorMessage } from '../services/api';
import { notificationsService } from '../services/notificationsService';
import type { Notification, NotificationType } from '../types/api';

const typeLabel: Record<NotificationType, string> = { INFO: 'Informação', SUCCESS: 'Sucesso', WARNING: 'Atenção', ERROR: 'Erro', INCIDENT: 'Incidente', DEPLOYMENT: 'Deploy', QUALITY: 'Qualidade' };
const typeColor: Record<NotificationType, string> = { INFO: '#56b6f7', SUCCESS: '#39c995', WARNING: '#f2b84b', ERROR: '#ef6673', INCIDENT: '#ef6673', DEPLOYMENT: '#5b8cff', QUALITY: '#9d7bff' };

export function AlertsPage() {
  const theme = useTheme();
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [type, setType] = useState<NotificationType | 'ALL'>('ALL');
  const [readState, setReadState] = useState<'ALL' | 'UNREAD' | 'READ'>('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { const data = await notificationsService.list(); setNotifications(data.items); setUnreadCount(data.unreadCount); }
    catch (requestError) { setError(getApiErrorMessage(requestError)); } finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const filtered = useMemo(() => notifications.filter((item) =>
    (type === 'ALL' || item.type === type)
    && (readState === 'ALL' || (readState === 'READ' ? item.read : !item.read))), [notifications, type, readState]);

  const markRead = async (item: Notification) => {
    if (item.read) return;
    try { const updated = await notificationsService.markRead(item.id); setNotifications((current) => current.map((value) => value.id === updated.id ? updated : value)); setUnreadCount((current) => Math.max(0, current - 1)); }
    catch (requestError) { setNotice(getApiErrorMessage(requestError)); }
  };
  const markAll = async () => {
    try { await notificationsService.markAllRead(); setNotifications((current) => current.map((item) => ({ ...item, read: true }))); setUnreadCount(0); setNotice('Todos os alertas foram marcados como lidos.'); }
    catch (requestError) { setNotice(getApiErrorMessage(requestError)); }
  };

  return <Box px={{ xs: 2, sm: 3, xl: 4 }} py={{ xs: 2.5, md: 3.5 }} maxWidth={1300} mx="auto">
    <PageHeader title="Alertas" description={`${unreadCount} ${unreadCount === 1 ? 'notificação não lida' : 'notificações não lidas'} para sua conta`} eyebrow="Central de notificações"
      actions={!isDemoMode && <Button variant="outlined" startIcon={<DoneAllRoundedIcon />} onClick={() => void markAll()} disabled={!unreadCount}>Marcar todas como lidas</Button>} />
    <Panel sx={{ p: 2, mb: 2 }}><Stack direction={{ xs: 'column', sm: 'row' }} gap={1.2}><TextField size="small" select label="Tipo" value={type} onChange={(event) => setType(event.target.value as NotificationType | 'ALL')} sx={{ minWidth: 190 }}><MenuItem value="ALL">Todos os tipos</MenuItem>{(Object.keys(typeLabel) as NotificationType[]).map((value) => <MenuItem key={value} value={value}>{typeLabel[value]}</MenuItem>)}</TextField><TextField size="small" select label="Leitura" value={readState} onChange={(event) => setReadState(event.target.value as 'ALL' | 'UNREAD' | 'READ')} sx={{ minWidth: 170 }}><MenuItem value="ALL">Todos</MenuItem><MenuItem value="UNREAD">Não lidos</MenuItem><MenuItem value="READ">Lidos</MenuItem></TextField></Stack></Panel>
    {loading && <Stack spacing={1.2}>{[1,2,3,4].map((item) => <Skeleton key={item} variant="rounded" height={105}/>)}</Stack>}
    {!loading && error && <ViewState kind="error" title="Falha ao carregar alertas" description={error} actionLabel="Tentar novamente" onAction={() => void load()} />}
    {!loading && !error && filtered.length === 0 && <Panel><ViewState kind="empty" title="Nenhum alerta encontrado" description="Quando algo exigir sua atenção, o PulseOps avisará por aqui." /></Panel>}
    {!loading && !error && <Stack spacing={1}>{filtered.map((item) => <Panel
      key={item.id}
      role={!isDemoMode && !item.read ? 'button' : undefined}
      tabIndex={!isDemoMode && !item.read ? 0 : undefined}
      aria-label={!isDemoMode && !item.read ? `Marcar alerta ${item.title} como lido` : undefined}
      onClick={item.read || isDemoMode ? undefined : () => void markRead(item)}
      onKeyDown={(event) => {
          if (!isDemoMode && !item.read && (event.key === 'Enter' || event.key === ' ')) {
          event.preventDefault();
          void markRead(item);
        }
      }}
      sx={{ p: 2, cursor: !isDemoMode && !item.read ? 'pointer' : 'default', opacity: item.read ? .72 : 1, bgcolor: item.read ? 'background.paper' : alpha(typeColor[item.type], theme.palette.mode === 'dark' ? .035 : .025) }}
    ><Stack direction="row" gap={1.5} alignItems="flex-start"><Box width={38} height={38} flex="0 0 38px" display="grid" sx={{ placeItems: 'center', borderRadius: 2.3, bgcolor: alpha(typeColor[item.type],.12), color: typeColor[item.type] }}><NotificationsActiveRoundedIcon fontSize="small"/></Box><Box flex={1}><Stack direction="row" gap={1} alignItems="center" flexWrap="wrap"><Typography variant="body2" fontWeight={item.read ? 600 : 750}>{item.title}</Typography><Chip size="small" label={typeLabel[item.type]} variant="outlined" sx={{ color: typeColor[item.type], borderColor: alpha(typeColor[item.type],.35) }}/>{!item.read && <Box width={7} height={7} bgcolor="primary.main" borderRadius="50%"/>}</Stack><Typography variant="body2" color="text.secondary" mt={.55} lineHeight={1.6}>{item.message}</Typography><Typography variant="caption" color="text.secondary" display="block" mt={.8}>{formatRelativeTime(item.createdAt)}</Typography></Box>{item.read && <CheckRoundedIcon color="disabled" fontSize="small"/>}</Stack></Panel>)}</Stack>}
    <Snackbar open={Boolean(notice)} autoHideDuration={4000} onClose={() => setNotice(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}><Alert variant="filled" severity={notice?.startsWith('Todos') ? 'success' : 'error'} onClose={() => setNotice(null)}>{notice}</Alert></Snackbar>
  </Box>;
}
