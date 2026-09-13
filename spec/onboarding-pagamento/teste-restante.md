# Testes restantes — payment-service (Fases 4–7 + classes de teste)

Checklist dos testes a fazer **depois da Fase 3** (que já está implementada e validada manualmente —
ver `teste-fase3-checkout.md`). Cobre: (A) classes de teste automatizadas a escrever e (B) testes
manuais/e2e por fase.

> **Alinhamentos com o estado real do repo** (divergem da spec genérica):
> - Status da subscription usa **`ATIVA`** (não `ATIVO`), `AGUARDANDO_PAGAMENTO`, `SUSPENSO`, `CANCELADO`.
> - Cache de status é **write-through (put)**, nunca evict.
> - **Fase 5 (Status API síncrona `GET /internal/billing/status`) foi DESCARTADA** — auth é event-driven
>   (`SubscriptionActivatedConsumer` consome `billing.subscription.activated`). Os cenários §22 de
>   `GET /internal/billing/status` (402/200) **não se aplicam**; testar, no lugar, o consumer no auth.
>   ⚠️ **GAP da Fase 5 (pareia com Fase 7):** hoje billing só publica `billing.subscription.activated` e
>   auth só consome esse. **Não há** evento/consumer de suspensão/cancelamento — então um tenant
>   SUSPENSO/CANCELADO no billing **continua conseguindo logar** (o `loginWithTenant` só barra quem não é
>   ATIVO/TRIAL, mas nada muda o `Tenant.status` no auth). Implementar junto da Fase 7:
>   billing publica `billing.subscription.suspended`/`.cancelled` no DunningJob → auth consome e seta
>   `Tenant.status = SUSPENSO/CANCELADO`.
> - **Comissão é criada via Kafka — trilha única (decidido 2026-06-24, Fase 4 FEITA).** billing publica
>   `billing.subscription.activated` → partner-service calcula → `partner.commission.calculated` →
>   `PartnerCommissionCalculatedConsumer` → **`CommissionEngine.generate`** (Strategy/Factory em
>   `services/commission/`). O engine **não** é ligado ao `PaymentReceivedHandler` (evita 2ª trilha). O
>   `amount` vem pronto do partner-service; o billing só persiste e deriva `base_value` da subscription.
> - **Fase 6 (payout PIX) IMPLEMENTADA (2026-06-28).** PIX-only (sem TED). billing lê `pix_key`+`pix_key_type`
>   direto de `partner.partner` (mesmo Postgres, `JdbcTemplate` read-only — sem HTTP). Colunas reais:
>   `commission.asaas_transfer_id` + `paid_at` (NÃO `payout_asaas_id`/`confirmed_at`); sem
>   `transfer_failed_reason`/`adjusted_amount` → `failReason` só logado, `effectiveAmount`=`amount`.
>   Idempotência de lote por `externalReference=payout-{partnerId}-{period}` + check-then-act (sem retry cego);
>   **não** há `payout_asaas_id UNIQUE`. 1 transfer cobre N comissões do parceiro/período.

---

## A. Classes de teste automatizadas a escrever

### A.0 Fase 3 — retroativo — ✅ FEITO (2026-06-24, 23 testes, `mvnw -pl billing-service test`)
- [x] `AsaasGatewayTest` (unit, Mockito + CB falso): 4xx→`AsaasValidationException` (1 chamada, sem retry), 5xx→`AsaasException` (retenta até `maxAttempts`), sucesso retorna id.
- [x] `CheckoutServiceTest` (unit): plano inexistente→404 sem tocar o Asaas; happy persiste `AGUARDANDO_PAGAMENTO` + `CheckoutResponse` com boleto/PIX; PIX QR indisponível (`AsaasException`) não quebra (campos null).
- [x] `PaymentReceivedHandlerTest` (unit): ATIVA + `activatedAt` + `next_due_date` do Asaas + write-through cache + Kafka; guard CANCELADO (não reativa); valor divergente ativa e evento usa valor recebido (§28.8); falha do gateway→`TransientException` (não consulta o banco).
- [x] `WebhookProcessorTest` (unit): duplicata descartada (sem rotear), sem handler→IGNORADO+markDone, sucesso→markDone+markProcessed, transient→release+markError(TRANSIENT), permanente→markError(chave mantida).
- [x] `WebhookSecurityServiceTest` (unit): token válido passa; inválido/null→`WebhookAuthException` (constant-time).
- [x] `WebhookLogServiceTest` (unit): tolera duplicata `asaas_event_id` (retorna existente, sem save); preenche `tenant_id` via subscription; `markProcessed` seta status+timestamp.

