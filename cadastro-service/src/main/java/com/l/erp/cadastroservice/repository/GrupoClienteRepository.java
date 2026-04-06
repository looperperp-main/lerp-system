package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.GrupoCliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GrupoClienteRepository extends JpaRepository<GrupoCliente, UUID> {

    Page<GrupoCliente> findAllByTenantId(Long tenantId, Pageable pageable);

    Optional<GrupoCliente> findByIdAndTenantId(UUID id, Long tenantId);

    boolean existsByTenantIdAndNomeIgnoreCase(Long tenantId, String nome);
}
