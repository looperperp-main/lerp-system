# Casos de Teste — Motor Fiscal (IBS/CBS/IS)

**Última atualização:** 27 de agosto de 2026
**Alvo de teste:** `MotorFiscalService.calcular(MotorFiscalRequest, String tenantId)` (`fiscal-service`, Fin.md §1.4).
O `tenantId` vem do header `X-Tenant-Id` e **só** decide o split payment por tenant — não entra no cálculo.
**Oráculo contra:** `TabelaFiscalFake` (fixture de teste, alíquotas **reais** de 2033; espelha o seed de
`fiscal-schema-002.yaml`). Em produção o conteúdo fiscal vem de `fiscal.*` via `TabelaFiscalJdbc`,
que tem teste próprio (`TabelaFiscalJdbcTest`, PostgreSQL) — este documento não cobre acesso a dados.

> ⚠️ Os valores esperados abaixo valem para a **fixture atual**. Se as alíquotas mudarem
> (CGIBS / carga nova no DB), recalcular. A matemática (IS na base, redução de alíquota, monofásico,
> split-teto, arredondamento HALF_UP 2 casas) é que está sendo validada — não os números em si.

## Premissas fixas da seed

| Parâmetro | Valor |
|---|---|
| Município destino / prestação | `3550308` (São Paulo) |
| Ano de competência | `2033` — regime permanente (ex.: `dataCompetencia = 2033-03-15`) |
| IBS São Paulo 2033 | estadual **16,00%** + municipal **2,50%** (linha de **referência**, `fiscal-023`) |
| CBS Lucro Real 2033 | **8,50%** |

> ⚠️ **Por que 2033 e não 2027.** As alíquotas reais da transição (API de dados abertos do piloto
> CBS, 30/07/2026) são simbólicas: 2026 = CBS 0,9% + IBS 0,1% (0,1 estadual / 0 municipal);
> 2027-2028 = CBS 8,4% + IBS 0,05% + 0,05%. Com elas o IBS de R$ 10.000 é R$ 10,00 — não testa
> arredondamento, redução por regime nem repartição estado/município. O oráculo usa então o
> **regime permanente**: IBS 16,00% + 2,50% e CBS 8,50% — também **lidos do portal**, não
> estimados (2029 a 2032 são 10/20/30/40% desses valores). Carga por ano: changeset
> `fiscal-022-aliquotas-reais-portal-cbs`.
| IS cigarro (NCM `24022000`) | **150%** |
| CFOP `5101` | saída, produção própria → **1ª etapa da cadeia** |
| CFOP `5102` / `5405` | saída, revenda → **fora da 1ª etapa** |
| CFOP `5933` | saída, prestação de serviço |
| NCM `10063021` (arroz) | regime **ANEXO_I_ZERO** (redução 100% → alíquota zero) |
| NCM `24022000` (cigarro) | regime **MONOFASICO** + IS |
| Serviço item `4.01` × `cClassTrib` `200029` | par admitido (Anexo VIII); regime **ANEXO_III_60** (redução 60%) |
| Serviço item `1.01` × `cClassTrib` `000001` | par admitido; regime **INTEGRAL** (redução 0 — tributa cheio *por classificação*) |
| NCM não mapeado / `cClassTrib` sem linha de regime | regime **PADRAO** (tributa cheio *por dado faltando*) |

**Serviço é classificado pelo `cClassTrib` declarado, não pelo código LC 116.** O código LC 116
(`codigoServico`) só entra na validação do par: o Anexo VIII fixa quais `cClassTrib` valem para cada
item. O mesmo serviço muda de classificação conforme o contexto (à administração pública vira
`200043`), então deduzir o regime do item seria errado. Consequências para os casos abaixo:

