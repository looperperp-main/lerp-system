# Referência — Webservices NF-e por autorizador (nacional)

> Última atualização: 25 de setembro de 2026

Dado bruto, colado pelo usuário em 14/09/2026 a partir do Portal Nacional da
NF-e. Não é plano — é **fonte de dado** para o changeset Liquibase que vai
carregar `emissao.webservice_endpoint` (ver `emissao-fiscal.md` §3, item 12).
Mesmo padrão de `spec/fiscal/fontes-dados-fiscais.md`: a spec não é a fonte de
verdade em runtime, a tabela é — isto aqui é só o registro de onde os valores
vieram.

**Confirmar contra o manual vigente antes de gerar o changeset** — URLs de
webservice mudam de versão ao longo do tempo, e este snapshot tem data.

## Mapa UF → autorizador → contingência (NF-e)

| Autorizador primário | UFs |
|---|---|
| Próprio (webservice na própria UF) | AM, BA, GO, MG, MS, MT, PE, PR, RS, SP |
| SVAN (Sefaz Virtual Ambiente Nacional) | MA |
| SVRS (Sefaz Virtual RS) — demais serviços | AC, AL, AP, CE, DF, ES, PA, PB, PI, RJ, RN, RO, RR, SC, SE, TO |
| SVRS — só Consulta Cadastro (subconjunto acima) | AC, ES, RN, PB, SC |

| Autorizador de contingência | UFs cobertas |
|---|---|
| SVC-AN (Sefaz Virtual de Contingência Ambiente Nacional) | AC, AL, AP, CE, DF, ES, **MG**, PA, PB, PI, RJ, RN, RO, RR, **RS**, SC, SE, **SP**, TO |
| SVC-RS (Sefaz Virtual de Contingência RS) | AM, BA, GO, MA, MS, MT, PE, PR |

Padrão: a contingência nunca é o mesmo ambiente do autorizador primário — os
16 estados SVRS + MG/RS/SP (que têm autorizador próprio) caem no SVC-AN; os
demais estados com autorizador próprio (mais MA) caem no SVC-RS.

**Para as 5 UFs hoje priorizadas em `emissao-fiscal.md` §1:**

| UF | Autorizador | Contingência |
|---|---|---|
| SP | próprio | SVC-AN |
| MG | próprio | SVC-AN |
| RJ | SVRS | SVC-AN |
| DF | SVRS | SVC-AN |
| SC | SVRS | SVC-AN |

Todas as 5 caem em **SVC-AN** — não há necessidade de implementar SVC-RS para
cobrir o escopo inicial.

## URLs por autorizador (NF-e, layout 4.00)

### AM — Sefaz Amazonas
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.sefaz.am.gov.br/services2/services/NfeInutilizacao4` |
| NfeConsultaProtocolo | `https://nfe.sefaz.am.gov.br/services2/services/NfeConsulta4` |
| NfeStatusServico | `https://nfe.sefaz.am.gov.br/services2/services/NfeStatusServico4` |
| NfeConsultaCadastro | `https://nfe.sefaz.am.gov.br/services2/services/CadConsultaCadastro4` |
| RecepcaoEvento | `https://nfe.sefaz.am.gov.br/services2/services/RecepcaoEvento4` |
| NFeAutorizacao | `https://nfe.sefaz.am.gov.br/services2/services/NfeAutorizacao4` |
| NFeRetAutorizacao | `https://nfe.sefaz.am.gov.br/services2/services/NfeRetAutorizacao4` |

### BA — Sefaz Bahia
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.sefaz.ba.gov.br/webservices/NFeInutilizacao4/NFeInutilizacao4.asmx` |
| NfeConsultaProtocolo | `https://nfe.sefaz.ba.gov.br/webservices/NFeConsultaProtocolo4/NFeConsultaProtocolo4.asmx` |
| NfeStatusServico | `https://nfe.sefaz.ba.gov.br/webservices/NFeStatusServico4/NFeStatusServico4.asmx` |
| NfeConsultaCadastro | `https://nfe.sefaz.ba.gov.br/webservices/CadConsultaCadastro4/CadConsultaCadastro4.asmx` |
| RecepcaoEvento | `https://nfe.sefaz.ba.gov.br/webservices/NFeRecepcaoEvento4/NFeRecepcaoEvento4.asmx` |
| NFeAutorizacao | `https://nfe.sefaz.ba.gov.br/webservices/NFeAutorizacao4/NFeAutorizacao4.asmx` |
| NFeRetAutorizacao | `https://nfe.sefaz.ba.gov.br/webservices/NFeRetAutorizacao4/NFeRetAutorizacao4.asmx` |

