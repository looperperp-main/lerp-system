# Permissões e Roles (RBAC) — catálogo e plano

**Status:** seed de permissões IMPLEMENTADO · roles a definir (PLANEJADO) · **Data:** 2026-06-25 · **Serviços:** `auth-service`, `liquibase-service`, consumidores: todos via `authorities` no JWT

**Decisões fechadas:** convenção de code = `DOMINIO_ACAO` · ações padrão **READ / INSERT / UPDATE / DELETE / STATUS** · permissões são **globais** (tabela `auth.permission` não tem `tenant_id`; o vínculo por tenant é em `role_permission`).

---

## Convenção

- `code`: `DOMINIO_ACAO` em UPPER_SNAKE (ex.: `CLIENTE_INSERT`).
- Ações: `READ` (listar/detalhar), `INSERT` (criar), `UPDATE` (editar), `DELETE` (remoção hard), `STATUS` (ativar/inativar — soft toggle).
- A ação por entidade reflete os endpoints que existem hoje (ver matriz). Entidades novas / endpoints novos → acrescentar linha no próximo `auth-schema-0NN.yaml`.
- Legado a manter como está: `PERMISSION_CREATE` (em vez de `_INSERT`) e o conjunto `TENANT_*` já inseridos manualmente.

## Seed

Implementado em `liquibase-service/.../auth/auth-schema-009.yaml`:
- `auth-018-permission-code-unique`: unique constraint em `permission.code` (invariante + habilita o ON CONFLICT). **Pré-requisito:** sem codes duplicados no ambiente.
- `auth-019-permission-seed`: INSERT multi-linha com `ON CONFLICT (code) DO NOTHING` → idempotente (DB novo insere tudo; DB existente pula o que já há). `created_by='system-seed'`.

## Matriz de permissões semeadas

| Domínio | READ | INSERT | UPDATE | DELETE | STATUS |
|---|:-:|:-:|:-:|:-:|:-:|
| CLIENTE | ✓ | ✓ | ✓ | | ✓ |
| FORNECEDOR | ✓ | ✓ | ✓ | | ✓ |
| TRANSPORTADORA | ✓ | ✓ | ✓ | | ✓ |
| PRODUTO | ✓ | ✓ | ✓ | ✓ | |
| PRODUTO_CATEGORIA | ✓ | ✓ | ✓ | | ✓ |
| GRUPO_CLIENTE | ✓ | ✓ | ✓ | | |
| TABELA_PRECO | ✓ | ✓ | ✓ | | ✓ |
| CONDICAO_PAGAMENTO | ✓ | ✓ | ✓ | | |
| CONDICAO_PAGAMENTO_PARCELA | ✓ | | ✓ (lote) | | |
| DEPOSITO | ✓ | ✓ | ✓ | | |
| VENDEDOR | ✓ | ✓ | ✓ | | |
| PESSOA | ✓ | ✓ | ✓ | | |
| CONTATO | ✓ | ✓ | ✓ | | |
| ENDERECO | ✓ | ✓ | ✓ | | |
| GRUPO_CLIENTE_TABELA_PRECO | ✓ | | ✓ | | |
| ROLE (auth) | ✓ | ✓ | ✓ | ✓ | |
| USER (auth) | ✓ | (existe) | (existe) | ✓ | ✓ |
| PERMISSION (auth) | ✓ | (CREATE existe) | (existe) | ✓ | |
| TENANT (auth) | já completo (READ/INSERT/UPDATE/DELETE) | | | | |

> A matriz espelha os endpoints atuais. Quando `delete`/`status` forem adicionados a uma entidade (ex.: hoje só PRODUTO tem delete), criar a permissão correspondente.

## Como a permissão chega na autorização

1. `auth.permission` (catálogo global) → `auth.role_permission` (liga role ↔ permission, com `tenant_id`) → `auth.user_role` (liga user ↔ role).
2. No login, `AuthService.getPermissions(userId)` coleta os `permission.code` via user_role→role_permission e injeta como claim **`authorities`** no JWT (`TokenService`).
3. O gateway lê `authorities` e popula os `GrantedAuthority`. **Hoje os controllers usam `@Secured(ROLE_*)`** — para autorização granular por permissão será preciso `@PreAuthorize("hasAuthority('CLIENTE_INSERT')")` nos endpoints (ainda NÃO aplicado).

## Próximos passos (roles — a decidir)

1. Definir o conjunto de **roles padrão por tenant** (ex.: `ADMIN`, `VENDAS`, `ESTOQUE`, `FINANCEIRO`, `SOMENTE_LEITURA`) e o mapa role→permissions.
   - Pergunta em aberto: roles padrão são **semeadas por tenant** na criação do tenant, ou um catálogo global clonado? (relacionado a M7 em `spec/seguranca-tenant-scoping.md` — separar owner GLOBAL vs TENANT).
2. Trocar `@Secured(ROLE_*)` por `@PreAuthorize(hasAuthority(...))` nos controllers de cadastro (e auth) conforme a matriz.
3. Tela de gestão no front-admin já existe (role-permissions / user-roles) — validar contra o catálogo novo.
4. Verificar `TENANT` precisa de `STATUS` quando houver endpoint de ativar/inativar tenant.