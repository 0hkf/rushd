# Authentication refactor verification — 6 October 2026

- Prerequisite audit: [auth-audit.md](auth-audit.md); one old JWT implementation identified, plus frontend/config/tests/script consumers.
- Backend: mvn clean test completed successfully; 90 tests, zero failures/errors/skips. Existing property/location/sale/rental/admin rules still covered.
- Frontend: npm test completed successfully; 6 tests covering single-flight renewal, failed refresh, login exclusions, retry limits, late 401 and logout coordination.
- npm run build and npm run lint succeeded. git diff --check succeeded.
- Actual Chrome + Spring Boot HTTP test succeeded on isolated ports 5174/18080 with an ephemeral in-memory H2 database, not existing PostgreSQL:
  cookie login, HttpOnly attributes, no localStorage/sessionStorage credentials, five concurrent /me calls sharing one refresh, refresh rotation, reload restoration, server logout, rejected revoked refresh.
- Temporary test servers were stopped. Main workspace/dev data untouched; no admin account seeded.
- SQL migration [20261006-auth-token-lifecycle.sql](../backend/db/20261006-auth-token-lifecycle.sql) added only, not applied to a real database.
- No commit, merge or push performed.
- Deployment-specific PostgreSQL constraints/locking, TLS, proxy forwarding and frontend-host CSP remain deployment verification requirements. H2 concurrency test does not certify every PostgreSQL isolation configuration.
- Current limits: access JWT copies remain valid until expiry; refresh-family revocation is immediate. Single-flight is per SPA client, not cross-tab. Local rate limiting is per process; multi-instance/proxy deployments need trusted shared edge limits. ADMIN MFA is not implemented.
See [api-auth.md](api-auth.md) for configuration, API contract and rollout requirements.
