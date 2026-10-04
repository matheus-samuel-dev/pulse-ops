import { ThemeProvider, createTheme } from '@mui/material';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';
const signIn = vi.hoisted(() => vi.fn(async () => undefined));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: null, isAuthenticated: false, isBootstrapping: false, signIn, signOut: vi.fn() }) }));
function show(){return render(<MemoryRouter><ThemeProvider theme={createTheme()}><LoginPage /></ThemeProvider></MemoryRouter>);}
describe('LoginPage', () => {
  beforeEach(() => { signIn.mockReset(); }); afterEach(cleanup);
  it('offers account creation and never exposes demo credentials by default', () => { show();expect(screen.getByRole('link',{name:'Criar conta'})).toHaveAttribute('href','/cadastro');expect(screen.queryByText('admin@pulseops.dev')).not.toBeInTheDocument(); });
  it('validates credentials before calling the backend',async()=>{show();await userEvent.click(screen.getByRole('button',{name:'Entrar no PulseOps'}));expect(signIn).not.toHaveBeenCalled();expect(screen.getByText('Informe seu e-mail.')).toBeVisible();});
  it('submits credentials and the persistence preference',async()=>{show();await userEvent.type(screen.getByLabelText('E-mail'),'operator@example.org');await userEvent.type(screen.getByLabelText('Senha'),'Password@2026');await userEvent.click(screen.getByRole('checkbox',{name:'Manter conectado'}));await userEvent.click(screen.getByRole('button',{name:'Entrar no PulseOps'}));expect(signIn).toHaveBeenCalledWith({email:'operator@example.org',password:'Password@2026'},true);});
  it('shows an error and finishes loading after failure',async()=>{signIn.mockRejectedValue(new Error('offline'));show();await userEvent.type(screen.getByLabelText('E-mail'),'operator@example.org');await userEvent.type(screen.getByLabelText('Senha'),'Password@2026');await userEvent.click(screen.getByRole('button',{name:'Entrar no PulseOps'}));expect(await screen.findByRole('alert')).toHaveTextContent('Ocorreu um erro inesperado');expect(screen.getByRole('button',{name:'Entrar no PulseOps'})).toBeEnabled();});
});
