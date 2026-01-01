import { get } from './http'

const CHECKOUT_BASE_URL = (import.meta.env.VITE_CHECKOUT_BASE_URL as string | undefined) ?? ''
const PAYMENT_BASE_URL = (import.meta.env.VITE_PAYMENT_BASE_URL as string | undefined) ?? ''

export type CheckoutTimelineEventDto = {
  type: string
  status: string
  at: string
}

export type PaymentTimelineEventDto = {
  type: string
  status: string
  at: string
  failureReason?: string | null
}

export async function getCheckoutTimeline(orderId: string): Promise<CheckoutTimelineEventDto[]> {
  if (!CHECKOUT_BASE_URL) {
    throw new Error('VITE_CHECKOUT_BASE_URL is not set')
  }

  return get<CheckoutTimelineEventDto[]>(`${CHECKOUT_BASE_URL}/checkouts/${encodeURIComponent(orderId)}/timeline`)
}

export async function getPaymentTimeline(orderId: string): Promise<PaymentTimelineEventDto[]> {
  if (!PAYMENT_BASE_URL) {
    throw new Error('VITE_PAYMENT_BASE_URL is not set')
  }

  return get<PaymentTimelineEventDto[]>(`${PAYMENT_BASE_URL}/payments/${encodeURIComponent(orderId)}/timeline`)
}
