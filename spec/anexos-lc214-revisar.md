# Anexos da LC 214/2025 — itens NÃO carregados em `fiscal.regime_dif_ncm`

Gerado por `extrai-anexos-v2.ps1` a partir de `spec/ANEXOS.md`. Cada linha aqui é um item cuja
regra **não é decidível só pelo código** — carregá-la automaticamente erraria imposto em
silêncio. Resolver um item = decidir o(s) código(s) e acrescentar a linha num changeset novo.

Total pendente: **240** itens. Carregados automaticamente: **242** códigos.

## Pendências de `cClassTrib` (serviço) — seção mantida À MÃO, não sai do script

> Atualizada em 29 de julho de 2026, junto com o changeset `fiscal-021`.

`fiscal.regime_cclasstrib` passou a ter **25 dos 27** `cClassTrib` do Anexo VIII: 7 do
`fiscal-019` (fundamentados pelo próprio nome do anexo) + 18 do `fiscal-021` (cada linha com o
artigo da LC 214 no comentário). Sobraram **2**, porque o modelo de dados não consegue expressá-los
— um único `percentual_reducao` que vale para IBS e CBS ao mesmo tempo:

| `cClassTrib` | setor | por que ficou fora | o que falta para resolver |
|---|---|---|---|
| `010002` | operações do serviço financeiro | art. 233 não dá redução: fixa a **soma** de IBS + CBS em valor absoluto (10,85% em 2027-2028, 11,00% em 2029, 11,15% em 2030 ...). Não é percentual sobre a alíquota de referência, é outra alíquota. | coluna/tabela de alíquota absoluta por ano, ou uma linha em `aliq_cbs_regime`/`aliq_ibs_municipio` com regime próprio |
| `200025` | Prouni | art. 308 reduz a zero **apenas a CBS**; o IBS segue cheio. Um percentual só, aplicado aos dois tributos, erra um dos lados. | separar a redução por tributo (`percentual_reducao_ibs` / `_cbs`) |

Os dois continuam caindo em `RegimeDiferenciado.PADRAO` — **tributam cheio, e aqui o erro é contra
o contribuinte**. Emitir NFS-e de serviço financeiro ou de ensino superior no Prouni depende de
resolver isto antes.

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

## Sem referência a código NCM na descrição e sem coluna NCM/SH — 37 itens

| Anexo | Item | Descrição |
|---|---|---|
| V | 1 | ACESSÓRIOS E ADAPTAÇÕES ESPECIAIS PARA SEREM INSTALADOS EM VEÍCULOS AUTOMOTORES PERTENCENTES OU QUE FOREM DESTINADOS A PESSOAS COM DEFICIÊNCIA FÍSICA |
| V | 2 | PRODUTOS DESTINADOS A USO DE PESSOA COM DEFICIÊNCIA VISUAL |
| V |  | Relógio em **braille,** com sintetizador de voz e mostrador ampliado |
| V |  | Calculadora digital com sistema de voz, com verbalização dos ajustes de minutos e horas, tanto no modo horário, como no modo alarme, e comunicação por voz dos dígitos de cálculo e resultados |
| V | 3 | PRODUTOS DESTINADOS AO USO DE PESSOAS COM DEFICIÊNCIA AUDITIVA |
| V |  | Relógio despertador vibratório e/ou luminoso |
| VI | 26 | 2827.20.10<br><br>2827.20.90 |
| VI | 27 | 2827.31.10<br><br>2827.31.90 |
| VI | 29 | 3104.20.10<br><br>3104.20.90 |
| VI | 67 | 2930.40.10<br><br>2930.40.90 |
| VI | 81 | Triglicerídeos de cadeia média |
| IX | 2 | Fertilizantes (adubos), em conformidade com as definições e demais requisitos da legislação específica |
| IX | 3 | Corretivos de solo (inclusive condicionadores), remineralizadores e substratos para plantas; em conformidade com as definições e demais requisitos da legislação específica |
| IX | 5 | Bioestimulantes e bioinsumos para controle fitossanitário, em conformidade com as definições e demais requisitos da legislação específica |
| IX | 6 | Inseticidas, fungicidas, formicidas, herbicidas, parasiticidas, germicidas, acaricidas, nematicidas, raticidas, desfolhantes, dessecantes, espalhantes adesivos, estimuladores e inibidores de crescimento (reguladores); todos destinados diretamente ao uso agropecuário ou destinados diretamente à fa... |
| IX | 7 | Calcário, casca de coco triturada, turfa; tortas, bagaços e demais resíduos e desperdícios vegetais das indústrias alimentares; cascas, serragens e demais resíduos e desperdícios de madeira; resíduos da indústria de celulose (dregs e grits), ossos, borra de carnaúba, cinzas, resíduos agroindustri... |
| IX | 8 | 2503.00.10 <br> 2503.00.90 <br> 2510.10.10 <br> 2510.10.90 <br> 2510.20.10 <br> 2510.20.90 <br> 2802.00.00 <br> 2806.10.20 <br> 2807.00.10 <br> 2808.00.10 <br> 2809.20.11 <br> 2809.20.19 <br> 2811.19.20 <br> 2815.11.00 <br> 2815.12.00 <br> 2836.20.10 <br> 2836.20.90 <br> 2915.21.00 |
| IX | 10 | Semente genética, semente básica, semente nativa in natura, semente certificada de primeira geração (C1), semente certificada de segunda geração (C2), semente não certificada de primeira geração (S1), semente não certificada de segunda geração (S2) e sementes de cultivar local, tradicional ou cri... |
| IX | 11 | Mudas de plantas e demais materiais propagativos de plantas e fungos, inclusive plantas e fungos nativos de espécies florestais; em conformidade com as definições e demais requisitos da legislação específica |
| IX | 14 | Embriões e sêmen, congelado ou resfriado |
| IX | 15 | Reprodutores de raça pura, inclusive matrizes de animais puros de origem com registro genealógico; em conformidade com as definições e demais requisitos da legislação específica |
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

