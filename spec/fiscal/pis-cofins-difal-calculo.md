# Cálculo real de PIS/COFINS (Lucro Presumido) e DIFAL/FCP no `fiscal-service`

> Última atualização: 26 de setembro de 2026

Status: **Fase A (PIS/COFINS) ainda spec, nada implementado** — issue
[#101](https://github.com/looperperp-main/lerp-system/issues/101). **Fase B
(DIFAL/FCP) implementada em código em 26/09/2026** — issue
[#103](https://github.com/looperperp-main/lerp-system/issues/103):
`AliquotaInterestadual`, DIFAL/FCP dentro de `MotorFiscalService.calcularLegado`,
`fiscal.difal_uf` (changesets `fiscal-schema-019`/`020`, 27 UFs em método
`UNICA` — ver ponytail no changelog sobre `DUPLA`), testes em
`MotorFiscalServiceTest`. **✅ Testado pelo usuário em 26/09/2026** — `mvn`
verde e migração Liquibase (`fiscal-schema-019`/`020`) aplicada com sucesso.

## 1. Por que existe

- `fiscal-service` hoje só emite o aviso
  `Constants.FISCAL_AVISO_PIS_COFINS_APURACAO_EXTERNA` em
  `MotorFiscalService.calcularLegado` quando `TransicaoAno.pisCofinsVigente()` —
  nunca calcula o tributo. Base legal e histórico:
  `spec/fiscal/motor-fiscal-proximos-passos.md` item 7.9 (art. 348 da LC
  214/2025: PIS/COFINS continuam devidos **integralmente** em 2026; a
  "compensação" é só pra quem descumprir a obrigação acessória). Calcular o
  tributo de verdade não contradiz essa decisão — só nunca foi feito.
- `spec/modulos/emissao-fiscal/emissao-fiscal.md` §3 item 9 ("PIS/COFINS e os
  condicionais", 22/09/2026): enquanto não houver cálculo, o snapshot tem os
  campos como opcionais e `POST /emissao/documentos` responde 400 em NF-e de
  produto de regime normal sem eles. Esta spec é o que tira esse 400 do
  caminho — preenchendo os campos, não relaxando a guarda.
- O `emissao-fiscal-service` continua executor puro: **tudo que está aqui muda
  só o `fiscal-service`** (mais o repasse de campos no `operacoes-service`,
  §6).

## 2. Achados no código que mudam o desenho

1. **Validade curta do PIS/COFINS.** `TransicaoAno.pisCofinsVigente` só é
   `true` em 2026 (extintos em 2027, `motor-fiscal-proximos-passos.md`
   §"Calendário"). Confirmado direto no seed carregado
   (`liquibase-service/.../fiscal/fiscal-schema-008.yaml`, tabela
   `fiscal.transicao_ano`): `(2026, 100.00, true)`, `(2027, 100.00, false)` —
   extinção de uma vez (vira CBS), não é curva gradual. Na data desta spec
   restam ~3 meses de competência. O cálculo continua necessário (notas de
   out-dez/2026, reemissão e correção de snapshot de competência 2026,
   `emissao-fiscal.md` §3 item 11 "nova versão"), mas justifica o recorte
   mínimo: sem tabela nova, sem parametrização por tenant — não vale
   investir além disso numa regra que deixa de existir em poucos meses.
   **Contraste com a Fase B:** DIFAL/FCP são mecanismos do ICMS, e o ICMS
   fica em `pct_remanescente = 100` até 2028 (o mesmo seed), só reduz de
   2029 a 2032 e zera em 2033 — validade bem mais longa, não tem esse
   problema de prazo curto. Issue [#101](https://github.com/looperperp-main/lerp-system/issues/101)
   atualizada com esta nota em 23/09/2026.
2. **Venda interestadual de produto hoje dá 400.** `calcularLegado` chama
   `TabelaFiscal.aliquotaIcms(tenant, ncm, ufOrigem, ufDestino, data)`; a
   `SQL_MATRIZ_ICMS` (`TabelaFiscalJdbc.java:130`) filtra
   `uf_origem = :ufOrigem AND uf_destino = :ufDestino`, e a carga
   (`fiscal-schema-011.yaml`, `fiscal-031`) só tem linhas com
   `uf_origem = uf_destino`. O próprio changeset diz que a alíquota
   interestadual (4/7/12%) "é função sobre lista de UF, não dado" — a função
   nunca foi escrita. Resultado: qualquer saída interestadual de produto com
   `pctRemanescente > 0` (2026-2032, ou seja, hoje) cai em
   `FISCAL_ICMS_SEM_COBERTURA`, mesmo com `cfop_regra` já resolvendo 6102.
   DIFAL depende exatamente dessa alíquota, então **a Fase B começa por aqui**.
3. **FCP embutido na alíquota interna.** As linhas de RJ (22%, "inclui
   FECP") e SE (20%, "inclui FUNPOBREZA") em `fiscal.matriz_tributaria`
   guardam ICMS + FCP somados. A NF-e exige `pICMS` e `pFCP` separados (e o
   DIFAL exige `pICMSUFDest` e `pFCPUFDest` separados) — a carga tem que ser
   desmembrada.
4. **DIFAL foi declarado fora de escopo em 29/07/2026**
   (`motor-fiscal-proximos-passos.md` §"Decisão (29 de julho de 2026)", linhas
   105 e 162-164: "Nada de ST, MVA, DIFAL, pauta…"). A Fase B **reabre essa
   decisão só para DIFAL/FCP de consumidor final não contribuinte** —
   motivo: a guarda de 22/09 da emissão bloqueia essas vendas, então sem o
   cálculo o ERP simplesmente não fatura e-commerce/venda B2C interestadual.
   **Precisa de aprovação explícita do usuário antes de implementar.** ST/MVA,
   pauta e IPI continuam fora (§7), mantendo a decisão de 29/07.

## 3. Fases

| Fase | Escopo | Issue | Depende de |
|---|---|---|---|
| **A** | PIS/COFINS cumulativo, Lucro Presumido, produto | #101 | nada |
| **B** | Alíquota interestadual + FCP separado + DIFAL/FCP destino (EC 87/2015) | nova | aprovação do item 2.4 |

As duas são independentes; A pode ir sozinha.

## 4. Fase A — PIS/COFINS cumulativo

### 4.1 Regra

Só quando **todas** valem; fora disso os campos saem `null` (e a guarda da
emissão faz o resto):

- `transicao(ano).pisCofinsVigente()` (competência ≤ 2026);
- saída (`!entrada` — Lucro Presumido não tem crédito);
- produto (`ncm` preenchido; serviço fica fora desta fase, §7);
- `regimeEmpresa == Constants.REGIME_LUCRO_PRESUMIDO`;
- caminho que passa pelo PASSO 3 (não MEI, não `zerado()` — ver 4.3);
- `pisCofinsRegimeEspecial` **não** declarado `true` (4.2).

Cálculo:

```
baseCalculoPis = baseCalculoCofins = valorTributavel − valorIcms (ICMS próprio destacado)
valorPis    = pct(base, 0,65)   // Lei 9.715/1998
valorCofins = pct(base, 3,00)   // Lei 9.718/1998
cstPis = cstCofins = "01"       // operação tributável, alíquota básica
```

- `valorTributavel` é o do PASSO 0.5 (frete/seguro/acessórias entram, desconto
  incondicional sai), **antes** da exclusão do art. 57 §7º do PASSO 0.6 —
  aquela exclusão é regra de IBS/CBS, não de PIS/COFINS. Guardar o valor do
  PASSO 0.5 numa variável própria antes do PASSO 0.6 mutá-lo.
- **Exclusão do ICMS destacado** (STF RE 574.706, Tema 69; Parecer SEI
  7.698/2021): usa `legado.icms()` — o ICMS próprio, não o DIFAL. Em 2026
  `pctRemanescente` é 100, então é o ICMS cheio.
- IS não entra (não existe IS em 2026). IBS/CBS de 2026 não entram na base.
- Arredondamento: `pct()` existente (HALF_UP, 2 casas).
- Alíquotas e CST como constantes em `Constants.java`
  (`FISCAL_PIS_CUMULATIVO_PCT`, `FISCAL_COFINS_CUMULATIVO_PCT`,
  `FISCAL_CST_PIS_COFINS_TRIBUTAVEL_BASICA`). `ponytail:` sem tabela — valor
  fixado em lei, tributo extinto em 2027; vira tabela só se Lucro Real
  (alíquotas 1,65/7,6) entrar no escopo.

### 4.2 Monofásico / alíquota zero de PIS/COFINS

O motor **não tem como saber** se o NCM é monofásico ou alíquota zero de
PIS/COFINS (Lei 10.147/2000, Lei 10.925/2004 etc.) — são listas diferentes do
`RegimeDiferenciado` (que é IBS/CBS). Mesmo padrão "declarado, não deduzido"
de `cClassTrib`/retenção:

- novo campo opcional `Boolean pisCofinsRegimeEspecial` em
  `MotorFiscalRequest`;
- `true` ⇒ campos PIS/COFINS `null` + linha de memória
  (`FISCAL_AVISO_PIS_COFINS_REGIME_ESPECIAL`) ⇒ a emissão bloqueia, em vez de
  destacar 3,65% num produto que não deve;
- ausente/`false` ⇒ calcula e registra na memória que assumiu regime
  cumulativo básico (mesma lógica do aviso de `PADRAO`: erro contra o
  contribuinte nunca sai calado).

### 4.3 Caminhos que continuam `null` (e por quê)

| Caminho | Motivo |
|---|---|
| MEI | não destaca tributo (MF-02) |
| Simples Nacional | PIS/COFINS dentro do DAS; CST 49/99 é outra fase |
| Lucro Real | não-cumulativo, com crédito — fora do escopo |
| `zerado()` (alíquota zero / monofásico de IBS/CBS) | a desoneração de IBS/CBS não implica a de PIS/COFINS; calcular 3,65% ali seria chute. `null` + aviso ⇒ bloqueio na emissão |
| Entrada | LP não credita |
| Serviço | fora desta fase (§7) |
| competência ≥ 2027 | tributo extinto — **não** é aviso, é ausência normal |

O aviso `FISCAL_AVISO_PIS_COFINS_APURACAO_EXTERNA` deixa de sair quando o
PIS/COFINS foi calculado (Lucro Presumido produto); continua saindo nos demais
casos de 2026. Texto da constante revisado para não dizer que "o recolhimento
é apurado fora" como se fosse o único caminho — o recolhimento (DARF mensal)
continua fora do motor, mas o valor da nota agora é dele.

## 5. Fase B — alíquota interestadual, FCP e DIFAL

### 5.1 Quando o DIFAL se aplica

EC 87/2015 + LC 190/2022 + Convênio ICMS 236/2021. Calcula quando **todas**:

- saída de produto, `ufOrigem ≠ ufDestino`;
- destinatário consumidor final (`indFinal = 1`) **e** não contribuinte do
  ICMS (`indIEDest = 9`);
- regime normal (LP/LR). Simples Nacional fica fora nesta fase (tratamento do
  DIFAL do Simples como remetente é controverso desde a ADI 5.464 — validar
  com o contador antes de abrir).

Destinatário **contribuinte** (`indIEDest = 1`) consumidor final: o DIFAL é
recolhido pelo destinatário, não vai no grupo `ICMSUFDest` da NF-e do
emitente — fora do motor.

Partilha: 100% para a UF de destino desde 2019 (ADCT art. 99) ⇒
`valorIcmsUfRemetente = 0`, `percentualPartilhaDestino = 100` — constantes, não
tabela.

### 5.2 Mesma chamada, sem endpoint novo

DIFAL entra **dentro de `POST /fiscal/calcular`**, no ramo de produto de
`calcularLegado`. Justificativa pelo código:

- usa exatamente os mesmos insumos que o ICMS legado já recebe (`ufOrigem`,
  `ufDestino`, `ncm`, `valorTributavel`, `tenantId`, `TransicaoAno`) e a mesma
  fonte (`TabelaFiscal.aliquotaIcms` para a alíquota interna do destino);
- o DIFAL depende do ICMS de origem (base dupla, 5.4) — separar em outro
  endpoint obrigaria o chamador a orquestrar dois cálculos e juntar;
- o snapshot da emissão (`emissao-fiscal.md` §3 item 11) espelha **um**
  `OperacaoFiscalDTO` por item — um resultado só, imutável, é o que o desenho
  do snapshot pressupõe;
- o `pctRemanescente` da transição (2029-2032) vale para o DIFAL (é ICMS), e
  esse fator já vive em `calcularLegado`.

### 5.3 Alíquota interestadual (pré-requisito, fecha o achado 2.2)

Função pura (não tabela, como o `fiscal-schema-011` já decidiu), p.ex.
`AliquotaInterestadual.de(ufOrigem, ufDestino, origemProduto)` em
`services/fiscal/`:

- `origemProduto == ESTRANGEIRO` ⇒ 4% (Res. Senado 13/2012);
- origem S/SE exceto ES (SP, RJ, MG, PR, SC, RS) com destino N/NE/CO ou ES ⇒ 7%;
- demais ⇒ 12% (Res. Senado 22/1989).

`calcularLegado` passa a: `ufOrigem == ufDestino` ⇒ matriz (como hoje);
`≠` ⇒ função acima para o ICMS próprio da operação. Isso vale para **toda**
venda interestadual, com ou sem DIFAL — é o que tira o 400 atual.

`ponytail:` origem `ESTRANGEIRO` ⇒ 4% ignora a nuance do conteúdo de
importação ≤ 40% (origens 3/5/8 da NF-e); `origemProduto` hoje não distingue
isso. Upgrade quando o cadastro de produto trouxer a origem de 0 a 8.

### 5.4 Cálculo

Alíquota interna do destino = `aliquotaIcms(tenant, ncm, ufDestino, ufDestino, data)`
(linha interna da UF de destino — já existe na matriz). FCP do destino =
nova coluna da mesma linha (5.5). Método de base por UF de destino (5.5):

- **Base única:** `vBCUFDest = valorTributavel`;
  `vICMSUFDest = pct(vBC, pInternaDest − pInter)`.
- **Base dupla (Conv. 236/2021):**
  `vBCUFDest = (valorTributavel − ICMSorigem) / (1 − pInternaDest/100)`;
  `vICMSUFDest = pct(vBCUFDest, pInternaDest) − ICMSorigem`.
- `vBCFCPUFDest = vBCUFDest`; `vFCPUFDest = pct(vBCFCPUFDest, pFcpDest)`.
- Transição: DIFAL e FCP multiplicados pelo mesmo `fatorLegado`
  (`pctRemanescente/100`) do ICMS.
- Divisão da base dupla com escala interna ≥ 10 e arredondamento só no valor
  final (mesmo cuidado do `fatoresEfetivos`).

FCP também na **operação interna** (`pFCP`/`vFCP` do grupo ICMS00): com a
alíquota desmembrada (achado 2.3), o motor passa a devolver
`valorFcp = pct(base ICMS, pFcp)` na venda interna da UF que tem FCP — senão
o desmembramento faria o ICMS interno de RJ/SE cair 2 pontos em silêncio.

### 5.5 Dados

- `fiscal.matriz_tributaria` ganha `p_fcp numeric(5,2) default 0 not null`
  (FCP é por UF **e** por NCM em vários estados — mesma granularidade da
  alíquota, por isso na mesma linha). Changeset novo desmembra RJ (20 + 2) e
  SE (18 + 2 — **conferir** com o contador) via nova linha com `vigente_de`,
  fechando a antiga com `vigente_ate` (historização já prevista na
  constraint `uk_matriz_tributaria`). Demais UFs: carga de FCP por NCM é
  levantamento à parte; começa em 0, mesmo critério "override quando cliente
  real reclamar" do `fiscal-031`.
- Tabela nova `fiscal.difal_uf` (`uf`, `metodo_base` `'UNICA'|'DUPLA'`,
  `vigente_de`, `vigente_ate`), 27 linhas — é atributo da UF de destino, não
  do NCM, por isso não vai na matriz. UF sem linha vigente ⇒ 400
  `FISCAL_DIFAL_SEM_COBERTURA` (nunca assume base única calado).
- `RegimeIcms` ganha `pFcp`; `TabelaFiscal` ganha
  `Optional<String> metodoBaseDifal(String ufDestino, LocalDate competencia)`;
  `TabelaFiscalJdbc`/`TabelaFiscalFake` implementam.

### 5.6 Entrada nova

`MotorFiscalRequest` ganha `indFinal` (`"0"|"1"`) e `indIEDest`
(`"1"|"2"|"9"`) — nomes da NF-e, declarados pelo chamador. Em saída de
produto interestadual os dois são **obrigatórios** (400
`FISCAL_DESTINATARIO_INDICADORES_OBRIGATORIOS`): sem eles não dá pra
distinguir "sem DIFAL" de "esqueceram". Não quebra ninguém — essa combinação
já dá 400 hoje (achado 2.2).

## 6. Modelo de dados — `OperacaoFiscalDTO`

Nomes seguem o padrão já usado no DTO (`percentualIbsUf`, `valorIcms`…);
tabela de/para com o snapshot da emissão abaixo. Todos `null` fora do caminho
em que se aplicam (`@JsonInclude(NON_NULL)` já cuida do JSON).

| `OperacaoFiscalDTO` (novo) | Snapshot / tag NF-e |
|---|---|
| `cstPis` / `cstCofins` | `cstPis` / `cstCofins` (CST) |
| `baseCalculoPis` / `baseCalculoCofins` | `vBcPis` / `vBcCofins` (vBC) |
| `percentualPis` / `percentualCofins` | `pPis` / `pCofins` |
| `valorPis` / `valorCofins` | `vPis` / `vCofins` |
| `percentualFcp` / `valorFcp` | `pFCP` / `vFCP` (ICMS00) |
| `percentualIcmsInterestadual` | `pICMSInter` |
| `baseCalculoUfDestino` | `vBCUFDest` |
| `baseCalculoFcpUfDestino` | `vBCFCPUFDest` |
| `percentualIcmsUfDestino` | `pICMSUFDest` |
| `percentualFcpUfDestino` | `pFCPUFDest` |
| `percentualPartilhaDestino` | `pICMSInterPart` (sempre 100) |
| `valorIcmsUfDestino` | `vICMSUFDest` |
| `valorFcpUfDestino` | `vFCPUFDest` |
| `valorIcmsUfRemetente` | `vICMSUFRemet` (sempre 0) |

Os oito campos de PIS/COFINS da coluna direita são os nomes exatos de
`emissao-fiscal.md` §3 item 9. **Os de FCP/DIFAL ainda não têm nome naquela
spec** (ela só cita "campos de DIFAL/FCP"): ao implementar a Fase B, a lista
do snapshot em `emissao-fiscal.md` §3 item 11 (`pedido_item_fiscal_snapshot`)
ganha essas colunas com os nomes da tag NF-e acima — atualização de doc a
fazer junto com o código, não agora.

Fora do `fiscal-service`, mas necessário para o valor chegar ao XML:
`FiscalServiceClient` (`operacoes-service`) envia `indFinal`/`indIEDest` e já
vai deixar de truncar o resultado (`emissao-fiscal.md` §3 item 11) — os campos
novos entram nesse mesmo DTO espelho.

## 7. Onde o código muda (só `fiscal-service` + `common` + Liquibase)

| Arquivo | Mudança | Fase |
|---|---|---|
| `common/.../util/Constants.java` | alíquotas/CST PIS-COFINS, avisos novos, códigos de erro 400 novos, texto revisado do aviso de apuração externa | A, B |
| `api/dto/MotorFiscalRequest.java` | `pisCofinsRegimeEspecial` (A); `indFinal`, `indIEDest` (B) | A, B |
| `api/dto/OperacaoFiscalDTO.java` | campos do §6 | A, B |
| `services/MotorFiscalService.java` | novo `calcularPisCofins(...)` chamado após `calcularLegado` (guardar `valorTributavel` pré-PASSO 0.6); ramo interestadual + DIFAL + FCP dentro de `calcularLegado`; record `Legado` ganha os campos de FCP/DIFAL | A, B |
| `services/fiscal/AliquotaInterestadual.java` (novo) | função 4/7/12% | B |
| `services/fiscal/RegimeIcms.java` | `pFcp` | B |
| `services/fiscal/TabelaFiscal.java` / `TabelaFiscalJdbc.java` | `p_fcp` no `SQL_MATRIZ_ICMS`; `metodoBaseDifal` | B |
| `liquibase-service/.../fiscal/fiscal-schema-0XX.yaml` | `p_fcp`, desmembramento RJ/SE, `fiscal.difal_uf` + carga | B |

Fase A não tem changeset.

## 8. Testes

No padrão de `MotorFiscalServiceTest` (serviço puro sobre `TabelaFiscalFake`,
nome `cenario_condicao_resultado`, valores conferidos à mão na memória de
cálculo). `MotorFiscalControllerTest` só ganha um caso de contrato (campos no
JSON / 400 novo).

**Fase A**
- `pisCofins_lucroPresumido_2026_calculaCumulativo` — base, 0,65/3,00, CST 01.
- `pisCofins_baseExcluiIcmsDestacado` — valor esperado = (V − ICMS) × alíquota.
- `pisCofins_baseComposta_freteEntraDescontoSai`.
- `pisCofins_vedacao57_naoReduzBasePisCofins` — PASSO 0.6 reduz IBS/CBS, não PIS/COFINS.
- `pisCofins_2027_naoCalculaNemAvisa`.
- `pisCofins_lucroReal_null_comAviso`; `pisCofins_simples_null`; `pisCofins_mei_null`.
- `pisCofins_regimeEspecialDeclarado_null_comAviso`.
- `pisCofins_caminhoZerado_null_comAviso` (NCM de alíquota zero IBS/CBS).
- `pisCofins_entrada_null`; `pisCofins_servico_null`.
- ajustar `legado_2026_avisaPisCofinsApuracaoExterna`: com LP produto o aviso
  **não** sai mais — o teste passa a usar regime/caminho em que ele ainda sai.

**Fase B** (UFs escolhidas para cobrir as três faixas)
- `interestadual_sp_para_mg_icms12` / `sp_para_ba_icms7` / `ba_para_sp_icms12` / `es_para_sp_icms12` / `importado_icms4`.
- `interestadual_contribuinte_semDifal` (`indIEDest=1`).
- `interestadual_consumidorFinalNaoContribuinte_baseUnica_calculaDifal`.
- `…_baseDupla_calculaDifal` (conferir número com planilha do contador).
- `…_destinoRj_separaFcp` (DIFAL + FCP destino).
- `interestadual_semIndicadores_lancaFiscalException`.
- `difal_ufSemMetodoVigente_lancaFiscalException`.
- `difal_transicao2030_aplicaPctRemanescente`.
- `difal_simplesNacional_null`.
- `interna_rj_devolveFcpSeparado` (regressão do desmembramento).
- `operacaoInterna_semAlteracao` — o ramo `ufOrigem == ufDestino` não muda
  para UF sem FCP (todos os testes de legado atuais continuam verdes).
- `TabelaFiscalJdbcTest`: `p_fcp` lido; `metodoBaseDifal` por vigência.

Nada disso foi rodado — é spec.

## 9. Fora de escopo (conscientemente)

- **Lucro Real / não-cumulativo** (1,65% + 7,6%, crédito na entrada).
- **Monofásico, alíquota zero, isenção e suspensão de PIS/COFINS** (CST 02-09)
  — só a declaração que bloqueia (4.2), não o cálculo.
- **PIS/COFINS no Simples Nacional** (CST 49/99) e **de serviço** (inclui a
  discussão de ISS na base).
- **PIS/COFINS-ST** e qualquer regime setorial (combustíveis, bebidas frias,
  ZFM, REIDI etc.).
- **ICMS-ST / MVA / pauta** — mantida a decisão de 29/07/2026
  (`motor-fiscal-proximos-passos.md` linhas 105 e 162-164: CST 60 chega com o
  imposto já retido por quem calculou fora).
- **IPI** — não há decisão anterior que o inclua; segue fora (os segmentos
  prioritários — serviço, EPI/tecnologia em revenda — não são industriais). Continua sob
  a guarda da emissão.
- **DIFAL de destinatário contribuinte** (uso/consumo/ativo) e **DIFAL do
  Simples Nacional**.
- **FCP por NCM** nas UFs que o cobram só sobre itens específicos — coluna
  existe, carga é levantamento posterior.
- **Apuração/recolhimento** (DARF de PIS/COFINS, GNRE do DIFAL) — é do
  `operacoes-service`/AR, mesmo motivo do item 5 de
  `motor-fiscal-proximos-passos.md`.

## 10. Pontos a validar com o contador antes de codar

1. Composição da base de PIS/COFINS em LP: frete/seguro/acessórias entram? ICMS
   excluído é o destacado (confirmado pelo Parecer SEI 7.698/2021)?
2. IBS/CBS de 2026 fora da base de PIS/COFINS — dispositivo exato.
3. Método de base (única × dupla) por UF de destino vigente em 2026.
4. Percentuais de FCP de RJ e SE (e se há outras UFs com FCP geral).
5. DIFAL de remetente do Simples Nacional — manter fora?
