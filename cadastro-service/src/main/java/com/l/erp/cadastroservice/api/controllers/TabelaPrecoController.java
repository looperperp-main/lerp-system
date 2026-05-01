package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.TabelaPrecoDTO;
import com.l.erp.cadastroservice.api.dto.TabelaPrecoResponseDTO;
import com.l.erp.cadastroservice.api.mappers.TabelaPrecoAssembler;
import com.l.erp.cadastroservice.domain.TabelaPreco;
import com.l.erp.cadastroservice.services.TabelaPrecoService;
import com.l.erp.common.util.Constants;
import com.l.erp.cadastroservice.util.SecurityUtils;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tabelas-preco")
public class TabelaPrecoController {
    private final Logger logger = LoggerFactory.getLogger(TabelaPrecoController.class);
    private final TabelaPrecoService service;
    private final TabelaPrecoAssembler assembler;

    public TabelaPrecoController(TabelaPrecoService service, TabelaPrecoAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<PagedModel<TabelaPrecoResponseDTO>> findAll(Pageable pageable, PagedResourcesAssembler<TabelaPreco> pagedResourcesAssembler) {
        logger.info("Listando Tabelas de Preço");
        Page<TabelaPreco> page = service.getAll(pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TabelaPrecoResponseDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando Tabela de Preço por ID: {}", id);
        TabelaPreco tabelaPreco = service.findById(id);
        return ResponseEntity.ok(assembler.toModel(tabelaPreco));
    }

    @PostMapping
    public ResponseEntity<TabelaPrecoResponseDTO> save(@RequestBody @Valid TabelaPrecoDTO dto) {
        logger.info("Criando Tabela de Preço: {}", dto.nome());
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        TabelaPreco salvo = service.save(dto, userId);
        TabelaPrecoResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.created(response.getRequiredLink(org.springframework.hateoas.IanaLinkRelations.SELF).toUri())
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TabelaPrecoResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid TabelaPrecoDTO dto) {
        logger.info("Atualizando Tabela de Preço: {}", id);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        TabelaPreco salvo = service.update(id, dto, userId);
        TabelaPrecoResponseDTO response = assembler.toModel(salvo);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id) {
        logger.info("Alterando status da Tabela de Preço: {}", id);
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new RuntimeException(Constants.USER_NOT_FOUND));

        service.updateStatus(id, userId);

        return ResponseEntity.noContent().build();
    }
}