- serviço sem `cClassTrib` ⇒ `FISCAL_CCLASSTRIB_OBRIGATORIO` (400), nunca fallback para PADRAO;
- `cClassTrib` fora do par admitido ⇒ `FISCAL_CCLASSTRIB_INVALIDO_PARA_SERVICO` (400);
- `cClassTrib` admitido mas sem linha em `fiscal.regime_cclasstrib` ⇒ PADRAO (tributa cheio).

**Regras-chave (Fin.md §1.4.2):** IS incide sobre o valor bruto e **integra a base** de IBS/CBS;
a redução do regime é de **alíquota**, não de base; MEI, cesta básica e monofásico-fora-da-1ª-etapa
**zeram** IBS/CBS/IS (base = valor da operação); `pct = valor × alíquota ÷ 100`, arredondado a 2 casas **HALF_UP**.

Coluna **Teste**: ✅ já em `MotorFiscalServiceTest`; ➕ novo (candidato a incluir).

---

## A. Produto padrão (regime PADRAO, sem redução)

### A1 — Notebook R$ 10.000 ✅
`cfop=5101, ncm=84713012, ibgeDestino=3550308, valorOperacao=10000, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| valorIs | 0 | produto sem IS |
| baseCalculo | 10000 | 10000 + 0 |
| valorIbsEstadual | 1600.00 | 10000 × 16,00% |
| valorIbsMunicipal | 250.00 | 10000 × 2,50% |
| valorIbs | 1850.00 | 1600 + 250 |
| valorCbs | 850.00 | 10000 × 8,50% |
| valorSplitIbs / Cbs | `null` (campo ausente) | flag de split desligada (default) |
| regimeAplicado | PADRAO | |

### A2 — Arredondamento (R$ 33,33) ➕
`cfop=5101, ncm=84713012, ibgeDestino=3550308, valorOperacao=33.33, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| baseCalculo | 33.33 | |
| valorIbsEstadual | 5.33 | 33,33 × 16,00% = 5,3328 → 5,33 |
| valorIbsMunicipal | 0.83 | 33,33 × 2,50% = 0,833250 → 0,83 |
| valorIbs | 6.16 | |
| valorCbs | 2.83 | 33,33 × 8,50% = 2,833050 → 2,83 |
| regimeAplicado | PADRAO | |

### A3 — Arredondamento no limite (R$ 1,00) ➕
`cfop=5101, ncm=84713012, ibgeDestino=3550308, valorOperacao=1.00, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| valorIbsEstadual | 0.16 | 1,00 × 16,00% = 0,16 |
| valorIbsMunicipal | 0.03 | 1,00 × 2,50% = 0,025 → **0,03** (HALF_UP) |
| valorIbs | 0.19 | |
| valorCbs | 0.09 | 1,00 × 8,50% = 0,085 → **0,09** (HALF_UP) |
| regimeAplicado | PADRAO | |

---

## B. Cesta básica (zera tudo)

### B1 — Arroz R$ 500, revenda ✅
`cfop=5102, ncm=10063021, ibgeDestino=3550308, valorOperacao=500, regime=LUCRO_REAL`

| Campo | Esperado |
|---|---|
| valorIs / valorIbs / valorCbs | 0 / 0 / 0 |
| baseCalculo | 500 |
| regimeAplicado | ANEXO_I_ZERO |

### B2 — Arroz R$ 1.234,56 em 1ª etapa (ainda zera) ➕
`cfop=5101, ncm=10063021, ibgeDestino=3550308, valorOperacao=1234.56, regime=LUCRO_REAL`

| Campo | Esperado | Nota |
|---|---|---|
| valorIs / valorIbs / valorCbs | 0 / 0 / 0 | cesta básica zera **independe** de CFOP/1ª etapa |
| baseCalculo | 1234.56 | |
| regimeAplicado | ANEXO_I_ZERO | |

---

## C. Monofásico + IS (cigarro)

### C1 — Cigarro R$ 100, fabricante (1ª etapa) ✅
`cfop=5101, ncm=24022000, ibgeDestino=3550308, valorOperacao=100, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| valorIs | 150.00 | 100 × 150% |
| baseCalculo | 250 | 100 + 150 (IS integra a base) |
| valorIbsEstadual | 40.00 | 250 × 16,00% |
| valorIbsMunicipal | 6.25 | 250 × 2,50% |
| valorIbs | 46.25 | |
| valorCbs | 21.25 | 250 × 8,50% |
| regimeAplicado | MONOFASICO | |

