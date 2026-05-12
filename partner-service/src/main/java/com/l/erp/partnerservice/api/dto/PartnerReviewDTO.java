package com.l.erp.partnerservice.api.dto;

import jakarta.validation.constraints.Size;

public record PartnerReviewDTO(

        @Size(max = 500)
        String notes

) {}