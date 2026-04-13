package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.ContatoController;
import com.l.erp.cadastroservice.api.controllers.PessoaController;
import com.l.erp.cadastroservice.api.dto.ContatoResponseDTO;
import com.l.erp.cadastroservice.domain.Contato;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ContatoAssembler extends RepresentationModelAssemblerSupport<Contato, ContatoResponseDTO> {
    public ContatoAssembler() {
        super(ContatoController.class, ContatoResponseDTO.class);
    }

    @Override
    public ContatoResponseDTO toModel(Contato entity) {
        ContatoResponseDTO dto = instantiateModel(entity);

        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setNome(entity.getNome());
        dto.setTipo(entity.getTipo());
        dto.setCargo(entity.getCargo());
        dto.setEmail(entity.getEmail());
        dto.setTelefone(entity.getTelefone());
        dto.setAtivo(entity.getAtivo());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());

        UUID pessoaId = entity.getPessoa().getId();

        dto.add(linkTo(methodOn(ContatoController.class).findById(pessoaId, entity.getId())).withSelfRel());
        dto.add(linkTo(methodOn(ContatoController.class).listarPorPessoa(pessoaId)).withRel("contatos"));
        dto.add(linkTo(methodOn(PessoaController.class).findById(pessoaId)).withRel("pessoa"));

        return dto;
    }
}
