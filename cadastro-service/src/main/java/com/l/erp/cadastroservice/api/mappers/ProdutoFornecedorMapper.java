package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.ProdutoFornecedorDTO;
import com.l.erp.cadastroservice.domain.ProdutoFornecedor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProdutoFornecedorMapper {

    @Mapping(target = "fornecedorId", source = "fornecedor.id")
    ProdutoFornecedorDTO toDto(ProdutoFornecedor entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "produto", ignore = true) // Controlado no Service do Produto
    @Mapping(target = "fornecedor", ignore = true) // Instanciado no service
    @Mapping(target = "produtoEstoqueConfigs", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastUpdatedBy", ignore = true)
    ProdutoFornecedor toEntity(ProdutoFornecedorDTO dto);
}
