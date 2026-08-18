# Fontes de Dados Fiscais — Checklist de Coleta (Motor Fiscal)

**Última atualização:** 18 de agosto de 2026
**Contexto:** o motor fiscal (`fiscal-service`, Fin.md Módulo I) **já lê do banco** — `TabelaFiscalJdbc`
consulta o schema `fiscal.*` (JdbcClient; não há JPA aqui). O que era estimativa em memória virou
conteúdo carregado por Liquibase. Este doc é o roteiro de coleta: o que já está dentro, com que
cobertura, e o que ainda depende de fonte oficial.

Legenda de **Bloqueio**:
- ✅ **Carregado** — já está no banco por changeset Liquibase; o motor lê de lá.
- 🔴 **Duro** — sem isso não há alíquota/enquadramento correto; depende de publicação oficial ainda pendente.
- 🟡 **Médio** — fonte **em mãos** (arquivo em `spec/` ou portal público), só dá trabalho extrair/importar.
- 🟢 **Fácil / já temos** — dado em mãos ou trivial.

> **Mudança 24/07/2026:** as fontes oficiais foram coletadas para `spec/`. Só o **IS (item 3)** segue 🔴
> (regulamentação por NCM ainda não publicada). Todo o resto passou de 🔴 para 🟡 (extrair/importar) ou 🟢.

> **Mudança 29/07/2026 — a extração aconteceu.** Estão no banco: **15.156** NCM (`fiscal-012`),
> **629** CFOP (`fiscal-014`), **242** códigos NCM/SH com regime da LC 214 (`fiscal-016`, recarga do
> `fiscal-015`), **895** pares LC 116 × NBS e **246** pares LC 116 × cClassTrib do Anexo VIII
> (`fiscal-018`). O que sobrou de pendência mudou de natureza: não é mais "extrair o PDF", é
> **cobertura** — alíquotas semeadas só para São Paulo/2027 e IS só cigarro.
>
> **Mudança 29/07/2026 (mais tarde) — `fiscal-021`:** os 20 `cClassTrib` que o `fiscal-019` deixou
> sem percentual foram fundamentados artigo por artigo na LC 214; **18 entraram** (25 dos 27 agora
> têm redução). Só `010002` (serviços financeiros) e `200025` (Prouni) ficaram fora, porque
> `percentual_reducao` é único para IBS e CBS e esses dois pedem alíquota absoluta / redução só de
> CBS — detalhe em `spec/anexos-lc214-revisar.md`.
>
> **Mudança estrutural:** regime de **serviço** saiu de `regime_dif_ncm` e virou `regime_cclasstrib`
> (`fiscal-020`). A classificação de um serviço vem do **`cClassTrib` declarado**, não do código
> LC 116 — o mesmo serviço muda de regime conforme o contexto (à administração pública vira `200043`).
> O item LC 116 só valida se o par é admitido pelo Anexo VIII (`servico_cclasstrib`).

> **Mudança 18/08/2026 — dois tributos legados entraram no banco (itens 11 e 12, novos nesta lista).**
> `fiscal.aliq_iss_municipio` (fatia 3d, `fiscal-028`/`fiscal-029`) e `fiscal.matriz_tributaria` (fatia 3b,
> `fiscal-030`/`fiscal-031`) existem e são consultáveis via `TabelaFiscal.aliquotaIss`/`aliquotaIcms`. **Testado
> e verde:** o usuário rodou `mvn verify -pl fiscal-service` e confirmou 61/61 testes passando (30 em
> `TabelaFiscalJdbcTest`, cobrindo a precedência de 4 níveis tenant×NCM/fallback e nacional×NCM/fallback do
> ICMS). Igual às fatias 1/2/7 acima, nenhuma das duas é consumida pelo motor ainda — o passo de ICMS/ISS
> legado no `POST /fiscal/calcular` é a fatia 3c, que segue pendente.

