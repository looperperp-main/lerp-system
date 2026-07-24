# Fontes de Dados Fiscais — Checklist de Coleta (Motor Fiscal)

**Última atualização:** 24 de julho de 2026
**Contexto:** o motor fiscal (`fiscal-service`, Fin.md Módulo I) roda hoje com valores **estimados**
(`TabelaFiscalInMemory`, marcados `ponytail:`). Este doc lista o que precisa vir de fonte oficial
para os números virarem reais. Serve de roteiro de coleta e de origem dos seeds Liquibase `fiscal.*`.

Legenda de **Bloqueio**:
- 🔴 **Duro** — sem isso não há alíquota/enquadramento correto; depende de publicação oficial ainda pendente.
- 🟡 **Médio** — fonte **em mãos** (arquivo em `spec/` ou portal público), só dá trabalho extrair/importar.
- 🟢 **Fácil / já temos** — dado em mãos ou trivial.

> **Mudança 24/07/2026:** as fontes oficiais foram coletadas para `spec/`. Só o **IS (item 3)** segue 🔴
> (regulamentação por NCM ainda não publicada). Todo o resto passou de 🔴 para 🟡 (extrair/importar) ou 🟢.

**🟡 vs 🟢 — por que a maioria é 🟡 mesmo com a fonte em mãos?** 🟢 = o dado já é tabular, é só carregar
(CSV de NCM, lista fechada da LC 116, tabela IBGE). 🟡 = a fonte existe mas **não** vem pronta pra `INSERT` —
precisa extrair/transformar/derivar, com risco de erro se feito no olho:
- **Item 1 e 2:** o [portal][portal] devolve por ente/ano e a curva é **fixada ano a ano** pelo Senado — não é um
  arquivo único, é **carga recorrente** (e o item 2 é por município, ~5.570 deles).
- **Item 4:** os Anexos estão num **PDF legal de 298 páginas** — transcrever NCM/NBS→regime é trabalho manual e de
  **alto risco** (dado fiscal errado = imposto errado).
- **Item 6:** a planilha tem os CFOP, mas `primeiraEtapaCadeia` **não está pronta** lá — precisa derivar por faixa.
- **Item 9:** não é dado pra carregar, é **implementar** o contrato de integração + a feature flag liga/desliga.

---

## Resumo por prioridade

| # | Dado | Fonte (em mãos) | Tabela-alvo | Bloqueio |
|---|---|---|---|---|
| 1 | Alíquotas de referência IBS/CBS por ano (curva de transição 2026–2033) | `spec/IBSCBS_Presentation_GOV.pdf` + [portal calculadora][portal] | `fiscal.aliq_ibs`, `fiscal.aliq_cbs` | 🟡 |
| 2 | Alíquotas IBS por ente (união/estado/município) | [portal calculadora][portal] | `fiscal.aliq_ibs` | 🟡 |
| 3 | Alíquotas do IS (Imposto Seletivo) por NCM | **ainda sem tabela oficial** | `fiscal.aliq_is` | 🔴 |
| 4 | De-para NCM/NBS → regime (Anexos LC 214/2025) | `spec/leicomplementar-214-...-pl.pdf` | `fiscal.regime_dif_ncm` | 🟡 |
| 5 | Tabela NCM (descrições) | `spec/Tabela_NCM_..._20260702.csv` | `fiscal.ncm` | 🟢 |
| 6 | Tabela CFOP (geradoras de crédito) | `spec/Tabela_CFOPOperacoesGeradorasCreditos.xlsx` | `fiscal.cfop` | 🟡 |
| 7 | Códigos de município IBGE | IBGE (público) | referência (`fiscal.aliq_ibs.ibge`) | 🟢 |
| 8 | Lista de serviços LC 116/2003 | `spec/LC1162003.pdf` | `fiscal.regime_dif_ncm` (NBS) | 🟢 |
| 9 | Regras de Split Payment | `spec/03153733-manual-de-integracao-...v1.pdf` + `spec/30145925-minuta-split-payment-manual-de-operacoes.pdf` | lógica (`splitPaymentAplicavel`) + contrato PSP | 🟡 |

[portal]: https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/calculadora/aliquotas

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
- **⚠️ Por que ainda 🟡 e não 🟢:** as alíquotas de referência são **fixadas anualmente pelo Senado** (cálculo do
  TCU, até 31/out do ano anterior à vigência). A curva cheia 2029–2033 vai sendo publicada ano a ano — modelar
  por `(ente, ano)` já absorve isso; carregar do portal a cada fixação.

### 2. 🟡 Alíquotas IBS por ente (união/estado/município)
- **O que extrair:** parcela estadual e municipal do IBS por unidade (chave = código IBGE do município de
  destino; o estado deriva dele). O motor já separa `estadual` + `municipal`.
- **Fonte em mãos:** [portal calculadora][portal] — consulta por União, Estado e Município.
- **Formato-alvo:** `ibge_municipio` (7 díg.) → (aliq_estadual %, aliq_municipal %), por ano.
- **Nota:** no começo a maioria usa a alíquota de referência; entes podem fixar a própria depois. Tabela por
  `(ibge, ano)` cobre os dois casos.

