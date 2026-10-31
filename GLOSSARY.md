# Glossary

This file documents shared terminology used in this codebase. Agents should use these terms consistently when generating code, writing ADRs, or creating issues.

## Core Concepts

| Term | Definition | Example Usage |
|------|------------|---------------|
| **Entity** | JPA entity representing a persistent domain object | `class User implements Serializable` |
| **Repository** | Spring Data repository for data access operations | `interface UserRepository extends JpaRepository<User, Long>` |
| **Service** | Business logic layer containing core functionality | `class UserService { ... }` |
| **Controller** | REST endpoint handler exposing HTTP API | `@RestController class UserController` |
| **DTO** | Data Transfer Object for request/response payloads | `class CreateUserRequest { ... }` |

## Package Structure

- **`controller/**`**: REST endpoints and HTTP handlers
- **`service/**`**: Business logic and domain services
- **`repository/**`**: Data access layers (JPA repositories)
- **`entity/**`**: JPA entities mapped to database tables
- **`dto/**`**: Data transfer objects for API requests/responses
- **`configuration/**`**: Spring Boot configuration classes
- **`exception/**`**: Custom exception types and handlers

## Layered Architecture

```
┌─────────────────────────────────────────────────┐
│              REST Controllers                    │
│           (HTTP → Business Logic Bridge)         │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│                 Services                         │
│          (Core business logic and rules)         │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│              Repositories                        │
│           (Data access abstraction layer)        │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│                Entities                          │
│          (JPA entities mapped to DB tables)      │
└─────────────────────────────────────────────────┘
```

## Common Patterns

### Repository Patterns
- **`JpaRepository<T, ID>`**: Standard CRUD operations
- **Custom queries**: Defined via method names or `@Query` annotations
- **Read-only repositories**: Subset of operations for read scenarios

### Service Layer Patterns
- **Service interface + implementation**: For testability and inversion of control
- **Transaction management**: Via `@Transactional` annotation
- **Validation**: Business rules enforced in service layer

## Data Flow

```
Request → Controller → Service → Repository → Entity ↔ Database
Response ← Entity ← Repository ← Service ← Controller ← Response
```

## Naming Conventions (Summary)

| Element | Convention | Example |
|---------|------------|---------|
| Classes | UpperCamelCase | `UserController` |
| Methods | lowerCamelCase | `findUserById()` |
| Fields | lowerCamelCase | `private UserRepository userRepository;` |
| Parameters | lowerCamelCase | `Long userId` |
| Constants | UPPER_SNAKE_CASE | `public static final String STATUS` |
| Package names | lowercase.no_underscore | `com.dendi.peony.controller` |

## Technical Stack

- **Framework**: Spring Boot 4.1.1 (Java 25)
- **ORM**: Hibernate via Spring Data JPA
- **Database**: PostgreSQL
- **Migration**: Liquibase
- **Validation**: Bean Validation (JSR-380)
- **Testing**: JUnit 5 + Testcontainers

## Domain-Specific Terms

Add entries here as domain concepts emerge during development. Each entry should include:
1. Clear definition
2. Example code snippet (if applicable)
3. Related terms (if any)

### PlantNet Integration

| Term | Definition | Example Usage |
|------|------------|----------------|
| **PlantNet API** | External service for plant species recognition via citizen science data (v2 identify endpoint) | `new PlantNetClient(apiKey)` |
| **Hit / HitResponse** | A single prediction from PlantNet: confidence score, species details, GBIF/POWO IDs | `result.getHits().get(0)` is the best match |
| **RecognitionRequest** | Generated client-side request bundling image bytes with options (format, organs, language) | `request.setImage(imageBytes); request.setFormat("image/jpeg")` |
| **Scientific name parts** | `scientificName` (with author), `scientificNameWithoutAuthor`, `scientificNameAuthorship`; missing parts are derived by splitting the full name at the last space | `hit.getSpecies().getScientificNameWithoutAuthor()` |
| **API key** | Optional PlantNet key passed as the `api-key` query parameter; empty key uses the free tier | `plantnet.api-key` in `application.yml` |
| **Retry backoff** | Exponential backoff (delay doubles per attempt, capped at 30 s) on 429/503 and network failures | `plantnet.retry-attempts`, `plantnet.retry-base-delay-ms` |
| **PlantNetApiException** | Upstream API error carrying the HTTP status code, mapped by the controller to 429/400/502 | `exception.getStatusCode()` |

