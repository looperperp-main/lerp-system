package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.ProdutoController;
import com.l.erp.cadastroservice.api.dto.ProdutoResponseDTO;
import com.l.erp.cadastroservice.domain.Produto;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ProdutoAssembler extends RepresentationModelAssemblerSupport<Produto, ProdutoResponseDTO> {

    private final ProdutoMapper mapper;

    public ProdutoAssembler(ProdutoMapper mapper) {
        super(ProdutoController.class, ProdutoResponseDTO.class);
        this.mapper = mapper;
    }

    @Override
    public ProdutoResponseDTO toModel(Produto entity) {
        ProdutoResponseDTO dto = mapper.toDtoResponse(entity);

        dto.add(linkTo(methodOn(ProdutoController.class).findById(entity.getId())).withSelfRel());
        dto.add(linkTo(methodOn(ProdutoController.class).findAll(null,null)).withRel("produtos"));

        // Links para recursos aninhados (sub-recursos)
        dto.add(linkTo(methodOn(ProdutoController.class).findPrecos(entity.getId())).withRel("precos"));
        dto.add(linkTo(methodOn(ProdutoController.class).findFornecedores(entity.getId())).withRel("fornecedores"));
        dto.add(linkTo(methodOn(ProdutoController.class).findEstoqueConfig(entity.getId())).withRel("estoque-config"));

        return dto;
    }
}