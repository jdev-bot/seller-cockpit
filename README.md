# Seller Cockpit 🚀

**AI-powered mobile-first selling platform.**

Capture product photos → AI extracts facts → researches comparable listings → generates optimized titles/descriptions → prices intelligently → publishes to eBay & Kleinanzeigen.

Built for: private declutterers, resellers, and professional sellers who want to minimize manual listing effort.

---

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Mobile App    │────▶│  Quarkus API    │────▶│   PostgreSQL  │
│   (Expo / RN)   │     │   (Kotlin/JVM)  │     │   (Primary DB)  │
└─────────────────┘     └────────┬────────┘     └─────────────────┘
       │                           │
       │                           ├────▶ Redis (Cache, Pub/Sub)
       │                           ├────▶ MinIO (S3 Media Storage)
       │                           └────▶ AI Providers (OpenAI/Anthropic)
       │
       └────▶ Firebase Auth (Google Sign-In / Email)
              Push Notifications (FCM)
              eBay OAuth
              Kleinanzeigen (Manual/Assisted)
```

---

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Mobile** | Expo (React Native) | Cross-platform iOS/Android app |
| **Backend** | Quarkus 3.15 + Kotlin | Fast, low-memory JVM API |
| **Database** | PostgreSQL 16 + Flyway | Schema migrations, ACID data |
| **Cache** | Redis 7 | Session cache, job queues |
| **Storage** | MinIO | S3-compatible object storage for media |
| **Auth** | Firebase Authentication | JWT identity, Google/Email sign-in |
| **AI** | OpenAI GPT-4o / Anthropic Claude | Vision extraction, research, pricing |
| **Marketplaces** | eBay REST API / Kleinanzeigen | Direct + assisted publishing |
| **DevOps** | Docker Compose, Traefik, GHCR | Local dev + production deployment |

---

## Prerequisites

- **Docker + Compose v2.20+**
- **JDK 21** (for backend builds)
- **Node 20+ + npm** (for mobile)
- **Expo CLI** (`npm install -g @expo/cli`)

---

## Quick Start (Local Development)

### 1. Clone & Configure

```bash
git clone https://github.com/jdev-bot/seller-cockpit.git
cd seller-cockpit

# Backend + Infrastructure
cp infra/.env.example infra/.env
# Edit infra/.env with your secrets
```

### 2. Start the Backend Stack

```bash
cd infra
./start.sh
```

Services available:
- API: http://localhost:8080
- API Health: http://localhost:8080/q/health
- API Docs: http://localhost:8080/q/openapi
- MinIO Console: http://localhost:9001
- Postgres: localhost:5432
- Redis: localhost:6379

### 3. Start the Mobile App

```bash
cd apps/mobile
npm install
npx expo start
```

Scan the QR code with Expo Go (iOS/Android) or press `a` for Android emulator / `i` for iOS simulator.

---

## Project Structure

```
seller-cockpit/
├── apps/
│   └── mobile/              # Expo React Native app
│       ├── app/             # Expo Router screens
│       ├── components/      # Reusable UI (Skeleton, EmptyState, etc.)
│       └── hooks/           # useAuth, useApi
├── backend/
│   └── app/                 # Quarkus Kotlin backend
│       ├── src/main/kotlin/ # API resources, services, domain
│       ├── src/test/kotlin/ # Integration tests (REST-assured)
│       └── src/main/resources/
│           ├── application.properties
│           └── db/migration/ # Flyway SQL migrations
├── shared/
│   └── domain-model/        # Shared Kotlin domain models
├── infra/
│   ├── docker-compose.yml           # Local dev stack
│   ├── docker-compose.prod.yml      # Production (Traefik + TLS)
│   ├── .env.example                 # Config template
│   ├── DEPLOYMENT.md                # Detailed deployment guide
│   └── start.sh                     # One-command bootstrap
└── .github/workflows/
    └── ci.yml              # Build + test + push to GHCR
```

---

## Authentication

### Mobile → Firebase → Backend Flow

```
┌────────┐    ┌────────────┐    ┌──────────────┐    ┌──────────┐
│ Mobile │───▶│ Firebase   │───▶│ ID Token     │───▶│ Backend  │
│        │    │ Auth (Email│    │ (JWT)        │    │ (Verify) │
│        │    │ / Google)  │    │              │    │          │
└────────┘    └────────────┘    └──────────────┘    └──────────┘
```

1. **Mobile**: User signs in via email/password or Google → receives Firebase ID token
2. **Mobile**: Stores token in AsyncStorage, sends `Authorization: Bearer ***` with every API request
3. **Backend**: `FirebaseAuthFilter` validates token (signature + claims) and injects `AuthenticatedUser`
4. **Backend**: Resources access user via `@Context user: AuthenticatedUser`

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `FIREBASE_PROJECT_ID` | ✅ | Your Firebase project ID |
| `FIREBASE_JWKS_URL` | Production | Google cert endpoint for signature validation |
| `EXPO_PUBLIC_FIREBASE_API_KEY` | ✅ Mobile | Firebase Web API key |

---

## API Overview

### Key Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/auth/verify` | POST | Optional | Verify token, get/create user |
| `/api/product-cases` | GET/POST | ✅ | List / create product cases |
| `/api/product-cases/{id}` | GET/PATCH/DELETE | ✅ | CRUD single case |
| `/api/product-cases/{id}/media/upload-url` | POST | ✅ | Get presigned MinIO URL |
| `/api/product-cases/{id}/process-media` | POST | ✅ | Trigger AI vision pipeline |
| `/api/product-cases/{id}/research` | POST/GET | ✅ | Run / get market research |
| `/api/product-cases/{id}/pricing/recalculate` | POST | ✅ | AI price recommendation |
| `/api/product-cases/{id}/listing-drafts/generate` | POST | ✅ | AI generate drafts |
| `/api/marketplaces/ebay/connect-url` | GET | ✅ | Get eBay OAuth URL |
| `/api/marketplaces/ebay/publish/{draftId}` | POST | ✅ | Publish to eBay |
| `/api/marketplaces/ebay/fees` | POST | Public | Estimate eBay fees |
| `/api/marketplaces/kleinanzeigen/assisted-publish/{id}` | POST | ✅ | Assisted publish flow |

