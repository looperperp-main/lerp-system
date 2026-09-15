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

    /**
     * Resolves and retrieves the detailed pricing information for a specific product.
     *
     * @param produtoId The unique identifier of the product for which pricing is being resolved. This parameter is required.
     * @param clienteId The unique identifier of the client for whom the pricing is being resolved. This parameter is optional.
     * @param data The specific date for which the pricing is to be resolved in ISO format. This parameter is optional.
     * @return A {@link ResponseEntity} containing the resolved pricing details encapsulated in a {@link PrecoResolvidoDTO} object.
     * @throws BusinessException if the tenant information cannot be retrieved or if authorization fails.
     */
    @GetMapping("/resolver")
    public ResponseEntity<PrecoResolvidoDTO> resolver(@RequestParam(required = false) UUID produtoId,
                                                       @RequestParam(required = false) UUID clienteId,
                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        Long tenantId = SecurityUtils.getCurrentTenantId().orElseThrow(() -> new BusinessException(Constants.TENANT_NOT_FOUND, HttpStatus.UNAUTHORIZED));
        if (produtoId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(service.resolver(produtoId, clienteId, data, tenantId));
    }
}
