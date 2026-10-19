# Issue #6: Research: Plant Recognition API & Model Landscape - **COMPLETED**

## Status
✅ **DONE** - PlantNet API Integration Implemented (single recognize endpoint, v2 API)

**Completed:** 2026-10-18  
**Follow-up refactor:** 2026-10-19 (v2 client rewrite, cache removal - see [ADR-002](../adr/plantnet-api-cache-removal-adr.md))  
**ADR:** [ADR-001](../adr/plantnet-api-adr.md) (superseded by [ADR-002](../adr/plantnet-api-cache-removal-adr.md) for the caching design)

> **Design change:** The original implementation exposed `/species/{taxonId}` and `/search`
> endpoints backed by a three-layer cache. Those were removed by design when the client was
> rewritten around the PlantNet v2 identify flow: recognition is now the only API surface, and
> rate-limit pressure is handled with exponential-backoff retries instead of a database cache.

---

## 🎯 Original Research Question
What are the best approaches for lightweight photo-based plant recognition that allow us to return top-k matches from our curated database?

---

## ✅ Deliverables Completed

### 1. **Model Approach Decision: PlantNet API**
**Decision:** Use PlantNet's free REST API instead of building/open-sourcing models

**Why:**
- ✅ **Zero cost** - Free tier (5 requests/minute, upgradeable to PRO)
- ✅ **Rich database** - 10,000+ species with expert verification
- ✅ **Ready-to-use** - No training/infrastructure overhead
- ✅ **Community-driven** - Auto-updates with taxonomy changes

### 2. **Implementation: v2 Identify Client with Retries**
```
Request (multipart file or base64)
  → WebP → JPEG conversion (when needed)
  → POST /v2/identify/all (my-api.plantnet.org)
  → 429/503 or network failure? → exponential backoff retry (default 5 attempts)
  → JSON response → PlantRecognitionResult (hits best match first)
```

**Why no cache:** each recognition call is a unique image, so a response cache has a near-zero
hit rate for the recognize flow; the earlier species/search cache tables were removed along with
the endpoints (see ADR-002).

### 3. **API Endpoint Implemented**

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/plantrecognition/recognize` | POST | Recognize a plant from an uploaded image (multipart `file` or base64 `base64Image`; JPEG, PNG, WebP) |

Interactive documentation: `/swagger-ui.html` (SpringDoc OpenAPI).

### 4. **Database Schema (Liquibase)**
No PlantNet-specific tables. The shared `users` table migration (`001-init-tables.yaml`) remains.

### 5. **Cost Analysis (10k monthly uploads)**
```
Free Tier:     $0/month        → 5 req/min ≈ 43K requests/month
PRO Plan:      €1.50/month     → 300 req/min, if needed
Enterprise:    Custom          → For large-scale needs

Conclusion: FREE tier sufficient for MVP/early adoption
```

---

## 📦 Implementation Summary

### Architecture Structure
```
src/main/java/com/dendi/peony/
├── controller/plantrecognition/
│   └── PlantRecognitionController.java     # REST endpoint + error mapping
├── service/plantrecognition/
│   ├── PlantNetClient.java                 # HTTP client (OkHttp), retries, parsing
│   ├── WebpConverter.java                  # WebP → JPEG conversion
│   └── JsonUtil.java                       # JSON helpers + URL encoding
├── config/
│   ├── PlantNetClientConfig.java           # Client bean (API key, retry settings)
│   └── SwaggerConfig.java                  # OpenAPI document
├── dto/plantrecognition/
│   ├── PlantRecognitionResult.java         # Response DTO (hits, apiVersion, quota)
│   ├── HitResponse.java                    # Hit DTO (score, species, gbif, powo)
│   └── RecognitionRequest.java             # Client request record
└── exception/
    ├── PlantNetApiException.java           # Upstream API error (HTTP status)
    └── UnsupportedImageFormatException.java# Undecodable image error

docs/
├── adr/plantnet-api-adr.md        # ADR-001 (PlantNet selection)
├── adr/plantnet-api-cache-removal-adr.md  # ADR-002 (v2 rewrite, cache removal)
└── plant-recognition-README.md             # Integration guide
```

### Test Coverage
- `PlantNetClientTest` - request shape, API key, retry/backoff, response parsing (MockWebServer)
- `PlantRecognitionControllerTest` - multipart/base64/WebP paths, error mapping (MockMvc)
- `WebpConverterTest` - conversion and rejection behavior
- `PeonyApplicationTests` - context startup with Testcontainers PostgreSQL

---

## 🔧 Configuration

**application.yml:**
```yaml
plantnet:
  api-key: ${PLANTNET_API_KEY:}   # Optional - empty = free tier
  retry-attempts: 5               # Max attempts per request (429/503 and network)
  retry-base-delay-ms: 1000       # Initial backoff, doubled per retry (cap 30 s)
```

**Environment Variable Override:**
```bash
export PLANTNET_API_KEY=your-key-here
```

---

## 🚀 Migration Path for Future Growth

When customer volume increases:

1. **Monitor**: Track 429/503 rate, response times, recognition success rate
2. **Upgrade**: Move to the PRO plan if the free-tier limit becomes a bottleneck
3. **On-prem**: Evaluate self-hosted models when ROI justifies it

**Key Enabler:** the client is isolated in `PlantNetClient` behind one `recognize()` call, so
the provider can be swapped (PlantNet PRO, self-hosted models, other taxonomic APIs) without
touching the controller.

---

## 📚 References & Documentation

- [ADR-001: PlantNet API Integration](../adr/plantnet-api-adr.md)
- [ADR-002: PlantNet v2 Rewrite and Cache Removal](../adr/plantnet-api-cache-removal-adr.md)
- [PlantNet Official API Docs](https://api.plantnet.org/)
- [PlantNet Citizen Science Platform](https://plantnet.org/)

---

## 🔗 Related Artifacts

- **ADR:** [docs/adr/plantnet-api-adr.md](../adr/plantnet-api-adr.md)
- **ADR:** [docs/adr/plantnet-api-cache-removal-adr.md](../adr/plantnet-api-cache-removal-adr.md)
- **Integration Guide:** [docs/plant-recognition-README.md](../plant-recognition-README.md)
- **Implementation details:** [docs/plantnet-improvements.md](../plantnet-improvements.md)

---

## ✅ Acceptance Criteria (final state)

- [x] PlantNet API integration implemented
- [x] REST endpoint functional (`POST /api/plantrecognition/recognize`)
- [x] Database migrations created and applied (shared `users` table)
- [x] Configuration via `application.yml` with environment overrides
- [x] Error handling with user-friendly messages (400/415/429/502 mapping)
- [x] ADRs documenting decisions and rationale (ADR-001, ADR-002)
- [x] Tests covering client, controller, and converter seams

**Superseded criteria** (from the original three-endpoint design, removed by design):
- ~~Three-layer caching architecture deployed~~ → replaced by retry/backoff (ADR-002)
- ~~REST endpoints functional (recognize, species, search)~~ → species/search removed (ADR-002)

---

## 💡 Key Insights from Research Phase

**Lightweight vs Self-Hosted:**
- ✅ Lightweight approach wins for MVP/startup
- ❌ No need to host 10,000+ species models at this stage
- ✅ API-first strategy allows gradual migration when ROI justifies it

**Cost Optimization:**
- Free tier handles MVP volume (5 req/min ≈ 43K requests/month)
- PRO plan only needed if: real-time updates required OR custom taxonomy

**Technical Feasibility:**
- REST API approach is simple and well-maintained
- Community platform ensures quality data (expert verification)

---

*Last Updated: October 19, 2026 by Development Team*