**O que ainda não está resolvido, e por quê:**
- **Itens 1, 2 e 7 (alíquotas por ente/ano):** o [portal][portal] devolve por ente/ano e a curva é **fixada ano a
  ano** pelo Senado — não é arquivo único, é **carga recorrente**. **Deixou de ser coleta manual em
  30/07/2026:** o portal expõe **API de dados abertos** (ver abaixo), que é a fonte dos changesets
  `fiscal-022-aliquotas-reais-portal-cbs` e `fiscal-023-aliquota-ibs-referencia-nacional` (este último,
  **não testado**). O `fiscal-023` removeu as 32 linhas municipais do `fiscal-022` (eram cópia da
  referência, não alíquota própria) e inseriu **uma linha nacional por ano** (`ibge_municipio =
  '0000000'`) em `aliq_ibs_municipio`, 2026 a 2033 — o motor busca a linha do município OU a sentinela,
  preferindo a própria quando existir. **Qualquer município do Brasil calcula IBS hoje, para
  2026–2033**, com `Constants.FISCAL_AVISO_ALIQUOTA_REFERENCIA` (WARN + memória de cálculo) avisando
  quando cai na referência. Fora dessa curva (ex. 2035) segue 400 (`FISCAL_VIGENCIA_SEM_COBERTURA` /
  `FISCAL_REGIME_SEM_ALIQUOTA_CBS`). Carregar os ~5.570 municípios do IBGE deixou de ser bloqueio e virou
  **conferência**: confirma a uniformidade e captura entes que publicarem alíquota própria.
- **Item 3 (IS):** sem tabela oficial. Só cigarro (`2402.20`, 150%) e é estimativa.
- **Item 10 (redução por cClassTrib):** resolvido para 25 dos 27 (`fiscal-019` + `fiscal-021`). Faltam só
  `010002` e `200025`, que o modelo de um percentual único para IBS e CBS não expressa.
- **Item 9 (split):** a feature flag existe (`fiscal.split-payment`, default off, allowlist por tenant); o que
  falta é a **integração com o PSP/Plataforma Pública**, que não é carga de dado.

Os itens 4, 5, 6 e 8 saíram desta lista: foram extraídos e carregados (ver mudança de 29/07/2026).

---

## Resumo por prioridade

| # | Dado | Fonte (em mãos) | Tabela-alvo | Bloqueio |
|---|---|---|---|---|
| 1 | Alíquotas de referência IBS/CBS por ano (curva de transição 2026–2033) | [API de dados abertos][portal-api] | `fiscal.aliq_ibs_municipio`, `fiscal.aliq_cbs_regime` | ✅ **2026–2033 real** |
| 2 | Alíquotas IBS por ente (união/estado/município) | [API de dados abertos][portal-api] | `fiscal.aliq_ibs_municipio` | ✅ **referência nacional, 2026–2033** |
| 3 | Alíquotas do IS (Imposto Seletivo) por NCM | **ainda sem tabela oficial** | `fiscal.aliq_is_ncm` | 🔴 |
| 4 | De-para NCM → regime (Anexos LC 214/2025) | `spec/leicomplementar-214-...-pl.pdf` | `fiscal.regime_dif_ncm` | ✅ 242 códigos |
| 5 | Tabela NCM (descrições) | `spec/Tabela_NCM_..._20260702.csv` | `fiscal.ncm` | ✅ 15.156 |
| 6 | Tabela CFOP (geradoras de crédito) | `spec/Tabela_CFOPOperacoesGeradorasCreditos.xlsx` | `fiscal.cfop` | ✅ 629 |
| 7 | Códigos de município IBGE | IBGE (público) | referência (`fiscal.aliq_ibs_municipio.ibge_municipio`) | 🟡 **4 de ~5.570** |
| 8 | Lista de serviços LC 116/2003 + Anexo VIII | `spec/LC1162003.pdf` + LC 214 Anexo VIII | `fiscal.servico_nbs`, `fiscal.servico_cclasstrib` | ✅ 895 + 246 pares |
| 9 | Regras de Split Payment | `spec/03153733-manual-de-integracao-...v1.pdf` + `spec/30145925-minuta-split-payment-manual-de-operacoes.pdf` | lógica (`splitPaymentAplicavel`) + contrato PSP | 🟡 flag feita, PSP não |
| 10 | **Percentual de redução por cClassTrib** | LC 214, artigos fora do Anexo VIII | `fiscal.regime_cclasstrib` | 🟡 **25 de 27** |
| 11 | Alíquota de ISS (legado) por município e item LC 116 | LC 116 art. 8-A (teto) | `fiscal.aliq_iss_municipio` | ✅ **referência nacional (teto 5%), testada e verde** |
| 12 | Alíquota interna de ICMS (legado) por UF | WebSearch, múltiplas fontes cruzadas | `fiscal.matriz_tributaria` | ✅ **27 UFs, testada e verde** |

