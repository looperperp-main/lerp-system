package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.controllers.compras.RecebimentoMercadoriaController;
import com.l.erp.operacoesservice.api.dto.RecebimentoMercadoriaResponseDTO;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoriaItem;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/** Wrapper de HATEOAS sobre o RecebimentoMercadoriaMapper (mesmo padrão de PedidoCompraAssembler). */
@Component
public class RecebimentoMercadoriaAssembler
        extends RepresentationModelAssemblerSupport<RecebimentoMercadoria, RecebimentoMercadoriaResponseDTO> {

    private final RecebimentoMercadoriaMapper mapper;

    public RecebimentoMercadoriaAssembler(RecebimentoMercadoriaMapper mapper) {
        super(RecebimentoMercadoriaController.class, RecebimentoMercadoriaResponseDTO.class);
        this.mapper = mapper;
    }

    @Override
    public RecebimentoMercadoriaResponseDTO toModel(RecebimentoMercadoria entity) {
        RecebimentoMercadoriaResponseDTO dto = mapper.toResponseDto(entity);
        dto.add(linkTo(methodOn(RecebimentoMercadoriaController.class).buscarPorId(entity.getId())).withSelfRel());
        return dto;
    }

    /** Detalhe (GET /{id}): resumo + itens. */
    public RecebimentoMercadoriaResponseDTO toDetailModel(RecebimentoMercadoria entity,
                                                           List<RecebimentoMercadoriaItem> itens) {
        RecebimentoMercadoriaResponseDTO dto = toModel(entity);
        dto.setItens(mapper.toItemResponseDtos(itens));
        return dto;
    }
}
