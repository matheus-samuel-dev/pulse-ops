import { api } from './api';
import type { AuthResponse, LoginCredentials, User } from '../types/api';

export async function login(credentials: LoginCredentials): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', credentials, { timeout: 30_000 });
  return data;
}

export async function registerAccount(input: LoginCredentials & { name: string }): Promise<AuthResponse> {
  return (await api.post<AuthResponse>('/auth/register', input, { timeout: 30_000 })).data;
}
export async function getProfile(): Promise<User> { return (await api.get<User>('/account/me')).data; }
export async function saveProfile(input: { name: string; email: string; currentPassword?: string }): Promise<AuthResponse> {
  return (await api.put<AuthResponse>('/account/me', input)).data;
}
export async function logout(): Promise<void> { await api.post('/account/logout'); }