### GO — Sefaz Goiás
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.sefaz.go.gov.br/nfe/services/NFeInutilizacao4?wsdl` |
| NfeConsultaProtocolo | `https://nfe.sefaz.go.gov.br/nfe/services/NFeConsultaProtocolo4?wsdl` |
| NfeStatusServico | `https://nfe.sefaz.go.gov.br/nfe/services/NFeStatusServico4?wsdl` |
| NfeConsultaCadastro | `https://nfe.sefaz.go.gov.br/nfe/services/CadConsultaCadastro4?wsdl` |
| RecepcaoEvento | `https://nfe.sefaz.go.gov.br/nfe/services/NFeRecepcaoEvento4?wsdl` |
| NFeAutorizacao | `https://nfe.sefaz.go.gov.br/nfe/services/NFeAutorizacao4?wsdl` |
| NFeRetAutorizacao | `https://nfe.sefaz.go.gov.br/nfe/services/NFeRetAutorizacao4?wsdl` |

### MG — Sefaz Minas Gerais
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.fazenda.mg.gov.br/nfe2/services/NFeInutilizacao4` |
| NfeConsultaProtocolo | `https://nfe.fazenda.mg.gov.br/nfe2/services/NFeConsultaProtocolo4` |
| NfeStatusServico | `https://nfe.fazenda.mg.gov.br/nfe2/services/NFeStatusServico4` |
| NfeConsultaCadastro | `https://nfe.fazenda.mg.gov.br/nfe2/services/CadConsultaCadastro4` |
| RecepcaoEvento | `https://nfe.fazenda.mg.gov.br/nfe2/services/NFeRecepcaoEvento4` |
| NFeAutorizacao | `https://nfe.fazenda.mg.gov.br/nfe2/services/NFeAutorizacao4` |
| NFeRetAutorizacao | `https://nfe.fazenda.mg.gov.br/nfe2/services/NFeRetAutorizacao4` |

### MS — Sefaz Mato Grosso do Sul
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.sefaz.ms.gov.br/ws/NFeInutilizacao4` |
| NfeConsultaProtocolo | `https://nfe.sefaz.ms.gov.br/ws/NFeConsultaProtocolo4` |
| NfeStatusServico | `https://nfe.sefaz.ms.gov.br/ws/NFeStatusServico4` |
| NfeConsultaCadastro | `https://nfe.sefaz.ms.gov.br/ws/CadConsultaCadastro4` |
| RecepcaoEvento | `https://nfe.sefaz.ms.gov.br/ws/NFeRecepcaoEvento4` |
| NFeAutorizacao | `https://nfe.sefaz.ms.gov.br/ws/NFeAutorizacao4` |
| NFeRetAutorizacao | `https://nfe.sefaz.ms.gov.br/ws/NFeRetAutorizacao4` |

### MT — Sefaz Mato Grosso
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.sefaz.mt.gov.br/nfews/v2/services/NfeInutilizacao4?wsdl` |
| NfeConsultaProtocolo | `https://nfe.sefaz.mt.gov.br/nfews/v2/services/NfeConsulta4?wsdl` |
| NfeStatusServico | `https://nfe.sefaz.mt.gov.br/nfews/v2/services/NfeStatusServico4?wsdl` |
| NfeConsultaCadastro | `https://nfe.sefaz.mt.gov.br/nfews/v2/services/CadConsultaCadastro4?wsdl` |
| RecepcaoEvento | `https://nfe.sefaz.mt.gov.br/nfews/v2/services/RecepcaoEvento4?wsdl` |
| NFeAutorizacao | `https://nfe.sefaz.mt.gov.br/nfews/v2/services/NfeAutorizacao4?wsdl` |
| NFeRetAutorizacao | `https://nfe.sefaz.mt.gov.br/nfews/v2/services/NfeRetAutorizacao4?wsdl` |

