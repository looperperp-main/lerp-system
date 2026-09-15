package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaItemResponseDTO;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaRequestDTO;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaResponseDTO;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoriaItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Cópia de campos entidade <-> DTO (spec/p2p-compras.md, Fase 3). Mesmo padrão de
 * PedidoCompraMapper — links HATEOAS ficam pro Assembler, itens são entidades à parte. */
@Mapper(componentModel = "spring")
public interface RecebimentoMercadoriaMapper {

    // RecebimentoMercadoriaRequestDTO.itens não tem contrapartida na entidade (itens são
    // RecebimentoMercadoriaItem à parte, construídos manualmente no service porque cada item
    // exige buscar o PedidoCompraItem por pedidoItemId antes de existir — MapStruct ignora.
    RecebimentoMercadoria toEntity(RecebimentoMercadoriaRequestDTO dto);

    @Mapping(target = "pedidoId", source = "pedido.id")
    @Mapping(target = "itens", ignore = true)
    RecebimentoMercadoriaResponseDTO toResponseDto(RecebimentoMercadoria entity);

    @Mapping(target = "pedidoItemId", source = "pedidoItem.id")
    @Mapping(target = "produtoId", source = "pedidoItem.produtoId")
    RecebimentoMercadoriaItemResponseDTO toItemResponseDto(RecebimentoMercadoriaItem entity);

    List<RecebimentoMercadoriaItemResponseDTO> toItemResponseDtos(List<RecebimentoMercadoriaItem> entities);
}
