package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.Fornecedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FornecedorRepository extends JpaRepository<Fornecedor, UUID> {
    boolean existsByPessoaId(UUID pessoaId);
    @EntityGraph(attributePaths = {"pessoa"})
    Page<Fornecedor> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"pessoa"})
    Optional<Fornecedor> findById(UUID id);

    // Acesso por-id COM escopo de tenant — o @Filter não cobre load por PK (ver TenantFilterAspect).
    @EntityGraph(attributePaths = {"pessoa"})
    Optional<Fornecedor> findByIdAndTenantId(UUID id, Long tenantId);
}
