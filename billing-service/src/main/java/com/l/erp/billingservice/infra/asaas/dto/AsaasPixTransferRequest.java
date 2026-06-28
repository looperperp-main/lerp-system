package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Request de transferência PIX para o Asaas (repasse de comissão — spec §10.3).
 *
 * <p>{@code externalReference} = {@code payout-{partnerId}-{period}} é a idempotency key de lote:
 * um PIX por parceiro por período. Usada no check-then-act após timeout (§28.4) para nunca pagar em
 * dobro — money-out NUNCA usa retry cego.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasPixTransferRequest(
        BigDecimal value,
        String pixAddressKey,
        String pixAddressKeyType,
        String description,
        String externalReference
) {
}