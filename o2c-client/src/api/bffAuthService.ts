import type { ApiError } from '../types';

export type MeInfo = { username: string; roles: string[] };

export type StartLoginResult =
  | { status: 'MFA_REQUIRED'; challengeId: string }
  | { status: 'ENROLL_REQUIRED'; setupId: string }
  | { status: 'AUTHENTICATED'; username?: string };

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

  async startLogin(username: string, password: string): Promise<StartLoginResult> {
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

    const json = (await res.json()) as { status: string; challengeId?: string | null; username?: string };
    if (json.status === 'AUTHENTICATED') {
      return { status: 'AUTHENTICATED', username: json.username };
    }

    if (json.status === 'MFA_REQUIRED' && json.challengeId) {
      return { status: 'MFA_REQUIRED', challengeId: json.challengeId };
    }

    if (json.status === 'ENROLL_REQUIRED' && json.challengeId) {
      return { status: 'ENROLL_REQUIRED', setupId: json.challengeId };
    }

    throw toApiError('Unexpected login response');
  },

  async confirmTotpEnrollment(setupId: string, code: string): Promise<{ username: string }> {
    const res = await fetch('/auth/mfa/totp/confirm', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ setupId, code }),
    });

    if (!res.ok) {
      if (res.status === 401) {
        throw toApiError('Invalid code');
      }
      const body = await res.json().catch(() => null);
      throw toApiError(body?.message || res.statusText);
    }

    const json = (await res.json()) as { status: string; username: string };
    return { username: json.username };
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
        throw toApiError('Invalid or expired code');
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
