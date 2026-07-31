package com.l.erp.billingservice.services.webhook;

import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.exception.TransientException;
import com.l.erp.billingservice.infra.redis.WebhookIdempotencyService;
import com.l.erp.billingservice.services.WebhookLogService;
import com.l.erp.billingservice.services.webhook.handler.PaymentReceivedHandler;
import com.l.erp.common.util.Constants;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookProcessorTest {

    @Mock
    WebhookIdempotencyService idempotencyService;

    @Mock
    WebhookHandlerFactory handlerFactory;

    @Mock
    WebhookLogService logService;

    @Mock
    PaymentReceivedHandler handler;

    // Registry real (não mock): mock devolveria Counter null e estouraria no increment().
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private WebhookProcessor processor;

    private final WebhookLog webhookLog = new WebhookLog();

    @BeforeEach
    void setUp() {
        processor = new WebhookProcessor(idempotencyService, handlerFactory, logService, meterRegistry);
    }

    /** Valor do contador webhook_processado_total para um par evento/resultado (0 se nunca incrementado). */
    private double contador(String evento, String resultado) {
        return meterRegistry.find(Constants.METRIC_WEBHOOK_PROCESSADO)
                .tag(Constants.METRIC_TAG_EVENTO, evento)
                .tag(Constants.METRIC_TAG_RESULTADO, resultado)
                .counters().stream()
                .mapToDouble(c -> c.count())
                .sum();
    }

    @Test
    void duplicate_isDiscardedWithoutRouting() {
        AsaasWebhookPayload payload = payload("PAYMENT_RECEIVED", "evt_1");
        when(idempotencyService.tryAcquire("PAYMENT_RECEIVED", "evt_1")).thenReturn(false);

        processor.processAsync(payload, webhookLog);

        verify(handlerFactory, never()).getHandler(any());
        verify(logService, never()).markProcessed(any());
        assertEquals(1, contador("PAYMENT_RECEIVED", Constants.METRIC_RESULTADO_DUPLICADO));
    }

    @Test
    void noHandler_isIgnored() {
        AsaasWebhookPayload payload = payload("UNKNOWN_EVENT", "evt_2");
        when(idempotencyService.tryAcquire("UNKNOWN_EVENT", "evt_2")).thenReturn(true);
        when(handlerFactory.getHandler("UNKNOWN_EVENT")).thenReturn(Optional.empty());

        processor.processAsync(payload, webhookLog);

        verify(idempotencyService).markDone("UNKNOWN_EVENT", "evt_2");
        verify(logService).markIgnored(eq(webhookLog), any());
        verify(logService, never()).markProcessed(any());
        assertEquals(1, contador("UNKNOWN_EVENT", Constants.METRIC_RESULTADO_IGNORADO));
    }

    @Test
    void success_marksDoneAndProcessed() {
        AsaasWebhookPayload payload = payload("PAYMENT_RECEIVED", "evt_3");
        when(idempotencyService.tryAcquire("PAYMENT_RECEIVED", "evt_3")).thenReturn(true);
        when(handlerFactory.getHandler("PAYMENT_RECEIVED")).thenReturn(Optional.of(handler));

        processor.processAsync(payload, webhookLog);

        verify(handler).handle(payload);
        verify(idempotencyService).markDone("PAYMENT_RECEIVED", "evt_3");
        verify(logService).markProcessed(webhookLog);
        assertEquals(1, contador("PAYMENT_RECEIVED", Constants.METRIC_RESULTADO_OK));
    }

    @Test
    void transientError_releasesKeyForRetry() {
        AsaasWebhookPayload payload = payload("PAYMENT_RECEIVED", "evt_4");
        when(idempotencyService.tryAcquire("PAYMENT_RECEIVED", "evt_4")).thenReturn(true);
        when(handlerFactory.getHandler("PAYMENT_RECEIVED")).thenReturn(Optional.of(handler));
        doThrowTransient();

        processor.processAsync(payload, webhookLog);

        verify(idempotencyService).release("PAYMENT_RECEIVED", "evt_4");
        verify(logService).markError(eq(webhookLog), startsWith("TRANSIENT"));
        verify(idempotencyService, never()).markError(any(), any());
        assertEquals(1, contador("PAYMENT_RECEIVED", Constants.METRIC_RESULTADO_ERRO_TRANSITORIO));
    }

    @Test
    void permanentError_keepsKeyAndMarksError() {
        AsaasWebhookPayload payload = payload("PAYMENT_RECEIVED", "evt_5");
        when(idempotencyService.tryAcquire("PAYMENT_RECEIVED", "evt_5")).thenReturn(true);
        when(handlerFactory.getHandler("PAYMENT_RECEIVED")).thenReturn(Optional.of(handler));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(handler).handle(payload);

        processor.processAsync(payload, webhookLog);

        verify(idempotencyService).markError("PAYMENT_RECEIVED", "evt_5");
        verify(logService).markError(eq(webhookLog), any());
        verify(idempotencyService, never()).release(any(), any());
        assertEquals(1, contador("PAYMENT_RECEIVED", Constants.METRIC_RESULTADO_ERRO_PERMANENTE));
    }

    private void doThrowTransient() {
        org.mockito.Mockito.doThrow(new TransientException("asaas indisponível", new RuntimeException()))
                .when(handler).handle(any());
    }

    private static AsaasWebhookPayload payload(String event, String id) {
        AsaasWebhookPayload p = new AsaasWebhookPayload();
        p.setEvent(event);
        p.setId(id);
        return p;
    }
}