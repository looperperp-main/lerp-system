# Migração de Dados de Onboarding — SYAX ERP

**Status:** Draft técnico
**Autor:** Vitor
**Última atualização:** 12 de outubro de 2026
**Documento relacionado:** `exportacao-dados-syax.md`; telas e UX do portal em `exportacao-importacao-front.md`
**Serviço:** `migracao-service` (novo, fora do MVP inicial — sem prazo de entrega definido). Registra no Eureka e fica atrás do gateway — reusa validação de JWT e resolução de tenant (`SecurityUtils`) dos demais serviços, não reimplementa auth do zero.

---

## 1. Objetivo

Definir o pipeline de migração de dados para onboarding de novos tenants no SYAX, cobrindo entidades mínimas do MVP (Plano de Contas, Clientes/Fornecedores, Produtos, Saldos de Abertura).

**Meta de negócio:** cliente consegue concluir a migração em até 1 semana. Essa meta é **um SLA de regime maduro**, não uma garantia desde o onboarding #1 — o pipeline precisa absorver 3–4 clientes reais antes que o template e as regras de validação estejam calibrados o suficiente para bater o prazo de forma consistente. Isso deve estar explícito para quem definiu o requirement.

**Consequência de design:** o SLA de 1 semana é o critério de desempate deste documento. Nenhum estado do pipeline pode ficar sem saída, nenhum retry pode ser infinito e nenhuma decisão de roteamento pode depender de julgamento humano em tempo real — qualquer um dos três trava um onboarding por tempo indeterminado. A seção 13 define como o SLA é medido de fato, não só declarado.

## 2. Escopo

- **Dentro do escopo:** template fixo por entidade, staging table multi-tenant, validação assíncrona, mapeamento self-service de colunas, relatório de erro granular, promoção transacional para tabelas finais.
- **Fora do escopo (v2):** motor de mapeamento genérico com IA/heurística para reconciliar schemas arbitrários, importação incremental contínua (sync), suporte a formatos não tabulares (PDF, imagem) via OCR automatizado.

## 3. Visão geral da arquitetura

O roteamento entre trilhas é **automático apenas entre Trilha 1 e Trilha 2**, por critério estrutural objetivo (as colunas batem com o template ou não). A Trilha 3 (concierge) **não é auto-detectada** — ver seção 3.1.

```mermaid
flowchart TD
    A[Cliente faz upload do arquivo] --> A2{Arquivo legível e dentro dos limites?}
    A2 -->|Não| A3[Batch FAILED - erro imediato e acionável]
    A2 -->|Sim| B{Colunas batem com o template canônico?}
    B -->|Sim| C[Parser XLSX/CSV - Trilha 1]
    B -->|Não - tabular com cabeçalho identificável| D[Tela de mapeamento self-service - Trilha 2]

    C --> F[Grava em raw_import_row - staging]
    D --> F

    X[Cliente clica: preciso de ajuda com meu arquivo] --> E[Fila concierge - Trilha 3]
    E --> G[Normalização manual - script pandas]
    G --> F

    F --> H[Validação assíncrona por linha]
    H --> I{Todas as linhas válidas?}
    I -->|Não| J[Relatório de erro por linha/campo]
    J --> K[Cliente corrige e reenvia linhas com erro]
    K --> H
    I -->|Sim| L[Cliente confirma import]
    L --> P{Pré-requisitos de entidade satisfeitos?}
    P -->|Não| P2[Erro acionável: importe X antes de Y]
    P2 --> L
    P -->|Sim| M[Job transacional: staging -> tabelas finais]
    M --> N[Tenant pronto para operar]
```

### 3.1 Decisão de roteamento entre trilhas

**Decisão:** o sistema sempre tenta primeiro a Trilha 1. Se o arquivo é tabular e tem cabeçalho identificável mas as colunas não batem com o template, cai automaticamente na Trilha 2, sem intervenção humana. A Trilha 3 é acionada **exclusivamente por ação explícita do cliente** (botão "não consigo montar meu arquivo, preciso de ajuda" na própria tela de import), nunca por classificação automática.

**Razão:** classificar automaticamente um arquivo como "bagunçado demais" exige um julgamento que nenhum critério objetivo sustenta — na prática viraria uma fila de triagem humana no meio do caminho crítico do onboarding, que é exatamente o que trava o SLA de 1 semana por tempo indeterminado. Estrutura (colunas batem / não batem) é decidível por código; "bagunça" não é. Deixar a Trilha 3 como porta de entrada separada e voluntária mantém o fluxo automático com apenas duas saídas determinísticas e dá ao cliente o controle de quando pedir ajuda — sem que o sistema decida por ele que ele precisa esperar.

### 3.2 Modelo de execução assíncrona

Validação (`VALIDATING`) e promoção (`PROMOTING`) rodam via `@Async` do Spring, não Kafka. O motivo é escopo: são tarefas internas do próprio `migracao-service`, contra o próprio banco dele, sem nenhum outro serviço consumindo o evento. Trazer um broker para decolar uma tarefa sem consumidor externo é complexidade sem comprador — o resto do projeto usa Kafka para comunicação *entre* serviços (auditoria cruzando `auth-service`), não é esse o caso aqui.

**Configuração obrigatória, não o padrão default do Spring:**
- `@Async` sem executor configurado cai no `SimpleAsyncTaskExecutor`, que cria uma thread nova por chamada sem teto — um pico de uploads derruba a VPS antes de qualquer limite de disco. `ThreadPoolTaskExecutor` com fila limitada é obrigatório.
- Exceção não capturada num método `@Async void` é engolida silenciosamente pelo `AsyncUncaughtExceptionHandler` default (só loga). Cada handler de job precisa de try/catch explícito que grava `status=FAILED` + `failure_reason` — sem isso, o batch fica preso no estado assíncrono para sempre sem ninguém saber por quê.
- **Recuperação de crash:** `@Async` não sobrevive a restart da JVM — diferente de uma mensagem Kafka não confirmada, uma task em memória simplesmente some. Ver seção 12.1 para o job de timeout que cobre isso.

