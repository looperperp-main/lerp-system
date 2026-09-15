# Roteiro de Teste — Fase 3 (Asaas client + criação de assinatura)

Passo a passo para testar localmente a criação de assinatura (checkout) e a ativação via webhook
`PAYMENT_RECEIVED`, incluindo o `nextDueDate` real buscado no Asaas.

---

## ✅ RESULTADOS (2026-06-23)

**Checkout (passo G) — VERIFICADO de ponta a ponta.** `POST /api/v1/checkout` (planType `MENSAL`)
retornou **HTTP 200** com boleto real do sandbox:
```json
{"paymentUrl":"https://sandbox.asaas.com/i/5io2sl2ndb7y2j18",
 "boletoUrl":"https://sandbox.asaas.com/b/pdf/5io2sl2ndb7y2j18",
 "pixQrCode":null,"pixCopyPaste":null,"dueDate":"2026-06-24",
 "planType":"MENSAL","planName":"Plano Mensal Básico","value":179.00}
```
- Cliente + assinatura criados no Asaas; `AsaasGateway` (RestClient + circuit breaker) funcionando.
- `pixQrCode`/`pixCopyPaste` vêm `null` — esperado com `billingType=UNDEFINED` (o PIX só é gerado
  quando o pagador escolhe); o código trata sem quebrar.

**Webhook PAYMENT_RECEIVED (passo H) — VERIFICADO de ponta a ponta.** Com `sub_ftag1525etlzlh7d`:
- `POST /api/v1/webhooks/asaas` → **200** imediato (ack).
- subscription → status **`ATIVA`**, `next_due_date=2026-07-24` (**buscado do Asaas**, não calculado), `activated_at` setado.
- `webhook_log` → **`PROCESSADO`** (sem erro).
- Kafka `billing.subscription.activated` publicado: `{planType:MENSAL, tenantId:1, asaasSubscriptionId:sub_ftag..., value:179.00, asaasPaymentId:pay_teste_001}`.
- Token do header = valor de `ASAAS_WEBHOOK_TOKEN` (NÃO a API key); painel/ngrok desnecessários na Opção 2.

**Caminho negativo (404) — VERIFICADO:** planType inexistente → 404 com `StandardError` e mensagem.

**Bugs encontrados e corrigidos durante o teste (todos verificados):**
1. Boot quebrava por ambiguidade de bean `RedisTemplate<String,String>` → `@Primary` no `redisTemplate` do `RedisConfig`.
2. Corpo de erro genérico sem mensagem → `@SpringBootApplication(scanBasePackages="com.l.erp")` em billing/cadastro/partner ativa o `GlobalExceptionHandler` do common (auth já tinha).
3. 500 sem stack no log → `GlobalExceptionHandler.handleGenericException` não logava; add `log.error(...)`.
4. **404 do Asaas (createCustomer)** → o HTTP Interface client descartava o prefixo `/api/v3` por causa da **barra inicial** nos `@PostExchange("/customers")`. Fix: baseUrl normalizada com `/` final + paths SEM barra inicial.
5. **404 persistente** → a env var `ASAAS_BASE_URL` no IntelliJ tinha uma **aspas simples (`'`) sobrando no fim**. Removido.
6. **`webhook_log.tenant_id` ficava null** (payload Asaas não traz tenant) → `WebhookLogService.logReceived` agora resolve via `SubscriptionRepository.findByAsaasSubscriptionId`. VERIFICADO: `pay_teste_002` gravou `tenant_id=1` (linha antiga `pay_teste_001` fica null, é histórica).

**Passo J (token inválido) — VERIFICADO:** header `asaas-access-token` errado → **401**, sem processar e sem criar linha no `webhook_log` (token validado antes do `logReceived`).

**Núcleo da Fase 3 (checkout + webhook + nextDueDate + Kafka) VERIFICADO.** Resta só sanidade opcional: I (resiliência/backoff+circuit breaker) e J (token inválido → 401).