### A.1 Unitários — Fases 4/6/7 (spec §22.1)
- [x] `RecurrentCommissionStrategyTest` (✅ 2026-06-24): `base_value` = mensalidade (`value`, ou `value÷12` se ciclo `YEARLY`); `amount` é o que veio no comando (não recalcula taxa); status `PENDENTE`/model `RECORRENTE`/`asaas_payment_id`/tenant setados.
- [x] `CommissionEngineTest` (✅ 2026-06-24): pré-check de duplicado descarta antes do lookup; subscription inexistente não gera; happy salva `PENDENTE`; corrida → catch `DataIntegrityViolationException` sem estourar.
- [x] `CommissionPayoutServiceTest` ✅ (2026-06-28): parceiro **sem PIX** é pulado; happy marca `EM_TRANSFERENCIA` + `asaas_transfer_id`; `transferId=null` → deixa PENDENTE.
- [x] `TransferCompletedHandlerTest` ✅: `status=PAGO` + `paid_at` (TODAS as comissões do `asaas_transfer_id`); sem comissão → no-op; sem transfer.id → erro.
- [x] `TransferFailedHandlerTest` ✅: volta `status=PENDENTE` + limpa `asaas_transfer_id`; loga `failReason`.
- [x] `AsaasGatewayTest` ✅ atualizado p/ `transferClient` no ctor.
- [x] `RolesServiceGuardTest` (auth) ✅: conceder permissão `PLATFORM` sem `PLATFORM_PERMISSION_ASSIGN` → 403 (mockStatic SecurityUtils).
- [ ] `DistributedLockService`/`CommissionPayoutJob` (lock acquire/release + skip; IT) — pendente.
- [ ] `ReconciliationJob` (Fase 7).

### A.2 Integração — Testcontainers (PostgreSQL + Redis) (spec §22.2)
- `PAYMENT_RECEIVED`→`ATIVA` + cache write-through; duplicado→processado 1×.
- `PAYMENT_RECEIVED` com parceiro→`commission` PENDENTE + valor correto.
- `PAYMENT_OVERDUE`→`suspend_at=now+5d`, `cancel_at=now+7d`.
- `SUBSCRIPTION_INACTIVATED`→só grava `asaas_inactivated_at` (status não muda).
- `TRANSFER_COMPLETED`→`PAGO`+`confirmed_at`; `TRANSFER_FAILED`→`PENDENTE`+`payout_asaas_id=null`+notifica; e reentra no próximo D+1.
- `DunningJob`: lembrete (`suspend_at-2d<=now` e `reminder_sent_at` null); suspensão (`suspend_at<=now`→SUSPENSO+cache); cancelamento (`cancel_at<=now`→CANCELADO + comissões PENDENTE canceladas).
- `ReconciliationJob`: pagamento no Asaas não refletido→ativa+comissão; idempotente por `asaas_payment_id`.
- `WebhookRecoveryJob`: webhook preso em `RECEBIDO` >10min→reprocessado (idempotência Redis protege).

### A.3 Contract tests — WireMock (Asaas) (spec §22.3)
- `POST /customers`→200 com id; `POST /subscriptions`→200 com boleto+PIX.
- `POST /transfers` PIX→200 `tra_xxx` PENDING; chave PIX inválida→400→`AsaasValidationException`.
- `GET /payments` paginado (`hasMore` true→false).
- Timeout→retry 3×→circuit breaker abre→fallback sem exception.