[portal]: https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/calculadora/aliquotas
[portal-api]: https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/api/calculadora/dados-abertos/aliquota-municipio?data=2026-07-30&codigoMunicipio=3104205

### API de dados abertos do piloto CBS (fonte dos itens 1, 2 e 7)

Base: `https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/api/calculadora/dados-abertos`.
GET sem autenticação, resposta `{"aliquotaReferencia": <número>}` (percentual, não fração). `data` é a
**data do fato gerador** — a alíquota é buscada pela vigência, não pelo ano isolado:

| Ente | Endpoint |
|---|---|
| União (CBS) | `/aliquota-uniao?data=2026-07-30` |
| UF (IBS estadual) | `/aliquota-uf?data=2026-07-30&codigoUf=31` |
| Município (IBS municipal) | `/aliquota-municipio?data=2026-07-30&codigoMunicipio=3104205` |

Valores obtidos em 30/07/2026 (carregados pelo `fiscal-022-aliquotas-reais-portal-cbs`):

| Ano do fato gerador | União/CBS | UF | Município |
|---|---|---|---|
| 2026 | 0,9% | 0,1% | 0,0% |
| 2027 | 8,4% | 0,05% | 0,05% |
| 2028 | 8,4% | 0,05% | 0,05% |
| 2029 | 8,5% | 1,6% | 0,25% |
| 2030 | 8,5% | 3,2% | 0,5% |
| 2031 | 8,5% | 4,8% | 0,75% |
| 2032 | 8,5% | 6,4% | 1,0% |
| **2033** | **8,5%** | **16,0%** | **2,5%** |

2033 é o regime permanente (carga total de 27%) e 2029–2032 são **10/20/30/40% dele** — conferido no
portal, não interpolado. A alíquota de **referência** é uniforme por tipo de ente, não por ente: MG
(`31`) e SP (`35`) devolveram o mesmo valor em todos os anos, e os quatro municípios também. Por isso
uma linha por ano vale para os 4 municípios do seed.

⚠️ **Rate limit / WAF:** depois de ~15 requisições em rajada o portal passa a devolver HTML
`Request Rejected` em **todos** os endpoints, inclusive os que já tinham respondido. Com ~5s entre
chamadas ele não reclamou. Carga futura: espaçar as chamadas e persistir o que voltou antes de
continuar, nunca varrer 5.570 municípios em loop apertado.

⚠️ **A curva real da transição é simbólica:** IBS de 0,1% em 2026 e 0,05%+0,05% em 2027–2028 não
exercita arredondamento nem redução de alíquota. Por isso o oráculo de teste e os exemplos de
`Fin.md` §1.4.8 usam **2033** — que agora também é valor **real** (16,0% + 2,5% de IBS e 8,5% de CBS),
não mais a estimativa 13,12 + 4,50 / 8,80 do `fiscal-010`, que o `fiscal-022` sobrescreveu.

---

## Detalhamento

### 1. 🟡 Alíquotas de referência IBS/CBS por ano (transição)
- **O que extrair:** o percentual de referência de IBS e de CBS válido em cada ano da transição.
- **Fonte em mãos:** `spec/IBSCBS_Presentation_GOV.pdf` (SERT/Min. Fazenda, jun/2024) traz a **metodologia**
  e os pontos de partida; o [portal calculadora][portal] devolve os números vigentes por ano/ente.
