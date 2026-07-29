# Casos de Teste — Motor Fiscal (IBS/CBS/IS)

**Última atualização:** 29 de julho de 2026
**Alvo de teste:** `MotorFiscalService.calcular(MotorFiscalRequest, String tenantId)` (`fiscal-service`, Fin.md §1.4).
O `tenantId` vem do header `X-Tenant-Id` e **só** decide o split payment por tenant — não entra no cálculo.
**Oráculo contra:** `TabelaFiscalInMemory` (valores **estimados** `ponytail:` — trocar quando entrar `TabelaFiscalJpa`).

> ⚠️ Os valores esperados abaixo valem para a **seed em memória atual**. Se as alíquotas mudarem
> (CGIBS / troca por DB), recalcular. A matemática (IS na base, redução de alíquota, monofásico,
> split-teto, arredondamento HALF_UP 2 casas) é que está sendo validada — não os números em si.

## Premissas fixas da seed

| Parâmetro | Valor |
|---|---|
| Município destino / prestação | `3550308` (São Paulo) |
| Ano de competência | `2027` (ex.: `dataCompetencia = 2027-03-15`) |
| IBS São Paulo 2027 | estadual **13,12%** + municipal **4,50%** |
| CBS Lucro Real 2027 | **8,80%** |
| IS cigarro (NCM `24022000`) | **150%** |
| CFOP `5101` | saída, produção própria → **1ª etapa da cadeia** |
| CFOP `5102` / `5405` | saída, revenda → **fora da 1ª etapa** |
| CFOP `5933` | saída, prestação de serviço |
| NCM `10063021` (arroz) | regime **CESTA_BASICA** (redução 100%) |
| NCM `24022000` (cigarro) | regime **MONOFASICO** + IS |
| Serviço `4.01` (saúde) | regime **REDUCAO_60** (redução 60%) |
| NCM/serviço não mapeado | regime **PADRAO** (sem redução) |

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
| valorIbsEstadual | 1312.00 | 10000 × 13,12% |
| valorIbsMunicipal | 450.00 | 10000 × 4,50% |
| valorIbs | 1762.00 | 1312 + 450 |
| valorCbs | 880.00 | 10000 × 8,80% |
| valorSplitIbs / Cbs | `null` (campo ausente) | flag de split desligada (default) |
| regimeAplicado | PADRAO | |

### A2 — Arredondamento (R$ 33,33) ➕
`cfop=5101, ncm=84713012, ibgeDestino=3550308, valorOperacao=33.33, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| baseCalculo | 33.33 | |
| valorIbsEstadual | 4.37 | 33,33 × 13,12% = 4,372896 → 4,37 |
| valorIbsMunicipal | 1.50 | 33,33 × 4,50% = 1,49985 → 1,50 |
| valorIbs | 5.87 | |
| valorCbs | 2.93 | 33,33 × 8,80% = 2,93304 → 2,93 |
| regimeAplicado | PADRAO | |

### A3 — Arredondamento no limite (R$ 1,00) ➕
`cfop=5101, ncm=84713012, ibgeDestino=3550308, valorOperacao=1.00, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| valorIbsEstadual | 0.13 | 1,00 × 13,12% = 0,1312 → 0,13 |
| valorIbsMunicipal | 0.05 | 1,00 × 4,50% = 0,045 → **0,05** (HALF_UP) |
| valorIbs | 0.18 | |
| valorCbs | 0.09 | 1,00 × 8,80% = 0,088 → 0,09 |
| regimeAplicado | PADRAO | |

---

## B. Cesta básica (zera tudo)

### B1 — Arroz R$ 500, revenda ✅
`cfop=5102, ncm=10063021, ibgeDestino=3550308, valorOperacao=500, regime=LUCRO_REAL`

| Campo | Esperado |
|---|---|
| valorIs / valorIbs / valorCbs | 0 / 0 / 0 |
| baseCalculo | 500 |
| regimeAplicado | CESTA_BASICA |

### B2 — Arroz R$ 1.234,56 em 1ª etapa (ainda zera) ➕
`cfop=5101, ncm=10063021, ibgeDestino=3550308, valorOperacao=1234.56, regime=LUCRO_REAL`