### PE — Sefaz Pernambuco
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.sefaz.pe.gov.br/nfe-service/services/NFeInutilizacao4` |
| NfeConsultaProtocolo | `https://nfe.sefaz.pe.gov.br/nfe-service/services/NFeConsultaProtocolo4` |
| NfeStatusServico | `https://nfe.sefaz.pe.gov.br/nfe-service/services/NFeStatusServico4` |
| NfeConsultaCadastro | `https://nfe.sefaz.pe.gov.br/nfe-service/services/CadConsultaCadastro4?wsdl` |
| RecepcaoEvento | `https://nfe.sefaz.pe.gov.br/nfe-service/services/NFeRecepcaoEvento4` |
| NFeAutorizacao | `https://nfe.sefaz.pe.gov.br/nfe-service/services/NFeAutorizacao4` |
| NFeRetAutorizacao | `https://nfe.sefaz.pe.gov.br/nfe-service/services/NFeRetAutorizacao4` |

### PR — Sefaz Paraná
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.sefa.pr.gov.br/nfe/NFeInutilizacao4?wsdl` |
| NfeConsultaProtocolo | `https://nfe.sefa.pr.gov.br/nfe/NFeConsultaProtocolo4?wsdl` |
| NfeStatusServico | `https://nfe.sefa.pr.gov.br/nfe/NFeStatusServico4?wsdl` |
| NfeConsultaCadastro | `https://nfe.sefa.pr.gov.br/nfe/CadConsultaCadastro4?wsdl` |
| RecepcaoEvento | `https://nfe.sefa.pr.gov.br/nfe/NFeRecepcaoEvento4?wsdl` |
| NFeAutorizacao | `https://nfe.sefa.pr.gov.br/nfe/NFeAutorizacao4?wsdl` |
| NFeRetAutorizacao | `https://nfe.sefa.pr.gov.br/nfe/NFeRetAutorizacao4?wsdl` |

### RS — Sefaz Rio Grande do Sul
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.sefazrs.rs.gov.br/ws/nfeinutilizacao/nfeinutilizacao4.asmx` |
| NfeConsultaProtocolo | `https://nfe.sefazrs.rs.gov.br/ws/NfeConsulta/NfeConsulta4.asmx` |
| NfeStatusServico | `https://nfe.sefazrs.rs.gov.br/ws/NfeStatusServico/NfeStatusServico4.asmx` |
| NfeConsultaCadastro | `https://cad.svrs.rs.gov.br/ws/cadconsultacadastro/cadconsultacadastro4.asmx` |
| RecepcaoEvento | `https://nfe.sefazrs.rs.gov.br/ws/recepcaoevento/recepcaoevento4.asmx` |
| NFeAutorizacao | `https://nfe.sefazrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx` |
| NFeRetAutorizacao | `https://nfe.sefazrs.rs.gov.br/ws/NfeRetAutorizacao/NFeRetAutorizacao4.asmx` |

### SP — Sefaz São Paulo
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.fazenda.sp.gov.br/ws/nfeinutilizacao4.asmx` |
| NfeConsultaProtocolo | `https://nfe.fazenda.sp.gov.br/ws/nfeconsultaprotocolo4.asmx` |
| NfeStatusServico | `https://nfe.fazenda.sp.gov.br/ws/nfestatusservico4.asmx` |
| NfeConsultaCadastro | `https://nfe.fazenda.sp.gov.br/ws/cadconsultacadastro4.asmx` |
| RecepcaoEvento | `https://nfe.fazenda.sp.gov.br/ws/nferecepcaoevento4.asmx` |
| NFeAutorizacao | `https://nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx` |
| NFeRetAutorizacao | `https://nfe.fazenda.sp.gov.br/ws/nferetautorizacao4.asmx` |

### SVAN — Sefaz Virtual Ambiente Nacional (autoriza MA)
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://www.sefazvirtual.fazenda.gov.br/NFeInutilizacao4/NFeInutilizacao4.asmx` |
| NfeConsultaProtocolo | `https://www.sefazvirtual.fazenda.gov.br/NFeConsultaProtocolo4/NFeConsultaProtocolo4.asmx` |
| NfeStatusServico | `https://www.sefazvirtual.fazenda.gov.br/NFeStatusServico4/NFeStatusServico4.asmx` |
| RecepcaoEvento | `https://www.sefazvirtual.fazenda.gov.br/NFeRecepcaoEvento4/NFeRecepcaoEvento4.asmx` |
| NFeAutorizacao | `https://www.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx` |
| NFeRetAutorizacao | `https://www.sefazvirtual.fazenda.gov.br/NFeRetAutorizacao4/NFeRetAutorizacao4.asmx` |

