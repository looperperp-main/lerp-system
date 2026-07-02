package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    Page<Cliente> findAllByTenantId(Long tenantId, Pageable pageable);
    Optional<Cliente> findByIdAndTenantId(UUID id, Long tenantId);
    boolean existsByTenantIdAndPessoaNomeRazaoIgnoreCase(Long tenantId, String nome);
    boolean existsByTenantIdAndPessoaId(Long tenantId, UUID pessoaId);
    long deleteByIdAndTenantId(UUID id, Long tenantId);
}