**Gatilho de revisão:** esta decisão vale para instância única. Se o `migracao-service` escalar para múltiplas instâncias, `@Async` para de funcionar (job pego pela instância A que morre não é retomado por B) — mesmo gatilho de revisão já usado para a decisão de disco local vs. S3 no doc de export (seção 12.2 de lá).

## 4. Modelo de dados — staging

Toda entrada, independente da trilha de origem, converge para a mesma tabela de staging. Isso garante que a exceção (trilha concierge) não precise de caminho de código separado no restante do pipeline.

```sql
CREATE TABLE import_batch (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    entity_type           VARCHAR(50) NOT NULL,
    schema_version        VARCHAR(10) NOT NULL DEFAULT '1.0', -- versão do template canônico usada neste upload
    status                VARCHAR(20) NOT NULL DEFAULT 'UPLOADED', -- UPLOADED, VALIDATING, AWAITING_FIX, READY, PROMOTING, DONE, FAILED
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(), -- mantido pela aplicação a cada transição de status; base do timeout da seção 12.1
    source_track          VARCHAR(20) NOT NULL,   -- TEMPLATE, SELF_MAPPED, CONCIERGE
    field_mapping         JSONB,                  -- salvo por tenant para reuso em imports futuros
    raw_file_path         TEXT,                   -- arquivo ORIGINAL enviado pelo cliente, como veio: /var/syax/imports/{tenant_id}/{batch_id}/original.xlsx
    row_count             INTEGER,
    concierge_deadline_at TIMESTAMPTZ,            -- só na Trilha 3: entrada na fila + 48h
    concierge_rejections  SMALLINT NOT NULL DEFAULT 0,
    failure_reason        TEXT,                   -- preenchido quando status=FAILED
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    promoted_at           TIMESTAMPTZ,            -- início da promoção (PROMOTING)
    done_at               TIMESTAMPTZ,            -- fim da promoção com sucesso (DONE)
    purge_after           TIMESTAMPTZ,            -- ver seção 12 (retenção do staging)
    purged_at             TIMESTAMPTZ
);

CREATE INDEX idx_import_batch_tenant ON import_batch (tenant_id, entity_type, status);
```

```sql
CREATE TABLE raw_import_row (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    batch_id        UUID NOT NULL REFERENCES import_batch(id) ON DELETE CASCADE,
    entity_type     VARCHAR(50) NOT NULL,   -- 'CHART_OF_ACCOUNTS', 'CUSTOMER', 'PRODUCT', 'OPENING_BALANCE'
    row_num         INTEGER NOT NULL,
    raw_json        JSONB NOT NULL,          -- linha JÁ MAPEADA para o schema canônico (não é o conteúdo original do arquivo)
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, VALID, ERROR, PROMOTED, PURGED
    error_detail    JSONB,                   -- lista de {field, message}
    source_track    VARCHAR(20) NOT NULL,    -- TEMPLATE, SELF_MAPPED, CONCIERGE
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    validated_at    TIMESTAMPTZ,
    promoted_at     TIMESTAMPTZ,

    CONSTRAINT uq_raw_import_row UNIQUE (batch_id, entity_type, row_num)
);

CREATE INDEX idx_raw_import_batch ON raw_import_row (tenant_id, batch_id, status);
```

`field_mapping` persistido por tenant é o que torna a trilha self-service rápida em uploads subsequentes do mesmo cliente — ele não precisa remapear colunas toda vez. Os campos-alvo que o cliente associa na tela de mapeamento vêm de `canonical_schema_field` (seção 4.3), não de uma lista fixa no código.

`entity_type` em `raw_import_row` é redundante com `import_batch.entity_type` enquanto o batch for mono-entidade (ver 4.1). Fica mantido porque é o que permite a UNIQUE ser estável se um dia o batch virar multi-entidade, e porque evita join em toda query de validação. Redundância consciente, não descuido.

### 4.1 Assimetria proposital com o modelo de export

`import_batch` é **mono-entidade** (um batch = um `entity_type`); `export_batch` é **multi-entidade** (`scope.entities` é um array). A assimetria é deliberada e não deve ser "corrigida":

| | Import | Export |
|---|---|---|
| Granularidade | 1 batch = 1 entidade | 1 batch = N entidades |
| Motivo | cada entidade tem template, mapeamento, validação e ordem de promoção próprios; misturar entidades num batch tornaria o estado do batch ambíguo (metade DONE, metade AWAITING_FIX) | não há validação nem falha parcial: ou extrai tudo, ou falha; agrupar reduz N cliques do cliente a um |
| Falha parcial | esperada e endereçada por linha | não existe |

### 4.2 Decisão: sem tabela de audit log dedicada no import

**Decisão:** o import **não** ganha uma tabela `import_audit_log` espelhando a do export.

**Razão:** o que uma tabela dessas registraria — quem subiu, quando, quando promoveu, quando o dado pessoal foi descartado — já está em `import_batch` como colunas (`created_at`, `promoted_at`, `done_at`, `purged_at`, `source_track`) e sobrevive ao purge do staging, porque `import_batch` não guarda dado pessoal. O export precisa de log em tabela separada porque lá o evento relevante (tentativa de download, inclusive com token inválido) é **repetível e originado de fora**, sem lugar natural numa coluna. Aqui não é. Uma tabela a menos para manter, com a mesma rastreabilidade LGPD.

### 4.3 Schema canônico como tabela — fonte única de verdade

Até aqui, "schema canônico" era um conceito implícito — cada `entity_type` tem uma lista de campos que o parser, o validador, a tela de mapeamento self-service (Trilha 2) e o gerador de export (`exportacao-dados-syax.md`, seção 8) precisam concordar sobre, sem nenhum lugar único que declare essa lista.

```sql
CREATE TABLE canonical_schema_field (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type     VARCHAR(50) NOT NULL,     -- mesmo vocabulário de import_batch.entity_type
    schema_version  VARCHAR(10) NOT NULL,     -- mesmo campo que já existe em import_batch/export_batch
    field_name      VARCHAR(100) NOT NULL,    -- chave usada em raw_import_row.raw_json e no export "fields"
    field_order     SMALLINT NOT NULL,        -- ordem no template baixado pelo cliente
    data_type       VARCHAR(20) NOT NULL,     -- STRING, DECIMAL, DATE, CNPJ, CPF...
    required        BOOLEAN NOT NULL DEFAULT false,
    validation_rule VARCHAR(50),              -- identificador de estratégia (ex: 'CNPJ_DV', 'FK_CHART_OF_ACCOUNTS'), não regex livre
    help_text       TEXT,                     -- rótulo mostrado na Trilha 2
    is_active       BOOLEAN NOT NULL DEFAULT true, -- só versão ativa aparece em novo upload/export

    CONSTRAINT uq_canonical_field UNIQUE (entity_type, schema_version, field_name)
);

CREATE INDEX idx_canonical_field_lookup ON canonical_schema_field (entity_type, schema_version, is_active);
```

