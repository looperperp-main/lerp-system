package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.VendedorController;
import com.l.erp.cadastroservice.api.dto.VendedorResponseDTO;
import com.l.erp.cadastroservice.domain.Vendedor;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class VendedorAssembler extends RepresentationModelAssemblerSupport<Vendedor, VendedorResponseDTO> {
    private final VendedorMapper vendedorMapper;

    public VendedorAssembler(VendedorMapper vendedorMapper) {
        super(VendedorController.class, VendedorResponseDTO.class);
        this.vendedorMapper = vendedorMapper;
    }

    @Override
    public VendedorResponseDTO toModel(Vendedor entity) {
        VendedorResponseDTO dto = vendedorMapper.toDtoResponse(entity);
        // Link para a própria coleção de parcelas (já que substituímos em lote, o self pode ser a listagem toda)
        dto.add(linkTo(methodOn(VendedorController.class)
                .findById(entity.getId())).withSelfRel());
        dto.add(linkTo(methodOn(VendedorController.class)).withRel("vendedores"));
        return dto;
    }
}
