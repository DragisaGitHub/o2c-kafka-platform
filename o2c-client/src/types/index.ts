// Order types
export interface Order {
  orderId: string;
  customerId: string;
  totalAmount: number;
  currency: string;
  status: OrderStatus;
  correlationId?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateOrderRequest {
  customerId: string;
  totalAmount: number;
  currency: string;
}

export interface CreateOrderResponse {
  orderId: string;
  status: OrderStatus;
  correlationId: string;
}

// Order-service statuses observed: CREATED, CONFIRMED, FAILED (anything else is treated as UNKNOWN in aggregation)
export type OrderStatus = 'CREATED' | 'CONFIRMED' | 'FAILED' | 'UNKNOWN';

// Checkout types
export interface CheckoutStatus {
  orderId: string;
  status: CheckoutStatusType;
  correlationId?: string;
}

export type CheckoutStatusType = 'PENDING' | 'COMPLETED' | 'FAILED';

export interface CheckoutTimelineEvent {
  type: string;
  status: CheckoutStatusType;
  at: string | null;
}

// Payment types
export interface PaymentStatus {
  orderId: string;
  status: PaymentStatusType;
  correlationId?: string;
  failureReason?: string;
}

export type PaymentStatusType = 'PENDING' | 'SUCCEEDED' | 'FAILED';

export interface PaymentTimelineEvent {
  type: string;
  status: PaymentStatusType;
  at: string | null;
  failureReason?: string | null;
}

export interface RetryPaymentRequest {
  orderId: string;
  retryRequestId: string;
}

// Aggregated types
export type AggregatedStatus = 
  | 'FAILED' 
  | 'COMPLETED' 
  | 'PAYMENT_PENDING'
  | 'CHECKOUT_PENDING' 
  | 'PROCESSING';

export interface OrderWithStatuses extends Order {
  checkoutStatus?: CheckoutStatusType;
  paymentStatus?: PaymentStatusType;
  aggregatedStatus: AggregatedStatus;
  paymentFailureReason?: string;
}

// Timeline types
export interface TimelineEvent {
  service: 'order' | 'checkout' | 'payment';
  status: string;
  timestamp: string;
  message?: string;
  failureReason?: string;
}

// API Error types
export interface ApiError {
  code?: string;
  message: string;
  correlationId?: string;
}

// Admin / Control Panel types
export interface AdminUserSummary {
  username: string;
  roles: string[];
  enabled: boolean;
  createdAt: string;
  updatedAt?: string;
}

export type AdminUserDetails = AdminUserSummary;

export interface CreateAdminUserRequest {
  username: string;
  password: string;
  roles: string[];
}

// Filter types
export interface OrderFilters {
  customerId?: string;
  fromDate?: string;
  toDate?: string;
}
