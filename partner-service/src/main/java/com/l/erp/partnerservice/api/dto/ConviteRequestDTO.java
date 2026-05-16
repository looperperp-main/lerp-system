package com.l.erp.partnerservice.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConviteRequestDTO(
        @NotBlank @Pattern(regexp = "\\d{14}", message = "CNPJ deve conter exatamente 14 dígitos numéricos")
        String cnpj,

        @NotBlank @Size(max = 200)
        String razaoSocial,

        @Size(max = 200)
        String nomeFantasia,

        @NotBlank @Email @Size(max = 200)
        String emailContato,

        @Size(max = 20)
        String telefone,

        @Size(max = 50)
        String planoSugerido
) {}