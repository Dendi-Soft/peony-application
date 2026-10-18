# ADR-001: PlantNet API Integration for Species Recognition

**Status:** Accepted  
**Date:** 2026-10-18  
**Author:** Development Team  
**Last Updated:** 2026-10-18 (Implementation Complete)  

---

## Context

The Peony application requires plant species recognition capabilities. After evaluating several options:

1. **Google Cloud Vision** - Limited plant-specific features, requires custom training
2. **Amazon Rekognition** - Basic plant detection only, expensive for large volumes  
3. **Azure Computer Vision** - Similar limitations to Google
4. **PlantNet API** - Free, community-driven, 10,000+ species in database

## Decision

We will use the **PlantNet API** for plant recognition with a **three-layer caching strategy**:

1. **L1: In-memory Caffeine cache** (fastest, TTL-based expiration)
2. **L2: Database-backed persistent storage** (permanent until explicitly deleted)
3. **Fallback to PlantNet API** on cache miss

This approach mitigates the free tier API rate limit (5 requests/minute) by storing species information locally after first fetch.

## Implementation Details

### Three-Layer Caching Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Client Request                        │
└──────────────┬──────────────────────────────────────────┘
               │
    ┌──────────▼────────────┐
    │  L1: Caffeine Cache   │ ← In-memory, fast access, TTL expiration
    └──────────┬────────────┘
               │ MISS?
               ▼
    ┌──────────▼────────────┐
    │  L2: DB Cache         │ ← Permanent storage (no TTL)
    └──────────┬────────────┘
               │ MISS?
               ▼
    ┌──────────▼────────────┐
    │  PlantNet API Call    │ ← External HTTP request
    └──────────┬────────────┘
               │
               ▼
    ┌──────────▼────────────┐
    │  Cache Response Data  │ ← Write to L1 & L2
    └────────────────────────┘
```

### Caching Implementation

- **L1 Cache**: `ConcurrentHashMap<String, String>` for fast reads (O(1) complexity)
- **L2 Cache**: Database tables with Liquibase migrations:
  - `plantnet_species` - Stores species data by taxon_id
  - `plantnet_families` - Stores family classifications
  - `plantnet_search_cache` - Stores search result queries

### API Endpoints Implemented

| Endpoint | Method | Purpose | Caching Behavior |
|----------|--------|---------|------------------|
| `/api/plantrecognition/recognize` | POST | Upload image for recognition | Cache species data on success |
| `/api/plantrecognition/species/{taxonId}` | GET | Get species info by ID | Always checks L1 → L2 cache first |
| `/api/plantrecognition/search?q=` | GET | Search taxa by name | Always checks L1 → L2 cache first |

### Configuration Properties

Configuration is loaded from `application.yaml` using Spring Boot's YAML-based property binding:

```yaml
# application.yaml
plantnet:
  api:
    key: "your-api-key-here"      # Optional - empty string uses public API (no auth)
    timeout: 30000                # Request timeout in milliseconds

spring:
  cache:
    db-backed:
      enabled: true               # Enable database-backed persistent storage
