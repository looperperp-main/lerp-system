package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.SyaxQueueDTO;
import com.l.erp.authservice.api.dto.UpdateSyaxQueueStatusRequest;
import com.l.erp.authservice.dominio.SyaxQueue;
import com.l.erp.authservice.services.SyaxQueueService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/api/v1/syax-queue")
public class SyaxQueueController {

    private final SyaxQueueService service;

    public SyaxQueueController(SyaxQueueService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('APP_OWNER')")
    public ResponseEntity<Page<SyaxQueueDTO>> listar(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(service.listar(status, pageable).map(this::toDTO));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('APP_OWNER')")
    public ResponseEntity<SyaxQueueDTO> atualizarStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSyaxQueueStatusRequest req) {
        SyaxQueue updated = service.atualizarStatus(id, req.status(), req.resolvedBy(), req.resolutionNotes());
        return ResponseEntity.ok(toDTO(updated));
    }

    private SyaxQueueDTO toDTO(SyaxQueue q) {
        return new SyaxQueueDTO(q.getId(), q.getTipo(), q.getStatus(), q.getTenantId(),
                q.getTenantName(), q.getTenantCnpj(), q.getTenantEmail(), q.getPayload(),
                q.getCreatedAt(), q.getUpdatedAt(), q.getResolvedBy(), q.getResolutionNotes());
    }
}