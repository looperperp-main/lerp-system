# Estabelecimentos / Filiais — Modelo Party + Estabelecimento (estilo TCA)
## Especificação de Mudança

**Status:** Planejado (não iniciado)
**Serviço primário:** `cadastro-service` (porta 8086) · schema `cadastros`
**Serviços impactados:** `auth-service` (onboarding do tenant), `liquibase-service` (DDL), futuros módulos NF-e / motor fiscal IBS-CBS / estoque / financeiro
**Base package:** `com.l.erp.cadastroservice`
**Data:** 2026-06-19

---

## 1. Contexto e Problema

O `cadastro-service` já implementa um **party/role model** ao estilo Oracle TCA: `pessoa`
(party) + papéis `cliente` / `fornecedor` / `transportadora` / `vendedor`, com `endereco` e
`contato` pendurados na `pessoa`. As constraints corretas existem
(`uq_pessoa_tenant_documento`, `uq_<papel>_tenant_pessoa`, índice parcial de endereço
principal).

**Limitação:** o modelo plano não representa **matriz/filial**. No Brasil, matriz e filial
compartilham a **raiz do CNPJ** (8 dígitos) e diferem na **ordem do estabelecimento**
(dígitos 9–12): matriz = `0001`, filiais = `0002`, `0003`…

### Pontos concretos de falha no schema atual

| # | Falha | Consequência |
|---|---|---|
| 1 | `pessoa.documento` guarda o CNPJ completo (14) e `uq_pessoa_tenant_documento` é único sobre ele | Matriz e filial viram duas `pessoa` sem vínculo |
| 2 | Sem `matriz_id` / `cnpj_raiz` | Impossível navegar matriz↔filial ou agrupar por grupo econômico |
| 3 | `ie` / `im` ficam em `pessoa` como valor único | IE é **por estabelecimento**; empresa com filiais em N UFs tem N IEs |
| 4 | `endereco` pendura na `pessoa` com um só `principal` | Filiais têm endereço fiscal próprio; não há binding por CNPJ |
| 5 | NF-e (roadmap) é emitida **por estabelecimento/CNPJ** (emitente e destinatário) | Sem granularidade de estabelecimento, não há emitente nem destinatário corretos |

### Escopo decidido

**Ambos** os lados usam o mesmo conceito de `estabelecimento`:

- **Emitente** — a própria empresa do tenant (flag `proprio=true`) emite NF-e por filial.
- **Destinatário** — clientes/fornecedores com filiais (`proprio=false`).
- **Dimensão operacional** — estoque (`deposito`) e financeiro passam a referenciar `estabelecimento`.

---

## 2. Modelo-alvo

`estabelecimento` vive inteiramente no `cadastro-service` (schema `cadastros`). O tenant **não**
ganha uma árvore separada: a "própria empresa" do tenant é uma `pessoa` + estabelecimento(s)
com `proprio=true`.

```
pessoa (entidade legal — agrupada por cnpj_raiz)
  ├─ estabelecimento (matriz 0001, is_matriz=true)
  │     ├─ cnpj_completo · ie · im
  │     ├─ endereco fiscal · contato
  │     └─ proprio=true  → EMITENTE (empresa do tenant)
  └─ estabelecimento (filial 0002 ...)
        └─ proprio=false → DESTINATARIO (se for cliente/fornecedor)

papéis: cliente / fornecedor       → permanecem em pessoa (relação comercial)
docs operacionais (pedido, NF-e,   → referenciam estabelecimento (ship-to / bill-to / emitente)
                   título, estoque)
```

### Mapeamento conceitual TCA

| Oracle TCA | Schema `cadastros` (alvo) |
|---|---|
| `HZ_PARTIES` | `pessoa` (entidade legal, agrupada por `cnpj_raiz`) |
| `HZ_PARTY_SITES` + `HZ_LOCATIONS` | `estabelecimento` + `endereco` |
| `HZ_CUST_ACCOUNTS` / supplier | `cliente` / `fornecedor` (papéis em `pessoa`) |
| `HZ_RELATIONSHIPS` (matriz/filial) | `pessoa.cnpj_raiz` + `estabelecimento.ordem` / `is_matriz` |

