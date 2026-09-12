package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.dto.CompraStatusHistoricoDTO;
import com.l.erp.operacoesservice.api.dto.PedidoCompraItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoCompraItemResponseDTO;
import com.l.erp.operacoesservice.api.dto.PedidoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.PedidoCompraResponseDTO;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Cópia de campos entidade <-> DTO (spec/p2p-compras.md, Fase 2). Links HATEOAS ficam pro Assembler. */
@Mapper(componentModel = "spring")
public interface PedidoCompraMapper {

    // PedidoCompraRequestDTO.itens não tem contrapartida em PedidoCompra (itens são PedidoCompraItem
    // à parte, service usa toItemEntities(...) pra isso) — MapStruct ignora silenciosamente.
    PedidoCompra toEntity(PedidoCompraRequestDTO dto);

    // PedidoCompraItem.pedido não tem contrapartida em PedidoCompraItemRequestDTO
    // (setado pelo service ao associar o item ao pedido pai) — MapStruct ignora silenciosamente.
    PedidoCompraItem toItemEntity(PedidoCompraItemRequestDTO dto);

    List<PedidoCompraItem> toItemEntities(List<PedidoCompraItemRequestDTO> dtos);

    @Mapping(target = "itens", ignore = true)
    @Mapping(target = "historico", ignore = true)
    PedidoCompraResponseDTO toResponseDto(PedidoCompra entity);

    PedidoCompraItemResponseDTO toItemResponseDto(PedidoCompraItem entity);

    List<PedidoCompraItemResponseDTO> toItemResponseDtos(List<PedidoCompraItem> entities);

    CompraStatusHistoricoDTO toHistoricoDto(CompraStatusHistorico entity);

    List<CompraStatusHistoricoDTO> toHistoricoDtos(List<CompraStatusHistorico> entities);
}
