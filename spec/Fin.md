# Spec Funcional — Módulo Financeiro do ERP
## Documento Consolidado

**Versão:** 12.0 — revisão fiscal/AP-AR (crédito Simples, IS na base, redução de alíquota, split; retenções, estorno, alçada, dunning, PIX, estabelecimento)
**Stack:** Spring Boot (Java) · PostgreSQL · Angular
**Schema financeiro:** `financeiro` · `fiscal` · `contabil`
**Última atualização:** Julho 2026

---

## Índice

1. [Visão Geral e Arquitetura](#1)
2. [Premissas Globais](#2)
3. [Decisões Arquiteturais](#3)
4. [Fundação Transversal](#fundacao)
5. [Módulo I — Motor Fiscal e Reforma Tributária](#modulo-i)
6. [Módulo II — Contas a Pagar e Contas a Receber](#modulo-ii)
7. [Módulo III — Fluxo de Caixa e Conciliação Bancária](#modulo-iii)
8. [Módulo IV — Tesouraria e Emissão de Boletos](#modulo-iv)
9. [Módulo V — Contabilidade e GL](#modulo-v)
10. [Módulo VI — Análises Gerenciais e Relatórios](#modulo-vi)
11. [Mapeamento dos Diagramas de Arquitetura](#diagramas)
12. [Plano de Implementação](#implementacao)
13. [Arquitetura de Software](#arquitetura)
14. [Roadmap Reforma Tributária](#roadmap)
15. [Maturidade do Documento](#maturidade)

---

## 1. Visão Geral e Arquitetura

### 1.1 Escopo do Módulo Financeiro

| Sub-módulo | Status |
|---|---|
| RBAC / Permissões | ✅ No auth service (externo) |
| Feriados Bancários | ✅ Especificado |
| Audit Log | ✅ Especificado |
| Centro de Custo | ✅ Especificado |
| Contrato NF-e → Financeiro | ✅ Especificado |
| Módulo Fiscal (IBS/CBS) | ✅ Início especificado |
| Contabilidade / GL | ✅ Início especificado |
| Contas a Pagar | ✅ Especificado |
| Contas a Receber | ✅ Especificado |
| Adiantamento | ✅ Especificado |
| Compensação entre contas | ✅ Especificado |
| Empréstimo / Leasing | ✅ Especificado |
| Fluxo de Caixa | ✅ Especificado |
| Conciliação Bancária (OFX) | ✅ Especificado |
| Controle de Conta Corrente | ✅ Especificado |
| Orçamento Financeiro | ✅ Especificado |
| Tesouraria — Boletos | ✅ Especificado |
| Tesouraria — CNAB 240/400 | ✅ Especificado |
| Tesouraria — DDA | ✅ Especificado |
| Tesouraria — Cheques | ✅ Especificado |
| Tesouraria — Aplicações Financeiras | ✅ Especificado |
| Análises Gerenciais e Relatórios | ✅ Especificado |
| Plano de Contas | ✅ Template oficial (elenco base) editável pelo tenant — sem bloqueio |
| SPED / DCTFWeb / Decl. IBS/CBS | ⏳ Roadmap fases 5–6 |
| Módulo Fiscal legado (ICMS/ISS) | ⏳ Spec separado |

### 1.2 Ordem Arquitetural e Dependências

| Ordem | Módulo | Por quê |
|---|---|---|
| 1º | **Módulo I** — Motor Fiscal | Fundação. Calcula IBS/CBS antes de qualquer título |
| 2º | **Módulo II** — AP/AR | Consome resultado do motor. Todas as operações dependem do título |
| 3º | **Módulo III** — Fluxo de Caixa / Conciliação | Depende de títulos e contas correntes |
| 4º | **Módulo IV** — Tesouraria | Depende de conta corrente (III) e AR (II) |
| 5º | **Módulo V** — Contabilidade / GL | Consome eventos de todos os anteriores |
| 6º | **Módulo VI** — Análises | Lê de tudo — não escreve nada |

```
[Motor Fiscal — I]
    ↓ preenche titulo.impostos JSONB
[AP / AR — II]
    ↓ titulo_baixa → cria automaticamente
[Fluxo de Caixa / Conciliação — III]
    ↓ confirma baixas planejadas
[Tesouraria — IV]
    ↓ boleto → CNAB → baixa automática
[Contabilidade / GL — V]
    ↑ consome eventos de todos acima
[Análises — VI]
    ↑ lê de todos — não escreve
```

### 1.3 Separação Billing Service vs. Módulo Financeiro

| Camada | Responsável | O que faz |
|---|---|---|
| **billing-service** | Infra SaaS | Planos, trials, assinaturas, comissões de parceiros |
| **financeiro-service** | Produto ERP | AP/AR, fluxo de caixa, boletos, conciliação dos tenants |

Os dois nunca se comunicam diretamente.

---

## 2. Premissas Globais

- Toda entidade do schema `financeiro` tem `tenant_id` — isolamento multi-tenant completo.
- `tenant_id` não tem FK declarada para o schema principal — integridade garantida pela aplicação (mesmo padrão do billing service).
- Usuário logado (`user_name`) é sempre rastreado em campos `created_by` / `updated_by`.
- Moeda: BRL. Valores em `NUMERIC(15,2)`.
- Datas armazenadas como `DATE` (sem hora), exceto campos de auditoria (`TIMESTAMPTZ`).
- Saldos nunca são armazenados como campo — sempre calculados sobre movimentações confirmadas.
- Operações críticas (transferências, geração de nosso_número) usam transações com lock.
- Todos os parsers de arquivo (OFX, CNAB, DDA) são **idempotentes** — reimportar não duplica.

---


---


---

## 3. Decisões Arquiteturais Registradas

> Não reabrir sem análise de impacto em cascata.

| Decisão | Escolha | Impacto |
|---|---|---|
| Schema fiscal | `fiscal` (não `tax`) | Todos os 16 migrations do Módulo I usam `fiscal.*`. A conversa de reforma tributária usou `tax` — ignorar |
| Changelog Liquibase | Separado por módulo no `liquibase-service` | `db-changelog/financeiro/`, `db-changelog/fiscal/`, `db-changelog/contabil/` |
| Multi-tenancy | `BaseTenantEntity` + `TenantFilterAspect` | Toda entidade nova estende `BaseTenantEntity`. Filtro via AOP em todos os services e repositories |
| RBAC | No `auth-service` com granularidade por operação | O `financeiro-service` não tem tabelas de permissão. Lê `permissions[]` do JWT propagado pelo `gateway` |
| Plano de contas | Template versionado (base: elenco oficial, `spec/elenco-de-contas-contabil.pdf`) copiado na ativação; tenant edita livremente contas sem lançamento | Sem bloqueio — regras de imutabilidade (§F6.2) protegem o histórico |
| JWT / Auth | `gateway` valida — `financeiro-service` recebe headers propagados | Não chamar `auth-service` direto do financeiro |
| Serviços separados | `financeiro-service` + `fiscal-service` + `contabil-service` | Decisão pendente: começar dentro do financeiro, extrair quando necessário |
| `user_account.id` | UUID (não BIGINT) | `audit_log.user_id` deve ser `UUID` |

---

---

## FUNDAÇÃO TRANSVERSAL

> Pré-requisitos para todos os módulos. Implementar antes de qualquer sprint funcional.

## F1. Feriados Bancários

Necessário para: cálculo de `data_vencimento` via `forma_pagamento` (quando `considera_dias_uteis = TRUE`), conciliação automática (tolerância de ±3 dias úteis) e alertas de boleto.

```sql
financeiro.feriado_bancario
─────────────────────────────────────────────
id          BIGSERIAL PK
data        DATE NOT NULL
descricao   VARCHAR(200) NOT NULL
tipo        VARCHAR(15) NOT NULL   -- 'NACIONAL' | 'ESTADUAL' | 'MUNICIPAL'
uf          VARCHAR(2)             -- preenchido se tipo = ESTADUAL ou MUNICIPAL
ibge_municipio VARCHAR(7)          -- preenchido se tipo = MUNICIPAL
created_at  TIMESTAMPTZ NOT NULL
UNIQUE (data, tipo, uf, ibge_municipio)
```

**Seed inicial:** feriados nacionais fixos (Carnaval calculado dinamicamente, não como seed).

**Cálculo de dia útil:**
```java
public LocalDate proximoDiaUtil(LocalDate data, String uf) {
    while (data.getDayOfWeek() == SATURDAY
        || data.getDayOfWeek() == SUNDAY
        || feriadoRepository.isFeriado(data, uf)) {
        data = data.plusDays(1);
    }
    return data;
}
```

**Endpoint de gestão:** `GET/POST/DELETE /api/financeiro/feriados` — admin pode adicionar feriados municipais específicos do tenant.

---

## F2. Audit Log

Rastreabilidade exigida pelo fisco para qualquer alteração em dados financeiros. A Receita Federal pode solicitar histórico completo de alterações em títulos, baixas e apurações.

```sql
financeiro.audit_log
─────────────────────────────────────────────
id              BIGSERIAL PK
tenant_id       BIGINT NOT NULL
tabela          VARCHAR(50) NOT NULL    -- 'titulo' | 'titulo_baixa' | 'lancamento' ...
registro_id     BIGINT NOT NULL        -- id do registro alterado
operacao        VARCHAR(10) NOT NULL   -- 'INSERT' | 'UPDATE' | 'DELETE'
campos_antes    JSONB                  -- valores anteriores (null em INSERT)
campos_depois   JSONB                  -- valores novos (null em DELETE)
actor_user_id   UUID NOT NULL          -- user_account.id é UUID
user_nome       VARCHAR(100) NOT NULL
ip_origem       VARCHAR(45)
created_at      TIMESTAMPTZ NOT NULL

INDEX idx_audit_tabela_registro (tenant_id, tabela, registro_id)
INDEX idx_audit_user            (tenant_id, actor_user_id)
INDEX idx_audit_data            (tenant_id, created_at)
```

**Tabelas que obrigatoriamente geram audit log:**
`titulo`, `titulo_baixa`, `titulo_ajuste`, `compensacao`, `lancamento`, `apuracao_mensal`, `periodo` (fechamento), `boleto`.

**Implementação sugerida:** `@EntityListeners` do JPA com `AuditListener` interceptando `@PostPersist`, `@PostUpdate`, `@PreRemove`. Não usar triggers de banco — dificulta testes.

**Retenção:** mínimo 5 anos conforme legislação fiscal brasileira. Implementar soft-delete ou archiving automático após esse prazo.

---

## F3. Centro de Custo

O centro de custo é modelado como **entidade própria** (`financeiro.centro_custo`) — dimensão analítica por departamento/projeto referenciada por `titulo`, `titulo_baixa`, `conta_movimentacao` e `lancamento_partida` (`titulo_ajuste` não tem coluna própria: herda o centro de custo do título). Sem essa entidade, os relatórios gerenciais ficam sem dimensão analítica.

```sql
financeiro.centro_custo
─────────────────────────────────────────────
id                BIGSERIAL PK
tenant_id         BIGINT NOT NULL
codigo            VARCHAR(30) NOT NULL
descricao         VARCHAR(200) NOT NULL
centro_pai_id     BIGINT REFERENCES centro_custo   -- hierarquia opcional; serviço valida ausência de ciclo
nivel             INT NOT NULL DEFAULT 1           -- derivado do pai — mantido pelo serviço, não editável na tela
aceita_lancamento BOOLEAN DEFAULT TRUE             -- analítico (folha) = TRUE; sintético (agrupador) = FALSE
                                                   -- mesmo conceito de contabil.conta.aceita_lancamento
ativo             BOOLEAN DEFAULT TRUE
created_at        TIMESTAMPTZ NOT NULL
created_by        VARCHAR(100) NOT NULL
updated_at        TIMESTAMPTZ
updated_by        VARCHAR(100)
UNIQUE (tenant_id, codigo)
```

```sql
financeiro.centro_custo_rateio
─────────────────────────────────────────────
id               BIGSERIAL PK
tenant_id        BIGINT NOT NULL
nome             VARCHAR(100) NOT NULL         -- ex: 'Rateio Comercial'
ativo            BOOLEAN DEFAULT TRUE
created_at       TIMESTAMPTZ NOT NULL
```

```sql
financeiro.centro_custo_rateio_item
─────────────────────────────────────────────
id               BIGSERIAL PK
tenant_id        BIGINT NOT NULL
rateio_id        BIGINT NOT NULL REFERENCES centro_custo_rateio
centro_custo_id  BIGINT NOT NULL REFERENCES centro_custo
percentual       NUMERIC(5,2) NOT NULL CHECK (percentual > 0)
UNIQUE (rateio_id, centro_custo_id)
-- Regras (service): SUM(percentual) por rateio_id = 100;
-- só entram CCs ativos com aceita_lancamento = TRUE
```

**Impacto em entidades existentes:** adicionar `centro_custo_id BIGINT` (nullable) em `titulo`, `titulo_baixa`, `conta_movimentacao` e `lancamento_partida`. Em `titulo`, adicionar também `rateio_id BIGINT` (nullable, REFERENCES `centro_custo_rateio`) — **mutuamente exclusivo** com `centro_custo_id` (CHECK: no máximo um dos dois preenchido).

**Onde o rateio é aplicado:** o título carrega a referência (`rateio_id`); a **explosão percentual acontece na contabilização** — o lançamento gera N `lancamento_partida`, uma por centro de custo do rateio, conforme os percentuais vigentes na data do lançamento. Relatórios gerenciais por CC leem as `lancamento_partida` **já explodidas** quando o título está contabilizado (o rateio pode ser editado depois — re-explodir com percentuais atuais divergiria do razão); só re-explodem ao vivo para títulos ainda **não contabilizados** (visão prospectiva). Baixas herdam a referência do título (CC direto ou rateio); rateio em `conta_movimentacao` avulsa não é suportado — usar CC direto.

---

## F4. Contrato de Integração NF-e → Financeiro via Kafka

Define o contrato dos eventos Kafka publicados pelo módulo fiscal e consumidos pelo financeiro. Kafka foi escolhido sobre Feign pelos seguintes motivos:
- Retry automático com backoff — sem implementar circuit breaker manual
- DLQ nativa — o projeto já tem `consumer_error_log` implementado
- Financeiro-service não depende do cadastro-service estar disponível
- Evento fica no tópico até ser consumido com sucesso — sem perda de dados

**Decisão de payload:** o evento carrega todos os campos necessários para o motor fiscal (`ncm`, `regime_empresa`, `ibge_destino`). O financeiro-service não precisa chamar o cadastro-service — sem Feign, sem acoplamento síncrono.

---

### F4.1 Tópicos Kafka

| Tópico | Publicado por | Consumido por | Retenção |
|---|---|---|---|
| `nfe.entrada.aprovada` | fiscal-service / emissor NF-e | financeiro-service | 7 dias |
| `nfe.saida.autorizada` | fiscal-service / emissor NF-e | financeiro-service | 7 dias |
| `nfe.cancelada` | fiscal-service / emissor NF-e | financeiro-service | 7 dias |

---

### F4.2 NF de Entrada → Título a Pagar

**Tópico:** `nfe.entrada.aprovada`

**Payload rico — financeiro não precisa chamar cadastro-service:**
```json
{
  "event_id": "uuid-v4",
  "tenant_id": 1,
  "nfe_chave": "35250612345678000195550010000001231234567890",
  "nfe_numero": "000001234",
  "nfe_serie": "001",
  "data_emissao": "2025-06-15",

  "fornecedor_id": 42,
  "fornecedor_pessoa_id": "uuid-da-pessoa",
  "fornecedor_nome": "Fornecedor Exemplo Ltda",
  "fornecedor_cnpj": "12345678000195",
  "fornecedor_regime": "LUCRO_REAL",

  "itens": [
    {
      "produto_id": 10,
      "ncm": "84713012",
      "cst": "000",
      "c_class_trib": 1,
      "regime_diferenciado": "PADRAO",
      "ibge_destino": "3550308",
      "valor": 15000.00
    }
  ],

  "impostos": {
    "ibs": 2643.00,
    "cbs": 1320.00,
    "is": 0.00
  },

  "condicao_pagamento_id": 3,
  "parcelas": [
    { "numero": 1, "vencimento": "2025-07-15", "valor": 5000.00 },
    { "numero": 2, "vencimento": "2025-08-15", "valor": 5000.00 },
    { "numero": 3, "vencimento": "2025-09-15", "valor": 5000.00 }
  ]
}
```

**Consumer:**
```java
@KafkaListener(topics = "nfe.entrada.aprovada", groupId = "financeiro-service")
public void onNfeEntradaAprovada(NfeEntradaAprovadaEvent event) {
    try {
        tituloService.criarDaNfeEntrada(event);
    } catch (Exception e) {
        // consumer_error_log já implementado no projeto — DLQ automática
        consumerErrorLog.registrar("financeiro-service", "nfe.entrada.aprovada", e, event);
        throw e; // devolve para o Kafka fazer retry
    }
}
```

**Ação:** criar N títulos a pagar com `origem = 'NF_ENTRADA'`, `origem_documento_id = nfe_chave`, `pessoa_id = fornecedor_pessoa_id`, `impostos = payload.impostos JSONB`.

> **`pessoa_id` desnormalizado (decisão registrada):** todo fluxo que cria título preenche
> `titulo.pessoa_id` — eventos NF-e trazem `fornecedor_pessoa_id`/`cliente_pessoa_id` no payload;
> lançamento manual, empréstimo, adiantamento, parcelamento e renegociação resolvem
> cliente/fornecedor → `pessoa` na criação (parcelas/renegociações herdam do título de origem).
> Nullable apenas para `terceiro_tipo IN ('FUNCIONARIO','OUTRO')`, que não têm party no cadastro.

---

### F4.3 NF de Saída → Título a Receber

**Tópico:** `nfe.saida.autorizada`

Mesma estrutura de F4.2, substituindo `fornecedor_*` por `cliente_*` (inclusive `cliente_pessoa_id`) e criando título a receber com `origem = 'NF_SAIDA'`.

---

### F4.4 Cancelamento de NF → Cancelar Título

**Tópico:** `nfe.cancelada`

```json
{
  "event_id": "uuid-v4",
  "tenant_id": 1,
  "nfe_chave": "35250612345678000195550010000001231234567890",
  "motivo": "Erro na emissão"
}
```

**Ação:** cancelar títulos vinculados se não houver baixas com `status_baixa = 'REAL'`. Se houver, publicar evento `financeiro.titulo.cancelamento_bloqueado` e alertar operador.

---

### F4.5 Configuração Kafka

```yaml
# application.yml — financeiro-service
spring:
  kafka:
    consumer:
      group-id: financeiro-service
      auto-offset-reset: earliest
      enable-auto-commit: false        # commit manual após sucesso
    listener:
      ack-mode: MANUAL_IMMEDIATE
    retry:
      topic:
        enabled: true
        attempts: 3                    # 3 tentativas antes da DLQ
        delay: 1000                    # 1s entre tentativas
        multiplier: 2.0                # backoff exponencial: 1s, 2s, 4s
        max-delay: 10000               # máximo 10s entre tentativas
```

**Particionamento por tenant_id:**

Usar `tenant_id` como chave de partição garante que todos os eventos do mesmo tenant sempre vão para a mesma partição — o que garante **ordem de processamento por tenant**. Sem isso, um evento `nfe.cancelada` poderia ser processado antes do `nfe.entrada.aprovada` correspondente em partições diferentes, causando bug silencioso.

```java
// Producer — publicar com tenant_id como chave
kafkaTemplate.send(
    "nfe.entrada.aprovada",
    event.getTenantId().toString(),  // chave = tenant_id
    event
);
// Kafka faz hash(tenant_id) % num_partitions automaticamente
// Mesmo tenant → sempre mesma partição → ordem garantida
```

Número de partições configurável:

```yaml
# Começar com 20 — suficiente para MVP com PMEs
# Kafka faz hash(tenant_id) % 20: dois tenants podem cair na mesma
# partição mas a ordem dentro de cada tenant é sempre garantida
kafka:
  topics:
    nfe-entrada-aprovada:
      partitions: 20      # aumentar conforme volume real de tenants
      replication: 3
    nfe-saida-autorizada:
      partitions: 20
      replication: 3
    nfe-cancelada:
      partitions: 20
      replication: 3
```

> **Migração futura para 1 partição por tenant:** quando o volume justificar,
> mudar para `partitions: {num_tenants}` e usar `tenant_id` como chave mantém
> o mesmo código do producer e consumer sem alteração — só o número de
> partições muda no broker. O isolamento total por tenant elimina qualquer
> possibilidade de interferência entre tenants na mesma partição.
> Fazer via `kafka-topics.sh --alter` sem downtime.

**Idempotência:** chave = `event_id` (UUID do payload), persistida em tabela dedicada
**na mesma transação** que cria os títulos — retry após crash parcial reprocessa o evento
inteiro (a transação anterior deu rollback) e evento já processado é ignorado com segurança.
Não usar `existsByOrigemDocumentoId` como chave: uma NF cria N títulos (parcelas) e um crash
no meio deixaria parcelas faltando no retry.

```sql
financeiro.evento_processado
─────────────────────────────────────────────
event_id        UUID PK
tenant_id       BIGINT NOT NULL
topico          VARCHAR(60) NOT NULL
processado_at   TIMESTAMPTZ NOT NULL
```

```java
@Transactional
public void criarDaNfeEntrada(NfeEntradaAprovadaEvent event) {
    // INSERT em evento_processado primeiro — PK duplicada = já processado
    if (!eventoProcessadoRepository.registrar(event.getEventId(), TOPICO)) {
        log.warn("Evento já processado — ignorando. event_id={}", event.getEventId());
        return;
    }
    // ... cria os N títulos na MESMA transação
}
```

---

## F5. Migrations da Fundação Transversal

| # | Arquivo | Conteúdo |
|---|---|---|
| `transversal-001` | `financeiro-feriados.yaml` | `feriado_bancario` + seed feriados nacionais |
| `transversal-002` | `financeiro-audit-log.yaml` | `audit_log` |
| `transversal-003` | `financeiro-centro-custo.yaml` | `centro_custo`, `centro_custo_rateio`, `centro_custo_rateio_item` |
| ~~`transversal-004`~~ | — | **Movida** — o addColumn de `centro_custo_id`/`rateio_id` vive em `financeiro/v1/010`, `012` e `019` + `contabil/v1/008` (§12.4–12.8): as tabelas-alvo ainda não existem no Sprint 1 |


---


## F6. Plano de Contas — Template Versionado

> **Sem bloqueio de contador.** O template usa o elenco de contas oficial como base
> (`spec/elenco-de-contas-contabil.pdf`) e o tenant pode alterar sua cópia livremente
> (respeitando F6.2). Revisão de contador é opcional/qualidade, não pré-requisito.

### F6.1 Por que as regras de imutabilidade importam

O plano de contas é por tenant mas copiado de um template global na ativação. Depois que um tenant tem lançamentos, mudanças de código, tipo ou hierarquia de contas quebram o histórico — por isso as regras de F6.2 travam esses campos quando a conta já tem lançamentos. Como a base é o elenco oficial e o tenant edita a própria cópia, um ajuste futuro do template afeta só tenants novos (template é versionado).

### F6.2 Regras de imutabilidade por estado da conta

| Estado | Pode mudar | Não pode mudar |
|---|---|---|
| Sem lançamentos | Tudo | — |
| Com lançamentos | `descricao`, `ativo` | `codigo`, `tipo`, `natureza`, `conta_pai_id` |
| Com filhos | — | Deletar |

Enforçado no `ContaService`, não no banco:

```java
public void atualizar(Long id, ContaUpdateDTO dto) {
    Conta conta = contaRepository.findById(id).orElseThrow();
    
    boolean temLancamentos = lancamentoPartidaRepository
        .existsByContaId(id);
    
    if (temLancamentos) {
        // só permite mudar descricao e ativo
        conta.setDescricao(dto.getDescricao());
        conta.setAtivo(dto.isAtivo());
    } else {
        // permite tudo
        conta.setCodigo(dto.getCodigo());
        conta.setTipo(dto.getTipo());
        conta.setNatureza(dto.getNatureza());
        conta.setContaPaiId(dto.getContaPaiId());
        conta.setDescricao(dto.getDescricao());
        conta.setAtivo(dto.isAtivo());
    }
    contaRepository.save(conta);
}
```

### F6.3 Entidade de template

```sql
contabil.plano_contas_template
─────────────────────────────────────────────
id                BIGSERIAL PK
versao            INT NOT NULL DEFAULT 1
codigo            VARCHAR(30) NOT NULL
descricao         VARCHAR(200) NOT NULL
tipo              VARCHAR(20) NOT NULL
                  -- 'ATIVO' | 'PASSIVO' | 'PATRIMONIO_LIQUIDO'
                  -- 'RECEITA' | 'CUSTO' | 'DESPESA'
natureza          VARCHAR(10) NOT NULL   -- 'DEVEDORA' | 'CREDORA'
nivel             INT NOT NULL
codigo_pai        VARCHAR(30)            -- referência por código (não por id)
aceita_lancamento BOOLEAN DEFAULT FALSE
ativo             BOOLEAN DEFAULT TRUE
UNIQUE (versao, codigo)
```

Registrar versão usada no tenant:

```sql
-- adicionar em contabil.periodo
template_versao   INT NOT NULL DEFAULT 1
```

### F6.4 Listener de ativação do tenant

```java
@Component
public class TenantAtivacaoListener {

    @EventListener
    @Transactional
    public void onTenantAtivado(TenantAtivadoEvent event) {
        Long tenantId = event.getTenantId();
        int versaoAtiva = templateRepository.findVersaoAtiva();

        List<PlanoContasTemplate> template =
            templateRepository.findByVersaoOrderByNivel(versaoAtiva);

        Map<String, Long> codigoParaId = new HashMap<>();

        for (PlanoContasTemplate t : template) {
            Conta conta = new Conta();
            conta.setTenantId(tenantId);
            conta.setCodigo(t.getCodigo());
            conta.setDescricao(t.getDescricao());
            conta.setTipo(t.getTipo());
            conta.setNatureza(t.getNatureza());
            conta.setNivel(t.getNivel());
            conta.setAceitaLancamento(t.isAceitaLancamento());

            if (t.getCodigoPai() != null) {
                conta.setContaPaiId(codigoParaId.get(t.getCodigoPai()));
            }

            Conta salva = contaRepository.save(conta);
            codigoParaId.put(t.getCodigo(), salva.getId());
        }
    }
}
```

### F6.5 Estrutura do template (base: `spec/elenco-de-contas-contabil.pdf` — validar com contador)

> Estrutura de grupos alinhada ao elenco de contas base: **1 Ativo · 2 Passivo (PL = 2.4)
> · 3 Receitas · 4 Custos · 5 Despesas e Demais Resultados**. Nomenclatura PCLD conforme
> o elenco. Itens setoriais do PDF (pedágio, faixa de domínio etc.) NÃO entram no template.

```
1       ATIVO
1.1       Ativo Circulante
1.1.1       Caixa e Equivalentes de Caixa
1.1.1.01      Caixa Geral / Fundo Fixo             [lançamento]
1.1.1.02      Banco Conta Movimento                [lançamento]
1.1.1.03      Numerário em Trânsito (a depositar)  [lançamento]
1.1.1.04      Aplicações de Liquidez Imediata      [lançamento]
1.1.2       Clientes e Operações a Receber
1.1.2.01      Clientes / Títulos a Receber         [lançamento]
1.1.2.02      Cartões e Meios Eletrônicos          [lançamento]
1.1.2.03      (-) PCLD — Prov. Créd. Liq. Duvidosa [lançamento] (retificadora)
1.1.3       Estoques
1.1.3.01      Mercadorias                          [lançamento]
1.1.4       Despesas Antecipadas
1.1.4.01      Prêmios de Seguros a Apropriar       [lançamento]
1.1.4.99      Outras Despesas Antecipadas          [lançamento]
1.1.5       Outros Créditos
1.1.5.01      Adiantamentos a Fornecedores         [lançamento]
1.1.5.02      Adiantamentos a Funcionários         [lançamento]
1.1.6       Tributos a Recuperar
1.1.6.01      IBS a Recuperar                      [lançamento]
1.1.6.02      CBS a Recuperar                      [lançamento]
1.1.6.03      ICMS a Recuperar (transição)         [lançamento]
1.1.6.04      PIS/COFINS a Recuperar (transição)   [lançamento]
1.1.6.05      Tributos Retidos na Fonte a Recuperar[lançamento]
1.1.6.06      IRPJ/CSLL — Antecipações             [lançamento]
1.2       Ativo Não Circulante
1.2.1       Realizável a Longo Prazo
1.2.1.01      Depósitos Judiciais                  [lançamento]
1.2.2       Investimentos
1.2.2.01      Participações Societárias            [lançamento]
1.2.3       Imobilizado
1.2.3.01      Máquinas e Equipamentos              [lançamento]
1.2.3.02      (-) Depreciação Acumulada            [lançamento] (retificadora)
1.2.4       Intangível
1.2.4.01      Software e Licenças                  [lançamento]
1.2.4.02      (-) Amortização Acumulada            [lançamento] (retificadora)

2       PASSIVO
2.1       Passivo Circulante
2.1.1       Empréstimos e Financiamentos CP
2.1.1.01      Empréstimos Bancários                [lançamento]
2.1.2       Fornecedores e Contas a Pagar
2.1.2.01      Fornecedores e Prestadores           [lançamento]
2.1.2.02      Adiantamentos de Clientes            [lançamento]
2.1.3       Tributos e Contribuições Federais
2.1.3.01      CBS a Recolher                       [lançamento]
2.1.3.02      IS a Recolher                        [lançamento]
2.1.3.03      IRPJ/CSLL a Recolher                 [lançamento]
2.1.3.04      PIS/COFINS a Recolher (transição)    [lançamento]
2.1.3.05      Retenções na Fonte a Recolher (IRRF/CSRF/INSS) [lançamento]
2.1.4       Tributos Estaduais e Municipais
2.1.4.01      IBS a Recolher                       [lançamento]
2.1.4.02      ICMS a Recolher (transição)          [lançamento]
2.1.4.03      ISS a Recolher (transição)           [lançamento]
2.1.5       Obrigações Trabalhistas e Previdenciárias
2.1.5.01      Salários a Pagar                     [lançamento]
2.1.5.02      Encargos Sociais a Recolher          [lançamento]
2.1.5.03      Provisão de Férias e 13º             [lançamento]
2.1.6       Provisões
2.1.6.01      Provisões Diversas                   [lançamento]
2.2       Passivo Não Circulante
2.2.1       Empréstimos e Financiamentos LP
2.2.1.01      Empréstimos Bancários LP             [lançamento]
2.2.2       Provisão para Contingências
2.2.2.01      Contingências Fiscais/Trabalhistas   [lançamento]
2.4       Patrimônio Líquido
2.4.1.01      Capital Social                       [lançamento]
2.4.2.01      Reservas de Capital                  [lançamento]
2.4.3.01      Reservas de Lucros                   [lançamento]
2.4.4.01      Lucros/Prejuízos Acumulados          [lançamento]
2.4.5.01      Ajustes de Avaliação Patrimonial     [lançamento]
2.4.6.01      AFAC — Adiant. p/ Futuro Aumento de Capital [lançamento]

3       RECEITAS
3.1       Receita Bruta
3.1.1.01      Receita Bruta de Vendas              [lançamento]
3.1.1.02      Receita Bruta de Serviços            [lançamento]
3.2       (-) Deduções da Receita
3.2.1.01      (-) Abatimentos e Devoluções         [lançamento]
3.2.2.01      (-) Tributos sobre Vendas (IBS/CBS/transição) [lançamento]
3.3       Outras Receitas
3.3.1.01      Receitas Financeiras                 [lançamento]
3.3.2.01      Outras Receitas Operacionais         [lançamento]

4       CUSTOS
4.1.1.01      CMV — Custo das Mercadorias Vendidas [lançamento]
4.1.2.01      CSP — Custo dos Serviços Prestados   [lançamento]
4.2.1.01      Depreciação e Amortização (custo)    [lançamento]

5       DESPESAS E DEMAIS RESULTADOS
5.1       Despesas Operacionais
5.1.1.01      Despesas com Pessoal                 [lançamento]
5.1.2.01      Serviços de Terceiros                [lançamento]
5.1.3.01      Despesas Administrativas/Gerais      [lançamento]
5.1.4.01      Despesas com Vendas                  [lançamento]
5.1.5.01      Depreciação e Amortização            [lançamento]
5.1.6.01      Despesas com Tributos e Contribuições[lançamento]
5.2       Despesas Financeiras
5.2.1.01      Juros e Encargos                     [lançamento]
5.3       Outras Despesas Operacionais
5.3.1.01      Outras Despesas                      [lançamento]
5.4       IRPJ e CSLL
5.4.1.01      Imposto de Renda e Contrib. Social   [lançamento]
```

> Nota: contas de PL têm `codigo` sob 2.4 mas `tipo = 'PATRIMONIO_LIQUIDO'`; grupo 4 usa
> `tipo = 'CUSTO'` (enum de `tipo` ganha o valor CUSTO). Contas marcadas (retificadora)
> usam o campo `retificadora = true`.

### F6.6 Estratégia de seed

Seed direto com `versao = 1, ativo = true` — a estrutura F6.5 vem do elenco oficial e o tenant pode ajustar a própria cópia. Se uma revisão (interna ou de contador) alterar o template depois, cria-se `versao = 2`: tenants novos recebem a v2, tenants existentes permanecem na versão registrada em `periodo.template_versao`.

---

## F7. Aprovação por Alçada (substituto do BPM Worklist)

Workflow genérico de aprovação para operações financeiras — primeiro consumidor: pagamento
de títulos AP. Genérico por design (`entidade` + `entidade_id`) para servir compras e outros
módulos no futuro.

```sql
financeiro.approval_request
─────────────────────────────────────────────
id              BIGSERIAL PK
tenant_id       BIGINT NOT NULL
entidade        VARCHAR(30) NOT NULL   -- 'TITULO_PAGAMENTO' | 'CNAB_REMESSA' | ...
entidade_id     BIGINT NOT NULL
valor           NUMERIC(15,2) NOT NULL
status          VARCHAR(15) NOT NULL DEFAULT 'PENDENTE'
                -- 'PENDENTE' | 'APROVADO' | 'REJEITADO' | 'ESCALADO' | 'EXPIRADO'
regra_id        BIGINT NOT NULL REFERENCES approval_regra
aprovador_user_id   UUID              -- quem está com a tarefa
justificativa   VARCHAR(500)          -- obrigatória na rejeição
criado_em       TIMESTAMPTZ NOT NULL
decidido_em     TIMESTAMPTZ
decidido_por    UUID
INDEX idx_approval_pendente (tenant_id, status, aprovador_user_id)
```

```sql
financeiro.approval_regra          -- alçadas configuráveis por tenant
─────────────────────────────────────────────
id              BIGSERIAL PK
tenant_id       BIGINT NOT NULL
entidade        VARCHAR(30) NOT NULL
valor_de        NUMERIC(15,2) NOT NULL      -- ex: 0
valor_ate       NUMERIC(15,2)               -- null = sem teto
papel_aprovador VARCHAR(50) NOT NULL        -- role RBAC do auth-service (ex: FIN_GERENTE, FIN_DIRETOR)
timeout_dias    INT                         -- null = sem timeout
acao_timeout    VARCHAR(20)                 -- 'ESCALAR' | 'APROVAR_AUTO'
escalar_para    VARCHAR(50)                 -- papel do superior quando ESCALAR
ativo           BOOLEAN DEFAULT TRUE
```

Exemplo de configuração: abaixo de R$ 5.000 aprova `FIN_GERENTE`; acima, `FIN_DIRETOR`.

**Fluxo:**
1. Baixa/remessa de valor que casa com uma `approval_regra` → cria `approval_request`
   e o título fica retido (`bloqueado = TRUE`, `motivo_bloqueio = 'AGUARDANDO_APROVACAO'`).
2. Notificação por e-mail/push ao papel aprovador; tela **Aprovações Pendentes** com
   filtros por valor / tipo / data.
3. Aprovador decide no sistema (vê valor, centro de custo, tipo de despesa):
   - **Aprovado** → desbloqueia e segue o fluxo.
   - **Rejeitado** → devolve com justificativa obrigatória.
   - **Timeout sem ação** → conforme `acao_timeout`: escala automaticamente pro papel
     superior ou aprova automaticamente (parametrizado).
4. **Audit trail imutável**: toda decisão registrada em `audit_log` (quem, quando, IP) —
   `approval_request` nunca é deletada nem editada após decisão.

**Endpoints:** `GET /api/financeiro/aprovacoes/pendentes` · `PATCH /api/financeiro/aprovacoes/{id}/aprovar` · `PATCH /api/financeiro/aprovacoes/{id}/rejeitar`

---


---


---
---

## MÓDULO I — MOTOR FISCAL E REFORMA TRIBUTÁRIA

> ⚠️ Validar com especialista fiscal antes de produção. Alíquotas, CSTs e listas de NCM precisam de confirmação com LC 214/2025 e NTs da Receita Federal.

### 1.1 Visão Geral

O Motor Fiscal é o núcleo de cálculo tributário. Determina, para cada operação, quais tributos incidem e com quais alíquotas, gravando o resultado em `titulo.impostos JSONB`. Durante a transição 2026–2033 dois regimes coexistem: ICMS/ISS/PIS/Cofins (extinguindo progressivamente) e IBS/CBS/IS (crescendo progressivamente).

### 1.2 Cronograma de Transição

| Ano | Mudança | Impacto no ERP |
|---|---|---|
| **2026** | CBS 0,9% + IBS 0,1% destacados nas NFs (piloto) | Campos IBS/CBS obrigatórios na NF-e. Campo `impostos JSONB` já reservado |
| **2027** | PIS/Cofins extintos. CBS alíquota cheia (~8,8%). IS criado. Split payment começa | `tipo_baixa.meio = 'SPLIT_PAYMENT'` entra em produção |
| **2028** | CBS em regime normal. ICMS/ISS ainda vigentes | Dois regimes simultâneos na apuração |
| **2029–2032** | ICMS/ISS reduzidos progressivamente | Alíquotas de transição atualizadas anualmente |
| **2033** | ICMS/ISS extintos. Sistema 100% IBS + CBS | Limpeza de campos legados |

### 1.3 Entidades (schema `fiscal`)

```sql
fiscal.config_empresa          -- 1 linha por tenant (regime, CRT, CNPJ, IE)
fiscal.ncm                     -- ~10.500 NCMs com regime diferenciado e IS
fiscal.cst_ibs_cbs             -- Códigos CST do novo IVA
fiscal.cfop                    -- ~600 CFOPs com flags gera_credito_ibs/cbs
fiscal.aliq_cbs_regime         -- Alíquota CBS por regime e ano (2026-2033)
fiscal.aliq_ibs_municipio      -- Alíquota IBS por ibge_municipio e ano
fiscal.aliq_is_ncm             -- Alíquota IS por NCM
fiscal.regime_dif_ncm          -- NCMs com cesta básica, redução 60%, monofásico
fiscal.vigencia_tributo        -- Fases da transição 2026→2033
fiscal.operacao_fiscal         -- Resultado do cálculo por operação (persiste)
fiscal.apuracao_mensal         -- Apuração consolidada por tenant × competência
```

## 1.4 Motor Fiscal — Lógica de Cálculo Detalhada

> ⚠️ Validar com especialista fiscal antes de ir a produção. Códigos CST e listas de NCM precisam de confirmação com o texto da LC 214/2025 e NTs da Receita Federal.

---

### 1.4.1 Hierarquia de Regras — Ordem de Precedência

O motor fiscal resolve conflitos de alíquota e regime pela seguinte hierarquia (maior número = maior prioridade):

```
1. Regra geral    → alíquota padrão IBS + CBS do ano
2. NCM            → regime diferenciado definido em fiscal.ncm
3. Operação       → CFOP pode zerar crédito (ex: brinde)
4. Produto        → aliquota_is_override sobrescreve NCM
5. Regime empresa → Simples Nacional: sem crédito para o comprador
```

Exemplo: produto com NCM de cesta básica (alíquota zero) vendido por empresa do Simples = alíquota zero prevalece independente do regime.

---

### 1.4.2 Fluxo Completo de Cálculo — Saída (NF-e / NFC-e)

```
ENTRADA
  └── cfop, ncm, ibge_destino, valor_item,
      regime_empresa, data_competencia

PASSO 1 — Validar CFOP
  ├── CFOP não encontrado → lançar FiscalException("CFOP_NAO_ENCONTRADO")
  └── CFOP.tipo_operacao ≠ 'SAIDA' → lançar FiscalException("CFOP_INVALIDO_SAIDA")

PASSO 2 — Verificar tributação do produto
  ├── NCM não encontrado → warning, usar alíquota padrão sem regime diferenciado
  ├── ncm.cesta_basica = TRUE → IBS = 0, CBS = 0, IS = 0 → FIM
  └── ncm.monofasico = TRUE:
        ├── emitente é o fabricante/importador (1ª etapa da cadeia — identificado pelo
        │   CFOP de produção própria/importação) → calcular IS (Passo 4) e IBS/CBS sobre
        │   a base com IS (Passos 5–7) — recolhimento concentrado na origem (ver Exemplo 3)
        └── demais etapas (distribuidor/varejo) → IBS = 0, CBS = 0, IS = 0
            (já recolhidos na origem) → FIM

PASSO 3 — Buscar alíquotas vigentes pela data_competencia
  ├── aliq_ibs = AliquotaIbsProvider.get(ibge_destino, ano_competencia)
  │     ├── Cache hit → retornar cached
  │     └── Cache miss → buscar fiscal.aliq_ibs_municipio, armazenar cache TTL 7 dias
  │           └── Não encontrado → lançar FiscalException("MUNICIPIO_SEM_ALIQUOTA_IBS")
  └── aliq_cbs = AliquotaCbsProvider.get(regime_empresa, ano_competencia)
        └── Cache miss → buscar fiscal.aliq_cbs_regime, armazenar cache TTL 7 dias
              └── Não encontrado → lançar FiscalException("REGIME_SEM_ALIQUOTA_CBS")

  -- TTL de 7 dias justificado: alíquotas IBS mudam no máximo uma vez por ano
  -- (publicação CGIBS) e CBS com legislação, o que é ainda mais raro. TTL de
  -- 24h reconstruiria o cache 365x/ano sem necessidade. Com 7 dias, uma correção
  -- de alíquota entra no sistema em até uma semana sem deploy ou invalidação manual.

PASSO 4 — Calcular IS (Imposto Seletivo) — ANTES do IBS/CBS
  ├── Verificar fiscal.aliq_is_ncm para o NCM
  ├── Se produto.aliquota_is_override IS NOT NULL → usar override
  ├── Se NCM sujeito ao IS:
  │     valor_is = valor_item × aliquota_is / 100
  │     (IS incide sobre o valor bruto, sem redução)
  └── Senão: valor_is = 0

PASSO 5 — Calcular base e alíquotas efetivas do IBS/CBS
  ├── base = valor_item + valor_is
  │     (o IS INTEGRA a base de cálculo do IBS e da CBS — LC 214/2025)
  └── Regimes diferenciados reduzem a ALÍQUOTA, não a base:
        aliq_ibs_efetiva = aliq_ibs × (1 - reducao/100)
        aliq_cbs_efetiva = aliq_cbs × (1 - reducao/100)
        → preencher p_red_ibs / p_red_cbs (§1.8.4) — a NF-e exige o
          percentual de redução de alíquota, e base cheia no XML

PASSO 6 — Calcular IBS
  valor_ibs_estadual  = base × aliq_ibs_efetiva.aliquota_estadual / 100
  valor_ibs_municipal = base × aliq_ibs_efetiva.aliquota_municipal / 100
  valor_ibs           = valor_ibs_estadual + valor_ibs_municipal

PASSO 7 — Calcular CBS
  valor_cbs = base × aliq_cbs_efetiva.aliquota_pct / 100

PASSO 8 — Calcular split payment (se aplicável a partir de 2027)
  ├── condicao_pagamento.split_payment_aplicavel = TRUE?
  ├── E vigencia_tributo.split_payment_ativo = TRUE para o ano?
  ├── Sim: modelo "split inteligente" da regulamentação —
  │         o arranjo de pagamento segrega NO MÁXIMO o saldo devedor do
  │         fornecedor (considerando seus créditos apurados); excedente
  │         devolvido em D+3. Valores de referência calculados pelo motor:
  │         valor_split_ibs = valor_ibs, valor_split_cbs = valor_cbs
  │         (teto — o valor efetivamente segregado vem da liquidação)
  └── Não: valor_split_ibs = 0, valor_split_cbs = 0

  -- Split aplica-se a instrumentos de pagamento ELETRÔNICOS, incluindo
  -- PIX, cartão e BOLETO (liquidação via arranjo de pagamento).
  -- Fora do split: dinheiro e cheque.
  -- ⚠️ Modelo simplificado — revisar contra a regulamentação do CGIBS/RFB
  --    antes de 2027. Percentuais e mecânica são PARAMETRIZADOS (§1.9).

PASSO 9 — Regime atual (transição — até extinção em 2033)
  └── Calcular ICMS/ISS/PIS/Cofins conforme regime vigente
      (lógica separada — spec do módulo fiscal legado)

PASSO 10 — Gravar resultado
  → INSERT fiscal.operacao_fiscal com todos os valores calculados
  → UPDATE financeiro.titulo.impostos JSONB com resumo

SAÍDA
  └── OperacaoFiscalDTO com valores IBS, CBS, IS, split, créditos
```

---

### 1.4.3 Fluxo Completo de Cálculo — Entrada (NF-e de Compra / CT-e)

```
PASSO 1 a 8 → idêntico ao fluxo de saída

PASSO 9 — Verificar direito a crédito (regime do FORNECEDOR determina o crédito)
  ├── cfop.gera_credito_ibs = FALSE → credito_ibs = 0
  ├── cfop.gera_credito_cbs = FALSE → credito_cbs = 0
  ├── regime_fornecedor = 'MEI' → credito_ibs = 0, credito_cbs = 0
  ├── regime_fornecedor = 'SIMPLES_NACIONAL' (recolhe IBS/CBS dentro do DAS):
  │     credito_ibs / credito_cbs = montante EQUIVALENTE ao efetivamente
  │     cobrado dentro do Simples (crédito reduzido — LC 214/2025;
  │     lógica herdada do art. 23 da LC 123)
  ├── regime_fornecedor = 'SIMPLES_NACIONAL' com pessoa.ibs_cbs_por_fora = TRUE
  │     (optante que recolhe IBS/CBS pelo regime regular, fora do DAS):
  │     credito_ibs = valor_ibs, credito_cbs = valor_cbs (crédito integral)
  └── Demais regimes (Lucro Real, Lucro Presumido):
        credito_ibs = valor_ibs (crédito integral do destacado)
        credito_cbs = valor_cbs

PASSO 10 — Acumular na apuração mensal
  → apuracao_mensal.creditos_ibs += credito_ibs
  → apuracao_mensal.creditos_cbs += credito_cbs
```

---

### 1.4.4 Regimes Diferenciados por NCM

> O enum canônico dos regimes diferenciados é o mapeamento por Anexo da LC 214/2025
> em **§1.8.5** (`ANEXO_I_ZERO` … `ANEXO_XI_60`, `MONOFASICO`, `ISENTO`, `IMUNE`, `ZFM`).
> A resolução NCM → regime usa match por prefixo mais longo (§1.8-A).
> A redução é sempre de **alíquota** (§1.4.2 Passo 5).

---

### 1.4.5 Casos Especiais

#### Simples Nacional — Saída

Empresa do Simples que vende para pessoa jurídica: destaca IBS e CBS normalmente na NF-e (o comprador pode ou não tomar crédito dependendo do seu regime). A empresa do Simples **recolhe** IBS e CBS mas em alíquota reduzida calculada sobre a receita bruta mensal (não por operação). O motor fiscal calcula o destaque da NF-e, não o recolhimento do Simples.

#### Simples Nacional — Entrada

Empresa do Simples que compra: **não tem direito a crédito** de IBS e CBS (quem apura pelo DAS não aproveita créditos). O valor do imposto destacado na NF-e do fornecedor vai para custo do produto.

#### Comprando DE fornecedor do Simples (comprador no regime regular)

O comprador do regime regular **tem crédito** nas aquisições de optante do Simples: crédito reduzido (montante equivalente ao cobrado dentro do DAS) ou integral quando o fornecedor optou por recolher IBS/CBS por fora do DAS. Ver Passo 9 do fluxo de entrada.

#### MEI — Saída e Entrada

MEI não destaca IBS e CBS nas NF-e. `cfop.gera_credito_ibs = FALSE` e `cfop.gera_credito_cbs = FALSE` para todas as operações de MEI. O motor retorna todos os valores zerados.

#### Serviços (NFS-e)

Serviços substituem ISS por IBS + CBS. O `ibge_destino` para serviços é o **local da prestação** (onde o serviço é executado), não o endereço do tomador. O campo `local_prestacao_ibge` deve ser informado na requisição ao motor.

```json
POST /api/fiscal/calcular
{
  "tipo_operacao": "SAIDA",
  "tipo_documento": "NFSe",
  "cfop": "5933",
  "ncm_ou_servico": "17.11",     ← código LC 116 para NFS-e
  "ibge_destino": "3550308",
  "local_prestacao_ibge": "3550308",
  "valor_operacao": 5000.00,
  "data_competencia": "2025-06-15"
}
```

#### Importação

Produtos importados têm tributação diferente: CBS incide no desembaraço (recolhida pelo importador), IBS incide conforme destino final. O motor fiscal marca `origem_produto = 'ESTRANGEIRO'` e aplica as regras da LC 214/2025 para importação.

#### Devolução

CFOP de devolução (ex: 1411, 2411 para entradas / 5411, 6411 para saídas) gera operação fiscal com valores negativos — anula o débito ou crédito original.

```java
// Devolução de venda: CFOP 5411
// valor_ibs = -1 × valor_ibs_original
// credito_ibs = -1 × credito_ibs_original (reduz crédito)
```

**Vínculo com o financeiro (evento `nfe.devolucao.autorizada` — mesma estrutura de F4.2 + `nfe_chave_referenciada`):**

1. Localizar títulos com `origem_documento_id = nfe_chave_referenciada`.
2. Título(s) `EM_ABERTO` com saldo suficiente → criar `titulo_ajuste` de **DESCONTO**
   (categoria `DEVOLUCAO`) no valor devolvido, reduzindo o saldo. Devolução total → título cancelado.
3. Título já `BAIXADO` (ou saldo insuficiente) → criar **título de crédito** na natureza
   oposta (`origem = 'DEVOLUCAO'`) em favor do terceiro, elegível para compensação (§II.5).
4. Parcial ou total, o rateio segue a ordem: parcelas de vencimento mais distante primeiro.

---

### 1.4.6 NF com Múltiplos Itens

O motor fiscal é chamado **por item**, não por NF. A NF é a soma dos resultados de cada item.

```java
// NF com 3 produtos, NCMs diferentes
List<OperacaoFiscalDTO> resultados = itens.stream()
    .map(item -> motorFiscalService.calcular(
        MotorFiscalRequest.builder()
            .cfop(nfe.getCfop())
            .ncm(item.getNcm())
            .ibgeDestino(nfe.getDestinatario().getIbgeMunicipio())
            .valorOperacao(item.getValorTotal())
            .dataCompetencia(nfe.getDataEmissao())
            .regimeEmpresa(configEmpresa.getRegimeTributario())
            .build()
    ))
    .collect(toList());

// Totalizar na NF
BigDecimal totalIbs = resultados.stream()
    .map(OperacaoFiscalDTO::getValorIbs)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

---

### 1.4.7 Zona Franca de Manaus (ZFM) ⚠️

⚠️ Caso altamente específico — validar com especialista antes de implementar.

Produtos industrializados na ZFM têm benefício fiscal preservado pela LC 214/2025 (art. 124). O `ibge_destino` para produtos da ZFM enviados para fora de Manaus aplica alíquota reduzida de IBS e CBS conforme tabela específica. O campo `origem = 'ZFM'` no cadastro do produto aciona essa regra.

---

### 1.4.8 Exemplos Numéricos

#### Exemplo 1 — Venda de mercadoria padrão (Lucro Real, SP → SP)

```
Produto:      Notebook, NCM 84713012, regime PADRAO
Destinatário: Empresa em São Paulo (ibge: 3550308)
Valor:        R$ 10.000,00
Ano:          2027 (CBS em vigor, ICMS transição)

Alíquota IBS São Paulo 2027:
  Estadual:   13,12% (estimado — validar com CGIBS)
  Municipal:   4,50% (estimado)
  Total:      17,62%

Alíquota CBS Lucro Real 2027: 8,80%

Cálculo:
  base_ibs = 10.000,00
  valor_ibs = 10.000 × 17,62% = R$ 1.762,00
    → ibs_estadual = 10.000 × 13,12% = R$ 1.312,00
    → ibs_municipal = 10.000 × 4,50% = R$   450,00

  base_cbs = 10.000,00
  valor_cbs = 10.000 × 8,80% = R$ 880,00

  IS = 0 (notebook não sujeito ao IS)

  Total tributos novos = R$ 2.642,00
  Crédito para o comprador = R$ 2.642,00 (se Lucro Real ou Presumido)
```

#### Exemplo 2 — Venda de produto da cesta básica

```
Produto:      Arroz tipo 1, NCM 10063021, regime CESTA_BASICA
Valor:        R$ 500,00

  IBS = 0 (cesta básica nacional — alíquota zero)
  CBS = 0
  IS = 0

  Total tributos = R$ 0,00
```

#### Exemplo 3 — Venda de cigarro (Imposto Seletivo)

```
Produto:      Cigarro, NCM 24022000, sujeito_is = true
Alíquota IS:  150% (estimado — validar com LC 214/2025)
Valor:        R$ 100,00

  IS  = 100 × 150%   = R$ 150,00 (recolhido pelo fabricante)
  base IBS/CBS = 100 + 150 = R$ 250,00 (IS integra a base — LC 214/2025)
  IBS = 250 × 17,62% = R$ 44,05 (monofásico: = 0 no restante da cadeia)
  CBS = 250 × 8,80%  = R$ 22,00 (monofásico: = 0)

  No distribuidor e varejista: IBS = 0, CBS = 0 (monofásico)
```

#### Exemplo 4 — Serviço com redução de 60% (saúde)

```
Serviço:      Consulta médica, código LC116 4.01
Valor:        R$ 300,00
Regime:       REDUCAO_60

  base = R$ 300,00 (base cheia — a redução é de alíquota)
  aliq_ibs_efetiva = 17,62% × (1 - 60%) = 7,048%
  aliq_cbs_efetiva =  8,80% × (1 - 60%) = 3,52%
  valor_ibs = 300 × 7,048% = R$ 21,14
  valor_cbs = 300 × 3,52%  = R$ 10,56
  IS = 0   (p_red_ibs = p_red_cbs = 60 na NF-e)

  Total = R$ 31,70 (vs R$ 79,14 sem redução)
```

---

### 1.4.9 Tratamento de Erros do Motor Fiscal

| Código de Erro | Causa | Ação |
|---|---|---|
| `CFOP_NAO_ENCONTRADO` | CFOP não existe na tabela | Bloquear emissão — CFOP inválido |
| `MUNICIPIO_SEM_ALIQUOTA_IBS` | ibge_destino sem alíquota cadastrada | Fallback PARAMETRIZADO (§1.9): usa alíquota estadual da UF e **zera a parcela municipal** (mesmo comportamento do Oracle EBS hoje) + alerta. Valores do fallback em `fiscal.parametro_fiscal` — atualizável sem deploy |
| `REGIME_SEM_ALIQUOTA_CBS` | Regime tributário sem alíquota para o ano | Fallback parametrizado (§1.9, default: alíquota do Lucro Real) + alerta |
| `NCM_NAO_ENCONTRADO` | NCM não cadastrado | Warning — calcular com regime PADRAO + alerta |
| `VIGENCIA_SEM_COBERTURA` | Data fora do cronograma de transição | Erro crítico — cronograma deve cobrir 2026–2033 |
| `SPLIT_SEM_FORMA_PAGAMENTO` | Split payment sem forma de pagamento eletrônico | Ignorar split — dinheiro e cheque não suportam (boleto SUPORTA) |

---

### 1.4.10 Interface do MotorFiscalService

```java
@Service
public class MotorFiscalService {

    /**
     * Calcula impostos para uma operação fiscal.
     * Determinístico — mesmos inputs sempre produzem mesmo output.
     * Sem side effects — não persiste nada, apenas calcula.
     */
    public OperacaoFiscalDTO calcular(MotorFiscalRequest request) { ... }

    /**
     * Calcula e persiste — usado na aprovação de NF-e entrada/saída.
     * Persiste em fiscal.operacao_fiscal e atualiza titulo.impostos JSONB.
     */
    @Transactional
    public OperacaoFiscalDTO calcularEPersistir(
            MotorFiscalRequest request,
            Long tituloId
    ) { ... }

    /**
     * Recalcula operações de um período — usado após atualização de alíquotas.
     * Executa de forma assíncrona via cnabExecutor.
     */
    @Async("cnabExecutor")
    public CompletableFuture<RecalculoResultado> recalcularPeriodo(
            Long tenantId,
            String competencia
    ) { ... }
}
```

```java
@Builder
public class MotorFiscalRequest {
    private String cfop;
    private String ncm;                    // null para serviços
    private String codigoServico;          // código LC 116 — null para produtos
    private String ibgeDestino;            // município do destinatário
    private String ibgeLocalPrestacao;     // apenas para serviços
    private BigDecimal valorOperacao;
    private LocalDate dataCompetencia;
    private String regimeEmpresa;          // regime do emitente
    private String origemProduto;          // 'NACIONAL' | 'ESTRANGEIRO' | 'ZFM'
    private Boolean splitPaymentAplicavel; // da condicao_pagamento
    private String tipoDocumento;          // 'NFe' | 'NFCe' | 'NFSe' | 'CTe'
}
```


### 1.6 Regras de Negócio

| # | Regra |
|---|---|
| MF-01 | Alíquota IBS determinada pelo `ibge_destino` (município do destinatário) — nunca pelo emitente |
| MF-02 | MEI não gera crédito de IBS/CBS para o comprador. Fornecedor do Simples gera crédito REDUZIDO (montante equivalente ao cobrado no DAS) ou INTEGRAL se optar por recolher IBS/CBS por fora do DAS (`pessoa.ibs_cbs_por_fora`) |
| MF-03 | NCM cesta básica nacional → alíquota zero. NCM monofásico → sem crédito na cadeia |
| MF-04 | IS não é compensável — recolhido integralmente pelo fabricante/importador |
| MF-05 | Split payment se aplica a pagamentos eletrônicos — PIX, cartão e boleto (liquidação via arranjo). Fora: dinheiro e cheque. Modelo "split inteligente": segrega até o saldo devedor, excedente devolvido em D+3 |
| MF-06 | Motor fiscal é determinístico — mesmos inputs, mesmo output, sem estado mutable |
| MF-07 | `aliq_ibs_municipio` atualizada anualmente conforme publicação do CGIBS |
| MF-08 | Saldo credor IBS/CBS acumulado não expira — compensa tributos futuros |
| MF-09 | Apuração FECHADA não reabre — somente retificada via nova competência |
| MF-10 | NCM não encontrado → warning + alíquota padrão (não bloquear operação) |
| MF-11 | **Inadimplência não estorna tributo.** IBS/CBS/IS têm fato gerador na operação (emissão/entrega), não no recebimento — cliente que não paga **não** reverte o imposto devido. A perda é tratada como risco de crédito via **PDD/PCLD (§24)**, nunca como estorno fiscal. Tributo só é estornado por **cancelamento ou devolução documentada** (nota de crédito), que gera título de ajuste (§4.6) e retificação da apuração — inadimplência pura não. |

### 1.7 Integração com Módulo Financeiro

```
NF Entrada aprovada
  → fiscal.calcular (ENTRADA)
  → fiscal.operacao_fiscal (credito_ibs, credito_cbs)
  → financeiro.titulo.impostos JSONB
  → fiscal.apuracao_mensal.creditos_ibs

NF Saída emitida
  → fiscal.calcular (SAIDA)
  → fiscal.operacao_fiscal (valor_ibs, valor_cbs)
  → financeiro.titulo.impostos JSONB
  → fiscal.apuracao_mensal.debitos_ibs

Baixa com SPLIT_PAYMENT (2027+)
  → lê fiscal.operacao_fiscal.valor_split_ibs
  → financeiro.conta_movimentacao.valor_retido_governo
  → financeiro.titulo_baixa.valor_split_payment
```

### 1.7.1 Apuração Fechada → Títulos a Pagar (guias de recolhimento)

Ao fechar a apuração mensal, o módulo fiscal publica evento e o financeiro cria
automaticamente os títulos a pagar das guias:

```
[Módulo Fiscal: Apuração Fechada]
       │
       ▼ (evento fiscal.apuracao.fechada — Kafka ou in-process)
[Módulo Financeiro: Contas a Pagar]
       │
       ├──> Cria Título 1: Guia IBS (Comitê Gestor — CGIBS)
       ├──> Cria Título 2: Guia CBS (Receita Federal)
       └──> Cria Título 3: DARF (Imposto Seletivo — IS)
```

- Títulos criados com `origem = 'APURACAO_FISCAL'`, `origem_documento_id = 'APUR-{competencia}'`,
  vencimento no prazo legal de recolhimento (parametrizado em §1.9), `terceiro_tipo = 'OUTRO'`.
- Só cria título para tributo com saldo devedor > 0 (saldo credor acumula, não gera guia).
- Durante a transição, o mesmo mecanismo cobre as guias do regime atual (ICMS/ISS/PIS/Cofins)
  quando a apuração legada estiver no sistema.
- Idempotente: uma apuração fechada gera títulos uma única vez; retificação (nova competência
  RETIFICADA) gera títulos complementares.

### 1.8-A Matching de NCM nos Regimes Diferenciados — Prefixo Mais Longo

Os seeds de `fiscal.regime_dif_ncm` contêm códigos de 2 a 8 dígitos (`06`, `07.01`,
`1006.20`, `0401.10.10`). A resolução do regime para um produto usa **match por
prefixo mais longo**:

```
1. Normalizar: remover pontos do NCM do produto (8 dígitos) e dos códigos da tabela
2. Buscar todas as linhas de regime_dif_ncm cujo código normalizado seja PREFIXO
   do NCM do produto (vigência cobrindo data_competencia)
3. Vencedor = o de código mais longo (mais específico)
4. Nenhum match → regime PADRAO
```

Exemplo: produto NCM `04061010` → match `0406.10.10` (8 díg.) vence sobre `0406` (4 díg.).
Mesma regra vale para NBS em serviços.

### 1.8-B Local de Prestação (NFS-e) — Regras por NBS

O `ibge_destino` de serviços segue regra por tipo de serviço (exceções do art. 11 da LC 214/2025):

```sql
fiscal.regra_local_prestacao
─────────────────────────────────────────────
id          BIGSERIAL PK
nbs         VARCHAR(20) NOT NULL      -- prefixo NBS (match por prefixo mais longo)
regra       VARCHAR(30) NOT NULL
            -- 'LOCAL_PRESTACAO'          (default — onde o serviço é executado)
            -- 'LOCAL_IMOVEL'             (construção, serviços sobre imóveis)
            -- 'LOCAL_EVENTO'             (eventos, feiras, espetáculos)
            -- 'DESTINO_TRANSPORTE'       (transporte de carga/passageiros)
            -- 'DOMICILIO_TOMADOR'        (serviços digitais/remotos)
descricao   VARCHAR(200)
vigente_de  DATE NOT NULL
UNIQUE (nbs, vigente_de)
```

Sem linha na tabela → `LOCAL_PRESTACAO` (default). Seed inicial com as exceções da LC 214;
mantido pelo painel admin (parametrizado).

### 1.8 Dados Fiscais Confirmados — Fontes Oficiais

> Dados extraídos diretamente da **LC 214/2025** (atualizada até LC 227/2026) e do **Informe Técnico RT 2025.002 v1.10** (publicado em Portal NF-e, abril/2026). Não requerem validação adicional — são as fontes primárias.

---

#### 1.8.1 Esclarecimento Arquitetural: CST vs. cClassTrib

O spec anterior usava apenas `cst varchar(3)`. A RT 2025.002 esclarece a distinção:

| Campo | Tamanho | Obrigatório | O que é |
|---|---|---|---|
| `CST` | 3 dígitos | Sim, a partir de jan/2026 | Código de Situação Tributária — nível macro |
| `cClassTrib` | N dígitos (os 3 primeiros = CST) | Sim, a partir de jan/2026 | Classificação granular vinculada a artigo da LC 214/2025 |

**Impacto no schema:** a tabela `fiscal.cst_ibs_cbs` representa os CSTs (macro). O `cClassTrib` completo deve ser baixado do Portal NF-e (CSV oficial) e armazenado em `fiscal.c_class_trib` separado. A `operacao_fiscal` precisa de ambos os campos.

**Simples Nacional e MEI:** durante 2026, NÃO são obrigados a informar CST e cClassTrib. Obrigatoriedade inicia em janeiro de 2027. O motor fiscal deve retornar `cst = null` e `c_class_trib = null` para essas empresas em 2026.

---

#### 1.8.2 Seed Confirmado — `fiscal.cst_ibs_cbs`

18 códigos confirmados pelo RT 2025.002:

```sql
INSERT INTO fiscal.cst_ibs_cbs (codigo, descricao, natureza) VALUES
('000', 'Tributação integral', 'AMBOS'),
('010', 'Tributação com alíquotas uniformes — operações setor financeiro', 'AMBOS'),
('011', 'Tributação com alíquotas uniformes reduzidas em 60% ou 30%', 'AMBOS'),
('200', 'Alíquota zero / Alíquota reduzida (80%, 70%, 60%, 50%, 40% ou 30%)', 'AMBOS'),
('220', 'Alíquota fixa', 'AMBOS'),
('221', 'Alíquota fixa proporcional', 'AMBOS'),
('222', 'Redução de base de cálculo', 'AMBOS'),
('400', 'Isenção', 'AMBOS'),
('410', 'Imunidade e não incidência', 'AMBOS'),
('510', 'Diferimento', 'AMBOS'),
('515', 'Diferimento com redução de alíquota', 'AMBOS'),
('550', 'Suspensão', 'AMBOS'),
('620', 'Tributação monofásica', 'AMBOS'),
('800', 'Transferência de crédito', 'AMBOS'),
('810', 'Ajustes de IBS na ZFM', 'SAIDA'),
('811', 'Ajustes', 'AMBOS'),
('820', 'Tributação em documento específico', 'AMBOS'),
('830', 'Exclusão de base de cálculo', 'AMBOS');
```

---

#### 1.8.3 Nova Entidade — `fiscal.c_cred_pres` (Crédito Presumido)

13 códigos confirmados pelo RT 2025.002:

```sql
fiscal.c_cred_pres
─────────────────────────────────────────────
id          BIGSERIAL PK
codigo      INT NOT NULL UNIQUE   -- 1 a 13
descricao   VARCHAR(500) NOT NULL
artigo_lc   VARCHAR(20)           -- ex: 'art. 168'
UNIQUE (codigo)
```

Seed:
```sql
INSERT INTO fiscal.c_cred_pres (codigo, descricao, artigo_lc) VALUES
(1,  'Crédito presumido — aquisição de bens/serviços de produtor rural não contribuinte', 'art. 168'),
(2,  'Crédito presumido — serviço de transportador autônomo de carga PF não contribuinte', 'art. 169'),
(3,  'Crédito presumido — resíduos para reciclagem/reutilização de PF/cooperativa', 'art. 170'),
(4,  'Crédito presumido — bens móveis usados de PF não contribuinte para revenda', 'art. 171'),
(5,  'Crédito presumido — regime automotivo', 'art. 310'),
(6,  'Crédito presumido — regime automotivo', 'art. 311'),
(7,  'Crédito presumido — aquisição por contribuinte na Zona Franca de Manaus', 'art. 444'),
(8,  'Crédito presumido — aquisição por contribuinte na Zona Franca de Manaus', 'art. 447'),
(9,  'Crédito presumido — aquisição por contribuinte na Zona Franca de Manaus', 'art. 447'),
(10, 'Crédito presumido — aquisição por contribuinte na Zona Franca de Manaus', 'art. 450'),
(11, 'Crédito presumido — aquisição por contribuinte na Área de Livre Comércio', 'art. 462'),
(12, 'Crédito presumido — aquisição por contribuinte na Área de Livre Comércio', 'art. 465'),
(13, 'Crédito presumido — aquisição pela indústria na Área de Livre Comércio', 'art. 467');
```

---

#### 1.8.4 Atualização Schema — `fiscal.operacao_fiscal`

Adicionar campos que a RT 2025.002 torna obrigatórios na NF-e:

```sql
-- Adicionar em fiscal.operacao_fiscal (addColumn)
cst                VARCHAR(3)    -- CST IBS/CBS (null para Simples/MEI em 2026)
c_class_trib       VARCHAR(20)   -- cClassTrib completo (null para Simples/MEI em 2026)
c_cred_pres_id     BIGINT        -- REFERENCES fiscal.c_cred_pres (nullable)
p_red_ibs          NUMERIC(5,2)  -- % redução alíquota IBS (do cClassTrib)
p_red_cbs          NUMERIC(5,2)  -- % redução alíquota CBS (do cClassTrib)
```

---

#### 1.8.5 Regimes Diferenciados — Mapeamento Completo dos Anexos LC 214/2025

Atualiza a entidade `fiscal.regime_dif_ncm` com os valores reais. O campo `regime_diferenciado` em `produto` também deve refletir esses valores.

**Enum atualizado para `regime_diferenciado` (produto):**

| Valor | Efeito IBS | Efeito CBS | Fonte |
|---|---|---|---|
| `PADRAO` | Alíquota cheia | Alíquota cheia | — |
| `ANEXO_I_ZERO` | Zero | Zero | Anexo I — alimentos básicos |
| `ANEXO_XII_ZERO` | Zero | Zero | Anexo XII — dispositivos médicos |
| `ANEXO_XIII_ZERO` | Zero | Zero | Anexo XIII — acessibilidade PcD |
| `ANEXO_XIV_ZERO` | Zero | Zero | Anexo XIV — medicamentos |
| `ANEXO_XV_ZERO` | Zero | Zero | Anexo XV — hortícolas, frutas, ovos |
| `ANEXO_II_60` | Redução 60% | Redução 60% | Anexo II — educação |
| `ANEXO_III_60` | Redução 60% | Redução 60% | Anexo III — saúde |
| `ANEXO_IV_60` | Redução 60% | Redução 60% | Anexo IV — dispositivos médicos (60%) |
| `ANEXO_V_60` | Redução 60% | Redução 60% | Anexo V — acessibilidade PcD (60%) |
| `ANEXO_VI_60` | Redução 60% | Redução 60% | Anexo VI — nutrição enteral/parenteral |
| `ANEXO_VII_60` | Redução 60% | Redução 60% | Anexo VII — alimentos (60%) |
| `ANEXO_VIII_60` | Redução 60% | Redução 60% | Anexo VIII — higiene pessoal baixa renda |
| `ANEXO_IX_60` | Redução 60% | Redução 60% | Anexo IX — insumos agropecuários |
| `ANEXO_X_60` | Redução 60% | Redução 60% | Anexo X — produções artísticas/culturais |
| `ANEXO_XI_60` | Redução 60% | Redução 60% | Anexo XI — segurança nacional/cibernética |
| `MONOFASICO` | Zero (já recolhido na origem) | Zero | LC 214/2025 art. específico |
| `ISENTO` | Zero | Zero | CST 400 |
| `IMUNE` | Zero | Zero | CST 410 |
| `ZFM` | Ajuste específico | Ajuste específico | CST 810 |

---

#### 1.8.6 Seed Confirmado — Anexo I (Alíquota Zero — Alimentos Básicos)

26 itens confirmados da LC 214/2025 — NCMs reais para seed de `fiscal.regime_dif_ncm`:

```sql
-- Anexo I — Alíquota Zero IBS e CBS
INSERT INTO fiscal.regime_dif_ncm (ncm, descricao, regime, percentual_reducao, vigente_de) VALUES
-- Arroz
('1006.20', 'Arroz subposição 1006.20', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1006.30', 'Arroz subposição 1006.30', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1006.40.00', 'Arroz código 1006.40.00', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Leite fresco
('0401.10.10', 'Leite para consumo direto', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0401.10.90', 'Leite para consumo direto', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0401.20.10', 'Leite para consumo direto', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0401.20.90', 'Leite para consumo direto', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0401.40.10', 'Leite para consumo direto', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0401.50.10', 'Leite para consumo direto', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Leite em pó
('0402.10.10', 'Leite em pó', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0402.10.90', 'Leite em pó', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0402.21.10', 'Leite em pó', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0402.21.20', 'Leite em pó', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0402.29.10', 'Leite em pó', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0402.29.20', 'Leite em pó', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Fórmulas infantis
('1901.10.10', 'Fórmulas infantis', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1901.10.90', 'Fórmulas infantis', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Manteiga, margarina
('0405.10.00', 'Manteiga', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1517.10.00', 'Margarina', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Feijões
('0713.33.19', 'Feijões', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0713.33.29', 'Feijões', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0713.33.99', 'Feijões', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0713.35.90', 'Feijões', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Farinha de mandioca, tapioca
('1106.20.00', 'Farinha de mandioca', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1903.00.00', 'Tapioca e sucedâneos', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Farinha de milho
('1102.20.00', 'Farinha de milho', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1103.13.00', 'Sêmola de milho', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Milho em grão
('1104.19.00', 'Grãos de milho', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1104.23.00', 'Grãos de milho', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Farinha de trigo
('1101.00.10', 'Farinha de trigo', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Açúcar
('1701.14.00', 'Açúcar', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1701.99.00', 'Açúcar', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Massas alimentícias (subposição 1902.1 - múltiplos)
('1902.1', 'Massas alimentícias', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Pão francês
('1905.90.90', 'Pão francês', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1901.20.10', 'Pré-mistura para pão francês', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1901.20.90', 'Pré-mistura para pão francês', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Aveia
('1104.12.00', 'Grãos de aveia', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1104.22.00', 'Grãos de aveia', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('1102.90.00', 'Farinha de aveia', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Queijos (mozarela, minas, prato, coalho, ricota, requeijão, provolone, parmesão, fresco, reino)
('0406.10.10', 'Queijo fresco não maturado', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0406.10.90', 'Queijo fresco não maturado', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0406.20.00', 'Queijo ralado ou em pó', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0406.90.10', 'Outros queijos (mozarela, minas, prato, coalho)', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0406.90.20', 'Outros queijos', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('0406.90.30', 'Outros queijos (provolone, parmesão, reino)', 'ANEXO_I_ZERO', 100, '2026-01-01'),
-- Sal
('2501.00.20', 'Sal para consumo humano', 'ANEXO_I_ZERO', 100, '2026-01-01'),
('2501.00.90', 'Sal para consumo humano', 'ANEXO_I_ZERO', 100, '2026-01-01');
-- Nota: Carnes (NCMs 02.xx), peixes (NCMs 03.xx), café (09.01, 2101.1), mate (09.03) e
-- outros itens com múltiplos NCMs/subposições devem ser expandidos conforme tabela NCM completa
```

---

#### 1.8.7 Seed Confirmado — Anexo XVII (Imposto Seletivo — IS)

Produtos e NCMs sujeitos ao IS confirmados pela LC 214/2025:

```sql
-- Seed de fiscal.aliq_is_ncm (IS)
-- Nota: alíquotas específicas serão regulamentadas. Estrutura confirmada.
INSERT INTO fiscal.aliq_is_ncm (ncm, descricao, aliquota_pct, vigente_de) VALUES
-- Veículos (exceto caminhões e veículos militares/segurança pública)
('87.03', 'Automóveis de passageiros', NULL, '2027-01-01'),
('8704.21', 'Veículos para transporte de mercadorias (exceto caminhões)', NULL, '2027-01-01'),
('8704.31', 'Veículos para transporte de mercadorias (exceto caminhões)', NULL, '2027-01-01'),
('8704.41.00', 'Veículos para transporte de mercadorias (exceto caminhões)', NULL, '2027-01-01'),
('8704.51.00', 'Veículos para transporte de mercadorias (exceto caminhões)', NULL, '2027-01-01'),
('8704.60.00', 'Veículos elétricos para transporte (exceto caminhões)', NULL, '2027-01-01'),
('8704.90.00', 'Outros veículos (exceto caminhões)', NULL, '2027-01-01'),
-- Aeronaves (exceto militares e 8802.60.00)
('8802', 'Aeronaves (exceto 8802.60.00 e militares)', NULL, '2027-01-01'),
-- Embarcações com motor
('8903', 'Embarcações com motor', NULL, '2027-01-01'),
-- Produtos fumígenos
('2401', 'Fumo não manufaturado', NULL, '2027-01-01'),
('2402', 'Charutos, cigarros, cigarrilhas', NULL, '2027-01-01'),
('2403', 'Outros produtos do fumo', NULL, '2027-01-01'),
('2404', 'Produtos com nicotina', NULL, '2027-01-01'),
-- Bebidas alcoólicas
('2203', 'Cerveja de malte', NULL, '2027-01-01'),
('2204', 'Vinhos de uvas frescas', NULL, '2027-01-01'),
('2205', 'Vermutes e outros vinhos', NULL, '2027-01-01'),
('2206', 'Outras bebidas fermentadas', NULL, '2027-01-01'),
('2208', 'Álcool etílico e bebidas espirituosas', NULL, '2027-01-01'),
-- Bebidas açucaradas
('2202.10.00', 'Águas e bebidas gaseificadas com açúcar/edulcorante', NULL, '2027-01-01'),
-- Bens minerais
('2601', 'Minérios de ferro', NULL, '2027-01-01'),
('2709.00.10', 'Petróleo bruto', NULL, '2027-01-01'),
('2711.11.00', 'Gás natural liquefeito', NULL, '2027-01-01'),
('2711.21.00', 'Gás natural em estado gasoso', NULL, '2027-01-01');
-- Concursos de prognósticos e Fantasy sport: sem NCM (CST 820 — tributação em documento específico)
-- Nota: aliquota_pct = NULL porque o IS ainda aguarda regulamentação das alíquotas específicas
```

---

#### 1.8.8 Seed Parcial — Anexos II e III (Serviços com Redução 60%)

Para NFS-e, o campo de classificação é NBS (Nomenclatura Brasileira de Serviços), não NCM.

**Impacto arquitetural:** `fiscal.regime_dif_ncm` precisa suportar tanto NCM quanto NBS:

```sql
-- Adicionar campo nbs em fiscal.regime_dif_ncm (addColumn)
ALTER TABLE fiscal.regime_dif_ncm ADD COLUMN nbs VARCHAR(20);
-- ncm e nbs são mutuamente exclusivos (CHECK constraint)
```

Seed Anexo II — Educação (Redução 60%):
```sql
INSERT INTO fiscal.regime_dif_ncm (nbs, descricao, regime, percentual_reducao, vigente_de) VALUES
('1.2201.1',    'Ensino Infantil, inclusive creche e pré-escola', 'ANEXO_II_60', 60, '2026-01-01'),
('1.2201.20.00','Ensino Fundamental', 'ANEXO_II_60', 60, '2026-01-01'),
('1.2201.30.00','Ensino Médio', 'ANEXO_II_60', 60, '2026-01-01'),
('1.2202.00.00','Ensino Técnico de Nível Médio', 'ANEXO_II_60', 60, '2026-01-01'),
('1.2203',      'EJA — Ensino para jovens e adultos', 'ANEXO_II_60', 60, '2026-01-01'),
('1.2204',      'Ensino Superior (graduação, pós-graduação, extensão)', 'ANEXO_II_60', 60, '2026-01-01'),
('1.2205.13.00','Ensino de sistemas linguísticos e línguas nativas', 'ANEXO_II_60', 60, '2026-01-01');
```

Seed Anexo III — Saúde (Redução 60% — 30 itens):
```sql
INSERT INTO fiscal.regime_dif_ncm (nbs, descricao, regime, percentual_reducao, vigente_de) VALUES
('1.2301.11.00','Serviços cirúrgicos', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.12.00','Serviços ginecológicos e obstétricos', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.13.00','Serviços psiquiátricos', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.14.00','Serviços de UTI', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.15.00','Serviços de urgência', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.19.00','Serviços hospitalares não classificados', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.21.00','Serviços de clínica médica', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.22.00','Serviços médicos especializados', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.23.00','Serviços odontológicos', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.91.00','Serviços de enfermagem', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.92.00','Serviços de fisioterapia', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.93.00','Serviços laboratoriais', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.94.00','Serviços de diagnóstico por imagem', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.95.00','Serviços de bancos de material biológico humano', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.96.00','Serviços de ambulância', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.97.00','Serviços de assistência ao parto e pós-parto', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.98.00','Serviços de psicologia', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2301.99.00','Outros serviços de saúde (vigilância, epidemiologia, vacinação, fonoaudiologia, nutrição, optometria, biomedicina, farmácia, esterilização)', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2302',      'Serviços de cuidado a idosos e PcD em acolhimento', 'ANEXO_III_60', 60, '2026-01-01'),
('1.2603.00.00','Serviços funerários, cremação e embalsamamento', 'ANEXO_III_60', 60, '2026-01-01');
```

---

#### 1.8.9 Seed Confirmado — Anexo XV (Hortícolas, Frutas, Ovos — Alíquota Zero)

```sql
INSERT INTO fiscal.regime_dif_ncm (ncm, descricao, regime, percentual_reducao, vigente_de) VALUES
('0407.2',  'Ovos', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
-- Hortícolas (Cap 7 exceto 0709.5 e 0710.80.00)
('07.01', 'Batatas', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('07.02.00.00', 'Tomates', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('07.03', 'Cebolas, alhos, alho-poró', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('07.04', 'Couves e brássicas', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('07.05', 'Alfaces e chicórias', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('07.06', 'Cenouras, nabos, beterrabas', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('0707.00.00', 'Pepinos e pepinilhos', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('07.08', 'Legumes de vagem', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('07.09', 'Outros produtos hortícolas (exceto cogumelos 0709.5)', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('07.10', 'Produtos hortícolas congelados (exceto 0710.80.00)', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
-- Frutas frescas (Cap 8)
('08.03', 'Bananas', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('08.04', 'Tâmaras, figos, abacaxis, abacates, goiabas, mangas', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('08.05', 'Frutas cítricas', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('08.06', 'Uvas', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('08.07', 'Melões, melancias, mamões', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('08.08', 'Maçãs, peras, marmelos', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('08.09', 'Damascos, cerejas, pêssegos, ameixas', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('08.10', 'Outros frutos frescos (morangos, framboesas, kiwi, caju, etc.)', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('08.11', 'Frutas congeladas sem açúcar ou edulcorante', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
-- Raízes e tubérculos, cocos
('07.14', 'Raízes e tubérculos', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
('0801.1', 'Cocos', 'ANEXO_XV_ZERO', 100, '2026-01-01'),
-- Capítulo 6 (plantas/flores para fins alimentares/ornamentais/medicinais)
('06', 'Plantas e produtos de floricultura (cap 6)', 'ANEXO_XV_ZERO', 100, '2026-01-01');
```

---

#### 1.8.10 Higiene Pessoal — Anexo VIII (Redução 60%)

7 itens confirmados:

```sql
INSERT INTO fiscal.regime_dif_ncm (ncm, descricao, regime, percentual_reducao, vigente_de) VALUES
('3401.11.90', 'Sabões de toucador', 'ANEXO_VIII_60', 60, '2026-01-01'),
('3306.10.00', 'Dentifrícios', 'ANEXO_VIII_60', 60, '2026-01-01'),
('9603.21.00', 'Escovas de dentes', 'ANEXO_VIII_60', 60, '2026-01-01'),
('4818.10.00', 'Papel higiênico', 'ANEXO_VIII_60', 60, '2026-01-01'),
('3808.94.19', 'Água sanitária', 'ANEXO_VIII_60', 60, '2026-01-01'),
('3401.19.00', 'Sabões em barra', 'ANEXO_VIII_60', 60, '2026-01-01'),
('9619.00.00', 'Fraldas e artigos higiênicos semelhantes', 'ANEXO_VIII_60', 60, '2026-01-01');
```

---

#### 1.8.11 Simples Nacional — Alíquotas IBS/CBS por Faixa (2027–2033)

Dados confirmados pelos Anexos XVIII–XXII da LC 214/2025 para seed de `fiscal.aliq_cbs_regime`:

**Simples Nacional — Comércio (2027–2028):**

| Faixa | Alíquota efetiva | % CBS | % IBS | CBS efetiva | IBS efetivo |
|---|---|---|---|---|---|
| 1ª (até 180k) | 4,00% | 15,33% | 0,17% | 0,6132% | 0,0068% |
| 2ª–5ª | varia | 15,33% | 0,17% | varia | varia |

**MEI — valores fixos mensais confirmados (Anexo XXIII):**

| Ano | ICMS | ISS | CBS | IBS | Total |
|---|---|---|---|---|---|
| 2027–2028 | R$ 1,00 | R$ 5,00 | R$ 0,994 | R$ 0,006 | R$ 7,00 |
| 2029 | R$ 0,90 | R$ 4,50 | R$ 1,00 | R$ 0,20 | R$ 6,60 |
| 2030 | R$ 0,80 | R$ 4,00 | R$ 1,00 | R$ 0,40 | R$ 6,20 |
| 2031 | R$ 0,70 | R$ 3,50 | R$ 1,00 | R$ 0,60 | R$ 5,80 |
| 2032 | R$ 0,60 | R$ 3,00 | R$ 1,00 | R$ 0,80 | R$ 5,40 |
| 2033+ | — | — | R$ 1,00 | R$ 2,00 | R$ 3,00 |

MEI não destaca IBS/CBS por item na NF-e. O motor retorna `cst = null` para MEI.

---

#### 1.8.12 Migrations Adicionais Necessárias

Com base nas informações dos documentos oficiais, adicionar ao plano de migrations:

| Arquivo | Operação | Descrição |
|---|---|---|
| `fiscal/v1/017-c-cred-pres.yaml` | `createTable` + seed | 13 códigos de crédito presumido confirmados pelo RT 2025.002 |
| `fiscal/v1/018-addcol-operacao-fiscal-cst.yaml` | `addColumn` | Adiciona `cst`, `c_class_trib`, `c_cred_pres_id`, `p_red_ibs`, `p_red_cbs` em `operacao_fiscal` |
| `fiscal/v1/019-addcol-regime-dif-nbs.yaml` | `addColumn` | Adiciona coluna `nbs varchar(20)` em `regime_dif_ncm` para suportar serviços |
| `fiscal/v1/020-seed-anexo-i.yaml` | `loadData` | Seed Anexo I — alimentos básicos alíquota zero |
| `fiscal/v1/021-seed-anexo-ii-iii.yaml` | `loadData` | Seed Anexos II e III — educação e saúde (60%) |
| `fiscal/v1/022-seed-anexo-viii.yaml` | `loadData` | Seed Anexo VIII — higiene pessoal (60%) |
| `fiscal/v1/023-seed-anexo-xv.yaml` | `loadData` | Seed Anexo XV — hortícolas, frutas e ovos (zero) |
| `fiscal/v1/024-seed-anexo-xvii-is.yaml` | `loadData` | Seed Anexo XVII — produtos sujeitos ao IS |
| `fiscal/v1/025-addcol-produto-regime.yaml` | N/A | Atualizar enum `regime_diferenciado` com novos valores dos Anexos |

---

#### 1.8.13 Status dos Seeds — Pós Planilhas Oficiais

Com o processamento das três planilhas oficiais, os seeds estão prontos para uso direto no Liquibase `loadData`. Ver §15 para maturidade geral atualizada.

| Arquivo gerado | Fonte | Linhas | Status |
|---|---|---|---|
| `c_class_trib.csv` | cClassTrib_2026_04_15.xlsx | 156 | ✅ Pronto |
| `ncm_codigos.csv` | Tabela_NCM_2022_vigência_01_02_26 | 10.520 | ✅ Pronto |
| `seed_cst_ibs_cbs.sql` | cClassTrib_2026_04_15.xlsx aba CST | 18 | ✅ Pronto |
| `seed_aliq_cbs.sql` | IT_2026_002_v_1_00_Aliquotas_CBS | 5 | ✅ Pronto |
| `schema_c_class_trib.sql` | — (DDL) | — | ✅ Pronto |

**Correção importante confirmada pelas planilhas:** `c_class_trib` é `INTEGER` (ex: 1, 200028, 410004), não `VARCHAR`. O schema e o spec foram atualizados.

---

### 1.9 Parametrização Fiscal — `fiscal.parametro_fiscal`

**Decisão:** tudo que é valor de legislação (alíquotas, fallbacks, prazos, mecânica de split)
é parametrizado — nunca hardcode. Atualização via painel admin ou migration, sem deploy.

```sql
fiscal.parametro_fiscal
─────────────────────────────────────────────
id          BIGSERIAL PK
chave       VARCHAR(60) NOT NULL UNIQUE
valor       VARCHAR(200) NOT NULL
descricao   VARCHAR(300)
updated_at  TIMESTAMPTZ
updated_by  VARCHAR(100)
```

Seeds iniciais:

| Chave | Valor default | Uso |
|---|---|---|
| `fallback.ibs.municipal.zerar` | `true` | MUNICIPIO_SEM_ALIQUOTA_IBS → zera parcela municipal |
| `fallback.ibs.usar_estadual_uf` | `true` | Usa alíquota estadual da UF no fallback |
| `fallback.cbs.regime` | `LUCRO_REAL` | REGIME_SEM_ALIQUOTA_CBS → regime de fallback |
| `guia.ibs.dia_vencimento` | `20` | Vencimento da guia IBS (dia do mês seguinte) |
| `guia.cbs.dia_vencimento` | `20` | Vencimento da guia CBS |
| `guia.is.dia_vencimento` | `20` | Vencimento do DARF IS |
| `split.modelo` | `INTELIGENTE` | Mecânica do split (revisar com regulamentação) |

As alíquotas IBS/CBS continuam nas tabelas dedicadas (`aliq_ibs_municipio`, `aliq_cbs_regime`) —
o `parametro_fiscal` cobre o restante da mecânica.

---

## MÓDULO II — CONTAS A PAGAR E CONTAS A RECEBER

## II.1 Entidades e Schema

### 2.1 Forma de Pagamento

Controla como os vencimentos são calculados ao lançar um título.

```sql
financeiro.forma_pagamento
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
codigo              VARCHAR(20) NOT NULL
descricao           VARCHAR(100) NOT NULL
data_referencia     VARCHAR(20) NOT NULL  -- 'EMISSAO_INCLUSIVA' | 'EMISSAO_EXCLUSIVA' | 'SAIDA_INCLUSIVA' | 'SAIDA_EXCLUSIVA'
considera_dias_uteis BOOLEAN DEFAULT FALSE
ativo               BOOLEAN DEFAULT TRUE
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
updated_at          TIMESTAMPTZ
updated_by          VARCHAR(100)
UNIQUE (tenant_id, codigo)
```

```sql
financeiro.forma_pagamento_periodo
─────────────────────────────────────────────
id                  BIGSERIAL PK
forma_pagamento_id  BIGINT NOT NULL REFERENCES forma_pagamento(id)
tenant_id           BIGINT NOT NULL
dia_inicial_periodo INT            -- dia do mês: início do intervalo de faturamento
dia_final_periodo   INT            -- dia do mês: fim do intervalo de faturamento
mes_pagamento       VARCHAR(10)    -- 'CORRENTE' | 'SEGUINTE'
dia_pagamento       INT            -- dia específico de vencimento
numero_meses        INT DEFAULT 0  -- meses adicionais para o cálculo
dia_semana          VARCHAR(15)    -- 'SEGUNDA' | 'TERCA' | ... | null
```

**Regra de cálculo de vencimento:**
- Pega a data base conforme `data_referencia` (data de emissão do título ou data de saída da NF).
- Encontra o período (`dia_inicial_periodo` ≤ dia da data base ≤ `dia_final_periodo`).
- Aplica `numero_meses` e `dia_pagamento` no mês alvo (`mes_pagamento`).
- Se `considera_dias_uteis = TRUE`, avança para o próximo dia útil quando o resultado cair em feriado/fim de semana.
- Se `dia_semana` preenchido, ajusta para o dia da semana informado.

---

### 2.2 Tipo de Título

Classifica a finalidade do título.

```sql
financeiro.tipo_titulo
─────────────────────────────────────────────
id                          BIGSERIAL PK
tenant_id                   BIGINT NOT NULL
codigo                      VARCHAR(20) NOT NULL
descricao                   VARCHAR(100) NOT NULL
natureza                    VARCHAR(10) NOT NULL   -- 'PAGAR' | 'RECEBER' | 'AMBOS'
categoria                   VARCHAR(20) NOT NULL   -- 'NORMAL' | 'ADIANTAMENTO' | 'EMPRESTIMO'
usa_lancamento_manual       BOOLEAN DEFAULT FALSE  -- habilita lançamento manual na tela
ativo                       BOOLEAN DEFAULT TRUE
created_at                  TIMESTAMPTZ NOT NULL
created_by                  VARCHAR(100) NOT NULL
UNIQUE (tenant_id, codigo, natureza)
```

**Regra:** categoria `EMPRESTIMO` só é válida para `natureza = 'PAGAR'`.

---

### 2.3 Tipo de Ajuste

Define a classificação de acréscimos/descontos aplicados a títulos.

```sql
financeiro.tipo_ajuste
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
codigo              VARCHAR(20) NOT NULL
descricao           VARCHAR(100) NOT NULL
natureza            VARCHAR(10) NOT NULL  -- 'PAGAR' | 'RECEBER' | 'AMBOS'
operacao            VARCHAR(10) NOT NULL  -- 'ACRESCIMO' | 'DESCONTO'
categoria           VARCHAR(20) NOT NULL  -- 'MULTA' | 'MORA' | 'DESCONTO' | 'ADIANTAMENTO' | 'OUTROS'
ativo               BOOLEAN DEFAULT TRUE
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
UNIQUE (tenant_id, codigo, natureza)
```

---

### 2.4 Tipo de Baixa

Define o meio pelo qual um pagamento ou recebimento é confirmado.

```sql
financeiro.tipo_baixa
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
codigo              VARCHAR(20) NOT NULL
descricao           VARCHAR(100) NOT NULL
natureza            VARCHAR(10) NOT NULL  -- 'PAGAR' | 'RECEBER' | 'AMBOS'
meio                VARCHAR(30) NOT NULL  -- 'DINHEIRO' | 'BOLETO' | 'CREDITO_CONTA' | 'PIX' | 'CARTAO' | 'CHEQUE' | 'ANTECIPACAO' | 'COMPENSACAO' | 'RETENCAO' | 'SPLIT_PAYMENT'
ativo               BOOLEAN DEFAULT TRUE
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
UNIQUE (tenant_id, codigo, natureza)
```

---

### 2.5 Classificação Financeira

Agrupamento livre para relatórios e centros de custo.

```sql
financeiro.classificacao_financeira
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
codigo              VARCHAR(30) NOT NULL
descricao           VARCHAR(200) NOT NULL
natureza            VARCHAR(10) NOT NULL  -- 'PAGAR' | 'RECEBER' | 'AMBOS'
ativo               BOOLEAN DEFAULT TRUE
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
UNIQUE (tenant_id, codigo)
```

---

### 2.6 Motivos (Cancelamento, Parcelamento, Prorrogação)

```sql
financeiro.motivo
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
tipo                VARCHAR(20) NOT NULL  -- 'CANCELAMENTO' | 'PARCELAMENTO' | 'PRORROGACAO'
descricao           VARCHAR(200) NOT NULL
ativo               BOOLEAN DEFAULT TRUE
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
```

---

### 2.7 Parâmetros Financeiros do Tenant

Configurações globais dos módulos para o tenant.

```sql
financeiro.parametros
─────────────────────────────────────────────
tenant_id                           BIGINT PK  -- 1 linha por tenant
-- Contas a Pagar
pagar_tipo_ajuste_multa_id          BIGINT REFERENCES tipo_ajuste
pagar_tipo_ajuste_mora_id           BIGINT REFERENCES tipo_ajuste
pagar_tipo_ajuste_desconto_id       BIGINT REFERENCES tipo_ajuste
pagar_tipo_ajuste_cnab_acrescimo_id BIGINT REFERENCES tipo_ajuste
pagar_tipo_ajuste_cnab_desconto_id  BIGINT REFERENCES tipo_ajuste
pagar_permite_data_baixa_anterior   BOOLEAN DEFAULT FALSE
-- Contas a Receber
receber_tipo_ajuste_multa_id        BIGINT REFERENCES tipo_ajuste
receber_tipo_ajuste_mora_id         BIGINT REFERENCES tipo_ajuste
receber_tipo_ajuste_desconto_id     BIGINT REFERENCES tipo_ajuste
receber_tipo_ajuste_cnab_acrescimo_id BIGINT REFERENCES tipo_ajuste
receber_tipo_ajuste_cnab_desconto_id  BIGINT REFERENCES tipo_ajuste
receber_permite_data_baixa_anterior BOOLEAN DEFAULT FALSE
-- Geral
considera_feriado_bancario          BOOLEAN DEFAULT FALSE
gl_fato_periodo_fechado             VARCHAR(30) DEFAULT 'LANCAR_COMPETENCIA_ABERTA'
                                    -- 'LANCAR_COMPETENCIA_ABERTA' | 'AGUARDAR_REABERTURA' (§37, passo 2)
updated_at                          TIMESTAMPTZ
updated_by                          VARCHAR(100)
```

---

### 2.8 Título (entidade central)

```sql
financeiro.titulo
─────────────────────────────────────────────
id                      BIGSERIAL PK
tenant_id               BIGINT NOT NULL
natureza                VARCHAR(10) NOT NULL   -- 'PAGAR' | 'RECEBER'
numero                  VARCHAR(50) NOT NULL   -- identificação do documento
parcela                 VARCHAR(10)            -- ex: '001', '002'
serie                   VARCHAR(20)
tipo_titulo_id          BIGINT NOT NULL REFERENCES tipo_titulo
status_titulo           VARCHAR(20) NOT NULL   -- ver máquina de estados §3.1
status_baixa            VARCHAR(20)            -- ver máquina de estados §3.2
forma_pagamento_id      BIGINT REFERENCES forma_pagamento
classificacao_id        BIGINT REFERENCES classificacao_financeira

-- Terceiro
terceiro_tipo           VARCHAR(15) NOT NULL   -- 'FORNECEDOR' | 'CLIENTE' | 'FUNCIONARIO' | 'OUTRO'
terceiro_id             BIGINT                 -- referência ao cadastro de terceiros (schema principal)
terceiro_nome           VARCHAR(200) NOT NULL  -- desnormalizado para histórico
terceiro_cnpj_cpf       VARCHAR(14)            -- comporta CNPJ alfanumérico (NT 2026.004)
pessoa_id               UUID                   -- desnormalizado de cadastros.pessoa (party) — preenchido
                                               -- na criação do título resolvendo cliente/fornecedor → pessoa.
                                               -- Usado por netting (§II.5), compensação (CO-01) e relatórios
                                               -- por grupo. FK lógica, sem FK cross-schema.

-- Datas
data_emissao            DATE NOT NULL
data_vencimento         DATE NOT NULL
data_competencia        DATE                   -- referência contábil

-- Valores
valor_original          NUMERIC(15,2) NOT NULL CHECK (valor_original > 0)
valor_ajuste_acrescimo  NUMERIC(15,2) DEFAULT 0
valor_ajuste_desconto   NUMERIC(15,2) DEFAULT 0
valor_baixado           NUMERIC(15,2) DEFAULT 0

-- Campos calculados (atualizados em toda operação)
valor_liquido           NUMERIC(15,2) GENERATED ALWAYS AS
                        (valor_original + valor_ajuste_acrescimo - valor_ajuste_desconto) STORED
valor_saldo             NUMERIC(15,2) GENERATED ALWAYS AS
                        (valor_original + valor_ajuste_acrescimo - valor_ajuste_desconto - valor_baixado) STORED

-- Origem
origem                  VARCHAR(20) NOT NULL   -- 'MANUAL' | 'NF_ENTRADA' | 'NF_SAIDA' | 'CNAB' | 'EMPRESTIMO' | 'ADIANTAMENTO' | 'PARCELAMENTO' | 'RENEGOCIACAO' | 'APURACAO_FISCAL' | 'RECORRENTE' (reservado — roadmap)
origem_documento_id     VARCHAR(50)            -- id/chave do documento de origem (comporta nfe_chave de 44 dígitos)
nota_fiscal_numero      VARCHAR(50)
nota_fiscal_serie       VARCHAR(10)

-- Hold / bloqueio de pagamento (substituto mínimo do Hold do Oracle EBS)
bloqueado               BOOLEAN NOT NULL DEFAULT FALSE
motivo_bloqueio         VARCHAR(200)           -- obrigatório quando bloqueado = TRUE
-- Título bloqueado não entra em remessa CNAB, não aceita baixa e não
-- aparece em sugestão de pagamento. Bloqueio/desbloqueio é manual hoje;
-- o matching 3-vias (compras) passará a bloquear automaticamente no futuro.

-- Estabelecimento (dimensão por filial — spec/estabelecimentos-filiais.md)
estabelecimento_id      UUID                   -- FK lógica → cadastros.estabelecimento

-- Associação
associacao_id           BIGINT                 -- grupo de títulos associados

-- Observações
observacao              TEXT

-- Auditoria
created_at              TIMESTAMPTZ NOT NULL
created_by              VARCHAR(100) NOT NULL
updated_at              TIMESTAMPTZ
updated_by              VARCHAR(100)
cancelled_at            TIMESTAMPTZ
cancelled_by            VARCHAR(100)
motivo_cancelamento_id  BIGINT REFERENCES motivo

INDEX idx_titulo_tenant_natureza      (tenant_id, natureza)
INDEX idx_titulo_tenant_vencimento    (tenant_id, data_vencimento)
INDEX idx_titulo_tenant_terceiro      (tenant_id, terceiro_tipo, terceiro_id)
INDEX idx_titulo_tenant_pessoa        (tenant_id, pessoa_id) WHERE pessoa_id IS NOT NULL
INDEX idx_titulo_tenant_status        (tenant_id, status_titulo, status_baixa)
INDEX idx_titulo_associacao           (associacao_id) WHERE associacao_id IS NOT NULL
```

---

### 2.9 Ajuste de Título

Registra cada acréscimo/desconto aplicado a um título.

```sql
financeiro.titulo_ajuste
─────────────────────────────────────────────
id                  BIGSERIAL PK
titulo_id           BIGINT NOT NULL REFERENCES titulo
tenant_id           BIGINT NOT NULL
tipo_ajuste_id      BIGINT NOT NULL REFERENCES tipo_ajuste
valor               NUMERIC(15,2) NOT NULL CHECK (valor > 0)
observacao          VARCHAR(500)
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
```

---

### 2.10 Baixa

Registra cada evento de pagamento ou recebimento de um título.

```sql
financeiro.titulo_baixa
─────────────────────────────────────────────
id                  BIGSERIAL PK
titulo_id           BIGINT NOT NULL REFERENCES titulo
tenant_id           BIGINT NOT NULL
tipo_baixa_id       BIGINT NOT NULL REFERENCES tipo_baixa
data_baixa          DATE NOT NULL
valor               NUMERIC(15,2) NOT NULL CHECK (valor <> 0)
                    -- valor NEGATIVO permitido SOMENTE quando origem = 'ESTORNO' (§4.6.1);
                    -- CHECK complementar: (valor > 0 OR origem = 'ESTORNO')
status              VARCHAR(15) NOT NULL DEFAULT 'PLANEJADA'  -- 'PLANEJADA' | 'REAL'
conta_corrente_id   BIGINT                 -- referência à conta corrente usada
observacao          VARCHAR(500)

-- Rastreabilidade
origem              VARCHAR(20) NOT NULL   -- 'MANUAL' | 'CNAB' | 'COMPENSACAO' | 'ADIANTAMENTO' | 'ESTORNO'
compensacao_id      BIGINT                 -- preenchido se origem = 'COMPENSACAO'
adiantamento_id     BIGINT                 -- preenchido se origem = 'ADIANTAMENTO'

-- Confirmação
confirmada_at       TIMESTAMPTZ            -- preenchido ao passar PLANEJADA → REAL
confirmada_by       VARCHAR(100)

created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
```

---

### 2.11 Prorrogação

```sql
financeiro.titulo_prorrogacao
─────────────────────────────────────────────
id                      BIGSERIAL PK
titulo_id               BIGINT NOT NULL REFERENCES titulo
tenant_id               BIGINT NOT NULL
data_vencimento_anterior DATE NOT NULL
data_vencimento_nova     DATE NOT NULL
motivo_id               BIGINT REFERENCES motivo
observacao              VARCHAR(500)
created_at              TIMESTAMPTZ NOT NULL
created_by              VARCHAR(100) NOT NULL
```

---

### 2.12 Parcelamento

```sql
financeiro.titulo_parcelamento
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
titulo_origem_id    BIGINT NOT NULL REFERENCES titulo  -- título original que foi parcelado
motivo_id           BIGINT REFERENCES motivo
total_parcelas      INT NOT NULL
observacao          VARCHAR(500)
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
```

Cada parcela gerada pelo parcelamento é um novo `titulo` com `origem = 'PARCELAMENTO'` e referência ao `titulo_parcelamento.id`.

---

### 2.13 Adiantamento Disponível (saldo)

Controla saldo de adiantamentos usáveis em baixas futuras.

```sql
financeiro.adiantamento_saldo
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
titulo_id           BIGINT NOT NULL REFERENCES titulo  -- título do tipo ADIANTAMENTO
terceiro_tipo       VARCHAR(15) NOT NULL
terceiro_id         BIGINT NOT NULL
natureza            VARCHAR(10) NOT NULL   -- 'PAGAR' | 'RECEBER'
valor_total         NUMERIC(15,2) NOT NULL
valor_utilizado     NUMERIC(15,2) DEFAULT 0
valor_disponivel    NUMERIC(15,2) GENERATED ALWAYS AS (valor_total - valor_utilizado) STORED
ativo               BOOLEAN DEFAULT TRUE
created_at          TIMESTAMPTZ NOT NULL
updated_at          TIMESTAMPTZ
```

---

### 2.14 Compensação

Vincula um título a pagar com um a receber do mesmo terceiro para compensação mútua.

```sql
financeiro.compensacao
─────────────────────────────────────────────
id                      BIGSERIAL PK
tenant_id               BIGINT NOT NULL
titulo_pagar_id         BIGINT NOT NULL REFERENCES titulo
titulo_receber_id       BIGINT NOT NULL REFERENCES titulo
valor_compensado        NUMERIC(15,2) NOT NULL
status                  VARCHAR(15) NOT NULL DEFAULT 'PENDENTE'  -- 'PENDENTE' | 'CONFIRMADA' | 'CANCELADA'
observacao              VARCHAR(500)
created_at              TIMESTAMPTZ NOT NULL
created_by              VARCHAR(100) NOT NULL
confirmada_at           TIMESTAMPTZ
confirmada_by           VARCHAR(100)
```

**Regra de negócio:**
- `titulo_pagar.pessoa_id` = `titulo_receber.pessoa_id` (mesma pessoa, não nulo — CO-01).
- `valor_compensado` ≤ `titulo_pagar.valor_saldo` E ≤ `titulo_receber.valor_saldo`.
- Compensação parcial: saldo remanescente permanece em aberto nos dois títulos.
- Compensação total: ambos vão para status `BAIXADO`.

---

### 2.15 Empréstimo / Leasing

```sql
financeiro.emprestimo
─────────────────────────────────────────────
id                      BIGSERIAL PK
tenant_id               BIGINT NOT NULL
fornecedor_id           BIGINT NOT NULL
fornecedor_nome         VARCHAR(200) NOT NULL
data_contrato           DATE NOT NULL
primeira_vencimento     DATE NOT NULL
valor_emprestimo        NUMERIC(15,2) NOT NULL
taxa_juros              NUMERIC(8,4) NOT NULL   -- percentual ao mês
tipo_amortizacao        VARCHAR(10) NOT NULL    -- 'PRICE' | 'SAC' | 'OUTROS'
total_parcelas          INT NOT NULL
juros_total             NUMERIC(15,2) NOT NULL  -- calculado ao confirmar
tipo_ajuste_juros_id    BIGINT REFERENCES tipo_ajuste
tipo_garantia           VARCHAR(100)
operacao                VARCHAR(100)
documento               VARCHAR(50)
serie                   VARCHAR(20)
observacao              TEXT
status                  VARCHAR(15) NOT NULL DEFAULT 'ATIVO'  -- 'ATIVO' | 'QUITADO' | 'CANCELADO'
created_at              TIMESTAMPTZ NOT NULL
created_by              VARCHAR(100) NOT NULL
```

Ao confirmar um empréstimo, o sistema gera automaticamente N títulos a pagar (parcelas) vinculados ao `emprestimo.id`.

---

## II.2 Máquinas de Estado

### II.2.1 Status do Título (`status_titulo`)

```
PREVISTO ──────────────────────────────────────────┐
    │                                               │
    │ [ativar]                                      │
    ▼                                               │
EM_ABERTO ◄──────────────┐                          │
    │      ╲              │[cancelar emissão]       │
    │       ╲[emitir]     │                         │
    │        ▼            │                    [cancelar]
    │      EMITIDO ───────┘                         │
    │        │  ╲                                   │
    │        │   ╲[descontar]                       │
    │        │    ▼                                 │
    │        │  DESCONTADO                          │
    │        │    │ [liquidação]                    │
    │[baixar]│    │                                 │
    ▼        ▼    ▼                                 │
BAIXADO ◄────┴────┘      CANCELADO ◄────────────────┘
```

| Transição | Quem dispara | Condição |
|---|---|---|
| `PREVISTO → EM_ABERTO` | Usuário (ação "Ativar") | tipo_titulo.usa_lancamento_manual = TRUE ou origem integração |
| `EM_ABERTO → EMITIDO` | Emissão de cobrança (§5.1) — só AR | Gera código de barras / registro de boleto |
| `EMITIDO → EM_ABERTO` | Cancelar emissão (§5.1) | Não vinculado a remessa CNAB |
| `EMITIDO → DESCONTADO` | Desconto de título (§5.4) — só AR | — |
| `EM_ABERTO / EMITIDO / DESCONTADO → BAIXADO` | Baixa confirmada (status_baixa = REAL) | valor_saldo = 0 |
| `EM_ABERTO → CANCELADO` | Usuário | Sem baixas com status REAL |
| `PREVISTO → CANCELADO` | Usuário | Sem restrição |
| (qualquer) | — | Título com `bloqueado = TRUE` não aceita baixa nem entra em remessa |

---

### 3.2 Status da Baixa (`status_baixa`)

```
null (sem baixa)
    │
    │ [registrar baixa]
    ▼
PLANEJADA
    │         ╲
    │[confirmar]╲[cancelar baixa]
    ▼            ▼
  REAL         null (volta para EM_ABERTO se saldo > 0)
```

| Regra | Detalhe |
|---|---|
| Baixa parcial | `valor_baixado < valor_liquido` → status_titulo permanece EM_ABERTO |
| Baixa total | `valor_baixado = valor_liquido` → status_titulo = BAIXADO |
| Múltiplas baixas | Permitido enquanto `valor_saldo > 0` |
| Cancelar baixa REAL | Não permitido — somente estorno via novo lançamento |
| Recebimento **N→1** (1 pagamento, vários títulos) | Uma `titulo_baixa` por título; alocar por **ordem de vencimento (mais antigo primeiro)**; juros/mora/multa/desconto calculados **por título** sobre o saldo de cada um na data do recebimento; sobra vira crédito/adiantamento (§II.5), nunca "baixa a mais" num título |
| Recebimento **1→N** (baixas parciais no mesmo título) | Cada baixa recalcula juros/mora sobre o **saldo residual** na sua própria data (juros proporcionais ao saldo e ao tempo, não ao valor cheio); título só fecha quando `saldo = 0`; baixa parcial mantém `EM_ABERTO` |
| Cancelar baixa PLANEJADA | Permitido — reverte `valor_baixado` |

---

### 3.3 Status da Compensação

```
PENDENTE → CONFIRMADA
         ↘ CANCELADA
```

Compensação só pode ser cancelada enquanto as baixas associadas estiverem com `status = PLANEJADA`.

---

## II.3 Operações — Contas a Pagar

### 4.1 Lançar Título a Pagar

**Endpoint:** `POST /api/financeiro/titulos/pagar`

**Fluxo:**
1. Validar `tipo_titulo.natureza` IN ('PAGAR', 'AMBOS') e `categoria != 'EMPRESTIMO'`.
2. Validar `tipo_titulo.usa_lancamento_manual = TRUE` se `origem = 'MANUAL'`.
3. Calcular `data_vencimento` via `forma_pagamento` se não informada diretamente.
4. Criar registro com `status_titulo = 'EM_ABERTO'` (ou `'PREVISTO'` se informado).
5. Retornar título criado.

**Campos obrigatórios:** `numero`, `tipo_titulo_id`, `terceiro_tipo`, `terceiro_id`, `data_emissao`, `data_vencimento`, `valor_original`.

---

### 4.2 Ajustar Título a Pagar

**Endpoint:** `POST /api/financeiro/titulos/pagar/{id}/ajustes`

**Fluxo:**
1. Verificar `status_titulo = 'EM_ABERTO'`.
2. Validar `tipo_ajuste.natureza` IN ('PAGAR', 'AMBOS').
3. Se `tipo_ajuste.operacao = 'ACRESCIMO'`: incrementa `valor_ajuste_acrescimo`.
4. Se `tipo_ajuste.operacao = 'DESCONTO'`: incrementa `valor_ajuste_desconto`.
5. Validar que `valor_desconto_total <= valor_original + valor_acrescimo_total`.
6. Inserir em `titulo_ajuste`.
7. Recalcular `valor_liquido` e `valor_saldo`.

---

### 4.3 Prorrogar Título a Pagar

**Endpoint:** `PATCH /api/financeiro/titulos/pagar/{id}/prorrogacao`

**Fluxo:**
1. Verificar `status_titulo = 'EM_ABERTO'`.
2. Validar `data_vencimento_nova > data_vencimento_atual`.
3. Registrar em `titulo_prorrogacao` com a data anterior.
4. Atualizar `titulo.data_vencimento`.

---

### 4.4 Parcelar Título a Pagar

**Endpoint:** `POST /api/financeiro/titulos/pagar/{id}/parcelamento`

**Body:** `{ total_parcelas: int, parcelas: [{ data_vencimento: date, valor: decimal }], motivo_id?: long }`

**Fluxo:**
1. Verificar `status_titulo = 'EM_ABERTO'` e sem baixas REAL.
2. Validar que `sum(parcelas[].valor) = titulo.valor_liquido`.
3. Cancelar título original (status → `CANCELADO`).
4. Criar registro em `titulo_parcelamento`.
5. Criar N novos títulos com `origem = 'PARCELAMENTO'`, `origem_documento_id = parcelamento.id`.
6. Retornar lista das parcelas criadas.

---

### 4.5 Associar Títulos

**Endpoint:** `POST /api/financeiro/titulos/associacao`

**Body:** `{ titulo_ids: long[] }`

**Fluxo:**
1. Validar que todos os títulos pertencem ao mesmo `tenant_id` e `terceiro_id`.
2. Validar que todos têm `status_titulo = 'EM_ABERTO'`.
3. Gerar novo `associacao_id` (sequence BIGINT — o campo em `titulo` é BIGINT) e atualizar todos os títulos.

---

### 4.6 Baixar Título a Pagar

**Endpoint:** `POST /api/financeiro/titulos/pagar/{id}/baixas`

**Body:**
```json
{
  "tipo_baixa_id": 1,
  "data_baixa": "2025-06-10",
  "valor": 500.00,
  "status": "REAL",
  "conta_corrente_id": 2,
  "ajustes": [
    { "tipo_ajuste_id": 3, "valor": 25.00 }
  ],
  "observacao": ""
}
```

**Fluxo:**
1. Verificar `status_titulo` IN (`'EM_ABERTO'`, `'EMITIDO'`, `'DESCONTADO'`) — alinhado à
   máquina de estados §II.2.1 (boleto emitido é baixado pelo retorno CNAB/PIX).
2. Se `parametros.pagar_permite_data_baixa_anterior = FALSE`, validar `data_baixa >= data_atual` (bloqueia baixa retroativa). Independente do parâmetro, `data_baixa >= data_emissao` é sempre exigido.
2b. Verificar `titulo.bloqueado = FALSE` — título em hold não aceita baixa.
3. Processar ajustes embutidos na baixa (atualiza `titulo_ajuste` e recalcula `valor_liquido`).
4. Validar `valor <= titulo.valor_saldo`.
5. Criar `titulo_baixa` com `status = 'PLANEJADA'` ou `'REAL'` conforme payload.
6. Se `status = 'REAL'`: atualizar `titulo.valor_baixado` e recalcular `valor_saldo`.
7. Se `valor_saldo = 0`: atualizar `titulo.status_titulo = 'BAIXADO'`.
8. Se `valor_saldo > 0`: manter `status_titulo = 'EM_ABERTO'`.

**Confirmar baixa planejada:** `PATCH /api/financeiro/titulos/pagar/{id}/baixas/{baixa_id}/confirmar`

---

### 4.6.1 Estornar Baixa REAL

> Nada disso existe hoje — endpoint e fluxo novos. O reflexo contábil do estorno (lançamento
> de reversão no GL) está especificado em **§37.1**.

**Endpoint:** `POST /api/financeiro/titulos/{id}/baixas/{baixa_id}/estorno`

**Body:** `{ "motivo": "PIX devolvido pelo banco", "data_estorno": "2026-07-02" }`

**Fluxo:**
1. Verificar `titulo_baixa.status = 'REAL'` e que ainda não foi estornada.
2. Criar novo registro `titulo_baixa` com `origem = 'ESTORNO'`, `status = 'REAL'` e
   `valor` **negativo** (−valor da baixa original), vinculado via novo campo `baixa_estornada_id`.
3. Reverter `titulo.valor_baixado` (recalcula `valor_saldo`).
4. Se o título estava `BAIXADO` → volta para `EM_ABERTO`.
5. Se a baixa gerou `conta_movimentacao`: criar movimentação inversa (`CONFIRMADO`,
   `categoria = 'LANCAMENTO'`, **tipo oposto e valor positivo** — o sinal negativo existe
   só em `titulo_baixa`; movimentação mantém `CHECK (valor > 0)`, histórico "Estorno baixa #id").
   Nunca deletar a original.
6. Registrar em `audit_log`. Baixa original ganha `estornada_at/by`.

**Campos novos em `titulo_baixa`:** `baixa_estornada_id BIGINT`, `estornada_at TIMESTAMPTZ`,
`estornada_by VARCHAR(100)`.

**Impacto do valor negativo (decisão registrada — checar em toda query que agrega baixas):**
- `CHECK (valor > 0)` de `titulo_baixa` vira `CHECK (valor <> 0)` + regra `valor > 0 OR origem = 'ESTORNO'` (§2.10).
- `titulo.valor_baixado = SUM(baixas REAL)` — soma algébrica: o estorno reduz naturalmente. CP-10
  (`valor <= valor_saldo`) **não se aplica** a baixas de estorno (validação pula quando `origem = 'ESTORNO'`).
- Fluxo de caixa realizado (§III 5.1), totalizadores de listagem (§7.1), aging e KPIs (Módulo VI):
  somam baixas algebricamente — estorno entra como redução do realizado na data do estorno
  (não retroage à data da baixa original).
- Conciliação automática (§III 4.4): match do estorno se dá pela movimentação inversa
  (positiva, tipo oposto) — o matcher não precisa tratar valores negativos.
- GL (§37.1): lançamento de reversão consome o evento da baixa de estorno.
- Uma baixa de estorno não pode ser estornada (bloquear `origem = 'ESTORNO'` no endpoint).

---

### 4.6.2 Multa e Mora Automáticas na Baixa em Atraso (parametrizado)

Ao baixar título com `data_baixa > data_vencimento`, o sistema **sugere** os ajustes de
multa e mora na tela de baixa (o operador confirma ou edita):

```
multa = valor_saldo × parametros.percentual_multa / 100          (padrão 2%)
mora  = valor_saldo × (parametros.percentual_mora_mes / 30)
        × dias_atraso / 100                                       (pro-rata die)
```

**Campos novos em `financeiro.parametros`:** `percentual_multa NUMERIC(5,2) DEFAULT 2.00`,
`percentual_mora_mes NUMERIC(5,2) DEFAULT 1.00`, `sugerir_multa_mora BOOLEAN DEFAULT TRUE` —
tudo parametrizado por tenant, valendo para AP e AR (AR usa nos recebimentos manuais;
boleto já tem os percentuais próprios em `cobranca_config`).

---

### 4.7 Adiantamento a Pagar

#### 4.7.1 Lançar adiantamento

1. Criar título com `tipo_titulo.categoria = 'ADIANTAMENTO'` e `natureza = 'PAGAR'`.
2. Baixar o título com `tipo_baixa.meio = 'ANTECIPACAO'`.
3. Ao confirmar a baixa (status = REAL), criar/atualizar `adiantamento_saldo` para o fornecedor.

#### 4.7.2 Usar adiantamento em baixa

**Endpoint:** `POST /api/financeiro/titulos/pagar/{id}/baixas` (com `tipo_baixa.meio = 'ANTECIPACAO'`)

**Body adicional:** `{ "adiantamento_id": 10, "valor_adiantamento": 200.00 }`

**Fluxo:**
1. Verificar `adiantamento_saldo.valor_disponivel >= valor_adiantamento`.
2. Verificar `adiantamento_saldo.terceiro_id = titulo.terceiro_id`.
3. Criar `titulo_baixa` com `origem = 'ADIANTAMENTO'` no valor utilizado.
4. **Incrementar** `adiantamento_saldo.valor_utilizado`.

> **Por que só baixa, sem ajuste de desconto:** o desenho anterior criava a baixa
> (que reduz `valor_saldo` via `valor_baixado`) **e** um ajuste de desconto
> (que reduz `valor_liquido` e, por consequência, `valor_saldo` de novo) pelo
> mesmo valor — o título seria quitado duas vezes: um adiantamento de R$ 200
> em um título de R$ 1.000 deixaria saldo de R$ 600 em vez de R$ 800. Além da
> dupla contagem, o ajuste distorceria relatórios de descontos concedidos com
> valores que não são desconto comercial. A baixa com `origem = 'ADIANTAMENTO'`
> já carrega a rastreabilidade (`adiantamento_id`) e o efeito financeiro correto.

---

### 4.8 Empréstimo / Leasing

**Endpoint:** `POST /api/financeiro/emprestimos`

**Fluxo:**
1. Receber dados do empréstimo (fornecedor, valor, taxa, parcelas, tipo amortização).
2. Calcular tabela de parcelas conforme o tipo de amortização.
3. Criar registro em `emprestimo`.
4. Gerar N títulos a pagar com `origem = 'EMPRESTIMO'`, vinculados ao empréstimo.
5. Retornar empréstimo + parcelas.

**Cálculo de parcelas:**

| Tipo | Lógica |
|---|---|
| `PRICE` | Parcela fixa: `PMT = PV * [i(1+i)^n] / [(1+i)^n - 1]` |
| `SAC` | Amortização fixa: `amort = PV/n`, juros decrescentes |
| `OUTROS` | Usuário define valores manualmente |

---

### 4.9 Retenções na Fonte (IRRF · CSRF · INSS · ISS · IBS/CBS retido)

> IRPJ/CSLL/INSS **não acabam com a reforma tributária** — retenção na fonte continua
> obrigatória em serviços. Tudo parametrizado: alíquotas, pisos e códigos de receita
> ficam em tabela de configuração, nunca hardcode.

```sql
financeiro.titulo_retencao
─────────────────────────────────────────────
id              BIGSERIAL PK
titulo_id       BIGINT NOT NULL REFERENCES titulo
tenant_id       BIGINT NOT NULL
tributo         VARCHAR(15) NOT NULL   -- 'IRRF' | 'CSRF' | 'INSS' | 'ISS' | 'IBS_CBS'
base_calculo    NUMERIC(15,2) NOT NULL
aliquota        NUMERIC(6,4) NOT NULL
valor           NUMERIC(15,2) NOT NULL
codigo_receita  VARCHAR(10)            -- código DARF/guia (ex: 1708, 5952)
competencia     VARCHAR(7) NOT NULL    -- 'YYYY-MM'
titulo_guia_id  BIGINT                 -- título a pagar da guia, preenchido na geração
created_at      TIMESTAMPTZ NOT NULL
```

```sql
financeiro.retencao_config          -- parametrização por tenant
─────────────────────────────────────────────
id              BIGSERIAL PK
tenant_id       BIGINT NOT NULL
tributo         VARCHAR(15) NOT NULL
aliquota        NUMERIC(6,4) NOT NULL  -- ex: IRRF 1,5% / CSRF 4,65% / INSS 11%
valor_minimo    NUMERIC(15,2) DEFAULT 0 -- piso legal de dispensa da retenção
codigo_receita  VARCHAR(10)
dia_vencimento_guia INT               -- vencimento da guia no mês seguinte
ativo           BOOLEAN DEFAULT TRUE
UNIQUE (tenant_id, tributo)
```

**Fluxo no AP (título de serviço com retenção):**
1. No lançamento do título, o operador (ou a integração NFS-e) informa as retenções —
   o sistema sugere pelos `retencao_config` aplicáveis.
2. `valor_liquido_pagar = valor_liquido − SUM(retencoes)`. A baixa ao fornecedor é pelo
   líquido; **cada retenção gera uma baixa no título com `tipo_baixa.meio = 'RETENCAO'`**
   na data do pagamento — assim o título fecha por completo (líquido pago + baixas de
   retenção = valor_liquido). As retenções ficam como obrigação do tenant.
3. Job mensal (`RetencaoGuiaJob`): agrupa retenções por `(tributo, codigo_receita, competencia)`
   e cria **um título a pagar por guia** (`origem = 'APURACAO_FISCAL'`, favorecido = ente
   arrecadador), vinculando `titulo_retencao.titulo_guia_id`.
4. No AR (nosso cliente reteve): registrar a retenção sofrida como baixa parcial
   `tipo_baixa.meio = 'RETENCAO'` — o caixa nunca recebe esse valor; o crédito tributário
   vai para conta de "Tributos Retidos na Fonte a Recuperar" (plano de contas 1.1.6.05).

---

## II.4 Operações — Contas a Receber

Contas a Receber compartilha as operações §4.1 a §4.7 com as adaptações abaixo. As operações exclusivas estão descritas nesta seção.

### 5.1 Emitir Título (preparar para cobrança)

**Endpoint:** `PATCH /api/financeiro/titulos/receber/{id}/emitir`

**Fluxo:**
1. Verificar `status_titulo = 'EM_ABERTO'`.
2. Gerar código de barras / linha digitável (integração com banco ou geração local conforme configuração).
3. Atualizar `titulo.status_titulo = 'EMITIDO'`.
4. Bloquear alterações no título enquanto `status = 'EMITIDO'`.

**Desfazer emissão:** `PATCH /api/financeiro/titulos/receber/{id}/cancelar-emissao`
- Só permitido se título não estiver vinculado a remessa CNAB.

---

### 5.2 Renegociar Título

**Endpoint:** `POST /api/financeiro/titulos/receber/{id}/renegociacao`

**Body:**
```json
{
  "novo_vencimento": "2025-08-01",
  "novo_valor": 480.00,
  "tipo_ajuste_acrescimo_id": 2,
  "tipo_ajuste_desconto_id": 5,
  "observacao": "Acordo comercial"
}
```

**Fluxo:**
1. Verificar `status_titulo = 'EM_ABERTO'`.
2. Cancelar título original.
3. Criar novo título com os novos termos, `origem = 'RENEGOCIACAO'`, `origem_documento_id = id_original`.
4. Registrar histórico da renegociação.

---

### 5.3 Enviar Carta de Cobrança

**Endpoint:** `POST /api/financeiro/titulos/receber/{id}/cobranca`

**Fluxo:**
1. Verificar `status_titulo = 'EM_ABERTO'` e `data_vencimento < hoje` (título vencido).
2. Gerar PDF da carta de cobrança com dados do título, cliente e instruções de pagamento.
3. Enviar por e-mail ao `terceiro.email`.
4. Registrar envio em log de comunicações.

---

### 5.3.1 Dunning — Régua de Cobrança Automática Sequenciada

Motor de cobrança automática por título vencido (cron diário `DunningJob`), com etapas
**parametrizadas por tenant** (a régua abaixo é o seed default, espelhando o desenho aprovado):

| Etapa | Gatilho | Ação |
|---|---|---|
| 1 | D+1 do vencimento | E-mail amigável — lembrete com 2ª via |
| 2 | D+7 | E-mail firme com valor atualizado (multa + mora de §4.6.2) |
| 3 | D+15 | Marca cliente como `bloqueado_para_vendas` (evento consumível pelo futuro módulo de pedidos) |
| 4 | D+30 | Escala pro time de crédito — abre caso de cobrança manual (fila) |

```sql
financeiro.dunning_regua           -- etapas parametrizáveis por tenant
─────────────────────────────────────────────
id              BIGSERIAL PK
tenant_id       BIGINT NOT NULL
dias_apos_vencimento INT NOT NULL
acao            VARCHAR(30) NOT NULL  -- 'EMAIL_LEMBRETE' | 'EMAIL_FIRME' | 'BLOQUEAR_CLIENTE' | 'ESCALAR_CREDITO'
template_email  VARCHAR(50)
ativo           BOOLEAN DEFAULT TRUE
UNIQUE (tenant_id, dias_apos_vencimento)

financeiro.dunning_evento          -- histórico por título (idempotência)
─────────────────────────────────────────────
id              BIGSERIAL PK
tenant_id       BIGINT NOT NULL
titulo_id       BIGINT NOT NULL REFERENCES titulo
regua_id        BIGINT NOT NULL REFERENCES dunning_regua
executado_at    TIMESTAMPTZ NOT NULL
resultado       VARCHAR(20)           -- 'ENVIADO' | 'ERRO' | 'PULADO'
UNIQUE (titulo_id, regua_id)          -- cada etapa dispara 1x por título
```

Regras: título baixado/cancelado/bloqueado ou em renegociação sai da régua; pagamento em
qualquer etapa encerra a sequência; a carta manual (§5.3) continua disponível.

---

### 5.4 Descontar Título (antecipação de recebimento)

**Endpoint:** `POST /api/financeiro/titulos/receber/{id}/desconto`

**Body:**
```json
{
  "conta_corrente_id": 1,
  "taxa_desconto": 2.5,
  "data_desconto": "2025-06-01"
}
```

**Fluxo:**
1. Verificar `status_titulo = 'EMITIDO'`.
2. Calcular o líquido da antecipação: `valor_antecipado = valor_saldo − (valor_saldo × taxa_desconto/100)`.
3. Criar `conta_movimentacao` CRÉDITO pelo `valor_antecipado` na conta informada e registrar a taxa como **despesa financeira** (movimentação DÉBITO, categoria LANCAMENTO). **Não** criar ajuste de desconto no título — o sacado continua devendo o valor integral (quem recebe a liquidação é o banco); um ajuste distorceria o saldo e o relatório de descontos concedidos.
4. Atualizar status para `DESCONTADO`. O título mantém o saldo integral até a liquidação pelo sacado (retorno CNAB) — a baixa quita 100% do valor.
5. Se o sacado não pagar, o banco exerce o regresso (debita o valor antecipado): registrar movimentação DÉBITO correspondente e o título segue o fluxo de cobrança/dunning normalmente.

---

## II.5 Compensação entre Contas

**Endpoint:** `POST /api/financeiro/compensacoes`

**Body:**
```json
{
  "titulo_pagar_id": 15,
  "titulo_receber_id": 42,
  "valor_compensado": 300.00,
  "observacao": ""
}
```

**Fluxo:**
1. Validar mesmo `pessoa_id` (não nulo) nos dois títulos (CO-01).
2. Validar `valor_compensado <= min(titulo_pagar.valor_saldo, titulo_receber.valor_saldo)`.
3. Criar registro em `compensacao` com `status = 'PENDENTE'`.
4. Criar `titulo_baixa` em ambos os títulos com `status = 'PLANEJADA'` e `origem = 'COMPENSACAO'`.
5. Retornar compensação criada.

**Confirmar compensação:** `PATCH /api/financeiro/compensacoes/{id}/confirmar`
1. Atualizar ambas as baixas para `status = 'REAL'`.
2. Atualizar `valor_baixado` em ambos os títulos.
3. Se `valor_saldo = 0` em qualquer um: atualizar `status_titulo = 'BAIXADO'`.
4. Atualizar `compensacao.status = 'CONFIRMADA'`.

**Cancelar compensação:** `DELETE /api/financeiro/compensacoes/{id}`
- Permitido somente enquanto `status = 'PENDENTE'` (baixas ainda PLANEJADAS).

### Sugestão automática de compensação (`NettingSugestaoJob`)

Cron diário que identifica terceiros com títulos em aberto **nos dois lados**
(PAGAR e RECEBER) e gera sugestões para o operador confirmar — nenhuma compensação
é criada automaticamente:

1. Agrupar títulos `EM_ABERTO` não bloqueados por **`titulo.pessoa_id`** (campo desnormalizado —
   ver §2.8/F4.2; sem chamada ao cadastro-service e sem join cross-schema).
2. Pessoa com saldo em ambas as naturezas → criar sugestão (par de maior valor
   compensável primeiro) e notificar na tela de compensação.
3. O party model do cadastro (dedup por `cnpj_raiz`, ver spec/estabelecimentos-filiais.md)
   garante que cliente e fornecedor do mesmo CNPJ apontam para a mesma `pessoa` — e o
   `pessoa_id` gravado no título é o que permite ao netting encontrar os dois lados.
   Títulos com `pessoa_id IS NULL` (FUNCIONARIO/OUTRO) ficam fora do netting.

**Endpoint:** `GET /api/financeiro/compensacoes/sugestoes`

---

## II.6 Listagens e Filtros

### 7.1 Listar Títulos a Pagar

**Endpoint:** `GET /api/financeiro/titulos/pagar`

**Filtros disponíveis:**

| Parâmetro | Tipo | Descrição |
|---|---|---|
| `vencimento_de` | date | Vencimento a partir de |
| `vencimento_ate` | date | Vencimento até |
| `status_titulo` | enum | EM_ABERTO, BAIXADO, CANCELADO, PREVISTO |
| `status_baixa` | enum | PLANEJADA, REAL |
| `terceiro_id` | long | Fornecedor específico |
| `terceiro_nome` | string | Busca parcial por nome |
| `tipo_titulo_id` | long | Tipo do título |
| `classificacao_id` | long | Classificação financeira |
| `valor_de` | decimal | Valor mínimo |
| `valor_ate` | decimal | Valor máximo |
| `numero` | string | Número do documento |
| `origem` | enum | MANUAL, NF_ENTRADA, etc. |

**Resposta inclui totalizadores:**
```json
{
  "content": [...],
  "totais": {
    "quantidade": 12,
    "valor_original_total": 15000.00,
    "valor_ajuste_total": 200.00,
    "valor_liquido_total": 15200.00,
    "valor_saldo_total": 10200.00
  }
}
```

### 7.2 Listar Títulos a Receber

Mesmos filtros de §7.1 substituindo fornecedor por cliente.

---

## 8. Regras de Negócio Consolidadas

### 8.1 Contas a Pagar

| # | Regra |
|---|---|
| CP-01 | Lançamento manual exige `tipo_titulo.usa_lancamento_manual = TRUE` |
| CP-02 | Título com status `BAIXADO` ou `CANCELADO` não aceita nenhuma operação de edição |
| CP-03 | Título com baixas `REAL` não pode ser cancelado — somente estornado |
| CP-04 | Soma dos descontos nunca pode exceder `valor_original + acréscimos` |
| CP-05 | Prorrogação exige nova data posterior à data de vencimento atual |
| CP-06 | Parcelamento cancela o título original e cria N novos títulos |
| CP-07 | Adiantamento disponível é por fornecedor (não reutilizável entre fornecedores distintos) |
| CP-08 | Empréstimo gera parcelas automaticamente ao ser confirmado |
| CP-09 | Baixa planejada não atualiza `valor_baixado` — apenas a confirmação (status REAL) atualiza |
| CP-10 | Não é permitida baixa com `valor > valor_saldo` |

### 8.2 Contas a Receber

| # | Regra |
|---|---|
| CR-01 | Título emitido fica bloqueado para edição |
| CR-02 | Renegociação cancela o título original e cria um novo |
| CR-03 | Carta de cobrança só pode ser enviada para títulos vencidos |
| CR-04 | Desconto de título exige que o título esteja emitido |
| CR-05 | Adiantamento de cliente segue a mesma lógica do fornecedor (§4.7) |

### 8.3 Compensação

| # | Regra |
|---|---|
| CO-01 | Os dois títulos devem ser da mesma pessoa (`titulo.pessoa_id` igual e não nulo) |
| CO-02 | `valor_compensado` ≤ menor saldo entre os dois títulos |
| CO-03 | Compensação parcial mantém saldo em aberto nos dois títulos |
| CO-04 | Cancelamento só é possível enquanto `status = 'PENDENTE'` |
| CO-05 | Um título pode ter no máximo uma compensação PENDENTE por vez — enforçado por índices parciais únicos em `compensacao`: `(titulo_pagar_id) WHERE status = 'PENDENTE'` e `(titulo_receber_id) WHERE status = 'PENDENTE'` |

---

## II.8 Integrações Internas

### 9.1 Integração com NF de Entrada e Saída

**Via Kafka — contrato único em §F4** (decisão registrada: Kafka, não chamada síncrona).
NF de entrada aprovada → `nfe.entrada.aprovada` → títulos a pagar (`origem = 'NF_ENTRADA'`);
NF de saída autorizada → `nfe.saida.autorizada` → títulos a receber (`origem = 'NF_SAIDA'`).
O `tipo_titulo` e a `forma_pagamento` são determinados pela parametrização do `tipo_operacao` da NF.
Os endpoints `POST /api/financeiro/titulos/{pagar|receber}` existem apenas para lançamento manual.

### 9.3 Eventos publicados

O módulo financeiro publica eventos internos para outros módulos do ERP:

| Evento | Quando |
|---|---|
| `titulo.baixado` | Baixa confirmada (status REAL), independente de parcial/total |
| `titulo.cancelado` | Título cancelado |
| `emprestimo.quitado` | Todas as parcelas baixadas |

---

## II.9 Separação de Responsabilidades — Billing vs. Financeiro

| Camada | Quem cuida | O que faz |
|---|---|---|
| **Billing Service** | Equipe (infra SaaS) | Planos, trials, assinaturas, webhooks Asaas, comissões de parceiros, status do tenant |
| **Módulo Financeiro** | Produto ERP (para tenants) | Contas a pagar/receber dos clientes finais do tenant, títulos, baixas, etc. |

Os dois nunca se comunicam diretamente. O billing service verifica se o tenant está ativo; o módulo financeiro é habilitado/desabilitado conforme o plano contratado, mas não conhece os detalhes do billing.

---

## II.10 Próximos Módulos

| Módulo | Dependência |
|---|---|
| Controle Bancário | `titulo_baixa`, `conta_corrente` |
| CNAB 240/400 | `titulo`, `tipo_baixa.meio = 'BOLETO'`, `remessa` |
| DDA | `titulo_pagar`, importação de código de barras |
| Relatórios financeiros | Todas as entidades deste spec |
| Fluxo de caixa | `titulo.data_vencimento`, `titulo.valor_saldo` |

---

## II.12 Checklist de Implementação

### Backend (Spring Boot)

- [ ] Migrations Liquibase YAML para todas as tabelas do schema `financeiro`
- [ ] Entidades JPA com mapeamento correto (sem FK cross-schema)
- [ ] Services: `TituloService`, `BaixaService`, `AjusteService`, `CompensacaoService`, `AdiantamentoService`, `EmprestimoService`
- [ ] Isolamento multi-tenant via `@TenantAware` ou filtro Hibernate
- [ ] Cálculo de `valor_liquido` e `valor_saldo` centralizado em `TituloCalculoService`
- [ ] Validações de negócio em camada de service (não nos controllers)
- [ ] Eventos internos com Spring `ApplicationEventPublisher`
- [ ] Testes unitários das máquinas de estados §3.1 e §3.2
- [ ] Testes de integração para os fluxos de adiantamento e compensação

### Frontend (Angular)

- [ ] Tela de listagem com filtros e totalizadores (§7)
- [ ] Tela de lançamento (manual e visualização de origens)
- [ ] Modal de baixa com suporte a ajustes embutidos
- [ ] Modal de parcelamento com distribuição de valores
- [ ] Tela de compensação: seleção de dois títulos do mesmo terceiro
- [ ] Tela de adiantamentos disponíveis por fornecedor/cliente
- [ ] Wizard de empréstimo/leasing com preview das parcelas

### Migrations (Liquibase YAML)

- [ ] `v1/001-financeiro-forma-pagamento.yaml`
- [ ] `v1/002-financeiro-tipos-base.yaml` (tipo_titulo, tipo_ajuste, tipo_baixa)
- [ ] `v1/003-financeiro-classificacao-motivo.yaml`
- [ ] `v1/004-financeiro-parametros.yaml`
- [ ] `v1/005-financeiro-titulo.yaml`
- [ ] `v1/006-financeiro-titulo-operacoes.yaml` (ajuste, baixa, prorrogacao, parcelamento)
- [ ] `v1/007-financeiro-adiantamento.yaml`
- [ ] `v1/008-financeiro-compensacao.yaml`
- [ ] `v1/009-financeiro-emprestimo.yaml`
- [ ] Todos com `rollback` declarado

---

## MÓDULO III — FLUXO DE CAIXA E CONCILIAÇÃO BANCÁRIA

## III.1 Entidades e Schema

### 2.1 Banco

Cadastro de instituições financeiras.

```sql
financeiro.banco
─────────────────────────────────────────────
id                          BIGSERIAL PK
tenant_id                   BIGINT NOT NULL
codigo_compensacao          VARCHAR(10) NOT NULL   -- código FEBRABAN
nome                        VARCHAR(200) NOT NULL
utiliza_digito_agencia       BOOLEAN DEFAULT FALSE
mascara_agencia             VARCHAR(20)            -- ex: '9999-9'
mascara_conta               VARCHAR(20)            -- ex: '99999-9'
ativo                       BOOLEAN DEFAULT TRUE
created_at                  TIMESTAMPTZ NOT NULL
created_by                  VARCHAR(100) NOT NULL
UNIQUE (tenant_id, codigo_compensacao)
```

---

### 2.2 Conta Corrente

Contas bancárias do tenant.

```sql
financeiro.conta_corrente
─────────────────────────────────────────────
id                      BIGSERIAL PK
tenant_id               BIGINT NOT NULL
banco_id                BIGINT NOT NULL REFERENCES banco
agencia                 VARCHAR(20) NOT NULL
conta                   VARCHAR(30) NOT NULL
digito                  VARCHAR(5)
descricao               VARCHAR(200) NOT NULL
tipo                    VARCHAR(20) NOT NULL   -- 'CORRENTE' | 'POUPANCA' | 'INVESTIMENTO' | 'CAIXA'
moeda                   VARCHAR(3) DEFAULT 'BRL'
saldo_inicial           NUMERIC(15,2) DEFAULT 0
data_saldo_inicial      DATE
ativo                   BOOLEAN DEFAULT TRUE
conta_contabil          VARCHAR(30)            -- referência futura ao GL
created_at              TIMESTAMPTZ NOT NULL
created_by              VARCHAR(100) NOT NULL
updated_at              TIMESTAMPTZ
updated_by              VARCHAR(100)
UNIQUE (tenant_id, banco_id, agencia, conta)
```

---

### 2.3 Movimentação em Conta Corrente

Lançamentos manuais de crédito e débito — despesas bancárias, tarifas, transferências, aplicações.

```sql
financeiro.conta_movimentacao
─────────────────────────────────────────────
id                      BIGSERIAL PK
tenant_id               BIGINT NOT NULL
conta_corrente_id       BIGINT NOT NULL REFERENCES conta_corrente
data_movimentacao       DATE NOT NULL
tipo                    VARCHAR(10) NOT NULL   -- 'CREDITO' | 'DEBITO'
categoria               VARCHAR(20) NOT NULL   -- 'LANCAMENTO' | 'TRANSFERENCIA' | 'APLICACAO' | 'RESGATE'
valor                   NUMERIC(15,2) NOT NULL CHECK (valor > 0)
historico               VARCHAR(500) NOT NULL
documento               VARCHAR(50)
terceiro_nome           VARCHAR(200)
status                  VARCHAR(15) NOT NULL DEFAULT 'PENDENTE'
                                               -- 'PENDENTE' | 'CONFIRMADO' | 'CANCELADO'

-- Para transferências entre contas
conta_destino_id        BIGINT REFERENCES conta_corrente
valor_destino           NUMERIC(15,2)          -- pode diferir por taxas de câmbio

-- Vínculo com título (quando movimentação origina de baixa)
titulo_baixa_id         BIGINT                 -- referência a financeiro.titulo_baixa

-- Conciliação
conciliado              BOOLEAN DEFAULT FALSE
extrato_linha_id        BIGINT                 -- preenchido após conciliação

-- Auditoria
created_at              TIMESTAMPTZ NOT NULL
created_by              VARCHAR(100) NOT NULL
updated_at              TIMESTAMPTZ
updated_by              VARCHAR(100)

INDEX idx_mov_conta_data (tenant_id, conta_corrente_id, data_movimentacao)
INDEX idx_mov_conciliacao (tenant_id, conciliado) WHERE conciliado = FALSE
```

---

### 2.4 Saldo de Conta Corrente (calculado por data)

View materializada ou calculada sob demanda — nunca armazenada como campo simples para evitar inconsistência.

```sql
-- View de saldo por conta e data
CREATE VIEW financeiro.v_saldo_conta_corrente AS
SELECT
  cc.id AS conta_corrente_id,
  cc.tenant_id,
  cc.descricao,
  cc.saldo_inicial,
  cc.data_saldo_inicial,
  COALESCE(SUM(
    CASE
      WHEN m.tipo = 'CREDITO' AND m.status = 'CONFIRMADO' THEN m.valor
      WHEN m.tipo = 'DEBITO'  AND m.status = 'CONFIRMADO' THEN -m.valor
      ELSE 0
    END
  ), 0) AS movimentacoes_confirmadas,
  cc.saldo_inicial + COALESCE(SUM(
    CASE
      WHEN m.tipo = 'CREDITO' AND m.status = 'CONFIRMADO' THEN m.valor
      WHEN m.tipo = 'DEBITO'  AND m.status = 'CONFIRMADO' THEN -m.valor
      ELSE 0
    END
  ), 0) AS saldo_atual
FROM financeiro.conta_corrente cc
LEFT JOIN financeiro.conta_movimentacao m
  ON m.conta_corrente_id = cc.id
  AND m.data_movimentacao >= COALESCE(cc.data_saldo_inicial, DATE '1900-01-01')
  -- COALESCE obrigatório: data_saldo_inicial é nullable; sem ele, o >= com NULL
  -- descartaria TODAS as movimentações e o saldo congelaria no saldo_inicial
GROUP BY cc.id, cc.tenant_id, cc.descricao, cc.saldo_inicial, cc.data_saldo_inicial;
```

---

### 2.5 Extrato Bancário

Linhas do extrato importado (OFX) ou inseridas manualmente.

```sql
financeiro.extrato_bancario
─────────────────────────────────────────────
id                      BIGSERIAL PK
tenant_id               BIGINT NOT NULL
conta_corrente_id       BIGINT NOT NULL REFERENCES conta_corrente
data_lancamento         DATE NOT NULL
data_compensacao        DATE
tipo                    VARCHAR(10) NOT NULL   -- 'CREDITO' | 'DEBITO'
valor                   NUMERIC(15,2) NOT NULL CHECK (valor > 0)
historico               VARCHAR(500)
documento               VARCHAR(100)           -- número do documento no banco
origem                  VARCHAR(10) NOT NULL   -- 'OFX' | 'MANUAL'

-- Conciliação
status_conciliacao      VARCHAR(15) NOT NULL DEFAULT 'PENDENTE'
                                               -- 'PENDENTE' | 'CONCILIADO' | 'IGNORADO'
movimentacao_id         BIGINT REFERENCES conta_movimentacao  -- preenchido ao conciliar
conciliado_at           TIMESTAMPTZ
conciliado_by           VARCHAR(100)
conciliado_tipo         VARCHAR(15)            -- 'MANUAL' | 'AUTOMATICO'

-- Importação
importacao_id           BIGINT                 -- agrupa linhas do mesmo arquivo OFX
created_at              TIMESTAMPTZ NOT NULL
created_by              VARCHAR(100) NOT NULL

INDEX idx_extrato_conta_data    (tenant_id, conta_corrente_id, data_lancamento)
INDEX idx_extrato_pendente      (tenant_id, status_conciliacao) WHERE status_conciliacao = 'PENDENTE'
UNIQUE (tenant_id, conta_corrente_id, documento, data_lancamento) -- evita duplicação de OFX
```

---

### 2.6 Importação de Extrato OFX

Controla cada arquivo importado.

```sql
financeiro.extrato_importacao
─────────────────────────────────────────────
id                      BIGSERIAL PK
tenant_id               BIGINT NOT NULL
conta_corrente_id       BIGINT NOT NULL REFERENCES conta_corrente
nome_arquivo            VARCHAR(300) NOT NULL
data_importacao         TIMESTAMPTZ NOT NULL
periodo_de              DATE NOT NULL
periodo_ate             DATE NOT NULL
total_linhas            INT NOT NULL
total_creditos          NUMERIC(15,2) DEFAULT 0
total_debitos           NUMERIC(15,2) DEFAULT 0
status                  VARCHAR(15) NOT NULL   -- 'PROCESSADO' | 'ERRO' | 'PARCIAL'
created_by              VARCHAR(100) NOT NULL
```

---

### 2.7 Tipo de Débito / Crédito Bancário

Classificação das movimentações manuais.

```sql
financeiro.tipo_movimentacao_bancaria
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
codigo              VARCHAR(20) NOT NULL
descricao           VARCHAR(100) NOT NULL
natureza            VARCHAR(10) NOT NULL   -- 'CREDITO' | 'DEBITO'
ativo               BOOLEAN DEFAULT TRUE
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
UNIQUE (tenant_id, codigo, natureza)
```

---

### 2.8 Orçamento de Fluxo de Caixa

Valores orçados por conta e classificação para comparação com realizado.

```sql
financeiro.orcamento_fluxo
─────────────────────────────────────────────
id                      BIGSERIAL PK
tenant_id               BIGINT NOT NULL
ano                     INT NOT NULL
mes                     INT NOT NULL CHECK (mes BETWEEN 1 AND 12)
conta_corrente_id       BIGINT REFERENCES conta_corrente   -- null = consolidado
classificacao_id        BIGINT REFERENCES classificacao_financeira
natureza                VARCHAR(10) NOT NULL   -- 'ENTRADA' | 'SAIDA'
valor_orcado            NUMERIC(15,2) NOT NULL
observacao              VARCHAR(500)
created_at              TIMESTAMPTZ NOT NULL
created_by              VARCHAR(100) NOT NULL
updated_at              TIMESTAMPTZ
updated_by              VARCHAR(100)
UNIQUE (tenant_id, ano, mes, conta_corrente_id, classificacao_id, natureza)
```

---

## III.2 Operações — Conta Corrente

### 3.1 Cadastrar Conta Corrente

**Endpoint:** `POST /api/financeiro/contas-correntes`

**Fluxo:**
1. Validar `banco_id` pertence ao tenant.
2. Validar unicidade de `(banco_id, agencia, conta)` no tenant.
3. Se `saldo_inicial` informado, exigir `data_saldo_inicial`.
4. Criar conta com `saldo_inicial` como ponto de partida do cálculo de saldo.

---

### 3.2 Lançar Movimentação Manual

**Endpoint:** `POST /api/financeiro/contas-correntes/{id}/movimentacoes`

**Body:**
```json
{
  "data_movimentacao": "2025-06-10",
  "tipo": "DEBITO",
  "categoria": "LANCAMENTO",
  "tipo_movimentacao_id": 3,
  "valor": 150.00,
  "historico": "Tarifa bancária junho",
  "documento": "TAR-2025-06"
}
```

**Fluxo:**
1. Validar `conta_corrente_id` pertence ao tenant e está ativa.
2. Criar movimentação com `status = 'PENDENTE'`.
3. Confirmar imediatamente se `confirmacao_automatica = true` nos parâmetros do tenant.

**Confirmar movimentação:** `PATCH /api/financeiro/contas-correntes/{id}/movimentacoes/{mov_id}/confirmar`
- Atualiza `status = 'CONFIRMADO'` — a partir daqui entra no cálculo de saldo.

---

### 3.3 Transferência entre Contas

**Endpoint:** `POST /api/financeiro/contas-correntes/transferencias`

**Body:**
```json
{
  "conta_origem_id": 1,
  "conta_destino_id": 2,
  "data_movimentacao": "2025-06-10",
  "valor": 5000.00,
  "historico": "Reforço de caixa filial"
}
```

**Fluxo:**
1. Validar ambas as contas pertencem ao mesmo tenant.
2. Validar saldo suficiente na conta de origem (opcional — configurável).
3. Criar duas movimentações atomicamente (transação):
    - Débito na conta de origem
    - Crédito na conta de destino
4. Ambas com `categoria = 'TRANSFERENCIA'` e `conta_destino_id` / `conta_origem_id` cruzados.
5. Status inicial: `PENDENTE` nas duas.

**Regra:** débito total = crédito total. Se houver diferença por taxa, registrar ajuste separado.

---

### 3.4 Consultar Extrato por Período

**Endpoint:** `GET /api/financeiro/contas-correntes/{id}/extrato`

**Parâmetros:** `data_de`, `data_ate`

**Resposta:**
```json
{
  "conta_corrente": { ... },
  "periodo": { "de": "2025-06-01", "ate": "2025-06-30" },
  "saldo_anterior": 12500.00,
  "movimentacoes": [
    {
      "data": "2025-06-05",
      "tipo": "CREDITO",
      "historico": "Recebimento NF 001",
      "valor": 3000.00,
      "saldo_acumulado": 15500.00,
      "conciliado": true
    }
  ],
  "total_creditos": 8000.00,
  "total_debitos": 3200.00,
  "saldo_final": 17300.00
}
```

---

## III.3 Operações — Conciliação Bancária

### 4.1 Importar Extrato OFX

**Endpoint:** `POST /api/financeiro/conciliacao/importar-ofx`

**Body:** `multipart/form-data` com arquivo `.ofx` + `conta_corrente_id`

**Fluxo:**
1. Fazer parse do arquivo OFX (formato padrão SGML/XML).
2. Criar registro em `extrato_importacao`.
3. Para cada transação do OFX:
   a. Verificar duplicidade por `(conta_corrente_id, documento, data_lancamento)`.
   b. Se duplicado: ignorar (não gera erro, registra log).
   c. Se novo: inserir em `extrato_bancario` com `status_conciliacao = 'PENDENTE'`.
4. Retornar resumo: total importado, duplicatas ignoradas, saldo do extrato.

**Campos OFX mapeados:**
| Campo OFX | Campo interno |
|---|---|
| `<DTPOSTED>` | `data_lancamento` |
| `<TRNAMT>` | `valor` (positivo = crédito, negativo = débito) |
| `<NAME>` | `historico` |
| `<FITID>` | `documento` |
| `<MEMO>` | `historico` (complemento) |

---

### 4.2 Inserir Linha de Extrato Manualmente

**Endpoint:** `POST /api/financeiro/conciliacao/extrato`

**Body:**
```json
{
  "conta_corrente_id": 1,
  "data_lancamento": "2025-06-15",
  "tipo": "DEBITO",
  "valor": 250.00,
  "historico": "Tarifa DOC"
}
```

Útil quando o banco não fornece OFX ou quando há lançamentos não capturados.

---

### 4.3 Conciliar Manualmente

Vincula uma linha do extrato bancário a uma movimentação do sistema.

**Endpoint:** `POST /api/financeiro/conciliacao/conciliar`

**Body:**
```json
{
  "extrato_linha_id": 42,
  "movimentacao_id": 15
}
```

**Fluxo:**
1. Validar ambos pertencem ao mesmo `tenant_id` e `conta_corrente_id`.
2. Validar `extrato_linha_id.status_conciliacao = 'PENDENTE'`.
3. Validar `movimentacao_id.conciliado = FALSE`.
4. Validar tipos iguais (`CREDITO/DEBITO`) e valores compatíveis (tolerância configurável, ex: ± R$ 0,05).
5. Atualizar `extrato_bancario.status_conciliacao = 'CONCILIADO'`.
6. Atualizar `extrato_bancario.movimentacao_id`.
7. Atualizar `conta_movimentacao.conciliado = TRUE`.
8. Se `movimentacao_id` está vinculado a uma `titulo_baixa` com `status = 'PLANEJADA'`: confirmar a baixa automaticamente (atualizar para `status = 'REAL'`).

---

### 4.4 Conciliação Automática

**Endpoint:** `POST /api/financeiro/conciliacao/conciliar-automatico`

**Body:** `{ "conta_corrente_id": 1, "periodo_de": "2025-06-01", "periodo_ate": "2025-06-30" }`

**Algoritmo:**
1. Buscar todas as linhas do extrato com `status_conciliacao = 'PENDENTE'` no período.
2. Para cada linha, buscar movimentações com:
    - Mesmo `tipo` (CREDITO/DEBITO)
    - Mesmo `valor` (ou dentro da tolerância)
    - `data_movimentacao` próxima (± 3 dias úteis — configurável)
    - `conciliado = FALSE`
3. Se encontrar exatamente 1 match: conciliar automaticamente com `conciliado_tipo = 'AUTOMATICO'`.
4. Se encontrar 0 ou 2+ matches: deixar pendente para conciliação manual.
5. Retornar relatório: conciliados automaticamente, pendentes, sem correspondência.

---

### 4.5 Ignorar Linha do Extrato

**Endpoint:** `PATCH /api/financeiro/conciliacao/extrato/{id}/ignorar`

Usado para tarifas, IOF e lançamentos que não têm correspondência no sistema e não precisam ser conciliados.

**Fluxo:**
1. Atualizar `status_conciliacao = 'IGNORADO'`.
2. Exigir `observacao` justificando o motivo.

---

### 4.6 Desfazer Conciliação

**Endpoint:** `DELETE /api/financeiro/conciliacao/{extrato_linha_id}`

**Fluxo:**
1. Verificar `extrato_bancario.status_conciliacao = 'CONCILIADO'`.
2. Reverter `extrato_bancario.status_conciliacao = 'PENDENTE'`.
3. Reverter `conta_movimentacao.conciliado = FALSE`.
4. Desfazer a conciliação **não reverte baixa REAL** — a máquina de estados (§II.2/3.2) só admite saída de REAL via estorno (§4.6.1). O desfazer apenas desvincula extrato ↔ movimentação; se a confirmação automática (CB-07) foi indevida, o operador estorna a baixa pelo fluxo de estorno (que cria a movimentação inversa e reabre o título).

---

### 4.7 Relatório de Conciliação

**Endpoint:** `GET /api/financeiro/conciliacao/relatorio`

**Parâmetros:** `conta_corrente_id`, `periodo_de`, `periodo_ate`

**Resposta:**
```json
{
  "periodo": { "de": "2025-06-01", "ate": "2025-06-30" },
  "saldo_extrato_banco": 18500.00,
  "saldo_sistema": 18500.00,
  "diferenca": 0.00,
  "resumo": {
    "linhas_extrato_total": 45,
    "conciliadas": 42,
    "pendentes": 2,
    "ignoradas": 1
  },
  "pendentes": [
    {
      "extrato_linha_id": 38,
      "data": "2025-06-28",
      "tipo": "DEBITO",
      "valor": 120.00,
      "historico": "PIX enviado"
    }
  ],
  "sem_correspondencia_no_sistema": [
    {
      "movimentacao_id": 55,
      "data": "2025-06-20",
      "valor": 800.00,
      "historico": "Recebimento antecipado"
    }
  ]
}
```

---

## III.4 Operações — Fluxo de Caixa

### 5.1 Fluxo de Caixa Projetado

Combina realizado (movimentações confirmadas) com previsto (títulos em aberto + movimentações pendentes).

**Endpoint:** `GET /api/financeiro/fluxo-caixa`

**Parâmetros:**
| Parâmetro | Tipo | Descrição |
|---|---|---|
| `conta_corrente_ids` | long[] | Filtrar por contas (null = todas) |
| `periodo_de` | date | Início |
| `periodo_ate` | date | Fim |
| `agrupamento` | string | `DIARIO` / `SEMANAL` / `MENSAL` |
| `incluir_previsto` | boolean | Incluir títulos em aberto |
| `incluir_orcado` | boolean | Incluir valores orçados |

**Lógica de composição:**

```
REALIZADO:
  Movimentações confirmadas no período
  + Baixas de títulos confirmadas (status = REAL)

PREVISTO:
  Títulos EM_ABERTO com vencimento no período (valor_saldo)
  + Movimentações pendentes

ORÇADO:
  Valores de financeiro.orcamento_fluxo para o período
```

**Resposta:**
```json
{
  "periodo": { "de": "2025-06-01", "ate": "2025-06-30" },
  "saldo_inicial": 12500.00,
  "linhas": [
    {
      "data": "2025-06-01",
      "entradas_realizadas": 5000.00,
      "saidas_realizadas": 1200.00,
      "saldo_realizado": 16300.00,
      "entradas_previstas": 3000.00,
      "saidas_previstas": 800.00,
      "saldo_previsto": 18500.00,
      "entradas_orcadas": 4000.00,
      "saidas_orcadas": 1000.00
    }
  ],
  "totais": {
    "entradas_realizadas": 28000.00,
    "saidas_realizadas": 12000.00,
    "saldo_final_realizado": 28500.00,
    "entradas_previstas": 15000.00,
    "saidas_previstas": 6000.00,
    "saldo_final_previsto": 37500.00
  }
}
```

---

### 5.2 Fluxo de Caixa por Classificação

Detalha o fluxo separado por `classificacao_financeira` — equivalente ao DRE gerencial de caixa.

**Endpoint:** `GET /api/financeiro/fluxo-caixa/por-classificacao`

Mesmo parâmetros de §5.1, resposta agrupada por classificação:

```json
{
  "grupos": [
    {
      "classificacao": "Vendas à vista",
      "entradas_realizadas": 15000.00,
      "entradas_previstas": 8000.00
    },
    {
      "classificacao": "Fornecedores",
      "saidas_realizadas": 9000.00,
      "saidas_previstas": 4000.00
    }
  ]
}
```

---

### 5.3 Posição de Caixa Atual

Snapshot do saldo atual de todas as contas do tenant.

**Endpoint:** `GET /api/financeiro/fluxo-caixa/posicao-atual`

```json
{
  "data_referencia": "2025-06-10",
  "contas": [
    {
      "conta_corrente_id": 1,
      "descricao": "Conta BB - Operações",
      "banco": "Banco do Brasil",
      "saldo_atual": 18500.00,
      "saldo_pendente": 2300.00,
      "saldo_disponivel": 16200.00
    }
  ],
  "total_disponivel": 16200.00,
  "total_pendente": 2300.00,
  "total_geral": 18500.00
}
```

`saldo_disponivel` = `saldo_atual` - `saldo_pendente` (movimentações pendentes de confirmação)

---

### 5.4 Necessidade de Capital de Giro (NCG)

**Endpoint:** `GET /api/financeiro/fluxo-caixa/ncg`

**Parâmetros:** `periodo_de`, `periodo_ate`

**Lógica:**
```
NCG = Total de saídas previstas no período
    - Total de entradas previstas no período
    - Saldo disponível atual

Se NCG > 0: há necessidade de captação ou antecipação de recebíveis
Se NCG < 0: há sobra que pode ser aplicada
```

---

### 5.5 Orçamento vs. Realizado

**Endpoint:** `GET /api/financeiro/fluxo-caixa/orcamento-vs-realizado`

**Parâmetros:** `ano`, `mes`, `conta_corrente_id?`

```json
{
  "ano": 2025,
  "mes": 6,
  "linhas": [
    {
      "classificacao": "Vendas",
      "orcado": 50000.00,
      "realizado": 47500.00,
      "variacao": -2500.00,
      "variacao_pct": -5.0
    }
  ]
}
```

---

## 6. Máquinas de Estado

### 6.1 Status da Movimentação

```
PENDENTE → CONFIRMADO
         ↘ CANCELADO
```

| Regra | Detalhe |
|---|---|
| Apenas CONFIRMADO entra no saldo | PENDENTE não afeta saldo_atual |
| CANCELADO não pode ser desfeito | Criar novo lançamento de estorno |
| CONFIRMADO via conciliação | Quando extrato é conciliado com movimentação PENDENTE |

---

### 6.2 Status da Conciliação

```
PENDENTE → CONCILIADO (manual ou automático)
         ↘ IGNORADO
CONCILIADO → PENDENTE (desfazer conciliação)
```

---

## 7. Regras de Negócio Consolidadas

| # | Regra |
|---|---|
| CB-01 | Saldo nunca é um campo armazenado — sempre calculado sobre movimentações confirmadas |
| CB-02 | Importação OFX é idempotente — reimportar o mesmo arquivo não duplica lançamentos |
| CB-03 | Conciliação automática só ocorre com match único — ambiguidade vai para manual |
| CB-04 | Tolerância de valor para conciliação automática é configurável por tenant (default: R$ 0,05) |
| CB-05 | Transferência entre contas cria dois lançamentos atômicos — não existe débito sem crédito |
| CB-06 | Linha de extrato IGNORADA não pode ser conciliada — exige desfazer o IGNORADO primeiro |
| CB-07 | Confirmar conciliação de baixa PLANEJADA → a baixa passa para REAL automaticamente |
| CB-08 | Movimentação vinculada a título não pode ser cancelada diretamente — precisa cancelar a baixa primeiro |
| CB-09 | Fluxo de caixa previsto usa `data_vencimento` dos títulos, não data de emissão |
| CB-10 | NCG negativo não é alerta — é informação. O sistema não bloqueia nada com base nela |

---

## III.7 Integração com AP/AR

A integração acontece em dois pontos:

**Ponto 1 — Baixa confirma movimentação:**
Quando uma `titulo_baixa` é criada com `status = 'REAL'`, o sistema cria automaticamente uma `conta_movimentacao` correspondente na conta corrente informada na baixa. Essa movimentação nasce como `CONFIRMADO`.

```
titulo_baixa (REAL)
      ↓ cria automaticamente
conta_movimentacao (CONFIRMADO, titulo_baixa_id preenchido)
```

**Ponto 2 — Conciliação confirma baixa:**
Quando uma `conta_movimentacao` com `status = 'PENDENTE'` (baixa ainda planejada) é conciliada com uma linha do extrato, o sistema confirma a movimentação E confirma a baixa associada.

```
extrato_bancario (PENDENTE)
      ↓ conciliado com
conta_movimentacao (PENDENTE, titulo_baixa_id preenchido)
      ↓ ambos confirmados → baixa passa para REAL
titulo_baixa (REAL)
      ↓ titulo.valor_baixado atualizado
titulo (status atualizado se valor_saldo = 0)
```

---

## 9. Checklist de Implementação

### Backend (Spring Boot)

- [ ] Migrations Liquibase YAML para todas as tabelas deste spec
- [ ] `BancoService`, `ContaCorrenteService`
- [ ] `MovimentacaoService` com controle transacional em transferências
- [ ] `ExtratoOFXParser` — parser de arquivo OFX (biblioteca: `ofx4j` ou implementação própria)
- [ ] `ConciliacaoService` com algoritmo de match automático
- [ ] `FluxoCaixaService` com projeção realizado + previsto
- [ ] View `v_saldo_conta_corrente` como `@Subselect` ou query nativa
- [ ] Listener para `titulo.baixado` → criar `conta_movimentacao` automaticamente
- [ ] Testes de integração para o fluxo de conciliação completo
- [ ] Testes do parser OFX com arquivos de exemplo de diferentes bancos

### Frontend (Angular)

- [ ] Tela de cadastro de banco e conta corrente
- [ ] Tela de extrato por período com saldo acumulado por linha
- [ ] Tela de conciliação: dois painéis lado a lado (extrato banco / movimentações sistema)
- [ ] Ação de drag-and-drop ou clique para vincular linhas na conciliação
- [ ] Indicador visual de linhas não conciliadas (vermelho) e conciliadas (verde)
- [ ] Gráfico de fluxo de caixa: realizado vs. previsto vs. orçado
- [ ] Dashboard de posição de caixa com cards por conta
- [ ] Tela de orçamento mensal por classificação

### Migrations (Liquibase YAML)

- [ ] `v1/010-financeiro-banco.yaml`
- [ ] `v1/011-financeiro-conta-corrente.yaml`
- [ ] `v1/012-financeiro-conta-movimentacao.yaml`
- [ ] `v1/013-financeiro-extrato-bancario.yaml`
- [ ] `v1/014-financeiro-extrato-importacao.yaml`
- [ ] `v1/015-financeiro-tipo-movimentacao-bancaria.yaml`
- [ ] `v1/016-financeiro-orcamento-fluxo.yaml`
- [ ] `v1/017-financeiro-views.yaml` (view de saldo)
- [ ] Todos com `rollback` declarado

---

## III.9 Dependências para Próximos Módulos

| Módulo | O que usa deste spec |
|---|---|
| Tesouraria e boletos | `conta_corrente`, `conta_movimentacao` |
| Controle de aplicações | `conta_corrente.tipo = 'INVESTIMENTO'`, `categoria = 'APLICACAO'` |
| Relatórios gerenciais | `v_saldo_conta_corrente`, `orcamento_fluxo`, fluxo de caixa endpoints |
| Contabilidade / GL | `conta_movimentacao` como origem de lançamentos contábeis |
| Split payment (2027) | `conta_movimentacao` precisará de `valor_retido_governo` e `tipo = 'SPLIT'` |

---

## MÓDULO IV — TESOURARIA E EMISSÃO DE BOLETOS

> Reconstruído nesta versão (v12.1) a partir das entidades e decisões já registradas.
> **DDLs alinhados ao plano de migrations do §12** (sprints 4/5/7 — fonte da verdade dos campos,
> enums e uniques). Cobre entidades (§16), operações (§17), máquinas de estado (§18), regras (§19)
> e cron jobs (§20). A estratégia CNAB (§IV-CNAB) e o QR PIX (§IV-PIX) permanecem logo abaixo.

**Meios de cobrança/recebimento suportados:** Boleto · CNAB · **PIX** · DDA · Cheque

### IV-CNAB. Estratégia de Layout — FEBRABAN primeiro, banco depois

**Decisão:** implementar o **layout padrão FEBRABAN CNAB 240** como motor único
(cobrança: segmentos P/Q/R; pagamento: A/B/J), e tratar diferenças por banco como
**overrides finos** via Strategy — mesmo padrão do `CodigoBarrasGenerator`:

- ~90% do arquivo 240 é idêntico entre bancos (estrutura de header/lote/segmentos é FEBRABAN).
- O que varia por banco: formato/DV do nosso número, códigos de carteira/modalidade,
  uso de campos livres reservados e alguns códigos de ocorrência no retorno.
- `Cnab240LayoutFebraban` (base) + `Cnab240Override{Banco}` sobrescrevendo só esses pontos.
- **CNAB 400 não é padronizado FEBRABAN** (layout legado, um por banco) — implementar apenas
  sob demanda, banco a banco, quando um cliente exigir.
- Validação: piloto com 1 banco em homologação de van/banco antes de habilitar os demais.

> **Eixos distintos:** o campo `layout_cnab` (`CNAB240` | `CNAB400`) grava só o **tamanho do
> registro**; o dialeto FEBRABAN-vs-override é resolvido pelo Strategy no código, não por coluna.

### IV-CNAB.1 De-para campo a campo — cobrança CNAB 240 (FEBRABAN v10.09)

> **Fonte normativa:** `spec/Layout padrao CNAB240 V 10 09 - 14_10_21.pdf` (FEBRABAN v10.09).
> As tabelas abaixo são o **de-para de implementação** (campo da norma → fonte no nosso modelo);
> as posições seguem o leiaute FEBRABAN e devem ser **conferidas 1:1 contra o PDF** nos testes
> unitários de layout e na homologação (passo 2 do §IV-CNAB.3). Onde a norma delega ao banco
> (nosso número, convênio, carteira), a linha está marcada como **override por banco**.

Estrutura do arquivo de cobrança (largura fixa 240, um lote de serviço):

```
Registro 0  Header de arquivo
Registro 1  Header de lote        (tipo de serviço 01 = cobrança)
Registro 3  Detalhe — segmentos P + Q (+ R quando há multa/desconto 2º nível)  × N títulos
Registro 5  Trailer de lote       (quantidades e somatórias)
Registro 9  Trailer de arquivo
```

**Header de arquivo (registro `0`):**

| Campo FEBRABAN | Pos. | Fonte no modelo | Obs. |
|---|---|---|---|
| Código do banco | 001–003 | `banco.codigo_compensacao` | |
| Lote de serviço | 004–007 | `'0000'` fixo | |
| Tipo de registro | 008 | `'0'` fixo | |
| Tipo de inscrição da empresa | 018 | `'2'` (CNPJ) | |
| Nº de inscrição | 019–032 | CNPJ do estabelecimento emissor | ⚠️ CNPJ alfanumérico: conferir NT do leiaute vigente (mesmo ponto em aberto do §14.1) |
| Código do convênio | 033–052 | `cobranca_config.codigo_cedente` | **override por banco** (formato/posicionamento interno variam) |
| Agência + DV | 053–058 | `conta_corrente.agencia` + dígito | |
| Conta + DV | 059–072 | `conta_corrente.conta` + `digito` | |
| Nome da empresa | 073–102 | razão social do tenant (30, sem acento) | |
| Nome do banco | 103–132 | `banco.nome` | |
| Código remessa/retorno | 143 | `'1'` remessa / `'2'` retorno | |
| Data/hora de geração | 144–157 | timestamp da geração | `DDMMAAAA` + `HHMMSS` |
| NSA (nº sequencial do arquivo) | 158–163 | `cnab_remessa.numero_sequencial` | sequência por conta, nunca repete |
| Versão do leiaute do arquivo | 164–166 | constante conforme PDF v10.09 | conferir na homologação |

**Header de lote (registro `1`)** repete os dados da empresa/convênio e adiciona: tipo de
operação (remessa), tipo de serviço `'01'` (cobrança), versão do leiaute do lote (constante do
PDF), nº da remessa e data de gravação — todas as fontes já mapeadas acima.

**Segmento P (registro `3`, dados do título):**

| Campo FEBRABAN | Pos. | Fonte no modelo | Obs. |
|---|---|---|---|
| Nº sequencial no lote | 009–013 | contador por lote | |
| Código do segmento | 014 | `'P'` | |
| Código de movimento remessa | 016–017 | `cnab_remessa_item.tipo_movimento` | de-para abaixo |
| Agência/conta + DVs | 018–037 | `conta_corrente` | |
| Identificação do título no banco (nosso número) | 038–057 | `boleto.nosso_numero` + DV | **override por banco** — formato, tamanho útil e cálculo do DV são a principal variação entre bancos |
| Código da carteira | 058 | `cobranca_config.carteira` | **override por banco** |
| Forma de cadastramento | 059 | `'1'` (cobrança registrada) | |
| Nº do documento de cobrança | 063–077 | `boleto.numero_documento` | |
| Data de vencimento | 078–085 | `boleto.vencimento` | `DDMMAAAA` |
| Valor nominal | 086–100 | `boleto.valor` | 13 int + 2 dec, sem separador |
| Espécie do título | 107–108 | `'02'` duplicata mercantil (default) | parametrizável em `cobranca_config` |
| Data de emissão | 110–117 | `titulo.data_emissao` | |
| Juros de mora (código/data/valor) | 118–141 | `boleto.percentual_mora_mes` | código `2` = taxa mensal; data = vencimento + 1 |
| Desconto 1 (código/data/valor) | 142–165 | `boleto.percentual_desconto` + data limite | |
| Valor do abatimento | 181–195 | ajustes tipo DESCONTO do título | |
| Uso da empresa ("seu número") | 196–220 | `boleto.id` | **chave de conciliação do retorno** — volta intacta no `.RET` |
| Código/prazo para protesto | 221–223 | `cobranca_config.dias_protesto` | código `1` protestar / `3` não protestar |
| Código/prazo para baixa/devolução | 224–227 | parametrizado em `cobranca_config` | prazo p/ banco baixar título vencido não pago |
| Código da moeda | 228 | `'09'` (Real) | |

**Segmento Q (registro `3`, dados do sacado):** tipo de inscrição (018) e CNPJ/CPF (019–033) ←
`titulo.terceiro_cnpj_cpf`; nome (034–073) ← `titulo.terceiro_nome`; endereço, bairro, CEP,
cidade e UF ← cadastro do cliente (desnormalizados no momento da emissão do boleto — mudança
posterior no cadastro não altera boleto já emitido). Sacador/avalista (154–209): em branco (não
usado no MVP).

**Segmento R (registro `3`, opcional):** gerado só quando há multa ou 2º/3º desconto. Multa:
código (`'2'` percentual), data (vencimento + 1) e percentual ← `boleto.percentual_multa`.
Descontos 2 e 3: não usados no MVP.

**Trailers:** trailer de lote (registro `5`) traz a quantidade de registros e as somatórias de
quantidade/valor por carteira (agregado da remessa); trailer de arquivo (registro `9`) traz a
quantidade de lotes (1) e o total de registros.

**De-para dos códigos de movimento (remessa):**

| `tipo_movimento` (§IV-CNAB) | Código FEBRABAN | Efeito no banco |
|---|---|---|
| `INCLUSAO` | `01` | Entrada de título |
| `EXCLUSAO` | `02` | Pedido de baixa |
| `ALTERACAO` (vencimento) | `06` | Alteração de vencimento |
| `ALTERACAO` (demais dados) | `31` | Alteração de outros dados |
| `BLOQUEIO` | sustação de protesto/cobrança — código conforme tabela de movimentos do PDF | conferir na homologação |

**Ocorrências do retorno (`.RET`) → efeito no sistema** (complementa o fluxo do §17.6):

| Ocorrência | Significado | Efeito |
|---|---|---|
| `02` | Entrada confirmada | boleto `EMITIDO` → `REGISTRADO` (`registrado_em`) |
| `03` | Entrada rejeitada | item `ERRO` + alerta crítico em fila; boleto volta a `EMITIDO` (RN do §17.6) |
| `06` | Liquidação | baixa `PLANEJADA` pelo valor pago; tarifa em `valor_tarifa`; **parcial** se valor pago < nominal (§17.6) |
| `09` | Baixa | boleto `CANCELADO` (confirmação da `EXCLUSAO`) |
| `14` | Alteração de vencimento confirmada | atualiza `boleto.vencimento` local (§17.2) |
| `17` | Liquidação após baixa | baixa `PLANEJADA` + alerta (título pode já ter sido renegociado) |
| `19` / `23` | Protesto (confirmação / cartório) | histórico operacional do boleto (não muda o enum — §17.3) |
| `26` / `30` | Instrução/alteração rejeitada | item `ERRO` + alerta |
| `28` | Débito de tarifas | `conta_movimentacao` DÉBITO (tarifa avulsa, sem título) |

Ocorrência sem tratamento mapeado → item `IGNORADO` + log estruturado; **nunca aborta o
arquivo** (os demais itens seguem o processamento normal).

### IV-CNAB.2 De-para campo a campo — pagamento CNAB 240 (segmentos A/B/J)

Remessa de **pagamento em lote** (tipo de serviço de pagamento; forma de lançamento por item):

- **Segmento A** (crédito em conta / TED / PIX transferência): banco, agência e conta do
  favorecido + nome ← **dados bancários do fornecedor**; "seu número" ← `titulo.id`; data de
  pagamento ← vencimento/agendamento da baixa `PLANEJADA`; valor ← valor da baixa. As posições
  231–240 trazem no retorno os códigos de ocorrência (pago/rejeitado), que confirmam ou
  devolvem a baixa — mesma regra do §17.5 (efetivação REAL só na conciliação).
  ⚠️ **Dependência de cadastro:** o modelo atual não tem tabela de dados bancários de
  fornecedor — requisito para o `cadastro-service` (`fornecedor_conta_bancaria`: banco,
  agência, conta, tipo, chave PIX) antes de implementar remessa de pagamento.
- **Segmento B** (complemento do favorecido): CNPJ/CPF e endereço ← cadastro do fornecedor;
  quando a forma de lançamento é PIX, carrega a **chave PIX** conforme a NT FEBRABAN de PIX no
  CNAB (leiaute do B varia por tipo de chave — conferir no PDF).
- **Segmento J** (pagamento de boleto — casa com o fluxo DDA do §17.7): código de barras (44
  posições) ← `dda_boleto.codigo_barras`; valor nominal, vencimento e cedente ← dados do DDA;
  data/valor do pagamento ← baixa `PLANEJADA` do título vinculado; complemento J-52 com as
  inscrições de pagador/beneficiário.

### IV-CNAB.3 Banco piloto e plano de homologação

**Banco piloto: Banco do Brasil (001)** — default técnico (convênio de cobrança amplamente
documentado e validador público de arquivo). A escolha final é **comercial**: se o primeiro
tenant pagante concentrar movimento em outro banco, troca-se o piloto — o motor FEBRABAN é o
mesmo, muda só o override (nosso número/DV, carteira, códigos de convênio).

Plano de homologação (pré-requisito para habilitar cobrança em produção):

1. Contratar convênio de cobrança em **ambiente de homologação** junto ao banco/van.
2. **Conferência 1:1** das posições geradas contra o PDF FEBRABAN v10.09 (e o manual do
   convênio do banco) — testes unitários de layout por segmento: largura fixa 240, tipos
   numérico/alfa, zeros/brancos de preenchimento.
3. Gerar **remessa sintética** com ≥ 5 boletos cobrindo: valor quebrado (ex.: R$ 101,01), com
   multa+mora, com desconto, vencimento em feriado (RN-FUND-001) e sacados PF e PJ.
4. Submeter ao **validador de arquivo do banco**; corrigir até aceite sem ressalvas.
5. Processar **retorno de homologação** cobrindo as ocorrências `02`, `03`, `06` (total e
   parcial), `09` e `28` — incluindo teste de idempotência (reimportar o mesmo `.RET` não
   duplica efeito).
6. Conciliar com **extrato OFX de homologação** — baixas `PLANEJADA` → `REAL` de ponta a ponta.
7. **Produção assistida:** 1 ciclo real com poucos boletos (tenant interno/parceiro) antes da
   liberação geral.
8. Critério de aceite: **2 ciclos consecutivos** com 100% dos boletos registrados e liquidados
   sem intervenção manual. Só então iniciar o 2º banco (implementando apenas o override).

### IV-PIX. Cobrança PIX (QR dinâmico)

```sql
financeiro.pix_cobranca
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
titulo_id           BIGINT NOT NULL REFERENCES titulo
conta_corrente_id   BIGINT NOT NULL REFERENCES conta_corrente
txid                VARCHAR(35) NOT NULL      -- identificador na API PIX do PSP
qr_code_payload     TEXT NOT NULL             -- copia-e-cola
valor               NUMERIC(15,2) NOT NULL
expiracao           TIMESTAMPTZ NOT NULL
status              VARCHAR(15) NOT NULL      -- 'ATIVA' | 'PAGA' | 'EXPIRADA' | 'CANCELADA'
e2e_id              VARCHAR(35)               -- endToEndId da liquidação
pago_em             TIMESTAMPTZ
created_at          TIMESTAMPTZ NOT NULL
UNIQUE (tenant_id, txid)
```

**Fluxo:**
1. `POST /api/financeiro/titulos/receber/{id}/pix` → cria cobrança na API PIX do PSP
   (provider configurável em `cobranca_config`), grava `txid` + QR.
2. Webhook do PSP (`PIX recebido`) → localiza por `txid`, cria `titulo_baixa` REAL
   com `tipo_baixa.meio = 'PIX'` e `conta_movimentacao` CONFIRMADO. Idempotente por `e2e_id`.
3. Conciliação OFX reforça por `e2e_id`/`txid` no histórico.
4. Split payment (2027+): PIX é instrumento com split — segregação ocorre na liquidação (§1.4.2 Passo 8).

> **PIX × CNAB:** o PIX liquida direto pela API/webhook (baixa REAL, dinheiro já caiu) — não passa
> por retorno CNAB nem pela máquina PLANEJADA. Só o **boleto liquidado por retorno CNAB** nasce
> PLANEJADA (§17.6), porque o retorno é aviso, não a compensação no extrato.

> **Decisão registrada — entrada multi-canal de NF (AP):** portal do fornecedor (iSupplier-like),
> OCR de PDF e EDI **ficam fora do escopo** desta versão; entrada de NF é via Kafka (NF-e) e manual.
> Registrado no roadmap (§14).

---

## §16 — Entidades

Todas as tabelas moram no schema `financeiro`, carregam `tenant_id BIGINT NOT NULL` e as
colunas de auditoria (`created_at/created_by`, e `updated_at/updated_by` quando mutáveis),
seguindo o padrão dos Módulos I–III. **Nomes de campo, enums e uniques abaixo espelham o §12.**

### 16.1 `cobranca_config` — Configuração de cobrança por conta (§12.6 / migration 023)

Uma configuração por conta corrente (`UNIQUE (tenant_id, conta_corrente_id)`). Concentra convênio
de boleto/CNAB e, como extensão, os dados de PSP PIX.

```sql
financeiro.cobranca_config
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
conta_corrente_id   BIGINT NOT NULL REFERENCES conta_corrente
codigo_cedente      VARCHAR(20) NOT NULL      -- convênio/cedente no banco
carteira            VARCHAR(10)
modalidade          VARCHAR(15)               -- 'SIMPLES' | 'VINCULADA' | 'DESCONTADA'
nosso_numero_atual  BIGINT DEFAULT 0          -- sequencial do nosso número (lock atômico)
instrucoes          TEXT                      -- instruções de cobrança padrão
dias_protesto       INT                       -- null = não protestar
dias_negativacao    INT                       -- null = não negativar
layout_cnab         VARCHAR(10)               -- 'CNAB240' | 'CNAB400'

-- Extensão PIX (o mesmo registro serve boleto e PIX da conta)
pix_provider        VARCHAR(30)               -- identificador do PSP (ex.: 'ASAAS','GERENCIANET')
pix_chave           VARCHAR(140)              -- chave PIX recebedora
pix_expiracao_seg   INT DEFAULT 86400         -- TTL do QR dinâmico

ativo               BOOLEAN DEFAULT TRUE
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
updated_at          TIMESTAMPTZ
updated_by          VARCHAR(100)
UNIQUE (tenant_id, conta_corrente_id)
```

> **Sequencial do nosso número:** `nosso_numero_atual` é incrementado sob lock por conta/cedente
> (mesmo padrão do `DistributedLock` dos jobs de billing) — dois boletos nunca compartilham nosso número.

### 16.2 `boleto` — Boleto emitido (§12.6 / migration 024)

```sql
financeiro.boleto
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
titulo_id           BIGINT NOT NULL REFERENCES titulo   -- sempre a receber
conta_corrente_id   BIGINT NOT NULL REFERENCES conta_corrente

nosso_numero        VARCHAR(30) NOT NULL
codigo_barras       VARCHAR(44) NOT NULL       -- 44 dígitos FEBRABAN
linha_digitavel     VARCHAR(54) NOT NULL
numero_documento    VARCHAR(30)               -- normalmente = titulo.numero_documento
valor               NUMERIC(15,2) NOT NULL
vencimento          DATE NOT NULL
percentual_multa    NUMERIC(5,2)
percentual_mora_mes NUMERIC(5,2)
percentual_desconto NUMERIC(5,2)

status              VARCHAR(15) NOT NULL       -- ver §18.1
                    -- 'EMITIDO' | 'REGISTRADO' | 'PAGO' | 'CANCELADO' | 'VENCIDO'
registrado_em       TIMESTAMPTZ               -- confirmação de registro no banco (retorno)
pago_em             DATE                      -- data do crédito (retorno)
valor_pago          NUMERIC(15,2)
valor_tarifa        NUMERIC(15,2)             -- tarifa bancária do retorno

url_pdf             VARCHAR(500)
motivo_cancelamento VARCHAR(200)

created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
updated_at          TIMESTAMPTZ
updated_by          VARCHAR(100)
UNIQUE (tenant_id, conta_corrente_id, nosso_numero)
INDEX idx_boleto_titulo (tenant_id, titulo_id)
INDEX idx_boleto_status (tenant_id, status)
```

### 16.3 `cnab_remessa` / `cnab_remessa_item` — Arquivo de remessa (§12.6 / migration 025)

```sql
financeiro.cnab_remessa
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
conta_corrente_id   BIGINT NOT NULL REFERENCES conta_corrente
tipo                VARCHAR(15) NOT NULL       -- 'COBRANCA' | 'PAGAMENTO'
layout_cnab         VARCHAR(10) NOT NULL       -- 'CNAB240' | 'CNAB400'
numero_sequencial   INT NOT NULL              -- NSA — sequencial do arquivo por banco
nome_arquivo        VARCHAR(300) NOT NULL
conteudo_ref        VARCHAR(500)              -- caminho/URL do .REM gerado
total_itens         INT NOT NULL
valor_total         NUMERIC(15,2) NOT NULL
status              VARCHAR(15) NOT NULL       -- ver §18.2
                    -- 'GERADO' | 'ENVIADO' | 'PROCESSADO' | 'ERRO'
gerado_em           TIMESTAMPTZ NOT NULL
enviado_em          TIMESTAMPTZ
created_by          VARCHAR(100) NOT NULL
UNIQUE (tenant_id, conta_corrente_id, tipo, numero_sequencial)

financeiro.cnab_remessa_item
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
remessa_id          BIGINT NOT NULL REFERENCES cnab_remessa
titulo_id           BIGINT NOT NULL REFERENCES titulo
boleto_id           BIGINT REFERENCES boleto             -- cobrança
tipo_movimento      VARCHAR(15) NOT NULL       -- 'INCLUSAO' | 'EXCLUSAO' | 'ALTERACAO' | 'BLOQUEIO'
segmento            VARCHAR(2)                -- 'P'|'Q'|'R'|'A'|'B'|'J'
valor               NUMERIC(15,2) NOT NULL
situacao            VARCHAR(15) NOT NULL DEFAULT 'PENDENTE'  -- 'PENDENTE'|'CONFIRMADO'|'REJEITADO'
UNIQUE (tenant_id, remessa_id, titulo_id, tipo_movimento)
```

### 16.4 `cnab_retorno` / `cnab_retorno_item` — Arquivo de retorno (§12.6 / migration 026)

```sql
financeiro.cnab_retorno
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
conta_corrente_id   BIGINT NOT NULL REFERENCES conta_corrente
layout_cnab         VARCHAR(10) NOT NULL       -- 'CNAB240' | 'CNAB400'
nome_arquivo        VARCHAR(300) NOT NULL
numero_sequencial   INT                       -- NSA do retorno
total_itens         INT NOT NULL
valor_total         NUMERIC(15,2)
status              VARCHAR(15) NOT NULL       -- 'PROCESSADO' | 'PARCIAL' | 'ERRO'
importado_em        TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
UNIQUE (tenant_id, conta_corrente_id, nome_arquivo)   -- reimportar mesmo arquivo é idempotente

financeiro.cnab_retorno_item
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
retorno_id          BIGINT NOT NULL REFERENCES cnab_retorno
titulo_id           BIGINT REFERENCES titulo
boleto_id           BIGINT REFERENCES boleto
nosso_numero        VARCHAR(30)
codigo_ocorrencia   VARCHAR(5) NOT NULL       -- ocorrência FEBRABAN (ex.: '06' liquidação)
descricao_ocorrencia VARCHAR(200)
data_ocorrencia     DATE
valor_principal     NUMERIC(15,2)
valor_tarifa        NUMERIC(15,2)
valor_acrescimos    NUMERIC(15,2)             -- juros/multa recebidos
valor_desconto      NUMERIC(15,2)
valor_liquido       NUMERIC(15,2)
status              VARCHAR(15) NOT NULL DEFAULT 'PENDENTE'  -- 'PENDENTE'|'BAIXADO'|'IGNORADO'|'ERRO'
erro_processamento  VARCHAR(300)
INDEX idx_retorno_item (tenant_id, retorno_id, status)
```

> **Baixa nasce PLANEJADA (§12.6):** as baixas geradas por retorno CNAB **sempre** nascem
> `PLANEJADA`; a conciliação com o extrato (§III 4.3) é o que as promove a `REAL`. Ver §17.6.

### 16.5 `cheque` — Cheques emitidos e recebidos (§12.6 / migration 027)

```sql
financeiro.cheque
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
natureza            VARCHAR(10) NOT NULL       -- 'EMITIDO' (pagar) | 'RECEBIDO' (receber)
conta_corrente_id   BIGINT REFERENCES conta_corrente   -- conta do cheque emitido
titulo_id           BIGINT REFERENCES titulo           -- título vinculado
pessoa_id           BIGINT                    -- emitente (recebido) / favorecido (emitido)

banco_codigo        VARCHAR(10)
agencia             VARCHAR(20)
conta               VARCHAR(30)
numero              VARCHAR(20) NOT NULL
valor               NUMERIC(15,2) NOT NULL
data_bom_para       DATE                      -- pré-datado; alimenta o cron de alerta diário
status              VARCHAR(15) NOT NULL       -- ver §18.3
                    -- 'EMITIDO' | 'COMPENSADO' | 'DEVOLVIDO' | 'CANCELADO' | 'SUSTADO'
compensacao_em      DATE
motivo_devolucao    VARCHAR(10)               -- alínea de devolução (ex.: '11','12','21')

created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
updated_at          TIMESTAMPTZ
updated_by          VARCHAR(100)
UNIQUE (tenant_id, conta_corrente_id, numero)
INDEX idx_cheque_status (tenant_id, natureza, status)
INDEX idx_cheque_bompara (tenant_id, data_bom_para) WHERE status = 'EMITIDO'
```

### 16.6 `aplicacao_financeira` — Aplicações e resgates (§12.6 / migration 028)

Só permitida em conta corrente do tipo `INVESTIMENTO`.

```sql
financeiro.aplicacao_financeira
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
conta_corrente_id   BIGINT NOT NULL REFERENCES conta_corrente   -- tipo INVESTIMENTO
descricao           VARCHAR(200) NOT NULL
tipo                VARCHAR(20) NOT NULL       -- 'CDB' | 'LCI' | 'LCA' | 'FUNDOS'
data_aplicacao      DATE NOT NULL
valor_aplicado      NUMERIC(15,2) NOT NULL
data_vencimento     DATE                      -- null = liquidez diária
indexador           VARCHAR(20)               -- 'CDI'|'SELIC'|'PRE'|'IPCA'
taxa                NUMERIC(9,4)
valor_resgatado     NUMERIC(15,2)
data_resgate        DATE
rendimento_bruto    NUMERIC(15,2)
ir_retido           NUMERIC(15,2)
rendimento_liquido  NUMERIC(15,2)
status              VARCHAR(15) NOT NULL       -- ver §18.4  'ATIVO' | 'RESGATADO' | 'VENCIDO'
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
updated_at          TIMESTAMPTZ
updated_by          VARCHAR(100)
INDEX idx_aplic_status (tenant_id, status)
```

### 16.7 `dda_boleto` — Boletos DDA a pagar (§12.6 / migration 029)

DDA (Débito Direto Autorizado): boletos que o banco disponibiliza como **a pagar** para o
CNPJ do tenant. Entram como candidatos a virar/vincular título a pagar.

```sql
financeiro.dda_boleto
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
conta_corrente_id   BIGINT NOT NULL REFERENCES conta_corrente
linha_digitavel     VARCHAR(54) NOT NULL
codigo_barras       VARCHAR(44) NOT NULL
cedente_nome        VARCHAR(200)
cedente_cnpj        VARCHAR(14)
valor               NUMERIC(15,2) NOT NULL
vencimento          DATE NOT NULL
titulo_id           BIGINT REFERENCES titulo   -- preenchido ao vincular/gerar título
status              VARCHAR(15) NOT NULL DEFAULT 'IMPORTADO'
                    -- 'IMPORTADO' | 'VINCULADO' | 'PAGO' | 'IGNORADO'
importado_em        TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
UNIQUE (tenant_id, codigo_barras)          -- idempotência do DDA
INDEX idx_dda_status (tenant_id, status)
```

---

## §17 — Operações

### 17.1 Emitir boleto

**Endpoint:** `POST /api/financeiro/titulos/receber/{titulo_id}/boleto`

**Body:** `{ "instrucoes": ["...","..."] }` (usa a `cobranca_config` única da conta)

**Fluxo:**
1. Validar título a receber pertence ao tenant e está `EM_ABERTO`/`EMITIDO` (§2 máquina de status_titulo).
2. Resolver a `cobranca_config` da conta corrente.
3. Alocar `nosso_numero` sob lock (§16.1) e calcular DV conforme banco.
4. Gerar `codigo_barras` (44) e `linha_digitavel` (`CodigoBarrasGenerator` — mesmo motor do CNAB).
5. Persistir `boleto` com `status = 'EMITIDO'`.
6. Renderizar PDF (armazenar `url_pdf`).
7. **Não** cria movimentação nem baixa — boleto emitido é promessa de recebimento, não caixa.

**Regra:** um título pode ter vários boletos ao longo do tempo (2ª via, reemissão pós-cancelamento),
mas **no máximo um** em status `EMITIDO`/`REGISTRADO` simultaneamente.

### 17.2 Segunda via / alteração de vencimento

**Endpoint:** `PATCH /api/financeiro/boletos/{id}` — altera vencimento/instruções e regenera PDF.
Se o boleto já está `REGISTRADO`, a alteração de vencimento exige remessa CNAB de movimento
`ALTERACAO` — não basta mudar no banco de dados.

### 17.3 Cancelar boleto

**Endpoint:** `POST /api/financeiro/boletos/{id}/cancelar` (`{ "motivo": "..." }`)
- Boleto `REGISTRADO` → gera item de remessa CNAB movimento `EXCLUSAO` (baixa/cancelamento no banco)
  e passa a `CANCELADO` só após confirmação do retorno.
- Boleto apenas `EMITIDO` (nunca registrado) → cancela direto (`CANCELADO`).
- **Nunca** baixa o título — o título só é baixado por liquidação (retorno) ou pela operação de AR.

> **Protesto/negativação** são **comandos de remessa** (`dias_protesto`/`dias_negativacao` da
> `cobranca_config`), não um status de boleto — o boleto permanece `REGISTRADO` até liquidar,
> cancelar ou vencer. O acompanhamento do protesto é operacional (histórico), não muda o enum.

### 17.4 Gerar remessa CNAB de cobrança

**Endpoint:** `POST /api/financeiro/cnab/remessa/cobranca`
**Body:** `{ "conta_corrente_id": 1, "boleto_ids": [..] }` (ou filtro por período/status)

**Fluxo:**
1. Selecionar boletos `EMITIDO` da conta ainda não incluídos em remessa aberta.
2. Alocar NSA (`numero_sequencial`) por banco.
3. Montar header de arquivo/lote + segmentos **P** (título), **Q** (sacado/pagador),
   **R** (multa/desconto/juros) via `Cnab240LayoutFebraban` + override do banco.
4. Persistir `cnab_remessa` (`tipo = 'COBRANCA'`, `status = 'GERADO'`) + `cnab_remessa_item`
   por boleto (`tipo_movimento = 'INCLUSAO'`).
5. Gravar arquivo `.REM` (`conteudo_ref`).
6. Ao marcar como enviada (`PATCH .../enviar`): `status = 'ENVIADO'`; boletos passam a aguardar
   confirmação de registro (retorno).

### 17.5 Gerar remessa CNAB de pagamento

**Endpoint:** `POST /api/financeiro/cnab/remessa/pagamento`
**Body:** `{ "conta_corrente_id": 1, "titulo_ids": [..] }` (títulos **a pagar** aprovados)

**Fluxo:**
1. Selecionar títulos a pagar com baixa `PLANEJADA` e dentro da alçada aprovada (§4.10 Alçada).
2. Montar segmentos **A** (crédito em conta/TED/PIX), **B** (dados do favorecido/PIX),
   **J** (pagamento de boleto/tributo) conforme o meio de cada título.
3. Persistir remessa (`tipo = 'PAGAMENTO'`) + itens.
4. Ao confirmar retorno (§17.6): cada pagamento efetivado confirma a **baixa** do título +
   `conta_movimentacao` DÉBITO CONFIRMADO.

### 17.6 Importar retorno CNAB

**Endpoint:** `POST /api/financeiro/cnab/retorno` (`multipart` com `.RET` + `conta_corrente_id`)

**Fluxo:**
1. Parse do arquivo conforme `layout_cnab` da conta.
2. Idempotência: `UNIQUE (tenant_id, conta_corrente_id, nome_arquivo)` — reimportar não duplica efeito.
3. Persistir `cnab_retorno` + `cnab_retorno_item` (um por ocorrência), `status = 'PENDENTE'`.
4. Para cada item, aplicar efeito por `codigo_ocorrencia` (FEBRABAN):
   - **Entrada confirmada / registro** → boleto `EMITIDO` → `REGISTRADO` (`registrado_em`).
   - **Liquidação** → boleto `PAGO`; criar `titulo_baixa` **`PLANEJADA`** com
     `tipo_baixa.meio = 'BOLETO'`, valor = `valor_liquido`; guardar `valor_acrescimos`
     (juros/multa) e `valor_desconto`; tarifa bancária como `conta_movimentacao` DÉBITO (despesa).
   - **Liquidação parcial** (ocorrência FEBRABAN de baixa parcial) → baixa **`PLANEJADA` parcial**
     pelo `valor_liquido`; o título **continua `EM_ABERTO`** com saldo residual (§2 — baixa parcial
     do Módulo I) e o boleto pode permanecer para reapresentação do saldo. Não fecha o título.
   - **Baixa/devolução** → refletir no `status` do boleto (`CANCELADO`); não altera o saldo do
     título além do que a ocorrência determina.
   - **Rejeição** (ex.: "CPF/CNPJ inválido", "agência inválida") → `cnab_retorno_item.status = 'ERRO'`,
     **persistir código+motivo da ocorrência** (`codigo_ocorrencia`/`motivo`), boleto volta a `EMITIDO` e
     dispara **alerta crítico** ao operador (fica na fila até tratado). O **título permanece `EM_ABERTO`**
     — o status de registro bancário é do **boleto**, não do título (não há estado "Em Processamento" no
     título; boleto rejeitado não trava o recebível). Operador corrige o cadastro e reemite (§17.1).
5. Marcar item `status = 'BAIXADO'` (ou `IGNORADO`/`ERRO`). Erros por item não abortam o arquivo
   (`cnab_retorno.status = 'PARCIAL'`).

> **Por que PLANEJADA e não REAL:** o retorno CNAB é o **aviso** do banco de que o boleto foi pago,
> não a confirmação de que o dinheiro compensou na conta. A promoção `PLANEJADA → REAL` acontece na
> **conciliação com o extrato** (§III 4.3), quando a linha do OFX bate com a movimentação. Assim
> a máquina de baixa fica coerente com §4.6.1 (REAL só sai por estorno) e com a correção de
> "desfazer conciliação" — que desvincula extrato↔movimentação sem reverter uma REAL.

### 17.7 Importar e vincular DDA a título a pagar

- **Importar feed DDA** (`POST /api/financeiro/dda/importar`): recebe o arquivo/consulta do banco,
  insere `dda_boleto` `status = 'IMPORTADO'`; idempotente por `codigo_barras`.
- **Vincular** (`POST /api/financeiro/dda/{id}/vincular` `{ "titulo_id": 123 }`): exige `valor`/
  `vencimento` compatíveis com o título (tolerância configurável) → `status = 'VINCULADO'`.
- **Gerar título** (`POST /api/financeiro/dda/{id}/gerar-titulo`): cria `titulo` a pagar
  `origem = 'DDA'`, pessoa = cedente (dedup por CNPJ), `EM_ABERTO`.
- DDA ignorado (`status = 'IGNORADO'`) some da fila de conciliação.

### 17.8 Cheques

- **Receber cheque** (`POST /api/financeiro/cheques` natureza `RECEBIDO`): entra em `EMITIDO`
  (em carteira/registrado); se vinculado a título a receber, gera baixa **PLANEJADA** (só vira
  REAL na compensação).
- **Depositar** (`POST .../{id}/depositar`): ação operacional (registra depósito); o cheque
  permanece `EMITIDO` até o banco compensar ou devolver.
- **Compensar** (`ChequeCompensacaoJob` ou manual): `EMITIDO`→`COMPENSADO` (`compensacao_em`);
  confirma a baixa do título (PLANEJADA→REAL) + `conta_movimentacao` CRÉDITO CONFIRMADO.
- **Devolver** (`POST .../{id}/devolver` `{ "alinea": "11" }`): `EMITIDO`→`DEVOLVIDO`; **estorna**
  a baixa (§4.6.1); título volta a `EM_ABERTO`; opcional reapresentar (novo registro/cheque).
- **Sustar** (`POST .../{id}/sustar`): `EMITIDO`→`SUSTADO` (contraordem); estorna baixa pendente.
- **Cheque emitido** (natureza `EMITIDO`, pagar): `EMITIDO`→`COMPENSADO` confirma baixa do título a pagar.

### 17.9 Aplicação / resgate

- **Aplicar** (`POST /api/financeiro/aplicacoes`): DÉBITO na `conta_corrente` origem
  (`categoria = 'APLICACAO'`), cria `aplicacao_financeira` `ATIVO`.
- **Resgatar** (`POST .../{id}/resgatar`): CRÉDITO na conta (`categoria = 'RESGATE'`) pelo
  `valor_resgatado`; registra `rendimento_bruto`, `ir_retido`, `rendimento_liquido`;
  `status = 'RESGATADO'`. O rendimento líquido é receita financeira; IR retido é despesa/tributo —
  refletidos no GL (§37).

---

## §18 — Máquinas de estado

### 18.1 `boleto.status`

```
EMITIDO ──(remessa INCLUSAO + retorno de registro)──► REGISTRADO
EMITIDO ──(cancelar, nunca registrado)──────────────► CANCELADO
REGISTRADO ──(retorno liquidação)───────────────────► PAGO        [terminal — baixa PLANEJADA do título]
REGISTRADO ──(remessa EXCLUSAO + retorno)───────────► CANCELADO   [terminal]
REGISTRADO ──(BoletoVencidoJob, vencido)────────────► VENCIDO
VENCIDO ──(retorno liquidação)──────────────────────► PAGO
VENCIDO ──(remessa EXCLUSAO + retorno)──────────────► CANCELADO
```
- `PAGO`/`CANCELADO` são terminais. Reemissão cria **novo** boleto.
- Protesto/negativação são comandos de remessa, não status (§17.3).

### 18.2 `cnab_remessa.status`

```
GERADO ──► ENVIADO ──► PROCESSADO   (todos os itens confirmados no retorno)
GERADO ──► ERRO                      (falha na geração)
ENVIADO ──► ERRO                     (arquivo rejeitado pelo banco)
```

### 18.3 `cheque.status`

```
EMITIDO ─► COMPENSADO        [baixa REAL do título]
EMITIDO ─► DEVOLVIDO ─►(reapresentar)─► EMITIDO   [estorna baixa]
EMITIDO ─► SUSTADO           [estorna baixa pendente]
EMITIDO ─► CANCELADO
```
- Vale para cheque `RECEBIDO` (a receber) e `EMITIDO` (a pagar) — a natureza distingue o efeito no título.

### 18.4 `aplicacao_financeira.status`

```
ATIVO ─► RESGATADO        (resgate total)
ATIVO ─► VENCIDO          (vencimento sem resgate — AplicacaoVencidaJob sinaliza)
```

---

## §19 — Regras de negócio

- **RN-TES-01** Boleto emitido não é caixa: nenhuma emissão/registro cria movimentação ou baixa.
  A **liquidação por retorno** gera baixa **PLANEJADA**; a conciliação com o extrato a torna REAL.
- **RN-TES-02** Nosso número é único e alocado sob lock por conta/cedente; nunca reutilizado.
- **RN-TES-03** Retorno CNAB é idempotente por nome de arquivo; reprocessar não duplica baixa.
- **RN-TES-04** Alterar vencimento/valor de boleto já registrado exige movimento CNAB `ALTERACAO`
  — o dado local só muda após a confirmação do retorno (o banco é a fonte da verdade do registro).
- **RN-TES-05** Cheque recebido gera baixa **PLANEJADA**; só a compensação a torna **REAL**.
  Devolução/sustação **estorna** (nunca "desfaz" baixa REAL sem estorno — §4.6.1).
- **RN-TES-06** Remessa de pagamento só inclui títulos dentro da **alçada aprovada** (§4.10).
- **RN-TES-07** DDA é idempotente por código de barras; vincular exige compatibilidade valor/vencimento.
- **RN-TES-08** Split payment (2027+): quando ativo, a liquidação do boleto/PIX segrega o valor do
  governo na própria baixa (`tipo_baixa.meio = 'SPLIT_PAYMENT'`, §1.4.2 Passo 8) — não é passo manual.
- **RN-TES-09** `layout_cnab` grava só `CNAB240`/`CNAB400` (tamanho do registro); o dialeto
  FEBRABAN×override é resolvido pelo Strategy no código (§IV-CNAB).

---

## §20 — Cron jobs

| Job | Frequência | Ação |
|---|---|---|
| `BoletoVencidoJob` | diária | Boletos `REGISTRADO` vencidos → `VENCIDO`; aplica instrução de protesto/negativação conforme `cobranca_config`; sinaliza inadimplência (Módulo VI) |
| `AplicacaoVencidaJob` | diária | Aplicações `ATIVO` com `data_vencimento < hoje` → `VENCIDO` + alerta de resgate |
| `ChequeCompensacaoJob` | diária | Cheques `EMITIDO` com `data_bom_para ≤ hoje` → tenta compensar |
| `PixExpiradoJob` | diária | `pix_cobranca` `ATIVA` com `expiracao < now()` → `EXPIRADA` |
| `CnabRetornoPollJob` | configurável | (Quando houver integração VAN/API) baixa retornos disponíveis e chama §17.6 |

Todos rodam sob `DistributedLock` (Redis) e persistem execução em `job_execution` — mesmo padrão
dos jobs de billing.

---

## MÓDULO V — CONTABILIDADE E GL (General Ledger)

> Reconstruído nesta versão (v12.1). **DDLs alinhados ao §12.8** (sprint 5, migrations 002–008).
> Cobre entidades (§36), geração automática a partir dos eventos do financeiro (§37), demonstrações
> — Razão (§38), Balanço (§39), DRE (§40), Livro Diário (§41) — fechamento (§42), conciliação GL ×
> sub-ledgers (§43) e regras (§44). A dimensão matriz/filial (§36.7) segue a decisão já registrada.

**Escopo:** GL por **partidas dobradas** consumindo os eventos do financeiro. O contábil é
**consumidor**: não origina títulos nem movimentações — traduz fatos financeiros em lançamentos.

---

## §36 — Entidades

Schema **`contabil`**. Todas com `tenant_id BIGINT NOT NULL` + auditoria (exceto o template global).

### 36.1 `plano_contas_template` — Elenco oficial editável (§12.8 / migration 007)

Template **global** (sem `tenant_id`) semeado no onboarding do tenant. **Editável** — sem bloqueio
(decisão §F6): é ponto de partida, não camisa de força. `codigo_pai` referencia **por código**
(não por id) para viabilizar a cópia no `TenantAtivacaoListener`.

```sql
contabil.plano_contas_template
─────────────────────────────────────────────
id                  BIGSERIAL PK
versao              INT NOT NULL DEFAULT 1
codigo              VARCHAR(30) NOT NULL       -- '1.1.1.02'
descricao           VARCHAR(200) NOT NULL
tipo                VARCHAR(20) NOT NULL       -- ATIVO|PASSIVO|PATRIMONIO_LIQUIDO|RECEITA|CUSTO|DESPESA
natureza            VARCHAR(10) NOT NULL       -- 'DEVEDORA' | 'CREDORA'
nivel               INT NOT NULL
codigo_pai          VARCHAR(30)                -- referência por código (não por id)
aceita_lancamento   BOOLEAN NOT NULL           -- analítica = TRUE; sintética = FALSE
retificadora        BOOLEAN NOT NULL DEFAULT FALSE  -- depreciação/PCLD: subtrai do grupo no BP
ativo               BOOLEAN NOT NULL DEFAULT TRUE
UNIQUE (versao, codigo)
```

### 36.2 `conta` — Plano de contas do tenant (§12.8 / migration 002)

```sql
contabil.conta
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
codigo              VARCHAR(30) NOT NULL       -- '1.1.1.02'
descricao           VARCHAR(200) NOT NULL
conta_pai_id        BIGINT REFERENCES conta    -- hierarquia; serviço valida ausência de ciclo
nivel               INT NOT NULL               -- derivado do pai — mantido pelo serviço
tipo                VARCHAR(20) NOT NULL        -- ATIVO|PASSIVO|PATRIMONIO_LIQUIDO|RECEITA|CUSTO|DESPESA
natureza            VARCHAR(10) NOT NULL        -- DEVEDORA | CREDORA
aceita_lancamento   BOOLEAN NOT NULL DEFAULT TRUE  -- analítica (folha) = TRUE; sintética = FALSE
retificadora        BOOLEAN NOT NULL DEFAULT FALSE -- depreciação/PCLD: subtrai do grupo no BP
ativo               BOOLEAN DEFAULT TRUE
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
updated_at          TIMESTAMPTZ
updated_by          VARCHAR(100)
UNIQUE (tenant_id, codigo)
INDEX idx_conta_pai (tenant_id, conta_pai_id)
```

> **Analítica × sintética:** lançamentos só entram em conta com `aceita_lancamento = TRUE`
> (folha da árvore). Sintética consolida saldos dos filhos. Mesma regra do centro de custo (§F3).
> **Sem tabela de filial** — a dimensão é `cadastros.estabelecimento` (FK lógica UUID, §36.7).

### 36.3 `periodo` — Período contábil (§12.8 / migration 003)

```sql
contabil.periodo
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
estabelecimento_id  UUID                       -- null = consolidado do grupo (§36.7)
competencia         VARCHAR(7) NOT NULL         -- 'YYYY-MM'
template_versao     INT                        -- versão do plano de contas vigente no período
status              VARCHAR(10) NOT NULL        -- 'ABERTO' | 'FECHADO' | 'BLOQUEADO'
fechado_em          TIMESTAMPTZ
fechado_by          VARCHAR(100)
created_at          TIMESTAMPTZ NOT NULL
UNIQUE (tenant_id, estabelecimento_id, competencia)
```

> `BLOQUEADO` = trava administrativa temporária (impede lançamento sem fechar de vez).
> Permite fechar por estabelecimento individualmente antes do consolidado.

### 36.4 `lancamento` — Cabeçalho do lançamento (§12.8 / migration 004)

```sql
contabil.lancamento
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
periodo_id          BIGINT NOT NULL REFERENCES periodo
numero              VARCHAR(20) NOT NULL       -- sequencial por período SEM lacunas (Livro Diário)
data_lancamento     DATE NOT NULL              -- data de competência
tipo                VARCHAR(15) NOT NULL        -- 'AUTOMATICO' | 'MANUAL' | 'ABERTURA' | 'ENCERRAMENTO'
historico           VARCHAR(500) NOT NULL
origem              VARCHAR(30)                -- TITULO_BAIXA|MOVIMENTACAO|APURACAO_FISCAL|
                                               --  EMPRESTIMO|APLICACAO|MANUAL
origem_id           BIGINT                     -- id do fato financeiro (rastreabilidade/idempotência)
status              VARCHAR(15) NOT NULL DEFAULT 'ATIVO'  -- 'ATIVO' | 'ESTORNADO'
estorno_de_id       BIGINT REFERENCES lancamento
created_at          TIMESTAMPTZ NOT NULL
created_by          VARCHAR(100) NOT NULL
INDEX idx_lanc_periodo (tenant_id, periodo_id)
UNIQUE (tenant_id, periodo_id, numero)         -- numeração gapless (§12.8)
UNIQUE (tenant_id, origem, origem_id) WHERE origem <> 'MANUAL'  -- idempotência 1 fato = 1 lançamento
```

> **Duas unicidades:** `(periodo_id, numero)` garante a numeração legal sem lacunas do Livro Diário;
> `(origem, origem_id)` parcial garante que um mesmo fato financeiro não gere dois lançamentos (§37).

### 36.5 `lancamento_partida` — Partidas (débito/crédito) (§12.8 / migration 005)

```sql
contabil.lancamento_partida
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
lancamento_id       BIGINT NOT NULL REFERENCES lancamento
conta_id            BIGINT NOT NULL REFERENCES conta   -- deve ter aceita_lancamento = TRUE
tipo                VARCHAR(1) NOT NULL         -- 'D' | 'C'
valor               NUMERIC(15,2) NOT NULL CHECK (valor > 0)

-- Dimensões analíticas
centro_custo_id     BIGINT                     -- financeiro.centro_custo (FK lógica)
estabelecimento_id  UUID                       -- cadastros.estabelecimento (§36.7)
pessoa_id           BIGINT                     -- contrapartida (cliente/fornecedor)

historico           VARCHAR(200)
INDEX idx_partida_conta (tenant_id, conta_id)
INDEX idx_partida_cc (tenant_id, centro_custo_id)
```

> **Partidas dobradas:** por `lancamento_id`, `SUM(valor WHERE tipo='D') = SUM(valor WHERE tipo='C')`.
> Validado no `LancamentoService` (não no banco) — lançamento desbalanceado é rejeitado (RN-CONT-01).

### 36.6 `mapeamento` — De-para financeiro → contábil (§12.8 / migration 006)

Regras que traduzem uma entidade financeira nas contas de débito/crédito, e a linha da DRE.

```sql
contabil.mapeamento
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
tipo_origem         VARCHAR(30) NOT NULL        -- CONTA_CORRENTE|TIPO_BAIXA|TIPO_AJUSTE|
                                                --  CLASSIFICACAO_FINANCEIRA|TRIBUTO|LINHA_DRE
origem_id           BIGINT NOT NULL            -- id da entidade financeira mapeada
conta_debito_id     BIGINT REFERENCES conta
conta_credito_id    BIGINT REFERENCES conta
linha_dre           VARCHAR(50)                -- linha da DRE (configurável — muda com a reforma)
ativo               BOOLEAN DEFAULT TRUE
UNIQUE (tenant_id, tipo_origem, origem_id)
```

### 36.7 Dimensão Filial = `cadastros.estabelecimento` (não criar `contabil.filial`)

> **Decisão:** a dimensão matriz/filial usa o **relationship model** do
> `spec/estabelecimentos-filiais.md` (party + estabelecimento, estilo TCA) — o
> `estabelecimento` vive no `cadastro-service`. **Não existe** tabela `contabil.filial`;
> seria um segundo cadastro do mesmo conceito.

- O plano de contas é **compartilhado** — o estabelecimento é dimensão do lançamento, não do plano.
- `estabelecimento.id` é **UUID** — todas as colunas de dimensão são `estabelecimento_id UUID`
  (FK lógica, sem FK cross-schema, padrão do projeto).
- **Seed da matriz:** criado pelo onboarding do tenant (Fase 4 do spec de filiais — `pessoa`
  própria + estabelecimento matriz `proprio=true` no cadastro-service). O contábil só consome;
  o `TenantAtivacaoListener` contábil **não** cria filial.
- IE/UF/município do emitente vêm do estabelecimento — `fiscal.config_empresa` fica reduzida a
  regime tributário/CRT/opção Simples (dados que não são por estabelecimento).

`contabil.periodo` e `contabil.lancamento_partida` carregam `estabelecimento_id UUID`
(null = consolidado). Financeiro (`titulo`, `conta_movimentacao`) também — bill-to/pay-from por
filial (§2.8). Apuração: IBS/CBS consolida por raiz de CNPJ (`estabelecimento_id = NULL`);
ICMS/ISS (até 2033) apura **por estabelecimento**.

---

## §37 — Geração automática de lançamentos

O GL consome eventos do financeiro. **Eventos internos ao serviço** (se contábil e financeiro
forem o mesmo processo) via `ApplicationEventPublisher`; **eventos que cruzam serviços** via Kafka
(§13.4). Idempotência garantida pela unique parcial `(tenant_id, origem, origem_id)` em `lancamento`.

**Eventos que geram lançamento:**

| Evento financeiro | Débito | Crédito | Observação |
|---|---|---|---|
| Emissão título a receber | Clientes (1.1.2) | Receita (3.x) / IBS-CBS a recolher | competência da emissão |
| Baixa a receber (REAL) | Caixa/Banco (1.1.1) | Clientes (1.1.2) | por `titulo_baixa` |
| Emissão título a pagar | Despesa/Estoque | Fornecedores (2.1.1) | crédito IBS/CBS quando houver |
| Baixa a pagar (REAL) | Fornecedores (2.1.1) | Caixa/Banco (1.1.1) | |
| **Retenção** (§4.9) | Fornecedores | Impostos a recolher (2.1.3) | baixa `meio='RETENCAO'` fecha o título |
| **Desconto de título** (§5.4) | Banco (líquido) + Despesa financeira (taxa) | Títulos Descontados (2.x, obrigação c/ banco) | título mantém saldo integral; não há baixa por desconto. Na liquidação pelo sacado, baixa o recebível e zera "Títulos Descontados" |
| Juros/multa recebidos | Caixa/Banco | Receita financeira | do retorno CNAB (`valor_acrescimos`) |
| Tarifa bancária | Despesa bancária | Caixa/Banco | `conta_movimentacao` DÉBITO |
| Rendimento de aplicação | Caixa/Banco | Receita financeira (líq. IR) | §17.9 |
| **Provisão PDD** (§24) | Despesa com PCLD | PCLD (retificadora do ativo, `retificadora=TRUE`) | só quando tenant contabiliza PDD; `PddCalculoJob` mensal; estimativa, não baixa título |
| **Baixa com split payment** (2027+) | Caixa/Banco (valor **líquido**) **+** IBS/CBS Recolhido na Liquidação (conta transitória, pelo **retido**) | Clientes/Fornecedores (valor **bruto**) | resolve a divergência de centavos da conciliação — ver nota abaixo |

> **Split payment — baixa pelo bruto, caixa pelo líquido (Q-conciliação).** Quando o adquirente (cartão)
> ou o BC (PIX) retém IBS/CBS na liquidação, o título é **baixado pelo valor bruto** (a obrigação do
> cliente era o bruto), mas a Tesouraria só recebe o **líquido**. A partida de caixa registra o líquido e
> a diferença retida vai para a **conta transitória `IBS/CBS Recolhido na Liquidação`** (ativo — crédito a
> compensar na apuração). Assim `titulo_baixa.valor = bruto`, `conta_movimentacao.valor = líquido`,
> `valor_retido_governo = diferença`, e a conciliação bancária (§III 4.3) casa pelo líquido — sem quebra de
> centavos. O acerto da transitória contra o IBS/CBS a recolher é feito na apuração (módulo fiscal).

**Resolução das contas:** o `GeracaoLancamentoService` consulta `mapeamento` por
`(tipo_origem, origem_id)` — ex.: `TIPO_BAIXA` → contas de D/C daquela baixa, `CONTA_CORRENTE` →
conta de caixa/banco, `TRIBUTO` → conta de imposto a recolher. Se não houver mapeamento
específico, cai no de-para por `CLASSIFICACAO_FINANCEIRA`.

**Explosão de rateio (centro de custo):** quando o título tem `rateio_id` (§F3), a partida da
conta de resultado é **explodida em N partidas**, uma por item do rateio, cada uma com seu
`centro_custo_id` e `valor = base × percentual/100`. A soma das partidas explodidas = valor da
conta de resultado (fecha as partidas dobradas). **Arredondamento:** cada partida arredonda a
2 casas e a **diferença residual de centavos vai na partida de maior percentual** (ex.: base
R$ 100,00 com 33,33/33,33/33,34 → 33,33 + 33,33 + 33,34), garantindo ΣD=ΣC exato. Título com `centro_custo_id` direto → 1 partida
com aquele centro. Título sem centro nem rateio → partida sem dimensão de centro de custo.

**Fluxo (`GeracaoLancamentoService`):**
1. Recebe o fato financeiro (evento) com `origem`/`origem_id`.
2. Resolve `periodo` pela competência; **rejeita** se o período estiver `FECHADO`/`BLOQUEADO`
   (RN-CONT-03). O destino do fato é dado por `parametros.gl_fato_periodo_fechado`:
   `'LANCAR_COMPETENCIA_ABERTA'` (**default**) lança na competência aberta atual, com histórico
   referenciando a competência original; `'AGUARDAR_REABERTURA'` retém o fato numa fila de
   pendências até o período reabrir (fila visível na tela de Períodos — fatos pendentes
   bloqueiam novo fechamento até serem tratados, §42 passo 1).
3. Aloca `numero` sequencial do período sob lock (gapless).
4. Seleciona contas via `mapeamento`; monta partidas D/C; aplica explosão de rateio.
5. Valida partidas dobradas; persiste `lancamento` + `lancamento_partida` atômico.
6. Idempotente: se já existe lançamento para `(origem, origem_id)`, ignora (log).

### §37.1 — Lançamento de reversão do estorno de baixa

O estorno de uma baixa REAL (§4.6.1) gera automaticamente um **lançamento de reversão** no GL:

- **Gatilho:** o evento da baixa de estorno (`titulo_baixa` com `origem = 'ESTORNO'`) — mesmo
  canal dos demais fatos financeiros desta seção.
- **Partidas:** **copiadas do lançamento original com D/C invertidos** — não são re-derivadas
  do `mapeamento` (que pode ter mudado desde a baixa original); copiar garante simetria exata,
  inclusive o rateio de centro de custo já explodido. Valores das partidas sempre positivos
  (o sinal negativo existe só em `titulo_baixa` — a reversão é de D/C, não de sinal).
- **Vínculo e idempotência:** novo `lancamento` com `origem = 'TITULO_BAIXA'` e `origem_id` =
  id da **baixa de estorno** (a unique parcial `(origem, origem_id)` continua valendo — é outro
  fato); `estorno_de_id` aponta o lançamento original, que passa a `status = 'ESTORNADO'` —
  nunca é apagado.
- **Período fechado:** se o lançamento original pertence a período `FECHADO`, a reversão entra
  na **competência aberta atual** (coerente com RN-CONT-10), com histórico
  "Estorno do lançamento nº {numero} (competência {YYYY-MM})" — o período fechado permanece intacto.
- **Numeração:** sequencial normal do período em que a reversão é lançada (sem lacunas — RN-CONT-08).

---

## §38 — Razão Contábil e Balancete

**Razão** — `GET /api/contabil/razao?conta_id=&de=&ate=&estabelecimento_id=&centro_custo_id=`

Movimento analítico de uma conta no período, com saldo acumulado. Filtro por estabelecimento
(individual ou consolidado quando `NULL`) e por centro de custo.

```json
{
  "conta": { "codigo": "1.1.1.02", "descricao": "Banco Conta Movimento" },
  "saldo_anterior": 12000.00,
  "partidas": [
    { "data": "2025-06-05", "historico": "Baixa NF 001", "debito": 3000.00, "credito": 0, "saldo": 15000.00 }
  ],
  "total_debito": 8000.00, "total_credito": 3200.00, "saldo_final": 16800.00
}
```

**Balancete de verificação** — `GET /api/contabil/balancete?competencia=&estabelecimento_id=`

Lista de todas as contas com `saldo_anterior`, `débitos`, `créditos` e `saldo_final` no período —
a ponte entre o Razão e as demonstrações. Valida a partida dobrada agregada: `Σ débitos = Σ créditos`.

---

## §39 — Balanço Patrimonial

**Endpoint:** `GET /api/contabil/balanco?competencia=&estabelecimento_id=`

Ativo / Passivo / PL a partir dos saldos das contas sintéticas (consolidação da árvore). Contas
**retificadoras** (`retificadora = TRUE`, ex.: depreciação acumulada, PCLD) **subtraem** do grupo.
`estabelecimento_id = NULL` → consolidado do grupo; informado → balanço individual da filial.
Só considera lançamentos de períodos até a competência pedida. Valida `Ativo = Passivo + PL`.

---

## §40 — DRE por Competência

**Endpoint:** `GET /api/contabil/dre?de=&ate=&estabelecimento_id=`

Receitas − Despesas por competência (contas de resultado), estruturado pelas linhas configuradas em
`mapeamento.linha_dre` (receita bruta, deduções, CMV, despesas operacionais, resultado financeiro,
resultado antes/depois de tributos). As linhas são **configuráveis** — nunca hardcode, pois a
estrutura muda com a reforma tributária. Não confundir com a **DRE gerencial** (§25) — esta é a
societária/contábil.

---

## §41 — Livro Diário

**Endpoint:** `GET /api/contabil/livro-diario?de=&ate=`

Lançamentos em ordem cronológica pelo `numero` sequencial **sem lacunas**, cada um com suas
partidas D/C balanceadas — formato para impressão/ECD. Termo de abertura/encerramento por exercício.

---

## §42 — Fechamento de período e apuração de resultado

**Endpoint:** `POST /api/contabil/periodo/{id}/fechar`

**Fluxo:**
1. Validar que não há fato financeiro pendente de lançar na competência.
2. Validar partidas dobradas de todos os lançamentos do período (balancete fecha).
3. Marcar `periodo.status = 'FECHADO'` — a partir daí `GeracaoLancamentoService` rejeita a competência.
4. **Fechamento anual:** apurar resultado — transferir saldos de contas de RECEITA/CUSTO/DESPESA para
   conta de resultado do exercício (lançamento `ENCERRAMENTO`), zerando as contas de resultado;
   o resultado é transferido ao PL (Lucros/Prejuízos Acumulados).
5. Abertura do exercício seguinte: lançamento `ABERTURA` com os saldos patrimoniais.

- **Reabertura** (`POST .../reabrir`, com alçada): `FECHADO` → `ABERTO`, com auditoria.
- **Bloqueio/desbloqueio** (`POST .../bloquear`): `ABERTO` → `BLOQUEADO` (trava temporária que
  impede lançamento sem fechar de vez); reversível para `ABERTO`.

---

## §43 — Conciliação GL × sub-ledgers

Job/relatório que confere se os saldos das contas de controle batem com os sub-ledgers do financeiro:

| Conta de controle (GL) | Sub-ledger (financeiro) | Deve igualar |
|---|---|---|
| Clientes (1.1.2) | `SUM(titulo.saldo)` a receber em aberto | saldo contábil = saldo dos títulos |
| Fornecedores (2.1.1) | `SUM(titulo.saldo)` a pagar em aberto | idem |
| Banco/Caixa (1.1.1) | `v_saldo_conta_corrente` | saldo GL = saldo das contas correntes |
| Impostos a recolher (2.1.3) | retenções não recolhidas | |

Divergência → relatório de itens que não amarram (lançamento sem fato, fato sem lançamento).
Roda como `ConciliacaoGlJob` (mensal, pós-fechamento).

---

## §44 — Regras de negócio

- **RN-CONT-01** Todo lançamento respeita partidas dobradas (ΣD = ΣC); desbalanceado é rejeitado.
- **RN-CONT-02** Partida só em conta analítica (`aceita_lancamento = TRUE`).
- **RN-CONT-03** Período `FECHADO`/`BLOQUEADO` não aceita novo lançamento na competência.
- **RN-CONT-04** 1 fato financeiro = 1 lançamento (idempotência por `(origem, origem_id)`);
  correção só por **estorno** (partidas invertidas, original → `ESTORNADO`), nunca delete.
- **RN-CONT-05** Explosão de rateio preserva o total da conta de resultado (§37/§F3).
- **RN-CONT-06** Plano de contas é editável (§F6) — sem elenco travado.
- **RN-CONT-07** Dimensão filial é `estabelecimento_id UUID`; `estabelecimento_id = NULL` = consolidado.
- **RN-CONT-08** `numero` do lançamento é sequencial por período **sem lacunas** (exigência do Livro Diário).
- **RN-CONT-09** Contas `retificadora = TRUE` subtraem do grupo no Balanço (depreciação/PCLD).
- **RN-CONT-10** A trava de RN-CONT-03 vale também para o **financeiro**: alterar
  `data_competencia`/`data_emissao` de um título (ou emitir/baixar retroativo) que caia num período
  contábil `FECHADO`/`BLOQUEADO` é **bloqueado**, mesmo para perfil administrativo. A correção
  econômica é lançada na **competência aberta atual** via lançamento de ajuste (com
  `origem`/`origem_id` apontando o título), nunca reescrevendo o mês fechado — o Livro Diário é
  imutável após fechamento (§41/§42). ΣD=ΣC do período fechado permanece intacto.

---

## MÓDULO VI — ANÁLISES GERENCIAIS E RELATÓRIOS

> Reconstruído nesta versão (v12.1). **DDL de `pdd_config` alinhado ao §12.10** (migration 030).
> Cobre relatórios operacionais (aging §21), inadimplência (§22), KPIs (§23), PDD (§24), DRE
> gerencial (§25), dashboard executivo (§26) e materialização/cron (§27). Consome os dados dos
> Módulos I–V; **não** escreve fatos — é camada de leitura/analítica.

**Natureza:** relatórios são **consultas** (views/materialized views + endpoints de leitura).
Nada aqui altera título, baixa ou lançamento. Filtros comuns: período, `estabelecimento_id`,
`centro_custo_id`, `classificacao_financeira`, pessoa.

---

## §21 — Aging (posição por faixa de vencimento)

**Endpoint:** `GET /api/financeiro/relatorios/aging?natureza=RECEBER|PAGAR&data_base=&estabelecimento_id=`

Distribui os saldos em aberto por faixa de dias vencidos, a partir da `data_base`. As faixas
espelham as do `pdd_config` (§24):

| Faixa (`pdd_config.faixa`) | Critério |
|---|---|
| NAO_VENCIDO | `vencimento ≥ data_base` |
| ATE_30 | vencido 1 a 30 dias |
| DE_31_60 | vencido 31 a 60 dias |
| DE_61_90 | vencido 61 a 90 dias |
| ACIMA_90 | vencido mais de 90 dias |

Agrupável por pessoa (cliente/fornecedor), classificação e centro de custo. Base do PDD (§24) e
da régua de cobrança (dunning, Módulo II).

---

## §22 — Inadimplência e posição de títulos

- **Inadimplência** (`GET .../relatorios/inadimplencia`): títulos a receber vencidos e não pagos,
  com dias de atraso, valor original + juros/multa projetados, situação na régua de cobrança.
- **Posição de títulos** (`GET .../relatorios/posicao-titulos`): visão consolidada de saldos por
  status (`PREVISTO/EM_ABERTO/EMITIDO/DESCONTADO/BAIXADO`) — a receber e a pagar, com totalizadores.

---

## §23 — KPIs financeiros

**Endpoint:** `GET /api/financeiro/relatorios/kpis?de=&ate=&estabelecimento_id=`

| KPI | Fórmula (resumo) |
|---|---|
| **PMR** (prazo médio de recebimento) | Σ(saldo receber × dias) / Σ recebimentos no período |
| **PMP** (prazo médio de pagamento) | Σ(saldo pagar × dias) / Σ pagamentos no período |
| **Ciclo financeiro** | PMR − PMP (+ PME de estoque, quando houver módulo de estoque) |
| **Giro de recebíveis** | Recebimentos no período / saldo médio a receber |
| **Taxa de inadimplência** | Vencido não pago / total a receber no período |

Retorna valor atual + série histórica mensal para gráfico.

---

## §24 — PDD (Provisão para Devedores Duvidosos) (§12.10 / migration 030)

**Configuração:** percentual de provisão (PCLD) por faixa de aging (§21), parametrizável pelo tenant.
Seed default na ativação: 0,5% / 3% / 8% / 20% / 50%.

```sql
financeiro.pdd_config
─────────────────────────────────────────────
id                  BIGSERIAL PK
tenant_id           BIGINT NOT NULL
faixa               VARCHAR(15) NOT NULL       -- NAO_VENCIDO|ATE_30|DE_31_60|DE_61_90|ACIMA_90
percentual          NUMERIC(5,2) NOT NULL      -- % provisionado da faixa
ativo               BOOLEAN DEFAULT TRUE
UNIQUE (tenant_id, faixa)
```

**Cálculo** (`GET .../relatorios/pdd`): para cada faixa, `provisão = saldo_faixa × percentual`.
O total da PDD alimenta o lançamento contábil de provisão (despesa × conta retificadora do ativo)
via §37 quando o tenant contabiliza PDD. **É estimativa gerencial** — não baixa título.

---

## §25 — DRE gerencial

**Endpoint:** `GET /api/financeiro/relatorios/dre-gerencial?de=&ate=&por=CLASSIFICACAO|CENTRO_CUSTO`

Diferente da DRE contábil (§40): estrutura por **`classificacao_financeira`** (plano gerencial de
receitas/despesas) e/ou por **centro de custo**, em regime de caixa **ou** competência (parâmetro).
Comparativo orçado × realizado usando `orcamento_fluxo` (§2.8 do Módulo III). Permite drill-down
até o título/movimentação de origem.

---

## §26 — Dashboard executivo

**Endpoint:** `GET /api/financeiro/relatorios/dashboard?estabelecimento_id=`

Painel consolidado (uma chamada, várias métricas):
- **Posição de caixa:** saldo atual de todas as contas (`v_saldo_conta_corrente`) + projeção de
  fluxo (entradas previstas − saídas previstas) para 7/30/90 dias.
- **Recebíveis:** total a receber, vencido, aging resumido, top 5 clientes.
- **Pagáveis:** total a pagar, vencido, compromissos dos próximos 30 dias, alçadas pendentes.
- **Indicadores:** PMR, PMP, ciclo, inadimplência (§23).
- **Alertas:** boletos vencidos (§20), cheques a compensar, aplicações vencidas, títulos em atraso
  na régua de cobrança, divergências de conciliação (§43).

---

## §27 — Materialização e cron

- Relatórios pesados (aging, KPIs, dashboard) usam **materialized views** por tenant, atualizadas
  por `RelatoriosRefreshJob` (diário, madrugada) + refresh sob demanda ao abrir o painel.
- Consultas ad-hoc por período curto rodam direto nas tabelas (índices de `vencimento`/`status`).
- Todos os endpoints deste módulo são **read-only** e respeitam o `TenantContext` (filtro por tenant)
  e as permissões RBAC (visão gerencial exige permissão específica).

**Cron jobs do módulo:**

| Job | Frequência | Ação |
|---|---|---|
| `RelatoriosRefreshJob` | diária (madrugada) | `REFRESH MATERIALIZED VIEW` de aging/KPIs/dashboard por tenant |
| `PddCalculoJob` | mensal | Recalcula PDD por faixa e (se configurado) dispara lançamento de provisão (§37) |

---

## 11. Mapeamento dos Diagramas de Arquitetura

> ⚠️ **Seção histórica (pré-v12)** — mantida apenas como rastreabilidade do planejamento original.
> Dois pontos estão defasados em relação ao resto do documento:
> 1. **Numeração dos módulos deslocada**: onde esta seção diz "Módulo I (§3–§8)" para AP/AR,
>    leia **Módulo II**; fluxo de caixa/conciliação = **Módulo III**; tesouraria (§17) =
>    **Módulo IV**; análises = **Módulo VI**.
> 2. **Status desatualizados**: o motor fiscal (Módulo I, §1.4–§1.9) e a contabilidade/GL
>    (Módulo V, §36–§44) hoje estão **especificados neste próprio documento** — os marcadores
>    "spec separado" / "⏳ reservado" abaixo refletem o estado da época. As obrigações
>    acessórias têm decisão de escopo em §14.1 (MVP Simples Nacional).

Esta seção conecta cada caixa dos dois diagramas compartilhados durante o planejamento às seções deste documento. Serve como rastreabilidade entre o diagrama de alto nível e o spec técnico.

---

### 11.1 Diagrama 1 — Visão Fiscal do ERP

> Diagrama com motor fiscal no topo, dois tracks (entrada/saída) convergindo para módulo fiscal, contabilidade e obrigações acessórias.

| Caixa do diagrama | Coberta neste spec? | Onde | Observação |
|---|---|---|---|
| **motor fiscal** — alíquotas · CST IBS/CBS/IS · NCM · regras por regime · tributação no destino | ⏳ Parcial | §8 Roadmap fase 3 · campo `impostos JSONB` em `titulo` | Motor fiscal é spec separado. O campo `impostos JSONB` no título garante que os valores calculados pelo motor sejam armazenados sem migration destrutiva |
| **importação de documentos** — NF-e · CT-e · NFS-e (entrada) | ⏳ Parcial | §5.1 (`origem = 'NF_ENTRADA'`) · §9.1 Integração NF Entrada | O título a pagar é criado com `origem = 'NF_ENTRADA'` quando o módulo de documentos aprovar a NF. O spec do módulo de importação fiscal é separado |
| **emissão de documentos** — NF-e · NFC-e · NFS-e (saída) | ⏳ Parcial | §6.1 Emitir Título · §8 Roadmap fase 2 | A emissão de boleto (§17.1) cobre a parte financeira. A emissão da NF-e com campos IBS/CBS é spec separado, previsto para antes de agosto/2026 |
| **AP — contas a pagar** — créditos IBS/CBS · split payment | ✅ AP coberto · ⏳ fiscal parcial | Módulo I inteiro (§3 a §8) · §8 Roadmap fase 4 | Toda a operação de AP está especificada. Créditos IBS/CBS serão armazenados em `titulo.impostos JSONB`. Split payment entra como `tipo_baixa.meio = 'SPLIT_PAYMENT'` em 2027 |
| **AR — contas a receber** — débitos IBS/CBS · split payment | ✅ AR coberto · ⏳ fiscal parcial | Módulo I inteiro (§3 a §8) · §8 Roadmap fase 4 | Mesma lógica do AP. O campo `valor_split_payment` em `titulo_baixa` já está mapeado como migration futura não destrutiva |
| **módulo fiscal** — IBS/CBS/IS · ICMS/ISS/PIS/Cofins · livros fiscais | ❌ Fora do escopo | §8 Roadmap fases 3–6 | Spec separado. Durante a transição (2026–2033) os dois regimes coexistem. A apuração consome dados de `titulo.impostos` |
| **contabilidade / GL** — lançamentos automáticos | ⏳ Reservado | `conta_corrente.conta_contabil` (campo reservado) · `conta_movimentacao` como origem | O campo `conta_contabil` foi incluído como placeholder. O GL consome eventos publicados pelo financeiro (`titulo.baixado`, `conta_movimentacao` CONFIRMADA) |
| **SPED / EFD** — EFD-Contrib → CBS | ❌ Fora do escopo | §8 Roadmap fase 5 | Depende do módulo fiscal estar concluído |
| **DCTFWeb / DARF** — guias de pagamento | ❌ Fora do escopo | §8 Roadmap fase 5 | Guias geradas a partir da apuração do módulo fiscal |
| **decl. IBS / CBS** — nova declaração | ❌ Fora do escopo | §8 Roadmap fase 5 | Novo obrigação acessória — CGIBS ainda publicando regulamentação |

---

### 11.2 Diagrama 2 — Procure-to-Pay (P2P) e Order-to-Cash (O2C)

> Diagrama com dois tracks paralelos convergindo para módulo fiscal e contabilidade.

#### Track P2P — Procure-to-Pay (esquerda)

| Caixa do diagrama | Coberta neste spec? | Onde | Observação |
|---|---|---|---|
| **motor fiscal** | ⏳ Parcial | §8 Roadmap fase 3 · `titulo.impostos JSONB` | Idem Diagrama 1 |
| **compras / PO** — requisição de compra | ❌ Fora do escopo | — | Módulo de Compras — spec independente. Ao aprovar uma PO/NF de entrada, dispara `POST /api/financeiro/titulos/pagar` com `origem = 'NF_ENTRADA'` |
| **recebimento / estoque** — conferência de mercadoria | ❌ Fora do escopo | — | Módulo de Estoque. O recebimento físico não afeta o financeiro diretamente — só a aprovação da NF fiscal gera o título a pagar |
| **importação NF-e / CT-e** — créditos IBS/CBS | ⏳ Parcial | §5.1 `origem = 'NF_ENTRADA'` · `titulo.impostos JSONB` | A criação do título a pagar está coberta. Os créditos IBS/CBS serão calculados pelo motor fiscal e armazenados em `impostos JSONB` |
| **AP — contas a pagar** — créditos IBS/CBS · split payment | ✅ Coberto | Módulo I (§3–§8) completo | Toda operação de AP está especificada, incluindo tipos de baixa configuráveis para split payment |
| **pagamento** — split payment 2027 | ⏳ Mapeado | §8 Roadmap fase 4 · `conta_movimentacao.valor_retido_governo` | Campo `valor_retido_governo` mapeado como migration futura. `tipo_baixa.meio = 'SPLIT_PAYMENT'` já é suportado pela entidade configurável |

#### Track O2C — Order-to-Cash (direita)

| Caixa do diagrama | Coberta neste spec? | Onde | Observação |
|---|---|---|---|
| **vendas / pedido** — cotação e confirmação | ❌ Fora do escopo | — | Módulo de Vendas. Ao confirmar pedido/NF de saída, dispara `POST /api/financeiro/titulos/receber` com `origem = 'NF_SAIDA'` |
| **separação / expedição** — picking · baixa estoque | ❌ Fora do escopo | — | Módulo de Estoque/Logística. Não afeta o financeiro diretamente |
| **emissão NF-e / NFC-e** — SEFAZ · IBS/CBS | ⏳ Parcial | §6.1 Emitir Título · §8 Roadmap fase 2 | A emissão do título a receber está coberta. A emissão da NF-e com campos IBS/CBS é spec separado. O vínculo é: NF-e aprovada → cria `titulo receber` com `origem = 'NF_SAIDA'` |
| **AR — contas a receber** — débitos IBS/CBS | ✅ Coberto | Módulo I (§3–§8) completo | Toda operação de AR está especificada. Débitos IBS/CBS armazenados em `titulo.impostos JSONB` |
| **cobrança / recebimento** — split payment 2027 | ✅ Coberto (cobrança) · ⏳ Mapeado (split) | Módulo III §17 completo · §8 Roadmap fase 4 | Boleto, CNAB, DDA, carta de cobrança — tudo especificado. Split payment mapeado como migration futura em `titulo_baixa.valor_split_payment` |

#### Convergência (base dos dois diagramas)

| Caixa do diagrama | Coberta neste spec? | Onde | Observação |
|---|---|---|---|
| **módulo fiscal** — apuração IBS/CBS/IS · crédito AP × débito AR | ❌ Fora do escopo | §8 Roadmap fases 3–6 | O cruzamento de créditos (AP) com débitos (AR) para apuração do IBS/CBS líquido a recolher é responsabilidade do módulo fiscal separado. Os dados estão em `titulo.impostos JSONB` prontos para consumo |
| **contabilidade / GL** — lançamentos automáticos | ⏳ Reservado | `conta_corrente.conta_contabil` · eventos `titulo.baixado` e `conta_movimentacao CONFIRMADA` | O GL consome os eventos publicados por este módulo. Os campos de conta contábil já estão reservados nas entidades relevantes |

---

### 11.3 Resumo Visual — O que este spec cobre no contexto dos diagramas

```
DIAGRAMA 2 — P2P / O2C
─────────────────────────────────────────────────────────────────

  [compras/PO]          [motor fiscal]          [vendas/pedido]
  ❌ fora escopo       ⏳ spec separado         ❌ fora escopo
       ↓                  consulta ↕                  ↓
  [recebimento/         ┌──────────┐            [separação/
    estoque]            │          │              expedição]
  ❌ fora escopo        │          │            ❌ fora escopo
       ↓                └──────────┘                  ↓
  [importação NF-e]                          [emissão NF-e/NFC-e]
  ⏳ spec fiscal                             ⏳ spec fiscal
       ↓                                           ↓
┌─────────────────────────────────────────────────────────────┐
│  AP — CONTAS A PAGAR        AR — CONTAS A RECEBER           │  ← MÓDULO I
│  ✅ spec completo           ✅ spec completo                │
│                                                             │
│  FLUXO DE CAIXA · CONCILIAÇÃO BANCÁRIA                     │  ← MÓDULO II
│  ✅ spec completo                                           │
│                                                             │
│  TESOURARIA · BOLETOS · CNAB · DDA · CHEQUES               │  ← MÓDULO III
│  ✅ spec completo                                           │
│                                                             │
│  ANÁLISES GERENCIAIS · KPIs · DRE · PDD · DASHBOARD        │  ← MÓDULO IV
│  ✅ spec completo                                           │
└─────────────────────────────────────────────────────────────┘
       ↓                                           ↓
  [pagamento]                               [cobrança/recebimento]
  split payment ⏳                          split payment ⏳
  roadmap fase 4                            roadmap fase 4
       ↓                                           ↓
  ┌──────────────────────────────────────────────────────┐
  │  MÓDULO FISCAL — apuração IBS/CBS/IS                 │  ← spec separado
  │  🔶 início especificado neste documento                   │
  └──────────────────────────────────────────────────────┘
                         ↓
  ┌──────────────────────────────────────────────────────┐
  │  CONTABILIDADE / GL — lançamentos automáticos        │  ← spec separado
  │  🔶 início especificado neste documento                     │
  └──────────────────────────────────────────────────────┘
                         ↓
         SPED/EFD · DCTFWeb/DARF · Decl. IBS/CBS
         ❌ fora do escopo · roadmap fases 5–6
```

**Legenda:**
- ✅ Especificado neste documento
- ⏳ Parcialmente coberto ou mapeado como campo reservado / roadmap
- ❌ Fora do escopo — spec separado necessário

---



---


---
---



---

## 12. Plano de Implementação Completo

> Esta seção é a fonte de verdade para implementação. Cruza o que já existe no banco, o que a conversa de reforma tributária definiu (Liquibase v2 — schema `fiscal`) e o que este spec adicionou. Tudo em ordem de execução.

**⚠️ Decisão registrada:** a conversa de reforma tributária usou schema `tax`. Este spec usa `fiscal`. Na implementação, use `fiscal` em tudo — os arquivos YAML devem criar `fiscal.*`, não `tax.*`. As tabelas `fiscal.operacao_fiscal` e `fiscal.config_empresa` viram `fiscal.operacao_fiscal` e `fiscal.config_empresa` respectivamente.

---

### 12.1 O Que Já Existe — Não Criar, Não Alterar

#### Schema principal (sem prefixo)

| Tabela | Campos relevantes para o financeiro | Observação |
|---|---|---|
| `tenant` | `cnpj`, `ie`, `ibge_codigo`, `uf`, `email`, `status` | `ibge_codigo` já está no formato IBGE correto — FK lógica para `aliq_ibs_municipio` |
| `user_account` | `id UUID`, `tenant_id`, `email`, `active` | `id` é UUID — audit_log deve usar `user_id UUID`, não BIGINT |
| `pessoa` | `documento` (sem máscara; CNPJ pode ser alfanumérico — NT 2026.004), `tipo` (PF/PJ) | `tipo` distingue B2C de B2B para IBS/CBS |
| `endereco` | `uf`, `ibge_codigo` | Já cobre destino IBS sem campo novo |
| `produto` | `origem` (fiscal), `preco` | Base para IS e crédito IBS/CBS |
| `produto_fornecedor` | `preco_custo` | Custo de entrada para crédito |
| `produto_estoque_config` | existe | Precisa de addColumn |
| `cliente` | via `pessoa` | Tem documento e endereço |
| `fornecedor` | via `pessoa` | Tem documento |
| `transportadora` | via `pessoa` | CT-e gera crédito IBS/CBS |
| `condicao_pagamento` | `forma_pagamento` nas parcelas | Base para split payment |
| `condicao_pagamento_parcela` | `forma_pagamento` | PIX/cartão = split, boleto/cheque = sem split |

#### Schema `billing` — intocável

| Tabela | Status |
|---|---|
| `billing.partner` | ✅ Não mexer |
| `billing.partner_referral` | ✅ Não mexer |
| `billing.subscription` | ✅ Não mexer |
| `billing.commission` | ✅ Não mexer |
| `billing.trial_engagement` | ✅ Não mexer |
| `billing.webhook_log` | ✅ Não mexer |

---

### 12.2 Estrutura de Pastas dos Changelogs

```
db/
├── db.changelog-master.yaml          ← inclui os 3 masters abaixo
├── changelog/
│   ├── financeiro/
│   │   ├── db.changelog-financeiro.yaml   ← master do módulo financeiro
│   │   └── v1/
│   │       └── [migrations aqui]
│   ├── fiscal/
│   │   ├── db.changelog-fiscal.yaml       ← master do módulo fiscal
│   │   └── v1/
│   │       └── [migrations aqui]
│   └── contabil/
│       ├── db.changelog-contabil.yaml     ← master do módulo contábil
│       └── v1/
│           └── [migrations aqui]
```

**Convenção de id dos changesets:** `{autor}-{schema}-v1.{numero}-{descricao}`
Exemplo: `vitor-financeiro-v1.001-feriado-bancario`

---

### 12.3 Sprint 1 — Fundação e Motor Fiscal Base

> Sem bloqueantes. Execute tudo aqui antes de qualquer outro sprint.

#### Bloco 1A — Infraestrutura transversal (schema `financeiro`)

| Arquivo | Operação | Descrição |
|---|---|---|
| `financeiro/v1/001-feriado-bancario.yaml` | `createTable` | Tabela `financeiro.feriado_bancario` com colunas: `id`, `data DATE`, `descricao`, `tipo` (NACIONAL/ESTADUAL/MUNICIPAL), `uf` nullable, `ibge_municipio` nullable. Unique em `(data, tipo, uf, ibge_municipio)`. Seed inline com os 12 feriados nacionais fixos (Confraternização, Tiradentes, Trabalho, Independência, N.Sra.Aparecida, Finados, Proclamação, Natal). Feriados móveis (Carnaval, Corpus Christi, Sexta Santa) calculados em código, não no seed. |
| `financeiro/v1/002-audit-log.yaml` | `createTable` | Tabela `financeiro.audit_log` com colunas: `id`, `tenant_id`, `tabela varchar(50)`, `registro_id BIGINT`, `operacao varchar(10)` (INSERT/UPDATE/DELETE), `campos_antes JSONB`, `campos_depois JSONB`, `actor_user_id UUID` (UUID — não BIGINT, pois `user_account.id` é UUID; nome `actor_user_id` segue o padrão da audit_log principal — ver §F2), `user_nome varchar(100)`, `ip_origem varchar(45)`, `created_at TIMESTAMPTZ`. Índices em `(tenant_id, tabela, registro_id)`, `(tenant_id, actor_user_id)` e `(tenant_id, created_at)`. Sem FK — referência a `user_account` garantida pela aplicação. |
| `financeiro/v1/003-centro-custo.yaml` | `createTable` | Tabelas `financeiro.centro_custo` (hierárquica com `centro_pai_id` self-reference, `aceita_lancamento boolean`, `ativo boolean`) e `financeiro.centro_custo_rateio` + `financeiro.centro_custo_rateio_item` (com `tenant_id`; percentual por CC, soma deve ser 100%; unique `(rateio_id, centro_custo_id)`). Índice em `(tenant_id, codigo)` unique. |
| `financeiro/v1/004-addcol-centro-custo-referencias.yaml` | `addColumn` | Adiciona `centro_custo_id BIGINT nullable` em: `financeiro.titulo`, `financeiro.titulo_baixa`, `financeiro.conta_movimentacao`, `contabil.lancamento_partida`. Atenção: `titulo` e `titulo_baixa` ainda não existem neste ponto — esta migration deve vir **depois** das migrations que criam essas tabelas. Mover para após financeiro/v1/006. |

> ⚠️ **Ajuste de ordem:** `004-addcol-centro-custo-referencias.yaml` deve ser renumerado para `financeiro/v1/010-addcol-centro-custo-referencias.yaml` para vir após a criação de `titulo` e `titulo_baixa`.

#### Bloco 1B — Colunas novas em tabelas existentes (schema principal)

| Arquivo | Operação | Descrição |
|---|---|---|
| `fiscal/v1/001-create-schema-fiscal.yaml` | `sql: CREATE SCHEMA IF NOT EXISTS fiscal` | Cria o schema `fiscal`. **Atenção:** a conversa de reforma tributária usou `tax` — usar `fiscal` aqui conforme decisão registrada. Rollback: `DROP SCHEMA IF EXISTS fiscal CASCADE`. |
| `fiscal/v1/002-addcol-pessoa.yaml` | `addColumn` | Adiciona em `pessoa`: `regime_tributario varchar(20) nullable` (LUCRO_REAL, LUCRO_PRESUMIDO, SIMPLES, MEI, ISENTO, PF), `ibs_cbs_por_fora boolean not null default false` (Simples que optou por recolher IBS/CBS pelo regime regular — gera crédito integral ao comprador, ver Passo 9), `contribuinte_icms boolean not null default false` (relevante até 2033 na transição). Obs: `ie`/`im` NÃO entram em `pessoa` — são por estabelecimento (spec/estabelecimentos-filiais.md). |
| `fiscal/v1/003-addcol-produto.yaml` | `addColumn` | Adiciona em `produto`: `ncm varchar(8) nullable` (8 dígitos, FK lógica para `fiscal.ncm`), `cst_ibs_cbs varchar(2) nullable`, `sujeito_is boolean not null default false`, `aliquota_is_override numeric(5,2) nullable` (sobrescreve a alíquota do NCM quando preenchido), `regime_diferenciado varchar(20) not null default 'PADRAO'` (PADRAO/CESTA_BASICA/REDUCAO_60/MONOFASICO/ISENTO/IMUNE), `cfop_padrao_saida varchar(4) nullable`, `cfop_padrao_entrada varchar(4) nullable`. |
| `fiscal/v1/004-addcol-produto-estoque-config.yaml` | `addColumn` | Adiciona em `produto_estoque_config`: `saldo_atual numeric(15,4) not null default 0`, `saldo_reservado numeric(15,4) not null default 0`. Estes campos serão gerenciados pelo módulo de estoque — incluídos aqui pois fazem parte do escopo do módulo de cadastros. |
| `fiscal/v1/005-addcol-condicao-pagamento.yaml` | `addColumn` | Adiciona em `condicao_pagamento`: `split_payment_aplicavel boolean not null default false`. Quando `true`, pagamentos via PIX e cartão desta condição estarão sujeitos ao split payment a partir de 2027. |

#### Bloco 1C — Tabelas do Motor Fiscal (schema `fiscal`)

| Arquivo | Operação | Descrição |
|---|---|---|
| `fiscal/v1/006-config-empresa.yaml` | `createTable` | Tabela `fiscal.config_empresa` — 1 linha por tenant. Colunas: `tenant_id BIGINT PK` (sem FK), `cnpj varchar(14)`, `razao_social varchar(200)`, `ie varchar(20)`, `im varchar(20)`, `regime_tributario varchar(20)`, `uf varchar(2)`, `ibge_municipio varchar(7)`, `crt varchar(1)` (Código de Regime Tributário: 1=Simples, 2=Simples/excesso, 3=Normal, 4=MEI), `optante_simples boolean default false`, `data_opcao_simples date nullable`. **Equivale a `fiscal.config_empresa` da conversa de reforma tributária.** |
| `fiscal/v1/007-ncm.yaml` | `createTable` + `loadData` | Tabela `fiscal.ncm`. Colunas: `codigo varchar(8) PK`, `descricao text`, `unidade_medida varchar(10)`, `reducao_aliquota_pct numeric(5,2) default 0`, `monofasico boolean default false`, `cesta_basica boolean default false`, `sujeito_is boolean default false`, `aliquota_is_pct numeric(5,2) nullable`, `updated_at timestamptz`. Seed via `loadData` apontando para `ncm.csv` (~10.500 linhas). Fonte: MDIC — tabela NCM pública. Script CSV gerado pelo Claude Code separadamente. |
| `fiscal/v1/008-cst-ibs-cbs.yaml` | `createTable` + seed inline | Tabela `fiscal.cst_ibs_cbs`. Colunas: `id`, `codigo varchar(3) unique`, `descricao varchar(200)`, `natureza varchar(10)` (ENTRADA/SAIDA/AMBOS). Seed inline (~20 códigos baseados na NT 2025.002 — **validar códigos exatos com a NT antes de rodar em produção**). |
| `fiscal/v1/009-cfop.yaml` | `createTable` + `loadData` | Tabela `fiscal.cfop`. Colunas: `id`, `codigo varchar(4) unique`, `descricao varchar(300)`, `tipo_operacao varchar(10)` (ENTRADA/SAIDA), `origem varchar(15)` (INTERNA/INTERESTADUAL/EXTERIOR), `gera_credito_ibs boolean default false`, `gera_credito_cbs boolean default false`. Seed via `loadData` apontando para `cfop.csv` (~600 linhas). Fonte: tabela CFOP SEFAZ pública. |
| `fiscal/v1/010-vigencia-tributo.yaml` | `createTable` + seed inline | Tabela `fiscal.vigencia_tributo` — fases da transição 2026→2033. Colunas: `id`, `ano_inicio int`, `ano_fim int`, `descricao varchar(200)`, `aliquota_ibs_teste numeric(6,4)`, `aliquota_cbs_teste numeric(6,4)`, `split_payment_ativo boolean default false`. Seed inline com as 7 fases da LC 214/2025. **Equivale a `fiscal.vigencia_tributo`**. |
| `fiscal/v1/011-aliq-cbs-regime.yaml` | `createTable` + seed inline | Tabela `fiscal.aliq_cbs_regime` — alíquota CBS por regime tributário e ano de vigência. Colunas: `id`, `ano_vigencia int`, `regime varchar(20)`, `aliquota_pct numeric(6,4)`, `vigente_de date`, `vigente_ate date nullable`. Seed inline com valores 2026–2033 por regime (Lucro Real ~8,8%, Simples Nacional reduzido). **Equivale a `fiscal.aliq_cbs_regime`**. ⚠️ Validar alíquotas com regulamentação atual. |
| `fiscal/v1/012-aliq-ibs-municipio.yaml` | `createTable` | Tabela `fiscal.aliq_ibs_municipio`. Colunas: `id`, `ibge_municipio varchar(7)`, `uf varchar(2)`, `nome_municipio varchar(200)`, `ano_vigencia int`, `aliquota_estadual numeric(6,4)`, `aliquota_municipal numeric(6,4)`, `aliquota_total` (gerado), `vigente_de date`. Unique em `(ibge_municipio, ano_vigencia)`. **Sem seed agora** — tabela completa aguarda publicação do CGIBS. Seed parcial apenas com alíquota teste 2026 (IBS 0,1% total). **Equivale a `fiscal.aliq_ibs_municipio`**. |
| `fiscal/v1/013-aliq-is-ncm.yaml` | `createTable` + seed | Tabela `fiscal.aliq_is_ncm` — alíquota IS por NCM. Colunas: `id`, `ncm varchar(8)`, `descricao varchar(200)`, `aliquota_pct numeric(5,2)`, `vigente_de date`, `vigente_ate date nullable`. Seed com ~50 NCMs sujeitos ao IS (bebidas, cigarros, veículos, etc.) conforme LC 214/2025. ⚠️ Validar lista exata com o texto da lei. **Equivale a `fiscal.aliq_is_ncm`**. |
| `fiscal/v1/014-regime-dif-ncm.yaml` | `createTable` + seed | Tabela `fiscal.regime_dif_ncm` — NCMs com regime diferenciado (cesta básica, redução 60%, etc). Colunas: `id`, `ncm varchar(8)`, `regime varchar(20)`, `percentual_reducao numeric(5,2)`, `vigente_de date`. Seed com Anexos I-IX da LC 214/2025. ⚠️ Lista granular requer o texto da lei. **Equivale a `fiscal.regime_dif_ncm`**. |
| `fiscal/v1/015-operacao-fiscal.yaml` | `createTable` | Tabela `fiscal.operacao_fiscal` — resultado do motor fiscal por operação. Colunas: todas as listadas no §30.7 deste spec. **Equivale a `fiscal.operacao_fiscal` da conversa de reforma tributária** — mesma função, nome diferente conforme decisão de nomenclatura. |

---

### 12.4 Sprint 2 — Contas a Pagar e Contas a Receber (schema `financeiro`)

> Depende do Sprint 1 estar completo. O campo `impostos JSONB` em `titulo` aceita null — não bloqueia se o motor fiscal ainda não estiver gerando dados.

| Arquivo | Operação | Descrição |
|---|---|---|
| `financeiro/v1/005-forma-pagamento.yaml` | `createTable` | Tabelas `financeiro.forma_pagamento` e `financeiro.forma_pagamento_periodo`. `forma_pagamento` define como calcular vencimentos (data referência inclusiva/exclusiva, considera dias úteis). `forma_pagamento_periodo` define os intervalos de faturamento e dia de vencimento. Unique em `(tenant_id, codigo)`. |
| `financeiro/v1/006-tipos-base.yaml` | `createTable` | Tabelas `financeiro.tipo_titulo` (NORMAL/ADIANTAMENTO/EMPRESTIMO por natureza PAGAR/RECEBER/AMBOS), `financeiro.tipo_ajuste` (ACRESCIMO/DESCONTO com categorias MULTA/MORA/DESCONTO/ADIANTAMENTO), `financeiro.tipo_baixa` (meio de pagamento: DINHEIRO/BOLETO/CREDITO_CONTA/PIX/CARTAO/CHEQUE/ANTECIPACAO/COMPENSACAO). Todas com `(tenant_id, codigo, natureza)` unique. |
| `financeiro/v1/007-classificacao-motivo.yaml` | `createTable` | Tabelas `financeiro.classificacao_financeira` (agrupamento livre para relatórios) e `financeiro.motivo` (justificativas de cancelamento, parcelamento, prorrogação). |
| `financeiro/v1/008-parametros.yaml` | `createTable` | Tabela `financeiro.parametros` — 1 linha por tenant. Armazena tipos de ajuste padrão para multa/mora/desconto em AP e AR (usados quando retorno CNAB traz valor diferente do boleto), flag de permissão de baixa com data anterior, consideração de feriado bancário, e tolerância de conciliação automática. |
| `financeiro/v1/009-titulo.yaml` | `createTable` | Tabela `financeiro.titulo` — entidade central. Campos principais: `natureza` (PAGAR/RECEBER), `numero`, `tipo_titulo_id`, `status_titulo` (PREVISTO/EM_ABERTO/EMITIDO/BAIXADO/CANCELADO), `status_baixa` (PLANEJADA/REAL), `terceiro_tipo/id/nome/cnpj_cpf`, `data_emissao/vencimento/competencia`, `valor_original`, `valor_ajuste_acrescimo/desconto`, `valor_baixado`, colunas geradas `valor_liquido` e `valor_saldo`, `origem` (MANUAL/NF_ENTRADA/NF_SAIDA/CNAB/EMPRESTIMO/ADIANTAMENTO/PARCELAMENTO/RENEGOCIACAO), `impostos JSONB` (reservado para IBS/CBS). Índices em `(tenant_id, natureza)`, `(tenant_id, data_vencimento)`, `(tenant_id, terceiro_tipo, terceiro_id)`, `(tenant_id, status_titulo, status_baixa)`. |
| `financeiro/v1/010-addcol-centro-custo-referencias.yaml` | `addColumn` | Adiciona `centro_custo_id BIGINT nullable` e `rateio_id BIGINT nullable` (mutuamente exclusivos — CHECK, §F3) em `financeiro.titulo`. Executar aqui pois `titulo` agora existe. |
| `financeiro/v1/011-titulo-operacoes.yaml` | `createTable` | Tabelas `financeiro.titulo_ajuste` (acréscimos/descontos por tipo), `financeiro.titulo_baixa` (cada evento de pagamento/recebimento com status PLANEJADA/REAL, origem MANUAL/CNAB/COMPENSACAO/ADIANTAMENTO), `financeiro.titulo_prorrogacao` (histórico de prorrogações com data anterior/nova), `financeiro.titulo_parcelamento` (controle de parcelamentos com referência ao título original). |
| `financeiro/v1/012-addcol-centro-custo-baixa.yaml` | `addColumn` | Adiciona `centro_custo_id BIGINT nullable` em `financeiro.titulo_baixa`. Separado da migration anterior pois `titulo_baixa` é criada em 011. |
| `financeiro/v1/013-adiantamento.yaml` | `createTable` | Tabela `financeiro.adiantamento_saldo` — controla saldo disponível de adiantamentos por terceiro (fornecedor ou cliente). Campos: `titulo_id`, `terceiro_tipo/id`, `natureza`, `valor_total`, `valor_utilizado`, coluna gerada `valor_disponivel`. Unique em `(tenant_id, titulo_id)`. |
| `financeiro/v1/014-compensacao.yaml` | `createTable` | Tabela `financeiro.compensacao` — vincula título a pagar com título a receber do mesmo terceiro para compensação mútua. Status: PENDENTE/CONFIRMADA/CANCELADA. Regra: `valor_compensado <= min(saldo_pagar, saldo_receber)`. |
| `financeiro/v1/015-emprestimo.yaml` | `createTable` | Tabela `financeiro.emprestimo` — parâmetros do empréstimo/leasing (valor, taxa, tipo de amortização PRICE/SAC/OUTROS, número de parcelas). Ao confirmar, sistema gera automaticamente N títulos a pagar com `origem = 'EMPRESTIMO'`. |

---

### 12.5 Sprint 3 — Fluxo de Caixa e Conciliação Bancária (schema `financeiro`)

> Depende do Sprint 2 estar completo.

| Arquivo | Operação | Descrição |
|---|---|---|
| `financeiro/v1/016-banco.yaml` | `createTable` | Tabela `financeiro.banco` — cadastro de bancos com código FEBRABAN, máscaras de agência e conta, flag de dígito verificador. Unique em `(tenant_id, codigo_compensacao)`. |
| `financeiro/v1/017-conta-corrente.yaml` | `createTable` | Tabela `financeiro.conta_corrente` — contas bancárias do tenant. Tipo: CORRENTE/POUPANCA/INVESTIMENTO/CAIXA. Campos: `saldo_inicial`, `data_saldo_inicial` (ponto de partida do cálculo — tenant informa o saldo na data de início de uso), `conta_contabil varchar(30)` (reservado para integração com GL). Unique em `(tenant_id, banco_id, agencia, conta)`. |
| `financeiro/v1/018-conta-movimentacao.yaml` | `createTable` | Tabela `financeiro.conta_movimentacao` — lançamentos de crédito e débito na conta corrente. Categoria: LANCAMENTO/TRANSFERENCIA/APLICACAO/RESGATE. Status: PENDENTE/CONFIRMADO/CANCELADO. Apenas CONFIRMADO entra no cálculo de saldo. Campos `conta_destino_id` (para transferências), `titulo_baixa_id` (vínculo com baixa de título), `conciliado boolean`, `extrato_linha_id` (preenchido após conciliação). Também cria `financeiro.tipo_movimentacao_bancaria`. |
| `financeiro/v1/019-addcol-centro-custo-movimentacao.yaml` | `addColumn` | Adiciona `centro_custo_id BIGINT nullable` em `financeiro.conta_movimentacao`. |
| `financeiro/v1/020-extrato-bancario.yaml` | `createTable` | Tabelas `financeiro.extrato_bancario` (linhas do extrato importado via OFX ou inseridas manualmente, com `status_conciliacao`: PENDENTE/CONCILIADO/IGNORADO, `conciliado_tipo`: MANUAL/AUTOMATICO) e `financeiro.extrato_importacao` (controle de cada arquivo OFX importado com período, totais e status do processamento). Unique em `(tenant_id, conta_corrente_id, documento, data_lancamento)` para idempotência de reimportações OFX. |
| `financeiro/v1/021-orcamento-fluxo.yaml` | `createTable` | Tabela `financeiro.orcamento_fluxo` — valores orçados por tenant, mês, conta corrente e classificação financeira para comparação com realizado. Unique em `(tenant_id, ano, mes, conta_corrente_id, classificacao_id, natureza)`. |
| `financeiro/v1/022-view-saldo-conta.yaml` | `createView` | View `financeiro.v_saldo_conta_corrente` — calcula saldo atual de cada conta somando movimentações CONFIRMADAS a partir do `saldo_inicial`. Nunca armazena saldo como campo — sempre recalculado. |

---

### 12.6 Sprint 4 — Tesouraria, Boletos e CNAB (schema `financeiro`)

> Depende do Sprint 3 estar completo.

| Arquivo | Operação | Descrição |
|---|---|---|
| `financeiro/v1/023-cobranca-config.yaml` | `createTable` | Tabela `financeiro.cobranca_config` — parametriza emissão de boletos por conta corrente: `codigo_cedente`, `nosso_numero_atual BIGINT` (sequencial com lock atômico), `carteira`, `modalidade` (SIMPLES/VINCULADA/DESCONTADA), instruções de cobrança, `dias_protesto`, `dias_negativacao`, `layout_cnab` (CNAB240/CNAB400). Unique em `(tenant_id, conta_corrente_id)`. |
| `financeiro/v1/024-boleto.yaml` | `createTable` | Tabela `financeiro.boleto` — boleto emitido vinculado a título a receber. Campos: `nosso_numero`, `codigo_barras varchar(44)` (44 dígitos FEBRABAN), `linha_digitavel varchar(54)`, percentuais de multa/mora/desconto. Status: EMITIDO/REGISTRADO/PAGO/CANCELADO/VENCIDO. Unique em `(tenant_id, conta_corrente_id, nosso_numero)`. |
| `financeiro/v1/025-cnab-remessa.yaml` | `createTable` | Tabelas `financeiro.cnab_remessa` (arquivo de remessa CNAB gerado, tipo COBRANCA ou PAGAMENTO, layout CNAB240/CNAB400, status GERADO/ENVIADO/PROCESSADO/ERRO) e `financeiro.cnab_remessa_item` (cada boleto/título incluído na remessa com tipo de movimento INCLUSAO/EXCLUSAO/ALTERACAO/BLOQUEIO). |
| `financeiro/v1/026-cnab-retorno.yaml` | `createTable` | Tabelas `financeiro.cnab_retorno` (arquivo de retorno recebido do banco) e `financeiro.cnab_retorno_item` (cada linha processada com `codigo_ocorrencia`, valores principal/tarifa/acréscimos/desconto/líquido, status PENDENTE/BAIXADO/IGNORADO/ERRO). Baixas geradas pelo retorno nascem sempre como PLANEJADA. |
| `financeiro/v1/027-cheque.yaml` | `createTable` | Tabela `financeiro.cheque` — controle de cheques emitidos e recebidos. Status: EMITIDO/COMPENSADO/DEVOLVIDO/CANCELADO/SUSTADO. Campo `data_bom_para` alimenta o cron de alerta diário. Unique em `(tenant_id, conta_corrente_id, numero)`. |
| `financeiro/v1/028-aplicacao-financeira.yaml` | `createTable` | Tabela `financeiro.aplicacao_financeira` — controle de aplicações em investimentos (CDB/LCI/LCA/FUNDOS). Só permitida em contas do tipo INVESTIMENTO. Campos de resgate: `valor_resgatado`, `rendimento_bruto`, `ir_retido`, `rendimento_liquido`. Status: ATIVO/RESGATADO/VENCIDO. |
| `financeiro/v1/029-dda-boleto.yaml` | `createTable` | Tabela `financeiro.dda_boleto` — boletos recebidos via DDA (Débito Direto Autorizado). Status: IMPORTADO/VINCULADO/PAGO/IGNORADO. Campo `titulo_id` preenchido quando operador vincula manualmente ao título a pagar. Unique em `(tenant_id, codigo_barras)` para idempotência. |

---

### 12.7 Plano de Contas — sem bloqueio

O template usa o elenco oficial como base e o tenant edita a própria cópia — **os sprints 5 e 6
não dependem de validação externa**. Único pré-requisito: testar o `TenantAtivacaoListener`
em homologação antes de ativar tenant em produção.

---

### 12.8 Sprint 5 — Contabilidade e GL (schema `contabil`)

> Depende do Sprint 4 + validação do plano de contas.

| Arquivo | Operação | Descrição |
|---|---|---|
| `contabil/v1/001-create-schema-contabil.yaml` | `sql` | Cria schema `contabil`. Rollback: `DROP SCHEMA IF EXISTS contabil CASCADE`. |
| `contabil/v1/002-conta.yaml` | `createTable` | Tabela `contabil.conta` — plano de contas hierárquico. Campos: `codigo varchar(30)` (ex: '1.1.1.02'), `tipo` (ATIVO/PASSIVO/PATRIMONIO_LIQUIDO/RECEITA/CUSTO/DESPESA), `natureza` (DEVEDORA/CREDORA), `nivel int`, `conta_pai_id` self-reference, `aceita_lancamento boolean` (só analíticas), `retificadora boolean default false` (contas como depreciação e PCLD que subtraem do grupo no BP). Unique em `(tenant_id, codigo)`. **Sem tabela de filial** — a dimensão é `cadastros.estabelecimento` (FK lógica UUID). |
| `contabil/v1/003-periodo.yaml` | `createTable` | Tabela `contabil.periodo` — período contábil mensal. Campos: `estabelecimento_id UUID` (FK lógica → `cadastros.estabelecimento`; null = consolidado do grupo), `template_versao int`. Status: ABERTO/FECHADO/BLOQUEADO. Unique em `(tenant_id, estabelecimento_id, competencia)`. Permite fechar por estabelecimento individualmente antes do consolidado. |
| `contabil/v1/004-lancamento.yaml` | `createTable` | Tabela `contabil.lancamento` — lançamento contábil. Campos: `numero varchar(20)` (sequencial por período sem lacunas), `origem varchar(30)` (TITULO_BAIXA/MOVIMENTACAO/APURACAO_FISCAL/EMPRESTIMO/APLICACAO/MANUAL), `origem_id BIGINT` (rastreabilidade ao evento financeiro). Status: ATIVO/ESTORNADO. Unique em `(tenant_id, periodo_id, numero)`. |
| `contabil/v1/005-lancamento-partida.yaml` | `createTable` | Tabela `contabil.lancamento_partida` — partidas do lançamento (débito/crédito). Campos: `estabelecimento_id UUID` (dimensão analítica — FK lógica), `centro_custo_id BIGINT` (FK lógica para `financeiro.centro_custo`), `historico varchar(200)`. Regra: soma de débitos = soma de créditos por lançamento — enforçada no `LancamentoService`, não no banco. |
| `contabil/v1/006-mapeamento.yaml` | `createTable` | Tabela `contabil.mapeamento` — de/para entre entidades financeiras e contas contábeis. `tipo_origem`: CONTA_CORRENTE/TIPO_BAIXA/TIPO_AJUSTE/CLASSIFICACAO_FINANCEIRA/TRIBUTO/LINHA_DRE. Campo `linha_dre varchar(50)` permite configurar qual linha da DRE cada conta pertence (configurável — nunca hardcode, pois muda com a reforma tributária). Unique em `(tenant_id, tipo_origem, origem_id)`. |
| `contabil/v1/007-plano-contas-template.yaml` | `createTable` + seed | Tabela `contabil.plano_contas_template` (global, sem `tenant_id`). Campos: `versao int`, `codigo varchar(30)`, `descricao`, `tipo`, `natureza`, `nivel`, `codigo_pai varchar(30)` (referência por código, não por id — necessário para a cópia no `TenantAtivacaoListener`), `aceita_lancamento boolean`, `retificadora boolean`, `ativo boolean`. Seed direto: `versao=1, ativo=true` (sem bloqueio de contador — decisão §F6.6; revisão posterior vira `versao=2`). |
| `contabil/v1/008-addcol-lancamento-centro-custo.yaml` | `addColumn` | Adiciona `centro_custo_id BIGINT nullable` em `contabil.lancamento_partida`. |

---

### 12.9 Sprint 6 — Fechamento Fiscal e Apuração (schema `fiscal`)

> Depende do Sprint 5 (GL) estar operacional — apuração publica evento que o GL consome.

| Arquivo | Operação | Descrição |
|---|---|---|
| `fiscal/v1/016-apuracao-mensal.yaml` | `createTable` | Tabela `fiscal.apuracao_mensal` — resultado consolidado por tenant × competência para recolhimento. Campos de débito/crédito para IBS, CBS e IS, saldo credor acumulado (não expira), campos de tributos do regime atual (ICMS/ISS/PIS/Cofins) durante a transição. Status: ABERTA/FECHADA/RETIFICADA. Unique em `(tenant_id, competencia)`. |

---

### 12.10 Sprint 7 — Análises e PDD (schema `financeiro`)

> Pode ser desenvolvido em paralelo com Sprint 5 e 6 na parte de queries. A migration em si não tem bloqueante.

| Arquivo | Operação | Descrição |
|---|---|---|
| `financeiro/v1/030-pdd-config.yaml` | `createTable` + seed | Tabela `financeiro.pdd_config` — percentuais de provisão (PCLD) por faixa de aging (NAO_VENCIDO/ATE_30/DE_31_60/DE_61_90/ACIMA_90). Unique em `(tenant_id, faixa)`. Seed com defaults: 0,5% / 3% / 8% / 20% / 50%. Seed executado automaticamente na ativação do tenant junto com o plano de contas. |

---

### 12.10-B Migrations Adicionais desta Revisão (v11 → v12)

Novas entidades e colunas introduzidas pelas correções desta revisão — distribuir nos sprints indicados:

| Arquivo | Sprint | Conteúdo |
|---|---|---|
| `fiscal/v1/026-parametro-fiscal.yaml` | 1 | `fiscal.parametro_fiscal` (chave/valor) + seeds de fallback e vencimento de guias (§1.9) |
| `fiscal/v1/027-regra-local-prestacao.yaml` | 1 | `fiscal.regra_local_prestacao` (NBS → regra de local, §1.8-B) + seed exceções LC 214 |
| `financeiro/v1/031-titulo-hold-estabelecimento.yaml` | 2 | addColumn em `titulo`: `bloqueado`, `motivo_bloqueio`, `estabelecimento_id UUID`, `pessoa_id UUID` + índice parcial `(tenant_id, pessoa_id)`; `origem_documento_id` como VARCHAR(50) |
| `financeiro/v1/032-titulo-baixa-estorno.yaml` | 2 | addColumn em `titulo_baixa`: `baixa_estornada_id`, `estornada_at`, `estornada_by`; trocar `CHECK (valor > 0)` por `CHECK (valor <> 0)` + `CHECK (valor > 0 OR origem = 'ESTORNO')` (§4.6.1) |
| `financeiro/v1/033-retencao.yaml` | 2 | `financeiro.titulo_retencao` + `financeiro.retencao_config` (§4.9) |
| `financeiro/v1/034-parametros-multa-mora.yaml` | 2 | addColumn em `parametros`: `percentual_multa`, `percentual_mora_mes`, `sugerir_multa_mora` (§4.6.2) |
| `financeiro/v1/035-approval.yaml` | 2 | `financeiro.approval_regra` + `financeiro.approval_request` (§F7) |
| `financeiro/v1/036-dunning.yaml` | 2 | `financeiro.dunning_regua` + `financeiro.dunning_evento` + seed régua default D+1/7/15/30 (§5.3.1) |
| `financeiro/v1/037-conta-mov-estabelecimento.yaml` | 3 | addColumn `estabelecimento_id UUID` em `conta_movimentacao` |
| `financeiro/v1/038-pix-cobranca.yaml` | 4 | `financeiro.pix_cobranca` (§IV-PIX) |
| `financeiro/v1/039-evento-processado.yaml` | 2 | `financeiro.evento_processado` — chave de idempotência dos consumers Kafka (§F4.5) |
| `financeiro/v1/040-compensacao-unique-pendente.yaml` | 2 | índices parciais únicos em `compensacao` para CO-05 (`titulo_pagar_id` / `titulo_receber_id` WHERE status='PENDENTE') |
| `fiscal/v1/028-apuracao-estabelecimento.yaml` | 6 | addColumn `estabelecimento_id UUID` nullable em `apuracao_mensal` (ICMS/ISS por estabelecimento; IBS/CBS consolidado = NULL) |
| `financeiro/v1/041-parametros-gl-periodo-fechado.yaml` | 5 | addColumn em `parametros`: `gl_fato_periodo_fechado` (§37, passo 2) |

---

### 12.11 Resumo Visual — Ordem de Execução

```
SPRINT 1 — Fundação + Motor Fiscal (sem bloqueante)
├── financeiro/v1/001  feriado-bancario
├── financeiro/v1/002  audit-log
├── financeiro/v1/003  centro-custo
├── fiscal/v1/001      create-schema-fiscal
├── fiscal/v1/002      addcol-pessoa
├── fiscal/v1/003      addcol-produto
├── fiscal/v1/004      addcol-produto-estoque-config
├── fiscal/v1/005      addcol-condicao-pagamento
├── fiscal/v1/006      config-empresa
├── fiscal/v1/007      ncm + loadData CSV
├── fiscal/v1/008      cst-ibs-cbs + seed
├── fiscal/v1/009      cfop + loadData CSV
├── fiscal/v1/010      vigencia-tributo + seed
├── fiscal/v1/011      aliq-cbs-regime + seed
├── fiscal/v1/012      aliq-ibs-municipio (seed parcial)
├── fiscal/v1/013      aliq-is-ncm + seed
├── fiscal/v1/014      regime-dif-ncm + seed
└── fiscal/v1/015      operacao-fiscal

SPRINT 2 — AP/AR
├── financeiro/v1/005  forma-pagamento
├── financeiro/v1/006  tipos-base
├── financeiro/v1/007  classificacao-motivo
├── financeiro/v1/008  parametros
├── financeiro/v1/009  titulo
├── financeiro/v1/010  addcol-centro-custo-titulo
├── financeiro/v1/011  titulo-operacoes
├── financeiro/v1/012  addcol-centro-custo-baixa
├── financeiro/v1/013  adiantamento
├── financeiro/v1/014  compensacao
└── financeiro/v1/015  emprestimo

SPRINT 3 — Fluxo de Caixa / Conciliação
├── financeiro/v1/016  banco
├── financeiro/v1/017  conta-corrente
├── financeiro/v1/018  conta-movimentacao
├── financeiro/v1/019  addcol-centro-custo-movimentacao
├── financeiro/v1/020  extrato-bancario
├── financeiro/v1/021  orcamento-fluxo
└── financeiro/v1/022  view-saldo-conta

SPRINT 4 — Tesouraria
├── financeiro/v1/023  cobranca-config
├── financeiro/v1/024  boleto
├── financeiro/v1/025  cnab-remessa
├── financeiro/v1/026  cnab-retorno
├── financeiro/v1/027  cheque
├── financeiro/v1/028  aplicacao-financeira
└── financeiro/v1/029  dda-boleto

SPRINT 5 — Contabilidade / GL (sem bloqueio — template oficial editável)
├── contabil/v1/001    create-schema-contabil
├── contabil/v1/002    conta
├── contabil/v1/003    periodo
├── contabil/v1/004    lancamento
├── contabil/v1/005    lancamento-partida
├── contabil/v1/006    mapeamento
├── contabil/v1/007    plano-contas-template + seed
└── contabil/v1/008    addcol-lancamento-centro-custo

SPRINT 6 — Apuração Fiscal
└── fiscal/v1/016      apuracao-mensal

SPRINT 7 — Análises
└── financeiro/v1/030  pdd-config + seed

Total: ~76 migrations em 3 schemas novos
(53 dos sprints acima + 14 do §12.10-B + 9 do §1.8.12)
```

---

### 12.12 Scripts Externos (não Liquibase — Claude Code gera separado)

Três tabelas são grandes demais para seed inline no YAML. O Claude Code deve gerar scripts CSV + `loadData`:

| Tabela | Fonte | Volume estimado | Como carregar |
|---|---|---|---|
| `fiscal.ncm` | CSV do MDIC (tabela NCM pública) | ~10.500 linhas | `loadData` no changeset 007 |
| `fiscal.cfop` | CSV tabela CFOP SEFAZ pública | ~600 linhas | `loadData` no changeset 009 |
| `fiscal.aliq_ibs_municipio` | IBGE + CGIBS (quando publicado) | ~5.570 municípios | `loadData` no changeset 012 |

---

### 12.13 Pontos de Atenção para o Claude Code

| # | Ponto | Detalhe |
|---|---|---|
| 1 | Schema `fiscal`, não `tax` | A conversa de reforma tributária usou `tax`. Toda geração de YAML deve usar `fiscal.*` |
| 2 | `user_account.id` é UUID | `audit_log.user_id` deve ser `UUID`, não `BIGINT` |
| 3 | Sem FK cruzando schemas | Igual ao padrão do `billing` — integridade pela aplicação |
| 4 | Rollback obrigatório | Todos os changesets com `rollback` declarado |
| 5 | `preconditions` | Cada `createTable` com `precondition: tableNotExists` para evitar re-execução |
| 6 | `id format` | `{autor}-{schema}-v1.{numero}-{descricao}` — ex: `vitor-financeiro-v1.009-titulo` |
| 7 | Colunas geradas | `valor_liquido` e `valor_saldo` em `titulo` são `GENERATED ALWAYS AS ... STORED` — verificar suporte na versão do PostgreSQL |
| 8 | Seeds de `cst_ibs_cbs` | Códigos baseados na NT 2025.002 — validar antes de rodar |
| 9 | Seed `plano_contas_template` | Seed direto `versao=1, ativo=true` — sem bloqueio de contador (§F6.6) |
| 10 | `aliq_ibs_municipio` | Seed parcial com alíquota 2026 (0,1% total). Seed completo aguarda CGIBS |


---



---

## 13. Arquitetura de Software

> Cross-cutting concerns que afetam todos os módulos. Devem ser definidos antes do primeiro service ser escrito — mudar depois é refatoração em cascata.

---

### 13.1 Estrutura de Pacotes — Projeto Real

> Baseada na estrutura de microserviços existente no repositório. O módulo financeiro segue o mesmo padrão dos serviços já criados.

#### Visão geral do monorepo

```
erp-root/
├── frontend/                        ← Angular
├── gateway/                         ← Spring Cloud Gateway — JWT + rate limiting
├── registry/                        ← Eureka — service discovery
├── common/                          ← módulo compartilhado (jar, não serviço)
│   └── com.l.erp.common/
│       ├── api.dto/                 ← DTOs compartilhados entre serviços
│       ├── domain/                  ← interfaces e classes base
│       └── validation/              ← validadores reutilizáveis
├── liquibase-service/               ← TODAS as migrations ficam aqui
│   └── resources/db-changelog/
│       ├── billing/                 ← já existe
│       ├── cadastro/                ← já existe
│       ├── financeiro/              ← criar — Sprints 2–4 e 7
│       ├── fiscal/                  ← criar — Sprint 1
│       └── contabil/                ← criar — Sprint 5
├── auth-service/                    ← autenticação + RBAC (já existe)
├── billing-service/                 ← planos, assinaturas, comissões (já existe)
├── cadastro-service/                ← pessoa, produto, cliente, fornecedor (já existe)
├── partner-service/                 ← parceiros contadores (já existe)
├── financeiro-service/              ← CRIAR — este spec
├── fiscal-service/                  ← CRIAR — motor fiscal (Módulo I)
└── contabil-service/                ← CRIAR — GL (Módulo V)
```

> **Decisão pendente:** `fiscal-service` e `contabil-service` são serviços separados ou ficam dentro de `financeiro-service`? O padrão atual tem serviços por domínio. Recomendação: começar dentro de `financeiro-service` e extrair quando houver necessidade de deploy independente.

---

#### `financeiro-service` — estrutura interna

Segue o padrão dos serviços existentes: `api`, `domain`, `infra`, `repository`, `services`, `util`.

```
financeiro-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com.l.erp.financeiroservice/
│   │   │       ├── api/
│   │   │       │   ├── titulo/
│   │   │       │   │   ├── TituloController.java
│   │   │       │   │   ├── TituloCreateDTO.java
│   │   │       │   │   ├── TituloResponseDTO.java
│   │   │       │   │   └── TituloFilterDTO.java
│   │   │       │   ├── baixa/
│   │   │       │   ├── compensacao/
│   │   │       │   ├── emprestimo/
│   │   │       │   ├── banco/
│   │   │       │   ├── contacorrente/
│   │   │       │   ├── conciliacao/
│   │   │       │   ├── fluxocaixa/
│   │   │       │   ├── tesouraria/
│   │   │       │   │   ├── boleto/
│   │   │       │   │   ├── cnab/
│   │   │       │   │   └── dda/
│   │   │       │   └── relatorio/
│   │   │       │
│   │   │       ├── domain/
│   │   │       │   ├── Titulo.java              ← entidade JPA + @Version
│   │   │       │   ├── TituloBaixa.java
│   │   │       │   ├── Compensacao.java
│   │   │       │   ├── Emprestimo.java
│   │   │       │   ├── Banco.java
│   │   │       │   ├── ContaCorrente.java
│   │   │       │   ├── ContaMovimentacao.java
│   │   │       │   ├── ExtratoBancario.java
│   │   │       │   ├── Boleto.java
│   │   │       │   ├── CnabRemessa.java
│   │   │       │   ├── CnabRetorno.java
│   │   │       │   ├── Cheque.java
│   │   │       │   ├── AplicacaoFinanceira.java
│   │   │       │   └── events/
│   │   │       │       ├── TituloBaixadoEvent.java
│   │   │       │       ├── TituloCanceladoEvent.java
│   │   │       │       └── ContaMovimentacaoConfirmadaEvent.java
│   │   │       │
│   │   │       ├── infra/
│   │   │       │   ├── audit/
│   │   │       │   │   ├── AuditListener.java   ← @EntityListeners
│   │   │       │   │   └── AuditContext.java    ← ThreadLocal userId/ip
│   │   │       │   ├── cache/
│   │   │       │   │   └── CacheConfig.java     ← Caffeine
│   │   │       │   ├── async/
│   │   │       │   │   └── AsyncConfig.java     ← pools relatorioExecutor, cnabExecutor
│   │   │       │   └── exceptions/
│   │   │       │       ├── BusinessException.java
│   │   │       │       └── GlobalExceptionHandler.java
│   │   │       │
│   │   │       ├── repository/
│   │   │       │   ├── TituloRepository.java
│   │   │       │   ├── TituloBaixaRepository.java
│   │   │       │   ├── BoletoRepository.java
│   │   │       │   ├── ContaCorrenteRepository.java
│   │   │       │   └── ...
│   │   │       │
│   │   │       ├── services/
│   │   │       │   ├── TituloService.java
│   │   │       │   ├── BaixaService.java
│   │   │       │   ├── CompensacaoService.java
│   │   │       │   ├── AdiantamentoService.java
│   │   │       │   ├── EmprestimoService.java
│   │   │       │   ├── TituloCalculoService.java ← valor_liquido, valor_saldo
│   │   │       │   ├── ConciliacaoService.java
│   │   │       │   ├── FluxoCaixaService.java
│   │   │       │   ├── BoletoService.java
│   │   │       │   ├── boleto/
│   │   │       │   │   ├── CodigoBarrasGenerator.java  ← interface Strategy
│   │   │       │   │   ├── BancoBrasilGenerator.java
│   │   │       │   │   ├── ItauGenerator.java
│   │   │       │   │   ├── BradescoGenerator.java
│   │   │       │   │   ├── CaixaGenerator.java
│   │   │       │   │   └── SantanderGenerator.java
│   │   │       │   ├── CnabRemessaService.java
│   │   │       │   ├── CnabRetornoParser.java
│   │   │       │   ├── DdaService.java
│   │   │       │   ├── ChequeService.java
│   │   │       │   ├── AplicacaoFinanceiraService.java
│   │   │       │   ├── RelatorioAgingService.java
│   │   │       │   ├── FluxoCaixaExportService.java
│   │   │       │   └── jobs/
│   │   │       │       ├── BoletoVencidoJob.java
│   │   │       │       ├── AplicacaoVencidaJob.java
│   │   │       │       └── ChequeCompensacaoJob.java
│   │   │       │
│   │   │       ├── util/
│   │   │       │   ├── FeriadoUtils.java        ← proximoDiaUtil()
│   │   │       │   └── CnabUtils.java
│   │   │       │
│   │   │       └── FinanceiroServiceApplication.java
│   │   │
│   │   └── resources/
│   │       └── application.yml
│   │
│   └── test/
├── .gitattributes
├── .gitignore
├── HELP.md
├── mvnw / mvnw.cmd
└── pom.xml
```

---

#### `common` — o que vai aqui vs. no serviço

```
com.l.erp.common/
├── api.dto/
│   ├── ApiResponse.java             ← envelope { data, meta, error }
│   ├── PageMeta.java                ← paginação { page, size, total_elements }
│   ├── ErrorResponse.java           ← { code, message, fields, timestamp }
│   └── FieldError.java
├── domain/
│   ├── BaseTenantEntity.java        ← @MappedSuperclass — já existe
│   ├── DomainEvent.java             ← interface base de eventos
│   └── DomainEventPublisher.java    ← wrapper ApplicationEventPublisher
└── validation/
    └── CnpjCpfValidator.java        ← provavelmente já existe
```

**Regra:** `common` só contém código sem dependência de negócio específico. `TituloService` não vai para `common` — vai para `financeiro-service`.

---

#### `liquibase-service` — onde as migrations do financeiro vão

```
liquibase-service/
└── src/main/resources/db-changelog/
    ├── billing/                     ← já existe (v1/)
    ├── cadastro/                    ← já existe
    ├── financeiro/                  ← CRIAR — Sprints 2, 3, 4 e 7
    │   ├── db.changelog-financeiro.yaml
    │   └── v1/
    │       ├── 001-feriado-bancario.yaml
    │       ├── 002-audit-log.yaml
    │       ├── 003-centro-custo.yaml
    │       └── ...030-pdd-config.yaml
    ├── fiscal/                      ← CRIAR — Sprint 1
    │   ├── db.changelog-fiscal.yaml
    │   └── v1/
    │       ├── 001-create-schema-fiscal.yaml
    │       └── ...016-apuracao-mensal.yaml
    └── contabil/                    ← CRIAR — Sprint 5
        ├── db.changelog-contabil.yaml
        └── v1/
            ├── 001-create-schema-contabil.yaml
            └── ...008-addcol-lancamento-centro-custo.yaml
```

O `db-changelog-master.yaml` inclui os masters de cada módulo. O `liquibase-service` roda as migrations de todos os serviços — nenhum serviço individual gerencia seu próprio schema.

---

#### `gateway` — o que já resolve

```
gateway/
└── com.l.erp.gateway/
    ├── security/                    ← validação de JWT já está aqui
    └── SecurityConfig.java          ← rate limiting vai aqui também
```

**Implicação:** o módulo financeiro **não precisa** de filtro de autenticação próprio. O gateway já valida o JWT e injeta os claims. O `financeiro-service` confia no token recebido e extrai `tenant_id` e `permissions` dos headers propagados pelo gateway.

---

#### Observações sobre serviços existentes

| Serviço | Relação com o financeiro |
|---|---|
| `auth-service` | Emite JWT com `permissions[]`. O financeiro lê as permissões do token — não chama o auth diretamente |
| `billing-service` | Controla se o tenant está ATIVO. O financeiro **não é chamado** pelo billing e **não chama** o billing |
| `cadastro-service` | Gerencia `pessoa`, `produto`, `cliente`, `fornecedor`. O financeiro referencia `terceiro_id` sem FK — integridade pela aplicação |
| `partner-service` | Separado do billing. Confirmar se `billing.partner` pertence ao billing-service ou ao partner-service |
| `registry` | Eureka — o financeiro se registra como qualquer outro serviço |
| `common` | DTOs compartilhados e `BaseTenantEntity` — `financeiro-service` depende deste módulo |

---

### 13.2 Formato Padrão de Resposta da API

Toda resposta da API segue o mesmo envelope. Controllers nunca retornam entidades JPA diretamente.

**Sucesso — dado único:**
```json
{
  "data": { ... },
  "meta": null
}
```

**Sucesso — lista paginada:**
```json
{
  "data": [ ... ],
  "meta": {
    "page": 0,
    "size": 20,
    "total_elements": 145,
    "total_pages": 8
  }
}
```

**Erro de negócio (400):**
```json
{
  "error": {
    "code": "TITULO_JA_BAIXADO",
    "message": "Não é possível cancelar um título com baixas confirmadas.",
    "field": null,
    "timestamp": "2025-06-15T10:30:00Z"
  }
}
```

**Erro de validação (422):**
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Erros de validação encontrados.",
    "fields": [
      { "field": "valor", "message": "deve ser maior que zero" },
      { "field": "data_vencimento", "message": "não pode ser anterior à data de emissão" }
    ],
    "timestamp": "2025-06-15T10:30:00Z"
  }
}
```

**Códigos de erro de negócio padronizados por módulo:**

| Prefixo | Módulo |
|---|---|
| `TITULO_*` | AP/AR |
| `BAIXA_*` | Baixas |
| `COMPENSACAO_*` | Compensação |
| `BOLETO_*` | Tesouraria |
| `CNAB_*` | CNAB |
| `CONCILIACAO_*` | Conciliação |
| `FISCAL_*` | Motor fiscal |
| `CONTABIL_*` | GL |
| `PERIODO_*` | Período contábil |

---

### 13.3 Paginação e Filtros

Convenção uniforme em todos os endpoints de listagem.

**Query params padrão:**

| Param | Tipo | Default | Descrição |
|---|---|---|---|
| `page` | int | 0 | Página (zero-based) |
| `size` | int | 20 | Itens por página (max 100) |
| `sort` | string | `created_at,desc` | Campo e direção |

**Filtros por convenção de sufixo:**

| Sufixo | Operador | Exemplo |
|---|---|---|
| `_eq` | igual | `status_eq=EM_ABERTO` |
| `_in` | lista | `status_in=EM_ABERTO,PREVISTO` |
| `_de` / `_ate` | intervalo de data | `vencimento_de=2025-06-01` |
| `_min` / `_max` | intervalo numérico | `valor_min=100.00` |
| `_like` | contém (case-insensitive) | `terceiro_nome_like=cliente` |

Implementação via `Specification<T>` do Spring Data JPA — não repetir filtros em cada repository.

---

### 13.4 Publicação de Eventos de Domínio

**Decisão:** eventos **internos ao serviço** via Spring `ApplicationEventPublisher`; eventos que **cruzam serviços** (NF-e, ativação de tenant) via Kafka (contrato em §F4) — não usar broker para o que fica dentro do mesmo deploy.

**Interface base:**
```java
public interface DomainEvent {
    Long getTenantId();
    Instant getOccurredAt();
}
```

**Eventos definidos e seus consumidores:**

| Evento | Mecanismo | Publicado por | Consumido por |
|---|---|---|---|
| `TituloBaixadoEvent` | In-process | `BaixaService` | `ContaMovimentacaoService`, `GlEventListener` |
| `TituloCanceladoEvent` | In-process | `TituloService` | `GlEventListener` |
| `EmprestimoQuitadoEvent` | In-process | `EmprestimoService` | `GlEventListener` |
| `ContaMovimentacaoConfirmadaEvent` | In-process | `MovimentacaoService` | `GlEventListener`, `FluxoCaixaService` |
| `ApuracaoFechadaEvent` | In-process | `ApuracaoMensalService` | `GlEventListener` |
| `AplicacaoResgatadaEvent` | In-process | `AplicacaoFinanceiraService` | `GlEventListener` |
| `TenantAtivadoEvent` | **Kafka** (auth-service é outro processo — in-process não alcança) | Auth Service (externo) | `TenantAtivacaoListener` |
| `nfe.entrada.aprovada` | **Kafka** | fiscal-service / emissor | `NfeEntradaConsumer` → `TituloService` |
| `nfe.saida.autorizada` | **Kafka** | fiscal-service / emissor | `NfeSaidaConsumer` → `TituloService` |
| `nfe.cancelada` | **Kafka** | fiscal-service / emissor | `NfeCanceladaConsumer` → `TituloService` |

> Eventos **in-process** usam `@TransactionalEventListener` — ficam dentro do mesmo serviço.
> Eventos **Kafka** cruzam serviços — payload rico, sem Feign, retry via `consumer_error_log` já existente.

**Regra:** eventos são publicados **após** o commit da transação via `@TransactionalEventListener(phase = AFTER_COMMIT)`. Nunca dentro da transação — garante que o consumidor vê os dados persistidos.

```java
// Correto
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onTituloBaixado(TituloBaixadoEvent event) {
    contaMovimentacaoService.criarDesd(event);
}

// Errado — consumidor pode ler dados ainda não commitados
@EventListener
public void onTituloBaixado(TituloBaixadoEvent event) { ... }
```

---

### 13.5 Tratamento de Concorrência

Dois usuários operando o mesmo título simultaneamente é um cenário real (operador de caixa + tesouraria, por exemplo).

**Estratégia: Optimistic Locking com `@Version`**

```java
@Entity
public class Titulo extends BaseTenantEntity {

    @Version
    private Long version;

    // ...
}
```

Quando dois usuários tentam atualizar o mesmo título ao mesmo tempo, o segundo recebe `ObjectOptimisticLockingFailureException`. O `GlobalExceptionHandler` converte para:

```json
{
  "error": {
    "code": "TITULO_MODIFICADO_CONCORRENTEMENTE",
    "message": "Este título foi alterado por outro usuário. Recarregue e tente novamente."
  }
}
```

**Casos que exigem Pessimistic Lock (`SELECT FOR UPDATE`):**

| Operação | Por quê |
|---|---|
| Incremento de `nosso_numero_atual` em `cobranca_config` | Sequencial único — race condition gera boletos com mesmo número |
| Baixa parcial que zera `valor_saldo` | Dois usuários baixando parcelas simultaneamente podem ultrapassar o saldo |
| Criação de sequence de Livro Diário | Numeração sem lacunas |

```java
// Para nosso_numero — pessimistic lock
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM CobrancaConfig c WHERE c.id = :id")
CobrancaConfig findByIdForUpdate(@Param("id") Long id);
```

**Tabelas com `@Version`:** `Titulo`, `TituloBaixa`, `CobrancaConfig`, `ApuracaoMensal`, `Periodo`.

---

### 13.6 Cache

**Decisão:** Caffeine (in-process) por padrão. Redis quando houver múltiplas instâncias em produção — mesma anotação, troca só o provider no `application.yml`.

**O que cachear:**

| Cache | Chave | TTL | Invalidar quando |
|---|---|---|---|
| `aliquota-ibs` | `ibge_municipio + ano_vigencia` | 7 dias | Migration de alíquota nova (muda no máximo 1x/ano) |
| `aliquota-cbs` | `ano_vigencia` | 7 dias | Migration de alíquota nova (muda com legislação) |
| `feriados` | `ano + uf` | 24h | Nova migration de feriado |
| `plano-contas` | `tenant_id` | 1h | Conta criada/alterada |
| `dashboard` | `tenant_id` | 5min | Qualquer baixa ou movimentação |
| `pdd-config` | `tenant_id` | 1h | Config de PDD alterada |

```yaml
# application.yml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=500,expireAfterWrite=3600s
```

---

### 13.7 Processamento Assíncrono

**Decisão:** `@Async` do Spring com pool de threads dedicado por tipo de operação. Sem broker externo por enquanto.

**Pools de threads:**

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("relatorioExecutor")
    public Executor relatorioExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(20);
        exec.setThreadNamePrefix("relatorio-");
        return exec;
    }

    @Bean("cnabExecutor")
    public Executor cnabExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(10);
        exec.setThreadNamePrefix("cnab-");
        return exec;
    }
}
```

**Operações assíncronas:**

| Operação | Pool | Motivo |
|---|---|---|
| Geração PDF de relatório > 1.000 linhas | `relatorioExecutor` | Bloqueia thread HTTP por segundos |
| Geração XLSX de relatório | `relatorioExecutor` | Idem |
| Processamento de retorno CNAB | `cnabExecutor` | Arquivo pode ter milhares de linhas |
| Envio de e-mail (carta de cobrança, extrato) | `emailExecutor` | I/O de rede |
| Cálculo de conciliação automática em lote | `cnabExecutor` | CPU-bound |

**Status de jobs assíncronos:** para operações longas, o endpoint retorna `202 Accepted` com um `job_id`. O cliente faz polling:

```
POST /api/financeiro/relatorios/aging/exportar
→ 202 Accepted
   { "job_id": "abc123", "status": "PROCESSANDO" }

GET /api/financeiro/jobs/abc123
→ 200 OK
   { "job_id": "abc123", "status": "CONCLUIDO", "download_url": "/api/..." }
```

---

### 13.8 Observabilidade

**Logging estruturado (JSON):**

```java
// Nunca logar assim:
log.info("Título " + id + " baixado por " + user);

// Sempre assim (compatível com Loki/CloudWatch/Datadog):
log.info("titulo.baixado",
    kv("titulo_id", id),
    kv("tenant_id", tenantId),
    kv("user_id", userId),
    kv("valor", valor)
);
```

**MDC obrigatório em toda requisição:**
```java
MDC.put("tenant_id", tenantId.toString());
MDC.put("user_id", userId.toString());
MDC.put("trace_id", traceId);  // gerado pelo gateway ou UUID
```

**Métricas a instrumentar (Micrometer → Prometheus):**

| Métrica | Tipo | O que mede |
|---|---|---|
| `financeiro.titulo.criado` | Counter | Títulos criados por natureza |
| `financeiro.baixa.confirmada` | Counter | Baixas por tipo_baixa |
| `financeiro.conciliacao.pendente` | Gauge | Linhas pendentes por conta |
| `financeiro.boleto.emitido` | Counter | Boletos por banco |
| `fiscal.calculo.duracao` | Timer | Latência do motor fiscal |
| `contabil.lancamento.criado` | Counter | Lançamentos por origem |

**Health checks:**
```java
// Expor via /actuator/health
- DB connection (automático)
- Liquibase migrations (automático)
- Cache disponível
- AliquotaIbsProvider (última atualização < 8 dias)
```

---

### 13.9 Segurança da API

**Autenticação:** JWT emitido pelo `auth-service` e **validado pelo `gateway`**. O `financeiro-service` não valida o token — recebe os claims já propagados pelo gateway via headers (`X-Tenant-Id`, `X-User-Id`, `X-Permissions`). Stateless por padrão.

**Claims obrigatórios no JWT:**
```json
{
  "sub": "user-uuid",
  "tenant_id": 123,
  "name": "Nome do Usuário",
  "permissions": ["TITULO:CRIAR", "TITULO:BAIXAR", "RELATORIO:EXPORTAR"],
  "exp": 1750000000
}
```

**Validação de permissão no controller:**
```java
@PreAuthorize("hasPermission('TITULO', 'CANCELAR')")
@DeleteMapping("/{id}/cancelar")
public ResponseEntity<Void> cancelar(@PathVariable Long id) { ... }
```

**Rate limiting por tenant:** evita que um tenant sobrecarregue o sistema.
```yaml
# Sugestão de limites
/api/financeiro/titulos:         100 req/min por tenant
/api/financeiro/cnab/retorno:    10  req/min por tenant
/api/fiscal/calcular:            200 req/min por tenant
/api/financeiro/relatorios/*:    20  req/min por tenant
```

**Sanitização:** toda string recebida da API é sanitizada antes de entrar no banco. Campos `historico`, `observacao` e `descricao` são os mais expostos.

---

### 13.10 Convenções de DTOs

Nunca expor entidades JPA diretamente. Separação clara entre camadas:

```
Controller ← recebe →  RequestDTO
Controller → retorna → ResponseDTO
Service    ← usa →     entidade JPA internamente
Service    → mapeia →  ResponseDTO via MapStruct
```

**Convenção de nomes:**

| Sufixo | Uso |
|---|---|
| `CreateDTO` | Payload de criação (POST) |
| `UpdateDTO` | Payload de atualização (PUT/PATCH) |
| `ResponseDTO` | Resposta padrão |
| `SummaryDTO` | Resposta reduzida para listagens |
| `FilterDTO` | Parâmetros de filtro (query params) |

**MapStruct** para mapeamento — não usar `BeanUtils.copyProperties` (frágil) nem mapear manualmente em services (verboso).

---

### 13.11 Migrations — Convenções Adicionais

Complementa o que está em §10.2.

```yaml
# Estrutura padrão de todo changeset
- changeSet:
    id: vitor-financeiro-v1.009-titulo          # único no projeto todo
    author: vitor
    preConditions:
      - onFail: MARK_RAN
      - tableNotExists:
          schemaName: financeiro
          tableName: titulo
    changes:
      - createTable:
          ...
    rollback:
      - dropTable:
          schemaName: financeiro
          tableName: titulo
```

**Atenção:** as migrations não ficam no `financeiro-service` — ficam no `liquibase-service` conforme estrutura do projeto. Ver §12.1 para a localização correta dos arquivos.

**Regras:**
- `id` único globalmente — formato `{autor}-{schema}-v{versao}.{numero}-{descricao}`
- `preConditions` em todo `createTable` e `createIndex` — evita erro em re-execução
- `rollback` obrigatório em todo changeset
- Nunca usar `runOnChange: true` em produção
- DDL e DML separados — não misturar `createTable` com `insert` no mesmo changeset (exceto seeds de configuração)
- Seeds de tabelas grandes via `loadData` com arquivo CSV separado

---

### 13.12 Checklist — Antes de Escrever o Primeiro Service

```
□ GlobalExceptionHandler implementado com todos os formatos de resposta
□ BaseTenantEntity + TenantFilterAspect cobrindo schema financeiro
□ AuditListener configurado e testado
□ DomainEventPublisher wrapper criado
□ Caffeine configurado no application.yml
□ MDC filter adicionado ao filter chain (tenant_id, user_id, trace_id)
□ MapStruct dependency no pom.xml
□ @Version adicionado nas entidades críticas
□ Pools de thread configurados (relatorioExecutor, cnabExecutor)
□ PermissionEvaluator implementado consumindo claims do JWT
□ Testes de integração com @SpringBootTest rodando contra banco real (Testcontainers)
```

---


---



---

## 14. Roadmap Reforma Tributária

| Fase | Entrega | Prazo |
|---|---|---|
| 1 | Módulos I–VI (este documento) | Agora |
| 2 | Emissão NF-e com campos IBS/CBS obrigatórios | Antes de agosto/2026 |
| 3 | Motor fiscal: alíquotas IBS/CBS por NCM e destino | 2026/2027 |
| 4 | Split payment em `conta_movimentacao` + `titulo_baixa` | Antes de 2027 |
| 5 | Apuração IBS/CBS, DCTFWeb, declaração CGIBS | 2027–2033 |
| 6 | ICMS/ISS → extinção progressiva nos relatórios | 2029–2033 |

**Roadmap não-fiscal (decisões registradas nesta revisão):**

| Item | Motivo |
|---|---|
| Faturamento recorrente (assinatura/contrato) como origem de título AR | Não sai em 2026 — `origem = 'RECORRENTE'` já reservado no enum do título |
| Adquirência/cartão no AR (taxas, agenda de recebíveis) | Fora do desenho aprovado (SVG) — marcar como futuro |
| Entrada multi-canal de NF no AP: portal do fornecedor, OCR, EDI | Fora de escopo desta versão — entrada via Kafka NF-e e manual |
| Matching 3 vias (PO × Recebimento × NF) com hold automático | Depende do módulo de Compras; o campo `titulo.bloqueado` já dá o hold manual |

**Campos reservados (já no schema, sem migration futura):**

```sql
financeiro.titulo.impostos JSONB                    -- absorve IBS/CBS/IS
-- Adicionar em conta_movimentacao (migration futura):
valor_retido_governo  NUMERIC(15,2) DEFAULT 0
tipo_retencao         VARCHAR(20)   -- 'IBS' | 'CBS' | 'IBS_CBS'
-- Adicionar em titulo_baixa (migration futura):
valor_split_payment   NUMERIC(15,2) DEFAULT 0
```

---

### 14.1 Obrigações acessórias (arquivos da Receita) — escopo por regime

> **Decisão de escopo:** o MVP atende tenant **Simples Nacional** (público-alvo). Isso dispensa a
> maior parte dos SPEDs — o que sobra é exportar dados para o contador do tenant transmitir.
> Tenants Lucro Presumido/Real entram na Fase 2 e puxam ECD/ECF/EFD ICMS-IPI.
> O regime tributário do tenant é parametrização (`SIMPLES_NACIONAL` | `LUCRO_PRESUMIDO` |
> `LUCRO_REAL`) e governa quais obrigações e retenções se aplicam.

| Obrigação | Simples Nacional (MVP) | Papel do ERP | Fase |
|---|---|---|---|
| **PGDAS-D** (mensal) + **DEFIS** (anual) | Obrigatório — transmitido pelo contador no portal | Exportar **relatório de receita bruta** segregada por atividade/anexo e por estabelecimento (base do PGDAS-D); dados vêm dos títulos AR por competência | MVP |
| **EFD-Reinf** | **Obrigatório quando há fato** (ME/EPP do Simples = 3º grupo, desde 10/mai/2021): retenção de INSS 11% ao contratar serviço com cessão de mão de obra (R-2010) e pagamentos a PF/PJ com IRRF (R-4010/R-4020, série **R-4000** — que substituiu a DIRF) | ERP **não transmite**; exporta **relatório de pagamentos e retenções** por período/CNPJ-CPF/natureza de rendimento a partir das baixas (§4.9). Atenção: R-4010/4020 são devidos **mesmo sem retenção** nos casos previstos (e retenção dispensada < R$ 10 também é informada) — o relatório lista os pagamentos por natureza, não só as baixas `meio = 'RETENCAO'` | MVP |
| **DCTFWeb** | Gerada no portal a partir de Reinf/eSocial | Nenhum (consequência do Reinf) | — |
| **ECD (SPED Contábil)** | **Dispensado** para Simples | Fundação já pronta: Livro Diário com numeração sem lacunas (§41) + fechamento imutável (§42). Exportação do arquivo ECD = Fase 2 (tenants Lucro Presumido) | Fase 2 |
| **ECF** | **Não se aplica** (Simples entrega DEFIS) | Depende do de-para com plano referencial (abaixo) | Fase 2 |
| **EFD ICMS/IPI (SPED Fiscal)** | **Dispensado** em regra para Simples (exigências estaduais pontuais fora de escopo) | Nenhum no MVP; reavaliar na Fase 2 com Lucro Presumido + mercadorias | Fase 2+ |
| **Obrigações municipais de ISS** (DES/DMS) | ISS do Simples é recolhido via DAS/PGDAS — declarações municipais dispensadas em regra; emissão de **NFS-e** é do motor fiscal | Nenhum no financeiro | — |

**Preparação barata agora (evita retrabalho na Fase 2):**

- `contabil.conta.conta_referencial VARCHAR(20) NULL` — de-para com o **Plano de Contas
  Referencial da RFB**, exigido por ECD/ECF. Campo opcional na migration do plano de contas;
  o template versionado (§F6) já pode vir com o de-para preenchido.
- Retenções na baixa (§4.9) devem guardar a **natureza do rendimento** (Tabela 01 dos leiautes
  da EFD-Reinf) — é ela que, combinada ao tipo de beneficiário, define o **código de receita**
  do DARF e a periodicidade de recolhimento. Guardar ambos (natureza + código derivado).
- **Competência por tributo**: INSS retido (R-2010) é apurado pela **emissão da NF** do
  prestador; IR retido (R-4010/4020) pela data do **pagamento/crédito** (a baixa). O relatório
  de retenções precisa das duas visões — o modelo já tem as duas datas (título e baixa).
- Motor de retenção **por regime do pagador**: tenant Simples **não retém** PIS/COFINS/CSLL
  (art. 30, Lei 10.833 exclui optantes), mas **retém** IRRF sobre serviços profissionais e
  INSS 11% em cessão de mão de obra. A matriz regime × tributo retido é configuração, não código.

> **Arquivos de referência em `spec/`:**
> - `Manual da EFD-Reinf versão 2.1.2.1.pdf` — manual do **usuário** (leiaute 2.1.2, ADE Cofis
>   23/2023, ago/2023); cobre toda a série R-4000 e as regras de negócio citadas acima.
> - `Manual-Desenvolvedor-EFD-Reinf-v2.7.pdf` — manual do **desenvolvedor** v2.7 (out/2025): API REST de lote assíncrono +
>   webservice SOAP síncrono, certificado digital/procuração, XSDs, consulta de recibo. Só é
>   relevante se um dia o ERP transmitir direto (hoje o escopo é exportar relatório).
> - `EFD-REINF - Tabela 01.xlsx/.docx` — **Natureza de Rendimentos** (código, flags 13º/RRA/
>   deduções/isento, beneficiário PF/PJ, tributo, vigência início/fim). É o **seed** da
>   configuração de retenção (a "natureza do rendimento" que a baixa deve guardar).
> - `EFD-REINF - Tabela R-4010/4020/4040/4080.xlsx/.docx` — de-para **natureza × residência
>   fiscal × tributo × código de receita × periodicidade de recolhimento** com vigências
>   (R-4020 tem ~1.000 combinações). É o seed da derivação do código de receita do DARF.
>   Usar os `.xlsx` como fonte de seed Liquibase (mesmo padrão do NCM); os `.docx` são
>   duplicatas para leitura humana.
>
> **⚠️ CNPJ alfanumérico:** nenhum dos dois manuais trata do assunto (as menções a
> "alfanumérico" no manual do desenvolvedor são tipos de campo — recibo/ID de evento).
> Confirmar a NT/versão de leiaute vigente antes de implementar o relatório de retenções.
> O manual v1.5.1.3 (2021), obsoleto, foi removido da pasta.

---

## 15. Maturidade do Documento

```
Modelagem de dados       ████████░░  85%
Regras de negócio        ████████░░  80%
Motor Fiscal             ██████████  100% ← todos os seeds gerados: Anexos I–XV + XVII completos
Fiscal / contábil        ████████░░  82%  ← plano de contas e demonstrações aguardam contador
Integrações técnicas     ███████░░░  70%  ← CNAB especificado campo a campo (§IV-CNAB.1); NF-e ainda pendente
Arquitetura de software  █████████░  85%

Maturidade geral         ████████░░  82%
```

### Status detalhado por item

| Item | Status | Arquivo | Detalhe |
|---|---|---|---|
| CST IBS/CBS (18 códigos) | ✅ | `seed_cst_ibs_cbs.sql` | RT 2025.002 v1.10 — oficial |
| cClassTrib (156 linhas) | ✅ | `c_class_trib.csv` | cClassTrib_2026_04_15.xlsx — oficial |
| NCM (10.520 códigos) | ✅ | `ncm_codigos.csv` | Vigência 01/02/2026 — oficial |
| CBS 2026 = 0,90% | ✅ | `seed_aliq_cbs.sql` | IT 2026.002 v1.00 — oficial |
| cCredPres (13 códigos) | ✅ | spec §1.8.3 | RT 2025.002 — oficial |
| Anexo I — Alimentos básicos (zero) | ✅ | spec §1.8.6 | LC 214/2025 — oficial |
| Anexo II — Educação (60%) | ✅ | spec §1.8.8 | LC 214/2025 — oficial |
| Anexo III — Saúde (60%) | ✅ | spec §1.8.8 | LC 214/2025 — oficial |
| Anexo IV — Disp. médicos (60%) | ✅ | `seed_anexo_iv_disp_medicos_60.csv` | 66 NCMs — LC 214/2025 |
| Anexo V — Acessibilidade PcD (60%) | ✅ | `seed_anexo_v_acessibilidade_pcd_60.csv` | 21 NCMs — LC 214/2025 |
| Anexo VI — Nutrição enteral (60%) | ✅ | `seed_anexo_vi_nutricao_60.csv` | 65 NCMs — LC 214/2025 |
| Anexo VII — Alimentos (60%) | ✅ | spec §1.8.6 | LC 214/2025 — oficial |
| Anexo VIII — Higiene (60%) | ✅ | spec §1.8.10 | LC 214/2025 — oficial |
| Anexo IX — Insumos agro (60%) | ✅ | `seed_anexo_ix_*` (2 arquivos) | 29 NCMs+NBS — LC 214/2025 |
| Anexo X — Produções artísticas (60%) | ✅ | `seed_anexo_x_producoes_artisticas_60.csv` | 46 NBS — LC 214/2025 |
| Anexo XI — Segurança nacional (60%) | ✅ | `seed_anexo_xi_seguranca_nacional_60.csv` | 30 NCMs+NBS — LC 214/2025 |
| Anexo XII — Disp. médicos (zero) | ✅ | `seed_anexo_xii_disp_medicos_zero.csv` | 19 NCMs — LC 214/2025 |
| Anexo XIII — Acessibilidade PcD (zero) | ✅ | `seed_anexo_xiii_acessibilidade_pcd_zero.csv` | 7 NCMs — LC 214/2025 |
| Anexo XIV — Medicamentos (zero) | ✅ | `seed_anexo_xiv_medicamentos_zero.csv` | 87 NCMs — LC 214/2025 |
| Anexo XV — Hortícolas/frutas/ovos (zero) | ✅ | spec §1.8.9 | LC 214/2025 — oficial |
| Anexo XVII — IS (NCMs) | ✅ | spec §1.8.7 | LC 214/2025 — oficial |
| Simples/MEI 2026 (sem CST) | ✅ | spec §1.8.1 | RT 2025.002 — confirmado |
| MEI valores fixos 2027–2033 | ✅ | spec §1.8.11 | Anexo XXIII LC 214/2025 |
| Simples % IBS/CBS por faixa | ✅ | spec §1.8.11 | Anexos XVIII–XXII LC 214/2025 |
| **Alíquotas IS numéricas** | ⏳ | — | Aguarda regulamentação — fora do controle |
| **Alíquotas IBS por município** | ⏳ | — | Aguarda CGIBS — único bloqueante para produção |
| Plano de contas padrão | ✅ | §F6.5 | Elenco oficial como base, editável pelo tenant — sem bloqueio |
| DRE e BP formais | ⏳ | — | Estrutura em §5.9 — revisão contábil opcional |
| CNAB campo a campo | ✅ | §IV-CNAB.1 | De-para FEBRABAN 240 (v10.09) + banco piloto e plano de homologação; conferência 1:1 contra o PDF na homologação |
| NF-e campos obrigatórios (NT) | ⏳ | — | Portal NF-e — necessário para emissão real |
| Stack de observabilidade | ⏳ | — | Decisão de infra pendente |

---

## 16. Mapeamento de Cadastros — Responsabilidade de Front-end

> Define onde cada cadastro é gerenciado: no painel de administração interno (configuração nossa, do SaaS) ou no front-end do ERP pelo próprio tenant (Cadastro Service).

---

### 16.1 Painel de Administração Interna (SaaS — nossa responsabilidade)

Estas tabelas são mantidas por nós. O tenant não tem acesso. Mudanças impactam todos os tenants.

#### Módulo Fiscal — Tabelas de Referência Oficiais

| Tabela | Descrição | Quem atualiza | Frequência |
|---|---|---|---|
| `fiscal.ncm` | 10.520 NCMs ativos com vigência | Equipe técnica via migration | Anual (MDIC) |
| `fiscal.cst_ibs_cbs` | 18 Códigos de Situação Tributária IBS/CBS | Equipe técnica via migration | Conforme RT 2025.002 |
| `fiscal.c_class_trib` | 156 classificações tributárias granulares | Equipe técnica via migration | Conforme RT 2025.002 |
| `fiscal.c_cred_pres` | 13 códigos de crédito presumido | Equipe técnica via migration | Conforme LC 214/2025 |
| `fiscal.aliq_cbs_regime` | Alíquotas CBS por regime e ano (2026–2033) | Equipe técnica via migration | Anual / legislação |
| `fiscal.aliq_ibs_municipio` | Alíquotas IBS por município e ano | Equipe técnica via migration | Anual (CGIBS) |
| `fiscal.aliq_is_ncm` | Alíquotas IS por NCM | Equipe técnica via migration | Conforme regulamentação |
| `fiscal.regime_dif_ncm` | NCMs/NBS com regime diferenciado (Anexos I–XV) | Equipe técnica via migration | Conforme LC 214/2025 |
| `fiscal.vigencia_tributo` | Fases da transição IBS/CBS 2026–2033 | Equipe técnica via migration | Conforme legislação |

#### Módulo Financeiro — Configurações de Plataforma

| Tabela | Descrição | Quem atualiza | Frequência |
|---|---|---|---|
| `financeiro.feriado_bancario` (tipo NACIONAL) | Feriados nacionais fixos e móveis | Equipe técnica via migration | Anual |
| `financeiro.feriado_bancario` (tipo ESTADUAL) | Feriados estaduais base | Equipe técnica via migration | Sob demanda |

#### Módulo Contábil — Templates

| Tabela | Descrição | Quem atualiza | Frequência |
|---|---|---|---|
| `contabil.plano_contas_template` | Template do plano de contas para novos tenants | Equipe técnica + contador via migration | Versão nova a cada revisão |

**Telas necessárias no painel admin:**
- Listagem + upload CSV de NCM, cClassTrib, CST (sincronização com Portal NF-e)
- CRUD de feriados nacionais e estaduais
- CRUD de alíquotas CBS/IBS/IS com histórico de versões
- Gerenciamento de versões do template de plano de contas
- Visualização de `regime_dif_ncm` por anexo da LC 214/2025

---

### 16.2 Front-end do ERP — Responsabilidade do Tenant (Cadastro Service)

Estas tabelas são configuradas pelo próprio tenant no ERP. Cada tenant tem seus dados isolados.

#### Configuração Fiscal do Tenant

| Tabela | Tela no ERP | Campos principais |
|---|---|---|
| `fiscal.config_empresa` | **Configurações → Dados Fiscais** | Regime tributário, CRT, IE, IM, optante Simples, data opção |

#### Parâmetros Financeiros

| Tabela | Tela no ERP | Campos principais |
|---|---|---|
| `financeiro.parametros` | **Configurações → Parâmetros Financeiros** | Tipo de ajuste padrão (multa/mora/desconto), tolerância de conciliação, considera feriado bancário, permite baixa com data anterior |

#### Cadastros de AP/AR

| Tabela | Tela no ERP | Campos principais |
|---|---|---|
| `financeiro.forma_pagamento` | **Cadastros → Formas de Pagamento** | Código, descrição, considera dias úteis, data referência inclusiva/exclusiva |
| `financeiro.tipo_titulo` | **Cadastros → Tipos de Título** | Código, descrição, natureza (PAGAR/RECEBER/AMBOS) |
| `financeiro.tipo_ajuste` | **Cadastros → Tipos de Ajuste** | Código, descrição, categoria (MULTA/MORA/DESCONTO/ADIANTAMENTO) |
| `financeiro.tipo_baixa` | **Cadastros → Tipos de Baixa** | Código, descrição, meio de pagamento (BOLETO/PIX/CARTÃO etc.) |
| `financeiro.classificacao_financeira` | **Cadastros → Classificações Financeiras** | Código, descrição (agrupamento livre para relatórios) |
| `financeiro.motivo` | **Cadastros → Motivos** | Código, descrição, tipo (CANCELAMENTO/PRORROGAÇÃO/PARCELAMENTO) |

#### Centro de Custo

| Tabela | Tela no ERP | Campos principais |
|---|---|---|
| `financeiro.centro_custo` | **Cadastros → Centros de Custo** | Código, descrição, hierarquia (pai), aceita lançamento (analítico/sintético), ativo |
| `financeiro.centro_custo_rateio` | **Cadastros → Centros de Custo → Rateios** | Nome do rateio, percentual por CC (soma = 100%) |

#### Bancário e Tesouraria

| Tabela | Tela no ERP | Campos principais |
|---|---|---|
| `financeiro.banco` | **Cadastros → Bancos** | Código FEBRABAN, nome, máscara agência/conta |
| `financeiro.conta_corrente` | **Cadastros → Contas Correntes** | Banco, agência, conta, tipo (CORRENTE/POUPANÇA/INVESTIMENTO/CAIXA), saldo inicial, data saldo inicial |
| `financeiro.cobranca_config` | **Configurações → Cobrança** | Código cedente, carteira, modalidade, nosso número inicial, instruções de cobrança, dias protesto/negativação, layout CNAB |

#### Feriados Municipais

| Tabela | Tela no ERP | Campos principais |
|---|---|---|
| `financeiro.feriado_bancario` (tipo MUNICIPAL) | **Configurações → Feriados Municipais** | Data, descrição, ibge_municipio (preenchido pelo ibge_codigo do tenant) |

#### Contabilidade

| Tabela | Tela no ERP | Campos principais |
|---|---|---|
| `cadastros.estabelecimento` | **Cadastros → Estabelecimentos/Filiais** (cadastro-service) | CNPJ completo, ordem (0001 matriz), IE, IM, endereço fiscal — matriz criada automaticamente no onboarding (spec/estabelecimentos-filiais.md) |
| `contabil.conta` | **Cadastros → Plano de Contas** | Código, descrição, tipo, natureza, hierarquia, aceita lançamento, retificadora — copiado do template na ativação, editável pelo tenant |
| `contabil.mapeamento` | **Configurações → Mapeamento Contábil** | De/para entre entidades financeiras e contas contábeis, linha DRE |
| `contabil.periodo` | **Contabilidade → Períodos** | Competência, status (ABERTO/FECHADO/BLOQUEADO) — criado automaticamente, fechado pelo contador |

#### Orçamento e Análises

| Tabela | Tela no ERP | Campos principais |
|---|---|---|
| `financeiro.orcamento_fluxo` | **Fluxo de Caixa → Orçamento** | Ano, mês, conta corrente, classificação, natureza, valor orçado |
| `financeiro.pdd_config` | **Configurações → Provisão Devedores Duvidosos** | Percentual por faixa de aging (não vencido / 1-30 / 31-60 / 61-90 / acima 90) |

---

### 16.3 Resumo Visual

```
                    ADMIN INTERNO              TENANT (Cadastro Service)
                    ─────────────              ───────────────────────────
fiscal.*            NCM, CST,                  config_empresa
                    cClassTrib,
                    cCredPres,
                    alíquotas,
                    regime_dif_ncm,
                    vigencia_tributo

financeiro.*        feriado NACIONAL           forma_pagamento
                    feriado ESTADUAL           tipo_titulo, tipo_ajuste
                                               tipo_baixa
                    plano_contas_template      classificacao_financeira
                                               motivo
                                               parametros
                                               centro_custo + rateio
                                               banco, conta_corrente
                                               cobranca_config
                                               feriado MUNICIPAL
                                               orcamento_fluxo
                                               pdd_config

contabil.*          plano_contas_template      filial
                                               conta (cópia editável)
                                               mapeamento
                                               periodo
```

---

### 16.4 Tabelas Puramente Operacionais (sem tela de cadastro)

Estas tabelas são criadas/atualizadas automaticamente pelo sistema durante a operação. Não há tela de CRUD direta — só visualização.

| Tabela | Criada por |
|---|---|
| `financeiro.titulo` | AP/AR — lançamento de título |
| `financeiro.titulo_baixa` | Operação de baixa / retorno CNAB |
| `financeiro.titulo_ajuste` | Operação de ajuste |
| `financeiro.conta_movimentacao` | Baixa de título / transferência / aplicação |
| `financeiro.extrato_bancario` | Importação OFX |
| `financeiro.boleto` | Emissão de boleto |
| `financeiro.cnab_remessa` / `cnab_retorno` | Geração / importação CNAB |
| `financeiro.dda_boleto` | Importação DDA |
| `financeiro.cheque` | Registro de cheque |
| `financeiro.aplicacao_financeira` | Registro de aplicação |
| `financeiro.emprestimo` | Registro de empréstimo |
| `financeiro.compensacao` | Operação de compensação |
| `financeiro.adiantamento_saldo` | Lançamento de adiantamento |
| `fiscal.operacao_fiscal` | Motor fiscal — cálculo automático |
| `fiscal.apuracao_mensal` | Fechamento mensal automático |
| `contabil.lancamento` / `lancamento_partida` | Eventos financeiros (automático) + lançamento manual |
| `financeiro.audit_log` | Qualquer alteração em entidade auditada |

---


---

*Fim do documento — Spec Funcional Módulo Financeiro v12.1 (Módulos IV/V/VI reconstruídos).*
