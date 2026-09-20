# Anexos da LC 214/2025 — itens NÃO carregados em `fiscal.regime_dif_ncm`

Gerado originalmente por `extrai-anexos-v2.ps1` a partir de `spec/fiscal/ANEXOS.md`. O script foi
efêmero e nunca chegou a ser versionado no repositório — a partir daqui este documento é mantido
À MÃO. Não regerar por script: ele descartava a coluna NBS dos anexos (que a fonte tem), e a
regeração apagaria as seções mantidas manualmente ("✅ Resolvido" e as ressalvas de `cClassTrib`). Cada linha
aqui é um item cuja regra **não é decidível só pelo código** — carregá-la automaticamente erraria
imposto em silêncio. Resolver um item = decidir o(s) código(s) e acrescentar a linha num changeset
novo.

Total pendente: **40** itens — soma real das seções abaixo (240 originais − 44 resolvidos em 01 de
setembro de 2026 − 143 serviços dos Anexos II/III/X/XI − 20 itens de produto resolvidos em 20 de
setembro de 2026; a diferença para 240 são linhas que o extrator duplicou entre seções).
Carregados: **247** códigos na carga automática + os changesets manuais `fiscal-037` a `fiscal-051`.

## ✅ Resolvido em 01 de setembro de 2026 (changesets `fiscal-037` a `fiscal-048`)

> Tentativa de fechar o máximo do backlog de 240 pendências que dava pra decidir com segurança só
> lendo o texto integral de `spec/ANEXOS.md` — sem adivinhar nada que dependesse de origem/uso do
> produto ou de fonte que eu não tivesse 100% de confiança na transcrição. Detalhe item a item (por
> que cada código entrou, e sob qual regime) está nos comentários do próprio
> `liquibase-service/.../fiscal/fiscal-schema-014.yaml` — esta seção só resume o resultado.

**44 itens saíram do backlog:**
- **31 por carga nova** (regime `EXCECAO_INTEGRAL` criado para as exceções "exceto X" sem outro
  anexo aplicável — não reusa `PADRAO` de propósito, ver comentário do próprio changeset):
  Anexo I itens 19/20 (carnes/peixes); Anexo IV itens 48/49/51/54/61; Anexo V (relógio braille,
  calculadora falante, despertador vibratório); Anexo VI itens 26/27/29; Anexo VII itens 1
  (crustáceos/moluscos), 4/5/6 (farinhas/grumos/grãos), 10/11 (sucos/polpas), 14/15 (capítulos
  7/8/10/12 — **exceto** a distinção "frutas de casca rija não regionais", que é por origem, não
  por NCM, e continua pendente); Anexo IX itens 2/5/6/11/14/15; Anexo XII itens 5/7/11; Anexo XIII
  item 4; Anexo XV itens 2/4.
- **8 por correção de falso positivo** (o extrator listou como pendente algo que já estava
  carregado ou já resolvido junto com outro item, sem precisar de changeset à parte): Anexo VI
  item 81 (triglicerídeo de cadeia média — já cai na posição `1513` do Anexo VII, mesmos 60%);
  Anexo XII item 1 e a exceção de eletrodiagnóstico (os códigos "exceto" já eram os próprios itens
  1.1/1.2, já carregados); Anexo XIII item 2 (cadeira de rodas — códigos já carregados); Anexo XII
  códigos `9022.14`/`9022.19` (eram continuação do item 7, já entraram junto com ele no
  `fiscal-045`).
- **5 por releitura limpa do Anexo XI** (`fiscal-048`, feita em 01/09/2026 direto em
  `spec/ANEXOS.md` linha 475 — a leva anterior tinha ficado de fora por baixa confiança de
  transcrição): item 2.2 (carro de combate, `8710.00.00`), 2.7 (lançador de foguete/arma de
  guerra, `9301.20.00`), 2.17 (catapulta/gancho de aterrissagem em porta-aviões, `8805.10.00`),
  2.18 (simulador de combate aéreo, `8805.21.00`), 2.21 (navio de guerra, `8906.10.00`) — únicos
  5 dos 30 subitens cujo heading do NCM é exclusivamente militar, sem equivalente civil no mesmo
  código.

