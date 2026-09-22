# Modelo de Planos — Métrica de Cobrança e Cotas

Última atualização: 20 de setembro de 2026

**Documentos relacionados:** `payments-service.md` (implementação de assinatura/cobrança),
`exportacao-dados-syax.md` seção 5.4 (medição de consumo de disco),
`customizacao-por-cliente.md` (receita adicional fora do plano).

**Status: decisão de direção, nada implementado** — com exceção da seção 3, que é decisão
fechada. Nenhuma migration ou linha de código deste doc
existe. O objetivo aqui é fixar *em cima de qual métrica* o SYAX cobra antes de a tabela de preços
endurecer — mudar métrica de cobrança depois de ter base instalada é uma das renegociações mais
caras que um SaaS enfrenta.

**Origem:** pergunta de 20/09/2026 sobre construir no portal admin a visibilidade de consumo de
disco por tenant (banco + arquivos de exportação), e se esse número poderia virar base de plano.

---

## 1. O que existe hoje

`billing.plan` guarda `monthly_value` e o ciclo (`MONTHLY`/`YEARLY`), e o `SubscriptionService` lê
esse valor no momento da conversão. Ou seja: **preço flat por plano, sem nenhuma métrica de uso**.
Nada neste documento quebra isso — o modelo abaixo é uma evolução do mesmo cadastro, acrescentando
limites ao plano, não substituindo o valor fixo por cobrança variável.

## 2. Princípio: cobrar pela *value metric*, não pelo custo de infra

A métrica de cobrança precisa satisfazer três condições ao mesmo tempo:

1. **O cliente percebe valor nela.** Ele entende por que paga mais quando ela cresce.
2. **Ele consegue prever quanto vai dar.** De cabeça, antes de assinar.
3. **Ela cresce junto com o negócio dele.** Não com o tempo, não com hábito de uso interno.

Consumo de disco falha nas três. Documento fiscal emitido por mês passa nas três.

## 3. Decisão fechada: disco **não** é métrica de preço

**Decisão (ratificada em 20/09/2026, sócio técnico):** o consumo de armazenamento (banco e
arquivos) é medido, exposto no portal admin e usado como **cota técnica por plano**. Nunca como
linha de preço nem como cobrança por GB. Diferente do resto deste documento, este ponto não é
recomendação em aberto — está decidido, e proposta de cobrança por armazenamento não volta à mesa
sem fato novo.

O critério que fechou a decisão, nas palavras do sócio técnico: **não punir quem cumpre a lei.**
Guarda de documento fiscal por cinco anos é obrigação legal do cliente; transformar essa obrigação
em custo variável na fatura dele é cobrar pelo cumprimento da lei.

Razões, em ordem de peso:

- **A conta subiria sem o cliente crescer.** Storage de ERP cresce com o *tempo* — um cliente com
  faturamento estável acumula histórico todo mês. Cobrar por isso significa fatura crescente sem
  contrapartida percebida, que é churn com má vontade e reclamação pública.
- **Puniria o cliente por cumprir a lei.** Documento fiscal tem guarda obrigatória de 5 anos. Preço
  por volume armazenado cria incentivo para o cliente apagar o que ele é obrigado a manter — ou
  para culpar o SYAX por não poder apagar.
- **O dinheiro não justifica o atrito.** Block storage na OCI sai na ordem de US$ 0,025/GB/mês. Um
  tenant SMB com anos de histórico relacional dá poucos GB: centavos. Criar objeção de venda e
  ticket de suporte recorrente para recuperar esse troco é mau negócio.
- **Não é o que o mercado faz.** No ERP SMB brasileiro (Bling, Tiny, Omie, ContaAzul e similares) a
  cobrança é por nota/pedido, usuários, módulos ou CNPJ — não por GB. O contraexemplo mais citado é
  o armazenamento excedente da Salesforce, lembrado como caso de *gotcha billing*, não como modelo.
  *(Tabelas de preço de concorrente mudam — conferir as vigentes antes de fechar valores.)*

## 4. Métricas candidatas

| Métrica | Serve como preço? | Observação |
|---|---|---|
| Documentos fiscais emitidos/mês (NF-e, NFC-e, NFS-e) | **Sim — principal** | Canônica no mercado brasileiro; escala junto com o faturamento do cliente |
| Usuários ativos | Sim — secundária | Fácil de entender, mas incentiva compartilhamento de login |
| Estabelecimentos/CNPJs | Sim — secundária | Já modelado (`estabelecimentos-filiais.md`); degrau natural de porte |
| Módulos habilitados (fiscal, estoque, financeiro, compras) | Sim — ortogonal | Empacota valor por necessidade, não por volume |
| Armazenamento de anexos (XML, PDF, imagens) | **Não — só cota** | Ver seção 5 |
| Tamanho da base de dados | **Não — só cota** | Ver seção 5 |

## 5. Cota: guard-rail anti-abuso, não receita

