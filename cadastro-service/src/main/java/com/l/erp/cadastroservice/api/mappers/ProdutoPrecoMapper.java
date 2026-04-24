package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.ProdutoPrecoDTO;
import com.l.erp.cadastroservice.domain.ProdutoPreco;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProdutoPrecoMapper {

    @Mapping(target = "tabelaPrecoId", source = "tabelaPreco.id")
    ProdutoPrecoDTO toDto(ProdutoPreco produtoPreco);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "produto", ignore = true) // O produto root será vinculado no service
    @Mapping(target = "tabelaPreco", ignore = true) // A tabela será instanciada pelo ID no service
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastUpdatedBy", ignore = true)
    ProdutoPreco toEntity(ProdutoPrecoDTO dto);
}