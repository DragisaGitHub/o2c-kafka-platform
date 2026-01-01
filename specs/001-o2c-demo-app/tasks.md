---

description: "Task list for implementing the O2C demo application"
---

# Tasks: O2C Demo Application

**Input**: Design documents from `/specs/001-o2c-demo-app/` (`plan.md`, `spec.md`)

**Tests**: Tests are NOT optional for this repository.
- Unit tests are mandatory for business logic.
- Integration tests are required for Kafka flows and database interactions.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[TaskID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[US#]**: Which user story the task belongs to (e.g., US1, US2)
- Setup + Foundational phases MUST NOT include a story label
- Every task MUST include at least one concrete file path

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Finalize the plan, establish local dev docs, and scaffold the Vue frontend.

- [ ] T001 Update specs/001-o2c-demo-app/plan.md to remove placeholders and document concrete ports/URLs (order 8082, checkout 8081, payment 8083) in specs/001-o2c-demo-app/plan.md
- [ ] T002 Record the selected integration approach (Option B: frontend calls services directly) and the contract mapping in specs/001-o2c-demo-app/plan.md
- [ ] T003 Create Vue 3 + TypeScript + Vite scaffold with routing in frontend/package.json (and frontend/vite.config.ts, frontend/src/main.ts)
- [ ] T004 [P] Add environment template for local base URLs in frontend/.env.example
- [ ] T005 [P] Add local run instructions (docker compose + services + frontend) in specs/001-o2c-demo-app/quickstart.md
- [ ] T006 [P] Add local CORS configuration for the frontend dev origin in order-service/src/main/java/rs/master/o2c/order/config/CorsConfig.java
- [ ] T007 [P] Add local CORS configuration for the frontend dev origin in checkout-service/src/main/java/rs/master/o2c/checkout/config/CorsConfig.java
- [ ] T008 [P] Add local CORS configuration for the frontend dev origin in payment-service/src/main/java/rs/master/o2c/payment/config/CorsConfig.java

**Checkpoint**: `docker/docker-compose.local.yml` + services + `frontend` can run locally and the browser can call the services without CORS errors.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Cross-cutting platform concerns required by the constitution and the demo UX.

- [ ] T009 Define frontend status types + aggregation + backend-to-UI mappings (including `SUCCEEDED`→`COMPLETED`) in frontend/src/domain/status.ts
- [ ] T010 [P] Implement a typed HTTP client with correlation ID propagation in frontend/src/api/http.ts
- [ ] T011 [P] Implement a structured API error normalizer in frontend/src/api/errors.ts
- [ ] T012 Implement an `X-Correlation-Id` WebFilter that sets request/response correlation ID in order-service/src/main/java/rs/master/o2c/order/observability/CorrelationIdWebFilter.java
- [ ] T013 Implement an `X-Correlation-Id` WebFilter that sets request/response correlation ID in checkout-service/src/main/java/rs/master/o2c/checkout/observability/CorrelationIdWebFilter.java
- [ ] T014 Implement an `X-Correlation-Id` WebFilter that sets request/response correlation ID in payment-service/src/main/java/rs/master/o2c/payment/observability/CorrelationIdWebFilter.java
- [ ] T015 [P] Define correlation header constants in common-events/src/main/java/rs/master/o2c/events/CorrelationHeaders.java
- [ ] T016 [P] Replace `System.out.println` with structured SLF4J logging (include correlationId where available) in order-service/src/main/java/rs/master/o2c/order/messaging/consumer/CheckoutEventsConsumer.java
- [ ] T017 [P] Replace `System.out.println` with structured SLF4J logging (include correlationId where available) in checkout-service/src/main/java/rs/master/o2c/checkout/messaging/consumer/OrderEventsConsumer.java
- [ ] T018 [P] Replace `System.out.println` with structured SLF4J logging (include correlationId where available) in payment-service/src/main/java/rs/master/o2c/payment/messaging/consumer/PaymentRequestsConsumer.java

**Checkpoint**: Correlation IDs show up consistently in REST responses and logs; frontend has consistent error handling.

---

## Phase 3: User Story 1 - Create Order (Priority: P1) 🎯 MVP

**Goal**: Create an order and immediately show the new order ID and initial status.

**Independent Test**: Submit Create Order in the UI and see `orderId`, `status=CREATED`, and a `correlationId` without waiting for Kafka processing.

### Tests for User Story 1

- [ ] T019 [P] [US1] Add unit tests for create-order validation + response mapping in order-service/src/test/java/rs/master/o2c/order/api/OrderControllerTest.java

### Implementation for User Story 1

- [ ] T020 [US1] Add Bean Validation constraints for create order input in order-service/src/main/java/rs/master/o2c/order/api/dto/CreateOrderRequest.java
- [ ] T021 [US1] Extend create order response to include status + correlationId in order-service/src/main/java/rs/master/o2c/order/api/dto/CreateOrderResponse.java
- [ ] T022 [US1] Update `POST /orders` to use `@Valid` and return the extended response in order-service/src/main/java/rs/master/o2c/order/api/OrderController.java
- [ ] T023 [US1] Persist correlationId for later retrieval by adding `correlation_id` to `orders` in order-service/src/main/resources/db/migration/V3__orders_add_correlation_id.sql
- [ ] T024 [US1] Add `correlationId` field plumbing to the order entity in order-service/src/main/java/rs/master/o2c/order/persistence/entity/OrderEntity.java
- [ ] T025 [US1] Set order correlationId from request context during creation in order-service/src/main/java/rs/master/o2c/order/domain/impl/OrderServiceImpl.java
- [ ] T026 [P] [US1] Create the Create Order page UI in frontend/src/pages/CreateOrderPage.vue
- [ ] T027 [P] [US1] Add typed API client for create order in frontend/src/api/orderApi.ts
- [ ] T028 [P] [US1] Add a minimal accessible form field component in frontend/src/components/FormField.vue

**Checkpoint**: User Story 1 is complete and demoable.

---

## Phase 4: User Story 2 - Orders List & Search (Priority: P2)

**Goal**: List recent orders with filters, and show per-row order/checkout/payment statuses that refresh over time.

**Independent Test**: Create multiple orders, open the list, filter by customer/date, and see statuses update via polling without a full page reload.

### Tests for User Story 2

- [ ] T029 [P] [US2] Add unit tests for query param validation and filtering in order-service/src/test/java/rs/master/o2c/order/api/OrderQueryControllerTest.java
- [ ] T030 [P] [US2] Add controller tests for checkout status batch endpoint in checkout-service/src/test/java/rs/master/o2c/checkout/api/CheckoutStatusControllerTest.java
- [ ] T031 [P] [US2] Add controller tests for payment status batch endpoint in payment-service/src/test/java/rs/master/o2c/payment/api/PaymentStatusControllerTest.java

### Implementation for User Story 2

- [ ] T032 [P] [US2] Add list DTO for orders in order-service/src/main/java/rs/master/o2c/order/api/dto/OrderSummaryDto.java
- [ ] T033 [US2] Implement `GET /orders` list/search endpoint in order-service/src/main/java/rs/master/o2c/order/api/OrderQueryController.java
- [ ] T034 [US2] Add repository query methods for list/search in order-service/src/main/java/rs/master/o2c/order/persistence/repository/OrderRepository.java
- [ ] T035 [P] [US2] Add checkout status DTOs in checkout-service/src/main/java/rs/master/o2c/checkout/api/dto/CheckoutStatusDto.java
- [ ] T036 [US2] Implement `GET /checkouts/status?orderIds=` batch endpoint in checkout-service/src/main/java/rs/master/o2c/checkout/api/CheckoutStatusController.java
- [ ] T037 [US2] Add repository query methods for status lookups in checkout-service/src/main/java/rs/master/o2c/checkout/persistence/repository/CheckoutRepository.java
- [ ] T038 [P] [US2] Add payment status DTOs (incl. failureReason) in payment-service/src/main/java/rs/master/o2c/payment/api/dto/PaymentStatusDto.java
- [ ] T039 [US2] Implement `GET /payments/status?orderIds=` batch endpoint in payment-service/src/main/java/rs/master/o2c/payment/api/PaymentStatusController.java
- [ ] T040 [US2] Add repository query methods for status lookups in payment-service/src/main/java/rs/master/o2c/payment/persistence/repository/PaymentRepository.java
- [ ] T041 [US2] Implement Orders List page UI with filters and polling in frontend/src/pages/OrdersListPage.vue
- [ ] T042 [P] [US2] Implement list state + polling refresh orchestration in frontend/src/state/ordersList.ts
- [ ] T043 [P] [US2] Add typed clients for list + batch status in frontend/src/api/orderApi.ts and frontend/src/api/statusApi.ts

**Checkpoint**: User Story 2 is complete and makes eventual consistency visible.

---

## Phase 5: User Story 3 - Order Details & Timeline (Priority: P3)

**Goal**: Show an aggregated order details view including correlationId and a timeline view of what happened.

**Independent Test**: Open order details and observe timeline/status updates as checkout/payment progress.

### Tests for User Story 3

- [ ] T044 [P] [US3] Add controller tests for order details endpoint in order-service/src/test/java/rs/master/o2c/order/api/OrderDetailsControllerTest.java
- [ ] T045 [P] [US3] Add controller tests for payment timeline endpoint in payment-service/src/test/java/rs/master/o2c/payment/api/PaymentTimelineControllerTest.java

### Implementation for User Story 3

- [ ] T046 [P] [US3] Add details DTOs (order + statuses + correlationId) in order-service/src/main/java/rs/master/o2c/order/api/dto/OrderDetailsDto.java
- [ ] T047 [US3] Implement `GET /orders/{orderId}` details endpoint in order-service/src/main/java/rs/master/o2c/order/api/OrderDetailsController.java
- [ ] T048 [P] [US3] Add checkout timeline migration to support `updated_at` in checkout-service/src/main/resources/db/migration/V2__checkout_add_updated_at.sql
- [ ] T049 [US3] Add `updatedAt` field + completion/failure reason plumbing in checkout-service/src/main/java/rs/master/o2c/checkout/persistence/entity/CheckoutEntity.java
- [ ] T050 [US3] Ensure checkout marks COMPLETED (and updates timestamps) on success in checkout-service/src/main/java/rs/master/o2c/checkout/messaging/handler/OrderEventsHandler.java
- [ ] T051 [P] [US3] Add checkout timeline endpoint based on `created_at/updated_at/status` in checkout-service/src/main/java/rs/master/o2c/checkout/api/CheckoutTimelineController.java
- [ ] T052 [P] [US3] Add payment timeline endpoint based on `payment` + `payment_attempt` tables in payment-service/src/main/java/rs/master/o2c/payment/api/PaymentTimelineController.java
- [ ] T053 [US3] Implement Order Details page UI (summary + correlationId + timeline + retry button slot) in frontend/src/pages/OrderDetailsPage.vue
- [ ] T054 [P] [US3] Implement timeline merge/sort logic in frontend/src/domain/timeline.ts
- [ ] T055 [P] [US3] Add typed clients for details + timelines in frontend/src/api/detailsApi.ts and frontend/src/api/timelineApi.ts

**Checkpoint**: User Story 3 is complete and traceable.

---

## Phase 6: User Story 4 - Payment Failure + Retry (Priority: P4)

**Goal**: When payment fails, show a failure reason and allow idempotent retry.

**Independent Test**: Force a failure (e.g., currency `FAIL`), see `FAILED` + reason, click retry, and observe a new attempt and eventual success/failure.

### Tests for User Story 4

- [ ] T056 [P] [US4] Add tests for retry endpoint idempotency behavior in payment-service/src/test/java/rs/master/o2c/payment/api/PaymentRetryControllerTest.java

### Implementation for User Story 4

- [ ] T057 [US4] Publish payment requests from checkout completion in checkout-service/src/main/java/rs/master/o2c/checkout/kafka/PaymentRequestPublisher.java
- [ ] T058 [US4] Wire checkout to send `PaymentRequested` to `payment.requests.v1` on success in checkout-service/src/main/java/rs/master/o2c/checkout/messaging/handler/OrderEventsHandler.java
- [ ] T059 [US4] Refactor payment processing to support multiple attempts per checkout using `payment_attempt` in payment-service/src/main/java/rs/master/o2c/payment/messaging/handler/PaymentRequestsHandler.java
- [ ] T060 [US4] Add retry REST endpoint that enqueues a new `PaymentRequested` with stable `retryRequestId` in payment-service/src/main/java/rs/master/o2c/payment/api/PaymentRetryController.java
- [ ] T061 [P] [US4] Add retry DTOs (request/response) in payment-service/src/main/java/rs/master/o2c/payment/api/dto/RetryPaymentRequest.java
- [ ] T062 [US4] Persist retry request idempotency (unique) in payment-service/src/main/resources/db/migration/V2__payment_retry_idempotency.sql
- [ ] T063 [P] [US4] Show payment failure reason and retry action in frontend/src/pages/OrderDetailsPage.vue
- [ ] T064 [P] [US4] Add typed client for retry endpoint in frontend/src/api/paymentApi.ts

**Checkpoint**: User Story 4 is complete with idempotent retry behavior.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final alignment, documentation, and integration verification.

- [ ] T065 [P] Add a manual smoke checklist for the end-to-end demo in specs/001-o2c-demo-app/checklists/smoke.md
- [ ] T066 Add a local demo script (curl commands) for US1–US4 in specs/001-o2c-demo-app/scripts/demo.ps1
- [ ] T067 [P] Add basic accessibility checks (labels, focus, keyboard navigation) to frontend/src/components/StatusBadge.vue and frontend/src/components/ErrorBanner.vue
- [ ] T068 Add integration test coverage for Kafka flow (order→checkout→payment) using JUnit tags in payment-service/src/test/java/rs/master/o2c/payment/messaging/PaymentFlowIT.java

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies
- **Phase 2 (Foundational)**: Depends on Phase 1; blocks all user stories
- **Phase 3+ (User Stories)**: Depend on Phase 2; can proceed in parallel if staffed
- **Phase 7 (Polish)**: Depends on desired stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational; provides MVP
- **US2 (P2)**: Can start after Foundational; benefits from data created by US1
- **US3 (P3)**: Can start after Foundational; benefits from US2 endpoints for status reuse
- **US4 (P4)**: Depends on payment flow wiring (US2/US3 can still be demoed without retry)

### Parallel Opportunities

- Tasks marked **[P]** can be worked on concurrently
- After Phase 2 completes, backend endpoints and frontend pages can be built in parallel per story

---

## Parallel Example: User Story 2

```text
Run in parallel:
- T035 [US2] Checkout status DTOs/controller/repo tasks
- T038 [US2] Payment status DTOs/controller/repo tasks
- T041–T043 [US2] Frontend list + state + API clients
```

## Parallel Example: User Story 1

```text
Run in parallel:
- T020–T022 [US1] Backend DTO + controller changes
- T026–T028 [US1] Frontend page + client + components
```

## Parallel Example: User Story 3

```text
Run in parallel:
- T048–T051 [US3] Checkout schema/entity/controller work
- T052 [US3] Payment timeline endpoint
- T053–T055 [US3] Frontend details + timeline merge + clients
```

## Parallel Example: User Story 4

```text
Run in parallel:
- T057–T058 [US4] Checkout publishes PaymentRequested
- T059–T062 [US4] Payment retry + multi-attempt support
- T063–T064 [US4] Frontend retry UX + client
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 + Phase 2
2. Complete Phase 3 (US1)
3. Validate US1 manually + via tests

### Incremental Delivery

1. Add US2 (list + batch statuses) and demo eventual consistency via polling
2. Add US3 (details + timeline)
3. Add US4 (retry) once payment flow wiring is in place
2. US1 (Create Order) → demoable MVP
3. US2 (Orders list) → shows eventual consistency at scale
4. US3 (Details/timeline) → makes async flow understandable
5. US4 (Failure + retry) → demonstrates resilience + idempotency
6. Polish + integration script
