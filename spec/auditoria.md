# Auditoria — billing-service / uso de Redis e inconsistências

**Data:** 2026-06-23 · **Escopo:** `billing-service` (Fases 1–3 implementadas) + uso de Redis.
**Método:** callers confirmados via grep no código real (não suposição).
**Adendo 2026-07-03:** ver §4 (auditoria geral de segurança e melhorias — todos os serviços) e §5 (status dos itens antigos).
**Adendo 2026-07-03 (correções):** 4.1 a 4.5 **RESOLVIDOS** — ver §6.
**Adendo 2026-07-21 (revisão pós-commit):** re-revisão do commit `c2ae6d5` achou 2 follow-ups (órfã Asaas no 409 do checkout, `MethodArgumentTypeMismatch` 500→400) — ver §9.

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

**2.2 — `TokenService.validateSecret` (auth-service): check de ≥32 chars está COMENTADO.** ✅ **RESOLVIDO — doc estava desatualizada, corrigido em 2026-07-14.**
~~`if (secret == null /*|| secret.length() < 32*/)`. O `CLAUDE.md` afirma que o secret é validado no startup (mín. 32 chars). Doc divergente do código + proteção real desligada (JWT_SECRET fraco passaria).~~
**Verificado em 2026-07-14:** o check já está ativo no código (`if (secret == null || secret.length() < 32) throw new IllegalStateException(...)`), sem comentário — corrigido no commit `1973eb0` ("fix: reativa validação de tamanho mínimo do JWT_SECRET no startup"), anterior a esta auditoria. Este item ficou marcado como pendente nas seções 3/5 abaixo por desatualização do documento, não por o problema seguir existindo no código.

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

**4.6 — `JwtFilter` (auth-service) nunca retorna 401 explícito.** ✅ **RESOLVIDO (2026-07-14).**
~~Sem `Bearer`, ele só não popula o SecurityContext e delega ao `SecurityConfig` negar. Funciona, mas um token inválido estoura `JWTVerificationException` sem catch → 500 em vez de 401.~~
**Feito:** `doFilterInternal` agora envolve `verify(token)`/parsing de claims em `try/catch`, no mesmo molde do `SecurityFilter` do gateway — `TokenExpiredException` → 401 + header `X-Token-Expired`, `JWTVerificationException` (assinatura/issuer inválidos) → 401.

**4.7 — `SecurityFilter` do gateway responde `OPTIONS` com 200 seco antes do CORS.** ✅ **RESOLVIDO (2026-07-14).**
~~O preflight retorna 200 sem passar pelo `CorsFilter` do Spring dependendo da ordem da chain — se o CORS funciona hoje é porque o filter do Spring roda antes; a curto-circuito no filtro é redundante e pode mascarar config errada.~~
**Feito:** confirmado que o preflight real é resolvido pelo `CorsFilter` do Spring Security (`HttpSecurity.cors(...)` em `SecurityConfig`), que roda antes do `SecurityFilter` na chain e encerra a request sem chegar a ele — atalho removido. De caminho, corrigido um double-registration não documentado: `SecurityFilter` é `@Component` (necessário para injeção em `SecurityConfig` via `addFilterBefore`) mas isso também fazia o Spring Boot auto-registrá-lo como filtro de servlet standalone — toda request passava pelo filtro duas vezes. Desligado via `FilterRegistrationBean<SecurityFilter>.setEnabled(false)`.

**4.8 — `PaymentReceivedHandler`/`DunningService` escrevem no `TenantStatusCacheService` (agora 2 escritores) e continua sem nenhum leitor** — reforça o 2.3: ou nasce o `get()` interno, ou remover os `put()`. **Pendente** — aguardando revisão da spec de payments antes de decidir entre plugar um leitor ou remover o cache.

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
1. ✅ **4.1 resolvido** (filtro no cadastro-service). **2.2** (descomentar check do JWT_SECRET) — ✅ resolvido (o registro aqui estava desatualizado; ver §5, corrigido em 2026-07-14).
2. ✅ **4.2/4.3 resolvidos** (fail-fast de secrets do billing) — fecha o vetor de webhook forjado.
3. ✅ **4.4/4.5 resolvidos** (endurecimento do gateway).
4. ✅ **4.9/4.10/4.11 resolvidos** (constantes de header centralizadas, exceptions 401 coerentes, claim `roles` real). 4.6–4.8 seguem conforme oportunidade.