> **O que cada peça exige:**
> - `POST /api/v1/checkout` (criar assinatura) → headers `X-User-Id` **e** `X-Tenant-Id` (o
>   `InternalRequestFilter` bloqueia sem `X-User-Id`; o controller lê `X-Tenant-Id`).
> - `POST /api/v1/webhooks/asaas` (ativar) → público, exige só `asaas-access-token` igual ao configurado.
> - O `PaymentReceivedHandler` chama o Asaas (`getSubscription`) para pegar o `nextDueDate` real —
>   então o webhook só funciona com um `asaasSubscriptionId` que **exista de verdade** no sandbox
>   (criado no passo G).

---

## A. Conta e credenciais Asaas (sandbox)
- [x] 1. Criar conta em **sandbox.asaas.com**. ✅ FEITO
- [x] 2. **Configurações → Integrações → Chave de API** → copiar a key (começa com `$aact_...`). ✅ FEITO
- [ ] 3. **(Só Opção 1 / webhook real)** **Integrações → Webhooks → Adicionar Webhook**: exige URL
      pública (ngrok), e-mail e Tipo de envio = **Sequencial**. O **token** ali deve ser o mesmo da env
      var `ASAAS_WEBHOOK_TOKEN`. **Para a Opção 2 (curl) NÃO crie webhook** — o token é só a env var
      local; o Asaas rejeita salvar sem URL pública ("A url informada é inválida").

## B. Variáveis de ambiente (PowerShell, na sessão que vai rodar o serviço)
```powershell
$env:ASAAS_API_KEY        = '$aact_SuaChaveSandboxAqui'
$env:ASAAS_BASE_URL       = 'https://sandbox.asaas.com/api/v3'
$env:ASAAS_WEBHOOK_TOKEN  = 'meu-token-teste'
$env:DB_USER              = 'postgres'      # ajuste ao seu compose
$env:DB_PASS              = 'postgres'
$env:REDIS_PASSWORD       = 'test-password'
$env:KAFKA_BROKERS        = 'localhost:9092'
```
> ⚠️ **Cuidado com aspas sobrando** ao setar `ASAAS_BASE_URL` (sobretudo na run config do IntelliJ):
> o valor deve ser exatamente `https://sandbox.asaas.com/api/v3` — uma `'` no fim deu 404 no checkout.
> No IntelliJ, as env vars do PowerShell **não** são herdadas; configure na própria Run Config.

## C. Subir infraestrutura
```powershell
docker compose up -d postgres zookeeper kafka redis
```
- [x] Conferir o Redis com senha: `docker compose logs redis | Select-String requirepass`
      (o default `test-password` já bate nos dois lados). ✅ infra de pé (postgres/redis/kafka/zookeeper healthy)

## D. Rodar as migrations
```powershell
./mvnw spring-boot:run -pl liquibase-service
```
- [x] Esperar terminar (app standalone que aplica e encerra). ✅

## E. Semear um plano ativo
O checkout busca `findByPlanTypeAndActiveTrue`. Inserir via Adminer (:8081) ou psql:
```sql
INSERT INTO billing.plan (id, name, plan_type, billing_cycle, value, active, created_at)
VALUES (gen_random_uuid(), 'Plano Starter', 'STARTER', 'MONTHLY', 99.90, true, now());
```
> `plan_type` aqui (`STARTER`) é o que vai no corpo do checkout.
- [x] ✅ Planos semeados: `MENSAL` (MONTHLY, 179.00) e `ANUAL` (YEARLY, 479.00), ambos ativos.

## F. Rodar o billing-service
```powershell
./mvnw spring-boot:run -pl billing-service
```
- [x] Sobe na porta **8088**. ✅ (rodado via IntelliJ Run Config; env vars configuradas lá)

## G. Testar criação de assinatura (checkout)
```powershell
curl.exe -s -X POST http://localhost:8088/api/v1/checkout `
  -H "Content-Type: application/json" `
  -H "X-User-Id: 1" `
  -H "X-Tenant-Id: 42" `
  -d '{ "planType": "STARTER", "cnpj": "47960950000121", "email": "teste@empresa.com", "razaoSocial": "Empresa Teste LTDA" }'
```
**Esperado:** JSON com `paymentUrl`, `boletoUrl`, `pixQrCode`/`pixCopyPaste` (podem vir nulos se o PIX
ainda não gerou), `dueDate`, plano e valor.

