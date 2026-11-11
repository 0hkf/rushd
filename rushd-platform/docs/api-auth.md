# Authentication API — cookie lifecycle

## Policy

The single default lifetime policy is [auth-policy.yml](../backend/src/main/resources/auth-policy.yml):
access JWT **1 hour (3600 seconds)**; opaque refresh credential **3 days (259200 seconds)**.
JWT exp, cookie Max-Age and persisted refresh expiration derive from typed JwtProperties, never independent constants.
JWT_ACCESS_TOKEN_TTL and JWT_REFRESH_TOKEN_TTL can override the whole policy together with all consumers; approved deployment values must preserve the requested one-hour/three-day policy.

## Browser contract

The server delivers credentials only through HttpOnly, host-only cookies:
- rawafed_access: Path=/, TTL from access policy.
- rawafed_refresh: Path=/api/auth, TTL from refresh policy.
Both explicitly use configured SameSite and Secure. Production defaults Secure=true, SameSite=Lax. No authentication JWT/refresh credential is returned in JSON, read by React, or stored in sessionStorage/localStorage. Bearer-header compatibility is intentionally removed.

Refresh credentials are 32 random bytes, base64url encoded with the r1. type prefix. They are NOT JWTs. Only SHA-256 hashes are persisted. The access parser requires token_type=access and validates signature, expiration, nonempty subject; authorities come from the current DB user.

## Endpoints

| Method | Path | Contract |
|---|---|---|
| GET | /api/auth/csrf | Public CSRF bootstrap; returns token and headerName, sets HttpOnly XSRF-TOKEN cookie |
| POST | /api/auth/register | Public, CSRF required; textual BUYER only; required name/email max255, password min6 characters/max72 UTF-8 bytes; 201 user response |
| POST | /api/auth/login | Public, CSRF required; Spring AuthenticationManager + BCrypt; 200 {user}, sets both auth cookies |
| POST | /api/auth/refresh | Public at security routing layer, CSRF and valid refresh cookie required; rotates, sets both cookies, 200 {user} |
| POST | /api/auth/logout | Public routing, CSRF required; revoke family if present, expire cookies, idempotent 204 |
| GET | /api/auth/me | Valid access cookie required, 200 user response |

Login, successful refresh and logout clear the CSRF cookie. Obtain a new CSRF bootstrap before the next unsafe request. Cookies are deleted with the same Path/SameSite/Secure attributes used when created.

User responses contain id,name,email,role,createdAt, never passwords, hashes, secrets or persisted credential state.

## CSRF and CORS

CookieCsrfTokenRepository is used with Spring's default XOR-masked request handler.
The SPA reads a CSRF proof from the /csrf JSON response **into memory only**, and sends X-XSRF-TOKEN for POST/PUT/PATCH/DELETE. The CSRF cookie itself is HttpOnly; this supports separate frontend/API origins without reading API cookies from frontend JavaScript.
All state changes, including login, refresh, register and logout, require CSRF. SameSite is defense in depth, not a substitute.

CORS allows one configured FRONTEND_ORIGIN with credentials; no wildcard. Allowed request headers are Content-Type and X-XSRF-TOKEN. OPTIONS preflight does not require CSRF/authentication. No token headers are exposed.

## Rotation and revocation

Every login creates an AuthSession UUID family. Every successful refresh consumes A and inserts B in one transaction, preserving the family.
The family row is pessimistically locked BEFORE reading token consumption state; concurrent refresh/logout operations serialize.
Reusing consumed A returns 401 and commits family revocation (noRollbackFor is deliberate); B then cannot refresh.
Expired/unknown/malformed credentials and deleted identities fail. Expiration is sliding: each successfully rotated credential lasts the configured refresh TTL from issuance, not an absolute lifetime for the whole login.

