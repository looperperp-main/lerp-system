package com.l.erp.migracaoservice.api.controllers;

import com.l.erp.common.util.Constants;
import com.l.erp.migracaoservice.api.dto.ExportDtos.AdminExportResponse;
import com.l.erp.migracaoservice.api.dto.ExportDtos.Pagina;
import com.l.erp.migracaoservice.services.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Suporte da plataforma: visão de exports de qualquer tenant, só metadados (status, falha, tentativas,
 * eventos de auditoria). Não há token, caminho de arquivo nem download aqui — o dado é do cliente (LGPD).
 */
@RestController
@RequestMapping("/migracao/admin/exports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Constants.MIGRACAO_AUTH_ADMIN + "')")
public class AdminExportController {

    private final ExportService service;

    @GetMapping
    public Pagina<AdminExportResponse> listar(@RequestParam(required = false) Long tenantId,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "20") int tamanho) {
        return service.listarAdmin(tenantId, status, pagina, tamanho);
    }

    @GetMapping("/{id}")
    public AdminExportResponse detalhar(@PathVariable UUID id) {
        return service.detalharAdmin(id);
    }
}