- **Números já confirmados pela apresentação:**
  - **2026** (teste): CBS **0,9%** + IBS **0,1%** — recolhimento pode ser dispensado se cumpridas obrigações acessórias.
  - **2027/2028**: alíquota de referência da CBS **reduzida em 0,1 p.p.**; IBS **0,1%** (0,05% estadual + 0,05% municipal).
  - IBS é sempre cindido em **IBS-E** (estadual) + **IBS-M** (municipal) — casa com o `AliquotaIbs(estadual, municipal)` do motor.
  - **Teto** de carga regulamentado para a CBS em **2030** e para CBS+IBS em **2035**.
- **⚠️ Por que é carga recorrente e não "acabou":** as alíquotas de referência são **fixadas anualmente pelo
  Senado** (cálculo do TCU, até 31/out do ano anterior à vigência). A curva 2026–2033 já está publicada e
  carregada, mas pode ser **refixada** — modelar por `(ente, ano)` absorve isso; reconferir no portal a cada
  fixação.
- **Estado no banco (30/07/2026):** o `fiscal-022-aliquotas-reais-portal-cbs` **sobrescreveu** o seed estimado
  do `fiscal-010`, e o `fiscal-023-aliquota-ibs-referencia-nacional` (**não testado**) foi além: removeu as
  4 linhas municipais do `fiscal-022` (eram cópia da referência) e as substituiu por **uma linha nacional
  por ano** (`ibge_municipio = '0000000'`), 2026 a 2033. `aliq_cbs_regime` tem os três regimes de 2026 a
  2033 e `aliq_ibs_municipio` tem a referência nacional para o mesmo período — qualquer município cai nela
  quando não tem linha própria. **Não há mais valor estimado na base.**

### 2. ✅ Alíquotas IBS por ente (união/estado/município)
- **O que extrair:** parcela estadual e municipal do IBS por unidade (chave = código IBGE do município de
  destino; o estado deriva dele). O motor já separa `estadual` + `municipal`.
- **Fonte em mãos:** [portal calculadora][portal] — consulta por União, Estado e Município.
- **Formato-alvo (tabela já existe, `fiscal-007`):** `fiscal.aliq_ibs_municipio(ibge_municipio, uf,
  nome_municipio, ano_vigencia, aliquota_estadual, aliquota_municipal)`.
- **Nota:** a maioria usa a alíquota de referência; entes podem fixar a própria depois. Tabela por
  `(ibge, ano)` cobre os dois casos — `TabelaFiscalJdbc.SQL_ALIQ_IBS` busca a linha do município OU a
  sentinela nacional `'0000000'`, preferindo a própria quando existir.
- **✅ Cobertura: todo município do Brasil, 2026–2033 (`fiscal-023-aliquota-ibs-referencia-nacional`, não
  testado).** A linha nacional (`ibge_municipio = '0000000'`) substituiu as 4 linhas municipais do
  `fiscal-022` — eram cópia da referência, não alíquota própria. Quando o motor cai na referência, emite
  `Constants.FISCAL_AVISO_ALIQUOTA_REFERENCIA` (WARN + `memoriaCalculo`), sem mudar o cálculo. O que falta
  é conferência, não bloqueio: carregar os ~5.570 municípios via API (item 7) confirma a uniformidade e
  captura quem tiver fixado alíquota própria — essa passa a vencer a referência automaticamente.

### 3. 🔴 Alíquotas do IS (Imposto Seletivo) por NCM
- **O que extrair:** quais NCM sofrem IS e a alíquota de cada um (cigarro, bebidas açucaradas, veículos
  poluentes, extração mineral...). No banco (`fiscal.aliq_is_ncm`) há **uma** linha: `2402.20`
  ("Cigarros que contêm tabaco") a **150%**, estimada, vigente de 2027-01-01.
- **Status:** **não há tabela oficial publicada** por NCM. Enquanto não sair a regulamentação específica do IS,
  esse número permanece placeholder.
- **Regra do motor (já implementada):** IS incide antes e **integra a base** de IBS/CBS (Fin.md §1.4.2 Passo 5).
  Só falta a tabela de alíquotas; a matemática não muda quando ela chegar.

### 4. ✅ De-para NCM → regime diferenciado (Anexos LC 214/2025)
- **Carregado:** `fiscal-016-load-regime-lc214-v2` — **242 códigos** NCM/SH com regime
  (`ANEXO_I_ZERO`, `ANEXO_*_60`, `MONOFASICO`, `ISENTO`, `IMUNE`, `ZFM`...), lendo a coluna NCM/SH dos anexos.
  Recarga do `fiscal-015` (95 códigos), que lia a coluna errada.
