package com.l.erp.billingservice.api.dto;

import jakarta.validation.constraints.Size;

public record PartnerReviewDTO(

        @Size(max = 500)
        String notes

) {}