### SVRS — Sefaz Virtual Rio Grande do Sul (autoriza os 16 estados do mapa acima)
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe.svrs.rs.gov.br/ws/nfeinutilizacao/nfeinutilizacao4.asmx` |
| NfeConsultaProtocolo | `https://nfe.svrs.rs.gov.br/ws/NfeConsulta/NfeConsulta4.asmx` |
| NfeStatusServico | `https://nfe.svrs.rs.gov.br/ws/NfeStatusServico/NfeStatusServico4.asmx` |
| NfeConsultaCadastro | `https://cad.svrs.rs.gov.br/ws/cadconsultacadastro/cadconsultacadastro4.asmx` |
| RecepcaoEvento | `https://nfe.svrs.rs.gov.br/ws/recepcaoevento/recepcaoevento4.asmx` |
| NFeAutorizacao | `https://nfe.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx` |
| NFeRetAutorizacao | `https://nfe.svrs.rs.gov.br/ws/NfeRetAutorizacao/NFeRetAutorizacao4.asmx` |

### SVC-AN — contingência (cobre as 5 UFs priorizadas neste doc)
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://www.sefazvirtual.fazenda.gov.br/NFeInutilizacao4/NFeInutilizacao4.asmx` |
| NfeConsultaProtocolo | `https://www.sefazvirtual.fazenda.gov.br/NFeConsultaProtocolo4/NFeConsultaProtocolo4.asmx` |
| NfeStatusServico | `https://www.sefazvirtual.fazenda.gov.br/NFeStatusServico4/NFeStatusServico4.asmx` |
| RecepcaoEvento | `https://www.sefazvirtual.fazenda.gov.br/NFeRecepcaoEvento4/NFeRecepcaoEvento4.asmx` |
| NFeAutorizacao | `https://www.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx` |
| NFeRetAutorizacao | `https://www.sefazvirtual.fazenda.gov.br/NFeRetAutorizacao4/NFeRetAutorizacao4.asmx` |

### SVC-RS — contingência (não necessária para as 5 UFs priorizadas)
| Serviço | URL |
|---|---|
| NfeConsultaProtocolo | `https://nfe.svrs.rs.gov.br/ws/NfeConsulta/NfeConsulta4.asmx` |
| NfeStatusServico | `https://nfe.svrs.rs.gov.br/ws/NfeStatusServico/NfeStatusServico4.asmx` |
| RecepcaoEvento | `https://nfe.svrs.rs.gov.br/ws/recepcaoevento/recepcaoevento4.asmx` |
| NFeAutorizacao | `https://nfe.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx` |
| NFeRetAutorizacao | `https://nfe.svrs.rs.gov.br/ws/NfeRetAutorizacao/NFeRetAutorizacao4.asmx` |

### AN — Ambiente Nacional (serviços que não são por autorizador)
| Serviço | URL |
|---|---|
| NFeDistribuicaoDFe (1.00) | `https://www1.nfe.fazenda.gov.br/NFeDistribuicaoDFe/NFeDistribuicaoDFe.asmx` |
| RecepcaoEvento (4.00) | `https://www.nfe.fazenda.gov.br/NFeRecepcaoEvento4/NFeRecepcaoEvento4.asmx` |

`NFeDistribuicaoDFe` é o serviço de manifestação do destinatário/DF-e — não
entra nesta fase (ver `emissao-fiscal.md` §6: fica para uma Fase 2, desenho
já existente do usuário, fora deste doc). Registrado aqui porque a URL já
está levantada.

## URLs de homologação (colado pelo usuário em 25/09/2026)

Só os 4 autorizadores hoje semeados em `emissao.webservice_endpoint`
(`emissao-schema-006.yaml`, changesets `emissao-017` a `emissao-020`) — os
mesmos das 5 UFs priorizadas. URLs de homologação dos demais autorizadores
(AM, BA, GO, MS, MT, PE, PR, RS direto, SVAN, SVC-RS, AN) também foram
recebidas nesta data mas não entraram no changeset: nenhuma UF priorizada
usa esses autorizadores hoje — ver `webservices-nfe-referencia.md` §"Mapa
UF → autorizador" acima. Ficam de fora até alguma dessas UFs entrar no
escopo.

