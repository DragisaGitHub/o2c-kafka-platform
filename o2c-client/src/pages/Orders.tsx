import { useState, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import { orderService } from '../api/orderService';
import { checkoutService } from '../api/checkoutService';
import { paymentService } from '../api/paymentService';
import type {
  OrderWithStatuses,
  ApiError,
  OrderFilters as OrderFiltersType,
  CheckoutStatus,
  PaymentStatus,
} from '../types';
import { enrichOrderWithStatuses } from '../domain/statusAggregation';
import { usePolling } from '../hooks/usePolling';
import { ErrorBanner } from '../components/ErrorBanner';
import { StatusBadge } from '../components/StatusBadge';
import { LoadingSpinner } from '../components/LoadingSpinner';

export function Orders() {
  const [orders, setOrders] = useState<OrderWithStatuses[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);
  const [filters, setFilters] = useState<OrderFiltersType>({});

  const checkoutStatusCooldownUntil = useRef(0);
  const paymentStatusCooldownUntil = useRef(0);

  const fetchOrders = useCallback(async () => {
    try {
      const ordersResponse = await orderService.getOrders(filters);
      const ordersList = ordersResponse.data;

      if (ordersList.length === 0) {
        setOrders([]);
        setLoading(false);
        return;
      }

      // Fetch checkout and payment statuses in parallel
      const orderIds = ordersList.map((o) => o.orderId);

      const now = Date.now();
      const shouldFetchCheckout = now >= checkoutStatusCooldownUntil.current;
      const shouldFetchPayment = now >= paymentStatusCooldownUntil.current;

      const [checkoutResponse, paymentResponse] = await Promise.all([
        shouldFetchCheckout
          ? checkoutService.getCheckoutStatuses(orderIds).catch(() => {
              checkoutStatusCooldownUntil.current = Date.now() + 30000;
              return { data: [] as CheckoutStatus[] };
            })
          : Promise.resolve({ data: [] as CheckoutStatus[] }),
        shouldFetchPayment
          ? paymentService.getPaymentStatuses(orderIds).catch(() => {
              paymentStatusCooldownUntil.current = Date.now() + 30000;
              return { data: [] as PaymentStatus[] };
            })
          : Promise.resolve({ data: [] as PaymentStatus[] }),
      ]);

      // Create maps for quick lookup
      const checkoutMap = new Map(
        checkoutResponse.data.map((c) => [c.orderId, c])
      );
      const paymentMap = new Map(
        paymentResponse.data.map((p) => [p.orderId, p])
      );

      // Enrich orders with statuses
      const enrichedOrders = ordersList.map((order) => {
        const checkout = checkoutMap.get(order.orderId);
        const payment = paymentMap.get(order.orderId);

        return enrichOrderWithStatuses(
          order,
          checkout?.status,
          payment?.status,
          payment?.failureReason
        );
      });

      setOrders(enrichedOrders);
      setError(null);
    } catch (err) {
      setError(err as ApiError);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  // Poll every 4 seconds
  usePolling(fetchOrders, { interval: 4000 });

  const handleFilterChange = (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const { name, value } = e.target;
    setFilters((prev) => ({
      ...prev,
      [name]: value || undefined,
    }));
  };

  const handleClearFilters = () => {
    setFilters({});
  };

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="mb-6">
        <h2 className="text-gray-900">Orders</h2>
        <p className="mt-2 text-gray-600">
          View and monitor all orders in the system. Data refreshes every 4 seconds.
        </p>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {/* Filters */}
      <div className="bg-white shadow-sm rounded-lg p-4 mb-6 border border-gray-200">
        <h3 className="text-gray-900 mb-4">Filters</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label
              htmlFor="customerId"
              className="block text-sm text-gray-700 mb-2"
            >
              Customer ID
            </label>
            <input
              type="text"
              id="customerId"
              name="customerId"
              value={filters.customerId || ''}
              onChange={handleFilterChange}
              placeholder="Filter by customer ID"
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>

          <div>
            <label
              htmlFor="fromDate"
              className="block text-sm text-gray-700 mb-2"
            >
              From Date
            </label>
            <input
              type="date"
              id="fromDate"
              name="fromDate"
              value={filters.fromDate || ''}
              onChange={handleFilterChange}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>

          <div>
            <label
              htmlFor="toDate"
              className="block text-sm text-gray-700 mb-2"
            >
              To Date
            </label>
            <input
              type="date"
              id="toDate"
              name="toDate"
              value={filters.toDate || ''}
              onChange={handleFilterChange}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </div>
        </div>

        {(filters.customerId || filters.fromDate || filters.toDate) && (
          <div className="mt-4">
            <button
              onClick={handleClearFilters}
              className="text-sm text-blue-600 hover:text-blue-700"
            >
              Clear Filters
            </button>
          </div>
        )}
      </div>

      {/* Orders Table */}
      {loading ? (
        <div className="bg-white shadow-sm rounded-lg p-12 border border-gray-200">
          <LoadingSpinner size="lg" />
        </div>
      ) : orders.length === 0 ? (
        <div className="bg-white shadow-sm rounded-lg p-12 text-center border border-gray-200">
          <svg
            className="mx-auto h-12 w-12 text-gray-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
            />
          </svg>
          <h3 className="mt-2 text-gray-900">No orders found</h3>
          <p className="mt-1 text-gray-500">
            Get started by creating a new order.
          </p>
          <div className="mt-6">
            <Link
              to="/create"
              className="inline-flex items-center px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-md transition-colors"
            >
              Create Order
            </Link>
          </div>
        </div>
      ) : (
        <div className="bg-white shadow-sm rounded-lg border border-gray-200 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs text-gray-500 uppercase tracking-wider">
                    Order ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs text-gray-500 uppercase tracking-wider">
                    Customer ID
                  </th>
                  <th className="px-6 py-3 text-left text-xs text-gray-500 uppercase tracking-wider">
                    Amount
                  </th>
                  <th className="px-6 py-3 text-left text-xs text-gray-500 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-6 py-3 text-left text-xs text-gray-500 uppercase tracking-wider">
                    Created
                  </th>
                  <th className="px-6 py-3 text-right text-xs text-gray-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {orders.map((order) => (
                  <tr key={order.orderId} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <Link
                        to={`/orders/${order.orderId}`}
                        className="text-blue-600 hover:text-blue-700 hover:underline"
                      >
                        {order.orderId.substring(0, 8)}...
                      </Link>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-gray-900">
                      {order.customerId.substring(0, 8)}...
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-gray-900">
                      {order.currency} {order.totalAmount.toFixed(2)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <StatusBadge status={order.aggregatedStatus} />
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-gray-500">
                      {new Date(order.createdAt).toLocaleString()}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right">
                      <Link
                        to={`/orders/${order.orderId}`}
                        className="text-blue-600 hover:text-blue-700"
                      >
                        View Details
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="mt-4 text-sm text-gray-500 text-right">
        Total: {orders.length} order{orders.length !== 1 ? 's' : ''}
      </div>
    </div>
  );
}