---

## 3. Decisões de design

1. **Dedup da empresa passa a ser por raiz.** Substituir `uq_pessoa_tenant_documento` por
   índices parciais: `(tenant_id, cnpj_raiz)` quando `tipo='PJ'` e `(tenant_id, documento)`
   quando `tipo='PF'`. Matriz e filial colapsam numa **única `pessoa`**. A raiz é extraída pelo
   `CnpjService` existente (trata CNPJ alfanumérico base-36, NT 2026.004).
2. **`ie` / `im` saem de `pessoa` e vão para `estabelecimento`.** IE é por estabelecimento.
   `pessoa` fica só com identidade da entidade legal.
3. **Papéis ficam em `pessoa`; `endereco` / `contato` migram para `estabelecimento`.** A relação
   comercial (cliente/fornecedor) é com a empresa; o endereço fiscal e o ship-to/bill-to são por
   filial. Ganha-se o split sold-to vs ship-to.
4. **Emitente vs destinatário é só a flag `proprio`.** NF-e:
   `emitente = estabelecimento WHERE proprio=true AND tenant`;
   `destinatario = estabelecimento do cliente`. O motor IBS/CBS lê UF/município do `endereco`
   do estabelecimento.
5. **PF não tem estabelecimento.** Pessoa física (CPF) permanece sem filial; dedup por
   `documento`. Apenas `tipo='PJ'` recebe estabelecimentos.

---

## 4. Schema — novo changelog `cadastro-schema-00X.yaml`

> Numeração exata a confirmar contra o último changelog em
> `liquibase-service/src/main/resources/db/changelog/cadastro/`.

### 4.1 Tabela `estabelecimento`

| Coluna | Tipo | Notas |
|---|---|---|
| `id` | uuid PK | `GenerationType.UUID` |
| `tenant_id` | bigint NN | `BaseTenantEntity` |
| `pessoa_id` | uuid NN | FK → `cadastros.pessoa(id)` |
| `cnpj_completo` | varchar(18) NN | espelha o mask de `pessoa.documento` |
| `ordem` | varchar(4) NN | `0001` matriz, `0002`+ filial (varchar p/ alfanumérico) |
| `is_matriz` | boolean NN | |
| `proprio` | boolean NN default false | empresa do tenant (emitente) |
| `ie` | varchar(20) | inscrição estadual (por estabelecimento) |
| `im` | varchar(20) | inscrição municipal |
| `ativo` | boolean NN default true | |
| `created_at` / `created_by` | NN | auditoria |
| `updated_at` / `last_updated_by` | nullable | auditoria |

**Constraints / índices**
- `uq_estab_tenant_cnpj` UNIQUE `(tenant_id, cnpj_completo)`
- índice parcial `uq_estab_matriz_pessoa` UNIQUE `(tenant_id, pessoa_id) WHERE is_matriz`
  (uma matriz por empresa)
- índice parcial `uq_estab_proprio` UNIQUE `(tenant_id, pessoa_id) WHERE proprio`
  (uma "própria empresa" por tenant — validar regra com onboarding)
- `idx_estab_pessoa_id` `(pessoa_id)`

### 4.2 Alterações em tabelas existentes

| Tabela | Mudança |
|---|---|
| `pessoa` | + `cnpj_raiz varchar(8)`; trocar uniques por índices parciais por `tipo`; **depois** dropar `ie` / `im` |
| `endereco` | + `estabelecimento_id uuid` FK; rebind do endereço fiscal; soltar `pessoa_id` (nullable na transição) |
| `contato` | + `estabelecimento_id uuid` FK (transição igual a `endereco`) |
| `deposito` | + `estabelecimento_id uuid` FK (estoque por filial — fase 2) |

---

## 5. Migração de dados (pré-MVP — baixo custo)

Ordem dos changesets (idempotentes, reversíveis quando possível):

1. Criar tabela `estabelecimento`.
2. **Backfill matriz**: para cada `pessoa` com `tipo='PJ'` → 1 estabelecimento
   `ordem='0001'`, `is_matriz=true`, `cnpj_completo = pessoa.documento`, `ie`/`im` copiados.
