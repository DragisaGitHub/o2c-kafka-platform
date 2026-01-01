# Feature Specification: O2C Demo Application

**Feature Branch**: `001-o2c-demo-app`  
**Created**: 2026-01-01  
**Status**: Draft  
**Input**: Build an Order-to-Cash demo UI on top of existing order/checkout/payment services, making eventual consistency visible.

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Create Order (Priority: P1)

As a user, I can create a new order by entering a customer ID, total amount, and currency, and I immediately see the created order and its initial status.

**Why this priority**: Establishes the end-to-end demo entry point and creates data used by all other flows.

**Independent Test**: Can be tested by creating an order and verifying an order identifier is returned and the UI shows an initial status without waiting for downstream processing.

**Acceptance Scenarios**:

1. **Given** a valid `customerId`, `totalAmount`, and `currency`, **When** I submit the Create Order form, **Then** I receive a new `orderId` and see the order with status `CREATED` immediately.
2. **Given** an invalid input (missing `customerId`, non-positive `totalAmount`, or unsupported `currency`), **When** I submit the form, **Then** I see a user-friendly validation error and no order is created.

---

### User Story 2 - Order List & Search (Priority: P2)

As a user, I can view recent orders and filter by customer ID and date range, and each row shows the latest known statuses for order, checkout, and payment.

**Why this priority**: Provides the “control center” view and makes eventual consistency visible across many orders.

**Independent Test**: Can be tested by listing orders, applying filters, and observing that statuses update asynchronously over time without manual data entry.

**Acceptance Scenarios**:

1. **Given** recent orders exist, **When** I open the Orders list, **Then** I see a list of orders with aggregated statuses (order/checkout/payment) per row.
2. **Given** I enter a `customerId` filter and a date range, **When** I apply filters, **Then** the list shows only orders matching those criteria.
3. **Given** an order is being processed asynchronously, **When** I stay on the list page, **Then** statuses refresh and change over time without requiring a full page reload.

---

### User Story 3 - Order Details & Timeline (Priority: P3)

As a user, I can open an order detail view that shows the latest known statuses (order/checkout/payment) and a timeline of state changes with timestamps.

**Why this priority**: Provides traceability and demonstrates the event-driven workflow in a human-readable way.

**Independent Test**: Can be tested by opening an order and verifying the timeline grows as asynchronous processing occurs.

**Acceptance Scenarios**:

1. **Given** an order exists, **When** I open its details, **Then** I see latest statuses for order/checkout/payment and a timeline of state changes with timestamps.
2. **Given** a correlation ID exists for the flow, **When** I view order details, **Then** I can see the `correlationId` and use it to trace logs.
3. **Given** the system is eventually consistent, **When** downstream services complete later, **Then** the timeline and statuses update without the user needing to re-create the order.

---

### User Story 4 - Payment Failure + Retry (Priority: P4)

As a user, when payment fails, I can see the failure reason and trigger a retry from the order details page.

**Why this priority**: Demonstrates failure handling, recovery, and idempotent, asynchronous retry behavior.

**Independent Test**: Can be tested by forcing a payment validation failure, verifying UI shows `FAILED` with a reason, and retrying until payment succeeds or fails again.

**Acceptance Scenarios**:

1. **Given** payment fails due to a validation error (e.g., unsupported currency), **When** I view the order, **Then** I see payment status `FAILED` and a user-friendly reason.
2. **Given** payment is `FAILED`, **When** I click Retry Payment, **Then** the UI shows a pending/processing state and later updates to the new payment outcome.
3. **Given** I click Retry Payment multiple times, **When** the backend receives duplicate retry requests, **Then** the system does not create duplicate payment side effects.

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- Create Order when `currency` is not supported.
- Create Order when `totalAmount` is zero/negative or has too many decimal places.
- Orders list when filters are invalid (fromDate > toDate) or return no results.
- Order details for an unknown `orderId`.
- Timeline contains gaps (some downstream events not yet visible) due to eventual consistency.
- Retry Payment when the order is not in a retryable state.
- Backend returns transient failures (timeouts) vs permanent failures (validation).

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: The system MUST allow a user to create an order with `customerId` (UUID), `totalAmount` (decimal), and `currency` (string).
- **FR-002**: On successful order creation, the system MUST return an `orderId` and an initial order status immediately.
- **FR-003**: The system MUST provide an Orders list view that supports filtering by `customerId` and date range.
- **FR-004**: Each order row MUST show the latest known statuses for order, checkout, and payment.
- **FR-005**: The system MUST provide an Order details view showing:
  - latest known statuses for order/checkout/payment,
  - a timeline of state changes (with timestamps), and
  - a `correlationId` when available.
