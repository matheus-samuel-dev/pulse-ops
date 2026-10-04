import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createTheme, ThemeProvider } from '@mui/material';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ReportsPage } from './ReportsPage';
import { reportsService } from '../services/reportsService';
import { systemsService } from '../services/systemsService';
vi.mock('../services/reportsService',()=>({reportsService:{operational:vi.fn()}}));
vi.mock('../services/systemsService',()=>({systemsService:{list:vi.fn()}}));
const result={period:'7d',windowStart:'2026-10-01T00:00:00Z',windowEnd:'2026-10-03T00:00:00Z',feed:[],totalEvents:0,eventPages:0,eventPage:0,kpis:{availability:null,activeIncidents:0,incidentsOpened:0,totalHealthChecks:0,successfulHealthChecks:0,deployments:0,successfulDeployments:0,deploymentSuccessRate:null,totalTests:0,passedTests:0,testPassRate:null,averageLineCoverage:null,averageBranchCoverage:null}};
beforeEach(()=>{vi.resetAllMocks();vi.mocked(reportsService.operational).mockResolvedValue(result as never);vi.mocked(systemsService.list).mockResolvedValue([{id:'system-a',name:'Aplicação real'}] as never);});
afterEach(cleanup);
function show(){render(<ThemeProvider theme={createTheme()}><ReportsPage/></ThemeProvider>);}
it('shows absent measurements without invented percentages',async()=>{show();expect(await screen.findByText('Sem eventos')).toBeVisible();expect(screen.getAllByText('Sem dados').length).toBeGreaterThan(0);expect(screen.queryByText('0,0%')).toBeNull();expect(screen.getByRole('button',{name:'Exportar esta página'})).toBeDisabled();});
it('applies system scope to the backend and all KPIs',async()=>{show();await screen.findByText('Sem eventos');await userEvent.click(screen.getByRole('combobox',{name:'Sistema'}));await userEvent.click(screen.getByRole('option',{name:'Aplicação real'}));expect(reportsService.operational).toHaveBeenLastCalledWith('7d',undefined,'system-a',0);});
it('requests the selected window from the backend',async()=>{show();await screen.findByText('Sem eventos');await userEvent.click(screen.getByRole('combobox',{name:'Período'}));await userEvent.click(screen.getByRole('option',{name:'24 horas'}));expect(reportsService.operational).toHaveBeenLastCalledWith('24h',undefined,undefined,0);});
it('does not display stale metrics as a successful new query',async()=>{vi.mocked(reportsService.operational).mockRejectedValue(new Error('offline'));show();expect(await screen.findByText('Falha ao gerar relatório')).toBeVisible();expect(screen.queryByText('Disponibilidade média')).toBeNull();});
