# Implementation Plan: O2C Demo Application

**Branch**: `001-o2c-demo-app` | **Date**: 2026-01-01 | **Spec**: [specs/001-o2c-demo-app/spec.md](spec.md)
**Input**: Feature specification from `/specs/001-o2c-demo-app/spec.md`

## Summary

Deliver an end-to-end Order-to-Cash demo experience on top of the existing Kafka-driven microservices platform:

- Backend: existing Spring Boot (WebFlux) services (`order-service`, `checkout-service`, `payment-service`) integrating via REST + Kafka.
- Frontend: new Vue 3 + TypeScript app that calls backend REST APIs only.
- UX: makes eventual consistency visible via polling, shows correlation IDs for traceability, and supports payment failure + idempotent retry.

## Integration approach (selected)

**Selected**: **Option B** (frontend calls services directly)

**Why**:

- Smallest demo surface area: avoids introducing and maintaining a new BFF/gateway service.
- Aligns with the existing platform boundaries (three services already exist and are independently deployable).
- Keeps the demo focused on event-driven workflow + eventual consistency, not API gateway composition.

**Trade-offs and mitigation**:

- Option B can create N+1 REST calls for aggregated views; this plan mitigates that with **batch** status endpoints on checkout/payment services.

## Local ports / base URLs

- `order-service`: `http://localhost:8082`
- `checkout-service`: `http://localhost:8081`
- `payment-service`: `http://localhost:8083`

## Contract mapping (frontend ↔ REST)

All frontend calls MUST use REST only. The frontend sets `X-Correlation-Id` on requests and services echo it back (and include it in error responses where applicable).

- **Create Order screen**
  - Calls `order-service` (`http://localhost:8082`)
    - `POST /orders`

- **Orders List / Search screen**
  - Calls `order-service` (`http://localhost:8082`)
    - `GET /orders?customerId=&fromDate=&toDate=&limit=&cursor=`
  - Calls `checkout-service` (`http://localhost:8081`) for batch checkout statuses (to avoid per-row calls)
    - `GET /checkouts/status?orderIds={id1,id2,...}`
  - Calls `payment-service` (`http://localhost:8083`) for batch payment statuses (to avoid per-row calls)
    - `GET /payments/status?orderIds={id1,id2,...}`

- **Order Details screen**
  - Calls `order-service` (`http://localhost:8082`)
    - `GET /orders/{orderId}`
  - Calls `checkout-service` (`http://localhost:8081`) for timeline
    - `GET /checkouts/timeline?orderId={orderId}`
  - Calls `payment-service` (`http://localhost:8083`) for timeline
    - `GET /payments/timeline?orderId={orderId}`

- **Retry Payment action** (from Order Details)
  - Calls `payment-service` (`http://localhost:8083`)
    - `POST /payments/{orderId}/retry`
      - Request body: `RetryPaymentRequest { retryRequestId: string }`
      - Behavior: idempotent for duplicate `retryRequestId`

## Technical Context

**Backend language/version**: Java 21

**Backend frameworks**:

- Spring Boot (reactive/WebFlux)
- Reactive Kafka client (project uses `ReactiveKafkaProducerTemplate` / reactive consumers)

**Frontend**:

- Vue 3 + TypeScript + Vite

**Storage**:

- MySQL per service (as currently used by the platform)
- Flyway migrations for schema evolution

**Testing**:

- JUnit 5
- Unit tests for business logic
- Integration tests for Kafka + database interactions

**Target platform**: Local dev (Docker Compose + local JVM services) and standard JVM runtime

**Project type**: Multi-module backend + separate web frontend at repository root

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Boundaries:
  - No cross-service database access.
  - No shared domain/persistence models across services.
  - Cross-service integration is REST + Kafka only.
  - Frontend consumes REST only (no Kafka access).
- DTO discipline:
  - REST and Kafka payloads use boundary DTOs.
  - Persistence entities are not used as API/event DTOs.
- Contracts & naming:
  - Any new/changed Kafka event has an explicit, versioned name.
  - Any new/changed topic is explicit and versioned.
  - Status values are canonical and mapped consistently for frontend.
  - Contract changes include compatibility notes and migration plan if breaking.
- Testing (non-negotiable):
  - Business logic has unit tests.
  - Kafka flows and DB interactions have integration tests.
  - Unit vs integration vs E2E are clearly separated (Gradle source sets and/or JUnit tags).
- Reliability:
  - Kafka consumers are idempotent.
  - Retry + dead-letter/quarantine handling is defined.
  - Invalid message behavior is explicitly defined.
- Observability & UX:
  - Correlation IDs propagate through REST and Kafka.
  - Logging is structured and includes correlation ID and stable error codes.
  - Frontend states account for eventual consistency (pending/processing).

## Project Structure

### Documentation (this feature)

```text
specs/001-o2c-demo-app/
├── checklists/
│   └── requirements.md  # Spec readiness checklist
├── plan.md              # This file
├── spec.md              # Feature specification (source of truth)
├── tasks.md             # Implementation task breakdown
└── quickstart.md        # Local dev quickstart (to be added in Phase 1)
```

### Source Code (repository root)
```text
order-service/
checkout-service/
payment-service/
common-events/
frontend/                # Vue app (to be added in Phase 1)
dockers/
```

**Structure Decision**: Keep the existing multi-module backend as-is and add a single Vue 3 frontend at `frontend/` that calls the three services directly over REST.

## Complexity Tracking

No Constitution Check violations are expected for this feature.
