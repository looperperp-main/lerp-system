package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.ConfiguracaoNfse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfiguracaoNfseRepository extends JpaRepository<ConfiguracaoNfse, UUID> {

    Optional<ConfiguracaoNfse> findByTenantIdAndEmitenteId(Long tenantId, UUID emitenteId);
}
