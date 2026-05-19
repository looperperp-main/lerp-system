package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.TrialTriggerResponse;
import com.l.erp.authservice.dominio.SyaxQueue;
import com.l.erp.authservice.services.TrialScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/auth/api/v1/trial/admin")
public class TrialTriggerController {

    private final TrialScheduler scheduler;

    public TrialTriggerController(TrialScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping("/trigger-d10")
    @PreAuthorize("hasRole('APP_OWNER')")
    public ResponseEntity<TrialTriggerResponse> triggerD10(
            @RequestParam(required = false) Long tenantId) {
        List<SyaxQueue> tickets = tenantId != null
                ? scheduler.processarD10ParaTenant(tenantId)
                : runD10All();
        return ResponseEntity.ok(toResponse(tickets));
    }

    @PostMapping("/trigger-d15")
    @PreAuthorize("hasRole('APP_OWNER')")
    public ResponseEntity<TrialTriggerResponse> triggerD15(
            @RequestParam(required = false) Long tenantId) {
        List<SyaxQueue> tickets = tenantId != null
                ? scheduler.processarD15ParaTenant(tenantId)
                : runD15All();
        return ResponseEntity.ok(toResponse(tickets));
    }

    private List<SyaxQueue> runD10All() {
        scheduler.processarD10();
        return List.of();
    }

    private List<SyaxQueue> runD15All() {
        scheduler.processarD15();
        return List.of();
    }

    private TrialTriggerResponse toResponse(List<SyaxQueue> tickets) {
        List<String> tenants = tickets.stream()
                .map(t -> t.getTenantName() + " (#" + t.getTenantId() + ")")
                .toList();
        return new TrialTriggerResponse(tickets.size(), tenants);
    }
}