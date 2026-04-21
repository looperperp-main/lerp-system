package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.ClienteResponseDTO;
import com.l.erp.cadastroservice.domain.Cliente;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface ClienteMapper {

    ClienteResponseDTO toModel(Cliente dto);

    Cliente toEntity(ClienteResponseDTO dto);

    List<ClienteResponseDTO> toModel(List<Cliente> dtos);

    List<Cliente> toEntity(List<ClienteResponseDTO> dtos);
}
