# O2C Kafka Platform — Full App Review

**Date**: 2026-01-13  
**Repo**: `o2c-kafka-platform` (multi-module Gradle + React client)  
**Scope**: repo-level architecture, developer experience, security, reliability, eventing/Kafka, testing, and frontend integration.

---

## Executive Summary

This is a solid, *teachable* event-driven Order-to-Cash (O2C) demo platform:

- Backend is cleanly split into services (`order-service`, `checkout-service`, `payment-service`, `auth-service`) plus `common-events`.
- The services use **Spring Boot 3.5.x + WebFlux** with **R2DBC** for reactive DB access and **reactor-kafka** via Spring’s `ReactiveKafkaConsumerTemplate` / `ReactiveKafkaProducerTemplate`.
- Local infra is straightforward: **Kafka + Kafka UI + MySQL-per-service** via Docker Compose.
- You’ve already implemented important demo-grade patterns: correlation IDs, batch status endpoints (per plan), and idempotent retries (notably for payment retries).

The biggest opportunities are around:

1) **Aligning docs/UX with the chosen integration model** (direct-to-services vs auth-service BFF proxy).  
2) **Hardening Kafka error handling** (backoff, DLQ usage, and poison-message strategy).  
3) **Tightening security hygiene** (avoid printing JWT secret info; clarify CSRF/CORS posture; reduce TRACE logging that can leak sensitive material).  
4) **Build/dependency consistency** (avoid version drift and duplicate dependencies across modules).

---

## What’s In This Repo (as observed)

### Modules

- **`order-service/`** — REST API for order creation + querying; consumes checkout events.
- **`checkout-service/`** — consumes order events; exposes checkout status + timeline.
- **`payment-service/`** — consumes payment requests; exposes payment status + timeline; payment retry publishes `payment.requests.v1`.
- **`payment-provider/`** — fake provider service (port 8090) calling back into `payment-service` via webhook.
- **`auth-service/`** — login + MFA + session cookie; provides a BFF-style proxy `GET/POST /api/{service}/**`.
- **`common-events/`** — shared event envelope types, topic names, producer names.
- **`o2c-client/`** — React + TypeScript + Vite UI.

### Local Ports

From config/docs:

- Kafka: `localhost:9092` (host), `kafka:29092` (docker network)
- Kafka UI: `http://localhost:8089`
- MySQL:
  - checkout: `3307` (`checkout_db`)
  - order: `3308` (`order_db`)
  - payment: `3309` (`payment_db`)
  - auth: `3310` (`auth_db`)
- Services (local profiles):
  - checkout-service: `8081`
  - order-service: `8082`
  - payment-service: `8083`
  - auth-service: `8084`
  - payment-provider: `8090`

---

## Architecture & Flow

A simplified (conceptual) flow:

```
UI (o2c-client)
  |  (Option B) direct REST calls
  |      -> order-service / checkout-service / payment-service
  |
  |  (Option A-ish) auth-service as BFF
  |      -> auth-service cookie session
  |      -> /api/{service}/... proxied to services with bearer JWT
  v
order-service --(Kafka: order.events.v1)--> checkout-service
checkout-service --(Kafka: checkout.events.v1)--> order-service
order/checkout --(Kafka: payment.requests.v1)--> payment-service
payment-service --(Kafka: payment.events.v1)--> (consumers depending on design)

payment-service <-(webhook)- payment-provider
```

Kafka topics are explicitly versioned in `common-events/src/main/java/rs/master/o2c/events/TopicNames.java`:

- `order.events.v1`
- `checkout.events.v1`
- `payment.requests.v1`
- `payment.events.v1`
- `payment.events.dlq.v1` (currently defined; see DLQ notes below)

---

## Strengths (Keep These)

- **Clear module boundaries** and per-service databases.
- **Java 21 toolchain** across modules.
- **Consistent, structured error responses** via `GlobalExceptionHandler` (e.g., order/payment). This is huge for UI stability.
- **Correlation ID** patterns exist in services (WebFilter + Kafka header logging).
- **Idempotent retry** implementation in payment is good:
  - `payment-service/.../PaymentRetryController.java` inserts `payment_retry_request` row and returns `ALREADY_ACCEPTED` on duplicate key.
- **Reactive Kafka + manual offset ack** after successful handling (`receiverOffset().acknowledge()` after `handler.handle(...)`).

---

## Gaps / Risks / Recommendations

### P0 — Must Fix / Clarify Soon

1) **Decide and document the frontend integration model (Option B vs auth-service BFF)**

- Your feature plan (`specs/001-o2c-demo-app/plan.md`) says **Option B: UI calls services directly**.
- But `auth-service` is clearly implementing a BFF pattern, including session cookie + proxy at `GET/POST /api/{service}/**` (`auth-service/.../BffProxyController.java`).

**Why it matters**: it affects CORS/CSRF, token handling, UI base URL, error propagation, and local onboarding.

**Repository decision (enterprise default):** use **auth-service as the single browser entry point (BFF)**.

The UI talks to `auth-service` only (cookie session + `/api/{service}/**` proxy). Direct browser access to downstream services is considered non-default and should only exist (if at all) behind a dedicated local/dev-only profile.

**Alternative (not default):** direct-to-services calls.

- **A. Keep Option B (direct to services)**
  - Then local dev must explain how the UI obtains/uses JWT (or how security is relaxed locally).
  - Tighten CORS on each service if you’re allowing browser requests from `http://localhost:5173`.

- **B. Use auth-service as the single browser entry point (“real BFF”)**
  - UI talks to `http://localhost:8084/api/...` only (with `credentials: 'include'`).
  - auth-service issues/holds session cookie and injects bearer token to upstream services.
  - This is generally cleaner for demos: the browser never handles JWT.