- **Fonte:** `spec/leicomplementar-214-16-janeiro-2025-796905-normaatualizada-pl.pdf` (298 págs.,
  texto consolidado com os anexos) — **fonte da verdade**.
- **Destino:** `fiscal.regime_dif_ncm`, agora **exclusivamente produto** — a coluna `nbs` foi removida
  (`fiscal-020`); ela se chamava `nbs` mas guardava código LC 116. Serviço migrou para o item 10.
- **Catálogo de regimes deixou de ser enum:** `RegimeDiferenciado` é `record` e o `percentual_reducao` da
  linha é a fonte autoritativa. Motivo: com enum, um regime novo no banco e ausente do código cairia calado
  em PADRAO e cobraria alíquota cheia num item reduzido. Revisa o ADR de Fin.md §1.4.4.
- **Cobertura:** NCM sem linha aqui cai em PADRAO (tributa cheio) **sem bloquear** — MF-10. Diferente do
  serviço, aqui o erro é a favor do fisco e o motor segue; ainda falta o warn/alerta.

### 5. ✅ Tabela NCM (descrições)
- **Fonte:** `spec/Tabela_NCM_Desc_Concatenada_Vigente_20260702.csv` (~61k linhas, `;`, header na linha 5).
- **Carregado:** `fiscal-012-load-ncm` → `fiscal.ncm` com **15.156 códigos vigentes** em 02/07/2026 (o CSV
  tem histórico; só o vigente entrou).

### 6. ✅ Tabela CFOP
- **Carregado:** `fiscal-014-load-cfop` → **629 CFOP** (Convênio SINIEF s/nº 1970 até Ajuste 03/22), com
  natureza (entrada/saída) e as flags do motor (`geraCreditoIbs`, `geraCreditoCbs`, `primeiraEtapaCadeia`).
  Substitui os 4 CFOP do seed parcial (`fiscal-006`).
- **Fonte:** `spec/Tabela_CFOPOperacoesGeradorasCreditos.xlsx` + derivação por faixa para `primeiraEtapaCadeia`
  (produção própria/importação vs revenda) — a planilha não traz essa flag pronta.

### 7. 🟡 Códigos de município IBGE
- **Fonte:** IBGE (tabela de municípios, 7 dígitos). Pública, estável.
- **Uso:** chave de `fiscal.aliq_ibs_municipio` e validação do `ibgeDestino`/`ibgeLocalPrestacao` do request.
- **Status:** **não carregada** — `fiscal.aliq_ibs_municipio` hoje só tem a linha de referência nacional
  (sentinela `'0000000'`, `fiscal-023`, não testado), que já cobre todo município. Carregar os ~5.570
  códigos deixou de ser pré-requisito do item 2 e virou **conferência**: confirma a uniformidade da
  referência e captura quem tiver fixado alíquota própria.

### 8. ✅ Lista de serviços LC 116/2003 + correlação do Anexo VIII
- **Carregado:** `fiscal-018-load-servico-anexo-viii` → `fiscal.servico_nbs` com **895 pares LC 116 × NBS**
  (675 NBS distintos) e `fiscal.servico_cclasstrib` com **246 pares LC 116 × cClassTrib**.
- **Fontes:** `spec/LC1162003.pdf` (lista de serviços, ex. `4.01` saúde) + Anexo VIII da LC 214.
- **Uso no motor:** `servico_cclasstrib` é o que valida o par — `cClassTrib` fora dele devolve
  `FISCAL_CCLASSTRIB_INVALIDO_PARA_SERVICO` (400). O item vem no CSV como `01.01`/`04.01` e é normalizado
  no SQL para casar com o `1.01`/`4.01` do request.
- **⚠️ `loadData` guarda checksum do CSV:** editar `data/servico-*.csv` quebra a validação em quem já rodou.
  Correção entra como changeset novo.

