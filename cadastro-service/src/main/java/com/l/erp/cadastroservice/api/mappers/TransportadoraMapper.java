package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.TransportadoraDTO;
import com.l.erp.cadastroservice.api.dto.TransportadoraResponseDTO;
import com.l.erp.cadastroservice.domain.Transportadora;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = HtmlSanitizerUtil.class)
public interface TransportadoraMapper {

    @Mapping(source = "pessoa.id", target = "pessoaId")
    @Mapping(source = "pessoa.apelidoFantasia", target = "pessoaNomeRazao")
    TransportadoraDTO toDto(Transportadora entity);

    @Mapping(source = "pessoaId", target = "pessoa.id")
    Transportadora toEntity(TransportadoraDTO dto);

    List<TransportadoraDTO> toDtos(List<Transportadora> entities);
    List<Transportadora> toEntities(List<TransportadoraDTO> dtos);

    @Mapping(source = "pessoa.id", target = "pessoaId")
    @Mapping(source = "pessoa.apelidoFantasia", target = "pessoaNomeRazao")
    TransportadoraResponseDTO toDtoResponse(Transportadora entity);
}
