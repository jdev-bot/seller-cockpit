# Seller Cockpit — Deployment Guide

## Quick Start (Local Development)

```bash
# 1. Configure environment
cp infra/.env.example infra/.env
# Edit infra/.env with your secrets

# 2. Start the stack
cd infra
docker compose up --build

# 3. Verify
open http://localhost:8080/q/health      # App health
open http://localhost:8080/q/openapi    # API docs
open http://localhost:9001              # MinIO console (minioadmin / minioadmin)
```

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Mobile    │────▶│   Traefik   │────▶│  Quarkus    │
│   (Expo)    │     │  (reverse   │     │   API       │
└─────────────┘     │   proxy)    │     └──────┬──────┘
                    └─────────────┘            │
                                               │
                    ┌─────────────┐     ┌──────┴──────┐
                    │   MinIO     │     │  PostgreSQL │
                    │   (S3)      │     │    (16)     │
                    └─────────────┘     └─────────────┘
                    ┌─────────────┐
                    │    Redis    │
                    │   (cache)   │
                    └─────────────┘
```

## Services

| Service | Port | Purpose | Credentials |
|---------|------|---------|-------------|
| API | 8080 | Quarkus backend | — |
| Postgres | 5432 | Primary database | from `.env` |
| MinIO API | 9000 | S3-compatible storage | from `.env` |
| MinIO Console | 9001 | Web admin UI | from `.env` |
| Redis | 6379 | Cache / pub-sub | from `.env` |

## Production Deployment

### Prerequisites

- Ubuntu 22.04+ server with Docker + Compose v2.20+
- Domain name with DNS A record pointing to server
- Ports 80 and 443 open

### Step-by-step

```bash
# 1. Clone repo on server
git clone https://github.com/jdev-bot/seller-cockpit.git
cd seller-cockpit/infra

# 2. Configure production secrets
cp .env.example .env
# Edit .env:
#   - Set strong passwords
#   - Set your domain names (API_HOST, MINIO_HOST)
#   - Fill AI provider API keys
#   - Fill eBay credentials
#   - Generate TOKEN_ENCRYPTION_KEY: openssl rand -hex 32

# 3. Start production stack
docker compose -f docker-compose.prod.yml up -d

# 4. Verify
docker compose -f docker-compose.prod.yml ps
curl https://api.yourdomain.com/q/health
```

### TLS / HTTPS

Traefik automatically obtains Let's Encrypt certificates. No manual cert management required.

### Database Backups

Add a cron job on the host:

```bash
# /etc/cron.daily/seller-cockpit-backup
#!/bin/bash
docker exec sc-postgres pg_dump -U postgres sellercockpit | gzip > /backups/seller-cockpit-$(date +%Y%m%d).sql.gz
```

## CI / CD (GitHub Actions)

The repo includes `.github/workflows/ci.yml` which:

1. Builds the backend on every push to `main`
2. Runs `./gradlew test`
3. Builds and pushes Docker image to GHCR
4. Updates `docker-compose.prod.yml` image tag

Required repository secrets:
- `GHCR_TOKEN` — GitHub personal access token with `write:packages` scope
- `SSH_HOST`, `SSH_USER`, `SSH_KEY` — for deployment (optional)

## Health Checks

| Endpoint | Description |
|----------|-------------|
| `/q/health` | Overall health (UP/DOWN) |
| `/q/health/live` | Liveness probe |
| `/q/health/ready` | Readiness probe |
| `/q/metrics` | Prometheus metrics |
| `/q/openapi` | OpenAPI schema |

## Troubleshooting

**API fails to start — `Connection refused` to Postgres**
> Wait for `postgres` healthcheck to pass. The API has `depends_on` with `condition: service_healthy`.

**MinIO bucket not found**
> The `minio-init` service auto-creates the bucket on first startup. If it fails, run manually:
> ```bash
> docker compose exec minio mc mb local/seller-cockpit-media --ignore-existing
> ```

**eBay OAuth callback fails in production**
> Ensure `EBAY_REDIRECT_URI` exactly matches the eBay Developer Console registered redirect URI, including `https://`.

## Environment Variables Reference

See `infra/.env.example` for the full list. Key required variables for production:

- `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`, `REDIS_PASSWORD`
- `TOKEN_ENCRYPTION_KEY` (generate with `openssl rand -hex 32`)
- `API_HOST`, `MINIO_HOST` (your domain names)
- `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` (for AI pipeline)
- `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET` (for eBay marketplace)
- `FIREBASE_PROJECT_ID` (for mobile auth)
