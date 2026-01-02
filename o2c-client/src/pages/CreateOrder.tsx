import React, {type FormEvent, useState} from 'react';
import { useNavigate } from 'react-router-dom';
import { orderService } from '../api/orderService';
import type { CreateOrderRequest, ApiError } from '../types';
import { ErrorBanner } from '../components/ErrorBanner';
import { StatusBadge } from '../components/StatusBadge';
import { CopyToClipboard } from '../components/CopyToClipboard';
import { LoadingSpinner } from '../components/LoadingSpinner';

export function CreateOrder() {
  const navigate = useNavigate();
  const [formData, setFormData] = useState<CreateOrderRequest>({
    customerId: '',
    totalAmount: 0,
    currency: 'USD',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [result, setResult] = useState<{
    orderId: string;
    status: string;
    correlationId: string;
  } | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setResult(null);
    setLoading(true);

    try {
      const response = await orderService.createOrder(formData);
      setResult({
        orderId: response.data.orderId,
        status: response.data.status,
        correlationId: response.correlationId || '',
      });
    } catch (err) {
      setError(err as ApiError);
    } finally {
      setLoading(false);
    }
  };

  const handleInputChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === 'totalAmount' ? parseFloat(value) || 0 : value,
    }));
  };

  const handleReset = () => {
    setFormData({
      customerId: '',
      totalAmount: 0,
      currency: 'USD',
    });
    setResult(null);
    setError(null);
  };

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-6">
        <h2 className="text-gray-900">Create New Order</h2>
        <p className="mt-2 text-gray-600">
          Submit a new order to the Order-to-Cash system
        </p>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {result ? (
        <div className="bg-white shadow-sm rounded-lg p-6 border border-gray-200">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-gray-900">Order Created Successfully</h3>
            <StatusBadge status={result.status} />
          </div>

          <dl className="grid grid-cols-1 gap-4">
            <div className="border-b border-gray-200 pb-4">
              <dt className="text-sm text-gray-500 mb-1">Order ID</dt>
              <dd className="flex items-center gap-2">
                <span className="text-gray-900">{result.orderId}</span>
                <CopyToClipboard text={result.orderId} />
              </dd>
            </div>

            <div className="border-b border-gray-200 pb-4">
              <dt className="text-sm text-gray-500 mb-1">Correlation ID</dt>
              <dd className="flex items-center gap-2">
                <span className="text-gray-900 break-all">
                  {result.correlationId}
                </span>
                <CopyToClipboard text={result.correlationId} />
              </dd>
            </div>

            <div>
              <dt className="text-sm text-gray-500 mb-1">Status</dt>
              <dd>
                <StatusBadge status={result.status} />
              </dd>
            </div>
          </dl>

          <div className="mt-6 flex gap-3">
            <button
              onClick={() => navigate(`/orders/${result.orderId}`)}
              className="flex-1 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
            >
              View Order Details
            </button>
            <button
              onClick={handleReset}
              className="flex-1 bg-gray-100 hover:bg-gray-200 text-gray-700 px-4 py-2 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-gray-500 focus:ring-offset-2"
            >
              Create Another Order
            </button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="bg-white shadow-sm rounded-lg p-6 border border-gray-200">
          <div className="space-y-6">
            <div>
              <label
                htmlFor="customerId"
                className="block text-sm text-gray-700 mb-2"
              >
                Customer ID (UUID)
              </label>
              <input
                type="text"
                id="customerId"
                name="customerId"
                value={formData.customerId}
                onChange={handleInputChange}
                required
                placeholder="e.g., 550e8400-e29b-41d4-a716-446655440000"
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>

            <div>
              <label
                htmlFor="totalAmount"
                className="block text-sm text-gray-700 mb-2"
              >
                Total Amount
              </label>
              <input
                type="number"
                id="totalAmount"
                name="totalAmount"
                value={formData.totalAmount}
                onChange={handleInputChange}
                required
                min="0.01"
                step="0.01"
                placeholder="0.00"
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>

            <div>
              <label
                htmlFor="currency"
                className="block text-sm text-gray-700 mb-2"
              >
                Currency
              </label>
              <select
                id="currency"
                name="currency"
                value={formData.currency}
                onChange={handleInputChange}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option value="USD">USD</option>
                <option value="EUR">EUR</option>
                <option value="GBP">GBP</option>
                <option value="JPY">JPY</option>
              </select>
            </div>
          </div>

          <div className="mt-6 flex gap-3">
            <button
              type="submit"
              disabled={loading}
              className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white px-4 py-2 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 flex items-center justify-center"
            >
              {loading ? (
                <>
                  <LoadingSpinner size="sm" className="mr-2" />
                  Creating Order...
                </>
              ) : (
                'Create Order'
              )}
            </button>
            <button
              type="button"
              onClick={handleReset}
              disabled={loading}
              className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-gray-500 focus:ring-offset-2"
            >
              Reset
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
