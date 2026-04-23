package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.FornecedorController;
import com.l.erp.cadastroservice.api.controllers.PessoaController;
import com.l.erp.cadastroservice.api.dto.FornecedorResponseDTO;
import com.l.erp.cadastroservice.domain.Fornecedor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class FornecedorAssembler extends RepresentationModelAssemblerSupport<Fornecedor, FornecedorResponseDTO> {

    private final FornecedorMapper fornecedorMapper;

    public FornecedorAssembler(FornecedorMapper fornecedorMapper) {
        super(FornecedorController.class, FornecedorResponseDTO.class);
        this.fornecedorMapper = fornecedorMapper;
    }

    @Override
    public FornecedorResponseDTO toModel(Fornecedor entity) {
        // Utilizamos o Mapper do MapStruct para fazer o 'de-para' básico
        FornecedorResponseDTO dto = fornecedorMapper.toDtoResponse(entity);

        // Self Link
        dto.add(linkTo(methodOn(FornecedorController.class).findById(entity.getId())).withSelfRel());

        // Rel para a coleção caso precise voltar a listagem
        dto.add(linkTo(methodOn(FornecedorController.class).findAll(null, null)).withRel("fornecedores"));

        // Link de Navegação Satélite (Party Model / Relationships)
        if (entity.getPessoa() != null) {
            dto.add(linkTo(methodOn(PessoaController.class).findById(entity.getPessoa().getId())).withRel("pessoa"));
        }

        return dto;
    }
}
