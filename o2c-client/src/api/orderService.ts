import { HttpClient } from './httpClient';
import type {
  Order,
  CreateOrderRequest,
  CreateOrderResponse,
  OrderFilters,
} from '../types';

const ORDER_BASE_URL =
  import.meta.env.VITE_ORDER_BASE_URL || '/api/order';

const httpClient = new HttpClient(ORDER_BASE_URL);

export const orderService = {
  async createOrder(
    request: CreateOrderRequest,
    correlationId?: string
  ): Promise<{ data: CreateOrderResponse; correlationId?: string }> {
    return httpClient.post<CreateOrderResponse>(
      '/orders',
      request,
      correlationId
    );
  },

  async getOrders(
    filters?: OrderFilters,
    correlationId?: string
  ): Promise<{ data: Order[]; correlationId?: string }> {
    const params = new URLSearchParams();

    if (filters?.customerId) {
      params.append('customerId', filters.customerId);
    }
    if (filters?.fromDate) {
      params.append('fromDate', filters.fromDate);
    }
    if (filters?.toDate) {
      params.append('toDate', filters.toDate);
    }

    const queryString = params.toString();
    const endpoint = queryString ? `/orders?${queryString}` : '/orders';

    return httpClient.get<Order[]>(endpoint, correlationId);
  },

  async getOrder(
    orderId: string,
    correlationId?: string
  ): Promise<{ data: Order; correlationId?: string }> {
    return httpClient.get<Order>(`/orders/${orderId}`, correlationId);
  },
};
