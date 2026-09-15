package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.PedidoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusPedidoCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositório de pedido de compra, mesmo padrão de RequisicaoCompraRepository (Fase 1b). */
@Repository
public interface PedidoCompraRepository extends JpaRepository<PedidoCompra, UUID> {

    List<PedidoCompra> findAllByTenantId(Long tenantId);

    Optional<PedidoCompra> findByIdAndTenantId(UUID id, Long tenantId);

    Optional<PedidoCompra> findByTenantIdAndNumero(Long tenantId, Long numero);

    boolean existsByTenantIdAndNumero(Long tenantId, Long numero);

    @Query("select p from PedidoCompra p where p.tenantId = :tenantId "
            + "and (:status is null or p.status = :status) "
            + "and (:fornecedorId is null or p.fornecedorId = :fornecedorId) "
            + "and (:dataEmissaoDe is null or p.dataEmissao >= :dataEmissaoDe) "
            + "and (:dataEmissaoAte is null or p.dataEmissao <= :dataEmissaoAte)")
    Page<PedidoCompra> buscarComFiltros(@Param("tenantId") Long tenantId,
                                         @Param("status") StatusPedidoCompra status,
                                         @Param("fornecedorId") UUID fornecedorId,
                                         @Param("dataEmissaoDe") LocalDate dataEmissaoDe,
                                         @Param("dataEmissaoAte") LocalDate dataEmissaoAte,
                                         Pageable pageable);
}
