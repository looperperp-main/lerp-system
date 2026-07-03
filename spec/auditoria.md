# Auditoria — billing-service / uso de Redis e inconsistências

**Data:** 2026-06-23 · **Escopo:** `billing-service` (Fases 1–3 implementadas) + uso de Redis.
**Método:** callers confirmados via grep no código real (não suposição).
**Adendo 2026-07-03:** ver §4 (auditoria geral de segurança e melhorias — todos os serviços) e §5 (status dos itens antigos).
**Adendo 2026-07-03 (correções):** 4.1 a 4.5 **RESOLVIDOS** — ver §6.

---

## 1. Uso do Redis — confirmado (quem realmente chama)

| Serviço / script Lua | Caller real | Veredito |
|---|---|---|
| **WebhookIdempotencyService** (`webhookIdempotencyAcquire` + `webhookComplete`) | `WebhookProcessor` | ✅ **Usado e testado** — única peça de Redis genuinamente em uso |
| **DistributedLockService** (`acquireLock` + `releaseLock`) | `CommissionPayoutJob` (Fase 6) | ✅ **Em uso** desde a Fase 6 — o cron de payout adquire/libera o lock por período. DunningJob (Fase 7) também usará |
| **TenantStatusCacheService.put** (write-through) | `PaymentReceivedHandler` | ⚠️ **Escreve, mas ninguém lê** — `get()`/`evict()` sem caller (endpoint síncrono `GET /internal/billing/status` foi descartado). Custo sem consumidor |
| **annualGuardScript** + `annualCommissionGuard.lua` | **ninguém** | ❌ **Morto** — modelo de comissão anual não existe (comissão é recorrente, criada via Kafka) |

**Resumo:** dos 5 scripts Lua, **3 em uso** (idempotência de webhook + lock distribuído desde a Fase 6). 1 (guard anual) é morto. 1 (cache) escreve sem leitor.

---

## 2. Inconsistências / o que não faz sentido

### 🔴 Crítico
**2.1 — `CommissionService.processarRepasses` marca comissões como PAGO com payout FALSO.** ✅ **RESOLVIDO (2026-06-24, Fase 4).**
~~`@Scheduled(cron "0 0 8 2 * *")`: todo dia 2 às 08h chama `AsaasClient.transferPix` (stub que retorna
`"SIMULADO-..."` sem mandar dinheiro) e mesmo assim seta `status=PAGO` + `paidAt`. Em qualquer instância
rodando, **falsifica repasses no banco**.~~
**Feito (Fase 4):** removido o `@Scheduled` e o corpo que chamava o stub; `processarRepasses()` virou no-op.
**Atualizado (Fase 6, 2026-06-28):** o stub `AsaasClient.java` foi **DELETADO** (código morto/perigoso — "SIMULADO-"). O payout real vive em `CommissionPayoutService`/`CommissionPayoutJob` via `AsaasTransferClient` (`POST /v3/transfers`, sem retry — §28.4). `processarRepasses()` agora dispara o payout real do período atual (trigger manual de dev). Comissão só vira `PAGO` no webhook `TRANSFER_COMPLETED` — nunca por chamada falsa.

**2.2 — `TokenService.validateSecret` (auth-service): check de ≥32 chars está COMENTADO.**
`if (secret == null /*|| secret.length() < 32*/)`. O `CLAUDE.md` afirma que o secret é validado no startup
(mín. 32 chars). Doc divergente do código + proteção real desligada (JWT_SECRET fraco passaria).
**Ação:** reativar o check.

### 🟡 Médio
**2.3 — `TenantStatusCacheService` write-through sem leitor.** Cache é atualizado (`put`) mas nada consome
(`get`/`evict` sem caller). Decisão: ligar um leitor interno (ex.: dunning) **ou** remover o `put` até
existir consumidor. A javadoc da classe já admite que o endpoint síncrono foi descartado.

**2.4 — `annualGuard` (bean + Lua) é código morto.** Remover, ou marcar explicitamente como roadmap (modelo anual).

