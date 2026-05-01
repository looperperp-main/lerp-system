package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.api.dto.PartnerRequestDTO;
import com.l.erp.billingservice.api.dto.PartnerResponseDTO;
import com.l.erp.billingservice.api.dto.PartnerReviewDTO;
import com.l.erp.billingservice.api.mappers.PartnerAssembler;
import com.l.erp.billingservice.domain.Partner;
import com.l.erp.billingservice.services.PartnerService;
import com.l.erp.billingservice.util.SecurityUtils;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/partners")
public class PartnerController {

    private final Logger logger = LoggerFactory.getLogger(PartnerController.class);
    private final PartnerService service;
    private final PartnerAssembler assembler;

    public PartnerController(PartnerService service, PartnerAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    public ResponseEntity<PagedModel<PartnerResponseDTO>> findAll(
            @RequestParam(required = false) String status,
            Pageable pageable,
            PagedResourcesAssembler<Partner> pagedResourcesAssembler) {
        logger.info("Listando parceiros (status={})", status);
        Page<Partner> page = service.findAll(status, pageable);
        return ResponseEntity.ok(pagedResourcesAssembler.toModel(page, assembler));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartnerResponseDTO> findById(@PathVariable UUID id) {
        logger.info("Buscando parceiro por ID: {}", id);
        return ResponseEntity.ok(assembler.toModel(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<PartnerResponseDTO> save(@RequestBody @Valid PartnerRequestDTO dto) {
        logger.info("Criando parceiro: {}", dto.email());
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        Partner saved = service.save(dto, actor);
        PartnerResponseDTO response = assembler.toModel(saved);
        return ResponseEntity.created(response.getRequiredLink(IanaLinkRelations.SELF).toUri()).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PartnerResponseDTO> update(
            @PathVariable UUID id,
            @RequestBody @Valid PartnerRequestDTO dto) {
        logger.info("Atualizando parceiro {}", id);
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        return ResponseEntity.ok(assembler.toModel(service.update(id, dto, actor)));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<PartnerResponseDTO> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid PartnerReviewDTO dto) {
        logger.info("Aprovando parceiro {}", id);
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        return ResponseEntity.ok(assembler.toModel(service.approve(id, dto, actor)));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<PartnerResponseDTO> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid PartnerReviewDTO dto) {
        logger.info("Reprovando parceiro {}", id);
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        return ResponseEntity.ok(assembler.toModel(service.reject(id, dto, actor)));
    }

    @PatchMapping("/{id}/inactivate")
    public ResponseEntity<PartnerResponseDTO> inactivate(@PathVariable UUID id) {
        logger.info("Inativando parceiro {}", id);
        String actor = SecurityUtils.getCurrentUserSub().orElse("system");
        return ResponseEntity.ok(assembler.toModel(service.inactivate(id, actor)));
    }
}