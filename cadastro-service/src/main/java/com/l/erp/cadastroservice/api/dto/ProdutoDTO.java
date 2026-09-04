package com.l.erp.cadastroservice.api.dto;

import com.l.erp.cadastroservice.domain.enumerators.TipoProduto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProdutoDTO(
        UUID id,
        Long tenantId,
        UUID categoriaId,

        @NotBlank(message = "O SKU é obrigatório")
        @Size(max = 80)
        String sku,

        @Size(max = 80)
        String codigoExterno,

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 200)
        String nome,

        String descricao,

        // Nulo = MERCADORIA (default aplicado no ProdutoService, compatível com clientes
        // que ainda não enviam o campo).
        TipoProduto tipo,

        @NotBlank
        @Size(max = 10)
        String unidade,

        @Size(max = 10)
        String unidadeSecundaria,

        BigDecimal fatorConversao,

        @Size(max = 10)
        String ncm,

        @Size(max = 10)
        String codigoServico,

        // D4 (spec/o2c-vendas.md §8) — classificação tributária IBS/CBS do serviço (Anexo VIII);
        // obrigatório quando tipo=SERVICO (validado em ProdutoService.validarTipo).
        @Size(max = 10)
        String classTrib,

        @Size(max = 14)
        String ean,

        @Size(max = 10)
        String cest,

        @Size(max = 2)
        String origem,

        BigDecimal pesoBruto,
        BigDecimal pesoLiquido,
        BigDecimal altura,
        BigDecimal largura,
        BigDecimal comprimento,

        @NotNull
        Boolean ativo,

        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID lastUpdatedBy,

        // Relacionamentos aninhados (podem vir na criação/atualização)
        List<ProdutoPrecoDTO> precos,
        List<ProdutoFornecedorDTO> fornecedores,
        List<ProdutoEstoqueConfigDTO> estoqueConfigs
) {}
