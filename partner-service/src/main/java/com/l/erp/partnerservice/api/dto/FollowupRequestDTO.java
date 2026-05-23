package com.l.erp.partnerservice.api.dto;

import com.l.erp.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FollowupRequestDTO(
        @NotBlank
        @NoHtml
        @Size(max = 2000)
        String message
) {}