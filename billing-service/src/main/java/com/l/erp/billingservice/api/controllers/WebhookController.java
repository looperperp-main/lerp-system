package com.l.erp.billingservice.api.controllers;

import com.l.erp.billingservice.domain.WebhookLog;
import com.l.erp.billingservice.repository.WebhookLogRepository;
import com.l.erp.billingservice.services.WebhookProcessorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookLogRepository webhookLogRepository;
    private final WebhookProcessorService processorService;

    public WebhookController(WebhookLogRepository webhookLogRepository,
                              WebhookProcessorService processorService) {
        this.webhookLogRepository = webhookLogRepository;
        this.processorService = processorService;
    }

    @PostMapping("/asaas")
    public ResponseEntity<Void> receberWebhook(@RequestBody String payload) {
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