# PlantNet API Endpoints Comparison

## Visual Endpoint Map

```mermaid
graph TB
    subgraph "Official PlantNet API"
        A1[POST /v2/identify] -->|Image Recognition| R1{Recognition Results}
        B1[GET /v2/projects] -->|List Projects| P1[Project List]
        C1[GET /v2/species] -->|All Species| S1[Species Catalog]
        D1[GET /v2/projects/{id}/species] -->|Paginated Species| S2[Filtered Species]
        E1[GET /v2/projects/{id}/species/align] -->|Name Alignment| A2{Taxonomy Match}
    end
    
    subgraph "Your Peony Implementation"
        F1[POST /api/plantrecognition/recognize] -->|Image Recognition| R2{Recognition Results}
        G1[(L1 Cache)] -.->F1
        H1[(L2 DB Cache)] -.->G1
    end
    
    style A1 fill:#e1f5fe,stroke:#01579b
    style F1 fill:#e8f5e9,stroke:#1b5e20
    style R1 fill:#fff3e0,stroke:#e65100
    style R2 fill:#c8e6c9,stroke:#2e7d32
    
```

## Endpoint-by-Endpoint Comparison

### 1. Recognition Endpoints

| Feature | PlantNet `/v2/identify` | Your `/api/plantrecognition/recognize` | Status |
|---------|------------------------|----------------------------------------|--------|
| **Method** | POST | POST | ✅ Match |
| **Input Format** | Multipart form-data | Multipart + JSON | ⚠️ Partial match |
| **Image Support** | 1-5 images (multipart array) | Single image only | ❌ Gap |
| **Output** | Full recognition results | Simplified DTOs | ⚠️ Incomplete |

### Input Parameter Comparison

```yaml
# PlantNet API accepts:
plantnet_api_input:
  version: "1"                    # ✅ Your impl has this
  images: [...]                   # ❌ Only single image supported
  format: "jpeg|png"              # ✅ Supported via multipart field name
  language: "en|fr|de..."         # ⚠️ Partially implemented
  
# Missing parameters (all optional):
project: null                     # ❌ Not supported  
organs: ["auto", "leaf", ...]     # ❌ Not supported
include-related-images: false     # ❌ Not supported
no-reject: false                  # ❌ Not supported
nb-results: null                  # ⚠️ Can't limit results
detailed: false                   # ⚠️ No detailed family/genus results

# Your implementation only supports:
your_impl_input:
  - file (multipart)              # ✅ File upload
  - base64Image (string)          # ✅ Base64 input  
  - format (default: jpeg)        # ✅ Format parameter
```

### Output Response Comparison

#### PlantNet API Full Response Structure

```json
{
  "query": {                           // Input echo-back
    "project": "all",
    "images": ["hash1"],
    "organs": ["auto"]
  },
  "predictedOrgans": [                 // ⚠️ Missing in your impl
    {
      "image": "hash...",
      "filename": "test.jpg", 
      "organ": "flower",
      "score": 0.66656                // Organ detection confidence
    }
  ],
  "bestMatch": "Ajuga genevensis L.", // ✅ Functionally available via hits[0]
  
  "results": [                         // Multiple predictions with scores
    {
      "score": 0.90734,               // ⚠️ MISSING - confidence ranking!
      
      "species": {                    // Species details
        "scientificNameWithoutAuthor": "Ajuga genevensis",
        "scientificNameAuthorship": "L.",           // ✅ Your: authority field
        "genus": {"scientificName": "Ajuga"},       // ⚠️ Simplified to String
        "family": {"scientificName": "Lamiaceae"},  // ⚠️ Only ID stored, not name
        "commonNames": ["Blue bugleweed", ...],     // ✅ Stored as JSON string
        "scientificName": "Ajuga genevensis L."      // ✅ Full name captured
      },
      
      "gbif": {"id": "2927079"},       // ❌ MISSING - external ID
      "powo": {"id": "444576-1"}       // ❌ MISSING - external ID
    }
  ],
  
  "otherResults": [],                  // ⚠️ Missing if detailed=true
  
  "version": "2025-01-17 (7.3)",      // ❌ MISSING - API version
  
  "remainingIdentificationRequests": 498 // ❌ MISSING - quota tracking
}
```