**O que isso muda:**
- `raw_import_row.raw_json` deixa de ter chaves definidas por convenção implícita no parser — as chaves são exatamente `field_name` desta tabela para o `entity_type`+`schema_version` daquele batch.
- A tela de mapeamento self-service (seção 7) lista os campos-alvo consultando esta tabela, não uma lista fixa no front.
- O `fields` do metadata de export (`exportacao-dados-syax.md`, seção 8) passa a ser gerado por `SELECT field_name FROM canonical_schema_field WHERE entity_type=? AND schema_version=? AND is_active ORDER BY field_order`, não digitado à mão.
- Evolução de schema (`schema_version` 1.0 → 2.0) vira um INSERT com `is_active=true` na nova versão e `false` na antiga — sem deploy de código.

**O que essa tabela NÃO elimina:** `validation_rule` é um identificador de estratégia, não regex livre em coluna — ainda existe um pequeno mapa `Map<String, Validator>` no código Java, porque validação de FK cross-batch (ex: `FK_CHART_OF_ACCOUNTS`, usada na seção 11) não dá para expressar só em SQL. A tabela decide *quais* regras se aplicam a cada campo; o código decide *como* validar cada regra.

**Seed inicial:** changeset de dado (não só DDL) populando os 4 `entity_type` do MVP, no mesmo molde dos changesets `fiscal-schema-0XX` que já povoam NCM/CFOP/regime no `fiscal-service` — é o mesmo movimento: tirar conteúdo de domínio do código Java e colocar no banco.

## 5. Estados do batch

```mermaid
stateDiagram-v2
    [*] --> UPLOADED
    UPLOADED --> VALIDATING
    UPLOADED --> FAILED: arquivo ilegível, corrompido, vazio ou acima do limite
    VALIDATING --> AWAITING_FIX: existem linhas ERROR
    VALIDATING --> FAILED: arquivo não pôde ser parseado
    VALIDATING --> READY: todas linhas VALID
    AWAITING_FIX --> VALIDATING: cliente reenvia correções
    READY --> PROMOTING: cliente confirma
    READY --> AWAITING_FIX: pré-requisito de entidade não satisfeito
    PROMOTING --> DONE
    PROMOTING --> FAILED: erro transacional
    FAILED --> READY: retry manual (exige correção do arquivo ou do mapeamento)
    DONE --> [*]
    FAILED --> [*]: cliente abandona o batch
```

`FAILED` é terminal por si só — nunca há retry automático em loop. O caminho de volta para `READY` depende de ação explícita do cliente (novo arquivo, novo mapeamento), e `failure_reason` sempre carrega a mensagem que diz o que fazer.

## 6. Trilha 1 — Caminho feliz (template padrão)

Maioria dos clientes deve cair aqui. Validação nativa do Excel (data validation, dropdown, máscara de CNPJ/CPF) elimina boa parte dos erros antes mesmo do upload.

```mermaid
sequenceDiagram
    participant C as Cliente
    participant UI as SYAX UI
    participant API as Import Service
    participant D as Disco local (VPS)
    participant DB as Staging DB

    C->>UI: Upload template.xlsx
    UI->>API: POST /imports (entity_type, file)
    API->>D: Grava arquivo ORIGINAL em /var/syax/imports/{tenant_id}/{batch_id}/
    API->>DB: INSERT import_batch (status=UPLOADED, raw_file_path, purge_after)
    API->>DB: INSERT raw_import_row (status=PENDING) x N linhas
    API-->>UI: batch_id
    API->>API: Validação assíncrona (job)
    API->>DB: UPDATE status por linha
    UI->>API: GET /imports/{batch_id}/status
    API-->>UI: resumo + lista de erros (se houver)
    C->>UI: Confirma import
    UI->>API: POST /imports/{batch_id}/promote
    API->>API: Checa pré-requisitos de entidade (seção 11)
    API->>DB: Transação: staging -> tabelas finais
    API-->>UI: DONE
```

O parser deve **ignorar o bloco de metadata** de arquivos gerados pelo próprio SYAX: no XLSX, a aba `_syax_meta`; no CSV, o arquivo irmão `*.meta.json`, que nunca vem embutido no corpo do CSV. Ver `exportacao-dados-syax.md`, seção 8 — é o que garante que um export do SYAX seja reimportável sem edição manual.

## 7. Trilha 2 — Mapeamento self-service

Cobre o caso mais comum de "dado desorganizado": não é bagunça de fato, é estrutura diferente da esperada (colunas em outra ordem, nomes diferentes, planilha própria do cliente). Resolvido pelo próprio cliente, sem intervenção manual.

```mermaid
flowchart TD
    A[Colunas não bateram com o template - queda automática da Trilha 1] --> B[Sistema lê cabeçalhos do arquivo]
    B --> C[Tela: cliente associa cada coluna própria a um campo canônico]
    C --> D{Mapeamento já existe para este tenant?}
    D -->|Sim| E[Pré-preenche com mapeamento salvo]
    D -->|Não| F[Cliente mapeia do zero]
    E --> G[Cliente confirma/ajusta mapeamento]
    F --> G
    G --> H[Salva field_mapping no import_batch]
    H --> I[Aplica mapeamento a todas as linhas]
    I --> J[Segue para staging - mesmo fluxo da Trilha 1]
```

**Critério de entrada nesta trilha:** arquivo tabular, com cabeçalho identificável, mas colunas não batem com o template. Não requer normalização de conteúdo — só de estrutura. A entrada é **automática**: o cliente não escolhe a trilha, ele cai nela ao subir um arquivo cujo cabeçalho o parser não reconheceu. A lista de campos-alvo mostrada na tela de mapeamento vem de `canonical_schema_field` (seção 4.3).

