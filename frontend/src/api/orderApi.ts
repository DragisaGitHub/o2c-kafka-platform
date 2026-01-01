import { post } from './http'

const ORDER_BASE_URL = (import.meta.env.VITE_ORDER_BASE_URL as string | undefined) ?? ''

export type CreateOrderRequest = {
  customerId: string
  totalAmount: number
  currency: string
}

export type CreateOrderResponse = {
  orderId: string
  status: string
  correlationId: string
}

export async function createOrder(request: CreateOrderRequest): Promise<CreateOrderResponse> {
  if (!ORDER_BASE_URL) {
    throw new Error('VITE_ORDER_BASE_URL is not set')
  }

  return post<CreateOrderResponse>(`${ORDER_BASE_URL}/orders`, request)
}