Logout revokes the current family, even when presented with a consumed predecessor, and removes browser cookies.
Other devices have separate families and are not logged out automatically.
Access JWTs remain stateless: a copied access JWT can remain usable until its one-hour expiry after logout/replay. This task revokes refresh families, not every outstanding access JWT. Immediate access revocation would require a session claim/revocation check or denylist.
Strict replay handling can end a session when separate browser tabs rotate the same cookie concurrently; single-flight currently coordinates requests within each SPA client, not across tabs.

## Frontend renewal

A single Axios client uses withCredentials=true and VITE_API_BASE_URL.
Initialization always calls /me, without inspecting browser token storage.
A protected 401 triggers one shared refresh promise and one original-request retry. A generation counter lets late responses from pre-refresh requests retry without another rotation.
Auth lifecycle endpoints never recursively refresh. A failed refresh or second 401 clears React user state and navigates /dashboard to /login; public browsing is not forcibly redirected.
Login/logout coordinate with pending renewal. Logout waits for any in-flight rotation before revoking the last credential. A network/CSRF logout failure is shown; the UI does not claim revocation succeeded.
There are no client TTL timers or duplicate expiration constants.

## Errors

Responses use timestamp,status,error,message,path.
- 400: invalid request/validation.
- 401: missing/invalid/expired access, invalid/expired/consumed refresh, wrong login.
- 403: valid authentication without permission OR invalid/missing CSRF. CSRF checking can reject an unsafe anonymous request before authentication; with valid CSRF, missing auth is 401.
- 405: unsupported HTTP method; includes the framework's Allow header.
- 415: unsupported request content type.
- 409: duplicate registration email.
- 429: temporary authentication rate limit; Retry-After supplied.
No stack trace or cryptographic details are returned.

## Deployment and upgrade checklist

1. For an existing legacy schema, review/back up and manually apply [20261006-auth-token-lifecycle.sql](../backend/db/20261006-auth-token-lifecycle.sql). For an empty database only, use the reviewed [empty-database baseline](../backend/db/20261013-empty-database-baseline.sql), which already includes these tables and constraints; do not run this auth upgrade after it. The baseline was manually applied to the local development database with explicit approval; see [local setup](local-development-setup.md). No migration runs automatically at startup.
2. Supply a high-entropy JWT_SECRET of at least 32 UTF-8 bytes from environment/secret management. There is no development fallback and no weak-key padding; startup fails for missing/short keys. Do not reuse test secrets.
3. Set FRONTEND_ORIGIN to the exact frontend origin. Set VITE_API_BASE_URL at frontend build time.
4. Serve both sites over HTTPS, keep AUTH_COOKIE_SECURE=true, explicitly select AUTH_COOKIE_SAME_SITE. Cross-site deployments require None + Secure and remain subject to browser third-party-cookie restrictions; same-site deployment is preferable.
5. Existing browser-storage sessions must log in again once. Old untyped JWTs/Bearer headers are not accepted. Any obsolete browser-storage value is ignored; no insecure compatibility mechanism is added.
6. For local HTTP only, export AUTH_COOKIE_SECURE=false and FRONTEND_ORIGIN=http://localhost:5173 plus a development-only random JWT_SECRET. Use localhost consistently, not a mix of localhost and 127.0.0.1. Spring Boot does not auto-load .env.example.

## Rate limiting and production hardening

The bounded in-process fixed-window limiter defaults to 10 login and 30 refresh requests per client address per minute, max 10000 address/endpoint keys. Counts expire, no permanent account lockout; full capacity fails closed. Typed security.auth.rate-limit configuration is adjustable.
Registration has a separate bounded in-process quota and capacity, defaulting to five POST requests per remote address in one minute, with 10000 address entries. The sixth request receives 429 and `Retry-After: 60`; requests with malformed or invalid bodies count if they pass the preceding CSRF filter. Requests rejected by CSRF before the rate-limit filter do not count. Registration does not consume login/refresh quotas or their storage capacity. Defaults can be configured with `security.auth.registration-rate-limit.requests`, `window`, and `max-clients`; test-only configuration permits more requests to avoid sharing a low quota across unrelated tests.
Only request.getRemoteAddr is used; arbitrary forwarded headers are NOT trusted. Rate-limit filters run only in the security chain, not double-registered servlet filters.
For multiple instances or a reverse proxy, a trusted gateway/WAF with shared limits and correctly configured client-IP handling is a **production deployment requirement**. In-memory limits reset on restart and do not coordinate across instances; no claim of distributed brute-force protection is made.

