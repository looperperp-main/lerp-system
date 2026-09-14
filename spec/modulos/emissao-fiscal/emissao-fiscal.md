# Emissão Fiscal — NF-e, CT-e, NFS-e, NFCom, NF3e (plano)

> Última atualização: 13 de setembro de 2026

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

## 3. Infra comum (o que todo tipo de documento exige)

1. **Certificado digital por tenant** — upload de A1 (`.pfx` + senha); A3
   (token/HSM) fica de fora do MVP. Guarda da chave nunca em disco puro —
   decisão de segurança que precisa de infra, resolvida com **Vault OSS
   self-hosted** (§7), não com serviço proprietário de nuvem.
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

## 4. Por tipo de documento

### 4.1 NF-e (modelo 55) — SP, MG, RJ, DF, SC

- Layout 4.00 (schema XSD nacional / manual de integração do contribuinte).
- Autorizador varia por UF — **conferir contra o manual vigente antes de
  codar** (o dado abaixo é conhecimento geral, não fonte oficial consultada
  nesta sessão):
  - SP, MG: historicamente autorizador **próprio** (SEFAZ-SP, SEFAZ-MG).
  - RJ, DF, SC: historicamente atendidos pela **SVRS** (SEFAZ Virtual do Rio
    Grande do Sul).
  - Ambiente homologação × produção tem URL própria por UF — isso é
    **configuração** (uma linha por UF/ambiente), nunca hardcode no código.
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
- Autorizador consolidado por poucos ambientes (a maioria das UFs cai na SVRS;
  poucas mantêm ambiente próprio) — **conferir contra o manual vigente**, mesma
  ressalva do item anterior.
- MVP cobre só CT-e normal; complementar/substituto/anulação ficam para depois
  — YAGNI até haver caso real.
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
vez de uma por prefeitura. Município não aderente ao ADN fica fora do MVP —
levantar a lista de cobertura real antes de prometer "emite em qualquer
cidade".

- Depende de: item LC 116 + `cClassTrib` (✅ já resolvidos no motor fiscal),
  ISS por município (`fiscal.aliq_iss_municipio`, já existe desde a fatia 3d).

### 4.5 NFCom ("NF-telecom", modelo 62)

- Layout próprio (1.00), **autorizador único nacional** (SVRS) — mais simples
  que NF-e nesse aspecto, sem variação por UF.
- Nicho: só relevante para tenant do setor de telecomunicações. **Ativar por
  feature flag/vertical**, não construir para todo tenant (mesmo raciocínio
  do split payment: interruptor, não comportamento sempre ligado).

### 4.6 NF3e (energia elétrica, modelo 66)

- Layout próprio (1.00), autorizador único nacional — mesmo tratamento do
  NFCom.
- Nicho ainda mais restrito (concessionária/geradora de energia). Mesma
  recomendação: feature flag, implementar só se/quando houver tenant real
  desse setor.

## 5. Ordem de implementação

Decidida em 13 de setembro de 2026 — sequência, não lista solta de opções:

| Etapa | Entrega | Depende de |
|---|---|---|
| 1 | Infra comum (§3): certificado por tenant, assinatura XML, cliente SOAP genérico, numeração/série, persistência do documento — **sem emitir nada ainda** | — |
| 2 | NF-e em homologação, 1 UF (**SP** — autorizador próprio, cobre o caso mais difícil primeiro) | 1 |
| 3 | NF-e produção SP + expansão MG/RJ/DF/SC (cada UF nova é configuração de endpoint, não código novo) | 2 |
| 4 | Cancelamento + CC-e + inutilização de numeração — NF-e (obrigatório em toda UF) | 3 |
| 5 | **NFC-e** (§4.2): mesmo autorizador da NF-e, série própria, QR Code/CSC, e a contingência offline básica que a NFC-e exige desde o início (não espera a etapa 9) | 3 |
| 6 | CT-e (reaproveita quase toda a infra da etapa 1) | 1 |
| 7 | NFS-e padrão nacional (ADN) | 1 + item LC 116/ISS já prontos |
| 8 | NFCom / NF3e — só se houver tenant do setor | 1 |
| 9 | DANFE/DACTE/DANFCE/DANFS-e (representação gráfica completa) + contingência SVC geral (NF-e/CT-e) | 2-7 |

Ordem por UF nas etapas 2/3: SP primeiro porque autorizador próprio é o caso
mais exigente (endpoint e regras específicas) — se funcionar para SP,
MG/RJ/DF/SC tendem a ser configuração, não código novo. Isso é hipótese de
arquitetura, a confirmar quando o manual de cada UF for revisado.

A NFC-e entra logo depois da NF-e (etapa 5, não junto do CT-e/NFS-e) porque
reaproveita quase toda a infra e o autorizador já validados nas etapas 2-4 —
o que ela acrescenta é local (série, QR Code, contingência antecipada), não
uma integração nova do zero.

## 6. Riscos e decisões em aberto