- **FR-006**: When payment fails, the system MUST expose a stable failure reason suitable for user display.
- **FR-007**: The system MUST allow a user to trigger a “Retry Payment” action when an order is in a retryable payment state.
- **FR-008**: The UI MUST make eventual consistency visible by representing pending/processing states and asynchronously refreshed statuses.
- **FR-009**: APIs MUST return consistent error responses with stable error codes and user-friendly messages.
- **FR-010**: The local development setup MUST work with the existing docker compose configuration and require minimal manual steps.
- **FR-011**: The UI MUST support keyboard navigation for primary actions (create order, search, open details, retry payment).

### API Contracts (REST) *(required deliverable)*

The frontend MUST communicate via REST only.

Two acceptable patterns are defined below. The default for this demo is **Option A**.

#### Option A (recommended): Simple BFF for aggregated views

Purpose: Provide a single REST API for the UI to avoid N+1 calls and to standardize the aggregated status model.

Required endpoints:

- `POST /api/o2c/orders`
  - Creates an order.
  - Returns `orderId` and initial aggregated status.

- `GET /api/o2c/orders?customerId=&fromDate=&toDate=&limit=&cursor=`
  - Returns recent orders with aggregated statuses.

- `GET /api/o2c/orders/{orderId}`
  - Returns the aggregated order view including timeline and correlation ID (if available).

- `POST /api/o2c/orders/{orderId}/payment/retry`
  - Requests a payment retry (asynchronous).

#### Option B (alternative): Frontend aggregates by calling services directly

If Option B is used, each service MUST expose endpoints that allow efficient aggregation without per-row calls.

Minimum required capabilities:

- Order service: create, list/search, get details.
- Checkout service: get checkout status by order ID (including batch lookup).
- Payment service: get payment status by order ID (including batch lookup) and trigger retry.

### Frontend DTOs *(required deliverable)*

All DTOs below are boundary contracts for the UI.

- `CreateOrderRequest`
  - `customerId: string`
  - `totalAmount: string`
  - `currency: string`

- `CreateOrderResponse`
  - `orderId: string`
  - `status: AggregatedOrderStatus`
  - `correlationId?: string`

- `OrderListResponse`
  - `items: OrderSummary[]`
  - `nextCursor?: string`

- `OrderSummary`
  - `orderId: string`
  - `customerId: string`
  - `createdAt: string`
  - `totalAmount: string`
  - `currency: string`
  - `orderStatus: OrderStatus`
  - `checkoutStatus: CheckoutStatus`
  - `paymentStatus: PaymentStatus`
  - `aggregatedStatus: AggregatedOrderStatus`

- `OrderDetails`
  - `order: OrderSummary`
  - `timeline: TimelineEvent[]`
  - `correlationId?: string`
  - `retryPaymentAllowed: boolean`
  - `paymentFailureReason?: FailureReason`

- `TimelineEvent`
  - `timestamp: string`
  - `source: "order" | "checkout" | "payment"`
  - `type: string`
  - `message: string`
  - `statusAfter?: string`

- `FailureReason`
  - `code: string`
  - `message: string`

- `ErrorResponse`
  - `code: string`
  - `message: string`
  - `correlationId?: string`

### Status Model & Aggregation *(required deliverable)*

