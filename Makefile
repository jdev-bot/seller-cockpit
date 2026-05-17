# Build / Run
.PHONY: dev

dev:
	docker compose -f infra/docker-compose.yml up -d

.PHONY: backend
backend:
	cd backend/app && ./gradlew quarkusDev

.PHONY: mobile
mobile:
	cd apps/mobile && npm start

# Gradle bootstrap for CI
.PHONY: bootstrap-gradle
bootstrap-gradle:
	cd backend/app && ./gradlew wrapper --gradle-version 8.10.1 || true

# Test
.PHONY: test
test:
	cd backend/app && ./gradlew test

# DB
.PHONY: db-migrate
db-migrate:
	cd backend/app && ./gradlew flywayMigrate

# Clean
.PHONY: clean
clean:
	docker compose -f infra/docker-compose.yml down -v
	rm -rf backend/app/build backend/app/.gradle
	rm -rf shared/domain-model/build shared/domain-model/.gradle
	rm -rf apps/mobile/node_modules apps/mobile/.expo
