# Customização Paga por Cliente → Sinal de Demanda → Produto

Última atualização: 31 de agosto de 2026

Origem: brainstorming de receita adicional (31/08/2026) — customização é uma fonte de receita comum
em ERP (Totvs, SAP e similares vivem disso), mas compete por tempo de engenharia com o produto
principal. Este doc desenha o processo e o mínimo técnico pra oferecer isso **sem** virar item de
roadmap disfarçado.

**Status: planejado, não iniciado.** Nenhuma linha de código ou migration deste doc foi escrita —
vale executar quando o primeiro pedido real de customização acontecer, não antes (YAGNI).

## 0. Princípio central

Customização **nunca é feature de roadmap**. É serviço sob demanda, cobrado à parte da assinatura.
Só vira feature de produto (grátis, pra todo mundo) quando o sinal de demanda justificar — e nesse
momento, quem já pagava para de pagar (sem retroativo, ver §7).

## 1. Fluxo de ponta a ponta

1. **Cliente pede** uma customização (canal: suporte/comercial, fora do sistema por enquanto).
2. **Syax registra o pedido** — mesmo antes de orçar/aceitar, pra contar sinal de demanda depois
   (ver §6). Um pedido que não vira negócio ainda conta como "alguém quis isso".
3. **Negociação**: preço e modelo de cobrança (única ou recorrente — decidido caso a caso, ver §4)
   definidos na conversa comercial, não pelo sistema.
4. **Aceite** → status do pedido muda pra `ORCADO` → `PAGO` quando a cobrança é criada.
5. **Implementação**: código isolado, atrás de uma permissão nova exclusiva do tenant (§5).
6. **Entrega**: feature liberada pro tenant, cobrança ativa.
7. **Sinal de demanda**: cada novo pedido da mesma coisa por outro tenant conta no total (§6),
   pago ou não.
8. **Decisão de promoção**: qualitativa, olhando a contagem — não é gatilho automático no sistema.
9. **Promoção a produto** (§7): generaliza o código, abre pra todo mundo, encerra cobrança
   recorrente de quem pagava (sem retroativo), fecha o ciclo com auditoria.

## 2. Modelo de dados — o único componente novo

Tabela pequena em `billing-service` (schema `billing`), provisoriamente `customizacao_solicitada`.
É o único dado genuinamente novo do plano — tudo mais reaproveita o que já existe.

| Coluna | Tipo | Nota |
|---|---|---|
| `id` | UUID | PK |
| `tenant_id` | BIGINT | quem pediu |
| `feature_key` | VARCHAR | identificador curto da customização pedida (usado pra agrupar pedidos iguais de tenants diferentes — ex. `EXPORT_XML_CUSTOM`) |
| `descricao` | VARCHAR(500) | texto livre do que foi pedido |
| `status` | VARCHAR | `SOLICITADO` \| `ORCADO` \| `PAGO` \| `PROMOVIDO` \| `RECUSADO` |
| `modelo_cobranca` | VARCHAR | `UNICA` \| `RECORRENTE` \| null (ainda não orçado) |
| `subscription_id` | UUID, nullable | FK pra `Subscription` quando `status=PAGO` (ver §4) |
| `created_at` / `updated_at` | timestamp | auditoria simples |

`feature_key` é o campo que responde "quantos tenants pediram a mesma coisa": `SELECT
COUNT(DISTINCT tenant_id) FROM customizacao_solicitada WHERE feature_key = ?`.

**Sem tabela nova pra cobrança nem pra permissão** — os dois pontos seguintes reaproveitam
domínio existente.

## 3. Por que `billing-service` e não um módulo novo

O ciclo de vida da customização termina em cobrança (`Subscription`) na maioria dos casos — faz
sentido nascer perto de onde vai morar. Alternativa descartada: `partner-service` (mais perto do
padrão de engajamento que inspirou a tabela, mas customização não é dado de parceiro/indicação,
é dado comercial direto Syax↔tenant).

## 4. Cobrança — reaproveita `Subscription`, não cria conceito de billing novo

`billing-service/domain/Subscription.java` já modela `tenantId + planType + billingCycle + value +
status` como campos soltos (sem FK rígida a `Plan`) — formato que já serve pra uma customização sem
mudar schema:

```java
// Subscription existente, valores novos:
subscription.setPlanType("ADDON_CUSTOM");       // nova constante em Constants.java quando implementar
subscription.setBillingCycle(modeloEscolhido);  // "UNICA" ou "MENSAL" — campo já é texto livre
subscription.setValue(valorNegociado);
subscription.setStatus("ATIVA");
```

- **`UNICA`**: cobrança de projeto, sem recorrência. Cliente "não leva" o software (é SaaS, não
  código entregue) — mas pra trabalho pontual (ex. migração de dado específica) cobrar uma vez só
  ainda faz sentido; não gera `status` pra cancelar depois.
- **`MENSAL`**: cliente paga enquanto usa a feature — é aluguel, não compra. Quando cancela (ou
  quando a feature é promovida a produto), `status → CANCELADO`, sem reembolso do que já foi
  cobrado (decisão tomada em 31/08/2026, ver §7).
