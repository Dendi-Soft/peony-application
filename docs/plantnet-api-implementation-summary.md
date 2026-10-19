# PlantNet API Implementation Summary

## Overview

This document describes how the Peony Application's PlantNet recognition flow now matches the official PlantNet API request specification.

## Target Request

The implementation is based on this curl command:

```bash
curl -X 'POST' \
    'https://my-api.plantnet.org/v2/identify/all?include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false&api-key=<KEY>' \
    -H 'accept: application/json' \
    -H 'Content-Type: multipart/form-data' \
    -F 'images=@plant.png;type=image/png'
```

## Changes Made

### 1. API Endpoint URL (`PlantNetClient.java`)

**Before:** `https://api.plantnet.org/v2/identify`  
**After:** `https://my-api.plantnet.org/v2/identify/all`

The endpoint now uses `/identify/all` which is the comprehensive identification endpoint recommended by PlantNet.

### 2. Query Parameters

All required query parameters from the curl command are now included:

| Parameter | Value | Description |
|-----------|-------|-------------|
| `include-related-images` | `false` | Don't include related images in response |
| `no-reject` | `false` | Include rejected results (not just best matches) |
| `nb-results` | `10` | Return up to 10 identification results |
| `lang` | `en` | Response language: English |
| `detailed` | `false` | Don't include detailed species information |

### 3. API Key Authentication

**Before:** Used Bearer token header (`Authorization: Bearer <key>`)  
**After:** Uses query parameter (`api-key=<KEY>`)

The PlantNet API v2 uses the API key as a URL query parameter, not in the Authorization header. The implementation now correctly passes it via `?api-key=<encoded_key>` when authentication is enabled.

### 4. Base URL Update

**Before:** `https://api.plantnet.org/v2`  
**After:** `https://my-api.plantnet.org/v2`

Updated to use the official PlantNet API domain with proper subdomain routing.

## Files Modified

1. **PlantNetClient.java** - Main client implementation
   - Updated base URL and endpoint path
   - Added query parameter constants
   - Fixed authentication method (query param instead of header)
   - Added retry logic for rate limiting

2. **JsonUtil.java** - Utility class
   - Added `encodeUrl()` method for proper URL encoding of API keys

3. **PlantNetClientConfig.java** - Configuration bean
   - Now enables authentication when API key is provided via environment variable
   - Reads `PLANTNET_API_KEY` from environment or config file

4. **`src/main/resources/application.yml`** - PlantNet configuration
   - `plantnet.api-key: ${PLANTNET_API_KEY:}` - API key from the environment
   - `plantnet.retry-attempts` / `plantnet.retry-base-delay-ms` - retry settings
   - Authentication is enabled automatically when a key is configured (no toggle)

## Configuration

### Environment Variable (Recommended)

```bash
export PLANTNET_API_KEY=<YOUR_API_KEY>
```

Or add to your `.env` file:
```
PLANTNET_API_KEY=your-api-key-here
```

### Application Properties

In `src/main/resources/application.yml`:

```yaml
plantnet:
  api-key: ${PLANTNET_API_KEY:}  # Read from environment variable (empty = free tier)
  retry-attempts: 5              # Max attempts per request (429/503 and network retries)
  retry-base-delay-ms: 1000      # Initial backoff delay, doubled per retry
```

## Request Flow

When an image recognition request is made, the client now constructs a URL like this:

```
POST https://my-api.plantnet.org/v2/identify/all?include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false&api-key=<ENCODED_KEY>
Content-Type: multipart/form-data

--boundary
images=image1.png; filename="image1.png"
organs=auto
```

## Response Handling

The implementation correctly handles the PlantNet API response structure, extracting:
- Hit results with scores and scientific names
- Genus information (extracted from full species name)
- API metadata (version, remaining requests if available)

## Testing

To test locally:

1. Set your API key:
   ```bash
   export PLANTNET_API_KEY=<YOUR_API_KEY>
   ```

2. Start the application:
   ```bash
   ./gradlew bootRun
   ```

3. Test with a sample image or use Swagger UI at `http://localhost:8080/swagger-ui.html`

## Rate Limiting

The implementation includes built-in retry logic for handling PlantNet API rate limits (5 requests/minute on free tier):

- Maximum 5 retry attempts
- Progressive backoff delays
- Proper error messages for 429, 503 responses

## Notes

- The `api-key` parameter is only appended when authentication is enabled and a key is provided
- Without an API key, PlantNet may still allow limited free-tier access (no auth mode)
- All query parameters are hardcoded to match the official API specification for consistency
- Language defaults to English (`lang=en`) but can be overridden via `language` form field parameter