Verificar:
- [x] ✅ **HTTP 200** com boleto real (ver seção RESULTADOS no topo). Cliente + assinatura criados no Asaas.
- [x] ✅ No banco: `tenant_id=1`, status **`AGUARDANDO_PAGAMENTO`**, `asaas_subscription_id=sub_ftag1525etlzlh7d`.
- [x] ✅ **`asaas_subscription_id` anotado p/ o passo H:** `sub_ftag1525etlzlh7d`.

## H. Testar ativação via webhook `PAYMENT_RECEIVED`

**Opção 1 — fluxo real (exige URL pública):** rodar `ngrok http 8088`, colar a URL
`https://xxxx.ngrok.../api/v1/webhooks/asaas` no webhook do Asaas, e no painel sandbox confirmar o
recebimento da cobrança ("Confirmar recebimento em dinheiro"). O Asaas dispara o webhook sozinho.

**Opção 2 — simular com curl (mais rápido):** usar o `sub_xxx` do passo G. O `paymentId` pode ser
qualquer string nova.
```powershell
curl.exe -s -X POST http://localhost:8088/api/v1/webhooks/asaas `
  -H "Content-Type: application/json" `
  -H "asaas-access-token: meu-token-teste" `
  -d '{ "event": "PAYMENT_RECEIVED", "payment": { "id": "pay_teste_001", "subscription": "sub_xxx_DO_PASSO_G", "status": "RECEIVED", "value": 99.90, "billingType": "PIX" } }'
```
**Esperado:** `200 OK` imediato.

Verificar (é aqui que a Fase 3 entrega valor):
- [x] ✅ subscription `sub_ftag1525etlzlh7d` → **`ATIVA`** + **`next_due_date=2026-07-24`** (vindo do Asaas) + `activated_at`.
- [x] ✅ `webhook_log` mais recente → **`PROCESSADO`** (sem `error_message`).
- [x] ✅ Tópico `billing.subscription.activated` com o evento do tenant 1 (lido via `kafka-console-consumer`).
      Obs.: o binário nessa imagem é `/usr/bin/kafka-console-consumer` (sem `.sh`).

> **Teste de borda do `nextDueDate`:** com um `sub_xxx` **inexistente** no Asaas, o handler lança
> `TransientException`, o webhook_log fica em erro/liberado e a chave Redis é solta para retry —
> comportamento esperado.

## I. (Opcional) Validar resiliência do client
- [ ] Parar o serviço, trocar `ASAAS_BASE_URL` para um host inválido (ex. `https://10.255.255.1/api/v3`),
      refazer o checkout e observar nos logs as **3 tentativas com backoff (1s→2s→4s)** e a abertura
      do **circuit breaker `asaas-api`** após repetir.

## J. Token inválido (sanidade da segurança)
- [x] ✅ Token errado → **401**, sem processar, sem linha no `webhook_log`.

---

## Referência rápida dos artefatos da Fase 3
| Arquivo | Papel |
|---|---|
| `infra/asaas/client/Asaas{Customer,Subscription,Payment}Client` | Sub-clients HTTP Interface (`@HttpExchange`) |
| `infra/asaas/dto/*` | Records de request/response do Asaas |
| `infra/asaas/AsaasGateway` | Fachada: circuit breaker + retry/backoff, mapa de erros 4xx/5xx |
| `infra/config/AsaasClientConfig` | RestClient + proxies + customizer do CB (time limiter 20s) |
| `infra/asaas/AsaasValidationException` | Erro 4xx permanente (sem retry) |
| `services/CheckoutService` + `api/dto/CheckoutResponse` | Criação de assinatura + boleto/PIX no retorno |
| `services/webhook/handler/PaymentReceivedHandler` | Liga o `nextDueDate` real do Asaas na ativação |