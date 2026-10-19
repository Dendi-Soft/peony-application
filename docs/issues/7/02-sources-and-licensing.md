# T1 — Sources, data model & recommendation

Companion doc for spec #7 (research: open plant datasets & licensing). Per the
maintainer decision (2026-10-19): sources are **listed, not deeply classified**,
and the PlantNet response ledger is **design-only** — its implementation is
deferred to #9 (data-retention note, ADR-001).

## 1. Sources

| Source | Provides | License | Commercial OK? |
|--------|----------|---------|----------------|
| Manual curation | Whatever we author: care, toxicity, growth | none (our own work) | yes |
| Wikidata / Wikipedia | Structured facts per species: taxonomy strong, toxicity sparse, care almost absent | data: CC0; text: CC-BY-SA 4.0 | yes (data is CC0; text needs attribution) |
| PlantNet API & dataset | taxonomy + confidence only — **no** care/toxicity/growth | n/a — used as recognition API, not a data source | excluded (settled in #6 / ADR-002) |
| Planta dataset | pre-labeled recognition images | CC-BY-NC 4.0 (per #7) | no — non-commercial clause |
| iNaturalist API | community-tagged observations | CC-BY-NC 4.0 (per #7) | no — non-commercial clause |

License references:

- CC0: <https://creativecommons.org/publicdomain/zero/1.0/>
- CC-BY-SA 4.0: <https://creativecommons.org/licenses/by-sa/4.0/>
- CC-BY-NC 4.0: <https://creativecommons.org/licenses/by-nc/4.0/>

License claims for Planta and iNaturalist come from the scope note in issue #7's
body; the PlantNet and iNaturalist rows are marked excluded per the same note.
The Wikidata/Wikipedia split (data CC0, text CC-BY-SA 4.0) is Wikimedia's standard
licensing.

## 2. Data model

### `plants` — curated MVP table (source of truth)

From the #9 schema, plus `family` from #7's critical data fields:

| Column | Type | Maps to |
|--------|------|---------|
| plant_id | PK | plantId |
| common_name | text | commonName |
| scientific_name | text, unique | scientificName — join key to the ledger |
| family | text | family |
| light_needs | text | careInfo.lightNeeds |
| water_frequency | text | careInfo.waterFrequency |
| humidity | text | careInfo.humidity |
| temp_range | text | careInfo.tempRange |
| toxic_to_pets | boolean | toxicity.toPets |
| toxic_to_humans | boolean | toxicity.toHumans |
| difficulty | text | growth.difficulty |
| native_region | text | growth.nativeRegion |
| mature_size | text | growth.matureSize |

### `plantnet_responses` — recognition ledger (design-only)

| Column | Type | Notes |
|--------|------|-------|
| scientific_name | text, PK | normalized: no authorship, casefolded |
| common_name | text | as reported by PlantNet |
| response_count | bigint | recognitions matched this species |
| avg_confidence | numeric | running average of hit confidence |
| first_seen | timestamp | |
| last_seen | timestamp | |

Upserted per unique response in the identify flow. **Design-only**: PlantNet
returns taxonomy + confidence only, so this ledger grows the *recognition
corpus* (what people photograph, how often, at what confidence). It never
populates `plants` care/toxicity/growth. Implementation is #9's job.

## 3. Recommendation

- **Care / toxicity / growth** → hand-curated table of the 50 priority plants
  ([01-priority-plant-list.md](01-priority-plant-list.md)) as the single source
  of truth: bounded (50 rows), one-time, zero licensing risk. Cross-check names
  and taxonomy against Wikidata (CC0).
- **Recognition-corpus growth** → persist unique PlantNet responses in the
  `plantnet_responses` ledger above: zero licensing risk (we store facts we
  fetch ourselves) and it matches the data-retention purpose flagged in
  ADR-001 / #9. Caveats: taxonomy + confidence only (never fills
  care/toxicity); confidence is a statistic, not ground truth; dedupe on
  normalized scientific name; cold-start — the curated list guarantees
  day-one coverage of the 50.
- **Excluded** → Planta (CC-BY-NC, no care fields) and iNaturalist (NC,
  observations rather than care data).

## Sources

- Issue #7 — scope and license notes: <https://github.com/Dendi-Soft/peony-application/issues/7>
- Issue #9 — MVP schema: <https://github.com/Dendi-Soft/peony-application/issues/9>
- CC0 / CC-BY-SA 4.0 / CC-BY-NC 4.0 canonical license pages (above)
