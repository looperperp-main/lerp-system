# Billing Service — Motor de Pagamentos Asaas
## Especificação para Implementação

**Status:** Pronto para implementação  
**Versão:** 1.0.0  
**Serviço:** `billing-service` (porta 8088 — ver mapeamento 1.1)  
**Base package:** `com.l.erp.billingservice`

---

## 1. Contexto e Objetivos

Este documento especifica o motor de pagamentos que cobre dois fluxos financeiros:

1. **Tenant → Syax**: assinatura recorrente via Asaas (boleto + PIX automático)
2. **Syax → Parceiro**: repasse de comissões via PIX ou TED

> **Documento complementar:** `Loop_ERP_-_Doc_Inicial_-_Riscos_Juridicos.docx` (v1, Jan/2026) cobre LGPD, NF-e/NFS-e, SLA, DPA e riscos comerciais/operacionais. As decisões jurídicas registradas lá impactam diretamente as seções de segurança, retenção de dados e compliance deste spec.

> **Valor dos planos é gerenciado via CRUD admin** — a entidade `billing.plan` armazena nome, descrição e `monthly_value`. O `SubscriptionService` lê o valor atual do plano no momento da conversão. Não há preço hardcoded no código.

> **Já implementados no codebase (externos ao billing service):** ativação de token de convite (`GET /activate?token=xxx`), CRUD de parceiro, CRUD de planos (`billing.plan`), portal do parceiro (leitura de clientes e comissões), rastreamento de engajamento de trial (`billing.trial_engagement`). O billing service consume/integra com esses recursos mas não os implementa.

> **Serviço de e-mail** — notificações (D+10, D+15, overdue, etc.) usam o serviço de e-mail já existente no codebase. `NotificationService` é injetado como dependência, não implementado neste serviço. Não há novo provider a configurar.

> **Sem valor mínimo de repasse** — todo valor acumulado é repassado ao parceiro no D+1, incluindo centavos. A única verificação é `total > 0` (zero não aciona transferência).

> **Follow-up manual pelo contador** — o endpoint `POST /api/billing/v1/referrals/{tenantId}/followup` (ver seção 4.7) registra cada tentativa e marca o tenant como `PERDIDO` ao atingir 3.

Requisitos não-funcionais obrigatórios:
- **Idempotência**: nenhum webhook processado duas vezes — Redis Lua scripts como barreira primária, constraint UNIQUE no banco como barreira secundária
- **Idempotência de payout**: nenhuma comissão paga duas vezes — coluna `payout_asaas_id UNIQUE`
- **Resiliência**: Asaas retenta webhooks 5× a cada 10 min; nosso sistema deve ser tolerante a duplicatas
- **Distributed lock**: cron jobs não executam em paralelo em múltiplas instâncias
- **Auditoria**: toda interação com Asaas é logada em `billing.webhook_log`

---

## 1.1 Mapeamento para o repositório ERP-VSD

| Spec (genérico) | Repositório real |
|---|---|
| `syax-billing`, porta 8082 | módulo `billing-service`, porta **8088** |
| package `com.syax.billing` | `com.l.erp.billingservice` |
| Pacotes por feature (`webhook/`, `commission/`...) | Convenção do projeto: `api/controllers`, `api/dto`, `api/mappers`, `domain`, `repository`, `services`, `infra/config`, `util` |
| Migrations `db/changelog/v1/` no próprio serviço | Migrations centralizadas no módulo `liquibase-service`, em `db/changelog/billing/`, incluídas no `db.changelog-master.yaml` |
| Redis disponível | **Redis ainda não existe** no `compose.yaml` nem no `pom.xml` do billing-service — adicionar serviço Redis ao compose e `spring-boot-starter-data-redis` + `commons-pool2` ao pom antes da Fase 1 |

> (1) Todos os nomes de classe e a lógica da spec valem — apenas o package e a localização seguem a convenção do repo.  
> (2) O gateway já roteia para o billing-service na porta 8088.  
> (3) A entidade JPA continua com `@Table(schema = "billing")`.

---

## ✅ Decisão Fechada — Estorno de Comissão em Churn de Plano Anual

> **Este cenário só se aplica quando o modelo ANUAL de comissão for implementado (roadmap).**  
> **Com o modelo RECORRENTE atual não existe problema: o parceiro só recebe pelos meses que o tenant permaneceu ativo.**

**Decisão dos sócios:** Opção C — Sem automação, regra contratual.

**Descrição:** Nenhum código de estorno é implementado no sistema. Se um tenant com plano anual cancelar antes do fim do contrato, a questão da devolução de comissão ao parceiro é tratada exclusivamente via contrato entre a Syax e o parceiro. Não há lógica de clawback automático.

**Comportamento atual do `SubscriptionInactivatedHandler` (já correto):**

O handler existente já implementa o comportamento da Opção C por omissão:

```java
// SubscriptionInactivatedHandler.java — comportamento ATUAL, sem alteração necessária
// Ao receber SUBSCRIPTION_INACTIVATED:
// 1. Seta subscription.status = CANCELADO
// 2. Cancela comissões com status PENDENTE (ainda não pagas)
// 3. NÃO reverte comissões com status PAGO — correto pela Opção C
// 4. NÃO gera commission com valor negativo
```

**Contexto do exemplo para referência contratual:**

| Exemplo | Valor |
|---|---|
| Plano anual | R$ 9.900 |
| Comissão ANUAL (10%) paga na conversão | R$ 990,00 |
| Tenant cancela no mês 3 (de 12) | — |
| Comissão "proporcional ao tempo" (referência) | R$ 247,50 (3/12 × R$ 990) |
| **Tratamento** | **Via contrato com o parceiro** |

**Impacto no código:** Zero. O `SubscriptionInactivatedHandler` não precisa de alteração. Não há novas migrations. Não há novos campos.

**Nota para o contrato com parceiros:** O contrato de parceria deve prever explicitamente que a comissão do modelo ANUAL é pelo ato de conversão, não pela retenção. Cabe ao parceiro assumir o risco de churn no plano anual. A Syax pode negociar estorno manual caso a caso conforme relacionamento com o parceiro.

---

## 2. Architecture Decision Records (ADRs)

### ADR-001: Processamento assíncrono de webhooks
**Decisão:** O endpoint `POST /webhook/asaas` retorna `200 OK` imediatamente e envia o payload para um thread pool interno.  
**Motivo:** Asaas considera timeout (>30s) como falha e retenta. O processamento (incluindo geração de comissão) pode exceder esse limite.  
**Consequência:** Adicionar `@EnableAsync` e configurar `TaskExecutor` dedicado (`webhook-async-pool`).

### ADR-002: Redis Lua scripts para idempotência
**Decisão:** Usar scripts Lua executados atomicamente no Redis como primeira barreira de idempotência.  
**Motivo:** `GET + SET` em dois comandos tem race condition; Lua é atômico por design no Redis.  
**Consequência:** 5 scripts Lua na pasta `src/main/resources/lua/`. Ver seção 6.

### ADR-003: Distributed lock via Redis para cron jobs
**Decisão:** Cada cron job tenta adquirir um lock Redis antes de executar. Se falhar (outra instância está rodando), loga e encerra silenciosamente.  
**Motivo:** Deploy com múltiplas instâncias (k8s, ECS) causaria duplo processamento.  
**Consequência:** `DistributedLockService` com acquire/release via scripts Lua 3 e 4.

### ADR-004: Strategy pattern para comissões
**Decisão:** `CommissionStrategy` é uma interface com implementação `RecurrentCommissionStrategy`. A interface é mantida para suportar novos modelos no futuro sem alterar o engine.  
**Motivo:** Hoje só existe o modelo RECORRENTE (10% da mensalidade, todo mês). O padrão Strategy permite adicionar um modelo ANUAL ou por volume futuramente sem modificar `CommissionEngine`.  
**Consequência:** `RecurrentCommissionStrategy` é um `@Component` Spring. `CommissionStrategyFactory` não é necessária agora — o engine chama a strategy diretamente. Manter a abstração para evolução futura.

### ADR-005: PIX como método primário de repasse
**Decisão:** Repasses ao parceiro usam `POST /v3/transfers` do Asaas com a chave PIX do parceiro.  
**Motivo:** PIX é instantâneo, sem custo, disponível 24/7.  
**Consequência:** Preferência é PIX (`pix_key` + `pix_key_type`). Se o parceiro não tiver chave PIX, usar TED com dados bancários (`bank_code`, `bank_agency`, `bank_account`, `account_digit`, `account_type`). Pelo menos um dos dois deve estar preenchido antes do primeiro repasse.

### ADR-006: Comissão gerada de forma assíncrona após ativação
**Decisão:** `PaymentReceivedHandler` primeiro ativa o tenant (transação A), depois dispara geração de comissão de forma assíncrona (transação B separada).  
**Motivo:** Falha na comissão não deve reverter ativação do tenant — são preocupações separadas.  
**Consequência:** `CommissionEngine` tem seu próprio `@Transactional`. Se falhar, está logado; pode ser reprocessado manualmente via `billing.webhook_log`.

### ADR-007: Dunning em 2 etapas com timestamps absolutos
**Decisão:** `PAYMENT_OVERDUE` não suspende imediatamente — grava `suspend_at = now() + 5d` e `cancel_at = now() + 7d`. O `DunningJob` (roda 4× ao dia: 00h/06h/12h/18h UTC) executa lembretes, suspensões e cancelamentos comparando esses timestamps com `now()`.  
**Motivo:** Boleto pode ser pago um dia antes do vencimento mas processado no dia seguinte. Suspensão imediata geraria falsos positivos. Timestamps absolutos eliminam dependência do Asaas para controlar transições de estado — o relógio é da Syax.  
**Consequência:** `GracePeriodJob` NÃO deve ser implementado. Usar exclusivamente `DunningJob` (seção 27.7.4). O `DunningJob` usa write-through no cache Redis (put, não evict) ao suspender e cancelar.

---

## 3. Arquitetura Interna do Billing Service

O Billing Service (`syax-billing`, porta 8082) é organizado em **seis camadas internas** com responsabilidades bem separadas.

### 3.1 Camada de entrada — Webhook e API REST

Todo tráfego externo entra por três pontos distintos:

- **`POST /webhook/asaas`** — recebe eventos do Asaas. Valida token via `WebhookSecurityService`, persiste recebimento no `webhook_log` com status `RECEBIDO` e retorna `200 OK` imediatamente. **Nunca processa de forma síncrona** — Asaas tem timeout de 30s.
- **`POST /api/billing/v1/subscriptions`** — atende o Angular Frontend (JWT do tenant). Cria assinatura no Asaas e devolve boleto/QR Code.
- **`GET /internal/billing/status/{tenantId}`** — atende exclusivamente o Auth Service via token interno. Retorna status atual do tenant (com cache Redis de 5 min).

### 3.2 Camada de processamento assíncrono — Webhook Processor

Após o controller retornar `200 OK`, o `WebhookProcessor` assume em thread pool dedicado (`webhook-async-pool`, 5–20 threads). Pipeline fixo em três etapas:

1. **Idempotência** — Redis Lua `tryAcquire(event:paymentId)`. Se retorna `0` (já processado), descarta e loga como `IGNORADO`.
2. **Roteamento** — `WebhookHandlerFactory` entrega o payload ao handler correto pelo tipo de evento.
3. **Finalização** — Redis Lua `markDone/markError` + atualiza `webhook_log.status`.

Os handlers implementam `WebhookEventHandler`:

| Handler | Evento | Ação principal |
|---|---|---|
| `PaymentReceivedHandler` | `PAYMENT_RECEIVED` · `PAYMENT_CONFIRMED` | Ativa tenant + write-through no cache Redis + dispara comissão (async, tx separada) |
| `PaymentOverdueHandler` | `PAYMENT_OVERDUE` | Grava `suspend_at = now() + 5d` e `cancel_at = now() + 7d` (27.7) + notifica |
| `SubscriptionInactivatedHandler` | `SUBSCRIPTION_INACTIVATED` | Apenas metadado `asaas_inactivated_at` — DunningJob controla transições (27.7.5) |
| `PaymentDeletedHandler` | `PAYMENT_DELETED` | Cancela comissão vinculada ao pagamento deletado |
| `TransferCompletedHandler` | `TRANSFER_COMPLETED` | Comissão `EM_TRANSFERENCIA → PAGO` + `confirmed_at` |
| `TransferFailedHandler` | `TRANSFER_FAILED` | Reverte comissão para `PENDENTE` + alerta admin |

### 3.3 Camada de integração — Asaas Client

Interface Feign com quatro especializações: `AsaasCustomerClient`, `AsaasSubscriptionClient`, `AsaasPaymentClient`, `AsaasTransferClient`. Toda chamada passa por:

```
Circuit Breaker (abre com 50% de falha em 10 chamadas, espera 30s)
  └─ Retry (3 tentativas, backoff 1s → 2s → 4s)
       └─ Feign HTTP (timeout 15s, header access_token)
```

`AsaasValidationException` (CNPJ inválido, cliente já existe) **não** é retentada.

### 3.4 Camada de negócio — Commission Engine

Recebe o evento de pagamento confirmado e decide o que fazer. Usa Strategy Pattern:

- **`RecurrentCommissionStrategy`** — único modelo ativo. `CommissionStrategyFactory` mantida para extensão futura (modelo ANUAL está no roadmap mas não implementado).
- **`RecurrentCommissionStrategy`** — idempotência via `UNIQUE (asaas_payment_id)` no banco. Calcula `monthly_value × commission_rate` (10% padrão). Para plano anual, prorateiam a mensalidade: `subscription.value ÷ 12 × commission_rate`.

Falha na geração de comissão **não** reverte a ativação do tenant — são transações independentes.

### 3.5 Camada de infraestrutura interna — Redis Scripts

Cinco scripts Lua em `src/main/resources/lua/`, carregados no boot pelo `RedisConfig`:

| Script | Função | Chave Redis |
|---|---|---|
| `webhookIdempotencyAcquire.lua` | Marca webhook como "em processamento" (atômico) | `syax:billing:webhook:{event}:{paymentId}` |
| `webhookComplete.lua` | Atualiza status final `DONE`/`ERROR` | mesma chave |
| `acquireDistributedLock.lua` | Adquire lock para cron job | `syax:billing:lock:{job}:{period}` |
| `releaseDistributedLock.lua` | Libera lock (só se for o dono) | mesma chave |
| `annualCommissionGuard.lua` | Garante comissão anual única por (parceiro, tenant, ano) | `syax:billing:commission:annual:{p}:{t}:{year}` |

### 3.6 Camada de cron — Jobs agendados

Todos adquirem distributed lock antes de executar (sem lock, encerram silenciosamente):

| Job | Horário | Ação |
|---|---|---|
| `DunningJob` | 00/06/12/18h UTC | Lembretes, suspensões (`suspend_at`) e cancelamentos (`cancel_at`) — ver 27.7.4 |
| `CommissionPayoutJob` | 02:00 D+1/mês | Agrega comissões PENDENTE por parceiro (`effectiveAmount`), notifica admin e envia via Asaas Transfers (PIX/TED) |
| `ReconciliationJob` | 02:30 diário | Cruza pagamentos RECEIVED do Asaas com o banco (seção 19) |
| `WebhookRecoveryJob` | a cada 10 min | Reprocessa webhooks presos em `RECEBIDO` (28.5) |

> Os crons de trial D+10/D+15 rodam no auth service e no partner service — ver seção 12. O billing service não os implementa.

### 3.7 Fluxo completo de um pagamento em produção

```
1.  Tenant paga boleto/PIX → Asaas confirma
2.  Asaas → POST /webhook/asaas  (header: asaas-access-token)
3.  WebhookController valida token → 200 OK  (< 100ms)
4.  WebhookProcessor (thread pool async):
    a. Redis Lua tryAcquire("PAYMENT_RECEIVED:pay_xxxx") → 1 (primeira vez)
    b. PaymentReceivedHandler:
       - subscription.status = ATIVO
       - subscription.next_due_date = +30d ou +365d
       - Redis evict: cache status tenant
    c. CommissionEngine (async, transação separada):
       - Identifica parceiro via partner_referral
       - Seleciona strategy por commission_model
       - Insere billing.commission (status PENDENTE)
    d. Redis Lua markDone → webhook_log.status = PROCESSADO
5.  Auth Service → GET /internal/billing/status/42
    → Redis cache hit (TTL 5min) → "ATIVO" → JWT emitido
6.  D+1 do mês: CommissionPayoutJob
    → Asaas POST /v3/transfers (PIX ou TED ao parceiro)
    → commission.status = EM_TRANSFERENCIA  +  payout_asaas_id gravado
7.  Webhook TRANSFER_COMPLETED
    → commission.status = PAGO  +  confirmed_at
```

**Princípio central:** nenhuma dessas etapas compartilha transação entre si. Ativação, comissão e payout são transações independentes — falha em qualquer uma não afeta as outras.

---

## 4. Integração com Asaas — Visão Geral

A integração tem **duas direções** com contratos e mecanismos de segurança completamente distintos.

### 4.1 Billing Service → Asaas (chamadas de saída)

**Na conversão (Trial → Ativo):**

Passo 1 — criar cliente Asaas (`asaasCustomerId` nulo até este momento):
```
POST https://api.asaas.com/v3/customers
access_token: {ASAAS_API_KEY}

{
  "name":         "{tenant.name}",
  "cpfCnpj":      "{tenant.cnpj}",
  "email":        "{tenant.email}",
  "phone":        "{tenant.telefone}",
  "address":      "{tenant.logradouro}",
  "addressNumber":"{tenant.numero}",
  "province":     "{tenant.bairro}",
  "city":         "{tenant.cidade}",
  "state":        "{tenant.uf}",
  "postalCode":   "{tenant.cep}"
}

Response: { "id": "cus_xxxxxxxx" }
```

Passo 2 — criar assinatura recorrente:
```
POST https://api.asaas.com/v3/subscriptions
access_token: {ASAAS_API_KEY}

{
  "customer":         "cus_xxxxxxxx",
  "billingType":      "BOLETO",
  "value":            990.00,           // MONTHLY: planValue | ANNUAL: planValue × 10
  "nextDueDate":      "2025-02-01",
  "cycle":            "MONTHLY",        // ou "YEARLY" para plano anual
  "description":      "Plano Mensal — Syax",
  "externalReference":"42"              // tenantId para rastreabilidade
}

Response: { "id": "sub_xxxxxxxx", "status": "ACTIVE" }
```

O Asaas gera automaticamente o primeiro pagamento com **boleto + QR Code PIX** quando `billingType = BOLETO`. O Billing Service consulta `GET /v3/subscriptions/{id}/payments` para retornar a URL e QR Code ao frontend.

**No payout de comissões (D+1 do mês):**
```
POST https://api.asaas.com/v3/transfers
access_token: {ASAAS_API_KEY}

{
  "value":            450.00,
  "pixAddressKey":    "contador@escritorio.com.br",
  "pixAddressKeyType":"EMAIL",
  "description":      "Comissão Syax — Referência 2025-01 — Parceiro 42",
  "externalReference":"payout-42-2025-01"
}

Response: { "id": "tra_xxxxxxxx", "status": "PENDING" }
```

O `id` retornado é gravado em `billing.commission.payout_asaas_id` (coluna `UNIQUE`) — serve como idempotência e trilha de auditoria. `pixAddressKeyType` aceita: `CPF`, `CNPJ`, `EMAIL`, `PHONE`, `EVP`. O campo `externalReference` segue o padrão `payout-{partnerId}-{period}` — permite o check-then-act após timeout (seção 28.4).

### 4.2 Asaas → Billing Service (webhooks de entrada)

O Asaas chama `POST /webhook/asaas` para os eventos tratados:

| Evento | Quando ocorre | Ação |
|---|---|---|
| `PAYMENT_RECEIVED` | Tenant pagou | Ativa tenant + gera comissão |
| `PAYMENT_CONFIRMED` | Cartão de crédito confirmado (liquidação futura) | Mesmo tratamento de `PAYMENT_RECEIVED` |
| `PAYMENT_OVERDUE` | Boleto venceu | Inicia grace period de 5 dias |
| `SUBSCRIPTION_INACTIVATED` | Assinatura cancelada | Cancela tenant |
| `PAYMENT_DELETED` | Cobrança deletada | Cancela comissão associada |
| `TRANSFER_COMPLETED` | Transfer PIX/TED concluído | Marca comissão `PAGO` + `confirmed_at` |
| `TRANSFER_FAILED` | Transfer PIX/TED falhou | Reverte comissão + notifica admin |

Estrutura do payload:
```json
{
  "event": "PAYMENT_RECEIVED",
  "payment": {
    "id":           "pay_xxxxxxxx",
    "customer":     "cus_xxxxxxxx",
    "subscription": "sub_xxxxxxxx",
    "status":       "RECEIVED",
    "value":        990.00,
    "billingType":  "PIX",
    "dueDate":      "2025-02-01",
    "paymentDate":  "2025-02-01"
  }
}
```

### 4.3 Autenticação em cada direção

