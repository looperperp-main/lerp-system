package com.l.erp.operacoesservice.repository.estoque;

import com.l.erp.operacoesservice.domain.estoque.OrdemProducao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrdemProducaoRepository extends JpaRepository<OrdemProducao, UUID> {
    Optional<OrdemProducao> findByIdAndTenantId(UUID id, Long tenantId);

    Page<OrdemProducao> findByTenantId(Long tenantId, Pageable pageable);
}
