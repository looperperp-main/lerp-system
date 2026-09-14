# Emissão Fiscal — NF-e, CT-e, NFS-e, NFCom, NF3e (plano)

> Última atualização: 14 de setembro de 2026

Escrito para ser lido do zero. Nada aqui foi implementado — é planejamento.

## 0. Por que este doc existe

`spec/fiscal/motor-fiscal-proximos-passos.md` (item 5/7) registrou em **29 de julho
de 2026** a decisão de **não emitir documento fiscal** na primeira fase do ERP: o
`fiscal-service` ficaria só cálculo (IBS/CBS/IS), e emissão (NF-e/NFC-e/NFS-e)
ficaria condicionada a duas coisas que ainda não existiam — entidade de
**estabelecimento** (emitente) e a **matriz da transição** (ICMS/ISS legado).

Duas coisas mudaram desde então:

- `spec/modulos/estabelecimentos/estabelecimentos-filiais.md` — Fases 1-6
  **escritas** (não testadas): existe entidade `Estabelecimento`, a Fase 6 já
  resolve `ufOrigem` do emitente próprio e o consome no `operacoes-service` /
  `fiscal-service`. O bloqueio de "não existe emitente" **deixou de valer**.
- A matriz da transição (item 3 do doc acima) está **código-completa e verde**
  desde 27 de agosto de 2026.

Este doc reabre o item 7 daquele roadmap (emissão), a pedido explícito, e
**amplia o escopo** além do que estava desenhado: não só NF-e/NFC-e, mas
CT-e, NFS-e (padrão nacional), NFCom (NF-telecom) e NF3e.

**Isto não decide se emissão deve entrar antes de AR/O2C ou P2P no roadmap geral**
— só planeja o que a emissão em si exige, para quando for priorizada.

## 1. Escopo e não-escopo

| Documento | Modelo | Finalidade | UF/abrangência pedida |
|---|---|---|---|
| NF-e | 55 | produto | SP, MG, RJ, DF, SC |
| NFC-e | 65 | venda ao consumidor final (varejo/PDV) | SP, MG, RJ, DF, SC |
| CT-e | 57 | transporte | SP, MG, RJ, DF, SC |
| NFS-e | — (padrão nacional) | serviço | nacional (por município aderente) |
| NFCom ("nf-telecom") | 62 | telecom | nacional, autorizador único |
| NF3e | 66 | energia elétrica | nacional, autorizador único |

Fora de escopo deste doc, por não ter sido pedido:

- Impressão térmica genérica de DANFE/DACTE/DANFCE (representação gráfica
  completa fica na etapa 9 da ordem de implementação, §5).
- Contingência SVC-AN/SVC-RS completa para NF-e/CT-e — etapa 9. A
  **contingência offline básica da NFC-e** não entra nessa exceção: é
  requisito da própria NFC-e (§4.2), não do backlog geral de contingência.

**Continua valendo a separação já decidida no motor fiscal:** `fiscal-service`
segue **só cálculo**, sem persistência. Este módulo de emissão consome o
resultado de `POST /fiscal/calcular` (e, quando existir, do cálculo legado do
item 3 daquele doc) — não duplica regra tributária.

## 2. Decisão de arquitetura: onde a emissão vive

**Decisão (13 de setembro de 2026): serviço isolado, nome `emissao-fiscal-service`.**
Segue a mesma convenção de nome dos demais módulos do reator (`cadastro-service`,
`billing-service`, `fiscal-service`). Não fica dentro do `operacoes-service`, que
seria a alternativa B (dono do documento comercial — pedido de venda, NF de
entrada) descartada. Razões da decisão:

1. Assinatura digital com certificado A1/A3 por tenant é responsabilidade
   sensível e isolável (guarda de chave, rotação, HSM futuro) — não deveria
   compartilhar processo com regra de negócio comercial do `operacoes-service`.
2. Comunicação com SEFAZ é webservice SOAP com timeout, retry e fila de
   contingência — natureza operacional diferente de CRUD/regra comercial.
3. Os cinco tipos de documento (§4) compartilham a mesma infraestrutura de
   assinatura/transmissão/protocolo (§3) — um serviço único evita triplicar
   essa infra dentro do `operacoes-service`.

**Mas não criar um microserviço por tipo de documento.** Um serviço só, com
módulos internos por tipo (`nfe`, `cte`, `nfse`, `nfcom`, `nf3e`) reaproveitando
a mesma infra comum de assinatura/transmissão. Isso é o rung 1 da lista
(evitar abstração não pedida): cinco serviços autorizadores é over-engineering
enquanto não houver evidência de que um módulo interno não escala.

O `operacoes-service` (AR) continua dono do documento comercial e **chama**
este novo serviço para emitir — o mesmo padrão já usado com `fiscal-service`
(`FiscalServiceClient` via Eureka).

### 2.1 Build vs. terceirizar (SaaS de emissão)

**Decisão (14 de setembro de 2026): não terceirizar via SaaS de emissão**
(ex. Focus NFe, PlugNotas, Tecnospeed) — o `emissao-fiscal-service` é
construído dentro do próprio reator, sem dependência de fornecedor externo de
emissão. Isso não impede o uso de **bibliotecas open-source self-hosted**
para reduzir esforço (assinatura XMLDSig, validação de schema XSD) — usar
biblioteca não é "comprar", é reuso, e continua valendo o rung 5 da escada
(dependência já instalável resolve, não reinventar). O que fica descartado é
delegar a emissão em si a uma API de terceiro.

Consequência prática: quem decide qual UF/documento está pronto para
produção é este serviço, não um provedor externo — o que também está
alinhado com a exigência do §7 de não introduzir lock-in.

## 3. Infra comum (o que todo tipo de documento exige)

