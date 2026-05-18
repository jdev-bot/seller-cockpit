# =============================================================================
# Seller Cockpit Backend — Production Dockerfile
# Multi-stage build: builder → runtime
# =============================================================================

# ─────────────────────────────────────────────────────────────────────────────
# STAGE 1: Builder
# ─────────────────────────────────────────────────────────────────────────────
FROM gradle:8.10.1-jdk21 AS builder

WORKDIR /workspace

# Copy shared module + backend build files first for layer caching
COPY shared/domain-model /workspace/shared/domain-model
COPY backend/app/build.gradle.kts backend/app/settings.gradle.kts backend/app/gradle.properties ./
COPY backend/app/gradle ./gradle
COPY backend/app/gradlew ./

# Download dependencies (cached layer if build files don't change)
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

# Copy source
COPY backend/app/src ./src

# Build Quarkus fast-jar (production), skip tests (CI runs tests separately)
RUN ./gradlew build \
    -Dquarkus.package.type=fast-jar \
    -Dquarkus.package.jar.type=fast-jar \
    -x test \
    --no-daemon

# ─────────────────────────────────────────────────────────────────────────────
# STAGE 2: Runtime
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Install ffmpeg + imagemagick for media processing (required by the app)
RUN apk add --no-cache ffmpeg imagemagick

WORKDIR /app

# Copy Quarkus fast-jar output
COPY --from=builder --chown=appuser:appgroup /workspace/build/quarkus-app/ ./

USER appuser

EXPOSE 8080

# Health check (SmallRye Health endpoint)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/q/health || exit 1

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
