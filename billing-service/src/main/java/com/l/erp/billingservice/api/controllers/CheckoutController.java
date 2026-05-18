package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.api.dto.CheckoutRequest;
import com.l.erp.billingservice.api.dto.CheckoutResponse;
import com.l.erp.billingservice.services.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestBody @Valid CheckoutRequest req) {
        return ResponseEntity.ok(checkoutService.createCheckout(tenantId, req));
    }
}