## 8. Trilha 3 — Exceção / concierge

Reservada para dado de fato não estruturado (PDF escaneado, planilha sem cabeçalho, múltiplos formatos misturados). Tratada como serviço com SLA interno definido, não como suporte aberto — isso é o que impede a exceção de virar gargalo de escala.

**Ponto de entrada:** botão explícito na tela de import ("não consigo montar meu arquivo, preciso de ajuda"). O sistema **nunca** roteia um cliente para cá automaticamente — ver 3.1.

```mermaid
flowchart TD
    A[Cliente clica: preciso de ajuda com meu arquivo] --> B[Entra na fila interna - concierge_deadline_at = now + 48h]
    B --> C[Script pandas ad-hoc normaliza para schema canônico]
    C --> D[Output gera raw_import_row com source_track=CONCIERGE]
    D --> E[Cliente recebe preview para conferência]
    E --> F{Cliente aprova?}
    F -->|Não| R{concierge_rejections < 3?}
    R -->|Sim| C
    R -->|Não| S[Escalation: revisão humana sênior - sessão direta com o cliente]
    S --> C
    F -->|Sim| G[Segue para validação - mesmo fluxo das outras trilhas]
```

**Mecanismo do SLA de 24–48h:** `concierge_deadline_at` é calculado na entrada da fila (`created_at + 48h`). Um job diário varre `import_batch WHERE source_track='CONCIERGE' AND done_at IS NULL AND concierge_deadline_at < now()` e dispara alerta interno (mesmo canal do alerta de disco, seção 12 do doc de export). Sem alerta, "SLA de 24–48h" é só uma frase num documento.

**Teto de iterações:** após 3 rejeições do preview pelo cliente (`concierge_rejections >= 3`), o loop para de repetir e escala para revisão humana sênior. Loop de aprovação sem teto é a forma mais fácil de um onboarding ficar preso indefinidamente sem que ninguém perceba — não há estado de erro, só idas e voltas.

**Importante:** o script de normalização é descartável por cliente — não é reaproveitado como feature de produto. Se o volume desta trilha crescer a ponto de virar gargalo recorrente, isso é sinal para investir em v2 (heurística/LLM para reconciliação automática), não para expandir o time de concierge.

## 9. Contrato do relatório de erro

Erro deve ser acionável por linha e campo — nunca um erro genérico de "arquivo inválido".

```json
{
  "batch_id": "b3f1...",
  "total_rows": 512,
  "valid_rows": 498,
  "error_rows": 14,
  "errors": [
    {
      "row_num": 47,
      "field": "cnpj",
      "message": "Dígito verificador inválido",
      "raw_value": "12.345.678/0001-00"
    },
    {
      "row_num": 103,
      "field": "conta_contabil",
      "message": "Conta não existe no plano de contas já importado",
      "raw_value": "3.1.02.999"
    }
  ]
}
```

`raw_value` vem da linha **já mapeada** (`raw_import_row.raw_json`). Quando o cliente contesta o valor apontado — típico na Trilha 2, onde a suspeita é de mapeamento errado e não de dado errado —, o suporte cruza com o arquivo original em `import_batch.raw_file_path`, que é justamente por que ele é persistido.

### 9.1 Semântica de reenvio

| Modo | O que o cliente envia | Comportamento |
|---|---|---|
| Incremental por linha | só as linhas com erro, identificadas por `row_num` | UPDATE (upsert) da linha existente em `raw_import_row`, chave `(batch_id, entity_type, row_num)`; revalida só as linhas tocadas |
| Reupload completo | arquivo inteiro corrigido | merge por **chave natural** da entidade (abaixo); linhas ausentes no novo arquivo permanecem como estavam |

**Chave natural por entidade:**

| Entidade | Chave natural |
|---|---|
| `CUSTOMER` / fornecedor | CNPJ (ou CPF, para PF) |
| `PRODUCT` | código do produto (SKU) |
| `CHART_OF_ACCOUNTS` | código da conta contábil |
| `OPENING_BALANCE` | (código da conta, data de referência) |

**Reaproveitamento de mapeamento:** o reenvio incremental reusa o `field_mapping` já salvo no `import_batch` — o cliente não refaz o mapeamento nem reenvia o arquivo inteiro. A única exceção é quando o próprio mapeamento é o que está errado: aí ele volta para a tela da Trilha 2, e um novo arquivo completo é exigido, porque todas as linhas precisam ser remapeadas.

## 10. Limites de upload

| Limite | Valor | Motivo |
|---|---|---|
| Tamanho do arquivo | 50 MB | alinhado ao `client_max_body_size 50M` do Nginx (ver doc de export, seção 12.1); acima disso o gateway corta antes da aplicação ver o request |
| Linhas por arquivo | 50.000 | teto de parse/validação síncrona sem estourar timeout de gateway |
| Formatos aceitos | `.xlsx`, `.csv` | qualquer outro cai em `FAILED` imediato com mensagem explícita |

Acima do teto de linhas, a UI orienta a dividir em múltiplos uploads da mesma entidade (o merge por chave natural da seção 9.1 é o que torna isso seguro). A validação de limite acontece **antes** do parse, na borda, e o erro é imediato — um timeout de gateway no primeiro upload é a pior primeira impressão possível do produto e come dias do SLA em investigação de suporte.

## 11. Dependência de ordem entre entidades

Entidades do MVP não são independentes: saldo de abertura referencia conta contábil, e lançamento referencia cliente/produto. Sem checagem de pré-requisito, importar na ordem errada produz 100% das linhas em erro genérico de FK — o cliente não descobre que o problema é ordem, e sim que "nada funciona".

```
CHART_OF_ACCOUNTS  ──┐
                     ├──> OPENING_BALANCE
CUSTOMER / PRODUCT ──┘
```

**Regra de promoção:** `POST /imports/{batch_id}/promote` valida os pré-requisitos **antes** de abrir a transação:

| Entidade sendo promovida | Pré-requisito |
|---|---|
| `CHART_OF_ACCOUNTS` | nenhum |
| `CUSTOMER` | nenhum |
| `PRODUCT` | nenhum |
| `OPENING_BALANCE` | existe `import_batch` do mesmo tenant com `entity_type='CHART_OF_ACCOUNTS'` e `status='DONE'` |

