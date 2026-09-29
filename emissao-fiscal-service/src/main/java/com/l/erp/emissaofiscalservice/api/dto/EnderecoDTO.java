package com.l.erp.emissaofiscalservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Endereço de emitente/destinatário (grupos C05/E05 do leiaute NF-e 4.00). {@code codigoMunicipio} é
 * o código IBGE de 7 dígitos — vem resolvido por quem chama (spec §2, este serviço não consulta cadastro).
 */
public record EnderecoDTO(
        @NotBlank @Size(max = 60) String logradouro,
        @NotBlank @Size(max = 60) String numero,
        @Size(max = 60) String complemento,
        @NotBlank @Size(max = 60) String bairro,
        @NotBlank @Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos (código IBGE)") String codigoMunicipio,
        @NotBlank @Size(max = 60) String municipio,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "deve ser a sigla da UF com 2 letras maiúsculas") String uf,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "deve ter 8 dígitos") String cep,
        @Pattern(regexp = "\\d{6,14}", message = "deve ter de 6 a 14 dígitos") String telefone
) {
}