**2.5 — `DistributedLockService` órfão.** ✅ **RESOLVIDO (2026-06-28, Fase 6).** O `CommissionPayoutJob`
adquire/libera o lock por período (`syax:billing:lock:commission-payout:{período}`, TTL 1800s). Falta só o
teste integrado do serviço (a Fase 1 validou o Lua isolado).

**2.6 — Duas trilhas de comissão.** ✅ **RESOLVIDO (2026-06-24, Fase 4).**
~~Hoje a comissão nasce via Kafka (partner-service → `CommissionService.createCommission`); a spec prevê
`CommissionEngine` interno na Fase 4.~~
**Decisão (sócios):** manter **uma trilha só** — a Kafka. O `CommissionEngine` formaliza (Strategy/Factory)
a persistência que já vinha do consumer; é alimentado por `partner.commission.calculated`, **não** pelo
`PaymentReceivedHandler`. Billing fica responsável só por pagamentos + Asaas; o cálculo da taxa fica no
partner-service. `CommissionService.createCommission` foi removido (corpo migrou para o engine).

### 🟢 Menor
**2.7 — `getPixQrCode` no checkout com `billingType=UNDEFINED`.** O PIX ainda não existe nesse momento →
sempre retorna `null`. Chamada extra ao Asaas sem efeito (tratada sem quebrar, mas desnecessária aqui).

---

## 3. Prioridade sugerida
- ✅ **2.1 resolvido** (Fase 4 desabilitou o payout falso; Fase 6 deletou o stub e ligou o payout real),
  ✅ **2.6 resolvido** (Fase 4 — trilha única) e ✅ **2.5 resolvido** (Fase 6 — lock distribuído em uso).
- **Agir já:** 2.2 (reativar check do JWT secret no auth-service).
- **Resgatado por fases seguintes:** 2.3 (cache sem leitor) — Fase 7 pode resgatar se um leitor interno
  (dunning) for criado. 2.4 (`annualGuard` morto) segue como roadmap do modelo ANUAL.

> Relacionado: gap da Fase 5 (suspensão/cancelamento não propagados ao auth) — ver `spec/teste-restante.md`.

---

## 4. Auditoria geral de segurança e melhorias — 2026-07-03

**Escopo:** todos os serviços (gateway, auth, cadastro, billing, partner). **Método:** o mesmo — callers e configs confirmados via grep/leitura do código real.

### 🔴 Crítico

**4.1 — `cadastro-service` confia cegamente nos headers, sem `InternalRequestFilter`.** ✅ **RESOLVIDO (2026-07-03).**
~~`SecurityConfig` do cadastro tem `anyRequest().permitAll()` (`SecurityConfig.java:25`) e a identidade vem só de `SecurityUtils.getHeader("X-User-Id")` / `X-Tenant-Id`.~~
**Feito:** criado `cadastro-service/.../infra/filter/InternalRequestFilter.java` (mesmo padrão do partner-service — `@Component extends OncePerRequestFilter`, auto-registrado pelo Spring Boot). Rejeita com 401 qualquer request sem `X-User-Id`, exceto `/actuator/health`, `/actuator/info`, `/v3/api-docs*` e `/swagger-ui*`.

### 🟡 Médio

**4.2 — Defaults inseguros no `application.yaml` do billing-service.** ✅ **RESOLVIDO (2026-07-03).**
~~`webhook-token: ${ASAAS_WEBHOOK_TOKEN:changeme-webhook-token}`, `redis password: ${REDIS_PASSWORD:test-password}`, `api-key: ${ASAAS_API_KEY:$aact_sandbox_key}`.~~
**Feito:** os três defaults removidos (`${ASAAS_WEBHOOK_TOKEN}`, `${REDIS_PASSWORD}`, `${ASAAS_API_KEY}` agora sem fallback) — o serviço falha no startup se a env var não estiver setada, em qualquer profile.

**4.3 — `WebhookSecurityService` aceita token vazio == header ausente.** ✅ **RESOLVIDO (2026-07-03).**
~~Construtor converte `null` → `byte[0]` e `validateToken(null)` → `byte[0]`; `MessageDigest.isEqual([],[])` é `true`.~~
**Feito:** construtor agora lança `IllegalStateException` se `asaas.webhook-token` for nulo ou vazio (`isBlank()`), fail-fast no boot.

