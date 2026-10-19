# Swagger/OpenAPI Configuration

This project uses SpringDoc OpenAPI to generate API documentation.

## Overview

Swagger is configured to provide interactive API documentation at:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

## Configuration Files

### 1. `build.gradle` - Dependencies

The OpenAPI/Swagger dependency has been added to the build file:

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
```

### 2. `SwaggerConfig.java` - OpenAPI Bean

Located at `src/main/java/com/dendi/peony/config/SwaggerConfig.java`:

- Configures the OpenAPI info section with title and description
- Sets up server URLs (local and production)
- Provides API metadata for Swagger UI

### 3. `application.yml` - SpringDoc Settings

Key configurations in `src/main/resources/application.yml`:

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
    display-request-duration: true
```

## Controller Annotations

The `PlantRecognitionController` has been annotated with Swagger metadata:

### Controller Tag

```java
@Tag(name = "Plant Recognition", description = "Endpoints for recognizing plants from images")
```

### Endpoint Operations

Each endpoint includes:

- **@Operation** - Summary and detailed description
- **@ApiResponses** - HTTP response codes and descriptions
- **@Parameter** - Parameter documentation (name, description, required, examples)

## Available Endpoints

| Method | Path | Description | Parameters |
|--------|------|-------------|------------|
| POST | `/api/v1/plantrecognition/recognize` | Recognize plant from image | `file` (multipart, optional), `base64Image` (optional), `format` (MIME type, default `image/jpeg`) |

## Building the Application

```bash
./gradlew build
```

Or run directly:

```bash
./gradlew bootRun
```

Then visit `http://localhost:8080/swagger-ui.html` to view the API documentation.

## Notes

- Swagger UI displays request duration when enabled
- Tags are sorted alphabetically
- Operations are shown in operation order by default