#### Your Response DTO Structure

```java
// PlantRecognitionResult.java
@Data
public class PlantRecognitionResult {
  private boolean successful;          // ✅ Computed field (not in API)
  private List<HitResponse> hits;      // ⚠️ Simplified structure
  
  public HitResponse hit() {           // Legacy compatibility method
    return hits != null && !hits.isEmpty() ? hits.get(0) : null;
  }
}

// HitResponse.java  
@Data
public class HitResponse {
  private String genus;                // ⚠️ Only genus, missing other fields!
}
```

### Missing Response Fields Impact Analysis

| Field | Purpose | Your Implementation | User Impact |
|-------|---------|---------------------|-------------|
| `score` | Confidence ranking | ❌ Not captured | Can't sort/rank predictions by quality |
| `predictedOrgans[]` | Organ detection results | ❌ Missing entirely | No organ prediction info |
| `gbif.id` / `powo.id` | External database links | ❌ Not stored | Can't provide external references |
| `version` | API model version | ❌ Not captured | Can't track AI engine changes |
| `remainingIdentificationRequests` | Quota tracking | ❌ Not tracked | No rate limit warnings |

---

## Taxonomy Endpoints (Not Implemented)

### 2. Projects Endpoint - NOT IMPLEMENTED ❌

```java
// Proposed implementation: GET /api/plantrecognition/projects
@GetMapping("/projects")
public ResponseEntity<List<ProjectInfo>> listProjects() {
  
  // PlantNet API call to /v2/projects
  Request request = new Request.Builder()
      .url("https://api.plantnet.org/v2/projects?lang=en")
      .get()
      .addHeader("Accept", "application/json")
      .build();
      
  Response response = httpClient.newCall(request).execute();
  String json = response.body().string();
  
  // Parse and return project list
}

// Example ProjectInfo DTO:
@Data
public class ProjectInfo {
  private String id;          // "k-world-flora", "useful", etc.
  private String title;       // "World flora"
  private String description; // "Plants of the world..."
  private Integer speciesCount; // 73591
}
```

**Use Cases:**
- Users want to filter by specific project (e.g., only weeds)
- Display available projects in UI dropdown
- Switch between different flora databases

### 3. Species Catalog Endpoint - NOT IMPLEMENTED ❌

```java
// Proposed implementation: GET /api/plantrecognition/species?q=acer&project=all
@GetMapping("/species")
public ResponseEntity<List<SpeciesInfo>> searchSpecies(
    @RequestParam String q,
    @RequestParam(defaultValue = "all") String project) {
  
  // PlantNet API call to /v2/projects/{project}/species?prefix={q}
  Request request = new Request.Builder()
      .url("https://api.plantnet.org/v2/projects/" + project 
            + "/species?prefix=" + URLEncoder.encode(q, "UTF-8")
            + "&pageSize=10&page=1&lang=en")
      .get()
      .addHeader("Accept", "application/json")
      .build();
      
  Response response = httpClient.newCall(request).execute();
}

// Example SpeciesInfo DTO:
@Data
public class SpeciesInfo {
  private String id;                    // Taxon ID from PlantNet
  private List<String> commonNames;     // ["Field Maple", "Hedge maple"]
  private String genus;                 // "Acer"
  private String family;                // "Sapindaceae"
  private String scientificNameWithoutAuthor; 
  private String scientificNameAuthorship;
  private Integer gbifId;              // External ID
  private String powoId;               // POWO external ID
}
```

**Use Cases:**
- Browse species catalog by name prefix
- Autocomplete search in UI
- Display species information without image upload

### 4. Species Alignment Endpoint - NOT IMPLEMENTED ❌

