import { HttpClient } from './httpClient';
import type { CheckoutStatus, CheckoutTimelineEvent } from '../types';

const CHECKOUT_BASE_URL =
  import.meta.env.VITE_CHECKOUT_BASE_URL || '/api/checkout';

const httpClient = new HttpClient(CHECKOUT_BASE_URL);

export const checkoutService = {
  async getCheckoutStatuses(
    orderIds: string[],
    correlationId?: string
  ): Promise<{ data: CheckoutStatus[]; correlationId?: string }> {
    const params = new URLSearchParams({ orderIds: orderIds.join(',') });

    return httpClient.get<CheckoutStatus[]>(
      `/checkouts/status?${params.toString()}`,
      correlationId
    );
  },

  async getCheckoutTimeline(
    orderId: string,
    correlationId?: string
  ): Promise<{ data: CheckoutTimelineEvent[]; correlationId?: string }> {
    return httpClient.get<CheckoutTimelineEvent[]>(
      `/checkouts/${orderId}/timeline`,
      correlationId
    );
  },
};