### OpenAPI Schema

Available at `/q/openapi` when the API is running.

---

## AI Pipeline

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ Media Upload │───▶│ Vision       │───▶│ Research     │───▶│ Pricing      │
│ (Images/Video)│   │ Extraction   │    │ & Comparison │    │ Engine       │
└──────────────┘    └──────────────┘    └──────────────┘    └──────┬───────┘
                                                                  │
                    ┌──────────────┐    ┌──────────────┐         │
                    │ eBay Publish │◄───│ Listing      │◄────────┘
                    │ (Direct)     │    │ Generator    │
                    └──────────────┘    └──────────────┘
```

### Supported AI Providers

| Provider | Config Key | Model Default |
|----------|-----------|---------------|
| OpenAI | `OPENAI_API_KEY` | `gpt-4o` |
| Anthropic | `ANTHROPIC_API_KEY` | `claude-3-opus-20240229` |
| Google Gemini | `GEMINI_API_KEY` | `gemini-1.5-pro-latest` |

Set `AI_MOCK_ENABLED=true` for local dev without API keys (returns placeholder data).

---

## Marketplace Integration

### eBay (Direct Publishing)

1. **Connect**: Mobile opens OAuth URL → user authorizes on eBay → deep-link callback exchanges code for token
2. **Publish**: Backend calls eBay Inventory API (Inventory Item + Offer) with user's encrypted token
3. **Sync**: Background job polls eBay for status updates

Required env vars:
- `EBAY_CLIENT_ID`, `EBAY_CLIENT_SECRET`
- `EBAY_REDIRECT_URI` (must match eBay Developer Console)

### Kleinanzeigen (Assisted Publishing)

No API available. The app generates optimized copy and opens the Kleinanzeigen listing form with pre-filled data via deep link.

---

## Testing

### Backend Integration Tests

```bash
cd backend/app
./gradlew test
```

Tests cover:
- Auth filter (public path access, token validation, 401 rejection)
- Product case CRUD
- Marketplace endpoints
- eBay service fee estimation

### Mobile

```bash
cd apps/mobile
npm test        # Jest unit tests
npx expo start  # Manual E2E testing
```

---

## Production Deployment

### Docker Compose (Recommended)

```bash
cd infra
cp .env.example .env
# Fill: domains, API keys, passwords, encryption key
docker compose -f docker-compose.prod.yml up -d
```

Features:
- **Traefik** reverse proxy with Let's Encrypt auto-TLS
- **MinIO** S3 storage with dedicated domain
- **Postgres** with persistent volume
- **Redis** with password protection
- **API** pre-built image from GHCR

See `infra/DEPLOYMENT.md` for detailed production guide.

### CI/CD

GitHub Actions workflow (`.github/workflows/ci.yml`):
1. Runs `./gradlew test` on every PR
2. Builds Docker image on merge to `main`
3. Pushes to `ghcr.io/jdev-bot/seller-cockpit-api:latest`

---

## Environment Variables Reference

### Required for Production

| Variable | Example | Purpose |
|----------|---------|---------|
| `POSTGRES_PASSWORD` | `changeme` | Database password |
| `MINIO_ROOT_PASSWORD` | `changeme` | Object storage password |
| `REDIS_PASSWORD` | `changeme` | Cache password |
| `TOKEN_ENCRYPTION_KEY` | `openssl rand -hex 32` | AES-256 key for eBay tokens |
| `API_HOST` | `api.example.com` | Public API domain |
| `OPENAI_API_KEY` | `sk-...` | AI provider |
| `EBAY_CLIENT_ID` | `your-id` | eBay app credentials |
| `EBAY_CLIENT_SECRET` | `your-secret` | eBay app secret |
| `FIREBASE_PROJECT_ID` | `my-project` | Firebase auth |

Full list: `infra/.env.example`

---

## Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feat/my-feature`)
3. Commit with conventional commits (`feat:`, `fix:`, `infra:`, `ui:`)
4. Push and open a PR

### Conventional Commit Tags

| Tag | Use For |
|-----|---------|
| `feat` | New features |
| `fix` | Bug fixes |
| `infra` | Docker, CI, deployment |
| `ui` | Mobile UI changes |
| `docs` | Documentation |
| `test` | Tests only |

---

## License

MIT — see [LICENSE](LICENSE) for details.

---

## Support

- Issues: [GitHub Issues](https://github.com/jdev-bot/seller-cockpit/issues)
- Docs: This README + `infra/DEPLOYMENT.md`
- API: Run locally and visit `/q/openapi`

---

<p align="center">Built with ❤️ by the Seller Cockpit team</p>
