import type { ApiError } from '../types';

export type MeInfo = { username: string; roles: string[] };

function toApiError(message: string, correlationId?: string): ApiError {
  return {
    code: 'AUTH_ERROR',
    message,
    correlationId,
  };
}

function dispatchUnauthorized() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('o2c:unauthorized'));
  }
}

export const bffAuthService = {
  async getMe(): Promise<MeInfo> {
    const res = await fetch('/api/me', { method: 'GET', credentials: 'include' });
    if (!res.ok) {
      if (res.status === 401) {
        dispatchUnauthorized();
      }
      throw toApiError(res.statusText);
    }
    return (await res.json()) as MeInfo;
  },

  async startLogin(username: string, password: string): Promise<{ challengeId: string }> {
    const res = await fetch('/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    if (!res.ok) {
      if (res.status === 401) {
        throw toApiError('Invalid username or password');
      }
      const body = await res.json().catch(() => null);
      throw toApiError(body?.message || res.statusText);
    }

    const json = (await res.json()) as { status: string; challengeId: string };
    return { challengeId: json.challengeId };
  },

  async verifyPin(challengeId: string, pin: string): Promise<{ username: string }> {
    const res = await fetch('/auth/mfa/verify', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ challengeId, pin }),
    });

    if (!res.ok) {
      if (res.status === 401) {
        throw toApiError('Invalid or expired PIN');
      }
      const body = await res.json().catch(() => null);
      throw toApiError(body?.message || res.statusText);
    }

    const json = (await res.json()) as { status: string; username: string };
    return { username: json.username };
  },

  async logout(): Promise<void> {
    await fetch('/logout', { method: 'POST', credentials: 'include' });
  },
};
