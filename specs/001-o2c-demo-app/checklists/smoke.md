# Smoke Checklist: O2C Demo (End-to-End)

**Purpose**: Manual, end-to-end validation of the demo across infra, services, and UI.

## Local URLs / Ports

- Order service: http://localhost:8082
- Checkout service: http://localhost:8081
- Payment service: http://localhost:8083
- Web client: http://localhost:5173

## Cross-cutting

- Correlation header: `X-Correlation-Id`
- Kafka topic (payment requests): `payment.requests.v1`

---

## 1) Infra / prerequisites

- [ ] Docker Desktop is running
- [ ] Start local infra from repo root:
  - `docker compose -f docker/docker-compose.local.yml up -d`
- [ ] Verify containers are up:
  - `docker compose -f docker/docker-compose.local.yml ps`

---

## 2) Start services

- [ ] Start services (3 terminals recommended):
  - `./gradlew.bat :order-service:bootRun`
  - `./gradlew.bat :checkout-service:bootRun`
  - `./gradlew.bat :payment-service:bootRun`

## 3) Service health checks (copy/paste)

- [ ] Order service:
  - `curl.exe -sS http://localhost:8082/actuator/health`
  - Expected: `{"status":"UP"}`
- [ ] Checkout service:
  - `curl.exe -sS http://localhost:8081/actuator/health`
  - Expected: `{"status":"UP"}`
- [ ] Payment service:
  - `curl.exe -sS http://localhost:8083/actuator/health`
  - Expected: `{"status":"UP"}`

---

## 4) Start web client

- [ ] In `o2c-client/`:
  - Command: `npm install` (first time only)
  - Command: `npm run dev`
- [ ] Open: http://localhost:5173

**If it fails**
- Ensure `.env` is configured with base URLs (see `o2c-client/.env.local`)
- Confirm services are reachable from the browser

---

## 5) UI flows checklist (US1–US4)

### US1 — Create Order (success)

- [ ] In the UI, create an order with a normal currency (e.g., `USD`)
- [ ] Verify the response shows:
  - `orderId` present
  - `status` is `CREATED`
  - `correlationId` present

**If it fails**
- Open browser devtools Network tab
- Check the response header `X-Correlation-Id`
- Check order-service logs for that correlation id

---

### US2 — Orders list filters + polling

- [ ] Open the Orders List page
- [ ] Verify that:
  - The new order appears
  - Status fields update over time without a full page reload (polling)

**If it fails**
- Check that list/search endpoint responds:
  - GET http://localhost:8082/orders
- Check batch endpoints respond for the listed order ids:
  - GET http://localhost:8081/checkouts/status?orderIds={id1,id2,...}
  - GET http://localhost:8083/payments/status?orderIds={id1,id2,...}

---

### US3 — Order details page + timeline

- [ ] Open the Order Details page for the created order
- [ ] Verify that:
  - Correlation id is displayed
  - Timeline shows checkout/payment events as they occur
  - Timeline updates with polling (no manual refresh required)

**If it fails**
- Confirm timeline endpoints return JSON arrays:
  - GET http://localhost:8081/checkouts/{orderId}/timeline
  - GET http://localhost:8083/payments/{orderId}/timeline
- Use correlation id to find the related log lines across services

---

### US4 — Force payment failure + retry

**Goal**: Ensure a failed payment shows a reason and retry enqueues a new attempt.

- [ ] Create a new order using currency `FAIL`
- [ ] In Order Details, verify:
  - Payment status becomes `FAILED`
  - A failure reason is displayed (non-empty preferred)
  - A "Retry payment" button is visible


- [ ] Click "Retry payment" twice
- [ ] Verify idempotent behavior:
  - Two clicks do NOT create duplicate attempts for the same retry request
  - After polling, payment timeline/status reflects at most one new attempt for a single retry action

## Debug hints (If it fails…)

- Correlation id tracing:
  - In browser devtools Network tab, copy the `X-Correlation-Id` header
  - Search that id in service logs (order/checkout/payment)

- Where to look:
  - `docker logs <container>` (or `docker compose -f docker/docker-compose.local.yml logs -n 200`)
  - Service terminal output (stack traces / connection errors)

- Kafka / flow hints:
  - Topic: `payment.requests.v1`
  - If payment never starts, verify checkout publishes `PaymentRequested` (checkout-service logs)

- Typical failure modes:
  - CORS (browser blocks calls) → verify allowed origin and correct base URLs
  - Services not running / wrong ports → verify health endpoints and local URLs
  - Kafka not up → verify docker containers and broker logs
  - DB migrations not applied → check service startup logs for Flyway errors

- Tip: eventual consistency
  - Wait 1–2 polling cycles before concluding the system is stuck
