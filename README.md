# ERP-VSD

ERP multi-tenant construído como monorepo de microserviços Spring Boot (Maven multi-módulo) com frontend Angular. **Java 25**, **Spring Boot 4.x**, **Spring Cloud 2025.x**.

## 📦 Módulos

| Módulo | Porta | Descrição |
|---|---|---|
| `registry` | 8761 | Eureka service discovery |
| `gateway` | 8090 | Spring Cloud Gateway MVC — validação de JWT + roteamento |
| `auth-service` | 8085 | Autenticação, usuários, tenants, roles, permissões, auditoria via Kafka |
| `cadastro-service` | 8086 | CRUD de cadastros básicos (clientes, produtos, fornecedores, etc.) |
| `partner-service` | 8087 | Onboarding de parceiros, aprovação, códigos de indicação, engajamento de trial |
| `billing-service` | 8088 | Assinaturas, webhooks Asaas, repasse de comissões |
| `liquibase-service` | — | App standalone que roda todas as migrações Liquibase no startup |
| `common` | — | Lib compartilhada: `GlobalExceptionHandler`, `BusinessException`, `AuditEventDTO`, `Constants` |
| `Angular/erp-front-end-web` | 4200 | SPA Angular 21 |

## 🏗️ Arquitetura

- **Multi-tenancy:** o gateway valida o JWT e injeta `X-Tenant-Id` / `X-Is-Owner` como headers para os serviços downstream. O `cadastro-service` lê o tenant via `SecurityContext` e filtra todas as queries por ele.
- **Autenticação:** `auth-service` valida credenciais e emite JWT HS256 (TTL 1h) + refresh token persistido no PostgreSQL com rotação e detecção de reuso. Serviços downstream confiam nos headers injetados pelo gateway (não revalidam a assinatura do JWT).
- **Migrações:** todo o schema é versionado no `liquibase-service` (`db/changelog/`, organizado por `auth/`, `audit/`, `cadastro/`, `billing/`). Os serviços rodam com `ddl-auto=validate` — Liquibase é dono de todo o DDL.
- **Auditoria:** `auth-service` publica `AuditEventDTO` no Kafka em eventos de segurança e os consome via `AuditConsumer`, persistindo no schema `audit`.
- **Observabilidade:** Prometheus + Grafana para métricas; Logback → Logstash → Elasticsearch → Kibana para logs.

## 🛠️ Stack

- **Java 25**, **Spring Boot 4.x**, **Spring Cloud 2025.x** (Gateway MVC, Eureka)
- **PostgreSQL 17** — dados relacionais
- **Apache Kafka** — mensageria de auditoria e eventos entre serviços
- **Liquibase** — versionamento de schema
- **MapStruct** + **Lombok** — mapeamento DTO ↔ entidade
- **Asaas** — integração de pagamentos (assinaturas, webhooks)
- **Passay** + **OWASP HTML Sanitizer** — política de senha e prevenção de XSS
- **Angular 21** — SPA standalone (sem NgModules)

## 🚀 Como Executar

### Infraestrutura

```bash
# Toda a infra (PostgreSQL, Kafka, Zookeeper, stack de monitoramento)
docker compose up -d

# Apenas o essencial
docker compose up -d postgres zookeeper kafka
```

UIs: Kafka UI `:8080`, Adminer `:8081`, Prometheus `:9090`, Grafana `:3000`, Kibana `:5601`.

### Migrações

```bash
./mvnw spring-boot:run -pl liquibase-service
```

### Backend

```bash
# Build completo (sem testes)
./mvnw clean install -DskipTests

# Rodar todos os testes com checagem de cobertura JaCoCo
./mvnw verify

# Rodar um serviço específico
./mvnw spring-boot:run -pl auth-service
```

### Frontend

```bash
cd Angular/erp-front-end-web
npm start        # ng serve (porta 4200)
npm test         # Vitest
npm run build
```

## ✅ CI/CD

Pipeline em **Jenkins + SonarQube** (definido no `Jenkinsfile` declarativo). Estágios: Checkout → Build & Test (`mvnw clean verify`) → Análise SonarQube → Quality Gate → Docker Build & Push. Builds Docker usam um daemon DinD; testes de integração usam Testcontainers.

Hook local de pre-push em `.githooks/pre-push` (habilite com `git config core.hooksPath .githooks`) — roda `./mvnw verify` apenas nos serviços Java afetados pelo push.