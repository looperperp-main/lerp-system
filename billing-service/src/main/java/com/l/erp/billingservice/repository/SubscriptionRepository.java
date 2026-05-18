package com.l.erp.billingservice.repository;

import com.l.erp.billingservice.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByAsaasSubscriptionId(String asaasSubscriptionId);
    Optional<Subscription> findByTenantIdAndStatus(Long tenantId, String status);
}