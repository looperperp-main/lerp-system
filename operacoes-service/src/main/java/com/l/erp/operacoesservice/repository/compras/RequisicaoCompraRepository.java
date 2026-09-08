package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.RequisicaoCompra;
import com.l.erp.operacoesservice.domain.compras.enumerators.StatusRequisicaoCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequisicaoCompraRepository extends JpaRepository<RequisicaoCompra, UUID> {
    Page<RequisicaoCompra> findAllByTenantId(Long tenantId, Pageable pageable);
    Optional<RequisicaoCompra> findByIdAndTenantId(UUID id, Long tenantId);
    Optional<RequisicaoCompra> findByTenantIdAndNumero(Long tenantId, Long numero);
    boolean existsByTenantIdAndNumero(Long tenantId, Long numero);

    // Filtros da listagem (GET /requisicoes): status, solicitante e período de data_necessidade
    // (spec/p2p-compras.md §"Requisições"), mesmo idioma de filtro opcional de PedidoRepository.buscarComFiltros.
    @Query("select r from RequisicaoCompra r where r.tenantId = :tenantId "
            + "and (:status is null or r.status = :status) "
            + "and (:solicitanteId is null or r.solicitanteId = :solicitanteId) "
            + "and (:dataNecessidadeDe is null or r.dataNecessidade >= :dataNecessidadeDe) "
            + "and (:dataNecessidadeAte is null or r.dataNecessidade <= :dataNecessidadeAte)")
    Page<RequisicaoCompra> buscarComFiltros(@Param("tenantId") Long tenantId,
                                             @Param("status") StatusRequisicaoCompra status,
                                             @Param("solicitanteId") UUID solicitanteId,
                                             @Param("dataNecessidadeDe") LocalDate dataNecessidadeDe,
                                             @Param("dataNecessidadeAte") LocalDate dataNecessidadeAte,
                                             Pageable pageable);
}
