import { useState, useCallback, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { orderService } from '../api/orderService';
import { checkoutService } from '../api/checkoutService';
import { paymentService } from '../api/paymentService';
import type {
  OrderWithStatuses,
  ApiError,
  TimelineEvent,
  CheckoutTimelineEvent,
  PaymentTimelineEvent,
} from '../types';
import { enrichOrderWithStatuses } from '../domain/statusAggregation';
import { mergeTimelines } from '../domain/timelineMerge';
import { usePolling } from '../hooks/usePolling';
import { ErrorBanner } from '../components/ErrorBanner';
import { StatusBadge } from '../components/StatusBadge';
import { CopyToClipboard } from '../components/CopyToClipboard';
import { Timeline } from '../components/Timeline';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { generateUUID } from '../api/httpClient';

export function OrderDetails() {
  const { orderId } = useParams<{ orderId: string }>();
  const [order, setOrder] = useState<OrderWithStatuses | null>(null);
  const [timeline, setTimeline] = useState<TimelineEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [retryError, setRetryError] = useState<ApiError | null>(null);

  const checkoutTimelineCooldownUntil = useRef(0);
  const paymentTimelineCooldownUntil = useRef(0);
  const checkoutStatusCooldownUntil = useRef(0);
  const paymentStatusCooldownUntil = useRef(0);

  const fetchOrderDetails = useCallback(async () => {
    if (!orderId) return;

    try {
      const now = Date.now();
      const shouldFetchCheckoutTimeline = now >= checkoutTimelineCooldownUntil.current;
      const shouldFetchPaymentTimeline = now >= paymentTimelineCooldownUntil.current;

      const [orderResponse, checkoutTimelineResponse, paymentTimelineResponse] =
        await Promise.all([
          orderService.getOrder(orderId),
          shouldFetchCheckoutTimeline
            ? checkoutService.getCheckoutTimeline(orderId).catch(() => {
                checkoutTimelineCooldownUntil.current = Date.now() + 30000;
                return { data: [] as CheckoutTimelineEvent[] };
              })
            : Promise.resolve({ data: [] as CheckoutTimelineEvent[] }),
          shouldFetchPaymentTimeline
            ? paymentService.getPaymentTimeline(orderId).catch(() => {
                paymentTimelineCooldownUntil.current = Date.now() + 30000;
                return { data: [] as PaymentTimelineEvent[] };
              })
            : Promise.resolve({ data: [] as PaymentTimelineEvent[] }),
        ]);

      const orderData = orderResponse.data;

      // Fetch current statuses for aggregation
      const now2 = Date.now();
      const shouldFetchCheckoutStatus = now2 >= checkoutStatusCooldownUntil.current;
      const shouldFetchPaymentStatus = now2 >= paymentStatusCooldownUntil.current;

      const [checkoutStatusResponse, paymentStatusResponse] =
        await Promise.all([
          shouldFetchCheckoutStatus
            ? checkoutService.getCheckoutStatuses([orderId]).catch(() => {
                checkoutStatusCooldownUntil.current = Date.now() + 30000;
                return { data: [] };
              })
            : Promise.resolve({ data: [] }),
          shouldFetchPaymentStatus
            ? paymentService.getPaymentStatuses([orderId]).catch(() => {
                paymentStatusCooldownUntil.current = Date.now() + 30000;
                return { data: [] };
              })
            : Promise.resolve({ data: [] }),
        ]);

      const checkoutStatus = checkoutStatusResponse.data.find(
        (c) => c.orderId === orderId
      );
      const paymentStatus = paymentStatusResponse.data.find(
        (p) => p.orderId === orderId
      );

      const enrichedOrder = enrichOrderWithStatuses(
        orderData,
        checkoutStatus?.status,
        paymentStatus?.status,
        paymentStatus?.failureReason
      );

      setOrder(enrichedOrder);

      // Merge timelines
      const mergedTimeline = mergeTimelines(
        orderData,
        checkoutTimelineResponse.data,
        paymentTimelineResponse.data
      );

      setTimeline(mergedTimeline);
      setError(null);
    } catch (err) {
      setError(err as ApiError);
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  // Poll every 4 seconds
  usePolling(fetchOrderDetails, { interval: 4000 });

  const handleRetryPayment = async () => {
    if (!orderId) return;

    setRetrying(true);
    setRetryError(null);

    try {
      await paymentService.retryPayment(orderId, {
        orderId,
        retryRequestId: generateUUID(),
      });

      // Refresh data immediately
      await fetchOrderDetails();
    } catch (err) {
      setRetryError(err as ApiError);
    } finally {
      setRetrying(false);
    }
  };

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-8">
        <div className="bg-white shadow-sm rounded-lg p-12 border border-gray-200">
          <LoadingSpinner size="lg" />
        </div>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-8">
        <ErrorBanner error={error} />
        <div className="bg-white shadow-sm rounded-lg p-12 text-center border border-gray-200">
          <h3 className="text-gray-900">Order not found</h3>
          <p className="mt-2 text-gray-600">
            The order you're looking for doesn't exist or couldn't be loaded.
          </p>
          <div className="mt-6">
            <Link
              to="/orders"
              className="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-md transition-colors"
            >
              Back to Orders
            </Link>
          </div>
        </div>
      </div>
    );
  }

  const showRetryButton =
    order.paymentStatus === 'FAILED' || order.aggregatedStatus === 'FAILED';

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center gap-2 mb-2">
          <Link
            to="/orders"
            className="text-blue-600 hover:text-blue-700 hover:underline"
          >
            ← Back to Orders
          </Link>
        </div>
        <h2 className="text-gray-900">Order Details</h2>
        <p className="mt-2 text-gray-600">
          Data refreshes every 4 seconds
        </p>
      </div>

      <ErrorBanner error={retryError} onDismiss={() => setRetryError(null)} />

      {/* Summary Card */}
      <div className="bg-white shadow-sm rounded-lg p-6 mb-6 border border-gray-200">
        <div className="flex items-center justify-between mb-6">
          <h3 className="text-gray-900">Summary</h3>
          <StatusBadge status={order.aggregatedStatus} />
        </div>

        <dl className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <dt className="text-sm text-gray-500 mb-1">Order ID</dt>
            <dd className="flex items-center gap-2">
              <span className="text-gray-900 break-all">{order.orderId}</span>
              <CopyToClipboard text={order.orderId} />
            </dd>
          </div>

          <div>
            <dt className="text-sm text-gray-500 mb-1">Correlation ID</dt>
            <dd className="flex items-center gap-2">
              <span className="text-gray-900 break-all">
                {order.correlationId || 'N/A'}
              </span>
              {order.correlationId && (
                <CopyToClipboard text={order.correlationId} />
              )}
            </dd>
          </div>

          <div>
            <dt className="text-sm text-gray-500 mb-1">Customer ID</dt>
            <dd className="flex items-center gap-2">
              <span className="text-gray-900 break-all">
                {order.customerId}
              </span>
              <CopyToClipboard text={order.customerId} />
            </dd>
          </div>

          <div>
            <dt className="text-sm text-gray-500 mb-1">Total Amount</dt>
            <dd className="text-gray-900">
              {order.currency} {order.totalAmount.toFixed(2)}
            </dd>
          </div>

          <div>
            <dt className="text-sm text-gray-500 mb-1">Created At</dt>
            <dd className="text-gray-900">
              {new Date(order.createdAt).toLocaleString()}
            </dd>
          </div>

          <div>
            <dt className="text-sm text-gray-500 mb-1">Order Status</dt>
            <dd>
              <StatusBadge status={order.status} />
            </dd>
          </div>

          {order.checkoutStatus && (
            <div>
              <dt className="text-sm text-gray-500 mb-1">Checkout Status</dt>
              <dd>
                <StatusBadge status={order.checkoutStatus} />
              </dd>
            </div>
          )}

          {order.paymentStatus && (
            <div>
              <dt className="text-sm text-gray-500 mb-1">Payment Status</dt>
              <dd>
                <StatusBadge status={order.paymentStatus} />
              </dd>
            </div>
          )}
        </dl>

        {/* Payment Failure Reason */}
        {order.paymentFailureReason && (
          <div className="mt-6 bg-red-50 border border-red-200 rounded p-4">
            <h4 className="text-sm text-red-800 mb-2">Payment Failure</h4>
            <p className="text-sm text-red-700">
              {order.paymentFailureReason}
            </p>
          </div>
        )}

        {/* Retry Payment Button */}
        {showRetryButton && (
          <div className="mt-6">
            <button
              onClick={handleRetryPayment}
              disabled={retrying}
              className="w-full bg-orange-600 hover:bg-orange-700 disabled:bg-orange-400 text-white px-4 py-2 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-orange-500 focus:ring-offset-2 flex items-center justify-center"
            >
              {retrying ? (
                <>
                  <LoadingSpinner size="sm" className="mr-2" />
                  Retrying Payment...
                </>
              ) : (
                'Retry Payment'
              )}
            </button>
          </div>
        )}
      </div>

      {/* Timeline Card */}
      <div className="bg-white shadow-sm rounded-lg p-6 border border-gray-200">
        <h3 className="text-gray-900 mb-6">Timeline</h3>
        <Timeline events={timeline} />
      </div>
    </div>
  );
}
