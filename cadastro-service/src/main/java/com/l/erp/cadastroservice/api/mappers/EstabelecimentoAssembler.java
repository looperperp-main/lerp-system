package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.EstabelecimentoController;
import com.l.erp.cadastroservice.api.controllers.PessoaController;
import com.l.erp.cadastroservice.api.dto.EstabelecimentoResponseDTO;
import com.l.erp.cadastroservice.domain.Estabelecimento;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class EstabelecimentoAssembler extends RepresentationModelAssemblerSupport<Estabelecimento, EstabelecimentoResponseDTO> {
    public EstabelecimentoAssembler() {
        super(EstabelecimentoController.class, EstabelecimentoResponseDTO.class);
    }

    @Override
    public EstabelecimentoResponseDTO toModel(Estabelecimento entity) {
        EstabelecimentoResponseDTO dto = instantiateModel(entity);

        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setCnpjCompleto(entity.getCnpjCompleto());
        dto.setOrdem(entity.getOrdem());
        dto.setMatriz(entity.getMatriz());
        dto.setProprio(entity.getProprio());
        dto.setIe(entity.getIe());
        dto.setIm(entity.getIm());
        dto.setAtivo(entity.getAtivo());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());

        UUID pessoaId = entity.getPessoa().getId();

        dto.add(linkTo(methodOn(EstabelecimentoController.class).findById(pessoaId, entity.getId())).withSelfRel());
        dto.add(linkTo(methodOn(EstabelecimentoController.class).listarPorPessoa(pessoaId)).withRel("estabelecimentos"));
        dto.add(linkTo(methodOn(PessoaController.class).findById(pessoaId)).withRel("pessoa"));

        return dto;
    }
}
