import type { AggregatedStatus, CheckoutStatusType, PaymentStatusType } from '../types';

type StatusType = AggregatedStatus | CheckoutStatusType | PaymentStatusType | string;

interface StatusBadgeProps {
  status: StatusType;
  className?: string;
}

const STATUS_CONFIG: Record<
  string,
  { label: string; colorClass: string }
> = {
  // Aggregated statuses
  COMPLETED: {
    label: 'Completed',
    colorClass: 'bg-green-100 text-green-800 border-green-200',
  },
  FAILED: {
    label: 'Failed',
    colorClass: 'bg-red-100 text-red-800 border-red-200',
  },
  PROCESSING: {
    label: 'Processing',
    colorClass: 'bg-blue-100 text-blue-800 border-blue-200',
  },
  CHECKOUT_PENDING: {
    label: 'Checkout Pending',
    colorClass: 'bg-purple-100 text-purple-800 border-purple-200',
  },
  // Order statuses
  CREATED: {
    label: 'Created',
    colorClass: 'bg-gray-100 text-gray-800 border-gray-200',
  },
  ACTIVE: {
    label: 'Active',
    colorClass: 'bg-blue-100 text-blue-800 border-blue-200',
  },
  // Checkout/Payment statuses
  PENDING: {
    label: 'Pending',
    colorClass: 'bg-yellow-100 text-yellow-800 border-yellow-200',
  },
  SUCCEEDED: {
    label: 'Succeeded',
    colorClass: 'bg-green-100 text-green-800 border-green-200',
  },
};

export function StatusBadge({ status, className = '' }: StatusBadgeProps) {
  const config = STATUS_CONFIG[status] || {
    label: status,
    colorClass: 'bg-gray-100 text-gray-800 border-gray-200',
  };

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full border ${config.colorClass} ${className}`}
    >
      {config.label}
    </span>
  );
}
