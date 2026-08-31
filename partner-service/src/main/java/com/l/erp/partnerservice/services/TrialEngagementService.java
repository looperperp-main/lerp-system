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

    // Catálogo fixo de features rastreadas (exibidas no painel do parceiro/admin).
    // Map.ofEntries porque Map.of trava em 10 pares — cadastros sozinho já usa 12.
    public static final Map<String, String> FEATURE_CATALOG = Map.ofEntries(
            // cadastros — módulo em produção hoje
            Map.entry("grupo_clientes",      "Grupo de Clientes"),
            Map.entry("depositos",           "Depósitos"),
            Map.entry("cond_pagamento",      "Condições de Pagamento"),
            Map.entry("pessoas",             "Pessoas (Geral)"),
            Map.entry("vendedores",          "Vendedores"),
            Map.entry("clientes",            "Clientes"),
            Map.entry("categorias",          "Categorias"),
            Map.entry("fornecedores",        "Fornecedores"),
            Map.entry("transportadoras",     "Transportadoras"),
            Map.entry("tabela_preco",        "Tabelas de Preço"),
            Map.entry("tabela_preco_grupo",  "Preços por Grupo"),
            Map.entry("produtos",            "Produtos"),
            // telas ainda não implementadas — mantidas pro dia em que existirem
            Map.entry("nfe",                 "Emissão de NF-e"),
            Map.entry("contas_pagar_receber","Contas a pagar/receber"),
            Map.entry("relatorios",          "Relatórios financeiros"),
            Map.entry("conciliacao",         "Conciliação bancária"),
            Map.entry("folha_pagamento",     "Folha de pagamento"),
            Map.entry("integracao_contabil", "Integração contábil")
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