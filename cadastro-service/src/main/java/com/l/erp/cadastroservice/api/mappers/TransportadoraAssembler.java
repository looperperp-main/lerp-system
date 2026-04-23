package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.PessoaController;
import com.l.erp.cadastroservice.api.controllers.TransportadoraController;
import com.l.erp.cadastroservice.api.dto.TransportadoraResponseDTO;
import com.l.erp.cadastroservice.domain.Transportadora;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class TransportadoraAssembler extends RepresentationModelAssemblerSupport<Transportadora, TransportadoraResponseDTO> {

    private final TransportadoraMapper transportadoraMapper;

    public TransportadoraAssembler(TransportadoraMapper transportadoraMapper) {
        super(TransportadoraController.class, TransportadoraResponseDTO.class);
        this.transportadoraMapper = transportadoraMapper;
    }

    @Override
    public TransportadoraResponseDTO toModel(Transportadora entity) {
        TransportadoraResponseDTO dto = transportadoraMapper.toDtoResponse(entity);
        dto.setPessoaNomeRazao(entity.getPessoa().getNomeRazao());

        // Self Link
        dto.add(linkTo(methodOn(TransportadoraController.class).findById(entity.getId())).withSelfRel());

        // Rel para a coleção
        dto.add(linkTo(methodOn(TransportadoraController.class).findAll(null, null)).withRel("transportadoras"));

        // Link para a Pessoa vinculada
        if (entity.getPessoa() != null) {
            dto.add(linkTo(methodOn(PessoaController.class).findById(entity.getPessoa().getId())).withRel("pessoa"));
        }

        return dto;
    }
}