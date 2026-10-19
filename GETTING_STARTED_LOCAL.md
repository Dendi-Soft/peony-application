# Getting Started with Local Testing 🌸

Welcome to the Peony Application! This guide will get you up and running in minutes.

## Step 1: Copy Environment Variables

```bash
cp .env.example .env
```

Edit `.env` with your credentials:
- `POSTGRES_PASSWORD`: Set a secure password (default: `postgres`)
- `PLANTNET_API_KEY`: Leave empty for free tier, or add your key

## Step 2: Start the Application

### Option A: Hot Reload (Recommended for Development)
```bash
docker compose watch
```
This will automatically rebuild when you change code!

### Option B: Run in Background
```bash
docker compose up -d
```

## Step 3: Verify Everything is Running

```bash
# Check service status
docker compose ps

# View logs
docker compose logs -f peony
docker compose logs -f db
```

## Step 4: Test the Application

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

You should see:
```json
{
  "status": "UP",
  "timestamp": "2026-10-18T..."
}
```

## Step 5: Test Plant Recognition

Create a test JSON file `test-image.json`:
```json
{
  "imageData": "/9j/4AAQSkZJRgABAQEASABIAAD...",
  "format": "image/jpeg"
}
```

Upload and get recognition:
```bash
curl -X POST http://localhost:8080/api/plantrecognition/upload \
  -H "Content-Type: application/json" \
  -d @test-image.json
```

## What's Available

### Services
- **Peony App**: Running on `http://localhost:8080`
- **PostgreSQL Database**: Running on `localhost:5432`

### Endpoints
| URL | Method | Purpose |
|-----|--------|---------|
| `/actuator/health` | GET | Health check |
| `/actuator/info` | GET | Application info |
| `/api/plantrecognition/upload` | POST | Upload image for recognition |
| `/api/plantrecognition/recognize` | POST | Alternative recognition |
| `/api/plantrecognition/species/{taxonId}` | GET | Get species by ID |

### Useful Commands
```bash
# Start with hot-reload
docker compose watch

# Run in background
docker compose up -d

# View logs
docker compose logs -f

# Stop services
docker compose down

# Restart database (if needed)
docker compose exec db pg_ctl restart

# Run tests locally
./gradlew test
```

## Troubleshooting

### Port 8080 already in use?
```bash
lsof -i :8080
# Kill the process or change port in .env: PORT=8081
```

### Database connection fails?
```bash
docker compose exec db pg_isready
# Or check logs: docker compose logs db
```

### Application won't start?
```bash
docker compose exec peony ./gradlew clean bootJar --refresh-dependencies
```

## Development Workflow

1. **Make code changes** in `src/` directory
2. **Save file** - Docker will automatically rebuild on changes
3. **Test at localhost:8080**
4. **View logs**: `docker compose logs peony -f`

## Environment Variables Quick Reference

```bash
# Required for database connection
export POSTGRES_URL=jdbc:postgresql://localhost:5432/peony
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=yourpassword
export POSTGRES_DB=peony

# Optional: PlantNet API key for higher rate limits
export PLANTNET_API_KEY=your-key-here

# Caching defaults (can be overridden)
CACHE_EXPIRE_MINUTES=60
CACHE_ACCESS_TTL_MINUTES=30
```

## Next Steps

1. ✅ Copy `.env.example` to `.env` and configure it
2. ✅ Run `docker compose watch`
3. ✅ Verify health endpoint works
4. ✅ Test with a sample image
5. 🌸 **Start developing!**

---

Need help? Check out:
- [Local Testing Setup Guide](./docs/local-testing-setup.md)
- [Plant Recognition Documentation](./docs/plant-recognition-README.md)

Happy coding! 🚀
