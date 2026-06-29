package com.l.erp.billingservice.repository;

import com.l.erp.billingservice.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByAsaasSubscriptionId(String asaasSubscriptionId);
    Optional<Subscription> findByTenantIdAndStatus(Long tenantId, String status);

    /** Assinaturas num status (Fase 7 — ReconciliationJob varre AGUARDANDO_PAGAMENTO). */
    List<Subscription> findByStatus(String status);

    // ── Dunning (Fase 7) ──
    /** A suspender: ainda no status dado e já passou do suspend_at. */
    List<Subscription> findByStatusAndSuspendAtLessThanEqual(String status, OffsetDateTime now);

    /** A cancelar: em qualquer dos status e já passou do cancel_at. */
    List<Subscription> findByStatusInAndCancelAtLessThanEqual(Collection<String> statuses, OffsetDateTime now);

    /** Lembrete pendente: ATIVA, sem reminder enviado, com suspend_at na janela (now, limit]. */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = :status
              AND s.reminderSentAt IS NULL
              AND s.suspendAt IS NOT NULL
              AND s.suspendAt > :now
              AND s.suspendAt <= :limit
            """)
    List<Subscription> findRemindersDue(@Param("status") String status,
                                        @Param("now") OffsetDateTime now,
                                        @Param("limit") OffsetDateTime limit);
}