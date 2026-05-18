package com.l.erp.authservice.api.dto;

import com.l.erp.common.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record TenantDTO(Long id,
                        @NoHtml
                        @NotBlank
                        @Size(max = 120)
                        String name,
                        @NotBlank
                        @Pattern(regexp = "\\d{14}")
                        String cnpj,
                        @NotBlank
                        @Pattern(regexp = "ATIVO|PENDENTE|SUSPENSO|CANCELADO")
                        String status,
                        @NoHtml String slug,
                        @NoHtml String nomeFantasia,
                        @NoHtml String inscricaoEstadual,
                        @NoHtml String email,
                        @NoHtml String telefone,
                        @NoHtml String logradouro,
                        @NoHtml String numero,
                        @NoHtml String complemento,
                        @NoHtml String bairro,
                        @NoHtml String cidade,
                        @NoHtml String uf,
                        @NoHtml String cep,
                        @NoHtml String ibgeCodigo,
                        Instant creationDate,
                        String createdBy,
                        Instant updateDate,
                        String lastUpdatedBy) {
}
