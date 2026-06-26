# Tenant-scoping / IDOR cross-tenant — Plano de correção

**Status:** PLANEJADO (não iniciado) · **Data:** 2026-06-25 · **Serviços:** `cadastro-service` (M8, foco) · `auth-service` (M7) · `gateway` (defense-in-depth)

**Decisões fechadas:** padronizar acesso por-id em **`findByIdAndTenantId(id, tenantId)`** (escopo na query, não carrega registro de outro tenant) · `orElseThrow` → **404 NOT_FOUND** (não 403, para não vazar existência) · M7 aguarda a definição final das roles antes de implementar.

---

## Contexto / raiz do problema

O multi-tenancy do `cadastro-service` depende do `@Filter` do Hibernate (`tenantFilter`, definido em `repository/filter/BaseTenantEntity.java`, ligado por `repository/filter/TenantFilterAspect.java`). **Esse filtro só atua em queries (HQL/criteria/`findAll`) — NÃO atua em load por chave primária** (`EntityManager.find` / Spring Data `findById` / `deleteById`). É comportamento documentado do Hibernate.

Consequência: services que carregam por `findById(id)` **sem checar tenant** permitem que um usuário do tenant A — adivinhando/enumerando um UUID — **leia, altere ou exclua** registros do tenant B.

`tenant_id` é `@Column(updatable=false)` em `BaseTenantEntity`, então **não** dá para mover/roubar o registro para outro tenant. Mas leitura/escrita/exclusão cross-tenant já é quebra de isolamento (vazamento LGPD + integridade).

O header `X-Tenant-Id` em si é confiável: o `gateway/SecurityFilter` injeta via wrapper de request que sobrescreve `getHeader/getHeaders`, mascarando header forjado pelo cliente. `cadastro-service` lê via `SecurityUtils.getCurrentTenantId()` (header) e os downstream rodam `permitAll` confiando no gateway.

---

## M8 — `cadastro-service` (foco)

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

## M7 — `auth-service` (relacionado, aguarda definição de roles)

IDOR cross-tenant no CRUD de usuários/roles + níveis de owner conflados. Endpoints `@Secured({APP_OWNER, TENANT_OWNER})` carregam alvo por `findById` sem checar tenant do caller; e `AuthService.login()` concede `List.of(APP_OWNER, TENANT_OWNER)` juntos para qualquer `owner_marker`. Contido hoje porque tenant users normais logam por `loginWithTenant` (sempre `ROLE_TENANT_USER`, `isOwner=false`); vira crítico ao emitir owner_marker por-tenant.

> Field injection para virar owner é **impossível**: owner vem da tabela `owner_marker`, nunca do body; nenhum endpoint grava nela; o PUT enumera campos.

### Passos (depois de definir as roles)
1. Distinguir owner **GLOBAL** (staff Syax → `APP_OWNER`) de **TENANT** (dono empresa → `TENANT_OWNER`), ex.: campo `scope` no `owner_marker`.
2. `AuthService` (`login` + `generateJwtForUser`): derivar a role do tipo de marker, em vez de `List.of(APP_OWNER, TENANT_OWNER)`. Resolve o `//TODO: get roles from database`.
3. Helper `assertCanManage(targetTenantId)` (APP_OWNER passa; TENANT_OWNER exige `== SecurityUtils.getCurrentTenantId()`).
4. Aplicar após cada `findById`: `UserService.updateUserById/updateUserStatusById/createUser`, `AttributionsService.*`, `RolesService.assign/removePermission/delete/updateRole`.
5. Testes: TENANT_OWNER do tenant A → 403 em recurso do tenant B; APP_OWNER passa; regressão de que TENANT_OWNER não recebe APP_OWNER na claim.

---

## Defense-in-depth — `gateway`

Stripar explicitamente os headers de entrada `X-Tenant-Id` / `X-User-Id` / `X-Is-Owner` / `X-Partner-Id` do request do cliente **antes** de injetar os valores derivados do JWT no `SecurityFilter` — em vez de depender só da semântica do wrapper (`getHeader/getHeaders`). Reduz risco se algum caminho de proxy ler headers fora do wrapper.

---

## Fora de escopo (já OK — não mexer)
- Mass-assignment de `tenantId` no create: services setam `tenantId` do `TenantContext` (confiável) via builder, ignorando o DTO. `tenant_id` é `updatable=false`.
- Spoof de `X-Tenant-Id`: mascarado pelo wrapper do gateway.