Se o pré-requisito não está satisfeito, o batch volta para `AWAITING_FIX` com mensagem acionável, não com 500 linhas de erro de FK:

> "Plano de Contas precisa ser importado e confirmado antes de Saldos de Abertura. Importe o Plano de Contas primeiro e confirme; depois volte aqui."

A mesma regra vale para qualquer entidade que referencie outra por chave natural: a validação por linha aponta o registro faltante (`"Conta não existe no plano de contas já importado"`), mas o **bloqueio de pré-requisito no nível do batch** é o que evita que o cliente descubra isso linha a linha.

## 12. Retenção e descarte do staging

`raw_import_row` e o arquivo original guardam CPF/CNPJ, endereço e contato de **terceiros** (os clientes do cliente). Manter isso indefinidamente num diretório e numa staging table é o pior passivo do pipeline — não há razão operacional para reter dado de staging depois que ele foi promovido.

**Política:**

| Situação do batch | Retenção do staging (`raw_import_row` + `raw_file_path`) |
|---|---|
| `DONE` | 30 dias após `done_at` — janela para o cliente conferir o import e o suporte cruzar com o original |
| `FAILED` / abandonado | 30 dias após `created_at` |
| `import_batch` (metadados, sem dado pessoal) | mantido indefinidamente — é a base da métrica de SLA (seção 13) e o registro de rastreabilidade |

`purge_after` é gravado no INSERT do batch e é configurável (`syax.import.retention-days`, default 30). O job é simétrico ao de limpeza de export (doc de export, seção 12.1):

```java
@Scheduled(cron = "0 30 3 * * *") // 3h30, depois do cleanup de export
public void purgeExpiredImportStaging() {
    List<ImportBatch> toPurge = importBatchRepo
            .findByPurgeAfterBeforeAndPurgedAtIsNull(Instant.now());
    for (ImportBatch batch : toPurge) {
        try {
            if (batch.rawFilePath() != null) {
                Files.deleteIfExists(Path.of(batch.rawFilePath()));
                Files.deleteIfExists(Path.of(batch.rawFilePath()).getParent()); // remove {batch_id}/
            }
            rawImportRowRepo.purgeByBatchId(batch.id()); // DELETE das linhas; status PURGED se optar por manter contagem
            batch.setPurgedAt(Instant.now());
            importBatchRepo.save(batch);
        } catch (IOException e) {
            log.warn("Falha ao purgar staging do batch {}: {}", batch.id(), e.getMessage());
            // segue para o próximo batch — uma falha de I/O não pode abortar a varredura inteira
        }
    }
}
```

O diretório `/var/syax/imports/{tenant_id}/{batch_id}/` é criado no upload e removido pelo purge — sem isso ele só cresce, e disco cheio na VPS derruba a aplicação inteira, não só o import.

### 12.1 Recuperação de falha e timeout do processamento assíncrono

Simétrico ao job de timeout do export (`exportacao-dados-syax.md`, seção 6.1) — sem isso, um crash da JVM no meio de um `@Async` (seção 3.2) deixa o batch preso em `VALIDATING` ou `PROMOTING` para sempre, sem erro nem retry.

| Estado | Timeout | Ação |
|---|---|---|
| `VALIDATING` | 15 minutos | volta para `FAILED` com `failure_reason='Timeout na validação'` |
| `PROMOTING` | 5 minutos | volta para `FAILED` com `failure_reason='Timeout na promoção'` — a transação já está sob controle do banco, então o pior caso é rollback automático, não dado inconsistente |

Mesmo job diário (`0 30 3 * * *`) que faz a purga do staging (seção 12) varre `import_batch WHERE status IN ('VALIDATING','PROMOTING') AND updated_at < now() - interval` (usando o timeout da tabela acima) e aplica os timeouts. `FAILED` por timeout segue a mesma regra da seção 5: volta para `READY` só por ação explícita do cliente, nunca retry automático em loop.

## 13. Medição do SLA de 1 semana

O SLA é sobre o **onboarding completo** do tenant (todos os batches obrigatórios promovidos), não sobre um batch isolado. Como `import_batch` é mono-entidade, um onboarding é sempre N batches.

**Decisão:** não existe tabela `onboarding_tracking`. O onboarding é derivado por agregação sobre `import_batch`.

**Razão:** uma tabela de tracking seria um segundo lugar guardando um fato que já está em `import_batch` — com o custo permanente de mantê-la em sincronia com as transições de status, e o risco clássico de ela divergir e a métrica mentir. A agregação é uma query; ela não pode ficar dessincronizada porque não guarda nada. Se um dia o onboarding passar a ter etapas que não são import (configuração fiscal, usuários, integrações), aí sim vale a tabela — mas isso é outro escopo.

**Heurística de agrupamento:** onboarding de um tenant = do primeiro `import_batch.created_at` do tenant até o `done_at` do último batch **obrigatório**. Entidades obrigatórias do MVP: `CHART_OF_ACCOUNTS`, `CUSTOMER`, `PRODUCT`, `OPENING_BALANCE`. O onboarding só conta como concluído quando as quatro têm um batch `DONE`.

```sql
-- Tempo de onboarding por tenant; NULL em done significa onboarding ainda aberto
SELECT tenant_id,
       MIN(created_at) AS started_at,
       CASE WHEN COUNT(DISTINCT entity_type) FILTER (WHERE status = 'DONE') = 4
            THEN MAX(done_at) END AS finished_at,
       CASE WHEN COUNT(DISTINCT entity_type) FILTER (WHERE status = 'DONE') = 4
            THEN MAX(done_at) - MIN(created_at) END AS elapsed
FROM import_batch
WHERE entity_type IN ('CHART_OF_ACCOUNTS','CUSTOMER','PRODUCT','OPENING_BALANCE')
GROUP BY tenant_id;
```

**Alerta:** onboarding aberto há mais de 7 dias (`started_at < now() - interval '7 days'` e `finished_at IS NULL`) vira alerta interno diário. É a única forma de o SLA ser um compromisso e não uma aspiração — sem esse alerta, um onboarding travado só aparece quando o cliente reclama.

