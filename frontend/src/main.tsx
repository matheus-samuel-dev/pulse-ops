import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { AppErrorBoundary } from './components/common/AppErrorBoundary';
import { AuthProvider } from './auth/AuthContext';
import { PulseOpsThemeProvider } from './theme/PulseOpsThemeProvider';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <PulseOpsThemeProvider>
      <AppErrorBoundary>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </AppErrorBoundary>
    </PulseOpsThemeProvider>
  </StrictMode>,
);