1. **Certificado digital por tenant** — upload de A1 (`.pfx` + senha); A3
   (token/HSM) fica de fora do MVP. Guarda da chave nunca em disco puro —
   **decisão (14 de setembro de 2026): envelope encryption** (AES-256-GCM,
   chave de proteção — KEK — fora do banco), não um serviço de secrets à
   parte. Ver §7.2 para o raciocínio completo.
2. **Assinatura XML** (XMLDSig, padrão SEFAZ/ENCAT) — biblioteca Java
   existente (ex. wrappers de assinatura sobre `javax.xml.crypto`) em vez de
   implementar canonicalização/assinatura do zero.
3. **Numeração e série por documento × estabelecimento** — evita duplicidade;
   inutilização de faixa quando pula número.
4. **Cliente SOAP genérico** para os webservices de autorização — um cliente
   parametrizado por UF/endpoint, não um por UF.
5. **Contingência** (EPEC para NF-e/CT-e, SVC-AN/SVC-RS) — etapa 9 (§5), não
   bloqueia o MVP em ambiente de homologação.
6. **Consulta de status/protocolo, cancelamento, Carta de Correção Eletrônica
   (CC-e)** — obrigação de toda UF, não é "nice to have".
7. **Persistência do documento emitido** (XML assinado + protocolo + status)
   — dono é este novo serviço, não o `fiscal-service` (que segue sem schema de
   escrita) nem duplicado no `operacoes-service`.
8. **Representação gráfica** (DANFE/DACTE/DANFS-e, PDF) — etapa 9 (§5).
9. **Consumo do cálculo e do cadastro** — chama `fiscal-service`
   (`POST /fiscal/calcular`) para os valores de tributo por item e o
   `cadastro-service` (`Estabelecimento`, Fase 6 de
   `estabelecimentos-filiais.md`) para os dados do emitente.
10. **Emissão é assíncrona.** A SEFAZ responde em segundos/minutos, cai, entra
    em manutenção programada e às vezes autoriza sem o chamador saber (timeout
    na resposta) — tratar isso como uma chamada síncrona comum (padrão hoje
    usado com `fiscal-service`, que responde em milissegundos e é
    determinístico) é o erro clássico dessa integração. Desenho:
    - `POST /emissao/documentos` responde **202** com o id do documento; o
      desfecho sai por evento Kafka (mesmo padrão dos 11 tópicos já
      existentes) e por `GET /emissao/documentos/{id}`.
    - Máquina de estados explícita: `RASCUNHO → ASSINADO → TRANSMITIDO →
      AUTORIZADO | REJEITADO | DENEGADO | CONTINGENCIA`. `DENEGADA` é estado
      próprio — nota denegada não pode ser cancelada nem reaproveitada, e o
      número é consumido.
    - **XML assinado é persistido antes de transmitir** — se o processo cair
      entre assinar e transmitir, ainda dá para consultar pela chave de
      acesso.
    - Depois de timeout, o único caminho correto é **consultar pela chave de
      acesso antes de qualquer retentativa** — retry cego duplica número ou
      gera duas notas.
    - Job de reconciliação varrendo documentos presos em `TRANSMITIDO` há
      mais de N minutos.
    - Ver `CLAUDE.md` — regra geral de integrações externas longas adicionada
      a partir desta decisão.
11. **Pré-requisitos de cadastro e credenciamento.** Levantados ao revisar o
    que o `cadastro-service` tem hoje (14 de setembro de 2026, conferido no
    código, não só na spec):
    - `Estabelecimento` **não tem CRT** (Código de Regime Tributário: 1
      Simples, 2 Simples com excesso, 3 Normal, 4 MEI) — campo obrigatório do
      grupo `emit`. Falta coluna + changeset Liquibase.
    - `Endereco.ibge_codigo` é **nullable** hoje — `cMun` é obrigatório no
      XML tanto para emitente quanto destinatário. Precisa virar
      `NOT NULL` antes da Etapa 2 (ou validado na hora de emitir).
    - `Pessoa` **não tem `indIEDest`** (1 contribuinte / 2 isento / 9 não
      contribuinte) — campo obrigatório do grupo `dest` e fonte comum de
      rejeição. Não existe em nenhuma entidade do `cadastro-service` hoje
      (`ie`/`im` de `Pessoa` são `@Transient`, resolvidos do estabelecimento
      matriz).
    - A tela Angular de `Estabelecimento` **já existe**
      (`pages/cadastros/estabelecimento/`, rota
      `cadastros/pessoas/:pessoaId/estabelecimentos`, commit `3bb6e19` de
      07/09/2026) — CRUD completo (lista + form), não é gap. A afirmação em
      contrário veio de uma linha desatualizada em
      `estabelecimentos-filiais.md` §9 (escrita em 04/09/2026, antes desse
      commit); aquele doc precisa de uma correção pontual quando alguém for
      mexer nele de novo.
    - **Credenciamento do tenant como emissor na SEFAZ** é processo externo,
      humano, por tenant × UF × modelo (NF-e e NFC-e são credenciamentos
      separados). Proposta: tabela `emissao.credenciamento_sefaz`
      (`tenant_id`, `estabelecimento_id`, `uf`, `modelo` [`NFE`/`NFCE`/`CTE`],
      `status` [`PENDENTE`/`CREDENCIADO`/`BLOQUEADO`], `data_credenciamento`),
      preenchida manualmente por quem faz o credenciamento (não é
      automatizável) e consultada pelo fluxo de emissão **antes** de permitir
      produção — sem linha `CREDENCIADO`, emissão em produção nega com erro
      em PT-BR claro (`GlobalExceptionHandler`), nunca tenta e falha na
      SEFAZ. Vira checklist de onboarding fiscal na UI do tenant.
    - **`infRespTec` + CSRT** (Código de Segurança do Responsável Técnico) é
      obrigação do **fornecedor** (esta empresa), não do tenant — um
      `infRespTec` fixo (CNPJ, contato) entra em `common/Constants.java`
      (convenção do projeto para valor constante reutilizado); o CSRT é
      obtido junto à SEFAZ **por UF**, então vira tabela pequena
      `emissao.csrt_config` (`uf`, `id_csrt`, `hash`, `vigente_de`,
      `vigente_ate`) — mesmo padrão de vigência já usado em `fiscal.*`.
      Levantar o processo de obtenção por UF antes da Etapa 2.