---

## 5. Status dos itens antigos — reconferido em 2026-07-03

| Item | Status em 2026-07-03 |
|---|---|
| 2.2 JWT_SECRET check comentado | ✅ **Resolvido** — commit `1973eb0` reativou a validação de tamanho mínimo no startup; confirmado ativo em 2026-07-14 |
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

---

## 7. Auditoria — abuso de cobrança e negação de serviço — 2026-07-14

**Escopo:** `billing-service` (autorização/lógica financeira) + `gateway`/`auth-service` (rate-limit e esgotamento de recursos). **Método:** callers e configs confirmados lendo o código real; achados abaixo verificados manualmente (arquivo:linha) antes de entrar nesta lista.

### 🔴 Crítico

**7.1 — Qualquer usuário autenticado (de qualquer tenant) pode reescrever o catálogo de planos e pagar centavos.** ✅ **RESOLVIDO (2026-07-14).**
~~Gateway `SecurityFilter.java` só marca `GET /billing/api/v1/plans` como público (`PUBLIC_METHOD_PATHS`); `POST`/`PUT` em `/billing/api/v1/plans` caem em `anyRequest().authenticated()`. `PlanController.java` (`@PostMapping` linha 58, `@PutMapping("/{id}")` linha 67) não tem **nenhum** `@PreAuthorize`, e o `billing-service` `SecurityConfig` é `anyRequest().permitAll()` — o único gate real é o `InternalRequestFilter`, que só exige o header `X-User-Id` (qualquer usuário logado, de qualquer tenant, sem checar role). `PlanRequest.value` só valida `@Positive`, então `0.01` passa.~~
~~**Cenário:** um usuário comum de um tenant qualquer faz `PUT /billing/api/v1/plans/{id}` baixando o `value` do plano PRO pra R$0,01 (ou cria um plano próprio com `value=0.01 active=true`), depois `POST /billing/api/v1/checkout` com aquele `planType`. `CheckoutService` busca o plano por `findByPlanTypeAndActiveTrue` e cria a assinatura no Asaas com o valor adulterado — paga 1 centavo, ganha acesso completo. Como o `PUT` altera a linha global do plano, afeta **todos os tenants** (pode zerar receita geral). Era o item mais grave de toda a auditoria.~~
**Feito:** nova permission `PLATFORM` `PLANO_MANAGE` semeada via Liquibase (`liquibase-service/.../auth/auth-schema-016.yaml`, mesmo molde do `REPASSE_EXECUTE` em `auth-schema-011.yaml` — scope `PLATFORM`, não atribuível pelo portal de tenant). `@PreAuthorize("hasAuthority('PLANO_MANAGE')")` adicionado em `PlanController.criar`/`.atualizar`/`.toggleStatus` (POST/PUT/PATCH); infra de authorities via header `X-Authorities` já existia (`InternalRequestFilter`), reusada sem mudança. Mensagem do `SecurityExceptionHandler` generalizada (não é mais hardcoded pra "repasses"). **Pendente operacional (fora do código):** atribuir `PLANO_MANAGE` a uma role de admin Syax — ninguém tem essa permission ainda até isso ser feito manualmente (via SQL ou portal admin).