**Billing → Asaas:** header `access_token: {ASAAS_API_KEY}` em toda requisição. Variável de ambiente `ASAAS_API_KEY`. Em desenvolvimento: `sandbox.asaas.com/api/v3`.

**Asaas → Billing (webhook):** Asaas envia `asaas-access-token` no header. O `WebhookSecurityService` compara com `ASAAS_WEBHOOK_TOKEN` usando `MessageDigest.isEqual()` (comparação em tempo constante, evita timing attack). Token configurado em: Asaas → Integrações → Webhooks.

### 4.4 Resiliência nas chamadas de saída

```
Circuit Breaker ──── abre com 50% de falha em 10 chamadas
                     espera 30s antes de tentar novamente
  └─ Retry ────────── 3 tentativas
                     backoff: 1s → 2s → 4s
                     retenta: ConnectException, timeout
                     ignora: AsaasValidationException (400 do Asaas)
```

### 4.5 Resiliência nos webhooks de entrada

O Asaas retenta automaticamente 5× com intervalos de 10 minutos em caso de falha. O pipeline de idempotência protege contra duplicatas:

```
Webhook chega
  → token válido?  (WebhookSecurityService)
  → Redis Lua tryAcquire(event:paymentId)
       retorna 0 → descarta + loga IGNORADO
       retorna 1 → processa → markDone/markError
```

Chave Redis com TTL de 24h cobre qualquer janela de retry do Asaas. O `UNIQUE (asaas_payment_id)` no banco é a segunda barreira caso o Redis seja flushed.

### 4.6 O que nunca vai para o Asaas

- **Parceiros não têm conta Asaas** — repasse vai direto por PIX key armazenada em `billing.partner.pix_key`.
- **`asaasCustomerId` fica nulo durante o trial** — cliente só é criado no Asaas no momento da conversão.
- **O schema principal não tem FK para billing** — `tenant_id` nas tabelas billing é referência por convenção, não por constraint de banco; os serviços evoluem de forma independente.


### 4.7 Endpoint de Follow-up Manual

O contador registra cada tentativa de conversão através do painel parceiro, que chama:

```
POST /api/billing/v1/referrals/{tenantId}/followup
Authorization: Bearer {partner-jwt}

Response 200:
{
  "tenantId": 42,
  "followup_attempts": 2,
  "referral_status": "ATIVADO",
  "lost": false
}

Response 200 (terceira tentativa — marca como PERDIDO):
{
  "tenantId": 42,
  "followup_attempts": 3,
  "referral_status": "PERDIDO",
  "lost": true,
  "lost_at": "2025-02-21T10:30:00Z"
}
```

Lógica do `PartnerReferralService.recordFollowup(tenantId)`:

```java
@Transactional
public FollowupResponse recordFollowup(Long tenantId, Long partnerId) {
    PartnerReferral referral = referralRepo
        .findActiveByTenantIdAndPartnerId(tenantId, partnerId)
        .orElseThrow(() -> new NotFoundException("Referral não encontrado"));

    referral.setFollowupAttempts(referral.getFollowupAttempts() + 1);

    boolean lost = referral.getFollowupAttempts() >= 3;
    if (lost) {
        referral.setStatus(ReferralStatus.PERDIDO);
        referral.setLostAt(Instant.now());
    }

    referralRepo.save(referral);
    return FollowupResponse.from(referral, lost);
}
```


> **Sem automação de D+15+48h.** O sistema não cria follow-ups automaticamente — é o contador quem aciona. O cron `TrialExpiryJob` (D+15) só dispara o alerta; o registro da tentativa é sempre explícito via este endpoint.


---

## 5. Segurança do Webhook

> ⚠ **Comportamento crítico da fila Asaas (ver 28.7):** o Asaas usa fila sequencial — respostas
> não-2xx repetidas PAUSAM a fila inteira (nenhum webhook de nenhum evento chega até reativação
> manual no painel). Por isso o controller SEMPRE retorna 200 após validar o token, mesmo que
> o processamento async venha a falhar. 401 apenas para token inválido. Monitoramento: alertar
> se nenhum webhook chegar em 6h úteis.

### 5.1 Validação do token Asaas

Asaas envia o header `asaas-access-token` em todo webhook. O valor é o token configurado no painel Asaas → Integrações → Webhooks.

```java
// WebhookSecurityService.java
@Service
public class WebhookSecurityService {

    @Value("${billing.asaas.webhook-token}")
    private String expectedToken;

    public void validateToken(String receivedToken) {
        if (!MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                receivedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new WebhookAuthException("Token inválido");
        }
    }
}
```

> ⚠️ Usar `MessageDigest.isEqual()` (constant-time comparison) para evitar timing attack.

### 5.2 IP Allowlist (opcional, recomendado em produção)

Adicionar filtro Servlet que verifica `X-Forwarded-For` ou `REMOTE_ADDR`:

```yaml
billing:
  asaas:
    webhook-ip-allowlist:
      - "54.94.97.128/25"
      - "18.228.149.0/25"
      - "18.228.249.0/25"
      - "52.67.10.0/25"
```

Implementar `WebhookIpFilter implements Filter` — verificar se o IP do request está em algum CIDR. Usar biblioteca `commons-net` (`SubnetUtils`).

### 5.3 Rate limiting

Adicionar `@RateLimiter(name = "asaas-webhook")` (Resilience4j) no método do controller — máximo 100 req/s (Asaas garante máximo de 5 webhooks simultâneos em condições normais).

### 5.4 Configuração Spring Security

```java
// WebhookSecurityConfig.java
@Configuration
public class WebhookSecurityConfig {
    @Bean
    public SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/webhook/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/webhook/asaas").permitAll()
                .anyRequest().denyAll()
            );
        return http.build();
    }
}
```

---

## 6. Redis — Chaves, TTLs e Lua Scripts

### 6.1 Convenção de chaves

| Chave | Formato | TTL | Uso |
|---|---|---|---|
| Idempotência webhook | `syax:billing:webhook:{eventType}:{asaasPaymentId}` | 86400s (24h) | Barreira primária anti-duplicata |
| Lock cron | `syax:billing:lock:{jobName}:{period}` | TTL do job | Distributed lock |
| Cache status tenant | `syax:billing:tenant:status:{tenantId}` | 300s (5min) | Cache para Auth Service |
| Guard comissão anual | `syax:billing:commission:annual:{partnerId}:{tenantId}:{year}` | 31536000s (1 ano) | Evita dupla comissão anual |

### 6.2 Configuração Redis

```java
// RedisConfig.java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(factory);
        tpl.setKeySerializer(new StringRedisSerializer());
        tpl.setValueSerializer(new StringRedisSerializer());
        return tpl;
    }

    @Bean("webhookIdempotencyScript")
    public RedisScript<Long> webhookIdempotencyScript() {
        return RedisScript.of(
            loadScript("lua/webhookIdempotencyAcquire.lua"), Long.class);
    }

    @Bean("webhookCompleteScript")
    public RedisScript<Long> webhookCompleteScript() {
        return RedisScript.of(
            loadScript("lua/webhookComplete.lua"), Long.class);
    }

    @Bean("acquireLockScript")
    public RedisScript<Long> acquireLockScript() {
        return RedisScript.of(
            loadScript("lua/acquireDistributedLock.lua"), Long.class);
    }

    @Bean("releaseLockScript")
    public RedisScript<Long> releaseLockScript() {
        return RedisScript.of(
            loadScript("lua/releaseDistributedLock.lua"), Long.class);
    }

    @Bean("annualGuardScript")
    public RedisScript<Long> annualGuardScript() {
        return RedisScript.of(
            loadScript("lua/annualCommissionGuard.lua"), Long.class);
    }

    private String loadScript(String path) {
        try {
            return StreamUtils.copyToString(
                new ClassPathResource(path).getInputStream(),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar script Lua: " + path, e);
        }
    }
}
```

### 6.3 Script 1 — webhookIdempotencyAcquire.lua

```lua
-- Tenta marcar webhook como "em processamento" de forma atômica.
-- Retorna 1 se pode processar (primeiro acesso), 0 se já existe (duplicata).
-- KEYS[1] = chave de idempotência
-- ARGV[1] = TTL em segundos
local key = KEYS[1]
local ttl = tonumber(ARGV[1])
local result = redis.call('SET', key, 'PROCESSING', 'NX', 'EX', ttl)
if result then
    return 1
else
    return 0
end
```

### 6.4 Script 2 — webhookComplete.lua

```lua
-- Atualiza status final do webhook (DONE ou ERROR).
-- Retorna 1 se atualizou, 0 se chave expirou (não bloqueia reprocessamento).
-- KEYS[1] = chave de idempotência
-- ARGV[1] = status final: 'DONE' | 'ERROR'
-- ARGV[2] = TTL em segundos (reinicia contagem)
local key = KEYS[1]
local status = ARGV[1]
local ttl = tonumber(ARGV[2])
if redis.call('EXISTS', key) == 1 then
    redis.call('SETEX', key, ttl, status)
    return 1
end
return 0
```

### 6.5 Script 3 — acquireDistributedLock.lua

```lua
-- Adquire lock distribuído para cron jobs.
-- Retorna 1 se lock adquirido, 0 se já está travado por outra instância.
-- KEYS[1] = chave do lock (ex: "syax:billing:lock:commission-payout:2025-01")
-- ARGV[1] = identificador único desta instância (UUID + thread)
-- ARGV[2] = TTL em segundos
local result = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', tonumber(ARGV[2]))
return result ~= false and 1 or 0
```

### 6.6 Script 4 — releaseDistributedLock.lua

```lua
-- Libera lock apenas se a instância atual é a dona.
-- Retorna 1 se liberou, 0 se não é dona ou expirou.
-- KEYS[1] = chave do lock
-- ARGV[1] = identificador único desta instância (deve coincidir)
local key = KEYS[1]
local owner = ARGV[1]
local current = redis.call('GET', key)
if current == owner then
    redis.call('DEL', key)
    return 1
else
    return 0
end
```

### 6.7 Script 5 — annualCommissionGuard.lua

```lua
-- Garante que comissão anual seja gerada no máximo uma vez por ano por (parceiro, tenant).
-- Retorna 1 se é a primeira vez este ano, 0 se já foi gerada.
-- KEYS[1] = "syax:billing:commission:annual:{partnerId}:{tenantId}:{year}"
-- ARGV[1] = TTL em segundos (ex: 31536000 = 1 ano)
local result = redis.call('SET', KEYS[1], '1', 'NX', 'EX', tonumber(ARGV[1]))
return result ~= false and 1 or 0
```

### 6.8 WebhookIdempotencyService.java

```java
@Service
public class WebhookIdempotencyService {

    private final RedisTemplate<String, String> redis;
    private final RedisScript<Long> acquireScript;
    private final RedisScript<Long> completeScript;

    @Value("${billing.redis.webhook-idempotency-ttl-seconds:86400}")
    private long ttl;

    public boolean tryAcquire(String eventType, String asaasPaymentId) {
        String key = buildKey(eventType, asaasPaymentId);
        Long result = redis.execute(acquireScript,
            Collections.singletonList(key),
            String.valueOf(ttl));
        return Long.valueOf(1L).equals(result);
    }

    public void markDone(String eventType, String asaasPaymentId) {
        markFinal(eventType, asaasPaymentId, "DONE");
    }

    public void markError(String eventType, String asaasPaymentId) {
        markFinal(eventType, asaasPaymentId, "ERROR");
    }

    /** Erro transitório: apaga a chave para a retentativa do Asaas reprocessar. */
    public void release(String eventType, String asaasPaymentId) {
        redis.delete(buildKey(eventType, asaasPaymentId));
    }

    private void markFinal(String eventType, String asaasPaymentId, String status) {
        String key = buildKey(eventType, asaasPaymentId);
        redis.execute(completeScript,
            Collections.singletonList(key),
            status, String.valueOf(ttl));
    }

    private String buildKey(String eventType, String asaasPaymentId) {
        return String.format("syax:billing:webhook:%s:%s", eventType, asaasPaymentId);
    }
}
```

---

## 7. Fluxo A — Criação de Assinatura (Trial → Ativo)

### 7.1 Endpoint

```
POST /api/billing/v1/subscriptions
Authorization: Bearer {jwt}   ← JWT do tenant autenticado
Content-Type: application/json

{
  "planId": 1,
  "planType": "MONTHLY" | "ANNUAL",
  "billingType": "BOLETO" | "PIX" | "CREDIT_CARD"
}
```

### 7.2 Lógica (SubscriptionService.java)

```java
@Service
@Transactional
public class SubscriptionService {

    public SubscriptionCreationResponse createSubscription(Long tenantId, CreateSubscriptionRequest req) {

        // 1. Buscar subscription existente ou criar nova (fluxo B2C não tem registro prévio)
        Subscription sub = subscriptionRepo.findByTenantId(tenantId)
            .orElseGet(() -> {
                // Tenant B2C: subscription criada aqui pela primeira vez
                Subscription nova = Subscription.builder()
                    .tenantId(tenantId)
                    .status(SubscriptionStatus.TRIAL)
                    .build();
                return subscriptionRepo.save(nova);
            });

        if (sub.getStatus() != SubscriptionStatus.TRIAL) {
            throw new BusinessException("Tenant não está em período de trial");
        }

        // 2. Criar ou recuperar customer Asaas
        String asaasCustomerId = getOrCreateAsaasCustomer(tenantId, sub);

        // 3. Carregar plano e calcular valor — necessário para B2C onde sub não tem plano ainda
        Plan plan = planRepo.findById(req.getPlanId())
            .orElseThrow(() -> new NotFoundException("Plano não encontrado: " + req.getPlanId()));
        BigDecimal value = calculateValue(req.getPlanType(), plan.getMonthlyValue());

        // 4. Criar subscription no Asaas
        AsaasSubscriptionResponse asaasSub = asaasSubscriptionClient.create(
            AsaasSubscriptionRequest.builder()
                .customer(asaasCustomerId)
                .billingType(req.getBillingType().name())
                .value(value)
                .nextDueDate(LocalDate.now().plusDays(1))
                .cycle(req.getPlanType() == PlanType.ANNUAL ? "YEARLY" : "MONTHLY")
                .description("Plano " + req.getPlanType().label() + " — Syax")
                .build()
        );

        // 5. Persistir IDs Asaas na subscription (aguarda webhook PAYMENT_RECEIVED para ativar)
        sub.setAsaasCustomerId(asaasCustomerId);
        sub.setAsaasSubscriptionId(asaasSub.getId());
        sub.setPlanId(plan.getId());
        sub.setPlanType(req.getPlanType());
        sub.setValue(value);
        sub.setBillingCycle(req.getPlanType() == PlanType.ANNUAL ? BillingCycle.YEARLY : BillingCycle.MONTHLY);
        sub.setNextDueDate(LocalDate.now().plusDays(1));
        subscriptionRepo.save(sub);

        // 6. Buscar primeiro pagamento para retornar link/QR Code
        AsaasPaymentDetails firstPayment = asaasPaymentClient.getFirstBySubscription(asaasSub.getId());

        return SubscriptionCreationResponse.builder()
            .subscriptionId(sub.getId())
            .asaasSubscriptionId(asaasSub.getId())
            .boletoUrl(firstPayment.getBankSlipUrl())
            .pixQrCode(firstPayment.getPixQrCodeImage())
            .pixCopyPaste(firstPayment.getPixCopyPaste())
            .value(value)
            .dueDate(firstPayment.getDueDate())
            .build();
    }

    private String getOrCreateAsaasCustomer(Long tenantId, Subscription sub) {
        if (sub.getAsaasCustomerId() != null) {
            return sub.getAsaasCustomerId();
        }
        Tenant tenant = tenantRepo.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant " + tenantId));
        AsaasCustomerResponse customer = asaasCustomerClient.create(
            AsaasCustomerRequest.from(tenant));
        return customer.getId();
    }

    /**
     * Calcula o valor da assinatura a partir do valor mensal do plano carregado.
     * Recebe monthlyValue do plano (nunca de sub.getPlanValue() — subscription B2C pode não ter plano ainda).
     */
    private BigDecimal calculateValue(PlanType planType, BigDecimal monthlyValue) {
        if (planType == PlanType.ANNUAL) {
            return monthlyValue.multiply(BigDecimal.valueOf(10)); // 2 meses grátis
        }
        return monthlyValue;
    }
}
```

### 7.3 Asaas API — Criação de Customer

```
POST https://api.asaas.com/v3/customers
access_token: {ASAAS_API_KEY}

{
  "name": "{tenant.name}",
  "cpfCnpj": "{tenant.cnpj}",
  "email": "{tenant.email}",
  "phone": "{tenant.telefone}",
  "address": "{tenant.logradouro}",
  "addressNumber": "{tenant.numero}",
  "province": "{tenant.bairro}",
  "city": "{tenant.cidade}",
  "state": "{tenant.uf}",
  "postalCode": "{tenant.cep}"
}

Response: { "id": "cus_xxxxxxxx", ... }
```

### 7.4 Asaas API — Criação de Subscription

```
POST https://api.asaas.com/v3/subscriptions
access_token: {ASAAS_API_KEY}

{
  "customer": "cus_xxxxxxxx",
  "billingType": "BOLETO",
  "value": 990.00,
  "nextDueDate": "2025-02-01",
  "cycle": "MONTHLY",
  "description": "Plano Mensal — Syax",
  "externalReference": "{tenantId}"
}

Response: { "id": "sub_xxxxxxxx", "status": "ACTIVE", ... }
```

> O Asaas gera automaticamente o primeiro pagamento com **boleto + QR Code PIX** quando `billingType = BOLETO`.

---

## 8. Fluxo B — Processamento de Webhooks

### 8.1 Controller

```java
// WebhookController.java
@RestController
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

    private final WebhookSecurityService securityService;
    private final WebhookLogService logService;
    private final WebhookProcessor processor;

    @PostMapping("/asaas")
    public ResponseEntity<Void> handleAsaas(
            @RequestHeader("asaas-access-token") String token,
            @RequestBody AsaasWebhookPayload payload,
            HttpServletRequest request) {

        // 1. Validar token (lança 401 se inválido)
        securityService.validateToken(token);

        // 2. Logar recebimento (status RECEBIDO) — fire and not forget a persistência
        // ATENÇÃO: logReceived DEVE tolerar duplicata de asaas_event_id (UNIQUE 005-12).
        // Usar INSERT ... ON CONFLICT (asaas_event_id) DO NOTHING, ou capturar
        // DataIntegrityViolationException e seguir silenciosamente.
        // Nunca deixar exceção de persistência vazar daqui — veja callout abaixo.
        logService.logReceived(payload, request.getRemoteAddr());

        // 3. Processar assincronamente
        processor.processAsync(payload);

        // 4. Retornar 200 imediatamente (Asaas aguarda resposta rápida)
        return ResponseEntity.ok().build();
    }
}
```

> **CRÍTICO — tolerância a duplicatas no logReceived:** a migration 005-12 (seção 28.5) cria um índice UNIQUE em `webhook_log.asaas_event_id`. Na retentativa do Asaas, o INSERT viola esse UNIQUE. `logService.logReceived` DEVE tratar esse conflito como duplicata benigna — `INSERT ... ON CONFLICT (asaas_event_id) DO NOTHING` via query nativa, ou capturar `DataIntegrityViolationException` e continuar normalmente. Se a exceção vazar do controller, a resposta deixa de ser 2xx, e a seção 28.7 explica que isso **pausa a fila inteira do Asaas**. Regra: o webhook sempre responde 200 após token válido, independente do que acontecer no log.

### 8.2 Processor assíncrono

```java
// WebhookProcessor.java
@Service
@Slf4j
public class WebhookProcessor {

    private final WebhookIdempotencyService idempotencyService;
    private final WebhookHandlerFactory handlerFactory;
    private final WebhookLogService logService;

    @Async("webhookExecutor")
    public void processAsync(AsaasWebhookPayload payload) {
        String eventType = payload.getEvent();
        // Chave de idempotência: event.id do Asaas (único POR ENTREGA de webhook).
        // Fallback em cascata para paymentId, transferId ou subscriptionId se id ausente.
        // Nota: TRANSFER_COMPLETED/TRANSFER_FAILED têm payload.transfer não nulo;
        // payment e subscription são null nesses eventos.
        String eventId;
        if (payload.getId() != null) {
            eventId = payload.getId();
        } else if (payload.getPayment() != null) {
            eventId = payload.getPayment().getId();
        } else if (payload.getTransfer() != null) {
            eventId = payload.getTransfer().getId();
        } else if (payload.getSubscription() != null) {
            eventId = payload.getSubscription().getId();
        } else {
            eventId = UUID.randomUUID().toString(); // último recurso — nunca deve chegar aqui
            log.warn("Payload sem id identificável — gerado UUID temporário event={}", eventType);
        }

        // 1. Verificar idempotência via Redis Lua
        if (!idempotencyService.tryAcquire(eventType, eventId)) {
            log.info("Webhook duplicado ignorado — event={} id={}", eventType, eventId);
            logService.logIgnored(payload);
            return;
        }

        try {
            // 2. Rotear para handler do evento
            WebhookEventHandler handler = handlerFactory.getHandler(eventType);
            handler.handle(payload);

            // 3. Marcar como processado (Redis + log)
            idempotencyService.markDone(eventType, eventId);
            logService.logProcessed(payload);

        } catch (TransientException e) {
            // Erro TRANSITÓRIO (DB indisponível, timeout, deadlock):
            // LIBERA a chave de idempotência para a retentativa do Asaas reprocessar.
            log.warn("Erro transitório webhook event={} id={} — chave liberada para retry",
                eventType, eventId, e);
            idempotencyService.release(eventType, eventId);
            logService.logError(payload, "TRANSIENT: " + e.getMessage());
            // Asaas vai retentar (até 5×) e o evento será reprocessado

        } catch (Exception e) {
            // Erro PERMANENTE (payload inválido, regra de negócio violada):
            // mantém a chave para NÃO reprocessar lixo. Admin investiga.
            log.error("Erro permanente webhook event={} id={}", eventType, eventId, e);
            idempotencyService.markError(eventType, eventId);
            logService.logError(payload, e.getMessage());
            notificationService.sendWebhookErrorAlert(eventType, eventId, e.getMessage());
        }
    }
}
```

