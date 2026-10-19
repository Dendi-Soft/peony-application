# T3 — Ingestion strategy into the MVP schema

Companion doc for spec #7. How data gets into the MVP schema
([02-sources-and-licensing.md](02-sources-and-licensing.md) §2) for the 50-plant
list ([01-priority-plant-list.md](01-priority-plant-list.md)). Two paths:

1. **Curated seed** — the source of truth for care/toxicity/growth (T1
   recommendation).
2. **PlantNet response ledger** — grows the recognition corpus (design-only;
   implementation is #9, which carries the data-retention note from
   [ADR-001](../../adr/plantnet-api-adr.md)).

## Path 1 — Curated seed (50 plants)

- **Artifact:** a version-controlled seed file (JSON or YAML) of 50 rows in the
  MVP schema shape, plus an **API seed script** that loads it into `plants`
  (both are #9 deliverables).
- **Flow:** curate 50 rows from trusted horticultural references (T2 shows care
  fields come from no open source) → validate → seed script upserts into
  `plants` → the plant API serves them.
- **Field mapping:** 1:1 with the T1 `plants` table — curation produces exactly
  the 12 scored fields (common name, scientific name, family, 4 care fields, 2
  toxicity booleans, 3 growth fields); `plantId` is assigned on load.
- **Validation:** all 12 content fields non-empty; `scientific_name` unique;
  names/taxonomy cross-checked against Wikidata (CC0); toxicity as booleans
  (mixed/unknown cases are gaps — see T4).
- **Idempotent:** upsert keyed on `scientific_name`; the seed script can be
  re-run on any environment.

## Path 2 — PlantNet response ledger (design-only)

- **Trigger:** after a successful recognize call.
- **Key:** normalized scientific name = `scientificNameWithoutAuthor`
  (glossary: derived by splitting the full name at the last space), casefolded.
- **Upsert:** `response_count` += 1, `avg_confidence` running average,
  `first_seen`/`last_seen` maintained, `common_name` from the hit.
- **Top hit only:** one ledger upsert per response (the best-confidence hit);
  multi-hit bookkeeping is out of scope.
- **Non-fatal:** a ledger write failure must never fail the recognize response —
  catch, log, continue.
- **No photos stored:** taxonomy + confidence only, consistent with
  [ADR-002](../../adr/plantnet-api-cache-removal-adr.md) (no response cache,
  no image retention).
- **Scope:** design-only per the maintainer decision (2026-10-19). #9
  implements it alongside the seed script.

## What #9 takes from this

- Seed file format + seed script (its "API seed script" deliverable).
- Ledger table + upsert (its data-retention note).
- The validation checklist from Path 1.

## Sources

- T0 list: [01-priority-plant-list.md](01-priority-plant-list.md)
- T1 sources & data model: [02-sources-and-licensing.md](02-sources-and-licensing.md)
- T2 completeness: [03-completeness-scores.md](03-completeness-scores.md)