> Convenção do repo: `@WebMvcTest`+MockMvc+Mockito p/ controllers; H2/Testcontainers p/ repo/IT.
> JaCoCo exige ≥40% de instrução no `verify` (DTO/mapper/domain/config excluídos).

---

## B. Testes manuais / e2e por fase (curl + DB + Asaas sandbox)

Pré: infra de pé (`postgres/redis/kafka/zookeeper`), billing rodando, env do Asaas sandbox correta
(`ASAAS_BASE_URL` sem aspas!), plano semeado. Reusar a assinatura/`sub_xxx` criada na Fase 3 ou criar nova.

### Fase 4 — Engine de comissões (trilha Kafka; engine alimentado pelo consumer) — ✅ VERIFICADO MANUALMENTE (2026-06-27)
- [x] Webhook `PAYMENT_RECEIVED` de um tenant **com parceiro vinculado** → após o ciclo Kafka, `billing.commission` com `status=PENDENTE`, `partner_id`, `amount` (calculado pelo partner-service = `value × commission_rate`), `commission_model=RECORRENTE`, `base_value` = mensalidade da subscription, `period=YYYY-MM`. **Obs:** partner-service calcula o `amount` sobre o **valor cheio do evento** (ANUAL 479×5%=23.95), enquanto o billing deriva `base_value` da subscription.
- [x] **Idempotência:** reenviar mesmo `asaas_payment_id` (replay do `partner.commission.calculated`) → **não** duplica comissão (pré-check `existsByAsaasPaymentId` + UNIQUE `uq_commission_asaas_payment`).
- [x] Tenant **sem** parceiro → ativa, mas partner-service não emite `partner.commission.calculated` → **nenhuma** comissão (consumer loga "Nenhum PartnerReferral encontrado").
- [x] `PAYMENT_DELETED` do mesmo pagamento → comissão associada vira `CANCELADO` (`PaymentDeletedHandler`; só usa `payment.id`, ignora `status`).
- [x] `base_value` prorateado: subscription com `billing_cycle=YEARLY` → `base_value = value÷12` (479→39.92; o `amount` segue vindo do partner).
- [x] Conferir no Kafka UI (:8080) o fluxo `billing.subscription.activated` → `partner.commission.calculated` → comissão gravada pelo `CommissionEngine`.
- [x] **Payout desabilitado:** `POST /api/v1/commissions/admin/trigger-repasse` (header `X-User-Id` obrigatório, senão 401) → 200 "Repasse desabilitado até a Fase 6 (payout PIX real). Nenhuma comissão alterada."; nenhuma comissão muda para `PAGO`.

> **Gotchas do teste manual (2026-06-27):** (1) no PowerShell usar `Invoke-RestMethod` — `curl` é alias bugado; token do webhook = `ASAAS_WEBHOOK_TOKEN` (não a API key). (2) O tenant ativado vem do `X-Tenant-Id` gravado na subscription **no checkout**; o webhook só relê via `findByAsaasSubscriptionId`. (3) `uq_subscription_tenant` = 1 subscription por tenant (não dá 2 planos no mesmo tenant — usar tenants distintos). (4) Comissão só gera se o tenant tiver `partner_referral` (status TRIAL/FOLLOWUP/CONVIDADO/ATIVADO/CONVERTIDO) **E** o Partner tiver `commission_rate` não-null. (5) `PaymentReceivedHandler` busca `nextDueDate` via `getSubscription` no Asaas — id errado (404) vira `TRANSIENT` porque `AsaasValidationException extends AsaasException` e o handler só faz `catch(AsaasException)`.

