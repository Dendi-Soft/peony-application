# ADR-004: API Versioning Strategy

**Status:** Accepted
**Date:** 2026-10-19
**Author:** Development Team

---

## Context

Issue #13 rescopes the API contract work: `openapi/peony-api.yaml` becomes the
single source of truth for the wire contract, and the Gradle build generates the
shared DTOs from it. The spec already pins endpoint families beyond the one that
currently ships — the recognition endpoint is live, while the plants CRUD, location,
and auth endpoints are documented so #10 and #12 build to the same contract.

A versioning policy has to exist before the first un-shipped endpoint family lands,
because the URL scheme chosen now constrains every client that follows. The
candidate options are: (a) version in the URL from day one (`/api/v1/...`),
(b) no version prefix now, introducing one later, or (c) version via headers or
content negotiation.

## Decision

1. **No URL version prefix now.** While the API is a single endpoint family
   (recognition only), endpoints stay at their current unversioned paths (e.g.
   `/api/plantrecognition/recognize`).
2. **`/api/v1/` is introduced when the plants family ships** (issue #10). At that
   migration, the entire API moves under the `v1` prefix in one change; there is no
   period where some paths carry a prefix and others do not.
3. **The spec is the single migration vehicle.** `openapi/peony-api.yaml` is the only
   place versioning is declared or changed: the `servers` URL, the path prefixes, and
   the DTO generation all follow it. No per-endpoint version annotations, no header-
   based negotiation, no parallel v1/v2 controllers. A breaking change is a new
   version in the spec; the old version stays documented until its clients migrate.

## Rationale

- **One endpoint family does not justify a version prefix.** A prefix is a signal for
  coexisting incompatible contracts; with recognition as the only family there is
  nothing to distinguish, and `/api/v1/` would be dead weight on every request and
  every client.
- **Introduce the prefix at the first real branching point.** The plants family is
  where the API gains a second, independently evolving endpoint group. Moving the
  whole API under `/api/v1/` in one change keeps the scheme uniform, is a one-line
  spec edit, and gives future v2 work a clean prefix to work from.
- **Spec as the single vehicle.** The DTOs are generated from the spec, so the spec
  already sits between the wire contract and the code. Making it the only place
  versioning lives means a version change cannot be made in code without making it
  in the spec first, and the generated models always reflect the versioned contract.
  Header-based negotiation (option c) would hide the version in a per-request
  mechanism that the spec cannot express at the path level and that clients and
  proxies would each have to special-case.

## Consequences

- The recognition endpoint keeps its current unversioned path until the plants
  family ships; the RN client and any existing integrations are unaffected now.
- #10 must implement the plants/locations endpoints under the `v1` prefix as part of
  that family's migration, moving recognition at the same time.
- A breaking change to a shipped endpoint is a spec-level version bump, not an in-
  place edit: the old contract stays in the spec (documented as the previous
  version) until its clients are migrated.
- Clients cannot rely on the absence of a prefix as a version signal; they should
  track the `info.version` of the spec they generated from.

## References

- [openapi/peony-api.yaml](../../openapi/peony-api.yaml)
- [ADR-002: PlantNet v2 Rewrite and Cache Removal](./plantnet-api-cache-removal-adr.md)
- [ADR-003: Plant Schema Completion and Free/Paid Tiering](./plant-schema-and-tiering-adr.md)
- [API tier contracts](../api-tier-contracts.md)
- [Issue #13: OpenAPI contract and DTO generation](https://github.com/Dendi-Soft/peony-application/issues/13)
- [Issue #10: MVP plant API endpoints](https://github.com/Dendi-Soft/peony-application/issues/10)

---
