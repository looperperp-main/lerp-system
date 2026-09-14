# Referência — Prazo de cancelamento e servidor de contingência por UF (NF-e)

> Última atualização: 14 de setembro de 2026

Dado bruto, colado pelo usuário em 14/09/2026. Fonte de dado para
`emissao.uf_autorizador` (coluna `prazo_cancelamento_horas` — ver
`emissao-fiscal.md` §3, item 12) e para a máquina de estados (§3, item 10:
esconder o botão de cancelar fora do prazo, regra do `CLAUDE.md`). Mesmo
aviso de `webservices-nfe-referencia.md`: confirmar contra o manual/legislação
vigente antes de codar — prazo pode mudar por decreto estadual.

**Regra geral, válida em todas as UFs:** só cancela NF-e com Autorização de
Uso e sem circulação de mercadoria/prestação de serviço ainda ter ocorrido.
NF-e cancelada **nunca** pode ser inutilizada — cada número/série só existe
num destes estados, mutuamente exclusivos: autorizada, cancelada, denegada,
ou inutilizada.

| UF | Código IBGE | Prazo cancelamento | Contingência | Observação |
|---|---|---|---|---|
| AC | 12 | 24h | SVC-AN | — |
| AL | 27 | 24h | SVC-AN | — |
| AP | 16 | 24h | SVC-AN | — |
| AM | 13 | 24h | SVC-RS | — |
| BA | 29 | 24h | SVC-RS | — |
| CE | 23 | 24h | SVC-AN | confirmado pelo usuário 14/09/2026 — a divergência anterior era porque o CT-e (documento diferente) usa SVC-RS nesta UF, não o NF-e |
| DF | 53 | 24h | SVC-AN | prioridade deste doc |
| ES | 32 | 24h | SVC-AN | após o prazo, o instrumento é **NF-e de estorno**, não cancelamento extemporâneo — ver nota abaixo |
| GO | 52 | 24h | SVC-RS | — |
| MA | 21 | 24h | SVC-RS | autorizador primário é SVAN |
| MT | 51 | **8h** | SVC-RS | extemporâneo possível mediante taxa (TSE) |
| MS | 50 | 24h | SVC-RS | após prazo, só via pedido formal à SEFAZ |
| **MG** | 31 | 24h | SVC-AN | extemporâneo até 168h convalidado; depois disso, autorizado mas com multa — **prioridade deste doc** |
| PA | 15 | 24h | SVC-AN | confirmado pelo usuário 14/09/2026 — mesmo caso de CE: CT-e usa SVC-RS nesta UF, NF-e usa SVC-AN |
| PB | 25 | 24h | SVC-AN | — |
| PR | 41 | **168h** | SVC-RS | — |
| PE | 26 | 24h | SVC-RS | — |
| PI | 22 | **1440h (60 dias)** | SVC-AN | prazo bem mais longo que a regra geral |
| **RJ** | 33 | 24h | SVC-AN | **prioridade deste doc** |
| RN | 24 | 24h | SVC-AN | — |
| RS | 43 | **168h** | SVC-AN | RS não é contingência de si mesmo |
| RO | 11 | **720h** | SVC-AN | extemporâneo, casos excepcionais |
| RR | 14 | 24h | SVC-AN | — |
| **SC** | 42 | 24h | SVC-AN | **prioridade deste doc** |
| **SP** | 35 | 24h | SVC-AN | **prioridade deste doc** |
| SE | 28 | 24h | SVC-AN | — |
| TO | 17 | 24h | SVC-AN | — |

## Impacto no escopo inicial

As 5 UFs priorizadas (SP, MG, RJ, DF, SC) são **todas 24h** — a etapa 3
(§5 da spec principal) pode implementar um prazo fixo de 24h sem perda de
generalidade agora, desde que o campo já exista como dado configurável
(`prazo_cancelamento_horas` por UF), não uma constante no código — MT (8h),
PR/RS (168h), PI (1440h) e RO (720h) mostram que a variação é real e vai
aparecer assim que uma UF de fora das 5 entrar (o que a arquitetura de SVRS
dinâmica do §4.1 já torna provável cedo).

## Nota de leitura — ES e o gap da "NF-e de devolução" (§6 da spec principal)

O Espírito Santo já resolveu, na própria legislação estadual, o problema que
`emissao-fiscal.md` §6 registra como não mapeado: fora do prazo de 24h, ES
extinguiu o cancelamento extemporâneo por processo e criou a **NF-e de
estorno** como instrumento formal de correção. Não é um documento novo de
verdade — é uma nota fiscal comum, com CFOP e natureza de estorno, emitida
para reverter o efeito fiscal da nota que não pôde mais ser cancelada. É a
pista mais concreta de como desenhar esse gap quando ele for priorizado; não
é urgente para o MVP porque as 5 UFs deste doc dão 24h (regra geral) e o
volume de casos que estoura esse prazo tende a ser baixo.
