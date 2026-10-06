# Authentication audit — 6 October 2026 (before refactor)
Repository-wide source/config/test/script/doc search performed before implementation.
- One JWT implementation: JwtService, HS256; @Value jwt.secret / jwt.expiration.
- Main and test YAML independently hardcode a 24-hour TTL. Main includes a development fallback signing secret; short keys are padded.
- JwtAuthenticationFilter accepts Bearer headers; signature/expiry/subject checked, no token-type check. User authorities loaded from DB.
- AuthService manually verifies BCrypt; login returns raw JWT JSON. No refresh persistence, refresh endpoint, cookie delivery or server logout.
- SecurityConfig stateless, CSRF disabled, CORS hardcoded localhost:5173, Spring default security headers.
- ADMIN method/service checks protect property writes. Public signup restricted BUYER; legacy SELLER has no publishing privileges.
- One Axios client; AuthProvider/api.js use sessionStorage and Bearer headers. Navbar logout only clears browser state.
- Existing MockMvc tests generate JWTs and send Bearer headers. scripts/test-auth.sh expects raw login JSON and obsolete privileged registration.
- PostgreSQL ddl-auto:none, no migration runner; manual SQL artifacts convention. Tests use H2 create-drop.
- No other active token implementation or localStorage auth found. No rate limiter, MFA, refresh revocation or session family.
Existing uncommitted business/design changes are preserved. New cookie contract intentionally requires one-time re-login; no compatibility path for old browser-storage JWTs.
