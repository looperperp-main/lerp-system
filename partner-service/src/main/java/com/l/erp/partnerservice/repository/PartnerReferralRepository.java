package com.l.erp.partnerservice.repository;

import com.l.erp.partnerservice.domain.PartnerReferral;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartnerReferralRepository extends JpaRepository<PartnerReferral, UUID> {
    boolean existsByPartner_IdAndCnpjAndStatusIn(UUID partnerId, String cnpj, List<String> statuses);
    Page<PartnerReferral> findByPartner_Id(UUID partnerId, Pageable pageable);
    Optional<PartnerReferral> findByIdAndPartner_Id(UUID id, UUID partnerId);
}