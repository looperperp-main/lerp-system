# Syax Billing Service — Fluxos, Decisões e Regras de Negócio

> Documento técnico complementar.  
> Cobre os dois fluxos de onboarding, as regras de negócio definidas e as lacunas resolvidas.

---

## Sumário

1. [Fluxo A — Onboarding via Contador Parceiro](#fluxo-a)
2. [Fluxo B — Onboarding Direto sem Parceiro](#fluxo-b)
3. [Diferenças entre os fluxos](#diferencas)
4. [Regras de negócio — lacunas resolvidas](#lacunas)
5. [Máquina de estados — atualizada](#estados)
6. [Cron jobs — lista completa](#crons)

---

## Fluxo A — Onboarding via Contador Parceiro

> Aplicado quando o tenant é cadastrado por um contador parceiro com código de indicação.  
> Um registro `partner_referral` é criado e comissões são calculadas a cada pagamento.

### Fase 1 — Onboarding do parceiro

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 1 | Contador | Acessa landing page de parceiros | Preenche nome, CRC, CNPJ do escritório, e-mail e telefone |
| 2 | Sistema | Valida dados e aprova conta de parceiro | Verificação automática do CRC ou revisão manual pelo admin no painel |
| 3 | Sistema | Gera código de indicação único | Envia e-mail de boas-vindas com credenciais de acesso + código parceiro (ex: CTR-00123) |
| 4 | Contador | Acessa painel parceiro | Visualiza clientes cadastrados, status de cada tenant, comissões acumuladas e repasses |

### Fase 2 — Cadastro do cliente

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 5 | Contador | Informa CNPJ do cliente no painel | Código de indicação já vinculado automaticamente à sessão do contador |
| 6 | Sistema | Valida formato + consulta Receita Federal | Algoritmo de dígitos verificadores → OpenCNPJ → retorna situação cadastral e dados da empresa |
| — | — | **Decisão: CNPJ está ativo na Receita?** | |
| 7a | Sistema | ✓ Sim — pré-preenche dados | Razão social, e-mail e telefone — contador revisa e confirma |
| 7b | Sistema | ✗ Não — bloqueia cadastro | Notifica o contador com o motivo exato (suspensa, inapta, baixada etc) |
| 8 | Contador | Revisa dados e dispara convite | Confirma e-mail de contato, define plano sugerido e envia convite ao cliente |
| 9 | Sistema | Cria tenant — status: CONVIDADO | Gera token de ativação (JWT com TTL de 7 dias) + registra vínculo em partner_referral |
| 10 | Sistema | Envia e-mail de convite ao cliente | Link de ativação personalizado + nome do escritório do contador + prazo para ativar |
| 11 | Contador | Recebe confirmação de convite enviado | Painel atualiza status para "Aguardando ativação" — contador pode reenviar se necessário |

### Fase 3 — Ativação e trial (D+0 → D+15)

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 12 | Cliente | Clica no link e ativa a conta | Tela com dados pré-preenchidos pelo contador — cliente define apenas a senha |
| 13 | Sistema | Ativa tenant — status: TRIAL | Registra trial_started_at + calcula trial_expires_at (D+15) + invalida o token de ativação |
| 14 | Sistema | Notifica contador: cliente ativou | Push no painel + e-mail: "Cliente X ativou a conta — acompanhe o engajamento" |
| 15 | Cliente | Usa o produto durante o trial | Sistema rastreia features acessadas, logins, tempo de uso e gaps de adoção |
| 16 | Sistema | D+10 — alerta proativo ao contador | Envia relatório de engajamento: features usadas, features não exploradas, nº de logins |
| 17 | Contador | Contato proativo com o cliente | WhatsApp, ligação ou e-mail — apresenta features não exploradas e tira dúvidas antes do D+15 |

### Fase 4 — Conversão (D+15)

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 18 | Sistema | D+15 — dispara ativação comercial | E-mail automático ao cliente sobre fim do trial + notificação urgente ao contador no painel |
| 19 | Contador | Follow-up humanizado com o cliente | Apresenta gaps de uso, demonstra ROI, tira últimas dúvidas e propõe o plano ideal |
| — | — | **Decisão: cliente decide continuar?** | |
| 20a | Cliente | ✓ Sim — seleciona plano | Segue para seleção de plano e pagamento |
| 20b | Contador | ✗ Não — novo ciclo de follow-up | Máx. 3 tentativas. Após isso, tenant marcado como PERDIDO e contador é notificado |
| 21 | Cliente | Escolhe plano — mensal ou anual | Anual com 2 meses grátis — preços exibidos com desconto já aplicado |
| 22 | Cliente | Realiza pagamento via Asaas | Boleto com QR Code PIX ou cartão — Asaas cria cliente e assinatura recorrente |
| 23 | Sistema | Webhook PAYMENT_RECEIVED → ativa tenant | Status: ATIVO — atualiza partner_referral com converted_at e subscription_id do Asaas |
| 24 | Sistema | Notifica contador: cliente converteu | Painel atualiza cliente de "Trial" para "Ativo" + prévia da comissão a receber |

### Fase 5 — Comissão e repasse

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 25 | Financeiro | Calcula comissão sobre o plano | % definido no cadastro do parceiro × valor do plano — registra em commission com status PENDENTE |
| 26 | Financeiro | Acumula no ciclo mensal | Agrupa todas as comissões do mês por parceiro — exibe extrato em tempo real no painel |
| 27 | Sistema | D+1 do mês — processa repasses | Cron job varre commissions PENDENTES → transferência via Asaas (PIX ou TED) → status: PAGO |
| 28 | Contador | Recebe comissão + extrato no painel | Histórico por cliente, período e valor — download de extrato disponível em PDF |
| 29 | Financeiro | ↺ Cada renovação → nova comissão automática | PAYMENT_RECEIVED recorrente → nova commission PENDENTE — repete todo mês enquanto o cliente estiver ativo |

---

## Fluxo B — Onboarding Direto sem Parceiro

> Aplicado quando o tenant se cadastra diretamente pela landing page sem código de indicação.  
> Nenhum `partner_referral` é criado. O follow-up comercial no D+15 é responsabilidade da equipe interna da Syax. Nenhuma comissão é calculada.

### Fase 1 — Cadastro direto (D+0)

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 1 | Cliente | Acessa landing page e clica "Criar conta grátis" | Nenhum código de indicação — campo referral nulo |
| 2 | Cliente | Preenche e-mail + senha | Sem CNPJ obrigatório, sem dados de pagamento — mínimo atrito |
| 3 | Sistema | Valida e-mail + cria tenant — status: TRIAL | partner_referral = null · registra trial_started_at e trial_expires_at (D+15) |
| 4 | Sistema | Envia e-mail de boas-vindas | Guia de primeiros passos + features principais + link para o suporte |

### Fase 2 — Trial (D+0 → D+15)

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 5 | Cliente | Usa o produto livremente | Sistema rastreia features acessadas, logins, tempo de uso e gaps de adoção |
| 6 | Sistema | Rastreia engajamento em trial_engagement | Uma linha por tenant + feature · incrementa access_count a cada acesso |
| 7 | Sistema | D+10 — gera relatório de engajamento | Sem parceiro vinculado: entrega para fila interna da Syax em vez de notificar contador |
| 8 | Syax | Equipe Syax consulta relatório e prepara abordagem | Analisa features usadas, gaps de adoção e número de logins antes de contatar o cliente |

### Fase 3 — Ativação comercial (D+15)

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 9 | Sistema | D+15 — dispara ativação comercial | E-mail automático ao cliente sobre fim do trial · cria ticket na fila interna da Syax |
| 10 | Syax | Follow-up humanizado pela equipe Syax | Apresenta gaps de uso, demonstra ROI, tira dúvidas e propõe plano ideal — mesmo modelo do contador parceiro |
| — | — | **Decisão: cliente decide continuar?** | |
| 11a | Cliente | ✓ Sim — seleciona plano e paga | Segue para seleção de plano e pagamento |
| 11b | Syax | ✗ Não — novo follow-up a cada 48h | Máx. 3 tentativas. Após isso, tenant marcado como PERDIDO |

### Fase 4 — Conversão

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 12 | Cliente | Escolhe plano — mensal ou anual | Anual com 2 meses grátis — mesmo fluxo do tenant via parceiro |
| 13 | Cliente | Realiza pagamento via Asaas | Boleto com QR Code PIX ou cartão · Asaas cria cliente e assinatura recorrente |
| 14 | Sistema | Webhook PAYMENT_RECEIVED → ativa tenant | Status: ATIVO · partner_referral = null · nenhuma comissão calculada |
| 15 | Sistema | Assinatura recorrente ativa | Renovações automáticas via Asaas · webhooks gerenciados normalmente |

### Fase 5 — Pós-conversão

| # | Ator | Ação | Detalhe |
|---|---|---|---|
| 16 | Sistema | Grace period de 5 dias em caso de inadimplência | PAYMENT_OVERDUE → alerta ao cliente · após D+5 sem pagamento → status: SUSPENSO |
| 17 | Sistema | Reativação automática por PAYMENT_RECEIVED | Mesmo fluxo do parceiro — webhook reativa tenant de SUSPENSO para ATIVO sem ação manual |
| 18 | Syax | Vínculo retroativo com parceiro — opcional | Equipe Syax pode vincular manualmente um parceiro ao tenant após conversão · comissão passa a ser calculada da próxima renovação em diante |

---

## Diferenças entre os fluxos

| Aspecto | Fluxo A — com parceiro | Fluxo B — direto |
|---|---|---|
| `partner_referral` | Criado com status `CONVIDADO` | **Nulo — não criado** |
| Token de ativação | JWT enviado por e-mail (TTL 7 dias) | Não existe — cadastro direto |
| Status inicial | `CONVIDADO` | `TRIAL` imediato |
| Alerta D+10 | Notifica o contador | Fila interna da Syax |
| Follow-up D+15 | Contador faz o contato | **Equipe interna da Syax** |
| Comissão | Calculada a cada `PAYMENT_RECEIVED` | **Nenhuma** |
| Notificações | Contador recebe push + e-mail | Fila de atendimento interno |
| Vínculo com parceiro | Pré-existente | Pode ser criado retroativamente |

---

## Regras de negócio — lacunas resolvidas

### 1. Grace period

**Definição:** 5 dias corridos após o vencimento da cobrança.

**Fluxo (modelo de timestamps absolutos — convenção do billing):**
1. Asaas dispara `PAYMENT_OVERDUE` no dia do vencimento
2. O `PaymentOverdueHandler` grava timestamps absolutos na `billing.subscription`: `suspend_at = now + 5d`
   e `cancel_at = now + 7d` (não recalcula janela a cada execução)
3. Sistema alerta o tenant por e-mail
4. O `DunningJob` (Fase 7) roda diariamente e age por comparação de timestamp:
   `suspend_at <= now` → status `SUSPENSO`; `cancel_at <= now` → status `CANCELADO`
5. `PAYMENT_RECEIVED` antes do prazo zera `suspend_at`/`cancel_at` e reativa

**Regra técnica:** o `DunningJob` varre `billing.subscription` onde `suspend_at <= NOW()` (ainda `ATIVA`)
para suspender e `cancel_at <= NOW()` para cancelar. Os timestamps absolutos vêm do `PaymentOverdueHandler`,
dispensando o cruzamento com `webhook_log` do modelo antigo (`next_due_date < hoje - 5d`).

---

### 2. Cron de suspensão

Quarto cron job do sistema — implementado como `DunningJob` (Fase 7 do billing).

| Job | Disparo | Ação | Tabelas |
|---|---|---|---|
| **DunningJob** | Diário (ex: 02:00) | Suspende subscriptions com `suspend_at <= now` (→ `SUSPENSO`) e cancela as com `cancel_at <= now` (→ `CANCELADO` + comissões PENDENTE canceladas). Timestamps gravados pelo `PaymentOverdueHandler`. | `subscription`, `commission` |

**Idempotência:** age por comparação de timestamp e checa o estado atual antes de transicionar — reexecuções não geram efeito duplicado.

---

### 3. Reativação após suspensão

Fluxo 100% automático via webhook — nenhuma ação manual necessária.

> **Arquitetura event-driven (decisão de resiliência):** o auth service **não** consulta o billing
> de forma síncrona no login. O estado de acesso do tenant vive no próprio `auth.tenant.status`,
> atualizado por eventos Kafka que o billing publica. Assim, se o billing estiver fora do ar, o login
> continua funcionando com o último status conhecido. (O endpoint síncrono `billing.isActive()` /
> `GET /internal/billing/status` que retornaria HTTP 402 foi **descartado** — ver `payments-service.md`
> §11 e §21 Fase 5.)

```
Billing service: ATIVO → SUSPENSO (cron de suspensão, Fase 7)
    ↓ publica billing.subscription.suspended
Auth service consome o evento → auth.tenant.status = SUSPENSO
    ↓
Tenant tenta logar → POST /auth/tenant/login
    ↓ auth barra localmente (status != ATIVO/TRIAL) → 401 TENANT_NOT_ACTIVE
    ↓ mensagem: "Sua conta está suspensa. Regularize o pagamento."
    ↓
Cliente paga boleto ou PIX
    ↓
Asaas dispara PAYMENT_RECEIVED
    ↓
Billing service: SUSPENSO → ATIVO · suspended_at preservado (auditoria) · next_due_date atualizado
    ↓ publica billing.subscription.activated
Auth service consome o evento → auth.tenant.status = ATIVO
    ↓
Próximo login liberado normalmente
```

> **Gap conhecido (a fechar na Fase 7):** hoje o billing só publica `billing.subscription.activated` e o
> auth só consome esse. Os eventos `billing.subscription.suspended`/`.cancelled` e seus consumers ainda
> **não existem** — então um tenant suspenso/cancelado no billing ainda consegue logar até essa fase ser
> implementada.

---

### 4. Comissão durante suspensão

**Regra:** sem comissão retroativa.

Comissão é gerada apenas por `PAYMENT_RECEIVED` efetivo. Se o tenant ficou 2 meses suspenso e pagou apenas a fatura atual, o contador recebe comissão somente desse mês. Garantido pela arquitetura — sem webhook, sem comissão.

---

### 5. Token de convite expirado

**TTL:** 7 dias a partir de `invited_at`.

**Ao expirar:** tenant permanece em `CONVIDADO`. O sistema não regenera token automaticamente.

**Fluxo de reenvio:**
1. Contador localiza o cliente com badge "Convite expirado" no painel
2. Clica em "Reenviar convite"
3. Sistema invalida o token anterior (`activation_token = null`)
4. Gera novo JWT com TTL de 7 dias e atualiza `partner_referral`
5. Envia novo e-mail de convite ao cliente

**Regra:** não há limite de reenvios, mas cada reenvio invalida o anterior. Apenas um token ativo por vez por `partner_referral`.

---

### 6. Cancelamento manual

**Quem pode cancelar:** usuário admin do tenant pelo painel, ou equipe interna da Syax pelo admin service.

**Plano mensal:** aviso prévio de 30 dias (CDC, art. 49). Acesso mantido até o fim do período pago.

**Plano anual:** sem aviso prévio. Sem reembolso. Acesso mantido até `next_due_date`.

**Fluxo técnico:**
```
Confirmação do cancelamento
    ↓
Billing service → POST /api/asaas/subscriptions/{id}/cancel
    ↓
Asaas dispara SUBSCRIPTION_INACTIVATED
    ↓
subscription.status = CANCELADO · tenant.status = CANCELADO · cancelled_at = NOW()
```

**Comissão:** comissão do mês já calculada (PENDENTE) não é estornada. Nenhuma nova comissão gerada a partir do cancelamento.

---

### 7. Plano anual — política de não reembolso

> **Cancelamento de plano anual não gera reembolso do valor residual.**  
> O acesso permanece ativo até o fim do período contratado.  
> Política amparada em contrato aceito digitalmente no ato da assinatura.  
> **O sistema não implementa lógica de reembolso.**

Esta decisão deve constar no contrato de uso exibido no checkout. O sistema cancela a renovação futura e mantém o acesso até `next_due_date`. Qualquer questionamento tem respaldo no aceite contratual registrado com timestamp.

---

### 8. Follow-up no fluxo direto

Quando o tenant se cadastra sem parceiro, o follow-up comercial no D+15 é responsabilidade da **equipe interna da Syax**.

Tecnicamente: se `partner_referral` for nulo, os cron jobs D+10 e D+15 entregam o relatório e criam ticket na fila interna em vez de notificar um contador.

**Vínculo retroativo com parceiro:**
- Equipe Syax pode vincular manualmente um parceiro ao tenant já convertido
- Cria `partner_referral` com `status = CONVERTIDO` e `converted_at` preenchido com a data atual
- `invited_at` e `activated_at` ficam nulos
- Comissão começa a ser calculada a partir da **próxima renovação** — sem retroativo
- Requer aprovação manual no admin service

---

## Máquina de estados — atualizada

```
CONVIDADO → TRIAL → ATIVO ⇄ SUSPENSO
                  ↘              ↓
                PERDIDO      CANCELADO
```

> No Fluxo B (cadastro direto), o status `CONVIDADO` não é usado. O tenant inicia em `TRIAL`.

| Transição | Gatilho |
|---|---|
| `CONVIDADO → TRIAL` | Cliente clica no link de ativação e define senha |
| `TRIAL → ATIVO` | `PAYMENT_RECEIVED` após conversão |
| `TRIAL → PERDIDO` | 3 follow-ups sem conversão |
| `ATIVO → SUSPENSO` | `DunningJob` quando `suspend_at <= now` (grace period de 5 dias) |
| `SUSPENSO → ATIVO` | `PAYMENT_RECEIVED` (pagamento regularizado) |
| `ATIVO → CANCELADO` | `SUBSCRIPTION_INACTIVATED` ou cancelamento manual |
| `SUSPENSO → CANCELADO` | `SUBSCRIPTION_INACTIVATED` ou cancelamento manual |

---

## Cron jobs — lista completa

| Job | Disparo | Ação | Tabelas |
|---|---|---|---|
| Trial Alert | D+10 após `trial_started_at` | Com parceiro: envia relatório ao contador. Sem parceiro: entrega para fila interna da Syax | `trial_engagement`, `partner_referral` |
| Trial Expiry | D+15 após `trial_started_at` | Com parceiro: notificação urgente ao contador. Sem parceiro: cria ticket interno + e-mail ao cliente | `subscription`, `partner_referral` |
| **DunningJob** | Diário (02:00) | Suspende (`suspend_at <= now` → `SUSPENSO`) e cancela (`cancel_at <= now` → `CANCELADO`) por timestamps absolutos gravados pelo `PaymentOverdueHandler` | `subscription`, `commission` |
| Commission Payout | D+1 de cada mês | Processa repasses PENDENTES via Asaas (PIX ou TED) → status: PAGO | `commission` |

> Todos os jobs devem ser idempotentes. Usar distributed lock via Redis (`DistributedLockService` +
> scripts Lua, implementado na Fase 1 do billing) em ambiente multi-instância.