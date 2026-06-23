package com.l.erp.billingservice.infra.asaas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response da criação de customer no Asaas — só o {@code id} (cus_xxx) interessa. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasCustomerResponse(String id) {
}