### 13.1 Faseamento do SLA

| Fase | Onboardings | Trilha dominante | SLA realista |
|---|---|---|---|
| Calibração | Clientes 1–3 | Concierge + ajuste de template | Sem garantia de prazo — foco em capturar erros e maturar o template |
| Estabilização | Clientes 4–8 | Self-service + concierge pontual | 1–2 semanas |
| Regime | Cliente 9+ | Template + self-service | 1 semana |

## 14. Considerações de multi-tenancy

- **Toda query de staging filtra obrigatoriamente por `tenant_id`** — validação, promoção, relatório de erro e purge. Não há particionamento físico (`PARTITION BY`) do Postgres envolvido; é regra de acesso, e nenhuma query pode cruzar tenants.
- `field_mapping` é por tenant — mapeamento de um cliente nunca deve ser sugerido como default para outro (dados de clientes diferentes têm semânticas de coluna diferentes mesmo com nomes parecidos).
- Promoção para tabelas finais deve ser transacional por tenant, permitindo rollback de um batch sem afetar outros tenants em import simultâneo.
- Diretório de upload é isolado por tenant (`/var/syax/imports/{tenant_id}/{batch_id}/`), sob o mesmo usuário de serviço `syax` descrito no doc de export.

## 15. Próximos passos (v2, fora do escopo atual)

- Heurística ou LLM-assisted para sugerir `field_mapping` automaticamente com base em nomes de coluna e amostra de dados, reduzindo fricção da Trilha 2.
- OCR + extração estruturada para admitir PDFs na Trilha 3 sem intervenção manual completa.
- Import incremental/sync contínuo, para clientes que querem manter SYAX espelhado a um ERP legado durante período de transição.
- `entity_type=GL_ENTRY` (lançamentos/transações), hoje exportável mas não importável — ver doc de export, seção 8.

## 16. Desvios da implementação (fatia 4 — Trilha 1: upload, staging e validação)

Escrito em 12/10/2026; `verify` e Liquibase (007–009) rodados e verdes pelo usuário no mesmo dia. Cobre `CUSTOMER` e `PRODUCT` (as únicas com campos seedados); a promoção para as tabelas finais está no §17.

| Ponto | Spec | Implementado | Por quê |
|---|---|---|---|
| `tenant_id` | UUID | `BIGINT` | o tenant do SYAX é `Long` (igual ao export) |
| `import_batch` | sem autor | + `requested_by` (UUID) e `original_file_name` | rastreabilidade LGPD sem `import_audit_log` (§4.2) e rótulo na tela |
| `raw_file_path` | caminho completo | relativo ao `syax.import.base-dir` (`{tenant}/{batch}/original.{ext}`) | mesmo critério do export: só o valor de `SYAX_IMPORT_DIR` muda entre local e OCI |
| `canonical_schema_field` | sem tamanho | + `max_length`; regras `ENUM_TIPO_PRODUTO`, `ENUM_IND_IE_DEST`, `EAN_DIGITOS` | sem isso o erro só apareceria na promoção, como falha de INSERT |
| `error_detail` | `{field, message}` | igual; o relatório da API usa camelCase PT (`linha`, `campo`, `mensagem`, `valorInformado`) | molde do export |
| Colunas fora do modelo (§7) | cai na Trilha 2 | **cai na Trilha 2** (batch `AWAITING_MAPPING`, ver §18); colunas a mais, quando as obrigatórias batem, são ignoradas e devolvidas em `colunasIgnoradas` | *(substitui o FAILED + 422 da fatia 4, que valia enquanto a Trilha 2 não existia)* |
| Número da linha | `row_num` | a linha **como a planilha mostra** (cabeçalho = 1) | o cliente acha o erro sem contar |
| Chave natural repetida no arquivo | só no reupload (§9.1) | já é erro na validação (2ª ocorrência, citando a linha da 1ª) | senão a promoção gravaria duas vezes o mesmo registro |
| Erro estrutural (ilegível, sem dados, >50.000 linhas) | `FAILED` imediato | batch gravado como `FAILED` com `failure_reason` acionável **e** resposta 422 | o cliente vê o motivo na hora e o SLA (§13) enxerga a tentativa |

**API (`MIGRACAO_IMPORTAR`):** `GET /migracao/imports/modelo/{entidade}?formato=xlsx|csv`, `POST /migracao/imports` (multipart `entidade` + `arquivo`, 202), `GET /migracao/imports`, `GET /migracao/imports/{id}`, `GET /migracao/imports/{id}/erros?pagina&tamanho`. O modelo (XLSX com listas suspensas e aba de instruções, ou CSV com `;` e BOM) é gerado de `canonical_schema_field`, a mesma fonte do parser e do validador.

**Ainda não feito (próximas fatias):** reenvio incremental/reupload com merge (§9.1). (Promoção, timeout e purga: ver §17. Trilha 2: ver §18. Trilha 3, SLA e telas: ver §19. Download de posse: ver `exportacao-dados-syax.md`.)

## 17. Desvios da implementação (fatia 5 — promoção, timeout e purga)

Escrito em 12/10/2026, **não testado** até o usuário rodar `verify` e o Liquibase (changeset 010). Cobre `CUSTOMER` e `PRODUCT`.

