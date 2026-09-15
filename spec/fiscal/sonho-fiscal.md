# 🌙 Sonho Fiscal — motor fiscal como produto (napkin sketch)

> **AVISO: isto NÃO é roadmap.** É um esboço de guardanapo — "um sonho de uma noite de
> verão". Ideia de produto pra vender o motor fiscal separado do ERP, estilo Avalara.
> Nada aqui está comprometido, priorizado ou aprovado. Serve só pra não perder a ideia.
> Se um dia virar plano de verdade, vira uma spec própria com escopo, fases e bloqueantes.

**Última atualização:** 22 de julho de 2026 · **Status:** SONHO (não iniciado, não planejado) · **Autor da ideia:** os sócios

---

## O pitch de uma linha

> *"Uma chamada de API. Imposto certo, nota emitida. IBS/CBS de nascença, sem herança de ICMS."*

## A cena do sonho

Um dev de um ERP concorrente, 2h da manhã, quebrando a cabeça com a transição de 2027.
Acha a doc, cola uma chave, manda um POST — e nunca mais quer tocar em SEFAZ na vida.
Vira cliente.

```
POST /v1/fiscal/quote
{ "cnpjEmitente": "...", "destino": {"uf":"SP","municipio":"..."},
  "itens": [{ "ncm": "...", "valor": 1000.00, "cfop_hint": "venda" }] }

→ { "ibs": 87.50, "cbs": 92.30, "is": 0,
    "memoria": [ ...cada regra aplicada, citável... ],
    "regime": "transicao_2027", "confianca": "oficial" }
```

Depois `POST /v1/fiscal/emitir` → XML autorizado pela SEFAZ + DANFE.

---

## As 3 camadas (e por que só uma é o negócio)

| Camada | O que é | Valor real |
|---|---|---|
| **1. Motor de cálculo** | IBS/CBS/IS + legado ICMS/PIS/COFINS na transição | Comoditiza rápido — é "só código" |
| **2. Emissão** | NF-e/NFC-e → SEFAZ, certificado A1/A3, homologação por UF, contingência | Operação chata; dor real do cliente |
| **3. Conteúdo fiscal** | NCM, CST, alíquotas, regimes, benefícios por UF/município, regras da reforma, **versionado e datado** | **O moat.** É time jurídico-fiscal atualizando pra sempre. É o que Avalara/TaxWeb/Sovos cobram caro. |

O que dá pra vender tecnicamente é a camada 1. O que sustenta o preço é a 3 — e a 3 é
negócio de operação contínua, não de software.

## As 3 peças da API do sonho

- **`/quote`** — cálculo puro + **memória de cálculo citável** (regra por regra). Contador confia; é o que justifica o preço.
- **`/emitir`** — NF-e/NFC-e, certificado gerenciado, contingência. A parte que ninguém quer fazer duas vezes.
- **`/rules`** — conteúdo fiscal versionado e datado. *"Me dá a regra que valia em 15/03/2027"* → resposta determinística. Auditoria e retroatividade viram feature, não pesadelo.

## O truque que os incumbentes não têm

Eles têm 20 anos de ICMS remendado. Nascer **IBS/CBS-first**, tratando ICMS/PIS/COFINS
como legado em extinção, faz o modelo de dados já ser o do mundo pós-2033. Quando a poeira
baixar, o remendo deles é dívida técnica e o nosso default é o certo.

## Timing

Transição IBS/CBS: 2026 (teste) → 2027–2033 (fase-in). É a janela em que **todo** ERP e
negócio precisa trocar de motor. Melhor argumento pra existir agora.

## Preço do sonho

Grátis até ~100 docs/mês (pega o dev das 2h da manhã) → centavos por documento → tier
enterprise com SLA e suporte de contador. Igual Stripe fez com pagamento: **a doc boa é o vendedor.**

## Trajetória imaginada

`Ano 0` motor interno do ERP → `Ano 1` alguém de fora pede a API → `Ano 2` a API paga mais
que o ERP → `Ano 3` "a gente é uma fiscal-tech que também tem um ERP".

---

## Aterrissagem (os pés no chão que o sonho ignora)

- **Bloqueante externo:** CGIBS / layout oficial IBS/CBS ainda não fechado — a camada 3 é
  alvo móvel. Produtizar antes da regra estabilizar é construir sobre areia. Manter o motor
  **acoplado ao ERP** até a poeira baixar.
- **Auth de produto externo:** hoje os serviços confiam em header injetado pelo gateway
  (`permitAll` downstream). API vendável precisa de API key/OAuth por cliente, multi-tenancy
  isolado, rate limit, versionamento de contrato, SLA, idempotência e auditoria por emissão.
  Isso é "produto novo", não "expõe o serviço interno".
- **Foco:** o MVP de novembro/2026 vem primeiro. Isto aqui é distração até (a) o motor
  aguentar o próprio volume sem incidente fiscal e (b) alguém de fora pedir pra usar sem o ERP.

> Regra de ouro do napkin: **sonho anotado demais vira backlog e perde a graça.** Se isto
> começar a ganhar fases e datas, aí sim promove pra spec de verdade — não antes.