# Auditoria — billing-service / uso de Redis e inconsistências

**Data:** 2026-06-23 · **Escopo:** `billing-service` (Fases 1–3 implementadas) + uso de Redis.
**Método:** callers confirmados via grep no código real (não suposição).

---

## 1. Uso do Redis — confirmado (quem realmente chama)

| Serviço / script Lua | Caller real | Veredito |
|---|---|---|
| **WebhookIdempotencyService** (`webhookIdempotencyAcquire` + `webhookComplete`) | `WebhookProcessor` | ✅ **Usado e testado** — única peça de Redis genuinamente em uso |
| **DistributedLockService** (`acquireLock` + `releaseLock`) | **ninguém** | ⚠️ **Órfão** — os crons que usariam (CommissionPayoutJob/DunningJob) são Fase 6/7, ainda não existem |
| **TenantStatusCacheService.put** (write-through) | `PaymentReceivedHandler` | ⚠️ **Escreve, mas ninguém lê** — `get()`/`evict()` sem caller (endpoint síncrono `GET /internal/billing/status` foi descartado). Custo sem consumidor |
| **annualGuardScript** + `annualCommissionGuard.lua` | **ninguém** | ❌ **Morto** — modelo de comissão anual não existe (comissão é recorrente, criada via Kafka) |

**Resumo:** dos 5 scripts Lua, só **2 estão em uso** (idempotência de webhook). 2 (lock) aguardam Fase 6/7. 1 (guard anual) é morto.

---

## 2. Inconsistências / o que não faz sentido

### 🔴 Crítico
**2.1 — `CommissionService.processarRepasses` marca comissões como PAGO com payout FALSO.** ✅ **RESOLVIDO (2026-06-24, Fase 4).**
~~`@Scheduled(cron "0 0 8 2 * *")`: todo dia 2 às 08h chama `AsaasClient.transferPix` (stub que retorna
`"SIMULADO-..."` sem mandar dinheiro) e mesmo assim seta `status=PAGO` + `paidAt`. Em qualquer instância
rodando, **falsifica repasses no banco**.~~
**Feito:** removido o `@Scheduled` e o corpo que chamava o stub; `processarRepasses()` virou no-op que só
loga "desabilitado — payout real só na Fase 6". O endpoint manual `/admin/trigger-repasse` devolve a mesma
mensagem. Nenhuma comissão é mais marcada `PAGO` sem transferência real.

**2.2 — `TokenService.validateSecret` (auth-service): check de ≥32 chars está COMENTADO.**
`if (secret == null /*|| secret.length() < 32*/)`. O `CLAUDE.md` afirma que o secret é validado no startup
(mín. 32 chars). Doc divergente do código + proteção real desligada (JWT_SECRET fraco passaria).
**Ação:** reativar o check.

### 🟡 Médio
**2.3 — `TenantStatusCacheService` write-through sem leitor.** Cache é atualizado (`put`) mas nada consome
(`get`/`evict` sem caller). Decisão: ligar um leitor interno (ex.: dunning) **ou** remover o `put` até
existir consumidor. A javadoc da classe já admite que o endpoint síncrono foi descartado.

**2.4 — `annualGuard` (bean + Lua) é código morto.** Remover, ou marcar explicitamente como roadmap (modelo anual).

**2.5 — `DistributedLockService` órfão.** OK se Fase 6/7 vier logo; senão é código nunca exercido
(os testes da Fase 1 validaram o Lua isolado, não o serviço integrado).

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
- ✅ **2.1 resolvido** (Fase 4 — payout falso desabilitado) e ✅ **2.6 resolvido** (Fase 4 — trilha única).
- **Agir já:** 2.2 (reativar check do JWT secret no auth-service).
- **Resgatado por fases seguintes:** 2.3, 2.5 (Redis órfão/sem-leitor) — a Fase 6 resgata o lock e o payout
  real; a Fase 5/7 resgata o cache (se um leitor interno for criado). 2.4 (`annualGuard` morto) segue como
  roadmap do modelo ANUAL.

> Relacionado: gap da Fase 5 (suspensão/cancelamento não propagados ao auth) — ver `spec/teste-restante.md`.