# Apuração Fiscal Recorrente — Checklist por Imposto

**Última atualização:** 05 de agosto de 2026
**Público-alvo:** quem for contratado para manter o conteúdo fiscal do `fiscal-service` em dia — não
pressupõe conhecimento do schema `fiscal.*`, só da legislação. Organizado por **imposto**, não por
tabela (a versão técnica por tabela está em `spec/Fin.md` §1.12; a versão "onde já coletamos os dados
originais" está em `spec/fontes-dados-fiscais.md`).

Cada imposto abaixo tem: **o que muda**, **onde consultar** (fonte oficial), **de quanto em quanto
tempo** conferir, e **o que quebra no sistema se atrasar**. "Não consumido pelo motor hoje" quer dizer
que o `fiscal-service` não calcula esse imposto ainda — só documentado porque a empresa segue precisando
apurá-lo enquanto ele existir (2026-2033).

**A planilha para preencher é `spec/planilha-apuracao-fiscal.xlsx`** (mesma pasta deste arquivo) — já vem
com uma aba por imposto, cabeçalho, uma linha de exemplo preenchida com valor real e uma aba de códigos
de UF. Abra a aba **Leia-me** dela primeiro. Depois de preenchida, salvar na pasta compartilhada
combinada com o responsável do projeto (Drive/OneDrive) — não circular por e-mail solto.

**Exemplo completo, do zero, pra não ter dúvida do formato** — apurar o IBS estadual de São Paulo em
2033:
1. Abrir no navegador: `https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/api/calculadora/dados-abertos/aliquota-uf?data=2033-01-01&codigoUf=35` (35 = código da UF de SP, ver aba **Códigos UF (IBGE)** da planilha)
2. A página mostra só isto: `{"aliquotaReferencia": 16.0}`
3. Na planilha, aba **IBS**, nova linha: `ano_vigencia=2033`, `uf=SP`, `aliquota_estadual=16.0`, `fonte_url=<a URL do passo 1>`, `data_consulta=<data de hoje>`, `responsavel=<seu nome>`.
4. Repetir trocando só `data` no passo 1 pra pegar outro ano, ou `codigoUf` pra pegar outro estado (mas ver observação de uniformidade no item IBS abaixo — não precisa repetir por estado toda vez).

---

## IBS — Imposto sobre Bens e Serviços (estadual + municipal)

| | |
|---|---|
| **O que muda** | Alíquota de **referência** (fixada pelo Senado, parte estadual + parte municipal) do ano seguinte; e alíquotas **próprias** que um estado ou município tenha legislado acima/abaixo da referência |
| **Onde** | Referência: Resolução do Senado Federal (cálculo do TCU, CF art. 156-A + LC 214) — hoje espelhada pela [API do piloto CBS](https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/api/calculadora/dados-abertos/aliquota-municipio). Própria: diário oficial de cada estado/município |
| **Frequência** | Anual. Curva 2026-2033 já carregada; **a partir de 2034 é obrigatório carregar todo ano**. Alíquota própria: sempre que o ente publicar (normalmente dezembro, antes do ano vigente) |
| **Cobertura hoje** | 2026-2033, só a referência nacional (nenhum município com alíquota própria carregada ainda) |
| **Se atrasar** | Ano sem linha → erro `FISCAL_VIGENCIA_SEM_COBERTURA` (400) em toda operação daquela competência |

**Como apurar, passo a passo:**
1. Abrir a calculadora do piloto: https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/calculadora/aliquotas
2. Ou direto pela API (abre no navegador, devolve JSON), uma chamada por UF e uma por município:
   - Estadual: `https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/api/calculadora/dados-abertos/aliquota-uf?data=AAAA-MM-DD&codigoUf=NN` (`NN` = código IBGE da UF, ex. `31` = MG, `35` = SP)
   - Municipal: `.../aliquota-municipio?data=AAAA-MM-DD&codigoMunicipio=NNNNNNN` (código IBGE de 7 dígitos do município)
3. `data` é qualquer data dentro do ano que se quer consultar (ex. `2029-01-01` para pegar a alíquota de 2029).
4. A resposta vem como `{"aliquotaReferencia": n}` — **em percentual**. Copiar esse número.
5. Anotar na planilha (aba **IBS**, ver §Planilha no fim deste doc): `ano_vigencia`, `codigo_ibge_municipio`, `uf`, `aliquota_estadual` (resultado do endpoint UF) e `aliquota_municipal` (resultado do endpoint município).
6. ⚠️ **Rate limit:** depois de ~15 chamadas em rajada o portal passa a devolver HTML de erro em todos os endpoints, inclusive os que já tinham respondido. Espaçar ~5s entre chamadas resolve.
7. A alíquota de referência é igual para todo município de uma mesma competência (só muda por ano, não por ente) — **não é preciso** consultar os ~5.570 municípios um a um. Uma chamada de UF + uma de município por ano já confirma que continua uniforme; o endpoint de município só importa de verdade se você estiver checando um município específico onde a empresa emite nota, pra ver se ele publicou alíquota PRÓPRIA diferente da referência (aí é caso a caso — checar o diário oficial daquele município, não a API).

## CBS — Contribuição sobre Bens e Serviços (federal)

| | |
|---|---|
| **O que muda** | Alíquota de referência da União, por ano e por regime (padrão, Simples Nacional, MEI) |
| **Onde** | Mesma API do piloto CBS, endpoint `aliquota-uniao` |
| **Frequência** | Anual, obrigatório a partir de 2034 |
| **Cobertura hoje** | 2026-2033 × 3 regimes |
| **Se atrasar** | `FISCAL_REGIME_SEM_ALIQUOTA_CBS` (400) |

**Como apurar, passo a passo:**
1. Mesma calculadora do IBS: https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/calculadora/aliquotas
2. API direta: `https://piloto-cbs.tributos.gov.br/servico/calculadora-consumo/api/calculadora/dados-abertos/aliquota-uniao?data=AAAA-MM-DD`
3. Resposta `{"aliquotaReferencia": n}` em percentual — é o valor da CBS daquele ano.
4. Anotar na planilha (aba **CBS**): `ano_vigencia` e `aliquota_pct`. O mesmo valor vale para os 3 regimes (`LUCRO_REAL`, `LUCRO_PRESUMIDO`, `SIMPLES_NACIONAL`) — a coluna regime existe porque Simples/MEI têm tratamento próprio no motor, não porque a alíquota da União varie por regime.
5. Mesmo aviso de rate limit do item IBS acima.

## IS — Imposto Seletivo (federal, "imposto do pecado")

| | |
|---|---|
| **O que muda** | Lista de NCM sujeitos ao IS e a alíquota de cada um (cigarros, bebidas açucaradas, veículos poluentes, extração mineral etc.) |
| **Onde** | Regulamentação específica (lei ordinária federal + decreto) — a maior parte ainda **não foi publicada**; acompanhar Receita Federal/Congresso. Depois de publicada, segue o mesmo ritmo de manutenção do NCM (abaixo) para produtos novos/reclassificados |
| **Frequência** | Sem calendário fixo hoje (depende da regulamentação sair); depois, estimar 2-4×/ano junto com NCM |
| **Cobertura hoje** | 1 linha placeholder (`2402.20` cigarro, 150%, estimada) |
| **Se atrasar** | Motor calcula com o placeholder — risco de **subapurar** o tributo real, não erro visível |

**Como apurar, passo a passo:**
1. Não existe portal de consulta pronto (a regulamentação ainda não saiu) — a tarefa aqui é **monitorar publicação**, não ler um valor.
2. Verificar periodicamente:
   - Diário Oficial da União: https://www.in.gov.br/leiturajornal (buscar "Imposto Seletivo")
   - Tramitação de projetos de lei: https://www.congressonacional.leg.br ou https://www.camara.leg.br/propostas-legislativas (buscar "Imposto Seletivo" / "IS reforma tributária")
   - Receita Federal: https://www.gov.br/receitafederal (seção reforma tributária)
3. Quando sair regulamentação por NCM: anotar na planilha (aba **IS**) `ncm`, `descricao`, `aliquota_pct`, `vigente_de` — mesmas colunas da tabela do banco.
4. Enquanto não sair nada, não há o que preencher além de confirmar "sem novidade" na data da checagem (ver observação de frequência abaixo).

---

## Legado em extinção (2026-2033) — não consumido pelo motor hoje, mas a empresa continua obrigada a apurar

O `fiscal-service` só calcula IBS/CBS/IS. Estes seguem sendo devidos pelo regime atual enquanto durar a
transição; `fiscal.transicao_ano` guarda o **percentual remanescente** por ano (100% até 2028, depois
90/80/70/60% em 2029-2032, 0% em 2033) para quem for modelar o módulo legado (spec separado).

### ICMS (estadual)

| | |
|---|---|
| **O que muda** | Alíquotas internas/interestaduais por estado/NCM/CFOP, benefícios fiscais e convênios |
| **Onde** | Legislação estadual + [Convênios CONFAZ](https://www.confaz.fazenda.gov.br) |
| **Frequência** | Alta — convênios saem várias vezes por mês, o ano todo |
| **Se atrasar** | Não quebra o `fiscal-service` (fora de escopo); afeta o módulo legado futuro |

**Como apurar:** portal do CONFAZ → [Legislação → Convênios](https://www.confaz.fazenda.gov.br/legislacao/convenios) (lista por ano); alíquota interna de cada estado fica no site da SEFAZ estadual (ex. SEFAZ-SP, SEFAZ-MG). Baixa prioridade hoje — nenhum sistema consome isso ainda.

### ISS (municipal)

| | |
|---|---|
| **O que muda** | Alíquota ISS por município e item da LC 116/2003 (2% a 5%) |
| **Onde** | Legislação de cada uma das ~5.570 prefeituras |
| **Frequência** | Tipicamente 1×/ano por município, mas varia |
| **Se atrasar** | Não quebra o `fiscal-service` hoje |

**Como apurar:** não há portal único — Diário Oficial de cada município (a maioria tem site próprio, ex. `diariooficial.<cidade>.mg.gov.br`) ou o portal da prefeitura, seção "legislação tributária"/"código tributário municipal". Sem cobertura nacional viável para todos os ~5.570 — priorizar só os municípios onde a empresa/os tenants efetivamente emitem nota.

### PIS/COFINS (federal)

| | |
|---|---|
| **O que muda** | Nada por causa da reforma — só relevante em **2026** (ano-teste), coexistindo com CBS a 0,9%. `fiscal.transicao_ano.pis_cofins_vigente` já marca isso |
| **Onde** | Lei 10.637/2002 e 10.833/2003 (regime não-cumulativo) — legislação estável, sem calendário da reforma |
| **Frequência** | Nenhuma manutenção pela reforma em si; só se houver alteração da legislação PIS/COFINS por conta própria (raro, fora do calendário da LC 214) |

### IPI (federal)

| | |
|---|---|
| **O que muda** | TIPI zera a maioria dos NCM a partir de 2027; **mantém alíquota** só para itens que concorrem com produção da Zona Franca de Manaus (proteção do diferencial competitivo da ZFM) |
| **Onde** | Receita Federal — TIPI (Decreto) |
| **Frequência** | Mesmo ritmo do NCM, 2-4×/ano por NCM |
| **Se atrasar** | Não quebra o `fiscal-service` hoje |

**Como apurar:** [gov.br/receitafederal](https://www.gov.br/receitafederal) → busca "TIPI" (Tabela de Incidência do Imposto sobre Produtos Industrializados) → consultar por NCM. Baixa prioridade hoje — nenhum sistema consome isso ainda.

---

## Cadastros que alimentam o cálculo (não são "imposto", mas envelhecem junto)

| Cadastro | Muda por | Frequência | Onde apurar |
|---|---|---|---|
| Tabela NCM | Alteração/renumeração de código | 2-4×/ano | [gov.br/siscomex](https://www.gov.br/siscomex) → Tabelas → NCM |
| CFOP | Ajuste SINIEF | Sem calendário fixo | [CONFAZ → Ajustes SINIEF](https://www.confaz.fazenda.gov.br/legislacao/ajustes) |
| `servico_nbs` / `servico_cclasstrib` (Anexo VIII, LC 116) | Nova lei/regulamentação | Sem calendário fixo | [planalto.gov.br](https://www.planalto.gov.br) — texto da LC 214/2025 e alterações |
| Códigos de município IBGE | Emancipação/fusão de município | Raro | [IBGE → Organização do Território](https://www.ibge.gov.br/geociencias/organizacao-do-territorio/estrutura-territorial.html) |

---

## Planilha de apuração (Excel)

Uma aba por imposto/cadastro, colunas iguais às da tabela do banco — quem preenche não precisa saber
SQL, e quem carrega monta o `INSERT`/changeset direto da linha da planilha.

| Aba | Colunas | Alimenta |
|---|---|---|
| **IBS** | `ano_vigencia`, `uf`, `codigo_ibge_municipio`, `nome_municipio`, `aliquota_estadual`, `aliquota_municipal`, `fonte_url`, `data_consulta`, `responsavel` | `fiscal.aliq_ibs_municipio` |
| **CBS** | `ano_vigencia`, `aliquota_pct`, `fonte_url`, `data_consulta`, `responsavel` | `fiscal.aliq_cbs_regime` (mesmo valor nos 3 regimes) |
| **IS** | `ncm`, `descricao`, `aliquota_pct`, `vigente_de`, `fonte_url`, `data_consulta`, `responsavel` | `fiscal.aliq_is_ncm` |
| **cClassTrib (redução serviço)** | `cclasstrib`, `regime`, `percentual_reducao`, `vigente_de`, `vigente_ate`, `fonte_url`, `data_consulta` | `fiscal.regime_cclasstrib` |
| **ICMS** (legado) | `uf`, `ncm_ou_cfop`, `aliquota`, `convenio`, `vigente_de`, `fonte_url`, `data_consulta` | ainda sem tabela — arquivo fica pronto para quando o módulo legado existir |
| **ISS** (legado) | `codigo_ibge_municipio`, `item_lc116`, `aliquota`, `vigente_de`, `fonte_url`, `data_consulta` | idem |
| **IPI** (legado) | `ncm`, `aliquota`, `so_zfm` (sim/não), `vigente_de`, `fonte_url`, `data_consulta` | idem |

`fonte_url` e `data_consulta` em toda aba não são burocracia: é o que permite auditar depois "de onde
veio esse número" sem repetir a apuração.

---

## Observação operacional

Quem faz essa manutenção **não edita** um changeset já aplicado (quebra checksum do Liquibase) — toda
correção ou ano novo entra como changeset seguinte (`fiscal-0NN`), nunca alterando o anterior. Ver
`spec/Fin.md` §1.12 para o detalhe técnico por tabela e quem opera isso (Painel de Administração Interna,
não o tenant).