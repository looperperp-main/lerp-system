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

Aparece em três módulos (`titulo_ajuste`, `conta_movimentacao`, `lancamento_partida`) mas nunca foi modelado como entidade. Sem isso, os relatórios gerenciais ficam sem dimensão analítica por departamento/projeto.

```sql
financeiro.centro_custo
─────────────────────────────────────────────
id              BIGSERIAL PK
tenant_id       BIGINT NOT NULL
codigo          VARCHAR(30) NOT NULL
descricao       VARCHAR(200) NOT NULL
centro_pai_id   BIGINT REFERENCES centro_custo   -- hierarquia opcional
nivel           INT NOT NULL DEFAULT 1
aceita_rateio   BOOLEAN DEFAULT TRUE             -- só analíticos aceitam lançamento
ativo           BOOLEAN DEFAULT TRUE
created_at      TIMESTAMPTZ NOT NULL
created_by      VARCHAR(100) NOT NULL
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
rateio_id        BIGINT NOT NULL REFERENCES centro_custo_rateio
centro_custo_id  BIGINT NOT NULL REFERENCES centro_custo
percentual       NUMERIC(5,2) NOT NULL
-- Regra: SUM(percentual) por rateio_id = 100
```

**Impacto em entidades existentes:** adicionar `centro_custo_id BIGINT` (nullable) em `titulo`, `titulo_baixa`, `conta_movimentacao` e `lancamento_partida`.

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

**Ação:** criar N títulos a pagar com `origem = 'NF_ENTRADA'`, `origem_documento_id = nfe_chave`, `impostos = payload.impostos JSONB`.

---

### F4.3 NF de Saída → Título a Receber

**Tópico:** `nfe.saida.autorizada`

Mesma estrutura de F4.2, substituindo `fornecedor_*` por `cliente_*` e criando título a receber com `origem = 'NF_SAIDA'`.

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

**Idempotência:** o `event_id` (UUID) garante que um evento processado duas vezes não crie título duplicado. O consumer verifica `titulo.origem_documento_id = event.nfe_chave` antes de criar.

```java
// Verificação de idempotência antes de criar
if (tituloRepository.existsByOrigemDocumentoId(event.getNfeChave())) {
    log.warn("Evento já processado — ignorando. nfe_chave={}", event.getNfeChave());
    return;
}
```

---

## F5. Migrations da Fundação Transversal

| # | Arquivo | Conteúdo |
|---|---|---|
| `transversal-001` | `financeiro-feriados.yaml` | `feriado_bancario` + seed feriados nacionais |
| `transversal-002` | `financeiro-audit-log.yaml` | `audit_log` |
| `transversal-003` | `financeiro-centro-custo.yaml` | `centro_custo`, `centro_custo_rateio`, `centro_custo_rateio_item` |
| `transversal-004` | `financeiro-addcolumn-centro-custo.yaml` | addColumn `centro_custo_id` em `titulo`, `titulo_baixa`, `conta_movimentacao` |


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
  └── ncm.monofasico = TRUE → IBS = 0, CBS = 0 (já recolhido na origem) → calcular IS se aplicável

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
meio                VARCHAR(30) NOT NULL  -- 'DINHEIRO' | 'BOLETO' | 'CREDITO_CONTA' | 'PIX' | 'CARTAO' | 'CHEQUE' | 'ANTECIPACAO' | 'COMPENSACAO'
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
terceiro_cnpj_cpf       VARCHAR(14)

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
valor               NUMERIC(15,2) NOT NULL CHECK (valor > 0)
status              VARCHAR(15) NOT NULL DEFAULT 'PLANEJADA'  -- 'PLANEJADA' | 'REAL'
conta_corrente_id   BIGINT                 -- referência à conta corrente usada
observacao          VARCHAR(500)

-- Rastreabilidade
origem              VARCHAR(20) NOT NULL   -- 'MANUAL' | 'CNAB' | 'COMPENSACAO' | 'ADIANTAMENTO'
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
- `titulo_pagar.terceiro_id` = `titulo_receber.terceiro_id` (mesmo terceiro).
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
3. Gerar UUID para `associacao_id` e atualizar todos os títulos.

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
1. Verificar `status_titulo = 'EM_ABERTO'`.
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
> de reversão no GL) fica no **roadmap** (§14) — ainda não desenhado.

**Endpoint:** `POST /api/financeiro/titulos/{id}/baixas/{baixa_id}/estorno`