| Ponto | Spec | Implementado | Por quê |
|---|---|---|---|
| Gravação no destino | promoção "para as tabelas finais" | **SQL direto** em `cadastros.*` pelo `migracao-service`, na transação do batch (`CadastroDestinoRepository`), replicando `PessoaService.create` + `EstabelecimentoService.criarMatriz` | a spec exige tudo-ou-nada por batch (§11); chamar o `cadastro-service` linha a linha (50.000 chamadas) não é transacional. **Risco:** se as regras do cadastro mudarem, o repositório muda junto; nenhum evento de auditoria Kafka é emitido (a rastreabilidade é o próprio `import_batch`, §4.2) |
| Registro que já existe | não definido | **mantém o existente, nunca sobrescreve**; a linha vira `SKIPPED` com o motivo em `error_detail`; o batch vai a `DONE` | sobrescrever sem aviso um cliente que já tem pedido e nota emitida é o pior erro possível; atualizar é decisão do cliente na tela de cadastro |
| `raw_import_row.status` | PENDING/VALID/ERROR/PROMOTED/PURGED | + `SKIPPED` (changeset `migracao-010`) | ver linha acima |
| Chave de CUSTOMER | CNPJ ou CPF | PF por documento; PJ pela **raiz do CNPJ** (8 primeiros), como o `uq_pessoa_tenant_cnpj_raiz`: matriz e filial são a mesma pessoa. Filial de empresa já cadastrada, ou 2ª linha com a mesma raiz no arquivo, vira `SKIPPED` | sem isso a 2ª linha violaria a unique e derrubaria o batch inteiro |
| Pessoa que já existe sem ser cliente (ex.: é fornecedor) | não definido | só ganha o vínculo `cliente`; os dados da pessoa não são tocados | `cliente` aponta para `pessoa` (uma pessoa, vários papéis) |
| Documento gravado | — | **com máscara** (`999.999.999-99`, `99.999.999/9999-99`), igual ao front | a dedup do cadastro compara o texto; sem máscara, o cadastro duplicaria o que foi importado |
| Estabelecimento matriz (PJ) | — | criado com `ordem 0001`, `proprio=false`, `crt` no default da coluna (`REGIME_NORMAL`) | igual ao `PessoaService`; o regime real é revisto no onboarding |
| Pré-requisito de ordem (§11) | bloqueia `OPENING_BALANCE` sem plano de contas | **não implementado**: `CUSTOMER` e `PRODUCT` não têm pré-requisito | o ponto de extensão é `ImportService.promover`, antes de `iniciarPromocao` |
| `DONE` | gravado ao fim da promoção | gravado **na mesma transação** do destino | se o commit passar e o UPDATE falhar, o batch ficaria preso em `PROMOTING` com os dados já gravados e o timeout o marcaria `FAILED` por engano |
| Timeout `VALIDATING`/`PROMOTING` (§12.1) | no job diário das 3h30 | job **a cada 60 s** (`syax.import.intervalo-recuperacao-ms`); purga continua às 3h30 (`syax.import.cron-purga`) | esperar até 24 h por um batch preso não é aceitável para o cliente |
| Purga de batch não confirmado | não definido | o batch vira `FAILED` (“prazo de retenção terminou”) em vez de ficar `READY` sem linhas | `READY` sem linhas promoveria zero registros e chegaria a `DONE` |
| Contagem do relatório | `valid_rows` | `linhasValidas` = VALID + PROMOTED + SKIPPED; novos `linhasPromovidas` e `linhasIgnoradas` | o total de válidas não pode encolher depois da promoção |

**API (`MIGRACAO_IMPORTAR`):** `POST /migracao/imports/{id}/promover` → 202 com o batch em `PROMOTING`; o cliente consulta `GET /migracao/imports/{id}` até `DONE` ou `FAILED`. Batch que não está `READY` (com erros, já em andamento, já concluído) → 409 com “Esta importação não pode ser confirmada agora…”; id de outro tenant ou inexistente → 404.

**Gaps conhecidos:** a lista das linhas `SKIPPED` ainda não tem endpoint (só a contagem em `linhasIgnoradas`; o motivo fica gravado em `error_detail`); o `migracao-service` precisa de permissão de escrita nas tabelas de `cadastros` no usuário do banco (não verificado); tenants com centenas de milhares de pessoas carregam o cadastro em memória na promoção (comentário `ponytail` no código, trocar por consulta por lote se acontecer); não há lock distribuído nos jobs (os UPDATEs condicionais bastam para duas instâncias).

## 18. Desvios da implementação (fatia 7 — Trilha 2, mapeamento self-service)

Escrito em 12/10/2026, **não testado** até o usuário rodar `verify` e o Liquibase (changeset 012). Cobre `CUSTOMER` e `PRODUCT`.

| Ponto | Spec | Implementado | Por quê |
|---|---|---|---|
| Estados (§5) | sem espera por mapeamento | + `AWAITING_MAPPING` (`UPLOADED → AWAITING_MAPPING → UPLOADED → VALIDATING…`); `source_track` vira `SELF_MAPPED` na queda | o arquivo precisa ficar guardado enquanto o cliente decide; sem estado próprio o batch ficaria `UPLOADED` e o job de timeout/painel não distinguiria |
| Entrada na Trilha 2 | colunas não batem com o template | falta **qualquer coluna obrigatória** do modelo no cabeçalho; o upload responde 202 com `status=AWAITING_MAPPING` (não mais 422) | critério estrutural e objetivo (§3.1); colunas a mais com as obrigatórias presentes seguem na Trilha 1 |
| Staging | gravado no upload | **não** é gravado na queda: nada vai a `raw_import_row` até o mapeamento ser confirmado; o `original.{ext}` fica em disco | o cabeçalho é a 1ª linha, então nada foi gravado quando a divergência aparece |
| Colunas na tela | parser lê o arquivo | relê **só o cabeçalho** do arquivo em disco a cada consulta (para na 1ª linha não vazia); cabeçalhos repetidos valem uma vez (vale a 1ª coluna) | 50 MB não se percorre para listar colunas; a tela não persiste lista nenhuma |
| `field_mapping` | JSONB no batch | JSONB `campo → nome exato da coluna do arquivo`, gravado ao confirmar; vazio/em branco = campo sem coluna (fica fora) | reuso no próximo import do mesmo tenant |
| Sugestão (§7, §14) | pré-preenche com o mapeamento salvo | 1º o **último** `field_mapping` do mesmo tenant+entidade (só colunas que existem neste arquivo), 2º coluna com o mesmo nome do campo (normalizado); uma coluna sugerida a no máximo um campo | nunca sugere mapeamento de outro tenant; **não** é heurística por similaridade (continua v2, §15) |
| Casamento de nomes | — | o cliente pode digitar a coluna sem acento/caixa; a API normaliza e grava o nome exato do arquivo | tolerância igual à da Trilha 1 |
| Validação do pedido | — | 400 acionável: campo desconhecido; coluna que não existe no arquivo; coluna usada em dois campos; campos obrigatórios sem coluna. O batch **continua** `AWAITING_MAPPING` | erro do pedido não condena o arquivo |
| Confirmação duplicada | — | `UPDATE … WHERE status='AWAITING_MAPPING'` é a guarda: 2º clique/2ª aba → 409 sem recarregar o arquivo | mesmo padrão de `iniciarPromocao` |
| Falha ao carregar o arquivo mapeado | — | mesma regra da Trilha 1: arquivo sem dados / >50.000 linhas / ilegível → batch `FAILED` + 422; o cliente reenvia o arquivo (§9.1: mapeamento errado exige arquivo completo de novo) | — |
| Linhas | `source_track=SELF_MAPPED` | igual | — |
| Retenção | §12 | o batch em `AWAITING_MAPPING` não tem timeout próprio: vale a purga de 30 dias, que apaga o arquivo e o marca `FAILED` com a mensagem de retenção | evita mais um job; custo é um arquivo ocioso em disco por até 30 dias |

