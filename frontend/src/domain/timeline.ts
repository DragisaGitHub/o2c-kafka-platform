import { aggregateOrderStatus, type AggregatedOrderStatus } from './status'
import type { CheckoutTimelineEventDto, PaymentTimelineEventDto } from '../api/timelineApi'

export type TimelineItem = {
  source: 'order' | 'checkout' | 'payment'
  type: string
  label: string
  status: string
  at: string
  failureReason?: string | null
}

function labelFor(source: TimelineItem['source'], type: string): string {
  if (source === 'checkout') {
    if (type === 'CHECKOUT_CREATED') return 'Checkout created'
    if (type === 'CHECKOUT_COMPLETED') return 'Checkout completed'
    if (type === 'CHECKOUT_FAILED') return 'Checkout failed'
  }

  if (source === 'payment') {
    if (type === 'PAYMENT_CREATED') return 'Payment created'
    if (type === 'PAYMENT_SUCCEEDED') return 'Payment succeeded'
    if (type === 'PAYMENT_FAILED') return 'Payment failed'
    if (type.startsWith('PAYMENT_ATTEMPT_')) return `Payment attempt ${type.slice('PAYMENT_ATTEMPT_'.length)}`
  }

  if (source === 'order') {
    if (type === 'ORDER_CREATED') return 'Order created'
  }

  return type
}

function safeTimeValue(iso: string): number {
  const t = Date.parse(iso)
  return Number.isNaN(t) ? 0 : t
}

export function mergeTimelines(input: {
  orderCreatedAt?: string
  checkout?: CheckoutTimelineEventDto[]
  payment?: PaymentTimelineEventDto[]
}): TimelineItem[] {
  const items: TimelineItem[] = []

  if (input.orderCreatedAt) {
    items.push({
      source: 'order',
      type: 'ORDER_CREATED',
      status: 'CREATED',
      at: input.orderCreatedAt,
      label: labelFor('order', 'ORDER_CREATED'),
    })
  }

  for (const ev of input.checkout ?? []) {
    items.push({
      source: 'checkout',
      type: ev.type,
      status: ev.status,
      at: ev.at,
      label: labelFor('checkout', ev.type),
    })
  }

  for (const ev of input.payment ?? []) {
    items.push({
      source: 'payment',
      type: ev.type,
      status: ev.status,
      at: ev.at,
      failureReason: ev.failureReason ?? null,
      label: labelFor('payment', ev.type),
    })
  }

  items.sort((a, b) => safeTimeValue(a.at) - safeTimeValue(b.at))
  return items
}

function latestStatusFrom(events: Array<{ at: string; status: string }> | undefined): string | undefined {
  if (!events?.length) return undefined

  return [...events]
    .sort((a, b) => safeTimeValue(b.at) - safeTimeValue(a.at))
    .map((e) => e.status)
    .find((s) => Boolean(s))
}

export function computeAggregatedStatus(input: {
  orderStatus?: string
  checkoutTimeline?: CheckoutTimelineEventDto[]
  paymentTimeline?: PaymentTimelineEventDto[]
}): AggregatedOrderStatus {
  const checkoutStatus = latestStatusFrom(input.checkoutTimeline)
  const paymentStatus = latestStatusFrom(input.paymentTimeline)

  return aggregateOrderStatus({
    orderStatus: input.orderStatus as any,
    checkoutStatus: checkoutStatus as any,
    paymentStatus: paymentStatus as any,
  })
}
