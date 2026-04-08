package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoDTO;
import com.l.erp.cadastroservice.domain.CondicaoPagamento;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface CondicaoPagamentoMapper {

    CondicaoPagamentoDTO toDto(CondicaoPagamento entity);

    CondicaoPagamento toEntity(CondicaoPagamentoDTO dto);

    List<CondicaoPagamentoDTO> toDtos(List<CondicaoPagamento> entities);

    List<CondicaoPagamento> toEntities(List<CondicaoPagamentoDTO> dtos);
}
