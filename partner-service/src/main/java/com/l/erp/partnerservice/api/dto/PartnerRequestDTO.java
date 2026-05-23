package com.l.erp.partnerservice.api.dto;

import com.l.erp.common.validation.NoHtml;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PartnerRequestDTO(

        @NotBlank
        @NoHtml
        @Size(max = 200)
        String name,

        @NoHtml
        @Size(max = 20)
        String crc,

        @NotBlank
        @Size(min = 14, max = 14, message = "CNPJ deve conter exatamente 14 dígitos")
        @Pattern(regexp = "\\d{14}", message = "CNPJ deve conter apenas dígitos")
        String cnpj,

        @NotBlank
        @Email
        @Size(max = 200)
        String email,

        @Size(max = 20)
        @Pattern(regexp = "\\d{10,11}", message = "Telefone deve conter 10 ou 11 dígitos")
        String phone

) {}