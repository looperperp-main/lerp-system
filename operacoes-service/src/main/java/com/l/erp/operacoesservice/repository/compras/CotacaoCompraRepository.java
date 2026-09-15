package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.CotacaoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusCotacaoCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositório de cotação de compra, mesmo padrão de PedidoCompraRepository (Fase 2). */
@Repository
public interface CotacaoCompraRepository extends JpaRepository<CotacaoCompra, UUID> {

    List<CotacaoCompra> findAllByTenantId(Long tenantId);

    Optional<CotacaoCompra> findByIdAndTenantId(UUID id, Long tenantId);

    Optional<CotacaoCompra> findByTenantIdAndNumero(Long tenantId, Long numero);

    boolean existsByTenantIdAndNumero(Long tenantId, Long numero);

    @Query("select c from CotacaoCompra c where c.tenantId = :tenantId "
            + "and (:status is null or c.status = :status) "
            + "and (:requisicaoId is null or c.requisicaoId = :requisicaoId)")
    Page<CotacaoCompra> buscarComFiltros(@Param("tenantId") Long tenantId,
                                          @Param("status") StatusCotacaoCompra status,
                                          @Param("requisicaoId") UUID requisicaoId,
                                          Pageable pageable);
}
