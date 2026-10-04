import { ThemeProvider, createTheme } from '@mui/material';
import { render, screen, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SystemFormDialog } from './SystemFormDialog';
const initial={name:'Real app',baseUrl:'https://example.org',healthEndpoint:'/',environment:'PRODUCTION' as const,active:true,monitoringIntervalSeconds:60,maintenance:false,expectedStatusCode:200,timeoutMs:1000,latencyThresholdMs:500,targetAvailability:99.9};
afterEach(cleanup);
describe('System form',()=>{
  function show(submit=vi.fn()){render(<ThemeProvider theme={createTheme()}><SystemFormDialog open initial={initial} onClose={vi.fn()} onSubmit={submit}/></ThemeProvider>);return submit;}
  it('sends a complete validated configuration',async()=>{const submit=show();await userEvent.click(within(screen.getByRole('dialog')).getByRole('button',{name:'Cadastrar sistema'}));expect(submit).toHaveBeenCalledWith({...initial,description:''});});
  it('rejects non HTTP URLs before submitting',async()=>{const submit=show();await userEvent.clear(screen.getByLabelText('URL base'));await userEvent.type(screen.getByLabelText('URL base'),'file:///etc/passwd');await userEvent.click(screen.getByRole('button',{name:'Cadastrar sistema'}));expect(await screen.findByText('Use HTTP ou HTTPS.')).toBeVisible();expect(submit).not.toHaveBeenCalled();});
  it('renders backend errors in the dialog and restores the submit button',async()=>{show(vi.fn().mockRejectedValue(new Error('offline')));await userEvent.click(screen.getByRole('button',{name:'Cadastrar sistema'}));expect(await screen.findByRole('alert')).toBeVisible();expect(screen.getByRole('button',{name:'Cadastrar sistema'})).toBeEnabled();});
});
