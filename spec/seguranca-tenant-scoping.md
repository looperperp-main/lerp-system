# Tenant-scoping / IDOR cross-tenant — Plano de correção

**Status:** PLANEJADO (não iniciado) · **Data:** 2026-06-25 · **Serviços:** `cadastro-service` (M8, foco) · `auth-service` (M7) · `gateway` (defense-in-depth)

> **Status atualizado (2026-07-03, verificado via grep no código):**
> - **M8: ✅ IMPLEMENTADO** — os 4 services usam `findByIdAndTenantId`; delete do Produto via `deleteByIdAndTenantId`; referências filhas escopadas. Pendente confirmar só o passo 5 (testes cross-tenant → 404 por entidade).
> - **M7: ✅ IMPLEMENTADO (2026-07-15)** — `AuthService.resolveRoles()` já lê as roles reais do usuário em `user_role` em vez de `List.of(APP_OWNER, TENANT_OWNER)`/`"ROLE_USER"` fixo; `//TODO: get roles from database` resolvido. IDOR em `UserController`/`RoleController`/`AttributionsController` (rotas planas `/auth/users`, `/auth/roles`) corrigido adicionando `hasRole('APP_OWNER')` a todo `@PreAuthorize`, restringindo essas rotas ao portal de staff (`erp-front-end-admin`) — o portal do tenant (`erp-front-end-web`) já usava só `/auth/tenant/security/**`, escopado. `UserController.updateUserById` (reatribuição de `tenantId`) fica coberto pela mesma restrição: só APP_OWNER chega nele agora. Pendente só o passo 5 (testes cross-tenant → 404/403 por entidade; testes de negação sem `APP_OWNER` já cobertos, ver seção M7 abaixo).
> - **Defense-in-depth gateway: ✅ IMPLEMENTADO** — `PROTECTED_HEADERS` + strip case-insensitive no wrapper do `SecurityFilter`. Gap residual: strip só roda no caminho autenticado (ver `spec/auditoria.md` §4.4).
> - Contexto vencido: "downstream rodam permitAll" — billing e partner agora têm `InternalRequestFilter`; só o cadastro segue `permitAll` puro (ver `spec/auditoria.md` §4.1).

**Decisões fechadas:** padronizar acesso por-id em **`findByIdAndTenantId(id, tenantId)`** (escopo na query, não carrega registro de outro tenant) · `orElseThrow` → **404 NOT_FOUND** (não 403, para não vazar existência) · M7 aguarda a definição final das roles antes de implementar.

---

## Contexto / raiz do problema

O multi-tenancy do `cadastro-service` depende do `@Filter` do Hibernate (`tenantFilter`, definido em `repository/filter/BaseTenantEntity.java`, ligado por `repository/filter/TenantFilterAspect.java`). **Esse filtro só atua em queries (HQL/criteria/`findAll`) — NÃO atua em load por chave primária** (`EntityManager.find` / Spring Data `findById` / `deleteById`). É comportamento documentado do Hibernate.

Consequência: services que carregam por `findById(id)` **sem checar tenant** permitem que um usuário do tenant A — adivinhando/enumerando um UUID — **leia, altere ou exclua** registros do tenant B.

`tenant_id` é `@Column(updatable=false)` em `BaseTenantEntity`, então **não** dá para mover/roubar o registro para outro tenant. Mas leitura/escrita/exclusão cross-tenant já é quebra de isolamento (vazamento LGPD + integridade).

O header `X-Tenant-Id` em si é confiável: o `gateway/SecurityFilter` injeta via wrapper de request que sobrescreve `getHeader/getHeaders`, mascarando header forjado pelo cliente. `cadastro-service` lê via `SecurityUtils.getCurrentTenantId()` (header) e os downstream rodam `permitAll` confiando no gateway.

---

## M8 — `cadastro-service` (foco)

