package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.GrupoClienteTabelaPrecoRequestDTO;
import com.l.erp.cadastroservice.api.dto.GrupoClienteTabelaPrecoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.GrupoClienteTabelaPrecoAssembler;
import com.l.erp.cadastroservice.domain.TabelaPrecoGrupoCliente;
import com.l.erp.cadastroservice.services.GrupoClienteTabelaPrecoService;
import com.l.erp.common.util.Constants;
import com.l.erp.cadastroservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/v1/grupos-clientes/{grupoClienteId}/tabelas-preco")
@RequiredArgsConstructor
public class GrupoClienteTabelaPrecoController {

    private final GrupoClienteTabelaPrecoService service;
    private final GrupoClienteTabelaPrecoAssembler assembler;

    @GetMapping
    public ResponseEntity<CollectionModel<GrupoClienteTabelaPrecoResponseDTO>> getAssociacoes(
            @PathVariable UUID grupoClienteId) {
        Long tenantId = SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        List<TabelaPrecoGrupoCliente> associacoes = service.getAssociacoes(grupoClienteId, tenantId);
        CollectionModel<GrupoClienteTabelaPrecoResponseDTO> response = assembler.toCollectionModel(associacoes);

        response.add(linkTo(methodOn(GrupoClienteTabelaPrecoController.class)
                .getAssociacoes(grupoClienteId)).withSelfRel());

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<Void> sincronizarAssociacoes(
            @PathVariable UUID grupoClienteId,
            @Valid @RequestBody GrupoClienteTabelaPrecoRequestDTO request) {
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));
        Long tenantId = SecurityUtils.getCurrentTenantId()
                .orElseThrow(() -> new RuntimeException(Constants.USUARIO_UUID_NAO_ENCONTRADO));
        // Passamos os ids que vieram no record (request.tabelaPrecoIds())
        service.sincronizarAssociacoes(grupoClienteId, request.tabelaPrecoIds(), tenantId, userId);
        return ResponseEntity.noContent().build();
    }
}