**Ficou de fora de propósito** (não é lacuna esquecida — é risco/indecidibilidade documentada no
rodapé do `fiscal-schema-014.yaml`):
- Anexo XI, os outros 25 subitens do item 2 (bens de defesa/segurança nacional) — releitura já
  feita (ver bullet acima), mas o restante não é decidível só por NCM: ou o heading é largo e tem
  uso civil real no mesmo código (8709 viaturas/tratores industriais, 8701 tratores agrícolas,
  8802/8806 aeronaves e drones civis, 8804 paraquedas esportivos, 9014.20 navegação aérea civil,
  9306 mistura munição militar com munição de caça/esporte), ou a distinção do bem civil é só por
  FINALIDADE ("para uso pela segurança nacional"), que não tem campo no cadastro de produto —
  mesma limitação do item 6 abaixo. Vale também pros subitens 2.22-2.30 (dispositivos de segurança
  da informação/cibernética — IPS, firewall, storage criptografado): os NCM citados são
  classificações genéricas de hardware de rede/TI (switch, servidor, SSD comuns), não dá pra
  isolar só o "de segurança" pelo código. **Item 2.5 (trator, NCM `8701`) checado via web em
  01/09/2026 — confirmado, não é falta de leitura**: o texto oficial da lei cita só o heading
  genérico "8701" e a NCM não tem subposição de trator militar (8701.10/20/30/9x classificam por
  tipo mecânico e potência do motor, nunca por uso); carregar traria 60% pra qualquer trator
  agrícola/florestal/de obra do país. Gap estrutural — não adianta reler o texto de novo.
- Anexo IX item 3 (capítulo 25 inteiro — mistura corretivo de solo agrícola com cimento/cal
  industrial, largo demais).
- Anexo IX itens 7/8 (listas grandes com sobreposição parcial já coberta por outro anexo,
  precisa curadoria código a código).
- Anexo IX itens 12/13/18/19/20/21 (distinção "exceto animais domésticos"/"exceto ornamentais" —
  por USO, não por código NCM).

## Pendências de `cClassTrib` (serviço) — seção mantida À MÃO, não sai do script

> Atualizada em 27 de agosto de 2026, junto com os changesets `fiscal-034`/`035`/`036` (item 7.7).

`fiscal.regime_cclasstrib` tem os **27 dos 27** `cClassTrib` do Anexo VIII: 7 do `fiscal-019`
(fundamentados pelo próprio nome do anexo) + 18 do `fiscal-021` (cada linha com o artigo da LC 214
no comentário) + os **2 últimos**, abaixo, que o `fiscal-021` deixou de fora de propósito porque um
único `percentual_reducao` (vale igual para IBS e CBS) não conseguia expressá-los:

| `cClassTrib` | setor | por que não cabia num `percentual_reducao` só | como foi resolvido |
|---|---|---|---|
| `010002` | operações do serviço financeiro | art. 233 não dá redução: fixa a **soma** de IBS + CBS em valor absoluto por ano (10,85% em 2027-2028, 11,00% em 2029, 11,15% em 2030, 11,30% em 2031, 11,50% em 2032, 12,50% em 2033). Não é percentual sobre a alíquota de referência, é outra alíquota. | ✅ **Resolvido**: nova tabela `fiscal.aliquota_regime_tributo` (regime `SERVICO_FINANCEIRO`, tributo `TOTAL`, tipo `ALIQUOTA_ABSOLUTA`, uma linha por ano — curva completa conferida via WebSearch cruzado em 3 fontes) — `fiscal-034`/`fiscal-036`. `regime_cclasstrib` ganhou a linha do cClassTrib com `percentual_reducao = 0` (placeholder; quem decide o valor real é o override) — `fiscal-035`. **Não inclui** o redutor adicional do §10 do art. 233 (serviço financeiro que também sofre ISS, ex. corretagem de seguros) — regra à parte, ainda pendente. |
| `200025` | Prouni | art. 308 reduz a zero **apenas a CBS**; o IBS segue cheio. Um percentual só, aplicado aos dois tributos, erra um dos lados. | ✅ **Resolvido**: mesma tabela `fiscal.aliquota_regime_tributo` (regime `PROUNI`, tributo `CBS`, tipo `PERCENTUAL_REDUCAO`, 100%, sem `ano_vigencia` — vale pra sempre) — `fiscal-034`/`fiscal-036`. Ausência de linha de `IBS` é intencional: sai cheio por padrão. |

Os dois passam a sair com a alíquota correta (`MotorFiscalService.fatoresEfetivos`) — antes caíam em
`RegimeDiferenciado.PADRAO` (tributação cheia), erro que era **contra o contribuinte**. Tabela nova
e separada de propósito: dá pra montar um CRUD (regime/tributo/tipo/valor/ano) num backend futuro
sem tocar nas tabelas de classificação nem no código do motor. **Não testado ainda** (build/testes
não rodados nesta sessão).