**7.2 — Rate-limit do gateway é anulável via `X-Forwarded-For` forjado (amplifica todos os itens de DoS abaixo).** ✅ **RESOLVIDO (2026-07-14).**
~~`RateLimitFilter.java:75-88` (`resolveKey`/`isTrustedProxy`) trata qualquer `remoteAddr` de faixa RFC-1918/loopback como proxy confiável e usa o primeiro valor de `X-Forwarded-For` como chave do bucket, sem validar quantos hops nem allowlist de proxy conhecido. No deploy real (Angular → nginx/LB OCI → gateway, ver `CLAUDE.md`), o `remoteAddr` que o gateway enxerga **é** o IP privado do LB — ou seja, toda requisição cai no ramo "confiável" e a chave do bucket passa a ser 100% controlada pelo atacante. Também impactava dev local: qualquer origem em faixa privada (docker bridge, loopback) já entrava no ramo "confiável".~~
**Feito:** `isTrustedProxy`/`PRIVATE_PREFIXES` removidos. `resolveKey` só confia em `X-Forwarded-For` se `remoteAddr` estiver na allowlist explícita `gateway.trusted-proxies` (nova propriedade, `application.yml`, default **vazio** — em prod configurar via env var `GATEWAY_TRUSTED_PROXIES` com o(s) IP(s) exato(s) do LB/nginx). Default vazio também resolve o bloqueio observado em dev local, já que nada é tratado como proxy confiável sem configuração explícita. O `Map<String, Bucket>` (linha 25) virou um `LinkedHashMap` com `removeEldestEntry` (LRU, teto de 50 000 entradas) em vez de `ConcurrentHashMap` sem limite — cobre o esgotamento de heap sem depender de allowlist correta. `/actuator/**` também ficou fora do rate-limit (health checks/Prometheus não devem contar pro mesmo bucket de tráfego de API). Testes novos em `RateLimitFilterTest.java` cobrem allowlist vazia/preenchida e o bypass de `/actuator`.

**7.3 — `/auth/criar-conta` (público) dispara Argon2 memory-hard + 2 INSERTs sem rate-limit dedicado.** ✅ **RESOLVIDO (2026-07-14).**
~~`AuthController` → `AuthService.criarContaGratis` (`AuthService.java:467-502`), endpoint em `PUBLIC_PATHS` do gateway. Cada chamada roda `passwordEncoder.encode()` com Argon2id (16 MB RAM + 2 iterações por chamada) mais INSERTs em `tenant`+`users_account`, bootstrap de roles e evento Kafka. Sem o item 7.2 resolvido, o único limite era o rate-limit genérico do gateway.~~
**Feito:** `RateLimitFilter` do gateway ganhou um segundo bucket Bucket4j, dedicado e mais restrito, só pra esse path (janela de 10 min, default 3 tentativas/IP — configurável via `rate-limit.criar-conta.*`), independente do limite genérico por IP.

### 🟡 Médio

**7.4 — IDOR: cancelar/reprocessar assinatura e ler cobranças de qualquer tenant.** ✅ **RESOLVIDO (2026-07-14).**
~~`SubscriptionController.java`: `/{id}/reprocess`, `/{id}/cobrancas`, `/{id}/cancel` recebiam o UUID da assinatura direto no path, sem `@PreAuthorize` e sem checar se pertence ao tenant autenticado.~~
**Feito:** nova permission `PLATFORM` `ASSINATURA_MANAGE` (`auth-schema-017.yaml`) + `@PreAuthorize("hasAuthority('ASSINATURA_MANAGE')")` em `listar`, `/mrr`, `/{id}/reprocess`, `/{id}/cobrancas`, `/{id}/cancel`. `/me/cancel` (self-service, escopado por `X-Tenant-Id`) não mudou.

**7.5 — Vazamento cross-tenant de MRR e extrato de comissões.** ✅ **RESOLVIDO (2026-07-14).**
~~`SubscriptionController.java:32-42` (`GET /subscriptions` aceita `tenantId` livre por query param) e `CommissionController.java` (`GET /commissions`, `GET /commissions/extrato?partnerId=`) — nenhum tinha `@PreAuthorize` nem validava dono.~~
**Feito:** `GET /subscriptions` coberto pela mesma `ASSINATURA_MANAGE` do 7.4. Nova permission `PLATFORM` `COMISSAO_MANAGE` (`auth-schema-017.yaml`) + `@PreAuthorize` em `listar`, `/summary`, `/extrato` do `CommissionController`. Confirmado no código que `/extrato` não é chamado pelo partner-service via HTTP (o comentário estava desatualizado) — a integração real é por Kafka (`BillingClient.getExtrato` → `partner.extrato.request`/`ExtratoRequestConsumer`), então travar com permission de admin não quebra nada.

