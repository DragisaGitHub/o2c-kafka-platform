import { get, post } from './http'

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

export type ListOrdersParams = {
  customerId?: string
  fromDate?: string // YYYY-MM-DD
  toDate?: string // YYYY-MM-DD
  limit?: number
  cursor?: string // ISO-8601 instant
}

export type OrderSummaryDto = {
  orderId: string
  customerId: string
  status: string
  totalAmount: number
  currency: string
  createdAt: string
}

export async function createOrder(request: CreateOrderRequest): Promise<CreateOrderResponse> {
  if (!ORDER_BASE_URL) {
    throw new Error('VITE_ORDER_BASE_URL is not set')
  }

  return post<CreateOrderResponse>(`${ORDER_BASE_URL}/orders`, request)
}

export async function listOrders(params: ListOrdersParams = {}): Promise<OrderSummaryDto[]> {
  if (!ORDER_BASE_URL) {
    throw new Error('VITE_ORDER_BASE_URL is not set')
  }

  const qs = new URLSearchParams()
  if (params.customerId?.trim()) qs.set('customerId', params.customerId.trim())
  if (params.fromDate?.trim()) qs.set('fromDate', params.fromDate.trim())
  if (params.toDate?.trim()) qs.set('toDate', params.toDate.trim())
  if (typeof params.limit === 'number') qs.set('limit', String(params.limit))
  if (params.cursor?.trim()) qs.set('cursor', params.cursor.trim())

  const suffix = qs.toString() ? `?${qs.toString()}` : ''
  return get<OrderSummaryDto[]>(`${ORDER_BASE_URL}/orders${suffix}`)
}