### 9. 🟡 Regras de Split Payment
- **Fontes em mãos:**
  - `spec/03153733-manual-de-integracao-plataforma-publica-de-split-payment-v1.pdf` (v1.0, RFB/Serpro/CGIBS/PROCERGS) — **contrato de integração** (fluxos, campos, endpoints).
  - `spec/30145925-minuta-split-payment-manual-de-operacoes.pdf` — **manual de operações** (regras de negócio da segregação).
- **Como funciona (do manual de integração):**
  - A **Plataforma Pública de Split Payment** é um HUB entre os PSPs (bancos/operadoras) e os entes (CGIBS + RFB).
    Ela **não executa regra de negócio** — transporta, valida e rastreia eventos. A **segregação** (retenção do
    tributo) é feita pelo **PSP recebedor** na **liquidação financeira** da transação.
  - Ciclo de vida por transação: Informe de **Transação Iniciada** → **Atualizada** → **Baixa** → **Preliminar de
    Pagamento** → **Segregação** (liquidada) → Retorno/Consulta "Super Inteligente".
  - Arranjos suportados: **PXA** (Pix Automático), **PXD** (Pix Dinâmico), **PXE** (Pix Estático), **BOL** (Boleto),
    **TED**, **TEF**.
  - Valores que trafegam: `vlIbsInf`/`vlCbsInf` (**informado** no documento fiscal) → `vlIbsCorr`/`vlCbsCorr`
    (corrigido por RFB/CG se divergir do doc fiscal) → `vlIbsAberto`/`vlCbsAberto` (não extinto) →
    `vlIbsSegr`/`vlCbsSegr` (**efetivamente segregado**). Vínculo pelo `docFiscal`.
  - Restrições de contrato: **máx. 1.000 transações por requisição**; **CNPJ alfanumérico** (IN RFB 2.229/2024,
    vigente a partir de **07/2026**); headers `messageId` (UUID v4), `correlationId`, `tenantId` (CNPJ do PSP).
- **Impacto no motor (o que muda vs. hoje):** o motor produz o **valor destacado** de IBS/CBS que vira
  `vlIbsInf`/`vlCbsInf` no documento fiscal — é esse número que o PSP segrega. Hoje o motor devolve só o **teto**
  (valor do tributo) em `valorSplitIbs/Cbs`; a **obrigatoriedade** do split e o **fluxo de liquidação** (quando/quanto
  retém) vêm da minuta de operações e são responsabilidade do PSP, **não** do ERP. Ou seja: para o `fiscal-service`,
  o split não altera o cálculo — altera o **contrato de saída** (que campos o documento fiscal precisa carregar).
- **✅ Feita a feature flag:** `fiscal.split-payment` (`SplitPaymentProperties`), default **off**, com
  allowlist por tenant — desligada, `valorSplitIbs`/`valorSplitCbs` saem **ausentes** (null) do contrato, não
  zerados. Ligada, `splitPaymentAplicavel` passa a ser obrigatório no request
  (`FISCAL_SPLIT_SEM_FORMA_PAGAMENTO`). Falta a integração com o PSP/Plataforma Pública — o motor devolve o
  **teto** (valor do tributo), não a liquidação.
- **⚠️ Requisito original: split payment atrás de botão liga/desliga (feature flag).** A adesão ao split é **faseada**
  (piloto, entrada gradual dos PSPs/arranjos, 2026 é período de teste, obrigatoriedade progressiva). O ERP precisa
  **ligar e desligar** o split — por config e, provavelmente, **por tenant** (cada empresa/estabelecimento pode
  entrar em momento diferente). Desligado: o motor calcula normal, o documento fiscal **não** carrega os campos de
  split e nenhum informe é emitido à Plataforma Pública. Ligado: emite `vlIbsInf`/`vlCbsInf` e os informes.
  Default = **desligado** até a integração PSP estar homologada.

### 10. 🟡 Percentual de redução por cClassTrib (serviços) — 25 de 27
- **O que era:** o Anexo VIII lista os 27 `cClassTrib` mas **não** traz o percentual — ele está em artigos da
  LC 214 fora do anexo.
