# Portal Admin — Tasks Funcionais

Origem: avaliação funcional do portal admin (2026-07-04). Nota funcional ~8/10.
Itens 1, 2, 3 e 4 implementados (pendente teste de runtime). Seção nova: **Ferramentas de diagnóstico** (5 a 8) para o time técnico resolver problemas em produção. Kafka (9) descartado (já têm DLQ + Kafka UI).

## 1. Status TRIAL/CONVIDADO na tela de Tenants ✅ (feito em 2026-07-04)

- `TenantDTO` (auth-service): `@Pattern` do status aceita `TRIAL|CONVIDADO` (antes um PUT em tenant TRIAL falhava validação mesmo sem mexer no status).
- Front admin: `TenantModel`, dropdowns de status (lista e form) e badges (TRIAL azul, CONVIDADO roxo).

---

## 2. Ações em Assinaturas ✅ (feito em 2026-07-04, exceto 2.2)

**Problema:** quando um pagamento dava errado o suporte não tinha ferramenta — via a assinatura mas não conseguia agir.

**Como ficou:**

1. **Reprocessar ativação ✅** — `POST /api/v1/subscriptions/{id}/reprocess`: consulta a 1ª cobrança no Asaas e, se paga (`Constants.ASAAS_PAID_STATUSES`), reativa pelo fluxo normal do webhook (`PaymentReceivedHandler`) — tenant/comissão/indicação consistentes. A lógica mora em `SubscriptionService.reprocessarPagamento` e o `ReconciliationJob` agora delega pra ela (dedup). Botão azul na tela Assinaturas em `AGUARDANDO_PAGAMENTO`/`SUSPENSO`, idempotente (já ativa → "sem mudança"). Audita `ASSINATURA_REPROCESS`.
2. **Link externo pro Asaas** — ❌ inviável: o painel do Asaas usa id interno numérico na URL (`/subscription/show/1146671`), não derivável do `sub_xxx` da API. Acesso externo fica pelo `invoiceUrl` das cobranças no drill-down.
3. **Drill-down assinatura → cobranças ✅** — `GET /api/v1/subscriptions/{id}/cobrancas` (`AsaasGateway.listPayments`, sem persistência local — consulta ao vivo). Linha expansível na tela com valor, vencimento, status colorido e link "Abrir fatura" (`invoiceUrl` do Asaas).
4. **Cancelar assinatura pelo admin ✅** — `POST /api/v1/subscriptions/{id}/cancel`: mesmas regras do self-service (idempotente, `CANCELAMENTO_SOLICITADO`, cancela renovação no Asaas, acesso até `next_due_date`) + auditoria `ASSINATURA_CANCEL_ADMIN`. Botão vermelho com dialog de confirmação em `ATIVA`/`SUSPENSO`.

**Pendente:** teste de runtime (reiniciar billing-service e exercitar os 3 fluxos no sandbox).

## 3. Visão 360 do Tenant ✅ (feito em 2026-07-05)

**Problema:** os dados do tenant existem espalhados (cadastro, usuários, assinatura, indicação de origem, auditoria) e o suporte precisa abrir 5 telas.

**Como ficou:** rota `admin/cadastros/tenants/:id` (clique na linha da listagem ou botão "Visão 360" leva pra lá) — componente `TenantDetail` no front admin, com seções:

- **Cabeçalho ✅**: nome, CNPJ (pipe), status (badge), plano, datas de trial/ativação (`GET /auth/tenants/{id}`).
- **Assinatura ✅**: `GET /billing/api/v1/subscriptions?tenantId=` — o controller ganhou o filtro opcional (`SubscriptionRepository.findByTenantId` paginado). Reaproveita as ações do item 2 (reprocessar, drill-down de cobranças, cancelar).
- **Usuários do tenant ✅**: `POST /auth/users/search` com `tenantId` (já existia).
- **Origem ✅**: `GET /partner/api/v1/partners/referrals/by-tenant/{tenantId}` (novo) → `OrigemTenantDTO` (contador, CRC, código, %, status, datas). 404/vazio ⇒ "cadastro direto".
- **Auditoria do tenant ✅**: `GET /auth/audits` ganhou filtro opcional `targetType`/`targetId` (`AuditRepository.findByTarget`). Como o `targetId` da auditoria é UUID de usuário (não o `tenantId` Long), a seção tem um seletor de usuário do tenant e consulta `targetType=USER&targetId={userUuid}`.

**Backend novo:** filtro `tenantId` nas subscriptions, filtro de alvo nos audits, endpoint "origem por tenantId" no partner-service. Todos filtros aditivos em queries existentes — nada estrutural, sem DDL.

**Pendente:** teste de runtime (subir auth/billing/partner + admin front e exercitar as 5 seções).

## 4. Visão agregada de Comissões pro admin ✅ (feito em 2026-07-05)

**Problema:** o parceiro tem extrato no portal dele, mas o admin não sabe "quanto vou pagar de comissão em X" sem somar na mão.

**Como ficou:**

- `GET /api/v1/commissions/summary?competencia=yyyy-MM` (billing; sem competência → mês atual): `CommissionRepository.summarizeByPeriod` (GROUP BY parceiro+status) agregado em `CommissionSummaryDTO` (`totalPendente`, `totalPago`, `parceirosAPagar`, `porParceiro[]`). PENDENTE + EM_TRANSFERENCIA contam como "a pagar".
- Tela Pagamentos (`invoices`): cards no topo (A pagar / Pago / Parceiros a pagar) + `<input type="month">` de competência + tabela por parceiro. A listagem detalhada de comissões continua abaixo.