> ✅ **RESOLVIDO (confirmado 2026-07-03):** os 4 services abaixo foram corrigidos — `findByIdAndTenantId` em read/update/updateStatus, `deleteByIdAndTenantId` no delete do Produto e referências filhas (categoria/fornecedor/tabela de preço) escopadas por tenant.

### Vulneráveis (sem guard de tenant — corrigir)
| Service | Operações | Observação |
|---|---|---|
| `ProdutoService` | `findById` (read), `update`, **`delete`** | `delete` faz `produtoRepository.deleteById(id)` **sem nenhuma checagem**. Além disso anexa `categoria/deposito/fornecedor/tabelaPreco` por `findById` bare (referência cross-tenant). |
| `TransportadoraService` | `findById`, `update`, `updateStatus` | |
| `FornecedorService` | `findById`, `update`, `updateStatus` | `update` só valida `pessoaId`, nunca o tenant do próprio fornecedor. |
| `TabelaPrecoService` | `findById`, `update`, `updateStatus` | |

### Já corretos (padrão a replicar — não mexer)
- `ProdutoCategoriaService`, `ClienteService` → usam `findByIdAndTenantId`.
- `GrupoClienteService`, `CondicaoPagamentoService`, `DepositoService`, `PessoaService` → `findById` + check manual `getTenantId().equals(tenantId)`.

> A inconsistência prova que o time já conhece a limitação do filtro; falta só uniformizar.

### Passos
1. **Acesso por-id → `findByIdAndTenantId(id, tenantId)`** em read/`update`/`updateStatus` dos 4 services vulneráveis. Adicionar os métodos nos repositórios que faltarem. `orElseThrow` → `BusinessException(..., NOT_FOUND)`.
2. **DELETE** (`ProdutoService.delete`): usar `deleteByIdAndTenantId` (ou load via `findByIdAndTenantId` + remove). Nunca `deleteById(id)` puro.
3. **Produto — referências filhas:** validar `categoriaId`, `depositoId`, `fornecedorId`, `tabelaPrecoId` por `findByIdAndTenantId(..., tenantId)` antes de anexar (hoje vêm por `findById` bare).
4. **`@Filter`/`TenantFilterAspect`:** manter apenas para listagens (`findAll`); **documentar no código** que NÃO protege acesso por-id.
5. **Testes:** GET/PUT/PATCH/DELETE by-id de tenant A contra recurso de tenant B → **404**, por entidade.

---

## M7 — `auth-service` (✅ implementado 2026-07-15)

IDOR cross-tenant no CRUD de usuários/roles + níveis de owner conflados. Contido hoje porque tenant users normais logam por `loginWithTenant` (sempre `ROLE_TENANT_USER`, `isOwner=false`).

> Field injection para virar owner é **impossível**: owner vem da tabela `owner_marker`, nunca do body; nenhum endpoint grava nela; o PUT enumera campos.

> ✅ **Roles-from-DB (verificado 2026-07-15):** o `//TODO: get roles from database` sumiu. `AuthService.resolveRoles(userId, isOwner)` monta a claim `roles` a partir de `userRoleRepository.findAllByUserId(userId)` — mesma tabela `user_role` que já alimentava `authorities` via `getPermissions` — só adicionando `APP_OWNER` como flag extra quando `isOwner=true`. Usado em `login()` e `generateJwtForUser()`. `owner_marker` continua único por instalação (`auth-schema-013`), então o cenário de owner_marker por-tenant (antigo passo 1) não se aplica mais.

