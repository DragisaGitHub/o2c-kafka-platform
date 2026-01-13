# Quickstart (Local) — O2C Kafka Platform

This repository is set up with an enterprise-style default: **auth-service is the only browser-facing API (BFF)**.

- The web UI talks only to `auth-service` endpoints (`/auth/**`, `/logout`, and `/api/{service}/**`).
- `auth-service` maintains the `O2C_BFF_SESSION` HttpOnly cookie and injects bearer JWT to upstream services.
- The browser must **not** call `order-service`, `checkout-service`, or `payment-service` directly.

## Prerequisites

- Docker Desktop
- JDK 21
- Node.js 18+

## 1) Start Kafka + MySQL (Docker Compose)

From repository root:

```powershell
docker compose -f docker/docker-compose.local.yml up -d

docker compose -f docker/docker-compose.local.yml ps
```

Kafka UI: `http://localhost:8089`

## 2) Run backend services locally

Run each in its own terminal (from repository root):

```powershell
# checkout-service (8081)
.\gradlew.bat :checkout-service:bootRun
```

```powershell
# order-service (8082)
.\gradlew.bat :order-service:bootRun
```

```powershell
# payment-service (8083)
.\gradlew.bat :payment-service:bootRun
```

```powershell
# auth-service (BFF) (8084)
.\gradlew.bat :auth-service:bootRun
```

```powershell
# optional: payment-provider (fake provider) (8090)
.\gradlew.bat :payment-provider:bootRun
```

## 3) Run the web UI (Vite)

```powershell
cd o2c-client
yarn install
yarn dev
```

Open:

- UI: `http://localhost:5173`
- Login: `http://localhost:5173/login`

## 4) How the UI reaches the backend (important)

- In local dev, Vite proxies `/api`, `/auth`, `/logout` to `http://localhost:8084`.
- So the browser still only uses BFF paths; downstream services do not need browser CORS.

Auth model recap:

- `auth-service` sets an `O2C_BFF_SESSION` **HttpOnly** cookie after MFA verification.
- The UI uses `fetch(..., { credentials: 'include' })` so the cookie is sent on `/api/**` calls.

## 5) Smoke test (runtime checklist)

1) Start infra:

```powershell
docker compose -f docker/docker-compose.local.yml up -d
```

2) Start services (separate terminals):

```powershell
# checkout-service (8081)
.\gradlew.bat :checkout-service:bootRun

# order-service (8082)
.\gradlew.bat :order-service:bootRun

# payment-service (8083)
.\gradlew.bat :payment-service:bootRun

# auth-service (BFF) (8084)
.\gradlew.bat :auth-service:bootRun
```

3) Start UI:

```powershell
cd o2c-client
yarn install
yarn dev
```

4) Login + happy path:

- Open `http://localhost:5173/login`
- Log in + complete MFA (PIN is typically available in `auth-service` logs in local/dev)
- Create an order at `http://localhost:5173/create`

5) Observe the system:

- Kafka UI: `http://localhost:8089` (topics and message flow)
- Order list: `http://localhost:5173/orders` (polling updates)
- Order details/timeline: open an order and verify checkout/payment timelines update

Notes:

- The browser should only call BFF paths (`/auth/**`, `/logout`, `/api/{service}/**`).
- If you need to debug services directly from a terminal, hit their `/actuator/health` endpoints.

## Troubleshooting

- If you see `401` in the UI, log in again (session cookie missing/expired).
- If `/api/...` calls fail during dev, confirm `auth-service` is running on `:8084`.
- If Kafka UI shows no topics yet, create an order and wait for events.
