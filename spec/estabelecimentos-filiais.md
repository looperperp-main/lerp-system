# Estabelecimentos / Filiais — Modelo Party + Estabelecimento (estilo TCA)
## Especificação de Mudança

**Status:** Fases 1-6 escritas, não testadas (Fase 5 = `deposito.estabelecimento_id`; Fase 6 = `ufOrigem` no faturamento) — ver §8
**Serviço primário:** `cadastro-service` (porta 8086) · schema `cadastros`
**Serviços impactados:** `auth-service` (onboarding do tenant), `liquibase-service` (DDL), futuros módulos NF-e / motor fiscal IBS-CBS / estoque / financeiro
**Base package:** `com.l.erp.cadastroservice`
**Data:** 4 de setembro de 2026 (última atualização — §6.1/§8 Fase 6 marcada como escrita)

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

### 5.1 Escopo real implementado na Fase 1 (`cadastro-schema-011.yaml`, `cad-030`..`cad-039`)

Os passos 1, 3, 5 e 6 desta lista (tabela `estabelecimento`, `pessoa.cnpj_raiz` + backfill,
`proprio=true` na matriz do tenant, swap das uniques de `pessoa`) foram escritos — **não
testados** (sem `mvn`/Liquibase rodado). Os passos 2 (rebind `endereco`/`contato` para
`estabelecimento_id`, incluindo o CHECK XOR) e 7 (dropar `ie`/`im` de `pessoa`) foram
**deliberadamente cortados** desta Fase, porque:

- **Rebind de `endereco`/`contato`:** `CadastroServiceClient.buscarEnderecoFiscal`
  (operacoes-service, ver §6.1) e qualquer outro consumidor hoje leem endereço/contato por
  `pessoa_id`. Nular `pessoa_id` nos registros de PJ antes de qualquer código migrar pra ler
  por `estabelecimento_id` quebraria esses fluxos em produção assim que este changelog
  rodasse. Isso precisa entrar **junto** com a Fase 2 (entidades `Endereco`/`Contato` +
  services que passam a resolver por `estabelecimento`), não antes.
- **Drop de `ie`/`im`:** `Pessoa.java` ainda mapeia essas colunas; `cadastro-service` roda com
  `ddl-auto=validate` (CLAUDE.md) — dropar as colunas sem atualizar a entity na mesma janela
  de deploy derruba o startup do serviço por falha de validação de schema. Sai junto da Fase 3
  (que já é onde o doc original também colocava essa remoção, §8).

Nenhuma dessas duas exclusões quebra o que foi criado agora: são todas aditivas (tabela nova,
coluna nova, troca de unique constraint sem mudança de dado) e não têm contrapartida em
`Pessoa.java`/`Endereco.java`/`Contato.java` ainda — Hibernate `validate` ignora colunas de
banco não mapeadas na entity.

Achado à parte, também sem correção nesta Fase (fica pra Fase 3, junto com a reconciliação de
dedup): `CnpjService` mencionado na decisão 1 (§3) **não existe** com esse propósito — o único
`CnpjService` do monorepo é o de consulta à Receita (`partner-service`, API externa). A raiz do
CNPJ foi extraída inline via SQL (`LEFT(UPPER(REGEXP_REPLACE(documento, ...)), 8)`); quando a
Fase 2/3 escrever isso em Java, não há utilitário pronto pra reaproveitar — precisa ser criado
(provavelmente em `common`, já que `cadastro-service` e o onboarding de `auth-service`, Fase 4,
vão precisar da mesma lógica).

### 5.2 Escopo real implementado na Fase 2 + rebind (`cadastro-schema-012.yaml`, `cad-040`..`cad-049`)

Os dois passos cortados da Fase 1 (rebind `endereco`/`contato` + CHECK XOR, e drop de
`ie`/`im`) foram implementados **juntos**, na mesma leva que a Fase 2 (camada Java), por
decisão explícita — em vez de separar numa Fase 3, porque o drop de coluna só é seguro depois
que a entity para de mapeá-la (ver risco do §5.1), e as duas coisas mudam junto:

