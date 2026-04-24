package com.l.erp.cadastroservice.api.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ProdutoResponseDTO extends RepresentationModel<ProdutoResponseDTO> {
    private UUID id;
    private Long tenantId;
    private UUID categoriaId;
    private String sku;
    private String codigoExterno;
    private String nome;
    private String descricao;
    private String unidade;
    private String unidadeSecundaria;
    private BigDecimal fatorConversao;
    private String ncm;
    private String ean;
    private String cest;
    private String origem;
    private BigDecimal pesoBruto;
    private BigDecimal pesoLiquido;
    private BigDecimal altura;
    private BigDecimal largura;
    private BigDecimal comprimento;
    private Boolean ativo;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID createdBy;
    private UUID lastUpdatedBy;

    // Relacionamentos aninhados
    private List<ProdutoPrecoDTO> precos;
    private List<ProdutoFornecedorDTO> fornecedores;
    private List<ProdutoEstoqueConfigDTO> estoqueConfigs;
}
