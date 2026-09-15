package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.controllers.compras.PedidoCompraController;
import com.l.erp.operacoesservice.api.dto.PedidoCompraResponseDTO;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/** Wrapper de HATEOAS sobre o PedidoCompraMapper (mesmo padrão de RequisicaoCompraAssembler). */
@Component
public class PedidoCompraAssembler
        extends RepresentationModelAssemblerSupport<PedidoCompra, PedidoCompraResponseDTO> {

    private final PedidoCompraMapper mapper;

    public PedidoCompraAssembler(PedidoCompraMapper mapper) {
        super(PedidoCompraController.class, PedidoCompraResponseDTO.class);
        this.mapper = mapper;
    }

    @Override
    public PedidoCompraResponseDTO toModel(PedidoCompra entity) {
        PedidoCompraResponseDTO dto = mapper.toResponseDto(entity);
        dto.add(linkTo(methodOn(PedidoCompraController.class).buscarPorId(entity.getId())).withSelfRel());
        return dto;
    }

    /** Detalhe (GET /{id}): resumo + itens + histórico. */
    public PedidoCompraResponseDTO toDetailModel(PedidoCompra entity, List<PedidoCompraItem> itens,
                                                  List<CompraStatusHistorico> historico) {
        PedidoCompraResponseDTO dto = toModel(entity);
        dto.setItens(mapper.toItemResponseDtos(itens));
        dto.setHistorico(mapper.toHistoricoDtos(historico));
        return dto;
    }
}