### 8.3 Handler: PAYMENT_RECEIVED

> Registrado na `WebhookHandlerFactory` também para `PAYMENT_CONFIRMED` — cartão de crédito
> confirma antes de liquidar; o tratamento é idêntico.

```java
// PaymentReceivedHandler.java
@Component
@Slf4j
public class PaymentReceivedHandler implements WebhookEventHandler {

    @Override
    public String getEventType() { return "PAYMENT_RECEIVED"; }

    @Override
    public void handle(AsaasWebhookPayload payload) {
        String asaasSubId = payload.getPayment().getSubscription();
        String asaasPaymentId = payload.getPayment().getId();
        BigDecimal value = payload.getPayment().getValue();

        // IMPORTANTE: buscar nextDueDate no Asaas ANTES de abrir a transação.
        // Chamada HTTP dentro de @Transactional segura conexão do pool durante round-trip
        // de rede (podendo levar segundos). Se a consulta falhar, lançar TransientException
        // para a retentativa do Asaas reprocessar — não deixar a transação abrir em vão.
        LocalDate nextDueDate;
        try {
            nextDueDate = asaasSubscriptionClient.get(asaasSubId).getNextDueDate();
        } catch (Exception e) {
            throw new TransientException("Falha ao consultar nextDueDate no Asaas para " + asaasSubId, e);
        }

        // Self-invocation não passa pelo proxy do Spring — @Transactional em método
        // chamado via `this` NÃO abre transação. Usar TransactionTemplate injetado.
        transactionTemplate.executeWithoutResult(tx ->
            handleTransactional(asaasSubId, asaasPaymentId, value, nextDueDate));
    }

    void handleTransactional(String asaasSubId, String asaasPaymentId,
                              BigDecimal value, LocalDate nextDueDate) {

        // 1. Buscar subscription pelo ID Asaas
        Subscription sub = subscriptionRepo.findByAsaasSubscriptionId(asaasSubId)
            .orElseThrow(() -> new NotFoundException("Subscription Asaas não encontrada: " + asaasSubId));

        // 1b. Validação de valor (28.8) — divergência não bloqueia, mas alerta
        if (value.compareTo(sub.getValue()) != 0) {
            log.warn("Valor divergente — esperado={} recebido={} tenant={}",
                sub.getValue(), value, sub.getTenantId());
            adminNotificationService.notifyValueMismatch(sub.getTenantId(), sub.getValue(), value);
        }

        // 2. Guard de máquina de estados — eventos fora de ordem
        if (sub.getStatus() == SubscriptionStatus.CANCELADO) {
            // Pagamento após cancelamento — admin decide (ver 27.7.6)
            adminNotificationService.notifyPaymentAfterCancellation(
                sub.getTenantId(), value, asaasPaymentId);
            return;
        }

        // 3. Ativar tenant + zerar dunning
        sub.setStatus(SubscriptionStatus.ATIVO);
        // nextDueDate já obtido do Asaas ANTES da transação — NUNCA calcular com plusDays(30/365).
        // Meses têm 28-31 dias e anos bissextos quebram a conta. O Asaas é a fonte.
        sub.setNextDueDate(nextDueDate);
        sub.setSuspendAt(null);
        sub.setCancelAt(null);
        sub.setReminderSentAt(null);
        sub.setGracePeriodExpiresAt(null);
        subscriptionRepo.save(sub);
        log.info("Tenant {} ativado via PAYMENT_RECEIVED asaasPaymentId={}", sub.getTenantId(), asaasPaymentId);

        // 4. WRITE-THROUGH no cache — nunca evict.
        // O handler conhece o novo valor; evict forçaria round-trip ao banco
        // (com read replica + lag = cache populado com valor velho).
        tenantStatusCacheService.put(sub.getTenantId(),
            TenantStatusResponse.of(sub.getTenantId(), SubscriptionStatus.ATIVO));

        // 5. Disparar geração de comissão de forma assíncrona (transação separada)
        commissionEngine.generateAsync(sub.getTenantId(), sub.getId(), asaasPaymentId, value);
    }
}
```

### 8.4 Handler: PAYMENT_OVERDUE

```java
// PaymentOverdueHandler.java
@Component
public class PaymentOverdueHandler implements WebhookEventHandler {

    @Value("${billing.grace-period-days:5}")
    private int gracePeriodDays;

    @Override
    public String getEventType() { return "PAYMENT_OVERDUE"; }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        String asaasSubId = payload.getPayment().getSubscription();

        Subscription sub = subscriptionRepo.findByAsaasSubscriptionId(asaasSubId)
            .orElseThrow(() -> new NotFoundException("Subscription Asaas não encontrada: " + asaasSubId));

        // Guard de estado (28.6): dunning só inicia a partir de ATIVO
        if (sub.getStatus() != SubscriptionStatus.ATIVO) {
            log.info("PAYMENT_OVERDUE ignorado — tenant {} em estado {}",
                sub.getTenantId(), sub.getStatus());
            return;
        }

        // Timestamps ABSOLUTOS de dunning (ver 27.7) — o relógio é nosso, não do Asaas
        Instant now = Instant.now();
        sub.setSuspendAt(now.plus(dunningProps.getGracePeriodDays(), ChronoUnit.DAYS));
        sub.setCancelAt(now.plus(dunningProps.getCancelAfterDays(), ChronoUnit.DAYS));
        sub.setGracePeriodExpiresAt(sub.getSuspendAt()); // compatibilidade
        sub.setReminderSentAt(null);
        subscriptionRepo.save(sub);

        // Email 1 — aviso imediato
        notificationService.sendOverdueAlert(sub.getTenantId(), sub.getSuspendAt());

        log.warn("Dunning iniciado — tenant {} suspendAt={} cancelAt={}",
            sub.getTenantId(), sub.getSuspendAt(), sub.getCancelAt());
    }
}
```

### 8.5 Handler: SUBSCRIPTION_INACTIVATED

```java
// SubscriptionInactivatedHandler.java
@Component
public class SubscriptionInactivatedHandler implements WebhookEventHandler {

    @Override
    public String getEventType() { return "SUBSCRIPTION_INACTIVATED"; }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        String asaasSubId = payload.getSubscription().getId();

        Subscription sub = subscriptionRepo.findByAsaasSubscriptionId(asaasSubId)
            .orElseThrow(() -> new NotFoundException("Subscription Asaas não encontrada: " + asaasSubId));

        // Guard de mudança de plano (27.6): cancelamento da assinatura antiga
        // durante o swap NÃO pode cancelar o tenant
        if (sub.isPendingPlanChange()) {
            log.info("SUBSCRIPTION_INACTIVATED ignorado — mudança de plano em andamento. tenantId={}",
                sub.getTenantId());
            return;
        }

        // METADADO APENAS (27.7.5) — o DunningJob controla as transições via cancel_at.
        // Este evento NÃO cancela o tenant. O relógio é da Syax, não do Asaas.
        sub.setAsaasInactivatedAt(Instant.now());
        subscriptionRepo.save(sub);

        log.info("Asaas inativou assinatura — DunningJob controla o cancelamento. tenantId={} status={}",
            sub.getTenantId(), sub.getStatus());
    }
}
```

### 8.6 Handler: PAYMENT_DELETED

```java
// PaymentDeletedHandler.java — cancela cobrança avulsa associada
@Component
public class PaymentDeletedHandler implements WebhookEventHandler {

    @Override
    public String getEventType() { return "PAYMENT_DELETED"; }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        String asaasPaymentId = payload.getPayment().getId();

        // Cancelar comissão gerada por este pagamento, se houver
        commissionRepo.findByAsaasPaymentId(asaasPaymentId)
            .ifPresent(commission -> {
                commission.setStatus(CommissionStatus.CANCELADO);
                commissionRepo.save(commission);
                log.info("Comissão cancelada por PAYMENT_DELETED: asaasPaymentId={}", asaasPaymentId);
            });
    }
}
```

### 8.7 DTO — AsaasWebhookPayload.java

```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasWebhookPayload {
    private String id;       // event id do Asaas — chave de idempotência preferida
    private String event;
    private AsaasPaymentData payment;
    private AsaasSubscriptionData subscription;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasPaymentData {
    private String id;
    private String customer;
    private String subscription;
    private String status;
    private BigDecimal value;
    private String billingType;
    private LocalDate dueDate;
    private LocalDate paymentDate;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasSubscriptionData {
    private String id;
    private String status;
    private String customer;
    private String cycle;
    private BigDecimal value;
}
```

### 8.8 Thread pool para async

```java
// AsyncConfig.java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("webhook-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("commissionExecutor")
    public Executor commissionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("commission-async-");
        executor.initialize();
        return executor;
    }
}
```

### 8.9 Handler: TRANSFER_COMPLETED

```java
// TransferCompletedHandler.java
@Component
@Slf4j
public class TransferCompletedHandler implements WebhookEventHandler {

    @Override
    public String getEventType() { return "TRANSFER_COMPLETED"; }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        String transferId = payload.getTransfer().getId();

        commissionRepo.findByPayoutAsaasId(transferId).ifPresentOrElse(commission -> {
            commission.setStatus(CommissionStatus.PAGO);
            commission.setConfirmedAt(Instant.now());
            commissionRepo.save(commission);
            log.info("Comissão confirmada via TRANSFER_COMPLETED — transferId={} partnerId={}",
                transferId, commission.getPartnerId());
        }, () -> log.warn("TRANSFER_COMPLETED sem comissão correspondente — transferId={}", transferId));
    }
}
```

### 8.10 Handler: TRANSFER_FAILED

```java
// TransferFailedHandler.java
@Component
@Slf4j
public class TransferFailedHandler implements WebhookEventHandler {

    @Override
    public String getEventType() { return "TRANSFER_FAILED"; }

    @Override
    @Transactional
    public void handle(AsaasWebhookPayload payload) {
        String transferId = payload.getTransfer().getId();
        String failReason = payload.getTransfer().getFailReason(); // ex: "INVALID_PIX_KEY"

        commissionRepo.findByPayoutAsaasId(transferId).ifPresent(commission -> {
            // Reverter para PENDENTE — próximo ciclo D+1 vai reprocessar
            commission.setStatus(CommissionStatus.PENDENTE);
            commission.setPayoutAsaasId(null);       // limpar para permitir nova tentativa
            commission.setApprovedAt(null);
            commission.setApprovedBy(null);
            commission.setTransferFailedReason(failReason);
            commissionRepo.save(commission);

            // Notificar admin imediatamente
            notificationService.sendTransferFailedAlert(
                commission.getPartnerId(), commission.getId(), failReason);

            log.error("Transfer falhou — comissão revertida para PENDENTE — " +
                "transferId={} partnerId={} reason={}",
                transferId, commission.getPartnerId(), failReason);
        });
    }
}
```

> **Comportamento esperado:** a comissão voltará ao pool `PENDENTE` e será reprocessada
> automaticamente no próximo ciclo D+1. O admin é notificado para investigar a causa
> (chave PIX inválida, banco indisponível, saldo insuficiente na conta Asaas).

---

## 9. Fluxo C — Engine de Comissões

### 9.1 CommissionEngine.java (orquestrador)

```java
// CommissionEngine.java
@Service
@Slf4j
public class CommissionEngine {

    private final PartnerReferralRepository referralRepo;
    private final CommissionStrategyFactory strategyFactory;

    @Async("commissionExecutor")
    @Transactional
    public void generateAsync(Long tenantId, Long subscriptionId, String asaasPaymentId, BigDecimal paymentValue) {
        try {
            // 1. Verificar se tenant tem parceiro vinculado
            Optional<PartnerReferral> referral = referralRepo.findActiveByTenantId(tenantId);
            if (referral.isEmpty()) {
                log.debug("Tenant {} sem parceiro — sem comissão", tenantId);
                return;
            }

            PartnerReferral pr = referral.get();
            Partner partner = pr.getPartner();

            if (partner.getStatus() != PartnerStatus.ATIVO) {
                log.warn("Parceiro {} não está ativo — comissão não gerada", partner.getId());
                return;
            }

            // 2. Selecionar strategy conforme modelo de comissão do parceiro
            CommissionStrategy strategy = strategyFactory.getStrategy(partner.getCommissionModel());

            // 3. Gerar comissão
            Subscription sub = subscriptionRepo.findById(subscriptionId)
                .orElseThrow(() -> new NotFoundException("Subscription " + subscriptionId));

            strategy.generate(partner, sub, asaasPaymentId, paymentValue);

        } catch (Exception e) {
            log.error("Erro ao gerar comissão para tenant={} paymentId={}", tenantId, asaasPaymentId, e);
            // Não relança — falha de comissão não deve impactar ativação do tenant
        }
    }
}
```

### 9.2 Strategy: RECORRENTE — escopo

> **Modelo ANUAL removido do escopo atual.** Apenas o modelo RECORRENTE está implementado.
> A interface `CommissionStrategy` e a `CommissionStrategyFactory` permanecem no código
> para que o modelo ANUAL seja adicionado no futuro sem alterar o `CommissionEngine`.

### 9.3 Strategy: RECORRENTE (implementação)

```java
// RecurrentCommissionStrategy.java
@Component
@Slf4j
public class RecurrentCommissionStrategy implements CommissionStrategy {

    @Override
    public CommissionModel getModel() { return CommissionModel.RECORRENTE; }

    @Override
    @Transactional
    public void generate(Partner partner, Subscription sub, String asaasPaymentId, BigDecimal paymentValue) {
        // Idempotência via UNIQUE constraint em asaas_payment_id
        // Se payment_id já existe, o INSERT vai lançar DataIntegrityViolationException
        // Capturamos e logamos silenciosamente

        // Calcular base mensal (se plano anual, prorateiar)
        BigDecimal monthlyBase = sub.getBillingCycle() == BillingCycle.YEARLY
            ? sub.getValue().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
            : sub.getValue();

        BigDecimal amount = monthlyBase
            .multiply(partner.getCommissionRate())
            .setScale(2, RoundingMode.HALF_UP);

        Commission commission = Commission.builder()
            .partnerId(partner.getId())
            .tenantId(sub.getTenantId())
            .subscriptionId(sub.getId())
            .amount(amount)
            .period(YearMonth.now().toString())
            .status(CommissionStatus.PENDENTE)
            .asaasPaymentId(asaasPaymentId) // UNIQUE — barreira de idempotência
            .commissionModel(CommissionModel.RECORRENTE)
            .baseValue(monthlyBase)
            .build();

        try {
            commissionRepo.save(commission);
            log.info("Comissão RECORRENTE gerada — partner={} tenant={} amount={}",
                partner.getId(), sub.getTenantId(), amount);
        } catch (DataIntegrityViolationException e) {
            log.info("Comissão RECORRENTE duplicada ignorada (UNIQUE asaas_payment_id={})", asaasPaymentId);
        }
    }
}
```

---

## 10. Fluxo D — Repasse de Comissões (Cron D+1)

### 10.1 CommissionPayoutJob.java

```java
// CommissionPayoutJob.java
@Component
@Slf4j
public class CommissionPayoutJob {

    private final DistributedLockService lockService;
    private final CommissionPayoutService payoutService;

    // D+1: notifica admin e envia os repasses automaticamente (usando effectiveAmount)
    @Scheduled(cron = "${billing.cron.commission-payout}")
    public void run() {
        YearMonth period = YearMonth.now().minusMonths(1);
        String lockKey = "syax:billing:lock:commission-payout:" + period;
        String lockOwner = UUID.randomUUID().toString();
        if (!lockService.acquire(lockKey, lockOwner, 1800)) return;
        try {
            List<CommissionSummary> summary = commissionRepo.summarizePendingByPartner(period.toString());
            if (summary.isEmpty()) return;
            BigDecimal total = summary.stream().map(CommissionSummary::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            notificationService.sendAdminPayoutPendingAlert(period, summary.size(), total);
            log.info("Admin notificado: {} parceiros · R${} pendentes para {}", summary.size(), total, period);
            payoutService.processPayouts(period);   // envia transfers — EM_TRANSFERENCIA até TRANSFER_COMPLETED
        } finally {
            lockService.release(lockKey, lockOwner);
        }
    }
}
```

### 10.2 CommissionPayoutService.java

```java
// CommissionPayoutService.java
@Service
@Slf4j
public class CommissionPayoutService {

    private final CommissionRepository commissionRepo;
    private final PartnerRepository partnerRepo;
    private final AsaasTransferClient transferClient;

    // Sem @Transactional aqui: cada parceiro é processado em transação própria e curta.
    // O transfer HTTP acontece FORA da transação — abrir transação durante chamada HTTP
    // segura conexão do pool por tempo indeterminado e aumenta risco de deadlock.
    // A atualização das comissões (EM_TRANSFERENCIA + payout_asaas_id) é feita em
    // transação curta, executada imediatamente APÓS o transfer ser confirmado.
    public void processPayouts(YearMonth period) {
        // 1. Buscar comissões PENDENTE do período agrupadas por parceiro
        Map<Long, List<Commission>> byPartner = commissionRepo
            .findPendingByPeriod(period.toString())
            .stream()
            .collect(Collectors.groupingBy(Commission::getPartnerId));

        for (Map.Entry<Long, List<Commission>> entry : byPartner.entrySet()) {
            Long partnerId = entry.getKey();
            List<Commission> commissions = entry.getValue();

            try {
                processPartnerPayout(partnerId, commissions, period);
            } catch (Exception e) {
                // Falha de um parceiro não bloqueia os outros
                log.error("Falha no repasse para parceiro={} período={}", partnerId, period, e);
            }
        }
    }

    private void processPartnerPayout(Long partnerId, List<Commission> commissions, YearMonth period) {
        Partner partner = partnerRepo.findById(partnerId)
            .orElseThrow(() -> new NotFoundException("Parceiro " + partnerId));

        if (partner.getPixKey() == null) {
            log.warn("Parceiro {} sem chave PIX — repasse adiado", partnerId);
            return;
        }

        // 2. Somar effectiveAmount das comissões do parceiro neste período
        BigDecimal total = commissions.stream()
            .map(this::effectiveAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sem valor mínimo de repasse — qualquer valor > 0 é transferido
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Total zerado para parceiro={} — nenhum repasse", partnerId);
            return;
        }

        // 3. Enviar transfer via check-then-act (seção 28.4) — NUNCA retry cego em money-out
        TransferResult result = sendTransfer(partner, commissions, period);

        if (!result.isSent()) {
            log.error("Transfer não criado para parceiro={} período={} — será reprocessado no próximo ciclo",
                partnerId, period);
            return;
        }

        // 4. Marcar como EM_TRANSFERENCIA em transação curta — PAGO só após TRANSFER_COMPLETED webhook
        markAsInTransfer(commissions, result.getTransferId());

        log.info("Transfer criado (PENDING) — parceiro={} total={} transferId={}",
            partnerId, total, result.getTransferId());
    }

    /**
     * Envia transfer PIX/TED agregado por parceiro (um PIX por parceiro por período).
     * Usa check-then-act após timeout para evitar double-payment (seção 28.4).
     * externalReference = "payout-{partnerId}-{period}" — idempotency key de lote.
     */
    public TransferResult sendTransfer(Partner partner, List<Commission> commissions, YearMonth period) {
        // Chave de idempotência de lote: um transfer por parceiro por período
        String externalRef = "payout-" + partner.getId() + "-" + period;

        BigDecimal total = commissions.stream()
            .map(this::effectiveAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        String description = String.format("Comissão Syax — Referência %s — Parceiro %d", period, partner.getId());

        try {
            AsaasTransferResponse resp = transferClient.createPixTransfer(
                AsaasPixTransferRequest.builder()
                    .value(total)
                    .pixAddressKey(partner.getPixKey())
                    .pixAddressKeyType(partner.getPixKeyType())
                    .description(description)
                    .externalReference(externalRef) // ← idempotency key de lote
                    .build()
            );
            return TransferResult.sent(resp.getId());

        } catch (FeignException.GatewayTimeout | RetryableException e) {
            // TIMEOUT: NÃO retentar às cegas — o Asaas pode ter processado.
            // Consultar por externalReference antes de desistir.
            // NUNCA usar @Retry em POST /transfers — risco de pagar o parceiro em dobro.
            Optional<AsaasTransferData> existing =
                transferClient.findByExternalReference(externalRef)
                    .getData().stream().findFirst();

            if (existing.isPresent()) {
                log.warn("Transfer já existia após timeout — parceiro={} period={} transferId={}",
                    partner.getId(), period, existing.get().getId());
                return TransferResult.sent(existing.get().getId());
            }
            // Não existe — seguro marcar para retry no próximo ciclo D+1
            return TransferResult.failed("timeout — não criada no Asaas");
        }
    }

    // Self-invocation não passa pelo proxy do Spring — @Transactional aqui não abriria
    // transação quando chamado via `this`. Usar TransactionTemplate injetado.
    private void markAsInTransfer(List<Commission> commissions, String transferId) {
        transactionTemplate.executeWithoutResult(tx -> {
            commissions.forEach(commission -> {
                commission.setStatus(CommissionStatus.EM_TRANSFERENCIA);
                commission.setPayoutAsaasId(transferId);
                commission.setApprovedAt(Instant.now());
            });
            commissionRepo.saveAll(commissions);
        });
    }

    private BigDecimal effectiveAmount(Commission c) {
        return c.getAdjustedAmount() != null ? c.getAdjustedAmount() : c.getAmount();
    }
}
```

