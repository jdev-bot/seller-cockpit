#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "=== Seller Cockpit — Local Dev Bootstrap ==="

# Ensure .env exists
if [[ ! -f .env ]]; then
    echo "Creating .env from example..."
    cp .env.example .env
    echo "⚠️  Please edit .env and set your secrets before continuing."
    exit 1
fi

echo "Starting Docker Compose stack..."
docker compose up --build -d

echo ""
echo "Waiting for services to be healthy..."
sleep 5

docker compose ps

echo ""
echo "=== Services ==="
echo "API:       http://localhost:8080"
echo "Health:    http://localhost:8080/q/health"
echo "OpenAPI:   http://localhost:8080/q/openapi"
echo "MinIO:     http://localhost:9001 (minioadmin / \${MINIO_ROOT_PASSWORD})"
echo "Postgres:  localhost:5432"
echo "Redis:     localhost:6379"
echo ""
echo "To stop: docker compose down"
echo "To logs: docker compose logs -f api"
