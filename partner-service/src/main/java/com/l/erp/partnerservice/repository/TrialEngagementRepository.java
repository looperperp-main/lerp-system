package com.l.erp.partnerservice.repository;

import com.l.erp.partnerservice.domain.TrialEngagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrialEngagementRepository extends JpaRepository<TrialEngagement, UUID> {

    Optional<TrialEngagement> findByTenantIdAndFeatureKey(Long tenantId, String featureKey);

    List<TrialEngagement> findByTenantId(Long tenantId);

    @Modifying
    @Query(value = """
            INSERT INTO partner.trial_engagement (id, tenant_id, feature_key, access_count, first_accessed_at, last_accessed_at)
            VALUES (gen_random_uuid(), :tenantId, :featureKey, 1, :now, :now)
            ON CONFLICT (tenant_id, feature_key)
            DO UPDATE SET access_count = partner.trial_engagement.access_count + 1,
                          last_accessed_at = :now
            """, nativeQuery = true)
    void upsertFeatureAccess(@Param("tenantId") Long tenantId,
                             @Param("featureKey") String featureKey,
                             @Param("now") OffsetDateTime now);
}