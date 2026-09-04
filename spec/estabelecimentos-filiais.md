# Estabelecimentos / Filiais — Modelo Party + Estabelecimento (estilo TCA)
## Especificação de Mudança

**Status:** Planejado (não iniciado)
**Serviço primário:** `cadastro-service` (porta 8086) · schema `cadastros`
**Serviços impactados:** `auth-service` (onboarding do tenant), `liquibase-service` (DDL), futuros módulos NF-e / motor fiscal IBS-CBS / estoque / financeiro
**Base package:** `com.l.erp.cadastroservice`
**Data:** 4 de setembro de 2026 (última atualização — §6.1 adicionado)

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
- índice parcial `uq_estab_proprio_matriz` UNIQUE `(tenant_id) WHERE proprio AND is_matriz`
  (**regra decidida:** o tenant tem UMA própria empresa — uma matriz e as filiais dela.
  Todos os estabelecimentos `proprio=true` de um tenant pertencem à mesma `pessoa`;
  o índice garante a matriz única e o `EstabelecimentoService` valida que filial
  `proprio=true` tem a mesma `pessoa_id` da matriz própria)
- `idx_estab_pessoa_id` `(pessoa_id)`

### 4.2 Alterações em tabelas existentes

| Tabela | Mudança |
|---|---|
| `pessoa` | + `cnpj_raiz varchar(8)`; trocar uniques por índices parciais por `tipo`; **depois** dropar `ie` / `im` |
| `endereco` | + `estabelecimento_id uuid` FK; rebind do endereço fiscal de PJ. **`pessoa_id` NÃO é dropada** — PF não tem estabelecimento (decisão 5), então endereço de PF permanece em `pessoa`. Regra final: XOR — exatamente um de `pessoa_id` (PF) / `estabelecimento_id` (PJ) preenchido (CHECK) |
| `contato` | + `estabelecimento_id uuid` FK — mesma regra XOR de `endereco` |
| `deposito` | + `estabelecimento_id uuid` FK (estoque por filial — fase 2) |

---

## 5. Migração de dados (pré-MVP — baixo custo)

Ordem dos changesets (idempotentes, reversíveis quando possível):

1. Criar tabela `estabelecimento`.
2. **Backfill matriz**: para cada `pessoa` com `tipo='PJ'` → 1 estabelecimento
   `ordem='0001'`, `is_matriz=true`, `cnpj_completo = pessoa.documento`, `ie`/`im` copiados.
3. Backfill `pessoa.cnpj_raiz = CnpjService.raiz(documento)` (PJ).
4. Rebind `endereco.estabelecimento_id` / `contato.estabelecimento_id` para a matriz —
   **somente registros de pessoa PJ**; endereço/contato de PF permanece com `pessoa_id`
   (PF não tem estabelecimento). Ao final, adicionar CHECK XOR (`pessoa_id` ⊕ `estabelecimento_id`).
5. Marcar `proprio=true` na matriz da `pessoa` da própria empresa do tenant.
   **Fonte do vínculo (regra decidida):** para tenants existentes não há vínculo gravado
   tenant→pessoa; o backfill casa `raiz(tenant.cnpj) = pessoa.cnpj_raiz` dentro do mesmo
   tenant (raiz via `CnpjService` — CNPJ alfanumérico ok) e marca a matriz dessa pessoa.
   Se não existir `pessoa` com essa raiz, o backfill **cria** pessoa + estabelecimento
   matriz (mesmo efeito da Fase 4 do onboarding). Tenants novos ganham o vínculo direto
   no onboarding (§6).
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

### 6.1 Stopgaps já implementados no `operacoes-service` (D4, spec/o2c-vendas.md §8)

Antes deste modelo existir, o motor fiscal (`POST /fiscal/calcular`) já exige `cClassTrib` e
`ibgeDestino`/`ufDestino` pra calcular IBS/CBS/ISS no faturamento do pedido (§8). Dois gaps
foram fechados em 2026-09-04 como stopgap, **sem depender de `estabelecimento`**:

- **`cClassTrib` (classificação tributária do serviço, Anexo VIII):** virou campo
  `Produto.classTrib` (`cadastro-schema-010.yaml`, `cad-029`), obrigatório quando
  `tipo=SERVICO` (mesma validação de `codigoServico` em `ProdutoService.validarTipo`). Isso é
  **permanente** — não tem relação com matriz/filial, fica como está depois desta migração.
- **`ibgeDestino`/`ufDestino` (UF/IBGE do destinatário):** `CadastroServiceClient.buscarEnderecoFiscal`
  busca o endereço da `pessoa` do cliente hoje (prioriza `tipo=FISCAL`, senão `principal`, senão
  o primeiro). Isso **também é permanente** pro lado destinatário — `estabelecimento` não muda
  onde mora esse dado (endereço do cliente continua em `pessoa`/`endereco`, só ganha
  `estabelecimento_id` como FK adicional pra PJ, XOR com `pessoa_id`, §4.2). Nenhum retrabalho
  necessário aqui quando a Fase 1–3 deste doc sair.

**O que fica bloqueado até este modelo existir — `ufOrigem` (emitente):**
`FiscalServiceClient` (operacoes-service) hoje envia `ufOrigem=null` sempre — não há
`estabelecimento` emitente modelado, então não existe onde ler a UF de origem do tenant. Isso
só destrava na **Fase 6** deste doc (Integração NF-e / motor fiscal), quando
`estabelecimento WHERE proprio=true` existir: `ufOrigem` passa a vir do endereço do
estabelecimento emitente do tenant (a matriz, ou a filial de onde o pedido saiu, se o modelo
de filial-por-pedido existir nessa altura). Até lá, o cálculo fiscal do O2C roda com origem
indefinida — aceitável pro estágio atual (regime único presumido, sem split multi-UF), mas é
o ponto exato a resolver quando a Fase 6 começar.

---

## 7. Camada Java (cadastro-service)

| Artefato | Ação |
|---|---|
| `domain/Estabelecimento.java` | NOVO — entity (`BaseTenantEntity`), FK `pessoa` |
| `domain/Pessoa.java` | MODIFICADO — remover `ie`/`im`, adicionar `cnpjRaiz`, `@OneToMany estabelecimentos` |
| `domain/Endereco.java` / `Contato.java` | MODIFICADO — FK `estabelecimento` (PJ) mantendo FK `pessoa` (PF) — XOR |
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
- ~~Regra `uq_estab_proprio`~~ **Resolvido:** uma própria empresa por tenant (uma matriz +
  suas filiais, mesma `pessoa`). Grupo com múltiplas raízes de CNPJ = múltiplos tenants.
- **Documentos antigos** (se houver dados) que assumem `endereco.pessoa_id` direto precisam do
  rebind antes de soltar a coluna.