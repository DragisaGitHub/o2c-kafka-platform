<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <header>
    <h1>O2C Demo</h1>
    <nav aria-label="Primary">
      <RouterLink to="/create">Create Order</RouterLink>
      <span> | </span>
      <RouterLink to="/orders">Orders</RouterLink>
    </nav>
  </header>

  <main>
    <RouterView />
  </main>
</template>