### Fase 6 — Payout de comissões (PIX) — ✅ VERIFICADO E2E VIA PORTAIS (2026-06-28)
Pré: rodar liquibase (aplica `partner-schema-010` = `pix_key_type`). Setup da chave PIX do parceiro: **portal do parceiro → Configurações** (`PUT /me/payout-info`), ou SQL direto em `partner.partner` (`pix_key`, `pix_key_type`).
- [x] **Setar chave PIX no portal do parceiro** (`/configuracoes`): salva `pix_key` + `pix_key_type` (CPF/CNPJ/EMAIL/PHONE/EVP) → confere em `partner.partner`.
- [x] **Gate de autorização (money-out):** `POST /admin/trigger-repasse` **sem** `REPASSE_EXECUTE` → **403**. Verificado pela tela admin **Pagamentos** com usuário sem a permissão: `AuthorizationDeniedException` traduzido para 403 amigável pelo `SecurityExceptionHandler` (billing) → toast "Operação não permitida. Fale com um administrador responsável pelos repasses." (antes vazava 500). Via curl direto no billing passar `X-User-Id` + `X-Authorities: REPASSE_EXECUTE`; pelo gateway, o JWT precisa ter `REPASSE_EXECUTE`.
- [x] **RBAC scope (auth):** permissão `REPASSE_EXECUTE` é scope PLATFORM; só atribuível por quem tem `PLATFORM_PERMISSION_ASSIGN` (guard no `RolesService`, coberto por `RolesServiceGuardTest`). Checkbox de scope na tela admin de permissão.
- [x] Parceiro com `pix_key` + comissão PENDENTE → trigger-repasse (com `REPASSE_EXECUTE`) → comissão(ões) → `EM_TRANSFERENCIA` + `asaas_transfer_id`; `POST /v3/transfers` criado no sandbox. Disparado pela tela admin **Pagamentos** (botão "Processar Repasses do Mês").
- [x] Webhook `TRANSFER_COMPLETED` (simulado por `Invoke-RestMethod`) → comissões do transfer → **PAGO** + `paid_at`. (Callback de entrada do Asaas; 100% automático exigiria ngrok + webhook de Transferência cadastrado no sandbox + saldo.)
- [ ] Webhook `TRANSFER_FAILED` → comissões voltam a `PENDENTE` + `asaas_transfer_id=null`; log com `failReason`. (Coberto por `TransferFailedHandlerTest`; e2e não exercitado.)
- [x] **Chave PIX inválida não derruba o lote:** `AsaasValidationException` (400 "chave Pix válida") é capturada **por parceiro** no `processPayouts` (try/catch) → aquele parceiro fica PENDENTE, os outros seguem; **sem 500** (o 500 visto antes era build sem o catch). Chave EVP válida no sandbox: `cliente-a00001@pix.bcb.gov.br`.
- [ ] **Idempotência de payout:** `externalReference=payout-{partnerId}-{period}`; check-then-act no timeout (sem retry cego). (Coberto por design/unit; e2e do replay não exercitado.)
- ⚠️ **Sandbox:** transfer PIX exige **saldo** na conta Asaas — pode não completar de verdade. Validado `EM_TRANSFERENCIA` + criação do transfer; `TRANSFER_COMPLETED` simulado por curl → PAGO.

> **Telas admin criadas p/ o e2e (2026-06-28):** menu **Subscrições → Assinaturas** (`/admin/cadastros/subscription`, lista `billing.subscription`) e **Pagamentos** (`/admin/cadastros/invoices`, lista `billing.commission` + botão Processar Repasses). Backend: `GET /api/v1/subscriptions` e `GET /api/v1/commissions` (paginados, `SubscriptionAdminDTO`/`CommissionAdminDTO`). Telas no padrão `p-table` do `plans.html` com control flow `@for`/`@if`.

> **Checkout via portal do tenant (2026-06-28):** o passo "tenant paga" do e2e passou a ser feito pela UI — `erp-front-end-web` `/web/assinar` (lista planos → `POST /billing/api/v1/checkout` → PIX/boleto), banner de TRIAL no home e item de menu "Assinar Plano". Suporte: `GET /auth/tenant/me` (`TenantProfileDTO`) p/ prefill/banner. Fixes do dia: `ativarConta` passou a preencher `trial_started_at`/`trial_expires_at` (antes nulos); banner usava signals p/ evitar `NG0100`.

