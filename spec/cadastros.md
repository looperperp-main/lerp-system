classDiagram
direction BT
class cliente {
bigint tenant_id
uuid pessoa_id
varchar(50) sku  /* Código interno do cliente */
uuid condicao_pagamento_id
uuid grupo_cliente_id
uuid vendedor_id
numeric(15,2) limite_credito
varchar(10) classificacao_risco  /* BAIXO, MEDIO, ALTO */
integer prazo_medio_pagamento_dias
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class condicao_pagamento {
bigint tenant_id
varchar(100) nome
varchar(500) descricao
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class condicao_pagamento_parcela {
uuid condicao_pagamento_id
integer numero_parcela
integer dias
numeric(5,2) percentual
varchar(50) forma_pagamento  /* BOLETO, PIX, CARTAO_CREDITO, CARTAO_DEBITO, DINHEIRO, TRANSFE... */
timestamp(6) with time zone created_at
uuid created_by
uuid last_updated_by
timestamp(6) with time zone updated_at
uuid id
}
class contato {
bigint tenant_id
uuid pessoa_id
varchar(200) nome
varchar(20) tipo  /* COMERCIAL, FINANCEIRO, TECNICO, ADMINISTRATIVO, OUTRO */
varchar(100) cargo
varchar(200) email
varchar(20) telefone
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
boolean principal  /* Define se o contato é o contato principal da pessoa. */
uuid id
}
class deposito {
bigint tenant_id
varchar(100) nome
varchar(500) descricao
varchar(30) tipo  /* PRINCIPAL, FILIAL, TERCEIRO, TRANSITO */
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class endereco {
bigint tenant_id
uuid pessoa_id
varchar(20) tipo  /* FISCAL, COBRANCA, ENTREGA, PRINCIPAL */
varchar(200) logradouro
varchar(20) numero
varchar(100) complemento
varchar(100) bairro
varchar(100) cidade
varchar(2) uf
varchar(9) cep
varchar(10) ibge_codigo  /* Código IBGE do município — necessário para NF-e */
varchar(60) pais
boolean principal
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class fornecedor {
bigint tenant_id
uuid pessoa_id
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class grupo_cliente {
bigint tenant_id
varchar(100) nome
varchar(500) descricao
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class pessoa {
bigint tenant_id
varchar(2) tipo  /* PF = Pessoa Física, PJ = Pessoa Jurídica */
nome_razao  /* Nome (PF) ou Razão Social (PJ) */ varchar(200)
apelido_fantasia  /* Apelido (PF) ou Nome Fantasia (PJ) */ varchar(200)
documento  /* CPF (PF) ou CNPJ (PJ) */ varchar(18)
ie  /* Inscrição Estadual (PJ) */ varchar(20)
im  /* Inscrição Municipal (PJ) */ varchar(20)
rg  /* RG (PF) */ varchar(20)
data_nascimento  /* Data de nascimento (PF) ou data de fundação (PJ) */ date
varchar(200) email
varchar(20) telefone
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class produto {
bigint tenant_id
uuid categoria_id
varchar(80) sku
varchar(80) codigo_externo  /* Código no sistema do fornecedor ou código de barras adicional */
varchar(200) nome
text descricao
varchar(10) unidade  /* UN, KG, CX, MT, LT... */
unidade_secundaria  /* Unidade de medida alternativa (ex: CX com fator de conversão) */ varchar(10)
numeric(10,4) fator_conversao  /* Fator entre unidade principal e secundária */
varchar(10) ncm  /* Nomenclatura Comum do Mercosul — obrigatório NF-e */
varchar(14) ean  /* Código de barras EAN/GTIN */
varchar(10) cest  /* Código Especificador da Substituição Tributária */
origem  /* 0=Nacional, 1=Estrangeira importação direta, etc. (tabela ICMS) */ varchar(2)
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
numeric(10,4) peso_bruto  /* Peso bruto em KG — obrigatório NF-e */
numeric(10,4) peso_liquido  /* Peso líquido em KG — obrigatório NF-e */
numeric(10,4) altura  /* Altura em CM */
numeric(10,4) largura  /* Largura em CM */
numeric(10,4) comprimento  /* Comprimento em CM */
uuid id
}
class produto_categoria {
bigint tenant_id
varchar(100) nome
varchar(500) descricao
uuid categoria_pai_id  /* Hierarquia de categorias — self-reference */
boolean ativa
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class produto_estoque_config {
bigint tenant_id
uuid produto_id
uuid deposito_id
uuid fornecedor_preferencial_id  /* FK para produto_fornecedor — fornecedor padrão de reposição */
numeric(15,4) estoque_minimo
numeric(15,4) estoque_maximo
numeric(15,4) ponto_reposicao  /* Nível de estoque que dispara pedido de reposição */
integer lead_time_dias  /* Prazo médio de reposição para este produto neste depósito */
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class produto_fornecedor {
bigint tenant_id
uuid produto_id
uuid fornecedor_id
varchar(80) codigo_produto_fornecedor  /* Código do produto no catálogo do fornecedor */
numeric(15,4) preco_custo
integer lead_time_dias  /* Prazo de entrega do fornecedor em dias */
boolean preferencial
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class produto_preco {
bigint tenant_id
uuid produto_id
uuid tabela_preco_id
numeric(15,4) preco
date inicio_vigencia
date fim_vigencia
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class tabela_preco {
bigint tenant_id
varchar(100) nome
varchar(3) moeda  /* ISO 4217: BRL, USD, EUR... */
boolean ativa
boolean padrao  /* Indica se é a tabela padrão para novos clientes */
date inicio_vigencia
date fim_vigencia
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class tabela_preco_grupo_cliente {
bigint tenant_id
uuid tabela_preco_id
uuid grupo_cliente_id
}
class transportadora {
bigint tenant_id
uuid pessoa_id
varchar(20) rntrc  /* Registro Nacional de Transportadores Rodoviários de Cargas */
varchar(20) modal  /* RODOVIARIO, AEREO, FERROVIARIO, MARITIMO */
boolean ativo
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}
class vendedor {
bigint tenant_id
uuid pessoa_id
varchar(100) nome
boolean ativo
numeric(5,2) comissao_percentual
timestamp created_at
timestamp updated_at
uuid created_by
uuid last_updated_by
uuid id
}

cliente  -->  condicao_pagamento : condicao_pagamento_id
cliente  -->  grupo_cliente : grupo_cliente_id
cliente  -->  pessoa : pessoa_id
cliente  -->  vendedor : vendedor_id
condicao_pagamento_parcela  -->  condicao_pagamento : condicao_pagamento_id
contato  -->  pessoa : pessoa_id
endereco  -->  pessoa : pessoa_id
fornecedor  -->  pessoa : pessoa_id
produto  -->  produto_categoria : categoria_id
produto_categoria  -->  produto_categoria : categoria_pai_id
produto_estoque_config  -->  deposito : deposito_id
produto_estoque_config  -->  produto : produto_id
produto_estoque_config  -->  produto_fornecedor : fornecedor_preferencial_id
produto_fornecedor  -->  fornecedor : fornecedor_id
produto_fornecedor  -->  produto : produto_id
produto_preco  -->  produto : produto_id
produto_preco  -->  tabela_preco : tabela_preco_id
tabela_preco_grupo_cliente  -->  grupo_cliente : grupo_cliente_id
tabela_preco_grupo_cliente  -->  tabela_preco : tabela_preco_id
transportadora  -->  pessoa : pessoa_id
vendedor  -->  pessoa : pessoa_id
