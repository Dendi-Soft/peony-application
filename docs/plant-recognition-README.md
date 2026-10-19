# Plant Recognition Integration

This document describes the PlantNet API integration for the Peony application.

## Overview

The plant recognition service integrates with [PlantNet](https://plantnet.org/), a free,
open-source plant identification platform using citizen science data from contributors
worldwide.

### Key Features

- **Species-level identification** for 10,000+ plant species
- **Confidence scores** for every prediction (best match first)
- **Multi-language support** via the `lang` query parameter (default `en`)
- **WebP support** - WebP uploads are converted to JPEG before being sent to PlantNet
- **Automatic retries** with exponential backoff on 429/503 responses

### API Limits

| Plan | Rate Limit | Cost |
|------|------------|------|
| Free | 5 requests/minute | $0/month |
| PRO | 300 requests/minute | €1.50/month |
| Enterprise | Custom limits | Custom pricing |

See: https://plantnet.org/pricing/

## Quick Start

### Step 1: Configure the API Key (Optional)

```yaml
# In src/main/resources/application.yml (or as an environment variable)
plantnet:
  api-key: ${PLANTNET_API_KEY:}  # Optional, leave empty for the free tier
```

To get an API key:
1. Register at https://plantnet.org/
2. Go to Account Settings
3. Copy your API key

**Note:** The free tier (no API key) has a 5 requests/minute rate limit but works for
development and small-scale use. The client retries automatically with exponential backoff
when the rate limit is hit.

### Step 2: Run the Application

```bash
./gradlew bootRun
```

Or using docker-compose:
```bash
docker compose watch
```

### Step 3: Test the API

```bash
# Multipart file upload
curl -X POST http://localhost:8080/api/plantrecognition/recognize \
  -F "file=@plant.jpg" \
  -F "format=image/jpeg"

# Base64-encoded image (form field)
curl -X POST http://localhost:8080/api/plantrecognition/recognize \
  -F "base64Image=<base64-encoded-image>" \
  -F "format=image/jpeg"
```

## API Endpoints

### Recognize Image

```http
POST /api/plantrecognition/recognize
Content-Type: multipart/form-data

file: <image>            (multipart part, optional)
base64Image: <base64>    (query or form parameter, optional)
format: image/jpeg       (query or form parameter, default image/jpeg; image/webp is converted)
```

One of `file` or `base64Image` is required.

Response:
- `successful` - whether PlantNet reported a successful identification
- `hits` - predictions, best match first, each with:
  - `score` - confidence (0.0 to 1.0)
  - `species` - scientific name (with/without author), genus, family, common names
  - `gbif` / `powo` - external taxon IDs
- `apiVersion` - PlantNet API/model version used
- `remainingRequests` - remaining identification requests (quota)

## Error Handling

| HTTP Status | Description |
|-------------|-------------|
| 400 | Missing image, invalid base64, unparseable format, or upstream client error |
| 415 | Uploaded WebP could not be decoded |
| 429 | PlantNet API rate limit exceeded (after retries) |
| 502 | PlantNet API unavailable or network failure (after retries) |

Error responses include an `error` field with a human-readable message.

## Configuration Properties

```yaml
plantnet:
  api-key: ${PLANTNET_API_KEY:}   # Optional, empty for the free tier
  retry-attempts: 5               # Max attempts per request (429/503 and network retries)
  retry-base-delay-ms: 1000       # Initial backoff delay, doubled per retry (cap 30 s)
```

Environment variables can override the properties:
```bash
export PLANTNET_API_KEY=your-key
```

## Development Notes

- Maximum multipart size follows the Spring Boot default (1 MB); increase
  `spring.servlet.multipart.max-file-size` if larger photos are needed
- Best accuracy for **clear, well-lit photos** of leaves/flowers
- Works best with **single-dominant** subjects (avoid mixed species shots)
- WebP uploads are transparently converted to JPEG by `WebpConverter`

## Troubleshooting

### "400 Bad Request" errors
- Check that `file` or `base64Image` is provided and that `format` is a valid media type
  (e.g. `image/jpeg`, `image/png`, `image/webp`)

### "429 Too Many Requests" errors
- Free tier limit: 5 requests/minute
- The client already retries with exponential backoff; persisting 429s mean the limit is
  exceeded for longer than the retry window - upgrade to a paid plan or add a key

### Poor identification results
- Try with clearer photos (good lighting, sharp focus)
- Include leaves, flowers, or distinctive features
- Check if the species is in the PlantNet database

## License

This integration uses the PlantNet API under its terms of use. See:
https://api.plantnet.org/#Terms-and-Conditions

---