**4.4 — Gateway só faz strip dos headers protegidos em requests autenticadas.** ✅ **RESOLVIDO (2026-07-03).**
~~O wrapper que mascara `X-User-Id`/`X-Tenant-Id`/`X-Authorities` etc. só envolvia a request no caminho autenticado; nos `PUBLIC_PATHS` a request original seguia com headers forjados intactos.~~
**Feito:** `SecurityFilter.doFilterInternal` agora envolve a request com `getWrappedRequest(request, Map.of())` também nos ramos `PUBLIC_PATHS`/`PUBLIC_PREFIXES`/`PUBLIC_METHOD_PATHS` antes do `doFilter` — headers protegidos (`X-User-Id`, `X-Tenant-Id`, `X-Authorities` etc.) são descartados mesmo em rota pública.

**4.5 — `PUBLIC_PATHS` do gateway casa por `startsWith`.** ✅ **RESOLVIDO (2026-07-03).**
~~`/auth/login`, `/auth/ativar`, `/partner/api/v1/partners/cnpj` etc. eram comparados com `path::startsWith` — `/auth/loginqualquercoisa` ou `/auth/ativar-x` viravam públicos.~~
**Feito:** `PUBLIC_PATHS` agora é exact-match (`Set.contains`). Extraído `PUBLIC_PREFIXES` só para o único caso de subpath intencional (`/partner/api/v1/partners/cnpj/`, com `/` terminal explícito para não vazar para rotas futuras que só compartilhem o prefixo).

### 🟢 Menor

**4.6 — `JwtFilter` (auth-service) nunca retorna 401 explícito.** Sem `Bearer`, ele só não popula o SecurityContext e delega ao `SecurityConfig` negar. Funciona, mas um token inválido estoura `JWTVerificationException` sem catch → 500 em vez de 401. Tratar a exceção e responder 401.

**4.7 — `SecurityFilter` do gateway responde `OPTIONS` com 200 seco antes do CORS.** O preflight retorna 200 sem passar pelo `CorsFilter` do Spring dependendo da ordem da chain — se o CORS funciona hoje é porque o filter do Spring roda antes; a curto-circuito no filtro é redundante e pode mascarar config errada. Verificar ordem e remover o atalho se redundante.

**4.8 — `PaymentReceivedHandler`/`DunningService` escrevem no `TenantStatusCacheService` (agora 2 escritores) e continua sem nenhum leitor** — reforça o 2.3: ou nasce o `get()` interno, ou remover os `put()`.

### Melhorias (não-segurança)

**4.9 — Constantes soltas.** ✅ **RESOLVIDO (2026-07-03).**
~~Nomes de headers (`"X-User-Id"`, `"X-Tenant-Id"`, `"X-Authorities"`, `"asaas-access-token"`) estão como literais duplicados em gateway, billing, partner e cadastro.~~
**Feito:** adicionados `Constants.HEADER_USER_ID`, `HEADER_USER_EMAIL`, `HEADER_TENANT_ID`, `HEADER_IS_OWNER`, `HEADER_PARTNER_ID`, `HEADER_AUTHORITIES`, `HEADER_ASAAS_ACCESS_TOKEN` em `common/Constants.java`. Substituídos os literais em `auth-service` (`TenantController`, `TenantSecurityController`), `billing-service` (`SecurityUtils`, `InternalRequestFilter`, `WebhookController`, `SubscriptionController`, `PlanController`, `CheckoutController`), `partner-service` (`SecurityUtils`, `InternalRequestFilter`) e `cadastro-service` (`SecurityUtils`, `InternalRequestFilter`). **Exceção:** `gateway` não depende do módulo `common` (herda `spring-boot-starter-parent` direto, decisão arquitetural documentada no `CLAUDE.md`) — os nomes de header foram centralizados localmente em constantes privadas no próprio `SecurityFilter` (elimina a duplicação `getExtraHeaders`/`PROTECTED_HEADERS` dentro da classe, sem adicionar dependência nova ao módulo).