### 10.3 Asaas API — Transferência PIX (preferencial) ou TED (fallback)

**PIX** — quando o parceiro tem `pix_key` preenchido:
```
POST https://api.asaas.com/v3/transfers
access_token: {ASAAS_API_KEY}

{
  "value": 150.00,
  "pixAddressKey": "parceiro@email.com",
  "pixAddressKeyType": "EMAIL",
  "description": "Comissão Syax — Referência 2025-01 — Parceiro 42",
  "externalReference": "payout-42-2025-01"
}
```
`pixAddressKeyType` aceita: `CPF`, `CNPJ`, `EMAIL`, `PHONE`, `EVP`.

**TED** — fallback quando não há chave PIX:
```
POST https://api.asaas.com/v3/transfers
access_token: {ASAAS_API_KEY}

{
  "value": 150.00,
  "bankAccount": {
    "bank": { "code": "{partner.bank_code}" },
    "ownerName": "{partner.name}",
    "cpfCnpj": "{partner.cpf_cnpj}",
    "agency": "{partner.bank_agency}",
    "account": "{partner.bank_account}",
    "accountDigit": "{partner.account_digit}",
    "bankAccountType": "CONTA_CORRENTE"
  },
  "description": "Comissão Syax — Referência 2025-01 — Parceiro 42",
  "externalReference": "payout-42-2025-01"
}
```

Lógica de seleção em `CommissionPayoutService`:
```java
private AsaasTransferResponse sendPayout(Partner partner, BigDecimal total, String description) {
    if (partner.getPixKey() != null) {
        return transferClient.pixTransfer(partner.getPixKey(), partner.getPixKeyType(), total, description);
    } else if (partner.getBankAccount() != null) {
        return transferClient.tedTransfer(partner.toBankAccountDto(), total, description);
    }
    throw new PayoutException("Parceiro " + partner.getId() + " sem PIX nem TED cadastrado");
}
```

---

## 11. Integração com Auth Service

### 11.1 Endpoint para Auth Service consultar

```
GET /internal/billing/status/{tenantId}
X-Service-Token: {INTERNAL_SERVICE_TOKEN}   ← token fixo entre microserviços

Response 200:
{
  "tenantId": 42,
  "status": "ATIVO",
  "expiresAt": null,
  "gracePeriodExpiresAt": null
}

Response 404: tenant sem subscription (deve bloquear login — tratar como inativo)
```

```java
// TenantStatusController.java
@RestController
@RequestMapping("/internal/billing")
public class TenantStatusController {

    @GetMapping("/status/{tenantId}")
    public ResponseEntity<TenantStatusResponse> getStatus(@PathVariable Long tenantId) {
        TenantStatusResponse status = tenantStatusService.getStatus(tenantId);
        return ResponseEntity.ok(status);
    }
}
```

### 11.2 TenantStatusService com cache Redis

```java
// TenantStatusService.java
@Service
public class TenantStatusService {

    private final SubscriptionRepository subscriptionRepo;
    private final TenantStatusCacheService cacheService;

    public TenantStatusResponse getStatus(Long tenantId) {
        // 1. Tentar cache primeiro
        Optional<TenantStatusResponse> cached = cacheService.get(tenantId);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. Buscar no banco
        Subscription sub = subscriptionRepo.findByTenantId(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant sem subscription: " + tenantId));

        TenantStatusResponse response = TenantStatusResponse.builder()
            .tenantId(tenantId)
            .status(sub.getStatus().name())
            .expiresAt(sub.getTrialExpiresAt())
            .gracePeriodExpiresAt(sub.getGracePeriodExpiresAt())
            .build();

        // 3. Cachear por 5 minutos
        cacheService.put(tenantId, response);

        return response;
    }
}
```

```java
// TenantStatusCacheService.java
@Service
public class TenantStatusCacheService {

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;

    @Value("${billing.redis.tenant-status-cache-ttl-seconds:300}")
    private long ttl;

    public Optional<TenantStatusResponse> get(Long tenantId) {
        String value = redis.opsForValue().get(buildKey(tenantId));
        if (value == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(value, TenantStatusResponse.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void put(Long tenantId, TenantStatusResponse response) {
        try {
            redis.opsForValue().set(buildKey(tenantId),
                objectMapper.writeValueAsString(response),
                Duration.ofSeconds(ttl));
        } catch (JsonProcessingException e) {
            log.warn("Falha ao cachear status tenant {}", tenantId);
        }
    }

    public void evict(Long tenantId) {
        redis.delete(buildKey(tenantId));
    }

    private String buildKey(Long tenantId) {
        return "syax:billing:tenant:status:" + tenantId;
    }
}
```

### 11.3 Regra no Auth Service

```java
// No Auth Service — antes de gerar JWT:
TenantStatusResponse status = billingClient.getStatus(tenant.getId()); // Feign

if (!"ATIVO".equals(status.getStatus()) && !"TRIAL".equals(status.getStatus())) {
    throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
        "Conta suspensa ou cancelada. Status: " + status.getStatus());
}
```

---

## 12. Cron Jobs

> **Dois schedulers separados, já implementados — o billing service não implementa D+10/D+15.**
> Ambos estão em outros serviços e usam repositórios do schema principal.

### 12.1 TrialScheduler (auth service) — fluxo B2C

Queries contra `Tenant` (schema principal). Runs em horário de baixa carga.

```java
// TrialScheduler.java (auth service)

@Scheduled(cron = "0 0 1 * * *")   // 01:00 — D+10
public void processarD10() {
    Instant ref  = Instant.now().minus(10, ChronoUnit.DAYS);
    Instant from = ref.minus(12, ChronoUnit.HOURS);
    Instant to   = ref.plus(12, ChronoUnit.HOURS);

    List<Tenant> tenants = tenantRepository
        .findByStatusAndTrialStartedAtBetween(EnumTenantStatus.TRIAL, from, to);

    for (Tenant tenant : tenants) {
        try {
            processarRelatorioD10(tenant);  // cria ticket na fila interna Syax
        } catch (Exception e) {
            log.error("Falha D+10 tenant={}", tenant.getId(), e);
        }
    }
}

@Scheduled(cron = "0 0 2 * * *")   // 02:00 — D+15
@Transactional
public void processarD15() {
    List<Tenant> tenants = tenantRepository
        .findByStatusAndTrialExpiresAtBefore(EnumTenantStatus.TRIAL, Instant.now());

    for (Tenant tenant : tenants) {
        try {
            processarExpiracaoTrial(tenant);  // cria ticket + e-mail automático
        } catch (Exception e) {
            log.error("Falha D+15 tenant={}", tenant.getId(), e);
        }
    }
}
```

### 12.2 PartnerScheduler — fluxo com parceiro

Queries contra `PartnerReferral` (`billing.partner_referral`). Roda às 08h para o
comercial começar o dia com os alertas já prontos.

```java
// PartnerScheduler.java

@Scheduled(cron = "0 0 8 * * *")   // 08:00 — D+10
@Transactional
public void processarD10() {
    OffsetDateTime now = OffsetDateTime.now();
    List<PartnerReferral> referrals = referralRepository
        .findByStatusAndTrialStartedAtBetween(
            "TRIAL", now.minusDays(11), now.minusDays(9));  // janela 2 dias

    for (PartnerReferral referral : referrals) {
        try {
            enviarRelatorioD10(referral);  // envia relatório de engajamento ao contador
        } catch (Exception e) {
            logger.error("Falha D+10 referralId={}", referral.getId(), e);
        }
    }
}

@Scheduled(cron = "0 5 8 * * *")   // 08:05 — D+15
@Transactional
public void processarD15() {
    OffsetDateTime now = OffsetDateTime.now();
    List<PartnerReferral> referrals = referralRepository
        .findByStatusAndTrialExpiresAtLessThanEqual("TRIAL", now);

    for (PartnerReferral referral : referrals) {
        try {
            transicionarParaFollowup(referral);  // inicia ciclo de follow-up
        } catch (Exception e) {
            logger.error("Falha D+15 referralId={}", referral.getId(), e);
        }
    }
}
```

> `PartnerReferral` tem `trial_started_at` e `trial_expires_at` — campos populados
> quando o tenant ativa o convite (`CONVIDADO → TRIAL`). Ver migration abaixo.

> D+15 encontra os mesmos referrals a cada execução até o status mudar.
> `transicionarParaFollowup` deve ser idempotente.

### 12.3 DunningJob — billing service

> **`GracePeriodSuspensionJob` foi SUBSTITUÍDO pelo `DunningJob`** — código completo na seção 27.7.4.
> Roda 4× ao dia (00h, 06h, 12h, 18h UTC), compara `now()` com `suspend_at` / `cancel_at`
> e executa lembretes, suspensões e cancelamentos. Usa write-through no cache (put, não evict).
> NÃO implementar o GracePeriodSuspensionJob antigo.

### 12.4 WebhookRecoveryJob — billing service

> Código completo na seção 28.5. A cada 10 minutos, reprocessa webhooks presos em
> `RECEBIDO` há mais de 10 min (pod morreu entre o HTTP 200 e o processamento async).
> A idempotência Redis protege contra dupla execução. Requer migration 005-12
> (UNIQUE em `webhook_log.asaas_event_id`).

---

## 13. Tratamento de Erros & Resiliência

### 13.1 Circuit Breaker (Resilience4j)

> **Atenção Spring Boot 4:** anotações `@CircuitBreaker` e `@Retry` do Resilience4j NÃO funcionam diretamente em interfaces Feign. A anotação precisa estar em um método concreto de um `@Service` que envolva a chamada ao client Feign, ou usar a integração `spring-cloud-starter-circuitbreaker-resilience4j` (que o billing-service já tem no pom). O código da interface abaixo é **ilustrativo do contrato** — as anotações servem de documentação; o comportamento real vem da configuração YAML + wrapper service.

```java
// AsaasClient.java
// REGRA: @Retry apenas em operações onde duplicata é detectável/corrigível.
// POST /transfers (money-out) NUNCA tem @Retry — ver seção 28.4 (check-then-act).
@FeignClient(name = "asaas", url = "${billing.asaas.base-url}",
    configuration = AsaasClientConfig.class)
public interface AsaasClient {

    @CircuitBreaker(name = "asaas-api", fallbackMethod = "createCustomerFallback")
    @Retry(name = "asaas-api")
    @PostMapping("/customers")
    AsaasCustomerResponse createCustomer(@RequestBody AsaasCustomerRequest req);

    @CircuitBreaker(name = "asaas-api", fallbackMethod = "createSubscriptionFallback")
    @Retry(name = "asaas-api")
    @PostMapping("/subscriptions")
    AsaasSubscriptionResponse createSubscription(@RequestBody AsaasSubscriptionRequest req);

    // ⚠ MONEY-OUT: SEM @Retry. Timeout → consultar por externalReference antes de reenviar.
    // Retry cego aqui = risco de pagar o parceiro em dobro. Fluxo completo na seção 28.4.
    @CircuitBreaker(name = "asaas-api")
    @PostMapping("/transfers")
    AsaasTransferResponse createPixTransfer(@RequestBody AsaasPixTransferRequest req);

    // Consulta por externalReference — usada no check-then-act após timeout
    @GetMapping("/transfers")
    AsaasListResponse<AsaasTransferData> listTransfersByExternalReference(
        @RequestParam("externalReference") String externalReference);
}
```

### 13.2 Configuração Resilience4j

```yaml
resilience4j:
  circuitbreaker:
    instances:
      asaas-api:
        failure-rate-threshold: 50
        slow-call-duration-threshold: 8s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        sliding-window-size: 10
        sliding-window-type: COUNT_BASED
  retry:
    instances:
      asaas-api:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - feign.RetryableException
          - java.net.ConnectException
        ignore-exceptions:
          - com.syax.billing.exception.AsaasValidationException
```

### 13.3 Dead letter — webhook_log

Webhooks com `status = 'ERRO'` na tabela `billing.webhook_log` são o DLQ. Monitoramento deve alertar quando a contagem de erros cresce. Reprocessamento manual:

```sql
-- Verificar erros nas últimas 24h
SELECT event_type, COUNT(*), MAX(received_at)
FROM billing.webhook_log
WHERE status = 'ERRO'
  AND received_at > NOW() - INTERVAL '24 hours'
GROUP BY event_type;
```

Para reprocessar manualmente (endpoint administrativo):
```
POST /api/admin/billing/v1/webhooks/{webhookLogId}/retry
Authorization: Bearer {admin-jwt}
```

### 13.4 DistributedLockService.java

```java
@Service
public class DistributedLockService {

    private final RedisTemplate<String, String> redis;
    private final RedisScript<Long> acquireScript;
    private final RedisScript<Long> releaseScript;

    public boolean acquire(String key, String owner, long ttlSeconds) {
        Long result = redis.execute(acquireScript,
            Collections.singletonList(key),
            owner, String.valueOf(ttlSeconds));
        return Long.valueOf(1L).equals(result);
    }

    public boolean release(String key, String owner) {
        Long result = redis.execute(releaseScript,
            Collections.singletonList(key),
            owner);
        return Long.valueOf(1L).equals(result);
    }
}
```

---

## 14. Estrutura de Classes (Spring Boot)

### 14.1 Estrutura de pacotes

```
com.syax.billing/
├── config/
│   ├── AsyncConfig.java
│   ├── RedisConfig.java
│   ├── FeignConfig.java (Asaas interceptors)
│   └── WebhookSecurityConfig.java
├── webhook/
│   ├── WebhookController.java
│   ├── WebhookProcessor.java
│   ├── WebhookIdempotencyService.java
│   ├── WebhookLogService.java
│   ├── WebhookHandlerFactory.java
│   ├── WebhookEventHandler.java (interface)
│   └── handler/
│       ├── PaymentReceivedHandler.java
│       ├── PaymentOverdueHandler.java
│       ├── SubscriptionInactivatedHandler.java
│       ├── PaymentDeletedHandler.java
│       ├── TransferCompletedHandler.java
│       └── TransferFailedHandler.java
├── subscription/
│   ├── SubscriptionService.java
│   ├── SubscriptionController.java
│   └── CancelResponse.java
├── commission/
│   ├── CommissionEngine.java
│   ├── CommissionStrategy.java (interface)
│   ├── CommissionStrategyFactory.java
│   ├── strategy/
│   │   └── RecurrentCommissionStrategy.java
│   └── payout/
│       ├── CommissionPayoutService.java
│       └── CommissionPayoutJob.java
├── tenant/
│   ├── TenantStatusController.java
│   ├── TenantStatusService.java
│   └── TenantStatusCacheService.java
├── cron/
│   ├── DistributedLockService.java
│   ├── DunningJob.java
│   ├── WebhookRecoveryJob.java
│   └── ReconciliationJob.java
├── asaas/
│   ├── client/
│   │   ├── AsaasClient.java (Feign interface)
│   │   ├── AsaasCustomerClient.java
│   │   ├── AsaasSubscriptionClient.java
│   │   ├── AsaasPaymentClient.java
│   │   └── AsaasTransferClient.java
│   └── dto/
│       ├── AsaasWebhookPayload.java
│       ├── AsaasCustomerRequest.java
│       ├── AsaasCustomerResponse.java
│       ├── AsaasSubscriptionRequest.java
│       ├── AsaasSubscriptionResponse.java
│       ├── AsaasPaymentData.java
│       ├── AsaasPixTransferRequest.java
│       └── AsaasTransferResponse.java
├── domain/
│   ├── Subscription.java (entity)
│   ├── Commission.java (entity)
│   ├── Partner.java (entity)
│   ├── PartnerReferral.java (entity)
│   ├── WebhookLog.java (entity)
│   └── TrialEngagement.java (entity)
├── repository/
│   ├── SubscriptionRepository.java
│   ├── CommissionRepository.java
│   ├── PartnerRepository.java
│   ├── PartnerReferralRepository.java
│   ├── WebhookLogRepository.java
│   └── TrialEngagementRepository.java
└── exception/
    ├── WebhookAuthException.java
    ├── AsaasValidationException.java
    ├── BusinessException.java
    └── NotFoundException.java
```

### 14.2 Interfaces e enums

```java
// WebhookEventHandler.java
public interface WebhookEventHandler {
    String getEventType();
    void handle(AsaasWebhookPayload payload);
}

// CommissionStrategy.java
public interface CommissionStrategy {
    CommissionModel getModel();
    void generate(Partner partner, Subscription sub, String asaasPaymentId, BigDecimal paymentValue);
}

// CommissionStrategyFactory.java
@Component
public class CommissionStrategyFactory {
    private final Map<CommissionModel, CommissionStrategy> strategies;

    public CommissionStrategyFactory(List<CommissionStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(CommissionStrategy::getModel, Function.identity()));
    }

    // Factory mantida para futura extensão (modelo ANUAL, etc.)
    public CommissionStrategy getStrategy(CommissionModel model) {
        return Optional.ofNullable(strategies.get(model))
            .orElseThrow(() -> new IllegalArgumentException("Strategy não encontrada: " + model));
    }
}

// Enums (se não existirem, criar em pacote domain/enums):
public enum SubscriptionStatus { TRIAL, ATIVO, SUSPENSO, CANCELADO }
public enum CommissionStatus { PENDENTE, EM_TRANSFERENCIA, PAGO, CANCELADO }
public enum CommissionModel { RECORRENTE } // ANUAL previsto no roadmap
public enum PlanType { MONTHLY, ANNUAL }
public enum BillingCycle { MONTHLY, YEARLY }
public enum PartnerStatus { PENDENTE, ATIVO, SUSPENSO, INATIVO }
public enum PixKeyType { CPF, CNPJ, EMAIL, PHONE, EVP }
```

### 14.3 CommissionRepository — queries específicas

```java
// CommissionRepository.java
public interface CommissionRepository extends JpaRepository<Commission, Long> {

    @Query("SELECT COUNT(c) > 0 FROM Commission c WHERE c.partnerId = :partnerId " +
           "AND c.tenantId = :tenantId AND c.referenceYear = :year " +
           "AND c.commissionModel = 'ANUAL' AND c.status != 'CANCELADO'")
    boolean existsAnnualForYear(Long partnerId, Long tenantId, int year);

    @Query("SELECT c FROM Commission c WHERE c.status = 'PENDENTE' AND c.period = :period")
    List<Commission> findPendingByPeriod(String period);

    @Query("SELECT c FROM Commission c WHERE c.tenantId = :tenantId AND c.status = 'PENDENTE'")
    List<Commission> findPendingByTenantId(Long tenantId);

    @Modifying
    @Query("UPDATE Commission c SET c.status = 'CANCELADO' WHERE c.tenantId = :tenantId AND c.status = 'PENDENTE'")
    void cancelPendingByTenantId(Long tenantId);

    Optional<Commission> findByAsaasPaymentId(String asaasPaymentId);
}
```

### 14.4 SubscriptionRepository — queries específicas

```java
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByTenantId(Long tenantId);

    Optional<Subscription> findByAsaasSubscriptionId(String asaasSubscriptionId);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIAL' " +
           "AND FUNCTION('DATE', s.trialStartedAt) + 10 = :today")
    List<Subscription> findTrialAt10Days(LocalDate today);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIAL' " +
           "AND s.trialExpiresAt <= :now")
    List<Subscription> findExpiredTrials(Instant now);

    // Queries usadas pelo DunningJob (substitui o antigo findGracePeriodExpired — fluxo redesenhado em 27.7)

    // Email 2: lembrete — suspend_at atingido em breve, lembrete ainda não enviado
    List<Subscription> findByStatusAndSuspendAtBeforeAndReminderSentAtIsNull(
        SubscriptionStatus status, Instant threshold);

    // Suspensão: suspend_at <= now e ainda ATIVO
    List<Subscription> findByStatusAndSuspendAtBefore(
        SubscriptionStatus status, Instant now);

    // Cancelamento: cancel_at <= now e ainda SUSPENSO
    List<Subscription> findByStatusAndCancelAtBefore(
        SubscriptionStatus status, Instant now);
}
```

