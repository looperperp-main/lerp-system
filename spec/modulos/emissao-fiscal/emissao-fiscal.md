# Emissão Fiscal — NF-e, CT-e, NFS-e, NFCom, NF3e (plano)

> Última atualização: 22 de setembro de 2026

Escrito para ser lido do zero. Nada aqui foi implementado — é planejamento.

## 0. Por que este doc existe

`spec/fiscal/motor-fiscal-proximos-passos.md` (item 5/7) registrou em **29 de julho
de 2026** a decisão de **não emitir documento fiscal** na primeira fase do ERP: o
`fiscal-service` ficaria só cálculo (IBS/CBS/IS), e emissão (NF-e/NFC-e/NFS-e)
ficaria condicionada a duas coisas que ainda não existiam — entidade de
**estabelecimento** (emitente) e a **matriz da transição** (ICMS/ISS legado).

Duas coisas mudaram desde então:

- `spec/modulos/estabelecimentos/estabelecimentos-filiais.md` — Fases 1-6
  **escritas e testadas** (confirmado pelo usuário, 22/09/2026): existe
  entidade `Estabelecimento`, a Fase 6 já resolve `ufOrigem` do emitente
  próprio e o consome no `operacoes-service` / `fiscal-service`. O bloqueio
  de "não existe emitente" **deixou de valer**.
- A matriz da transição (item 3 do doc acima) está **código-completa e verde**
  desde 27 de agosto de 2026.

Este doc reabre o item 7 daquele roadmap (emissão), a pedido explícito, e
**amplia o escopo** além do que estava desenhado: não só NF-e/NFC-e, mas
CT-e, NFS-e (padrão nacional), NFCom (NF-telecom) e NF3e.

**A ordem de priorização foi resolvida na prática: emissão entrou depois de
O2C e de P2P** (confirmado pelo usuário, 22/09/2026) — ver §8.

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
  completa fica na etapa 8 da ordem de implementação, §5).
- Contingência SVC-AN/SVC-RS completa para NF-e/CT-e — etapa 8. A
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

**Confirmado nesta revisão (22 de setembro de 2026): a decisão segue firme e
ficou mais forte, não mais fraca.** O `operacoes-service` (alternativa B
descartada) passou a existir de fato entre 13/09 e a data desta revisão —
vendas + compras + estoque num serviço só, 164 arquivos Java. Colocar
assinatura/SOAP/máquina de estados dentro dele agora seria pior do que era
quando a decisão foi tomada. Nada a mudar.

**Novo requisito de arquitetura (22 de setembro de 2026, decisão do usuário):
a API tem que ser vendável separadamente.** Consequência prática, não só
intenção: o `emissao-fiscal-service` **não pode chamar `fiscal-service` nem
`cadastro-service` em tempo de emissão** — todo dado fiscal e de
emitente/destinatário chega pronto no payload de `POST /emissao/documentos`
(ver item 9 e item 11 abaixo). Isso resolve junto a exigência de nunca
recalcular o que foi faturado: o serviço vira um executor puro (assina,
transmite, guarda protocolo, controla estado), sem lógica de cálculo
tributário nem acoplamento a schema interno do reator — o mesmo contrato que
um tenant do ERP-VSD usa é o que, no futuro, um cliente de fora do reator
poderia usar enviando os próprios dados fiscais já calculados.

