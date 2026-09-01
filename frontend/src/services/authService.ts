import { api } from './api';
import type { AuthResponse, LoginCredentials } from '../types/api';

export async function login(credentials: LoginCredentials): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', credentials);
  return data;
}
