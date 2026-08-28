# Motor Fiscal — próximos passos

> Última atualização: 27 de agosto de 2026

Handoff das fatias seguintes do motor fiscal. Escrito para ser lido do zero, sem
contexto de conversa anterior.

## Onde o motor está hoje

`fiscal-service` (porta 8093) expõe `POST /fiscal/calcular`: determinístico, sem
persistência, **saída e, desde o item 4, entrada (crédito)**. Lê conteúdo fiscal real de `fiscal.*` via
`TabelaFiscalJdbc` (JdbcClient, read-only; schema e carga pertencem ao
`liquibase-service`, changesets `fiscal-001..031`).

**Divisão de responsabilidade (decidida em 29 de julho de 2026):** o
`fiscal-service` é **só cálculo** — stateless, sem schema de escrita, para sempre
(MF-06 deixa de ser "por ora" e passa a ser desenho). Quem guarda documento,
saldo e movimento é o `operacoes-service`, que será o **AP** (contas a pagar /
P2P) e o **AR** (contas a receber / O2C).

O que já existe:

| tabela | conteúdo | quem usa |
|---|---|---|
| `fiscal.ncm` | 15.156 NCM | catálogo |
| `fiscal.cfop` | 629 CFOP | motor (tipo de operação, 1ª etapa da cadeia) |
| `fiscal.regime_dif_ncm` | 242 regimes da LC 214, **só produto** | motor |
| `fiscal.aliq_ibs_municipio` / `aliq_cbs_regime` / `aliq_is_ncm` | alíquotas | motor |
| `fiscal.servico_nbs` | 895 linhas, 675 NBS (Anexo VIII) | catálogo, picklist do cadastro |
| `fiscal.servico_cclasstrib` | 246 pares item LC 116 × cClassTrib | motor (validação) |
| `fiscal.regime_cclasstrib` | **25 de 27** cClassTrib com redução | motor |

Serviço é classificado pelo `cClassTrib` **declarado** na nota (não deduzido do
código LC 116 — o mesmo serviço muda de classificação conforme o contexto).
Sem `cClassTrib`, ou com par item×cClassTrib não admitido, o motor devolve 400.

Testes: `MotorFiscalServiceTest` (aritmética, oráculo do Fin.md §1.4.8, contra
`TabelaFiscalFake`) e `TabelaFiscalJdbcTest` (SQL, H2 em modo PostgreSQL).

Fallback para `PADRAO` (NCM/`cClassTrib` sem linha de regime ⇒ tributa cheio) não
é mais silencioso: sai `WARN` no log e uma linha de aviso na `memoriaCalculo` da
resposta. `INTEGRAL` — classificação declarada com redução 0 — não avisa.

---

## 2. Os 20 percentuais de redução que faltam ✅ FEITO (29 de julho de 2026)

Entrou o changeset **`fiscal-021-seed-regime-cclasstrib-lc214`** (no fim de
`fiscal-schema-007.yaml`, sem tocar no `fiscal-019`), com **18 dos 20** códigos —
uma linha por `cClassTrib`, cada uma comentada com o artigo da LC 214 (redação
atualizada pela LC 227/2026) que fixa o percentual:

| redução | `cClassTrib` | artigo |
|---|---|---|
| 100% | `200016` ICT | art. 156 (reduzidas a zero) |
| 100% | `200021` transp. ferroviário/hidroviário urbano | art. 285, I |
| 100% | `400001` transp. rodoviário/metroviário urbano | art. 157 (isenção) |
| 70% | `200027` locação/cessão/arrendamento de imóveis | art. 261, § único |
| 60% | `011001` plano funerária | art. 236 → 237 |
| 60% | `011002` plano de saúde | art. 237 |
| 60% | `011003` intermediação de plano de saúde | art. 240 (mesma alíquota do plano) |
| 60% | `200037` serviço ambiental de vegetação nativa | art. 137, caput + § 3º |
| 60% | `200040` comunicação institucional | art. 140 |
| 60% | `200041` / `200042` educação desportiva | art. 141, I e II |
| 60% | `200045` reabilitação urbana | art. 158, caput |
| 50% | `200046` operações com bens imóveis | art. 261, caput |
| 40% | `200048` hotelaria e parques | art. 281 |
| 40% | `200051` agências de turismo | art. 289, II (= alíquota da hotelaria) |
| 30% | `011005` plano de saúde de animal doméstico | art. 243 |
| 30% | `200052` profissões intelectuais | art. 127 |
| 0% | `000002` exploração de via | art. 11, VIII (integral; só define o local) |

Ficaram de fora os **2** que o modelo não expressa — `percentual_reducao` é um só
para IBS e CBS: `010002` (serviços financeiros, art. 233 fixa a *soma* de IBS+CBS
em valor absoluto, 10,85% em 2027-2028) e `200025` (Prouni, art. 308 zera só a
CBS). Seguem em `PADRAO`, tributando cheio, registrados em
`spec/anexos-lc214-revisar.md` junto com duas ressalvas dos que entraram
(`200045` tem 80% na locação do art. 162, VI; `000002` exige rateio por extensão
da via, que o motor não faz).

Migração aplicada e suíte verde em 30 de julho de 2026 (`liquibase-service` +
`verify -pl fiscal-service`).

---

## 3. Cálculo dual da transição (2026–2032) — decisão tomada ✅ FEITO (27 de agosto de 2026)

Era "o maior buraco: sem isso o ERP não emite documento válido em 2027–2032". Com
a decisão de **não emitir nota inicialmente** (§5), deixou de ser bloqueante —
segue necessário para o imposto total ficar certo na transição (formação de preço,
contas a receber, DRE), mas pode vir depois do item 4.

O motor calcula só o lado novo (IBS/CBS/IS). Mas na transição a nota carrega os
dois sistemas no mesmo item: ICMS e ISS em redução progressiva, PIS/COFINS até
2026. Calendário da LC 214, em resumo:

- **2026** — ano de teste: CBS 0,9% e IBS 0,1%, compensáveis com PIS/COFINS.
- **2027** — PIS/COFINS extintos, CBS cheia, IS entra em cena.
- **2029–2032** — ICMS e ISS reduzidos 10%/20%/30%/40% ao ano.
- **2033** — só IBS/CBS.

