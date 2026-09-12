package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.controllers.compras.CotacaoCompraController;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraFornecedorResponseDTO;
import com.l.erp.operacoesservice.api.dto.CotacaoCompraResponseDTO;
import com.l.erp.operacoesservice.domain.compras.CompraStatusHistorico;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompra;
import com.l.erp.operacoesservice.domain.compras.CotacaoCompraItem;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/** Mesmo padrão de PedidoCompraAssembler (Fase 2). */
@Component
public class CotacaoCompraAssembler
        extends RepresentationModelAssemblerSupport<CotacaoCompra, CotacaoCompraResponseDTO> {

    private final CotacaoCompraMapper mapper;

    public CotacaoCompraAssembler(CotacaoCompraMapper mapper) {
        super(CotacaoCompraController.class, CotacaoCompraResponseDTO.class);
        this.mapper = mapper;
    }

    @Override
    public CotacaoCompraResponseDTO toModel(CotacaoCompra entity) {
        CotacaoCompraResponseDTO dto = mapper.toResponseDto(entity);
        dto.add(linkTo(methodOn(CotacaoCompraController.class).buscarPorId(entity.getId())).withSelfRel());
        return dto;
    }

    // fornecedores já chega pronto do service (valorTotalOfertado/ordemSugerida são calculados,
    // sem contrapartida na entidade — ver CotacaoCompraMapper).
    public CotacaoCompraResponseDTO toDetailModel(CotacaoCompra entity, List<CotacaoCompraItem> itens,
                                                   List<CotacaoCompraFornecedorResponseDTO> fornecedores,
                                                   List<CompraStatusHistorico> historico) {
        CotacaoCompraResponseDTO dto = toModel(entity);
        dto.setItens(mapper.toItemResponseDtos(itens));
        dto.setFornecedores(fornecedores);
        dto.setHistorico(mapper.toHistoricoDtos(historico));
        return dto;
    }
}
