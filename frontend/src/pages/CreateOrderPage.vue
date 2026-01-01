<template>
  <section>
    <h2>Create Order</h2>

    <form @submit.prevent="onSubmit" novalidate>
      <FormField id="customerId" v-model="customerId" label="Customer ID" :has-error="!!customerIdError">
        <template #error>{{ customerIdError }}</template>
      </FormField>

      <FormField
        id="totalAmount"
        v-model="totalAmount"
        label="Total Amount"
        type="number"
        input-mode="decimal"
        :has-error="!!totalAmountError"
      >
        <template #error>{{ totalAmountError }}</template>
      </FormField>

      <FormField id="currency" v-model="currency" label="Currency" :has-error="!!currencyError">
        <template #error>{{ currencyError }}</template>
      </FormField>

      <button type="submit" :disabled="submitting">
        {{ submitting ? 'Submitting…' : 'Create Order' }}
      </button>
    </form>

    <p v-if="submitError" role="alert">{{ submitError }}</p>

    <div v-if="result">
      <h3>Created</h3>
      <dl>
        <dt>Order ID</dt>
        <dd>{{ result.orderId }}</dd>

        <dt>Status</dt>
        <dd>{{ result.status }}</dd>

        <dt>Correlation ID</dt>
        <dd>{{ result.correlationId }}</dd>
      </dl>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import FormField from '../components/FormField.vue'
import { createOrder, type CreateOrderResponse } from '../api/orderApi'

const customerId = ref('')
const totalAmount = ref('')
const currency = ref('USD')

const submitting = ref(false)
const submitError = ref<string | null>(null)
const result = ref<CreateOrderResponse | null>(null)

const customerIdError = computed(() => {
  if (customerId.value.trim()) return ''
  return 'Customer ID is required.'
})

const totalAmountError = computed(() => {
  const v = totalAmount.value.trim()
  if (!v) return 'Total amount is required.'
  const n = Number(v)
  if (!Number.isFinite(n) || n <= 0) return 'Total amount must be a positive number.'
  return ''
})

const currencyError = computed(() => {
  const v = currency.value.trim()
  if (!v) return 'Currency is required.'
  if (v.length !== 3) return 'Currency must be a 3-letter code (e.g., USD).'
  return ''
})

function isValid(): boolean {
  return !customerIdError.value && !totalAmountError.value && !currencyError.value
}

async function onSubmit() {
  submitError.value = null
  result.value = null

  if (!isValid()) {
    submitError.value = 'Please fix the highlighted fields.'
    return
  }

  submitting.value = true
  try {
    const response = await createOrder({
      customerId: customerId.value.trim(),
      totalAmount: Number(totalAmount.value.trim()),
      currency: currency.value.trim().toUpperCase(),
    })

    result.value = response
  } catch (e) {
    // Minimal error handling per task: log + show simple message.
    console.error('Create order failed', e)
    submitError.value = 'Create order failed. Check console for details.'
  } finally {
    submitting.value = false
  }
}
</script>
