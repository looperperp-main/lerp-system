package com.l.erp.operacoesservice.repository.vendas;

import com.l.erp.operacoesservice.domain.vendas.PedidoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PedidoItemRepository extends JpaRepository<PedidoItem, UUID> {
    List<PedidoItem> findAllByPedidoId(UUID pedidoId);
    Optional<PedidoItem> findByIdAndTenantId(UUID id, Long tenantId);
    void deleteByIdAndTenantId(UUID id, Long tenantId);
    // Substituição em lote dos itens no update do orçamento (PUT /api/v1/pedidos/{id}, §5/§10).
    void deleteAllByPedidoId(UUID pedidoId);
}
