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
| **PlantNet API** | External service for plant species recognition via citizen science data | `new PlantNetClient(cacheService)` |
| **TaxonId** | Unique identifier for a plant species in PlantNet's database (e.g., "123456") | `species.getSpecies(taxonId)` |
| **PlantHit** | A prediction from PlantNet API containing genus, score, and species info | `result.hit()` returns top prediction |
| **PlantStandard** | Expert-verified reference specimen data (used for verification) | `hit.standards()` contains standards |
| **Treatment** | Scientific literature reference linked to a taxon | `species.treatments` provides research links |
| **L1 Cache** | In-memory Caffeine cache with TTL-based expiration | `caffeineCache.get(taxonId)` |
| **L2 Cache** | Database-backed persistent storage (no TTL) | `speciesRepository.findByTaxonId()` |

---

*Last updated: October 2026*
