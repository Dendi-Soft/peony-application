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

Both tiers also see the read-only `source` field (the catalogue row's creation origin:
`PLANTNET_SCAN`, `MANUAL`, or `SEED`), as recorded in
[ADR-007](adr/catalogue-source-adr.md).

## Plant field groups

Field names follow #9's Phase 2 list; the database columns are snake_case in the
`plants` table (changeset `002-complete-plants-schema`).

| Group | Fields (API names → `plants` columns) | Tier |
|---|---|---|
| identity | `commonName` → `name`, `scientificName` → `scientific_name`, `family` → `family` | free |
| provenance | `source` → `source` (read-only; `PLANTNET_SCAN` \| `MANUAL` \| `SEED`) | free (both tiers) |
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

Unknown identity/care fields are also omitted (absent, not null): `commonName` and
`waterFrequency` are optional because scan-created rows have no common name or watering
interval, and `family` may be absent. Clients must treat an absent field as unknown rather
than as an empty value.

## Per-endpoint field visibility

Endpoints per [#10](https://github.com/Dendi-Soft/peony-application/issues/10). Writes
accept the full field set regardless of tier; **responses** are filtered by the
requesting user's tier.

### Plant CRUD (#10 Phase 1, #38)

| Endpoint | Free tier response | Paid tier response |
|---|---|---|
| `GET /api/v1/plants?q=` (typahead) | free field set per plant | full plant object per plant |
| `POST /api/v1/plants` (manual registration) | created **individual** (its id, `plantId`, empty nickname, watering started now) with the free plant field set | the same individual with the full plant field set |
| `POST /api/v1/plants/from-scan` (scan registration) | created **individual** with the free plant field set; catalogue row is found or created | the same individual with the full plant field set |
| `GET /api/v1/plants/{plantId}` | free field set | full plant object |

Manual registration (`POST /api/v1/plants`) requires only `scientificName`;
`commonName`, `waterFrequency`, and the care fields are optional as of #38. A new row
records `source = MANUAL`; an existing row is reused without filling its blanks or
changing its source.

Scan registration (`POST /api/v1/plants/from-scan`) accepts the draft's
`scientificName` (required), optional `commonName`, `family`, `nickname`, `roomId`, and
`lastWateredAt`. It does not accept care data. A missing catalogue row is created with
identity/taxonomy only and `source = PLANTNET_SCAN`; an existing row keeps its values and
source, with only blank `commonName`/`family` filled. The catalogue row and the new
individual are written in one transaction, so a foreign/missing room (404) or a malformed
`lastWateredAt` (400) leaves nothing behind. Because a scan-created row has no watering
frequency, a supplied `lastWateredAt` does not produce a `nextWaterDueAt` until #9 curates
the frequency.

The per-plant notes endpoint (`PATCH /api/v1/plants/{plantId}/notes`) was retired in #37;
nicknames now live on the individual and are edited through `PATCH /api/v1/me/plants/{userPlantId}`.

### My plants (#25, #37)

Each row is one **individual** specimen (`user_plants` row), not one catalog plant: the same
catalog plant can produce several rows. Both tiers see the same individuals; the tier affects
only the plant field visibility. Every row carries the individual's `id`, its catalog `plantId`,
the caller's `nickname` (empty string when unset), the nullable `lastWateredAt` /
`nextWaterDueAt` (absent when the watering history is unknown), and the single optional
`room` + `house` pair (both absent when the individual is unplaced — they are never returned
half-populated). There is no `locations` array any more.

| Endpoint | Free tier response | Paid tier response |
|---|---|---|
| `GET /api/v1/me/plants` | the caller's individuals, newest first, with the free field set and individual state | the same rows with the full plant object |
| `POST /api/v1/me/plants` | created individual, free field set; defaults: nickname `""`, no room, unknown watering history | created individual, full field set |
| `GET /api/v1/me/plants/{userPlantId}` | the individual, free field set | the individual, full field set |
| `PATCH /api/v1/me/plants/{userPlantId}` | replaced individual, free field set (replace, not sparse: omitted fields become null/empty) | replaced individual, full field set |
| `DELETE /api/v1/me/plants/{userPlantId}` | 204; the catalog plant and room stay | 204 |
| `POST /api/v1/me/plants/{userPlantId}/water` | updated individual, free field set; `lastWateredAt` = now, `nextWaterDueAt` = now + catalog frequency | updated individual, full field set |

All six routes derive the caller from the bearer token and scope reads and writes to the
caller's own individuals; another user's individual behaves as 404. A `roomId` is accepted
only when the room belongs to one of the caller's houses.

### Location management (#10 Phase 2, #26)

No field split — location endpoints return the same payload in both tiers.
`GET /api/v1/me/houses` returns the caller's houses with their rooms nested and has no tier field split.

| Endpoint | Free tier | Paid tier |
|---|---|---|
| `POST /api/v1/me/houses` | available | available |
| `GET /api/v1/me/houses` | available | available |
| `POST /api/v1/houses/{houseId}/rooms` | available | available |

#37 retired the plant-scoped house and assignment routes (`/api/v1/plants/{plantId}/locations*`):
an individual is placed by setting its single `roomId` through the my-plants create/update
routes, and `plant_locations` no longer exists.

### Image endpoints (#10 Phase 3)

Serving stored images is not tier-gated; the monthly upload cap applies to recognition
uploads, not to these endpoints.

| Endpoint | Free tier | Paid tier |
|---|---|---|
| `GET /api/v1/plants/{plantId}/thumb` | available | available |
| `GET /api/v1/plants/{plantId}/gallery/{index}` | available | available |

### Recognition (existing endpoint)

| Endpoint | Free tier | Paid tier |
|---|---|---|
| `POST /api/v1/plantrecognition/recognize` | basic recognition results; 5 uploads per month | complete recognition results; no monthly cap |

As of #38 every hit at or above a confidence score of `0.30` carries a stateless
`catalogDraft` (binomial `scientificName`, first common name, family). The free tier
carries the top hit's draft only (none when that hit is below `0.30`); the paid tier
carries one per qualifying hit while still returning lower-scoring hits without a draft.
Drafts are built from the full PlantNet payload before the free-tier shape drops the
species' taxonomy, so a free response can have `catalogDraft.family` even though
`species.family` is absent.

## Enforcement notes

- The tier is determined per authenticated user; tier state changes via
  `POST /api/v1/subscription/upgrade` (#12, no payment in the MVP).
- #12 enforces: 401 on protected routes without a session, the monthly free-upload
  counter (DB-backed, survives restarts), and capability flags on API responses
  (profile + recognition response). The exact free/paid recognition response shape is
  defined by #12 and must match this visibility split.
- Manual registration (`POST /api/v1/plants`) always creates one individual for the caller,
  stamped `last_watered_at = now`; `next_water_due_at` is `now + watering_frequency`, or
  null when the catalogue row has no frequency (possible since #38). `POST
  /api/v1/me/plants` instead starts with an unknown watering history (both dates null)
  unless `lastWateredAt` is supplied, in which case the next due date is derived from the
  catalog frequency. Watering dates and the room are nullable as of changeset 007;
  `commonName`, `waterFrequency`, and the catalogue `source` follow changeset 008.
- Multi-device sync and advanced analytics are paid-tier product features; their API
  surface is outside the current endpoint set.
