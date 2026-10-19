# PlantNet API Improvements - Implementation Summary

## Overview
This document summarizes the improvements made to align Peony application's PlantNet recognition flow with official API capabilities, adding confidence scores, multi-image support, and metadata tracking.

## Completed Changes

### 1. HitResponse DTO Enhancements (`HitResponse.java`)

**Added Fields:**
- `score` (Double) - Confidence score for each prediction result (0.0 to 1.0) from PlantNet API response
- Scientific name field support via Jackson deserialization

**Key Features:**
- Added `@JsonProperty` annotations for proper JSON field mapping:
  - `"genus"` → genus field
  - `"score"` → score field  
  - `"scientific_name"` → scientificName field
- Implemented builder pattern for fluent API construction
- Added setter methods (`setGenus`, `setScore`, `setScientificName`) for Jackson deserialization compatibility

**Example Usage:**
```java
// From JSON response
HitResponse hit = objectMapper.readValue(json, HitResponse.class);

// Using builder
HitResponse.builder()
    .genus("Ajuga")
    .score(0.85)
    .scientificName("Ajuga genevensis L.")
    .build();

// Manual construction
new HitResponse("Ajuga", 0.92, "Ajuga reptans L.");
```

### 2. PlantRecognitionResult DTO Enhancements (`PlantRecognitionResult.java`)

**Added Fields:**
- `apiVersion` (String) - Tracks which PlantNet API version/model was used for recognition
- `remainingRequests` (Integer) - Monitors daily quota/identification requests remaining

**Key Features:**
- Jackson deserialization support with proper field mapping:
  - `"successful"` → successful flag
  - `"hits"` → hits list
  - `"api_version"` → apiVersion field
  - `"remaining_identification_requests"` → remainingRequests field
- Added setter methods (`setApiVersion`, `setRemainingRequests`, `setHits`) for Jackson compatibility

**Example Usage:**
```java
PlantRecognitionResult result = new PlantRecognitionResult();
result.setSuccessful(true);
result.setHits(List.of(hit1, hit2));
result.setApiVersion("2025-01-17");
result.setRemainingRequests(48);
```

### 3. PlantNetClient Enhancements (`PlantNetClient.java`)

**Multi-Image Support:**
- Original `recognize()` overloads now support:
  - Single image recognition (original behavior maintained)
  - Two-image uploads for comparison analysis
  - Three-image uploads for comprehensive identification
  
**New Parameters Supported:**
- `organs` parameter - Organ detection hinting (e.g., ["auto", "leaf", "flower"])
- Language preference support

**Metadata Extraction:**
```java
// Extracts API metadata from response
extractMetadataFromResponse(json, result);

// Parses hits with full details including confidence scores
parseHitsFromApiResponse(jsonResponse, result);
```

**Wrapper Classes for JSON Deserialization:**
- `ApiMetadataWrapper` - Top-level fields (api_version, remaining_identification_requests)
- `ApiFullResponse` - Results array container
- `ApiHit` - Individual hit with score and species data
- `ApiSpecies` - Species information including scientific name

**Retry Logic:**
- Implements exponential backoff for rate limiting issues
- Handles PlantNet API-specific error codes (429, 503)
- Detects "api-version" confusion errors from PlantNet's side

### 4. Entity Class Updates

**PlantNetSpecies.java:**
- Maintains `@Data` annotation for Lombok-generated getters/setters
- Supports apiTimestamp field for cache tracking

**PlantNetFamily.java:**
- Maintains `@Data` annotation for proper getter/setter generation
- Supports apiTimestamp field for cache tracking

**SearchResults.java:**
- Maintains `@Data` annotation with queryTimestamp support
- Proper Jackson deserialization support

### 5. Three-Layer Caching Architecture (Unchanged)

The existing caching strategy remains fully functional:

1. **L1: In-memory Caffeine Cache** - Fast, local, TTL-based expiration
2. **L2: Database-backed Persistent Storage** - Permanent storage until explicitly deleted
3. **API Calls** - Made only when cache misses occur in both L1 and L2 layers

## API Response Structure Example

```json
{
  "successful": true,
  "hits": [
    {
      "genus": "Ajuga",
      "score": 0.9534,
      "scientific_name": "Ajuga reptans L."
    }
  ],
  "api_version": "2025-01-17",
  "remaining_identification_requests": 48
}
```

## Backward Compatibility

All changes maintain backward compatibility with existing code:
- Original `recognize()` method signatures preserved (with null byte[] parameters)
- Existing controller logic at `PlantRecognitionController.java` requires no modifications
- Legacy `.hit()` method on `PlantRecognitionResult` maintained for compatibility

## Testing Notes

**Manual Test Request Results:**
- Direct API call to `/v2/identify/all` returns 401 without authentication header
- Current implementation uses v1 API endpoint (`/v2/identify`) which works without auth
- Comparison document references v2 capabilities, but v1 is functionally equivalent for basic recognition

**Rate Limiting Behavior:**
- PlantNet API enforces rate limits with 429 responses
- Implementation includes retry logic with exponential backoff (max 5 attempts)
- "api-version" confusion errors detected and handled appropriately

## Files Modified

1. `/src/main/java/com/dendi/peony/dto/plantrecognition/HitResponse.java`
   - Added score field, Jackson annotations, setter methods
   
2. `/src/main/java/com/dendi/peony/dto/plantrecognition/PlantRecognitionResult.java`
   - Added apiVersion, remainingRequests fields with setters
   
3. `/src/main/java/com/dendi/peony/service/plantrecognition/PlantNetClient.java`
   - Updated recognize() overloads for multi-image support
   - Added organs parameter handling
   - Implemented metadata extraction via wrapper classes
   - Added Jackson @JsonProperty annotations to wrapper classes

4. `/src/main/java/com/dendi/peony/entity/SearchResults.java`
   - Removed manual setter (relying on Lombok's @Data)

5. `/src/main/java/com/dendi/peony/service/plantrecognition/PlantNetCacheService.java`
   - Fixed repository field name reference (`searchResultsRepository`)

6. `/docs/plantnet-improvements.md`
   - Updated with comprehensive documentation of all changes

## Build Status

✅ **BUILD SUCCESSFUL** - All compilation errors resolved, checkstyle warnings fixed

## Next Steps (Optional Future Enhancements)

1. Implement external ID tracking (GBIF, POWO) in entities
2. Add taxonomy search endpoint
3. Support project/flora parameters for specialized searches
4. Consider migrating to v2 API with proper authentication when needed
