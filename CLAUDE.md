# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ERP-VSD is a multi-tenant ERP system built as a Spring Boot microservices monorepo (Maven multi-module) with an Angular 21 frontend. Java 25, Spring Boot 4.x, Spring Cloud 2025.x.

## Modules

| Module | Port | Description |
|---|---|---|
| `registry` | 8761 | Eureka service discovery |
| `gateway` | 8090 | Spring Cloud Gateway MVC — JWT validation + routing |
| `auth-service` | 8085 | Auth, users, tenants, roles, permissions, Kafka audit |
| `cadastro-service` | 8086 | Master data CRUD (clients, products, suppliers, etc.) |
| `partner-service` | 8087 | Partner onboarding, approval, referral codes, trial engagement |
| `billing-service` | 8088 | Subscriptions, Asaas webhooks, commission payouts |
| `liquibase-service` | — | Standalone app that runs all Liquibase migrations on startup |
| `common` | — | Shared library: `GlobalExceptionHandler`, `BusinessException`, `AuditEventDTO`, `Constants` |
| `Angular/erp-front-end-web` | 4200 | Angular 21 SPA |

## Commands

### Backend (Maven)

```bash
# Build entire project (skip tests)
./mvnw clean install -DskipTests

# Run all tests with JaCoCo coverage check (minimum 40% instruction coverage)
./mvnw verify

# Run tests for a single module
./mvnw test -pl auth-service

# Run a single test class
./mvnw test -pl auth-service -Dtest=AuthControllerTest

# Run a specific service
./mvnw spring-boot:run -pl auth-service

# Build Docker image for a service
./mvnw spring-boot:build-image -pl auth-service -DskipTests

# Apply Liquibase migrations
./mvnw spring-boot:run -pl liquibase-service
```

### Infrastructure

```bash
# Start all infrastructure (PostgreSQL, Kafka, Zookeeper, monitoring stack)
docker compose up -d

# Start only essential infrastructure
docker compose up -d postgres zookeeper kafka

# Web UIs: Kafka UI :8080, Adminer :8081, Prometheus :9090, Grafana :3000, Kibana :5601
```

### Frontend

```bash
cd Angular/erp-front-end-web
npm start         # ng serve (port 4200)
npm test          # ng test (Vitest)
npm run build
```

## Architecture & Key Patterns

### Multi-tenancy

Every authenticated request carries tenant context. The gateway's `SecurityFilter` validates the JWT and injects `X-Tenant-Id` and `X-Is-Owner` as downstream request headers. In `cadastro-service`, `TenantInterceptor` reads the tenant ID from the Spring `SecurityContext` via `SecurityUtils.getCurrentTenantId()` and stores it in a `ThreadLocal` (`TenantContext`). Repositories then filter all queries by that tenant ID.

### Authentication Flow

1. Client calls `POST /auth/login` or `POST /auth/tenant/login` on the gateway (passes through unauthenticated).
2. `auth-service` validates credentials, checks brute-force lock, and issues a signed HS256 JWT (issuer: `L-ERP-auth-service`, 1 hour TTL) plus a refresh token stored in PostgreSQL.
3. JWT claims: `sub` (userId), `roles`, `authorities` (granular permissions), `isOwner`, `tenantId`, `tenantName`, `tenantCnpj`, `loginType`.
4. All subsequent requests hit the gateway's `SecurityFilter`, which verifies the JWT and injects headers before forwarding to downstream services.
5. Downstream services (`auth-service`, `cadastro-service`) have their own `SecurityConfig` but rely on the gateway's header injection — they do **not** re-validate JWT signatures; they read `X-Tenant-Id` from headers via `SecurityUtils`.

### JWT Secret

Configurado via variável de ambiente `JWT_SECRET` em ambos `auth-service` e `gateway`. O `TokenService` do auth-service valida no startup que o secret tem no mínimo 32 caracteres.

### Package Structure Convention

Both `auth-service` and `cadastro-service` follow the same layout:

```
api/
  controllers/   REST controllers (@RestController)
  dto/           Request/Response DTOs
  mappers/       MapStruct mappers (Entity ↔ DTO)
dominio| domain/ JPA entities
repositorios/    Spring Data JPA repositories
services/        Business logic
infra/
  config/        Spring configuration beans (Security, Kafka, etc.)
util/            Static utility classes
```

