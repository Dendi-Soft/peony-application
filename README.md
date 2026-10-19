# Peony Application

Backend service with plant recognition capabilities via PlantNet API integration.

## Quick Start

### Installing and running the app

To install and run the app first clone the repository then start docker compose:

```sh
git clone git@github.com:Dendi-Soft/peony-application.git
cd peony-application
# Copy environment variables (optional)
cp .env.example .env
# Start services with hot-reload
docker compose watch
```

### Using the application

Once started, access:
- **Application**: http://localhost:8080
- **Database (PostgreSQL)**: localhost:5432

### Testing locally without Docker

You can also run locally without Docker by editing `application-local.yaml` and setting your own database connection.

## Features

- **Plant Recognition API**: Integrated with [PlantNet](https://plantnet.org/) for species-level plant identification
- **Recognition endpoint** accepting multipart file or base64-encoded images (JPEG, PNG, WebP)
- **Free tier support**: Works out-of-the-box with the public PlantNet API (5 requests/min)
- **Configurable authentication**: Optional API key for higher rate limits
- **Automatic retries** with exponential backoff on rate-limited (429) or unavailable (503) responses

## Plant Recognition Integration

See [docs/plant-recognition-README.md](./docs/plant-recognition-README.md) for complete documentation.

### Quick Test

Upload an image and get plant recognition:

```bash
# Multipart file upload
curl -X POST http://localhost:8080/api/plantrecognition/recognize \
  -F "file=@plant.jpg" \
  -F "format=image/jpeg"

# Or base64-encoded image
curl -X POST http://localhost:8080/api/plantrecognition/recognize \
  -F "base64Image=<base64-image-data>" \
  -F "format=image/jpeg"
```

## Architecture

Follows standard Spring Boot layered architecture:
- **Controllers**: `controller/plantrecognition/`
- **Services**: `service/plantrecognition/`
- **Configuration**: `config/`
- **DTOs**: `dto/plantrecognition/`
- **Exceptions**: `exception/`

See [docs/adr/plantnet-api-adr.md](./docs/adr/plantnet-api-adr.md) for architectural decisions.

## Configuration

Set in `src/main/resources/application.yml`:

```yaml
plantnet:
  api-key: ${PLANTNET_API_KEY:}   # Optional, leave empty for free tier
  retry-attempts: 5               # Max attempts per request (429/503 retries)
  retry-base-delay-ms: 1000       # Initial backoff delay, doubled per retry
```

Or use environment variables:

```bash
export PLANTNET_API_KEY=your-key-here
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/plantrecognition/recognize` | POST | Recognize a plant from an uploaded image (multipart `file` or base64 `base64Image`) |

Interactive API documentation is available at `/swagger-ui.html` when the app is running.

## Local Development Configuration

### Using application-local.yaml

The `src/main/resources/application-local.yaml` file provides:
- **Debug logging** for troubleshooting
- **Health check endpoints** at `/actuator/health`
- **Connection pooling** optimized for development

### Environment Variables

Use `.env` file or export variables directly:

```bash
# Required environment variables
export POSTGRES_URL=jdbc:postgresql://localhost:5432/peony
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=yourpassword
export POSTGRES_DB=peony

export PLANTNET_API_KEY=  # Optional, empty for free tier
```

### Running with Docker Compose

Start all services (app + database):
```bash
# Hot-reload mode (recommended for development)
docker compose watch

# Or run as containers
docker compose up -d
```

Stop services:
```bash
docker compose down
```

### Testing endpoints

Once the app is running, test the API:

```bash
# Health check
curl http://localhost:8080/actuator/health

# Recognize a plant from an image
curl -X POST http://localhost:8080/api/plantrecognition/recognize \
  -F "file=@plant.jpg" \
  -F "format=image/jpeg"
```

## License

See individual files for license information. PlantNet API usage is subject to their [Terms and Conditions](https://api.plantnet.org/#Terms-and-Conditions).

---
*Peony Application v0.0.1-SNAPSHOT*
