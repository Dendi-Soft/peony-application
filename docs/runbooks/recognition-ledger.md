# Runbook: PlantNet response ledger

The PlantNet response ledger (issue #9) is an internal aggregate of the species that recognition
returns, kept in `plantnet_responses`. It grows the recognition corpus without retaining raw
responses or photos, consistent with [ADR-002](../adr/plantnet-api-cache-removal-adr.md). It is not
a response cache: every recognize call still reaches PlantNet.

## Table

Changeset `012-plantnet-responses.yaml` creates:

| Column            | Type           | Meaning                                              |
| ----------------- | -------------- | ---------------------------------------------------- |
| `scientific_name` | `TEXT`         | Primary key: normalized, author-free binomial        |
| `common_name`     | `TEXT`         | First non-blank common name seen, or null            |
| `response_count`  | `BIGINT`       | Number of recognitions whose top hit was this species|
| `avg_confidence`  | `NUMERIC(6,5)` | Running arithmetic mean of top-hit scores (0..1)     |
| `first_seen`      | `TIMESTAMP`    | First sighting; never changes                        |
| `last_seen`       | `TIMESTAMP`    | Most recent sighting                                 |

The key is lowercased and whitespace-collapsed, so `Ajuga Reptans`, `ajuga  reptans`, and names
carrying authorship fold into one row.

## Write path

`PlantRecognitionService.recognize` calls `PlantNetResponseLedger.record` after a successful
identify and the species-image staging step:

- Top hit only (`hits[0]`); no hit or no derivable binomial is a no-op.
- Name: `scientificNameWithoutAuthor`, falling back to the binomial of `scientificName`, normalized
  through `ScientificNames`.
- Confidence: `BigDecimal.valueOf(score)`; NaN, infinite, and out-of-range scores are skipped.
- Timestamp: from the injected `Clock` (never `LocalDateTime.now()`).
- One native `INSERT ... ON CONFLICT (scientific_name) DO UPDATE`: increments `response_count`,
  recomputes the running mean, preserves `first_seen` and an existing non-blank `common_name`, and
  advances `last_seen`.

A `DataAccessException` is caught by the service, logged as a warning, and swallowed: a ledger
failure never fails a recognition response.

## Inspecting

```sql
SELECT scientific_name,
       common_name,
       response_count,
       round(avg_confidence, 5) AS avg_confidence,
       first_seen,
       last_seen
FROM plantnet_responses
ORDER BY response_count DESC, scientific_name;
```

A missing row after a successful recognition means the top hit had no usable name or score, or the
ledger write failed. Search the backend log for
`Could not record the PlantNet recognition in the response ledger`.

## Scope

- Internal state only: no API response, DTO, or OpenAPI change.
- No photos, image bytes, or raw PlantNet payloads are stored.
- The ledger is separate from the curated `plants` catalogue; it never writes to `plants`.
