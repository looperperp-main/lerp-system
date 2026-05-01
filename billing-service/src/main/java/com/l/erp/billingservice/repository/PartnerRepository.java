package com.l.erp.billingservice.repository;

import com.l.erp.billingservice.domain.Partner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PartnerRepository extends JpaRepository<Partner, UUID> {
    boolean existsByCnpj(String cnpj);
    boolean existsByEmail(String email);
    boolean existsByReferralCode(String referralCode);
    Page<Partner> findByStatus(String status, Pageable pageable);
}