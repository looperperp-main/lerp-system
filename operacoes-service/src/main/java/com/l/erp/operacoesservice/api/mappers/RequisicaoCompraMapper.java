package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.dto.CompraStatusHistoricoDTO;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraItemResponseDTO;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraResponseDTO;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompraItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Cópia de campos entidade <-> DTO (spec/p2p-compras.md, Fase 1a). Links HATEOAS ficam pro Assembler da Fase 1b. */
@Mapper(componentModel = "spring")
public interface RequisicaoCompraMapper {

    // RequisicaoCompraRequestDTO.itens não tem contrapartida em RequisicaoCompra (itens são
    // RequisicaoCompraItem à parte, service usa toItemEntities(...) pra isso) — MapStruct ignora silenciosamente.
    RequisicaoCompra toEntity(RequisicaoCompraRequestDTO dto);

    // RequisicaoCompraItem.requisicao não tem contrapartida em RequisicaoCompraItemRequestDTO
    // (setado pelo service ao associar o item à requisição pai) — MapStruct ignora silenciosamente.
    RequisicaoCompraItem toItemEntity(RequisicaoCompraItemRequestDTO dto);

    List<RequisicaoCompraItem> toItemEntities(List<RequisicaoCompraItemRequestDTO> dtos);

    @Mapping(target = "itens", ignore = true)
    @Mapping(target = "historico", ignore = true)
    RequisicaoCompraResponseDTO toResponseDto(RequisicaoCompra entity);

    RequisicaoCompraItemResponseDTO toItemResponseDto(RequisicaoCompraItem entity);

    List<RequisicaoCompraItemResponseDTO> toItemResponseDtos(List<RequisicaoCompraItem> entities);

    CompraStatusHistoricoDTO toHistoricoDto(CompraStatusHistorico entity);

    List<CompraStatusHistoricoDTO> toHistoricoDtos(List<CompraStatusHistorico> entities);
}
