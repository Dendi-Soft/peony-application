# PlantNet API Implementation Summary

> **Status note:** This is a research-phase gap analysis. The implementation has since
> evolved (v2 `/identify/all` endpoint, organ hints, multi-image support, retries, cache
> removal - see [ADR-002](./adr/plantnet-api-cache-removal-adr.md) and
> [plantnet-improvements.md](./plantnet-improvements.md)), so some of the "missing" items
> below no longer apply.

## Quick Reference Table

| Feature | Official PlantNet API | Your Implementation | Status |
|---------|----------------------|---------------------|--------|
| **Image Recognition** | POST `/v2/identify` | ✅ Implemented | Working |
| - Single image input | 1-5 images (multipart) | ⚠️ Single only | Partial |
| - Language parameter | `lang=en/fr/de...` | ⚠️ Basic support | Minor gap |
| **Taxonomy Search** | GET `/v2/species` | ❌ Not exposed | Missing |
| **Project Selection** | Project/Flora selector | ❌ Always "all" | Missing |
| **Organ Detection** | `organs=auto/leaf...` | ❌ Not supported | Missing |
| **Confidence Scores** | Per-prediction scores | ❌ Not captured | Critical gap |
| **External IDs** | GBIF, POWO links | ❌ Not stored | Minor gap |

---

## What's Working Well ✅✅✅

### 1. Three-Layer Caching Strategy ⭐⭐⭐⭐⭐
```
L1: In-memory (Caffeine) → Fast, TTL-based
L2: Database-backed     → Persistent storage  
L3: PlantNet API        → Fallback only
```
**Status:** Exceeds recommendations! Effectively eliminates rate limits.

### 2. Rate Limit Handling ⭐⭐⭐⭐⭐
- Retry logic with exponential backoff
- Proper error messages for 429/503 responses  
- Quota-aware design (even without tracking)

### 3. Clean Architecture ⭐⭐⭐⭐⭐
```
Controller → Client → CacheService → Repository
```
**Status:** Well-separated concerns, easy to maintain.

---

## What Needs Attention 🔧🔨

### Critical: Missing Response Fields 🚨

Your `HitResponse` only captures genus name, missing:

```json
{
  "score": 0.90734,                    // ⚠️ Confidence score (CRITICAL!)
  "scientificName": "Ajuga genevensis L.",  // More complete than just genus
}
```

**Impact:** Users can't see prediction quality or full species name.

### High Priority: Add External IDs 📊

PlantNet provides links to biodiversity databases:
- GBIF ID: `"gbif": {"id": "2927079"}`
- POWO ID: `"powo": {"id": "444576-1"}`

**Impact:** Can't link users to external resources.

---

## Implementation Effort Estimates

| Enhancement | Effort | Priority | Impact |
|-------------|--------|----------|--------|
| Add confidence score field | 30 min | 🔴 High | Major UX improvement |
| Store GBIF/POWO IDs | 1 hour | 🟠 Medium | Enables external links |
| Multi-image support | 2 hours | 🟡 Medium | Leverages full API capability |
| Taxonomy search endpoint | 4 hours | 🟢 Low | Nice-to-have feature |

---

## Bottom Line

**Your implementation is solid and production-ready.** The caching strategy alone makes it superior to many commercial alternatives. Focus on adding missing response fields first for better user experience, then consider feature expansion based on actual usage patterns.
