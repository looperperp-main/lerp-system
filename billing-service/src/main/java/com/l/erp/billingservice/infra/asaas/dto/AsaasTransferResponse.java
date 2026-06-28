package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response da criação de transferência no Asaas (spec §10.3). {@code id} = {@code tra_xxx}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasTransferResponse(
        String id,
        String status
) {
}