### Decisão (29 de julho de 2026)

**Não vamos construir motor legado.** Nada de ST, MVA, DIFAL, pauta, benefício
por UF, CSOSN por regime — mesmo valendo 7 anos, atrasa demais o
desenvolvimento. O lado legado sai de uma **matriz tributária parametrizada**:
uma linha por rota fiscal, alíquota já resolvida, o motor só multiplica.

Rascunho da tabela (`fiscal.matriz_tributaria`, chave
`ncm_nbs` + `uf_origem` + `uf_destino`, blocos ICMS / ISS / PIS-COFINS, com
`'00000000'` como linha de fallback geral) — a direção está certa. Cinco ajustes
antes de virar changeset:

1. **Vigência é obrigatória.** Sem `vigencia_inicio`/`vigencia_fim` (ou `ano`) a
   `UNIQUE (ncm_nbs, uf_origem, uf_destino)` impede ter 2028 e 2029 na mesma
   rota — e é exatamente a redução de 10/20/30/40% de 2029–2032 que precisa
   variar por ano. Recomendado: a matriz guarda a alíquota **cheia** e a curva da
   transição vira uma tabela pequena `ano → % remanescente de ICMS/ISS`, do mesmo
   jeito que IBS/CBS já fazem com `aliq_ibs_municipio`/`aliq_cbs_regime`. Assim a
   matriz não é recarregada todo ano.
2. **ISS não é por UF, é por município.** `aliq_efetiva_iss` chaveada em
   `uf_destino` não resolve: a alíquota (2%–5%) é do município e varia por item
   da LC 116. Ou entra `ibge_municipio` (7 dígitos) + item LC 116, ou o ISS sai
   para tabela própria — que é o mais limpo, já que UF de origem/destino não diz
   nada sobre serviço.
3. **`aliq_efetiva_icms` e `p_reducao_base_icms` juntos se contradizem.** Se a
   alíquota já é efetiva, a redução de base está embutida e vai ser contada duas
   vezes. A NF-e exige os dois campos separados (`vBC` reduzida + `pICMS`
   nominal), então guardar **nominal + `p_reducao_base`** e calcular a efetiva.
4. **NBS tem 9 dígitos**, não 8 — `VARCHAR(8)` trunca. Coluna `VARCHAR(9)` mais
   um discriminador (`tipo_item` `'P'`/`'S'`), senão não se sabe se `12345678` é
   NCM ou NBS truncado.
5. **Multi-tenant — decidido: matriz global com override por tenant.** Benefício
   fiscal, regime especial e TTD são por contribuinte, então entra
   `tenant_id UUID` **nulável** na mesma tabela: `NULL` = linha base, valendo para
   todos; linha com `tenant_id` = override daquele contribuinte. Uma tabela só, sem
   duplicar a carga base por tenant. Cuidado com a chave: no Postgres `NULL` não
   colide em `UNIQUE`, logo `UNIQUE (tenant_id, ncm_nbs, uf_origem, uf_destino)`
   aceitaria duas linhas base iguais — usar
   `UNIQUE NULLS NOT DISTINCT` (PG 17, é a imagem do `compose.yaml`). Se o H2 do
   `TabelaFiscalJdbcTest` não engolir, o DDL do teste usa dois índices parciais
   (`WHERE tenant_id IS NULL` / `IS NOT NULL`) — o DDL do teste é escrito à mão e
   já divergia.

Ordem de resolução da busca — override antes da base, específico antes do
fallback, para não explodir em 27×27×NCM linhas e nem obrigar o tenant a
recadastrar tudo para mudar uma alíquota:

1. `(tenant_id, ncm, uf_origem, uf_destino)`
2. `(tenant_id, '00000000', uf_origem, uf_destino)`
3. `(NULL, ncm, uf_origem, uf_destino)`
4. `(NULL, '00000000', uf_origem, uf_destino)`

Nenhuma das quatro casa ⇒ 400 com código próprio, mesmo padrão do
`FISCAL_VIGENCIA_SEM_COBERTURA`: nunca assumir alíquota zero por dado
faltando (é o mesmo princípio do aviso de `PADRAO`).

PIS/COFINS têm prazo de validade curto (extintos em 2027): valem para competência
de 2026 e retroativo. Vale carregar, não vale investir.

Fora de escopo por decisão, registrado para não voltar como surpresa: ST/MVA
(`cst_icms = '060'` chega com o imposto já retido por quem calculou fora), DIFAL,
pauta fiscal e CSOSN do Simples.

### Plano de execução — 5 fatias (30 de julho de 2026)

**✅ FEITO e VERDE (27 de agosto de 2026): as 5 fatias estão código-completas e
confirmadas.** 3a/3b/3d já estavam verdes; 3c/3e foram escritas em 26/08 e
confirmadas em 27/08 pelo `mvn verify -pl fiscal-service -am` do usuário: BUILD
SUCCESS, 79 testes, 0 falhas, JaCoCo ok. O item 3 está fechado de ponta a ponta.

O caro aqui não é schema nem código, é **a alíquota interna de ICMS**. O resto do
ICMS é mais barato do que o rascunho acima sugere: a **interestadual não precisa de
tabela** — é Resolução do Senado 22/89 + 13/12 (12% geral, 7% saindo de S/SE exceto
ES para N/NE/CO/ES, 4% em importado), ou seja uma função com a lista de UFs, não
27×27 = 729 linhas de carga que envelhecem sozinhas. A **interna** é que varia: 17% a
23% por UF, e por produto dentro da UF (cesta básica, energia, combustível).

**Decisão tomada (30 de julho de 2026) — 27 linhas.** A base carrega a alíquota interna
GERAL de cada UF e exceção por NCM vira override. O `'00000000'` do rascunho já é
exatamente esse fallback, e exceção por NCM entra quando um cliente real reclamar.
Carga completa por NCM é projeto de meses e não tem fonte oficial consolidada.

