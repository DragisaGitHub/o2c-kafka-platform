import { post } from './http'

const PAYMENT_BASE_URL = (import.meta.env.VITE_PAYMENT_BASE_URL as string | undefined) ?? ''

export type RetryPaymentRequest = {
  orderId: string
  retryRequestId: string
}

export type RetryPaymentResponse = {
  status: string
  retryRequestId: string
}

function generateUuidV4(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  // Minimal UUIDv4 fallback (good enough for local/demo usage)
  const bytes = new Uint8Array(16)
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = Math.floor(Math.random() * 256)
  }

  // Set version (4) and variant (10)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80

  const hex = Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')

  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export async function retryPayment(
  orderId: string,
  options?: {
    retryRequestId?: string
    correlationId?: string
  }
): Promise<RetryPaymentResponse> {
  if (!PAYMENT_BASE_URL) {
    throw new Error('VITE_PAYMENT_BASE_URL is not set')
  }

  const retryRequestId = options?.retryRequestId?.trim() ? options.retryRequestId.trim() : generateUuidV4()

  const body: RetryPaymentRequest = {
    orderId,
    retryRequestId,
  }

  return post<RetryPaymentResponse>(
    `${PAYMENT_BASE_URL}/payments/${encodeURIComponent(orderId)}/retry`,
    body,
    { correlationId: options?.correlationId }
  )
}
