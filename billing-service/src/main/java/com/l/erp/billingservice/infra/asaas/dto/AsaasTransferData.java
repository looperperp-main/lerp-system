package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Dados da transferência (repasse de comissão) no payload de webhook do Asaas (spec §23.1).
 * {@code externalReference} segue o padrão {@code payout-{partnerId}-{period}} — idempotency key de lote.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AsaasTransferData {
    private String id;
    private String status;          // PENDING | DONE | FAILED
    private BigDecimal value;
    private String failReason;      // INVALID_PIX_KEY | INSUFFICIENT_BALANCE | BANK_UNAVAILABLE
    private String pixAddressKey;
    private String pixAddressKeyType;
    private String externalReference;
}