**Decisão tomada (30 de julho de 2026) — retenção na fonte fica DENTRO do motor.** ISS
retido, IRRF, PIS/COFINS/CSLL e INSS são tributos da mesma nota e saem no mesmo
`/fiscal/calcular`, não no contas a receber: duas verdades sobre a mesma nota é pior, e
o AR precisaria de metade do conteúdo fiscal para calcular sozinho. Custo aceito — o
request ganha o que retenção exige e o motor hoje não recebe (natureza do tomador, se é
PJ, acumulado do mês para o piso do IRRF), e o contrato de saída cresce junto com o da
3c. Para quem vende serviço B2B isso é mais visível do que qualquer alíquota de NCM.

**Ordem revista (30 de julho de 2026) — 3d passa na frente da 3b.** O mercado-alvo é
serviço, depois EPI e produtos de tecnologia; supermercado e material médico por último.
Para serviço a perna legada da transição é o **ISS**, não o ICMS. EPI (cap. 39/40/61-65)
e tecnologia (cap. 84/85) não têm anexo de redução na LC 214 — tributam cheio, que é
exatamente o que `RegimeDiferenciado.PADRAO` já devolve hoje —, então a 3b continua
necessária mas deixou de ser urgente. Pelo mesmo motivo o backlog de
`spec/anexos-lc214-revisar.md` (alimento, farmácia, agro) sai do caminho crítico.

- **3a — curva da transição.** ✅ **feita (30 de julho de 2026) e confirmada verde em 18 de
  agosto de 2026** (`mvn verify -pl fiscal-service`: 61/61, 0 falhas — os 3 testes desta fatia
  em `TabelaFiscalJdbcTest` fazem parte da mesma rodada que confirmou 3b/3d).
  Novo `fiscal-schema-008.yaml` (incluído em `db.changelog-master.yaml` depois do
  `007`) com dois changesets: `fiscal-024-cria-transicao-ano` cria
  `fiscal.transicao_ano` (`ano int` PK, `pct_remanescente numeric(5,2) NOT NULL`,
  `pis_cofins_vigente boolean NOT NULL`, CHECK `chk_transicao_ano_pct` 0–100); e
  `fiscal-025-seed-transicao-ano` carrega as 8 linhas (2026–2028 = 100,00, 2029 =
  90,00, 2030 = 80,00, 2031 = 70,00, 2032 = 60,00, 2033 = 0,00,
  `pis_cofins_vigente = true` só em 2026). Novo record `TransicaoAno`
  (`pctRemanescente BigDecimal`, `pisCofinsVigente boolean`) e novo método
  `Optional<TransicaoAno> transicao(int ano)` na interface `TabelaFiscal` — vazio
  para ano fora de 2026–2033, sem default (o motor é quem decide devolver 400,
  mesmo princípio da alíquota IBS). Implementado em `TabelaFiscalJdbc` (SELECT por
  ano) e no `TabelaFiscalFake` (as 8 linhas); 3 testes novos em
  `TabelaFiscalJdbcTest` (anos íntegros + PIS/COFINS só em 2026, degraus
  2029–2033, ano fora da curva volta vazio), com DDL/seed adicionados ao fixture
  H2. É só a tabela e o acesso a ela: o `MotorFiscalService` já **consome** a
  curva desde a 3c (26 de agosto de 2026, código-completa mas ainda não verificada
  — ver seção 3c abaixo).
- **3b — matriz ICMS.** ✅ **feita e verde em 18 de agosto de 2026** (`mvn verify -pl
  fiscal-service`: 61/61 testes, 0 falhas — 6 novos em `TabelaFiscalJdbcTest` cobrindo a
  precedência de 4 níveis).
  Novo `fiscal-schema-011.yaml` (incluído em `db.changelog-master.yaml` depois
  do `010`) com dois changesets: `fiscal-030-cria-matriz-tributaria` cria
  `fiscal.matriz_tributaria` com o schema já corrigido pelos ajustes 3, 4 e 5:
  `aliq_nominal` + `p_reducao_base` separados (não a efetiva — a NF-e exige
  `vBC` reduzida e `pICMS` nominal como campos distintos), `ncm_nbs VARCHAR(9)`
  (NBS tem 9 dígitos, NCM 8) + `tipo_item` (`'P'`/`'S'`) para desambiguar,
  `tenant_id UUID` nulável com `UNIQUE NULLS NOT DISTINCT` (PG 17). Diferente
  da `aliq_iss_municipio` (3d), `vigente_de` entra na chave: a pesquisa de
  fonte oficial achou uma mudança real e datada (Alagoas 19%→20,5% em
  01/04/2026, Lei 9.776/2025), então sem `vigente_de` na constraint a linha
  nova colidiria com a histórica ao tentar registrar o reajuste.
  `fiscal-031-seed-matriz-icms-27-ufs` carrega as 27 UFs (fallback
  `'00000000'` = `Constants.FISCAL_NCM_NBS_FALLBACK`, alíquota geral por
  estado, sem override de NCM ou de tenant ainda) — valores levantados via
  WebSearch cruzados em fontes múltiplas, com 3 divergências resolvidas por
  busca dirigida (Alagoas, Sergipe, Mato Grosso do Sul). Novo record
  `RegimeIcms` (`aliqNominal`, `pReducaoBase`, `ncmGenerico`) e novo método
  `Optional<RegimeIcms> aliquotaIcms(String tenantId, String ncmNbs, String
  ufOrigem, String ufDestino)` na interface `TabelaFiscal`, busca em 4 níveis
  (tenant+ncm > tenant+fallback > nacional+ncm > nacional+fallback) já
  especificada acima. Implementado em `TabelaFiscalJdbc` (SQL com `ORDER BY`
  na mesma técnica de `SQL_ALIQ_IBS`/`SQL_ALIQ_ISS`) e no `TabelaFiscalFake`;
  6 testes novos em `TabelaFiscalJdbcTest` (tenant específico vence tudo,
  tenant fallback vence nacional específico, nacional específico vence
  fallback nacional, sem tenant e sem NCM cai no fallback nacional, vigência
  vencida ignorada, sem cobertura nenhuma volta vazio), com DDL/seed
  adicionados ao fixture H2. É só a tabela e o acesso a ela: o
  `MotorFiscalService` já **consome** o resultado desde a 3c (26 de agosto de 2026,
  ver abaixo).
