package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.VendedorDTO;
import com.l.erp.cadastroservice.api.dto.VendedorResponseDTO;
import com.l.erp.cadastroservice.domain.Vendedor;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = HtmlSanitizerUtil.class)
public interface VendedorMapper {

    @Mapping(source = "pessoa.id", target = "pessoaId")
    VendedorDTO toDto(Vendedor entity);

    @Mapping(target = "pessoa", ignore = true)
    Vendedor toEntity(VendedorDTO dto);

    List<VendedorDTO> toDtos(List<Vendedor> entities);

    List<Vendedor> toEntities(List<VendedorDTO> dtos);

    @Mapping(source = "pessoa.id", target = "pessoaId")
    VendedorResponseDTO toDtoResponse(Vendedor entity);
}