**7.6 — Webhook do Asaas confia no `amount` do payload sem revalidar contra o valor esperado.** ✅ **RESOLVIDO (2026-07-14).**
~~`PaymentReceivedHandler.java:92-96` — divergência entre valor esperado e valor recebido no payload só gerava `WARN`, e a comissão era calculada sobre `payment.getValue()` do corpo do webhook (autenticado só por token estático, não HMAC por payload).~~
**Feito:** a ativação continua acontecendo mesmo com divergência (o pagamento já ocorreu no Asaas — bloquear ativação seria pior), mas o valor usado pro evento `subscription.activated`/comissão passou a ser sempre `sub.getValue()` (DB, dado de origem interna) — o `amount` do payload nunca mais alimenta cálculo financeiro, só o log de `WARN` continua comparando os dois pra observabilidade.

**7.7 — Trial gratuito ilimitado por rotação de CNPJ.** — pendente, decisão de produto (não é fix de código: validar DV do CNPJ contra a Receita, limitar por IP/telefone, etc.).
`AuthService.java:467-495` (`criarContaGratis`, endpoint público). Dedup só por CNPJ exato + e-mail exato; sem validação de DV do CNPJ contra a Receita, sem cartão, sem limite por IP/telefone. Cada par CNPJ+e-mail novo (mesmo descartável) gera novo TRIAL de 15 dias (`trialExpiresAt`, linha 482). Não recria com o *mesmo* CNPJ, mas nada impede rotacionar CNPJs válidos-porém-descartáveis + e-mail temporário pra manter acesso grátis indefinidamente.

**Decisão (2026-07-14):** linha 468 (`cnpjDigits = req.cnpj().replaceAll("\\D", "")`) precisa sair — o strip-pra-só-dígito quebra quando o CNPJ alfanumérico (NT 2026.004, já suportado em `partner-service` via `CnpjService.java`/pipe Angular base-36, produção 01/07/2026) chegar nesse fluxo. Escopo decidido pra essa correção, por enquanto: validar só o dígito verificador (algoritmo mod 11, sem chamada externa) — **não** consultar Receita/BrasilAPI, porque isso introduz fricção pra testar o próprio fluxo de cadastro. `CnpjService.java` hoje vive só em `partner-service` (não em `common`), então precisa decidir mover/duplicar a lógica de DV pro auth-service quando isso for implementado.
**Feito parcialmente (2026-07-14):** `criarContaGratis` normaliza mantendo letras (`replaceAll("[.\-/\s]", "").toUpperCase()`) e valida o CNPJ via `cnpjTemDvValido`/`calcularDigitoVerificadorCnpj` (novo, `AuthService.java`), algoritmo alfanumérico-ready (valor de cada caractere = ASCII - 48, pesos oficiais, 2 DVs sempre numéricos). CNPJ sem DV válido → 400. **Não implementado ainda:** limite por IP/telefone e qualquer verificação de posse (SMS/e-mail) — o achado original de rotação de CNPJ+e-mail descartável pra farmar TRIAL continua de pé, só a porta "CNPJ com DV inválido/mal formado" foi fechada. **Não testado** — sem suíte de testes cobrindo o método (privado, precisaria de reflection ou expor pra testar isolado); a aritmética do algoritmo não foi executada, só escrita a partir da especificação publicada.

