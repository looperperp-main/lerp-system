package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.ProdutoCategoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProdutoCategoriaRepository extends JpaRepository<ProdutoCategoria, UUID> {
    Page<ProdutoCategoria> findAllByTenantId(Long tenantId, Pageable pageable);
    Optional<ProdutoCategoria> findByIdAndTenantId(UUID id, Long tenantId);
    boolean existsByTenantIdAndNome(Long tenantId, String nome);
}
