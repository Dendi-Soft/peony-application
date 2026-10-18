# Peony Application

Backend service with plant recognition capabilities via PlantNet API integration.

## Quick Start

### Installing and running the app

To install and run the app first clone the repository then start docker compose:

```sh
git clone git@github.com:Dendi-Soft/peony-application.git
cd peony-application
docker compose watch
```

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

## License

See individual files for license information. PlantNet API usage is subject to their [Terms and Conditions](https://api.plantnet.org/#Terms-and-Conditions).

---
*Peony Application v0.0.1-SNAPSHOT*
