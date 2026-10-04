import { ThemeProvider, createTheme } from '@mui/material';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RegisterPage } from './RegisterPage';
import { ProfilePage } from './ProfilePage';
const signUp=vi.hoisted(()=>vi.fn()); const acceptSession=vi.hoisted(()=>vi.fn());
const accountUser=vi.hoisted(()=>({id:'user',name:'Pessoa real',email:'person@example.org',role:'DEVELOPER'}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({isAuthenticated:false,signUp,acceptSession,user:accountUser})}));
vi.mock('../services/authService',()=>({saveProfile:vi.fn()}));
import { saveProfile } from '../services/authService';
function show(page: React.ReactNode){return render(<MemoryRouter><ThemeProvider theme={createTheme()}>{page}</ThemeProvider></MemoryRouter>);}
describe('Account forms',()=>{
  beforeEach(()=>{vi.resetAllMocks();});afterEach(cleanup);
  async function signup(){show(<RegisterPage/>);await userEvent.type(screen.getByLabelText('Nome'),'Pessoa real');await userEvent.type(screen.getByLabelText('E-mail'),'person@example.org');await userEvent.type(screen.getByLabelText('Senha',{exact:true}),'Password@2026');}
  it('rejects a different password confirmation',async()=>{await signup();await userEvent.type(screen.getByLabelText('Confirmar senha'),'different');await userEvent.click(screen.getByRole('button',{name:'Criar conta'}));expect(await screen.findByText('As senhas devem ser iguais.')).toBeVisible();expect(signUp).not.toHaveBeenCalled();});
  it('creates an account with validated fields',async()=>{await signup();await userEvent.type(screen.getByLabelText('Confirmar senha'),'Password@2026');await userEvent.click(screen.getByRole('button',{name:'Criar conta'}));expect(signUp).toHaveBeenCalledWith({name:'Pessoa real',email:'person@example.org',password:'Password@2026'});});
  it('presents server registration errors without an infinite loader',async()=>{signUp.mockRejectedValue(new Error('offline'));await signup();await userEvent.type(screen.getByLabelText('Confirmar senha'),'Password@2026');await userEvent.click(screen.getByRole('button',{name:'Criar conta'}));expect(await screen.findByRole('alert')).toBeVisible();expect(screen.getByRole('button',{name:'Criar conta'})).toBeEnabled();});
  it('edits the actual profile and updates the auth session',async()=>{show(<ProfilePage/>);vi.mocked(saveProfile).mockResolvedValue({token:'new',tokenType:'Bearer',expiresAt:'2030-01-01',user:{id:'user',name:'Nome atualizado',email:'person@example.org',role:'DEVELOPER'}});await userEvent.clear(screen.getByLabelText('Nome'));await userEvent.type(screen.getByLabelText('Nome'),'Nome atualizado');await userEvent.click(screen.getByRole('button',{name:'Salvar alterações'}));expect(await screen.findByText('Perfil atualizado com sucesso.')).toBeVisible();expect(saveProfile).toHaveBeenCalledWith({name:'Nome atualizado',email:'person@example.org',currentPassword:''});expect(acceptSession).toHaveBeenCalled();});
  it('asks for the current password when changing email',async()=>{show(<ProfilePage/>);await userEvent.clear(screen.getByLabelText('E-mail'));await userEvent.type(screen.getByLabelText('E-mail'),'changed@example.org');expect(await screen.findByLabelText('Senha atual')).toBeVisible();});
  it('shows a profile failure and permits another attempt',async()=>{show(<ProfilePage/>);vi.mocked(saveProfile).mockRejectedValue(new Error('offline'));await userEvent.click(screen.getByRole('button',{name:'Salvar alterações'}));expect(await screen.findByRole('alert')).toBeVisible();expect(screen.getByRole('button',{name:'Salvar alterações'})).toBeEnabled();});
});
