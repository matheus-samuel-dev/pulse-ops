import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { login as loginRequest, registerAccount, getProfile, logout } from '../services/authService';
import { clearSession, readSession, writeSession } from '../services/sessionStorage';
import type { AuthResponse, LoginCredentials, User } from '../types/api';

interface AuthContextValue {
  user: User | null;
  isAuthenticated: boolean;
  isBootstrapping: boolean;
  signIn: (credentials: LoginCredentials, remember?: boolean) => Promise<void>;
  signUp: (input: LoginCredentials & { name: string }) => Promise<void>;
  acceptSession: (session: AuthResponse) => void;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => readSession()?.user ?? null);
  const [isBootstrapping, setBootstrapping] = useState(Boolean(readSession()));

  const clear = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  useEffect(() => {
    window.addEventListener('pulseops:unauthorized', clear);
    const session = readSession();
    const expiry = session ? window.setTimeout(clear, Math.min(2_147_483_647, Date.parse(session.expiresAt) - Date.now())) : undefined;
    return () => { window.removeEventListener('pulseops:unauthorized', clear); window.clearTimeout(expiry); };
  }, [clear, user]);

  useEffect(() => {
    if (!readSession()) return;
    let active = true;
    void getProfile().then(profile => { if (active) setUser(profile); })
      .catch(() => { if (active && !readSession()) clear(); })
      .finally(() => { if (active) setBootstrapping(false); });
    return () => { active = false; };
  }, [clear]);

  const signOut = useCallback(async () => { try { await logout(); } finally { clear(); } }, [clear]);
  const acceptSession = useCallback((session: AuthResponse) => { writeSession(session); setUser(session.user); }, []);
  const signUp = useCallback(async (input: LoginCredentials & { name: string }) => {
    const session = await registerAccount(input); writeSession(session, false); setUser(session.user);
  }, []);

  const signIn = useCallback(async (credentials: LoginCredentials, remember = false) => {
    const session = await loginRequest(credentials);
    writeSession(session, remember);
    setUser(session.user);
  }, []);

  const value = useMemo(
    () => ({ user, isAuthenticated: Boolean(user), isBootstrapping, signIn, signUp, acceptSession, signOut }),
    [user, isBootstrapping, signIn, signUp, acceptSession, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth deve ser usado dentro de AuthProvider.');
  return value;
}
