package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response de cobrança do Asaas (spec §7.2). {@code invoiceUrl} é a página hospedada com
 * boleto + PIX; {@code bankSlipUrl} é o PDF do boleto. O QR Code copia-e-cola do PIX vem de
 * uma chamada separada ({@code GET /payments/{id}/pixQrCode}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPaymentResponse(
        String id,
        String status,
        BigDecimal value,
        LocalDate dueDate,
        String invoiceUrl,
        String bankSlipUrl
) {
}