The UI MUST represent each service’s status explicitly and also compute a single aggregated status.

- `OrderStatus` MUST include at minimum: `CREATED` and terminal outcomes (`COMPLETED` and/or `FAILED`).
- `CheckoutStatus` MUST include at minimum: `PENDING`, `COMPLETED`, `FAILED`.
- `PaymentStatus` MUST include at minimum: `PENDING`, `COMPLETED`, `FAILED`.

Aggregation rules (deterministic and consistent across UI screens):

- If any of checkout/payment is `FAILED`, then `aggregatedStatus` MUST be `FAILED`.
- Else if payment is `COMPLETED`, then `aggregatedStatus` MUST be `COMPLETED`.
- Else if checkout is `COMPLETED`, then `aggregatedStatus` MUST be `PAYMENT_PENDING`.
- Else if order is `CREATED`, then `aggregatedStatus` MUST be `CHECKOUT_PENDING`.
- Otherwise, `aggregatedStatus` MUST be `PROCESSING`.

The UI MUST visually distinguish:

- stable terminal states (completed/failed), and
- non-terminal states that may change (pending/processing).

### Minimal UI Pages & Components *(required deliverable)*

The demo UI MUST include the following pages:

- Create Order
  - form fields: customerId, totalAmount, currency
  - submit action
  - immediate display of created orderId + initial status

- Orders List
  - filter controls: customerId, fromDate, toDate
  - list/table of orders with aggregated statuses
  - row click navigates to details

- Order Details
  - summary (ids, amounts, latest statuses)
  - correlationId (when available)
  - timeline list
  - Retry Payment button when allowed

Common UI components (minimal):

- Status badge component (shared across pages)
- Error banner component (shared across pages)
- Loading/pending indicator for eventual consistency states

### Assumptions & Scope

- Authentication is out of scope for this demo; the UI is accessible locally without login.
- The backend already performs the Kafka-driven workflow; the UI’s responsibility is to show current
  state and make asynchronous updates visible.
- The demo targets local development; production hardening is out of scope.

### Constitution-Driven Constraints *(mandatory)*

<!--
  ACTION REQUIRED: Capture cross-cutting constraints required by the repository constitution.
  These are enforceable engineering rules, not “nice to haves”.
-->

- Boundary discipline: APIs/events MUST use DTOs; persistence entities MUST NOT leak.
- Service coupling: No cross-service DB access; integration is REST + Kafka only.
- Contracts: Kafka events/topics/status values MUST be explicit and versioned; breaking changes MUST
  include a migration plan.
- Testing: Unit tests are mandatory for business logic; integration tests are required for Kafka/DB
  interactions; test types must be distinguishable.
- Observability: Correlation IDs must propagate through REST and Kafka; logs must be structured and
  include stable error codes.
- UX: Status and errors must be represented consistently across backend and frontend; UI must model
  eventual consistency states.

### Key Entities *(include if feature involves data)*

- **Order**: Represents an order created by a customer, including amount, currency, and timestamps.
- **Checkout**: Represents checkout processing state for an order.
- **Payment**: Represents payment processing state for an order.
- **Aggregated Order View**: Combines latest statuses across order/checkout/payment for display.
- **Timeline Event**: Represents a state transition record shown to the user with timestamp and source.
- **Failure Reason**: Stable code and message describing why a payment failed.
- **Correlation ID**: Identifier used to trace a single end-to-end flow across REST and Kafka.

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: A user can create an order and see an `orderId` and initial status within 2 seconds.
- **SC-002**: Orders list renders within 2 seconds for a typical demo dataset (hundreds of orders).
- **SC-003**: Order details renders within 2 seconds and shows a timeline.
- **SC-004**: Status refresh visibly updates the UI within 10 seconds of backend state changes in local demo conditions.
- **SC-005**: 100% of API failures shown in the UI include a stable error code and a user-friendly message.
- **SC-006**: Primary actions are usable via keyboard only (create order, search, open details, retry payment).
