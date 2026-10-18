# Plant Recognition Integration

This document describes the PlantNet API integration for the Peony application.

## Overview

The plant recognition service integrates with [PlantNet](https://plantnet.org/), a free, open-source plant identification platform using citizen science data from contributors worldwide.

### Key Features

- **Species-level identification** for 10,000+ plant species
- **Botanical database** with Latin binomial names and family classifications
- **Expert-verified reference images** (standards)
- **Scientific literature links** via treatments
- **Multi-language support** (en, fr, de, es, etc.)

### API Limits

| Plan | Rate Limit | Cost |
|------|------------|------|
| Free | 5 requests/minute | $0/month |
| PRO | 300 requests/minute | €1.50/month |
| Enterprise | Custom limits | Custom pricing |

See: https://plantnet.org/pricing/

## Quick Start

### Step 1: Enable PlantNet in Configuration

```properties
# In application.properties or environment variables
plantnet.api.key=your-api-key-here  # Optional, for public access leave empty
plantnet.api.timeout=30000
plantnet.default.language=en
```

To get an API key:
1. Register at https://plantnet.org/
2. Go to Account Settings
3. Copy your API key

**Note:** The free tier (no API key) has a 5 requests/minute rate limit but works for development and small-scale use.

### Step 2: Run the Application

```bash
./gradlew bootRun
```

Or using docker-compose:
```bash
docker compose watch
```

### Step 3: Test the API

Upload an image (e.g., a flower photo):

```bash
curl -X POST http://localhost:8080/api/plantrecognition/upload \
  -H "Content-Type: application/json" \
  -d '{"imageData": "BASE64_ENCODED_IMAGE_HERE", "format": "image/jpeg"}'
```

## API Endpoints

### Recognize Image (Multipart Upload)

```http
POST /api/plantrecognition/upload
Content-Type: application/json

{
  "imageData": "<base64-encoded-image>",
  "format": "image/jpeg"
}
```

Response includes:
- Top prediction with species name and confidence score
- Family classification
- Common names in requested language(s)
- Reference images (standards) for verification
- Scientific literature references

### Get Species by Taxon ID

```http
GET /api/plantrecognition/species/{taxonId}
```

### Search Taxa by Name

```http
GET /api/plantrecognition/search?q=roses
```

### Download Reference Images

```http
GET /api/plantrecognition/images/standards/{taxonId}/{imageId}
```

## Response Format

```json
{
  "query": "<original-query>",
  "hits": [
    {
      "id": "plantnet-taxa:rosaceae/rubus/fruticos",
      "genus": "Rubus",
      "speciesName": "fruticosus",
      "score": "0.9823",
      "imagesUsed": ["standard-123", ...],
      "taxonId": 123456,
      "standards": [...],
      "treatments": [...]
    }
  ],
  "timestamp": 1698765432,
  "version": 2
}
```

## Error Handling

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 401 | NOT_AUTHORIZED | API key required but missing (if configured) |
| 429 | TOO_MANY_REQUESTS | Rate limit exceeded |
| 404 | NOT_FOUND | Taxon/image not found |

Error responses include:
- `code`: Error identifier
- `message`: Human-readable description  
- `details`: Additional context (if available)
- `statusCode`: HTTP status code

## Configuration Properties

```properties
# API Authentication
plantnet.api.key=your-api-key-here  # Optional

# Request Timeout (milliseconds)
plantnet.api.timeout=30000

# Default response language
plantnet.default.language=en
```

Environment variables can override properties file settings:
```bash
export PLANTNET_API_KEY=your-key
```

## Development Notes

- Images are **automatically resized** to 1024px max width before upload
- Maximum image size accepted is ~5MB (adjust if using larger crops)
- Best accuracy for **clear, well-lit photos** of leaves/flowers
- Works best with **single-dominant** subjects (avoid mixed species shots)

## Monitoring & Metrics

Track these metrics:
- Recognition success rate (vs. failure rates)
- Average response time
- Rate limit hits (429 responses)
- Most commonly misidentified species

## Troubleshooting

### "401 Unauthorized" errors
```bash
# Make sure PlantNet API is configured correctly
# Check application.properties or use environment variable:
export PLANTNET_API_KEY=your-key-here
```

### "429 Too Many Requests" errors
- Free tier limit: 5 requests/minute
- Upgrade to PRO plan or add retry backoff logic

### Poor identification results
- Try with clearer photos (good lighting, sharp focus)
- Include leaves, flowers, or distinctive features
- Check if species is in PlantNet database
- Compare against reference images (standards)

## Migration Path

When customer volume grows:

1. **Monitor API costs** (if using authenticated tier)
2. **Consider on-premise deployment** when ROI justifies it
3. **Evaluate self-hosting options** for specific plant families
4. **Plan gradual migration** using abstraction layer in code

See [ADR-001](./architecture/plantnet-api-adr.md) for full migration strategy.

## License

This integration uses the PlantNet API under its terms of use. See:
https://api.plantnet.org/#Terms-and-Conditions

---
