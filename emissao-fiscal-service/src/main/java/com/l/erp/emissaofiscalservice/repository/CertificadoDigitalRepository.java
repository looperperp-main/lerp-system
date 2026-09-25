package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.CertificadoDigital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificadoDigitalRepository extends JpaRepository<CertificadoDigital, UUID> {

    Optional<CertificadoDigital> findByTenantIdAndEmitenteId(Long tenantId, UUID emitenteId);

    /** Ignora o {@code @Filter} de tenant de propósito — o job de alerta varre todos os tenants. */
    List<CertificadoDigital> findByAtivoTrueAndAlertaEnviadoFalseAndCertificadoValidoAteLessThanEqual(OffsetDateTime limite);
}
