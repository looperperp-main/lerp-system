package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoria;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRecebimentoMercadoria;
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

/** Repositório de recebimento de mercadoria, mesmo padrão de PedidoCompraRepository (Fase 2). */
@Repository
public interface RecebimentoMercadoriaRepository extends JpaRepository<RecebimentoMercadoria, UUID> {

    Optional<RecebimentoMercadoria> findByIdAndTenantId(UUID id, Long tenantId);

    List<RecebimentoMercadoria> findAllByPedidoId(UUID pedidoId);

    boolean existsByTenantIdAndNumero(Long tenantId, Long numero);

    @Query("select r from RecebimentoMercadoria r where r.tenantId = :tenantId "
            + "and (:status is null or r.status = :status) "
            + "and (:pedidoId is null or r.pedido.id = :pedidoId) "
            + "and (:dataRecebimentoDe is null or r.dataRecebimento >= :dataRecebimentoDe) "
            + "and (:dataRecebimentoAte is null or r.dataRecebimento <= :dataRecebimentoAte)")
    Page<RecebimentoMercadoria> buscarComFiltros(@Param("tenantId") Long tenantId,
                                                  @Param("status") StatusRecebimentoMercadoria status,
                                                  @Param("pedidoId") UUID pedidoId,
                                                  @Param("dataRecebimentoDe") LocalDate dataRecebimentoDe,
                                                  @Param("dataRecebimentoAte") LocalDate dataRecebimentoAte,
                                                  Pageable pageable);
}