Dois códigos entraram no `fiscal-021`, mas **com ressalva** — a alíquota está certa, o resto do
regime não:

| `cClassTrib` | carregado | ressalva |
|---|---|---|
| `200045` | 60% (art. 158, caput) | o parágrafo único sobe para **80%** na locação de imóveis do art. 162, VI. O `cClassTrib` sozinho não distingue os dois casos; hoje sai 60% sempre, o que **tributa a mais** na locação. |
| `000002` | 0% (integral) | art. 11, VIII manda ratear a operação entre Municípios/Estados **proporcionalmente à extensão da via explorada**. O motor calcula para um único município (`ibgeLocalPrestacao`), sem rateio. |

Fora disso, os regimes específicos carregados (hotelaria, agências de turismo, planos de
assistência, bens imóveis) têm regra de **base de cálculo** própria na LC 214 — dedução de
repasses, provisões técnicas, sinistros. O `fiscal-021` carrega só a alíquota; a base continua
chegando pronta em `valorOperacao`, por conta de quem chama o motor.

## ✅ Resolvido em 20 de setembro de 2026 — serviços dos Anexos II/III/X/XI (143 itens)

> Esta seção listava 143 itens de serviço como pendentes de um de/para NBS <-> LC 116. A pendência
> não existe mais: ela descrevia um desenho do motor que já foi substituído.
>
> - **A premissa caiu no `fiscal-020`.** Serviço era gravado em `regime_dif_ncm.nbs` e casado por
>   prefixo contra o código LC 116 da requisição; esse changeset dropou a coluna. Hoje o regime do
>   serviço vem do **cClassTrib declarado no documento** (`TabelaFiscal.regimeCClassTrib`), igual à
>   NF-e — não é deduzido do cadastro, nem por NBS nem por LC 116.
> - **Os quatro anexos já saem com a redução correta** por `fiscal.regime_cclasstrib` (`fiscal-019`):
>   `200028` → `ANEXO_II_60` (educação), `200029` → `ANEXO_III_60` (saúde), `200039` → `ANEXO_X_60`
>   (cultura), `200043`/`200044` → `ANEXO_XI_60` (segurança nacional). A redução é por anexo, não
>   item a item — os 143 itens não precisam virar linha nenhuma.
> - **O de/para pedido existe**: `fiscal.servico_nbs` (`fiscal-017`, `data/servico-nbs.csv`, 895
>   linhas item LC 116 ↔ NBS do Anexo VIII) e `fiscal.servico_cclasstrib`, com validação em
>   `TabelaFiscal.cClassTribAdmitido(itemLc116, cClassTrib)`.
> - **O NBS nunca faltou na origem**: `spec/fiscal/ANEXOS.md` traz o código na 3ª coluna (linha 42,
>   Ensino Infantil `1.2201.1`; linha 58, Serviços cirúrgicos `1.2301.11.00`; linha 417,
>   Licenciamento de direitos de autor `1.1103`). A tabela desta seção saiu sem ele porque o
>   `extrai-anexos-v2.ps1` só capturou as colunas Anexo/Item/Descrição.
>
> **Risco residual** (mesma classe da ressalva do `200045` acima): item cujo percentual difira do
> cabeçalho do seu anexo sai com o percentual do cabeçalho — o cClassTrib declarado é o único
> discriminador que o motor tem.

## Sem referência a código NCM na descrição e sem coluna NCM/SH — 4 itens

