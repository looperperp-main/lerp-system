package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoParcelaRequestDTO;
import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoParcelaResponseDTO;
import com.l.erp.cadastroservice.domain.CondicaoPagamentoParcela;
import com.l.erp.cadastroservice.util.HtmlSanitizerUtil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring",uses = HtmlSanitizerUtil.class)
public interface CondicaoPagamentoParcelaMapper {
    @Mapping(source = "condicaoPagamento.id", target = "condicaoPagamentoId")
    CondicaoPagamentoParcelaResponseDTO toResponseDto(CondicaoPagamentoParcela entity);

    @Mapping(source = "condicaoPagamentoId", target = "condicaoPagamento.id")
    CondicaoPagamentoParcela toEntity(CondicaoPagamentoParcelaRequestDTO dto);

    List<CondicaoPagamentoParcelaRequestDTO> toDtos(List<CondicaoPagamentoParcela> entities);

    List<CondicaoPagamentoParcela> toEntities(List<CondicaoPagamentoParcelaRequestDTO> dtos);
}