**7.8 — Checkout sem guarda de assinatura existente nem idempotência.** ✅ **RESOLVIDO (2026-07-14).**
~~`CheckoutService.java:52-79` (`createCheckout`) não verificava se o tenant já tinha assinatura `ATIVA`/`AGUARDANDO_PAGAMENTO` antes de criar customer+subscription no Asaas, e não tinha lock/idempotency-key. Chamadas repetidas ou em paralelo criavam múltiplas assinaturas Asaas + múltiplas linhas locais pro mesmo tenant.~~
**Feito:** guard fail-fast no início de `createCheckout` (rejeita com 409 antes de gastar chamada no Asaas) + índice único parcial `billing.subscription(tenant_id) WHERE status IN ('ATIVA','AGUARDANDO_PAGAMENTO')` (`billing-schema-018.yaml`) — é o índice que garante de verdade sob concorrência (o guard sozinho tem uma corrida TOCTOU). `saveAndFlush` (não `save`) força o INSERT dentro do `try`, com `catch (DataIntegrityViolationException)` → 409 em vez de vazar 500.
**Follow-up (2026-07-21, §9):** no braço TOCTOU o `catch` já tinha criado a assinatura no Asaas antes do INSERT estourar — ela ficava **órfã** (cobrando o cliente sem linha local). O `catch` agora chama `asaasGateway.cancelSubscription(asaasSub.id())` antes de devolver 409 (falha do cancel só loga pra ação manual). Coberto por `CheckoutServiceTest.concurrentCheckout_...cancelsOrphanAsaasSubscription_andReturns409`.

**7.9 — `billing-service` sem `max-page-size` configurado (inconsistente com os outros 3 serviços).** ✅ **RESOLVIDO (2026-07-14).**
~~`billing-service/src/main/resources/application.yaml` não tinha bloco `spring.data.web.pageable` — `GET /subscriptions` recebia `Pageable` cru, valendo o default do Spring (2000).~~
**Feito:** `spring.data.web.pageable.max-page-size: 100` adicionado, mesmo valor dos outros 3 serviços.

### 🟢 Menor

**7.10 — `SubscriptionController /mrr` faz `findAll()` sem filtro.** ✅ **RESOLVIDO (2026-07-14).**
~~`SubscriptionController.java:51` carrega a tabela `subscription` inteira e itera 6× em streams a cada chamada (já tem comentário `ponytail:` reconhecendo a dívida). Cresce linearmente com o total de assinaturas de todos os tenants; admin-only, por isso severidade menor.~~
**Feito:** novo `findByActivatedAtIsNotNull()` no repositório, usado em vez de `findAll()` — corta as assinaturas que nunca ativaram (`AGUARDANDO_PAGAMENTO` morta) do full scan. Continua agregando em memória (mesmo `ponytail:` de antes, teto documentado: query nativa com `generate_series` se a tabela crescer).

**7.11 — `RolesService.getAllRoles()` sem paginação.** ✅ **RESOLVIDO (2026-07-14).**
~~`auth-service/.../services/RolesService.java:71`. Tabela `roles` é pequena por natureza — impacto baixo, mas inconsistente com o padrão paginado do resto do CRUD.~~
**Feito:** `findAll()` trocado por `findAll(PageRequest.of(0, 500))` — cap defensivo de 500, mantendo o contrato `List<RoleDTO>` do endpoint (`GET /auth/roles`, usado em dropdowns/picklists). Listagem paginada de verdade já existe em `GET /auth/roles/pages`.

**7.12 — Login público roda Argon2 (16 MB/chamada) antes de qualquer CAPTCHA.** ✅ **RESOLVIDO (2026-07-14).**
~~`AuthService.java` (`login`/`loginPartner`/`loginWithTenant`, linhas 115/154/221) — mitigado parcialmente por `isUserLocked()` rodar antes do hash e pelo lock após `MAX_FAILED_ATTEMPTS` (`:361-366`), e por short-circuit em e-mail inexistente (`:134`, sem hash). Combinado com o bypass do rate-limit (7.2) e uma lista de e-mails válidos conhecidos, ainda dá pra sustentar carga de Argon2 suficiente pra pressionar CPU/heap.~~
**Feito:** o bucket dedicado do 7.3 foi generalizado — `RateLimitFilter` agora cobre `/auth/login`, `/auth/tenant/login`, `/auth/partner/login` além de `/auth/criar-conta` (mesmo mecanismo, `rate-limit.argon2.*`, janela 10min, default 5/IP). `/auth/refresh` e `/auth/logout` ficam de fora — não tocam em hash de senha.

### Verificado e sem vetor (fecha o escopo, não repetir em auditorias futuras)

