package com.l.erp.billingservice.repository;

import com.l.erp.billingservice.domain.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {
    Optional<WebhookLog> findByAsaasPaymentId(String asaasPaymentId);
}