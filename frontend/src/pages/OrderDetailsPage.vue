<script setup lang="ts">
defineProps<{ orderId: string }>()
</script>

<template>
  <section>
    <h2>Order Details</h2>
    <p>This is a placeholder page.</p>
    <p>
      <strong>orderId:</strong>
      <span>{{ orderId }}</span>
    </p>
  </section>
</template>