### 3. 🔴 Alíquotas do IS (Imposto Seletivo) por NCM — ÚNICO BLOQUEIO DURO RESTANTE
- **O que extrair:** quais NCM sofrem IS e a alíquota de cada um (cigarro, bebidas açucaradas, veículos
  poluentes, extração mineral...). Hoje só cigarro está chutado (150%).
- **Status:** **não há tabela oficial publicada** por NCM. Enquanto não sair a regulamentação específica do IS,
  esse número permanece placeholder.
- **Regra do motor (já implementada):** IS incide antes e **integra a base** de IBS/CBS (Fin.md §1.4.2 Passo 5).
  Só falta a tabela de alíquotas; a matemática não muda quando ela chegar.

### 4. 🟡 De-para NCM/NBS → regime diferenciado (Anexos LC 214/2025)
- **O que extrair:** por Anexo, a lista de NCM (produtos) e NBS (serviços) e o regime correspondente
  (`ANEXO_I_ZERO`, `ANEXO_*_60`, `MONOFASICO`, `ISENTO`, `IMUNE`, `ZFM`...).
- **Fonte em mãos:** `spec/leicomplementar-214-16-janeiro-2025-796905-normaatualizada-pl.pdf` (298 págs.,
  texto consolidado com os anexos) — **fonte da verdade**.
- **Formato-alvo:** `codigo` (NCM 2–8 díg. ou NBS), `regime`, `percentual_reducao` (0/30/60/100), `descricao`.
- **Destino:** `fiscal.regime_dif_ncm` (seed). Trabalho = transcrever os anexos do PDF. Catálogo de regimes é
  fechado (enum, não CRUD) — ver ADR Fin.md §1.4.4.

### 5. 🟢 Tabela NCM (descrições) — JÁ TEMOS
- **Fonte:** `spec/Tabela_NCM_Desc_Concatenada_Vigente_20260702.csv` (~61k linhas, `;`, header na linha 5).
- **Ação:** importar para `fiscal.ncm` (script de seed / job de carga). Não bloqueia nada.

### 6. 🟡 Tabela CFOP (operações geradoras de crédito)
- **O que extrair:** CFOP com natureza (entrada/saída) e as flags do motor (`geraCreditoIbs`, `geraCreditoCbs`,
  `primeiraEtapaCadeia`). Hoje só 4 CFOP semeados.
- **Fonte em mãos:** `spec/Tabela_CFOPOperacoesGeradorasCreditos.xlsx` — já mapeia justamente as operações que
  **geram crédito** de IBS/CBS, que é o que as flags precisam.
- **Nota:** `primeiraEtapaCadeia` (produção própria/importação vs revenda) pode exigir derivação por faixa de
  CFOP — confirmar critério contra a planilha.

### 7. 🟢 Códigos de município IBGE
- **Fonte:** IBGE (tabela de municípios, 7 dígitos). Pública, estável.
- **Uso:** chave de `fiscal.aliq_ibs` e validação do `ibgeDestino`/`ibgeLocalPrestacao` do request.

### 8. 🟢 Lista de serviços LC 116/2003
- **Fonte em mãos:** `spec/LC1162003.pdf` (anexo com a lista de serviços, ex. `4.01` saúde).
- **Uso:** validar `codigoServico` e ligar serviço → regime (NBS) em `fiscal.regime_dif_ncm`.

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
- **⚠️ Requisito: split payment atrás de botão liga/desliga (feature flag).** A adesão ao split é **faseada**
  (piloto, entrada gradual dos PSPs/arranjos, 2026 é período de teste, obrigatoriedade progressiva). O ERP precisa
  **ligar e desligar** o split — por config e, provavelmente, **por tenant** (cada empresa/estabelecimento pode
  entrar em momento diferente). Desligado: o motor calcula normal, o documento fiscal **não** carrega os campos de
  split e nenhum informe é emitido à Plataforma Pública. Ligado: emite `vlIbsInf`/`vlCbsInf` e os informes.
  Default = **desligado** até a integração PSP estar homologada.

---

## O que dá pra construir AGORA (só o IS 🔴 continua bloqueando)

Com as fontes acima em mãos, dá pra sair do in-memory para dados reais:
- Tabelas Liquibase: `fiscal.ncm`, `fiscal.cfop`, `fiscal.aliq_ibs`, `fiscal.aliq_cbs`, `fiscal.aliq_is`
  (+ `fiscal.regime_dif_ncm`, já criada).
- Import do CSV de NCM (item 5) → `fiscal.ncm`.
- Import da planilha CFOP (item 6) → `fiscal.cfop` com as flags de crédito.
- Seed dos serviços LC 116 (item 8) e transcrição dos Anexos da LC 214 (item 4) → `fiscal.regime_dif_ncm`.
- Carga das alíquotas 2026/2027 (item 1) + parcelas por ente do portal (item 2) → `fiscal.aliq_ibs`/`aliq_cbs`.
- `TabelaFiscalJpa implements TabelaFiscal` no lugar do in-memory (swap de 1 linha no wiring).

**Só o IS (item 3) fica com valor estimado** até a regulamentação por NCM sair — e, como o IS já entra na base
pela lógica testada em `MotorFiscalServiceTest`, é só trocar a tabela quando o número oficial chegar.

> ⚠️ Enquanto o **IS** não vier, é seguro desenvolver/demonstrar com o valor estimado marcado `ponytail:`, mas
> **não** emitir documento fiscal real com esse número.