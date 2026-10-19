#!/bin/bash
# ===========================================
# Peony Application - Local Testing Script
# ===========================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}"
echo "╔════════════════════════════════════════════════════════╗"
echo "║    Peony Application - Local Testing Setup             ║"
echo "╚════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Colors for output
GREEN() { echo -e "${GREEN}$1${NC}"; }
RED() { echo -e "${RED}$1${NC}"; }
YELLOW() { echo -e "${YELLOW}$1${NC}"; }
BLUE() { echo -e "${BLUE}$1${NC}"; }

# Check if .env exists
if [ ! -f .env ]; then
    YELLOW "No .env file found. Copying from .env.example..."
    cp .env.example .env
    GREEN ".env created successfully!"
    echo ""
    YELLOW "Edit .env file with your credentials before proceeding:"
    YELLOW "  - POSTGRES_PASSWORD"
    YELLOW "  - PLANTNET_API_KEY (optional, empty for free tier)"
else
    GREEN ".env file already exists!"
fi

echo ""
echo -e "${YELLOW}Commands to run Peony Application locally:${NC}"
echo "  ${BLUE}1.${NC} docker compose watch     # Start with hot-reload (recommended)"
echo "  ${BLUE}2.${NC} docker compose up -d    # Run in background"
echo ""
echo -e "${YELLOW}To stop services:${NC}"
echo "  ${BLUE}docker compose down${NC}"
echo ""
echo -e "${GREEN}Quick test commands:${NC}"
echo "  curl http://localhost:8080/actuator/health    # Health check"
echo "  curl http://localhost:8080/actuator/info     # App info"
echo ""

# Wait for user to edit .env if needed
if [ ! -f .env ] || grep -q "^#" "$0"; then
    echo "Press Ctrl+C when ready to start (or if .env is already configured)"
fi

# Start services
echo -e "${GREEN}Starting Peony Application and PostgreSQL database...${NC}"
docker compose up -d

echo ""
YELLOW "Waiting for services to be ready..."
sleep 10

# Check if services are running
echo ""
BLUE "Checking service status..."
docker compose ps

echo ""
BLUE "Testing health endpoint..."
HEALTH_RESPONSE=$(curl -s http://localhost:8080/actuator/health || echo "")
if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"'; then
    GREEN "✅ Application is healthy!"
else
    YELLOW "⚠️  Application might still be starting..."
fi

echo ""
BLUE "Application available at: http://localhost:8080"
GREEN "Health endpoint: http://localhost:8080/actuator/health"

# Show logs in background
YELLOW "Press Ctrl+C to stop services"
echo -e "${YELLOW}View logs with:${NC}"
echo -e "${BLUE}docker compose logs -f${NC}"
echo ""