- **Confirmar autorizador por UF** (próprio × SVRS × SVAN) contra o manual de
  integração vigente antes de codar — o que está no §4.1/4.2 é conhecimento
  geral, não fonte oficial consultada nesta sessão.
- **Cobertura real do NFS-e nacional (ADN)** — nem todo município aderiu;
  mapear antes de prometer emissão universal.
- **CSC (Código de Segurança do Contribuinte) da NFC-e é cadastro próprio por
  estabelecimento**, obtido no ambiente da SEFAZ de cada UF — não é gerado
  nem derivado do certificado digital. Levantar o processo de obtenção por UF
  antes da etapa 5.
- Este módulo **não** reabre a decisão de que apuração (item 5 do doc do motor
  fiscal) é independente de emissão — as duas seguem desacopladas.

## 7. Infraestrutura e portabilidade — evitar lock-in, testar localmente

O projeto já está saindo do OCI (Oracle Cloud) para um VPS genérico, ainda não
definido (`project_github_project_reorg_vps_hostgator`, GitHub Project —
"HostGator" cotado, não fechado). Este serviço **não pode reintroduzir** um
vínculo de nuvem por conta própria, e precisa rodar localmente para
desenvolvimento/teste sem depender de nenhum provedor.

1. **Empacotamento é igual aos outros sete módulos.** Dockerfile próprio,
   imagem publicada no Docker Hub via Jenkins
   (`vitorff1234/emissao-fiscal-service:<BUILD_NUMBER>`/`:latest`), registro no
   Eureka, rota no gateway. O único vínculo com Oracle hoje é a **VM de
   produção** (OCI Ampere ARM64) — fora do escopo deste doc e já em processo
   de troca. Nada neste serviço depende disso.

2. **O ponto que travaria em nuvem se não for pensado agora: guarda do
   certificado digital (§3, item 1).** Nunca usar um serviço proprietário
   (OCI Vault/KMS, AWS KMS, etc.) — isso prenderia o serviço ao provedor.
   **Decisão: HashiCorp Vault OSS, self-hosted em container**, no mesmo
   `compose.yaml` onde já rodam Postgres/Kafka/Redis. Vault OSS sobe em
   qualquer VPS ou máquina local — é só mais um serviço no
   `docker compose up -d`, sem custo e sem vínculo de nuvem.

3. **Armazenamento do XML assinado e do protocolo (§3, item 7).** Evitar
   serviço de objeto proprietário (ex. OCI Object Storage). Começar em
   **Postgres** (schema `emissao.*`, mesmo padrão dos outros serviços) — XML
   assinado não é grande, cabe em coluna `text`/`bytea`. Se algum dia o volume
   justificar storage de objeto, a opção portável é **MinIO** (self-hosted,
   API compatível S3), não um serviço de nuvem específico. Não introduzir
   MinIO agora — YAGNI até haver volume real.

4. **Perfil local (`dev`), sem nenhuma nuvem envolvida:**
   - `emissao-fiscal-service` entra no `docker compose up -d` local dos
     demais, porta sugerida **8094** (próxima livre depois do `fiscal-service`
     em 8093).
   - Vault local em modo dev (`vault server -dev`, imagem oficial) — sem
     unseal/HA, que só é necessário em produção.
   - Endpoints SEFAZ chamados são os de **homologação** por padrão no profile
     `dev` (`application-dev.yml`), mesmo padrão de `SPRING_PROFILES_ACTIVE=dev`
     já usado nos outros serviços — homologação é ambiente público e gratuito
     de cada UF, não exige infra paga.
   - Certificado de teste: A1 de homologação (algumas UFs aceitam certificado
     de teste próprio; onde não aceitam, um A1 real de baixo custo resolve —
     não é dependência de nuvem, é um custo pontual de certificação).
   - Todos os componentes desse perfil (Postgres, Kafka, Redis, Vault) já são
     containers padrão — rodam no Docker Desktop/WSL local do Windows sem
     nenhuma conta de nuvem.

5. **CI/CD sem mudança de natureza.** Entra no mesmo `Jenkinsfile` reator
   (`-pl emissao-fiscal-service -am`), mesmo `jacoco-maven-plugin` com
   `coverage.minimum` inicial baixo (padrão de `gateway`/`registry`, 0.20).
   Jenkins já é self-hosted — nenhuma dependência nova de nuvem aqui.

**Resumo:** o único ponto deste serviço que poderia prender a nuvem era a
guarda do certificado, resolvido com Vault OSS. Com isso, `emissao-fiscal-service`
roda igual em OCI, no VPS que for escolhido, ou local — sem exigir Oracle em
nenhum ponto.

## 8. O que este doc não decide

Não decide se emissão deve entrar antes de AR/O2C, P2P ou da matriz de
transição no roadmap geral — isso é chamada de priorização de produto, não de
arquitetura de emissão. Ver `spec/fiscal/motor-fiscal-proximos-passos.md`
("Próximos passos gerais") para o estado do resto do roadmap. Também não
decide qual VPS substitui o OCI — isso é rastreado à parte no GitHub Project
(`project_github_project_reorg_vps_hostgator`).
