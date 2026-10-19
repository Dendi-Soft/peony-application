# Local Testing Setup - Summary

## What Was Created/Updated

### ✅ Files Created

1. **`docker-compose.yml`** (updated)
   - Full Docker Compose configuration with health checks
   - Hot-reload support for development
   - PostgreSQL 17 Alpine container
   - Network isolation
   - Port mappings (8080 and 5432)

2. **`src/main/resources/application-local.yaml`**
   - Local development overrides (base settings live in `application.yml`)
   - Debug logging enabled
   - Health endpoints exposed (`/actuator/health`)
   - Connection pooling optimized
   - All settings via environment variables

3. **`.env.example`**
   - Environment variable template
   - Default database credentials
   - Optional PlantNet API key field

4. **`.gitignore` updates needed** (already exists)

### ✅ Documentation Created

1. **`docs/local-testing-setup.md`** - Complete setup guide
2. **Updated `README.md`** - Added local testing instructions

## Quick Start Commands

### Option 1: Full Docker Compose (Recommended)

```bash
# 1. Copy environment template
cp .env.example .env

# 2. Edit .env if needed (optional):
#    - Set POSTGRES_PASSWORD=yourpassword
#    - Set PLANTNET_API_KEY=your-key (or leave empty for free tier)

# 3. Start with hot-reload
docker compose watch

# 4. Test health endpoint
curl http://localhost:8080/actuator/health
```

### Option 2: Run as Containers

```bash
cp .env.example .env
docker compose up -d
docker compose logs -f peony
```

### Option 3: Local Without Docker

```bash
# Edit application-local.yaml to point to your local database
# Then run directly with Spring Boot DevTools
./gradlew bootRun
```

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│         Peony Application (Port 8080)       │
│  • Hot Reload (Spring Boot DevTools)        │
│  • Health: /actuator/health                 │
│  • Debug Logging Enabled                    │
└─────────────────────────────────────────────┘
                      ↕
┌─────────────────────────────────────────────┐
│              Docker Compose                 │
│  ┌─────────────────┐     ┌────────────────┐ │
│  │     peony       │     │      db        │ │
│  │   (Spring Boot) │     │   (PostgreSQL) │ │
│  └─────────────────┘     └────────────────┘ │
└─────────────────────────────────────────────┘
```

## Environment Variables Reference

```bash
# Database Configuration
POSTGRES_URL=jdbc:postgresql://localhost:5432/peony
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=peony
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres  # Change this for security!

# PlantNet API (Optional)
PLANTNET_API_KEY=            # Leave empty for free tier
                              # Or set your key for higher limits

# Caching Configuration
CACHE_EXPIRE_MINUTES=60
CACHE_ACCESS_TTL_MINUTES=30

# Application
PORT=8080
APP_DEBUG_MODE=true
```

## Available Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Health check |
| `/actuator/info` | GET | Application info |
| `/actuator/metrics` | GET | Metrics |
| `/api/plantrecognition/upload` | POST | Upload image for recognition |
| `/api/plantrecognition/recognize` | POST | Alternative recognition endpoint |

## Useful Commands

```bash
# Start services with hot-reload
docker compose watch

# Run in background
docker compose up -d

# View logs
docker compose logs -f peony
docker compose logs -f db

# Stop services
docker compose down

# Restart database migrations
docker compose exec peony ./gradlew liquibaseUpdate

# Test health endpoint
curl http://localhost:8080/actuator/health

# Run tests locally
./gradlew test
```

## Configuration Files Structure

| File | Purpose |
|------|---------|
| `application.yml` | Base configuration (env-var overridable) |
| `application-local.yaml` | Local development (debug mode, health endpoints) |
| `application-dev.yaml` | Development mode (if needed later) |
| `application-prod.yaml` | Production configuration (if needed later) |

## Troubleshooting Quick Guide

**Database won't connect:**
```bash
docker compose ps          # Check if DB is running
docker compose logs db     # Check database logs
```

**Application won't start:**
```bash
docker compose logs peony  # Check app logs
docker compose exec peony ./gradlew clean bootJar --refresh-dependencies
```

**Port 8080 in use:**
```bash
lsof -i :8080             # Find what's using port 8080
# Change PORT in .env to another value like 8081
```

## Security Notes

1. **Change database password** - Don't use default `postgres` password in production
2. **Set PlantNet API key** - Use your own key for higher rate limits if needed
3. **Don't commit `.env`** - Keep it out of version control
4. **Health endpoints** - Consider restricting `/actuator/**` in production

## Next Steps

1. ✅ Copy `.env.example` to `.env`
2. ✅ Edit `.env` with your database password
3. ✅ Run `docker compose watch` for hot-reload development
4. ✅ Test at http://localhost:8080/actuator/health
5. ✅ Upload a test image via curl or Postman

## Additional Resources

- [Local Testing Setup Guide](./docs/local-testing-setup.md)
- [Plant Recognition Documentation](./docs/plant-recognition-README.md)
- [Architecture Decisions](./docs/adr/plantnet-api-adr.md)

---

**Happy testing! 🌸**
