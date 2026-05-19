package com.l.erp.authservice.services;

import com.l.erp.authservice.dominio.TrialEngagement;
import com.l.erp.authservice.repositorios.TrialEngagementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TrialEngagementService {

    private static final Logger log = LoggerFactory.getLogger(TrialEngagementService.class);
    static final String LOGIN_FEATURE = "__LOGIN__";

    private final TrialEngagementRepository repository;

    public TrialEngagementService(TrialEngagementRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void track(Long tenantId, String feature) {
        try {
            repository.upsert(tenantId, feature, Instant.now());
        } catch (Exception e) {
            log.error("Falha ao registrar engagement tenantId={} feature={}", tenantId, feature, e);
        }
    }

    @Transactional
    public void trackLogin(Long tenantId) {
        track(tenantId, LOGIN_FEATURE);
    }

    public List<TrialEngagement> getByTenantId(Long tenantId) {
        return repository.findByTenantId(tenantId);
    }
}