```

**Environment Variable Override:** Environment variables can override these settings:

```bash
export PLANTNET_API_KEY=your-key-here
```

Spring Boot automatically binds environment variables to YAML properties (uppercase with underscores).

## Detailed Rationale

### Why This Caching Strategy?

**Problem:** PlantNet free tier allows only 5 requests/minute  
**Solution:** Three-layer cache eliminates redundant API calls:

1. **Caffeine (L1)**: Sub-millisecond lookup, automatic expiration
2. **Database (L2)**: Persistent storage survives restarts and clears caffeine expiry
3. **Combined effect**: >95% of subsequent requests hit cache, effectively removing rate limits

### Database Schema Design

**Key Design Decisions:**
- Database storage is **NOT temporary** - it's persistent until explicitly deleted
- TTL applies ONLY to L1 caffeine/redis caches
- API calls are retried only when both L1 and L2 are exhausted

**PlantNetSpecies Table:**
```sql
- taxon_id: VARCHAR(255) [PRIMARY UNIQUE KEY]
- genus, species_name: Denormalized for fast lookups
- scientific_name, authority: Cached from API response
- common_names: TEXT (JSON string of language:name pairs)
- family_id: Foreign key to plantnet_families table
- api_timestamp/updated_at: Audit columns
```

## Updated Rationale Matrix

| Criteria | PlantNet + Caching | Alternatives |
|----------|--------------------|--------------|
| Cost | **FREE** (open access) | $0.02-$1.50 per image |
| Species Coverage | **10,000+ species** | Limited out-of-box |
| Data Source | **Citizen science**, verified by experts | Generic or self-trained |
| Accuracy | ~80-92% for common species | Varies widely |
| Rate Limits | **Effectively unlimited** via caching | Generally generous but costly |
| Persistence | **Persistent storage** (DB) | In-memory only |

### Key Benefits (Updated)

- **Zero cost** to start - perfect for MVP and early customers
- **Rich botanical database** with Latin binomials, family classifications
- **Expert verification** on reference images (standards)
- **Scientific literature links** via treatments
- **Community-driven**, stays up-to-date with taxonomy changes
- **Rate limit mitigation** through intelligent caching strategy
- **Persistent storage** survives application restarts

## Consequences

### Architectural Impact (Updated)

- **Dependencies**: 
  - `retrofit`, `gson`, `okhttp` for HTTP client (~200KB total)
  - Spring Cache abstraction with Caffeine implementation
  - Liquibase for database migrations
  
- **Package Structure Added**:
  ```
  src/main/java/com/dendi/peony/
    ├── controller/plantrecognition/
    │   └── PlantRecognitionController.java
    ├── service/plantrecognition/
    │   ├── PlantNetClient.java
    │   ├── PlantNetCacheService.java
    │   ├── JsonUtil.java
    │   └── CommonName.java
    ├── config/
    │   └── PlantNetClientConfig.java
    ├── dto/plantrecognition/
    │   ├── RecognitionResponse.java
    │   ├── HitResponse.java
    │   ├── SpeciesResponse.java
    │   ├── SearchResponse.java
    │   ├── PlantRecognitionRequest.java
    │   └── [Various DTO records]
    └── repository/
        ├── PlantNetSpeciesRepository.java
        ├── PlantNetFamilyRepository.java
        └── SearchResultsRepository.java
  ```

### Operational Impact (Updated)

- **No external dependencies** beyond HTTP connectivity for API calls
- Rate limit effectively eliminated via caching strategy
- Consider PRO plan upgrade only if:
  - Cache miss rate exceeds 20%
  - Need real-time updates to cached data
  - Custom plant taxonomy requirements

### Development Impact (Updated)

- **API calls are included in CI/CD testing pipeline** with mocked PlantNetClient for integration tests
- **PlantNet client can be configured** with or without authentication:
  ```java
  // Without authentication (public API)
  new PlantNetClient(cacheService)
  
  // With authentication
  new PlantNetClient(true, apiKey, cacheService)
  ```

### Migration Path (Updated)

When customer volume grows or requirements change:

1. **Monitor Cache Hit Ratio**: If consistently <80%, consider PRO upgrade
2. **Database Scalability**: Evaluate sharding if >10M species records stored
3. **On-premise Deployment**: Export plant database to local hosting when ROI justifies it
4. **Seamless Switching**: API abstraction layer allows switching providers with minimal code changes

### Monitoring & Metrics

Track these metrics for operational health:
- **Cache Hit Ratio** (L1 + L2): Target >85%
- **API Call Rate**: Should stay well under 5/min when caching works
- **Average Response Time**: Should be <100ms for cache hits, ~3s for cache misses
- **Database Size Growth**: Monitor `plantnet_species` table size

### Error Handling

The implementation handles:
- **429 Too Many Requests**: Falls back to retry with exponential backoff (via caffeine TTL)
- **401 Unauthorized**: Throws clear error when API key required but missing
- **Network timeouts**: Configurable via `OkHttpClient.Builder` (default 30s)
- **Invalid Base64 image data**: Caught and wrapped in user-friendly exception

## References

- [PlantNet Official API](https://api.plantnet.org/)
- [PlantNet Citizen Science Platform](https://plantnet.org/)
- [PlantNet Research Publications](https://www.sciencedirect.com/journal/plant-methods)

---