### Authentication

Settled in [ADR-005](docs/adr/jwt-auth-adr.md), implemented in #17–#19. The API is stateless:
no `JSESSIONID`, no cookies.

| Term | Definition | Example Usage |
|------|------------|---------------|
| **Access token** | Short-lived HS256 JWT signed with `peony.jwt.secret`; claims `sub` (user id), `iat`, `exp`; TTL `peony.jwt.access-token-ttl` (default 1 h). Sent as `Authorization: Bearer <token>` | `JwtTokenService.createAccessToken(userId)` |
| **Refresh token** | Opaque 256-bit Base64URL random value, valid `peony.jwt.refresh-token-ttl` (default 30 days), stored SHA-256 hashed in `refresh_tokens`; exchanged for a fresh pair at `POST /api/v1/auth/refresh` | `RefreshTokenService.issueRefreshToken(userId)` |
| **Refresh rotation** | Single-use refresh: a successful refresh revokes the presented row and issues a new pair. Unknown, already-rotated, and expired tokens all return the same 401 envelope, so no oracle distinguishes them | `RefreshTokenService.rotateRefreshToken(presentedToken)` |
| **Bearer authentication** | Every protected route reads `Authorization: Bearer <access token>`; missing or invalid credentials leave the request unauthenticated | `JwtAuthenticationFilter` |
| **Authentication required** | The 401 JSON envelope for every auth failure (missing, invalid, expired, rotated) on protected routes and refresh | `{"error":"Authentication required"}` |

### Tiering

See [docs/api-tier-contracts.md](docs/api-tier-contracts.md) for the per-endpoint contract and
[ADR-003](docs/adr/plant-schema-and-tiering-adr.md) for the recorded decision.

| Term | Definition | Example Usage |
|------|------------|---------------|
| **Tier** | Subscription level (free or paid) determining API field visibility and usage limits; enforced by #12 | `user.tier` |
| **Free tier** | Default tier: identity + tags + basic light/water/toxicity fields, 5 photo uploads/month, basic recognition results | `GET /api/v1/plants/{plantId}` returns the free field set |
| **Paid tier** | Free tier plus humidity/tempRange, all growth fields, multi-device sync, and advanced analytics | `GET /api/v1/plants/{plantId}` returns the full plant object |

### Tiered Access

Quota and recognition shaping implemented by #12; authentication is JWT-based since #17–#19
([ADR-005](docs/adr/jwt-auth-adr.md)).

| Term | Definition | Example Usage |
|------|------------|----------------|
| **Access tier** | A user's subscription level: `FREE` (basic recognition, 5 photo uploads per calendar month) or `PREMIUM` (complete recognition, unlimited uploads). Stored in `users.tier` as `FREE`/`PREMIUM`; exposed in JSON in lowercase (`free`/`premium`) | `UserTier.FREE.getPhotoUploadsPerMonth()` |
| **Capability** | A feature flag granted by a tier and exposed on tiered responses (profile, recognition) so clients can adapt to what the caller may do; the tier→capability mapping lives in `UserTier` | `UserCapability.RECOGNITION_COMPLETE` |
| **Upload quota** | The free tier's limit of 5 photo uploads per calendar month, counted in `user_upload_counters` (one row per user per `yyyy-MM` period, pessimistic lock); the period is derived from an injected `Clock` so tests can move the calendar; exceeding it returns 429 with an upgrade prompt | `UploadRateLimitService.recordUpload(tier, userId)` |
| **Basic recognition result** | The free-tier shape of a recognition response: the top hit only, with the species reduced to `scientificName` and `commonNames`; genus/family, GBIF/POWO IDs and the remaining-request counter are omitted | `RecognitionTierShaper.shape(result, UserTier.FREE)` |
| **Complete recognition result** | The premium-tier shape: the full PlantNet result (all hits and species detail) unchanged | `RecognitionTierShaper.shape(result, UserTier.PREMIUM)` |

### Location model

Settled in ADR-003, implemented in #10 Phase 2 (changesets 004+).

| Term | Definition | Example Usage |
|------|------------|---------------|
| **House** | User-owned top-level location; root of the nested location hierarchy | `houses` table |
| **Room** | Location nested under a house | `rooms` table |
| **Plant location** | Assignment of a plant to a room; a plant may occupy multiple locations | `plant_locations` table (unique plant↔room pair) |

---

*Last updated: October 2026*
