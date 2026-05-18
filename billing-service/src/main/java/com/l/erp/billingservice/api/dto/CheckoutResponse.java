package com.l.erp.billingservice.api.dto;

import java.math.BigDecimal;

public record CheckoutResponse(
        String paymentUrl,
        String planType,
        String planName,
        BigDecimal value
) {}