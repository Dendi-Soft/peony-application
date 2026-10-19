# Issue #7: Research: Open Plant Datasets & Licensing - **COMPLETED**

## Status
✅ **DONE** - Sources listed, data model defined, ingestion strategy set, gaps identified

**Completed:** 2026-10-19
**Scope note:** re-scoped 2026-10-19 (maintainer decision) — sources are *listed*, not
deeply classified; the PlantNet response ledger is *design-only*, with implementation
deferred to #9 (data-retention note, [ADR-001](../adr/plantnet-api-adr.md)).

---

## 🎯 Original Research Question
Where do we source **care instructions, toxicity data, and growth characteristics** for
the 50-plant MVP priority list (#9), under licenses that permit commercial use?

---

## ✅ Deliverables Completed

| # | Deliverable | Doc |
|---|-------------|-----|
| T0 | 50 common houseplant list (MVP priority list) | [7/01-priority-plant-list.md](7/01-priority-plant-list.md) |
| T1 | Sources listed, data model, recommendation | [7/02-sources-and-licensing.md](7/02-sources-and-licensing.md) |
| T2 | Completeness scores per source | [7/03-completeness-scores.md](7/03-completeness-scores.md) |
| T3 | Ingestion strategy into the MVP schema | [7/04-ingestion-strategy.md](7/04-ingestion-strategy.md) |
| T4 | Data gaps | [7/05-data-gaps.md](7/05-data-gaps.md) |

## The answer

**No open dataset carries care/toxicity/growth data for the 50-plant list at useful
coverage, and the only complete sources are non-commercial or absent.** So:

1. **Care / toxicity / growth** → **hand-curated table** of the 50 priority plants as
   the single source of truth: bounded (50 rows), one-time, zero licensing risk.
   Wikidata (CC0 data) cross-checks names/taxonomy only — it does not fill care fields.
2. **Recognition-corpus growth** → **persist unique PlantNet responses** in a
   `plantnet_responses` ledger (design in T1/T3; implementation in #9). PlantNet
   returns taxonomy + confidence only, so the ledger grows *what people photograph* —
   it never fills care/toxicity. Zero licensing risk (we store facts we fetch
   ourselves); consistent with ADR-002's no-image-retention stance.
3. **Excluded** → Planta (CC-BY-NC, no care fields) and iNaturalist (NC, observations
   rather than care data); PlantNet as a *data* source (recognition only — settled in
   #6 / [ADR-002](../adr/plantnet-api-cache-removal-adr.md)).

**Data model:** `plants` table (12 curated fields per the #9 schema + `family`) and the
`plantnet_responses` ledger table — see [7/02-sources-and-licensing.md](7/02-sources-and-licensing.md) §2.

---

## ⏭️ Follow-ups for #9

- Curate the 50 rows (fix species for the 14 genus-level entries — T4), attach a
  reference to every toxicity boolean.
- Build the seed file + API seed script (T3 Path 1).
- Implement the `plantnet_responses` ledger upsert in the recognize flow (T3 Path 2).
- Decide the boolean-vs-qualified toxicity schema question (T4).

## 📚 References

- Issue #7: <https://github.com/Dendi-Soft/peony-application/issues/7>
- Issue #9 (consumer): <https://github.com/Dendi-Soft/peony-application/issues/9>
- [ADR-001: PlantNet API Integration](../adr/plantnet-api-adr.md)
- [ADR-002: PlantNet v2 Rewrite and Cache Removal](../adr/plantnet-api-cache-removal-adr.md)
- CC0 / CC-BY-SA 4.0 / CC-BY-NC 4.0 license pages: listed in T1
