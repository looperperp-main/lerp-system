package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.repository.WebhookLogRepository;
import com.l.erp.billingservice.services.WebhookProcessorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookLogRepository webhookLogRepository;
    private final WebhookProcessorService processorService;

    @Value("${asaas.webhook-token}")
    private String webhookToken;

    public WebhookController(WebhookLogRepository webhookLogRepository,
                              WebhookProcessorService processorService) {
        this.webhookLogRepository = webhookLogRepository;
        this.processorService = processorService;
    }

    @PostMapping("/asaas")
    public ResponseEntity<Void> receberWebhook(
            @RequestHeader(value = "asaas-access-token", required = false) String tokenHeader,
            @RequestBody String payload) {

        if (tokenHeader == null || !tokenHeader.equals(webhookToken)) {
            log.warn("Webhook Asaas rejeitado — token inválido ou ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("Webhook Asaas recebido, tamanho={}", payload.length());

        WebhookLog webhookLog = new WebhookLog();
        webhookLog.setEventType("ASAAS_WEBHOOK");
        webhookLog.setPayload(payload);
        webhookLog.setStatus("RECEBIDO");
        webhookLog.setReceivedAt(OffsetDateTime.now());
        webhookLogRepository.save(webhookLog);

        processorService.process(payload, webhookLog);

        return ResponseEntity.ok().build();
    }
}