3. Backfill `pessoa.cnpj_raiz = CnpjService.raiz(documento)` (PJ).
4. Rebind `endereco.estabelecimento_id` / `contato.estabelecimento_id` para a matriz; manter
   `pessoa_id` nullable durante a transição.
5. Marcar `proprio=true` na matriz da `pessoa` da própria empresa do tenant
   (novo passo no onboarding — ver §6).
6. Swap das uniques de `pessoa` (índices parciais por `tipo`).
7. Só então dropar `ie` / `im` de `pessoa`.

> Rollback: cada changeset com `rollback` explícito; a remoção de `ie`/`im` é a única
> irreversível sem re-backfill — executar por último e após validação.

---

## 6. Impacto cross-service

- **`auth-service` (onboarding do tenant):** ao provisionar o tenant, criar a `pessoa` da
  própria empresa + estabelecimento matriz com `proprio=true`. Vale tanto para o fluxo de
  parceiro quanto para o self-service (`criar-conta`). Definir se a criação é síncrona (no
  onboarding) ou via evento Kafka para o `cadastro-service`.
- **NF-e / motor fiscal (futuro):** resolução de emitente/destinatário por `estabelecimento`;
  endereço fiscal do estabelecimento alimenta IBS/CBS.
- **Estoque / financeiro (futuro):** `deposito` e títulos ganham `estabelecimento_id` como
  dimensão de segregação por filial.

---

## 7. Camada Java (cadastro-service)

| Artefato | Ação |
|---|---|
| `domain/Estabelecimento.java` | NOVO — entity (`BaseTenantEntity`), FK `pessoa` |
| `domain/Pessoa.java` | MODIFICADO — remover `ie`/`im`, adicionar `cnpjRaiz`, `@OneToMany estabelecimentos` |
| `domain/Endereco.java` / `Contato.java` | MODIFICADO — FK `estabelecimento` |
| `repository/EstabelecimentoRepository.java` | NOVO — `findByTenantId`, `findByPessoaId`, `findByCnpjCompletoAndTenantId` |
| `services/EstabelecimentoService.java` | NOVO — CRUD + regra "find-or-create matriz" |
| `services/PessoaService.java` | MODIFICADO — dedup por `cnpj_raiz` (PJ) / `documento` (PF); find-or-return-existing |
| `api/controllers/EstabelecimentoController.java` | NOVO — endpoints CRUD por pessoa |
| `api/dto` + `api/mappers` | NOVOS — DTOs e MapStruct |

---

## 8. Fases de implementação

| Fase | Entrega | Depende |
|---|---|---|
| 1 | Changelog `estabelecimento` + migração de dados (backfill matriz) | — |
| 2 | Entity + repository + service + controller no cadastro-service | Fase 1 |
| 3 | Dedup de `pessoa` por raiz + remoção de `ie`/`im` | Fase 2 |
| 4 | Passo de onboarding no auth-service (pessoa própria + matriz `proprio`) | Fase 2 |
| 5 | `deposito.estabelecimento_id` (estoque por filial) | Fase 2 |
| 6 | Integração NF-e / motor fiscal (emitente/destinatário) | Fases 2–4 + módulo fiscal |

---

## 9. Riscos e pontos abertos

- **Inconsistência de dedup já existente:** `PessoaService` checa
  `existsByDocumentoAndNomeRazaoAndTenantId` mas o DB é único por `documento`. Reconciliar para
  dedup por `cnpj_raiz` (PJ) nesta mudança.
- **Onboarding síncrono vs assíncrono** da pessoa própria do tenant — decidir (evento Kafka
  vs chamada direta) na Fase 4.
- **Regra `uq_estab_proprio`** (uma própria empresa por tenant) — confirmar se o tenant pode ter
  mais de uma empresa emitente (grupos com múltiplos CNPJ raiz).
- **Documentos antigos** (se houver dados) que assumem `endereco.pessoa_id` direto precisam do
  rebind antes de soltar a coluna.