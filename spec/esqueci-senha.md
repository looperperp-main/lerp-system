# Esqueci minha senha (password reset) — Plano de implementação

**Status:** PARCIAL — **fluxo TENANT implementado + testado** (2026-06-30) · partner e throttle pendentes · **Serviço:** `auth-service`
**Data original:** 2026-06-23

**Decisões fechadas:** token **em DB single-use** (não JWT stateless) · escopo **Tenant + Parceiro** (admin de fora por ora).

## Status de implementação (2026-06-30)

**✅ Feito — fluxo TENANT (backend + unit test + frontend web):**
- Migration `auth/auth-schema-011.yaml` → `auth.password_reset_token` (id, `token_hash` SHA-256 único, `user_id` FK, `expires_at`, `used_at`, `created_at`) + entity `PasswordResetToken` + `PasswordResetTokenRepository`.
- `PasswordResetService` (novo, **não** dentro do `AuthService` — isolado p/ não tocar construtor/testes do AuthService): `solicitarResetTenant(email,cnpj)` e `redefinirSenha(token,nova,confirm)`.
- `PasswordResetController`: `POST /auth/tenant/esqueci-senha` + `POST /auth/redefinir-senha`.
- E-mail: case `RESET_SENHA` no `EmailConsumerService` (link `clientPortalUrl + /redefinir-senha?token=`). Publicado via `KafkaTemplate` no tópico `EmailNotificationService.EMAIL_TOPIC`; o payload leva o **token cru**, o consumer monta o link.
- Revogação: `RefreshTokenRepository.revokeAllActiveByUserId` (novo) chamado na redefinição; zera `failedLoginAttempts`/`lockedUntil`.
- Rotas públicas: auth `SecurityConfig` permitAll + gateway `SecurityFilter.PUBLIC_PATHS`.
- Teste: `PasswordResetServiceTest` (8 casos) — token inválido/usado/expirado→422, senhas≠→400, anti-enumeração no-op (tenant/user inexistente), happy revoga sessões + zera lockout.
- Frontend `erp-front-end-web`: `pages/esqueci-senha/` (`password-reset.service`, página `/esqueci-senha` CNPJ+email, página `/redefinir-senha?token=` nova+confirmação) + link "Esqueceu sua senha?" no login.

**✅ Throttle + validade (2026-06-30):**
- **Throttle de 120s** no `solicitarResetTenant`: `findFirstByUser_IdOrderByCreatedAtDesc` → se o último token (não usado) foi criado há < 120s, no-op silencioso (reaproveita o pedido em voo, sem novo e-mail; mantém o 200 genérico). Reusa a tabela, sem Redis. Cobre e-mail bombing / abuso de cota SMTP. Testes `solicitar_recentTokenWithinCooldown_skipsEmail` / `solicitar_oldTokenBeyondCooldown_sendsNew`.
- **Validade do token: 7 dias** (`TOKEN_TTL_DAYS = 7`) — textos de e-mail e da página `/esqueci-senha` atualizados.

**⏳ Pendente:**
- **Fluxo PARCEIRO** (`POST /auth/partner/esqueci-senha`, body `{ email }` → `findByEmail` com `userType=PARTNER`) + link/páginas no portal do parceiro. `/auth/redefinir-senha` já é compartilhado (token aponta pro user).
- **Purga** de tokens expirados/usados (hoje só o refresh token tem `@Scheduled purge`).
- **e2e manual**: subir stack + e-mail real, solicitar → abrir link → redefinir → login com nova senha; confirmar revogação das sessões antigas.

## Forma da API (espelha o split tenant/partner do login)
- `POST /auth/tenant/esqueci-senha` — body `{ email, cnpj }` → resolve tenant por CNPJ + `findByEmailAndTenantId`
- `POST /auth/partner/esqueci-senha` — body `{ email }` → `findByEmail` com `userType=PARTNER`
- `POST /auth/redefinir-senha` — body `{ token, novaSenha, confirmacaoSenha }` — **compartilhado** (o token aponta pro user, que já carrega o `userType`)

## Fases
1. **Persistência** — Liquibase `auth/auth-schema-010.yaml` (009 passou a ser o seed de permissões): tabela `auth.password_reset_token` (`user_id` FK, `token_hash` SHA-256, `expires_at` ~30min, `used_at`, `created_at`) + entity + repo. Token aleatório forte, guarda **só o hash** (padrão do `RefreshToken`), uso único.
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