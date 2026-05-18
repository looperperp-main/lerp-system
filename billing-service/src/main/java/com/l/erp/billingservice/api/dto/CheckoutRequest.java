package com.l.erp.billingservice.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutRequest(
        @NotBlank String planType,
        @NotBlank String cnpj,
        @NotBlank String email,
        @NotBlank String razaoSocial
) {}