- **3c — motor multiplica.** ✅ **código-completa em 26 de agosto de 2026, ainda NÃO
  verificada** (build/`mvn verify` é sempre do usuário — não rodei nada). `MotorFiscalService`
  ganhou `calcularLegado(...)`: ICMS (produto) ou ISS (serviço) proporcional ao
  `pctRemanescente` da transição — nunca os dois juntos, produto x serviço são mutuamente
  exclusivos desde o PASSO 0. Serviço busca `TabelaFiscal.aliquotaIss(...)`; produto busca
  `TabelaFiscal.aliquotaIcms(...)` (exige `ufOrigem`/`ufDestino`, senão 400
  `FISCAL_UF_OBRIGATORIA_TRANSICAO`). PIS/COFRINS — PIS/COFINS (vigente só em 2026) fica de
  fora: sem tabela de alíquota carregada, o motor só avisa
  (`Constants.FISCAL_AVISO_PIS_COFINS_APURACAO_EXTERNA`, log + `memoriaCalculo`; ver item 7.9) em vez de calcular.
  `pctRemanescente = 0` (2033) ou ano fora da curva pulam o legado sem exigir UF. `OperacaoFiscalDTO`
  ganhou `valorIcms`/`valorIss` (mutuamente exclusivos com o novo bloco de retenção da 3e,
  abaixo). 6 testes novos em `MotorFiscalServiceTest` (ICMS em 2029, ISS em 2029, transição
  zero não calcula nenhum, produto sem UF com transição ativa → 400, ICMS sem cobertura → 400,
  ano fora da curva 2026–2033 → 400).
- **3d — ISS.** ✅ **feita e verde em 18 de agosto de 2026** (confirmado na mesma
  rodada de `mvn verify -pl fiscal-service` que fechou a 3b: 61/61 testes totais,
  30 em `TabelaFiscalJdbcTest`, incluindo os 4 de ISS; Liquibase aplicado e
  conferido pelo usuário). Tabela própria por
  `ibge_municipio` + item da LC 116 (ajuste nº 2: ISS não é por UF). É a perna legada
  de quem vende **serviço**, que é o mercado-alvo. Continua sendo o pior dado de
  todos — 5.570 municípios legislando cada um o seu —, e por isso entra com default
  5% (teto da LC 116 art. 8-A) + override por município, em vez de esperar carga
  completa. Novo `fiscal-schema-010.yaml` (incluído em `db.changelog-master.yaml`
  depois do `009`) com dois changesets: `fiscal-028-cria-aliq-iss-municipio` cria
  `fiscal.aliq_iss_municipio` (`ibge_municipio varchar(7)`, `item_lc116 varchar(5)`
  nulável — NULL vale para qualquer item —, `aliquota_pct numeric(5,2)` com CHECK
  `chk_aliq_iss_municipio_pct` 2–5, `vigente_de`/`vigente_ate`, UNIQUE
  `(ibge_municipio, item_lc116)`); `fiscal-029-seed-aliq-iss-referencia-nacional`
  carrega só a linha sentinela (`'0000000'`, item NULL, 5,00%) — nenhum município tem
  alíquota própria carregada ainda. Novo record `AliquotaIss` (`aliquotaPct
  BigDecimal`, `referenciaNacional boolean`, mesmo desenho de `AliquotaIbs`) e novo
  método `Optional<AliquotaIss> aliquotaIss(String ibgeMunicipio, String itemLc116)`
  na interface `TabelaFiscal`. Implementado em `TabelaFiscalJdbc` (SQL com a mesma
  precedência item próprio > genérico do município > referência de `SQL_ALIQ_IBS`,
  mais `lpad` do item como em `cClassTribAdmitido`) e no `TabelaFiscalFake`; 4 testes
  novos em `TabelaFiscalJdbcTest` (item próprio vence genérico e referência, genérico
  do município vence referência, sem cadastro nenhum cai na referência, item aceita
  com ou sem zero à esquerda), com DDL/seed adicionados ao fixture H2. Junto dela vem
  o **local da prestação**: quem
  decide se o ISS é do prestador ou do tomador é a LC 116 art. 3º, com ~20 exceções,
  e o motor já recebia `ibgeLocalPrestacao` pronto desde antes desta fatia (usado
  hoje para IBS de serviço) — se quem chama errar, o cálculo sai certo para o
  município errado. O `MotorFiscalService` já **consome** o resultado desde a 3c
  (26 de agosto de 2026, ver abaixo).
