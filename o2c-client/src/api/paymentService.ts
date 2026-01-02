import { HttpClient } from './httpClient';
import type {
  PaymentStatus,
  PaymentTimelineEvent,
  RetryPaymentRequest,
} from '../types';

const PAYMENT_BASE_URL =
  import.meta.env.VITE_PAYMENT_BASE_URL || '/api/payment';

const httpClient = new HttpClient(PAYMENT_BASE_URL);

export const paymentService = {
  async getPaymentStatuses(
    orderIds: string[],
    correlationId?: string
  ): Promise<{ data: PaymentStatus[]; correlationId?: string }> {
    const params = new URLSearchParams({ orderIds: orderIds.join(',') });

    return httpClient.get<PaymentStatus[]>(
      `/payments/status?${params.toString()}`,
      correlationId
    );
  },

  async getPaymentTimeline(
    orderId: string,
    correlationId?: string
  ): Promise<{ data: PaymentTimelineEvent[]; correlationId?: string }> {
    return httpClient.get<PaymentTimelineEvent[]>(
      `/payments/${orderId}/timeline`,
      correlationId
    );
  },

  async retryPayment(
    orderId: string,
    request?: RetryPaymentRequest,
    correlationId?: string
  ): Promise<{ data: unknown; correlationId?: string }> {
    return httpClient.post(
      `/payments/${orderId}/retry`,
      request,
      correlationId
    );
  },
};
