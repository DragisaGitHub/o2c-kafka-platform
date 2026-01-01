export const CORRELATION_ID_HEADER = 'X-Correlation-Id'

export type HttpOptions = Omit<RequestInit, 'method' | 'body'> & {
  headers?: HeadersInit
  correlationId?: string
}

export type JsonRequestOptions = HttpOptions & {
  /** Allows overriding JSON parsing behavior for empty responses. */
  allowEmptyJson?: boolean
}

export type JsonResponse<T> = {
  data: T
  correlationId: string
  response: Response
}

export function generateCorrelationId(): string {
  // Prefer RFC 4122 UUIDs where available.
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  // Fallback: not a UUID, but stable enough for local correlation.
  return `cid_${Date.now().toString(16)}_${Math.random().toString(16).slice(2)}`
}

export function ensureCorrelationId(options?: { correlationId?: string }): string {
  return options?.correlationId?.trim() ? options.correlationId.trim() : generateCorrelationId()
}

export function withCorrelationIdHeader(headers: HeadersInit | undefined, correlationId: string): Headers {
  const merged = new Headers(headers)
  merged.set(CORRELATION_ID_HEADER, correlationId)
  return merged
}

async function requestJson<T>(url: string, init: RequestInit, options?: JsonRequestOptions): Promise<JsonResponse<T>> {
  const correlationId = ensureCorrelationId(options)

  const headers = withCorrelationIdHeader(options?.headers ?? init.headers, correlationId)
  const response = await fetch(url, { ...init, headers, ...options })

  // Do not normalize errors here (handled in T011). We still throw on non-2xx.
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status} ${response.statusText}`)
    ;(error as any).status = response.status
    ;(error as any).correlationId = correlationId
    throw error
  }

  // Handle empty bodies (e.g., 204) safely.
  const allowEmpty = options?.allowEmptyJson ?? true
  if (allowEmpty && (response.status === 204 || response.headers.get('content-length') === '0')) {
    return { data: undefined as unknown as T, correlationId, response }
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.toLowerCase().includes('application/json')) {
    const text = await response.text()
    return { data: text as unknown as T, correlationId, response }
  }

  const data = (await response.json()) as T
  return { data, correlationId, response }
}

export async function get<T>(url: string, options?: HttpOptions): Promise<T> {
  const result = await requestJson<T>(url, { method: 'GET' }, options)
  return result.data
}

export async function post<T>(url: string, body: unknown, options?: HttpOptions): Promise<T> {
  const correlationId = ensureCorrelationId(options)
  const headers = withCorrelationIdHeader(options?.headers, correlationId)
  headers.set('Accept', 'application/json')
  headers.set('Content-Type', 'application/json')

  const result = await requestJson<T>(
    url,
    {
      method: 'POST',
      body: JSON.stringify(body),
      headers,
    },
    { ...options, correlationId }
  )

  return result.data
}

// Exported for reuse/testing by higher-level API modules.
export const _internal = {
  requestJson,
}
