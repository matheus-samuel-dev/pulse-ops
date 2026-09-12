import { act, cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IntegrationsPage } from './IntegrationsPage';
import { integrationsService } from '../services/integrationsService';
import { auditorFixture, hospitalFixture, overviewFixture } from '../test/integrationFixtures';

const auth = vi.hoisted(() => ({ role: 'ADMIN' }));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { role: auth.role } }) }));
vi.mock('../config/demo', () => ({ isDemoMode: false }));
vi.mock('../services/integrationsService', () => ({ integrationsService: { overview: vi.fn(), detail: vi.fn(), events: vi.fn(), check: vi.fn() } }));

function viewport(width: number) {
  vi.stubGlobal('matchMedia', vi.fn((query: string) => ({ matches: query.includes('min-width:900px') && width >= 900, media: query,
    onchange: null, addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn() })));
}
function show() { return render(<ThemeProvider theme={createTheme()}><IntegrationsPage /></ThemeProvider>); }

describe('IntegrationsPage', () => {
  beforeEach(() => {
    vi.resetAllMocks(); auth.role = 'ADMIN'; viewport(1440);
    vi.mocked(integrationsService.overview).mockResolvedValue(overviewFixture);
    vi.mocked(integrationsService.events).mockResolvedValue([]);
    vi.mocked(integrationsService.detail).mockResolvedValue(auditorFixture);
  });
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it('renders server metrics, distinct statuses, map and real empty activity', async () => {
    show();
    expect(await screen.findByRole('table', { name: 'Sistemas integrados' })).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Mapa de integrações' })).toBeInTheDocument();
    expect(screen.getByText('Integração principal')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma atividade recente')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('Offline')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('Não configurado')).toBeInTheDocument();
    expect(screen.queryByText('Screenshots')).not.toBeInTheDocument();
  });

  it('shows a bounded loading state while awaiting the API', () => {
    vi.mocked(integrationsService.overview).mockReturnValue(new Promise(() => undefined)); show();
    expect(screen.getByRole('status', { name: 'Carregando integrações' })).toBeInTheDocument();
  });

  it('retries a failed load', async () => {
    vi.mocked(integrationsService.overview).mockRejectedValueOnce(new Error('offline')); show();
    await screen.findByText('Não foi possível carregar as integrações');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('isolates an activity failure from the overview', async () => {
    vi.mocked(integrationsService.events).mockRejectedValue(new Error('offline')); show();
    expect(await screen.findByText('Atividade indisponível')).toBeInTheDocument();
    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('filters by accent-insensitive name and shows the empty state', async () => {
    show(); await screen.findByRole('table');
    await userEvent.type(screen.getByLabelText('Buscar integração'), 'gestao');
    expect(within(screen.getByRole('table')).getByText('Gestão Hospitalar')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).queryByText('HelpDesk')).not.toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Buscar integração'));
    await userEvent.type(screen.getByLabelText('Buscar integração'), 'inexistente');
    expect(screen.getByText('Nenhuma integração encontrada')).toBeInTheDocument();
  });

  it('filters by status', async () => {
    show(); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('combobox', { name: 'Status' }));
    await userEvent.click(screen.getByRole('option', { name: 'Offline' }));
    expect(within(screen.getByRole('table')).getByText('HelpDesk')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).queryByText('AI Web Auditor')).not.toBeInTheDocument();
  });

  it.each([360, 390, 430, 768])('renders accessible cards instead of a table at %ipx', async width => {
    viewport(width); show();
    expect(await screen.findByRole('button', { name: 'Detalhes de HelpDesk' })).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Detalhes de Gestão Hospitalar' })).toBeInTheDocument();
  });

  it('opens a dialog, invokes the backend check and refreshes persisted data', async () => {
    vi.mocked(integrationsService.check).mockResolvedValue({ integrationId: auditorFixture.id, status: 'ONLINE', responseTimeMs: 120,
      checkedAt: '2026-09-10T14:00:00Z', message: 'Conexão realizada com sucesso', cached: false, nextCheckAt: '2026-09-10T14:01:00Z' });
    show(); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: 'Detalhes de AI Web Auditor' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(await within(dialog).findByRole('button', { name: 'Testar conexão' }));
    expect(await within(dialog).findByText('Conexão realizada com sucesso.')).toBeInTheDocument();
    expect(within(dialog).getAllByRole('alert').some(alert => alert.textContent?.includes('Conexão realizada com sucesso.'))).toBe(true);
    expect(integrationsService.check).toHaveBeenCalledWith('ai-web-auditor');
    expect(integrationsService.overview).toHaveBeenCalledTimes(2);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Fechar' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows connection failure without losing the page', async () => {
    vi.mocked(integrationsService.check).mockRejectedValue(new Error('offline')); show(); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: 'Ver integração' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Testar conexão' }));
    expect(await screen.findByText('Ocorreu um erro inesperado. Tente novamente.')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it.each(['VIEWER', 'DEMO', 'UNCONFIGURED'])('does not offer unauthorized or impossible checks: %s', async mode => {
    if (mode === 'VIEWER') auth.role = 'VIEWER';
    if (mode === 'DEMO') vi.mocked(integrationsService.overview).mockResolvedValue({ ...overviewFixture, readOnly: true });
    if (mode === 'UNCONFIGURED') vi.mocked(integrationsService.detail).mockResolvedValue(hospitalFixture);
    show(); await screen.findByRole('table');
    await userEvent.click(screen.getByRole('button', { name: 'Ver integração' }));
    await screen.findByRole('heading', { name: 'Eventos recentes' });
    expect(screen.queryByRole('button', { name: 'Testar conexão' })).not.toBeInTheDocument();
  });

  it('cancels requests when leaving the page', async () => {
    const view = show(); await screen.findByRole('table');
    const signal = vi.mocked(integrationsService.overview).mock.calls[0][0];
    await act(async () => view.unmount());
    expect(signal?.aborted).toBe(true);
  });
});