---

## 15. Configuração — application.yml (billing service)

```yaml
server:
  port: 8082

spring:
  application:
    name: syax-billing
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:syax}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      schema: billing
      connection-init-sql: SET search_path TO billing
  jpa:
    properties:
      hibernate:
        default_schema: billing
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
  task:
    scheduling:
      thread-name-prefix: "cron-"

billing:
  asaas:
    base-url: ${ASAAS_BASE_URL:https://sandbox.asaas.com/api/v3}
    api-key: ${ASAAS_API_KEY}
    webhook-token: ${ASAAS_WEBHOOK_TOKEN}
    timeout-ms: 15000
  redis:
    webhook-idempotency-ttl-seconds: 86400
    tenant-status-cache-ttl-seconds: 300
    annual-commission-guard-ttl-seconds: 31536000
  grace-period-days: 5
  cron:
    trial-alert: "0 0 1 * * *"   # D+10 — implementado no auth service
    trial-expiry: "0 0 2 * * *"  # D+15 — implementado no auth service
    commission-payout: "0 0 2 1 * *"
    reconciliation: "0 30 2 * * *"
  internal:
    service-token: ${INTERNAL_SERVICE_TOKEN}

resilience4j:
  circuitbreaker:
    instances:
      asaas-api:
        failure-rate-threshold: 50
        slow-call-duration-threshold: 8000
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
  retry:
    instances:
      asaas-api:
        max-attempts: 3
        wait-duration: 1000ms
        exponential-backoff-multiplier: 2
```

---

## 16. Migrations Liquibase — Novos Campos

Estes campos devem ser adicionados às tabelas existentes no schema `billing`.  
Criar arquivo: `db/changelog/v1/005-billing-payment-engine-additions.yaml`

```yaml
databaseChangeLog:

  - changeSet:
      id: 005-00-billing-plan-table
      author: billing-engine
      comment: Tabela de planos gerenciada via CRUD admin
      changes:
        - createTable:
            tableName: plan
            schemaName: billing
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints: { primaryKey: true }
              - column:
                  name: name
                  type: VARCHAR(100)
                  constraints: { nullable: false }
              - column:
                  name: description
                  type: TEXT
              - column:
                  name: monthly_value
                  type: NUMERIC(10,2)
                  constraints: { nullable: false }
              - column:
                  name: active
                  type: BOOLEAN
                  defaultValueBoolean: true
                  constraints: { nullable: false }
              - column:
                  name: created_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
              - column:
                  name: updated_at
                  type: TIMESTAMPTZ
      rollback:
        - dropTable:
            tableName: plan
            schemaName: billing

  - changeSet:
      id: 005-00b-partner-referral-trial-dates
      author: billing-engine
      comment: Campos de trial no partner_referral — populados na ativação do token (CONVIDADO→TRIAL)
      changes:
        - addColumn:
            tableName: partner_referral
            schemaName: billing
            columns:
              - column:
                  name: trial_started_at
                  type: TIMESTAMPTZ
                  constraints:
                    nullable: true
              - column:
                  name: trial_expires_at
                  type: TIMESTAMPTZ
                  constraints:
                    nullable: true
      rollback:
        - dropColumn:
            tableName: partner_referral
            schemaName: billing
            columnName: trial_started_at
        - dropColumn:
            tableName: partner_referral
            schemaName: billing
            columnName: trial_expires_at

  - changeSet:
      id: 005-01-partner-pix-and-ted-fields
      author: billing-engine
      comment: Campos PIX (preferencial) e TED (fallback) para repasse de comissões
      changes:
        - addColumn:
            tableName: partner
            schemaName: billing
            columns:
              - column:
                  name: pix_key
                  type: VARCHAR(140)
                  constraints:
                    nullable: true
              - column:
                  name: pix_key_type
                  type: VARCHAR(20)
                  constraints:
                    nullable: true
              - column:
                  name: bank_code
                  type: VARCHAR(10)
                  constraints:
                    nullable: true
              - column:
                  name: bank_agency
                  type: VARCHAR(10)
                  constraints:
                    nullable: true
              - column:
                  name: bank_account
                  type: VARCHAR(20)
                  constraints:
                    nullable: true
              - column:
                  name: account_digit
                  type: VARCHAR(2)
                  constraints:
                    nullable: true
              - column:
                  name: account_type
                  type: VARCHAR(20)
                  defaultValue: CONTA_CORRENTE
                  constraints:
                    nullable: true
      rollback:
        - dropColumn:
            tableName: partner
            schemaName: billing
            columnName: pix_key
        - dropColumn:
            tableName: partner
            schemaName: billing
            columnName: pix_key_type
        - dropColumn:
            tableName: partner
            schemaName: billing
            columnName: bank_code
        - dropColumn:
            tableName: partner
            schemaName: billing
            columnName: bank_agency
        - dropColumn:
            tableName: partner
            schemaName: billing
            columnName: bank_account
        - dropColumn:
            tableName: partner
            schemaName: billing
            columnName: account_digit
        - dropColumn:
            tableName: partner
            schemaName: billing
            columnName: account_type

  - changeSet:
      id: 005-02-commission-payout-fields
      author: billing-engine
      comment: Adiciona campos de controle do repasse PIX na tabela commission
      changes:
        - addColumn:
            tableName: commission
            schemaName: billing
            columns:
              - column:
                  name: payout_asaas_id
                  type: VARCHAR(100)
                  constraints:
                    nullable: true
                    unique: true
                    uniqueConstraintName: uq_commission_payout_asaas_id
              - column:
                  name: paid_at
                  type: TIMESTAMPTZ
                  constraints:
                    nullable: true
      rollback:
        - dropColumn:
            tableName: commission
            schemaName: billing
            columnName: payout_asaas_id
        - dropColumn:
            tableName: commission
            schemaName: billing
            columnName: paid_at

  - changeSet:
      id: 005-02b-commission-model-fields
      author: billing-engine
      comment: Campos usados pelas strategies de comissão — necessários antes do índice 005-05 (reference_year)
      changes:
        - addColumn:
            tableName: commission
            schemaName: billing
            columns:
              - column:
                  name: commission_model
                  type: VARCHAR(20)
                  defaultValue: RECORRENTE
                  constraints:
                    nullable: false
              - column:
                  name: base_value
                  type: NUMERIC(10,2)
                  constraints:
                    nullable: true
              - column:
                  name: reference_year
                  type: INT
                  constraints:
                    nullable: true
      rollback:
        - dropColumn:
            tableName: commission
            schemaName: billing
            columnName: commission_model
        - dropColumn:
            tableName: commission
            schemaName: billing
            columnName: base_value
        - dropColumn:
            tableName: commission
            schemaName: billing
            columnName: reference_year

  - changeSet:
      id: 005-03-subscription-grace-period
      author: billing-engine
      comment: Adiciona controle de grace period e timestamps de status na subscription
      changes:
        - addColumn:
            tableName: subscription
            schemaName: billing
            columns:
              - column:
                  name: grace_period_expires_at
                  type: TIMESTAMPTZ
                  constraints:
                    nullable: true
              - column:
                  name: suspended_at
                  type: TIMESTAMPTZ
                  constraints:
                    nullable: true
              - column:
                  name: cancelled_at
                  type: TIMESTAMPTZ
                  constraints:
                    nullable: true
      rollback:
        - dropColumn:
            tableName: subscription
            schemaName: billing
            columnName: grace_period_expires_at
        - dropColumn:
            tableName: subscription
            schemaName: billing
            columnName: suspended_at
        - dropColumn:
            tableName: subscription
            schemaName: billing
            columnName: cancelled_at

  - changeSet:
      id: 005-04-webhook-log-error-message
      author: billing-engine
      comment: Adiciona coluna de mensagem de erro no webhook_log
      changes:
        - addColumn:
            tableName: webhook_log
            schemaName: billing
            columns:
              - column:
                  name: error_message
                  type: TEXT
                  constraints:
                    nullable: true
      rollback:
        - dropColumn:
            tableName: webhook_log
            schemaName: billing
            columnName: error_message

  - changeSet:
      id: 005-05-indexes-performance
      author: billing-engine
      comment: Índices para queries de cron jobs e webhook processing
      changes:
        - createIndex:
            indexName: idx_subscription_status_trial_dates
            tableName: subscription
            schemaName: billing
            columns:
              - column:
                  name: status
              - column:
                  name: trial_started_at
              - column:
                  name: trial_expires_at
        - createIndex:
            indexName: idx_subscription_grace_period
            tableName: subscription
            schemaName: billing
            columns:
              - column:
                  name: grace_period_expires_at
              - column:
                  name: status
        - createIndex:
            indexName: idx_commission_period_status
            tableName: commission
            schemaName: billing
            columns:
              - column:
                  name: period
              - column:
                  name: status
        - createIndex:
            indexName: idx_commission_partner_tenant_annual
            tableName: commission
            schemaName: billing
            columns:
              - column:
                  name: partner_id
              - column:
                  name: tenant_id
              - column:
                  name: reference_year
              - column:
                  name: commission_model
        - createIndex:
            indexName: idx_webhook_log_asaas_payment_event
            tableName: webhook_log
            schemaName: billing
            columns:
              - column:
                  name: asaas_payment_id
              - column:
                  name: event_type
      rollback:
        - dropIndex:
            indexName: idx_subscription_status_trial_dates
            schemaName: billing
        - dropIndex:
            indexName: idx_subscription_grace_period
            schemaName: billing
        - dropIndex:
            indexName: idx_commission_period_status
            schemaName: billing
        - dropIndex:
            indexName: idx_commission_partner_tenant_annual
            schemaName: billing
        - dropIndex:
            indexName: idx_webhook_log_asaas_payment_event
            schemaName: billing
```

Adicionar ao `db/db.changelog-master.yaml`:
```yaml
- include:
    file: db/changelog/v1/005-billing-payment-engine-additions.yaml
```

---

> **Migrations adicionais definidas em seções posteriores** (implementar todas):
> - `005-09-lgpd-partner-fields` (seção 27.2) — campos LGPD em billing.partner
> - `005-10-subscription-plan-change-flag` (seção 27.6) — pending_plan_change
> - `005-11-dunning-absolute-timestamps` (seção 27.7.1) — suspend_at, cancel_at, reminder_sent_at, asaas_inactivated_at
> - `005-12-webhook-log-event-id-unique` (seção 28.5) — asaas_event_id UNIQUE

---

## 17. Dependências Maven (adicionar ao pom.xml)

> **Atenção Spring Boot 4:** o projeto usa Spring Boot 4.x. O artefato `resilience4j-spring-boot3` é para Boot 3 e pode não ser compatível. Verificar se existe `resilience4j-spring-boot4`; caso contrário, usar `spring-cloud-starter-circuitbreaker-resilience4j` (que o billing-service já deve ter no pom como dependência do Spring Cloud 2025.x). Confirmar compatibilidade antes de adicionar `resilience4j-spring-boot3` ao pom.

```xml
<!-- Resilience4j -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <!-- ATENÇÃO: verificar artefato compatível com Spring Boot 4 — veja callout acima -->
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-feign</artifactId>
</dependency>

<!-- Spring Data Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>

<!-- OpenFeign (se não tiver) -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>

<!-- Commons Net para IP CIDR validation -->
<dependency>
    <groupId>commons-net</groupId>
    <artifactId>commons-net</artifactId>
    <version>3.10.0</version>
</dependency>
```

---

## 18. Admin — Gestão de Comissões e Repasses

> **Modelo híbrido:** o cron D+1 notifica o admin **e** envia os repasses automaticamente,
> usando `effectiveAmount = COALESCE(adjusted_amount, amount)`. Os endpoints admin servem para
> ajustar/cancelar comissões **antes** do D+1, conferir o preview e reprocessar repasses
> manualmente (parceiros pulados, `TRANSFER_FAILED`).

### 18.1 Modelo de roles (claims no JWT do auth service)

| Role | Permissões |
|---|---|
| `ROLE_BILLING_READ` | Visualizar comissões e extrato de parceiros |
| `ROLE_BILLING_ADMIN` | Tudo acima + editar valor de comissão + processar repasse + cancelar |

```java
// BillingAdminSecurityConfig.java
@Configuration
public class BillingAdminSecurityConfig {
    @Bean
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/billing/v1/admin/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/billing/v1/admin/**")
                    .hasAnyRole("BILLING_READ", "BILLING_ADMIN")
                .anyRequest().hasRole("BILLING_ADMIN")
            );
        return http.build();
    }
}
```

### 18.2 Endpoints

#### GET /api/billing/v1/admin/commissions

Lista paginada com filtros. Query params: `period` (obrigatório), `status`, `partnerId`.

Cada item retorna: `id`, `partnerId`, `partnerName`, `tenantId`, `tenantName`, `period`,
`amount` (calculado), `adjustedAmount` (null se não editado), `effectiveAmount` (COALESCE dos dois),
`status`, `adjustedBy`, `adjustedAt`, `adjustmentReason`.

#### GET /api/billing/v1/admin/commissions/payout-preview

Resumo do que será pago por parceiro se processado agora. Query param: `period`.

Retorna por parceiro: `pendingCount`, `totalEffective`, `pixKey` ou dados TED, `hasPayoutMethod`.
Inclui `partnersWithoutPayoutMethod` no total — esses serão pulados no processamento.

#### PATCH /api/billing/v1/admin/commissions/{id}/adjust — ROLE_BILLING_ADMIN

Ajusta o valor efetivo. Só comissões `PENDENTE`. Body: `adjustedAmount`, `reason`.

```java
@Transactional
public CommissionAdjustResponse adjust(Long id, BigDecimal newAmount, String reason, String adminEmail) {
    Commission c = commissionRepo.findById(id)
        .orElseThrow(() -> new NotFoundException("Comissão " + id));
    if (c.getStatus() != CommissionStatus.PENDENTE)
        throw new BusinessException("Apenas comissões PENDENTE podem ser ajustadas");
    if (newAmount.compareTo(BigDecimal.ZERO) < 0)
        throw new BusinessException("Valor ajustado não pode ser negativo");
    c.setAdjustedAmount(newAmount);
    c.setAdjustedBy(adminEmail);
    c.setAdjustedAt(Instant.now());
    c.setAdjustmentReason(reason);
    return CommissionAdjustResponse.from(commissionRepo.save(c));
}
```

#### POST /api/billing/v1/admin/payouts/process — ROLE_BILLING_ADMIN

**Aciona/reprocessa o repasse manualmente** — fora do ciclo automático D+1 (ex.: parceiros pulados,
comissões revertidas por `TRANSFER_FAILED`). Body: `{ "period": "2025-01", "partnerIds": null }` (`null` = todos).

Usa `effectiveAmount = COALESCE(adjusted_amount, amount)` para calcular cada repasse.
Parceiros sem PIX nem TED são pulados com status `PULADO` no response.

```java
private BigDecimal effectiveAmount(Commission c) {
    return c.getAdjustedAmount() != null ? c.getAdjustedAmount() : c.getAmount();
}
```

**Processamento assíncrono** (`PayoutAdminService.startJobAsync`): a requisição retorna `202 Accepted`
com `jobId`; o job roda em background sob o mesmo distributed lock do cron (execução concorrente é
rejeitada) e o estado fica em `syax:billing:payout:job:{jobId}` no Redis (TTL 24h).

```
Response 202: { "jobId": "...", "period": "2025-01" }

GET /api/billing/v1/admin/payouts/{jobId}/status
Response 200: { jobId, status: PROCESSANDO | CONCLUIDO | ERRO, period, processed, skipped,
                totalPaid, results: [{partnerId, status, amount, transferId, method}] }
Response 404: jobId inexistente ou expirado
```

#### PATCH /api/billing/v1/admin/commissions/{id}/cancel — ROLE_BILLING_ADMIN

Cancela comissão `PENDENTE`. Body: `{ "reason": "..." }`.
Grava `cancelled_by`, `cancellation_reason`. Não reverte comissões já pagas.

### 18.3 Campos novos — billing.commission (migration 005-06)

```yaml
- changeSet:
    id: 005-06-commission-admin-fields
    author: billing-engine
    changes:
      - addColumn:
          tableName: commission
          schemaName: billing
          columns:
            - column: { name: adjusted_amount, type: NUMERIC(10,2) }
            - column: { name: adjusted_by,     type: VARCHAR(200)  }
            - column: { name: adjusted_at,     type: TIMESTAMPTZ   }
            - column: { name: adjustment_reason, type: TEXT         }
            - column: { name: approved_by,     type: VARCHAR(200)  }
            - column: { name: approved_at,     type: TIMESTAMPTZ   }
            - column: { name: cancelled_by,    type: VARCHAR(200)  }
            - column: { name: cancellation_reason,   type: TEXT         }
            - column: { name: confirmed_at,           type: TIMESTAMPTZ  }
            - column: { name: transfer_failed_reason, type: VARCHAR(300) }
```

> `confirmed_at` — preenchido pelo `TransferCompletedHandler` ao receber o webhook do Asaas.  
> `transfer_failed_reason` — preenchido pelo `TransferFailedHandler` com o motivo da falha (ex: `INVALID_PIX_KEY`, `INSUFFICIENT_BALANCE`).

### 18.4 Classes novas

```
com.syax.billing/
└── admin/
    ├── CommissionAdminController.java   // GET list, GET preview, PATCH adjust, PATCH cancel
    ├── CommissionAdminService.java      // adjust(), cancel(), preview()
    ├── PayoutAdminController.java       // POST /admin/payouts/process
    ├── BillingAdminSecurityConfig.java  // role-based access
    └── dto/
        ├── CommissionAdjustRequest.java
        ├── CommissionAdjustResponse.java
        ├── PayoutProcessRequest.java
        ├── PayoutProcessResult.java
        ├── PayoutPreviewResponse.java
        └── CommissionSummary.java
```

### 18.5 Queries novas em CommissionRepository

```java
// Resumo por parceiro (usado pelo cron e pelo preview)
@Query(value = """
    SELECT partner_id, COUNT(*), SUM(COALESCE(adjusted_amount, amount))
    FROM billing.commission
    WHERE period = :period AND status = 'PENDENTE'
    GROUP BY partner_id""", nativeQuery = true)
List<Object[]> summarizePendingByPartnerRaw(String period);

@Query("SELECT c FROM Commission c WHERE c.period = :period"
    + " AND c.status = 'PENDENTE' AND c.partnerId IN :ids")
List<Commission> findPendingByPeriodAndPartners(String period, List<Long> ids);
```

---

## 19. Reconciliação Asaas ↔ Sistema (ReconciliationJob)

Cobre o cenário onde o Asaas esgotou as 5 retentativas de webhook e o tenant permanece
em `TRIAL` mesmo tendo pago. Sem este job, o problema só seria detectado manualmente.

### 19.1 Comportamento

Cron diário às `02:30`, após o `CommissionPayoutJob`. Consulta o Asaas, cruza com o banco
e dispara o `PaymentReceivedHandler` para cada divergência encontrada.

```java
@Component @Slf4j
public class ReconciliationJob {

    @Scheduled(cron = "${billing.cron.reconciliation}")
    public void run() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String lockKey = "syax:billing:lock:reconciliation:" + yesterday;
        String lockOwner = UUID.randomUUID().toString();
        if (!lockService.acquire(lockKey, lockOwner, 1800)) return;
        try {
            int fixed = reconcile(yesterday);
            if (fixed > 0) notificationService.sendReconciliationReport(yesterday, fixed);
        } finally {
            lockService.release(lockKey, lockOwner);
        }
    }

    @Transactional
    public int reconcile(LocalDate date) {
        // 1. Buscar pagamentos RECEIVED no Asaas para o período (paginado)
        List<AsaasPaymentData> payments = asaasPaymentClient.listReceived(date);

        AtomicInteger fixed = new AtomicInteger();
        for (AsaasPaymentData payment : payments) {
            if (payment.getSubscription() == null) continue;

            subscriptionRepo.findByAsaasSubscriptionId(payment.getSubscription())
                .filter(sub -> sub.getStatus() == SubscriptionStatus.TRIAL)
                .ifPresent(sub -> {
                    // Divergência: Asaas diz RECEIVED, sistema está em TRIAL
                    log.warn("Reconciliation: divergência tenant={} paymentId={}",
                        sub.getTenantId(), payment.getId());
                    // Reutiliza handler existente — toda a lógica (ativação + comissão) já está lá
                    paymentReceivedHandler.handle(buildSyntheticPayload(payment));
                    fixed.incrementAndGet();   // conta apenas divergências corrigidas
                });
        }
        return fixed.get();
    }

    private AsaasWebhookPayload buildSyntheticPayload(AsaasPaymentData payment) {
        AsaasWebhookPayload p = new AsaasWebhookPayload();
        p.setEvent("PAYMENT_RECEIVED");
        p.setPayment(payment);
        return p;
    }
}
```

### 19.2 Asaas API — listar pagamentos por período