**API (`MIGRACAO_IMPORTAR`):** `GET /migracao/imports/{id}/mapeamento` → `{id, entidade, colunasArquivo[], campos[{campo, rotulo, tipo, obrigatorio}], sugestao{campo:coluna}}` (409 se o batch não está `AWAITING_MAPPING`; 404 se de outro tenant); `POST /migracao/imports/{id}/mapeamento` com `{"mapeamento": {"documento": "CPF", ...}}` → 202 com o batch (já com o staging gravado; a validação segue em segundo plano, como na Trilha 1).

**Gaps conhecidos:** sem tela (front da Trilha 2 entra na fatia 8); botão "preciso de ajuda" (Trilha 3) segue fora; `LeitorCabecalho` interrompe o leitor de XLSX por exceção sem stack — **premissa não verificada** (o SAX do JDK/POI precisa deixar a `RuntimeException` passar; o teste `leitorDeCabecalhoParaNaPrimeiraLinhaNaoVaziaNoCsvENoXlsx` é quem confirma); arquivo cuja 1ª linha não vazia é um título (e não o cabeçalho) cai na Trilha 2 com colunas sem sentido — o cliente corrige apagando a linha e reenviando, ou pede ajuda (Trilha 3).

## 19. Desvios da implementação (fatia 8 — Trilha 3, painel de SLA e telas)

Escrita em 12 de outubro de 2026, **não testada** até o usuário rodar `verify`, o Liquibase `migracao-013` e as telas.

| Ponto | Spec | Implementação | Motivo |
|---|---|---|---|
| Estado da fila | §5 não prevê | `AWAITING_CONCIERGE` (CHECK ampliado em `migracao-013`) + índice parcial `idx_import_batch_concierge` | o pedido precisa de um estado próprio; reaproveitar `UPLOADED` confundiria a validação e o timeout |
| Entrada | botão "preciso de ajuda" | `POST /migracao/imports/concierge` (multipart, `arquivo` **opcional**): cria batch `CONCIERGE`, `concierge_deadline_at = now() + 48h` | o cliente pode não ter planilha (PDF/papel); formatos aceitos: xlsx, xls, csv, ods, pdf, txt, zip (o concierge normaliza à mão) |
| Prévia | script pandas descartável gera `raw_import_row` | o suporte sobe um xlsx/csv **no modelo do SYAX** em `POST /migracao/admin/imports/{id}/previa`; a carga é a mesma da Trilha 1 (`ImportService.carregar`), com `source_track=CONCIERGE`, e segue para a validação normal (READY / AWAITING_FIX) | evita um caminho de código separado (§4); o script de normalização continua fora do produto |
| Aprovar | cliente aprova o preview | aprovar = **confirmar a importação** (`POST /{id}/promover`), igual às outras trilhas | uma única porta de entrada para gravar no cadastro |
| Recusar | `concierge_rejections++`, volta ao concierge | `POST /{id}/concierge/recusar` (só em READY/AWAITING_FIX da Trilha 3): apaga o staging, conta a recusa, renova o prazo (+48h) e volta a `AWAITING_CONCIERGE` | prazo renovado: cada rodada tem o seu SLA |
| Teto de 3 recusas | escala para revisão sênior | `recusasConcierge >= 3` ⇒ `escalado=true` (selo na fila do admin e contador no painel). **Não bloqueia** nem automatiza nada: a "sessão direta" é humana | a spec não define mais que o sinal |
| Prévia com arquivo ruim | — | colunas fora do modelo / arquivo ilegível ao subir a prévia **devolvem o pedido à fila** (`reabrirFila`) e respondem 422 ao suporte; nunca viram `FAILED` do cliente | erro do concierge não pode derrubar o pedido do cliente |
| Alerta de SLA | job diário varre `concierge_deadline_at < now()` | `@Scheduled` diário `0 0 8 * * *` (`syax.import.cron-sla`): `log.error` por pedido vencido e por onboarding aberto há mais de 7 dias | o canal de alerta de disco (§12 do export) não existe como componente; o `ERROR` no Loki é o gancho — **ligar um alerta do Grafana a esse log é pendência** |
| Painel de SLA | §13 | `GET /migracao/admin/imports/sla` agrega por tenant sobre `import_batch` (sem tabela de tracking, como decidido); entidades obrigatórias do MVP = CUSTOMER e PRODUCT (as outras duas dependem do financeiro-service, fatia 9) | onboarding "concluído" só quando as obrigatórias têm batch `DONE` |
| Arquivo do cliente | — | `GET /migracao/admin/imports/{id}/arquivo`, restrito a `ROLE_APP_OWNER` e **só para lote da Trilha 3** (outros tenants/trilhas: 409) | o dado é do cliente (LGPD); a exceção existe porque ele pediu a ajuda e enviou o arquivo para isso |
| Download do export pela tela | token no GET | `GET /migracao/exports/{id}/arquivo` (sessão do tenant, por id) | a API não devolve o token ao cliente logado (§5.3); sem este endpoint a tela não conseguia baixar |

**Gaps conhecidos:** reenvio incremental (§9.1) continua fora (a tela só oferece "arquivo completo corrigido"); a tela pública `/dados/posse/:token` segue pendente; nenhum controle de visibilidade do menu por authority (o portal web não tem esse mecanismo hoje); o portal admin usa mensagem inline na tela de importações (não há toast global lá).