**4.10 — `ClienteController`/outros lançam `RuntimeException(Constants.TENANT_NOT_FOUND)` cru.** ✅ **RESOLVIDO (2026-07-03).**
~~No `orElseThrow` do tenantId/userId — vira 500 genérico; usar `BusinessException` (padrão já existente nos services) para responder 401/403 coerente.~~
**Feito:** nos 15 controllers do `cadastro-service` (`ClienteController`, `CondicaoPagamentoController`, `CondicaoPagamentoParcelaController`, `ContatoController`, `DepositoController`, `EnderecoController`, `FornecedorController`, `GrupoClienteController`, `GrupoClienteTabelaPrecoController`, `PessoaController`, `ProdutoCategoriaController`, `ProdutoController`, `TabelaPrecoController`, `TransportadoraController`, `VendedorController`) e em `SecurityUtils.getCurrentUserInfo()`, todo `orElseThrow(() -> new RuntimeException(Constants.X))` derivado de header ausente (`TENANT_NOT_FOUND`, `USER_NOT_FOUND`, `USUARIO_UUID_NAO_ENCONTRADO`, `USUARIO_NAO_AUTENTICADO`) virou `new BusinessException(Constants.X, HttpStatus.UNAUTHORIZED)` — responde 401 coerente via `GlobalExceptionHandler` em vez de 500. **Fora do escopo desta correção:** os `RuntimeException(Constants.X_NOT_FOUND)` na camada de serviço (`ClienteService`, `ProdutoCategoriaService`, `GrupoClienteTabelaPrecoService`) para entidade-não-encontrada-por-id — semântica correta é 404, não 401/403; não foram tocados aqui para não confundir os dois casos.

**4.11 — Claim `roles` do JWT é hardcoded, não reflete o RBAC real (`AuthService.java:168` e `:293`).** ✅ **RESOLVIDO (2026-07-03).**
~~`List<String> roles = isOwner ? List.of(Roles.APP_OWNER, Roles.TENANT_OWNER) : List.of("ROLE_USER");//TODO: get roles from database`. A claim `authorities` (permissões granulares) já é correta — `getPermissions(userId)` consulta de verdade `userRoleRepository.findAllByUserId` → `rolePermissionRepository.findAllByRoleId`. Mas a claim `roles` ignorava essa mesma tabela: todo usuário não-owner virava `"ROLE_USER"` genérico, não importa qual role (`PROPRIETARIO` ou futuras) foi de fato atribuída.~~
**Feito:** novo método `resolveRoles(userId, isOwner)` em `AuthService.java` busca as roles reais via `userRoleRepository.findAllByUserId(userId).stream().map(ur -> ur.getRole().getName()).distinct().toList()`; `isOwner` entra só como flag adicional (`APP_OWNER`), sem mais sobrescrever com `"ROLE_USER"` fixo nem forçar `TENANT_OWNER`. Aplicado nas duas ocorrências (`login()` e `generateJwtForUser()`), TODO removido. Detalhado em `spec/seguranca-tenant-scoping.md` (M7).

### Prioridade sugerida (2026-07-03)
1. ✅ **4.1 resolvido** (filtro no cadastro-service). **2.2** (descomentar check do JWT_SECRET) segue pendente, reconfirmado hoje.
2. ✅ **4.2/4.3 resolvidos** (fail-fast de secrets do billing) — fecha o vetor de webhook forjado.
3. ✅ **4.4/4.5 resolvidos** (endurecimento do gateway).
4. ✅ **4.9/4.10/4.11 resolvidos** (constantes de header centralizadas, exceptions 401 coerentes, claim `roles` real). 4.6–4.8 seguem conforme oportunidade.

---

## 5. Status dos itens antigos — reconferido em 2026-07-03