### C2 — Cigarro R$ 100, distribuidor (fora da 1ª etapa) ✅
`cfop=5102, ncm=24022000, ibgeDestino=3550308, valorOperacao=100, regime=LUCRO_REAL`

| Campo | Esperado | Nota |
|---|---|---|
| valorIs / valorIbs / valorCbs | 0 / 0 / 0 | já recolhido na origem |
| baseCalculo | 100 | |
| regimeAplicado | MONOFASICO | |

### C2b — Cigarro R$ 100, revenda ST (CFOP 5405) ➕
`cfop=5405, ncm=24022000, ibgeDestino=3550308, valorOperacao=100, regime=LUCRO_REAL`
→ idêntico a C2 (5405 também é fora da 1ª etapa): tudo **0**, base 100, MONOFASICO.

### C3 — Cigarro R$ 1.000, fabricante ➕
`cfop=5101, ncm=24022000, ibgeDestino=3550308, valorOperacao=1000, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| valorIs | 1500.00 | 1000 × 150% |
| baseCalculo | 2500 | 1000 + 1500 |
| valorIbsEstadual | 400.00 | 2500 × 16,00% |
| valorIbsMunicipal | 62.50 | 2500 × 2,50% |
| valorIbs | 462.50 | |
| valorCbs | 212.50 | 2500 × 8,50% |
| regimeAplicado | MONOFASICO | |

---

## D. Serviço (NFS-e, IBS pelo local da prestação)

### D1 — Saúde R$ 300, redução 60% ✅
`cfop=5933, codigoServico=4.01, cClassTrib=200029, ibgeLocalPrestacao=3550308, valorOperacao=300, regime=LUCRO_REAL`
(sem `ncm` → tratado como serviço; IS sempre 0 em serviço. A redução de 60% vem do `cClassTrib`
`200029`; o item `4.01` só é usado para validar que o par é admitido pelo Anexo VIII)

| Campo | Esperado | Conta (alíquota × fator 0,40) |
|---|---|---|
| valorIs | 0 | serviço |
| baseCalculo | 300 | |
| valorIbsEstadual | 19.20 | 300 × (16,00% × 0,40) = 300 × 6,40% |
| valorIbsMunicipal | 3.00 | 300 × (2,50% × 0,40) = 300 × 1,00% |
| valorIbs | 22.20 | |
| valorCbs | 10.20 | 300 × (8,50% × 0,40) = 300 × 3,40% |
| regimeAplicado | ANEXO_III_60 | nome do regime como está no banco, não "REDUCAO_60" |

### D2 — Saúde R$ 1.000, redução 60% ➕
`cfop=5933, codigoServico=4.01, cClassTrib=200029, ibgeLocalPrestacao=3550308, valorOperacao=1000, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| baseCalculo | 1000 | |
| valorIbsEstadual | 52.48 | 1000 × 5,248% |
| valorIbsMunicipal | 18.00 | 1000 × 1,80% |
| valorIbs | 70.48 | |
| valorCbs | 35.20 | 1000 × 3,52% |
| regimeAplicado | ANEXO_III_60 | |

### D3 — Serviço com tributação integral R$ 500 ✅
`cfop=5933, codigoServico=1.01, cClassTrib=000001, ibgeLocalPrestacao=3550308, valorOperacao=500, regime=LUCRO_REAL`
(`000001` = "situações tributadas integralmente": par admitido pelo Anexo VIII para o item `1.01` **e**
com linha em `fiscal.regime_cclasstrib` (redução 0, `fiscal-019`) → regime **INTEGRAL**, alíquota cheia.
Não confundir com PADRAO, que é o fallback de `cClassTrib` **sem** linha lá — mesmo resultado numérico,
sinal diferente: INTEGRAL é classificação declarada, PADRAO é dado fiscal faltando)