- **Camada Java (Fase 2, ver §7):** `Estabelecimento.java` (entity/repository/service
  /controller/DTOs/mapper, completo) + `Pessoa.ie`/`Pessoa.im` viram `@Transient` (resolvidos
  via matriz no `PessoaService`, não mais colunas) + `Endereco.java`/`Contato.java` ganham FK
  `estabelecimento` (nullable) ao lado de `pessoa` (agora também nullable), com o
  branch PF→`pessoa`/PJ→`estabelecimento` (matriz) decidido em `EnderecoService.create`/
  `ContatoService.create` a partir de `Pessoa.getTipo()`.
- **`cadastro-schema-012.yaml`:** `cad-040`/`cad-041` (coluna `estabelecimento_id` + FK +
  índice em `endereco`/`contato`), `cad-042`/`cad-043` (`pessoa_id` vira nullable),
  `cad-044`/`cad-045` (rebind SQL: linhas de pessoa PJ passam de `pessoa_id` para
  `estabelecimento_id` da matriz — usa o backfill de matriz já feito na Fase 1), `cad-046`
  /`cad-047` (CHECK XOR — exatamente um de `pessoa_id`/`estabelecimento_id`), `cad-048`
  /`cad-049` (drop `pessoa.ie`/`pessoa.im`, sem `rollback` — irreversível sem re-backfill,
  mesma observação do §5, seguindo o padrão do resto do monorepo de não reverter `dropColumn`).
- **Repositórios (`EnderecoRepository`/`ContatoRepository`):** os finders antes derivados por
  nome de método (`findAllByPessoaIdAndTenantId`) viraram `@Query` JPQL explícita com OR entre
  `pessoa.id` e `estabelecimento.pessoa.id`, reforçando `tenantId` manualmente na cláusula
  (além do `@Filter` do Hibernate) — sem essa dupla checagem o XOR quebraria o isolamento por
  tenant nas leituras.
- **API pública inalterada:** `EnderecoController`/`ContatoController` (rotas, DTOs, assinaturas)
  não mudaram — o roteamento PF/PJ é transparente na camada de service/repository. Consumidores
  como `CadastroServiceClient.buscarEnderecoFiscal` (operacoes-service, §6.1) continuam lendo
  por `pessoaId` sem saber se o dado hoje vive em `pessoa_id` ou `estabelecimento_id`.
- **Não testado** (sem `mvn`/Liquibase rodado); testes existentes que citam o método de
  repositório removido (`existsByDocumentoAndNomeRazaoAndTenantId`) ainda precisam ser
  atualizados — ver §9.

---

## 6. Impacto cross-service

