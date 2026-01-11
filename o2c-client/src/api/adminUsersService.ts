import type { ApiError, AdminUserDetails, AdminUserSummary, CreateAdminUserRequest } from '../types';

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
  init: RequestInit
): Promise<T> {
  const res = await fetch(url, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers || {}),
    },
  });

  if (res.status === 401) {
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
  init: RequestInit
): Promise<void> {
  const res = await fetch(url, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers || {}),
    },
  });

  if (res.status === 401) {
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
}

const ADMIN_BASE = '/api/admin';

export const adminUsersService = {
  async listUsers(): Promise<AdminUserSummary[]> {
    return requestJson<AdminUserSummary[]>(`${ADMIN_BASE}/users`, {
      method: 'GET',
    });
  },

  async getUser(username: string): Promise<AdminUserDetails> {
    return requestJson<AdminUserDetails>(
      `${ADMIN_BASE}/users/${encodeURIComponent(username)}`,
      { method: 'GET' }
    );
  },

  async createUser(payload: CreateAdminUserRequest): Promise<void> {
    await requestNoContent(`${ADMIN_BASE}/users`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  async setEnabled(username: string, enabled: boolean): Promise<void> {
    await requestNoContent(
      `${ADMIN_BASE}/users/${encodeURIComponent(username)}/enabled`,
      { method: 'PATCH', body: JSON.stringify({ enabled }) }
    );
  },

  async setRoles(username: string, roles: string[]): Promise<void> {
    await requestNoContent(
      `${ADMIN_BASE}/users/${encodeURIComponent(username)}/roles`,
      { method: 'PATCH', body: JSON.stringify({ roles }) }
    );
  },

  async resetPassword(username: string, password: string): Promise<void> {
    await requestNoContent(
      `${ADMIN_BASE}/users/${encodeURIComponent(username)}/password`,
      { method: 'POST', body: JSON.stringify({ password }) }
    );
  },
};
