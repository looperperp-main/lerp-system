package com.l.erp.operacoesservice.api.mappers;

import com.l.erp.operacoesservice.api.controllers.PedidoController;
import com.l.erp.operacoesservice.api.dto.PedidoResponseDTO;
import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import com.l.erp.operacoesservice.domain.vendas.PedidoStatusHistorico;
import com.l.erp.operacoesservice.services.vendas.PedidoService;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/** Wrapper de HATEOAS sobre o PedidoMapper (mesmo padrão de CondicaoPagamentoParcelaAssembler no cadastro-service). */
@Component
public class PedidoAssembler extends RepresentationModelAssemblerSupport<Pedido, PedidoResponseDTO> {

    private final PedidoMapper mapper;

    public PedidoAssembler(PedidoMapper mapper) {
        super(PedidoController.class, PedidoResponseDTO.class);
        this.mapper = mapper;
    }

    @Override
    public PedidoResponseDTO toModel(Pedido entity) {
        PedidoResponseDTO dto = mapper.toResponseDto(entity);
        dto.add(linkTo(methodOn(PedidoController.class).buscarPorId(entity.getId())).withSelfRel());
        return dto;
    }

    /** Detalhe (GET /{id}): resumo + itens + histórico. */
    public PedidoResponseDTO toDetailModel(Pedido entity, List<PedidoItem> itens, List<PedidoStatusHistorico> historico) {
        PedidoResponseDTO dto = toModel(entity);
        dto.setItens(mapper.toItemResponseDtos(itens));
        dto.setHistorico(mapper.toHistoricoDtos(historico));
        return dto;
    }

    /** Resposta de POST .../faturar: resumo + itens + histórico + parcelas calculadas (não persistidas, §8/Fase 5). */
    public PedidoResponseDTO toFaturamentoModel(PedidoService.FaturamentoResultado resultado, List<PedidoItem> itens, List<PedidoStatusHistorico> historico) {
        PedidoResponseDTO dto = toModel(resultado.pedido());
        dto.setItens(mapper.toItemResponseDtos(itens));
        dto.setHistorico(mapper.toHistoricoDtos(historico));
        dto.setParcelas(mapper.toParcelaDtos(resultado.parcelas()));
        return dto;
    }
}
