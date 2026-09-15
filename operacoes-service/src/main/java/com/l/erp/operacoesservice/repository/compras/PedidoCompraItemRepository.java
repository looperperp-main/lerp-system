package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.PedidoCompraItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Mesmo padrão de RequisicaoCompraItemRepository (Fase 1b). */
@Repository
public interface PedidoCompraItemRepository extends JpaRepository<PedidoCompraItem, UUID> {

    List<PedidoCompraItem> findAllByPedidoId(UUID pedidoId);

    void deleteAllByPedidoId(UUID pedidoId);
}