### SVRS — homologação
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://nfe-homologacao.svrs.rs.gov.br/ws/nfeinutilizacao/nfeinutilizacao4.asmx` |
| NfeConsultaProtocolo | `https://nfe-homologacao.svrs.rs.gov.br/ws/NfeConsulta/NfeConsulta4.asmx` |
| NfeStatusServico | `https://nfe-homologacao.svrs.rs.gov.br/ws/NfeStatusServico/NfeStatusServico4.asmx` |
| RecepcaoEvento | `https://nfe-homologacao.svrs.rs.gov.br/ws/recepcaoevento/recepcaoevento4.asmx` |
| NFeAutorizacao | `https://nfe-homologacao.svrs.rs.gov.br/ws/NfeAutorizacao/NFeAutorizacao4.asmx` |
| NFeRetAutorizacao | `https://nfe-homologacao.svrs.rs.gov.br/ws/NfeRetAutorizacao/NFeRetAutorizacao4.asmx` |

### MG — homologação
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://hnfe.fazenda.mg.gov.br/nfe2/services/NFeInutilizacao4` |
| NfeConsultaProtocolo | `https://hnfe.fazenda.mg.gov.br/nfe2/services/NFeConsultaProtocolo4` |
| NfeStatusServico | `https://hnfe.fazenda.mg.gov.br/nfe2/services/NFeStatusServico4` |
| RecepcaoEvento | `https://hnfe.fazenda.mg.gov.br/nfe2/services/NFeRecepcaoEvento4` |
| NFeAutorizacao | `https://hnfe.fazenda.mg.gov.br/nfe2/services/NFeAutorizacao4` |
| NFeRetAutorizacao | `https://hnfe.fazenda.mg.gov.br/nfe2/services/NFeRetAutorizacao4` |

### SP — homologação
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://homologacao.nfe.fazenda.sp.gov.br/ws/nfeinutilizacao4.asmx` |
| NfeConsultaProtocolo | `https://homologacao.nfe.fazenda.sp.gov.br/ws/nfeconsultaprotocolo4.asmx` |
| NfeStatusServico | `https://homologacao.nfe.fazenda.sp.gov.br/ws/nfestatusservico4.asmx` |
| RecepcaoEvento | `https://homologacao.nfe.fazenda.sp.gov.br/ws/nferecepcaoevento4.asmx` |
| NFeAutorizacao | `https://homologacao.nfe.fazenda.sp.gov.br/ws/nfeautorizacao4.asmx` |
| NFeRetAutorizacao | `https://homologacao.nfe.fazenda.sp.gov.br/ws/nferetautorizacao4.asmx` |

### SVC-AN — homologação
| Serviço | URL |
|---|---|
| NfeInutilizacao | `https://hom.sefazvirtual.fazenda.gov.br/NFeInutilizacao4/NFeInutilizacao4.asmx` |
| NfeConsultaProtocolo | `https://hom.sefazvirtual.fazenda.gov.br/NFeConsultaProtocolo4/NFeConsultaProtocolo4.asmx` |
| NfeStatusServico | `https://hom.sefazvirtual.fazenda.gov.br/NFeStatusServico4/NFeStatusServico4.asmx` |
| RecepcaoEvento | `https://hom.sefazvirtual.fazenda.gov.br/NFeRecepcaoEvento4/NFeRecepcaoEvento4.asmx` |
| NFeAutorizacao | `https://hom.sefazvirtual.fazenda.gov.br/NFeAutorizacao4/NFeAutorizacao4.asmx` |
| NFeRetAutorizacao | `https://hom.sefazvirtual.fazenda.gov.br/NFeRetAutorizacao4/NFeRetAutorizacao4.asmx` |

## Pendências

- ~~Ambiente de **homologação** tem URLs próprias~~ — ✅ **resolvido em
  25/09/2026** para os 4 autorizadores das 5 UFs priorizadas (seção acima +
  `emissao-schema-006.yaml` changesets `emissao-017` a `emissao-020`).
- **CT-e**: SVRS/SVSP já cobertos em `emissao-fiscal.md` §4.3 com URLs de
  produção. URLs de **homologação** de CT-e (MT, MS, MG, PR, RS, SP, SVRS,
  SVSP, SVC-RS, SVC-SP, AN) também foram coladas pelo usuário em 25/09/2026,
  mas CT-e é etapa 7 no roadmap (§5 da spec principal) — sem
  `ServicoWebservice` nem `uf_autorizador` de CT-e no código hoje, carregar
  esse dado agora ficaria órfão. Fica registrado aqui para não perder o
  levantamento; carrega junto quando a etapa 7 começar.
- **NFC-e, NFCom, NF3e**: webservices ainda não levantados. Mesma tarefa
  desta página quando as etapas correspondentes (§5 da spec principal)
  chegarem — não travam o planejamento atual.