```
GET /v3/payments?status=RECEIVED&paymentDate[ge]=2025-01-31&paymentDate[le]=2025-01-31&limit=100
access_token: {ASAAS_API_KEY}
```

> Filtrar por **`paymentDate`**, não `dateCreated` — um boleto criado dias antes e pago ontem
> seria perdido pelo filtro de data de criação.

Paginar com `offset` enquanto `hasMore = true`. Adicionar `AsaasPaymentClient.listReceived()`
como novo método no cliente Feign existente.

### 19.3 Idempotência

`PaymentReceivedHandler` já é idempotente via Redis + `UNIQUE (asaas_payment_id)`.
Chamar duas vezes para o mesmo `pay_xxx` é seguro — a segunda tentativa é descartada.

### 19.4 Janela variável

`RECONCILIATION_LOOKBACK_DAYS` (env var, padrão `1`) permite ampliar a janela
em caso de indisponibilidade prolongada sem mudar código.

---

## 20. Cancelamento de Assinatura

### 20.1 Endpoint

```
POST /api/billing/v1/subscriptions/cancel
Authorization: Bearer {jwt-tenant}
Body: { "reason": "..." }   ← opcional, para análise de churn

Response 200: { "tenantId": 42, "status": "CANCELAMENTO_SOLICITADO" }
Response 409: se já CANCELADO ou CANCELAMENTO_SOLICITADO
Response 422: se status TRIAL (sem assinatura Asaas para cancelar)
```

Admin:
```
POST /api/billing/v1/admin/subscriptions/{tenantId}/cancel
Authorization: ROLE_BILLING_ADMIN
```

### 20.2 Lógica

```java
// SubscriptionService.java
@Transactional
public CancelResponse requestCancellation(Long tenantId, String reason) {
    Subscription sub = subscriptionRepo.findByTenantId(tenantId)
        .orElseThrow(() -> new NotFoundException("Subscription não encontrada"));

    if (sub.getStatus() == SubscriptionStatus.CANCELADO
            || sub.getStatus() == SubscriptionStatus.CANCELAMENTO_SOLICITADO)
        throw new BusinessException("Assinatura já cancelada ou com cancelamento em curso");

    if (sub.getAsaasSubscriptionId() == null)
        throw new BusinessException("Tenant em trial — sem assinatura Asaas ativa");

    // 1. Cancelar no Asaas — dispara SUBSCRIPTION_INACTIVATED assincronamente
    asaasSubscriptionClient.cancel(sub.getAsaasSubscriptionId());

    // 2. Status intermediário — o CANCELADO real vem pelo webhook
    sub.setStatus(SubscriptionStatus.CANCELAMENTO_SOLICITADO);
    sub.setCancellationReason(reason);
    subscriptionRepo.save(sub);

    tenantStatusCacheService.evict(tenantId);
    return new CancelResponse(tenantId, "CANCELAMENTO_SOLICITADO");
}
```

### 20.3 Asaas API

```
DELETE /v3/subscriptions/{id}
access_token: {ASAAS_API_KEY}
Response: { "deleted": true }
```

Asaas cancela cobranças futuras e dispara `SUBSCRIPTION_INACTIVATED`.
Cobranças já pagas não são revertidas.

### 20.4 Novo status e migration

```java
public enum SubscriptionStatus {
    TRIAL, ATIVO, SUSPENSO, CANCELAMENTO_SOLICITADO, CANCELADO
}
```

```yaml
- changeSet:
    id: 005-07-subscription-cancellation
    author: billing-engine
    changes:
      - addColumn:
          tableName: subscription
          schemaName: billing
          columns:
            - column:
                name: cancellation_reason
                type: TEXT
                constraints:
                  nullable: true
    rollback:
      - dropColumn:
          tableName: subscription
          schemaName: billing
          columnName: cancellation_reason
```

### 20.5 SubscriptionInactivatedHandler — atualização

Handler já existente funciona para qualquer status de origem (`ATIVO` ou
`CANCELAMENTO_SOLICITADO`). Apenas garantir que `cancellationReason`
já preenchido pelo endpoint não seja sobrescrito:

```java
sub.setStatus(SubscriptionStatus.CANCELADO);
sub.setCancelledAt(Instant.now());
// cancellationReason: preservar o que veio do endpoint se já preenchido
if (sub.getCancellationReason() == null) {
    sub.setCancellationReason("Cancelado via webhook SUBSCRIPTION_INACTIVATED");
}
```


---

## 21. Ordem de Implementação

Seguir esta ordem para garantir que cada fase é testável antes de avançar.

> ⚠ **LER ANTES DE COMEÇAR — seções que alteram código desta ordem:**
> - **27.6** Mudança de plano (PlanChangeService + flag pending_plan_change)
> - **27.7** Dunning redesenhado (DunningJob substitui GracePeriodSuspensionJob; PaymentOverdueHandler usa suspend_at/cancel_at)
> - **28** Hardening (TransientException, event.id como chave, check-then-act em transfers, WebhookRecoveryJob, guards de estado, validação de valor)
    > Os códigos das seções 27-28 são a versão FINAL — onde houver divergência com seções anteriores, 27-28 prevalecem.

### Fase 1 — Infraestrutura (sem lógica de negócio) — ✅ FEITO (2026-06-20)
1. [x] Criar os 5 scripts Lua em `src/main/resources/lua/` (`webhookIdempotencyAcquire`, `webhookComplete`, `acquireDistributedLock`, `releaseDistributedLock`, `annualCommissionGuard`)
2. [x] Implementar `RedisConfig.java` (em `infra/config/`) — carrega os 5 scripts como beans + `RedisTemplate<String,String>`
3. [x] Implementar `DistributedLockService.java` (em `infra/redis/`)
4. [x] Implementar `WebhookIdempotencyService.java` (em `infra/redis/`)
5. [x] Implementar `TenantStatusCacheService.java` (em `infra/redis/`) — simplificado para `String` status; endpoint síncrono §11 descartado por decisão de resiliência (auth é event-driven)
6. [x] Testar scripts Lua com `redis-cli EVAL` — validado em 2026-06-21 (idempotência 1→0, lock com ownership, guard anual 1→0)

> **Infra adicional desta fase (fora da lista original):** Redis adicionado ao `compose.yaml` (serviço `redis` + volume `redis_data`); `spring-boot-starter-data-redis` + `commons-pool2` no `billing-service/pom.xml`; bloco `spring.data.redis` + `billing.redis.*` TTLs no `application.yaml`. Compila limpo (`mvnw -pl billing-service -am compile`).
>
> **Desvios propositais da spec no repo:** pacotes seguem a convenção do repo (`com.l.erp.billingservice.infra.{config,redis}`, não `com.syax.billing.config/...`). A integração auth via §11 (Feign síncrono) **não** será implementada — substituída pelo consumo Kafka já feito no auth (`SubscriptionActivatedConsumer`).

### Fase 2 — Webhook endpoint (core do sistema) — ✅ FEITO (10/10 — 2026-06-21)
7. [x] DTOs de webhook em `infra/asaas/dto/`: `AsaasWebhookPayload`, `AsaasPaymentData`, `AsaasSubscriptionData`, `AsaasTransferData` (DTOs de request/response do client Asaas ficam para a Fase 3)
8. [x] `WebhookSecurityService.java` (em `services/`) — constant-time `MessageDigest.isEqual`, lê `asaas.webhook-token`, lança `WebhookAuthException` (em `infra/exception/`)
9. [x] `WebhookLogService.java` (em `services/`) — RECEBIDO/PROCESSADO/IGNORADO/ERRO + tolerância a duplicata. Requereu: migration `billing-schema-013` (coluna `asaas_event_id` + UNIQUE `uq_webhook_log_event_id`, registrada no master), campo `asaasEventId` na entity `WebhookLog`, `findByAsaasEventId` no repo, constantes `WEBHOOK_*` em common
10. [x] `WebhookController.java` — valida token (401 só aqui), parseia, `logReceived`, dispara async, 200 sempre (§28.7). Path mantido `/api/v1/webhooks/asaas`
11. [x] `WebhookProcessor.java` (em `services/webhook/`) — `@Async("webhookExecutor")`, idempotência Redis, `resolveEventId` (event.id→payment→transfer→subscription §28.2), classifica `TransientException`/`TransientDataAccessException` (release) vs permanente (markError)
12. [x] `WebhookHandlerFactory.java` — mapa por `getEventType()`; `PAYMENT_CONFIRMED` aliased p/ `PaymentReceivedHandler`
13. [x] `PaymentReceivedHandler.java` — ATIVA + zera dunning + write-through cache + publica `billing.subscription.activated`; guards CANCELADO (§27.7.6) e valor divergente (§28.8). `nextDueDate` do Asaas pendente p/ Fase 3 (§28.3)
14. [x] `PaymentOverdueHandler.java` — seta `suspend_at`/`cancel_at` (timestamps absolutos §27.7.3), guard estado (§28.6)
15. [x] `SubscriptionInactivatedHandler.java` — só metadado `asaas_inactivated_at` (§27.7.5)
16. [x] `PaymentDeletedHandler.java` — cancela comissão por `asaas_payment_id` (§8.6)

> **Infra de suporte da Fase 2 (além dos 10 itens):** `infra/config/AsyncConfig` (`@EnableAsync` + `webhookExecutor`/`commissionExecutor`), `infra/config/DunningProperties` (`billing.dunning.*` + bloco no `application.yaml`), `infra/exception/TransientException`, `domain/SubscriptionStatus` (constantes String — enum fica p/ Fase 4), migration `billing-schema-014` (timestamps de dunning `grace_period_expires_at`/`suspend_at`/`cancel_at`/`reminder_sent_at`/`asaas_inactivated_at` na subscription, §27.7.1) + campos na entity `Subscription`, `findByAsaasPaymentId` no `CommissionRepository`. **Removido:** `WebhookProcessorService` antigo (substituído pela pipeline). Compila limpo. NotificationService/e-mails de dunning entram na fase de dunning (Fase 7).

### Fase 3 — Asaas client e criação de assinatura
17. Implementar `AsaasClient.java` (Feign + Resilience4j)
18. Implementar `AsaasCustomerClient.java`, `AsaasSubscriptionClient.java`, `AsaasPaymentClient.java`
19. Implementar `SubscriptionService.java`
20. Implementar `SubscriptionController.java`
21. Testar fluxo completo no sandbox Asaas

### Fase 4 — Engine de comissões
22. Implementar `CommissionStrategy.java` (interface) e `CommissionStrategyFactory.java`
23. Implementar `RecurrentCommissionStrategy.java`
24. Implementar `CommissionEngine.java`
25. Conectar `PaymentReceivedHandler` ao `CommissionEngine`
26. Rodar `005-billing-payment-engine-additions.yaml` (migration)

### Fase 5 — Status API e integração Auth
27. Implementar `TenantStatusService.java`
28. Implementar `TenantStatusController.java`
29. Testar integração com Auth Service (mock billing status → verificar que JWT é bloqueado)

### Fase 6 — Payout de comissões
30. Implementar `AsaasTransferClient.java`
31. Implementar `CommissionPayoutService.java`
32. Implementar `CommissionPayoutJob.java` com distributed lock
33. Implementar `TransferCompletedHandler.java` e `TransferFailedHandler.java` (8.9/8.10)
34. Testar com contas sandbox Asaas

### Fase 7 — Cron jobs de dunning e recuperação
35. Implementar `DunningJob.java` (27.7.4 — substitui o antigo GracePeriodSuspensionJob)
36. Implementar `WebhookRecoveryJob.java` (28.5)
37. Implementar `ReconciliationJob.java` (seção 19)

> Os crons de trial D+10/D+15 já estão implementados no auth service e no partner service (seção 12) — nada a fazer no billing.

---

## 22. Testes Requeridos

### 22.1 Testes unitarios

| Classe | Cenario |
|---|---|
| `WebhookIdempotencyService` | `tryAcquire` retorna 1 na 1a vez, 0 na 2a |
| `WebhookIdempotencyService` | `markDone`/`markError` atualiza Redis |
| `DistributedLockService` | Acquire + release; acquire falha se ja existe |
| `RecurrentCommissionStrategy` | Prorateio correto para plano anual (div 12) |
| `RecurrentCommissionStrategy` | Idempotencia via `DataIntegrityViolationException` |
| `CommissionPayoutService` | Parceiro sem PIX nem TED e pulado sem quebrar os outros |
| `CommissionPayoutService` | `effectiveAmount` usa `adjustedAmount` se preenchido |
| `WebhookSecurityService` | Token invalido lanca `WebhookAuthException` |
| `TenantStatusCacheService` | Cache hit retorna valor; `evict` remove |
| `SubscriptionService` | Plano anual: value = monthlyValue x 10 |
| `SubscriptionService` | B2C: cria `Subscription` nova se nao existe (`orElseGet`) |
| `SubscriptionService` | Parceiro: usa `Subscription` existente |
| `SubscriptionService.requestCancellation` | ATIVO -> CANCELAMENTO_SOLICITADO + Asaas chamado |
| `SubscriptionService.requestCancellation` | Tenant em TRIAL -> 422 |
| `SubscriptionService.requestCancellation` | Ja CANCELADO -> 409 |
| `CommissionAdminService.adjust` | Ajuste valido grava `adjustedAmount` + auditoria |
| `CommissionAdminService.adjust` | Comissao nao-PENDENTE -> 422 |
| `CommissionAdminService.adjust` | Valor negativo -> 422 |
| `CommissionAdminService.cancel` | PENDENTE -> CANCELADO com motivo |
| `PartnerReferralAdminService.linkRetroactively` | Cria vinculo CONVERTIDO para tenant ATIVO |
| `PartnerReferralAdminService.linkRetroactively` | Tenant ja tem parceiro -> 409 |
| `PartnerReferralAdminService.linkRetroactively` | Tenant nao-ATIVO -> 422 |
| `PayoutAdminService.startJobAsync` | Job criado no Redis com status PROCESSANDO |
| `PayoutAdminService.startJobAsync` | Job atualizado para CONCLUIDO ao terminar |
| `PayoutAdminService.startJobAsync` | Job atualizado para ERRO em excecao |
| `CommissionPayoutJob` | D+1 notifica admin e envia transfers usando `effectiveAmount` |
| `CommissionPayoutJob` | Sem comissoes PENDENTE -> nao notifica nem transfere |
| `ReconciliationJob` | Tenant TRIAL com pagamento RECEIVED -> aciona handler |
| `ReconciliationJob` | Sem divergencias -> nenhuma acao |
| `TransferCompletedHandler` | `commission.status = PAGO` + `confirmed_at` preenchido |
| `TransferCompletedHandler` | `transferId` desconhecido -> loga e ignora silenciosamente |
| `TransferFailedHandler` | `commission.status = PENDENTE` + `payout_asaas_id = null` |
| `TransferFailedHandler` | `transfer_failed_reason` gravado + notificacao disparada |

### 22.2 Testes de integracao (TestContainers: PostgreSQL + Redis)

| Cenario | Verificacao |
|---|---|
| `PAYMENT_RECEIVED` -> ativar tenant | `subscription.status = ATIVO` + cache atualizado via write-through (put) |
| `PAYMENT_RECEIVED` duplicado | Processado 1x; 2a chamada descartada via Redis |
| `PAYMENT_RECEIVED` com parceiro RECORRENTE | `commission.status = PENDENTE` + valor correto |
| `PAYMENT_OVERDUE` | `suspend_at = now+5d` e `cancel_at = now+7d` gravados |
| `SUBSCRIPTION_INACTIVATED` | Apenas grava `asaas_inactivated_at` — status nao muda (27.7.5) |
| `SUBSCRIPTION_INACTIVATED` pos-cancelamento via endpoint | `cancellationReason` preservado |
| `TRANSFER_COMPLETED` | `commission.status = PAGO` + `confirmed_at` preenchido |
| `TRANSFER_FAILED` | `commission.status = PENDENTE` + `payout_asaas_id = null` + notificacao |
| `TRANSFER_FAILED` + proximo D+1 | Comissao incluida novamente no proximo payout |
| `DunningJob` — lembrete | Email enviado quando `suspend_at - 2d <= now` e `reminder_sent_at IS NULL` |
| `DunningJob` — suspensao | Tenant SUSPENSO + cache write-through (put) quando `suspend_at <= now` |
| `DunningJob` — cancelamento | Tenant CANCELADO + comissoes PENDENTE canceladas quando `cancel_at <= now` |
| `POST /admin/payouts/process` | Retorna 202 + `jobId`; nao bloqueia request |
| `GET /admin/payouts/{jobId}/status` | Evolui PROCESSANDO -> CONCLUIDO |
| `GET /admin/payouts/{jobId}/status` | `jobId` inexistente -> 404 |
| `POST /admin/payouts/process` concorrente | Apenas 1 instancia executa (lock Redis) |
| `PATCH /admin/commissions/{id}/adjust` | `effectiveAmount` usado no proximo payout |
| `PATCH /admin/commissions/{id}/adjust` | Comissao PAGO -> 422 |
| `PATCH /admin/commissions/{id}/cancel` | Status CANCELADO + motivo gravado |
| `POST /admin/referrals/link` | Proximo `PAYMENT_RECEIVED` gera comissao para parceiro |
| `POST /subscriptions` (B2C) | Cria `billing.subscription` + Asaas customer + sub |
| `POST /subscriptions/cancel` | `CANCELAMENTO_SOLICITADO` + Asaas chamado |
| `ReconciliationJob` | Tenant TRIAL com pagamento no Asaas -> ativado + comissao |
| `ReconciliationJob` idempotente | Mesmo `asaas_payment_id` processado 2x -> 2a ignorada |
| `GET /internal/billing/status` | CANCELAMENTO_SOLICITADO -> permite login (200) |
| `GET /internal/billing/status` | SUSPENSO -> retorna 402 |

### 22.3 Contract tests (WireMock -- Asaas sandbox)

| Cenario |
|---|
| `POST /v3/customers` -> 200 com ID |
| `POST /v3/subscriptions` -> 200 com boleto + PIX |
| `POST /v3/transfers` PIX -> 200 com `tra_xxx` PENDING |
| `POST /v3/transfers` chave PIX invalida -> 400 -> `AsaasValidationException` |
| `DELETE /v3/subscriptions/{id}` -> `{ "deleted": true }` |
| `GET /v3/payments?status=RECEIVED` -> paginado, `hasMore=true` 1a pagina, `false` na 2a |
| Asaas timeout -> retry 3x -> circuit breaker abre |
| Circuit breaker aberto -> fallback executado sem exception |

---

## 23. DTOs e métodos em falta (compilação)

### 23.1 AsaasTransferData.java

```java
// asaas/dto/AsaasTransferData.java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasTransferData {
    private String id;
    private String status;          // PENDING | DONE | FAILED
    private BigDecimal value;
    private String failReason;      // INVALID_PIX_KEY | INSUFFICIENT_BALANCE | BANK_UNAVAILABLE
    private String pixAddressKey;
    private String pixAddressKeyType;
    private String externalReference; // ← idempotency key de lote: "payout-{partnerId}-{period}"
}
```

### 23.2 AsaasWebhookPayload — campo transfer

```java
// Adicionar ao AsaasWebhookPayload.java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasWebhookPayload {
    private String event;
    private AsaasPaymentData payment;
    private AsaasSubscriptionData subscription;
    private AsaasTransferData transfer;   // ← novo — presente em TRANSFER_COMPLETED / TRANSFER_FAILED
}
```

### 23.3 AsaasListResponse.java (wrapper paginado)

```java
// asaas/dto/AsaasListResponse.java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasListResponse<T> {
    private List<T> data;
    private boolean hasMore;
    private int totalCount;
    private int limit;
    private int offset;
}
```

### 23.4 CommissionRepository — findByPayoutAsaasId

```java
// CommissionRepository.java — adicionar:
Optional<Commission> findByPayoutAsaasId(String payoutAsaasId);
```

### 23.5 AsaasPaymentClient — listReceived

```java
// AsaasPaymentClient.java — adicionar:
@GetMapping("/payments")
AsaasListResponse<AsaasPaymentData> listPayments(
    @RequestParam("status") String status,
    @RequestParam("paymentDate[ge]") String from,
    @RequestParam("paymentDate[le]") String to,
    @RequestParam(value = "limit", defaultValue = "100") int limit,
    @RequestParam(value = "offset", defaultValue = "0") int offset
);
```

`ReconciliationJob.listReceived()` implementado com paginação:

```java
private List<AsaasPaymentData> listReceived(LocalDate date) {
    List<AsaasPaymentData> all = new ArrayList<>();
    String from = date.toString();
    String to   = date.toString();   // paymentDate[ge/le] é inclusivo — mesma data cobre o dia
    int offset  = 0;

    AsaasListResponse<AsaasPaymentData> page;
    do {
        page = asaasPaymentClient.listPayments("RECEIVED", from, to, 100, offset);
        all.addAll(page.getData());
        offset += page.getData().size();
    } while (page.isHasMore());

    return all;
}
```

### 23.6 AsaasSubscriptionClient — cancel

