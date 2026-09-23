package com.l.erp.emissaofiscalservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Payload de {@code POST /emissao/documentos} — spec §3 item 9/10/11. Autocontido de propósito:
 * o emissao-fiscal-service nunca busca estabelecimento/cálculo fiscal em outro serviço (spec §2).
 * {@code snapshotFiscal} carrega o restante dos dados por item (CST/CFOP/percentuais/etc., já
 * resolvidos por quem chama) sem o emissao-fiscal-service precisar conhecer o formato completo do
 * NF-e ainda — isso é detalhado na Etapa 2, quando o tipo de documento concreto é implementado.
 */
public record DocumentoFiscalRequestDTO(
        @NotNull UUID estabelecimentoId,
        @NotBlank String documento,
        String modelo,
        @NotBlank String serie,
        @NotBlank String ambiente,
        @NotBlank String destinatarioDocumento,
        @NotNull @PositiveOrZero BigDecimal valorTotal,
        @Positive int quantidadeItens,
        Map<String, Object> snapshotFiscal
) {
}
