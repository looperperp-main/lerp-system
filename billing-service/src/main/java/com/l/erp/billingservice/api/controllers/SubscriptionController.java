package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.api.dto.CancelSubscriptionResponse;
import com.l.erp.common.util.Constants;
import com.l.erp.billingservice.api.dto.MrrMensalDTO;
import com.l.erp.billingservice.api.dto.SubscriptionAdminDTO;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import com.l.erp.billingservice.services.SubscriptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Listagem admin de assinaturas + cancelamento self-service do tenant. */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionRepository subscriptionRepository,
                                  SubscriptionService subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public ResponseEntity<Page<SubscriptionAdminDTO>> listar(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long tenantId,
            Pageable pageable) {
        Page<com.l.erp.billingservice.domain.Subscription> page = (tenantId != null)
                ? subscriptionRepository.findByTenantId(tenantId, pageable)
                : subscriptionRepository.findAll(pageable);
        return ResponseEntity.ok(page.map(s -> new SubscriptionAdminDTO(
                s.getId(), s.getTenantId(), s.getPlanType(), s.getBillingCycle(), s.getValue(),
                s.getStatus(), s.getAsaasSubscriptionId(), s.getNextDueDate(), s.getCreatedAt())));
    }

    /**
     * MRR dos últimos 6 meses: soma do valor das assinaturas vigentes no fim de cada mês
     * (ativada até lá e não suspensa/cancelada antes; reativação conta pela activatedAt mais recente).
     */
    @GetMapping("/mrr")
    public ResponseEntity<java.util.List<MrrMensalDTO>> mrr() {
        // ponytail: agrega em memória via findAll(); query nativa com generate_series se a tabela crescer
        java.util.List<com.l.erp.billingservice.domain.Subscription> subs = subscriptionRepository.findAll();
        java.util.List<MrrMensalDTO> meses = new java.util.ArrayList<>();
        java.time.YearMonth atual = java.time.YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            java.time.YearMonth ym = atual.minusMonths(i);
            java.time.OffsetDateTime fimDoMes = ym.atEndOfMonth().atTime(23, 59, 59)
                    .atOffset(java.time.ZoneOffset.UTC);
            java.math.BigDecimal soma = subs.stream()
                    .filter(s -> s.getActivatedAt() != null && !s.getActivatedAt().isAfter(fimDoMes))
                    .filter(s -> s.getCancelledAt() == null || s.getCancelledAt().isAfter(fimDoMes)
                            || s.getActivatedAt().isAfter(s.getCancelledAt()))
                    .filter(s -> s.getSuspendedAt() == null || s.getSuspendedAt().isAfter(fimDoMes)
                            || s.getActivatedAt().isAfter(s.getSuspendedAt()))
                    .map(s -> s.getValue() != null ? s.getValue() : java.math.BigDecimal.ZERO)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            meses.add(new MrrMensalDTO(ym.toString(), soma));
        }
        return ResponseEntity.ok(meses);
    }

    /** Cancelamento manual da assinatura do tenant logado (gateway injeta X-Tenant-Id). */
    @PostMapping("/me/cancel")
    public ResponseEntity<CancelSubscriptionResponse> cancelar(@RequestHeader(Constants.HEADER_TENANT_ID) Long tenantId) {
        return ResponseEntity.ok(subscriptionService.cancelForTenant(tenantId));
    }

    /** Reprocessa a ativação (webhook perdido): consulta o Asaas e, se pago, ativa pelo fluxo normal. */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<com.l.erp.billingservice.api.dto.ReprocessResultDTO> reprocessar(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        return ResponseEntity.ok(subscriptionService.reprocessar(id));
    }

    /** Cobranças da assinatura direto do Asaas (drill-down do admin). */
    @GetMapping("/{id}/cobrancas")
    public ResponseEntity<java.util.List<com.l.erp.billingservice.api.dto.CobrancaAdminDTO>> cobrancas(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        return ResponseEntity.ok(subscriptionService.listarCobrancas(id));
    }

    /** Cancelamento pelo admin (mesmas regras do self-service + auditoria). */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<CancelSubscriptionResponse> cancelarAdmin(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        return ResponseEntity.ok(subscriptionService.cancelForAdmin(id));
    }
}
