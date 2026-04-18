package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Vendedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VendedorRepository extends JpaRepository<Vendedor, UUID> {
    Page<Vendedor> findAllByTenantId(Long tenantId, Pageable pageable);

    Optional<Vendedor> findByIdAndTenantId(UUID id, Long tenantId);

    boolean existsByTenantIdAndNomeIgnoreCase(Long tenantId, String nome);
}
