<template>
  <section>
    <h2>Orders List</h2>

    <form @submit.prevent="onApplyFilters" novalidate>
      <fieldset :disabled="loading">
        <legend>Filters</legend>

        <FormField id="customerId" v-model="customerId" label="Customer ID (optional)" />

        <div>
          <label for="fromDate">From date (optional)</label>
          <input id="fromDate" v-model="fromDate" type="date" />
        </div>

        <div>
          <label for="toDate">To date (optional)</label>
          <input id="toDate" v-model="toDate" type="date" />
        </div>

        <button type="submit">Apply</button>
        <button type="button" @click="onRefresh">Refresh</button>
      </fieldset>
    </form>

    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="loading">Loading…</p>

    <p v-else-if="rows.length === 0">No orders found.</p>

    <table v-else>
      <thead>
        <tr>
          <th>Order ID</th>
          <th>Customer ID</th>
          <th>Created</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.orderId">
          <td>
            <RouterLink :to="`/orders/${row.orderId}`">{{ row.orderId }}</RouterLink>
          </td>
          <td>{{ row.customerId }}</td>
          <td>{{ formatDate(row.createdAt) }}</td>
          <td>{{ row.aggregatedStatus }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

import FormField from '../components/FormField.vue'
import { useOrdersListState } from '../state/ordersList'

const customerId = ref('')
const fromDate = ref('')
const toDate = ref('')

const { loading, error, rows, load, refreshStatusesOnce, startPolling, stopPolling } = useOrdersListState()

function formatDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

async function onApplyFilters() {
  await load({
    customerId: customerId.value.trim() || undefined,
    fromDate: fromDate.value.trim() || undefined,
    toDate: toDate.value.trim() || undefined,
    limit: 50,
  })
}

async function onRefresh() {
  await refreshStatusesOnce()
}

onMounted(async () => {
  await onApplyFilters()
  startPolling(4000)
})

onUnmounted(() => {
  stopPolling()
})
</script>
