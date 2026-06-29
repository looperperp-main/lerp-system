package com.l.erp.billingservice.infra.asaas.client;

import com.l.erp.billingservice.infra.asaas.dto.AsaasSubscriptionRequest;
import com.l.erp.billingservice.infra.asaas.dto.AsaasSubscriptionResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** Sub-client Asaas para subscriptions (spec §3.3). Proxy criado em {@code AsaasClientConfig}. */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface AsaasSubscriptionClient {

    @PostExchange("subscriptions")
    AsaasSubscriptionResponse create(@RequestBody AsaasSubscriptionRequest req);

    @GetExchange("subscriptions/{id}")
    AsaasSubscriptionResponse get(@PathVariable String id);

    /** Cancela a assinatura no Asaas (para renovações futuras). */
    @DeleteExchange("subscriptions/{id}")
    AsaasSubscriptionResponse delete(@PathVariable String id);
}