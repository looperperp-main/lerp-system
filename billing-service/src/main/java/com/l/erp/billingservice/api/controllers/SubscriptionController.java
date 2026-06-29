package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.api.dto.CancelSubscriptionResponse;
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
    public ResponseEntity<Page<SubscriptionAdminDTO>> listar(Pageable pageable) {
        return ResponseEntity.ok(subscriptionRepository.findAll(pageable).map(s -> new SubscriptionAdminDTO(
                s.getId(), s.getTenantId(), s.getPlanType(), s.getBillingCycle(), s.getValue(),
                s.getStatus(), s.getAsaasSubscriptionId(), s.getNextDueDate(), s.getCreatedAt())));
    }

    /** Cancelamento manual da assinatura do tenant logado (gateway injeta X-Tenant-Id). */
    @PostMapping("/me/cancel")
    public ResponseEntity<CancelSubscriptionResponse> cancelar(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return ResponseEntity.ok(subscriptionService.cancelForTenant(tenantId));
    }
}
