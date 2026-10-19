# Local Testing Setup Guide

This guide covers different ways to test the Peony Application locally.

## Quick Start (Recommended)

### Using Docker Compose with Hot Reload

```bash
# 1. Copy environment template and set your values
cp .env.example .env

# 2. Edit .env file if needed (optional for defaults):
#    - POSTGRES_PASSWORD=postgres (default)
#    - PLANTNET_API_KEY= (leave empty for free tier)

# 3. Start services with hot-reload
docker compose watch

# Access the application
curl http://localhost:8080/actuator/health
```

### Running as Containers (Production-like)

```bash
docker compose up -d
# Check status
docker compose ps
# View logs
docker compose logs -f peony
docker compose logs -f db
```

## Manual Testing Without Docker

### Option 1: Use PostgreSQL locally

```bash
# Start PostgreSQL
docker run --name peony-postgres \
  -e POSTGRES_DB=peony \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -d postgres:17-alpine

# Or use your existing local database
```

Then update `.env`:
```bash
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=yourpassword
export POSTGRES_DB=peony
```

### Option 2: Use H2 Database (Development Only)

For quick testing without PostgreSQL:

Edit `src/main/resources/application-local.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    username: sa
    password: 
```

Note: Liquibase won't run with H2 in-memory, so skip migrations for this option.

## Configuration Files Explained

### application.yml (Default)
- Base configuration for all environments
- All settings overridable via environment variables
- Minimal defaults

### application-local.yaml (Local Testing)
- Debug logging enabled
- Health endpoints exposed
- Connection pooling optimized for development

### Docker Compose Setup

The `docker-compose.yml` includes:
- **peony**: Main Spring Boot application with hot-reload
- **db**: PostgreSQL database (version 17-alpine)
- **Health checks**: Automatic restart on service failure
- **Network isolation**: Separate network for services
- **Port mapping**: 8080 and 5432 exposed

## API Testing

### Using curl

```bash
# Health check
curl http://localhost:8080/actuator/health

# Get app info
curl http://localhost:8080/actuator/info

# Metrics (if enabled)
curl http://localhost:8080/actuator/metrics

# Plant recognition (multipart upload)
curl -X POST http://localhost:8080/api/plantrecognition/recognize \
  -F "file=@test-image.jpg" \
  -F "format=image/jpeg"
```

### Using Postman or cURL with a base64 image

`base64Image` is a query or form parameter (not a JSON body):

```bash
curl -X POST http://localhost:8080/api/plantrecognition/recognize \
  -F "base64Image=/9j/4AAQSkZJRg..." \
  -F "format=image/jpeg"
```

## Troubleshooting

### Database connection fails
1. Check if PostgreSQL is running: `docker compose ps`
2. Verify credentials in `.env`:
   ```bash
   echo $POSTGRES_USER
   echo $POSTGRES_PASSWORD
   ```
3. Check database logs: `docker compose logs db`

### Application won't start
1. Check for port conflicts:
   ```bash
   lsof -i :8080
   ```
2. View application logs:
   ```bash
   docker compose logs peony
   ```

### Rate limiting issues
- Free tier allows 5 requests/minute
- Set `PLANTNET_API_KEY` for higher limits
- The client automatically retries 429/503 responses with exponential backoff

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| POSTGRES_URL | - | JDBC connection string |
| POSTGRES_HOST | localhost | Database host |
| POSTGRES_PORT | 5432 | Database port |
| POSTGRES_DB | peony | Database name |
| POSTGRES_USER | postgres | Database user |
| POSTGRES_PASSWORD | postgres | Database password |
| PLANTNET_API_KEY | - | PlantNet API key (optional) |
| PORT | 8080 | Application port |

## Useful Commands

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# Restart with rebuild (code changes)
docker compose watch

# Run migration scripts
docker compose exec peony ./gradlew liquibaseUpdate

# View logs
docker compose logs -f
docker compose logs -f db
docker compose logs -f peony

# Test endpoint from inside container
docker compose exec peony curl http://localhost:8080/actuator/health
```

## Next Steps

1. Copy `.env.example` to `.env` and set your database password
2. Start with `docker compose watch` for development
3. Test the health endpoint at `http://localhost:8080/actuator/health`
4. Recognize a test image via curl or Postman

For more information, see:
- [Plant Recognition Documentation](./plant-recognition-README.md)
- [Architecture Decisions](./adr/plantnet-api-adr.md)
