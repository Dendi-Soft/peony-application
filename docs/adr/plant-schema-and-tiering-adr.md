# ADR-003: Plant Schema Completion and Free/Paid Tiering

**Status:** Accepted
**Date:** 2026-10-19
**Author:** Development Team

---

## Context

The research in issue #8 established the MVP plant entity, but the initial schema
(`001-init-tables`) only provides `name`, `scientific_name`, `description`, and
`watering_frequency` on `plants`. The fields #8 required and #9's Phase 2 field list
relies on — tags, the remaining care fields, toxicity, and growth — are missing, and
the curated dataset (#9) needs a stable target schema to load against without remapping.

The initial schema also carries a defect: `watering_frequency` is marked `unique`, so
two plants with the same watering interval would collide.

Two of the original research questions — the location model and the auth strategy —
were settled downstream (#10 Phase 2 and #12 respectively). Issue #8 is the research
issue that owns them, so their settled shape is recorded here.

Finally, the free vs paid tier API contract (which plant fields each tier returns)
was undocumented, and both #10 (endpoint responses) and #12 (enforcement) depend on it.

## Decision

1. **Additive schema completion.** A new changeset
   `src/main/resources/db/changelog/changes/002-complete-plants-schema.yaml` (included
   in `changelog-master.yaml`; 001 is not rewritten, no renames) adds nullable columns
   to `plants`: `family`, `tags` (`TEXT[]` with a GIN index `idx_plants_tags`),
   `light_needs`, `humidity`, `temp_range`, `toxic_to_pets` (`BOOLEAN`),
   `toxic_to_humans` (`BOOLEAN`), `difficulty`, `native_region`, `mature_size`.
   Text fields use `TEXT` per the #7 T1 data model; the new columns are nullable
   because the dataset may have per-plant gaps.
2. **Defect fix.** The same changeset drops the `unique` constraint on
   `plants.watering_frequency` (constraint name `plants_watering_frequency_key`, the
   name 001 generates — Liquibase 5's `dropUniqueConstraint` requires it explicitly).
   `watering_frequency` stays `INTEGER` (days between waterings).
3. **Location model** (recorded; implemented in #10 Phase 2): nested house → room
   hierarchy, with a plant assignable to multiple locations (a plant↔room association,
   unique pair).
4. **Auth strategy** (recorded; implemented in #12): session-based MVP authentication
   with `password_hash` on the `users` table.
5. **Tier field visibility.** Free tier: identity (commonName, scientificName, family)
   + tags + lightNeeds + waterFrequency + toPets + toHumans. Paid tier: everything the
   free tier returns + humidity + tempRange + all growth fields (difficulty,
   nativeRegion, matureSize) + multi-device sync + advanced analytics. The free tier
   also gets 5 photo uploads per month and basic recognition results (enforced by
   #12). The per-endpoint contract lives in
   [docs/api-tier-contracts.md](../api-tier-contracts.md).

## Rationale

- **Additive and nullable.** #9's dataset may have gaps per plant, so new columns are
  nullable; existing rows and deployments keep working. No renames means 001's history
  stays valid, and the 001 → master upgrade path is what existing installations will
  run — it is pinned by `PlantsSchemaMigrationTest`.
- **`TEXT` over `VARCHAR`.** Follows the #7 T1 data model for the plant fields.
- **GIN index on `tags`.** Secondary search over a list (typahead for "low light",
  "pet friendly") needs an array-friendly index; `TEXT[]` + GIN is the PostgreSQL
  standard for that.
- **Recording location and auth here.** The implementations live in #10 and #12, but
  #8 is the research issue that posed the questions; recording the settled shape now
  prevents re-litigating them in the implementation tickets.
- **The tier split.** The free tier returns enough care information to decide whether
  a plant is keepable (basic light, water, toxicity); the paid tier returns the full
  care profile, the growth fields, and the sync/analytics conveniences.

## Consequences

- **#10** builds the plant endpoints and DTOs against the completed 16-column
  `plants` table and filters responses by tier per the contract doc.
- **#12** must not modify the `plants` table; it owns the users tier column
  (changeset `003-users-tier`) and the enforcement (401 on protected routes, monthly
  upload counter, capability flags).
- Environments already at the initial schema pick up changeset 002 on next startup.
- The dropped constraint's name is referenced in 002; hand-editing the 001-generated
  constraint in an environment would make 002 fail — migrations remain the single
  source of truth for schema.

## References

- [API tier contracts](../api-tier-contracts.md)
- [ADR-002: PlantNet v2 Rewrite and Cache Removal](./plantnet-api-cache-removal-adr.md)
- [Issue #8: Research — MVP Plant Database Schema Design](https://github.com/Dendi-Soft/peony-application/issues/8)
- [Issue #9: dataset curation (field values)](https://github.com/Dendi-Soft/peony-application/issues/9)
- [Issue #10: MVP plant API endpoints](https://github.com/Dendi-Soft/peony-application/issues/10)
- [Issue #12: tiered access](https://github.com/Dendi-Soft/peony-application/issues/12)

---
