# PlantNet API Request Comparison

> **Status note:** This document records the v1 → v2 API migration. A later refactor
> (see [ADR-002](./adr/plantnet-api-cache-removal-adr.md)) removed the caching
> layer and simplified the client wiring; the current state is documented in
> [plantnet-improvements.md](./plantnet-improvements.md). The BEFORE/AFTER snippets below
> are kept as a historical record of the migration.

## Official Curl Command (Target)

```bash
curl -X 'POST' \
    'https://my-api.plantnet.org/v2/identify/all?include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false&api-key=<KEY>' \
    -H 'accept: application/json' \
    -H 'Content-Type: multipart/form-data' \
    -F 'images=@plant.png;type=image/png'
```

## Implementation Comparison Table

| Component | Official API | Previous Implementation | Current Implementation | Status |
|-----------|--------------|------------------------|----------------------|---------|
| **Base URL** | `https://my-api.plantnet.org/v2` | `https://api.plantnet.org/v2` | ✅ `https://my-api.plantnet.org/v2` | FIXED |
| **Endpoint Path** | `/identify/all` | `/identify` | ✅ `/identify/all` | FIXED |
| **include-related-images** | `false` | Not set | ✅ `false` | ADDED |
| **no-reject** | `false` | Not set | ✅ `false` | ADDED |
| **nb-results** | `10` | Not set | ✅ `10` | ADDED |
| **lang** | `en` | Not set | ✅ `en` | ADDED |
| **detailed** | `false` | Not set | ✅ `false` | ADDED |
| **api-key parameter** | Query param | Bearer header | ✅ Query param | FIXED |
| **Content-Type** | `multipart/form-data` | ✅ `multipart/form-data` | ✅ `multipart/form-data` | MATCHES |
| **Form fields** | `images` (required), optional `organs`, optional `version` | `images`, `organs` | `images`, `organs` (default `auto`), optional `language` | MATCHES (upstream `version` field is optional and not sent) |

## Code Changes Summary

### PlantNetClient.java

#### Base URL and Endpoint (Lines 70-159)

```java
// BEFORE:
private static final String BASE_URL = "https://api.plantnet.org/v2";
String url = BASE_URL + "/identify";

// AFTER:
private static final String BASE_URL = "https://my-api.plantnet.org/v2";
String baseUrl = BASE_URL + "/identify/all";
StringBuilder urlBuilder = new StringBuilder(baseUrl);
urlBuilder.append("?include-related-images=false")
          .append("&no-reject=false")
          .append("&nb-results=10")
          .append("&lang=en")
          .append("&detailed=false");

// Add API key as query parameter if authentication is enabled
if (useAuthentication && apiKey != null) {
  urlBuilder.append("&api-key=").append(JsonUtil.encodeUrl(apiKey));
}
```

#### Authentication Method (Lines 41-66)

```java
// BEFORE: Used Bearer token header
builder.addInterceptor(chain -> 
    chain.proceed(
        chain.request()
            .newBuilder()
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Accept", "application/json")
            .build()));

// AFTER: Uses query parameter (PlantNet API v2 spec)
builder.addInterceptor(chain -> 
    chain.proceed(
        original.newBuilder()
            .addHeader("Accept", "application/json")
            .build()));

// API key passed as URL query param, not header
```

### JsonUtil.java - New Utility Method

```java
/**
 * URL-encodes a string for use in query parameters.
 */
public static String encodeUrl(String value) {
  if (value == null || value.isEmpty()) {
    return "";
  }
  try {
    return java.net.URLEncoder.encode(value, "UTF-8");
  } catch (java.io.UnsupportedEncodingException e) {
    throw new RuntimeException("Unsupported encoding", e);
  }
}
```

### PlantNetClientConfig.java - Authentication Enabled

```java
// BEFORE:
@Bean
public PlantNetClient plantNetClient(PlantNetCacheService cacheService) {
  this.plantNetClient = new PlantNetClient(cacheService);
  return this.plantNetClient;
}

// AFTER:
@Value("${plantnet.api.key:}")
private String apiKey;

@Bean
public PlantNetClient plantNetClient(PlantNetCacheService cacheService) {
  // Enable authentication when API key is provided
  this.plantNetClient = new PlantNetClient(true, 
      apiKey != null && !apiKey.isEmpty() ? apiKey : null, 
      cacheService);
  return this.plantNetClient;
}
```

## Request Construction Flow

### Step-by-Step URL Building

1. **Start with base URL:** `https://my-api.plantnet.org/v2`
2. **Append endpoint path:** `/identify/all`
3. **Add query parameters:** 
   - `?include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false`
4. **Add API key (if authenticated):** `&api-key=<ENCODED_KEY>`

### Final Request Example

```java
POST https://my-api.plantnet.org/v2/identify/all?include-related-images=false&no-reject=false&nb-results=10&lang=en&detailed=false&api-key=<YOUR_API_KEY>
Content-Type: multipart/form-data

--boundary
images=image1.png; filename="image1.png"
organs=auto
```

## Verification Checklist

- [x] Base URL matches official API domain (`my-api.plantnet.org`)
- [x] Endpoint uses `/identify/all` for comprehensive identification
- [x] All query parameters included with correct values
- [x] API key passed as URL query parameter (not header)
- [x] Content-Type is `multipart/form-data`
- [x] Form fields match PlantNet specification (`images`, `organs`; upstream `version` is optional)
- [x] Retry logic handles rate limiting gracefully
- [x] Response parsing extracts all relevant data

## Testing the Implementation

### Test 1: Without API Key (Free Tier)

```bash
export PLANTNET_API_KEY=""
./gradlew bootRun
# Should work with limited free tier access
```

### Test 2: With API Key (Authenticated)

```bash
export PLANTNET_API_KEY=<YOUR_API_KEY>
./gradlew bootRun
# Will include api-key in request URL
```

### Test 3: Using Swagger UI

Visit `http://localhost:8080/swagger-ui.html` and test the `/api/v1/recognize` endpoint.

## Notes on Query Parameters

All query parameters are hardcoded to match PlantNet's recommended defaults:

- **include-related-images=false**: Reduces response size
- **no-reject=false**: Includes all results, not just high-confidence matches
- **nb-results=10**: Returns up to 10 identifications (reasonable default)
- **lang=en**: English responses for consistency
- **detailed=false**: Omits verbose species details

These can be overridden in future if needed by adding optional parameters.

## Conclusion

The implementation now exactly matches the official PlantNet API request specification, including:
- Correct endpoint path (`/identify/all`)
- All required query parameters with correct values
- Proper authentication method (query param vs header)
- Official API domain and routing
