# ADR-007: Catalogue Source and Scan-Created Entries

**Status:** Accepted
**Date:** 2026-11-02
**Author:** Development Team
**Related:** [ADR-003](./plant-schema-and-tiering-adr.md) (plant schema),
[ADR-006](./user-plant-individuals-adr.md) (user plants as individuals)

---

## Context

Scan-based recognition (#38) lets a user add a plant without typing its details. PlantNet
supplies identity and taxonomy only: scientific name, common names, genus, family, and
GBIF/POWO identifiers. It supplies no care data: no watering interval, light, humidity,
temperature, toxicity, or description.

The catalogue therefore needs rows that carry identity and unknown care, and it needs to
record where each row came from. A scan-created row must not be mistaken for one a user
registered manually or one supplied by the curated dataset (#9), and a later scan of the
same species must not overwrite curated or user-entered values.

## Decision

1. **Every catalogue row records its creation origin.** `plants.source` is a nullable
   `TEXT` column with the wire values `PLANTNET_SCAN`, `MANUAL`, and `SEED`. Existing rows
   are backfilled as `MANUAL` because they predate scan-created entries. The column is
   exposed read-only on `PlantResponse` (and therefore on `UserPlantResponse`) to both
   tiers so the UI can show attribution. The column stays nullable for rows created
   outside the application paths; application writes always set it.
2. **Identity fields may be unknown.** `plants.name` (common name) and
   `plants.watering_frequency` become nullable. `plants.description` remains `NOT NULL`;
   scan-created rows store `""`. A scan-created row therefore holds
   `scientific_name`, optional `name`/`family`, `source = PLANTNET_SCAN`, no care data,
   and `description = ""`.
3. **Classification is applied before tier shaping.** Recognition drafts
   (`catalogDraft`) are built from the full PlantNet payload before `RecognitionTierShaper`
   reduces the free-tier result, because the free shape drops the species' family. A draft
   is attached only to hits scoring at least `0.30` (inclusive). Free tier attaches the top
   hit's draft (none below the threshold); premium attaches a draft to every qualifying
   hit while still returning lower-scoring hits without one. The scientific name in a draft
   is the binomial: the first two whitespace tokens of PlantNet's author-free name when
   present, otherwise of the full name. Infraspecific names such as
   `Caladium bicolor subsp. bicolor` therefore reduce to `Caladium bicolor`, and a chosen
   name with fewer than two tokens yields no draft.
4. **Find-or-create is a single-statement upsert.** `POST /api/v1/plants/from-scan`
   inserts with `INSERT ... ON CONFLICT (scientific_name) DO UPDATE`, filling only blank
   `name` and `family` from the incoming scan and never touching existing values, care
   fields, or `source`, then reads the row back. A concurrent scan that loses the insert
   race takes the update path on the unique key instead of aborting its transaction, so a
   duplicate scientific name never surfaces as a 500 on the scan path.
5. **The catalogue row and the individual share one transaction.** From-scan resolves the
   caller-owned room and parses the optional last-watered date before saving the
   individual; a missing/foreign room or malformed date rolls the catalogue upsert back.
   A supplied `lastWateredAt` produces a `nextWaterDueAt` when the reused catalogue row has
   a watering frequency; for a newly scan-created row with unknown care, it is stored while
   `nextWaterDueAt` stays absent.
6. **Manual registration is relaxed but keeps its semantics.** `POST /api/v1/plants`
   requires only `scientificName`; `commonName` and `waterFrequency` are optional. A new
   row records `source = MANUAL`. An existing row is reused without filling blanks or
   changing its source, so manual registration cannot reclassify a scan or seed row. Manual
   registration keeps its find-then-insert shape, so a concurrent duplicate can still hit
   the unique constraint; that `DataIntegrityViolationException` is mapped to HTTP 409
   (rather than a 500) with the standard error body.

## Rationale

- **Creation origin is not the same as current content.** A scan-created row later
  enriched by the curated dataset should still be attributable to the scan that first
  created it. Recording only the origin keeps attribution stable and avoids a mutable
  "last writer" field.
- **Nullable identity and care fields are the honest schema.** PlantNet genuinely cannot
  supply a common name or watering interval; storing sentinel values such as `""` or `0`
  would make unknown indistinguishable from known.
- **Drafts are stateless.** Confidence and recognition history belong to the future
  recognition ledger (#9's `plantnet_responses`), not to the catalogue. The draft is a
  client convenience derived per response.
- **The upsert is safer than retry plumbing.** Catching a unique violation inside the same
  PostgreSQL transaction leaves it aborted; a single `ON CONFLICT` statement is atomic and
  needs no `REQUIRES_NEW` boundary or retry loop.
- **One transaction protects the user-visible operation.** A scan that cannot place its
  individual must not leave a stray catalogue row behind.

## Consequences

- Existing clients see `commonName`/`waterFrequency` omitted when unknown instead of
  always present; the generated models omit null fields. The contract version moves to
  `0.4.0`.
- New catalogue rows from scans have no care schedule until curated data arrives; their
  individuals may have `lastWateredAt` but no `nextWaterDueAt`.
- The free tier's `catalogDraft.family` can be present even though `species.family` is
  omitted from the free shape; they are separate fields.

## Coordination with #9 (curated seed)

The curated dataset is still owned by #9 and is **not** implemented here. When #9 seeds
or enriches catalogue rows:

1. It **upserts curated care fields over scan-created rows** (watering frequency, light,
   humidity, temperature, toxicity, growth, description) instead of skipping rows that
   already exist.
2. It **leaves `plants.source` as the creation origin**. A row first created by a scan
   stays `PLANTNET_SCAN` even after curation; seeding must not rewrite it to `SEED`.
3. When it sets or changes `watering_frequency`, it **backfills `next_water_due_at`** for
   individuals that have a `last_watered_at` and a null `next_water_due_at`, using
   `last_watered_at + watering_frequency`. Individuals without a last-watered date stay
   null ("history unknown"). This is the only write that makes scan-created individuals
   schedulable.

## References

- [ADR-003: Plant Schema Completion and Free/Paid Tiering](./plant-schema-and-tiering-adr.md)
- [ADR-006: User Plants as Individuals](./user-plant-individuals-adr.md)
- [API tier contracts](../api-tier-contracts.md)
- [Issue #38: Create catalogue entries from PlantNet scan results](https://github.com/Dendi-Soft/peony-application/issues/38)
