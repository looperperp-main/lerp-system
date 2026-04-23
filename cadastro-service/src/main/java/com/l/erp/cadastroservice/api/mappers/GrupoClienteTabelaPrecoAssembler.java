package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.GrupoClienteTabelaPrecoController;
import com.l.erp.cadastroservice.api.controllers.TabelaPrecoController;
import com.l.erp.cadastroservice.api.dto.GrupoClienteTabelaPrecoResponseDTO;
import com.l.erp.cadastroservice.domain.TabelaPrecoGrupoCliente;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class GrupoClienteTabelaPrecoAssembler extends RepresentationModelAssemblerSupport<TabelaPrecoGrupoCliente, GrupoClienteTabelaPrecoResponseDTO> {

    public GrupoClienteTabelaPrecoAssembler() {
        super(GrupoClienteTabelaPrecoController.class, GrupoClienteTabelaPrecoResponseDTO.class);
    }

    @Override
    public GrupoClienteTabelaPrecoResponseDTO toModel(TabelaPrecoGrupoCliente entity) {
        GrupoClienteTabelaPrecoResponseDTO dto = instantiateModel(entity);

        dto.setTabelaPrecoId(entity.getId().getTabelaPrecoId());
        dto.setGrupoClienteId(entity.getId().getGrupoClienteId());
        dto.setTenantId(entity.getTenantId());

        if (entity.getTabelaPreco() != null) {
            dto.setTabelaPrecoNome(entity.getTabelaPreco().getNome());
        }

        // Self Link
        dto.add(linkTo(methodOn(GrupoClienteTabelaPrecoController.class)
                .getAssociacoes(entity.getId().getGrupoClienteId())).withSelfRel());

        // Link para ver detalhes da Tabela de Preço
        if (entity.getTabelaPreco() != null) {
            // Supondo que você tenha ou venha a ter um TabelaPrecoController
             dto.add(linkTo(methodOn(TabelaPrecoController.class)
                     .findById(entity.getId().getTabelaPrecoId())).withRel("tabela-preco"));
        }

        return dto;
    }
}