12. **Configuração de endpoint é dado, não código — com roteamento de
    contingência acionável pelo tenant.** Decisão (14 de setembro de 2026):
    nenhuma URL de webservice fica hardcoded nem em `application.yml` — vive
    em tabela, editável por tela de administração, e a troca para
    contingência pode ser **forçada manualmente**, não só detectada
    automaticamente pela máquina de estados (item 10).
    - `emissao.webservice_endpoint` (`documento`, `autorizador`, `servico`,
      `versao`, `url`, `ambiente` [HOMOLOGACAO/PRODUCAO], `vigente_de`,
      `vigente_ate`) — carga inicial via Liquibase a partir de
      `webservices-nfe-referencia.md` (dado bruto, não plano).
    - `emissao.uf_autorizador` (`uf`, `documento`, `autorizador_normal`,
      `autorizador_contingencia`, `vigente_de`/`vigente_ate`) — mesmo padrão
      de vigência das tabelas de `fiscal.*`.
    - `emissao.contingencia_status` (`estabelecimento_id`, `documento`, `uf`,
      `modo` [NORMAL/CONTINGENCIA], `ativado_em`, `ativado_por`
      [AUTOMATICO/MANUAL], `motivo`) — a máquina de estados (item 10) escreve
      aqui após N falhas consecutivas; uma tela do tenant também escreve
      aqui, com um botão para forçar contingência (e outro para voltar ao
      normal) — regra do `CLAUDE.md` de esconder, não desabilitar: se já está
      em contingência, esconde "forçar contingência" e mostra só "voltar ao
      normal".
    - Resolução em runtime: `(estabelecimento, documento)` → UF do emitente
      → `uf_autorizador` dá o autorizador normal e o de contingência →
      `contingencia_status` decide qual dos dois usar agora →
      `webservice_endpoint` dá a URL final por `(autorizador, servico,
      ambiente, vigência)`. Adicionar ou trocar UF vira changeset + linha de
      config, nunca deploy de código.
    - Cache em memória (`@Cacheable`, TTL curto, `@CacheEvict` na edição pela
      UI) — dado muda raramente, não justifica Redis só para isso.

## 4. Por tipo de documento

### 4.1 NF-e (modelo 55) — SP, MG, RJ, DF, SC

- Layout 4.00 (schema XSD nacional / manual de integração do contribuinte).
- **Autorizador/contingência confirmados (14 de setembro de 2026)** — dados
  oficiais completos em `webservices-nfe-referencia.md`. Resumo das 5 UFs
  priorizadas:

  | UF | Autorizador | Contingência |
  |---|---|---|
  | SP | próprio (SEFAZ-SP) | SVC-AN |
  | MG | próprio (SEFAZ-MG) | SVC-AN |
  | RJ | SVRS | SVC-AN |
  | DF | SVRS | SVC-AN |
  | SC | SVRS | SVC-AN |

  As 5 caem na mesma contingência (SVC-AN) — implementar só um ambiente de
  contingência cobre o escopo inicial inteiro.
- **Cobertura não fica travada nessas 5.** A SVRS sozinha atende **16 UFs**
  nacionalmente (AC, AL, AP, CE, DF, ES, PA, PB, PI, RJ, RN, RO, RR, SC, SE,
  TO) — construir a integração SVRS para RJ/DF/SC já deixa as outras 13
  prontas para entrar como **linha de configuração** (§3, item 12), não
  código novo. Isso muda a ordem de prioridade dentro da NF-e (ver §5).
- Endpoint é dado, não hardcode — resolução em runtime pelo desenho do §3,
  item 12.
- Depende de: `Estabelecimento` como emitente (✅ escrito, Fase 6), matriz da
  transição para o lado legado ICMS (✅ código-completa), cálculo IBS/CBS novo
  (✅ pronto, `fiscal-service`).

### 4.2 NFC-e (modelo 65) — SP, MG, RJ, DF, SC

- Layout 4.00, mesma família de schema/assinatura da NF-e — mesmo autorizador
  por UF do §4.1 (SP/MG próprio, RJ/DF/SC via SVRS), a confirmar contra o
  manual vigente.
- Não é "NF-e menor" — três diferenças mudam o desenho:
  - **Série de numeração exclusiva**, nunca compartilhada com a NF-e do mesmo
    estabelecimento.
  - **QR Code + CSC (Código de Segurança do Contribuinte)** obrigatórios na
    representação gráfica (DANFCE). O CSC é gerado pela SEFAZ por
    estabelecimento e entra na composição do QR Code — material distinto da
    chave de assinatura XML, precisa de cadastro próprio.
  - **Contingência offline é requisito desde o início, não do backlog geral
    de contingência (etapa 9, §5).** Venda em PDV não pode parar por
    indisponibilidade da SEFAZ: a emissão local ocorre na hora, a
    autorização pode ser transmitida depois. É por isso que a NFC-e ganha
    etapa própria na ordem de implementação, com essa contingência básica
    incluída, em vez de esperar a etapa de contingência completa.
- DANFCE é impressão simplificada (cupom, não A4) — reaproveita o gerador de
  representação gráfica da etapa 9, com layout próprio.
- Depende de: a mesma infra da NF-e (§4.1) — na prática o mesmo módulo
  interno, com variação de fluxo (contingência antecipada) e de
  numeração/layout de saída.