**Body:** `{ "motivo": "PIX devolvido pelo banco", "data_estorno": "2026-07-02" }`

**Fluxo:**
1. Verificar `titulo_baixa.status = 'REAL'` e que ainda não foi estornada.
2. Criar novo registro `titulo_baixa` com `origem = 'ESTORNO'`, `valor` igual ao da baixa
   original com sinal de reversão, vinculado via novo campo `baixa_estornada_id`.
3. Reverter `titulo.valor_baixado` (recalcula `valor_saldo`).
4. Se o título estava `BAIXADO` → volta para `EM_ABERTO`.
5. Se a baixa gerou `conta_movimentacao`: criar movimentação inversa (`CONFIRMADO`,
   `categoria = 'LANCAMENTO'`, histórico "Estorno baixa #id"). Nunca deletar a original.
6. Registrar em `audit_log`. Baixa original ganha `estornada_at/by`.

**Campos novos em `titulo_baixa`:** `baixa_estornada_id BIGINT`, `estornada_at TIMESTAMPTZ`,
`estornada_by VARCHAR(100)`.

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
   líquido; as retenções ficam como obrigação do tenant.
3. Job mensal (`RetencaoGuiaJob`): agrupa retenções por `(tributo, codigo_receita, competencia)`
   e cria **um título a pagar por guia** (`origem = 'APURACAO_FISCAL'`, favorecido = ente
   arrecadador), vinculando `titulo_retencao.titulo_guia_id`.
4. No AR (nosso cliente reteve): registrar a retenção sofrida como baixa parcial
   `tipo_baixa.meio = 'RETENCAO'` — o caixa nunca recebe esse valor; o crédito tributário
   vai para conta de "Tributos Retidos na Fonte a Recuperar" (plano de contas 1.1.6.04).

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
2. Calcular valor líquido após desconto.
3. Criar ajuste de desconto no título.
4. Registrar operação e atualizar status para `DESCONTADO`.

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
1. Validar mesmo `terceiro_id` nos dois títulos.
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

1. Agrupar títulos `EM_ABERTO` não bloqueados por `terceiro_id`.
2. Terceiro com saldo em ambas as naturezas → criar sugestão (par de maior valor
   compensável primeiro) e notificar na tela de compensação.
3. Match por `pessoa_id` do cadastro: o mesmo CNPJ pode ter papel de cliente **e**
   fornecedor apontando para a mesma `pessoa` (party model do cadastro-service —
   dedup por `cnpj_raiz`, ver spec/estabelecimentos-filiais.md). É esse vínculo que
   garante que o netting encontra os dois lados.

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
| CO-01 | Os dois títulos devem ser do mesmo terceiro |
| CO-02 | `valor_compensado` ≤ menor saldo entre os dois títulos |
| CO-03 | Compensação parcial mantém saldo em aberto nos dois títulos |
| CO-04 | Cancelamento só é possível enquanto `status = 'PENDENTE'` |
| CO-05 | Um título pode ter no máximo uma compensação ativa por vez |

---

## II.8 Integrações Internas

### 9.1 Integração com NF de Entrada (Contas a Pagar)

Ao aprovar uma Nota Fiscal de Entrada, o módulo de Documentos de Entrada invoca:

```
POST /api/financeiro/titulos/pagar (origem = 'NF_ENTRADA')
```

O `tipo_titulo` e a `forma_pagamento` são determinados pela parametrização do `tipo_operacao` da NF (ver `parametros.tipo_operacao`).

### 9.2 Integração com NF de Saída (Contas a Receber)

Ao emitir uma Nota Fiscal de Saída, o módulo de Documentos de Saída invoca:

```
POST /api/financeiro/titulos/receber (origem = 'NF_SAIDA')
```

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

## IV.12 Checklist de Implementação

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
  AND m.data_movimentacao >= cc.data_saldo_inicial
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
2. Verificar se a baixa associada ainda pode ser revertida (não pode se já gerou obrigações contábeis).
3. Reverter `extrato_bancario.status_conciliacao = 'PENDENTE'`.
4. Reverter `conta_movimentacao.conciliado = FALSE`.
5. Se a baixa foi confirmada automaticamente pela conciliação: reverter para `PLANEJADA`.

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

> Spec completo disponível na versão anterior do documento (v10.0). As seções §16–§20 cobrem entidades, operações (boleto, CNAB, DDA, cheques, aplicações), máquinas de estado, regras de negócio e cron jobs.

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

