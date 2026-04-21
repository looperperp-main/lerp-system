package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.ProdutoCategoriaController;
import com.l.erp.cadastroservice.api.dto.ProdutoCategoriaResponseDTO;
import com.l.erp.cadastroservice.domain.ProdutoCategoria;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ProdutoCategoriaAssembler extends RepresentationModelAssemblerSupport<ProdutoCategoria, ProdutoCategoriaResponseDTO> {

    public ProdutoCategoriaAssembler() {
        super(ProdutoCategoriaController.class, ProdutoCategoriaResponseDTO.class);
    }

    @Override
    public ProdutoCategoriaResponseDTO toModel(ProdutoCategoria entity) {
        ProdutoCategoriaResponseDTO dto = instantiateModel(entity);

        dto.setId(entity.getId());
        dto.setNome(entity.getNome());
        dto.setDescricao(entity.getDescricao());
        dto.setAtiva(entity.getAtiva());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());
        dto.add(linkTo(methodOn(ProdutoCategoriaController.class)
                .findById(entity.getId())).withSelfRel());
        dto.add(linkTo(methodOn(ProdutoCategoriaController.class)).withRel("produto_categoria"));
        return dto;
    }
}
