package com.l.erp.operacoesservice.repository.vendas;

import com.l.erp.operacoesservice.domain.vendas.PedidoSequencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Numeração concorrente do pedido (spec/o2c-vendas.md §3.4, §16 Fase 3): linha criada on-demand
 * via upsert, depois lida com SELECT ... FOR UPDATE pelo PedidoNumeroService.
 */
@Repository
public interface PedidoSequenciaRepository extends JpaRepository<PedidoSequencia, Long> {

    @Modifying
    @Query(value = "INSERT INTO vendas.pedido_sequencia (tenant_id, proximo_numero) VALUES (:tenantId, 1) "
            + "ON CONFLICT (tenant_id) DO NOTHING", nativeQuery = true)
    void inicializarSeNaoExiste(@Param("tenantId") Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PedidoSequencia s where s.tenantId = :tenantId")
    Optional<PedidoSequencia> findByTenantIdForUpdate(@Param("tenantId") Long tenantId);
}
