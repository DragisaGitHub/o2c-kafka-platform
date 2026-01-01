export type NormalizedApiError = {
  code: string
  message: string
  status: number
  correlationId?: string
}

const CORRELATION_ID_HEADER = 'X-Correlation-Id'

type BackendErrorPayload = {
  code?: unknown
  message?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

function pickFirstString(...candidates: Array<unknown>): string | undefined {
  for (const candidate of candidates) {
    const s = asString(candidate)
    if (s && s.trim()) return s
  }
  return undefined
}

function getStatusFromUnknown(err: unknown): number | undefined {
  if (!isRecord(err)) return undefined
  const status = err.status
  return typeof status === 'number' ? status : undefined
}

function getCorrelationIdFromUnknown(err: unknown): string | undefined {
  if (!isRecord(err)) return undefined
  return pickFirstString(err.correlationId)
}

function getResponseFromUnknown(err: unknown): Response | undefined {
  if (err instanceof Response) return err
  if (!isRecord(err)) return undefined
  const response = err.response
  return response instanceof Response ? response : undefined
}

async function tryReadBackendErrorPayload(response: Response): Promise<BackendErrorPayload | undefined> {
  const contentType = (response.headers.get('content-type') ?? '').toLowerCase()

  // Clone so that if callers need the body later, this doesn't consume it.
  const clone = response.clone()

  if (contentType.includes('application/json')) {
    try {
      const json = (await clone.json()) as unknown
      return isRecord(json) ? (json as BackendErrorPayload) : undefined
    } catch {
      return undefined
    }
  }

  // Non-JSON: treat body as message if present.
  try {
    const text = await clone.text()
    if (!text.trim()) return undefined
    return { message: text }
  } catch {
    return undefined
  }
}

/**
 * Normalizes unknown errors originating from `fetch` or our `http.ts` wrappers.
 *
 * - No logging, no UI concerns.
 * - Attempts to extract backend `{ code, message }` JSON.
 * - Attempts to extract `X-Correlation-Id` from response headers.
 */
export async function normalizeApiError(err: unknown): Promise<NormalizedApiError> {
  const response = getResponseFromUnknown(err)

  if (response) {
    const payload = await tryReadBackendErrorPayload(response)

    const correlationId = pickFirstString(
      response.headers.get(CORRELATION_ID_HEADER),
      getCorrelationIdFromUnknown(err)
    )

    const status = response.status
    const code = pickFirstString(payload?.code) ?? 'HTTP_ERROR'

    const message =
      pickFirstString(payload?.message) ??
      pickFirstString((isRecord(err) ? err.message : undefined)) ??
      (response.statusText || 'Request failed')

    return { code, message, status, correlationId }
  }

  // Common case for our current `http.ts`: it throws an Error with `.status` and `.correlationId`.
  const status = getStatusFromUnknown(err) ?? 0
  const correlationId = getCorrelationIdFromUnknown(err)

  // Network / CORS failures from fetch are typically TypeError with no response.
  const message =
    pickFirstString(isRecord(err) ? err.message : undefined) ??
    (status === 0 ? 'Network error' : 'Request failed')

  return {
    code: status === 0 ? 'NETWORK_ERROR' : 'HTTP_ERROR',
    message,
    status,
    correlationId,
  }
}

/**
 * Synchronous variant when no `Response` is available.
 * Useful for callers that only have errors thrown from `http.ts`.
 */
export function normalizeApiErrorSync(err: unknown): NormalizedApiError {
  const status = getStatusFromUnknown(err) ?? 0
  const correlationId = getCorrelationIdFromUnknown(err)

  const message =
    pickFirstString(isRecord(err) ? err.message : undefined) ??
    (status === 0 ? 'Network error' : 'Request failed')

  return {
    code: status === 0 ? 'NETWORK_ERROR' : 'HTTP_ERROR',
    message,
    status,
    correlationId,
  }
}
