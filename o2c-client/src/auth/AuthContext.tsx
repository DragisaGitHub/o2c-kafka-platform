import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { bffAuthService } from '../api/bffAuthService';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export type AuthState = {
  status: AuthStatus;
  username?: string;
  roles?: string[];
};

type AuthContextValue = {
  state: AuthState;
  refresh: () => Promise<void>;
  startLogin: (username: string, password: string) => Promise<{ challengeId: string }>;
  verifyPin: (challengeId: string, pin: string) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  const refresh = useCallback(async () => {
    try {
      const me = await bffAuthService.getMe();
      setState({ status: 'authenticated', username: me.username, roles: me.roles });
    } catch {
      setState({ status: 'anonymous' });
    }
  }, []);

  const startLogin = useCallback(async (username: string, password: string) => {
    return bffAuthService.startLogin(username, password);
  }, []);

  const verifyPin = useCallback(async (challengeId: string, pin: string) => {
    await bffAuthService.verifyPin(challengeId, pin);
  }, []);

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

  const value = useMemo<AuthContextValue>(
    () => ({ state, refresh, startLogin, verifyPin, logout }),
    [state, refresh, startLogin, verifyPin, logout]
  );

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