## Redação exclui parte dos códigos citados (exceto/ressalvado) — decidir a lista à mão — 25 itens

| Anexo | Item | Descrição |
|---|---|---|
| I | 19 | Carnes bovina, suína, ovina, caprina e de aves e produtos de origem animal (exceto _foies_ _gras_**)** dos seguintes códigos, subposições e posições da NCM/SH: a) 02.01, 02.02, 0206.10.00, 0206.2 e 0210.20.00; b) 02.03, 0206.30.00, 0206.4, 0209.10 e 0210.1; c) 02.04 e 0210.99.20, carne caprina cl... |
| I | 20 | Peixes e carnes de peixes (exceto salmonídeos, atuns, bacalhaus, hadoque, saithe e ovas e outros subprodutos) dos seguintes códigos, subposições e posições da NCM/SH: a) 03.02; exceto os produtos das subposições e dos códigos 0302.1, 0302.3, 0302.51.00, 0302.52.00, 0302.53.00 e 0302.9 da NCM/SH; ... |
| IV | 48 | Outras frações do sangue, exceto as preparadas como medicamentos, as imunoglobulinas séricas, o concentrado de fator VIII e a soroalbumina sob a forma de gel para preparação de reagentes de diagnóstico |
| IV | 49 | Reagentes de diagnóstico ou de laboratório em qualquer suporte e reagentes de diagnóstico ou de laboratório preparados, mesmo em um suporte, mesmo apresentados sob a forma de estojos, exceto os da posição 30.06; materiais de referência certificados |
| IV | 51 | Produtos para obturação dentária, exceto cimentos |
| IV | 54 | Equipamentos identificáveis para ostomia, exceto bolsas para uso em colostomia, ileostomia e urostomia |
| IV | 61 | Agulhas, exceto as de metal e as para suturas |
| VII | 1 | Crustáceos (exceto lagostas e lagostim) e moluscos dos seguintes códigos e subposições da NCM/SH: a) 0306.1 e 0306.3, exceto os produtos da subposição 0306.11 e dos códigos 0306.15.00, 0306.31.00, 0306.34.00, 0306.39.10; e b) 0307.31.00, 0307.32.00, 0307.42.00, 0307.43, 0307.51.00, 0307.52.00, 03... |
| VII | 4 | Farinha das posições 1101.00, 11.02, 11.05, 11.06 e 12.08 da NCM/SH; ressalvados os produtos relacionados no Anexo I |
| VII | 5 | Grumos e sêmolas de cereais dos códigos 1103.11.00 e 1103.19.00 da NCM/SH; ressalvados os produtos relacionados no Anexo I |
| VII | 6 | Grãos de cereais das subposições 1104.1 e 1104.2 da NCM/SH; ressalvados os produtos relacionados no Anexo I |
| VII | 14 | Frutas, produtos hortícolas e demais produtos vegetais, sem adição de açúcar ou de outros edulcorantes, classificados nos capítulos 7 e 8 da NCM/SH, ressalvados as frutas de casca rija não regionais e os produtos relacionados nos Anexos I e XV e excetuadas as posições 07.11, 08.12 e 0814.00.00 |
| VII | 15 | Cereais do capítulo 10 e sementes e frutos oleaginosos classificados no capítulo 12, ambos da NCM/SH, ressalvados os produtos relacionados no Anexo I |
| IX | 12 | Vacinas, soros e medicamentos, de uso veterinário, exceto de animais domésticos |
| IX | 13 | Aves de um dia, exceto as ornamentais |
| IX | 18 | Rações para animais, concentrados, suplementos, aditivos, premix ou núcleo, exceto para animais domésticos |
| IX | 19 | Sementes e cereais, mesmo triturados, em grãos esmagados ou trabalhados de outro modo; todos destinados diretamente à fabricação de ração para animais ou diretamente à alimentação animal, exceto de animais domésticos |
| IX | 20 | Farelos e tortas de produtos vegetais e demais resíduos e desperdícios das indústrias alimentares; todos destinados diretamente à fabricação de ração para animais ou diretamente à alimentação animal, exceto de animais domésticos |
| IX | 21 | Alho em pó, sal mineralizado, farinhas de peixe, de ostra, de carne, de osso, de pena, de sangue e de víscera, calcário calcítico, gorduras e óleos animais, resíduos de óleo e de gordura de origem animal ou vegetal descartados por empresas do ramo alimentício, e DL-Metionina e seus análogos; todo... |
| XII |  | Aparelhos de eletrodiagnóstico, exceto os produtos classificados nos códigos 9018.11.00, 9018.12.10, 9018.12.90, 9018.13.00, 9018.14.10, 9018.14.20, 9018.14.90, 9018.19.10 e 9018.19.20 |
| XII | 5 | Artigos e aparelhos de prótese, exceto os dentários e os produtos classificados nos códigos 9021.39.91 e 9021.39.99 |
| XII | 7 | Aparelhos de raio X, móveis, exceto os produtos classificados no código 9022.19.91 |
| XII | 11 | Aparelhos que utilizem radiações alfa, beta, gama ou outras radiações ionizantes, para usos médicos, cirúrgicos, odontológicos ou veterinários, incluídos os aparelhos de radiofotografia ou de radioterapia, exceto os produtos classificados nos códigos 9022.21.10 e 9022.21.20 |
| XIII | 4 | Aparelhos para facilitar a audição dos surdos, exceto partes e acessórios |
| XV | 2 | Produtos hortícolas das posições 07.01, 07.02.00.00, 07.03, 07.04, 07.05, 07.06, 0707.00.00, 07.08, 07.09 e 07.10, exceto os cogumelos e trufas classificados na subposição 0709.5 e no código 0710.80.00 da NCM/SH |

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

## Nenhum código NCM identificável na descrição — 4 itens

| Anexo | Item | Descrição |
|---|---|---|
| VII | 10 | Sucos naturais de fruta ou de produtos hortícolas sem adição de açúcar ou de outros edulcorantes e sem conservantes classificados na posição 20.09 da NCM/SH |
| VII | 11 | Polpas de frutas ou de produtos hortícolas sem adição de açúcar ou de outros edulcorantes e sem conservantes classificadas na posição 20.08 da NCM/SH |
| IX |  | NBS / NCM/SH |
| XV | 4 | Plantas e produtos de floricultura relativos à horticultura e cultivados para fins alimentares, ornamentais ou medicinais classificados no Capítulo 6 da NCM/SH |

## Continuação de item pendente — 4 itens

| Anexo | Item | Descrição |
|---|---|---|
| XII | 9022.14 | (código de continuação do item acima, que não entrou na carga) |
| XII | 9022.19 | (código de continuação do item acima, que não entrou na carga) |
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

