package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Pessoa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {
    Page<Pessoa> findAllByTenantId(Long tenantId, Pageable pageable);

    Optional<Pessoa> findByIdAndTenantId(UUID id, Long tenantId);

    boolean existsByDocumentoAndNomeRazaoAndTenantId(String documento, String nomeRazao, Long tenantId);
}
