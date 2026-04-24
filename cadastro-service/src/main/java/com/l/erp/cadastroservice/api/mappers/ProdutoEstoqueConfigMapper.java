package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.ProdutoEstoqueConfigDTO;
import com.l.erp.cadastroservice.domain.ProdutoEstoqueConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProdutoEstoqueConfigMapper {

    @Mapping(target = "fornecedorPreferencialId", source = "fornecedorPreferencial.id")
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