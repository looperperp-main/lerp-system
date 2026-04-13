package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.CondicaoPagamentoController;
import com.l.erp.cadastroservice.api.controllers.CondicaoPagamentoParcelaController;
import com.l.erp.cadastroservice.api.dto.CondicaoPagamentoParcelaResponseDTO;
import com.l.erp.cadastroservice.domain.CondicaoPagamentoParcela;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class CondicaoPagamentoParcelaAssembler extends RepresentationModelAssemblerSupport<CondicaoPagamentoParcela, CondicaoPagamentoParcelaResponseDTO> {

    private final CondicaoPagamentoParcelaMapper mapper;

    public CondicaoPagamentoParcelaAssembler(CondicaoPagamentoParcelaMapper mapper) {
        super(CondicaoPagamentoParcelaController.class, CondicaoPagamentoParcelaResponseDTO.class);
        this.mapper = mapper;
    }

    @Override
    public CondicaoPagamentoParcelaResponseDTO toModel(CondicaoPagamentoParcela entity) {
        CondicaoPagamentoParcelaResponseDTO dto = mapper.toResponseDto(entity);

        // Link para a própria coleção de parcelas (já que substituímos em lote, o self pode ser a listagem toda)
        dto.add(linkTo(methodOn(CondicaoPagamentoParcelaController.class)
                .getParcelasByCondicaoId(entity.getCondicaoPagamento().getId())).withSelfRel());

        // Link para a Condição de Pagamento (Pai)
        dto.add(linkTo(methodOn(CondicaoPagamentoController.class)
                .findById(entity.getCondicaoPagamento().getId())).withRel("condicao-pagamento"));

        return dto;
    }

    @Override
    public CollectionModel<CondicaoPagamentoParcelaResponseDTO> toCollectionModel(Iterable<? extends CondicaoPagamentoParcela> entities) {
        CollectionModel<CondicaoPagamentoParcelaResponseDTO> collectionModel = super.toCollectionModel(entities);

        // Pega o ID da condição da primeira entidade (se a lista não for vazia) para montar o link "self" da coleção
        if (entities.iterator().hasNext()) {
            UUID condicaoId = entities.iterator().next().getCondicaoPagamento().getId();
            collectionModel.add(linkTo(methodOn(CondicaoPagamentoParcelaController.class)
                    .getParcelasByCondicaoId(condicaoId)).withSelfRel());
        }

        return collectionModel;
    }

}