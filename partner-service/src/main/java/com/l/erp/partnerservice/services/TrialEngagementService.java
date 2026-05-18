package com.l.erp.partnerservice.services;

import com.l.erp.partnerservice.api.dto.FeatureStatDTO;
import com.l.erp.partnerservice.domain.TrialEngagement;
import com.l.erp.partnerservice.repository.TrialEngagementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TrialEngagementService {

    public static final String FEATURE_LOGIN = "LOGIN";

    // Catálogo fixo de features rastreadas (exibidas no painel do parceiro)
    public static final Map<String, String> FEATURE_CATALOG = Map.of(
            "nfe",                "Emissão de NF-e",
            "contas_pagar_receber","Contas a pagar/receber",
            "relatorios",         "Relatórios financeiros",
            "conciliacao",        "Conciliação bancária",
            "folha_pagamento",    "Folha de pagamento",
            "integracao_contabil","Integração contábil"
    );

    private static final Logger logger = LoggerFactory.getLogger(TrialEngagementService.class);

    private final TrialEngagementRepository repository;

    public TrialEngagementService(TrialEngagementRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(Long tenantId, String featureKey) {
        try {
            repository.upsertFeatureAccess(tenantId, featureKey, OffsetDateTime.now());
        } catch (Exception e) {
            logger.error("Falha ao registrar engajamento tenantId={} featureKey={}", tenantId, featureKey, e);
        }
    }

    @Transactional(readOnly = true)
    public List<FeatureStatDTO> getEngagement(Long tenantId) {
        Map<String, TrialEngagement> byKey = repository.findByTenantId(tenantId).stream()
                .filter(e -> !FEATURE_LOGIN.equals(e.getFeatureKey()))
                .collect(Collectors.toMap(TrialEngagement::getFeatureKey, e -> e));

        return FEATURE_CATALOG.entrySet().stream()
                .map(entry -> {
                    TrialEngagement eng = byKey.get(entry.getKey());
                    return new FeatureStatDTO(
                            entry.getKey(),
                            entry.getValue(),
                            eng != null ? eng.getAccessCount() : 0,
                            eng != null ? eng.getLastAccessedAt() : null
                    );
                })
                .sorted((a, b) -> Integer.compare(b.accessCount(), a.accessCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getAdoptionGaps(Long tenantId) {
        Set<String> accessedKeys = repository.findByTenantId(tenantId).stream()
                .filter(e -> !FEATURE_LOGIN.equals(e.getFeatureKey()) && e.getAccessCount() > 0)
                .map(TrialEngagement::getFeatureKey)
                .collect(Collectors.toSet());

        return FEATURE_CATALOG.entrySet().stream()
                .filter(entry -> !accessedKeys.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TrialEngagement> getLoginStats(Long tenantId) {
        return repository.findByTenantIdAndFeatureKey(tenantId, FEATURE_LOGIN);
    }
}