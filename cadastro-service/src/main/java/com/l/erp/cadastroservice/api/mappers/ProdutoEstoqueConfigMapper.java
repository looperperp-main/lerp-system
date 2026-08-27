package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.ProdutoEstoqueConfigDTO;
import com.l.erp.cadastroservice.domain.ProdutoEstoqueConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProdutoEstoqueConfigMapper {

    // fornecedorPreferencial na entidade é o ProdutoFornecedor (linha produto↔fornecedor), não o
    // Fornecedor em si — sem o .fornecedor.id, o DTO devolvia o id da linha de vínculo, que não bate
    // com nenhuma option do dropdown de fornecedores no front (que é keyed por Fornecedor.id).
    @Mapping(target = "depositoId", source = "deposito.id")
    @Mapping(target = "fornecedorPreferencialId", source = "fornecedorPreferencial.fornecedor.id")
    ProdutoEstoqueConfigDTO toDto(ProdutoEstoqueConfig entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "produto", ignore = true) // Controlado no Service do Produto
    @Mapping(target = "deposito", ignore = true)
    @Mapping(target = "fornecedorPreferencial", ignore = true) // Instanciado no service
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastUpdatedBy", ignore = true)
    ProdutoEstoqueConfig toEntity(ProdutoEstoqueConfigDTO dto);
}