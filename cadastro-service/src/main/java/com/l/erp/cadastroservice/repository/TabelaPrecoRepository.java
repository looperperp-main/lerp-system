package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.TabelaPreco;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TabelaPrecoRepository extends JpaRepository<TabelaPreco, UUID> {
    boolean existsByNomeIgnoreCaseAndTenantId(String nome, Long tenantId);
    boolean existsByPadraoIsTrueAndTenantId( Long tenantId);
    Page<TabelaPreco> findAll(Pageable pageable);
    Optional<TabelaPreco> findById(UUID id);

    Optional<TabelaPreco> findByIdAndTenantId(UUID tabelaId, Long tenantId);
}