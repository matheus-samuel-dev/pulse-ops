import { Box, CircularProgress } from '@mui/material';
import { lazy, Suspense, useEffect } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { AppShell } from './layout/AppShell';

const LoginPage = lazy(() => import('./pages/LoginPage').then((module) => ({ default: module.LoginPage })));
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })));
const SystemsPage = lazy(() => import('./pages/SystemsPage').then((module) => ({ default: module.SystemsPage })));
const SystemDetailPage = lazy(() => import('./pages/SystemDetailPage').then((module) => ({ default: module.SystemDetailPage })));
const IncidentsPage = lazy(() => import('./pages/IncidentsPage').then((module) => ({ default: module.IncidentsPage })));
const DeploymentsPage = lazy(() => import('./pages/DeploymentsPage').then((module) => ({ default: module.DeploymentsPage })));
const QualityPage = lazy(() => import('./pages/QualityPage').then((module) => ({ default: module.QualityPage })));
const AlertsPage = lazy(() => import('./pages/AlertsPage').then((module) => ({ default: module.AlertsPage })));
const ReportsPage = lazy(() => import('./pages/ReportsPage').then((module) => ({ default: module.ReportsPage })));
const AuditPage = lazy(() => import('./pages/AuditPage').then((module) => ({ default: module.AuditPage })));
const SettingsPage = lazy(() => import('./pages/SettingsPage').then((module) => ({ default: module.SettingsPage })));
const IntegrationsPage = lazy(() => import('./pages/IntegrationsPage').then((module) => ({ default: module.IntegrationsPage })));

export function App() {
  return (
    <Suspense fallback={<Box minHeight="100vh" display="grid" sx={{ placeItems: 'center' }}><CircularProgress aria-label="Carregando página" /></Box>}>
      <ScrollToTop />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="sistemas" element={<SystemsPage />} />
            <Route path="sistemas/:id" element={<SystemDetailPage />} />
            <Route path="incidentes" element={<IncidentsPage />} />
            <Route path="deploys" element={<DeploymentsPage />} />
            <Route path="qualidade" element={<QualityPage />} />
            <Route path="integracoes" element={<IntegrationsPage />} />
            <Route path="integrations" element={<Navigate to="/integracoes" replace />} />
            <Route path="alertas" element={<AlertsPage />} />
            <Route path="relatorios" element={<ReportsPage />} />
            <Route path="auditoria" element={<AuditPage />} />
            <Route path="configuracoes" element={<SettingsPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}

function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, [pathname]);

  return null;
}
