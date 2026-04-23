package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.FornecedorDto;
import com.l.erp.cadastroservice.api.dto.FornecedorResponseDTO;
import com.l.erp.cadastroservice.domain.Fornecedor;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = HtmlSanitizerUtil.class)
public interface FornecedorMapper {
    @Mapping(source = "pessoa.id", target = "pessoaId")
    FornecedorDto toDto(Fornecedor entity);

    @Mapping(source = "pessoaId", target = "pessoa.id")
    Fornecedor toEntity(FornecedorDto dto);

    List<FornecedorDto> toDtos(List<Fornecedor> entities);

    List<Fornecedor> toEntities(List<FornecedorDto> dtos);

    @Mapping(source = "pessoa.id", target = "pessoaId")
    @Mapping(source = "pessoa.nomeRazao", target = "pessoaNomeRazao")
    FornecedorResponseDTO toDtoResponse(Fornecedor entity);
}
