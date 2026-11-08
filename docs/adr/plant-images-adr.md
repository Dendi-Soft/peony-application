# ADR-008: Plant Images — Personal Photos and Species Fallbacks

**Status:** Accepted
**Date:** 2026-11-06
**Author:** Development Team
**Related:** [ADR-002](./plantnet-api-cache-removal-adr.md) (no recognition image retention),
[ADR-006](./user-plant-individuals-adr.md) (user plants as individuals),
[ADR-007](./catalogue-source-adr.md) (catalogue source),
[Issue #9](https://github.com/Dendi-Soft/peony-application/issues/9) (curated seed),
[Issue #27](https://github.com/Dendi-Soft/peony-application/issues/27) (catalogue image volume)

---

## Context

The scan→catalogue flow (#38/#50) creates individuals (`user_plants`) from PlantNet
identification results. The app already has a species-image pipeline: `plant_images` maps a
catalogue plant to files under `peony.plant-images.dir`, served by
`GET /api/v1/plants/{plantId}/thumb` and `/gallery/{index}`. The catalogue seed (#9) and the
compose volume (#27) are still open, so most catalogue images are absent today.

The recognition flow discards the uploaded photo after PlantNet responds; ADR-002 deliberately
kept no image state. The individual screen has no image, and My Plants rows use the catalogue
thumbnail. Users need a recognisable image per individual, and manually created plants need a
non-blank fallback.

PlantNet returns related species images when `include-related-images=true`, with `s`/`m`/`o`
URLs, author, licence and citation. Images are usually CC BY-SA (commercial reuse allowed)
with per-image exceptions; reuse requires attribution and share-alike for adaptations. Our
API key is on the free plan; the parameter is documented but not explicitly marked
plan-gated, so availability must be verified before relying on it.

Production is a single Docker host with local disk and a Postgres container; there are no
plans for multiple backend replicas or serverless. The privacy policy already describes
"photos you keep with your plants".

## Decision

### 1. Resolution order

Each individual has one image slot. Every surface resolves **personal scan photo → shared
species image → leaf placeholder**. The My Plants row and the individual screen use the same
order. The species image is shared by all individuals of that catalogue species; a personal
photo is private to its owner.

### 2. Personal photo capture

The personal photo is stored when the user adds the plant from a scan: the client re-uploads
the already-prepared JPEG (long edge 1280 px, ≤ 1 MB) to the new per-individual endpoint. The
backend does not retain photos from the recognition request. This limits storage to plants the
user keeps and avoids a scan-photo TTL/sweeper. If the upload fails, the client retries once;
the plant is still created with no personal image and falls back to the species image.

There is one personal image per individual in v1. No gallery, manual upload, replace or remove
UI. Deleting the individual removes its image.

### 3. Personal image storage

- `user_plant_images` table, one row per individual with a unique `user_plant_id` and FK
  cascade. Columns: immutable `image_id` (UUID), `filename`, `thumb_filename`, width, height,
  byte size, `created_at`.
- Bytes on local disk under `peony.user-images.dir` (env `PEONY_USER_IMAGES_DIR`), on a named
  volume. Display and thumbnail are JPEG. The server validates, re-encodes and strips
  EXIF/GPS.
- The display image is the prepared 1280 px JPEG (≤ 1 MB); the thumbnail is generated
  server-side at 256 px. A 52 px row uses the thumbnail; the individual hero uses the display
  image.

### 4. Personal image API

- `POST /api/v1/me/plants/{userPlantId}/image` (multipart JPEG)
- `GET /api/v1/me/plants/{userPlantId}/image`
- `GET /api/v1/me/plants/{userPlantId}/image/thumb`

All endpoints are owner-checked. `UserPlantResponse` gains `hasImage` and `imageId`; the
immutable `imageId` is part of the image URL so caches are per-image and a future replacement
busts them. Responses are private-cacheable. The upload is idempotent for retries: a `POST`
for an individual that already has an image returns `409 Conflict`, which the client treats as
success and refreshes. There is no delete or replace endpoint in v1; deleting the plant
cascades the row and file. On sign-out the client clears expo-image memory and disk caches.

### 5. Species images

- `plant_images` gains nullable `source`, `author`, `license` and `citation`; `source` uses
  `SEED` and `PLANTNET`. Position 0 remains the thumbnail/hero; the existing position
  conventions are unchanged.
- Curated seed images (#9) take precedence. When the seed supplies an image for a species, the
  seed image takes position 0 and an existing PlantNet fallback is retained as a later gallery
  image.
- The PlantNet fallback is one image per species taken from the identify response, preferring
  a whole-plant (`habit`) image, then leaf, then any. Only licences compatible with commercial
  reuse and adaptation are accepted: CC0, public domain, CC BY and CC BY-SA; NC, ND and
  unknown licences are rejected.
- The backend stages the candidate reference (URLs plus credits) at scan time for every
  draft-bearing hit, keyed by normalised scientific name, with a TTL. The image is downloaded
  when recognition creates or upserts the top hit's catalogue row with no image (issue #54),
  when `POST /plants/from-scan` finds a catalogue row with no image, or when a later scan finds
  an existing image-less row. There is no bulk backfill. If no candidate passes the licence
  filter or the download fails, the species falls back to the leaf and can retry on a later
  scan.
- `PlantResponse` carries per-position credits; `UserPlantResponse` carries the
  primary/fallback credit. Credits appear under the individual hero and on the species
  detail/gallery; My Plants rows include the credit in the accessible label. The existing
  `PlantNetAttribution` marker remains identification-only.

### 6. Contract and client behaviour

- API contract 0.7.0. The backend spec is authoritative; the UI vendors it after the backend
  lands.
- The list row and hero render the personal image when `hasImage`, otherwise the catalogue
  thumbnail, otherwise the leaf. If a personal image request fails, the client falls back to
  the species image, then the leaf.
- Cross-device: the server is the source of truth. Every device receives `hasImage`/`imageId`
  from `/me/plants` and fetches the same bytes on its next refetch; each device caches its own
  copy. Offline devices show cached images or the fallback. There is one stored copy per
  individual.

### 7. Privacy and lifecycle

- Personal images are private: owner-checked endpoints, `Cache-Control: private`, no public
  URLs.
- Deleting the individual deletes its row and file synchronously. The email-only
  account-deletion runbook covers the user-images directory. An orphan sweeper removes files
  whose row never committed.
- The add sheet shows "We'll save this photo with your plant" when a personal image will
  attach; the marketing FAQ "What happens to my photo?" is updated. The privacy policy already
  covers photos kept with plants.

## Rationale

- **Personal photo primary** makes the collection feel like the user's own plants and
  distinguishes individuals of the same species; the species fallback prevents blank rows for
  manual plants and the long tail.
- **Add-time storage** is the data-minimising option: only plants the user keeps consume
  storage. Retaining every scan temporarily would require a TTL/sweeper and would broaden
  ADR-002's no-retention stance.
- **A separate table** mirrors the existing `plant_images` pattern, keeps `user_plants` about
  the plant rather than its media, and leaves room for a future gallery without migrating the
  data.
- **Local disk** matches the current single-host deployment and the existing catalogue-image
  service; a storage seam keeps object storage a later swap.
- **PlantNet as the species fallback** covers scanned species outside the curated 50 without
  an extra identification call. Copying the image into our storage gives consistent auth,
  caching and attribution and avoids a runtime CDN dependency. The licence whitelist and
  stored credits make the CC BY-SA obligations explicit.
- **Seed precedence** keeps curation as the source of truth; retaining the fallback preserves
  a useful second image without destructive upserts.
- **Versioned, owner-checked URLs** make cross-device caching safe and make a future
  replacement a new URL rather than a stale cache.

## Consequences

**Positive**

- Every individual gets an image slot with a fallback; scanned plants can show the user's own
  photo, and manual plants can show a species image.
- Personal images stay private and die with the individual; species imagery is shared, bounded
  and attributed.
- No new infrastructure beyond a named volume; the existing image endpoints and thumbnail
  conventions are reused.
- The contract change is additive: older clients ignore `hasImage`/`imageId` and keep using
  the catalogue thumbnail.

**Negative / accepted**

- Personal photos add storage and lifecycle work (deletion, orphan sweeping, account-deletion
  runbook) and need a retention notice.
- PlantNet images carry CC BY-SA obligations: attribution and share-alike for resized copies.
  NC/ND images are excluded.
- Free-plan availability of `include-related-images=true` is an implementation risk; if it is
  unavailable, the species fallback degrades to the seed/leaf and must be revisited.
- Local disk is single-host; the design intentionally does not support multiple replicas or
  serverless without moving to object storage.
- There is no replace/remove UI in v1; users must delete and re-add to change a photo.
- #9 must coordinate position 0 and must not treat its image as a second primary image.

## Alternatives considered

- **Species image only:** simplest and cheapest, but loses the personal record and the
  differentiation between individuals.
- **Personal photo only:** manual plants stay blank until scanned; no shared reference for the
  long tail.
- **Per-user switch between personal and reference images:** doubles display modes and support
  without adding data.
- **Recognition-time photo retention:** one upload and survives a lost local URI, but retains
  every scan temporarily and requires TTL cleanup; rejected in favour of data minimisation.
- **Hotlinking PlantNet images:** less storage, but third-party requests leak usage, URLs and
  caching are outside our control, and attribution is harder to guarantee.
- **Postgres `bytea`:** transactional and backed up with the DB, but bloats the database and
  bypasses the existing file-serving pattern.
- **Object storage now:** durable and scalable, but unnecessary for the single-host target;
  the storage seam keeps it available later.
- **Gallery or replace in v1:** more storage, endpoints and state than the one-image value
  justifies; deferred.
- **Deleting the PlantNet fallback when the seed arrives:** cleanest curated-only catalogue,
  but destroys a usable image and makes the seed upsert destructive.

## References

- [ADR-002: PlantNet v2 Rewrite and Cache Removal](./plantnet-api-cache-removal-adr.md)
- [ADR-006: User Plants as Individuals](./user-plant-individuals-adr.md)
- [ADR-007: Catalogue Source and Scan-Created Entries](./catalogue-source-adr.md)
- [Issue #9: Source & Curate Initial Plant Dataset](https://github.com/Dendi-Soft/peony-application/issues/9)
- [Issue #27: Persistent volume for plant-images in docker-compose](https://github.com/Dendi-Soft/peony-application/issues/27)
- [Issue #38: Create catalogue entries from PlantNet scan results](https://github.com/Dendi-Soft/peony-application/issues/38)
- [Issue #54: Catalogue image missing on "See care details" after scanning a species not in the catalogue](https://github.com/Dendi-Soft/peony-application/issues/54)
- [Pl@ntNet identify API](https://my.plantnet.org/doc/api/identify)
- [Using images](https://my.plantnet.org/doc/references/using-images)
- [Data and image licensing](https://docs.plantnet.org/reference/data-and-image-licensing/)
- [Pl@ntNet API terms](https://my.plantnet.org/terms_of_use)