> ✅ **IDOR RoleController/UserController/AttributionsController — corrigido (2026-07-15):** `RolesService`/`AttributionsService`/`UserService` têm dois conjuntos de métodos — os `*ForTenant` (escopados, usam `assertRoleInTenant`/`assertUserInTenant`) chamados só por `TenantSecurityController`, e os "planos" (sem checagem de tenant nenhuma) chamados por `UserController`, `RoleController` e `AttributionsController`. Confirmado via investigação (grep no frontend) que essas rotas planas são exclusivas do portal de staff `erp-front-end-admin` (`role.service.ts`, `users.service.ts`, `user-role.service.ts`, `role-permission.service.ts`) — o portal do tenant (`erp-front-end-web`) usa só `/auth/tenant/security/**`. O problema era que `@PreAuthorize("hasAuthority(...)")` sozinho não distingue staff de tenant owner: `TenantOwnerBootstrapService.bootstrapOwner()` dá a **todo** dono de tenant as mesmas authorities (`USER_UPDATE`, `ROLE_DELETE`, `PERMISSION_UPDATE` etc., domínios `PERMISSION`/`USER`/`ROLE`) pra ele gerenciar o próprio tenant via as rotas `ForTenant` — as mesmas authorities liberavam as rotas planas, sem checar dono do `userId`/`roleId` no path.
>
> **Fix aplicado:** adicionado `hasRole('APP_OWNER')` a todo `@PreAuthorize` de `UserController`, `RoleController` e `AttributionsController` (mesmo padrão já usado em `SyaxQueueController`/`TrialTriggerController`/`AuditController`). `Roles.APP_OWNER = "ROLE_APP_OWNER"` já vem pronto pra `hasRole()` (JwtFilter injeta a claim `roles` direto como `GrantedAuthority`, sem prefixo extra). Como só APP_OWNER passa agora, o problema de `updateUserById` reatribuir `tenantId` livre também fica coberto — só staff da plataforma chega nesse endpoint. `getAllRoles()` (`findAll()` sem filtro de tenant) continua cross-tenant de propósito — é o comportamento esperado pro portal de staff enxergar roles de todos os tenants; só ficou mais seguro por estar atrás do mesmo `hasRole('APP_OWNER')`.
>
> Testes atualizados: `RoleControllerTest`/`UserControllerTest`/`AttributionsControllerTest` ganharam `ROLE_APP_OWNER` nos `@WithMockUser` existentes (senão quebrariam) + 1 teste novo por controller (`shouldDenyAccessWithoutAppOwnerRole`) confirmando 403 sem o role. **Não rodado** (`./mvnw test -pl auth-service` fica por conta do usuário, ver `CLAUDE.md`).

### Pendente
- Testes de regressão end-to-end com TENANT_OWNER real (JWT emitido de verdade, não só `@WithMockUser`) tentando `/auth/roles/{id}` de outro tenant → confirmar 403 em ambiente rodando.
- Se o portal de staff algum dia precisar que um TENANT_OWNER administre parcialmente via essas rotas planas (hoje não precisa — ele já tem `/auth/tenant/security/**`), a trava por `hasRole('APP_OWNER')` vai bloquear; reavaliar se isso mudar.

---

## Defense-in-depth — `gateway`

Stripar explicitamente os headers de entrada `X-Tenant-Id` / `X-User-Id` / `X-Is-Owner` / `X-Partner-Id` do request do cliente **antes** de injetar os valores derivados do JWT no `SecurityFilter` — em vez de depender só da semântica do wrapper (`getHeader/getHeaders`). Reduz risco se algum caminho de proxy ler headers fora do wrapper.

> ✅ **RESOLVIDO (confirmado 2026-07-03):** `SecurityFilter` tem `PROTECTED_HEADERS` (inclui também `X-User-Email` e `X-Authorities`) com mascaramento case-insensitive em `getHeader`/`getHeaders`/`getHeaderNames`. Gap residual: o strip só roda no caminho autenticado; em `PUBLIC_PATHS` a request original segue com headers forjados — ver `spec/auditoria.md` §4.4.

---

## Fora de escopo (já OK — não mexer)
- Mass-assignment de `tenantId` no create: services setam `tenantId` do `TenantContext` (confiável) via builder, ignorando o DTO. `tenant_id` é `updatable=false`.
- Spoof de `X-Tenant-Id`: mascarado pelo wrapper do gateway.