import type { AuthResponse } from '../types/api';

const SESSION_KEY = 'pulseops.session';

export function readSession(): AuthResponse | null {
  try {
    const stored = window.localStorage.getItem(SESSION_KEY);
    if (!stored) return null;

    const session = JSON.parse(stored) as AuthResponse;
    if (!session.token || !session.user || Date.parse(session.expiresAt) <= Date.now()) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    clearSession();
    return null;
  }
}

export function writeSession(session: AuthResponse): void {
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  window.localStorage.removeItem(SESSION_KEY);
}