**Fora do MVP, registrado em backlog:** autenticação/API key, rate limit e
medição de uso para um chamador **externo** de verdade (o MVP atende só
chamada interna via Eureka/headers do gateway) — issue
[#100](https://github.com/looperperp-main/lerp-system/issues/100).

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
   parte. Ver §7.2 para o raciocínio completo. **Decisão (22 de setembro de
   2026): versionamento sim, rotação em si adiada.** Toda linha cifrada
   (certificado e, futuramente, CSC — ver §4.2) já nasce com coluna
   `kek_version smallint` desde o changeset inicial — evita migração de
   schema quando a rotação virar necessidade real. O job de re-cifragem em
   si fica fora de escopo: operação rara (comprometimento de chave, ou
   rotina anual), não bloqueia a Etapa 1. *ponytail: coluna existe, job de
   rotação vem quando houver motivo real pra rotacionar.*
   **Alerta de vencimento do certificado — decisão (22 de setembro de
   2026): job diário, não checagem em request.** O certificado A1 expira em
   1 ano e é a causa nº1 de parada de emissão em qualquer integrador fiscal
   do mercado — mas checar validade a cada `POST /emissao/documentos` seria
   custo no caminho quente pra algo que muda uma vez por ano. Desenho mais
   barato e mais correto ao mesmo tempo: no upload, extrai
   `notAfter` do X.509 (`X509Certificate.getNotAfter()`, JDK puro, sem lib
   nova) pra coluna `certificado_valido_ate`; um `@Scheduled` diário roda
   **uma query indexada** (`WHERE certificado_valido_ate <= now() +
   interval '30 days' AND alerta_enviado = false`) e publica evento
   (`AuditEventDTO`/Kafka). Não é desenho novo — mesma forma de
   `TrialScheduler` (`auth-service`/`partner-service`) e `DunningJob`
   (`billing-service`), que já resolvem "avisar N dias antes de uma data"
   no projeto. Custo total: uma query por dia numa tabela pequena, zero
   overhead na emissão.
2. **Assinatura XML** (XMLDSig, padrão SEFAZ/ENCAT) — biblioteca Java
   existente (ex. wrappers de assinatura sobre `javax.xml.crypto`) em vez de
   implementar canonicalização/assinatura do zero. **Duas validações
   síncronas decididas nesta revisão (22/09/2026), antes de assinar/
   transmitir, para não gastar número nem depender da SEFAZ pra descobrir
   erro local:**
   - **CNPJ do certificado × emitente.** Confere o CNPJ do subject do X.509
     contra o `Estabelecimento.cnpj` (matriz ou filial — o CNPJ completo já
     distingue) antes de assinar. Falha vira 400 síncrono no
     `POST /emissao/documentos`, em PT-BR, antes de consumir número de
     série.
   - **Validação contra o XSD oficial antes de montar o envelope SOAP.**
     Gerar o XML a partir de classes tipadas (JAXB via `xjc` sobre o próprio
     XSD) já elimina erro estrutural (elemento fora de ordem, tag errada) —
     mas não pega restrição de *facet* (tamanho máximo de string, enum fora
     da lista, precisão decimal errada em campo monetário), que só aparece
     na prática como rejeição SEFAZ genérica se não for checado antes. Uma
     chamada de `javax.xml.validation.Validator.validate()` (JDK puro, sem
     lib nova) contra o XSD oficial — que já precisa estar em disco pra
     gerar as classes JAXB — fecha isso sem virar subsistema novo: uma
     linha, antes do SOAP, rung 3 da escada (stdlib resolve).
3. **Numeração e série por documento × estabelecimento** — evita duplicidade;
   inutilização de faixa quando pula número. **Decisão (22 de setembro de
   2026): lock via `SELECT ... FOR UPDATE` no Postgres**, não
   `DistributedLockService`/Redis. Motivo: essa classe mora hoje em
   `billing-service` (não em `common`), e reaproveitá-la aqui exigiria movê-la
   e tornaria o Redis dependência dura do `emissao-fiscal-service` — mesmo
   efeito de health agregado DOWN que o `CLAUDE.md` já documenta para o
   billing quando o Redis cai. `FOR UPDATE` na linha de
   `emissao.numeracao_documento` usa uma dependência que já é obrigatória
   (Postgres), sem novo ponto de falha.
4. **Cliente SOAP genérico** para os webservices de autorização — um cliente
   parametrizado por UF/endpoint, não um por UF.
5. **Contingência** (EPEC para NF-e/CT-e, SVC-AN/SVC-RS) — etapa 8 (§5), não
   bloqueia o MVP em ambiente de homologação.
6. **Consulta de status/protocolo, cancelamento, Carta de Correção Eletrônica
   (CC-e)** — obrigação de toda UF, não é "nice to have". Cancelamento entra
   junto da produção (etapa 3, §5); CC-e entra na etapa 4 — nenhuma das duas
   é descartada, só chegam em etapas diferentes do rollout.
7. **Persistência do documento emitido** (XML assinado + protocolo + status)
   — dono é este novo serviço, não o `fiscal-service` (que segue sem schema de
   escrita) nem duplicado no `operacoes-service`.
8. **Representação gráfica** (DANFE/DACTE/DANFS-e, PDF) — etapa 8 (§5).
9. **Consumo do cálculo e do cadastro — não acontece em tempo de emissão**
   (decisão §2 / §3 item 11: opção b). O `emissao-fiscal-service` nunca chama
   `fiscal-service` nem `cadastro-service` — recebe no próprio payload de
   `POST /emissao/documentos` o snapshot fiscal já resolvido por item
   (gravado no faturamento, ver item 11) e os dados de emitente/destinatário
   já resolvidos (`Estabelecimento`, Fase 6 de `estabelecimentos-filiais.md`,
   resolvido por quem chama — hoje o `operacoes-service`).

   **PIS/COFINS e os condicionais (ICMS-ST/IPI/FCP/DIFAL) — decisão (22 de
   setembro de 2026): bloquear em vez de emitir errado, nunca inventar um
   CST "seguro".** `fiscal-service` mantém a decisão já tomada e testada
   (`Constants.FISCAL_AVISO_PIS_COFINS_APURACAO_EXTERNA`,
   `motor-fiscal-proximos-passos.md` item 7.9) de não calcular PIS/COFINS —
   PIS/COFINS continua devido **integralmente** (art. 348 LC 214/2025), não
   é isento, então usar um CST de isenção/não-incidência como placeholder
   seria declarar uma coisa falsa no XML, pior do que não emitir. Em vez
   disso:
   - O snapshot fiscal (item 11) ganha os campos do grupo PIS/COFINS
     (`cstPis`/`cstCofins`/`vBcPis`/`vBcCofins`/`pPis`/`pCofins`/`vPis`/
     `vCofins`), como **opcionais** — quem monta o payload pode fornecer se
     tiver a apuração real de outro lugar.
   - Se a operação for de **produto** (NF-e, regime normal) e esses campos
     vierem nulos, `POST /emissao/documentos` **rejeita com 400** antes de
     gastar número — não tenta adivinhar. Cálculo real fica pra depois,
     backlog na issue
     [#101](https://github.com/looperperp-main/lerp-system/issues/101).
   - Mesma lógica pros condicionais que já eram excluídos do MVP (ICMS-ST,
     IPI, FCP, DIFAL — §5, Etapa 0): a exclusão vira **guarda ativa**, não
     só nota de rodapé. `operacoes-service`/`emissao-fiscal-service` detecta
     o caso que exigiria um desses (ex. venda interestadual pra consumidor
     final não contribuinte exige DIFAL/FCP) e bloqueia com erro claro, em
     vez de emitir uma nota que sairia sem o valor devido.
   - **NFS-e (serviço) não é afetada** — esse grupo é específico de NF-e/
     produto; serviço já tem sua própria retenção modelada (fatia 3e,
     `valorIssRetido`/`valorCsrf`/etc.).
10. **Emissão é assíncrona.** A SEFAZ responde em segundos/minutos, cai, entra
    em manutenção programada e às vezes autoriza sem o chamador saber (timeout
    na resposta) — tratar isso como uma chamada síncrona comum (padrão hoje
    usado com `fiscal-service`, que responde em milissegundos e é
    determinístico) é o erro clássico dessa integração. Desenho:
    - `POST /emissao/documentos` responde **202** com o id do documento; o
      desfecho sai por evento Kafka (mesmo padrão dos 11 tópicos já
      existentes) e por `GET /emissao/documentos/{id}`.
    - **Idempotency key obrigatória no `POST` — decisão (22 de setembro de
      2026): chave fornecida pelo chamador, não derivada de `pedidoId`.**
      Como o serviço precisa ser vendável separadamente (§2), não dá para
      assumir que todo chamador tem o conceito de `pedidoId` do
      `operacoes-service` — mesmo padrão do Stripe: header
      `Idempotency-Key` obrigatório (string livre, gerada por quem chama),
      gravado em `emissao.idempotency_key` (`tenant_id`, `idempotency_key`,
      `documento_id`, UNIQUE em `tenant_id` + `idempotency_key`). Reenvio
      com a mesma chave devolve a resposta já processada (mesmo status/id),
      sem reprocessar — nunca dispara segunda emissão. Requisição sem o
      header é rejeitada com 400 antes de tocar em qualquer lógica de
      emissão. É adicional ao item de contingência logo abaixo (que protege
      contra duplicação do lado SEFAZ) — este protege contra duplicação do
      lado do chamador, antes de qualquer SEFAZ estar envolvida.
      **Decisão (22 de setembro de 2026): mesma chave = mesmo evento, mas com
      fingerprint de identidade, não validação do payload inteiro.** Validar
      o corpo inteiro é frágil — qualquer campo incidental (timestamp,
      metadado de rastreio) muda entre duas chamadas "iguais" e gera falso
      conflito, confuso principalmente pra um chamador externo (§2) sem
      acesso ao log deste serviço. Mas confiar cegamente na chave sem checar
      nada tem um risco real: um bug de geração de chave do lado de quem
      chama (chave fixa, ou reaproveitada por engano entre dois documentos
      diferentes) faz o serviço devolver **silenciosamente** o resultado do
      primeiro documento pra todas as chamadas seguintes — sucesso reportado,
      nota nunca emitida de fato, o pior tipo de falha num sistema fiscal.
      Meio-termo: `emissao.idempotency_key` ganha uma coluna `fingerprint`
      (hash de um subconjunto pequeno e estável — `estabelecimentoId` +
      `modelo` + CNPJ/CPF do destinatário + `valorTotal` + quantidade de
      itens —, nunca o payload cru; **sem `tenantId`** — a linha já é
      escopada por `tenant_id` na UNIQUE da tabela, incluir de novo no
      fingerprint não agrega distinção nenhuma, é só um `Long` redundante).
      Fingerprint bate → mesmo evento, devolve a resposta cacheada (regra de
      cima). Fingerprint diverge → **409**, sem tentar adivinhar qual dos
      dois documentos é o "certo" — obriga o chamador a gerar uma chave
      nova.
    - **Máquina de estados — revisão (22 de setembro de 2026): faltavam
      `CANCELADO`/`INUTILIZADO`, nomenclatura `DENEGADA`/`DENEGADO`
      inconsistente, reaproveitamento de número na `REJEITADO` não estava
      explícito, e `ERRO` aparecia só na métrica sem ser estado real.**
      `RASCUNHO → ASSINADO → TRANSMITIDO → AUTORIZADO | REJEITADO | DENEGADO
      | CONTINGENCIA`, mais:
      - `AUTORIZADO → CANCELADO` — só via evento de cancelamento, dentro da
        janela legal; documento já tem efeito fiscal, esse é o único jeito
        de desfazer.
      - `RASCUNHO → INUTILIZADO` — número reservado mas nunca chegou a ser
        assinado/transmitido (gap de sequência que o emitente decide pular).
      - `REJEITADO → RASCUNHO` — rejeição da SEFAZ (erro de schema/regra)
        **não consome o número**, ao contrário de `DENEGADO`; reaproveita o
        mesmo número e a mesma `Idempotency-Key` da tentativa anterior pra
        reemissão corrigida (ver item 9, correção de snapshot).
      - `DENEGADO` (nomenclatura uniformizada — nunca "DENEGADA") é estado
        terminal próprio — nota denegada não pode ser cancelada nem
        reaproveitada, e o número é consumido.
      - `ERRO` — estado real, não só rótulo de métrica: o job de
        reconciliação (abaixo) joga aqui os documentos presos além da
        janela sem resposta classificável da SEFAZ; exige intervenção
        manual, não é reprocessado automaticamente.
      - **Contingência muda a chave de acesso** — `tpEmis` faz parte dos 44
        dígitos. Se o timeout dispara `TRANSMITIDO → CONTINGENCIA` num
        documento já assinado em modo normal, a assinatura anterior fica
        inválida — precisa **reassinar** com o `tpEmis` de contingência, não
        só retransmitir o mesmo XML.
    - **XML assinado é persistido antes de transmitir** — se o processo cair
      entre assinar e transmitir, ainda dá para consultar pela chave de
      acesso.
    - Depois de timeout, o único caminho correto é **consultar pela chave de
      acesso antes de qualquer retentativa** — retry cego duplica número ou
      gera duas notas.
    - Job de reconciliação varrendo documentos presos em `TRANSMITIDO` há
      mais de N minutos.
    - **Observabilidade — decisão (22 de setembro de 2026): reaproveitar o
      padrão já em produção no `billing-service`, sem desenhar nada novo.**
      Métricas em `/actuator/prometheus`, mesmo formato de
      `webhook_processado_total{evento,resultado}`:
      - `emissao_documento_total{documento,resultado}` — contador,
        incrementado a cada transição terminal da máquina de estados
        (`documento` = NFE/NFCE/CTE/…, `resultado` =
        AUTORIZADO/REJEITADO/DENEGADO/ERRO).
      - `emissao_documento_pendente{documento}` — gauge, quantos documentos
        estão presos em ASSINADO/TRANSMITIDO há mais da janela do job de
        reconciliação acima.
      - `job_segundos_desde_ok{job="emissao-reconciliacao"}` — reaproveita a
        métrica de job já genérica no reator (mesma usada pelo billing), só
        com um novo valor de label — não é métrica nova.
      Logs seguem o padrão já fixado no `CLAUDE.md`: `logback-spring.xml` com
      `Loki4jAppender` e `correlationId` no pattern, labels só de baixa
      cardinalidade (`app`/`host`/`level`) — `tenantId` e chave de acesso
      nunca viram label, filtram-se por `|=` no LogQL, igual ao resto do
      reator.
    - Ver `CLAUDE.md` — regra geral de integrações externas longas adicionada
      a partir desta decisão.
    - **Publicação do evento Kafka — decisão (22 de setembro de 2026):
      transactional outbox, piloto neste serviço antes de virar padrão do
      reator.** Sem isso, a transição de estado no banco e a publicação no
      Kafka são duas operações separadas — se o processo cair entre uma e
      outra, o evento nunca sai (consumidor nunca sabe que a nota mudou de
      estado) ou sai duplicado num retry. Desenho (reaproveita infra que já
      existe, sem serviço novo):
      - Tabela `emissao.outbox_evento` (`id` UUID, `documento_id`,
        `tipo_evento`, `payload` JSONB, `criado_em`, `publicado_em`
        nullable) — a escrita aqui acontece **na mesma transação** que muda
        o estado do documento, então banco garante atomicidade entre os
        dois sem transação distribuída (é a mesma instância do Postgres).
      - Um `@Scheduled` de poll curto (mesma forma de `ReconciliationJob`/
        `DunningJob` do `billing-service`, não infra nova) lê linhas não
        publicadas com `FOR UPDATE SKIP LOCKED` (permite mais de uma
        instância do serviço rodando sem publicar a mesma linha duas
        vezes), publica no Kafka e marca `publicado_em`. Falha de publish
        deixa a linha pra próxima rodada — retry natural pelo próprio
        intervalo do poll, sem backoff separado.
      - **Entrega é at-least-once, não exactly-once** — um crash entre o ack
        do Kafka e marcar `publicado_em` reenvia no próximo poll. Consumidor
        do evento precisa ser idempotente (dedupe pelo `id` do evento) — não
        é requisito novo, é a garantia padrão de qualquer consumidor Kafka
        bem escrito, só ficou explícito agora.
      - **Fica só neste serviço por ora, não vai para `common` ainda.**
        Extrair uma abstração compartilhada antes de validar em produção
        seria abstração sem uso comprovado (rung 1 da escada) — a decisão do
        usuário é validar aqui primeiro e replicar depois se funcionar.
11. **Pré-requisitos de cadastro e credenciamento — parte já implementada,
    parte ainda em aberto (revisado em 22 de setembro de 2026, conferido
    contra o código, não só contra esta spec).**

    **Já implementado, no commit `d313921` (15/09/2026), um dia depois da
    revisão anterior desta spec — a Etapa 0 do cadastro está pronta:**
    - `Estabelecimento.crt` (CRT — Código de Regime Tributário) existe:
      `cadastro-service/.../domain/Estabelecimento.java:81-84` (`@NotNull`,
      enum `CodigoRegimeTributario`), enum em
      `domain/enumerators/CodigoRegimeTributario.java:7`, changeset
      `liquibase-service/.../cadastro/cadastro-schema-016.yaml:7-31`
      (`cad-052`, com backfill `REGIME_NORMAL` — exatamente o desenho do §10),
      exposto em `EstabelecimentoRequestDTO.java:16` /
      `EstabelecimentoResponseDTO.java:25`, usado em
      `services/EstabelecimentoService.java:93,116,142` e no form Angular
      (`estabelecimento-form.html:29-41`).
    - `Endereco.ibgeCodigo` segue nullable na coluna (`domain/Endereco.java:81-83`),
      mas o gap foi fechado do jeito que o §10 desenhava: CHECK condicional
      `NOT VALID` só quando `estabelecimento_id IS NOT NULL`
      (`cadastro-schema-016.yaml:59-72`, `cad-054`) + validação de negócio em
      PT-BR (`services/EnderecoService.java:124-134`). O front resolve o
      código IBGE por lookup de CEP via ViaCEP
      (`pessoa-form.ts:312-326`), como o §10 também pedia.
    - `Pessoa.indIeDest` existe: `domain/Pessoa.java:84-86` (nullable, como
      desenhado), enum `IndicadorIeDestinatario.java:7`, changeset
      `cadastro-schema-016.yaml:33-49` (`cad-053`), exposto em
      `PessoaRequestDTO.java:29` / `PessoaResponseDTO.java:29`.
    - `OperacaoFiscalDTO` (`fiscal-service`) já expõe percentuais e base
      separados por item, conforme o §10 pedia:
      `api/dto/OperacaoFiscalDTO.java:47-75`, populado em
      `services/MotorFiscalService.java:261-268` (saída) e `:599-602`
      (entrada), com asserts em `MotorFiscalServiceTest.java:64,222`.
    - A tela Angular de `Estabelecimento` **já existe**
      (`pages/cadastros/estabelecimento/`, rota
      `cadastros/pessoas/:pessoaId/estabelecimentos`, commit `3bb6e19` de
      07/09/2026) — CRUD completo (lista + form), não é gap. A afirmação em
      contrário veio de uma linha desatualizada em
      `estabelecimentos-filiais.md` §9 (escrita em 04/09/2026, antes desse
      commit); **ainda sem correção** — confirmado nesta revisão que aquele
      doc segue afirmando "não existe nenhuma tela/serviço Angular para
      Estabelecimento" (linhas 365-372) e que seu §4.1/§7 também não
      mencionam o `crt` novo. Corrigir os dois pontos na próxima vez que
      alguém mexer naquele doc.

    **Ainda em aberto — o que resta de fato da Etapa 0:**
    - ~~**CST/CSOSN por item.**~~ **Resolvido (commit `20209a6`, 22/09/2026,
      confirmado verde — 123 testes):** tabela `fiscal.cst_icms_regra` no
      `fiscal-service`, resolvida em `TabelaFiscal.resolverCstIcms`/
      `MotorFiscalService` — detalhe completo em §10/§11 ("fiscal-service —
      CST e CFOP").
    - ~~**CFOP real por item.**~~ **Resolvido (commit `20209a6`, 22/09/2026,
      confirmado verde):** tabela `fiscal.cfop_regra` no `fiscal-service`,
      chaveada por natureza de operação × âmbito × tipo de operação — detalhe
      completo em §10/§11. `FiscalServiceClient.java` (operacoes-service) já
      consome via `naturezaOperacao`, em vez do CFOP `5102` fixo.
    - ~~**Quem carrega CST/CFOP/percentuais até o XML — ainda em aberto**~~
      **Decisão (22 de setembro de 2026, do usuário): opção (b) — snapshot
      persistido no faturamento, nunca recalculado na emissão.** Duas
      exigências do produto fecham essa escolha, não só a consistência do
      valor: (1) o dado que vai no XML tem que ser exatamente o que foi
      faturado, mesmo que o `fiscal-service` mude tabela de vigência depois;
      (2) o `emissao-fiscal-service` precisa ser vendável como produto
      separado (§2) — não pode depender de `fiscal-service`/`cadastro-service`
      pra montar o XML. Plano (nada implementado ainda):
      - **Nova tabela `vendas.pedido_item_fiscal_snapshot`** (1:1 com
        `pedido_item`, novo changelog `vendas-schema-004.yaml`), escrita uma
        única vez dentro da mesma transação de `PedidoService.faturar()` que
        hoje só grava os agregados no `Pedido`
        (`PedidoService.java:355-363`) — espelha os campos do
        `OperacaoFiscalDTO` (`fiscal-service/.../api/dto/OperacaoFiscalDTO.java`)
        que hoje se perdem no faturamento: `cst`, `cstIcms`, `csosn`,
        `cClassTrib`, `cfop` (o resolvido, não o default), `naturezaOperacao`,
        `baseCalculo`, `percentualIbsUf`/`percentualIbsMunicipal`/
        `percentualCbs`/`percentualReducaoAplicado`,
        `valorIbsEstadual`/`valorIbsMunicipal`, legado
        (`valorIcms`/`valorIss`/`percentualIcmsNominal`/
        `percentualReducaoBaseIcms`/`modalidadeBaseCalculoIcms`),
        `regimeAplicado`. Nunca é sobrescrita depois de gravada — imutável,
        mesmo padrão do restante do documento fiscal já emitido.
      - **Correção pós-rejeição — decisão (22 de setembro de 2026): nova
        versão, nunca `UPDATE` na linha original.** Gap identificado na
        revisão: snapshot imutável não tinha caminho nenhum de correção
        quando a SEFAZ rejeita por CST/NCM errado (item 1, `REJEITADO →
        RASCUNHO`). `pedido_item_fiscal_snapshot` ganha `versao` (inteiro,
        default 1) e `motivo_correcao` (obrigatório a partir da versão 2);
        `POST /emissao/documentos` sempre lê a versão mais recente do
        `pedido_item`. Correção é ação manual — tela fiscal em
        `operacoes-service` grava a nova versão —, permitida só enquanto o
        documento estiver em `RASCUNHO`/`REJEITADO`; depois de `AUTORIZADO`
        o snapshot não importa mais pra esse documento e o caminho já
        existente resolve (CC-e pra dado não financeiro na etapa 4,
        cancelamento + reemissão pra dado financeiro na etapa 3) — não cria
        mecanismo novo pra esse caso.
      - **`FiscalServiceClient.java:91-95` (operacoes-service) para de
        truncar o resultado.** O record interno
        `OperacaoFiscalResultado`/`ResultadoFiscalItem` hoje desserializa só
        8 campos de valor com `@JsonIgnoreProperties(ignoreUnknown = true)` —
        vira um DTO espelhando o `OperacaoFiscalDTO` completo, porque é
        exatamente esse resultado (sem recálculo) que alimenta o snapshot
        acima.
      - **`POST /emissao/documentos` carrega o snapshot inteiro no corpo da
        requisição, por item** — não um id que o `emissao-fiscal-service`
        resolveria buscando de volta no `operacoes-service`. É isso que
        garante os dois requisitos ao mesmo tempo: nunca recalcula (lê
        snapshot imutável) e nunca acopla (payload autocontido — qualquer
        chamador, interno ou externo, pode preenchê-lo do jeito dele).
      - Consequência já registrada no item 9 acima (consumo do cálculo/cadastro).
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
    - **Responsável Técnico (RT) ≠ Emissor.** O emissor de cada nota é sempre
      o tenant (CNPJ dele, certificado dele, credenciamento dele — item
      acima). O **Responsável Técnico** é quem *desenvolveu o software*, e
      aparece num grupo à parte do XML (`infRespTec`), independente de quem
      emitiu a nota. **SYAX não precisa ter CNPJ próprio para isso** — o RT é
      identificado só pelo CNPJ informado, e esse CNPJ pode ser o do Vitor
      (CNPJ hoje usado para outro serviço dele); "SYAX" é só o nome do
      produto, não precisa ser uma pessoa jurídica separada. Se um dia SYAX
      virar CNPJ próprio, é só re-credenciar o RT com o CNPJ novo — troca de
      dado, não de arquitetura.
    - **Cadastro do RT tem duas partes, uma externa e uma em código:**
      1. **Credenciamento (externo, manual, uma vez por UF):** o RT se
         cadastra no portal da SEFAZ daquele estado (CNPJ, nome de contato,
         e-mail, telefone) e recebe um **CSRT** (código, com vigência —
         precisa renovar periodicamente). Isso não é automatizável, é feito
         fora do sistema.
      2. **Preenchimento do XML (código, por documento):** com o CSRT em
         mãos, todo NF-e/NFC-e emitido carrega um grupo `infRespTec` com
         CNPJ/contato fixos (`common/Constants.java`, convenção do projeto
         para valor constante reutilizado) mais dois campos calculados por
         nota: `idCSRT` (o código vigente daquela UF) e `hashCSRT` =
         Base64(SHA-1(CSRT + chave de acesso de 44 dígitos da NF-e)). Isso é
         puramente determinístico, sem chamada externa.
      O resultado do passo 1 (por UF) fica em `emissao.csrt_config` (`uf`,
      `id_csrt`, `vigente_de`, `vigente_ate`) — mesmo padrão de vigência já
      usado em `fiscal.*`. **Guarda do CSRT — corrigido nesta revisão
      (22/09/2026): envelope encryption, não hash.** O `hashCSRT` acima é
      calculado **por nota**, a partir do CSRT original — o sistema precisa
      do valor reversível toda vez que emite um documento novo, porque a
      chave de acesso muda a cada nota e o hash tem que ser recalculado.
      Guardar só um hash do CSRT (sem o valor original) quebra o mecanismo:
      hash é via de mão única, não dá pra recuperar o CSRT de volta pra
      recalcular o próximo `hashCSRT`. Colunas corretas:
      `csrt_cifrado` (AES-256-GCM) + `kek_version` — mesmo envelope
      encryption e mesma KEK do certificado (§3, item 1) e do CSC (§4.2), não
      um mecanismo novo.
    - **Não existe portal nacional único para o credenciamento do RT** — é
      por SEFAZ estadual (página da área do contribuinte/desenvolvedor, às
      vezes também via webservice). Preencher `infRespTec` numa UF que não
      exige gera **rejeição 225 (falha de schema)** — não é "preencher
      sempre por segurança", tem que saber por UF se está exigido. Limite:
      até 5 CSRT válidos simultâneos por estado (revogar um antigo pra gerar
      o 6º).
    - **Confirmado (14/09/2026) para as 5 UFs prioritárias — pendência
      fechada.** Fonte: NT 2018.005 v1.52 (`spec/NT2018 005_v1_52-
      AlteracaodeleiauteNF-eNFC.pdf`, §2.1/2.2 — CSRT é "a critério da UF",
      cada estado publica se/como exige) + confirmação direta nos portais
      oficiais de SP/MG/DF:
      - **SC**: **obrigatório** desde 29/04/2020 (lista oficial da NT:
        AM, MS, PE, PR, SC, TO — early adopters em produção desde
        03/06/2019).
      - **RJ**: aceita o campo **facultativamente** — não obrigatório.
      - **SP**: SEFAZ-SP não publicou sistema/exigência de CSRT — não
        obrigatório.
      - **MG**: SEF-MG declara oficialmente (FAQ NFC-e/CSRT) que não tem
        previsão de exigir — não obrigatório, sem link de cadastro
        disponível.
      - **DF**: SEEC-DF usa infraestrutura padrão sem ambiente próprio de
        CSRT — não obrigatório.
      **Conclusão prática**: `infRespTec`/CSRT só entra em produção de fato
      para **SC** na Etapa 2/3; para SP/MG/RJ/DF o campo fica implementado
      (é dado, não código travado) mas sem CSRT configurado — nunca
      preenchido, evitando a rejeição 225 por schema.
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
      `autorizador_contingencia`, `vigente_de`/`vigente_ate`,
      `prazo_cancelamento_horas`) — mesmo padrão de vigência das tabelas de
      `fiscal.*`. **Corrigido nesta revisão (22/09/2026):** a coluna
      `prazo_cancelamento_horas` já era exigida por
      `regras-cancelamento-nfe.md:5-7`, mas não constava no desenho da
      tabela aqui — os dois docs estavam divergentes; alinhado agora.
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
- Depende de: `Estabelecimento` como emitente (✅ escrito e testado, Fase 6),
  matriz da transição para o lado legado ICMS (✅ código-completa), cálculo
  IBS/CBS novo (✅ pronto, `fiscal-service`).

### 4.2 NFC-e (modelo 65) — SP, MG, RJ, DF, SC

- Layout 4.00, mesma família de schema/assinatura da NF-e — mesmo autorizador
  por UF do §4.1 (SP/MG próprio, RJ/DF/SC via SVRS), a confirmar contra o
  manual vigente.
- Não é "NF-e menor" — três diferenças mudam o desenho:
  - **Série de numeração exclusiva**, nunca compartilhada com a NF-e do mesmo
    estabelecimento.
  - **QR Code + CSC (Código de Segurança do Contribuinte).** A URL do QR
    Code é **obrigatória dentro do XML**, grupo `infNFeSupl` — a DANFCE só
    reproduz essa URL como imagem impressa, não é ela quem carrega o dado
    (correção de texto nesta revisão: uma frase anterior dava a entender que
    era só requisito de representação gráfica). O CSC em si nunca vai no XML
    nem é transmitido à SEFAZ — entra num hash (SHA-1) junto da chave de
    acesso, e esse hash compõe a URL do QR Code que valida a nota (app "De
    olho na nota", fiscalização); é por isso que é material sensível, mesma
    classe do certificado digital.
    **Decisão (22 de setembro de 2026): guarda dentro do próprio
    `emissao-fiscal-service`, não numa tabela do `fiscal-service` ou do
    `cadastro-service`.** Tabela `emissao.estabelecimento_csc`
    (`estabelecimento_id`, `uf`, `id_token`, `csc` cifrado, `kek_version`) —
    mesmo envelope encryption (AES-256-GCM) e mesma KEK do certificado (§3,
    item 1; §7.2), não um mecanismo novo. Morar dentro deste serviço (e não
    ser buscado de outro em tempo de emissão) é consequência direta do
    requisito de §2 — o `emissao-fiscal-service` nunca chama outro serviço
    pra montar o documento; CSC é credencial de emissão, não dado fiscal
    calculado, então fica no balde do certificado, não no balde do snapshot
    fiscal (item 9/11). **Não é urgente**: NFC-e é a etapa 6 do §5, uma das
    últimas — o changeset só precisa existir quando essa etapa começar de
    fato.
  - **Contingência offline é requisito desde o início, não do backlog geral
    de contingência (etapa 8, §5).** Venda em PDV não pode parar por
    indisponibilidade da SEFAZ: a emissão local ocorre na hora, a
    autorização pode ser transmitida depois. É por isso que a NFC-e ganha
    etapa própria na ordem de implementação, com essa contingência básica
    incluída, em vez de esperar a etapa de contingência completa.
- DANFCE é impressão simplificada (cupom, não A4) — reaproveita o gerador de
  representação gráfica da etapa 8, com layout próprio.
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
sequência, não lista solta de opções. **A regra de produção da etapa 3 (nada
vai a produção sem autorização + cancelamento + representação gráfica) vale
igualmente para a etapa 5 (NFS-e) e a etapa 7 (CT-e) — gap identificado na
revisão de 22/09/2026: a tabela abaixo listava as duas dependendo só da
etapa 1, o que deixaria NFS-e/CT-e irem a produção sem DACTE/DANFS-e nem
cancelamento, o mesmo furo que a segunda rodada já tinha corrigido para a
NF-e sem generalizar para os outros documentos:**

| Etapa | Entrega | Depende de |
|---|---|---|
| 0 | `fiscal-service` passa a devolver, por item, os campos que faltam para montar o XML — CST, `cClassTrib` (eco), base e alíquota separadas (`pIBSUF`/`pIBSMun`/`pCBS`), percentual de redução aplicado; no legado, CST/CSOSN + `vBC` reduzida + `pICMS` nominal. **✅ 100% pronto e verde** (§3, item 11; §10/§11) — cadastro-service e os percentuais do `fiscal-service` no commit `d313921` (15/09/2026); CST/CSOSN e CFOP real no commit `20209a6` (22/09/2026, 123 testes). Quem carrega esses campos até o XML (§3 item 11, último bullet) **já está decidido** — opção (b), snapshot persistido no faturamento. **Fora desta etapa, por decisão já registrada** (`motor-fiscal-proximos-passos.md`): PIS/COFINS/IPI/ICMS-ST/FCP/DIFAL continuam sem cálculo — **decisão de 22/09/2026 (§3, item 9): isso vira guarda ativa** (bloqueia emissão do caso em vez de emitir sem o valor devido), não só nota de rodapé; cálculo real de PIS/COFINS fica na issue [#101](https://github.com/looperperp-main/lerp-system/issues/101) | — |
| 1 | Infra comum (§3): envelope encryption do certificado, assinatura XML, cliente SOAP genérico, numeração/série (lock via `SELECT FOR UPDATE`, não Redis), persistência do documento, máquina de estados assíncrona com idempotency key (§3, item 10), UI de credenciamento/certificado — **sem emitir nada ainda** | 0 |
| 2 | NF-e em homologação, **via SVRS** (RJ, DF ou SC — a primeira UF concreta) | 1 |
| 3 | **NF-e em produção via SVRS: autorização + cancelamento + inutilização + DANFE no mesmo pacote.** Nada vai a produção sem os três — produção sem DANFE não é utilizável, e sem cancelamento é risco fiscal do tenant | 2 |
| 4 | **MG, depois SP** (autorizador próprio, uma integração específica cada) + demais UFs da SVRS como configuração (§4.1) + CC-e | 3 |
| 5 | NFS-e padrão nacional (ADN) — REST/JSON, mais simples que os documentos SOAP e alinhada à prioridade de mercado (serviço primeiro). **Produção exige a mesma regra da etapa 3: autorização + cancelamento + representação gráfica (DANFS-e) juntos** | 1 + item LC 116/ISS já prontos |
| 6 | **NFC-e** (§4.2): série própria, CSC/QR Code **dentro do XML** (grupo `infNFeSupl`, não é só representação gráfica — guarda do CSC já decidida, `emissao.estabelecimento_csc`, §4.2), DANFCE com gerador próprio (cupom, não A4), contingência offline desde o início | 3 |
| 7 | CT-e — autorizador/contingência já mapeados (§4.3). **Produção exige a mesma regra da etapa 3: autorização + cancelamento + representação gráfica (DACTE) juntos** | 1 |
| 8 | Contingência SVC completa (NF-e/CT-e) + EPEC | 2-7 |

NFCom e NF3e saíram da tabela — ver §4.5/§8 (fora do plano até haver tenant
do setor).

**Estratégia de validação — decisão (22 de setembro de 2026): homologação
real da SEFAZ, não mock.** O endpoint já é dado configurável por `ambiente`
(`emissao.webservice_endpoint`, §3 item 12) — cada etapa que entrega um
documento novo bate primeiro no ambiente de homologação público e gratuito
da UF (mesma infraestrutura, só troca a linha de config) e só muda pra
`PRODUCAO` depois de passar. Não é infraestrutura de teste nova: é a mesma
tabela que resolve a URL em runtime, com `ambiente` como mais um campo de
vigência — trocar de homologação pra produção é o mesmo tipo de operação que
trocar de UF (§3, item 12). As etapas 2 (homologação) e 3 (produção) do §5
já refletiam essa ordem; esta nota só a torna decisão explícita.

**CI além de homologação real — decisão (22 de setembro de 2026): fixture
gravado uma vez, não simulador.** Gap identificado na revisão: homologação
real depende de rede externa, é lenta e não tem credencial em pipeline —
não dá pra rodar em todo commit no Jenkins. Duas camadas:
- **Toda build (sem rede):** um XML de resposta real por resultado
  (`AUTORIZADO`/`REJEITADO`/`DENEGADO`) gravado uma vez a partir da
  homologação, servido por um stub do `WebServiceClient` (mesma interface
  que a integração real usa) — testa assinatura → montagem do SOAP → parse
  da resposta → transição de estado (item 10) fim a fim, e valida o XML
  contra o XSD oficial (`javax.xml.validation.Validator`, já decidido no
  item 6) — sem tocar SEFAZ.
- **Definition of done de cada etapa (já decidido acima, não muda):**
  homologação real continua sendo o gate antes de promover `ambiente` pra
  `PRODUCAO`.
- ponytail: fixture só é regravado quando uma NT (nota técnica) mudar o
  schema — não é simulador de SEFAZ, é uma resposta canned.

Mudanças em relação à primeira versão deste doc, motivadas pela revisão:
- **Etapa 0 nova** — sem os campos por item, a Etapa 2 trava no primeiro XML.
- **Cancelamento e DANFE entraram na etapa 3**, não mais isolados nas etapas 4/8 — não faz sentido "produção" sem os dois.
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
- ~~CSC (Código de Segurança do Contribuinte) da NFC-e sem tabela de
  guarda~~ — **decidido (22/09/2026, ver §4.2):** tabela
  `emissao.estabelecimento_csc` dentro do próprio `emissao-fiscal-service`,
  mesmo envelope encryption do certificado. A lista de autorizadores/
  endpoints da NFC-e **não herda automaticamente** a do NF-e (§4.1) — é
  publicada à parte no Portal Nacional; isso continua de pé, sem urgência
  (NFC-e é etapa 6, uma das últimas).
- **Custo recorrente de manutenção, não custo de projeto.** A SEFAZ publica
  Notas Técnicas (2-3 por ano) com data de obrigatoriedade, cada uma exigindo
  atualizar XSD e código sob pena de rejeição em produção. Emissão fiscal
  precisa de dono contínuo depois de "pronta", não é projeto que termina.
- ~~Prazos de cancelamento/CC-e não estão fixados~~ — **resolvido em dado
  (14/09/2026)**, `regras-cancelamento-nfe.md`: varia de 8h (MT) a 1440h/60
  dias (PI); as 5 UFs priorizadas são todas 24h, mas o campo precisa ser
  configurável por UF desde já (§3, item 12 — coluna `prazo_cancelamento_horas`
  alinhada entre os dois docs nesta revisão), não constante no código. Fora
  da janela, o instrumento correto é nota de devolução/estorno — **ainda não
  existe** em lugar nenhum do sistema (`motor-fiscal-proximos-passos.md`,
  item 7.15), mas o Espírito Santo já tem um desenho legal pronto (NF-e de
  estorno) documentado como referência em `regras-cancelamento-nfe.md` para
  quando esse gap for priorizado — baixa urgência, as 5 UFs alvo dão 24h.
- ~~Numeração exige lock de concorrência~~ — **resolvido (22/09/2026):**
  `SELECT ... FOR UPDATE` no Postgres na linha de
  `emissao.numeracao_documento`, não `DistributedLockService`/Redis (ver §3,
  item 3, para o motivo). NFC-e em PDV multi-terminal provavelmente precisa
  de série por terminal, não só por estabelecimento — isso não muda o
  mecanismo de lock, só a granularidade da chave.
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
  S3-compatível. **Decisão (22 de setembro de 2026): o arquivo frio cobre XML
  de entrada também, não só o emitido** — a obrigação legal de guarda de 5
  anos (art. 173 CTN + regras do SPED) não distingue documento emitido de
  documento recebido. Partição por `direcao` (`ENTRADA`/`SAIDA`) na chave do
  objeto, pra não misturar os dois lados numa auditoria que peça só um deles.
  **Correção (22/09/2026, verificado contra o código):** o XML de entrada
  não existe hoje em lugar nenhum do sistema — `RecebimentoMercadoria`
  (`operacoes-service`, P2P Fase 3) guarda só a *referência* digitada
  manualmente (`nfeNumero`/`nfeSerie`/`nfeChave`/`nfeDataEmissao`,
  `RecebimentoMercadoria.java:79-99`), não o XML em si; o comentário no
  próprio código já registra isso como fora de escopo do P2P. A única forma
  real de capturar esse XML é o webservice `NFeDistribuicaoDFe` (Manifestação
  do Destinatário/DF-e), que é a Fase 2 já citada abaixo como fora deste doc.
  **Decidido: essa captura vira spec própria, fora do MVP de emissão fiscal**
  — backlog registrado na issue
  [#99](https://github.com/looperperp-main/lerp-system/issues/99). Não
  bloqueia nada da Etapa 1.
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
   produção** (OCI Ampere ARM64) — fora do escopo deste doc. **Corrigido
   nesta revisão:** a frase original dizia essa VM "já em processo de
   troca", o que contradiz a decisão do próprio §7 (14/09/2026) de
   permanecer no OCI — não há troca de VM em andamento. Nada neste serviço
   depende disso de qualquer forma.

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
   desenho (blob cifrado no Postgres) continua igual. **Decisão (22 de
   setembro de 2026, ver §3 item 1): coluna `kek_version smallint` em toda
   tabela cifrada (certificado e CSC, §4.2) desde o changeset inicial — o job
   de re-cifragem que efetivamente rotaciona fica fora de escopo por ora,
   operação rara demais pra bloquear a Etapa 1.**

3. **Armazenamento e retenção legal do XML (5 anos) — decisão revista (14 de
   setembro de 2026), priorizando custo baixo/zero.** A guarda pelos 5 anos
   é responsabilidade do ERP, não só do tenant, e não pode depender só da
   disponibilidade do Postgres de produção.
   - **Postgres continua sendo a cópia quente** (schema `emissao.*`) — é o
     que a aplicação consulta no dia a dia, sem mudança em relação à versão
     anterior deste doc.
   - **Arquivo frio de 5 anos: bucket de Object Storage (**OCI**, ver escolha
     de provedor logo abaixo — a API S3-compatível é só a interface de
     acesso, não confundir com AWS S3), acessado só por essa API**, não o SDK
     proprietário do provedor. Cada XML assinado é copiado para o bucket de
     forma assíncrona logo após autorizado — mesmo padrão de write-through de
     um cache. **Cobre XML de entrada também (decisão 22/09/2026, ver §6)** —
     mesmo bucket, chave particionada por `direcao` (`ENTRADA`/`SAIDA`).
   - **Estrutura de chave — decisão (22 de setembro de 2026): uma "subpasta"
     por chave de acesso, eventos separados do documento principal.**
     Prefixo (o OCI Object Storage não tem pasta real, mas o prefixo funciona
     como hierarquia igual a diretório):
     ```
     {direcao}/{tenantId}/{ano}/{estabelecimentoId}/{modelo}/{chaveAcesso}/
       documento.xml       — nfeProc (XML assinado + protocolo de
                             autorização, formato padrão SEFAZ, não só o XML
                             cru)
       eventos/
         cancelamento.xml  — procEventoNFe
         cce-01.xml        — CC-e pode ter mais de uma; sequência no nome
     ```
     Reflete o próprio modelo de dado da SEFAZ (`procNFe` e `procEventoNFe`
     já são tipos de XML distintos) — a estrutura só espelha isso, não
     inventa modelagem nova.
   - **Confirmação de entrega no bucket — decisão (22 de setembro de 2026):
     retry com backoff exponencial, 3 tentativas por padrão, número
     configurável.** A cópia assíncrona pro bucket tenta de novo sozinha
     antes de alertar alguém — `emissao.arquivo-frio.max-tentativas` em
     `application.yml` (default `3`, sobe pra `5` só trocando config, sem
     deploy). Backoff exponencial entre tentativas (ex. 1s, 2s, 4s) evita
     martelar o bucket num problema transitório. Esgotadas as tentativas,
     publica evento (mesmo padrão de `AuditEventDTO`/Kafka já usado no
     projeto) e loga `ERROR` — a garantia de retenção legal de 5 anos
     depende dessa cópia existir, então falha silenciosa aqui não é
     aceitável (regra do `CLAUDE.md`: nunca simplificar tratamento de erro
     que evita perda de dado).
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

Não decide a arquitetura interna de O2C/P2P nem da matriz de transição — isso
é escopo dos respectivos docs (`spec/modulos/o2c-vendas/o2c-vendas.md`,
`spec/p2p-compras.md`, `spec/fiscal/motor-fiscal-proximos-passos.md`). **A
ordem de priorização no roadmap geral já não é mais item em aberto: na
prática, emissão entrou depois de O2C e de P2P** (confirmado pelo usuário,
22/09/2026) — os dois já tinham backend implementado quando este doc foi
retomado. A migração OCI→VPS **já foi decidida** (14/09/2026: permanece no
OCI, ver §7) — também não é mais item em aberto. E não inclui NFCom/NF3e
(§4.5) nem manifestação do destinatário/DF-e (§6) — ambos fora deste plano
por ora, o segundo com desenho próprio do usuário para uma fase futura.

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

## 10. Revisão (22 de setembro de 2026) — Etapa 0 no código, decisões de CST/CFOP/lock

Nova revisão de arquitetura (agente com modelo Opus, mesmo protocolo da §9),
pedida antes de começar a Etapa 1. Achado central: **a Etapa 0 já tinha sido
implementada no commit `d313921` (15/09/2026), um dia depois da revisão
anterior (§9)** — CRT, `ibgeCodigo` condicional e `indIeDest` no
`cadastro-service`, percentuais/base separados no `OperacaoFiscalDTO` do
`fiscal-service`. O doc ficou desatualizado no dia seguinte à própria
revisão; corrigido nesta rodada (§3, item 11, e tabela do §5).

O que restava de fato da Etapa 0 — CST/CSOSN (sempre `null` hoje) e CFOP
(default fixo hardcoded) — ganhou decisão nesta revisão: ambos resolvidos em
tabela própria no `fiscal-service` (`fiscal.cst_*` e `fiscal.cfop_regra`),
mesmo padrão das demais tabelas fiscais já existentes, mantendo a separação
"fiscal-service é o único dono de regra tributária" (§1/§2). Lock de
numeração de nota (§3, item 3) decidido como `SELECT ... FOR UPDATE` no
Postgres, não `DistributedLockService`/Redis — a classe reaproveitável mora
no `billing-service`, não em `common`, e usá-la tornaria Redis dependência
dura do serviço novo.

Gaps identificados nesta revisão, **todos fechados ainda no mesmo dia**
(rodada seguinte, registrada logo abaixo): idempotency key no
`POST /emissao/documentos` (§3, item 10); tabela de guarda do CSC da NFC-e
(§4.2); rotação/versionamento da KEK do envelope encryption (§3, item 1;
§7.2); se o arquivo frio de 5 anos cobre XML de entrada além do emitido (§6);
e quem carrega CST/CFOP/percentuais até o XML (§3, item 11) — opção (b),
snapshot persistido no faturamento. Inconsistências de texto corrigidas:
seis referências a uma "etapa 9" inexistente (a tabela do §5 vai até a etapa
8) trocadas para "etapa 8"; a regra de produção da etapa 3 (autorização +
cancelamento + representação gráfica) propagada para as etapas 5 e 7, que
antes dependiam só da etapa 1; `prazo_cancelamento_horas` adicionada ao
desenho de `emissao.uf_autorizador` (§3, item 12), alinhando com
`regras-cancelamento-nfe.md`.

**Rodada seguinte (mesmo dia, 22/09/2026) — os 4 gaps acima fechados:**
- **Idempotency key**: header `Idempotency-Key` obrigatório, fornecido pelo
  chamador (não derivado de `pedidoId` — o serviço precisa funcionar pra
  qualquer chamador, §2); tabela `emissao.idempotency_key`, replay devolve a
  resposta já processada. Detalhe completo em §3, item 10.
- **CSC da NFC-e**: guarda dentro do próprio `emissao-fiscal-service`
  (`emissao.estabelecimento_csc`), não no `fiscal-service`/`cadastro-service`
  — consequência direta do requisito "vendável separadamente" (§2): CSC é
  credencial de emissão, não dado fiscal calculado. Sem urgência (NFC-e é
  etapa 6, uma das últimas). Detalhe completo em §4.2.
- **KEK**: coluna `kek_version` em toda tabela cifrada desde o changeset
  inicial; job de rotação em si fica fora de escopo por ora (operação rara).
  Detalhe completo em §3, item 1, e §7.2.
- **Arquivo frio cobre entrada também**: mesma retenção de 5 anos vale pro
  XML recebido (P2P), não só o emitido — obrigação legal não distingue os
  dois. Partição por `direcao` na chave do objeto. Detalhe completo em §6 e
  §7.3.

Decisões de arquitetura do §2 (serviço isolado) e §7.2 (envelope encryption)
reavaliadas e confirmadas sem alteração — nada no restante do projeto desde
14/09/2026 enfraqueceu nenhuma das duas; o `operacoes-service`, que seria a
alternativa descartada no §2, só cresceu (164 arquivos Java hoje), reforçando
que a decisão de isolar a emissão foi a certa.

**Terceira rodada (mesmo dia, 22/09/2026) — revisão independente (agente
Opus 5.5) e fechamento dos gaps que sobraram:**
- **PIS/COFINS e condicionais (ICMS-ST/IPI/FCP/DIFAL)**: guarda ativa em vez
  de nota de rodapé — campos opcionais no snapshot, `POST
  /emissao/documentos` rejeita com 400 se faltar num caso que exigiria
  algum deles, nunca inventa CST de isenção. `fiscal-service` não muda.
  Cálculo real fica no backlog, issue
  [#101](https://github.com/looperperp-main/lerp-system/issues/101).
  Detalhe completo em §3, item 9.
- **Máquina de estados incompleta**: `CANCELADO`/`INUTILIZADO` adicionados,
  `DENEGADO` uniformizado (nunca "DENEGADA"), `REJEITADO → RASCUNHO`
  reaproveita número (ao contrário de `DENEGADO`, que consome), `ERRO` virou
  estado real do job de reconciliação, e contingência exige reassinatura
  (muda `tpEmis`, logo muda a chave de acesso). Detalhe completo em §3,
  item 10.
- **Snapshot imutável sem caminho de correção**: `versao` +
  `motivo_correcao` no `pedido_item_fiscal_snapshot`, nunca `UPDATE`;
  correção manual permitida só em `RASCUNHO`/`REJEITADO` — depois de
  `AUTORIZADO` usa CC-e ou cancelamento, que já existiam. Detalhe completo
  em §3, item 11.
- **CI sem depender de homologação real**: fixture de resposta SEFAZ
  gravado uma vez (por resultado) + stub do `WebServiceClient`, roda em
  todo commit sem rede; homologação real continua como gate de etapa, não
  de build. Detalhe completo antes da tabela do §5.

Outros itens que a revisão do Opus 5.5 levantou (idempotency key, guarda do
CSC, observabilidade, outbox transacional do Kafka, entrada XML, estratégia
de teste, validação de CNPJ/XSD, CSRT cifrado) já estavam cobertos pelas
rodadas anteriores desta seção ou foram fechados junto com os quatro itens
acima — não sobrou gap identificado nesta revisão sem decisão registrada.

## 11. Plano de mudança — cadastro-service e fiscal-service (Etapa 0)

Detalhamento dos gaps identificados no §3 (item 11) e na Etapa 0 do §5.
**Atualizado em 22/09/2026: cadastro-service, percentuais e a seção
"fiscal-service — CST e CFOP" abaixo estão todos ✅ implementados e
commitados** (a primeira leva no commit `d313921`, 15/09/2026; CST/CFOP no
commit `20209a6`, 22/09/2026, confirmado verde — 123 testes) — mantidos aqui
como registro do que foi pedido/entregue.

### cadastro-service — back-end (✅ implementado, `d313921`)

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

### cadastro-service — front-end Angular (✅ implementado, `d313921`)

- `estabelecimento-form`: novo campo CRT (select, 4 opções).
- Form de endereço: o campo de município passa a resolver o `ibgeCodigo`
  automaticamente via lookup/autocomplete, em vez de digitação livre — evita
  o erro mais comum de rejeição de NF-e (`cMun` errado). Reaproveitar fonte
  de dado já existente no projeto se houver uma (ex. a carga de município do
  `fiscal.aliq_iss_municipio` já usa código IBGE).
- Form de pessoa: novo campo "Indicador de IE do destinatário" (select),
  visível quando a pessoa é usada como destinatário de operação fiscal.

### fiscal-service — percentuais/base separados (✅ implementado, `d313921`)

- `OperacaoFiscalDTO` ganha, por item: `cClassTrib` (eco do que veio no
  request), `percentualIbsUf`, `percentualIbsMunicipal`, `percentualCbs`,
  `percentualReducaoAplicado`; no legado: `percentualIcmsNominal`,
  `percentualReducaoBaseIcms`, `modalidadeBaseCalculoIcms` (`modBC`).
- `MotorFiscalService` já resolve `RegimeCClassTrib`/`AliquotaIbs`/
  `AliquotaCbs`/`RegimeIcms` internamente para calcular o valor final — a
  mudança foi **parar de descartar** esses valores intermediários e devolvê-
  los no DTO, sem recalcular nada novo.
- Sem mudança de schema — `fiscal-service` continua sem persistência, é só
  extensão de DTO + service.
- `MotorFiscalServiceTest` ganhou asserts nos campos novos sobre o oráculo já
  existente (§1.4.8 do `Fin.md`), sem caso de teste novo do zero.

### fiscal-service — CST e CFOP (✅ implementado e commitado, `20209a6`, 22/09/2026)

- **CST/CSOSN**: nova tabela `fiscal.cst_icms_regra` (changeset
  `fiscal-schema-018.yaml`, `fiscal-053`/`054`), chaveada por
  `regime_tributario` (`NORMAL`/`SIMPLES`, derivado de `regimeEmpresa`) ×
  `situacao` (`INTEGRAL`/`REDUZIDA`/`ISENTA`, derivada de
  `RegimeDiferenciado`). `TabelaFiscal.resolverCstIcms(...)` resolve; o
  `MotorFiscalService` popula `cstIcms`/`csosn` (só produto, ICMS não existe
  em serviço) ANTES dos retornos antecipados de alíquota-zero/monofásico —
  ISENTA (CST 40) é exatamente o caso mais comum que passava por `zerado()`.
  `cst` (IBS/CBS, Anexo NT 2023.001) continua sem fonte — classificação
  distinta, fora de escopo desta revisão.
  **Risco aceito e registrado** (mesmo padrão do placeholder de IS/cigarro):
  CSOSN sempre resolve `102` (sem permissão de crédito) — o cadastro não
  modela se o contribuinte do Simples aproveita crédito (101 x 102). Rever
  antes de emitir NF-e real para tenant que precise de 101. ST/pauta/DIFAL
  e monofásico legado seguem fora de escopo (mesma decisão de
  `motor-fiscal-proximos-passos.md` linha 163) — sem linha cadastrada,
  `cstIcms`/`csosn` saem `null`, nunca um código chutado.
- **CFOP**: nova tabela `fiscal.cfop_regra` (`fiscal-055`–`058`), chaveada
  por `natureza_operacao` × `ambito` (`INTERNO`/`INTERESTADUAL`, derivado de
  ufOrigem × ufDestino) × `tipo_operacao` (só `SAIDA` — CFOP de entrada
  continua vindo pronto do chamador). `MotorFiscalRequest.cfop` deixou de
  ser `@NotBlank`: quando ausente, `naturezaOperacao` + `ufOrigem`/
  `ufDestino` disparam a resolução no `MotorFiscalService` (PASSO 0), com
  validação cross-field (`@AssertTrue`) garantindo que pelo menos um dos
  dois venha preenchido. Seed inicial (fiscal-056) tinha só `VENDA`;
  complementado (fiscal-057, a partir de `spec/tabela_cfop.pdf`, tabela
  oficial CFOP) com `DEVOLUCAO_COMPRA` (5202/6202), `TRANSFERENCIA`
  (5152/6152) e `REMESSA_BONIFICACAO`/`REMESSA_AMOSTRA` (5910/6910,
  5911/6911) — **sem consumidor Java ainda** (só `VENDA` é chamada hoje, via
  `FiscalServiceClient`; as demais ficam prontas para quando P2P/Estoque
  precisarem). Âmbito EXTERIOR fica de fora em todas — sem sinal de país no
  request, seria chute.
- **Consumo**: `FiscalServiceClient.java` (operacoes-service) corrigido —
  mercadoria manda `naturezaOperacao=VENDA` + `cfop=null` em vez do
  `'5102'` fixo (`Constants.PEDIDO_FISCAL_CFOP_MERCADORIA_DEFAULT`, removida
  por ficar morta), que saía errado em toda venda interestadual. Serviço
  mantém `cfop` fixo (`5933`) — NFS-e não tem CFOP no XML, o valor é só
  sinal interno de SAÍDA pro motor. **Corrigido nesta revisão:** o texto
  original dizia que isso ficava em aberto — já está decidido (§3, item 11,
  opção b): o `@JsonIgnoreProperties(ignoreUnknown = true)` do
  `OperacaoFiscalResultado` local **vai parar de descartar** CST/CFOP
  resolvidos, é parte do plano do snapshot fiscal (ainda não implementado,
  só decidido).
- **Testes**: `TabelaFiscalJdbcTest` (SQL real, H2) e
  `MotorFiscalServiceTest` (oráculo com `TabelaFiscalFake`) ganharam casos
  novos para os dois métodos de resolução, incluindo os 3 erros de validação
  do CFOP (natureza ausente, UF ausente, natureza sem regra cadastrada).
