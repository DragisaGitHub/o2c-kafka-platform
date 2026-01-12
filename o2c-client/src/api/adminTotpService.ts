import type { ApiError } from '../types';

export type EnrollTotpResponse = {
  setupId: string;
  username: string;
  issuer: string;
  label: string;
  expiresAt: string;
};

function dispatchUnauthorized() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('o2c:unauthorized'));
  }
}

async function parseErrorMessage(res: Response): Promise<string> {
  const contentType = res.headers.get('content-type') || '';
  if (!contentType.includes('application/json')) {
    return res.statusText;
  }

  const body = (await res.json().catch(() => null)) as
    | { message?: string; error?: string }
    | null;

  return body?.message || body?.error || res.statusText;
}

function toApiError(code: string, message: string): ApiError {
  return { code, message };
}

async function requestJson<T>(
  url: string,
  init: RequestInit,
  opts?: { dispatchUnauthorizedOn401?: boolean }
): Promise<T> {
  const res = await fetch(url, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers || {}),
    },
  });

  if (res.status === 401 && (opts?.dispatchUnauthorizedOn401 ?? true)) {
    dispatchUnauthorized();
  }

  if (!res.ok) {
    const message = await parseErrorMessage(res);

    switch (res.status) {
      case 400:
        throw toApiError('VALIDATION', message);
      case 401:
        throw toApiError('UNAUTHORIZED', message);
      case 403:
        throw toApiError('FORBIDDEN', message || "You don't have permission");
      case 404:
        throw toApiError('NOT_FOUND', message);
      case 409:
        throw toApiError('DUPLICATE', message);
      default:
        throw toApiError(`HTTP_${res.status}`, message);
    }
  }

  return (await res.json()) as T;
}

async function requestNoContent(
  url: string,
  init: RequestInit,
  opts?: { dispatchUnauthorizedOn401?: boolean; map401ToInvalidCode?: boolean }
): Promise<void> {
  const res = await fetch(url, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers || {}),
    },
  });

  if (res.status === 401 && (opts?.dispatchUnauthorizedOn401 ?? true)) {
    dispatchUnauthorized();
  }

  if (!res.ok) {
    if (res.status === 401 && opts?.map401ToInvalidCode) {
      throw toApiError('INVALID_CODE', 'Invalid code. Please try again.');
    }

    const message = await parseErrorMessage(res);

    switch (res.status) {
      case 400:
        throw toApiError('VALIDATION', message);
      case 401:
        throw toApiError('UNAUTHORIZED', message);
      case 403:
        throw toApiError('FORBIDDEN', message || "You don't have permission");
      case 404:
        throw toApiError('NOT_FOUND', message);
      case 409:
        throw toApiError('DUPLICATE', message);
      default:
        throw toApiError(`HTTP_${res.status}`, message);
    }
  }
}

const ADMIN_BASE = '/api/admin';

export const adminTotpService = {
  async enrollTotp(username: string): Promise<EnrollTotpResponse> {
    return requestJson<EnrollTotpResponse>(
      `${ADMIN_BASE}/users/${encodeURIComponent(username)}/mfa/totp/enroll`,
      { method: 'POST' }
    );
  },

  async confirmTotp(username: string, setupId: string, code: string): Promise<void> {
    await requestNoContent(
      `${ADMIN_BASE}/users/${encodeURIComponent(username)}/mfa/totp/confirm`,
      { method: 'POST', body: JSON.stringify({ setupId, code }) },
      {
        dispatchUnauthorizedOn401: false,
        map401ToInvalidCode: true,
      }
    );
  },
};
