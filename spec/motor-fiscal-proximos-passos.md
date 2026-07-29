# Motor Fiscal — próximos passos

> Última atualização: 29 de julho de 2026

Handoff das fatias seguintes do motor fiscal. Escrito para ser lido do zero, sem
contexto de conversa anterior.

## Onde o motor está hoje

`fiscal-service` (porta 8093) expõe `POST /fiscal/calcular`: determinístico, sem
persistência, **só saída**. Lê conteúdo fiscal real de `fiscal.*` via
`TabelaFiscalJdbc` (JdbcClient, read-only; schema e carga pertencem ao
`liquibase-service`, changesets `fiscal-001..020`).

O que já existe:

| tabela | conteúdo | quem usa |
|---|---|---|
| `fiscal.ncm` | 15.156 NCM | catálogo |
| `fiscal.cfop` | 629 CFOP | motor (tipo de operação, 1ª etapa da cadeia) |
| `fiscal.regime_dif_ncm` | 242 regimes da LC 214, **só produto** | motor |
| `fiscal.aliq_ibs_municipio` / `aliq_cbs_regime` / `aliq_is_ncm` | alíquotas | motor |
| `fiscal.servico_nbs` | 895 linhas, 675 NBS (Anexo VIII) | catálogo, picklist do cadastro |
| `fiscal.servico_cclasstrib` | 246 pares item LC 116 × cClassTrib | motor (validação) |
| `fiscal.regime_cclasstrib` | **7 de 27** cClassTrib com redução | motor |

Serviço é classificado pelo `cClassTrib` **declarado** na nota (não deduzido do
código LC 116 — o mesmo serviço muda de classificação conforme o contexto).
Sem `cClassTrib`, ou com par item×cClassTrib não admitido, o motor devolve 400.

Testes: `MotorFiscalServiceTest` (aritmética, oráculo do Fin.md §1.4.8, contra
`TabelaFiscalFake`) e `TabelaFiscalJdbcTest` (SQL, H2 em modo PostgreSQL).

---

## 2. Os 20 percentuais de redução que faltam

**Bloqueante para emitir NFS-e nesses setores. Hoje eles tributam cheio, calado.**

`fiscal.regime_cclasstrib` só tem os 7 cClassTrib cujo percentual dava para
fundamentar pelo próprio nome no Anexo VIII (os que citam anexo II/III/IX/X/XI,
todos 60%, mais o `000001` = integral). Os outros 20 têm a redução fixada em
artigos da LC 214 que não constam do anexo — e escrever percentual de cabeça
dentro de motor fiscal é pior do que não ter o dado.

Fonte: `spec/leicomplementar-214-16-janeiro-2025-796905-normaatualizada-pl.pdf`
(está no repo; ler por faixas de página com o parâmetro `pages`).

Códigos pendentes e o setor de cada um:

| cClassTrib | setor |
|---|---|
| `200048` | hotelaria |
| `200046`, `200027` | bens imóveis |
| `200052` | profissões intelectuais |
| `200021`, `400001` | transporte público coletivo |
| `011002` | planos de assistência à saúde |
| `200025` | Prouni |
| `200016` | ICT (ciência e tecnologia) |
| `000002`, `010002`, `011001`, `011003`, `011005`, `200037`, `200040`, `200041`, `200042`, `200045`, `200051` | ver `spec/anexos-lc214-revisar.md` |

Entregável: um changeset novo (`fiscal-021`) com os INSERT fundamentados, cada
linha comentada com o artigo da LC 214 que a sustenta. Não editar o
`fiscal-019` — ele já rodou, e mexer em changeset aplicado quebra o checksum.

O que **não** dá para fundamentar sozinho fica de fora e continua em `PADRAO`,
com a pendência registrada em `spec/anexos-lc214-revisar.md`.

---

## 3. Cálculo dual da transição (2026–2032)

**O maior buraco. Sem isso o ERP não emite documento válido em 2027–2032.**

O motor calcula só o lado novo (IBS/CBS/IS). Mas na transição a nota carrega os
dois sistemas no mesmo item: ICMS e ISS em redução progressiva, PIS/COFINS até
2026. Calendário da LC 214, em resumo:

- **2026** — ano de teste: CBS 0,9% e IBS 0,1%, compensáveis com PIS/COFINS.
- **2027** — PIS/COFINS extintos, CBS cheia, IS entra em cena.
- **2029–2032** — ICMS e ISS reduzidos 10%/20%/30%/40% ao ano.
- **2033** — só IBS/CBS.

**Decisão de escopo que precisa vir do usuário antes de escrever código:** o ERP
vai *calcular* ICMS/ISS/PIS/COFINS (motor legado inteiro: ST, MVA, DIFAL, pauta,
alíquota interestadual, benefício por UF — projeto grande e que hoje não existe
em lugar nenhum do repositório), ou vai apenas *transportar* valores calculados
fora? As duas respostas levam a arquiteturas diferentes; não dá para default.

---

## 4. Crédito de entrada (Fin.md §1.4.3)

Hoje o motor só faz saída — `CfopInfo` já carrega `gera_credito_ibs` e
`gera_credito_cbs`, mas nada consome esses campos.

**Decisão que precisa vir do usuário:** crédito exige memória (o que entrou, o
que já foi aproveitado), e o `fiscal-service` é deliberadamente stateless
(MF-06). Ele ganha schema próprio e lado de escrita, ou o crédito é persistido
pelo `operacoes-service` e o fiscal segue só calculando?

Depende do item 3 na parte da transição (crédito de ICMS na entrada convive com
crédito de IBS no mesmo período).

---

## 5. Apuração mensal, depois emissão

Depende do item 4. Ordem: apuração (consolidar débito − crédito por período e
por estabelecimento) → geração do arquivo → emissão NF-e/NFC-e/NFS-e.

Emissão também depende de `spec/estabelecimentos-filiais.md`, que está planejado
e não iniciado — não há entidade de estabelecimento para figurar como emitente.

---

## 6. Documentação desatualizada

Rápido, sem dependência de nada acima:

- `spec/casos-teste-motor-fiscal.md` — descreve serviço classificado pelo código
  LC 116; hoje é por `cClassTrib` declarado.
- `spec/Fin.md` §1.4.10 — contrato do `MotorFiscalRequest` sem o campo
  `cClassTrib` e sem os 400 novos (`FISCAL_CCLASSTRIB_OBRIGATORIO`,
  `FISCAL_CCLASSTRIB_INVALIDO_PARA_SERVICO`).

---

## Ordem sugerida

6 (barato, fecha inconsistência) → 2 (desbloqueia NFS-e, é pesquisa em fonte que
já está no repo) → 3 (precisa da decisão de escopo) → 4 → 5.
