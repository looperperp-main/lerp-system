package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.EnderecoController;
import com.l.erp.cadastroservice.api.controllers.PessoaController;
import com.l.erp.cadastroservice.api.dto.EnderecoResponseDTO;
import com.l.erp.cadastroservice.domain.Endereco;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class EnderecoAssembler extends RepresentationModelAssemblerSupport<Endereco, EnderecoResponseDTO> {
    public EnderecoAssembler() {
        super(EnderecoController.class, EnderecoResponseDTO.class);
    }

    @Override
    public EnderecoResponseDTO toModel(Endereco entity) {
        EnderecoResponseDTO dto = instantiateModel(entity);

        dto.setId(entity.getId());
        dto.setTipo(entity.getTipo());
        dto.setLogradouro(entity.getLogradouro());
        dto.setNumero(entity.getNumero());
        dto.setComplemento(entity.getComplemento());
        dto.setBairro(entity.getBairro());
        dto.setCidade(entity.getCidade());
        dto.setUf(entity.getUf());
        dto.setCep(entity.getCep());
        dto.setIbgeCodigo(entity.getIbgeCodigo());
        dto.setPais(entity.getPais());
        dto.setPrincipal(entity.getPrincipal());
        dto.setTenantId(entity.getTenantId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());
        UUID pessoaId = entity.getPessoa().getId();

        // Self link
        dto.add(linkTo(methodOn(EnderecoController.class).findById(pessoaId, entity.getId())).withSelfRel());

        // Relacionamento com a coleção de endereços daquela pessoa
        dto.add(linkTo(methodOn(EnderecoController.class).listarPorPessoa(pessoaId)).withRel("enderecos"));

        // Navegação de volta para a pessoa "Pai"
        dto.add(linkTo(methodOn(PessoaController.class).findById(pessoaId)).withRel("pessoa"));

        return dto;
    }
}