- **Carregado (`fiscal-019`), 7:** `000001` INTEGRAL (0%), `200028` ANEXO_II_60, `200029` ANEXO_III_60,
  `200038` ANEXO_IX_60, `200039` ANEXO_X_60, `200043` e `200044` ANEXO_XI_60 — os que citam um anexo no
  próprio nome (anexos II/III/IX/X/XI são todos de 60%) mais o integral, que é 0% por definição.
- **Carregado (`fiscal-021`, 29/07/2026), +18:** um artigo da LC 214 por linha, no comentário do changeset.
  100% para ICT (art. 156), transporte ferroviário/hidroviário urbano (art. 285, I) e o rodoviário/metroviário
  isento (art. 157); 70% locação de imóveis (art. 261, § único); 60% planos de assistência (arts. 236/237/240),
  serviço ambiental (art. 137, § 3º), comunicação institucional (art. 140), educação desportiva (art. 141) e
  reabilitação urbana (art. 158); 50% demais operações com imóveis (art. 261); 40% hotelaria (art. 281) e
  agências de turismo (art. 289, II); 30% plano de saúde animal (art. 243) e profissões intelectuais (art. 127);
  0% exploração de via (art. 11, VIII — integral).
- **Faltam 2, e é limitação de modelo, não de pesquisa:** `010002` serviços financeiros (art. 233 fixa a *soma*
  de IBS+CBS em valor absoluto — 10,85% em 2027-2028) e `200025` Prouni (art. 308 zera **só a CBS**).
  `percentual_reducao` é um só para os dois tributos; resolver exige coluna por tributo ou tabela de alíquota
  absoluta. Ver `spec/anexos-lc214-revisar.md`.
- **⚠️ O sinal do erro aqui é CONTRA o contribuinte.** `cClassTrib` ausente de `regime_cclasstrib` cai em
  PADRAO e sai **tributado cheio** — por isso esses 2 continuam bloqueando NFS-e nos respectivos ramos.
- **Não confundir INTEGRAL com PADRAO:** os dois tributam cheio, mas INTEGRAL é classificação declarada e
  PADRAO é dado fiscal faltando. Escrever percentual de cabeça dentro de motor fiscal é pior que não ter o dado.

### 11. ✅ Alíquota de ISS (legado) por município — só a referência nacional
- **O que é:** ISS não é IBS — é o imposto municipal legado que ainda incide durante toda a transição
  (fatia 3d). Guarda alíquota por município (IBGE) e item da LC 116, com `item_lc116` nulável (linha
  "curinga" que vale para qualquer item do município).
- **Carregado (`fiscal-028`/`fiscal-029`):** só a linha de referência sentinela (`ibge_municipio = '0000000'`,
  `Constants.FISCAL_IBGE_REFERENCIA_NACIONAL`) com o **teto constitucional da LC 116 art. 8-A (5%)** —
  mesmo desenho de `fiscal.aliq_ibs_municipio` (item 1). Nenhum município tem alíquota própria carregada
  ainda; carregar os ~5.570 é a mesma tarefa de uniformidade do item 1, não um bloqueio novo.
- **Testado e verde (18/08/2026):** `TabelaFiscal.aliquotaIss` coberto por `TabelaFiscalJdbcTest`; usuário
  confirmou `mvn verify -pl fiscal-service` com 0 falhas.
- **Motor não consome ainda:** a tabela existe e é consultável, mas o passo de ISS legado no
  `POST /fiscal/calcular` é a fatia 3c, que depende também do item 12 e da curva de transição (item 2, já pronta).

### 12. ✅ Alíquota interna de ICMS (legado) por UF — 27 de 27
- **O que é:** ICMS não é IBS — é o imposto estadual legado (fatia 3b). Guarda a alíquota interna **cheia**
  por UF; a curva de redução 2029-2033 já é `fiscal.transicao_ano` (item 2) e não entra nesta tabela.
  `aliq_nominal` e `p_reducao_base` ficam separados porque a NF-e pede vBC reduzida e pICMS nominal como
  campos distintos.
- **Fonte:** WebSearch, cross-checado em múltiplas fontes independentes por UF — 3 divergências (Alagoas,
  Sergipe, Mato Grosso do Sul) resolvidas com buscas direcionadas de acompanhamento antes de fechar o seed.
