import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DashboardPage } from './DashboardPage';
import { getDashboard } from '../services/dashboardService';
import type { DashboardData } from '../types/api';
vi.mock('../services/dashboardService', () => ({ getDashboard: vi.fn() }));
const empty: DashboardData = { summary: { monitoredSystems: 0, averageAvailability: null, availabilityChange: null, openIncidents: 0, incidentChange: 0, averageCoverage: null, coverageChange: null, deployments: 0, operationalSystems: 0, degradedSystems: 0, downSystems: 0,configurationRequiredSystems:0,problemSystems:0, overallHealth: 'UNKNOWN', period: '24h' }, latency: [], errors: { serverErrors:0,clientErrors:0,timeouts:0,others:0,total:0,period:'24h' }, health: [] };
function show() { return render(<MemoryRouter><Routes><Route path="/" element={<DashboardPage/>}/><Route path="/sistemas" element={<h1>Cadastro aberto</h1>}/></Routes></MemoryRouter>); }
describe('Visão geral operacional', () => {
  beforeEach(() => { vi.mocked(getDashboard).mockReset(); vi.mocked(getDashboard).mockResolvedValue(empty); });
  afterEach(cleanup);
  it('não inventa disponibilidade, latência ou falhas no primeiro acesso e abre o cadastro', async () => {
    show(); await screen.findByText('Cadastre seu primeiro sistema');
    expect(screen.getByText('Ainda não há amostras de latência')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma falha registrada')).toBeInTheDocument();
    expect(screen.queryByText('100,0%')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button',{name:'Cadastrar sistema'}));
    expect(screen.getByRole('heading',{name:'Cadastro aberto'})).toBeInTheDocument();
  });
  it('envia filtros de período e ambiente à API em vez de alterar apenas o rótulo', async () => {
    show(); await screen.findByText('Cadastre seu primeiro sistema');
    await userEvent.click(screen.getByRole('combobox',{name:'Período'})); await userEvent.click(screen.getByRole('option',{name:'Últimos 7 dias'}));
    await waitFor(()=>expect(getDashboard).toHaveBeenLastCalledWith({period:'7d',environment:'ALL'}));
    await screen.findByText('Cadastre seu primeiro sistema');
    await userEvent.click(screen.getByRole('combobox',{name:'Ambiente'})); await userEvent.click(screen.getByRole('option',{name:'Produção'}));
    await waitFor(()=>expect(getDashboard).toHaveBeenLastCalledWith({period:'7d',environment:'PRODUCTION'}));
  });
  it('encerra o carregamento com erro e permite tentar novamente', async () => {
    vi.mocked(getDashboard).mockRejectedValueOnce(new Error('controlled failure')); show();
    await screen.findByText('Não foi possível carregar o painel');
    await userEvent.click(screen.getByRole('button',{name:'Tentar novamente'}));
    await screen.findByText('Cadastre seu primeiro sistema'); expect(getDashboard).toHaveBeenCalledTimes(2);
  });
  it('preserva dados reais anteriores e informa falha ao atualizar', async () => {
    show(); await screen.findByText('Cadastre seu primeiro sistema'); vi.mocked(getDashboard).mockRejectedValueOnce(new Error('offline'));
    await userEvent.click(screen.getByRole('button',{name:'Atualizar'}));
    await screen.findByText(/Dados anteriores preservados/); expect(screen.getByRole('button',{name:'Atualizar'})).toBeEnabled();
    expect(screen.getByText('Cadastre seu primeiro sistema')).toBeInTheDocument();
  });
  it('mantém o carregamento até a resposta efetiva, sem exibir um painel fictício', async () => {
    let finish!: (data:DashboardData)=>void; vi.mocked(getDashboard).mockReturnValue(new Promise(resolve=>{finish=resolve;}));
    show(); expect(screen.queryByText('Cadastre seu primeiro sistema')).not.toBeInTheDocument();
    finish(empty); await screen.findByText('Cadastre seu primeiro sistema');
  });
});
