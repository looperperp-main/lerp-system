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
    boolean existsByNomeIgnoreCase(String nome);
    Page<TabelaPreco> findAll(Pageable pageable);
    Optional<TabelaPreco> findById(UUID id);

    Optional<TabelaPreco> findByIdAndTenantId(UUID tabelaId, Long tenantId);
}