import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuditPage } from './AuditPage';
import { eventsService, type RecordedEvent } from '../services/eventsService';
import type { PageResponse } from '../types/api';
vi.mock('../services/eventsService',()=>({eventsService:{list:vi.fn()}}));
vi.mock('../services/systemsService',()=>({systemsService:{list:vi.fn(async()=>[])}}));
function result(content:RecordedEvent[]=[]):PageResponse<RecordedEvent>{return {content,totalElements:content.length,totalPages:content.length?1:0,number:0,size:20,first:true,last:true,numberOfElements:content.length,empty:!content.length};}
const event:RecordedEvent={id:'event-1',systemName:'Aplicação de teste',environment:'PRODUCTION',type:'INCIDENT_RESOLVED',severity:'SUCCESS',title:'Incidente resolvido',description:'Duas verificações confirmaram a recuperação.',source:'Monitoramento PulseOps',occurredAt:'2026-10-03T12:00:00Z'};
describe('Eventos persistidos',()=>{
  beforeEach(()=>{vi.mocked(eventsService.list).mockReset();vi.mocked(eventsService.list).mockResolvedValue(result());});
  afterEach(cleanup);
  it('exibe um estado vazio em vez de reconstruir uma timeline demonstrativa',async()=>{
    render(<AuditPage/>);expect(screen.getByRole('status')).toHaveTextContent('Carregando eventos');
    await screen.findByText('Nenhum evento registrado');expect(screen.queryByText('Incidente resolvido')).not.toBeInTheDocument();
  });
  it('exibe a descrição, origem e severidade realmente recebidas',async()=>{
    vi.mocked(eventsService.list).mockResolvedValue(result([event]));render(<AuditPage/>);
    await screen.findByText('Incidente resolvido');expect(screen.getByText(event.description!)).toBeInTheDocument();
    expect(screen.getByText(/Monitoramento PulseOps/)).toBeInTheDocument();expect(screen.getByText('Sucesso')).toBeInTheDocument();
  });
  it('debounceia a busca e envia a severidade selecionada ao backend',async()=>{
    render(<AuditPage/>);await screen.findByText('Nenhum evento registrado');
    await userEvent.type(screen.getByLabelText('Buscar eventos'),'resolvido');
    await waitFor(()=>expect(eventsService.list).toHaveBeenLastCalledWith(expect.objectContaining({query:'resolvido',page:0}),expect.any(AbortSignal)));
    await userEvent.click(screen.getByRole('combobox',{name:'Severidade'}));await userEvent.click(screen.getByRole('option',{name:'Crítico'}));
    await waitFor(()=>expect(eventsService.list).toHaveBeenLastCalledWith(expect.objectContaining({query:'resolvido',severity:'CRITICAL',page:0}),expect.any(AbortSignal)));
  });
  it('encerra carregamento em falha e tenta novamente com a mesma consulta',async()=>{
    vi.mocked(eventsService.list).mockRejectedValueOnce(new Error('offline'));render(<AuditPage/>);
    await screen.findByText('Não foi possível carregar os eventos');await userEvent.click(screen.getByRole('button',{name:'Tentar novamente'}));
    await screen.findByText('Nenhum evento registrado');expect(screen.queryByText('Carregando eventos…')).not.toBeInTheDocument();
  });
});
