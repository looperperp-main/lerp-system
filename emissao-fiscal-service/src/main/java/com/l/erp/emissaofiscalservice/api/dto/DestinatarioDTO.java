package com.l.erp.emissaofiscalservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Destinatário (grupo E). {@code documento} é CPF (11 dígitos) ou CNPJ (14, alfanumérico — NT 2026.004).
 * {@code indicadorIe}: 1=contribuinte, 2=isento, 9=não contribuinte (campo indIEDest do leiaute).
 */
public record DestinatarioDTO(
        @NotBlank @Pattern(regexp = "\\d{11}|[0-9A-Z]{12}[0-9]{2}", message = "deve ser CPF (11 dígitos) ou CNPJ (14 posições), sem máscara") String documento,
        @NotBlank @Size(max = 60) String nome,
        @NotBlank @Pattern(regexp = "[129]", message = "indicadorIe deve ser 1, 2 ou 9") String indicadorIe,
        @Pattern(regexp = "\\d{2,14}", message = "deve conter só dígitos (2 a 14)") String inscricaoEstadual,
        @Size(max = 60) String email,
        @NotNull @Valid EnderecoDTO endereco
) {
}
