package com.l.erp.billingservice.infra.asaas.client;

import com.l.erp.billingservice.infra.asaas.dto.AsaasListResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPixTransferRequest;
import com.l.erp.billingservice.infra.asaas.dto.AsaasTransferData;
import com.l.erp.billingservice.infra.asaas.dto.AsaasTransferResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Sub-client Asaas para transferências (repasse de comissão — Fase 6, spec §10.3).
 * Proxy criado em {@code AsaasClientConfig}. Money-out: o {@code AsaasGateway} NÃO aplica retry
 * cego aqui — usa check-then-act por {@code externalReference} após timeout (§28.4).
 */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface AsaasTransferClient {

    @PostExchange("transfers")
    AsaasTransferResponse create(@RequestBody AsaasPixTransferRequest req);

    /** Consulta idempotente por externalReference — usada no check-then-act após timeout. */
    @GetExchange("transfers")
    AsaasListResponse<AsaasTransferData> findByExternalReference(@RequestParam String externalReference);
}