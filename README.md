# seller-cockpit

> AI-powered seller cockpit for physical products you already own.

## Quick Start

### Prerequisites

- Docker + Docker Compose
- JDK 21 (for local backend dev)
- Node.js 20+ + npm (for mobile dev)

### Start Infrastructure

```bash
make dev
```

This starts PostgreSQL, MinIO, and Redis in Docker.

### Start Backend

```bash
make backend
```

The Quarkus dev server starts on http://localhost:8080 with hot reload.

### Start Mobile App

```bash
cd apps/mobile
npm install
npx expo start
```

Scan the QR code with the Expo Go app on your phone.

## API Endpoints

- `POST /api/product-cases` — create product case
- `GET /api/product-cases` — list cases
- `GET /api/product-cases/{id}` — get case
- `PATCH /api/product-cases/{id}` — update case
- `POST /api/product-cases/{id}/process-media` — run AI pipeline
- `POST /api/product-cases/{id}/research` — market research
- `POST /api/product-cases/{id}/pricing/recalculate` — pricing
- `POST /api/product-cases/{id}/listing-drafts/generate` — generate drafts
- `POST /api/product-cases/listing-drafts/{draftId}/publish` — publish
- `GET /api/dashboard` — seller cockpit dashboard

## Architecture

- **Mobile**: Expo (React Native) — cross-platform, camera-first
- **Backend**: Kotlin + Quarkus — JVM-native, fast startup
- **Storage**: PostgreSQL + MinIO (S3-compatible)
- **Queue**: Redis (for async jobs)
- **AI**: Mock pipeline for MVP; replace with real vision + LLM later

## Seller Modes

1. **Private Decluttering** — sell old items, expected payout focus
2. **Private Reselling** — flip items, profit and ROI focus
3. **Professional** — commercial selling, tax, margin, net profit

## Marketplace Support

- **eBay**: Official API adapter (OAuth, inventory, offers, publish, sync)
- **Kleinanzeigen**: Assisted publishing + manual tracking (no official API)

## Product Principles

1. Mobile-first
2. User owns the final decision
3. Marketplace-compliant automation only
4. Evidence-based pricing
5. Transparent calculations
6. Real product photos
7. Seller modes are first-class

## License

MIT — see [LICENSE](LICENSE)
