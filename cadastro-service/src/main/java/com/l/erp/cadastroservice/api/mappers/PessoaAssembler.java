package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.ContatoController;
import com.l.erp.cadastroservice.api.controllers.EnderecoController;
import com.l.erp.cadastroservice.api.controllers.PessoaController;
import com.l.erp.cadastroservice.api.dto.PessoaResponseDTO;
import com.l.erp.cadastroservice.domain.Pessoa;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class PessoaAssembler extends RepresentationModelAssemblerSupport<Pessoa, PessoaResponseDTO> {

    public PessoaAssembler() {
        super(PessoaController.class, PessoaResponseDTO.class);
    }

    @Override
    public PessoaResponseDTO toModel(Pessoa entity) {
        PessoaResponseDTO dto = instantiateModel(entity);

        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setTipo(entity.getTipo());
        dto.setNomeRazao(entity.getNomeRazao());
        dto.setApelidoFantasia(entity.getApelidoFantasia());
        dto.setDocumento(entity.getDocumento());
        dto.setIe(entity.getDocumento());
        dto.setIm(entity.getDocumento());
        dto.setRg(entity.getDocumento());
        dto.setDataNascimento(entity.getDataNascimento());
        dto.setEmail(entity.getEmail());
        dto.setTelefone(entity.getTelefone());
        dto.setAtivo(entity.getAtivo());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());

        // Adicionando links HATEOAS
        dto.add(linkTo(methodOn(PessoaController.class).findById(entity.getId())).withSelfRel());
        dto.add(linkTo(PessoaController.class).withRel("pessoas"));

        // Relacionamentos aninhados (Rotas das Entidades Satélites)
        dto.add(linkTo(methodOn(EnderecoController.class).listarPorPessoa(entity.getId())).withRel("enderecos"));
        dto.add(linkTo(methodOn(ContatoController.class).listarPorPessoa(entity.getId())).withRel("contatos"));



        return dto;
    }
}
