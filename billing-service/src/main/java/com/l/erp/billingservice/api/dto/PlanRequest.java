package com.l.erp.billingservice.api.dto;

import com.l.erp.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PlanRequest(
        @NotBlank @Size(max = 100) @NoHtml String name,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Z_]{1,20}", message = "planType deve conter apenas letras maiúsculas e underscores") String planType,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Z_]{1,20}", message = "billingCycle deve conter apenas letras maiúsculas e underscores") String billingCycle,
        @NotNull @Positive BigDecimal value,
        @Size(max = 500) @NoHtml String description
) {}