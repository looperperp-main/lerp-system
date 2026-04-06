package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.GrupoClienteDTO;
import com.l.erp.cadastroservice.domain.GrupoCliente;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface GrupoClienteMapper {

    GrupoClienteDTO toDto(GrupoCliente entity);

    GrupoCliente toEntity(GrupoClienteDTO dto);

    List<GrupoClienteDTO> toDtos(List<GrupoCliente> entities);

    List<GrupoCliente> toEntities(List<GrupoClienteDTO> dtos);
}
