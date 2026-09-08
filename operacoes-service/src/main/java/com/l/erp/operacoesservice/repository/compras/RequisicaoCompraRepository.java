package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequisicaoCompraRepository extends JpaRepository<RequisicaoCompra, UUID> {
    Page<RequisicaoCompra> findAllByTenantId(Long tenantId, Pageable pageable);
    Optional<RequisicaoCompra> findByIdAndTenantId(UUID id, Long tenantId);
    Optional<RequisicaoCompra> findByTenantIdAndNumero(Long tenantId, Long numero);
    boolean existsByTenantIdAndNumero(Long tenantId, Long numero);
}