ADMIN MFA/TOTP is not implemented; require stronger admin authentication before sensitive production use.
Plan operational retention/cleanup for expired/revoked families; preserve consumed hashes long enough for replay detection. No automatic purge is included.

## Security headers and logging

API responses set CSP default-src 'none'; frame-ancestors 'none'; base-uri 'none', X-Frame-Options DENY, Referrer-Policy no-referrer, and Spring's nosniff/no-store defaults.
HSTS is supplied by Spring only for secure requests. A TLS-terminating proxy must safely convey scheme from a trusted edge.
The API CSP does not secure the separately hosted React HTML. Thmanyah WOFF2 fonts are served locally with the frontend assets, not from Tajawal/Google Fonts hosts. The frontend host must apply a reviewed CSP for its actual assets/API origin; no production policy is inferred here, and development HMR policies must not be shipped blindly to production.
Application code never logs raw credentials, password bodies or headers; JwtProperties.toString redacts the key. Gateway/proxy/APM logging must also redact Authorization, Cookie, Set-Cookie and auth request bodies.

## Verification

Backend tests cover policy/expiry, secure cookie attributes, hashing, rotation, replay family revocation, concurrent single-use rotation, wrong token types/signatures, logout, CSRF cookie/header flow, CORS, headers and rate-limit behavior, alongside existing business tests.
Frontend node tests exercise the actual centralized Axios client, single-flight and late 401 handling, failure/no-loop behavior and logout coordination.
MockMvc/H2 tests are not a substitute for deployment-specific PostgreSQL, TLS, proxy and browser-cookie verification.

Spring CSRF design reference: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html

## Approved input hardening — 25 October 2026

- No global Jackson change: RegisterRequest.role and the six Create/Update property enums require exact supported string names. Numeric ordinals, numeric strings, unknown names and booleans return400. Decimal numeric/string request compatibility and the five exact decimal-string response fields are unchanged.
- Registration rejects passwords exceeding72 UTF-8 bytes with400; login returns the usual generic401 without trying a truncated prefix. BCrypt algorithm, salt format and cost remain unchanged. Arabic/emoji can occupy multiple bytes; there is no trimming, normalization or pre-hashing.
- Email must fit255 Java characters both as supplied and after the existing trim/Locale.ROOT lowercase normalization. Unicode lowercase may expand a character; this boundary is checked before persistence rather than allowing a500.
- **Legacy impact:** a user whose old password exceeded72 bytes cannot submit that full password successfully. BCrypt hashes do not reveal original input length; they cannot identify all affected accounts or undo old truncation. A known72-byte prefix may still match an old truncated hash. Before public deployment, approve a secure identity-verified recovery/reset procedure for affected accounts. No account/hash was reset automatically and no self-service reset endpoint was added.
- The application's bounded encoder is a local fail-closed mitigation, **not a dependency upgrade or a claim that all library advisories are closed**. The BOM upgrade proposal and remaining decisions are recorded in the remediation report.
- users.email retains its unique constraint, named uq_users_email consistently with the reviewed PostgreSQL baseline. Only SQLState23505 for that constraint (or H2's corresponding backing index) maps to409; unrelated integrity failures remain server errors. No schema SQL is applied at startup or to the project database.
- Unexpected-error logging records exception class/method only, not message/cause, request body, cookies or query strings. Existing provider/database logging also needs redaction review before production.
