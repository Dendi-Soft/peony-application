# Runbook: seed curated catalogue images

The catalogue image seeder (issue #9 follow-up) writes one curated Wikimedia Commons image
per seeded species into `plant_images` at position 0. It is a one-shot job that runs after
the plant seeder, not part of normal startup.

- Entry point: `com.dendi.peony.dataset.CatalogueImageSeedRunner` (`@Order(2)`)
- Seeder: `com.dendi.peony.dataset.CatalogueImageSeeder`
- Writer: `com.dendi.peony.dataset.CatalogueImageWriter`
- Downloader: `com.dendi.peony.dataset.CatalogueImageDownloader`
- Selection file: [`dataset/catalogue-images.json`](../../dataset/catalogue-images.json)
- Dataset documentation: [`docs/dataset/README.md`](../dataset/README.md)
- Image-cache documentation: [`docs/dataset/image-cache.md`](../dataset/image-cache.md)
- Precedence and attribution: [ADR-008](../adr/plant-images-adr.md),
  [`species-images.md`](species-images.md)

## Configuration

| Property | Environment variable | Default | Meaning |
|----------|----------------------|---------|---------|
| `peony.dataset.images.enabled` | `PEONY_DATASET_IMAGES_ENABLED` | `false` | run the image seeder at startup |
| `peony.dataset.images.long-edge` | `PEONY_DATASET_IMAGES_LONG_EDGE` | `512` | stored image long edge, px |
| `peony.dataset.images.request-interval-ms` | `PEONY_DATASET_IMAGES_REQUEST_INTERVAL_MS` | `500` | pause between downloads, ms |
| `peony.dataset.images.file` | `PEONY_DATASET_IMAGES_FILE` | `dataset/catalogue-images.json` | classpath location of the selection |
| `peony.plant-images.dir` | `PEONY_PLANT_IMAGES_DIR` | `plant-images` | directory holding the stored files |

The downloader is pinned to `https://upload.wikimedia.org`, refuses redirects, accepts only
`image/jpeg`, `image/png`, or `image/webp`, checks both the declared content type and the
body's magic bytes, and caps the body at 32 MiB. No credentials are attached.

Wikimedia rate-limits rapid callers with HTTP 429. The seeder waits
`request-interval-ms` between downloads, and the downloader retries 429 and 5xx responses
up to five times with exponential backoff, honouring `Retry-After` and capping the wait at
60 s. Non-retryable statuses (401, 403, 404, …) fail immediately.

## What a run does

1. Loads the selection from the configured classpath resource and validates the whole file
   before the first download. A missing or malformed file is fatal: the run throws and the
   process exits non-zero without writing anything.
2. For each entry, in seed-file order:
   1. looks up the catalogue plant by scientific name. A missing plant is counted as a
      failure; run the plant seeder first.
   2. skips the species when it already has any `SEED` image, *before* downloading.
   3. waits `request-interval-ms`, then downloads the selected URL, re-encodes it at
      `long-edge` px as JPEG (metadata dropped), and writes `seed-<uuid>.jpg` under
      `peony.plant-images.dir`.
   4. inserts the position-0 row with `source = SEED`, the manifest `author`, the canonical
      `license`, and `citation = "<title> — <descriptionUrl>"`.
3. Logs `Seeded <n> catalogue images, skipped <n>` and, when applicable, a warning with the
   failure count. The run does not fail because of per-species failures.

## Precedence and the position shift

A pre-existing `PLANTNET` row at position 0 is **retained**, not overwritten. The writer shifts
every existing image of the plant up by a large offset and then down by `offset - 1`, so the
old position-0 fallback becomes position 1 with its file, `source`, and attribution intact,
and the seed image takes position 0. The two-step move avoids the `(plant_id, position)`
unique constraint during the shift. The PlantNet file name is never reused: seed files use
`seed-<uuid>.jpg`.

## Idempotency

Re-running is safe. `existsByPlantIdAndSource(plantId, SEED)` is checked before the download,
so a species with a curated image is skipped and no duplicate row or file is created. To force
a re-seed of one species, delete its `SEED` row and file first (see the SQL below), then run
the seeder again.

## Failure behaviour

| Failure | Result |
|---------|--------|
| selection file missing/malformed/invalid | fatal; exception propagates, process exits non-zero, no writes |
| plant not in the catalogue | logged, counted as failed, run continues |
| download, decode, or file write fails | logged, counted as failed, run continues; no row |
| database insert fails | logged, counted as failed, run continues; the just-written file is deleted |
| Wikimedia returns 429/5xx | retried with backoff; a persistent failure is counted and retried on the next start |

There is no orphan sweeper, so a crash between writing a file and committing the row can
leave an unreferenced file; remove it manually.

A full first run downloads 50 images with 500 ms of pacing plus the download time, typically
one to a few minutes. The backend is already serving requests during that window, so a client
that loads the catalogue before the run finishes will see some leaf placeholders; reload
after the `Seeded … catalogue images` log line. Subsequent starts skip every stored image.

## Gradle

The plant seeder must run first (`@Order(1)`); the image seeder is `@Order(2)`. Use the local
profile for a development JWT secret and disable the web server so the process exits:

```sh
SPRING_PROFILES_ACTIVE=local \
  ./gradlew bootRun --args='--spring.main.web-application-type=none --peony.dataset.seed.enabled=true --peony.dataset.images.enabled=true'
```

To run only the image seeder against an already-seeded catalogue:

```sh
SPRING_PROFILES_ACTIVE=local \
  ./gradlew bootRun --args='--spring.main.web-application-type=none --peony.dataset.images.enabled=true'
```

## Docker Compose

The root `docker-compose.yml` sets `PEONY_DATASET_IMAGES_ENABLED: "true"`, so the image seeder
runs on every backend start (idempotent) and is ordered after the plant seeder. For a one-shot
manual run, start the database and pass the flags to the `backend` service:

```sh
docker compose up -d db

docker compose run --rm --no-deps backend \
  --spring.main.web-application-type=none \
  --peony.dataset.seed.enabled=true \
  --peony.dataset.images.enabled=true
```

The entrypoint is `java -jar app.jar`, so the arguments are passed straight to Spring Boot.
The same command works without the explicit flags when the environment variable is set, but
the one-shot run above makes the intent explicit.

## Verifying

```sql
-- One SEED row per seeded species, at position 0.
SELECT count(*) FILTER (WHERE source = 'SEED') AS seed_rows,
       count(*) FILTER (WHERE source = 'PLANTNET') AS plantnet_rows
FROM plant_images;

-- Every seeded species has its curated image at position 0.
SELECT count(*) AS seeded_species_without_position_zero
FROM plants p
WHERE p.source = 'SEED'
  AND NOT EXISTS (
    SELECT 1 FROM plant_images pi
    WHERE pi.plant_id = p.id AND pi.position = 0 AND pi.source = 'SEED'
  );

-- Retained PlantNet fallbacks moved to position 1, with credits intact.
SELECT p.scientific_name, pi.position, pi.source, pi.filename, pi.author, pi.license, pi.citation
FROM plant_images pi
JOIN plants p ON p.id = pi.plant_id
WHERE pi.source IN ('SEED', 'PLANTNET')
ORDER BY p.scientific_name, pi.position;

-- Image dimensions and stored files: check a sample on disk (512 px long edge).
```

```sh
docker compose exec backend ls -l /app/plant-images/seed-*.jpg
```

The first query should report 50 `SEED` rows after a clean seed; the second should return 0;
the third should show each plant's `SEED` row at position 0 and, where a fallback existed,
`PLANTNET` at position 1.

To remove one species' curated image and let the seeder retry, delete the row first, then the
file:

```sql
DELETE FROM plant_images WHERE plant_id = :plant_id AND source = 'SEED';
```

```sh
docker compose exec backend rm /app/plant-images/seed-<uuid>.jpg
```

## Notes and limits

- Only position 0 is seeded. There is no multi-image gallery, and there is no thumbnail/hero
  split; the single 512 px file serves both.
- The selection is generated from the committed manifest and reviewed; the seeder itself does
  not verify the image botanically.
- The image binaries are not committed. The seeder fetches the selected Wikimedia URL at run
  time, so the job needs outbound HTTPS access.
- `dataset/image-cache/` (recognition-training downloads) is unrelated to this job and is
  never read by the seeder.
