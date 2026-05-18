package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.api.dto.ComissaoItemDTO;
import com.l.erp.billingservice.api.dto.ExtratoComissoesDTO;
import com.l.erp.billingservice.services.CommissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/commissions")
public class CommissionController {

    private final CommissionService commissionService;

    public CommissionController(CommissionService commissionService) {
        this.commissionService = commissionService;
    }

    // Endpoint interno — chamado pelo partner-service via HTTP direto (porta 8088)
    @GetMapping("/extrato")
    public ResponseEntity<ExtratoComissoesDTO> getExtrato(@RequestParam UUID partnerId) {
        List<ComissaoItemDTO> historico = commissionService.findByPartner(partnerId).stream()
                .map(c -> new ComissaoItemDTO(
                        c.getId(),
                        c.getTenantId(),
                        c.getAmount(),
                        c.getPeriod(),
                        c.getStatus(),
                        c.getCalculatedAt(),
                        c.getPaidAt()))
                .toList();

        ExtratoComissoesDTO response = new ExtratoComissoesDTO(
                commissionService.getComissaoMesAtual(partnerId),
                commissionService.getTotalPago(partnerId),
                historico
        );

        return ResponseEntity.ok(response);
    }
}