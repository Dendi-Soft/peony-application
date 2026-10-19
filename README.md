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
- **RESTful endpoints** for image upload, recognition, and reference image retrieval
- **Free tier support**: Works out-of-the-box with public PlantNet API (5 requests/min)
- **Configurable authentication**: Optional API key for higher rate limits

## Plant Recognition Integration

See [docs/plant-recognition-README.md](./docs/plant-recognition-README.md) for complete documentation.

### Quick Test

Upload an image and get plant recognition:

```bash
# Example using curl with a base64-encoded image
curl -X POST http://localhost:8080/api/plantrecognition/upload \
  -H "Content-Type: application/json" \
  -d '{"imageData": "<base64-image-data>", "format": "image/jpeg"}'
```

## Architecture

Follows standard Spring Boot layered architecture:
- **Controllers**: `controller/plantrecognition/`
- **Services**: `service/plantrecognition/`  
- **Configuration**: `config/`
- **DTOs**: `dto/plantrecognition/`, `dto/ErrorResponse.java`

See [docs/architecture/plantnet-api-adr.md](./docs/architecture/plantnet-api-adr.md) for architectural decisions.

## Configuration

Set in `src/main/resources/application.properties`:

```properties
plantnet.api.key=your-key-here    # Optional, leave empty for free tier
plantnet.api.timeout=30000         # Request timeout (ms)
plantnet.default.language=en       # Response language
```

Or use environment variables:

```bash
export PLANTNET_API_KEY=your-key-here
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/plantrecognition/upload` | POST | Upload image for recognition |
| `/api/plantrecognition/recognize` | POST | Recognize (alternative endpoint) |
| `/api/plantrecognition/species/{taxonId}` | GET | Get species info by taxon ID |
| `/api/plantrecognition/search?q=...` | GET | Search taxa by name |

## Local Development Configuration

### Using application-local.yaml

The `src/main/resources/application-local.yaml` file provides:
- **Hot reloading** with Spring Boot DevTools
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

# Upload image for recognition
curl -X POST http://localhost:8080/api/plantrecognition/upload \
  -H "Content-Type: application/json" \
  -d '{"imageData": "<base64-image>", "format": "image/jpeg"}'
```

## License

See individual files for license information. PlantNet API usage is subject to their [Terms and Conditions](https://api.plantnet.org/#Terms-and-Conditions).

---
*Peony Application v0.0.1-SNAPSHOT*