```java
// AsaasSubscriptionClient.java — adicionar:
@DeleteMapping("/subscriptions/{id}")
AsaasDeleteResponse cancel(@PathVariable("id") String subscriptionId);

// AsaasDeleteResponse.java
@Data
public class AsaasDeleteResponse {
    private boolean deleted;
    private String id;
}
```

### 23.7 CANCELAMENTO_SOLICITADO — comportamento no auth service

Tenant em `CANCELAMENTO_SOLICITADO` ainda está pagando — assinatura ativa até o webhook
confirmar. Login deve ser permitido normalmente.

```java
// Auth service — mapeamento de status
boolean ativo = switch (status.getStatus()) {
    case "ATIVO", "TRIAL", "CANCELAMENTO_SOLICITADO" -> true;
    case "SUSPENSO", "CANCELADO"                     -> false;
    default -> false;
};
if (!ativo) throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, ...);
```

---

## 24. Vínculo Retroativo Parceiro → Tenant B2C

Equipe Syax identifica que um tenant B2C foi indicado por um parceiro (informalmente,
fora do fluxo de convite). Admin vincula manualmente após conversão.

### 24.1 Endpoint

```
POST /api/billing/v1/admin/referrals/link
Authorization: ROLE_BILLING_ADMIN

{
  "tenantId": 42,
  "partnerId": 7,
  "reason": "Parceiro identificado após conversão B2C"
}

Response 201:
{
  "referralId": 123,
  "tenantId": 42,
  "partnerId": 7,
  "status": "CONVERTIDO",
  "convertedAt": "2025-02-01T10:00:00Z",
  "message": "Vínculo criado. Comissão calculada a partir da próxima renovação."
}

Response 409: tenant já tem partner_referral ativo
Response 422: tenant não está ATIVO (só faz sentido vincular pós-conversão)
Response 422: parceiro não está ATIVO
```

### 24.2 Lógica

```java
// PartnerReferralAdminService.java
@Transactional
public LinkReferralResponse linkRetroactively(Long tenantId, Long partnerId, String reason) {

    // Validações
    Subscription sub = subscriptionRepo.findByTenantId(tenantId)
        .orElseThrow(() -> new NotFoundException("Tenant sem subscription"));

    if (sub.getStatus() != SubscriptionStatus.ATIVO)
        throw new BusinessException("Vínculo retroativo só permitido para tenants ATIVO");

    referralRepo.findActiveByTenantId(tenantId).ifPresent(existing -> {
        throw new BusinessException("Tenant já tem parceiro vinculado: " + existing.getPartnerId());
    });

    Partner partner = partnerRepo.findById(partnerId)
        .orElseThrow(() -> new NotFoundException("Parceiro não encontrado"));

    if (partner.getStatus() != PartnerStatus.ATIVO)
        throw new BusinessException("Parceiro não está ativo");

    // Criar vínculo já como CONVERTIDO (pulando CONVIDADO e ATIVADO)
    PartnerReferral referral = PartnerReferral.builder()
        .tenantId(tenantId)
        .partnerId(partnerId)
        .status(ReferralStatus.CONVERTIDO)
        .convertedAt(Instant.now())
        .adminNote(reason)
        .build();

    referralRepo.save(referral);

    // Comissão NÃO gerada retroativamente para pagamentos anteriores
    // Começa a ser calculada no próximo PAYMENT_RECEIVED
    log.info("Vínculo retroativo criado — tenant={} parceiro={}", tenantId, partnerId);

    return LinkReferralResponse.from(referral);
}
```

### 24.3 Campo novo em partner_referral

```yaml
- changeSet:
    id: 005-08-partner-referral-admin-note
    author: billing-engine
    changes:
      - addColumn:
          tableName: partner_referral
          schemaName: billing
          columns:
            - column:
                name: admin_note
                type: TEXT
                constraints:
                  nullable: true
    rollback:
      - dropColumn:
          tableName: partner_referral
          schemaName: billing
          columnName: admin_note
```

### 24.4 Impacto no CommissionEngine

Nenhuma mudança necessária. Na próxima renovação, `PAYMENT_RECEIVED` chega,
`CommissionEngine.generateAsync()` consulta `partner_referral` por `tenantId`,
encontra o vínculo criado retroativamente e gera a comissão normalmente.


---

## 25. Variáveis de Ambiente Necessárias

```bash
# Asaas
ASAAS_API_KEY=         # token da conta Asaas (sandbox: começa com $aact_)
ASAAS_BASE_URL=        # https://sandbox.asaas.com/api/v3 (dev) | https://api.asaas.com/v3 (prod)
ASAAS_WEBHOOK_TOKEN=   # token configurado em Asaas > Integrações > Webhooks

# Banco
DB_HOST=
DB_NAME=syax
DB_USER=
DB_PASSWORD=

# Redis
REDIS_HOST=
REDIS_PORT=6379
REDIS_PASSWORD=

# Segurança interna (billing <-> auth service)
INTERNAL_SERVICE_TOKEN=  # UUID aleatório, compartilhado entre auth e billing
```

---

## 26. Notas para o Claude Code

- **Não renomear** o auth service — ele já existe e não deve ser tocado além da integração via HTTP
- **Schema `billing`** está em schema separado — toda entity JPA deve usar `@Table(schema = "billing")`
- **Sem FK declarada** entre billing e schema principal — integridade pelo código, não pelo banco (decisão arquitetural já tomada)
- **`asaas_payment_id` em `billing.commission`** é a chave do pagamento RECEBIDO (incoming); `payout_asaas_id` é do pagamento ENVIADO (outgoing) — não confundir
- **Sandbox Asaas**: usar `https://sandbox.asaas.com/api/v3` em desenvolvimento; o painel sandbox está em `https://sandbox.asaas.com`
- **Plano anual**: `cycle = YEARLY`, `value = planValue * 10` (cobra 10 meses, equivale a 2 meses grátis)
- **Lua scripts** devem estar em `src/main/resources/lua/` — carregar via `ClassPathResource`
- Todos os cron jobs devem ser **idempotentes** — re-executar o mesmo cron no mesmo dia deve ser seguro
- O **filtro de idempotência Redis** é a barreira RÁPIDA; o **UNIQUE constraint do banco** é a barreira DEFINITIVA — ambos são necessários


---

## 27. Gaps, Prontidão para Produção e Score

### 27.1 Score — prontidão para produção (atualizado)

> Distinct do score de "completude interna do spec" (95%). Este score mede se o **sistema está pronto para operar em produção real**.

| Categoria | Score | Status | Observação |
|---|---|---|---|
| Lógica de pagamento e comissão | 95% | ✓ | Fluxos, webhooks, payout, commission engine |
| Resiliência e idempotência | 95% | ✓ | TransientException + release; WebhookRecoveryJob; check-then-act em transfers; 3 camadas de recuperação (seção 28) |
| Completude de fluxos | 95% | ✓ | Mudança de plano (27.6) e dunning completo (27.7) spec'd |
| Segurança | 90% | ✓ | Asaas usa API key (HMAC-SHA256 não suportado — verificado); token blacklist é do auth service, fora de escopo |
| Operacional | 88% | ✓ | Crons em UTC; alerta de fila Asaas pausada; métricas Prometheus a definir em produção |
| Legal / Compliance | 75% | ⚠ | LGPD spec'd pós-MVP (27.2); NFS-e fechado (Asaas emite); contrato parceiro pendente validação jurídica |
| **Prontidão para produção** | **91%** | | |

> Os 9% restantes: métricas de observabilidade (definem-se com o sistema rodando), contrato do parceiro (jurídico, não código) e ajustes finos do sandbox Asaas. Nenhum item bloqueia o início da implementação em 10-Jun.

---

### 27.2 LGPD — status e requisitos técnicos (PARCIAL)

**Base jurídica:** Doc de Riscos Jurídicos v1, Seção 2.1. Papéis definidos:
- **Controlador**: o cliente (empresa que usa o ERP) — decide quais dados coletar
- **Operador**: Syax — apenas processa sob as ordens do cliente

**O que já está coberto:**
- Isolamento de dados por `tenant_id` / schema separation = Privacy by Design (argumento jurídico de conformidade desde a concepção)
- Audit log no `financeiro.audit_log` com retenção de 5 anos (atende guarda fiscal)
- Sem FK cruzada entre schemas = tenants não conseguem ver dados uns dos outros

**O que ainda falta implementar — decisões tomadas, implementação pós-MVP:**

**1. Exclusão de dados (botão no painel admin)**

- Não deleta o tenant — **anonimiza** todos os PII em cascata por todos os schemas
- Mantém registros financeiros intactos (commission, subscription.value) — prazo legal 5 anos + ano corrente
- Campos afetados no billing schema: `partner.nome`, `partner.email`, `partner.telefone`, `partner.pix_key`, `partner.bank_*` → substituídos por `"[REMOVIDO]"`
- Registra no `audit_log` com timestamp e operador
- Bloqueia novas assinaturas para o `tenant_id`
- **Pré-requisito:** toda a implementação do sistema estar no ar — é feature de fase final, não MVP

```
DELETE /api/billing/v1/admin/tenants/{tenantId}/personal-data
ROLE_BILLING_ADMIN

Response 200: { tenantId, anonymizedAt, fieldsAffected: [...] }
```

```
Migration: 005-09-lgpd-partner-fields
Campos novos em billing.partner:
  lgpd_consent_at              TIMESTAMPTZ NULL
  lgpd_deletion_requested_at   TIMESTAMPTZ NULL
  lgpd_deleted_at              TIMESTAMPTZ NULL
```

**2. Exportação de dados (menu para o próprio tenant)**

- Tenant acessa menu de exportação no SPA e baixa todos os dados que lhe pertencem
- Atende direito de portabilidade da LGPD (Art. 18, V)
- Escopo do billing service: subscription history, commission history (se parceiro), invoice list
- Formato: JSON ou CSV, gerado assincronamente, link de download por e-mail
- **Pré-requisito:** igual ao acima — feature de fase final

```
POST /api/billing/v1/tenants/{tenantId}/export
ROLE_TENANT (próprio tenant autenticado)

Response 202: { jobId, estimatedReady: "..." }
→ quando pronto: e-mail com link de download (TTL 24h)
```

**3. Incidente de segurança (Art. 48 LGPD)**

- Prazo: 48-72h para notificar ANPD e titulares afetados
- Implementação: plano de resposta a incidentes global do ERP — não é endpoint do billing service
- Doc de riscos jurídicos seção 2.1 detalha o processo

**DPA (Data Processing Agreement):** documento jurídico separado, formaliza Syax como Operador. Doc de riscos mapeia o conteúdo (Seção 2.9). Não é código.

---

### 27.3 NF-e / NFS-e — status (FECHADO para o billing service)

**Base jurídica:** Doc de Riscos Jurídicos v1, Seção 2.3.

**Decisão:** O Asaas emite NFS-e automaticamente para cada cobrança liquidada — configuração feita uma vez no painel Asaas. O billing service não precisa implementar nada além do que já faz.

**Dois contextos separados — não confundir:**

| Contexto | Emissor | Quem paga | Quem recebe a nota |
|---|---|---|---|
| Assinatura SaaS Syax | **Asaas** (automático) | Tenant | Tenant ← nota da Syax |
| Módulo fiscal do ERP | **Emissor próprio Syax** (microserviço interno) | Clientes do tenant | Clientes do tenant ← nota do tenant |

**Responsabilidade solidária (Lei 8.137/90):** já atendido pelo modelo imutável do `commission` e pelo `audit_log` — sem edição de XMLs autorizados nem exclusão sem rastro.

**Impacto no billing service:** zero. Nenhum campo novo, nenhuma migration, nenhuma integração. `PAYMENT_RECEIVED` chega → tenant ativado → Asaas já emitiu a nota.

**O emissor próprio** é um microserviço separado consumido pelo módulo fiscal do ERP — fora do escopo deste spec.

---

### 27.4 Outros gaps mapeados (ordem de prioridade)

| Gap | Impacto | Próximo passo |
|---|---|---|
| Write-through Redis (vs eviction) | IMPORTANTE | Implementar certo na primeira vez (início 10-Jun). `PaymentReceivedHandler`: usar `put(tenantId, ATIVO, TTL)` em vez de `evict()`. Handler já conhece o novo valor — não há motivo para descartar e forçar round-trip ao banco. |
| Mudança de plano MONTHLY ↔ ANNUAL | **FAZER** | Ver seção 27.6 — recálculo de valor, substituição da assinatura Asaas e pró-rata definidos. |
| Dunning detalhado | MELHORIA | Definir quantas retentativas o Asaas faz antes de `SUBSCRIPTION_INACTIVATED` e qual e-mail o tenant recebe em cada etapa |
| HMAC webhook Asaas | IMPORTANTE | Verificar docs Asaas: se suportado, adicionar validação de assinatura no `WebhookSecurityService` |
| Token blacklist (logout forçado) | IMPORTANTE | **Auth service — não tem relação com billing.** Doc jurídico seção 6.11: JWT stateless com TTL 1h deixa ex-funcionário de tenant ativo após demissão. Implementar Redis blacklist no auth service. O billing service não é afetado. |
| Commission async DLQ | MELHORIA | `generateAsync()` falha silenciosa = comissão perdida. Adicionar retry + `consumer_error_log` |
| Payout escalation | MELHORIA | Após N `TRANSFER_FAILED` consecutivos para mesmo parceiro, marcar `BLOQUEADO` e notificar admin em vez de apenas reverter para `PENDENTE` |
| Observabilidade | MELHORIA | Definir métricas Prometheus: webhook failure rate, payout success rate, commission pending total, MRR |

---

### 27.5 Decisões de timezone confirmadas

| Job | Horário | Timezone | Observação |
|---|---|---|---|
| `TrialScheduler` D+10 | 08:00 | UTC | 05:00 BRT |
| `TrialScheduler` D+15 | 08:05 | UTC | 05:05 BRT |
| `CommissionPayoutJob` | 02:00 | UTC | 23:00 BRT (dia anterior) |
| `ReconciliationJob` | 02:30 | UTC | 23:30 BRT |
| `DunningJob` | 00/06/12/18h | UTC | 21h/03h/09h/15h BRT |

> Grace period (5 dias) e todos os cálculos de `D+N` são em **dias corridos** (não dias úteis). Feriados brasileiros não afetam a contagem.

---

### 27.6 Mudança de plano — MONTHLY ↔ ANNUAL (FAZER)

Este fluxo **vai ser implementado** — não é melhoria futura. Requer recálculo de valor, substituição da assinatura no Asaas e tratamento de pró-rata.

#### Endpoint

```
PATCH /api/billing/v1/subscriptions/{tenantId}/plan
ROLE_TENANT (próprio tenant) ou ROLE_BILLING_ADMIN

Body: { "newBillingCycle": "ANNUAL" | "MONTHLY" }

Response 200: { tenantId, oldCycle, newCycle, oldValue, newValue, asaasNewSubscriptionId, effectiveAt }
Response 422: tenant não está ATIVO
Response 422: mesmo cycle que o atual
```

#### Recálculo de valor

```java
BigDecimal newValue = switch (newBillingCycle) {
    case ANNUAL   -> plan.getMonthlyValue().multiply(BigDecimal.TEN); // 10 meses
    case MONTHLY  -> plan.getMonthlyValue();
};
subscription.setValue(newValue);
subscription.setBillingCycle(newBillingCycle);
```

#### Fluxo no Asaas

O Asaas não suporta alteração de ciclo em assinatura existente — é necessário cancelar e recriar:

```
1. DELETE /v3/subscriptions/{asaasSubscriptionId}   ← cancela atual
2. POST  /v3/subscriptions                          ← cria nova com novo cycle e value
3. subscription.asaasSubscriptionId = novo id
4. subscription.nextDueDate = data da próxima cobrança
```

#### Pró-rata

| Direção | Comportamento |
|---|---|
| MONTHLY → ANNUAL | Tenant paga o anual completo na próxima cobrança. Dias restantes do mês atual: **sem crédito** — política definida em contrato (simplifica implementação e alinha com no-refund policy) |
| ANNUAL → MONTHLY | Assinatura anual cancelada. Meses não utilizados: **sem reembolso** — mesma política. Tenant começa a ser cobrado mensalmente a partir do próximo ciclo |

> **Decisão de no-refund:** alinhada com a política do plano anual já definida no contrato SaaS. Simplifica o fluxo e evita lógica de pró-rata complexa no Asaas.

#### Impacto na comissão do parceiro

- Modelo RECORRENTE (atual): comissão calculada sobre `subscription.value / 12` se anual, ou `subscription.value` se mensal — já implementado no `RecurrentCommissionStrategy`
- Na mudança de plano, o próximo `PAYMENT_RECEIVED` usará o novo `subscription.value` automaticamente — **sem código adicional no commission engine**

#### Eventos gerados

```
1. SUBSCRIPTION_INACTIVATED (Asaas cancela assinatura antiga) → handler existente executa
   ⚠ Cuidado: o handler atual marca tenant como CANCELADO ao receber SUBSCRIPTION_INACTIVATED
   Solução: adicionar flag `pendingPlanChange = true` em billing.subscription antes de cancelar
   O handler verifica essa flag e, se true, apenas aguarda — não cancela o tenant

2. PAYMENT_RECEIVED (Asaas confirma primeiro pagamento da nova assinatura) → handler existente ativa tenant
```

#### Flag de controle

```sql
-- Migration: 005-10-subscription-plan-change-flag
ALTER TABLE billing.subscription
  ADD COLUMN pending_plan_change BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN plan_change_requested_at TIMESTAMPTZ NULL;
```

```java
// PlanChangeService.java
@Transactional
public PlanChangeResponse changePlan(Long tenantId, BillingCycle newCycle) {
    Subscription sub = subscriptionRepo.findByTenantId(tenantId).orElseThrow();
    
    if (sub.getStatus() != SubscriptionStatus.ATIVO)
        throw new BusinessException("Mudança de plano só permitida para tenant ATIVO");
    if (sub.getBillingCycle() == newCycle)
        throw new BusinessException("Tenant já está no ciclo solicitado");

    BigDecimal newValue = newCycle == BillingCycle.ANNUAL
        ? planRepo.findById(sub.getPlanId()).get().getMonthlyValue().multiply(BigDecimal.TEN)
        : planRepo.findById(sub.getPlanId()).get().getMonthlyValue();

    // 1. Flag para proteger o handler de SUBSCRIPTION_INACTIVATED
    sub.setPendingPlanChange(true);
    sub.setPlanChangeRequestedAt(Instant.now());
    subscriptionRepo.save(sub);

    // 2. Cancela assinatura antiga no Asaas
    asaasSubscriptionClient.cancel(sub.getAsaasSubscriptionId());

    // 3. Cria nova assinatura no Asaas
    AsaasSubscriptionResponse newSub = asaasSubscriptionClient.create(
        buildRequest(sub.getAsaasCustomerId(), newCycle, newValue)
    );

    // 4. Atualiza subscription local
    sub.setBillingCycle(newCycle);
    sub.setValue(newValue);
    sub.setAsaasSubscriptionId(newSub.getId());
    sub.setNextDueDate(newSub.getNextDueDate());
    sub.setPendingPlanChange(false);
    subscriptionRepo.save(sub);

    // 5. Invalida cache
    tenantStatusCache.put(tenantId, sub.getStatus());

    return PlanChangeResponse.from(sub);
}
```

#### Atualização necessária no SubscriptionInactivatedHandler

```java
// SubscriptionInactivatedHandler.java — adicionar verificação no início
if (subscription.isPendingPlanChange()) {
    log.info("SUBSCRIPTION_INACTIVATED ignorado — mudança de plano em andamento. tenantId={}", tenantId);
    webhookIdempotency.markDone(eventId);
    return; // não cancela o tenant
}
// ... lógica existente de cancelamento
```





---

### 27.7 Dunning — fluxo de inadimplência redesenhado

> **Princípio central:** o relógio é da Syax, não do Asaas. Timestamps absolutos gravados no momento do `PAYMENT_OVERDUE`. Webhooks do Asaas são insumo — nunca comandam transições de estado diretamente.

---

#### 27.7.1 Campos novos em billing.subscription

```yaml
# Migration: 005-11-dunning-absolute-timestamps
- addColumn:
    tableName: subscription
    schemaName: billing
    columns:
      - column:
          name: suspend_at
          type: TIMESTAMPTZ
          constraints:
            nullable: true
      - column:
          name: cancel_at
          type: TIMESTAMPTZ
          constraints:
            nullable: true
      - column:
          name: reminder_sent_at
          type: TIMESTAMPTZ
          constraints:
            nullable: true
      - column:
          name: asaas_inactivated_at
          type: TIMESTAMPTZ
          constraints:
            nullable: true
```

> **`grace_period_expires_at` existente → substituído por `suspend_at`.** Manter o campo antigo por compatibilidade mas usar `suspend_at` em toda lógica nova.

---

#### 27.7.2 Configuração — prazos não hardcoded

```yaml
# application.yml
billing:
  dunning:
    grace-period-days: 5        # PAYMENT_OVERDUE → suspend_at
    cancel-after-days: 7        # PAYMENT_OVERDUE → cancel_at
    reminder-before-days: 2     # envia email 2 quando suspend_at - 2d <= now
```