- **Carregado (`fiscal-030`/`fiscal-031`):** as 27 UFs (26 estados + DF), uma linha cada, fallback geral por
  estado (`ncm_nbs = '00000000'`, `Constants.FISCAL_NCM_NBS_FALLBACK` — sentinela de 8 zeros, diferente do
  de 7 zeros usado para município). Exceção por NCM fica de fora por decisão: vira override quando um cliente
  real reclamar, não pesquisa especulativa agora.
- **`vigente_de` entra na chave** (diferente da tabela de ISS) porque alíquota de UF muda de verdade — Alagoas
  foi de 19% para 20,5% em 01/04/2026 (Lei 9.776/2025, DO-AL 23/12/2025), já carregado com a data de vigência certa.
- **Testado e verde (18/08/2026):** `TabelaFiscal.aliquotaIcms` coberto por 6 testes novos em
  `TabelaFiscalJdbcTest` (precedência de 4 níveis: tenant+NCM > tenant+fallback > nacional+NCM > nacional+fallback,
  mais vigência vencida e ausência de cobertura); usuário confirmou `mvn verify -pl fiscal-service`, 61/61 testes.
- **Motor não consome ainda:** mesma situação do item 11 — fatia 3c ainda pendente.
- **Fora do escopo por decisão, não por falta de dado:** interestadual (12%/7%/4%, Resolução do Senado 22/89 +
  13/12) é função sobre lista de UF, não dado que muda — não mora nesta tabela. ST/MVA/DIFAL/pauta fiscal/CSOSN
  ficam de fora do motor.

---

## O que já está construído, e o que falta

**Feito:** schema `fiscal.*` por Liquibase (`fiscal-001` … `fiscal-031`), `TabelaFiscalJdbc implements
TabelaFiscal` lendo de lá, e as cargas dos itens 4, 5, 6, 8 e 10 (este último 25 de 27). O motor calcula IBS/CBS/IS de **saída** por
`POST /fiscal/calcular` com conteúdo real da LC 214. **Fora do motor de saída**, os itens 11 (ISS legado,
referência nacional) e 12 (matriz ICMS legado, 27 UFs) também estão carregados, testados e verdes
(`mvn verify -pl fiscal-service`, 61/61) — mas o motor ainda **não os consome**: isso é a fatia 3c.

**Falta, em ordem de impacto prático:**
1. **Anos fora da curva 2026–2033** (itens 1, 2, 7) — qualquer município já calcula IBS via alíquota de
   referência nacional (`fiscal-023`, não testado); só um ano fora dessa faixa (ex. 2035) ainda devolve
   400. Carregar os ~5.570 municípios do IBGE via API virou conferência de uniformidade, não pré-requisito.
2. ✅ **Os 20 `cClassTrib` sem percentual** (item 10) — 18 entraram no `fiscal-021`; sobraram `010002` e
   `200025`, que precisam de mudança de modelo (redução por tributo / alíquota absoluta), não de pesquisa —
   e enquanto isso esses dois ramos seguem tributando cheio, com o erro contra o contribuinte.
3. **Tabela do IS** (item 3) — depende de publicação oficial; a matemática (IS integra a base) já está testada
   em `MotorFiscalServiceTest`, é só trocar a tabela.
4. **Integração PSP do split** (item 9) — a flag existe, a liquidação não.
5. **Warn de NCM/cClassTrib sem regime** — hoje o fallback para PADRAO é silencioso (MF-10).
6. **Motor consumir ISS/ICMS legado** (itens 11, 12 — fatia 3c) — as tabelas existem, estão testadas e
   verdes, mas nada em `POST /fiscal/calcular` ainda chama `TabelaFiscal.aliquotaIss`/`aliquotaIcms`.

> ⚠️ Enquanto o **IS** não vier, é seguro desenvolver/demonstrar com o valor estimado do cigarro, mas **não**
> emitir documento fiscal real com esse número. O mesmo vale para as alíquotas de 2027, que são estimativas
> (`fiscal-010-seed-aliquotas-estimadas`) e não a curva fixada pelo Senado.