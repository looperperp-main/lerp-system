# Estoque: visão funcional

**Status:** decisões fechadas · **Data:** 8 de setembro de 2026 · **Módulo:** `operacoes-service` (microsserviço único, também dono de vendas e compras)

> Este documento descreve o controle de estoque em linguagem de negócio — sem schema de banco, endpoint ou detalhe técnico. Para a implementação, ver `spec/estoque.md`.

---

## Objetivo

Saber, a qualquer momento, quanto existe de cada produto em cada depósito — alimentado automaticamente pela saída de uma venda e pela entrada de uma compra, com um caminho manual (ajuste/contagem) para corrigir o saldo ou carregar o estoque inicial.

## O fluxo, passo a passo

```mermaid
flowchart LR
    V[Venda expedida] -->|baixa| S[(Saldo do produto)]
    C[Compra recebida] -->|entrada| S
    A[Ajuste ou inventário manual] -->|corrige| S
    S --> E[Extrato de movimentos]
    V -.-> S
```

### 1. Movimentação automática

Toda vez que uma venda é **expedida**, o sistema baixa do saldo a quantidade de cada item de mercadoria daquele pedido, no depósito informado na expedição. Se o pedido for cancelado depois de já ter sido expedido, o sistema devolve exatamente a mesma quantidade ao saldo (estorno) — nunca fica uma baixa sem par se a venda volta atrás.

O mesmo vale, na outra ponta, quando o módulo de compras confirma o **recebimento de mercadoria**: a quantidade recebida entra no saldo do depósito de destino, imediatamente e na mesma operação — não existe intervalo em que a mercadoria já chegou fisicamente mas o sistema ainda não sabe.

Item de **serviço nunca movimenta estoque** — não faz sentido "ter saldo" de uma prestação de serviço, então venda e compra de serviço não passam por aqui.

### 2. Ajuste e contagem de inventário

Além do que entra e sai sozinho pela venda ou compra, existe um caminho manual: o operador informa **o saldo que ele conta fisicamente na prateleira** — não a diferença, o número certo. O sistema calcula sozinho se isso significa uma entrada ou uma saída e registra o ajuste com o motivo, que é obrigatório para não virar um buraco de auditoria.

É o mesmo mecanismo tanto para o ajuste avulso ("achei duas unidades avariadas, tirando do saldo") quanto para a contagem cíclica periódica — o que muda é só o motivo que a pessoa digita, não a tela nem a regra.

Se a contagem bate com o saldo que o sistema já tinha, nada é gravado — contagem que confirma o saldo não é fato de estoque.

### 3. Extrato de movimentos

Toda entrada, saída, ajuste e estorno fica registrado permanentemente, sem exceção — o extrato nunca é editado ou apagado; qualquer correção é sempre um novo registro. É esse histórico completo que permite reconstruir "o que aconteceu com esse produto" a qualquer momento, e que sustenta a auditoria: quem fez, quando, e de onde veio o movimento (pedido de venda, recebimento de compra ou ajuste manual).

### 4. Bloqueio por saldo insuficiente (ainda desligado)

Hoje, vender mais do que o saldo mostra **não impede a venda** — o saldo simplesmente fica negativo, sinalizando que a operação vendeu algo que o sistema não sabia que tinha. Essa checagem já existe pronta no sistema, atrás de uma chave liga/desliga: assim que o saldo inicial de todos os produtos estiver carregado (via ajuste) e a tela de consulta/extrato estiver disponível para a operação acompanhar, a chave é ligada e passa a barrar a expedição ou o ajuste que deixaria algum produto negativo — sem precisar trocar de sistema nem esperar desenvolvimento novo. Estornos e entradas nunca são bloqueados, mesmo com a chave ligada — devolver e corrigir precisam sempre funcionar.

## Resumo das regras de negócio

| Situação | Regra |
|---|---|
| Venda expedida | Baixa automática do saldo, só de itens de mercadoria |
| Venda cancelada após expedida | Estorno automático (devolve o que foi baixado) |
| Compra recebida | Entrada automática no saldo do depósito, imediata |
| Item de serviço | Nunca movimenta estoque |
| Ajuste/inventário | Um único mecanismo: informa-se o saldo contado, o sistema calcula a diferença; motivo obrigatório |
| Contagem igual ao saldo atual | Nenhum movimento gravado (não é fato de estoque) |
| Saldo insuficiente para vender | Não bloqueia hoje (fica negativo); o bloqueio existe pronto, atrás de uma chave ainda desligada |
| Estorno/entrada com saldo negativo | Nunca bloqueado, mesmo com a chave de bloqueio ligada |
| Histórico de movimentos | Permanente e imutável — correção é sempre um novo registro, nunca edição |

## O que fica de fora por enquanto

- Bloqueio de venda por saldo insuficiente (a checagem existe, mas está desligada até o saldo inicial ser carregado).
- Reserva de saldo na confirmação do pedido (hoje a baixa só acontece na expedição).
- Custo médio ponderado / valorização de estoque (fica com o Financeiro, spec própria).
- Controle por lote, validade ou número de série.
- Transferência entre depósitos (hoje se resolve com dois ajustes manuais).
- Documento de inventário com etapas (abertura → contagem → apuração) — hoje é contagem direta, produto a produto.
- Expedição ou recebimento dividido entre múltiplos depósitos no mesmo documento.
- Devolução de cliente (RMA) com fluxo fiscal próprio.
- Sugestão automática de compra por ponto de reposição.
- Importação em massa de saldo inicial (planilha/CSV).