---

#### 27.7.3 PaymentOverdueHandler — seta timestamps absolutos

```java
@Override
public void handle(AsaasWebhookPayload payload) {
    Subscription sub = subscriptionRepo.findByAsaasSubscriptionId(payload.getSubscription().getId())
        .orElseThrow(() -> new NotFoundException("Subscription não encontrada"));

    Instant now = Instant.now();

    sub.setSuspendAt(now.plus(dunningProps.getGracePeriodDays(), ChronoUnit.DAYS));
    sub.setCancelAt(now.plus(dunningProps.getCancelAfterDays(), ChronoUnit.DAYS));
    sub.setGracePeriodExpiresAt(sub.getSuspendAt()); // compatibilidade
    sub.setReminderSentAt(null); // reset caso fosse reativado antes

    subscriptionRepo.save(sub);

    // Email 1 — aviso imediato
    emailService.sendPaymentOverdueNotice(sub.getTenantId(), sub.getSuspendAt());

    log.info("Dunning iniciado tenantId={} suspendAt={} cancelAt={}",
        sub.getTenantId(), sub.getSuspendAt(), sub.getCancelAt());
}
```

---

#### 27.7.4 DunningJob — job único, roda 4× por dia

Substitui o `GracePeriodSuspensionJob`. Um único job que verifica o que cada subscription precisa.

```java
@Component
public class DunningJob {

    // 00:00, 06:00, 12:00, 18:00 UTC
    @Scheduled(cron = "0 0 0,6,12,18 * * *")
    public void run() {
        if (!distributedLock.acquire("dunning-job")) return;
        try {
            processReminders();
            processSuspensions();
            processCancellations();
        } finally {
            distributedLock.release("dunning-job");
        }
    }

    // Email 2 — lembrete 2 dias antes de suspender
    private void processReminders() {
        Instant threshold = Instant.now().plus(dunningProps.getReminderBeforeDays(), DAYS);
        List<Subscription> subs = subscriptionRepo
            .findByStatusAndSuspendAtBeforeAndReminderSentAtIsNull(ATIVO, threshold);

        subs.forEach(sub -> {
            emailService.sendSuspensionReminderNotice(sub.getTenantId(), sub.getSuspendAt());
            sub.setReminderSentAt(Instant.now());
            subscriptionRepo.save(sub);
        });
    }

    // Email 3 — suspende quando suspend_at <= now
    private void processSuspensions() {
        List<Subscription> subs = subscriptionRepo
            .findByStatusAndSuspendAtBefore(ATIVO, Instant.now());

        subs.forEach(sub -> {
            sub.setStatus(SubscriptionStatus.SUSPENSO);
            sub.setSuspendedAt(Instant.now());
            subscriptionRepo.save(sub);
            tenantStatusCache.put(sub.getTenantId(), SubscriptionStatus.SUSPENSO);
            emailService.sendAccountSuspendedNotice(sub.getTenantId());
        });
    }

    // Email 4 — cancela quando cancel_at <= now
    private void processCancellations() {
        List<Subscription> subs = subscriptionRepo
            .findByStatusAndCancelAtBefore(SUSPENSO, Instant.now());

        subs.forEach(sub -> {
            sub.setStatus(SubscriptionStatus.CANCELADO);
            sub.setCancelledAt(Instant.now());
            sub.setCancellationReason("Inadimplência — cancel_at atingido");
            subscriptionRepo.save(sub);
            tenantStatusCache.put(sub.getTenantId(), SubscriptionStatus.CANCELADO);
            // Cancela comissões PENDENTE do parceiro
            commissionRepo.cancelPendingByTenantId(sub.getTenantId());
            emailService.sendSubscriptionCancelledNotice(sub.getTenantId());
        });
    }
}
```

---

#### 27.7.5 SubscriptionInactivatedHandler — apenas metadado

`SUBSCRIPTION_INACTIVATED` não comanda mais nenhuma transição de estado. Apenas registra que o Asaas desistiu.

```java
@Override
public void handle(AsaasWebhookPayload payload) {
    Subscription sub = subscriptionRepo
        .findByAsaasSubscriptionId(payload.getSubscription().getId())
        .orElseThrow();

    // Grava quando o Asaas desistiu — o DunningJob usa cancel_at, não isso
    sub.setAsaasInactivatedAt(Instant.now());
    subscriptionRepo.save(sub);

    log.info("Asaas inativou assinatura — DunningJob controla o cancelamento. tenantId={}",
        sub.getTenantId());

    // Se tenant já está CANCELADO (DunningJob chegou primeiro), apenas loga
    if (sub.getStatus() == SubscriptionStatus.CANCELADO) {
        log.info("Tenant já cancelado pelo DunningJob — evento ignorado.");
    }
}
```

---

#### 27.7.6 PaymentReceivedHandler — caso CANCELADO

Tenant cancela, paga depois (boleto/PIX com atraso). Admin decide.

```java
// Adicionar ao PaymentReceivedHandler antes da lógica de ativação
if (sub.getStatus() == SubscriptionStatus.CANCELADO) {
    // Não reativa automaticamente — admin decide
    adminNotificationService.notifyPaymentAfterCancellation(
        sub.getTenantId(),
        payload.getPayment().getValue(),
        payload.getPayment().getId()
    );
    log.warn("PAYMENT_RECEIVED para tenant CANCELADO — aguardando decisão admin. tenantId={}",
        sub.getTenantId());
    return; // não processa mais nada
}
```

**Admin vê no painel:**
```
⚠ Tenant X pagou R$ 990,00 após cancelamento (asaas_payment_id: pay_xxx).
Decisão necessária: [Reativar manualmente] [Solicitar estorno]
```

Endpoint para reativação manual:
```
POST /api/billing/v1/admin/tenants/{tenantId}/reactivate
Body: { "asaasPaymentId": "pay_xxx", "reason": "..." }
ROLE_BILLING_ADMIN
```

---

#### 27.7.7 Suspensão — bloqueia escrita, mantém leitura

**Decisão:** tenant SUSPENSO pode ler e exportar dados (obrigações fiscais em curso), mas não pode executar operações de escrita.

O check é feito no **middleware da API Gateway / Auth Service**, não só no login:

```java
// JwtClaimsEnricher — adicionar claim ao JWT
claims.put("subscription_status", billingService.getStatus(tenantId));

// WriteAccessFilter — filtro em cada serviço do ERP
if (subscriptionStatus == SUSPENSO && isWriteOperation(request)) {
    return ResponseEntity.status(402)
        .body(new ErrorResponse("Conta suspensa. Leitura permitida. " +
                                "Regularize o pagamento para retomar operações."));
}
```

```
Operações bloqueadas em SUSPENSO:  POST · PUT · PATCH · DELETE
Operações permitidas em SUSPENSO:  GET · exportações · download de XMLs
```

---

#### 27.7.8 Sequência de e-mails

| Momento | Gatilho | E-mail | Destinatário |
|---|---|---|---|
| `PAYMENT_OVERDUE` | Handler imediato | "Pagamento não aprovado — 5 dias para regularizar" | Tenant |
| `suspend_at - 2d` | DunningJob · processReminders | "Sua conta suspende em 2 dias" | Tenant |
| `suspend_at` | DunningJob · processSuspensions | "Conta suspensa — regularize para retomar" | Tenant |
| `cancel_at` | DunningJob · processCancellations | "Assinatura cancelada" | Tenant |
| `PAYMENT_RECEIVED` pós-CANCELADO | Handler imediato | Alerta de pagamento tardio | Admin |

---

#### 27.7.9 Fluxo completo

```
PAYMENT_OVERDUE
  → suspend_at = now + 5d
  → cancel_at  = now + 7d
  → Email 1: "5 dias para regularizar"

DunningJob (4× ao dia)
  → suspend_at - 2d atingido → Email 2: "suspende em 2 dias"
  → suspend_at atingido      → SUSPENSO · Email 3 · leitura OK, escrita bloqueada
  → cancel_at atingido       → CANCELADO · Email 4 · comissões PENDENTE canceladas

SUBSCRIPTION_INACTIVATED (Asaas — qualquer momento)
  → Grava asaas_inactivated_at · não muda status · DunningJob controla tudo

PAYMENT_RECEIVED (qualquer momento antes de CANCELADO)
  → ATIVO · cache atualizado · dunning zerado (suspend_at e cancel_at = null)

PAYMENT_RECEIVED (após CANCELADO)
  → Não reativa · alerta admin no painel · admin decide: reativar ou estornar
```

---

#### 27.7.10 Itens que entram com o dunning

| Item | Tipo |
|---|---|
| Migration `005-11` — 4 campos novos em subscription | Migration |
| `DunningJob` — substitui `GracePeriodSuspensionJob` | Novo cron |
| `PaymentOverdueHandler` — seta timestamps absolutos | Handler atualizado |
| `SubscriptionInactivatedHandler` — apenas metadado | Handler simplificado |
| `PaymentReceivedHandler` — caso CANCELADO | Handler atualizado |
| `POST /admin/tenants/{id}/reactivate` | Novo endpoint admin |
| `WriteAccessFilter` — bloqueia escrita em SUSPENSO | Middleware ERP |
| `billing.dunning.*` em application.yml | Configuração |



---

## 28. Hardening — Melhores Práticas de Mercado (revisão pré-implementação)

> Revisão final aplicada antes do início da implementação (10-Jun). Cada item abaixo é padrão de mercado em sistemas de pagamento (Stripe, Adyen, gateways brasileiros). Os fixes 28.1–28.3 **já foram aplicados** no código das seções anteriores; 28.4–28.8 são adições novas.

---

### 28.1 ✅ APLICADO — Erro transitório vs permanente no webhook

**Problema:** o processor marcava `ERROR` no Redis em qualquer exceção. A chave de idempotência permanecia — e as 5 retentativas do Asaas eram descartadas como "duplicatas". Um hiccup de banco de 2 segundos = evento perdido até a reconciliação (que roda 1× ao dia).

**Fix aplicado:** duas vias de erro:
- `TransientException` (DB down, timeout, deadlock) → `idempotencyService.release()` apaga a chave → retentativa do Asaas reprocessa
- Exceção permanente (payload inválido, regra violada) → mantém a chave + alerta admin

```java
// TransientException.java — wrappear erros de infra
public class TransientException extends RuntimeException {
    public TransientException(String msg, Throwable cause) { super(msg, cause); }
}

// Nos handlers: capturar e reclassificar
try {
    subscriptionRepo.save(sub);
} catch (DataAccessResourceFailureException | QueryTimeoutException
       | CannotAcquireLockException e) {
    throw new TransientException("Falha de infra ao persistir", e);
}
```

---

### 28.2 ✅ APLICADO — `event.id` do Asaas como chave de idempotência

**Problema:** a chave era `{eventType}:{paymentId}`. O Asaas envia um `id` único por entrega de webhook — esse é o identificador correto. Usar `paymentId` funciona para o fluxo normal mas colide em cenários de reenvio manual via painel Asaas.

**Fix aplicado:** `payload.getId()` como chave primária de idempotência, fallback para `paymentId` se ausente. Campo `id` adicionado ao `AsaasWebhookPayload`.

---

### 28.3 ✅ APLICADO — PaymentReceivedHandler corrigido (3 itens)

1. **Write-through em vez de evict** — handler conhece o novo status; popular o cache diretamente elimina a race condition com read replica lag
2. **`nextDueDate` vem do Asaas** — `plusDays(30)` deriva (meses têm 28–31 dias) e `plusDays(365)` quebra em bissexto. O Asaas calcula a próxima cobrança; consultar e usar
3. **Guard de estado** — `PAYMENT_RECEIVED` com tenant `CANCELADO` não reativa (alerta admin, conforme 27.7.6)

---

### 28.4 🆕 Money-out: NUNCA retry automático em POST /transfers

**Problema crítico:** o `@Retry` do Resilience4j estava aplicado em todas as chamadas Asaas — incluindo `POST /transfers`. Cenário de double-payment:

```
1. POST /transfers (R$ 990 para o parceiro)
2. Timeout de rede — mas o Asaas PROCESSOU a transferência
3. @Retry dispara nova chamada
4. Parceiro recebe R$ 1.980
```

**Regra de mercado:** retry automático apenas em operações idempotentes (GET) ou de criação verificável. Para money-out: **check-then-act**.

```java
// CommissionPayoutService.java — fluxo seguro (transfer AGREGADO por parceiro por período)
// A chave de idempotência é por LOTE, não por comissão individual:
// externalReference = "payout-{partnerId}-{period}"
// Isso garante um único PIX por parceiro por período, mesmo em retentativas.
public TransferResult sendTransfer(Partner partner, List<Commission> commissions, YearMonth period) {
    // Chave de idempotência de lote: um transfer por parceiro por período
    String externalRef = "payout-" + partner.getId() + "-" + period;

    BigDecimal total = commissions.stream()
        .map(this::effectiveAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    String description = String.format("Comissão Syax — Referência %s — Parceiro %d", period, partner.getId());

    try {
        AsaasTransferResponse resp = asaasTransferClient.create(
            AsaasTransferRequest.builder()
                .value(total)
                .pixAddressKey(partner.getPixKey())
                .pixAddressKeyType(partner.getPixKeyType())
                .description(description)
                .externalReference(externalRef)   // ← idempotency key de lote
                .build());
        return TransferResult.sent(resp.getId());

    } catch (FeignException.GatewayTimeout | RetryableException e) {
        // TIMEOUT: NÃO retentar às cegas. Consultar se a transfer existe por externalReference.
        // NUNCA usar @Retry em POST /transfers — risco de pagar o parceiro em dobro.
        Optional<AsaasTransferData> existing =
            asaasTransferClient.findByExternalReference(externalRef)
                .getData().stream().findFirst();

        if (existing.isPresent()) {
            // Transfer FOI criada — usar o id retornado, não reenviar
            log.warn("Transfer já existia após timeout — parceiro={} period={} transferId={}",
                partner.getId(), period, existing.get().getId());
            return TransferResult.sent(existing.get().getId());
        }
        // Não existe — seguro marcar para retry no próximo ciclo D+1
        return TransferResult.failed("timeout — não criada no Asaas");
    }
}
```

```java
// AsaasTransferClient.java — SEM @Retry no create
@CircuitBreaker(name = "asaas-api")  // circuit breaker SIM, retry NÃO
@PostMapping("/transfers")
AsaasTransferResponse create(@RequestBody AsaasTransferRequest req);

@GetMapping("/transfers?externalReference={ref}")
AsaasListResponse<AsaasTransferData> listByExternalReference(@PathVariable("ref") String ref);
```

> `@Retry` permanece válido para: GET (qualquer), POST /customers e POST /subscriptions (duplicata é detectável e corrigível). **Nunca** para /transfers.

---

### 28.5 🆕 Inbox recovery — webhook persistido antes de processado

**Problema:** o controller retorna 200 e processa via `@Async` (fila em memória). Se o pod morrer entre o 200 e o processamento, o evento é perdido — o Asaas não retenta (recebeu 200).

**Fix:** o `webhook_log` (status `RECEBIDO`) já é gravado ANTES do 200. Adicionar:

1. **UNIQUE constraint** no event id do log:

```yaml
# Migration: 005-12-webhook-log-event-id-unique
- addColumn:
    tableName: webhook_log
    schemaName: billing
    columns:
      - column: { name: asaas_event_id, type: "VARCHAR(64)" }
- addUniqueConstraint:
    tableName: webhook_log
    schemaName: billing
    columnNames: asaas_event_id
    constraintName: uq_webhook_log_event_id
```

2. **WebhookRecoveryJob** — reprocessa eventos presos:

```java
@Component
public class WebhookRecoveryJob {

    // A cada 10 minutos
    @Scheduled(fixedDelay = 600_000)
    public void recoverStuck() {
        if (!lockService.acquire("webhook-recovery", instanceId, 300)) return;
        try {
            // RECEBIDO há mais de 10 min = processamento morreu no meio
            List<WebhookLog> stuck = webhookLogRepo
                .findByStatusAndReceivedAtBefore("RECEBIDO",
                    Instant.now().minus(10, ChronoUnit.MINUTES));

            stuck.forEach(log -> {
                AsaasWebhookPayload payload = parsePayload(log.getPayload());
                processor.processAsync(payload); // idempotência protege contra dupla execução
            });
        } finally {
            lockService.release("webhook-recovery", instanceId);
        }
    }
}
```

> Com isso o sistema tem **3 camadas de recuperação**: retentativas do Asaas (minutos), WebhookRecoveryJob (10 min), ReconciliationJob (diário).

> **Conexão com o controller (seção 8.1):** a constraint UNIQUE em `webhook_log.asaas_event_id` criada por esta migration é exatamente o que faz o `logReceived` precisar de tratamento de conflito. Na retentativa do Asaas, o INSERT vai falhar com `DataIntegrityViolationException`. O `WebhookLogService.logReceived` DEVE usar `INSERT ... ON CONFLICT (asaas_event_id) DO NOTHING` (query nativa) ou capturar a exceção e seguir silenciosamente — nunca deixar a exceção vazar para o controller. Ver callout na seção 8.1 para o contexto completo.

---

### 28.6 🆕 Guards de máquina de estado — eventos fora de ordem

Webhooks podem chegar fora de ordem (rede, retentativas). Cada handler valida o estado atual antes de transicionar:

| Evento chegando | Estado atual | Ação |
|---|---|---|
| PAYMENT_RECEIVED | TRIAL / SUSPENSO | Ativa (fluxo normal) |
| PAYMENT_RECEIVED | ATIVO | Renovação — atualiza nextDueDate, zera dunning |
| PAYMENT_RECEIVED | CANCELADO | NÃO reativa — alerta admin (27.7.6) |
| PAYMENT_OVERDUE | ATIVO | Inicia dunning (suspend_at/cancel_at) |
| PAYMENT_OVERDUE | SUSPENSO / CANCELADO | Ignora — dunning já em curso ou encerrado |
| PAYMENT_OVERDUE | TRIAL | Ignora + log warn (não deveria acontecer) |
| SUBSCRIPTION_INACTIVATED | qualquer | Apenas metadado (asaas_inactivated_at) — 27.7.5 |
| TRANSFER_COMPLETED | EM_TRANSFERENCIA | PAGO (fluxo normal) |
| TRANSFER_COMPLETED | PAGO | Ignora (duplicata pós-TTL) |
| TRANSFER_FAILED | PAGO | Alerta admin — inconsistência grave, investigar |

```java
// Exemplo — PaymentOverdueHandler com guard
if (sub.getStatus() != SubscriptionStatus.ATIVO) {
    log.info("PAYMENT_OVERDUE ignorado — tenant {} em estado {}",
        sub.getTenantId(), sub.getStatus());
    return;
}
```

---

### 28.7 🆕 Comportamento da fila de webhooks do Asaas — nota operacional

O Asaas usa **fila sequencial de webhooks**: se o endpoint responder erro (não-2xx) repetidamente, o Asaas **pausa a fila inteira** — nenhum evento de nenhum tenant é entregue até reativação manual no painel.

**Implicações:**
- O controller DEVE retornar 200 mesmo quando o processamento falhar (já faz — processa async)
- 401 só para token realmente inválido (ataque), nunca para erro interno
- **Alerta de monitoramento obrigatório:** se nenhum webhook chegar em 6h úteis, notificar admin — a fila pode estar pausada
- Runbook: reativar fila em Asaas → Configurações → Webhooks → "Fila de sincronização"

---

### 28.8 🆕 Validação de valor no PAYMENT_RECEIVED

Defesa contra inconsistência (mudança de plano no meio do ciclo, cobrança parcial, erro do gateway):

```java
// PaymentReceivedHandler — após carregar subscription
BigDecimal expected = sub.getValue();
BigDecimal received = payload.getPayment().getValue();

if (received.compareTo(expected) != 0) {
    // Não bloqueia a ativação — tenant pagou. Mas alerta para investigação.
    log.warn("Valor divergente — esperado={} recebido={} tenant={}",
        expected, received, sub.getTenantId());
    adminNotificationService.notifyValueMismatch(sub.getTenantId(), expected, received);
}
```

> Política: divergência **não bloqueia** ativação (o tenant pagou algo — pior UX seria bloquear). Admin investiga via alerta. Comissão é calculada sobre o **valor recebido**, não o esperado.

---

### 28.9 Itens novos desta revisão

| Item | Tipo | Migration |
|---|---|---|
| `TransientException` + release de chave | Classe + fluxo de erro | — |
| `event.id` como chave de idempotência | Campo DTO + processor | — |
| Write-through + guard + nextDueDate no PaymentReceivedHandler | Handler (aplicado) | — |
| Check-then-act em POST /transfers + externalReference | PayoutService | — |
| `WebhookRecoveryJob` (10 min) | Novo cron | — |
| UNIQUE em `webhook_log.asaas_event_id` | Constraint | 005-12 |
| Guards de estado em todos os handlers | Handlers | — |
| Alerta de fila Asaas pausada (sem webhook em 6h) | Monitoramento | — |
| Validação de valor no PAYMENT_RECEIVED | Handler | — |