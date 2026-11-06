# Runbook: Catalogue species images

Catalogue plant images (issue #45, ADR-008). This runbook covers the curated seed images and the
PlantNet species fallback that fills an image-less catalogue entry after a scan.

## Layout and configuration

- Image files live in `peony.plant-images.dir`, configured by `PEONY_PLANT_IMAGES_DIR` (default
  `plant-images`). In Docker Compose the directory is the named volume `plant_images` mounted at
  `/app/plant-images`.
- `plant_images` maps a plant and a 0-based gallery position to one file. The directory is flat:
  all files sit at the top level and are named `plantnet-<uuid>.jpg` for fallbacks or
  `seed-<uuid>.jpg` for curated catalogue images.
- The bytes are served by `GET /api/v1/plants/{id}/thumb` (position 0) and
  `GET /api/v1/plants/{id}/gallery/{position}`. There is no static file server and no public URL.
- Each row carries its origin in `source` (`SEED` or `PLANTNET`; null for rows that predate
  attribution) and the attribution fields `author`, `license`, and `citation`.

## The position-0 hero

A curated seed image (issue #9 follow-up) is a 512 px JPEG stored at position 0 with
`source = SEED`. It is the primary thumbnail and gallery hero. When a PlantNet fallback
already occupies position 0, the seeder shifts it to position 1 instead of replacing it, so
both images and their credits remain truthful.

The PlantNet fallback is downloaded once from the medium (`url.m`) image, downscaled to a
long edge of 256 px, re-encoded as JPEG, and stored at position 0 only when the plant has no
image at all. There is no separate thumbnail column: `/thumb` and `/gallery/0` serve the
same bytes. Do not use the 256 px fallback as a full-screen hero or zoom source; personal
photos (issue #44, `docs/runbooks/user-images.md`) are the high-resolution path.

## Seed precedence and the position-shift rule

**Implemented** by `CatalogueImageSeeder` and `CatalogueImageWriter` (issue #9 follow-up);
see [`dataset-images.md`](dataset-images.md) for the runbook. When a curated seed image is
added for a plant that already has a PlantNet fallback at position 0:

- the seed image is inserted at position 0 and the existing `PLANTNET` row is shifted to the
  next position, keeping its file, `source`, and attribution;
- the PlantNet row is never deleted or overwritten;
- the seed image never reuses the PlantNet file name (`seed-<uuid>.jpg` vs
  `plantnet-<uuid>.jpg`);
- a species that already has any `SEED` image is skipped before downloading, so re-running
  the seeder is idempotent.

The shift is two native updates through a large offset (up, then down by `offset - 1`), which
avoids violating the `(plant_id, position)` unique constraint while rows move.

## Staging and TTL

Recognition with PlantNet stages at most one acceptable candidate per normalized binomial in
`plantnet_species_images` (medium URL, organ, author, licence, citation, staged timestamp).
Candidates are not catalogue images: they are only used when a scan of that species is kept and
the catalogue plant has no image.

- TTL: `peony.plantnet-staging.ttl`, default `7d`.
- Cleanup: `PlantNetStagingCleanupRunner` prunes expired rows on every backend startup, and the
  staging service prunes opportunistically before each stage and lookup. There is no `@Scheduled`
  job; do not add one.
- A scan that yields no usable candidate leaves any previous staging row untouched; only the TTL
  retires it.
- A successful fill consumes (deletes) that species' staging row. A failed fill keeps it, so the
  next scan of the same species retries.

## Fill flow

`POST /api/v1/plants/from-scan` first commits the catalogue row and the caller's individual, then
runs the best-effort fill in a separate transaction:

1. skip when the catalogue plant already has any image;
2. look up a fresh staged candidate by the plant's normalized scientific name;
3. download the medium image from the allowlisted origin only
   (`peony.plantnet-image.allowed-origin`, default `https://bs.plantnet.org`), refusing redirects,
   non-JPEG responses, and bodies over 5 MiB; no API key is ever attached;
4. downscale to 256 px and re-encode as JPEG;
5. write `plantnet-<uuid>.jpg` under `peony.plant-images.dir`;
6. insert the position-0 row with `source = PLANTNET` and the candidate's attribution, and delete
   the staging row in one short transaction.

Every failure is logged and swallowed: the scan still returns 201 without an image, and a later
scan retries. There is no bulk backfill; only a scan of that species can fill it.

## Attribution and licensing

Only four licence families are accepted: `cc0`, `public-domain`, `cc-by`, `cc-by-sa`. The
upstream value is normalized (trim, lowercase, `_`/spaces to `-`) and matched exactly against the
family and its known versions; versioned forms such as `cc0-1.0`, `cc-by-3.0`, and `cc-by-sa-4.0`
are accepted and stored as the unversioned canonical value. Non-commercial, no-derivatives,
unknown, and blank licences are rejected and never stored.

The API exposes the attribution so clients can show it:

- `PlantResponse.imageCredits` lists every catalogue image ascending by position, with `source`,
  `author`, `license`, and `citation`; fields that are unknown are absent.
- `UserPlantResponse.imageCredit` carries the position-0 credit while the individual has no
  personal photo. A personal photo always wins, and a catalogue plant without a position-0 image
  has no credit.

Requirements:

- Show the author, licence, and citation wherever a catalogue image appears.
- CC0 and public-domain images carry no conditions.
- CC BY and CC BY-SA require attribution. The stored fallback is a resized, re-encoded derivative
  of the original, so CC BY-SA's share-alike applies: keep the same licence and credit, do not
  relicense the derivative, and do not strip the attribution. If the UI crops or overlays the
  image, treat that as another derivative and keep the CC BY-SA terms.
- Never store a candidate whose licence is outside the whitelist.

## No species-image orphan sweeper

Unlike personal user images, there is **no orphan sweeper** for catalogue species images. The
following leave unreferenced files or unattributed rows and are cleaned up manually:

- a crash between writing `plantnet-<uuid>.jpg` and committing the image row;
- deleting a `plant_images` row without deleting its file;
- restoring an older database snapshot onto a newer `plant_images` volume.

Back up the database and the volume together.

## Inspecting and removing a bad fallback

List the fallbacks and their attribution:

```sql
SELECT pi.id, pi.plant_id, pi.position, pi.filename,
       pi.source, pi.author, pi.license, pi.citation, p.scientific_name
FROM plant_images pi
JOIN plants p ON p.id = pi.plant_id
WHERE pi.source = 'PLANTNET'
ORDER BY pi.plant_id, pi.position;
```

Inspect the stored file inside the container:

```bash
docker compose exec peony ls -l /app/plant-images/plantnet-*.jpg
docker compose exec peony identify /app/plant-images/plantnet-<uuid>.jpg   # ImageMagick
```

Remove a bad fallback in two steps, row first and file second (or file first, but never leave a
row serving a missing file):

```sql
DELETE FROM plant_images
WHERE plant_id = :plant_id AND position = 0 AND source = 'PLANTNET';
```

```bash
docker compose exec peony rm /app/plant-images/plantnet-<uuid>.jpg
```

Deleting only the file leaves a row whose `/thumb` and `/gallery/0` return 404 while the credit
is still advertised. Deleting only the row leaves an unreferenced file. Do both.

To let the pipeline fill it again, run a fresh PlantNet recognition of the species so a candidate
is staged, then keep the scan through `POST /api/v1/plants/from-scan`; an existing catalogue row is
reused and the image-less plant is filled. There is no manual refill endpoint.
