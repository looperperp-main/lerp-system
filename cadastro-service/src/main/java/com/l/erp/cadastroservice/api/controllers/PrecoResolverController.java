package com.l.erp.cadastroservice.api.controllers;

import com.l.erp.cadastroservice.api.dto.PrecoResolvidoDTO;
import com.l.erp.cadastroservice.services.PrecoResolverService;
import com.l.erp.cadastroservice.util.SecurityUtils;
import com.l.erp.common.exception.custom.BusinessException;
import com.l.erp.common.util.Constants;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/precos")
public class PrecoResolverController {

    private final PrecoResolverService service;

    public PrecoResolverController(PrecoResolverService service) {
        this.service = service;
    }

    @GetMapping("/resolver")
    public ResponseEntity<PrecoResolvidoDTO> resolver(@RequestParam UUID produtoId,
                                                       @RequestParam(required = false) UUID clienteId,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        return ResponseEntity.ok(service.resolver(produtoId, clienteId, data, tenantId));
    }
}