**Pendente:** teste de runtime. **ponytail:** `porParceiro` mostra o `partnerId` (UUID) — billing não tem o nome do parceiro (vive no partner-service); enriquecer com nome depois se necessário. Card no dashboard da home (extra barato) não feito.

## 5. Rastreador de operação (correlationId) ✅ (feito em 2026-07-05)

**Problema (técnico):** quando algo falha pra um tenant ("não ativou / não recebi e-mail"), rastrear o que aconteceu exige caçar no Kibana por serviço.

**Como ficou:** todo evento de auditoria já grava `correlation_id`. `GET /auth/audits/trace/{correlationId}` (`@Secured APP_OWNER`, `AuditRepository.findByCorrelationIdOrderByEventDateAsc`) devolve a linha do tempo da operação. Tela `admin/diagnostico/rastreador`: cola o correlationId → tabela ordenada (data, ação, alvo, resultado, ator).

**Limite:** hoje só cruza a auditoria do auth-service. Cruzar com logs do ELK (por correlationId) fica como evolução.

## 6. Painel de saúde dos serviços ✅ (feito em 2026-07-05)

**Problema (técnico):** "tá tudo no ar? qual serviço caiu?" sem abrir o Eureka/Grafana — especialmente via SSH-tunnel na OCI.

**Como ficou:** `GET /auth/diagnostics/health` (`@Secured APP_OWNER`) usa o `DiscoveryClient` (Eureka) pra enumerar os serviços e o `RestClient` pra pingar o `/actuator/health` de uma instância de cada → `[{name, status, instances}]`. Tela `admin/diagnostico/saude`: tabela status/instâncias + botão atualizar.

**ponytail:** pinga só a 1ª instância de cada serviço; iterar todas se rodar réplicas. `/actuator/info` (git/build) fica como próximo incremento.

## 7. Toggle de log level em runtime ✅ (feito em 2026-07-05)

**Problema (técnico):** debugar em produção (OCI) exige subir um pacote pra DEBUG — hoje só com redeploy/restart do container.

**Como ficou:** `loggers` exposto no `/actuator` dos 4 serviços de app (`management.endpoints...include` + `endpoint.loggers.access: unrestricted`). Dois endpoints proxy no `DiagnosticsController` (`@Secured APP_OWNER`), reusando o `DiscoveryClient`:
- `GET /auth/diagnostics/loggers?service=X` → devolve o mapa de loggers/levels do serviço.
- `POST /auth/diagnostics/loggers?service=X&logger=Y&level=Z` → faz o POST em `/actuator/loggers/{Y}` com `{configuredLevel:Z}` (level vazio reseta).

Tela `admin/diagnostico/logs`: seletor serviço (vem do painel de saúde) → pacote → nível, botões Aplicar e Consultar nível.

**Segurança/ponytail:** o `/actuator/loggers` fica só na rede interna (não passa pelo gateway); o gate real é o `@Secured APP_OWNER` do proxy. Gateway não teve `loggers` exposto (só os 4 serviços de negócio); só a 1ª instância recebe o nível.

## 8. Runner de jobs agendados ("rodar agora") ✅ (feito em 2026-07-06)

**Problema (técnico):** quando um cron não disparou (scheduler travou, deploy no horário), hoje só resta esperar o próximo dia. Precisa forçar.

**Como ficou:** cada serviço expõe seu runner (o front junta tudo numa tela só). Todos os jobs já são idempotentes com lock distribuído próprio, então rodar manualmente é seguro. Execução **assíncrona** (não segura o thread HTTP) — a tela marca `EXECUTANDO` e o botão Atualizar mostra `OK`/`ERRO` + duração.
- **billing** `JobRunnerController` (`/api/v1/diagnostics/jobs`, `@PreAuthorize hasAuthority('REPASSE_EXECUTE')` — mesma authority PLATFORM do trigger de repasse): `reconciliation`, `webhook-recovery`, `dunning`, `commission-payout`.
- **auth** `DiagnosticsController` (`/auth/diagnostics/jobs`, `@Secured APP_OWNER`): `trial-d10`, `trial-d15` (o `TrialScheduler`).
- Tela `admin/diagnostico/jobs`: tabela job/serviço/última execução/status/duração + botão "Rodar agora".

**ponytail / limites:** (1) só histórico de execução **manual** (in-memory por instância) — execução agendada não aparece e reiniciar o serviço zera; (2) **próxima execução** não é mostrada (exigiria parsear o cron de cada job); (3) o `TrialScheduler` do **partner-service** ficou de fora — mesmo padrão, dá pra adicionar se precisar.

---

## Ordem sugerida

| # | Task | Esforço | Valor | Status |
|---|------|---------|-------|--------|
| 2.1 | Reprocessar ativação | S | Alto (destrava suporte a pagamento) | ✅ feito |
| 4 | Resumo de comissões | S | Alto (fecha o ciclo financeiro do parceiro) | ✅ feito |
| 2.3–2.4 | Drill-down, cancel admin | M | Médio | ✅ feitos (2.2 inviável) |
| 3 | Visão 360 do tenant | L | Alto (as ações do item 2 já existem pra ela reusar) | ✅ feito |
| 5 | Rastreador de correlationId | S | Alto (diagnóstico técnico) | ✅ feito |
| 6 | Painel de saúde dos serviços | S | Médio (diagnóstico técnico) | ✅ feito |
| 7 | Toggle de log level em runtime | S | Alto (debug em prod sem redeploy) | ✅ feito |
| 8 | Runner de jobs agendados (run now) | M | Alto (forçar Reconciliation/Dunning/Payout/Trial) | ✅ feito |
| 9 | Kafka: lag + eventos falhos + replay | M/L | Baixo (já têm DLQ + Kafka UI :8080) | ❌ descartado |
