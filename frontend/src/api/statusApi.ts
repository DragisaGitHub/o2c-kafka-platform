import { get } from './http'

const CHECKOUT_BASE_URL = (import.meta.env.VITE_CHECKOUT_BASE_URL as string | undefined) ?? ''
const PAYMENT_BASE_URL = (import.meta.env.VITE_PAYMENT_BASE_URL as string | undefined) ?? ''

export type CheckoutStatusDto = {
  orderId: string
  status: string
}

export type PaymentStatusDto = {
  orderId: string
  status: string
  failureReason?: string | null
}

export async function getCheckoutStatuses(orderIds: string[]): Promise<Record<string, CheckoutStatusDto>> {
  if (orderIds.length === 0) return {}
  if (!CHECKOUT_BASE_URL) {
    throw new Error('VITE_CHECKOUT_BASE_URL is not set')
  }

  const params = new URLSearchParams({ orderIds: orderIds.join(',') })
  const list = await get<CheckoutStatusDto[]>(`${CHECKOUT_BASE_URL}/checkouts/status?${params.toString()}`)

  const byOrderId: Record<string, CheckoutStatusDto> = {}
  for (const item of list) {
    if (item?.orderId) byOrderId[item.orderId] = item
  }
  return byOrderId
}

export async function getPaymentStatuses(orderIds: string[]): Promise<Record<string, PaymentStatusDto>> {
  if (orderIds.length === 0) return {}
  if (!PAYMENT_BASE_URL) {
    throw new Error('VITE_PAYMENT_BASE_URL is not set')
  }

  const params = new URLSearchParams({ orderIds: orderIds.join(',') })
  const list = await get<PaymentStatusDto[]>(`${PAYMENT_BASE_URL}/payments/status?${params.toString()}`)

  const byOrderId: Record<string, PaymentStatusDto> = {}
  for (const item of list) {
    if (item?.orderId) byOrderId[item.orderId] = item
  }
  return byOrderId
}