> **20 itens saíram daqui em 20 de setembro de 2026.** A coluna NCM/SH desses anexos foi recuperada
> do texto oficial (o extrator a perdia nas células multi-linha) e cruzada contra
> `data/regime-lc214-v2.csv`. Quase tudo já estava carregado:
>
> - **Falso positivo do extrator** — a linha era o **cabeçalho de grupo** dos subitens, não item
>   próprio, e os subitens já estão na carga: Anexo V item 1 (`87089910`, `87082999`, `84289090`,
>   `84253110`), item 2 (`66020000`, `90251990`, `85437099`, `90172000`, `84716090`, `84729099`,
>   `84433222`, `84718000` + relógio/calculadora do `fiscal-039`) e item 3 (`85171`, `84716053` +
>   despertador do `fiscal-039`) — todos `ANEXO_V_60`; Anexo XII item 1 (`90181100`, `90181980`,
>   `ANEXO_XII_ZERO`); Anexo XIII item 2 (`87131000`, `87139000`, `ANEXO_XIII_ZERO`).
> - **Carregados agora** (`fiscal-schema-016.yaml`): Anexo VI item 67 — metionina, `2930.40.10` e
>   `2930.40.90` (`fiscal-050`); Anexo IX item 35 — vinhaça, `2303.20.00` e `2303.30.00`
>   (`fiscal-051`).
> - **São serviço, não produto**: Anexo IX itens 22 a 34 (e a linha solta que era só a célula NBS
>   do item 24). A coluna traz **NBS** (`1.1410.90.00`, `1.1405.2x`, `1.1403.x`, `1.1901.10.00`,
>   `1.110x`), não NCM — e regime de serviço vem do `cClassTrib` declarado, não de
>   `regime_dif_ncm` (ver seção de 20/09 acima). Nada a carregar.
> - Já haviam saído em 01/09/2026: V (relógio braille, calculadora falante, despertador
>   vibratório), VI itens 26/27/29/81, IX itens 2/5/6/11/14/15.
>
> Os 4 que sobram **já têm o código** — o bloqueio é outro, na última coluna.

| Anexo | Item | Descrição | Por que continua pendente |
|---|---|---|---|
| IX | 3 | Corretivos de solo (inclusive condicionadores), remineralizadores e substratos para plantas; em conformidade com as definições e demais requisitos da legislação específica | Capítulo 25 inteiro — mistura corretivo agrícola com cimento/cal industrial. Largo demais |
| IX | 7 | Calcário, casca de coco, turfa, tortas, bagaços, resíduos vegetais/de madeira/de celulose, ossos, cinzas, DL-metionina, vermiculita e afins — 29 códigos: `05.06`, `1201.10.00`, `1213.00.00`, `1301.90.90`, `1302.19.9`, `1401.90.00`, `1404.90.90`, `2102.20.00`, `23.02`, `23.03`, `2304.00`, `2305.00.00`, `23.06`, `2308.00.00`, `2703.00.00`, `2839.90.10`, `2839.90.50`, `2922.4`, `2930.40`, `33.01`, `3802.90.40`, `3804.00`, `3824.99.71`, `4401.39.00`, `4401.4`, `4402.90.00`, `4701.00.00`, `5305.00.90`, `6806.20.00` | **Destinação, não código**: só vale "destinados diretamente à fabricação de biofertilizantes/corretivos". Os mesmos `23.02`–`23.08` são os farelos do item 20 (pendente por uso). Carregar daria 60% a qualquer farelo/resíduo do país — erro **a favor** do contribuinte, que vira autuação no cliente |
| IX | 8 | Ácido nítrico, sulfúrico, fosfórico, clorídrico, fosforoso e acético, fosfatos de cálcio naturais, enxofre, hidróxido de sódio e carbonato dissódico — `2503.00.10/90`, `2510.10.10/90`, `2510.20.10/90`, `2802.00.00`, `2806.10.20`, `2807.00.10`, `2808.00.10`, `2809.20.11/19`, `2811.19.20`, `2815.11.00`, `2815.12.00`, `2836.20.10/90`, `2915.21.00` | Mesma trava do item 7 ("todos destinados diretamente à fabricação de fertilizantes") — são químicos de uso industrial amplo. Dos 18 códigos só `2915.21.00` já está carregado, via Anexo VI, com os mesmos 60% |
| IX | 10 | Semente genética, básica, nativa in natura, certificada (C1/C2), não certificada (S1/S2) e de cultivar local, tradicional ou crioula | Sem coluna NCM na fonte, e a distinção é por **categoria de registro da semente**, não por código |

## Redação exclui parte dos códigos citados (exceto/ressalvado) — decidir a lista à mão — 6 itens

> Resolvidos em 01/09/2026 (ver seção "✅ Resolvido" acima) e removidos desta lista: I itens 19/20;
> IV itens 48/49/51/54/61; VII itens 1/4/5/6/14/15 (a exceção "casca rija não regional" do item 14
> continua indecidível por NCM, mas os códigos-base já entraram); XII (eletrodiagnóstico, já
> carregado) e itens 5/7/11; XIII item 4; XV item 2. Os 6 que sobram (IX 12/13/18/19/20/21) são
> todos distinção por USO ("exceto animais domésticos"/"exceto ornamentais"), não por NCM — sem
> campo de uso final no cadastro de produto não dá pra resolver sem arriscar mistributar.

