# Auth Service

Microsserviço responsável pelo gerenciamento de identidade, autenticação, autorização e controle de acesso baseado em pagamentos/assinaturas do ERP.

## 📋 Funcionalidades Principais

*   **Autenticação e Autorização:** Login de usuários padrão e usuários vinculados a *Tenants* (inquilinos), emissão e validação de JWT (JSON Web Tokens).
*   **Gerenciamento de Refresh Tokens:** Mecanismo seguro para renovação de sessões sem a necessidade de reautenticação manual.
*   **Controle de Acesso Baseado em Pagamento:** Integração com Stripe para liberação de acesso e provisionamento de recursos após a confirmação do pagamento.
*   **Gestão de Inquilinos (Tenants):** Criação e gerenciamento de contas isoladas para diferentes clientes do ERP.
*   **Gestão de Usuários, Roles e Permissões:** Controle granular do que cada usuário pode acessar dentro de cada Tenant.
*   **Auditoria:** Registro de eventos de segurança (login, logout, tentativas falhas, criação de usuários) via publicação de mensagens no Apache Kafka.
*   **Proteção contra Força Bruta:** Bloqueio temporário de contas após múltiplas tentativas de login malsucedidas.

## 🛠️ Tecnologias Utilizadas

*   **Java 25**
*   **Spring Boot 3.4.x / Spring Security**
*   **PostgreSQL** (Armazenamento de dados de usuários e tenants)
*   **Stripe Java SDK** (Integração de pagamentos)
*   **Apache Kafka** (Mensageria para trilhas de auditoria)
*   **Auth0 Java JWT** (Geração e validação de tokens)
*   **Passay** (Validação de políticas de senha)
*   **OWASP HTML Sanitizer** (Prevenção contra ataques XSS)

## 🚀 Como Executar

Este serviço depende do PostgreSQL e do Kafka. Certifique-se de que a infraestrutura esteja rodando via Docker Compose na raiz do projeto:

```bash
docker-compose up -d postgres zookeeper kafka
```

```bash
./mvnw spring-boot:run
```


# Cadastro Service

Microsserviço responsável pelo gerenciamento dos cadastros básicos e essenciais do ERP (Master Data). Ele fornece as entidades fundamentais que serão consumidas e referenciadas por outros módulos do sistema.

## 📋 Funcionalidades Principais

*   **Gestão de Condições de Pagamento:** Cadastro, listagem, atualização e exclusão das condições de pagamento disponíveis para uso em transações financeiras e comerciais.
*   **Gestão de Depósitos:** Controle de locais de armazenamento e estoques.
*   **Gestão de Grupos de Clientes:** Categorização e agrupamento de clientes para aplicação de regras de negócio específicas (ex: descontos, tabelas de preços).
*   **Auditoria e Rastreabilidade:** Registro das alterações e criações de cadastros importantes via eventos enviados ao serviço central de auditoria.
*   **Isolamento de Dados:** Filtro e separação de cadastros com base no Tenant autenticado, garantindo que um cliente não veja os dados de outro.

## 🛠️ Tecnologias Utilizadas

*   **Java 25**
*   **Spring Boot 3.4.x** (Web, Data JPA, Validation)
*   **PostgreSQL** (Armazenamento relacional dos dados de cadastro)
*   **Apache Kafka** (Producer para envio de eventos de auditoria)
*   **MapStruct** (Mapeamento entre Entidades e DTOs)
*   **Lombok** (Redução de boilerplate de código)

## 🚀 Como Executar

Este serviço requer o PostgreSQL, o Kafka, e o `auth-service` para validação correta dos acessos (ou o API Gateway configurado).

Inicie a infraestrutura básica na raiz do projeto:
```bash
docker-compose up -d postgres zookeeper kafka
```

Para rodar a aplicação localmente:
```bash
./mvnw spring-boot:run
```

## 📡 Integrações e Comunicação

O `cadastro-service` atua primariamente como uma API RESTful para os front-ends (`erp-front-end-admin` e `erp-front-end-web`).
As requisições devem passar pelo API Gateway, que lida com o roteamento e a injeção dos cabeçalhos do Tenant e do Usuário a partir do JWT gerado pelo `auth-service`.


