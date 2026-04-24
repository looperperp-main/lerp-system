package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.ProdutoDTO;
import com.l.erp.cadastroservice.api.dto.ProdutoResponseDTO;
import com.l.erp.cadastroservice.domain.Produto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {
        ProdutoCategoriaMapper.class,
        ProdutoPrecoMapper.class,
        ProdutoFornecedorMapper.class,
        ProdutoEstoqueConfigMapper.class
})
public interface ProdutoMapper {

    @Mapping(target = "categoriaId", source = "categoria.id")
    @Mapping(target = "precos", source = "produtoPrecos")
    @Mapping(target = "fornecedores", source = "produtoFornecedors")
    @Mapping(target = "estoqueConfigs", source = "produtoEstoqueConfigs")
    ProdutoDTO toDto(Produto produto);

    @Mapping(target = "categoriaId", source = "categoria.id")
    @Mapping(target = "precos", source = "produtoPrecos")
    @Mapping(target = "fornecedores", source = "produtoFornecedors")
    @Mapping(target = "estoqueConfigs", source = "produtoEstoqueConfigs")
    ProdutoResponseDTO toDtoResponse(Produto produto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "categoria", ignore = true) // Setado no service
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastUpdatedBy", ignore = true)
    @Mapping(target = "produtoEstoqueConfigs", ignore = true) // O gerenciamento das listas aninhadas deve ser feito no Service
    @Mapping(target = "produtoFornecedors", ignore = true)
    @Mapping(target = "produtoPrecos", ignore = true)
    Produto toEntity(ProdutoDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastUpdatedBy", ignore = true)
    @Mapping(target = "produtoEstoqueConfigs", ignore = true)
    @Mapping(target = "produtoFornecedors", ignore = true)
    @Mapping(target = "produtoPrecos", ignore = true)
    void updateEntityFromDto(ProdutoDTO dto, @MappingTarget Produto entity);
}
