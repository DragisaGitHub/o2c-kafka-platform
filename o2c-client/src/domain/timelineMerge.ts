import type {
  Order,
  CheckoutTimelineEvent,
  PaymentTimelineEvent,
  TimelineEvent,
} from '../types';

function safeDateMs(value: string | null | undefined): number | null {
  if (!value) return null;
  const ms = new Date(value).getTime();
  return Number.isFinite(ms) ? ms : null;
}

function messageFromType(type: string, fallback: string): string {
  if (!type) return fallback;
  return type
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

/**
 * Merge and sort timeline events from order, checkout, and payment services
 */
export function mergeTimelines(
  order: Order,
  checkoutEvents: CheckoutTimelineEvent[] = [],
  paymentEvents: PaymentTimelineEvent[] = []
): TimelineEvent[] {
  const timeline: TimelineEvent[] = [];

  // Add order creation event
  timeline.push({
    service: 'order',
    status: order.status,
    timestamp: order.createdAt,
    message: `Order ${order.status.toLowerCase()}`,
  });

  // Add order update event if different from creation
  if (order.updatedAt && order.updatedAt !== order.createdAt) {
    timeline.push({
      service: 'order',
      status: order.status,
      timestamp: order.updatedAt,
      message: `Order ${order.status.toLowerCase()}`,
    });
  }

  // Add checkout events
  checkoutEvents.forEach((event) => {
    timeline.push({
      service: 'checkout',
      status: event.status,
      timestamp: event.at ?? '',
      message: messageFromType(
        event.type,
        `Checkout ${event.status.toLowerCase()}`
      ),
    });
  });

  // Add payment events
  paymentEvents.forEach((event) => {
    timeline.push({
      service: 'payment',
      status: event.status === 'SUCCEEDED' ? 'COMPLETED' : event.status,
      timestamp: event.at ?? '',
      message: messageFromType(
        event.type,
        `Payment ${event.status.toLowerCase()}`
      ),
      failureReason: event.failureReason ?? undefined,
    });
  });

  // Sort by timestamp (newest first)
  timeline.sort((a, b) => {
    const aMs = safeDateMs(a.timestamp);
    const bMs = safeDateMs(b.timestamp);
    if (aMs === null && bMs === null) return 0;
    if (aMs === null) return 1;
    if (bMs === null) return -1;
    return bMs - aMs;
  });

  return timeline;
}
