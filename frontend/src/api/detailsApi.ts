import { get } from './http'
import { getCheckoutTimeline, getPaymentTimeline } from './timelineApi'

const ORDER_BASE_URL = (import.meta.env.VITE_ORDER_BASE_URL as string | undefined) ?? ''

export type OrderDetailsDto = {
  orderId: string
  customerId: string
  status: string
  totalAmount: number
  currency: string
  createdAt: string
  correlationId: string
}

export async function getOrderDetails(orderId: string): Promise<OrderDetailsDto> {
  if (!ORDER_BASE_URL) {
    throw new Error('VITE_ORDER_BASE_URL is not set')
  }

  return get<OrderDetailsDto>(`${ORDER_BASE_URL}/orders/${encodeURIComponent(orderId)}`)
}

export async function getOrderDetailsBundle(orderId: string): Promise<{
  order: OrderDetailsDto
  checkoutTimeline: Awaited<ReturnType<typeof getCheckoutTimeline>>
  paymentTimeline: Awaited<ReturnType<typeof getPaymentTimeline>>
}> {
  const [order, checkoutTimeline, paymentTimeline] = await Promise.all([
    getOrderDetails(orderId),
    getCheckoutTimeline(orderId),
    getPaymentTimeline(orderId),
  ])

  return { order, checkoutTimeline, paymentTimeline }
}
