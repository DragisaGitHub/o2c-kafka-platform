<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

import { normalizeApiError } from '../api/errors'
import { getOrderDetails } from '../api/detailsApi'
import { retryPayment } from '../api/paymentApi'
import { getCheckoutTimeline, getPaymentTimeline } from '../api/timelineApi'
import { computeAggregatedStatus, mergeTimelines } from '../domain/timeline'

const props = defineProps<{ orderId: string }>()

const loading = ref(false)
const error = ref<string | null>(null)

const order = ref<Awaited<ReturnType<typeof getOrderDetails>> | null>(null)
const checkoutTimeline = ref<Awaited<ReturnType<typeof getCheckoutTimeline>>>([])
const paymentTimeline = ref<Awaited<ReturnType<typeof getPaymentTimeline>>>([])

const retryingPayment = ref(false)

let pollHandle: number | undefined

function formatDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

const aggregatedStatus = computed(() =>
  computeAggregatedStatus({
    orderStatus: order.value?.status,
    checkoutTimeline: checkoutTimeline.value,
    paymentTimeline: paymentTimeline.value,
  })
)

const latestPaymentEvent = computed(() => {
  const events = paymentTimeline.value
  if (!events.length) return null
  return [...events].sort((a, b) => Date.parse(b.at) - Date.parse(a.at))[0]
})

const paymentStatus = computed(() => latestPaymentEvent.value?.status)
const paymentFailureReason = computed(() => latestPaymentEvent.value?.failureReason ?? null)

const timelineItems = computed(() =>
  mergeTimelines({
    orderCreatedAt: order.value?.createdAt,
    checkout: checkoutTimeline.value,
    payment: paymentTimeline.value,
  })
)

async function loadOnce(): Promise<void> {
  loading.value = true
  error.value = null

  const [orderRes, checkoutRes, paymentRes] = await Promise.allSettled([
    getOrderDetails(props.orderId),
    getCheckoutTimeline(props.orderId),
    getPaymentTimeline(props.orderId),
  ])

  if (orderRes.status === 'fulfilled') {
    order.value = orderRes.value
  } else {
    const e = await normalizeApiError(orderRes.reason)
    error.value = `${e.message}${e.correlationId ? ` (correlationId: ${e.correlationId})` : ''}`
  }

  if (checkoutRes.status === 'fulfilled') {
    checkoutTimeline.value = checkoutRes.value
  }

  if (paymentRes.status === 'fulfilled') {
    paymentTimeline.value = paymentRes.value
  }

  loading.value = false
}

function startPolling(ms: number) {
  stopPolling()
  pollHandle = window.setInterval(() => {
    void loadOnce()
  }, ms)
}

function stopPolling() {
  if (pollHandle !== undefined) {
    window.clearInterval(pollHandle)
    pollHandle = undefined
  }
}

onMounted(async () => {
  await loadOnce()
  startPolling(4000)
})

onUnmounted(() => {
  stopPolling()
})

async function onRetryPayment(): Promise<void> {
  if (retryingPayment.value) return
  retryingPayment.value = true
  try {
    await retryPayment(props.orderId)
  } catch (e) {
    const err = await normalizeApiError(e)
    error.value = `${err.message}${err.correlationId ? ` (correlationId: ${err.correlationId})` : ''}`
  } finally {
    retryingPayment.value = false
  }
}
</script>

<template>
  <section>
    <h2>Order Details</h2>

    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading">Loading…</p>

    <div v-if="order">
      <p>
        <strong>Order ID:</strong>
        <span>{{ order.orderId }}</span>
      </p>
      <p>
        <strong>Correlation ID:</strong>
        <span>{{ order.correlationId }}</span>
      </p>
      <p>
        <strong>Status:</strong>
        <span>{{ aggregatedStatus }}</span>
      </p>
      <p>
        <strong>Customer ID:</strong>
        <span>{{ order.customerId }}</span>
      </p>
      <p>
        <strong>Total:</strong>
        <span>{{ order.totalAmount }} {{ order.currency }}</span>
      </p>
      <p>
        <strong>Created:</strong>
        <span>{{ formatDate(order.createdAt) }}</span>
      </p>

      <h3>Timeline</h3>

      <p v-if="timelineItems.length === 0">No timeline events yet.</p>

      <ol v-else>
        <li v-for="(item, idx) in timelineItems" :key="`${item.source}:${item.type}:${item.at}:${idx}`">
          <div>
            <strong>{{ formatDate(item.at) }}</strong>
          </div>
          <div>
            <span>{{ item.label }}</span>
            <span> — </span>
            <span>{{ item.status }}</span>
          </div>
          <div v-if="item.failureReason">
            <small>Reason: {{ item.failureReason }}</small>
          </div>
        </li>
      </ol>

      <h3>Actions</h3>
      <div v-if="paymentStatus === 'FAILED'">
        <p v-if="paymentFailureReason">
          <strong>Payment failure reason:</strong>
          <span>{{ paymentFailureReason }}</span>
        </p>
        <p v-else>
          <strong>Payment failure reason:</strong>
          <span>Unknown</span>
        </p>

        <button type="button" :disabled="retryingPayment" @click="onRetryPayment">
          {{ retryingPayment ? 'Retrying…' : 'Retry payment' }}
        </button>
      </div>
    </div>
  </section>
</template>
