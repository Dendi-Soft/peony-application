# T2 — Data completeness per source

Companion doc for spec #7. Scores how completely each source from
[02-sources-and-licensing.md](02-sources-and-licensing.md) fills the MVP fields
for the 50-plant list in [01-priority-plant-list.md](01-priority-plant-list.md).

Per the maintainer decision (2026-10-19) this is a **quick pass**: completeness is
estimated from what each source is known to carry, not from scraping every plant.

## MVP fields scored

From the #9 schema: common name, scientific name, family, light needs, water
frequency, humidity, temperature range, toxic to pets, toxic to humans,
difficulty, native region, mature size — **12 fields**.

## Completeness

| Source | Coverage of 50 plants | Fields filled | Est. completeness |
|--------|----------------------|---------------|-------------------|
| Manual curation | 50/50 (we write them) | all 12 | **100%** — by construction |
| Wikidata / Wikipedia | most common species have an item | name, family, sometimes native region, occasional toxicity; **care fields mostly absent** | ~25–40% |
| Planta dataset | labeled images for a subset of species | images only — **no care/toxicity/growth fields** | ~0% for care data |
| PlantNet (ledger) | only species users have photographed so far | name + confidence; **no care/toxicity** | ~0% for care data; grows over time |
| iNaturalist | observations for many species | observations, not structured care fields | ~0% for care data |

## Read-out

- **No open source carries the care/toxicity/growth fields** for the 50-plant
  list at useful coverage. Wikidata gives names/taxonomy and occasional toxicity,
  but light/water/humidity/temperature/difficulty/mature-size are largely absent.
- That is exactly why T1 recommends **manual curation** as the source of truth:
  it is the only path to 100% on the 12 fields, and it is bounded (50 rows).
- Wikidata's real value here is a **cross-check** on names/taxonomy for the
  curated rows, not a care-data source.
- The PlantNet ledger (T1) is complementary: it grows recognition coverage and
  popularity signals, and will never fill the care fields.

## Gaps to watch (fed to T4)

- Care fields (light/water/humidity/temp) — absent from every open source; must
  be curated.
- Toxicity — partially available via Wikidata/Wikipedia; needs per-plant
  verification, especially for the Calathea/Philodendron/Hoya/Echeveria
  genus-level entries.
- Mature size + difficulty — essentially curation-only.

## Sources

- T0 plant list: [01-priority-plant-list.md](01-priority-plant-list.md)
- T1 sources & licensing: [02-sources-and-licensing.md](02-sources-and-licensing.md)
