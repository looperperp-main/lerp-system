package com.l.erp.billingservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PlanRequest(
        @NotBlank String name,
        @NotBlank String planType,
        @NotBlank String billingCycle,
        @NotNull @Positive BigDecimal value,
        String description
) {}