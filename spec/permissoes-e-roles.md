# Permissões e Roles (RBAC) — catálogo e plano

**Status:** seed de permissões IMPLEMENTADO · roles a definir (PLANEJADO) · **Data:** 2026-06-25 · **Serviços:** `auth-service`, `liquibase-service`, consumidores: todos via `authorities` no JWT

**Decisões fechadas:** convenção de code = `DOMINIO_ACAO` · ações padrão **READ / INSERT / UPDATE / DELETE / STATUS** · permissões são **globais** (tabela `auth.permission` não tem `tenant_id`; o vínculo por tenant é em `role_permission`).

> **Status atualizado (2026-07-03, verificado no código):**
> - O seed evoluiu além do `auth-schema-009`: **`auth-schema-010`** adicionou a coluna **`scope`** em `auth.permission` (TENANT = atribuível pelo portal do tenant vs plataforma) + novas permissões; **`auth-schema-012`** semeou permissões `ROLE_*` adicionais com `scope=TENANT`. A decisão "permissões são globais" continua valendo para o catálogo, mas agora há dimensão de escopo — a matriz abaixo não reflete os seeds 010/012.
> - **`@PreAuthorize(hasAuthority(...))` JÁ APLICADO no auth-service** (`RoleController`, `PermissionController`, `AttributionsController`). No **cadastro-service** segue sem nenhuma anotação de autorização (nem `@Secured` nem `@PreAuthorize`) — passo 2 dos próximos passos está metade feito.
> - Roles padrão por tenant: **parcialmente implementado** — na criação/ativação do tenant (`AuthService.ativarConta` e `criarContaGratis`), o `TenantOwnerBootstrapService.bootstrapOwner` cria a role **PROPRIETARIO** do tenant com as permissões dos domínios de segurança (`PERMISSION`, `USER`, `ROLE`) e a atribui ao usuário owner. As demais roles padrão (VENDAS, ESTOQUE, etc.) continuam a decidir.
> - **Claim `roles` do JWT: ✅ RESOLVIDO (2026-07-03)** — antes era hardcoded (`isOwner ? [APP_OWNER, TENANT_OWNER] : ["ROLE_USER"]`, TODO intacto), ignorando a tabela `user_role` mesmo com a role **PROPRIETARIO** já atribuída de verdade pelo bootstrap acima. Agora `AuthService.resolveRoles(userId, isOwner)` busca as roles reais via `userRoleRepository.findAllByUserId(...).map(ur -> ur.getRole().getName())`; `isOwner` virou só uma flag adicional (`APP_OWNER`), não substitui mais a role real. Detalhe completo em `spec/auditoria.md` §4.11 e `spec/seguranca-tenant-scoping.md` (M7).

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
   > **Atualização 2026-07-03:** aplicado no **auth-service** (Role/Permission/Attributions controllers, e o gateway propaga `authorities` via header `X-Authorities`); pendente apenas no **cadastro-service**.

## Próximos passos (roles — a decidir)

1. **PARCIAL** — Role **PROPRIETARIO** já é criada por tenant na ativação/criação de conta (`TenantOwnerBootstrapService.bootstrapOwner`), com as permissões de segurança (`PERMISSION`, `USER`, `ROLE`) e vínculo ao owner. Falta definir as demais roles padrão (ex.: `VENDAS`, `ESTOQUE`, `FINANCEIRO`, `SOMENTE_LEITURA`) e o mapa role→permissions.
2. **EM ANDAMENTO** — Trocar `@Secured(ROLE_*)` por `@PreAuthorize(hasAuthority(...))`: feito no auth-service; cadastro-service em andamento.
3. **FUNCIONANDO** — Tela de gestão no front-admin (role-permissions / user-roles) validada contra o catálogo novo.
4. **NÃO IMPLEMENTADO** (verificado 2026-07-03) — Já existe `PATCH /tenants/{tenantId}/status` no `TenantController`, mas ele usa `@PreAuthorize(hasAuthority('TENANT_DELETE'))`; a permissão `TENANT_STATUS` não existe em nenhum seed do liquibase-service. Pendente: semear `TENANT_STATUS` e trocar a authority do endpoint.