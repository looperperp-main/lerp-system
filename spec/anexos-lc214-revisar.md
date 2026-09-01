# Anexos da LC 214/2025 — itens NÃO carregados em `fiscal.regime_dif_ncm`

Gerado por `extrai-anexos-v2.ps1` a partir de `spec/ANEXOS.md`. Cada linha aqui é um item cuja
regra **não é decidível só pelo código** — carregá-la automaticamente erraria imposto em
silêncio. Resolver um item = decidir o(s) código(s) e acrescentar a linha num changeset novo.

Total pendente: **196** itens (240 originais − 44 resolvidos/corrigidos em 01 de setembro de 2026,
ver seção abaixo). Carregados automaticamente: **247** códigos.

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

## Serviço com código NBS; o motor casa por código LC 116 — falta o de/para NBS<->LC116 — 143 itens

| Anexo | Item | Descrição |
|---|---|---|
| II | 1 | Ensino Infantil, inclusive creche e pré-escola |
| II | 2 | Ensino Fundamental |
| II | 3 | Ensino Médio |
| II | 4 | Ensino Técnico de Nível Médio |
| II | 5 | Ensino para jovens e adultos destinado àqueles que não tiveram acesso ou continuidade de estudos no ensino fundamental e médio na idade própria |
| II | 6 | Ensino Superior, compreendidos os cursos e programas de graduação, pós-graduação, de extensão e cursos sequenciais |
| II | 7 | Ensino de sistemas linguísticos de natureza visomotora e de escrita tátil |
| II | 8 | Ensino de línguas nativas de povos originários |
| II | 9 | Educação especial destinada a pessoas com deficiência, transtornos globais do desenvolvimento e altas habilidades ou superdotação, de modo isolado ou agregado a qualquer das etapas de educação tratadas neste Anexo |
| III | 1 | Serviços cirúrgicos |
| III | 2 | Serviços ginecológicos e obstétricos |
| III | 3 | Serviços psiquiátricos |
| III | 4 | Serviços prestados em Unidades de Terapia Intensiva |
| III | 5 | Serviços de atendimento de urgência |
| III | 6 | Serviços hospitalares não classificados em subposições anteriores |
| III | 7 | Serviços de clínica médica |
| III | 8 | Serviços médicos especializados |
| III | 9 | Serviços odontológicos |
| III | 10 | Serviços de enfermagem |
| III | 11 | Serviços de fisioterapia |
| III | 12 | Serviços laboratoriais |
| III | 13 | Serviços de diagnóstico por imagem |
| III | 14 | Serviços de bancos de material biológico humano |
| III | 15 | Serviços de ambulância |
| III | 16 | Serviços de assistência ao parto e pós-parto |
| III | 17 | Serviços de psicologia |
| III | 18 | Serviços de vigilância sanitária |
| III | 19 | Serviços de epidemiologia |
| III | 20 | Serviços de vacinação |
| III | 21 | Serviços de fonoaudiologia |
| III | 22 | Serviços de nutrição |
| III | 23 | Serviços de optometria |
| III | 24 | Serviços de instrumentação cirúrgica |
| III | 25 | Serviços de biomedicina |
| III | 26 | Serviços farmacêuticos |
| III | 27 | Serviços de cuidado e assistência a idosos e pessoas com deficiência em unidades de acolhimento |
| III | 28 | Serviços domiciliares de apoio a pessoas adultas, idosas, crianças, adolescentes, pessoas com transtornos mentais e com deficiências |
| III | 29 | Serviços de esterilização |
| III | 30 | Serviços funerários, de cremação e de embalsamamento |
| X | 1 | Licenciamento de direitos de autor e de direitos conexos |
| X | 2 | Licenciamento de direitos de obras literárias |
| X | 3 | Licenciamento de direitos de autor de obras cinematográficas |
| X | 4 | Licenciamento de direitos de autor de obras jornalísticas |
| X | 5 | Licenciamento de direitos conexos de artistas intérpretes ou executantes em obras audiovisuais |
| X | 6 | Licenciamento de direitos conexos de produtores de obras audiovisuais |
| X | 7 | Licenciamento de direitos de obras audiovisuais destinadas à televisão |
| X | 8 | Licenciamento de direitos de obras musicais e fonogramas |
| X | 9 | Cessão temporária de direitos de obras literárias |
| X | 10 | Cessão temporária de direitos de autor de obras cinematográficas |
| X | 11 | Cessão temporária de direitos de autor de obras jornalísticas |
| X | 12 | Cessão temporária de direitos conexos de artistas intérpretes ou executantes em obras audiovisuais |
| X | 13 | Cessão temporária de direitos conexos de produtores de obras audiovisuais |
| X | 14 | Cessão temporária de direitos de obras audiovisuais destinadas à televisão |
| X | 15 | Cessão temporária de direitos de obras musicais e fonogramas |
| X | 16 | Cessão definitiva de direitos de obras literárias |
| X | 17 | Cessão definitiva de direitos de obras cinematográficas |
| X | 18 | Cessão definitiva de direitos de obras jornalísticas |
| X | 19 | Cessão definitiva de direitos de obras musicais e fonogramas |
| X | 20 | Serviços de agências de notícias para jornais e periódicos |
| X | 21 | [Serviços de agências de notícias para mídia audiovisual](http://nbs.economia.gov.br/pt/concepts/servicos-de-agencias-de-noticias-para-midia-audiovisual/glance.html) |
| X | 22 | Serviços de assistência e organização de convenções, feiras de negócios, exposições e outros eventos |
| X | 23 | Serviços de gravação de som em estúdio destinados diretamente às produções nacionais artísticas, culturais e audiovisuais |
| X | 24 | Serviços de gravação de som ao vivo destinados diretamente às produções nacionais artísticas, culturais e audiovisuais |
| X | 25 | Serviços de produção de programas de televisão, videoteipes e filmes |
| X | 26 | Serviços de produção de programas de rádio |
| X | 27 | Serviços de edição de obras audiovisuais destinados diretamente às produções nacionais artísticas, culturais e audiovisuais |
| X | 28 | Serviços de duplicação e transferência de obras audiovisuais destinados diretamente às produções nacionais artísticas, culturais e audiovisuais |
| X | 29 | Serviços de correção de cor e restauração digital de obras audiovisuais destinados diretamente às produções nacionais artísticas, culturais e audiovisuais |
| X | 30 | Serviços de efeitos visuais em obras audiovisuais destinados diretamente às produções nacionais artísticas, culturais e audiovisuais |
| X | 31 | Serviços de animação destinados diretamente às produções nacionais artísticas, culturais e audiovisuais |
| X | 32 | Serviços de legendas, títulos e dublagem em obras audiovisuais destinados diretamente às produções nacionais artísticas, culturais e audiovisuais |
| X | 33 | Serviços de projeto e edição de som em obras audiovisuais destinados diretamente às produções nacionais artísticas, culturais e audiovisuais |
| X | 34 | Serviços de projeção de filmes |
| X | 35 | Serviços de produção audiovisual, de apoio e relacionados não classificados em subposições anteriores |
| X | 36 | [Serviços de organização e promoção de atuações artísticas ao vivo](http://nbs.economia.gov.br/pt/concepts/servicos-de-organizacao-e-promocao-de-atuacoes-artisticas-ao-vivo/glance.html) |
| X | 37 | [Serviços de produção e apresentação de atuações artísticas ao vivo,](http://nbs.economia.gov.br/pt/concepts/servicos-de-producao-e-apresentacao-de-atuacoes-artisticas-ao-vivo/glance.html) inclusive os ingressos relativos a estes serviços |
| X | 38 | Serviços de atuação artística |
| X | 39 | Serviços de autores, compositores, escultores, pintores e outros artistas, exceto os de atuação artística |
| X | 40 | Serviços de museus, inclusive serviços relativos a mostras e coleções de arte |
| X | 41 | Serviços de reservas de ingressos para eventos de produções nacionais artísticas, culturais e audiovisuais |
| X | 42 | Fotografias artísticas originais |
| X | 43 | Quadros, pinturas e desenhos, artísticos originais |
| X | 44 | Gravuras, estampas e litografias, artísticas originais |
| X | 45 | Produções originais de arte estatutária ou de escultura |
| X | 46 | Licenciamento de direitos conexos de artistas intérpretes ou executantes |
| X | 47 | Cessão temporária de direitos de autor e de direitos conexos |
| X | 48 | Cessão temporária de direitos conexos de artistas intérpretes ou executantes |
| X | 49 | Licenciamento de direitos de autor de obras teatrais |
| X | 50 | Licenciamento de direitos conexos de produtores de obras teatrais |
| X | 51 | Licenciamento de direitos conexos de artistas intérpretes ou executantes em obras teatrais |
| X | 52 | Cessão temporária de direitos de autor de obras teatrais |
| X | 53 | Cessão temporária de direitos conexos de artistas intérpretes ou executantes em obras teatrais |
| X | 54 | Cessão temporária de direitos conexos de produtores intérpretes ou executantes em obras teatrais |
| X | 55 | Serviços de sonorização, iluminação, figurino, videografia e cenografia para atuações artísticas ao vivo, destinados às produções de que trata o art. 139 desta Lei Complementar |
| X | 56 | Serviços de locação, montagem e desmontagem de palcos, destinados às produções de que trata o art. 139 desta Lei Complementar |
| X | 57 | Serviços de apresentação e promoção de atuações artísticas, inclusive gestão de espaços destinados a apresentações de exposições de artes cênicas, espetáculos e demais produções de que trata o art. 139 desta Lei Complementar |
| XI |  | NBS / NCM/SH |
| XI | 1 | SERVIÇOS RELACIONADOS À SOBERANIA E À SEGURANÇA NACIONAL, À SEGURANÇA DA INFORMAÇÃO E À SEGURANÇA CIBERNÉTICA |
| XI |  | Segurança em Tecnologia da Informação (TI) |
| XI |  | Serviços de projeto e desenvolvimento de aplicativos e programas em Tecnologia da Informação (TI) não classificados em subposições anteriores |
| XI |  | Serviços de Tecnologia da Informação (TI) não classificados em subposições anteriores |
| XI |  | (VETADO) |
| XI |  | (VETADO) |
| XI |  | Serviço de localização de dispositivo perdido ou furtado, para proteção de informações pessoais |
| XI |  | Serviço de bloqueio de dispositivo perdido ou furtado, para proteção de informações pessoais |
| XI |  | pendente de classificação |
| XI |  | pendente de classificação |
| XI |  | Serviço de monitoramento de uso de dados pessoais e corporativos em redes do tipo onion |
| XI |  | Serviço de conexão protegida e criptografada para dispositivos |
| XI |  | Identificação e alerta de arquivos maliciosos ou alterações indevidas em dispositivos, que permitam o acesso a informações |
| XI |  | Serviços de manutenção e reparação de veículos militares para uso pela segurança nacional |
| XI |  | Serviços de manutenção e reparação de equipamentos militares para uso pela segurança nacional |
| XI | 2 | BENS RELACIONADOS À SOBERANIA E À SEGURANÇA NACIONAL, À SEGURANÇA DA INFORMAÇÃO E À SEGURANÇA CIBERNÉTICA |
| XI | 8709 | Viatura operacional militar e também suas partes e peças |
| XI |  | Carro blindado e carro de combate, terrestre ou anfíbio, sobre lagartas ou rodas, com ou sem armamento e também suas partes e peças |
| XI | 8709 | Outros veículos de qualquer tipo, para uso pelos órgãos de Segurança Pública e das Forças Armadas, com especificação própria dos Órgãos Militares e de Segurança Pública e também suas partes e peças |
| XI |  | Simuladores de veículos militares |
| XI | 8701 | Tratores de baixa ou de alta velocidades, para uso pelos órgãos de Segurança Pública e das Forças Armadas, sobre lagartas ou rodas, destinados às unidades de engenharia ou de artilharia, para obras ou para rebocar equipamentos pesados e também suas partes e peças |
| XI |  | Radares para uso militar |
| XI |  | Foguetes para uso militar |
| XI |  | Explosivos de emprego militar |
| XI |  | Optrônicos |
| XI |  | Rações operacionais |
| XI | 9306 | Minas marítimas |
| XI |  | Cartuchos de munição naval e de artilharia e seus componentes (projétil, estojo, estopilha, espoleta, traçador, pólvora e alto-explosivo), de calibre igual ou superior a 40 mm de diâmetro interno de tubo da arma |
| XI | 9306 | Bombas, torpedos, minas, mísseis, foguetes e seus componentes |
| XI |  | Aeronaves, inclusive Veículo Aéreo Não Tripulado (VANT) para uso pela segurança nacional e também suas partes e peças |
| XI |  | Veículos espaciais para uso pela segurança nacional |
| XI |  | Paraquedas para uso pela segurança nacional |
| XI |  | Aparelhos e dispositivos para lançamento e aterrissagem de veículos aéreos e espaciais para uso pela segurança nacional |
| XI |  | Simuladores de voo e similares para uso pela segurança nacional |
| XI | 8805 | Equipamentos de apoio no solo para uso pela segurança nacional |
| XI |  | Equipamentos de auxílio à comunicação, navegação e controle de tráfego aéreo para uso pela segurança nacional |
| XI |  | Embarcações construídas no País suas peças, partes e componentes utilizados no reparo, conserto e reconstrução de embarcações |
| XI |  | Dispositivos destinados a prover a segurança da informação do tipo Prevenção de Intrusão (IPS) |
| XI |  | Dispositivos destinados a prover a segurança da informação do tipo de Detecção de Intrusão (IDS) |
| XI |  | Dispositivos de Autenticação (tokens, leitores biométricos) que garantam a segurança da informação/cibernética |
| XI |  | Equipamentos para criptografia para a segurança da informação/cibernética |
| XI |  | Firewalls para a segurança da informação/cibernética |
| XI |  | Switches e roteadores seguros para a segurança da informação/cibernética |
| XI |  | Dispositivos de comunicação criptografada para a segurança da informação/cibernética |
| XI |  | Unidades de armazenamento criptografadas para a segurança da informação/cibernética |
| XI |  | Servidores de armazenamento seguro para a segurança da informação/cibernética |

## Sem referência a código NCM na descrição e sem coluna NCM/SH — 22 itens

> Resolvidos em 01/09/2026 (ver seção "✅ Resolvido" acima) e removidos desta lista: V (relógio
> braille, calculadora falante), V (despertador vibratório), VI itens 26/27/29/81, IX itens
> 2/5/6/11/14/15, XII item 1, XIII item 2.

| Anexo | Item | Descrição |
|---|---|---|
| V | 1 | ACESSÓRIOS E ADAPTAÇÕES ESPECIAIS PARA SEREM INSTALADOS EM VEÍCULOS AUTOMOTORES PERTENCENTES OU QUE FOREM DESTINADOS A PESSOAS COM DEFICIÊNCIA FÍSICA |
| V | 2 | PRODUTOS DESTINADOS A USO DE PESSOA COM DEFICIÊNCIA VISUAL |
| V | 3 | PRODUTOS DESTINADOS AO USO DE PESSOAS COM DEFICIÊNCIA AUDITIVA |
| VI | 67 | 2930.40.10<br><br>2930.40.90 |
| IX | 3 | Corretivos de solo (inclusive condicionadores), remineralizadores e substratos para plantas; em conformidade com as definições e demais requisitos da legislação específica |
| IX | 7 | Calcário, casca de coco triturada, turfa; tortas, bagaços e demais resíduos e desperdícios vegetais das indústrias alimentares; cascas, serragens e demais resíduos e desperdícios de madeira; resíduos da indústria de celulose (dregs e grits), ossos, borra de carnaúba, cinzas, resíduos agroindustri... |
| IX | 8 | 2503.00.10 <br> 2503.00.90 <br> 2510.10.10 <br> 2510.10.90 <br> 2510.20.10 <br> 2510.20.90 <br> 2802.00.00 <br> 2806.10.20 <br> 2807.00.10 <br> 2808.00.10 <br> 2809.20.11 <br> 2809.20.19 <br> 2811.19.20 <br> 2815.11.00 <br> 2815.12.00 <br> 2836.20.10 <br> 2836.20.90 <br> 2915.21.00 |
| IX | 10 | Semente genética, semente básica, semente nativa in natura, semente certificada de primeira geração (C1), semente certificada de segunda geração (C2), semente não certificada de primeira geração (S1), semente não certificada de segunda geração (S2) e sementes de cultivar local, tradicional ou cri... |
| IX | 22 | Serviços agronômicos |
| IX | 23 | Serviços de técnico agrícola, agropecuário ou em agroecologia |
| IX | 24 | 1.1405.21.00<br><br>1.1405.22.00 1.1405.90.00 |
| IX | 25 | Serviços de zootecnistas |
| IX | 26 | Serviços de inseminação e fertilização de animais de criação |
| IX | 27 | Serviços de engenharia florestal |
| IX | 28 | Serviços de pulverização e controle de pragas |
| IX | 29 | Serviços de semeadura, adubação, inclusive mistura de adubos, reparação de solo, plantio e colheita |
| IX | 30 | Serviços de projetos para irrigação e fertirrigação |
| IX | 31 | Serviços de análise laboratorial de solos, sementes e outros materiais propagativos, fitossanitários, água de produção, bromatologia e sanidade animal |
| IX | 32 | Licenciamento de direitos sobre cultivares |
| IX | 33 | Cessão definitiva de direitos sobre cultivares |
| IX | 34 | Melhoramento genético de animais e plantas e biotecnologia, inclusive seus royalties |
| IX | 35 | 2303.30.00<br><br>2303.20.00 |
| XII | 1 | Aparelhos de eletrodiagnóstico (incluídos os aparelhos de exploração funcional e os de verificação de parâmetros fisiológicos) |
| XIII | 2 | CADEIRA DE RODAS E OUTROS VEÍCULOS PARA DEFICIENTES, MESMO COM MOTOR OU OUTRO MECANISMO DE PROPULSÃO |

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