- **Upload de arquivo:** não existe endpoint multipart hoje (só na spec futura `spec/Fin.md`, importação OFX/CNAB) — quando implementar, configurar `max-file-size`/`max-request-size`.
- **ReDoS:** nenhum `Pattern.compile`/`.matches()` com input do usuário; buscas usam JPA/Spring Data, não regex.
- **JSON gigante/aninhado:** defaults do Jackson (profundidade 1000, string 20 MB) não foram sobrescritos — contido, embora sem cap explícito de tamanho de body JSON (só multipart tem).
- **Pools (HikariCP/Tomcat/Kafka):** `maximum-pool-size` default 10 nos 4 serviços; nenhuma transação longa em loop nem N+1 sobre lista vinda do client encontrada. Kafka producers usam defaults (`acks=all`, `delivery.timeout.ms=120000`), sends são fire-and-forget (não bloqueiam thread HTTP); sem poison-pill/retry infinito identificado.
- **Auto-inflação de comissão pelo parceiro:** o `amount` da comissão vem pronto do evento Kafka `partner.commission.calculated` (`RecurrentCommissionStrategy.java:33`) e a idempotência por `asaasPaymentId` (`CommissionEngine.java:42-46`) impede duplicata pelo mesmo pagamento. Eventual auto-inflação teria que vir do cálculo no partner-service — fora do escopo do billing, não investigado aqui.

### Resumo de prioridade

1. ~~**7.1** (plano a R$0,01)~~ — ✅ resolvido (2026-07-14). Falta só atribuir `PLANO_MANAGE` a uma role de admin (passo operacional, fora do código).
2. ~~**7.2** (bypass do rate-limit)~~ — ✅ resolvido (2026-07-14).
3. ~~**7.3** (flood no criar-conta)~~ — ✅ resolvido (2026-07-14).
4. ~~**7.4/7.5** (IDOR/vazamento cross-tenant billing)~~ — ✅ resolvido (2026-07-14). Falta atribuir `ASSINATURA_MANAGE`/`COMISSAO_MANAGE` a uma role de admin (mesmo passo operacional pendente do 7.1).
5. ~~**7.6/7.8** (webhook amount / checkout duplicado)~~ — ✅ resolvido (2026-07-14). **7.7** parcialmente resolvido (validação de DV do CNPJ) — a rotação CNPJ+e-mail descartável em si segue sem limite (decisão de produto: IP/telefone/verificação de posse).
6. ~~**7.9/7.10/7.11/7.12**~~ — ✅ resolvido (2026-07-14).

### Correções aplicadas — 2026-07-14

