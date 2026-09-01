import axios, { AxiosError } from 'axios';
import { clearSession, readSession } from './sessionStorage';
import type { ApiErrorPayload } from '../types/api';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  timeout: 12_000,
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = readSession()?.token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorPayload>) => {
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      clearSession();
      window.dispatchEvent(new CustomEvent('pulseops:unauthorized'));
    }
    return Promise.reject(error);
  },
);

export function getApiErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorPayload>(error)) {
    if (!error.response) {
      return 'Não foi possível alcançar a API do PulseOps. Verifique sua conexão e tente novamente.';
    }
    return error.response.data?.message ?? 'A solicitação não pôde ser concluída.';
  }
  return 'Ocorreu um erro inesperado. Tente novamente.';
}
