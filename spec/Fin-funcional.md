# Spec Funcional — Módulo Financeiro do ERP

**Spec Funcional v1 — derivada da Fin.md v12**
**Data:** 2026-07-05
**Idioma:** Português (Brasil)

> **Nota para o revisor:** este documento descreve exclusivamente as **regras de negócio** do
> módulo Financeiro — o que o sistema faz, por que faz e sob quais condições. Não contém
> detalhes de implementação (banco de dados, integrações técnicas, código). Destina-se a um
> revisor de negócio (contador/analista financeiro) validar se as regras fiscais, contábeis e
> operacionais descritas correspondem à prática e à legislação. Cada regra é numerada (ex.
> **RN-AP-001**) para facilitar comentários e referências durante a revisão.

---

## Índice

1. [Visão Geral](#1-visão-geral)
2. [Fundação Transversal](#2-fundação-transversal)
3. [Módulo I — Motor Fiscal e Reforma Tributária](#3-módulo-i--motor-fiscal-e-reforma-tributária)
4. [Módulo II — Contas a Pagar e Contas a Receber](#4-módulo-ii--contas-a-pagar-e-contas-a-receber)
5. [Módulo III — Fluxo de Caixa e Conciliação Bancária](#5-módulo-iii--fluxo-de-caixa-e-conciliação-bancária)
6. [Módulo IV — Tesouraria (Boletos, PIX, CNAB, DDA, Cheques, Aplicações)](#6-módulo-iv--tesouraria)
7. [Módulo V — Contabilidade e Livro-Razão (GL)](#7-módulo-v--contabilidade-e-livro-razão-gl)
8. [Módulo VI — Análises Gerenciais e Relatórios](#8-módulo-vi--análises-gerenciais-e-relatórios)
9. [Perfis e Alçadas de Aprovação](#9-perfis-e-alçadas-de-aprovação)
10. [Telas e Menus](#10-telas-e-menus)
11. [Pontos em Aberto / Decisões Pendentes](#11-pontos-em-aberto--decisões-pendentes)
12. [Cenários de Aceitação (exemplos numéricos)](#12-cenários-de-aceitação-exemplos-numéricos)

---

## 1. Visão Geral

### 1.1 O que é o módulo Financeiro

O módulo Financeiro do ERP cobre toda a gestão financeira, fiscal e contábil de um tenant
(empresa cliente do sistema): contas a pagar e a receber, fluxo de caixa, conciliação
bancária, emissão de cobranças (boleto, PIX, CNAB, DDA, cheques), aplicações financeiras,
contabilidade (livro-razão) e relatórios gerenciais. Ele também absorve o cálculo dos novos
tributos da Reforma Tributária (IBS, CBS e Imposto Seletivo) desde a emissão da nota fiscal
até a apuração mensal e o recolhimento das guias.

Este é o módulo do **produto ERP**, usado pelos tenants (empresas clientes) para gerir a
própria contabilidade e finanças. Ele é totalmente separado da parte de cobrança do próprio
ERP como serviço (planos, assinatura, comissão de parceiros) — essa parte é tratada por uma
camada interna à parte ("billing"), que não se comunica diretamente com o módulo Financeiro.
O billing apenas controla se o tenant está com a assinatura ativa; o módulo Financeiro é
habilitado ou não conforme o plano contratado, mas desconhece os detalhes de cobrança do
próprio SaaS.

### 1.2 Escopo por sub-área

| Sub-área | Situação nesta revisão |
|---|---|
| Feriados bancários | Especificado |
| Trilha de auditoria | Especificado |
| Centro de custo e rateio | Especificado |
| Integração NF-e → Financeiro | Especificado |
| Motor Fiscal (IBS/CBS/IS) | Especificado — cálculo completo, alíquotas municipais aguardando publicação oficial |
| Contabilidade / Livro-Razão | Especificado nos pontos essenciais |
| Contas a Pagar | Especificado |
| Contas a Receber | Especificado |
| Adiantamentos | Especificado |
| Compensação entre contas (netting) | Especificado |
| Empréstimo / Leasing | Especificado |
| Fluxo de Caixa | Especificado |
| Conciliação Bancária (extrato OFX) | Especificado |
| Controle de conta corrente | Especificado |
| Orçamento financeiro | Especificado |
| Boletos (emissão e CNAB) | Especificado — inclusive o detalhamento campo a campo do arquivo bancário (padrão FEBRABAN 240, banco piloto e plano de homologação), descrito na spec técnica |
| DDA (débito direto autorizado) | Especificado |
| Cheques | Especificado |
| Aplicações financeiras | Especificado |
| Análises gerenciais e relatórios | Especificado |
| Plano de contas | Elenco contábil oficial usado como modelo de partida, editável pelo tenant, sem bloqueio |
| Obrigações acessórias (SPED, DCTFWeb, declaração IBS/CBS) | Especificado por regime tributário — MVP entrega relatórios de apoio (receita bruta e retenções) para tenant do Simples Nacional; ECD/ECF/EFD ICMS-IPI ficam para a Fase 2 (Lucro Presumido/Real) — ver §3.10 |
| Módulo fiscal legado (ICMS/ISS, ainda vigente até 2033) | Spec separada |

### 1.3 Ordem de dependência entre os módulos

Os módulos foram desenhados em cadeia — cada um consome o resultado do anterior:

1. **Motor Fiscal** calcula os tributos de cada operação antes de qualquer título existir.
2. **Contas a Pagar / Receber** usa o resultado do cálculo fiscal para criar os títulos.
3. **Fluxo de Caixa e Conciliação Bancária** trabalha em cima dos títulos e das contas
   correntes.
4. **Tesouraria** (boletos, CNAB, PIX, DDA, cheques) depende de conta corrente e das contas a
   receber.
5. **Contabilidade** consome os eventos financeiros de todos os módulos anteriores e gera os
   lançamentos contábeis.
6. **Análises Gerenciais** apenas lê dados de todos os módulos — não grava nada.

### 1.4 Premissas gerais que valem para todo o módulo

- Cada empresa (tenant) enxerga só os próprios dados — isolamento total entre empresas.
- Toda ação de criação/alteração registra o usuário responsável.
- Moeda: Real (BRL). Valores sempre com duas casas decimais.
- **Saldos nunca são um número fixo guardado — são sempre recalculados a partir dos
  lançamentos confirmados.** Isso evita que um saldo "trave" desatualizado se algum
  lançamento for corrigido depois.
- Reimportar um arquivo (extrato bancário, retorno de banco) nunca duplica lançamentos —
  o sistema identifica o que já foi processado.

---

## 2. Fundação Transversal

> Conjunto de regras e cadastros que servem de base para todos os módulos — pré-requisito
> antes de qualquer operação financeira.

### 2.1 Objetivo

Prover recursos comuns que todo o módulo usa: cálculo de dias úteis, rastreabilidade de
alterações, dimensão de centro de custo, o contrato de como uma nota fiscal vira título
financeiro, o plano de contas de partida e o fluxo de aprovação por alçada.

### 2.2 Conceitos

**Feriados bancários.** Cadastro de feriados nacionais, estaduais e municipais, usado para
calcular vencimentos que "empurram" para o próximo dia útil e para dar tolerância na
conciliação bancária automática. Feriados nacionais fixos vêm prontos no sistema; feriados
móveis (Carnaval, Corpus Christi, Sexta-feira Santa) são calculados automaticamente a cada
ano. O administrador do tenant pode cadastrar feriados municipais específicos.

**Trilha de auditoria.** Toda alteração em dado financeiro sensível (título, baixa, ajuste,
compensação, apuração mensal, fechamento de período, boleto) fica registrada: o que mudou, o
valor antes e depois, quem fez, de onde (IP) e quando. Essa trilha é exigida pela legislação
fiscal — a Receita Federal pode solicitar o histórico completo de alterações.

**Centro de custo.** Dimensão analítica (departamento, projeto, filial) que pode ser
associada a um título, a uma baixa ou a um lançamento contábil, para permitir relatórios por
área da empresa. Centros de custo podem ter hierarquia (centro pai/filho); apenas os
centros "analíticos" (folha da hierarquia) recebem lançamento direto. Um rateio permite
distribuir um mesmo valor entre vários centros de custo por percentual (ex.: uma nota de
software rateada 50% TI / 30% Vendas / 20% Administrativo); os percentuais somam sempre
100% e, na contabilização, eventual diferença de centavos do arredondamento fica no centro
de maior percentual (ver RN-FUND-016 a RN-FUND-018).

**Contrato NF-e → Financeiro.** Define como uma nota fiscal aprovada (de entrada ou saída)
gera automaticamente um título financeiro, e como o cancelamento de uma nota reflete no
título correspondente.

**Plano de contas.** Estrutura contábil (Ativo, Passivo, Patrimônio Líquido, Receitas,
Custos, Despesas) que classifica todo lançamento contábil.

**Alçada de aprovação.** Fluxo de aprovação configurável por faixa de valor, usado
principalmente para liberar pagamentos.

### 2.3 Regras de Negócio

| # | Regra |
|---|---|
| RN-FUND-001 | Um vencimento pode ser calculado considerando apenas dias úteis — nesse caso, sábados, domingos e feriados (nacional, estadual ou municipal, conforme a praça) empurram a data para o próximo dia útil. |
| RN-FUND-002 | Toda alteração em título, baixa, ajuste, compensação, apuração mensal, fechamento de período contábil ou boleto gera um registro de auditoria imutável — quem alterou, quando, de onde, e os valores antes/depois. |
| RN-FUND-003 | O histórico de auditoria deve ser mantido por, no mínimo, 5 anos, conforme exigência fiscal. |
| RN-FUND-004 | Apenas centros de custo marcados como "analíticos" podem receber lançamento direto; centros que são apenas agrupadores (nível hierárquico superior) não recebem lançamento. |
| RN-FUND-005 | A soma dos percentuais de um rateio por centro de custo deve fechar em exatamente 100%. |
| RN-FUND-006 | Toda nota fiscal de entrada aprovada gera automaticamente um ou mais títulos a pagar; toda nota de saída autorizada gera automaticamente um ou mais títulos a receber — o número de títulos corresponde ao número de parcelas da condição de pagamento. |
| RN-FUND-007 | O cancelamento de uma nota fiscal cancela o(s) título(s) vinculado(s), **desde que nenhuma baixa efetiva (confirmada) já tenha ocorrido**. Se já houve baixa efetiva, o cancelamento é bloqueado e um alerta é enviado ao operador financeiro para tratamento manual. |
| RN-FUND-008 | O plano de contas de cada tenant nasce como uma cópia de um modelo oficial (elenco de contas contábil) no momento em que a empresa é ativada no sistema. A partir daí, o tenant pode editar livremente a própria cópia — não há bloqueio de revisão por contador como pré-requisito. |
| RN-FUND-009 | Uma conta contábil **sem nenhum lançamento** pode ser alterada livremente (código, tipo, natureza, hierarquia, descrição). |
| RN-FUND-010 | Uma conta contábil **que já tem lançamento** só permite alterar descrição e status ativo/inativo — código, tipo, natureza e posição na hierarquia ficam travados para não quebrar o histórico. |
| RN-FUND-011 | Uma conta contábil que tem contas-filhas na hierarquia não pode ser excluída. |
| RN-FUND-012 | Uma futura revisão do modelo oficial de plano de contas gera uma nova versão do modelo; tenants já ativos continuam na versão que receberam na ativação — só tenants novos recebem a versão mais recente. |
| RN-FUND-013 | Operações financeiras que se enquadrem em uma faixa de valor configurada para exigir aprovação (ex.: pagamentos acima de determinado valor) ficam retidas até a decisão do aprovador — o título correspondente fica bloqueado para pagamento/remessa enquanto aguarda decisão. |
| RN-FUND-014 | Toda decisão de aprovação (aprovar ou rejeitar) é registrada de forma imutável na trilha de auditoria — quem decidiu, quando e de onde. Rejeição exige justificativa. |
| RN-FUND-015 | Se o aprovador não decidir dentro do prazo configurado, a solicitação pode ser automaticamente escalada para um aprovador de nível superior ou aprovada automaticamente, conforme a política configurada para aquela faixa de valor. |
| RN-FUND-016 | Um rateio por centro de custo só admite centros de custo analíticos e ativos — centros agrupadores ou inativos não podem participar. |
| RN-FUND-017 | Ao contabilizar um título rateado, o valor é dividido entre os centros de custo conforme os percentuais definidos; a eventual diferença de centavos gerada pelo arredondamento é absorvida pelo centro de custo de **maior percentual**, garantindo que a soma dos valores lançados feche exatamente igual ao valor do título. |
| RN-FUND-018 | Um relatório gerencial sobre um título **já contabilizado** sempre lê os valores por centro de custo tal como foram divididos no momento da contabilização — alterar o rateio depois não reescreve o passado. Um título **ainda não contabilizado** usa os percentuais vigentes do rateio como visão prospectiva, sujeita a mudar até a contabilização efetiva. |

### 2.4 Fluxo — Aprovação por Alçada

1. Uma operação (por exemplo, pagamento de um título) cujo valor se enquadra em uma faixa
   configurada para exigir aprovação gera uma solicitação de aprovação. O título fica
   bloqueado, com o motivo "aguardando aprovação".
2. O responsável pela aprovação (conforme papel configurado para aquela faixa de valor — por
   exemplo, gerente financeiro até certo valor, diretor acima disso) é notificado e vê a
   solicitação na tela de Aprovações Pendentes, com filtros por valor, tipo e data.
3. O aprovador analisa (valor, centro de custo, tipo de despesa) e decide:
   - **Aprova** → o título é desbloqueado e o fluxo original continua.
   - **Rejeita** → o título permanece bloqueado, com justificativa obrigatória visível para
     quem solicitou.
   - **Não decide dentro do prazo** → conforme a configuração daquela faixa, o pedido é
     escalado automaticamente para o aprovador do nível seguinte, ou é aprovado
     automaticamente.
4. Toda decisão fica registrada de forma permanente na trilha de auditoria.

### 2.5 Validações

- Alíquota/percentual de rateio por centro de custo: soma deve ser exatamente 100%.
- Conta contábil com lançamento: apenas descrição e status podem mudar.
- Conta contábil com contas-filhas: não pode ser excluída.
- Nota fiscal cancelada com baixa efetiva já registrada: cancelamento do título é bloqueado,
  não silenciosamente ignorado.

### 2.6 Telas

- **Feriados** — cadastro/consulta de feriados nacionais (somente leitura para o tenant),
  estaduais e municipais (o tenant pode adicionar feriados municipais específicos).
- **Centros de Custo** — cadastro hierárquico, marcação de "aceita rateio", ativação/
  inativação.
- **Rateios de Centro de Custo** — definição de um rateio nomeado com a lista de centros e
  seus percentuais.
- **Aprovações Pendentes** — lista de solicitações aguardando decisão do usuário logado, com
  ação de aprovar/rejeitar e campo de justificativa.
- **Configuração de Alçadas** — definição das faixas de valor, papel aprovador, prazo e ação
  em caso de estouro de prazo (escalar ou aprovar automaticamente).

---

## 3. Módulo I — Motor Fiscal e Reforma Tributária

> ⚠️ Este módulo depende de parâmetros ainda não publicados pelo órgão gestor da reforma
> (Comitê Gestor do IBS). Todo o texto abaixo deve ser validado por um especialista fiscal
> antes de qualquer uso em produção — alíquotas, códigos de situação tributária e listas de
> NCM (Nomenclatura Comum do Mercosul) dependem de confirmação com a legislação em vigor
> (LC 214/2025 e normas técnicas complementares da Receita Federal).

### 3.1 Objetivo

Calcular, para cada operação de venda ou compra, quais tributos incidem e em qual valor,
durante o período de transição (2026–2033) em que os tributos atuais (ICMS, ISS, PIS,
Cofins) coexistem com os novos tributos da reforma (IBS, CBS e Imposto Seletivo — IS). O
resultado desse cálculo alimenta o título a pagar/receber gerado a partir da nota fiscal.

### 3.2 Conceitos

- **IBS (Imposto sobre Bens e Serviços)** — parte estadual e parte municipal, calculado com
  base no **município de destino** da operação (onde está o comprador/tomador), não no
  município do vendedor.
- **CBS (Contribuição sobre Bens e Serviços)** — tributo federal que substitui PIS/Cofins,
  calculado conforme o **regime tributário do vendedor**.
- **IS (Imposto Seletivo)** — incide sobre produtos específicos considerados nocivos à saúde
  ou ao meio ambiente (cigarros, bebidas alcoólicas, veículos, embarcações, aeronaves,
  bebidas açucaradas, produtos minerais, entre outros listados em lei). Não é compensável —
  é recolhido integralmente por quem fabrica ou importa o produto.
- **Split payment** — mecanismo pelo qual, em pagamentos eletrônicos, parte do valor
  correspondente ao imposto é automaticamente segregada e direcionada ao poder público no
  momento da liquidação, em vez de ficar com o vendedor para recolhimento posterior. Entra
  em produção a partir de 2027.
- **Regimes diferenciados** — determinados produtos e serviços têm tratamento tributário
  especial: alíquota zero (ex.: itens da cesta básica, hortícolas, frutas, ovos,
  medicamentos, dispositivos médicos, produtos de acessibilidade para PcD), redução de 60%
  na alíquota (ex.: educação, saúde, produções artísticas, insumos agropecuários, higiene
  pessoal básica) ou tributação monofásica (imposto recolhido uma única vez na origem da
  cadeia, sem incidência nas etapas seguintes).
- **Crédito tributário** — direito de abater, do imposto devido nas vendas, o imposto pago
  nas compras. A regra de quem tem direito a crédito e em que proporção depende do regime
  tributário de quem vendeu (ver regras abaixo).

### 3.3 Cronograma da transição (2026–2033)

| Período | O que muda |
|---|---|
| 2026 | CBS (0,9%) e IBS (0,1%) começam a ser destacados nas notas fiscais, em caráter de teste/piloto, convivendo com os tributos atuais. |
| 2027 | PIS e Cofins deixam de existir. CBS passa a valer em alíquota plena (estimada em torno de 8,8%). O Imposto Seletivo passa a ser cobrado. O split payment entra em produção. |
| 2028 | CBS já em regime normal; ICMS e ISS ainda vigentes, convivendo com os novos tributos. |
| 2029–2032 | ICMS e ISS são reduzidos progressivamente, ano a ano. |
| 2033 | ICMS e ISS deixam de existir — sistema tributário 100% baseado em IBS e CBS. |

### 3.4 Ordem de prioridade das regras de cálculo

Quando mais de uma regra poderia se aplicar a uma mesma operação, prevalece a mais
específica, na seguinte ordem (da mais geral para a mais específica):

1. Alíquota padrão do ano vigente.
2. Regime diferenciado do produto/serviço (definido pelo código fiscal — NCM ou código de
   serviço).
3. Regra da operação em si (por exemplo, uma operação classificada como "brinde" pode zerar
   o direito a crédito, independentemente do produto).
4. Configuração específica do produto, que pode sobrescrever a alíquota do Imposto Seletivo
   definida pelo código fiscal.
5. Regime tributário de quem está vendendo — por exemplo, uma empresa do Simples Nacional
   não gera crédito de IBS/CBS integral para quem compra dela.

**Exemplo:** um produto da cesta básica (alíquota zero) vendido por uma empresa do Simples
Nacional continua com alíquota zero — a regra do produto prevalece sobre a regra do regime
do vendedor.

### 3.5 Regras de Negócio

| # | Regra |
|---|---|
| RN-MF-001 | A alíquota de IBS é sempre determinada pelo **município de destino** da operação (onde está o destinatário), nunca pelo município de quem vendeu. |
| RN-MF-002 | Um comprador **MEI** nunca tem direito a crédito de IBS/CBS na compra. Um comprador que compra de um fornecedor do **Simples Nacional** tem direito a um crédito **reduzido**, equivalente ao valor efetivamente recolhido dentro do regime simplificado — a menos que o fornecedor do Simples tenha optado por recolher IBS/CBS pelo regime normal (fora do regime simplificado), caso em que o crédito é integral. |
| RN-MF-003 | Produto da cesta básica nacional tem alíquota zero de IBS, CBS e IS. Produto de tributação monofásica não gera crédito ao longo da cadeia de revenda (o imposto já foi recolhido de uma só vez na origem). |
| RN-MF-004 | O Imposto Seletivo não é compensável — é recolhido integralmente por quem fabrica ou importa; nas etapas seguintes da cadeia (distribuidor, varejista) o IS não incide novamente. |
| RN-MF-005 | O split payment se aplica a pagamentos por meio eletrônico — PIX, cartão e boleto (quando liquidado por meio de arranjo de pagamento eletrônico). Pagamentos em dinheiro ou cheque ficam fora do split. O modelo adotado ("split inteligente") segrega, no máximo, o saldo devedor do fornecedor considerando os créditos que ele já apurou; o excedente eventualmente retido é devolvido em até 3 dias úteis. |
| RN-MF-006 | O cálculo do motor fiscal é determinístico: os mesmos dados de entrada sempre produzem o mesmo resultado, sem depender de estado interno ou histórico de cálculos anteriores. |
| RN-MF-007 | As alíquotas de IBS por município são atualizadas anualmente, conforme publicação do órgão gestor nacional do IBS (Comitê Gestor). |
| RN-MF-008 | Saldo credor acumulado de IBS/CBS (quando os créditos superam os débitos em um mês) **não expira** — é utilizado para compensar tributos devidos em meses futuros. |
| RN-MF-009 | Uma apuração mensal fechada não pode ser reaberta. Uma correção é feita através de uma nova apuração retificadora, referente à mesma competência. |
| RN-MF-010 | Se um produto não tiver o código fiscal (NCM) cadastrado, o sistema não bloqueia a operação: calcula com a alíquota padrão e emite um aviso para o operador revisar o cadastro do produto. |
| RN-MF-011 | O Imposto Seletivo integra a base de cálculo do IBS e do CBS (ou seja, primeiro calcula-se o IS, depois soma-se ao valor da operação para então calcular IBS e CBS sobre essa base maior). |
| RN-MF-012 | Regimes diferenciados (alíquota zero, redução de alíquota) reduzem a **alíquota** aplicada, não a base de cálculo — a nota fiscal sempre mostra a base cheia, com o percentual de redução destacado. |
| RN-MF-013 | Para serviços, o município relevante para o IBS é o **local onde o serviço é efetivamente prestado**, e não necessariamente o endereço de quem contratou — exceto para categorias específicas de serviço com regra própria (ex.: serviços sobre imóveis usam o local do imóvel; serviços digitais remotos usam o domicílio de quem contratou; transporte usa o destino da carga/passageiro). |
| RN-MF-014 | Uma devolução de mercadoria ou serviço gera o cálculo fiscal com valores invertidos (créditos e débitos revertidos na proporção devolvida), anulando o efeito da operação original na medida da devolução. |
| RN-MF-015 | Ao fechar a apuração mensal, o sistema gera automaticamente os títulos a pagar das guias de recolhimento (uma para IBS, uma para CBS, uma para o Imposto Seletivo), com vencimento no prazo legal — apenas para os tributos com saldo devedor; tributo com saldo credor não gera guia. |
| RN-MF-016 | IBS, CBS e IS têm fato gerador na **operação** (emissão/entrega), não no recebimento. Um cliente que não paga o título **não** reverte o imposto já devido — a perda é tratada como risco de crédito, via provisão para devedores duvidosos (ver §8), e não como estorno fiscal. Estorno fiscal só ocorre por cancelamento da operação ou devolução documentada (nota de crédito — RN-MF-014). |

### 3.6 Casos especiais

- **Venda por empresa do Simples Nacional:** a nota fiscal destaca IBS e CBS normalmente
  (o comprador toma crédito ou não, dependendo do próprio regime). A empresa do Simples
  recolhe o tributo em alíquota reduzida, calculada sobre a receita bruta mensal — não
  operação a operação.
- **Compra por empresa do Simples Nacional:** não há direito a crédito — o valor do imposto
  destacado na nota do fornecedor vira custo do produto comprado.
- **MEI:** não destaca IBS nem CBS nas notas fiscais, tanto na compra quanto na venda; todos
  os valores calculados para o MEI saem zerados.
- **Zona Franca de Manaus:** produtos industrializados lá têm benefício fiscal preservado —
  alíquota reduzida quando enviados para fora de Manaus. Caso de alta complexidade, a ser
  validado com especialista antes de entrar em produção.
- **Importação:** a CBS incide no desembaraço aduaneiro (recolhida por quem importa); o IBS
  segue as regras do destino final do produto.

### 3.7 Integração com o Financeiro

Quando uma nota fiscal de entrada é aprovada, o motor calcula os tributos e o resultado é
usado para: (1) criar o(s) título(s) a pagar correspondente(s) com os valores de imposto já
identificados, e (2) somar os créditos de IBS/CBS na apuração mensal do tenant. Quando uma
nota de saída é emitida, o mesmo cálculo cria o(s) título(s) a receber e soma os débitos na
apuração mensal.

**Conciliação do split payment (a partir de 2027):** quando a adquirente/Banco Central retém
IBS/CBS já na liquidação do pagamento (RN-MF-005), o título é baixado pelo valor **bruto**
(o valor da venda, imposto incluso), mas o caixa só recebe o valor **líquido** (bruto menos o
imposto retido na fonte pelo arranjo de pagamento). A diferença retida é registrada numa conta
transitória de impostos recolhidos na liquidação — assim a conciliação bancária casa
exatamente com o valor líquido depositado, sem sobrar diferença de centavos em aberto. O
acerto dessa conta transitória contra o valor efetivamente apurado acontece na apuração fiscal
mensal (§3.5, RN-MF-015).

### 3.8 Validações

- Uma operação sem código fiscal (NCM/CFOP) do tipo esperado não é bloqueada — segue com
  alíquota padrão e alerta.
- Um código de operação (CFOP) inválido para o sentido da operação (por exemplo, um CFOP de
  entrada usado numa nota de saída) bloqueia a emissão.
- Uma data de competência fora da faixa coberta pelo cronograma de transição (2026–2033) é
  um erro crítico — o cronograma deve cobrir todo esse intervalo.

### 3.9 Telas

- Consulta de NCM, código de situação tributária e CFOP (referência, mantida pela equipe da
  plataforma — o tenant não edita).
- Configuração fiscal da empresa (regime tributário, código de regime, inscrição estadual/
  municipal, opção pelo Simples Nacional).
- Consulta da apuração mensal (créditos, débitos, saldo a recolher ou saldo credor
  acumulado).

### 3.10 Obrigações acessórias — escopo por regime tributário

**Decisão de escopo:** o MVP atende tenants optantes pelo **Simples Nacional** — o público-alvo
do produto. O regime tributário do tenant (Simples Nacional, Lucro Presumido ou Lucro Real) é
um parâmetro cadastral e é ele quem determina quais obrigações fiscais acessórias e quais
retenções na fonte se aplicam a cada tenant. Tenants em Lucro Presumido ou Lucro Real ficam
para uma **Fase 2** do produto.

Importante: o ERP **nunca transmite nada diretamente à Receita Federal** — quem transmite é o
contador do tenant, usando os relatórios e os dados que o ERP produz.

**O que o ERP entrega no MVP (tenant Simples Nacional):**

- Relatório de **receita bruta** segregada por atividade e por estabelecimento — a base que o
  contador usa para preencher o PGDAS-D mensal e a DEFIS anual.
- Relatório de **retenções e pagamentos**, por período, por contraparte (fornecedor/prestador),
  por natureza de rendimento (tabela oficial da Receita) e código de receita derivado dela — a
  base que o contador usa para a EFD-Reinf. Uma empresa do Simples Nacional é obrigada a
  declarar a EFD-Reinf sempre que efetua algum tipo de retenção — por exemplo, INSS de 11% ao
  contratar serviço com cessão de mão de obra, ou IRRF sobre pagamentos a prestadores de
  serviços profissionais. Atenção: os eventos de pagamentos a pessoas físicas e jurídicas são
  devidos, nos casos previstos, **mesmo quando não há imposto retido** (e uma retenção
  dispensada por ficar abaixo de R$ 10 também é informada) — por isso o relatório lista os
  pagamentos por natureza de rendimento, e não apenas os que tiveram retenção.
- O relatório oferece **duas visões de competência**, porque cada tributo é apurado por uma
  data diferente: a retenção de INSS é apurada pela data de **emissão da nota fiscal** do
  prestador, enquanto a retenção de IR é apurada pela data do **pagamento**.

**O que fica fora do MVP e vira Fase 2** (aplicável a Lucro Presumido/Real): a Escrituração
Contábil Digital (ECD/SPED Contábil), a Escrituração Contábil Fiscal (ECF) e a EFD ICMS/IPI
(SPED Fiscal). Ainda assim, a fundação contábil do MVP já nasce compatível com essa evolução
futura: o Livro Diário é gerado sem lacunas de numeração e o fechamento de período é imutável
(ver §7) — pré-requisitos de qualquer SPED contábil. O plano de contas também prevê, desde já,
um de-para opcional com o plano de contas referencial da Receita Federal, para que a Fase 2 não
exija reclassificar retroativamente nenhuma conta.

**Regras de retenção por regime do pagador** — quem retém e o quê depende do regime tributário
do próprio tenant (quem paga o fornecedor), não do regime do fornecedor:

| # | Regra |
|---|---|
| RN-MF-017 | Um tenant do Simples Nacional nunca retém PIS, COFINS ou CSLL sobre pagamentos a fornecedores/prestadores — a legislação exclui optantes do Simples dessa retenção. |
| RN-MF-018 | Um tenant do Simples Nacional retém IRRF sobre pagamentos a prestadores de serviços profissionais e retém INSS (11%) quando contrata serviço com cessão de mão de obra — essas retenções exigem declaração posterior via EFD-Reinf, feita pelo contador. |
| RN-MF-019 | A matriz de qual tributo é retido para cada combinação de regime tributário do tenant e tipo de serviço é **configuração do sistema**, não uma regra fixa — permite ajustar quando a legislação mudar ou quando o tenant migrar de regime. |

**Fontes oficiais consultadas:** a pasta `spec/` do projeto guarda o manual do usuário da EFD-Reinf
(leiaute 2.1.2, ADE Cofis 23/2023, ago/2023) — cobre as regras de negócio citadas acima — e o
manual do desenvolvedor (v2.7, out/2025), que só passa a ser relevante se um dia o sistema
transmitir declarações diretamente à Receita; hoje o escopo é exportar relatórios para o
contador transmitir.

**Tabelas oficiais como dado, não como regra:** a natureza de rendimento de cada retenção e o
código de receita do DARF que dela deriva vêm de tabelas oficiais da Receita Federal — a Tabela
de Naturezas de Rendimento e as tabelas de-para (natureza × tributo × código de receita ×
periodicidade de recolhimento, cerca de mil combinações, cada uma com vigência de início e fim).
O sistema carrega essas tabelas como dados de configuração versionados, e o relatório de
retenções deriva o código de receita a partir delas — quando a Receita atualiza a tabela,
atualiza-se o dado, não o código do relatório.

**Ponto em aberto:** nenhum dos manuais anexados documenta o suporte da EFD-Reinf ao CNPJ
alfanumérico. Antes de implementar o relatório de retenções, confirmar a nota técnica de leiaute
vigente para esse ponto.

**Obrigações que não têm papel no módulo Financeiro:** o ISS do Simples Nacional é recolhido
dentro do próprio DAS (guia única do Simples) — não gera nenhuma declaração municipal separada
tratada pelo financeiro. A DCTFWeb é uma consequência automática do que já foi declarado via
EFD-Reinf/eSocial no portal da Receita — o ERP não participa dessa etapa.

---

## 4. Módulo II — Contas a Pagar e Contas a Receber

### 4.1 Objetivo

Gerenciar todo o ciclo de vida de um título financeiro — do lançamento (manual, por nota
fiscal, por empréstimo, por parcelamento) até a baixa (pagamento/recebimento), passando por
ajustes, prorrogações, adiantamentos, compensações e retenções na fonte.

### 4.2 Conceitos

- **Título** — a entidade central: um valor a pagar ou a receber, com vencimento, terceiro
  (fornecedor, cliente, funcionário ou outro), origem (lançamento manual, nota fiscal,
  empréstimo, parcelamento, apuração fiscal, entre outras) e um "saldo" que só existe
  enquanto o título não estiver totalmente baixado.
- **Baixa** — o registro de um pagamento ou recebimento (total ou parcial) de um título. Uma
  baixa pode nascer **planejada** (ainda não confirmada — por exemplo, um boleto emitido mas
  ainda não compensado) ou já **real** (confirmada, com efeito imediato sobre o saldo do
  título).
- **Ajuste** — um acréscimo (multa, juros de mora) ou desconto aplicado a um título, sempre
  vinculado a um tipo de ajuste configurável.
- **Forma de pagamento** — a regra usada para calcular a data de vencimento de um título a
  partir da data de emissão ou de saída da mercadoria, considerando ou não dias úteis.
- **Bloqueio (hold)** — um título pode ser bloqueado manualmente para pagamento; enquanto
  bloqueado, não aceita baixa e não entra em nenhuma remessa de pagamento.
- **Adiantamento** — um valor pago (ou recebido) antecipadamente a um fornecedor/cliente,
  que fica disponível como saldo para ser usado como forma de baixa em títulos futuros
  daquele mesmo terceiro.
- **Compensação (netting)** — o encontro de contas entre um título a pagar e um título a
  receber da mesma pessoa (mesmo CNPJ/CPF, considerando que cliente e fornecedor do mesmo
  grupo econômico são identificados como a mesma "pessoa" no cadastro), reduzindo ou
  quitando ambos sem movimentar caixa.
- **Empréstimo/leasing** — um contrato de financiamento que, ao ser confirmado, gera
  automaticamente as parcelas como títulos a pagar.
- **Retenção na fonte** — parte do valor de um título de serviço que não é paga ao
  fornecedor, mas retida pelo tenant para recolhimento de tributos (IRRF, contribuições
  previdenciárias, ISS, entre outros) diretamente ao órgão arrecadador.
- **Dunning (régua de cobrança)** — sequência automática de ações de cobrança disparadas
  conforme os dias de atraso de um título a receber.

### 4.3 Ciclo de vida do título

Um título nasce **previsto** (ainda não confirmado) ou **em aberto** (confirmado, aguardando
pagamento). A partir de "em aberto", pode ser **emitido** (no caso de contas a receber, ao
gerar boleto/cobrança), **descontado** (antecipação de recebível), **baixado** (quando o
saldo chega a zero) ou **cancelado**. Um título com qualquer baixa **confirmada** não pode
mais ser cancelado — apenas estornado. Um título bloqueado não aceita nenhuma baixa nem
entra em remessa de pagamento até ser desbloqueado.

Uma baixa nasce **planejada** e passa a **real** quando confirmada (por exemplo, quando a
conciliação bancária confirma que o dinheiro efetivamente entrou/saiu da conta). Uma baixa
real não pode ser cancelada diretamente — apenas revertida por meio de um **estorno**
(ver RN-AP-011). Uma baixa planejada, ao contrário, pode ser cancelada livremente, devolvendo
o saldo ao título.

### 4.4 Regras de Negócio — Contas a Pagar

| # | Regra |
|---|---|
| RN-AP-001 | Um lançamento manual de título só é permitido para tipos de título habilitados explicitamente para lançamento manual. |
| RN-AP-002 | Um título com status Baixado ou Cancelado não aceita nenhuma edição. |
| RN-AP-003 | Um título com alguma baixa confirmada (real) não pode ser cancelado — só pode ser estornado. |
| RN-AP-004 | A soma dos descontos aplicados a um título nunca pode ultrapassar o valor original somado aos acréscimos. |
| RN-AP-005 | Uma prorrogação de vencimento só é aceita se a nova data for posterior à data de vencimento atual. |
| RN-AP-006 | Ao parcelar um título, o título original é cancelado e são criados tantos novos títulos quanto o número de parcelas definido; a soma das novas parcelas deve ser igual ao valor líquido do título original. |
| RN-AP-007 | O saldo de adiantamento é individual por fornecedor (ou cliente) — não pode ser usado para baixar título de um terceiro diferente daquele que originou o adiantamento. |
| RN-AP-008 | Ao confirmar um empréstimo/leasing, o sistema gera automaticamente as parcelas como novos títulos a pagar, calculadas conforme o tipo de amortização escolhido (parcela fixa com juros embutidos, amortização constante com juros decrescentes, ou valores definidos manualmente). |
| RN-AP-009 | Uma baixa **planejada** não reduz o saldo do título — só a confirmação (baixa **real**) reduz o saldo e, se zerar, muda o status do título para Baixado. |
| RN-AP-010 | Não é permitida uma baixa cujo valor ultrapasse o saldo disponível do título. |
| RN-AP-011 | **Estorno de baixa confirmada:** uma baixa já confirmada pode ser estornada — isso cria um novo registro de baixa (não apaga a baixa original), com valor invertido, que devolve o saldo ao título (voltando de Baixado para Em Aberto, se for o caso). Uma baixa de estorno não pode, por sua vez, ser estornada novamente. |
| RN-AP-012 | Ao baixar um título em atraso, o sistema sugere automaticamente os valores de multa (percentual configurável, padrão 2%) e mora (percentual mensal configurável, calculado proporcionalmente aos dias de atraso, padrão 1% ao mês) — o operador pode aceitar ou ajustar antes de confirmar. |
| RN-AP-013 | Um título com uso de adiantamento é baixado pelo valor do adiantamento utilizado — sem criar, adicionalmente, um desconto pelo mesmo valor (isso evitaria contar o abatimento duas vezes e reduziria o saldo além do correto). |
| RN-AP-014 | Retenções na fonte aplicadas a um título de serviço reduzem o valor líquido efetivamente pago ao fornecedor; o valor retido não é recolhido ao fornecedor — vira uma obrigação do tenant perante o órgão arrecadador correspondente, agrupada mensalmente em uma guia de recolhimento por tributo. |
| RN-AP-015 | **Trava de competência retroativa** (vale para contas a pagar e a receber): alterar a data de competência ou de emissão de um título, ou emitir/baixar um título retroativamente, caindo num período contábil já fechado é bloqueado — mesmo para um usuário administrador. A correção é feita por um lançamento de ajuste na competência aberta atual; o período fechado permanece imutável (ver §7, período contábil). |

### 4.5 Regras de Negócio — Contas a Receber

Contas a Receber compartilha as mesmas operações de lançamento, ajuste, prorrogação,
parcelamento, baixa e estorno descritas acima. As regras específicas são:

| # | Regra |
|---|---|
| RN-AR-001 | Um título de recebimento, ao ser "emitido" (preparado para cobrança — geração de boleto/linha digitável), fica bloqueado para edição enquanto estiver nesse status. |
| RN-AR-002 | Renegociar um título a receber cancela o título original e cria um novo com os termos renegociados (novo vencimento, novo valor, possíveis acréscimos/descontos). |
| RN-AR-003 | Uma carta de cobrança só pode ser enviada para títulos já vencidos. |
| RN-AR-004 | O desconto de um título (antecipação de recebível junto ao banco) só é permitido para títulos já emitidos. |
| RN-AR-005 | O adiantamento recebido de um cliente segue a mesma lógica do adiantamento a fornecedor: fica disponível como saldo para baixar títulos futuros daquele mesmo cliente. |
| RN-AR-006 | Uma retenção sofrida do cliente (quando o próprio cliente retém parte do pagamento por obrigação legal) é registrada como uma baixa parcial "por retenção" — o caixa nunca chega a receber esse valor; ele vira um crédito tributário a recuperar. |
| RN-AR-007 | **Recebimento quitando vários títulos (N-para-1):** um único pagamento do cliente que cobre vários títulos gera uma baixa por título, alocando o valor recebido por ordem de vencimento (o mais antigo primeiro). Juros, mora, multa e desconto são calculados individualmente por título, sobre o saldo de cada um na data do recebimento. Se sobrar valor depois de quitar todos os títulos elegíveis, a sobra vira crédito/adiantamento do cliente (RN-AR-005). |
| RN-AR-008 | **Baixas parciais sucessivas (1-para-N):** cada baixa parcial recalcula juros e mora sobre o saldo residual do título na data daquela baixa (não sobre o valor original) — o título só passa a Baixado quando o saldo chega a zero. |

### 4.6 Régua de cobrança automática (Dunning)

Título a receber vencido entra automaticamente em uma régua de cobrança, com etapas
configuráveis por empresa (valores abaixo são o padrão sugerido):

| Etapa | Quando dispara (após o vencimento) | Ação |
|---|---|---|
| 1 | 1 dia | E-mail amigável, com 2ª via do boleto. |
| 2 | 7 dias | E-mail mais firme, com o valor já atualizado (juros e multa). |
| 3 | 15 dias | Cliente marcado como bloqueado para novas vendas (sinalização para o futuro módulo de pedidos consumir). |
| 4 | 30 dias | Escalado para o time de crédito — abre um caso de cobrança manual em uma fila de trabalho. |

Regras da régua: um título baixado, cancelado, bloqueado ou em processo de renegociação sai
da régua automaticamente. O pagamento em qualquer etapa encerra a sequência. O envio manual
de carta de cobrança continua disponível a qualquer momento, independentemente da régua
automática.

### 4.7 Compensação entre contas (Netting)

| # | Regra |
|---|---|
| RN-CO-001 | Um título a pagar só pode ser compensado com um título a receber da **mesma pessoa** (mesmo CNPJ/CPF — inclusive quando cliente e fornecedor pertencem ao mesmo grupo econômico e são reconhecidos como a mesma pessoa no cadastro). Compensação exige que ambos os títulos tenham essa identificação de pessoa preenchida. |
| RN-CO-002 | O valor compensado nunca pode ultrapassar o menor saldo entre os dois títulos envolvidos. |
| RN-CO-003 | Uma compensação parcial mantém saldo em aberto nos dois títulos, proporcionalmente ao que sobrou. |
| RN-CO-004 | Uma compensação só pode ser cancelada enquanto ainda estiver pendente de confirmação — depois de confirmada, não pode mais ser desfeita diretamente. |
| RN-CO-005 | Um mesmo título só pode ter uma compensação pendente por vez. |

Diariamente, o sistema identifica automaticamente terceiros que têm títulos em aberto tanto
a pagar quanto a receber e **sugere** a compensação ao operador — nenhuma compensação é
criada automaticamente sem confirmação humana.

### 4.8 Fluxo — Lançamento e baixa de um título (visão passo a passo)

1. **Origem:** o título nasce de uma nota fiscal aprovada, de um lançamento manual, de um
   empréstimo confirmado ou de um parcelamento/renegociação.
2. **Vencimento:** se não informado diretamente, é calculado pela forma de pagamento
   configurada (considerando ou não dias úteis).
3. **Ajustes** (opcional): acréscimos (multa, juros) ou descontos podem ser aplicados
   enquanto o título estiver em aberto.
4. **Aprovação por alçada** (se aplicável): pagamentos que se enquadrem em uma faixa
   configurada para exigir aprovação ficam retidos até a decisão do aprovador.
5. **Baixa:** o pagamento/recebimento é registrado — total ou parcial, imediato (real) ou
   planejado. Uma baixa em atraso sugere multa e mora automaticamente.
6. **Confirmação:** uma baixa planejada é confirmada quando o dinheiro efetivamente
   circula (por exemplo, confirmado pela conciliação bancária), passando a real e reduzindo
   o saldo do título.
7. **Encerramento:** quando o saldo chega a zero, o título passa para Baixado.
8. **Se necessário, estorno:** uma baixa real pode ser revertida por um estorno, devolvendo
   o saldo ao título.

### 4.9 Validações

- Descontos acumulados não podem exceder valor original + acréscimos.
- Data de baixa não pode ser anterior à data de emissão do título (e, por política do
  tenant, pode ser proibida de ser retroativa em relação à data atual).
- Baixa com valor maior que o saldo disponível é rejeitada.
- Compensação exige mesma pessoa nos dois títulos e valor dentro do menor saldo.
- Um título bloqueado (hold) não aceita baixa nem entra em remessa de pagamento.

### 4.10 Telas

- **Formas de Pagamento** — regras de cálculo de vencimento.
- **Tipos de Título, Tipos de Ajuste, Tipos de Baixa, Classificações Financeiras, Motivos**
  — cadastros auxiliares que parametrizam o comportamento dos títulos.
- **Parâmetros Financeiros** — configurações do tenant (tipo de ajuste padrão para multa/
  mora/desconto, permissão de baixa retroativa, consideração de feriado bancário).
- **Lançamento de Título a Pagar / a Receber** — tela de criação manual e de visualização
  de títulos vindos de outras origens (nota fiscal, empréstimo etc.).
- **Listagem de Títulos** (pagar e receber) — filtros por vencimento, status, terceiro,
  tipo, classificação, valor, origem — com totalizadores.
- **Modal de Baixa** — registro de pagamento/recebimento, com ajustes embutidos (multa,
  mora, desconto sugeridos automaticamente em caso de atraso).
- **Modal de Parcelamento** — distribuição de um título em várias parcelas.
- **Tela de Compensação** — seleção de um título a pagar e um a receber do mesmo terceiro,
  com sugestões automáticas.
- **Adiantamentos Disponíveis** — saldo de adiantamento por fornecedor/cliente.
- **Assistente de Empréstimo/Leasing** — simulação e criação de parcelas.
- **Aprovações Pendentes** — decisão de aprovação por alçada (ver §2).

---

## 5. Módulo III — Fluxo de Caixa e Conciliação Bancária

### 5.1 Objetivo

Controlar as contas correntes do tenant, seus movimentos (manuais e originados de baixas de
títulos), conciliar automaticamente esses movimentos com o extrato bancário real, e
apresentar a visão de fluxo de caixa (realizado, previsto e orçado).

### 5.2 Conceitos

- **Conta corrente** — conta bancária (ou caixa, poupança, investimento) do tenant, com um
  saldo inicial em uma data de referência.
- **Movimentação** — um lançamento de crédito ou débito na conta corrente: pode vir de uma
  baixa de título, de uma transferência entre contas, de uma aplicação/resgate ou de um
  lançamento manual (tarifa bancária, por exemplo). Só entra no cálculo do saldo quando
  **confirmada**.
- **Extrato bancário** — as linhas do movimento real do banco, importadas de um arquivo
  (formato OFX) ou digitadas manualmente.
- **Conciliação** — o processo de casar cada linha do extrato bancário com a movimentação
  correspondente no sistema, garantindo que o saldo do sistema bata com o saldo real do
  banco.
- **Fluxo de caixa projetado** — combina o que já é realizado (movimentações confirmadas)
  com o que é previsto (títulos em aberto com vencimento no período) e, opcionalmente, o
  que foi orçado.

### 5.3 Regras de Negócio

| # | Regra |
|---|---|
| RN-CB-001 | O saldo de uma conta corrente nunca é um número fixo gravado — é sempre recalculado somando o saldo inicial com todas as movimentações **confirmadas**. |
| RN-CB-002 | A importação de um extrato bancário (arquivo OFX) é idempotente: reimportar o mesmo arquivo não duplica lançamentos — linhas já importadas são identificadas e ignoradas. |
| RN-CB-003 | A conciliação automática só efetiva o casamento quando encontra exatamente **uma** correspondência (mesmo tipo, valor dentro da tolerância, data próxima); se encontrar zero ou mais de uma correspondência possível, a linha fica pendente para conciliação manual. |
| RN-CB-004 | A tolerância de valor aceita na conciliação automática é configurável por tenant (padrão: R$ 0,05); a tolerância de data também é configurável (padrão: 3 dias úteis). |
| RN-CB-005 | Uma transferência entre duas contas correntes sempre gera dois lançamentos simultâneos (débito na origem, crédito no destino) — nunca existe um débito sem o crédito correspondente. |
| RN-CB-006 | Uma linha do extrato marcada como "ignorada" (tarifas, IOF ou lançamentos sem correspondência no sistema, que não precisam conciliação) não pode ser conciliada enquanto estiver nesse status — é preciso desfazer o "ignorado" primeiro. Ignorar uma linha exige uma justificativa. |
| RN-CB-007 | Quando a conciliação confirma uma movimentação vinculada a uma baixa **planejada**, essa baixa passa automaticamente para **real**. |
| RN-CB-008 | Uma movimentação vinculada a um título não pode ser cancelada diretamente — é preciso cancelar/estornar a baixa primeiro, e o cancelamento se propaga. |
| RN-CB-009 | O fluxo de caixa previsto usa a **data de vencimento** dos títulos em aberto, não a data de emissão. |
| RN-CB-010 | Uma necessidade de capital de giro negativa (ou seja, sobra de caixa projetada) não é um alerta de risco — é apenas informação; o sistema não bloqueia nenhuma operação com base nesse indicador. |

### 5.4 Fluxo — Integração entre baixa de título e movimentação bancária

Há dois pontos de integração:

**Ponto 1 — a baixa confirma o movimento:** quando uma baixa de título é confirmada (status
real), o sistema cria automaticamente a movimentação correspondente na conta corrente
informada, já nascendo confirmada.

**Ponto 2 — a conciliação confirma a baixa:** quando uma movimentação ainda pendente (baixa
ainda planejada) é conciliada com uma linha do extrato bancário, o sistema confirma a
movimentação **e**, junto, confirma a baixa do título associado — o saldo do título é
atualizado nesse momento.

### 5.5 Fluxo — Conciliação de extrato

1. Importa-se o arquivo do extrato bancário (ou insere-se manualmente uma linha).
2. Linhas já importadas anteriormente (mesmo documento, mesma data, mesma conta) são
   identificadas e ignoradas silenciosamente — sem duplicar.
3. Para cada linha nova, o sistema tenta encontrar automaticamente a movimentação
   correspondente no sistema (mesmo tipo, valor dentro da tolerância, data próxima).
4. Encontrando exatamente uma correspondência, concilia automaticamente. Encontrando
   ambiguidade (nenhuma ou mais de uma correspondência), deixa pendente para o operador
   conciliar manualmente, escolhendo a linha do extrato e a movimentação do sistema.
5. Linhas sem correspondência esperada (tarifas, por exemplo) podem ser marcadas como
   "ignoradas", com justificativa.
6. Um relatório de conciliação mostra o saldo do extrato do banco, o saldo do sistema, a
   diferença (idealmente zero), e a lista de pendências dos dois lados.

### 5.6 Validações

- Extrato e movimentação a conciliar devem ser da mesma conta corrente e do mesmo tenant.
- Tipos (crédito/débito) devem coincidir; valores devem estar dentro da tolerância
  configurada.
- Uma linha já conciliada não pode ser conciliada novamente sem antes desfazer a conciliação.
- Desfazer uma conciliação cujo movimento já gerou obrigação contábil pode não ser permitido
  (a reversão contábil precisa ser tratada separadamente).

### 5.7 Telas

- **Bancos e Contas Correntes** — cadastro.
- **Extrato por Período** — consulta com saldo acumulado linha a linha.
- **Conciliação Bancária** — dois painéis lado a lado (extrato do banco / movimentações do
  sistema), com indicação visual de conciliado/pendente e ação de vincular manualmente.
- **Fluxo de Caixa** — gráfico e tabela comparando realizado, previsto e orçado, com
  agrupamento diário/semanal/mensal.
- **Posição de Caixa Atual** — resumo do saldo disponível e pendente por conta.
- **Necessidade de Capital de Giro** — indicador informativo (não bloqueante).
- **Orçamento Financeiro** — lançamento de valores orçados por mês/conta/classificação, com
  comparação orçado x realizado.

---

## 6. Módulo IV — Tesouraria

> Cobre emissão de boletos, cobrança via PIX, geração e leitura de arquivos bancários
> (remessa/retorno — "CNAB"), débito direto autorizado (DDA), controle de cheques e
> aplicações financeiras.

### 6.1 Objetivo

Operacionalizar os meios de recebimento e pagamento do tenant junto aos bancos: emitir
cobrança (boleto ou PIX), processar a comunicação de arquivos com o banco para cobrança e
pagamento em lote, importar boletos recebidos via DDA, controlar cheques emitidos/recebidos
e aplicações financeiras.

### 6.2 Conceitos

- **Configuração de cobrança** — parâmetros por conta corrente para emissão de boletos:
  código do cedente junto ao banco, carteira, modalidade, numeração sequencial exclusiva
  ("nosso número"), prazos para protesto e negativação, e o layout de arquivo bancário
  usado.
- **Boleto** — cobrança emitida vinculada a um título a receber, com código de barras e
  linha digitável, percentuais próprios de multa/mora/desconto, e status (emitido,
  registrado no banco, pago, cancelado, vencido).
- **Cobrança PIX (QR dinâmico)** — alternativa de cobrança instantânea vinculada a um título
  a receber; ao ser paga, gera automaticamente a baixa do título.
- **Arquivo de remessa** — arquivo enviado ao banco contendo instruções (cobrança ou
  pagamento em lote).
- **Arquivo de retorno** — arquivo recebido do banco informando o que ocorreu com cada
  título enviado (pago, alterado, protestado etc.) — as baixas geradas a partir do retorno
  nascem sempre como planejadas, aguardando confirmação.
- **DDA (Débito Direto Autorizado)** — mecanismo em que boletos de outros emissores (que o
  tenant deve pagar) chegam automaticamente ao sistema via banco, sem precisar do código de
  barras físico; o operador vincula manualmente cada boleto DDA ao título a pagar
  correspondente — ou, quando o título ainda não existe, gera um novo título a pagar a
  partir do próprio boleto DDA (ação igualmente manual, nunca automática).
- **Cheque** — controle de cheques emitidos e recebidos, com status (emitido, compensado,
  devolvido, cancelado, sustado) e alerta quando a data de "bom para" se aproxima.
- **Aplicação financeira** — controle de recursos aplicados (CDB, LCI, LCA, fundos),
  permitido apenas em contas do tipo investimento, com registro de resgate (valor
  resgatado, rendimento bruto, imposto de renda retido, rendimento líquido).

### 6.3 Estratégia de arquivo bancário (CNAB)

O sistema adota o layout padrão nacional (CNAB 240) como motor principal, tratando as
diferenças específicas de cada banco como pequenos ajustes pontuais sobre esse padrão — já
que a maior parte da estrutura do arquivo é comum a todos os bancos; o que varia é o formato
do número de identificação do título junto ao banco, os códigos de carteira/modalidade e
alguns códigos de ocorrência no retorno. O layout mais antigo (CNAB 400), que não é
padronizado entre bancos, só é implementado quando algum cliente exigir um banco específico.

### 6.4 Regras de Negócio

| # | Regra |
|---|---|
| RN-TES-001 | A numeração sequencial usada para identificar cada boleto junto ao banco ("nosso número") é exclusiva por conta de cobrança e nunca pode se repetir — mesmo com dois usuários emitindo boletos ao mesmo tempo. |
| RN-TES-002 | Uma aplicação financeira só pode ser feita em conta corrente do tipo investimento. |
| RN-TES-003 | Um boleto vinculado a uma remessa bancária não pode ter a emissão desfeita — só pode ser cancelado ou seguir o fluxo normal de pagamento/vencimento. |
| RN-TES-004 | Uma baixa originada de um retorno bancário nasce sempre como **planejada**, exigindo confirmação (a confirmação normalmente ocorre pela conciliação bancária do valor efetivamente creditado). |
| RN-TES-005 | Um boleto recebido via DDA só é baixado no título a pagar depois que o operador o vincula manualmente ao título correspondente. Quando o título ainda não existe, o operador pode gerar um novo título a pagar a partir do boleto DDA (com o cedente como fornecedor) — também ação manual; nada é criado ou baixado automaticamente. |
| RN-TES-006 | O sistema alerta automaticamente sobre cheques cuja data de "bom para" (data a partir da qual o cheque pode ser depositado) se aproxima. |
| RN-TES-007 | Uma cobrança via PIX que expira sem ser paga não gera baixa; o pagamento fora do prazo de validade da cobrança não é reconhecido automaticamente por aquele código — é preciso gerar nova cobrança. |
| RN-TES-008 | A partir de 2027, cobranças via PIX (e demais meios eletrônicos, incluindo boleto liquidado por arranjo de pagamento) ficam sujeitas ao split payment (ver §3, RN-MF-005) — a segregação do valor do imposto ocorre no momento da liquidação. |
| RN-TES-009 | **Rejeição de boleto pelo banco:** quando o retorno bancário traz uma ocorrência de rejeição (ex.: CPF/CNPJ inválido do sacado), o sistema registra o código e o motivo, gera um alerta crítico para o operador (que permanece numa fila até ser tratado) e devolve o boleto ao estado de "emitido" para correção e reemissão. O título continua em aberto — o problema é do boleto, ele nunca trava o recebível. |
| RN-TES-010 | **Liquidação parcial de boleto:** quando o retorno bancário informa pagamento parcial, o sistema gera uma baixa parcial pelo valor recebido; o título permanece em aberto com o saldo residual, e o boleto pode ser reapresentado ao banco pelo saldo remanescente. |

### 6.5 Fluxo — Cobrança PIX

1. Uma cobrança PIX é criada para um título a receber, gerando um código (QR/copia-e-cola)
   com prazo de validade.
2. Quando o pagamento é recebido, o sistema localiza a cobrança correspondente, cria a baixa
   do título já confirmada e a movimentação bancária correspondente — de forma que reprocessar
   a mesma notificação de pagamento não gera baixa duplicada.
3. A conciliação bancária reforça essa correspondência ao comparar com o extrato.

### 6.6 Fluxo — Emissão de boleto e ciclo CNAB (visão de negócio)

1. O título a receber é emitido, gerando o boleto com código de barras e linha digitável.
2. O boleto entra em uma remessa enviada ao banco.
3. O banco processa e devolve um arquivo de retorno informando o que aconteceu com cada
   título (pago, alterado, protestado, cancelado etc.).
4. Cada ocorrência do retorno gera uma baixa planejada, que é confirmada assim que o
   dinheiro é identificado na conciliação bancária. Uma ocorrência de **rejeição** (RN-TES-009)
   não gera baixa — devolve o boleto para reemissão. Uma ocorrência de **pagamento parcial**
   (RN-TES-010) gera baixa parcial, deixando o título em aberto pelo saldo residual.

### 6.7 Cron jobs (rotinas automáticas relevantes ao negócio)

- Verificação diária de boletos vencidos.
- Verificação diária de aplicações financeiras vencidas.
- Verificação diária de cheques a compensar.
- Verificação diária de cobranças PIX expiradas.

### 6.8 Validações

- Aplicação financeira restrita a contas do tipo investimento.
- Nosso número exclusivo por conta de cobrança — sem duplicidade mesmo sob concorrência.
- Boleto vinculado a remessa não permite desfazer emissão.
- Vínculo de boleto DDA ao título a pagar é sempre manual, nunca automático.

### 6.9 Telas

- **Configurações → Cobrança** — parâmetros de emissão de boleto por conta corrente.
- **Emissão de Boleto** — geração de cobrança para título a receber.
- **Cobrança PIX** — geração de QR code para título a receber.
- **Remessa e Retorno CNAB** — geração de arquivo de remessa e importação/processamento do
  retorno do banco.
- **Importação DDA** — lista de boletos recebidos via DDA aguardando vínculo manual ao
  título a pagar.
- **Cheques** — registro e controle de cheques emitidos/recebidos.
- **Aplicações Financeiras** — registro de aplicação e de resgate.

---

## 7. Módulo V — Contabilidade e Livro-Razão (GL)

### 7.1 Objetivo

Registrar automaticamente, em partidas dobradas (débito/crédito), os eventos financeiros
gerados pelos módulos anteriores (baixa de título, movimentação bancária confirmada,
apuração fiscal fechada, empréstimo, resgate de aplicação), permitir lançamento manual
quando necessário, e produzir as demonstrações financeiras (Balanço Patrimonial, DRE, Razão,
Livro Diário).

### 7.2 Conceitos

- **Plano de contas** — estrutura hierárquica de contas contábeis (Ativo, Passivo,
  Patrimônio Líquido, Receitas, Custos, Despesas), copiada de um modelo oficial na ativação
  do tenant e editável a partir daí (ver regras de imutabilidade em §2).
- **Período contábil** — competência mensal que pode estar aberta, fechada ou bloqueada.
  Um período fechado não aceita novo lançamento.
- **Lançamento contábil** — o registro de um evento financeiro em partidas de débito e
  crédito. A soma dos débitos deve sempre ser igual à soma dos créditos.
- **Mapeamento contábil** — a configuração de "de/para" entre entidades financeiras (conta
  corrente, tipo de baixa, tipo de ajuste, classificação financeira, tributo) e as contas
  contábeis correspondentes — necessária para que o sistema saiba automaticamente em qual
  conta lançar cada tipo de evento.
- **Estabelecimento (matriz/filial)** — dimensão que permite lançar e fechar período por
  filial individualmente, além do consolidado do grupo. O plano de contas é compartilhado
  entre as filiais — o estabelecimento é uma dimensão do lançamento, não do plano de contas.

### 7.3 Regras de Negócio

| # | Regra |
|---|---|
| RN-CONT-001 | A soma dos débitos deve ser sempre igual à soma dos créditos em cada lançamento contábil. |
| RN-CONT-002 | Um período contábil fechado não aceita nenhum lançamento novo — para corrigir, é preciso reabrir o período (se permitido) ou lançar no período seguinte. |
| RN-CONT-003 | É possível fechar o período de uma filial individualmente antes do fechamento consolidado do grupo. |
| RN-CONT-004 | O plano de contas é compartilhado entre todas as filiais de um mesmo tenant — não existe um plano de contas por filial; a filial é apenas uma dimensão de análise dentro do lançamento. |
| RN-CONT-005 | Balanço Patrimonial e DRE podem ser gerados de forma consolidada (todas as filiais juntas) ou individual (por filial). |
| RN-CONT-006 | Contas retificadoras (como a de depreciação acumulada ou a de provisão para devedores duvidosos) reduzem o saldo do grupo de contas ao qual pertencem na apresentação do Balanço. |
| RN-CONT-007 | Ver também as regras de imutabilidade do plano de contas (RN-FUND-008 a RN-FUND-012, em §2). |
| RN-CONT-008 | O estorno de uma baixa confirmada gera automaticamente um lançamento contábil de **reversão**: as mesmas contas do lançamento original, com débito e crédito invertidos. O lançamento original é marcado como "estornado" e nunca é apagado. Se o original pertence a um período já fechado, a reversão é lançada na competência aberta atual, com histórico referenciando o lançamento e a competência originais — o período fechado permanece intacto. |
| RN-CONT-009 | Um evento financeiro cuja competência cai em período contábil **fechado** não gera lançamento naquele período. O tratamento é configurável por empresa: **lançar automaticamente na competência aberta atual** (padrão, com histórico referenciando a competência original) ou **reter o evento como pendente** até eventual reabertura do período — nesse caso, eventos pendentes impedem um novo fechamento até serem tratados. |

### 7.4 Estrutura do modelo de plano de contas (elenco oficial de partida)

O modelo de partida segue a estrutura clássica: **1. Ativo · 2. Passivo (com o Patrimônio
Líquido sob o grupo 2.4) · 3. Receitas · 4. Custos · 5. Despesas e Demais Resultados**,
já incluindo contas específicas para os novos tributos da reforma (IBS/CBS a recuperar
e a recolher, IS a recolher) convivendo com as contas dos tributos atuais durante a
transição (ICMS, ISS, PIS/Cofins). Itens setoriais muito específicos não entram no modelo
padrão — cada tenant pode adicioná-los à própria cópia, se necessário.

### 7.5 Demonstrações previstas

- **Razão Contábil** — pode ser filtrado por estabelecimento (visão por filial) ou
  consolidado.
- **Balanço Patrimonial** — consolidado (todas as filiais) ou individual por estabelecimento.
- **DRE por Competência** — idem.
- **Livro Diário** — sequência cronológica e numerada sem lacunas dos lançamentos.
- **Fechamento Anual**.
- **Conciliação entre o Livro-Razão e os sub-livros** (contas a pagar/receber, banco) —
  garante que o saldo contábil bate com o saldo operacional de cada módulo.

### 7.6 Validações

- Lançamento contábil desbalanceado (débito ≠ crédito) é rejeitado.
- Lançamento em período fechado é rejeitado.
- Conta sem permissão de lançamento (conta "sintética", apenas agrupadora na hierarquia)
  não aceita lançamento direto.

### 7.7 Telas

- **Plano de Contas** — consulta e edição da cópia do tenant.
- **Mapeamento Contábil** — configuração de/para entre entidades financeiras e contas.
- **Períodos Contábeis** — consulta de status e fechamento (pelo contador ou responsável).
- **Razão, Balanço, DRE, Livro Diário** — telas de consulta/exportação das demonstrações.

---

## 8. Módulo VI — Análises Gerenciais e Relatórios

### 8.1 Objetivo

Fornecer visão analítica e indicadores de gestão financeira, sem originar nenhum lançamento
— é um módulo somente de leitura sobre os dados de todos os módulos anteriores.

### 8.2 Conceitos

- **Aging (posição por faixa de vencimento)** — agrupamento de títulos em aberto por faixa
  de atraso ou de vencimento futuro.
- **Inadimplência** — indicador de quanto do total a receber está vencido e não pago.
- **PDD (Provisão para Devedores Duvidosos)** — provisão contábil calculada mensalmente por
  faixa de aging, configurável por tenant (percentuais sugeridos de partida: 0,5% para não
  vencido, 3% para até 30 dias, 8% para 31–60 dias, 20% para 61–90 dias, 50% para acima de 90
  dias). É uma **estimativa de perda**, não uma baixa de título: o total apurado alimenta um
  lançamento contábil de despesa contra uma conta redutora do ativo (quando o tenant opta por
  contabilizar a provisão — ver §7), sem afetar o saldo em aberto de nenhum título individual.
- **KPIs financeiros** — Giro de Recebíveis, Prazo Médio de Recebimento (PMR), Prazo Médio
  de Pagamento (PMP), Ciclo Financeiro, Taxa de Inadimplência.
- **Dashboard executivo** — visão consolidada de posição de caixa, recebíveis, pagáveis,
  fluxo de caixa e alertas.

### 8.3 Regras de Negócio

| # | Regra |
|---|---|
| RN-GER-001 | O percentual de provisão para devedores duvidosos é aplicado por faixa de atraso (aging), configurável por tenant — quanto maior o atraso, maior o percentual provisionado. |
| RN-GER-002 | Este módulo não grava nenhum dado transacional — apenas consulta e agrega informações já registradas pelos demais módulos. |

### 8.4 Telas

- **Aging** — posição de títulos por faixa de vencimento/atraso.
- **Inadimplência** — indicadores e listagem de títulos vencidos.
- **Posição de Títulos** — visão consolidada de pagar/receber.
- **Provisão para Devedores Duvidosos** — configuração de percentuais por faixa e cálculo
  automático.
- **Dashboard Executivo** — indicadores consolidados e alertas.

---

## 9. Perfis e Alçadas de Aprovação

O controle de quem pode fazer o quê (permissões) é administrado fora do módulo Financeiro,
em um serviço de autenticação e controle de acesso central — o Financeiro não mantém sua
própria tabela de perfis; ele consulta as permissões do usuário logado a cada operação. Do
ponto de vista de negócio, os papéis relevantes são:

| Papel (exemplo) | Responsabilidade |
|---|---|
| Operador financeiro | Lança títulos, registra baixas, faz conciliação, emite boletos. |
| Aprovador de nível 1 (ex.: gerente financeiro) | Aprova pagamentos até determinada faixa de valor, configurável por tenant. |
| Aprovador de nível 2 (ex.: diretor financeiro) | Aprova pagamentos acima da faixa do nível 1, ou recebe o pedido escalado quando o aprovador de nível 1 não decide a tempo. |
| Contador / responsável contábil | Fecha períodos contábeis, edita o plano de contas, valida demonstrações. |
| Administrador do tenant | Configura parâmetros financeiros, formas de pagamento, alçadas de aprovação, cadastros auxiliares. |

As faixas de valor, os papéis responsáveis por cada faixa, o prazo de decisão e o que
acontece se o prazo estourar (escalar ou aprovar automaticamente) são configuráveis
livremente por cada tenant — não são fixos no sistema (ver §2.4).

---

## 10. Telas e Menus

Este módulo separa dois grupos de configuração:

### 10.1 Mantidas pela equipe da plataforma (o tenant não edita)

- Tabelas fiscais de referência oficiais: NCM, código de situação tributária do IBS/CBS,
  classificações tributárias granulares, códigos de crédito presumido, alíquotas de CBS por
  regime e ano, alíquotas de IBS por município e ano, alíquotas do Imposto Seletivo,
  regimes diferenciados por NCM/serviço, cronograma de vigência da transição tributária.
- Feriados nacionais e estaduais de base.
- Modelo (template) do plano de contas, com controle de versão.

### 10.2 Configuradas pelo próprio tenant, dentro do ERP

| Área | Tela | O que o usuário faz |
|---|---|---|
| Fiscal | Dados Fiscais da Empresa | Define regime tributário, código de regime, inscrições, opção pelo Simples. |
| Financeiro | Parâmetros Financeiros | Define tipo de ajuste padrão, tolerância de conciliação, regras de baixa retroativa. |
| Cadastros | Formas de Pagamento | Regras de cálculo de vencimento. |
| Cadastros | Tipos de Título / Ajuste / Baixa | Parametrização do comportamento dos títulos. |
| Cadastros | Classificações Financeiras | Agrupamento livre para relatórios. |
| Cadastros | Motivos | Justificativas de cancelamento/prorrogação/parcelamento. |
| Cadastros | Centros de Custo e Rateios | Estrutura analítica por área/projeto. |
| Cadastros | Bancos e Contas Correntes | Cadastro bancário do tenant. |
| Configurações | Cobrança | Parâmetros de emissão de boleto por conta. |
| Configurações | Feriados Municipais | Feriados específicos da praça do tenant. |
| Cadastros | Estabelecimentos/Filiais | Matriz (criada automaticamente na ativação) e filiais. |
| Cadastros | Plano de Contas | Cópia editável do modelo oficial. |
| Configurações | Mapeamento Contábil | De/para entre eventos financeiros e contas contábeis. |
| Contabilidade | Períodos | Consulta de status e fechamento. |
| Fluxo de Caixa | Orçamento | Lançamento de valores orçados. |
| Configurações | Provisão para Devedores Duvidosos | Percentuais por faixa de aging. |

### 10.3 Telas geradas automaticamente pela operação (só consulta, sem cadastro direto)

Título, baixa, ajuste, movimentação bancária, extrato importado, boleto, arquivos de
remessa/retorno, boletos DDA, cheques, aplicações financeiras, empréstimos, compensações,
saldos de adiantamento, resultado da apuração fiscal, apuração mensal, lançamentos
contábeis e trilha de auditoria — todos criados/atualizados automaticamente pelas operações
descritas nos módulos acima.

---

## 11. Pontos em Aberto / Decisões Pendentes

Itens que a Fin.md registra explicitamente como **fora do escopo desta revisão** ou
**dependentes de fatores externos** — não implementados nem detalhados nesta spec.

| Item | Situação |
|---|---|
| **Alíquotas de IBS por município** | Único bloqueante real para colocar o motor fiscal em produção — depende da publicação oficial do Comitê Gestor do IBS (CGIBS). Enquanto isso, o sistema usa um valor de teste para 2026. |
| **Alíquotas do Imposto Seletivo** | A lista de produtos sujeitos ao IS já está mapeada, mas os percentuais exatos ainda aguardam regulamentação. |
| **SPED / EFD-Contribuições, ECD, ECF, EFD ICMS-IPI** | Decisão de escopo tomada (ver §3.10): dispensados para tenant do Simples Nacional (MVP); viram Fase 2 para tenants Lucro Presumido/Real. A fundação contábil (Livro Diário sem lacunas, fechamento imutável, de-para opcional com plano referencial da RFB) já nasce pronta para essa evolução. |
| **DCTFWeb e geração de DARF** | Fora do escopo — é consequência automática do que já foi declarado via EFD-Reinf/eSocial no portal da Receita; as guias geradas hoje cobrem apenas o recolhimento operacional (títulos a pagar). |
| **Nova declaração de IBS/CBS** | Obrigação acessória ainda não definida pelo órgão gestor — fora do escopo. |
| **Módulo fiscal legado (ICMS/ISS/PIS/Cofins)** | Tratado apenas como convivência durante a transição; o detalhamento operacional completo (livros fiscais do regime atual) é uma spec separada. |
| **Faturamento recorrente (assinatura/contrato) como origem automática de título a receber** | Reservado para uma fase futura — não entra nesta versão. |
| **Taxas de adquirência de cartão e agenda de recebíveis no contas a receber** | Fora do desenho aprovado até o momento — marcado como possível evolução futura. |
| **Entrada de nota fiscal por múltiplos canais** (portal do fornecedor, leitura óptica de PDF, troca eletrônica de dados) | Fora de escopo desta versão — a entrada de nota fiscal continua sendo automática (via integração eletrônica) ou manual. |
| **Conferência cruzada entre pedido de compra, recebimento físico e nota fiscal, com bloqueio automático de pagamento em caso de divergência** | Depende de um futuro módulo de Compras; hoje o bloqueio de pagamento existe, mas é sempre acionado manualmente. |
| **Detalhamento campo a campo dos arquivos bancários (CNAB) por banco** | O padrão nacional (FEBRABAN 240) está detalhado campo a campo na spec técnica, com banco piloto definido e plano de homologação; bancos adicionais entram como ajustes pontuais sob demanda, sempre com homologação prévia. |
| **Revisão formal do plano de contas por um contador** | Hoje é opcional/qualidade — não é pré-requisito para a operação. Recomendável ainda assim, antes de uso extensivo em produção. |
| **Split payment — mecânica definitiva** | O modelo adotado ("split inteligente") é uma simplificação a ser confrontada com a regulamentação final do Comitê Gestor antes de 2027. |
| **Zona Franca de Manaus** | Regra de benefício fiscal descrita em alto nível — caso de alta especificidade que precisa de validação de especialista antes de entrar em produção. |

---

## 12. Cenários de Aceitação (exemplos numéricos)

> Cenários de referência para validação de negócio e para os testes de aceitação. Cada um
> exercita as regras indicadas entre parênteses, com valores concretos. Percentuais de multa,
> mora e PDD usam os padrões sugeridos — em produção valem os configurados pelo tenant.

**CA-01 — Baixa em atraso com multa e mora (RN-AP-012).** Título de R$ 1.000,00 vence em
10/03; é pago em 09/04 (30 dias de atraso). O sistema sugere multa de 2% = R$ 20,00 e mora de
1% a.m. proporcional (30/30 dias) = R$ 10,00 → total sugerido R$ 1.030,00. O operador pode
ajustar antes de confirmar.

**CA-02 — Estorno de baixa confirmada (RN-AP-011).** Título de R$ 500,00 baixado
integralmente (baixa real) → status Baixado. O estorno cria uma **nova** baixa de −R$ 500,00
(a original é preservada); o saldo volta a R$ 500,00 e o título retorna a Em Aberto. A baixa
de estorno não pode ser estornada.

**CA-03 — Um recebimento quita vários títulos (RN-AR-007, RN-AR-005).** O cliente paga
R$ 1.700,00 de uma vez; há três títulos em aberto: A R$ 600,00 (venc. 10/01), B R$ 700,00
(10/02), C R$ 300,00 (10/03). Alocação por vencimento: A 600 + B 700 + C 300 = R$ 1.600,00 —
uma baixa por título. A sobra de R$ 100,00 vira adiantamento (crédito) do cliente.

**CA-04 — Baixas parciais sucessivas (RN-AR-008).** Título de R$ 1.000,00 vencido em 10/01.
Primeira baixa parcial de R$ 400,00 em 20/01: mora calculada sobre o saldo de R$ 1.000,00
(1% × 10/30 = R$ 3,33). Segunda baixa em 19/02 quitando o restante: mora calculada sobre o
**saldo residual** de R$ 600,00 na data (1% × 40/30 = R$ 8,00) — nunca sobre o valor
original. O título só passa a Baixado quando o saldo zera.

**CA-05 — Rateio com diferença de centavos (RN-FUND-017).** Rateio 33,33% TI / 33,33% Vendas
/ 33,34% Adm sobre título de R$ 50,00: as parcelas arredondadas dariam 16,67 + 16,67 + 16,67
= R$ 50,01. A diferença de −R$ 0,01 é absorvida pelo centro de maior percentual (Adm) →
16,67 + 16,67 + **16,66** = R$ 50,00 exato.

**CA-06 — Baixa por adiantamento sem desconto duplicado (RN-AP-007, RN-AP-013).** Fornecedor
tem adiantamento de R$ 200,00. Título de R$ 1.000,00 recebe baixa por adiantamento de
R$ 200,00 → saldo passa a R$ 800,00 (não R$ 600,00 — nenhum desconto adicional é criado) e o
saldo de adiantamento zera. O adiantamento não pode baixar título de outro fornecedor.

**CA-07 — Compensação parcial (RN-CO-002, RN-CO-003).** Mesmo CNPJ: título a pagar de
R$ 800,00 e a receber de R$ 500,00 → compensável no máximo R$ 500,00. Uma compensação parcial
de R$ 300,00 deixa R$ 500,00 em aberto no pagar e R$ 200,00 no receber.

**CA-08 — Retenções na fonte no pagamento (RN-AP-014, RN-MF-018).** NFS-e de R$ 10.000,00 de
serviço com cessão de mão de obra, prestador PJ: INSS 11% = R$ 1.100,00 (competência pela
**emissão** da nota) e IRRF 1,5% = R$ 150,00 (competência pelo **pagamento**). O fornecedor
recebe R$ 8.750,00; o título fecha com 8.750 + 1.100 + 150 = R$ 10.000,00. Os valores retidos
viram obrigação do tenant, agrupados na guia mensal por tributo/código de receita, e entram no
relatório de apoio à EFD-Reinf.

**CA-09 — Retenção sofrida no recebimento (RN-AR-006).** Nota de R$ 2.000,00; o cliente retém
IRRF 1,5% = R$ 30,00 e deposita R$ 1.970,00. O título fecha com a baixa de R$ 1.970,00 mais
uma baixa "por retenção" de R$ 30,00 — que vira crédito tributário a recuperar (o caixa nunca
recebe esses R$ 30,00).

**CA-10 — Trava de competência retroativa (RN-AP-015).** Maio/2026 está fechado. Em
07/07/2026 um operador (mesmo administrador) tenta registrar uma baixa com data 15/05/2026 →
**bloqueado**. A correção é feita por lançamento de ajuste na competência aberta (julho); o
período fechado permanece imutável.

**CA-11 — Split payment na liquidação (RN-MF-005, §3.7).** Venda de R$ 1.000,00 paga via PIX
em 2027, com IBS+CBS destacados de R$ 264,20. O título é baixado pelo **bruto** (R$ 1.000,00 →
Baixado); o caixa recebe o **líquido** R$ 735,80; os R$ 264,20 retidos vão para a conta
transitória de tributos recolhidos na liquidação. A conciliação bancária casa exatamente com
R$ 735,80; o acerto da transitória ocorre na apuração mensal.

**CA-12 — Boleto rejeitado pelo banco (RN-TES-009).** O retorno bancário traz rejeição (CPF
do sacado inválido): o boleto volta a Emitido, um alerta crítico entra na fila até ser
tratado, e o **título permanece Em Aberto** — o problema é do boleto, nunca trava o recebível.
Após corrigir o cadastro, o operador reemite.

**CA-13 — Liquidação parcial de boleto (RN-TES-010).** Boleto de R$ 1.000,00; o retorno
informa pagamento de R$ 600,00 → baixa parcial planejada de R$ 600,00 (vira real na
conciliação); o título fica em aberto com saldo de R$ 400,00 e o boleto pode ser reapresentado
pelo residual.

**CA-14 — PDD por faixa de aging (RN-GER-001).** Carteira a receber: R$ 10.000,00 não vencido
(0,5% → R$ 50,00), R$ 2.000,00 vencidos até 30 dias (3% → R$ 60,00), R$ 1.000,00 acima de 90
dias (50% → R$ 500,00). PDD total = R$ 610,00 → alimenta o lançamento de despesa contra a
conta redutora (quando o tenant contabiliza a provisão). **Nenhum título tem o saldo
alterado** — é estimativa de perda, não baixa.

**CA-15 — Cancelamento de nota com baixa efetiva (RN-FUND-007).** NF de saída gera 3 parcelas
de R$ 500,00; a parcela 1 já foi recebida (baixa real). O cancelamento da NF é **bloqueado** e
um alerta é enviado ao financeiro para tratamento manual (estorno/nota de crédito). Se nenhuma
baixa real existisse, as 3 parcelas seriam canceladas automaticamente.

**CA-16 — Reversão contábil do estorno (RN-CONT-008).** A baixa de R$ 500,00 gerou o
lançamento D Fornecedores / C Banco. O estorno da baixa gera automaticamente o lançamento
invertido D Banco / C Fornecedores de R$ 500,00; o lançamento original é marcado como
"estornado" (nunca apagado). Se o original está em competência já fechada, a reversão entra na
competência aberta atual com histórico referenciando o lançamento e a competência originais.

---

*Fim do documento.*
