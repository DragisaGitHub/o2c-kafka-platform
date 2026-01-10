import type { ApiError } from '../types';

export type SessionInfo = { username: string };

function toApiError(message: string, correlationId?: string): ApiError {
  return {
    code: 'AUTH_ERROR',
    message,
    correlationId,
  };
}

export const bffAuthService = {
  async getSession(): Promise<SessionInfo> {
    const res = await fetch('/api/session', { method: 'GET' });
    if (!res.ok) {
      throw toApiError(res.statusText);
    }
    return (await res.json()) as SessionInfo;
  },

  async login(username: string, password: string): Promise<{ username: string }> {
    const res = await fetch('/login', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    if (!res.ok) {
      const body = await res.json().catch(() => null);
      throw toApiError(body?.message || 'Invalid username or password');
    }

    const json = (await res.json()) as { status: string; username: string };
    return { username: json.username };
  },

  async logout(): Promise<void> {
    await fetch('/logout', { method: 'POST', credentials: 'include' });
  },
};