| Campo | Esperado | Nota |
|---|---|---|
| valorIs / valorIbs / valorCbs | 0 / 0 / 0 | cesta básica zera **independe** de CFOP/1ª etapa |
| baseCalculo | 1234.56 | |
| regimeAplicado | CESTA_BASICA | |

---

## C. Monofásico + IS (cigarro)

### C1 — Cigarro R$ 100, fabricante (1ª etapa) ✅
`cfop=5101, ncm=24022000, ibgeDestino=3550308, valorOperacao=100, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| valorIs | 150.00 | 100 × 150% |
| baseCalculo | 250 | 100 + 150 (IS integra a base) |
| valorIbsEstadual | 32.80 | 250 × 13,12% |
| valorIbsMunicipal | 11.25 | 250 × 4,50% |
| valorIbs | 44.05 | |
| valorCbs | 22.00 | 250 × 8,80% |
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
| valorIbsEstadual | 328.00 | 2500 × 13,12% |
| valorIbsMunicipal | 112.50 | 2500 × 4,50% |
| valorIbs | 440.50 | |
| valorCbs | 220.00 | 2500 × 8,80% |
| regimeAplicado | MONOFASICO | |

---

## D. Serviço (NFS-e, IBS pelo local da prestação)

### D1 — Saúde R$ 300, redução 60% ✅
`cfop=5933, codigoServico=4.01, ibgeLocalPrestacao=3550308, valorOperacao=300, regime=LUCRO_REAL`
(sem `ncm` → tratado como serviço; IS sempre 0 em serviço)

| Campo | Esperado | Conta (alíquota × fator 0,40) |
|---|---|---|
| valorIs | 0 | serviço |
| baseCalculo | 300 | |
| valorIbsEstadual | 15.74 | 300 × (13,12% × 0,40) = 300 × 5,248% |
| valorIbsMunicipal | 5.40 | 300 × (4,50% × 0,40) = 300 × 1,80% |
| valorIbs | 21.14 | |
| valorCbs | 10.56 | 300 × (8,80% × 0,40) = 300 × 3,52% |
| regimeAplicado | REDUCAO_60 | |

### D2 — Saúde R$ 1.000, redução 60% ➕
`cfop=5933, codigoServico=4.01, ibgeLocalPrestacao=3550308, valorOperacao=1000, regime=LUCRO_REAL`

| Campo | Esperado | Conta |
|---|---|---|
| baseCalculo | 1000 | |
| valorIbsEstadual | 52.48 | 1000 × 5,248% |
| valorIbsMunicipal | 18.00 | 1000 × 1,80% |
| valorIbs | 70.48 | |
| valorCbs | 35.20 | 1000 × 3,52% |
| regimeAplicado | REDUCAO_60 | |

### D3 — Serviço genérico R$ 500, sem redução ➕
`cfop=5933, codigoServico=1.01, ibgeLocalPrestacao=3550308, valorOperacao=500, regime=LUCRO_REAL`
(serviço `1.01` não mapeado → PADRAO, alíquota cheia)

| Campo | Esperado | Conta |
|---|---|---|
| baseCalculo | 500 | |
| valorIbsEstadual | 65.60 | 500 × 13,12% |
| valorIbsMunicipal | 22.50 | 500 × 4,50% |
| valorIbs | 88.10 | |
| valorCbs | 44.00 | 500 × 8,80% |
| regimeAplicado | PADRAO | |

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
| valorSplitIbs | 1762.00 (= valorIbs) |
| valorSplitCbs | 880.00 (= valorCbs) |

### F2 — Notebook R$ 10.000, flag ligada + pagamento não splitável ✅
A1 + `splitPaymentAplicavel=false`, `enabled=true`

| Campo | Esperado | Nota |
|---|---|---|
| valorIbs / valorCbs | 1762.00 / 880.00 | tributo continua destacado |
| valorSplitIbs / valorSplitCbs | 0.00 / 0.00 | campo presente, nada a segregar |

### F2b — Notebook R$ 10.000, flag desligada ✅
A1 + `splitPaymentAplicavel=true`, `enabled=false`, tenant fora da allowlist

| Campo | Esperado | Nota |
|---|---|---|
| valorIbs / valorCbs | 1762.00 / 880.00 | flag não altera o cálculo |
| valorSplitIbs / valorSplitCbs | `null` — ausentes no JSON | nada informado à Plataforma Pública |

### F2c — Allowlist por tenant ✅
`enabled=false`, `tenants=[tenant-piloto]`: `tenantId=tenant-piloto` ⇒ split **1762.00**;
qualquer outro tenant ⇒ campos ausentes. Cobre o piloto de 2026 sem ligar para a base toda.

### F3 — Serviço saúde R$ 300, flag ligada ➕
D1 + `splitPaymentAplicavel=true` → valorSplitIbs **21.14**, valorSplitCbs **10.56** (espelha o tributo reduzido).

---

## G. Erros / exceções (`FiscalException.getCodigo()`)

| # | Cenário | Request | Código esperado | Teste |
|---|---|---|---|---|
| G1 | CFOP inexistente | `cfop=9999, ncm=84713012, ibge=3550308, 2027, LUCRO_REAL` | `FISCAL_CFOP_NAO_ENCONTRADO` | ✅ |
| G2 | Município sem alíquota IBS | `cfop=5101, ncm=84713012, ibgeDestino=9999999, 2027, LUCRO_REAL` | `FISCAL_MUNICIPIO_SEM_ALIQUOTA_IBS` | ➕ |
| G3 | Vigência sem cobertura (ano) | `cfop=5101, ncm=84713012, ibge=3550308, dataCompetencia=2026-03-15, LUCRO_REAL` | `FISCAL_MUNICIPIO_SEM_ALIQUOTA_IBS` | ➕ |
| G4 | Regime sem alíquota CBS | `cfop=5101, ncm=84713012, ibge=3550308, 2027, regime=SIMPLES_NACIONAL` | `FISCAL_REGIME_SEM_ALIQUOTA_CBS` | ➕ |
| G5 | CFOP de entrada em saída | CFOP `tipoOperacao=ENTRADA` | `FISCAL_CFOP_INVALIDO_SAIDA` | ⚠️ |
| G6 | Split ligado sem forma de pagamento | A1 + `splitPaymentAplicavel=null`, `enabled=true` | `FISCAL_SPLIT_SEM_FORMA_PAGAMENTO` | ✅ |
| G7 | NCM e serviço juntos | `cfop=5933, ncm=84713012, codigoServico=4.01` | `FISCAL_NCM_E_SERVICO_CONFLITANTES` | ✅ |
| G8 | Nem NCM nem serviço | `cfop=5101, ibge=3550308, sem ncm/codigoServico` | `FISCAL_NCM_OU_SERVICO_OBRIGATORIO` | ✅ |

> G6 só dispara com a flag ligada: sem `splitPaymentAplicavel` não dá pra distinguir "pagamento não
> splitável" de "o chamador esqueceu de mandar". Com a flag desligada o campo segue opcional.
> G7/G8 são o XOR produto × serviço — validado no motor (código `FISCAL_*`), não por bean validation.

> **G5 não é reproduzível com a seed atual** — `TabelaFiscalInMemory` só tem CFOP de SAÍDA. Para cobrir,
> seedar um CFOP de ENTRADA (ex. `1101`) ou deixar para a fatia com `fiscal.cfop` no DB. O caminho de
> código (`cfop.tipoOperacao() != SAIDA → FISCAL_CFOP_INVALIDO_SAIDA`) existe e está pronto.

---

## Resumo de cobertura

- **✅ no teste atual (13 no `MotorFiscalServiceTest`):** A1, B1, C1, C2, D1, E1, F1, F2, F2b, F2c, G1, G6, G7, G8, + `memoriaCalculo não vazia`.
- **✅ no `MotorFiscalControllerTest` (4):** 200 com `X-Tenant-Id` e sem campos de split no JSON, 400 de bean validation, 400 de corpo mal-formado, `FiscalException` → 400 com o código no `message`.
- **➕ candidatos a adicionar:** A2, A3, B2, C2b, C3, D2, D3, E2, F3, G2, G3, G4 (e G5 quando houver CFOP de entrada).

Prioridade dos novos: **arredondamento** (A2/A3) e **exceções** (G2–G4) — são os que pegam regressão de
borda que os casos "redondos" não pegam.