# T4 — Data gaps

Companion doc for spec #7. Gaps in common houseplant information for the
50-plant list ([01-priority-plant-list.md](01-priority-plant-list.md)), fed by
[T2](03-completeness-scores.md). Category-level on purpose: #9's curation fills
these in.

## Systemic gaps (affect all 50 plants)

| Gap | Detail |
|-----|--------|
| Care fields | light/water/humidity/temperature exist in **no open source** (T2) — all 50 rows must be curated from horticultural references. |
| Difficulty + mature size | curation-only; mature size in particular is often unknown or variable for potted houseplants — record a range or "varies" rather than a false precision. |

## Per-entry gaps (affect specific rows)

- **Genus-level entries** need a species choice before care/toxicity can be
  written: Calathea (#10), Philodendron (#15), Hoya (#16), Tillandsia (#17),
  Anthurium (#21), Echeveria (#28), Haworthia (#29), Opuntia (#30), Yucca
  (#39), Dieffenbachia (#42), Aglaonema (#43), Tradescantia (#44), Begonia
  (#45), Alocasia (#48). Care and toxicity differ between species within these
  genera — the curation should fix the most commonly grown species and note
  it.
- **Toxicity qualifiers** — the MVP schema uses booleans
  (`toxic_to_pets`, `toxic_to_humans`), but real toxicity is often
  part-specific (sap vs. leaf), dose-dependent, or "mildly toxic". Flag such
  rows during curation; a qualified field (or a `notes` column) is a candidate
  schema refinement for #9.
- **Toxicity verification** — Wikidata/Wikipedia carry only sparse toxicity
  data (T2); every toxicity boolean needs a citable reference per plant, which
  is the bulk of the verification effort.

## Coverage gaps (ledger, not curation)

- **Cold start** — the PlantNet ledger (T1/T3) is empty at launch; species
  users never photograph never appear. This affects popularity signals only;
  day-one care coverage comes from the curated 50.
- **Misidentification** — ledger rows are statistics, not ground truth
  (T3); low-confidence hits can pollute `avg_confidence` for a species.

## What #9 should do about these

1. Fix species for every genus-level entry (curation step).
2. Attach a source reference to each toxicity boolean.
3. Decide the boolean-vs-qualified toxicity schema question.
4. Treat ledger data as signals (frequency, popularity), never as care data.

## Sources

- T0 list: [01-priority-plant-list.md](01-priority-plant-list.md)
- T1 data model: [02-sources-and-licensing.md](02-sources-and-licensing.md)
- T2 completeness: [03-completeness-scores.md](03-completeness-scores.md)
- T3 ingestion & ledger: [04-ingestion-strategy.md](04-ingestion-strategy.md)
