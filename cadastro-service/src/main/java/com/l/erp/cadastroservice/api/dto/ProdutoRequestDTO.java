package com.l.erp.cadastroservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ProdutoRequestDTO {
    private UUID categoriaId;

    @NotBlank
    @Size(max = 80)
    private String sku;

    @Size(max = 80)
    private String codigoExterno;

    @NotBlank
    @Size(max = 200)
    private String nome;

    private String descricao;

    @NotBlank
    @Size(max = 10)
    private String unidade;

    @Size(max = 10)
    private String unidadeSecundaria;

    private BigDecimal fatorConversao;

    @Size(max = 10)
    private String ncm;

    @Size(max = 14)
    private String ean;

    @Size(max = 10)
    private String cest;

    @Size(max = 2)
    private String origem;

    private BigDecimal pesoBruto;

    private BigDecimal pesoLiquido;

    private BigDecimal altura;

    private BigDecimal largura;

    private BigDecimal comprimento;

    @NotNull
    private Boolean ativo;
}
