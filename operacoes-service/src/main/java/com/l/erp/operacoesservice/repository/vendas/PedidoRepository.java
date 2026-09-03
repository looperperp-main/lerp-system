package com.l.erp.operacoesservice.repository.vendas;

import com.l.erp.operacoesservice.domain.vendas.Pedido;
import com.l.erp.operacoesservice.domain.vendas.enumerators.StatusPedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, UUID> {
    Page<Pedido> findAllByTenantId(Long tenantId, Pageable pageable);
    Optional<Pedido> findByIdAndTenantId(UUID id, Long tenantId);
    Optional<Pedido> findByTenantIdAndNumero(Long tenantId, Long numero);
    boolean existsByTenantIdAndNumero(Long tenantId, Long numero);

    /**
     * Soma local do valor_total dos pedidos do cliente em CONFIRMADO/EXPEDIDO ainda não faturados —
     * base da exposição de crédito (spec/o2c-vendas.md §7). Não inclui o AR do financeiro-service
     * (serviço ainda não existe neste monorepo); somar isso é upgrade futuro, quando o AR existir.
     */
    @Query("select coalesce(sum(p.valorTotal), 0) from Pedido p where p.tenantId = :tenantId "
            + "and p.clienteId = :clienteId and p.status in :statuses and p.id <> :pedidoIdExcluido")
    BigDecimal somaValorTotalPorStatus(@Param("tenantId") Long tenantId,
                                        @Param("clienteId") UUID clienteId,
                                        @Param("statuses") Collection<StatusPedido> statuses,
                                        @Param("pedidoIdExcluido") UUID pedidoIdExcluido);

    /** Listagem paginada com filtros opcionais (endpoint GET /api/v1/pedidos, spec §5/§10). */
    @Query("select p from Pedido p where p.tenantId = :tenantId "
            + "and (:status is null or p.status = :status) "
            + "and (:clienteId is null or p.clienteId = :clienteId) "
            + "and (:vendedorId is null or p.vendedorId = :vendedorId) "
            + "and (:numero is null or p.numero = :numero) "
            + "and (:dataEmissaoDe is null or p.dataEmissao >= :dataEmissaoDe) "
            + "and (:dataEmissaoAte is null or p.dataEmissao <= :dataEmissaoAte)")
    Page<Pedido> buscarComFiltros(@Param("tenantId") Long tenantId,
                                   @Param("status") StatusPedido status,
                                   @Param("clienteId") UUID clienteId,
                                   @Param("vendedorId") UUID vendedorId,
                                   @Param("numero") Long numero,
                                   @Param("dataEmissaoDe") LocalDate dataEmissaoDe,
                                   @Param("dataEmissaoAte") LocalDate dataEmissaoAte,
                                   Pageable pageable);
}
