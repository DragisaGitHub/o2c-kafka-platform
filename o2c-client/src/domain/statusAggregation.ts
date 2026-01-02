import type {
  Order,
  CheckoutStatusType,
  PaymentStatusType,
  AggregatedStatus,
  OrderWithStatuses,
} from '../types';

type NormalizedOrderStatus = 'CREATED' | 'CONFIRMED' | 'FAILED' | 'UNKNOWN';
type NormalizedCheckoutStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'UNKNOWN';
type NormalizedPaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'UNKNOWN';

export function normalizeOrderStatus(status: unknown): NormalizedOrderStatus {
  if (typeof status !== 'string') return 'UNKNOWN';
  switch (status) {
    case 'CREATED':
    case 'CONFIRMED':
    case 'FAILED':
      return status;
    default:
      return 'UNKNOWN';
  }
}

export function normalizeCheckoutStatus(
  status: unknown
): NormalizedCheckoutStatus | undefined {
  if (status === undefined || status === null) return undefined;
  if (typeof status !== 'string') return 'UNKNOWN';
  switch (status) {
    case 'PENDING':
    case 'COMPLETED':
    case 'FAILED':
      return status;
    default:
      return 'UNKNOWN';
  }
}

/**
 * Normalize payment status: SUCCEEDED → COMPLETED
 */
export function normalizePaymentStatus(
  status: unknown
): NormalizedPaymentStatus {
  if (typeof status !== 'string') return 'UNKNOWN';
  if (status === 'SUCCEEDED') return 'COMPLETED';
  switch (status) {
    case 'PENDING':
    case 'COMPLETED':
    case 'FAILED':
      return status;
    default:
      return 'UNKNOWN';
  }
}

/**
 * Calculate aggregated order status based on order, checkout, and payment statuses
 * 
 * Rules:
 * 1. FAILED if ANY of: order FAILED, checkout FAILED, payment FAILED
 * 2. COMPLETED if payment is terminal success (SUCCEEDED or COMPLETED)
 * 3. PAYMENT_PENDING if checkout COMPLETED but payment is missing or PENDING
 * 4. CHECKOUT_PENDING if order exists (CREATED/CONFIRMED) but checkout is missing or PENDING
 * 5. PROCESSING otherwise (unknown/unmapped states)
 */
export function calculateAggregatedStatus(
  orderStatus: unknown,
  checkoutStatus?: CheckoutStatusType,
  paymentStatus?: PaymentStatusType
): AggregatedStatus {
  const normalizedOrderStatus = normalizeOrderStatus(orderStatus);
  const normalizedCheckoutStatus = normalizeCheckoutStatus(checkoutStatus);
  const normalizedPaymentStatus =
    paymentStatus !== undefined ? normalizePaymentStatus(paymentStatus) : undefined;

  // Rule 1: FAILED if any service failed
  if (
    normalizedOrderStatus === 'FAILED' ||
    normalizedCheckoutStatus === 'FAILED' ||
    normalizedPaymentStatus === 'FAILED'
  ) {
    return 'FAILED';
  }

  // Rule 2: COMPLETED if payment is terminal success
  if (normalizedPaymentStatus === 'COMPLETED') {
    return 'COMPLETED';
  }

  // Rule 3: PAYMENT_PENDING if checkout completed but payment not terminal yet
  if (
    normalizedCheckoutStatus === 'COMPLETED' &&
    (normalizedPaymentStatus === undefined || normalizedPaymentStatus === 'PENDING')
  ) {
    return 'PAYMENT_PENDING';
  }

  // Rule 4: CHECKOUT_PENDING if order exists but checkout missing/pending
  if (
    (normalizedOrderStatus === 'CREATED' || normalizedOrderStatus === 'CONFIRMED') &&
    (normalizedCheckoutStatus === undefined || normalizedCheckoutStatus === 'PENDING')
  ) {
    return 'CHECKOUT_PENDING';
  }

  // Rule 5: PROCESSING otherwise (unknown/unmapped)
  return 'PROCESSING';
}

/**
 * Dev helper: quick sanity checks for status aggregation.
 * Exposed in dev via `window.__o2cStatusSelfCheck()`.
 */
export function runStatusAggregationSelfCheck(): void {
  const cases: Array<{
    orderStatus: unknown;
    checkoutStatus?: CheckoutStatusType;
    paymentStatus?: PaymentStatusType;
    expected: AggregatedStatus;
  }> = [
    {
      orderStatus: 'CONFIRMED',
      checkoutStatus: 'COMPLETED',
      paymentStatus: 'SUCCEEDED',
      expected: 'COMPLETED',
    },
    {
      orderStatus: 'CONFIRMED',
      checkoutStatus: 'COMPLETED',
      paymentStatus: 'PENDING',
      expected: 'PAYMENT_PENDING',
    },
    {
      orderStatus: 'CONFIRMED',
      checkoutStatus: 'PENDING',
      paymentStatus: undefined,
      expected: 'CHECKOUT_PENDING',
    },
    {
      orderStatus: 'CONFIRMED',
      checkoutStatus: 'FAILED',
      paymentStatus: 'SUCCEEDED',
      expected: 'FAILED',
    },
    {
      orderStatus: 'FAILED',
      checkoutStatus: 'COMPLETED',
      paymentStatus: 'SUCCEEDED',
      expected: 'FAILED',
    },
    {
      orderStatus: 'CONFIRMED',
      checkoutStatus: 'COMPLETED',
      paymentStatus: 'FAILED',
      expected: 'FAILED',
    },
  ];

  const failures: string[] = [];
  for (const c of cases) {
    const actual = calculateAggregatedStatus(
      c.orderStatus,
      c.checkoutStatus,
      c.paymentStatus
    );
    if (actual !== c.expected) {
      failures.push(
        `expected ${c.expected} but got ${actual} for order=${String(
          c.orderStatus
        )}, checkout=${String(c.checkoutStatus)}, payment=${String(c.paymentStatus)}`
      );
    }
  }

  if (failures.length) {
    // eslint-disable-next-line no-console
    console.error('[o2c] status aggregation self-check failed', failures);
    throw new Error(failures.join('; '));
  }
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
