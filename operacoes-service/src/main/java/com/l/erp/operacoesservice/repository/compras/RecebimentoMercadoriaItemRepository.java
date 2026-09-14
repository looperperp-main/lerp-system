package com.l.erp.operacoesservice.repository.compras;

import com.l.erp.operacoesservice.domain.compras.RecebimentoMercadoriaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Mesmo padrão de PedidoCompraItemRepository (Fase 2). */
@Repository
public interface RecebimentoMercadoriaItemRepository extends JpaRepository<RecebimentoMercadoriaItem, UUID> {

    List<RecebimentoMercadoriaItem> findAllByRecebimentoId(UUID recebimentoId);

    // Busca em lote pra listagem paginada (evita N+1 — uma query por página, não por linha).
    List<RecebimentoMercadoriaItem> findAllByRecebimentoIdIn(List<UUID> recebimentoIds);

    void deleteAllByRecebimentoId(UUID recebimentoId);
}
