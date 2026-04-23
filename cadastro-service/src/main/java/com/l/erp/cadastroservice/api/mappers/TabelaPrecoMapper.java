package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.TabelaPrecoDTO;
import com.l.erp.cadastroservice.api.dto.TabelaPrecoResponseDTO;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = HtmlSanitizerUtil.class)
public interface TabelaPrecoMapper {

    TabelaPrecoDTO toDto(TabelaPreco entity);

    TabelaPreco toEntity(TabelaPrecoDTO dto);

    List<TabelaPrecoDTO> toDtos(List<TabelaPreco> entities);

    List<TabelaPreco> toEntities(List<TabelaPrecoDTO> dtos);

    TabelaPrecoResponseDTO toDtoResponse(TabelaPreco entity);
}