| Anexo | Item | Descrição |
|---|---|---|
| IX | 12 | Vacinas, soros e medicamentos, de uso veterinário, exceto de animais domésticos |
| IX | 13 | Aves de um dia, exceto as ornamentais |
| IX | 18 | Rações para animais, concentrados, suplementos, aditivos, premix ou núcleo, exceto para animais domésticos |
| IX | 19 | Sementes e cereais, mesmo triturados, em grãos esmagados ou trabalhados de outro modo; todos destinados diretamente à fabricação de ração para animais ou diretamente à alimentação animal, exceto de animais domésticos |
| IX | 20 | Farelos e tortas de produtos vegetais e demais resíduos e desperdícios das indústrias alimentares; todos destinados diretamente à fabricação de ração para animais ou diretamente à alimentação animal, exceto de animais domésticos |
| IX | 21 | Alho em pó, sal mineralizado, farinhas de peixe, de ostra, de carne, de osso, de pena, de sangue e de víscera, calcário calcítico, gorduras e óleos animais, resíduos de óleo e de gordura de origem animal ou vegetal descartados por empresas do ramo alimentício, e DL-Metionina e seus análogos; todo... |

## CARREGADO, mas com condição não verificável pelo ERP (registro/destinação) — 11 itens

| Anexo | Item | Descrição |
|---|---|---|
| I | 2 | Leite, em conformidade com os requisitos da legislação específica relativos ao consumo direto pela população, classificado nos códigos 0401.10.10, 0401.10.90, 0401.20.10, 0401.20.90, 0401.40.10 e 0401.50.10 da NCM/SH |
| I | 3 | Leite em pó, em conformidade com os requisitos da legislação específica, classificado nos códigos 0402.10.10, 0402.10.90, 0402.21.10, 0402.21.20, 0402.29.10 e 0402.29.20 da NCM/SH |
| I | 4 | Fórmulas infantis, em conformidade com os requisitos da legislação específica, classificadas nos códigos 1901.10.10, 1901.10.90 e 2106.90.90 da NCM/SH |
| I | 9 | Óleo de babaçu do código 1513.21.20 da NCM/SH, em conformidade com os requisitos da legislação específica relativos ao consumo como alimento |
| I | 16 | Pão comumente denominado pão francês, de formato cilíndrico e alongado, com miolo branco creme e macio, e casca dourada e crocante, elaborado a partir da mistura ou pré-mistura de farinha de trigo, fermento biológico, água, sal, açúcar, aditivos alimentares e produtos de fortificação de farinhas,... |
| I | 22 | Sal em conformidade com os requisitos da legislação específica relativos ao teor de iodo enquadrado nos limites próprios para consumo humano classificado nos códigos 2501.00.20 e 2501.00.90 da NCM/SH |
| VII | 2 | Leite fermentado, bebidas e compostos lácteos, em conformidade com os requisitos da legislação específica, classificados nos códigos 0403.20.00, 0403.90.00 e 2202.99.00 da NCM/SH |
| VII | 8 | Óleos de soja, de milho, canola e demais óleos vegetais, em conformidade com os requisitos da legislação específica relativos ao consumo como alimento, classificados na subposição 1507.90 e nas posições 15.08, 15.11, 15.12, 15.13, 15.14 e 15.15 da NCM/SH |
| IX | 1 | Biofertilizantes, em conformidade com as definições e demais requisitos da legislação específica |
| IX | 4 | Inoculantes, meios de cultura e outros microorganismos para uso agrícola; em conformidade com as definições e demais requisitos da legislação específica |
| XII | 12 | Densímetros, areômetros, pesa-líquidos e instrumentos flutuantes semelhantes, termômetros, pirômetros, barômetros, higrômetros e psicômetros, registradores ou não, mesmo combinados entre si |

## Anexo XVII (Imposto Seletivo) — outra tabela, alíquota pendente de lei ordinária — 9 itens

