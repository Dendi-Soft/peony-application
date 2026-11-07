# Runbook: seed the curated plant dataset

The curated dataset seeder (issue #9, T9.3) upserts the 50 plants in
[`src/main/resources/dataset/plants-seed.json`](../../src/main/resources/dataset/plants-seed.json)
into the `plants` catalogue. It is a one-shot job, not part of normal startup.

- Entry point: `com.dendi.peony.dataset.DatasetSeedRunner`
- Seeder: `com.dendi.peony.dataset.PlantDatasetSeeder`
- Dataset documentation: [`docs/dataset/README.md`](../dataset/README.md)
- Source semantics: [ADR-007](../adr/catalogue-source-adr.md)

## What a run does

1. Loads `classpath:dataset/plants-seed.json` and validates the whole file before writing
   anything: 50 entries, every field present, unique binomial scientific names, toxicity
   sources present, watering frequency 1-90, difficulty `Easy | Moderate | Hard`, and
   non-empty tags. An invalid file fails with one message listing every problem and writes
   nothing.
2. Upserts each entry with `INSERT ... ON CONFLICT (scientific_name) DO UPDATE`.
   - A new row gets `source = 'SEED'` and `created_at = now`.
   - An existing row has its curated content columns overwritten, but **keeps its
     `source` and `created_at`**. A row first created by a scan stays `PLANTNET_SCAN`; a
     manually registered row stays `MANUAL`.
3. Backfills `user_plants.next_water_due_at = last_watered_at + watering_frequency days`
   for individuals of seeded species that have a last-watered date and no due date.
   Individuals with no last watering stay unscheduled, species outside the dataset are
   untouched, and an existing due date is never overwritten.

Re-running is safe: the upsert key is `scientific_name` and the backfill only fills
null due dates. Run it after every dataset change and after restoring a backup that
predates the curated catalogue.

## Prerequisites

- The database is reachable and Liquibase migrations have run (starting the application
  applies them; the seeder itself needs the final schema).
- A JWT signing secret is configured. The context still validates `peony.jwt.secret` even
  with the web server disabled, so either export `JWT_SECRET` or activate the `local`
  profile, which supplies a development secret.
- Docker is available for `./gradlew bootRun` only if you start PostgreSQL with Docker
  Compose; a directly reachable PostgreSQL works too.

## Gradle

```sh
# Use the local profile (development JWT secret) against the compose database.
SPRING_PROFILES_ACTIVE=local \
  ./gradlew bootRun --args='--spring.main.web-application-type=none --peony.dataset.seed.enabled=true'
```

Without the local profile, set `JWT_SECRET` and the usual `POSTGRES_*` variables:

```sh
JWT_SECRET=your-secret \
  ./gradlew bootRun --args='--spring.main.web-application-type=none --peony.dataset.seed.enabled=true'
```

`--spring.main.web-application-type=none` stops the process after the runner completes;
without it, `bootRun` would keep serving on port 8080.

## Docker Compose

```sh
# The seeder needs the database; do not start the long-running app service.
docker compose up -d db

docker compose run --rm --no-deps peony \
  --spring.main.web-application-type=none \
  --peony.dataset.seed.enabled=true
```

The image entrypoint is `java -jar app.jar`, so the arguments are passed straight to
Spring Boot. The `peony` service already sets `SPRING_PROFILES_ACTIVE=local` and the
`db` host, and provides the development JWT secret through that profile.

## Expected output and exit status

On success the log contains:

```text
Seeded 50 curated plants and backfilled <n> watering due dates
```

with the upserted row count and the number of newly scheduled individuals. The process
exits `0` after the context closes.

On failure the exception propagates, Spring Boot logs it, and the process exits non-zero.
A validation failure names every bad entry and writes nothing; a database failure rolls
back the whole transaction.

## Verifying

```sql
SELECT source, count(*) FROM plants GROUP BY source ORDER BY source;

SELECT p.scientific_name, count(*) AS individuals,
       count(up.next_water_due_at) AS scheduled
FROM user_plants up
JOIN plants p ON p.id = up.plant_id
GROUP BY p.scientific_name
ORDER BY p.scientific_name;
```

The first query should show 50 `SEED` rows plus any pre-existing `PLANTNET_SCAN` or
`MANUAL` rows that were enriched in place. The second shows the due-date backfill; an
individual with a null `last_watered_at` is expected to stay unscheduled.

## Notes and limits

- The seeder does not create catalogue images. Curated catalogue images are out of scope
  for issue #9; the PlantNet species fallback (ADR-008) is the species-image source.
- The seeder never deletes catalogue rows. Removing a species from the seed file leaves
  the existing row in place.
- The runner is disabled unless `peony.dataset.seed.enabled=true` (or
  `PEONY_DATASET_SEED_ENABLED=true`).
