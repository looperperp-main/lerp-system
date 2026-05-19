package com.l.erp.authservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l.erp.authservice.dominio.SyaxQueue;
import com.l.erp.authservice.dominio.Tenant;
import com.l.erp.authservice.dominio.TrialEngagement;
import com.l.erp.authservice.dominio.enumerators.EnumTenantStatus;
import com.l.erp.authservice.repositorios.TenantRepository;
import com.l.erp.authservice.repositorios.TrialEngagementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrialScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrialScheduler.class);

    static final Set<String> ALL_FEATURES = Set.of(
            "nfe", "contas_pagar_receber", "relatorios", "conciliacao",
            "folha_pagamento", "integracao_contabil", "clientes", "produtos",
            "fornecedores", "estoque"
    );

    private final TenantRepository tenantRepository;
    private final TrialEngagementRepository engagementRepository;
    private final SyaxQueueService syaxQueueService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public TrialScheduler(TenantRepository tenantRepository,
                          TrialEngagementRepository engagementRepository,
                          SyaxQueueService syaxQueueService,
                          KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.engagementRepository = engagementRepository;
        this.syaxQueueService = syaxQueueService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // D+10: roda às 1h todo dia
    @Scheduled(cron = "0 0 1 * * *")
    public void processarD10() {
        log.info("TrialScheduler D+10 iniciado");
        Instant ref = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant from = ref.minus(12, ChronoUnit.HOURS);
        Instant to = ref.plus(12, ChronoUnit.HOURS);

        List<Tenant> tenants = tenantRepository.findByStatusAndTrialStartedAtBetween(
                EnumTenantStatus.TRIAL, from, to);

        log.info("D+10: {} tenants encontrados", tenants.size());
        for (Tenant tenant : tenants) {
            try {
                processarRelatorioD10(tenant);
            } catch (Exception e) {
                log.error("Falha D+10 para tenant {}", tenant.getId(), e);
            }
        }
    }

    // D+15: roda às 2h todo dia
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void processarD15() {
        log.info("TrialScheduler D+15 iniciado");
        List<Tenant> tenants = tenantRepository.findByStatusAndTrialExpiresAtBefore(
                EnumTenantStatus.TRIAL, Instant.now());

        log.info("D+15: {} tenants com trial expirado", tenants.size());
        for (Tenant tenant : tenants) {
            try {
                processarExpiracaoTrial(tenant);
            } catch (Exception e) {
                log.error("Falha D+15 para tenant {}", tenant.getId(), e);
            }
        }
    }

    public List<SyaxQueue> processarD10ParaTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> List.of(processarRelatorioD10(t)))
                .orElse(List.of());
    }

    public List<SyaxQueue> processarD15ParaTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> List.of(processarExpiracaoTrial(t)))
                .orElse(List.of());
    }

    private SyaxQueue processarRelatorioD10(Tenant tenant) {
        List<TrialEngagement> engagements = engagementRepository.findByTenantId(tenant.getId());

        int loginCount = engagements.stream()
                .filter(e -> TrialEngagementService.LOGIN_FEATURE.equals(e.getFeature()))
                .mapToInt(TrialEngagement::getAccessCount)
                .sum();

        List<String> featuresAcessadas = engagements.stream()
                .map(TrialEngagement::getFeature)
                .filter(f -> !TrialEngagementService.LOGIN_FEATURE.equals(f))
                .collect(Collectors.toList());

        Set<String> acessadasSet = new HashSet<>(featuresAcessadas);
        List<String> gaps = ALL_FEATURES.stream()
                .filter(f -> !acessadasSet.contains(f))
                .collect(Collectors.toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loginCount", loginCount);
        payload.put("features", featuresAcessadas);
        payload.put("gaps", gaps);
        payload.put("trialExpiresAt", tenant.getTrialExpiresAt() != null ? tenant.getTrialExpiresAt().toString() : "");

        SyaxQueue ticket = syaxQueueService.criarTicket("RELATORIO_D10", tenant, payload);
        log.info("Ticket D+10 criado: id={} tenant={}", ticket.getId(), tenant.getName());
        return ticket;
    }

    private SyaxQueue processarExpiracaoTrial(Tenant tenant) {
        publishTrialExpirouCliente(tenant);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trialExpiresAt", tenant.getTrialExpiresAt() != null ? tenant.getTrialExpiresAt().toString() : "");
        payload.put("action", "CONTACTAR_CLIENTE");

        SyaxQueue ticket = syaxQueueService.criarTicket("TRIAL_EXPIRADO", tenant, payload);

        tenant.setStatus(EnumTenantStatus.SUSPENSO);
        tenantRepository.save(tenant);

        log.info("Trial expirado processado: tenant={} ticket={}", tenant.getId(), ticket.getId());
        return ticket;
    }

    private void publishTrialExpirouCliente(Tenant tenant) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "TRIAL_EXPIROU_CLIENTE");
            event.put("to", tenant.getEmail());
            event.put("partnerName", "");
            event.put("clientName", tenant.getName());
            event.put("clientCnpj", tenant.getCnpj());
            kafkaTemplate.send("partner.email.notification", tenant.getCnpj(),
                    objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Falha ao publicar TRIAL_EXPIROU_CLIENTE para tenant {}", tenant.getId(), e);
        }
    }
}