| Campo | Esperado | Conta |
|---|---|---|
| baseCalculo | 500 | |
| valorIbsEstadual | 80.00 | 500 × 16,00% |
| valorIbsMunicipal | 12.50 | 500 × 2,50% |
| valorIbs | 92.50 | |
| valorCbs | 42.50 | 500 × 8,50% |
| regimeAplicado | INTEGRAL | |

---

## E. MEI (nunca destaca — vence tudo)

### E1 — MEI notebook R$ 10.000 ✅
`cfop=5101, ncm=84713012, ibgeDestino=3550308, valorOperacao=10000, regime=MEI`
→ tudo **0**, baseCalculo 10000, regimeAplicado PADRAO.

### E2 — MEI cigarro R$ 100 em 1ª etapa (MEI vence monofásico/IS) ➕
`cfop=5101, ncm=24022000, ibgeDestino=3550308, valorOperacao=100, regime=MEI`

| Campo | Esperado | Nota |
|---|---|---|
| valorIs / valorIbs / valorCbs | 0 / 0 / 0 | MEI curto-circuita **antes** do IS |
| baseCalculo | 100 | |
| regimeAplicado | PADRAO | |

---

## F. Split payment (teto = valor do tributo)

Dois interruptores independentes, e a ordem importa:

1. **A flag** (`fiscal.split-payment.enabled` global, ou o tenant na allowlist `fiscal.split-payment.tenants`)
   decide se os campos de split **existem** na resposta. Desligada ⇒ `valorSplitIbs`/`valorSplitCbs` são
   `null` e o JSON **não traz os campos** (`@JsonInclude(NON_NULL)`) — o documento fiscal não carrega split.
2. **`splitPaymentAplicavel`** (da condição de pagamento) decide o **valor**, e só é lido com a flag ligada.

A flag nunca altera IBS/CBS/IS — só o contrato de saída. Default do produto: **desligada**.

### F1 — Notebook R$ 10.000, flag ligada + pagamento splitável ✅
A1 + `splitPaymentAplicavel=true`, `enabled=true`

| Campo | Esperado |
|---|---|
| valorSplitIbs | 1850.00 (= valorIbs) |
| valorSplitCbs | 850.00 (= valorCbs) |

### F2 — Notebook R$ 10.000, flag ligada + pagamento não splitável ✅
A1 + `splitPaymentAplicavel=false`, `enabled=true`

| Campo | Esperado | Nota |
|---|---|---|
| valorIbs / valorCbs | 1850.00 / 850.00 | tributo continua destacado |
| valorSplitIbs / valorSplitCbs | 0.00 / 0.00 | campo presente, nada a segregar |

### F2b — Notebook R$ 10.000, flag desligada ✅
A1 + `splitPaymentAplicavel=true`, `enabled=false`, tenant fora da allowlist

| Campo | Esperado | Nota |
|---|---|---|
| valorIbs / valorCbs | 1850.00 / 850.00 | flag não altera o cálculo |
| valorSplitIbs / valorSplitCbs | `null` — ausentes no JSON | nada informado à Plataforma Pública |

### F2c — Allowlist por tenant ✅
`enabled=false`, `tenants=[tenant-piloto]`: `tenantId=tenant-piloto` ⇒ split **1850.00**;
qualquer outro tenant ⇒ campos ausentes. Cobre o piloto de 2026 sem ligar para a base toda.

### F3 — Serviço saúde R$ 300, flag ligada ➕
D1 + `splitPaymentAplicavel=true` → valorSplitIbs **21.14**, valorSplitCbs **10.56** (espelha o tributo reduzido).

---

