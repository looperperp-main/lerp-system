package com.l.erp.emissaofiscalservice.repository;

import com.l.erp.emissaofiscalservice.domain.CredenciamentoSefaz;
import com.l.erp.emissaofiscalservice.domain.TipoDocumentoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredenciamentoSefazRepository extends JpaRepository<CredenciamentoSefaz, UUID> {

    Optional<CredenciamentoSefaz> findByTenantIdAndEmitenteIdAndUfAndModelo(
            Long tenantId, UUID emitenteId, String uf, TipoDocumentoFiscal modelo);

    List<CredenciamentoSefaz> findByTenantIdAndEmitenteId(Long tenantId, UUID emitenteId);
}
