package com.l.erp.cadastroservice.api.mappers;

import com.l.erp.cadastroservice.api.controllers.ClienteController;
import com.l.erp.cadastroservice.api.controllers.CondicaoPagamentoController;
import com.l.erp.cadastroservice.api.controllers.GrupoClienteController;
import com.l.erp.cadastroservice.api.controllers.PessoaController;
import com.l.erp.cadastroservice.api.controllers.VendedorController;
import com.l.erp.cadastroservice.api.dto.ClienteResponseDTO;
import com.l.erp.cadastroservice.domain.Cliente;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ClienteAssembler extends RepresentationModelAssemblerSupport<Cliente, ClienteResponseDTO> {
    public ClienteAssembler() {
        super(ClienteController.class, ClienteResponseDTO.class);
    }

    public ClienteResponseDTO toModel(Cliente entity) {
        ClienteResponseDTO dto = instantiateModel(entity);

        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());

        // Mapeando chaves estrangeiras
        dto.setPessoaId(entity.getPessoa() != null ? entity.getPessoa().getId() : null);
        dto.setCondicaoPagamentoId(entity.getCondicaoPagamento() != null ? entity.getCondicaoPagamento().getId() : null);
        dto.setGrupoClienteId(entity.getGrupoCliente() != null ? entity.getGrupoCliente().getId() : null);
        dto.setVendedorId(entity.getVendedor() != null ? entity.getVendedor().getId() : null);

        // Campos normais
        dto.setNome(entity.getPessoa() != null ? entity.getPessoa().getNomeRazao() : null);
        dto.setClassificacaoRisco(entity.getClassificacaoRisco());
        dto.setPrazoMedioPagamentoDias(entity.getPrazoMedioPagamentoDias());
        dto.setLimiteCredito(entity.getLimiteCredito());
        dto.setAtivo(entity.getAtivo());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastUpdatedBy(entity.getLastUpdatedBy());
        dto.setCodigoInterno(entity.getCodigoInterno());

        // Self Link
        dto.add(linkTo(methodOn(ClienteController.class).findById(entity.getId())).withSelfRel());

        // Links de Navegação Satélite (Party Model / Relationships)
        if (entity.getPessoa() != null) {
            dto.add(linkTo(methodOn(PessoaController.class).findById(entity.getPessoa().getId())).withRel("pessoa"));
        }
        if (entity.getCondicaoPagamento() != null) {
            dto.add(linkTo(methodOn(CondicaoPagamentoController.class).findById(entity.getCondicaoPagamento().getId())).withRel("condicao-pagamento"));
        }
        if (entity.getGrupoCliente() != null) {
            dto.add(linkTo(methodOn(GrupoClienteController.class).findById(entity.getGrupoCliente().getId())).withRel("grupo-cliente"));
        }
        if (entity.getVendedor() != null) {
            dto.add(linkTo(methodOn(VendedorController.class).findById(entity.getVendedor().getId())).withRel("vendedor"));
        }

        return dto;
    }
}
