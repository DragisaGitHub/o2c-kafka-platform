import { computed, reactive, ref } from 'vue'

import type { OrderSummaryDto, ListOrdersParams } from '../api/orderApi'
import { listOrders } from '../api/orderApi'
import { getCheckoutStatuses, getPaymentStatuses } from '../api/statusApi'
import { aggregateOrderStatus, type AggregatedOrderStatus, type CheckoutStatus, type PaymentStatus } from '../domain/status'

export type OrdersListRow = {
  orderId: string
  customerId: string
  createdAt: string
  aggregatedStatus: AggregatedOrderStatus
}

function asOrderStatus(status?: string): 'CREATED' | 'COMPLETED' | 'FAILED' | undefined {
  if (!status) return undefined
  if (status === 'CREATED' || status === 'COMPLETED' || status === 'FAILED') return status
  return undefined
}

function asCheckoutStatus(status?: string): CheckoutStatus | undefined {
  if (!status) return undefined
  if (status === 'PENDING' || status === 'COMPLETED' || status === 'FAILED') return status
  return undefined
}

function asPaymentStatus(status?: string): PaymentStatus | undefined {
  if (!status) return undefined
  if (status === 'PENDING' || status === 'SUCCEEDED' || status === 'COMPLETED' || status === 'FAILED') return status
  return undefined
}

export function useOrdersListState() {
  const loading = ref(false)
  const error = ref<string | null>(null)

  const orders = ref<OrderSummaryDto[]>([])
  const checkoutByOrderId = reactive<Record<string, string>>({})
  const paymentByOrderId = reactive<Record<string, { status: string; failureReason?: string | null }>>({})

  let pollHandle: number | null = null

  async function load(params: ListOrdersParams = {}) {
    loading.value = true
    error.value = null

    try {
      orders.value = await listOrders(params)
    } catch (e) {
      console.error('Failed to load orders', e)
      error.value = 'Failed to load orders.'
      orders.value = []
    } finally {
      loading.value = false
    }

    await refreshStatusesOnce()
  }

  async function refreshStatusesOnce() {
    const orderIds = orders.value.map(o => o.orderId).filter(Boolean)
    if (orderIds.length === 0) return

    try {
      const [checkouts, payments] = await Promise.all([
        getCheckoutStatuses(orderIds),
        getPaymentStatuses(orderIds),
      ])

      // Reset + reassign to keep the reactive object in sync.
      for (const k of Object.keys(checkoutByOrderId)) delete checkoutByOrderId[k]
      for (const k of Object.keys(paymentByOrderId)) delete paymentByOrderId[k]

      for (const [orderId, dto] of Object.entries(checkouts)) {
        checkoutByOrderId[orderId] = dto.status
      }
      for (const [orderId, dto] of Object.entries(payments)) {
        paymentByOrderId[orderId] = { status: dto.status, failureReason: dto.failureReason ?? null }
      }
    } catch (e) {
      console.error('Failed to refresh statuses', e)
      // Minimal handling per requirements: keep showing existing data.
    }
  }

  function startPolling(intervalMs = 4000) {
    stopPolling()
    pollHandle = window.setInterval(() => {
      refreshStatusesOnce()
    }, intervalMs)
  }

  function stopPolling() {
    if (pollHandle !== null) {
      window.clearInterval(pollHandle)
      pollHandle = null
    }
  }

  const rows = computed<OrdersListRow[]>(() => {
    return orders.value.map(o => {
      const checkoutStatus = asCheckoutStatus(checkoutByOrderId[o.orderId])
      const paymentStatus = asPaymentStatus(paymentByOrderId[o.orderId]?.status)

      const aggregatedStatus = aggregateOrderStatus({
        orderStatus: asOrderStatus(o.status),
        checkoutStatus,
        paymentStatus,
      })

      return {
        orderId: o.orderId,
        customerId: o.customerId,
        createdAt: o.createdAt,
        aggregatedStatus,
      }
    })
  })

  return {
    loading,
    error,
    orders,
    rows,
    load,
    refreshStatusesOnce,
    startPolling,
    stopPolling,
  }
}