| Anexo | Item | Descrição |
|---|---|---|
| XVII |  | Veículos |
| XVII |  | 87.03; 8704.21 (exceto os caminhões); 8704.31 (exceto os caminhões); 8704.41.00 (exceto os caminhões); 8704.51.00 (exceto os caminhões); 8704.60.00 (exceto os caminhões); 8704.90.00 (exceto os caminhões); ressalvados os veículos com características técnicas específicas para uso operacional das Fo... |
| XVII |  | Aeronaves e Embarcações |
| XVII |  | 8802, exceto o código 8802.60.00; e embarcações com motor classificadas na posição 8903; ressalvadas as aeronaves e embarcações com características técnicas específicas para uso operacional das Forças Armadas ou dos órgãos de Segurança Pública |
| XVII |  | Produtos fumígenos |
| XVII |  | Bebidas alcóolicas |
| XVII |  | Bebidas açucaradas |
| XVII |  | Bens minerais |
| XVII |  | Concursos de prognósticos e _Fantasy_ _sport_ |

## NCM em mais de um anexo com reduções diferentes: ANEXO_IV_60=60%, ANEXO_XII_ZERO=100% — 4 itens

| Anexo | Item | Descrição |
|---|---|---|
| IV/XII | 90189099 | Sistema para drenagem com conjunto intermediário para medição contínua da diurese \|\|\| Oxigenador de bolha com tubos para circulação extracorpórea \|\|\| Oxigenador de membrana com tubos para circulação extracorpórea \|\|\| Reservatório de cardiotomia \|\|\| Reservatório para cardioplegia com t... |
| IV/XII | 90189010 | Conjunto para autotransfusão \|\|\| Bomba de infusão |
| IV/XII | 90211010 | Implantes osseointegráveis, na forma de parafuso, e seus componentes manufaturados, tais como tampas de proteção, montadores, conjuntos, pilares (cicatrizador, conector, de transferência ou temporário), cilindros, seus acessórios, destinados a sustentar, amparar, acoplar ou fixar próteses dentári... |
| IV/XII | 90211020 | Implantes osseointegráveis, na forma de parafuso, e seus componentes manufaturados, tais como tampas de proteção, montadores, conjuntos, pilares (cicatrizador, conector, de transferência ou temporário), cilindros, seus acessórios, destinados a sustentar, amparar, acoplar ou fixar próteses dentári... |

## Nenhum código NCM identificável na descrição — 1 item

> Resolvidos em 01/09/2026: VII itens 10/11 (sucos/polpas) e XV item 4 (capítulo 6, floricultura).
> A linha `IX |  | NBS / NCM/SH` que sobra é artefato do extrator (capturou o cabeçalho da coluna
> como se fosse linha de dado) — não é item real, não precisa de changeset.

| Anexo | Item | Descrição |
|---|---|---|
| IX |  | NBS / NCM/SH |

## Continuação de item pendente — 2 itens

> Resolvidos em 01/09/2026: `9022.14` e `9022.19` já entraram no fiscal-045 junto com o item 7 do
> Anexo XII (raio X móvel) — eram continuação daquele item, não algo separado.

| Anexo | Item | Descrição |
|---|---|---|
| XVII | 2202.10.00 | (código de continuação do item acima, que não entrou na carga) |
| XVII | 2709.00.10 2711.11.00 2711.21.00 | (código de continuação do item acima, que não entrou na carga) |

## NCM em mais de um anexo com reduções diferentes: ANEXO_I_ZERO=100%, ANEXO_VI_60=60% — 2 itens

| Anexo | Item | Descrição |
|---|---|---|
| I/VI | 21069090 | Fórmulas infantis, em conformidade com os requisitos da legislação específica, classificadas nos códigos 1901.10.10, 1901.10.90 e 2106.90.90 da NCM/SH \|\|\| Fórmula para dieta isenta de fenilalanina \|\|\| Fórmula para dieta isenta demetionina \|\|\| Fórmula para dieta isenta de lisina e pobre d... |
| I/VI | 25010090 | Sal em conformidade com os requisitos da legislação específica relativos ao teor de iodo enquadrado nos limites próprios para consumo humano classificado nos códigos 2501.00.20 e 2501.00.90 da NCM/SH \|\|\| Cloreto de sódio |

## NCM em mais de um anexo com reduções diferentes: ANEXO_IV_60=60%, ANEXO_XIII_ZERO=100% — 1 itens

| Anexo | Item | Descrição |
|---|---|---|
| IV/XIII | 90219019 | Conjunto para hidrocefalia de baixo perfil \|\|\| Conjunto para hidrocefalia **standard** \|\|\| Espaçador de tendão \|\|\| _Shunt_ lombo-peritonal \|\|\| Válvula para hidrocefalia \|\|\| Válvula para tratamento de ascite \|\|\| Implantes cocleares |

