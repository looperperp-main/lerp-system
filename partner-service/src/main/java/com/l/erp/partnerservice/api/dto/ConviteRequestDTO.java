package com.l.erp.partnerservice.api.dto;

import com.l.erp.common.validation.NoHtml;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConviteRequestDTO(
        @NotBlank @Pattern(regexp = "[A-Z0-9]{14}", message = "CNPJ deve conter exatamente 14 caracteres alfanuméricos (maiúsculos)")
        String cnpj,

        @NotBlank @Size(max = 200) @NoHtml
        String razaoSocial,

        @Size(max = 200) @NoHtml
        String nomeFantasia,

        @NotBlank @Email @Size(max = 200)
        String emailContato,

        @Size(max = 20) @NoHtml
        String telefone,

        @Size(max = 50) @NoHtml
        String planoSugerido
) {}