- **Modelo escolhido por caso** (decisão tomada 31/08/2026): o campo já é texto livre, então os
  dois modelos convivem sem trabalho extra de schema — a decisão é só comercial, na hora de orçar.

A integração com Asaas (cobrança real) reaproveita o pipeline que `Subscription` já usa — nenhuma
integração nova.

## 5. Controle de acesso — permissão exclusiva do tenant

Mesmo padrão RBAC já usado no sistema (`DOMINIO_ACAO`, seed em `auth-schema-009`):

- Nova ação por customização, convenção de nome pra não poluir o catálogo geral:
  `CUSTOM_<TENANT_SLUG>_<FEATURE_KEY>` (ex. `CUSTOM_ACME_EXPORT_XML`). Prefixo `CUSTOM_` deixa
  filtrável/limpável na tela de permissões quando o cliente sair ou a feature for promovida.
- Concessão só via fluxo já existente e gated a `APP_OWNER`
  (`PermissionController`/`RoleController`, `@PreAuthorize("hasRole('APP_OWNER')")`) — cliente
  nunca se autoconcede, mesma trava que já existe pra qualquer role/permissão hoje.
- Backend: `@PreAuthorize("hasAuthority('CUSTOM_ACME_EXPORT_XML')")` no(s) endpoint(s) novos.
- Front: guard de rota + `*ngIf` no menu, mesmo padrão de `authorities` já decodificado do JWT
  (usado hoje em `web-layout.ts`/`app.routes.ts`).

## 6. Isolamento de código

Customização de 1 cliente mora em pasta própria, nunca em `if` inline dentro de componente/serviço
compartilhado:

- Front: `pages/custom/<tenant-slug>/`
- Back: pacote próprio, ex. `services/custom/<TenantSlug>...Service`

Motivo: em 1-2 clientes um `if` inline seria mais rápido, mas em 5+ vira código que ninguém ousa
tocar por medo de quebrar o cliente X. Pasta própria = fácil de generalizar (promoção, §7) ou
apagar inteira (cliente saiu, feature não pegou).

## 7. Promoção a produto

Disparada por decisão qualitativa (não é um gatilho automático no sistema — o "8 de 10" do
brainstorming é exemplo de ordem de grandeza, não regra codificada).

Passos, nessa ordem:

1. **Generalizar o código** — sai de "resolve o caso do tenant X" pra "resolve qualquer tenant".
   Trabalho de engenharia real, não é flip de flag.
2. **Abre o acesso** — remove a `@PreAuthorize`/guard (feature vira `PADRAO`, sem gate; mais simples
   que conceder a authority a todo mundo manualmente).
3. **Encerra cobrança recorrente de quem pagava** (decisão tomada 31/08/2026): `Subscription.status
   → CANCELADO` a partir da promoção, **sem retroativo** — quem já pagou por período anterior não
   é reembolsado, e ninguém mais é cobrado dali pra frente. Cobrança `UNICA` não é afetada (já foi
   entregue e consumida integralmente).
4. **Fecha o pedido**: `customizacao_solicitada.status = PROMOVIDO` pra todos os registros daquele
   `feature_key`.
5. **Audita** (§8) o encerramento.

## 8. Auditoria

Reaproveita `AuditEventDTO`/Kafka (`common`) já usado em eventos de auth. Novos tipos de evento
(constantes a criar em `Constants.java` quando implementar, por regra do projeto — nunca literal
solto):

- `CUSTOMIZACAO_SOLICITADA`
- `CUSTOMIZACAO_COBRANCA_CRIADA`
- `CUSTOMIZACAO_ACESSO_CONCEDIDO`
- `CUSTOMIZACAO_PROMOVIDA_A_PRODUTO`
- `CUSTOMIZACAO_COBRANCA_ENCERRADA`

Sem essa trilha, o "quantos pediram" e o "quem parar de cobrar" na promoção dependem de memória
humana — não escala além do primeiro cliente.

## 9. Riscos já mapeados e como o desenho mitiga

| Risco | Mitigação no desenho |
|---|---|
| Cliente se autoconcede a feature | Concessão só via `APP_OWNER` (já é assim pra qualquer permissão hoje) |
| Catálogo de permissões poluído com 1-offs | Convenção de prefixo `CUSTOM_<slug>_...` |
| Código compartilhado virando minefield de `if`s por cliente | Isolamento em pasta própria (§6) |
| Ninguém lembra quem pagou o quê na hora de promover | Tabela `customizacao_solicitada` + auditoria (§2, §8) |
| Cobrança sem rastro contábil claro | Reaproveita `Subscription` (já integrado a Asaas/faturamento) em vez de cobrança "por fora" |

## 10. Decisões já tomadas (31/08/2026)

- Modelo de cobrança: **depende do caso** (única ou recorrente, negociado por pedido) — não é
  regra fixa do sistema, é escolha comercial por cliente.
- Política de promoção: **sem reembolso retroativo** — só encerra cobrança recorrente dali pra
  frente; cobrança única não é mexida.

## 11. Em aberto / próximos passos

- Schema exato (migration Liquibase, DTOs, endpoints) — desenhar só quando o primeiro pedido real
  de customização acontecer, não antes.
- Critério de promoção continua deliberadamente qualitativo — não codificar um threshold automático
  a menos que o volume de pedidos justifique depois.
