# Quickstart: O2C Demo Application (Local)

This quickstart is copy/paste-friendly for a fresh clone on **Windows + PowerShell**.

## Prerequisites

- Docker Desktop (for Kafka + MySQL containers)
- JDK 21 (to run Spring Boot services)
- Node.js 18+ (to run the Vue + Vite frontend)

## 1) Start Kafka + MySQL databases (Docker Compose)

From the repository root:

```powershell
# Start Kafka, Kafka UI, and per-service MySQL containers
docker compose -f docker/docker-compose.local.yml up -d

# Optional: verify containers are running
docker compose -f docker/docker-compose.local.yml ps
```

What this starts:

- Kafka broker: `localhost:9092`
- Kafka UI: `http://localhost:8089`
- MySQL (checkout): `localhost:3307` (DB: `checkout_db`, user: `root`, password: `root`)
- MySQL (order): `localhost:3308` (DB: `order_db`, user: `root`, password: `root`)
- MySQL (payment): `localhost:3309` (DB: `payment_db`, user: `root`, password: `root`)

To stop everything later:

```powershell
docker compose -f docker/docker-compose.local.yml down
```

## 2) Run backend services locally

Run each service in its own terminal (from the repository root).

```powershell
# checkout-service (port 8081)
.\gradlew.bat :checkout-service:bootRun
```

```powershell
# order-service (port 8082)
.\gradlew.bat :order-service:bootRun
```

```powershell
# payment-service (port 8083)
.\gradlew.bat :payment-service:bootRun
```

### Backend ports / base URLs

- checkout-service: `http://localhost:8081`
- order-service: `http://localhost:8082`
- payment-service: `http://localhost:8083`

## 3) Start the frontend (Vue + Vite)

From the repository root:

```powershell
cd frontend

# One-time install
npm install

# Optional: create a local env file for base URLs
# (no secrets; safe to keep local)
Copy-Item .env.example .env.local

# Start Vite dev server
npm run dev
```

By default, Vite serves the UI at:

- `http://localhost:5173/`

## 4) Open the demo in the browser

- Create Order: `http://localhost:5173/create`
- Orders List: `http://localhost:5173/orders`
- Order Details (example): `http://localhost:5173/orders/123`

## Troubleshooting

- If ports are already in use, stop conflicting processes or update the port bindings.
- If Kafka UI is up but no topics appear yet, create an order and wait for the workflow to emit events.
