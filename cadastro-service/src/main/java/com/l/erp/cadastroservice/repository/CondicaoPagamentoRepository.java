package com.l.erp.cadastroservice.repository;

import com.l.erp.cadastroservice.domain.CondicaoPagamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CondicaoPagamentoRepository extends JpaRepository<CondicaoPagamento, UUID> {

    Page<CondicaoPagamento> findAllByTenantId(Long tenantId, Pageable pageable);

    Optional<CondicaoPagamento> findByIdAndTenantId(UUID id, Long tenantId);

    boolean existsByTenantIdAndNomeIgnoreCase(Long tenantId, String nome);
}
