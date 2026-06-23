package com.l.erp.billingservice.infra.asaas.client;

import com.l.erp.billingservice.infra.asaas.dto.AsaasListResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPaymentResponse;
import com.l.erp.billingservice.infra.asaas.dto.AsaasPixQrCodeResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Sub-client Asaas para cobranças/pagamentos (spec §3.3). Proxy criado em {@code AsaasClientConfig}. */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface AsaasPaymentClient {

    /** Cobranças de uma assinatura, mais recente primeiro — a 1ª é o boleto/PIX inicial. */
    @GetExchange("subscriptions/{subscriptionId}/payments")
    AsaasListResponse<AsaasPaymentResponse> listBySubscription(@PathVariable String subscriptionId);

    @GetExchange("payments/{id}/pixQrCode")
    AsaasPixQrCodeResponse getPixQrCode(@PathVariable String id);
}