- **3e — retenção na fonte** (decidida em 30 de julho de 2026). ✅ **código-completa em
  26 de agosto de 2026, ainda NÃO verificada** (`mvn verify` não rodado — build é sempre
  do usuário). `MotorFiscalService` ganhou `calcularRetencao(...)`: ISS retido, IRRF, CSRF
  (PIS/COFINS/CSLL combinados) e INSS, cada um só quando **declarado** no request
  (`issRetidoNaFonte`/`reterIrrf`/`reterCsrf`/`reterInss` — mesmo padrão "declarado, não
  deduzido" do `cClassTrib`; nunca inferido). Produto + qualquer flag de retenção ⇒ 400
  `FISCAL_RETENCAO_APENAS_SERVICO` (retenção é só de serviço). Piso de dispensa usa `<=`
  uniformemente nos três (CSRF/INSS comparam o piso contra o `valorTributavel` da operação;
  IRRF soma `valorAcumuladoMesIrrf` + o calculado desta operação contra o piso, mas retorna
  só a parcela calculada desta operação — sem ajuste retroativo no mês, simplificação
  deliberada). ISS retido usa a alíquota municipal já calculada na 3c/3d (dependência
  cumprida). Nova tabela `fiscal.retencao_config` (`fiscal-schema-012.yaml`,
  changesets `fiscal-032`/`fiscal-033`) com override por tenant, 2 níveis de precedência
  igual ao ISS (`tenant > nacional`), seed nacional de IRRF (1,50%, piso 10,00),
  CSRF (4,65%, piso 5.000,00) e INSS (11,00%, piso 0). Novo método
  `Optional<AliquotaRetencao> retencao(String tenantId, String tributo)` em `TabelaFiscal`,
  implementado em `TabelaFiscalJdbc` e `TabelaFiscalFake`. 4 testes novos em
  `TabelaFiscalJdbcTest` (sem tenant traz nacional, tenant com override vence, tenant sem
  override cai no nacional, sem cobertura volta vazio) e 8 em `MotorFiscalServiceTest`
  (nenhuma flag → tudo nulo, retenção em produto → 400, ISS retido = ISS legado, IRRF sobre
  valor tributável, IRRF piso mensal dispensa sem acúmulo e retém com acúmulo, CSRF acima do
  piso retém, CSRF no piso ou abaixo dispensa, INSS sem piso sempre retém).

---

## 4. Crédito de entrada (Fin.md §1.4.3) ✅ FEITO (27 de agosto de 2026)

`CfopInfo` já carregava `gera_credito_ibs` e `gera_credito_cbs`, mas nada
consumia esses campos — o motor só fazia saída.

**Decisão (29 de julho de 2026): o crédito é persistido pelo `operacoes-service`
(AP), e o `fiscal-service` continua só calculando.** O crédito nasce de um
documento de entrada, que é registro do P2P; um segundo dono de escrita para o
mesmo fato só criaria divergência. Fim da dúvida de escopo — o fiscal **não** ganha
schema de escrita.

O que cabia ao `fiscal-service` (era o último código de motor que faltava) foi
implementado:

- `MotorFiscalService.calcular()` resolve o CFOP no início do fluxo e ramifica
  por `TipoOperacaoFiscal`: `SAIDA` segue o fluxo existente, `ENTRADA` vai para
  o novo `calcularCredito(...)` — não existe mais 400 para CFOP de entrada (a
  constante `FISCAL_CFOP_INVALIDO_SAIDA` foi removida do `common/Constants.java`);
- `calcularCredito` lê `gera_credito_ibs` / `gera_credito_cbs` do `CfopInfo` e
  devolve `valorCreditoIbs` / `valorCreditoCbs` em `OperacaoFiscalDTO` (campos
  novos, `null` em saída) com memória de cálculo citável; IS **nunca** gera
  crédito (é monofásico, cumulativo por desenho);
- aplica as **vedações**, declaradas pelo chamador em `MotorFiscalRequest`
  (campos novos `usoConsumoPessoal` e `percentualSaidaDesonerada`, mesmo padrão
  de `cClassTrib`/retenção — o motor não deduz intenção): uso e consumo pessoal
  zera o crédito integralmente (`semCredito`); entrada cujo destino é saída
  desonerada credita só o complemento (reaproveita `fatorReducao`, já usado no
  cálculo de saída); CFOP sem `gera_credito_ibs`/`gera_credito_cbs` zera por
  conta própria, independente das flags do request;
- devolve o valor calculado no mesmo formato determinístico da saída, com
  memória de cálculo — quem grava continua sendo o AP.
- bugfix lateral: o guard de split payment (`splitLigado && aplicavel == null`
  → 400) virou `!entrada && splitLigado && ...` — antes, com split ligado para
  o tenant, uma entrada exigia `splitPaymentAplicavel` indevidamente (split é
  conceito de saída).

O que cabe ao `operacoes-service` (AP): guardar o documento de entrada, o saldo de
crédito, o que já foi aproveitado e em qual período — nada disso passa pelo fiscal.

**Não dependeu do item 3.** O único cruzamento é a transição (crédito
de ICMS na entrada convive com crédito de IBS no mesmo período) — e como o legado
não é calculado, e sim parametrizado/transportado, o crédito legado também só é
transportado. Os dois itens seguem podendo andar em qualquer ordem.

**Testes:** `MotorFiscalServiceTest` ganhou 5 casos novos
(`entrada_creditaIbsECbsIntegral`, `entrada_usoConsumoPessoal_naoGeraCredito`,
`entrada_saidaDesonerada_creditaProporcional`, `entrada_cfopSemDireitoACredito_zeraCredito`,
`entrada_comSplitLigado_naoExigeFormaPagamento`), substituindo o antigo
`cfopDeEntrada_lancaFiscalException` (testava o 400 removido). `TabelaFiscalFake`
ganhou o CFOP `1556` (entrada sem direito a crédito). **✅ Confirmado verde pelo
usuário (27 de agosto de 2026)** via `mvn verify -pl fiscal-service -am`.

---

## 5. Apuração mensal (emissão fora do escopo inicial)

**Decisão (29 de julho de 2026): o ERP não vai emitir nota inicialmente.** Então
este item se reduz a **apuração**: consolidar débito − crédito por período e por
estabelecimento. Emissão NF-e/NFC-e/NFS-e sai do plano de curto prazo — o que
também tira do caminho a dependência de `spec/estabelecimentos-filiais.md`
(planejado, não iniciado: não existe entidade de estabelecimento para figurar como
emitente).

Apuração **depende do item 4** (sem crédito não há o que subtrair do débito) e
**não depende do item 3**: o lado legado entra na apuração como valor
parametrizado, não calculado.

**Dono: `operacoes-service`, não o fiscal.** Apuração é soma de valores já
persistidos (débito das saídas do AR − crédito das entradas do AP, por período e
por estabelecimento) — não precisa de motor, precisa de quem tem os dados. Com o
fiscal sendo só cálculo, ele **não participa deste item**: nada de
`calcularEPersistir` nem `recalcularPeriodo` (a `spec/Fin.md` §1.4.10 já registra
que não existem). Se algum dia a apuração exigir regra de lei em vez de soma
(compensação entre tributos, ordem de aproveitamento), aí sim entra um endpoint
puro no fiscal recebendo os movimentos e devolvendo o resultado — sem gravar.

Consequência de não emitir: o prazo do item 3 deixa de ser fatal — a matriz não é
mais o que separa o ERP de "documento válido em 2027–2032". Continua necessária
para o número total de imposto ficar certo em formação de preço, contas a receber
e DRE durante a transição, mas pode vir depois do item 4.

---

## 6. Documentação desatualizada ✅ FEITO (29 de julho de 2026)

Rápido, sem dependência de nada acima:

- ✅ `spec/casos-teste-motor-fiscal.md` — descrevia serviço classificado pelo
  código LC 116; agora documenta a classificação por `cClassTrib` declarado
  (o item LC 116 só valida o par do Anexo VIII). Também corrigidos: nome do
  oráculo (`TabelaFiscalFake`, não `TabelaFiscalInMemory`), nomes reais de
  `regimeAplicado` (`ANEXO_I_ZERO` / `ANEXO_III_60`, não `CESTA_BASICA` /
  `REDUCAO_60`), casos D1–D3 com `cClassTrib`, novos G9/G10 e resumo de
  cobertura (16 métodos).
- ✅ `spec/Fin.md` — §1.4.10 ganhou o campo `cClassTrib`, a assinatura real
  `calcular(request, tenantId)` e nota de que `calcularEPersistir` /
  `recalcularPeriodo` ainda não existem; §1.4.9 ganhou os cinco 400 novos
  (`CFOP_INVALIDO_SAIDA`, `NCM_OU_SERVICO_OBRIGATORIO`,
  `NCM_E_SERVICO_CONFLITANTES`, `CCLASSTRIB_OBRIGATORIO`,
  `CCLASSTRIB_INVALIDO_PARA_SERVICO`) e a nota de que os fallbacks
  parametrizados da §1.9 ainda não estão implementados.

- ✅ `spec/fontes-dados-fiscais.md` — descrevia o motor como in-memory
  (`TabelaFiscalInMemory`) e a migração para dados reais como plano futuro, com
  `TabelaFiscalJpa` como destino. Atualizado para o estado real: conteúdo em
  `fiscal.*` via `TabelaFiscalJdbc`, itens 4/5/6/8 carregados, e o que sobrou de
  coleta pendente (IS, curva de alíquotas, 20 dos 27 `cClassTrib`).

Fechado junto (código de teste): o caso **D3** virou o teste
`ex5_servico_cclassTribIntegral_semReducao` — o `TabelaFiscalFake` ganhou o par
`1.01 × 000001` e o regime `INTEGRAL` (redução 0), espelhando o `fiscal-019`.
Detalhe que o doc errava: `000001` **tem** linha em `fiscal.regime_cclasstrib`,
então sai `INTEGRAL`, não PADRAO — os dois tributam cheio, mas PADRAO significa
dado fiscal faltando e INTEGRAL é classificação declarada.

---

## 7. Inventário do que falta no motor (levantado em 29 de julho de 2026)

Os itens 2 a 6 acima são as fatias grandes. Esta seção é o resto — o que sobra
quando se olha o motor inteiro, e que não estava escrito em lugar nenhum. Os três
últimos de código (7.12, 7.13 e 7.14) saíram de **ler o código**, não de spec.

### Dado (não tem decisão, é carga)

- **7.1 Alíquotas IBS por município × ano.** ✅ **feito (30 de julho de 2026, `fiscal-023`,
  não testado).** Descoberta que destravou: o portal do piloto CBS tem **API de dados
  abertos** (`/dados-abertos/aliquota-uniao|aliquota-uf|aliquota-municipio?data=…`,
  resposta `{"aliquotaReferencia": n}`) — a carga não é digitação, é GET. O
  `fiscal-022-aliquotas-reais-portal-cbs` semeou 4 municípios (Arcos/MG `3104205`,
  Formiga/MG `3126109`, Suzano/SP `3552502`, São Paulo/SP `3550308`) × 2026 a 2033; o
  `fiscal-023-aliquota-ibs-referencia-nacional` **removeu essas 4 linhas** (eram cópia
  da referência, não alíquota própria) e inseriu **uma linha nacional por ano**
  (`ibge_municipio = '0000000'`) no lugar, 2026 a 2033. `TabelaFiscalJdbc.SQL_ALIQ_IBS`
  agora busca a linha do município OU a sentinela, preferindo a própria quando existir —
  `AliquotaIbs` ganhou o terceiro componente `referenciaNacional`, e o
  `MotorFiscalService` emite `Constants.FISCAL_AVISO_ALIQUOTA_REFERENCIA` (WARN +
  `memoriaCalculo`) quando cai nela, sem mudar o cálculo. **Qualquer município do
  Brasil calcula IBS hoje, para 2026–2033**; `FISCAL_VIGENCIA_SEM_COBERTURA` só
  sobra para ano fora dessa curva (ex. 2035). Carregar os ~5.570 municípios via API
  virou **conferência** (uniformidade + captura de alíquota própria), não carga
  bloqueante. Endpoints e o gotcha de rate limit em `spec/fontes-dados-fiscais.md`.
- **7.2 CBS por regime × ano.** ✅ **feito (idem).** `aliq_cbs_regime` tem os três
  regimes de 2026 a 2033: 0,9% em 2026, 8,4% em 2027-2028 e 8,5% de 2029 em diante.
- **7.3 Curva 2026–2033.** ✅ **feita, toda real.** 2029–2032 são 10/20/30/40% do
  regime permanente, conferidos ano a ano no portal. Nota importante: a alíquota da
  transição é **simbólica** (IBS de 0,1% em 2026, 0,05%+0,05% em 2027–2028), então o
  oráculo de teste e os exemplos do `Fin.md` §1.4.8 usam **2033** — que também é real
  (IBS 16,00% + 2,50%, CBS 8,50%; total 27%), e não mais a estimativa 13,12+4,50/8,80
  do `fiscal-010`, sobrescrita pelo `fiscal-022`.
- **7.4 `aliq_is_ncm`.** Uma linha de exemplo; depende de lei ordinária. Quando
  sair, precisa da lista de NCM sujeitos ao IS (cigarro, bebida, veículo, mineral)
  com as alíquotas.
- **7.5 240 itens de anexo de produto**, pendentes em
  `spec/anexos-lc214-revisar.md`.
- **7.6 Lista de vedação de crédito** (art. 57, uso e consumo pessoal): não existe
  tabela nenhuma. Pré-requisito do item 4 se a vedação não vier declarada pelo AP.

### Modelo (o schema não expressa)

- **7.7 `percentual_reducao` é um valor único para IBS e CBS.** Não expressa
  redução por tributo (art. 308: Prouni zera só a CBS) nem alíquota em valor
  absoluto (art. 233: serviços financeiros, soma fixa de 10,85%). São exatamente os
  2 `cClassTrib` que ficaram fora do `fiscal-021`. Correção: coluna por tributo ou
  tabela de alíquota absoluta.
- **7.8 Vigência.** Nem alíquota nem regime têm `vigencia_inicio`/`vigencia_fim`; o
  ano está solto na chave. Dói na transição, quando o mesmo NCM muda de percentual
  de um ano para o outro.

### Código

- **7.9 Ano de teste 2026** ✅ **resolvido — não era código faltando (27 de agosto de
  2026).** Pesquisa no texto da LC 214/2025 (art. 348, cruzado em duas fontes)
  mudou o entendimento: o contribuinte que cumprir as obrigações acessórias de
  2026 (§1º) fica **dispensado** do recolhimento de IBS/CBS — não há valor a
  compensar, porque não há cobrança. O §2º exige o PIS/COFINS **integral**, sem
  desconto, do mesmo jeito de sempre. A "compensação com PIS/COFINS" só existe
  para quem **descumprir** a obrigação acessória e for cobrado do IBS/CBS
  simbólico — aí sim o valor pago vira crédito contra PIS/COFINS (ou outro
  tributo federal, ou ressarcimento em 60 dias). Isso é apuração
  multi-competência de quem furou o prazo, não cálculo de uma nota isolada — cai
  fora do `fiscal-service` pelo mesmo motivo do item 5 (apuração é do
  `operacoes-service`). O motor já calcula IBS 0,1% e CBS 0,9% de 2026 pelas
  tabelas existentes (`aliq_cbs_regime`/`fiscal-023`, sem tabela nova); só a
  constante e o comentário estavam desatualizados — renomeada
  `FISCAL_AVISO_PIS_COFINS_SEM_DADO` → `FISCAL_AVISO_PIS_COFINS_APURACAO_EXTERNA`
  em `common/Constants.java`, mensagem cita o art. 348 em vez de insinuar dado
  faltando. Teste novo em `MotorFiscalServiceTest`
  (`legado_2026_avisaPisCofinsApuracaoExterna`) cobre o caminho de 2026, que
  antes não tinha teste nenhum.
- **7.10 Fallbacks parametrizados da `spec/Fin.md` §1.9** — especificados, não
  implementados (a §1.4.9 do Fin.md já registra isso).
- **7.11 Split payment é só o valor.** Liquidação real e integração com a
  Plataforma Pública não existem — declarado como fatia futura no próprio código.
- **7.12 `origemProduto` era ignorado** ✅ **avisado (30 de julho de 2026, verde).** O tratamento da ZFM (LC 214) **continua não implementado** — é
  pesquisa —, mas deixou de ser silencioso: `origemProduto = 'ZFM'` gera `WARN` de
  uma linha e linha na `memoriaCalculo` (`Constants.FISCAL_AVISO_ORIGEM_ZFM`)
  dizendo que o item foi tributado como nacional. Mesmo padrão do aviso de `PADRAO`.
  O aviso sai **antes** dos retornos de MEI e de alíquota zero, então vale para
  todos os caminhos. Falta ainda a regra fiscal da ZFM propriamente.
- **7.13 `tipoDocumento` era ignorado** ✅ **validado (30 de julho de 2026, verde).** No PASSO 0: `NFSe` com `ncm`, ou `NFe`/`NFCe` com `codigoServico` ⇒ 400
  `FISCAL_TIPO_DOCUMENTO_INCOMPATIVEL`. `CTe` fica **fora** da regra (o motor não
  trata transporte) e o campo segue opcional — quem não manda não é afetado.
- **7.14 O motor não compunha a base de cálculo** ✅ **resolvido (30 de julho de
  2026, verde).** Decisão tomada: **o request recebe os componentes e o motor
  compõe** — a alternativa (registrar que a composição é do chamador) deixaria a
  memória de cálculo começando num número que o motor não sabe explicar, e a NF-e
  exige os campos separados de qualquer forma. Como ficou:
  - `MotorFiscalRequest` ganhou `valorDesconto`, `valorFrete`, `valorSeguro` e
    `valorOutrasDespesas`, **todos opcionais** e `@PositiveOrZero` (negativo é 400
    de bean validation). Request antigo continua válido: sem nenhum componente, o
    `valorOperacao` **é** a base, exatamente como antes.
  - PASSO 0.5 no `MotorFiscalService`: `tributável = operação + frete + seguro +
    acessórias − desconto incondicional` (LC 214 art. 12, §2º). Roda **antes** dos
    retornos de MEI e de alíquota zero, que também devolvem `baseCalculo`.
  - O **IS incide sobre a base já composta** e continua integrando a base do
    IBS/CBS (`base = tributável + IS`).
  - `tributável <= 0` ⇒ 400 `FISCAL_DESCONTO_MAIOR_QUE_OPERACAO`: desconto não zera
    nem inverte a operação.
  - Auditoria: linha `Constants.FISCAL_MEMORIA_BASE_COMPOSTA` na `memoriaCalculo`
    com os seis valores, emitida **só** quando algum componente vem (sem eles seria
    ruído).
  - Fica de fora, deliberadamente: **desconto condicional** (não reduz base, então
    não entra no request) e rateio de frete/desconto de cabeçalho por item — isso é
    do AR/O2C, que chama o motor **por item**.
- **7.15 Devolução, nota de crédito e ajuste** — nada existe. Estorno de crédito e
  cancelamento também não.

### Qualidade

- **7.16 `TabelaFiscalFake` não tinha CFOP de entrada** ✅ **resolvido (30 de julho de
  2026, verde).** Ganhou o `1102` (ENTRADA, gera crédito de IBS e de CBS, não é
  1ª etapa — mesmas flags do `cfop.csv`). Serviu de oráculo direto para o item 4
  (crédito de entrada, ✅ feito em 27/08/2026): `1556` (sem crédito) se juntou a
  ele quando a fatia chegou.
- **7.17 Não testado** ✅ **rodado e verde (30 de julho de 2026).** `fiscal-021` aplicado
  via `./mvnw spring-boot:run -pl liquibase-service`, e `./mvnw verify -pl fiscal-service`
  com **41 testes / 0 falhas** (22 em `MotorFiscalServiceTest`, 15 em `TabelaFiscalJdbcTest`,
  4 em `MotorFiscalControllerTest`) — cobre o aviso de `PADRAO` e os itens 7.12, 7.13 e 7.16.
  Os dois `WARN` novos apareceram no log da execução, como esperado.
  Segunda rodada, mesmo dia: `fiscal-023` aplicado e suíte verde de novo, agora com os três
  testes da referência nacional (2 em `TabelaFiscalJdbcTest` — fallback e precedência da
  alíquota própria — e o caso H3 em `MotorFiscalServiceTest`). A fusão de
  `FISCAL_MUNICIPIO_SEM_ALIQUOTA_IBS` em `FISCAL_VIGENCIA_SEM_COBERTURA` veio **depois**
  dessa rodada: é troca de uma constante sem asserção em teste, mas ainda não recompilada.

---

## Ordem sugerida (dentro deste doc)

6 ✅ → 2 ✅ → 4 ✅ → 3 ✅ → **5** (apuração, e ela já não é do fiscal).

6, 2, 4 e 3 estão fechados (2 e 4 código-completos, não testados por mim; 3
confirmado verde por `mvn verify` do usuário; 6 é doc). O item 5 saiu do escopo
do `fiscal-service` (vira trabalho do `operacoes-service`) — não sobra mais
nenhum item de motor de cálculo pendente neste doc, só apuração/persistência
no `operacoes-service`.

Com 6, 2, 3 e 4 fechados, o que ainda separa o motor de "pronto" fora do item 5 é
**dado**, não código: alíquota IBS existe para todo município via referência nacional
2026–2033 (`fiscal-023`, aplicado e verde; só ano fora dessa curva devolve 400
`FISCAL_VIGENCIA_SEM_COBERTURA`), `aliq_is_ncm` tem uma linha de exemplo e depende
de lei ordinária, e sobram os 240 itens de anexo de produto em
`spec/anexos-lc214-revisar.md`.

---

## Próximos passos gerais (fora do motor)

Onde o motor fiscal para e o resto do ERP começa. O `fiscal-service` está
**funcionalmente fechado para saída e entrada** — o que falta é carga de dado
(IS, anexos de produto), não arquitetura. O inventário fino está no item 7; dos três
itens de lá que mexem no **contrato** e valiam ser resolvidos antes de o AR chamar o
motor, **os três estão fechados**: 7.12 (`origemProduto` — avisa em vez de ignorar),
7.13 (`tipoDocumento` — valida coerência) e 7.14 (o motor compõe a base a partir de
desconto/frete/seguro/acessórias, campos opcionais novos no request — 30 de julho de
2026, **verde**). O contrato de entrada do motor está estável para o AR.

**1. Conferência das alíquotas de todos os municípios — deixou de bloquear.**
Até 29/07/2026 isto travava tudo: só 4 municípios (Arcos, Formiga, Suzano, São Paulo)
tinham linha própria e qualquer outro devolvia 400. Com o
`fiscal-023-aliquota-ibs-referencia-nacional` (30/07/2026, **não testado**) — uma linha
nacional por ano (`ibge_municipio = '0000000'`) em `fiscal.aliq_ibs_municipio`, que o
motor usa quando não há linha própria do município — **qualquer município do Brasil
já calcula IBS para 2026–2033**. Deixou de ser item nº 1 de prioridade: o que resta é
um job/script que varra os ~5.570 códigos IBGE **espaçando as chamadas** (o WAF corta
em ~15 requisições em rajada; ~5s entre elas passou liso) e persista incrementalmente,
usando a API de dados abertos do portal do piloto CBS
(`/dados-abertos/aliquota-municipio?data=…&codigoMunicipio=…` → `{"aliquotaReferencia": n}`,
documentada em `spec/fontes-dados-fiscais.md`). Como a alíquota de referência é uniforme
por tipo de ente, a varredura serve para **confirmar** a uniformidade e pegar quem tiver
alíquota própria (essa linha vence a referência automaticamente) — não para descobrir
5.570 valores diferentes. Pode rodar depois, sem pressa.

**2. AR / O2C no `operacoes-service`** — é o próximo módulo, e o mais barato: usa o
motor de saída que já existe e está testado, sem escrever uma linha de fiscal.
Orçamento → pedido → expedição → faturamento (`spec/o2c-vendas.md`). É também o que
valida o motor com dado de verdade, em vez de `curl`.

**3. Fatia de entrada do motor** (item 4 acima, lado fiscal) — pequena, e
pré-requisito do AP. Pode ser feita em paralelo com o AR ou imediatamente antes do
AP; não faz sentido antes disso, porque não haveria quem consumisse o crédito.

**4. AP / P2P no `operacoes-service`** — requisição → cotação → pedido →
recebimento → NF de entrada (`spec/p2p-compras.md`). É quem passa a persistir o
crédito calculado no passo 3.

**5. Apuração** (item 5 acima) — só faz sentido com AR e AP existindo, e roda no
`operacoes-service`, sobre valores já gravados.

**6. Matriz da transição** (item 3 acima) — necessária para o imposto total ficar
certo em 2026–2032 (formação de preço, DRE), mas não bloqueia AR nem AP: até ela
existir, os documentos carregam só o lado novo.

**7. Emissão** (NF-e/NFC-e/NFS-e) — fora do escopo inicial por decisão. Quando
voltar, depende de `spec/estabelecimentos-filiais.md` (emitente) e da matriz do
passo 6.

Resumindo em uma linha: **o motor de saída está pronto; falta ir para o AR (a
conferência de alíquotas por município roda em paralelo, sem bloquear), depois a
fatia de entrada e o AP.**
