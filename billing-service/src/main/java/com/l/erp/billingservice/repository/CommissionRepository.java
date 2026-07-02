package com.l.erp.billingservice.repository;

import com.l.erp.billingservice.domain.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, UUID> {

    boolean existsByAsaasPaymentId(String asaasPaymentId);

    Optional<Commission> findByAsaasPaymentId(String asaasPaymentId);

    @Query("SELECT c FROM Commission c JOIN FETCH c.subscription WHERE c.partnerId = :partnerId ORDER BY c.calculatedAt DESC")
    List<Commission> findByPartnerIdOrderByCalculatedAtDesc(@Param("partnerId") UUID partnerId);

    List<Commission> findByStatusAndPeriod(String status, String period);

    /** Comissões de um tenant num status (Fase 7 — cancelar PENDENTE ao cancelar a assinatura). */
    List<Commission> findByTenantIdAndStatus(Long tenantId, String status);

    /** Comissões de um transfer de payout (1 transfer por parceiro/período cobre N comissões). */
    List<Commission> findByAsaasTransferId(String asaasTransferId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM Commission c WHERE c.partnerId = :partnerId AND c.period = :period AND c.status = 'PENDENTE'")
    BigDecimal sumPendenteByPartnerAndPeriod(@Param("partnerId") UUID partnerId, @Param("period") String period);

    /** Todo o pendente do parceiro (independe de período) — é o que será repassado no próximo payout. */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM Commission c WHERE c.partnerId = :partnerId AND c.status = 'PENDENTE'")
    BigDecimal sumPendenteByPartner(@Param("partnerId") UUID partnerId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM Commission c WHERE c.partnerId = :partnerId AND c.status = 'PAGO'")
    BigDecimal sumPagoByPartner(@Param("partnerId") UUID partnerId);
}