Armazenamento entra no plano como **limite generoso incluído**, dimensionado para que a grande
maioria nunca chegue perto dele. A função da cota é proteger contra a minoria que sobe volume
desproporcional de anexo — sem colocar uma linha de cobrança variável na fatura de quem nunca
encosta no limite.

Regras que tornam a cota aceitável para o cliente:

- **Visível no painel do próprio cliente**, não só no admin. Cota que só aparece quando estoura é
  a mesma armadilha que a seção 3 rejeita.
- **Aviso em ~80% do limite**, com antecedência para agir.
- **Estourar não bloqueia o que já existe.** Nunca apagar dado do cliente nem travar acesso ao
  histórico — no máximo bloquear *novos* uploads de anexo, com upgrade a um clique.
- **Dado fiscal obrigatório nunca conta contra a cota** se o bloqueio puder impedir a operação
  legal do cliente. Guarda obrigatória é custo do produto, não do plano.
- **Excedente vira pacote adicional**, não cobrança por GB medido: preço previsível e sem
  surpresa no fechamento do mês.

## 6. O ganho comercial

O que a mudança de métrica compra, em ordem de impacto:

- **Receita que cresce sem vender de novo.** Com preço atrelado a volume fiscal, o cliente que
  cresce paga mais sem renegociação, sem vendedor e sem novo contrato. É o mecanismo de expansão
  que sustenta retenção líquida de receita acima de 100% — receita crescendo na base instalada
  mesmo com churn. Preço flat só cresce vendendo logo novo, e todo logo novo custa CAC.
- **Entrada barata sem entregar a margem.** Plano de entrada pequeno porque o cliente é pequeno
  derruba a barreira de assinatura — o que importa para um ERP que se posiciona por custo baixo. O
  valor é capturado depois, quando o cliente cresceu e o dado dele já está dentro. Cobrar caro na
  entrada, ao contrário, filtra exatamente o cliente que o SYAX quer pegar cedo.
- **Menos objeção no fechamento.** O cliente sabe de cabeça quantas notas emite por mês. Ele não
  sabe — nem tem como saber — quantos GB ocupa. Métrica que o prospect consegue simular sozinho
  encurta o ciclo de venda; métrica opaca vira pedido de desconto e pedido de garantia.
- **Margem estável por faixa.** O custo real do SYAX (processamento fiscal, emissão, guarda de XML)
  correlaciona razoavelmente com volume de documento. Precificar na mesma métrica que dirige o
  custo mantém a margem estável entre faixas em vez de deixar o cliente pesado subsidiado pelo leve.
- **Upsell que não parece punição.** Bater a cota ao crescer é conversa fácil ("seu negócio
  cresceu"). Fatura surpresa por GB acumulado é conversa de retenção com cliente irritado.
- **Sinal de produto de graça.** A distribuição de consumo por tenant mostra quem está perto do
  teto — lista de upsell qualificada — e quem regrediu, que é sinal precoce de churn.

**O custo do modelo**, para não ficar só o lado bom: cobrança por uso exige medição confiável,
auditável e explicável na fatura, senão vira disputa de cobrança. É por isso que a recomendação
aqui é **cota (binária, simples de explicar)** em vez de metering fino — o metering só se paga
quando o volume justifica a complexidade.

## 7. Pré-requisito: medir antes de precificar

A visibilidade de consumo por tenant no portal admin é pré-requisito dos dois caminhos e vale
construir agora, independentemente da decisão comercial. Sem a distribuição real de consumo, a cota
é chute: alta demais não protege de nada, baixa demais transforma cliente bom em reclamação.

Ordem correta: instrumentar → acumular alguns meses de snapshot → calibrar o limite num percentil
observado da base → só então publicar a cota no plano.

Fontes de medição:

- **Arquivos de exportação:** `export_batch.total_bytes` (`exportacao-dados-syax.md`, seção 5.4).
- **Banco de dados:** snapshot periódico por tenant. Em schema compartilhado (modelo atual) não há
  métrica nativa por tenant — exige job que soma linhas/bytes por `tenant_id` fora do horário de
  pico e grava o resultado, com a tela lendo o snapshot, nunca o agregado ao vivo.

## 8. Efeito de um eventual schema dedicado por tenant

Se o multi-tenant migrar de schema compartilhado para schema dedicado, a **medição** fica trivial:
`pg_total_relation_size` agregado por schema é consulta de catálogo, instantânea, sem varrer tabela.
O job da seção 7 encolhe para uma query.

Isso **não** muda nada da seção 3: ficar fácil de medir não torna a métrica boa para cobrar. Com
schema dedicado o custo por tenant fica mais visível e pode fazer sentido ter faixas de porte — mas
continuam sendo faixas com cota, não preço por GB consumido.

## 9. O que não fazer agora

- Não modelar cobrança variável em `billing.plan` enquanto o preço flat atender.
- Não publicar número de cota antes de ter a distribuição real da base (seção 7).
- Não expor consumo de disco ao cliente final com cara de fatura enquanto for só visibilidade
  interna — o número aparece como uso, não como valor a pagar.