**Resumo das entidades:** `cobranca_config`, `boleto`, `pix_cobranca`, `cnab_remessa`, `cnab_remessa_item`, `cnab_retorno`, `cnab_retorno_item`, `cheque`, `aplicacao_financeira`, `dda_boleto`

**Operações-chave:** emitir boleto (§17.1), gerar cobrança PIX (§IV-PIX), gerar remessa CNAB cobrança/pagamento (§17.4-5), importar retorno CNAB (§17.6), vincular DDA a título (§17.7)

**Cron jobs:** `BoletoVencidoJob` (diário), `AplicacaoVencidaJob` (diário), `ChequeCompensacaoJob` (diário), `PixExpiradoJob` (diário)

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

> **Decisão registrada — entrada multi-canal de NF (AP):** portal do fornecedor (iSupplier-like),
> OCR de PDF e EDI **ficam fora do escopo** desta versão; entrada de NF é via Kafka (NF-e) e manual.
> Registrado no roadmap (§14).

---

## MÓDULO V — CONTABILIDADE E GL (General Ledger)

> Spec completo disponível na versão anterior do documento (v10.0). As seções §36–§44 cobrem entidades, geração automática de lançamentos, demonstrações financeiras (BP, DRE, Razão, Livro Diário) e fechamento anual.

**Resumo das entidades:** `conta` (plano hierárquico), `periodo`, `lancamento`, `lancamento_partida`, `mapeamento`, `plano_contas_template`

### Dimensão Filial = `cadastros.estabelecimento` (não criar `contabil.filial`)

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

**`contabil.periodo`** — coluna `estabelecimento_id UUID` (null = consolidado do grupo):

| id | tenant_id | estabelecimento_id | competencia | status |
|---|---|---|---|---|
| 1 | 42 | NULL | `2025-06` | FECHADO | ← consolidado do grupo |
| 2 | 42 | `uuid-matriz-0001` | `2025-06` | FECHADO | ← Matriz fechada |
| 3 | 42 | `uuid-filial-0002` | `2025-06` | ABERTO | ← Filial ainda aberta |

**`contabil.lancamento_partida`** — coluna `estabelecimento_id UUID`. Mesmo `lancamento_id`,
mesma conta contábil, estabelecimentos diferentes → a dimensão analítica resolve
transferências e resultado por filial sem duplicar o plano de contas.

**Financeiro:** `titulo` e `conta_movimentacao` também carregam `estabelecimento_id UUID`
(bill-to/pay-from por filial — já incluído no schema do título, §2.8).

**Apuração fiscal na transição:** `fiscal.apuracao_mensal` ganha `estabelecimento_id UUID`
nullable — IBS/CBS apura consolidado por raiz de CNPJ (`estabelecimento_id = NULL`);
ICMS/ISS (até 2033) apuram **por estabelecimento**.

**Demonstrações financeiras:**
- Razão Contábil: filtrar por `estabelecimento_id` — visão por filial ou consolidada
- Balanço Patrimonial / DRE: `estabelecimento_id = NULL` gera consolidado; por estabelecimento gera individual
- Fechamento de período: pode fechar por estabelecimento individualmente antes do consolidado

---

**Demonstrações:** Razão Contábil (§5.9.1), Balanço Patrimonial (§5.9.2), DRE por Competência (§5.9.3), Fechamento Anual (§5.9.4), Livro Diário (§5.9.5), Conciliação GL vs Sub-ledgers (§5.9.6)

Plano de contas: template oficial editável pelo tenant — sem bloqueio (§F6).

---

## MÓDULO VI — ANÁLISES GERENCIAIS E RELATÓRIOS

> Spec completo disponível na versão anterior do documento (v10.0). As seções §21–§27 cobrem relatórios operacionais, KPIs, DRE gerencial, PDD, dashboard executivo.

**Relatórios:** Aging (§6.2), Inadimplência (§6.2.3), Posição de Títulos (§6.2.4)

**KPIs:** Giro de Recebíveis, PMR, PMP, Ciclo Financeiro, Taxa de Inadimplência (§6.3)

**PDD:** Provisão para Devedores Duvidosos configurável por faixa de aging (§6.3.5)

**Dashboard executivo:** posição de caixa + recebíveis + pagáveis + fluxo + alertas (§6.5)


