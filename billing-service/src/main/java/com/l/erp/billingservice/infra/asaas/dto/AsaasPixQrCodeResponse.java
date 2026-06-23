package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response de {@code GET /payments/{id}/pixQrCode}. {@code encodedImage} é o QR em base64 e
 * {@code payload} é o copia-e-cola PIX. Ausente quando a cobrança não gerou PIX ainda.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasPixQrCodeResponse(
        Boolean success,
        String encodedImage,
        String payload
) {
}