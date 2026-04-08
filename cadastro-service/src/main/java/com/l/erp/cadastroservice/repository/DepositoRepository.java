package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Deposito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepositoRepository extends JpaRepository<Deposito, UUID> {

    Page<Deposito> findAllByTenantId(Long tenantId, Pageable pageable);

    Optional<Deposito> findByTenantIdAndId(Long tenantId, UUID id);

    boolean existsByTenantIdAndNomeIgnoreCase(Long tenantId, String nome);

}