## 11. Mapeamento dos Diagramas de Arquitetura

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
| `pessoa` | `documento` (só dígitos), `tipo` (PF/PJ) | `tipo` distingue B2C de B2B para IBS/CBS |
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
| `financeiro/v1/002-audit-log.yaml` | `createTable` | Tabela `financeiro.audit_log` com colunas: `id`, `tenant_id`, `tabela varchar(50)`, `registro_id BIGINT`, `operacao varchar(10)` (INSERT/UPDATE/DELETE), `campos_antes JSONB`, `campos_depois JSONB`, `user_id UUID` (UUID — não BIGINT, pois `user_account.id` é UUID), `user_nome varchar(100)`, `ip_origem varchar(45)`, `created_at TIMESTAMPTZ`. Índices em `(tenant_id, tabela, registro_id)`, `(tenant_id, user_id)` e `(tenant_id, created_at)`. Sem FK — referência a `user_account` garantida pela aplicação. |
| `financeiro/v1/003-centro-custo.yaml` | `createTable` | Tabelas `financeiro.centro_custo` (hierárquica com `centro_pai_id` self-reference, `aceita_rateio boolean`, `ativo boolean`) e `financeiro.centro_custo_rateio` + `financeiro.centro_custo_rateio_item` (percentual por CC, soma deve ser 100%). Índice em `(tenant_id, codigo)` unique. |
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
| `financeiro/v1/010-addcol-centro-custo-referencias.yaml` | `addColumn` | Adiciona `centro_custo_id BIGINT nullable` em `financeiro.titulo`. Executar aqui pois `titulo` agora existe. |
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
| `contabil/v1/007-plano-contas-template.yaml` | `createTable` + seed | Tabela `contabil.plano_contas_template` (global, sem `tenant_id`). Campos: `versao int`, `codigo varchar(30)`, `descricao`, `tipo`, `natureza`, `nivel`, `codigo_pai varchar(30)` (referência por código, não por id — necessário para a cópia no `TenantAtivacaoListener`), `aceita_lancamento boolean`, `retificadora boolean`, `ativo boolean`. Seed: `versao=1, ativo=true` somente após validação do contador. Até lá: `versao=0, ativo=false`. |
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
| `financeiro/v1/031-titulo-hold-estabelecimento.yaml` | 2 | addColumn em `titulo`: `bloqueado`, `motivo_bloqueio`, `estabelecimento_id UUID`; `origem_documento_id` como VARCHAR(50) |
| `financeiro/v1/032-titulo-baixa-estorno.yaml` | 2 | addColumn em `titulo_baixa`: `baixa_estornada_id`, `estornada_at`, `estornada_by` (§4.6.1) |
| `financeiro/v1/033-retencao.yaml` | 2 | `financeiro.titulo_retencao` + `financeiro.retencao_config` (§4.9) |
| `financeiro/v1/034-parametros-multa-mora.yaml` | 2 | addColumn em `parametros`: `percentual_multa`, `percentual_mora_mes`, `sugerir_multa_mora` (§4.6.2) |
| `financeiro/v1/035-approval.yaml` | 2 | `financeiro.approval_regra` + `financeiro.approval_request` (§F7) |
| `financeiro/v1/036-dunning.yaml` | 2 | `financeiro.dunning_regua` + `financeiro.dunning_evento` + seed régua default D+1/7/15/30 (§5.3.1) |
| `financeiro/v1/037-conta-mov-estabelecimento.yaml` | 3 | addColumn `estabelecimento_id UUID` em `conta_movimentacao` |
| `financeiro/v1/038-pix-cobranca.yaml` | 4 | `financeiro.pix_cobranca` (§IV-PIX) |
| `fiscal/v1/028-apuracao-estabelecimento.yaml` | 6 | addColumn `estabelecimento_id UUID` nullable em `apuracao_mensal` (ICMS/ISS por estabelecimento; IBS/CBS consolidado = NULL) |

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

Total: 50 migrations em 3 schemas novos
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
| 9 | Seed `plano_contas_template` | Gerar com `ativo=false` até validação do contador |
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

**Decisão:** eventos in-process via Spring `ApplicationEventPublisher`. Sem broker externo (RabbitMQ/Kafka) por enquanto — adicionar quando houver necessidade de comunicação entre serviços deployados separadamente.

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
| `TenantAtivadoEvent` | In-process | Auth Service (externo) | `TenantAtivacaoListener` |
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
| Reflexo contábil do estorno de baixa no GL (lançamento de reversão) | Estorno operacional especificado (§4.6.1); o desenho contábil ainda não foi pensado |
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

## 15. Maturidade do Documento

