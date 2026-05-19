package com.l.erp.authservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.dominio.SyaxQueue;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.repositorios.SyaxQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class SyaxQueueService {

    private static final Logger log = LoggerFactory.getLogger(SyaxQueueService.class);

    private final SyaxQueueRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.syax-internal-email}")
    private String syaxInternalEmail;

    @Value("${app.syax-admin-url}")
    private String syaxAdminUrl;

    public SyaxQueueService(SyaxQueueRepository repository,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SyaxQueue criarTicket(String tipo, Tenant tenant, Map<String, Object> payloadData) {
        SyaxQueue ticket = new SyaxQueue();
        ticket.setTipo(tipo);
        ticket.setStatus("PENDENTE");
        ticket.setTenantId(tenant.getId());
        ticket.setTenantName(tenant.getName());
        ticket.setTenantCnpj(tenant.getCnpj());
        ticket.setTenantEmail(tenant.getEmail());
        ticket.setCreatedAt(Instant.now());

        try {
            ticket.setPayload(objectMapper.writeValueAsString(payloadData));
        } catch (Exception e) {
            log.warn("Falha ao serializar payload do ticket para tenant {}", tenant.getId(), e);
        }

        SyaxQueue saved = repository.save(ticket);
        publishInternalEmail(saved, payloadData);
        return saved;
    }

    public Page<SyaxQueue> listar(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return repository.findByStatus(status, pageable);
        }
        return repository.findAll(pageable);
    }

    @Transactional
    public SyaxQueue atualizarStatus(Long id, String status, String resolvedBy, String notes) {
        SyaxQueue ticket = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket não encontrado"));
        ticket.setStatus(status);
        ticket.setUpdatedAt(Instant.now());
        if (resolvedBy != null) ticket.setResolvedBy(resolvedBy);
        if (notes != null) ticket.setResolutionNotes(notes);
        return repository.save(ticket);
    }

    private void publishInternalEmail(SyaxQueue ticket, Map<String, Object> payloadData) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "TICKET_INTERNO_SYAX");
            event.put("to", syaxInternalEmail);
            event.put("clientName", ticket.getTenantName());
            event.put("clientCnpj", ticket.getTenantCnpj());
            event.put("partnerName", "Syax");

            Map<String, Object> extra = new HashMap<>(payloadData);
            extra.put("ticketId", String.valueOf(ticket.getId()));
            extra.put("ticketTipo", ticket.getTipo());
            extra.put("adminUrl", syaxAdminUrl + "/admin/fila-interna");
            event.put("extraData", extra);

            kafkaTemplate.send("partner.email.notification", ticket.getTenantCnpj(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Falha ao publicar e-mail interno para ticket {}", ticket.getId(), e);
        }
    }
}