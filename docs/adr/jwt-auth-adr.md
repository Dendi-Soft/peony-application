# ADR-005: Session-to-JWT Authentication Cutover

**Status:** Accepted
**Date:** 2026-10-22
**Author:** Development Team
**Supersedes:** The session-based auth strategy in [ADR-003](./plant-schema-and-tiering-adr.md)
(decision 4, implemented in issue #12); ADR-003's schema, location, and tiering decisions
remain in force

---

## Context

The API authenticates with server-side sessions: `POST /api/v1/auth/signin` validates
credentials against `users.password_hash` and binds a Spring Security HTTP session,
protected routes require the `JSESSIONID` cookie, and every other auth failure returns
`{"error":"Authentication required"}` (401). The strategy was recorded in ADR-003
(decision 4) and implemented in issue #12.

The session model is a poor fit for the client being built against it:

- The React Native client (web + Android + iOS, separate repo) cannot treat the cookie
  uniformly: on web `JSESSIONID` is `HttpOnly` (invisible to JS), on native it requires
  cookie-jar handling, and any backend restart destroys the sessions, signing every user
  out.
- In-JVM sessions prevent running more than one replica.

The client's design contract (the UI design doc referenced in spec #17, §8 and §14 item 1)
targets a JWT flow: a 1 h access token plus a 30-day refresh token with rotation, sent as
`Authorization: Bearer`, with silent refresh on 401. Spec #17 records the agreed design
in full; this ADR records its decisions and the lineage it changes.

## Decision

1. **In-place cutover under `/api/v1`; no `/api/v2` prefix.** The app is not live and no
   client is deployed, so there are no coexisting clients to protect. The OpenAPI spec's
   `info.version` bumps **0.1.0 → 0.2.0** as the client-facing change signal ADR-004
   prescribes. **This is an explicit exception to ADR-004**, which otherwise requires a
   breaking change to be a new spec version with the old contract kept documented until
   its clients migrate: there is no client to migrate, so the in-place edit with a
   version bump is safe. The exception is conditional — once a client is deployed,
   breaking changes return to ADR-004's rule.
2. **Access token: JWT, HS256.** Claims `sub` (user id), `iat`, `exp`. TTL default
   **1 h** via `peony.jwt.access-token-ttl` (env `JWT_ACCESS_TOKEN_TTL`); the agreed
   1–5 h band is an environment concern — tuning within it is a config decision, not a
   code change.
3. **Refresh token: opaque, single-use, rotating.** An opaque 256-bit random value
   (Base64URL), TTL default **30 days** via `peony.jwt.refresh-token-ttl` (env
   `JWT_REFRESH_TOKEN_TTL`). Stored server-side in a new `refresh_tokens` table
   (Liquibase changeset 005) as a **SHA-256 hash**, never in cleartext. **Single-use
   rotation:** a refresh revokes the presented row and issues a fresh pair; an unknown,
   already-rotated, or expired refresh token returns 401. All refresh failures use the
   identical 401 envelope, so there is no oracle between failure kinds.
4. **Stateless auth.** A bearer `JwtAuthenticationFilter` runs per request: it parses
   `Authorization: Bearer`, verifies signature and expiry, loads the account, and sets
   the authenticated principal. Sessions are retired (`SessionCreationPolicy.STATELESS`;
   no `JSESSIONID` is issued anywhere), so the API is replica-safe and restarts no longer
   sign users out. The 401 envelope `{"error":"Authentication required"}` is unchanged.
5. **Signing secret from the environment, fail-fast.** HS256 signs with
   `peony.jwt.secret` (env `JWT_SECRET`); startup fails fast when the secret is unset
   outside the local/test profiles, which use a fixed dev secret.
6. **Public routes.** `/api/v1/auth/signup`, `/api/v1/auth/signin`,
   `/api/v1/auth/refresh` (new), `/actuator/health/**`, and the springdoc/swagger paths
   are public; everything else requires a valid bearer token. Signup (201) and signin
   (200) return one shared `AuthResponse { token, refreshToken, profile }`, and
   `POST /api/v1/auth/refresh` (body `{ refreshToken }`) returns a fresh `AuthResponse`.

## Rationale

- **One credential across RN platforms.** A bearer token in the JSON body and in the
  `Authorization` header works identically on web, Android, and iOS — no `Set-Cookie`
  parsing, no cookie jar, no `HttpOnly` asymmetry. The client's credential adapter flips
  in one place.
- **Stateless ⇒ replicas and restarts.** Session state in the JVM made single-replica
  deployment and restart-signs-everyone-out structural properties. Verifying a signed
  token per request moves that state to the client, so the server can run behind a load
  balancer and survive deployments.
- **In-place cutover (the ADR-004 exception).** Nothing is deployed, so a `/api/v2`
  prefix would protect nothing and keeping the 0.1.0 contract documented in the spec
  would be dead weight. Bumping `info.version` keeps ADR-004's client-facing signal:
  generated clients track the spec version they were built from.
- **HS256 is enough for a single service.** One service, one shared secret; asymmetric
  keys, key rotation, and external identity providers are out of scope for the MVP and
  can be revisited without changing the wire contract.
- **Opaque rotating refresh tokens, hashed at rest.** The server must know which refresh
  tokens are live for single-use rotation to work and for a sign-out/revocation endpoint
  to be addable later without re-architecture. Hashing at rest means a database leak does
  not hand out live sign-ins; single-use rotation means a captured token cannot be
  replayed for its full 30-day life. A stateless (JWT) refresh token would give up both.
- **One 401 envelope.** Missing, invalid, expired, and rotated credentials all return
  `{"error":"Authentication required"}` — one client code path for every auth failure,
  and no oracle against the server.

## Consequences

- **Hard cutover, no dual mode.** Nothing is deployed, so there is no window where both
  cookie and bearer must work; the client's credential adapter flips at the same moment.
  `JSESSIONID` stops being issued.
- **Swagger manual testing means pasting a bearer token.** The spec's `bearerAuth`
  scheme enables Swagger UI's authorize flow for that.
- **ADR-003's auth strategy (decision 4) and issue #12's session MVP are history**; the
  rest of ADR-003 (schema completion, location model, tiering) remains in force, and
  tier enforcement rides on the new bearer principal.
- **`refresh_tokens` rows accumulate until pruned.** With single-use rotation and a
  30-day cap the growth is bounded per active user (one live row per device); pruning is
  an operational note for later.
- **TTL tuning is a config decision.** The final production access-token TTL is chosen
  within the 1–5 h band via `JWT_ACCESS_TOKEN_TTL`; no code or client change is needed.
- **Deferred:** the sign-out/revocation endpoint (UI design doc §14 item 7) and
  refresh-reuse theft detection — the schema already supports both.

## Alternatives Considered

- **`/api/v2` prefix:** rejected — the app is not live and no client is deployed, so a
  second version prefix would protect nothing.
- **RS256 / asymmetric keys:** deferred — a single-service MVP shares one secret; the
  bearer wire contract stays the same if this changes later.
- **Stateless JWT refresh tokens:** rejected — the server-side `refresh_tokens` store is
  what enables single-use rotation and a future revocation endpoint.
- **Dual mode (cookie + bearer) during a transition:** rejected — there is no deployed
  client to transition, so dual mode would be dead weight on every request.

## References

- [Issue #17: Switch Auth from Server Sessions to JWT (spec)](https://github.com/Dendi-Soft/peony-application/issues/17)
- [ADR-004: API Versioning Strategy](./api-versioning-adr.md)
- [ADR-003: Plant Schema Completion and Free/Paid Tiering](./plant-schema-and-tiering-adr.md)
- [Issue #12: Implement Tiered Access Architecture](https://github.com/Dendi-Soft/peony-application/issues/12)
- [openapi/peony-api.yaml](../../openapi/peony-api.yaml)

---