`auth-service` uses `dominio`/`repositorios` (Portuguese spelling); `cadastro-service` uses `domain`/`repository`.

### Database Migrations

All schema changes go through `liquibase-service`. Changelog files are in `liquibase-service/src/main/resources/db/changelog/`, organized by schema (`auth/`, `audit/`, `cadastro/`, `billing/`). The master file is `db.changelog-master.yaml`. **Never use `ddl-auto=create` or `ddl-auto=create-drop` in production** — schema is owned by Liquibase.

Both `auth-service` and `cadastro-service` run with `spring.jpa.hibernate.ddl-auto=validate` — Hibernate checks the schema against the entities but never alters it; Liquibase owns all DDL.

### Audit Trail

`auth-service` publishes `AuditEventDTO` (from `common`) to Kafka on security events (login, logout, user creation, etc.). `auth-service` also consumes audit events via `AuditConsumer` and persists them to the `audit` schema.

### Observability Stack

- Prometheus scrapes `/actuator/prometheus` from each service.
- Grafana (`:3000`, admin/admin123) for dashboards.
- Logback → Logstash (`:5000`) → Elasticsearch (`:9200`) → Kibana (`:5601`) for logs.
- Sentry BOM is managed in the parent POM (not yet wired per service).

### Required Environment Variables

`auth-service`:
- `DB_USER`, `DB_PASS` — PostgreSQL credentials
- `DB_HOST`, `DB_PORT` — defaults to `localhost:5432`
- `JWT_SECRET` — HMAC-256 signing secret
- `KAFKA_BROKERS` — e.g. `localhost:9092`
- `EMAIL_USER`, `EMAIL_PASSWORD` — Gmail SMTP

`cadastro-service`:
- `DB_USER`, `DB_PASS`, `DB_HOST`, `DB_PORT`
- `KAFKA_BROKERS`

### Test Strategy

Tests in `auth-service` use `@WebMvcTest` + `MockMvc` with Mockito for mocking service/repository layers. H2 is on the test classpath for repository-level tests. JaCoCo enforces ≥40% instruction coverage at `verify` phase, excluding DTOs, mappers, domain entities, and `@Configuration` classes. The test `WebSecurityConfig` replaces the production `SecurityConfig` with an in-memory user.

### CI/CD

CI/CD runs on **Jenkins + SonarQube** (TeamCity and Qodana were removed). The pipeline is defined in `Jenkinsfile` (declarative). Jenkins and SonarQube run via `compose.yaml`; the Jenkins controller image is built from `jenkins.Dockerfile`. Docker builds use a DinD daemon (`DOCKER_HOST=tcp://dind:2375`), and Testcontainers integration tests rely on `TESTCONTAINERS_HOST_OVERRIDE=dind`.

Pipeline stages: **Checkout → Build & Test (`mvnw clean verify` on the four services with `-am`) → SonarQube Analysis → Quality Gate (aborts on failure) → Docker Build & Push** (images `vitorff1234/<service>:<BUILD_NUMBER>` + `:latest` to Docker Hub). Only the four application services are built: `auth-service`, `cadastro-service`, `partner-service`, `billing-service`.

A local pre-push hook lives in `.githooks/pre-push` — enable once with `git config core.hooksPath .githooks`. It runs `./mvnw verify` only on the Java services touched by the push (changes under `common/` trigger a full verify; frontend/infra-only changes skip Java verify).

### Angular Frontend

Angular 21, standalone components (no NgModules). Pages in `src/app/pages/` (`login`, `cadastros`, `home`). Shared components in `src/app/components/` (`primary-button`, `primary-input`, `table`, `web-layout`, `default-login-layout`). Prettier is configured in `package.json` (100 char width, single quotes).

**Never use `[innerHTML]` with data that originates from user input or API responses.** Use Angular's template binding (`{{ }}`) instead, which escapes by default. If rich text rendering is truly required, sanitize explicitly with `DomSanitizer.sanitize(SecurityContext.HTML, value)` before binding.

## Workflow Directives

After every set of code changes, propose a short commit message in the format:

```
<type>: <short description>

- bullet summarizing each changed file/area
```

Types: `fix` (bug/security), `feat` (new feature), `refactor`, `chore` (config/tooling), `docs`.