## G. Erros / exceções (`FiscalException.getCodigo()`)

| # | Cenário | Request | Código esperado | Teste |
|---|---|---|---|---|
| G1 | CFOP inexistente | `cfop=9999, ncm=84713012, ibge=3550308, 2033, LUCRO_REAL` | `FISCAL_CFOP_NAO_ENCONTRADO` | ✅ |
| G2 | Município fora do seed **e** ano fora da curva | `cfop=5101, ncm=84713012, ibgeDestino=9999999, dataCompetencia=2035-03-15, LUCRO_REAL` | `FISCAL_VIGENCIA_SEM_COBERTURA` | ➕ |
| G3 | Vigência sem cobertura (ano) | `cfop=5101, ncm=84713012, ibge=3550308, dataCompetencia=2035-03-15, LUCRO_REAL` | `FISCAL_VIGENCIA_SEM_COBERTURA` | ➕ |
| G4 | Regime sem alíquota CBS | `cfop=5101, ncm=84713012, ibge=3550308, 2033, regime=SIMPLES_NACIONAL` | `FISCAL_REGIME_SEM_ALIQUOTA_CBS` | ➕ |
| G6 | Split ligado sem forma de pagamento | A1 + `splitPaymentAplicavel=null`, `enabled=true` | `FISCAL_SPLIT_SEM_FORMA_PAGAMENTO` | ✅ |
| G7 | NCM e serviço juntos | `cfop=5933, ncm=84713012, codigoServico=4.01` | `FISCAL_NCM_E_SERVICO_CONFLITANTES` | ✅ |
| G8 | Nem NCM nem serviço | `cfop=5101, ibge=3550308, sem ncm/codigoServico` | `FISCAL_NCM_OU_SERVICO_OBRIGATORIO` | ✅ |
| G9 | Serviço sem `cClassTrib` | `cfop=5933, codigoServico=4.01, ibgeLocalPrestacao=3550308` | `FISCAL_CCLASSTRIB_OBRIGATORIO` | ✅ |
| G10 | `cClassTrib` não admitido para o item | `cfop=5933, codigoServico=4.01, cClassTrib=200048` (hotelaria em serviço de saúde) | `FISCAL_CCLASSTRIB_INVALIDO_PARA_SERVICO` | ✅ |
| G11 | `NFSe` com produto | `cfop=5101, ncm=84713012, tipoDocumento=NFSe` | `FISCAL_TIPO_DOCUMENTO_INCOMPATIVEL` | ✅ |
| G12 | `NFe`/`NFCe` com serviço | `cfop=5933, codigoServico=4.01, cClassTrib=200029, tipoDocumento=NFe` | `FISCAL_TIPO_DOCUMENTO_INCOMPATIVEL` | ✅ |
| G13 | Desconto zera/inverte a operação | `cfop=5101, ncm=84713012, valorOperacao=100, valorDesconto=100` | `FISCAL_DESCONTO_MAIOR_QUE_OPERACAO` | ✅ |

> G6 só dispara com a flag ligada: sem `splitPaymentAplicavel` não dá pra distinguir "pagamento não
> splitável" de "o chamador esqueceu de mandar". Com a flag desligada o campo segue opcional.
> G7/G8 são o XOR produto × serviço — validado no motor (código `FISCAL_*`), não por bean validation.

> G9/G10 são 400 de propósito, não fallback: cair em PADRAO tributaria cheio um serviço possivelmente
> desonerado — erro **contra o contribuinte**, e calado. `200048` existe no Anexo VIII (hotelaria),
> mas não é par admitido do item `4.01`.

> **G5 aposentado (27 de agosto de 2026):** CFOP `1102` (ENTRADA) deixou de ser erro — o item 4
> implementou o crédito de entrada e `FISCAL_CFOP_INVALIDO_SAIDA` foi removido. Os casos que usam
> esse CFOP e os demais de crédito estão na seção J.

