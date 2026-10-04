import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, createTheme } from '@mui/material';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IntegrationsPage } from './IntegrationsPage';
import { integrationsService } from '../services/integrationsService';
import { connectionsService } from '../services/connectionsService';
import { systemsService } from '../services/systemsService';
import { auditorFixture, overviewFixture } from '../test/integrationFixtures';
const auth=vi.hoisted(()=>({role:'DEVELOPER'}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({user:{role:auth.role}})}));
vi.mock('../config/demo',()=>({isDemoMode:false}));
vi.mock('../services/integrationsService',()=>({integrationsService:{overview:vi.fn(),events:vi.fn(),check:vi.fn()}}));
vi.mock('../services/connectionsService',()=>({connectionsService:{runs:vi.fn(),get:vi.fn(),save:vi.fn()}}));
vi.mock('../services/systemsService',()=>({systemsService:{list:vi.fn()}}));
const overview={...overviewFixture,integrations:[{...auditorFixture,systemId:null},{...auditorFixture,id:'nexus-flow',name:'Nexus Flow',configured:false,status:'NOT_CONFIGURED' as const}],summary:{connected:1,operational:1,withErrors:0,eventsToday:0}};
function show(){return render(<ThemeProvider theme={createTheme()}><IntegrationsPage/></ThemeProvider>);}
beforeEach(()=>{vi.resetAllMocks();auth.role='DEVELOPER';vi.mocked(integrationsService.overview).mockResolvedValue(overview);vi.mocked(integrationsService.events).mockResolvedValue([]);vi.mocked(connectionsService.runs).mockResolvedValue({content:[],totalPages:0} as never);vi.mocked(connectionsService.get).mockResolvedValue(null);vi.mocked(systemsService.list).mockResolvedValue([]);});
afterEach(cleanup);
describe('Specialized integrations',()=>{
 it('offers only actual connectors and empty persisted activity',async()=>{show();await screen.findByRole('heading',{name:'AI Web Auditor'});expect(screen.getByRole('heading',{name:'Nexus Flow'})).toBeVisible();expect(screen.queryByText('Mapa de integrações')).toBeNull();expect(await screen.findByText('Nenhuma atividade recente')).toBeVisible();expect(screen.queryByText('Arena Predict')).toBeNull();});
 it('does not test an unconfigured connector',async()=>{show();expect(await screen.findByRole('button',{name:'Testar conexão com Nexus Flow'})).toBeDisabled();expect(integrationsService.check).not.toHaveBeenCalled();});
 it('calls the backend connection probe and refreshes evidence',async()=>{vi.mocked(integrationsService.check).mockResolvedValue({integrationId:'ai-web-auditor',status:'ONLINE',responseTimeMs:176,checkedAt:new Date().toISOString(),message:'HTTP 200 real',cached:false,nextCheckAt:new Date().toISOString()});show();await userEvent.click(await screen.findByRole('button',{name:'Testar conexão com AI Web Auditor'}));expect(await screen.findByText('HTTP 200 real')).toBeVisible();expect(integrationsService.check).toHaveBeenCalledWith('ai-web-auditor');expect(integrationsService.overview).toHaveBeenCalledTimes(2);});
 it('shows a real connection failure without inventing success',async()=>{vi.mocked(integrationsService.check).mockRejectedValue(new Error('offline'));show();await userEvent.click(await screen.findByRole('button',{name:'Testar conexão com AI Web Auditor'}));expect(await screen.findByRole('alert')).toBeVisible();expect(screen.getByRole('button',{name:'Testar conexão com AI Web Auditor'})).toBeEnabled();});
 it('retries a failed overview',async()=>{vi.mocked(integrationsService.overview).mockRejectedValueOnce(new Error('offline'));show();await userEvent.click(await screen.findByRole('button',{name:'Tentar novamente'}));expect(await screen.findByRole('heading',{name:'AI Web Auditor'})).toBeVisible();});
 it('shows loading until the bounded service request settles',()=>{vi.mocked(integrationsService.overview).mockReturnValue(new Promise(()=>undefined));show();expect(screen.getByLabelText('Carregando integrações')).toBeVisible();});
 it('keeps activity errors independent of configuration',async()=>{vi.mocked(integrationsService.events).mockRejectedValue(new Error('offline'));show();expect(await screen.findByText('Atividade indisponível')).toBeVisible();expect(screen.getByRole('heading',{name:'AI Web Auditor'})).toBeVisible();});
 it('configures its own URL without requiring a monitored system',async()=>{show();await userEvent.click(await screen.findByRole('button',{name:'Configurar AI Web Auditor'}));expect(await screen.findByLabelText(/^URL base da integração/)).toBeVisible();expect(screen.queryByLabelText('Serviço da integração')).toBeNull();expect(screen.getByRole('button',{name:'Salvar configuração'})).toBeDisabled();});
 it('viewer can read but cannot configure or probe',async()=>{auth.role='VIEWER';show();await screen.findByRole('heading',{name:'AI Web Auditor'});expect(screen.queryByRole('button',{name:'Configurar AI Web Auditor'})).toBeNull();expect(screen.queryByRole('button',{name:'Testar conexão com AI Web Auditor'})).toBeNull();});
 it('demo is explicitly read only',async()=>{vi.mocked(integrationsService.overview).mockResolvedValue({...overview,readOnly:true});show();expect(await screen.findByText('Demonstração em modo somente leitura.')).toBeVisible();expect(screen.queryByRole('button',{name:'Configurar AI Web Auditor'})).toBeNull();});
 it('honestly displays no configured integrations',async()=>{vi.mocked(integrationsService.overview).mockResolvedValue({...overview,summary:{...overview.summary,connected:0}});show();expect(await screen.findByText(/Nenhuma integração configurada/)).toBeVisible();});
});

