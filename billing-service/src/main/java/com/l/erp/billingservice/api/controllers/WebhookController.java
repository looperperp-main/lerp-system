package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.infra.asaas.dto.AsaasWebhookPayload;
import com.l.erp.billingservice.infra.exception.WebhookAuthException;
import com.l.erp.billingservice.services.WebhookLogService;
import com.l.erp.billingservice.services.WebhookSecurityService;
import com.l.erp.billingservice.services.webhook.WebhookProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

/**
 * Endpoint de webhooks do Asaas (spec §8.1).
 *
 * <p>Valida o token, persiste o recebimento e dispara o processamento assíncrono — retornando
 * <b>200 imediatamente</b>. Resposta não-2xx só para token inválido (401): o Asaas usa fila
 * sequencial e pausa tudo se receber erros repetidos (§28.7), por isso qualquer falha de
 * parsing/processamento ainda responde 200.</p>
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookSecurityService securityService;
    private final WebhookLogService logService;
    private final WebhookProcessor processor;
    private final ObjectMapper objectMapper;

    public WebhookController(WebhookSecurityService securityService,
                            WebhookLogService logService,
                            WebhookProcessor processor,
                            ObjectMapper objectMapper) {
        this.securityService = securityService;
        this.logService = logService;
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/asaas")
    public ResponseEntity<Void> receberWebhook(
            @RequestHeader(value = "asaas-access-token", required = false) String token,
            @RequestBody String rawPayload) {

        // 1. Validar token — único caso de resposta não-2xx
        try {
            securityService.validateToken(token);
        } catch (WebhookAuthException e) {
            log.warn("Webhook Asaas rejeitado — {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. Parsear o payload — falha aqui não pode pausar a fila do Asaas (§28.7)
        AsaasWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, AsaasWebhookPayload.class);
        } catch (Exception e) {
            log.error("Webhook Asaas com payload inválido — ignorado, tamanho={}", rawPayload.length(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // 3. Persistir recebimento (RECEBIDO, tolera duplicata) e processar async
        try {
            WebhookLog webhookLog = logService.logReceived(
                    payload.getEvent(),
                    WebhookProcessor.resolveEventId(payload),
                    payload.getPayment() != null ? payload.getPayment().getId() : null,
                    resolveSubscriptionId(payload),
                    rawPayload);

            processor.processAsync(payload, webhookLog);
        } catch (Exception e) {
            log.error("Falha ao registrar/disparar webhook event={} — respondendo 200 para não pausar a fila",
                    payload.getEvent(), e);
        }

        // 4. 200 imediato
        return ResponseEntity.ok().build();
    }

    private String resolveSubscriptionId(AsaasWebhookPayload payload) {
        if (payload.getSubscription() != null) {
            return payload.getSubscription().getId();
        }
        if (payload.getPayment() != null) {
            return payload.getPayment().getSubscription();
        }
        return null;
    }
}