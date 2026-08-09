.PHONY: db-up db-down api frontend test build dataset

JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21 2>/dev/null)
export JAVA_HOME

db-up:
	docker compose up -d postgres

db-down:
	docker compose down

api:
	cd backend && ./mvnw -pl reconciliation-api spring-boot:run

frontend:
	cd frontend && pnpm dev

test:
	cd backend && ./mvnw verify
	cd frontend && pnpm test

build:
	cd backend && ./mvnw -DskipTests package
	cd frontend && pnpm build

dataset:
	cd backend && ./mvnw -Pdataset-generator -pl dataset-generator -am package