**Actionable doc fix**: update quickstart + client README to state which URLs the UI calls.

2) **Remove or gate any JWT secret-related logging**

In multiple services the JWT config prints secret length:

- `payment-service/.../JwtConfig.java`
- `order-service/.../JwtConfig.java`
- `checkout-service/.../JwtConfig.java`

Even “just length” is avoidable and can become a real security footgun (and encourages printing secrets later).

**Recommended**: remove entirely; if you need diagnostics, log a boolean like “secret configured” at `DEBUG`, never print the secret or derived details.

3) **Kafka consumer retry strategy is “retry forever immediately”**

Consumers use `.retry()` with no backoff:

- `payment-service/.../PaymentRequestsConsumer.java`
- `checkout-service/.../OrderEventsConsumer.java`
- (and similarly in order-service)

If a persistent bug or schema incompatibility happens, this can become a tight loop.

**Recommended**:

- Add **exponential backoff** retries.
- Define a **poison message strategy**:
  - parse/validation error → publish to DLQ with reason + original payload
  - transient infra error → retry with backoff
  - unknown error → limited retries then DLQ

Right now you define `payment.events.dlq.v1`, but runtime code doesn’t appear to publish there.

### P1 — Important Improvements

4) **Dependency & build hygiene across modules**

- `checkout-service` and `payment-service` declare `spring-boot-starter-oauth2-resource-server` twice.
- `common-events` pins Jackson versions independently from Spring Boot’s BOM.

**Recommended**:

- Centralize versions via Spring Boot dependency management, Gradle version catalog, or a `java-platform` module.
- Apply the same dependency-management approach to `common-events` to avoid Jackson version drift.

5) **Observability: correlation IDs exist; make them consistently useful end-to-end**

You already propagate correlation IDs via:

- REST header: `X-Correlation-Id` (see `specs/001-o2c-demo-app/plan.md`)
- Kafka header: `CorrelationHeaders.X_CORRELATION_ID`

**Recommended**:

- Ensure every REST response includes the correlation ID header (you already do this in WebFilters).
- Ensure every log line includes it via MDC (if not already).
- Consider adopting the W3C `traceparent` header (optional) to align with OpenTelemetry later.

6) **Security posture: CSRF/CORS and cookie-based auth**

`auth-service` README is honest and good: if you do cross-origin cookies (`credentials: 'include'`) you should think about CSRF.

If auth-service remains the BFF:

- Keep a strict CORS allowlist (you already do).
- Decide on a CSRF strategy for non-GET calls if you move beyond local dev.

If the UI calls services directly:

- Each service needs CORS config + careful token handling.
- Consider consolidating to BFF to avoid exposing service base URLs to browser.

7) **Local logging levels**

`order-service` local config sets Spring Security to TRACE. This can leak auth details into logs.

- Keep TRACE only when actively debugging.
- Default to INFO/DEBUG and selectively turn on TRACE via env var.

### P2 — Nice-to-have / Scaling the Demo

8) **Topic management / auto-create topics**

Docker Kafka is configured with `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`. Great for demos; less great for stability.

If you want to mature this:

- Move topic definitions to a “bootstrap” step or infra provisioning.
- Or ensure services create topics (with AdminClient) on startup in local profile only.

9) **Contract clarity**

You have a strong spec and plan.

Recommended improvements:

- Add a short “API contract index” in docs listing the key endpoints per service.
- Consider OpenAPI generation for each service (even if only in dev).

10) **Frontend README**

`o2c-client/README.md` is still the default Vite template.

Recommended:

- Replace it with your O2C-specific instructions (base URLs, auth approach, polling cadence, env vars).

---

## Testing & Quality

What’s good:

- Unit tests exist (e.g., controller tests for validation).
- There is a meaningful integration test in payment (`PaymentFlowIT`) that uses Kafka topic creation and checks DB state.

Opportunities:

- If integration tests rely on a locally running Kafka, consider Testcontainers to make CI deterministic.
- Separate unit vs integration tests explicitly (e.g., JUnit tags or Gradle source sets) so `test` stays fast.

---

## Data & Consistency

- Per-service DBs and Kafka-driven workflow are a good fit for eventual consistency.
- Payment retry is implemented with an idempotency table, which is the right idea.

Next-level reliability ideas (optional):

- For “exactly-once-ish” processing, consider a lightweight outbox pattern for critical event publishing.
- Ensure every event envelope includes a stable message id (you already use `retryRequestId` as stable message id in retry).

---

## Concrete Next Steps (1–2 day plan)

If you want the biggest improvement per hour:

1) Pick and document one integration model (direct vs auth BFF). Update quickstart + client README.
2) Remove `System.out.println` secret-length logs from JWT configs.
3) Add retry backoff + DLQ publishing for Kafka consumers.
4) Clean up duplicated dependencies and centralize shared versions.

---

## Reference Files (high-signal)

- Local infra: `docker/docker-compose.local.yml`
- Feature spec + plan: `specs/001-o2c-demo-app/spec.md`, `specs/001-o2c-demo-app/plan.md`, `specs/001-o2c-demo-app/quickstart.md`
- Kafka topics: `common-events/src/main/java/rs/master/o2c/events/TopicNames.java`
- Payment retry/idempotency: `payment-service/src/main/java/rs/master/o2c/payment/api/PaymentRetryController.java`
- Auth BFF proxy: `auth-service/src/main/java/rs/master/o2c/auth/bff/BffProxyController.java`
- Kafka consumer pattern: `payment-service/src/main/java/rs/master/o2c/payment/messaging/consumer/PaymentRequestsConsumer.java`