| Item | Arquivo(s) alterado(s) | O que mudou |
|---|---|---|
| **7.1** | `liquibase-service/.../auth/auth-schema-016.yaml` (+ `db.changelog-master.yaml`), `billing-service/.../PlanController.java`, `billing-service/.../SecurityExceptionHandler.java` | Nova permission `PLATFORM` `PLANO_MANAGE`; `@PreAuthorize("hasAuthority('PLANO_MANAGE')")` em criar/atualizar/toggle de plano; mensagem 403 generalizada. |
| **7.2** | `gateway/.../filter/RateLimitFilter.java`, `gateway/src/main/resources/application.yml`, `gateway/.../filter/RateLimitFilterTest.java` | Trust de `X-Forwarded-For` trocado de "qualquer IP RFC1918" pra allowlist explícita (`gateway.trusted-proxies`, default vazio); `Map` de buckets virou LRU limitado (50k entradas) em vez de crescer sem teto; `/actuator/**` isento do rate-limit. |
| **2.2** | (nenhum — doc corrigida) | Achado já estava resolvido no código (commit `1973eb0`, anterior a esta auditoria); `spec/auditoria.md` só não refletia isso. |
| **4.6** | `auth-service/.../infra/config/JwtFilter.java` | `try/catch` em volta do `verify(token)`/parsing de claims — `TokenExpiredException`/`JWTVerificationException` agora respondem 401 em vez de estourar 500. |
| **4.7** | `gateway/.../security/SecurityFilter.java`, `gateway/.../SecurityConfig.java`, `gateway/.../security/SecurityFilterTest.java` | Removido o atalho de `OPTIONS` (preflight real já é resolvido pelo `CorsFilter` do Spring, que roda antes na chain); corrigido double-registration do `SecurityFilter` como filtro standalone via `FilterRegistrationBean.setEnabled(false)`. |
| **7.3** | `gateway/.../filter/RateLimitFilter.java`, `gateway/.../filter/RateLimitFilterTest.java` | Segundo bucket Bucket4j dedicado só pra `/auth/criar-conta` (janela 10min, default 3/IP), independente do bucket genérico. |
| **7.4/7.5** | `liquibase-service/.../auth/auth-schema-017.yaml` (+ `db.changelog-master.yaml`), `billing-service/.../SubscriptionController.java`, `billing-service/.../CommissionController.java` | Novas permissions `PLATFORM` `ASSINATURA_MANAGE`/`COMISSAO_MANAGE`; `@PreAuthorize` em todos os endpoints admin dos dois controllers (`/me/cancel` do tenant não mudou). |
| **7.6** | `billing-service/.../webhook/handler/PaymentReceivedHandler.java` | Comissão/evento `subscription.activated` passam a usar sempre `sub.getValue()` (DB) — nunca mais o `amount` do payload do webhook. |
| **7.8** | `liquibase-service/.../billing/billing-schema-018.yaml` (+ `db.changelog-master.yaml`), `billing-service/.../repository/SubscriptionRepository.java`, `billing-service/.../services/CheckoutService.java` | Índice único parcial (1 assinatura ativa/pendente por tenant); guard fail-fast + `saveAndFlush`/catch de `DataIntegrityViolationException` → 409 em vez de duplicata ou 500. |
| **7.9** | `billing-service/src/main/resources/application.yaml` | `spring.data.web.pageable.max-page-size: 100`, alinhado com os outros 3 serviços. |
| **7.7** (parcial) | `auth-service/.../infra/AuthService.java` | `cnpjTemDvValido`/`calcularDigitoVerificadorCnpj` novos — CNPJ sem DV válido → 400 antes de tocar o banco. Normalização mantém letras (alfanumérico-ready). Limite por IP/telefone e verificação de posse seguem pendentes (decisão de produto). |
| **7.10** | `billing-service/.../repository/SubscriptionRepository.java`, `billing-service/.../SubscriptionController.java` | `findByActivatedAtIsNotNull()` no lugar de `findAll()` no `/mrr`. |
| **7.11** | `auth-service/.../services/RolesService.java` | `getAllRoles()` (sem paginação) agora usa `findAll(PageRequest.of(0, 500))` — cap defensivo, contrato inalterado. |
| **7.12** | `gateway/.../filter/RateLimitFilter.java`, `gateway/.../filter/RateLimitFilterTest.java` | Bucket dedicado do 7.3 generalizado (`CRIAR_CONTA_PATH` → `ARGON2_PATHS`) pra cobrir também `/auth/login`, `/auth/tenant/login`, `/auth/partner/login`. |
| **7.7 teste** | `auth-service/.../infra/AuthService.java` (visibilidade), `auth-service/.../infra/AuthServiceTest.java` | `cnpjTemDvValido`/`calcularDigitoVerificadorCnpj` viraram package-private (eram `private`) só pra testar sem reflection. 6 casos novos: DV válido (CNPJ de exemplo oficial Serpro `11.222.333/0001-81`), DV inválido, alfanumérico (auto-consistente, sem vetor oficial), tamanho errado, `null`, DVs não-numéricos. |

## 8. Onde validar 2.3 e 2.4 (pendentes, não mexidos nesta sessão)

**2.3 — `TenantStatusCacheService` sem leitor** (mesmo achado do 7.8 original antes de renumerar; hoje é o mesmo problema descrito em 2.3 e em §4.8 acima — duas entradas pro mesmo achado):
- `billing-service/src/main/java/com/l/erp/billingservice/infra/redis/TenantStatusCacheService.java` — o `get(Long tenantId)` (linha 36) é o método sem caller.
- Escritores confirmados: `billing-service/.../services/webhook/handler/PaymentReceivedHandler.java` (`tenantStatusCache.put(...)`, ativação) e `billing-service/.../services/dunning/DunningService.java` (`tenantStatusCache.put(...)`, suspensão/cancelamento).
- Pra validar: `grep -rn "tenantStatusCache\.get\|TenantStatusCacheService" billing-service/src/main/java` — se continuar só achando `put`/`evict` e a declaração da classe, o `get()` segue morto. Decisão pendente (já adiada por você nesta sessão, aguardando revisão da spec de payments): plugar um leitor de verdade ou remover o cache inteiro.

