import {
  Avatar,
  Badge,
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import DashboardRoundedIcon from '@mui/icons-material/DashboardRounded';
import DnsRoundedIcon from '@mui/icons-material/DnsRounded';
import ReportProblemRoundedIcon from '@mui/icons-material/ReportProblemRounded';
import RocketLaunchRoundedIcon from '@mui/icons-material/RocketLaunchRounded';
import ScienceRoundedIcon from '@mui/icons-material/ScienceRounded';
import HubRoundedIcon from '@mui/icons-material/HubRounded';
import NotificationsNoneRoundedIcon from '@mui/icons-material/NotificationsNoneRounded';
import AssessmentRoundedIcon from '@mui/icons-material/AssessmentRounded';
import HistoryRoundedIcon from '@mui/icons-material/HistoryRounded';
import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded';
import MenuRoundedIcon from '@mui/icons-material/MenuRounded';
import LightModeRoundedIcon from '@mui/icons-material/LightModeRounded';
import DarkModeRoundedIcon from '@mui/icons-material/DarkModeRounded';
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded';
import KeyboardArrowDownRoundedIcon from '@mui/icons-material/KeyboardArrowDownRounded';
import { useState, type ComponentType } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import type { SvgIconProps } from '@mui/material';
import { PulseOpsLogo } from '../components/common/PulseOpsLogo';
import { useAuth } from '../auth/AuthContext';
import { useColorMode } from '../theme/PulseOpsThemeProvider';
import { isDemoMode } from '../config/demo';

const roleLabels = { ADMIN: 'Administrador', DEVELOPER: 'Desenvolvedor', VIEWER: 'Visualizador' } as const;

const drawerWidth = 252;

interface NavigationItem {
  label: string;
  path: string;
  icon: ComponentType<SvgIconProps>;
  available: boolean;
}

const navigation: NavigationItem[] = [
  { label: 'Dashboard', path: '/', icon: DashboardRoundedIcon, available: true },
  { label: 'Sistemas', path: '/sistemas', icon: DnsRoundedIcon, available: true },
  { label: 'Incidentes', path: '/incidentes', icon: ReportProblemRoundedIcon, available: true },
  { label: 'Deploys', path: '/deploys', icon: RocketLaunchRoundedIcon, available: true },
  { label: 'Qualidade', path: '/qualidade', icon: ScienceRoundedIcon, available: true },
  { label: 'Integrações', path: '/integracoes', icon: HubRoundedIcon, available: true },
  { label: 'Alertas', path: '/alertas', icon: NotificationsNoneRoundedIcon, available: true },
  { label: 'Relatórios', path: '/relatorios', icon: AssessmentRoundedIcon, available: true },
  { label: 'Auditoria', path: '/auditoria', icon: HistoryRoundedIcon, available: true },
  { label: 'Configurações', path: '/configuracoes', icon: SettingsRoundedIcon, available: true },
];

export function AppShell() {
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('lg'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, signOut } = useAuth();
  const { mode, toggleColorMode } = useColorMode();

  const logout = () => {
    signOut();
    navigate('/login', { replace: true });
  };

  const drawer = (
    <Box height="100%" display="flex" flexDirection="column" bgcolor="background.paper">
      <Box px={2.25} py={2.5}>
        <PulseOpsLogo />
      </Box>
      <Divider />
      <Box px={1.25} pt={2}>
        <Typography px={1.2} mb={0.75} variant="caption" color="text.secondary" fontWeight={650} textTransform="uppercase">
          Workspace
        </Typography>
        <List disablePadding aria-label="Navegação principal">
          {navigation.map((item) => {
            const Icon = item.icon;
            const active = item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path);
            const button = (
              <ListItemButton
                key={item.label}
                selected={active}
                disabled={!item.available}
                aria-current={active ? 'page' : undefined}
                onClick={() => {
                  navigate(item.path);
                  setMobileOpen(false);
                }}
                sx={{
                  minHeight: 42,
                  px: 1.25,
                  mb: 0.35,
                  borderRadius: 2,
                  color: active ? 'primary.light' : 'text.secondary',
                  '&.Mui-selected': {
                    bgcolor: 'rgba(91,140,255,.105)',
                    color: 'primary.light',
                    '&:hover': { bgcolor: 'rgba(91,140,255,.14)' },
                  },
                  '&.Mui-disabled': { opacity: 0.48 },
                }}
              >
                <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}>
                  <Icon sx={{ fontSize: 19 }} />
                </ListItemIcon>
                <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: '0.84rem', fontWeight: active ? 650 : 500 }} />
                {active && <Box width={3} height={16} borderRadius={3} bgcolor="primary.main" />}
              </ListItemButton>
            );
            return item.available ? button : (
              <Tooltip key={item.label} title="Disponível na experiência completa" placement="right">
                <span>{button}</span>
              </Tooltip>
            );
          })}
        </List>
      </Box>
      <Box mt="auto" px={1.25} pb={1.5}>
        <Divider sx={{ mb: 1.2 }} />
        <Stack direction="row" alignItems="center" px={0.6} py={0.7} spacing={1}>
          <Avatar sx={{ width: 33, height: 33, bgcolor: 'primary.dark', fontSize: '0.75rem', fontWeight: 700 }}>
            {user?.name.split(' ').slice(0, 2).map((part) => part[0]).join('').toUpperCase()}
          </Avatar>
          <Box minWidth={0} flex={1}>
            <Typography variant="body2" noWrap fontWeight={650}>{user?.name}</Typography>
            <Typography variant="caption" color="text.secondary">{user ? roleLabels[user.role] : ''}</Typography>
          </Box>
          <KeyboardArrowDownRoundedIcon color="disabled" fontSize="small" />
        </Stack>
        <Stack direction="row" mt={0.5}>
          <Tooltip title={mode === 'dark' ? 'Usar tema claro' : 'Usar tema escuro'}>
            <IconButton size="small" aria-label={mode === 'dark' ? 'Ativar tema claro' : 'Ativar tema escuro'} onClick={toggleColorMode}>
              {mode === 'dark' ? <LightModeRoundedIcon fontSize="small" /> : <DarkModeRoundedIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
          <Tooltip title="Sair">
            <IconButton size="small" aria-label="Sair da plataforma" onClick={logout} sx={{ ml: 'auto' }}>
              <LogoutRoundedIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      </Box>
    </Box>
  );

  return (
    <Box minHeight="100vh" display="flex">
      <Box component="nav" aria-label="Navegação do PulseOps">
        {desktop ? (
          <Drawer
            variant="permanent"
            open
            sx={{
              width: drawerWidth,
              flexShrink: 0,
              '& .MuiDrawer-paper': { width: drawerWidth, borderRightColor: 'divider' },
            }}
          >
            {drawer}
          </Drawer>
        ) : (
          <Drawer
            variant="temporary"
            open={mobileOpen}
            onClose={() => setMobileOpen(false)}
            ModalProps={{ keepMounted: true }}
            sx={{ '& .MuiDrawer-paper': { width: drawerWidth } }}
          >
            {drawer}
          </Drawer>
        )}
      </Box>
      <Box component="main" flex={1} minWidth={0}>
        <Toolbar
          disableGutters
          sx={{
            height: 64,
            px: { xs: 2, md: 3.5 },
            borderBottom: '1px solid',
            borderColor: 'divider',
            bgcolor: 'background.default',
            position: 'sticky',
            top: 0,
            zIndex: theme.zIndex.appBar,
          }}
        >
          {!desktop && (
            <IconButton onClick={() => setMobileOpen(true)} edge="start" aria-label="Abrir menu" sx={{ mr: 1 }}>
              <MenuRoundedIcon />
            </IconButton>
          )}
          {!desktop && <PulseOpsLogo compact />}
          <Box flex={1} />
          {isDemoMode && (
            <Chip
              size="small"
              label="Ambiente demonstrativo"
              variant="outlined"
              color="secondary"
              sx={{ mr: { xs: 0.5, sm: 1 }, display: { xs: 'none', sm: 'inline-flex' }, fontWeight: 650 }}
            />
          )}
          <Tooltip title="Central de alertas">
            <IconButton aria-label="Abrir alertas" onClick={() => navigate('/alertas')}>
              <Badge color="error" variant="dot" overlap="circular">
                <NotificationsNoneRoundedIcon fontSize="small" />
              </Badge>
            </IconButton>
          </Tooltip>
          <Avatar sx={{ ml: 1, width: 30, height: 30, bgcolor: 'primary.dark', fontSize: '0.68rem', fontWeight: 700 }}>
            {user?.name.charAt(0).toUpperCase()}
          </Avatar>
        </Toolbar>
        <Outlet />
      </Box>
    </Box>
  );
}