### 4.3 CT-e (modelo 57)

- Layout 4.00, mesma família de assinatura/transmissão do SEFAZ/ENCAT — reaproveita
  quase toda a infra comum (§3).
- **Autorizador e contingência confirmados (14 de setembro de 2026, URLs
  oficiais levantadas pelo usuário)** — diferente do NF-e (§4.1), o CT-e usa
  só **dois** ambientes federados, não um por UF:

  | UF alvo | Autorizador | Contingência |
  |---|---|---|
  | SP | **SVSP** (Sefaz Virtual São Paulo) | SVC-RS |
  | MG, RJ, DF, SC | **SVRS** (Sefaz Virtual RS) | SVC-SP |

  Nota: diferente do NF-e, onde MG tem autorizador próprio, **para CT-e o MG
  autoriza via SVRS**. Regra de contingência publicada pelo SVRS: quem
  autoriza na SVRS (mais MG, PR, RS) cai na SVC-SP; quem autoriza na SVSP
  (mais MT, MS, SP) cai na SVC-RS — a contingência nunca é o mesmo ambiente
  da autorização, por desenho (evita que a falha do autorizador prejudique a
  contingência).

  Serviços SVRS (versão 4.00): `CteStatusServicoV4`, `CteConsultaV4`,
  `CteRecepcaoEventoV4`, `CTeRecepcaoOSV4`, `CTeRecepcaoSincV4`,
  `CTeRecepcaoGTVeV4`, `CTeRecepcaoSimpV4`, `qrcode` — todos em
  `https://cte.svrs.rs.gov.br/ws/...` (QR Code em
  `https://dfe-portal.svrs.rs.gov.br/cte/qrCode`). Serviços SVSP equivalentes
  em `https://nfe.fazenda.sp.gov.br/CTeWS/WS/...` (QR Code em
  `https://nfe.fazenda.sp.gov.br/CTeConsulta/qrCode`). Confirmar essas URLs
  contra o manual vigente no momento de implementar (webservices mudam de
  versão ao longo do tempo).
- MVP cobre só CT-e normal (`CTeRecepcaoSincV4`); complementar/substituto/
  anulação ficam para depois — YAGNI até haver caso real.
- Depende de: cadastro de transportadora (já existe no `cadastro-service`);
  tabela de frete/rota fica fora de escopo aqui.

### 4.4 NFS-e (Nota Fiscal de Serviço Eletrônica)

Maior risco de fragmentação do grupo: até poucos anos atrás, cada município
tinha layout próprio (ABRASF ou proprietário). Existe hoje um **padrão
nacional** (Sistema Nacional NFS-e, RFB + ENCAT, DPS/NFS-e via ADN — Ambiente
de Dados Nacional), que os municípios vêm migrando para adotar.

**Recomendação (lazy — usar o que já existe em vez de construir N
integrações):** implementar direto o **padrão nacional (ADN)**, não os legados
ABRASF por município. Ganho: uma integração serve todo município aderente, em
vez de uma por prefeitura.

- **Transporte é REST/JSON, não SOAP** — diferente de NF-e/NFC-e/CT-e/NFCom/
  NF3e (§3, item 4), o ADN recebe a DPS assinada por API REST. O "cliente SOAP
  genérico" do §3 não serve para este documento; a NFS-e herda de §3 a
  assinatura XML e a persistência, não o transporte.
- **Cobertura confirmada (14 de setembro de 2026), via painel Power BI da
  Receita.** ADN cobre mais de 5.000 municípios; **Metrópole e Grande Porte
  estão em 100% "Ativo operacional"** — zero aparecem no recorte de
  municípios não conveniados/não operacionais do painel, que sobra só para
  Pequeno Porte I/II e uma fatia pequena de Médio Porte. Ou seja: **todas as
  capitais e grandes cidades já emitem NFS-e pelo padrão nacional hoje** —
  não há mais risco de prometer emissão numa capital e ela não estar
  aderente. Fica fechado o item que antes pedia validação pontual.
- Depende de: item LC 116 + `cClassTrib` (✅ já resolvidos no motor fiscal),
  ISS por município (`fiscal.aliq_iss_municipio`, já existe desde a fatia 3d).

### 4.5 NFCom e NF3e (modelos 62 e 66) — fora do plano por ora

Ambos têm layout próprio (1.00) e autorizador único nacional (SVRS), o que os
tornaria simples de integrar reaproveitando a infra da Etapa 1 — mas o
público é restrito a um perfil de tenant que este ERP praticamente não
atende hoje (concessionária de telecom/energia). Pelo próprio critério deste
doc (§0: "precisa existir?"), a resposta é não, ainda. Ficam registrados
apenas para não surpreender depois; ver §8.

## 5. Ordem de implementação

Decidida em 14 de setembro de 2026 (revista após a revisão do Opus, §9) —
sequência, não lista solta de opções:

| Etapa | Entrega | Depende de |
|---|---|---|
| 0 | `fiscal-service` passa a devolver, por item, os campos que faltam para montar o XML — CST, `cClassTrib` (eco), base e alíquota separadas (`pIBSUF`/`pIBSMun`/`pCBS`), percentual de redução aplicado; no legado, CST/CSOSN + `vBC` reduzida + `pICMS` nominal. **Fora desta etapa, por decisão já registrada** (`motor-fiscal-proximos-passos.md`): PIS/COFINS/IPI/ICMS-ST/FCP/DIFAL continuam sem cálculo — o MVP de emissão se restringe a operações sem substituição tributária | — |
| 1 | Infra comum (§3): envelope encryption do certificado, assinatura XML, cliente SOAP genérico, numeração/série, persistência do documento, máquina de estados assíncrona (§3, item 10), UI de credenciamento/certificado — **sem emitir nada ainda** | 0 |
| 2 | NF-e em homologação, **via SVRS** (RJ, DF ou SC — a primeira UF concreta) | 1 |
| 3 | **NF-e em produção via SVRS: autorização + cancelamento + inutilização + DANFE no mesmo pacote.** Nada vai a produção sem os três — produção sem DANFE não é utilizável, e sem cancelamento é risco fiscal do tenant | 2 |
| 4 | **MG, depois SP** (autorizador próprio, uma integração específica cada) + demais UFs da SVRS como configuração (§4.1) + CC-e | 3 |
| 5 | NFS-e padrão nacional (ADN) — REST/JSON, mais simples que os documentos SOAP e alinhada à prioridade de mercado (serviço primeiro) | 1 + item LC 116/ISS já prontos |
| 6 | **NFC-e** (§4.2): série própria, CSC/QR Code **dentro do XML** (não é só representação gráfica), DANFCE com gerador próprio (cupom, não A4), contingência offline desde o início | 3 |
| 7 | CT-e — autorizador/contingência já mapeados (§4.3) | 1 |
| 8 | Contingência SVC completa (NF-e/CT-e) + EPEC | 2-7 |

NFCom e NF3e saíram da tabela — ver §4.5/§8 (fora do plano até haver tenant
do setor).

Mudanças em relação à primeira versão deste doc, motivadas pela revisão:
- **Etapa 0 nova** — sem os campos por item, a Etapa 2 trava no primeiro XML.
- **Cancelamento e DANFE entraram na etapa 3**, não mais isolados nas etapas 4/9 — não faz sentido "produção" sem os dois.
- **NFC-e não depende mais do gerador de representação gráfica** de uma etapa futura — o §4.2 já deixava claro que QR Code/CSC são requisito do XML, então o gerador de DANFCE é próprio da etapa 6, não emprestado.
- **NFS-e subiu para antes do CT-e** — é REST (mais simples que os documentos SOAP), a cobertura do ADN já está confirmada em volume (§4.4), e o mercado-alvo é serviço.

**Ordem por UF nas etapas 2/4, corrigida:** a versão anterior deste doc sugeria
"SP primeiro, por ser o caso mais difícil (autorizador próprio)" — isso nunca
foi uma posição do usuário, e a revisão mostrou que é a escolha errada.
**Ordem decidida (14 de setembro de 2026): SVRS → MG → SP.** A SVRS sozinha
autoriza **16 UFs** no Brasil (§4.1) — construí-la primeiro (via RJ, DF ou SC,
tanto faz qual das três) já deixa as outras 13 prontas como linha de
configuração (§3, item 12), não código novo. MG e SP entram depois, nessa
ordem, porque autorizador próprio é integração específica que só desbloqueia
a si mesmo — cada um é isolado, não há ganho em fazer um antes do outro além
da ordem escolhida.

**Mínimo comercialmente utilizável (B20):** NF-e modelo 55, via SVRS, com
autorização + cancelamento + inutilização + DANFE (etapas 0-3). Esforço é
desigual entre etapas — a Etapa 0 é dias (extensão de DTO sobre cálculo já
feito), a Etapa 1 é semanas (infra nova: assinatura, SOAP, máquina de
estados, config dinâmica), e cada etapa de documento novo (5 a 8) tende a ser
mais rápida que a 1 porque reaproveita a infra. Não há estimativa em
dias/horas neste doc — não é verificável sem começar a implementar.

## 6. Riscos e decisões em aberto

- ~~Confirmar autorizador de NF-e por UF contra o manual vigente~~ —
  **resolvido em dado (14/09/2026)**, `webservices-nfe-referencia.md`. E o
  usuário decidiu que isso **não bloqueia nada**: como a URL é config
  dinâmica editável por tela (§3, item 12), um endpoint desatualizado se
  corrige depois, sem deploy — não é pré-requisito de codar.
- **CSC (Código de Segurança do Contribuinte) da NFC-e é cadastro próprio por
  estabelecimento**, obtido no ambiente da SEFAZ de cada UF, e a lista de
  autorizadores/endpoints da NFC-e **não herda automaticamente** a do NF-e
  (§4.1) — é publicada à parte no Portal Nacional. Levantar antes da etapa 6.
- **Custo recorrente de manutenção, não custo de projeto.** A SEFAZ publica
  Notas Técnicas (2-3 por ano) com data de obrigatoriedade, cada uma exigindo
  atualizar XSD e código sob pena de rejeição em produção. Emissão fiscal
  precisa de dono contínuo depois de "pronta", não é projeto que termina.
- ~~Prazos de cancelamento/CC-e não estão fixados~~ — **resolvido em dado
  (14/09/2026)**, `regras-cancelamento-nfe.md`: varia de 8h (MT) a 1440h/60
  dias (PI); as 5 UFs priorizadas são todas 24h, mas o campo precisa ser
  configurável por UF desde já (§3, item 12), não constante no código. Fora
  da janela, o instrumento correto é nota de devolução/estorno — **ainda não
  existe** em lugar nenhum do sistema (`motor-fiscal-proximos-passos.md`,
  item 7.15), mas o Espírito Santo já tem um desenho legal pronto (NF-e de
  estorno) documentado como referência em `regras-cancelamento-nfe.md` para
  quando esse gap for priorizado — baixa urgência, as 5 UFs alvo dão 24h.
- **Numeração exige lock de concorrência** por `(estabelecimento, modelo,
  série)` — dois faturamentos simultâneos não podem tirar o mesmo número.
  Reaproveitar o `DistributedLock`/Redis que o `billing-service` já usa em
  vez de desenhar um mecanismo novo. NFC-e em PDV multi-terminal
  provavelmente precisa de série por terminal, não só por estabelecimento.
