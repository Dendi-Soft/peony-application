# API Tier Contracts

Free vs paid tier field visibility for the Peony API. This is the contract the
endpoint implementations ([#10](https://github.com/Dendi-Soft/peony-application/issues/10))
and the tier enforcement ([#12](https://github.com/Dendi-Soft/peony-application/issues/12))
must honor. The schema decision behind the plant fields and the tier split is recorded
in [ADR-003](adr/plant-schema-and-tiering-adr.md).

## Tiers at a glance

| | Free tier | Paid tier |
|---|---|---|
| **Plant fields** | identity + tags + lightNeeds + waterFrequency + toPets + toHumans | all plant fields (free set + humidity, tempRange, difficulty, nativeRegion, matureSize) |
| **Recognition results** | basic | complete |
| **Photo uploads** | 5 per month | no monthly cap |
| **Multi-device sync** | no | yes |
| **Advanced analytics** | no | yes |

## Plant field groups

Field names follow #9's Phase 2 list; the database columns are snake_case in the
`plants` table (changeset `002-complete-plants-schema`).

| Group | Fields (API names → `plants` columns) | Tier |
|---|---|---|
| identity | `commonName` → `name`, `scientificName` → `scientific_name`, `family` → `family` | free |
| tags | `tags` → `tags` (searchable list) | free |
| careInfo | `lightNeeds` → `light_needs` | free |
| careInfo | `waterFrequency` → `watering_frequency` (days between waterings) | free |
| careInfo | `humidity` → `humidity` | paid |
| careInfo | `tempRange` → `temp_range` | paid |
| toxicity | `toPets` → `toxic_to_pets` | free |
| toxicity | `toHumans` → `toxic_to_humans` | free |
| growth | `difficulty` → `difficulty` | paid |
| growth | `nativeRegion` → `native_region` | paid |
| growth | `matureSize` → `mature_size` | paid |

Free-tier responses omit the paid-only fields (absent, not null); paid-tier responses
carry the full plant object.

## Per-endpoint field visibility

Endpoints per [#10](https://github.com/Dendi-Soft/peony-application/issues/10). Writes
accept the full field set regardless of tier; **responses** are filtered by the
requesting user's tier.

### Plant CRUD (#10 Phase 1)

| Endpoint | Free tier response | Paid tier response |
|---|---|---|
| `GET /api/plants?q=` (typahead) | free field set per plant | full plant object per plant |
| `POST /api/plants` (manual registration) | created plant, free field set | created plant, full field set |
| `GET /api/plants/{plantId}` | free field set | full plant object |
| `PATCH /api/plants/{plantId}/notes` | updated note; identical shape in both tiers | identical shape |

### Location management (#10 Phase 2)

No field split — location endpoints return the same payload in both tiers.

| Endpoint | Free tier | Paid tier |
|---|---|---|
| `POST /api/plants/{plantId}/locations/houses` | available | available |
| `POST /api/houses/{houseId}/rooms` | available | available |
| `POST /api/plants/{plantId}/locations` | available | available |
| `GET /api/plants/{plantId}/locations` | available | available |

### Image endpoints (#10 Phase 3)

Serving stored images is not tier-gated; the monthly upload cap applies to recognition
uploads, not to these endpoints.

| Endpoint | Free tier | Paid tier |
|---|---|---|
| `GET /api/plants/{plantId}/thumb` | available | available |
| `GET /api/plants/{plantId}/gallery/{index}` | available | available |

### Recognition (existing endpoint)

| Endpoint | Free tier | Paid tier |
|---|---|---|
| `POST /api/plantrecognition/recognize` | basic recognition results; 5 uploads per month | complete recognition results; no monthly cap |

## Enforcement notes

- The tier is determined per authenticated user; tier state changes via
  `POST /api/subscription/upgrade` (#12, no payment in the MVP).
- #12 enforces: 401 on protected routes without a session, the monthly free-upload
  counter (DB-backed, survives restarts), and capability flags on API responses
  (profile + recognition response). The exact free/paid recognition response shape is
  defined by #12 and must match this visibility split.
- Multi-device sync and advanced analytics are paid-tier product features; their API
  surface is outside the current endpoint set.
