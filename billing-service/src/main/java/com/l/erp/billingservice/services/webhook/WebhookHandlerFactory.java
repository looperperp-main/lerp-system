package com.l.erp.billingservice.services.webhook;

import com.l.erp.billingservice.services.webhook.handler.PaymentReceivedHandler;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolve o {@link WebhookEventHandler} pelo tipo de evento (spec §3.2, §14.2).
 *
 * <p>Monta o mapa a partir de {@code getEventType()} de cada handler. {@code PAYMENT_CONFIRMED}
 * (cartão confirmado antes da liquidação) é tratado igual a {@code PAYMENT_RECEIVED} (§8.3).</p>
 */
@Component
public class WebhookHandlerFactory {

    private final Map<String, WebhookEventHandler> handlers = new HashMap<>();

    public WebhookHandlerFactory(List<WebhookEventHandler> handlerList,
                                 PaymentReceivedHandler paymentReceivedHandler) {
        for (WebhookEventHandler handler : handlerList) {
            handlers.put(handler.getEventType(), handler);
        }
        // PAYMENT_CONFIRMED compartilha o tratamento de PAYMENT_RECEIVED
        handlers.put("PAYMENT_CONFIRMED", paymentReceivedHandler);
    }

    /** @return o handler do evento, ou vazio se nenhum tratar este tipo. */
    public Optional<WebhookEventHandler> getHandler(String eventType) {
        return Optional.ofNullable(handlers.get(eventType));
    }
}