- **Convenções do projeto — decidido (14 de setembro de 2026): seguir a
  convenção já fixada, sem exceção nem desenho próprio.** Concretamente:
  schema `emissao.*` só via `liquibase-service` (nunca `ddl-auto`); camadas
  `api/controllers`, `api/dto`, `api/mappers`, `services`, `repository`,
  `domain` (mesmo layout de `cadastro-service`); config dinâmica do §3 item
  12 via **Spring Data JPA** + `@Cacheable`/`@CacheEvict` (`ConcurrentMapCache`
  ou Caffeine — não precisa de Redis só para isso, dado muda raro); erros via
  `GlobalExceptionHandler`/`StandardError` do `common`; códigos de
  status/rejeição/tipo de evento em `common/Constants.java`; enums Java
  próprios para `documento`/`servico`/`ambiente`/`modo` (não são "string
  reutilizável" no sentido do `Constants.java`, são tipo); `logback-spring.xml`
  com Loki e `correlationId`; headers `X-Tenant-Id`/`X-Is-Owner` injetados
  pelo gateway, nunca revalidados aqui.
- **Retenção legal do XML é 5 anos, e a guarda é responsabilidade do ERP, não
  só do tenant.** Ver plano completo em §7.3 (Postgres quente + arquivo frio
  em Object Storage) — decidido priorizando custo baixo/zero sobre pureza de
  portabilidade, com a exposição a fornecedor único mitigada pela API
  S3-compatível.
- **Adjacências fora de escopo, registradas para não surpreender depois**:
  MDF-e (modelo 58, obrigatório em transporte interestadual com frota
  própria — quem for emitir CT-e provavelmente precisa dele), CT-e OS
  (modelo 67), NF-e de exportação/devolução/complementar/remessa.
  **Manifestação do destinatário/DF-e não é gap** — o usuário já tem desenho
  próprio para isso; entra numa **Fase 2**, fora deste doc. A URL do
  `NFeDistribuicaoDFe` já está registrada em `webservices-nfe-referencia.md`
  para quando essa fase chegar.
- **Quem decide o modelo do documento (NF-e vs. NFC-e) é o `operacoes-service`**
  — regra comercial (NFC-e só para consumidor final, presencial, dentro da
  UF). O `emissao-fiscal-service` recebe o modelo já decidido e só valida
  coerência.
- Este módulo **não** reabre a decisão de que apuração (item 5 do doc do motor
  fiscal) é independente de emissão — as duas seguem desacopladas.

## 7. Infraestrutura e portabilidade — evitar lock-in, testar localmente

**Atualização (14 de setembro de 2026): a decisão da migração OCI→VPS foi
resolvida — a saída vai ser permanecer no OCI**, não migrar para HostGator
nem outro VPS (`project_github_project_reorg_vps_hostgator` fica desatualizado
nesse ponto). Isso não muda nenhuma decisão deste §7: as escolhas abaixo
(envelope encryption em vez de Vault, API S3-compatível em vez de SDK
proprietário) continuam corretas mesmo ficando no OCI — são sobre não travar
o **serviço** a um provedor, não sobre trocar de provedor agora. Ficar no OCI
só torna a arquitetura mais simples de operar no curto prazo (já é onde tudo
roda), sem abrir mão da portabilidade se a decisão mudar de novo no futuro.
Este serviço **não pode reintroduzir** um vínculo de nuvem além do que já
existe, e precisa rodar localmente para desenvolvimento/teste sem depender de
nenhum provedor.

1. **Empacotamento é igual aos outros sete módulos.** Dockerfile próprio,
   imagem publicada no Docker Hub via Jenkins
   (`vitorff1234/emissao-fiscal-service:<BUILD_NUMBER>`/`:latest`), registro no
   Eureka, rota no gateway. O único vínculo com Oracle hoje é a **VM de
   produção** (OCI Ampere ARM64) — fora do escopo deste doc e já em processo
   de troca. Nada neste serviço depende disso.

2. **O ponto que travaria em nuvem se não for pensado agora: guarda do
   certificado digital (§3, item 1).** Nunca usar um serviço proprietário
   (OCI Vault/KMS, AWS KMS, etc.) — isso prenderia o serviço ao provedor.

   **Decisão revista (14 de setembro de 2026): envelope encryption, não
   Vault.** A primeira versão deste doc recomendava HashiCorp Vault OSS
   self-hosted. Duas correções mudaram essa decisão:
   - O Vault mudou de licença (MPL → BUSL) em 2023 — não é mais "OSS" pela
     definição da OSI, e a Community Edition não tem namespaces (isolamento
     por tenant teria que ser por path/policy).
   - Mais importante: **todo container do Vault sobe selado (`sealed`)**, e
     nenhuma emissão funciona até alguém destravar. Auto-unseal exige KMS de
     nuvem — exatamente o lock-in que este documento existe para evitar — ou
     um segundo Vault de Transit unseal, que também precisa ser destravado
     (circular). Numa VPS única, isso é um ritual operacional para alguém
     acordar de madrugada, não um detalhe de produção.

   **Solução adotada: envelope encryption.** O `.pfx` do certificado é
   cifrado com **AES-256-GCM** usando uma chave de proteção (KEK) que vive
   fora do banco — variável de ambiente ou arquivo com permissão restrita no
   host, no mesmo padrão de `JWT_SECRET`/`DB_PASS` já usado pelos outros
   serviços. O blob cifrado fica no Postgres, junto do resto do cadastro do
   tenant. Ganhos: zero container novo, zero ritual de unseal, zero questão
   de licença, portabilidade idêntica entre OCI/VPS/local. Perde-se audit log
   e rotação nativos do Vault — cobertos, na prática, pelo `AuditEventDTO`/
   Kafka que o projeto já usa para eventos de segurança. Se um dia o audit
   granular ou a rotação automática virarem requisito real, a migração para
   Vault/OpenBao é incremental: só o ponto que lê a KEK muda, o resto do
   desenho (blob cifrado no Postgres) continua igual.

3. **Armazenamento e retenção legal do XML (5 anos) — decisão revista (14 de
   setembro de 2026), priorizando custo baixo/zero.** A guarda pelos 5 anos
   é responsabilidade do ERP, não só do tenant, e não pode depender só da
   disponibilidade do Postgres de produção.
   - **Postgres continua sendo a cópia quente** (schema `emissao.*`) — é o
     que a aplicação consulta no dia a dia, sem mudança em relação à versão
     anterior deste doc.
   - **Arquivo frio de 5 anos: bucket de Object Storage, acessado só pela
     API S3-compatível** (não o SDK proprietário do provedor). Cada XML
     assinado é copiado para o bucket de forma assíncrona logo após
     autorizado — mesmo padrão de write-through de um cache.
   - **Escolha de provedor: OCI Object Storage (Always Free), aceito de
     propósito.** É diferente do lock-in que o §7.1/§7.2 evita: ali o
     problema era a **VM de computação** (Ampere) e um **serviço de secrets**
     proprietário — os dois travariam a operação do dia a dia. Um bucket de
     objeto é o tipo de recurso mais portável que existe (API S3 é padrão de
     fato, mesma interface em MinIO/AWS/Backblaze/Cloudflare R2); XML tem
     ~20-40 KB, então mesmo em volume alto o Always Free (20 GB) cobre muito
     tempo sem custo, e mesmo saindo do free tier o Object Storage é mais
     barato que manter um MinIO self-hosted com disco e backup próprios numa
     VPS pequena. Migrar depois é trocar endpoint/credencial na config, não
     reescrever código.
   - **Backup do Postgres em si** (pg_dump/WAL) continua sendo item separado,
     de disponibilidade operacional — o arquivo frio no bucket é a garantia
     de retenção legal mesmo se o banco de produção for perdido, não
     substitui backup de banco.

4. **Perfil local (`dev`), sem nenhuma nuvem envolvida:**
   - `emissao-fiscal-service` entra no `docker compose up -d` local dos
     demais, porta sugerida **8094** (próxima livre depois do `fiscal-service`
     em 8093).
   - Com envelope encryption (§7.2) não há container de secrets para subir —
     a KEK local é uma variável de ambiente no `.env` de desenvolvimento, sem
     ritual nenhum.
   - Endpoints SEFAZ chamados são os de **homologação** por padrão no profile
     `dev` (`application-dev.yml`), mesmo padrão de `SPRING_PROFILES_ACTIVE=dev`
     já usado nos outros serviços — homologação é ambiente público e gratuito
     de cada UF, não exige infra paga.
   - **Certificado de teste: resolvido.** O usuário já tem certificado
     digital ICP-Brasil válido (confirmado em 14/09/2026) — homologação da
     SEFAZ exige certificado real (não há autoassinado que passe no
     handshake), e esse pré-requisito já está coberto. Falta só garantir que
     a cadeia ICP-Brasil está no truststore da JVM local (sintoma se faltar:
     `PKIX path building failed`).
   - Todos os componentes desse perfil (Postgres, Kafka, Redis) já são
     containers padrão — rodam no Docker Desktop/WSL local do Windows sem
     nenhuma conta de nuvem.

5. **CI/CD sem mudança de natureza.** Entra no mesmo `Jenkinsfile` reator
   (`-pl emissao-fiscal-service -am`), mesmo `jacoco-maven-plugin` com
   `coverage.minimum` inicial baixo (padrão de `gateway`/`registry`, 0.20).
   Jenkins já é self-hosted — nenhuma dependência nova de nuvem aqui.

**Resumo:** o único ponto deste serviço que poderia prender a nuvem era a
guarda do certificado, resolvido com envelope encryption (AES-256-GCM + KEK
fora do banco). Com isso, `emissao-fiscal-service` roda igual em OCI, no VPS
que for escolhido, ou local — sem exigir Oracle nem nenhum serviço de nuvem
proprietário em nenhum ponto.

## 8. O que este doc não decide

Não decide se emissão deve entrar antes de AR/O2C, P2P ou da matriz de
transição no roadmap geral — isso é chamada de priorização de produto, não de
arquitetura de emissão. Ver `spec/fiscal/motor-fiscal-proximos-passos.md`
("Próximos passos gerais") para o estado do resto do roadmap. A migração
OCI→VPS **já foi decidida** (14/09/2026: permanece no OCI, ver §7) — não é
mais item em aberto. E não inclui NFCom/NF3e (§4.5) nem manifestação do
destinatário/DF-e (§6) — ambos fora deste plano por ora, o segundo com
desenho próprio do usuário para uma fase futura.

## 9. Revisão (14 de setembro de 2026)

A primeira versão deste doc (13/09/2026) passou por revisão de arquitetura
(agente com modelo Opus, por pedido explícito do usuário — o doc original
tinha sido escrito com Sonnet). Achados endereçados nesta revisão: contrato
do `fiscal-service` incompleto para montar o XML (Etapa 0 nova, §5), ordem de
etapas com dependência circular (DANFE/cancelamento reagrupados, §5), Vault
trocado por envelope encryption (§7.2), certificado de teste resolvido (o
usuário já tem um válido), dados reais de autorizador/contingência do CT-e
(§4.3), transporte REST do NFS-e e cobertura do ADN confirmada (§4.4),
credenciamento por tenant/UF e `infRespTec`/CSRT propostos (§3, item 11),
gaps de cadastro confirmados no código — CRT, `ibge_codigo`, `indIEDest`
(§3, item 11). Um achado da revisão foi **corrigido por não se confirmar no
código**: a UI de `Estabelecimento` já existe (commit `3bb6e19`, 07/09/2026);
o que gerou a confusão foi uma linha desatualizada em
`estabelecimentos-filiais.md` §9, escrita antes desse commit.

Build vs. terceirizar (SaaS de emissão) foi decidido por rejeição direta —
não terceirizar (§2.1) — sem reabrir a construção própria em si.

**Segunda rodada (mesmo dia), sobre os achados B11-B20 e um requisito novo:**
configuração de endpoint virou dado dinâmico com contingência acionável pelo
tenant, não mais uma URL implícita (§3, item 12) — motivada por pedido direto
do usuário, não pela revisão; tabela completa de webservices de NF-e por
UF/autorizador/contingência levantada e movida para
`webservices-nfe-referencia.md`, mostrando que a SVRS sozinha atende 16 UFs
— isso corrigiu a ordem de implementação (§5: prioriza SVRS, não mais "SP
primeiro", posição que nunca foi do usuário); convenção do projeto para a
config dinâmica fechada como decisão, não risco (§6); retenção de 5 anos
resolvida com Postgres quente + arquivo frio em Object Storage OCI via API
S3-compatível, aceito deliberadamente por ser o recurso mais portável e mais
barato disponível, diferente do lock-in de VM/secrets que o §7 evita (§7.3);
manifestação do destinatário confirmada como não-gap (desenho próprio do
usuário, Fase 2); NFCom/NF3e cortados da tabela de etapas para um parágrafo
em não-escopo (§4.5/§8).

**Terceira rodada (14/09/2026):** tabela oficial de prazo de
cancelamento/servidor de contingência por UF levantada e movida para
`regras-cancelamento-nfe.md` — confirma que as 5 UFs alvo são todas 24h, mas
expõe variação real (MT 8h, PR/RS 168h, PI 1440h) que reforça a necessidade
de o prazo ser dado configurável, não constante; achado o desenho legal do
Espírito Santo ("NF-e de estorno") como referência pro gap de nota de
devolução; cobertura do ADN confirmada por print de painel Power BI —
Metrópole e Grande Porte em 100% "Ativo operacional" (§4.4); decisão de
manter a produção no OCI em vez de migrar pra VPS, sem alterar as escolhas de
portabilidade do §7 (são sobre não travar o serviço, não sobre qual provedor
usar agora).

## 10. Plano de mudança — cadastro-service e fiscal-service (Etapa 0)

Detalhamento dos gaps já identificados no §3 (item 11) e na Etapa 0 do §5,
pra ficar pronto pra execução quando essa fase começar.

### cadastro-service — back-end

- `Estabelecimento` ganha `crt` (enum `CodigoRegimeTributario`:
  `SIMPLES_NACIONAL`=1, `SIMPLES_EXCESSO`=2, `REGIME_NORMAL`=3, `MEI`=4),
  `NOT NULL`. Changeset Liquibase novo — dado existente sem CRT precisa de
  backfill; `REGIME_NORMAL` é o fallback mais conservador (tributa cheio),
  mas o valor real por tenant deve ser levantado no onboarding, não assumido
  em massa.
- `Endereco.ibgeCodigo` vira `NOT NULL` **só quando o endereço pertence a um
  `Estabelecimento`** (endereço de `Pessoa` PF pode continuar opcional) —
  constraint condicional, no mesmo espírito do CHECK XOR pessoa/estabelecimento
  que já existe na entidade.
- `Pessoa` ganha `indIeDest` (enum: `CONTRIBUINTE`=1, `ISENTO`=2,
  `NAO_CONTRIBUINTE`=9) — nullable no banco (nem toda pessoa é destinatário
  fiscal), mas obrigatório na validação do `emissao-fiscal-service` antes de
  emitir contra ela.
- DTOs e mappers (`EstabelecimentoDTO`, `PessoaDTO`, etc.) expõem os três
  campos novos nas APIs REST existentes.

### cadastro-service — front-end Angular

- `estabelecimento-form`: novo campo CRT (select, 4 opções).
- Form de endereço: o campo de município passa a resolver o `ibgeCodigo`
  automaticamente via lookup/autocomplete, em vez de digitação livre — evita
  o erro mais comum de rejeição de NF-e (`cMun` errado). Reaproveitar fonte
  de dado já existente no projeto se houver uma (ex. a carga de município do
  `fiscal.aliq_iss_municipio` já usa código IBGE).
- Form de pessoa: novo campo "Indicador de IE do destinatário" (select),
  visível quando a pessoa é usada como destinatário de operação fiscal.

### fiscal-service — Etapa 0 (contrato XML-ready)

- `OperacaoFiscalDTO` ganha, por item: `cst`, `cClassTrib` (eco do que veio
  no request), `percentualIbsUf`, `percentualIbsMunicipal`, `percentualCbs`,
  `percentualReducaoAplicado`; no legado: `cstIcms`/`csosn`,
  `percentualIcmsNominal`, `percentualReducaoBaseIcms`,
  `modalidadeBaseCalculoIcms` (`modBC`).
- `MotorFiscalService` já resolve `RegimeCClassTrib`/`AliquotaIbs`/
  `AliquotaCbs`/`RegimeIcms` internamente para calcular o valor final — a
  mudança é **parar de descartar** esses valores intermediários e devolvê-los
  no DTO, não recalcular nada novo.
- Sem mudança de schema — `fiscal-service` continua sem persistência, é só
  extensão de DTO + service.
- `MotorFiscalServiceTest` ganha asserts nos campos novos sobre o oráculo já
  existente (§1.4.8 do `Fin.md`), sem caso de teste novo do zero.

Esforço relativo: cadastro-service é pequeno-médio (a parte que exige cuidado
é a migração de dado existente sem CRT/`ibgeCodigo`, não o código novo em si);
fiscal-service Etapa 0 é pequeno (extensão de contrato sobre cálculo que já
existe).
