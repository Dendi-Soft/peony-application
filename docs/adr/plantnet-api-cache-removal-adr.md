# ADR-002: PlantNet v2 Rewrite and Cache Removal

**Status:** Accepted
**Date:** 2026-10-19
**Author:** Development Team
**Supersedes:** The three-layer caching design in [ADR-001](./plantnet-api-adr.md) (the PlantNet API selection itself remains valid)

---

## Context

The original issue #6 implementation exposed three endpoints (`/recognize`, `/species/{taxonId}`,
`/search`) backed by a three-layer cache (Caffeine → PostgreSQL → PlantNet API). During the
follow-up rewrite of the recognition flow around the PlantNet v2 identify endpoint
(`my-api.plantnet.org/v2/identify/all`), the client was rebuilt and the species/search
endpoints along with the cache were dropped.

This ADR records why the current design keeps a single recognition endpoint with no database
cache.

## Decision

1. **Single API surface:** `POST /api/plantrecognition/recognize` is the only plant recognition
   endpoint. It accepts a multipart image file or a base64-encoded image and returns the
   PlantNet predictions (best match first) with confidence scores, species details, and external
   taxon IDs.

2. **No response/species cache:** the `PlantNetCacheService`, the `plantnet_species`,
   `plantnet_families`, and `plantnet_search_cache` tables, and the related entities and
   repositories are removed.

3. **Rate-limit mitigation via retries:** instead of caching, the client retries 429/503
   responses and network failures with exponential backoff (`plantnet.retry-attempts`,
   `plantnet.retry-base-delay-ms`, delay doubling per attempt, capped at 30 s).

## Rationale

- **Recognition is not cacheable in practice.** Every request carries a different image, so a
  response cache has a near-zero hit rate for the recognize flow. The cache earned its keep only
  for the species/search endpoints, which served repeated lookups by taxon ID/name.
- **Fewer moving parts.** Removing the cache deletes two database tables, three JPA entities,
  three repositories, a Caffeine dependency, and a Redis option - less state to migrate, test,
  and operate for an MVP.
- **The free tier covers MVP volume.** 5 requests/minute is ~43,000 requests per month;
  exponential-backoff retries absorb short bursts, and a paid API key removes the limit when
  needed.
- **Provider swap stays cheap.** All PlantNet specifics live in `PlantNetClient` behind one
  `recognize(RecognitionRequest)` call, so moving to PlantNet PRO, self-hosted models, or
  another taxonomic API does not touch the controller.

## Consequences

**Positive**
- Simpler schema and startup (no PlantNet tables to migrate).
- One endpoint to document, secure, and test; Swagger UI reflects the actual surface.
- Retry behavior is configurable and unit-tested against a mock server.

**Negative / accepted trade-offs**
- Repeated recognitions of the same image pay the API call cost again.
- Sustained load above the free-tier rate limit surfaces as 429 responses even after retries;
  the fix is an API key / paid plan, not a cache.
- Environments that already applied the old `002-create-plantnet-species-tables` migration keep
  three unused tables (the develop-branch migration was removed; production rollouts should drop
  the tables manually when convenient).

## Alternatives Considered

- **Keep the three-layer cache with only `/recognize`:** rejected - no realistic hit rate for
  unique images, and the species/search endpoints that justified it are gone.
- **Restore `/species` and `/search` against the v2 API:** deferred - not needed for the current
  product scope; the client design allows re-adding them behind the same HTTP boundary if a
  use case appears.
- **Add a local Caffeine response cache keyed by image hash:** possible later if the same
  images are genuinely re-submitted; not worth the complexity today.

---

## References

- [ADR-001: PlantNet API Integration](./plantnet-api-adr.md)
- [Issue #6 completion record](../issues/6-IMPLEMENTATION_COMPLETE.md)
- [Current implementation summary](../plantnet-improvements.md)
