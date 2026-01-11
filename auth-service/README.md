# auth-service

This service implements the O2C auth flow:

1. `POST /auth/login` validates credentials and creates an MFA challenge.
2. `POST /auth/mfa/verify` verifies the MFA PIN, creates a server-side session, and sets an `O2C_BFF_SESSION` HttpOnly cookie.
3. All `GET/POST /api/**` endpoints are authenticated by resolving `O2C_BFF_SESSION` -> session -> JWT -> roles.

## Local manual tests

Assumptions:
- Service running on `http://localhost:8084` with `local` profile.
- You have a valid `username/password` in `auth_db`.

### curl (Windows Git Bash / WSL)

1) Login (creates MFA challenge)

```bash
curl -i -X POST http://localhost:8084/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice"}'
```

Expected:
- `200 OK` with JSON like `{ "status": "MFA_REQUIRED", "challengeId": "..." }`
- or `401 Unauthorized` if credentials are invalid.

2) Verify MFA (sets the session cookie)

```bash
curl -i -X POST http://localhost:8084/auth/mfa/verify \
  -H "Content-Type: application/json" \
  -d '{"challengeId":"<challengeId>","pin":"<pin>"}'
```

Expected:
- `200 OK` with `Set-Cookie: O2C_BFF_SESSION=...; HttpOnly; Path=/; Max-Age=...`
- or `401 Unauthorized` if the challenge/pin is invalid/expired.

3) Call an authenticated endpoint

```bash
curl -i http://localhost:8084/api/me \
  -H "Cookie: O2C_BFF_SESSION=<sessionId>"
```

Expected:
- `200 OK` with `{ "username": "alice", "roles": ["USER", ...] }` (roles are unprefixed)
- or `401 Unauthorized` if no/invalid cookie.

4) Logout

```bash
curl -i -X POST http://localhost:8084/logout \
  -H "Cookie: O2C_BFF_SESSION=<sessionId>"
```

Expected:
- `204 No Content` and `Set-Cookie: O2C_BFF_SESSION=; ...; Max-Age=0`.

### PowerShell

```powershell
# 1) Login
$loginBody = @{ username = 'alice'; password = 'alice' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8084/auth/login -ContentType 'application/json' -Body $loginBody
$login

# 2) Read PIN from logs (local/dev only) or provide from your MFA channel
$verifyBody = @{ challengeId = $login.challengeId; pin = (Read-Host 'PIN') } | ConvertTo-Json

# 3) Verify and capture the cookie in a web session
Invoke-WebRequest -Method Post -Uri http://localhost:8084/auth/mfa/verify -ContentType 'application/json' -Body $verifyBody -SessionVariable s | Out-Null

# 4) Authenticated call (cookie is sent automatically)
Invoke-RestMethod -Method Get -Uri http://localhost:8084/api/me -WebSession $s

# 5) Logout
Invoke-WebRequest -Method Post -Uri http://localhost:8084/logout -WebSession $s | Out-Null
```

## Browser/FE flow (cookies)

- Use `fetch(..., { credentials: 'include' })` so the browser sends `O2C_BFF_SESSION` on `/api/**`.
- Example:

```ts
await fetch('http://localhost:8084/api/me', {
  method: 'GET',
  credentials: 'include',
});
```

## Cookie behavior

Cookie defaults are centralized under `bff.cookie.*`:
- `HttpOnly=true`
- `Path=/`
- `SameSite=Lax`
- `Max-Age` matches `auth.jwt.expires-in-minutes`
- `Secure=true` by default (non-local) and `Secure=false` in `local` profile (see `application-local.yml`).

## Security notes (CSRF + CORS)

- CSRF is disabled in the reactive security chain.
- Recommended deployment model is same-origin (BFF and API on the same site) with HttpOnly cookies.
- If you enable cross-origin requests with credentials (`credentials: 'include'`) outside local dev:
  - keep a strict CORS allowlist (never `*` with credentials),
  - add a CSRF strategy (token/header) because cookies will be sent automatically,
  - consider `SameSite=None; Secure` if you truly need cross-site cookies.