### Fase 7 — Dunning, recuperação e reconciliação
> **Status (2026-06-28):** **Fase 7 IMPLEMENTADA** — DunningJob, WebhookRecoveryJob e ReconciliationJob + propagação de bloqueio. Unit verde: `DunningServiceTest` (lembrete/suspensão/cancelamento), `WebhookRecoveryJobTest` (reprocessa preso / skip sem lock), `ReconciliationJobTest` (pago→ativa via handler / não-pago→ignora).
>
> Implementado:
> - `DunningService`/`DunningJob` (cron `billing.cron.dunning`, lock Redis); `SubscriptionRepository` queries de dunning; `CommissionRepository.findByTenantIdAndStatus`.
> - `KafkaBillingProducerService.sendSubscriptionSuspended/Cancelled` (tópicos `billing.subscription.suspended`/`.cancelled`); auth `SubscriptionLifecycleConsumer` + `TenantService.applyBillingStatus` (fecha o gap da Fase 5). Reativação (zera suspend_at/cancel_at) já estava no `PaymentReceivedHandler`.
> - `WebhookRecoveryJob` (cron `billing.cron.webhook-recovery`, 15min): reprocessa `webhook_log` RECEBIDO >10min via `WebhookProcessor` (idempotência Redis protege). `WebhookLogRepository.findByStatusAndReceivedAtBefore`.
> - `ReconciliationJob` (cron `billing.cron.reconciliation`, 30min): varre `AGUARDANDO_PAGAMENTO`, consulta `AsaasGateway.getFirstPayment`; se RECEIVED/CONFIRMED reusa `PaymentReceivedHandler` (idempotente — sai de AGUARDANDO ao ativar). `SubscriptionRepository.findByStatus`.
> - **Pendente:** e2e manual dos 3 jobs (ajustar timestamps/forçar pagamento) e o `DistributedLockService`/job IT (A.1).

- [x] **DunningJob** (rodar manual ajustando timestamps no banco) — lógica coberta por unit test; e2e manual pendente:
  - `suspend_at <= now` → tenant `SUSPENSO` + cache write-through.
  - `cancel_at <= now` → tenant `CANCELADO` + comissões PENDENTE canceladas.
  - lembrete: `suspend_at-2d <= now` e `reminder_sent_at` null → e-mail enviado + `reminder_sent_at` setado (não reenvia).
  - pagamento antes de suspender (`PAYMENT_RECEIVED`) → zera `suspend_at`/`cancel_at` (reativa).
- [ ] **WebhookRecoveryJob:** inserir um `webhook_log` em `RECEBIDO` com `received_at` > 10min atrás → job reprocessa (idempotência Redis evita dupla execução).
- [ ] **ReconciliationJob:** cobrança paga no Asaas sandbox ("Confirmar recebimento em dinheiro") **sem** webhook entregue (curl não disparado) → job cruza e ativa o tenant; rodar 2× → 2ª ignora (idempotente).
- [ ] E-mails de dunning (overdue/D-2/suspensão) saem pelo serviço de e-mail existente.
- [ ] **Propagação de bloqueio (gap da Fase 5):** ao suspender/cancelar no DunningJob, billing publica
      `billing.subscription.suspended`/`.cancelled` → auth consome e seta `Tenant.status = SUSPENSO/CANCELADO`
      → `POST /auth/tenant/login` do tenant suspenso retorna **401/`TENANT_NOT_ACTIVE`** (hoje passaria).

### Sanidade transversal (todas as fases)
- [ ] Webhook sempre responde **200** após token válido (mesmo com falha no processamento async) — fila do Asaas não pausa (§28.7).
- [ ] Token de webhook inválido → **401** (já verificado na Fase 3, passo J).
- [ ] `mvnw verify -pl billing-service` passa com JaCoCo ≥40%.