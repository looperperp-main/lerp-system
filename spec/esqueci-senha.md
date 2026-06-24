# Esqueci minha senha (password reset) — Plano de implementação

**Status:** PLANEJADO (não iniciado) · **Data:** 2026-06-23 · **Serviço:** `auth-service`

**Decisões fechadas:** token **em DB single-use** (não JWT stateless) · escopo **Tenant + Parceiro** (admin de fora por ora).

## Forma da API (espelha o split tenant/partner do login)
- `POST /auth/tenant/esqueci-senha` — body `{ email, cnpj }` → resolve tenant por CNPJ + `findByEmailAndTenantId`
- `POST /auth/partner/esqueci-senha` — body `{ email }` → `findByEmail` com `userType=PARTNER`
- `POST /auth/redefinir-senha` — body `{ token, novaSenha, confirmacaoSenha }` — **compartilhado** (o token aponta pro user, que já carrega o `userType`)

## Fases
1. **Persistência** — Liquibase `auth/auth-schema-009.yaml`: tabela `auth.password_reset_token` (`user_id` FK, `token_hash` SHA-256, `expires_at` ~30min, `used_at`, `created_at`) + entity + repo. Token aleatório forte, guarda **só o hash** (padrão do `RefreshToken`), uso único.
2. **Solicitação** — `AuthService.solicitarResetTenant/Partner`: resolve user → gera token → salva hash → publica Kafka `type=RESET_SENHA` `{email, nome, link, userType}`. **200 genérico sempre** (anti-enumeração, igual ao `/auth/criar-conta`). Throttle por e-mail.
3. **Redefinição** — valida token (existe / não usado / não expirado) → confere senhas + `PasswordValidatorUtil` (14+ chars, 4/4 grupos, blacklist) → `setPasswordHash(encode())` → marca `used_at` → **revoga refresh tokens do user** (`RefreshTokenService` talvez precise de `revokeAllForUser` — hoje só tem `revoke`/`revokeFamily`) → zera lockout (`failedLoginAttempts`/`lockedUntil`) → audit.
4. **E-mail** — `EmailConsumerService` novo case `RESET_SENHA` → `sendResetSenhaEmail`. Link por portal: tenant = `app.client-portal-url`, partner = `app.partner-portal-url` (essa config pode não existir — adicionar) + `/redefinir-senha?token=`.
5. **Rotas públicas** — `auth/SecurityConfig` permitAll + `gateway/SecurityFilter.PUBLIC_PATHS` + `gateway/SecurityConfig`. Constants (common): `PASSWORD_RESET_REQUESTED` / `PASSWORD_RESET_COMPLETED`.
6. **Frontend** — portal web + portal parceiro (link "Esqueci minha senha" + página de solicitação + página de redefinição lendo `token` da query; nunca `[innerHTML]`).
7. **Testes** — `@WebMvcTest` + serviço (token expirado/usado/inválido, senha fraca, 200 anti-enumeração, revogação de refresh tokens) + e2e manual.

## Reaproveita (já existe no auth-service)
- **Molde:** `AuthService.ativarConta` (valida token → confere senhas → `PasswordValidatorUtil` → `setPasswordHash` → audit)
- **E-mail Kafka:** tópico `user-welcome-email-topic` + dispatch por `type` no `EmailConsumerService`; link estilo `clientPortalUrl + "/ativar?token="`
- **Token DB hash:** padrão do `RefreshToken` / `RefreshTokenService`
- `passwordEncoder` (BCrypt) + `PasswordValidatorUtil`
- E-mail **não é único globalmente** (`findByEmailAndTenantId`) — por isso o fluxo tenant exige CNPJ.