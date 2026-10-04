import type { AuthResponse } from '../types/api';

const SESSION_KEY = 'pulseops.session';

export function readSession(): AuthResponse | null {
  try {
    const stored = window.sessionStorage.getItem(SESSION_KEY) ?? window.localStorage.getItem(SESSION_KEY);
    if (!stored) return null;

    const session = JSON.parse(stored) as AuthResponse;
    const expires = Date.parse(session.expiresAt);
    if (!session.token || !session.user?.id || !session.user?.name || !Number.isFinite(expires) || expires <= Date.now()) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    clearSession();
    return null;
  }
}

export function writeSession(session: AuthResponse, remember = window.localStorage.getItem(SESSION_KEY) !== null): void {
  clearSession();
  (remember ? window.localStorage : window.sessionStorage).setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  window.localStorage.removeItem(SESSION_KEY);
  window.sessionStorage.removeItem(SESSION_KEY);
}