- **`auth-service` (onboarding do tenant):** ao provisionar o tenant, criar a `pessoa` da
  própria empresa + estabelecimento matriz com `proprio=true`. Vale tanto para o fluxo de
  parceiro quanto para o self-service (`criar-conta`).
  **Decisão (04/09/2026):** implementado **síncrono via REST** por ora — `TenantService.createTenant`
  chama `CadastroServiceClient.provisionarPessoaPropria` (novo, `infra/client/`) logo após salvar o
  tenant: `POST /api/v1/pessoas` seguido de `PATCH .../estabelecimentos/matriz/proprio` no
  cadastro-service; falha propaga como erro na criação do tenant. Migração pra evento Kafka
  assíncrono fica registrada como [issue #81](https://github.com/looperperp-main/lerp-system/issues/81)
  no GitHub Project #1, coluna Todo.
  **Gap fechado (2026-09-04, ✅ verde em `mvn verify`):** `AuthService.java` tinha mais dois pontos que criam um
  tenant "de verdade" sem passar por `TenantService.createTenant` — `criarContaGratis` (self-service,
  cria `Tenant`+`UserAccount` na mesma transação) e `ativarConta` (conversão de convite de parceiro,
  transiciona `Tenant` de `CONVIDADO` pra `TRIAL`). Os dois agora chamam
  `cadastroServiceClient.provisionarPessoaPropria(tenant, userId)` no mesmo ponto (logo após o
  `save`/update do tenant que os torna "reais"), reaproveitando o `CadastroServiceClient` já criado
  pra `TenantService`. `InviteRequestedConsumer` (cria o `Tenant` stub `CONVIDADO` a partir do evento
  Kafka de convite) foi deixado de fora de propósito — nesse estágio o tenant ainda não é real e não
  há usuário autenticado que sirva de `userId` pro provisionamento; o provisionamento acontece depois,
  em `ativarConta`. `AuthServiceTest` ganhou 2 testes (`ativarConta_provisionaPessoaPropriaAposAtivar`,
  `criarContaGratis_provisionaPessoaPropriaAposCriar`) verificando a chamada.
  **Regressão encontrada e corrigida (2026-09-04, ✅ confirmada verde):** o `mvn verify` do usuário
  mostrou 22 erros em `AuthControllerTest`/`TenantControllerTest` (`ApplicationContext failure
  threshold (1) exceeded`) — esses testes usam `@WebMvcTest` + `@Import({AuthService.class/TenantService.class, ...})`
  com o serviço real montado a partir de `@MockitoBean` por dependência, e o novo parâmetro de
  construtor `CadastroServiceClient` ficou sem `@MockitoBean` correspondente. Corrigido adicionando
  `@MockitoBean private CadastroServiceClient cadastroServiceClient;` nos dois arquivos.
  `./mvnw verify -pl auth-service,cadastro-service -am -DskipITs` rodou tudo verde depois do fix
  (`auth-service` e `cadastro-service` BUILD SUCCESS) — **Fase 4 (onboarding síncrono) está feita
  e testada.**
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

**`ufOrigem` (emitente) — ✅ escrito na Fase 6, não testado:** cadastro-service ganhou
`GET /api/v1/estabelecimentos/proprio` (tenant-wide, `EstabelecimentoProprioController` +
`EstabelecimentoService.buscarProprio`), retornando o `pessoaId` do estabelecimento
`proprio=true` do tenant. `CadastroServiceClient.buscarPessoaIdEstabelecimentoProprio`
(operacoes-service) consome esse endpoint e reaproveita `buscarEnderecoFiscal` (mesmo método
já usado pro destinatário) pra achar a UF; `PedidoController.calcularFiscal` busca essa UF uma
vez por pedido e passa como `ufOrigem` em `FiscalServiceClient.calcularItem`/
`MotorFiscalRequestLocal`, que agora tem o campo (antes nem existia no record local). Ainda não
considera filial-por-pedido — sempre usa o estabelecimento "próprio" (matriz), o que é
aceitável enquanto o O2C não modela emissão por filial.

---

## 7. Camada Java (cadastro-service)

**Status: implementado nesta leva (Fase 2 + rebind), não testado.**

| Artefato | Ação |
|---|---|
| `domain/Estabelecimento.java` | NOVO — entity (`BaseTenantEntity`), FK `pessoa` |
| `domain/Pessoa.java` | MODIFICADO — `ie`/`im` viram `@Transient` (populados via matriz), `cnpjRaiz` persistido (já era Fase 1) |
| `domain/Endereco.java` / `Contato.java` | MODIFICADO — FK `estabelecimento` (PJ, nullable) ao lado de `pessoa` (PF, agora nullable) — XOR garantido no DB (`cad-046`/`cad-047`) |
| `repository/EstabelecimentoRepository.java` | NOVO — `findAllByPessoaIdAndTenantId`, `findByPessoaIdAndMatrizTrueAndTenantId`, `findAllByPessoaIdInAndMatrizTrueAndTenantId` (batch) |
| `repository/EnderecoRepository.java` / `ContatoRepository.java` | MODIFICADO — finders viram `@Query` JPQL (OR `pessoa.id`/`estabelecimento.pessoa.id`, `tenantId` explícito) |
| `services/EstabelecimentoService.java` | NOVO — CRUD de filial + `criarMatriz`/`atualizarIeImMatriz` (ciclo de vida da matriz, chamado por `PessoaService`) + busca em lote (`buscarMatrizesPorPessoas`) |
| `services/PessoaService.java` | MODIFICADO — dedup por `cnpj_raiz` (PJ, `existsByCnpjRaizAndTenantId`) / `documento` (PF, `existsByDocumentoAndTenantId`); cria/atualiza matriz e popula `ie`/`im` em memória (fetch único e em lote) |
| `services/EnderecoService.java` / `ContatoService.java` | MODIFICADO — `create()` roteia PF→`pessoa` / PJ→`estabelecimento` (matriz) conforme `Pessoa.getTipo()` |
| `api/controllers/EstabelecimentoController.java` | NOVO — endpoints CRUD de filial por pessoa (matriz não é gravável aqui, é automática) |
| `api/dto/EstabelecimentoRequestDTO.java` / `EstabelecimentoResponseDTO.java` + `api/mappers/EstabelecimentoAssembler.java` | NOVOS |
| `api/mappers/EnderecoAssembler.java` / `ContatoAssembler.java` | MODIFICADO — `pessoaId` resolvido via `pessoa` OU `estabelecimento.pessoa` (null-safe) |
| `common/Constants.java` | MODIFICADO — chaves `ESTABELECIMENTO_*` |

---

## 8. Fases de implementação

| Fase | Entrega | Depende | Status |
|---|---|---|---|
| 1 | Changelog `estabelecimento` + migração de dados (backfill matriz) | — | ✅ migração aplicada + verde (§5.1) |
| 2 | Entity + repository + service + controller no cadastro-service | Fase 1 | ✅ verde (§5.2, §7) |
| 3 | Dedup de `pessoa` por raiz + remoção de `ie`/`im` | Fase 2 | ✅ verde — dedup por raiz já veio na Fase 1 (`cad-037`..`039`), drop de `ie`/`im` fechado nesta leva (`cad-048`/`cad-049`, §5.2) |
| 4 | Passo de onboarding no auth-service (pessoa própria + matriz `proprio`) | Fase 2 | ✅ verde (§6) |
| 5 | `deposito.estabelecimento_id` (estoque por filial) | Fase 2 | ✅ escrito, não testado — coluna nullable (`cadastro-schema-013.yaml`, `cad-050`) + FK/índice, `Deposito.estabelecimentoId`/`DepositoDTO.estabelecimentoId`, validação via `estabelecimentoRepository.existsByIdAndTenantId` em `save`/`update` (400 se filial não existe/é de outro tenant), `DepositoServiceTest` com 2 novos casos. Sem UI Angular (dropdown de filial) — fora do escopo por ora |
| 6 | Integração NF-e / motor fiscal (emitente/destinatário) | Fases 2–4 + módulo fiscal | ✅ escrito, não testado — `ufOrigem` fechado: `GET /api/v1/estabelecimentos/proprio` (cadastro-service) + `CadastroServiceClient.buscarPessoaIdEstabelecimentoProprio`/`FiscalServiceClient.calcularItem` (operacoes-service) agora enviam a UF real do estabelecimento próprio do tenant ao invés de `null` (ver §6.1) |

---

## 9. Riscos e pontos abertos

- ~~**Inconsistência de dedup já existente**~~ **Resolvido nesta leva:**
  `existsByDocumentoAndNomeRazaoAndTenantId` foi substituído por
  `existsByCnpjRaizAndTenantId` (PJ) / `existsByDocumentoAndTenantId` (PF) em
  `PessoaRepository`/`PessoaService`.
- **Onboarding síncrono vs assíncrono** da pessoa própria do tenant — decidir (evento Kafka
  vs chamada direta) na Fase 4.
- ~~Regra `uq_estab_proprio`~~ **Resolvido:** uma própria empresa por tenant (uma matriz +
  suas filiais, mesma `pessoa`). Grupo com múltiplas raízes de CNPJ = múltiplos tenants.
- ~~**Documentos antigos** que assumem `endereco.pessoa_id` direto~~ **Resolvido nesta leva:**
  rebind SQL (`cad-044`/`cad-045`) roda antes do CHECK XOR (`cad-046`/`cad-047`) no mesmo
  changelog — não há janela em que o XOR exista sem o rebind já ter passado.
- ~~**Pendente:** `PessoaServiceTest.java`/`PessoaServiceIT.java`/`EnderecoServiceTest.java`/
  `ContatoServiceTest.java` desatualizados~~ **Resolvido nesta leva:** os quatro arquivos
  atualizados (mock `EstabelecimentoService` adicionado, dedup por raiz, novos testes
  `shouldRouteToEstabelecimentoMatrizWhenPessoaIsPJ` em `Endereco`/`ContatoServiceTest`
  cobrindo o roteamento PJ→matriz). Fidelidade do rewrite de `EnderecoServiceTest.java`
  confirmada via `git diff` (só adições, nenhuma linha original perdida). Ainda não rodado
  via `mvn`.
- ~~**Sem cobertura de teste nova** para `EstabelecimentoService`/`EstabelecimentoController`~~
  **Parcialmente resolvido nesta leva:** `EstabelecimentoServiceTest.java` criado (18 casos —
  CRUD, `buscarMatrizPorPessoa`/`buscarMatrizOpcionalPorPessoa`/`buscarMatrizesPorPessoas`,
  `criarMatriz`/`atualizarIeImMatriz`, validação PJ-only e not-found). Ainda **sem teste pro
  `EstabelecimentoController`** (camada REST/`@WebMvcTest`) — não decidido se é necessário.
  Ainda não rodado via `mvn`.
- ~~**Liquibase quebrado**: `cad-036-backfill-estabelecimento-proprio-tenant` falhava com
  `Unterminated dollar quote` (bloco `DO $$...$$` em `sql: >` YAML folded scalar com indentação
  variável — o folding do YAML inseria quebra de linha no meio do bloco, e o splitter padrão do
  Liquibase cortava ali antes do `$$` de fechamento)~~ **Resolvido nesta leva:** trocado pra
  `sql: |` (literal, preserva quebras exatamente) + `splitStatements: false` no changeset. Não
  precisa de limpeza manual no banco — `cad-030`..`cad-035` já ficaram gravados, o retry parte
  direto do `cad-036`. Conferido que `cadastro-schema-012.yaml` (Fase 2, ainda não alcançada)
  não repete o padrão (sem blocos `DO $$`, indentação uniforme). Não testado por mim — usuário
  precisa rodar `liquibase-service` de novo pra confirmar.
- **Frontend não coberto por esta spec.** `Pessoa`/`Endereco`/`Contato` no Angular continuam
  funcionando sem mudança nenhuma (API pública inalterada — `PessoaService.java` já roteia
  `ie`/`im` pra/da matriz por baixo dos panos, mantendo os mesmos campos em
  `PessoaRequestDTO`/`PessoaResponseDTO`). Mas **não existe nenhuma tela/serviço Angular para
  `Estabelecimento`** (`estabelecimento.service.ts`, página de filiais) — o backend já tem CRUD
  completo (`EstabelecimentoController`), só acessível hoje via chamada direta à API. Sem UI, um
  tenant não consegue criar/ver/editar uma filial (`is_matriz=false`). Não está em nenhuma das
  Fases 1-6 (todas backend); precisa virar item explícito quando a UI de filiais for priorizada.