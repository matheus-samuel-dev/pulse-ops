import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { login as loginRequest } from '../services/authService';
import { clearSession, readSession, writeSession } from '../services/sessionStorage';
import type { LoginCredentials, User } from '../types/api';

interface AuthContextValue {
  user: User | null;
  isAuthenticated: boolean;
  isBootstrapping: boolean;
  signIn: (credentials: LoginCredentials) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => readSession()?.user ?? null);
  const isBootstrapping = false;

  const signOut = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  useEffect(() => {
    window.addEventListener('pulseops:unauthorized', signOut);
    return () => window.removeEventListener('pulseops:unauthorized', signOut);
  }, [signOut]);

  const signIn = useCallback(async (credentials: LoginCredentials) => {
    const session = await loginRequest(credentials);
    writeSession(session);
    setUser(session.user);
  }, []);

  const value = useMemo(
    () => ({ user, isAuthenticated: Boolean(user), isBootstrapping, signIn, signOut }),
    [user, isBootstrapping, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth deve ser usado dentro de AuthProvider.');
  return value;
}
