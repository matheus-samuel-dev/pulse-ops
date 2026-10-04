import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SettingsPage } from './SettingsPage';
import { api } from '../services/api';
import type { AxiosResponse } from 'axios';
const context=vi.hoisted(()=>({ user:{id:'operator',name:'Operador real',email:'operator@example.org',role:'DEVELOPER'},toggle:vi.fn() }));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({user:context.user})}));
vi.mock('../theme/PulseOpsThemeProvider',()=>({useColorMode:()=>({mode:'dark',toggleColorMode:context.toggle})}));
vi.mock('../services/api',()=>({api:{get:vi.fn()},getApiErrorMessage:()=> 'Não foi possível carregar os parâmetros.'}));
vi.mock('../services/usersService',()=>({usersService:{list:vi.fn(async()=>[]),create:vi.fn(),remove:vi.fn()}}));
vi.mock('../config/demo',()=>({isDemoMode:false}));
function show(){return render(<MemoryRouter><Routes><Route path="/" element={<SettingsPage/>}/><Route path="/perfil" element={<h1>Perfil da conta</h1>}/></Routes></MemoryRouter>);}
describe('Configurações funcionais',()=>{
  beforeEach(()=>{vi.clearAllMocks();vi.mocked(api.get).mockResolvedValue({data:{enabled:false,intervalMs:90000,failuresToOpen:4,successesToResolve:3}} as AxiosResponse);});
  afterEach(cleanup);
  it('mostra os parâmetros ativos da API e o usuário autenticado sem conceder administração',async()=>{
    show();await screen.findByText('90 segundos');expect(screen.getByText('Pausado')).toBeInTheDocument();expect(screen.getByText('4 falhas consecutivas')).toBeInTheDocument();expect(screen.getByText('3 respostas saudáveis')).toBeInTheDocument();
    expect(screen.getByText('Operador real')).toBeInTheDocument();expect(screen.queryByText('Adicionar pessoa')).not.toBeInTheDocument();
  });
  it('abre o perfil real e aplica a preferência de tema',async()=>{
    show();await screen.findByText('90 segundos');await userEvent.click(screen.getByRole('switch',{name:'Alternar tema claro e escuro'}));expect(context.toggle).toHaveBeenCalledOnce();
    await userEvent.click(screen.getByRole('button',{name:'Editar perfil'}));expect(screen.getByRole('heading',{name:'Perfil da conta'})).toBeInTheDocument();
  });
  it('não deixa os parâmetros carregando infinitamente quando a API falha',async()=>{
    vi.mocked(api.get).mockRejectedValueOnce(new Error('offline'));show();await screen.findByText('Não foi possível carregar os parâmetros.');
    expect(screen.queryByText('Carregando parâmetros…')).not.toBeInTheDocument();expect(screen.queryByText('60 segundos')).not.toBeInTheDocument();
  });
});
