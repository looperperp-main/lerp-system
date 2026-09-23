package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.DocumentoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentoFiscalRepository extends JpaRepository<DocumentoFiscal, UUID> {

    Optional<DocumentoFiscal> findByIdAndTenantId(UUID id, Long tenantId);
}