**2.4 — `annualGuardScript` morto:**
- `billing-service/src/main/java/com/l/erp/billingservice/infra/config/RedisConfig.java:58-60` — bean `@Bean("annualGuardScript")` que carrega `lua/annualCommissionGuard.lua` via `RedisScript.of(...)`.
- Script em si: `billing-service/src/main/resources/lua/annualCommissionGuard.lua` (se existir no classpath — confirmar path exato com `find billing-service -iname "annualCommissionGuard.lua"`).
- Pra validar que está morto: `grep -rn "annualGuardScript" billing-service/src/main/java` — se o único resultado for a própria declaração do bean (`RedisConfig.java:58`), não tem injeção/uso em nenhum service. O motivo documentado (linha 17 deste arquivo) é que o modelo de comissão anual nunca existiu — comissão é sempre recorrente, criada via Kafka.
---

## 9. Follow-ups da re-revisão do commit `c2ae6d5` — 2026-07-21

Re-revisão manual do commit de correções da §7 (`c2ae6d5`). Três achados, todos corrigidos na branch `fix-audit-review` (**não rodado em CI** — testes locais verdes pelo usuário; ver `CLAUDE.md`).

| # | Achado | Correção | Teste |
|---|---|---|---|
| **9.1** | **Assinatura Asaas órfã no 409 do checkout (§7.8).** O `catch (DataIntegrityViolationException)` já tinha criado a subscription no Asaas antes do INSERT local estourar sob corrida TOCTOU — ela seguia cobrando o cliente sem linha local correspondente. | `CheckoutService.createCheckout`: o `catch` chama `asaasGateway.cancelSubscription(asaasSub.id())` antes de devolver 409; falha do cancel só loga (`log.error`) pra ação manual, ainda devolve 409. Customer criado é benigno (não cobra sozinho) → sem compensação. | `CheckoutServiceTest.concurrentCheckout_...cancelsOrphanAsaasSubscription_andReturns409` (+ `happyPath` passou a verificar `saveAndFlush`). |
| **9.2** | **Login de tenant não normalizava o CNPJ (§7.7-adjacente).** `criarContaGratis` grava o CNPJ sem pontuação e em maiúsculas, mas `loginWithTenant` fazia `findByCnpj(cnpj)` com o valor cru do request — quem digitasse `11.222.333/0001-81` no login não achava o próprio tenant (gravado `11222333000181`). | `AuthService.normalizeCnpj(String)` (sem pontuação + uppercase, alfanumérico-ready) extraído e usado nos **dois** caminhos (`loginWithTenant` e `criarContaGratis`). `TenantLoginRequest` regex passou a aceitar base `[A-Za-z0-9]{12}` + 2 DVs numéricos. | `AuthServiceTest.normalizeCnpj_removePontuacaoEUppercase`. |
| **9.3** | **Param tipado mal-formado virava 500.** Path/query var tipada (UUID/Long/enum/data) inválida estourava `MethodArgumentTypeMismatchException` sem handler → 500 (viola o padrão de erros: erro de cliente nunca vira 5xx). | `common/GlobalExceptionHandler`: handler novo → **400** com `StandardError` PT-BR (WARN 1 linha, sem stacktrace). | Sem teste dedicado (mapeamento trivial em infra do `common`; validado manualmente via curl). |

> Achado **falso-positivo** descartado na mesma revisão: a trava `hasRole('APP_OWNER')` nas rotas planas `/auth/roles`/`/auth/users` (M7) **não** quebra o portal do tenant — o `erp-front-end-web` usa só `/auth/tenant/security/**` (escopado). Confirmado por grep no frontend; ver `spec/seguranca-tenant-scoping.md` §M7. Nenhuma mudança necessária.
