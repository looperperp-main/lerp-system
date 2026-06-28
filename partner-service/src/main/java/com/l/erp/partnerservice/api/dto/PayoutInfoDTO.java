package com.l.erp.partnerservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados de repasse do parceiro (chave PIX + tipo). Usado no portal do parceiro para configurar
 * para onde a comissão é transferida. {@code pixKeyType} segue os tipos aceitos pelo Asaas.
 */
public record PayoutInfoDTO(
        @NotBlank
        @Size(max = 100)
        String pixKey,

        @NotBlank
        @Pattern(regexp = "CPF|CNPJ|EMAIL|PHONE|EVP",
                message = "pixKeyType deve ser um de: CPF, CNPJ, EMAIL, PHONE, EVP")
        String pixKeyType
) {
}
