package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.TabelaPrecoController;
import com.l.erp.cadastroservice.api.dto.TabelaPrecoResponseDTO;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class TabelaPrecoAssembler extends RepresentationModelAssemblerSupport<TabelaPreco, TabelaPrecoResponseDTO> {

    private final TabelaPrecoMapper mapper;

    public TabelaPrecoAssembler(TabelaPrecoMapper mapper) {
        super(TabelaPrecoController.class, TabelaPrecoResponseDTO.class);
        this.mapper = mapper;
    }

    @Override
    public TabelaPrecoResponseDTO toModel(TabelaPreco entity) {
        TabelaPrecoResponseDTO dto = mapper.toDtoResponse(entity);

        dto.add(linkTo(methodOn(TabelaPrecoController.class).findById(entity.getId())).withSelfRel());
        dto.add(linkTo(methodOn(TabelaPrecoController.class).findAll(null, null)).withRel("tabelas-preco"));

        return dto;
    }
}