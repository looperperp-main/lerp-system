package com.l.erp.authservice.repositorios;

import com.l.erp.authservice.dominio.TrialEngagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TrialEngagementRepository extends JpaRepository<TrialEngagement, Long> {

    List<TrialEngagement> findByTenantId(Long tenantId);

    @Modifying
    @Query(value = """
            INSERT INTO auth.trial_engagement (tenant_id, feature, access_count, first_accessed_at, last_accessed_at)
            VALUES (:tenantId, :feature, 1, :now, :now)
            ON CONFLICT (tenant_id, feature) DO UPDATE
              SET access_count = auth.trial_engagement.access_count + 1,
                  last_accessed_at = :now
            """, nativeQuery = true)
    void upsert(@Param("tenantId") Long tenantId, @Param("feature") String feature, @Param("now") Instant now);
}