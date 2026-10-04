import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearSession, readSession, writeSession } from './sessionStorage';
import type { AuthResponse } from '../types/api';

const session: AuthResponse = {
  token: 'signed-token',
  tokenType: 'Bearer',
  expiresAt: '2026-09-01T15:00:00Z',
  user: { id: 'user-1', name: 'Marina Costa', email: 'admin@pulseops.dev', role: 'ADMIN' },
};

describe('session storage', () => {
  beforeEach(() => {
    window.localStorage.clear(); window.sessionStorage.clear();
    vi.setSystemTime(new Date('2026-08-31T15:00:00Z'));
  });

  it('round-trips a valid authenticated session', () => {
    writeSession(session);
    expect(readSession()).toEqual(session);
  });

  it('drops expired and malformed sessions', () => {
    writeSession({ ...session, expiresAt: '2026-08-30T15:00:00Z' });
    expect(readSession()).toBeNull();
    window.localStorage.setItem('pulseops.session', '{invalid');
    expect(readSession()).toBeNull();
  });

  it('clears the stored session explicitly', () => {
    writeSession(session);
    clearSession();
    expect(readSession()).toBeNull();
  });
});
