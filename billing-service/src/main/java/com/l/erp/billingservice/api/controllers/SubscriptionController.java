package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.api.dto.SubscriptionAdminDTO;
import com.l.erp.billingservice.repository.SubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Listagem admin de assinaturas (tela Assinaturas). */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionController(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @GetMapping
    public ResponseEntity<Page<SubscriptionAdminDTO>> listar(Pageable pageable) {
        return ResponseEntity.ok(subscriptionRepository.findAll(pageable).map(s -> new SubscriptionAdminDTO(
                s.getId(), s.getTenantId(), s.getPlanType(), s.getBillingCycle(), s.getValue(),
                s.getStatus(), s.getAsaasSubscriptionId(), s.getNextDueDate(), s.getCreatedAt())));
    }
}
