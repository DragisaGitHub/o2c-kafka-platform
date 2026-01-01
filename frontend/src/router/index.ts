import { createRouter, createWebHistory } from 'vue-router'

import CreateOrderPage from '../pages/CreateOrderPage.vue'
import OrderDetailsPage from '../pages/OrderDetailsPage.vue'
import OrdersListPage from '../pages/OrdersListPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/create' },
    { path: '/create', component: CreateOrderPage },
    { path: '/orders', component: OrdersListPage },
    {
      path: '/orders/:orderId',
      component: OrderDetailsPage,
      props: true,
    },
  ],
})

export default router
