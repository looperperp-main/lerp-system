package com.l.erp.billingservice.repository;

import com.l.erp.billingservice.domain.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {
    Optional<WebhookLog> findByAsaasPaymentId(String asaasPaymentId);

    Optional<WebhookLog> findByAsaasEventId(String asaasEventId);

    /** Webhooks presos em RECEBIDO além do cutoff (Fase 7 — WebhookRecoveryJob). */
    List<WebhookLog> findByStatusAndReceivedAtBefore(String status, OffsetDateTime cutoff);
}