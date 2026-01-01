export type OrderStatus = 'CREATED' | 'COMPLETED' | 'FAILED'

export type CheckoutStatus = 'PENDING' | 'COMPLETED' | 'FAILED'

/**
 * Backend payment statuses seen in this repo include `SUCCEEDED`, but the UI model
 * uses `COMPLETED` for terminal success.
 */
export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'COMPLETED' | 'FAILED'

export type AggregatedOrderStatus =
  | 'CHECKOUT_PENDING'
  | 'PAYMENT_PENDING'
  | 'COMPLETED'
  | 'FAILED'
  | 'PROCESSING'

export type AggregationInput = {
  orderStatus?: OrderStatus
  checkoutStatus?: CheckoutStatus
  paymentStatus?: PaymentStatus
}

export function normalizePaymentStatus(status?: PaymentStatus): Exclude<PaymentStatus, 'SUCCEEDED'> | undefined {
  if (!status) return undefined
  return status === 'SUCCEEDED' ? 'COMPLETED' : status
}

export function isTerminalAggregatedStatus(status: AggregatedOrderStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED'
}

/**
 * Deterministic aggregation for eventual-consistency UX.
 * Rules:
 * - If any order/checkout/payment is FAILED -> FAILED
 * - Else if payment is COMPLETED (or SUCCEEDED) -> COMPLETED
 * - Else if checkout is COMPLETED -> PAYMENT_PENDING
 * - Else if order is CREATED -> CHECKOUT_PENDING
 * - Else -> PROCESSING
 */
export function aggregateOrderStatus(input: AggregationInput): AggregatedOrderStatus {
  const payment = normalizePaymentStatus(input.paymentStatus)

  const orderFailed = input.orderStatus === 'FAILED'
  const checkoutFailed = input.checkoutStatus === 'FAILED'
  const paymentFailed = payment === 'FAILED'

  if (orderFailed || checkoutFailed || paymentFailed) return 'FAILED'
  if (payment === 'COMPLETED') return 'COMPLETED'
  if (input.checkoutStatus === 'COMPLETED') return 'PAYMENT_PENDING'
  if (input.orderStatus === 'CREATED') return 'CHECKOUT_PENDING'

  return 'PROCESSING'
}
