package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.controllers.compras.RequisicaoCompraController;
import com.l.erp.operacoesservice.api.dto.RequisicaoCompraResponseDTO;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.RequisicaoCompraItem;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/** Wrapper de HATEOAS sobre o RequisicaoCompraMapper (mesmo padrão de PedidoAssembler em vendas). */
@Component
public class RequisicaoCompraAssembler
        extends RepresentationModelAssemblerSupport<RequisicaoCompra, RequisicaoCompraResponseDTO> {

    private final RequisicaoCompraMapper mapper;

    public RequisicaoCompraAssembler(RequisicaoCompraMapper mapper) {
        super(RequisicaoCompraController.class, RequisicaoCompraResponseDTO.class);
        this.mapper = mapper;
    }

    @Override
    public RequisicaoCompraResponseDTO toModel(RequisicaoCompra entity) {
        RequisicaoCompraResponseDTO dto = mapper.toResponseDto(entity);
        dto.add(linkTo(methodOn(RequisicaoCompraController.class).buscarPorId(entity.getId())).withSelfRel());
        return dto;
    }

    /** Detalhe (GET /{id}): resumo + itens + histórico. */
    public RequisicaoCompraResponseDTO toDetailModel(RequisicaoCompra entity, List<RequisicaoCompraItem> itens,
                                                       List<CompraStatusHistorico> historico) {
        RequisicaoCompraResponseDTO dto = toModel(entity);
        dto.setItens(mapper.toItemResponseDtos(itens));
        dto.setHistorico(mapper.toHistoricoDtos(historico));
        return dto;
    }
}
