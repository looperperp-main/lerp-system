package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.dto.CompraStatusHistoricoDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraItemRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraItemResponseDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraRequestDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraResponseDTO;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompra;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Cópia de campos entidade <-> DTO (spec/p2p-compras.md, Fase 5). Links HATEOAS ficam pro
 * Assembler. {@code CotacaoCompraFornecedor}/{@code CotacaoCompraFornecedorItem} não entram aqui:
 * o response desses carrega campos calculados (valorTotalOfertado, ordemSugerida, valorTotal por
 * item) sem contrapartida na entidade, então o service monta esses DTOs na mão.
 */
@Mapper(componentModel = "spring")
public interface CotacaoCompraMapper {

    // CotacaoCompraRequestDTO.fornecedorIds/itens não têm contrapartida em CotacaoCompra (são
    // CotacaoCompraFornecedor/CotacaoCompraItem à parte, tratados pelo service) — MapStruct ignora
    // silenciosamente, assim como em PedidoCompraMapper.
    CotacaoCompra toEntity(CotacaoCompraRequestDTO dto);

    // CotacaoCompraItem.cotacao não tem contrapartida em CotacaoCompraItemRequestDTO (setado pelo
    // service ao associar o item à cotação pai) — MapStruct ignora silenciosamente.
    CotacaoCompraItem toItemEntity(CotacaoCompraItemRequestDTO dto);

    List<CotacaoCompraItem> toItemEntities(List<CotacaoCompraItemRequestDTO> dtos);

    @Mapping(target = "itens", ignore = true)
    @Mapping(target = "fornecedores", ignore = true)
    @Mapping(target = "historico", ignore = true)
    CotacaoCompraResponseDTO toResponseDto(CotacaoCompra entity);

    CotacaoCompraItemResponseDTO toItemResponseDto(CotacaoCompraItem entity);

    List<CotacaoCompraItemResponseDTO> toItemResponseDtos(List<CotacaoCompraItem> entities);

    CompraStatusHistoricoDTO toHistoricoDto(CompraStatusHistorico entity);

    List<CompraStatusHistoricoDTO> toHistoricoDtos(List<CompraStatusHistorico> entities);
}