| Item | Status em 2026-07-03 |
|---|---|
| 2.2 JWT_SECRET check comentado | ❌ **Ainda pendente** — `TokenService.java:28` segue com `/*|| secret.length() < 32*/` |
| 2.3 Cache sem leitor | ❌ **Ainda pendente** — ganhou 2º escritor (`DunningService.put` em SUSPENSO/CANCELADO), mas `get()` continua sem caller |
| 2.4 `annualGuard` morto | ❌ **Ainda pendente** — bean `annualGuardScript` em `RedisConfig.java:58` sem caller |
| 2.7 `getPixQrCode` com UNDEFINED | Sem mudança |
| M8 (IDOR cross-tenant cadastro `findById`) | ✅ **Resolvido** — `FornecedorService`, `TransportadoraService` e `TabelaPrecoService` agora usam `findByIdAndTenantId` via `TenantContext` (confirmado via grep). Nota: 4.1 resolvido também — a ressalva de bypass da camada inteira não se aplica mais |

---

## 6. Correções aplicadas — 2026-07-03

| Item | Arquivo(s) alterado(s) | O que mudou |
|---|---|---|
| **4.1** | `cadastro-service/.../infra/filter/InternalRequestFilter.java` (novo) | Filtro auto-registrado (`@Component extends OncePerRequestFilter`) rejeitando com 401 qualquer request sem `X-User-Id`, exceto actuator health/info e docs OpenAPI/Swagger. |
| **4.2** | `billing-service/src/main/resources/application.yaml` | Removidos os defaults de `ASAAS_API_KEY`, `ASAAS_WEBHOOK_TOKEN` e `REDIS_PASSWORD` — a ausência da env var agora quebra o binding do Spring no startup. |
| **4.3** | `billing-service/.../services/WebhookSecurityService.java` | Construtor lança `IllegalStateException` se `asaas.webhook-token` for nulo/vazio, em vez de degradar para `byte[0]` (que fazia `MessageDigest.isEqual` aceitar header ausente). |
| **4.4** | `gateway/.../security/SecurityFilter.java` | `doFilterInternal` passa a envolver a request com `getWrappedRequest(request, Map.of())` também nos ramos `PUBLIC_PATHS`/`PUBLIC_PREFIXES`/`PUBLIC_METHOD_PATHS`, garantindo strip de `X-User-Id`/`X-Tenant-Id`/`X-Authorities` etc. mesmo em rota pública. |
| **4.5** | `gateway/.../security/SecurityFilter.java` | `PUBLIC_PATHS` virou exact-match (`Set.contains`); extraído `PUBLIC_PREFIXES` só para o subpath intencional de CNPJ (`/partner/api/v1/partners/cnpj/`, com `/` terminal). |
| **4.9** | `common/.../util/Constants.java` + `auth-service`/`billing-service`/`partner-service`/`cadastro-service` (`SecurityUtils`, `InternalRequestFilter`, controllers com `@RequestHeader`) + `gateway/.../SecurityFilter.java` | Novas constantes `HEADER_*`/`HEADER_ASAAS_ACCESS_TOKEN` em `common`; literais trocados nos 4 serviços que dependem de `common`. Gateway (sem dependência em `common`) ganhou constantes locais equivalentes, eliminando a duplicação interna entre `getExtraHeaders` e `PROTECTED_HEADERS`. |
| **4.10** | 15 controllers de `cadastro-service` + `cadastro-service/.../util/SecurityUtils.java` | `orElseThrow(() -> new RuntimeException(Constants.X))` (tenant/user ausente) virou `new BusinessException(Constants.X, HttpStatus.UNAUTHORIZED)` — responde 401 via `GlobalExceptionHandler` em vez de 500 genérico. |
| **4.11** | `auth-service/.../infra/AuthService.java` | Novo `resolveRoles(userId, isOwner)` busca roles reais via `userRoleRepository.findAllByUserId`; `isOwner` vira flag adicional (`APP_OWNER`) em vez de sobrescrever com `"ROLE_USER"` fixo. Aplicado em `login()` e `generateJwtForUser()`. |

**Não incluído neste round** (fora do escopo pedido): 2.2 (JWT_SECRET check comentado), 2.3/2.4 (cache/annualGuard mortos), 4.6–4.8 (menores). Os `RuntimeException` de entidade-não-encontrada na camada de serviço (`ClienteService`, `ProdutoCategoriaService`, `GrupoClienteTabelaPrecoService`) também ficaram de fora — semântica correta é 404, não 401/403, então não foram tocados junto com o 4.10.