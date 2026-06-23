package com.l.erp.billingservice.infra.asaas.client;

import com.l.erp.billingservice.infra.asaas.dto.AsaasCustomerRequest;
import com.l.erp.billingservice.infra.asaas.dto.AsaasCustomerResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** Sub-client Asaas para customers (spec §3.3). Proxy criado em {@code AsaasClientConfig}. */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface AsaasCustomerClient {

    @PostExchange("customers")
    AsaasCustomerResponse create(@RequestBody AsaasCustomerRequest req);
}