> G11/G12: `tipoDocumento` é **opcional** e `CTe` fica FORA da regra (o motor não trata transporte).
> Só o par explicitamente incoerente é 400 — documento trocado muda o destino do IBS (local da
> prestação × município do destinatário), então não pode passar calado.

---

## H. Avisos na `memoriaCalculo` (não bloqueiam o cálculo)

| # | Cenário | Request | Esperado | Teste |
|---|---|---|---|---|
| H1 | NCM/`cClassTrib` sem linha de regime | `cfop=5101, ncm=84713012` (sem linha em `regime_dif_ncm`) | regime `PADRAO` + `FISCAL_AVISO_REGIME_PADRAO` na memória; tributa CHEIO | ✅ |
| H2 | Origem ZFM | A1 + `origemProduto=ZFM` | `FISCAL_AVISO_ORIGEM_ZFM` na memória; **cálculo idêntico** ao nacional | ✅ |
| H3 | Município sem alíquota própria | A1 com `ibgeDestino=3304557` (Rio, fora do seed) | alíquota de **referência** (16,00% + 2,50%) + `FISCAL_AVISO_ALIQUOTA_REFERENCIA` na memória; **valores idênticos** a A1 | ➕ |

> **Por que o G2 mudou.** Desde o `fiscal-023` existe uma linha-base de alíquota de referência
> (`ibge_municipio = '0000000'`) para cada ano de 2026 a 2033, então município fora do seed **não é
> mais erro** — cai na referência e gera aviso (H3). O 400 sobrou para o que de fato não tem
> alíquota nenhuma: ano fora da curva, com ou sem município conhecido (G2 e G3).

> H1/H2/H3 são a mesma regra de conduta: quando o motor tributa **a mais** por falta de dado (H1) ou de
> regra ainda não implementada (H2), o número sai — mas com `WARN` de uma linha no log e a linha
> correspondente na `memoriaCalculo`. Nunca calado. A regra fiscal da ZFM (LC 214) segue pendente:
> H2 valida o **aviso**, não a desoneração.

---

## I. Base de cálculo composta (item 7.14)

`tributável = valorOperacao + valorFrete + valorSeguro + valorOutrasDespesas − valorDesconto`
(LC 214 art. 12, §2º). Todos os componentes são **opcionais**; sem nenhum deles o `valorOperacao`
**é** a base e nada muda em relação aos casos A–H. Desconto aqui é só o **incondicional**.

### I1 — Notebook R$ 10.000 com frete/seguro/acessórias e desconto ✅
`cfop=5101, ncm=84713012, ibgeDestino=3550308, valorOperacao=10000, valorFrete=500, valorSeguro=100, valorOutrasDespesas=50, valorDesconto=200, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| baseCalculo | 10450 | 10000 + 500 + 100 + 50 − 200 (IS = 0) |
| valorIbsEstadual | 1672.00 | 10450 × 16,00% |
| valorIbsMunicipal | 261.25 | 10450 × 2,50% |
| valorIbs | 1933.25 | |
| valorCbs | 888.25 | 10450 × 8,50% |
| memoriaCalculo | contém a linha `Valor tributável: …` | `Constants.FISCAL_MEMORIA_BASE_COMPOSTA` |

Controle no mesmo teste: **A1** (sem componentes) **não** traz a linha `Valor tributável:` — sem
componentes a composição seria ruído na memória.

### I2 — IS incide sobre a base já composta ✅
`cfop=5101, ncm=24022000, valorOperacao=100, valorFrete=20, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| valorIs | 180.00 | (100 + 20) × 150% — o IS **não** ignora o frete |
| baseCalculo | 300 | 120 + 180 (IS integra a base) |
| valorIbsEstadual / valorIbsMunicipal | 48.00 / 7.50 | 300 × 16,00% / 300 × 2,50% |
| valorCbs | 25.50 | 300 × 8,50% |

