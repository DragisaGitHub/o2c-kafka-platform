import type {
  Order,
  CheckoutStatusType,
  PaymentStatusType,
  AggregatedStatus,
  OrderWithStatuses,
} from '../types';

/**
 * Normalize payment status: SUCCEEDED → COMPLETED
 */
export function normalizePaymentStatus(
  status: PaymentStatusType
): 'PENDING' | 'COMPLETED' | 'FAILED' {
  if (status === 'SUCCEEDED') {
    return 'COMPLETED';
  }
  return status;
}

/**
 * Calculate aggregated order status based on order, checkout, and payment statuses
 * 
 * Rules:
 * 1. FAILED if any service has FAILED status
 * 2. COMPLETED if order, checkout, and payment are all COMPLETED
 * 3. CHECKOUT_PENDING if order is active and checkout is PENDING
 * 4. PROCESSING otherwise
 */
export function calculateAggregatedStatus(
  orderStatus: string,
  checkoutStatus?: CheckoutStatusType,
  paymentStatus?: PaymentStatusType
): AggregatedStatus {
  const normalizedPaymentStatus = paymentStatus
    ? normalizePaymentStatus(paymentStatus)
    : undefined;

  // Rule 1: FAILED if any service failed
  if (
    orderStatus === 'FAILED' ||
    checkoutStatus === 'FAILED' ||
    normalizedPaymentStatus === 'FAILED'
  ) {
    return 'FAILED';
  }

  // Rule 2: COMPLETED if all services completed
  if (
    orderStatus === 'COMPLETED' &&
    checkoutStatus === 'COMPLETED' &&
    normalizedPaymentStatus === 'COMPLETED'
  ) {
    return 'COMPLETED';
  }

  // Rule 3: CHECKOUT_PENDING if order active and checkout pending
  if (
    (orderStatus === 'ACTIVE' || orderStatus === 'CREATED') &&
    checkoutStatus === 'PENDING'
  ) {
    return 'CHECKOUT_PENDING';
  }

  // Rule 4: PROCESSING otherwise
  return 'PROCESSING';
}

/**
 * Enrich an order with checkout and payment statuses and calculate aggregated status
 */
export function enrichOrderWithStatuses(
  order: Order,
  checkoutStatus?: CheckoutStatusType,
  paymentStatus?: PaymentStatusType,
  paymentFailureReason?: string
): OrderWithStatuses {
  return {
    ...order,
    checkoutStatus,
    paymentStatus,
    paymentFailureReason,
    aggregatedStatus: calculateAggregatedStatus(
      order.status,
      checkoutStatus,
      paymentStatus
    ),
  };
}