```java
// Proposed implementation: GET /api/plantrecognition/species/align?name=acer+campestre&project=all
@GetMapping("/species/align")
public ResponseEntity<AlignmentResult> alignSpecies(
    @RequestParam String name,
    @RequestParam(defaultValue = "all") String project) {
  
  // PlantNet API call to /v2/projects/{project}/species/align?name={name}
  Request request = new Request.Builder()
      .url("https://api.plantnet.org/v2/projects/" + project 
            + "/species/align?name=" + URLEncoder.encode(name, "UTF-8")
            + "&authorship=false&synonyms=false&lang=en")
      .get()
      .addHeader("Accept", "application/json")
      .build();
      
  Response response = httpClient.newCall(request).execute();
}

// Example AlignmentResult DTO:
@Data
public class AlignmentResult {
  private String searchedName;         // Input name (e.g., "Acer campestre")
  private String matchingName;         // Normalized name with authorship
  private String acceptedName;         // Accepted scientific name
  private boolean isSynonym;           // Whether it's a synonym
  
  private String family;               // Family classification
  private String genus;                // Genus classification
  
  private GbifInfo gbif;              // External GBIF ID
  private PowoInfo powo;               // POWO external ID
}

@Data
public class GbifInfo {
  private Integer id;
}

@Data  
public class PowoInfo {
  private String id;                   // Format: "781250-1"
}
```

**Use Cases:**
- Validate user-entered species names against PlantNet taxonomy
- Resolve synonyms to accepted names
- Check if a name is valid in the database

---

## Feature Matrix Summary

### Core Recognition Features

| Feature | Official API | Your Implementation | Gap Severity |
|---------|--------------|---------------------|--------------|
| Image upload (multipart) | ✅ 1-5 images | ⚠️ Single image only | Medium |
| Language selection | ✅ Full support | ⚠️ Basic support | Low |
| Project/Flora selector | ✅ Multiple projects | ❌ Always "all" | Medium |
| Organ detection hints | ✅ auto/leaf/flower... | ❌ Not supported | Low |
| Related images feature | ✅ PRO plan only | ❌ Not implemented | N/A (PRO) |

### Response Quality Features

| Feature | Official API | Your Implementation | Gap Severity |
|---------|--------------|---------------------|--------------|
| Confidence scores | ✅ Per-prediction | ❌ Missing entirely | **Critical** |
| External IDs (GBIF/POWO) | ✅ Included | ❌ Not stored | Medium |
| Organ prediction results | ✅ With confidence | ❌ Missing | Low |
| API version tracking | ✅ In response | ❌ Not captured | Low |
| Quota monitoring | ✅ Remaining requests | ❌ Not tracked | Low |

### Taxonomy Features (All Missing) ❌❌❌

| Feature | Official API | Your Implementation | Gap Severity |
|---------|--------------|---------------------|--------------|
| List all projects | GET `/v2/projects` | ❌ Not exposed | Medium |
| Browse species catalog | GET `/v2/species` | ❌ Not exposed | Low |
| Species name alignment | GET `/align` endpoint | ❌ Not exposed | Low |

---

## Implementation Recommendations by Priority

### 🔴 Critical (Add Immediately)

1. **Capture confidence scores** in `HitResponse`
   - Add `private Double score;` field
   - Extract from API response JSON
   
2. **Store external IDs** (GBIF, POWO)  
   - Add fields to `PlantNetSpecies` entity
   - Populate during cache population

### 🟠 High Priority (Add in Next Sprint)

3. **Implement taxonomy search endpoint**
   - Expose `/api/plantrecognition/species?q=` 
   - Use PlantNet species listing API
   
4. **Support multi-image recognition**
   - Modify `recognize()` to accept optional second image
   - Update multipart body builder accordingly

### 🟡 Medium Priority (Future Enhancements)

5. Add project/flora selector parameter
6. Support organ detection hints  
7. Include related images feature (if PRO plan adopted)

---

## Conclusion

Your implementation successfully captures the **core recognition functionality** but misses several important response fields and doesn't expose taxonomy endpoints that PlantNet provides. The caching strategy more than compensates for these gaps by effectively mitigating rate limits.

**Focus areas:**
1. Add missing confidence scores (Critical!)
2. Store external IDs for better linking
3. Consider adding taxonomy search endpoint for discoverability
