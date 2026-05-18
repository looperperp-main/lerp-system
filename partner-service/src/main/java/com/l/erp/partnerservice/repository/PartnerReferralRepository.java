package com.l.erp.partnerservice.repository;

import com.l.erp.partnerservice.domain.PartnerReferral;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartnerReferralRepository extends JpaRepository<PartnerReferral, UUID> {

    // Existência
    boolean existsByPartner_IdAndCnpjAndStatusIn(UUID partnerId, String cnpj, List<String> statuses);

    // Listagem paginada
    Page<PartnerReferral> findByPartner_Id(UUID partnerId, Pageable pageable);

    // Busca por ID + parceiro
    Optional<PartnerReferral> findByIdAndPartner_Id(UUID id, UUID partnerId);

    // Busca por tenantId (para login tracking)
    Optional<PartnerReferral> findByTenantIdAndStatusIn(Long tenantId, List<String> statuses);

    // Contagens para dashboard
    long countByPartner_IdAndStatus(UUID partnerId, String status);
    long countByPartner_IdAndStatusIn(UUID partnerId, List<String> statuses);
    long countByPartner_IdAndStatusAndTrialExpiresAtBefore(UUID partnerId, String status, OffsetDateTime deadline);

    // Trials urgentes (TRIAL ordenado por expiração)
    List<PartnerReferral> findTop10ByPartner_IdAndStatusOrderByTrialExpiresAtAsc(UUID partnerId, String status);

    // Atividade recente
    List<PartnerReferral> findTop10ByPartner_IdOrderByInvitedAtDesc(UUID partnerId);
    List<PartnerReferral> findTop10ByPartner_IdAndActivatedAtNotNullOrderByActivatedAtDesc(UUID partnerId);

    // Schedulers
    List<PartnerReferral> findByStatusAndTrialExpiresAtLessThanEqual(String status, OffsetDateTime deadline);
    List<PartnerReferral> findByStatusAndTrialStartedAtBetween(String status, OffsetDateTime from, OffsetDateTime to);
}