### I3 — Caminho de retorno antecipado também devolve base composta ✅
`cfop=5102, ncm=10063021, valorOperacao=500, valorFrete=20` → `baseCalculo` **520**, IBS/CBS/IS **0**.
Vale igual para MEI: a composição roda **antes** dos retornos de alíquota zero e de MEI.

> Fora do motor, por decisão: **desconto condicional** (não reduz base, então não entra no request) e
> o **rateio** de frete/desconto de cabeçalho entre os itens — o motor é chamado por item (§1.4.6),
> quem rateia é o AR/O2C.

---

## J. Crédito de entrada (item 4, `motor-fiscal-proximos-passos.md` §4) ✅ FEITO (27 de agosto de 2026)

CFOP de **entrada** deixa de ser 400 e passa por `calcularCredito`. Base: SP, NCM
`84713012`, `LUCRO_REAL`, competência 2033 (IBS 16,00% + 2,50%, CBS 8,50%).

### J1 — Crédito integral ✅
`cfop=1102 (ENTRADA, geraCreditoIbs/Cbs=true), valorOperacao=10000` → `valorCreditoIbs=1850.00`
(1600.00 + 250.00), `valorCreditoCbs=850.00`. Sem vedação declarada, credita 100% do que o
CFOP permite. `valorIs` nunca é creditado (IS não é recuperável).

### J2 — Uso e consumo pessoal veda o crédito ✅
J1 + `usoConsumoPessoal=true` → `valorCreditoIbs=0`, `valorCreditoCbs=0`, mesmo o CFOP
permitindo crédito — a vedação declarada pelo chamador tem prioridade.

### J3 — Saída desonerada credita só o complemento ✅
J1 + `percentualSaidaDesonerada=40` → `valorCreditoIbs=1110.00`, `valorCreditoCbs=510.00`
(60% do crédito integral — mesmo `fatorReducao` usado no cálculo de saída).

### J4 — CFOP sem direito a crédito zera por conta própria ✅
`cfop=1556 (ENTRADA, geraCreditoIbs/Cbs=false)` → `valorCreditoIbs=0`, `valorCreditoCbs=0`,
independente de `usoConsumoPessoal`/`percentualSaidaDesonerada` não terem sido declarados.

### J5 — Split ligado não exige forma de pagamento em entrada ✅
J1 + split ligado para o tenant (`enabled=true`) e `splitPaymentAplicavel=null` → **não** lança
`FISCAL_SPLIT_SEM_FORMA_PAGAMENTO` (esse guard é só de saída — bugfix do item 4, antes bloqueava
entrada indevidamente).

> Persistência de saldo/aproveitamento continua fora do fiscal-service — é do
> `operacoes-service` (AP), por decisão de 29 de julho de 2026.

---

## Resumo de cobertura

- **✅ no teste atual (30 métodos no `MotorFiscalServiceTest`):** A1, B1, C1, C2, D1, D3, E1, F1+F2 (mesmo método), F2b, F2c, G1, G6, G7, G8, G9, G10, G11+G12 (mesmo método), G13, H1, H2, I1, I2, I3, J1, J2, J3, J4, J5, + `tipoDocumento` coerente/`CTe` e `memoriaCalculo não vazia`.
- **✅ no `MotorFiscalControllerTest` (4):** 200 com `X-Tenant-Id` e sem campos de split no JSON, 400 de bean validation, 400 de corpo mal-formado, `FiscalException` → 400 com o código no `message`.
- **➕ candidatos a adicionar:** A2, A3, B2, C2b, C3, D2, E2, F3, G2, G3, G4.
- **Fora daqui:** acesso a dados (`TabelaFiscalJdbcTest`) — normalização do item LC 116 (`4.01` × `04.01`), casamento por prefixo de NCM e vigência das alíquotas se validam lá, contra PostgreSQL.

Prioridade dos novos: **arredondamento** (A2/A3) e **exceções** (G2–G4) — são os que pegam regressão de
borda que os casos "redondos" não pegam.