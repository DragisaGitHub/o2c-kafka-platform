# Auth-Service (WebFlux) Authentication/Authorization Audit

Date: 2026-01-11  
Module: `auth-service` (Spring Boot 3.5, WebFlux, Spring Security)

## 1) What Exists Today (Summary)

### Entry points
- **Unified MFA → BFF cookie login (browser flow)**
  - `POST /auth/login`: username/password validated against DB; creates a PIN challenge; responds `MFA_REQUIRED` with `challengeId` (PIN is not returned).
  - `POST /auth/mfa/verify`: verifies challengeId+PIN; generates a JWT; creates a BFF session; sets cookie `O2C_BFF_SESSION` (HttpOnly, SameSite=Lax); responds `AUTHENTICATED` with `username`.
- **Local-only shortcut login (optional)**
  - `POST /login` is available only under the `local` profile (kept for convenience).
- **BFF session endpoints**
  - `POST /logout`: invalidates in-memory session and expires cookie.
  - `GET /api/session`: returns username from the authenticated principal.
  - `GET /api/me`: returns `{username, roles}` from the Spring Security context.
- **BFF proxy**
  - `GET/POST/... /api/{service}/**`: proxies requests to `order/checkout/payment` upstream services, injecting `Authorization: Bearer <accessToken>` derived from the authenticated request.

### Security chain
- A custom `AuthenticationWebFilter` is installed for `pathMatchers("/api/**")`.
- Authentication is derived from cookie `O2C_BFF_SESSION`:
  - sessionId is looked up in a `ConcurrentHashMap`.
  - the stored access token is parsed/verified via JJWT.
  - roles are extracted from a `roles` claim and mapped to `ROLE_<role>` authorities.
- Method security is enabled and admin endpoints use `@PreAuthorize`.
- CSRF is **disabled**.
- `NoOpServerSecurityContextRepository` is used (no persistence between requests; each request re-authenticates).

### JWT contents & role mapping
- JWT is signed with **HMAC SHA-384 (HS384)**.
- Claims include:
  - `sub`: username
  - `iat`, `exp`
  - `mfa: true`
  - `roles`: emitted as a JSON array (List of strings)
- Role mapping: each extracted role string becomes `ROLE_<role>`.

### MFA/PIN challenge
- Challenge has a configurable TTL (default 120s via `auth.mfa.challenge-ttl-seconds`).
- Expired challenges are rejected and cleaned up opportunistically.

### Admin endpoints
- `POST /api/admin/users` is protected by `@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN')")`.
- Creates users + inserts roles into `user_roles` in a reactive transaction.

## 2) What’s Good

- **Per-request authentication** with a no-op security context repository: reduces risk of stale security context.
- **HttpOnly cookie** for the browser session.
- **Role-based access control** uses Spring’s built-in method security and normalized `ROLE_*` authorities.
- **Transactional admin user creation** with FK-backed role table.

## 3) Risks / Gaps (Security & Operational)

### Browser/BFF flow risks
- **Two parallel login flows** (`/login` and `/auth/*`) increases attack surface and makes the “real” browser flow ambiguous.
- `GET /api/session` and `BffProxyController` **manually read cookie/session** instead of using the authenticated principal; that duplicates logic and makes it easier to drift.
- Cookie is created with **`secure(false)` hardcoded** (OK for local HTTP, unsafe for production TLS).
- Cookie uses `SameSite=Lax`: good baseline, but **not a full CSRF defense** for state-changing requests.

### MFA/PIN risks
- `POST /auth/login` currently **returns the PIN** and also logs it. Returning the PIN defeats MFA.
- **No TTL** for challenges; an attacker who obtains a `challengeId` can retry later.
- No cleanup of in-memory challenge map → memory growth.
- Challenge consumption occurs before PIN validation (current `consume()`), which is good for single-use, but should still reject expired challenges.

### JWT handling
- Startup fails fast if `auth.jwt.secret` is blank or < 32 bytes.
- JWT parsing enforces the expected signing algorithm (`HS384`).
- Role claim parsing is tolerant of legacy array/list forms.

### CSRF
- CSRF is currently **disabled** globally. With cookie-authenticated endpoints, this is typically a production risk.
  - SameSite=Lax reduces cross-site POSTs, but does not cover all cases (top-level GET navigations, legacy browsers, or if SameSite changes).

### In-memory state
- In-memory sessions and challenges:
  - **Not horizontally scalable** (multi-instance will break affinity).
  - Sessions/challenges lost on restart.
  - Memory growth risk without TTL/cleanup.

## 4) Recommendations

### Local / dev
- Keep in-memory BFF sessions and challenges.
- Keep `secure(false)` for cookies on HTTP.
- Allow PIN logging **only behind a local/dev guard**.
- Use a long but developer-friendly JWT secret (still >= 32 bytes) and fail fast if too short.

### Production
- Prefer a **server-side session store** (Redis) or a **pure bearer token** approach (no server session map) depending on architecture.
- Set cookie `Secure`, consider `SameSite=Strict` if UX allows, and consider a CSRF strategy:
  - standard cookie + header token (double-submit), or
  - Spring Security CSRF with `CookieServerCsrfTokenRepository`.
- Store JWT secret in a secret manager (Key Vault, env-injected secret) and rotate.
- Add rate limiting / lockout for MFA verification attempts.

## 5) Concrete Refactoring Plan (This PR)

1. **Unify login flow**: make MFA the single browser login.
   - `/auth/login` returns only `{status, challengeId}`.
   - `/auth/mfa/verify` sets the BFF cookie and creates the server session; returns `{status, username}` (or 204).
   - Keep `/login` as **local-only** (or remove) to reduce production surface.
2. **Add `/api/me`** that reads from `SecurityContext` and returns `{username, roles}`.
3. **Add PIN challenge TTL** (default 120s) configurable via `auth.mfa.challenge-ttl-seconds` in `application-local.yml`.
   - Reject expired challenges.
   - Cleanup expired entries on access (and/or scheduled cleanup).
4. **JWT hardening**:
   - Fail fast if secret is blank or < 32 bytes.
   - Keep HS384 for now; enforce algorithm on parse.
   - Emit roles as `List<String>` but accept legacy deserialization forms.
5. **Tests**:
   - MFA verify sets cookie and creates session.
   - `/api/me` returns username + roles for authenticated request.
   - expired PIN challenge rejected.
   - admin create-user requires admin role.
