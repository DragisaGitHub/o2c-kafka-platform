# O2C Client (React + TypeScript + Vite)

This is the browser UI for the O2C demo.

## Integration model (important): auth-service is the BFF

The browser must talk only to **auth-service**.

- Browser endpoints:
  - `POST /auth/login`
  - `POST /auth/mfa/verify`
  - `POST /logout`
  - `GET/POST /api/{service}/**` (BFF proxy)
- After MFA verification, auth-service sets an `O2C_BFF_SESSION` **HttpOnly** cookie.
- The UI never calls `order-service`, `checkout-service`, or `payment-service` directly from the browser.

Local dev routing:

- Vite proxies `/api`, `/auth`, `/logout` to `http://localhost:8084` (see `vite.config.ts`).
- Requests are sent with `credentials: 'include'` so the browser includes `O2C_BFF_SESSION`.

## Prerequisites

- Node.js 18+
- Backend stack running locally (Kafka + MySQL + services). See `../QUICKSTART.md`.

## Run (Yarn only)

From repository root:

```powershell
cd o2c-client

yarn install
yarn dev
```

UI is served at `http://localhost:5173`.

## Login flow

1) Open `http://localhost:5173/login`
2) Log in with username/password
3) Complete MFA (local/dev PIN is typically available via auth-service logs)
4) The browser stores the session as an HttpOnly cookie (no token stored in JS)

## Configuration

You normally do not need env vars for local dev.

Optional overrides (advanced):

- `VITE_ORDER_BASE_URL` (default: `/api/order`)
- `VITE_CHECKOUT_BASE_URL` (default: `/api/checkout`)
- `VITE_PAYMENT_BASE_URL` (default: `/api/payment`)

These should still point to the BFF proxy paths (not to downstream service hosts).
  # O2C Client (React + TypeScript + Vite)
