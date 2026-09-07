package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.dto.ParcelaFaturamentoDTO;
import com.l.erp.operacoesservice.api.dto.PedidoItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoItemResponseDTO;
import com.l.erp.operacoesservice.api.dto.PedidoRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoResponseDTO;
import com.l.erp.operacoesservice.api.dto.PedidoStatusHistoricoDTO;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.PedidoStatusHistorico;
import com.l.erp.operacoesservice.services.vendas.PedidoService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Cópia de campos entidade <-> DTO (spec/o2c-vendas.md §5/§10, Fase 4). Links HATEOAS ficam no PedidoAssembler. */
@Mapper(componentModel = "spring")
public interface PedidoMapper {

    // PedidoRequestDTO.itens não tem contrapartida em Pedido (itens são PedidoItem à parte,
    // controller usa toItemEntities(...) pra isso) — MapStruct ignora silenciosamente.
    Pedido toEntity(PedidoRequestDTO dto);

    PedidoItem toItemEntity(PedidoItemRequestDTO dto);

    List<PedidoItem> toItemEntities(List<PedidoItemRequestDTO> dtos);

    @Mapping(target = "itens", ignore = true)
    @Mapping(target = "historico", ignore = true)
    @Mapping(target = "parcelas", ignore = true)
    PedidoResponseDTO toResponseDto(Pedido entity);

    PedidoItemResponseDTO toItemResponseDto(PedidoItem entity);

    List<PedidoItemResponseDTO> toItemResponseDtos(List<PedidoItem> entities);

    PedidoStatusHistoricoDTO toHistoricoDto(PedidoStatusHistorico entity);

    List<PedidoStatusHistoricoDTO> toHistoricoDtos(List<PedidoStatusHistorico> entities);

    List<ParcelaFaturamentoDTO> toParcelaDtos(List<PedidoService.ParcelaFaturamento> parcelas);
}