Análise completa. Deixa eu te dar um diagnóstico honesto — o projeto tem uma base sólida mas com lacunas que separam um mid-level de um sênior de verdade.

  ---
O que você já fez bem

- Multi-tenancy via Hibernate filters + header injection no gateway — arquitetura correta
- Rate limiting com Bucket4j
- Password policy forte (14+ chars, blacklist, sequências)
- Audit trail via Kafka
- GlobalExceptionHandler centralizado
- Separação de camadas consistente (controller → service → repository → DTO)

  ---
O que um sênior implementaria — por prioridade

1. Segurança (crítico)

~~Refresh Token Rotation — o maior gap de segurança. Hoje seu refresh token pode ser roubado e reusado infinitamente. Um sênior implementa: ao usar o refresh token, invalida o anterior e emite um novo. Se o token antigo aparecer depois, toda a família de tokens é revogada (rotação + detecção de
reuso).~~

JWT Secret unificado via env var no gateway — hoje está hardcoded security-key-for-now. Isso é falha de produção garantida.

Validação de tamanho de payload — sem limite, qualquer payload gigante passa pelo gateway. Um sênior adiciona spring.codec.max-in-memory-size e validação de tamanho em endpoints críticos.

Sanitização HTML consistente — só o ClienteMapper sanitiza. O restante dos DTOs está cru.

  ---
2. Testes (o que mais diferencia um sênior)

Você tem 18 testes Java para 26 controllers. Isso não escala.

O que falta:

- Testes de integração com Testcontainers (PostgreSQL real + Kafka real) — nenhum mock quebra em produção, apenas o banco de verdade pega
- Testes do SecurityFilter e RateLimitFilter do gateway — a camada mais crítica está sem cobertura
- Testes de contrato (Spring Cloud Contract ou Pact) — garante que gateway e auth-service falam a mesma língua
- Cobertura acima de 70% nos caminhos de negócio — hoje o JaCoCo exige 40%, o mínimo aceitável

  ---
3. Observabilidade (o que distingue sênior de pleno)

Você tem Prometheus + Grafana + ELK, mas não está usando:

- Distributed Tracing com OpenTelemetry — hoje se uma request falha, você não consegue rastrear ela entre gateway → auth-service → cadastro-service. Isso é indispensável em microserviços
- Structured logging — logs devem ter traceId, spanId, tenantId, userId em JSON. Hoje provavelmente é texto livre
- Custom metrics de negócio — ex: taxa de login failure, requests por tenant, tokens expirados. Dashboards de infra sem métricas de negócio são inúteis em produção

  ---
4. Resiliência

O gateway não tem:

- Circuit Breaker (Resilience4j) configurado nos routes — se auth-service cair, o gateway trava junto
- Timeout por rota — requisição pendurada pode esgotar threads pool
- Retry com backoff exponencial — falhas transitórias simplesmente falham hoje

  ---
5. Frontend Angular (menor prioridade, mas visível)

- Route Guards — canActivate: [authGuard] está comentado. Qualquer usuário acessa /admin sem autenticar
- Global HTTP Error Interceptor — hoje cada componente lida com erro por conta própria, duplicando lógica
- Refresh token automático no interceptor — quando o 401 com X-Token-Expired: true chega, deve tentar refresh antes de jogar o usuário no login

  ---
6. Maturidade de Engenharia

- API versionamento — /api/v1/ nos endpoints. Permite evoluir sem quebrar clientes
- OpenAPI/Swagger — documentação gerada automaticamente via springdoc-openapi. Sênior não documenta à mão
- Soft delete — deletedAt timestamp ao invés de DELETE físico. Permite auditoria e recovery
- Nomeação consistente — dominio/ vs domain/, repositorios/ vs repository/. Isso sinaliza projeto inacabado para qualquer revisor
- Paginação com limite máximo — hoje ?size=999999 é válido, DoS simples

  ---
O caminho realista

Agora:           ~~Refresh token rotation~~ + JWT secret via env var
Próximo mês:     Testcontainers + subir cobertura para 70%
Próximo mês:     OpenTelemetry distributed tracing
Próximo mês:     Circuit breaker + timeouts no gateway
Depois:          API versioning + Swagger + soft delete
