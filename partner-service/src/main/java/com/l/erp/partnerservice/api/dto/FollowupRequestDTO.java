package com.l.erp.partnerservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FollowupRequestDTO(
        @NotBlank
        @Size(max = 2000)
        String message
) {}