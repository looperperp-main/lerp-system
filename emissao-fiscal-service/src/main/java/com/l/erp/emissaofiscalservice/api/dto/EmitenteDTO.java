package com.l.erp.emissaofiscalservice.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Emitente da nota (grupo C). {@code cnpj} aceita o padrão alfanumérico (NT 2026.004): 12 posições
 * [0-9A-Z] + 2 dígitos verificadores. {@code inscricaoEstadual} é opcional no leiaute desde a
 * NT 2026.007 (contribuinte exclusivo de IBS/CBS) — mas a emissão sem IE ainda é barrada na guarda.
 */
public record EmitenteDTO(
        @NotBlank @Pattern(regexp = "[0-9A-Z]{12}[0-9]{2}", message = "CNPJ inválido (14 posições, sem máscara)") String cnpj,
        @NotBlank @Size(max = 60) String razaoSocial,
        @Size(max = 60) String nomeFantasia,
        @Pattern(regexp = "\\d{2,14}", message = "deve conter só dígitos (2 a 14)") String inscricaoEstadual,
        @NotBlank @Pattern(regexp = "[1-4]", message = "CRT deve ser 1, 2, 3 ou 4") String crt,
        @NotNull @Valid EnderecoDTO endereco
) {
}
