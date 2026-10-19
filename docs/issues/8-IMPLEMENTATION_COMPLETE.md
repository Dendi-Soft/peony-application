# Issue #8: Research: MVP Plant Database Schema Design - **COMPLETED**

## Status

✅ **DONE** - Plant entity completed (care/toxicity/growth/tags, `watering_frequency`
defect fixed), free/paid tier contracts documented, and the spec's full task graph
implemented: tiered access (#12), OpenAPI contract source (#13), MVP plant API (#10)

**Completed:** 2026-10-19
**Branch:** `feature/8` (PR #16 → develop)

> **Scope note (2026-10-19):** the base schema (`users`, `plants`, `user_plants`) and two
> downstream decisions (location model, auth strategy) were already settled; this work
> closes the remaining gap between the current `plants` table and the fields the original
> research required, and ships the API surface the schema exists to serve.

---

## 🎯 Original Research Question

What remains to complete the MVP plant entity schema, and what are the free vs paid tier
API contracts?

---

## ✅ Deliverables Completed

| # | Deliverable | Where |
|---|-------------|-------|
| T8 | Liquibase changeset completing the plant entity (family, tags, care, toxicity, growth) and dropping the `watering_frequency` unique defect | `src/main/resources/db/changelog/changes/002-complete-plants-schema.yaml` |
| T8 | Free/paid tier API contract (per-endpoint field visibility) | [api-tier-contracts.md](../api-tier-contracts.md) |
| T8 | ADR-003: schema completion, tier split, location model, auth strategy | [ADR-003](../adr/plant-schema-and-tiering-adr.md) |
| T12 | Session auth (signup/signin/profile/upgrade), tier access control, 5-uploads/month free-tier rate limiting, capability flags | `src/main/java/com/dendi/peony/` (auth, subscription, tier shaping) + changeset `003-users-tier.yaml` |
| T13 | OpenAPI 3.x spec as the single contract source + Gradle `org.openapi.generator` DTO generation (models only) | [openapi/peony-api.yaml](../../openapi/peony-api.yaml) |
| T13 | ADR-004: API versioning strategy (`/api/v1` introduced when the plants family ships) | [ADR-004](../adr/api-versioning-adr.md) |
| T10 | MVP plant API: CRUD + typahead, nested house/room locations, image serving — and the ADR-004 whole-API `/api/v1` migration | `src/main/java/com/dendi/peony/controller/plant/` + changesets `004-locations-and-images.yaml` |
| — | Two-axis code review (standards + spec) and single-pass fixes (13 findings: single auth contract, spec 400s, latent 500 → 404, refactors) | this branch, `review-fixes` merge |

## The answer

**The MVP plant entity now carries the full field list #9's curation loads, and the
free/paid contract is fixed before the endpoints that serve it.**

1. **Schema:** `plants` is completed additively (changeset `002`): `family`, `tags`
   (TEXT[] + GIN index for secondary search), `light_needs` / `humidity` / `temp_range`,
   `toxic_to_pets` / `toxic_to_humans`, `difficulty` / `native_region` / `mature_size`.
   The spurious `unique` constraint on `watering_frequency` is dropped — two plants may
   share a watering interval. The curated 50-plant dataset (#9) loads 1:1 without
   remapping.
2. **Tier contract:** free tier gets identity + tags + lightNeeds + waterFrequency +
   toxicity (basic light/water/toxicity); paid tier gets the full plant object (humidity,
   tempRange, growth) plus multi-device sync and advanced analytics. Free tier: 5 photo
   uploads/month, basic recognition results. Per-endpoint visibility:
   [api-tier-contracts.md](../api-tier-contracts.md).
3. **Decisions recorded:** location model (nested house → room, a plant assignable to
   multiple locations) and auth strategy (session-based MVP on `users.password_hash`) —
   both in [ADR-003](../adr/plant-schema-and-tiering-adr.md); API versioning in
   [ADR-004](../adr/api-versioning-adr.md).

## 📦 Implementation Summary

```
openapi/peony-api.yaml                    # single contract source (v1: 14 operations, 4 tags)
build.gradle                              # org.openapi.generator 7.26.0 → com.dendi.peony.api.model
src/main/java/com/dendi/peony/
├── controller/
│   ├── plant/           PlantsController, LocationsController, PlantImagesController
│   ├── auth/            AuthController (signup/signin/profile)
│   ├── subscription/    SubscriptionController (upgrade)
│   └── plantrecognition/PlantRecognitionController (tier shaping, generated DTOs)
├── service/             auth (User/Auth/UploadRateLimit), plant (Plant/Location/PlantImage)
├── repository/          User, UserUploadCounter, Plant, UserPlant, House, Room, PlantLocation, PlantImage
├── entity/              User (+tier), Plant, UserPlant, House, Room, PlantLocation, PlantImage, ...
└── config/              SecurityConfig (sessions, v1 route protection), ClockConfig, ...
src/main/resources/db/changelog/changes/
├── 002-complete-plants-schema.yaml       # T8
├── 003-users-tier.yaml                   # T12 (users.tier, user_upload_counters)
└── 004-locations-and-images.yaml         # T10 (houses, rooms, plant_locations, plant_images)
```

**Final route table** (all session-authenticated under `/api/v1`, per ADR-004):

| Method | Path |
|--------|------|
| POST | `/api/v1/plantrecognition/recognize` (tier-shaped; free: 5/month) |
| GET / POST | `/api/v1/plants` (typahead `?q=` / manual registration) |
| GET | `/api/v1/plants/{plantId}` (tier-filtered detail) |
| PATCH | `/api/v1/plants/{plantId}/notes` (→ `user_plants.nickname`) |
| POST | `/api/v1/plants/{plantId}/locations/houses` |
| POST | `/api/v1/houses/{houseId}/rooms` |
| POST / GET | `/api/v1/plants/{plantId}/locations` (assign room, multi-location) |
| GET | `/api/v1/plants/{plantId}/thumb` · `/api/v1/plants/{plantId}/gallery/{index}` |
| POST / GET | `/api/v1/auth/signup` · `signin` · `profile` |
| POST | `/api/v1/subscription/upgrade` |

### Test coverage

110 tests, 0 failures (Testcontainers PostgreSQL + MockMvc): schema migration paths
(fresh + 001→002 upgrade, duplicate watering frequency, field exposure), full auth flow,
rate limiting (free 5/month, premium unlimited, month rollover via injected clock),
free/premium recognition shapes, plant CRUD + tier field visibility, location nesting +
multi-location, image serving + 404s, and the `/api/v1` migration (old routes 404).

---

## ⏭️ Follow-ups

- **#9** (unblocked): curate the 50-plant seed into the completed schema + the
  `plantnet_responses` ledger (design in [issue #7's docs](7/02-sources-and-licensing.md)).
- **RN client generation** from `openapi/peony-api.yaml` (carried over from #13's out-of-scope).
- **Paid features** beyond capability flags: multi-device sync and advanced analytics
  (flagged in tier responses; no implementation yet).
- **Image ingestion** for the gallery endpoints (serving is live; images land via #9's
  seed or a future upload flow).

## 📚 References

- Issue #8 (spec): <https://github.com/Dendi-Soft/peony-application/issues/8>
- Tickets: [#12](https://github.com/Dendi-Soft/peony-application/issues/12) ·
  [#13](https://github.com/Dendi-Soft/peony-application/issues/13) ·
  [#10](https://github.com/Dendi-Soft/peony-application/issues/10)
- [ADR-003: Plant Schema Completion and Free/Paid Tiering](../adr/plant-schema-and-tiering-adr.md)
- [ADR-004: API Versioning Strategy](../adr/api-versioning-adr.md)
- [API tier contracts](../api-tier-contracts.md)
- [ADR-001](../adr/plantnet-api-adr.md) / [ADR-002](../adr/plantnet-api-cache-removal-adr.md) (recognition, unchanged)

---

*Last Updated: October 19, 2026 by Development Team*