```
Modelagem de dados       ████████░░  85%
Regras de negócio        ████████░░  80%
Motor Fiscal             ██████████  100% ← todos os seeds gerados: Anexos I–XV + XVII completos
Fiscal / contábil        ████████░░  82%  ← plano de contas e demonstrações aguardam contador
Integrações técnicas     █████░░░░░  55%  ← CNAB campo a campo e NF-e ainda pendentes
Arquitetura de software  █████████░  85%

Maturidade geral         ████████░░  80%
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
| CNAB campo a campo | ⏳ | — | Estratégia: layout FEBRABAN 240 padrão primeiro, overrides por banco depois (§IV-CNAB) |
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
| `financeiro.centro_custo` | **Cadastros → Centros de Custo** | Código, descrição, hierarquia (pai), aceita rateio, ativo |
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

*Fim do documento — Spec Funcional Módulo Financeiro v16.0 completo.*

---

## 1.8 Dados Fiscais Confirmados — Fontes Oficiais

> Dados extraídos diretamente da **LC 214/2025** (atualizada até LC 227/2026) e do **Informe Técnico RT 2025.002 v1.10** (publicado em Portal NF-e, abril/2026). Não requerem validação adicional — são as fontes primárias.

---

### 1.8.1 Esclarecimento Arquitetural: CST vs. cClassTrib

O spec anterior usava apenas `cst varchar(3)`. A RT 2025.002 esclarece a distinção:

| Campo | Tamanho | Obrigatório | O que é |
|---|---|---|---|
| `CST` | 3 dígitos | Sim, a partir de jan/2026 | Código de Situação Tributária — nível macro |
| `cClassTrib` | N dígitos (os 3 primeiros = CST) | Sim, a partir de jan/2026 | Classificação granular vinculada a artigo da LC 214/2025 |

**Impacto no schema:** a tabela `fiscal.cst_ibs_cbs` representa os CSTs (macro). O `cClassTrib` completo deve ser baixado do Portal NF-e (CSV oficial) e armazenado em `fiscal.c_class_trib` separado. A `operacao_fiscal` precisa de ambos os campos.

**Simples Nacional e MEI:** durante 2026, NÃO são obrigados a informar CST e cClassTrib. Obrigatoriedade inicia em janeiro de 2027. O motor fiscal deve retornar `cst = null` e `c_class_trib = null` para essas empresas em 2026.

---

### 1.8.2 Seed Confirmado — `fiscal.cst_ibs_cbs`

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

### 1.8.3 Nova Entidade — `fiscal.c_cred_pres` (Crédito Presumido)

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

### 1.8.4 Atualização Schema — `fiscal.operacao_fiscal`

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

### 1.8.5 Regimes Diferenciados — Mapeamento Completo dos Anexos LC 214/2025

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

### 1.8.6 Seed Confirmado — Anexo I (Alíquota Zero — Alimentos Básicos)

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

### 1.8.7 Seed Confirmado — Anexo XVII (Imposto Seletivo — IS)

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

### 1.8.8 Seed Parcial — Anexos II e III (Serviços com Redução 60%)

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

### 1.8.9 Seed Confirmado — Anexo XV (Hortícolas, Frutas, Ovos — Alíquota Zero)

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

### 1.8.10 Higiene Pessoal — Anexo VIII (Redução 60%)

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

### 1.8.11 Simples Nacional — Alíquotas IBS/CBS por Faixa (2027–2033)

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

### 1.8.12 Migrations Adicionais Necessárias

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

### 1.8.13 Status dos Seeds — Pós Planilhas Oficiais

Com o processamento das três planilhas oficiais, os seeds estão prontos para uso direto no Liquibase `loadData`. Ver §15 para maturidade geral atualizada.

| Arquivo gerado | Fonte | Linhas | Status |
|---|---|---|---|
| `c_class_trib.csv` | cClassTrib_2026_04_15.xlsx | 156 | ✅ Pronto |
| `ncm_codigos.csv` | Tabela_NCM_2022_vigência_01_02_26 | 10.520 | ✅ Pronto |
| `seed_cst_ibs_cbs.sql` | cClassTrib_2026_04_15.xlsx aba CST | 18 | ✅ Pronto |
| `seed_aliq_cbs.sql` | IT_2026_002_v_1_00_Aliquotas_CBS | 5 | ✅ Pronto |
| `schema_c_class_trib.sql` | — (DDL) | — | ✅ Pronto |

**Correção importante confirmada pelas planilhas:** `c_class_trib` é `INTEGER` (ex: 1, 200028, 410004), não `VARCHAR`. O schema e o spec foram atualizados.