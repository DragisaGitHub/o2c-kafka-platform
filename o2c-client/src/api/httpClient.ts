import type {ApiError} from '../types';

// Generate UUID v4
export function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

interface RequestOptions extends RequestInit {
  correlationId?: string;
}

export class HttpClient {
  private readonly baseUrl: string;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
  }

  private async request<T>(
    endpoint: string,
    options: RequestOptions = {}
  ): Promise<{ data: T; correlationId?: string }> {
    const correlationId = options.correlationId || generateUUID();

    const headers: HeadersInit = {
      'Content-Type': 'application/json',
      'X-Correlation-Id': correlationId,
      ...options.headers,
    };

    try {
      const response = await fetch(`${this.baseUrl}${endpoint}`, {
        credentials: 'include',
        ...options,
        headers,
      });

      const responseCorrelationId =
        response.headers.get('X-Correlation-Id') || correlationId;

      if (!response.ok) {
        if (response.status === 401 && typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent('o2c:unauthorized'));
        }

        const errorData = await response.json().catch(() => ({
          message: response.statusText,
        }));

        throw {
          code: errorData.code || `HTTP_${response.status}`,
          message: errorData.message || response.statusText,
          correlationId: responseCorrelationId,
        };
      }

      const data = await response.json();
      return {
        data,
        correlationId: responseCorrelationId,
      };
    } catch (error) {
      if ((error as ApiError).correlationId) {
        throw error;
      }

      throw {
        message:
            error instanceof Error
                ? error.message
                : 'An unexpected error occurred',
        correlationId,
      };
    }
  }

  async get<T>(
    endpoint: string,
    correlationId?: string
  ): Promise<{ data: T; correlationId?: string }> {
    return this.request<T>(endpoint, { method: 'GET', correlationId });
  }

  async post<T>(
    endpoint: string,
    body?: unknown,
    correlationId?: string
  ): Promise<{ data: T; correlationId?: string }> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
      correlationId,
    });
  }
}
