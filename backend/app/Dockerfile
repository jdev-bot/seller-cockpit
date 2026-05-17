# Build stage
FROM gradle:8.10.1-jdk21 AS builder
WORKDIR /app
COPY backend/app/build.gradle.kts backend/app/settings.gradle.kts backend/app/gradle.properties ./
COPY backend/app/src ./src
COPY shared/domain-model ../shared/domain-model
RUN gradle build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/quarkus-app/ .
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
