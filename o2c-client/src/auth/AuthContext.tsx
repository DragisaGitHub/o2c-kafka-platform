import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { bffAuthService } from '../api/bffAuthService';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export type AuthState = {
  status: AuthStatus;
  username?: string;
};

type AuthContextValue = {
  state: AuthState;
  refresh: () => Promise<void>;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  const refresh = useCallback(async () => {
    try {
      const session = await bffAuthService.getSession();
      setState({ status: 'authenticated', username: session.username });
    } catch {
      setState({ status: 'anonymous' });
    }
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    await bffAuthService.login(username, password);
    await refresh();
  }, [refresh]);

  const logout = useCallback(async () => {
    await bffAuthService.logout();
    setState({ status: 'anonymous' });
  }, []);

  useEffect(() => {
    (async () => {
      await refresh();
    })();
  }, [refresh]);

  useEffect(() => {
    const onUnauthorized = () => setState({ status: 'anonymous' });
    window.addEventListener('o2c:unauthorized', onUnauthorized as EventListener);
    return () => window.removeEventListener('o2c:unauthorized', onUnauthorized as EventListener);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({ state, refresh, login, logout }), [state, refresh, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
