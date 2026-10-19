# PlantNet API Improvements - Implementation Summary

## Overview
This document summarizes the current state of Peony's PlantNet recognition flow: a single
`POST /api/plantrecognition/recognize` endpoint backed by the PlantNet v2 identify API
(`my-api.plantnet.org/v2/identify/all`), with confidence scores, organ hints, language
preference, WebP support, and automatic retries with exponential backoff.

## Recognition Flow

```
Controller (multipart file or base64)
  → WebpConverter (WebP → JPEG, when the format is image/webp)
  → PlantNetClient.recognize(RecognitionRequest)
  → POST /v2/identify/all?include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false
  → JSON response parsed directly into PlantRecognitionResult (results → hits)
```

## Current Components

### 1. `RecognitionRequest` (DTO record)
Bundles the image bytes with the upload options (`secondImage`, `thirdImage`, `format`,
`organs`, `language`) so the client method takes one parameter instead of a clump of values.
`RecognitionRequest.single(image, format)` covers the common single-image case.

### 2. `HitResponse` DTO
One prediction from the PlantNet API:
- `score` (Double) - confidence (0.0 to 1.0)
- `species` - `scientificName`, `scientificNameWithoutAuthor`, `scientificNameAuthorship`,
  nested `genus`, `family`, and `commonNames`
- `gbif` / `powo` - external taxon IDs

Scientific-name parts the API omits are derived from `scientificName` (split at the last
space) during response parsing.

### 3. `PlantRecognitionResult` DTO
- `successful` - API success flag
- `hits` - prediction results, best match first (mapped from the API's `results` array
  via `@JsonAlias`)
- `apiVersion` - API/model version used (e.g. `2025-01-17`)
- `remainingRequests` - remaining identification requests (quota monitoring)

### 4. `PlantNetClient`
- Builds the multipart identify request (parts named `images`, plus `organs` / `language`)
- Adds the API key as a URL-encoded `api-key` query parameter when configured
- **Retries** on HTTP 429/503 and network failures with exponential backoff
  (`plantnet.retry-attempts`, `plantnet.retry-base-delay-ms`; delay doubles per attempt,
  capped at 30 s)
- Throws `PlantNetApiException` (carries the upstream status) for non-retryable errors
- Parses the response JSON directly into `PlantRecognitionResult`

### 5. `WebpConverter` (service)
Decodes WebP uploads with the TwelveMonkeys ImageIO WebP reader and re-encodes them as
JPEG, since the PlantNet identify endpoint accepts JPEG and PNG. The controller applies it
when `format` is `image/webp` and forwards the effective `image/jpeg` media type.

### 6. Error mapping (`PlantRecognitionController`)
| Failure | HTTP status |
|---|---|
| Missing image / invalid base64 / unparseable format | 400 |
| Undecodable WebP | 415 |
| Upstream 429 (after retries) | 429 |
| Upstream 4xx (after no retry) | 400 with the upstream detail |
| Upstream 5xx (after retries) / network failure | 502 |

## What Was Removed (by design)

The earlier implementation exposed `GET /species/{taxonId}` and `GET /search` endpoints and
a three-layer cache (Caffeine → PostgreSQL → API). These were removed when the client was
rewritten around the v2 identify flow: the recognition endpoint is the only API surface,
and each request goes straight to PlantNet (retries mitigate the free-tier rate limit).
See [ADR-002](./adr/plantnet-api-cache-removal-adr.md) for the decision record.

## Testing

- `PlantNetClientTest` - request shape, API key handling, retry/backoff behavior, response
  parsing (OkHttp MockWebServer)
- `PlantRecognitionControllerTest` - multipart and base64 paths, WebP conversion handoff,
  error status mapping (MockMvc, mocked client)
- `WebpConverterTest` - WebP → JPEG conversion and rejection of invalid input
- `PeonyApplicationTests` - full context startup with Testcontainers PostgreSQL

## Files

| File | Role |
|---|---|
| `controller/plantrecognition/PlantRecognitionController.java` | REST endpoint + error mapping |
| `service/plantrecognition/PlantNetClient.java` | HTTP client, retries, parsing |
| `service/plantrecognition/WebpConverter.java` | WebP → JPEG conversion |
| `service/plantrecognition/JsonUtil.java` | JSON helpers + URL encoding |
| `dto/plantrecognition/PlantRecognitionResult.java` | Response DTO |
| `dto/plantrecognition/HitResponse.java` | Hit DTO (species, GBIF, POWO) |
| `dto/plantrecognition/RecognitionRequest.java` | Client request record |
| `exception/PlantNetApiException.java` | Upstream API error with HTTP status |
| `exception/UnsupportedImageFormatException.java` | Undecodable image error |
| `config/PlantNetClientConfig.java` | Client bean wiring (API key, retry settings) |
